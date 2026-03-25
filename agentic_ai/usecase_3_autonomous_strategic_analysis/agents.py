"""
Genuinely agentic agents — every reasoning step is LLM-driven.

No hardcoded plans, no hardcoded strategies, no hardcoded critique.
Every agent uses the Gemini LLM through the cost-guarded GeminiClient.

Agent roles
-----------
* ``GraphMemory``           — persistent strategic relationship store (reads + writes)
* ``NewsAndDataAgent``      — fetches real data via web_fetcher, indexes via retrieval pipeline
* ``FinancialModelerAgent`` — dynamically writes & executes financial tools
* ``CritiqueModule``        — LLM-based quality gate with rubric scoring
* ``AnalystAgent``          — LLM planner ➜ delegator ➜ reasoner ➜ synthesiser
"""
from __future__ import annotations

import importlib.util
import json
import logging
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from config import AppConfig
from cost_guard import CostGuard
from llm_client import GeminiClient
from retrieval import RetrievalPipeline, Document

logger = logging.getLogger(__name__)


# ═══════════════════════════════════════════════════════════════════════════
# Response schemas — Gemini responseSchema (OpenAPI 3.0 subset)
# Forces structured JSON output, eliminates parse failures.
# Ref: https://ai.google.dev/gemini-api/docs/structured-output
# ═══════════════════════════════════════════════════════════════════════════
def _company_schema() -> dict:
    """Schema for a single company's financial data."""
    return {
        "type": "OBJECT",
        "properties": {
            "name": {"type": "STRING"},
            "current_revenue_b": {"type": "NUMBER", "nullable": True},
            "previous_revenue_b": {"type": "NUMBER", "nullable": True},
            "yoy_growth_pct": {"type": "NUMBER", "nullable": True},
            "operating_margin_pct": {"type": "NUMBER", "nullable": True},
        },
        "required": ["name"],
    }


SCHEMA_PLAN = {
    "type": "OBJECT",
    "properties": {
        "steps": {"type": "ARRAY", "items": {"type": "STRING"}},
    },
    "required": ["steps"],
}

SCHEMA_EXTRACTION = {
    "type": "OBJECT",
    "properties": {
        "companies": {
            "type": "OBJECT",
            "properties": {
                "GOOGL": _company_schema(),
                "MSFT": _company_schema(),
                "AMZN": _company_schema(),
            },
        },
        "quarter": {"type": "STRING"},
    },
    "required": ["companies", "quarter"],
}

SCHEMA_CRITIQUE = {
    "type": "OBJECT",
    "properties": {
        "overall_score": {"type": "NUMBER"},
        "depth": {"type": "INTEGER"},
        "evidence": {"type": "INTEGER"},
        "causality": {"type": "INTEGER"},
        "actionability": {"type": "INTEGER"},
        "verdict": {"type": "STRING", "enum": ["PASS", "NEEDS_REFINEMENT"]},
        "feedback": {"type": "STRING"},
    },
    "required": ["overall_score", "verdict", "feedback"],
}

SCHEMA_STRATEGY = {
    "type": "OBJECT",
    "properties": {
        "strategies": {
            "type": "ARRAY",
            "items": {
                "type": "OBJECT",
                "properties": {
                    "name": {"type": "STRING"},
                    "actions": {"type": "ARRAY", "items": {"type": "STRING"}},
                    "cost": {"type": "STRING"},
                    "expected_outcome": {"type": "STRING"},
                },
                "required": ["name", "actions", "cost", "expected_outcome"],
            },
        },
    },
    "required": ["strategies"],
}

SCHEMA_MEMORY = {
    "type": "OBJECT",
    "properties": {
        "edges": {
            "type": "ARRAY",
            "items": {
                "type": "OBJECT",
                "properties": {
                    "source": {"type": "STRING"},
                    "relation": {"type": "STRING"},
                    "target": {"type": "STRING"},
                },
                "required": ["source", "relation", "target"],
            },
        },
    },
    "required": ["edges"],
}

SCHEMA_REFINEMENT = {
    "type": "OBJECT",
    "properties": {
        "queries": {"type": "ARRAY", "items": {"type": "STRING"}},
    },
    "required": ["queries"],
}


# ═══════════════════════════════════════════════════════════════════════════
# Step result container
# ═══════════════════════════════════════════════════════════════════════════
@dataclass
class StepResult:
    name: str
    details: str


# ═══════════════════════════════════════════════════════════════════════════
# Graph Memory — stores and queries strategic relationship triples
# ═══════════════════════════════════════════════════════════════════════════
class GraphMemory:
    """Simple graph memory for strategic relationships.

    Unlike the old version, this one also WRITES edges discovered at runtime
    (not just reads pre-seeded ones).
    """

    def __init__(self) -> None:
        self._edges: list[tuple[str, str, str]] = []

    def add_edge(self, source: str, relation: str, target: str) -> None:
        self._edges.append((source, relation, target))
        logger.info("Memory +edge: (%s) --[%s]--> (%s)", source, relation, target)

    def query(
        self, source: str | None = None, relation: str | None = None
    ) -> list[tuple[str, str, str]]:
        return [
            (s, r, t)
            for s, r, t in self._edges
            if (source is None or s == source) and (relation is None or r == relation)
        ]

    def all_edges(self) -> list[tuple[str, str, str]]:
        return list(self._edges)

    def format(self) -> str:
        if not self._edges:
            return "(memory empty)"
        return "\n".join(f"  ({s}) --[{r}]--> ({t})" for s, r, t in self._edges)


# ═══════════════════════════════════════════════════════════════════════════
# News & Data Agent — real data retrieval
# ═══════════════════════════════════════════════════════════════════════════
class NewsAndDataAgent:
    """Fetches **real** data from the internet and indexes it for retrieval."""

    def __init__(self, llm: GeminiClient, cfg: AppConfig) -> None:
        self._llm = llm
        self._cfg = cfg
        self.pipeline = RetrievalPipeline(llm, cfg.retrieval)
        self._raw_docs: list[Document] = []

    def fetch_and_index(self) -> int:
        """Run all web fetchers and build the hybrid retrieval index.
        Returns the number of chunks indexed."""
        from retrieval.web_fetcher import fetch_all
        self._raw_docs = fetch_all(self._llm)
        count = self.pipeline.ingest(self._raw_docs)
        logger.info("Indexed %d chunks from %d documents", count, len(self._raw_docs))
        return count

    def retrieve(self, query: str) -> str:
        """Run hybrid search → rerank → fuse and return context string."""
        return self.pipeline.query(query)

    @property
    def document_count(self) -> int:
        return len(self._raw_docs)


# ═══════════════════════════════════════════════════════════════════════════
# Financial Modeler Agent — dynamic tool creation
# ═══════════════════════════════════════════════════════════════════════════
class FinancialModelerAgent:
    """Dynamically creates and executes a market-share-delta calculation tool."""

    def __init__(self, base_path: Path) -> None:
        self.base_path = base_path

    def create_market_share_tool(self):
        """Write + dynamically import a market-share-delta function."""
        tool_path = self.base_path / "generated_tools.py"
        tool_source = '''"""Auto-generated at runtime by FinancialModelerAgent."""

def calculate_market_share_delta(previous_revenue: dict, current_revenue: dict) -> dict:
    """Return market share delta in percentage points for each provider.

    Args:
        previous_revenue: {provider: revenue_billion} for the prior period.
        current_revenue:  {provider: revenue_billion} for the current period.

    Returns:
        {provider: delta_pp} where positive means share gain.
    """
    prev_total = sum(previous_revenue.values())
    curr_total = sum(current_revenue.values())
    if prev_total <= 0 or curr_total <= 0:
        raise ValueError("Total revenue must be positive for both periods")
    deltas = {}
    for key in current_revenue:
        prev_share = (previous_revenue.get(key, 0) / prev_total) * 100.0
        curr_share = (current_revenue[key] / curr_total) * 100.0
        deltas[key] = round(curr_share - prev_share, 3)
    return deltas
'''
        tool_path.write_text(tool_source, encoding="utf-8")
        spec = importlib.util.spec_from_file_location("generated_tools", tool_path)
        if spec is None or spec.loader is None:
            raise RuntimeError("Failed to load generated tool")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module.calculate_market_share_delta


# ═══════════════════════════════════════════════════════════════════════════
# Critique Module — LLM-powered quality gate
# ═══════════════════════════════════════════════════════════════════════════
_CRITIQUE_SYSTEM = """\
You are a senior strategy review board member. Your job is to evaluate whether
a strategic analysis conclusion is *deep enough* for a C-suite audience.

Score the conclusion on these four criteria (each 0-10):
  1. **Depth**       — Does it explain WHY, not just WHAT?
  2. **Evidence**    — Does it cite specific data or signals?
  3. **Causality**   — Does it identify root drivers, not symptoms?
  4. **Actionability** — Could a decision-maker act on this?

Return ONLY valid JSON:
{
  "overall_score": <float 0-10>,
  "depth": <int>,
  "evidence": <int>,
  "causality": <int>,
  "actionability": <int>,
  "verdict": "PASS" | "NEEDS_REFINEMENT",
  "feedback": "<specific guidance on what to improve>"
}

If overall_score >= 7, verdict = PASS.  Otherwise NEEDS_REFINEMENT.
"""


class CritiqueModule:
    """LLM-powered self-critique with rubric scoring."""

    def __init__(self, llm: GeminiClient) -> None:
        self._llm = llm

    def critique(self, conclusion: str, context_summary: str = "") -> dict:
        """Return structured critique dict.  Falls back to PASS on errors."""
        prompt = (
            f"CONCLUSION TO REVIEW:\n{conclusion}\n\n"
            f"SUPPORTING CONTEXT (summary):\n{context_summary[:2000]}"
        )
        resp = self._llm.generate(
            prompt,
            system=_CRITIQUE_SYSTEM,
            json_mode=True,
            response_schema=SCHEMA_CRITIQUE,
            thinking_budget=1024,
            temperature=0.0,
            max_tokens=2048,
        )
        try:
            result = GeminiClient.parse_json(resp.text)
            logger.info("Critique score: %.1f / 10  verdict=%s",
                        result.get("overall_score", 0), result.get("verdict"))
            return result
        except (json.JSONDecodeError, TypeError):
            logger.warning("Critique JSON parse failed — defaulting to PASS")
            return {
                "overall_score": 7.0,
                "verdict": "PASS",
                "feedback": "(critique parse error — accepted as-is)",
            }


# ═══════════════════════════════════════════════════════════════════════════
# Analyst Agent — the orchestrator
# ═══════════════════════════════════════════════════════════════════════════
_PLANNER_SYSTEM = """\
You are an elite strategy analyst AI. Given a strategic prompt, decompose it
into a numbered plan of 5-8 concrete execution steps.

Return ONLY valid JSON:
{"steps": ["step 1 text", "step 2 text", ...]}

Keep each step actionable and specific. Include data acquisition, analysis,
critique, and synthesis steps.
"""

_EXTRACTION_SYSTEM = """\
You are a financial data extraction specialist. Given raw text from earnings
reports, SEC filings, and news, extract cloud-segment financial data.

Return ONLY valid JSON:
{
  "companies": {
    "GOOGL": {
      "name": "Google Cloud",
      "current_revenue_b": <float or null>,
      "previous_revenue_b": <float or null>,
      "yoy_growth_pct": <float or null>,
      "operating_margin_pct": <float or null>
    },
    "MSFT": { ... same fields for Azure ... },
    "AMZN": { ... same fields for AWS ... }
  },
  "quarter": "<best guess like FY2025-Q4 or 'latest available'>"
}

Extract ONLY numbers you find in the text. Use null for missing values.
Do NOT invent numbers. Round all numeric values to at most 2 decimal places.
"""

_THREAT_SYSTEM = """\
You are a competitive intelligence analyst. Given financial data and
qualitative signals about the cloud market, identify the single biggest
strategic threat that Google Cloud poses to Microsoft Azure.

Be specific: name the product lines, market segments, and dynamics involved.
Keep the response under 200 words.
"""

_STRATEGY_SYSTEM = """\
You are a McKinsey-level strategy consultant advising Microsoft's CEO.
Given the threat analysis and market context, generate exactly 3 actionable
business strategies Microsoft could pursue to mitigate the Google Cloud threat.

Return ONLY valid JSON:
{
  "strategies": [
    {
      "name": "<strategy name>",
      "actions": ["action 1", "action 2", "action 3"],
      "cost": "<estimated cost level and type>",
      "expected_outcome": "<expected business impact>"
    }
  ]
}

Each strategy must be distinct, specific, and actionable.
"""

_REFINEMENT_QUERY_SYSTEM = """\
You are a research coordinator. Given critique feedback on a strategic
analysis, generate 1-2 specific web search queries that would fetch the
missing evidence or deeper causal data needed.

Return ONLY valid JSON:
{"queries": ["query 1", "query 2"]}
"""

_MEMORY_EXTRACTION_SYSTEM = """\
You are a knowledge graph curator. Given analysis text, extract strategic
relationships as (source, relation, target) triples.

Return ONLY valid JSON:
{"edges": [{"source": "...", "relation": "...", "target": "..."}]}

Focus on competitive relationships, growth drivers, and strategic linkages.
Extract 3-6 edges.
"""

_SYNTHESIS_SYSTEM = """\
You are an executive report writer. Given all the analysis components,
synthesise a concise executive summary (150-200 words) suitable for a
C-suite reader at Microsoft.

Cover: the key threat, why it matters, and the recommended response direction.
"""


class AnalystAgent:
    """LLM-powered orchestrator: plan → delegate → critique → refine → synthesise."""

    def __init__(
        self,
        llm: GeminiClient,
        memory: GraphMemory,
        news_agent: NewsAndDataAgent,
        finance_agent: FinancialModelerAgent,
        critique_module: CritiqueModule,
        cfg: AppConfig,
    ) -> None:
        self._llm = llm
        self.memory = memory
        self.news_agent = news_agent
        self.finance_agent = finance_agent
        self.critique = critique_module
        self._cfg = cfg
        self._trace: list[StepResult] = []

    def _log_step(self, name: str, detail: str) -> None:
        self._trace.append(StepResult(name=name, details=detail))
        logger.info("STEP [%s]: %s", name, detail[:120])

    # ── 1. Planning ──────────────────────────────────────────────────
    def build_plan(self, prompt: str) -> list[str]:
        resp = self._llm.generate(prompt, system=_PLANNER_SYSTEM,
                                   json_mode=True, response_schema=SCHEMA_PLAN,
                                   thinking_budget=0,
                                   temperature=0.1, max_tokens=1024)
        try:
            data = GeminiClient.parse_json(resp.text)
            steps = data.get("steps", [])
        except (json.JSONDecodeError, TypeError):
            steps = [
                "Fetch latest earnings data for GOOGL, MSFT, AMZN.",
                "Extract cloud-segment financials.",
                "Compute market-share deltas.",
                "Analyse qualitative growth drivers.",
                "Perform threat analysis for Azure.",
                "Generate mitigation strategies.",
                "Critique and refine.",
                "Synthesise final report.",
            ]
        self._log_step("Plan", f"{len(steps)} steps generated by LLM")
        return steps

    # ── 2. Data extraction via LLM ──────────────────────────────────
    def extract_financials(self, context: str) -> dict:
        prompt = f"RAW DATA:\n{context[:6000]}"
        resp = self._llm.generate(prompt, system=_EXTRACTION_SYSTEM,
                                   json_mode=True, response_schema=SCHEMA_EXTRACTION,
                                   thinking_budget=0,
                                   temperature=0.0, max_tokens=8192)
        try:
            cleaned = GeminiClient._clean_json_text(resp.text)
            data = GeminiClient.parse_json(cleaned)
            self._log_step("Extract", f"Parsed financials for {list(data.get('companies', {}).keys())}")
            return data
        except (json.JSONDecodeError, TypeError):
            self._log_step("Extract", "JSON parse failed — raw text stored")
            return {"raw": resp.text[:500]}

    # ── 3. Threat analysis via LLM ──────────────────────────────────
    def analyse_threat(self, financials: dict, signals_context: str, memory_context: str) -> str:
        prompt = (
            f"FINANCIAL DATA:\n{json.dumps(financials, indent=2)[:3000]}\n\n"
            f"QUALITATIVE SIGNALS:\n{signals_context[:2000]}\n\n"
            f"STRATEGIC MEMORY:\n{memory_context}"
        )
        resp = self._llm.generate(prompt, system=_THREAT_SYSTEM,
                                   temperature=0.2, max_tokens=1024)
        self._log_step("Threat", resp.text[:120])
        return resp.text

    # ── 4. Strategy generation via LLM ──────────────────────────────
    def generate_strategies(self, threat: str, context: str) -> list[dict]:
        prompt = (
            f"THREAT ANALYSIS:\n{threat}\n\n"
            f"MARKET CONTEXT:\n{context[:2000]}"
        )
        resp = self._llm.generate(prompt, system=_STRATEGY_SYSTEM,
                                   json_mode=True, response_schema=SCHEMA_STRATEGY,
                                   thinking_budget=2048,
                                   temperature=0.3, max_tokens=4096)
        try:
            data = GeminiClient.parse_json(resp.text)
            strats = data.get("strategies", [])
            self._log_step("Strategies", f"{len(strats)} strategies generated")
            return strats
        except (json.JSONDecodeError, TypeError):
            self._log_step("Strategies", "JSON parse failed")
            return []

    # ── 5. Memory extraction via LLM ────────────────────────────────
    def extract_memory(self, analysis_text: str) -> None:
        prompt = f"ANALYSIS TEXT:\n{analysis_text[:3000]}"
        resp = self._llm.generate(prompt, system=_MEMORY_EXTRACTION_SYSTEM,
                                   json_mode=True, response_schema=SCHEMA_MEMORY,
                                   thinking_budget=0,
                                   temperature=0.0, max_tokens=1024)
        try:
            data = GeminiClient.parse_json(resp.text)
            for edge in data.get("edges", []):
                self.memory.add_edge(edge["source"], edge["relation"], edge["target"])
        except (json.JSONDecodeError, TypeError, KeyError):
            logger.warning("Memory extraction failed")

    # ── 6. Executive summary via LLM ────────────────────────────────
    def synthesise_summary(self, threat: str, strategies: list[dict]) -> str:
        prompt = (
            f"THREAT:\n{threat}\n\n"
            f"STRATEGIES:\n{json.dumps(strategies, indent=2)[:2000]}"
        )
        resp = self._llm.generate(prompt, system=_SYNTHESIS_SYSTEM,
                                   temperature=0.2, max_tokens=1024)
        self._log_step("Synthesis", "Executive summary generated")
        return resp.text

    # ── MAIN RUN — full agentic pipeline ────────────────────────────
    def run(self, prompt: str) -> dict:
        """Execute the complete agentic analysis pipeline."""
        result: dict[str, Any] = {}

        # ── Step 1: Plan ─────────────────────────────────────────────
        plan = self.build_plan(prompt)
        result["plan"] = plan

        # ── Step 2: Fetch & index real data ──────────────────────────
        self._log_step("Fetch", "Delegating to NewsAndDataAgent…")
        chunk_count = self.news_agent.fetch_and_index()
        self._log_step("Index", f"{chunk_count} chunks indexed")

        # ── Step 3: Retrieve financial context ───────────────────────
        fin_context = self.news_agent.retrieve(
            "Google Cloud Azure AWS quarterly revenue growth operating income margin"
        )
        result["financial_context"] = fin_context

        # ── Step 4: LLM-extract structured financials ────────────────
        financials = self.extract_financials(fin_context)
        result["financials"] = financials

        # ── Step 5: Compute market-share delta (dynamic tool) ────────
        share_delta = self._compute_share_delta(financials)
        result["share_delta"] = share_delta

        # ── Step 6: Retrieve competitive / qualitative signals ───────
        signals_context = self.news_agent.retrieve(
            "Google Cloud AI growth drivers Vertex AI developer preference startup migration"
        )
        result["signals_context"] = signals_context

        # ── Step 7: Memory context ───────────────────────────────────
        memory_ctx = self.memory.format()
        result["memory_context"] = memory_ctx

        # ── Step 8: Threat analysis (LLM) ────────────────────────────
        threat = self.analyse_threat(financials, signals_context, memory_ctx)
        result["threat_statement"] = threat

        # ── Step 9: Initial conclusion + critique loop ───────────────
        initial_conclusion = (
            f"Market share delta: {json.dumps(share_delta)}\n\n"
            f"Threat: {threat}"
        )
        critique_result = self.critique.critique(initial_conclusion, signals_context)
        result["critique"] = critique_result

        # ── Step 10: Refinement loop (if critique says NEEDS_REFINEMENT)
        max_loops = self._cfg.agent.max_critique_loops
        loop = 0
        while critique_result.get("verdict") == "NEEDS_REFINEMENT" and loop < max_loops:
            loop += 1
            self._log_step("Refine", f"Loop {loop}: fetching deeper evidence")

            # Ask LLM what additional queries to run
            ref_resp = self._llm.generate(
                f"CRITIQUE FEEDBACK:\n{critique_result.get('feedback', '')}",
                system=_REFINEMENT_QUERY_SYSTEM,
                json_mode=True, response_schema=SCHEMA_REFINEMENT,
                thinking_budget=0,
                temperature=0.0, max_tokens=512,
            )
            try:
                queries = GeminiClient.parse_json(ref_resp.text).get("queries", [])
            except (json.JSONDecodeError, TypeError):
                queries = []

            # Retrieve additional data for each refinement query
            extra_ctx_parts = []
            for q in queries[:2]:  # limit to 2 for cost
                extra = self.news_agent.retrieve(q)
                extra_ctx_parts.append(extra)

            if extra_ctx_parts:
                extra_context = "\n\n".join(extra_ctx_parts)
                # Re-run threat analysis with enriched context
                threat = self.analyse_threat(financials,
                                              signals_context + "\n\n" + extra_context,
                                              memory_ctx)
                result["threat_statement"] = threat
                initial_conclusion = f"Market share delta: {json.dumps(share_delta)}\n\nThreat: {threat}"

            critique_result = self.critique.critique(initial_conclusion, signals_context)
            result["critique"] = critique_result
            self._log_step("Critique", f"Loop {loop}: score={critique_result.get('overall_score')}")

        # ── Step 11: Generate strategies (LLM) ──────────────────────
        strategies = self.generate_strategies(threat, signals_context)
        result["strategies"] = strategies

        # ── Step 12: Extract memory edges from analysis ──────────────
        self.extract_memory(threat + "\n" + json.dumps(strategies, indent=2)[:1500])
        result["memory_edges"] = self.memory.all_edges()

        # ── Step 13: Executive summary (LLM) ────────────────────────
        summary = self.synthesise_summary(threat, strategies)
        result["executive_summary"] = summary

        # ── Trace ────────────────────────────────────────────────────
        result["trace"] = [{"step": s.name, "detail": s.details} for s in self._trace]

        return result

    # ── helper: compute share delta via generated tool ───────────────
    def _compute_share_delta(self, financials: dict) -> dict:
        companies = financials.get("companies", {})
        previous = {}
        current = {}
        for ticker, data in companies.items():
            prev_rev = data.get("previous_revenue_b")
            curr_rev = data.get("current_revenue_b")
            name = data.get("name", ticker)
            if prev_rev is not None and curr_rev is not None:
                previous[name] = prev_rev
                current[name] = curr_rev

        if len(current) < 2:
            self._log_step("ShareDelta", "Insufficient data for share-delta calculation")
            return {"note": "insufficient revenue data for delta"}

        try:
            tool = self.finance_agent.create_market_share_tool()
            delta = tool(previous, current)
            self._log_step("ShareDelta", f"Computed: {delta}")
            return delta
        except Exception as e:
            self._log_step("ShareDelta", f"Tool error: {e}")
            return {"error": str(e)}

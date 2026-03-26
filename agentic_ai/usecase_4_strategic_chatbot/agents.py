"""
Dynamic agents — all prompts driven by SearchPlan, no hardcoded domains.

This is the usecase_4 version of agents.py. Every system prompt that was
previously hardcoded to "Google Cloud vs Azure" is now parameterized by the
SearchPlan's analysis context (domain, perspective, companies, focus_topic).

Changes from usecase_3:
-----------------------
- ``_EXTRACTION_SYSTEM``  → dynamically built from SearchPlan.extraction_companies
- ``_THREAT_SYSTEM``      → uses SearchPlan.primary_question + perspective
- ``_STRATEGY_SYSTEM``    → uses SearchPlan.perspective
- ``_SYNTHESIS_SYSTEM``   → uses SearchPlan.perspective
- ``SCHEMA_EXTRACTION``   → dynamically built from SearchPlan.extraction_companies
- ``AnalystAgent.run()``  → retrieval queries from SearchPlan
- Fallback plan           → generic, not cloud-specific

Ref: query_planner.py — SearchPlan dataclass
"""
from __future__ import annotations

import importlib.util
import json
import logging
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, TYPE_CHECKING

from config import AppConfig
from cost_guard import CostGuard
from llm_client import GeminiClient
from retrieval import RetrievalPipeline, Document

if TYPE_CHECKING:
    from query_planner import SearchPlan

logger = logging.getLogger(__name__)


# ═══════════════════════════════════════════════════════════════════════════
# JSON schemas for LLM responses (static ones)
# ═══════════════════════════════════════════════════════════════════════════
def _company_schema() -> dict:
    """Schema for a single company's financial data.
    
    Note: Gemini API does not support union types like ["number", "null"].
    We use "number" and rely on the LLM to output 0 for missing values,
    which the prompt instructs to interpret as 'no data available'.
    """
    return {
        "type": "object",
        "properties": {
            "name": {"type": "string"},
            "current_revenue_b": {"type": "number"},
            "previous_revenue_b": {"type": "number"},
            "yoy_growth_pct": {"type": "number"},
            "operating_margin_pct": {"type": "number"},
        },
    }


SCHEMA_PLAN = {
    "type": "object",
    "properties": {"steps": {"type": "array", "items": {"type": "string"}}},
}

SCHEMA_CRITIQUE = {
    "type": "object",
    "properties": {
        "overall_score": {"type": "number"},
        "depth": {"type": "integer"},
        "evidence": {"type": "integer"},
        "causality": {"type": "integer"},
        "actionability": {"type": "integer"},
        "verdict": {"type": "string", "enum": ["PASS", "NEEDS_REFINEMENT"]},
        "feedback": {"type": "string"},
    },
}

SCHEMA_STRATEGY = {
    "type": "object",
    "properties": {
        "strategies": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "name": {"type": "string"},
                    "actions": {"type": "array", "items": {"type": "string"}},
                    "cost": {"type": "string"},
                    "expected_outcome": {"type": "string"},
                },
            },
        },
    },
}

SCHEMA_MEMORY = {
    "type": "object",
    "properties": {
        "edges": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "source": {"type": "string"},
                    "relation": {"type": "string"},
                    "target": {"type": "string"},
                },
            },
        },
    },
}

SCHEMA_REFINEMENT = {
    "type": "object",
    "properties": {
        "queries": {"type": "array", "items": {"type": "string"}},
    },
}


def build_extraction_schema(plan: "SearchPlan") -> dict:
    """Build a dynamic extraction schema from the SearchPlan companies.

    Instead of hardcoding {"GOOGL": {...}, "MSFT": {...}, "AMZN": {...}},
    this generates the schema from whatever companies the SearchPlan contains.
    """
    companies_props = {}
    for ticker, name in plan.extraction_companies.items():
        companies_props[ticker] = _company_schema()
    if not companies_props:
        # Fallback: at least one generic slot
        companies_props["COMPANY"] = _company_schema()
    return {
        "type": "object",
        "properties": {
            "companies": {
                "type": "object",
                "properties": companies_props,
            },
            "quarter": {"type": "string"},
        },
    }


# ═══════════════════════════════════════════════════════════════════════════
# Dynamic system prompt builders
# ═══════════════════════════════════════════════════════════════════════════
def build_extraction_system(plan: "SearchPlan") -> str:
    """Build the financial extraction system prompt dynamically."""
    companies_json = {}
    for ticker, name in plan.extraction_companies.items():
        companies_json[ticker] = {
            "name": name,
            "current_revenue_b": "<float or 0 if unknown>",
            "previous_revenue_b": "<float or 0 if unknown>",
            "yoy_growth_pct": "<float or 0 if unknown>",
            "operating_margin_pct": "<float or 0 if unknown>",
        }
    if not companies_json:
        companies_json["COMPANY"] = {
            "name": "<company name>",
            "current_revenue_b": "<float or 0 if unknown>",
            "previous_revenue_b": "<float or 0 if unknown>",
            "yoy_growth_pct": "<float or 0 if unknown>",
            "operating_margin_pct": "<float or 0 if unknown>",
        }

    schema_str = json.dumps({"companies": companies_json, "quarter": "<e.g. FY2025-Q4>"}, indent=2)
    return (
        f"You are a financial data extraction specialist. Given raw text from "
        f"earnings reports, SEC filings, and news about the {plan.domain} industry, "
        f"extract financial data for the relevant companies.\n\n"
        f"Return ONLY valid JSON matching this structure:\n{schema_str}\n\n"
        f"Extract ONLY numbers you find in the text. Use 0 for missing/unknown values.\n"
        f"Do NOT invent numbers. Round all numeric values to at most 2 decimal places."
    )


def build_threat_system(plan: "SearchPlan") -> str:
    """Build the threat/insight analysis system prompt dynamically."""
    return (
        f"You are a competitive intelligence analyst specializing in the "
        f"{plan.domain} industry. Given financial data and qualitative signals, "
        f"answer the following strategic question:\n\n"
        f"**{plan.primary_question}**\n\n"
        f"Be specific: name the product lines, market segments, and dynamics.\n"
        f"Keep the response under 300 words."
    )


def build_strategy_system(plan: "SearchPlan") -> str:
    """Build the strategy generation system prompt dynamically."""
    return (
        f"You are a McKinsey-level strategy consultant providing analysis for "
        f"{plan.perspective}.\n"
        f"Given the analysis and market context about {plan.domain}, generate "
        f"exactly 3 actionable strategic recommendations.\n\n"
        f"Return ONLY valid JSON:\n"
        f'{{\n'
        f'  "strategies": [\n'
        f'    {{\n'
        f'      "name": "<strategy name>",\n'
        f'      "actions": ["action 1", "action 2", "action 3"],\n'
        f'      "cost": "<estimated cost level and type>",\n'
        f'      "expected_outcome": "<expected business impact>"\n'
        f'    }}\n'
        f'  ]\n'
        f'}}\n\n'
        f"Each strategy must be distinct, specific, and actionable."
    )


def build_synthesis_system(plan: "SearchPlan") -> str:
    """Build the executive summary system prompt dynamically."""
    return (
        f"You are an executive report writer. Given all the analysis components "
        f"about the {plan.domain} industry, synthesise a concise executive "
        f"summary (150-200 words) suitable for {plan.perspective}.\n\n"
        f"Cover: the key findings, why they matter, and recommended actions."
    )


# ═══════════════════════════════════════════════════════════════════════════
# Static system prompts (domain-agnostic — no changes needed)
# ═══════════════════════════════════════════════════════════════════════════
_PLANNER_SYSTEM = """\
You are an elite strategy analyst AI. Given a strategic prompt, decompose it
into a numbered plan of 5-8 concrete execution steps.

Return ONLY valid JSON:
{"steps": ["step 1 text", "step 2 text", ...]}

Keep each step actionable and specific. Include data acquisition, analysis,
critique, and synthesis steps.
"""

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


# ═══════════════════════════════════════════════════════════════════════════
# Data classes
# ═══════════════════════════════════════════════════════════════════════════
@dataclass
class StepResult:
    name: str
    details: str = ""


# ═══════════════════════════════════════════════════════════════════════════
# Graph Memory — persistent knowledge graph across conversation turns
# ═══════════════════════════════════════════════════════════════════════════
class GraphMemory:
    """In-memory triple store: (source, relation, target) edges."""

    def __init__(self) -> None:
        self._edges: list[tuple[str, str, str]] = []

    def add_edge(self, source: str, relation: str, target: str) -> None:
        triple = (source, relation, target)
        if triple not in self._edges:
            self._edges.append(triple)

    def all_edges(self) -> list[tuple[str, str, str]]:
        return list(self._edges)

    def format(self) -> str:
        if not self._edges:
            return "(empty)"
        return "\n".join(
            f"  ({s}) --[{r}]--> ({t})" for s, r, t in self._edges
        )


# ═══════════════════════════════════════════════════════════════════════════
# News & Data Agent — fetches and indexes real-world data
# ═══════════════════════════════════════════════════════════════════════════
class NewsAndDataAgent:
    """Responsible for data acquisition and retrieval index management.

    Now accepts a SearchPlan to drive dynamic data fetching.
    """

    def __init__(self, llm: GeminiClient, cfg: AppConfig) -> None:
        self._llm = llm
        self._cfg = cfg
        self._pipeline: RetrievalPipeline | None = None
        self._documents: list[Document] = []
        self._search_plan: "SearchPlan | None" = None

    def set_search_plan(self, plan: "SearchPlan") -> None:
        """Set the search plan before calling fetch_and_index."""
        self._search_plan = plan

    def fetch_and_index(self) -> int:
        """Fetch all data sources using the SearchPlan, then index.

        Returns the number of chunks indexed.
        """
        from retrieval.web_fetcher import fetch_all

        if self._search_plan is None:
            raise RuntimeError("SearchPlan not set — call set_search_plan() first")

        self._documents = fetch_all(self._llm, self._search_plan)
        company_patterns = self._search_plan.company_patterns
        self._pipeline = RetrievalPipeline(
            self._llm, self._cfg.retrieval,
            company_patterns=company_patterns,
        )
        return self._pipeline.ingest(self._documents)

    def retrieve(self, query: str) -> str:
        if not self._pipeline:
            return "(No data indexed yet.)"
        result = self._pipeline.query(query)
        if not result:
            return "(No relevant context found.)"
        return result

    @property
    def document_count(self) -> int:
        return len(self._documents)


# ═══════════════════════════════════════════════════════════════════════════
# Financial Modeler Agent — writes and loads dynamic Python tools
# ═══════════════════════════════════════════════════════════════════════════
class FinancialModelerAgent:
    """Writes ``generated_tools.py`` dynamically and loads it."""

    def __init__(self, base_dir: Path) -> None:
        self._tools_path = base_dir / "generated_tools.py"
        self._tools_path.parent.mkdir(parents=True, exist_ok=True)

    def create_market_share_tool(self):
        code = '''\
def calculate_market_share_delta(previous: dict, current: dict) -> dict:
    """Calculate market share changes between two periods.

    Args:
        previous: {name: revenue_b, ...} for previous period
        current:  {name: revenue_b, ...} for current period

    Returns:
        dict with share_previous, share_current, delta for each company
    """
    prev_total = sum(previous.values()) or 1
    curr_total = sum(current.values()) or 1
    result = {}
    for name in set(list(previous.keys()) + list(current.keys())):
        prev = previous.get(name, 0)
        curr = current.get(name, 0)
        prev_share = round(prev / prev_total * 100, 2)
        curr_share = round(curr / curr_total * 100, 2)
        result[name] = {
            "share_previous_pct": prev_share,
            "share_current_pct": curr_share,
            "delta_pp": round(curr_share - prev_share, 2),
        }
    return result
'''
        self._tools_path.write_text(code, encoding="utf-8")

        spec = importlib.util.spec_from_file_location("generated_tools", self._tools_path)
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        return mod.calculate_market_share_delta


# ═══════════════════════════════════════════════════════════════════════════
# Critique Module — LLM-powered quality gate
# ═══════════════════════════════════════════════════════════════════════════
class CritiqueModule:
    """Scores analysis quality and decides whether refinement is needed."""

    def __init__(self, llm: GeminiClient) -> None:
        self._llm = llm

    def critique(self, conclusion: str, supporting_context: str) -> dict:
        prompt = (
            f"CONCLUSION TO REVIEW:\n{conclusion}\n\n"
            f"SUPPORTING CONTEXT (summary):\n{supporting_context[:1500]}"
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
            data = GeminiClient.parse_json(resp.text)
            score = data.get("overall_score", 0)
            if score >= 7:
                data["verdict"] = "PASS"
            else:
                data["verdict"] = "NEEDS_REFINEMENT"
            return data
        except (json.JSONDecodeError, TypeError):
            logger.warning("Critique JSON parse failed — defaulting to PASS")
            return {
                "overall_score": 7.0,
                "verdict": "PASS",
                "feedback": "(critique parse error — accepted as-is)",
            }


# ═══════════════════════════════════════════════════════════════════════════
# Analyst Agent — the main orchestrator (now SearchPlan-aware)
# ═══════════════════════════════════════════════════════════════════════════
class AnalystAgent:
    """LLM-powered orchestrator: plan → delegate → critique → refine → synthesise.

    Now accepts a SearchPlan to parameterize all system prompts.
    """

    def __init__(
        self,
        llm: GeminiClient,
        memory: GraphMemory,
        news_agent: NewsAndDataAgent,
        finance_agent: FinancialModelerAgent,
        critique_module: CritiqueModule,
        cfg: AppConfig,
        search_plan: "SearchPlan | None" = None,
    ) -> None:
        self._llm = llm
        self.memory = memory
        self.news_agent = news_agent
        self.finance_agent = finance_agent
        self.critique = critique_module
        self._cfg = cfg
        self._plan = search_plan
        self._trace: list[StepResult] = []

    def set_search_plan(self, plan: "SearchPlan") -> None:
        self._plan = plan

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
                "Fetch latest data for the companies in the analysis.",
                "Extract relevant financial metrics.",
                "Compute comparative metrics.",
                "Analyse qualitative signals and trends.",
                "Perform strategic analysis.",
                "Generate actionable recommendations.",
                "Critique and refine.",
                "Synthesise final report.",
            ]
        self._log_step("Plan", f"{len(steps)} steps generated by LLM")
        return steps

    # ── 2. Data extraction via LLM (dynamic schema) ─────────────────
    def extract_financials(self, context: str) -> dict:
        if not self._plan:
            raise RuntimeError("SearchPlan not set on AnalystAgent")
        prompt = f"RAW DATA:\n{context[:6000]}"
        extraction_system = build_extraction_system(self._plan)
        extraction_schema = build_extraction_schema(self._plan)
        resp = self._llm.generate(prompt, system=extraction_system,
                                   json_mode=True, response_schema=extraction_schema,
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

    # ── 3. Strategic analysis via LLM (dynamic prompt) ───────────────
    def analyse_threat(self, financials: dict, signals_context: str, memory_context: str) -> str:
        if not self._plan:
            raise RuntimeError("SearchPlan not set on AnalystAgent")
        prompt = (
            f"FINANCIAL DATA:\n{json.dumps(financials, indent=2)[:3000]}\n\n"
            f"QUALITATIVE SIGNALS:\n{signals_context[:2000]}\n\n"
            f"STRATEGIC MEMORY:\n{memory_context}"
        )
        threat_system = build_threat_system(self._plan)
        resp = self._llm.generate(prompt, system=threat_system,
                                   temperature=0.2, max_tokens=1024)
        self._log_step("Analysis", resp.text[:120])
        return resp.text

    # ── 4. Strategy generation via LLM (dynamic prompt) ──────────────
    def generate_strategies(self, threat: str, context: str) -> list[dict]:
        if not self._plan:
            raise RuntimeError("SearchPlan not set on AnalystAgent")
        prompt = (
            f"ANALYSIS:\n{threat}\n\n"
            f"MARKET CONTEXT:\n{context[:2000]}"
        )
        strategy_system = build_strategy_system(self._plan)
        resp = self._llm.generate(prompt, system=strategy_system,
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

    # ── 6. Executive summary via LLM (dynamic prompt) ───────────────
    def synthesise_summary(self, threat: str, strategies: list[dict]) -> str:
        if not self._plan:
            raise RuntimeError("SearchPlan not set on AnalystAgent")
        prompt = (
            f"ANALYSIS:\n{threat}\n\n"
            f"STRATEGIES:\n{json.dumps(strategies, indent=2)[:2000]}"
        )
        synthesis_system = build_synthesis_system(self._plan)
        resp = self._llm.generate(prompt, system=synthesis_system,
                                   temperature=0.2, max_tokens=1024)
        self._log_step("Synthesis", "Executive summary generated")
        return resp.text

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

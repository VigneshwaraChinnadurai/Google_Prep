"""
Orchestrator — session-aware conversation manager with dynamic pipeline.

This module bridges the stateless Gradio UI and the stateful agentic
pipeline. It manages:
  1. **Session state** — LLM client, retrieval pipeline, knowledge graph,
     conversation history persist across chat turns.
  2. **Three response modes:**
     - Quick Chat:    Direct Gemini call with conversation context
     - Deep Analysis: Full agentic pipeline with streaming progress
     - Follow-up:     RAG query against already-built index
  3. **Dynamic query planning** — SearchPlan generated from user prompt
     drives ALL data fetching and analysis (no hardcoded domains).
  4. **Streaming generator pattern** — Deep analysis yields progressive
     status updates. Gradio replaces the displayed message on each yield.

Architecture: Local pipeline files
-----------------------------------
All pipeline modules (agents, retrieval, llm_client, config, etc.) are
now local copies in usecase_4. No sys.path hack or usecase_3 imports.
The query_planner.py module dynamically generates SearchPlans from ANY
user question, making the pipeline truly question-adaptive.

Ref: query_planner.py — SearchPlan generation
"""
from __future__ import annotations

import json
import logging
import sys
import time
import threading
from datetime import datetime
from pathlib import Path
from typing import Any, Callable, Generator

# ═══════════════════════════════════════════════════════════════════════════
# Local imports — all modules live in usecase_4 now
# ═══════════════════════════════════════════════════════════════════════════
_BASE_DIR = Path(__file__).resolve().parent
if str(_BASE_DIR) not in sys.path:
    sys.path.insert(0, str(_BASE_DIR))

from config import load_config                                   # noqa: E402
from cost_guard import CostGuard, BudgetExceeded                 # noqa: E402
from llm_client import GeminiClient, enable_prompt_logging       # noqa: E402
from agents import (                                             # noqa: E402
    AnalystAgent,
    CritiqueModule,
    FinancialModelerAgent,
    GraphMemory,
    NewsAndDataAgent,
)
from query_planner import generate_search_plan, SearchPlan       # noqa: E402

logger = logging.getLogger(__name__)


# ═══════════════════════════════════════════════════════════════════════════
# Heartbeat helper — keeps SSE connections alive during long blocking calls
#
# Problem: fetch_and_index() in Step 2 blocks for 60-90 seconds.
# Gradio streams via Server-Sent Events (SSE). If the generator doesn't
# yield for too long, the browser's EventSource can time out:
#   "Connection to the server was lost. Attempting reconnection..."
#
# Solution: run the blocking call in a background thread and yield
# heartbeat status messages every N seconds from the main thread.
# The heartbeat keeps the SSE connection alive; the actual result is
# delivered once the thread completes.
#
# Ref: https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events
# ═══════════════════════════════════════════════════════════════════════════
class _HeartbeatTick:
    """Sentinel yielded by ``_run_with_heartbeat`` on each tick.

    Callers use ``isinstance(x, _HeartbeatTick)`` to distinguish
    heartbeat ticks from the actual function return value.
    """
    __slots__ = ("message",)

    def __init__(self, message: str) -> None:
        self.message = message


# ═══════════════════════════════════════════════════════════════════════════
# Chat Session — holds all state for one user conversation
# ═══════════════════════════════════════════════════════════════════════════
class ChatSession:
    """
    Manages a single user session with persistent state across turns.

    Lifecycle:
        session = ChatSession(llm, cfg, guard)
        response = session.quick_chat("What is Google Cloud?")
        for chunk in session.deep_analysis("Analyze GC vs Azure"):
            display(chunk)  # streaming
        for chunk in session.follow_up("What about AWS?"):
            display(chunk)  # reuses existing index

    Thread safety:
        This class is NOT thread-safe. For a local single-user chatbot,
        that's fine. For multi-user production, each user needs their own
        ChatSession instance (store in Gradio's session state).

    Ref: https://www.gradio.app/guides/state-in-blocks
    """

    def __init__(
        self,
        llm: GeminiClient,
        cfg,
        guard: CostGuard,
        *,
        max_history_turns: int = 10,
        heartbeat_interval_sec: float = 10.0,
    ) -> None:
        self._llm = llm
        self._cfg = cfg
        self._guard = guard

        # ── Unique session ID for log correlation ────────────────
        self._session_id = datetime.now().strftime("%Y%m%d_%H%M%S")
        self._turn_counter = 0  # increments on every user message

        # ── Pipeline behaviour (from chatbot_config.yaml) ────────
        self._heartbeat_interval = heartbeat_interval_sec

        # ── State that persists across conversation turns ────────
        # GraphMemory: starts empty, gains edges during deep analysis.
        # Ref: agents.py GraphMemory class
        self._memory = GraphMemory()

        # NewsAndDataAgent: created on first deep analysis, then reused
        # for follow-up queries (the index persists in memory).
        self._news_agent: NewsAndDataAgent | None = None

        # Flags / cached results
        self._index_built = False
        self._analysis_result: dict | None = None

        # Conversation history: stores user/assistant turns for context.
        # Limited to last N turns to avoid exceeding context window.
        self._conversation: list[dict[str, str]] = []
        self._max_history_turns = max_history_turns

        logger.info(
            "[session=%s] ChatSession.__init__: memory=%d edges, "
            "index_built=%s, max_history=%d, heartbeat=%.1fs",
            self._session_id, len(self._memory.all_edges()),
            self._index_built, self._max_history_turns,
            self._heartbeat_interval,
        )

    # ═══════════════════════════════════════════════════════════════════
    # Heartbeat helper — runs a blocking function in a thread
    #
    # Usage (inside a generator):
    #     for tick_or_result in self._run_with_heartbeat(fn, ...):
    #         if isinstance(tick_or_result, _HeartbeatTick):
    #             yield _emit(tick_or_result.message)  # keep SSE alive
    #         else:
    #             actual_result = tick_or_result
    # ═══════════════════════════════════════════════════════════════════
    def _run_with_heartbeat(
        self,
        fn: Callable[..., Any],
        *args: Any,
        heartbeat_msg: str = "   ⏳ Working... ({elapsed:.0f}s)",
        heartbeat_interval: float | None = None,
    ) -> Generator[_HeartbeatTick | Any, None, None]:
        """Run *fn* in a background thread, yielding heartbeat ticks.

        Args:
            fn: Blocking callable to execute.
            *args: Positional arguments for *fn*.
            heartbeat_msg: Format string with ``{elapsed}`` placeholder.
            heartbeat_interval: Seconds between ticks (default: from config).

        Yields:
            ``_HeartbeatTick`` objects periodically, then the actual return
            value of *fn* as the final yield.

        Raises:
            Re-raises any exception from *fn* in the caller's thread.
        """
        interval = heartbeat_interval or self._heartbeat_interval
        result_holder: list = []          # [return_value]
        error_holder: list = []           # [exception]
        done = threading.Event()

        def _worker() -> None:
            try:
                result_holder.append(fn(*args))
            except Exception as exc:
                error_holder.append(exc)
            finally:
                done.set()

        t = threading.Thread(target=_worker, daemon=True)
        t.start()
        t0 = time.perf_counter()

        while not done.wait(timeout=interval):
            elapsed = time.perf_counter() - t0
            yield _HeartbeatTick(heartbeat_msg.format(elapsed=elapsed))

        # Thread finished — propagate error or yield result
        if error_holder:
            raise error_holder[0]
        yield result_holder[0]

    # ═══════════════════════════════════════════════════════════════════
    # Mode 1: Quick Chat — direct LLM call with conversation context
    # ═══════════════════════════════════════════════════════════════════
    def quick_chat(self, message: str) -> str:
        """
        Direct Gemini LLM call with conversation history as context.

        This mode is for casual questions that don't need the full RAG +
        agent pipeline. The LLM uses:
          - Conversation history (last 10 turns)
          - Any existing memory edges (from prior deep analysis)
          - Any prior analysis summary (if deep analysis was run)

        Returns the complete response as a string (no streaming).

        Cost: ~$0.0002 per call (120 in / 200 out tokens typical).
        """
        self._turn_counter += 1
        turn = self._turn_counter
        logger.info(
            "[session=%s][turn=%d] QUICK_CHAT request: prompt_len=%d, "
            "prompt_preview=%.120s",
            self._session_id, turn, len(message), message,
        )
        self._conversation.append({"role": "user", "content": message})

        # ── Build contextual system prompt ───────────────────────
        # We inject prior analysis results and memory edges into the
        # system prompt so the LLM has context from earlier turns.
        context_parts = []

        if self._memory.all_edges():
            context_parts.append(
                f"Known strategic relationships:\n{self._memory.format()}"
            )

        if self._analysis_result:
            summary = self._analysis_result.get("executive_summary", "")
            if summary:
                context_parts.append(
                    f"Previous deep analysis summary:\n{summary[:1500]}"
                )

        system = (
            "You are a strategic analysis assistant. You can analyse any "
            "industry, company, or strategic topic. Provide thoughtful, "
            "data-aware responses. If the user asks for detailed data-backed "
            "analysis, suggest they switch to **Deep Analysis** mode.\n\n"
            + ("\n\n".join(context_parts) if context_parts else "")
        )
        logger.info(
            "[session=%s][turn=%d] QUICK_CHAT system_prompt: len=%d, "
            "context_parts=%d, memory_edges=%d, has_prior_analysis=%s",
            self._session_id, turn, len(system), len(context_parts),
            len(self._memory.all_edges()), bool(self._analysis_result),
        )

        # ── Build conversation context for the user prompt ───────
        # Include recent conversation turns so the LLM maintains
        # coherent multi-turn dialogue.
        # Ref: https://ai.google.dev/gemini-api/docs/text-generation
        recent = self._conversation[-self._max_history_turns:]
        history_text = "\n".join(
            f"{m['role'].upper()}: {m['content']}" for m in recent
        )

        t0 = time.perf_counter()
        resp = self._llm.generate(
            history_text,
            system=system,
            temperature=0.3,
            max_tokens=2048,
        )
        elapsed = time.perf_counter() - t0
        logger.info(
            "[session=%s][turn=%d] QUICK_CHAT response: elapsed=%.2fs, "
            "response_len=%d, cost=$%.4f, api_calls=%d",
            self._session_id, turn, elapsed, len(resp.text),
            self._guard.total_cost_usd, self._guard.api_calls,
        )

        self._conversation.append({"role": "assistant", "content": resp.text})
        return resp.text

    # ═══════════════════════════════════════════════════════════════════
    # Mode 2: Deep Analysis — full agentic pipeline with streaming
    # ═══════════════════════════════════════════════════════════════════
    def deep_analysis(self, prompt: str) -> Generator[str, None, None]:
        """
        Run the complete agentic pipeline from usecase_3 with real-time
        status updates streamed to the UI.

        This is a Python generator:
        - Each ``yield`` sends the COMPLETE message displayed so far.
        - Gradio replaces the assistant's message on each yield.
        - No partial tokens — we yield after each pipeline STEP completes.

        Pipeline steps:
            1. Plan (LLM decomposes the prompt)
            2. Fetch real data (web, SEC EDGAR, news)
            3. Index (chunk, embed, build hybrid index)
            4. Retrieve financial context → LLM extract financials
            5. Compute market share delta (generated Python tool)
            6. Retrieve qualitative signals
            7. LLM threat analysis
            8. LLM self-critique (quality gate)
            8b. [conditional] Refinement loop
            9. LLM generate strategies
            10. LLM extract memory edges + executive summary

        Ref: https://www.gradio.app/guides/creating-a-chatbot-fast#streaming
        """
        self._turn_counter += 1
        turn = self._turn_counter
        pipeline_t0 = time.perf_counter()
        logger.info(
            "[session=%s][turn=%d] DEEP_ANALYSIS request: prompt_len=%d, "
            "prompt_preview=%.120s",
            self._session_id, turn, len(prompt), prompt,
        )
        self._conversation.append({"role": "user", "content": prompt})
        accumulated = ""

        def _emit(text: str) -> str:
            """Append text to the running message and return the full message.

            The generator yields this return value.  Gradio replaces the
            assistant bubble with the latest value on each yield.
            """
            nonlocal accumulated
            accumulated += text + "\n"
            return accumulated

        # ── Seed memory if first analysis ────────────────────────
        # Dynamic seeds from SearchPlan (generated by query_planner)
        # instead of hardcoded cloud-market relationships.
        # Seeds are generated AFTER query planning (Step 0 below).

        # ── Step 0: Query Planning (NEW — dynamic) ──────────────
        yield _emit("🧠 **Step 0:** Understanding your question...")
        step_t0 = time.perf_counter()
        search_plan = generate_search_plan(prompt, self._llm)
        logger.info(
            "[session=%s][turn=%d] PIPELINE step=0 action=query_plan "
            "domain=%s companies=%s queries=%d elapsed=%.2fs cost=$%.4f",
            self._session_id, turn, search_plan.domain,
            [c.get("ticker", "?") for c in search_plan.companies],
            len(search_plan.grounding_queries),
            time.perf_counter() - step_t0, self._guard.total_cost_usd,
        )
        yield _emit(
            f"   ✅ Domain: **{search_plan.domain}** | "
            f"Companies: {', '.join(c.get('ticker', '?') for c in search_plan.companies)} | "
            f"Queries planned: {len(search_plan.grounding_queries)}\n"
        )

        # Seed memory from the search plan
        if not self._memory.all_edges():
            for seed in search_plan.memory_seeds:
                self._memory.add_edge(
                    seed.get("source", ""),
                    seed.get("relation", ""),
                    seed.get("target", ""),
                )
            if search_plan.memory_seeds:
                logger.info(
                    "[session=%s][turn=%d] PIPELINE memory seeded: %d edges from plan",
                    self._session_id, turn, len(search_plan.memory_seeds),
                )

        # ── Build agent team ─────────────────────────────────────
        # NewsAndDataAgent now receives the SearchPlan for dynamic fetching.
        self._news_agent = NewsAndDataAgent(self._llm, self._cfg)
        self._news_agent.set_search_plan(search_plan)
        finance_agent = FinancialModelerAgent(_BASE_DIR)
        critique_mod = CritiqueModule(self._llm)

        analyst = AnalystAgent(
            llm=self._llm,
            memory=self._memory,
            news_agent=self._news_agent,
            finance_agent=finance_agent,
            critique_module=critique_mod,
            cfg=self._cfg,
            search_plan=search_plan,
        )

        try:
            # ── Step 1: Planning ─────────────────────────────────
            yield _emit("🔍 **Step 1/10:** Planning analysis steps...")
            step_t0 = time.perf_counter()
            plan = analyst.build_plan(prompt)
            logger.info(
                "[session=%s][turn=%d] PIPELINE step=1/10 action=plan "
                "steps=%d elapsed=%.2fs cost=$%.4f",
                self._session_id, turn, len(plan),
                time.perf_counter() - step_t0, self._guard.total_cost_usd,
            )
            yield _emit(f"   ✅ Generated {len(plan)} steps\n")

            # ── Step 2: Real data fetching ───────────────────────
            # This step is the LONGEST in the pipeline (~60-90 seconds):
            #   - 6 grounded web searches via Gemini
            #   - 3 SEC EDGAR API calls
            #   - 2 SEC full-text searches
            #   - RSS news feeds
            #   - Chunking + embedding 200+ documents
            #
            # Problem: fetch_and_index() is a BLOCKING call. If the
            # generator doesn't yield for >60s, the SSE connection
            # between browser/Gradio can time out, showing:
            #   "Connection to the server was lost."
            #
            # Solution: run the blocking call in a background thread and
            # yield heartbeat status updates every few seconds to keep
            # the SSE connection alive.
            #
            # Ref: https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events
            yield _emit(
                f"📡 **Step 2/10:** Fetching real-time data "
                f"({len(search_plan.grounding_queries)} web searches, "
                f"{len(search_plan.ticker_to_cik)} SEC filings, "
                f"{len(search_plan.news_queries)} news feeds)..."
            )
            step_t0 = time.perf_counter()

            # Run blocking fetch_and_index in a thread with heartbeat.
            # heartbeat_interval comes from chatbot_config.yaml → pipeline.heartbeat_interval_sec
            for heartbeat_or_result in self._run_with_heartbeat(
                self._news_agent.fetch_and_index,
                heartbeat_msg="   ⏳ Still fetching data... ({elapsed:.0f}s)",
            ):
                if isinstance(heartbeat_or_result, _HeartbeatTick):
                    yield _emit(heartbeat_or_result.message)
                else:
                    chunk_count = heartbeat_or_result

            self._index_built = True
            logger.info(
                "[session=%s][turn=%d] PIPELINE step=2/10 action=fetch_and_index "
                "chunks=%d documents=%d elapsed=%.2fs cost=$%.4f",
                self._session_id, turn, chunk_count,
                self._news_agent.document_count,
                time.perf_counter() - step_t0, self._guard.total_cost_usd,
            )
            yield _emit(
                f"   ✅ Indexed {chunk_count} chunks "
                f"from {self._news_agent.document_count} documents\n"
            )

            # ── Step 3: Financial retrieval + extraction ─────────
            yield _emit(
                "📊 **Step 3/10:** Retrieving financial context "
                "(hybrid search → rerank → fuse)..."
            )
            step_t0 = time.perf_counter()
            fin_context = self._news_agent.retrieve(
                search_plan.financial_retrieval_query
                or f"{search_plan.domain} quarterly revenue growth operating income"
            )
            logger.info(
                "[session=%s][turn=%d] PIPELINE step=3a/10 action=retrieve_financials "
                "context_len=%d elapsed=%.2fs",
                self._session_id, turn, len(fin_context),
                time.perf_counter() - step_t0,
            )
            yield _emit("   ✅ Financial context retrieved")
            yield _emit("   ⏳ Extracting structured metrics via LLM...")
            step_t0 = time.perf_counter()
            financials = analyst.extract_financials(fin_context)
            logger.info(
                "[session=%s][turn=%d] PIPELINE step=3b/10 action=extract_financials "
                "elapsed=%.2fs cost=$%.4f",
                self._session_id, turn,
                time.perf_counter() - step_t0, self._guard.total_cost_usd,
            )
            yield _emit("   ✅ Financial data extracted\n")

            # ── Step 4: Market share delta (dynamic tool) ────────
            yield _emit(
                "🧮 **Step 4/10:** Computing market share deltas "
                "(dynamic Python tool)..."
            )
            step_t0 = time.perf_counter()
            share_delta = analyst._compute_share_delta(financials)
            logger.info(
                "[session=%s][turn=%d] PIPELINE step=4/10 action=share_delta "
                "result=%s elapsed=%.2fs",
                self._session_id, turn, json.dumps(share_delta),
                time.perf_counter() - step_t0,
            )
            yield _emit(f"   ✅ {json.dumps(share_delta)}\n")

            # ── Step 5: Quali signals + memory context ───────────
            yield _emit("🔎 **Step 5/10:** Retrieving competitive signals...")
            step_t0 = time.perf_counter()
            signals_context = self._news_agent.retrieve(
                search_plan.qualitative_retrieval_query
                or f"{search_plan.domain} {search_plan.focus_topic} competitive signals trends"
            )
            memory_ctx = self._memory.format()
            logger.info(
                "[session=%s][turn=%d] PIPELINE step=5/10 action=retrieve_signals "
                "context_len=%d memory_edges=%d elapsed=%.2fs",
                self._session_id, turn, len(signals_context),
                len(self._memory.all_edges()),
                time.perf_counter() - step_t0,
            )
            yield _emit("   ✅ Qualitative signals retrieved\n")

            # ── Step 6: Threat analysis ──────────────────────────
            yield _emit("⚡ **Step 6/10:** Synthesising strategic analysis...")
            step_t0 = time.perf_counter()
            threat = analyst.analyse_threat(financials, signals_context, memory_ctx)
            logger.info(
                "[session=%s][turn=%d] PIPELINE step=6/10 action=threat_analysis "
                "threat_len=%d elapsed=%.2fs cost=$%.4f",
                self._session_id, turn, len(threat),
                time.perf_counter() - step_t0, self._guard.total_cost_usd,
            )
            yield _emit("   ✅ Strategic analysis complete\n")

            # ── Step 7: Critique (quality gate) ──────────────────
            yield _emit("📝 **Step 7/10:** Self-critique (quality gate)...")
            conclusion = (
                f"Market share delta: {json.dumps(share_delta)}\n\n"
                f"Threat: {threat}"
            )
            step_t0 = time.perf_counter()
            critique = analyst.critique.critique(conclusion, signals_context)
            score = critique.get("overall_score", 0)
            verdict = critique.get("verdict", "PASS")
            logger.info(
                "[session=%s][turn=%d] PIPELINE step=7/10 action=critique "
                "score=%.1f verdict=%s feedback=%.100s elapsed=%.2fs cost=$%.4f",
                self._session_id, turn, score, verdict,
                critique.get("feedback", ""),
                time.perf_counter() - step_t0, self._guard.total_cost_usd,
            )
            icon = "✅" if verdict == "PASS" else "⚠️"
            yield _emit(f"   {icon} Score: {score}/10 — {verdict}\n")

            # ── Step 7b: Refinement loop (if critique < 7) ──────
            # This is the KEY agentic behaviour: the system automatically
            # improves its own output when quality is insufficient.
            max_loops = self._cfg.agent.max_critique_loops
            loop = 0
            while verdict == "NEEDS_REFINEMENT" and loop < max_loops:
                loop += 1
                logger.info(
                    "[session=%s][turn=%d] PIPELINE step=7b action=refinement_loop "
                    "loop=%d/%d",
                    self._session_id, turn, loop, max_loops,
                )
                yield _emit(
                    f"🔄 **Refinement {loop}/{max_loops}:** "
                    "Generating deeper evidence queries..."
                )

                # Ask LLM what data is missing
                ref_resp = self._llm.generate(
                    f"CRITIQUE FEEDBACK:\n{critique.get('feedback', '')}",
                    system=(
                        "You are a research coordinator. Given critique feedback "
                        "on a strategic analysis, generate 1-2 specific search "
                        "queries that would fetch the missing evidence."
                        '\n\nReturn ONLY JSON: {"queries": ["query 1", "query 2"]}'
                    ),
                    json_mode=True,
                    thinking_budget=0,
                    temperature=0.0,
                    max_tokens=512,
                )
                try:
                    queries = GeminiClient.parse_json(ref_resp.text).get("queries", [])
                except (json.JSONDecodeError, TypeError):
                    queries = []

                # Retrieve extra context and re-analyse
                extra_parts = []
                for q in queries[:2]:
                    extra_parts.append(self._news_agent.retrieve(q))

                if extra_parts:
                    enriched = signals_context + "\n\n" + "\n\n".join(extra_parts)
                    threat = analyst.analyse_threat(
                        financials, enriched, memory_ctx
                    )
                    conclusion = (
                        f"Market share delta: {json.dumps(share_delta)}\n\n"
                        f"Threat: {threat}"
                    )

                critique = analyst.critique.critique(conclusion, signals_context)
                score = critique.get("overall_score", 0)
                verdict = critique.get("verdict", "PASS")
                logger.info(
                    "[session=%s][turn=%d] PIPELINE step=7b action=re-critique "
                    "loop=%d score=%.1f verdict=%s cost=$%.4f",
                    self._session_id, turn, loop, score, verdict,
                    self._guard.total_cost_usd,
                )
                icon = "✅" if verdict == "PASS" else "⚠️"
                yield _emit(f"   {icon} Refined score: {score}/10 — {verdict}\n")

            # ── Step 8: Strategy generation ──────────────────────
            yield _emit("💡 **Step 8/10:** Generating strategic recommendations...")
            step_t0 = time.perf_counter()
            strategies = analyst.generate_strategies(threat, signals_context)
            logger.info(
                "[session=%s][turn=%d] PIPELINE step=8/10 action=strategies "
                "count=%d elapsed=%.2fs cost=$%.4f",
                self._session_id, turn, len(strategies),
                time.perf_counter() - step_t0, self._guard.total_cost_usd,
            )
            yield _emit(f"   ✅ {len(strategies)} strategies generated\n")

            # ── Step 9: Memory extraction ────────────────────────
            yield _emit("🧠 **Step 9/10:** Extracting knowledge graph edges...")
            step_t0 = time.perf_counter()
            edges_before = len(self._memory.all_edges())
            analyst.extract_memory(
                threat + "\n" + json.dumps(strategies, indent=2)[:1500]
            )
            edge_count = len(self._memory.all_edges())
            logger.info(
                "[session=%s][turn=%d] PIPELINE step=9/10 action=memory_extract "
                "edges_before=%d edges_after=%d new_edges=%d elapsed=%.2fs",
                self._session_id, turn, edges_before, edge_count,
                edge_count - edges_before, time.perf_counter() - step_t0,
            )
            yield _emit(f"   ✅ {edge_count} total edges in memory\n")

            # ── Step 10: Executive summary ───────────────────────
            yield _emit("📋 **Step 10/10:** Writing executive summary...")
            step_t0 = time.perf_counter()
            summary = analyst.synthesise_summary(threat, strategies)
            pipeline_elapsed = time.perf_counter() - pipeline_t0
            logger.info(
                "[session=%s][turn=%d] PIPELINE step=10/10 action=summary "
                "summary_len=%d step_elapsed=%.2fs",
                self._session_id, turn, len(summary),
                time.perf_counter() - step_t0,
            )
            logger.info(
                "[session=%s][turn=%d] DEEP_ANALYSIS complete: "
                "total_elapsed=%.2fs total_cost=$%.4f total_api_calls=%d "
                "memory_edges=%d",
                self._session_id, turn, pipeline_elapsed,
                self._guard.total_cost_usd, self._guard.api_calls,
                len(self._memory.all_edges()),
            )
            yield _emit("   ✅ Complete!\n")

            # ── Store result for follow-up queries ───────────────
            self._analysis_result = {
                "plan": plan,
                "financials": financials,
                "share_delta": share_delta,
                "threat_statement": threat,
                "strategies": strategies,
                "critique": critique,
                "executive_summary": summary,
                "memory_edges": self._memory.all_edges(),
            }

            # ── Format the final report ──────────────────────────
            yield _emit("\n---\n")
            yield _emit(f"## 📄 Executive Summary\n\n{summary}\n")
            yield _emit(f"## ⚡ Key Analysis\n\n{threat}\n")

            yield _emit("## 💡 Strategies\n")
            for i, s in enumerate(strategies, 1):
                yield _emit(f"### {i}. {s.get('name', '?')}")
                for a in s.get("actions", []):
                    yield _emit(f"- {a}")
                yield _emit(
                    f"\n**Cost:** {s.get('cost', '?')} | "
                    f"**Outcome:** {s.get('expected_outcome', '?')}\n"
                )

            yield _emit(
                f"\n---\n*💰 Total cost: ${self._guard.total_cost_usd:.4f} "
                f"({self._guard.api_calls} API calls)*"
            )

            self._conversation.append({"role": "assistant", "content": accumulated})

        except BudgetExceeded as e:
            # Budget guard triggered — show graceful message
            logger.warning(
                "[session=%s][turn=%d] DEEP_ANALYSIS budget_exceeded: %s "
                "spent=$%.4f budget=$%.2f",
                self._session_id, turn, e,
                self._guard.total_cost_usd, self._guard.budget_usd,
            )
            yield _emit(
                f"\n⚠️ **Budget Exceeded:** {e}\n\n"
                f"💰 Spent: ${self._guard.total_cost_usd:.4f} / "
                f"${self._guard.budget_usd:.2f}"
            )

        except Exception as e:
            # Catch-all for unexpected errors — log + display
            logger.exception(
                "[session=%s][turn=%d] DEEP_ANALYSIS failed: %s",
                self._session_id, turn, e,
            )
            yield _emit(f"\n❌ **Error:** {type(e).__name__}: {e}")

    # ═══════════════════════════════════════════════════════════════════
    # Mode 3: Follow-up — RAG query on already-built index
    # ═══════════════════════════════════════════════════════════════════
    def follow_up(self, question: str) -> Generator[str, None, None]:
        """
        Answer a follow-up question using the existing retrieval index.

        This mode is available after at least one Deep Analysis has been
        run. It reuses the already-built index (no re-fetching, no
        re-embedding) and the accumulated memory edges.

        Cost: ~$0.001 per follow-up (reranker + generation, no embedding).

        Ref: agents.py — NewsAndDataAgent.retrieve()
        """
        self._turn_counter += 1
        turn = self._turn_counter

        if not self._index_built or self._news_agent is None:
            logger.info(
                "[session=%s][turn=%d] FOLLOW_UP rejected: no index built",
                self._session_id, turn,
            )
            yield (
                "ℹ️ No analysis index available yet. "
                "Run a **Deep Analysis** first to build the retrieval index, "
                "or use **Quick Chat** for general questions."
            )
            return

        logger.info(
            "[session=%s][turn=%d] FOLLOW_UP request: prompt_len=%d, "
            "prompt_preview=%.120s",
            self._session_id, turn, len(question), question,
        )
        self._conversation.append({"role": "user", "content": question})
        accumulated = ""

        def _emit(text: str) -> str:
            nonlocal accumulated
            accumulated += text + "\n"
            return accumulated

        yield _emit("🔎 Searching existing index...")
        step_t0 = time.perf_counter()
        context = self._news_agent.retrieve(question)
        logger.info(
            "[session=%s][turn=%d] FOLLOW_UP index_search: context_len=%d "
            "elapsed=%.2fs",
            self._session_id, turn, len(context),
            time.perf_counter() - step_t0,
        )
        yield _emit("✅ Found relevant chunks. Generating answer...\n")

        # ── Build a rich prompt with all available context ────────
        system = (
            "You are a strategic analysis assistant. Answer the user's "
            "follow-up question using the provided context and prior analysis. "
            "Be specific, cite data where available.\n\n"
            f"Known relationships:\n{self._memory.format()}\n\n"
        )
        if self._analysis_result:
            system += (
                "Previous analysis summary:\n"
                f"{self._analysis_result.get('executive_summary', '(none)')}"
            )

        prompt = f"RETRIEVED CONTEXT:\n{context[:3000]}\n\nQUESTION: {question}"
        logger.info(
            "[session=%s][turn=%d] FOLLOW_UP system_prompt: len=%d, "
            "memory_edges=%d, has_prior_analysis=%s",
            self._session_id, turn, len(system),
            len(self._memory.all_edges()), bool(self._analysis_result),
        )

        step_t0 = time.perf_counter()
        resp = self._llm.generate(
            prompt,
            system=system,
            temperature=0.3,
            max_tokens=2048,
        )
        logger.info(
            "[session=%s][turn=%d] FOLLOW_UP response: elapsed=%.2fs "
            "response_len=%d cost=$%.4f api_calls=%d",
            self._session_id, turn, time.perf_counter() - step_t0,
            len(resp.text), self._guard.total_cost_usd, self._guard.api_calls,
        )
        yield _emit(f"\n{resp.text}")
        yield _emit(
            f"\n---\n*💰 Cost: ${self._guard.total_cost_usd:.4f} "
            f"({self._guard.api_calls} API calls)*"
        )

        self._conversation.append({"role": "assistant", "content": accumulated})

    # ═══════════════════════════════════════════════════════════════════
    # Status helpers for the sidebar
    # ═══════════════════════════════════════════════════════════════════
    def cost_summary(self) -> str:
        """Format cost for the Gradio sidebar Markdown component."""
        return (
            f"**💰 Cost:** ${self._guard.total_cost_usd:.4f} "
            f"({self._guard.api_calls} API calls)"
        )

    def session_info(self) -> str:
        """Format session state for the Gradio sidebar Markdown component."""
        parts = []
        if self._index_built and self._news_agent:
            parts.append(f"📄 {self._news_agent.document_count} docs indexed")
        edges = self._memory.all_edges()
        if edges:
            parts.append(f"🧠 {len(edges)} memory edges")
        if self._analysis_result:
            parts.append("✅ Analysis available")
        if not parts:
            parts.append("No analysis run yet")
        return "**📊 Session:** " + " | ".join(parts)


# ═══════════════════════════════════════════════════════════════════════════
# Factory function — creates a ready-to-use ChatSession
# ═══════════════════════════════════════════════════════════════════════════
def create_session(
    *,
    budget_usd: float = 0.50,
    dry_run: bool = False,
    max_history_turns: int = 10,
    heartbeat_interval_sec: float = 10.0,
) -> ChatSession:
    """
    Create a new ChatSession with all dependencies wired up.

    This is the single entry point for the Gradio app. It:
      1. Loads config (resolves Gemini API key from .env / local.properties)
      2. Creates CostGuard with budget cap
      3. Creates GeminiClient with disk cache + cost guard
      4. Enables prompt logging to outputs/chatbot_prompt_log_{timestamp}.jsonl
      5. Returns a ChatSession ready for chat / analysis / follow-up

    Args:
        budget_usd:             Hard spending cap (default $0.50).
        dry_run:                If True, all LLM calls return placeholders.
        max_history_turns:      Conversation turns kept in memory (from config).
        heartbeat_interval_sec: Seconds between SSE heartbeat ticks (from config).

    Ref: config.py — load_config()
    Ref: llm_client.py — GeminiClient
    Ref: chatbot_config.yaml — pipeline section
    """
    # ── Load configuration (API key resolved from .env / local.properties)
    cfg = load_config(_BASE_DIR, budget_usd=budget_usd, dry_run=dry_run)
    guard = CostGuard(budget_usd=cfg.billing.budget_usd, dry_run=cfg.billing.dry_run)

    # ── LLM client with disk cache (shared with usecase_3) ───────
    # Cache is at usecase_3/.cache/gemini/ — reusing it means
    # previously cached responses are free for both projects.
    cache_path = _BASE_DIR / ".cache" / "gemini"
    llm = GeminiClient(cfg.gemini, cache_dir=str(cache_path), cost_guard=guard)

    # ── Enable prompt logging (timestamped, never overwrites) ────
    log_dir = Path(__file__).resolve().parent / "outputs"
    log_dir.mkdir(exist_ok=True)
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    enable_prompt_logging(str(log_dir / f"chatbot_prompt_log_{ts}.jsonl"))

    logger.info(
        "ChatSession created: model=%s budget=$%.2f dry_run=%s "
        "max_history=%d heartbeat=%.1fs",
        cfg.gemini.generation_model, guard.budget_usd, guard.dry_run,
        max_history_turns, heartbeat_interval_sec,
    )

    return ChatSession(
        llm, cfg, guard,
        max_history_turns=max_history_turns,
        heartbeat_interval_sec=heartbeat_interval_sec,
    )

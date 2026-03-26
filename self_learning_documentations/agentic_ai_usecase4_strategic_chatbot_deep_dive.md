# Usecase 4: Strategic Analysis Chatbot — Deep-Dive Execution Walkthrough

> **Purpose:** This document walks through every single part of the chatbot system — from `python main.py` to a live streaming conversation — with the **why** behind each design decision, actual code, real execution behaviour, and documentation references. It is written for someone who wants to *understand*, not just *use* the system.
>
> **What this builds on:** Usecase 4 is now a **fully standalone** chatbot with its own copies of the pipeline modules. It includes:
> - A **dynamic query planner** (`query_planner.py`) that generates search plans from ANY user question
> - **Dynamic agents** that adapt their prompts based on the user's domain (not hardcoded to cloud computing)
> - A Gradio UI + session orchestrator totaling **~2500 lines** of new/adapted code
>
> **Prompt Log:** All prompts are captured to `outputs/chatbot_prompt_log_{timestamp}.jsonl` — each entry contains the system prompt, user prompt, call type, and parameters.
>
> **Key Update (March 2026):** Previously usecase_4 imported from usecase_3 via `sys.path`. Now usecase_4 is **fully standalone** with dynamic, domain-agnostic pipeline that works for ANY strategic analysis question (not just cloud computing).

---

## Table of Contents

1. [The Problem We're Solving](#1-the-problem-were-solving)
2. [Why This Architecture? The Design Philosophy](#2-why-this-architecture-the-design-philosophy)
   - 2.1 Why Gradio?
   - 2.2 Why Standalone (Not Import)?
   - 2.3 Why a Session Class?
   - 2.4 Why Streaming Generators?
   - 2.5 Why Three Modes?
   - **2.6 The SearchPlan Revolution** (NEW)
3. [File-By-File: What Each File Does and Why](#3-file-by-file-what-each-file-does-and-why)
4. [Step-By-Step Execution Trace](#4-step-by-step-execution-trace)
5. [The Three Conversation Modes (Deep-Dive)](#5-the-three-conversation-modes-deep-dive)
6. [Best Practices Implemented (And Why)](#6-best-practices-implemented-and-why)
7. [Lessons Learned and Design Tradeoffs](#7-lessons-learned-and-design-tradeoffs)
8. [Cost Analysis](#8-cost-analysis)
9. [Documentation Links](#9-documentation-links)

---

## 1. The Problem We're Solving

**Business problem:** Usecase 3 produces excellent strategic analysis, but it's a CLI tool. You type `python main.py`, wait 60 seconds, and read a file. No interactivity. No follow-up questions. No casual conversation.

**Technical problem:** Build a system that:
- Puts a **conversational UI** on the agentic pipeline (no terminal expertise needed)
- Supports **three interaction modes** (direct chat, full pipeline, RAG follow-up)
- Shows **real-time streaming** progress during long analysis runs
- Maintains **session state** (memory, index, conversation history) across turns
- Reuses usecase_3's code via **composition** (not duplication)
- Is **cost-transparent** (live cost display in the UI)

### Why this specific use case?

This use case is the natural next step after usecase_3 because it forces you to learn:

| Concept | Where it shows up | Why it matters |
|---|---|---|
| **Gradio Blocks** | `main.py` — layout, events, themes | The standard way to prototype AI UIs in Python |
| **Streaming generators** | `orchestrator.py` → `main.py` | How chatbots show progressive responses (not just final text) |
| **Session management** | `ChatSession` class with persistent state | How to maintain context across multiple conversation turns |
| **Module composition** | `sys.path` import from usecase_3 | How to reuse code across projects without duplication |
| **Multi-turn context** | Conversation history injected into system prompts | How LLMs "remember" prior messages (they don't — you re-send context) |
| **Agent routing** | Mode selector → different code paths | How to build multi-modal chat interfaces |
| **RAG reuse** | Follow-up mode queries existing index | How to amortize expensive pipeline work across many questions |
| **Live cost engineering** | Sidebar cost display updated on every yield | How to make AI costs visible to users |

### What's new vs. usecase_3?

| Capability | Usecase 3 | Usecase 4 |
|---|---|---|
| Interface | CLI (`python main.py`) | Web UI (browser, `localhost:7860`) |
| Interaction | One-shot (prompt → report) | Multi-turn conversation |
| Progress visibility | Log file only | Real-time streaming in chat bubble |
| Follow-up questions | Impossible (pipeline resets) | Built-in (RAG on existing index) |
| Casual questions | N/A | Quick Chat mode (direct LLM) |
| Cost visibility | Printed at end | Live sidebar counter |
| State persistence | None (process exits) | In-memory across chat turns |

---

## 2. Why This Architecture? The Design Philosophy

### 2.1 Why Gradio (Not Flask, Streamlit, or React)?

This was a deliberate choice. Here's the comparison:

| Framework | Chat support | Streaming | Python-only? | Lines of code | Why not? |
|---|---|---|---|---|---|
| **Gradio** | ✅ Native `gr.Chatbot` | ✅ Generator yields | ✅ Zero JS/HTML needed | ~200 LOC | ✅ **We use this** |
| Streamlit | ⚠️ `st.chat_message` (workarounds) | ⚠️ Re-runs entire script | ✅ Python only | ~300 LOC | Re-execution model fights chat state |
| Flask + HTMX | ❌ Build from scratch | ⚠️ SSE or WebSocket manual | ❌ Need HTML/JS | ~600 LOC | Too much boilerplate for a learning project |
| React + FastAPI | ✅ Any chat lib | ✅ Full control | ❌ JS + Python | ~1200 LOC | Overkill — need Node.js, NPM, build tools |

**The key insight:** Gradio was built for AI applications. A streaming chatbot is ~50 lines:

```python
# This is literally all you need for a basic streaming chatbot:
import gradio as gr

def respond(msg, history):
    for token in generate_streaming(msg):
        yield token

gr.ChatInterface(fn=respond).launch()
```

We use `gr.Blocks` instead of `gr.ChatInterface` because we need a sidebar (mode selector, cost display, session info). `ChatInterface` is simpler but doesn't support custom layouts.

**Ref:** [Gradio Quickstart](https://www.gradio.app/guides/quickstart)
**Ref:** [Creating a Chatbot Fast](https://www.gradio.app/guides/creating-a-chatbot-fast)
**Ref:** [Streamlit Chat](https://docs.streamlit.io/develop/tutorials/llms/build-conversational-apps) (for comparison)

### 2.2 Why Standalone (Not Import from Usecase 3)?

**Architectural Evolution (March 2026):**

Previously, usecase_4 imported modules from usecase_3 via `sys.path.insert`. This had a critical limitation: **the pipeline was hardcoded to cloud computing analysis**. The data fetcher had hardcoded queries like "Google Cloud revenue growth", SEC CIKs for GOOGL/MSFT/AMZN, and system prompts mentioning "Microsoft Azure" and "Google Cloud".

**The problem:** When a user asked about "Nvidia and Qualcomm quantum computing R&D", the pipeline still fetched cloud computing data and analyzed Microsoft vs. Google!

**The solution:** Usecase 4 is now **fully standalone** with:

```python
# orchestrator.py — all imports are LOCAL
_BASE_DIR = Path(__file__).resolve().parent
if str(_BASE_DIR) not in sys.path:
    sys.path.insert(0, str(_BASE_DIR))

from config import load_config          # LOCAL copy
from agents import AnalystAgent, ...    # LOCAL dynamic version
from query_planner import SearchPlan    # NEW — dynamic plan generator
```

**New files added to usecase_4:**

| File | Purpose | Lines |
|---|---|---|
| `query_planner.py` | LLM-powered search plan generator from ANY user question | ~280 |
| `agents.py` | Dynamic system prompts based on SearchPlan | ~620 |
| `retrieval/web_fetcher.py` | Dynamic queries from SearchPlan | ~270 |
| `retrieval/metadata_enricher.py` | Dynamic company patterns from SearchPlan | ~110 |

**Before (hardcoded):**
```python
_GROUNDING_QUERIES = [
    "Google Cloud quarterly revenue growth Azure",  # Always same queries!
    "Microsoft Azure market share trends",
    ...
]
```

**After (dynamic):**
```python
# query_planner.py generates this from user prompt
SearchPlan(
    domain="Semiconductor Manufacturing",
    companies=[{"ticker": "NVDA", "name": "Nvidia"}, ...],
    grounding_queries=["Nvidia quantum computing R&D", ...],  # Dynamic!
)
```

**Ref:** [SearchPlan design pattern — domain-driven query generation](#the-searchplan-revolution)
**Ref:** [Python dataclasses](https://docs.python.org/3/library/dataclasses.html)

### 2.3 Why a Session Class (Not Global Functions)?

```python
class ChatSession:
    def __init__(self, llm, cfg, guard):
        self._memory = GraphMemory()         # persists across turns
        self._news_agent = None              # built on first deep analysis
        self._index_built = False            # flag
        self._analysis_result = None         # cached result
        self._conversation = []              # multi-turn history
```

**Why a class instead of module-level globals?**

1. **Encapsulation.** All session state lives in one object. Easy to reason about, easy to reset.

2. **Testability.** You can create a session with `dry_run=True`, call methods, and assert on results — no mocking globals.

3. **Multi-user readiness.** Right now we have one global `_session`. But if we wanted multiple users, each would get their own `ChatSession` via Gradio's `gr.State()`. The class design makes this trivial.

4. **Lifecycle clarity.** When you click "New Session", we just set `_session = None`. The old session (and all its state) gets garbage collected. No manual cleanup.

**Ref:** [Gradio State in Blocks](https://www.gradio.app/guides/state-in-blocks)

### 2.4 Why Streaming Generators (Not Async/WebSockets)?

```python
# orchestrator.py — deep_analysis() is a generator
def deep_analysis(self, prompt: str) -> Generator[str, None, None]:
    yield "🔍 Step 1/10: Planning..."
    plan = analyst.build_plan(prompt)
    yield "🔍 Step 1/10: Planning...\n✅ Generated 7 steps"
    ...
```

**How Gradio streaming works:**

1. `respond()` in `main.py` is a generator (uses `yield`).
2. Gradio calls `next()` on the generator repeatedly.
3. Each `yield` returns the **complete** updated state (chatbot history, cost, session info).
4. Gradio patches the UI with the new state — the chat bubble gets progressively longer.

**Why generators, not WebSockets or Server-Sent Events (SSE)?**

| Approach | Complexity | Gradio support | Code |
|---|---|---|---|
| **Generator (yield)** | Zero — just `yield` | ✅ Native | 1 line per update |
| SSE (Server-Sent Events) | Medium — need an endpoint | ⚠️ Manual | ~30 lines extra |
| WebSockets | High — bidirectional protocol | ⚠️ `gradio_client` only | ~50 lines extra |
| Async (asyncio) | Medium — event loop | ⚠️ Complex in Gradio | Needs async rewrite |

Generators are the **Gradio-native** way to stream. The framework handles SSE/WebSocket transport under the hood — you don't see it.

**Critical implementation detail:** Each yield returns the **FULL accumulated message**, not just the new text. This is because Gradio replaces the bubble content entirely on each update:

```python
accumulated = ""

def _emit(text: str) -> str:
    nonlocal accumulated
    accumulated += text + "\n"
    return accumulated    # ← returns FULL text, not just the new line

yield _emit("Step 1...")   # Bubble shows: "Step 1..."
yield _emit("Step 2...")   # Bubble shows: "Step 1...\nStep 2..."
```

**Ref:** [Gradio Streaming Chatbots](https://www.gradio.app/guides/creating-a-chatbot-fast#streaming-chatbots)
**Ref:** [Python Generators (PEP 255)](https://peps.python.org/pep-0255/)

### 2.5 Why Three Modes (Not Just One)?

Different questions need different levels of AI effort:

| Question type | What's needed | LLM calls | Cost | Right mode |
|---|---|---|---|---|
| "What is Google Cloud?" | General knowledge | 1 | ~$0.0002 | Quick Chat |
| "Analyze GC's threat to Azure with latest earnings data" | Full pipeline | 17+ | ~$0.01 | Deep Analysis |
| "What about AWS's positioning?" (after analysis) | RAG on existing index | 3 | ~$0.001 | Follow-up |

Running the full pipeline for every message would be wasteful (17 API calls × $0.0006 each for a simple question). Quick Chat handles 90% of questions at 1/50th the cost.

### 2.6 The SearchPlan Revolution — Domain-Agnostic Dynamic Pipeline

**The Core Problem (Before):**

The original pipeline from usecase_3 had **hardcoded** domain knowledge:

```python
# web_fetcher.py (OLD — hardcoded)
_GROUNDING_QUERIES = [
    "Google Cloud quarterly revenue growth Azure",
    "Microsoft Azure market share trends",
    # ... always the same queries!
]

_CIKS = {"GOOGL": "0001652044", "MSFT": "0000789019", "AMZN": "0001018724"}
```

This meant that even if a user asked about "Tesla's EV strategy vs. BYD", the pipeline would still fetch Google Cloud data!

**The Solution: SearchPlan Dataclass**

```python
# query_planner.py — NEW
@dataclass
class SearchPlan:
    domain: str                           # "Semiconductor Manufacturing"
    focus_topic: str                      # "quantum computing R&D"
    perspective: str                      # "Technology industry analyst"
    primary_question: str                 # The user's actual question
    companies: list[dict]                 # [{"ticker": "NVDA", "name": "Nvidia", "cik": "..."}]
    grounding_queries: list[str]          # LLM-generated search queries
    news_queries: list[str]               # RSS queries
    sec_fulltext_queries: list[str]       # SEC EDGAR fulltext searches
    financial_retrieval_query: str        # For hybrid index query
    qualitative_retrieval_query: str      # For qualitative signals
    memory_seeds: list[tuple[str, str, str]]  # Initial knowledge graph edges
    extraction_companies: dict[str, str]  # {"NVDA": "Nvidia Corporation"}
```

**How it works (Step 0 in the pipeline):**

```
User prompt: "Analyze Nvidia and Qualcomm quantum computing R&D"
                           │
                           ▼
         ┌─────────────────────────────────────┐
         │  query_planner.generate_search_plan │
         │  (LLM call with structured output)  │
         └─────────────────────────────────────┘
                           │
                           ▼
         SearchPlan(
           domain="Semiconductor Manufacturing",
           companies=[{"ticker": "NVDA"}, {"ticker": "QCOM"}],
           grounding_queries=[
             "Nvidia quantum computing R&D progress",
             "Qualcomm quantum computing strategy",
             ...
           ],
           ...
         )
                           │
                           ▼
         All downstream modules read from SearchPlan
         (web_fetcher, agents, metadata_enricher)
```

**The LLM prompt for plan generation:**

```
You are a research planning assistant. Given a user question about
strategic analysis, generate a comprehensive search plan.

User question: "Analyze the 2 largest chip manufacturing industries
(Nvidia and Qualcomm) and extract information how fast are they doing
research and develop in Quantum computing."

Generate a JSON response with:
- domain: The industry/sector
- focus_topic: The specific area of analysis
- companies: List of companies to analyze (with tickers if known)
- grounding_queries: 6-8 web search queries to find relevant data
- news_queries: 4-6 news search queries
...
```

**Why this design pattern matters:**

| Principle | How SearchPlan implements it |
|---|---|
| **Single Source of Truth** | All domain info flows from one SearchPlan object |
| **Dependency Injection** | Modules receive SearchPlan, don't hardcode data |
| **Open/Closed Principle** | New domains work without changing module code |
| **Separation of Concerns** | Planning logic isolated in query_planner.py |

**Ref:** [Dataclasses (PEP 557)](https://peps.python.org/pep-0557/)
**Ref:** [Dependency Injection](https://en.wikipedia.org/wiki/Dependency_injection)

---

## 3. File-By-File: What Each File Does and Why

### 3.0 Data & State Storage Locations

| Data Type | Storage Location | Persistence |
|---|---|---|
| **LLM response cache** | `usecase_4/.cache/gemini/` (LOCAL, not shared) | Persistent on disk via `diskcache`. Survives restarts. |
| **Server log** | `usecase_4/outputs/server_log_{timestamp}.txt` | Persistent. Timestamped per server launch. |
| **Prompt log** | `usecase_4/outputs/chatbot_prompt_log_{timestamp}.jsonl` | Persistent. Timestamped. One entry per LLM call. |
| **Retrieval index** | **In-memory only** — `NewsAndDataAgent` holds chunks, BM25, dense vectors | Lost when session resets or process exits. |
| **Knowledge graph** | **In-memory only** — `GraphMemory` in `ChatSession` | Same — lost on session reset. |
| **Conversation history** | **In-memory only** — `ChatSession._conversation` list | Same. Limited to last 10 turns. |
| **Analysis results** | **In-memory only** — `ChatSession._analysis_result` dict | Available for follow-up queries until session reset. |
| **Search plan** | **In-memory only** — `SearchPlan` dataclass in `ChatSession` | Generated fresh per deep analysis. |

**Why no persistent storage (database, vector store)?** This is a local single-user learning tool. All state lives in Python objects. A "New Session" button in the UI creates a fresh `ChatSession` — the old one gets garbage collected. For a production multi-user app, you'd add PostgreSQL for conversations, Redis for sessions, and a vector DB for the index.

### 3.1 `orchestrator.py` — The Session Manager (558 lines)

**What:** Bridges the stateless Gradio UI and the stateful agentic pipeline. Manages the `ChatSession` lifecycle.

**Why this file exists:** Gradio event handlers should be thin. The orchestrator holds all the business logic (mode routing, state management, streaming coordination) separate from the UI layout code.

#### 3.1.1 The `ChatSession` Class

```python
class ChatSession:
    def __init__(self, llm: GeminiClient, cfg, guard: CostGuard) -> None:
        self._llm = llm
        self._cfg = cfg
        self._guard = guard
        self._memory = GraphMemory()
        self._news_agent: NewsAndDataAgent | None = None
        self._index_built = False
        self._analysis_result: dict | None = None
        self._conversation: list[dict[str, str]] = []
        self._max_history_turns = 10
```

**Why `_max_history_turns = 10`?** Each turn is roughly 100-200 tokens. 10 turns ≈ 1000-2000 tokens. Adding this to the system prompt (~200 tokens) keeps the total well under 4K tokens — cheap and within the model's effective attention window. More turns = more cost and less accurate attention to the latest message.

**Ref:** [Multi-turn conversations in Gemini](https://ai.google.dev/gemini-api/docs/text-generation#multi-turn-conversations)

#### 3.1.2 The `_emit()` Pattern (Accumulating Streamer)

```python
def deep_analysis(self, prompt):
    accumulated = ""

    def _emit(text: str) -> str:
        nonlocal accumulated
        accumulated += text + "\n"
        return accumulated

    yield _emit("🔍 Step 1...")
    # ...work...
    yield _emit("   ✅ Done")
```

**Why a nested `_emit()` function?** Two reasons:

1. **DRY.** Without `_emit`, every yield would look like:
   ```python
   accumulated += "🔍 Step 1...\n"
   yield accumulated
   accumulated += "   ✅ Done\n"
   yield accumulated
   ```
   Repetitive and error-prone.

2. **Closure.** `_emit` closes over `accumulated` (via `nonlocal`). This is a Python closure — the inner function "remembers" the outer function's variable. It's a clean pattern for accumulating state inside a generator.

**Ref:** [Python closures](https://docs.python.org/3/faq/programming.html#why-am-i-getting-unexpected-results-with-simple-arithmetic-operations)
**Ref:** [nonlocal statement (PEP 3104)](https://peps.python.org/pep-3104/)

#### 3.1.3 The `create_session()` Factory

```python
def create_session(*, budget_usd=0.50, dry_run=False) -> ChatSession:
    cfg = load_config(_USECASE3_DIR, budget_usd=budget_usd, dry_run=dry_run)
    guard = CostGuard(budget_usd=cfg.billing.budget_usd, dry_run=cfg.billing.dry_run)
    cache_path = cfg.cache_dir / "gemini"
    llm = GeminiClient(cfg.gemini, cache_dir=str(cache_path), cost_guard=guard)

    log_dir = Path(__file__).resolve().parent / "outputs"
    log_dir.mkdir(exist_ok=True)
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    enable_prompt_logging(str(log_dir / f"chatbot_prompt_log_{ts}.jsonl"))

    return ChatSession(llm, cfg, guard)
```

**Why a factory function (not `ChatSession.__init__` directly)?**

1. **Separation of concerns.** Session creation involves config loading, cache setup, logging — infrastructure concerns. The `ChatSession` class itself only cares about chat logic.

2. **Keyword-only arguments** (`*` in the signature). This forces callers to be explicit: `create_session(budget_usd=0.10)` not `create_session(0.10)`. Prevents positional argument mistakes.

3. **Shared cache directory.** The factory points the LLM client at `usecase_3/.cache/gemini/`. This means if you've already run usecase_3, those cached LLM responses are available here too — the second project benefits from the first's cache.

**Ref:** [Factory pattern (Refactoring Guru)](https://refactoring.guru/design-patterns/factory-method)
**Ref:** [Keyword-only arguments (PEP 3102)](https://peps.python.org/pep-3102/)

### 3.2 `main.py` — The Gradio UI (426 lines)

**What:** Defines the entire web interface — layout, widgets, event bindings, CLI args, and server launch.

**Why Gradio Blocks (not ChatInterface)?**

```python
# Simple (but limited):
gr.ChatInterface(fn=respond).launch()

# Full control (what we use):
with gr.Blocks(theme=gr.themes.Soft()) as app:
    # custom layout with sidebar...
```

`gr.ChatInterface` creates a minimal chat UI automatically. But we need:
- A sidebar with mode selector, cost display, example prompts
- Custom button layout (Clear, New Session)
- Multiple output components updated per message

`gr.Blocks` gives full layout control. The tradeoff: more code (~200 LOC vs ~20 LOC), but much more capability.

**Ref:** [Gradio Blocks Documentation](https://www.gradio.app/docs/gradio/blocks)

#### 3.2.1 The Layout

```python
with gr.Blocks(title="Strategic Analysis Chatbot", theme=gr.themes.Soft()) as app:
    gr.Markdown("# 🏢 Strategic Analysis Chatbot")

    with gr.Row():
        with gr.Column(scale=3):   # 75% — chat area
            chatbot = gr.Chatbot(type="messages", height=550)
            with gr.Row():
                msg = gr.Textbox(scale=4)
                submit_btn = gr.Button("Send", scale=1)

        with gr.Column(scale=1):   # 25% — sidebar
            mode = gr.Radio(choices=["💬 Quick Chat", "🔍 Deep Analysis", ...])
            cost_display = gr.Markdown("...")
            session_info = gr.Markdown("...")
            # example buttons, clear, reset...
```

**Why `type="messages"` on Chatbot?**

Gradio's Chatbot supports two formats:
- **Legacy tuple format:** `[("user msg", "bot msg"), ...]` — simple but limited
- **Messages format:** `[{"role": "user", "content": "..."}, {"role": "assistant", "content": "..."}]` — OpenAI-compatible, supports metadata

We use `type="messages"` because:
1. It matches the format LLM APIs use (OpenAI, Gemini, etc.)
2. It supports per-message metadata (role, name, etc.)
3. It's the direction Gradio is moving (tuple format is deprecated)

**Ref:** [Gradio Chatbot docs](https://www.gradio.app/docs/gradio/chatbot)

**Why `scale=3` and `scale=1`?** Gradio's `scale` parameter controls relative width within a `gr.Row()`. 3:1 means 75%/25%. This gives the chat area dominant space while keeping controls accessible.

**Ref:** [Gradio Column docs](https://www.gradio.app/docs/gradio/column)

#### 3.2.2 The `respond()` Generator

```python
def respond(message, history, mode):
    session = _get_session()
    history = history + [{"role": "user", "content": message}]

    if mode == "🔍 Deep Analysis":
        for update in session.deep_analysis(message):
            yield ("", history + [{"role": "assistant", "content": update}],
                   session.cost_summary(), session.session_info())

    elif mode == "🔄 Follow-up (uses index)":
        for update in session.follow_up(message):
            yield ("", history + [{"role": "assistant", "content": update}],
                   session.cost_summary(), session.session_info())

    else:
        response = session.quick_chat(message)
        yield ("", history + [{"role": "assistant", "content": response}],
               session.cost_summary(), session.session_info())
```

**This single function is the heart of the UI. Here's what happens on each yield:**

1. `""` → clears the input textbox (so user doesn't see their old message)
2. `history + [{"role": "assistant", "content": update}]` → replaces the entire chatbot display. The last message is the assistant's progressive response.
3. `session.cost_summary()` → updates the sidebar cost counter
4. `session.session_info()` → updates the sidebar session state

**Why `history + [...]` (not `history.append(...)`)?**

Immutability! `history.append()` modifies the list in-place, which can cause Gradio state bugs. `history + [...]` creates a new list each time. Gradio detects the change by comparing old and new values. In-place mutation might go undetected.

**Ref:** [Gradio event listeners](https://www.gradio.app/docs/gradio/textbox#event-listeners)

#### 3.2.3 Event Binding Pattern

```python
submit_args = dict(
    fn=respond,
    inputs=[msg, chatbot, mode],
    outputs=[msg, chatbot, cost_display, session_info],
)
msg.submit(**submit_args)        # Enter key
submit_btn.click(**submit_args)  # Send button
```

**Why define `submit_args` as a dict?** Both the Enter key (`msg.submit`) and the Send button (`submit_btn.click`) should trigger the same function with the same I/O. Repeating the argument list would be error-prone. The dict is defined once and unpacked with `**`.

**Why both `.submit()` and `.click()`?** Users expect both: pressing Enter in the textbox AND clicking the Send button should work. These are separate widget events in Gradio.

**Ref:** [Gradio Textbox submit event](https://www.gradio.app/docs/gradio/textbox#event-listeners)

#### 3.2.4 Example Prompt Buttons

```python
ex1 = gr.Button("Analyze Google Cloud vs Azure", size="sm")
ex1.click(
    lambda: "Analyze the strategic threat Google Cloud poses to Microsoft Azure...",
    outputs=msg,
)
```

**Why lambdas?** Each button just needs to set the textbox value to a pre-written prompt. A lambda is the simplest way — no function definition needed, the intent is obvious.

**Why example prompts?** Empty text boxes are intimidating. Pre-written prompts show users what the tool can do and how to phrase queries. This is a common UX pattern in AI chat interfaces (ChatGPT does this too).

#### 3.2.5 Session Management Buttons

```python
def reset_session():
    global _session
    _session = None    # garbage collected — all state gone
    return ([], "**💰 Cost:** $0.0000 ...", "**📊 Session:** New session started")

new_session_btn.click(reset_session, outputs=[chatbot, cost_display, session_info])
```

**Why `_session = None` (not `_session.reset()`)?** Setting to `None` means the next `_get_session()` call creates a completely fresh `ChatSession` — new LLM client, new cost guard (reset to $0), new memory. This is simpler and more reliable than implementing a `reset()` method that must carefully zero out every field.

**Why separate Clear and Reset?**
- **Clear Chat:** Empties the display but keeps the session (memory, index, cost counter). Useful if the chat gets long but you want to continue the analysis.
- **New Session:** Destroys everything. Fresh API key resolution, new cost guard, empty memory. Useful when switching topics entirely.

#### 3.2.6 CLI Arguments

```python
p.add_argument("--dry-run", action="store_true", help="Skip all LLM calls (zero cost)")
p.add_argument("--budget", type=float, default=0.50, help="Hard budget cap in USD")
p.add_argument("--port", type=int, default=7860, help="Server port")
p.add_argument("--share", action="store_true", help="Create a public ngrok URL")
```

**Why `--dry-run`?** Tests the entire UI (layout, streaming, mode switching) without spending any money. Every LLM call returns `"[DRY RUN — LLM call skipped]"`. Essential for development.

**Why `--share`?** Gradio's share mode creates a temporary public URL via ngrok. You can share this URL with someone on another computer — they'll see your chatbot. No deployment needed. Useful for demos.

**Ref:** [Gradio Share docs](https://www.gradio.app/docs/gradio/blocks#blocks-launch)

#### 3.2.7 The Launch Call

```python
app.launch(
    server_name="127.0.0.1",    # local only
    server_port=_cli_args.port,  # default 7860
    share=_cli_args.share,       # ngrok public URL
    inbrowser=True,              # auto-open browser
)
```

**Why `server_name="127.0.0.1"` (not `"0.0.0.0"`)?**
- `127.0.0.1` = only accessible from your own machine (safe)
- `0.0.0.0` = accessible from any device on your network (useful but exposes API key)

For a local learning tool, localhost is correct. If you need remote access, use `--share` (tunnels through ngrok with a unique URL).

**What happens under the hood:** Gradio starts a [Starlette](https://www.starlette.io/) ASGI server (not Flask — that changed in Gradio 4.x). It serves the frontend (Svelte components compiled to JS) and handles WebSocket connections for streaming.

**Ref:** [Gradio Blocks.launch()](https://www.gradio.app/docs/gradio/blocks#blocks-launch)

---

## 4. Step-By-Step Execution Trace

This traces what happens when you run `python main.py` and interact with the chatbot.

### Phase 1: Startup

```
$ python main.py

10:30:01  INFO  __main__  Starting chatbot: port=7860 budget=$0.50 dry_run=False share=False
* Running on local URL: http://127.0.0.1:7860
```

**What happens internally:**
1. `parse_args()` → `argparse.Namespace(dry_run=False, budget=0.5, port=7860, share=False)`
2. `_cli_args` is stored globally.
3. `gr.Blocks(...)` defines the UI layout (no session created yet — it's lazy).
4. `app.launch(...)` starts the Starlette server and opens the browser.
5. **No `ChatSession` is created yet.** It's created lazily on the first message.

**Why lazy session creation?** The Gradio UI might take 2 seconds to start. If session creation (config loading, cache initialization) adds 1 more second, the browser would open to a non-responsive page for 3 seconds. With lazy creation, the UI loads instantly and the session is created when needed. The first message takes slightly longer, but subsequent messages are instant.

### Phase 2: First Message (session creation)

When the user sends their first message, `_get_session()` is called:

```python
def _get_session() -> ChatSession:
    global _session
    if _session is None:
        _session = create_session(budget_usd=budget, dry_run=dry_run)
    return _session
```

This triggers `create_session()`:

```
10:30:15  INFO  orchestrator  ChatSession created: model=gemini-2.5-flash budget=$0.50 dry_run=False
```

Internally:
1. `load_config()` resolves the API key (walks up directories looking for `.env` / `local.properties`)
2. `CostGuard` initialized with $0.50 budget
3. `GeminiClient` created with `diskcache` at `usecase_3/.cache/gemini/`
4. Prompt logging enabled: `outputs/chatbot_prompt_log_20260326_103015.jsonl`
5. `ChatSession` constructed with empty memory, no index, empty conversation

### Phase 3: Quick Chat Response

If mode is "💬 Quick Chat" and user sends "What is Google Cloud?":

```
10:30:15  INFO  llm_client  generate: 145 in / 312 out  $0.00021
```

**Code path:**
```
main.py respond() → session.quick_chat() → llm.generate() → Gemini API
```

<details>
<summary><strong>📋 Actual Prompt — Quick Chat</strong></summary>

**System Prompt:**
```
You are a strategic analysis assistant specializing in cloud computing
competition (Google Cloud, Microsoft Azure, AWS). Provide thoughtful,
data-aware responses. If the user asks for detailed data-backed analysis,
suggest they switch to **Deep Analysis** mode.
```

(No context_parts on first message — memory empty, no prior analysis.)

**User Prompt (conversation history format):**
```
USER: What is Google Cloud?
```

**Parameters:** `temperature=0.3, max_tokens=2048`

</details>

**Why is conversation history sent as a flat string (not Gemini's multi-turn format)?**

Gemini's REST API supports multi-turn via `contents: [{role: "user"}, {role: "model"}, ...]`. But our `GeminiClient.generate()` takes a single `prompt` string + `system` string. Converting conversation history to the multi-turn format would require changing `llm_client.py`.

Instead, we format history as a plain text string:
```
USER: What is Google Cloud?
ASSISTANT: Google Cloud is...
USER: How fast is it growing?
```

This works because LLMs can read and understand conversation formats in plain text. The multi-turn API format is more token-efficient (avoids repeating "USER:" and "ASSISTANT:" labels), but the difference is ~10 tokens per turn — negligible for our use case.

### Phase 4: Deep Analysis (Streaming)

If mode is "🔍 Deep Analysis" and user sends an analysis prompt:

**Example prompt (actual test run, March 2026):**
> "Analyze the 2 largest chip manufacturing industries (Nvidia and Qualcomm) and extract information how fast are they doing research and develop in Quantum computing."

The pipeline now starts with **Step 0: Query Planning** — the LLM analyzes the user's question and generates a domain-specific search plan:

```
🧠 **Step 0:** Understanding your question...
   ✅ Domain: **Semiconductor Manufacturing** | Companies: NVDA, QCOM | Queries planned: 8
```

The UI then shows progressive updates in the chat bubble:

```
🔍 **Step 1/10:** Planning analysis steps...
   ✅ Generated 7 steps

📡 **Step 2/10:** Fetching real-time data (8 web searches, 2 SEC filings, 5 news feeds)...
   ⏳ Still fetching data... (10s)
   ⏳ Still fetching data... (20s)
   ...
   ⏳ Still fetching data... (120s)
   ✅ Indexed 441 chunks from 415 documents

📊 **Step 3/10:** Retrieving financial context (hybrid search → rerank → fuse)...
   ✅ Financial context retrieved
   ⏳ Extracting structured metrics via LLM...
   ✅ Financial data extracted

🧮 **Step 4/10:** Computing market share deltas (dynamic Python tool)...
   ✅ {"note": "insufficient revenue data for delta"}

🔎 **Step 5/10:** Retrieving competitive signals...
   ✅ Qualitative signals retrieved

⚡ **Step 6/10:** Synthesising strategic analysis...
   ✅ Strategic analysis complete

📝 **Step 7/10:** Self-critique (quality gate)...
   ✅ Score: 7.5/10 — PASS

💡 **Step 8/10:** Generating strategic recommendations...
   ✅ 0 strategies generated

🧠 **Step 9/10:** Extracting knowledge graph edges...
   ✅ 4 total edges in memory

📋 **Step 10/10:** Writing executive summary...
   ✅ Complete!


---

## 📄 Executive Summary

**Executive Summary: Quantum Computing R&D Velocity**

Nvidia is rapidly establishing itself as a critical enabler in quantum computing, focusing its R&D on accelerating hybrid classical-quantum...

## ⚡ Key Analysis

Nvidia is conducting quantum computing R&D at a rapid pace, focusing on accelerating the field through its powerful GPU platforms and specialized software. Their strategic approach is to be the "bridge" between classical and quantum computing, enabling hybrid systems rather than building quantum processing units (QPUs).

**Nvidia's Speed and Strategy:**
Nvidia's R&D is fast-paced, demonstrated by significant speedups in quantum emulation. Their **cuQuantum SDK** (including cuStateVec, cuTensorNet, cuDensityMat) provides optimized libraries for GPU-accelerated quantum simulations, achieving orders-of-magnitude speedups...

**Qualcomm's Speed and Strategy:**
Information on Qualcomm's quantum computing R&D speed is limited by the provided data. Their strategic approach appears to be more exploratory, focusing on **quantum machine learning** and **academic quantum research partnerships**...

---
*💰 Total cost: $0.0085 (20 API calls)*
```

**Notice the key differences from the old hardcoded pipeline:**

| Aspect | Old (Hardcoded) | New (Dynamic) |
|---|---|---|
| Step 0 | None | **Query Planning** — LLM generates SearchPlan |
| Domain | Always "Cloud Computing" | Dynamic: "Semiconductor Manufacturing" |
| Companies | Always GOOGL/MSFT/AMZN | Dynamic: NVDA/QCOM from user prompt |
| Queries | 6 hardcoded cloud queries | 8 queries generated from prompt |
| SEC CIKs | Hardcoded for 3 companies | Dynamically looked up from SEC EDGAR |

Each line above is a separate `yield _emit(...)` in the generator. The UI updates after each one.

**The 20 LLM calls include the new Step 0 (query planning).** The total cost is slightly lower than before because the dynamic queries are more focused on the actual user question.

**What's different from usecase_3's pipeline?**

| Aspect | Usecase 3 (Hardcoded) | Usecase 4 (Dynamic) |
|---|---|---|
| Domain | Always cloud computing | **Any domain** — determined by SearchPlan |
| Companies | Always GOOGL/MSFT/AMZN | **Any companies** — extracted from user prompt |
| Queries | Static lists | **Dynamic** — generated by LLM |
| Execution | `AnalystAgent.run()` — single call, blocking | Step-by-step calls with `yield` between each |
| Progress | Log file only | Real-time streaming to chat bubble |
| Error handling | Process crashes | Caught + displayed in UI (`❌ Error: ...`) |
| Budget exceeded | Raises exception | Shows `⚠️ Budget Exceeded` in chat |
| Result storage | Files on disk (`outputs/strategic_report.md`) | In-memory `_analysis_result` dict |
| Follow-up | Impossible | Built-in via Follow-up mode |

### Phase 5: Follow-up Query

After deep analysis, if mode is "🔄 Follow-up" and user sends "What about AWS?":

```
🔎 Searching existing index...
✅ Found relevant chunks. Generating answer...

AWS captured $35.6 billion in Q4 2025 revenue with 24% year-over-year growth...
The company maintains its dominant market share position but faces...

---
*💰 Cost: $0.0115 (20 API calls)*
```

<details>
<summary><strong>📋 Actual Prompt — Follow-up Query</strong></summary>

**System Prompt:**
```
You are a strategic analysis assistant. Answer the user's follow-up
question using the provided context and prior analysis. Be specific,
cite data where available.

Known relationships:
  (Microsoft Azure) --[StronglyLinkedTo]--> (Office365/EnterpriseSales)
  (Google Cloud) --[StronglyLinkedTo]--> (AI-First Data-Native Workloads)
  (Google Cloud) --[poses strategic threat to]--> (Microsoft Azure)
  (Google Cloud) --[dominates in attracting]--> (AI-first, data-native workloads)
  ...

Previous analysis summary:
The biggest strategic threat Google Cloud poses to Microsoft Azure is its
dominance in attracting AI-first, data-native workloads...
```

**User Prompt:**
```
RETRIEVED CONTEXT:
[source=grounded_search company=AMZN score=1.00]
AWS Q4 2025: Revenue $35.6 billion, 24% YoY growth, Operating margin 37%...

[source=edgar company=AMZN]
Amazon.com Inc. Annual revenues 2024: $638.0B...

QUESTION: What about AWS?
```

**Parameters:** `temperature=0.3, max_tokens=2048`

</details>

**Why is follow-up cheaper?** No data fetching ($0), no embedding ($0), re-uses existing BM25/dense index (instant). Only costs are the reranker (2 LLM calls) and the final generation (1 LLM call). ~$0.001 total.

---

## 5. The Three Conversation Modes (Deep-Dive)

### 5.1 Quick Chat — Direct LLM Conversation

**When to use:** Casual questions, clarifications, general knowledge.

**What happens:**
```
User message → Append to conversation history
             → Build system prompt (+ memory + prior summary if available)
             → Format last 10 turns as plain text
             → Single LLM call
             → Return response
```

**Key design choice: progressive context enrichment.**

On the first message, the system prompt is bare:
```
You are a strategic analysis assistant specializing in cloud computing competition...
```

After a deep analysis, the system prompt gets enriched with:
```
Known strategic relationships:
  (Google Cloud) --[poses strategic threat to]--> (Microsoft Azure)
  ...

Previous deep analysis summary:
The biggest strategic threat Google Cloud poses to Microsoft Azure is...
```

This means Quick Chat gets *smarter* after a Deep Analysis — it has context from the analysis without re-running the pipeline. This is called **progressive context enrichment**.

### 5.2 Deep Analysis — Full Agentic Pipeline

**When to use:** Data-backed strategic analysis with real data.

**What happens:**
```
User prompt → Seed memory (if first analysis)
            → Create agent team (NewsAndDataAgent, AnalystAgent, CritiqueModule)
            → 10-step pipeline (each step yields progress to UI)
               1. Plan
               2. Fetch (6 web searches, 3 SEC filings, news RSS)
               3. Index (chunk, embed, build hybrid index)
               4. Retrieve financial context → Extract structured metrics
               5. Compute market share delta (dynamic Python tool)
               6. Retrieve qualitative signals
               7. Threat analysis
               8. Critique (quality gate at 7.0)
               8b. [conditional] Refinement loop
               9. Generate 3 strategies
              10. Extract memory edges + Executive summary
            → Format final report in Markdown
            → Store result for follow-up queries
```

**Error handling strategy:**

```python
try:
    # ... 10-step pipeline ...
except BudgetExceeded as e:
    yield _emit(f"⚠️ Budget Exceeded: {e}")
except Exception as e:
    logger.exception("Deep analysis failed")
    yield _emit(f"❌ Error: {type(e).__name__}: {e}")
```

Three layers:
1. **`BudgetExceeded`** — specific, expected. Shows remaining budget.
2. **`Exception`** — catch-all for unexpected errors. Logs full traceback to console + shows user-friendly message in chat.
3. **`logger.exception()`** — captures the full stack trace in the terminal for debugging.

**Why not retry on error?** The pipeline is expensive (~$0.01). Retrying blindly could double the cost. Instead, the error is shown to the user, who can decide whether to try again.

### 5.3 Follow-up — RAG on Existing Index

**When to use:** After a deep analysis, ask questions about the data that was retrieved.

**What happens:**
```
User question → Check index exists (if not, prompt for Deep Analysis)
              → Hybrid search on existing index (BM25 + dense)
              → LLM reranker (2 calls)
              → Context fusion (dedup + budget)
              → LLM generation with:
                  - Retrieved context (top 6 chunks)
                  - Memory edges (2 seeded + 6 discovered)
                  - Prior analysis summary
              → Return answer
```

**Why this mode exists:** After a deep analysis, the index contains 230+ chunks from diverse sources. Users naturally want to ask follow-up questions ("What about AWS?" "How does Vertex AI compare to Azure ML?"). Without this mode, they'd have to run the full pipeline again ($0.01 each time) or switch to Quick Chat (which has no access to the fetched data).

Follow-up reuses EVERYTHING built during deep analysis:
- BM25 index (term frequencies)
- Dense index (embeddings)
- Memory edges
- Analysis summary for context

**Cost: ~$0.001** (just reranker + generation, no embedding or data fetching).

---

## 6. Best Practices Implemented (And Why)

### 6.1 UI/UX Patterns

| Technique | Implementation | Why |
|---|---|---|
| **Streaming progress** | Generator yields after each pipeline step | Users see activity, not a frozen screen |
| **Mode selector** | `gr.Radio` with 3 options | Clear intent — users choose how much AI effort to spend |
| **Live cost counter** | Sidebar `gr.Markdown` updated on each yield | Cost transparency — users know what they're spending |
| **Example prompts** | 4 pre-written buttons → fill textbox | Reduces "empty box anxiety" — users know what to type |
| **Clear vs Reset** | Two separate buttons | Clear keeps state (for long chats); Reset destroys all state |
| **Auto-open browser** | `inbrowser=True` on launch | One command to go from terminal to usable UI |

### 6.2 Software Engineering Patterns

| Technique | Implementation | Why |
|---|---|---|
| **Lazy initialization** | `_get_session()` creates on first use | UI loads instantly, no upfront delay |
| **Factory pattern** | `create_session()` wires dependencies | Separates construction from usage |
| **Standalone modules** | All modules LOCAL in usecase_4 | No external dependencies, self-contained |
| **Dynamic pipeline** | `SearchPlan` dataclass drives all fetching | Domain-agnostic — works for ANY question |
| **Immutable event data** | `history + [...]` not `history.append(...)` | Prevents Gradio state mutation bugs |
| **Keyword-only args** | `create_session(*, budget_usd=...)` | Prevents positional argument mistakes |
| **Closure pattern** | `_emit()` closes over `accumulated` | Clean accumulating state in generator |
| **Type hints** | `Generator[str, None, None]` on deep_analysis | IDE support + documentation |

### 6.3 Cost Engineering

| Technique | Implementation | Savings |
|---|---|---|
| **Mode routing** | Quick Chat uses 1 LLM call | 20x cheaper than Deep Analysis for simple questions |
| **Index reuse** | Follow-up queries skip data fetch + embedding | No re-embedding cost ($0 vs ~$0 but saves API calls) |
| **Local disk cache** | `usecase_4/.cache/gemini/` per project | Cached responses are free on retry |
| **Focused queries** | SearchPlan generates domain-specific queries | Lower token count than generic queries |
| **Budget guard** | `CostGuard` with hard cap | Prevents runaway costs — raises `BudgetExceeded` |
| **Conversation trim** | Last 10 turns only | Keeps prompt cost bounded as conversation grows |
| **Prompt logging** | JSONL log with timestamps | Audit trail for every cent spent |

### 6.4 Reliability

| Technique | Implementation | Why |
|---|---|---|
| **Graceful budget exceeded** | `except BudgetExceeded` → UI message | User sees the issue, doesn't crash the app |
| **Catch-all exception** | `except Exception` → logs + UI message | Unexpected errors don't crash the server |
| **Index guard** | Follow-up checks `_index_built` flag | Clear error message instead of crashing on empty index |
| **Lazy Gradio import** | `try: import gradio except ImportError` | Helpful error message if Gradio isn't installed |
| **Session null check** | `_get_session()` creates if `None` | New Session button works correctly |

---

## 7. Lessons Learned and Design Tradeoffs

### 7.1 Tradeoff: Streaming Granularity

**The choice:** Do we yield after every LLM call (fine-grained) or after each logical step (coarse)?

**What we chose:** Yield after each *logical step* (10 yields for a full analysis).

**Why:** Fine-grained yields (e.g., after each reranker call) would produce too many rapid UI updates. The user would see the bubble flickering. Logical steps ("Planning...", "Fetching data...", "Analysing threat...") give a meaningful narrative.

**Alternative we considered:** Token-by-token streaming (like ChatGPT). This would require modifying `GeminiClient.generate()` to use the streaming API endpoint (`POST ...streamGenerateContent`). Possible but adds complexity — and our pipeline steps are fast enough that per-step updates feel responsive.

**Ref:** [Gemini Streaming API](https://ai.google.dev/gemini-api/docs/text-generation#generate-a-text-stream)

### 7.2 Tradeoff: Global Session vs. Gradio State

**The choice:** Module-level `_session` variable vs. Gradio's `gr.State()`.

**What we chose:** Global variable.

```python
_session: ChatSession | None = None

def _get_session() -> ChatSession:
    global _session
    if _session is None:
        _session = create_session(...)
    return _session
```

**Why:** This is a single-user local application. A global is simpler to reason about and debug. No serialization concerns (Gradio State pickles objects between requests).

**When you'd switch to `gr.State()`:** Multi-user deployment. Each user needs their own `ChatSession`. `gr.State()` stores per-session data:

```python
# What multi-user would look like:
session_state = gr.State(value=None)

def respond(message, history, mode, session):
    if session is None:
        session = create_session()
    # ... use session ...
    yield ..., session  # pass back the updated session
```

**Ref:** [Gradio State in Blocks](https://www.gradio.app/guides/state-in-blocks)

### 7.3 Tradeoff: Rebuilding Index per Deep Analysis

**The choice:** Every deep analysis call creates a fresh `NewsAndDataAgent` and re-fetches all data.

**Why not reuse the existing index?** The web search results change daily. SEC filings update quarterly. News headlines change hourly. Reusing stale data would produce outdated analysis.

**The compromise:** Follow-up mode reuses the index. This lets users explore the *current* dataset without re-fetching. When the user wants *fresh* data, they run a new Deep Analysis.

### 7.4 Tradeoff: Conversation History as Flat Text

**The choice:** We send conversation history as formatted text, not Gemini's multi-turn `contents` array.

**Why:** Our `GeminiClient` exposes `generate(prompt, system=...)` — a simple interface. Converting to multi-turn would require adding a new method or changing the existing signature, which would also affect usecase_3.

**The cost:** ~10 extra tokens per conversation turn (for the "USER:" and "ASSISTANT:" labels).

**When you'd change this:** If conversations frequently exceed 20 turns, the token overhead becomes significant (~200 extra tokens). At that point, switching to Gemini's native multi-turn format would save ~15% on input tokens.

---

## 8. Cost Analysis

### Per-Mode Cost Breakdown (Updated March 2026)

| Mode | LLM Calls | Typical Input Tokens | Typical Output Tokens | Cost (USD) | Cost (INR) |
|---|---|---|---|---|---|
| Quick Chat | 1 | ~200 | ~300 | ~$0.0002 | ~₹0.02 |
| Deep Analysis | 20 | ~12,000 | ~10,000 | ~$0.0085 | ~₹0.71 |
| Follow-up | 3 | ~2,500 | ~500 | ~$0.0007 | ~₹0.06 |

**Note:** Deep Analysis now includes Step 0 (query planning), adding 1 extra LLM call but producing more focused queries, resulting in slightly lower overall cost.

**Actual test run (Nvidia/Qualcomm quantum computing, March 26, 2026):**

| Step | LLM Calls | Cost |
|---|---|---|
| Step 0: Query Planning | 1 | $0.00055 |
| Step 1: Planning | 2 | $0.00016 |
| Step 2: Data Fetching (8 grounded searches) | 8 | $0.0040 |
| Step 3: Financial Extraction | 3 | $0.0010 |
| Step 4: Share Delta | 0 | $0.00 |
| Step 5: Signal Retrieval | 2 | $0.0005 |
| Step 6: Threat Analysis | 1 | $0.00030 |
| Step 7: Self-Critique | 1 | $0.00015 |
| Step 8: Strategies | 1 | $0.00170 |
| Step 9: Memory Extraction | 1 | $0.00017 |
| Step 10: Summary | 1 | $0.00009 |
| **Total** | **20** | **$0.0085** |

### Typical Session Cost

A typical 30-minute session might look like:

| Activity | Count | Unit Cost | Total |
|---|---|---|---|
| Quick Chat messages | 10 | $0.0002 | $0.0020 |
| Deep Analysis runs | 2 | $0.0085 | $0.0170 |
| Follow-up questions | 5 | $0.0007 | $0.0035 |
| **Session total** | | | **$0.0225** |

That's **~₹2.14** for a 30-minute interactive strategic analysis session — well within the $0.50 budget.

### Cost Comparison: Running Usecase 3 vs. Usecase 4

| Scenario | Cost | Interactivity |
|---|---|---|
| Usecase 3: 5 separate pipeline runs (different prompts) | $0.050 | None (CLI only) |
| Usecase 4: 1 deep analysis + 4 follow-ups + 10 quick chats | $0.015 | Full conversational |

Usecase 4 is **3x cheaper** for equivalent information because follow-ups reuse the index instead of re-running the full pipeline.

---

## 9. Documentation Links

### Gradio (UI Framework)

| Topic | Link |
|---|---|
| Quickstart | https://www.gradio.app/guides/quickstart |
| Creating a Chatbot Fast | https://www.gradio.app/guides/creating-a-chatbot-fast |
| Streaming Chatbots | https://www.gradio.app/guides/creating-a-chatbot-fast#streaming-chatbots |
| Blocks (full layout control) | https://www.gradio.app/docs/gradio/blocks |
| Chatbot widget | https://www.gradio.app/docs/gradio/chatbot |
| Radio widget | https://www.gradio.app/docs/gradio/radio |
| Theming guide | https://www.gradio.app/guides/theming-guide |
| State in Blocks | https://www.gradio.app/guides/state-in-blocks |
| Blocks.launch() | https://www.gradio.app/docs/gradio/blocks#blocks-launch |

### Gemini API

| Topic | Link |
|---|---|
| REST API Quickstart | https://ai.google.dev/gemini-api/docs/quickstart?lang=rest |
| Text generation | https://ai.google.dev/gemini-api/docs/text-generation |
| Multi-turn conversations | https://ai.google.dev/gemini-api/docs/text-generation#multi-turn-conversations |
| Structured Output (responseSchema) | https://ai.google.dev/gemini-api/docs/structured-output |
| Thinking Config | https://ai.google.dev/gemini-api/docs/thinking |
| Search Grounding | https://ai.google.dev/gemini-api/docs/grounding |
| Streaming | https://ai.google.dev/gemini-api/docs/text-generation#generate-a-text-stream |
| Embeddings | https://ai.google.dev/gemini-api/docs/embeddings |
| Pricing | https://ai.google.dev/pricing |

### Python

| Topic | Link |
|---|---|
| Generators (PEP 255) | https://peps.python.org/pep-0255/ |
| Keyword-only args (PEP 3102) | https://peps.python.org/pep-3102/ |
| nonlocal (PEP 3104) | https://peps.python.org/pep-3104/ |
| sys.path | https://docs.python.org/3/library/sys.html#sys.path |
| Import system | https://docs.python.org/3/reference/import.html#the-module-search-path |
| argparse | https://docs.python.org/3/library/argparse.html |
| typing.Generator | https://docs.python.org/3/library/typing.html#typing.Generator |
| Closures FAQ | https://docs.python.org/3/faq/programming.html#why-am-i-getting-unexpected-results-with-simple-arithmetic-operations |

### Design Patterns

| Topic | Link |
|---|---|
| Composition over inheritance | https://en.wikipedia.org/wiki/Composition_over_inheritance |
| Factory pattern | https://refactoring.guru/design-patterns/factory-method |
| Singleton (why we chose not to) | https://refactoring.guru/design-patterns/singleton |

### Usecase 3 (the agentic core)

| Topic | Link |
|---|---|
| Usecase 3 deep-dive walkthrough | `self_learning_documentations/agentic_ai_usecase3_deep_dive_execution_walkthrough.md` |
| Usecase 3 README | `agentic_ai/usecase_3_autonomous_strategic_analysis/README.md` |
| Usecase 3 detailed documentation | `agentic_ai/usecase_3_autonomous_strategic_analysis/DETAILED_DOCUMENTATION.md` |

---

*Generated as a learning companion for the Usecase 4 Strategic Analysis Chatbot.*
*Updated March 2026: Usecase 4 is now fully standalone with dynamic SearchPlan-driven pipeline.*
*Total codebase: ~2500 lines (orchestrator.py, main.py, agents.py, query_planner.py, retrieval modules).*
*All prompts are illustrative — actual prompts vary based on user question and domain.*

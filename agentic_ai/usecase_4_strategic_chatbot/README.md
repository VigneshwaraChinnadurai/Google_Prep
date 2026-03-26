# Usecase 4: Strategic Analysis Chatbot

> A conversational UI that wraps the **usecase_3 agentic AI pipeline** behind a Gradio chatbot, enabling interactive strategic analysis with real-time progress streaming, live cost tracking, and comprehensive server logging.

---

## First-Time Setup (Step by Step)

### Prerequisites

| Requirement | Why | Check |
|---|---|---|
| **Python 3.10+** | Gradio 6.x requires it | `python --version` |
| **Gemini API key** | Powers all LLM calls | [Get one free](https://aistudio.google.com/apikey) |
| **usecase_3 present** | Core agentic pipeline (this project imports it) | `ls ../usecase_3_autonomous_strategic_analysis/` |

### Step 1 — Create & activate a virtual environment

```bash
# From the Google_Prep root directory (parent of agentic_ai/)
cd Google_Prep

# Create venv (one-time)
python -m venv .venv

# Activate it
# Windows PowerShell:
.venv\Scripts\Activate.ps1
# Windows CMD:
.venv\Scripts\activate.bat
# macOS / Linux:
source .venv/bin/activate
```

### Step 2 — Install dependencies

```bash
# From the usecase_4 directory
cd agentic_ai/usecase_4_strategic_chatbot

pip install -r requirements.txt
```

This installs Gradio 6.x + all shared usecase_3 dependencies (requests, diskcache, numpy, etc.).

### Step 3 — Set your Gemini API key

The chatbot needs a **Gemini API key** to call the LLM. Choose ONE method:

```bash
# Option A: Environment variable (recommended, session-only)
# PowerShell:
$env:GEMINI_API_KEY = "your-api-key-here"
# Bash:
export GEMINI_API_KEY="your-api-key-here"

# Option B: .env file (persists across sessions)
# Create a file named .env anywhere in the Google_Prep tree:
echo 'GEMINI_API_KEY=your-api-key-here' > ../../.env

# Option C: local.properties file (same as Android projects)
echo 'GEMINI_API_KEY=your-api-key-here' > ../../local.properties
```

> **Note:** If you already ran usecase_3, your key is already configured — they share the same resolution chain.

### Step 4 — Launch the chatbot

```bash
# Normal mode (uses real Gemini API — costs ~$0.01 per deep analysis)
python main.py

# Opens http://127.0.0.1:7860 in your default browser automatically
```

That's it! You should see the chatbot UI in your browser.

### Step 5 (Optional) — Try dry-run mode first

If you want to verify everything works **without spending any money**:

```bash
python main.py --dry-run
```

All LLM calls return `[DRY RUN — LLM call skipped]` placeholders. The pipeline still runs end-to-end (web fetches, indexing, etc.), but no Gemini API costs are incurred.

---

## Configuration

All tuneable parameters are centralised in **`chatbot_config.yaml`**. Edit this file to change defaults without touching code.

### Config sections at a glance

| Section | What it controls | Key settings |
|---|---|---|
| `server` | Gradio HTTP server | `host`, `port`, `share`, `open_browser` |
| `billing` | Cost guardrails | `budget_usd`, `dry_run` |
| `ui` | Theme & chatbot appearance | `theme`, `chatbot_height`, `chatbot_layout`, `chatbot_buttons` |
| `pipeline` | Analysis behaviour | `max_history_turns`, `heartbeat_interval_sec` |
| `logging` | Server log format & level | `level`, `format`, `log_dir` |

### Config priority (highest wins)

```
CLI arguments  →  chatbot_config.yaml  →  hardcoded defaults
```

For example, `--port 8080` on the command line overrides `port: 7860` in the YAML.

### Common customisations

```yaml
# chatbot_config.yaml — change any of these:

server:
  host: "0.0.0.0"     # Listen on all interfaces (LAN-accessible)
  port: 8080           # Different port

billing:
  budget_usd: 0.10     # Tighter budget ($0.10)
  dry_run: true         # Skip LLM calls permanently

ui:
  theme: "Monochrome"  # Try: Soft, Default, Glass, Ocean, Neon, Cyberpunk
  chatbot_height: 700  # Taller chat for 1440p monitors
  chatbot_layout: "panel"  # ChatGPT-style full-width messages
```

---

## CLI Options

CLI flags override `chatbot_config.yaml` values for a single run.

| Flag | Default | Description |
|---|---|---|
| `--dry-run` | `false` | Skip all LLM calls (zero cost — test UI only) |
| `--budget 0.10` | `0.50` | Hard spending cap in USD |
| `--port 8080` | `7860` | Custom server port |
| `--share` | `false` | Create a public ngrok URL |

### Examples

```bash
# Zero-cost test
python main.py --dry-run

# Tight budget, custom port
python main.py --budget 0.10 --port 8080

# Share publicly (generates a *.gradio.live URL)
python main.py --share

# Combine
python main.py --dry-run --port 9000
```

---

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│                  Gradio Blocks UI                        │
│  ┌─────────────────────────┐  ┌───────────────────────┐  │
│  │    Chat Area (75%)       │  │  Sidebar (25%)        │  │
│  │  - User messages         │  │  - Mode selector      │  │
│  │  - Streaming responses   │  │  - Cost tracker       │  │
│  │  - Markdown rendering    │  │  - Session info       │  │
│  │                          │  │  - Example prompts    │  │
│  │  [Input] [Send]          │  │  - Clear / Reset      │  │
│  └─────────────────────────┘  └───────────────────────┘  │
└──────────────────────┬───────────────────────────────────┘
                       │
            ┌──────────▼──────────┐
            │   orchestrator.py    │
            │   (ChatSession)      │
            │                      │
            │   quick_chat()      ─┼──▶  Direct Gemini call
            │   deep_analysis()   ─┼──▶  Full 10-step pipeline
            │   follow_up()       ─┼──▶  RAG on existing index
            └──────────┬──────────┘
                       │ imports via sys.path
            ┌──────────▼──────────┐
            │   usecase_3 modules  │
            │   - agents.py       │
            │   - llm_client.py   │
            │   - retrieval/      │
            │   - config.py       │
            │   - cost_guard.py   │
            └─────────────────────┘
```

---

## Three Chat Modes

| Mode | What it does | Cost | Time | When to use |
|---|---|---|---|---|
| **💬 Quick Chat** | Direct Gemini call with conversation history | ~$0.0002 | ~2s | Casual questions, quick clarifications |
| **🔍 Deep Analysis** | Full 10-step agentic pipeline: plan → fetch → index → RAG → critique → strategies | ~$0.01 | ~2–4 min | Data-backed strategic analysis |
| **🔄 Follow-up** | RAG query on already-built index + memory edges | ~$0.001 | ~5s | Drill into a completed analysis |

### Deep Analysis pipeline steps

```
Step 1:  Planning          — LLM decomposes prompt into 5-8 execution steps
Step 2:  Data fetching     — 6 web searches + 3 SEC filings + news RSS
Step 3:  Financial extract — Hybrid retrieval → LLM extract metrics
Step 4:  Market delta      — Dynamic Python tool computes share changes
Step 5:  Signal retrieval  — Qualitative competitive signals via RAG
Step 6:  Threat analysis   — LLM synthesises the key strategic threat
Step 7:  Self-critique     — Quality gate (score ≥ 7/10 to pass)
Step 7b: Refinement loop   — Auto-retry if critique score < 7 (up to 2x)
Step 8:  Strategies        — LLM generates 3 mitigation strategies
Step 9:  Memory extract    — Knowledge graph edges for future queries
Step 10: Executive summary — C-suite-ready summary
```

---

## Server Logging

Every event is logged with timestamps to **two destinations**:

1. **Console** — real-time visibility in the terminal
2. **Persistent file** — `outputs/server_log_{timestamp}.txt`

### What gets logged

| Category | Examples |
|---|---|
| UI events | Session creation, user messages, mode selection, button clicks, clear/reset |
| LLM calls | Every `generate()` call with tokens in/out, cost, elapsed time |
| Pipeline steps | Step number, action, result size, elapsed time, running cost |
| Errors | Full stack traces with session/turn correlation IDs |

### Viewing logs

```bash
# Real-time (in the terminal where you ran python main.py)
# Logs stream automatically

# After the fact — open the persistent log file:
cat outputs/server_log_20260326_113644.txt

# Or in PowerShell:
Get-Content outputs/server_log_20260326_113644.txt -Tail 50
```

---

## File Structure

```
usecase_4_strategic_chatbot/
├── main.py                # Gradio UI — layout, events, launch, error handling
├── orchestrator.py        # ChatSession — 3 modes, streaming, heartbeat, state
├── chatbot_config.yaml    # ★ All tuneable parameters (port, theme, budget, etc.)
├── README.md              # This file — first-time setup guide
├── runtime_flow.mmd       # Mermaid sequence diagram
├── requirements.txt       # Python dependencies
└── outputs/               # Generated at runtime (gitignored)
    ├── server_log_{ts}.txt             # Persistent server logs
    └── chatbot_prompt_log_{ts}.jsonl   # Every LLM prompt/response captured
```

---

## Dependencies

| Package | Version | Purpose |
|---|---|---|
| `gradio` | ≥4.0 (6.10 tested) | Web UI framework with native chat + streaming |
| `pyyaml` | ≥5.0 | YAML config file parsing |
| `requests` | ≥2.25 | HTTP client (Gemini API, SEC EDGAR) |
| `diskcache` | ≥5.0 | LLM response caching (persistent on disk) |
| `beautifulsoup4` | ≥4.9 | Google News RSS parsing |
| `lxml` | ≥4.9 | XML parser backend for BeautifulSoup |
| `numpy` | ≥1.21 | Embedding vector math (cosine similarity) |
| `pydantic` | ≥2.0 | Data validation |

---

## What You'll Learn

| Concept | Where it appears |
|---|---|
| **Gradio Blocks UI** | `main.py` — layout with Row/Column, event binding, themes |
| **Streaming responses** | `respond()` generator → progressive UI updates via yield |
| **YAML config management** | `chatbot_config.yaml` + `app_config.py` — config priority chain |
| **Session management** | `orchestrator.py` — persistent state across chat turns |
| **System composition** | Importing usecase_3 via `sys.path` (no packaging needed) |
| **Multi-turn context** | Conversation history + memory edges in system prompt |
| **Agent routing** | Mode selector determines which response path to take |
| **RAG pipeline** | Follow-up mode reuses the existing retrieval index |
| **Cost engineering** | Live cost display, budget guard, per-call cost logging |
| **Comprehensive logging** | UI events, LLM calls, pipeline steps → console + file |
| **Error resilience** | Top-level try/except keeps server alive on any error |
| **SSE heartbeat** | Background thread + periodic yields prevent connection timeout |

---

## Troubleshooting

| Problem | Cause | Fix |
|---|---|---|
| `Gradio not installed` | Missing dependency | `pip install -r requirements.txt` |
| `GEMINI_API_KEY not found` | No API key configured | See [Step 3](#step-3--set-your-gemini-api-key) above |
| `Connection to server was lost` during deep analysis | Long-running step with no heartbeat | Heartbeat is now built-in; ensure you're on the latest code |
| `Port 7860 already in use` | Another Gradio instance is running | `python main.py --port 8080` or change `port` in `chatbot_config.yaml` |
| Deep analysis costs too much | Budget not set tightly enough | `python main.py --budget 0.05` or set `budget_usd: 0.05` in config |
| `[DRY RUN]` in all responses | Dry-run mode is on | Remove `--dry-run` flag or set `dry_run: false` in config |

---

## Relationship to Usecase 3

This project is a **thin UI layer** on top of usecase_3. It adds:
- `orchestrator.py` — ~750 lines (session management, streaming, heartbeat, logging)
- `main.py` — ~530 lines (Gradio UI, events, error handling, logging)
- `chatbot_config.yaml` — centralised configuration

It reuses ~2000 lines from usecase_3:
- LLM client, agents, retrieval pipeline, cost guard, config

This demonstrates **composition over duplication** — the agentic intelligence lives in usecase_3; usecase_4 provides a human-friendly conversational interface on top.

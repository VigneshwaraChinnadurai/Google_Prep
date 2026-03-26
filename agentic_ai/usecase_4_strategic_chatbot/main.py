"""
Strategic Analysis Chatbot — Gradio-powered UI for the Agentic AI pipeline.

This module creates a local web interface (default: http://localhost:7860)
that connects to the usecase_3 strategic analysis engine, providing:

    1. **Quick Chat**     — Direct Gemini LLM conversation for casual questions
    2. **Deep Analysis**  — Full agentic pipeline with real-time progress
    3. **Follow-up**      — RAG query against already-built retrieval index

How it works
------------
    ┌─────────────────┐          ┌──────────────────┐         ┌─────────────┐
    │   Gradio Chat    │─────────▶│   Orchestrator    │────────▶│  Gemini API  │
    │  (localhost UI)  │◀─────────│ (ChatSession)    │◀────────│  (REST)      │
    └─────────────────┘ streaming └──────────────────┘ cache   └─────────────┘
                                        │      ▲
                                        ▼      │
                                  ┌───────────────────┐
                                  │  RAG Pipeline       │
                                  │  (usecase_3 import) │
                                  └───────────────────┘

Running
-------
    # Normal mode (uses Gemini API — costs ~$0.01 per deep analysis):
    python main.py

    # Dry run (zero API cost — tests plumbing only):
    python main.py --dry-run

    # Custom port:
    python main.py --port 8080

    # Share via ngrok (generates a public URL):
    python main.py --share

Gradio references
-----------------
    * Quickstart:       https://www.gradio.app/guides/quickstart
    * Chatbot guide:    https://www.gradio.app/guides/creating-a-chatbot-fast
    * Blocks layout:    https://www.gradio.app/docs/gradio/blocks
    * Chatbot widget:   https://www.gradio.app/docs/gradio/chatbot
    * Streaming:        https://www.gradio.app/guides/creating-a-chatbot-fast#streaming-chatbots
    * Themes:           https://www.gradio.app/guides/theming-guide
"""
from __future__ import annotations

import argparse
import logging
import sys
from datetime import datetime
from pathlib import Path

# ── App config ─────────────────────────────────────────────────────────
# Centralised config: chatbot_config.yaml → app_config.py → ChatbotConfig.
# CLI args override YAML values; YAML overrides hardcoded defaults.
# Ref: app_config.py for the full priority chain explanation.
from app_config import load_chatbot_config, resolve_theme, ChatbotConfig

# ── Logging setup ───────────────────────────────────────────────────────
# 1. Console handler: so you see events in the terminal.
# 2. File handler: persistent timestamped server log that captures every
#    event (UI interactions, LLM calls, pipeline steps, errors).
#
# Logging format & level are read from chatbot_config.yaml (logging section).
# We do a lightweight pre-parse here (YAML only) to get log settings before
# the full config is built later in __main__.

# Pre-load config with no CLI args just for logging defaults.
# This is a cheap YAML read; the real config (with CLI merge) is built in __main__.
_boot_cfg = load_chatbot_config(cli_args=None)
_LOG_FORMAT = _boot_cfg.logging.format
_LOG_DATEFMT = _boot_cfg.logging.datefmt
_LOG_LEVEL = getattr(logging, _boot_cfg.logging.level.upper(), logging.INFO)

# Console handler
logging.basicConfig(
    level=_LOG_LEVEL,
    format=_LOG_FORMAT,
    datefmt=_LOG_DATEFMT,
)

# File handler — captures ALL loggers (root level) to a timestamped file.
# Each server launch creates a NEW log file with the startup timestamp,
# so logs are neatly separated per session and easy to correlate.
# Log directory comes from chatbot_config.yaml → logging.log_dir
_log_dir = Path(__file__).resolve().parent / _boot_cfg.logging.log_dir
_log_dir.mkdir(exist_ok=True)
_log_ts = datetime.now().strftime("%Y%m%d_%H%M%S")
_server_log_path = _log_dir / f"server_log_{_log_ts}.txt"
_file_handler = logging.FileHandler(str(_server_log_path), mode="a", encoding="utf-8")
_file_handler.setLevel(logging.DEBUG)  # capture everything, including DEBUG
_file_handler.setFormatter(logging.Formatter(_LOG_FORMAT, datefmt=_LOG_DATEFMT))
logging.getLogger().addHandler(_file_handler)  # attach to ROOT logger

logger = logging.getLogger(__name__)
logger.info("=" * 80)
logger.info("SERVER START — %s", datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
logger.info("Persistent log file: %s", _server_log_path)
logger.info("=" * 80)

# ── Import Gradio ────────────────────────────────────────────────────────
# Gradio is a Python-first UI framework for AI applications.
# It generates a full web app from Python decorators — no JS/HTML needed.
# Install: pip install gradio
# Ref: https://www.gradio.app/docs/gradio
try:
    import gradio as gr
except ImportError:
    logger.error(
        "Gradio not installed. Run: pip install gradio\n"
        "Ref: https://www.gradio.app/docs/gradio"
    )
    sys.exit(1)

from orchestrator import ChatSession, create_session

# ═══════════════════════════════════════════════════════════════════════════
# Module-level session (single-user local application)
#
# For a multi-user production app, you'd use Gradio's gr.State() to store
# a per-user session object. For a local learning tool, a global is fine.
# Ref: https://www.gradio.app/guides/state-in-blocks
# ═══════════════════════════════════════════════════════════════════════════
_session: ChatSession | None = None
_app_cfg: ChatbotConfig | None = None  # set in __main__ after CLI parsing


def _get_session() -> ChatSession:
    """Lazily create or return the global ChatSession."""
    global _session
    if _session is None:
        cfg = _app_cfg or _boot_cfg  # prefer CLI-merged config
        logger.info(
            "UI_EVENT session_create: dry_run=%s budget=$%.2f",
            cfg.billing.dry_run, cfg.billing.budget_usd,
        )
        _session = create_session(
            budget_usd=cfg.billing.budget_usd,
            dry_run=cfg.billing.dry_run,
            max_history_turns=cfg.pipeline.max_history_turns,
            heartbeat_interval_sec=cfg.pipeline.heartbeat_interval_sec,
        )
    return _session


# ═══════════════════════════════════════════════════════════════════════════
# Chat handler — the core function Gradio calls on each user message
# ═══════════════════════════════════════════════════════════════════════════
def respond(
    message: str,
    history: list[dict],
    mode: str,
):
    """
    Process a user message and yield streaming responses.

    How Gradio streaming works:
    ---------------------------
    This is a **generator function** (uses ``yield``).  Gradio calls it and
    iterates over the yielded values.  Each yielded tuple updates the UI:
        yield (textbox_value, chatbot_history, cost_markdown, session_markdown)

    For streaming chat, we append the user message to history, then
    progressively update the assistant's last message via yields.

    Ref: https://www.gradio.app/guides/creating-a-chatbot-fast#streaming-chatbots
    """
    session = _get_session()

    if not message.strip():
        logger.info("UI_EVENT empty_message_submitted")
        yield "", history, session.cost_summary(), session.session_info()
        return

    # Log the user message and selected mode — FULL detail for server log
    logger.info(
        "UI_EVENT user_message: mode=%s msg_len=%d msg_preview=%.120s",
        mode, len(message), message,
    )
    logger.debug(
        "UI_EVENT user_message_full: mode=%s history_len=%d message=<<<\n%s\n>>>",
        mode, len(history), message,
    )

    # Add user message to the chat display
    history = history + [{"role": "user", "content": message}]

    # ── Top-level error handling ─────────────────────────────────
    # Wrap the entire response pipeline in try-except so that ANY
    # unhandled error is displayed gracefully in the chat instead of
    # crashing the Gradio server (which would show the user:
    # "Connection to the server was lost. Attempting reconnection...").
    #
    # Common causes of server crashes during deep analysis:
    #   - Network timeout during SEC EDGAR / web fetch
    #   - LLM API rate limit or quota exceeded
    #   - Memory issues with very large document sets
    #   - Budget exceeded (handled separately by BudgetExceeded)
    #
    # This catch-all ensures the server stays alive and the user gets
    # actionable feedback instead of a cryptic connection error.
    # Ref: https://www.gradio.app/docs/gradio/chatbot
    import time as _time
    _t0 = _time.perf_counter()

    try:
        if mode == "🔍 Deep Analysis":
            # ── Full agentic pipeline with streaming progress ────────
            # orchestrator.deep_analysis() is a generator that yields
            # progressively longer strings as each pipeline step completes.
            for update in session.deep_analysis(message):
                yield (
                    "",                     # clear the input textbox
                    history + [{"role": "assistant", "content": update}],
                    session.cost_summary(),
                    session.session_info(),
                )
            logger.info(
                "UI_EVENT deep_analysis_complete: elapsed=%.1fs cost=%s",
                _time.perf_counter() - _t0, session.cost_summary(),
            )

        elif mode == "🔄 Follow-up (uses index)":
            # ── RAG query against already-built index ────────────────
            for update in session.follow_up(message):
                yield (
                    "",
                    history + [{"role": "assistant", "content": update}],
                    session.cost_summary(),
                    session.session_info(),
                )
            logger.info(
                "UI_EVENT follow_up_complete: elapsed=%.1fs",
                _time.perf_counter() - _t0,
            )

        else:
            # ── Quick Chat: direct LLM call (no streaming) ──────────
            response = session.quick_chat(message)
            yield (
                "",
                history + [{"role": "assistant", "content": response}],
                session.cost_summary(),
                session.session_info(),
            )
            logger.info(
                "UI_EVENT quick_chat_complete: elapsed=%.1fs resp_len=%d",
                _time.perf_counter() - _t0, len(response),
            )

    except Exception as exc:
        # ── Catch-all: display error in chat, keep server alive ──
        logger.exception(
            "UI_EVENT respond_error: mode=%s error=%s: %s",
            mode, type(exc).__name__, exc,
        )
        error_msg = (
            f"\n\n❌ **Error during {mode}**\n\n"
            f"`{type(exc).__name__}`: {exc}\n\n"
            "The server is still running. You can retry or switch modes."
        )
        yield (
            "",
            history + [{"role": "assistant", "content": error_msg}],
            session.cost_summary() if _session else "**💰 Cost:** Error",
            session.session_info() if _session else "**📊 Session:** Error",
        )


# ═══════════════════════════════════════════════════════════════════════════
# Gradio UI Layout
#
# We use gr.Blocks() for full layout control (vs gr.ChatInterface which
# is simpler but doesn't support sidebars).
#
# Layout:
#   ┌──────────────────────────────┬──────────────────┐
#   │                              │  Mode selector    │
#   │        Chat area             │  Cost display     │
#   │     (scrollable, 550px)      │  Session info     │
#   │                              │  Example prompts  │
#   ├──────────────────────────────│  Clear / Reset    │
#   │  [Input textbox] [Send]     │                    │
#   └──────────────────────────────┴──────────────────┘
#
# Ref: https://www.gradio.app/docs/gradio/blocks
# Ref: https://www.gradio.app/docs/gradio/row
# Ref: https://www.gradio.app/docs/gradio/column
# ═══════════════════════════════════════════════════════════════════════════
with gr.Blocks(
    # Gradio themes control the visual appearance.
    # In Gradio 6.x, theme is passed to launch() instead of Blocks().
    # Theme name is read from chatbot_config.yaml → ui.theme
    # Ref: https://www.gradio.app/guides/theming-guide
    title=_boot_cfg.ui.title,
) as app:

    # ── Header ───────────────────────────────────────────────────
    gr.Markdown(
        """
        # 🏢 Strategic Analysis Chatbot
        *Powered by Gemini 2.5 Flash + RAG Pipeline + Self-Critique Loop*

        Ask strategic questions about cloud market competition. Use **Deep Analysis**
        for data-backed reports with real SEC filings and web data.
        """
    )

    with gr.Row():
        # ── Main chat area (75% width) ───────────────────────────
        with gr.Column(scale=3):
            chatbot = gr.Chatbot(
                # In Gradio 6.x, "messages" format (OpenAI-style) is the
                # default — no need for type="messages".
                #   [{"role": "user", "content": "..."}, ...]
                # Ref: https://www.gradio.app/docs/gradio/chatbot
                height=_boot_cfg.ui.chatbot_height,
                layout=_boot_cfg.ui.chatbot_layout,
                # Gradio 6.x: show_copy_button → buttons=["copy"]
                buttons=_boot_cfg.ui.chatbot_buttons,
                render_markdown=_boot_cfg.ui.render_markdown,
                placeholder=_boot_cfg.ui.chatbot_placeholder,
            )

            with gr.Row():
                msg = gr.Textbox(
                    placeholder=_boot_cfg.ui.textbox_placeholder,
                    show_label=False,
                    scale=4,
                    container=False,
                    # submit_btn=True would add a built-in submit button,
                    # but we add our own for more control.
                )
                submit_btn = gr.Button(
                    "Send",
                    variant="primary",  # blue button
                    scale=1,
                )

        # ── Sidebar (25% width) ──────────────────────────────────
        with gr.Column(scale=1):
            # Mode selector — determines which response path is used.
            # Ref: https://www.gradio.app/docs/gradio/radio
            mode = gr.Radio(
                choices=[
                    "💬 Quick Chat",
                    "🔍 Deep Analysis",
                    "🔄 Follow-up (uses index)",
                ],
                value="💬 Quick Chat",
                label="Mode",
                info=(
                    "Quick Chat = direct LLM | "
                    "Deep Analysis = full agentic pipeline | "
                    "Follow-up = reuse existing index"
                ),
            )

            # Live cost and session displays — updated after each response.
            # These are Markdown components that accept formatted strings.
            cost_display = gr.Markdown("**💰 Cost:** $0.0000 (0 API calls)")
            session_info = gr.Markdown("**📊 Session:** No analysis run yet")

            gr.Markdown("---")
            gr.Markdown("### 📝 Example Prompts")

            # Example prompt buttons — clicking fills the textbox.
            # Ref: https://www.gradio.app/docs/gradio/button
            ex1 = gr.Button("Analyze Google Cloud vs Azure", size="sm")
            ex2 = gr.Button("AWS competitive positioning in AI", size="sm")
            ex3 = gr.Button(
                "Microsoft strategies to counter GC's startup program", size="sm"
            )
            ex4 = gr.Button(
                "Impact of OpenAI non-exclusive compute rights", size="sm"
            )

            gr.Markdown("---")

            # Session management buttons
            clear_btn = gr.Button("🗑️ Clear Chat", variant="secondary")
            new_session_btn = gr.Button("🔄 New Session", variant="secondary")

    # ═══════════════════════════════════════════════════════════════
    # Event handlers
    #
    # Gradio's event system is declarative: you bind functions to
    # widget events (click, submit, change).
    #
    # Ref: https://www.gradio.app/docs/gradio/textbox#event-listeners
    # ═══════════════════════════════════════════════════════════════

    # Both Enter key and Send button trigger the same handler
    submit_args = dict(
        fn=respond,
        inputs=[msg, chatbot, mode],
        outputs=[msg, chatbot, cost_display, session_info],
    )
    msg.submit(**submit_args)
    submit_btn.click(**submit_args)

    # Example prompt buttons → fill the textbox with pre-written prompts
    def _example_click(label: str, prompt_text: str):
        """Log which example prompt was clicked and return the prompt text."""
        def _handler():
            logger.info("UI_EVENT example_prompt_clicked: label=%s", label)
            return prompt_text
        return _handler

    ex1.click(
        _example_click(
            "Analyze Google Cloud vs Azure",
            "Analyze the strategic threat Google Cloud poses to Microsoft Azure "
            "based on the latest quarterly earnings",
        ),
        outputs=msg,
    )
    ex2.click(
        _example_click(
            "AWS competitive positioning in AI",
            "Compare AWS and Google Cloud's competitive positioning in "
            "AI workloads for 2025-2026",
        ),
        outputs=msg,
    )
    ex3.click(
        _example_click(
            "Microsoft strategies to counter GC's startup program",
            "What strategies should Microsoft pursue to counter Google "
            "Cloud's dominance in AI-first startups?",
        ),
        outputs=msg,
    )
    ex4.click(
        _example_click(
            "Impact of OpenAI non-exclusive compute rights",
            "Analyze the impact of OpenAI's restructured non-exclusive "
            "compute rights on Azure's competitive position",
        ),
        outputs=msg,
    )

    # Clear chat — resets display but keeps session state (memory, index)
    def clear_chat():
        logger.info("UI_EVENT clear_chat: display cleared, session state kept")
        return [], "**💰 Cost:** $0.0000", "**📊 Session:** Chat cleared"

    clear_btn.click(
        clear_chat,
        outputs=[chatbot, cost_display, session_info],
    )

    # New session — destroys everything and starts fresh
    def reset_session():
        global _session
        logger.info("UI_EVENT reset_session: destroying session, all state cleared")
        _session = None
        return (
            [],
            "**💰 Cost:** $0.0000 (0 API calls)",
            "**📊 Session:** New session started",
        )

    new_session_btn.click(
        reset_session,
        outputs=[chatbot, cost_display, session_info],
    )


# ═══════════════════════════════════════════════════════════════════════════
# CLI argument parser
#
# CLI arguments override chatbot_config.yaml values for a single run.
# Ref: chatbot_config.yaml for the full list of tuneable parameters.
# ═══════════════════════════════════════════════════════════════════════════
def parse_args() -> argparse.Namespace:
    """Parse command-line arguments for server configuration."""
    p = argparse.ArgumentParser(
        description="Strategic Analysis Chatbot — Gradio UI",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "Examples:\n"
            "  python main.py                    # Normal mode\n"
            "  python main.py --dry-run           # Zero cost testing\n"
            "  python main.py --port 8080         # Custom port\n"
            "  python main.py --share              # Public ngrok URL\n"
            "  python main.py --budget 0.10        # Tight budget\n"
        ),
    )
    # NOTE: defaults are None (not 0.50, 7860, etc.) so that
    # load_chatbot_config() can distinguish "user passed a CLI flag"
    # from "user didn't pass anything → fall through to YAML → hardcoded".
    p.add_argument(
        "--dry-run", action="store_true", default=None,
        help="Skip all LLM calls (zero cost — tests UI plumbing)",
    )
    p.add_argument(
        "--budget", type=float, default=None,
        help="Hard budget cap in USD (YAML default: $0.50)",
    )
    p.add_argument(
        "--port", type=int, default=None,
        help="Server port (YAML default: 7860)",
    )
    p.add_argument(
        "--share", action="store_true", default=None,
        help="Create a public ngrok URL (requires internet)",
    )
    return p.parse_args()


# ═══════════════════════════════════════════════════════════════════════════
# Launch
#
# Gradio's app.launch() starts a local HTTP server (Flask/FastAPI under
# the hood). Key parameters:
#   server_name: "127.0.0.1" = local only, "0.0.0.0" = network-accessible
#   share: True = create a public URL via ngrok (Gradio provides this)
#   inbrowser: True = auto-open the default browser
#
# Ref: https://www.gradio.app/docs/gradio/blocks#blocks-launch
# ═══════════════════════════════════════════════════════════════════════════
if __name__ == "__main__":
    _cli_args = parse_args()

    # ── Build final config: CLI overrides → YAML → defaults ─────
    _app_cfg = load_chatbot_config(cli_args=_cli_args)

    if _app_cfg.billing.dry_run:
        logger.info("═══ DRY RUN MODE — no LLM calls will be made ═══")

    logger.info(
        "Starting chatbot: host=%s port=%d budget=$%.2f dry_run=%s "
        "share=%s theme=%s",
        _app_cfg.server.host,
        _app_cfg.server.port,
        _app_cfg.billing.budget_usd,
        _app_cfg.billing.dry_run,
        _app_cfg.server.share,
        _app_cfg.ui.theme,
    )

    app.launch(
        server_name=_app_cfg.server.host,
        server_port=_app_cfg.server.port,
        share=_app_cfg.server.share,
        inbrowser=_app_cfg.server.open_browser,
        # In Gradio 6.x, theme moved from Blocks() to launch().
        # resolve_theme() maps the string name to a Gradio theme object.
        theme=resolve_theme(_app_cfg.ui.theme),
    )

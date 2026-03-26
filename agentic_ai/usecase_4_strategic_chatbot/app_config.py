"""
App-level configuration loader for the Strategic Analysis Chatbot.

Loads ``chatbot_config.yaml``, merges with CLI overrides, and exposes a
typed ``ChatbotConfig`` dataclass consumed by ``main.py`` and ``orchestrator.py``.

Priority chain (highest wins):
    1. CLI arguments   (--port 8080, --dry-run, etc.)
    2. YAML file       (chatbot_config.yaml)
    3. Hardcoded defaults in this module

Design decisions
----------------
- **Dataclasses** (not Pydantic) — zero extra dependencies; frozen for safety.
- **PyYAML** for parsing — lightweight, well-supported.
- ``resolve_theme()`` maps a string name (e.g. "Soft") to a Gradio theme
  object at runtime, keeping the YAML human-readable.

Ref: https://docs.python.org/3/library/dataclasses.html
Ref: https://pyyaml.org/wiki/PyYAMLDocumentation
Ref: https://www.gradio.app/guides/theming-guide
"""
from __future__ import annotations

import argparse
import logging
from dataclasses import dataclass, field
from pathlib import Path

logger = logging.getLogger(__name__)

# ═══════════════════════════════════════════════════════════════════════════
# Config dataclasses — typed, frozen, documented
#
# Each section mirrors a top-level key in chatbot_config.yaml.
# Frozen dataclasses prevent accidental mutation after loading.
# Ref: https://docs.python.org/3/library/dataclasses.html#frozen-instances
# ═══════════════════════════════════════════════════════════════════════════


@dataclass(frozen=True)
class ServerConfig:
    """Gradio HTTP server settings.

    Ref: https://www.gradio.app/docs/gradio/blocks#blocks-launch
    """
    host: str = "127.0.0.1"
    port: int = 7860
    share: bool = False
    open_browser: bool = True


@dataclass(frozen=True)
class BillingConfig:
    """Cost guardrails for the LLM pipeline.

    Ref: usecase_3 cost_guard.py
    """
    budget_usd: float = 0.50
    dry_run: bool = False


@dataclass(frozen=True)
class UIConfig:
    """Gradio theme and chatbot widget settings.

    Ref: https://www.gradio.app/guides/theming-guide
    Ref: https://www.gradio.app/docs/gradio/chatbot
    """
    theme: str = "Soft"
    title: str = "Strategic Analysis Chatbot"
    chatbot_height: int = 550
    chatbot_layout: str | None = None
    chatbot_buttons: list[str] = field(default_factory=lambda: ["copy"])
    render_markdown: bool = True
    chatbot_placeholder: str = (
        "💬 Send a message to start chatting.\n\n"
        "Try: *Analyze the strategic threat Google Cloud poses "
        "to Microsoft Azure*"
    )
    textbox_placeholder: str = (
        "Ask a strategic question... "
        "(e.g., 'Analyze Google Cloud vs Azure')"
    )


@dataclass(frozen=True)
class PipelineConfig:
    """Analysis pipeline behaviour settings."""
    max_history_turns: int = 10
    heartbeat_interval_sec: float = 10.0


@dataclass(frozen=True)
class LoggingConfig:
    """Server logging settings.
    """
    level: str = "INFO"
    format: str = "%(asctime)s  %(levelname)-7s  %(name)s  %(message)s"
    datefmt: str = "%Y-%m-%d %H:%M:%S"
    log_dir: str = "outputs"


@dataclass(frozen=True)
class ChatbotConfig:
    """Top-level configuration container — aggregates all sections.

    Access individual sections via attributes:
        cfg = load_chatbot_config(cli_args)
        cfg.server.port       # → 7860
        cfg.billing.dry_run   # → False
        cfg.ui.theme          # → "Soft"
    """
    server: ServerConfig = field(default_factory=ServerConfig)
    billing: BillingConfig = field(default_factory=BillingConfig)
    ui: UIConfig = field(default_factory=UIConfig)
    pipeline: PipelineConfig = field(default_factory=PipelineConfig)
    logging: LoggingConfig = field(default_factory=LoggingConfig)


# ═══════════════════════════════════════════════════════════════════════════
# YAML loading
# ═══════════════════════════════════════════════════════════════════════════
_CONFIG_FILENAME = "chatbot_config.yaml"


def _load_yaml(config_dir: Path) -> dict:
    """Load chatbot_config.yaml from *config_dir*. Returns {} on failure."""
    yaml_path = config_dir / _CONFIG_FILENAME
    if not yaml_path.exists():
        logger.warning("Config file not found: %s — using defaults", yaml_path)
        return {}

    try:
        import yaml
    except ImportError:
        logger.warning(
            "PyYAML not installed — cannot read %s. "
            "Install it: pip install pyyaml\n"
            "Using hardcoded defaults instead.",
            yaml_path,
        )
        return {}

    try:
        with open(yaml_path, "r", encoding="utf-8") as f:
            data = yaml.safe_load(f) or {}
        logger.info("Loaded config from %s", yaml_path)
        return data
    except Exception as exc:
        logger.warning("Failed to parse %s: %s — using defaults", yaml_path, exc)
        return {}


# ═══════════════════════════════════════════════════════════════════════════
# Merge logic: YAML values → dataclass, then CLI overrides
#
# The merge follows a simple pattern:
#   1. Start with dataclass defaults (hardcoded fallbacks)
#   2. Override with YAML values (if present)
#   3. Override with CLI values (if explicitly provided)
#
# We use a helper ``_merge()`` that only overwrites a field when the
# source dict contains a matching key with a non-None value.
# ═══════════════════════════════════════════════════════════════════════════
def _build_section(cls, yaml_section: dict) -> object:
    """Construct a frozen dataclass from YAML dict, ignoring unknown keys."""
    # Get the set of valid field names for this dataclass
    valid = {f.name for f in cls.__dataclass_fields__.values()}
    # Filter YAML to only known keys, skip None values
    filtered = {k: v for k, v in yaml_section.items() if k in valid and v is not None}
    return cls(**filtered)


def load_chatbot_config(
    cli_args: argparse.Namespace | None = None,
    config_dir: Path | None = None,
) -> ChatbotConfig:
    """
    Load configuration with the full priority chain:
        CLI args  >  chatbot_config.yaml  >  hardcoded defaults

    Args:
        cli_args:   Parsed CLI namespace (from argparse). Optional.
        config_dir: Directory containing chatbot_config.yaml.
                    Defaults to the directory of this file.

    Returns:
        A frozen ``ChatbotConfig`` instance.
    """
    if config_dir is None:
        config_dir = Path(__file__).resolve().parent

    # ── Step 1: Load YAML file ────────────────────────────────────
    raw = _load_yaml(config_dir)

    # ── Step 2: Build section dataclasses from YAML ───────────────
    server = _build_section(ServerConfig, raw.get("server", {}))
    billing = _build_section(BillingConfig, raw.get("billing", {}))
    ui = _build_section(UIConfig, raw.get("ui", {}))
    pipeline = _build_section(PipelineConfig, raw.get("pipeline", {}))
    log_cfg = _build_section(LoggingConfig, raw.get("logging", {}))

    # ── Step 3: Apply CLI overrides (highest priority) ────────────
    # CLI args only override when explicitly provided (not default values).
    # We check against known argparse conventions:
    #   - store_true flags: True means user passed the flag
    #   - typed args: we always apply them (argparse sets defaults anyway)
    if cli_args is not None:
        # Server overrides
        if hasattr(cli_args, "port") and cli_args.port is not None:
            server = ServerConfig(
                host=server.host,
                port=cli_args.port,
                share=cli_args.share if hasattr(cli_args, "share") else server.share,
                open_browser=server.open_browser,
            )
        if hasattr(cli_args, "share") and cli_args.share:
            server = ServerConfig(
                host=server.host,
                port=server.port,
                share=True,
                open_browser=server.open_browser,
            )

        # Billing overrides
        if hasattr(cli_args, "budget") and cli_args.budget is not None:
            billing = BillingConfig(
                budget_usd=cli_args.budget,
                dry_run=cli_args.dry_run if hasattr(cli_args, "dry_run") else billing.dry_run,
            )
        if hasattr(cli_args, "dry_run") and cli_args.dry_run:
            billing = BillingConfig(
                budget_usd=billing.budget_usd,
                dry_run=True,
            )

    cfg = ChatbotConfig(
        server=server,
        billing=billing,
        ui=ui,
        pipeline=pipeline,
        logging=log_cfg,
    )

    logger.info(
        "Config loaded: host=%s port=%d share=%s budget=$%.2f dry_run=%s "
        "theme=%s heartbeat=%.1fs log_level=%s",
        cfg.server.host, cfg.server.port, cfg.server.share,
        cfg.billing.budget_usd, cfg.billing.dry_run,
        cfg.ui.theme, cfg.pipeline.heartbeat_interval_sec,
        cfg.logging.level,
    )

    return cfg


# ═══════════════════════════════════════════════════════════════════════════
# Theme resolver — maps string name → Gradio theme object
#
# This must be called AFTER importing Gradio (since theme classes live
# in the gradio.themes module). We keep it in app_config.py so main.py
# can simply call resolve_theme(cfg.ui.theme).
#
# Ref: https://www.gradio.app/guides/theming-guide
# ═══════════════════════════════════════════════════════════════════════════
def resolve_theme(theme_name: str):
    """
    Convert a theme name string (e.g. "Soft") to a Gradio theme instance.

    Supported names (case-insensitive):
        Soft, Default, Monochrome, Glass, Base, Citrus, Ocean,
        Origin, Neon, Cyberpunk, Ember

    Falls back to gr.themes.Soft() if the name is not recognised.
    """
    try:
        import gradio as gr
    except ImportError:
        return None

    # Map lowercase name → theme constructor
    theme_map = {
        "soft": gr.themes.Soft,
        "default": gr.themes.Default,
        "monochrome": gr.themes.Monochrome,
        "glass": gr.themes.Glass,
        "base": gr.themes.Base,
        "citrus": gr.themes.Citrus,
        "ocean": gr.themes.Ocean,
        "origin": gr.themes.Origin,
        "neon": gr.themes.Neon,
        "cyberpunk": gr.themes.Cyberpunk,
        "ember": gr.themes.Ember,
    }

    constructor = theme_map.get(theme_name.lower().strip())
    if constructor is None:
        logger.warning(
            "Unknown theme '%s' — falling back to Soft. "
            "Available: %s",
            theme_name, ", ".join(sorted(theme_map.keys())),
        )
        constructor = gr.themes.Soft

    return constructor()

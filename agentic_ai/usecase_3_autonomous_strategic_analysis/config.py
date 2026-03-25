"""
Centralised configuration for the Autonomous Strategic Analysis system.

Loads the Gemini API key from (in order):
  1. Environment variable  GEMINI_API_KEY
  2. .env file in this folder or any ancestor up to the workspace root
  3. local.properties anywhere up the folder tree

Exposes typed, frozen dataclass configs consumed by every module.
"""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path


# ---------------------------------------------------------------------------
# Gemini
# ---------------------------------------------------------------------------
@dataclass(frozen=True)
class GeminiConfig:
    api_key: str
    # ── Model selection (cheapest options for learning) ──────────
    generation_model: str = "gemini-2.5-flash"  # $0.15/$0.60 per 1M
    embedding_model: str = "gemini-embedding-001"                # FREE
    base_url: str = "https://generativelanguage.googleapis.com/v1beta"
    # ── Token limits (kept low for cost control) ────────────────
    max_output_tokens: int = 4096       # halved from 8192
    temperature: float = 0.25
    top_p: float = 0.95
    request_timeout: int = 300
    max_retries: int = 5
    retry_base_delay: float = 2.0


# ---------------------------------------------------------------------------
# Billing / cost guard
# ---------------------------------------------------------------------------
@dataclass(frozen=True)
class BillingConfig:
    budget_usd: float = 0.50            # hard cap — halts execution if exceeded
    dry_run: bool = False               # True → skip ALL LLM calls (zero cost)
    warn_at_pct: float = 0.70           # print warning at 70% spend
    print_cost_per_call: bool = True    # log cost after every API call


# ---------------------------------------------------------------------------
# Retrieval pipeline
# ---------------------------------------------------------------------------
@dataclass(frozen=True)
class RetrievalConfig:
    chunk_size_tokens: int = 480
    chunk_overlap_tokens: int = 60
    bm25_k1: float = 1.5
    bm25_b: float = 0.75
    hybrid_alpha: float = 0.55           # lean toward sparse (free BM25)
    rrf_k: int = 60                      # Reciprocal Rank Fusion constant
    top_k_retrieval: int = 15            # reduced from 20 → fewer embeddings
    top_k_rerank: int = 6               # reduced from 8 → fewer LLM tokens
    rerank_relevance_floor: float = 0.35 # stricter floor → fewer chunks pass
    context_token_budget: int = 4000     # reduced from 6000 → tighter prompts


# ---------------------------------------------------------------------------
# Agent behaviour
# ---------------------------------------------------------------------------
@dataclass(frozen=True)
class AgentConfig:
    max_critique_loops: int = 2
    max_plan_steps: int = 10


# ---------------------------------------------------------------------------
# Top-level app config
# ---------------------------------------------------------------------------
@dataclass
class AppConfig:
    gemini: GeminiConfig
    billing: BillingConfig = field(default_factory=BillingConfig)
    retrieval: RetrievalConfig = field(default_factory=RetrievalConfig)
    agent: AgentConfig = field(default_factory=AgentConfig)
    base_path: Path = field(default_factory=lambda: Path(__file__).resolve().parent)
    cache_dir: Path = field(default_factory=lambda: Path(__file__).resolve().parent / ".cache")


# ---------------------------------------------------------------------------
# Key resolution helpers
# ---------------------------------------------------------------------------
def _scan_file_for_key(path: Path, prefix: str = "GEMINI_API_KEY=") -> str | None:
    if not path.exists():
        return None
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        stripped = line.strip()
        if stripped.startswith(prefix):
            val = stripped.split("=", 1)[1].strip().strip("\"'")
            if val and val != "your-api-key-here":
                return val
    return None


def _load_api_key(start: Path) -> str:
    """Walk up from *start* looking for a key in env / .env / local.properties."""
    key = os.environ.get("GEMINI_API_KEY", "").strip()
    if key:
        return key

    search = start.resolve()
    for _ in range(8):                            # up to 8 levels
        for name in (".env", "local.properties"):
            found = _scan_file_for_key(search / name)
            if found:
                return found
        parent = search.parent
        if parent == search:
            break
        search = parent

    raise RuntimeError(
        "GEMINI_API_KEY not found.  Provide it via:\n"
        "  1. Environment variable GEMINI_API_KEY\n"
        "  2. .env   file with GEMINI_API_KEY=<key>\n"
        "  3. local.properties with GEMINI_API_KEY=<key>\n"
        "Searched upward from: " + str(start)
    )


def load_config(
    base_path: Path | None = None,
    *,
    budget_usd: float = 0.50,
    dry_run: bool = False,
) -> AppConfig:
    """Load config.  Pass ``dry_run=True`` to skip all paid API calls."""
    base = (base_path or Path(__file__).resolve().parent).resolve()
    api_key = _load_api_key(base)
    return AppConfig(
        gemini=GeminiConfig(api_key=api_key),
        billing=BillingConfig(budget_usd=budget_usd, dry_run=dry_run),
        base_path=base,
        cache_dir=base / ".cache",
    )

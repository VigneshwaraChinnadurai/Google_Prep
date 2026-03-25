"""
Cost guard — real-time billing estimator and hard budget cap.

Gemini pricing (as of March 2025, Google AI Studio / pay-as-you-go)
===================================================================
Model                     Input (/1M tok)   Output (/1M tok)   Notes
─────────────────────────────────────────────────────────────────────
gemini-2.5-flash          $0.15             $0.60              cheapest flash
gemini-2.0-flash          $0.10             $0.40              cheapest overall
gemini-1.5-flash          $0.075            $0.30              legacy cheap
gemini-2.5-pro            $1.25             $10.00             expensive!
text-embedding-004        $0.00             free               free tier

With Google AI Studio free tier you get:
  • 15 RPM, 1M TPM, 1500 RPD for flash models   (FREE)
  • Grounding: 500 free grounded queries/day

So for a learning project, you should stay well within the FREE tier.
This module enforces a hard cap anyway as a safety net.

Usage
-----
    guard = CostGuard(budget_usd=0.50)
    guard.record(input_tokens=1200, output_tokens=400, model="gemini-2.5-flash")
    guard.check()        # raises BudgetExceeded if cap hit
    guard.summary()      # prints running total
"""
from __future__ import annotations

import logging
from dataclasses import dataclass, field

logger = logging.getLogger(__name__)


class BudgetExceeded(RuntimeError):
    """Raised when the cost guard's hard budget cap is reached."""


# Per-million-token pricing
_PRICING: dict[str, tuple[float, float]] = {
    # model_prefix → (input_usd_per_1M, output_usd_per_1M)
    "gemini-2.5-flash":  (0.15,  0.60),
    "gemini-2.0-flash":  (0.10,  0.40),
    "gemini-1.5-flash":  (0.075, 0.30),
    "gemini-2.5-pro":    (1.25,  10.00),
    "gemini-1.5-pro":    (1.25,  5.00),
    "text-embedding":    (0.00,  0.00),    # free
}


def _price_for(model: str) -> tuple[float, float]:
    """Find best-matching pricing row for *model* string."""
    model_l = model.lower()
    for prefix, prices in _PRICING.items():
        if prefix in model_l:
            return prices
    # conservative fallback → flash pricing
    return (0.15, 0.60)


@dataclass
class CostGuard:
    """
    Tracks cumulative token usage and estimated USD cost.

    Set ``budget_usd`` to a hard cap (default $0.50 — generous for free-tier).
    Set ``dry_run=True`` to skip all LLM calls entirely (zero cost).
    """
    budget_usd: float = 0.50
    dry_run: bool = False

    # accumulators
    total_input_tokens: int = field(default=0, init=False)
    total_output_tokens: int = field(default=0, init=False)
    total_embedding_tokens: int = field(default=0, init=False)
    total_cost_usd: float = field(default=0.0, init=False)
    api_calls: int = field(default=0, init=False)
    cache_hits: int = field(default=0, init=False)

    def record(self, input_tokens: int, output_tokens: int, model: str) -> float:
        """Record a completed API call. Returns incremental cost in USD."""
        ip, op = _price_for(model)
        cost = (input_tokens * ip + output_tokens * op) / 1_000_000
        self.total_input_tokens += input_tokens
        self.total_output_tokens += output_tokens
        self.total_cost_usd += cost
        self.api_calls += 1
        return cost

    def record_embedding(self, tokens: int) -> None:
        """Embeddings are free for text-embedding-004 but tracked."""
        self.total_embedding_tokens += tokens

    def record_cache_hit(self) -> None:
        self.cache_hits += 1

    def check(self) -> None:
        """Raise ``BudgetExceeded`` if cumulative cost exceeds the cap."""
        if self.total_cost_usd >= self.budget_usd:
            raise BudgetExceeded(
                f"Budget cap reached!  Spent ~${self.total_cost_usd:.4f} "
                f"(limit ${self.budget_usd:.2f}).  "
                f"Tokens: {self.total_input_tokens} in / {self.total_output_tokens} out / "
                f"{self.api_calls} API calls / {self.cache_hits} cache hits."
            )

    def summary(self) -> str:
        pct = (self.total_cost_usd / self.budget_usd * 100) if self.budget_usd > 0 else 0
        return (
            f"╔══ COST SUMMARY ══════════════════════════════════════╗\n"
            f"║  API calls:        {self.api_calls:>6}                         ║\n"
            f"║  Cache hits:       {self.cache_hits:>6}  (saved API calls)      ║\n"
            f"║  Input tokens:     {self.total_input_tokens:>8,}                      ║\n"
            f"║  Output tokens:    {self.total_output_tokens:>8,}                      ║\n"
            f"║  Embedding tokens: {self.total_embedding_tokens:>8,}  (free)              ║\n"
            f"║  Estimated cost:   ${self.total_cost_usd:>8.4f}                      ║\n"
            f"║  Budget remaining: ${self.budget_usd - self.total_cost_usd:>8.4f}  "
            f"({100 - pct:.1f}% left)       ║\n"
            f"╚══════════════════════════════════════════════════════╝"
        )

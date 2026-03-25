"""
Context fusion — assembles re-ranked chunks into a single coherent context
string that fits within a token budget.

Steps
-----
1. De-duplicate near-identical chunks (Jaccard on token sets).
2. Order by relevance score (highest first).
3. Accumulate chunks until the token budget is reached.
4. Format with source attribution headers.
"""
from __future__ import annotations

from typing import Sequence

from . import SearchResult


def _jaccard(a: str, b: str) -> float:
    sa = set(a.lower().split())
    sb = set(b.lower().split())
    inter = sa & sb
    union = sa | sb
    return len(inter) / len(union) if union else 0.0


def _estimate_tokens(text: str) -> int:
    return max(1, len(text) // 4)


class ContextFuser:
    """Merge re-ranked chunks into a token-bounded context string."""

    def __init__(self, token_budget: int = 6000, dedup_threshold: float = 0.80) -> None:
        self.budget = token_budget
        self.dedup_thresh = dedup_threshold

    def fuse(self, results: Sequence[SearchResult]) -> str:
        if not results:
            return "(No relevant context found.)"

        # 1. de-duplicate
        kept: list[SearchResult] = []
        for sr in results:
            if any(_jaccard(sr.chunk.text, k.chunk.text) > self.dedup_thresh for k in kept):
                continue
            kept.append(sr)

        # 2. accumulate within budget
        parts: list[str] = []
        used = 0
        for sr in kept:
            cost = _estimate_tokens(sr.chunk.text) + 10  # header overhead
            if used + cost > self.budget:
                break
            meta = sr.chunk.metadata
            src = meta.get("source_type", "unknown")
            company = meta.get("company", "")
            header = f"[source={src} company={company} score={sr.score:.2f}]"
            parts.append(f"{header}\n{sr.chunk.text}")
            used += cost

        return "\n\n---\n\n".join(parts) if parts else "(No relevant context found.)"

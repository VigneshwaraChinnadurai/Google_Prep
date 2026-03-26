"""
Hybrid index — merges Sparse (BM25) and Dense (embedding) retrieval with
Reciprocal Rank Fusion (RRF) and optional metadata filtering.

RRF formula
-----------
    score_rrf(d) =  Σ_{r ∈ rankings}   1 / (k + rank_r(d))

With an optional alpha parameter to weight sparse vs dense before fusion.
"""
from __future__ import annotations

from collections import defaultdict
from typing import Sequence

from . import Chunk, SearchResult
from .sparse_retrieval import BM25Index
from .dense_retrieval import DenseIndex


class HybridIndex:
    """Combines BM25 and embedding search via Reciprocal Rank Fusion."""

    def __init__(
        self,
        sparse: BM25Index,
        dense: DenseIndex,
        *,
        alpha: float = 0.4,
        rrf_k: int = 60,
    ) -> None:
        self._sparse = sparse
        self._dense = dense
        self.alpha = alpha          # higher → more weight on sparse
        self.rrf_k = rrf_k

    def build(self, chunks: Sequence[Chunk]) -> None:
        self._sparse.build(chunks)
        self._dense.build(chunks)

    def search(
        self,
        query: str,
        top_k: int = 20,
        *,
        filter_meta: dict | None = None,
    ) -> list[SearchResult]:
        """
        1. Run sparse + dense searches
        2. Apply RRF to merge rankings
        3. Optionally filter by metadata
        """
        sp = self._sparse.search(query, top_k=top_k * 2)
        dn = self._dense.search(query, top_k=top_k * 2)

        # id → chunk mapping
        chunk_map: dict[str, Chunk] = {}
        for sr in sp + dn:
            chunk_map[sr.chunk.chunk_id] = sr.chunk

        # RRF scoring
        scores: dict[str, float] = defaultdict(float)
        for sr in sp:
            scores[sr.chunk.chunk_id] += self.alpha / (self.rrf_k + sr.rank)
        for sr in dn:
            scores[sr.chunk.chunk_id] += (1.0 - self.alpha) / (self.rrf_k + sr.rank)

        # metadata filter
        if filter_meta:
            scores = {
                cid: s for cid, s in scores.items()
                if all(chunk_map[cid].metadata.get(k) == v for k, v in filter_meta.items())
            }

        ranked = sorted(scores.items(), key=lambda x: x[1], reverse=True)[:top_k]
        return [
            SearchResult(chunk=chunk_map[cid], score=s, rank=r + 1)
            for r, (cid, s) in enumerate(ranked)
        ]

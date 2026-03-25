"""
Dense retrieval via Gemini text-embedding-004 embeddings.

Each chunk is embedded once (cached by GeminiClient).
Queries use cosine similarity against the stored vectors.
"""
from __future__ import annotations

import math
from typing import Sequence

from . import Chunk, SearchResult
from llm_client import GeminiClient


def _cosine(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a)) or 1e-9
    nb = math.sqrt(sum(x * x for x in b)) or 1e-9
    return dot / (na * nb)


class DenseIndex:
    """Embedding-based dense index backed by Gemini embeddings."""

    def __init__(self, llm: GeminiClient) -> None:
        self._llm = llm
        self._chunks: list[Chunk] = []
        self._vecs: list[list[float]] = []

    def build(self, chunks: Sequence[Chunk]) -> None:
        self._chunks = list(chunks)
        self._vecs = []

        # Collect chunks that need embedding
        needs_embed: list[int] = []
        texts: list[str] = []
        for i, ch in enumerate(self._chunks):
            if ch.embedding:
                self._vecs.append(ch.embedding)
            else:
                self._vecs.append([])      # placeholder
                needs_embed.append(i)
                texts.append(ch.text)

        if texts:
            results = self._llm.embed_batch(texts, task="RETRIEVAL_DOCUMENT")
            for j, idx in enumerate(needs_embed):
                vals = results[j].values if results[j] else []
                self._chunks[idx].embedding = vals
                self._vecs[idx] = vals

    def search(self, query: str, top_k: int = 20) -> list[SearchResult]:
        if not self._chunks:
            return []
        qv = self._llm.embed(query, task="RETRIEVAL_QUERY").values
        if not qv:
            return []

        scored = [(i, _cosine(qv, v)) for i, v in enumerate(self._vecs) if v]
        scored.sort(key=lambda x: x[1], reverse=True)

        return [
            SearchResult(chunk=self._chunks[i], score=s, rank=r + 1)
            for r, (i, s) in enumerate(scored[:top_k])
        ]

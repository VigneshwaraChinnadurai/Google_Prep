"""
BM25 sparse retrieval index — implemented from scratch.

Okapi BM25 scoring
-------------------
    score(q, d) = Σ_{t in q}  IDF(t) · (tf(t,d) · (k1+1)) / (tf(t,d) + k1 · (1 - b + b · |d|/avgdl))

where  IDF(t) = ln((N - n(t) + 0.5) / (n(t) + 0.5) + 1)

Stop-word list is minimal on purpose — the reranker handles noise.
"""
from __future__ import annotations

import math
import re
from collections import Counter, defaultdict
from typing import Sequence

from . import Chunk, SearchResult

_TOKEN_RE = re.compile(r"[a-z0-9]+", re.IGNORECASE)
_STOP = frozenset(
    "a an and are as at be but by for if in into is it no not of on or such "
    "that the their then there these they this to was will with".split()
)


def _tokenize(text: str) -> list[str]:
    return [w.lower() for w in _TOKEN_RE.findall(text) if w.lower() not in _STOP]


class BM25Index:
    """In-memory BM25 index over Chunk objects."""

    def __init__(self, k1: float = 1.5, b: float = 0.75) -> None:
        self.k1 = k1
        self.b = b
        self._chunks: list[Chunk] = []
        self._doc_tfs: list[Counter] = []
        self._doc_lens: list[int] = []
        self._df: dict[str, int] = defaultdict(int)
        self._avgdl: float = 0.0
        self._N: int = 0

    def build(self, chunks: Sequence[Chunk]) -> None:
        """(Re-)build the index from *chunks*."""
        self._chunks = list(chunks)
        self._doc_tfs.clear()
        self._doc_lens.clear()
        self._df = defaultdict(int)
        self._N = len(self._chunks)

        for ch in self._chunks:
            tokens = _tokenize(ch.text)
            tf = Counter(tokens)
            self._doc_tfs.append(tf)
            self._doc_lens.append(len(tokens))
            for term in tf:
                self._df[term] += 1

        self._avgdl = (sum(self._doc_lens) / self._N) if self._N else 1.0

    def search(self, query: str, top_k: int = 20) -> list[SearchResult]:
        q_tokens = _tokenize(query)
        if not q_tokens or not self._chunks:
            return []

        scores: list[float] = []
        for idx in range(self._N):
            s = 0.0
            dl = self._doc_lens[idx]
            tf_map = self._doc_tfs[idx]
            for t in q_tokens:
                if t not in tf_map:
                    continue
                tf = tf_map[t]
                n = self._df.get(t, 0)
                idf = math.log((self._N - n + 0.5) / (n + 0.5) + 1.0)
                num = tf * (self.k1 + 1.0)
                den = tf + self.k1 * (1.0 - self.b + self.b * dl / self._avgdl)
                s += idf * num / den
            scores.append(s)

        ranked = sorted(range(self._N), key=lambda i: scores[i], reverse=True)[:top_k]
        return [
            SearchResult(chunk=self._chunks[i], score=scores[i], rank=r + 1)
            for r, i in enumerate(ranked) if scores[i] > 0
        ]

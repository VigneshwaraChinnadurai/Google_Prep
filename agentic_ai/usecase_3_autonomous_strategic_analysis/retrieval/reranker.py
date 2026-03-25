"""
LLM-based relevance re-ranker.

For each candidate chunk the LLM scores relevance to the query on 0-1.
Chunks below ``threshold`` are dropped; the rest are returned sorted.

Token optimisation: chunks are batched into a single prompt (up to a safe
limit) so the LLM scores several chunks per API call.
"""
from __future__ import annotations

import json
import logging
from typing import Sequence

from . import SearchResult
from llm_client import GeminiClient

logger = logging.getLogger(__name__)

_SYSTEM = (
    "You are a relevance judge. Given a QUERY and a numbered list of TEXT "
    "chunks, output a JSON array of objects [{\"id\": <int>, \"score\": <float 0-1>}] "
    "where score reflects how relevant that chunk is to the query. "
    "1.0 = perfectly relevant, 0.0 = irrelevant. Be strict."
)

_MAX_CHUNKS_PER_CALL = 10          # keep prompt within safe token range

_RERANKER_SCHEMA = {
    "type": "ARRAY",
    "items": {
        "type": "OBJECT",
        "properties": {
            "id": {"type": "INTEGER"},
            "score": {"type": "NUMBER"},
        },
        "required": ["id", "score"],
    },
}


class LLMReranker:
    """Score-and-filter re-ranker powered by Gemini."""

    def __init__(self, llm: GeminiClient) -> None:
        self._llm = llm

    def rerank(
        self,
        query: str,
        results: Sequence[SearchResult],
        top_k: int = 8,
        threshold: float = 0.3,
    ) -> list[SearchResult]:
        if not results:
            return []

        scored: list[tuple[SearchResult, float]] = []

        # batch chunks into groups
        batches = [results[i:i + _MAX_CHUNKS_PER_CALL]
                   for i in range(0, len(results), _MAX_CHUNKS_PER_CALL)]

        for batch in batches:
            numbered = "\n\n".join(
                f"[{i}] {sr.chunk.text[:600]}" for i, sr in enumerate(batch)
            )
            prompt = f"QUERY: {query}\n\nCHUNKS:\n{numbered}"
            resp = self._llm.generate(prompt, system=_SYSTEM, json_mode=True,
                                       response_schema=_RERANKER_SCHEMA,
                                       thinking_budget=0,
                                       temperature=0.0, max_tokens=1024)
            try:
                arr = GeminiClient.parse_json(resp.text)
                score_map = {item["id"]: float(item["score"]) for item in arr}
            except (json.JSONDecodeError, KeyError, TypeError):
                logger.warning("Reranker JSON parse failed, using original scores")
                score_map = {i: sr.score for i, sr in enumerate(batch)}

            for i, sr in enumerate(batch):
                s = score_map.get(i, 0.0)
                scored.append((sr, s))

        # filter + sort
        scored = [(sr, s) for sr, s in scored if s >= threshold]
        scored.sort(key=lambda x: x[1], reverse=True)

        return [
            SearchResult(chunk=sr.chunk, score=s, rank=r + 1)
            for r, (sr, s) in enumerate(scored[:top_k])
        ]

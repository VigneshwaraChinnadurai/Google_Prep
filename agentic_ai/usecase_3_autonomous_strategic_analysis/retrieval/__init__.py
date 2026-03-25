"""
retrieval — Sparse + Dense hybrid retrieval pipeline.

Public surface
--------------
* Document, Chunk, SearchResult  — core data types
* RetrievalPipeline              — single facade that chains
      ingest → chunk → enrich → index → search → rerank → fuse
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from config import RetrievalConfig
    from llm_client import GeminiClient


# ── Shared data types ────────────────────────────────────────────────────
@dataclass
class Document:
    """A raw document obtained from an external source."""
    content: str
    doc_id: str
    metadata: dict = field(default_factory=dict)   # source, company, date, url …


@dataclass
class Chunk:
    """A token-bounded slice of a Document."""
    text: str
    chunk_id: str
    doc_id: str
    start_char: int = 0
    end_char: int = 0
    metadata: dict = field(default_factory=dict)
    embedding: list[float] | None = None


@dataclass
class SearchResult:
    """A scored Chunk returned by the hybrid index."""
    chunk: Chunk
    score: float
    rank: int = 0


# ── Pipeline facade ─────────────────────────────────────────────────────
class RetrievalPipeline:
    """
    End-to-end: ingest → chunk → metadata → sparse+dense index → search → rerank → fuse.

    Usage::

        pipe = RetrievalPipeline(llm, cfg)
        pipe.ingest(docs)
        context_str = pipe.query("What is Google Cloud's operating margin?")
    """

    def __init__(self, llm: "GeminiClient", cfg: "RetrievalConfig") -> None:
        from .chunker import chunk_documents
        from .metadata_enricher import enrich_chunks
        from .sparse_retrieval import BM25Index
        from .dense_retrieval import DenseIndex
        from .hybrid_index import HybridIndex
        from .reranker import LLMReranker
        from .context_fusion import ContextFuser

        self.cfg = cfg
        self._chunk_fn = chunk_documents
        self._enrich_fn = enrich_chunks

        self._sparse = BM25Index(k1=cfg.bm25_k1, b=cfg.bm25_b)
        self._dense = DenseIndex(llm)
        self._hybrid = HybridIndex(self._sparse, self._dense,
                                   alpha=cfg.hybrid_alpha, rrf_k=cfg.rrf_k)
        self._reranker = LLMReranker(llm)
        self._fuser = ContextFuser(token_budget=cfg.context_token_budget)
        self._chunks: list[Chunk] = []

    # ── Ingest ───────────────────────────────────────────────────────
    def ingest(self, documents: list[Document]) -> int:
        """Chunk, enrich, and index *documents*. Returns chunk count."""
        raw = self._chunk_fn(documents,
                             chunk_size=self.cfg.chunk_size_tokens,
                             overlap=self.cfg.chunk_overlap_tokens)
        enriched = self._enrich_fn(raw)
        self._chunks.extend(enriched)
        self._hybrid.build(enriched)
        return len(enriched)

    # ── Query ────────────────────────────────────────────────────────
    def query(self, query: str, top_k: int | None = None) -> str:
        """Return a fused context string for *query*."""
        k_retrieve = self.cfg.top_k_retrieval
        k_rerank = top_k or self.cfg.top_k_rerank

        results = self._hybrid.search(query, k_retrieve)
        reranked = self._reranker.rerank(query, results, k_rerank,
                                         threshold=self.cfg.rerank_relevance_floor)
        return self._fuser.fuse(reranked)

    @property
    def chunk_count(self) -> int:
        return len(self._chunks)

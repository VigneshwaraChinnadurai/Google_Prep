"""
Sentence-aware document chunker with configurable overlap.

Strategy
--------
1.  Split on sentence boundaries (period / newline).
2.  Accumulate sentences until the estimated token count reaches ``chunk_size``.
3.  Slide back by ``overlap`` tokens worth of sentences for the next chunk.
4.  Preserve document-level metadata on every chunk.
"""
from __future__ import annotations

import re
import uuid
from typing import Sequence

from . import Chunk, Document

_SENT_RE = re.compile(r"(?<=[.!?])\s+|\n{2,}")


def _estimate_tokens(text: str) -> int:
    return max(1, len(text) // 4)


def _split_sentences(text: str) -> list[str]:
    parts = _SENT_RE.split(text)
    return [p.strip() for p in parts if p.strip()]


def chunk_documents(
    documents: Sequence[Document],
    *,
    chunk_size: int = 480,
    overlap: int = 60,
) -> list[Chunk]:
    """Return a flat list of Chunks across all *documents*."""
    all_chunks: list[Chunk] = []
    for doc in documents:
        sents = _split_sentences(doc.content)
        if not sents:
            continue

        buf: list[str] = []
        buf_tokens = 0
        start_char = 0

        for sent in sents:
            stok = _estimate_tokens(sent)
            if buf and buf_tokens + stok > chunk_size:
                text = " ".join(buf)
                all_chunks.append(Chunk(
                    text=text,
                    chunk_id=uuid.uuid4().hex[:12],
                    doc_id=doc.doc_id,
                    start_char=start_char,
                    end_char=start_char + len(text),
                    metadata=dict(doc.metadata),
                ))
                # slide back for overlap
                overlap_toks = 0
                cut = len(buf)
                for i in range(len(buf) - 1, -1, -1):
                    overlap_toks += _estimate_tokens(buf[i])
                    if overlap_toks >= overlap:
                        cut = i
                        break
                removed = buf[:cut]
                start_char += sum(len(s) + 1 for s in removed)
                buf = buf[cut:]
                buf_tokens = sum(_estimate_tokens(s) for s in buf)

            buf.append(sent)
            buf_tokens += stok

        if buf:
            text = " ".join(buf)
            all_chunks.append(Chunk(
                text=text,
                chunk_id=uuid.uuid4().hex[:12],
                doc_id=doc.doc_id,
                start_char=start_char,
                end_char=start_char + len(text),
                metadata=dict(doc.metadata),
            ))

    return all_chunks

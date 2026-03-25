"""
Metadata enricher — decorates every Chunk with structured tags.

Tags added
----------
* company   – GOOGL / MSFT / AMZN / cross_market / unknown
* topic     – revenue / growth / ai_ml / cloud / strategy / competitive / general
* source_type – sec_filing / earnings_call / analyst / news / grounded_search / unknown
* date      – ISO date string if detectable
"""
from __future__ import annotations

import re
from typing import Sequence

from . import Chunk

_COMPANY_RULES: list[tuple[re.Pattern, str]] = [
    (re.compile(r"google\s*cloud|alphabet|googl|vertex\s*ai|bigquery|gemini", re.I), "GOOGL"),
    (re.compile(r"azure|microsoft|msft|copilot|office\s*365|synapse", re.I), "MSFT"),
    (re.compile(r"aws|amazon|amzn|sagemaker|bedrock", re.I), "AMZN"),
]

_TOPIC_RULES: list[tuple[re.Pattern, str]] = [
    (re.compile(r"revenue|sales|billion|earning", re.I), "revenue"),
    (re.compile(r"growth|yoy|qoq|quarter.over.quarter", re.I), "growth"),
    (re.compile(r"ai|ml|machine.learn|model|vertex|gemini|copilot|bedrock", re.I), "ai_ml"),
    (re.compile(r"cloud|infrastructure|compute|data.center", re.I), "cloud"),
    (re.compile(r"strateg|compet|threat|moat|acqui", re.I), "strategy"),
    (re.compile(r"market.share|benchmark|compar", re.I), "competitive"),
]

_DATE_RE = re.compile(r"(20\d{2}[-/]\d{2}[-/]\d{2}|Q[1-4]\s*20\d{2}|FY20\d{2})", re.I)


def _detect(text: str, rules: list[tuple[re.Pattern, str]]) -> list[str]:
    return list({tag for pat, tag in rules if pat.search(text)})


def enrich_chunks(chunks: Sequence[Chunk]) -> list[Chunk]:
    """Return enriched copies of *chunks* with metadata tags."""
    out: list[Chunk] = []
    for ch in chunks:
        meta = dict(ch.metadata)
        txt = ch.text

        # Company
        companies = _detect(txt, _COMPANY_RULES)
        meta.setdefault("company", companies[0] if len(companies) == 1 else
                         ("cross_market" if len(companies) > 1 else "unknown"))
        meta["companies_mentioned"] = companies or ["unknown"]

        # Topic
        topics = _detect(txt, _TOPIC_RULES)
        meta.setdefault("topic", topics[0] if topics else "general")
        meta["topics"] = topics or ["general"]

        # Date
        dm = _DATE_RE.search(txt)
        if dm:
            meta.setdefault("date", dm.group(1))

        out.append(Chunk(
            text=ch.text, chunk_id=ch.chunk_id, doc_id=ch.doc_id,
            start_char=ch.start_char, end_char=ch.end_char,
            metadata=meta, embedding=ch.embedding,
        ))
    return out

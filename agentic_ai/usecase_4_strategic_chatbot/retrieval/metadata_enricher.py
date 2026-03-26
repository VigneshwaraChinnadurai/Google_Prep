"""
Dynamic metadata enricher — company patterns from SearchPlan.

Tags added
----------
* company     – ticker from SearchPlan / cross_market / unknown
* topic       – revenue / growth / ai_ml / cloud / strategy / competitive / general
* source_type – sec_filing / earnings_call / analyst / news / grounded_search / unknown
* date        – ISO date string if detectable

Changes from usecase_3:
-----------------------
- ``_COMPANY_RULES`` is no longer hardcoded to GOOGL/MSFT/AMZN.
- ``enrich_chunks()`` now accepts an optional ``company_patterns`` parameter
  generated from SearchPlan.company_patterns.
- Topic rules remain generic (they work for any domain).
"""
from __future__ import annotations

import re
from typing import Sequence

from . import Chunk

# ── Topic rules are domain-agnostic — kept as-is ────────────────────
_TOPIC_RULES: list[tuple[re.Pattern, str]] = [
    (re.compile(r"revenue|sales|billion|earning", re.I), "revenue"),
    (re.compile(r"growth|yoy|qoq|quarter.over.quarter", re.I), "growth"),
    (re.compile(r"ai|ml|machine.learn|model|neural|deep.learn", re.I), "ai_ml"),
    (re.compile(r"cloud|infrastructure|compute|data.center", re.I), "cloud"),
    (re.compile(r"quantum|qubit|superconducting|error.correct", re.I), "quantum"),
    (re.compile(r"r&d|research.and.develop|patent|innovat", re.I), "research"),
    (re.compile(r"semiconductor|chip|fab|foundry|wafer|nm|nanometer", re.I), "semiconductor"),
    (re.compile(r"strateg|compet|threat|moat|acqui", re.I), "strategy"),
    (re.compile(r"market.share|benchmark|compar", re.I), "competitive"),
]

_DATE_RE = re.compile(r"(20\d{2}[-/]\d{2}[-/]\d{2}|Q[1-4]\s*20\d{2}|FY20\d{2})", re.I)


def _detect(text: str, rules: list[tuple[re.Pattern, str]]) -> list[str]:
    return list({tag for pat, tag in rules if pat.search(text)})


def _build_company_rules(
    company_patterns: list[tuple[str, str]] | None = None,
) -> list[tuple[re.Pattern, str]]:
    """Build compiled regex rules from SearchPlan company patterns.

    Args:
        company_patterns: List of (regex_pattern_str, ticker) tuples
            from SearchPlan.company_patterns. If None, returns empty list
            (all chunks tagged as "unknown").
    """
    if not company_patterns:
        return []
    rules = []
    for pattern_str, ticker in company_patterns:
        try:
            rules.append((re.compile(pattern_str, re.I), ticker))
        except re.error as exc:
            import logging
            logging.getLogger(__name__).warning(
                "Invalid company regex for %s: %s — skipping", ticker, exc
            )
    return rules


def enrich_chunks(
    chunks: Sequence[Chunk],
    company_patterns: list[tuple[str, str]] | None = None,
) -> list[Chunk]:
    """Return enriched copies of *chunks* with metadata tags.

    Args:
        chunks: Sequence of Chunk objects to enrich.
        company_patterns: Optional company regex patterns from SearchPlan.
            If provided, these replace the hardcoded GOOGL/MSFT/AMZN rules.
    """
    company_rules = _build_company_rules(company_patterns)
    out: list[Chunk] = []
    for ch in chunks:
        meta = dict(ch.metadata)
        txt = ch.text

        # Company
        companies = _detect(txt, company_rules) if company_rules else []
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

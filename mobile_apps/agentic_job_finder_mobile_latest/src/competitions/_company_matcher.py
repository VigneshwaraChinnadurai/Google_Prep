"""Match raw sponsor names to canonical companies in companies.yaml.

Normalizes by lowercasing and stripping common legal suffixes / punctuation,
then does bidirectional substring matching. Conservative: prefers no match
over a wrong match.
"""
from __future__ import annotations
import re
from functools import lru_cache
from src.config import load_companies

_SUFFIXES = re.compile(
    r"\b(inc|incorporated|ltd|limited|llc|llp|pvt|private|corp|corporation|"
    r"company|co|gmbh|sa|ag|plc|labs|technologies|tech|systems|solutions|"
    r"software|india|global)\b\.?",
    re.IGNORECASE,
)
_NON_ALNUM = re.compile(r"[^a-z0-9]+")


def _normalize(name: str) -> str:
    if not name:
        return ""
    s = name.lower()
    s = _SUFFIXES.sub("", s)
    s = _NON_ALNUM.sub(" ", s).strip()
    s = re.sub(r"\s+", " ", s)
    return s


@lru_cache(maxsize=1)
def _tracked_index() -> dict[str, str]:
    """Map normalized → canonical name, once per process."""
    return {_normalize(c.name): c.name for c in load_companies()}


def match_company(raw: str | None) -> str | None:
    """Return canonical company name if a tracked match is found, else None."""
    if not raw:
        return None
    norm = _normalize(raw)
    if not norm:
        return None
    idx = _tracked_index()

    # 1. exact normalized match
    if norm in idx:
        return idx[norm]

    # 2. bidirectional substring match (longer side wins ambiguity)
    candidates: list[tuple[int, str]] = []
    for tracked_norm, canonical in idx.items():
        if not tracked_norm:
            continue
        if tracked_norm in norm or norm in tracked_norm:
            # length-of-overlap-ish heuristic
            score = min(len(tracked_norm), len(norm))
            candidates.append((score, canonical))

    if not candidates:
        return None
    # take the highest-scoring candidate
    candidates.sort(reverse=True)
    return candidates[0][1]

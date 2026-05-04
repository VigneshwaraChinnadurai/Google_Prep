"""Resume parser agent — PDF/DOCX → Resume.

Stub: drop-in your full Strands-based parser here. The orchestrator only
calls .parse() and .load_cached(); keep those signatures stable.
"""
from __future__ import annotations
import json
from pathlib import Path
from src.schemas import Resume

DATA_DIR = Path("data")


class ResumeParserAgent:
    def __init__(self) -> None:
        DATA_DIR.mkdir(exist_ok=True)
        self._cache_path = DATA_DIR / "profile.json"

    def load_cached(self) -> Resume | None:
        if not self._cache_path.exists():
            return None
        return Resume.model_validate_json(self._cache_path.read_text())

    async def parse(self, file_path: str | Path, *, persist: bool = True) -> Resume:
        # TODO: replace with Strands-driven parsing using pypdf/python-docx.
        raise NotImplementedError("Wire your existing resume parser here.")

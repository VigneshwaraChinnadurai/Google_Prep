"""
Orchestrator — the top-level pipeline.

This ties everything together:
    resume -> parse -> scrape -> match -> (user approval in UI) -> apply

The orchestrator exposes stateful pipeline methods so the Streamlit UI can
call them incrementally (parse once, scrape on demand, apply per-job).
"""
from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Optional

from ..config import DATA_DIR, load_settings
from ..models import Job, MatchResult, Resume
from .applicator_agent import ApplicatorAgent
from .matcher_agent import MatcherAgent
from .resume_parser import ResumeParserAgent
from .scraper_agent import ScraperAgent


class Orchestrator:
    def __init__(self):
        self.settings = load_settings()
        self._resume: Optional[Resume] = None
        self._jobs: list[Job] = []
        self._matches: list[MatchResult] = []

        # Lazily instantiate agents — each holds an LLM client.
        self._parser: Optional[ResumeParserAgent] = None
        self._scraper: Optional[ScraperAgent] = None
        self._matcher: Optional[MatcherAgent] = None
        self._applicator: Optional[ApplicatorAgent] = None

    # ---- lazy agent getters ----

    @property
    def parser(self) -> ResumeParserAgent:
        if self._parser is None:
            self._parser = ResumeParserAgent()
        return self._parser

    @property
    def scraper(self) -> ScraperAgent:
        if self._scraper is None:
            self._scraper = ScraperAgent()
        return self._scraper

    @property
    def matcher(self) -> MatcherAgent:
        if self._matcher is None:
            self._matcher = MatcherAgent()
        return self._matcher

    @property
    def applicator(self) -> ApplicatorAgent:
        if self._applicator is None:
            self._applicator = ApplicatorAgent()
        return self._applicator

    # ---- pipeline steps ----

    def parse_resume(self, resume_path: str, use_cache: bool = True) -> Resume:
        cache = DATA_DIR / "profile.json"
        if use_cache and cache.exists():
            try:
                self._resume = Resume(**json.loads(cache.read_text(encoding="utf-8")))
                return self._resume
            except Exception:
                # Fall through to re-parse
                pass

        self._resume = self.parser.parse(resume_path)

        # Cache it
        cache.parent.mkdir(parents=True, exist_ok=True)
        cache.write_text(
            self._resume.model_dump_json(indent=2, exclude={"raw_text"}),
            encoding="utf-8",
        )
        return self._resume

    def scrape_all(self, use_cache: bool = False) -> list[Job]:
        cache = DATA_DIR / "jobs_cache.json"
        if use_cache and cache.exists():
            jobs_data = json.loads(cache.read_text(encoding="utf-8"))
            self._jobs = [Job(**j) for j in jobs_data]
            return self._jobs

        summary = self.scraper.run_all()
        if "error" in summary:
            raise RuntimeError(summary["error"])

        # Reload from the cache the tool just wrote
        jobs_data = json.loads(Path(summary["cache_path"]).read_text(encoding="utf-8"))
        self._jobs = [Job(**j) for j in jobs_data]
        return self._jobs

    def match_all(
        self, on_progress=None, threshold: Optional[float] = None
    ) -> list[MatchResult]:
        if self._resume is None:
            raise RuntimeError("parse_resume() must be called first")
        if not self._jobs:
            raise RuntimeError("scrape_all() must be called first")

        raw_matches = self.matcher.score_all(
            self._resume, self._jobs, on_progress=on_progress
        )
        self._matches = self.matcher.filter_and_save(raw_matches, threshold=threshold)
        return self._matches

    def apply_to(
        self, match: MatchResult, cover_letter: Optional[str] = None
    ) -> dict:
        if self._resume is None:
            # Try loading from cache
            cache = DATA_DIR / "profile.json"
            if cache.exists():
                self._resume = Resume(
                    **json.loads(cache.read_text(encoding="utf-8"))
                )
            else:
                raise RuntimeError("No parsed resume available")
        return self.applicator.apply(self._resume, match, cover_letter=cover_letter)

    # ---- cached accessors (for UI) ----

    @staticmethod
    def load_cached_matches() -> list[MatchResult]:
        p = DATA_DIR / "matches.json"
        if not p.exists():
            return []
        data = json.loads(p.read_text(encoding="utf-8"))
        return [MatchResult(**m) for m in data]

    @staticmethod
    def load_cached_profile() -> Optional[Resume]:
        p = DATA_DIR / "profile.json"
        if not p.exists():
            return None
        try:
            return Resume(**json.loads(p.read_text(encoding="utf-8")))
        except Exception:
            return None

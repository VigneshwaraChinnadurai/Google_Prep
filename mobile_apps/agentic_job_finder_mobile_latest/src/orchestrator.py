"""Stateful pipeline coordinator. Single source of truth for resume/jobs/matches/competitions."""
from __future__ import annotations
import asyncio
import json
import logging
from pathlib import Path
from typing import AsyncIterator, Literal

from src.agents.resume_parser import ResumeParserAgent
from src.agents.scraper import ScraperAgent
from src.agents.matcher import MatcherAgent
from src.agents.competition import CompetitionAgent
from src.config import load_settings, load_companies
from src.schemas import Resume, Job, MatchResult, Competition

logger = logging.getLogger(__name__)
DATA_DIR = Path("data")


class Orchestrator:
    """Stateful pipeline. Caches resume/jobs/matches/competitions to disk."""

    def __init__(self) -> None:
        self.cfg = load_settings()
        self.companies = load_companies()
        self.parser = ResumeParserAgent()
        self.scraper = ScraperAgent()
        self.matcher = MatcherAgent()
        self.competitions_agent = CompetitionAgent()

        self._resume: Resume | None = None
        self._jobs: list[Job] = []
        self._matches: dict[str, MatchResult] = {}
        self._competitions: dict[str, Competition] = {}
        self._lock = asyncio.Lock()
        self._load_caches()

    # --- public sync queries ---
    def has_resume(self) -> bool:
        return self._resume is not None

    def get_resume(self) -> Resume | None:
        return self._resume

    @property
    def target_count(self) -> int:
        return len(self.companies)

    def list_matches(self, *, min_score: float = 0.0, limit: int = 50) -> list[MatchResult]:
        items = [m for m in self._matches.values() if m.fit_score >= min_score]
        items.sort(key=lambda m: m.fit_score, reverse=True)
        return items[:limit]

    def get_match(self, match_id: str) -> MatchResult | None:
        return self._matches.get(match_id)

    def mark_match(self, match_id: str, *, status: Literal["approved", "skipped", "new"]) -> None:
        m = self._matches.get(match_id)
        if not m:
            return
        m.status = status
        self._save_matches()

    def set_resume(self, resume: Resume) -> None:
        self._resume = resume
        (DATA_DIR / "profile.json").write_text(resume.model_dump_json(indent=2))

    def list_competitions(
        self, *,
        tracked_only: bool = False,
        hiring_only: bool = False,
        platforms: list[str] | None = None,
        limit: int = 100,
    ) -> list[Competition]:
        items = list(self._competitions.values())
        if tracked_only:
            items = [c for c in items if c.is_tracked_company]
        if hiring_only:
            items = [c for c in items if c.is_hiring]
        if platforms:
            items = [c for c in items if c.platform in platforms]
        # Tracked companies first, then by start date (soonest first), nulls last
        items.sort(key=lambda c: (
            not c.is_tracked_company,
            c.starts_at.isoformat() if c.starts_at else "9999",
        ))
        return items[:limit]

    # --- async generators consumed by /pipeline routes ---
    async def scrape_iter(self) -> AsyncIterator[tuple[str, int]]:
        async with self._lock:
            collected: list[Job] = []
            cumulative = 0
            sem = asyncio.Semaphore(self.cfg.scraping.max_concurrent)

            async def _run(company):
                async with sem:
                    try:
                        return company.name, await self.scraper.scrape_company(company)
                    except Exception:
                        logger.exception("scrape failed for %s", company.name)
                        return company.name, []

            tasks = [asyncio.create_task(_run(c)) for c in self.companies]
            for coro in asyncio.as_completed(tasks):
                name, jobs = await coro
                collected.extend(jobs)
                cumulative += len(jobs)
                yield name, cumulative

            self._jobs = self._dedupe(collected)
            self._save_jobs()

    async def match_iter(self) -> AsyncIterator[tuple[int, int]]:
        async with self._lock:
            if not self._resume:
                raise RuntimeError("No resume loaded")
            jobs = list(self._jobs)
            total = len(jobs)
            if total == 0:
                yield 0, 0
                return

            results: dict[str, MatchResult] = {}
            sem = asyncio.Semaphore(self.cfg.matching.max_concurrent)

            async def _score(job: Job) -> MatchResult | None:
                async with sem:
                    try:
                        return await self.matcher.score(self._resume, job)
                    except Exception:
                        logger.exception("match failed for %s", job.title)
                        return None

            tasks = [asyncio.create_task(_score(j)) for j in jobs]
            done = 0
            for coro in asyncio.as_completed(tasks):
                r = await coro
                done += 1
                if r is not None:
                    prev = self._matches.get(r.id)
                    if prev and prev.status != "new":
                        r.status = prev.status
                    results[r.id] = r
                yield done, total

            self._matches = results
            self._save_matches()

    async def competitions_iter(self) -> AsyncIterator[tuple[str, int]]:
        """Yields (platform_name, count) per platform. Caches results when done."""
        async with self._lock:
            results = await self.competitions_agent.fetch_all()
            merged: dict[str, Competition] = {}
            for platform, comps in results.items():
                for c in comps:
                    merged[c.id] = c
                yield platform, len(comps)
            self._competitions = merged
            self._save_competitions()

    # --- cache I/O ---
    def _load_caches(self) -> None:
        DATA_DIR.mkdir(exist_ok=True)
        if (p := DATA_DIR / "profile.json").exists():
            self._resume = Resume.model_validate_json(p.read_text())
        if (p := DATA_DIR / "jobs_cache.json").exists():
            self._jobs = [Job.model_validate(j) for j in json.loads(p.read_text())]
        if (p := DATA_DIR / "matches.json").exists():
            data = json.loads(p.read_text())
            self._matches = {k: MatchResult.model_validate(v) for k, v in data.items()}
        if (p := DATA_DIR / "competitions_cache.json").exists():
            data = json.loads(p.read_text())
            self._competitions = {k: Competition.model_validate(v) for k, v in data.items()}

    def _save_jobs(self) -> None:
        (DATA_DIR / "jobs_cache.json").write_text(
            json.dumps([j.model_dump(mode="json") for j in self._jobs], indent=2)
        )

    def _save_matches(self) -> None:
        (DATA_DIR / "matches.json").write_text(
            json.dumps({k: v.model_dump(mode="json") for k, v in self._matches.items()}, indent=2)
        )

    def _save_competitions(self) -> None:
        (DATA_DIR / "competitions_cache.json").write_text(
            json.dumps({k: v.model_dump(mode="json") for k, v in self._competitions.items()}, indent=2)
        )

    @staticmethod
    def _dedupe(jobs: list[Job]) -> list[Job]:
        seen, out = set(), []
        for j in jobs:
            key = (j.company.lower(), j.title.lower(), str(j.apply_url))
            if key not in seen:
                seen.add(key)
                out.append(j)
        return out

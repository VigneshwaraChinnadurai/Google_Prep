"""
Scraper base class + a simple registry so the orchestrator can pick the right
adapter for each company.

Each scraper implements the hybrid strategy:
1. Try the ATS's public JSON API (fast, structured, no JS).
2. Fall back to Playwright if the API returns nothing (or the company uses a
   bespoke portal).
"""
from __future__ import annotations

import hashlib
from abc import ABC, abstractmethod
from typing import ClassVar, Optional

from ..models import ATSType, Company, Job


def job_id_from_url(url: str) -> str:
    """Stable short hash of a job URL, used as the primary key for dedup."""
    return hashlib.md5(url.encode("utf-8")).hexdigest()[:12]


def matches_location_filter(location: Optional[str], filters: list[str]) -> bool:
    """Case-insensitive substring match. Empty filters = allow everything."""
    if not filters:
        return True
    if not location:
        return False
    loc_lower = location.lower()
    return any(f.lower() in loc_lower for f in filters)


class BaseScraper(ABC):
    ats_type: ClassVar[ATSType]

    def __init__(self, company: Company, settings: dict):
        self.company = company
        self.settings = settings
        self.headless: bool = settings.get("scraping", {}).get("headless", True)
        self.timeout_ms: int = settings.get("scraping", {}).get(
            "page_timeout_ms", 30000
        )
        self.user_agent: str = settings.get("scraping", {}).get(
            "user_agent", "Mozilla/5.0 Job-Finder/1.0"
        )

    @abstractmethod
    async def fetch_jobs(self) -> list[Job]:
        """Return the list of jobs for this company, location-filtered."""

    # --- shared helpers ------------------------------------------------

    def _make_job(
        self,
        title: str,
        url: str,
        location: str | None,
        description: str | None = None,
        department: str | None = None,
    ) -> Job:
        return Job(
            id=job_id_from_url(url),
            company=self.company.name,
            title=title,
            location=location,
            url=url,
            description=description,
            department=department,
        )

    def _filter_by_location(self, jobs: list[Job]) -> list[Job]:
        return [
            j
            for j in jobs
            if matches_location_filter(j.location, self.company.location_filters)
        ]


# -------------------- registry --------------------

_REGISTRY: dict[ATSType, type[BaseScraper]] = {}


def register(cls: type[BaseScraper]) -> type[BaseScraper]:
    """Class decorator to register a scraper for its ATS type."""
    _REGISTRY[cls.ats_type] = cls
    return cls


def scraper_for(company: Company, settings: dict) -> BaseScraper:
    """Resolve the right scraper for a company, falling back to Generic."""
    cls = _REGISTRY.get(company.ats)
    if cls is None:
        from .generic import GenericScraper

        cls = GenericScraper
    return cls(company, settings)

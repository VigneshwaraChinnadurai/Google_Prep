"""Scraper registry — adapters self-register via @register_scraper."""
from __future__ import annotations
from typing import Protocol
from Google_Prep.mobile_apps.Archive.agentic_job_finder_mobile_latest.src.config import Company
from Google_Prep.mobile_apps.Archive.agentic_job_finder_mobile_latest.src.schemas import Job

_REGISTRY: dict[str, "ScraperAdapter"] = {}


class ScraperAdapter(Protocol):
    async def fetch(self, company: Company) -> list[Job]: ...


def register_scraper(key: str):
    def deco(cls):
        _REGISTRY[key] = cls()
        return cls
    return deco


def get_scraper(key: str) -> ScraperAdapter:
    if key not in _REGISTRY:
        raise KeyError(f"No scraper registered for ATS '{key}'")
    return _REGISTRY[key]


# Trigger registration
from .....agentic_job_finder_mobile_latest.src.scrapers import greenhouse, lever, workday, generic  # noqa: F401,E402

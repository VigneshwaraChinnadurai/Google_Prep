"""Generic Playwright-based scraper for in-house career portals.

Stub: each portal has a different DOM. Specialize per company by URL pattern
or write small per-portal extractors. Honor robots.txt.
"""
from __future__ import annotations
from src.config import Company
from src.schemas import Job
from . import register_scraper


@register_scraper("generic")
class GenericAdapter:
    async def fetch(self, company: Company) -> list[Job]:
        # TODO: implement Playwright traversal per portal. Filter by
        # company.location_filter once raw jobs are extracted.
        return []

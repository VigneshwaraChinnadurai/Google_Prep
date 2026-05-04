"""Scraper agent — dispatches to ATS adapters by company.ats."""
from __future__ import annotations
from src.config import Company
from src.schemas import Job
from src.scrapers import get_scraper


class ScraperAgent:
    async def scrape_company(self, company: Company) -> list[Job]:
        adapter = get_scraper(company.ats)
        return await adapter.fetch(company)

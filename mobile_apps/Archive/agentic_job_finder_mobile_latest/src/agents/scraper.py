"""Scraper agent — dispatches to ATS adapters by company.ats."""
from __future__ import annotations
from Google_Prep.mobile_apps.Archive.agentic_job_finder_mobile_latest.src.config import Company
from Google_Prep.mobile_apps.Archive.agentic_job_finder_mobile_latest.src.schemas import Job
from Google_Prep.mobile_apps.Archive.agentic_job_finder_mobile_latest.src.scrapers import get_scraper


class ScraperAgent:
    async def scrape_company(self, company: Company) -> list[Job]:
        adapter = get_scraper(company.ats)
        return await adapter.fetch(company)

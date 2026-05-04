"""Workday adapter — Workday exposes a JSON search endpoint per tenant.

NOTE: each Workday tenant has its own URL pattern. This is a lightweight
scaffold that POSTs to the common .../jobs endpoint. You'll likely need to
specialize per tenant.
"""
from __future__ import annotations
import httpx
from src.config import Company, load_settings
from src.schemas import Job
from . import register_scraper


@register_scraper("workday")
class WorkdayAdapter:
    async def fetch(self, company: Company) -> list[Job]:
        if not company.url:
            return []
        cfg = load_settings()
        # Convention: company.url ends with the tenant path; jobs API is /jobs
        # Many tenants accept POST { searchText, locations, limit }.
        api_url = company.url.rstrip("/") + "/jobs"
        payload = {"limit": 50, "offset": 0, "searchText": "",
                   "appliedFacets": {}}
        async with httpx.AsyncClient(timeout=cfg.scraping.request_timeout_seconds) as client:
            try:
                r = await client.post(api_url, json=payload,
                                      headers={"User-Agent": cfg.scraping.user_agent,
                                               "Accept": "application/json"})
                if r.status_code != 200:
                    return []
                data = r.json()
            except Exception:
                return []

        jobs: list[Job] = []
        for j in data.get("jobPostings", []):
            jobs.append(Job(
                id=f"wd-{company.name.lower()}-{j.get('bulletFields', [''])[0] or j.get('externalPath','')}",
                title=j.get("title", ""),
                company=company.name,
                location=j.get("locationsText"),
                apply_url=company.url.rstrip("/") + j.get("externalPath", ""),
                description="",  # Workday detail fetch is a separate call
                posted_at=j.get("postedOn"),
            ))
        if company.location_filter:
            lf = company.location_filter.lower()
            jobs = [j for j in jobs if lf in (j.location or "").lower()]
        return jobs

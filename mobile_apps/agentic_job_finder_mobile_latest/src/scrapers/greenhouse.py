"""Greenhouse boards-api adapter — JSON, no scraping."""
from __future__ import annotations
import httpx
from src.config import Company, load_settings
from src.schemas import Job
from . import register_scraper

BASE = "https://boards-api.greenhouse.io/v1/boards/{slug}/jobs?content=true"


@register_scraper("greenhouse")
class GreenhouseAdapter:
    async def fetch(self, company: Company) -> list[Job]:
        if not company.slug:
            return []
        cfg = load_settings()
        async with httpx.AsyncClient(timeout=cfg.scraping.request_timeout_seconds) as client:
            r = await client.get(BASE.format(slug=company.slug),
                                 headers={"User-Agent": cfg.scraping.user_agent})
            if r.status_code != 200:
                return []
            data = r.json()

        jobs: list[Job] = []
        for j in data.get("jobs", []):
            jobs.append(Job(
                id=f"gh-{company.slug}-{j['id']}",
                title=j["title"],
                company=company.name,
                location=(j.get("location") or {}).get("name"),
                apply_url=j["absolute_url"],
                description=j.get("content", "")[:5000],
                posted_at=j.get("updated_at"),
            ))
        if company.location_filter:
            lf = company.location_filter.lower()
            jobs = [j for j in jobs if lf in (j.location or "").lower()]
        return jobs

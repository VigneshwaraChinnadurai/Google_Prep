"""Lever public postings API — JSON."""
from __future__ import annotations
import httpx
from src.config import Company, load_settings
from src.schemas import Job
from . import register_scraper

BASE = "https://api.lever.co/v0/postings/{slug}?mode=json"


@register_scraper("lever")
class LeverAdapter:
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
        for j in data:
            cats = j.get("categories", {})
            jobs.append(Job(
                id=f"lever-{company.slug}-{j['id']}",
                title=j.get("text", ""),
                company=company.name,
                location=cats.get("location"),
                apply_url=j.get("hostedUrl") or j.get("applyUrl"),
                description=(j.get("descriptionPlain") or j.get("description") or "")[:5000],
                employment_type=cats.get("commitment"),
            ))
        if company.location_filter:
            lf = company.location_filter.lower()
            jobs = [j for j in jobs if lf in (j.location or "").lower()]
        return jobs

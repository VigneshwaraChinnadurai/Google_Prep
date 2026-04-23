"""
Lever scraper — uses the public JSON API at:
  https://api.lever.co/v0/postings/<company>?mode=json
"""
from __future__ import annotations

import re
from typing import Optional

import httpx

from ..models import ATSType, Job
from .base import BaseScraper, register


@register
class LeverScraper(BaseScraper):
    ats_type = ATSType.LEVER

    LEVER_API = "https://api.lever.co/v0/postings/{slug}"

    def _extract_slug(self) -> Optional[str]:
        m = re.search(r"lever\.co/([^/?#]+)", self.company.url)
        if m:
            return m.group(1)
        if "/" not in self.company.url and "." not in self.company.url:
            return self.company.url
        return None

    async def fetch_jobs(self) -> list[Job]:
        slug = self._extract_slug()
        if not slug:
            return []

        async with httpx.AsyncClient(
            timeout=self.timeout_ms / 1000,
            headers={"User-Agent": self.user_agent},
        ) as client:
            try:
                resp = await client.get(
                    self.LEVER_API.format(slug=slug), params={"mode": "json"}
                )
                resp.raise_for_status()
            except httpx.HTTPError:
                return []
            postings = resp.json()

        jobs: list[Job] = []
        for raw in postings:
            categories = raw.get("categories", {}) or {}
            jobs.append(
                self._make_job(
                    title=raw.get("text", ""),
                    url=raw.get("hostedUrl", ""),
                    location=categories.get("location"),
                    description=(raw.get("descriptionPlain") or raw.get("description") or ""),
                    department=categories.get("team") or categories.get("department"),
                )
            )

        return self._filter_by_location(jobs)

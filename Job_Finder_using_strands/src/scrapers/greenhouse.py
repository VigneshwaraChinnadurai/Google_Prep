"""
Greenhouse scraper — uses the public Boards API.

Greenhouse exposes a clean JSON API at:
  https://boards-api.greenhouse.io/v1/boards/<slug>/jobs?content=true

This is the fastest, most reliable path — no browser needed.
"""
from __future__ import annotations

import re
from typing import Optional

import httpx

from ..models import ATSType, Job
from .base import BaseScraper, register


@register
class GreenhouseScraper(BaseScraper):
    ats_type = ATSType.GREENHOUSE

    GREENHOUSE_API = "https://boards-api.greenhouse.io/v1/boards/{slug}/jobs"

    def _extract_slug(self) -> Optional[str]:
        """
        Accept either a full boards.greenhouse.io URL or just a slug.

        Examples:
          https://boards.greenhouse.io/airbnb            -> airbnb
          https://boards.greenhouse.io/airbnb/jobs       -> airbnb
          airbnb                                          -> airbnb
        """
        url = self.company.url
        m = re.search(r"greenhouse\.io/([^/?#]+)", url)
        if m:
            return m.group(1)
        # If it's already a bare slug
        if "/" not in url and "." not in url:
            return url
        return None

    async def fetch_jobs(self) -> list[Job]:
        slug = self._extract_slug()
        if not slug:
            return []

        api_url = self.GREENHOUSE_API.format(slug=slug)
        async with httpx.AsyncClient(
            timeout=self.timeout_ms / 1000,
            headers={"User-Agent": self.user_agent},
        ) as client:
            try:
                resp = await client.get(api_url, params={"content": "true"})
                resp.raise_for_status()
            except httpx.HTTPError:
                return []

            payload = resp.json()

        jobs: list[Job] = []
        for raw in payload.get("jobs", []):
            location = (raw.get("location") or {}).get("name")
            jobs.append(
                self._make_job(
                    title=raw.get("title", ""),
                    url=raw.get("absolute_url", ""),
                    location=location,
                    description=self._strip_html(raw.get("content", "")),
                    department=self._first_department(raw),
                )
            )

        return self._filter_by_location(jobs)

    @staticmethod
    def _strip_html(s: str) -> str:
        """Quick-and-dirty HTML strip. Good enough for JD text we hand to the LLM."""
        if not s:
            return ""
        # Decode common entities and strip tags.
        import html

        s = html.unescape(s)
        s = re.sub(r"<br\s*/?>", "\n", s, flags=re.IGNORECASE)
        s = re.sub(r"</(p|div|li)>", "\n", s, flags=re.IGNORECASE)
        s = re.sub(r"<[^>]+>", "", s)
        s = re.sub(r"\n{3,}", "\n\n", s)
        return s.strip()

    @staticmethod
    def _first_department(raw: dict) -> Optional[str]:
        depts = raw.get("departments") or []
        if depts and isinstance(depts, list):
            return depts[0].get("name")
        return None

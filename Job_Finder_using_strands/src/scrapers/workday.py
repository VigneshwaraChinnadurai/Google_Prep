"""
Workday scraper.

Workday sites have a semi-standard JSON endpoint at:
  <portal>/wday/cxs/<tenant>/<site>/jobs

e.g.  https://paloaltonetworks.wd1.myworkdayjobs.com/en-US/Careers
   -> https://paloaltonetworks.wd1.myworkdayjobs.com/wday/cxs/paloaltonetworks/Careers/jobs

We POST a simple search payload to that endpoint. If that fails (some tenants
require additional auth/cookies), we fall back to the Generic browser scraper.
"""
from __future__ import annotations

import re
from typing import Optional
from urllib.parse import urlparse

import httpx

from ..models import ATSType, Job
from .base import BaseScraper, register


@register
class WorkdayScraper(BaseScraper):
    ats_type = ATSType.WORKDAY

    async def fetch_jobs(self) -> list[Job]:
        api_url = self._build_api_url()
        if not api_url:
            return await self._fallback_browser()

        headers = {
            "User-Agent": self.user_agent,
            "Content-Type": "application/json",
            "Accept": "application/json",
        }
        payload = {
            "appliedFacets": {},
            "limit": 20,
            "offset": 0,
            "searchText": " ".join(self.company.location_filters[:1]) or "",
        }

        async with httpx.AsyncClient(
            timeout=self.timeout_ms / 1000, headers=headers
        ) as client:
            try:
                resp = await client.post(api_url, json=payload)
                resp.raise_for_status()
                data = resp.json()
            except (httpx.HTTPError, ValueError):
                return await self._fallback_browser()

        base = self._origin()
        jobs: list[Job] = []
        for raw in data.get("jobPostings", []):
            ext = raw.get("externalPath", "")
            url = base + ext if ext.startswith("/") else ext
            location = raw.get("locationsText")
            jobs.append(
                self._make_job(
                    title=raw.get("title", ""),
                    url=url,
                    location=location,
                    description=None,  # needs a second fetch; skip in quick pass
                )
            )

        return self._filter_by_location(jobs)

    # --------------------------------------------------------------

    def _build_api_url(self) -> Optional[str]:
        """
        Turn something like
            https://paloaltonetworks.wd1.myworkdayjobs.com/Careers
        into
            https://paloaltonetworks.wd1.myworkdayjobs.com/wday/cxs/paloaltonetworks/Careers/jobs
        """
        parsed = urlparse(self.company.url)
        host = parsed.netloc
        path = parsed.path.rstrip("/")

        m = re.match(r"^([^.]+)\.wd\d+\.myworkdayjobs\.com$", host)
        if not m:
            return None
        tenant = m.group(1)

        # path might be /Careers or /en-US/Careers
        site = path.split("/")[-1] if path else "Careers"
        return f"{parsed.scheme}://{host}/wday/cxs/{tenant}/{site}/jobs"

    def _origin(self) -> str:
        p = urlparse(self.company.url)
        return f"{p.scheme}://{p.netloc}"

    async def _fallback_browser(self) -> list[Job]:
        from .generic import GenericScraper

        fallback = GenericScraper(self.company, self.settings)
        return await fallback.fetch_jobs()

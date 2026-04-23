"""
Generic career-page scraper.

Strategy:
1. Playwright loads the page (JS-rendered content is the norm today).
2. We collect every `<a>` tag whose text looks job-like and whose href looks
   like a job-detail URL.
3. We keep the top N that pass a loose "looks like a job posting" filter.

This is deliberately lightweight. It won't beat a bespoke scraper, but it
handles the long tail of company-specific portals well enough to get a list
of URLs + titles + locations for the Matcher to reason about.
"""
from __future__ import annotations

import re
from typing import Optional

from ..models import ATSType, Job
from .base import BaseScraper, register

# Patterns that typically indicate a link points to a single job posting.
JOB_URL_PATTERNS = [
    r"/job/",
    r"/jobs/",
    r"/careers/",
    r"/opening/",
    r"/openings/",
    r"/position/",
    r"/apply/",
    r"/vacancy/",
    r"jobdetail",
    r"requisition",
    r"posting/",
]

# Anti-patterns — links we want to exclude.
EXCLUDE_PATTERNS = [
    r"#",
    r"mailto:",
    r"tel:",
    r"\.pdf$",
    r"/search",
    r"/login",
    r"/signup",
    r"javascript:",
]


@register
class GenericScraper(BaseScraper):
    ats_type = ATSType.GENERIC

    async def fetch_jobs(self) -> list[Job]:
        try:
            from playwright.async_api import async_playwright
        except ImportError:
            # Playwright not installed — nothing we can do.
            return []

        jobs: list[Job] = []
        max_jobs = self.settings.get("matching", {}).get(
            "max_jobs_per_company", 20
        )

        async with async_playwright() as p:
            browser = await p.chromium.launch(headless=self.headless)
            try:
                context = await browser.new_context(user_agent=self.user_agent)
                page = await context.new_page()
                page.set_default_timeout(self.timeout_ms)

                try:
                    await page.goto(self.company.url, wait_until="domcontentloaded")
                    # Give SPAs a moment to hydrate
                    await page.wait_for_timeout(2500)
                except Exception:
                    return []

                # Try to auto-scroll a bit to trigger lazy-loaded lists
                for _ in range(3):
                    await page.mouse.wheel(0, 4000)
                    await page.wait_for_timeout(700)

                # Collect candidate anchors
                anchors = await page.eval_on_selector_all(
                    "a",
                    """els => els.map(a => ({
                        href: a.href,
                        text: (a.innerText || a.textContent || '').trim(),
                        nearby: (a.closest('[class*="job"], [class*="position"], li, tr, article, .card')
                                  ?.innerText || '').slice(0, 400)
                    }))""",
                )

                seen: set[str] = set()
                for a in anchors:
                    href = a.get("href") or ""
                    text = (a.get("text") or "").strip()
                    nearby = a.get("nearby") or ""

                    if not href or not text or len(text) < 4 or len(text) > 160:
                        continue
                    if any(re.search(p, href, re.IGNORECASE) for p in EXCLUDE_PATTERNS):
                        continue
                    if not any(re.search(p, href, re.IGNORECASE) for p in JOB_URL_PATTERNS):
                        continue
                    if href in seen:
                        continue
                    seen.add(href)

                    location = self._guess_location(nearby)

                    jobs.append(
                        self._make_job(
                            title=text,
                            url=href,
                            location=location,
                            description=nearby[:400] if nearby else None,
                        )
                    )
                    if len(jobs) >= max_jobs * 3:  # over-collect; we filter next
                        break

            finally:
                await browser.close()

        filtered = self._filter_by_location(jobs)
        return filtered[:max_jobs]

    @staticmethod
    def _guess_location(text: str) -> Optional[str]:
        """Look for common location tokens in nearby text."""
        if not text:
            return None
        # Bangalore is the priority given the user's stated preference
        for loc in [
            "Bangalore",
            "Bengaluru",
            "Hyderabad",
            "Mumbai",
            "Pune",
            "Chennai",
            "Delhi",
            "Gurgaon",
            "Gurugram",
            "Noida",
            "India",
            "Remote",
        ]:
            if re.search(rf"\b{loc}\b", text, re.IGNORECASE):
                return loc
        return None

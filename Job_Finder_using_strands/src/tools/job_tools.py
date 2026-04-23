"""
Tools for the Scraper agent.

The agent itself is mostly a thin orchestrator — it picks which companies to
scrape, calls these tools, and handles failures. Heavy lifting is in
`src/scrapers/`.
"""
from __future__ import annotations

import asyncio
import json
from pathlib import Path

import httpx
from strands import tool

from ..config import DATA_DIR, load_companies, load_settings
from ..models import Company, Job
from ..scrapers import scraper_for


def _company_from_raw(raw: dict) -> Company:
    return Company(**raw)


@tool
def scrape_company(company_name: str) -> str:
    """
    Scrape all current job openings for a single company from the configured
    shortlist. Results are location-filtered per the company's config.

    Args:
        company_name: Name of the company (must match an entry in
                      config/companies.yaml).

    Returns:
        JSON array of jobs — each with id, title, location, url, description.
    """
    settings = load_settings()
    companies = load_companies()

    match = next(
        (c for c in companies if c["name"].lower() == company_name.lower()), None
    )
    if not match:
        return json.dumps(
            {"error": f"Company {company_name!r} not found in shortlist."}
        )

    company = _company_from_raw(match)
    scraper = scraper_for(company, settings)

    try:
        jobs: list[Job] = asyncio.run(scraper.fetch_jobs())
    except Exception as e:
        return json.dumps({"error": f"Scrape failed: {e!r}", "company": company_name})

    return json.dumps([j.model_dump(mode="json") for j in jobs], default=str)


@tool
def scrape_all_companies() -> str:
    """
    Scrape jobs for every company in config/companies.yaml in parallel (respecting
    the configured concurrency limit). Results are cached to data/jobs_cache.json.

    Returns:
        JSON object: {"total": N, "per_company": {name: count, ...}, "cache_path": "..."}
    """
    settings = load_settings()
    companies_raw = load_companies()
    concurrency = settings.get("scraping", {}).get("concurrency", 3)

    async def run_all() -> dict[str, list[Job]]:
        sem = asyncio.Semaphore(concurrency)
        results: dict[str, list[Job]] = {}

        async def one(raw: dict) -> None:
            company = _company_from_raw(raw)
            scraper = scraper_for(company, settings)
            async with sem:
                try:
                    jobs = await scraper.fetch_jobs()
                except Exception as e:
                    jobs = []
                    print(f"[scrape_all] {company.name} failed: {e!r}")
                results[company.name] = jobs

        await asyncio.gather(*(one(c) for c in companies_raw))
        return results

    results = asyncio.run(run_all())

    # Flatten + cache
    all_jobs: list[dict] = []
    per_company: dict[str, int] = {}
    for name, jobs in results.items():
        per_company[name] = len(jobs)
        all_jobs.extend(j.model_dump(mode="json") for j in jobs)

    cache_path = DATA_DIR / "jobs_cache.json"
    cache_path.parent.mkdir(parents=True, exist_ok=True)
    cache_path.write_text(
        json.dumps(all_jobs, indent=2, default=str), encoding="utf-8"
    )

    return json.dumps(
        {
            "total": len(all_jobs),
            "per_company": per_company,
            "cache_path": str(cache_path),
        }
    )


@tool
def fetch_job_detail(url: str) -> str:
    """
    Fetch the full job description HTML for a single job URL. Useful when the
    listing page only gave us a title + snippet and we need the full JD to
    score a match accurately.

    Args:
        url: The job posting URL.

    Returns:
        Plain-text job description (HTML stripped), capped at 8000 chars.
    """
    try:
        with httpx.Client(
            timeout=20.0,
            headers={"User-Agent": "Mozilla/5.0 Job-Finder/1.0"},
            follow_redirects=True,
        ) as client:
            resp = client.get(url)
            resp.raise_for_status()
    except httpx.HTTPError as e:
        return f"ERROR fetching {url}: {e!r}"

    from bs4 import BeautifulSoup

    soup = BeautifulSoup(resp.text, "lxml")
    # Strip scripts, styles, nav, footer
    for tag in soup(["script", "style", "nav", "footer", "header", "noscript"]):
        tag.decompose()

    text = soup.get_text(separator="\n", strip=True)
    # Collapse whitespace
    text = "\n".join(line for line in text.splitlines() if line.strip())
    return text[:8000]

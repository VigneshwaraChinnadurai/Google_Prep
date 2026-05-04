"""HackerEarth Hiring Challenges adapter.

Source: https://www.hackerearth.com/challenges/hiring/
HackerEarth doesn't expose a public API for challenges, so this is HTML
scraping. Cards in the listing follow a stable structure but selectors
should be revalidated periodically.
"""
from __future__ import annotations
import logging
from datetime import datetime
import httpx
from bs4 import BeautifulSoup
from src.config import load_settings
from src.schemas import Competition
from . import register_competition
from ._company_matcher import match_company

logger = logging.getLogger(__name__)
LISTING_URL = "https://www.hackerearth.com/challenges/hiring/"


@register_competition("hackerearth")
class HackerEarthAdapter:
    async def fetch(self) -> list[Competition]:
        cfg = load_settings()
        try:
            async with httpx.AsyncClient(
                timeout=cfg.scraping.request_timeout_seconds,
                headers={"User-Agent": cfg.scraping.user_agent},
                follow_redirects=True,
            ) as client:
                r = await client.get(LISTING_URL)
                if r.status_code != 200:
                    return []
                soup = BeautifulSoup(r.text, "html.parser")
        except Exception:
            logger.exception("HackerEarth fetch failed")
            return []

        out: list[Competition] = []
        # Each challenge is in a card. Selector subject to change — guarded.
        for card in soup.select("div.challenge-card-modern"):
            try:
                a = card.select_one("a.challenge-card-wrapper") or card.select_one("a")
                if not a or not a.get("href"):
                    continue
                href = a["href"]
                if href.startswith("/"):
                    href = "https://www.hackerearth.com" + href
                title = (card.select_one(".challenge-list-title") or card).get_text(strip=True)[:200]
                company_text = card.select_one(".challenge-list-company")
                sponsor = company_text.get_text(strip=True) if company_text else None
                canonical = match_company(sponsor)

                cid = href.rstrip("/").split("/")[-1] or title.lower().replace(" ", "-")[:50]
                out.append(Competition(
                    id=f"hackerearth-{cid}",
                    platform="hackerearth",
                    title=title,
                    sponsor_company=sponsor,
                    matched_company=canonical,
                    is_tracked_company=canonical is not None,
                    is_hiring=True,  # listing is /hiring/ — all are hiring
                    registration_url=href,
                    description_snippet=title,
                ))
            except Exception:
                continue
        return out

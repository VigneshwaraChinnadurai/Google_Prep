"""Kaggle competitions adapter.

Source: https://www.kaggle.com/competitions
Kaggle has a public-facing JSON endpoint they use for the listing page.
We flag competitions as 'hiring' only when 'recruitment' appears in the
category — Kaggle uses that explicitly for recruitment competitions.
"""
from __future__ import annotations
import logging
from datetime import datetime
import httpx
from src.config import load_settings
from src.schemas import Competition
from . import register_competition
from ._company_matcher import match_company

logger = logging.getLogger(__name__)
LISTING_URL = "https://www.kaggle.com/api/i/competitions.CompetitionService/ListCompetitions"


@register_competition("kaggle")
class KaggleAdapter:
    async def fetch(self) -> list[Competition]:
        cfg = load_settings()
        # Kaggle's UI hits a POST RPC. Body shape is stable enough.
        body = {"page": 1, "group": "active", "category": "all", "sortBy": "recentlyCreated"}
        try:
            async with httpx.AsyncClient(
                timeout=cfg.scraping.request_timeout_seconds,
                headers={
                    "User-Agent": cfg.scraping.user_agent,
                    "Content-Type": "application/json",
                    "Accept": "application/json",
                },
            ) as client:
                r = await client.post(LISTING_URL, json=body)
                if r.status_code != 200:
                    return []
                data = r.json()
        except Exception:
            logger.exception("Kaggle fetch failed")
            return []

        out: list[Competition] = []
        for c in data.get("competitions", []):
            try:
                category = (c.get("category") or "").lower()
                is_hiring = "recruitment" in category
                sponsor = c.get("organizationName")
                canonical = match_company(sponsor)
                slug = c.get("competitionUrl") or c.get("slug") or ""
                url = f"https://www.kaggle.com{slug}" if slug.startswith("/") else slug

                out.append(Competition(
                    id=f"kaggle-{c.get('id') or slug.rstrip('/').split('/')[-1]}",
                    platform="kaggle",
                    title=c.get("title", "").strip(),
                    sponsor_company=sponsor,
                    matched_company=canonical,
                    is_tracked_company=canonical is not None,
                    is_hiring=is_hiring,
                    starts_at=_parse_ts(c.get("enabledDate")),
                    ends_at=_parse_ts(c.get("deadline")),
                    registration_url=url or "https://www.kaggle.com/competitions",
                    prize_pool=c.get("rewardDisplay"),
                    tags=[category] if category else [],
                    description_snippet=c.get("description", "")[:280],
                ))
            except Exception:
                continue
        return out


def _parse_ts(s) -> datetime | None:
    if not s:
        return None
    try:
        return datetime.fromisoformat(str(s).replace("Z", "+00:00"))
    except Exception:
        return None

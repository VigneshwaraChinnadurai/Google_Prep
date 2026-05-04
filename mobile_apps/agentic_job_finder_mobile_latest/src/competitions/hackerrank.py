"""HackerRank Contests adapter.

Source: https://www.hackerrank.com/contests
HackerRank exposes a JSON endpoint at /rest/contests/upcoming.json that's
used by their own UI. Stable enough to depend on.
"""
from __future__ import annotations
import logging
from datetime import datetime, timezone
import httpx
from src.config import load_settings
from src.schemas import Competition
from . import register_competition
from ._company_matcher import match_company

logger = logging.getLogger(__name__)
API_URL = "https://www.hackerrank.com/rest/contests/upcoming?offset=0&limit=50&contest_slug=active"


@register_competition("hackerrank")
class HackerRankAdapter:
    async def fetch(self) -> list[Competition]:
        cfg = load_settings()
        try:
            async with httpx.AsyncClient(
                timeout=cfg.scraping.request_timeout_seconds,
                headers={"User-Agent": cfg.scraping.user_agent, "Accept": "application/json"},
            ) as client:
                r = await client.get(API_URL)
                if r.status_code != 200:
                    return []
                data = r.json()
        except Exception:
            logger.exception("HackerRank fetch failed")
            return []

        out: list[Competition] = []
        for c in data.get("models", []):
            try:
                slug = c.get("slug", "")
                # HackerRank flags sponsor in 'company_name' or similar; field
                # naming varies, so try several.
                sponsor = (c.get("company_name") or c.get("organization")
                           or c.get("custom_recruitment_text") or "").strip() or None
                canonical = match_company(sponsor)
                is_hiring = bool(c.get("hiring") or c.get("is_recruitment") or sponsor)

                out.append(Competition(
                    id=f"hackerrank-{slug}",
                    platform="hackerrank",
                    title=c.get("name", "").strip(),
                    sponsor_company=sponsor,
                    matched_company=canonical,
                    is_tracked_company=canonical is not None,
                    is_hiring=is_hiring,
                    starts_at=_parse_ts(c.get("starts_at") or c.get("epoch_starttime")),
                    ends_at=_parse_ts(c.get("ends_at") or c.get("epoch_endtime")),
                    registration_url=f"https://www.hackerrank.com/contests/{slug}",
                    description_snippet=c.get("description", "")[:280],
                ))
            except Exception:
                continue
        return out


def _parse_ts(value) -> datetime | None:
    if value is None:
        return None
    try:
        if isinstance(value, (int, float)):
            return datetime.fromtimestamp(value, tz=timezone.utc)
        return datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    except Exception:
        return None

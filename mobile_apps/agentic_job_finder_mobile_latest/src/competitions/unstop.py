"""Unstop (formerly Dare2Compete) adapter — heavy India hiring focus.

Source: https://unstop.com/api/public/opportunity/search-result
Unstop exposes a public search API that returns JSON. Supports filtering
by opportunity_type ('hackathons', 'jobs', 'competitions').
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
API_URL = "https://unstop.com/api/public/opportunity/search-result"


@register_competition("unstop")
class UnstopAdapter:
    async def fetch(self) -> list[Competition]:
        cfg = load_settings()
        results: list[Competition] = []

        # Fetch hackathons + competitions in parallel pages.
        for opp_type in ("hackathons", "competitions"):
            try:
                params = {
                    "opportunity": opp_type,
                    "per_page": 50,
                    "oppstatus": "open",
                }
                async with httpx.AsyncClient(
                    timeout=cfg.scraping.request_timeout_seconds,
                    headers={"User-Agent": cfg.scraping.user_agent, "Accept": "application/json"},
                ) as client:
                    r = await client.get(API_URL, params=params)
                    if r.status_code != 200:
                        continue
                    data = r.json()
            except Exception:
                logger.exception("Unstop fetch failed for %s", opp_type)
                continue

            items = data.get("data", {}).get("data", []) or []
            for c in items:
                try:
                    sponsor = (c.get("organisation", {}).get("name")
                               if isinstance(c.get("organisation"), dict)
                               else c.get("organisation_name"))
                    canonical = match_company(sponsor)
                    public_url = c.get("public_url") or c.get("seo_url") or ""
                    if public_url and not public_url.startswith("http"):
                        public_url = f"https://unstop.com/{public_url.lstrip('/')}"

                    results.append(Competition(
                        id=f"unstop-{c.get('id') or c.get('uuid') or public_url[-40:]}",
                        platform="unstop",
                        title=c.get("title", "").strip(),
                        sponsor_company=sponsor,
                        matched_company=canonical,
                        is_tracked_company=canonical is not None,
                        is_hiring="hiring" in (c.get("type", "") or "").lower()
                                  or bool(c.get("is_hiring")),
                        starts_at=_parse_ts(c.get("start_date") or c.get("registration_start_date")),
                        ends_at=_parse_ts(c.get("end_date") or c.get("registration_end_date")),
                        registration_url=public_url or "https://unstop.com/",
                        prize_pool=str(c.get("prize_money") or "") or None,
                        location=c.get("region") or "Online",
                        tags=[t.get("name", "") for t in (c.get("filters") or []) if isinstance(t, dict)],
                        description_snippet=c.get("subtitle", "")[:280],
                    ))
                except Exception:
                    continue
        return results


def _parse_ts(s) -> datetime | None:
    if not s:
        return None
    try:
        return datetime.fromisoformat(str(s).replace("Z", "+00:00"))
    except Exception:
        return None

"""
Dynamic web fetcher — driven by SearchPlan, NO hardcoded queries.

Data sources
------------
1. **Gemini + Google Search grounding** — primary source for real-time data.
   Queries come from SearchPlan.grounding_queries (LLM-generated).
2. **SEC EDGAR XBRL API** — structured revenue / income data from filings.
   CIK numbers come from SearchPlan.ticker_to_cik (LLM-generated + lookup).
3. **SEC EDGAR full-text search** — filing text matching topic queries.
   Search terms come from SearchPlan.sec_fulltext_queries.
4. **Google News RSS** — headlines and snippets.
   Search terms come from SearchPlan.news_queries.

Every public function returns ``list[Document]`` ready for the retrieval
pipeline. All search parameters are driven by the SearchPlan dataclass —
making this module work for ANY topic, not just cloud market analysis.

Ref: query_planner.py — SearchPlan generation
"""
from __future__ import annotations

import json
import logging
import time
import uuid
from typing import TYPE_CHECKING

import requests
from bs4 import BeautifulSoup

if TYPE_CHECKING:
    from llm_client import GeminiClient
    from retrieval import Document
    from query_planner import SearchPlan

logger = logging.getLogger(__name__)

# SEC EDGAR requires a descriptive User-Agent
_SEC_UA = "StrategicAnalysisBot/1.0 (contact: autonomous-analysis@example.com)"
_SEC_BASE = "https://data.sec.gov"

# Standard XBRL fact keys for financial data extraction
_FACT_KEYS = [
    "Revenues",
    "RevenueFromContractWithCustomerExcludingAssessedTax",
    "SalesRevenueNet",
    "OperatingIncome",
    "OperatingIncomeLoss",
    "NetIncomeLoss",
    "ResearchAndDevelopmentExpense",
    "CostOfRevenue",
    "GrossProfit",
]


def _doc(content: str, source_type: str, company: str = "", url: str = "") -> "Document":
    from retrieval import Document
    return Document(
        content=content,
        doc_id=uuid.uuid4().hex[:12],
        metadata={
            "source_type": source_type,
            "company": company,
            "url": url,
        },
    )


# ═══════════════════════════════════════════════════════════════════════════
# 1.  Gemini-grounded search (primary — real web data)
#     Queries are from SearchPlan.grounding_queries (dynamic)
# ═══════════════════════════════════════════════════════════════════════════
def fetch_grounded(llm: "GeminiClient", plan: "SearchPlan") -> list["Document"]:
    """Issue grounded-search queries through Gemini. Returns real web data.

    Args:
        llm: GeminiClient for grounded search calls.
        plan: SearchPlan with grounding_queries list.
    """
    docs: list["Document"] = []
    system = (
        "You are a research assistant. Provide precise, factual data with "
        "exact numbers where available. Cite your sources. If data is "
        "unavailable, say so explicitly rather than guessing."
    )
    for item in plan.grounding_queries:
        query = item.get("query", "")
        company = item.get("company", "cross_market")
        if not query:
            continue
        logger.info("Grounded search: %s …", query[:60])
        try:
            resp = llm.generate_grounded(query, system=system,
                                          temperature=0.1, max_tokens=2048)
            content = resp.text
            if resp.grounding_sources:
                content += "\n\nSources:\n" + "\n".join(
                    f"- {s.get('title', '')} — {s.get('uri', '')}"
                    for s in resp.grounding_sources
                )
            docs.append(_doc(
                content,
                source_type="grounded_search",
                company=company,
                url=resp.grounding_sources[0]["uri"] if resp.grounding_sources else "",
            ))
        except Exception as exc:
            logger.error("Grounded search failed for %s: %s", company, exc)
    return docs


# ═══════════════════════════════════════════════════════════════════════════
# 2.  SEC EDGAR XBRL (structured financial facts)
#     CIK numbers are from SearchPlan.ticker_to_cik (dynamic)
# ═══════════════════════════════════════════════════════════════════════════
def _edgar_get(url: str) -> dict | None:
    try:
        r = requests.get(url, headers={"User-Agent": _SEC_UA}, timeout=20)
        if r.status_code == 200:
            return r.json()
        logger.warning("EDGAR %d for %s", r.status_code, url)
    except Exception as e:
        logger.warning("EDGAR request failed: %s", e)
    return None


def fetch_sec_edgar(plan: "SearchPlan") -> list["Document"]:
    """Pull latest company facts from EDGAR XBRL for companies in the plan.

    Args:
        plan: SearchPlan with ticker_to_cik mapping.
    """
    docs: list["Document"] = []
    cik_map = plan.ticker_to_cik
    if not cik_map:
        logger.info("No CIK numbers available — skipping SEC EDGAR XBRL")
        return docs

    for ticker, cik in cik_map.items():
        if not cik or len(cik) < 5:
            logger.info("Skipping EDGAR for %s — no valid CIK", ticker)
            continue
        url = f"{_SEC_BASE}/api/xbrl/companyfacts/CIK{cik}.json"
        logger.info("EDGAR facts: %s (%s)", ticker, url)
        data = _edgar_get(url)
        if not data:
            continue

        company_name = data.get("entityName", ticker)
        facts_us = data.get("facts", {}).get("us-gaap", {})

        lines: list[str] = [f"SEC EDGAR XBRL Company Facts — {company_name} ({ticker})\n"]
        for key in _FACT_KEYS:
            fact = facts_us.get(key)
            if not fact:
                continue
            units = fact.get("units", {})
            for unit_key, entries in units.items():
                if unit_key != "USD":
                    continue
                recent = entries[-8:] if len(entries) > 8 else entries
                for e in recent:
                    val_b = e.get("val", 0) / 1_000_000_000
                    fp = e.get("fp", "?")
                    fy = e.get("fy", "?")
                    form = e.get("form", "?")
                    filed = e.get("filed", "?")
                    lines.append(
                        f"  {key} | {form} FY{fy} {fp} | ${val_b:.2f}B | filed {filed}"
                    )

        if len(lines) > 1:
            docs.append(_doc(
                "\n".join(lines),
                source_type="sec_filing",
                company=ticker,
                url=url,
            ))
        time.sleep(0.15)

    return docs


# ═══════════════════════════════════════════════════════════════════════════
# 3.  SEC EDGAR full-text search (recent filing text)
#     Queries are from SearchPlan.sec_fulltext_queries (dynamic)
# ═══════════════════════════════════════════════════════════════════════════
def fetch_sec_fulltext(query: str, max_results: int = 3) -> list["Document"]:
    """Search EDGAR full-text index for filings matching *query*."""
    docs: list["Document"] = []
    url = (
        f"https://efts.sec.gov/LATEST/search-index"
        f"?q={requests.utils.quote(query)}"
        f"&dateRange=custom&startdt=2025-01-01&enddt=2026-12-31"
        f"&forms=10-Q,10-K&from=0&size={max_results}"
    )
    try:
        r = requests.get(url, headers={"User-Agent": _SEC_UA}, timeout=20)
        if r.status_code != 200:
            logger.warning("EDGAR EFTS %d for query: %s", r.status_code, query[:60])
            return docs
        hits = r.json().get("hits", {}).get("hits", [])
        for hit in hits:
            src = hit.get("_source", {})
            text = (
                f"Filing: {src.get('display_names', [''])[0]}\n"
                f"Form: {src.get('form_type', '?')}  Filed: {src.get('file_date', '?')}\n"
                f"{src.get('entity_name', '')}\n\n"
            )
            docs.append(_doc(text, source_type="sec_filing",
                             company=src.get("ticker", ""),
                             url=f"https://www.sec.gov/Archives/edgar/data/{src.get('entity_id', '')}/"))
    except Exception as e:
        logger.warning("EDGAR EFTS failed for '%s': %s", query[:60], e)
    return docs


# ═══════════════════════════════════════════════════════════════════════════
# 4.  Financial news scraping (Google News RSS)
#     Queries are from SearchPlan.news_queries (dynamic)
# ═══════════════════════════════════════════════════════════════════════════
def fetch_news_rss(plan: "SearchPlan") -> list["Document"]:
    """Fetch headlines + snippets from Google News RSS.

    Args:
        plan: SearchPlan with news_queries list.
    """
    docs: list["Document"] = []
    for q in plan.news_queries:
        url = f"https://news.google.com/rss/search?q={q}&hl=en-US&gl=US&ceid=US:en"
        try:
            r = requests.get(url, timeout=15,
                             headers={"User-Agent": "Mozilla/5.0"})
            if r.status_code != 200:
                continue
            soup = BeautifulSoup(r.content, "lxml-xml")
            items = soup.find_all("item", limit=5)
            lines = [f"Google News results for: {q.replace('+', ' ')}\n"]
            for it in items:
                title = it.find("title")
                pub = it.find("pubDate")
                link = it.find("link")
                lines.append(
                    f"- {title.text if title else '?'} "
                    f"({pub.text if pub else '?'}) "
                    f"[{link.text if link else ''}]"
                )
            if len(lines) > 1:
                # Determine company tag from the query
                company = "cross_market"
                for c in plan.companies:
                    ticker = c.get("ticker", "").lower()
                    name = c.get("name", "").lower()
                    if ticker in q.lower() or any(w in q.lower() for w in name.split() if len(w) > 3):
                        company = c.get("ticker", "cross_market")
                        break
                docs.append(_doc("\n".join(lines), source_type="news",
                                 company=company, url=url))
        except Exception as e:
            logger.warning("News RSS failed for %s: %s", q[:40], e)
        time.sleep(0.3)
    return docs


# ═══════════════════════════════════════════════════════════════════════════
# Public orchestrator — fetches ALL sources using the SearchPlan
# ═══════════════════════════════════════════════════════════════════════════
def fetch_all(llm: "GeminiClient", plan: "SearchPlan") -> list["Document"]:
    """Run all fetchers using the dynamic SearchPlan and return combined docs.

    Args:
        llm: GeminiClient for grounded search.
        plan: SearchPlan with all dynamic queries.
    """
    all_docs: list["Document"] = []

    # Primary: Gemini-grounded web search (real-time data)
    all_docs.extend(fetch_grounded(llm, plan))

    # Secondary: SEC EDGAR structured data
    all_docs.extend(fetch_sec_edgar(plan))

    # Tertiary: SEC full-text search (each query from the plan)
    for q in plan.sec_fulltext_queries:
        all_docs.extend(fetch_sec_fulltext(q))

    # Quaternary: News RSS
    all_docs.extend(fetch_news_rss(plan))

    logger.info("Total documents fetched: %d (grounded=%d, plan_companies=%s)",
                len(all_docs),
                len(plan.grounding_queries),
                [c.get("ticker", "?") for c in plan.companies])
    return all_docs

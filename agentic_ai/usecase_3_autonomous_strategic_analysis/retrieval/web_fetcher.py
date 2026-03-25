"""
Real-data web fetcher — NO mock data.

Data sources
------------
1. **Gemini + Google Search grounding** — primary source for latest earnings,
   analyst commentary, competitive intelligence.  Gemini searches the live web
   and returns text *with citations*.
2. **SEC EDGAR XBRL API** — structured revenue / income data straight from
   10-Q / 10-K filings (free, no key, rate-limit 10 req/s).

Every public function returns ``list[Document]`` ready for the retrieval pipeline.
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

logger = logging.getLogger(__name__)

# SEC EDGAR requires a descriptive User-Agent
_SEC_UA = "StrategicAnalysisBot/1.0 (contact: autonomous-analysis@example.com)"
_SEC_BASE = "https://data.sec.gov"
_SEC_EFTS = "https://efts.sec.gov/LATEST/search-index"

# CIK numbers (zero-padded to 10 digits)
_CIKS: dict[str, str] = {
    "GOOGL": "0001652044",
    "MSFT": "0000789019",
    "AMZN": "0001018724",
}


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
# 1.  Gemini-grounded search  (primary — real web data)
# ═══════════════════════════════════════════════════════════════════════════
_GROUNDING_QUERIES: list[dict[str, str]] = [
    {
        "query": (
            "Alphabet Google Cloud latest quarterly earnings results: revenue in billions, "
            "year-over-year growth, operating income, operating margin percentage. "
            "Include exact numbers from the most recent earnings release."
        ),
        "company": "GOOGL",
    },
    {
        "query": (
            "Microsoft Azure Intelligent Cloud latest quarterly earnings results: "
            "revenue in billions, year-over-year growth, operating income, operating margin. "
            "Include exact numbers from the most recent earnings release."
        ),
        "company": "MSFT",
    },
    {
        "query": (
            "Amazon AWS latest quarterly earnings results: revenue in billions, "
            "year-over-year growth, operating income, operating margin. "
            "Include exact numbers from the most recent earnings release."
        ),
        "company": "AMZN",
    },
    {
        "query": (
            "Google Cloud vs Microsoft Azure competitive analysis 2025-2026: "
            "AI workloads, Vertex AI adoption, developer preference, startup migration "
            "trends, market share shifts."
        ),
        "company": "cross_market",
    },
    {
        "query": (
            "Analyst commentary on biggest strategic threats from Google Cloud to "
            "Microsoft Azure in enterprise AI and cloud market 2025-2026."
        ),
        "company": "cross_market",
    },
    {
        "query": (
            "Google Cloud Vertex AI BigQuery growth drivers: why are startups and "
            "data-native companies choosing Google Cloud over Azure?"
        ),
        "company": "GOOGL",
    },
]


def fetch_grounded(llm: "GeminiClient") -> list["Document"]:
    """Issue grounded-search queries through Gemini. Returns real web data."""
    docs: list["Document"] = []
    system = (
        "You are a financial research assistant. Provide precise, factual data with "
        "exact numbers where available. Cite your sources. If data is unavailable, "
        "say so explicitly rather than guessing."
    )
    for item in _GROUNDING_QUERIES:
        logger.info("Grounded search: %s …", item["query"][:60])
        try:
            resp = llm.generate_grounded(item["query"], system=system,
                                          temperature=0.1, max_tokens=2048)
            content = resp.text
            # append source URLs
            if resp.grounding_sources:
                content += "\n\nSources:\n" + "\n".join(
                    f"- {s.get('title', '')} — {s.get('uri', '')}"
                    for s in resp.grounding_sources
                )
            docs.append(_doc(
                content,
                source_type="grounded_search",
                company=item["company"],
                url=resp.grounding_sources[0]["uri"] if resp.grounding_sources else "",
            ))
        except Exception as exc:
            logger.error("Grounded search failed for %s: %s", item["company"], exc)
    return docs


# ═══════════════════════════════════════════════════════════════════════════
# 2.  SEC EDGAR  (structured financial facts)
# ═══════════════════════════════════════════════════════════════════════════
_FACT_KEYS = [
    "Revenues",
    "RevenueFromContractWithCustomerExcludingAssessedTax",
    "SalesRevenueNet",
    "OperatingIncome",
    "OperatingIncomeLoss",
    "NetIncomeLoss",
]


def _edgar_get(url: str) -> dict | None:
    try:
        r = requests.get(url, headers={"User-Agent": _SEC_UA}, timeout=20)
        if r.status_code == 200:
            return r.json()
        logger.warning("EDGAR %d for %s", r.status_code, url)
    except Exception as e:
        logger.warning("EDGAR request failed: %s", e)
    return None


def fetch_sec_edgar() -> list["Document"]:
    """Pull latest company facts from EDGAR XBRL for each cloud provider."""
    docs: list["Document"] = []
    for ticker, cik in _CIKS.items():
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
                # take the last 8 filings
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
        time.sleep(0.15)   # polite rate-limit

    return docs


# ═══════════════════════════════════════════════════════════════════════════
# 3.  SEC EDGAR Full-Text Search  (recent filing text)
# ═══════════════════════════════════════════════════════════════════════════
def fetch_sec_fulltext(query: str = "Google Cloud revenue", max_results: int = 3) -> list["Document"]:
    """Search EDGAR full-text index for filings matching *query*."""
    docs: list["Document"] = []
    url = f"https://efts.sec.gov/LATEST/search-index?q={requests.utils.quote(query)}&dateRange=custom&startdt=2025-01-01&enddt=2026-12-31&forms=10-Q,10-K&from=0&size={max_results}"
    try:
        r = requests.get(url, headers={"User-Agent": _SEC_UA}, timeout=20)
        if r.status_code != 200:
            logger.warning("EDGAR EFTS %d", r.status_code)
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
                             url=f"https://www.sec.gov/Archives/edgar/data/{src.get('entity_id','')}/"))
    except Exception as e:
        logger.warning("EDGAR EFTS failed: %s", e)
    return docs


# ═══════════════════════════════════════════════════════════════════════════
# 4.  Financial news scraping (Google News RSS)
# ═══════════════════════════════════════════════════════════════════════════
_NEWS_QUERIES = [
    ("Google+Cloud+earnings+revenue", "GOOGL"),
    ("Microsoft+Azure+cloud+earnings", "MSFT"),
    ("Amazon+AWS+cloud+earnings", "AMZN"),
    ("Google+Cloud+vs+Azure+AI+competitive", "cross_market"),
]


def fetch_news_rss() -> list["Document"]:
    """Fetch headlines + snippets from Google News RSS."""
    docs: list["Document"] = []
    for q, company in _NEWS_QUERIES:
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
                docs.append(_doc("\n".join(lines), source_type="news",
                                 company=company, url=url))
        except Exception as e:
            logger.warning("News RSS failed for %s: %s", q, e)
        time.sleep(0.3)
    return docs


# ═══════════════════════════════════════════════════════════════════════════
# Public orchestrator
# ═══════════════════════════════════════════════════════════════════════════
def fetch_all(llm: "GeminiClient") -> list["Document"]:
    """Run all fetchers and return combined document list."""
    all_docs: list["Document"] = []

    # Primary: Gemini-grounded web search (real-time data)
    all_docs.extend(fetch_grounded(llm))

    # Secondary: SEC EDGAR structured data
    all_docs.extend(fetch_sec_edgar())

    # Tertiary: SEC full-text search
    all_docs.extend(fetch_sec_fulltext("Google Cloud revenue operating income"))
    all_docs.extend(fetch_sec_fulltext("Azure cloud revenue"))

    # Quaternary: News RSS
    all_docs.extend(fetch_news_rss())

    logger.info("Total documents fetched: %d", len(all_docs))
    return all_docs

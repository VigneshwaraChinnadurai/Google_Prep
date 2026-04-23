"""
Scraper Agent.

A thin agent that knows how to:
  - Scrape all configured companies (the common path)
  - Scrape a single company on demand
  - Fetch a specific job detail if a JD snippet was insufficient

For bulk scraping we actually bypass the LLM for speed+cost — the tools just
run. The LLM only gets involved when there's ambiguity (e.g. a company whose
Generic scrape returned noisy results and we need the LLM to decide which
links are real jobs). That's a future enhancement.
"""
from __future__ import annotations

import json
from typing import Any

from strands import Agent

from ..config import build_model
from ..models import Job
from ..tools.job_tools import fetch_job_detail, scrape_all_companies, scrape_company

SYSTEM_PROMPT = """\
You are a job-scraping orchestrator. When asked to scrape, use the available
tools. When the user asks for a summary, give counts per company and note any
companies that returned zero jobs (they may need a different ATS type or URL).
"""


class ScraperAgent:
    def __init__(self):
        self.agent = Agent(
            model=build_model(),
            system_prompt=SYSTEM_PROMPT,
            tools=[scrape_all_companies, scrape_company, fetch_job_detail],
            name="Scraper",
        )

    # ---- Fast path: call the tool directly, skip the LLM ----

    def run_all(self) -> dict[str, Any]:
        """Scrape every configured company and return the summary dict."""
        result = self.agent.tool.scrape_all_companies()
        return _parse_json(_as_str(result))

    def run_one(self, company_name: str) -> list[Job]:
        """Scrape a single company; returns a list of Job models."""
        result = self.agent.tool.scrape_company(company_name=company_name)
        data = _parse_json(_as_str(result))
        if isinstance(data, dict) and "error" in data:
            raise RuntimeError(data["error"])
        return [Job(**j) for j in data]

    def fetch_detail(self, url: str) -> str:
        result = self.agent.tool.fetch_job_detail(url=url)
        return _as_str(result)


def _parse_json(s: str) -> Any:
    try:
        return json.loads(s)
    except json.JSONDecodeError:
        return {"error": "malformed JSON from tool", "raw": s[:500]}


def _as_str(tool_result) -> str:
    if isinstance(tool_result, str):
        return tool_result
    for attr in ("content", "output", "result"):
        v = getattr(tool_result, attr, None)
        if isinstance(v, str):
            return v
        if isinstance(v, list) and v:
            first = v[0]
            if isinstance(first, dict) and "text" in first:
                return first["text"]
            if hasattr(first, "text"):
                return first.text
    return str(tool_result)

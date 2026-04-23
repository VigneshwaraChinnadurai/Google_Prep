"""
Smoke tests — verify the project imports correctly and basic models work
without needing an LLM API key.

Run:  python -m pytest tests/test_smoke.py -v
"""
from __future__ import annotations

import json
from pathlib import Path

import pytest


def test_models_import():
    from src.models import (
        ApplicationStatus,
        Company,
        Education,
        Experience,
        Job,
        MatchResult,
        Resume,
        Skill,
    )
    assert Resume.__name__ == "Resume"
    assert ApplicationStatus.PENDING_REVIEW.value == "pending_review"


def test_resume_schema_roundtrip():
    from src.models import Resume, Skill

    r = Resume(
        full_name="Test User",
        field="Software Engineering",
        seniority="Senior",
        total_years_experience=7.5,
        skills=[Skill(name="Python", category="language", years=6)],
    )
    data = r.model_dump_json()
    r2 = Resume(**json.loads(data))
    assert r2.full_name == "Test User"
    assert r2.skills[0].name == "Python"


def test_settings_load():
    from src.config import load_companies, load_settings

    settings = load_settings()
    assert "llm" in settings
    assert "matching" in settings

    companies = load_companies()
    assert isinstance(companies, list)


def test_scraper_registry():
    from src.models import ATSType, Company
    from src.scrapers import scraper_for
    from src.scrapers.greenhouse import GreenhouseScraper
    from src.scrapers.lever import LeverScraper
    from src.scrapers.workday import WorkdayScraper

    gh = scraper_for(
        Company(name="X", ats=ATSType.GREENHOUSE, url="https://boards.greenhouse.io/x"),
        {"scraping": {}},
    )
    assert isinstance(gh, GreenhouseScraper)

    lv = scraper_for(
        Company(name="Y", ats=ATSType.LEVER, url="https://jobs.lever.co/y"),
        {"scraping": {}},
    )
    assert isinstance(lv, LeverScraper)


def test_greenhouse_slug_extraction():
    from src.models import ATSType, Company
    from src.scrapers.greenhouse import GreenhouseScraper

    s = GreenhouseScraper(
        Company(
            name="Airbnb",
            ats=ATSType.GREENHOUSE,
            url="https://boards.greenhouse.io/airbnb",
        ),
        {"scraping": {}},
    )
    assert s._extract_slug() == "airbnb"


def test_match_prompt_builds():
    from src.tools.match_tools import build_match_prompt

    prompt = build_match_prompt("{}", "{}")
    assert "skill_match_score" in prompt
    assert "fit_score" in prompt

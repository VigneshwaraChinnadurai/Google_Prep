"""Smoke tests."""
from src.config import load_settings, load_companies


def test_load_settings():
    s = load_settings()
    assert s.llm.model
    assert s.scraping.max_concurrent > 0


def test_load_companies():
    c = load_companies()
    assert len(c) > 0
    assert all(x.ats in {"greenhouse", "lever", "workday", "generic"} for x in c)


def test_competition_adapters_registered():
    from src.competitions import all_adapters
    a = all_adapters()
    assert {"hackerearth", "hackerrank", "unstop", "kaggle"} <= set(a.keys())


def test_company_matcher():
    from src.competitions._company_matcher import match_company
    # If "Google" is in companies.yaml, these messy strings should match it
    assert match_company("Google LLC") == "Google"
    assert match_company("google inc.") == "Google"
    # Garbage shouldn't match anything
    assert match_company("") is None
    assert match_company("Some Random Co That Doesnt Exist 9999") is None

"""Hiring/coding competition data model."""
from __future__ import annotations
from datetime import datetime
from pydantic import BaseModel, Field, HttpUrl


class Competition(BaseModel):
    id: str                                   # platform-prefixed, e.g. "hackerearth-12345"
    platform: str                             # hackerearth | hackerrank | unstop | kaggle
    title: str
    sponsor_company: str | None = None        # raw text as listed on platform
    matched_company: str | None = None        # canonical name from companies.yaml if matched
    is_tracked_company: bool = False
    is_hiring: bool = False                   # platform-flagged hiring event
    starts_at: datetime | None = None
    ends_at: datetime | None = None
    registration_url: HttpUrl
    prize_pool: str | None = None
    location: str | None = None               # "Online" most often
    eligibility: str | None = None            # e.g. "Final year students", "All"
    tags: list[str] = Field(default_factory=list)
    description_snippet: str = ""

from __future__ import annotations
from pydantic import BaseModel, HttpUrl


class Job(BaseModel):
    id: str
    title: str
    company: str
    location: str | None = None
    apply_url: HttpUrl
    description: str = ""
    posted_at: str | None = None
    employment_type: str | None = None

from __future__ import annotations
from typing import Literal
from pydantic import BaseModel, Field, HttpUrl


class FieldHint(BaseModel):
    label: str
    value: str
    field_type: Literal["text", "url", "email", "long_text", "select"] = "text"
    confidence: float = Field(ge=0.0, le=1.0, default=1.0)


class ApplyPacket(BaseModel):
    match_id: str
    job_title: str
    company: str
    application_url: HttpUrl
    cover_letter: str
    field_hints: list[FieldHint]
    tailored_resume_summary: str | None = None
    generated_at: str

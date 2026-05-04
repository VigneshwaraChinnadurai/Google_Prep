from __future__ import annotations
from typing import Literal
from pydantic import BaseModel, Field
from .job import Job


class MatchResult(BaseModel):
    id: str
    job: Job
    fit_score: float = Field(ge=0.0, le=1.0)
    fit_reasoning: str = ""
    matched_skills: list[str] = Field(default_factory=list)
    gap_skills: list[str] = Field(default_factory=list)
    status: Literal["new", "approved", "skipped"] = "new"

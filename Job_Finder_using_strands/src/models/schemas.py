"""
Pydantic data models used throughout the app.
These double as JSON schemas the LLM fills in via Strands' structured_output.
"""
from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import Optional

from pydantic import BaseModel, Field, HttpUrl


# ---------- Resume models ----------

class Skill(BaseModel):
    name: str
    category: Optional[str] = Field(
        default=None,
        description="e.g. 'language', 'framework', 'cloud', 'database', 'soft'",
    )
    years: Optional[float] = Field(default=None, description="Years of experience")


class Experience(BaseModel):
    company: str
    title: str
    start_date: Optional[str] = None  # free-form; resumes vary
    end_date: Optional[str] = None
    location: Optional[str] = None
    description: Optional[str] = None
    technologies: list[str] = Field(default_factory=list)


class Education(BaseModel):
    institution: str
    degree: Optional[str] = None
    field_of_study: Optional[str] = None
    graduation_year: Optional[int] = None
    gpa: Optional[float] = None


class Resume(BaseModel):
    """Structured profile extracted from the raw resume."""

    full_name: str
    email: Optional[str] = None
    phone: Optional[str] = None
    location: Optional[str] = None
    linkedin: Optional[str] = None
    github: Optional[str] = None
    portfolio: Optional[str] = None

    summary: Optional[str] = Field(
        default=None,
        description="2-3 sentence professional summary",
    )

    total_years_experience: float = Field(
        default=0.0,
        description="Total years of professional experience",
    )

    field: Optional[str] = Field(
        default=None,
        description="Primary field e.g. 'Software Engineering', 'Data Science'",
    )

    seniority: Optional[str] = Field(
        default=None,
        description="e.g. 'Junior', 'Mid', 'Senior', 'Staff', 'Principal'",
    )

    skills: list[Skill] = Field(default_factory=list)
    experiences: list[Experience] = Field(default_factory=list)
    education: list[Education] = Field(default_factory=list)
    certifications: list[str] = Field(default_factory=list)

    preferred_locations: list[str] = Field(
        default_factory=lambda: ["Bangalore", "Bengaluru", "Remote"],
    )
    preferred_roles: list[str] = Field(default_factory=list)

    raw_text: Optional[str] = Field(
        default=None, description="Original resume text, kept for reference"
    )


# ---------- Company & Job models ----------

class ATSType(str, Enum):
    GREENHOUSE = "greenhouse"
    LEVER = "lever"
    WORKDAY = "workday"
    SMARTRECRUITERS = "smartrecruiters"
    ASHBY = "ashby"
    GENERIC = "generic"


class Company(BaseModel):
    name: str
    ats: ATSType = ATSType.GENERIC
    url: str
    location_filters: list[str] = Field(default_factory=list)


class Job(BaseModel):
    id: str = Field(description="Stable ID (url hash) — used for dedup")
    company: str
    title: str
    location: Optional[str] = None
    url: str
    description: Optional[str] = None
    department: Optional[str] = None
    employment_type: Optional[str] = None  # Full-time, Contract, etc.
    posted_date: Optional[str] = None
    requirements: list[str] = Field(default_factory=list)
    technologies: list[str] = Field(default_factory=list)
    scraped_at: datetime = Field(default_factory=datetime.utcnow)


# ---------- Match & Application models ----------

class MatchResult(BaseModel):
    """Output of the Matcher agent — what the UI renders as an approvable card."""

    job: Job
    fit_score: float = Field(ge=0.0, le=1.0, description="Overall fit score")
    skill_match_score: float = Field(ge=0.0, le=1.0)
    experience_match_score: float = Field(ge=0.0, le=1.0)
    role_alignment_score: float = Field(ge=0.0, le=1.0)
    location_score: float = Field(ge=0.0, le=1.0)

    matched_skills: list[str] = Field(default_factory=list)
    missing_skills: list[str] = Field(default_factory=list)

    rationale: str = Field(
        description="1-2 sentence explanation of why this is a good (or poor) match"
    )

    recommendation: str = Field(
        description="'strong_match' | 'good_match' | 'stretch' | 'poor_match'"
    )


class ApplicationStatus(str, Enum):
    PENDING_REVIEW = "pending_review"
    APPROVED = "approved"
    SKIPPED = "skipped"
    APPLYING = "applying"
    PREFILLED_AWAITING_SUBMIT = "prefilled_awaiting_submit"
    SUBMITTED = "submitted"
    FAILED = "failed"


class Application(BaseModel):
    job_id: str
    status: ApplicationStatus = ApplicationStatus.PENDING_REVIEW
    applied_at: Optional[datetime] = None
    notes: Optional[str] = None
    cover_letter: Optional[str] = None
    error: Optional[str] = None

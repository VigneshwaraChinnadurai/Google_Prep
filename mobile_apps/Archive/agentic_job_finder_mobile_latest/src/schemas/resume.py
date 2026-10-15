from __future__ import annotations
from pydantic import BaseModel, EmailStr, Field, HttpUrl


class Experience(BaseModel):
    title: str
    company: str
    start_date: str | None = None
    end_date: str | None = None
    description: str | None = None


class Resume(BaseModel):
    first_name: str
    last_name: str
    email: EmailStr
    phone: str | None = None
    linkedin_url: HttpUrl | None = None
    github_url: HttpUrl | None = None
    portfolio_url: HttpUrl | None = None
    top_skills: list[str] = Field(default_factory=list)
    experience: list[Experience] = Field(default_factory=list)
    summary: str | None = None

    @property
    def full_name(self) -> str:
        return f"{self.first_name} {self.last_name}"

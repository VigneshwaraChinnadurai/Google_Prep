"""
Applicator Agent.

On user approval:
  1. Optionally generates a tailored cover letter for this specific job.
  2. Opens the portal in a visible browser via `prefill_application`.
  3. Returns a summary of what was filled / skipped so the UI can show it.

The browser stays open for the user to finish.
"""
from __future__ import annotations

import json
from typing import Optional

from strands import Agent

from ..config import build_model, load_settings
from ..models import MatchResult, Resume
from ..tools.apply_tools import prefill_application

COVER_LETTER_SYSTEM = """\
You write concise, specific cover letters in the candidate's voice. Keep
under 200 words. Lead with ONE concrete accomplishment from the resume that
maps to the job's top requirement. No generic filler. No 'I am writing to
apply for...' openers.
"""

APPLICATOR_SYSTEM = """\
You are a job-application assistant. Use `prefill_application` to open the
portal and pre-fill the form. Report the fields you filled and which ones
still need the user's attention.
"""


class ApplicatorAgent:
    def __init__(self):
        self.cover_agent = Agent(
            model=build_model(),
            system_prompt=COVER_LETTER_SYSTEM,
            name="CoverLetterWriter",
        )
        self.apply_agent = Agent(
            model=build_model(),
            system_prompt=APPLICATOR_SYSTEM,
            tools=[prefill_application],
            name="Applicator",
        )

    def generate_cover_letter(self, resume: Resume, match: MatchResult) -> str:
        """Ask the LLM for a tailored cover letter."""
        settings = load_settings()
        if not settings.get("applicator", {}).get("auto_fill_cover_letter", True):
            return ""

        prompt = f"""\
Write a cover letter for this application.

Candidate profile:
  Name: {resume.full_name}
  Field: {resume.field}
  Years of experience: {resume.total_years_experience}
  Top skills: {", ".join(s.name for s in resume.skills[:10])}
  Recent role: {resume.experiences[0].title + " at " + resume.experiences[0].company
              if resume.experiences else "N/A"}

Job:
  Company: {match.job.company}
  Title: {match.job.title}
  Location: {match.job.location}
  Description excerpt: {(match.job.description or "")[:1200]}

Matched skills: {", ".join(match.matched_skills)}
Gaps: {", ".join(match.missing_skills) or "none major"}

Write the cover letter now.
"""
        response = self.cover_agent(prompt)
        return _as_str(response).strip()

    def apply(
        self, resume: Resume, match: MatchResult, cover_letter: Optional[str] = None
    ) -> dict:
        """Pre-fill the application form. Returns the tool's result dict."""
        if cover_letter is None:
            cover_letter = self.generate_cover_letter(resume, match)

        result = self.apply_agent.tool.prefill_application(
            job_url=match.job.url, cover_letter=cover_letter or None
        )
        result_str = _as_str(result)
        try:
            return json.loads(result_str)
        except json.JSONDecodeError:
            return {"success": False, "error": "tool returned non-JSON", "raw": result_str}


def _as_str(obj) -> str:
    if isinstance(obj, str):
        return obj
    for attr in ("content", "output", "result", "message"):
        v = getattr(obj, attr, None)
        if isinstance(v, str):
            return v
        if isinstance(v, list) and v:
            first = v[0]
            if isinstance(first, dict) and "text" in first:
                return first["text"]
            if hasattr(first, "text"):
                return first.text
    return str(obj)

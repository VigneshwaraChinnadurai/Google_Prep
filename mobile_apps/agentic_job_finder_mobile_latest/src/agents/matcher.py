"""Matcher agent — scores a Resume against a Job using the configured LLM.

Uses Gemini to produce a structured fit assessment with score, reasoning,
matched skills, and gap skills.
"""
from __future__ import annotations
import hashlib
import json
import logging

from src.config import load_settings
from src.schemas import Resume, Job, MatchResult

log = logging.getLogger(__name__)

_SCORE_PROMPT = """\
You are a job-fit evaluator. Given the candidate's resume data and a job posting,
produce **only** a valid JSON object (no markdown fences) with these fields:

{{
  "fit_score": <float 0.0-1.0>,
  "fit_reasoning": "<2-3 sentences explaining the fit>",
  "matched_skills": ["skill1", "skill2", ...],
  "gap_skills": ["skill1", "skill2", ...]
}}

Scoring weights: skills={w_skills}, experience={w_exp}, seniority={w_sen}.
Be honest — a poor fit must score below 0.4.

--- Candidate ---
Name: {name}
Top skills: {skills}
Summary: {summary}
Experience:
{experience}

--- Job ---
Title: {job_title}
Company: {job_company}
Location: {job_location}
Description:
{job_desc}
"""


class MatcherAgent:
    async def score(self, resume: Resume, job: Job) -> MatchResult:
        settings = load_settings()
        llm_cfg = settings.llm
        weights = settings.matching.weights

        exp_lines = []
        for e in resume.experience[:5]:
            line = f"- {e.title} @ {e.company}"
            if e.start_date:
                line += f" ({e.start_date} – {e.end_date or 'present'})"
            exp_lines.append(line)

        prompt = _SCORE_PROMPT.format(
            w_skills=weights.skills,
            w_exp=weights.experience,
            w_sen=weights.seniority,
            name=resume.full_name,
            skills=", ".join(resume.top_skills[:10]),
            summary=resume.summary or "N/A",
            experience="\n".join(exp_lines) or "N/A",
            job_title=job.title,
            job_company=job.company,
            job_location=job.location or "N/A",
            job_desc=job.description[:2000],
        )

        import google.genai as genai

        client = genai.Client(api_key=llm_cfg.api_key)
        response = client.models.generate_content(
            model=llm_cfg.model,
            contents=prompt,
            config=genai.types.GenerateContentConfig(
                temperature=llm_cfg.temperature,
            ),
        )

        response_text = response.text.strip()
        # Strip markdown fences if the model added them
        if response_text.startswith("```"):
            lines = response_text.split("\n")
            lines = [l for l in lines if not l.strip().startswith("```")]
            response_text = "\n".join(lines)

        data = json.loads(response_text)

        match_id = hashlib.sha256(job.id.encode()).hexdigest()[:12]

        return MatchResult(
            id=match_id,
            job=job,
            fit_score=max(0.0, min(1.0, float(data.get("fit_score", 0)))),
            fit_reasoning=data.get("fit_reasoning", ""),
            matched_skills=data.get("matched_skills", []),
            gap_skills=data.get("gap_skills", []),
        )

"""
Matcher Agent.

For each job, uses structured_output against the MatchResult schema to get a
rigorous, comparable score. Results are sorted and filtered by the threshold.

We deliberately score one job at a time instead of batching N-at-once. It
keeps each LLM call small (~2-3K tokens in, ~400 out), which is the sweet
spot for Gemini Flash both cost-wise and quality-wise — batching tends to
dilute per-job reasoning quality.
"""
from __future__ import annotations

import json
from typing import Iterable

from strands import Agent

from ..config import build_model
from ..models import Job, MatchResult, Resume
from ..tools.match_tools import (
    build_match_prompt,
    filter_matches_by_threshold,
    save_matches,
)

SYSTEM_PROMPT = """\
You are a rigorous technical recruiter. You score candidate-job fit honestly
— you do NOT inflate scores to be encouraging. A strong_match must truly be
one: skills overlap, experience level aligns, role alignment is clear, and
location works. Flag stretches as stretches.
"""


class MatcherAgent:
    def __init__(self):
        self.agent = Agent(
            model=build_model(),
            system_prompt=SYSTEM_PROMPT,
            tools=[filter_matches_by_threshold, save_matches],
            name="Matcher",
        )

    def score_one(self, resume: Resume, job: Job) -> MatchResult:
        """Score a single job. Retries once on structured-output parse errors."""
        resume_json = resume.model_dump_json(exclude={"raw_text"})
        job_json = job.model_dump_json()
        prompt = build_match_prompt(resume_json, job_json)

        try:
            return self.agent.structured_output(MatchResult, prompt)
        except Exception as e:
            # One retry with a gentler nudge
            retry_prompt = (
                prompt
                + "\n\nIMPORTANT: Return valid JSON strictly matching the "
                "MatchResult schema. Do not add extra fields."
            )
            return self.agent.structured_output(MatchResult, retry_prompt)

    def score_all(
        self, resume: Resume, jobs: Iterable[Job], *, on_progress=None
    ) -> list[MatchResult]:
        """Score every job. `on_progress(i, total, job)` is called between jobs."""
        job_list = list(jobs)
        results: list[MatchResult] = []
        for i, job in enumerate(job_list, start=1):
            try:
                mr = self.score_one(resume, job)
            except Exception as e:
                print(f"[matcher] failed to score {job.company}/{job.title!r}: {e!r}")
                continue
            results.append(mr)
            if on_progress:
                on_progress(i, len(job_list), job)
        return results

    def filter_and_save(
        self, matches: list[MatchResult], threshold: float | None = None
    ) -> list[MatchResult]:
        """Filter by threshold and persist to data/matches.json."""
        matches_json = json.dumps([m.model_dump(mode="json") for m in matches])

        filtered_str = self.agent.tool.filter_matches_by_threshold(
            matches_json=matches_json, threshold=threshold
        )
        filtered_data = json.loads(_as_str(filtered_str))

        self.agent.tool.save_matches(matches_json=json.dumps(filtered_data))
        return [MatchResult(**m) for m in filtered_data]


def _as_str(tool_result) -> str:
    if isinstance(tool_result, str):
        return tool_result
    for attr in ("content", "output", "result"):
        v = getattr(tool_result, attr, None)
        if isinstance(v, str):
            return v
        if isinstance(v, list) and v:
            first = v[0]
            if isinstance(first, dict) and "text" in first:
                return first["text"]
            if hasattr(first, "text"):
                return first.text
    return str(tool_result)

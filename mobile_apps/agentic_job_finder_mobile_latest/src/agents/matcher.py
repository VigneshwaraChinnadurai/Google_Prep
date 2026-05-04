"""Matcher agent — scores a Resume against a Job using the configured LLM.

Stub: plug your Strands-based scorer here. Must return a MatchResult with a
deterministic id derived from job.id.
"""
from __future__ import annotations
from src.schemas import Resume, Job, MatchResult


class MatcherAgent:
    async def score(self, resume: Resume, job: Job) -> MatchResult:
        # TODO: replace with structured_output() Strands call.
        raise NotImplementedError("Wire your existing matcher here.")

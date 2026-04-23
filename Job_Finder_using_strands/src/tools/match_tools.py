"""
Tools for the Matcher agent.

`score_job_against_resume` is the heavy lifter — it asks the LLM (via the
agent's structured_output) for a MatchResult given a job + profile. The
Matcher agent calls it once per job.
"""
from __future__ import annotations

import json
from typing import Optional

from strands import tool

from ..config import DATA_DIR, load_settings


@tool
def filter_matches_by_threshold(matches_json: str, threshold: Optional[float] = None) -> str:
    """
    Filter a list of MatchResults down to those at or above the fit-score
    threshold. Also sorts by fit_score descending.

    Args:
        matches_json: JSON array of MatchResult objects.
        threshold: Override the configured threshold (0.0 - 1.0). Falls back to
                   settings.yaml value if None.

    Returns:
        JSON array of matches at/above threshold, sorted best-first.
    """
    try:
        matches = json.loads(matches_json)
    except json.JSONDecodeError as e:
        return json.dumps({"error": f"invalid JSON: {e}"})

    if threshold is None:
        threshold = load_settings()["matching"]["threshold"]

    filtered = [m for m in matches if m.get("fit_score", 0) >= threshold]
    filtered.sort(key=lambda m: m.get("fit_score", 0), reverse=True)

    return json.dumps(filtered, indent=2)


@tool
def save_matches(matches_json: str) -> str:
    """Persist match results to data/matches.json for the UI to read."""
    target = DATA_DIR / "matches.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    try:
        parsed = json.loads(matches_json)
    except json.JSONDecodeError as e:
        return f"ERROR: invalid JSON — {e}"
    target.write_text(
        json.dumps(parsed, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    return f"Saved {len(parsed)} matches to {target}"


# Convenience non-tool helper — called directly by the MatcherAgent,
# which wraps it with structured_output for per-job scoring.
def build_match_prompt(resume_json: str, job_json: str) -> str:
    """Construct the prompt body sent to the LLM for a single job scoring."""
    return f"""
You are a technical recruiter evaluating how well a candidate fits a specific
job posting. Score rigorously; do not inflate scores.

CANDIDATE PROFILE (JSON):
{resume_json}

JOB POSTING (JSON):
{job_json}

Evaluate on these dimensions, each 0.0 - 1.0:
  - skill_match_score: how many of the job's required skills the candidate has
  - experience_match_score: does the candidate's years of experience fit the
    seniority level the role calls for?
  - role_alignment_score: does the job title/responsibilities align with the
    candidate's field and career trajectory?
  - location_score: 1.0 if the job location is in the candidate's preferred
    list, 0.5 if remote-friendly, 0.0 otherwise.

Then compute fit_score as a weighted average using weights:
  skills 0.45, experience 0.25, role_alignment 0.20, location 0.10

Populate:
  - matched_skills: skills present in BOTH the candidate's profile and the JD
  - missing_skills: skills the JD requires but the candidate lacks
  - rationale: one honest 1-2 sentence explanation
  - recommendation: exactly one of 'strong_match', 'good_match', 'stretch', 'poor_match'
      - strong_match: fit_score >= 0.80
      - good_match:   0.65 <= fit_score < 0.80
      - stretch:      0.50 <= fit_score < 0.65
      - poor_match:   fit_score < 0.50

Return the Job object unchanged in the `job` field.
""".strip()

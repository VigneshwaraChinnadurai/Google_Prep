"""
Resume Parser Agent.

Takes the raw text of a resume and produces a structured `Resume` object.
Uses Strands' `structured_output` which under the hood forces the LLM to
return JSON matching the Pydantic schema.
"""
from __future__ import annotations

from strands import Agent

from ..config import build_model
from ..models import Resume
from ..tools.resume_tools import read_resume_file

SYSTEM_PROMPT = """\
You are a resume-parsing assistant. Your job is to read a resume and extract
a structured profile.

Be accurate and thorough:
- Extract EVERY skill mentioned, including tools, frameworks, languages, and
  soft skills. Put them in `skills`, each with a category.
- Compute `total_years_experience` from the work history (approximate is OK).
- Infer `field` (e.g. 'Software Engineering', 'Data Science', 'ML Engineering',
  'DevOps', 'Product Management') from the job titles and skills.
- Infer `seniority` from years + titles (Junior <2y, Mid 2-5y, Senior 5-10y,
  Staff 10+y with leadership signals, Principal 15+y).
- Populate `preferred_locations` with Bangalore/Bengaluru first if the resume
  mentions either; include 'Remote' if there's any remote-work signal.
- Populate `preferred_roles` with 3-5 job titles this candidate would be a
  strong fit for.
- Do not hallucinate. If a field is unclear, leave it null.
"""


class ResumeParserAgent:
    def __init__(self):
        self.agent = Agent(
            model=build_model(),
            system_prompt=SYSTEM_PROMPT,
            tools=[read_resume_file],
            name="ResumeParser",
        )

    def parse(self, resume_path: str) -> Resume:
        """Parse a resume from disk into a Resume model."""
        # Step 1: read the raw text via the tool (direct tool call)
        raw_text = self.agent.tool.read_resume_file(path=resume_path)
        # Strands tool calls return a result object — extract the string payload
        raw_text_str = _as_str(raw_text)

        if raw_text_str.startswith("ERROR"):
            raise RuntimeError(f"Could not read resume: {raw_text_str}")

        # Step 2: structured extraction
        profile = self.agent.structured_output(
            Resume,
            f"Parse this resume into the Resume schema:\n\n{raw_text_str}",
        )
        # Stash the raw text for reference
        profile.raw_text = raw_text_str
        return profile


def _as_str(tool_result) -> str:
    """Strands tool results can be an object with `.content[0].text` or a plain string."""
    if isinstance(tool_result, str):
        return tool_result
    # Strands >= 1.x wraps tool outputs; try common shapes
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

"""Resume parser agent — PDF/DOCX → Resume.

Extracts raw text with pypdf / python-docx, then asks Gemini to produce
a structured Resume JSON. The orchestrator only calls .parse() and
.load_cached(); keep those signatures stable.
"""
from __future__ import annotations
import json
import logging
from pathlib import Path

from src.config import load_settings
from src.schemas import Resume

log = logging.getLogger(__name__)
DATA_DIR = Path("data")

_EXTRACT_PROMPT = """\
You are a resume-parsing assistant. Given the raw text of a resume,
return **only** a valid JSON object matching this schema (no markdown fences):

{{
  "first_name": "...",
  "last_name": "...",
  "email": "...",
  "phone": "..." or null,
  "linkedin_url": "..." or null,
  "github_url": "..." or null,
  "portfolio_url": "..." or null,
  "top_skills": ["skill1", "skill2", ...],
  "experience": [
    {{
      "title": "...",
      "company": "...",
      "start_date": "..." or null,
      "end_date": "..." or null,
      "description": "..." or null
    }}
  ],
  "summary": "..." or null
}}

Resume text:
{text}
"""


def _extract_text(path: Path) -> str:
    """Extract plain text from PDF or DOCX."""
    suffix = path.suffix.lower()
    if suffix == ".pdf":
        from pypdf import PdfReader
        reader = PdfReader(str(path))
        return "\n".join(page.extract_text() or "" for page in reader.pages)
    elif suffix in (".docx", ".doc"):
        from docx import Document
        doc = Document(str(path))
        return "\n".join(p.text for p in doc.paragraphs)
    else:
        raise ValueError(f"Unsupported file type: {suffix}. Use PDF or DOCX.")


class ResumeParserAgent:
    def __init__(self) -> None:
        DATA_DIR.mkdir(exist_ok=True)
        self._cache_path = DATA_DIR / "profile.json"

    def load_cached(self) -> Resume | None:
        if not self._cache_path.exists():
            return None
        return Resume.model_validate_json(self._cache_path.read_text(encoding="utf-8"))

    async def parse(self, file_path: str | Path, *, persist: bool = True) -> Resume:
        path = Path(file_path)
        if not path.exists():
            raise FileNotFoundError(f"Resume file not found: {path}")

        # 1. Extract raw text
        raw_text = _extract_text(path)
        if not raw_text.strip():
            raise ValueError("Could not extract any text from the resume file.")
        log.info("Extracted %d chars from %s", len(raw_text), path.name)

        # 2. Ask Gemini to structure it
        settings = load_settings()
        llm_cfg = settings.llm

        import google.genai as genai

        client = genai.Client(api_key=llm_cfg.api_key)
        response = client.models.generate_content(
            model=llm_cfg.model,
            contents=_EXTRACT_PROMPT.format(text=raw_text),
            config=genai.types.GenerateContentConfig(
                temperature=llm_cfg.temperature,
            ),
        )

        # 3. Parse the JSON response
        response_text = response.text.strip()
        # Strip markdown fences if the model added them
        if response_text.startswith("```"):
            lines = response_text.split("\n")
            lines = [l for l in lines if not l.strip().startswith("```")]
            response_text = "\n".join(lines)

        resume = Resume.model_validate_json(response_text)
        log.info("Parsed resume for %s", resume.full_name)

        # 4. Cache
        if persist:
            self._cache_path.write_text(resume.model_dump_json(indent=2), encoding="utf-8")
            log.info("Cached resume to %s", self._cache_path)

        return resume

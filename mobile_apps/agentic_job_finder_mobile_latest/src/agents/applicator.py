"""Applicator agent — generates an ApplyPacket. Never auto-submits."""
from __future__ import annotations
from datetime import datetime, timezone
from strands import Agent
from strands.models.gemini import GeminiModel
from src.config import load_settings
from src.schemas import Resume, MatchResult
from src.schemas.apply_packet import ApplyPacket, FieldHint


class ApplicatorAgent:
    def __init__(self) -> None:
        cfg = load_settings()
        self.agent = Agent(
            model=GeminiModel(model_id=cfg.llm.model, api_key=cfg.llm.api_key),
            system_prompt=(
                "You write concise, role-specific cover letters (180-220 words). "
                "Match tone to seniority. No clichés. No 'I am writing to apply'."
            ),
        )

    async def build_packet(self, resume: Resume, match: MatchResult) -> ApplyPacket:
        cover = await self._generate_cover_letter(resume, match)
        return ApplyPacket(
            match_id=match.id,
            job_title=match.job.title,
            company=match.job.company,
            application_url=match.job.apply_url,
            cover_letter=cover,
            field_hints=self._extract_field_hints(resume),
            generated_at=datetime.now(timezone.utc).isoformat(),
        )

    async def _generate_cover_letter(self, resume: Resume, match: MatchResult) -> str:
        recent = resume.experience[0] if resume.experience else None
        recent_str = f"{recent.title} @ {recent.company}" if recent else "—"
        prompt = (
            f"Candidate: {resume.full_name}\n"
            f"Top skills: {', '.join(resume.top_skills[:8])}\n"
            f"Recent role: {recent_str}\n\n"
            f"Job: {match.job.title} at {match.job.company}\n"
            f"Job summary: {match.job.description[:1200]}\n"
            f"Why this is a fit: {match.fit_reasoning}\n\n"
            "Write the cover letter."
        )
        result = await self.agent.invoke_async(prompt)
        return str(result.message).strip()

    @staticmethod
    def _extract_field_hints(resume: Resume) -> list[FieldHint]:
        hints = [
            FieldHint(label="First name", value=resume.first_name),
            FieldHint(label="Last name", value=resume.last_name),
            FieldHint(label="Email", value=str(resume.email), field_type="email"),
            FieldHint(label="Phone", value=resume.phone or ""),
        ]
        if resume.linkedin_url:
            hints.append(FieldHint(label="LinkedIn", value=str(resume.linkedin_url), field_type="url"))
        if resume.github_url:
            hints.append(FieldHint(label="GitHub", value=str(resume.github_url), field_type="url"))
        if resume.portfolio_url:
            hints.append(FieldHint(label="Portfolio", value=str(resume.portfolio_url), field_type="url"))
        return [h for h in hints if h.value]

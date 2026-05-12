"""Persistent pipeline state — tracks which phases have completed.

Saved to data/pipeline_state.json so the pipeline can resume after
a crash, timeout, or manual stop.
"""
from __future__ import annotations
import json
import logging
from datetime import datetime
from pathlib import Path
from typing import Literal

from pydantic import BaseModel, Field

logger = logging.getLogger(__name__)
DATA_DIR = Path("data")

Phase = Literal["scrape", "match", "competitions"]
PhaseStatus = Literal["pending", "running", "done", "error", "skipped"]

ALL_PHASES: list[Phase] = ["scrape", "match", "competitions"]


class PhaseState(BaseModel):
    status: PhaseStatus = "pending"
    started_at: str | None = None
    finished_at: str | None = None
    message: str = ""
    progress: float = 0.0


class PipelineState(BaseModel):
    """Full pipeline run state, persisted to disk."""
    phases: dict[Phase, PhaseState] = Field(
        default_factory=lambda: {p: PhaseState() for p in ALL_PHASES}
    )
    last_updated: str = ""

    @property
    def current_phase(self) -> Phase | None:
        """Return the first phase that isn't done/skipped."""
        for p in ALL_PHASES:
            if self.phases[p].status not in ("done", "skipped"):
                return p
        return None

    @property
    def is_complete(self) -> bool:
        return all(
            self.phases[p].status in ("done", "skipped") for p in ALL_PHASES
        )

    @property
    def can_resume(self) -> bool:
        """True if there are phases left (error or pending) to run."""
        return not self.is_complete

    def next_phase_after(self, phase: Phase) -> Phase | None:
        """Return the phase after the given one, or None."""
        idx = ALL_PHASES.index(phase)
        if idx + 1 < len(ALL_PHASES):
            return ALL_PHASES[idx + 1]
        return None

    def mark_running(self, phase: Phase, message: str = "") -> None:
        self.phases[phase].status = "running"
        self.phases[phase].started_at = datetime.now().isoformat()
        self.phases[phase].message = message
        self.phases[phase].progress = 0.0
        self._touch()

    def mark_progress(self, phase: Phase, progress: float, message: str = "") -> None:
        self.phases[phase].progress = progress
        if message:
            self.phases[phase].message = message
        self._touch()

    def mark_done(self, phase: Phase, message: str = "") -> None:
        self.phases[phase].status = "done"
        self.phases[phase].finished_at = datetime.now().isoformat()
        self.phases[phase].progress = 1.0
        self.phases[phase].message = message or "Done"
        self._touch()

    def mark_error(self, phase: Phase, message: str) -> None:
        self.phases[phase].status = "error"
        self.phases[phase].finished_at = datetime.now().isoformat()
        self.phases[phase].message = message
        self._touch()

    def reset(self) -> None:
        """Reset all phases to pending (fresh run)."""
        for p in ALL_PHASES:
            self.phases[p] = PhaseState()
        self._touch()

    def reset_from(self, phase: Phase) -> None:
        """Reset this phase and all subsequent ones to pending."""
        idx = ALL_PHASES.index(phase)
        for p in ALL_PHASES[idx:]:
            self.phases[p] = PhaseState()
        self._touch()

    def _touch(self) -> None:
        self.last_updated = datetime.now().isoformat()


_STATE_FILE = DATA_DIR / "pipeline_state.json"


def load_state() -> PipelineState:
    """Load pipeline state from disk, or return a fresh state."""
    DATA_DIR.mkdir(exist_ok=True)
    if _STATE_FILE.exists():
        try:
            return PipelineState.model_validate_json(
                _STATE_FILE.read_text(encoding="utf-8")
            )
        except Exception:
            logger.warning("Corrupt pipeline_state.json — starting fresh")
    return PipelineState()


def save_state(state: PipelineState) -> None:
    """Persist pipeline state to disk."""
    DATA_DIR.mkdir(exist_ok=True)
    _STATE_FILE.write_text(
        state.model_dump_json(indent=2), encoding="utf-8"
    )

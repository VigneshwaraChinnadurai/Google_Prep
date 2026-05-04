"""In-memory run registry. Single-user, single-process — no Redis needed."""
from __future__ import annotations
import asyncio
import uuid
from dataclasses import dataclass, field
from typing import Literal

RunStatus = Literal["pending", "running", "done", "error"]


@dataclass
class Run:
    id: str
    kind: Literal["scrape", "match"]
    status: RunStatus = "pending"
    progress: float = 0.0
    message: str = ""
    error: str | None = None
    queue: asyncio.Queue = field(default_factory=asyncio.Queue)


_RUNS: dict[str, Run] = {}


def create_run(kind: str) -> Run:
    run = Run(id=str(uuid.uuid4()), kind=kind)
    _RUNS[run.id] = run
    return run


def get_run(run_id: str) -> Run | None:
    return _RUNS.get(run_id)


async def emit(run: Run, *, progress: float, message: str, status: RunStatus = "running") -> None:
    run.progress = progress
    run.message = message
    run.status = status
    await run.queue.put({"progress": progress, "message": message, "status": status})

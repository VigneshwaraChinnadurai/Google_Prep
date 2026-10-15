"""Competition scraper registry — adapters self-register via @register_competition."""
from __future__ import annotations
from typing import Protocol
from Google_Prep.mobile_apps.Archive.agentic_job_finder_mobile_latest.src.schemas import Competition

_REGISTRY: dict[str, "CompetitionAdapter"] = {}


class CompetitionAdapter(Protocol):
    async def fetch(self) -> list[Competition]: ...


def register_competition(key: str):
    def deco(cls):
        _REGISTRY[key] = cls()
        return cls
    return deco


def all_adapters() -> dict[str, CompetitionAdapter]:
    return dict(_REGISTRY)


# Trigger registration
from .....agentic_job_finder_mobile_latest.src.competitions import hackerearth, hackerrank, unstop, kaggle  # noqa: F401,E402

import os
from fastapi import Header, HTTPException, status
from src.orchestrator import Orchestrator

# Shared singleton — all routes use this one instance
_shared_orch: Orchestrator | None = None


def get_orchestrator() -> Orchestrator:
    global _shared_orch
    if _shared_orch is None:
        _shared_orch = Orchestrator()
    return _shared_orch


def require_api_key(x_api_key: str | None = Header(default=None)) -> None:
    expected = os.environ.get("API_KEY")
    if not expected:
        raise HTTPException(500, "Server missing API_KEY")
    if x_api_key != expected:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid API key")

from fastapi import APIRouter, Depends, HTTPException
from src.api.deps import require_api_key
from src.orchestrator import Orchestrator
from src.schemas import MatchResult

router = APIRouter(dependencies=[Depends(require_api_key)])
_orch = Orchestrator()


@router.get("", response_model=list[MatchResult])
async def list_matches(min_score: float = 0.0, limit: int = 50) -> list[MatchResult]:
    return _orch.list_matches(min_score=min_score, limit=limit)


@router.get("/{match_id}", response_model=MatchResult)
async def get_match(match_id: str) -> MatchResult:
    m = _orch.get_match(match_id)
    if not m:
        raise HTTPException(404)
    return m


@router.post("/{match_id}/approve")
async def approve(match_id: str) -> dict:
    _orch.mark_match(match_id, status="approved")
    return {"ok": True}


@router.post("/{match_id}/skip")
async def skip(match_id: str) -> dict:
    _orch.mark_match(match_id, status="skipped")
    return {"ok": True}

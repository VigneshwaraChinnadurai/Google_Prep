from fastapi import APIRouter, Depends, HTTPException
from src.api.deps import require_api_key, get_orchestrator
from src.agents.applicator import ApplicatorAgent
from src.schemas.apply_packet import ApplyPacket

router = APIRouter(dependencies=[Depends(require_api_key)])
_app = ApplicatorAgent()


@router.get("/{match_id}/packet", response_model=ApplyPacket)
async def apply_packet(match_id: str) -> ApplyPacket:
    orch = get_orchestrator()
    match = orch.get_match(match_id)
    resume = orch.get_resume()
    if not match or not resume:
        raise HTTPException(404)
    return await _app.build_packet(resume, match)

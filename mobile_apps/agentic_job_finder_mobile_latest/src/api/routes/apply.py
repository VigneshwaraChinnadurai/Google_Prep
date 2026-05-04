from fastapi import APIRouter, Depends, HTTPException
from src.api.deps import require_api_key
from src.agents.applicator import ApplicatorAgent
from src.orchestrator import Orchestrator
from src.schemas.apply_packet import ApplyPacket

router = APIRouter(dependencies=[Depends(require_api_key)])
_orch = Orchestrator()
_app = ApplicatorAgent()


@router.get("/{match_id}/packet", response_model=ApplyPacket)
async def apply_packet(match_id: str) -> ApplyPacket:
    match = _orch.get_match(match_id)
    resume = _orch.get_resume()
    if not match or not resume:
        raise HTTPException(404)
    return await _app.build_packet(resume, match)

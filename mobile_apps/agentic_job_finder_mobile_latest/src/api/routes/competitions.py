import asyncio
import os
from fastapi import APIRouter, BackgroundTasks, Depends, Query, WebSocket
from src.api.deps import require_api_key, get_orchestrator
from src.api.runs import create_run, get_run, emit
from src.schemas import Competition

router = APIRouter()


@router.get("", response_model=list[Competition], dependencies=[Depends(require_api_key)])
async def list_competitions(
    tracked_only: bool = Query(False, description="Only show competitions sponsored by tracked companies"),
    hiring_only: bool = Query(False, description="Only show platform-flagged hiring contests"),
    platform: list[str] | None = Query(None, description="Filter by one or more platforms"),
    limit: int = Query(100, ge=1, le=500),
) -> list[Competition]:
    return get_orchestrator().list_competitions(
        tracked_only=tracked_only,
        hiring_only=hiring_only,
        platforms=platform,
        limit=limit,
    )


@router.post("/refresh", dependencies=[Depends(require_api_key)])
async def refresh(bg: BackgroundTasks) -> dict:
    run = create_run("scrape")  # reuse the run kind
    bg.add_task(_run_competitions, run.id)
    return {"run_id": run.id}


@router.websocket("/{run_id}/events")
async def run_events(ws: WebSocket, run_id: str):
    if ws.query_params.get("api_key") != os.environ.get("API_KEY"):
        await ws.close(code=4401); return
    run = get_run(run_id)
    if not run:
        await ws.close(code=4404); return
    await ws.accept()
    try:
        await ws.send_json({"progress": run.progress, "message": run.message, "status": run.status})
        while run.status in ("pending", "running"):
            try:
                event = await asyncio.wait_for(run.queue.get(), timeout=30)
                await ws.send_json(event)
            except asyncio.TimeoutError:
                await ws.send_json({"heartbeat": True})
    finally:
        await ws.close()


async def _run_competitions(run_id: str):
    run = get_run(run_id)
    assert run
    orch = get_orchestrator()
    try:
        platforms_done = 0
        # We don't know total ahead of time without inspecting the registry,
        # but it's small (4). Read it for accurate progress.
        from src.competitions import all_adapters
        total = max(len(all_adapters()), 1)

        async for platform, count in orch.competitions_iter():
            platforms_done += 1
            await emit(run,
                       progress=platforms_done / total,
                       message=f"{platform}: {count} competitions")
        await emit(run, progress=1.0, message="Done", status="done")
    except Exception as e:
        run.error = str(e)
        await emit(run, progress=run.progress, message=str(e), status="error")

import asyncio
import os
from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, WebSocket
from src.api.deps import require_api_key, get_orchestrator
from src.api.runs import create_run, get_run, emit

router = APIRouter()


@router.post("/scrape", dependencies=[Depends(require_api_key)])
async def start_scrape(bg: BackgroundTasks) -> dict:
    run = create_run("scrape")
    bg.add_task(_run_scrape, run.id)
    return {"run_id": run.id}


@router.post("/match", dependencies=[Depends(require_api_key)])
async def start_match(bg: BackgroundTasks) -> dict:
    orch = get_orchestrator()
    if not orch.has_resume():
        raise HTTPException(409, "Upload resume first")
    run = create_run("match")
    bg.add_task(_run_match, run.id)
    return {"run_id": run.id}


@router.websocket("/{run_id}/events")
async def run_events(ws: WebSocket, run_id: str):
    if ws.query_params.get("api_key") != os.environ.get("API_KEY"):
        await ws.close(code=4401)
        return
    run = get_run(run_id)
    if not run:
        await ws.close(code=4404)
        return
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


async def _run_scrape(run_id: str):
    run = get_run(run_id)
    assert run
    orch = get_orchestrator()
    try:
        async for company, count in orch.scrape_iter():
            await emit(run,
                       progress=count / max(orch.target_count, 1),
                       message=f"Scraped {company} ({count} jobs)")
        await emit(run, progress=1.0, message="Done", status="done")
    except Exception as e:
        run.error = str(e)
        await emit(run, progress=run.progress, message=str(e), status="error")


async def _run_match(run_id: str):
    run = get_run(run_id)
    assert run
    orch = get_orchestrator()
    try:
        async for done, total in orch.match_iter():
            await emit(run,
                       progress=(done / total) if total else 1.0,
                       message=f"Matched {done}/{total}")
        await emit(run, progress=1.0, message="Done", status="done")
    except Exception as e:
        run.error = str(e)
        await emit(run, progress=run.progress, message=str(e), status="error")

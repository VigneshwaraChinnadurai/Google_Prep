import asyncio
import logging
import os
from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, WebSocket
from src.api.deps import require_api_key, get_orchestrator
from src.api.runs import create_run, get_run, emit
from src.pipeline_state import (
    PipelineState, Phase, ALL_PHASES,
    load_state, save_state,
)

logger = logging.getLogger(__name__)
router = APIRouter()


# ── Pipeline state endpoints ──────────────────────────────────────────

@router.get("/state", dependencies=[Depends(require_api_key)])
async def pipeline_state() -> dict:
    """Return current pipeline state (all phases)."""
    state = load_state()
    return {
        "phases": {p: state.phases[p].model_dump() for p in ALL_PHASES},
        "current_phase": state.current_phase,
        "is_complete": state.is_complete,
        "can_resume": state.can_resume,
        "last_updated": state.last_updated,
    }


@router.post("/reset", dependencies=[Depends(require_api_key)])
async def pipeline_reset() -> dict:
    """Reset all phases to pending (start fresh)."""
    state = load_state()
    state.reset()
    save_state(state)
    return {"message": "Pipeline reset", "current_phase": state.current_phase}


@router.post("/reset-from/{phase}", dependencies=[Depends(require_api_key)])
async def pipeline_reset_from(phase: Phase) -> dict:
    """Reset this phase and all subsequent phases to pending."""
    state = load_state()
    state.reset_from(phase)
    save_state(state)
    return {"message": f"Reset from {phase}", "current_phase": state.current_phase}


# ── Run individual phases ─────────────────────────────────────────────

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


@router.post("/competitions", dependencies=[Depends(require_api_key)])
async def start_competitions(bg: BackgroundTasks) -> dict:
    run = create_run("competitions")
    bg.add_task(_run_competitions_phase, run.id)
    return {"run_id": run.id}


# ── Run full pipeline (all remaining phases) ──────────────────────────

@router.post("/run", dependencies=[Depends(require_api_key)])
async def run_full_pipeline(bg: BackgroundTasks) -> dict:
    """Run all pending/errored phases sequentially. Resumable."""
    orch = get_orchestrator()
    if not orch.has_resume():
        raise HTTPException(409, "Upload resume first")
    run = create_run("full")
    bg.add_task(_run_full_pipeline, run.id)
    return {"run_id": run.id}


@router.post("/resume", dependencies=[Depends(require_api_key)])
async def resume_pipeline(bg: BackgroundTasks) -> dict:
    """Resume from the last failed/pending phase."""
    state = load_state()
    if state.is_complete:
        return {"message": "Pipeline already complete. Use /reset to start fresh."}
    orch = get_orchestrator()
    if not orch.has_resume() and state.current_phase in ("match",):
        raise HTTPException(409, "Upload resume first")
    run = create_run("resume")
    bg.add_task(_run_full_pipeline, run.id)
    return {"run_id": run.id, "resuming_from": state.current_phase}


# ── WebSocket for progress ────────────────────────────────────────────

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
        await ws.send_json({
            "progress": run.progress,
            "message": run.message,
            "status": run.status,
            "phase": getattr(run, "phase", ""),
        })
        while run.status in ("pending", "running"):
            try:
                event = await asyncio.wait_for(run.queue.get(), timeout=30)
                await ws.send_json(event)
            except asyncio.TimeoutError:
                await ws.send_json({"heartbeat": True})
    finally:
        await ws.close()


# ── Phase runners ─────────────────────────────────────────────────────

async def _run_scrape(run_id: str):
    run = get_run(run_id)
    assert run
    orch = get_orchestrator()
    state = load_state()
    state.mark_running("scrape", "Scraping jobs…")
    save_state(state)
    try:
        async for company, count in orch.scrape_iter():
            pct = count / max(orch.target_count, 1)
            state.mark_progress("scrape", pct, f"Scraped {company} ({count} jobs)")
            save_state(state)
            await emit(run, progress=pct,
                       message=f"Scraped {company} ({count} jobs)", phase="scrape")
        state.mark_done("scrape", f"Done — {len(orch._jobs)} jobs")
        save_state(state)
        await emit(run, progress=1.0, message=f"Done — {len(orch._jobs)} jobs",
                   status="done", phase="scrape")
    except Exception as e:
        state.mark_error("scrape", str(e))
        save_state(state)
        run.error = str(e)
        await emit(run, progress=run.progress, message=str(e),
                   status="error", phase="scrape")


async def _run_match(run_id: str):
    run = get_run(run_id)
    assert run
    orch = get_orchestrator()
    state = load_state()
    state.mark_running("match", "Matching jobs…")
    save_state(state)
    try:
        async for done, total in orch.match_iter():
            pct = (done / total) if total else 1.0
            state.mark_progress("match", pct, f"Matched {done}/{total}")
            save_state(state)
            await emit(run, progress=pct,
                       message=f"Matched {done}/{total}", phase="match")
        state.mark_done("match", f"Done — {len(orch._matches)} matches")
        save_state(state)
        await emit(run, progress=1.0,
                   message=f"Done — {len(orch._matches)} matches",
                   status="done", phase="match")
    except Exception as e:
        state.mark_error("match", str(e))
        save_state(state)
        run.error = str(e)
        await emit(run, progress=run.progress, message=str(e),
                   status="error", phase="match")


async def _run_competitions_phase(run_id: str):
    run = get_run(run_id)
    assert run
    orch = get_orchestrator()
    state = load_state()
    state.mark_running("competitions", "Fetching competitions…")
    save_state(state)
    try:
        async for platform, count in orch.competitions_iter():
            state.mark_progress("competitions", 0.5,
                                f"{platform}: {count} competitions")
            save_state(state)
            await emit(run, progress=0.5,
                       message=f"{platform}: {count} competitions",
                       phase="competitions")
        state.mark_done("competitions")
        save_state(state)
        await emit(run, progress=1.0, message="Done",
                   status="done", phase="competitions")
    except Exception as e:
        state.mark_error("competitions", str(e))
        save_state(state)
        run.error = str(e)
        await emit(run, progress=run.progress, message=str(e),
                   status="error", phase="competitions")


async def _run_full_pipeline(run_id: str):
    """Run all phases that aren't yet done. Emits progress for each."""
    run = get_run(run_id)
    assert run
    state = load_state()
    orch = get_orchestrator()

    phases_to_run: list[Phase] = [
        p for p in ALL_PHASES
        if state.phases[p].status not in ("done", "skipped")
    ]

    if not phases_to_run:
        await emit(run, progress=1.0, message="All phases complete",
                   status="done", phase="done")
        return

    total_phases = len(phases_to_run)

    for i, phase in enumerate(phases_to_run):
        base_progress = i / total_phases

        try:
            if phase == "scrape":
                state.mark_running("scrape", "Scraping…")
                save_state(state)
                await emit(run, progress=base_progress,
                           message="Scraping jobs…", phase="scrape")
                async for company, count in orch.scrape_iter():
                    pct = base_progress + (count / max(orch.target_count, 1)) / total_phases
                    state.mark_progress("scrape", count / max(orch.target_count, 1),
                                        f"Scraped {company} ({count} jobs)")
                    save_state(state)
                    await emit(run, progress=pct,
                               message=f"Scraped {company} ({count} jobs)",
                               phase="scrape")
                state.mark_done("scrape", f"{len(orch._jobs)} jobs")
                save_state(state)

            elif phase == "match":
                if not orch.has_resume():
                    state.mark_error("match", "No resume uploaded")
                    save_state(state)
                    await emit(run, progress=base_progress,
                               message="Skipped match — no resume",
                               phase="match")
                    continue
                state.mark_running("match", "Matching…")
                save_state(state)
                await emit(run, progress=base_progress,
                           message="Matching jobs to resume…", phase="match")
                async for done, total in orch.match_iter():
                    pct = base_progress + ((done / total) if total else 1.0) / total_phases
                    state.mark_progress("match", (done / total) if total else 1.0,
                                        f"Matched {done}/{total}")
                    save_state(state)
                    await emit(run, progress=pct,
                               message=f"Matched {done}/{total}",
                               phase="match")
                state.mark_done("match", f"{len(orch._matches)} matches")
                save_state(state)

            elif phase == "competitions":
                state.mark_running("competitions", "Fetching…")
                save_state(state)
                await emit(run, progress=base_progress,
                           message="Fetching competitions…",
                           phase="competitions")
                async for platform, count in orch.competitions_iter():
                    state.mark_progress("competitions", 0.5,
                                        f"{platform}: {count}")
                    save_state(state)
                    await emit(run, progress=base_progress + 0.5 / total_phases,
                               message=f"{platform}: {count} competitions",
                               phase="competitions")
                state.mark_done("competitions")
                save_state(state)

        except Exception as e:
            logger.exception("Phase %s failed", phase)
            state.mark_error(phase, str(e))
            save_state(state)
            await emit(run, progress=base_progress,
                       message=f"{phase} failed: {e}",
                       status="error", phase=phase)
            return  # stop the pipeline — user can /resume later

    await emit(run, progress=1.0, message="All phases complete",
               status="done", phase="done")

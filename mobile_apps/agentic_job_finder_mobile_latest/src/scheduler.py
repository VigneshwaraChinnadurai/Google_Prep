"""Background scheduler — runs the scrape→match pipeline on a cron schedule.

Uses a simple asyncio.Task so there are zero extra dependencies.
Configure schedule in config/settings.yaml under the `scheduler:` key.
"""
from __future__ import annotations
import asyncio
import logging
from datetime import datetime, time as dt_time, timedelta

logger = logging.getLogger(__name__)

_task: asyncio.Task | None = None


async def _run_pipeline_once() -> None:
    """Execute a full scrape → match → competitions cycle using the shared orchestrator."""
    from src.api.deps import get_orchestrator
    from src.pipeline_state import load_state, save_state

    orch = get_orchestrator()
    state = load_state()
    state.reset()  # fresh run each night
    save_state(state)

    if not orch.has_resume():
        logger.warning("[scheduler] Skipping — no resume uploaded yet.")
        return

    # Phase 1: Scrape
    logger.info("[scheduler] ▶ Starting scheduled scrape…")
    state.mark_running("scrape")
    save_state(state)
    job_count = 0
    try:
        async for company, count in orch.scrape_iter():
            job_count = count
            logger.info("[scheduler]   scraped %s (cumulative %d jobs)", company, count)
        state.mark_done("scrape", f"{job_count} jobs")
        save_state(state)
    except Exception as e:
        state.mark_error("scrape", str(e))
        save_state(state)
        logger.exception("[scheduler] Scrape failed")
        return

    # Phase 2: Match
    logger.info("[scheduler] ▶ Starting scheduled match…")
    state.mark_running("match")
    save_state(state)
    matched = 0
    try:
        async for done, total in orch.match_iter():
            matched = done
        state.mark_done("match", f"{matched} matches")
        save_state(state)
    except Exception as e:
        state.mark_error("match", str(e))
        save_state(state)
        logger.exception("[scheduler] Match failed")
        return

    # Phase 3: Competitions
    logger.info("[scheduler] ▶ Starting scheduled competitions refresh…")
    state.mark_running("competitions")
    save_state(state)
    try:
        async for platform, count in orch.competitions_iter():
            logger.info("[scheduler]   %s: %d competitions", platform, count)
        state.mark_done("competitions")
        save_state(state)
    except Exception as e:
        state.mark_error("competitions", str(e))
        save_state(state)
        logger.exception("[scheduler] Competitions failed")
        return

    logger.info("[scheduler] ✓ Full pipeline complete")


def _seconds_until(target: dt_time) -> float:
    """Return seconds from now until the next occurrence of `target` (local time)."""
    now = datetime.now()
    target_dt = datetime.combine(now.date(), target)
    if target_dt <= now:
        target_dt += timedelta(days=1)
    return (target_dt - now).total_seconds()


async def _scheduler_loop(run_at: list[dt_time]) -> None:
    """Sleep until the next scheduled time, run the pipeline, repeat."""
    while True:
        # Find the nearest upcoming run time
        waits = [_seconds_until(t) for t in run_at]
        wait = min(waits)
        next_time = run_at[waits.index(wait)]
        logger.info("[scheduler] Next run at %s (in %.0f min)", next_time, wait / 60)
        await asyncio.sleep(wait)

        try:
            await _run_pipeline_once()
        except Exception:
            logger.exception("[scheduler] Pipeline run failed")

        # Small sleep to avoid re-triggering on the same second
        await asyncio.sleep(2)


def start(run_at: list[str] | None = None) -> None:
    """Start the scheduler as a background asyncio task.

    Args:
        run_at: List of HH:MM times (24h). Default ["00:00"] (midnight).
    """
    global _task
    if _task and not _task.done():
        logger.info("[scheduler] Already running.")
        return

    times_str = run_at or ["00:00"]
    times = []
    for s in times_str:
        h, m = s.strip().split(":")
        times.append(dt_time(int(h), int(m)))

    logger.info("[scheduler] Scheduling runs at %s", [t.strftime("%H:%M") for t in times])
    _task = asyncio.create_task(_scheduler_loop(times))


def stop() -> None:
    global _task
    if _task and not _task.done():
        _task.cancel()
        logger.info("[scheduler] Stopped.")
    _task = None

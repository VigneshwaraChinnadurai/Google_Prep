import os
import platform
import socket
from contextlib import asynccontextmanager
from fastapi import FastAPI, BackgroundTasks, Depends
from fastapi.middleware.cors import CORSMiddleware
from src.config import load_settings
from src.api.deps import require_api_key
from src.api.routes import resume, pipeline, matches, apply, competitions

cfg = load_settings()


@asynccontextmanager
async def lifespan(app: FastAPI):
    # ── Startup ──
    from src import scheduler
    if cfg.scheduler.enabled:
        scheduler.start(cfg.scheduler.run_at)
    yield
    # ── Shutdown ──
    scheduler.stop()


app = FastAPI(title="Job Finder API", version="0.2.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=cfg.api.cors_origins,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(resume.router,       prefix="/api/v1/resume",       tags=["resume"])
app.include_router(pipeline.router,     prefix="/api/v1/pipeline",     tags=["pipeline"])
app.include_router(matches.router,      prefix="/api/v1/matches",      tags=["matches"])
app.include_router(apply.router,        prefix="/api/v1/apply",        tags=["apply"])
app.include_router(competitions.router, prefix="/api/v1/competitions", tags=["competitions"])


@app.get("/healthz")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/api/v1/connection-info", dependencies=[Depends(require_api_key)])
async def connection_info() -> dict[str, str]:
    """Return info about the backend machine the phone is connected to."""
    hostname = socket.gethostname()
    try:
        local_ip = socket.gethostbyname(hostname)
    except socket.gaierror:
        local_ip = "unknown"
    return {
        "hostname": hostname,
        "local_ip": local_ip,
        "os": f"{platform.system()} {platform.release()}",
        "user": os.environ.get("USERNAME") or os.environ.get("USER") or "unknown",
        "python_version": platform.python_version(),
    }


@app.get("/api/v1/scheduler", dependencies=[Depends(require_api_key)])
async def scheduler_status() -> dict:
    """Return current scheduler status."""
    from src import scheduler as sched
    return {
        "enabled": cfg.scheduler.enabled,
        "run_at": cfg.scheduler.run_at,
        "running": sched._task is not None and not sched._task.done(),
    }


@app.post("/api/v1/scheduler/run-now", dependencies=[Depends(require_api_key)])
async def scheduler_run_now(bg: BackgroundTasks) -> dict:
    """Trigger an immediate pipeline run (scrape + match + competitions)."""
    from src import scheduler as sched
    bg.add_task(sched._run_pipeline_once)
    return {"message": "Pipeline triggered"}

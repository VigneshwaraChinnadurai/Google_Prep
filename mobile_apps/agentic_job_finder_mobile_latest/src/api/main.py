import os
import platform
import socket
from fastapi import FastAPI, Depends
from fastapi.middleware.cors import CORSMiddleware
from src.config import load_settings
from src.api.deps import require_api_key
from src.api.routes import resume, pipeline, matches, apply, competitions

cfg = load_settings()

app = FastAPI(title="Job Finder API", version="0.2.0")

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

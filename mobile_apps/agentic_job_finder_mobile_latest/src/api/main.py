from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from src.config import load_settings
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

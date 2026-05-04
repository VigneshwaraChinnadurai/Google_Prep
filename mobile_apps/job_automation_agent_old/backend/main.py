"""
FastAPI main application for Job Automation Agent.
"""
import logging
from contextlib import asynccontextmanager
from datetime import datetime

from fastapi import FastAPI, HTTPException, Depends
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session

from config import get_settings
from database.database import get_db, init_db
from services.scheduler import get_scheduler

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

settings = get_settings()


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan management."""
    # Startup
    logger.info("Starting Job Automation Agent...")
    
    # Initialize database
    init_db()
    logger.info("Database initialized")
    
    # Start scheduler (but don't setup agents yet - let manual setup happen)
    scheduler = get_scheduler()
    scheduler.start()
    logger.info("Scheduler started")
    
    yield
    
    # Shutdown
    logger.info("Shutting down...")
    scheduler.stop()


# Create FastAPI app
app = FastAPI(
    title="Job Automation Agent API",
    description="AI-powered job application automation system",
    version="1.0.0",
    lifespan=lifespan
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # In production, specify allowed origins
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# Import and include routers
from api.routes import profile, jobs, applications, companies, chat, agents as agents_router

app.include_router(profile.router, prefix="/api/profile", tags=["Profile"])
app.include_router(jobs.router, prefix="/api/jobs", tags=["Jobs"])
app.include_router(applications.router, prefix="/api/applications", tags=["Applications"])
app.include_router(companies.router, prefix="/api/companies", tags=["Companies"])
app.include_router(chat.router, prefix="/api/chat", tags=["Chat"])
app.include_router(agents_router.router, prefix="/api/agents", tags=["Agents"])


@app.get("/")
async def root():
    """Root endpoint."""
    return {
        "name": settings.app_name,
        "version": settings.app_version,
        "status": "running"
    }


@app.get("/api/status")
async def get_status(db: Session = Depends(get_db)):
    """Get system status and statistics."""
    from database import crud
    from database.models import Job, Application, Company, JobStatus
    from sqlalchemy import func
    
    # Get counts
    total_jobs = db.query(func.count(Job.id)).scalar() or 0
    new_jobs = db.query(func.count(Job.id)).filter(Job.status == JobStatus.NEW).scalar() or 0
    analyzed_jobs = db.query(func.count(Job.id)).filter(Job.status == JobStatus.ANALYZED).scalar() or 0
    total_applications = db.query(func.count(Application.id)).scalar() or 0
    total_companies = db.query(func.count(Company.id)).scalar() or 0
    
    # Get application stats
    app_stats = crud.get_application_stats(db)
    
    # Get scheduler status
    scheduler = get_scheduler()
    scheduler_status = scheduler.get_all_jobs_status()
    
    # Get API cost info
    try:
        from services.gemini_client import get_gemini_client
        cost_stats = get_gemini_client().get_cost_stats()
    except:
        cost_stats = {}
    
    return {
        "status": "running",
        "timestamp": datetime.utcnow().isoformat(),
        "database": {
            "jobs": {
                "total": total_jobs,
                "new": new_jobs,
                "analyzed": analyzed_jobs
            },
            "applications": {
                "total": total_applications,
                "by_status": app_stats
            },
            "companies": total_companies
        },
        "scheduler": scheduler_status,
        "api_cost": cost_stats
    }


@app.get("/api/health")
async def health_check():
    """Health check endpoint."""
    return {"status": "healthy", "timestamp": datetime.utcnow().isoformat()}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "main:app",
        host=settings.host,
        port=settings.port,
        reload=settings.debug
    )

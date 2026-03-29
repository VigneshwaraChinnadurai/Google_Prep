"""
Jobs API routes.
"""
from typing import Optional, List
from fastapi import APIRouter, HTTPException, Depends, Query
from pydantic import BaseModel
from sqlalchemy.orm import Session

from database.database import get_db
from database import crud
from database.models import JobStatus

router = APIRouter()


class JobFilter(BaseModel):
    """Job filter parameters."""
    status: Optional[str] = None
    company_id: Optional[int] = None
    min_score: Optional[float] = None
    search: Optional[str] = None


@router.get("")
async def list_jobs(
    skip: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=100),
    status: Optional[str] = None,
    company_id: Optional[int] = None,
    min_score: Optional[float] = None,
    search: Optional[str] = None,
    db: Session = Depends(get_db)
):
    """List jobs with filtering."""
    # Map status string to enum
    status_enum = None
    if status:
        try:
            status_enum = JobStatus(status)
        except ValueError:
            raise HTTPException(status_code=400, detail=f"Invalid status: {status}")
    
    jobs = crud.get_jobs(
        db,
        skip=skip,
        limit=limit,
        status=status_enum,
        company_id=company_id,
        min_score=min_score,
        search_term=search
    )
    
    return {
        "jobs": [job.to_dict() for job in jobs],
        "count": len(jobs),
        "skip": skip,
        "limit": limit
    }


@router.get("/new")
async def list_new_jobs(
    limit: int = Query(50, ge=1, le=100),
    db: Session = Depends(get_db)
):
    """List jobs awaiting analysis."""
    jobs = crud.get_new_jobs(db, limit=limit)
    return {
        "jobs": [job.to_dict() for job in jobs],
        "count": len(jobs)
    }


@router.get("/ready")
async def list_ready_jobs(
    min_score: float = Query(60.0, ge=0, le=100),
    db: Session = Depends(get_db)
):
    """List jobs ready for application."""
    jobs = crud.get_ready_to_apply_jobs(db, min_score=min_score)
    return {
        "jobs": [job.to_dict() for job in jobs],
        "count": len(jobs)
    }


@router.get("/{job_id}")
async def get_job(job_id: int, db: Session = Depends(get_db)):
    """Get job details by ID."""
    job = crud.get_job(db, job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Job not found")
    
    # Include full details
    result = job.to_dict()
    result["description_full"] = job.description
    result["match_justification_full"] = job.match_justification
    
    # Include associated application if exists
    if job.application:
        result["application"] = job.application.to_dict()
    
    return result


@router.post("/{job_id}/analyze")
async def analyze_job(job_id: int, db: Session = Depends(get_db)):
    """Trigger analysis for a specific job."""
    job = crud.get_job(db, job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Job not found")
    
    from agents.analyst_agent import AnalystAgent
    import asyncio
    
    agent = AnalystAgent()
    result = asyncio.get_event_loop().run_until_complete(
        agent.analyze_single_job(job_id)
    )
    
    return result


@router.post("/{job_id}/apply")
async def apply_to_job(job_id: int, db: Session = Depends(get_db)):
    """Trigger application for a specific job."""
    job = crud.get_job(db, job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Job not found")
    
    # Check if already applied
    existing_app = crud.get_application_by_job(db, job_id)
    if existing_app:
        raise HTTPException(status_code=400, detail="Already applied to this job")
    
    from agents.applicant_agent import ApplicantAgent
    import asyncio
    
    agent = ApplicantAgent()
    result = asyncio.get_event_loop().run_until_complete(
        agent.apply_to_single_job(job_id)
    )
    
    return result


@router.put("/{job_id}/status")
async def update_job_status(
    job_id: int,
    status: str,
    db: Session = Depends(get_db)
):
    """Update job status."""
    job = crud.get_job(db, job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Job not found")
    
    try:
        status_enum = JobStatus(status)
    except ValueError:
        raise HTTPException(status_code=400, detail=f"Invalid status: {status}")
    
    updated = crud.update_job_status(db, job_id, status_enum)
    return updated.to_dict()


@router.post("/{job_id}/customize-resume")
async def customize_resume_for_job(job_id: int, db: Session = Depends(get_db)):
    """Generate ATS-optimized resume for a job."""
    job = crud.get_job(db, job_id)
    if not job:
        raise HTTPException(status_code=404, detail="Job not found")
    
    profile = crud.get_profile(db)
    if not profile:
        raise HTTPException(status_code=404, detail="Profile not found")
    
    if not job.description:
        raise HTTPException(status_code=400, detail="Job has no description")
    
    from services.gemini_client import get_gemini_client
    
    client = get_gemini_client()
    result = client.customize_resume(
        profile_json=profile.to_dict(),
        job_description=job.description
    )
    
    if result.get("parsed"):
        # Save customized resume to job
        crud.update_job(db, job_id, {
            "customized_resume": str(result["parsed"])
        })
    
    return result.get("parsed", {"error": "Failed to customize resume"})

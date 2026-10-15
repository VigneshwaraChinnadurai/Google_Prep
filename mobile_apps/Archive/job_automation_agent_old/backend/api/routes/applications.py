"""
Applications API routes.
"""
from typing import Optional, List
from datetime import datetime
from fastapi import APIRouter, HTTPException, Depends, Query
from pydantic import BaseModel
from sqlalchemy.orm import Session

from database.database import get_db
from database import crud
from database.models import ApplicationStatus

router = APIRouter()


class ApplicationUpdate(BaseModel):
    """Application update model."""
    status: Optional[str] = None
    notes: Optional[str] = None
    interview_notes: Optional[str] = None


@router.get("")
async def list_applications(
    skip: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=100),
    status: Optional[str] = None,
    db: Session = Depends(get_db)
):
    """List all applications."""
    status_enum = None
    if status:
        try:
            status_enum = ApplicationStatus(status)
        except ValueError:
            raise HTTPException(status_code=400, detail=f"Invalid status: {status}")
    
    applications = crud.get_applications(db, skip=skip, limit=limit, status=status_enum)
    
    # Enrich with job details
    results = []
    for app in applications:
        app_dict = app.to_dict()
        job = crud.get_job(db, app.job_id)
        if job:
            app_dict["job"] = {
                "id": job.id,
                "title": job.title,
                "company_name": job.company_name,
                "location": job.location,
                "match_score": job.match_score,
                "url": job.url
            }
        results.append(app_dict)
    
    return {
        "applications": results,
        "count": len(results),
        "skip": skip,
        "limit": limit
    }


@router.get("/stats")
async def get_application_stats(db: Session = Depends(get_db)):
    """Get application statistics."""
    stats = crud.get_application_stats(db)
    
    # Calculate additional metrics
    total = sum(stats.values())
    
    return {
        "by_status": stats,
        "total": total,
        "active": stats.get("SUBMITTED", 0) + stats.get("UNDER_REVIEW", 0) + stats.get("INTERVIEW_SCHEDULED", 0),
        "successful": stats.get("OFFER_RECEIVED", 0),
        "rejected": stats.get("REJECTED", 0)
    }


@router.get("/{application_id}")
async def get_application(application_id: int, db: Session = Depends(get_db)):
    """Get application details."""
    application = crud.get_application(db, application_id)
    if not application:
        raise HTTPException(status_code=404, detail="Application not found")
    
    result = application.to_dict()
    
    # Include full job details
    job = crud.get_job(db, application.job_id)
    if job:
        result["job"] = job.to_dict()
        result["job"]["description_full"] = job.description
        result["job"]["match_justification_full"] = job.match_justification
    
    # Include related emails
    from database.models import Email
    emails = db.query(Email).filter(Email.application_id == application_id).all()
    result["emails"] = [e.to_dict() for e in emails]
    
    # Include interview prep if exists
    from database.models import InterviewPrep
    prep = db.query(InterviewPrep).filter(
        InterviewPrep.application_id == application_id
    ).first()
    if prep:
        result["interview_prep"] = {
            "company_research": prep.company_research,
            "role_analysis": prep.role_analysis,
            "behavioral_questions": prep.behavioral_questions,
            "technical_questions": prep.technical_questions,
            "questions_for_interviewer": prep.questions_for_interviewer,
            "talking_points": prep.talking_points
        }
    
    return result


@router.put("/{application_id}")
async def update_application(
    application_id: int,
    update: ApplicationUpdate,
    db: Session = Depends(get_db)
):
    """Update application details."""
    application = crud.get_application(db, application_id)
    if not application:
        raise HTTPException(status_code=404, detail="Application not found")
    
    if update.status:
        try:
            status_enum = ApplicationStatus(update.status)
            crud.update_application_status(db, application_id, status_enum, "manual")
        except ValueError:
            raise HTTPException(status_code=400, detail=f"Invalid status: {update.status}")
    
    if update.notes:
        application.notes = update.notes
    
    if update.interview_notes:
        application.interview_notes = update.interview_notes
    
    db.commit()
    db.refresh(application)
    
    return application.to_dict()


@router.post("/{application_id}/generate-prep")
async def generate_interview_prep(
    application_id: int,
    db: Session = Depends(get_db)
):
    """Generate interview preparation document."""
    application = crud.get_application(db, application_id)
    if not application:
        raise HTTPException(status_code=404, detail="Application not found")
    
    job = crud.get_job(db, application.job_id)
    profile = crud.get_profile(db)
    
    if not job or not profile:
        raise HTTPException(status_code=400, detail="Missing job or profile data")
    
    from services.gemini_client import get_gemini_client
    
    client = get_gemini_client()
    result = client.generate_interview_prep(
        profile_json=profile.to_dict(),
        job_description=job.description or ""
    )
    
    if result.get("parsed"):
        from database.models import InterviewPrep
        
        prep_data = result["parsed"]
        
        # Check if prep already exists
        existing = db.query(InterviewPrep).filter(
            InterviewPrep.application_id == application_id
        ).first()
        
        if existing:
            # Update existing
            existing.company_research = str(prep_data.get("company_research", {}))
            existing.role_analysis = str(prep_data.get("role_analysis", {}))
            existing.behavioral_questions = prep_data.get("behavioral_questions", [])
            existing.technical_questions = prep_data.get("technical_questions", [])
            existing.questions_for_interviewer = prep_data.get("questions_to_ask", [])
            existing.talking_points = prep_data.get("talking_points", [])
            existing.weakness_mitigation = prep_data.get("weakness_mitigation", {})
            existing.updated_at = datetime.utcnow()
        else:
            # Create new
            prep = InterviewPrep(
                application_id=application_id,
                company_research=str(prep_data.get("company_research", {})),
                role_analysis=str(prep_data.get("role_analysis", {})),
                behavioral_questions=prep_data.get("behavioral_questions", []),
                technical_questions=prep_data.get("technical_questions", []),
                questions_for_interviewer=prep_data.get("questions_to_ask", []),
                talking_points=prep_data.get("talking_points", []),
                weakness_mitigation=prep_data.get("weakness_mitigation", {})
            )
            db.add(prep)
        
        db.commit()
        return prep_data
    
    raise HTTPException(status_code=500, detail="Failed to generate interview prep")


@router.delete("/{application_id}")
async def withdraw_application(
    application_id: int,
    db: Session = Depends(get_db)
):
    """Mark application as withdrawn."""
    application = crud.get_application(db, application_id)
    if not application:
        raise HTTPException(status_code=404, detail="Application not found")
    
    crud.update_application_status(db, application_id, ApplicationStatus.WITHDRAWN, "manual")
    
    return {"message": "Application withdrawn", "id": application_id}

"""
Chat API routes for conversational interface.
"""
from typing import Optional, List, Dict, Any
from datetime import datetime
from fastapi import APIRouter, HTTPException, Depends
from pydantic import BaseModel
from sqlalchemy.orm import Session

from database.database import get_db
from database import crud
from services.gemini_client import get_gemini_client

router = APIRouter()


class ChatMessage(BaseModel):
    """Chat message model."""
    message: str
    context: Optional[Dict[str, Any]] = None


class ChatResponse(BaseModel):
    """Chat response model."""
    response: str
    context_used: bool = False
    cost: float = 0.0


@router.post("")
async def chat(
    message: ChatMessage,
    db: Session = Depends(get_db)
):
    """
    Chat with the job search assistant.
    
    The assistant can answer questions about:
    - Job search status
    - Application statistics
    - Specific companies or roles
    - Interview preparation
    - Skill improvement suggestions
    """
    client = get_gemini_client()
    
    # Build context from database
    context_parts = []
    
    # Get profile summary
    profile = crud.get_profile(db)
    if profile:
        context_parts.append(f"User: {profile.full_name}")
        if profile.desired_roles:
            context_parts.append(f"Target roles: {', '.join(profile.desired_roles)}")
    
    # Get application stats
    stats = crud.get_application_stats(db)
    if stats:
        context_parts.append(f"Applications: {sum(stats.values())} total")
        context_parts.append(f"By status: {stats}")
    
    # Get recent activity
    from database.models import Job, Application
    from sqlalchemy import func
    
    new_jobs = db.query(func.count(Job.id)).filter(Job.status == "NEW").scalar() or 0
    context_parts.append(f"New jobs to analyze: {new_jobs}")
    
    # Add any provided context
    if message.context:
        context_parts.append(f"Additional context: {message.context}")
    
    context = "\n".join(context_parts) if context_parts else None
    
    # Generate response
    result = client.chat(message.message, context)
    
    return {
        "response": result["content"],
        "context_used": bool(context),
        "cost": result.get("cost", 0),
        "timestamp": datetime.utcnow().isoformat()
    }


@router.post("/status")
async def get_status_summary(db: Session = Depends(get_db)):
    """Get a natural language summary of job search status."""
    client = get_gemini_client()
    
    # Gather data
    stats = crud.get_application_stats(db)
    profile = crud.get_profile(db)
    
    from database.models import Job, Application
    from sqlalchemy import func
    
    total_jobs = db.query(func.count(Job.id)).scalar() or 0
    new_jobs = db.query(func.count(Job.id)).filter(Job.status == "NEW").scalar() or 0
    
    # Get top matches
    ready_jobs = crud.get_ready_to_apply_jobs(db, min_score=70)[:5]
    top_matches = [{"title": j.title, "company": j.company_name, "score": j.match_score} for j in ready_jobs]
    
    # Build prompt
    data = {
        "profile_name": profile.full_name if profile else "Unknown",
        "target_roles": profile.desired_roles if profile else [],
        "total_jobs_found": total_jobs,
        "new_jobs_pending": new_jobs,
        "application_stats": stats,
        "top_matches": top_matches
    }
    
    prompt = f"""Based on this job search data, provide a brief, encouraging status summary:

Data: {data}

Write a 2-3 sentence summary that:
1. Highlights key statistics
2. Notes any notable opportunities (top matches)
3. Suggests a next action

Be concise and actionable."""
    
    result = client.generate(prompt, temperature=0.7, max_tokens=200)
    
    return {
        "summary": result["content"],
        "data": data,
        "timestamp": datetime.utcnow().isoformat()
    }


@router.post("/analyze-trends")
async def analyze_trends(db: Session = Depends(get_db)):
    """Analyze job search trends and patterns."""
    client = get_gemini_client()
    
    # Get skill gaps
    skill_gaps = crud.get_top_skill_gaps(db, limit=10)
    gaps_data = [{"skill": g.skill_name, "frequency": g.frequency} for g in skill_gaps]
    
    # Get company types distribution
    from database.models import Company, Job
    from sqlalchemy import func
    
    company_types = db.query(
        Company.company_type,
        func.count(Job.id)
    ).join(Job).group_by(Company.company_type).all()
    
    type_dist = {str(t): c for t, c in company_types}
    
    # Get application success rate by company type
    # (simplified - in production would be more detailed)
    
    prompt = f"""Analyze these job search patterns and provide insights:

Skill Gaps (skills frequently missing for target jobs):
{gaps_data}

Jobs by Company Type:
{type_dist}

Provide:
1. Key insight about skill gaps
2. Recommendation for which skills to prioritize
3. Any pattern worth noting

Be specific and actionable."""
    
    result = client.generate(prompt, temperature=0.5, max_tokens=300)
    
    return {
        "analysis": result["content"],
        "skill_gaps": gaps_data,
        "company_distribution": type_dist,
        "timestamp": datetime.utcnow().isoformat()
    }


@router.get("/cost")
async def get_api_cost():
    """Get current API cost statistics."""
    client = get_gemini_client()
    return client.get_cost_stats()

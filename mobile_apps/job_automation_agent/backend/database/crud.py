"""
CRUD operations for database models.
"""
from datetime import datetime
from typing import List, Optional, Dict, Any
from sqlalchemy.orm import Session
from sqlalchemy import func, and_, or_

from database.models import (
    Profile, Company, Job, Application, Email, AgentRun,
    InterviewPrep, SkillGap, JobStatus, ApplicationStatus,
    CompanyPreference, EmailClassification
)


# ==================== Profile CRUD ====================

def get_profile(db: Session) -> Optional[Profile]:
    """Get the user profile (assumes single user)."""
    return db.query(Profile).first()


def create_profile(db: Session, profile_data: Dict[str, Any]) -> Profile:
    """Create a new profile."""
    profile = Profile(**profile_data)
    db.add(profile)
    db.commit()
    db.refresh(profile)
    return profile


def update_profile(db: Session, profile_id: int, profile_data: Dict[str, Any]) -> Optional[Profile]:
    """Update an existing profile."""
    profile = db.query(Profile).filter(Profile.id == profile_id).first()
    if profile:
        for key, value in profile_data.items():
            if hasattr(profile, key):
                setattr(profile, key, value)
        profile.updated_at = datetime.utcnow()
        db.commit()
        db.refresh(profile)
    return profile


# ==================== Company CRUD ====================

def get_company(db: Session, company_id: int) -> Optional[Company]:
    """Get company by ID."""
    return db.query(Company).filter(Company.id == company_id).first()


def get_company_by_name(db: Session, name: str) -> Optional[Company]:
    """Get company by name (case-insensitive)."""
    normalized = name.lower().replace(" ", "")
    return db.query(Company).filter(Company.normalized_name == normalized).first()


def get_companies(
    db: Session,
    skip: int = 0,
    limit: int = 100,
    preference: Optional[CompanyPreference] = None,
    company_type: Optional[str] = None
) -> List[Company]:
    """Get companies with optional filtering."""
    query = db.query(Company)
    if preference:
        query = query.filter(Company.preference == preference)
    if company_type:
        query = query.filter(Company.company_type == company_type)
    return query.offset(skip).limit(limit).all()


def create_company(db: Session, company_data: Dict[str, Any]) -> Company:
    """Create a new company."""
    # Add normalized name
    company_data["normalized_name"] = company_data["name"].lower().replace(" ", "")
    company = Company(**company_data)
    db.add(company)
    db.commit()
    db.refresh(company)
    return company


def update_company_preference(
    db: Session,
    company_id: int,
    preference: CompanyPreference,
    notes: Optional[str] = None
) -> Optional[Company]:
    """Update company preference (allow/block)."""
    company = db.query(Company).filter(Company.id == company_id).first()
    if company:
        company.preference = preference
        if notes:
            company.preference_notes = notes
        company.updated_at = datetime.utcnow()
        db.commit()
        db.refresh(company)
    return company


def get_allowed_companies(db: Session) -> List[Company]:
    """Get all companies that are allowed (not blocked)."""
    return db.query(Company).filter(
        Company.preference != CompanyPreference.BLOCKED
    ).all()


def get_priority_companies(db: Session) -> List[Company]:
    """Get priority companies."""
    return db.query(Company).filter(
        Company.preference == CompanyPreference.PRIORITY
    ).all()


# ==================== Job CRUD ====================

def get_job(db: Session, job_id: int) -> Optional[Job]:
    """Get job by ID."""
    return db.query(Job).filter(Job.id == job_id).first()


def get_job_by_url(db: Session, url: str) -> Optional[Job]:
    """Get job by URL."""
    return db.query(Job).filter(Job.url == url).first()


def get_jobs(
    db: Session,
    skip: int = 0,
    limit: int = 100,
    status: Optional[JobStatus] = None,
    company_id: Optional[int] = None,
    min_score: Optional[float] = None,
    search_term: Optional[str] = None
) -> List[Job]:
    """Get jobs with filtering."""
    query = db.query(Job)
    
    if status:
        query = query.filter(Job.status == status)
    if company_id:
        query = query.filter(Job.company_id == company_id)
    if min_score is not None:
        query = query.filter(Job.match_score >= min_score)
    if search_term:
        query = query.filter(
            or_(
                Job.title.ilike(f"%{search_term}%"),
                Job.company_name.ilike(f"%{search_term}%")
            )
        )
    
    return query.order_by(Job.match_score.desc().nullsfirst()).offset(skip).limit(limit).all()


def get_new_jobs(db: Session, limit: int = 50) -> List[Job]:
    """Get jobs with NEW status for analysis."""
    return db.query(Job).filter(Job.status == JobStatus.NEW).limit(limit).all()


def get_ready_to_apply_jobs(db: Session, min_score: float = 60.0) -> List[Job]:
    """Get analyzed jobs ready for application."""
    return db.query(Job).filter(
        and_(
            Job.status == JobStatus.READY_TO_APPLY,
            Job.match_score >= min_score
        )
    ).order_by(Job.match_score.desc()).all()


def create_job(db: Session, job_data: Dict[str, Any]) -> Job:
    """Create a new job."""
    job = Job(**job_data)
    db.add(job)
    db.commit()
    db.refresh(job)
    return job


def update_job(db: Session, job_id: int, job_data: Dict[str, Any]) -> Optional[Job]:
    """Update a job."""
    job = db.query(Job).filter(Job.id == job_id).first()
    if job:
        for key, value in job_data.items():
            if hasattr(job, key):
                setattr(job, key, value)
        job.updated_at = datetime.utcnow()
        db.commit()
        db.refresh(job)
    return job


def update_job_analysis(
    db: Session,
    job_id: int,
    match_score: float,
    justification: Dict[str, Any]
) -> Optional[Job]:
    """Update job with analysis results."""
    job = db.query(Job).filter(Job.id == job_id).first()
    if job:
        job.match_score = match_score
        job.match_justification = justification
        job.analyzed_at = datetime.utcnow()
        job.status = JobStatus.ANALYZED if match_score < 60 else JobStatus.READY_TO_APPLY
        db.commit()
        db.refresh(job)
    return job


def update_job_status(db: Session, job_id: int, status: JobStatus) -> Optional[Job]:
    """Update job status."""
    job = db.query(Job).filter(Job.id == job_id).first()
    if job:
        job.status = status
        job.updated_at = datetime.utcnow()
        db.commit()
        db.refresh(job)
    return job


# ==================== Application CRUD ====================

def get_application(db: Session, application_id: int) -> Optional[Application]:
    """Get application by ID."""
    return db.query(Application).filter(Application.id == application_id).first()


def get_application_by_job(db: Session, job_id: int) -> Optional[Application]:
    """Get application for a specific job."""
    return db.query(Application).filter(Application.job_id == job_id).first()


def get_applications(
    db: Session,
    skip: int = 0,
    limit: int = 100,
    status: Optional[ApplicationStatus] = None
) -> List[Application]:
    """Get applications with optional status filter."""
    query = db.query(Application)
    if status:
        query = query.filter(Application.status == status)
    return query.order_by(Application.applied_at.desc()).offset(skip).limit(limit).all()


def create_application(db: Session, application_data: Dict[str, Any]) -> Application:
    """Create a new application."""
    # Initialize status history
    application_data["status_history"] = [{
        "status": ApplicationStatus.SUBMITTED.value,
        "timestamp": datetime.utcnow().isoformat(),
        "source": "application_created"
    }]
    
    application = Application(**application_data)
    db.add(application)
    db.commit()
    db.refresh(application)
    return application


def update_application_status(
    db: Session,
    application_id: int,
    status: ApplicationStatus,
    source: str = "manual"
) -> Optional[Application]:
    """Update application status with history tracking."""
    application = db.query(Application).filter(Application.id == application_id).first()
    if application:
        # Add to status history
        history_entry = {
            "status": status.value,
            "timestamp": datetime.utcnow().isoformat(),
            "source": source
        }
        if application.status_history:
            application.status_history = application.status_history + [history_entry]
        else:
            application.status_history = [history_entry]
        
        application.status = status
        application.status_updated_at = datetime.utcnow()
        db.commit()
        db.refresh(application)
    return application


def get_applications_for_tracking(db: Session) -> List[Application]:
    """Get active applications that need status tracking."""
    active_statuses = [
        ApplicationStatus.SUBMITTED,
        ApplicationStatus.CONFIRMED,
        ApplicationStatus.UNDER_REVIEW,
        ApplicationStatus.INTERVIEW_SCHEDULED,
        ApplicationStatus.INTERVIEWED
    ]
    return db.query(Application).filter(
        Application.status.in_(active_statuses)
    ).all()


def get_application_stats(db: Session) -> Dict[str, int]:
    """Get application statistics by status."""
    results = db.query(
        Application.status,
        func.count(Application.id)
    ).group_by(Application.status).all()
    
    return {status.value: count for status, count in results}


# ==================== Email CRUD ====================

def get_email_by_gmail_id(db: Session, gmail_id: str) -> Optional[Email]:
    """Get email by Gmail ID."""
    return db.query(Email).filter(Email.gmail_id == gmail_id).first()


def get_unprocessed_emails(db: Session) -> List[Email]:
    """Get emails that haven't been processed."""
    return db.query(Email).filter(Email.processed == False).all()


def create_email(db: Session, email_data: Dict[str, Any]) -> Email:
    """Create a new email record."""
    email = Email(**email_data)
    db.add(email)
    db.commit()
    db.refresh(email)
    return email


def update_email_classification(
    db: Session,
    email_id: int,
    classification: EmailClassification,
    confidence: float,
    application_id: Optional[int] = None
) -> Optional[Email]:
    """Update email with classification results."""
    email = db.query(Email).filter(Email.id == email_id).first()
    if email:
        email.classification = classification
        email.classification_confidence = confidence
        email.classified_at = datetime.utcnow()
        if application_id:
            email.application_id = application_id
        db.commit()
        db.refresh(email)
    return email


def mark_email_processed(
    db: Session,
    email_id: int,
    action_taken: str
) -> Optional[Email]:
    """Mark email as processed."""
    email = db.query(Email).filter(Email.id == email_id).first()
    if email:
        email.processed = True
        email.action_taken = action_taken
        email.updated_at = datetime.utcnow()
        db.commit()
        db.refresh(email)
    return email


# ==================== Agent Run CRUD ====================

def create_agent_run(db: Session, agent_name: str) -> AgentRun:
    """Create a new agent run record."""
    run = AgentRun(agent_name=agent_name, status="running")
    db.add(run)
    db.commit()
    db.refresh(run)
    return run


def complete_agent_run(
    db: Session,
    run_id: int,
    status: str,
    items_processed: int,
    items_succeeded: int,
    items_failed: int,
    api_calls: int = 0,
    tokens_used: int = 0,
    estimated_cost: float = 0.0,
    error_message: Optional[str] = None
) -> Optional[AgentRun]:
    """Complete an agent run with results."""
    run = db.query(AgentRun).filter(AgentRun.id == run_id).first()
    if run:
        run.completed_at = datetime.utcnow()
        run.status = status
        run.items_processed = items_processed
        run.items_succeeded = items_succeeded
        run.items_failed = items_failed
        run.api_calls = api_calls
        run.tokens_used = tokens_used
        run.estimated_cost = estimated_cost
        run.error_message = error_message
        db.commit()
        db.refresh(run)
    return run


def get_recent_agent_runs(
    db: Session,
    agent_name: Optional[str] = None,
    limit: int = 10
) -> List[AgentRun]:
    """Get recent agent runs."""
    query = db.query(AgentRun)
    if agent_name:
        query = query.filter(AgentRun.agent_name == agent_name)
    return query.order_by(AgentRun.started_at.desc()).limit(limit).all()


# ==================== Skill Gap CRUD ====================

def get_or_create_skill_gap(db: Session, skill_name: str, category: str) -> SkillGap:
    """Get existing skill gap or create new one."""
    gap = db.query(SkillGap).filter(
        SkillGap.skill_name.ilike(skill_name)
    ).first()
    
    if not gap:
        gap = SkillGap(skill_name=skill_name, skill_category=category)
        db.add(gap)
        db.commit()
        db.refresh(gap)
    
    return gap


def update_skill_gap_frequency(
    db: Session,
    skill_gap_id: int,
    job_id: int,
    importance_delta: float
) -> Optional[SkillGap]:
    """Update skill gap when encountered in another job."""
    gap = db.query(SkillGap).filter(SkillGap.id == skill_gap_id).first()
    if gap:
        gap.frequency += 1
        gap.importance_score = (gap.importance_score or 0) + importance_delta
        if gap.job_ids:
            if job_id not in gap.job_ids:
                gap.job_ids = gap.job_ids + [job_id]
        else:
            gap.job_ids = [job_id]
        db.commit()
        db.refresh(gap)
    return gap


def get_top_skill_gaps(db: Session, limit: int = 10) -> List[SkillGap]:
    """Get most important skill gaps."""
    return db.query(SkillGap).order_by(
        SkillGap.importance_score.desc().nullsfirst()
    ).limit(limit).all()

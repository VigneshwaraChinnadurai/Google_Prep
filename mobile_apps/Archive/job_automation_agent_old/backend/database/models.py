"""
SQLAlchemy database models for Job Automation Agent.
"""
from datetime import datetime
from typing import Optional, List
from sqlalchemy import (
    Column, Integer, String, Text, Float, Boolean, DateTime, 
    ForeignKey, JSON, Enum as SQLEnum, Index, UniqueConstraint
)
from sqlalchemy.orm import relationship
from sqlalchemy.ext.declarative import declarative_base
import enum

Base = declarative_base()


# Enums
class JobStatus(str, enum.Enum):
    NEW = "NEW"
    ANALYZED = "ANALYZED"
    READY_TO_APPLY = "READY_TO_APPLY"
    APPLYING = "APPLYING"
    APPLIED = "APPLIED"
    SKIPPED = "SKIPPED"
    ERROR = "ERROR"


class ApplicationStatus(str, enum.Enum):
    SUBMITTED = "SUBMITTED"
    CONFIRMED = "CONFIRMED"
    UNDER_REVIEW = "UNDER_REVIEW"
    INTERVIEW_SCHEDULED = "INTERVIEW_SCHEDULED"
    INTERVIEWED = "INTERVIEWED"
    OFFER_RECEIVED = "OFFER_RECEIVED"
    REJECTED = "REJECTED"
    WITHDRAWN = "WITHDRAWN"
    NO_RESPONSE = "NO_RESPONSE"


class CompanyType(str, enum.Enum):
    MAANG = "MAANG"
    FAANG = "FAANG"
    PRODUCT = "PRODUCT"
    STARTUP = "STARTUP"
    CONSULTING = "CONSULTING"
    FINANCE = "FINANCE"
    HEALTHCARE = "HEALTHCARE"
    GOVERNMENT = "GOVERNMENT"
    OTHER = "OTHER"


class CompanyPreference(str, enum.Enum):
    ALLOWED = "ALLOWED"
    BLOCKED = "BLOCKED"
    NEUTRAL = "NEUTRAL"
    PRIORITY = "PRIORITY"


class EmailClassification(str, enum.Enum):
    APPLICATION_CONFIRMATION = "APPLICATION_CONFIRMATION"
    INTERVIEW_REQUEST = "INTERVIEW_REQUEST"
    REJECTION = "REJECTION"
    OFFER = "OFFER"
    FOLLOWUP_REQUEST = "FOLLOWUP_REQUEST"
    ASSESSMENT_REQUEST = "ASSESSMENT_REQUEST"
    GENERAL = "GENERAL"
    SPAM = "SPAM"


# Models
class Profile(Base):
    """User profile containing structured resume data."""
    __tablename__ = "profiles"
    
    id = Column(Integer, primary_key=True, index=True)
    
    # Basic info
    full_name = Column(String(255), nullable=False)
    email = Column(String(255), nullable=False, unique=True)
    phone = Column(String(50))
    location = Column(String(255))
    linkedin_url = Column(String(500))
    github_url = Column(String(500))
    portfolio_url = Column(String(500))
    
    # Professional summary
    summary = Column(Text)
    
    # Structured data as JSON
    skills = Column(JSON, default=dict)  # {"programming": [], "gen_ai": [], ...}
    experience = Column(JSON, default=list)  # List of experience objects
    education = Column(JSON, default=list)  # List of education objects
    projects = Column(JSON, default=list)  # List of project objects
    certifications = Column(JSON, default=list)  # List of certification objects
    
    # Preferences
    desired_roles = Column(JSON, default=list)  # ["Data Scientist", "ML Engineer"]
    desired_locations = Column(JSON, default=list)  # ["San Francisco", "Remote"]
    min_salary = Column(Integer)  # Minimum expected salary
    max_salary = Column(Integer)
    
    # Resume files
    resume_pdf_path = Column(String(500))
    resume_text = Column(Text)  # Plain text version for analysis
    
    # Timestamps
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    
    def to_dict(self) -> dict:
        """Convert profile to dictionary for API responses."""
        return {
            "id": self.id,
            "full_name": self.full_name,
            "email": self.email,
            "phone": self.phone,
            "location": self.location,
            "linkedin_url": self.linkedin_url,
            "github_url": self.github_url,
            "portfolio_url": self.portfolio_url,
            "summary": self.summary,
            "skills": self.skills,
            "experience": self.experience,
            "education": self.education,
            "projects": self.projects,
            "certifications": self.certifications,
            "desired_roles": self.desired_roles,
            "desired_locations": self.desired_locations,
            "min_salary": self.min_salary,
            "max_salary": self.max_salary,
            "resume_pdf_path": self.resume_pdf_path,
            "created_at": self.created_at.isoformat() if self.created_at else None,
            "updated_at": self.updated_at.isoformat() if self.updated_at else None
        }


class Company(Base):
    """Company information and user preferences."""
    __tablename__ = "companies"
    
    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(255), nullable=False, unique=True, index=True)
    normalized_name = Column(String(255), index=True)  # Lowercase, no spaces
    
    # Company info
    company_type = Column(SQLEnum(CompanyType), default=CompanyType.OTHER)
    industry = Column(String(255))
    size = Column(String(100))  # "1-50", "51-200", "201-500", etc.
    website = Column(String(500))
    careers_page = Column(String(500))
    linkedin_url = Column(String(500))
    glassdoor_url = Column(String(500))
    
    # Location
    headquarters = Column(String(255))
    locations = Column(JSON, default=list)  # List of office locations
    
    # User preference
    preference = Column(SQLEnum(CompanyPreference), default=CompanyPreference.NEUTRAL)
    preference_notes = Column(Text)  # Why blocked/priority
    
    # Metadata
    logo_url = Column(String(500))
    description = Column(Text)
    
    # Auto-discovered fields
    avg_salary_range = Column(JSON)  # {"min": 100000, "max": 200000}
    glassdoor_rating = Column(Float)
    interview_difficulty = Column(Float)  # 1-5 scale
    
    # Timestamps
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    
    # Relationships
    jobs = relationship("Job", back_populates="company")
    
    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "name": self.name,
            "company_type": self.company_type.value if self.company_type else None,
            "industry": self.industry,
            "size": self.size,
            "website": self.website,
            "careers_page": self.careers_page,
            "preference": self.preference.value if self.preference else None,
            "preference_notes": self.preference_notes,
            "headquarters": self.headquarters,
            "glassdoor_rating": self.glassdoor_rating,
            "created_at": self.created_at.isoformat() if self.created_at else None
        }


class Job(Base):
    """Job posting information."""
    __tablename__ = "jobs"
    
    id = Column(Integer, primary_key=True, index=True)
    
    # Job identifiers
    external_id = Column(String(255))  # ID from job board
    source = Column(String(100))  # "linkedin", "greenhouse", "company_career"
    url = Column(String(1000), nullable=False)
    
    # Basic info
    title = Column(String(500), nullable=False, index=True)
    company_id = Column(Integer, ForeignKey("companies.id"), index=True)
    company_name = Column(String(255), index=True)  # Denormalized for quick access
    
    # Job details
    description = Column(Text)
    description_html = Column(Text)  # Original HTML
    requirements = Column(JSON, default=list)  # Extracted requirements
    responsibilities = Column(JSON, default=list)
    
    # Location & type
    location = Column(String(255))
    remote_type = Column(String(50))  # "remote", "hybrid", "onsite"
    job_type = Column(String(50))  # "full-time", "contract", "part-time"
    experience_level = Column(String(50))  # "entry", "mid", "senior", "lead"
    
    # Compensation
    salary_min = Column(Integer)
    salary_max = Column(Integer)
    salary_currency = Column(String(10), default="USD")
    
    # Application info
    apply_method = Column(String(50))  # "easy_apply", "external", "email"
    easy_apply_available = Column(Boolean, default=False)
    
    # Status tracking
    status = Column(SQLEnum(JobStatus), default=JobStatus.NEW, index=True)
    
    # AI Analysis results
    match_score = Column(Float)  # 0-100
    match_justification = Column(JSON)  # {"strengths": [], "weaknesses": []}
    analyzed_at = Column(DateTime)
    
    # Customized materials
    customized_resume = Column(Text)  # ATS-optimized version
    cover_letter = Column(Text)
    
    # Discovery metadata
    posted_date = Column(DateTime)
    discovered_at = Column(DateTime, default=datetime.utcnow)
    expires_at = Column(DateTime)
    
    # Timestamps
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    
    # Relationships
    company = relationship("Company", back_populates="jobs")
    application = relationship("Application", back_populates="job", uselist=False)
    
    # Indexes
    __table_args__ = (
        Index("ix_jobs_status_score", "status", "match_score"),
        UniqueConstraint("url", name="uq_jobs_url"),
    )
    
    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "external_id": self.external_id,
            "source": self.source,
            "url": self.url,
            "title": self.title,
            "company_id": self.company_id,
            "company_name": self.company_name,
            "description": self.description[:500] + "..." if self.description and len(self.description) > 500 else self.description,
            "location": self.location,
            "remote_type": self.remote_type,
            "job_type": self.job_type,
            "experience_level": self.experience_level,
            "salary_min": self.salary_min,
            "salary_max": self.salary_max,
            "easy_apply_available": self.easy_apply_available,
            "status": self.status.value if self.status else None,
            "match_score": self.match_score,
            "match_justification": self.match_justification,
            "analyzed_at": self.analyzed_at.isoformat() if self.analyzed_at else None,
            "posted_date": self.posted_date.isoformat() if self.posted_date else None,
            "discovered_at": self.discovered_at.isoformat() if self.discovered_at else None,
            "created_at": self.created_at.isoformat() if self.created_at else None
        }


class Application(Base):
    """Job application tracking."""
    __tablename__ = "applications"
    
    id = Column(Integer, primary_key=True, index=True)
    job_id = Column(Integer, ForeignKey("jobs.id"), unique=True, index=True)
    
    # Application details
    applied_at = Column(DateTime, default=datetime.utcnow)
    applied_via = Column(String(100))  # "easy_apply", "direct", "referral"
    
    # Status tracking
    status = Column(SQLEnum(ApplicationStatus), default=ApplicationStatus.SUBMITTED, index=True)
    status_updated_at = Column(DateTime, default=datetime.utcnow)
    status_history = Column(JSON, default=list)  # List of status changes with timestamps
    
    # Response tracking
    response_received = Column(Boolean, default=False)
    response_date = Column(DateTime)
    response_type = Column(String(100))  # "rejection", "interview", "offer"
    
    # Interview details
    interview_dates = Column(JSON, default=list)  # List of interview dates
    interview_notes = Column(Text)
    interview_prep_doc = Column(Text)  # Generated prep document
    
    # Offer details
    offer_salary = Column(Integer)
    offer_details = Column(JSON)
    offer_deadline = Column(DateTime)
    
    # Follow-up tracking
    follow_up_sent = Column(Boolean, default=False)
    follow_up_dates = Column(JSON, default=list)
    next_follow_up = Column(DateTime)
    
    # Notes
    notes = Column(Text)
    
    # Materials used
    resume_version = Column(String(255))  # Which resume version was used
    cover_letter_used = Column(Boolean, default=False)
    
    # Automation details
    auto_applied = Column(Boolean, default=False)
    application_log = Column(JSON, default=list)  # Detailed automation log
    error_message = Column(Text)  # If application failed
    
    # Timestamps
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    
    # Relationships
    job = relationship("Job", back_populates="application")
    emails = relationship("Email", back_populates="application")
    
    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "job_id": self.job_id,
            "applied_at": self.applied_at.isoformat() if self.applied_at else None,
            "applied_via": self.applied_via,
            "status": self.status.value if self.status else None,
            "status_updated_at": self.status_updated_at.isoformat() if self.status_updated_at else None,
            "status_history": self.status_history,
            "response_received": self.response_received,
            "interview_dates": self.interview_dates,
            "offer_salary": self.offer_salary,
            "auto_applied": self.auto_applied,
            "notes": self.notes,
            "created_at": self.created_at.isoformat() if self.created_at else None
        }


class Email(Base):
    """Email tracking for application responses."""
    __tablename__ = "emails"
    
    id = Column(Integer, primary_key=True, index=True)
    
    # Gmail identifiers
    gmail_id = Column(String(255), unique=True, index=True)
    thread_id = Column(String(255), index=True)
    
    # Email content
    subject = Column(String(1000))
    sender = Column(String(500))
    sender_email = Column(String(255))
    received_at = Column(DateTime)
    body_text = Column(Text)
    body_html = Column(Text)
    
    # Classification
    classification = Column(SQLEnum(EmailClassification))
    classification_confidence = Column(Float)  # 0-1
    classified_at = Column(DateTime)
    
    # Linking to application
    application_id = Column(Integer, ForeignKey("applications.id"), index=True)
    matched_company = Column(String(255))
    matched_job_title = Column(String(500))
    match_confidence = Column(Float)
    
    # Processing status
    processed = Column(Boolean, default=False)
    action_taken = Column(String(255))  # "status_updated", "interview_added", etc.
    
    # Timestamps
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    
    # Relationships
    application = relationship("Application", back_populates="emails")
    
    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "gmail_id": self.gmail_id,
            "subject": self.subject,
            "sender": self.sender,
            "sender_email": self.sender_email,
            "received_at": self.received_at.isoformat() if self.received_at else None,
            "classification": self.classification.value if self.classification else None,
            "classification_confidence": self.classification_confidence,
            "application_id": self.application_id,
            "processed": self.processed,
            "action_taken": self.action_taken
        }


class AgentRun(Base):
    """Track agent execution history."""
    __tablename__ = "agent_runs"
    
    id = Column(Integer, primary_key=True, index=True)
    
    agent_name = Column(String(100), nullable=False, index=True)
    started_at = Column(DateTime, default=datetime.utcnow)
    completed_at = Column(DateTime)
    
    # Execution details
    status = Column(String(50))  # "running", "completed", "failed"
    items_processed = Column(Integer, default=0)
    items_succeeded = Column(Integer, default=0)
    items_failed = Column(Integer, default=0)
    
    # Cost tracking
    api_calls = Column(Integer, default=0)
    tokens_used = Column(Integer, default=0)
    estimated_cost = Column(Float, default=0.0)
    
    # Logs
    log = Column(JSON, default=list)
    error_message = Column(Text)
    
    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "agent_name": self.agent_name,
            "started_at": self.started_at.isoformat() if self.started_at else None,
            "completed_at": self.completed_at.isoformat() if self.completed_at else None,
            "status": self.status,
            "items_processed": self.items_processed,
            "items_succeeded": self.items_succeeded,
            "items_failed": self.items_failed,
            "api_calls": self.api_calls,
            "tokens_used": self.tokens_used,
            "estimated_cost": self.estimated_cost
        }


class InterviewPrep(Base):
    """Interview preparation documents."""
    __tablename__ = "interview_preps"
    
    id = Column(Integer, primary_key=True, index=True)
    application_id = Column(Integer, ForeignKey("applications.id"), index=True)
    
    # Prep document
    company_research = Column(Text)
    role_analysis = Column(Text)
    common_questions = Column(JSON, default=list)  # List of Q&A
    behavioral_questions = Column(JSON, default=list)
    technical_questions = Column(JSON, default=list)
    questions_for_interviewer = Column(JSON, default=list)
    
    # Personalized prep
    talking_points = Column(JSON, default=list)  # Key points to mention
    weakness_mitigation = Column(JSON, default=dict)  # How to address weaknesses
    
    # Generated at
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class SkillGap(Base):
    """Skill gap analysis and improvement recommendations."""
    __tablename__ = "skill_gaps"
    
    id = Column(Integer, primary_key=True, index=True)
    
    skill_name = Column(String(255), nullable=False)
    skill_category = Column(String(100))  # "programming", "tools", "concepts"
    
    # Gap analysis
    frequency = Column(Integer, default=1)  # How often this gap appears
    importance_score = Column(Float)  # Weighted by job match scores
    
    # Jobs where this gap was identified
    job_ids = Column(JSON, default=list)
    
    # Recommendations
    learning_resources = Column(JSON, default=list)  # Courses, tutorials
    project_suggestions = Column(JSON, default=list)  # Practice projects
    estimated_time_to_learn = Column(String(100))  # "2 weeks", "1 month"
    
    # Progress tracking
    status = Column(String(50), default="not_started")  # "not_started", "in_progress", "completed"
    notes = Column(Text)
    
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

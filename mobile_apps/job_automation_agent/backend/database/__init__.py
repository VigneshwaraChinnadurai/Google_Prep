"""
Database module initialization.
"""
from database.database import (
    Base,
    engine,
    SessionLocal,
    get_db,
    get_db_context,
    init_db,
    drop_db
)
from database.models import (
    Profile,
    Company,
    Job,
    Application,
    Email,
    AgentRun,
    InterviewPrep,
    SkillGap,
    JobStatus,
    ApplicationStatus,
    CompanyPreference,
    CompanyType,
    EmailClassification
)

__all__ = [
    # Database
    "Base",
    "engine",
    "SessionLocal",
    "get_db",
    "get_db_context",
    "init_db",
    "drop_db",
    # Models
    "Profile",
    "Company",
    "Job",
    "Application",
    "Email",
    "AgentRun",
    "InterviewPrep",
    "SkillGap",
    # Enums
    "JobStatus",
    "ApplicationStatus",
    "CompanyPreference",
    "CompanyType",
    "EmailClassification"
]

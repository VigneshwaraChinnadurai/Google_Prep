"""
Configuration management for Job Automation Agent backend.
"""
import os
from typing import Optional
from pydantic import Field
from pydantic_settings import BaseSettings
from functools import lru_cache


class Settings(BaseSettings):
    """Application settings loaded from environment variables."""
    
    # Application
    app_name: str = "Job Automation Agent"
    app_version: str = "1.0.0"
    debug: bool = Field(default=False, description="Enable debug mode")
    
    # Database
    database_url: str = Field(
        default="sqlite:///./job_automation.db",
        description="Database connection URL (SQLite for development, PostgreSQL for production)"
    )
    
    # Gemini API
    gemini_api_key: str = Field(
        default="",
        description="Google Gemini API key"
    )
    gemini_model: str = Field(
        default="gemini-1.5-flash",
        description="Gemini model to use"
    )
    
    # Gmail OAuth2
    gmail_client_id: Optional[str] = Field(
        default=None,
        description="Gmail OAuth2 client ID"
    )
    gmail_client_secret: Optional[str] = Field(
        default=None,
        description="Gmail OAuth2 client secret"
    )
    gmail_redirect_uri: str = Field(
        default="http://localhost:8000/api/auth/gmail/callback",
        description="Gmail OAuth2 redirect URI"
    )
    
    # Scheduler intervals
    scout_interval_hours: int = Field(
        default=6,
        description="Hours between Scout Agent runs"
    )
    analyst_interval_minutes: int = Field(
        default=30,
        description="Minutes between Analyst Agent runs"
    )
    tracker_interval_minutes: int = Field(
        default=60,
        description="Minutes between Tracker Agent runs"
    )
    applicant_interval_minutes: int = Field(
        default=15,
        description="Minutes between Applicant Agent runs"
    )
    
    # Application thresholds
    min_match_score_for_auto_apply: int = Field(
        default=60,
        description="Minimum match score to auto-apply"
    )
    max_applications_per_day: int = Field(
        default=50,
        description="Maximum number of applications per day"
    )
    
    # Cost management
    max_daily_api_cost: float = Field(
        default=5.0,
        description="Maximum daily Gemini API cost in USD"
    )
    
    # Server
    host: str = Field(default="0.0.0.0", description="Server host")
    port: int = Field(default=8000, description="Server port")
    
    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"
        case_sensitive = False


@lru_cache()
def get_settings() -> Settings:
    """Get cached settings instance."""
    return Settings()


# Job status enum values
class JobStatus:
    NEW = "NEW"
    ANALYZED = "ANALYZED"
    READY_TO_APPLY = "READY_TO_APPLY"
    APPLYING = "APPLYING"
    APPLIED = "APPLIED"
    SKIPPED = "SKIPPED"
    ERROR = "ERROR"


# Application status enum values
class ApplicationStatus:
    SUBMITTED = "SUBMITTED"
    CONFIRMED = "CONFIRMED"
    UNDER_REVIEW = "UNDER_REVIEW"
    INTERVIEW_SCHEDULED = "INTERVIEW_SCHEDULED"
    INTERVIEWED = "INTERVIEWED"
    OFFER_RECEIVED = "OFFER_RECEIVED"
    REJECTED = "REJECTED"
    WITHDRAWN = "WITHDRAWN"
    NO_RESPONSE = "NO_RESPONSE"


# Company type enum values
class CompanyType:
    MAANG = "MAANG"
    FAANG = "FAANG"
    PRODUCT = "PRODUCT"
    STARTUP = "STARTUP"
    CONSULTING = "CONSULTING"
    FINANCE = "FINANCE"
    HEALTHCARE = "HEALTHCARE"
    GOVERNMENT = "GOVERNMENT"
    OTHER = "OTHER"


# Company preference enum values
class CompanyPreference:
    ALLOWED = "ALLOWED"
    BLOCKED = "BLOCKED"
    NEUTRAL = "NEUTRAL"
    PRIORITY = "PRIORITY"


# Email classification enum values
class EmailClassification:
    APPLICATION_CONFIRMATION = "APPLICATION_CONFIRMATION"
    INTERVIEW_REQUEST = "INTERVIEW_REQUEST"
    REJECTION = "REJECTION"
    OFFER = "OFFER"
    FOLLOWUP_REQUEST = "FOLLOWUP_REQUEST"
    ASSESSMENT_REQUEST = "ASSESSMENT_REQUEST"
    GENERAL = "GENERAL"
    SPAM = "SPAM"

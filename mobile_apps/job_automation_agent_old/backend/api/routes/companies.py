"""
Companies API routes.
"""
from typing import Optional, List
from fastapi import APIRouter, HTTPException, Depends, Query
from pydantic import BaseModel
from sqlalchemy.orm import Session

from database.database import get_db
from database import crud
from database.models import CompanyType, CompanyPreference

router = APIRouter()


class CompanyCreate(BaseModel):
    """Company creation model."""
    name: str
    company_type: Optional[str] = "OTHER"
    industry: Optional[str] = None
    website: Optional[str] = None
    careers_page: Optional[str] = None
    linkedin_url: Optional[str] = None
    headquarters: Optional[str] = None
    preference: Optional[str] = "NEUTRAL"
    preference_notes: Optional[str] = None


class CompanyUpdate(BaseModel):
    """Company update model."""
    company_type: Optional[str] = None
    industry: Optional[str] = None
    website: Optional[str] = None
    careers_page: Optional[str] = None
    linkedin_url: Optional[str] = None
    headquarters: Optional[str] = None
    preference_notes: Optional[str] = None


class PreferenceUpdate(BaseModel):
    """Company preference update model."""
    preference: str
    notes: Optional[str] = None


@router.get("")
async def list_companies(
    skip: int = Query(0, ge=0),
    limit: int = Query(100, ge=1, le=500),
    preference: Optional[str] = None,
    company_type: Optional[str] = None,
    db: Session = Depends(get_db)
):
    """List companies with filtering."""
    pref_enum = None
    if preference:
        try:
            pref_enum = CompanyPreference(preference)
        except ValueError:
            raise HTTPException(status_code=400, detail=f"Invalid preference: {preference}")
    
    companies = crud.get_companies(
        db,
        skip=skip,
        limit=limit,
        preference=pref_enum,
        company_type=company_type
    )
    
    return {
        "companies": [c.to_dict() for c in companies],
        "count": len(companies)
    }


@router.get("/priority")
async def list_priority_companies(db: Session = Depends(get_db)):
    """List priority companies."""
    companies = crud.get_priority_companies(db)
    return {
        "companies": [c.to_dict() for c in companies],
        "count": len(companies)
    }


@router.get("/allowed")
async def list_allowed_companies(db: Session = Depends(get_db)):
    """List allowed companies (not blocked)."""
    companies = crud.get_allowed_companies(db)
    return {
        "companies": [c.to_dict() for c in companies],
        "count": len(companies)
    }


@router.get("/{company_id}")
async def get_company(company_id: int, db: Session = Depends(get_db)):
    """Get company details."""
    company = crud.get_company(db, company_id)
    if not company:
        raise HTTPException(status_code=404, detail="Company not found")
    
    result = company.to_dict()
    
    # Include job count
    from database.models import Job
    from sqlalchemy import func
    job_count = db.query(func.count(Job.id)).filter(Job.company_id == company_id).scalar() or 0
    result["job_count"] = job_count
    
    return result


@router.post("")
async def create_company(
    company_data: CompanyCreate,
    db: Session = Depends(get_db)
):
    """Create a new company."""
    # Check if exists
    existing = crud.get_company_by_name(db, company_data.name)
    if existing:
        raise HTTPException(status_code=400, detail="Company already exists")
    
    # Validate enums
    try:
        c_type = CompanyType(company_data.company_type) if company_data.company_type else CompanyType.OTHER
        pref = CompanyPreference(company_data.preference) if company_data.preference else CompanyPreference.NEUTRAL
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    
    data = company_data.dict()
    data["company_type"] = c_type
    data["preference"] = pref
    
    company = crud.create_company(db, data)
    return company.to_dict()


@router.put("/{company_id}")
async def update_company(
    company_id: int,
    update: CompanyUpdate,
    db: Session = Depends(get_db)
):
    """Update company details."""
    company = crud.get_company(db, company_id)
    if not company:
        raise HTTPException(status_code=404, detail="Company not found")
    
    update_data = {k: v for k, v in update.dict().items() if v is not None}
    
    if "company_type" in update_data:
        try:
            update_data["company_type"] = CompanyType(update_data["company_type"])
        except ValueError:
            raise HTTPException(status_code=400, detail="Invalid company type")
    
    for key, value in update_data.items():
        setattr(company, key, value)
    
    db.commit()
    db.refresh(company)
    
    return company.to_dict()


@router.put("/{company_id}/preference")
async def update_company_preference(
    company_id: int,
    update: PreferenceUpdate,
    db: Session = Depends(get_db)
):
    """Update company preference (allow/block)."""
    try:
        pref = CompanyPreference(update.preference)
    except ValueError:
        raise HTTPException(status_code=400, detail=f"Invalid preference: {update.preference}")
    
    company = crud.update_company_preference(db, company_id, pref, update.notes)
    if not company:
        raise HTTPException(status_code=404, detail="Company not found")
    
    return company.to_dict()


@router.post("/bulk")
async def bulk_create_companies(
    companies: List[CompanyCreate],
    db: Session = Depends(get_db)
):
    """Bulk create companies."""
    created = []
    skipped = []
    
    for company_data in companies:
        existing = crud.get_company_by_name(db, company_data.name)
        if existing:
            skipped.append(company_data.name)
            continue
        
        try:
            c_type = CompanyType(company_data.company_type) if company_data.company_type else CompanyType.OTHER
            pref = CompanyPreference(company_data.preference) if company_data.preference else CompanyPreference.NEUTRAL
            
            data = company_data.dict()
            data["company_type"] = c_type
            data["preference"] = pref
            
            company = crud.create_company(db, data)
            created.append(company.name)
        except Exception as e:
            skipped.append(f"{company_data.name}: {str(e)}")
    
    return {
        "created": created,
        "skipped": skipped,
        "created_count": len(created),
        "skipped_count": len(skipped)
    }


# Seed data for target companies
DEFAULT_TARGET_COMPANIES = [
    {"name": "Google", "company_type": "MAANG", "careers_page": "https://careers.google.com", "preference": "PRIORITY"},
    {"name": "Meta", "company_type": "MAANG", "careers_page": "https://www.metacareers.com", "preference": "PRIORITY"},
    {"name": "Amazon", "company_type": "MAANG", "careers_page": "https://www.amazon.jobs", "preference": "PRIORITY"},
    {"name": "Apple", "company_type": "MAANG", "careers_page": "https://jobs.apple.com", "preference": "PRIORITY"},
    {"name": "Netflix", "company_type": "MAANG", "careers_page": "https://jobs.netflix.com", "preference": "PRIORITY"},
    {"name": "Microsoft", "company_type": "MAANG", "careers_page": "https://careers.microsoft.com", "preference": "PRIORITY"},
    {"name": "OpenAI", "company_type": "PRODUCT", "careers_page": "https://openai.com/careers", "preference": "PRIORITY"},
    {"name": "Anthropic", "company_type": "PRODUCT", "careers_page": "https://www.anthropic.com/careers", "preference": "PRIORITY"},
    {"name": "Stripe", "company_type": "PRODUCT", "careers_page": "https://stripe.com/jobs", "preference": "ALLOWED"},
    {"name": "Airbnb", "company_type": "PRODUCT", "careers_page": "https://careers.airbnb.com", "preference": "ALLOWED"},
]


@router.post("/seed-defaults")
async def seed_default_companies(db: Session = Depends(get_db)):
    """Seed database with default target companies."""
    created = []
    skipped = []
    
    for company_data in DEFAULT_TARGET_COMPANIES:
        existing = crud.get_company_by_name(db, company_data["name"])
        if existing:
            skipped.append(company_data["name"])
            continue
        
        company_data["company_type"] = CompanyType(company_data["company_type"])
        company_data["preference"] = CompanyPreference(company_data["preference"])
        
        company = crud.create_company(db, company_data)
        created.append(company.name)
    
    return {
        "message": "Default companies seeded",
        "created": created,
        "skipped": skipped
    }

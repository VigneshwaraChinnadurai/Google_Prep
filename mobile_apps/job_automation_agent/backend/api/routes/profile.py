"""
Profile management API routes.
"""
from typing import Optional, Dict, Any, List
from fastapi import APIRouter, HTTPException, Depends, UploadFile, File
from pydantic import BaseModel, EmailStr
from sqlalchemy.orm import Session

from database.database import get_db
from database import crud

router = APIRouter()


class ProfileUpdate(BaseModel):
    """Profile update request model."""
    full_name: Optional[str] = None
    email: Optional[EmailStr] = None
    phone: Optional[str] = None
    location: Optional[str] = None
    linkedin_url: Optional[str] = None
    github_url: Optional[str] = None
    portfolio_url: Optional[str] = None
    summary: Optional[str] = None
    skills: Optional[Dict[str, List[str]]] = None
    experience: Optional[List[Dict[str, Any]]] = None
    education: Optional[List[Dict[str, Any]]] = None
    projects: Optional[List[Dict[str, Any]]] = None
    certifications: Optional[List[Dict[str, Any]]] = None
    desired_roles: Optional[List[str]] = None
    desired_locations: Optional[List[str]] = None
    min_salary: Optional[int] = None
    max_salary: Optional[int] = None


class ProfileCreate(BaseModel):
    """Profile creation request model."""
    full_name: str
    email: EmailStr
    phone: Optional[str] = None
    location: Optional[str] = None
    linkedin_url: Optional[str] = None
    github_url: Optional[str] = None
    portfolio_url: Optional[str] = None
    summary: Optional[str] = None
    skills: Dict[str, List[str]] = {}
    experience: List[Dict[str, Any]] = []
    education: List[Dict[str, Any]] = []
    projects: List[Dict[str, Any]] = []
    certifications: List[Dict[str, Any]] = []
    desired_roles: List[str] = []
    desired_locations: List[str] = []
    min_salary: Optional[int] = None
    max_salary: Optional[int] = None


@router.get("")
async def get_profile(db: Session = Depends(get_db)):
    """Get user profile."""
    profile = crud.get_profile(db)
    if not profile:
        raise HTTPException(status_code=404, detail="Profile not found")
    return profile.to_dict()


@router.post("")
async def create_profile(
    profile_data: ProfileCreate,
    db: Session = Depends(get_db)
):
    """Create user profile."""
    existing = crud.get_profile(db)
    if existing:
        raise HTTPException(
            status_code=400,
            detail="Profile already exists. Use PUT to update."
        )
    
    profile = crud.create_profile(db, profile_data.dict())
    return profile.to_dict()


@router.put("")
async def update_profile(
    profile_data: ProfileUpdate,
    db: Session = Depends(get_db)
):
    """Update user profile."""
    profile = crud.get_profile(db)
    if not profile:
        raise HTTPException(status_code=404, detail="Profile not found")
    
    # Only update fields that are provided
    update_data = {k: v for k, v in profile_data.dict().items() if v is not None}
    
    updated = crud.update_profile(db, profile.id, update_data)
    return updated.to_dict()


@router.post("/upload-resume")
async def upload_resume(
    file: UploadFile = File(...),
    db: Session = Depends(get_db)
):
    """Upload resume PDF."""
    profile = crud.get_profile(db)
    if not profile:
        raise HTTPException(status_code=404, detail="Profile not found. Create profile first.")
    
    if not file.filename.endswith('.pdf'):
        raise HTTPException(status_code=400, detail="Only PDF files are supported")
    
    # Save file
    import os
    upload_dir = "uploads/resumes"
    os.makedirs(upload_dir, exist_ok=True)
    
    file_path = os.path.join(upload_dir, f"resume_{profile.id}.pdf")
    
    with open(file_path, "wb") as f:
        content = await file.read()
        f.write(content)
    
    # Extract text from PDF (basic implementation)
    try:
        import PyPDF2
        with open(file_path, "rb") as f:
            reader = PyPDF2.PdfReader(f)
            text = ""
            for page in reader.pages:
                text += page.extract_text() + "\n"
        
        crud.update_profile(db, profile.id, {
            "resume_pdf_path": file_path,
            "resume_text": text
        })
    except Exception as e:
        crud.update_profile(db, profile.id, {"resume_pdf_path": file_path})
    
    return {"message": "Resume uploaded successfully", "path": file_path}


@router.post("/import-json")
async def import_profile_json(
    profile_json: Dict[str, Any],
    db: Session = Depends(get_db)
):
    """Import profile from JSON file format."""
    existing = crud.get_profile(db)
    
    # Map JSON structure to database fields
    profile_data = {
        "full_name": profile_json.get("name", profile_json.get("full_name")),
        "email": profile_json.get("email"),
        "phone": profile_json.get("phone"),
        "location": profile_json.get("location"),
        "linkedin_url": profile_json.get("linkedin"),
        "github_url": profile_json.get("github"),
        "summary": profile_json.get("summary"),
        "skills": profile_json.get("skills", {}),
        "experience": profile_json.get("experience", []),
        "education": profile_json.get("education", []),
        "projects": profile_json.get("projects", []),
        "certifications": profile_json.get("certifications", []),
        "desired_roles": profile_json.get("desired_roles", profile_json.get("target_roles", [])),
        "desired_locations": profile_json.get("desired_locations", profile_json.get("preferred_locations", []))
    }
    
    # Remove None values
    profile_data = {k: v for k, v in profile_data.items() if v is not None}
    
    if existing:
        profile = crud.update_profile(db, existing.id, profile_data)
    else:
        profile = crud.create_profile(db, profile_data)
    
    return profile.to_dict()

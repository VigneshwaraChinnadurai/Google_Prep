"""
Profile management API routes.
"""
import os
import json
import logging
from typing import Optional, Dict, Any, List
from fastapi import APIRouter, HTTPException, Depends, UploadFile, File
from pydantic import BaseModel, EmailStr
from sqlalchemy.orm import Session
import PyPDF2

from database.database import get_db
from database import crud
from config import get_settings

logger = logging.getLogger(__name__)
router = APIRouter()
settings = get_settings()

# Default resume path
DEFAULT_RESUME_PATH = r"C:\Users\vichinnadurai\Documents\Vignesh\Personal Enrichment\Resume\Vigneshwara_Chinnadurai_Apr_2026.pdf"


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


def extract_text_from_pdf(pdf_path: str) -> str:
    """Extract text content from a PDF file."""
    text = ""
    with open(pdf_path, "rb") as f:
        reader = PyPDF2.PdfReader(f)
        for page in reader.pages:
            page_text = page.extract_text()
            if page_text:
                text += page_text + "\n"
    return text


def parse_resume_with_gemini(resume_text: str) -> Dict[str, Any]:
    """Use Gemini to parse resume text into structured profile data."""
    try:
        # Check if Gemini API key is configured
        if not settings.gemini_api_key:
            print("Gemini API key not configured, using basic parsing")
            return parse_resume_basic(resume_text)
            
        from services.gemini_client import GeminiClient
        client = GeminiClient()
        
        prompt = f"""Parse the following resume and extract structured information.
Return a JSON object with these fields:
- full_name: string
- email: string
- phone: string (if available)
- location: string (city, state/country)
- linkedin_url: string (if available)
- github_url: string (if available)
- summary: string (professional summary, 2-3 sentences)
- skills: object with categories as keys and arrays of skills as values, e.g. {{"programming": ["Python", "Java"], "frameworks": ["FastAPI", "React"]}}
- experience: array of objects with: title, company, start_date (YYYY-MM), end_date (YYYY-MM or "Present"), description
- education: array of objects with: degree, institution, graduation_year, gpa (if available)
- certifications: array of objects with: name, issuer, date (if available)
- desired_roles: array of job titles this person would be qualified for

Resume:
{resume_text}

Return ONLY valid JSON, no markdown formatting or explanation."""

        result = client.generate(
            prompt=prompt,
            temperature=0.3,
            max_tokens=4096,
            json_mode=True
        )
        
        content = result.get("content", "{}")
        # Clean up any markdown formatting
        if "```json" in content:
            content = content.split("```json")[1].split("```")[0]
        elif "```" in content:
            content = content.split("```")[1].split("```")[0]
        
        return json.loads(content.strip())
    except Exception as e:
        # Return basic parsed data if Gemini fails
        return parse_resume_basic(resume_text)


def parse_resume_basic(resume_text: str) -> Dict[str, Any]:
    """Basic resume parsing without LLM (fallback)."""
    import re
    
    # Try to extract email
    email_match = re.search(r'[\w\.-]+@[\w\.-]+\.\w+', resume_text)
    email = email_match.group(0) if email_match else None
    
    # Try to extract phone
    phone_match = re.search(r'[\+]?[(]?[0-9]{1,3}[)]?[-\s\.]?[0-9]{3}[-\s\.]?[0-9]{4,6}', resume_text)
    phone = phone_match.group(0) if phone_match else None
    
    # Try to extract LinkedIn
    linkedin_match = re.search(r'linkedin\.com/in/[\w-]+', resume_text, re.IGNORECASE)
    linkedin = f"https://{linkedin_match.group(0)}" if linkedin_match else None
    
    # Try to extract GitHub
    github_match = re.search(r'github\.com/[\w-]+', resume_text, re.IGNORECASE)
    github = f"https://{github_match.group(0)}" if github_match else None
    
    # Try to extract name (usually first line or near email)
    lines = resume_text.strip().split('\n')
    name = lines[0].strip() if lines else "Unknown"
    
    return {
        "full_name": name,
        "email": email,
        "phone": phone,
        "linkedin_url": linkedin,
        "github_url": github,
        "summary": resume_text[:500] if len(resume_text) > 500 else resume_text,
        "skills": {},
        "experience": [],
        "education": [],
        "certifications": [],
        "desired_roles": []
    }


@router.post("/load-from-resume")
async def load_profile_from_resume(
    pdf_path: Optional[str] = None,
    db: Session = Depends(get_db)
):
    """
    Load profile from a PDF resume file.
    Uses Gemini to parse the resume into structured profile data.
    
    If pdf_path is not provided, uses the default resume path.
    """
    logger.info("load_profile_from_resume endpoint called")
    
    # Use provided path or default
    resume_path = pdf_path or DEFAULT_RESUME_PATH
    logger.info(f"Using resume path: {resume_path}")
    
    # Check if file exists
    if not os.path.exists(resume_path):
        logger.error(f"Resume file not found: {resume_path}")
        raise HTTPException(
            status_code=404,
            detail=f"Resume file not found: {resume_path}"
        )
    
    # Extract text from PDF
    try:
        logger.info("Extracting text from PDF...")
        resume_text = extract_text_from_pdf(resume_path)
        logger.info(f"Extracted {len(resume_text)} characters from PDF")
        if not resume_text.strip():
            raise HTTPException(
                status_code=400,
                detail="Could not extract text from PDF"
            )
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error reading PDF: {str(e)}")
        raise HTTPException(
            status_code=500,
            detail=f"Error reading PDF: {str(e)}"
        )
    
    # Parse resume using Gemini or basic parser
    try:
        logger.info("Parsing resume...")
        profile_data = parse_resume_with_gemini(resume_text)
        logger.info("Resume parsed successfully")
    except Exception as e:
        logger.warning(f"Gemini parsing failed: {str(e)}, using basic parser")
        # Fallback to basic parsing
        profile_data = parse_resume_basic(resume_text)
    
    # Add resume path and text
    profile_data["resume_pdf_path"] = resume_path
    profile_data["resume_text"] = resume_text
    
    # Remove None values
    profile_data = {k: v for k, v in profile_data.items() if v is not None}
    
    # Create or update profile
    existing = crud.get_profile(db)
    if existing:
        profile = crud.update_profile(db, existing.id, profile_data)
    else:
        profile = crud.create_profile(db, profile_data)
    
    return {
        "message": "Profile loaded from resume successfully",
        "profile": profile.to_dict(),
        "resume_path": resume_path
    }


@router.get("/resume-text")
async def get_resume_text(db: Session = Depends(get_db)):
    """Get the raw text extracted from the resume."""
    profile = crud.get_profile(db)
    if not profile:
        raise HTTPException(status_code=404, detail="Profile not found")
    
    return {
        "resume_text": getattr(profile, 'resume_text', None),
        "resume_path": getattr(profile, 'resume_pdf_path', None)
    }

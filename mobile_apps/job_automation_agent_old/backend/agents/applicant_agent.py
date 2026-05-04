"""
Applicant Agent - Automates job applications.
"""
import asyncio
import logging
from datetime import datetime, date
from typing import Dict, Any, List, Optional

from playwright.async_api import async_playwright, Browser, Page

from agents.base_agent import BaseAgent
from database.database import get_db_context
from database import crud
from database.models import Job, JobStatus, ApplicationStatus
from services.gemini_client import get_gemini_client
from config import get_settings

logger = logging.getLogger(__name__)
settings = get_settings()


class ApplicationAutomator:
    """Handles automated job application submission."""
    
    def __init__(self):
        self.browser: Optional[Browser] = None
        self.page: Optional[Page] = None
    
    async def init_browser(self):
        """Initialize browser for automation."""
        playwright = await async_playwright().start()
        self.browser = await playwright.chromium.launch(
            headless=True,
            args=['--no-sandbox', '--disable-dev-shm-usage']
        )
        self.page = await self.browser.new_page()
    
    async def close_browser(self):
        """Close browser."""
        if self.page:
            await self.page.close()
        if self.browser:
            await self.browser.close()
    
    async def apply_greenhouse(
        self,
        job_url: str,
        profile: Dict[str, Any],
        resume_path: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Apply through Greenhouse application form.
        """
        result = {
            "success": False,
            "method": "greenhouse",
            "log": []
        }
        
        try:
            await self.page.goto(job_url, timeout=60000)
            await self.page.wait_for_load_state('networkidle')
            result["log"].append("Loaded application page")
            
            # Click apply button if exists
            apply_btn = await self.page.query_selector("a[href*='#app'], button[data-job-id]")
            if apply_btn:
                await apply_btn.click()
                await asyncio.sleep(2)
                result["log"].append("Clicked apply button")
            
            # Fill form fields
            form_filled = await self._fill_greenhouse_form(profile)
            result["log"].extend(form_filled.get("log", []))
            
            # Upload resume
            if resume_path:
                file_input = await self.page.query_selector("input[type='file']")
                if file_input:
                    await file_input.set_input_files(resume_path)
                    result["log"].append("Uploaded resume")
                    await asyncio.sleep(2)
            
            # Submit application
            submit_btn = await self.page.query_selector(
                "button[type='submit'], input[type='submit'], button:has-text('Submit')"
            )
            if submit_btn:
                # In test mode, don't actually submit
                if settings.debug:
                    result["log"].append("DEBUG MODE: Would submit application")
                    result["success"] = True
                else:
                    await submit_btn.click()
                    await asyncio.sleep(5)
                    
                    # Check for success indicators
                    content = await self.page.content()
                    if "thank" in content.lower() or "success" in content.lower():
                        result["success"] = True
                        result["log"].append("Application submitted successfully")
                    else:
                        result["log"].append("Submission status unclear")
                        result["success"] = True  # Assume success if no error
            else:
                result["log"].append("Submit button not found")
            
        except Exception as e:
            result["log"].append(f"Error: {str(e)}")
            result["error"] = str(e)
        
        return result
    
    async def _fill_greenhouse_form(self, profile: Dict[str, Any]) -> Dict[str, Any]:
        """Fill Greenhouse application form fields."""
        log = []
        
        # Common field mappings
        field_mappings = {
            "first_name": profile.get("full_name", "").split()[0] if profile.get("full_name") else "",
            "last_name": " ".join(profile.get("full_name", "").split()[1:]) if profile.get("full_name") else "",
            "email": profile.get("email", ""),
            "phone": profile.get("phone", ""),
            "linkedin": profile.get("linkedin_url", ""),
            "location": profile.get("location", ""),
        }
        
        for field_name, value in field_mappings.items():
            if not value:
                continue
            
            # Try various selector patterns
            selectors = [
                f"input[name*='{field_name}']",
                f"input[id*='{field_name}']",
                f"input[placeholder*='{field_name.replace('_', ' ')}' i]",
                f"input[aria-label*='{field_name.replace('_', ' ')}' i]"
            ]
            
            for selector in selectors:
                try:
                    elem = await self.page.query_selector(selector)
                    if elem:
                        await elem.fill(value)
                        log.append(f"Filled {field_name}")
                        break
                except:
                    continue
        
        return {"log": log}
    
    async def apply_lever(
        self,
        job_url: str,
        profile: Dict[str, Any],
        resume_path: Optional[str] = None
    ) -> Dict[str, Any]:
        """Apply through Lever application form."""
        result = {
            "success": False,
            "method": "lever",
            "log": []
        }
        
        try:
            # Lever application URLs usually end with /apply
            apply_url = job_url if job_url.endswith('/apply') else f"{job_url}/apply"
            
            await self.page.goto(apply_url, timeout=60000)
            await self.page.wait_for_load_state('networkidle')
            result["log"].append("Loaded Lever application page")
            
            # Fill standard fields
            await self._fill_lever_form(profile)
            result["log"].append("Filled form fields")
            
            # Upload resume
            if resume_path:
                file_input = await self.page.query_selector("input[name='resume']")
                if file_input:
                    await file_input.set_input_files(resume_path)
                    result["log"].append("Uploaded resume")
            
            # Submit
            submit_btn = await self.page.query_selector("button[type='submit']")
            if submit_btn:
                if settings.debug:
                    result["log"].append("DEBUG MODE: Would submit application")
                    result["success"] = True
                else:
                    await submit_btn.click()
                    await asyncio.sleep(5)
                    result["success"] = True
                    result["log"].append("Application submitted")
            
        except Exception as e:
            result["log"].append(f"Error: {str(e)}")
            result["error"] = str(e)
        
        return result
    
    async def _fill_lever_form(self, profile: Dict[str, Any]):
        """Fill Lever application form."""
        # Name field
        name_field = await self.page.query_selector("input[name='name']")
        if name_field and profile.get("full_name"):
            await name_field.fill(profile["full_name"])
        
        # Email field
        email_field = await self.page.query_selector("input[name='email']")
        if email_field and profile.get("email"):
            await email_field.fill(profile["email"])
        
        # Phone field
        phone_field = await self.page.query_selector("input[name='phone']")
        if phone_field and profile.get("phone"):
            await phone_field.fill(profile["phone"])
        
        # LinkedIn field
        linkedin_field = await self.page.query_selector("input[name*='linkedin']")
        if linkedin_field and profile.get("linkedin_url"):
            await linkedin_field.fill(profile["linkedin_url"])
    
    async def apply_easy_apply(
        self,
        job_url: str,
        profile: Dict[str, Any]
    ) -> Dict[str, Any]:
        """
        Handle LinkedIn Easy Apply.
        Note: This requires LinkedIn authentication which is complex.
        For MVP, this logs the attempt and requires manual completion.
        """
        result = {
            "success": False,
            "method": "easy_apply",
            "log": ["LinkedIn Easy Apply requires manual authentication"],
            "requires_manual": True
        }
        
        return result


class ApplicantAgent(BaseAgent):
    """
    Applicant Agent - Automates job applications.
    
    Responsibilities:
    - Apply to jobs with high match scores
    - Handle different application platforms (Greenhouse, Lever, etc.)
    - Track application attempts
    - Respect daily application limits
    """
    
    def __init__(self):
        super().__init__("applicant_agent")
        self.automator = ApplicationAutomator()
        self.daily_limit = settings.max_applications_per_day
        self.min_score = settings.min_match_score_for_auto_apply
    
    async def execute(self) -> Dict[str, Any]:
        """Execute automated applications."""
        applications_submitted = 0
        applications_failed = 0
        skipped_manual = 0
        
        with get_db_context() as db:
            # Check daily limit
            today = date.today()
            today_apps = db.query(crud.Application).filter(
                crud.Application.applied_at >= datetime.combine(today, datetime.min.time())
            ).count() if hasattr(crud, 'Application') else 0
            
            # Simple check - get count from Applications table
            from database.models import Application
            from sqlalchemy import func
            today_apps = db.query(func.count(Application.id)).filter(
                Application.applied_at >= datetime.combine(today, datetime.min.time())
            ).scalar() or 0
            
            remaining_quota = self.daily_limit - today_apps
            
            if remaining_quota <= 0:
                self.info("Daily application limit reached")
                return {"message": "Daily limit reached", "applied": 0}
            
            self.info(f"Daily quota remaining: {remaining_quota}")
            
            # Get profile
            profile = crud.get_profile(db)
            if not profile:
                self.warning("No profile found")
                return {"error": "No profile configured"}
            
            profile_data = profile.to_dict()
            resume_path = profile.resume_pdf_path
            
            # Get ready-to-apply jobs
            jobs = crud.get_ready_to_apply_jobs(db, min_score=self.min_score)
            jobs = jobs[:remaining_quota]  # Respect quota
            
            if not jobs:
                self.info("No jobs ready for application")
                return {"message": "No jobs to apply", "applied": 0}
            
            self.info(f"Found {len(jobs)} jobs ready for application")
            
            # Initialize browser
            await self.automator.init_browser()
            
            for job in jobs:
                try:
                    self.info(f"Applying to: {job.title} at {job.company_name}")
                    
                    # Update status to applying
                    crud.update_job_status(db, job.id, JobStatus.APPLYING)
                    
                    # Determine application method
                    result = await self._apply_to_job(job, profile_data, resume_path)
                    
                    if result.get("requires_manual"):
                        skipped_manual += 1
                        self.info(f"Job requires manual application: {job.url}")
                        continue
                    
                    if result.get("success"):
                        # Create application record
                        application_data = {
                            "job_id": job.id,
                            "applied_at": datetime.utcnow(),
                            "applied_via": result.get("method", "automated"),
                            "status": ApplicationStatus.SUBMITTED,
                            "auto_applied": True,
                            "application_log": result.get("log", [])
                        }
                        
                        crud.create_application(db, application_data)
                        crud.update_job_status(db, job.id, JobStatus.APPLIED)
                        
                        applications_submitted += 1
                        self.record_success()
                        self.info(f"Successfully applied to {job.title}")
                    else:
                        # Record failure
                        crud.update_job(db, job.id, {
                            "status": JobStatus.ERROR,
                            "error_message": result.get("error", "Application failed")
                        })
                        applications_failed += 1
                        self.record_failure()
                        self.error(f"Failed to apply: {result.get('error')}")
                    
                except Exception as e:
                    self.error(f"Error applying to job {job.id}: {e}")
                    self.record_failure()
                    applications_failed += 1
                
                # Rate limiting between applications
                await asyncio.sleep(5)
            
            # Cleanup
            await self.automator.close_browser()
        
        return {
            "applications_submitted": applications_submitted,
            "applications_failed": applications_failed,
            "skipped_manual": skipped_manual
        }
    
    async def _apply_to_job(
        self,
        job: Job,
        profile: Dict[str, Any],
        resume_path: Optional[str]
    ) -> Dict[str, Any]:
        """Determine and execute application method."""
        url = job.url.lower()
        
        # Route to appropriate handler based on URL
        if "greenhouse.io" in url or job.source == "greenhouse":
            return await self.automator.apply_greenhouse(job.url, profile, resume_path)
        
        elif "lever.co" in url or job.source == "lever":
            return await self.automator.apply_lever(job.url, profile, resume_path)
        
        elif "linkedin.com" in url and job.easy_apply_available:
            return await self.automator.apply_easy_apply(job.url, profile)
        
        else:
            # Unknown platform - mark for manual application
            return {
                "success": False,
                "requires_manual": True,
                "log": [f"Unknown application platform for {job.url}"]
            }
    
    async def apply_to_single_job(self, job_id: int) -> Dict[str, Any]:
        """Apply to a single job (manual trigger)."""
        with get_db_context() as db:
            job = crud.get_job(db, job_id)
            if not job:
                return {"error": "Job not found"}
            
            profile = crud.get_profile(db)
            if not profile:
                return {"error": "No profile configured"}
            
            await self.automator.init_browser()
            
            result = await self._apply_to_job(
                job,
                profile.to_dict(),
                profile.resume_pdf_path
            )
            
            await self.automator.close_browser()
            
            if result.get("success"):
                application_data = {
                    "job_id": job.id,
                    "applied_at": datetime.utcnow(),
                    "applied_via": result.get("method", "manual_automated"),
                    "status": ApplicationStatus.SUBMITTED,
                    "auto_applied": True,
                    "application_log": result.get("log", [])
                }
                crud.create_application(db, application_data)
                crud.update_job_status(db, job.id, JobStatus.APPLIED)
            
            return result


async def run_applicant_agent() -> Dict[str, Any]:
    """Entry point for scheduler."""
    agent = ApplicantAgent()
    return await agent.run()

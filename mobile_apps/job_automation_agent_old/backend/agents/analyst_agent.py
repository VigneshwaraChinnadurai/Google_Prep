"""
Analyst Agent - Evaluates job fitment and scores matches.
"""
import asyncio
import logging
from datetime import datetime
from typing import Dict, Any, List, Optional

from agents.base_agent import BaseAgent
from database.database import get_db_context
from database import crud
from database.models import Job, JobStatus
from services.gemini_client import get_gemini_client, GeminiClient
from config import get_settings

logger = logging.getLogger(__name__)
settings = get_settings()


class AnalystAgent(BaseAgent):
    """
    Analyst Agent - Evaluates job-profile fitment.
    
    Responsibilities:
    - Analyze new jobs against user profile
    - Score match percentage (0-100)
    - Identify strengths and weaknesses
    - Mark jobs as ready for application or skipped
    - Track skill gaps for improvement suggestions
    """
    
    def __init__(self):
        super().__init__("analyst_agent")
        self.gemini_client: Optional[GeminiClient] = None
        self.min_score_threshold = settings.min_match_score_for_auto_apply
    
    async def execute(self) -> Dict[str, Any]:
        """Execute job analysis."""
        jobs_analyzed = 0
        jobs_ready = 0
        jobs_skipped = 0
        skill_gaps_identified = []
        
        # Initialize Gemini client
        self.gemini_client = get_gemini_client()
        
        with get_db_context() as db:
            # Get user profile
            profile = crud.get_profile(db)
            if not profile:
                self.warning("No profile found. Skipping analysis.")
                return {"analyzed": 0, "message": "No profile configured"}
            
            profile_data = profile.to_dict()
            
            # Get new jobs awaiting analysis
            new_jobs = crud.get_new_jobs(db, limit=50)
            
            if not new_jobs:
                self.info("No new jobs to analyze")
                return {"analyzed": 0, "message": "No new jobs found"}
            
            self.info(f"Analyzing {len(new_jobs)} new jobs")
            
            for job in new_jobs:
                try:
                    # Fetch full job description if we only have basic info
                    description = job.description
                    if not description or len(description) < 100:
                        description = await self._fetch_job_description(job)
                        if description:
                            crud.update_job(db, job.id, {"description": description})
                    
                    if not description:
                        self.warning(f"No description for job {job.id}, skipping")
                        crud.update_job_status(db, job.id, JobStatus.SKIPPED)
                        jobs_skipped += 1
                        continue
                    
                    # Analyze fitment using Gemini
                    analysis = await self._analyze_fitment(profile_data, description)
                    
                    if analysis:
                        match_score = analysis.get("parsed", {}).get("match_score", 0)
                        justification = analysis.get("parsed", {}).get("justification", {})
                        
                        # Track API usage
                        self.record_api_call(
                            analysis.get("input_tokens", 0) + analysis.get("output_tokens", 0),
                            analysis.get("cost", 0)
                        )
                        
                        # Update job with analysis results
                        crud.update_job_analysis(
                            db=db,
                            job_id=job.id,
                            match_score=match_score,
                            justification=justification
                        )
                        
                        jobs_analyzed += 1
                        
                        if match_score >= self.min_score_threshold:
                            jobs_ready += 1
                            self.info(
                                f"Job ready for application: {job.title} at {job.company_name} "
                                f"(score: {match_score})"
                            )
                        else:
                            jobs_skipped += 1
                        
                        # Track skill gaps
                        missing_skills = justification.get("missing_skills", [])
                        for skill in missing_skills:
                            if skill not in skill_gaps_identified:
                                skill_gaps_identified.append(skill)
                                # Update skill gap tracking
                                gap = crud.get_or_create_skill_gap(
                                    db, skill, "unknown"
                                )
                                crud.update_skill_gap_frequency(
                                    db, gap.id, job.id, match_score / 100
                                )
                        
                        self.record_success()
                    else:
                        self.error(f"Failed to analyze job {job.id}")
                        self.record_failure()
                    
                except Exception as e:
                    self.error(f"Error analyzing job {job.id}: {e}")
                    self.record_failure()
                
                # Rate limiting
                await asyncio.sleep(1)
        
        return {
            "analyzed": jobs_analyzed,
            "ready_to_apply": jobs_ready,
            "skipped": jobs_skipped,
            "skill_gaps_identified": skill_gaps_identified
        }
    
    async def _fetch_job_description(self, job: Job) -> Optional[str]:
        """Fetch full job description from URL."""
        import httpx
        from bs4 import BeautifulSoup
        
        try:
            async with httpx.AsyncClient() as client:
                response = await client.get(
                    job.url,
                    headers={"User-Agent": "Mozilla/5.0"},
                    follow_redirects=True,
                    timeout=30.0
                )
                
                if response.status_code != 200:
                    return None
                
                soup = BeautifulSoup(response.text, 'html.parser')
                
                # Common job description containers
                selectors = [
                    "div.job-description",
                    "div.description",
                    "section.job-details",
                    "[class*='job-description']",
                    "[class*='description-content']",
                    "article",
                    "main"
                ]
                
                for selector in selectors:
                    elem = soup.select_one(selector)
                    if elem:
                        text = elem.get_text(separator="\n", strip=True)
                        if len(text) > 200:  # Reasonable description length
                            return text[:10000]  # Limit size
                
                # Fallback: get main content
                body = soup.find('body')
                if body:
                    return body.get_text(separator="\n", strip=True)[:10000]
                
        except Exception as e:
            self.warning(f"Failed to fetch job description: {e}")
        
        return None
    
    async def _analyze_fitment(
        self,
        profile: Dict[str, Any],
        job_description: str
    ) -> Optional[Dict[str, Any]]:
        """Analyze job fitment using Gemini."""
        try:
            result = self.gemini_client.analyze_job_fitment(
                profile_json=profile,
                job_description=job_description
            )
            return result
        except Exception as e:
            self.error(f"Gemini analysis failed: {e}")
            return None
    
    async def analyze_single_job(self, job_id: int) -> Dict[str, Any]:
        """Analyze a single job (for manual trigger)."""
        self.gemini_client = get_gemini_client()
        
        with get_db_context() as db:
            job = crud.get_job(db, job_id)
            if not job:
                return {"error": "Job not found"}
            
            profile = crud.get_profile(db)
            if not profile:
                return {"error": "No profile configured"}
            
            description = job.description
            if not description:
                description = await self._fetch_job_description(job)
            
            if not description:
                return {"error": "No job description available"}
            
            analysis = await self._analyze_fitment(
                profile.to_dict(),
                description
            )
            
            if analysis and analysis.get("parsed"):
                parsed = analysis["parsed"]
                crud.update_job_analysis(
                    db=db,
                    job_id=job.id,
                    match_score=parsed.get("match_score", 0),
                    justification=parsed.get("justification", {})
                )
                
                return {
                    "job_id": job_id,
                    "match_score": parsed.get("match_score"),
                    "justification": parsed.get("justification"),
                    "breakdown": parsed.get("breakdown")
                }
            
            return {"error": "Analysis failed"}


async def run_analyst_agent() -> Dict[str, Any]:
    """Entry point for scheduler."""
    agent = AnalystAgent()
    return await agent.run()

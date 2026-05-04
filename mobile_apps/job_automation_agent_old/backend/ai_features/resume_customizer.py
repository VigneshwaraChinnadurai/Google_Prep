"""
AI-powered resume customization for ATS optimization.
"""
from typing import Dict, Any, List, Optional
import logging

from services.gemini_client import get_gemini_client

logger = logging.getLogger(__name__)


class ResumeCustomizer:
    """
    Generates ATS-optimized resume content based on job descriptions.
    """
    
    def __init__(self):
        self.client = get_gemini_client()
    
    def customize_for_job(
        self,
        profile: Dict[str, Any],
        job_description: str,
        optimize_for: List[str] = None
    ) -> Dict[str, Any]:
        """
        Create an ATS-optimized version of the resume.
        
        Args:
            profile: User profile data
            job_description: Target job description
            optimize_for: Specific areas to optimize ("keywords", "experience", "skills")
        
        Returns:
            Dict with optimized resume sections
        """
        result = self.client.customize_resume(
            profile_json=profile,
            job_description=job_description
        )
        
        if result.get("parsed"):
            return result["parsed"]
        
        return {"error": "Failed to customize resume"}
    
    def extract_keywords(self, job_description: str) -> Dict[str, Any]:
        """Extract important keywords from a job description."""
        prompt = f"""Analyze this job description and extract important keywords:

Job Description:
{job_description}

Return a JSON object:
{{
  "required_skills": ["skill1", "skill2"],
  "nice_to_have_skills": ["skill1", "skill2"],
  "tools_technologies": ["tool1", "tool2"],
  "soft_skills": ["skill1", "skill2"],
  "industry_keywords": ["keyword1", "keyword2"],
  "action_verbs": ["verb1", "verb2"]
}}"""
        
        result = self.client.generate_json(prompt, temperature=0.2)
        return result.get("parsed", {})
    
    def generate_bullet_points(
        self,
        experience: Dict[str, Any],
        job_description: str,
        num_bullets: int = 4
    ) -> List[str]:
        """Generate optimized bullet points for experience."""
        prompt = f"""Create {num_bullets} impactful resume bullet points for this experience that align with the job description.

Experience:
Company: {experience.get('company')}
Role: {experience.get('title')}
Original Description: {experience.get('description')}

Target Job Description:
{job_description[:2000]}

Guidelines:
- Start with strong action verbs
- Include quantifiable results where possible
- Incorporate relevant keywords from the job description
- Keep each bullet to 1-2 lines

Return a JSON array of bullet points:
["bullet 1", "bullet 2", ...]"""
        
        result = self.client.generate_json(prompt, temperature=0.4)
        return result.get("parsed", [])
    
    def generate_professional_summary(
        self,
        profile: Dict[str, Any],
        job_description: str
    ) -> str:
        """Generate a tailored professional summary."""
        prompt = f"""Create a professional summary (3-4 sentences) tailored to this job.

Profile:
{profile.get('summary', '')}
Key Skills: {', '.join(profile.get('skills', {}).get('programming', [])[:5])}
Years Experience: Based on experience dates

Target Job:
{job_description[:1500]}

The summary should:
- Highlight relevant experience and skills
- Include keywords from the job posting
- Be compelling and specific
- Not exceed 4 sentences

Return just the summary text, no JSON."""
        
        result = self.client.generate(prompt, temperature=0.5, max_tokens=200)
        return result.get("content", "")


class CoverLetterGenerator:
    """Generates tailored cover letters."""
    
    def __init__(self):
        self.client = get_gemini_client()
    
    def generate(
        self,
        profile: Dict[str, Any],
        job_description: str,
        company_name: str,
        tone: str = "professional"
    ) -> str:
        """Generate a cover letter."""
        prompt = f"""Write a cover letter for this application.

Candidate:
Name: {profile.get('full_name')}
Current Role: {profile.get('experience', [{}])[0].get('title', 'Professional')}
Key Skills: {', '.join(profile.get('skills', {}).get('programming', [])[:5])}
Summary: {profile.get('summary', '')}

Company: {company_name}
Job Description:
{job_description[:2000]}

Tone: {tone}

Requirements:
- 3-4 paragraphs
- Opening: Hook and role interest
- Middle: 2 paragraphs highlighting relevant experience
- Closing: Call to action
- Professional but personable
- Include specific examples from experience

Return the cover letter text only."""
        
        result = self.client.generate(prompt, temperature=0.6, max_tokens=600)
        return result.get("content", "")

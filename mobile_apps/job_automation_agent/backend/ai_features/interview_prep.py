"""
Interview preparation document generation.
"""
from typing import Dict, Any, List, Optional
import logging

from services.gemini_client import get_gemini_client

logger = logging.getLogger(__name__)


class InterviewPrepGenerator:
    """
    Generates comprehensive interview preparation materials.
    """
    
    def __init__(self):
        self.client = get_gemini_client()
    
    def generate_full_prep(
        self,
        profile: Dict[str, Any],
        job_description: str,
        company_info: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Generate comprehensive interview preparation document.
        """
        return self.client.generate_interview_prep(
            profile_json=profile,
            job_description=job_description,
            company_info=company_info
        )
    
    def generate_behavioral_stories(
        self,
        profile: Dict[str, Any],
        job_description: str,
        num_stories: int = 5
    ) -> List[Dict[str, Any]]:
        """Generate STAR-format stories for behavioral questions."""
        prompt = f"""Based on this profile and job, create {num_stories} behavioral interview stories using the STAR format.

Profile:
{profile.get('summary', '')}
Experience: {profile.get('experience', [])}
Projects: {profile.get('projects', [])}

Job Requirements:
{job_description[:2000]}

For each story, identify:
1. A relevant question it answers (e.g., "Tell me about a time you led a project")
2. STAR breakdown

Return JSON:
{{
  "stories": [
    {{
      "question_type": "leadership|conflict|challenge|failure|success|teamwork",
      "example_question": "Tell me about...",
      "situation": "Brief context",
      "task": "Your responsibility",
      "action": "What you did (detailed)",
      "result": "Quantifiable outcome",
      "key_skills_demonstrated": ["skill1", "skill2"]
    }}
  ]
}}"""
        
        result = self.client.generate_json(prompt, temperature=0.5)
        return result.get("parsed", {}).get("stories", [])
    
    def generate_technical_questions(
        self,
        job_description: str,
        skill_areas: List[str]
    ) -> List[Dict[str, Any]]:
        """Generate likely technical interview questions."""
        prompt = f"""Generate technical interview questions for this role.

Job Description:
{job_description[:2000]}

Skill Areas: {skill_areas}

Create 10 technical questions ranging from basic to advanced.

Return JSON:
{{
  "questions": [
    {{
      "question": "The technical question",
      "topic": "Specific topic/skill",
      "difficulty": "basic|intermediate|advanced",
      "what_they_assess": "What interviewer is looking for",
      "preparation_tips": "How to prepare"
    }}
  ]
}}"""
        
        result = self.client.generate_json(prompt, temperature=0.4)
        return result.get("parsed", {}).get("questions", [])
    
    def generate_questions_to_ask(
        self,
        job_description: str,
        company_name: str
    ) -> List[Dict[str, Any]]:
        """Generate thoughtful questions to ask the interviewer."""
        prompt = f"""Generate 8 thoughtful questions to ask in an interview at {company_name}.

Job Description:
{job_description[:1500]}

Create questions that:
1. Show genuine interest
2. Demonstrate industry knowledge
3. Help assess culture fit
4. Avoid questions with obvious online answers

Return JSON:
{{
  "questions": [
    {{
      "question": "Your question",
      "category": "role|team|culture|growth|company",
      "what_it_shows": "What asking this demonstrates",
      "follow_up_if": "When to ask follow-up"
    }}
  ]
}}"""
        
        result = self.client.generate_json(prompt, temperature=0.5)
        return result.get("parsed", {}).get("questions", [])
    
    def generate_salary_negotiation_prep(
        self,
        job_description: str,
        current_salary: Optional[int] = None,
        market_range: Optional[Dict[str, int]] = None
    ) -> Dict[str, Any]:
        """Generate salary negotiation preparation."""
        context = f"Current salary: ${current_salary:,}" if current_salary else ""
        market = f"Market range: ${market_range['min']:,} - ${market_range['max']:,}" if market_range else ""
        
        prompt = f"""Prepare salary negotiation strategy.

Job Description:
{job_description[:1500]}

{context}
{market}

Provide:
1. Recommended salary range to target
2. Key talking points for negotiation
3. Benefits/perks to negotiate if salary is fixed
4. How to respond to common negotiation situations

Return JSON:
{{
  "recommended_range": {{"min": number, "max": number}},
  "talking_points": ["point1", "point2"],
  "additional_benefits": ["benefit1", "benefit2"],
  "scenarios": [
    {{
      "situation": "If they say X",
      "response": "You should say Y"
    }}
  ]
}}"""
        
        result = self.client.generate_json(prompt, temperature=0.4)
        return result.get("parsed", {})

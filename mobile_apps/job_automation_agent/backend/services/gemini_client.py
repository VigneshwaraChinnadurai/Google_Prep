"""
Gemini API client for LLM interactions.
"""
import json
import logging
from typing import Optional, Dict, Any, List, Generator
from datetime import datetime
import google.generativeai as genai
from config import get_settings

logger = logging.getLogger(__name__)
settings = get_settings()


class CostTracker:
    """Track API costs for budget management."""
    
    # Gemini 1.5 Flash pricing (per 1M tokens)
    INPUT_COST_PER_MILLION = 0.075  # $0.075 per 1M input tokens
    OUTPUT_COST_PER_MILLION = 0.30  # $0.30 per 1M output tokens
    
    def __init__(self):
        self.daily_cost = 0.0
        self.total_calls = 0
        self.total_input_tokens = 0
        self.total_output_tokens = 0
        self.last_reset = datetime.utcnow().date()
    
    def _maybe_reset_daily(self):
        """Reset daily counters if it's a new day."""
        today = datetime.utcnow().date()
        if today > self.last_reset:
            self.daily_cost = 0.0
            self.last_reset = today
    
    def add_usage(self, input_tokens: int, output_tokens: int):
        """Record token usage and calculate cost."""
        self._maybe_reset_daily()
        
        input_cost = (input_tokens / 1_000_000) * self.INPUT_COST_PER_MILLION
        output_cost = (output_tokens / 1_000_000) * self.OUTPUT_COST_PER_MILLION
        cost = input_cost + output_cost
        
        self.daily_cost += cost
        self.total_calls += 1
        self.total_input_tokens += input_tokens
        self.total_output_tokens += output_tokens
        
        return cost
    
    def can_make_call(self) -> bool:
        """Check if we're within budget for another call."""
        self._maybe_reset_daily()
        return self.daily_cost < settings.max_daily_api_cost
    
    def get_stats(self) -> Dict[str, Any]:
        """Get current cost statistics."""
        return {
            "daily_cost": round(self.daily_cost, 4),
            "daily_budget": settings.max_daily_api_cost,
            "budget_remaining": round(settings.max_daily_api_cost - self.daily_cost, 4),
            "total_calls": self.total_calls,
            "total_input_tokens": self.total_input_tokens,
            "total_output_tokens": self.total_output_tokens
        }


class GeminiClient:
    """Client for interacting with Google Gemini API."""
    
    def __init__(self):
        if not settings.gemini_api_key:
            raise ValueError("GEMINI_API_KEY not configured")
        
        genai.configure(api_key=settings.gemini_api_key)
        self.model = genai.GenerativeModel(settings.gemini_model)
        self.cost_tracker = CostTracker()
        
        logger.info(f"Gemini client initialized with model: {settings.gemini_model}")
    
    def generate(
        self,
        prompt: str,
        system_prompt: Optional[str] = None,
        temperature: float = 0.7,
        max_tokens: int = 4096,
        json_mode: bool = False
    ) -> Dict[str, Any]:
        """
        Generate a response from Gemini.
        
        Returns:
            Dict with 'content', 'input_tokens', 'output_tokens', 'cost'
        """
        if not self.cost_tracker.can_make_call():
            raise RuntimeError("Daily API budget exceeded")
        
        try:
            # Build the full prompt
            full_prompt = ""
            if system_prompt:
                full_prompt = f"SYSTEM: {system_prompt}\n\nUSER: {prompt}"
            else:
                full_prompt = prompt
            
            # Configure generation
            generation_config = genai.GenerationConfig(
                temperature=temperature,
                max_output_tokens=max_tokens,
            )
            
            if json_mode:
                generation_config.response_mime_type = "application/json"
            
            # Generate response
            response = self.model.generate_content(
                full_prompt,
                generation_config=generation_config
            )
            
            # Extract token counts
            input_tokens = response.usage_metadata.prompt_token_count if response.usage_metadata else 0
            output_tokens = response.usage_metadata.candidates_token_count if response.usage_metadata else 0
            
            # Track cost
            cost = self.cost_tracker.add_usage(input_tokens, output_tokens)
            
            return {
                "content": response.text,
                "input_tokens": input_tokens,
                "output_tokens": output_tokens,
                "cost": cost
            }
            
        except Exception as e:
            logger.error(f"Gemini API error: {e}")
            raise
    
    def generate_json(
        self,
        prompt: str,
        system_prompt: Optional[str] = None,
        temperature: float = 0.3
    ) -> Dict[str, Any]:
        """Generate and parse JSON response."""
        result = self.generate(
            prompt=prompt,
            system_prompt=system_prompt,
            temperature=temperature,
            json_mode=True
        )
        
        try:
            parsed = json.loads(result["content"])
            result["parsed"] = parsed
            return result
        except json.JSONDecodeError as e:
            logger.error(f"Failed to parse JSON response: {e}")
            logger.debug(f"Raw content: {result['content']}")
            result["parsed"] = None
            result["parse_error"] = str(e)
            return result
    
    def analyze_job_fitment(
        self,
        profile_json: Dict[str, Any],
        job_description: str
    ) -> Dict[str, Any]:
        """
        Analyze job fitment using structured prompt.
        
        Returns:
            Dict with match_score, justification, strengths, weaknesses
        """
        system_prompt = """You are an expert career-matching AI. Your task is to analyze a user's profile against a job description and return a JSON object with a match score and a detailed analysis.

Be thorough but fair in your analysis. Consider:
- Skills alignment (both technical and soft skills)
- Years and type of experience
- Project relevance
- Industry knowledge
- Location and remote work compatibility"""

        user_prompt = f"""My Profile:
---
{json.dumps(profile_json, indent=2)}
---

Job Description:
---
{job_description}
---

Analyze my profile against the job description. Score the match from 0 to 100 based on:
1. Technical skills alignment (40%)
2. Experience relevance (30%)
3. Project/domain expertise (20%)
4. Cultural/soft skills fit (10%)

Provide a brief justification for your score, listing my strengths and weaknesses for this role.

Return ONLY a JSON object in the following format:
{{
  "match_score": <number>,
  "justification": {{
    "summary": "<brief overall assessment>",
    "strengths": ["<strength 1>", "<strength 2>", ...],
    "weaknesses": ["<weakness 1>", "<weakness 2>", ...],
    "missing_skills": ["<skill 1>", "<skill 2>", ...],
    "recommendations": ["<recommendation 1>", ...]
  }},
  "breakdown": {{
    "technical_skills": <0-100>,
    "experience": <0-100>,
    "projects": <0-100>,
    "soft_skills": <0-100>
  }}
}}"""

        result = self.generate_json(
            prompt=user_prompt,
            system_prompt=system_prompt,
            temperature=0.2
        )
        
        return result
    
    def classify_email(self, email_subject: str, email_body: str) -> Dict[str, Any]:
        """
        Classify an email related to job applications.
        """
        system_prompt = """You are an AI assistant that classifies job application-related emails.

Categories:
- APPLICATION_CONFIRMATION: Acknowledgment that application was received
- INTERVIEW_REQUEST: Request to schedule an interview
- REJECTION: The application was not successful
- OFFER: A job offer
- FOLLOWUP_REQUEST: Request for additional information
- ASSESSMENT_REQUEST: Request to complete an assessment/test
- GENERAL: Other job-related communication
- SPAM: Not related to job applications"""

        user_prompt = f"""Classify this email:

Subject: {email_subject}

Body:
{email_body[:2000]}  # Truncate for token efficiency

Return a JSON object:
{{
  "classification": "<category>",
  "confidence": <0.0-1.0>,
  "extracted_info": {{
    "company_name": "<if mentioned>",
    "job_title": "<if mentioned>",
    "interview_date": "<if scheduling>",
    "action_required": "<what user needs to do, if anything>",
    "deadline": "<any deadline mentioned>"
  }}
}}"""

        return self.generate_json(
            prompt=user_prompt,
            system_prompt=system_prompt,
            temperature=0.1
        )
    
    def customize_resume(
        self,
        profile_json: Dict[str, Any],
        job_description: str
    ) -> Dict[str, Any]:
        """
        Generate ATS-optimized resume content.
        """
        system_prompt = """You are an expert resume writer who specializes in optimizing resumes for Applicant Tracking Systems (ATS).

Your goal is to:
1. Identify key keywords from the job description
2. Rephrase experience and skills to match the job requirements
3. Highlight the most relevant projects and achievements
4. Ensure maximum ATS compatibility while maintaining authenticity"""

        user_prompt = f"""Profile:
{json.dumps(profile_json, indent=2)}

Job Description:
{job_description}

Create an ATS-optimized version of this profile. Return JSON:
{{
  "optimized_summary": "<new professional summary>",
  "highlighted_skills": ["<most relevant skill 1>", ...],
  "experience_bullets": [
    {{
      "company": "<company>",
      "role": "<role>",
      "bullets": ["<optimized bullet 1>", ...]
    }}
  ],
  "keywords_added": ["<keyword 1>", ...],
  "ats_score_estimate": <0-100>
}}"""

        return self.generate_json(
            prompt=user_prompt,
            system_prompt=system_prompt,
            temperature=0.4
        )
    
    def generate_interview_prep(
        self,
        profile_json: Dict[str, Any],
        job_description: str,
        company_info: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Generate comprehensive interview preparation document.
        """
        system_prompt = """You are an expert interview coach with deep knowledge of technical and behavioral interviews.

Create a comprehensive prep document that helps the candidate succeed."""

        company_context = f"\n\nCompany Information:\n{company_info}" if company_info else ""

        user_prompt = f"""Candidate Profile:
{json.dumps(profile_json, indent=2)}

Job Description:
{job_description}{company_context}

Generate interview preparation materials. Return JSON:
{{
  "company_research": {{
    "key_facts": ["<fact 1>", ...],
    "recent_news": ["<news item, if known>", ...],
    "culture_notes": "<insights about company culture>"
  }},
  "role_analysis": {{
    "key_responsibilities": ["<responsibility 1>", ...],
    "likely_challenges": ["<challenge 1>", ...],
    "success_metrics": ["<how success is measured>", ...]
  }},
  "behavioral_questions": [
    {{
      "question": "<question>",
      "why_asked": "<why they ask this>",
      "suggested_story": "<which experience to use>",
      "star_framework": {{
        "situation": "<brief situation>",
        "task": "<your task>",
        "action": "<what you did>",
        "result": "<outcome>"
      }}
    }}
  ],
  "technical_questions": [
    {{
      "question": "<technical question>",
      "topic": "<area being tested>",
      "preparation_tip": "<how to prepare>"
    }}
  ],
  "questions_to_ask": [
    {{
      "question": "<question for interviewer>",
      "shows_interest_in": "<what this demonstrates>"
    }}
  ],
  "talking_points": ["<key point to mention>", ...],
  "weakness_mitigation": {{
    "<weakness>": "<how to address if asked>"
  }}
}}"""

        return self.generate_json(
            prompt=user_prompt,
            system_prompt=system_prompt,
            temperature=0.5
        )
    
    def analyze_skill_gaps(
        self,
        profile_json: Dict[str, Any],
        missed_skills: List[str]
    ) -> Dict[str, Any]:
        """
        Analyze skill gaps and suggest learning resources.
        """
        system_prompt = """You are a career development advisor who specializes in helping professionals upskill efficiently.

Provide practical, actionable recommendations for learning new skills."""

        user_prompt = f"""Current Profile:
{json.dumps(profile_json, indent=2)}

Skills I'm missing for target jobs:
{json.dumps(missed_skills)}

For each missing skill, provide learning recommendations. Return JSON:
{{
  "skill_analysis": [
    {{
      "skill": "<skill name>",
      "importance": "<high/medium/low>",
      "time_to_competency": "<estimated time>",
      "learning_path": {{
        "beginner_resources": ["<resource 1>", ...],
        "intermediate_resources": ["<resource 1>", ...],
        "hands_on_projects": ["<project idea 1>", ...]
      }},
      "certification_options": ["<cert 1>", ...],
      "quick_wins": "<what can show progress quickly>"
    }}
  ],
  "priority_order": ["<skill to learn first>", "<skill 2>", ...],
  "synergies": "<skills that can be learned together>"
}}"""

        return self.generate_json(
            prompt=user_prompt,
            system_prompt=system_prompt,
            temperature=0.4
        )
    
    def chat(self, message: str, context: Optional[str] = None) -> Dict[str, Any]:
        """
        General chat for user queries about their job search.
        """
        system_prompt = """You are a helpful job search assistant. You have access to the user's profile, job applications, and search history.

Be concise, encouraging, and actionable. If asked about status, provide specific numbers and recommendations."""

        prompt = message
        if context:
            prompt = f"Context:\n{context}\n\nUser: {message}"
        
        return self.generate(
            prompt=prompt,
            system_prompt=system_prompt,
            temperature=0.7
        )
    
    def get_cost_stats(self) -> Dict[str, Any]:
        """Get current API cost statistics."""
        return self.cost_tracker.get_stats()


# Singleton instance
_client: Optional[GeminiClient] = None


def get_gemini_client() -> GeminiClient:
    """Get or create Gemini client singleton."""
    global _client
    if _client is None:
        _client = GeminiClient()
    return _client

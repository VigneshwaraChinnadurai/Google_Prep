"""
Skill gap analysis and improvement recommendations.
"""
from typing import Dict, Any, List, Optional
import logging

from services.gemini_client import get_gemini_client

logger = logging.getLogger(__name__)


class SkillGapAnalyzer:
    """
    Analyzes skill gaps and provides learning recommendations.
    """
    
    def __init__(self):
        self.client = get_gemini_client()
    
    def analyze_gaps(
        self,
        profile: Dict[str, Any],
        target_roles: List[str],
        job_descriptions: List[str]
    ) -> Dict[str, Any]:
        """
        Analyze skill gaps based on target roles and job descriptions.
        """
        # Combine job descriptions for analysis
        combined_requirements = "\n---\n".join(job_descriptions[:5])
        
        prompt = f"""Analyze the skill gap between this profile and target jobs.

Profile Skills:
{profile.get('skills', {})}

Target Roles: {target_roles}

Job Requirements (sample):
{combined_requirements[:3000]}

Identify:
1. Critical missing skills (blocking for most roles)
2. Nice-to-have skills (would improve candidacy)
3. Skills that need deepening (have basics, need advanced)
4. Emerging skills worth learning

Return JSON:
{{
  "critical_gaps": [
    {{
      "skill": "skill name",
      "frequency": "how often required",
      "impact": "high|medium",
      "current_level": "none|basic|intermediate",
      "required_level": "basic|intermediate|advanced"
    }}
  ],
  "nice_to_have": ["skill1", "skill2"],
  "needs_deepening": [
    {{
      "skill": "skill name",
      "current_level": "basic/intermediate",
      "target_level": "advanced/expert"
    }}
  ],
  "emerging_skills": ["skill1", "skill2"],
  "overall_readiness": "percentage 0-100",
  "summary": "Brief assessment"
}}"""
        
        result = self.client.generate_json(prompt, temperature=0.3)
        return result.get("parsed", {})
    
    def get_learning_plan(
        self,
        skills: List[str],
        time_available: str = "2 months",
        learning_style: str = "hands-on"
    ) -> Dict[str, Any]:
        """Generate a personalized learning plan."""
        result = self.client.analyze_skill_gaps(
            profile_json={},  # Not needed for learning plan
            missed_skills=skills
        )
        
        return result.get("parsed", {})
    
    def find_quick_wins(
        self,
        skill_gaps: List[str],
        profile: Dict[str, Any]
    ) -> List[Dict[str, Any]]:
        """Find skills that can be quickly acquired based on existing knowledge."""
        prompt = f"""Identify quick-win skills from this gap list.

Existing Skills:
{profile.get('skills', {})}

Gap Skills: {skill_gaps}

Quick wins are skills that:
1. Build on existing knowledge
2. Can show competency in 1-2 weeks
3. Have high impact on job applications

Return JSON:
{{
  "quick_wins": [
    {{
      "skill": "skill name",
      "builds_on": "existing skill it relates to",
      "time_to_basic": "estimated time",
      "learning_approach": "recommended method",
      "proof_of_learning": "how to demonstrate"
    }}
  ]
}}"""
        
        result = self.client.generate_json(prompt, temperature=0.4)
        return result.get("parsed", {}).get("quick_wins", [])
    
    def suggest_projects(
        self,
        skills_to_develop: List[str],
        profile: Dict[str, Any]
    ) -> List[Dict[str, Any]]:
        """Suggest portfolio projects that develop target skills."""
        prompt = f"""Suggest portfolio projects to develop these skills.

Skills to Develop: {skills_to_develop}

Current Experience:
{profile.get('summary', '')}
Projects: {profile.get('projects', [])}

Suggest 3-5 projects that:
1. Develop multiple target skills
2. Are impressive to employers
3. Can be completed in 1-4 weeks
4. Build on existing knowledge

Return JSON:
{{
  "projects": [
    {{
      "name": "Project name",
      "description": "What it does",
      "skills_developed": ["skill1", "skill2"],
      "difficulty": "beginner|intermediate|advanced",
      "time_estimate": "X weeks",
      "tech_stack": ["tech1", "tech2"],
      "why_impressive": "Why employers like this",
      "getting_started": "First steps"
    }}
  ]
}}"""
        
        result = self.client.generate_json(prompt, temperature=0.5)
        return result.get("parsed", {}).get("projects", [])


class CareerAdvisor:
    """Provides career advice and recommendations."""
    
    def __init__(self):
        self.client = get_gemini_client()
    
    def assess_career_trajectory(
        self,
        profile: Dict[str, Any],
        target_role: str
    ) -> Dict[str, Any]:
        """Assess how well current trajectory aligns with target role."""
        prompt = f"""Assess career trajectory alignment.

Profile:
{profile.get('summary', '')}
Experience: {profile.get('experience', [])}
Education: {profile.get('education', [])}

Target Role: {target_role}

Assess:
1. Current trajectory alignment
2. Gaps in experience
3. Recommended pivot points
4. Timeline to target role

Return JSON:
{{
  "alignment_score": "0-100",
  "assessment": "Brief assessment",
  "experience_gaps": ["gap1", "gap2"],
  "recommended_steps": ["step1", "step2"],
  "alternative_paths": [
    {{
      "path": "description",
      "pros": ["pro1"],
      "cons": ["con1"],
      "timeline": "estimated time"
    }}
  ],
  "timeline_to_target": "realistic estimate"
}}"""
        
        result = self.client.generate_json(prompt, temperature=0.4)
        return result.get("parsed", {})

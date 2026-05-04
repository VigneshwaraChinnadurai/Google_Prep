"""
Tracker Agent - Monitors email for application updates.
"""
import asyncio
import logging
import re
from datetime import datetime, timedelta
from typing import Dict, Any, List, Optional

from agents.base_agent import BaseAgent
from database.database import get_db_context
from database import crud
from database.models import (
    Application, Email, ApplicationStatus, EmailClassification
)
from services.gemini_client import get_gemini_client
from services.gmail_service import get_gmail_service, GmailService
from config import get_settings

logger = logging.getLogger(__name__)
settings = get_settings()


class EmailMatcher:
    """Matches emails to job applications."""
    
    @staticmethod
    def extract_company_from_email(email: Dict[str, Any]) -> Optional[str]:
        """Extract company name from email sender or content."""
        sender = email.get("sender", "").lower()
        sender_email = email.get("sender_email", "").lower()
        subject = email.get("subject", "").lower()
        body = email.get("body_text", "").lower()
        
        # Common patterns in sender domain
        domain_match = re.search(r'@([a-zA-Z0-9-]+)\.', sender_email)
        if domain_match:
            domain = domain_match.group(1)
            # Filter out generic domains
            if domain not in ['greenhouse', 'lever', 'indeed', 'linkedin', 'workday', 'gmail', 'outlook']:
                return domain.title()
        
        # Try to extract from "on behalf of" patterns
        behalf_match = re.search(r'on behalf of ([a-zA-Z0-9\s]+)', sender, re.IGNORECASE)
        if behalf_match:
            return behalf_match.group(1).strip()
        
        # Look for company names in subject/body
        company_patterns = [
            r'at\s+([A-Z][a-zA-Z0-9\s]+)',
            r'from\s+([A-Z][a-zA-Z0-9\s]+)',
            r'([A-Z][a-zA-Z0-9]+)\s+(?:is|has|team)',
        ]
        
        for pattern in company_patterns:
            match = re.search(pattern, subject)
            if match:
                return match.group(1).strip()
        
        return None
    
    @staticmethod
    def extract_job_title_from_email(email: Dict[str, Any]) -> Optional[str]:
        """Extract job title from email content."""
        subject = email.get("subject", "")
        body = email.get("body_text", "")[:2000]
        
        # Common job title patterns
        patterns = [
            r'for\s+(?:the\s+)?([A-Z][a-zA-Z\s]+(?:Engineer|Developer|Scientist|Manager|Analyst|Designer))',
            r'([A-Z][a-zA-Z\s]+(?:Engineer|Developer|Scientist|Manager|Analyst|Designer))\s+(?:position|role)',
            r'application.*?([A-Z][a-zA-Z\s]+(?:Engineer|Developer|Scientist|Manager|Analyst|Designer))',
        ]
        
        for pattern in patterns:
            match = re.search(pattern, subject + " " + body, re.IGNORECASE)
            if match:
                return match.group(1).strip()
        
        return None
    
    @staticmethod
    def match_email_to_application(
        email: Dict[str, Any],
        applications: List[Application]
    ) -> tuple[Optional[Application], float]:
        """
        Match an email to an application.
        
        Returns:
            Tuple of (matched_application, confidence_score)
        """
        company = EmailMatcher.extract_company_from_email(email)
        title = EmailMatcher.extract_job_title_from_email(email)
        
        best_match = None
        best_confidence = 0.0
        
        for app in applications:
            confidence = 0.0
            
            # Get associated job
            from database.database import get_db_context
            from database import crud
            
            with get_db_context() as db:
                job = crud.get_job(db, app.job_id)
                if not job:
                    continue
            
            # Match by company name
            if company and job.company_name:
                if company.lower() in job.company_name.lower() or \
                   job.company_name.lower() in company.lower():
                    confidence += 0.5
            
            # Match by job title
            if title and job.title:
                title_words = set(title.lower().split())
                job_words = set(job.title.lower().split())
                overlap = len(title_words & job_words) / max(len(title_words), len(job_words))
                confidence += overlap * 0.3
            
            # Recency bonus (emails about recent applications are more likely)
            if app.applied_at:
                days_since = (datetime.utcnow() - app.applied_at).days
                if days_since <= 7:
                    confidence += 0.1
                elif days_since <= 30:
                    confidence += 0.05
            
            # Status relevance
            if app.status in [ApplicationStatus.SUBMITTED, ApplicationStatus.UNDER_REVIEW]:
                confidence += 0.1
            
            if confidence > best_confidence:
                best_confidence = confidence
                best_match = app
        
        # Only return match if confidence is above threshold
        if best_confidence >= 0.3:
            return best_match, best_confidence
        
        return None, 0.0


class TrackerAgent(BaseAgent):
    """
    Tracker Agent - Syncs application status from Gmail.
    
    Responsibilities:
    - Fetch job-related emails from Gmail
    - Classify emails (rejection, interview, etc.)
    - Match emails to applications
    - Update application statuses
    - Generate interview prep when interview scheduled
    """
    
    def __init__(self):
        super().__init__("tracker_agent")
        self.gmail_service: Optional[GmailService] = None
        self.gemini_client = None
        self.email_matcher = EmailMatcher()
    
    async def execute(self) -> Dict[str, Any]:
        """Execute email tracking."""
        emails_processed = 0
        statuses_updated = 0
        interviews_found = 0
        rejections_found = 0
        
        # Initialize Gmail service
        self.gmail_service = get_gmail_service()
        if not self.gmail_service.authenticate():
            self.warning("Gmail authentication failed")
            return {"error": "Gmail authentication failed"}
        
        self.gemini_client = get_gemini_client()
        
        with get_db_context() as db:
            # Get active applications for matching
            active_applications = crud.get_applications_for_tracking(db)
            
            if not active_applications:
                self.info("No active applications to track")
                return {"message": "No active applications"}
            
            self.info(f"Tracking {len(active_applications)} active applications")
            
            # Fetch recent job-related emails
            emails = self.gmail_service.get_job_related_emails(
                days_back=7,
                max_results=50
            )
            
            self.info(f"Found {len(emails)} job-related emails")
            
            for email_data in emails:
                # Skip if already processed
                existing = crud.get_email_by_gmail_id(db, email_data['gmail_id'])
                if existing and existing.processed:
                    continue
                
                try:
                    # Save email record
                    if not existing:
                        email_record = crud.create_email(db, {
                            'gmail_id': email_data['gmail_id'],
                            'thread_id': email_data['thread_id'],
                            'subject': email_data['subject'],
                            'sender': email_data['sender'],
                            'sender_email': email_data['sender_email'],
                            'received_at': email_data['received_at'],
                            'body_text': email_data['body_text'][:10000] if email_data['body_text'] else None,
                            'body_html': email_data['body_html'][:10000] if email_data['body_html'] else None
                        })
                    else:
                        email_record = existing
                    
                    # Classify email using Gemini
                    classification_result = await self._classify_email(email_data)
                    
                    if classification_result:
                        parsed = classification_result.get("parsed", {})
                        classification = parsed.get("classification", "GENERAL")
                        confidence = parsed.get("confidence", 0.5)
                        
                        self.record_api_call(
                            classification_result.get("input_tokens", 0) + 
                            classification_result.get("output_tokens", 0),
                            classification_result.get("cost", 0)
                        )
                        
                        # Match to application
                        matched_app, match_confidence = self.email_matcher.match_email_to_application(
                            email_data, active_applications
                        )
                        
                        # Update email record
                        email_classification = self._map_classification(classification)
                        crud.update_email_classification(
                            db=db,
                            email_id=email_record.id,
                            classification=email_classification,
                            confidence=confidence,
                            application_id=matched_app.id if matched_app else None
                        )
                        
                        # Update application status if matched
                        if matched_app and confidence >= 0.6:
                            action = await self._handle_classification(
                                db, matched_app, email_classification, parsed.get("extracted_info", {})
                            )
                            
                            if action:
                                crud.mark_email_processed(db, email_record.id, action)
                                statuses_updated += 1
                                
                                if email_classification == EmailClassification.INTERVIEW_REQUEST:
                                    interviews_found += 1
                                elif email_classification == EmailClassification.REJECTION:
                                    rejections_found += 1
                        
                        emails_processed += 1
                        self.record_success()
                    
                except Exception as e:
                    self.error(f"Error processing email {email_data.get('gmail_id')}: {e}")
                    self.record_failure()
                
                # Rate limiting
                await asyncio.sleep(0.5)
        
        return {
            "emails_processed": emails_processed,
            "statuses_updated": statuses_updated,
            "interviews_found": interviews_found,
            "rejections_found": rejections_found
        }
    
    async def _classify_email(self, email: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        """Classify email using Gemini."""
        try:
            return self.gemini_client.classify_email(
                email_subject=email.get("subject", ""),
                email_body=email.get("body_text", "")[:2000]
            )
        except Exception as e:
            self.error(f"Email classification failed: {e}")
            return None
    
    def _map_classification(self, classification: str) -> EmailClassification:
        """Map string classification to enum."""
        mapping = {
            "APPLICATION_CONFIRMATION": EmailClassification.APPLICATION_CONFIRMATION,
            "INTERVIEW_REQUEST": EmailClassification.INTERVIEW_REQUEST,
            "REJECTION": EmailClassification.REJECTION,
            "OFFER": EmailClassification.OFFER,
            "FOLLOWUP_REQUEST": EmailClassification.FOLLOWUP_REQUEST,
            "ASSESSMENT_REQUEST": EmailClassification.ASSESSMENT_REQUEST,
            "GENERAL": EmailClassification.GENERAL,
            "SPAM": EmailClassification.SPAM
        }
        return mapping.get(classification, EmailClassification.GENERAL)
    
    async def _handle_classification(
        self,
        db,
        application: Application,
        classification: EmailClassification,
        extracted_info: Dict[str, Any]
    ) -> Optional[str]:
        """Handle classified email and update application."""
        action = None
        
        if classification == EmailClassification.APPLICATION_CONFIRMATION:
            crud.update_application_status(
                db, application.id, ApplicationStatus.CONFIRMED, "email_tracker"
            )
            action = "status_updated_confirmed"
        
        elif classification == EmailClassification.INTERVIEW_REQUEST:
            crud.update_application_status(
                db, application.id, ApplicationStatus.INTERVIEW_SCHEDULED, "email_tracker"
            )
            
            # Add interview date if extracted
            interview_date = extracted_info.get("interview_date")
            if interview_date:
                current = application.interview_dates or []
                current.append(interview_date)
                crud.update_application(db, application.id, {"interview_dates": current})
            
            # Trigger interview prep generation
            await self._generate_interview_prep(db, application)
            action = "interview_scheduled"
        
        elif classification == EmailClassification.REJECTION:
            crud.update_application_status(
                db, application.id, ApplicationStatus.REJECTED, "email_tracker"
            )
            action = "status_updated_rejected"
        
        elif classification == EmailClassification.OFFER:
            crud.update_application_status(
                db, application.id, ApplicationStatus.OFFER_RECEIVED, "email_tracker"
            )
            action = "offer_received"
        
        elif classification == EmailClassification.ASSESSMENT_REQUEST:
            # Keep status as UNDER_REVIEW but note the assessment
            application.notes = (application.notes or "") + "\nAssessment requested."
            db.commit()
            action = "assessment_noted"
        
        return action
    
    async def _generate_interview_prep(self, db, application: Application):
        """Generate interview preparation document."""
        try:
            # Get job and profile
            job = crud.get_job(db, application.job_id)
            profile = crud.get_profile(db)
            
            if not job or not profile:
                return
            
            # Generate prep document using Gemini
            prep_result = self.gemini_client.generate_interview_prep(
                profile_json=profile.to_dict(),
                job_description=job.description or "",
                company_info=None  # Could be enriched with company research
            )
            
            if prep_result and prep_result.get("parsed"):
                from database.models import InterviewPrep
                
                prep_data = prep_result["parsed"]
                
                prep = InterviewPrep(
                    application_id=application.id,
                    company_research=str(prep_data.get("company_research", {})),
                    role_analysis=str(prep_data.get("role_analysis", {})),
                    behavioral_questions=prep_data.get("behavioral_questions", []),
                    technical_questions=prep_data.get("technical_questions", []),
                    questions_for_interviewer=prep_data.get("questions_to_ask", []),
                    talking_points=prep_data.get("talking_points", []),
                    weakness_mitigation=prep_data.get("weakness_mitigation", {})
                )
                
                db.add(prep)
                db.commit()
                
                self.info(f"Generated interview prep for application {application.id}")
        
        except Exception as e:
            self.error(f"Failed to generate interview prep: {e}")


async def run_tracker_agent() -> Dict[str, Any]:
    """Entry point for scheduler."""
    agent = TrackerAgent()
    return await agent.run()

"""
Gmail API integration for email tracking.
"""
import os
import json
import base64
import logging
from typing import Optional, List, Dict, Any
from datetime import datetime, timedelta
from email.mime.text import MIMEText

from google.oauth2.credentials import Credentials
from google.auth.transport.requests import Request
from google_auth_oauthlib.flow import InstalledAppFlow
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError

from config import get_settings

logger = logging.getLogger(__name__)
settings = get_settings()

# Gmail API scopes
SCOPES = [
    'https://www.googleapis.com/auth/gmail.readonly',
    'https://www.googleapis.com/auth/gmail.modify',
    'https://www.googleapis.com/auth/gmail.labels'
]


class GmailService:
    """Service for interacting with Gmail API."""
    
    def __init__(self, credentials_path: str = "credentials.json", token_path: str = "token.json"):
        self.credentials_path = credentials_path
        self.token_path = token_path
        self.service = None
        self.credentials = None
    
    def authenticate(self) -> bool:
        """
        Authenticate with Gmail API using OAuth2.
        Returns True if authentication successful.
        """
        try:
            # Check for existing token
            if os.path.exists(self.token_path):
                self.credentials = Credentials.from_authorized_user_file(
                    self.token_path, SCOPES
                )
            
            # Refresh or get new credentials
            if not self.credentials or not self.credentials.valid:
                if self.credentials and self.credentials.expired and self.credentials.refresh_token:
                    self.credentials.refresh(Request())
                else:
                    if not os.path.exists(self.credentials_path):
                        logger.error(f"Credentials file not found: {self.credentials_path}")
                        return False
                    
                    flow = InstalledAppFlow.from_client_secrets_file(
                        self.credentials_path, SCOPES
                    )
                    self.credentials = flow.run_local_server(port=0)
                
                # Save credentials
                with open(self.token_path, 'w') as token:
                    token.write(self.credentials.to_json())
            
            # Build service
            self.service = build('gmail', 'v1', credentials=self.credentials)
            logger.info("Gmail API authenticated successfully")
            return True
            
        except Exception as e:
            logger.error(f"Gmail authentication failed: {e}")
            return False
    
    def is_authenticated(self) -> bool:
        """Check if service is authenticated."""
        return self.service is not None
    
    def search_emails(
        self,
        query: str,
        max_results: int = 100,
        after_date: Optional[datetime] = None
    ) -> List[Dict[str, Any]]:
        """
        Search emails matching the query.
        
        Args:
            query: Gmail search query (e.g., "from:greenhouse.io")
            max_results: Maximum number of results
            after_date: Only return emails after this date
        
        Returns:
            List of email metadata dictionaries
        """
        if not self.service:
            raise RuntimeError("Gmail service not authenticated")
        
        try:
            # Add date filter if specified
            if after_date:
                date_str = after_date.strftime("%Y/%m/%d")
                query = f"{query} after:{date_str}"
            
            # Search for messages
            results = self.service.users().messages().list(
                userId='me',
                q=query,
                maxResults=max_results
            ).execute()
            
            messages = results.get('messages', [])
            emails = []
            
            for msg in messages:
                email_data = self.get_email(msg['id'])
                if email_data:
                    emails.append(email_data)
            
            logger.info(f"Found {len(emails)} emails matching query: {query}")
            return emails
            
        except HttpError as e:
            logger.error(f"Gmail API error: {e}")
            return []
    
    def get_email(self, message_id: str) -> Optional[Dict[str, Any]]:
        """
        Get full email content by message ID.
        """
        if not self.service:
            raise RuntimeError("Gmail service not authenticated")
        
        try:
            message = self.service.users().messages().get(
                userId='me',
                id=message_id,
                format='full'
            ).execute()
            
            # Parse headers
            headers = {h['name'].lower(): h['value'] for h in message['payload']['headers']}
            
            # Extract body
            body_text = ""
            body_html = ""
            
            if 'parts' in message['payload']:
                for part in message['payload']['parts']:
                    if part['mimeType'] == 'text/plain':
                        body_text = self._decode_body(part.get('body', {}).get('data', ''))
                    elif part['mimeType'] == 'text/html':
                        body_html = self._decode_body(part.get('body', {}).get('data', ''))
            else:
                body_data = message['payload'].get('body', {}).get('data', '')
                if message['payload'].get('mimeType') == 'text/html':
                    body_html = self._decode_body(body_data)
                else:
                    body_text = self._decode_body(body_data)
            
            # Parse date
            date_str = headers.get('date', '')
            received_at = None
            try:
                from email.utils import parsedate_to_datetime
                received_at = parsedate_to_datetime(date_str)
            except:
                pass
            
            return {
                'gmail_id': message['id'],
                'thread_id': message['threadId'],
                'subject': headers.get('subject', ''),
                'sender': headers.get('from', ''),
                'sender_email': self._extract_email(headers.get('from', '')),
                'to': headers.get('to', ''),
                'date': date_str,
                'received_at': received_at,
                'body_text': body_text,
                'body_html': body_html,
                'labels': message.get('labelIds', []),
                'snippet': message.get('snippet', '')
            }
            
        except HttpError as e:
            logger.error(f"Failed to get email {message_id}: {e}")
            return None
    
    def _decode_body(self, data: str) -> str:
        """Decode base64url encoded email body."""
        if not data:
            return ""
        try:
            return base64.urlsafe_b64decode(data).decode('utf-8')
        except:
            return ""
    
    def _extract_email(self, from_header: str) -> str:
        """Extract email address from 'From' header."""
        import re
        match = re.search(r'<(.+?)>', from_header)
        if match:
            return match.group(1)
        return from_header
    
    def get_job_related_emails(
        self,
        days_back: int = 7,
        max_results: int = 50
    ) -> List[Dict[str, Any]]:
        """
        Get emails likely related to job applications.
        """
        after_date = datetime.utcnow() - timedelta(days=days_back)
        
        # Common job-related email patterns
        queries = [
            "from:greenhouse.io",
            "from:lever.co",
            "from:linkedin.com",
            "from:workday.com",
            "from:icims.com",
            "from:taleo.net",
            "from:smartrecruiters.com",
            "subject:(application OR interview OR position OR opportunity)",
            "subject:(thank you for applying OR we received your application)",
            "subject:(schedule interview OR phone screen)",
            "subject:(unfortunately OR regret OR not moving forward)"
        ]
        
        all_emails = []
        seen_ids = set()
        
        for query in queries:
            emails = self.search_emails(
                query=query,
                max_results=max_results // len(queries),
                after_date=after_date
            )
            
            for email in emails:
                if email['gmail_id'] not in seen_ids:
                    seen_ids.add(email['gmail_id'])
                    all_emails.append(email)
        
        # Sort by date descending
        all_emails.sort(
            key=lambda x: x.get('received_at') or datetime.min,
            reverse=True
        )
        
        return all_emails[:max_results]
    
    def add_label(self, message_id: str, label_name: str) -> bool:
        """Add a label to an email."""
        if not self.service:
            raise RuntimeError("Gmail service not authenticated")
        
        try:
            # Get or create label
            label_id = self._get_or_create_label(label_name)
            if not label_id:
                return False
            
            self.service.users().messages().modify(
                userId='me',
                id=message_id,
                body={'addLabelIds': [label_id]}
            ).execute()
            
            return True
            
        except HttpError as e:
            logger.error(f"Failed to add label: {e}")
            return False
    
    def _get_or_create_label(self, label_name: str) -> Optional[str]:
        """Get label ID or create if doesn't exist."""
        try:
            # List existing labels
            results = self.service.users().labels().list(userId='me').execute()
            labels = results.get('labels', [])
            
            for label in labels:
                if label['name'].lower() == label_name.lower():
                    return label['id']
            
            # Create new label
            label_body = {
                'name': label_name,
                'labelListVisibility': 'labelShow',
                'messageListVisibility': 'show'
            }
            
            created = self.service.users().labels().create(
                userId='me',
                body=label_body
            ).execute()
            
            return created['id']
            
        except HttpError as e:
            logger.error(f"Failed to get/create label: {e}")
            return None
    
    def mark_as_read(self, message_id: str) -> bool:
        """Mark an email as read."""
        if not self.service:
            raise RuntimeError("Gmail service not authenticated")
        
        try:
            self.service.users().messages().modify(
                userId='me',
                id=message_id,
                body={'removeLabelIds': ['UNREAD']}
            ).execute()
            return True
        except HttpError as e:
            logger.error(f"Failed to mark as read: {e}")
            return False


# Job application email search patterns
JOB_EMAIL_SENDERS = [
    # ATS/Job boards
    "greenhouse.io",
    "lever.co",
    "linkedin.com",
    "indeed.com",
    "workday.com",
    "icims.com",
    "taleo.net",
    "smartrecruiters.com",
    "jobvite.com",
    "bamboohr.com",
    "successfactors.com",
    "oracle.com",
    "myworkday.com",
    "ashbyhq.com",
    "wellfound.com",
    "angel.co",
    # Common noreply patterns
    "noreply",
    "no-reply",
    "recruiting",
    "careers",
    "talent",
    "hr@",
    "jobs@"
]

JOB_EMAIL_SUBJECTS = [
    "application",
    "interview",
    "position",
    "opportunity",
    "thank you for applying",
    "we received your application",
    "schedule",
    "phone screen",
    "next steps",
    "unfortunately",
    "regret",
    "not moving forward",
    "offer",
    "onboarding"
]


def get_gmail_service() -> GmailService:
    """Factory function to get Gmail service instance."""
    service = GmailService()
    return service

"""
Scout Agent - Discovers and collects job postings.
"""
import asyncio
import logging
from datetime import datetime
from typing import Dict, Any, List, Optional
from urllib.parse import urljoin, urlparse

from playwright.async_api import async_playwright, Browser, Page
from bs4 import BeautifulSoup
import httpx

from agents.base_agent import BaseAgent
from database.database import get_db_context
from database import crud
from database.models import Job, Company, JobStatus, CompanyPreference
from config import get_settings

logger = logging.getLogger(__name__)
settings = get_settings()


class JobScraper:
    """Base class for job scrapers."""
    
    def __init__(self):
        self.browser: Optional[Browser] = None
    
    async def init_browser(self):
        """Initialize Playwright browser."""
        playwright = await async_playwright().start()
        self.browser = await playwright.chromium.launch(headless=True)
    
    async def close_browser(self):
        """Close browser."""
        if self.browser:
            await self.browser.close()
    
    async def scrape(self, company: Company) -> List[Dict[str, Any]]:
        """Scrape jobs from a company. Override in subclasses."""
        raise NotImplementedError


class LinkedInJobScraper(JobScraper):
    """
    LinkedIn job scraper.
    Note: LinkedIn has strict anti-bot measures. This is a simplified implementation.
    For production, consider using official LinkedIn APIs or alternative job boards.
    """
    
    BASE_URL = "https://www.linkedin.com/jobs/search/"
    
    async def scrape(
        self,
        company_name: str,
        keywords: Optional[List[str]] = None,
        location: Optional[str] = None,
        max_jobs: int = 25
    ) -> List[Dict[str, Any]]:
        """
        Scrape LinkedIn jobs for a company.
        
        Note: This uses the public job search page which doesn't require auth
        but has limited results.
        """
        jobs = []
        
        try:
            # Build search URL
            params = {
                "keywords": f"{company_name} " + " ".join(keywords or []),
                "location": location or "United States",
                "f_TPR": "r86400"  # Posted in last 24 hours
            }
            
            query_string = "&".join(f"{k}={v}" for k, v in params.items())
            url = f"{self.BASE_URL}?{query_string}"
            
            async with httpx.AsyncClient() as client:
                response = await client.get(
                    url,
                    headers={
                        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                    },
                    follow_redirects=True
                )
                
                if response.status_code != 200:
                    logger.warning(f"LinkedIn returned status {response.status_code}")
                    return jobs
                
                soup = BeautifulSoup(response.text, 'html.parser')
                
                # Parse job cards (LinkedIn's public page structure)
                job_cards = soup.find_all('div', class_='base-card')
                
                for card in job_cards[:max_jobs]:
                    try:
                        job = self._parse_job_card(card)
                        if job:
                            job["source"] = "linkedin"
                            jobs.append(job)
                    except Exception as e:
                        logger.debug(f"Failed to parse job card: {e}")
                
        except Exception as e:
            logger.error(f"LinkedIn scraping error: {e}")
        
        return jobs
    
    def _parse_job_card(self, card) -> Optional[Dict[str, Any]]:
        """Parse a LinkedIn job card."""
        try:
            title_elem = card.find('h3', class_='base-search-card__title')
            company_elem = card.find('h4', class_='base-search-card__subtitle')
            location_elem = card.find('span', class_='job-search-card__location')
            link_elem = card.find('a', class_='base-card__full-link')
            
            if not title_elem or not link_elem:
                return None
            
            return {
                "title": title_elem.get_text(strip=True),
                "company_name": company_elem.get_text(strip=True) if company_elem else "",
                "location": location_elem.get_text(strip=True) if location_elem else "",
                "url": link_elem.get('href', ''),
                "posted_date": datetime.utcnow()
            }
        except Exception:
            return None


class GreenhouseJobScraper(JobScraper):
    """Scraper for Greenhouse job boards."""
    
    async def scrape(
        self,
        board_token: str,
        departments: Optional[List[str]] = None
    ) -> List[Dict[str, Any]]:
        """
        Scrape Greenhouse job board.
        
        Args:
            board_token: The company's Greenhouse board token
            departments: Optional list of department names to filter
        """
        jobs = []
        api_url = f"https://boards-api.greenhouse.io/v1/boards/{board_token}/jobs"
        
        try:
            async with httpx.AsyncClient() as client:
                response = await client.get(api_url)
                
                if response.status_code != 200:
                    logger.warning(f"Greenhouse API returned {response.status_code}")
                    return jobs
                
                data = response.json()
                
                for job_data in data.get("jobs", []):
                    # Filter by department if specified
                    if departments:
                        job_dept = job_data.get("departments", [{}])[0].get("name", "")
                        if not any(d.lower() in job_dept.lower() for d in departments):
                            continue
                    
                    jobs.append({
                        "external_id": str(job_data.get("id")),
                        "title": job_data.get("title"),
                        "company_name": board_token,  # Will be enriched later
                        "location": job_data.get("location", {}).get("name", ""),
                        "url": job_data.get("absolute_url", ""),
                        "posted_date": job_data.get("updated_at"),
                        "source": "greenhouse"
                    })
                
        except Exception as e:
            logger.error(f"Greenhouse scraping error: {e}")
        
        return jobs


class LeverJobScraper(JobScraper):
    """Scraper for Lever job boards."""
    
    async def scrape(self, company_slug: str) -> List[Dict[str, Any]]:
        """
        Scrape Lever job board.
        
        Args:
            company_slug: The company's Lever URL slug
        """
        jobs = []
        api_url = f"https://api.lever.co/v0/postings/{company_slug}?mode=json"
        
        try:
            async with httpx.AsyncClient() as client:
                response = await client.get(api_url)
                
                if response.status_code != 200:
                    logger.warning(f"Lever API returned {response.status_code}")
                    return jobs
                
                data = response.json()
                
                for job_data in data:
                    jobs.append({
                        "external_id": job_data.get("id"),
                        "title": job_data.get("text"),
                        "company_name": company_slug,
                        "location": job_data.get("categories", {}).get("location", ""),
                        "url": job_data.get("hostedUrl", ""),
                        "description": job_data.get("descriptionPlain", ""),
                        "posted_date": None,  # Lever doesn't expose this in the basic API
                        "source": "lever"
                    })
                
        except Exception as e:
            logger.error(f"Lever scraping error: {e}")
        
        return jobs


class CompanyCareersScraper(JobScraper):
    """Generic scraper for company career pages."""
    
    # Common career page patterns
    CAREER_PATH_PATTERNS = [
        "/careers",
        "/jobs",
        "/careers/search",
        "/job-openings",
        "/open-positions"
    ]
    
    async def scrape(
        self,
        company_url: str,
        keywords: Optional[List[str]] = None
    ) -> List[Dict[str, Any]]:
        """
        Scrape company's careers page.
        
        This is a generic scraper that attempts to find job listings
        on company websites. Results may vary based on site structure.
        """
        jobs = []
        
        if not self.browser:
            await self.init_browser()
        
        try:
            page = await self.browser.new_page()
            
            # Try different career paths
            for path in self.CAREER_PATH_PATTERNS:
                career_url = urljoin(company_url, path)
                
                try:
                    await page.goto(career_url, timeout=30000)
                    await page.wait_for_load_state('networkidle')
                    
                    # Check if we landed on a valid careers page
                    content = await page.content()
                    if "careers" in content.lower() or "jobs" in content.lower():
                        # Extract jobs from page
                        page_jobs = await self._extract_jobs_from_page(page, company_url)
                        jobs.extend(page_jobs)
                        break
                        
                except Exception as e:
                    logger.debug(f"Failed to access {career_url}: {e}")
                    continue
            
            await page.close()
            
        except Exception as e:
            logger.error(f"Company careers scraping error: {e}")
        
        return jobs
    
    async def _extract_jobs_from_page(
        self,
        page: Page,
        company_url: str
    ) -> List[Dict[str, Any]]:
        """Extract job listings from a careers page."""
        jobs = []
        
        # Common job listing selectors
        selectors = [
            "a[href*='/job']",
            "a[href*='/position']",
            "a[href*='/career']",
            ".job-listing a",
            ".careers-listing a",
            "[class*='job'] a",
            "[class*='position'] a"
        ]
        
        for selector in selectors:
            try:
                elements = await page.query_selector_all(selector)
                
                for elem in elements[:50]:  # Limit to prevent overload
                    try:
                        title = await elem.inner_text()
                        href = await elem.get_attribute('href')
                        
                        if title and href and len(title) > 3:
                            # Make URL absolute
                            if not href.startswith('http'):
                                href = urljoin(company_url, href)
                            
                            jobs.append({
                                "title": title.strip()[:500],
                                "company_name": urlparse(company_url).netloc,
                                "url": href,
                                "source": "company_careers"
                            })
                    except:
                        continue
                
                if jobs:
                    break
                    
            except Exception as e:
                logger.debug(f"Selector {selector} failed: {e}")
        
        return jobs


class ScoutAgent(BaseAgent):
    """
    Scout Agent - Discovers new job postings.
    
    Responsibilities:
    - Scrape job boards and company career pages
    - Filter by target companies
    - Save new jobs to database
    - Avoid duplicates
    """
    
    def __init__(self):
        super().__init__("scout_agent")
        self.linkedin_scraper = LinkedInJobScraper()
        self.greenhouse_scraper = GreenhouseJobScraper()
        self.lever_scraper = LeverJobScraper()
        self.careers_scraper = CompanyCareersScraper()
    
    async def execute(self) -> Dict[str, Any]:
        """Execute job discovery."""
        new_jobs_found = 0
        duplicates_skipped = 0
        companies_processed = 0
        
        with get_db_context() as db:
            # Get target companies (allowed or priority)
            companies = crud.get_allowed_companies(db)
            profile = crud.get_profile(db)
            
            if not profile:
                self.warning("No profile found. Skipping job search.")
                return {"new_jobs": 0, "message": "No profile configured"}
            
            desired_roles = profile.desired_roles or []
            
            for company in companies:
                companies_processed += 1
                self.info(f"Searching jobs at {company.name}")
                
                try:
                    jobs = []
                    
                    # Try different sources based on company configuration
                    if company.careers_page:
                        if "greenhouse.io" in company.careers_page:
                            # Extract board token from URL
                            board_token = company.careers_page.split("/")[-1]
                            jobs = await self.greenhouse_scraper.scrape(board_token)
                        elif "lever.co" in company.careers_page:
                            # Extract company slug
                            slug = company.careers_page.split("/")[-1]
                            jobs = await self.lever_scraper.scrape(slug)
                        else:
                            # Generic careers page
                            jobs = await self.careers_scraper.scrape(company.careers_page)
                    
                    # Also search LinkedIn if no jobs found
                    if not jobs:
                        jobs = await self.linkedin_scraper.scrape(
                            company_name=company.name,
                            keywords=desired_roles[:3] if desired_roles else None,
                            max_jobs=10
                        )
                    
                    # Process found jobs
                    for job_data in jobs:
                        # Check for duplicates
                        existing = crud.get_job_by_url(db, job_data.get("url", ""))
                        if existing:
                            duplicates_skipped += 1
                            continue
                        
                        # Create job entry
                        job_data["company_id"] = company.id
                        job_data["company_name"] = company.name
                        job_data["status"] = JobStatus.NEW
                        job_data["discovered_at"] = datetime.utcnow()
                        
                        crud.create_job(db, job_data)
                        new_jobs_found += 1
                        self.record_success()
                    
                except Exception as e:
                    self.error(f"Failed to scrape {company.name}: {e}")
                    self.record_failure()
                
                # Small delay between companies
                await asyncio.sleep(2)
        
        # Cleanup
        await self.careers_scraper.close_browser()
        
        return {
            "new_jobs": new_jobs_found,
            "duplicates_skipped": duplicates_skipped,
            "companies_processed": companies_processed
        }


async def run_scout_agent() -> Dict[str, Any]:
    """Entry point for scheduler."""
    agent = ScoutAgent()
    return await agent.run()

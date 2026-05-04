"""
Task scheduler for automated agent execution.
"""
import logging
from datetime import datetime
from typing import Callable, Optional, Dict, Any
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.interval import IntervalTrigger
from apscheduler.triggers.cron import CronTrigger
from apscheduler.events import EVENT_JOB_ERROR, EVENT_JOB_EXECUTED

from config import get_settings

logger = logging.getLogger(__name__)
settings = get_settings()


class AgentScheduler:
    """Scheduler for running agents on defined intervals."""
    
    def __init__(self):
        self.scheduler = AsyncIOScheduler()
        self.jobs: Dict[str, str] = {}  # job_name -> job_id mapping
        self._setup_event_listeners()
    
    def _setup_event_listeners(self):
        """Setup event listeners for job execution."""
        def job_executed_listener(event):
            logger.info(f"Job {event.job_id} executed successfully")
        
        def job_error_listener(event):
            logger.error(f"Job {event.job_id} failed with exception: {event.exception}")
        
        self.scheduler.add_listener(job_executed_listener, EVENT_JOB_EXECUTED)
        self.scheduler.add_listener(job_error_listener, EVENT_JOB_ERROR)
    
    def start(self):
        """Start the scheduler."""
        if not self.scheduler.running:
            self.scheduler.start()
            logger.info("Agent scheduler started")
    
    def stop(self):
        """Stop the scheduler."""
        if self.scheduler.running:
            self.scheduler.shutdown()
            logger.info("Agent scheduler stopped")
    
    def add_interval_job(
        self,
        func: Callable,
        job_name: str,
        hours: int = 0,
        minutes: int = 0,
        seconds: int = 0,
        start_immediately: bool = False
    ) -> str:
        """
        Add a job that runs at fixed intervals.
        
        Args:
            func: The function to execute
            job_name: Unique name for the job
            hours: Hours between executions
            minutes: Minutes between executions
            seconds: Seconds between executions
            start_immediately: Whether to run immediately on startup
        
        Returns:
            Job ID
        """
        trigger = IntervalTrigger(
            hours=hours,
            minutes=minutes,
            seconds=seconds
        )
        
        job = self.scheduler.add_job(
            func,
            trigger=trigger,
            id=job_name,
            name=job_name,
            replace_existing=True,
            max_instances=1,  # Prevent overlapping runs
            coalesce=True  # Combine missed runs
        )
        
        self.jobs[job_name] = job.id
        logger.info(f"Scheduled job '{job_name}' to run every {hours}h {minutes}m {seconds}s")
        
        # Run immediately if requested
        if start_immediately:
            self.scheduler.add_job(
                func,
                id=f"{job_name}_immediate",
                name=f"{job_name}_immediate"
            )
        
        return job.id
    
    def add_cron_job(
        self,
        func: Callable,
        job_name: str,
        hour: Optional[int] = None,
        minute: Optional[int] = None,
        day_of_week: Optional[str] = None
    ) -> str:
        """
        Add a job that runs on a cron schedule.
        
        Args:
            func: The function to execute
            job_name: Unique name for the job
            hour: Hour to run (0-23)
            minute: Minute to run (0-59)
            day_of_week: Days to run (e.g., "mon-fri")
        
        Returns:
            Job ID
        """
        trigger = CronTrigger(
            hour=hour,
            minute=minute,
            day_of_week=day_of_week
        )
        
        job = self.scheduler.add_job(
            func,
            trigger=trigger,
            id=job_name,
            name=job_name,
            replace_existing=True,
            max_instances=1
        )
        
        self.jobs[job_name] = job.id
        logger.info(f"Scheduled cron job '{job_name}'")
        
        return job.id
    
    def remove_job(self, job_name: str) -> bool:
        """Remove a scheduled job."""
        if job_name in self.jobs:
            try:
                self.scheduler.remove_job(self.jobs[job_name])
                del self.jobs[job_name]
                logger.info(f"Removed job '{job_name}'")
                return True
            except Exception as e:
                logger.error(f"Failed to remove job '{job_name}': {e}")
                return False
        return False
    
    def pause_job(self, job_name: str) -> bool:
        """Pause a scheduled job."""
        if job_name in self.jobs:
            try:
                self.scheduler.pause_job(self.jobs[job_name])
                logger.info(f"Paused job '{job_name}'")
                return True
            except Exception as e:
                logger.error(f"Failed to pause job '{job_name}': {e}")
                return False
        return False
    
    def resume_job(self, job_name: str) -> bool:
        """Resume a paused job."""
        if job_name in self.jobs:
            try:
                self.scheduler.resume_job(self.jobs[job_name])
                logger.info(f"Resumed job '{job_name}'")
                return True
            except Exception as e:
                logger.error(f"Failed to resume job '{job_name}': {e}")
                return False
        return False
    
    def run_job_now(self, job_name: str) -> bool:
        """Trigger immediate execution of a job."""
        if job_name in self.jobs:
            try:
                job = self.scheduler.get_job(self.jobs[job_name])
                if job:
                    job.modify(next_run_time=datetime.now())
                    logger.info(f"Triggered immediate run of '{job_name}'")
                    return True
            except Exception as e:
                logger.error(f"Failed to trigger job '{job_name}': {e}")
                return False
        return False
    
    def get_job_status(self, job_name: str) -> Optional[Dict[str, Any]]:
        """Get status of a scheduled job."""
        if job_name in self.jobs:
            try:
                job = self.scheduler.get_job(self.jobs[job_name])
                if job:
                    return {
                        "id": job.id,
                        "name": job.name,
                        "next_run": job.next_run_time.isoformat() if job.next_run_time else None,
                        "paused": job.next_run_time is None
                    }
            except Exception as e:
                logger.error(f"Failed to get job status for '{job_name}': {e}")
        return None
    
    def get_all_jobs_status(self) -> Dict[str, Any]:
        """Get status of all scheduled jobs."""
        statuses = {}
        for job_name in self.jobs:
            status = self.get_job_status(job_name)
            if status:
                statuses[job_name] = status
        return statuses


def setup_agent_schedules(scheduler: AgentScheduler):
    """
    Setup default agent schedules based on configuration.
    This should be called after agent functions are available.
    """
    from agents.scout_agent import run_scout_agent
    from agents.analyst_agent import run_analyst_agent
    from agents.applicant_agent import run_applicant_agent
    from agents.tracker_agent import run_tracker_agent
    
    # Scout Agent - Find new jobs
    scheduler.add_interval_job(
        func=run_scout_agent,
        job_name="scout_agent",
        hours=settings.scout_interval_hours
    )
    
    # Analyst Agent - Analyze new jobs
    scheduler.add_interval_job(
        func=run_analyst_agent,
        job_name="analyst_agent",
        minutes=settings.analyst_interval_minutes
    )
    
    # Applicant Agent - Auto-apply to matching jobs
    scheduler.add_interval_job(
        func=run_applicant_agent,
        job_name="applicant_agent",
        minutes=settings.applicant_interval_minutes
    )
    
    # Tracker Agent - Sync with email
    scheduler.add_interval_job(
        func=run_tracker_agent,
        job_name="tracker_agent",
        minutes=settings.tracker_interval_minutes
    )
    
    logger.info("All agent schedules configured")


# Singleton scheduler instance
_scheduler: Optional[AgentScheduler] = None


def get_scheduler() -> AgentScheduler:
    """Get or create scheduler singleton."""
    global _scheduler
    if _scheduler is None:
        _scheduler = AgentScheduler()
    return _scheduler

"""
Base agent class for all automation agents.
"""
import logging
from abc import ABC, abstractmethod
from datetime import datetime
from typing import Optional, Dict, Any, List

from database.database import get_db_context
from database import crud
from database.models import AgentRun

logger = logging.getLogger(__name__)


class BaseAgent(ABC):
    """Abstract base class for all agents."""
    
    def __init__(self, name: str):
        self.name = name
        self.current_run: Optional[AgentRun] = None
        self.log_entries: List[Dict[str, Any]] = []
        self.items_processed = 0
        self.items_succeeded = 0
        self.items_failed = 0
        self.api_calls = 0
        self.tokens_used = 0
        self.estimated_cost = 0.0
    
    def _log(self, level: str, message: str, **kwargs):
        """Add a structured log entry."""
        entry = {
            "timestamp": datetime.utcnow().isoformat(),
            "level": level,
            "message": message,
            **kwargs
        }
        self.log_entries.append(entry)
        
        log_func = getattr(logger, level.lower(), logger.info)
        log_func(f"[{self.name}] {message}")
    
    def info(self, message: str, **kwargs):
        """Log info message."""
        self._log("INFO", message, **kwargs)
    
    def warning(self, message: str, **kwargs):
        """Log warning message."""
        self._log("WARNING", message, **kwargs)
    
    def error(self, message: str, **kwargs):
        """Log error message."""
        self._log("ERROR", message, **kwargs)
    
    def record_api_call(self, tokens: int, cost: float):
        """Record an API call for tracking."""
        self.api_calls += 1
        self.tokens_used += tokens
        self.estimated_cost += cost
    
    def record_success(self):
        """Record a successful item processing."""
        self.items_processed += 1
        self.items_succeeded += 1
    
    def record_failure(self):
        """Record a failed item processing."""
        self.items_processed += 1
        self.items_failed += 1
    
    def _start_run(self):
        """Start a new agent run and create database record."""
        self.log_entries = []
        self.items_processed = 0
        self.items_succeeded = 0
        self.items_failed = 0
        self.api_calls = 0
        self.tokens_used = 0
        self.estimated_cost = 0.0
        
        with get_db_context() as db:
            self.current_run = crud.create_agent_run(db, self.name)
        
        self.info("Agent run started")
    
    def _complete_run(self, status: str = "completed", error_message: Optional[str] = None):
        """Complete the current agent run and update database."""
        if self.current_run:
            with get_db_context() as db:
                crud.complete_agent_run(
                    db=db,
                    run_id=self.current_run.id,
                    status=status,
                    items_processed=self.items_processed,
                    items_succeeded=self.items_succeeded,
                    items_failed=self.items_failed,
                    api_calls=self.api_calls,
                    tokens_used=self.tokens_used,
                    estimated_cost=self.estimated_cost,
                    error_message=error_message
                )
        
        self.info(
            f"Agent run completed: {status}",
            processed=self.items_processed,
            succeeded=self.items_succeeded,
            failed=self.items_failed,
            cost=f"${self.estimated_cost:.4f}"
        )
    
    @abstractmethod
    async def execute(self) -> Dict[str, Any]:
        """
        Execute the agent's main task.
        
        Returns:
            Dict with execution results
        """
        pass
    
    async def run(self) -> Dict[str, Any]:
        """
        Run the agent with proper lifecycle management.
        """
        self._start_run()
        
        try:
            result = await self.execute()
            self._complete_run("completed")
            return {
                "status": "completed",
                "agent": self.name,
                "result": result,
                "stats": {
                    "items_processed": self.items_processed,
                    "items_succeeded": self.items_succeeded,
                    "items_failed": self.items_failed,
                    "api_calls": self.api_calls,
                    "tokens_used": self.tokens_used,
                    "estimated_cost": self.estimated_cost
                }
            }
            
        except Exception as e:
            error_message = str(e)
            self.error(f"Agent execution failed: {error_message}")
            self._complete_run("failed", error_message)
            return {
                "status": "failed",
                "agent": self.name,
                "error": error_message,
                "stats": {
                    "items_processed": self.items_processed,
                    "items_succeeded": self.items_succeeded,
                    "items_failed": self.items_failed
                }
            }


class AgentOrchestrator:
    """Orchestrates multiple agents and manages dependencies."""
    
    def __init__(self):
        self.agents: Dict[str, BaseAgent] = {}
    
    def register_agent(self, agent: BaseAgent):
        """Register an agent."""
        self.agents[agent.name] = agent
        logger.info(f"Registered agent: {agent.name}")
    
    async def run_agent(self, agent_name: str) -> Dict[str, Any]:
        """Run a specific agent by name."""
        if agent_name not in self.agents:
            raise ValueError(f"Unknown agent: {agent_name}")
        
        agent = self.agents[agent_name]
        return await agent.run()
    
    async def run_pipeline(self, agent_names: List[str]) -> List[Dict[str, Any]]:
        """Run multiple agents in sequence."""
        results = []
        for name in agent_names:
            result = await self.run_agent(name)
            results.append(result)
            
            # Stop pipeline if agent fails
            if result["status"] == "failed":
                logger.warning(f"Pipeline stopped due to {name} failure")
                break
        
        return results

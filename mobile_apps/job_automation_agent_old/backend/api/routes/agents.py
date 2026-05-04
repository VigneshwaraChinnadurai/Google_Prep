"""
Agents management API routes.
"""
from typing import Optional
from fastapi import APIRouter, HTTPException, Depends
from pydantic import BaseModel
from sqlalchemy.orm import Session
import asyncio

from database.database import get_db
from database import crud
from services.scheduler import get_scheduler

router = APIRouter()


class AgentTrigger(BaseModel):
    """Agent trigger request."""
    force: bool = False  # Force run even if recently executed


@router.get("/status")
async def get_agents_status():
    """Get status of all scheduled agents."""
    scheduler = get_scheduler()
    return scheduler.get_all_jobs_status()


@router.get("/runs")
async def get_recent_runs(
    agent_name: Optional[str] = None,
    limit: int = 20,
    db: Session = Depends(get_db)
):
    """Get recent agent execution history."""
    runs = crud.get_recent_agent_runs(db, agent_name=agent_name, limit=limit)
    return {
        "runs": [run.to_dict() for run in runs],
        "count": len(runs)
    }


@router.post("/scout/run")
async def run_scout_agent(trigger: AgentTrigger = AgentTrigger()):
    """Manually trigger Scout Agent."""
    from agents.scout_agent import run_scout_agent
    
    try:
        result = await run_scout_agent()
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/analyst/run")
async def run_analyst_agent(trigger: AgentTrigger = AgentTrigger()):
    """Manually trigger Analyst Agent."""
    from agents.analyst_agent import run_analyst_agent
    
    try:
        result = await run_analyst_agent()
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/applicant/run")
async def run_applicant_agent(trigger: AgentTrigger = AgentTrigger()):
    """Manually trigger Applicant Agent."""
    from agents.applicant_agent import run_applicant_agent
    
    try:
        result = await run_applicant_agent()
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/tracker/run")
async def run_tracker_agent(trigger: AgentTrigger = AgentTrigger()):
    """Manually trigger Tracker Agent."""
    from agents.tracker_agent import run_tracker_agent
    
    try:
        result = await run_tracker_agent()
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/pipeline/run")
async def run_full_pipeline():
    """Run complete agent pipeline: Scout -> Analyst -> Applicant -> Tracker."""
    from agents import (
        run_scout_agent,
        run_analyst_agent,
        run_applicant_agent,
        run_tracker_agent
    )
    
    results = []
    
    # Run agents in sequence
    agents = [
        ("scout", run_scout_agent),
        ("analyst", run_analyst_agent),
        ("applicant", run_applicant_agent),
        ("tracker", run_tracker_agent)
    ]
    
    for name, func in agents:
        try:
            result = await func()
            results.append({"agent": name, "result": result})
            
            # Stop pipeline on critical failure
            if result.get("status") == "failed":
                results.append({"message": f"Pipeline stopped at {name} due to failure"})
                break
                
        except Exception as e:
            results.append({"agent": name, "error": str(e)})
            break
    
    return {"pipeline_results": results}


@router.post("/{agent_name}/pause")
async def pause_agent(agent_name: str):
    """Pause a scheduled agent."""
    scheduler = get_scheduler()
    
    job_mapping = {
        "scout": "scout_agent",
        "analyst": "analyst_agent",
        "applicant": "applicant_agent",
        "tracker": "tracker_agent"
    }
    
    job_name = job_mapping.get(agent_name)
    if not job_name:
        raise HTTPException(status_code=404, detail=f"Unknown agent: {agent_name}")
    
    success = scheduler.pause_job(job_name)
    if success:
        return {"message": f"Agent {agent_name} paused"}
    else:
        raise HTTPException(status_code=500, detail="Failed to pause agent")


@router.post("/{agent_name}/resume")
async def resume_agent(agent_name: str):
    """Resume a paused agent."""
    scheduler = get_scheduler()
    
    job_mapping = {
        "scout": "scout_agent",
        "analyst": "analyst_agent",
        "applicant": "applicant_agent",
        "tracker": "tracker_agent"
    }
    
    job_name = job_mapping.get(agent_name)
    if not job_name:
        raise HTTPException(status_code=404, detail=f"Unknown agent: {agent_name}")
    
    success = scheduler.resume_job(job_name)
    if success:
        return {"message": f"Agent {agent_name} resumed"}
    else:
        raise HTTPException(status_code=500, detail="Failed to resume agent")


@router.post("/setup-schedules")
async def setup_agent_schedules():
    """Setup default agent schedules."""
    from services.scheduler import setup_agent_schedules as setup_schedules
    
    scheduler = get_scheduler()
    setup_schedules(scheduler)
    
    return {
        "message": "Agent schedules configured",
        "schedules": scheduler.get_all_jobs_status()
    }

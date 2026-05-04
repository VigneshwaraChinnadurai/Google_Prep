"""Job Finder CLI. Run with: python -m src.cli <command>"""
from __future__ import annotations
import asyncio
import typer
from rich.console import Console
from rich.table import Table
from src.orchestrator import Orchestrator

app = typer.Typer(add_completion=False, help="Job Finder pipeline CLI")
console = Console()


@app.command()
def parse(file_path: str):
    """Parse a resume PDF/DOCX and cache to data/profile.json."""
    async def _go():
        orch = Orchestrator()
        resume = await orch.parser.parse(file_path)
        orch.set_resume(resume)
        console.print(f"[green]Parsed:[/green] {resume.full_name} <{resume.email}>")
    asyncio.run(_go())


@app.command()
def scrape():
    """Scrape all companies in companies.yaml."""
    async def _go():
        orch = Orchestrator()
        async for name, count in orch.scrape_iter():
            console.print(f"  • {name}: {count} cumulative")
    asyncio.run(_go())


@app.command()
def match():
    """Score all cached jobs against the cached resume."""
    async def _go():
        orch = Orchestrator()
        async for done, total in orch.match_iter():
            console.print(f"  • {done}/{total}", end="\r")
        console.print()
    asyncio.run(_go())


@app.command(name="list-matches")
def list_matches(min_score: float = 0.5, limit: int = 20):
    """Show top matches above a fit-score threshold."""
    orch = Orchestrator()
    items = orch.list_matches(min_score=min_score, limit=limit)
    t = Table(title=f"Matches >= {min_score}")
    t.add_column("Score"); t.add_column("Title"); t.add_column("Company"); t.add_column("Status")
    for m in items:
        t.add_row(f"{m.fit_score:.2f}", m.job.title, m.job.company, m.status)
    console.print(t)


@app.command()
def competitions(tracked_only: bool = False, hiring_only: bool = False, refresh: bool = False):
    """List hiring/coding competitions across all platforms."""
    async def _go():
        orch = Orchestrator()
        if refresh:
            console.print("[cyan]Refreshing…[/cyan]")
            async for platform, count in orch.competitions_iter():
                console.print(f"  • {platform}: {count}")
        items = orch.list_competitions(tracked_only=tracked_only, hiring_only=hiring_only)
        t = Table(title=f"Competitions ({len(items)})")
        t.add_column("Platform"); t.add_column("Title"); t.add_column("Sponsor")
        t.add_column("Tracked?"); t.add_column("Hiring?")
        for c in items:
            t.add_row(
                c.platform, c.title[:60],
                c.matched_company or c.sponsor_company or "-",
                "✓" if c.is_tracked_company else "",
                "✓" if c.is_hiring else "",
            )
        console.print(t)
    asyncio.run(_go())


@app.command(name="run-all")
def run_all(file_path: str | None = None):
    """parse → scrape → match end-to-end."""
    async def _go():
        orch = Orchestrator()
        if file_path:
            resume = await orch.parser.parse(file_path)
            orch.set_resume(resume)
        async for name, count in orch.scrape_iter():
            console.print(f"  scrape • {name}: {count}")
        async for done, total in orch.match_iter():
            console.print(f"  match  • {done}/{total}", end="\r")
    asyncio.run(_go())


if __name__ == "__main__":
    app()

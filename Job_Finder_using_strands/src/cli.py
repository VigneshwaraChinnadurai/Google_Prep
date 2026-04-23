"""
CLI entrypoint.

Usage:
    python -m src.cli parse data/resume.pdf
    python -m src.cli scrape
    python -m src.cli match
    python -m src.cli list-matches
    python -m src.cli run-all data/resume.pdf   # full pipeline, no apply
"""
from __future__ import annotations

import argparse
import sys

from rich.console import Console
from rich.table import Table

from .agents import Orchestrator

console = Console()


def cmd_parse(args: argparse.Namespace) -> None:
    orch = Orchestrator()
    profile = orch.parse_resume(args.resume, use_cache=False)
    console.print(
        f"[green]✓[/green] Parsed [bold]{profile.full_name}[/bold] — "
        f"{profile.field or 'unknown field'}, "
        f"{profile.total_years_experience:.1f}y exp"
    )
    console.print(f"  Skills: {', '.join(s.name for s in profile.skills[:15])}")


def cmd_scrape(args: argparse.Namespace) -> None:
    orch = Orchestrator()
    console.print("[cyan]Scraping all configured companies...[/cyan]")
    jobs = orch.scrape_all(use_cache=False)
    by_company: dict[str, int] = {}
    for j in jobs:
        by_company[j.company] = by_company.get(j.company, 0) + 1

    table = Table(title=f"Scraped {len(jobs)} jobs")
    table.add_column("Company")
    table.add_column("Jobs", justify="right")
    for name, count in sorted(by_company.items(), key=lambda x: -x[1]):
        table.add_row(name, str(count))
    console.print(table)


def cmd_match(args: argparse.Namespace) -> None:
    orch = Orchestrator()
    # Load caches
    profile = orch.load_cached_profile()
    if not profile:
        console.print("[red]No profile cache. Run `parse` first.[/red]")
        sys.exit(1)
    orch._resume = profile

    # Load jobs
    orch.scrape_all(use_cache=True)
    if not orch._jobs:
        console.print("[red]No jobs cache. Run `scrape` first.[/red]")
        sys.exit(1)

    console.print(f"[cyan]Scoring {len(orch._jobs)} jobs...[/cyan]")
    with console.status("Scoring..."):
        matches = orch.match_all(threshold=args.threshold)

    _print_matches_table(matches)


def cmd_list_matches(args: argparse.Namespace) -> None:
    matches = Orchestrator.load_cached_matches()
    if not matches:
        console.print("[yellow]No cached matches.[/yellow]")
        return
    _print_matches_table(matches)


def cmd_run_all(args: argparse.Namespace) -> None:
    orch = Orchestrator()
    console.print("[cyan]1/3 Parsing resume...[/cyan]")
    profile = orch.parse_resume(args.resume, use_cache=False)
    console.print(f"  ✓ {profile.full_name}")

    console.print("[cyan]2/3 Scraping companies...[/cyan]")
    jobs = orch.scrape_all(use_cache=False)
    console.print(f"  ✓ {len(jobs)} jobs")

    console.print("[cyan]3/3 Scoring matches...[/cyan]")
    matches = orch.match_all(threshold=args.threshold)

    _print_matches_table(matches)
    console.print("\n[green]Done.[/green] Run `streamlit run src/ui/app.py` to approve & apply.")


def _print_matches_table(matches):
    if not matches:
        console.print("[yellow]No matches above threshold.[/yellow]")
        return
    table = Table(title=f"{len(matches)} matches")
    table.add_column("Fit", justify="right")
    table.add_column("Company")
    table.add_column("Title")
    table.add_column("Location")
    table.add_column("Recommendation")
    for m in matches[:40]:
        table.add_row(
            f"{int(m.fit_score * 100)}%",
            m.job.company,
            m.job.title[:60],
            m.job.location or "—",
            m.recommendation,
        )
    console.print(table)


def main():
    parser = argparse.ArgumentParser(prog="job-finder")
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_parse = sub.add_parser("parse", help="Parse a resume")
    p_parse.add_argument("resume", help="Path to resume PDF/DOCX/TXT")
    p_parse.set_defaults(func=cmd_parse)

    p_scrape = sub.add_parser("scrape", help="Scrape all configured companies")
    p_scrape.set_defaults(func=cmd_scrape)

    p_match = sub.add_parser("match", help="Score jobs against cached profile")
    p_match.add_argument("--threshold", type=float, default=None)
    p_match.set_defaults(func=cmd_match)

    p_list = sub.add_parser("list-matches", help="Show cached matches")
    p_list.set_defaults(func=cmd_list_matches)

    p_run = sub.add_parser("run-all", help="Full pipeline: parse + scrape + match")
    p_run.add_argument("resume", help="Path to resume")
    p_run.add_argument("--threshold", type=float, default=None)
    p_run.set_defaults(func=cmd_run_all)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()

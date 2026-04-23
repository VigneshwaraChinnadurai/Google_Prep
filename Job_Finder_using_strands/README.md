# Job Finder — Agentic Job Application Assistant

An agentic system built on **Strands Agents SDK** that reads your resume, scans the career portals of companies you shortlist, ranks job openings by skill-match, and lets you review + approve each application before it is semi-automatically filled in your browser.

## What it does

1. **Parses your resume** (PDF / DOCX / TXT) — extracts skills, experience, field, location preferences.
2. **Scans career portals** for your shortlisted companies (hybrid: API first, Playwright fallback).
3. **Matches jobs to your profile** — uses an LLM to score each JD against your resume.
4. **Presents approvable candidates** — a local Streamlit UI shows matched jobs with Approve / Skip buttons.
5. **Semi-auto applies** — on approval, opens the portal in a Playwright-controlled browser, pre-fills the application form from your profile, and hands off to you for the final submit click.

## Why Strands?

Strands gives us a model-driven agent loop with tool use, which fits this workflow cleanly:
- **Resume Parser Agent** — extracts structured profile from resume
- **Scraper Agent** — orchestrates portal scrapers (tools), retries, handles fallbacks
- **Matcher Agent** — scores and ranks jobs against profile
- **Applicator Agent** — navigates the portal and pre-fills fields

Strands is model-agnostic, so we use **Gemini 2.5 Flash** by default (~$0.30 / $2.50 per 1M in/out tokens — very cost-efficient for this workload), but you can swap to Claude Haiku, OpenAI, or Bedrock by changing one config line.

## Cost estimate

For a typical run (1 resume, 10 companies, ~50 JDs scanned):
- Resume parse: ~2K tokens in, ~1K out
- Per-JD match: ~2K in, ~300 out × 50 = 100K in, 15K out
- Total: ~105K in + 16K out ≈ **$0.075 per run with Gemini 2.5 Flash**

## Quick start

```powershell
# Windows PowerShell
cd "C:\Users\vichinnadurai\Documents\Vignesh\Personal Enrichment\Personal Work\Google_Prep\Job_Finder_using_strands"

# 1. Create a virtual environment
python -m venv .venv
.venv\Scripts\Activate.ps1

# 2. Install dependencies
pip install -r requirements.txt
playwright install chromium

# 3. Set up your .env file (copy the template)
copy .env.example .env
# Then edit .env and add your GEMINI_API_KEY (get it free at https://aistudio.google.com/apikey)

# 4. Drop your resume in data/
copy "path\to\your_resume.pdf" data\resume.pdf

# 5. Edit config/companies.yaml to list the companies you want to target

# 6. Run
streamlit run src/ui/app.py
```

## Project structure

```
job_finder/
├── src/
│   ├── agents/          # Strands agents (parser, scraper, matcher, applicator)
│   ├── tools/           # @tool functions the agents call
│   ├── models/          # Pydantic models for Resume, Job, MatchResult
│   ├── scrapers/        # Career portal scrapers (Greenhouse, Lever, Workday, generic)
│   └── ui/              # Streamlit review-and-approve UI
├── config/
│   ├── companies.yaml   # Companies + their career URLs
│   └── settings.yaml    # LLM provider, match threshold, etc.
├── data/
│   ├── resume.pdf       # Your resume goes here
│   ├── profile.json     # Parsed profile (cached)
│   └── matches.json     # Latest match results (cached)
└── docs/
    └── ARCHITECTURE.md
```

## ⚠️ Important notes

- **Respect robots.txt and ToS.** Many career sites restrict scraping. This tool is intended for personal use on your own shortlist. Don't run mass-scale scraping.
- **LinkedIn is off-limits** — its ToS explicitly forbids scraping. Use LinkedIn's own "Easy Apply" feature instead.
- **Semi-auto, not full auto.** The tool pre-fills the application form, but you must review and click Submit. This is deliberate — application quality matters, and you should personalize cover letters.

## License

Personal use. Not for commercial redistribution.

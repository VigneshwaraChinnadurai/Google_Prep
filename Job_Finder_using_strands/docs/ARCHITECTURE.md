# Architecture

## System diagram

```
                           ┌──────────────────┐
                           │   Streamlit UI   │
                           │ (review+approve) │
                           └────────┬─────────┘
                                    │
                                    ▼
                           ┌──────────────────┐
                           │   Orchestrator   │
                           └────────┬─────────┘
                   ┌────────────────┼────────────────┬──────────────────┐
                   ▼                ▼                ▼                  ▼
          ┌────────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐
          │ ResumeParser   │ │ ScraperAgent │ │ MatcherAgent │ │ ApplicatorAgent  │
          │ Agent          │ │              │ │              │ │                  │
          └───────┬────────┘ └──────┬───────┘ └──────┬───────┘ └────────┬─────────┘
                  │                 │                │                  │
                  ▼                 ▼                ▼                  ▼
          ┌────────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐
          │ pypdf/docx     │ │ Scrapers:    │ │ Structured   │ │ Playwright       │
          │ + LLM          │ │  - Greenhouse│ │ output vs    │ │ (visible browser)│
          │ structured_out │ │  - Lever     │ │ MatchResult  │ │ + form-field     │
          │                │ │  - Workday   │ │ schema       │ │ heuristics       │
          │                │ │  - Generic   │ │              │ │                  │
          │                │ │    (Playwright)│              │ │                  │
          └────────────────┘ └──────────────┘ └──────────────┘ └──────────────────┘

  Data caches (JSON on disk):
    data/profile.json      parsed resume
    data/jobs_cache.json   scraped jobs
    data/matches.json      scored + filtered matches
```

## Agent responsibilities

### ResumeParserAgent
- **Input:** Path to a PDF/DOCX/TXT resume
- **Output:** A `Resume` Pydantic model
- **How:** `read_resume_file` tool extracts text, then `Agent.structured_output(Resume, text)` forces the LLM to return JSON conforming to the `Resume` schema.
- **Cached** to `data/profile.json` so we don't re-parse on every run.

### ScraperAgent
- **Input:** Company configs from `config/companies.yaml`
- **Output:** A list of `Job` models
- **How:** For each company, picks the right scraper (Greenhouse/Lever/Workday API; Playwright for generic). Runs them concurrently up to the configured limit.
- **Fast path:** the bulk scraping bypasses the LLM entirely — we only use the agent loop for on-demand single-company scraping and JD detail fetches.

### MatcherAgent
- **Input:** A `Resume` + a list of `Job`s
- **Output:** A list of `MatchResult`s, filtered by threshold and sorted by `fit_score`.
- **How:** One LLM call per job, using `structured_output(MatchResult, ...)`. Each call is ~2-3K input tokens, ~400 output — the sweet spot for Gemini Flash on both cost and quality.
- **Why one-at-a-time, not batched?** Batching N jobs in one call dilutes per-job reasoning and often trips max-output-token limits. Doing them individually is also trivially parallelizable if we ever need more throughput.

### ApplicatorAgent
- **Input:** An approved `MatchResult`
- **Output:** A dict describing what was pre-filled
- **How:**
  1. Generate a tailored cover letter (~150-200 words) via the LLM.
  2. Launch a **visible** Chromium window via Playwright.
  3. Navigate to the job URL, click Apply if found.
  4. Map form fields to profile keys using regex heuristics over id/name/aria-label/label text.
  5. Fill text fields, upload the resume file, paste the cover letter.
  6. **Stop.** The user reviews and clicks Submit.

This is intentionally semi-auto. Reasons: ToS compliance, application quality, user control over final submission.

## Why Strands?

- **Model-agnostic:** swapping Gemini → Claude → OpenAI is a one-line config change in `src/config.py`.
- **Structured output is first-class:** `Agent.structured_output(MyPydanticModel, prompt)` handles schema-forcing, which is critical for our Resume/MatchResult extraction.
- **Tools are just Python functions:** `@tool`-decorated functions become agent-callable. No step definitions, no graph DSL — which keeps the codebase readable.
- **MCP-ready:** If we later want to expose this as an MCP server (so e.g. Claude Desktop can drive it), Strands has built-in MCP support.

## Cost analysis

Per full run (1 resume, 10 companies, ~50 JDs, Gemini 2.5 Flash):

| Call                     | Input tokens | Output tokens | Count |
|--------------------------|--------------|---------------|-------|
| Resume parse             | 2,000        | 1,000         | 1     |
| Per-job match            | 2,500        | 400           | 50    |
| Cover letter (per apply) | 1,500        | 250           | ~5    |

**Total:** ~135K input, ~23K output.

At Gemini 2.5 Flash pricing (~$0.30/M in, $2.50/M out):
  ~$0.04 + ~$0.06 ≈ **$0.10 per full run**.

At Claude Haiku 4.5 pricing (~$1/M in, $5/M out):
  ~$0.14 + ~$0.12 ≈ **$0.26 per full run**.

Either is fine. Gemini is cheaper; Haiku may give marginally better match-reasoning quality. Both are negligible vs. the time saved.

## Extending

- **Add a new ATS type:** create `src/scrapers/<name>.py`, subclass `BaseScraper`, `@register` it. Add the type to the `ATSType` enum.
- **Swap LLM provider:** edit `src/config.py`'s `build_model()` — or just change `LLM_PROVIDER` + `LLM_MODEL_ID` in `.env`.
- **Better form filling:** extend the `FIELD_HINTS` list in `src/tools/apply_tools.py`. Consider adding a vision-based fallback for portals that use non-standard field structures.
- **Email notifications:** wire a tool that sends a daily digest of new matches via SMTP / SendGrid.
- **Multi-user:** move `data/` caches to per-user directories + a SQLite index.

## Known limitations

- LinkedIn, Indeed, Naukri are not supported — their ToS forbid scraping. Use their native "Easy Apply" flows.
- The Generic scraper is best-effort. For companies not on one of the common ATS platforms, you may need to add a bespoke adapter.
- Workday portals that require auth/cookies before listing jobs will fall back to the Generic scraper, which may miss details.
- The form-filling heuristic covers common fields but can't handle custom dropdowns or multi-step wizards reliably. That's part of why it's semi-auto.

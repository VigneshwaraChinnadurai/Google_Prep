# Job Finder Agent

> An agentic job-search assistant that parses your resume, scrapes career portals, scores matches with an LLM, and prepares copy-paste-ready application packets — all driven from an Android app.

Built with **AWS Strands Agents SDK**, **Gemini 2.5 Flash**, **FastAPI**, and **Jetpack Compose**.

---

## Table of contents

1. [What it does](#what-it-does)
2. [Architecture](#architecture)
3. [Tech stack](#tech-stack)
4. [Repository layout](#repository-layout)
5. [Prerequisites](#prerequisites)
6. [Backend setup](#backend-setup)
7. [Android app setup](#android-app-setup)
8. [Configuration](#configuration)
9. [Daily workflow](#daily-workflow)
10. [CLI usage](#cli-usage)
11. [API reference](#api-reference)
12. [How agents are wired](#how-agents-are-wired)
13. [Caching and data files](#caching-and-data-files)
14. [Contests feature](#contests-feature)
15. [Troubleshooting](#troubleshooting)
16. [Roadmap](#roadmap)
17. [Legal & ToS](#legal--tos)

---

## What it does

A 5-agent pipeline orchestrated end-to-end from a phone:

| Stage | Agent | Output |
|-------|-------|--------|
| 1. Parse | `ResumeParserAgent` | Structured `Resume` from PDF/DOCX |
| 2. Scrape | `ScraperAgent` | Jobs from Greenhouse, Lever, Workday, generic ATS |
| 3. Match | `MatcherAgent` | LLM-scored fit per job (`fit_score`, `fit_reasoning`) |
| 4. Apply | `ApplicatorAgent` | Cover letter + field hints (no auto-submit) |
| 5. Contests | `CompetitionAgent` | Hiring/coding competitions across HackerEarth, HackerRank, Unstop, Kaggle — auto-flagged when sponsor matches `companies.yaml` |

The mobile app handles approval, deep-links to the live application URL, and copies the prepared content to the clipboard. **Final submission is always manual** — by design.

---

## Architecture

```
┌────────────────────────────────────────────────────┐
│                Android App (Kotlin)                │
│   Compose UI · Ktor client · DataStore · Hilt      │
└────────────────────────┬───────────────────────────┘
                         │ HTTPS + WebSocket
                         │ X-API-Key auth
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                    FastAPI Backend (Python)                 │
│  /resume  /pipeline  /matches  /apply  /competitions  /hz   │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                   Orchestrator (stateful)                   │
│  resume · jobs · matches · competitions  (all cached)       │
└──┬──────────┬──────────┬──────────┬───────────────┬─────────┘
   ▼          ▼          ▼          ▼               ▼
Resume     Scraper    Matcher   Applicator    Competition
Parser        │                                    │
              ▼                                    ▼
       ┌──────┴──────┐                    ┌────────┴────────┐
       │ATS Adapters │                    │Contest Adapters │
       │ Greenhouse  │                    │  HackerEarth    │
       │ Lever       │                    │  HackerRank     │
       │ Workday     │                    │  Unstop         │
       │ Generic(PW) │                    │  Kaggle         │
       └─────────────┘                    └─────────────────┘
```

Agents communicate **only through the orchestrator**. The mobile app never talks to an agent directly — every request goes through the FastAPI surface.

---

## Tech stack

### Backend
- **Python** 3.10+ with type hints throughout
- **Strands Agents SDK** — agent framework (`strands-agents[gemini]`)
- **Gemini 2.5 Flash** — LLM (swappable for Claude, GPT-4, Bedrock)
- **FastAPI + Uvicorn** — HTTP/WebSocket layer
- **Pydantic v2** — schemas, DTOs, validation
- **httpx + BeautifulSoup** — API-first scraping
- **Playwright** — JS-rendered ATS fallback (generic adapter)
- **pypdf, python-docx** — resume parsing
- **tenacity** — retry/backoff
- **rich** — structured CLI logging
- **YAML** — all configuration

### Android
- **Kotlin** + **Jetpack Compose** (Material 3)
- **Ktor 3** client (REST + WebSocket)
- **Hilt** for DI
- **DataStore Preferences** for settings
- **kotlinx.serialization** for DTOs
- **kotlinx.coroutines + Flow** for async

---

## Repository layout

```
job-finder/
├── src/                          # Python backend
│   ├── agents/
│   │   ├── resume_parser.py      # PDF/DOCX → Resume
│   │   ├── scraper.py            # Multi-ATS concurrent scraping
│   │   ├── matcher.py            # LLM fit-score scoring
│   │   ├── applicator.py         # ApplyPacket generator
│   │   └── competition.py        # Aggregates contest adapters
│   ├── scrapers/                 # ATS adapters (decorator-registered)
│   │   ├── greenhouse.py
│   │   ├── lever.py
│   │   ├── workday.py
│   │   └── generic.py            # Playwright-based fallback
│   ├── competitions/             # Contest platform adapters
│   │   ├── _company_matcher.py   # canonical-name matching against companies.yaml
│   │   ├── hackerearth.py
│   │   ├── hackerrank.py
│   │   ├── unstop.py
│   │   └── kaggle.py
│   ├── tools/                    # @tool-decorated functions for agents
│   ├── schemas/
│   │   ├── resume.py
│   │   ├── job.py
│   │   ├── match.py
│   │   ├── apply_packet.py
│   │   └── competition.py
│   ├── api/                      # FastAPI layer
│   │   ├── main.py
│   │   ├── deps.py
│   │   ├── runs.py               # In-memory run tracker (WS progress)
│   │   └── routes/
│   │       ├── resume.py
│   │       ├── pipeline.py
│   │       ├── matches.py
│   │       ├── apply.py
│   │       └── competitions.py
│   ├── orchestrator.py           # Stateful pipeline coordinator
│   ├── config.py                 # YAML loader
│   └── cli.py                    # python -m src.cli ...
├── config/
│   ├── settings.yaml             # LLM, scoring weights, scraping config
│   └── companies.yaml            # Target companies + ATS type
├── data/                         # Runtime caches (gitignored)
│   ├── profile.json
│   ├── jobs_cache.json
│   └── matches.json
├── android/                      # Android Studio project
│   └── app/src/main/java/com/vc/jobfinder/
│       ├── data/
│       │   ├── local/            # SettingsStore (DataStore)
│       │   ├── remote/           # ApiClient, DTOs, ConfigHolder
│       │   └── repository/       # JobFinderRepository
│       ├── di/                   # Hilt modules
│       ├── ui/
│       │   ├── settings/
│       │   ├── resume/
│       │   ├── pipeline/
│       │   ├── matches/
│       │   ├── competitions/
│       │   └── apply/
│       └── MainActivity.kt
├── tests/
├── .env.example
├── pyproject.toml
└── README.md
```

---

## Prerequisites

| | Version | Notes |
|---|---|---|
| Python | 3.10+ | 3.12 recommended |
| Node | not required | — |
| Android Studio | Hedgehog (2023.1.1)+ | for the mobile client |
| Android SDK | API 28+ (target 35) | min SDK is Android 9 |
| `uv` or `pip` | latest | `uv` recommended |
| Playwright browsers | Chromium | `playwright install chromium` |
| Gemini API key | — | https://aistudio.google.com/apikey |

---

## Backend setup

```bash
# 1. clone and enter
git clone <your-repo-url> job-finder
cd job-finder

# 2. install deps (with uv — fastest)
uv sync
# or with pip:
# python -m venv .venv && source .venv/bin/activate
# pip install -e ".[dev]"

# 3. install Playwright browsers (for the generic scraper only)
uv run playwright install chromium

# 4. set up env
cp .env.example .env
# edit .env and set:
#   GEMINI_API_KEY=...
#   API_KEY=$(openssl rand -hex 24)

# 5. run the API
uv run uvicorn src.api.main:app --host 0.0.0.0 --port 8000 --reload

# 6. sanity check
curl http://localhost:8000/healthz
# {"status":"ok"}
```

The API binds to `0.0.0.0` so the Android emulator (and devices on your LAN) can reach it.

---

## Android app setup

### 1. Open the project

```bash
# From repo root
open -a "Android Studio" android/
```

Let Gradle sync. First sync downloads ~600 MB of dependencies; subsequent builds are fast.

### 2. Pick your run target

| Target | Backend URL to enter in Settings |
|---|---|
| **Emulator** (recommended for dev) | `http://10.0.2.2:8000/` |
| **Physical device on same Wi-Fi** | `http://<your-machine-LAN-ip>:8000/` |
| **Backend on a remote host** | `https://your-host.tld/` |

`10.0.2.2` is the emulator's special alias for the host machine's loopback. Don't try `localhost` from the emulator — it'll resolve to the emulator itself.

### 3. First launch

The app routes you to the Settings screen if no API key is configured. Paste:
- **Base URL** — see the table above
- **API key** — the value of `API_KEY` in your `.env`

Tap **Save**. You're sent to the Pipeline screen.

### 4. Cleartext HTTP for local dev

The manifest already includes `android:usesCleartextTraffic="true"` so `http://10.0.2.2` works during development. **Remove this flag before publishing anywhere** — it allows unencrypted traffic globally. For a production deploy, terminate TLS in front of the API and use `https://`.

---

## Configuration

### `.env`

```bash
GEMINI_API_KEY=AIza...                 # required
API_KEY=<random-hex>                   # required, mobile uses this in X-API-Key
LOG_LEVEL=INFO                         # optional
```

### `config/settings.yaml`

```yaml
llm:
  provider: gemini
  model: gemini-2.5-flash
  temperature: 0.2
  api_key_env: GEMINI_API_KEY

scraping:
  max_concurrent: 5
  cache_ttl_hours: 6
  user_agent: "JobFinder/0.1 (+contact@example.com)"
  respect_robots_txt: true

matching:
  max_concurrent: 8
  weights:
    skills: 0.5
    experience: 0.3
    seniority: 0.2

api:
  host: 0.0.0.0
  port: 8000
  cors_origins: ["*"]                  # tighten before exposing publicly
```

### `config/companies.yaml`

```yaml
- name: Stripe
  ats: greenhouse
  slug: stripe
- name: Anthropic
  ats: greenhouse
  slug: anthropic
- name: Notion
  ats: lever
  slug: notion
- name: Workday-using-corp
  ats: workday
  url: https://example.wd1.myworkdayjobs.com/External
- name: Google
  ats: generic
  url: https://www.google.com/about/careers/applications/jobs/results/
```

The `ats` field selects which adapter handles the company. `generic` falls back to Playwright.

---

## Daily workflow

1. **Settings** (one-time) — point the app at the backend.
2. **Resume tab** — upload your latest PDF/DOCX. The parsed `Resume` is cached in `data/profile.json`; you don't re-upload unless your resume changes.
3. **Run tab** — tap *Run scrape + match*. Live progress streams over WebSocket: company-by-company while scraping, job-by-job while matching.
4. **Matches tab** — adjust the min-fit-score slider, scan ranked results, tap *Approve* / *Skip*.
5. **Contests tab** — pull-to-refresh fetches hiring/coding contests from HackerEarth, HackerRank, Unstop, and Kaggle. Toggle **Tracked companies** to show only contests sponsored by a company in your `companies.yaml`. Toggle **Hiring only** to filter out non-recruitment contests. Tapping a card opens the platform's registration page.
6. **Apply** — tap *Apply* on any match:
   - Cover letter is generated and shown (one-tap copy)
   - Each form field (name, email, links, etc.) has its own copy button
   - *Open application* deep-links to the job's apply URL in your default browser
   - You paste, review, and submit manually

---

## CLI usage

The pipeline also runs from the terminal — useful for cron, debugging, or pre-warming caches before opening the app.

```bash
# parse a resume
python -m src.cli parse path/to/resume.pdf

# scrape all companies in companies.yaml
python -m src.cli scrape

# score all cached jobs against the cached resume
python -m src.cli match

# show top matches above a threshold
python -m src.cli list-matches --min-score 0.7 --limit 20

# end-to-end (parse if needed → scrape → match)
python -m src.cli run-all path/to/resume.pdf

# refresh + list hiring contests, optionally filtered to tracked companies
python -m src.cli competitions --refresh
python -m src.cli competitions --tracked-only --hiring-only
```

---

## API reference

All endpoints require `X-API-Key: <your-key>` (WebSockets accept it as `?api_key=` query param).

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/healthz` | Liveness check (no auth) |
| `POST` | `/api/v1/resume/upload` | Multipart upload, returns parsed `Resume` |
| `GET` | `/api/v1/resume` | Currently cached resume (or null) |
| `POST` | `/api/v1/pipeline/scrape` | Kick off scraping → `{run_id}` |
| `POST` | `/api/v1/pipeline/match` | Kick off matching → `{run_id}` |
| `WS` | `/api/v1/pipeline/{run_id}/events` | Live `{progress, message, status}` events |
| `GET` | `/api/v1/matches?min_score=0.5&limit=50` | Ranked match list |
| `GET` | `/api/v1/matches/{id}` | Single match |
| `POST` | `/api/v1/matches/{id}/approve` | Mark approved |
| `POST` | `/api/v1/matches/{id}/skip` | Mark skipped |
| `GET` | `/api/v1/apply/{match_id}/packet` | Generate `ApplyPacket` (cover letter + field hints) |
| `GET` | `/api/v1/competitions?tracked_only=&hiring_only=&platform=` | List hiring/coding contests; filterable |
| `POST` | `/api/v1/competitions/refresh` | Kick off a contest re-fetch across all platforms → `{run_id}` |
| `WS` | `/api/v1/competitions/{run_id}/events` | Live progress for the contest refresh |

OpenAPI docs at `http://localhost:8000/docs`.

---

## How agents are wired

Each agent owns one responsibility. They never call each other directly — the orchestrator is the only place that knows about all four.

```python
# Sketch of the orchestrator's contract
class Orchestrator:
    async def scrape_iter() -> AsyncIterator[tuple[str, int]]
    async def match_iter()  -> AsyncIterator[tuple[int, int]]
    def list_matches(min_score, limit) -> list[MatchResult]
    def mark_match(id, status) -> None
    def get_resume() -> Resume | None
```

Generators yield progress so the WebSocket layer can stream events without buffering whole batches. `asyncio.as_completed` is what makes "Stripe just finished, 47 jobs" appear before "Notion is still going."

### Adding a new ATS

1. Drop a new file in `src/scrapers/` and decorate the class with `@register_scraper("my_ats")`.
2. Implement `async def fetch(self, company: Company) -> list[Job]`.
3. Add companies with `ats: my_ats` in `config/companies.yaml`.

The orchestrator picks adapters by string key — no other wiring needed.

### Swapping the LLM

Edit `config/settings.yaml`:

```yaml
llm:
  provider: anthropic                  # or openai, bedrock
  model: claude-sonnet-4-5
  api_key_env: ANTHROPIC_API_KEY
```

The agent factory in `src/agents/_factory.py` reads `provider` and constructs the right Strands model wrapper.

---

## Caching and data files

| File | TTL | Purpose |
|---|---|---|
| `data/profile.json` | indefinite | Parsed resume — re-uploaded only when you upload a new file |
| `data/jobs_cache.json` | `scraping.cache_ttl_hours` | Scraped jobs, deduped by `(company, title, url)` |
| `data/matches.json` | indefinite | Scored matches, preserves `approved`/`skipped` status across re-runs |
| `data/competitions_cache.json` | manual refresh | Contests across all platforms, deduped by `(platform, id)` |

**Cache-first is the rule.** Every flow checks JSON before hitting the LLM or an ATS endpoint. To force a refresh:

```bash
rm data/jobs_cache.json    # next scrape will hit ATS APIs
rm data/matches.json       # next match will re-score everything
```

---

## Contests feature

The **Contests** tab aggregates hiring-flavored coding competitions from four platforms and surfaces the ones run by companies you're already tracking.

### Why it exists

Companies recruit through contests differently than through job listings — a contest can become a job offer in one weekend. Watching contest platforms manually is impractical; this feature reduces it to a single tab.

### Sources

| Platform | Method | Notes |
|---|---|---|
| HackerEarth | HTML scrape of `/challenges/hiring/` | All listings on this page are flagged `is_hiring=true` |
| HackerRank | JSON endpoint at `/rest/contests/upcoming` | `is_hiring` set when a `company_name` field is present |
| Unstop | Public search API at `/api/public/opportunity/search-result` | Fetches both hackathons and competitions |
| Kaggle | RPC endpoint behind `kaggle.com/competitions` listing | `is_hiring` set when category contains "recruitment" |

LinkedIn, Indeed, and Naukri are excluded — same ToS reasoning as the job-scraping side.

### Tracked-company matching

Contest sponsor names are messy ("Google LLC", "Stripe Inc.", "Razorpay Pvt Ltd"), so a normalizer in `src/competitions/_company_matcher.py` strips legal suffixes and punctuation, then does bidirectional substring matching against canonical names from `companies.yaml`. When a match is found, the contest gets `is_tracked_company=true` and the canonical name is stored on `matched_company`.

In the UI, tracked-company contests sort to the top and show a "In your list" badge. The **Tracked companies** filter chip restricts the list to those.

### Adding a new platform

1. Drop a file in `src/competitions/` with `@register_competition("yourname")`.
2. Implement `async def fetch(self) -> list[Competition]`.
3. Use `match_company(sponsor)` from `_company_matcher` to populate the canonical name.

The orchestrator and API pick up new adapters automatically — no other wiring.

---



| Symptom | First thing to check |
|---|---|
| Android app: "Invalid API key" | Settings screen — does the key match `API_KEY` in `.env` exactly? Whitespace matters. |
| Android app: connection timeout | Emulator → use `10.0.2.2`. Physical device → confirm phone and laptop are on the same Wi-Fi and there's no AP isolation. |
| WebSocket disconnects after 30s | Heartbeats are configured at 30s — anything longer is usually a proxy. Check for corporate VPN / nginx idle timeouts. |
| Matcher hangs / errors | `GEMINI_API_KEY` valid? Check `data/jobs_cache.json` is non-empty. Lower `matching.max_concurrent` if rate-limited. |
| Generic scraper throws Playwright errors | `uv run playwright install chromium`. Headless Chromium needs system libs on Linux — see Playwright docs. |
| Pydantic validation errors on `/resume/upload` | The PDF probably parsed to nothing usable. Try DOCX. Check the parser's logs. |
| Stale jobs in matches | Delete `data/jobs_cache.json` or shorten `scraping.cache_ttl_hours`. |

Backend logs are structured (rich) and live in stdout. Bump `LOG_LEVEL=DEBUG` in `.env` for verbose tracing.

---

## Roadmap

- [ ] Notification when a high-score match (>0.85) appears
- [ ] Background scrape (Android `WorkManager` + scheduled `cron` on backend)
- [ ] Saved searches / job filters per role
- [ ] WebView-injected pre-fill for top 3 ATS targets (Greenhouse/Lever/Workday)
- [ ] iOS client (extract a `:shared` Kotlin Multiplatform module from `data/`)
- [ ] Multi-resume profiles (different roles need different resumes)
- [ ] Match history & analytics — applications-per-week, response rate

---

## Legal & ToS

This project is built to **respect the rules of the sites it touches**:

- **API-first.** Greenhouse, Lever, and Workday all expose public job listings via documented endpoints.
- **`robots.txt` is honored** by every scraper unless explicitly disabled in config.
- **Polite scraping** — concurrency is bounded, retries use backoff, and a real `User-Agent` identifies the client.
- **No LinkedIn, Indeed, Naukri, Glassdoor.** These sites' ToS forbid automated scraping. Don't add adapters for them.
- **No auto-submit.** The applicator generates a packet; you submit. This is non-negotiable in this codebase — applying through automation generally violates the destination site's ToS and can also misrepresent you.

If you fork this and add an integration, keep these rules.

---

## License

MIT — see `LICENSE`.

## Author

**Vigneshwara Chinnadurai** — Senior AI Architect
Production-grade agentic systems, RAG, MLOps.

# Job Application Automation System - Detailed Documentation

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Backend Components](#backend-components)
4. [Android App Components](#android-app-components)
5. [Agent System](#agent-system)
6. [Database Schema](#database-schema)
7. [API Reference](#api-reference)
8. [Configuration](#configuration)
9. [Setup Guide](#setup-guide)
10. [Usage Guide](#usage-guide)

---

## Overview

The Job Application Automation System is a comprehensive solution for automating the job hunting process. It consists of:

- **Python FastAPI Backend**: The "Agent Brain" handling job discovery, analysis, auto-application, and email tracking
- **Android Kotlin App**: The "Controller" providing UI to manage and monitor the automation

### Core Features
- **Scout Agent**: Discovers jobs from LinkedIn, Greenhouse, Lever, and company career pages
- **Analyst Agent**: Scores job-profile fitment using Google Gemini LLM
- **Applicant Agent**: Auto-applies to high-scoring jobs via Playwright
- **Tracker Agent**: Syncs Gmail to track application status via email classification
- **AI Features**: Resume customization, interview prep generation, skill gap analysis

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         ANDROID APP                                  │
│  ┌─────────────┐ ┌──────────────┐ ┌─────────────┐ ┌──────────────┐ │
│  │  Dashboard  │ │ Applications │ │  Companies  │ │   Settings   │ │
│  └──────┬──────┘ └──────┬───────┘ └──────┬──────┘ └──────┬───────┘ │
│         │               │                │               │          │
│  ┌──────┴───────────────┴────────────────┴───────────────┴───────┐ │
│  │                    ViewModels (MVVM)                          │ │
│  └──────────────────────────┬────────────────────────────────────┘ │
│                             │                                       │
│  ┌──────────────────────────┴────────────────────────────────────┐ │
│  │              Repository + Retrofit API Client                 │ │
│  └──────────────────────────┬────────────────────────────────────┘ │
└─────────────────────────────┼───────────────────────────────────────┘
                              │ HTTP/JSON
┌─────────────────────────────┴───────────────────────────────────────┐
│                      PYTHON BACKEND (FastAPI)                        │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                      API Routes Layer                         │   │
│  │  /profile  /jobs  /applications  /companies  /chat  /agents  │   │
│  └──────────────────────────┬───────────────────────────────────┘   │
│                             │                                        │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                      Agent System                            │    │
│  │  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐   │    │
│  │  │   Scout   │ │  Analyst  │ │ Applicant │ │  Tracker  │   │    │
│  │  │  (6 hrs)  │ │  (30 min) │ │  (15 min) │ │  (60 min) │   │    │
│  │  └───────────┘ └───────────┘ └───────────┘ └───────────┘   │    │
│  └──────────────────────────┬──────────────────────────────────┘    │
│                             │                                        │
│  ┌──────────────────────────┴──────────────────────────────────┐    │
│  │                      Services Layer                          │    │
│  │  ┌───────────────┐ ┌───────────────┐ ┌────────────────┐    │    │
│  │  │ GeminiClient  │ │ GmailService  │ │   Scheduler    │    │    │
│  │  └───────────────┘ └───────────────┘ └────────────────┘    │    │
│  └──────────────────────────────────────────────────────────────┘    │
│                             │                                        │
│  ┌──────────────────────────┴──────────────────────────────────┐    │
│  │                   Database Layer (PostgreSQL)                │    │
│  │  profiles | jobs | applications | companies | emails        │    │
│  └──────────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Backend Components

### Directory Structure
```
backend/
├── main.py                 # FastAPI application entry
├── config.py               # Configuration management
├── requirements.txt        # Python dependencies
├── .env.example           # Environment variables template
├── database/
│   ├── database.py        # Database connection
│   ├── models.py          # SQLAlchemy ORM models
│   └── crud.py            # Database operations
├── services/
│   ├── gemini_client.py   # Google Gemini LLM client
│   ├── gmail_service.py   # Gmail API integration
│   └── scheduler.py       # APScheduler wrapper
├── agents/
│   ├── base_agent.py      # Base agent class
│   ├── scout_agent.py     # Job discovery agent
│   ├── analyst_agent.py   # Fitment scoring agent
│   ├── applicant_agent.py # Auto-apply agent
│   └── tracker_agent.py   # Email tracking agent
├── api/
│   └── routes/
│       ├── profile.py     # Profile endpoints
│       ├── jobs.py        # Jobs endpoints
│       ├── applications.py # Applications endpoints
│       ├── companies.py   # Companies endpoints
│       ├── chat.py        # Chat/AI endpoints
│       └── agents.py      # Agent control endpoints
├── ai_features/
│   ├── resume_customizer.py
│   ├── interview_prep.py
│   └── skill_gap_analyzer.py
└── sample_data/
    └── profile.json       # Sample profile template
```

### Key Files

#### main.py
- FastAPI application setup with lifespan management
- CORS configuration for mobile app access
- Scheduler initialization on startup
- Router includes for all API modules

#### config.py
- Pydantic Settings-based configuration
- Environment variable loading
- Default values for all settings
- API keys, thresholds, intervals

#### services/gemini_client.py
- Google Generative AI client wrapper
- Cost tracking per API call
- Methods: `analyze_job_fitment()`, `classify_email()`, `customize_resume()`, `generate_interview_prep()`
- Token counting and budget enforcement

#### services/gmail_service.py
- OAuth2 authentication flow
- Job-related email search patterns
- Email classification pipeline
- Application status extraction

#### services/scheduler.py
- APScheduler integration
- Agent job scheduling
- Pause/resume functionality
- Next-run tracking

---

## Android App Components

### Directory Structure
```
android/
├── build.gradle.kts        # Root build configuration
├── settings.gradle.kts     # Gradle settings
├── gradle.properties       # Gradle properties
└── app/
    ├── build.gradle.kts    # App build configuration
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/vignesh/jobautomation/
        │   ├── MainActivity.kt
        │   ├── data/
        │   │   ├── api/
        │   │   │   └── JobAutomationApi.kt
        │   │   ├── models/
        │   │   │   └── Models.kt
        │   │   └── repository/
        │   │       └── JobAutomationRepository.kt
        │   ├── ui/
        │   │   ├── theme/
        │   │   │   ├── Theme.kt
        │   │   │   └── Type.kt
        │   │   ├── dashboard/
        │   │   │   └── DashboardScreen.kt
        │   │   ├── applications/
        │   │   │   └── ApplicationsScreen.kt
        │   │   ├── companies/
        │   │   │   └── CompaniesScreen.kt
        │   │   ├── profile/
        │   │   │   └── ProfileScreen.kt
        │   │   └── settings/
        │   │       └── SettingsScreen.kt
        │   └── viewmodel/
        │       ├── DashboardViewModel.kt
        │       ├── ApplicationsViewModel.kt
        │       ├── CompaniesViewModel.kt
        │       ├── ProfileViewModel.kt
        │       └── SettingsViewModel.kt
        └── res/
            ├── values/
            │   ├── strings.xml
            │   ├── colors.xml
            │   └── themes.xml
            └── xml/
                ├── backup_rules.xml
                └── data_extraction_rules.xml
```

### Key Screens

#### DashboardScreen
- Status summary cards (jobs, applications, interviews, cost)
- Quick action buttons for running agents
- Chat interface for natural language queries
- Recent activity feed

#### ApplicationsScreen
- Application list with status filtering
- Stats row (total, active, interviews, rejected)
- Application detail dialog
- Interview prep generation

#### CompaniesScreen
- Company list with preference badges
- Filter by preference (Priority/Allowed/Blocked)
- Add company dialog
- Seed default companies button

#### ProfileScreen
- Profile header with avatar
- Skills by category
- Experience timeline
- Target preferences (roles, locations, salary)

#### SettingsScreen
- Connection status indicator
- Agent status with toggle switches
- API cost tracking with budget progress
- Backend URL configuration

---

## Agent System

### Scout Agent
**Purpose**: Discover new job postings

**Sources**:
- LinkedIn (via web scraping with anti-detection)
- Greenhouse boards
- Lever job boards
- Company career pages

**Schedule**: Every 6 hours

**Flow**:
1. Get target roles and locations from profile
2. Scrape each source for matching jobs
3. Deduplicate against existing jobs
4. Store new jobs with DISCOVERED status
5. Record agent run statistics

### Analyst Agent
**Purpose**: Score job-profile fitment

**Schedule**: Every 30 minutes

**Flow**:
1. Get unanalyzed jobs (status = DISCOVERED)
2. Load user profile
3. For each job:
   - Send job + profile to Gemini
   - Parse fitment score (0-100)
   - Extract matching skills, gaps, recommendations
   - Update job with score and analysis
4. Mark high-scoring jobs for application

**Gemini Prompt Structure**:
```
Analyze the fit between this job posting and candidate profile.
Return JSON with: score (0-100), matching_skills, skill_gaps, recommendations
```

### Applicant Agent
**Purpose**: Auto-apply to qualified jobs

**Schedule**: Every 15 minutes

**Flow**:
1. Get jobs ready for application (score >= threshold, status = ANALYZED)
2. For each job:
   - Check company preference (skip if BLOCKED)
   - Launch Playwright browser
   - Navigate to application page
   - Fill form fields from profile
   - Submit application
   - Create application record
   - Update job status

**Supported Platforms**:
- Greenhouse (standard forms)
- Lever (standard forms)
- LinkedIn Easy Apply
- Unknown forms → marked for manual application

### Tracker Agent
**Purpose**: Sync email to track application status

**Schedule**: Every 60 minutes

**Flow**:
1. Authenticate with Gmail API
2. Search for job-related emails (patterns: "application", "interview", "offer", etc.)
3. For each new email:
   - Extract company name from sender/content
   - Match to existing application
   - Classify email type using Gemini
   - Update application status
   - Store email record

**Email Classification Categories**:
- CONFIRMATION: Application received/confirmed
- REJECTION: Application rejected
- INTERVIEW_INVITE: Interview scheduled
- OFFER: Job offer
- FOLLOW_UP: General communication

---

## Database Schema

### profiles
| Column | Type | Description |
|--------|------|-------------|
| id | Integer | Primary key |
| full_name | String | Full name |
| email | String | Email address |
| phone | String | Phone number |
| location | String | Current location |
| summary | Text | Professional summary |
| skills | JSON | Skills by category |
| experience | JSON | Work experience list |
| education | JSON | Education list |
| desired_roles | JSON | Target job titles |
| desired_locations | JSON | Preferred locations |
| min_salary | Integer | Minimum salary |
| max_salary | Integer | Maximum salary |
| resume_path | String | Resume file path |
| linkedin_url | String | LinkedIn profile URL |

### companies
| Column | Type | Description |
|--------|------|-------------|
| id | Integer | Primary key |
| name | String | Company name |
| company_type | Enum | MAANG/PRODUCT/STARTUP/CONSULTING/AGENCY/OTHER |
| careers_page | String | Careers page URL |
| preference | Enum | PRIORITY/ALLOWED/BLOCKED/NEUTRAL |
| job_count | Integer | Jobs from this company |
| created_at | DateTime | Creation timestamp |

### jobs
| Column | Type | Description |
|--------|------|-------------|
| id | Integer | Primary key |
| title | String | Job title |
| company_id | Integer | FK to companies |
| company_name | String | Company name (denormalized) |
| location | String | Job location |
| description | Text | Full job description |
| requirements | JSON | Job requirements |
| salary_range | String | Salary range |
| source | String | Source URL |
| source_type | String | linkedin/greenhouse/lever/careers |
| status | Enum | DISCOVERED/ANALYZED/READY/APPLIED/SKIPPED |
| match_score | Float | Fitment score (0-100) |
| analysis | JSON | Full analysis result |
| discovered_at | DateTime | Discovery timestamp |
| analyzed_at | DateTime | Analysis timestamp |

### applications
| Column | Type | Description |
|--------|------|-------------|
| id | Integer | Primary key |
| job_id | Integer | FK to jobs |
| status | Enum | SUBMITTED/CONFIRMED/UNDER_REVIEW/INTERVIEW_SCHEDULED/INTERVIEWED/OFFER_RECEIVED/REJECTED/WITHDRAWN |
| applied_at | DateTime | Application timestamp |
| status_history | JSON | Status change history |
| notes | Text | User notes |
| interview_date | DateTime | Interview date if scheduled |

### emails
| Column | Type | Description |
|--------|------|-------------|
| id | Integer | Primary key |
| application_id | Integer | FK to applications (nullable) |
| gmail_id | String | Gmail message ID |
| from_address | String | Sender email |
| subject | String | Email subject |
| received_at | DateTime | Receive timestamp |
| classification | Enum | CONFIRMATION/REJECTION/INTERVIEW_INVITE/OFFER/FOLLOW_UP/OTHER |
| processed | Boolean | Processing status |

### agent_runs
| Column | Type | Description |
|--------|------|-------------|
| id | Integer | Primary key |
| agent_name | String | Agent identifier |
| started_at | DateTime | Start timestamp |
| finished_at | DateTime | End timestamp |
| status | String | success/failed |
| items_processed | Integer | Items handled |
| error | Text | Error message if failed |

---

## API Reference

### Status
- `GET /` - System status
- `GET /health` - Health check

### Profile
- `GET /profile` - Get current profile
- `PUT /profile` - Update profile

### Jobs
- `GET /jobs` - List jobs (with filters)
- `GET /jobs/{id}` - Get job detail
- `POST /jobs/{id}/analyze` - Trigger analysis
- `POST /jobs/{id}/apply` - Trigger application
- `POST /jobs/{id}/customize-resume` - Get customized resume

### Applications
- `GET /applications` - List applications
- `GET /applications/stats` - Get statistics
- `GET /applications/{id}` - Get application detail
- `PUT /applications/{id}` - Update application
- `POST /applications/{id}/interview-prep` - Generate prep

### Companies
- `GET /companies` - List companies
- `GET /companies/{id}` - Get company detail
- `POST /companies` - Create company
- `PUT /companies/{id}/preference` - Update preference
- `POST /companies/seed-defaults` - Seed MAANG companies

### Chat
- `POST /chat` - Natural language query
- `GET /chat/status-summary` - Get summary
- `GET /chat/cost` - Get API cost stats

### Agents
- `GET /agents/status` - Get all agent status
- `GET /agents/runs` - Get agent run history
- `POST /agents/scout/run` - Run scout agent
- `POST /agents/analyst/run` - Run analyst agent
- `POST /agents/applicant/run` - Run applicant agent
- `POST /agents/tracker/run` - Run tracker agent
- `POST /agents/pipeline/run` - Run full pipeline

---

## Configuration

### Environment Variables (.env)
```env
# Database
DATABASE_URL=postgresql://user:pass@localhost:5432/job_automation

# Google APIs
GEMINI_API_KEY=your-gemini-api-key
GOOGLE_CLIENT_ID=your-oauth-client-id
GOOGLE_CLIENT_SECRET=your-oauth-client-secret

# Agent Settings
SCOUT_INTERVAL_HOURS=6
ANALYST_INTERVAL_MINUTES=30
APPLICANT_INTERVAL_MINUTES=15
TRACKER_INTERVAL_MINUTES=60

# Thresholds
MIN_FITMENT_SCORE=60
AUTO_APPLY_ENABLED=true

# Cost Management
DAILY_API_BUDGET=5.00
```

---

## Setup Guide

### Backend Setup

1. **Create virtual environment**:
   ```bash
   cd backend
   python -m venv venv
   source venv/bin/activate  # or venv\Scripts\activate on Windows
   ```

2. **Install dependencies**:
   ```bash
   pip install -r requirements.txt
   playwright install chromium
   ```

3. **Configure environment**:
   ```bash
   cp .env.example .env
   # Edit .env with your API keys
   ```

4. **Setup database**:
   ```bash
   # Start PostgreSQL
   # Create database: job_automation
   ```

5. **Run server**:
   ```bash
   uvicorn main:app --reload --host 0.0.0.0 --port 8000
   ```

### Gmail API Setup

1. Go to Google Cloud Console
2. Create new project
3. Enable Gmail API
4. Create OAuth 2.0 credentials
5. Download client secret JSON
6. Place as `credentials/gmail_credentials.json`

### Android Setup

1. **Open in Android Studio**
2. **Configure backend URL** in `JobAutomationApi.kt`:
   - Emulator: `http://10.0.2.2:8000`
   - Physical device: Use machine's IP address
3. **Build and run**

---

## Usage Guide

### Initial Setup

1. **Start backend server**
2. **Launch Android app**
3. **Check connection** in Settings
4. **Create profile** with skills, experience, target roles
5. **Seed default companies** or add custom ones
6. **Mark priority companies** with star

### Daily Workflow

1. **Dashboard**: Check status summary
2. **Quick Actions**: Run agents manually or let scheduler handle
3. **Chat**: Ask questions like:
   - "What's my application status?"
   - "Which jobs should I apply to?"
   - "Prepare me for the Google interview"

### Managing Applications

1. **Filter by status** to see interview invites
2. **Generate interview prep** for scheduled interviews
3. **Update status** manually for offline interactions
4. **Track progress** via stats

### Company Management

1. **Mark as Priority**: Agent applies first
2. **Mark as Allowed**: Normal processing
3. **Mark as Blocked**: Agent skips applications

### Monitoring

- **API Cost**: Track daily spend in Settings
- **Agent Status**: See next scheduled run times
- **Agent Runs**: View history of agent executions

---

## Troubleshooting

### Connection Issues
- Verify backend is running
- Check network/firewall settings
- For emulator: use `10.0.2.2` not `localhost`

### Gmail Sync Not Working
- Re-authenticate OAuth flow
- Check Gmail API quotas
- Verify email search patterns

### Jobs Not Being Discovered
- Check target roles in profile
- LinkedIn may be rate-limiting
- Try running Scout manually

### Auto-Apply Failing
- Check if company is blocked
- Verify form format is supported
- Some sites require CAPTCHA

---

## Cost Management

The system tracks Gemini API costs:
- Job analysis: ~$0.001 per job
- Email classification: ~$0.0005 per email
- Chat queries: ~$0.001 per query
- Resume customization: ~$0.002 per job
- Interview prep: ~$0.003 per application

Daily budget enforcement prevents runaway costs.

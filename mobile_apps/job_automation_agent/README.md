# Job Application Automation Agent

An AI-powered job application automation system with a client-server architecture. The Android app serves as the controller/UI while a Python backend handles the heavy lifting - scraping, analysis, and job applications.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     Android App (Controller)                     │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ • Dashboard / Chat Interface                              │  │
│  │ • Applications Tracker                                     │  │
│  │ • Company Manager (Allow/Block lists)                     │  │
│  │ • Profile Management                                       │  │
│  │ • Settings                                                 │  │
│  └───────────────────────────────────────────────────────────┘  │
└────────────────────────────┬────────────────────────────────────┘
                             │ REST API
                             ↓
┌─────────────────────────────────────────────────────────────────┐
│               Python Backend (Agent Brain)                       │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                    Multi-Agent System                        ││
│  │  ┌─────────┐  ┌──────────┐  ┌───────────┐  ┌─────────────┐ ││
│  │  │ Scout   │  │ Analyst  │  │ Applicant │  │ Tracker     │ ││
│  │  │ Agent   │  │ Agent    │  │ Agent     │  │ Agent       │ ││
│  │  │         │  │          │  │           │  │             │ ││
│  │  │ • Find  │  │ • Score  │  │ • Auto    │  │ • Gmail     │ ││
│  │  │   jobs  │  │   fitment│  │   apply   │  │   sync      │ ││
│  │  │ • Scrape│  │ • Rank   │  │ • Form    │  │ • Status    │ ││
│  │  │   boards│  │   matches│  │   filling │  │   updates   │ ││
│  │  └─────────┘  └──────────┘  └───────────┘  └─────────────┘ ││
│  └─────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                   AI Enhancement Modules                     ││
│  │  • Resume Customization   • Interview Prep                  ││
│  │  • Network Analysis       • Salary Analysis                 ││
│  │  • Weakness Improvement   • Cover Letter Generation         ││
│  └─────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                    PostgreSQL Database                       ││
│  │  Tables: profile, jobs, applications, companies, emails     ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
                             │
          ┌──────────────────┼──────────────────┐
          ↓                  ↓                  ↓
   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
   │  Gemini API  │  │   Job Boards │  │  Gmail API   │
   │  (LLM)       │  │  Career Pages│  │  (OAuth2)    │
   └──────────────┘  └──────────────┘  └──────────────┘
```

## Project Structure

```
job_automation_agent/
├── backend/                      # Python FastAPI Backend
│   ├── main.py                   # FastAPI application entry point
│   ├── config.py                 # Configuration management
│   ├── database/
│   │   ├── models.py             # SQLAlchemy models
│   │   ├── database.py           # Database connection
│   │   └── crud.py               # CRUD operations
│   ├── agents/
│   │   ├── base_agent.py         # Base agent class
│   │   ├── scout_agent.py        # Job discovery agent
│   │   ├── analyst_agent.py      # Fitment scoring agent
│   │   ├── applicant_agent.py    # Auto-apply agent
│   │   └── tracker_agent.py      # Gmail sync agent
│   ├── ai_features/
│   │   ├── resume_customizer.py  # ATS-optimized resume
│   │   ├── interview_prep.py     # Interview preparation
│   │   ├── network_analyzer.py   # LinkedIn network analysis
│   │   ├── salary_analyzer.py    # Salary data scraping
│   │   └── weakness_improver.py  # Skill gap analysis
│   ├── scrapers/
│   │   ├── base_scraper.py       # Base scraper class
│   │   ├── linkedin_scraper.py   # LinkedIn job scraper
│   │   ├── greenhouse_scraper.py # Greenhouse forms
│   │   └── company_careers.py    # Company career pages
│   ├── services/
│   │   ├── gemini_client.py      # Gemini API client
│   │   ├── gmail_service.py      # Gmail API integration
│   │   └── scheduler.py          # Task scheduler
│   ├── api/
│   │   ├── routes/
│   │   │   ├── profile.py        # Profile endpoints
│   │   │   ├── jobs.py           # Jobs endpoints
│   │   │   ├── applications.py   # Applications endpoints
│   │   │   ├── companies.py      # Companies endpoints
│   │   │   └── chat.py           # Chatbot endpoints
│   │   └── dependencies.py       # API dependencies
│   ├── requirements.txt
│   └── sample_data/
│       └── profile.json          # Sample profile template
│
├── android/                      # Android App (Controller)
│   ├── app/
│   │   └── src/main/
│   │       ├── java/com/vignesh/jobautomation/
│   │       │   ├── MainActivity.kt
│   │       │   ├── data/
│   │       │   │   ├── models/
│   │       │   │   ├── api/
│   │       │   │   └── repository/
│   │       │   ├── ui/
│   │       │   │   ├── dashboard/
│   │       │   │   ├── applications/
│   │       │   │   ├── companies/
│   │       │   │   ├── profile/
│   │       │   │   └── settings/
│   │       │   └── viewmodel/
│   │       └── res/
│   ├── build.gradle.kts
│   └── settings.gradle.kts
│
├── docs/
│   ├── DETAILED_DOCUMENTATION.md
│   ├── API_REFERENCE.md
│   └── runtime_flow.mmd
│
└── README.md
```

## Features

### Core Agents

1. **Scout Agent** - Discovers job postings
   - Scrapes job boards (LinkedIn, Greenhouse, Lever, company careers)
   - Filters by target companies and roles
   - Scheduled execution (every 6 hours)

2. **Analyst Agent** - Evaluates job fit
   - Uses Gemini to score profile-job alignment (0-100)
   - Identifies strengths and weaknesses for each role
   - Prioritizes applications by match score

3. **Applicant Agent** - Automates applications
   - Handles Easy Apply and standard forms
   - Fills forms using profile data
   - Logs all application attempts

4. **Tracker Agent** - Monitors application status
   - Syncs with Gmail via OAuth2
   - Classifies emails (rejection, interview, etc.)
   - Updates application statuses automatically

### AI Enhancement Features

- **Resume Customization**: ATS-optimized resume variants per job
- **Interview Prep**: Automated prep docs when interviews scheduled
- **Network Analysis**: Find connections at target companies
- **Salary Analysis**: Market rate data from Glassdoor/Levels.fyi
- **Weakness Improvement**: Learning recommendations for skill gaps

## Quick Start

### Backend Setup

```bash
cd backend

# Create virtual environment
python -m venv venv
source venv/bin/activate  # Linux/Mac
.\venv\Scripts\Activate   # Windows

# Install dependencies
pip install -r requirements.txt

# Set environment variables
cp .env.example .env
# Edit .env with your API keys

# Initialize database
python -m database.init_db

# Run server
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

### Android Setup

See [android/README.md](android/README.md) for build instructions.

### Configuration

Create `.env` file in `backend/`:

```env
# Database
DATABASE_URL=postgresql://user:password@localhost:5432/job_automation

# Gemini API
GEMINI_API_KEY=your_gemini_api_key

# Gmail OAuth2
GMAIL_CLIENT_ID=your_client_id
GMAIL_CLIENT_SECRET=your_client_secret

# Scheduler
SCOUT_INTERVAL_HOURS=6
ANALYST_INTERVAL_MINUTES=30
TRACKER_INTERVAL_MINUTES=60
```

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/profile` | GET/PUT | Manage user profile |
| `/api/jobs` | GET | List discovered jobs |
| `/api/jobs/{id}/apply` | POST | Trigger application |
| `/api/applications` | GET | List all applications |
| `/api/applications/{id}` | GET | Application details |
| `/api/companies` | GET/POST | Manage company list |
| `/api/companies/{id}/preference` | PUT | Set allow/block |
| `/api/chat` | POST | Chat with AI agent |
| `/api/status` | GET | System health check |

## Tech Stack

### Backend
- **Framework**: FastAPI
- **Database**: PostgreSQL + SQLAlchemy
- **AI/LLM**: Google Gemini API
- **Scraping**: Playwright, BeautifulSoup
- **Email**: Gmail API (OAuth2)
- **Scheduling**: APScheduler

### Android
- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Architecture**: MVVM
- **Networking**: Retrofit + Moshi
- **Local Storage**: DataStore

## License

MIT License - See LICENSE file

# Job Automation Standalone Android App

A completely standalone Android application for job search automation. Unlike the client-server version, this app runs entirely on-device with direct Gemini API integration.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Standalone Android App                         │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                       UI Layer (Compose)                   │  │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐        │  │
│  │  │Dashboard│ │  Jobs   │ │ Applied │ │AI Chat  │        │  │
│  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘        │  │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐                    │  │
│  │  │ Profile │ │Companies│ │Settings │                    │  │
│  │  └─────────┘ └─────────┘ └─────────┘                    │  │
│  └───────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                   ViewModels (MVVM)                        │  │
│  │  Dashboard│Jobs│Applications│Chat│Profile│Companies       │  │
│  └───────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                Repository Layer                            │  │
│  │  JobAutomationRepository - orchestrates data & AI          │  │
│  └───────────────────────────────────────────────────────────┘  │
│  ┌─────────────────────────┐ ┌─────────────────────────────┐  │
│  │    Room Database         │ │     Gemini AI Client        │  │
│  │  • Profile               │ │  • Job Fitment Analysis     │  │
│  │  • Companies             │ │  • Resume Customization     │  │
│  │  • Jobs                  │ │  • Interview Prep           │  │
│  │  • Applications          │ │  • Skill Gap Analysis       │  │
│  │  • Chat History          │ │  • AI Chat Assistant        │  │
│  └─────────────────────────┘ └─────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Features

### Core Functionality
- **Dashboard**: Overview of jobs, applications, and quick actions
- **Jobs Management**: Add, analyze, and track job opportunities
- **Applications Tracker**: Track application status from submitted to offer
- **AI Chat**: Natural language assistant for job search advice
- **Company Manager**: Organize target companies with preferences (Priority, Allowed, Blocked)
- **Profile Management**: Store skills, experience, and preferences

### AI-Powered Features (Gemini)
1. **Job Fitment Analysis**
   - Scores job match (0-100%)
   - Identifies strengths and weaknesses
   - Provides apply/skip recommendation

2. **Resume Customization**
   - ATS-optimized summary generation
   - Keyword optimization for specific jobs
   - Cover letter opening suggestions

3. **Interview Preparation**
   - Company research compilation
   - Likely interview questions with STAR examples
   - Questions to ask interviewers
   - Technical topics to review

4. **Skill Gap Analysis**
   - Identifies critical skill gaps
   - Learning resource recommendations
   - Portfolio project suggestions

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM |
| Database | Room |
| AI | Google Gemini SDK |
| Navigation | Compose Navigation |
| State | StateFlow + Coroutines |

## Setup

### Prerequisites
- Android Studio Hedgehog or later
- **JDK 17** (Adoptium Temurin recommended - see note below)
- Android SDK 33 or 34

> **⚠️ Important JDK Note**: The JetBrains Runtime (JBR) bundled with Android Studio has a known `jlink` compatibility issue with Android SDK modules. Use Adoptium Temurin JDK 17 for command-line builds:
> - Download from: https://adoptium.net/temurin/releases/?version=17
> - Set `JAVA_HOME` before building:
>   ```powershell
>   $env:JAVA_HOME = "C:\Path\To\temurin-17"
>   ./gradlew assembleDebug
>   ```

### Configuration

1. Clone the repository

2. Create `local.properties` in project root:
```properties
sdk.dir=C:/Users/YourName/AppData/Local/Android/Sdk
GEMINI_API_KEY=your_gemini_api_key_here
```

3. Get your Gemini API key from [ai.google.dev](https://ai.google.dev/)

4. Build and run:
```powershell
# Windows (PowerShell)
$env:JAVA_HOME = "C:\Users\YourName\.jdks\temurin-17"
./gradlew assembleDebug

# macOS/Linux
export JAVA_HOME=/path/to/temurin-17
./gradlew assembleDebug
```

5. Install APK on device:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

```
app/src/main/java/com/vignesh/jobautomation/
├── MainActivity.kt          # Entry point with navigation
├── ai/
│   └── GeminiClient.kt      # Gemini API integration
├── data/
│   ├── database/
│   │   └── Database.kt      # Room entities, DAOs, database
│   └── repository/
│       └── JobAutomationRepository.kt
├── ui/
│   ├── dashboard/           # Home screen
│   ├── jobs/                # Job listing and details
│   ├── applications/        # Application tracking
│   ├── chat/                # AI chat interface
│   ├── profile/             # User profile management
│   ├── companies/           # Company management
│   ├── settings/            # App settings
│   └── theme/               # Material 3 theming
└── viewmodel/
    └── ViewModels.kt        # All ViewModels
```

## Comparison: Standalone vs Client-Server

| Feature | Standalone | Client-Server |
|---------|------------|---------------|
| Backend Required | No | Yes (Python FastAPI) |
| Database | SQLite (Room) | PostgreSQL |
| Job Scraping | Manual entry only | Automated with Playwright |
| AI Integration | Direct Gemini SDK | Backend API calls |
| Gmail Integration | Not included | OAuth2 email tracking |
| Offline Support | Full | Limited |
| Setup Complexity | Easy | Complex |

## Usage

### Adding a Job
1. Navigate to Jobs tab
2. Tap the + button
3. Enter job details (title, company, URL, description)
4. Tap "Add Job"

### Analyzing a Job
1. Select a job in NEW status
2. Tap "Analyze with AI"
3. View match score and insights
4. Job auto-moves to READY_TO_APPLY or SKIPPED based on score

### Tracking Applications
1. Mark job as "Applied"
2. Navigate to Applications tab
3. Update status as you progress
4. Generate interview prep when scheduled

## License

MIT License - See LICENSE file

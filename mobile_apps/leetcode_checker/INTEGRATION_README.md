# Vignesh Personal Development

A comprehensive Android application combining **LeetCode consistency tracking** with **strategic AI-powered analysis chatbot**. Built with Jetpack Compose, MVVM architecture, and Retrofit for seamless integration with a Python backend.

## Features

### 📱 Two Main Sections

#### 1. **Leetcode Tab**
- Daily LeetCode challenge tracking
- Gemini AI-powered solution generation
- Manual API and LLM refresh
- Completion tracking with reminders
- Submission history & revision export
- Calendar event integration
- Mermaid flow diagram viewer

#### 2. **Strategic Chatbot Tab**
- Three conversation modes:
  - **Quick Chat**: Direct LLM responses (~$0.0002/call)
  - **Deep Analysis**: Full agentic pipeline (~$0.01/call)
  - **Follow-up**: RAG-based queries (~$0.001/call)
- Real-time cost tracking with budget visualization
- Session state management
- Example prompts for easy interaction
- Architecture visualization (Mermaid diagram)

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│        Android App (Jetpack Compose)                    │
│  ┌──────────────────────────────────────────────────┐  │
│  │  TabbedMainScreen (Bottom Navigation)            │  │
│  │  ├─ Leetcode Tab (LeetCodeCheckerScreen)        │  │
│  │  └─ Strategic Chatbot Tab (ChatbotScreen)       │  │
│  └──────────────────────────────────────────────────┘  │
│                         ↓                               │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Retrofit API Client (ChatbotViewModel)          │  │
│  │  ├─ ChatbotRepository                            │  │
│  │  └─ StrategicChatbotApi Interface               │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
                         ↓ (HTTP REST)
┌─────────────────────────────────────────────────────────┐
│  Python Backend: usecase_4_strategic_chatbot            │
│  (Running on localhost:7860)                            │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Orchestrator → ChatSession                      │  │
│  │  ├─ Quick Chat (Direct LLM)                     │  │
│  │  ├─ Deep Analysis (Agentic Pipeline)            │  │
│  │  └─ Follow-up (RAG Query)                       │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Pipeline Modules                                │  │
│  │  ├─ agents.py (AnalystAgent, FinancialModeler)  │  │
│  │  ├─ query_planner.py (SearchPlan generation)   │  │
│  │  ├─ web_fetcher.py (Dynamic data fetching)     │  │
│  │  ├─ retrieval/ (RAG pipeline)                  │  │
│  │  └─ cost_guard.py (Budget tracking)            │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │  External Services                               │  │
│  │  ├─ Gemini 2.5 Flash API                        │  │
│  │  ├─ Google News RSS                             │  │
│  │  ├─ SEC EDGAR API                               │  │
│  │  └─ Diskcache (Response caching)                │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

---

## Setup Instructions

### Prerequisites

| Item | Purpose |
|------|---------|
| **Android Studio** | IDE for building and running the app |
| **JDK 17+** | Kotlin/Java compilation |
| **Python 3.10+** | Backend server |
| **Gemini API Key** | LLM calls (get free at https://aistudio.google.com/apikey) |
| **usecase_4 backend** | Running on localhost:7860 |

### Step 1: Configure local.properties

Create `mobile_apps/leetcode_checker/local.properties`:

```properties
GEMINI_API_KEY=your-gemini-api-key
GITHUB_TOKEN=your-github-token
GITHUB_OWNER=YourGithubUsername
GITHUB_REPO=YourRepo
GITHUB_BRANCH=main
SETTINGS_UPDATE_PASSWORD=your-strong-password
```

### Step 2: Start the Python Backend

From the workspace root:

```bash
cd agentic_ai/usecase_4_strategic_chatbot

# Create/activate virtual environment
python -m venv .venv
source .venv/bin/activate  # or .venv\Scripts\activate on Windows

# Install dependencies
pip install -r requirements.txt

# Launch the chatbot server
python main.py

# Server runs at http://127.0.0.1:7860
```

> **Note:** Keep this terminal open while testing the app.

### Step 3: Build and Run Android App

In Android Studio:

1. Open `mobile_apps/leetcode_checker` as a project
2. Connect an emulator or physical device
3. Run **→ Run 'app'** or press `Shift+F10`

The app will connect to `http://10.0.2.2:7860` (Android emulator localhost).

> **Physical Device:** Change `10.0.2.2` to your computer's IP address in `ChatbotViewModel.kt` line 32.

---

## Usage Guide

### 📱 Leetcode Tab

Same as before—daily problem tracking with Gemini AI assistance.

### 💬 Strategic Chatbot Tab

1. **Select a Chat Mode**
   - **Quick Chat**: Fast, simple questions (costs least)
   - **Deep Analysis**: Detailed research with agentic pipeline (slower, costs more)
   - **Follow-up**: Use existing index for faster queries

2. **Choose Input Method**
   - Type your question directly
   - Click **📋 Examples** to see pre-written prompts
   - Click a prompt to fill the input field

3. **Monitor Cost & Session**:
   - 💰 **Cost Tracker**: Real-time budget usage
   - 📊 **Session Info**: Turn count, total cost, analysis status

4. **View Architecture**:
   - Click **🔗 Architecture** to see the full system diagram

5. **Reset if Needed**:
   - Click **Reset Session** to clear history and start fresh

---

## API Endpoints

The Android app communicates with the Python backend via REST:

| Endpoint | Method | Purpose | Cost |
|----------|--------|---------|------|
| `/api/chat/quick` | POST | Quick Chat mode | $0.0002 |
| `/api/chat/deep` | POST | Deep Analysis mode | $0.01 |
| `/api/chat/followup` | POST | Follow-up RAG query | $0.001 |
| `/api/session` | GET | Get session state | Free |
| `/api/session/cost` | GET | Get cost info | Free |
| `/api/session/reset` | POST | Reset session | Free |
| `/api/health` | GET | Health check | Free |

See [StrategicChatbotApi.kt](app/src/main/java/com/vignesh/leetcodechecker/api/StrategicChatbotApi.kt) for details.

---

## File Structure

```
mobile_apps/leetcode_checker/
├── app/src/main/
│   ├── java/com/vignesh/leetcodechecker/
│   │   ├── MainActivity.kt                    # Entry point
│   │   ├── LeetCodeViewModel.kt              # Leetcode state
│   │   ├── ConsistencyReminderScheduler.kt   # Reminders
│   │   ├── api/
│   │   │   └── StrategicChatbotApi.kt        # REST interface
│   │   ├── models/
│   │   │   └── ChatbotModels.kt              # Data classes
│   │   ├── repository/
│   │   │   └── ChatbotRepository.kt          # Data layer
│   │   ├── viewmodel/
│   │   │   └── ChatbotViewModel.kt           # Business logic
│   │   └── ui/
│   │       ├── TabbedMainScreen.kt           # Navigation
│   │       ├── StrategicChatbotScreen.kt     # Chat UI
│   │       ├── MermaidDiagram.kt             # Architecture viz
│   │       └── MainActivity.kt (composables) # Leetcode UI
│   ├── res/
│   │   ├── values/strings.xml                # App name
│   │   └── drawable/                         # Icons & images
│   └── AndroidManifest.xml                   # Permissions
├── build.gradle.kts                          # Dependencies
├── local.properties                          # API keys (git-ignored)
└── README.md                                 # This file
```

---

## Configuration

### Android App Settings (Leetcode Tab)

Password: Set in `SETTINGS_UPDATE_PASSWORD` (default: `1234`)

### Backend Settings (Strategic Chatbot)

Edit `agentic_ai/usecase_4_strategic_chatbot/chatbot_config.yaml`:

```yaml
billing:
  budget_usd: 5.0    # Daily limit
  dry_run: false     # Set true to avoid API costs

server:
  host: 127.0.0.1
  port: 7860
  share: false       # Set true for ngrok public URL
```

---

## Troubleshooting

### "Connection refused" error

**Problem**: App can't reach the backend.

**Solution**:
- Ensure Python backend is running: `python main.py`
- Check if it's on `http://127.0.0.1:7860`
- For physical device: Use your PC's IP instead of `10.0.2.2`

### "Budget exceeded" error

**Problem**: Daily API cost limit reached.

**Solution**:
- Click **Reset Session** to clear costly analyses
- Use **Quick Chat** mode (cheaper)
- Edit `chatbot_config.yaml` to raise `budget_usd`

### "No network" on Android 9+

**Problem**: App won't connect to localhost.

**Solution**:
- AndroidManifest.xml has `android:usesCleartextTraffic="true"` (already added)
- Confirm network_security_config.xml allows `127.0.0.1`

### Mermaid Diagram Not Showing

**Problem**: Architecture diagram is blank.

**Solution**:
- This is a text-based visualization, not an image
- Click the diagram area to scroll/zoom
- No external image file needed

---

## Development Notes

### Adding New Chat Modes

1. Add to `ChatMode` enum in [ChatbotModels.kt](app/src/main/java/com/vignesh/leetcodechecker/models/ChatbotModels.kt)
2. Add endpoint to [StrategicChatbotApi.kt](app/src/main/java/com/vignesh/leetcodechecker/api/StrategicChatbotApi.kt)
3. Handle in `ChatbotViewModel.sendMessage()`
4. Update UI radio buttons in [StrategicChatbotScreen.kt](app/src/main/java/com/vignesh/leetcodechecker/ui/StrategicChatbotScreen.kt)

### Extending the Backend

The backend (usecase_4) can be extended without modifying the app:
- Add new agents in `agents.py`
- Modify `orchestrator.py` to use them
- Expose new endpoints in `main.py`
- App will auto-connect via Retrofit

---

## Performance Tips

1. **Reduce Deep Analysis Cost**: Use Quick Chat for casual questions
2. **Leverage Follow-up Mode**: After deep analysis, ask follow-ups (cheaper)
3. **Cache Results**: Backend auto-caches responses; avoid duplicate queries
4. **Batch Analyses**: Group related questions into one deep analysis

---

## License & Credits

- **LeetCode Checker** portion: Original work
- **Strategic Chatbot** integration: adapts `agentic_ai/usecase_4_strategic_chatbot`
- **Gemini API**: Google (pricing subject to change)

---

## Contact & Support

For issues or feature requests, refer to the main Google_Prep README.

---

**Version**: 2.0 (Integrated with Strategic Chatbot)  
**Last Updated**: March 29, 2026

# Strategic Chatbot Mobile App — Quick Start

> Integrated into **Vignesh Personal Development** Android app  
> Location: `mobile_apps/leetcode_checker` (Strategic Chatbot tab)

---

## 🚀 Quick Setup (5 minutes)

### 1. Start the Python Backend

```bash
cd agentic_ai/usecase_4_strategic_chatbot
python -m venv .venv
source .venv/bin/activate  # Windows: .venv\Scripts\activate
pip install -r requirements.txt
python main.py
```

Output should show:
```
SERVER START — 2026-03-29 10:30:45
Persistent log file: outputs/server_log_20260329_103045.txt
Gradio URL: http://127.0.0.1:7860
```

### 2. Configure Android App

Edit `mobile_apps/leetcode_checker/local.properties`:
```properties
GEMINI_API_KEY=your-api-key-here
```

### 3. Run Android App

In Android Studio:
```
Run → Run 'app' (Shift+F10)
```

Choose **Strategic Chatbot** tab → Done! 🎉

---

## 💬 Using the Chatbot

### Quick Chat Example
**Mode**: Quick Chat  
**Input**: "What is quantum computing?"  
**Response**: ~2 seconds, Direct LLM answer  
**Cost**: $0.0002

### Deep Analysis Example
**Mode**: Deep Analysis  
**Input**: "Analyze the quantum computing landscape"  
**Response**: ~30-60 seconds, Full agentic research  
**Cost**: $0.01 (may vary)

### Follow-up Example
**Prerequisites**: Must have run Deep Analysis first  
**Mode**: Follow-up  
**Input**: "What about IBM's quantum efforts?"  
**Response**: ~5 seconds, RAG-based answer  
**Cost**: $0.001

---

## 🛠️ Key Components

| Component | File | Purpose |
|-----------|------|---------|
| **Main Screen** | `ui/StrategicChatbotScreen.kt` | Chat UI + mode selection |
| **ViewModel** | `viewmodel/ChatbotViewModel.kt` | Business logic |
| **Repository** | `repository/ChatbotRepository.kt` | API calls & caching |
| **API Client** | `api/StrategicChatbotApi.kt` | REST interface |
| **Models** | `models/ChatbotModels.kt` | Data classes |
| **Architecture Viz** | `ui/MermaidDiagram.kt` | System diagram |

---

## 📊 Cost Tracking

The app shows:
- **Total Cost**: $\text{already spent}$
- **Daily Budget**: $5.00 (configurable)
- **Remaining**: Daily budget - total cost
- **Progress Bar**: Visual % usage
- **Warning**: 🔴 when >80% spent

---

## 🔗 API Endpoints

Backend provides:

```
POST /api/chat/quick        — Quick Chat
POST /api/chat/deep         — Deep Analysis
POST /api/chat/followup     — Follow-up Query
GET  /api/session           — Session State
GET  /api/session/cost      — Cost Info
POST /api/session/reset     — Reset Session
GET  /api/health            — Health Check
```

---

## 🎯 Architecture Diagram

Click **🔗 Architecture** button inside app to view a text-based diagram of:

1. **Mobile Layer** (Android app)
2. **API Layer** (Retrofit REST)
3. **Backend** (usecase_4 orchestrator)
4. **Agentic Pipeline** (agents, query planning, RAG)
5. **External Services** (Gemini, Google News, SEC EDGAR)

---

## ⚙️ Configuration

### App Config
`local.properties`:
```properties
GEMINI_API_KEY=...
SETTINGS_UPDATE_PASSWORD=...
```

### Backend Config
`chatbot_config.yaml`:
```yaml
billing:
  budget_usd: 5.0
  dry_run: false    # Set true to test without API costs
server:
  port: 7860
```

---

## 🐛 Common Issues

| Issue | Solution |
|-------|----------|
| "Connection refused" | Ensure backend is running on port 7860 |
| "Budget exceeded" | Click **Reset Session** or raise budget in config |
| "No messages appear" | Check backend logs at `outputs/server_log_*.txt` |
| "Slow responses" | Deep Analysis can take 60+ seconds, be patient |

---

## 📈 Cost Examples

Typical usage costs:

| Activity | Mode | Cost | Time |
|----------|------|------|------|
| "What is AI?" | Quick | $0.0002 | 2s |
| "Analyze tech sector" | Deep | $0.01 | 45s |
| "Follow-up Q" | Follow-up | $0.001 | 5s |
| **Daily budget** | — | **$5.00** | — |

---

## 🔐 Security Notes

- **API Keys**: Stored in local.properties (git-ignored)
- **Cleartext Traffic**: Enabled for localhost only (AndroidManifest.xml)
- **Settings Password**: Required for Leetcode tab settings (Strategic Chatbot has no sensitive settings)

---

## 📝 Logging

Backend logs to:
- **Console**: Real-time updates
- **File**: `outputs/server_log_YYYYMMDD_HHMMSS.txt`
- **Prompts**: `outputs/chatbot_prompt_log_YYYYMMDD_HHMMSS.jsonl`

App logs to:
- **Logcat**: Android Studio → Logcat tab (filter: `ChatbotViewModel`, `ChatbotRepository`)

---

## 🚀 Next Steps

1. ✅ Start backend (`python main.py`)
2. ✅ Launch Android app  
3. ✅ Select **Strategic Chatbot** tab
4. ✅ Choose **Quick Chat** mode
5. ✅ Type a question & send
6. ✅ Watch cost tracker update

---

**Questions?** Check the full README at [INTEGRATION_README.md](INTEGRATION_README.md)

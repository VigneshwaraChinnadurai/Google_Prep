# Technical Design: Strategic Chatbot Mobile Integration

## System Context Diagram

```
┌─────────────────────────────────────────────────────┐
│                  End User                            │
└────────────────────┬────────────────────────────────┘
                     │
                     ↓
┌─────────────────────────────────────────────────────┐
│      Android Phone (Jetpack Compose)                │
│  ┌─────────────────────────────────────────────┐   │
│  │  MainActivity                               │   │
│  │  ├─ TabbedMainScreen                       │   │
│  │  │  ├─ LeetCode Tab                        │   │
│  │  │  │  └─ LeetCodeViewModel               │   │
│  │  │  │                                      │   │
│  │  │  └─ Strategic Chatbot Tab              │   │
│  │  │     ├─ StrategicChatbotScreen          │   │
│  │  │     └─ ChatbotViewModel                │   │
│  │  │        ├─ ChatbotRepository            │   │
│  │  │        └─ StrategicChatbotApi          │   │
│  │  └─ (Retrofit)                            │   │
│  └─────────────────────────────────────────────┘   │
└────────────────────┬────────────────────────────────┘
                     │ HTTP/REST
                     ↓
┌─────────────────────────────────────────────────────┐
│   Python Backend (usecase_4_strategic_chatbot)     │
│   Running on localhost:7860 (Gradio)               │
│  ┌─────────────────────────────────────────────┐   │
│  │  orchestrator.py                            │   │
│  │  ├─ ChatSession (per-request session)      │   │
│  │  │  ├─ quick_chat() → str                  │   │
│  │  │  ├─ deep_analysis() → Generator[str]    │   │
│  │  │  └─ follow_up() → Generator[str]        │   │
│  │  │                                         │   │
│  │  ├─ GraphMemory (knowledge graph)          │   │
│  │  ├─ Conversation history (10 turns max)   │   │
│  │  └─ Analysis result cache                  │   │
│  │                                             │   │
│  │  query_planner.py                          │   │
│  │  └─ generate_search_plan()                 │   │
│  │     → SearchPlan (companies, queries)      │   │
│  │                                             │   │
│  │  agents.py                                 │   │
│  │  ├─ NewsAndDataAgent (Google News)        │   │
│  │  ├─ FinancialModelerAgent (SEC EDGAR)     │   │
│  │  ├─ AnalystAgent (LLM-driven loops)       │   │
│  │  ├─ CritiqueModule (quality checks)       │   │
│  │  └─ GraphMemory.add_edge()               │   │
│  │                                             │   │
│  │  Retrieval Pipeline (RAG)                  │   │
│  │  ├─ web_fetcher.py (query web data)       │   │
│  │  ├─ chunker.py (semantic chunking)        │   │
│  │  ├─ sparse_retrieval.py (BM25)            │   │
│  │  ├─ dense_retrieval.py (embeddings)       │   │
│  │  ├─ hybrid_index.py (RRF fusion)          │   │
│  │  ├─ reranker.py (LLM scoring)             │   │
│  │  └─ context_fusion.py (dedup)             │   │
│  │                                             │   │
│  │  cost_guard.py                             │   │
│  │  └─ CostGuard (budget enforcement)        │   │
│  │                                             │   │
│  │  llm_client.py                             │   │
│  │  └─ GeminiClient (REST to Gemini API)    │   │
│  └─────────────────────────────────────────────┘   │
└────────────────────┬────────────────────────────────┘
                     │ HTTP/REST
                     ├─────────────┬──────────────────┐
                     ↓             ↓                  ↓
          ┌──────────────────┐  ┌──────────────┐  ┌──────────┐
          │  Gemini API      │  │ Google News  │  │SEC EDGAR │
          │ (LLM calls)      │  │ RSS (free)   │  │ API      │
          │ $0.15/$0.60 /1M  │  │              │  │ (free)   │
          │ tokens           │  │              │  │          │
          └──────────────────┘  └──────────────┘  └──────────┘
```

---

## Data Flow: Quick Chat Mode

```
User Input
    ↓
[StrategicChatbotScreen.kt]
    ├─ Display loading indicator
    └─ OnSend → viewModel.sendMessage("what is AI?", mode=QUICK_CHAT)
        ↓
[ChatbotViewModel.kt]
    ├─ setChatMode(QUICK_CHAT)
    └─ sendMessage() → repository.sendQuickChat(message)
        ↓
[ChatbotRepository.kt]
    ├─ _uiState.update { isLoading = true }
    ├─ _messages.update { + userMessage }
    ├─ api.quickChat(ChatRequest(message, "quick"))
    │   ↓
    │ [StrategicChatbotApi.kt]
    │   ├─ Retrofit HTTP: POST /api/chat/quick
    │   ├─ Moshi JSON serialization
    │   └─ Timeout: 30 seconds
    │       ↓
    │   [Backend: orchestrator.py]
    │       ├─ Create ChatSession (if new)
    │       ├─ Append userMessage to conversation
    │       ├─ Build system prompt + context
    │       ├─ Call llm.generate(prompt)
    │       ├─ Append assistantMessage
    │       ├─ Return ChatResponse(response, cost=0.0002, ...)
    │       ↓
    │   [API Response]
    │       ↓
    ├─ _messages.update { + assistantMessage }
    ├─ updateCostInfo() → api.getCostInfo()
    ├─ _uiState.update { isLoading = false, successMessage = "..." }
    └─ Return Result.success(response)
        ↓
[StrategicChatbotScreen.kt]
    ├─ Display assistant message in chat history
    ├─ Clear input field
    ├─ Update cost tracker (💰)
    └─ Show success toast (✅)
```

---

## Data Flow: Deep Analysis Mode

```
User Input
    ↓
[StrategicChatbotScreen.kt]
    ├─ OnSend → viewModel.sendMessage("analyze quantum computing", mode=DEEP_ANALYSIS)
        ↓
[ChatbotViewModel.kt]
    └─ sendMessage() → repository.deepAnalysis(message)
        ↓
[ChatbotRepository.kt]
    ├─ _uiState.update { isLoading = true }
    ├─ _messages.update { + userMessage }
    ├─ api.deepAnalysis(ChatRequest(message, "deep"))
    │   ↓
    │ [StrategicChatbotApi.kt]
    │   ├─ Retrofit HTTP: POST /api/chat/deep
    │   └─ Timeout: 120 seconds (long-running)
    │       ↓
    │   [Backend: orchestrator.py → deep_analysis()]
    │       ├─ Step 1: Generate SearchPlan from user prompt
    │       │   └─ query_planner.generate_search_plan()
    │       │       └─ LLM decomposes question → companies, queries, perspective
    │       │
    │       ├─ Step 2: Fetch & Index data (via heartbeat for SSE keep-alive)
    │       │   └─ web_fetcher.fetch_and_index()
    │       │       ├─ Fetch Google News (RSS)
    │       │       ├─ Fetch SEC EDGAR (XBRL API)
    │       │       └─ Fetch web search results
    │       │           ↓
    │       │       ├─ chunker.split_sentences()
    │       │       ├─ sparse_retrieval.build_bm25()
    │       │       ├─ dense_retrieval.embed_chunks()
    │       │       ├─ hybrid_index.fuse_rankings()
    │       │       └─ Store in .cache/gemini/
    │       │
    │       ├─ Step 3: Run Agentic Pipeline
    │       │   ├─ NewsAndDataAgent.fetch_news()
    │       │   ├─ FinancialModelerAgent.extract_financials()
    │       │   ├─ AnalystAgent.run_analysis_loop()
    │       │   │   └─ LLM multi-turn analysis
    │       │   ├─ CritiqueModule.critique()
    │       │   │   └─ Quality checks
    │       │   └─ GraphMemory.add_edge() → knowledge graph
    │       │
    │       ├─ Step 4: Generate final response
    │       │   └─ LLM synthesis of all findings
    │       │
    │       └─ Return ChatResponse(response, cost=0.01, tokens=..., analysis_result={...})
    │       ↓
    │   [Streaming Response - Chunked/SSE]
    │   └─ Backend streams progress updates:
    │       "Fetching news... 0%"
    │       "Building index... 30%"
    │       "Running agents... 60%"
    │       "Generating response... 90%"
    │       "Complete: {full response}"
    │
    ├─ _messages.update { + assistantMessage }
    ├─ updateCostInfo()
    ├─ _uiState.update { isLoading = false, successMessage = "Deep analysis completed" }
    └─ Return Result.success(response)
        ↓
[StrategicChatbotScreen.kt]
    ├─ Display full response in chat bubble
    ├─ Update cost tracker (💰 now shows $0.01+)
    ├─ Enable Follow-up mode (index is built)
    └─ Show success toast (✅)
```

---

## Data Flow: Follow-up Mode

```
User Input
    ↓
[StrategicChatbotScreen.kt]
    ├─ OnSend → viewModel.sendMessage("what about IBM?", mode=FOLLOW_UP)
    └─ (Only available after Deep Analysis)
        ↓
[ChatbotRepository.kt]
    ├─ api.followUp(ChatRequest(message, "followup"))
    │   ↓
    │ [Backend: orchestrator.py → follow_up()]
    │   ├─ Retrieve from existing index (no new fetches)
    │   ├─ Hybrid search (BM25 + dense embeddings)
    │   ├─ Rerank results (LLM-based scoring)
    │   ├─ Fuse top-K contexts
    │   ├─ RAG: LLM generates response from context
    │   └─ Return ChatResponse(response, cost=0.001, ...)
    │       ↓
    ├─ _messages.update { + assistantMessage }
    ├─ updateCostInfo() (cheaper than Deep!)
    └─ Return Result.success(response)
        ↓
[StrategicChatbotScreen.kt]
    ├─ Display response
    └─ Update cost tracker (💰)
```

---

## Error Handling Flow

```
Any API Call
    ├─ Try block
    │   ├─ Make HTTP request
    │   ├─ Parse JSON response
    │   └─ Return Result.success(...)
    │
    └─ Catch block
        ├─ Log error: Log.e(TAG, "Failed: ...", exception)
        ├─ Update UI state: _uiState.update { errorMessage = "Error: ${e.localizedMessage}" }
        ├─ Display error card: ⚠️ {error message}
        └─ Return Result.failure(exception)
```

---

## State Management

### ChatUIState (Composite State)
```kotlin
data class ChatUIState(
    val isLoading: Boolean,              // "thinking..." indicator
    val messages: List<ChatMessage>,     // Full history
    val costInfo: CostInfo,              // Budget tracking
    val sessionState: SessionState?,     // Session metadata
    val errorMessage: String?,           // Error toast
    val successMessage: String?          // Success toast
)
```

### Flow-Based Updates
```
✅ StateFlow (collect in UI)
    ├─ uiState (composite)
    ├─ messages (individual)
    ├─ costInfo (cost tracking)
    └─ sessionState (session metadata)

✅ Reactive Updates
    ├─ collectAsStateWithLifecycle() (lifecycle-aware)
    ├─ LaunchedEffect() (side effects)
    └─ rememberSaveable() (configuration changes)

✅ No LiveData
    └─ Compose StateFlow is better
```

---

## Timeout Configuration

| Operation | Timeout | Reason |
|-----------|---------|--------|
| Quick Chat | 30s | Immediate LLM response |
| Deep Analysis | 120s | Web fetching + indexing + agentic loops |
| Follow-up | 30s | RAG on existing index |
| Connection | 30s | TCP handshake |

---

## Cost Calculation

### Quick Chat
```
Input Tokens   ~150 (conversation history + prompt)
Output Tokens  ~200 (LLM response)
Cost = (150 × $0.15 + 200 × $0.60) / 1,000,000 = $0.00015
```

### Deep Analysis
```
Input Tokens   ~5000 (search results + context + agents)
Output Tokens  ~2000 (analysis + synthesis)
Cost = (5000 × $0.15 + 2000 × $0.60) / 1,000,000 = $0.00195 (+ web/index overhead)
Typical = $0.01 (variable based on query scope)
```

### Follow-up
```
Input Tokens   ~1000 (RAG context + question)
Output Tokens  ~300 (short answer)
Cost = (1000 × $0.15 + 300 × $0.60) / 1,000,000 = $0.00033
Typical = $0.001
```

---

## Security & Isolation

### Session Isolation
```
Each HTTP request creates new ChatSession
    ├─ Session ID: UUID (stored on backend)
    ├─ Memory: Cleared on session end
    ├─ Index: Per-session (not shared)
    └─ Conversation: Max 10 turns (memory limit)

App-side
    ├─ No persistent session storage
    ├─ Reset Session = new request
    └─ No credentials cached (except GEMINI_API_KEY)
```

### API Key Security
```
✅ Stored in: local.properties (git-ignored)
✅ Passed to: BuildConfig.GEMINI_API_KEY
✅ Sent in: HTTP header (via Retrofit interceptor)
⚠️  Transmitted: Over cleartext (localhost only)
✅ Not logged: API keys excluded from logcat
```

---

## Testing Strategy

### Unit Tests (Kotlin)
```
✅ ChatbotViewModel.sendMessage()
✅ ChatbotRepository.sendQuickChat()
✅ Data model serialization (ChatMessage → JSON)
```

### Integration Tests (Retrofit)
```
✅ StrategicChatbotApi endpoints
✅ Moshi JSON parsing
✅ Timeout handling
```

### UI Tests (Compose)
```
✅ Tab switching
✅ Message sending & display
✅ Cost tracker updates
✅ Mode selection
✅ Reset session
```

### Manual Testing
```
✅ Backend connectivity
✅ Real API calls (Quick Chat)
✅ Deep Analysis (30-60s)
✅ Follow-up availability
✅ Cost accumulation
✅ Error messages
✅ Zoom/pan in diagram
```

---

## Performance Metrics

### Expected Latency
| Operation | Time | Note |
|-----------|------|------|
| Quick Chat | 2-5s | Network + LLM |
| Deep Analysis | 30-120s | Web fetch + index + agents |
| Follow-up | 5-10s | RAG retrieval + LLM |
| Cost Query | <1s | Cache lookup |

### Resource Usage
| Resource | Typical | Peak |
|----------|---------|------|
| Memory | ~150MB | ~300MB (during indexing) |
| Network | ~500KB | ~5MB (Deep Analysis fetch) |
| CPU | <10% | 50% (embedding generation) |
| Storage | ~100KB | ~10MB (index cache) |

---

## Deployment Checklist

### Development
- [ ] Backend running on `http://127.0.0.1:7860`
- [ ] App targeting `http://10.0.2.2:7860` (emulator)
- [ ] GEMINI_API_KEY set in local.properties
- [ ] Logcat showing "Server running OK" messages

### Physical Device
- [ ] Change `10.0.2.2` → your PC's IP in ChatbotViewModel.kt
- [ ] Backend accessible from device network
- [ ] Both on same WiFi
- [ ] Firewall allows port 7860

### Production
- [ ] Disable cleartext traffic (HTTPS only)
- [ ] Use environment secrets (not local.properties)
- [ ] Enable ProGuard/R8 minification
- [ ] Release build signed with keystore

---

## Future Extensibility

### Adding New Chat Modes
1. Add to `ChatMode` enum
2. Add endpoint to `StrategicChatbotApi`
3. Handle in `ChatbotViewModel.sendMessage()`
4. Update UI radio buttons

### Extending Backend
- Add agents to `agents.py`
- Modify `orchestrator.py`
- Expose new endpoints
- App automatically connects (Retrofit is flexible)

### Custom LLM Models
- Update `chatbot_config.yaml`
- Backend routes to different LLM
- App sees same API = no changes needed

---

## Monitoring & Debugging

### Backend Logs
```
Location: agentic_ai/usecase_4_strategic_chatbot/outputs/
├── server_log_20260329_103045.txt           (all events)
├── chatbot_prompt_log_20260329_103045.jsonl (LLM prompts)
└── cache/gemini/                            (response cache)
```

### App Logs
```
Filter: ChatbotViewModel | ChatbotRepository
Log.d("ChatbotViewModel", "Quick chat completed...")
Log.e("ChatbotRepository", "Failed to send message", exception)
```

### Cost Tracking
```
Real-time: _costInfo StateFlow updates
Persistent: Backend returns cost_usd in each response
No: Local app storage of costs (stateless design)
```

---

**Version**: 1.0  
**Last Updated**: March 29, 2026  
**Status**: Complete & Documented

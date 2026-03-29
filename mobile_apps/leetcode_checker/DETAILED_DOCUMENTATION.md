# Vignesh Personal Development — Detailed Documentation & Working Guide

> **App Name:** Vignesh Personal Development  
> **Package:** `com.vignesh.leetcodechecker`  
> **Platform:** Android (API 24+ / Android 7.0+)  
> **Architecture:** MVVM with Jetpack Compose  
> **Last Updated:** March 29, 2026

---

## Table of Contents

1. [Overview](#1-overview)
2. [Architecture & Tech Stack](#2-architecture--tech-stack)
3. [Project Structure](#3-project-structure)
4. [Build & Configuration](#4-build--configuration)
5. [Tab 1: LeetCode (Gemini)](#5-tab-1-leetcode-gemini)
6. [Tab 2: Ollama (Local LLM)](#6-tab-2-ollama-local-llm)
7. [Tab 3: Strategic Chatbot](#7-tab-3-strategic-chatbot)
8. [Deep Analysis Pipeline](#8-deep-analysis-pipeline)
9. [RAG Retrieval System](#9-rag-retrieval-system)
10. [Logging & Observability](#10-logging--observability)
11. [Session Management](#11-session-management)
12. [Cost Tracking](#12-cost-tracking)
13. [Shared Components](#13-shared-components)
14. [Settings & Customization](#14-settings--customization)
15. [Data Flow Diagrams](#15-data-flow-diagrams)
16. [Troubleshooting](#16-troubleshooting)

---

## 1. Overview

**Vignesh Personal Development** is a multi-purpose Android application with three main tabs:

| Tab | Icon | Purpose | LLM Backend |
|-----|------|---------|-------------|
| **Leetcode** | 🏠 Home | Daily LeetCode practice checker with AI grading | Gemini API (cloud) |
| **Ollama** | 🔧 Build | Same LeetCode checking but using local Ollama LLM | Ollama (on-device/local) |
| **Chatbot** | ⚙️ Settings | Strategic analysis chatbot with full agentic pipeline | Gemini API (cloud) |

All three tabs work **entirely without a backend server** — the app makes direct API calls to Gemini (Google) or Ollama (local) from the device.

### Key Capabilities

- **LeetCode Consistency Tracking**: Fetches daily LeetCode questions from a GitHub repo, checks solutions against AI, tracks streaks
- **Multi-Model Support**: Automatic model fallback chain (gemini-2.5-pro → gemini-pro-latest, etc.)
- **Deep Analysis Pipeline**: Full 10-step agentic pipeline with real-time data (news, SEC filings, web search), RAG retrieval, self-critique, and strategy generation
- **Multi-Session Management**: Create, switch, rename, delete chat sessions with full persistence
- **Comprehensive Logging**: Every LLM call, prompt, response, timing, and cost is logged and viewable in-app
- **Real-Time Cost Tracking**: Per-model token pricing with budget guards
- **Markdown Rendering**: Rich markdown output with headers, bold, italic, lists, code blocks, tables
- **Mermaid Diagram Viewer**: Architecture diagrams rendered from Mermaid syntax
- **Flexible Configuration**: 20+ settings configurable in-app with password protection

---

## 2. Architecture & Tech Stack

### MVVM Architecture

```
┌─────────────────────────────────────────────────────┐
│                    UI Layer (Compose)                │
│  LeetCodeScreen  OllamaLeetCodeScreen  ChatbotScreen│
├─────────────────────────────────────────────────────┤
│                  ViewModel Layer                     │
│  LeetCodeViewModel  OllamaViewModel  ChatbotViewModel│
├─────────────────────────────────────────────────────┤
│                Data / Pipeline Layer                 │
│  LeetCodeRepo  OllamaRepo  DeepAnalysisPipeline     │
│  GeminiApi     OllamaApi   Agents / RAG / DataFetcher│
├─────────────────────────────────────────────────────┤
│              Persistence / Storage                   │
│  SharedPreferences  ChatSessionManager               │
│  AppSettingsStore   ConsistencyStorage               │
└─────────────────────────────────────────────────────┘
```

### Tech Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Kotlin | 1.9.24 |
| UI Framework | Jetpack Compose | BOM 2024.09.01 |
| Design System | Material3 | Latest via BOM |
| Build Tool | Gradle (Kotlin DSL) | AGP 8.6.1 |
| Networking | Retrofit 2 + OkHttp | 2.11.0 |
| JSON | Moshi (with Kotlin reflection) | 1.15.1 |
| Coroutines | Kotlin Coroutines | 1.8.1 |
| Lifecycle | AndroidX Lifecycle | 2.8.6 |
| Min SDK | Android 7.0 (Nougat) | API 24 |
| Target SDK | Android 15 | API 35 |
| Java | OpenJDK | 17 |

### Key Design Decisions

1. **No backend server** — All LLM calls go directly from the Android app to the API
2. **No Room/SQLite** — All persistence uses SharedPreferences with JSON serialization (simpler, sufficient for chat data)
3. **No Hilt/Dagger** — Manual dependency injection via ViewModel constructors (keeps the app lightweight)
4. **Flow-based pipeline** — Deep Analysis uses Kotlin Flow for real-time streaming of pipeline status updates
5. **Logging wrapper pattern** — `LoggingGeminiApi` wraps the Retrofit interface transparently to intercept every call

---

## 3. Project Structure

```
app/src/main/java/com/vignesh/leetcodechecker/
│
├── MainActivity.kt                  # Entry point, theme setup, tab navigation
├── AppSettingsStore.kt              # Settings persistence (20+ fields)
├── ConsistencyStorage.kt           # LeetCode challenge/result caching
├── ConsistencyReminderScheduler.kt # Hourly reminder scheduling
├── ConsistencyReminderReceiver.kt  # BroadcastReceiver for reminders
├── LeetCodeViewModel.kt            # ViewModel for LeetCode tab
├── RevisionExportManager.kt        # Export revision data
│
├── models/
│   └── ChatbotModels.kt            # Data classes: ChatMessage, CostInfo, ChatUIState, ChatMode, etc.
│
├── viewmodel/
│   ├── ChatbotViewModel.kt         # Strategic Chatbot ViewModel (785 lines)
│   └── OllamaViewModel.kt          # Ollama tab ViewModel
│
├── data/
│   ├── GeminiApi.kt                # Retrofit interface for Gemini API (all request/response models)
│   ├── LeetCodeApi.kt              # Retrofit interface for LeetCode-specific Gemini calls
│   ├── LeetCodeRepository.kt       # GitHub fetch + AI grading logic
│   ├── OllamaApi.kt                # Retrofit interface for Ollama local API
│   ├── OllamaRepository.kt         # Ollama model management + code checking
│   ├── GitHubApi.kt                # GitHub API for fetching repo content
│   ├── ChatbotLogger.kt            # Comprehensive logging + LoggingGeminiApi wrapper
│   ├── ChatSessionManager.kt       # Multi-session persistence manager
│   └── ChatHistoryStore.kt         # Legacy single-session persistence
│
├── repository/
│   └── ChatbotRepository.kt        # (Thin repository for chatbot, mostly handled by ViewModel)
│
├── api/
│   └── StrategicChatbotApi.kt      # (Legacy API interface, replaced by direct Gemini calls)
│
├── pipeline/
│   ├── DeepAnalysisPipeline.kt     # Main 10-step pipeline orchestrator (375 lines)
│   ├── Agents.kt                   # QueryPlanner, AnalystAgent, CritiqueModule (740 lines)
│   ├── DataFetcher.kt              # Multi-source data fetching (Gemini Grounded, SEC, News)
│   ├── PipelineModels.kt           # SearchPlan, GraphMemory, PipelineCostGuard, JSON schemas
│   ├── HybridRetrieval.kt          # RetrievalPipeline: RRF of BM25 + Dense + LLM reranking
│   ├── BM25Index.kt                # Okapi BM25 sparse retrieval
│   ├── DenseIndex.kt               # Dense vector retrieval using Gemini embeddings
│   └── TextProcessing.kt           # Chunking, ContextFusion, MetadataEnrichment
│
└── ui/
    ├── TabbedMainScreen.kt         # Bottom navigation bar with 3 tabs
    ├── LeetCodeScreen.kt           # LeetCode tab UI (~1465 lines)
    ├── OllamaLeetCodeScreen.kt     # Ollama tab UI (~1040 lines)
    ├── StrategicChatbotScreen.kt   # Chatbot tab UI (~1191 lines)
    ├── MarkdownText.kt             # Pure Compose markdown renderer (250 lines)
    └── MermaidDiagram.kt           # Mermaid diagram viewer (273 lines)
```

**Total:** ~35 Kotlin files, ~8,700+ lines of code

---

## 4. Build & Configuration

### Prerequisites

- Android Studio (Hedgehog 2023.1.1 or later)
- JDK 17
- Android SDK 35
- (Optional) Ollama running locally for Tab 2

### Configuration Files

#### `local.properties` (in project root, NOT committed to git)

```properties
# Required for LeetCode tab
GEMINI_API_KEY=your-gemini-api-key-here

# Required for Chatbot tab (or falls back to GEMINI_API_KEY)
CHATBOT_GEMINI_API_KEY=your-chatbot-gemini-key

# GitHub Integration (for fetching LeetCode questions)
GITHUB_TOKEN=ghp_your_github_token
GITHUB_OWNER=VigneshwaraChinnadurai
GITHUB_REPO=Google_Prep
GITHUB_BRANCH=main

# Settings protection
SETTINGS_UPDATE_PASSWORD=1234

# Ollama (optional, defaults shown)
OLLAMA_BASE_URL=http://127.0.0.1:11434
OLLAMA_MODEL=qwen2.5:3b
```

#### Alternative: `.env` file for Chatbot key

The build script also checks `agentic_ai/usecase_4_strategic_chatbot/.env` for:
```
GEMINI_API_KEY=your-key-here
```

### Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires keystore.properties)
./gradlew assembleRelease

# APK location
app/build/outputs/apk/debug/app-debug.apk
```

### `BuildConfig` Fields

All configuration is injected at build time via `BuildConfig`:

| Field | Source | Description |
|-------|--------|-------------|
| `GEMINI_API_KEY` | local.properties | Primary Gemini API key |
| `CHATBOT_GEMINI_API_KEY` | .env or local.properties | Chatbot-specific key |
| `GITHUB_TOKEN` | local.properties | GitHub Personal Access Token |
| `GITHUB_OWNER` | local.properties | GitHub username |
| `GITHUB_REPO` | local.properties | Repository name |
| `GITHUB_BRANCH` | local.properties | Branch to fetch from |
| `SETTINGS_UPDATE_PASSWORD` | local.properties | Password for in-app settings |
| `OLLAMA_BASE_URL` | local.properties | Ollama server URL |
| `OLLAMA_MODEL` | local.properties | Default Ollama model |

---

## 5. Tab 1: LeetCode (Gemini)

### Purpose

Daily LeetCode practice assistant that:
1. Fetches today's question from a GitHub repository
2. Lets the user write/paste their solution
3. Sends the question + solution to Gemini for AI grading
4. Tracks consistency streaks and revision history
5. Supports model selection and fallback chains

### Key Files

| File | Lines | Responsibility |
|------|-------|---------------|
| `ui/LeetCodeScreen.kt` | ~1465 | Full UI: question display, code editor, results, settings |
| `LeetCodeViewModel.kt` | ~395 | State management, orchestration |
| `data/LeetCodeRepository.kt` | ~587 | GitHub fetch, Gemini grading, model fallback |
| `data/LeetCodeApi.kt` | ~120 | Retrofit interface for Gemini |
| `data/GitHubApi.kt` | ~50 | GitHub content API |

### Workflow

```
User opens app
    │
    ├── Auto-fetches today's question from GitHub
    │   (GitHub API → Leetcode_QA_Revision/{date}/question.txt)
    │
    ├── User writes/pastes solution
    │
    ├── Taps "Check Solution"
    │   │
    │   ├── Also fetches reference answer.py from GitHub
    │   ├── Builds prompt: question + user solution + reference answer
    │   ├── Sends to Gemini (with model fallback chain)
    │   └── Returns grading with: correctness, complexity, improvements
    │
    └── Consistency tracking
        ├── Tracks daily check-ins
        ├── Shows streak count
        └── Hourly reminders (9 AM - 10 PM IST)
```

### GitHub Repository Structure Expected

```
Leetcode_QA_Revision/
    2026-03-29/
        question.txt    # Today's LeetCode question
        answer.py       # Reference solution
        explanation.txt  # Explanation
```

### Model Fallback Chain

The app tries models in order from `preferredModelsCsv` setting (default: `gemini-2.5-pro,gemini-pro-latest`). If one model fails (rate limit, error, etc.), it automatically tries the next.

### Features

- **Question Browser**: Navigate to any date's question
- **Markdown Rendering**: AI responses rendered with rich formatting
- **Copy/Share**: Copy results to clipboard
- **Settings Panel**: Configure models, tokens, timeouts, GitHub integration
- **Revision Export**: Export revision data
- **Consistency Reminders**: Hourly Android notifications (configurable hours)

---

## 6. Tab 2: Ollama (Local LLM)

### Purpose

Same LeetCode checking functionality as Tab 1, but using a local Ollama server instead of cloud Gemini. This enables:
- **Offline operation** (no internet needed after model download)
- **Privacy** (code never leaves the device/local network)
- **Cost savings** (no API charges)

### Key Files

| File | Lines | Responsibility |
|------|-------|---------------|
| `ui/OllamaLeetCodeScreen.kt` | ~1040 | UI with Ollama-specific features |
| `viewmodel/OllamaViewModel.kt` | ~322 | Ollama state management |
| `data/OllamaRepository.kt` | ~603 | Ollama API calls, model management |
| `data/OllamaApi.kt` | ~80 | Retrofit interface for Ollama |

### Ollama API Integration

The app communicates with Ollama via its REST API:

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/tags` | GET | List installed models |
| `/api/generate` | POST | Generate text completion |
| `/api/show` | POST | Get model details |
| `/api/pull` | POST | Download a new model |

### Features Unique to Ollama Tab

- **Server Connection Test**: Ping Ollama server with visual status indicator
- **Model Catalog**: Browse and download models from Ollama registry
- **Model Selector**: Switch between installed models
- **Full Settings Panel**: Configure Ollama URL, model preferences
- **View More**: Expand/collapse long responses
- **Diagram Viewer**: Mermaid architecture diagrams

### Setup Requirements

1. Install Ollama on your PC/Mac: https://ollama.ai
2. Pull a model: `ollama pull qwen2.5:3b`
3. Start Ollama server (usually auto-starts)
4. Configure the URL in the app:
   - **Emulator**: `http://10.0.2.2:11434` (Android emulator loopback)
   - **Physical device on same WiFi**: `http://<PC-IP>:11434`

---

## 7. Tab 3: Strategic Chatbot

### Purpose

A full-featured strategic analysis chatbot that can:
- **Quick Chat**: Direct conversation with Gemini for strategic questions
- **Deep Analysis**: Run a complete 10-step agentic pipeline with real-time data
- **Follow-up**: Query the RAG index built during Deep Analysis

### Key Files

| File | Lines | Responsibility |
|------|-------|---------------|
| `ui/StrategicChatbotScreen.kt` | ~1191 | Full chatbot UI with modes, sessions, logs |
| `viewmodel/ChatbotViewModel.kt` | ~785 | Chat logic, pipeline orchestration, sessions |
| `models/ChatbotModels.kt` | ~123 | ChatMessage, CostInfo, ChatUIState, ChatMode |

### Chat Modes

#### 1. Quick Chat (💬)

Direct Q&A with Gemini. Maintains conversation context by sending the last N messages as context.

```
User: "What's Apple's competitive position in AR/VR?"
    │
    ├── Build prompt with system instruction + conversation history
    ├── Send to gemini-2.5-flash
    └── Return markdown-formatted response
```

**System Prompt:**
> You are a strategic analysis assistant. You can analyse any industry, company, or strategic topic. Provide thoughtful, data-aware responses.

#### 2. Deep Analysis (🔬)

Full 10-step pipeline execution. See [Section 8](#8-deep-analysis-pipeline) for details.

#### 3. Follow-up (🔄)

Two-tier follow-up system:
- **If RAG index is available**: Full vector-based retrieval on the indexed documents
- **If RAG index is lost** (app restart): Context-based fallback using chat history to find the last Deep Analysis result

### UI Components

- **Mode Selector**: Chip row to switch between Quick Chat / Deep Analysis / Follow-up
- **Quick Actions**: "New Session", "Sessions", "📋 Logs" chips
- **Chat Bubble List**: Scrollable message list with user/assistant bubbles
- **Markdown Rendering**: Full markdown support in assistant messages
- **Session Browser**: Bottom sheet with session list, rename, delete
- **Logs Panel**: Expandable log viewer with type filters

---

## 8. Deep Analysis Pipeline

### Overview

A full 10-step agentic pipeline ported from a Python backend (`agentic_ai/usecase_3`), running entirely on-device via direct Gemini API calls. No server required.

### Pipeline Steps

```
Step 0:  Query Planning (QueryPlanner)
    │    LLM generates a SearchPlan from the user's strategic question
    │    Output: domain, companies, grounding queries, news queries, SEC queries
    │
Step 1:  Planning (AnalystAgent.buildPlan)
    │    LLM decomposes into 5-8 concrete execution steps
    │
Step 2:  Fetch & Index (DataFetcher + RetrievalPipeline)
    │    Fetches data from multiple sources:
    │    - Gemini Grounded Search (web)
    │    - SEC EDGAR (10-K/10-Q filings)
    │    - Google News RSS
    │    Then: chunk → embed → build BM25 + Dense indexes
    │
Step 3:  Financial Retrieval + Extraction
    │    Hybrid search → LLM extracts structured financial metrics
    │
Step 4:  Market Share Delta
    │    Pure math computation from extracted financials
    │
Step 5:  Qualitative Signals
    │    Second retrieval pass for competitive signals
    │
Step 6:  Threat/Strategic Analysis (AnalystAgent.analyseThreat)
    │    LLM synthesizes financials + signals into strategic analysis
    │
Step 7:  Critique (CritiqueModule)
    │    LLM quality gate: scores depth, evidence, causality, actionability
    │    Verdict: PASS (score ≥ 7) or NEEDS_REFINEMENT
    │
Step 7b: Refinement Loop (0-2 iterations)
    │    If NEEDS_REFINEMENT: generate new queries → fetch more data → re-analyze
    │
Step 8:  Strategy Generation (AnalystAgent.generateStrategies)
    │    LLM generates 3 actionable strategic recommendations
    │
Step 9:  Memory Extraction
    │    LLM extracts knowledge graph edges for persistent memory
    │
Step 10: Executive Summary (AnalystAgent.synthesiseSummary)
         LLM writes board-level executive summary
```

### Agents

| Agent | Role | Key Methods |
|-------|------|-------------|
| `QueryPlanner` | Generates SearchPlan from any strategic question | `generateSearchPlan()` |
| `AnalystAgent` | Main analyst: plan, extract, analyze, strategize | `buildPlan()`, `extractFinancials()`, `analyseThreat()`, `generateStrategies()`, `synthesiseSummary()`, `extractMemory()` |
| `CritiqueModule` | Quality gate scoring | `critique()` → returns scores + PASS/NEEDS_REFINEMENT |
| `DataFetcher` | Multi-source data collection | `fetchAll()` → documents from web, SEC, news |

### Data Sources

| Source | API | Data Type |
|--------|-----|-----------|
| Gemini Grounded Search | `gemini-2.5-flash` with `google_search_retrieval` tool | Real-time web data |
| SEC EDGAR | `efts.sec.gov/LATEST/search-index` | 10-K, 10-Q financial filings |
| Google News RSS | `news.google.com/rss/search` | Recent news articles |

### Pipeline Output Format

```markdown
## 📄 Executive Summary
[Board-level summary of findings]

## ⚡ Key Analysis
[Detailed strategic analysis with data points]

## 💡 Strategies
### 1. [Strategy Name]
- Action item 1
- Action item 2
- Action item 3
**Cost:** [Estimated cost] | **Outcome:** [Expected impact]

### 2. [Strategy Name]
...

---
*💰 Total cost: $X.XXXX (N API calls) | M input tokens, K output tokens*
```

---

## 9. RAG Retrieval System

### Architecture

The retrieval system uses **Hybrid Search** with Reciprocal Rank Fusion (RRF):

```
Query
  │
  ├── BM25 (Sparse) ──────────┐
  │   Okapi BM25 with          │
  │   TF-IDF term matching     │
  │                             ├── RRF Fusion ── LLM Reranking ── ContextFusion
  ├── Dense (Vector) ──────────┘
  │   Gemini embedding-001
  │   Cosine similarity
  │
  └── Output: Fused context string
```

### Components

| File | Class | Responsibility |
|------|-------|---------------|
| `HybridRetrieval.kt` | `RetrievalPipeline` | Orchestrates ingest + query; RRF + LLM reranking |
| `BM25Index.kt` | `BM25Index` | Okapi BM25 sparse retrieval (TF-IDF variant) |
| `DenseIndex.kt` | `DenseIndex` | Dense vector retrieval using `gemini-embedding-001` |
| `TextProcessing.kt` | `TextChunker` | Splits documents into overlapping chunks |
| `TextProcessing.kt` | `ContextFuser` | Merges retrieved chunks into coherent context |
| `TextProcessing.kt` | `MetadataEnricher` | Adds source/company metadata to chunks |

### Ingest Flow

```
Documents (from DataFetcher)
    │
    ├── MetadataEnricher: Add company tags
    ├── TextChunker: Split into ~500-char chunks with overlap
    ├── BM25Index.add(): Index with term frequencies
    └── DenseIndex.add(): Embed with Gemini → store vectors
```

### Query Flow

```
Query string
    │
    ├── BM25Index.search(query, k=10) → ranked results
    ├── DenseIndex.search(query, k=10) → ranked results
    ├── RRF Fusion: Combine rankings (k=60)
    ├── LLM Reranking: Gemini re-scores top candidates
    └── ContextFuser: Merge into single context string (≤4000 chars)
```

---

## 10. Logging & Observability

### LoggingGeminiApi Pattern

`LoggingGeminiApi` implements the `GeminiApi` Retrofit interface and wraps the real Retrofit client. Every API call passes through it:

```
ViewModel / Pipeline / Agents
    │
    └── LoggingGeminiApi (wrapper)
        ├── Logs prompt, model, config BEFORE the call
        ├── Delegates to real Retrofit GeminiApi
        ├── Logs response, timing, finish reason AFTER the call
        ├── Records token usage in PipelineCostGuard (from usageMetadata)
        └── Logs errors with stack traces on failure
```

### Log Entry Types

| Type | Icon | What It Captures |
|------|------|-----------------|
| `LLM_REQUEST` | 📤 | Model, system prompt, user prompt, temperature, maxTokens |
| `LLM_RESPONSE` | 📥 | Response text, duration (ms), finish reason, token counts |
| `LLM_ERROR` | ❌ | Model, error message, duration |
| `EMBED_REQUEST` | 🔢 | Model, text count, total characters |
| `EMBED_RESPONSE` | 🔢 | Embedding count, duration |
| `PIPELINE_STEP` | ⚙️ | Step name, status message |
| `SESSION_EVENT` | 📂 | Session create/switch/delete/rename |
| `MODE_SWITCH` | 🔄 | Chat mode changes |
| `COST_UPDATE` | 💰 | Per-call cost, total cost, API call count |
| `DATA_FETCH` | 📡 | Data source, fetch details |
| `INDEX_EVENT` | 📇 | Index build/query events |
| `INFO` | ℹ️ | General information |
| `WARNING` | ⚠️ | Non-critical warnings |
| `ERROR` | ❌ | Critical errors |

### Viewing Logs In-App

1. Open the **Chatbot** tab
2. Tap the **📋 Logs** chip (shows live entry count)
3. Use **filter chips** to narrow: All, LLM, Response, Pipeline, Embed, Errors, Session, Cost
4. **Tap any entry** to expand and see full details (prompt text, response text, timing)
5. **Copy** all logs to clipboard or **Clear** them

### Log Storage

- **In-memory only** — logs are lost on app restart
- Capped at **2000 entries** to prevent OOM
- Thread-safe with synchronized access
- Also printed to Android logcat with tag `ChatbotLogger`

---

## 11. Session Management

### Overview

The Chatbot tab supports multiple independent chat sessions with full persistence.

### Features

| Feature | How |
|---------|-----|
| **New Session** | Tap "New Session" chip → saves current, creates blank session |
| **Browse Sessions** | Tap "Sessions" chip → bottom sheet with session list |
| **Switch Session** | Tap a session in the list → loads its messages and state |
| **Rename Session** | Long-press or tap rename icon → enter new name |
| **Delete Session** | Swipe or tap delete icon → confirms and removes |
| **Auto-Save** | Every message exchange is persisted immediately |

### Data Model

```kotlin
data class ChatSession(
    val id: String,          // UUID
    val name: String,        // "Session Mar 29, 14:30" or custom name
    val createdAt: Long,     // Creation timestamp
    val updatedAt: Long,     // Last activity timestamp
    val messages: List<ChatMessage>,
    val totalCostUsd: Double,
    val apiCalls: Int,
    val turnCount: Int,
    val chatMode: String     // "QUICK_CHAT", "DEEP_ANALYSIS", "FOLLOW_UP"
)
```

### Persistence

`ChatSessionManager` uses **SharedPreferences** with JSON serialization:

- **Session List**: Stored as JSON array of session metadata
- **Session Data**: Each session's messages stored separately by session ID
- **Active Session ID**: Tracked to restore on app restart
- **Max Sessions**: No hard limit (but storage is bounded by SharedPreferences capacity)

### Important Note

The **RAG index** (BM25 + Dense vectors) built during Deep Analysis is **in-memory only**. When you switch sessions or restart the app:
- The index is lost
- Follow-up mode falls back to **context-based** answering (uses chat history instead of RAG)
- To get full RAG follow-up, re-run Deep Analysis in the current session

---

## 12. Cost Tracking

### How It Works

Every Gemini API call returns `usageMetadata` with actual token counts:

```json
{
  "usageMetadata": {
    "promptTokenCount": 1523,
    "candidatesTokenCount": 847,
    "totalTokenCount": 2370
  }
}
```

The `LoggingGeminiApi` wrapper intercepts this and calls `PipelineCostGuard.record()`:

```kotlin
fun record(inputTokens: Int, outputTokens: Int, model: String): Double {
    val (inputPrice, outputPrice) = priceFor(model)  // per 1M tokens
    val cost = (inputTokens * inputPrice + outputTokens * outputPrice) / 1_000_000.0
    totalInputTokens += inputTokens
    totalOutputTokens += outputTokens
    totalCostUsd += cost
    apiCalls++
    return cost
}
```

### Per-Model Pricing (per 1M tokens)

| Model | Input Price | Output Price |
|-------|------------|-------------|
| gemini-2.5-flash | $0.15 | $0.60 |
| gemini-2.0-flash | $0.10 | $0.40 |
| gemini-1.5-flash | $0.075 | $0.30 |
| gemini-2.5-pro | $1.25 | $10.00 |
| embedding models | $0.00 | $0.00 |
| (fallback) | $0.15 | $0.60 |

### Budget Guard

`PipelineCostGuard` has a configurable budget (default: $0.50 per pipeline run). If exceeded, it throws `BudgetExceededException` which gracefully terminates the pipeline with a budget warning.

### Cost Display

- **In pipeline output**: Footer shows total cost, API calls, input/output tokens
- **In logs**: Every API call logs its individual cost contribution
- **In cost info bar**: ViewModel exposes `costInfo` StateFlow for UI display

---

## 13. Shared Components

### MarkdownText (Compose)

A pure-Compose markdown renderer supporting:

| Element | Syntax | Rendering |
|---------|--------|-----------|
| Headers | `# H1`, `## H2`, `### H3` | Sized, bold text |
| Bold | `**text**` | Bold weight |
| Italic | `*text*` | Italic style |
| Code inline | `` `code` `` | Monospace with background |
| Code blocks | ```` ```lang ... ``` ```` | Dark background, monospace |
| Bullet lists | `- item` or `* item` | Indented with bullet |
| Numbered lists | `1. item` | Indented with number |
| Tables | `| col | col |` | Grid layout |
| Horizontal rules | `---` | Divider line |
| Links | `[text](url)` | Clickable, colored text |

### MermaidDiagram

Renders Mermaid diagram syntax as a visual architecture diagram using a WebView with the Mermaid.js library.

### ConsistencyStorage

Caches LeetCode challenge data and AI results to avoid redundant API calls:

```kotlin
class ConsistencyStorage(context: Context) {
    fun getCachedChallenge(date: String): Challenge?
    fun cacheChallenge(date: String, challenge: Challenge)
    fun getCachedResult(date: String): AIResult?
    fun cacheResult(date: String, result: AIResult)
}
```

---

## 14. Settings & Customization

### AppSettings Data Class (20+ fields)

| Setting | Default | Description |
|---------|---------|-------------|
| `landingTitle` | "Vignesh Daily Activities Checker" | App header title |
| `checkerTitle` | "LeetCode Consistency Checker" | LeetCode tab title |
| `promptName` | "Prompt for Leetcode_solver" | Prompt template name |
| `preferredModelsCsv` | "gemini-2.5-pro,gemini-pro-latest" | Model fallback chain |
| `maxModelRetries` | 3 | Max retries per model |
| `maxInputTokens` | 1,048,576 | Max input context size |
| `maxOutputTokens` | 65,535 | Max output tokens |
| `thinkingBudgetDivisor` | 4 | Thinking budget = maxOutput / divisor |
| `networkTimeoutMinutes` | 15 | HTTP timeout |
| `reminderStartHourIst` | 9 | Reminder start (IST) |
| `reminderEndHourIst` | 22 | Reminder end (IST) |
| `reminderIntervalHours` | 1 | Reminder frequency |
| `revisionFolderName` | "Leetcode_QA_Revision" | GitHub folder path |
| `githubOwnerOverride` | "" | Override GitHub owner |
| `githubRepoOverride` | "" | Override GitHub repo |
| `githubBranchOverride` | "" | Override GitHub branch |
| `chatbotBackendUrl` | "" | Legacy backend URL |
| `ollamaBaseUrl` | "http://127.0.0.1:11434" | Ollama server URL |
| `ollamaPreferredModels` | "qwen2.5:3b" | Preferred Ollama model |

### Password Protection

Settings updates require the `SETTINGS_UPDATE_PASSWORD` (default: "1234", configurable in `local.properties`).

### Persistence

Settings are stored in SharedPreferences (`"leetcode_settings_prefs"`) as a JSON object. Changes take effect immediately.

---

## 15. Data Flow Diagrams

### LeetCode Tab Flow

```
┌──────────┐   GitHub API    ┌──────────────┐
│   User   │ ──────────────→ │  GitHub Repo │
│  (App)   │ ←────────────── │ (questions)  │
└──────────┘   question.txt  └──────────────┘
     │
     │ user solution
     ▼
┌──────────────────┐
│  LeetCodeRepo    │
│  buildPrompt()   │
│  + model fallback│
└──────────────────┘
     │
     │ Gemini API call
     ▼
┌──────────────────┐
│  Gemini API      │
│  (cloud)         │
└──────────────────┘
     │
     │ AI grading response
     ▼
┌──────────────────┐
│  MarkdownText    │
│  (rendered UI)   │
└──────────────────┘
```

### Deep Analysis Pipeline Flow

```
┌──────────┐
│  User    │ "Analyze Apple vs Google in cloud computing"
│  Query   │
└──────────┘
     │
     ▼
┌──────────────────┐    ┌─────────────────┐
│  QueryPlanner    │───→│  SearchPlan     │
│  (LLM)          │    │  domain, queries│
└──────────────────┘    └─────────────────┘
                              │
                    ┌─────────┼─────────┐
                    ▼         ▼         ▼
              ┌──────────┐┌──────┐┌──────────┐
              │Grounded  ││ SEC  ││ Google   │
              │Search    ││EDGAR ││ News RSS │
              └──────────┘└──────┘└──────────┘
                    │         │         │
                    └─────────┼─────────┘
                              ▼
                    ┌─────────────────┐
                    │  TextChunker    │
                    │  + Embeddings   │
                    ├─────────────────┤
                    │ BM25 │ Dense    │
                    │ Index│ Index    │
                    └─────────────────┘
                              │
                    ┌─────────┼─────────┐
                    ▼         ▼         ▼
              ┌──────────┐┌──────┐┌──────────┐
              │Financial ││Market││Strategic │
              │Extraction││Share ││Analysis  │
              │(LLM)     ││Delta ││(LLM)     │
              └──────────┘└──────┘└──────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │  Critique Gate  │
                    │  Score ≥ 7? ────┤─ No → Refinement Loop
                    │  (LLM)   │     │
                    └──────────│─────┘
                               ▼
                    ┌─────────────────┐
                    │ Strategies (LLM)│
                    │ Memory (LLM)    │
                    │ Summary (LLM)   │
                    └─────────────────┘
                               │
                               ▼
                    ┌─────────────────┐
                    │  Final Report   │
                    │  (Markdown)     │
                    └─────────────────┘
```

### Logging Wrapper Flow

```
      Any code calling geminiApi.generateContent()
                    │
                    ▼
      ┌─────────────────────────────┐
      │      LoggingGeminiApi       │
      │  ┌───────────────────────┐  │
      │  │ 1. Log REQUEST        │  │
      │  │    (prompt, model,    │  │
      │  │     temperature)      │  │
      │  └───────────┬───────────┘  │
      │              ▼              │
      │  ┌───────────────────────┐  │
      │  │ 2. delegate.generate  │  │──→ Real Retrofit HTTP call
      │  │    Content(...)       │  │←── Gemini JSON response
      │  └───────────┬───────────┘  │
      │              ▼              │
      │  ┌───────────────────────┐  │
      │  │ 3. Log RESPONSE       │  │
      │  │    (text, timing,     │  │
      │  │     finish reason)    │  │
      │  └───────────┬───────────┘  │
      │              ▼              │
      │  ┌───────────────────────┐  │
      │  │ 4. Record COST        │  │──→ PipelineCostGuard.record()
      │  │    (usageMetadata     │  │    using actual token counts
      │  │     tokens)           │  │
      │  └───────────────────────┘  │
      └─────────────────────────────┘
```

---

## 16. Troubleshooting

### Common Issues

| Issue | Cause | Fix |
|-------|-------|-----|
| "No API key configured" | Missing `GEMINI_API_KEY` in local.properties | Add key to local.properties or .env |
| Ollama connection failed | Ollama server not running or wrong URL | Start Ollama, check URL (10.0.2.2 for emulator) |
| "No analysis index available" | Index lost after app restart | Re-run Deep Analysis, or follow-up uses context fallback |
| Model rate limit errors | Too many requests | Wait and retry, or switch models in settings |
| Build fails: "SDK not found" | Missing `sdk.dir` in local.properties | Add `sdk.dir=C:\\Users\\...\\Android\\Sdk` |
| Cost showing $0.0000 | Old version without cost wiring | Update to latest version with LoggingGeminiApi cost tracking |
| Settings won't save | Wrong password | Enter the correct `SETTINGS_UPDATE_PASSWORD` (default: 1234) |
| Blank Ollama response | Model not installed | Pull a model first: `ollama pull qwen2.5:3b` |

### Debug Tips

1. **Check Logs Tab**: The 📋 Logs chip in the Chatbot tab shows every LLM call with full prompts and responses
2. **Android Logcat**: Filter by tags `ChatbotLogger`, `DeepAnalysisPipeline`, `QueryPlanner`, `AnalystAgent`
3. **Cost Verification**: After Deep Analysis, check the footer shows non-zero cost. If zero, ensure `LoggingGeminiApi.costGuard` is wired
4. **Network Issues**: Long timeouts (120s read, 30s connect) are configured. If still timing out, check network connectivity

### Performance Notes

- **Deep Analysis** typically takes 2-5 minutes and makes 10-20 API calls
- **Typical cost**: $0.01 - $0.05 per full pipeline run (with gemini-2.5-flash)
- **Memory usage**: RAG index with 100+ chunks uses ~5-10 MB RAM
- **SharedPreferences limit**: ~1 MB per session (sufficient for hundreds of messages)

---

## Appendix: File Line Counts

| File | Lines | Category |
|------|-------|----------|
| `ui/LeetCodeScreen.kt` | ~1465 | UI |
| `ui/StrategicChatbotScreen.kt` | ~1191 | UI |
| `ui/OllamaLeetCodeScreen.kt` | ~1040 | UI |
| `viewmodel/ChatbotViewModel.kt` | ~785 | ViewModel |
| `pipeline/Agents.kt` | ~740 | Pipeline |
| `data/OllamaRepository.kt` | ~603 | Data |
| `data/LeetCodeRepository.kt` | ~587 | Data |
| `data/ChatbotLogger.kt` | ~450 | Logging |
| `pipeline/DataFetcher.kt` | ~411 | Pipeline |
| `LeetCodeViewModel.kt` | ~395 | ViewModel |
| `pipeline/DeepAnalysisPipeline.kt` | ~375 | Pipeline |
| `viewmodel/OllamaViewModel.kt` | ~322 | ViewModel |
| `data/ChatSessionManager.kt` | ~322 | Data |
| `pipeline/PipelineModels.kt` | ~296 | Pipeline |
| `ui/MermaidDiagram.kt` | ~273 | UI |
| `pipeline/HybridRetrieval.kt` | ~263 | Pipeline |
| `ui/MarkdownText.kt` | ~250 | UI |
| `pipeline/TextProcessing.kt` | ~182 | Pipeline |
| `ConsistencyStorage.kt` | ~179 | Storage |
| `pipeline/DenseIndex.kt` | ~157 | Pipeline |
| `data/GeminiApi.kt` | ~154 | API |
| `models/ChatbotModels.kt` | ~123 | Models |
| `data/ChatHistoryStore.kt` | ~118 | Data |
| `pipeline/BM25Index.kt` | ~102 | Pipeline |
| `ui/TabbedMainScreen.kt` | ~100 | UI |
| `AppSettingsStore.kt` | ~92 | Settings |
| `data/OllamaApi.kt` | ~80 | API |
| `MainActivity.kt` | ~60 | Entry |
| **Total** | **~8,700+** | |

---

*Documentation generated for the Vignesh Personal Development Android app, March 2026.*

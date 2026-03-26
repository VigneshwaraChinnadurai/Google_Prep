# LeetCode Checker (Ollama) — Deep-Dive Architecture & Execution Walkthrough

> **Purpose:** This document walks through every component of the LeetCode Consistency Checker **Ollama variant** — the fully offline/self-hosted version that runs local LLMs instead of cloud Gemini — with the **why** behind each design decision, actual code, and the complete execution flow.
>
> **App Package:** `com.vignesh.leedcodecheckerollama`
> **LLM Backend:** Ollama (self-hosted, any GGUF model — default `qwen2.5:3b`)
> **Min SDK:** 24 (Android 7.0) | **Target SDK:** 35

---

## Table of Contents

1. [Why a Separate Ollama Variant?](#1-why-a-separate-ollama-variant)
2. [Architecture Differences vs Gemini Variant](#2-architecture-differences-vs-gemini-variant)
3. [Ollama-Specific Components](#3-ollama-specific-components)
4. [Complete Execution Flow](#4-complete-execution-flow)
5. [Model Management System](#5-model-management-system)
6. [Network & Connectivity](#6-network--connectivity)
7. [Troubleshooting & Diagnostics](#7-troubleshooting--diagnostics)
8. [Shared Components (Same as Gemini)](#8-shared-components-same-as-gemini)
9. [Build System Differences](#9-build-system-differences)
10. [When to Use Which Variant](#10-when-to-use-which-variant)

---

## 1. Why a Separate Ollama Variant?

The Gemini variant sends your LeetCode problems to Google's cloud API. This variant talks to an **Ollama server** instead — either:

| Deployment | Setup | Latency | Quality |
|---|---|---|---|
| **PC via ADB reverse** | `adb reverse tcp:11434 tcp:11434` | ~2-30s | Depends on GPU + model |
| **Same-WiFi server** | Set Ollama URL to `http://<server-ip>:11434/` | ~5-60s | Same |
| **On-device (Termux)** | Ollama installed on phone directly | ~30-120s | Limited by phone SoC |

**Key advantages over the Gemini variant:**
- **Zero API cost** — no per-token billing, unlimited usage
- **Privacy** — your code never leaves your network
- **Offline-capable** — works without internet (if model is already pulled)
- **Model flexibility** — use any GGUF model: `qwen2.5:3b`, `llama3.1:8b`, `codellama:13b`, etc.

**Tradeoff:** Solution quality depends entirely on your chosen model. `qwen2.5:3b` is acceptable for Easy/Medium problems; Hard problems often need 8B+ parameter models or Gemini.

---

## 2. Architecture Differences vs Gemini Variant

Both apps share ~80% of their code. Here's what's different:

### Component Comparison

| Component | Gemini Variant | Ollama Variant |
|---|---|---|
| **LLM API** | `GeminiApi.kt` (REST to Google) | `OllamaApi.kt` (REST to local Ollama) |
| **Model selection** | `geminiApi.listModels()` | `ollamaApi.listTags()` + catalog |
| **API key** | `GEMINI_API_KEY` in BuildConfig | None needed — Ollama has no auth |
| **Settings: extra field** | — | `ollamaBaseUrl` (server address) |
| **Settings: default models** | `gemini-2.5-pro,gemini-pro-latest` | `qwen2.5:3b,llama3.1:8b` |
| **ViewModel: extra methods** | — | `refreshInstalledModels()`, `downloadOllamaModel()`, `runOllamaDiagnostics()`, `setPreferredModel()` |
| **UiState: extra fields** | — | `catalogOllamaModels`, `installedOllamaModels`, `isModelActionLoading`, `modelDownloadProgress` |
| **Repository** | Gemini-specific pipeline | Ollama-specific pipeline + diagnostics |
| **Build config** | `GEMINI_API_KEY` | `OLLAMA_BASE_URL`, `OLLAMA_MODEL` |
| **Thinking config** | `thinkingConfig: { thinkingBudget }` | Not applicable (Ollama models don't have external thinking API) |
| **Model download** | N/A (cloud models) | In-app download via `POST /api/pull` with streaming progress |

### Shared (Identical) Components

| File | Purpose |
|---|---|
| `ConsistencyStorage.kt` | SharedPreferences: challenge, AI, completion status |
| `ConsistencyReminderScheduler.kt` | AlarmManager hourly reminders |
| `ConsistencyReminderReceiver.kt` | Notification display with IST window check |
| `RevisionExportManager.kt` | Build revision files, local write, GitHub push |
| `data/LeetCodeApi.kt` | LeetCode GraphQL API interface |
| `data/GitHubApi.kt` | GitHub Contents API interface |
| Calendar integration | `insertCompletionCalendarEvent()`, `deleteCompletionCalendarEvent()` |

---

## 3. Ollama-Specific Components

### 3.1 `data/OllamaApi.kt` (85 lines) — Retrofit Interface

```kotlin
data class OllamaGenerateRequest(
    val model: String,
    val prompt: String,
    val system: String,
    val stream: Boolean = false,
    val options: OllamaOptions? = null
)

data class OllamaOptions(
    val temperature: Double = 0.2,
    @Json(name = "num_predict")
    val numPredict: Int = 4096
)
```

**Ollama REST API endpoints used:**

| Endpoint | Method | Purpose |
|---|---|---|
| `GET /api/tags` | GET | List locally installed models |
| `POST /api/generate` | POST | Generate text (non-streaming) |
| `POST /api/pull` | POST | Download/pull a model |
| `GET ollama.com/api/tags` | GET | List catalog models (remote) |

**Why `stream: false`?**
```kotlin
val request = OllamaGenerateRequest(
    model = model,
    system = systemPrompt,
    prompt = userPrompt,
    stream = false,  // ← wait for complete response
    ...
)
```

Streaming would require parsing NDJSON (newline-delimited JSON) — each line is a partial token. For a code generation use case, we need the complete output before parsing XML tags (`<leetcode_python3_code>...</leetcode_python3_code>`). Non-streaming simplifies the code at the cost of perceived latency (no progressive display).

**Exception:** Model download DOES use streaming:
```kotlin
@Streaming
@POST("api/pull")
suspend fun pullModelStream(@Body body: OllamaPullRequest): ResponseBody
```
This enables real-time progress reporting ("downloading 45%") during multi-GB model downloads.

### 3.2 `data/LeetCodeRepository.kt` (689 lines) — Ollama Pipeline

The repository is ~100 lines longer than the Gemini variant due to:
1. **Model availability checking** (`ensureModelAvailable()`)
2. **Auto-pull** — if a model isn't local, automatically pull it
3. **Connection diagnostics** (`runOllamaConnectionDiagnostics()`)
4. **Base URL resolution** (`resolveOllamaBaseUrl()`)
5. **Catalog integration** (`listCatalogModels()`)

#### Key difference: No API key, no thinking budget

```kotlin
// Gemini variant:
generationConfig = GeminiGenerationConfig(
    maxOutputTokens = maxOutputTokens,
    thinkingConfig = GeminiThinkingConfig(thinkingBudget = maxThinkingBudget)
)

// Ollama variant:
options = OllamaOptions(
    temperature = 0.2,
    numPredict = maxOutputTokens  // ← Ollama's equivalent of maxOutputTokens
)
```

Ollama models don't have a separate "thinking" token budget. `numPredict` directly controls the maximum number of output tokens. Some models (like `qwen2.5` with `think` tag) have internal CoT, but it's not configurable via API.

#### Auto-pull: `ensureModelAvailable()`

```kotlin
private suspend fun ensureModelAvailable(model: String, debug: StringBuilder) {
    val localNames = ollamaApi().listTags().models.orEmpty()
        .mapNotNull { it.name }
        .map { normalizeModelName(it) }

    if (localNames.any { it == normalizedTarget }) {
        logDebug(debug, "Model already present on device: $normalizedTarget")
        return  // ← model exists, proceed to generation
    }

    logDebug(debug, "Model not present. Starting download via /api/pull")
    val pullResponse = ollamaApi().pullModel(OllamaPullRequest(model = model, stream = false))
    // ... verify model appears in tags after pull ...
}
```

**Why auto-pull instead of requiring manual download?** Convenience. If a user configures `codellama:7b` in settings but hasn't downloaded it yet, the pipeline automatically pulls it before generating. This is the **lazy initialization** pattern applied to ML models.

**Tradeoff:** First-time generation for a new model includes download time (minutes to hours depending on model size and connection). The debug log clearly shows "Starting download via /api/pull" so the user understands the delay.

#### Connection Diagnostics

```kotlin
suspend fun runOllamaConnectionDiagnostics(): String {
    val report = StringBuilder()
    val baseUrl = resolveOllamaBaseUrl()

    if (host == "127.0.0.1" || host == "localhost") {
        report.appendLine("Detected localhost/loopback base URL.")
        report.appendLine("If Ollama runs on PC, keep adb reverse active")
    }

    return runCatching {
        val tags = ollamaApi().listTags().models
        report.appendLine("Connectivity: SUCCESS")
        report.appendLine("Model count: ${tags.size}")
        ...
    }.getOrElse { error ->
        report.appendLine("Connectivity: FAILED")
        buildOllamaTroubleshootingHints(baseUrl, host, error)
            .forEach { hint -> report.appendLine("- $hint") }
        ...
    }
}
```

**Why built-in diagnostics?** Ollama networking is the #1 source of user confusion:
- Is the server running?
- Is `adb reverse` active?
- Is the firewall blocking port 11434?
- Is cleartext HTTP allowed in Android's network security config?

The diagnostics function checks connectivity and generates targeted troubleshooting hints. This saves enormous back-and-forth debugging time.

#### Troubleshooting Hints Generator

```kotlin
private fun buildOllamaTroubleshootingHints(baseUrl: String, host: String, error: Throwable): List<String> {
    val msg = error.message.orEmpty().lowercase()

    if (msg.contains("cleartext") || msg.contains("network security policy")) {
        hints += "HTTP cleartext is blocked. Ensure network_security_config allows cleartext."
    }

    if (host == "127.0.0.1" || host == "localhost") {
        hints += "If phone should use PC Ollama, run: adb reverse tcp:11434 tcp:11434"
        hints += "If Ollama runs on another laptop, set URL to http://<laptop-ip>:11434/"
    } else {
        hints += "Verify $baseUrl is reachable from phone browser (same Wi-Fi)."
        hints += "Allow inbound TCP 11434 on server firewall."
        hints += "Run Ollama with: OLLAMA_HOST=0.0.0.0:11434"
    }
}
```

**Why different hints for localhost vs remote?** The troubleshooting steps are completely different:
- **Localhost** → `adb reverse` issue (99% of the time)
- **Remote** → firewall, Wi-Fi, or `OLLAMA_HOST` binding issue

### 3.3 `LeetCodeViewModel.kt` (527 lines) — Extended ViewModel

The Ollama ViewModel adds model management methods not present in the Gemini variant:

```kotlin
// Extra UiState fields:
val catalogOllamaModels: List<OllamaModelInfo> = emptyList()
val installedOllamaModels: List<OllamaModelInfo> = emptyList()
val isModelActionLoading: Boolean = false
val modelDownloadProgress: String? = null
```

#### Model Download with Progress

```kotlin
fun downloadOllamaModel(modelName: String) {
    viewModelScope.launch {
        repository.downloadModel(target) { progressText ->
            _uiState.value = _uiState.value.copy(modelDownloadProgress = progressText)
        }
        .onSuccess {
            // Auto-set as preferred model
            val preferred = currentSettings.preferredModelsCsv.split(',').toMutableList()
            preferred.removeAll { it.equals(target, ignoreCase = true) }
            preferred.add(0, target)  // move to front
            // Save updated settings
        }
    }
}
```

**Why auto-set as preferred?** If you explicitly download a model, you almost certainly want to use it next. Adding it to the front of the preferred list avoids a separate "now go to settings and select it" step.

#### Model Selection

```kotlin
fun setPreferredModel(modelName: String) {
    val preferred = currentSettings.preferredModelsCsv.split(',').toMutableList()
    preferred.removeAll { it.equals(target, ignoreCase = true) }
    preferred.add(0, target)  // move to front of priority list
}
```

The preferred models CSV is an **ordered priority list**. The first model that's actually installed gets selected. This handles the case where you have `codellama:13b,qwen2.5:3b` configured but only `qwen2.5:3b` is downloaded — it gracefully falls back.

### 3.4 `AppSettingsStore.kt` — Extra Ollama Fields

```kotlin
data class AppSettings(
    ...
    val ollamaBaseUrl: String = BuildConfig.OLLAMA_BASE_URL,  // ← EXTRA
    val preferredModelsCsv: String = "qwen2.5:3b,llama3.1:8b",  // ← different defaults
    ...
)
```

**Why configurable base URL in settings (not just BuildConfig)?** 
- `BuildConfig` requires a rebuild to change
- Settings can be changed at runtime — critical when switching between `adb reverse` (localhost), Wi-Fi (remote IP), or different servers

---

## 4. Complete Execution Flow

### Step 1: App Launch (Identical to Gemini + model loading)
```
MainActivity.onCreate()
  → ConsistencyReminderScheduler.ensureHourlyReminder()
  → setContent { LeetCodeCheckerScreen() }
      → LeetCodeViewModel created
          → loadFromLocalStorage()
          → refreshLocalRevisionHistory()
          → refreshInstalledModels()    ← EXTRA: queries Ollama for local models
          → refreshCatalogModels()      ← EXTRA: queries ollama.com for all available models
          → collect repository.liveDebugLog
```

### Step 2: "Refresh LeetCode API" (Identical to Gemini)
```
viewModel.refreshApiChallenge()
  → repository.fetchDailyChallenge()  // LeetCode GraphQL, same as Gemini variant
```

### Step 3: "Refresh LLM Answer" (Ollama Pipeline)
```
viewModel.refreshLlmAnswer()
  → repository.generateDetailedAnswer(challenge, forceRefresh=true)
      → Build system prompt + user prompt (same tags format)
      → resolveOllamaBaseUrl()           // settings → BuildConfig fallback
      → ollamaApi().listTags()           // what models are installed?
      → Select first available preferred model
      → ensureModelAvailable():
          → Model present? → proceed
          → Model missing? → ollamaApi().pullModel() → verify → proceed
      → generateWithRetry():
          → POST /api/generate { model, system, prompt, stream:false, options }
          → Parse response.response (the generated text)
          → Extract <leetcode_python3_code>, <testcase_validation>, <explanation>
      → Return AiGenerationResult
```

### Step 4-6: Identical to Gemini
Edit → Mark Completed → Push to GitHub (same code in both variants).

---

## 5. Model Management System

### How Models Work in Ollama

```
Ollama Server (PC/laptop/phone)
  └── Models stored at ~/.ollama/models/
      ├── qwen2.5:3b         (1.9 GB)
      ├── llama3.1:8b         (4.7 GB)
      └── codellama:13b       (7.4 GB)
```

Each model is a GGUF file (quantized weights). The Ollama server loads models on-demand when a generation request arrives.

### In-App Model Flow

```
App Settings Screen:
  ┌─────────────────────────────────────────┐
  │ Installed Models  (from /api/tags)      │
  │  ✓ qwen2.5:3b          [Use This]      │
  │  ✓ llama3.1:8b         [Use This]      │
  │                                         │
  │ Available for Download (ollama.com)     │
  │  ○ codellama:13b       [Download]       │
  │  ○ deepseek-coder:6.7b [Download]       │
  │                                         │
  │ Download Progress: downloading (72%)    │
  │                                         │
  │ Manual download: [______________] [Pull]│
  └─────────────────────────────────────────┘
```

### Download with Streaming Progress

```kotlin
// Repository:
suspend fun downloadModel(model: String, onProgress: (String) -> Unit): Result<String> {
    ollamaApi().pullModelStream(OllamaPullRequest(model, stream = true)).use { responseBody ->
        responseBody.charStream().buffered().useLines { lines ->
            lines.forEach { line ->
                val event = ollamaPullEventAdapter.fromJson(line)
                val completed = event?.completed ?: 0L
                val total = event?.total ?: 0L
                if (total > 0L) {
                    val percent = ((completed * 100.0) / total).toInt()
                    onProgress("$status ($percent%)")
                }
            }
        }
    }
}
```

**Why streaming for download but not generation?** Downloads are multi-GB and take minutes. Without progress, the UI would appear frozen. The Ollama pull API emits NDJSON events with `completed`/`total` byte counts, enabling a real-time progress bar.

### Model Name Normalization

```kotlin
private fun normalizeModelName(model: String): String {
    return model.trim().lowercase().removePrefix("models/")
}
```

**Why normalize?** Ollama returns model names in various formats:
- `qwen2.5:3b`
- `Qwen2.5:3b`
- `models/qwen2.5:3b`

Normalization ensures comparison works regardless of casing or prefix.

---

## 6. Network & Connectivity

### Three Deployment Modes

#### Mode 1: PC via ADB Reverse (Most Common)

```bash
# On PC (one-time while USB connected):
adb reverse tcp:11434 tcp:11434
```

- App URL: `http://127.0.0.1:11434/`
- Phone talks to localhost:11434 → ADB tunnels to PC's 11434 → Ollama server
- **Pros:** Zero network config, works immediately
- **Cons:** Requires USB connection, breaks if ADB disconnects

#### Mode 2: Same WiFi

```
# On PC, start Ollama bound to all interfaces:
OLLAMA_HOST=0.0.0.0:11434 ollama serve
```

- App URL: `http://192.168.1.x:11434/` (set in Settings)
- **Pros:** No USB needed, works wirelessly
- **Cons:** Requires firewall rule, same network

#### Mode 3: On-Device (Termux)

```bash
# In Termux on the phone:
pkg install ollama
ollama serve
ollama pull qwen2.5:3b
```

- App URL: `http://127.0.0.1:11434/`
- **Pros:** Fully offline, no external dependency
- **Cons:** Phone SoC is slow for LLMs, limited RAM

### Base URL Resolution

```kotlin
private fun resolveOllamaBaseUrl(): String {
    val settingsUrl = loadSettings().ollamaBaseUrl.trim()
    val buildUrl = BuildConfig.OLLAMA_BASE_URL.trim()
    return ensureTrailingSlash(settingsUrl.ifBlank { buildUrl })
}
```

Priority: **Settings URL** > **BuildConfig URL** > **`http://127.0.0.1:11434/`**

This allows runtime switching without rebuilding the APK.

### Android Cleartext HTTP

Ollama uses plain HTTP (not HTTPS). Android 9+ blocks cleartext by default. The app includes a `network_security_config.xml` that allows cleartext for the Ollama port:

```xml
<!-- app/src/main/res/xml/network_security_config.xml -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">127.0.0.1</domain>
        <domain includeSubdomains="true">10.0.0.0/8</domain>
        <domain includeSubdomains="true">192.168.0.0/16</domain>
    </domain-config>
</network-security-config>
```

**Why allow cleartext?** Ollama doesn't support TLS out of the box. Since it's running on localhost or LAN, cleartext is acceptable (the traffic never leaves the local network).

---

## 7. Troubleshooting & Diagnostics

### Built-In Diagnostic Tool

The "Run Ollama Diagnostics" button in the app executes:

```kotlin
suspend fun runOllamaConnectionDiagnostics(): String {
    // 1. Log the configured base URL
    // 2. Detect localhost vs remote
    // 3. Attempt to connect to /api/tags
    // 4. On success: list available models
    // 5. On failure: generate targeted troubleshooting hints
}
```

**Sample diagnostic output (failure):**
```
[2026-03-26 10:15:33.421] Ollama diagnostics started
Configured Ollama base URL: http://127.0.0.1:11434/
Detected localhost/loopback base URL.
If Ollama runs on PC, keep adb reverse active: adb reverse tcp:11434 tcp:11434
Connectivity: FAILED
Error: Failed to connect to /127.0.0.1:11434
Troubleshooting:
- If phone should use PC Ollama, run: adb reverse tcp:11434 tcp:11434
- Confirm Ollama server is running: curl http://<server-ip>:11434/api/tags
- Confirm selected model exists on server, or pull model first.
```

**Sample diagnostic output (success):**
```
[2026-03-26 10:16:02.891] Ollama diagnostics started
Configured Ollama base URL: http://127.0.0.1:11434/
Detected localhost/loopback base URL.
Connectivity: SUCCESS
Model count: 2
Available models: llama3.1:8b, qwen2.5:3b
Diagnostics completed successfully.
```

### Common Issues and Why They Happen

| Symptom | Root Cause | Hint Generated |
|---|---|---|
| "Failed to connect to /127.0.0.1:11434" | ADB reverse not active | "run: `adb reverse tcp:11434 tcp:11434`" |
| "cleartext communication not permitted" | Android network security policy | "ensure network_security_config allows cleartext" |
| "Model 'codellama:13b' is still unavailable after pull" | Not enough storage on Ollama host | "Verify storage space and Ollama runtime health" |
| "Ollama returned an empty response" | Model too small for Hard problems | Consider `llama3.1:8b` or larger |
| "Failed after N retries" | Ollama server crashed or restarted | "Verify Ollama service is running" |

---

## 8. Shared Components (Same as Gemini)

These files are functionally identical between both variants (only the package name differs):

| File | Lines | What it does |
|---|---|---|
| `ConsistencyStorage.kt` | ~110 | SharedPreferences: save/load challenge, AI result, `markCompletedToday()`, `unmarkCompletedToday()`, IST date/time helpers |
| `ConsistencyReminderScheduler.kt` | ~40 | `setInexactRepeating` alarm every N hours |
| `ConsistencyReminderReceiver.kt` | ~75 | BroadcastReceiver: IST window check → completion check → show notification |
| `RevisionExportManager.kt` | ~226 | Build `question.txt` + `answer.py` + `explanation.txt`, write locally, push to GitHub |
| `data/LeetCodeApi.kt` | ~65 | Retrofit interface for LeetCode GraphQL API |
| `data/GitHubApi.kt` | ~40 | Retrofit interface for GitHub Contents API (upsert files) |
| Calendar integration | ~100 | Insert/delete timed calendar events with duplicate detection |
| DSA concept extraction | ~50 | Keyword-based concept tagging from explanation text |

---

## 9. Build System Differences

### `local.properties` — Ollama Variant

```properties
# Ollama-specific (NO Gemini API key needed):
OLLAMA_BASE_URL=http://127.0.0.1:11434/
OLLAMA_MODEL=qwen2.5:3b

# Shared:
GITHUB_TOKEN=ghp_...
GITHUB_OWNER=YourGitHubUsername
GITHUB_REPO=YourRepoName
GITHUB_BRANCH=main
SETTINGS_UPDATE_PASSWORD=1234
```

### `app/build.gradle.kts` Differences

```kotlin
// Gemini variant:
buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")

// Ollama variant:
buildConfigField("String", "OLLAMA_BASE_URL", "\"$ollamaBaseUrl\"")
buildConfigField("String", "OLLAMA_MODEL", "\"$ollamaModel\"")
```

### Building the APK

```powershell
# From project root:
cd mobile_apps\leedcode_checker_ollama
.\gradlew assembleRelease

# Or use the build script:
.\build_apk.ps1
```

---

## 10. When to Use Which Variant

| Scenario | Use Gemini Variant | Use Ollama Variant |
|---|---|---|
| **Need best code quality** | ✅ Gemini 2.5 Pro is top-tier | ❌ Small models struggle with Hard |
| **Privacy-sensitive** | ❌ Code sent to Google | ✅ Code stays on your network |
| **No internet access** | ❌ Requires internet | ✅ Works fully offline |
| **Cost-sensitive (heavy use)** | ❌ API costs accumulate | ✅ Zero marginal cost |
| **Quick one-off** | ✅ No setup needed | ❌ Ollama server required |
| **Learning about LLM serving** | Limited learning | ✅ Full exposure to model serving |
| **Phone with 4GB RAM** | ✅ All work in cloud | ❌ Can't run meaningful models locally |
| **PC with GPU available** | Either works | ✅ Leverage GPU for fast inference |

### Model Recommendations for Ollama

| Problem Difficulty | Minimum Model | Recommended |
|---|---|---|
| Easy | `qwen2.5:1.5b` (1 GB) | `qwen2.5:3b` (1.9 GB) |
| Medium | `qwen2.5:3b` (1.9 GB) | `codellama:7b` (3.8 GB) |
| Hard | `llama3.1:8b` (4.7 GB) | `codellama:13b` (7.4 GB) or Gemini variant |

---

## Appendix: File Inventory

### Ollama-Only Files (Not in Gemini Variant)

| File | Lines | Purpose |
|---|---|---|
| `data/OllamaApi.kt` | 85 | Ollama REST API: generate, pull, list tags |
| Network security config | ~10 | Allow cleartext HTTP for Ollama |

### Modified Files (Different from Gemini)

| File | Lines | Key Difference |
|---|---|---|
| `data/LeetCodeRepository.kt` | 689 | Ollama pipeline, auto-pull, diagnostics |
| `LeetCodeViewModel.kt` | 527 | Model management, download progress, diagnostics |
| `AppSettingsStore.kt` | ~95 | Extra `ollamaBaseUrl` field |
| `app/build.gradle.kts` | ~160 | `OLLAMA_BASE_URL`, `OLLAMA_MODEL` |

### Identical Files (Same in Both)

| File | Lines | Purpose |
|---|---|---|
| `ConsistencyStorage.kt` | ~110 | SharedPrefs cache + completion tracking |
| `ConsistencyReminderScheduler.kt` | ~40 | AlarmManager scheduling |
| `ConsistencyReminderReceiver.kt` | ~75 | Notification logic |
| `RevisionExportManager.kt` | ~226 | File assembly, local write, GitHub push |
| `data/LeetCodeApi.kt` | ~65 | LeetCode GraphQL |
| `data/GitHubApi.kt` | ~40 | GitHub Contents API |

---

*Generated as a learning companion for the LeetCode Checker (Ollama) Android application.*
*All code references, architecture decisions, and flow descriptions are from the actual codebase as of March 2026.*

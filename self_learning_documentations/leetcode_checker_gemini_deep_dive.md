# LeetCode Checker (Gemini) — Deep-Dive Architecture & Execution Walkthrough

> **Purpose:** This document walks through every component of the LeetCode Consistency Checker Android app — from app launch to pushing a solved problem to GitHub — with the **why** behind each design decision, actual code, and the complete execution flow.
>
> **App Package:** `com.vignesh.leetcodechecker`
> **LLM Backend:** Google Gemini 2.5 Pro (cloud API)
> **Min SDK:** 24 (Android 7.0) | **Target SDK:** 35

---

## Table of Contents

1. [What This App Does](#1-what-this-app-does)
2. [Architecture Overview](#2-architecture-overview)
3. [File-By-File Walkthrough](#3-file-by-file-walkthrough)
4. [Complete Execution Flow](#4-complete-execution-flow)
5. [Key Design Decisions (And Why)](#5-key-design-decisions-and-why)
6. [Build System & Secrets Management](#6-build-system--secrets-management)
7. [The Reminder System](#7-the-reminder-system)
8. [Calendar Integration](#8-calendar-integration)
9. [GitHub Push Pipeline](#9-github-push-pipeline)
10. [Settings & Configuration](#10-settings--configuration)

---

## 1. What This App Does

A single-purpose Android app for **daily LeetCode consistency tracking**:

1. **Fetch** today's LeetCode Daily Challenge (via LeetCode GraphQL API)
2. **Solve** the problem using Gemini 2.5 Pro (cloud LLM) — generates Python 3 code, testcase validation, and full explanation
3. **Edit** the generated code manually if needed (inline code editor)
4. **Copy/Paste** the code into LeetCode's editor and submit manually
5. **Mark Completed** → creates a calendar event at the current time, stops reminders
6. **Undo Completion** → removes calendar event, re-enables reminders
7. **Push to GitHub** → uploads `question.txt`, `answer.py`, `explanation.txt` to a configured repo
8. **Hourly Reminders** → notifications between configurable IST hours until you mark completed
9. **Submission History** → browse past local submissions by date
10. **Flow Diagram** → pinch-to-zoom Mermaid architecture diagram

### The Daily Workflow

```
Open App → Tap "LeetCode Consistency Checker"
         → Tap "Refresh LeetCode API"           (fetches today's problem)
         → Tap "Refresh LLM Answer"              (Gemini solves it)
         → Review code, optionally Edit Code
         → Copy Python3 Code → Paste in LeetCode → Submit
         → Tap "Mark as Completed"               (calendar + stop reminders)
         → Tap "Push QA Revision To GitHub"      (archives to repo)
```

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│                    MainActivity.kt                   │
│         Jetpack Compose Single-Activity UI           │
│  Screens: Landing | Checker | FlowDiagram |          │
│           SubmissionHistory | Settings               │
└──────────────┬───────────────────────────────────────┘
               │ observes StateFlow<LeetCodeUiState>
               ▼
┌─────────────────────────────────────────────────────┐
│                 LeetCodeViewModel.kt                 │
│  Orchestrates all actions, holds UI state            │
│  refreshApiChallenge() | refreshLlmAnswer()          │
│  updateAiCode() | pushRevisionFilesToGitHub()        │
│  markCompletedToday() | unmarkCompletedToday()       │
└──────────┬────────────┬──────────────────────────────┘
           │            │
    ┌──────▼──────┐  ┌──▼──────────────────────┐
    │ LeetCode    │  │ ConsistencyStorage.kt   │
    │ Repository  │  │ SharedPreferences cache  │
    │  .kt        │  │ challenge + AI + status  │
    └──────┬──────┘  └─────────────────────────┘
           │
    ┌──────▼──────────────────────────────────────┐
    │              External APIs                    │
    │  LeetCodeApi.kt → leetcode.com/graphql       │
    │  GeminiApi.kt   → generativelanguage API     │
    │  GitHubApi.kt   → api.github.com             │
    └─────────────────────────────────────────────┘
```

**Pattern:** Single-Activity MVVM with Compose — the most modern Android architecture.

---

## 3. File-By-File Walkthrough

### 3.1 `MainActivity.kt` (1212 lines) — The Entire UI

**What:** A single `@Composable` function (`LeetCodeCheckerScreen`) that renders all 5 screens inline via a `currentScreen` state variable. No Navigation component, no fragments.

**Why a single Composable instead of Jetpack Navigation?**
- The app has only 5 simple screens with no deep linking or complex back-stack needs
- Single-file approach means the entire UI is readable in one place
- No additional `navigation-compose` dependency (saving ~50KB of APK size)

**Key sections:**

#### Theme (lines 76-96)
```kotlin
private val AppDarkColors = darkColorScheme(
    primary = Color(0xFF9FC8FF),
    background = Color(0xFF0F1115),
    surface = Color(0xFF171A20),
    ...
)
```
Custom dark/light themes using Material3. The system theme is respected via `isSystemInDarkTheme()`.

#### Screen Navigation (lines 119-122)
```kotlin
private enum class AppScreen {
    Landing, ConsistencyChecker, FlowDiagram, SubmissionHistory, Settings
}
var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Landing) }
```
`rememberSaveable` survives configuration changes (rotation, process death). No framework — just an enum + when-branch.

#### LLM Confirmation Dialog (lines 626-650)
```kotlin
AlertDialog(
    title = { Text("Confirm LLM Call") },
    text = { Text("This will trigger a paid LLM request. Continue?") },
    ...
)
```
**Why a confirmation dialog?** Gemini API calls cost money. Accidentally tapping "Refresh LLM" shouldn't burn API credits. The dialog forces conscious confirmation.

#### Inline Code Editor (lines 748-810)
```kotlin
var isEditingCode by rememberSaveable { mutableStateOf(false) }
var editableCode by rememberSaveable(aiCode) { mutableStateOf(aiCode) }

if (isEditingCode) {
    OutlinedTextField(
        value = editableCode,
        onValueChange = { editableCode = it },
        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        ...
    )
}
```
**Why `rememberSaveable(aiCode)`?** The `aiCode` key parameter means `editableCode` resets whenever the LLM generates new code, but persists through rotations/recompositions otherwise.

#### Mark Completed Toggle (lines 910-960)
```kotlin
Button(
    onClick = {
        if (state.isCompletedToday) {
            viewModel.unmarkCompletedToday()
            deleteCompletionCalendarEvent(context)
        } else {
            viewModel.markCompletedToday()
            insertCompletionCalendarEvent(context, challenge)
        }
    },
)
```
A single button that toggles between "Mark as Completed" and "Undo — Mark Not Completed". The undo path clears SharedPreferences AND deletes the matching calendar event.

#### DSA Concept Extraction (lines 1150-1205)
```kotlin
private fun extractDsaConcepts(explanation: String, tags: List<String>): List<String> {
    val keywordMap = listOf(
        "prefix sum" to "Prefix Sum",
        "sliding window" to "Sliding Window",
        "binary search" to "Binary Search",
        ...
    )
}
```
**Why regex keyword extraction instead of LLM-based NER?** Cost. The explanation text is already LLM-generated — running another LLM call to extract concepts would be wasteful. Keyword matching against 28 known DSA patterns is free — and LeetCode's tag set is finite.

### 3.2 `LeetCodeViewModel.kt` (391 lines) — Business Logic Orchestrator

**What:** An `AndroidViewModel` that holds all UI state in a single `MutableStateFlow<LeetCodeUiState>` and exposes public functions for every user action.

**Why `AndroidViewModel` (not plain `ViewModel`)?**
```kotlin
class LeetCodeViewModel(
    application: Application,
    ...
) : AndroidViewModel(application)
```
`AndroidViewModel` gives access to `Application` context — needed for:
- SharedPreferences (ConsistencyStorage, AppSettingsStore)
- File I/O (RevisionExportManager.writeLocalRevisionFiles)
- AlarmManager (ConsistencyReminderScheduler)

**The UiState data class:**
```kotlin
data class LeetCodeUiState(
    val isLoading: Boolean = false,
    val challenge: DailyChallengeUiModel? = null,
    val aiCode: String? = null,
    val aiTestcaseValidation: String? = null,
    val aiExplanation: String? = null,
    val isAiLoading: Boolean = false,
    val error: String? = null,
    val aiError: String? = null,
    val aiDebugLog: String? = null,
    val infoMessage: String? = null,
    val isCompletedToday: Boolean = false,
    val isPushLoading: Boolean = false,
    val localRevisionPath: String? = null,
    val isHistoryLoading: Boolean = false,
    val revisionHistory: List<LocalRevisionHistoryItem> = emptyList(),
    val selectedHistoryItem: LocalRevisionHistoryItem? = null,
    val settings: AppSettings = AppSettings()
)
```

**Why a single state class?** This is the **unidirectional data flow** pattern. The UI always renders from one source of truth. No fragmented LiveData scattered across the ViewModel. One `copy()` call atomically updates whatever changed.

**Key methods:**

| Method | What it does |
|---|---|
| `refreshApiChallenge()` | Calls LeetCode GraphQL API, saves to SharedPrefs |
| `refreshLlmAnswer()` | Sends problem to Gemini, parses tagged response |
| `updateAiCode(newCode)` | Saves manually edited code to state + local files |
| `pushRevisionFilesToGitHub()` | Builds 3 files, pushes via GitHub Contents API |
| `markCompletedToday()` | Saves boolean flag to SharedPrefs by IST date |
| `unmarkCompletedToday()` | Clears the boolean flag |
| `saveSettings(settings)` | Validates ranges, saves to SharedPrefs, reschedules reminders |

### 3.3 `data/LeetCodeRepository.kt` (587 lines) — The LLM Pipeline

**What:** Handles all network calls — fetching the daily challenge from LeetCode and generating solutions via Gemini.

#### LeetCode GraphQL API
```kotlin
val dailyQuery = """
    query questionOfToday {
      activeDailyCodingChallengeQuestion {
        date
        link
        question {
          title, titleSlug, difficulty, questionFrontendId
          topicTags { name, slug }
        }
      }
    }
""".trimIndent()
```

**Why GraphQL?** LeetCode has no REST API. Their internal API uses GraphQL — we reverse-engineered the exact queries from their web frontend. Two queries are needed:
1. `questionOfToday` — gets the daily challenge metadata
2. `questionContent` — gets the full problem statement, testcases, and starter code

#### Gemini Integration
```kotlin
val systemPrompt = """
You are LC-Autonomous-Solver ($promptName).
Return only these tags in order:
<leetcode_python3_code>...</leetcode_python3_code>
<testcase_validation>...</testcase_validation>
<explanation>...</explanation>
"""
```

**Why custom XML-style tags instead of `responseSchema`?** Unlike the usecase3 project which uses `responseMimeType: "application/json"` + `responseSchema`, this app uses plain text with custom tags. The reason: the code output can contain special characters (brackets, quotes, colons) that would need escaping in JSON. XML-style tags with free-form text content are more robust for code output.

#### Model Selection with Dynamic Discovery
```kotlin
val availableModels = geminiApi.listModels(apiKey).models.orEmpty()
    .map { it.name.removePrefix("models/") }
val selectedModel = preferredModels.firstOrNull { candidate ->
    availableModels.any { it.equals(candidate, ignoreCase = true) }
} ?: preferredModels.first()
```

**Why dynamic model discovery?** Models get deprecated. `gemini-2.5-pro` might become inaccessible tomorrow. The app queries `ListModels` at runtime, then picks the first preferred model that actually exists. If none match, it falls back to the first preference (which will fail with a clear error).

#### Retry with Backoff
```kotlin
repeat(maxModelRetries) { index ->
    val attempt = index + 1
    try {
        // ... API call ...
    } catch (error: HttpException) {
        when (error.code()) {
            429 -> {
                val retryAfterSeconds = error.response()?.headers()?.get("Retry-After")
                    ?.toLongOrNull() ?: (5L * attempt).coerceAtMost(45L)
                delay(retryAfterSeconds * 1000L)
            }
            503 -> delay((3L * attempt * 1000L).coerceAtMost(30_000L))
            // ...
        }
    }
}
```

**Why `Retry-After` header parsing?** When Gemini returns 429 (rate limit), it sometimes includes a `Retry-After` header with the exact seconds to wait. Respecting this header is more efficient than arbitrary exponential backoff.

#### Token Budget Management
```kotlin
val boundedSystemPrompt = truncateToApproxTokenLimit(systemPrompt, maxInputTokens)
val remainingInputBudget = (maxInputTokens - estimateApproxTokens(boundedSystemPrompt))
    .coerceAtLeast(1024)
val boundedUserPrompt = truncateToApproxTokenLimit(userPrompt, remainingInputBudget)
```

**Why approximate limits using chars/4?** The Gemini tokenizer isn't available offline. `chars ÷ 4` is a well-known heuristic for English text (GPT models average ~4 chars/token; Gemini is similar). It's conservative enough to prevent 400 errors from oversized prompts.

#### Thinking Budget
```kotlin
generationConfig = GeminiGenerationConfig(
    maxOutputTokens = maxOutputTokens,
    thinkingConfig = GeminiThinkingConfig(
        thinkingBudget = maxThinkingBudget  // maxOutputTokens / divisor
    )
)
```

**Why a configurable thinking divisor?** Gemini 2.5's thinking tokens count against `maxOutputTokens`. The divisor (default 4) allocates 75% of the budget to visible output and 25% to thinking. Users can tune this in Settings — lower divisor = more thinking, higher divisor = more output.

### 3.4 `data/GeminiApi.kt` (80 lines) — Retrofit Interface

**What:** Data classes for Gemini REST API request/response + Retrofit interface.

```kotlin
interface GeminiApi {
    @GET("v1beta/models")
    suspend fun listModels(@Query("key") apiKey: String): GeminiModelListResponse

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(...): GeminiGenerateResponse

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContentRaw(...): ResponseBody
}
```

**Why two `generateContent` endpoints — typed and raw?**
- `generateContent` → Moshi deserializes automatically. Used for simple cases.
- `generateContentRaw` → Returns raw `ResponseBody`. Used in the pipeline because Moshi fails on edge cases (partial JSON, Gemini's nested response structure). Raw parsing gives full control.

### 3.5 `data/LeetCodeApi.kt` (65 lines) — LeetCode GraphQL Interface

```kotlin
interface LeetCodeApi {
    @POST("graphql")
    suspend fun postQuery(@Body request: GraphQLRequest): DailyChallengeResponse

    @POST("graphql")
    suspend fun postQuestionDetails(@Body request: GraphQLRequest): QuestionDetailsResponse
}
```

**Why two separate methods instead of one generic?** Type safety. Each method returns a strongly-typed response class. With one generic method, you'd need `Map<String, Any>` and manual JSON navigation — error-prone and unreadable.

### 3.6 `data/GitHubApi.kt` (40 lines) — GitHub Contents API

```kotlin
interface GitHubApi {
    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFile(...): GitHubContentFileResponse  // returns SHA

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun upsertFile(...): Any  // creates or updates file
}
```

**Why GET before PUT?** GitHub's Contents API requires the existing file's SHA to update it. If the file doesn't exist (404), you omit the SHA to create it. This is a classic **upsert** pattern — get-then-put.

### 3.7 `ConsistencyStorage.kt` (110 lines) — Persistent State

**What:** SharedPreferences wrapper that caches the challenge, AI output, and per-day completion status.

```kotlin
fun markCompletedToday(context: Context) {
    prefs(context).edit().putBoolean(KEY_COMPLETED_PREFIX + istDateKey(), true).apply()
}

fun istDateKey(now: Date = Date()): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    return formatter.format(now)
}
```

**Why IST (Asia/Kolkata)?** LeetCode's daily challenge resets at UTC midnight. In India (IST = UTC+5:30), that's 5:30 AM. Using IST for the date key means "today's challenge" aligns with how the user perceives days — not UTC's midnight boundary.

**Why SharedPreferences instead of Room/SQLite?**
- We store exactly 3 things: a challenge JSON, an AI result JSON, and boolean flags
- SharedPreferences is 0-dependency, instant, and perfectly suited for key-value pairs
- Room would add ~500KB to the APK for something that doesn't need SQL queries

### 3.8 `AppSettingsStore.kt` (95 lines) — Settings Persistence

**What:** Saves/loads the `AppSettings` data class to SharedPreferences as JSON.

```kotlin
data class AppSettings(
    val preferredModelsCsv: String = "gemini-2.5-pro,gemini-pro-latest",
    val maxModelRetries: Int = 3,
    val maxInputTokens: Int = 1_048_576,
    val maxOutputTokens: Int = 65_535,
    val thinkingBudgetDivisor: Int = 4,
    val networkTimeoutMinutes: Int = 15,
    val reminderStartHourIst: Int = 9,
    val reminderEndHourIst: Int = 22,
    val reminderIntervalHours: Int = 1,
    val githubOwnerOverride: String = "",
    val githubRepoOverride: String = "",
    val githubBranchOverride: String = "",
    ...
)
```

**Why all defaults in the data class itself?** If SharedPreferences is empty (fresh install) or corrupted, `AppSettings()` gives sane defaults. The app never crashes on missing settings.

### 3.9 `ConsistencyReminderScheduler.kt` (40 lines) — Alarm Scheduling

```kotlin
alarmManager.setInexactRepeating(
    AlarmManager.RTC_WAKEUP,
    firstTrigger,       // next top-of-hour
    AlarmManager.INTERVAL_HOUR * intervalHours,
    pendingIntent
)
```

**Why `setInexactRepeating`?** Android batches inexact alarms for battery efficiency. Since reminders are non-urgent (a few minutes drift is fine), inexact is the correct choice. `setExactAndAllowWhileIdle` would drain battery for no user benefit.

**Why `RTC_WAKEUP`?** We want the alarm to fire even when the device is sleeping (screen off). `RTC` without `WAKEUP` would only fire when the device next wakes.

### 3.10 `ConsistencyReminderReceiver.kt` (75 lines) — Notification Logic

```kotlin
override fun onReceive(context: Context, intent: Intent?) {
    val hourIst = ConsistencyStorage.istHour(now)
    val isWithinWindow = hourIst in startHour..endHour
    if (!isWithinWindow) return          // outside reminder hours
    if (ConsistencyStorage.isCompletedToday(context)) return  // already done
    // ... show notification ...
}
```

**Key design choices:**
1. **IST hour check** — alarms fire every N hours, but we suppress notifications outside the configured window (default 9 AM - 10 PM IST)
2. **Completion check** — if you've already marked completed, no notification fires
3. **Permission check** — Android 13+ requires `POST_NOTIFICATIONS` permission; we gracefully skip if not granted

### 3.11 `RevisionExportManager.kt` (226 lines) — File Assembly & GitHub Push

**What:** Builds 3 revision files, writes them locally, and can push them to GitHub.

```kotlin
fun buildRevisionFiles(...): RevisionFiles {
    val questionText = buildString {
        appendLine("Date: ${challenge.date}")
        appendLine("Question ID: ${challenge.questionId}")
        appendLine("Title: ${challenge.title}")
        appendLine("Difficulty: ${challenge.difficulty}")
        ...
    }
    return RevisionFiles(
        folderDate = challenge.date,
        questionText = questionText,
        answerPython = aiCode,
        explanationText = explanationText
    )
}
```

**GitHub push flow:**
```
For each file in [question.txt, answer.py, explanation.txt]:
  1. GET /repos/{owner}/{repo}/contents/{path}  → get existing SHA (or 404)
  2. PUT /repos/{owner}/{repo}/contents/{path}   → upsert with Base64 content
```

**Why Base64 encoding?** GitHub's Contents API requires file content as Base64-encoded strings. This is a GitHub API requirement, not a design choice.

---

## 4. Complete Execution Flow

### Step 1: App Launch
```
MainActivity.onCreate()
  → ConsistencyReminderScheduler.ensureHourlyReminder()  // schedule alarms
  → setContent { LeetCodeCheckerScreen() }
      → LeetCodeViewModel created
          → loadFromLocalStorage()         // restore challenge + AI from SharedPrefs
          → refreshLocalRevisionHistory()  // scan local files for past submissions
          → collect repository.liveDebugLog  // wire up live pipeline logging
```

### Step 2: "Refresh LeetCode API"
```
viewModel.refreshApiChallenge()
  → repository.fetchDailyChallenge()
      → POST leetcode.com/graphql (questionOfToday)
      → POST leetcode.com/graphql (questionContent)
      → Strip HTML tags from content
      → Extract Python3 starter code from codeSnippets
      → Return DailyChallengeUiModel
  → ConsistencyStorage.saveChallenge()  // persist to SharedPrefs
  → Update UiState with challenge data
```

### Step 3: "Refresh LLM Answer" (with confirmation dialog)
```
showLlmConfirmation = true → user taps "Confirm"
viewModel.refreshLlmAnswer()
  → repository.generateDetailedAnswer(challenge, forceRefresh=true)
      → Build system prompt + user prompt
      → Truncate to token budget
      → geminiApi.listModels() → select best available model
      → generateWithRetry():
          → POST generativelanguage.googleapis.com/.../generateContent
          → Parse response: candidates[0].content.parts[].text
          → Extract <leetcode_python3_code>, <testcase_validation>, <explanation>
      → Return AiGenerationResult
  → ConsistencyStorage.saveAi()
  → saveRevisionFilesLocally()  // write question.txt, answer.py, explanation.txt
  → Update UiState with aiCode, aiExplanation, etc.
```

### Step 4: Edit Code (optional)
```
User taps "Edit Code"
  → isEditingCode = true
  → OutlinedTextField renders with current code
User edits code, taps "Save Changes"
  → viewModel.updateAiCode(editableCode)
      → UiState.aiCode = newCode
      → saveRevisionFilesLocally() with edited code
```

### Step 5: "Mark as Completed"
```
User taps "Mark as Completed"
  → Check WRITE_CALENDAR + READ_CALENDAR permissions
  → viewModel.markCompletedToday()
      → SharedPrefs: completed_2026-03-26 = true
  → insertCompletionCalendarEvent()
      → Find primary calendar ID
      → Check for duplicate events today
      → Insert 30-min event at current device time
      → Add reminder at event start
  → Future reminder notifications suppressed (isCompletedToday check)
```

### Step 6: "Push QA Revision To GitHub" (with confirmation dialog)
```
User taps "Push" in confirmation dialog
viewModel.pushRevisionFilesToGitHub()
  → RevisionExportManager.buildRevisionFiles()
  → RevisionExportManager.writeLocalRevisionFiles()  // save locally first
  → RevisionExportManager.pushToGitHub()
      → For each of 3 files:
          → GET existing SHA (or 404 = new file)
          → PUT Base64-encoded content with commit message
```

---

## 5. Key Design Decisions (And Why)

### 5.1 Why Manually-Separated API + LLM Buttons?

Previous versions auto-triggered the LLM after fetching the challenge. This was removed because:
1. **Cost control** — accidental triggers waste API credits
2. **Workflow flexibility** — you can read the problem statement first, think about it, then request the LLM
3. **Offline resilience** — the cached challenge persists across app restarts; you don't need to re-fetch before requesting LLM

### 5.2 Why SharedPreferences for Everything (Not Room)?

This app stores exactly:
- 1 challenge JSON blob (~2 KB)
- 1 AI result JSON blob (~5 KB)
- ~365 booleans per year (daily completion flags)
- 1 settings JSON blob (~500 bytes)

Total: ~8 KB of data. Room/SQLite would add 500 KB to APK size and require schema migrations for something that fits comfortably in SharedPreferences.

### 5.3 Why Retrofit + Moshi (Not Ktor + Kotlinx Serialization)?

Retrofit is the Android industry standard with 15+ years of battle-testing. Moshi handles Kotlin data classes natively via `KotlinJsonAdapterFactory`. Ktor is excellent but adds ~2 MB of dependencies vs Retrofit's ~400 KB.

### 5.4 Why `forceRefresh` Flag Instead of Always Fresh?

```kotlin
if (!forceRefresh) {
    answerCache[cacheKey]?.let { return@runCatching it }
}
```
In-memory caching prevents re-sending the same problem to Gemini during the same session. The `forceRefresh=true` flag is only set when the user explicitly taps "Refresh LLM Answer" after the confirmation dialog.

### 5.5 Why Password-Protected Settings?

```kotlin
val expectedSettingsPassword = BuildConfig.SETTINGS_UPDATE_PASSWORD.ifBlank { "1234" }
```
The settings control model selection, token limits, and GitHub credentials. Password protection prevents accidental changes (e.g., a toddler tapping around) while keeping the barrier low (stored in `local.properties`, default "1234").

---

## 6. Build System & Secrets Management

### `app/build.gradle.kts` — Key Sections

```kotlin
val geminiApiKey = localProperties.getProperty("GEMINI_API_KEY", "")
val githubToken = localProperties.getProperty("GITHUB_TOKEN", "")
val githubOwner = localProperties.getProperty("GITHUB_OWNER", "VigneshwaraChinnadurai")
val githubRepo = localProperties.getProperty("GITHUB_REPO", "Google_Prep")

buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
buildConfigField("String", "GITHUB_TOKEN", "\"$githubToken\"")
```

**Why `BuildConfig` fields?** Secrets in `local.properties` are NOT committed to git. They're injected at build time into the generated `BuildConfig.java` class. The APK contains the actual values (encrypted at rest by Android), but the source code doesn't.

**Required `local.properties` entries:**
```properties
GEMINI_API_KEY=AIzaSy...
GITHUB_TOKEN=ghp_...
GITHUB_OWNER=YourGitHubUsername
GITHUB_REPO=YourRepoName
GITHUB_BRANCH=main
SETTINGS_UPDATE_PASSWORD=your_password
```

### Dependencies
| Library | Purpose | Size Impact |
|---|---|---|
| `retrofit:2.11.0` | HTTP client for all 3 APIs | ~150 KB |
| `converter-moshi:2.11.0` | JSON serialization | ~50 KB |
| `moshi-kotlin:1.15.1` | Kotlin data class support | ~200 KB |
| `compose-bom:2024.09.01` | Jetpack Compose UI toolkit | ~3 MB |
| `lifecycle-viewmodel-compose` | ViewModel + Compose integration | ~50 KB |

---

## 7. The Reminder System

### How It Works End-to-End

```
App Launch → ConsistencyReminderScheduler.ensureHourlyReminder()
           → AlarmManager.setInexactRepeating(INTERVAL_HOUR * intervalHours)

Every N hours (configurable):
  → ConsistencyReminderReceiver.onReceive()
    → Is IST hour in [startHour, endHour]?  No → silent return
    → Is today completed?                    Yes → silent return
    → Has POST_NOTIFICATIONS permission?     No → silent return
    → Show notification: "Complete today's LeetCode and tap Mark as Completed."
    → Tapping notification opens MainActivity
```

**Default schedule:** Every 1 hour, between 9 AM and 10 PM IST. Configurable via Settings.

---

## 8. Calendar Integration

### Insert Event
```kotlin
val start = Calendar.getInstance(timezone)  // device's current time
val end = (start.clone() as Calendar).apply { add(Calendar.MINUTE, 30) }

val values = ContentValues().apply {
    put(CalendarContract.Events.TITLE, "LeetCode Completed: ${challenge.title}")
    put(CalendarContract.Events.DTSTART, start.timeInMillis)
    put(CalendarContract.Events.DTEND, end.timeInMillis)
    put(CalendarContract.Events.ALL_DAY, 0)       // timed event, not all-day
    put(CalendarContract.Events.EVENT_TIMEZONE, timezone.id)
    put(CalendarContract.Events.EVENT_COLOR, 0xFF2E7D32.toInt())  // green
}
```

### Delete Event (Undo)
```kotlin
val selection = "${CalendarContract.Events.DTSTART}>=? AND " +
    "${CalendarContract.Events.DTSTART}<? AND " +
    "${CalendarContract.Events.TITLE} LIKE ?"
val selectionArgs = arrayOf(dayStart, dayEnd, "LeetCode Completed%")
context.contentResolver.delete(CalendarContract.Events.CONTENT_URI, selection, selectionArgs)
```

**Duplicate prevention:** Before inserting, the code queries for existing "LeetCode Completed%" events within today's boundaries. If found, it returns early without creating a duplicate.

---

## 9. GitHub Push Pipeline

### File Layout in Repo
```
Leetcode_QA_Revision/
  2026-03-26/
    question.txt       ← Problem statement, metadata, starter code, testcases
    answer.py          ← LLM-generated (or manually edited) Python solution
    explanation.txt    ← LLM explanation + testcase validation
```

### `question.txt` Content
```
Date: 2026-03-26
Question ID: 3548
Title: Equal Sum Grid Partition II
Difficulty: Hard
Tags: Array, Matrix, Prefix Sum
URL: https://leetcode.com/problems/equal-sum-grid-partition-ii/

Question:
You are given an m x n matrix grid of positive integers...

Starter Code (Python3):
class Solution:
    def canPartitionGrid(self, grid: List[List[int]]) -> bool:

Testcases:
[[1,4],[2,3]]
[[1,2],[3,4]]
```

---

## 10. Settings & Configuration

All configurable at runtime via the Settings screen (password-protected):

| Setting | Default | Range | Purpose |
|---|---|---|---|
| Preferred Models CSV | `gemini-2.5-pro,gemini-pro-latest` | — | Model priority list |
| Max Model Retries | 3 | 1-10 | Retries per model on failure |
| Max Input Tokens | 1,048,576 | 1K-2M | Prompt truncation limit |
| Max Output Tokens | 65,535 | 256-65K | Gemini output budget |
| Thinking Budget Divisor | 4 | 1-64 | `maxOutput / divisor` = thinking tokens |
| Network Timeout Minutes | 15 | 1-60 | OkHttp timeout for all APIs |
| Reminder Start Hour IST | 9 | 0-23 | Earliest reminder hour |
| Reminder End Hour IST | 22 | 0-23 | Latest reminder hour |
| Reminder Interval Hours | 1 | 1-12 | Gap between reminders |
| GitHub Owner | From `local.properties` | — | Repo owner for push |
| GitHub Repo | From `local.properties` | — | Repo name for push |
| GitHub Branch | `main` | — | Target branch for push |

---

*Generated as a learning companion for the LeetCode Checker (Gemini) Android application.*
*All code references, architecture decisions, and flow descriptions are from the actual codebase as of March 2026.*

# LeedCode Checker Ollama: Detailed Technical Documentation

## 1. Purpose

LeedCode Checker Ollama is the feature-parity variant of the LeetCode consistency app that uses Ollama instead of Gemini for generation.

Core goals:
1. Fetch the LeetCode daily challenge and cache it locally.
2. Generate Python solution, testcase validation, and explanation via Ollama.
3. Track daily completion with reminders and calendar insertion.
4. Export revision files locally and optionally push to GitHub.

## 2. Architecture

1. UI layer: Compose multi-screen flow.
2. ViewModel layer: orchestration, persistence, reminders, push flow.
3. Data layer: LeetCode GraphQL + Ollama APIs + GitHub contents API.

## 3. Main Features

### 3.1 App flow

1. Landing screen actions:
- Open Consistency Checker.
- Open Calendar app.
- Open Mermaid Flow Diagram viewer.
- Open Settings.
2. Checker screen handles API refresh, Ollama refresh, copy/export/push, completion.
3. Settings screen manages model/runtime/reminder/GitHub overrides.

### 3.2 Settings update protection

1. Save Settings requires password confirmation.
2. Password comes from BuildConfig key `SETTINGS_UPDATE_PASSWORD`.
3. If key is missing/blank, fallback is `1234`.

### 3.3 Data refresh model

1. Refresh LeetCode API is manual.
2. Refresh Ollama Answer is manual and confirmation-gated.
3. This split prevents accidental repeated generation calls.

### 3.4 Ollama generation behavior

1. Uses preferred model CSV from settings.
2. Resolves model using `/api/tags`.
3. Pulls model via `/api/pull` if not available.
4. Calls `/api/generate` with retry and settings-driven runtime controls.
5. Parses tagged contract:
- `<leetcode_python3_code>`
- `<testcase_validation>`
- `<explanation>`
6. Logs timestamped pipeline events for troubleshooting.

### 3.5 Local persistence

1. Challenge and AI output are cached in SharedPreferences.
2. Completion is persisted by IST date.
3. App restores cached data on relaunch.

### 3.6 Reminders and completion

1. Repeating alarm drives reminder receiver.
2. Reminder only fires within configured IST window.
3. Reminder is suppressed once today is marked completed.
4. Mark Completed attempts to insert an all-day calendar event + reminder row.

### 3.7 QA revision export and GitHub push

1. Generates `question.txt`, `answer.py`, `explanation.txt`.
2. Saves files under revision root/date in app external storage.
3. Pushes same files to GitHub via contents upsert API.
4. Supports owner/repo/branch overrides from settings.

### 3.8 Runtime flow diagram viewer

1. Landing screen has `Mermaid Flow Diagram` option.
2. Viewer shows packaged image `runtime_flow_diagram.png`.
3. Supports pinch zoom, drag pan, and Reset Zoom.

### 3.9 Theme behavior

1. Follows system dark/light mode (`isSystemInDarkTheme()`).
2. Uses explicit Material3 dark/light color schemes.

## 4. Configuration

Configure `local.properties` in `mobile_apps/leedcode_checker_ollama`:

1. `OLLAMA_BASE_URL`
2. `OLLAMA_MODEL`
3. `GITHUB_TOKEN`
4. `GITHUB_OWNER`
5. `GITHUB_REPO`
6. `GITHUB_BRANCH`
7. `SETTINGS_UPDATE_PASSWORD`

Example:

```properties
OLLAMA_BASE_URL=http://127.0.0.1:11434/
OLLAMA_MODEL=qwen2.5:3b
GITHUB_TOKEN=
GITHUB_OWNER=VigneshwaraChinnadurai
GITHUB_REPO=Google_Prep
GITHUB_BRANCH=main
SETTINGS_UPDATE_PASSWORD=replace_with_strong_value
```

Notes:

1. Use `http://10.0.2.2:11434/` on Android emulator when Ollama runs on host machine.
2. Use `http://127.0.0.1:11434/` when Ollama runs on the same device.

## 5. Build

From `mobile_apps/leedcode_checker_ollama`:

1. `./gradlew :app:assembleDebug`

APK output:

1. `app/build/outputs/apk/debug/app-debug.apk`

## 6. Mermaid Runtime Flow Diagram

Rendered image:

![LeedCode Checker Ollama Runtime Flow](docs/runtime_flow.png)

Source file:

`docs/runtime_flow.mmd`

```mermaid
flowchart TD
    A[App Launch] --> B[Schedule reminder alarm]
    B --> C[Load cached challenge and AI output]
    C --> D[Landing Screen]
    D --> E{User action}

    E --> F[Open Calendar App]
    F --> D

    E --> G[Open Mermaid Flow Diagram]
    G --> H[View image with zoom/pan/reset]
    H --> D

    E --> I[Open Settings]
    I --> J[Edit model, retries, tokens, timeout, reminder window, GitHub overrides]
    J --> K[Save Settings]
    K --> L{Password valid?}
    L -- No --> I
    L -- Yes --> D

    E --> M[Open Consistency Checker]
    M --> N[Refresh LeetCode API]
    N --> O[Fetch daily metadata from GraphQL]
    O --> P[Fetch question details by titleSlug]
    P --> Q[Store challenge locally]
    Q --> R[Render challenge card]

    R --> S[Refresh Ollama Answer with confirmation]
    S --> T[Resolve preferred model via /api/tags]
    T --> U{Model available?}
    U -- No --> V[/api/pull model and recheck]
    V --> U
    U -- Yes --> W[Call /api/generate]
    W --> X{Generation success?}
    X -- No --> Y[Retry up to maxModelRetries]
    Y --> X
    X -- Yes --> Z[Parse code, testcase_validation, explanation]

    Z --> AA[Store AI output locally]
    AA --> AB[Render code/testcases/concepts/explanation/logs]
    AB --> AC[Copy Python code]

    AB --> AD{Push QA revision?}
    AD -- Yes --> AE[Write question.txt, answer.py, explanation.txt]
    AE --> AF[Push files to GitHub]
    AD -- No --> AG[Continue]
    AF --> AG

    AG --> AH{Mark completed?}
    AH -- Yes --> AI[Save completed-today flag]
    AI --> AJ[Insert calendar all-day event + reminder]
    AJ --> AK[Reminder receiver suppresses notifications for today]
    AH -- No --> AK
```

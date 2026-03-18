# LeetCode Checker (Android)

This app is a LeetCode daily consistency tracker with a Gemini-based generation pipeline.

## Project location

`mobile_apps/leetcode_checker`

## What this app does

- Landing + Checker + Flow Diagram + Settings screens.
- Manual API refresh and manual-confirmed LLM refresh.
- LeetCode GraphQL fetch for daily challenge and details.
- Gemini generation with retry + timestamped pipeline logs.
- Local cache for challenge + AI output.
- Completion tracking with reminders and calendar event insertion.
- Revision export (`question.txt`, `answer.py`, `explanation.txt`) and optional GitHub push.
- Password-protected settings update.
- Mermaid runtime flow image viewer with pinch zoom, pan, and reset.

## Configure local.properties

Create `local.properties` in this project root (same folder as `settings.gradle.kts`) and add:

```properties
GEMINI_API_KEY=
GITHUB_TOKEN=
GITHUB_OWNER=VigneshwaraChinnadurai
GITHUB_REPO=Google_Prep
GITHUB_BRANCH=main
SETTINGS_UPDATE_PASSWORD=replace_with_strong_value
```

Notes:

- If `SETTINGS_UPDATE_PASSWORD` is missing, the app falls back to `1234`.
- Use a strong password in development and rotate it if shared.

## Build and run

From `mobile_apps/leetcode_checker`:

```powershell
.\gradlew :app:assembleDebug
```

APK path:

- `app/build/outputs/apk/debug/app-debug.apk`

For release signing:

1. Copy `keystore.properties.example` to `keystore.properties`.
2. Fill real keystore values.
3. Build with your preferred release command/script.

## Main files

- `app/src/main/java/com/vignesh/leetcodechecker/MainActivity.kt`
- `app/src/main/java/com/vignesh/leetcodechecker/LeetCodeViewModel.kt`
- `app/src/main/java/com/vignesh/leetcodechecker/data/LeetCodeRepository.kt`
- `app/src/main/java/com/vignesh/leetcodechecker/data/LeetCodeApi.kt`
- `app/src/main/java/com/vignesh/leetcodechecker/data/GeminiApi.kt`

## Detailed documentation

- See `DETAILED_DOCUMENTATION.md` for full feature and architecture details.
- Mermaid source and rendered image are in `docs/runtime_flow.mmd` and `docs/runtime_flow.png`.

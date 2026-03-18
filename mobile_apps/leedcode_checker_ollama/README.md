# LeedCode Checker Ollama (Android)

This app is the feature-parity version of LeetCode Consistency Checker using Ollama as the LLM backend.

## Project location

`mobile_apps/leedcode_checker_ollama`

## What this app does

- Landing + Checker + Flow Diagram + Settings screens.
- Manual API refresh and manual-confirmed LLM refresh.
- LeetCode GraphQL fetch for daily challenge and details.
- Ollama generation (`/api/generate`) with retry + timestamped pipeline logs.
- Model resolution from settings (`preferredModelsCsv`) with `/api/tags` and `/api/pull` fallback.
- Local cache for challenge + AI output.
- Completion tracking with reminders and optional calendar event.
- Revision export (`question.txt`, `answer.py`, `explanation.txt`) and optional GitHub push.
- Password-protected settings update.
- Mermaid runtime flow image viewer with pinch zoom, pan, and reset.

## Configure Ollama endpoint

Create `local.properties` in this project root (same folder as `settings.gradle.kts`) and add:

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

- `127.0.0.1` works when Ollama server runs on the same phone/device.
- On Android emulator targeting host machine Ollama, use `http://10.0.2.2:11434/`.
- This app is an Ollama client. The Ollama daemon/runtime must be running on the phone for true on-device model storage.
- If `SETTINGS_UPDATE_PASSWORD` is missing, the app falls back to `1234`.

## Build and run

From `mobile_apps/leedcode_checker_ollama`:

```powershell
.\gradlew :app:assembleDebug
```

APK path:

- `app/build/outputs/apk/debug/app-debug.apk`

## Main files

- `app/src/main/java/com/vignesh/leedcodecheckerollama/MainActivity.kt`
- `app/src/main/java/com/vignesh/leedcodecheckerollama/LeetCodeViewModel.kt`
- `app/src/main/java/com/vignesh/leedcodecheckerollama/data/LeetCodeRepository.kt`
- `app/src/main/java/com/vignesh/leedcodecheckerollama/data/OllamaApi.kt`

## Detailed documentation

- See `DETAILED_DOCUMENTATION.md` for full feature and architecture details.
- Mermaid source and rendered image are in `docs/runtime_flow.mmd` and `docs/runtime_flow.png`.


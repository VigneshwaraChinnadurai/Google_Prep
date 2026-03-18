# LeedCode Checker Ollama (Android)

This app fetches the LeetCode daily problem and generates Python3 solution output using an Ollama-compatible endpoint.

## Project location

`mobile_apps/leedcode_checker_ollama`

## What this app does

- Calls LeetCode GraphQL API to fetch the daily challenge and statement.
- Calls Ollama `POST /api/generate` for code + explanation generation.
- Calls Ollama `GET /api/tags` + `POST /api/pull` to ensure the configured model is downloaded and stored in the phone-side Ollama runtime.
- Shows code, explanation, validation text, and full pipeline logs with timestamps.
- Lets user refresh and select the exact Ollama model from local tags before running.

## Configure Ollama endpoint

Create `local.properties` in this project root (same folder as `settings.gradle.kts`) and add:

```properties
OLLAMA_BASE_URL=http://127.0.0.1:11434/
OLLAMA_MODEL=qwen2.5:3b
```

Notes:

- `127.0.0.1` works when Ollama server runs on the same phone/device.
- On Android emulator targeting host machine Ollama, use `http://10.0.2.2:11434/`.
- This app is an Ollama client. The Ollama daemon/runtime must be running on the phone for true on-device model storage.

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


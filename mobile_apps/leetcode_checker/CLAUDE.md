# CLAUDE.md — LeetCode Checker

Instructions for Claude Code when working in this directory. For user-facing docs see [README.md](README.md), [BUILD_GUIDE.md](BUILD_GUIDE.md), [USER_MANUAL.md](USER_MANUAL.md).

## What this is

Android app (Kotlin, Jetpack Compose, MVVM/StateFlow) — daily LeetCode tracking with AI assistance, GitHub sync, interview prep, and a strategic-analysis chatbot. Package `com.vignesh.leetcodechecker`. Single developer/user (Vigneshwara) — this is a personal tool, not a multi-tenant product.

## Standing rules

- **After any code change is complete, `git add`/commit/push it.** If the change touches this app, also build the debug APK (`gradlew assembleDebug`) to confirm it compiles — don't just claim a fix works without building it.
- Prefer editing existing files; don't create new docs/summary files unless asked.

## Architecture conventions — follow these, don't deviate silently

- **Every external API integration uses raw Retrofit + OkHttp + Moshi/org.json.** GitHub, Gemini, LeetCode, AI news feeds — none of them use an official cloud SDK. If adding a new provider (e.g. Claude/Anthropic), match this pattern for consistency rather than pulling in an official SDK, unless explicitly told otherwise.
- **Secrets are settings-first, no BuildConfig fallback.** `AppSettingsStore.kt` holds one JSON blob in SharedPreferences (`leetcode_settings_prefs`) with every app-wide setting: `globalGithubToken`, `globalGeminiApiKey`, `backupFolderUri`, `lastBackupTimeMillis`, `githubOwnerOverride`/`githubRepoOverride`/`githubBranchOverride`, `revisionFolderName`, etc. The GitHub token in particular is deliberately **not** available via `local.properties`/`BuildConfig` — it must be entered in the app's Global Settings screen. Don't reintroduce a build-time fallback for it.
- **Only 4 files actually construct a Retrofit client**: `data/LeetCodeRepository.kt`, `prooffiling/ProofFilingRepository.kt`, `viewmodel/ChatbotViewModel.kt`, `ui/AIInterviewPrepScreen.kt`. Everything under `ai/*` and `pipeline/*` takes an API client via constructor injection — don't add new self-built Retrofit instances scattered through those layers.
- **Use internal storage (`context.filesDir`), not `getExternalFilesDir()`, for app-private files.** `getExternalFilesDir()` goes through a FUSE/sdcardfs bridge that can transiently throw `EACCES` on `mkdirs()`/writes even when the app legitimately owns the path (hit this in `RevisionExportManager.kt`, fixed by switching to `filesDir`). `BackupManager.kt` already zips all of `filesDir` automatically, so anything written there is covered by the periodic backup with no extra wiring.
- Anthropic/Claude is not currently integrated anywhere in this app (all AI features go through Gemini or local Ollama). If asked to add Claude calls, note that Claude has no embeddings endpoint — the RAG/dense-retrieval pipeline (`pipeline/DenseIndex.kt`, `pipeline/HybridRetrieval.kt`) would need to keep Gemini (or another provider) for embeddings even if generation moves to Claude.

## Build & signing

- Requires `JAVA_HOME` set to Android Studio's bundled JDK before any `gradlew` invocation (Windows: `C:\Program Files\Android\Android Studio\jbr`).
- **A permanent keystore signs both debug and release builds**: `app/release-keystore.jks` + `keystore.properties` (project root, alias `leetcodechecker`), both gitignored. This is intentional — it replaces AGP's default per-machine `~/.android/debug.keystore`, which regenerates silently and breaks `adb install -r` over an existing debug install. Don't let a future change fall back to the AGP default debug signing.
- `signingConfigs { }` must stay above `buildTypes { }` in `app/build.gradle.kts` — `buildTypes.release.signingConfig = signingConfigs.getByName("release")` evaluates eagerly and errors if the order is reversed.
- Debug APK: `.\gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`. `assembleDebug` does **not** run lint — run `gradlew lintDebug` explicitly to catch `[NewApi]` and similar static-analysis errors.
- minSdk 24 / targetSdk 35 with core library desugaring enabled (`desugar_jdk_libs:2.1.4`) to backport `java.time.*`. `List#removeFirst/removeLast` still need manual `removeAt()` rewrites — desugaring can't backport new default methods on an existing platform interface.

## On-device testing (ADB / wireless debugging)

- `adb` is not on PATH by default on this machine — full path: `C:\Users\Charumathi\AppData\Local\Android\Sdk\platform-tools\adb.exe`.
- **Signature mismatch (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`)** means the installed app and the new build are signed with different keystores (e.g., an old install predates the permanent-keystore setup, or was reinstalled via Android Studio's own default debug config). Fix: back up app data, uninstall, reinstall the correctly-signed build, restore data — see below. Verify signer mismatches with `apksigner verify --print-certs` before assuming this is the cause.
- **`DELETE_FAILED_DEVICE_POLICY_MANAGER`** on uninstall means Uninstall Protection (device admin, `security/LeetCodeDeviceAdmin`) is active. `adb shell dpm remove-active-admin ...` can fail with `SecurityException: Attempt to remove non-test admin` depending on Android version/OEM — when that happens, the only reliable path is asking the user to open the app and use the in-app "Disable Protection (Password Required)" button on the Uninstall Protection screen (`ui/UninstallProtectionScreen.kt`), which calls `removeActiveAdmin` from within the admin app itself and always works.
- **Data-preserving reinstall recipe**: pull app-private data first with `adb exec-out "run-as com.vignesh.leetcodechecker tar -cf - -C /data/data/com.vignesh.leetcodechecker ." > backup.tar`, uninstall, install the new APK, then restore with `adb push backup.tar /data/local/tmp/` + `adb shell "run-as com.vignesh.leetcodechecker sh -c 'cat /data/local/tmp/backup.tar | tar -xf - -C /data/data/com.vignesh.leetcodechecker'"`.
- For precise UI taps via `uiautomator`, use `adb shell uiautomator dump` and parse `bounds="[x1,y1][x2,y2]"` rather than guessing coordinates from a downscaled screenshot.
- Git Bash/MSYS path translation breaks `adb shell` commands with on-device Unix paths. Use `MSYS_NO_PATHCONV=1` for pure `adb shell` calls, but *not* for `adb push`/`pull` (it also mangles the local Windows-side argument) — for those, escape only the device-side path with a leading `//`. Simplest overall: run these from the PowerShell tool instead of Bash to avoid the translation issue entirely.

## Known gotchas

- `org.json.JSONObject.optString()` returns the literal string `"null"` (not blank) when the underlying JSON value is `null` — always guard with `.takeIf { it.isNotBlank() && it != "null" }`, not just a blank check.
- PKCS12 keystores (the modern `keytool` default) require `storePassword == keyPassword`; a different `-keypass` is silently ignored.
- `storeFile` in `signingConfigs` resolves relative to the **module** directory (`app/`), not the project root.

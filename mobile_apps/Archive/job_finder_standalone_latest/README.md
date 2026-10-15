# Job Finder — Standalone Android

> An on-device, agentic job-search assistant. No backend, no laptop, no Wi-Fi pairing. Paste your Gemini API key, drop in a resume, and the app does the rest.

This is the **standalone** sibling of [`agentic_job_finder_mobile`](../usecase_2/agentic_job_finder_mobile). The earlier project ran a FastAPI backend on a laptop and an Android client over LAN. This one runs everything on the phone.

---

## Read this before you build

This isn't the FastAPI version with the server compiled into the APK. The mobile environment is materially different from a Linux backend, and a few features simply don't translate. The README is honest about what you give up.

---

## What's gone vs. the FastAPI version

| Feature | FastAPI version | Standalone | Reason |
|---|---|---|---|
| Greenhouse + Lever ATS | ✅ 50+ companies | ✅ **18 companies** | Some slugs need re-verification on mobile UA; trimmed list to ones I'm confident about |
| Workday ATS | ✅ Worked | ❌ Removed | Many tenants soft-block residential IPs; would degrade silently |
| Generic Playwright fallback | ✅ ~25 companies (Google, Apple, Meta, Flipkart…) | ❌ Removed | Playwright cannot run on Android; no port exists |
| Multi-device usage | ✅ Backend + N clients | ❌ Single device | By design |
| Server-side caching | ✅ Hours per company | ⚠️ Per-device only | Each phone re-fetches |
| Shared `companies.yaml` editing | ✅ Live edit | ❌ Bundled in APK | Need a release to change. Future: load from a Gist URL |
| Live progress over WebSocket | ✅ | ✅ Replaced with `Flow<Event>` | Same UX |
| LLM cost | Server-paid | **User-paid (their API key)** | No alternative without a proxy |

## What's the same

- Resume → Scrape → Match → Apply → Contests workflow
- 4 contest platforms (HackerEarth, HackerRank, Unstop, Kaggle)
- Tracked-company matching against `companies.yaml`
- Cover-letter generation per match
- Approve / Skip / Apply UX

---

## Why this exists

The FastAPI version is the "right" architecture if you have a laptop to run it. This one is for situations where you don't:

- Job-hunting on the move
- A friend wants to try the app and you can't host them on your machine
- You want one binary you can sideload anywhere

In exchange, the user pays for their own LLM calls, and the company list is smaller.

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Android App (Kotlin)                 │
│              Compose UI · Hilt · DataStore              │
├─────────────────────────────────────────────────────────┤
│                    JobFinderRepository                  │
│  resume · jobs · matches · contests   (all in Room)     │
├──────────┬───────────┬──────────┬──────────┬────────────┤
│  Resume  │  Scrape   │  Match   │  Apply   │  Contests  │
│  Parser  │  (jobs)   │  (LLM)   │  (LLM)   │            │
├──────────┼───────────┼──────────┴──────────┼────────────┤
│ PDFBox   │ Greenhouse│   Gemini REST API   │ HackerEarth│
│ POI      │ Lever     │ (user's API key)    │ HackerRank │
│          │           │                     │ Unstop     │
│          │           │                     │ Kaggle     │
└──────────┴───────────┴─────────────────────┴────────────┘
```

No backend. No proxy. Everything outside the boxes labeled "Gemini" and "Greenhouse/Lever/etc." stays on the device.

---

## Repository layout

```
job_finder_standalone/
├── build.gradle.kts                         # plugin classpath
├── settings.gradle.kts
├── gradle/wrapper/
└── app/
    ├── build.gradle.kts                     # AGP 8.7, Kotlin 2.0, Compose
    ├── src/main/
    │   ├── AndroidManifest.xml
    │   ├── assets/companies.yaml            # 18-entry trimmed list
    │   └── java/com/vc/jobfinder/
    │       ├── JobFinderApp.kt              # @HiltAndroidApp
    │       ├── MainActivity.kt
    │       ├── domain/
    │       │   ├── Models.kt                # Resume, Job, MatchResult, Competition…
    │       │   └── CompanyMatcher.kt        # "Google LLC" → "Google"
    │       ├── data/
    │       │   ├── JobFinderRepository.kt   # replaces Python Orchestrator
    │       │   ├── db/                      # Room entities, DAOs, AppDatabase
    │       │   ├── local/                   # CompaniesLoader, SettingsStore
    │       │   └── remote/
    │       │       ├── ats/                 # Greenhouse, Lever
    │       │       └── contests/            # HackerEarth, HackerRank, Unstop, Kaggle
    │       ├── llm/
    │       │   ├── GeminiClient.kt          # direct REST, no SDK
    │       │   ├── Matcher.kt               # JSON-mode fit scoring
    │       │   └── CoverLetterGenerator.kt
    │       ├── parser/
    │       │   └── ResumeParser.kt          # PDFBox + Apache POI
    │       ├── di/AppModule.kt              # Hilt
    │       └── ui/
    │           ├── JobFinderRoot.kt         # 4-tab nav scaffold
    │           ├── settings/                # API key, model, threshold
    │           ├── resume/
    │           ├── jobs/                    # Run scrape + match
    │           ├── matches/
    │           └── contests/
    └── schemas/                             # Room exports schema here at build
```

32 Kotlin files, ~3000 lines of code. APK size ≈ 18 MB (Apache POI is a tank).

---

## Prerequisites

| | |
|---|---|
| **Android Studio** | Ladybug (2024.2) or newer |
| **Min SDK** | 28 (Android 9) |
| **Target SDK** | 35 (Android 15) |
| **Kotlin** | 2.0.21 (uses Compose Compiler plugin) |
| **JDK** | 17 |
| **Gemini API key** | Free tier from https://aistudio.google.com/apikey |

Free-tier Gemini gives you 1500 requests/day on `gemini-2.5-flash`. A single full Run = (jobs scraped) + 1 cover letter per "Apply" tap. With the 18-company list, that's roughly 100–200 requests per Run, well within free tier.

---

## Build & install

1. **Open in Android Studio**
   ```
   File → Open → C:\Users\vichinnadurai\Documents\Vignesh\Personal Enrichment\Personal Work\Google_Prep\agentic_ai\usecase_3\job_finder_standalone
   ```
   (You can put it anywhere; `usecase_3` is a suggestion to keep things organized.)

2. **Let Gradle sync.** First sync downloads ~400 MB of Compose, Hilt, PDFBox, POI, Ktor. Subsequent builds are fast.

3. **Plug in your phone** (USB debugging on) or start an emulator.

4. **Run.** Android Studio's green play button. Or from CLI:
   ```powershell
   .\gradlew installDebug
   ```

5. **First launch flow:**
   - App opens to **Settings**
   - Paste your Gemini API key (stored encrypted on-device)
   - Confirm `gemini-2.5-flash` (default) or use `gemini-2.5-pro` for higher quality at higher cost
   - Save and continue

---

## Daily workflow

1. **Resume tab** — pick a PDF or DOCX. PDFBox-Android extracts text; POI handles `.docx`. Heuristic regex pulls name/email/phone/LinkedIn/GitHub. Anything missed doesn't matter — the full text goes to the LLM at scoring time.

2. **Run tab** — tap **Run scrape + match**. The repo:
   - Hits Greenhouse and Lever for all 18 companies (4 in parallel by default)
   - Dedupes by `(company, title, applyUrl)`
   - Persists to Room
   - Then sends each job to Gemini for fit scoring (4 in parallel)
   - Preserves `approved` / `skipped` status from previous runs

3. **Matches tab** — slide the threshold, scan ranked results, tap **Approve** or **Skip**. Tap **Apply** to:
   - Generate a 180–220-word cover letter targeted at this specific role
   - Show field hints (name, email, phone, LinkedIn, GitHub) ready to copy
   - Open the application URL in your default browser

4. **Contests tab** — tap **Refresh** to fetch from all 4 platforms in parallel. Toggle:
   - **Tracked companies** — only contests sponsored by your `companies.yaml` list
   - **Hiring only** — exclude non-recruitment events

   Tracked-company contests sort to the top with an "In your list" badge.

---

## What runs on what

| Component | On device | Off device |
|---|---|---|
| Resume parsing | ✅ PDFBox + POI | |
| Company config | ✅ assets/companies.yaml | |
| Job scraping | ✅ Ktor → Greenhouse/Lever | Their APIs |
| Job storage | ✅ Room (SQLite) | |
| Fit scoring | | Gemini API |
| Cover letter | | Gemini API |
| Contest fetching | ✅ Ktor + Jsoup | Their public endpoints |

The only outbound traffic is to:
- `boards-api.greenhouse.io`
- `api.lever.co`
- `generativelanguage.googleapis.com` (Gemini)
- `hackerearth.com`, `hackerrank.com`, `unstop.com`, `kaggle.com` (Contests tab only)

No analytics, no telemetry, no proxy.

---

## Limitations and gotchas

### Battery and data
A full Run hits 18 ATS endpoints + 1 LLM call per scraped job. If 18 companies yield 100 jobs, that's ~120 HTTPS round-trips. Expect 2–5 minutes on a decent connection and 3–8% battery on a Pixel 7. Plug in for big runs.

### Mobile UA blocking
Greenhouse and Lever serve their public job-board APIs to anyone, but Cloudflare-fronted tenants sometimes flag "unusual" mobile UAs. The adapter sets a desktop Chrome UA string to reduce this. If a company returns 0 jobs consistently, it's almost always a UA / IP issue, not a config error.

### API key on device
The key sits in `EncryptedSharedPreferences` (AES-256 GCM via Android's `MasterKey`). This is safe against casual file-system browsing but **not** against a determined attacker with root access. Don't store production keys on a phone you might lose.

### Free-tier rate limits
1500 RPD on `gemini-2.5-flash` resets daily. If you hit a 429, the Run task continues — only the failed scoring slots get a fallback `fit_score = 0.0` with a "Could not parse model output" reasoning. They re-score next Run.

### `companies.yaml` is bundled
Add a company → edit YAML → rebuild APK → reinstall. **Future**: load from a public Gist URL on app start with a fallback to the bundle.

### HackerEarth contest scraping is fragile
HackerEarth doesn't expose a contest API. The adapter parses `div.challenge-card-modern` from the listing HTML. If they ship a redesign, this returns `[]` silently and the other 3 platforms keep working. Easy to fix when it breaks; harder to detect.

### Resume parsing is best-effort
On-device regex heuristics will miss things on creative layouts (multi-column resumes, infographic-style CVs). The full extracted text always goes to the LLM at scoring time, so the visible "name not detected" warning rarely affects match quality. It does affect the **Apply** field hints, where the LLM isn't in the loop.

### No iOS path
By writing scrapers and parsers in Kotlin instead of behind a HTTP API, this codebase becomes Android-only. If you ever want iOS, you'll be looking at KMP or a rewrite. The FastAPI version had no such limitation.

---

## Adding a company

1. Open `app/src/main/assets/companies.yaml`.
2. Append:
   ```yaml
   - name: NewCo
     ats: greenhouse        # or "lever"
     slug: newco            # the bit between /boards/ or /jobs/ and the next /
   ```
3. Rebuild and reinstall.

Verify slugs by visiting the company's public board:
- Greenhouse: `https://boards.greenhouse.io/<slug>`
- Lever: `https://jobs.lever.co/<slug>`

---

## Adding a contest platform

1. Add a new adapter in `data/remote/contests/`:
   ```kotlin
   @Singleton
   class FooAdapter @Inject constructor(
       private val matcher: CompanyMatcher,
   ) : ContestAdapter {
       override suspend fun fetch(): List<Competition> { /* ... */ }
   }
   ```
2. Register it in `ContestRegistry`'s constructor.
3. Add an entry to the `Platform` enum and the `PlatformPill` color map in `ContestsScreen.kt`.

---

## Troubleshooting

**"No API key configured" on Run** — Open Settings, paste a key, hit Save.

**Run finishes with 0 jobs** — Mobile UA likely blocked. Try over Wi-Fi instead of cellular; many ATS WAFs are stricter on carrier IPs.

**"Could not parse model output" on every match** — Gemini returned non-JSON despite `responseMimeType=application/json`. Check Gemini Studio for service status; this happens during outages.

**Build fails with "Could not resolve POI"** — Apache POI requires `compileSdk` ≥ 34 and a few `META-INF` packaging excludes. Both are configured in `app/build.gradle.kts`. If you bumped Gradle/AGP, regenerate from this template.

**"Duplicate class kotlin.collections.jdk8.*"** — Kotlin stdlib version mismatch. `kotlin.android` plugin should pull a consistent stdlib; if you added a library that pins an older one, exclude it.

**App crashes opening Resume tab** — PDFBox needs `PDFBoxResourceLoader.init(ctx)` before any PDF operation. The parser does this in `init`, but if you Hilt-injected it lazily, it may fire on first parse instead. The included code initializes eagerly.

---

## Roadmap (in order of likely usefulness)

1. **Workday support via direct API calls** — most Workday tenants accept POSTs to their public job-feed endpoint without a real browser, if you craft the body correctly. Adapter would replace Playwright for ~half the missing 25 companies.
2. **Pull `companies.yaml` from a Gist URL** with periodic refresh — no more APK rebuild for new companies.
3. **WorkManager-driven daily background refresh** — run scrape+match overnight on charger; show a notification when high-score matches arrive.
4. **Distinct platform endpoints for HackerEarth** if they ever publish one.
5. **iOS via KMP** — only if there's actual demand. Currently low priority.

---

## Legal & ToS

- The app sends ATS requests at human pace (max 4 in flight, no retry storms).
- Contest scraping is one HTTP call per platform per **manual** refresh tap.
- Final job submission is **always manual** — the app never POSTs to an apply endpoint.
- LinkedIn / Indeed / Naukri are not scraped because their ToS forbid it.
- Your resume never leaves the device except as part of an LLM prompt to Gemini.

---

## Comparison with the FastAPI version

If you have both projects and want to know which to use:

| Use this if… | Use the FastAPI version if… |
|---|---|
| You want a single APK to install | You want richer ATS coverage (50+ companies) |
| You don't always have a laptop | You want one shared cache across phone/tablet |
| You're okay paying for your own LLM calls | You want to centralize cost on a server |
| You only care about Greenhouse + Lever | You need Workday or Playwright-driven sites |
| You want a reference for offline-first AI apps | You want a reference for FastAPI + Compose |

Both projects share `companies.yaml` semantics, `CompanyMatcher`, the contest platform list, and the overall data flow. Code duplication is intentional — keeping them in lockstep would require KMP, which is a bigger commitment than either version warrants right now.


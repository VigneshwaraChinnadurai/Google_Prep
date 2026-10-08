# Job Matcher

**Version 1.1.0** · Android (Kotlin, Jetpack Compose) · package `com.vignesh.jobmatcher`

Job Matcher watches the careers sites of a hand-picked list of companies, finds openings in
India that fit the resume, and keeps only the ones that match at **≥80%** (configurable).
Every LLM step goes through **Claude by manual copy-paste**: the app builds the prompt, you
send it in the Claude app, and paste the reply back. There's no API key and no per-request
billing; it's the same pattern as LeetCode Checker's "Claude (Manual)" provider.

---

## Contents

1. [Quick start](#1-quick-start)
2. [How it works: the pipeline](#2-how-it-works-the-pipeline)
3. [The four tabs](#3-the-four-tabs)
4. [The Claude copy-paste workflow](#4-the-claude-copy-paste-workflow)
5. [Companies and careers sources](#5-companies-and-careers-sources)
6. [Matching and scoring](#6-matching-and-scoring)
7. [Settings reference](#7-settings-reference)
8. [Daily auto-fetch and notifications](#8-daily-auto-fetch-and-notifications)
9. [Data and storage](#9-data-and-storage)
10. [Troubleshooting](#10-troubleshooting)
11. [Developer guide](#11-developer-guide)
12. [Known limitations](#12-known-limitations)

---

## 1. Quick start

**One-time setup**
1. Install the Claude app on the same phone and sign in. In Claude, turn on **web search**;
   the company-search prompts need it.
2. Open Job Matcher and allow notifications when asked. The daily fetch uses them.
3. *(Optional)* **Setup → Profile → Import PDF** with your latest resume, then run
   **Profile analysis** (see §4.3). The app ships with the Sep 2026 resume and a skill
   profile built from it, so this step is only needed when the resume changes.

**Daily routine (about 5–10 minutes)**
1. **Discover → ① Fetch now**, or just open the notification from the 07:00 auto-fetch.
2. **② Score the shortlist**: *Copy next 12* → send in Claude → copy Claude's whole reply →
   *Paste Claude reply*. Repeat until "Awaiting Claude" reaches 0.
3. **③ Claude web search**: *Search Google* (or *Search next 4*) → send → paste the reply.
4. Open **Matches**, review the new jobs, mark interesting ones **Saved**, and use the
   **Application kit** to tailor your resume before applying.

> The first run has a backlog (a few big employers alone shortlist ~150 jobs). After that,
> each day only adds new postings.

---

## 2. How it works: the pipeline

```
 ┌──────────────────────────── AUTOMATIC (12 companies) ─────────────────────────────┐
 │ Careers APIs ──► Location gate ──► Title gate ──► On-device score ──► Shortlist  │
 │ Workday, Eightfold,  (India cities)  (no intern/    (0-100, ≥50 to      (awaiting  │
 │ Oracle HCM, Smart-                    sales/...)     pass by default)     Claude)   │
 │ Recruiters, Amazon                                                          │       │
 └─────────────────────────────────────────────────────────────────────────────┼───────┘
                                                                               ▼
                                              ② Claude scoring (12 jobs per prompt)
                                                                               │
 ┌──────────── CLAUDE WEB SEARCH (14 companies, incl. Google) ──────────┐      ▼
 │ ③ Search prompt ──► Claude searches official careers sites ──► jobs  ├──► Scored jobs
 │   (Google alone once a day; others rotate 4 at a time)   arrive scored│      │
 └──────────────────────────────────────────────────────────────────────┘      ▼
                                      Claude score ≥ 80 ──► MATCHES ──► Tracker + Application kit
                                      Claude score < 80 ──► "below threshold" (hidden by default)
```

| Stage | Where it runs | What it does |
|---|---|---|
| **Fetch** | Phone → careers sites | Pulls live postings from public, key-less careers APIs |
| **Location gate** | Phone | Keeps postings whose location mentions one of your location keywords |
| **Title gate** | Phone | Drops titles containing excluded words (intern, sales, recruiter…) |
| **On-device score** | Phone | Fast keyword relevance score; only jobs scoring ≥ pre-filter reach Claude |
| **Claude scoring** | Claude app (manual) | Calibrated 0–100 match score, verdict, reasons, matched skills, gaps |
| **Claude web search** | Claude app (manual) | Finds and scores jobs at companies with no public API |
| **Matches** | Phone | Everything Claude scored ≥ the match threshold, top-choice companies first |

---

## 3. The four tabs

### 🔎 Discover
The control centre.
- **Pipeline** card: *Tracked jobs* (open postings stored), *Awaiting Claude* (the
  shortlist), *Matches ≥80%*.
- **① Fetch from careers sites**: fetches every enabled automatic-source company and shows
  progress per company. The result message lists how many new jobs reached the shortlist
  and which companies failed, if any.
- **② Score the shortlist with Claude**: the copy/paste round-trip for batch scoring.
- **③ Claude web search**: the copy/paste round-trip for companies without an API. The card
  names the companies the next prompt will cover.
- Below the cards: the **Awaiting Claude** list, highest on-device score first. Tap any job
  to open it.

### 🎯 Matches
- Jobs Claude scored **≥ the match threshold**. ⭐ **top-choice companies (Google) are
  always listed first**, then by score, then newest.
- Each card shows title, company, location, the Claude score pill (green ≥ threshold,
  amber within 15 points, red below), the verdict and status.
- **Show below-threshold** reveals scored jobs under the cutoff, which helps when calibrating
  the threshold.

### 📋 Tracker
Jobs you've moved out of *New*, grouped as **⭐ Saved → 📨 Applied → 🗣️ Interviewing → 🎉 Offer
→ ❌ Rejected**, each with the applied/updated date and the first line of your notes.

### ⚙️ Setup
Three sub-tabs:
- **Profile**: resume (import a PDF or edit the text), the Profile-analysis round-trip, and
  the current skill profile (headline, years, target titles, weighted skills ★–★★★).
- **Companies**: every company with its source, identifier, last-fetch status and
  enable/disable switch; **Test fetch** for automatic sources; **Edit** (name, priority,
  source, identifier, delete); **➕ Add company**.
- **Settings**: thresholds, batch sizes, keywords and auto-fetch (see §7).

### Job detail (tap any job)
- **🔗 Open posting / apply**, posted date, source, and a warning if the posting is closed
  or was found by Claude search (open the link to confirm it's live).
- **Scores**: Claude score + verdict + reasons, ✅ matched skills, ⚠️ gaps, and the
  on-device score with its keyword hits.
- **Score / Re-score this job with Claude**: a single-job scoring round-trip.
- **Status** chips and **Notes** (referrals, recruiter name, interview dates…).
- **Application kit**: a Claude round-trip that returns a match summary, 6–8 tailored resume
  bullets, a cover letter (<250 words), a gap plan and a LinkedIn referral message, each
  with a **Copy** button. Claude is instructed to use **only facts from your resume**.
- **Job description**, collapsible and selectable, plus **Copy job link**.

---

## 4. The Claude copy-paste workflow

Every Claude step is the same two-button round-trip:

1. **Copy button**: the app builds the prompt, copies it to the clipboard and opens the
   **Claude app directly** (or a share sheet if Claude isn't installed) with the prompt
   pre-filled.
2. In Claude: **send**, wait for the full answer, then **copy the entire reply** (long-press
   → Copy, or the copy icon under the message).
3. Back in Job Matcher: **📋 Paste Claude reply**. The app reads the clipboard and parses it.

The parser is forgiving: it accepts code fences around the JSON, extra text before or after,
and a missing closing tag at the very end. If you accidentally paste **the prompt itself**
instead of Claude's answer, the app recognises it and tells you, so nothing gets saved.

### 4.1 Scoring prompt (Discover ②, or Re-score on a job)
Sends your profile, full resume (toggleable) and the next batch of shortlisted jobs
(12 by default, each description trimmed to 2,000 characters). Claude scores each one:

| Weight | Criterion |
|---|---|
| 40 | Core skills & domain overlap with the posting's must-haves |
| 25 | Seniority & scope fit (years, IC vs. manager/architect) |
| 20 | Hard requirements met (PhD, specific languages, clearances…) |
| 15 | Career-trajectory fit |

Calibration given to Claude: **90–100** apply today · **80–89** strong, minor gaps ·
**65–79** partial · **<65** poor. A role that needs a PhD, or 4+ more years than you have,
must score below 70.

Reply format: `<job_scores>[{id, score, verdict, reasons, matched_skills, gaps}]</job_scores>`.
Scores are matched to jobs by id; if Claude shortens an id, the app falls back to its
trailing part.

### 4.2 Web-search prompt (Discover ③)
For companies without a public API. Claude is told to:
- search **only official careers sites**, not job boards like Naukri, Indeed or Glassdoor
  (LinkedIn's own roles on linkedin.com/jobs are allowed for LinkedIn),
- return **only postings it actually found open today, with a direct URL**. Jobs without a
  URL are dropped by the app,
- skip junior/intern roles and roles outside your locations,
- return at most 15 roles per company (**25 when searching the top-choice company alone**,
  with a "search thoroughly" instruction), scoring 60+ only, using the same rubric,
- skip jobs already tracked (the prompt lists them),
- answer "nothing suitable" if that's the truth. An empty reply is valid, and those
  companies are still marked as searched.

Reply format: `<jobs_json>[{company, title, location, url, posted_date, description, score,
verdict, reasons, matched_skills, gaps}]</jobs_json>`. These jobs arrive **already scored**,
so they skip the scoring step.

**Batching:** a ⭐ top-choice company that hasn't been searched in the last 24 hours always
gets a prompt **to itself**. Otherwise the app takes the **least-recently-searched**
companies, 4 at a time (configurable), so the whole list rotates. Pasting a reply marks
exactly the companies in that prompt as searched.

### 4.3 Profile-analysis prompt (Setup → Profile)
Turns the resume into the structured profile the on-device scorer uses: headline, years of
experience, 12–20 target titles, 25–40 weighted skills with aliases, and 4–8 search terms
used to query the careers sites. Pasting it replaces the profile and re-scores every stored
job on-device. **Reset to bundled default** restores the profile built from the Sep 2026 resume.

### 4.4 Tailoring prompt (Job detail → Application kit)
Sends the full resume and the full job description. The reply returns five tagged sections:
`<match_summary>`, `<resume_bullets>`, `<cover_letter>`, `<gap_plan>`, `<referral_message>`.
These are saved on the job and shown with Copy buttons.

---

## 5. Companies and careers sources

### 5.1 Bundled company list (verified against the live sites on 2026-10-08)

| Priority | Company | Source | Identifier |
|---|---|---|---|
| ⭐ 1 | **Google** | Claude web search | google.com/about/careers (India) |
| 2 | Amazon | Amazon Jobs | n/a |
| 2 | Microsoft | Eightfold | `https://apply.careers.microsoft.com?domain=microsoft.com` |
| 2 | Qualcomm | Eightfold | `https://careers.qualcomm.com?domain=qualcomm.com` |
| 2 | Morgan Stanley | Eightfold | `https://morganstanley.eightfold.ai?domain=morganstanley.com` |
| 2 | JPMorgan Chase | Oracle HCM | `https://jpmc.fa.oraclecloud.com/hcmUI/CandidateExperience/en/sites/CX_1001` |
| 2 | Adobe | Workday | `https://adobe.wd5.myworkdayjobs.com/external_experienced` |
| 2 | Target | Workday | `https://target.wd5.myworkdayjobs.com/targetcareers` |
| 2 | PayPal | Workday | `https://paypal.wd1.myworkdayjobs.com/jobs` |
| 2 | VMware (Broadcom) | Workday | `https://broadcom.wd1.myworkdayjobs.com/External_Career` |
| 2 | NVIDIA | Workday | `https://nvidia.wd5.myworkdayjobs.com/NVIDIAExternalCareerSite` |
| 2 | Nike | Workday | `https://nike.wd1.myworkdayjobs.com/nke` |
| 2 | Freshworks | SmartRecruiters | `Freshworks` |
| 2 | Apple, Meta (Facebook), Tesla, Uber, LinkedIn, Atlassian, Walmart (Global Tech India), 7-Eleven (GSC India), Visa, Goldman Sachs, Wells Fargo, Flipkart, Myntra | Claude web search | each company's careers page URL |

Why those 14 use Claude search: as of Oct 2026, none of them exposes a usable public JSON
feed. Some have no public feed at all (Google, Apple, Meta, Goldman, Flipkart…), Uber's
endpoint was removed, Walmart/Wells Fargo left Workday, and Visa isn't on SmartRecruiters.

Live sample (search term "machine learning", 2026-10-08): Amazon 285 India postings ·
Microsoft 39 · Qualcomm 40 · NVIDIA 40 · JPMorgan 39 · Freshworks 33 · Target 12 · Nike 12 ·
Adobe 7 · Morgan Stanley 5 · VMware 1 · PayPal 0 for that term (it does have Bangalore AI
roles under broader terms).

### 5.2 Priority
- **⭐ Top choice (1)**: listed first in Matches with a star, and searched alone by Claude
  once a day.
- **🎯 Target (2)**: the default.
- **Backup (3)**: rotates last in Claude-search batches when other things are equal.

Change it in **Setup → Companies → Edit → Priority**.

### 5.3 Supported sources and how to add a company

| Source | What to enter as identifier | How to find it |
|---|---|---|
| **Workday** | Full careers URL | Open the company's "Apply" page. The URL looks like `https://<tenant>.wd<N>.myworkdayjobs.com/<site>` (locale segments like `en-US` are ignored) |
| **Eightfold** | Careers host + `?domain=` | Careers site served from `*.eightfold.ai` or a branded host. Domain defaults from the host if omitted |
| **Oracle HCM** | Candidate Experience URL | URL contains `/hcmUI/CandidateExperience/<lang>/sites/<SITE_NUMBER>` |
| **SmartRecruiters** | Company id | `jobs.smartrecruiters.com/<id>` |
| **Greenhouse** | Board token | `boards.greenhouse.io/<token>` or `job-boards.greenhouse.io/<token>` |
| **Lever** | Company slug | `jobs.lever.co/<slug>` |
| **Ashby** | Org slug | `jobs.ashbyhq.com/<slug>` |
| **Amazon Jobs** | (none) | Amazon only |
| **Claude web search** | Careers page URL (optional) | Anything else. The URL just helps Claude look in the right place |

After adding a company, tap **Test fetch** on its card. The status line shows "N in your
locations" or the exact error.

### 5.4 How each source is fetched

| Source | Search | Location filtering | Details |
|---|---|---|---|
| Workday | One query per profile search term, 3 pages × 20 | Reads the tenant's location facet (`locationCountry` / `locationHierarchy1` / `locations`; names vary) and re-queries with it. "India" and city names are matched as whole words, so *Indiana ≠ India* | Detail call per posting, max 60 per company |
| Eightfold | `/api/pcsx/search`, 10 per page (fixed), up to 4 pages per term | `location=India` server-side | `/api/pcsx/position_details`, max 60 |
| Oracle HCM | `recruitingCEJobRequisitions` finder, 25 per page, 3 pages per term | `location=India` server-side | `recruitingCEJobRequisitionDetails`, max 60 |
| SmartRecruiters | Lists all postings, 100 per page, up to 5 pages | `country=in` server-side | Detail call per posting, max 60 |
| Amazon | `search.json`, 100 per page, 3 pages per term | `normalized_country_code[]=IND` (the `loc_query` param barely filters, ~13% India) | Description + qualifications are in the list response |
| Greenhouse / Lever / Ashby | Whole board in one call | Local location gate | Included in the list response |

Any response that comes back as HTML instead of JSON (Eightfold does this intermittently
when throttling) is retried up to 3 times with backoff.

---

## 6. Matching and scoring

### 6.1 Gates (before any scoring)
- **Location**: the posting's location text must contain one of the location keywords
  (default: India and the major Indian tech cities).
- **Title**: titles containing any excluded keyword (whole-word match, so "intern" doesn't
  block "Internal Tools") are dropped.

### 6.2 On-device score (0–100)

```
score = 50% skill coverage + 35% title fit + 15% seniority fit
```

- **Skill coverage**: sum of the weights (1–3) of profile skills found in the title or
  description (via name or alias, whole-word), divided by 18 and capped at 1.0. Roughly
  six core skills gives full coverage.
- **Title fit**: 1.0 if the title contains a target title; otherwise partial credit (up to
  0.7) for AI/ML tokens such as *ML, AI, applied, scientist, GenAI, LLM, architect*.
- **Seniority fit**: junior/intern titles score 0.1; level-numbered "I"/"II" titles score 0.2;
  senior/staff/lead/principal/manager/architect titles score 1.0. Required years > yours + 4
  gives 0.3, > yours + 1 gives 0.7.
- **Relevance caps**: no AI/ML signal in the title caps the score at **40**; no core
  (★★★) skill anywhere caps it at **25**.

Calibration (441 live India postings from Amazon + NVIDIA, Oct 2026): with the default
pre-filter of 50, about **155** pass. They're mostly Applied Scientist, ML, GenAI and
Solutions Architect roles. Generic SDE, analyst, PM and hardware roles fall below 50.

### 6.3 Claude score
The real decision; see the rubric in §4.1. A job appears in **Matches** when
`claudeScore ≥ match threshold` and it isn't dismissed.

---

## 7. Settings reference

| Setting | Default | Range | Effect |
|---|---|---|---|
| Match threshold | **80%** | 50–95 | Claude-score cutoff for the Matches tab |
| On-device pre-filter | **50%** | 0–80 | Local score needed to reach the Claude shortlist. Lower means more Claude rounds and fewer missed roles |
| Jobs per Claude scoring prompt | **12** | 3–20 | Batch size for ② |
| Description chars per job in prompt | **2000** | 1000–6000 | Trims each JD in scoring prompts |
| Companies per Claude web-search prompt | **4** | 1–8 | Batch size for ③ (top choice always goes alone) |
| Include full resume in scoring prompts | **On** | | Off = profile summary only (shorter prompts, slightly less accurate) |
| Location keywords | india, bengaluru, bangalore, hyderabad, chennai, pune, mumbai, gurgaon, gurugram, noida, delhi | comma-separated | Location gate. The first "India" keyword also drives server-side country filters |
| Exclude titles containing | intern, internship, new grad, graduate, apprentice, junior, account executive, sales, recruiter, marketing, legal, counsel, accountant, payroll, facilities, administrative | comma-separated | Title gate |
| Daily auto-fetch + notification | **On** | | See §8 |
| Fetch at | **07:00** | 0–23 h | Time of the daily run |

**Save settings** applies the changes. Changing the location or exclude keywords re-scores
stored jobs; **Restore defaults** resets everything.

---

## 8. Daily auto-fetch and notifications

- A WorkManager job runs **once every 24 h, starting at the configured hour**, whenever the
  phone has network. Android may delay it by a little under battery optimisation.
- It fetches all enabled automatic-source companies and, if any new jobs cleared the
  pre-filter, posts a notification: *"N new jobs worth a look — M waiting for Claude
  scoring."* Tapping it opens the app.
- Claude web search **isn't** automatic, since it needs you in the Claude app. Do ③ as part
  of your routine.
- Turn it off or change the hour in **Setup → Settings**. The schedule updates immediately.

---

## 9. Data and storage

Everything stays on the phone, in the app's private storage. Nothing is sent anywhere except
the careers-site requests and whatever you paste into Claude yourself.

| Data | Where |
|---|---|
| Settings | SharedPreferences `job_matcher_prefs` (one JSON blob) |
| Companies | `files/companies.json` |
| Jobs (scores, status, notes, tailoring) | `files/jobs.json` |
| Skill profile | `files/profile.json` (falls back to the bundled default) |
| Resume text | `files/resume.txt` (falls back to the bundled Sep 2026 resume) |

**Job lifecycle**
- Job id: `<SOURCE>:<companyId>:<externalId>`. Re-fetching updates the posting but **keeps
  your status, notes, Claude score and tailoring**.
- A job that disappears from its company's API is marked **"Posting closed"** (greyed card)
  but kept. Closed jobs still in *New* status are deleted after **45 days**; jobs you're
  tracking are never auto-deleted.
- Claude-search jobs are de-duplicated by URL.
- Deleting a company removes its *New* jobs and keeps any you're tracking.

**Bundled company-list updates**: when a new app version ships more companies, they're merged
into your list on first launch. Companies you edited or deleted are never re-added or
overwritten.

**Backup**: `allowBackup` is on, so Android's device backup covers the app.
For a manual copy over ADB:
`adb exec-out "run-as com.vignesh.jobmatcher tar -cf - -C /data/data/com.vignesh.jobmatcher ." > jm_backup.tar`

---

## 10. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| "This looks like the prompt you copied, not Claude's reply" | You pasted before copying Claude's answer. Copy Claude's reply, then tap Paste again |
| "Couldn't find the <job_scores> JSON…" | The reply was cut off or only partly copied. Use the copy icon under Claude's message to copy all of it |
| "None of the scored job ids match jobs in the app" | You pasted a reply for an older batch whose jobs were since re-scored or removed. Copy a fresh prompt |
| Claude search returns links that 404 | Postings close quickly, and Claude can be wrong. The app flags these jobs; always open the link before investing time |
| Claude search finds nothing | Make sure **web search is enabled** in Claude. "Nothing suitable" is also a valid answer; the batch is still marked searched and rotates on |
| Company shows ⚠️ error after fetch | Tap **Edit** and check the identifier format (§5.3), then **Test fetch**. "Not a Workday careers URL" / "Missing board token" are identifier problems; timeouts and "non-JSON page (throttled?)" are temporary, so retry later |
| A company returns 0 jobs | Usually real: no India openings for your search terms right now (e.g. PayPal for "machine learning"). Broaden the profile's search terms via Profile analysis |
| Too many jobs awaiting Claude | Raise the pre-filter (e.g. 55–60), raise the jobs-per-prompt, or add exclude keywords |
| Good roles never reach Claude | Lower the pre-filter, or re-run Profile analysis so the skill aliases match how postings phrase things |
| No daily notification | Check the notification permission and that auto-fetch is on. Notifications only fire when *new* jobs clear the pre-filter |
| Claude app doesn't open | The share sheet appears instead; pick Claude. If Claude isn't installed, paste the prompt (already on your clipboard) into claude.ai |

---

## 11. Developer guide

### 11.1 Build, test, install

```powershell
cd mobile_apps\job_matcher
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'

.\gradlew assembleDebug                          # app\build\outputs\apk\debug\app-debug.apk
.\gradlew testDebugUnitTest                      # offline JVM tests (parsers, scorer, prompts, batching)
.\gradlew testDebugUnitTest -PliveApiTests=1     # + hits every seeded careers API for real
.\gradlew lintDebug                              # assembleDebug doesn't run lint

& "C:\Users\Charumathi\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

- **Signing** reuses LeetCode Checker's permanent keystore so debug builds always install
  over each other. `keystore.properties` (gitignored) holds
  `storeFile=../../leetcode_checker/app/release-keystore.jks` plus the passwords; see
  `keystore.properties.example`.
- `local.properties` (gitignored) needs `sdk.dir`.
- Stack: AGP 8.6.1, Kotlin 1.9.24, Compose BOM 2024.09.01, minSdk 24 / targetSdk 35,
  core-library desugaring.

### 11.2 Project layout

```
app/src/main/
├── assets/
│   ├── default_companies.json   seed company list (bump DefaultData.SEED_VERSION when it grows)
│   ├── default_profile.json     skill profile hand-derived from the Sep 2026 resume
│   └── default_resume.txt       resume text (pdftotext of the Sep 2026 PDF)
└── java/com/vignesh/jobmatcher/
    ├── MainActivity.kt          tabs, job-detail navigation, notification permission, schedules worker
    ├── JobViewModel.kt          UiState (+ derived matches/shortlist/tracked/nextSearchBatch), actions
    ├── model/Models.kt          SourceType, Company, Job, JobStatus, Tailoring, CandidateProfile, AppSettings
    ├── data/
    │   ├── AppStorage.kt        prefs + JSON files, single lock, seed migration, pending-search ids
    │   ├── JsonCodec.kt         org.json (de)serialization (also parses Claude's <profile_json>)
    │   ├── DefaultData.kt       asset loaders, SEED_VERSION
    │   └── JobRepository.kt     the pipeline: fetch → gate → score → merge; Claude apply-*; search batching
    ├── sources/
    │   ├── CareersApi.kt        the one Retrofit/OkHttp client (absolute @Url, String bodies)
    │   ├── JobFetcher.kt        per-source fetch logic, Workday/Eightfold/Oracle targets, JSON-or-retry GET
    │   ├── JobParsers.kt        pure JSON → RawPosting parsers per ATS, Workday facet selection
    │   └── HtmlText.kt          JD HTML → readable text (keeps paragraphs and bullets)
    ├── matching/LocalScorer.kt  on-device score, location/title gates
    ├── claude/
    │   ├── PromptBuilder.kt     the four prompts (profile, scoring, search, tailoring) + PROMPT_MARKER
    │   ├── ClaudeResponseParser.kt  tolerant tagged-block parsing
    │   └── ClaudeHandoff.kt     clipboard + share intent (opens com.anthropic.claude directly)
    ├── work/DailyFetchWorker.kt WorkManager daily fetch + notification
    └── ui/                      Discover, Matches, Tracker, Setup, JobDetail screens + shared Components
app/src/test/                    JobParsersTest, NewSourcesTest, MatchingAndClaudeTest, LiveApiTest
```

### 11.3 Conventions
- **No LLM API keys, ever.** New LLM features follow PromptBuilder → ClaudeHandoff →
  ClaudeResponseParser. Every prompt starts with `PROMPT_MARKER`, and replies use a unique
  tagged block.
- **One Retrofit instance** (`CareersApi.create()`). Parsers stay pure (no Android types) so
  JVM tests cover them with fixtures copied from real responses.
- **All persisted writes go through `AppStorage.update*`**: the worker and the UI share
  `jobs.json`.
- Use internal storage (`filesDir`), not `getExternalFilesDir()`.
- **User-visible change → bump `versionCode`/`versionName`** in `app/build.gradle.kts`.
- After a change: run the tests, build, then commit and push. Before pushing, `git fetch`;
  the LeetCode app pushes `Add LeetCode QA revision…` commits in the background.

### 11.4 Adding a new careers source
1. Add a `SourceType` entry (label, `automatic = true`, identifier hint).
2. Add a pure parser in `JobParsers` (list → ids/total, detail → `RawPosting`) and a fixture
   test in `NewSourcesTest`.
3. Add the fetch function in `JobFetcher` (use `getJson`, cap detail calls at `MAX_DETAILS`,
   and filter titles before detail calls) and wire it into `fetch()`'s `when`.
4. Add the company to `default_companies.json`, bump `DefaultData.SEED_VERSION`, and run
   `-PliveApiTests=1`.

### 11.5 Re-calibrating the on-device scorer
Change weights in `LocalScorer` only against real data: fetch a few large companies in a
scratch JVM test, print the score distribution and the titles around the cutoff, and check
that relevant roles sit above the pre-filter and generic ones below. The current calibration
is described in §6.2.

---

## 12. Known limitations

- **Claude-search companies depend on Claude's web search**: results can include stale or
  occasionally wrong links, and coverage varies by how well each careers site is indexed.
  Google, the top choice, is in this group because it has no public jobs API.
- **Careers APIs are unofficial** and can change without notice. `LiveApiTest` is the
  early-warning check; run it when a company starts failing.
- **Workday** only exposes multi-location details per posting, so very large tenants are
  capped (3 pages per search term, 60 detail calls per company).
- **Manual scoring rounds**: a large backlog takes several copy/paste rounds. Tune the
  pre-filter and batch size to taste.
- **India-focused**: country-level server filters assume "India" is a location keyword.
  Other countries work through `loc_query`/keywords but are less precise.

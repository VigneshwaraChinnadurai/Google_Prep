# Job Matcher

Android app that finds jobs at a hand-picked list of companies and keeps only the ones
that match the resume at **≥80%** (configurable). All LLM work goes through Claude by
**manual copy-paste**, with no API key, the same flow as LeetCode Checker's "Claude (Manual)"
provider.

## How it works

```
Careers APIs ──► location + title gate ──► on-device pre-filter ──► Claude scoring ──► Matches (≥ threshold)
(Greenhouse, Lever,     (Settings)            (local score ≥ 50)     (copy/paste,          │
 Ashby, Workday,                                                       12 jobs/prompt)      ▼
 Eightfold, Oracle,
 SmartRecruiters, Amazon)                                                                              Tracker + tailoring
Companies with no API ──► Claude web-search prompt ──► pasted back, already scored ──┘
```

1. **Discover → ① Fetch now** pulls live postings. This also runs daily (default 07:00) and
   notifies you when new jobs clear the pre-filter.
2. **② Score the shortlist**: *Copy next 12* opens Claude with the prompt. Send it, copy
   Claude's whole reply, then tap *Paste Claude reply*. Repeat until the shortlist is empty.
3. **③ Claude web search** covers companies without a public API (e.g. Google). Turn on web
   search in Claude first.
4. **Matches** lists everything Claude scored at or above the threshold. Tap a job to see the
   reasons and gaps, set its status (Saved → Applied → Interviewing → Offer/Rejected), keep
   notes, and generate a **tailored application kit**: resume bullets, cover letter, gap plan
   and a referral message.
5. **Setup → Profile → Profile analysis** rebuilds the skill profile from your resume.
   Do this after importing a new resume PDF.

## Adding a company

| Source | Identifier to enter | Where to find it |
|---|---|---|
| Greenhouse | board token | `boards.greenhouse.io/<token>` or `job-boards.greenhouse.io/<token>` |
| Lever | company slug | `jobs.lever.co/<slug>` |
| Ashby | org slug | `jobs.ashbyhq.com/<slug>` |
| Workday | full careers URL | e.g. `https://nvidia.wd5.myworkdayjobs.com/NVIDIAExternalCareerSite` |
| Amazon Jobs | nothing | uses amazon.jobs search, filtered to India |
| Eightfold | careers host + `?domain=` | e.g. `https://apply.careers.microsoft.com?domain=microsoft.com` |
| Oracle HCM | candidate-experience site URL | e.g. `https://jpmc.fa.oraclecloud.com/hcmUI/CandidateExperience/en/sites/CX_1001` |
| SmartRecruiters | company id | `jobs.smartrecruiters.com/<id>` |
| Claude web search | careers page URL (optional) | anything without a public API: Google, Microsoft, Meta, … |

Use **Test fetch** on the company card to confirm the identifier works.

Each company has a **priority**: ⭐ Top choice / 🎯 Target / Backup. Top-choice matches
(Google) sort first in Matches. In Claude web search, a top-choice company gets its own,
more thorough prompt once a day; the remaining companies rotate in batches of 4
(configurable), least recently searched first.

### Bundled list (seed v2, verified against the live APIs on 2026-10-08)

| Source | Companies |
|---|---|
| Claude web search | ⭐ Google, Apple, Meta, Tesla, Uber, LinkedIn, Atlassian, Walmart, 7-Eleven, Visa, Goldman Sachs, Wells Fargo, Flipkart, Myntra |
| Workday | Adobe, Target, PayPal, VMware (Broadcom), NVIDIA, Nike |
| Eightfold | Microsoft, Qualcomm, Morgan Stanley |
| Oracle HCM | JPMorgan Chase |
| SmartRecruiters | Freshworks |
| Amazon Jobs | Amazon |

## Build

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew assembleDebug                         # app/build/outputs/apk/debug/app-debug.apk
.\gradlew testDebugUnitTest                     # parser / scorer / Claude-reply tests
.\gradlew testDebugUnitTest -PliveApiTests=1    # also hits the real careers APIs
```

Signing reuses LeetCode Checker's permanent keystore. `keystore.properties` (gitignored)
points at `../../leetcode_checker/app/release-keystore.jks`; see `keystore.properties.example`.

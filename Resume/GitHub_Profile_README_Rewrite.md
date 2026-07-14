# GitHub Profile README — Rewrite

Drafted 2026-07-14, **pushed live the same day.** Replaced the generic
"AI Enthusiast" / gif-badges / buy-me-a-coffee template. Old version backed
up at `Resume/GitHub_Profile_README_OLD_BACKUP.md`.

**Status as of 2026-07-14, end of day: fully live, no remaining gaps.**
All three flagship projects now exist as real, standalone, documented public
repos and are linked from the profile README:
- [multi-agent-strategic-analysis](https://github.com/VigneshwaraChinnadurai/multi-agent-strategic-analysis)
- [Quantum_Computing](https://github.com/VigneshwaraChinnadurai/Quantum_Computing) (pushed into the existing repo name, replacing a one-line placeholder)
- [leetcode-companion](https://github.com/VigneshwaraChinnadurai/leetcode-companion)

One manual step still on you (can't be done via API without credential
access I'm not going to extract): **pin these repos** — GitHub Settings →
your profile → Customize pins. Recommended six: the three above,
`Google_Prep`, `resume`, and one more existing strong repo
(`DiT-Document-Image-Transformers-` is a reasonable pick).

---

## Bio field (GitHub Settings → Profile, separate from the README, 160 char limit)

Current: `AI Enthusiast` — replace with:

```
Senior AI Architect | Agentic AI, GenAI & LLMs on AWS | Building production RAG + multi-agent systems
```

---

## README content

```markdown
### Vigneshwara Chinnadurai

Senior AI Architect building production agentic AI and GenAI systems on
AWS Bedrock — hybrid RAG, multi-agent orchestration, LLM-powered
self-critique loops. 8+ years, Fortune 500 clients (Disney, ESPN, Toyota,
HP), $270M+ in measured business impact across those engagements.

**Currently:** deep in Google interview prep — [tracking it daily](https://github.com/VigneshwaraChinnadurai/Google_Prep),
DSA through a from-first-principles roadmap alongside continued production
GenAI work.

---

#### What I've actually built

- **[Multi-Agent Strategic Analysis Engine](https://github.com/VigneshwaraChinnadurai/multi-agent-strategic-analysis)** —
  self-directed research system: LLM-planned task decomposition, hybrid BM25 +
  dense retrieval with reciprocal rank fusion, self-critique refinement loop,
  runtime code synthesis.
- **[Quantum Portfolio Optimization](https://github.com/VigneshwaraChinnadurai/Quantum_Computing)** —
  QUBO formulation from first principles, benchmarked head-to-head against
  classical max-Sharpe on the same data, with optional execution on D-Wave
  Leap's QPU.
- **[LeetCode AI Companion](https://github.com/VigneshwaraChinnadurai/leetcode-companion)** —
  Android app (Kotlin/Jetpack Compose): Gemini-generated solutions, one-tap
  GitHub export, calendar-integrated streak tracking.
- **[Google_Prep](https://github.com/VigneshwaraChinnadurai/Google_Prep)** —
  the actual day-by-day interview prep grind, public, updated daily.

#### Stack

Python · PyTorch · TensorFlow · PySpark · AWS (Bedrock, SageMaker, Lambda,
Kinesis) · Kotlin · SQL

#### Elsewhere

[LinkedIn](https://linkedin.com/in/vigneshwarac) · [Resume](https://github.com/VigneshwaraChinnadurai/resume) · [Credly](https://credly.com/users/vigneshwarachinnadurai)
```

---

## What changed from the current version, and why

- **Cut:** gifs, "Buy Me a Coffee" button, "I love exploring new
  technologies 💻" / "Reading, writing & watching Tech Stuff" bullet lists,
  generic emoji-bullet "Personal Stuff" section. None of this differentiates
  you from any other bootcamp-era GitHub template — it actively undersells
  someone with $270M+ in real production impact.
- **Cut:** GitHub stats/streak widgets (`github-readme-stats`,
  `streak-stats`) — these read as filler on a profile this strong; the
  actual project list and the live `Google_Prep` commit history already
  demonstrate consistency better than a decorative widget does.
- **Added:** the two flagship personal R&D projects front and center, since
  they're currently invisible on your public GitHub entirely (see below).
- **Added:** explicit mention that you're mid-prep, publicly — unlike
  LinkedIn, this is a normal and often *positive* signal on GitHub (visible
  daily-commit discipline reads as work ethic here, not desperation).
- **Kept:** Credly and resume-repo links; kept it short — a wall of text
  undersells the same way gifs did.

---

## Before this goes live: two real gaps to close first

1. **The flagship projects need their own real, public, documented repos.**
   Right now they only exist inside `Google_Prep/agentic_ai/` and
   `Google_Prep/quantum_modelling/` as subfolders — not browsable,
   pinnable, or linkable on their own. To actually use the README above:
   - Extract each into its own repo (`multi-agent-strategic-analysis`,
     `quantum-portfolio-optimization`, or similar names)
   - Each needs a real README (what it does, how to run it, key design
     decisions) — a recruiter or interviewer who clicks through and finds
     an undocumented folder of scripts will conclude less than if they'd
     found nothing
   - Same for the LeetCode AI Companion Android app currently under
     `mobile_apps/leetcode_checker`
   This is real, separate work — say the word if you want help extracting
   and writing READMEs for these; I didn't do it unprompted since it
   involves decisions about what exactly to expose publicly from each.

2. **Pin the right 6 repos** (GitHub Settings → your profile → Customize
   pins) once the above exist: the three flagship projects, `Google_Prep`,
   `resume`, and one more strong existing repo (`DiT-Document-Image-Transformers-`
   or `Quantum_Computing` are reasonable candidates from what's already public).

---

## To actually publish this

I can push this README directly to your `VigneshwaraChinnadurai/VigneshwaraChinnadurai`
repo — but that's your live public profile page, a more consequential,
externally-visible action than anything else I've pushed this session.
Confirm and I'll do it; otherwise copy the content block above into that
repo's `README.md` yourself whenever you're ready. The bio field change
needs to happen manually either way (Settings, not a repo).

---

## Maintenance — going forward

- Update the project list here the moment a new personal R&D project is
  worth showing — stale "coming soon" links are worse than no link.
- Keep `Google_Prep`'s visibility intentional: it's a genuine positive
  signal as a public repo (see the confidentiality note from earlier in
  this conversation) as long as you're comfortable with its current level
  of client-engagement detail staying public going forward.
- Re-pin repos whenever the six best ones change — don't let this go stale
  the way the current stats-widget version did.

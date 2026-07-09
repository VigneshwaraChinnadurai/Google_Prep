# Google Prep — Session Log

Append-only log of completed study sessions against `Google_Roadmap_v2.md`. One entry per completed day.

<!-- Format:
## Day N — YYYY-MM-DD — Phase name
**Start:** HH:MM  **End:** HH:MM  **Duration:** Xh Ym
**Topic:** ...
**Wins:** ...
**Struggles / added to spaced review:** ...
**Behavioral micro-rep:** ...
**Mock verdict (if applicable):** Strong Hire / Hire / Leaning Hire / Leaning No Hire / No Hire — one-line reasoning
-->

## Day 1 — 2026-07-09 — Phase 0 - Foundations

**Start:** 10:39 IST  **End:** 12:25 IST  **Duration:** 1h 46m
**Topic:** How a CPU executes instructions; stack vs. heap memory (deliverable: explain out loud why recursion has a call-stack limit)
**Wins:** Built the full explanation from scratch via Socratic back-and-forth — frames pushed/popped, stack vs. heap, why loops don't hit the same wall as naive recursion. Final synthesis was correct and unprompted. Also correctly solved warm-up #3689 (Maximum Total Subarray Value I) with a clean, fully-argued O(n) solution on the first attempt.
**Struggles / added to spaced review:** (1) Initial misconception that a callee's locals are "shared" with the caller's — corrected via live demo (`frames_demo.py`). (2) Imprecise mental model of frame cleanup as "overwriting" rather than "popping" (pointer moves, memory not immediately touched). (3) Wrongly assumed an iterative rewrite would hit the same stack wall as recursion — corrected via live comparison at n=3000 (`wall_compare.py`). (4) On warm-up #1967, first attempt deduped `patterns` with `set()` before counting, silently breaking the duplicate-counting requirement (caught before reveal). All four → into spaced review via `Leetcode_QA_Revision` cadence.
**Behavioral micro-rep:** Disney TVA latency-reduction story drafted, missing the Result number on first pass, tightened to full STAR — logged in `prep_pathway/mock interviews/Question and answers in STAR pattern.md`.
**Mock verdict (if applicable):** N/A — no mock round today.

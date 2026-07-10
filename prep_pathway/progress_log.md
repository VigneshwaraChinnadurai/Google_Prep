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

## Day 2 — 2026-07-10 — Phase 0 - Foundations

**Start:** 22:03 IST  **End:** 23:34 IST  **Duration:** 1h 31m
**Topic:** Compilation vs. interpretation; Python bytecode & the PVM (deliverable: read and explain the bytecode of a simple loop)
**Wins:** Correctly derived that CPython is a hybrid (compiles to bytecode, then the PVM interprets it) rather than pure line-by-line interpretation. Read and explained real `dis.dis()` output unaided — both a loop (`FOR_ITER`/`JUMP_BACKWARD`) and, going beyond today's minimum, a conditional (`COMPARE_OP`/`POP_JUMP_IF_TRUE`) — on his own `concatenate` function, correctly down to individual opcodes. Independently reasoned that a 100M-iteration loop uses flat stack space (proven live), correctly distinguishing stack-flat/time-scales from heap-growth-if-you-accumulate. Fixed #3754 fully (missing `x` concatenation, then the `n=0` edge case) across two passes. Deferred #3691 (Hard, Segment Tree/Heap — beyond current curriculum) into `deferred_items`, resurfacing Day 100+, instead of forcing or dropping it.
**Struggles / added to spaced review:** (1) Initially conflated "compiled" with "syntax-checked-then-executed," missing that compilation produces a standalone artifact executed separately/later. (2) Said the PVM "converts bytecode to machine code" — actually JIT compilation (what PyPy does), not what CPython does; CPython's PVM directly carries out bytecode via pre-compiled C routines, generating no new machine code at runtime. (3) On the `if i != '0':` bytecode, misread the compared constant as the int `0` rather than the string `'0'` — meaningful because `i` is always a string from `str(num)` iteration. (4) Described `POP_JUMP_IF_TRUE`'s false-path as a second jump target rather than fall-through-to-the-next-instruction. All four → spaced review.
**Behavioral micro-rep:** Toyota $46.1M forecasting story — first pass finally included the Result number unprompted (real progress from Day 1), but used "we" throughout and a generic Action; corrected to "I" + the actual per-SKU ensemble-model differentiator. Surfaced a real resume gap in the process — the "5 PCs / 15 PDCs" split wasn't in the resume text at all — confirmed accurate and added to `Resume/resume.tex` and `Resume/resume_v2.tex`. Logged in the STAR bank.
**Mock verdict (if applicable):** N/A — no mock round today.

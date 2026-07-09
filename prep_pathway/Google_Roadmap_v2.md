# Google SWE (Machine Learning) — Master Prep Roadmap v2
**One goal, no fallback: Google SDE / Software Engineer, Machine Learning offer.**
Bible status: this document is designed to be followed without re-litigation. Read the "ground truth" section once, then execute day by day.

---

## 0. Ground Truth — What Google Actually Tests (verified July 2026)

Confirmed from current interview-process reporting (sources at bottom):

- **Onsite loop = 5–6 rounds**, ~45–60 min each: 1–2 **coding/DSA** rounds, 1 **general system design** round, 1 **ML system design** round, 1 **Googleyness & Leadership** (behavioral) round. Some loops add a specialized ML-theory round.
- **The single biggest predictor of failure is not weak ML theory — it's underestimating how heavily the process tests pure coding and algorithms, calibrated to the same bar as Google's general SWE interviews.** A "Software Engineer, Machine Learning" candidate who is strong in ML but shaky on DSA fails the same way a generalist SWE candidate does. This is why DSA gets the largest time allocation in this plan, not ML.
- **Googleyness & Leadership** is scored on emergent leadership (stepping up without formal authority), intellectual humility, comfort with ambiguity, and bias to action — standard STAR format, 4–5 questions in one session.
- **2026 update:** a pilot format now folds a technical deep-dive on one of *your own past projects* into the Googleyness round for some loops — engineering judgment questions layered onto behavioral ones. You should be able to whiteboard your own resume projects, not just recite them.
- **2026 pilot (select US teams, junior/mid roles only):** one coding round may be replaced by a **code-comprehension** round (reading/debugging existing code instead of writing from scratch). Not universal — worth light exposure, not a redesign of this plan.
- Every interviewer submits independent written feedback; a **hiring committee that never met you** makes the call. This means consistency across rounds matters more than any single brilliant answer — no round can be phoned in.

**How this changes your instinct:** you came in wanting to start from "how programming languages work" and go deep on CS internals. That instinct is good discipline but low interview-yield if it becomes a rabbit hole — Google's own data says coding fluency + pattern recognition across ~10-12 DSA topics is what actually gates you, not CPython internals trivia. This plan keeps a foundations phase (you should understand what you're doing, not pattern-match blindly) but bounds it to 5 days, then spends the freed time on DSA depth and mock-interview reps, which is where offers are actually won or lost.

---

## 1. Program Overview

| Phase | Days | Calendar (approx, 6 active days/wk) | Goal |
|---|---|---|---|
| 0. Foundations | 1–5 | Week 1 | Understand execution model deeply enough to reason correctly, then stop |
| 1. Python Mastery | 6–15 | Weeks 2–3 | Fluent, idiomatic, fast Python — no syntax friction during problem-solving |
| 2. DSA Deep Mastery | 16–105 | Weeks 4–18 | Pattern-level mastery across every topic Google tests, pseudocode-first |
| 3. System Design (general) | 106–130 | Weeks 19–22 | Design any standard large-scale system confidently |
| 4. ML System Design | 131–155 | Weeks 23–26 | Convert your existing production ML depth into interview-ready design fluency |
| 5. Behavioral / Googleyness | 156–165 | Week 27 | STAR story bank + past-project deep-dive readiness |
| 6. Mock Loop Integration | 166–185 | Weeks 28–31 | Full-loop simulation, weak-spot elimination, go/no-go readiness |

**Total: 185 study days** (3 quality hours each = 555 hours). At 6 active days/week + 1 rest/buffer day, that's **~31 weeks (~7 months)** of calendar time. Starting 2026-07-09, that lands completion around mid-February 2027 — a target, not a deadline. No rush was the instruction; this plan still ends because open-ended prep without a finish line is how "no hurry" quietly becomes "no offer."

Rest days aren't optional padding — they're where spaced-repetition review and life happen. Skipping them to "go faster" will degrade retention, not improve it.

---

## 2. Daily 3-Hour Operating Template (applies every study day, all phases)

| Time | Block | Purpose |
|---|---|---|
| 0:00–0:15 | **Spaced-review warm-up** | Revisit problems/concepts due today per the spaced-repetition schedule (§3). Log outcome in `Leetcode_QA_Revision`. |
| 0:15–1:45 | **New material — theory then pseudocode then code** | Learn the day's topic conceptually first, write the algorithm in pseudocode/plain English, *then* implement in Python. Never skip straight to typing code. |
| 1:45–2:30 | **Independent timed practice** | Solve problems on today's topic solo, under a timer, no hints. This is what builds interview performance, not just knowledge. |
| 2:30–2:55 | **Review & journal** | For anything you got wrong or slow on: write *why*, not just the fix. Add it to the spaced-rep queue. |
| 2:55–3:00 | **Behavioral micro-rep** | One STAR story sentence, one "explain a past decision" rehearsal line. Small and constant beats a single cram week. |

---

## 3. Spaced Repetition System (your existing habit, formalized)

You already have the right instinct in `Leetcode_QA_Revision` — daily commits. Formalize it:

- Every problem solved gets logged with date solved.
- Re-attempt (not re-read — re-*solve*) at **+3 days, +10 days, +30 days**.
- If a re-attempt fails, it resets to +3 days from that point — it is not "done," it goes back in the queue.
- Weekly rhythm: **Day 6 of each week = mixed timed set** pulling from that week's new topics *and* whatever's due for spaced review that day. This is your recurring mock-lite.
- Once Phase 2 (DSA) ends, spaced review continues in the background for the rest of the program — old topics don't get to go stale while you're deep in system design.

---

## Phase 0 — Foundations: How Programs & Python Actually Execute (Days 1–5)

Bounded intentionally. Enough to reason correctly about correctness and complexity; not a CS-degree detour.

| Day | Topic | Resource | Practical Output |
|---|---|---|---|
| 1 | How a CPU executes instructions; stack vs. heap memory | CS:APP Ch.1 (skim, not cover-to-cover) + 30 min on godbolt.org compiling simple C/Python-adjacent snippets | Explain, out loud, why recursion has a call-stack limit |
| 2 | Compilation vs. interpretation; Python bytecode & the PVM | Python `dis` module — run `dis.dis()` on 5 of your own functions | Read and explain the bytecode of a simple loop |
| 3 | Python's memory model — mutability, reference counting, `is` vs `==` | Official CPython devguide (GC section only) | Predict output of 5 mutable-default-arg gotchas without running them |
| 4 | The GIL — what it is, why, threading vs. multiprocessing impact | David Beazley — "Understanding the Python GIL" | One paragraph: when would you reach for `multiprocessing` over `threading` in Python and why |
| 5 | Big-O foundations + Python built-in complexity cheat sheet | Build your own cheat sheet: list/dict/set/tuple operation complexities | Memorized table you can recite cold — this underpins every DSA day that follows |

---

## Phase 1 — Python Mastery for Interviews (Days 6–15)

Goal: zero syntax friction. In an interview, fighting the language costs you the problem.

| Day | Topic | Practical Output |
|---|---|---|
| 6 | Control flow, unpacking, slicing tricks | Rewrite 5 verbose snippets idiomatically |
| 7 | list/tuple/dict/set — deep operational fluency | Timed drill: implement each core operation from memory |
| 8 | `collections` — deque, Counter, defaultdict, namedtuple | Solve 3 problems where the right structure halves the code |
| 9 | `heapq` + `bisect` | Implement a max-heap via negation; implement binary search via bisect_left/right by hand once, then use the module |
| 10 | `itertools` + `functools` (incl. `lru_cache`) | Rewrite a brute-force solution using `lru_cache` memoization |
| 11 | OOP for design problems — classes, `__eq__`, `__hash__`, `__lt__` | Build a custom comparator class for a heap-based problem |
| 12 | Pitfalls — mutable default args, shallow/deep copy, closures, string immutability cost | Write down 5 bugs you've personally hit or would now avoid |
| 13 | Writing interview-clean code — style, edge cases, naming under time pressure | Solve 3 problems narrating out loud as you type |
| 14 | Timed fluency drill | 10 easy problems, pure typing/translation speed, no new concepts |
| 15 | Review & gap-fill | Redo anything from Days 6–14 that felt slow |

---

## Phase 2 — DSA Deep Mastery (Days 16–105, 90 days)

This is the core of the program, sized correctly per the ground-truth note above. Every topic: pseudocode first, then Python. Resources are trimmed from your original roadmap to the highest-yield ones only.

| Days | Topic Block | Core Resources | Target |
|---|---|---|---|
| 16–20 | Complexity, recursion, bit manipulation | MIT 6.006 recursion lectures; CP-Algorithms bitmask section | Comfortable deriving Big-O for recursive solutions unaided |
| 21–28 | Arrays & Strings — two pointers, sliding window, prefix sums, sorting | NeetCode.io patterns; CP-Algorithms string processing (Z-function, Rabin-Karp) | 25+ problems, all four patterns recognizable on sight |
| 29–32 | Hashing — maps/sets, design problems | LeetCode Explore: Hashing | 12+ problems incl. 2 design-a-data-structure problems |
| 33–37 | Linked Lists | LeetCode Explore: Linked List pointer manipulation | Reverse/merge/cycle-detect from memory, no reference |
| 38–43 | Stacks, Queues, Monotonic Stack/Queue | Kuo's monotonic stack template (LeetCode Discuss); WilliamFiset stack/queue basics | 15+ problems incl. next-greater-element family |
| 44–51 | Trees & BSTs | MIT 6.006 trees; Skiena (Algorithm Design Manual) balancing concepts | All traversal variants + BST construction/validation cold |
| 52–56 | Heaps / Priority Queues | MIT 6.006 Lecture 4 | Top-K family + merge-K-lists pattern |
| 57–59 | Tries | CP-Algorithms Trie section | Word search / autocomplete-style problem solved unaided |
| 60–71 | Graphs — BFS/DFS, topological sort, Union-Find, Dijkstra, Bellman-Ford | WilliamFiset Graph Theory Tutorial; CP-Algorithms Union-Find w/ path compression; MIT 6.046J advanced graphs | 30+ problems across all sub-patterns |
| 72–89 | Dynamic Programming — 1D/2D, knapsack family, interval DP, DP on trees/graphs | MIT 6.006 DP lectures; Aditya Verma DP playlist; AtCoder Educational DP Contest | 40+ problems; can state the recurrence before coding, every time |
| 90–93 | Greedy | NeetCode greedy set | 12+ problems w/ written proof-sketch of why greedy holds |
| 94–99 | Backtracking | Classic template drilling (subsets/permutations/combinations/N-Queens family) | 15+ problems |
| 100–105 | Advanced — segment/Fenwick trees, KMP/Z/Rabin-Karp, sweep line | CP-Algorithms advanced structures | Exposure-level competence; recognize when to reach for these, not necessarily blazing speed |

**Rule for this whole phase:** never move to a new topic block with unresolved failures in the current one carried silently — log them into spaced review (§3) and move on; the +3/+10/+30 cycle is what closes the gap, not re-reading.

---

## Phase 3 — System Design, General (Days 106–130)

| Days | Focus | Resources |
|---|---|---|
| 106–110 | Scalability fundamentals — CAP/PACELC, load balancing, caching, CDN/DNS | *Designing Data-Intensive Applications* (Kleppmann) Ch.5–6; Alex Xu, *System Design Interview* |
| 111–115 | Data layer — SQL vs NoSQL, indexing, replication, partitioning, consistent hashing | DDIA Ch.3; Discord Eng Blog (consistent hashing at scale) |
| 116–119 | Messaging & infra — queues, pub/sub, rate limiting, API gateways | Stripe Eng Blog (API rate limiters); NGINX architecture docs |
| 120–122 | Case studies I | Design: URL shortener, rate limiter, distributed key-value store |
| 123–125 | Case studies II | Design: chat system, news-feed/timeline (Twitter-style) |
| 126–128 | Case studies III | Design: distributed cache, search autocomplete, web crawler |
| 129–130 | Mock design + review | Full 45-min mock, self- or peer-graded against a rubric |

---

## Phase 4 — ML System Design (Days 131–155)

You already have production depth here (Bedrock/AgentCore, hybrid RAG, forecasting at Toyota scale) — this phase is mostly **converting existing expertise into interview-framed answers**, not learning from zero. Move faster where your resume already proves the muscle.

| Days | Focus | Resources |
|---|---|---|
| 131–134 | Applied ML fundamentals refresh — bias/variance, regularization, eval metrics, feature engineering | Quick refresher pass, not first-principles relearning |
| 135–137 | Deep learning refresh — training dynamics, optimization, distributed training | Horovod paper (Ring AllReduce); PyTorch Distributed docs |
| 138–141 | ML infra — training/serving pipelines, feature stores, monitoring/drift, A/B testing | *Designing Machine Learning Systems* (Chip Huyen) — deployment chapter |
| 142–144 | Google's own ML papers | Covington et al. (YouTube DNN reco); Cheng et al. (Wide & Deep); He et al. (Facebook CTR, for contrast) |
| 145–150 | Case studies | Design: YouTube-style recommender, ad click-prediction, spam/abuse classifier, search ranking |
| 151–153 | LLM/GenAI systems (your strongest ground) | Light refresher + reframe your Disney/ESPN work as an interview answer, not a resume bullet |
| 154–155 | Mock ML system design + review | Full 45-min mock |

---

## Phase 5 — Behavioral / Googleyness & Past-Project Narrative (Days 156–165)

Directly targets the confirmed 2026 format: STAR behavioral **plus** a technical deep-dive on a real past project.

| Days | Focus |
|---|---|
| 156–158 | Draft STAR stories from every major resume bullet (Disney PGT parser, TVA chatbot, ESPN highlight platform, HP Genie platform, Toyota forecasting, Halliburton, Morningstar, Wipro Holmes, Huawei, Shell) |
| 159–160 | Past-project deep-dive prep — be ready to whiteboard one of these end-to-end: architecture, alternatives considered, what you'd change now |
| 161–162 | Leadership/ambiguity/conflict/failure stories specifically — the "emergent leadership" dimension Google explicitly scores |
| 163–164 | Deliver every story out loud, timed, tightened to STAR structure |
| 165 | Mock Googleyness & Leadership round |

---

## Phase 6 — Full Mock Loop Integration (Days 166–185)

| Days | Focus |
|---|---|
| 166–170 | Full mock loop #1 (2 coding + system design + ML design + behavioral) → debrief, identify weakest round |
| 171–175 | Targeted remediation on that weakest round only |
| 176–180 | Full mock loop #2 → debrief |
| 181–183 | Final remediation + resume/story polish |
| 184–185 | Full mock loop #3 (dress rehearsal) → honest go/no-go self-assessment |

---

## 4. Milestone Checkpoints

- **End of Week 6 (~Day 40):** timed mixed set, arrays through stacks/queues — target 80%+ solved within time.
- **End of Week 12 (~Day 80):** timed mixed set across all DSA topics through DP — this is the real test of Phase 2.
- **End of Phase 2 (Day 105):** full DSA self-audit — any topic below your bar goes into an extra remediation week before Phase 3 starts.
- **End of Phase 4 (Day 155):** you should be able to design a Google-scale ML system cold, in 45 minutes, unaided.
- **Day 185:** go/no-go. If mock loop #3 has a shaky round, do not start applying — run one more remediation cycle (extend the timeline; this is exactly the "no hurry" clause in action).

## 5. Maintenance Mode (Day 185 onward, until offer)

Once you start applying: 1 hour/day spaced-review (§3) + 1 mock round/week rotating through all four types, so you don't decay while waiting on recruiter/onsite scheduling (which itself typically runs 6–8 weeks).

---

*v1 (`Google_Roadmap.md`) is superseded by this document. v1's resource list was good but unprioritized and untimed — this version keeps its best sources, cuts the low-yield depth (full CPython Internals book, multiple redundant OS/concurrency courses), and adds the daily structure, spaced-repetition system, and behavioral/past-project layer v1 was missing entirely.*

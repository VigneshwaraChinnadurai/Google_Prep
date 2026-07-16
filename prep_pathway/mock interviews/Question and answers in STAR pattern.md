# STAR Question & Answer Bank

Running log of behavioral/Googleyness stories, built up during daily prep sessions
(one behavioral micro-rep per day, per `Google_Roadmap_v2.md` Phase 5) and mock
Googleyness & Leadership rounds. Each entry anchors to a real resume project so
answers stay concrete, not generic.

Target: by the end of Phase 5 (roadmap Days 156-165), every major resume bullet
should have at least one polished entry here, ready to be delivered cold, out
loud, in under ~90 seconds.

Format per entry: the question asked, which resume project it draws on, the
STAR breakdown, and a status flag.

---

## 1. Tell me about a time you significantly improved a system's performance under a technical constraint.

**Resume anchor:** Disney TVA Schema Intelligence Chatbot — memory retrieval latency reduction
**Status:** Refined (drafted 2026-07-09, Day 1)

**Situation:** The Disney TVA chatbot's memory retrieval — pulling conversation history plus vector-DB context for every follow-up — was taking 17+ seconds, too slow for a conversational tool.

**Task:** Cut that latency without losing answer quality on follow-up questions.

**Action:** I classified incoming queries by intent — factual, comparative, or list-based — and used that classification to right-size how much memory and RAG context each query type actually needed, instead of pulling the same amount for every query.

**Result:** Cut memory retrieval latency 70%, from 17+ seconds to under 5.

**Notes:** First draft rambled — described the Action but never stated the Result number, plus filler words ("um", "like") that read as unstructured under pressure. Practice saying this version out loud until it's automatic, not read.

---

## 2. Tell me about a time you replaced an underperforming system with a data-driven approach and measured the business impact.

**Resume anchor:** Toyota Demand Forecasting Engine — $46.1M inventory cost reduction
**Status:** Refined (drafted 2026-07-10, Day 2)

**Situation:** Toyota's demand forecasting was underperforming across an enterprise-scale operation — 1.2 million SKU combinations across 2 Part Centers (PCs) and 15 Part Distribution Centers (PDCs).

**Task:** Improve forecast accuracy enough to actually reduce excess inventory holding costs, not just marginally tune the existing approach.

**Action:** I replaced the traditional averaging approach with an ensemble of models — LSTM, N-BEATS, SVR, and Prophet — selecting the best-fit model per SKU based on that SKU's own demand characteristics, rather than forcing one model across all 1.2 million SKUs.

**Result:** Cut annual inventory costs by $46.1M, hitting 70% WMAPE accuracy across all 17 centers.

**Notes:** First draft used "we" throughout — corrected to "I" for individual-ownership framing, since interviewers explicitly probe this in a consulting background. First draft's Action was generic ("ML backed forecasting analyzing trend/seasonality") — tightened to the actual differentiator (per-SKU ensemble model selection) so it survives a technical follow-up. Result number was included on the first attempt this time, unprompted — real improvement from Day 1's story. The "PCs / PDCs" split was missing from the resume itself; confirmed accurate and added to both `Resume/resume.tex` and `Resume/resume_v2.tex` (local working copies — not yet published to the public resume repo). Initially recorded as 5 PCs on 2026-07-10; corrected to 2 PCs on 2026-07-11 after he cross-checked other sources — worth double-checking numbers like this against something beyond memory before they're this deep in a rehearsed answer.

---

## 3. Tell me about a time you automated a manual process and it led to a significant business outcome.

**Resume anchor:** Disney ESPN Game Summary Platform — $231.76M AWS business expansion
**Status:** Refined (drafted 2026-07-11, Day 3)

**Situation:** Disney/ESPN's game-highlight extraction was a fully manual process, dependent on an external team in real time, with no path to scale beyond one game at a time.

**Task:** Automate the process end-to-end to remove that manual dependency and build something extensible to future sports.

**Action:** I designed and built a 7-step serverless pipeline (MediaLive → Kinesis → MediaPackage → S3 → Nova Premier → DynamoDB → Highlights), including capacity planning based on camera feed count and resolution (4K vs. 1080p) to size the pipeline correctly, and validated it end-to-end as an MVP for NFL (American Football).

**Result:** Validated a ~90% event capture rate on the NFL MVP, which contributed to a $231.76M AWS business expansion and is the basis for a proposed expansion to Cricket, Rugby, and Soccer, pending client validation of the NFL results.

**Notes:** This one needed real fact-checking, not just delivery polish. First draft claimed the platform was already "scaled to 4 leagues" with a 90% capture rate measured across all of them, using "we" throughout, plus an ambiguous "football" (meant Soccer) — but the actual, current state is: only NFL has been built and validated; the other three leagues are a *proposed* expansion still pending client sign-off on the NFL results. This directly matched — and corrected — the resume itself, which had overstated this as already-completed scope. Both `Resume/resume.tex` and `Resume/resume_v2.tex` updated to say "validated on NFL MVP; proposed expansion pending client validation" instead of claiming the 4-league scale-up as done. This is exactly the kind of claim a real interviewer would drill into — better caught now than in a live loop.

---

## 4. Tell me about a time you questioned a process everyone assumed was necessary and changed it despite that being the default way things were done.

**Resume anchor:** Disney ESPN Game Summary Platform — same project as #3, different angle (initiative/ownership rather than automation mechanics)
**Status:** Refined (drafted 2026-07-16, Day 4)

**Situation:** Game-highlight extraction at Disney/ESPN was done by a team physically on the ground during live play — a process nobody was questioning, despite depending on manual human presence and offering no path to scale.

**Task:** I chose to question why this wasn't running on cloud infrastructure, which could scale elastically instead of depending on a manual team's availability, and took it on myself to build the alternative.

**Action:** I designed and built a 7-stage serverless pipeline (MediaLive → Kinesis → Nova Premier → DynamoDB) that runs automatically and scales with load, replacing the manual on-site extraction process.

**Result:** Validated a ~90% event capture rate on the NFL MVP, contributing to a $231.76M AWS business expansion. The pipeline's design also targets cutting per-game human effort from ~30 hours (fully manual) to ~3 hours (validation-only) once running at full scale — a projected efficiency gain, not yet measured, since the pipeline is still in production testing rather than running at full scale. Proposed expansion to Cricket, Rugby, and Soccer is pending client validation of the NFL results.

**Notes:** Two live corrections needed this time, both numbers, not structure. (1) First stated the AWS business-expansion figure as $450M — this conflicts with the already-verified $231.76M from entry #3 (Day 3); confirmed $231.76M is correct, $450M was a misremember. He flagged himself as "bad with numbers" and plans dedicated memorization drilling in Phase 5. (2) First stated the 30hr→3hr labor reduction as an already-achieved result; on questioning, clarified it's a *design target* for full-scale operation that hasn't been measured yet, since the pipeline isn't running at full scale — reframed as projected, not achieved, matching the same validated-vs-proposed honesty pattern already established for this project's league expansion. Also surfaced for the first time: a Deloitte Innovation Award tied to this project — not yet on the resume or LinkedIn draft; worth confirming the exact award name/date and adding, same treatment as the Day 2 Toyota PC/PDC gap. The projected 30hr→3hr figure should stay out of the resume for now (resume claims should be achieved facts, not design targets) but is fair to mention in an interview room *as* a projection if asked what's next.

---

<!-- Next entry template:

## N. <Interview-style question>

**Resume anchor:** <which project/bullet>
**Status:** Draft | Refined | Interview-ready

**Situation:** ...
**Task:** ...
**Action:** ...
**Result:** ... (always end on the number/outcome)

**Notes:** <what needs work, what tripped you up when saying it out loud>

-->

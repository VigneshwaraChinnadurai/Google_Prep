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

**Situation:** Toyota's demand forecasting was underperforming across an enterprise-scale operation — 1.2 million SKU combinations across 5 Part Centers (PCs) and 15 Part Distribution Centers (PDCs).

**Task:** Improve forecast accuracy enough to actually reduce excess inventory holding costs, not just marginally tune the existing approach.

**Action:** I replaced the traditional averaging approach with an ensemble of models — LSTM, N-BEATS, SVR, and Prophet — selecting the best-fit model per SKU based on that SKU's own demand characteristics, rather than forcing one model across all 1.2 million SKUs.

**Result:** Cut annual inventory costs by $46.1M, hitting 70% WMAPE accuracy across all 20 centers.

**Notes:** First draft used "we" throughout — corrected to "I" for individual-ownership framing, since interviewers explicitly probe this in a consulting background. First draft's Action was generic ("ML backed forecasting analyzing trend/seasonality") — tightened to the actual differentiator (per-SKU ensemble model selection) so it survives a technical follow-up. Result number was included on the first attempt this time, unprompted — real improvement from Day 1's story. The "5 PCs / 15 PDCs" split was missing from the resume itself; confirmed accurate and added to both `Resume/resume.tex` and `Resume/resume_v2.tex` (local working copies — not yet published to the public resume repo).

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

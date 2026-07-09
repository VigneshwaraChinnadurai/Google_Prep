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

# LinkedIn Profile Rewrite

Drafted 2026-07-14 from resume content + a partial live snapshot of the current
profile (LinkedIn blocks full unauthenticated scraping, so this is written
fresh rather than edited from what's there). Copy each section directly into
the corresponding LinkedIn field.

**Ground rule applied throughout:** every fact here matches the corrected
resume (`resume.tex` as of 2026-07-14) — including the ESPN platform's real
scope (NFL MVP validated; other leagues proposed, not yet realized). Do not
let this drift from the resume in either direction; see the maintenance
section at the bottom.

**Strategic call made here, worth knowing about:** this does **not** mention
Google or "actively interviewing" anywhere in public-facing text. Naming one
target company publicly reads as presumptuous before you have an offer, and
narrows how recruiters at *other* strong companies perceive you in the
meantime. Use LinkedIn's private "Open to Work" setting (visible only to
recruiters, not on your public profile) if you want that signal without the
public downside — Settings → visible to → "Recruiters only."

---

## Headline (220 char limit)

```
Senior AI Architect & ML Engineer | Agentic AI, GenAI & LLMs on AWS Bedrock/AgentCore | 8+ yrs building production RAG & multi-agent systems
```

Why this shape: keeps your real title (credibility, matches what a background
check will show) while adding "ML Engineer" so you surface in both
"AI Architect" and "Machine Learning Engineer" recruiter searches — those are
genuinely different search terms people use.

---

## About

```
I build production AI systems that ship — not demos.

Over 8 years, I've architected agentic AI and GenAI platforms on AWS Bedrock
(AgentCore SDK, hybrid RAG, multi-agent orchestration) for Fortune 500
clients including Disney and ESPN, and engineered classical ML systems —
an LSTM/N-BEATS/SVR/Prophet ensemble forecasting engine that cut Toyota's
annual inventory costs by $46.1M across 1.2 million SKUs.

What I actually care about is the gap between "works in a notebook" and
"survives production traffic." Recent examples: cutting a conversational
AI's memory-retrieval latency 70% (17s to under 5s) by classifying query
intent before deciding how much context to pull, and eliminating 74% of a
document-parsing pipeline's latency by replacing LLM-based tool routing
with deterministic orchestration — same output quality, none of the
reasoning overhead.

Outside client work, I build things nobody assigned me: a self-directed
multi-agent research system with hybrid BM25+dense retrieval and a
self-critique refinement loop, and — because I like a genuinely hard
problem — a quantum annealing approach to portfolio optimization (QUBO
formulation, D-Wave Leap QPU).

Currently finishing an MBA in Analytics & Data Science (Manipal, 2026) to
pair the technical depth with sharper judgment on which problems are
actually worth solving.

Always glad to connect with people building serious AI systems, not just
talking about them.
```

---

## Experience — bullet rewrites per role

LinkedIn allows more breathing room than a resume; these are slightly more
narrative than the resume's terse XYZ bullets, but every number matches it
exactly.

### Deloitte Consulting USI — Senior AI Architect (Jan 2022 – Present)

```
Architecting agentic AI and GenAI systems on AWS Bedrock for Fortune 500
clients, from document-extraction pipelines to conversational AI to
real-time video analysis.

• Disney PGT Spec Sheet Agent: built an 11-tool deterministic pipeline on
  AWS Bedrock AgentCore, cutting end-to-end latency 5.3x (237s → 44.9s) by
  identifying that 74% of latency was LLM agent reasoning with zero
  decision value, and replacing it with deterministic orchestration.

• Disney TVA Schema Intelligence Chatbot: cut memory-retrieval latency 70%
  (17s → under 5s) by classifying query intent to right-size context
  retrieval, and extended AWS AgentCore SDK with a custom runtime to
  handle S3 Vectors' non-standard metadata pattern.

• Disney ESPN Highlight Generation: built a 7-stage serverless pipeline
  (MediaLive → Kinesis → Nova Premier → DynamoDB) for real-time video
  highlight extraction, validating ~90% event capture rate on an NFL MVP —
  contributing to a $231.76M AWS business expansion, with proposed
  expansion to additional leagues pending further client validation.

• Toyota Demand Forecasting: replaced a traditional averaging approach
  with an ensemble of models (LSTM, N-BEATS, SVR, Prophet) selected per
  SKU based on individual demand characteristics — cutting annual
  inventory costs $46.1M across 1.2M SKU combinations, 70% WMAPE.

• HP Enterprise: replaced executive BI dashboards with an NL-to-SQL
  conversational interface using Databricks Genie over a 13-table
  semantic layer.
```

### WIPRO Technologies — Senior Software Engineer, ML (Feb 2018 – Jan 2022)

```
• WIPRO Holmes (Contract Review R&D): improved legal clause extraction
  accuracy from 76% to 93% using Bi-LSTM + CRF sequence labeling with
  character-level embeddings for out-of-vocabulary legal terminology.

• Huawei Video (Recommendation R&D): built real-time recommendation
  serving across 3 pipelines using Deep Interest Network with
  attention-weighted embeddings, served via Redis + Milvus.

• Shell (P&ID Digitization): automated 2D-to-3D schematic conversion for
  engineering drawings using CNN (VGG19 transfer learning) + Hough Circle
  Transform + a custom OCR pipeline.
```

---

## Skills (add/reorder to put these at the top — LinkedIn ranks by
endorsement + recency, but initial order matters for what's visible first)

```
Agentic AI, Large Language Models (LLMs), Retrieval-Augmented Generation (RAG),
AWS Bedrock, Multi-Agent Systems, Machine Learning, Python, Deep Learning,
AWS (Lambda, SageMaker, S3, Kinesis), PyTorch, TensorFlow, PySpark, SQL,
MLOps, Distributed Systems, Docker, Kubernetes, Quantum Computing
```

That last one is a deliberate inclusion — it's an unusual, memorable
differentiator for a recruiter skimming, not filler.

---

## Featured section

Pin, in this order:
1. Resume (PDF, if you're comfortable making a version public — otherwise skip)
2. `github.com/VigneshwaraChinnadurai` (once the GitHub rewrite below is live)
3. Portfolio site (`vigneshwara-portfolio.vercel.app`) — confirm it's current;
   I couldn't audit its content (client-rendered site, not readable by fetch)
4. Medium blog, if any posts are current/relevant — drop it if stale

---

## Certifications section

Already on your live profile: **AI Fluency: Framework & Foundations**
(Anthropic, Mar 2026) and **Claude 101** (Anthropic, Mar 2026) — good, these
weren't on the resume, so I added them there too (`Resume/resume.tex`,
Certifications section) for consistency.

---

## Maintenance — going forward

- **Never let LinkedIn, the resume, and GitHub disagree on a factual claim.**
  The ESPN "scaled to 4 leagues" overstatement we caught and fixed on the
  resume (2026-07-11) is exactly the failure mode to avoid here — if that
  had been copied onto LinkedIn before the correction, a recruiter or
  interviewer cross-referencing both would have found the mismatch. Whenever
  the resume changes a factual claim, mirror it here in the same session,
  not "eventually."
- **Update Experience bullets as new engagements/results land** — don't wait
  for a resume refresh cycle; LinkedIn is cheaper to keep current since
  there's no PDF to regenerate.
- **Add new certifications as soon as earned**, not in a batch later — recency
  on a cert (like the March 2026 Anthropic ones) signals active, current
  learning, which fades in signal value the longer it sits unlisted.
- **Don't post "actively job searching."** If you want visibility, share
  genuine technical content (a specific problem you solved, a writeup of the
  quantum portfolio project, something you learned) — that builds signal
  passively without the narrow-target downside of naming Google publicly.

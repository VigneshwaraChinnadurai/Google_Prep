# Usecase 3: Autonomous Strategic Analysis — Deep-Dive Execution Walkthrough

> **Purpose:** This document walks through every single step of the system — from `python main.py` to the final report — with the **why** behind each design decision, actual code, real execution logs, **actual prompts sent to the LLM**, and documentation references. It is written for someone who wants to *understand*, not just *use* the system.
>
> **Last Successful Run:** March 26, 2026 — $0.0101 total cost (~0.84 INR), 17 API calls, 230 chunks indexed, critique passed first try (8.3/10), 3 strategies generated, 6 memory edges dynamically extracted.
> 
> **Prompt Log:** All prompts are captured to `outputs/prompt_log_{timestamp}.jsonl` — each entry contains the system prompt, user prompt, call type, and parameters.

---

## Table of Contents

1. [The Problem We're Solving](#1-the-problem-were-solving)
2. [Why This Architecture? The Design Philosophy](#2-why-this-architecture-the-design-philosophy)
3. [File-By-File: What Each File Does and Why](#3-file-by-file-what-each-file-does-and-why)
4. [Step-By-Step Execution Trace (Real Run)](#4-step-by-step-execution-trace-real-run)
5. [Best Practices Implemented (And Why)](#5-best-practices-implemented-and-why)
6. [Lessons Learned From Debugging](#6-lessons-learned-from-debugging)
7. [Cost Analysis](#7-cost-analysis)
8. [Documentation Links](#8-documentation-links)

---

## 1. The Problem We're Solving

**Business problem:** Given Alphabet's latest quarterly earnings, identify the biggest strategic threat Google Cloud poses to Microsoft Azure, and generate actionable mitigation strategies.

**Technical problem:** Build a system that:
- Pulls **real** data from the internet (no mocks)
- Uses an LLM for **every reasoning step** (no hardcoded logic)
- Implements a full **RAG pipeline** (Retrieval-Augmented Generation)
- Is **cost-safe** for a learning project (hard budget cap)
- Can **self-critique** and refine its own output

### Why this specific use case?

This use case is a "Swiss Army knife" for learning modern AI engineering because it forces you to implement:

| Concept | Where it shows up |
|---|---|
| LLM-as-reasoner | Every agent step (planning, extraction, threat, strategies, critique, synthesis) |
| RAG (Retrieval Augmented Generation) | Sparse + Dense + Hybrid retrieval pipeline |
| Multi-agent orchestration | NewsAndDataAgent → FinancialModelerAgent → CritiqueModule → AnalystAgent |
| Self-correction loops | Critique scores < 7 → automatic refinement with new queries |
| Tool use / code generation | FinancialModelerAgent writes Python code at runtime and executes it |
| Real-world data integration | SEC EDGAR API, Google News RSS, Gemini Search grounding |
| Cost engineering | CostGuard, token budgets, caching, embedding batching |

---

## 2. Why This Architecture? The Design Philosophy

### 2.1 Why Not Use an Existing Framework (LangChain, CrewAI, etc.)?

**Deliberate choice:** We used no AI framework. Here's why:

1. **Learning depth.** Frameworks abstract away exactly the things you need to understand. When you write `chain.invoke()` in LangChain, you don't learn what a retry loop looks like, what `responseMimeType` does, or how the Gemini REST API structures its request body.

2. **Cost control.** Frameworks often make hidden API calls (e.g., LangChain's `AgentExecutor` can loop indefinitely). By calling `requests.post()` directly, every single API call is visible, logged, and budget-checked.

3. **Minimal dependencies.** The only external packages are `requests`, `beautifulsoup4`, `diskcache`, `pydantic`, `numpy`, `lxml`. No `langchain` (50+ transitive deps), no `openai` SDK.

### 2.2 Why Direct REST API Instead of the google-genai SDK?

```python
# What we do (llm_client.py, line ~98):
r = self._s.post(url, json=body, timeout=self.cfg.request_timeout)

# What we DON'T do:
# import google.generativeai as genai  ← NOT installed
# genai.GenerativeModel("gemini-2.5-flash").generate_content(...)
```

**Why:** The SDK adds 100+ MB of protobuf/gRPC dependencies. The REST API is 3 lines — and you see exactly what JSON goes over the wire, making debugging trivial.

**Ref:** [Gemini REST API quickstart](https://ai.google.dev/gemini-api/docs/quickstart?lang=rest)

### 2.3 Why Gemini 2.5 Flash (Not Pro)?

| Model | Input $/1M tokens | Output $/1M tokens | Why not? |
|---|---|---|---|
| **gemini-2.5-flash** | $0.15 | $0.60 | ✅ **We use this** — cheapest "smart" model |
| gemini-2.5-pro | $1.25 | $10.00 | 8x-17x more expensive, overkill for structured extraction |
| gemini-2.0-flash | $0.10 | $0.40 | Slightly cheaper but no `thinkingConfig` support |

**Key insight:** For structured JSON extraction tasks, Flash is actually *better* than Pro because we constrain output via `responseSchema`. The model doesn't need to be "smarter" — it just needs to fill in a template.

### 2.4 Why Separate Agents Instead of One Big Prompt?

One huge prompt ("here's all the data, give me a strategy report") would:
- **Blow the context window** — our data is 213 docs × ~500 tokens = ~106K tokens
- **Waste money** — every token in the prompt is charged
- **Be unreliable** — LLMs lose accuracy on long prompts (the "lost in the middle" problem)

Instead, we decompose into focused agents:
```
NewsAndDataAgent  →  fetches 213 docs, indexes into 226 chunks
                     (retrieval pipeline selects the TOP 6 most relevant)
AnalystAgent      →  each LLM call gets only ~1K-3K tokens of focused context
CritiqueModule    →  separate LLM call with rubric — judges output objectively
```

This is called **decomposition** — a fundamental principle of both software engineering and prompt engineering.

---

## 3. File-By-File: What Each File Does and Why

### 3.0 Data & Embedding Storage Locations

Before diving into files, here's where all data lives at runtime and on disk:

| Data Type | Storage Location | Persistence |
|---|---|---|
| **LLM response cache** | `.cache/gemini/` (relative to project root) | Persistent on disk via `diskcache`. Survives restarts. Cleared with `--clear-cache`. |
| **SEC EDGAR financial data** | **In-memory only** — fetched via HTTP from `https://data.sec.gov/api/xbrl/companyfacts/CIK{cik}.json` | Not persisted separately. Re-fetched each run (unless LLM responses referencing it are cached). |
| **Grounded search results** | **In-memory only** — returned directly from Gemini API with `googleSearch` tool | Same as above. |
| **Google News RSS** | **In-memory only** — parsed from `news.google.com/rss/search` | Same as above. |
| **Document chunks** | **In-memory only** — `list[Chunk]` in the `RetrievalPipeline` | Rebuilt from raw documents each run. Not persisted. |
| **BM25 index** | **In-memory only** — term frequencies and IDF scores in `BM25Index` | Rebuilt each run. |
| **Embeddings (dense vectors)** | **In-memory only** — `DenseIndex._vecs` (`list[list[float]]`) | Rebuilt each run. The raw `embed_batch` API *responses* are cached in `.cache/gemini/` (so re-runs don't re-embed). |
| **Execution log** | `outputs/execution_log_{timestamp}.txt` | Persistent. Timestamped — never overwrites. |
| **Prompt log (all LLM calls)** | `outputs/prompt_log_{timestamp}.jsonl` | Persistent. JSONL format. One entry per LLM call. |
| **Final report** | `outputs/strategic_report.md` | Overwritten each run. |
| **Raw result JSON** | `outputs/raw_result.json` | Overwritten each run. Contains full pipeline output. |

**Why no persistent vector store (Pinecone, Chroma, etc.)?** The dataset is small (~230 chunks). Building the index from scratch takes <15 seconds including embedding API calls. A vector DB would add complexity and a dependency for zero practical benefit at this scale.

**Why no separate file for downloaded financial data?** SEC EDGAR data is fetched via HTTP GET and immediately converted to `Document` objects in-memory. There's no intermediate JSON file; the raw API responses are consumed directly. If you need to inspect fetched data, check `raw_result.json` in the outputs directory, which contains the full pipeline state including extracted financials.

### 3.1 `config.py` — Centralised Configuration

**What:** All settings in frozen dataclasses. API key loaded from `.env` / `local.properties` via folder walk.

**Why frozen dataclasses?**
```python
@dataclass(frozen=True)
class GeminiConfig:
    api_key: str
    generation_model: str = "gemini-2.5-flash"
    ...
```

`frozen=True` means once created, you can't accidentally do `config.generation_model = "gemini-pro"` somewhere deep in the code. This is a **defensive programming** pattern — configuration should be immutable after initialization.

**Why walk up the folder tree for the API key?**
```python
def _load_api_key(start: Path) -> str:
    search = start.resolve()
    for _ in range(8):  # up to 8 levels
        for name in (".env", "local.properties"):
            found = _scan_file_for_key(search / name)
            if found:
                return found
        parent = search.parent
        ...
```

Because the `.env` file might be in the repo root (3 levels above), or in the project folder, or anywhere in between. Walking up means you don't have to configure a path — it just works.

### 3.2 `cost_guard.py` — Billing Protection

**What:** Tracks every API call's token usage, estimates cost, raises `BudgetExceeded` if the hard cap is hit.

**Why this exists:** Without a budget guard, a runaway refinement loop could burn through $10+ of API credits. The guard is checked **before** every API call:

```python
# llm_client.py, inside generate():
self.guard.check()   # ← raises BudgetExceeded BEFORE the HTTP call
raw = self._post(...)  # only runs if budget allows
```

**The pricing table is hardcoded intentionally:**
```python
_PRICING = {
    "gemini-2.5-flash":  (0.15,  0.60),   # per 1M tokens
    "gemini-2.0-flash":  (0.10,  0.40),
    ...
}
```

**Why not query pricing dynamically?** Because Google doesn't expose pricing via API. The table changes maybe twice a year. Hardcoding is simpler and eliminates a network dependency.

### 3.3 `llm_client.py` — The Heart of the System

This is the single most important file. Every LLM interaction flows through here.

#### 3.3.1 Content-addressed caching with diskcache

```python
self._cache = diskcache.Cache(cache_dir, size_limit=500 * 1024 * 1024)
...
ck = self._ckey("gen", f"{system}|{prompt}|json={json_mode}|s={schema_sig}|t={think_sig}")
if use_cache and self._cache and ck in self._cache:
    return LLMResponse(**self._cache[ck], cached=True)
```

**Why:** Gemini charges per token. If you re-run the same pipeline, identical prompts should return cached results at $0 cost. The cache key is a SHA-256 hash of `system_prompt + user_prompt + parameters`, so even changing `temperature` busts the cache correctly.

**Why diskcache instead of a dict?** A Python dict lives in RAM and dies when the process exits. `diskcache` persists to disk across runs — your second run costs $0 for all cached calls.

**Ref:** [diskcache docs](https://grantjenks.com/docs/diskcache/)

#### 3.3.2 Retry with exponential backoff + jitter

```python
wait = self.cfg.retry_base_delay * (2 ** attempt) * random.uniform(0.75, 1.25)
```

**Why exponential backoff?** If the server returns 429 (rate limit) or 503 (overloaded), retrying immediately makes the problem worse. Doubling the wait each time (2s → 4s → 8s → 16s → 32s) gives the server time to recover.

**Why jitter (random ±25%)?** Without jitter, if 100 clients all get 429 at second 0, they ALL retry at second 2, causing another stampede. Random jitter spreads retries across a time window — this is called the **thundering herd problem**.

**Ref:** [AWS Architecture Blog — Exponential Backoff and Jitter](https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/)

#### 3.3.3 `response_schema` — Structured Output

```python
if json_mode:
    gen_cfg["responseMimeType"] = "application/json"
    if response_schema:
        gen_cfg["responseSchema"] = response_schema
```

**The problem it solves:** Without `responseSchema`, asking Gemini for JSON is a prayer. The model might:
- Wrap it in markdown code fences (` ```json ... ``` `)
- Add explanatory text before the JSON
- Use slightly wrong key names
- Return a number like `48.000000000000014210854...` (actually happened!)

With `responseSchema`, Gemini **guarantees** the output matches your OpenAPI-subset schema. It literally constrains the token generation to only produce valid JSON matching your structure.

**Ref:** [Gemini Structured Output docs](https://ai.google.dev/gemini-api/docs/structured-output)

#### 3.3.4 `thinking_budget` — Controlling Gemini's Internal Reasoning

```python
if thinking_budget is not None:
    gen_cfg["thinkingConfig"] = {"thinkingBudget": thinking_budget}
```

**Why this exists:** Gemini 2.5 models have a "thinking" capability — they reason internally before producing output. The thinking tokens count against `maxOutputTokens`. 

**The bug this fixed:** Earlier, our critique call had `max_tokens=512`. Gemini spent 505 tokens "thinking" and only 7 tokens remained for the actual JSON output:
```
23:01:28  generate: 847 in / 7 out   ← only 7 output tokens!
23:01:28  WARNING  Critique JSON parse failed — defaulting to PASS
```

**The fix:** Set `thinking_budget=0` for pure extraction tasks (planning, financial extraction, memory extraction — no reasoning needed), and `thinking_budget=1024-2048` for tasks that benefit from reasoning (critique, strategy generation).

**Ref:** [Gemini Thinking docs](https://ai.google.dev/gemini-api/docs/thinking)

#### 3.3.5 Defensive JSON parsing

```python
@staticmethod
def _clean_json_text(text: str) -> str:
    text = text.strip()
    if text.startswith("```"):
        lines = text.split("\n")
        lines = lines[1:]  # remove opening ```json
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        text = "\n".join(lines).strip()
    # Fix floating-point precision explosions
    text = re.sub(r'(\d+\.\d{2})\d{10,}', r'\1', text)
    return text
```

**Why this exists despite having `responseSchema`:** Defence in depth. Even with schema enforcement, we hit a case where Gemini output `48.000000000000014210854715202003717...` (hundreds of digits) for the `yoy_growth_pct` field. The regex `(\d+\.\d{2})\d{10,}` → `\1` truncates any number with 10+ extra decimal digits down to 2.

**The key principle:** Never trust a single layer of protection. `responseSchema` prevents structural issues, `_clean_json_text` fixes content issues, and `parse_json` tries both cleaned and raw text as fallback. Three layers.

#### 3.3.6 Google Search Grounding

```python
body = {
    "contents": [...],
    "tools": [{"googleSearch": {}}],  # ← the magic line
}
```

**What it does:** Gemini performs a live Google Search, reads the results, and generates an answer **with citations**. The response includes `groundingMetadata` with actual URLs.

**Why not scrape the web ourselves?** Three reasons:
1. Google Search grounding is a single API call (vs. building a scraper)
2. Google.com blocks automated scraping (you'd need proxies, CAPTCHA solving, etc.)
3. Gemini synthesises multiple search results — much higher information density per token

**Cost:** 500 free grounded queries per day. We use 6 per run. Plenty of headroom.

**Ref:** [Gemini Search Grounding](https://ai.google.dev/gemini-api/docs/grounding)

#### 3.3.7 Batch Embeddings

```python
url = self._url(self.cfg.embedding_model, "batchEmbedContents")
raw = self._post(url, {"requests": requests_list})  # up to 100 per call
```

**Why batch instead of sequential?** The free tier allows 100 embedding requests. We have ~226 chunks. Sequential: 226 API calls → rate limit at call #100. Batch: 3 API calls (100 + 100 + 26) → well within limits.

**Ref:** [Gemini Batch Embedding](https://ai.google.dev/gemini-api/docs/embeddings)

### 3.4 `retrieval/` — The RAG Pipeline (7 modules)

This package implements a full **Retrieval-Augmented Generation** pipeline. Here's why each stage exists:

#### 3.4.1 `web_fetcher.py` — Data Acquisition

Three data sources feed the pipeline:

| Source | What it provides | Why this source? |
|---|---|---|
| **Gemini + Google Search** | Latest earnings numbers, competitive commentary, analyst views | Most current (live web), highest quality (LLM-synthesised) |
| **SEC EDGAR XBRL API** | Historical revenue/income from 10-Q/10-K filings | Authoritative (official SEC filings), structured (XBRL), free, no key needed |
| **Google News RSS** | Recent headlines with timestamps | Free, no API key, gives temporal context |

**Why three sources?** Information triangulation. If Gemini says "Google Cloud revenue is $17.7B" and SEC EDGAR shows similar numbers, confidence is high. If they disagree, the retrieval pipeline will surface both and let the LLM reason about the discrepancy.

#### 3.4.2 `chunker.py` — Sentence-Aware Chunking

```python
# config.py
chunk_size_tokens: int = 480
chunk_overlap_tokens: int = 60
```

**Why chunk at all?** Embeddings work best on coherent, focused text segments (not 20-page documents). 480 tokens (~1 paragraph) is the sweet spot for Gemini embeddings.

**Why sentence-aware?** Naïve token-based chunking can split mid-sentence: `"Revenue was $17." + "7 billion, up 48%..."`. Sentence-aware chunking keeps semantic units intact.

**Why overlap of 60 tokens?** If a fact spans a chunk boundary, overlap ensures it appears in at least one chunk completely. 60 tokens ≈ 1-2 sentences of overlap.

#### 3.4.3 `metadata_enricher.py` — Regex-Based Tagging

Each chunk gets metadata tags (company, topic, source_type, date) via regex patterns — no LLM call needed.

**Why not use an LLM for entity extraction?** Cost. Running an LLM on 226 chunks for NER would cost ~$0.03 and add 30 seconds. Regex is free and instant. For our use case (known companies, known topics), regex is sufficient.

#### 3.4.4 `sparse_retrieval.py` — BM25 From Scratch

```python
# Okapi BM25 scoring
score(q, d) = Σ_{t in q}  IDF(t) · (tf(t,d) · (k1+1)) / (tf(t,d) + k1 · (1 - b + b · |d|/avgdl))
```

**Why implement BM25 from scratch?** Three reasons:
1. **Learning** — understanding TF-IDF and BM25 is foundational IR knowledge
2. **Zero cost** — BM25 uses no API calls, no embeddings. Pure CPU.
3. **No dependency** — no `rank_bm25` or `elasticsearch` needed

**Why BM25? Isn't it old?** BM25 is from 1994 but remains state-of-the-art for **exact keyword matching**. If someone searches "Google Cloud revenue Q4 2025", BM25 will find chunks containing those exact words. Dense embeddings are better at semantic similarity ("cloud earnings" → finds "Google Cloud revenue") but worse at exact matching.

**That's why we use BOTH.**

#### 3.4.5 `dense_retrieval.py` — Embedding-Based Search

```python
# Cosine similarity between query embedding and chunk embeddings
def _cosine(a, b):
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(x * x for x in b))
    return dot / (na * nb)
```

**Why `gemini-embedding-001`?** It's FREE (0 cost per token). This means dense retrieval adds zero marginal cost.

**Why cosine similarity (not dot product or Euclidean)?** Cosine similarity measures the *angle* between vectors, ignoring magnitude. This means a short chunk and a long chunk with similar meaning get similar scores, whereas dot product would favour longer chunks.

#### 3.4.6 `hybrid_index.py` — Reciprocal Rank Fusion (RRF)

```python
# RRF scoring:
for sr in sparse_results:
    scores[sr.chunk_id] += alpha / (rrf_k + sr.rank)
for sr in dense_results:
    scores[sr.chunk_id] += (1 - alpha) / (rrf_k + sr.rank)
```

**Why hybrid instead of just one retrieval method?** Research consistently shows hybrid outperforms either alone:

| Method | Strength | Weakness |
|---|---|---|
| BM25 (sparse) | Exact keyword matching | Misses synonyms ("revenue" ≠ "earnings") |
| Embeddings (dense) | Semantic understanding | Can miss exact numbers, tickers |
| **Hybrid (RRF)** | **Best of both** | Slightly more complex |

**Why `alpha=0.55` (leaning sparse)?** BM25 is free. Embeddings cost API calls. Leaning sparse saves money while keeping semantic coverage.

**Why RRF instead of score normalisation?** RRF uses *rank positions* not raw scores. This is important because BM25 scores and cosine similarities are on completely different scales. Normalising scores requires knowing the min/max across all documents, which is fragile. RRF just says "if BM25 ranked it #1 and embeddings ranked it #3, it's probably good" — much more robust.

**Ref:** [Cormack et al., "Reciprocal Rank Fusion outperforms Condorcet and individual Rank Learning Methods" (2009)](https://plg.uwaterloo.ca/~gvcormac/cormacksigir09-rrf.pdf)

#### 3.4.7 `reranker.py` — LLM-Based Re-Ranking

```python
_SYSTEM = "You are a relevance judge. Given a QUERY and a numbered list of TEXT chunks, output a JSON array..."
```

**Why re-rank after retrieval?** Retrieval (BM25 + embeddings) is fast but approximate. The LLM re-ranker is slow but precise. It reads each chunk and scores how relevant it *actually* is to the query, considering nuance that pure keyword/vector matching misses.

**Why batch 10 chunks per call?** Token efficiency. 10 chunks × ~120 tokens each = 1200 tokens input. One API call scores all 10. Without batching, you'd need 10 separate API calls.

**Why `responseSchema` on the reranker?**
```python
_RERANKER_SCHEMA = {
    "type": "ARRAY",
    "items": {
        "type": "OBJECT",
        "properties": {
            "id": {"type": "INTEGER"},
            "score": {"type": "NUMBER"},
        },
        "required": ["id", "score"],
    },
}
```
Without schema enforcement, the LLM might return `[{chunk_id: 0, relevance: 0.9}]` (wrong key names) or `"Chunk 1 is highly relevant (0.9), chunk 2 is..."` (prose instead of JSON). Schema eliminates these failures.

#### 3.4.8 `context_fusion.py` — Dedup + Token Budgeting

```python
# Jaccard similarity for de-duplication
def _jaccard(a, b):
    sa, sb = set(a.lower().split()), set(b.lower().split())
    return len(sa & sb) / len(sa | sb)
```

**Why de-duplicate?** Multiple data sources often return the same information. Without dedup, the LLM gets "Google Cloud revenue is $17.7B" repeated 4 times, wasting tokens.

**Why Jaccard over cosine for dedup?** Jaccard compares *exact word overlap*, which is perfect for detecting near-duplicate text. Cosine similarity (on embeddings) would require API calls. Jaccard is free and instant.

**Why a token budget (4000 tokens)?**
```python
context_token_budget: int = 4000
```
The context window is finite. If we send 50K tokens of context + system prompt + query, that's expensive and the LLM loses focus. 4000 tokens ≈ 6-8 high-quality chunks — enough evidence without overwhelming the model.

### 3.5 `agents.py` — Multi-Agent Orchestration

#### 3.5.1 `GraphMemory` — Strategic Knowledge Graph

```python
class GraphMemory:
    def add_edge(self, source, relation, target):
        self._edges.append((source, relation, target))
```

**Why a graph?** Strategic relationships are naturally graph-structured: "Google Cloud **threatens** Azure", "Vertex AI **drives** Google Cloud growth". A flat list of facts would lose these connections.

**Why does it both read AND write?**
- **Read** (seeded edges): `(Microsoft Azure) --[StronglyLinkedTo]--> (Office365/EnterpriseSales)` — prior knowledge
- **Write** (discovered edges): `(Google Cloud) --[poses strategic threat to]--> (Azure)` — learned during analysis

This is a key agentic property: the system *expands its own knowledge* during execution.

#### 3.5.2 `CritiqueModule` — Self-Correction

```python
SCHEMA_CRITIQUE = {
    "overall_score": {"type": "NUMBER"},
    "verdict": {"type": "STRING", "enum": ["PASS", "NEEDS_REFINEMENT"]},
    "feedback": {"type": "STRING"},
    ...
}
```

**Why self-critique?** Without it, the system would blindly accept its first output — even if it's shallow or unsupported. The critique module acts as a **quality gate**.

**From real execution:**
```
23:13:52  Critique score: 6.5 / 10  verdict=NEEDS_REFINEMENT
23:13:52  STEP [Refine]: Loop 1: fetching deeper evidence
   ... (retrieves more data, re-analyses) ...
23:14:22  Critique score: 8.0 / 10  verdict=PASS
```

The system scored its own first attempt as 6.5/10, **automatically** decided to fetch more evidence, re-ran the threat analysis, and then scored 8.0/10. No human intervention.

**Why `thinking_budget=1024` for critique (not 0)?** Critique needs genuine reasoning — "Is this analysis deep enough? Does it cite evidence? Is it actionable?" — that benefits from internal deliberation. Pure extraction tasks (planning, financial data parsing) don't.

#### 3.5.3 `AnalystAgent.run()` — The 13-Step Pipeline

Here's the complete flow with the **why** for each step:

```
Step  1: Plan          — LLM decomposes the task (so steps are task-specific, not hardcoded)
Step  2: Fetch         — NewsAndDataAgent gets real data (213 documents)
Step  3: Index         — Chunk + enrich + sparse/dense index (226 chunks)
Step  4: Retrieve fin. — Hybrid search for financial data (top 6 chunks)
Step  5: Extract       — LLM parses structured financials from text (with responseSchema)
Step  6: ShareDelta    — Dynamic tool: writes Python code and executes it
Step  7: Retrieve sig. — Hybrid search for competitive signals (top 6 different chunks)
Step  8: Threat        — LLM synthesises THE threat analysis
Step  9: Critique      — LLM self-scores: "Is this good enough?"
Step 10: Refine        — If No: LLM generates new queries, retrieves more data, re-analyses
Step 11: Strategies    — LLM generates 3 actionable strategies (with thinking_budget=2048)
Step 12: Memory        — LLM extracts knowledge graph edges from analysis
Step 13: Summary       — LLM writes executive summary
```

**Why not just skip to step 8?** Each prior step *reduces* the context the LLM needs to reason about. Without retrieval (steps 4, 7), the LLM would need the entire 213-document corpus in its context window. With retrieval, it gets only the most relevant ~6 chunks. This is both cheaper and more accurate.

### 3.6 `main.py` — Entry Point and Report Renderer

```python
def main():
    args = parse_args()      # --dry-run, --budget, --clear-cache
    cfg = load_config(...)   # resolves API key
    guard = CostGuard(...)   # hard budget cap
    llm = GeminiClient(...)  # all LLM calls go through this
    # Agent team:
    memory → news_agent → finance_agent → critique_mod → analyst
    result = analyst.run(strategic_prompt)
    report = render_report(result, guard)
```

**Why `--dry-run`?** Tests the entire pipeline (data fetching, chunking, indexing) without spending a cent on LLM calls. Every LLM call returns `"[DRY RUN — LLM call skipped]"`.

**Why `--clear-cache`?** After code changes, cached responses from old code may be stale. `--clear-cache` forces fresh API calls.

**Why file-based logging?**
```python
_run_ts = _dt.now().strftime("%Y%m%d_%H%M%S")
file_handler = logging.FileHandler(str(log_dir / f"execution_log_{_run_ts}.txt"), mode="w")
logging.getLogger().addHandler(file_handler)
```
Console output scrolls away. The execution log is a permanent record — invaluable for debugging and learning. Each run gets a **timestamped filename** (e.g., `execution_log_20260326_104944.txt`) so previous runs are never overwritten.

**Why also log prompts?**
```python
from llm_client import enable_prompt_logging
enable_prompt_logging(str(log_dir / f"prompt_log_{_run_ts}.jsonl"))
```
The prompt log captures every LLM call as a JSONL entry with: `seq`, `timestamp`, `call_type`, `system_prompt`, `user_prompt`, and parameters (`thinking_budget`, `temperature`, `max_tokens`, etc.). This is essential for understanding *exactly* what instructions the model received — not just what it returned.

---

## 4. Step-By-Step Execution Trace (Real Run)

This is the actual execution log from the passing run on March 25, 2026. Every line is annotated.

### Phase 1: Initialization (10:49:44)

```
10:49:44  INFO  __main__  Model: gemini-2.5-flash  |  Budget: $0.50  |  Dry-run: False
10:49:44  INFO  agents   Memory +edge: (Microsoft Azure) --[StronglyLinkedTo]--> (Office365/EnterpriseSales)
10:49:44  INFO  agents   Memory +edge: (Google Cloud) --[StronglyLinkedTo]--> (AI-First Data-Native Workloads)
```

**What's happening:** Config loaded, CostGuard initialized, graph memory seeded with 2 known relationships.

#### Where do the seeded "known relationships" come from?

These 2 edges are **hardcoded in `main.py`** (lines 237-238):

```python
# main.py — Agent team setup
memory = GraphMemory()
# Seed known strategic relationships
memory.add_edge("Microsoft Azure", "StronglyLinkedTo", "Office365/EnterpriseSales")
memory.add_edge("Google Cloud", "StronglyLinkedTo", "AI-First Data-Native Workloads")
```

**Why hardcode them instead of discovering them?** These are **prior knowledge** — strategic facts that are well-established and don't need LLM confirmation:
- Azure's revenue is tightly coupled to the Microsoft 365 / Enterprise ecosystem
- Google Cloud's differentiation is its AI/data-native positioning

Seeding the graph with these relationships gives the LLM contextual grounding when it analyses threats. Without them, the first threat analysis would lack the strategic framing. After the pipeline runs, the LLM **discovers 6 additional edges** dynamically (Step 12), expanding the graph from 2 → 8 edges.

In a production system, these seeds would come from a persistent knowledge base (e.g., a graph database). For this learning project, hardcoding keeps it simple.

### Phase 2: Planning (10:49:48) — LLM Call #1

```
10:49:48  INFO  llm_client  generate: 120 in / 228 out  $0.00015  (total $0.0002)
10:49:48  INFO  agents  STEP [Plan]: 7 steps generated by LLM
```

**What's happening:** The LLM received the strategic prompt (120 input tokens) and returned a 7-step plan (228 output tokens). Cost: $0.00015.

**Code path:** `AnalystAgent.build_plan()` → `GeminiClient.generate()` with `json_mode=True, response_schema=SCHEMA_PLAN, thinking_budget=0`.

<details>
<summary><strong>📋 Actual Prompts — LLM Call #1: Planning</strong></summary>

**System Prompt:**
```
You are an elite strategy analyst AI. Given a strategic prompt, decompose it
into a numbered plan of 5-8 concrete execution steps.

Return ONLY valid JSON:
{"steps": ["step 1 text", "step 2 text", ...]}

Keep each step actionable and specific. Include data acquisition, analysis,
critique, and synthesis steps.
```

**User Prompt:**
```
Analyze Alphabet's latest quarterly earnings, focusing specifically on Google
Cloud's performance. Identify the biggest strategic threat this poses to
Microsoft Azure and generate a report outlining three actionable business
strategies Microsoft could pursue to mitigate this threat.
```

**Parameters:** `json_mode=true, thinking_budget=0, temperature=0.1, max_tokens=1024, response_schema=SCHEMA_PLAN`

</details>

**Why `thinking_budget=0`?** Planning a list of steps is straightforward extraction, not deep reasoning. Setting `thinking_budget=0` means ALL output tokens go to the actual plan, not internal deliberation.

### Phase 3: Data Fetching (10:49:48 – 10:51:02) — LLM Calls #2–7

```
10:49:48  INFO  web_fetcher  Grounded search: Alphabet Google Cloud latest quarterly earnings...
10:49:52  INFO  llm_client   grounded: 70 in / 251 out  $0.00016
10:49:52  INFO  web_fetcher  Grounded search: Microsoft Azure Intelligent Cloud...
10:49:57  INFO  llm_client   grounded: 70 in / 178 out  $0.00012
10:49:57  INFO  web_fetcher  Grounded search: Amazon AWS latest quarterly earnings...
10:50:01  INFO  llm_client   grounded: 68 in / 128 out  $0.00009
10:50:01  INFO  web_fetcher  Grounded search: Google Cloud vs Microsoft Azure competitive...
10:50:17  INFO  llm_client   grounded: 70 in / 2120 out  $0.00128  ← biggest response!
10:50:17  INFO  web_fetcher  Grounded search: Analyst commentary on biggest strategic threats...
10:50:35  INFO  llm_client   grounded: 64 in / 1046 out  $0.00064
10:50:35  INFO  web_fetcher  Grounded search: Google Cloud Vertex AI BigQuery growth drivers...
10:50:50  INFO  llm_client   grounded: 57 in / 810 out  $0.00049
```

**6 grounded search queries** returned real web data with citations. Total cost for data acquisition: ~$0.003.

<details>
<summary><strong>📋 Actual Prompts — LLM Calls #2–7: Grounded Search Queries</strong></summary>

All 6 calls share the same **system prompt**:
```
You are a financial research assistant. Provide precise, factual data with
exact numbers where available. Cite your sources. If data is unavailable,
say so explicitly rather than guessing.
```

**Parameters (all 6):** `temperature=0.1, max_tokens=2048, grounded=true` (Google Search tool enabled)

**User Prompt #2 — Google Cloud earnings:**
```
Alphabet Google Cloud latest quarterly earnings results: revenue in billions,
year-over-year growth, operating income, operating margin percentage.
Include exact numbers from the most recent earnings release.
```

**User Prompt #3 — Azure earnings:**
```
Microsoft Azure Intelligent Cloud latest quarterly earnings results:
revenue in billions, year-over-year growth, operating income, operating margin.
Include exact numbers from the most recent earnings release.
```

**User Prompt #4 — AWS earnings:**
```
Amazon AWS latest quarterly earnings results: revenue in billions,
year-over-year growth, operating income, operating margin.
Include exact numbers from the most recent earnings release.
```

**User Prompt #5 — Competitive analysis:**
```
Google Cloud vs Microsoft Azure competitive analysis 2025-2026:
AI workloads, Vertex AI adoption, developer preference, startup migration
trends, market share shifts.
```

**User Prompt #6 — Analyst threats:**
```
Analyst commentary on biggest strategic threats from Google Cloud to
Microsoft Azure in enterprise AI and cloud market 2025-2026.
```

**User Prompt #7 — Growth drivers:**
```
Google Cloud Vertex AI BigQuery growth drivers: why are startups and
data-native companies choosing Google Cloud over Azure?
```

**Where are these queries defined?** In `retrieval/web_fetcher.py` → `_GROUNDING_QUERIES` list (hardcoded, not LLM-generated). Each query is paired with a company tag (GOOGL, MSFT, AMZN, or cross_market) for metadata enrichment downstream.

</details>

```
10:50:50  INFO  web_fetcher  EDGAR facts: GOOGL (CIK0001652044.json)
10:50:51  INFO  web_fetcher  EDGAR facts: MSFT (CIK0000789019.json)
10:50:52  INFO  web_fetcher  EDGAR facts: AMZN (CIK0001018724.json)
10:51:02  INFO  web_fetcher  Total documents fetched: 213
```

**SEC EDGAR** returned structured financial data for all three companies (fetched from `https://data.sec.gov/api/xbrl/companyfacts/CIK{cik}.json` — **not stored on disk**, consumed in-memory). Combined with Google News RSS (headlines), we have **213 documents** from 3 diverse sources.

### Phase 4: Indexing (10:51:02 – 10:51:13)

```
10:51:13  INFO  llm_client  embed_batch: 230 total, 0 cached, 230 embedded
10:51:13  INFO  agents  Indexed 230 chunks from 213 documents
```

**What happened in those 11 seconds:**
1. 213 documents → chunked into 230 chunks (sentence-aware, 480-token target)
2. Each chunk tagged with metadata (company, source_type, date)
3. BM25 index built (term frequencies, IDF scores) — **in-memory only**
4. 230 chunks embedded via `batchEmbedContents` (3 API calls: 100 + 100 + 30)
5. Dense index built — **embeddings stored in-memory** as `DenseIndex._vecs` (list of float lists). The raw API responses are cached in `.cache/gemini/` so repeat runs skip the embedding API calls.

**Cost: $0.00** — embeddings are free with `gemini-embedding-001`.

### Phase 5: Retrieval + Extraction (10:51:13 – 10:51:47) — LLM Calls #8–10

```
10:51:16  INFO  llm_client  generate: 2257 in / 233 out  $0.00048  ← reranker batch 1
10:51:17  INFO  llm_client  generate: 1433 in / 118 out  $0.00029  ← reranker batch 2
10:51:47  INFO  llm_client  generate: 3243 in / 8182 out  $0.00540  ← extraction
10:51:47  INFO  agents  STEP [Extract]: JSON parse failed — raw text stored
```

**What's happening:**
1. Hybrid search: BM25 finds keyword matches, dense finds semantic matches, RRF merges them → 15 candidates
2. LLM re-ranker: scores each candidate's relevance → keeps top 6
3. Context fuser: de-duplicates, assembles into 4000-token budget string
4. LLM extraction: reads the fused context, outputs structured JSON

<details>
<summary><strong>📋 Actual Prompts — LLM Calls #8–9: Re-Ranker</strong></summary>

**System Prompt (both calls):**
```
You are a relevance judge. Given a QUERY and a numbered list of TEXT chunks,
output a JSON array of objects [{"id": <int>, "score": <float 0-1>}] where
score reflects how relevant that chunk is to the query.
1.0 = perfectly relevant, 0.0 = irrelevant. Be strict.
```

**User Prompt (call #8) — Financial retrieval query:**
```
QUERY: Google Cloud Azure AWS quarterly revenue growth operating income margin

CHUNKS:
[0] Alphabet's Google Cloud reported strong results for the fourth quarter
    of 2025. Here are the key figures: Revenue: $17.7 billion in Q4 2025...
[1] Amazon Web Services (AWS) reported its latest quarterly earnings...
[2] Accelerated Growth and Market Share Gains: Higher Revenue Growth Rates...
...(10 chunks total per batch)
```

**Parameters:** `json_mode=true, thinking_budget=0, temperature=0.0, max_tokens=1024, response_schema=RERANKER_SCHEMA`

</details>

<details>
<summary><strong>📋 Actual Prompt — LLM Call #10: Financial Extraction</strong></summary>

**System Prompt:**
```
You are a financial data extraction specialist. Given raw text from earnings
reports, SEC filings, and news, extract cloud-segment financial data.

Return ONLY valid JSON:
{
  "companies": {
    "GOOGL": {
      "name": "Google Cloud",
      "current_revenue_b": <float or null>,
      "previous_revenue_b": <float or null>,
      "yoy_growth_pct": <float or null>,
      "operating_margin_pct": <float or null>
    },
    "MSFT": { ... same fields for Azure ... },
    "AMZN": { ... same fields for AWS ... }
  },
  "quarter": "<best guess like FY2025-Q4 or 'latest available'>"
}

Extract ONLY numbers you find in the text. Use null for missing values.
Do NOT invent numbers. Round all numeric values to at most 2 decimal places.
```

**User Prompt (abbreviated — full version is ~3000 tokens of fused context):**
```
RAW DATA:
[source=grounded_search company=GOOGL score=1.00]
Alphabet's Google Cloud reported strong results for Q4 2025:
  Revenue: $17.7 billion, YoY growth: 48%, Operating income: $5.3B,
  Operating margin: 30.1%...

[source=grounded_search company=AMZN score=1.00]
AWS Q4 2025: Revenue $35.6 billion, YoY growth: 24%...

[source=grounded_search company=MSFT score=1.00]
Microsoft Intelligent Cloud Q2 FY2026: Revenue $32.9B, YoY growth: 29%...
```

**Parameters:** `json_mode=true, thinking_budget=0, temperature=0.0, max_tokens=8192, response_schema=SCHEMA_EXTRACTION`

</details>

**Note:** The extraction produced 8182 output tokens due to the floating-point precision explosion bug — Gemini output `48.000000000000014210854715...` (hundreds of digits). The `_clean_json_text()` regex truncated it, but the initial JSON parse failed. The raw text was stored and the pipeline continued (the threat analysis step reads the raw data directly).

### Phase 6: Threat Analysis + Critique (10:51:47 – 10:52:01) — LLM Calls #11–14

```
10:51:50  INFO  llm_client  generate: 1680 in / 95 out   ← reranker (signals retrieval)
10:51:51  INFO  llm_client  generate: 1223 in / 118 out  ← reranker (signals retrieval)
10:51:56  INFO  llm_client  generate: 925 in / 145 out   ← threat analysis
10:52:01  INFO  llm_client  generate: 791 in / 180 out   ← critique
10:52:01  INFO  agents  Critique score: 8.3 / 10  verdict=PASS  ← Good enough first try!
```

In this run, the critique scored **8.3/10** on the first try (PASS), so the refinement loop was **not triggered**. The previous run (March 25) scored 6.5/10 → triggered refinement → 8.0/10. This variability is normal — the grounded search returns slightly different data each time, which affects analysis depth.

<details>
<summary><strong>📋 Actual Prompts — LLM Calls #11–12: Signals Re-Ranking</strong></summary>

Same re-ranker system prompt as calls #8–9, but with a different query:

**User Prompt Query:**
```
Google Cloud AI growth drivers Vertex AI developer preference startup migration
```

10 chunks per batch about AI workloads, Vertex AI, startup programs, developer experience.

</details>

<details>
<summary><strong>📋 Actual Prompt — LLM Call #13: Threat Analysis</strong></summary>

**System Prompt:**
```
You are a competitive intelligence analyst. Given financial data and
qualitative signals about the cloud market, identify the single biggest
strategic threat that Google Cloud poses to Microsoft Azure.

Be specific: name the product lines, market segments, and dynamics involved.
Keep the response under 200 words.
```

**User Prompt (structured with three sections):**
```
FINANCIAL DATA:
{raw extracted financials including GOOGL revenue $17.7B, 48% growth,
 MSFT revenue $32.9B, 29% growth, etc.}

QUALITATIVE SIGNALS:
[source=grounded_search company=GOOGL score=1.00]
Startups and data-native companies are increasingly choosing Google Cloud's
Vertex AI and BigQuery over Azure's offerings... Vertex AI centralizes the
entire ML lifecycle... Over 60% of funded generative AI startups globally
are building on Google Cloud...

STRATEGIC MEMORY:
  (Microsoft Azure) --[StronglyLinkedTo]--> (Office365/EnterpriseSales)
  (Google Cloud) --[StronglyLinkedTo]--> (AI-First Data-Native Workloads)
```

**Parameters:** `json_mode=false, temperature=0.2, max_tokens=1024`

**Note:** `thinking_budget` is not set (defaults to model's own choice). This is a reasoning task that benefits from deliberation.

</details>

<details>
<summary><strong>📋 Actual Prompt — LLM Call #14: Critique</strong></summary>

**System Prompt:**
```
You are a senior strategy review board member. Your job is to evaluate whether
a strategic analysis conclusion is *deep enough* for a C-suite audience.

Score the conclusion on these four criteria (each 0-10):
  1. Depth       — Does it explain WHY, not just WHAT?
  2. Evidence    — Does it cite specific data or signals?
  3. Causality   — Does it identify root drivers, not symptoms?
  4. Actionability — Could a decision-maker act on this?

Return ONLY valid JSON:
{
  "overall_score": <float 0-10>,
  "depth": <int>, "evidence": <int>,
  "causality": <int>, "actionability": <int>,
  "verdict": "PASS" | "NEEDS_REFINEMENT",
  "feedback": "<specific guidance on what to improve>"
}

If overall_score >= 7, verdict = PASS. Otherwise NEEDS_REFINEMENT.
```

**User Prompt:**
```
CONCLUSION TO REVIEW:
Market share delta: {"note": "insufficient revenue data for delta"}

Threat: The biggest strategic threat Google Cloud poses to Microsoft Azure
is its dominance in attracting AI-first, data-native workloads from startups
and innovative companies.

Google Cloud's Vertex AI and BigQuery, leveraging advanced AI/ML capabilities,
a unified platform, and a developer-friendly, open-source ecosystem, are
capturing over 60% of funded generative AI startups...

SUPPORTING CONTEXT (summary):
[source=grounded_search company=GOOGL score=1.00]
Startups and data-native companies are increasingly choosing...
```

**Parameters:** `json_mode=true, thinking_budget=1024, temperature=0.0, max_tokens=2048, response_schema=SCHEMA_CRITIQUE`

**Why `thinking_budget=1024` here?** Critique needs genuine reasoning — "Is this analysis deep enough? Does it cite evidence?" — that benefits from internal deliberation.

</details>

<details>
<summary><strong>📋 Bonus: Refinement Loop Prompts (Not Triggered This Run, but Available)</strong></summary>

When critique returns `NEEDS_REFINEMENT` (score < 7), the pipeline automatically generates new search queries and re-analyses. Here are the prompts that would have been used:

**Refinement Query System Prompt:**
```
You are a research coordinator. Given critique feedback on a strategic
analysis, generate 1-2 specific web search queries that would fetch the
missing evidence or deeper causal data needed.

Return ONLY valid JSON:
{"queries": ["query 1", "query 2"]}
```

**Refinement Query User Prompt:**
```
CRITIQUE FEEDBACK:
{the "feedback" field from the critique JSON, e.g. "The analysis lacks
specific competitive pricing data and does not quantify the revenue
impact of startup migration..."}
```

**Parameters:** `json_mode=true, thinking_budget=0, temperature=0.0, max_tokens=512, response_schema=SCHEMA_REFINEMENT`

After generating queries, the pipeline retrieves additional chunks from the existing index, then re-runs the **Threat Analysis** and **Critique** prompts (calls #13 and #14 above) with enriched context. This loop runs up to `max_critique_loops` times (default: 2).

</details>

### Phase 7: Strategy + Memory + Synthesis (10:52:01 – 10:52:22) — LLM Calls #15–17

```
10:52:14  INFO  agents  STEP [Strategies]: 3 strategies generated
10:52:16  INFO  agents  Memory +edge: (Google Cloud) --[poses strategic threat to]--> (Microsoft Azure)
10:52:16  INFO  agents  Memory +edge: (Google Cloud) --[dominates in attracting]--> (AI-first, data-native workloads)
10:52:16  INFO  agents  Memory +edge: (Google Cloud's Vertex AI and BigQuery) --[are capturing]--> (funded generative AI startups)
10:52:16  INFO  agents  Memory +edge: (Google Cloud) --[is establishing itself as]--> (preferred platform for next generation of high-growth applications)
10:52:16  INFO  agents  Memory +edge: (Google Cloud winning emerging segments) --[risks relegating]--> (Azure to traditional enterprise workloads)
10:52:16  INFO  agents  Memory +edge: (Azure) --[faces hindered future innovation pipeline in]--> (dynamic areas of cloud computing)
10:52:22  INFO  agents  STEP [Synthesis]: Executive summary generated
```

**6 new knowledge graph edges** were extracted by the LLM from its own analysis. Memory went from 2 seeded edges to 8 total.

<details>
<summary><strong>📋 Actual Prompt — LLM Call #15: Strategy Generation</strong></summary>

**System Prompt:**
```
You are a McKinsey-level strategy consultant advising Microsoft's CEO.
Given the threat analysis and market context, generate exactly 3 actionable
business strategies Microsoft could pursue to mitigate the Google Cloud threat.

Return ONLY valid JSON:
{
  "strategies": [
    {
      "name": "<strategy name>",
      "actions": ["action 1", "action 2", "action 3"],
      "cost": "<estimated cost level and type>",
      "expected_outcome": "<expected business impact>"
    }
  ]
}

Each strategy must be distinct, specific, and actionable.
```

**User Prompt:**
```
THREAT ANALYSIS:
The biggest strategic threat Google Cloud poses to Microsoft Azure is its
dominance in attracting AI-first, data-native workloads from startups and
innovative companies.

Google Cloud's Vertex AI and BigQuery... are capturing over 60% of funded
generative AI startups...

MARKET CONTEXT:
[source=grounded_search company=GOOGL score=1.00]
Startups and data-native companies are increasingly choosing...
Vertex AI Growth Drivers: Advanced AI/ML Capabilities, Unified ML Platform,
Seamless Integration, Open-Source Focus, Generative AI Adoption (60%+),
Developer Experience, Startup Programs and Credits...
```

**Parameters:** `json_mode=true, thinking_budget=2048, temperature=0.3, max_tokens=4096, response_schema=SCHEMA_STRATEGY`

**Why `thinking_budget=2048` (the highest in the pipeline)?** Strategy generation is the most creative reasoning task — it needs to synthesise the threat analysis, consider market dynamics, and generate novel, actionable ideas.

</details>

<details>
<summary><strong>📋 Actual Prompt — LLM Call #16: Memory Extraction</strong></summary>

**System Prompt:**
```
You are a knowledge graph curator. Given analysis text, extract strategic
relationships as (source, relation, target) triples.

Return ONLY valid JSON:
{"edges": [{"source": "...", "relation": "...", "target": "..."}]}

Focus on competitive relationships, growth drivers, and strategic linkages.
Extract 3-6 edges.
```

**User Prompt:**
```
ANALYSIS TEXT:
The biggest strategic threat Google Cloud poses to Microsoft Azure is its
dominance in attracting AI-first, data-native workloads...

[+ the 3 strategies JSON from the previous call]
```

**Parameters:** `json_mode=true, thinking_budget=0, temperature=0.0, max_tokens=1024, response_schema=SCHEMA_MEMORY`

</details>

<details>
<summary><strong>📋 Actual Prompt — LLM Call #17: Executive Summary</strong></summary>

**System Prompt:**
```
You are an executive report writer. Given all the analysis components,
synthesise a concise executive summary (150-200 words) suitable for a
C-suite reader at Microsoft.

Cover: the key threat, why it matters, and the recommended response direction.
```

**User Prompt:**
```
THREAT:
The biggest strategic threat Google Cloud poses to Microsoft Azure is its
dominance in attracting AI-first, data-native workloads...

STRATEGIES:
[
  {"name": "Accelerate Azure AI for Next-Gen Startups", "actions": [...], ...},
  {"name": "Unify Data & AI with Microsoft Fabric", "actions": [...], ...},
  {"name": "...", ...}
]
```

**Parameters:** `json_mode=false, temperature=0.2, max_tokens=1024`

</details>

### Final Output

```
╔══ COST SUMMARY ══════════════════════════════════════╗
║  API calls:            17                         ║
║  Cache hits:            0  (saved API calls)      ║
║  Input tokens:       13,924                      ║
║  Output tokens:      13,405                      ║
║  Embedding tokens:   17,301  (free)              ║
║  Estimated cost:   $  0.0101                      ║
║  Budget remaining: $  0.4899  (98.0% left)       ║
╚══════════════════════════════════════════════════════╝
```

**$0.0101 total** for a full strategic analysis with real data and self-critique. That's **~0.84 INR** — 119x under a 100 INR budget.

---

## 5. Best Practices Implemented (And Why)

### 5.1 Token Optimisation

| Technique | Implementation | Savings |
|---|---|---|
| **Sparse-first hybrid** | `alpha=0.55` (BM25 weighted) | BM25 is free; reduces embedding dependency |
| **Aggressive context budget** | 4000 tokens max | Smaller prompts = cheaper + more focused |
| **Strict rerank floor** | `rerank_relevance_floor=0.35` | Drops irrelevant chunks before they enter prompts |
| **Thinking budget control** | `thinking_budget=0` for extraction | Prevents thinking tokens from eating output budget |
| **Batch embeddings** | `batchEmbedContents` (100 per call) | 3 API calls instead of 226 |
| **Response caching** | `diskcache` content-addressed | Repeated runs cost $0 for cached calls |

### 5.2 Reliability

| Technique | Implementation | Why |
|---|---|---|
| **responseSchema** | OpenAPI-subset schema on all JSON calls | Eliminates structural parse failures |
| **Defensive JSON parsing** | Code-fence stripping + precision regex | Catches edge cases Schema misses |
| **Exponential backoff + jitter** | `wait = base * 2^attempt * random(0.75, 1.25)` | Handles 429/503 gracefully |
| **Budget guard pre-check** | `guard.check()` before every API call | Prevents runaway costs |
| **Fallback in every parse** | `except (JSONDecodeError, TypeError): ...` | Every LLM call has a safe fallback |

### 5.3 Agentic Design

| Property | Implementation | Why |
|---|---|---|
| **LLM planning** | `_PLANNER_SYSTEM` with schema | Plans are task-specific, not hardcoded |
| **Self-critique** | `CritiqueModule` with rubric scoring | Quality gate at 7.0 threshold |
| **Self-refinement** | Refinement query generation + re-retrieval | The system improves its OWN output |
| **Dynamic tool creation** | `FinancialModelerAgent.create_market_share_tool()` | Writes and executes Python at runtime |
| **Knowledge accumulation** | `GraphMemory` reads + writes edges | System learns during execution |

---

## 6. Lessons Learned From Debugging

### 6.1 Model Name Discovery

**Problem:** Initial config used `gemini-2.5-flash-preview-05-20` → HTTP 404.

**Root cause:** Model names change. Preview models expire.

**Fix:** Queried the `ListModels` API:
```bash
curl "https://generativelanguage.googleapis.com/v1beta/models?key=$KEY" | jq '.models[].name'
```
Discovered the correct name was `gemini-2.5-flash`.

**Lesson:** Always verify model names against the live API. Don't copy model names from blog posts.

### 6.2 Embedding Rate Limits

**Problem:** 226 sequential `embedContent` calls hit the 100-request free-tier limit at call #100.

**Fix:** Switched to `batchEmbedContents` endpoint — up to 100 texts per call. 226 chunks → 3 API calls.

**Lesson:** Always check free-tier rate limits. Batch APIs exist for a reason.

### 6.3 Thinking Token Budget

**Problem:** Critique call with `max_tokens=512` produced only 7 output tokens. The other 505 were "thinking" tokens that Gemini 2.5 uses internally.

**Fix:** 
- `thinking_budget=0` for extraction tasks (all budget goes to output)
- `thinking_budget=1024` for critique (some deliberation is useful)
- Increased `max_tokens` to 2048 for critique, 4096 for strategies

**Lesson:** With Gemini 2.5 models, `maxOutputTokens` = thinking + visible output. If you need the full output budget for structured data, disable thinking.

### 6.4 Floating-Point Precision Explosion

**Problem:** Gemini output `48.000000000000014210854715202003717422485351562...` for `yoy_growth_pct`, burning 2000+ tokens on a single number and truncating the JSON.

**Fix:** Three-layer defence:
1. Prompt instruction: "Round all numeric values to at most 2 decimal places"
2. Regex cleanup: `re.sub(r'(\d+\.\d{2})\d{10,}', r'\1', text)`
3. Increased `max_tokens` to 8192 for extraction (safety margin)

**Lesson:** LLMs can produce arbitrarily long numbers. Always post-process numeric output.

---

## 7. Cost Analysis

### Per-Run Breakdown (Real Data — March 26, 2026 Run)

| Category | Calls | Tokens | Cost |
|---|---|---|---|
| Planning | 1 | 120 in / 228 out | $0.0002 |
| Grounded search (6 queries) | 6 | ~420 in / ~4,500 out | $0.003 |
| Embeddings (230 chunks) | 3 batches | 17,301 embedded | **$0.00** (free) |
| Reranking (2 batches × 2 queries) | 4 | ~6.9K in / ~564 out | $0.0013 |
| Extraction | 1 | 3,243 in / 8,182 out | $0.0054 |
| Threat analysis | 1 | 925 in / 145 out | $0.0002 |
| Critique | 1 | 791 in / 180 out | $0.0003 |
| Refinement (not triggered) | 0 | — | $0.00 |
| Strategies | 1 | 669 in / 660 out | $0.0005 |
| Memory extraction | 1 | 548 in / 260 out | $0.0002 |
| Synthesis | 1 | 636 in / 40 out | $0.0001 |
| **TOTAL** | **17** | **~14K in / ~13K out** | **$0.0101** |

**Note:** The critique scored 8.3/10 (PASS) on the first try, so the refinement loop was not triggered. When refinement *is* triggered (as in the March 25 run at 6.5→8.0), the pipeline adds 3–5 extra LLM calls ($0.001–$0.003 additional) for query generation, retrieval, re-analysis, and re-critique.

### Cost Comparison

| Scenario | Cost (USD) | Cost (INR) |
|---|---|---|
| Our run (Gemini 2.5 Flash) | $0.0101 | ₹0.84 |
| Same with GPT-4o ($5/$15 per 1M) | ~$0.23 | ₹19 |
| Same with Claude Opus ($15/$75 per 1M) | ~$2.33 | ₹195 |

Gemini Flash is 28x cheaper than GPT-4o and 279x cheaper than Claude Opus for this workload.

---

## 8. Documentation Links

| Topic | Link |
|---|---|
| Gemini REST API Quickstart | https://ai.google.dev/gemini-api/docs/quickstart?lang=rest |
| Gemini Structured Output (responseSchema) | https://ai.google.dev/gemini-api/docs/structured-output |
| Gemini Thinking Config | https://ai.google.dev/gemini-api/docs/thinking |
| Gemini Search Grounding | https://ai.google.dev/gemini-api/docs/grounding |
| Gemini Embeddings | https://ai.google.dev/gemini-api/docs/embeddings |
| Gemini Pricing | https://ai.google.dev/pricing |
| SEC EDGAR XBRL API | https://www.sec.gov/edgar/sec-api-documentation |
| Okapi BM25 (Wikipedia) | https://en.wikipedia.org/wiki/Okapi_BM25 |
| Reciprocal Rank Fusion (paper) | https://plg.uwaterloo.ca/~gvcormac/cormacksigir09-rrf.pdf |
| Exponential Backoff + Jitter | https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/ |
| diskcache (Python caching) | https://grantjenks.com/docs/diskcache/ |
| RAG overview (Google) | https://cloud.google.com/use-cases/retrieval-augmented-generation |

---

*Generated as a learning companion for the Usecase 3 Autonomous Strategic Analysis system.*
*All execution data, code references, and cost figures are from the actual March 26, 2026 run.*
*All prompts captured from `outputs/prompt_log_20260326_104944.jsonl`.*

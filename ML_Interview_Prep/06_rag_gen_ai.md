# RAG (Retrieval-Augmented Generation) - Gen AI Comprehensive Guide

## Table of Contents
1. [RAG Fundamentals](#fundamentals)
2. [Architecture & Components](#architecture)
3. [Embedding Models & Vector Stores](#embeddings)
4. [Chunking Strategies](#chunking)
5. [Retrieval Strategies](#retrieval)
6. [Advanced RAG Patterns](#advanced-rag)
7. [Evaluation & Metrics](#evaluation)
8. [Production Considerations](#production)
9. [Interview Questions with Answers](#interview-questions)
10. [Comparisons & Alternatives](#comparisons)

---

## RAG Fundamentals

### What is RAG?
Retrieval-Augmented Generation combines information retrieval with language generation. Instead of relying solely on the LLM's training data, it retrieves relevant documents and uses them as context for generating answers.

**Layman Example:** Like an open-book exam — instead of memorizing everything, you can look up relevant pages in your textbook before answering the question. The LLM is the student, and the retrieval system is the textbook.

### Why RAG?

**Problems with Vanilla LLMs:**
- Hallucination (confidently generates incorrect information)
- Knowledge cutoff (doesn't know recent events)
- No source attribution (can't cite where info came from)
- Can't access private/proprietary data
- Expensive to retrain for new knowledge

**RAG Solves:**
| Problem | RAG Solution |
|---------|-------------|
| Hallucination | Grounds answers in retrieved facts |
| Knowledge cutoff | Retrieves up-to-date documents |
| No sources | Can cite retrieved passages |
| Private data | Indexes proprietary documents |
| Retraining cost | Just update the document index |

### RAG vs Fine-tuning vs Prompt Engineering

| Approach | When to Use | Pros | Cons |
|----------|-------------|------|------|
| Prompt Engineering | Simple tasks, few examples | Fast, no data needed | Limited by context window |
| RAG | Need external/dynamic knowledge | Grounded, updatable, citable | Retrieval quality dependency |
| Fine-tuning | Need behavior/style change | Consistent style, efficient inference | Expensive, static knowledge |
| RAG + Fine-tuning | Best of both worlds | Grounded + adapted behavior | Most complex |

---

## Architecture & Components

### Basic RAG Pipeline
```
Offline (Indexing):
Documents → Chunking → Embedding → Vector Store

Online (Query):
User Query → Embed Query → Retrieve Top-K chunks → 
Augment Prompt (query + retrieved chunks) → LLM → Answer
```

### Component Breakdown

#### 1. Document Loading
- **Sources:** PDF, HTML, Markdown, databases, APIs, emails
- **Parsers:** PyPDF, Unstructured, LlamaParse, Docling
- **Considerations:** Tables, images, metadata preservation

#### 2. Text Splitting/Chunking
- Split documents into manageable pieces
- Balance: Too small = missing context; Too large = noise + cost

#### 3. Embedding Model
- Convert text chunks to dense vectors
- Captures semantic meaning in vector space
- Same model for documents and queries

#### 4. Vector Store
- Stores and indexes embeddings for fast similarity search
- ANN (Approximate Nearest Neighbor) algorithms

#### 5. Retriever
- Finds relevant chunks given a query
- Similarity search (cosine, dot product, euclidean)

#### 6. Generator (LLM)
- Generates answer using retrieved context + query
- Prompt includes: system instructions + retrieved passages + user question

---

## Embedding Models & Vector Stores

### Embedding Models

| Model | Dimensions | Context | Open/Closed |
|-------|-----------|---------|-------------|
| OpenAI text-embedding-3-large | 3072 | 8191 tokens | Closed |
| OpenAI text-embedding-3-small | 1536 | 8191 tokens | Closed |
| Cohere embed-v3 | 1024 | 512 tokens | Closed |
| BGE-large-en | 1024 | 512 tokens | Open |
| E5-mistral-7b-instruct | 4096 | 32K tokens | Open |
| Nomic-embed-text | 768 | 8192 tokens | Open |
| GTE-Qwen2 | 1536 | 8192 tokens | Open |

### How Embeddings Work
- Map text to high-dimensional vector space
- Similar meanings → nearby vectors
- Trained via contrastive learning (positive pairs close, negative pairs far)
- Asymmetric models: Different encoding for query vs document

### Vector Stores

| Store | Type | Key Feature | Scale |
|-------|------|-------------|-------|
| FAISS (Meta) | Library | Fast, flexible indices | Billions |
| Pinecone | Managed | Serverless, easy | Millions+ |
| Weaviate | Open-source | Hybrid search, modules | Millions |
| Milvus/Zilliz | Open-source | Distributed, scalable | Billions |
| Chroma | Open-source | Simple, lightweight | Thousands-Millions |
| Qdrant | Open-source | Filtering + vector search | Millions |
| pgvector | Postgres extension | Existing Postgres infra | Millions |

### ANN Algorithms

| Algorithm | Index Type | Speed | Recall | Memory |
|-----------|-----------|-------|--------|--------|
| HNSW | Graph | Very fast | High | High |
| IVF | Inverted index | Fast | Medium-High | Medium |
| PQ (Product Quantization) | Compression | Fast | Medium | Low |
| Flat/Brute force | None | Slow | Perfect | High |
| ScaNN (Google) | Learned quantization | Very fast | High | Medium |

**HNSW (Hierarchical Navigable Small World):**
- Multi-layer graph structure
- Navigate from coarse (top) to fine (bottom) layers
- O(log n) search time
- Most popular for production systems

---

## Chunking Strategies

### Fixed-Size Chunking
```python
# Simple approach
chunks = [text[i:i+chunk_size] for i in range(0, len(text), chunk_size - overlap)]
```
- **Pros:** Simple, predictable
- **Cons:** May split sentences/concepts mid-way

### Recursive Character Splitting
- Split by: paragraphs → sentences → words → characters
- Try larger separators first, fall back to smaller
- Most common in LangChain

### Semantic Chunking
- Split based on meaning shifts (embedding similarity between consecutive sentences)
- When similarity drops → chunk boundary
- Produces more coherent chunks

### Document-Structure-Based
- Use headers, sections, paragraphs as natural boundaries
- Markdown: Split by headers (H1, H2, H3)
- HTML: Split by semantic tags
- Code: Split by functions/classes

### Agentic Chunking
- Use an LLM to decide chunk boundaries
- "Does this sentence belong with the previous chunk?"
- Most accurate but expensive

### Chunk Size Guidelines

| Use Case | Chunk Size | Overlap |
|----------|-----------|---------|
| Q&A (precise) | 256-512 tokens | 50-100 |
| Summarization | 1024-2048 tokens | 100-200 |
| General purpose | 512-1024 tokens | 100-150 |
| Code | Function/class level | None |

### Key Principles
- **Overlap:** Prevents losing context at boundaries (10-20% of chunk size)
- **Metadata:** Attach source, page, section info to each chunk
- **Parent-child:** Store small chunks for retrieval, return larger parent for context
- **Proposition-based:** Break into atomic facts/statements, each as a chunk

---

## Retrieval Strategies

### Dense Retrieval (Semantic Search)
- Embed query and documents → cosine similarity
- Captures semantic meaning ("dog" matches "puppy")
- Requires embedding model
- **Limitation:** May miss exact keyword matches

### Sparse Retrieval (BM25/TF-IDF)
- Keyword-based matching with term frequency weighting
- BM25: Improved TF-IDF with length normalization
- Fast, no embedding needed
- **Limitation:** Misses semantic similarity

### Hybrid Search (Best Practice)
- Combine dense + sparse retrieval
- **Reciprocal Rank Fusion (RRF):** Merge ranked lists from both
```
RRF_score(d) = Σ 1/(k + rank_i(d))  for each retrieval system i
```
- Typically k=60 (constant)
- Gets benefits of both semantic understanding AND keyword precision

### Multi-Query Retrieval
- Generate multiple search queries from the original question
- LLM rephrases user question in different ways
- Retrieve for each query, merge results
- Improves recall for ambiguous questions

### Query Transformation

| Technique | Description |
|-----------|-------------|
| Query rewriting | LLM improves/clarifies the query |
| HyDE (Hypothetical Document Embedding) | Generate hypothetical answer, use as query |
| Step-back prompting | Ask a broader question first |
| Sub-question decomposition | Break complex question into simpler parts |

### Re-ranking
- Retrieve more candidates (top-100), then re-rank to top-K
- Cross-encoder re-rankers (BERT-based): Score (query, passage) pairs
- More accurate than bi-encoder but slower (not scalable for initial retrieval)
- **Models:** Cohere Rerank, bge-reranker, ColBERT

### Metadata Filtering
- Pre-filter by metadata before vector search
- Example: Only search documents from 2024, or specific department
- Reduces search space, improves relevance

---

## Advanced RAG Patterns

### Naive RAG → Advanced RAG → Modular RAG

```
Naive RAG:    Query → Retrieve → Generate
Advanced RAG: Query Transform → Retrieve → Rerank → Generate (with citations)
Modular RAG:  Flexible composition of retrieval/generation modules
```

### Self-RAG (Self-Reflective RAG)
1. Decide if retrieval is needed for this query
2. If yes, retrieve and evaluate relevance of passages
3. Generate response
4. Critique own response (is it grounded in evidence?)
5. If not, regenerate or retrieve more

### Corrective RAG (CRAG)
- After retrieval, evaluate quality of retrieved documents
- If LOW quality → web search fallback
- If MEDIUM → refine/filter passages
- If HIGH → proceed to generation
- Self-correcting mechanism

### Graph RAG
- Build knowledge graph from documents
- Retrieve relevant graph neighborhoods
- LLM reasons over graph structure + text
- Better for multi-hop questions ("Who founded the company that made the iPhone?")
- Microsoft's GraphRAG: Communities + summaries at different levels

### Agentic RAG
- Agent decides WHEN and HOW to retrieve
- Tools: Search, retrieve from specific indices, code execution
- Iterative: Retrieve → Assess → Retrieve more if needed → Answer
- Can route to different retrieval strategies based on query type

### Parent-Child Document Retrieval
```
Index: Small chunks (256 tokens) — for precise matching
Return: Parent document (2048 tokens) — for full context
```
- Retrieve precise chunk, return surrounding context
- Balances retrieval precision with generation context

### Multi-Index RAG
- Different indices for different document types
- Route query to appropriate index
- Example: Technical docs index, FAQ index, Code index
- Router decides which index to query

### Contextual Compression
- After retrieval, compress/extract only relevant parts
- LLM extracts the specific sentence(s) answering the question
- Reduces noise in context, saves tokens

### Late Chunking
- Embed the full document with a long-context model
- Chunk AFTER embedding (preserving cross-chunk context in embeddings)
- Each chunk's embedding is context-aware

---

## Evaluation & Metrics

### RAG Evaluation Framework (RAGAS)

| Metric | What it Measures | How |
|--------|-----------------|-----|
| Faithfulness | Is answer grounded in context? | Check claims against retrieved passages |
| Answer Relevancy | Does answer address the question? | Compare answer to question semantically |
| Context Precision | Are retrieved docs relevant? | Check if retrieved passages relate to question |
| Context Recall | Are all needed facts retrieved? | Compare needed facts to retrieved content |

### Component-Level Evaluation

#### Retrieval Quality
- **Hit Rate@K:** Is the correct passage in top-K?
- **MRR:** Mean Reciprocal Rank of correct passage
- **NDCG@K:** Ranking quality of retrieved passages
- **Context Relevance:** % of retrieved content that's actually useful

#### Generation Quality
- **Faithfulness/Groundedness:** Does answer cite only retrieved info?
- **Answer Correctness:** Compared to ground truth
- **Hallucination Rate:** % of claims not in context
- **Completeness:** Does it answer all parts of the question?

### End-to-End Evaluation
- **Human evaluation:** Gold standard but expensive
- **LLM-as-judge:** GPT-4 evaluates quality (scalable)
- **Reference-based:** ROUGE, BLEU against reference answers
- **Custom rubrics:** Domain-specific evaluation criteria

### Building Evaluation Datasets
1. Collect representative questions
2. Identify relevant passages (ground truth retrieval)
3. Write reference answers
4. Create synthetic QA pairs using LLM from your documents
5. Include edge cases: multi-hop, unanswerable, ambiguous

---

## Production Considerations

### Scalability Architecture
```
Document Pipeline:
  Ingestion → Processing → Chunking → Embedding → Index Updates

Query Pipeline:
  Load Balancer → API Gateway → Retrieval Service → 
  Re-ranking Service → LLM Service → Response Cache
```

### Key Production Challenges

#### 1. Latency
- Embedding query: 10-50ms
- Vector search: 10-100ms
- Re-ranking: 100-500ms
- LLM generation: 500ms-5s
- **Total:** 1-6 seconds typical

**Optimizations:**
- Cache frequent queries
- Streaming responses
- Parallel retrieval from multiple indices
- GPU-accelerated vector search
- Smaller/faster models for re-ranking

#### 2. Cost
- Embedding API calls (per token)
- Vector store hosting
- LLM inference (per token)
- **Optimization:** Batch embeddings, cache, use open-source models where possible

#### 3. Document Updates
- How to handle new/updated/deleted documents
- Incremental indexing vs full re-index
- Versioning of embeddings (model upgrade = re-embed everything)

#### 4. Security & Access Control
- Document-level permissions
- Filter results by user's access level
- Don't leak private documents to unauthorized users
- PII detection and masking

### Observability & Monitoring
- Track retrieval quality over time
- Log: queries, retrieved chunks, answers, user feedback
- Alert on: high hallucination rate, low relevance scores
- A/B test different configurations

### Prompt Engineering for RAG

```
System: You are a helpful assistant. Answer based ONLY on the provided context.
If the context doesn't contain enough information, say "I don't have enough information."
Always cite your sources.

Context:
{retrieved_passages}

User: {question}
```

**Key Principles:**
- Instruct to stay grounded in context
- Allow "I don't know" responses
- Request citations
- Structure context clearly (numbered passages, with source metadata)

---

## Interview Questions with Answers

### Q1: What are the failure modes of RAG and how to address them?
**Answer:**
1. **Retrieval failures:**
   - Wrong/irrelevant documents retrieved → Better embeddings, hybrid search, re-ranking
   - Missing relevant documents → Improve chunking, multi-query, better coverage
   - Query doesn't match document language → Query rewriting, HyDE

2. **Generation failures:**
   - Hallucination despite context → Stronger system prompt, faithfulness checks
   - Ignoring retrieved context → Better prompt engineering, shorter context
   - Context too long (lost in middle) → Reorder (important docs first/last), summarize

3. **System failures:**
   - Outdated index → Incremental updates, freshness scoring
   - Latency too high → Caching, faster models, parallel retrieval

### Q2: How do you choose between RAG and fine-tuning?
**Answer:**
- **RAG when:**
  - Knowledge changes frequently
  - Need source citations
  - Large knowledge base
  - Factual accuracy is critical
  - Multiple data sources
  
- **Fine-tuning when:**
  - Need specific behavior/style/format
  - Domain-specific language understanding
  - Performance on specific task structure
  - Reduce inference cost (no retrieval overhead)
  
- **Both when:**
  - Fine-tune for domain understanding + RAG for specific facts
  - Example: Medical LLM (fine-tuned) + RAG for latest research papers

### Q3: Explain the "Lost in the Middle" problem
**Answer:**
- LLMs pay more attention to beginning and end of long contexts
- Information in the middle of retrieved passages may be ignored
- **Paper finding:** Performance drops for relevant info placed in middle positions
- **Solutions:**
  - Place most relevant passages first and last
  - Limit number of retrieved passages
  - Summarize/compress context
  - Use models trained for long context (Claude, Gemini)
  - Reorder passages by relevance score

### Q4: How do you handle multi-hop questions in RAG?
**Answer:**
- Multi-hop: Answer requires connecting info from multiple documents
- Example: "Who is the CEO of the company that acquired Twitter?"
  - Step 1: "Who acquired Twitter?" → Elon Musk/X Corp
  - Step 2: "Who is CEO of X Corp?" → Elon Musk

- **Solutions:**
  - **Iterative retrieval:** Retrieve → Extract info → Retrieve again with new context
  - **Graph RAG:** Build knowledge graph, traverse relationships
  - **Sub-question decomposition:** Break into steps, retrieve for each
  - **Agent-based:** Agent decides when to search again
  - **Chain-of-Thought + RAG:** Reason about what info is needed iteratively

### Q5: What is the difference between bi-encoder and cross-encoder?
**Answer:**
- **Bi-encoder (Two-tower):**
  - Encode query and document SEPARATELY
  - Score = dot product/cosine of embeddings
  - Fast (documents encoded offline)
  - Used for initial retrieval
  - Less accurate (no cross-attention between query and doc)

- **Cross-encoder:**
  - Encode query AND document TOGETHER
  - Full attention between query and document tokens
  - Much more accurate
  - Slow (must run for each query-document pair)
  - Used for re-ranking (top-100 → top-10)

- **Best practice:** Bi-encoder for retrieval (fast, scalable) → Cross-encoder for re-ranking (accurate, small set)

### Q6: How do you evaluate and improve chunking?
**Answer:**
- **Evaluate:** Retrieve and check if correct answer appears in retrieved chunks
  - If answer spans multiple chunks → chunks are too small
  - If chunks contain too much irrelevant text → chunks are too large
  - If retrieval precision is low → chunking loses context

- **Improvement strategies:**
  - Experiment with different sizes (256, 512, 1024)
  - Use semantic chunking (split at topic boundaries)
  - Add overlap to prevent boundary issues
  - Include metadata (title, section header) in each chunk
  - Parent-child: Small for retrieval, large for context
  - Proposition-based: Atomic facts as chunks

### Q7: What is HyDE and when would you use it?
**Answer:**
- **HyDE (Hypothetical Document Embeddings):**
  1. LLM generates a hypothetical answer to the query (may be wrong)
  2. Embed this hypothetical answer
  3. Use it as the search query (instead of the original question)

- **Why it works:**
  - Query embeddings and document embeddings live in different "spaces"
  - A hypothetical document is more similar to real documents
  - Bridges the gap between question-style and document-style text

- **When to use:**
  - Short queries that don't match document style
  - Technical domains where query language differs from document language
  - When standard retrieval has low recall

- **Limitation:** LLM may generate wrong hypothetical → retrieve wrong docs

### Q8: How do you handle structured data (tables, databases) in RAG?
**Answer:**
- **Text-to-SQL:** Convert natural language to SQL, execute against database
- **Table serialization:** Convert tables to markdown/text for embedding
- **Specialized embeddings:** Embed table structure (row-column awareness)
- **Hybrid approach:**
  - Route: If structured → Text-to-SQL; If unstructured → Vector search
  - Union results from both
- **Tools:** LlamaParse for table extraction, Docling for complex documents
- **Challenge:** Preserving table relationships in chunked text

### Q9: Explain different vector search distance metrics
**Answer:**
| Metric | Formula | Range | Use Case |
|--------|---------|-------|----------|
| Cosine Similarity | dot(A,B)/(‖A‖×‖B‖) | [-1, 1] | Text similarity (normalized) |
| Dot Product | Σ(A_i × B_i) | (-∞, +∞) | When magnitude matters |
| Euclidean (L2) | √Σ(A_i - B_i)² | [0, +∞) | Spatial proximity |

- **Cosine:** Most common for NLP (ignores magnitude, focuses on direction)
- **Dot Product:** Faster, use when embeddings are normalized
- **Euclidean:** Good for computer vision, spatial data
- **For normalized vectors:** Cosine = Dot Product (same result)

### Q10: What is Contextual Retrieval (Anthropic's approach)?
**Answer:**
- Prepend contextual information to each chunk before embedding
- Use LLM to add a short explanation of where each chunk fits in the document
- Example: Before: "Revenue grew 15%..." After: "In Q3 2024 earnings report, section on financials: Revenue grew 15%..."
- Dramatically improves retrieval accuracy (49% fewer failures)
- Combine with BM25 for even better results (67% fewer failures)
- Trade-off: Higher indexing cost (LLM call per chunk), but better retrieval

---

## Comparisons & Alternatives

### RAG Framework Comparison

| Framework | Strengths | Best For |
|-----------|-----------|----------|
| LangChain | Flexible, many integrations | Prototyping, complex chains |
| LlamaIndex | Data-focused, good indexing | Document QA, structured data |
| Haystack | Production-ready, pipelines | Enterprise deployments |
| Semantic Kernel (MS) | .NET/enterprise focus | Microsoft ecosystem |
| Vercel AI SDK | Web-focused, streaming | Frontend AI apps |

### When RAG is NOT the Right Approach
- Question doesn't need external knowledge (reasoning, math)
- All needed knowledge fits in system prompt
- Real-time data needed (use function calling/tools instead)
- Task is about behavior not knowledge (use fine-tuning)
- Documents are too noisy/low-quality

### RAG vs Long Context Models

| Aspect | RAG | Long Context (1M+ tokens) |
|--------|-----|---------------------------|
| Precision | High (retrieves specific chunks) | Moderate (needle in haystack) |
| Cost | Lower per query (fewer tokens) | Higher (all context every time) |
| Scalability | Unlimited corpus | Limited by context window |
| Latency | Retrieval overhead | All in one call |
| Freshness | Easy to update index | Must update context |
| Best for | Large corpus, precision | Small corpus, multi-doc reasoning |

### Emerging RAG Patterns (2024-2025)
1. **RAG + Agents:** Dynamic tool selection for retrieval
2. **Multi-modal RAG:** Retrieve images, tables, diagrams alongside text
3. **Graph RAG:** Knowledge graph + vector search combined
4. **Adaptive RAG:** System decides when retrieval is needed
5. **RAG with structured outputs:** Guaranteed format responses
6. **Speculative RAG:** Draft answers, verify with retrieval
7. **Collaborative RAG:** Multiple specialized retrievers

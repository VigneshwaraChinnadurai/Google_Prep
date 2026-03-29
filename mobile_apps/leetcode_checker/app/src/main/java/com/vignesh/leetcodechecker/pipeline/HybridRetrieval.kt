package com.vignesh.leetcodechecker.pipeline

import android.util.Log
import com.vignesh.leetcodechecker.data.*
import org.json.JSONObject

/**
 * Hybrid retrieval combining BM25 (sparse) + Dense (vector) via
 * Reciprocal Rank Fusion, plus LLM-based reranking.
 *
 * Ported from Python hybrid_index.py and reranker.py.
 */

// ════════════════════════════════════════════════════════════════════════
// Hybrid Index — RRF fusion of sparse + dense results
// ════════════════════════════════════════════════════════════════════════

class HybridIndex(
    private val sparse: BM25Index,
    private val dense: DenseIndex,
    private val alpha: Double = 0.4,   // weight for sparse vs dense
    private val rrfK: Int = 60
) {
    /**
     * Build both indices.
     */
    suspend fun build(chunks: List<PipelineChunk>) {
        sparse.build(chunks)
        dense.build(chunks)
    }

    /**
     * Search using Reciprocal Rank Fusion.
     * alpha controls sparse weight (1-alpha for dense).
     */
    suspend fun search(query: String, topK: Int = 20): List<PipelineSearchResult> {
        val sparseResults = sparse.search(query, topK = topK * 2)
        val denseResults = dense.search(query, topK = topK * 2)

        // Build RRF score maps
        val rrfScores = mutableMapOf<String, Double>()
        val chunkMap = mutableMapOf<String, PipelineChunk>()

        for ((rank, result) in sparseResults.withIndex()) {
            val id = result.chunk.chunkId
            chunkMap[id] = result.chunk
            rrfScores[id] = (rrfScores[id] ?: 0.0) + alpha / (rrfK + rank + 1)
        }
        for ((rank, result) in denseResults.withIndex()) {
            val id = result.chunk.chunkId
            chunkMap[id] = result.chunk
            rrfScores[id] = (rrfScores[id] ?: 0.0) + (1 - alpha) / (rrfK + rank + 1)
        }

        return rrfScores.entries
            .sortedByDescending { it.value }
            .take(topK)
            .mapIndexed { rank, (id, score) ->
                PipelineSearchResult(
                    chunk = chunkMap[id]!!,
                    score = score,
                    rank = rank
                )
            }
    }
}

// ════════════════════════════════════════════════════════════════════════
// LLM Reranker — uses Gemini to score relevance of retrieved chunks
// ════════════════════════════════════════════════════════════════════════

class LLMReranker(
    private val geminiApi: GeminiApi,
    private val apiKey: String,
    private val model: String = "gemini-2.5-flash",
    private val maxChunksPerCall: Int = 10,
    private val threshold: Double = 0.3
) {
    companion object {
        private const val TAG = "LLMReranker"

        private const val RERANKER_SYSTEM = """You are a relevance judge. Given a QUERY and a list of numbered TEXT CHUNKS, score each chunk's relevance to the query on a scale of 0.0 to 1.0.

Return ONLY valid JSON:
{"scores": [{"index": 0, "score": 0.85}, {"index": 1, "score": 0.2}, ...]}

Scoring guide:
- 1.0 = directly answers the query with specific data
- 0.7 = highly relevant context
- 0.4 = somewhat related
- 0.1 = barely relevant
- 0.0 = completely irrelevant"""

        private val RERANKER_SCHEMA: Map<String, Any> = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "scores" to mapOf(
                    "type" to "array",
                    "items" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "index" to mapOf("type" to "integer"),
                            "score" to mapOf("type" to "number")
                        )
                    )
                )
            )
        )
    }

    /**
     * Rerank search results using LLM scoring. Returns filtered + re-sorted results.
     */
    suspend fun rerank(
        query: String,
        results: List<PipelineSearchResult>,
        topK: Int = 10
    ): List<PipelineSearchResult> {
        if (results.isEmpty()) return emptyList()

        val allScored = mutableListOf<Pair<PipelineSearchResult, Double>>()

        // Process in batches
        for (batchStart in results.indices step maxChunksPerCall) {
            val batchEnd = minOf(batchStart + maxChunksPerCall, results.size)
            val batch = results.subList(batchStart, batchEnd)

            val chunksText = batch.mapIndexed { i, r ->
                "CHUNK $i:\n${r.chunk.text.take(500)}"
            }.joinToString("\n\n")

            val prompt = "QUERY: $query\n\n$chunksText"

            try {
                val request = GeminiGenerateRequest(
                    systemInstruction = GeminiContent(
                        parts = listOf(GeminiPart(text = RERANKER_SYSTEM))
                    ),
                    contents = listOf(
                        GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                    ),
                    generationConfig = GeminiGenerationConfig(
                        temperature = 0.0,
                        maxOutputTokens = 1024,
                        responseMimeType = "application/json",
                        responseSchema = RERANKER_SCHEMA,
                        thinkingConfig = null
                    )
                )
                val response = geminiApi.generateContent(model, apiKey, request)
                val text = response.candidates?.firstOrNull()?.content?.parts
                    ?.mapNotNull { it.text }?.joinToString("") ?: ""

                val json = JSONObject(cleanJsonText(text))
                val scores = json.optJSONArray("scores")

                if (scores != null) {
                    for (j in 0 until scores.length()) {
                        val item = scores.getJSONObject(j)
                        val idx = item.optInt("index", -1)
                        val score = item.optDouble("score", 0.0)
                        if (idx in batch.indices) {
                            allScored.add(batch[idx] to score)
                        }
                    }
                }
                // Add unscored as low-score
                val scoredIndices = (0 until (scores?.length() ?: 0)).map {
                    scores!!.getJSONObject(it).optInt("index", -1)
                }.toSet()
                for (i in batch.indices) {
                    if (i !in scoredIndices) {
                        allScored.add(batch[i] to 0.1)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Reranker batch failed: ${e.message}")
                // Fallback: keep original scores
                for (result in batch) {
                    allScored.add(result to result.score)
                }
            }
        }

        return allScored
            .filter { it.second >= threshold }
            .sortedByDescending { it.second }
            .take(topK)
            .mapIndexed { rank, (result, score) ->
                PipelineSearchResult(
                    chunk = result.chunk,
                    score = score,
                    rank = rank
                )
            }
    }

    private fun cleanJsonText(text: String): String {
        var cleaned = text.trim()
        if (cleaned.startsWith("```")) {
            val lines = cleaned.split("\n").toMutableList()
            lines.removeFirst()
            if (lines.isNotEmpty() && lines.last().trim() == "```") lines.removeLast()
            cleaned = lines.joinToString("\n").trim()
        }
        return cleaned
    }
}

// ════════════════════════════════════════════════════════════════════════
// RetrievalPipeline — facade combining all retrieval components
// ════════════════════════════════════════════════════════════════════════

class RetrievalPipeline(
    geminiApi: GeminiApi,
    apiKey: String,
    model: String = "gemini-2.5-flash",
    companyPatterns: List<Pair<String, String>>? = null,
    // Config params matching Python defaults
    chunkSize: Int = 480,
    chunkOverlap: Int = 60,
    bm25K1: Double = 1.5,
    bm25B: Double = 0.75,
    hybridAlpha: Double = 0.4,
    rrfK: Int = 60,
    contextTokenBudget: Int = 6000
) {
    private val companyPats = companyPatterns
    private val chunkSz = chunkSize
    private val chunkOv = chunkOverlap

    private val sparse = BM25Index(k1 = bm25K1, b = bm25B)
    private val dense = DenseIndex(geminiApi, apiKey)
    private val hybrid = HybridIndex(sparse, dense, alpha = hybridAlpha, rrfK = rrfK)
    private val reranker = LLMReranker(geminiApi, apiKey, model)
    private val fuser = ContextFuser(tokenBudget = contextTokenBudget)

    private var chunks = listOf<PipelineChunk>()

    /**
     * Ingest documents: chunk, enrich metadata, build indices.
     * Returns chunk count.
     */
    suspend fun ingest(documents: List<PipelineDocument>): Int {
        val raw = Chunker.chunkDocuments(documents, chunkSize = chunkSz, overlap = chunkOv)
        val enriched = MetadataEnricher.enrichChunks(raw, companyPats)
        chunks = enriched
        hybrid.build(enriched)
        return enriched.size
    }

    /**
     * Query: hybrid search → rerank → fuse into context string.
     */
    suspend fun query(query: String, topK: Int = 10): String {
        if (chunks.isEmpty()) return "(No data indexed yet.)"

        val candidates = hybrid.search(query, topK = 20)
        val reranked = reranker.rerank(query, candidates, topK = topK)
        return fuser.fuse(reranked)
    }
}

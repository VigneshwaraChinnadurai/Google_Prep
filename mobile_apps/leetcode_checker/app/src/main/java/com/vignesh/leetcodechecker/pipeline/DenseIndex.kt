package com.vignesh.leetcodechecker.pipeline

import com.vignesh.leetcodechecker.data.GeminiApi
import com.vignesh.leetcodechecker.data.GeminiContent
import com.vignesh.leetcodechecker.data.GeminiEmbedRequest
import com.vignesh.leetcodechecker.data.GeminiBatchEmbedRequest
import com.vignesh.leetcodechecker.data.GeminiPart
import kotlin.math.sqrt

/**
 * Dense vector retrieval using Gemini Embedding API.
 *
 * Ported from Python dense_retrieval.py.
 * Uses `gemini-embedding-001` for embeddings (free tier).
 * Similarity: cosine similarity.
 */
class DenseIndex(
    private val geminiApi: GeminiApi,
    private val apiKey: String,
    private val embeddingModel: String = "gemini-embedding-001"
) {
    private var chunks = listOf<PipelineChunk>()
    private var embeddings = listOf<List<Double>>()

    /**
     * Build the dense index: embed all chunks.
     */
    suspend fun build(indexChunks: List<PipelineChunk>) {
        chunks = indexChunks
        embeddings = embedBatch(
            indexChunks.map { it.text },
            task = "RETRIEVAL_DOCUMENT"
        )
        // Store embeddings back on chunks
        for (i in indexChunks.indices) {
            if (i < embeddings.size) {
                indexChunks[i].embedding = embeddings[i]
            }
        }
    }

    /**
     * Search for the top_k most similar chunks to the query.
     */
    suspend fun search(query: String, topK: Int = 20): List<PipelineSearchResult> {
        if (chunks.isEmpty()) return emptyList()

        val queryEmb = embed(query, task = "RETRIEVAL_QUERY")
        if (queryEmb.isEmpty()) return emptyList()

        val scored = embeddings.indices.map { i ->
            i to cosineSimilarity(queryEmb, embeddings[i])
        }.sortedByDescending { it.second }

        return scored
            .take(topK)
            .filter { it.second > 0.0 }
            .mapIndexed { rank, (idx, score) ->
                PipelineSearchResult(
                    chunk = chunks[idx],
                    score = score,
                    rank = rank
                )
            }
    }

    /**
     * Embed a single text.
     */
    private suspend fun embed(text: String, task: String = "RETRIEVAL_DOCUMENT"): List<Double> {
        return try {
            val request = GeminiEmbedRequest(
                model = "models/$embeddingModel",
                content = GeminiContent(parts = listOf(GeminiPart(text = text))),
                taskType = task
            )
            val response = geminiApi.embedContent(embeddingModel, apiKey, request)
            response.embedding?.values ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.w("DenseIndex", "Embed failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * Batch embed texts — up to 100 per call, with sequential fallback.
     */
    private suspend fun embedBatch(
        texts: List<String>,
        task: String = "RETRIEVAL_DOCUMENT"
    ): List<List<Double>> {
        if (texts.isEmpty()) return emptyList()

        val results = MutableList<List<Double>>(texts.size) { emptyList() }
        val batchSize = 100

        for (batchStart in texts.indices step batchSize) {
            val batchEnd = minOf(batchStart + batchSize, texts.size)
            val batch = texts.subList(batchStart, batchEnd)

            try {
                val requests = batch.map { txt ->
                    GeminiEmbedRequest(
                        model = "models/$embeddingModel",
                        content = GeminiContent(parts = listOf(GeminiPart(text = txt))),
                        taskType = task
                    )
                }
                val response = geminiApi.batchEmbedContents(
                    embeddingModel,
                    apiKey,
                    GeminiBatchEmbedRequest(requests = requests)
                )
                val embeddings = response.embeddings ?: emptyList()
                for (j in batch.indices) {
                    val idx = batchStart + j
                    results[idx] = if (j < embeddings.size) {
                        embeddings[j].values ?: emptyList()
                    } else emptyList()
                }
            } catch (e: Exception) {
                android.util.Log.w("DenseIndex", "Batch embed failed, falling back to sequential: ${e.message}")
                // Fallback to sequential
                for (j in batch.indices) {
                    results[batchStart + j] = embed(batch[j], task)
                }
            }

            // Rate limit delay between batches
            if (batchStart + batchSize < texts.size) {
                kotlinx.coroutines.delay(2000)
            }
        }

        return results
    }

    companion object {
        /**
         * Cosine similarity between two vectors.
         */
        fun cosineSimilarity(a: List<Double>, b: List<Double>): Double {
            if (a.size != b.size || a.isEmpty()) return 0.0
            var dot = 0.0
            var normA = 0.0
            var normB = 0.0
            for (i in a.indices) {
                dot += a[i] * b[i]
                normA += a[i] * a[i]
                normB += b[i] * b[i]
            }
            val denom = sqrt(normA) * sqrt(normB)
            return if (denom == 0.0) 0.0 else dot / denom
        }
    }
}

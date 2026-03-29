package com.vignesh.leetcodechecker.pipeline

import kotlin.math.ln

/**
 * BM25 sparse retrieval index — ported from Python sparse_retrieval.py.
 *
 * Okapi BM25 with parameters k1=1.5, b=0.75.
 * Tokenizer: [a-z0-9]+ regex, lowercase, stop word removal.
 */
class BM25Index(
    private val k1: Double = 1.5,
    private val b: Double = 0.75
) {
    private val STOP_WORDS = setOf(
        "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for",
        "of", "with", "by", "from", "is", "are", "was", "were", "be", "been",
        "being", "have", "has", "had", "do", "does", "did", "will", "would",
        "could", "should", "may", "might", "shall", "can", "this", "that",
        "these", "those", "it", "its", "not", "no", "as", "if", "so", "than",
        "very", "just", "about", "into", "over", "after"
    )

    private val TOKEN_REGEX = Regex("[a-z0-9]+")

    private var chunks = listOf<PipelineChunk>()
    private var docTokens = listOf<List<String>>()
    private var avgDl = 0.0
    private var idf = mapOf<String, Double>()

    /**
     * Build the BM25 index from a list of chunks.
     */
    fun build(indexChunks: List<PipelineChunk>) {
        chunks = indexChunks
        docTokens = indexChunks.map { tokenize(it.text) }
        avgDl = if (docTokens.isNotEmpty()) {
            docTokens.sumOf { it.size }.toDouble() / docTokens.size
        } else 0.0

        // Compute IDF
        val n = docTokens.size.toDouble()
        val df = mutableMapOf<String, Int>()
        for (tokens in docTokens) {
            for (term in tokens.toSet()) {
                df[term] = (df[term] ?: 0) + 1
            }
        }
        idf = df.mapValues { (_, docFreq) ->
            ln((n - docFreq + 0.5) / (docFreq + 0.5) + 1.0)
        }
    }

    /**
     * Query the index and return top_k results.
     */
    fun search(query: String, topK: Int = 20): List<PipelineSearchResult> {
        if (chunks.isEmpty()) return emptyList()

        val queryTerms = tokenize(query)
        val scores = DoubleArray(chunks.size)

        for (i in docTokens.indices) {
            val docLen = docTokens[i].size.toDouble()
            val tf = mutableMapOf<String, Int>()
            for (t in docTokens[i]) tf[t] = (tf[t] ?: 0) + 1

            var score = 0.0
            for (term in queryTerms) {
                val termIdf = idf[term] ?: continue
                val termTf = tf[term] ?: 0
                val numerator = termTf * (k1 + 1)
                val denominator = termTf + k1 * (1 - b + b * docLen / avgDl)
                score += termIdf * numerator / denominator
            }
            scores[i] = score
        }

        return scores.indices
            .sortedByDescending { scores[it] }
            .take(topK)
            .filter { scores[it] > 0.0 }
            .mapIndexed { rank, idx ->
                PipelineSearchResult(
                    chunk = chunks[idx],
                    score = scores[idx],
                    rank = rank
                )
            }
    }

    /**
     * Tokenize text: lowercase, extract [a-z0-9]+ tokens, remove stop words.
     */
    private fun tokenize(text: String): List<String> {
        return TOKEN_REGEX.findAll(text.lowercase())
            .map { it.value }
            .filter { it !in STOP_WORDS }
            .toList()
    }
}

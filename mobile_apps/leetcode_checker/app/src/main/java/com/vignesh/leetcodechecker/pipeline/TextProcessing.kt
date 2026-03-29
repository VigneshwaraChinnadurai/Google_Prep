package com.vignesh.leetcodechecker.pipeline

/**
 * Text processing utilities for the Deep Analysis pipeline.
 *
 * Ported from Python usecase_4:
 * - Chunker (chunker.py) — sentence-aware token-bounded splitting
 * - ContextFuser (context_fusion.py) — Jaccard dedup + budget assembly
 * - MetadataEnricher — regex tagging for company names
 */

// ════════════════════════════════════════════════════════════════════════
// Chunker — sentence-aware text splitter
// ════════════════════════════════════════════════════════════════════════

object Chunker {
    private val SENTENCE_SPLIT = Regex("""(?<=[.!?])\s+|\n{2,}""")

    /**
     * Estimate token count (~4 chars per token for English text).
     */
    fun estimateTokens(text: String): Int = maxOf(1, text.length / 4)

    /**
     * Split documents into token-bounded chunks with overlap.
     */
    fun chunkDocuments(
        documents: List<PipelineDocument>,
        chunkSize: Int = 480,
        overlap: Int = 60
    ): List<PipelineChunk> {
        val chunks = mutableListOf<PipelineChunk>()
        for (doc in documents) {
            val sentences = SENTENCE_SPLIT.split(doc.content).filter { it.isNotBlank() }
            var currentChunk = StringBuilder()
            var currentTokens = 0
            var chunkIdx = 0
            var charStart = 0

            for (sentence in sentences) {
                val sentenceTokens = estimateTokens(sentence)

                if (currentTokens + sentenceTokens > chunkSize && currentChunk.isNotEmpty()) {
                    // Emit current chunk
                    val text = currentChunk.toString().trim()
                    chunks.add(
                        PipelineChunk(
                            text = text,
                            chunkId = "${doc.docId}_c$chunkIdx",
                            docId = doc.docId,
                            startChar = charStart,
                            endChar = charStart + text.length,
                            metadata = doc.metadata.toMutableMap()
                        )
                    )
                    chunkIdx++

                    // Overlap: keep last ~overlap tokens worth of text
                    val overlapText = getOverlapText(currentChunk.toString(), overlap)
                    charStart += text.length - overlapText.length
                    currentChunk = StringBuilder(overlapText)
                    currentTokens = estimateTokens(overlapText)
                }

                if (currentChunk.isNotEmpty()) currentChunk.append(" ")
                currentChunk.append(sentence)
                currentTokens += sentenceTokens
            }

            // Emit remaining
            if (currentChunk.isNotEmpty()) {
                val text = currentChunk.toString().trim()
                chunks.add(
                    PipelineChunk(
                        text = text,
                        chunkId = "${doc.docId}_c$chunkIdx",
                        docId = doc.docId,
                        startChar = charStart,
                        endChar = charStart + text.length,
                        metadata = doc.metadata.toMutableMap()
                    )
                )
            }
        }
        return chunks
    }

    private fun getOverlapText(text: String, overlapTokens: Int): String {
        val overlapChars = overlapTokens * 4
        return if (text.length > overlapChars) {
            text.substring(text.length - overlapChars)
        } else text
    }
}

// ════════════════════════════════════════════════════════════════════════
// MetadataEnricher — tag chunks with company names via regex
// ════════════════════════════════════════════════════════════════════════

object MetadataEnricher {
    fun enrichChunks(
        chunks: List<PipelineChunk>,
        companyPatterns: List<Pair<String, String>>? = null
    ): List<PipelineChunk> {
        if (companyPatterns.isNullOrEmpty()) return chunks

        val patterns = companyPatterns.map { (pattern, name) ->
            Regex(pattern, RegexOption.IGNORE_CASE) to name
        }

        for (chunk in chunks) {
            val companies = mutableSetOf<String>()
            val textLower = chunk.text.lowercase()
            for ((regex, name) in patterns) {
                if (regex.containsMatchIn(chunk.text)) {
                    companies.add(name)
                }
            }
            if (companies.isNotEmpty()) {
                chunk.metadata["companies"] = companies.joinToString(",")
            }
        }
        return chunks
    }
}

// ════════════════════════════════════════════════════════════════════════
// ContextFuser — Jaccard dedup + budget assembly
// ════════════════════════════════════════════════════════════════════════

class ContextFuser(
    private val tokenBudget: Int = 6000,
    private val dedupThreshold: Double = 0.80
) {
    /**
     * Fuse search results into a budget-bounded context string.
     * Deduplicates via Jaccard similarity.
     */
    fun fuse(results: List<PipelineSearchResult>): String {
        if (results.isEmpty()) return "(No relevant context found.)"

        val selected = mutableListOf<PipelineSearchResult>()
        val selectedTexts = mutableListOf<Set<String>>()
        var totalTokens = 0

        for (result in results) {
            val words = tokenize(result.chunk.text)
            val wordsSet = words.toSet()

            // Jaccard dedup check
            val isDuplicate = selectedTexts.any { existing ->
                jaccardSimilarity(existing, wordsSet) >= dedupThreshold
            }
            if (isDuplicate) continue

            val chunkTokens = Chunker.estimateTokens(result.chunk.text)
            if (totalTokens + chunkTokens > tokenBudget) break

            selected.add(result)
            selectedTexts.add(wordsSet)
            totalTokens += chunkTokens
        }

        if (selected.isEmpty()) return "(No relevant context found.)"

        return selected.joinToString("\n\n") { sr ->
            val source = sr.chunk.metadata["source"] ?: "unknown"
            "[$source] ${sr.chunk.text}"
        }
    }

    private fun tokenize(text: String): List<String> =
        Regex("[a-z0-9]+").findAll(text.lowercase()).map { it.value }.toList()

    private fun jaccardSimilarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val intersection = a.intersect(b).size
        val union = a.union(b).size
        return if (union == 0) 0.0 else intersection.toDouble() / union
    }
}

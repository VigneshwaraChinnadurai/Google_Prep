package com.vignesh.leetcodechecker.data

import android.util.Log
import com.vignesh.leetcodechecker.pipeline.PipelineCostGuard
import java.text.SimpleDateFormat
import java.util.*

/**
 * ChatbotLogger — Comprehensive in-memory log for the Chatbot tab.
 *
 * Captures every LLM call (prompt + response), API calls, embeddings,
 * pipeline steps, session events, errors, and timing information.
 *
 * Each log entry is structured with a type, timestamp, and details.
 */

data class ChatLogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: LogEntryType,
    val title: String,
    val details: String = "",
    val durationMs: Long? = null,
    val model: String? = null,
    val promptTokensApprox: Int? = null,
    val responseTokensApprox: Int? = null
) {
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun icon(): String = when (type) {
        LogEntryType.LLM_REQUEST -> "📤"
        LogEntryType.LLM_RESPONSE -> "📥"
        LogEntryType.LLM_ERROR -> "❌"
        LogEntryType.EMBED_REQUEST -> "🔢"
        LogEntryType.EMBED_RESPONSE -> "🔢"
        LogEntryType.API_CALL -> "🌐"
        LogEntryType.PIPELINE_STEP -> "⚙️"
        LogEntryType.SESSION_EVENT -> "📂"
        LogEntryType.MODE_SWITCH -> "🔄"
        LogEntryType.COST_UPDATE -> "💰"
        LogEntryType.DATA_FETCH -> "📡"
        LogEntryType.INDEX_EVENT -> "📇"
        LogEntryType.INFO -> "ℹ️"
        LogEntryType.WARNING -> "⚠️"
        LogEntryType.ERROR -> "❌"
    }
}

enum class LogEntryType {
    LLM_REQUEST,
    LLM_RESPONSE,
    LLM_ERROR,
    EMBED_REQUEST,
    EMBED_RESPONSE,
    API_CALL,
    PIPELINE_STEP,
    SESSION_EVENT,
    MODE_SWITCH,
    COST_UPDATE,
    DATA_FETCH,
    INDEX_EVENT,
    INFO,
    WARNING,
    ERROR
}

/**
 * Thread-safe singleton logger.
 * Keeps all entries in memory (capped at MAX_ENTRIES to avoid OOM).
 */
class ChatbotLogger {
    companion object {
        private const val TAG = "ChatbotLogger"
        private const val MAX_ENTRIES = 2000
    }

    private val _entries = mutableListOf<ChatLogEntry>()
    private val lock = Any()

    /** Snapshot of all entries (newest first) */
    val entries: List<ChatLogEntry>
        get() = synchronized(lock) { _entries.toList().reversed() }

    val entryCount: Int
        get() = synchronized(lock) { _entries.size }

    fun log(entry: ChatLogEntry) {
        synchronized(lock) {
            _entries.add(entry)
            // Evict oldest when over limit
            if (_entries.size > MAX_ENTRIES) {
                _entries.removeAt(0)
            }
        }
        Log.d(TAG, "${entry.icon()} [${entry.type}] ${entry.title}")
        if (entry.details.isNotBlank()) {
            // Log first 500 chars of details to logcat
            Log.d(TAG, "   ${entry.details.take(500)}")
        }
    }

    // ── Convenience methods ────────────────────────────────────────

    fun logLlmRequest(
        model: String,
        systemPrompt: String,
        userPrompt: String,
        temperature: Double? = null,
        maxOutputTokens: Int? = null,
        responseMimeType: String? = null
    ) {
        val details = buildString {
            appendLine("MODEL: $model")
            if (temperature != null) appendLine("TEMPERATURE: $temperature")
            if (maxOutputTokens != null) appendLine("MAX_OUTPUT_TOKENS: $maxOutputTokens")
            if (responseMimeType != null) appendLine("RESPONSE_TYPE: $responseMimeType")
            appendLine()
            if (systemPrompt.isNotBlank()) {
                appendLine("═══ SYSTEM PROMPT ═══")
                appendLine(systemPrompt)
                appendLine()
            }
            appendLine("═══ USER PROMPT ═══")
            appendLine(userPrompt)
        }
        log(ChatLogEntry(
            type = LogEntryType.LLM_REQUEST,
            title = "LLM Request → $model",
            details = details,
            model = model,
            promptTokensApprox = (systemPrompt.length + userPrompt.length) / 4
        ))
    }

    fun logLlmResponse(
        model: String,
        responseText: String,
        durationMs: Long,
        finishReason: String? = null
    ) {
        val details = buildString {
            appendLine("MODEL: $model")
            appendLine("DURATION: ${durationMs}ms")
            if (finishReason != null) appendLine("FINISH_REASON: $finishReason")
            appendLine("RESPONSE_LENGTH: ${responseText.length} chars (~${responseText.length / 4} tokens)")
            appendLine()
            appendLine("═══ RESPONSE ═══")
            appendLine(responseText)
        }
        log(ChatLogEntry(
            type = LogEntryType.LLM_RESPONSE,
            title = "LLM Response ← $model (${durationMs}ms)",
            details = details,
            durationMs = durationMs,
            model = model,
            responseTokensApprox = responseText.length / 4
        ))
    }

    fun logLlmError(model: String, error: String, durationMs: Long? = null) {
        log(ChatLogEntry(
            type = LogEntryType.LLM_ERROR,
            title = "LLM Error ← $model",
            details = "MODEL: $model\nERROR: $error\n${if (durationMs != null) "DURATION: ${durationMs}ms" else ""}",
            durationMs = durationMs,
            model = model
        ))
    }

    fun logEmbedRequest(model: String, textCount: Int, totalChars: Int) {
        log(ChatLogEntry(
            type = LogEntryType.EMBED_REQUEST,
            title = "Embed Request → $model ($textCount texts)",
            details = "MODEL: $model\nTEXTS: $textCount\nTOTAL_CHARS: $totalChars",
            model = model
        ))
    }

    fun logEmbedResponse(model: String, embeddingCount: Int, durationMs: Long) {
        log(ChatLogEntry(
            type = LogEntryType.EMBED_RESPONSE,
            title = "Embed Response ← $model ($embeddingCount embeddings, ${durationMs}ms)",
            details = "MODEL: $model\nEMBEDDINGS: $embeddingCount\nDURATION: ${durationMs}ms",
            durationMs = durationMs,
            model = model
        ))
    }

    fun logPipelineStep(step: String, message: String) {
        log(ChatLogEntry(
            type = LogEntryType.PIPELINE_STEP,
            title = step,
            details = message
        ))
    }

    fun logSessionEvent(event: String, details: String = "") {
        log(ChatLogEntry(
            type = LogEntryType.SESSION_EVENT,
            title = event,
            details = details
        ))
    }

    fun logModeSwitch(mode: String) {
        log(ChatLogEntry(
            type = LogEntryType.MODE_SWITCH,
            title = "Mode → $mode"
        ))
    }

    fun logCostUpdate(totalCost: Double, apiCalls: Int, callCost: Double? = null) {
        log(ChatLogEntry(
            type = LogEntryType.COST_UPDATE,
            title = "Cost: \$${String.format("%.6f", totalCost)} ($apiCalls calls)",
            details = buildString {
                if (callCost != null) appendLine("THIS_CALL_COST: \$${String.format("%.6f", callCost)}")
                appendLine("TOTAL_COST: \$${String.format("%.6f", totalCost)}")
                appendLine("API_CALLS: $apiCalls")
            }
        ))
    }

    fun logDataFetch(source: String, details: String = "") {
        log(ChatLogEntry(
            type = LogEntryType.DATA_FETCH,
            title = "Fetch: $source",
            details = details
        ))
    }

    fun logIndexEvent(event: String, details: String = "") {
        log(ChatLogEntry(
            type = LogEntryType.INDEX_EVENT,
            title = event,
            details = details
        ))
    }

    fun logInfo(message: String, details: String = "") {
        log(ChatLogEntry(
            type = LogEntryType.INFO,
            title = message,
            details = details
        ))
    }

    fun logWarning(message: String, details: String = "") {
        log(ChatLogEntry(
            type = LogEntryType.WARNING,
            title = message,
            details = details
        ))
    }

    fun logError(message: String, details: String = "") {
        log(ChatLogEntry(
            type = LogEntryType.ERROR,
            title = message,
            details = details
        ))
    }

    fun clear() {
        synchronized(lock) { _entries.clear() }
        Log.d(TAG, "Log cleared")
    }

    /** Export all logs as a single formatted string (for sharing/copying) */
    fun exportAsText(): String = buildString {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        appendLine("═══════════════════════════════════════════")
        appendLine("  Chatbot Logs — Exported ${sdf.format(Date())}")
        appendLine("  Total entries: $entryCount")
        appendLine("═══════════════════════════════════════════")
        appendLine()
        synchronized(lock) {
            for (entry in _entries) {
                appendLine("${entry.icon()} [${sdf.format(Date(entry.timestamp))}] [${entry.type}]")
                appendLine("   ${entry.title}")
                if (entry.durationMs != null) appendLine("   Duration: ${entry.durationMs}ms")
                if (entry.details.isNotBlank()) {
                    entry.details.lines().forEach { line ->
                        appendLine("   $line")
                    }
                }
                appendLine()
            }
        }
    }
}

/**
 * LoggingGeminiApi — Transparent wrapper around GeminiApi that logs
 * every LLM call, embedding call, and their responses.
 *
 * Pass this instead of the raw Retrofit GeminiApi to capture all calls
 * across the entire pipeline (ViewModel + Agents + RetrievalPipeline).
 */
class LoggingGeminiApi(
    private val delegate: GeminiApi,
    private val logger: ChatbotLogger
) : GeminiApi {

    /**
     * Optional cost guard — when set, every generateContent call
     * automatically records token usage from Gemini's usageMetadata.
     * Set this before running the pipeline so costs are tracked.
     */
    var costGuard: PipelineCostGuard? = null

    override suspend fun listModels(apiKey: String): GeminiModelListResponse {
        logger.logInfo("Listing available Gemini models")
        val start = System.currentTimeMillis()
        val result = delegate.listModels(apiKey)
        val elapsed = System.currentTimeMillis() - start
        val count = result.models?.size ?: 0
        logger.logInfo("Listed $count models (${elapsed}ms)")
        return result
    }

    override suspend fun generateContent(
        model: String,
        apiKey: String,
        body: GeminiGenerateRequest
    ): GeminiGenerateResponse {
        // Extract prompt info for logging
        val systemPrompt = body.systemInstruction?.parts
            ?.mapNotNull { it.text }?.joinToString("") ?: ""
        val userPrompt = body.contents.flatMap { it.parts.orEmpty() }
            .mapNotNull { it.text }.joinToString("\n")
        val config = body.generationConfig

        logger.logLlmRequest(
            model = model,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            temperature = config?.temperature,
            maxOutputTokens = config?.maxOutputTokens,
            responseMimeType = config?.responseMimeType
        )

        val start = System.currentTimeMillis()
        return try {
            val response = delegate.generateContent(model, apiKey, body)
            val elapsed = System.currentTimeMillis() - start

            val responseText = response.candidates?.firstOrNull()?.content?.parts
                ?.mapNotNull { it.text }?.joinToString("") ?: ""
            val finishReason = response.candidates?.firstOrNull()?.finishReason

            logger.logLlmResponse(
                model = model,
                responseText = responseText,
                durationMs = elapsed,
                finishReason = finishReason
            )

            // ── Auto-record cost from actual Gemini usage metadata ──
            response.usageMetadata?.let { meta ->
                val inputTokens = meta.promptTokenCount ?: 0
                val outputTokens = meta.candidatesTokenCount ?: 0
                costGuard?.let { guard ->
                    val callCost = guard.record(inputTokens, outputTokens, model)
                    logger.logCostUpdate(
                        totalCost = guard.totalCostUsd,
                        apiCalls = guard.apiCalls,
                        callCost = callCost
                    )
                }
            }

            response
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - start
            logger.logLlmError(model, "${e.javaClass.simpleName}: ${e.message}", elapsed)
            throw e
        }
    }

    override suspend fun generateContentRaw(
        model: String,
        apiKey: String,
        body: GeminiGenerateRequest
    ): okhttp3.ResponseBody {
        val userPrompt = body.contents.flatMap { it.parts.orEmpty() }
            .mapNotNull { it.text }.joinToString("\n")
        logger.logLlmRequest(
            model = model,
            systemPrompt = body.systemInstruction?.parts?.mapNotNull { it.text }?.joinToString("") ?: "",
            userPrompt = userPrompt,
            responseMimeType = "RAW"
        )
        val start = System.currentTimeMillis()
        return try {
            val result = delegate.generateContentRaw(model, apiKey, body)
            val elapsed = System.currentTimeMillis() - start
            logger.logLlmResponse(model, "(raw response body)", elapsed)
            result
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - start
            logger.logLlmError(model, "${e.javaClass.simpleName}: ${e.message}", elapsed)
            throw e
        }
    }

    override suspend fun embedContent(
        model: String,
        apiKey: String,
        body: GeminiEmbedRequest
    ): GeminiEmbedResponse {
        val textLen = body.content?.parts?.mapNotNull { it.text }?.sumOf { it.length } ?: 0
        logger.logEmbedRequest(model, 1, textLen)
        val start = System.currentTimeMillis()
        return try {
            val result = delegate.embedContent(model, apiKey, body)
            val elapsed = System.currentTimeMillis() - start
            val dims = result.embedding?.values?.size ?: 0
            logger.logEmbedResponse(model, 1, elapsed)
            logger.logInfo("Embedding dimensions: $dims")
            result
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - start
            logger.logLlmError(model, "Embed error: ${e.message}", elapsed)
            throw e
        }
    }

    override suspend fun batchEmbedContents(
        model: String,
        apiKey: String,
        body: GeminiBatchEmbedRequest
    ): GeminiBatchEmbedResponse {
        val textCount = body.requests?.size ?: 0
        val totalChars = body.requests?.sumOf { req ->
            req.content?.parts?.mapNotNull { it.text }?.sumOf { it.length } ?: 0
        } ?: 0
        logger.logEmbedRequest(model, textCount, totalChars)
        val start = System.currentTimeMillis()
        return try {
            val result = delegate.batchEmbedContents(model, apiKey, body)
            val elapsed = System.currentTimeMillis() - start
            val count = result.embeddings?.size ?: 0
            logger.logEmbedResponse(model, count, elapsed)
            result
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - start
            logger.logLlmError(model, "Batch embed error: ${e.message}", elapsed)
            throw e
        }
    }
}

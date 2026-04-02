package com.vignesh.leetcodechecker.data

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.vignesh.leetcodechecker.AppSettings
import com.vignesh.leetcodechecker.AppSettingsStore
import com.vignesh.leetcodechecker.BuildConfig
import com.vignesh.leetcodechecker.llm.LlmBackend
import com.vignesh.leetcodechecker.llm.LlmConfig
import com.vignesh.leetcodechecker.llm.LlmProviderFactory
import com.vignesh.leetcodechecker.llm.LlmResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * OllamaRepository — Handles all Ollama-related operations:
 * - Ollama LLM generation for LeetCode problems
 * - Model management (list installed, browse catalog, download)
 * - Connectivity diagnostics
 *
 * Reuses the existing LeetCodeApi for fetching daily challenges,
 * and uses OllamaApi for local/remote Ollama server calls.
 */
private const val TAG = "OllamaRepository"
private const val APPROX_CHARS_PER_TOKEN = 4

class OllamaRepository(private val context: Context) {

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val leetCodeApi: LeetCodeApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://leetcode.com/")
            .client(createHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(LeetCodeApi::class.java)
    }

    private fun ollamaApi(): OllamaApi {
        return Retrofit.Builder()
            .baseUrl(resolveOllamaBaseUrl())
            .client(createHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OllamaApi::class.java)
    }

    private val ollamaResponseAdapter by lazy {
        moshi.adapter(OllamaGenerateResponse::class.java)
    }

    private val ollamaPullEventAdapter by lazy {
        moshi.adapter(OllamaPullStreamEvent::class.java)
    }

    private val ollamaRequestAdapter by lazy {
        moshi.adapter(OllamaGenerateRequest::class.java)
    }

    private val catalogApi: OllamaCatalogApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://ollama.com/")
            .client(createHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OllamaCatalogApi::class.java)
    }

    private val answerCache = mutableMapOf<String, AiGenerationResult>()
    private val _liveDebugLog = MutableStateFlow("")
    val liveDebugLog: StateFlow<String> = _liveDebugLog.asStateFlow()

    private fun loadSettings(): AppSettings = AppSettingsStore.load(context)

    private fun createHttpClient(): OkHttpClient {
        val timeoutMinutes = loadSettings().networkTimeoutMinutes.coerceIn(1, 60).toLong()
        return OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(timeoutMinutes, TimeUnit.MINUTES)
            .readTimeout(timeoutMinutes, TimeUnit.MINUTES)
            .writeTimeout(timeoutMinutes, TimeUnit.MINUTES)
            .callTimeout(timeoutMinutes, TimeUnit.MINUTES)
            .build()
    }

    fun defaultConfiguredModel(): String {
        return BuildConfig.OLLAMA_MODEL.trim().ifBlank { "qwen2.5:3b" }
    }

    /**
     * Create LlmConfig from current settings
     */
    private fun createLlmConfig(settings: AppSettings): LlmConfig {
        val backend = when (settings.ollamaBackend.lowercase()) {
            "local" -> LlmBackend.LOCAL
            else -> LlmBackend.OLLAMA
        }
        return LlmConfig(
            backend = backend,
            ollamaBaseUrl = settings.ollamaBaseUrl.ifBlank { BuildConfig.OLLAMA_BASE_URL },
            ollamaModel = settings.ollamaPreferredModels.split(',').firstOrNull()?.trim()
                ?: defaultConfiguredModel(),
            localModelPath = settings.localModelPath,
            localContextSize = settings.localContextSize.coerceIn(512, 8192),
            localMaxTokens = settings.localMaxTokens.coerceIn(64, 4096),
            networkTimeoutMinutes = settings.networkTimeoutMinutes.coerceIn(1, 60)
        )
    }

    /**
     * Check if local LLM backend is available
     */
    fun isLocalBackendAvailable(): Boolean = LlmProviderFactory.isLocalAvailable()

    /**
     * Safely check if local backend can be used without risking native crashes.
     * This checks if native library is loaded without calling any JNI functions.
     */
    private fun isLocalBackendSafe(): Boolean {
        return try {
            LlmProviderFactory.isLocalAvailable()
        } catch (e: Exception) {
            Log.e(TAG, "isLocalBackendSafe check failed", e)
            false
        } catch (e: Error) {
            Log.e(TAG, "isLocalBackendSafe check failed with Error", e)
            false
        }
    }

    /**
     * Fall back to Ollama HTTP backend for generation.
     */
    private suspend fun fallbackToOllamaHttp(
        debug: StringBuilder,
        preferredModels: List<String>,
        boundedSystem: String,
        boundedUser: String,
        maxModelRetries: Int,
        maxOutputTokens: Int
    ): String {
        logDebug(debug, "Using OLLAMA backend (HTTP)")
        
        val availableModels = runCatching {
            ollamaApi().listTags().models.orEmpty()
                .mapNotNull { it.name?.trim() }
                .filter { it.isNotBlank() }
        }.getOrDefault(emptyList())

        val selectedModel = preferredModels.firstOrNull { preferred ->
            availableModels.any { it.equals(preferred, ignoreCase = true) }
        } ?: preferredModels.first()

        logDebug(debug, "Selected model: $selectedModel")
        ensureModelAvailable(selectedModel, debug)
        
        return try {
            generateWithRetry(
                model = selectedModel,
                systemPrompt = boundedSystem,
                userPrompt = boundedUser,
                maxModelRetries = maxModelRetries,
                maxOutputTokens = maxOutputTokens,
                debug = debug
            )
        } catch (error: Throwable) {
            logDebug(debug, "Model failed: $selectedModel -> ${error.message}")
            throw PipelineException(
                message = error.message ?: "Ollama generation failed.",
                debugLog = debug.toString().trim()
            )
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Model Management
    // ════════════════════════════════════════════════════════════════════════

    suspend fun listAvailableModels(): Result<List<OllamaModelInfo>> {
        return runCatching {
            ollamaApi().listTags().models.orEmpty()
                .mapNotNull { tag ->
                    val trimmed = tag.name?.trim().orEmpty()
                    if (trimmed.isBlank()) null else OllamaModelInfo(trimmed, tag.size)
                }
                .distinctBy { it.name.lowercase() }
                .sortedBy { it.name.lowercase() }
        }
    }

    suspend fun listCatalogModels(): Result<List<OllamaModelInfo>> {
        return runCatching {
            catalogApi.listCatalogTags().models.orEmpty()
                .mapNotNull { tag ->
                    val trimmed = tag.name?.trim().orEmpty()
                    if (trimmed.isBlank()) null else OllamaModelInfo(trimmed, tag.size)
                }
                .distinctBy { it.name.lowercase() }
                .sortedBy { it.name.lowercase() }
        }
    }

    suspend fun downloadModel(model: String, onProgress: (String) -> Unit): Result<String> {
        val trimmed = model.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("Model name cannot be blank."))
        }

        return runCatching {
            onProgress("Starting download for '$trimmed'...")

            withContext(Dispatchers.IO) {
                ollamaApi().pullModelStream(
                    OllamaPullRequest(model = trimmed, stream = true)
                ).use { responseBody ->
                    responseBody.charStream().buffered().useLines { lines ->
                        lines.forEach { line ->
                            if (line.isBlank()) return@forEach
                            val event = runCatching { ollamaPullEventAdapter.fromJson(line) }.getOrNull()
                            val status = event?.status.orEmpty().ifBlank { "downloading" }
                            val errorText = event?.error.orEmpty()
                            if (errorText.isNotBlank()) {
                                throw IllegalStateException("Ollama pull failed: $errorText")
                            }

                            val completed = event?.completed ?: 0L
                            val total = event?.total ?: 0L
                            if (total > 0L && completed in 0..total) {
                                val percent = ((completed * 100.0) / total).toInt().coerceIn(0, 100)
                                onProgress("$status ($percent%)")
                            } else {
                                onProgress(status)
                            }
                        }
                    }
                }
            }

            val namesAfterPull = ollamaApi().listTags().models.orEmpty()
                .mapNotNull { it.name }
                .map { normalizeModelName(it) }

            val normalized = normalizeModelName(trimmed)
            if (namesAfterPull.none { it == normalized }) {
                error("Model '$trimmed' is still unavailable after pull.")
            }

            onProgress("Download completed.")
            "downloaded"
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Diagnostics
    // ════════════════════════════════════════════════════════════════════════

    suspend fun runOllamaConnectionDiagnostics(): String {
        val report = StringBuilder()
        val baseUrl = resolveOllamaBaseUrl()
        val host = runCatching { URI(baseUrl).host.orEmpty() }.getOrDefault("")

        report.appendLine("[${formatTimestamp(System.currentTimeMillis())}] Ollama diagnostics started")
        report.appendLine("Configured Ollama base URL: $baseUrl")

        if (host == "127.0.0.1" || host.equals("localhost", ignoreCase = true)) {
            report.appendLine("Detected localhost/loopback base URL.")
            report.appendLine("If Ollama runs on PC, keep adb reverse active: adb reverse tcp:11434 tcp:11434")
        }

        return runCatching {
            val tags = ollamaApi().listTags().models.orEmpty()
                .mapNotNull { it.name?.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()

            report.appendLine("Connectivity: SUCCESS")
            report.appendLine("Model count: ${tags.size}")
            if (tags.isNotEmpty()) {
                report.appendLine("Available models: ${tags.joinToString(", ")}")
            } else {
                report.appendLine("No models found. Run /api/pull or pull from Ollama CLI on server.")
            }
            report.appendLine("Diagnostics completed successfully.")
            report.toString().trim()
        }.getOrElse { error ->
            report.appendLine("Connectivity: FAILED")
            report.appendLine("Error: ${error.message.orEmpty()}")
            report.appendLine("Troubleshooting:")
            buildTroubleshootingHints(baseUrl, host, error)
                .forEach { hint -> report.appendLine("- $hint") }
            report.toString().trim()
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // LeetCode Challenge Fetch (reuses same LeetCode GraphQL API)
    // ════════════════════════════════════════════════════════════════════════

    suspend fun fetchDailyChallenge(): Result<DailyChallengeUiModel> {
        return runCatching {
            val dailyQuery = """
                query questionOfToday {
                  activeDailyCodingChallengeQuestion {
                    date
                    link
                    question {
                      title
                      titleSlug
                      difficulty
                      questionFrontendId
                      topicTags { name slug }
                    }
                  }
                }
            """.trimIndent()

            val dailyResponse = leetCodeApi.postQuery(GraphQLRequest(query = dailyQuery))
            val daily = dailyResponse.data?.activeDailyCodingChallengeQuestion
                ?: error("Daily challenge data not available")

            val detailQuery = """
                query questionContent(${'$'}titleSlug: String!) {
                  question(titleSlug: ${'$'}titleSlug) {
                    content
                    exampleTestcases
                    codeSnippets { lang langSlug code }
                  }
                }
            """.trimIndent()

            val detailsResponse = leetCodeApi.postQuestionDetails(
                GraphQLRequest(
                    query = detailQuery,
                    variables = mapOf("titleSlug" to daily.question.titleSlug)
                )
            )

            val questionDetails = detailsResponse.data?.question
                ?: error("Question details are not available")

            // Keep raw HTML for proper rendering
            val rawHtmlContent = questionDetails.content.orEmpty()

            // Plain text for preview and search
            val plainTextContent = rawHtmlContent
                .replace(Regex("<[^>]*>"), " ")
                .replace("&nbsp;", " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            val pythonStarter = questionDetails.codeSnippets
                ?.firstOrNull { it.langSlug.equals("python3", ignoreCase = true) }
                ?.code
                .orEmpty()

            val exampleTestcases = questionDetails.exampleTestcases?.trim().orEmpty()

            DailyChallengeUiModel(
                date = daily.date,
                title = daily.question.title,
                titleSlug = daily.question.titleSlug,
                difficulty = daily.question.difficulty,
                questionId = daily.question.questionFrontendId,
                tags = daily.question.topicTags.map { it.name },
                url = "https://leetcode.com${daily.link}",
                descriptionPreview = plainTextContent.take(500),
                fullStatement = plainTextContent,
                htmlContent = rawHtmlContent,
                pythonStarterCode = pythonStarter,
                exampleTestcases = exampleTestcases
            )
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Ollama Answer Generation
    // ════════════════════════════════════════════════════════════════════════

    suspend fun generateDetailedAnswer(
        challenge: DailyChallengeUiModel,
        forceRefresh: Boolean = false
    ): Result<AiGenerationResult> {
        return runCatching {
            val settings = loadSettings()
            val maxModelRetries = settings.maxModelRetries.coerceIn(1, 10)
            val maxInputTokens = settings.maxInputTokens.coerceIn(1_024, 2_000_000)
            val maxOutputTokens = settings.maxOutputTokens.coerceIn(256, 65_535)
            val promptName = settings.promptName.ifBlank { "Prompt for Leetcode_solver" }
            val preferredModels = settings.ollamaPreferredModels
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .ifEmpty { listOf(defaultConfiguredModel()) }

            val debug = StringBuilder()
            _liveDebugLog.value = ""

            val cacheKey = challenge.titleSlug
            if (!forceRefresh) {
                answerCache[cacheKey]?.let {
                    logDebug(debug, "Cache hit for ${challenge.titleSlug}")
                    return@runCatching it.copy(debugLog = mergeDebugLogs(it.debugLog, debug.toString()))
                }
            }

            if (forceRefresh) {
                answerCache.remove(cacheKey)
                logDebug(debug, "Forcing refresh for ${challenge.titleSlug}")
            }

            logDebug(debug, "Starting LLM pipeline for ${challenge.titleSlug}")
            logDebug(debug, "Token limits: maxInput=$maxInputTokens, maxOutput=$maxOutputTokens")
            logDebug(debug, "Backend: ${settings.ollamaBackend}")

            val systemPrompt = """
You are LC-Ollama-Solver ($promptName).
Return only these tags in order:
<leetcode_python3_code>...</leetcode_python3_code>
<testcase_validation>...</testcase_validation>
<explanation>...</explanation>
Hard rules:
- Python 3 only, LeetCode-ready, class Solution style.
- Match provided starter signature and method args.
- No markdown fences.
- Prefer optimal approach; include complexity and edge cases.
- Validate against provided testcases.
""".trimIndent()

            val userPrompt = """
Date=${challenge.date}
Id=${challenge.questionId}
Title=${challenge.title}
Difficulty=${challenge.difficulty}
Tags=${challenge.tags.joinToString()}
URL=${challenge.url}

Statement:
${challenge.fullStatement.ifBlank { challenge.descriptionPreview }}

StarterCode:
${challenge.pythonStarterCode.ifBlank { "class Solution:\n    pass" }}

Testcases:
${challenge.exampleTestcases.ifBlank { "Not provided" }}
""".trimIndent()

            val boundedSystem = truncateToApproxTokenLimit(systemPrompt, maxInputTokens / 2)
            val boundedUser = truncateToApproxTokenLimit(userPrompt, maxInputTokens / 2)

            // Check if using local llama.cpp backend
            val llmConfig = createLlmConfig(settings)
            
            // Validate local backend before attempting to use it
            val useLocalBackend = llmConfig.backend == LlmBackend.LOCAL 
                && llmConfig.localModelPath.isNotBlank()
                && isLocalBackendSafe()
            
            val generatedText = if (useLocalBackend) {
                logDebug(debug, "Using LOCAL backend (llama.cpp)")
                logDebug(debug, "Model path: ${llmConfig.localModelPath}")
                
                try {
                    val provider = LlmProviderFactory.create(llmConfig)
                    val result = provider.generate(
                        systemPrompt = boundedSystem,
                        userPrompt = boundedUser,
                        maxTokens = llmConfig.localMaxTokens,
                        temperature = 0.2f
                    )
                
                when (result) {
                    is LlmResult.Success -> {
                        logDebug(debug, "Local generation completed (${result.response.length} chars)")
                        result.response
                    }
                    is LlmResult.Error -> {
                        logDebug(debug, "Local generation failed: ${result.message}")
                        throw PipelineException(
                            message = result.message,
                            debugLog = debug.toString().trim()
                        )
                    }
                }
                } catch (e: Exception) {
                    // Native library crash - fall back to Ollama HTTP
                    logDebug(debug, "Local backend crashed, falling back to Ollama HTTP: ${e.message}")
                    Log.e(TAG, "Local backend exception", e)
                    fallbackToOllamaHttp(debug, preferredModels, boundedSystem, boundedUser, maxModelRetries, maxOutputTokens)
                }
            } else {
                // Backend is not LOCAL, or LOCAL not available - use Ollama HTTP
                if (llmConfig.backend == LlmBackend.LOCAL) {
                    logDebug(debug, "LOCAL backend configured but not available - falling back to Ollama HTTP")
                    if (llmConfig.localModelPath.isBlank()) {
                        logDebug(debug, "Reason: No model path configured")
                    } else if (!isLocalBackendSafe()) {
                        logDebug(debug, "Reason: Native library not available")
                    }
                }
                
                fallbackToOllamaHttp(debug, preferredModels, boundedSystem, boundedUser, maxModelRetries, maxOutputTokens)
            }

            if (generatedText.isBlank()) {
                logDebug(debug, "Model returned empty text")
                throw PipelineException(
                    message = "Ollama returned an empty response.",
                    debugLog = debug.toString().trim()
                )
            }

            val code = extractTaggedSection(generatedText, "leetcode_python3_code")
            val validation = extractTaggedSection(generatedText, "testcase_validation")
            val explanation = extractTaggedSection(generatedText, "explanation")

            val result = AiGenerationResult(
                leetcodePythonCode = code.ifBlank { generatedText }.trim(),
                testcaseValidation = validation.trim(),
                explanation = explanation.trim(),
                rawResponse = generatedText,
                debugLog = debug.toString().trim()
            )

            answerCache[cacheKey] = result
            result
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Internal Helpers
    // ════════════════════════════════════════════════════════════════════════

    private suspend fun generateWithRetry(
        model: String,
        systemPrompt: String,
        userPrompt: String,
        maxModelRetries: Int,
        maxOutputTokens: Int,
        debug: StringBuilder
    ): String {
        repeat(maxModelRetries) { index ->
            val attempt = index + 1
            try {
                val request = OllamaGenerateRequest(
                    model = model,
                    system = systemPrompt,
                    prompt = userPrompt,
                    stream = false,
                    options = OllamaOptions(temperature = 0.2, numPredict = maxOutputTokens)
                )

                logDebug(debug, "$model attempt $attempt")
                val startedAt = System.currentTimeMillis()
                val rawResponse = ollamaApi().generateRaw(request)
                val elapsed = System.currentTimeMillis() - startedAt
                logDebug(debug, "$model response received in ${elapsed}ms")

                val responseJson = rawResponse.string()
                logDebug(debug, "$model raw response: $responseJson")

                val parsed = ollamaResponseAdapter.fromJson(responseJson)
                    ?: throw PipelineException("Unreadable Ollama JSON.", debug.toString().trim())

                if (!parsed.error.isNullOrBlank()) {
                    throw PipelineException("Ollama error: ${parsed.error}", debug.toString().trim())
                }

                val output = parsed.response.orEmpty().trim()
                if (output.isNotBlank()) {
                    logDebug(debug, "$model output received (${output.length} chars)")
                    return output
                }

                if (attempt < maxModelRetries) {
                    delay((1_500L * attempt).coerceAtMost(6_000L))
                    return@repeat
                }

                throw PipelineException(
                    "Ollama returned no output after retries.",
                    debug.toString().trim()
                )
            } catch (error: PipelineException) {
                throw error
            } catch (error: HttpException) {
                val errorBody = error.response()?.errorBody()?.string().orEmpty()
                if (errorBody.isNotBlank()) logDebug(debug, "$model HTTP ${error.code()}: $errorBody")
                if (attempt < maxModelRetries) delay((2_000L * attempt).coerceAtMost(8_000L))
            } catch (error: IOException) {
                logDebug(debug, "$model network error: ${error.message}")
                if (attempt < maxModelRetries) delay((2_000L * attempt).coerceAtMost(8_000L))
            } catch (error: Throwable) {
                logDebug(debug, "$model attempt $attempt failed: ${error.message}")
                if (attempt < maxModelRetries) delay((2_000L * attempt).coerceAtMost(8_000L))
            }
        }

        throw PipelineException(
            "Failed after $maxModelRetries retries for model '$model'.",
            debug.toString().trim()
        )
    }

    private suspend fun ensureModelAvailable(model: String, debug: StringBuilder) {
        val normalized = normalizeModelName(model)
        logDebug(debug, "Checking model availability: $normalized")

        val tags = runCatching { ollamaApi().listTags() }
            .getOrElse { error ->
                throw PipelineException(
                    "Unable to query Ollama tags at ${resolveOllamaBaseUrl()}. ${error.message}",
                    debug.toString().trim()
                )
            }

        val localNames = tags.models.orEmpty().mapNotNull { it.name }.map { normalizeModelName(it) }
        if (localNames.any { it == normalized }) {
            logDebug(debug, "Model already present: $normalized")
            return
        }

        logDebug(debug, "Model not present. Auto-pulling: $normalized")
        val pullResponse = runCatching {
            ollamaApi().pullModel(OllamaPullRequest(model = model, stream = false))
        }.getOrElse { error ->
            throw PipelineException("Failed to pull '$model': ${error.message}", debug.toString().trim())
        }

        if (!pullResponse.error.isNullOrBlank()) {
            throw PipelineException("Pull failed for '$model': ${pullResponse.error}", debug.toString().trim())
        }

        logDebug(debug, "Pull status: ${pullResponse.status.orEmpty().ifBlank { "unknown" }}")
    }

    private fun extractTaggedSection(text: String, tag: String): String {
        val startTag = "<$tag>"
        val endTag = "</$tag>"
        val start = text.indexOf(startTag)
        val end = text.indexOf(endTag)
        if (start == -1 || end == -1 || end <= start) return ""
        return text.substring(start + startTag.length, end).trim()
    }

    private fun logDebug(debug: StringBuilder, message: String) {
        val line = "[${formatTimestamp(System.currentTimeMillis())}] $message"
        debug.appendLine(line)
        Log.d(TAG, line)
        _liveDebugLog.value = debug.toString().trim()
    }

    private fun formatTimestamp(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(millis))

    private fun mergeDebugLogs(existing: String, additional: String): String {
        if (existing.isBlank()) return additional
        if (additional.isBlank()) return existing
        return "$existing\n$additional"
    }

    private fun truncateToApproxTokenLimit(text: String, maxTokens: Int): String {
        if (maxTokens <= 0) return ""
        val maxChars = (maxTokens.toLong() * APPROX_CHARS_PER_TOKEN).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return if (text.length <= maxChars) text else text.take(maxChars)
    }

    private fun resolveOllamaBaseUrl(): String {
        val settingsUrl = loadSettings().ollamaBaseUrl.trim()
        val buildUrl = BuildConfig.OLLAMA_BASE_URL.trim()
        val url = settingsUrl.ifBlank { buildUrl }
        return if (url.endsWith("/")) url else "$url/"
    }

    private fun normalizeModelName(model: String): String =
        model.trim().lowercase().removePrefix("models/")

    private fun buildTroubleshootingHints(baseUrl: String, host: String, error: Throwable): List<String> {
        val hints = mutableListOf<String>()
        val msg = error.message.orEmpty().lowercase()

        if (msg.contains("cleartext") || msg.contains("network security policy")) {
            hints += "HTTP cleartext blocked. Ensure network_security_config allows cleartext for Ollama host."
        }

        if (host == "127.0.0.1" || host.equals("localhost", ignoreCase = true)) {
            hints += "For phone → PC: run 'adb reverse tcp:11434 tcp:11434'"
            hints += "For another host: set Ollama Base URL to http://<ip>:11434/"
        } else {
            hints += "Verify $baseUrl is reachable from phone (same Wi-Fi)."
            hints += "Allow TCP 11434 on server firewall."
            hints += "Run Ollama with: OLLAMA_HOST=0.0.0.0:11434"
        }

        hints += "Confirm Ollama is running: curl http://<ip>:11434/api/tags"
        return hints.distinct()
    }

    // ════════════════════════════════════════════════════════════════════════
    // AI Interview Prep Functions
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Generate an initial interview question for a given topic
     */
    suspend fun generateInterviewQuestion(prompt: String): String = withContext(Dispatchers.IO) {
        val settings = loadSettings()
        val model = settings.ollamaPreferredModels.split(',').firstOrNull()?.trim()
            ?: defaultConfiguredModel()
        
        try {
            ensureModelAvailable(model, StringBuilder())
            
            val request = OllamaGenerateRequest(
                model = model,
                prompt = prompt,
                system = "You are a senior software engineer conducting a technical interview. Be professional, clear, and helpful.",
                stream = false,
                options = OllamaOptions(numPredict = 500)
            )
            
            val response = ollamaApi().generate(request)
            response.response ?: throw Exception("No response from Ollama")
        } catch (e: Exception) {
            Log.e(TAG, "Interview question generation failed", e)
            throw e
        }
    }

    /**
     * Conduct an interview with context from previous messages
     */
    suspend fun conductInterview(
        topic: String,
        history: List<com.vignesh.leetcodechecker.ui.InterviewMessage>,
        userAnswer: String
    ): String = withContext(Dispatchers.IO) {
        val settings = loadSettings()
        val model = settings.ollamaPreferredModels.split(',').firstOrNull()?.trim()
            ?: defaultConfiguredModel()
        
        try {
            ensureModelAvailable(model, StringBuilder())
            
            // Build context from history
            val contextBuilder = StringBuilder()
            contextBuilder.append("Interview topic: $topic\n\n")
            contextBuilder.append("Previous conversation:\n")
            history.takeLast(6).forEach { msg ->
                val role = if (msg.isUser) "Candidate" else "Interviewer"
                contextBuilder.append("$role: ${msg.content}\n\n")
            }
            contextBuilder.append("Candidate's answer: $userAnswer\n\n")
            contextBuilder.append("As the interviewer, provide brief feedback on the answer and ask a follow-up question.")
            
            val request = OllamaGenerateRequest(
                model = model,
                prompt = contextBuilder.toString(),
                system = """You are a senior software engineer conducting a technical interview about $topic.
                    |Guidelines:
                    |- Provide constructive feedback on answers
                    |- Ask follow-up questions to probe deeper
                    |- Be encouraging but also point out areas for improvement
                    |- Keep responses concise (2-3 paragraphs max)
                    |- If the candidate seems stuck, provide hints""".trimMargin(),
                stream = false,
                options = OllamaOptions(numPredict = 400)
            )
            
            val response = ollamaApi().generate(request)
            response.response ?: throw Exception("No response from Ollama")
        } catch (e: Exception) {
            Log.e(TAG, "Interview response generation failed", e)
            throw e
        }
    }
}


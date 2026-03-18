package com.vignesh.leetcodechecker.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.vignesh.leetcodechecker.BuildConfig
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.net.UnknownHostException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class DailyChallengeUiModel(
    val date: String,
    val title: String,
    val titleSlug: String,
    val difficulty: String,
    val questionId: String,
    val tags: List<String>,
    val url: String,
    val descriptionPreview: String,
    val fullStatement: String,
    val pythonStarterCode: String,
    val exampleTestcases: String
)

data class AiGenerationResult(
    val leetcodePythonCode: String,
    val testcaseValidation: String,
    val explanation: String,
    val rawResponse: String,
    val debugLog: String
)

class PipelineException(
    message: String,
    val debugLog: String
) : Exception(message)

private const val PROMPT_NAME = "Prompt for Leetcode_solver"
private const val TAG = "LeetCodeRepository"
private val GEMINI_PRO_PREFERRED_MODELS = listOf("gemini-2.5-pro", "gemini-pro-latest")
private const val MAX_MODEL_RETRIES = 3
private const val MAX_INPUT_TOKENS = 1_048_576
private const val MAX_OUTPUT_TOKENS = 65_535
private const val MAX_THINKING_BUDGET = MAX_OUTPUT_TOKENS / 4
private const val APPROX_CHARS_PER_TOKEN = 4

class LeetCodeRepository {
    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val api: LeetCodeApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://leetcode.com/")
            .client(createHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(LeetCodeApi::class.java)
    }

    private val geminiApi: GeminiApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(createHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApi::class.java)
    }

    private val geminiResponseAdapter by lazy {
        moshi.adapter(GeminiGenerateResponse::class.java)
    }

    private val geminiRequestAdapter by lazy {
        moshi.adapter(GeminiGenerateRequest::class.java)
    }

    private val answerCache = mutableMapOf<String, AiGenerationResult>()
    private var geminiCooldownUntilMillis: Long = 0L
    private val _liveDebugLog = MutableStateFlow("")
    val liveDebugLog: StateFlow<String> = _liveDebugLog.asStateFlow()

    private fun createHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)

        if (!BuildConfig.DEBUG) {
            return builder.build()
        }

        // Debug-only fallback for environments with broken CA chains.
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        )

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        val trustManager = trustAllCerts[0] as X509TrustManager

        return builder
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

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
                      topicTags {
                        name
                        slug
                      }
                    }
                  }
                }
            """.trimIndent()

            val dailyResponse = api.postQuery(GraphQLRequest(query = dailyQuery))
            val daily = dailyResponse.data?.activeDailyCodingChallengeQuestion
                ?: error("Daily challenge data not available")

            val detailQuery = """
                query questionContent(
                  ${'$'}titleSlug: String!
                ) {
                  question(
                    titleSlug: ${'$'}titleSlug
                  ) {
                    content
                    exampleTestcases
                    codeSnippets {
                      lang
                      langSlug
                      code
                    }
                  }
                }
            """.trimIndent()

            val detailsResponse = api.postQuestionDetails(
                GraphQLRequest(
                    query = detailQuery,
                    variables = mapOf("titleSlug" to daily.question.titleSlug)
                )
            )

            val questionDetails = detailsResponse.data?.question
                ?: error("Question details are not available")

            val content = questionDetails.content
                ?.replace(Regex("<[^>]*>"), " ")
                ?.replace("&nbsp;", " ")
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                .orEmpty()

            val pythonStarter = questionDetails.codeSnippets
                ?.firstOrNull { it.langSlug.equals("python3", ignoreCase = true) }
                ?.code
                .orEmpty()

            val exampleTestcases = questionDetails.exampleTestcases
                ?.trim()
                .orEmpty()

            DailyChallengeUiModel(
                date = daily.date,
                title = daily.question.title,
                titleSlug = daily.question.titleSlug,
                difficulty = daily.question.difficulty,
                questionId = daily.question.questionFrontendId,
                tags = daily.question.topicTags.map { it.name },
                url = "https://leetcode.com${daily.link}",
                descriptionPreview = content.take(500),
                fullStatement = content,
                pythonStarterCode = pythonStarter,
                exampleTestcases = exampleTestcases
            )
        }
    }

    suspend fun generateDetailedAnswer(challenge: DailyChallengeUiModel): Result<AiGenerationResult> {
        return runCatching {
            val debug = StringBuilder()
            _liveDebugLog.value = ""

            val cacheKey = challenge.titleSlug
            answerCache[cacheKey]?.let {
                logDebug(debug, "Cache hit for ${challenge.titleSlug}")
                return@runCatching it.copy(debugLog = mergeDebugLogs(it.debugLog, debug.toString()))
            }

            logDebug(debug, "Starting AI pipeline for ${challenge.titleSlug}")

            val now = System.currentTimeMillis()
            if (now < geminiCooldownUntilMillis) {
                val waitSeconds = ((geminiCooldownUntilMillis - now) / 1000).coerceAtLeast(1)
                logDebug(debug, "Cooldown active: $waitSeconds seconds remaining")
                delay(waitSeconds * 1000L)
            }

            val apiKey = BuildConfig.GEMINI_API_KEY.trim()
            require(apiKey.isNotEmpty()) {
                "Missing Gemini API key. Add GEMINI_API_KEY in local.properties."
            }

            val availableModels = runCatching {
                geminiApi.listModels(apiKey).models.orEmpty().map { it.name.removePrefix("models/") }
            }.getOrDefault(emptyList())

            logDebug(debug, "Available models count: ${availableModels.size}")

            val selectedModel = GEMINI_PRO_PREFERRED_MODELS.firstOrNull { candidate ->
                availableModels.any { it.equals(candidate, ignoreCase = true) }
            } ?: GEMINI_PRO_PREFERRED_MODELS.first()

            logDebug(debug, "Selected model: $selectedModel")

            val systemPrompt = """
You are LC-Autonomous-Solver ($PROMPT_NAME).
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

            val boundedSystemPrompt = truncateToApproxTokenLimit(systemPrompt, MAX_INPUT_TOKENS)
            val remainingInputBudget = (MAX_INPUT_TOKENS - estimateApproxTokens(boundedSystemPrompt))
                .coerceAtLeast(1024)
            val boundedUserPrompt = truncateToApproxTokenLimit(userPrompt, remainingInputBudget)

            if (boundedSystemPrompt != systemPrompt) {
                logDebug(debug, "System prompt truncated to fit max input token budget: $MAX_INPUT_TOKENS")
            }
            if (boundedUserPrompt != userPrompt) {
                logDebug(debug, "User prompt truncated to fit max input token budget: $MAX_INPUT_TOKENS")
            }

            logDebug(
                debug,
                "Configured token limits: maxInputTokens=$MAX_INPUT_TOKENS, maxOutputTokens=$MAX_OUTPUT_TOKENS, thinkingBudget=$MAX_THINKING_BUDGET"
            )

            logDebug(debug, "Using model: $selectedModel")
            logDebug(debug, "System prompt: $boundedSystemPrompt")
            logDebug(debug, "User prompt: $boundedUserPrompt")
            val generatedText = try {
                generateWithRetry(selectedModel, apiKey, boundedSystemPrompt, boundedUserPrompt, debug)
            } catch (error: Throwable) {
                logDebug(debug, "Model failed: $selectedModel -> ${error.message}")
                throw PipelineException(
                    message = error.message ?: "Gemini generation failed.",
                    debugLog = debug.toString().trim()
                )
            }

            if (generatedText.isBlank()) {
                logDebug(debug, "Model returned empty text")
                throw PipelineException(
                    message = "Gemini returned an empty response.",
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

    private suspend fun generateWithRetry(
        model: String,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        debug: StringBuilder
    ): String {
        repeat(MAX_MODEL_RETRIES) { index ->
            val attempt = index + 1
            try {
                logDebug(debug, "$model attempt $attempt")
                val request = GeminiGenerateRequest(
                    systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = userPrompt)))),
                    generationConfig = GeminiGenerationConfig(
                        maxOutputTokens = MAX_OUTPUT_TOKENS,
                        thinkingConfig = GeminiThinkingConfig(thinkingBudget = MAX_THINKING_BUDGET)
                    )
                )
                val requestJson = geminiRequestAdapter.toJson(request)
                logDebug(debug, "$model request payload: $requestJson")

                val startedAt = System.currentTimeMillis()
                val rawResponse = geminiApi.generateContentRaw(
                    model = model,
                    apiKey = apiKey,
                    body = request
                )
                val finishedAt = System.currentTimeMillis()
                logDebug(debug, "$model response received in ${finishedAt - startedAt} ms")

                val responseJson = rawResponse.string()
                logDebug(debug, "$model raw response: $responseJson")

                val response = geminiResponseAdapter.fromJson(responseJson)
                    ?: throw PipelineException(
                        "Gemini returned an unreadable empty JSON response.",
                        debug.toString().trim()
                    )

                response.promptFeedback?.let { feedback ->
                    if (!feedback.blockReason.isNullOrBlank()) {
                        val reasonText = feedback.blockReasonMessage?.takeIf { it.isNotBlank() }
                            ?: feedback.blockReason
                        logDebug(debug, "$model prompt feedback block: $reasonText")
                    }
                }

                val firstCandidate = response.candidates?.firstOrNull()
                val finishReason = firstCandidate?.finishReason.orEmpty()
                if (finishReason.isNotBlank()) {
                    logDebug(debug, "$model finishReason: $finishReason")
                }
                val parts = firstCandidate?.content?.parts.orEmpty()
                val textParts = parts.mapNotNull { it.text?.takeIf { t -> t.isNotBlank() } }

                if (textParts.isEmpty()) {
                    val blockReason = response.promptFeedback?.blockReason
                    if (!blockReason.isNullOrBlank()) {
                        throw PipelineException(
                            "Gemini blocked the response ($blockReason). Try reducing prompt size or retry later.",
                            debug.toString().trim()
                        )
                    }
                    if (finishReason.equals("MAX_TOKENS", ignoreCase = true)) {
                        logDebug(debug, "$model exhausted output budget before returning visible text")
                        if (attempt < MAX_MODEL_RETRIES) {
                            val retryDelayMs = (1_500L * attempt).coerceAtMost(6_000L)
                            logDebug(debug, "$model retrying after MAX_TOKENS in ${retryDelayMs}ms")
                            delay(retryDelayMs)
                            return@repeat
                        }
                        throw PipelineException(
                            "Gemini hit MAX_TOKENS without returning output text. Try again; the next run may succeed.",
                            debug.toString().trim()
                        )
                    }
                    logDebug(debug, "$model returned no text parts in candidate content")
                    return ""
                }

                val combinedText = textParts.joinToString("\n").trim()
                logDebug(debug, "$model parsed output text: $combinedText")
                return combinedText
            } catch (error: PipelineException) {
                throw error
            } catch (error: HttpException) {
                val errorBody = error.response()?.errorBody()?.string().orEmpty()
                if (errorBody.isNotBlank()) {
                    logDebug(debug, "$model HTTP ${error.code()} error body: $errorBody")
                }
                logDebug(debug, "$model attempt $attempt failed with HTTP ${error.code()}")
                when (error.code()) {
                    400 -> {
                        throw PipelineException(
                            "HTTP 400 from Gemini (invalid request). Check the logged HTTP 400 error body for the exact field-level reason.",
                            debug.toString().trim()
                        )
                    }

                    403 -> {
                        throw PipelineException(
                            "HTTP 403 from Gemini. Check API key validity, quota/billing, and Generative Language API access.",
                            debug.toString().trim()
                        )
                    }

                    404 -> {
                        throw PipelineException(
                            "HTTP 404 from Gemini model '$model'. Model unavailable for this key/project or temporarily disabled.",
                            debug.toString().trim()
                        )
                    }

                    429 -> {
                        val retryAfterSeconds = error.response()?.headers()?.get("Retry-After")
                            ?.toLongOrNull()
                            ?: (5L * attempt).coerceAtMost(45L)
                        logDebug(debug, "$model rate limited. Retry after $retryAfterSeconds seconds")
                        geminiCooldownUntilMillis = System.currentTimeMillis() + retryAfterSeconds * 1000L
                        if (attempt < MAX_MODEL_RETRIES) {
                            delay(retryAfterSeconds * 1000L)
                        }
                    }

                    503 -> {
                        val retrySeconds = (3L * attempt).coerceAtMost(30L)
                        logDebug(debug, "$model service unavailable (503). Retry after $retrySeconds seconds")
                        if (attempt < MAX_MODEL_RETRIES) {
                            delay(retrySeconds * 1000L)
                        }
                    }

                    else -> {
                        logDebug(debug, "$model transient HTTP ${error.code()}, retrying")
                        if (attempt < MAX_MODEL_RETRIES) {
                            delay((2_000L * attempt).coerceAtMost(15_000L))
                        }
                    }
                }
            } catch (error: UnknownHostException) {
                logDebug(debug, "$model DNS/network error: ${error.message}")
                if (attempt < MAX_MODEL_RETRIES) {
                    delay((2_000L * attempt).coerceAtMost(15_000L))
                }
            } catch (error: IOException) {
                logDebug(debug, "$model IO/network error: ${error.message}")
                if (attempt < MAX_MODEL_RETRIES) {
                    delay((2_000L * attempt).coerceAtMost(15_000L))
                }
            } catch (error: Throwable) {
                logDebug(debug, "$model attempt $attempt failed with ${error::class.java.simpleName}: ${error.message}")
                if (attempt < MAX_MODEL_RETRIES) {
                    delay((2_000L * attempt).coerceAtMost(15_000L))
                }
            }
        }

        throw PipelineException(
            "Failed after $MAX_MODEL_RETRIES retries for model '$model'. This can be caused by repeated rate limits (429), service issues (503), or unstable network/DNS.",
            debug.toString().trim()
        )
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

    private fun formatTimestamp(millis: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        return formatter.format(Date(millis))
    }

    private fun mergeDebugLogs(existing: String, additional: String): String {
        if (existing.isBlank()) return additional
        if (additional.isBlank()) return existing
        return "$existing\n$additional"
    }

    private fun estimateApproxTokens(text: String): Int {
        return ((text.length + APPROX_CHARS_PER_TOKEN - 1) / APPROX_CHARS_PER_TOKEN).coerceAtLeast(1)
    }

    private fun truncateToApproxTokenLimit(text: String, maxTokens: Int): String {
        if (maxTokens <= 0) return ""
        val maxChars = (maxTokens.toLong() * APPROX_CHARS_PER_TOKEN)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        return if (text.length <= maxChars) text else text.take(maxChars)
    }
}

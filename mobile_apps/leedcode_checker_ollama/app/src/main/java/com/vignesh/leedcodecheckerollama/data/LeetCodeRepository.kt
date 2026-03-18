package com.vignesh.leedcodecheckerollama.data

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.vignesh.leedcodecheckerollama.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

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

private const val TAG = "LeedCodeRepository"
private const val MAX_MODEL_RETRIES = 3
private const val MAX_INPUT_TOKENS = 1_048_576
private const val MAX_OUTPUT_TOKENS = 65_535
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

    private val ollamaApi: OllamaApi by lazy {
        Retrofit.Builder()
            .baseUrl(ensureTrailingSlash(BuildConfig.OLLAMA_BASE_URL.trim()))
            .client(createHttpClient())
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OllamaApi::class.java)
    }

    private val ollamaResponseAdapter by lazy {
        moshi.adapter(OllamaGenerateResponse::class.java)
    }

    private val ollamaRequestAdapter by lazy {
        moshi.adapter(OllamaGenerateRequest::class.java)
    }

    private val answerCache = mutableMapOf<String, AiGenerationResult>()
    private val _liveDebugLog = MutableStateFlow("")
    val liveDebugLog: StateFlow<String> = _liveDebugLog.asStateFlow()

    private fun createHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .callTimeout(150, TimeUnit.SECONDS)
            .build()
    }

    fun defaultConfiguredModel(): String {
        return BuildConfig.OLLAMA_MODEL.trim().ifBlank { "qwen2.5:3b" }
    }

    suspend fun listAvailableModels(): Result<List<String>> {
        return runCatching {
            ollamaApi.listTags().models.orEmpty()
                .mapNotNull { it.name }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        }
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

    suspend fun generateDetailedAnswer(
        challenge: DailyChallengeUiModel,
        selectedModel: String?
    ): Result<AiGenerationResult> {
        return runCatching {
            val debug = StringBuilder()
            _liveDebugLog.value = ""

            val cacheKey = challenge.titleSlug
            answerCache[cacheKey]?.let {
                logDebug(debug, "Cache hit for ${challenge.titleSlug}")
                return@runCatching it.copy(debugLog = mergeDebugLogs(it.debugLog, debug.toString()))
            }

            logDebug(debug, "Starting Ollama pipeline for ${challenge.titleSlug}")
            logDebug(debug, "Configured token limits: maxInputTokens=$MAX_INPUT_TOKENS, maxOutputTokens=$MAX_OUTPUT_TOKENS")
            logDebug(debug, "Ollama base URL: ${BuildConfig.OLLAMA_BASE_URL}")

            val model = selectedModel?.trim().takeUnless { it.isNullOrBlank() } ?: defaultConfiguredModel()
            logDebug(debug, "Using model: $model")

            ensureModelAvailable(model, debug)

            val systemPrompt = """
You are LC-Ollama-Solver.
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

            val boundedSystemPrompt = truncateToApproxTokenLimit(systemPrompt, MAX_INPUT_TOKENS / 2)
            val boundedUserPrompt = truncateToApproxTokenLimit(userPrompt, MAX_INPUT_TOKENS / 2)

            logDebug(debug, "System prompt: $boundedSystemPrompt")
            logDebug(debug, "User prompt: $boundedUserPrompt")

            val generatedText = try {
                generateWithRetry(model, boundedSystemPrompt, boundedUserPrompt, debug)
            } catch (error: Throwable) {
                logDebug(debug, "Model failed: $model -> ${error.message}")
                throw PipelineException(
                    message = error.message ?: "Ollama generation failed.",
                    debugLog = debug.toString().trim()
                )
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

    private suspend fun generateWithRetry(
        model: String,
        systemPrompt: String,
        userPrompt: String,
        debug: StringBuilder
    ): String {
        repeat(MAX_MODEL_RETRIES) { index ->
            val attempt = index + 1
            try {
                val request = OllamaGenerateRequest(
                    model = model,
                    system = systemPrompt,
                    prompt = userPrompt,
                    stream = false,
                    options = OllamaOptions(
                        temperature = 0.2,
                        numPredict = MAX_OUTPUT_TOKENS
                    )
                )

                logDebug(debug, "$model attempt $attempt")
                logDebug(debug, "$model request payload: ${ollamaRequestAdapter.toJson(request)}")

                val startedAt = System.currentTimeMillis()
                val rawResponse = ollamaApi.generateRaw(request)
                val finishedAt = System.currentTimeMillis()
                logDebug(debug, "$model response received in ${finishedAt - startedAt} ms")

                val responseJson = rawResponse.string()
                logDebug(debug, "$model raw response: $responseJson")

                val parsed = ollamaResponseAdapter.fromJson(responseJson)
                    ?: throw PipelineException(
                        "Ollama returned an unreadable JSON response.",
                        debug.toString().trim()
                    )

                if (!parsed.error.isNullOrBlank()) {
                    throw PipelineException(
                        "Ollama error: ${parsed.error}",
                        debug.toString().trim()
                    )
                }

                val output = parsed.response.orEmpty().trim()
                if (output.isNotBlank()) {
                    logDebug(debug, "$model parsed output text: $output")
                    return output
                }

                val doneReason = parsed.doneReason.orEmpty()
                if (doneReason.isNotBlank()) {
                    logDebug(debug, "$model done reason: $doneReason")
                }

                if (attempt < MAX_MODEL_RETRIES) {
                    delay((1_500L * attempt).coerceAtMost(6_000L))
                    return@repeat
                }

                throw PipelineException(
                    "Ollama returned no output text after retries. Ensure model is pulled and endpoint is reachable.",
                    debug.toString().trim()
                )
            } catch (error: PipelineException) {
                throw error
            } catch (error: HttpException) {
                val errorBody = error.response()?.errorBody()?.string().orEmpty()
                if (errorBody.isNotBlank()) {
                    logDebug(debug, "$model HTTP ${error.code()} error body: $errorBody")
                }
                if (attempt < MAX_MODEL_RETRIES) {
                    delay((2_000L * attempt).coerceAtMost(8_000L))
                }
            } catch (error: IOException) {
                logDebug(debug, "$model network error: ${error.message}")
                if (attempt < MAX_MODEL_RETRIES) {
                    delay((2_000L * attempt).coerceAtMost(8_000L))
                }
            } catch (error: Throwable) {
                logDebug(debug, "$model attempt $attempt failed with ${error::class.java.simpleName}: ${error.message}")
                if (attempt < MAX_MODEL_RETRIES) {
                    delay((2_000L * attempt).coerceAtMost(8_000L))
                }
            }
        }

        throw PipelineException(
            "Failed after $MAX_MODEL_RETRIES retries for model '$model'. Verify Ollama service is running on the configured base URL.",
            debug.toString().trim()
        )
    }

    private suspend fun ensureModelAvailable(model: String, debug: StringBuilder) {
        val normalizedTarget = normalizeModelName(model)
        logDebug(debug, "Checking local Ollama model availability for $normalizedTarget")

        val tags = runCatching { ollamaApi.listTags() }
            .getOrElse { error ->
                throw PipelineException(
                    "Unable to query local Ollama tags. Ensure Ollama daemon is running on the phone (${BuildConfig.OLLAMA_BASE_URL}). ${error.message}",
                    debug.toString().trim()
                )
            }

        val localNames = tags.models.orEmpty()
            .mapNotNull { it.name }
            .map { normalizeModelName(it) }

        logDebug(debug, "Local Ollama model count: ${localNames.size}")

        if (localNames.any { it == normalizedTarget }) {
            logDebug(debug, "Model already present on device: $normalizedTarget")
            return
        }

        logDebug(debug, "Model not present. Starting on-device download via /api/pull: $normalizedTarget")
        val pullResponse = runCatching {
            ollamaApi.pullModel(OllamaPullRequest(model = model, stream = false))
        }.getOrElse { error ->
            throw PipelineException(
                "Failed to start model pull for '$model'. ${error.message}",
                debug.toString().trim()
            )
        }

        if (!pullResponse.error.isNullOrBlank()) {
            throw PipelineException(
                "Ollama pull failed for '$model': ${pullResponse.error}",
                debug.toString().trim()
            )
        }

        logDebug(debug, "Ollama pull status: ${pullResponse.status.orEmpty().ifBlank { "unknown" }}")

        val tagsAfterPull = runCatching { ollamaApi.listTags() }
            .getOrDefault(OllamaTagResponse(models = emptyList()))
        val namesAfterPull = tagsAfterPull.models.orEmpty()
            .mapNotNull { it.name }
            .map { normalizeModelName(it) }

        if (namesAfterPull.any { it == normalizedTarget }) {
            logDebug(debug, "Model downloaded and stored on device: $normalizedTarget")
            return
        }

        throw PipelineException(
            "Model '$model' is still unavailable after pull. Verify storage space and Ollama runtime health on phone.",
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

    private fun truncateToApproxTokenLimit(text: String, maxTokens: Int): String {
        if (maxTokens <= 0) return ""
        val maxChars = (maxTokens.toLong() * APPROX_CHARS_PER_TOKEN)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        return if (text.length <= maxChars) text else text.take(maxChars)
    }

    private fun ensureTrailingSlash(url: String): String {
        if (url.isBlank()) return "http://127.0.0.1:11434/"
        return if (url.endsWith("/")) url else "$url/"
    }

    private fun normalizeModelName(model: String): String {
        return model.trim().lowercase().removePrefix("models/")
    }
}

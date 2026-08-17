package com.vignesh.leetcodechecker.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.vignesh.leetcodechecker.AppSettings
import com.vignesh.leetcodechecker.AppSettingsStore
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
    val htmlContent: String,  // Raw HTML from LeetCode for proper rendering
    val pythonStarterCode: String,
    val exampleTestcases: String
)

data class LeetCodeProfileSummary(
    val easySolved: Int,
    val mediumSolved: Int,
    val hardSolved: Int,
    val totalSolved: Int,
    val topTags: List<TagProblemCount>,
    val badges: List<LeetCodeBadge>,
    val currentStreak: Int,
    val longestStreak: Int
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
private const val APPROX_CHARS_PER_TOKEN = 4

class LeetCodeRepository(
    private val context: Context
) {
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

    private fun loadSettings(): AppSettings = AppSettingsStore.load(context)

    private fun createHttpClient(): OkHttpClient {
        val timeoutMinutes = loadSettings().networkTimeoutMinutes.coerceIn(1, 60).toLong()
        val builder = OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(timeoutMinutes, TimeUnit.MINUTES)
            .readTimeout(timeoutMinutes, TimeUnit.MINUTES)
            .writeTimeout(timeoutMinutes, TimeUnit.MINUTES)
            .callTimeout(timeoutMinutes, TimeUnit.MINUTES)

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
                descriptionPreview = plainTextContent.take(500),
                fullStatement = plainTextContent,
                htmlContent = rawHtmlContent,
                pythonStarterCode = pythonStarter,
                exampleTestcases = exampleTestcases
            )
        }
    }

    /**
     * Fetch the real per-day submission calendar from the user's LeetCode profile
     * page (the same data leetcode.com/u/<username> renders its own heatmap from),
     * rather than deriving contribution counts from local completion history --
     * which only ever has one entry per day and undercounts actual activity.
     */
    suspend fun fetchSubmissionCalendar(username: String = AppSettingsStore.load(context).leetcodeUsername): Result<Map<String, Int>> {
        return runCatching {
            val query = """
                query userProfileCalendar(${'$'}username: String!) {
                  matchedUser(username: ${'$'}username) {
                    userCalendar {
                      submissionCalendar
                    }
                  }
                }
            """.trimIndent()

            val response = api.getUserCalendar(
                GraphQLRequest(query = query, variables = mapOf("username" to username))
            )
            val raw = response.data?.matchedUser?.userCalendar?.submissionCalendar
                ?: error("No submission calendar returned for $username")

            parseSubmissionCalendarJson(raw)
        }
    }

    /**
     * Fetch solved-count breakdown, topic/tag mastery, badges, and streaks for the
     * Profile tab's LeetCode section. One combined query -- LeetCode's schema nests
     * all of these under matchedUser, so it costs the same as fetching just one.
     * Current streak uses LeetCode's own authoritative userCalendar.streak field (the
     * same number leetcode.com itself displays) rather than a client-side
     * reimplementation, which can drift by a day or two around UTC day boundaries.
     * Longest streak has no equivalent API field, so it's computed from the
     * submission calendar -- there's nothing authoritative to match it against.
     */
    suspend fun fetchLeetCodeProfileSummary(username: String = AppSettingsStore.load(context).leetcodeUsername): Result<LeetCodeProfileSummary> {
        return runCatching {
            val query = """
                query leetcodeProfileDetails(${'$'}username: String!) {
                  matchedUser(username: ${'$'}username) {
                    submitStats {
                      acSubmissionNum {
                        difficulty
                        count
                      }
                    }
                    tagProblemCounts {
                      fundamental { tagName tagSlug problemsSolved }
                      intermediate { tagName tagSlug problemsSolved }
                      advanced { tagName tagSlug problemsSolved }
                    }
                    badges {
                      id
                      name
                      displayName
                      icon
                    }
                    userCalendar {
                      streak
                      submissionCalendar
                    }
                  }
                }
            """.trimIndent()

            val response = api.getLeetCodeProfileDetails(
                GraphQLRequest(query = query, variables = mapOf("username" to username))
            )
            val matchedUser = response.data?.matchedUser ?: error("No profile data returned for $username")

            val submissions = matchedUser.submitStats?.acSubmissionNum.orEmpty()
            val easy = submissions.firstOrNull { it.difficulty == "Easy" }?.count ?: 0
            val medium = submissions.firstOrNull { it.difficulty == "Medium" }?.count ?: 0
            val hard = submissions.firstOrNull { it.difficulty == "Hard" }?.count ?: 0
            val total = submissions.firstOrNull { it.difficulty == "All" }?.count ?: (easy + medium + hard)

            val tagCounts = matchedUser.tagProblemCounts
            val topTags = (tagCounts?.fundamental.orEmpty() + tagCounts?.intermediate.orEmpty() + tagCounts?.advanced.orEmpty())
                .filter { (it.problemsSolved ?: 0) > 0 }
                .sortedByDescending { it.problemsSolved }
                .take(8)

            val calendarRaw = matchedUser.userCalendar?.submissionCalendar
            val (computedCurrentStreak, longestStreak) = calendarRaw
                ?.let { computeStreaks(parseSubmissionCalendarJson(it)) }
                ?: (0 to 0)
            // Prefer LeetCode's own current-streak value; only fall back to our own
            // computation if the API didn't return one (e.g. a transient null).
            val currentStreak = matchedUser.userCalendar?.streak ?: computedCurrentStreak

            LeetCodeProfileSummary(
                easySolved = easy,
                mediumSolved = medium,
                hardSolved = hard,
                totalSolved = total,
                topTags = topTags,
                badges = matchedUser.badges.orEmpty(),
                currentStreak = currentStreak,
                longestStreak = longestStreak
            )
        }
    }

    private fun parseSubmissionCalendarJson(raw: String): Map<String, Int> {
        val json = org.json.JSONObject(raw)
        val calendar = mutableMapOf<String, Int>()
        json.keys().forEach { epochSecondsKey ->
            val epochSeconds = epochSecondsKey.toLongOrNull() ?: return@forEach
            val date = java.time.Instant.ofEpochSecond(epochSeconds)
                .atZone(java.time.ZoneOffset.UTC)
                .toLocalDate()
                .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
            calendar[date] = (calendar[date] ?: 0) + json.getInt(epochSecondsKey)
        }
        return calendar
    }

    private fun computeStreaks(calendar: Map<String, Int>): Pair<Int, Int> {
        val activeDates = calendar.filterValues { it > 0 }.keys
            .mapNotNull { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            .toSortedSet()
        if (activeDates.isEmpty()) return 0 to 0

        var longest = 1
        var run = 1
        val sorted = activeDates.toList()
        for (i in 1 until sorted.size) {
            run = if (sorted[i] == sorted[i - 1].plusDays(1)) run + 1 else 1
            if (run > longest) longest = run
        }

        val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
        var current = 0
        var cursor = if (activeDates.contains(today)) today else today.minusDays(1)
        while (activeDates.contains(cursor)) {
            current++
            cursor = cursor.minusDays(1)
        }
        return current to longest
    }

    suspend fun generateDetailedAnswer(
        challenge: DailyChallengeUiModel,
        forceRefresh: Boolean = false
    ): Result<AiGenerationResult> {
        return runCatching {
            val settings = AppSettingsStore.load(context)
            val apiKey = settings.globalGeminiApiKey.ifBlank { BuildConfig.GEMINI_API_KEY }.trim()

            if (apiKey.isEmpty()) {
                error("Gemini API key is not configured.")
            }

            val maxModelRetries = settings.maxModelRetries.coerceIn(1, 10)
            val maxInputTokens = settings.maxInputTokens.coerceIn(1_024, 2_000_000)
            val maxOutputTokens = settings.maxOutputTokens.coerceIn(256, 65_535)
            val thinkingDivisor = settings.thinkingBudgetDivisor.coerceIn(1, 64)
            val maxThinkingBudget = (maxOutputTokens / thinkingDivisor).coerceAtLeast(1)
            val promptName = settings.promptName.ifBlank { PROMPT_NAME }
            val preferredModels = settings.preferredModelsCsv
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .ifEmpty { listOf("gemini-2.5-pro", "gemini-pro-latest") }

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
            }

            if (forceRefresh) {
                logDebug(debug, "Forcing refresh for ${challenge.titleSlug}")
            } else {
                logDebug(debug, "No in-memory cache found. Fetching fresh response.")
            }

            logDebug(debug, "Starting AI pipeline for ${challenge.titleSlug}")

            val now = System.currentTimeMillis()
            if (now < geminiCooldownUntilMillis) {
                val waitSeconds = ((geminiCooldownUntilMillis - now) / 1000).coerceAtLeast(1)
                logDebug(debug, "Cooldown active: $waitSeconds seconds remaining")
                delay(waitSeconds * 1000L)
            }

            val availableModels = runCatching {
                geminiApi.listModels(apiKey).models.orEmpty().map { it.name.removePrefix("models/") }
            }.getOrDefault(emptyList())

            logDebug(debug, "Available models count: ${availableModels.size}")

            val selectedModel = preferredModels.firstOrNull { candidate ->
                availableModels.any { it.equals(candidate, ignoreCase = true) }
            } ?: preferredModels.first()

            logDebug(debug, "Selected model: $selectedModel")

            val systemPrompt = buildSystemPrompt(promptName)
            val userPrompt = buildUserPrompt(challenge)

            val boundedSystemPrompt = truncateToApproxTokenLimit(systemPrompt, maxInputTokens)
            val remainingInputBudget = (maxInputTokens - estimateApproxTokens(boundedSystemPrompt))
                .coerceAtLeast(1024)
            val boundedUserPrompt = truncateToApproxTokenLimit(userPrompt, remainingInputBudget)

            if (boundedSystemPrompt != systemPrompt) {
                logDebug(debug, "System prompt truncated to fit max input token budget: $maxInputTokens")
            }
            if (boundedUserPrompt != userPrompt) {
                logDebug(debug, "User prompt truncated to fit max input token budget: $maxInputTokens")
            }

            logDebug(
                debug,
                "Configured token limits: maxInputTokens=$maxInputTokens, maxOutputTokens=$maxOutputTokens, thinkingBudget=$maxThinkingBudget"
            )

            logDebug(debug, "Using model: $selectedModel")
            logDebug(debug, "System prompt: $boundedSystemPrompt")
            logDebug(debug, "User prompt: $boundedUserPrompt")
            val generatedText = try {
                generateWithRetry(
                    model = selectedModel,
                    apiKey = apiKey,
                    systemPrompt = boundedSystemPrompt,
                    userPrompt = boundedUserPrompt,
                    maxModelRetries = maxModelRetries,
                    maxOutputTokens = maxOutputTokens,
                    thinkingBudget = maxThinkingBudget,
                    debug = debug
                )
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

    private fun buildSystemPrompt(promptName: String): String = """
You are LC-Autonomous-Solver ($promptName).
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

    private fun buildUserPrompt(challenge: DailyChallengeUiModel): String = """
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

    /**
     * Build the full solve prompt for the "Claude (Manual)" provider -- copied to the
     * clipboard and handed to the Claude app via a share intent instead of an API call.
     * Unlike generateDetailedAnswer's Gemini path, this isn't token-bounded: it's going
     * to a human pasting into a chat app, not a request with an input-token budget.
     */
    fun buildManualSolvePrompt(challenge: DailyChallengeUiModel): String {
        val promptName = AppSettingsStore.load(context).promptName.ifBlank { PROMPT_NAME }
        return buildSystemPrompt(promptName) + "\n\n" + buildUserPrompt(challenge)
    }

    /**
     * Parse a manually-pasted LLM reply (e.g. copied back from the Claude app) using the
     * exact same <leetcode_python3_code>/<testcase_validation>/<explanation> tags the
     * Gemini path expects -- buildManualSolvePrompt's system prompt instructs the model
     * to return them, so any capable LLM given that prompt produces a parseable reply.
     */
    fun parseManualResponse(rawText: String): Result<AiGenerationResult> = runCatching {
        if (rawText.isBlank()) {
            error("Clipboard is empty. Copy the assistant's full reply first, then try again.")
        }

        val code = extractTaggedSection(rawText, "leetcode_python3_code")
        val validation = extractTaggedSection(rawText, "testcase_validation")
        val explanation = extractTaggedSection(rawText, "explanation")

        if (code.isBlank() && validation.isBlank() && explanation.isBlank()) {
            error(
                "Couldn't find the expected <leetcode_python3_code>/<testcase_validation>/<explanation> " +
                    "tags in the pasted text. Make sure you copied the assistant's complete reply."
            )
        }

        AiGenerationResult(
            leetcodePythonCode = code.ifBlank { rawText }.trim(),
            testcaseValidation = validation.trim(),
            explanation = explanation.trim(),
            rawResponse = rawText,
            debugLog = "Parsed from manually pasted response (Claude manual provider)."
        )
    }

    private suspend fun generateWithRetry(
        model: String,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
        maxModelRetries: Int,
        maxOutputTokens: Int,
        thinkingBudget: Int,
        debug: StringBuilder
    ): String {
        repeat(maxModelRetries) { index ->
            val attempt = index + 1
            try {
                logDebug(debug, "$model attempt $attempt")
                val request = GeminiGenerateRequest(
                    systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt))),
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = userPrompt)))),
                    generationConfig = GeminiGenerationConfig(
                        maxOutputTokens = maxOutputTokens,
                        thinkingConfig = GeminiThinkingConfig(thinkingBudget = thinkingBudget)
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
                        if (attempt < maxModelRetries) {
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
                        if (attempt < maxModelRetries) {
                            delay(retryAfterSeconds * 1000L)
                        }
                    }

                    503 -> {
                        val retrySeconds = (3L * attempt).coerceAtMost(30L)
                        logDebug(debug, "$model service unavailable (503). Retry after $retrySeconds seconds")
                        if (attempt < maxModelRetries) {
                            delay(retrySeconds * 1000L)
                        }
                    }

                    else -> {
                        logDebug(debug, "$model transient HTTP ${error.code()}, retrying")
                        if (attempt < maxModelRetries) {
                            delay((2_000L * attempt).coerceAtMost(15_000L))
                        }
                    }
                }
            } catch (error: UnknownHostException) {
                logDebug(debug, "$model DNS/network error: ${error.message}")
                if (attempt < maxModelRetries) {
                    delay((2_000L * attempt).coerceAtMost(15_000L))
                }
            } catch (error: IOException) {
                logDebug(debug, "$model IO/network error: ${error.message}")
                if (attempt < maxModelRetries) {
                    delay((2_000L * attempt).coerceAtMost(15_000L))
                }
            } catch (error: Throwable) {
                logDebug(debug, "$model attempt $attempt failed with ${error::class.java.simpleName}: ${error.message}")
                if (attempt < maxModelRetries) {
                    delay((2_000L * attempt).coerceAtMost(15_000L))
                }
            }
        }

        throw PipelineException(
            "Failed after $maxModelRetries retries for model '$model'. This can be caused by repeated rate limits (429), service issues (503), or unstable network/DNS.",
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

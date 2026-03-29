package com.vignesh.leetcodechecker.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.vignesh.leetcodechecker.BuildConfig
import com.vignesh.leetcodechecker.data.ChatHistoryStore
import com.vignesh.leetcodechecker.data.ChatLogEntry
import com.vignesh.leetcodechecker.data.ChatSession
import com.vignesh.leetcodechecker.data.ChatSessionManager
import com.vignesh.leetcodechecker.data.ChatbotLogger
import com.vignesh.leetcodechecker.data.GeminiApi
import com.vignesh.leetcodechecker.data.GeminiContent
import com.vignesh.leetcodechecker.data.GeminiGenerateRequest
import com.vignesh.leetcodechecker.data.GeminiGenerationConfig
import com.vignesh.leetcodechecker.data.GeminiPart
import com.vignesh.leetcodechecker.data.LoggingGeminiApi
import com.vignesh.leetcodechecker.models.*
import com.vignesh.leetcodechecker.pipeline.DeepAnalysisPipeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * ChatbotViewModel — Strategic Chatbot with multi-session management.
 *
 * All three modes work without any backend server:
 * - Quick Chat: Direct Gemini API call with conversation context.
 * - Deep Analysis: Full 10-step agentic pipeline (Gemini + SEC + News + RAG).
 * - Follow-up: RAG query on already-built index from Deep Analysis.
 *
 * Session management:
 * - New Session: Creates a fresh session, saves the current one
 * - Sessions list: Browse, switch, rename, delete past sessions
 * - Auto-save: Every message exchange is persisted immediately
 */
class ChatbotViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        const val TAG = "ChatbotViewModel"
        const val SYSTEM_PROMPT = """You are a strategic analysis assistant. You can analyse any industry, company, or strategic topic. Provide thoughtful, data-aware responses.

When analyzing companies or industries:
- Provide key metrics and market positioning
- Discuss competitive landscape
- Highlight recent trends and developments
- Offer strategic insights and outlook

If the user asks for detailed data-backed analysis with live data (news, SEC filings, real-time search), suggest they switch to **Deep Analysis** mode for a comprehensive 10-step pipeline analysis."""
    }

    // ── Gemini API setup ────────────────────────────────────────
    private val apiKey = BuildConfig.CHATBOT_GEMINI_API_KEY.ifBlank {
        BuildConfig.GEMINI_API_KEY  // fallback to LeetCode key if chatbot key missing
    }
    private val model = "gemini-2.5-flash"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val rawGeminiApi: GeminiApi = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(httpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GeminiApi::class.java)

    // ── Comprehensive Logger ────────────────────────────────────
    val chatbotLogger = ChatbotLogger()
    private val geminiApi: GeminiApi = LoggingGeminiApi(rawGeminiApi, chatbotLogger)

    // ── Deep Analysis Pipeline (runs entirely on device) ────────
    // Pipeline receives the logging wrapper so ALL its LLM calls are logged
    private val deepPipeline = DeepAnalysisPipeline(geminiApi, apiKey, model)

    // ── State ───────────────────────────────────────────────────
    private val _uiState = MutableStateFlow(ChatUIState())
    val uiState = _uiState.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _costInfo = MutableStateFlow(CostInfo())
    val costInfo = _costInfo.asStateFlow()

    private val _sessionState = MutableStateFlow<SessionState?>(null)
    val sessionState = _sessionState.asStateFlow()

    // ── Session Management State ────────────────────────────────
    private val _sessionList = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessionList = _sessionList.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId = _activeSessionId.asStateFlow()

    private val _activeSessionName = MutableStateFlow("New Session")
    val activeSessionName = _activeSessionName.asStateFlow()

    // ── Logger State (exposed for UI) ───────────────────────────
    private val _logEntries = MutableStateFlow<List<ChatLogEntry>>(emptyList())
    val logEntries = _logEntries.asStateFlow()

    private var currentChatMode = ChatMode.QUICK_CHAT
    private var totalCostUsd = 0.0
    private var apiCallCount = 0
    private var turnCount = 0

    private val appContext get() = getApplication<Application>().applicationContext

    init {
        chatbotLogger.logInfo("ChatbotViewModel initializing",
            "Model: $model\nAPI Key configured: ${apiKey.isNotBlank()}")

        // Migrate legacy single-session data if needed
        ChatSessionManager.migrateFromLegacy(appContext)

        // Load session list
        refreshSessionList()

        // Restore active session
        val activeId = ChatSessionManager.getActiveSessionId(appContext)
        if (activeId != null) {
            loadSessionById(activeId)
        } else {
            // If no active session, create a new one
            createNewSession()
        }

        chatbotLogger.logInfo("ChatbotViewModel ready",
            "Sessions: ${_sessionList.value.size}\nActive: ${_activeSessionName.value}")
        refreshLogEntries()
    }

    /** Refresh the session list from storage */
    fun refreshSessionList() {
        val sessions = ChatSessionManager.loadSessionList(appContext)
        // Also load message counts and previews
        _sessionList.value = sessions.map { session ->
            val msgCount = ChatSessionManager.getSessionMessageCount(appContext, session.id)
            val preview = ChatSessionManager.getSessionPreview(appContext, session.id)
            session.copy(
                messages = (1..msgCount).map {
                    ChatMessage(role = "", content = "")
                }, // placeholder for count
                totalCostUsd = session.totalCostUsd
            )
        }
    }

    /** Persist current active session to SharedPreferences */
    private fun persistState() {
        val sessionId = _activeSessionId.value ?: return
        val session = ChatSession(
            id = sessionId,
            name = _activeSessionName.value,
            messages = _messages.value,
            totalCostUsd = totalCostUsd,
            apiCalls = apiCallCount,
            turnCount = turnCount,
            chatMode = currentChatMode.name,
            updatedAt = System.currentTimeMillis()
        )
        ChatSessionManager.saveSession(appContext, session)
        refreshSessionList()

        // Also keep legacy store in sync for backward compat
        ChatHistoryStore.saveMessages(appContext, _messages.value)
        ChatHistoryStore.saveCostInfo(appContext, _costInfo.value, totalCostUsd, apiCallCount, turnCount)
    }

    // ── User Interactions ───────────────────────────────────────

    fun sendMessage(message: String) {
        chatbotLogger.logInfo("User message [${currentChatMode.name}]",
            "MODE: ${currentChatMode.name}\nMESSAGE: $message")
        refreshLogEntries()
        viewModelScope.launch {
            when (currentChatMode) {
                ChatMode.QUICK_CHAT -> sendQuickChat(message)
                ChatMode.DEEP_ANALYSIS -> sendDeepAnalysis(message)
                ChatMode.FOLLOW_UP -> sendFollowUp(message)
            }
            refreshLogEntries()
        }
    }

    /**
     * Quick Chat — calls Gemini directly from the device.
     * Replicates usecase_4's orchestrator.quick_chat() logic.
     */
    private suspend fun sendQuickChat(message: String) {
        if (apiKey.isBlank()) {
            chatbotLogger.logError("No API key", "CHATBOT_GEMINI_API_KEY not configured")
            refreshLogEntries()
            _uiState.update {
                it.copy(errorMessage = "No API key configured. Add CHATBOT_GEMINI_API_KEY to local.properties or set up the .env file.")
            }
            return
        }

        try {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }

            // Add user message
            val userMsg = ChatMessage(role = "user", content = message)
            _messages.update { it + userMsg }
            turnCount++

            // Build conversation history for context
            val recentMessages = _messages.value.takeLast(10)
            val historyText = recentMessages.joinToString("\n") { m ->
                "${m.role.uppercase()}: ${m.content}"
            }

            chatbotLogger.logInfo("Quick Chat preparing request",
                "History messages: ${recentMessages.size}\nHistory length: ${historyText.length} chars")

            // Call Gemini API directly (the LoggingGeminiApi wrapper will log prompt/response)
            val request = GeminiGenerateRequest(
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = SYSTEM_PROMPT))
                ),
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = historyText))
                    )
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.3,
                    maxOutputTokens = 4096,
                    responseMimeType = "text/plain"
                )
            )

            val response = geminiApi.generateContent(model, apiKey, request)
            val responseText = response.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.mapNotNull { it.text }
                ?.joinToString("")
                ?: "No response received."

            // Add assistant message
            val assistantMsg = ChatMessage(role = "assistant", content = responseText)
            _messages.update { it + assistantMsg }

            // Update cost tracking (approximate: $0.15/1M input + $0.60/1M output for flash)
            apiCallCount++
            val approxInputTokens = historyText.length / 4.0
            val approxOutputTokens = responseText.length / 4.0
            val callCost = (approxInputTokens * 0.15 + approxOutputTokens * 0.60) / 1_000_000.0
            totalCostUsd += callCost
            _costInfo.update {
                CostInfo(
                    totalCost = totalCostUsd,
                    dailyBudget = 5.0,
                    remainingBudget = 5.0 - totalCostUsd,
                    percentUsed = (totalCostUsd / 5.0).toFloat()
                )
            }

            _uiState.update { it.copy(isLoading = false, successMessage = "Response received") }
            // Persist after every exchange
            persistState()
            chatbotLogger.logCostUpdate(totalCostUsd, apiCallCount, callCost)
            Log.d(TAG, "Quick chat OK: cost=\$${"%.6f".format(callCost)}, total=\$${"%.4f".format(totalCostUsd)}")

        } catch (e: Exception) {
            Log.e(TAG, "Quick chat failed", e)
            chatbotLogger.logError("Quick Chat failed",
                "${e.javaClass.simpleName}: ${e.message}\n${e.stackTraceToString().take(500)}")
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Error: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    fun setChatMode(mode: ChatMode) {
        currentChatMode = mode
        chatbotLogger.logModeSwitch(mode.name)
        refreshLogEntries()
    }

    // ════════════════════════════════════════════════════════════════
    // Session Management
    // ════════════════════════════════════════════════════════════════

    /**
     * Create a new session, saving the current one first.
     */
    fun createNewSession() {
        // Save current session if it has messages
        if (_messages.value.isNotEmpty() && _activeSessionId.value != null) {
            persistState()
        }

        val newSession = ChatSession()
        _activeSessionId.value = newSession.id
        _activeSessionName.value = newSession.name
        _messages.value = emptyList()
        _costInfo.value = CostInfo()
        _sessionState.value = null
        totalCostUsd = 0.0
        apiCallCount = 0
        turnCount = 0
        deepPipeline.reset()

        ChatSessionManager.setActiveSessionId(appContext, newSession.id)
        ChatSessionManager.saveSession(appContext, newSession)
        refreshSessionList()

        _uiState.update { ChatUIState(successMessage = "New session created") }
        chatbotLogger.logSessionEvent("New session created",
            "ID: ${newSession.id}\nName: ${newSession.name}")
        refreshLogEntries()
        Log.d(TAG, "New session created: ${newSession.id} — ${newSession.name}")
    }

    /**
     * Switch to an existing session by ID.
     */
    fun switchToSession(sessionId: String) {
        // Save current session first
        if (_messages.value.isNotEmpty() && _activeSessionId.value != null) {
            persistState()
        }

        loadSessionById(sessionId)
        chatbotLogger.logSessionEvent("Switched session",
            "ID: $sessionId\nName: ${_activeSessionName.value}")
        refreshLogEntries()
        _uiState.update { ChatUIState(successMessage = "Switched to: ${_activeSessionName.value}") }
    }

    /**
     * Load a session by ID into the active state.
     */
    private fun loadSessionById(sessionId: String) {
        val session = ChatSessionManager.loadSession(appContext, sessionId)
        if (session != null) {
            _activeSessionId.value = session.id
            _activeSessionName.value = session.name
            _messages.value = session.messages
            totalCostUsd = session.totalCostUsd
            apiCallCount = session.apiCalls
            turnCount = session.turnCount
            _costInfo.value = CostInfo(
                totalCost = totalCostUsd,
                dailyBudget = 5.0,
                remainingBudget = 5.0 - totalCostUsd,
                percentUsed = (totalCostUsd / 5.0).toFloat()
            )
            ChatSessionManager.setActiveSessionId(appContext, sessionId)
            deepPipeline.reset()  // Pipeline state doesn't persist across sessions
            Log.d(TAG, "Loaded session: $sessionId — ${session.name} (${session.messages.size} msgs)")
        } else {
            Log.w(TAG, "Session not found: $sessionId — creating new")
            createNewSession()
        }
    }

    /**
     * Rename the active session or a specific session by ID.
     */
    fun renameSession(sessionId: String, newName: String) {
        ChatSessionManager.renameSession(appContext, sessionId, newName)
        if (sessionId == _activeSessionId.value) {
            _activeSessionName.value = newName
        }
        refreshSessionList()
        chatbotLogger.logSessionEvent("Session renamed", "ID: $sessionId\nNew name: $newName")
        refreshLogEntries()
        _uiState.update { ChatUIState(successMessage = "Renamed to: $newName") }
    }

    /**
     * Delete a session. If it's the active session, create a new one.
     */
    fun deleteSession(sessionId: String) {
        ChatSessionManager.deleteSession(appContext, sessionId)
        chatbotLogger.logSessionEvent("Session deleted", "ID: $sessionId")
        if (sessionId == _activeSessionId.value) {
            createNewSession()
        }
        refreshSessionList()
        refreshLogEntries()
        _uiState.update { ChatUIState(successMessage = "Session deleted") }
    }

    /**
     * Reset the current session (clear messages but keep the session).
     */
    fun resetSession() {
        _messages.update { emptyList() }
        _costInfo.update { CostInfo() }
        _sessionState.update { null }
        totalCostUsd = 0.0
        apiCallCount = 0
        turnCount = 0
        deepPipeline.reset()
        persistState()
        chatbotLogger.logSessionEvent("Session reset", "Session: ${_activeSessionName.value}")
        refreshLogEntries()
        _uiState.update { ChatUIState(successMessage = "Session reset") }
    }

    fun clearMessages() { _messages.update { emptyList() } }
    fun clearError() { _uiState.update { it.copy(errorMessage = null) } }
    fun clearSuccess() { _uiState.update { it.copy(successMessage = null) } }

    /**
     * Deep Analysis — runs the full 10-step agentic pipeline on-device.
     * Streams status updates via Flow.
     */
    private suspend fun sendDeepAnalysis(message: String) {
        if (apiKey.isBlank()) {
            chatbotLogger.logError("No API key for Deep Analysis")
            refreshLogEntries()
            _uiState.update {
                it.copy(errorMessage = "No API key configured. Add CHATBOT_GEMINI_API_KEY to local.properties.")
            }
            return
        }

        try {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            chatbotLogger.logPipelineStep("Deep Analysis started", "Query: $message")
            refreshLogEntries()
            val userMsg = ChatMessage(role = "user", content = message)
            _messages.update { it + userMsg }
            turnCount++

            // Add a placeholder assistant message that we'll update
            val placeholderMsg = ChatMessage(role = "assistant", content = "\uD83E\uDDE0 Starting Deep Analysis...")
            _messages.update { it + placeholderMsg }

            val pipelineStart = System.currentTimeMillis()

            // Run the pipeline and collect status updates
            // (All LLM calls inside the pipeline are automatically logged by LoggingGeminiApi)
            deepPipeline.runDeepAnalysis(message).collect { status ->
                // Update the last message (the assistant placeholder)
                _messages.update { list ->
                    val mutable = list.toMutableList()
                    if (mutable.isNotEmpty()) {
                        mutable[mutable.lastIndex] = ChatMessage(
                            role = "assistant",
                            content = status.message,
                            timestamp = System.currentTimeMillis()
                        )
                    }
                    mutable
                }

                if (status.isComplete) {
                    // Update cost tracking
                    totalCostUsd = deepPipeline.totalCost
                    apiCallCount = deepPipeline.apiCallCount
                    _costInfo.update {
                        CostInfo(
                            totalCost = totalCostUsd,
                            dailyBudget = 5.0,
                            remainingBudget = 5.0 - totalCostUsd,
                            percentUsed = (totalCostUsd / 5.0).toFloat()
                        )
                    }
                    val pipelineElapsed = System.currentTimeMillis() - pipelineStart
                    chatbotLogger.logPipelineStep("Deep Analysis complete",
                        "Duration: ${pipelineElapsed}ms\nTotal cost: \$${String.format("%.4f", totalCostUsd)}\nAPI calls: $apiCallCount")
                    chatbotLogger.logCostUpdate(totalCostUsd, apiCallCount)
                    refreshLogEntries()
                    _uiState.update { it.copy(isLoading = false, successMessage = "Deep Analysis complete") }
                    persistState()
                    Log.d(TAG, "Deep Analysis complete: cost=\$${"%.4f".format(totalCostUsd)}, calls=$apiCallCount")
                } else {
                    // Log intermediate pipeline steps periodically
                    chatbotLogger.logPipelineStep("Pipeline update",
                        status.step.ifBlank { "In progress..." })
                    refreshLogEntries()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Deep Analysis failed", e)
            chatbotLogger.logError("Deep Analysis failed",
                "${e.javaClass.simpleName}: ${e.message}\n${e.stackTraceToString().take(500)}")
            refreshLogEntries()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Deep Analysis Error: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    /**
     * Follow-up — queries the already-built RAG index from Deep Analysis.
     *
     * If the RAG index is available (same session, no restart), uses the full
     * hybrid retrieval pipeline for precise answers.
     *
     * If the index was lost (app restart / session switch), falls back to a
     * context-based approach: extracts the Deep Analysis results from the chat
     * history and uses them as LLM context for the follow-up answer.
     */
    private suspend fun sendFollowUp(message: String) {
        if (apiKey.isBlank()) {
            chatbotLogger.logError("No API key for Follow-up")
            refreshLogEntries()
            _uiState.update {
                it.copy(errorMessage = "No API key configured.")
            }
            return
        }

        // Check if the RAG index is available
        if (deepPipeline.isIndexBuilt) {
            // ── Full RAG follow-up (index in memory) ────────────
            sendFollowUpWithIndex(message)
        } else {
            // ── Fallback: context-based follow-up from chat history ──
            val analysisContext = extractDeepAnalysisFromHistory()
            if (analysisContext.isNullOrBlank()) {
                chatbotLogger.logWarning("Follow-up: no index and no analysis in history",
                    "Neither RAG index nor Deep Analysis results found in chat history")
                refreshLogEntries()
                _uiState.update {
                    it.copy(errorMessage = "No analysis data available. Run a Deep Analysis first, then use Follow-up to ask questions about the results.")
                }
                return
            }
            chatbotLogger.logInfo("Follow-up: using context fallback",
                "RAG index not available (app restarted or session switched). " +
                "Using ${analysisContext.length} chars of Deep Analysis from chat history as context.")
            refreshLogEntries()
            sendFollowUpWithContext(message, analysisContext)
        }
    }

    /**
     * Extract the longest assistant message that looks like a Deep Analysis report
     * from the current chat history. This is the fallback context when the RAG index
     * was lost due to app restart / session switch.
     */
    private fun extractDeepAnalysisFromHistory(): String? {
        val assistantMessages = _messages.value
            .filter { it.role == "assistant" }
            .sortedByDescending { it.content.length }

        // Look for a message that contains Deep Analysis markers
        val deepAnalysisMsg = assistantMessages.firstOrNull { msg ->
            msg.content.contains("Executive Summary", ignoreCase = true) ||
            msg.content.contains("Step 10/10", ignoreCase = true) ||
            msg.content.contains("Strategies", ignoreCase = true) ||
            msg.content.contains("Step 1/10", ignoreCase = true) ||
            msg.content.length > 1000  // Any large assistant response is likely analysis
        }

        return deepAnalysisMsg?.content
    }

    /**
     * Follow-up using the in-memory RAG index (full hybrid retrieval).
     */
    private suspend fun sendFollowUpWithIndex(message: String) {
        try {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            chatbotLogger.logPipelineStep("Follow-up (RAG index)", "Query: $message")
            refreshLogEntries()
            val userMsg = ChatMessage(role = "user", content = message)
            _messages.update { it + userMsg }
            turnCount++

            val placeholderMsg = ChatMessage(role = "assistant", content = "🔎 Searching index...")
            _messages.update { it + placeholderMsg }

            // All LLM calls inside are automatically logged by LoggingGeminiApi
            deepPipeline.runFollowUp(message).collect { status ->
                _messages.update { list ->
                    val mutable = list.toMutableList()
                    if (mutable.isNotEmpty()) {
                        mutable[mutable.lastIndex] = ChatMessage(
                            role = "assistant",
                            content = status.message,
                            timestamp = System.currentTimeMillis()
                        )
                    }
                    mutable
                }

                if (status.isComplete) {
                    totalCostUsd = deepPipeline.totalCost
                    apiCallCount = deepPipeline.apiCallCount
                    _costInfo.update {
                        CostInfo(
                            totalCost = totalCostUsd,
                            dailyBudget = 5.0,
                            remainingBudget = 5.0 - totalCostUsd,
                            percentUsed = (totalCostUsd / 5.0).toFloat()
                        )
                    }
                    _uiState.update { it.copy(isLoading = false, successMessage = "Follow-up complete") }
                    chatbotLogger.logPipelineStep("Follow-up (RAG) complete",
                        "Cost: \$${String.format("%.4f", totalCostUsd)}\nAPI calls: $apiCallCount")
                    chatbotLogger.logCostUpdate(totalCostUsd, apiCallCount)
                    refreshLogEntries()
                    persistState()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Follow-up (RAG) failed", e)
            chatbotLogger.logError("Follow-up (RAG) failed",
                "${e.javaClass.simpleName}: ${e.message}\n${e.stackTraceToString().take(500)}")
            refreshLogEntries()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Follow-up Error: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    /**
     * Follow-up using stored chat history as context (fallback when RAG index is lost).
     * Sends the Deep Analysis report + conversation history + new question to Gemini.
     */
    private suspend fun sendFollowUpWithContext(message: String, analysisContext: String) {
        try {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            chatbotLogger.logPipelineStep("Follow-up (context fallback)",
                "Query: $message\nContext length: ${analysisContext.length} chars")
            refreshLogEntries()

            val userMsg = ChatMessage(role = "user", content = message)
            _messages.update { it + userMsg }
            turnCount++

            val placeholderMsg = ChatMessage(role = "assistant",
                content = "🔎 Answering from previous analysis (context mode)...")
            _messages.update { it + placeholderMsg }

            // Build context from the analysis + recent conversation
            val recentConversation = _messages.value
                .filter { it.role == "user" }
                .takeLast(5)
                .joinToString("\n") { "USER: ${it.content}" }

            // Truncate analysis context to fit within reasonable limits
            val truncatedAnalysis = analysisContext.take(12000)

            val systemPrompt = buildString {
                appendLine("You are a strategic analysis assistant. The user previously ran a Deep Analysis and is now asking follow-up questions.")
                appendLine("Answer the question using the analysis results provided below. Be specific, cite data where available.")
                appendLine()
                appendLine("NOTE: The RAG retrieval index is no longer available (app was restarted), so you are working from the stored analysis report.")
                appendLine("If the question requires data not present in the analysis, clearly say so and suggest running a new Deep Analysis.")
            }

            val userPrompt = buildString {
                appendLine("=== PREVIOUS DEEP ANALYSIS RESULTS ===")
                appendLine(truncatedAnalysis)
                appendLine()
                if (recentConversation.isNotBlank()) {
                    appendLine("=== RECENT CONVERSATION ===")
                    appendLine(recentConversation)
                    appendLine()
                }
                appendLine("=== FOLLOW-UP QUESTION ===")
                appendLine(message)
            }

            val request = GeminiGenerateRequest(
                systemInstruction = GeminiContent(
                    parts = listOf(GeminiPart(text = systemPrompt))
                ),
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = userPrompt))
                    )
                ),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.3,
                    maxOutputTokens = 4096,
                    responseMimeType = "text/plain"
                )
            )

            val response = geminiApi.generateContent(model, apiKey, request)
            val responseText = response.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.mapNotNull { it.text }
                ?.joinToString("")
                ?: "No response received."

            val finalResponse = "*(Context-based follow-up — RAG index unavailable)*\n\n$responseText"
            val assistantMsg = ChatMessage(role = "assistant", content = finalResponse)
            _messages.update { list ->
                val mutable = list.toMutableList()
                if (mutable.isNotEmpty()) {
                    mutable[mutable.lastIndex] = assistantMsg
                }
                mutable
            }

            // Update cost tracking
            apiCallCount++
            val approxInputTokens = (systemPrompt.length + userPrompt.length) / 4.0
            val approxOutputTokens = responseText.length / 4.0
            val callCost = (approxInputTokens * 0.15 + approxOutputTokens * 0.60) / 1_000_000.0
            totalCostUsd += callCost
            _costInfo.update {
                CostInfo(
                    totalCost = totalCostUsd,
                    dailyBudget = 5.0,
                    remainingBudget = 5.0 - totalCostUsd,
                    percentUsed = (totalCostUsd / 5.0).toFloat()
                )
            }

            _uiState.update { it.copy(isLoading = false, successMessage = "Follow-up complete (context mode)") }
            chatbotLogger.logPipelineStep("Follow-up (context) complete",
                "Cost: \$${String.format("%.6f", callCost)}\nResponse: ${responseText.length} chars")
            chatbotLogger.logCostUpdate(totalCostUsd, apiCallCount, callCost)
            refreshLogEntries()
            persistState()

        } catch (e: Exception) {
            Log.e(TAG, "Follow-up (context) failed", e)
            chatbotLogger.logError("Follow-up (context) failed",
                "${e.javaClass.simpleName}: ${e.message}\n${e.stackTraceToString().take(500)}")
            refreshLogEntries()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Follow-up Error: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Logging
    // ════════════════════════════════════════════════════════════════

    /** Refresh log entries for the UI */
    fun refreshLogEntries() {
        _logEntries.value = chatbotLogger.entries
    }

    /** Clear all logs */
    fun clearLogs() {
        chatbotLogger.clear()
        refreshLogEntries()
    }

    /** Export logs as formatted text */
    fun exportLogsAsText(): String = chatbotLogger.exportAsText()

    fun getExamplePrompts(): List<String> = listOf(
        "Analyze the quantum computing landscape and key players",
        "Compare cloud providers: AWS vs Azure vs Google Cloud",
        "What are the latest trends in AI and machine learning?",
        "Evaluate semiconductor industry outlook for 2025"
    )
}

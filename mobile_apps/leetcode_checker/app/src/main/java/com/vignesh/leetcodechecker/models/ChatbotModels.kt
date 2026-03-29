package com.vignesh.leetcodechecker.models

import com.squareup.moshi.Json

// ════════════════════════════════════════════════════════════════════════════
// Chat Message Models
// ════════════════════════════════════════════════════════════════════════════

data class ChatMessage(
    val role: String,  // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatRequest(
    val message: String,
    val mode: String = "quick"  // "quick", "deep", or "followup"
)

data class ChatResponse(
    val response: String,
    @Json(name = "isStreaming")
    val isStreaming: Boolean = false,
    @Json(name = "costUsd")
    val costUsd: Double = 0.0,
    @Json(name = "tokenCount")
    val tokenCount: Int = 0
)

// ════════════════════════════════════════════════════════════════════════════
// Analysis Models
// ════════════════════════════════════════════════════════════════════════════

data class SearchPlan(
    val domain: String = "general",
    @Json(name = "focus_topic")
    val focusTopic: String = "",
    val perspective: String = "an industry analyst",
    @Json(name = "primary_question")
    val primaryQuestion: String = "",
    val companies: List<CompanyInfo> = emptyList(),
    @Json(name = "grounding_queries")
    val groundingQueries: List<String> = emptyList(),
    @Json(name = "news_queries")
    val newsQueries: List<String> = emptyList(),
    @Json(name = "sec_fulltext_queries")
    val secFulltextQueries: List<String> = emptyList()
)

data class CompanyInfo(
    val name: String,
    val ticker: String,
    val cik: String = "",
    val role: String = "primary"
)

data class AnalysisResult(
    @Json(name = "executive_summary")
    val executiveSummary: String = "",
    @Json(name = "key_findings")
    val keyFindings: List<String> = emptyList(),
    val companies: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

// ════════════════════════════════════════════════════════════════════════════
// Session & Cost Tracking
// ════════════════════════════════════════════════════════════════════════════

data class SessionState(
    @Json(name = "session_id")
    val sessionId: String,
    @Json(name = "total_cost_usd")
    val totalCostUsd: Double = 0.0,
    @Json(name = "api_calls")
    val apiCalls: Int = 0,
    @Json(name = "turn_count")
    val turnCount: Int = 0,
    @Json(name = "is_index_built")
    val isIndexBuilt: Boolean = false,
    @Json(name = "last_analysis_result")
    val lastAnalysisResult: AnalysisResult? = null
)

data class CostInfo(
    @Json(name = "total_cost")
    val totalCost: Double = 0.0,
    @Json(name = "daily_budget")
    val dailyBudget: Double = 5.0,
    @Json(name = "remaining_budget")
    val remainingBudget: Double = 5.0,
    @Json(name = "percent_used")
    val percentUsed: Float = 0f,
    @Json(name = "last_updated")
    val lastUpdated: Long = System.currentTimeMillis()
)

// ════════════════════════════════════════════════════════════════════════════
// UI State Models
// ════════════════════════════════════════════════════════════════════════════

data class ChatUIState(
    val isLoading: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val costInfo: CostInfo = CostInfo(),
    val sessionState: SessionState? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

enum class ChatMode {
    QUICK_CHAT,
    DEEP_ANALYSIS,
    FOLLOW_UP
}

data class ChatConfig(
    val chatbotUrl: String = "http://10.0.2.2:7860",  // localhost for emulator
    val timeout: Long = 120000L,  // 2 minutes for deep analysis
    val maxRetries: Int = 2,
    val defaultChatMode: ChatMode = ChatMode.QUICK_CHAT
)

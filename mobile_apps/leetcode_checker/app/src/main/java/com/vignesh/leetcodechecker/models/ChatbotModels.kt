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

// ════════════════════════════════════════════════════════════════════════════
// Analysis Models
// ════════════════════════════════════════════════════════════════════════════

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

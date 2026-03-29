package com.vignesh.leetcodechecker.api

import com.vignesh.leetcodechecker.models.*
import retrofit2.http.*

/**
 * Retrofit API interface for communicating with the Strategic Chatbot backend.
 *
 * The Python backend (usecase_4_strategic_chatbot) provides REST endpoints:
 * - POST /api/chat/quick — Quick Chat mode
 * - POST /api/chat/deep — Deep Analysis mode (streaming)
 * - POST /api/chat/followup — Follow-up RAG query
 * - GET /api/session — Get session state & cost info
 * - POST /api/session/reset — Reset session
 * - POST /api/session/analyze — Generate SearchPlan
 */
interface StrategicChatbotApi {

    // ════════════════════════════════════════════════════════════════════════
    // Chat Endpoints (Mode-specific)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Quick Chat — Direct LLM call with conversation context.
     *
     * Cost: ~$0.0002 per call
     * Response: Immediate (non-streaming)
     */
    @POST("/api/chat/quick")
    suspend fun quickChat(
        @Body request: ChatRequest
    ): ChatResponse

    /**
     * Deep Analysis — Full agentic pipeline with streaming progress.
     *
     * Cost: ~$0.01 per call (can vary based on analysis scope)
     * Response: Streaming (SSE or chunked)
     * Note: This is long-running (30-120 seconds); handle timeouts gracefully.
     */
    @POST("/api/chat/deep")
    suspend fun deepAnalysis(
        @Body request: ChatRequest
    ): ChatResponse

    /**
     * Follow-up — RAG query against already-built retrieval index.
     *
     * Prerequisites: Must have run Deep Analysis at least once.
     * Cost: ~$0.001 per call (cheaper than Deep Analysis)
     * Response: Streaming
     */
    @POST("/api/chat/followup")
    suspend fun followUp(
        @Body request: ChatRequest
    ): ChatResponse

    // ════════════════════════════════════════════════════════════════════════
    // Session Management
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Get current session state and cost tracking info.
     */
    @GET("/api/session")
    suspend fun getSessionState(): SessionState

    /**
     * Get cost tracking info.
     */
    @GET("/api/session/cost")
    suspend fun getCostInfo(): CostInfo

    /**
     * Reset session (clears conversation history, analysis results, index).
     */
    @POST("/api/session/reset")
    suspend fun resetSession(): SessionState

    /**
     * Generate a SearchPlan from user prompt using the LLM.
     *
     * This is used internally by Deep Analysis mode to dynamically determine
     * what companies, news feeds, SEC queries, etc. to fetch.
     */
    @POST("/api/session/analyze")
    suspend fun generateSearchPlan(
        @Body request: ChatRequest
    ): SearchPlan

    // ════════════════════════════════════════════════════════════════════════
    // Health/Config Endpoints
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Health check — verify the backend is running.
     */
    @GET("/api/health")
    suspend fun healthCheck(): Map<String, String>

    /**
     * Get backend configuration (budget limits, supported models, etc.).
     */
    @GET("/api/config")
    suspend fun getConfig(): Map<String, Any>
}

package com.vignesh.leetcodechecker.repository

import android.util.Log
import com.vignesh.leetcodechecker.api.StrategicChatbotApi
import com.vignesh.leetcodechecker.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ChatbotRepository — Manages API calls and caches session state.
 *
 * This repository:
 * 1. Interfaces with StrategicChatbotApi (Retrofit client)
 * 2. Caches session state (cost, messages, analysis results)
 * 3. Provides Flow-based state management for UI
 * 4. Handles network errors gracefully
 */
class ChatbotRepository(
    private val api: StrategicChatbotApi
) {
    private companion object {
        const val TAG = "ChatbotRepository"
    }

    // ════════════════════════════════════════════════════════════════════════
    // State Management (Mutable)
    // ════════════════════════════════════════════════════════════════════════

    private val _uiState = MutableStateFlow(ChatUIState())
    val uiState: Flow<ChatUIState> = _uiState.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: Flow<List<ChatMessage>> = _messages.asStateFlow()

    private val _costInfo = MutableStateFlow(CostInfo())
    val costInfo: Flow<CostInfo> = _costInfo.asStateFlow()

    private val _sessionState = MutableStateFlow<SessionState?>(null)
    val sessionState: Flow<SessionState?> = _sessionState.asStateFlow()

    // ════════════════════════════════════════════════════════════════════════
    // Chat Operations
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Send a message in Quick Chat mode.
     *
     * Flow:
     * 1. Add user message to history
     * 2. Make API call to /api/chat/quick
     * 3. Add assistant response to history
     * 4. Update cost tracking
     */
    suspend fun sendQuickChat(message: String): Result<String> {
        return try {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // Add user message to local history
            val userMessage = ChatMessage(
                role = "user",
                content = message
            )
            _messages.update { it + userMessage }

            // Call backend
            val request = ChatRequest(message = message, mode = "quick")
            val response = api.quickChat(request)

            // Add assistant response
            val assistantMessage = ChatMessage(
                role = "assistant",
                content = response.response
            )
            _messages.update { it + assistantMessage }

            // Update cost
            updateCostInfo()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    successMessage = "Quick chat response received"
                )
            }

            Log.d(TAG, "Quick chat completed. Response length: ${response.response.length}")
            Result.success(response.response)
        } catch (e: Exception) {
            Log.e(TAG, "Quick chat failed", e)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Error: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
            Result.failure(e)
        }
    }

    /**
     * Send a message in Deep Analysis mode.
     *
     * This mode runs the full agentic pipeline and may take 30-120 seconds.
     * The caller should handle streaming chunks by collecting the Flow.
     */
    suspend fun deepAnalysis(message: String): Result<String> {
        return try {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val userMessage = ChatMessage(role = "user", content = message)
            _messages.update { it + userMessage }

            val request = ChatRequest(message = message, mode = "deep")
            val response = api.deepAnalysis(request)

            val assistantMessage = ChatMessage(role = "assistant", content = response.response)
            _messages.update { it + assistantMessage }

            updateCostInfo()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    successMessage = "Deep analysis completed"
                )
            }

            Log.d(TAG, "Deep analysis completed. Response length: ${response.response.length}")
            Result.success(response.response)
        } catch (e: Exception) {
            Log.e(TAG, "Deep analysis failed", e)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Error: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
            Result.failure(e)
        }
    }

    /**
     * Send a follow-up question (RAG against existing index).
     *
     * Prerequisites: Must have run Deep Analysis at least once.
     */
    suspend fun followUp(message: String): Result<String> {
        return try {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val userMessage = ChatMessage(role = "user", content = message)
            _messages.update { it + userMessage }

            val request = ChatRequest(message = message, mode = "followup")
            val response = api.followUp(request)

            val assistantMessage = ChatMessage(role = "assistant", content = response.response)
            _messages.update { it + assistantMessage }

            updateCostInfo()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    successMessage = "Follow-up query answered"
                )
            }

            Log.d(TAG, "Follow-up completed. Response length: ${response.response.length}")
            Result.success(response.response)
        } catch (e: Exception) {
            Log.e(TAG, "Follow-up failed", e)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Error: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
            Result.failure(e)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Session Management
    // ════════════════════════════════════════════════════════════════════════

    suspend fun refreshSessionState() {
        try {
            val state = api.getSessionState()
            _sessionState.update { state }
            Log.d(TAG, "Session state refreshed: cost=${state.totalCostUsd}, turns=${state.turnCount}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh session state", e)
        }
    }

    suspend fun updateCostInfo() {
        try {
            val cost = api.getCostInfo()
            _costInfo.update { cost }
            Log.d(TAG, "Cost info updated: total=${cost.totalCost}, remaining=${cost.remainingBudget}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update cost info", e)
        }
    }

    suspend fun resetSession(): Result<Unit> {
        return try {
            api.resetSession()
            _messages.update { emptyList() }
            _costInfo.update { CostInfo() }
            _sessionState.update { null }
            _uiState.update {
                it.copy(
                    messages = emptyList(),
                    sessionState = null,
                    successMessage = "Session reset"
                )
            }
            Log.d(TAG, "Session reset")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset session", e)
            _uiState.update {
                it.copy(errorMessage = "Failed to reset session: ${e.localizedMessage}")
            }
            Result.failure(e)
        }
    }

    suspend fun generateSearchPlan(prompt: String): Result<SearchPlan> {
        return try {
            val request = ChatRequest(message = prompt, mode = "plan")
            val plan = api.generateSearchPlan(request)
            Log.d(TAG, "SearchPlan generated: domain=${plan.domain}, companies=${plan.companies.size}")
            Result.success(plan)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate search plan", e)
            Result.failure(e)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Utility Methods
    // ════════════════════════════════════════════════════════════════════════

    fun clearMessages() {
        _messages.update { emptyList() }
        _uiState.update { it.copy(messages = emptyList()) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }

    suspend fun healthCheck(): Boolean {
        return try {
            api.healthCheck()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Health check failed", e)
            false
        }
    }
}

package com.vignesh.leetcodechecker.llm

/**
 * Result from LLM generation
 */
sealed class LlmResult {
    data class Success(val response: String) : LlmResult()
    data class Error(val message: String, val exception: Throwable? = null) : LlmResult()
}

/**
 * Common interface for LLM providers.
 * Allows switching between Ollama HTTP API and local llama.cpp inference.
 */
interface LlmProvider {
    /**
     * Display name for this provider
     */
    val name: String
    
    /**
     * Check if the provider is ready to generate
     */
    suspend fun isReady(): Boolean
    
    /**
     * Initialize the provider (e.g., load model)
     */
    suspend fun initialize(): LlmResult
    
    /**
     * Generate text completion
     * @param systemPrompt System message/instructions
     * @param userPrompt User message/question
     * @param maxTokens Maximum tokens to generate
     * @param temperature Sampling temperature
     */
    suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int = 4096,
        temperature: Float = 0.2f
    ): LlmResult
    
    /**
     * Clean up resources
     */
    suspend fun cleanup()
}

/**
 * Configuration for LLM providers
 */
data class LlmConfig(
    val backend: LlmBackend,
    // Ollama settings
    val ollamaBaseUrl: String = "http://127.0.0.1:11434",
    val ollamaModel: String = "qwen2.5:3b",
    // Local llama.cpp settings
    val localModelPath: String = "",
    val localContextSize: Int = 2048,
    val localMaxTokens: Int = 512,
    val networkTimeoutMinutes: Int = 5
)

/**
 * Supported LLM backends
 */
enum class LlmBackend {
    OLLAMA,  // HTTP API to Ollama server
    LOCAL    // On-device llama.cpp
}

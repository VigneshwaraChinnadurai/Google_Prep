package com.vignesh.leetcodechecker.llm

/**
 * Factory for creating LLM providers based on configuration.
 */
object LlmProviderFactory {
    
    /**
     * Create an LLM provider based on the backend setting
     */
    fun create(config: LlmConfig): LlmProvider {
        return when (config.backend) {
            LlmBackend.LOCAL -> LocalLlmProvider(config)
            LlmBackend.OLLAMA -> OllamaLlmProvider(config)
        }
    }
    
    /**
     * Create with simple parameters
     */
    fun create(
        backend: LlmBackend,
        ollamaBaseUrl: String = "http://127.0.0.1:11434",
        ollamaModel: String = "qwen2.5:3b",
        localModelPath: String = "",
        localContextSize: Int = 2048,
        networkTimeoutMinutes: Int = 5
    ): LlmProvider {
        val config = LlmConfig(
            backend = backend,
            ollamaBaseUrl = ollamaBaseUrl,
            ollamaModel = ollamaModel,
            localModelPath = localModelPath,
            localContextSize = localContextSize,
            networkTimeoutMinutes = networkTimeoutMinutes
        )
        return create(config)
    }
    
    /**
     * Check if local (llama.cpp) provider is available
     */
    fun isLocalAvailable(): Boolean = LlamaCpp.isNativeAvailable()
}

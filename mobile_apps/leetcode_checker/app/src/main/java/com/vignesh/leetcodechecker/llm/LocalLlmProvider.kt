package com.vignesh.leetcodechecker.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * LLM provider using local llama.cpp for on-device inference.
 * No network required - runs entirely on the device.
 */
class LocalLlmProvider(
    private val config: LlmConfig
) : LlmProvider {
    
    companion object {
        private const val TAG = "LocalLlmProvider"
    }
    
    override val name: String = "Local (llama.cpp)"
    
    private var isInitialized = false
    
    override suspend fun isReady(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                LlamaCpp.isNativeAvailable() && LlamaCpp.isModelLoaded()
            } catch (e: Exception) {
                Log.e(TAG, "isReady() check failed", e)
                false
            } catch (e: Error) {
                Log.e(TAG, "isReady() check failed with Error", e)
                false
            }
        }
    }
    
    override suspend fun initialize(): LlmResult {
        return withContext(Dispatchers.IO) {
            try {
                if (!LlamaCpp.isNativeAvailable()) {
                    return@withContext LlmResult.Error(
                        "Native library not available. The app may not have been built with llama.cpp support."
                    )
                }
                
                // Initialize backend
                if (!LlamaCpp.ensureBackendReady()) {
                    return@withContext LlmResult.Error("Failed to initialize llama backend")
                }
                
                // Check model path
                if (config.localModelPath.isBlank()) {
                    return@withContext LlmResult.Error(
                        "No model path configured. Please set a GGUF model path in settings."
                    )
                }
                
                // Load model if not already loaded or path changed
                val currentPath = LlamaCpp.getLoadedModelPath()
                if (currentPath != config.localModelPath) {
                    Log.i(TAG, "Loading model: ${config.localModelPath}")
                    
                    val success = LlamaCpp.loadModel(
                        modelPath = config.localModelPath,
                        nCtx = config.localContextSize,
                        nGpuLayers = 0  // CPU only for most Android devices
                    )
                    
                    if (!success) {
                        return@withContext LlmResult.Error(
                            "Failed to load model: ${config.localModelPath}\n\n" +
                            "Make sure the file exists and is a valid GGUF model."
                        )
                    }
                }
                
                isInitialized = true
                Log.i(TAG, "Model loaded successfully")
                LlmResult.Success("Model loaded: ${config.localModelPath}")
                
            } catch (e: Exception) {
                Log.e(TAG, "Initialize failed", e)
                LlmResult.Error("Initialization failed: ${e.message}", e)
            } catch (e: Error) {
                Log.e(TAG, "Initialize failed with Error (native crash)", e)
                LlmResult.Error("Native library error: ${e.message}")
            }
        }
    }
    
    override suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
        temperature: Float
    ): LlmResult {
        return withContext(Dispatchers.IO) {
            try {
                if (!isReady()) {
                    // Try to initialize first
                    val initResult = initialize()
                    if (initResult is LlmResult.Error) {
                        return@withContext initResult
                    }
                }
                
                // Format prompt for chat-style models
                val prompt = LlamaCpp.formatPrompt(systemPrompt, userPrompt)
                
                Log.i(TAG, "Generating response (maxTokens=$maxTokens, temp=$temperature)")
                
                val response = LlamaCpp.generate(
                    prompt = prompt,
                    maxTokens = maxTokens.coerceAtMost(config.localMaxTokens),
                    temperature = temperature
                )
                
                if (response.startsWith("Error:")) {
                    LlmResult.Error(response)
                } else {
                    LlmResult.Success(response.trim())
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Generation failed", e)
                LlmResult.Error("Generation failed: ${e.message}", e)
            } catch (e: Error) {
                Log.e(TAG, "Generation failed with Error (native crash)", e)
                LlmResult.Error("Native library error during generation: ${e.message}")
            }
        }
    }
    
    override suspend fun cleanup() {
        withContext(Dispatchers.IO) {
            try {
                if (LlamaCpp.isNativeAvailable()) {
                    LlamaCpp.freeModel()
                    isInitialized = false
                    Log.i(TAG, "Resources cleaned up")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cleanup failed", e)
            } catch (e: Error) {
                Log.e(TAG, "Cleanup failed with Error", e)
            }
            Unit
        }
    }
}

package com.vignesh.leetcodechecker.llm

import android.util.Log

/**
 * JNI wrapper for llama.cpp native library.
 * Provides on-device LLM inference using GGUF models.
 * 
 * NOTE: Currently DISABLED due to native library crashes on some devices.
 * The native llama.cpp library causes SIGABRT crashes in ggml_abort.
 * All methods return safe fallback values.
 */
object LlamaCpp {
    private const val TAG = "LlamaCpp"
    
    // DISABLED: Native library causes crashes
    // Setting this to false prevents any JNI calls that could crash
    private const val NATIVE_ENABLED = false
    private var isLoaded = false
    
    init {
        if (NATIVE_ENABLED) {
            try {
                System.loadLibrary("llama_jni")
                isLoaded = true
                Log.i(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library: ${e.message}")
                isLoaded = false
            }
        } else {
            Log.w(TAG, "Native llama.cpp is DISABLED - using Ollama HTTP backend instead")
            isLoaded = false
        }
    }
    
    /**
     * Check if the native library is available
     * Returns false since native is disabled to prevent crashes
     */
    fun isNativeAvailable(): Boolean = NATIVE_ENABLED && isLoaded
    
    /**
     * Initialize the llama backend (call once at app startup)
     */
    external fun initBackend(): Boolean
    
    /**
     * Load a GGUF model from file path
     * @param modelPath Absolute path to the .gguf file
     * @param nCtx Context size (default 2048)
     * @param nGpuLayers Number of GPU layers (0 for CPU-only on most Android devices)
     */
    external fun loadModel(modelPath: String, nCtx: Int = 2048, nGpuLayers: Int = 0): Boolean
    
    /**
     * Check if a model is currently loaded
     */
    external fun isModelLoaded(): Boolean
    
    /**
     * Get the path of the currently loaded model
     */
    external fun getLoadedModelPath(): String
    
    /**
     * Generate text completion
     * @param prompt Input prompt including system message
     * @param maxTokens Maximum tokens to generate
     * @param temperature Sampling temperature (0.0 = deterministic, higher = more random)
     */
    external fun generate(prompt: String, maxTokens: Int = 512, temperature: Float = 0.2f): String
    
    /**
     * Free the loaded model and context
     */
    external fun freeModel()
    
    /**
     * Free the entire backend (call at app shutdown)
     */
    external fun freeBackend()
    
    /**
     * Get model info as JSON string
     */
    external fun getModelInfo(): String
    
    /**
     * High-level helper: Ensure backend is initialized
     */
    fun ensureBackendReady(): Boolean {
        if (!isLoaded) return false
        return initBackend()
    }
    
    /**
     * Format a chat-style prompt for LLM
     */
    fun formatPrompt(systemMessage: String, userMessage: String): String {
        return """<|im_start|>system
$systemMessage<|im_end|>
<|im_start|>user
$userMessage<|im_end|>
<|im_start|>assistant
"""
    }
}

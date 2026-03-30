/**
 * llama_jni.cpp - JNI bridge for llama.cpp in LeetCode Checker app
 * 
 * Provides native interface to run GGUF models directly on Android
 * using llama.cpp library for on-device LLM inference.
 */

#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "LlamaCpp"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Global state
static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static std::string g_loaded_model_path;

// Helper: Convert jstring to std::string
static std::string jstring_to_string(JNIEnv* env, jstring jstr) {
    if (!jstr) return "";
    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return result;
}

// Helper: Convert std::string to jstring
static jstring string_to_jstring(JNIEnv* env, const std::string& str) {
    return env->NewStringUTF(str.c_str());
}

extern "C" {

/**
 * Initialize llama backend
 */
JNIEXPORT jboolean JNICALL
Java_com_vignesh_leetcodechecker_llm_LlamaCpp_initBackend(JNIEnv* env, jobject /* this */) {
    LOGI("Initializing llama backend");
    llama_backend_init();
    LOGI("llama backend initialized successfully");
    return JNI_TRUE;
}

/**
 * Load a GGUF model file
 * @param modelPath Path to the .gguf model file
 * @param nCtx Context size (default 2048)
 * @param nGpuLayers Number of GPU layers (0 for CPU only on Android)
 */
JNIEXPORT jboolean JNICALL
Java_com_vignesh_leetcodechecker_llm_LlamaCpp_loadModel(
    JNIEnv* env, jobject /* this */,
    jstring modelPath, jint nCtx, jint nGpuLayers
) {
    std::string path = jstring_to_string(env, modelPath);
    
    // Skip if same model already loaded
    if (g_model && path == g_loaded_model_path) {
        LOGI("Model already loaded: %s", path.c_str());
        return JNI_TRUE;
    }
    
    // Free existing model/context
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    
    LOGI("Loading model: %s (ctx=%d, gpu_layers=%d)", path.c_str(), nCtx, nGpuLayers);
    
    // Model params
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = nGpuLayers;

    g_model = llama_model_load_from_file(path.c_str(), model_params);
    if (!g_model) {
        LOGE("Failed to load model: %s", path.c_str());
        return JNI_FALSE;
    }
    
    // Context params
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = nCtx > 0 ? nCtx : 2048;
    ctx_params.n_batch = 512;
    ctx_params.n_threads = 4;
    ctx_params.n_threads_batch = 4;
    
    g_ctx = llama_init_from_model(g_model, ctx_params);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }
    
    g_loaded_model_path = path;
    LOGI("Model loaded successfully: %s", path.c_str());
    return JNI_TRUE;
}

/**
 * Check if a model is currently loaded
 */
JNIEXPORT jboolean JNICALL
Java_com_vignesh_leetcodechecker_llm_LlamaCpp_isModelLoaded(JNIEnv* env, jobject /* this */) {
    return (g_model != nullptr && g_ctx != nullptr) ? JNI_TRUE : JNI_FALSE;
}

/**
 * Get currently loaded model path
 */
JNIEXPORT jstring JNICALL
Java_com_vignesh_leetcodechecker_llm_LlamaCpp_getLoadedModelPath(JNIEnv* env, jobject /* this */) {
    return string_to_jstring(env, g_loaded_model_path);
}

/**
 * Generate text completion
 * @param prompt Input prompt
 * @param maxTokens Maximum tokens to generate
 * @param temperature Sampling temperature
 */
JNIEXPORT jstring JNICALL
Java_com_vignesh_leetcodechecker_llm_LlamaCpp_generate(
    JNIEnv* env, jobject /* this */,
    jstring prompt, jint maxTokens, jfloat temperature
) {
    if (!g_model || !g_ctx) {
        LOGE("Model not loaded");
        return string_to_jstring(env, "Error: Model not loaded. Please load a model first.");
    }
    
    std::string prompt_str = jstring_to_string(env, prompt);
    LOGI("Generating (max_tokens=%d, temp=%.2f)", maxTokens, temperature);
    LOGD("Prompt: %.100s...", prompt_str.c_str());
    
    // Get vocabulary for tokenization
    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    
    // Tokenize prompt
    int n_ctx = llama_n_ctx(g_ctx);
    std::vector<llama_token> tokens(n_ctx);
    int n_tokens = llama_tokenize(vocab, prompt_str.c_str(), prompt_str.length(), 
                                   tokens.data(), tokens.size(), true, true);
    if (n_tokens < 0) {
        LOGE("Tokenization failed");
        return string_to_jstring(env, "Error: Tokenization failed");
    }
    tokens.resize(n_tokens);
    
    LOGI("Tokenized to %d tokens", n_tokens);
    
    // Clear the memory (KV cache)
    llama_memory_t mem = llama_get_memory(g_ctx);
    if (mem) {
        llama_memory_clear(mem, true);
    }
    
    // Evaluate the prompt tokens
    if (llama_decode(g_ctx, llama_batch_get_one(tokens.data(), n_tokens))) {
        LOGE("Failed to evaluate prompt");
        return string_to_jstring(env, "Error: Failed to evaluate prompt");
    }
    
    // Sampling parameters
    llama_sampler* sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature > 0 ? temperature : 0.2f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    
    // Generate tokens
    std::string result;
    int n_max = maxTokens > 0 ? maxTokens : 512;
    llama_token eos_token = llama_vocab_eos(vocab);
    
    for (int i = 0; i < n_max; i++) {
        llama_token new_token = llama_sampler_sample(sampler, g_ctx, -1);
        
        // Check for end of sequence
        if (new_token == eos_token) {
            LOGI("EOS reached at token %d", i);
            break;
        }
        
        // Convert token to text
        char buf[256];
        int n = llama_token_to_piece(vocab, new_token, buf, sizeof(buf), 0, true);
        if (n > 0) {
            result.append(buf, n);
        }
        
        // Prepare batch for next token
        llama_batch batch = llama_batch_get_one(&new_token, 1);
        if (llama_decode(g_ctx, batch)) {
            LOGE("Decode failed at token %d", i);
            break;
        }
    }
    
    llama_sampler_free(sampler);
    
    LOGI("Generated %zu chars", result.length());
    return string_to_jstring(env, result);
}

/**
 * Free the loaded model and context
 */
JNIEXPORT void JNICALL
Java_com_vignesh_leetcodechecker_llm_LlamaCpp_freeModel(JNIEnv* env, jobject /* this */) {
    LOGI("Freeing model");
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_loaded_model_path.clear();
    LOGI("Model freed");
}

/**
 * Free the backend
 */
JNIEXPORT void JNICALL
Java_com_vignesh_leetcodechecker_llm_LlamaCpp_freeBackend(JNIEnv* env, jobject /* this */) {
    LOGI("Freeing backend");
    Java_com_vignesh_leetcodechecker_llm_LlamaCpp_freeModel(env, nullptr);
    llama_backend_free();
    LOGI("Backend freed");
}

/**
 * Get model info as JSON string
 */
JNIEXPORT jstring JNICALL
Java_com_vignesh_leetcodechecker_llm_LlamaCpp_getModelInfo(JNIEnv* env, jobject /* this */) {
    if (!g_model) {
        return string_to_jstring(env, "{\"error\":\"No model loaded\"}");
    }
    
    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    int n_vocab = llama_vocab_n_tokens(vocab);
    int n_ctx = g_ctx ? llama_n_ctx(g_ctx) : 0;
    
    char buf[512];
    snprintf(buf, sizeof(buf), 
        "{\"path\":\"%s\",\"n_vocab\":%d,\"n_ctx\":%d}",
        g_loaded_model_path.c_str(), n_vocab, n_ctx);
    
    return string_to_jstring(env, buf);
}

} // extern "C"

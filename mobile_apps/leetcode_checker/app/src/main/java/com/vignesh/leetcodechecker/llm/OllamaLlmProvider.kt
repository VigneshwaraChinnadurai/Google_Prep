package com.vignesh.leetcodechecker.llm

import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * LLM provider using Ollama HTTP API.
 * Requires a running Ollama server (local or remote).
 */
class OllamaLlmProvider(
    private val config: LlmConfig
) : LlmProvider {
    
    companion object {
        private const val TAG = "OllamaLlmProvider"
    }
    
    override val name: String = "Ollama (HTTP)"
    
    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }
    
    private val httpClient: OkHttpClient by lazy {
        val timeout = config.networkTimeoutMinutes.coerceIn(1, 60).toLong()
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(timeout, TimeUnit.MINUTES)
            .readTimeout(timeout, TimeUnit.MINUTES)
            .writeTimeout(timeout, TimeUnit.MINUTES)
            .callTimeout(timeout, TimeUnit.MINUTES)
            .build()
    }
    
    private val api: OllamaLlmApi by lazy {
        Retrofit.Builder()
            .baseUrl(resolveBaseUrl())
            .client(httpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OllamaLlmApi::class.java)
    }
    
    private val responseAdapter by lazy {
        moshi.adapter(OllamaResponse::class.java)
    }
    
    private fun resolveBaseUrl(): String {
        val url = config.ollamaBaseUrl.trim()
        return if (url.endsWith("/")) url else "$url/"
    }
    
    override suspend fun isReady(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val tags = api.listTags()
                tags.models?.any { it.name?.contains(config.ollamaModel, ignoreCase = true) == true } == true
            } catch (e: Exception) {
                Log.w(TAG, "Ollama not ready: ${e.message}")
                false
            }
        }
    }
    
    override suspend fun initialize(): LlmResult {
        return withContext(Dispatchers.IO) {
            try {
                val tags = api.listTags()
                val models = tags.models?.mapNotNull { it.name } ?: emptyList()
                
                if (models.isEmpty()) {
                    LlmResult.Error(
                        "Ollama server has no models installed.\n\n" +
                        "Run: ollama pull ${config.ollamaModel}"
                    )
                } else if (!models.any { it.contains(config.ollamaModel, ignoreCase = true) }) {
                    LlmResult.Error(
                        "Model '${config.ollamaModel}' not found.\n\n" +
                        "Available models: ${models.joinToString()}\n\n" +
                        "Run: ollama pull ${config.ollamaModel}"
                    )
                } else {
                    LlmResult.Success("Connected to Ollama at ${config.ollamaBaseUrl}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Initialize failed", e)
                LlmResult.Error(
                    "Cannot connect to Ollama at ${config.ollamaBaseUrl}\n\n" +
                    "Make sure Ollama is running and accessible.\n\n" +
                    "Error: ${e.message}",
                    e
                )
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
                val request = OllamaRequest(
                    model = config.ollamaModel,
                    prompt = userPrompt,
                    system = systemPrompt,
                    stream = false,
                    options = OllamaRequestOptions(
                        temperature = temperature.toDouble(),
                        numPredict = maxTokens
                    )
                )
                
                Log.i(TAG, "Generating with ${config.ollamaModel}")
                
                val responseBody = api.generate(request)
                val responseText = responseBody.string()
                
                // Parse last JSON object (Ollama may stream multiple)
                val jsonLines = responseText.trim().lines()
                val lastLine = jsonLines.lastOrNull { it.contains("\"done\":true") }
                    ?: jsonLines.lastOrNull()
                    ?: return@withContext LlmResult.Error("Empty response from Ollama")
                
                val parsed = responseAdapter.fromJson(lastLine)
                
                when {
                    parsed == null -> LlmResult.Error("Failed to parse Ollama response")
                    !parsed.error.isNullOrBlank() -> LlmResult.Error("Ollama error: ${parsed.error}")
                    parsed.response.isNullOrBlank() -> LlmResult.Error("Empty response from model")
                    else -> LlmResult.Success(parsed.response.trim())
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Generation failed", e)
                LlmResult.Error("Generation failed: ${e.message}", e)
            }
        }
    }
    
    override suspend fun cleanup() {
        // HTTP client manages connections, nothing to clean up
    }
}

// Internal API models
private data class OllamaRequest(
    val model: String,
    val prompt: String,
    val system: String,
    val stream: Boolean = false,
    val options: OllamaRequestOptions? = null
)

private data class OllamaRequestOptions(
    val temperature: Double = 0.2,
    @Json(name = "num_predict")
    val numPredict: Int = 4096
)

private data class OllamaResponse(
    val model: String?,
    val response: String?,
    val done: Boolean?,
    @Json(name = "done_reason")
    val doneReason: String?,
    val error: String?
)

private data class OllamaTagResponse(
    val models: List<OllamaModelTag>?
)

private data class OllamaModelTag(
    val name: String?,
    val size: Long?
)

private interface OllamaLlmApi {
    @GET("api/tags")
    suspend fun listTags(): OllamaTagResponse
    
    @POST("api/generate")
    suspend fun generate(@Body body: OllamaRequest): ResponseBody
}

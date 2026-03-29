package com.vignesh.leetcodechecker.data

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.GET
import okhttp3.ResponseBody

// ════════════════════════════════════════════════════════════════════════
// Generate Content request/response models
// ════════════════════════════════════════════════════════════════════════

data class GeminiGenerateRequest(
    val systemInstruction: GeminiContent? = null,
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null,
    val tools: List<GeminiTool>? = null
)

data class GeminiGenerationConfig(
    val temperature: Double = 0.2,
    val maxOutputTokens: Int = 65535,
    val responseMimeType: String = "text/plain",
    val responseSchema: Map<String, Any>? = null,
    val thinkingConfig: GeminiThinkingConfig? = null,
    val topP: Double? = null
)

data class GeminiThinkingConfig(
    val thinkingBudget: Int
)

data class GeminiTool(
    val googleSearch: Map<String, Any>? = null
)

data class GeminiContent(
    val parts: List<GeminiPart>?
)

data class GeminiPart(
    val text: String?
)

data class GeminiGenerateResponse(
    val candidates: List<GeminiCandidate>?,
    val promptFeedback: GeminiPromptFeedback?,
    val usageMetadata: GeminiUsageMetadata? = null
)

data class GeminiCandidate(
    val content: GeminiContent?,
    val finishReason: String?,
    val groundingMetadata: GeminiGroundingMetadata? = null
)

data class GeminiGroundingMetadata(
    val groundingChunks: List<GeminiGroundingChunk>? = null
)

data class GeminiGroundingChunk(
    val web: GeminiWebChunk? = null
)

data class GeminiWebChunk(
    val title: String? = null,
    val uri: String? = null
)

data class GeminiUsageMetadata(
    val promptTokenCount: Int? = null,
    val candidatesTokenCount: Int? = null,
    val totalTokenCount: Int? = null
)

data class GeminiPromptFeedback(
    val blockReason: String?,
    val blockReasonMessage: String?
)

data class GeminiModelListResponse(
    val models: List<GeminiModel>?
)

data class GeminiModel(
    val name: String
)

// ════════════════════════════════════════════════════════════════════════
// Embedding request/response models
// ════════════════════════════════════════════════════════════════════════

data class GeminiEmbedRequest(
    val model: String,
    val content: GeminiContent,
    val taskType: String = "RETRIEVAL_DOCUMENT"
)

data class GeminiEmbedResponse(
    val embedding: GeminiEmbeddingValues? = null
)

data class GeminiEmbeddingValues(
    val values: List<Double>? = null
)

data class GeminiBatchEmbedRequest(
    val requests: List<GeminiEmbedRequest>
)

data class GeminiBatchEmbedResponse(
    val embeddings: List<GeminiEmbeddingValues>? = null
)

// ════════════════════════════════════════════════════════════════════════
// Retrofit API interface
// ════════════════════════════════════════════════════════════════════════

interface GeminiApi {
    @GET("v1beta/models")
    suspend fun listModels(
        @Query("key") apiKey: String
    ): GeminiModelListResponse

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body body: GeminiGenerateRequest
    ): GeminiGenerateResponse

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContentRaw(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body body: GeminiGenerateRequest
    ): ResponseBody

    @POST("v1beta/models/{model}:embedContent")
    suspend fun embedContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body body: GeminiEmbedRequest
    ): GeminiEmbedResponse

    @POST("v1beta/models/{model}:batchEmbedContents")
    suspend fun batchEmbedContents(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body body: GeminiBatchEmbedRequest
    ): GeminiBatchEmbedResponse
}

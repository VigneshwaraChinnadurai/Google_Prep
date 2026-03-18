package com.vignesh.leetcodechecker.data

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.GET

data class GeminiGenerateRequest(
    val systemInstruction: GeminiContent,
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null
)

data class GeminiGenerationConfig(
    val temperature: Double = 0.2,
    val maxOutputTokens: Int = 4096
)

data class GeminiContent(
    val parts: List<GeminiPart>?
)

data class GeminiPart(
    val text: String
)

data class GeminiGenerateResponse(
    val candidates: List<GeminiCandidate>?,
    val promptFeedback: GeminiPromptFeedback?
)

data class GeminiCandidate(
    val content: GeminiContent?
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
}

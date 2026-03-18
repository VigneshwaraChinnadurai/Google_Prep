package com.vignesh.leedcodecheckerollama.data

import com.squareup.moshi.Json
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Body
import retrofit2.http.POST

data class OllamaGenerateRequest(
    val model: String,
    val prompt: String,
    val system: String,
    val stream: Boolean = false,
    val options: OllamaOptions? = null
)

data class OllamaOptions(
    val temperature: Double = 0.2,
    @Json(name = "num_predict")
    val numPredict: Int = 4096
)

data class OllamaGenerateResponse(
    val model: String?,
    val response: String?,
    val done: Boolean?,
    @Json(name = "done_reason")
    val doneReason: String?,
    val error: String?
)

data class OllamaPullRequest(
    val model: String,
    val stream: Boolean = false
)

data class OllamaPullResponse(
    val status: String?,
    val error: String?
)

data class OllamaTagResponse(
    val models: List<OllamaModelTag>?
)

data class OllamaModelTag(
    val name: String?
)

interface OllamaApi {
    @GET("api/tags")
    suspend fun listTags(): OllamaTagResponse

    @POST("api/pull")
    suspend fun pullModel(
        @Body body: OllamaPullRequest
    ): OllamaPullResponse

    @POST("api/generate")
    suspend fun generateRaw(
        @Body body: OllamaGenerateRequest
    ): ResponseBody
}

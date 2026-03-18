package com.vignesh.leedcodecheckerollama.data

import com.squareup.moshi.Json
import okhttp3.ResponseBody
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

interface OllamaApi {
    @POST("api/generate")
    suspend fun generateRaw(
        @Body body: OllamaGenerateRequest
    ): ResponseBody
}

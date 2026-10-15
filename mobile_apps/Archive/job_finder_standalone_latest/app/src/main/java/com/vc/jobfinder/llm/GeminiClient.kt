package com.vc.jobfinder.llm

import com.vc.jobfinder.data.local.SettingsStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Direct REST client for Gemini's generateContent endpoint.
 * No Google AI SDK — keeps APK small and avoids the GMS dependency.
 */
@Singleton
class GeminiClient @Inject constructor(
    private val settings: SettingsStore,
) {
    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; explicitNulls = false })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 15_000
        }
    }

    /**
     * Generate text. The caller is responsible for prompt engineering and
     * for parsing structured responses out of the result text.
     */
    suspend fun generate(
        prompt: String,
        systemPrompt: String? = null,
        model: String = "gemini-2.5-flash",
        temperature: Float = 0.2f,
        responseMimeType: String? = null,
    ): String {
        val key = settings.apiKey()
            ?: throw GeminiError.NoApiKey

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

        val request = GenerateRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            systemInstruction = systemPrompt?.let { Content(parts = listOf(Part(text = it))) },
            generationConfig = GenerationConfig(
                temperature = temperature,
                responseMimeType = responseMimeType,
            ),
        )

        val response = try {
            http.post(url) {
                contentType(ContentType.Application.Json)
                headers { append("x-goog-api-key", key) }
                setBody(request)
            }
        } catch (t: Throwable) {
            throw GeminiError.Network(t.message ?: "Network error")
        }

        if (!response.status.isSuccess()) {
            val body = runCatching { response.body<String>() }.getOrDefault("")
            throw GeminiError.Api(response.status.value, body.take(500))
        }

        val parsed: GenerateResponse = response.body()
        return parsed.candidates?.firstOrNull()
            ?.content?.parts?.firstOrNull()
            ?.text
            ?: throw GeminiError.EmptyResponse
    }
}

private fun io.ktor.http.HttpStatusCode.isSuccess() = value in 200..299

sealed class GeminiError(message: String) : Exception(message) {
    data object NoApiKey : GeminiError("No Gemini API key configured. Add one in Settings.")
    data object EmptyResponse : GeminiError("Gemini returned an empty response.")
    data class Api(val code: Int, val body: String) : GeminiError("Gemini error $code: $body")
    data class Network(val detail: String) : GeminiError("Network error: $detail")
}

// --- request/response DTOs ---
@Serializable internal data class GenerateRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null,
    val generationConfig: GenerationConfig? = null,
)

@Serializable internal data class Content(
    val parts: List<Part>,
    val role: String? = null,
)

@Serializable internal data class Part(val text: String)

@Serializable internal data class GenerationConfig(
    val temperature: Float? = null,
    val responseMimeType: String? = null,
)

@Serializable internal data class GenerateResponse(
    val candidates: List<Candidate>? = null,
)

@Serializable internal data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null,
)

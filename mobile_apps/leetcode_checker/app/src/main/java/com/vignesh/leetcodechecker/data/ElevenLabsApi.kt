package com.vignesh.leetcodechecker.data

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming

data class ElevenLabsVoiceSettings(
    val stability: Double = 0.5,
    val similarity_boost: Double = 0.75
)

data class ElevenLabsTtsRequest(
    val text: String,
    val model_id: String = "eleven_multilingual_v2",
    val voice_settings: ElevenLabsVoiceSettings = ElevenLabsVoiceSettings()
)

interface ElevenLabsApi {
    // Response is raw audio/mpeg bytes, not JSON -- @Streaming + ResponseBody, same pattern
    // as GeminiApi.generateContentRaw for a non-JSON response body.
    @Streaming
    @POST("v1/text-to-speech/{voiceId}")
    suspend fun textToSpeech(
        @Path("voiceId") voiceId: String,
        @Header("xi-api-key") apiKey: String,
        @Body body: ElevenLabsTtsRequest
    ): ResponseBody
}

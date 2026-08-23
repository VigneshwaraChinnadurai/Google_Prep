package com.vignesh.leetcodechecker.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Wraps ElevenLabsApi for the Book Reader's optional natural-voice provider (the
 * alternative to Android's free built-in TextToSpeech).
 */
object ElevenLabsSpeechService {
    // ElevenLabs' well-known public "Rachel" premade voice -- a sane default until the
    // user sets their own voice ID in Global Settings.
    const val DEFAULT_VOICE_ID = "21m00Tcm4TlvDq8ikWAM"

    // ElevenLabs bills per character. Cap a single request rather than silently sending an
    // entire long chapter -- same spirit as the WAV truncation already done for the
    // Android-TTS mail-voice-over path.
    const val MAX_CHARS_PER_REQUEST = 4_000

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private val api: ElevenLabsApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.elevenlabs.io/")
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ElevenLabsApi::class.java)
    }

    /** Returns raw MP3 bytes on success. Takes credentials explicitly (rather than reading
     *  AppSettingsStore itself) so callers can preview unsaved settings-screen values, same
     *  as the existing "Send Test Email" flow does for GmailSmtpSender. */
    suspend fun synthesize(apiKey: String, voiceId: String, text: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val trimmedKey = apiKey.trim()
            if (trimmedKey.isEmpty()) {
                return@withContext Result.failure(Exception("ElevenLabs API key not configured. Set it in Global Settings."))
            }
            val resolvedVoiceId = voiceId.trim().ifBlank { DEFAULT_VOICE_ID }
            val truncated = text.take(MAX_CHARS_PER_REQUEST)

            val response = api.textToSpeech(
                voiceId = resolvedVoiceId,
                apiKey = trimmedKey,
                body = ElevenLabsTtsRequest(text = truncated)
            )
            Result.success(response.bytes())
        } catch (e: HttpException) {
            val detail = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            Result.failure(Exception("ElevenLabs request failed (HTTP ${e.code()})" + if (!detail.isNullOrBlank()) ": $detail" else ""))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

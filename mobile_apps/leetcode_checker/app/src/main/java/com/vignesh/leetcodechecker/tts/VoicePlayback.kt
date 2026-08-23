package com.vignesh.leetcodechecker.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.vignesh.leetcodechecker.AppSettingsStore
import com.vignesh.leetcodechecker.data.ElevenLabsSpeechService
import com.vignesh.leetcodechecker.email.EmailAttachment
import com.vignesh.leetcodechecker.email.GmailSmtpSender
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Shared read-aloud / mail-voice-over logic for both the Book Reader and the Mail reader
 * screens -- originally built for books, reused as-is for email bodies since both are just
 * "speak this text, or synthesize it and email it" with the same Android-TTS/ElevenLabs
 * provider choice from Global Settings.
 */
object VoicePlayback {

    fun speakWithAndroidTts(tts: TextToSpeech?, text: String, onSpeakingChange: (Boolean) -> Unit) {
        if (tts == null || text.isBlank()) return
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { onSpeakingChange(true) }
            override fun onDone(utteranceId: String?) { onSpeakingChange(false) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { onSpeakingChange(false) }
        })
        // TTS engines cap a single utterance's length (TextToSpeech.getMaxSpeechInputLength());
        // chunk long text so speech doesn't silently cut off partway through.
        val chunkSize = TextToSpeech.getMaxSpeechInputLength().coerceAtMost(3_900)
        text.chunked(chunkSize).forEachIndexed { index, chunk ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts.speak(chunk, mode, null, "chunk_$index")
        }
    }

    /**
     * ElevenLabs equivalent: synthesizes the whole request as one MP3 (no chunking --
     * ElevenLabsSpeechService already truncates to its per-request character cap) and
     * plays it back with MediaPlayer.
     */
    suspend fun speakWithElevenLabs(
        context: Context,
        apiKey: String,
        voiceId: String,
        text: String,
        mediaPlayerHolder: Array<android.media.MediaPlayer?>,
        onSpeakingChange: (Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        if (text.isBlank()) return
        onSpeakingChange(true)
        val bytes = ElevenLabsSpeechService.synthesize(apiKey, voiceId, text).getOrElse { e ->
            onSpeakingChange(false)
            onError(e.message ?: "ElevenLabs synthesis failed.")
            return
        }
        val file = withContext(Dispatchers.IO) {
            File(context.cacheDir, "read_aloud_${System.currentTimeMillis()}.mp3").apply { writeBytes(bytes) }
        }
        runCatching {
            val player = android.media.MediaPlayer()
            mediaPlayerHolder[0] = player
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener {
                onSpeakingChange(false)
                it.release()
                mediaPlayerHolder[0] = null
                file.delete()
            }
            player.setOnErrorListener { mp, _, _ ->
                onSpeakingChange(false)
                mp.release()
                mediaPlayerHolder[0] = null
                true
            }
            player.prepare()
            player.start()
        }.onFailure { e ->
            onSpeakingChange(false)
            onError(e.message ?: "Couldn't play the synthesized audio.")
        }
    }

    /**
     * Synthesizes [text] to a WAV file (Android TTS) and emails it via GmailSmtpSender.
     * synthesizeToFile() is a single-shot call producing one file, so very long text is
     * truncated to the engine's max utterance length rather than stitched from multiple
     * WAV files.
     */
    suspend fun mailVoiceOverAndroidTts(context: Context, tts: TextToSpeech?, subjectTitle: String, text: String): String {
        if (tts == null || text.isBlank()) return "Nothing to synthesize."
        return withContext(Dispatchers.IO) {
            runCatching {
                val outFile = File(context.cacheDir, "voiceover_${System.currentTimeMillis()}.wav")
                val utteranceId = "mail_voiceover_${System.currentTimeMillis()}"
                val done = CompletableDeferred<Boolean>()
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(id: String?) { if (id == utteranceId) done.complete(true) }
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) { if (id == utteranceId) done.complete(false) }
                })

                val maxLen = TextToSpeech.getMaxSpeechInputLength().coerceAtMost(3_900)
                val truncated = text.take(maxLen)
                val result = tts.synthesizeToFile(truncated, Bundle(), outFile, utteranceId)
                if (result != TextToSpeech.SUCCESS) error("TTS engine rejected the synthesis request.")

                val ok = withTimeoutOrNull(60_000) { done.await() } ?: false
                if (!ok || !outFile.exists()) error("Voice-over synthesis failed or timed out.")

                val settings = AppSettingsStore.load(context)
                if (settings.notificationEmailFrom.isBlank() || settings.notificationEmailAppPassword.isBlank()) {
                    error("Configure your email in Global Settings first (Email Notifications section).")
                }
                val to = settings.notificationEmailTo.ifBlank { settings.notificationEmailFrom }
                val bytes = outFile.readBytes()
                outFile.delete()

                GmailSmtpSender.send(
                    fromEmail = settings.notificationEmailFrom,
                    appPassword = settings.notificationEmailAppPassword,
                    toEmail = to,
                    subject = "Voice-over: $subjectTitle",
                    body = "Attached: a voice-over reading of \"$subjectTitle\"." +
                        if (text.length > maxLen) "\n\n(Truncated to the TTS engine's max length for a single file.)" else "",
                    attachment = EmailAttachment(fileName = "voiceover.wav", mimeType = "audio/wav", bytes = bytes)
                ).getOrThrow()

                "Sent to $to"
            }.getOrElse { e -> "Failed: ${e.message}" }
        }
    }

    /**
     * ElevenLabs equivalent: synthesizes one MP3 (already-raw audio bytes, no WAV-file
     * synthesizeToFile() round trip needed) and emails it as an attachment.
     */
    suspend fun mailVoiceOverElevenLabs(
        context: Context,
        apiKey: String,
        voiceId: String,
        subjectTitle: String,
        text: String
    ): String {
        if (text.isBlank()) return "Nothing to synthesize."
        return withContext(Dispatchers.IO) {
            runCatching {
                val bytes = ElevenLabsSpeechService.synthesize(apiKey, voiceId, text).getOrThrow()

                val settings = AppSettingsStore.load(context)
                if (settings.notificationEmailFrom.isBlank() || settings.notificationEmailAppPassword.isBlank()) {
                    error("Configure your email in Global Settings first (Email Notifications section).")
                }
                val to = settings.notificationEmailTo.ifBlank { settings.notificationEmailFrom }

                GmailSmtpSender.send(
                    fromEmail = settings.notificationEmailFrom,
                    appPassword = settings.notificationEmailAppPassword,
                    toEmail = to,
                    subject = "Voice-over: $subjectTitle",
                    body = "Attached: an ElevenLabs voice-over reading of \"$subjectTitle\"." +
                        if (text.length > ElevenLabsSpeechService.MAX_CHARS_PER_REQUEST) {
                            "\n\n(Truncated to ElevenLabs' per-request character limit.)"
                        } else "",
                    attachment = EmailAttachment(fileName = "voiceover.mp3", mimeType = "audio/mpeg", bytes = bytes)
                ).getOrThrow()

                "Sent to $to"
            }.getOrElse { e -> "Failed: ${e.message}" }
        }
    }
}

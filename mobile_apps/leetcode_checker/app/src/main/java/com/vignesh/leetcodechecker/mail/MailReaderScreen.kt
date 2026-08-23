package com.vignesh.leetcodechecker.mail

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vignesh.leetcodechecker.AppSettingsStore
import com.vignesh.leetcodechecker.email.ImapClient
import com.vignesh.leetcodechecker.email.MailSummary
import com.vignesh.leetcodechecker.tts.VoicePlayback
import kotlinx.coroutines.launch

/**
 * Reads one email's body (fetched on demand via IMAP) and offers the same Read Aloud /
 * Mail Voice-Over pipeline the Book Reader uses -- same VoicePlayback helper, same
 * Android-TTS/ElevenLabs provider choice from Global Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailReaderScreen(summary: MailSummary, onBackClick: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appSettings = remember { AppSettingsStore.load(context) }

    var bodyText by remember { mutableStateOf<String?>(null) }
    var isLoadingBody by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    var isSpeaking by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    var isMailing by remember { mutableStateOf(false) }
    var mailStatus by remember { mutableStateOf<String?>(null) }
    var readAloudError by remember { mutableStateOf<String?>(null) }

    val ttsHolder = remember { arrayOfNulls<TextToSpeech>(1) }
    val mediaPlayerHolder = remember { arrayOfNulls<android.media.MediaPlayer>(1) }
    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS }
        ttsHolder[0] = engine
        onDispose {
            runCatching { engine.stop() }
            engine.shutdown()
            mediaPlayerHolder[0]?.let { runCatching { it.stop(); it.release() } }
        }
    }

    LaunchedEffect(summary.uid) {
        isLoadingBody = true
        loadError = null
        val settings = AppSettingsStore.load(context)
        if (settings.notificationEmailFrom.isBlank() || settings.notificationEmailAppPassword.isBlank()) {
            loadError = "Configure your Gmail address and App Password in Global Settings first."
        } else {
            ImapClient.fetchMessageBody(settings.notificationEmailFrom, settings.notificationEmailAppPassword, summary.uid).fold(
                onSuccess = { bodyText = it },
                onFailure = { e -> loadError = e.message ?: "Couldn't load this message." }
            )
        }
        isLoadingBody = false
    }

    val speakableText = bodyText.orEmpty()
    val voiceReady = if (appSettings.ttsProvider == "elevenlabs") appSettings.elevenLabsApiKey.isNotBlank() else ttsReady

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(summary.subject, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(summary.subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(summary.from, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (summary.dateText.isNotBlank()) {
                    Text(summary.dateText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isLoadingBody -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    loadError != null -> Text(
                        loadError!!,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> Text(
                        text = bodyText.orEmpty(),
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        fontSize = 14.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    enabled = voiceReady && speakableText.isNotBlank(),
                    onClick = {
                        readAloudError = null
                        if (isSpeaking) {
                            if (appSettings.ttsProvider == "elevenlabs") {
                                mediaPlayerHolder[0]?.let { runCatching { it.stop(); it.release() } }
                                mediaPlayerHolder[0] = null
                            } else {
                                ttsHolder[0]?.stop()
                            }
                            isSpeaking = false
                        } else if (appSettings.ttsProvider == "elevenlabs") {
                            scope.launch {
                                VoicePlayback.speakWithElevenLabs(
                                    context = context,
                                    apiKey = appSettings.elevenLabsApiKey,
                                    voiceId = appSettings.elevenLabsVoiceId,
                                    text = speakableText,
                                    mediaPlayerHolder = mediaPlayerHolder,
                                    onSpeakingChange = { speaking -> isSpeaking = speaking },
                                    onError = { err -> readAloudError = err }
                                )
                            }
                        } else {
                            VoicePlayback.speakWithAndroidTts(ttsHolder[0], speakableText) { speaking -> isSpeaking = speaking }
                        }
                    }
                ) {
                    Text(if (isSpeaking) "⏹ Stop" else "🔊 Read Aloud")
                }

                OutlinedButton(
                    enabled = voiceReady && speakableText.isNotBlank() && !isMailing,
                    onClick = {
                        isMailing = true
                        mailStatus = null
                        scope.launch {
                            mailStatus = if (appSettings.ttsProvider == "elevenlabs") {
                                VoicePlayback.mailVoiceOverElevenLabs(
                                    context, appSettings.elevenLabsApiKey, appSettings.elevenLabsVoiceId,
                                    summary.subject, speakableText
                                )
                            } else {
                                VoicePlayback.mailVoiceOverAndroidTts(context, ttsHolder[0], summary.subject, speakableText)
                            }
                            isMailing = false
                        }
                    }
                ) {
                    Text(if (isMailing) "Mailing..." else "✉️ Mail Voice-Over")
                }
            }
            readAloudError?.let { err ->
                Text(
                    text = err,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            mailStatus?.let { status ->
                Text(
                    text = status,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status.startsWith("Sent")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

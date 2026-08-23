package com.vignesh.leetcodechecker.bookreader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.speech.tts.TextToSpeech
import android.webkit.WebView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vignesh.leetcodechecker.AppSettingsStore
import com.vignesh.leetcodechecker.tts.VoicePlayback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Views a book (TXT/EPUB/PDF) and can read the current chapter aloud (Android's built-in
 * TextToSpeech -- no third-party dependency or API key needed) or synthesize it to a WAV
 * and email it via the same GmailSmtpSender used for push notifications.
 *
 * EPUB rendering: renders each chapter's raw XHTML in a WebView -- simplest faithful
 * rendering (keeps the book's own formatting) without a heavy EPUB-rendering dependency.
 * PDF rendering: Android's built-in PdfRenderer rasterizes pages to bitmaps for the visual
 * view; PdfTextExtractor (PDFBox-Android) separately pulls the current page's text so
 * read-aloud/mail-voice-over work for PDFs too. A page with no embedded text layer (e.g. a
 * scanned image) still has nothing to read -- that's flagged in the UI, not a bug.
 *
 * Voice provider: Android's built-in TextToSpeech (free, offline) or ElevenLabs (paid API,
 * more natural voices) per AppSettings.ttsProvider, configured in Global Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookReaderScreen(
    book: Book,
    onBackClick: () -> Unit,
    onProgress: (Book) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bookFile = remember(book) { File(BookLibraryStorage.booksDir(context), book.storedFileName) }
    val appSettings = remember { AppSettingsStore.load(context) }

    var chapterIndex by remember { mutableStateOf(book.lastChapterIndex) }
    var pdfPageCount by remember { mutableStateOf(1) }
    var pdfPageText by remember { mutableStateOf("") }
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

    val epubChapters = remember(bookFile) {
        if (book.format == BookFormat.EPUB) runCatching { EpubReader.loadChapters(bookFile) }.getOrDefault(emptyList())
        else emptyList()
    }
    val txtContent = remember(bookFile) {
        if (book.format == BookFormat.TXT) runCatching { bookFile.readText() }.getOrDefault("") else ""
    }

    val chapterCount = when (book.format) {
        BookFormat.EPUB -> epubChapters.size
        BookFormat.PDF -> pdfPageCount
        else -> 1
    }
    val safeIndex = chapterIndex.coerceIn(0, (chapterCount - 1).coerceAtLeast(0))

    LaunchedEffect(safeIndex) {
        if (safeIndex != book.lastChapterIndex) onProgress(book.copy(lastChapterIndex = safeIndex))
    }

    LaunchedEffect(bookFile, safeIndex, book.format) {
        if (book.format == BookFormat.PDF) {
            pdfPageText = PdfTextExtractor.extractPageText(context, bookFile, safeIndex).getOrDefault("")
        }
    }

    val chapterHtml = epubChapters.getOrNull(safeIndex)?.html
    val chapterTitle = epubChapters.getOrNull(safeIndex)?.title ?: book.title
    val speakableText = when (book.format) {
        BookFormat.TXT -> txtContent
        BookFormat.EPUB -> chapterHtml?.let { EpubReader.plainText(it) }.orEmpty()
        BookFormat.PDF -> pdfPageText
        else -> ""
    }
    val voiceReady = if (appSettings.ttsProvider == "elevenlabs") appSettings.elevenLabsApiKey.isNotBlank() else ttsReady

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (book.format) {
                    BookFormat.TXT -> Text(
                        text = txtContent,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        fontSize = 14.sp
                    )
                    BookFormat.EPUB -> {
                        if (chapterHtml != null) {
                            AndroidView(
                                factory = { ctx -> WebView(ctx).apply { settings.javaScriptEnabled = false } },
                                update = { webView -> webView.loadDataWithBaseURL(null, chapterHtml, "text/html", "UTF-8", null) },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                "Couldn't read any chapters from this EPUB file.",
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    BookFormat.PDF -> PdfPageView(
                        file = bookFile,
                        pageIndex = safeIndex,
                        onPageCount = { pdfPageCount = it }
                    )
                    BookFormat.UNKNOWN -> Text(
                        "Unrecognized file format.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (book.format == BookFormat.EPUB || book.format == BookFormat.PDF) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { chapterIndex = (safeIndex - 1).coerceAtLeast(0) }, enabled = safeIndex > 0) {
                        Text("◀ Prev")
                    }
                    Text(
                        text = if (book.format == BookFormat.EPUB) "Chapter ${safeIndex + 1} / $chapterCount"
                        else "Page ${safeIndex + 1} / $chapterCount",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(
                        onClick = { chapterIndex = (safeIndex + 1).coerceAtMost(chapterCount - 1) },
                        enabled = safeIndex < chapterCount - 1
                    ) {
                        Text("Next ▶")
                    }
                }
            }

            if (book.format == BookFormat.PDF && speakableText.isBlank()) {
                Text(
                    "This page doesn't have any extractable text (it may be a scanned image), " +
                        "so voice-over isn't available for it.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                                    chapterTitle, speakableText
                                )
                            } else {
                                VoicePlayback.mailVoiceOverAndroidTts(context, ttsHolder[0], chapterTitle, speakableText)
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

@Composable
private fun PdfPageView(file: File, pageIndex: Int, onPageCount: (Int) -> Unit) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(file, pageIndex) {
        withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        onPageCount(renderer.pageCount)
                        val safePage = pageIndex.coerceIn(0, renderer.pageCount - 1)
                        renderer.openPage(safePage).use { page ->
                            val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap = bmp
                        }
                    }
                }
            }.onFailure { e -> error = e.message ?: "Couldn't render this PDF page." }
        }
    }

    when {
        bitmap != null -> Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "PDF page",
            modifier = Modifier.fillMaxSize()
        )
        error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}


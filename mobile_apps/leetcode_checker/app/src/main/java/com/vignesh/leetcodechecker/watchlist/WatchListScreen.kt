package com.vignesh.leetcodechecker.watchlist

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shows YouTube videos bookmarked in Chrome, grouped and colored by their bookmark
 * folder. Chrome has no API for third-party apps to read bookmarks directly -- there's no
 * public content provider or sync API for this -- so the only real path is Chrome's own
 * "Export bookmarks" (Bookmark Manager -> ... -> Export bookmarks) producing an HTML file,
 * which gets imported here via the system file picker. "Refresh" means re-exporting from
 * Chrome and re-importing that file; it replaces the video list to match (dropping
 * removed bookmarks) while preserving each video's watched status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchListScreen(onBackClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var videos by remember { mutableStateOf(WatchListStorage.loadVideos(context)) }
    var categories by remember { mutableStateOf(WatchListStorage.loadCategories(context)) }
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var showUnwatchedOnly by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf<String?>(null) }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isImporting = true
        importStatus = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val html = context.contentResolver.openInputStream(uri)?.use { stream ->
                        BufferedReader(InputStreamReader(stream)).readText()
                    } ?: error("Couldn't read the selected file.")
                    val parsed = ChromeBookmarksParser.parse(html)
                    if (parsed.isEmpty()) error("No YouTube links found in that file -- make sure it's a Chrome bookmarks export.")
                    WatchListStorage.mergeImport(context, parsed)
                }
            }
            result.fold(
                onSuccess = { merged ->
                    videos = merged
                    categories = WatchListStorage.loadCategories(context)
                    importStatus = "Imported ${merged.size} video(s)."
                },
                onFailure = { e -> importStatus = "Import failed: ${e.message}" }
            )
            isImporting = false
        }
    }

    fun toggleWatched(video: BookmarkedVideo) {
        val updated = videos.map { if (it.id == video.id) it.copy(isWatched = !it.isWatched) else it }
        WatchListStorage.saveVideos(context, updated)
        videos = updated
    }

    val filtered = videos
        .filter { categoryFilter == null || it.category == categoryFilter }
        .filter { !showUnwatchedOnly || !it.isWatched }
        .sortedByDescending { it.importedAtMillis }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("📺 Watch List") },
                    navigationIcon = {
                        if (onBackClick != null) {
                            IconButton(onClick = onBackClick) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            enabled = !isImporting,
                            onClick = { importPicker.launch(arrayOf("text/html")) }
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Import / Refresh from Chrome export")
                        }
                    }
                )
                if (categories.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(selected = categoryFilter == null, onClick = { categoryFilter = null }, label = { Text("All") })
                        categories.forEach { cat ->
                            FilterChip(
                                selected = categoryFilter == cat.name,
                                onClick = { categoryFilter = if (categoryFilter == cat.name) null else cat.name },
                                label = { Text(cat.name) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier.size(10.dp).clip(CircleShape)
                                            .background(runCatching { Color(android.graphics.Color.parseColor(cat.colorHex)) }.getOrDefault(Color.Gray))
                                    )
                                }
                            )
                        }
                        FilterChip(selected = showUnwatchedOnly, onClick = { showUnwatchedOnly = !showUnwatchedOnly }, label = { Text("Unwatched") })
                    }
                }
                if (isImporting) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                importStatus?.let { status ->
                    Text(
                        status,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status.startsWith("Import failed")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    ) { padding ->
        if (videos.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📺", style = MaterialTheme.typography.displayMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No videos yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "In Chrome: menu (⋮) → Bookmarks → Bookmark Manager → menu (⋮) → Export bookmarks. " +
                            "Then tap the refresh icon above and pick that file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { importPicker.launch(arrayOf("text/html")) }) {
                        Text("Import Bookmarks")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { video ->
                    val categoryColor = categories.firstOrNull { it.name == video.category }?.colorHex
                    VideoRow(
                        video = video,
                        categoryColorHex = categoryColor,
                        onToggleWatched = { toggleWatched(video) },
                        onOpen = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video.url)))
                            }.onFailure {
                                Toast.makeText(context, "No app found to open this link.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun VideoRow(
    video: BookmarkedVideo,
    categoryColorHex: String?,
    onToggleWatched: () -> Unit,
    onOpen: () -> Unit
) {
    Card(onClick = onOpen, shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = YouTubeUrlParser.thumbnailUrl(video.videoId),
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = 96.dp, height = 60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = if (video.isWatched) 0.5f else 0f))
                )
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White.copy(alpha = 0.9f))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    textDecoration = if (video.isWatched) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                    color = if (video.isWatched) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    categoryColorHex?.let { hex ->
                        Box(
                            modifier = Modifier.size(8.dp).clip(CircleShape)
                                .background(runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(video.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Checkbox(checked = video.isWatched, onCheckedChange = { onToggleWatched() })
        }
    }
}

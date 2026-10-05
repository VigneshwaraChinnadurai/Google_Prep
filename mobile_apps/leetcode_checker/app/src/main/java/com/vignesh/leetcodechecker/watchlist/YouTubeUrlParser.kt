package com.vignesh.leetcodechecker.watchlist

import android.net.Uri

/** Extracts the 11-character video ID from the common YouTube URL shapes, or null if the
 *  URL isn't a YouTube video link at all (used to filter non-video bookmarks out). */
object YouTubeUrlParser {
    private val VIDEO_ID_PATTERN = Regex("^[a-zA-Z0-9_-]{11}$")

    fun extractVideoId(url: String): String? {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (!host.contains("youtube.com") && !host.contains("youtu.be")) return null

        val candidate = when {
            host.contains("youtu.be") -> uri.pathSegments.firstOrNull()
            uri.path?.startsWith("/shorts/") == true -> uri.pathSegments.getOrNull(1)
            uri.path?.startsWith("/embed/") == true -> uri.pathSegments.getOrNull(1)
            else -> uri.getQueryParameter("v")
        }

        return candidate?.takeIf { VIDEO_ID_PATTERN.matches(it) }
    }

    fun thumbnailUrl(videoId: String): String = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

    fun watchUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"
}

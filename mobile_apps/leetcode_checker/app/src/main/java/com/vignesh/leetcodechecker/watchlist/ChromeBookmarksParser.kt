package com.vignesh.leetcodechecker.watchlist

import org.jsoup.Jsoup

/**
 * Parses a Chrome "Export bookmarks" file (Netscape Bookmark File Format -- old-style
 * HTML with unclosed <DT>/<P> tags, not well-formed XML/HTML5) and pulls out only the
 * YouTube video links, grouped by their enclosing bookmark folder.
 *
 * Approach: scan <h3> (folder heading) and <a href> (bookmark) elements in document
 * order and track "most recently seen folder heading" as each link's category. This is
 * correct regardless of exactly how Jsoup's lenient parser nests the malformed <DL>/<DT>
 * tag soup, because the export format is inherently a depth-first, pre-order listing --
 * a folder's heading always appears immediately before that folder's own links.
 */
object ChromeBookmarksParser {
    fun parse(html: String): List<BookmarkedVideo> {
        val doc = Jsoup.parse(html)
        val results = mutableListOf<BookmarkedVideo>()
        var currentFolder = "Uncategorized"

        for (el in doc.select("h3, a[href]")) {
            if (el.tagName().equals("h3", ignoreCase = true)) {
                currentFolder = el.text().trim().ifBlank { currentFolder }
            } else {
                val videoId = YouTubeUrlParser.extractVideoId(el.attr("href")) ?: continue
                results.add(
                    BookmarkedVideo(
                        title = el.text().trim().ifBlank { "Untitled" },
                        url = YouTubeUrlParser.watchUrl(videoId),
                        videoId = videoId,
                        category = currentFolder
                    )
                )
            }
        }
        return results
    }
}

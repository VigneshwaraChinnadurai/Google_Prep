package com.vignesh.leetcodechecker.bookreader

import java.io.File
import java.util.zip.ZipFile

data class EpubChapter(val title: String, val html: String)

/**
 * Minimal EPUB parsing: EPUB is a zip of XHTML chapter files. Rather than fully parsing
 * the OPF manifest/spine (the "correct" way to determine true reading order), this lists
 * every XHTML/HTML entry in the zip sorted by path -- correct for the common case where
 * chapter files are already named/numbered in reading order (chapter01.xhtml,
 * chapter02.xhtml, ...). Good enough for a first version; revisit with real OPF/spine
 * parsing if a book's chapter order comes out wrong in practice.
 */
object EpubReader {
    fun loadChapters(file: File): List<EpubChapter> {
        val chapters = mutableListOf<EpubChapter>()
        ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
                .filter { entry ->
                    !entry.isDirectory &&
                        (entry.name.endsWith(".xhtml") || entry.name.endsWith(".html") || entry.name.endsWith(".htm"))
                }
                .sortedBy { it.name }
            entries.forEach { entry ->
                val html = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText()
                val title = Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
                    .find(html)?.groupValues?.get(1)?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: entry.name.substringAfterLast('/')
                chapters.add(EpubChapter(title = title, html = html))
            }
        }
        return chapters
    }

    /** Strips an XHTML chapter down to plain text, for TTS input and search/preview. */
    fun plainText(html: String): String {
        return html
            .replace(Regex("<(script|style)[^>]*>.*?</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), " ")
            .replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

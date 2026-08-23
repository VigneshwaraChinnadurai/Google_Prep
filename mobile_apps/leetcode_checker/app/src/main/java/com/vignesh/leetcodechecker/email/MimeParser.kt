package com.vignesh.leetcodechecker.email

import com.vignesh.leetcodechecker.bookreader.EpubReader
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Best-effort RFC 822/2045 MIME parsing, scoped to the common case: multipart/mixed or
 * multipart/alternative with a text/plain or text/html leaf, base64/quoted-printable
 * transfer encoding, UTF-8/Latin-1-family charsets. Unusual structures (deeply nested
 * parts, inline images, uncommon charsets, S/MIME) may not render perfectly -- revisit if
 * it comes up wrong in practice, same tolerance as EpubReader's chapter-order heuristic.
 */
object MimeParser {

    fun extractPlainText(rawMessage: String): String {
        return runCatching { extractBestPart(rawMessage) }
            .getOrDefault("Couldn't parse this message's content.")
            .ifBlank { "This message has no readable text content." }
    }

    private fun extractBestPart(message: String): String {
        val (headers, body) = splitHeaderBody(message) ?: return message.take(4000)
        val contentType = headerValue(headers, "Content-Type").lowercase()
        val boundary = Regex("""boundary\s*=\s*"?([^";\r\n]+)"?""", RegexOption.IGNORE_CASE)
            .find(headers)?.groupValues?.get(1)

        return if (contentType.contains("multipart/") && !boundary.isNullOrBlank()) {
            findBestAmongParts(splitOnBoundary(body, boundary)) ?: decodeSinglePart(headers, body)
        } else {
            decodeSinglePart(headers, body)
        }
    }

    private fun findBestAmongParts(parts: List<String>): String? {
        // First pass: a direct text/plain leaf, recursing into nested multiparts
        // (Gmail typically wraps multipart/alternative inside multipart/mixed).
        for (part in parts) {
            val (h, b) = splitHeaderBody(part) ?: continue
            val ct = headerValue(h, "Content-Type").lowercase()
            if (ct.contains("multipart/")) {
                val nestedBoundary = Regex("""boundary\s*=\s*"?([^";\r\n]+)"?""", RegexOption.IGNORE_CASE)
                    .find(h)?.groupValues?.get(1)
                if (!nestedBoundary.isNullOrBlank()) {
                    findBestAmongParts(splitOnBoundary(b, nestedBoundary))?.let { return it }
                }
            } else if (ct.startsWith("text/plain") || ct.isBlank()) {
                return decodeSinglePart(h, b)
            }
        }
        // Second pass: fall back to text/html, stripped to plain text.
        for (part in parts) {
            val (h, b) = splitHeaderBody(part) ?: continue
            val ct = headerValue(h, "Content-Type").lowercase()
            if (ct.contains("multipart/")) {
                val nestedBoundary = Regex("""boundary\s*=\s*"?([^";\r\n]+)"?""", RegexOption.IGNORE_CASE)
                    .find(h)?.groupValues?.get(1)
                if (!nestedBoundary.isNullOrBlank()) {
                    findHtmlAmongParts(splitOnBoundary(b, nestedBoundary))?.let { return it }
                }
            } else if (ct.startsWith("text/html")) {
                return EpubReader.plainText(decodeSinglePart(h, b))
            }
        }
        return null
    }

    private fun findHtmlAmongParts(parts: List<String>): String? {
        for (part in parts) {
            val (h, b) = splitHeaderBody(part) ?: continue
            val ct = headerValue(h, "Content-Type").lowercase()
            if (ct.startsWith("text/html")) return EpubReader.plainText(decodeSinglePart(h, b))
        }
        return null
    }

    private fun decodeSinglePart(headers: String, body: String): String {
        val encoding = headerValue(headers, "Content-Transfer-Encoding").lowercase().trim()
        val charsetName = Regex("""charset\s*=\s*"?([^";\r\n]+)"?""", RegexOption.IGNORE_CASE)
            .find(headers)?.groupValues?.get(1) ?: "UTF-8"
        val charset = runCatching { Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8)
        return when (encoding) {
            "base64" -> runCatching {
                String(
                    android.util.Base64.decode(body.replace("\r", "").replace("\n", ""), android.util.Base64.DEFAULT),
                    charset
                )
            }.getOrDefault(body)
            "quoted-printable" -> decodeQuotedPrintable(body, charset)
            else -> body
        }.trim()
    }

    private fun splitHeaderBody(text: String): Pair<String, String>? {
        val crlfIdx = text.indexOf("\r\n\r\n")
        val lfIdx = text.indexOf("\n\n")
        val (splitIdx, sepLen) = when {
            crlfIdx in 0 until (if (lfIdx >= 0) lfIdx else Int.MAX_VALUE) -> crlfIdx to 4
            lfIdx >= 0 -> lfIdx to 2
            else -> return null
        }
        return text.substring(0, splitIdx) to text.substring(splitIdx + sepLen)
    }

    private fun headerValue(headers: String, key: String): String {
        return Regex("(?im)^$key:\\s*(.*(?:\\r?\\n[ \\t].*)*)")
            .find(headers)?.groupValues?.get(1)
            ?.replace(Regex("\\r?\\n[ \\t]"), " ")
            ?.trim()
            .orEmpty()
    }

    private fun splitOnBoundary(body: String, boundary: String): List<String> {
        val escaped = Regex.escape(boundary)
        return body.split(Regex("--$escaped(--)?\\r?\\n?"))
            .map { it.trim('\r', '\n') }
            .filter { it.isNotBlank() }
    }

    private fun decodeQuotedPrintable(text: String, charset: Charset): String {
        val unfolded = text.replace("=\r\n", "").replace("=\n", "")
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < unfolded.length) {
            val c = unfolded[i]
            if (c == '=' && i + 2 < unfolded.length && unfolded[i + 1].isHexDigit() && unfolded[i + 2].isHexDigit()) {
                out.write(unfolded.substring(i + 1, i + 3).toInt(16))
                i += 3
            } else {
                out.write(c.code)
                i += 1
            }
        }
        return String(out.toByteArray(), charset)
    }

    private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    /** RFC 2047 encoded-word decoding for header values like From/Subject. */
    fun decodeEncodedWords(input: String): String {
        val encodedWordRegex = Regex("""=\?([^?]+)\?([BbQq])\?([^?]*)\?=""")
        return encodedWordRegex.replace(input) { m ->
            val charset = runCatching { Charset.forName(m.groupValues[1]) }.getOrDefault(Charsets.UTF_8)
            val encoding = m.groupValues[2].uppercase()
            val text = m.groupValues[3]
            runCatching {
                when (encoding) {
                    "B" -> String(android.util.Base64.decode(text, android.util.Base64.DEFAULT), charset)
                    "Q" -> decodeQuotedPrintable(text.replace('_', ' '), charset)
                    else -> text
                }
            }.getOrDefault(text)
        }
    }
}

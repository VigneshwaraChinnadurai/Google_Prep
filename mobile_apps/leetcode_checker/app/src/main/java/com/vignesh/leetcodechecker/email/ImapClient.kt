package com.vignesh.leetcodechecker.email

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import javax.net.ssl.SSLSocketFactory

data class MailSummary(
    val uid: Long,
    val from: String,
    val subject: String,
    val dateText: String,
    val isUnread: Boolean
)

/**
 * Reads Gmail's inbox via raw IMAPS (port 993), reusing the same Gmail App Password
 * already configured for GmailSmtpSender -- Gmail App Passwords work for both SMTP and
 * IMAP, so no separate credential or OAuth flow is needed. Matches this app's existing
 * convention of raw protocol calls over heavy SDKs (see GmailSmtpSender).
 */
object ImapClient {
    private const val TAG = "ImapClient"
    private const val HOST = "imap.gmail.com"
    private const val PORT = 993

    suspend fun fetchInboxSummaries(email: String, appPassword: String, limit: Int = 30): Result<List<MailSummary>> =
        withContext(Dispatchers.IO) {
            runCatching {
                connect(email, appPassword) { input, output, nextTag ->
                    val selectTag = nextTag()
                    sendLine(output, "$selectTag SELECT INBOX")
                    val selectLines = readUntilTagged(input, selectTag)
                    val exists = selectLines.firstNotNullOfOrNull {
                        Regex("""\*\s+(\d+)\s+EXISTS""").find(it)?.groupValues?.get(1)?.toIntOrNull()
                    } ?: 0

                    if (exists == 0) {
                        emptyList()
                    } else {
                        val low = (exists - limit + 1).coerceAtLeast(1)
                        val fetchTag = nextTag()
                        sendLine(output, "$fetchTag FETCH $low:$exists (UID FLAGS BODY.PEEK[HEADER.FIELDS (FROM SUBJECT DATE)])")
                        val fetchLines = readUntilTagged(input, fetchTag)
                        parseSummaries(fetchLines).sortedByDescending { it.uid }
                    }
                }
            }.onFailure { e -> Log.e(TAG, "fetchInboxSummaries failed", e) }
        }

    suspend fun fetchMessageBody(email: String, appPassword: String, uid: Long): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                connect(email, appPassword) { input, output, nextTag ->
                    val selectTag = nextTag()
                    sendLine(output, "$selectTag SELECT INBOX")
                    readUntilTagged(input, selectTag)

                    val fetchTag = nextTag()
                    sendLine(output, "$fetchTag UID FETCH $uid (BODY.PEEK[])")
                    val fetchLines = readUntilTagged(input, fetchTag)
                    val fetchLine = fetchLines.firstOrNull { it.trimStart().startsWith("*") }
                        ?: error("No message content returned.")
                    MimeParser.extractPlainText(fetchLine)
                }
            }.onFailure { e -> Log.e(TAG, "fetchMessageBody failed", e) }
        }

    private fun <T> connect(
        email: String,
        appPassword: String,
        block: (InputStream, OutputStream, nextTag: () -> String) -> T
    ): T {
        val socket = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(HOST, PORT)
        socket.soTimeout = 20_000
        return socket.use {
            val input = BufferedInputStream(it.getInputStream())
            val output = it.getOutputStream()
            readLogicalLine(input) // discard greeting

            var tagCounter = 0
            val nextTag: () -> String = { "a${++tagCounter}" }

            val loginTag = nextTag()
            sendLine(output, "$loginTag LOGIN ${quote(email)} ${quote(appPassword)}")
            val loginLines = readUntilTagged(input, loginTag)
            if (loginLines.lastOrNull()?.trimStart()?.startsWith("$loginTag OK") != true) {
                error("IMAP login failed -- check the email/app password in Global Settings: ${loginLines.lastOrNull()}")
            }

            val result = block(input, output, nextTag)

            val logoutTag = nextTag()
            runCatching {
                sendLine(output, "$logoutTag LOGOUT")
                readUntilTagged(input, logoutTag)
            }
            result
        }
    }

    private fun quote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun sendLine(out: OutputStream, line: String) {
        out.write((line + "\r\n").toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun readLine(input: InputStream): String {
        val buf = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) break
            if (b == '\r'.code) continue
            if (b == '\n'.code) break
            buf.append(b.toChar())
        }
        return buf.toString()
    }

    private val literalMarker = Regex("""\{(\d+)\}\s*$""")

    /**
     * Reads one IMAP response line, transparently inlining any trailing "{N}" literal by
     * reading exactly N raw bytes and splicing them in, then continuing to read the rest of
     * that same logical line. IMAP literals are how it embeds binary-safe blobs (header/body
     * content) mid-response, so a plain line-oriented reader would otherwise misinterpret
     * CRLFs inside the literal's own content as response-line boundaries.
     */
    private fun readLogicalLine(input: InputStream): String {
        var line = readLine(input)
        while (true) {
            val match = literalMarker.find(line) ?: return line
            val n = match.groupValues[1].toInt()
            val bytes = ByteArray(n)
            var readCount = 0
            while (readCount < n) {
                val r = input.read(bytes, readCount, n - readCount)
                if (r == -1) break
                readCount += r
            }
            val literalText = String(bytes, Charsets.UTF_8)
            line = line.substring(0, match.range.first) + literalText + readLine(input)
        }
    }

    private fun readUntilTagged(input: InputStream, tag: String): List<String> {
        val lines = mutableListOf<String>()
        while (true) {
            val line = readLogicalLine(input)
            lines.add(line)
            if (line.trimStart().startsWith("$tag ") || line.trim() == tag) break
        }
        return lines
    }

    private fun parseSummaries(lines: List<String>): List<MailSummary> {
        return lines.mapNotNull { line ->
            if (!line.trimStart().startsWith("*")) return@mapNotNull null
            val uid = Regex("""UID\s+(\d+)""").find(line)?.groupValues?.get(1)?.toLongOrNull()
                ?: return@mapNotNull null
            val flags = Regex("""FLAGS\s+\(([^)]*)\)""").find(line)?.groupValues?.get(1).orEmpty()
            val isUnread = !flags.contains("\\Seen", ignoreCase = true)
            val from = extractHeader(line, "From").ifBlank { "(unknown sender)" }
            val subject = extractHeader(line, "Subject").ifBlank { "(no subject)" }
            val date = extractHeader(line, "Date")
            MailSummary(uid = uid, from = from, subject = subject, dateText = date, isUnread = isUnread)
        }
    }

    private fun extractHeader(text: String, key: String): String {
        val match = Regex("(?im)^$key:\\s*(.*(?:\\r?\\n[ \\t].*)*)").find(text) ?: return ""
        val raw = match.groupValues[1].replace(Regex("\\r?\\n[ \\t]"), " ").trim()
        return MimeParser.decodeEncodedWords(raw)
    }
}

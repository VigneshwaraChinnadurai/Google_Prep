package com.vignesh.leetcodechecker.email

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

data class EmailAttachment(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray
)

/**
 * Sends mail via Gmail's SMTP server using a Gmail App Password (Google Account ->
 * Security -> 2-Step Verification -> App Passwords), not full OAuth. Matches this app's
 * existing convention of raw protocol calls over official SDKs -- SMTP is a plain-text
 * protocol, not HTTP, so Retrofit/OkHttp don't apply and a full OAuth+Gmail-API integration
 * would be a lot of moving parts (Cloud project, consent screen, token refresh/storage) for
 * "send occasional short emails from my own account."
 */
object GmailSmtpSender {
    private const val TAG = "GmailSmtpSender"
    private const val HOST = "smtp.gmail.com"
    private const val PORT = 587

    suspend fun send(
        fromEmail: String,
        appPassword: String,
        toEmail: String,
        subject: String,
        body: String,
        attachment: EmailAttachment? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(fromEmail.isNotBlank() && appPassword.isNotBlank() && toEmail.isNotBlank()) {
                "Email sender/app password/recipient not configured. Set them in Global Settings."
            }

            Socket(HOST, PORT).use { plainSocket ->
                plainSocket.soTimeout = 15_000
                val plainReader = BufferedReader(InputStreamReader(plainSocket.getInputStream()))
                val plainOut = plainSocket.getOutputStream()

                readResponse(plainReader) // greeting
                sendLine(plainOut, "EHLO leetcodechecker.local")
                readResponse(plainReader)

                sendLine(plainOut, "STARTTLS")
                val startTlsResponse = readResponse(plainReader)
                if (!startTlsResponse.startsWith("220")) error("STARTTLS rejected: $startTlsResponse")

                val sslSocket = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                    .createSocket(plainSocket, HOST, PORT, true)
                val reader = BufferedReader(InputStreamReader(sslSocket.getInputStream()))
                val out = sslSocket.getOutputStream()

                sendLine(out, "EHLO leetcodechecker.local")
                readResponse(reader)

                sendLine(out, "AUTH LOGIN")
                readResponse(reader)
                sendLine(out, Base64.encodeToString(fromEmail.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                readResponse(reader)
                sendLine(out, Base64.encodeToString(appPassword.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                val authResponse = readResponse(reader)
                if (!authResponse.startsWith("235")) {
                    error("SMTP auth failed -- check the email/app password in Global Settings: $authResponse")
                }

                sendLine(out, "MAIL FROM:<$fromEmail>")
                readResponse(reader)
                sendLine(out, "RCPT TO:<$toEmail>")
                readResponse(reader)
                sendLine(out, "DATA")
                readResponse(reader)

                val boundary = "----leetcodechecker-${System.currentTimeMillis()}"
                val messageBody = buildString {
                    append("From: $fromEmail\r\n")
                    append("To: $toEmail\r\n")
                    append("Subject: $subject\r\n")
                    append("MIME-Version: 1.0\r\n")
                    if (attachment != null) {
                        append("Content-Type: multipart/mixed; boundary=\"$boundary\"\r\n")
                        append("\r\n--$boundary\r\n")
                        append("Content-Type: text/plain; charset=UTF-8\r\n\r\n")
                        append(body)
                        append("\r\n--$boundary\r\n")
                        append("Content-Type: ${attachment.mimeType}; name=\"${attachment.fileName}\"\r\n")
                        append("Content-Transfer-Encoding: base64\r\n")
                        append("Content-Disposition: attachment; filename=\"${attachment.fileName}\"\r\n\r\n")
                        // Wrap base64 at 76 chars ourselves (MIME line-length convention)
                        // rather than rely on Base64.DEFAULT's own line-wrapping behavior.
                        append(Base64.encodeToString(attachment.bytes, Base64.NO_WRAP).chunked(76).joinToString("\r\n"))
                        append("\r\n--$boundary--\r\n")
                    } else {
                        append("Content-Type: text/plain; charset=UTF-8\r\n\r\n")
                        append(body)
                        append("\r\n")
                    }
                }
                // Dot-stuff any line that starts with "." per SMTP spec (RFC 5321 4.5.2) so
                // it isn't mistaken for the end-of-data marker sent separately below.
                val dotStuffed = messageBody.split("\r\n").joinToString("\r\n") { line ->
                    if (line.startsWith(".")) ".$line" else line
                }
                sendLine(out, dotStuffed)
                sendLine(out, ".")
                val dataResponse = readResponse(reader)
                if (!dataResponse.startsWith("250")) error("Send failed: $dataResponse")

                sendLine(out, "QUIT")
                sslSocket.close()
            }
            Unit
        }.onFailure { e -> Log.e(TAG, "SMTP send failed", e) }
    }

    private fun sendLine(out: OutputStream, line: String) {
        out.write((line + "\r\n").toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun readResponse(reader: BufferedReader): String {
        val sb = StringBuilder()
        while (true) {
            val line = reader.readLine() ?: break
            sb.appendLine(line)
            // Multi-line SMTP responses use "250-" continuation prefixes; the final line
            // of a response has a space (not a dash) in the 4th column, e.g. "250 OK".
            if (line.length >= 4 && line[3] == ' ') break
        }
        return sb.toString()
    }
}

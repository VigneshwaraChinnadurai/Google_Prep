package com.vignesh.jobmatcher.export

import com.vignesh.jobmatcher.data.ContactInfo
import com.vignesh.jobmatcher.model.Job
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A cover letter laid out as a proper business letter (letterhead, date, recipient,
 * subject line, then Claude's letter body) and rendered to TXT or DOCX. DOCX is a minimal
 * hand-built WordprocessingML package -- Word, Google Docs and every ATS upload form open it
 * -- so no Office library is needed; PDF rendering lives in [CoverLetterPdf] (Android-only).
 */
data class CoverLetterDocument(
    val contact: ContactInfo,
    val job: Job,
    val body: String,
    val date: Date = Date()
) {
    enum class Format(val extension: String, val mimeType: String, val label: String) {
        DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Word (.docx)"),
        PDF("pdf", "application/pdf", "PDF (.pdf)"),
        TXT("txt", "text/plain", "Text (.txt)")
    }

    /** A paragraph of the laid-out letter; [style] drives bold/size in DOCX and PDF. */
    data class Para(val text: String, val style: Style = Style.BODY)
    enum class Style { NAME, CONTACT, BODY, SUBJECT }

    private val jobRef: String
        get() = Regex("""(?:JR|R|REQ)[-_]?\d{4,}|\b\d{6,}\b""").find(job.url)?.value?.let { " (Job ID $it)" }.orEmpty()

    val paragraphs: List<Para>
        get() = buildList {
            add(Para(contact.name, Style.NAME))
            if (contact.contactLine.isNotBlank()) add(Para(contact.contactLine, Style.CONTACT))
            add(Para(""))
            add(Para(SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH).format(date)))
            add(Para(""))
            add(Para("Hiring Team"))
            add(Para(job.companyName))
            if (job.location.isNotBlank()) add(Para(job.location.substringBefore(';').trim()))
            add(Para(""))
            add(Para("Re: Application for ${job.title}$jobRef", Style.SUBJECT))
            add(Para(""))
            body.trim().lines().forEach { add(Para(it.trim())) }
        }

    fun fileName(format: Format): String {
        fun clean(s: String) = s.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').take(40)
        return "Cover_Letter_${clean(contact.name.ifBlank { "Candidate" })}_${clean(job.companyName)}_${clean(job.title)}.${format.extension}"
    }

    fun toText(): String = paragraphs.joinToString("\n") { it.text } + "\n"

    fun toDocx(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, xml: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""
            )
            put(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
            )
            put("word/document.xml", documentXml())
        }
        return out.toByteArray()
    }

    private fun documentXml(): String {
        val body = paragraphs.joinToString("") { p ->
            val (bold, halfPoints) = when (p.style) {
                Style.NAME -> true to 32      // 16pt
                Style.CONTACT -> false to 19  // 9.5pt
                Style.SUBJECT -> true to 22
                Style.BODY -> false to 22     // 11pt
            }
            val run = if (p.text.isEmpty()) "" else
                """<w:r><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Calibri"/>${if (bold) "<w:b/>" else ""}<w:sz w:val="$halfPoints"/></w:rPr><w:t xml:space="preserve">${xmlEscape(p.text)}</w:t></w:r>"""
            """<w:p><w:pPr><w:spacing w:after="0" w:line="276" w:lineRule="auto"/></w:pPr>$run</w:p>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="0" w:footer="0" w:gutter="0"/></w:sectPr></w:body></w:document>"""
    }

    companion object {
        fun xmlEscape(s: String): String = buildString {
            s.forEach { c ->
                when {
                    c == '&' -> append("&amp;")
                    c == '<' -> append("&lt;")
                    c == '>' -> append("&gt;")
                    c == '"' -> append("&quot;")
                    // Control characters are illegal in XML 1.0 (would make Word refuse the file).
                    c < ' ' && c != '\t' -> Unit
                    else -> append(c)
                }
            }
        }
    }
}

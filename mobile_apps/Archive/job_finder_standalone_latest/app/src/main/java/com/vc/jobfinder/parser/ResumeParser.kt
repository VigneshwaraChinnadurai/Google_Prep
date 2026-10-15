package com.vc.jobfinder.parser

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.vc.jobfinder.domain.Resume
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local resume parser. Extracts text from PDF or DOCX and runs lightweight
 * heuristics to populate fields. Heavy lifting (job-targeted skill extraction)
 * happens later in the LLM matcher, not here.
 */
@Singleton
class ResumeParser @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    init {
        // PDFBox-Android needs explicit resource loading from assets.
        PDFBoxResourceLoader.init(ctx)
    }

    suspend fun parse(stream: InputStream, filename: String): Resume = withContext(Dispatchers.IO) {
        val text = when {
            filename.endsWith(".pdf", ignoreCase = true) -> extractPdf(stream)
            filename.endsWith(".docx", ignoreCase = true) -> extractDocx(stream)
            else -> throw IllegalArgumentException("Unsupported file type: $filename")
        }
        toResume(text)
    }

    private fun extractPdf(stream: InputStream): String {
        return PDDocument.load(stream).use { doc ->
            PDFTextStripper().getText(doc)
        }
    }

    private fun extractDocx(stream: InputStream): String {
        return XWPFDocument(stream).use { doc ->
            buildString {
                doc.paragraphs.forEach { append(it.text).append('\n') }
                doc.tables.forEach { table ->
                    table.rows.forEach { row ->
                        row.tableCells.forEach { cell -> append(cell.text).append(' ') }
                        append('\n')
                    }
                }
            }
        }
    }

    private fun toResume(rawText: String): Resume {
        val text = rawText.replace("\u0000", "").trim()
        val email = EMAIL.find(text)?.value ?: ""
        val phone = PHONE.find(text)?.value
        val linkedin = LINKEDIN.find(text)?.value
        val github = GITHUB.find(text)?.value
        val portfolio = URL_GENERAL.findAll(text)
            .map { it.value }
            .firstOrNull { url -> url !in listOfNotNull(linkedin, github) && "linkedin.com" !in url && "github.com" !in url }

        // Name = first non-empty line that doesn't look like contact info or section header
        val name = text.lineSequence()
            .map { it.trim() }
            .firstOrNull { line ->
                line.isNotEmpty() &&
                line.length in 3..60 &&
                EMAIL.containsMatchIn(line).not() &&
                PHONE.containsMatchIn(line).not() &&
                URL_GENERAL.containsMatchIn(line).not() &&
                line.split(' ').size in 1..5 &&
                line.none { it.isDigit() }
            } ?: ""
        val parts = name.split(Regex("\\s+")).filter { it.isNotBlank() }
        val first = parts.firstOrNull().orEmpty()
        val last  = if (parts.size > 1) parts.drop(1).joinToString(" ") else ""

        return Resume(
            firstName = first,
            lastName = last,
            email = email,
            phone = phone,
            linkedinUrl = linkedin?.normalizeUrl(),
            githubUrl = github?.normalizeUrl(),
            portfolioUrl = portfolio?.normalizeUrl(),
            topSkills = emptyList(),  // populated by LLM at first scoring pass
            rawText = text.take(20_000),  // cap to keep token cost predictable
        )
    }

    private fun String.normalizeUrl(): String =
        if (startsWith("http", ignoreCase = true)) this else "https://$this"

    private companion object {
        val EMAIL    = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
        val PHONE    = Regex("\\+?\\d[\\d\\s().-]{7,}\\d")
        val LINKEDIN = Regex("(https?://)?(www\\.)?linkedin\\.com/in/[A-Za-z0-9_-]+", RegexOption.IGNORE_CASE)
        val GITHUB   = Regex("(https?://)?(www\\.)?github\\.com/[A-Za-z0-9_-]+", RegexOption.IGNORE_CASE)
        val URL_GENERAL = Regex("https?://[\\w./?=&%-]+", RegexOption.IGNORE_CASE)
    }
}

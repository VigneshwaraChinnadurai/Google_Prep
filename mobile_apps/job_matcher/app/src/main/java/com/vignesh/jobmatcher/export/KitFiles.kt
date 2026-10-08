package com.vignesh.jobmatcher.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

/** PDF rendering of a [CoverLetterDocument] (A4, 2 cm margins, paginated). */
object CoverLetterPdf {
    private const val PAGE_W = 595 // A4 in points
    private const val PAGE_H = 842
    private const val MARGIN = 57  // ~2 cm

    fun render(doc: CoverLetterDocument): ByteArray {
        val text = SpannableStringBuilder()
        doc.paragraphs.forEach { p ->
            val start = text.length
            text.append(p.text).append('\n')
            val end = text.length
            when (p.style) {
                CoverLetterDocument.Style.NAME -> {
                    text.setSpan(StyleSpan(Typeface.BOLD), start, end, 0)
                    text.setSpan(AbsoluteSizeSpan(16), start, end, 0)
                }
                CoverLetterDocument.Style.CONTACT -> text.setSpan(AbsoluteSizeSpan(9), start, end, 0)
                CoverLetterDocument.Style.SUBJECT -> text.setSpan(StyleSpan(Typeface.BOLD), start, end, 0)
                CoverLetterDocument.Style.BODY -> Unit
            }
        }
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val width = PAGE_W - 2 * MARGIN
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.2f)
            .build()

        val pdf = PdfDocument()
        val usable = PAGE_H - 2 * MARGIN
        var line = 0
        var pageNo = 1
        while (line < layout.lineCount) {
            val top = layout.getLineTop(line)
            var end = line
            while (end < layout.lineCount && layout.getLineBottom(end) - top <= usable) end++
            if (end == line) end = line + 1
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo++).create())
            val canvas: Canvas = page.canvas
            canvas.save()
            canvas.translate(MARGIN.toFloat(), (MARGIN - top).toFloat())
            canvas.clipRect(0, top, width, layout.getLineBottom(end - 1))
            layout.draw(canvas)
            canvas.restore()
            pdf.finishPage(page)
            line = end
        }
        val out = ByteArrayOutputStream()
        pdf.writeTo(out)
        pdf.close()
        return out.toByteArray()
    }
}

/**
 * Writes kit documents for saving (Storage Access Framework) or sharing (FileProvider), and
 * builds the share intent that attaches the cover letter + your resume PDF together -- e.g.
 * to email, LinkedIn or a recruiter chat.
 */
object KitFiles {
    private const val RESUME_PDF = "resume.pdf"

    fun bytes(doc: CoverLetterDocument, format: CoverLetterDocument.Format): ByteArray = when (format) {
        CoverLetterDocument.Format.DOCX -> doc.toDocx()
        CoverLetterDocument.Format.PDF -> CoverLetterPdf.render(doc)
        CoverLetterDocument.Format.TXT -> doc.toText().toByteArray(Charsets.UTF_8)
    }

    fun save(context: Context, doc: CoverLetterDocument, format: CoverLetterDocument.Format, target: Uri) {
        val data = bytes(doc, format)
        context.contentResolver.openOutputStream(target, "wt")?.use { it.write(data) }
            ?: error("Couldn't open the chosen file for writing.")
    }

    /** The resume PDF kept from Setup → Profile → Import PDF, if you've imported one. */
    fun resumePdf(context: Context): File? = File(context.filesDir, RESUME_PDF).takeIf { it.exists() && it.length() > 0 }

    fun storeResumePdf(context: Context, data: ByteArray) {
        File(context.filesDir, RESUME_PDF).writeBytes(data)
    }

    /**
     * Share sheet with the cover letter (in [format]) and, when available, the resume PDF.
     * Returns null-safe intent ready for startActivity.
     */
    fun shareIntent(context: Context, doc: CoverLetterDocument, format: CoverLetterDocument.Format, message: String): Intent {
        val dir = File(context.cacheDir, "exports").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val letter = File(dir, doc.fileName(format)).apply { writeBytes(bytes(doc, format)) }
        val files = listOfNotNull(letter, resumePdf(context)?.let { src ->
            File(dir, "Resume_${doc.contact.name.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')}.pdf").apply { writeBytes(src.readBytes()) }
        })
        val authority = "${context.packageName}.fileprovider"
        val uris = ArrayList(files.map { FileProvider.getUriForFile(context, authority, it) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first()).setType(format.mimeType)
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris).setType("*/*")
        }
        intent.putExtra(Intent.EXTRA_SUBJECT, "Application: ${doc.job.title} -- ${doc.contact.name}")
        if (message.isNotBlank()) intent.putExtra(Intent.EXTRA_TEXT, message)
        // Grant read access to every attachment (ClipData is what receivers actually check).
        val clip = ClipData.newRawUri(files.first().name, uris.first())
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        intent.clipData = clip
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(intent, "Share cover letter + resume")
    }
}

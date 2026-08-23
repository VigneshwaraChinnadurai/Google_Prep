package com.vignesh.leetcodechecker.bookreader

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Extracts real text from a PDF page via PDFBox-Android's PDFTextStripper -- Android's
 * built-in PdfRenderer (used for the visual page view) only rasterizes pages to bitmaps,
 * it has no text extraction. This is what makes Read Aloud / Mail Voice-Over possible for
 * PDFs; a page with no embedded text layer (e.g. a scanned image) will still return blank,
 * since there's nothing to extract -- that's a genuine limitation of the source file, not
 * a bug here, and would need OCR (a different problem) to solve.
 */
object PdfTextExtractor {
    @Volatile private var resourcesLoaded = false

    private fun ensureLoaded(context: Context) {
        if (!resourcesLoaded) {
            synchronized(this) {
                if (!resourcesLoaded) {
                    PDFBoxResourceLoader.init(context.applicationContext)
                    resourcesLoaded = true
                }
            }
        }
    }

    /** pageIndex is 0-based, matching the rest of this app's chapter/page indexing. */
    suspend fun extractPageText(context: Context, file: File, pageIndex: Int): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                ensureLoaded(context)
                PDDocument.load(file).use { document ->
                    val pageNumber = pageIndex + 1 // PDFTextStripper pages are 1-based
                    val stripper = PDFTextStripper().apply {
                        startPage = pageNumber
                        endPage = pageNumber
                    }
                    stripper.getText(document).trim()
                }
            }
        }
}

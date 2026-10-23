@file:OptIn(ExperimentalForeignApi::class)

package app.expenses

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGDataProviderCreateWithData
import platform.CoreGraphics.CGDataProviderRelease
import platform.CoreGraphics.CGPDFDocumentCreateWithProvider
import platform.CoreGraphics.CGPDFDocumentRelease
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGSizeMake
import app.expenses.core.domain.OcrPiece
import app.expenses.core.domain.OcrRows
import platform.PDFKit.PDFDocument
import platform.PDFKit.PDFPage
import platform.PDFKit.PDFSelection
import platform.PDFKit.kPDFDisplayBoxMediaBox

/**
 * Reads a statement PDF on the phone with PDFKit, which also opens password-protected files. A PDF whose pages are pictures (a scan) has no text under them,
 * so its pages are drawn and read by [IosOcr]. The password is only handed to PDFKit to open this one file: not kept, not logged.
 */
internal object IosPdf {
    private const val MAX_PAGES = 80
    private const val MAX_OCR_PAGES = 12
    private const val MIN_TEXT_CHARS = 60
    private const val TARGET_WIDTH = 1800.0

    /**
     * One page as rows. PDFKit's plain `string` lists a table column by column (all the dates, then all the narrations), so the page's text is taken line by
     * line with each line's place on the page, and [OcrRows] puts the pieces on one height back into a row, as for a picture.
     */
    private fun rows(page: PDFPage): String {
        val box = page.boundsForBox(kPDFDisplayBoxMediaBox)
        val pageTop = box.useContents { origin.y + size.height }
        val lines = page.selectionForRect(box)?.selectionsByLine().orEmpty().filterIsInstance<PDFSelection>()
        val pieces = lines.mapNotNull { sel ->
            val text = sel.string?.trim().orEmpty()
            if (text.isEmpty()) return@mapNotNull null
            // PDF space has its origin at the bottom left; rows are read top down
            sel.boundsForPage(page).useContents { OcrPiece(text, origin.x.toFloat(), (pageTop - (origin.y + size.height / 2)).toFloat(), size.height.toFloat()) }
        }
        return if (pieces.isEmpty()) page.string.orEmpty() else OcrRows.group(pieces)
    }

    /** Whether the system can make a PDF of [bytes] at all (a protected file counts: it opens once unlocked). */
    private fun parses(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        return bytes.usePinned { pin ->
            val provider = CGDataProviderCreateWithData(null, pin.addressOf(0), bytes.size.convert(), null)
            val doc = CGPDFDocumentCreateWithProvider(provider)
            CGDataProviderRelease(provider)
            (doc != null).also { CGPDFDocumentRelease(doc) }
        }
    }

    fun read(bytes: ByteArray, password: String?): PdfText {
        // PDFKit answers nil for a damaged file, which Kotlin/Native does not allow for a constructor (it would take the app down), so the file is checked first
        if (!parses(bytes)) return PdfText.Unreadable("That PDF could not be read. It may be damaged or not a PDF.")
        val doc = try {
            PDFDocument(data = bytes.toNSData())
        } catch (e: Throwable) {
            return PdfText.Unreadable("That PDF could not be read. It may be damaged or not a PDF.")
        }
        if (doc.isLocked() && !doc.unlockWithPassword(password.orEmpty())) return if (password == null) PdfText.PasswordNeeded else PdfText.WrongPassword
        val pages = doc.pageCount.toInt()
        if (pages == 0) return PdfText.Unreadable("That PDF has no pages.")
        val text = (0 until minOf(pages, MAX_PAGES)).joinToString("\n") { i -> doc.pageAtIndex(i.toULong())?.let(::rows).orEmpty() }
        if (text.count { it.isLetterOrDigit() } >= MIN_TEXT_CHARS) return PdfText.Text(text, recognised = false)
        return try {
            val out = StringBuilder()
            for (i in 0 until minOf(pages, MAX_OCR_PAGES)) {
                val page = doc.pageAtIndex(i.toULong()) ?: continue
                val box = page.boundsForBox(kPDFDisplayBoxMediaBox)
                val (w, h) = box.useContents { size.width to size.height }
                if (w <= 0.0) continue
                val image = page.thumbnailOfSize(CGSizeMake(TARGET_WIDTH, h * TARGET_WIDTH / w), forBox = kPDFDisplayBoxMediaBox)
                val cg = image.CGImage ?: continue
                out.append(IosOcr.recognize(cg)).append('\n')
            }
            PdfText.Text(out.toString(), recognised = true)
        } catch (e: IllegalArgumentException) {
            PdfText.Unreadable(e.message ?: "The pages of that PDF could not be read.")
        }
    }
}

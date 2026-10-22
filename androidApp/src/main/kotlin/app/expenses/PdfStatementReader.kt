package app.expenses

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.IOException

/**
 * Reads the text of a statement PDF on the phone with PDFBox (which also opens password-protected files), and, when the pages are pictures with no text under
 * them (a scanned statement), draws the pages with the system's PdfRenderer and reads them with [OcrStatementReader]. No network is used and nothing is logged:
 * not the file, not the text, not the password, which is only ever handed to PDFBox to open this one file and is not kept. A copy of the file is written to the
 * app's cache only for the scanned-page case, without its password protection since the renderer needs that, and is deleted straight after.
 */
class PdfStatementReader(private val context: Context) {
    fun read(bytes: ByteArray, password: String?): PdfText {
        initLoader()
        val document = try {
            PDDocument.load(bytes, password ?: "")
        } catch (e: InvalidPasswordException) {
            return if (password == null) PdfText.PasswordNeeded else PdfText.WrongPassword
        } catch (e: IOException) {
            return PdfText.Unreadable("That PDF could not be read. It may be damaged or not a PDF.")
        } catch (e: OutOfMemoryError) {
            return PdfText.Unreadable("That PDF is too large to read on this phone.")
        }
        document.use { doc ->
            val pages = doc.numberOfPages
            if (pages == 0) return PdfText.Unreadable("That PDF has no pages.")
            val text = try {
                PDFTextStripper().apply { sortByPosition = true; endPage = MAX_PAGES }.getText(doc)
            } catch (e: IOException) {
                ""
            } catch (e: RuntimeException) {
                ""
            }
            if (text.count { it.isLetterOrDigit() } >= MIN_TEXT_CHARS) return PdfText.Text(text, recognised = false)
            return scanned(doc, pages)
        }
    }

    /** Pages that are pictures: write the document out unprotected, draw each page and recognise it. */
    private fun scanned(doc: PDDocument, pages: Int): PdfText {
        val dir = File(context.cacheDir, "pdf-import").also { it.mkdirs() }
        dir.listFiles().orEmpty().forEach { it.delete() } // a copy left by a run that was killed
        val file = File.createTempFile("statement", ".pdf", dir)
        try {
            try {
                doc.isAllSecurityToBeRemoved = true
                file.outputStream().use { doc.save(it) }
            } catch (e: IOException) {
                return PdfText.Unreadable("That PDF could not be read. It may be damaged or not a PDF.")
            }
            val out = StringBuilder()
            OcrStatementReader().use { ocr ->
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        for (i in 0 until minOf(pages, renderer.pageCount, MAX_OCR_PAGES)) {
                            val bitmap = renderer.openPage(i).use { page ->
                                // about 1800 pixels across: sharp enough for small print, small enough to be quick
                                val scale = TARGET_WIDTH / page.width.toFloat()
                                Bitmap.createBitmap(TARGET_WIDTH, (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
                                    it.eraseColor(Color.WHITE)
                                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            }
                            try {
                                out.append(ocr.recognize(bitmap)).append('\n')
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }
            return PdfText.Text(out.toString(), recognised = true)
        } catch (e: IllegalArgumentException) {
            return PdfText.Unreadable(e.message ?: "The pages of that PDF could not be read.")
        } catch (e: IOException) {
            return PdfText.Unreadable("The pages of that PDF could not be read.")
        } catch (e: SecurityException) {
            return PdfText.Unreadable("The pages of that PDF could not be read.")
        } finally {
            file.delete()
        }
    }

    private fun initLoader() {
        if (!loaderReady) synchronized(PdfStatementReader::class.java) {
            if (!loaderReady) { PDFBoxResourceLoader.init(context.applicationContext); loaderReady = true }
        }
    }

    private companion object {
        @Volatile private var loaderReady = false

        /** Text of fewer letters and digits than this is not a statement's text layer, so the pages are read as pictures. */
        const val MIN_TEXT_CHARS = 60
        const val MAX_PAGES = 80
        const val MAX_OCR_PAGES = 12
        const val TARGET_WIDTH = 1800
    }
}

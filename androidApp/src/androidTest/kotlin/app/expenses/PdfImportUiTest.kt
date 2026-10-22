package app.expenses

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.storage.OriginKind
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Statement import from a PDF and from a picture. The statement text is SYNTHETIC (invented to mirror the card statement layouts in use; no real statement was
 * seen). The PDFs are drawn here with the system's PdfDocument and, for the password case, protected with PDFBox itself, so this proves the whole path on a
 * device: file -> PDFBox -> StatementTextParser -> the usual preview -> import. It does not prove a real bank's PDF reads: real files differ in how their text is laid out.
 */
@RunWith(AndroidJUnit4::class)
class PdfImportUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val files = mutableListOf<File>()

    private fun resetApp() { runBlocking { container.startFresh() } }

    @Before fun fresh() = resetApp()

    @After fun cleanUp() {
        compose.waitForIdle(); scenario?.close()
        resetApp()
        files.forEach { it.delete() }
    }

    private fun file(name: String, content: ByteArray): Uri = Uri.fromFile(File(context.cacheDir, name).also { it.writeBytes(content); files += it })

    private fun waitFor(tag: String) = compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun waitText(tag: String, text: String) = compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag(tag) and hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun waitAnyText(text: String) = compose.waitUntil(30_000) { compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun entries() = runBlocking { container.write { it.db.schemaQueries.liveEntriesInInsertionOrder().executeAsList() } }!!
    private fun viewModel(): LedgerViewModel { var vm: LedgerViewModel? = null; scenario!!.onActivity { vm = ViewModelProvider(it)[LedgerViewModel::class.java] }; return vm!! }

    private fun openData() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        waitFor("add")
        compose.openMore()
        waitFor("open_data")
        compose.onNodeWithTag("open_data").performScrollTo().performClick()
        waitFor("import_statement")
    }

    private val card = listOf(
        "Credit Card Statement",
        "Statement Date: 12/10/2026",
        "Total Amount Due 5,432.10",
        "Minimum Amount Due 270.00",
        "Date Transaction Description Amount (Rs.)",
        "12/09/2026 AMAZON PAY INDIA MUMBAI 1,299.00",
        "13/09/2026 SWIGGY BANGALORE 450.50",
        "14/09/2026 PAYMENT RECEIVED - THANK YOU 5,000.00 Cr",
        "15/09/2026 AMAZON REFUND MUMBAI 799.00 Cr",
    )

    /** A one-page PDF whose lines are real text, as a bank's PDF is. */
    private fun pdf(lines: List<String>): ByteArray {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val paint = Paint().apply { textSize = 11f; color = Color.BLACK }
        lines.forEachIndexed { i, l -> page.canvas.drawText(l, 30f, 40f + i * 20f, paint) }
        doc.finishPage(page)
        return ByteArrayOutputStream().also { doc.writeTo(it); doc.close() }.toByteArray()
    }

    /** The same PDF protected with a password the way banks do (PdfDocument cannot do this itself). */
    private fun protect(pdf: ByteArray, password: String): ByteArray {
        PDFBoxResourceLoader.init(context)
        PDDocument.load(pdf).use { d ->
            d.protect(StandardProtectionPolicy("owner-$password", password, AccessPermission()).apply { encryptionKeyLength = 128 })
            return ByteArrayOutputStream().also { d.save(it) }.toByteArray()
        }
    }

    /** Big, plain text on white, which the recognizer reads reliably. A phone photo would be harder; this checks the path, not the recognizer's accuracy. */
    private fun picture(lines: List<String>): ByteArray {
        val bitmap = Bitmap.createBitmap(1400, 120 + lines.size * 70, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
        val paint = Paint().apply { textSize = 40f; color = Color.BLACK; isAntiAlias = true }
        lines.forEachIndexed { i, l -> canvas.drawText(l, 30f, 80f + i * 70f, paint) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    @Test
    fun aTextPdfIsReadIntoAPreviewWithTheCardPaymentSkippedAndThenImported() {
        openData()
        viewModel().startImport(file("card.pdf", pdf(card)).toString())
        waitText("import_summary", "3 transactions: spending ₹1,749.50, income ₹799.00")
        waitText("import_read_note", "Read from a PDF; check amounts before importing")
        waitText("import_card_payments", "Skipped 1 payment to the card")
        waitFor("preview_0")
        assertEquals(0, entries().size, "nothing is saved until the user confirms")
        compose.onNodeWithTag("import_confirm").performClick()
        waitAnyText("Imported 3 transactions")
        val rows = entries()
        // PDFBox sometimes puts a space inside a word of a PdfDocument-drawn PDF ("S WIGGY"), so the words are compared without spaces; a real bank PDF was not available to check
        assertEquals(listOf("AMAZONPAYINDIAMUMBAI", "SWIGGYBANGALORE", "AMAZONREFUNDMUMBAI"), rows.map { it.merchant!!.replace(" ", "") })
        assertEquals(listOf(129_900L, 45_050L, 79_900L), rows.map { it.amount_minor })
        // the refund is money in; the line it came from is kept as the entry's origin
        val origin = runBlocking { container.write { ok -> ok.origins.get(rows[2].id) } }!!
        assertEquals(OriginKind.STATEMENT, origin.kind)
        assertTrue(origin.text.contains("15/09/2026 AMAZON REFUND MUMBAI 799.00 Cr"), origin.text)
    }

    @Test
    fun aPasswordProtectedPdfAsksForItsPasswordAndAWrongOneIsRefusedPlainly() {
        openData()
        viewModel().startImport(file("locked.pdf", protect(pdf(card), "ASHA1990")).toString())
        waitFor("import_password")
        assertTrue(compose.onAllNodes(hasTestTag("import_confirm")).fetchSemanticsNodes().isEmpty(), "nothing is shown before the password opens the file")
        compose.onNodeWithTag("import_password").performTextInput("wrong")
        compose.onNodeWithTag("import_password_go").performScrollTo().performClick()
        waitText("import_password_error", "did not open")
        compose.onNodeWithTag("import_password").performTextInput("ASHA1990")
        compose.onNodeWithTag("import_password_go").performScrollTo().performClick()
        waitText("import_summary", "3 transactions")
        assertEquals(0, entries().size)
        compose.onNodeWithTag("import_confirm").performClick()
        waitAnyText("Imported 3 transactions")
        assertEquals(3, entries().size)
    }

    @Test
    fun aPdfWithNoTransactionsSaysSoAndSavesNothing() {
        openData()
        viewModel().startImport(file("none.pdf", pdf(listOf("Dear customer,", "Your statement will be sent separately.", "Thank you for banking with us."))).toString())
        waitText("import_error", "Could not find transactions in this PDF")
        assertEquals(0, entries().size)
    }

    @Test
    fun aPictureOfAStatementIsReadOnThePhoneIntoAPreviewAndImported() {
        openData()
        viewModel().startImport(file("shot.png", picture(listOf("Statement of Account", "12/09/2026 SWIGGY BANGALORE 450.50 Dr", "13/09/2026 SALARY ACME CORP 50000.00 Cr"))).toString())
        waitFor("preview_0")
        waitText("import_read_note", "Read from a photo; check amounts before importing")
        waitText("import_summary", "2 transactions")
        compose.onNodeWithTag("import_confirm").performClick()
        waitAnyText("Imported 2 transactions")
        assertEquals(2, entries().size)
    }
}

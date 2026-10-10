package app.expenses

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.storage.CsvExport
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Statement fixtures are SYNTHETIC: invented to mirror common bank layouts, not real statements. */
@RunWith(AndroidJUnit4::class)
class ImportUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val files = mutableListOf<File>()

    private fun resetApp() {
        runBlocking { container.startFresh() }
    }

    @Before fun fresh() = resetApp()

    @After fun cleanUp() {
        compose.waitForIdle(); scenario?.close()
        resetApp()
        files.forEach { it.delete() }
    }

    private fun file(name: String, content: ByteArray): Uri = Uri.fromFile(File(context.cacheDir, name).also { it.writeBytes(content); files += it })
    private fun file(name: String, content: String) = file(name, content.encodeToByteArray())

    private fun waitFor(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    private fun waitText(tag: String, text: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag) and hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    /** The screen scrolls, and a click does not, so scroll to a control first (as a person would). */
    private fun tap(tag: String) { compose.onNodeWithTag(tag).performScrollTo().performClick() }
    private fun waitAnyText(text: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun entryCount() = runBlocking { container.write { it.db.schemaQueries.entryCount().executeAsOne() } }!!
    private fun viewModel(): LedgerViewModel { var vm: LedgerViewModel? = null; scenario!!.onActivity { vm = ViewModelProvider(it)[LedgerViewModel::class.java] }; return vm!! }

    private fun openData() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        waitFor("add")
        compose.onNodeWithTag("more_menu").performClick()
        waitFor("open_data")
        compose.onNodeWithTag("open_data").performClick()
        waitFor("import_statement")
    }

    private fun start(uri: Uri) { viewModel().startImport(uri); waitFor("import_account_bank") }

    private val hdfc = """
        Account Statement,,,,,,
        Customer ID: 12345,,,,,,

        Date,Narration,Chq./Ref.No.,Value Dt,Withdrawal Amt.,Deposit Amt.,Closing Balance
        01/10/26,UPI-SWIGGY-9876,000123,01/10/26,"1,250.50",,"49,750.00"
        02/10/26,SALARY OCT,000124,02/10/26,,"50,000.00","99,750.00"
        03/10/26,"CAFE, INDIRANAGAR",000125,03/10/26,300.00,,"99,450.00"
        Closing balance,,,,,,"99,450.00"
    """.trimIndent()

    @Test
    fun aStatementIsShownBeforeItIsSavedThenImportedAndNothingIsDuplicatedOnASecondImport() {
        openData()
        start(file("hdfc.csv", hdfc))
        waitText("import_summary", "3 transactions: spending ₹1,550.50, income ₹50,000.00")
        waitFor("preview_0"); waitText("import_ignored", "1 row skipped")
        assertEquals(0L, entryCount(), "nothing is saved until the user confirms")
        compose.onNodeWithTag("import_confirm").performClick()
        waitAnyText("Imported 3 transactions")
        assertEquals(3L, entryCount())
        val rows = runBlocking { container.write { it.db.schemaQueries.liveEntriesInInsertionOrder().executeAsList() } }!!
        assertEquals(listOf("UPI-SWIGGY-9876", "SALARY OCT", "CAFE, INDIRANAGAR"), rows.map { it.merchant })
        assertEquals(listOf(125_050L, 5_000_000L, 30_000L), rows.map { it.amount_minor })
        assertEquals(setOf("csv"), rows.map { it.source }.toSet())
        assertEquals(setOf("bank"), rows.map { it.account_id }.toSet(), "the default account is the one used when none is picked")

        compose.onNodeWithTag("import_close").performClick()
        waitFor("import_statement")
        // on the list, the salary reads as money in and the purchases do not
        compose.onNodeWithTag("data_back").performClick()
        waitFor("add")
        waitAnyText("+₹50,000.00")
        assertTrue(compose.onAllNodes(hasText("+₹1,250.50"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "spending has no plus sign")
        compose.onNodeWithTag("more_menu").performClick(); waitFor("open_data"); compose.onNodeWithTag("open_data").performClick(); waitFor("import_statement")
        start(file("hdfc-again.csv", hdfc))
        compose.onNodeWithTag("import_confirm").performClick()
        waitAnyText("Imported 0 transactions")
        waitAnyText("3 were already in the app")
        assertEquals(3L, entryCount(), "importing the same statement again adds nothing")
    }

    @Test
    fun cancellingSavesNothing() {
        openData()
        start(file("hdfc.csv", hdfc))
        waitFor("import_confirm")
        compose.onNodeWithTag("import_close").performClick()
        waitFor("import_statement")
        assertEquals(0L, entryCount())
    }

    @Test
    fun theChosenAccountIsTheOneUsed() {
        openData()
        start(file("hdfc.csv", hdfc))
        tap("import_account_cash")
        compose.onNodeWithTag("import_confirm").performClick()
        waitFor("import_result")
        val accounts = runBlocking { container.write { it.db.schemaQueries.liveEntriesInInsertionOrder().executeAsList().map { e -> e.account_id } } }!!
        assertEquals(setOf("cash"), accounts.toSet())
    }

    @Test
    fun aFileWithoutAHeaderNeedsTheAmountColumnChosenAndThenReadsCorrectly() {
        openData()
        start(file("plain.csv", "01/10/2026,Shop,-250.00\n02/10/2026,Salary,500.00\n"))
        waitFor("import_mapping_problem")
        compose.onNodeWithTag("import_confirm").let { /* the button is off while the mapping is incomplete */ }
        tap("layout_SIGNED_AMOUNT")
        tap("pick_amount")
        waitFor("pick_amount_2"); tap("pick_amount_2")
        tap("pick_description")
        waitFor("pick_description_1"); tap("pick_description_1")
        waitText("import_summary", "2 transactions: spending ₹250.00, income ₹500.00")
        compose.onNodeWithTag("import_confirm").performClick()
        waitFor("import_result")
        assertEquals(listOf("Shop", "Salary"), runBlocking { container.write { it.db.schemaQueries.liveEntriesInInsertionOrder().executeAsList().map { e -> e.merchant } } })
    }

    @Test
    fun flippingTheSignConventionSwapsSpendingAndIncomeInThePreview() {
        openData()
        // purchases positive and a payment negative: the app guesses a bank account (minus is spending) and shows it plainly
        start(file("card.csv", "Date,Transaction Details,Amount\n01/10/2026,AMAZON,1250.00\n02/10/2026,PAYMENT RECEIVED,-5000.00\n03/10/2026,SWIGGY,310.00\n"))
        waitText("import_summary", "spending ₹5,000.00, income ₹1,560.00")
        tap("sign_positive")
        waitText("import_summary", "spending ₹1,560.00, income ₹5,000.00")
    }

    @Test
    fun rowsThatCannotBeReadAreListedWithTheirLineAndAreNotImported() {
        openData()
        start(file("bad.csv", "Date,Narration,Debit,Credit\n01/10/2026,OK,10.00,\n02/10/2026,BAD AMOUNT,abc,\n\n32/13/2026,BAD DATE,5.00,\n04/10/2026,OK TOO,,7.00\n"))
        waitText("import_summary", "2 transactions")
        waitText("import_problem_count", "2 rows could not be read and will NOT be imported")
        compose.onAllNodes(hasText("Line 3:", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty().also { assertTrue(it, "line 3 of the file") }
        compose.onAllNodes(hasText("Line 5:", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty().also { assertTrue(it, "line 5 of the file, counting the blank line") }
        compose.onNodeWithTag("import_confirm").performClick()
        waitFor("import_result")
        assertEquals(2L, entryCount())
    }

    @Test
    fun theDayAndMonthWarningAppearsWhenDatesCouldBeEitherAndTheOrderCanBeChanged() {
        openData()
        start(file("amb.csv", "Date,Narration,Amount\n02/10/2026,A,-1.00\n03/11/2026,B,-2.00\n"))
        waitFor("import_ambiguous")
        tap("order_MDY")
        waitAnyText("10 Feb 2026")
    }

    @Test
    fun unusableFilesGiveAClearMessageAndSaveNothing() {
        openData()
        viewModel().startImport(file("empty.csv", ""))
        waitText("import_error", "no rows to import")
        compose.onNodeWithTag("import_close").performClick()
        waitFor("import_statement")
        viewModel().startImport(file("one.csv", "Date,Amount\n"))
        waitText("import_error", "no rows to import")
        assertEquals(0L, entryCount())
    }

    @Test
    fun theAppsOwnCsvExportIsRecognisedAndPointsToRestoreInsteadOfImportingNonsense() {
        openData()
        val exported = runBlocking {
            container.write { ok ->
                ok.store.save(app.expenses.core.domain.ledger.Expense(app.expenses.core.domain.ledger.EntryId("x"), 1_000, app.expenses.core.domain.ledger.AccountId("cash"), app.expenses.core.domain.Money(500, app.expenses.core.domain.Currency.INR)), app.expenses.core.storage.EntryDetails(merchant = "Cafe"))
                CsvExport(ok.db).build(kotlinx.datetime.TimeZone.UTC)
            }
        }!!
        viewModel().startImport(file("own.csv", exported))
        waitText("import_error", "exported from this app")
        waitText("import_error", "make a backup")
        assertEquals(1L, entryCount(), "nothing was imported: only the one entry that was exported is there")
    }

    @Test
    fun anEmptyExportOfTheAppsOwnDataGetsTheRestoreMessageNotAGenericOne() {
        openData()
        val header = runBlocking { container.write { ok -> CsvExport(ok.db).build(kotlinx.datetime.TimeZone.UTC) } }!!   // a fresh ledger: only the header row
        viewModel().startImport(file("own-empty.csv", header))
        waitText("import_error", "exported from this app")
    }

    private fun workbook(rows: List<List<String>>) = app.expenses.core.storage.XlsxExport.build(rows, setOf(1, 2))

    private fun asset(name: String) = InstrumentationRegistry.getInstrumentation().context.assets.open("xls/$name").readBytes()

    @Test
    fun anOldFormatExcelStatementWithABankPreambleIsReadAndEveryTransactionImported() {
        openData()
        start(file("Acct_Statement.xls", asset("big.xls")))
        compose.waitUntil(10_000) { viewModel().statement.value.txnCount == 150 }
        assertEquals(0, viewModel().statement.value.problemCount, "the asterisk row and the summary block are not problems")
        compose.onNodeWithTag("import_confirm").performClick(); waitAnyText("Imported 150 transactions")
        assertEquals(150, runBlocking { container.write { it.db.schemaQueries.liveEntriesInInsertionOrder().executeAsList().size } })
    }

    @Test
    fun aFileThatIsNotAWorkbookButLooksLikeOneExplainsItself() {
        openData()
        viewModel().startImport(file("broken.xls", asset("big.xls").copyOf(700)))
        compose.waitUntil(10_000) { viewModel().statement.value.error != null }
        assertTrue(viewModel().statement.value.error!!.contains("Excel"), viewModel().statement.value.error!!)
    }

    @Test
    fun anExcelStatementIsReadLikeACsvOneAndImported() {
        openData()
        start(file("bank.xlsx", workbook(listOf(
            listOf("Date", "Narration", "Withdrawal Amt.", "Deposit Amt."),
            listOf("01/10/2026", "UPI-SWIGGY", "1250.50", ""), listOf("02/10/2026", "SALARY", "", "50000"),
        ))))
        waitText("import_summary", "2 transactions: spending ₹1,250.50, income ₹50,000.00")
        compose.onNodeWithTag("import_confirm").performClick(); waitAnyText("Imported 2 transactions")
        assertEquals(listOf("UPI-SWIGGY", "SALARY"), runBlocking { container.write { it.db.schemaQueries.liveEntriesInInsertionOrder().executeAsList().map { r -> r.merchant } } }!!)
    }

    @Test
    fun theWayAFileWasReadIsRememberedForTheNextFileWithTheSameColumnTitles() {
        openData()
        // Titles the detector cannot place: it is told which columns are which, once.
        start(file("a.csv", "When,What,Out,In\n01/10/2026,Cafe,100.00,\n02/10/2026,Pay,,900.00\n"))
        viewModel().changeImportMapping { it.copy(headerRows = 1, dateColumn = 0, descriptionColumn = 1, layout = app.expenses.core.domain.AmountLayout.SEPARATE_DEBIT_CREDIT, debitColumn = 2, creditColumn = 3, dateOrder = app.expenses.core.domain.DateOrder.MDY) }
        compose.waitUntil(10_000) { viewModel().statement.value.mapping?.dateOrder == app.expenses.core.domain.DateOrder.MDY }
        waitFor("import_confirm"); compose.onNodeWithTag("import_confirm").performClick(); waitAnyText("Imported 2 transactions")
        compose.onNodeWithTag("import_close").performClick(); waitFor("import_statement")
        // a different file with the same titles: read the remembered way (month first), and the note says so
        start(file("b.csv", "When,What,Out,In\n03/10/2026,Tea,50.00,\n"))
        assertEquals(app.expenses.core.domain.DateOrder.MDY, viewModel().statement.value.mapping!!.dateOrder)
        waitFor("import_profile_note")
        // different titles get no note and are guessed afresh
        compose.onNodeWithTag("import_close").performClick(); waitFor("import_statement")
        start(file("c.csv", "Date,Narration,Debit,Credit\n03/10/2026,Tea,50.00,\n"))
        assertTrue(compose.onAllNodes(hasTestTag("import_profile_note"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aRememberedLayoutIsFoundEvenWhenTheHeaderIsBelowAPreambleThatDiffersEachMonth() {
        openData()
        start(file("p1.csv", "Statement for Sep 2026,,,\nWhen,What,Out,In\n01/09/2026,Cafe,100.00,\n"))
        viewModel().changeImportMapping { it.copy(headerRows = 2, dateColumn = 0, descriptionColumn = 1, layout = app.expenses.core.domain.AmountLayout.SEPARATE_DEBIT_CREDIT, debitColumn = 2, creditColumn = 3, dateOrder = app.expenses.core.domain.DateOrder.MDY) }
        compose.waitUntil(10_000) { viewModel().statement.value.mapping?.dateOrder == app.expenses.core.domain.DateOrder.MDY }
        waitFor("import_confirm"); compose.onNodeWithTag("import_confirm").performClick(); waitAnyText("Imported 1 transaction")
        compose.onNodeWithTag("import_close").performClick(); waitFor("import_statement")
        start(file("p2.csv", "Statement for Oct 2026,,,\nCustomer 99,,,\nWhen,What,Out,In\n03/10/2026,Tea,50.00,\n"))
        assertEquals(app.expenses.core.domain.DateOrder.MDY, viewModel().statement.value.mapping!!.dateOrder)
        waitFor("import_profile_note")
    }

    // ---- reading the bytes ----

    @Test
    fun textInTheEncodingsBanksUseIsDecoded() {
        val text = "Date,Narration\n01/10/2026,Café ₹"
        assertEquals(text, StatementText.decode(text.encodeToByteArray()))
        assertEquals(text, StatementText.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.encodeToByteArray()), "UTF-8 with a byte-order mark")
        assertEquals(text, StatementText.decode(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)), "UTF-16 little-endian")
        assertEquals(text, StatementText.decode(byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + text.toByteArray(Charsets.UTF_16BE)), "UTF-16 big-endian")
        assertEquals("Café", StatementText.decode(byteArrayOf('C'.code.toByte(), 'a'.code.toByte(), 'f'.code.toByte(), 0xE9.toByte())), "Windows-1252 is used when the bytes are not valid UTF-8")
    }
}

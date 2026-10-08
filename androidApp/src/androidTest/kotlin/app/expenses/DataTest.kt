package app.expenses

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.BudgetPeriod
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.RecurringItem
import app.expenses.core.domain.RepeatUnit
import app.expenses.core.domain.Schedule
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.BackupException
import app.expenses.core.storage.EncryptedDriver
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DataTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val actions get() = DataActions(context, container)
    private val inr = Currency.INR
    private var scenario: ActivityScenario<MainActivity>? = null
    private val files = mutableListOf<File>()

    private fun resetApp() {
        runBlocking { container.close() }
        EncryptedDriver.reset(context)
    }

    /** The less frequent places are in the "More" menu on the list. */
    private fun openFromMenu(tag: String) {
        compose.onNodeWithTag("more_menu").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag).performClick()
    }

    @Before fun fresh() = resetApp()

    @After fun cleanUp() {
        scenario?.close()
        resetApp()
        files.forEach { it.delete() }
    }

    private fun file(name: String): Uri = Uri.fromFile(File(context.cacheDir, name).also { files += it })

    private fun populate() = runBlocking {
        container.write { ok ->
            val today = java.time.LocalDate.now().let { LocalDate(it.year, it.monthValue, it.dayOfMonth) }
            repeat(5) { i ->
                ok.store.save(
                    Expense(EntryId("e$i"), System.currentTimeMillis() - i * 1_000, AccountId("cash"), Money(10_000L * (i + 1), inr), listOf(CategorySplit(CategoryId("Food"), Money(10_000L * (i + 1), inr)))),
                    EntryDetails(merchant = "Shop, $i"),
                )
            }
            ok.budgets.setLimit(null, Money(5_000_000, inr)); ok.budgets.setPeriod(BudgetPeriod.Monthly(25))
            ok.recurring.save(RecurringItem("rent", "Rent", Money(1_500_000, inr), CategoryId("Bills"), AccountId("bank"), null, Schedule(RepeatUnit.MONTH, 1, today)))
        }
    }

    private fun entryCount() = runBlocking { container.write { it.db.schemaQueries.entryCount().executeAsOne() } }!!
    private fun foodTotal() = runBlocking { container.write { it.store.spendByCategory(inr)[CategoryId("Food")]?.minorUnits ?: 0L } }!!
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    private fun waitText(tag: String, text: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag) and hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun waitAnyText(text: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun launch() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        waitFor("add")
        openFromMenu("open_data")
        waitFor("open_backup")
    }

    private fun viewModel(): LedgerViewModel { var vm: LedgerViewModel? = null; scenario!!.onActivity { vm = ViewModelProvider(it)[LedgerViewModel::class.java] }; return vm!! }

    // ---- encryption ----

    @Test
    fun encryptionRoundTripsAndEveryMistakeIsRefused() {
        val plain = "private ledger".repeat(1_000).encodeToByteArray()
        val sealed = BackupCrypto.encrypt(plain, "correct horse")
        assertTrue(BackupCrypto.isEncrypted(sealed))
        assertTrue(sealed.size > plain.size)
        assertEquals(plain.toList(), BackupCrypto.decrypt(sealed, "correct horse").toList())
        assertNotEquals(sealed.toList(), BackupCrypto.encrypt(plain, "correct horse").toList(), "a fresh salt and nonce every time")

        assertTrue(assertFailsWith<BackupException> { BackupCrypto.decrypt(sealed, "wrong passphrase") }.message!!.contains("passphrase is wrong"))
        val flipped = sealed.copyOf().also { it[it.size - 5] = (it[it.size - 5] + 1).toByte() }
        assertFailsWith<BackupException> { BackupCrypto.decrypt(flipped, "correct horse") }
        val salted = sealed.copyOf().also { it[8 + 4 + 2] = (it[8 + 4 + 2] + 1).toByte() } // a changed salt is detected too
        assertFailsWith<BackupException> { BackupCrypto.decrypt(salted, "correct horse") }
        val weakened = sealed.copyOf().also { it[8] = 0; it[9] = 0; it[10] = 0; it[11] = 1 } // iteration count of 1
        assertEquals("This backup file is damaged.", assertFailsWith<BackupException> { BackupCrypto.decrypt(weakened, "correct horse") }.message, "refused by the count itself, not by a failed key")
        // an absurd count must be refused at once rather than making the phone grind for minutes
        val huge = sealed.copyOf().also { it[8] = 0x7f; it[9] = 0xff.toByte(); it[10] = 0xff.toByte(); it[11] = 0xff.toByte() }
        val started = System.nanoTime()
        assertEquals("This backup file is damaged.", assertFailsWith<BackupException> { BackupCrypto.decrypt(huge, "correct horse") }.message)
        assertTrue((System.nanoTime() - started) / 1_000_000 < 1_000, "refused immediately")
        assertFailsWith<BackupException> { BackupCrypto.decrypt(sealed.copyOf(30), "correct horse") }
        assertFailsWith<IllegalArgumentException> { BackupCrypto.encrypt(plain, "short") }
        assertTrue(!BackupCrypto.isEncrypted(plain))
    }

    // ---- the whole path on the real encrypted database ----

    @Test
    fun aBackupSurvivesWipingTheAppAndRestoresEverythingIncludingTheTotals() {
        populate()
        val before = foodTotal()
        val uri = file("plain.expbak")
        val saved = runBlocking { actions.backup(uri, null) }
        assertEquals(5, saved.counts.entries)
        assertEquals(false, saved.encrypted)

        resetApp() // what uninstalling does
        assertEquals(0L, entryCount())
        val opened = runBlocking { actions.open(uri, null) } as DataActions.Opened.Ready
        assertEquals(5, opened.report.added.entries)
        assertEquals(0L, entryCount(), "opening a backup changes nothing")
        runBlocking { actions.restore(opened.data) }
        assertEquals(5L, entryCount())
        assertEquals(before, foodTotal())
        assertEquals(BudgetPeriod.Monthly(25), runBlocking { container.write { it.budgets.period() } })
        assertEquals(1, runBlocking { container.write { it.recurring.list().size } })
        runBlocking { actions.restore(opened.data) }
        assertEquals(5L, entryCount(), "restoring again duplicates nothing")
    }

    @Test
    fun anEncryptedBackupNeedsItsPassphraseAndRestoresWithIt() {
        populate()
        val uri = file("secret.expbak")
        val saved = runBlocking { actions.backup(uri, "correct horse") }
        assertTrue(saved.encrypted)
        assertTrue(!File(uri.path!!).readText(Charsets.ISO_8859_1).contains("Shop"), "the merchant names are not readable in the file")
        resetApp()
        assertTrue(runBlocking { actions.open(uri, null) } is DataActions.Opened.NeedsPassphrase)
        assertTrue(assertFailsWith<BackupException> { runBlocking { actions.open(uri, "nope nope nope") } }.message!!.contains("wrong"))
        val ready = runBlocking { actions.open(uri, "correct horse") } as DataActions.Opened.Ready
        runBlocking { actions.restore(ready.data) }
        assertEquals(5L, entryCount())
    }

    @Test
    fun aDamagedBackupFileIsRefusedAndNothingChanges() {
        populate()
        val uri = file("damaged.expbak")
        runBlocking { actions.backup(uri, null) }
        val f = File(uri.path!!)
        f.writeBytes(f.readBytes().also { it[it.size - 30] = (it[it.size - 30] + 1).toByte() })
        resetApp()
        assertTrue(assertFailsWith<BackupException> { runBlocking { actions.open(uri, null) } }.message!!.contains("damaged"))
        assertEquals(0L, entryCount())
    }

    @Test
    fun theExcelExportIsAValidWorkbookWithNumericAmountsAndOneRowPerEntry() {
        populate()
        val uri = file("out.xlsx")
        assertEquals(5, runBlocking { actions.exportXlsx(uri) })
        val parts = java.util.zip.ZipInputStream(File(uri.path!!).inputStream()).use { z -> generateSequence { z.nextEntry }.associate { e -> e.name to z.readBytes().decodeToString() } }
        assertTrue("xl/worksheets/sheet1.xml" in parts && "xl/workbook.xml" in parts)
        val sheet = parts.getValue("xl/worksheets/sheet1.xml")
        assertEquals(6, Regex("<row ").findAll(sheet).count(), "a header and five entries")
        assertTrue(Regex("<c r=\"D2\"><v>[0-9.]+</v></c>").containsMatchIn(sheet), "the amount is a number cell")
        assertTrue(sheet.contains("Shop, 3"), "text with a comma is intact")
    }

    @Test
    fun theCsvExportHasTheHeaderTheEntriesAndTheByteOrderMark() {
        populate()
        val uri = file("out.csv")
        assertEquals(5, runBlocking { actions.exportCsv(uri) })
        val bytes = File(uri.path!!).readBytes()
        assertEquals(listOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), bytes.take(3))
        val text = bytes.decodeToString(3, bytes.size)
        assertTrue(text.startsWith("Date,Time,Type,Amount"))
        assertTrue(text.contains("\"Shop, 3\""))
    }

    // ---- the screen ----

    @Test
    fun openingABackupShowsAPreviewAndNothingChangesUntilRestoreIsConfirmed() {
        populate()
        val uri = file("ui.expbak")
        runBlocking { actions.backup(uri, null) }
        resetApp()
        launch()
        viewModel().openBackup(uri)
        waitFor("restore_preview")
        waitAnyText("5 entries are new to this phone")
        assertEquals(0L, entryCount())
        compose.onNodeWithTag("restore_cancel").performClick()
        waitGone("restore_preview")
        assertEquals(0L, entryCount())
        viewModel().openBackup(uri)
        waitFor("restore_confirm")
        compose.onNodeWithTag("restore_confirm").performClick()
        waitText("data_status", "Restored: 5 entries added")
        assertEquals(5L, entryCount())
        viewModel().openBackup(uri)
        waitAnyText("5 are already here")
    }

    @Test
    fun anEncryptedBackupAsksForThePassphraseOnTheScreenAndAWrongOneIsSaidOutLoud() {
        populate()
        val uri = file("ui-secret.expbak")
        runBlocking { actions.backup(uri, "correct horse") }
        resetApp()
        launch()
        viewModel().openBackup(uri)
        waitFor("open_passphrase")
        compose.onNodeWithTag("open_passphrase").performTextInput("wrong one!")
        compose.onNodeWithTag("open_unlock").performClick()
        waitText("data_status", "passphrase is wrong")
        waitFor("open_passphrase")
        compose.onNodeWithTag("open_passphrase").performTextInput("correct horse")
        compose.onNodeWithTag("open_unlock").performClick()
        waitFor("restore_preview")
    }

    @Test
    fun theBackupButtonStaysOffUntilThePassphrasesMatch() {
        launch()
        compose.onNodeWithTag("backup_pass").performTextInput("abc")
        waitFor("backup_pass2")
        compose.onNodeWithTag("create_backup").assertIsNotEnabled()
        compose.onNodeWithTag("backup_pass").performTextInput("defgh")
        compose.onNodeWithTag("backup_pass2").performTextInput("abcdefgX")
        compose.onNodeWithTag("create_backup").assertIsNotEnabled()
    }
}

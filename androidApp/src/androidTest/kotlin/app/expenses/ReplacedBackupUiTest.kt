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
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Transfer
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Restoring backups made before and after the type of an entry was changed, on the real encrypted database and through the real restore dialog: the older backup does not bring the replaced
 * expense back next to the transfer, the newer one applied to an older state takes the old expense away, and the preview and the message say so in plain words. Invented data.
 */
@RunWith(AndroidJUnit4::class)
class ReplacedBackupUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val actions get() = DataActions(context, container)
    private val inr = Currency.INR
    private var scenario: ActivityScenario<MainActivity>? = null
    private val files = mutableListOf<File>()

    private fun resetApp() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } } }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp(); files.forEach { it.delete() } }

    private fun file(name: String): Uri = Uri.fromFile(File(context.cacheDir, name).also { files += it })
    @Suppress("UNCHECKED_CAST") private fun <T> db(block: (AppContainer.Opened.Ok) -> T): T = runBlocking { container.write(block) } as T
    private fun live() = db { ok -> ok.db.schemaQueries.liveEntriesInInsertionOrder().executeAsList().map { it.id }.toSet() }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun waitAnyText(text: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun anyText(text: String) = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitText(tag: String, text: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag) and hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun viewModel(): LedgerViewModel { var vm: LedgerViewModel? = null; scenario!!.onActivity { vm = ViewModelProvider(it)[LedgerViewModel::class.java] }; return vm!! }

    private fun launch() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        waitFor("add")
        compose.openMore()
        waitFor("open_data")
        compose.onNodeWithTag("open_data").also { runCatching { it.performScrollTo() } }.performClick()
        waitFor("open_backup")
    }

    /** Three expenses from the Cash account (no bank reference: the case a restore could not tell apart before). */
    private fun seed() = db { ok ->
        for (i in 0..2) ok.store.save(Expense(EntryId("e$i"), System.currentTimeMillis() - i * 1_000, AccountId("cash"), Money(10_000L * (i + 1), inr), listOf(CategorySplit(CategoryId("Food"), Money(10_000L * (i + 1), inr)))), EntryDetails(merchant = "Shop $i"))
    }

    private fun makeTransfer() = db { ok -> ok.kindChanges.change("e0", Transfer(EntryId("t0"), System.currentTimeMillis(), AccountId("cash"), AccountId("bank"), Money(10_000, inr)), "Shop 0", emptyList()) }

    private fun restoreThrough(uri: Uri, passphrase: String? = null) = runBlocking { actions.restore((actions.open(uri, passphrase) as DataActions.Opened.Ready).data) }

    @Test
    fun theOlderBackupRestoredAfterTheNewerOneDoesNotBringTheReplacedExpenseBackAndTheDialogAndMessageSaySo() {
        seed()
        val old = file("old.expbak"); runBlocking { actions.backup(old, null) }
        makeTransfer()
        val new = file("new.expbak"); runBlocking { actions.backup(new, "correct horse battery") } // the newer one is a sealed file
        resetApp() // another phone
        restoreThrough(new, "correct horse battery")
        assertEquals(setOf("t0", "e1", "e2"), live())
        launch()
        viewModel().openBackup(old.toString())
        waitFor("restore_preview")
        waitFor("restore_replaced")
        waitAnyText("1 entry in this backup was changed to another type later")
        assertFalse(has("restore_removed"))
        waitAnyText("0 entries are new to this phone"); waitAnyText("2 are already here"); waitAnyText("Nothing is overwritten or deleted.")
        assertEquals(setOf("t0", "e1", "e2"), live(), "the preview changes nothing")
        compose.onNodeWithTag("restore_confirm").performClick()
        waitText("data_status", "Restored: 0 entries added, 2 were already here and were left as they are. 1 entry was not added because a type change had replaced it.")
        assertEquals(setOf("t0", "e1", "e2"), live(), "no old expense next to the transfer")
        // opening it again says the same, and restoring again changes nothing
        viewModel().openBackup(old.toString())
        waitFor("restore_replaced")
        compose.onNodeWithTag("restore_confirm").performClick()
        waitText("data_status", "1 entry was not added because a type change had replaced it.")
        assertEquals(setOf("t0", "e1", "e2"), live())
    }

    @Test
    fun theNewerBackupRestoredOverTheOlderStateTakesTheOldExpenseAwayAndTheDialogWarnsBeforeAnythingChanges() {
        seed()
        val old = file("old2.expbak"); runBlocking { actions.backup(old, null) }
        makeTransfer()
        val new = file("new2.expbak"); runBlocking { actions.backup(new, null) }
        resetApp()
        restoreThrough(old)
        assertEquals(setOf("e0", "e1", "e2"), live())
        launch()
        viewModel().openBackup(new.toString())
        waitFor("restore_preview")
        waitFor("restore_removed")
        waitAnyText("1 entry on this phone was changed to another type in this backup, and this phone still has the older copy. The older copy will be taken away, so it is not counted twice.")
        waitAnyText("1 entries are new to this phone"); waitAnyText("Nothing is overwritten.")
        assertFalse(anyText("Nothing is overwritten or deleted"), "this restore does remove something, so the dialog does not say it removes nothing")
        assertFalse(has("restore_replaced"))
        assertEquals(setOf("e0", "e1", "e2"), live(), "the preview changes nothing")
        compose.onNodeWithTag("restore_cancel").performClick(); waitGone("restore_preview")
        assertEquals(setOf("e0", "e1", "e2"), live(), "cancelled: nothing changed")
        viewModel().openBackup(new.toString())
        waitFor("restore_confirm")
        compose.onNodeWithTag("restore_confirm").performClick()
        waitText("data_status", "1 entry here was replaced by a type change in this backup and was taken away so it is not counted twice.")
        assertEquals(setOf("t0", "e1", "e2"), live())
        // the taken-away expense is a deleted row, as on the phone that made the change
        assertEquals(1L, db { ok -> ok.db.schemaQueries.entryById("e0").executeAsOne().deleted })
    }

    @Test
    fun aBackupOfALedgerWithoutATypeChangeShowsAndSaysNothingNewAndRestoresAsItAlwaysDid() {
        seed()
        val plain = file("plain.expbak"); runBlocking { actions.backup(plain, null) }
        resetApp()
        launch()
        viewModel().openBackup(plain.toString())
        waitFor("restore_preview")
        waitAnyText("3 entries are new to this phone"); waitAnyText("Nothing is overwritten or deleted.")
        assertFalse(has("restore_replaced")); assertFalse(has("restore_removed"))
        compose.onNodeWithTag("restore_confirm").performClick()
        waitText("data_status", "Restored: 3 entries added, 0 were already here and were left as they are.")
        assertFalse(anyText("type change"))
        assertEquals(setOf("e0", "e1", "e2"), live())
    }

    @Test
    fun theRecordIsAddedForEveryWayAnEntryIsReplacedAndRestoredBackOnAnotherPhoneInEitherOrder() {
        // a type change in the editor's way (KindChanges.change) and the other half of a pair (KindChanges.absorb) are both in the file, both restored the same way
        db { ok ->
            ok.store.save(Expense(EntryId("x"), 1_000, AccountId("cash"), Money(50_000, inr), listOf(CategorySplit(CategoryId("Food"), Money(50_000, inr)))), EntryDetails(merchant = "Moved out"))
            ok.store.save(app.expenses.core.domain.ledger.Income(EntryId("i"), 1_100, AccountId("bank"), Money(50_000, inr), CategoryId("income:salary")), EntryDetails(merchant = "Moved in"))
        }
        val old = file("old3.expbak"); runBlocking { actions.backup(old, null) }
        db { ok ->
            val c = ok.kindChanges.change("x", Transfer(EntryId("t"), 1_000, AccountId("cash"), AccountId("bank"), Money(50_000, inr)), "Moved", emptyList())
            ok.kindChanges.absorb("i", c.newId)
        }
        val new = file("new3.expbak"); runBlocking { actions.backup(new, null) }
        assertEquals(setOf("t"), live())
        for (order in listOf(listOf(old, new), listOf(new, old))) {
            resetApp()
            order.forEach { restoreThrough(it) }
            assertEquals(setOf("t"), live(), "the transfer alone, ${order.map { it.lastPathSegment }}")
        }
    }
}

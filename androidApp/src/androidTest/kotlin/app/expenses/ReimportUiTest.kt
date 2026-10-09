package app.expenses

import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import app.expenses.core.storage.EncryptedDriver
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** FR-LED-04: bringing the same transaction in again (a text delivered again, a statement re-imported) never undoes what the person did to it. */
@RunWith(AndroidJUnit4::class)
class ReimportUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.close() }; EncryptedDriver.reset(context) }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    private val oct5 = java.time.LocalDate.of(2026, 10, 5).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val text = "Rs.1,250.00 debited from A/c XX1234 on 05-10-26 to VPA shop@okhdfc (UPI Ref 628374659283). Not you? Call 18002586161"
    private fun ingest(at: Long) = runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", text, at) }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() }
    private fun textOf(tag: String): String { waitFor(tag); return compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.let { c -> if (c.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)) c[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text } else "" } }
    private fun openInbox() { tap("more_menu"); tap("open_inbox"); waitFor("inbox_back") }
    private fun pendingId() = runBlocking { container.write { it.inbox.list().single().id } }!!
    private fun saveSmsEntry() = runBlocking { container.write { it.store.save(Expense(EntryId("kept"), oct5, AccountId("bank"), Money(125_000, Currency.INR), listOf(CategorySplit(CategoryId("Fun"), Money(125_000, Currency.INR)))), EntryDetails(merchant = "My own name for it", source = "sms", sourceRef = "628374659283")) } }
    private fun live() = runBlocking { container.write { it.db.schemaQueries.recentEntries(50).executeAsList() } }!!

    @Test
    fun aTextDeliveredAgainAfterTheEntryWasEditedIsFlaggedAndCannotOverwriteOrDuplicateIt() {
        saveSmsEntry(); ingest(9_000)
        launch(); openInbox(); val id = pendingId(); waitFor("inbox_duplicate_$id")
        assertTrue(textOf("inbox_duplicate_$id").startsWith("Already in your ledger: My own name for it"))
        tap("inbox_add_$id"); waitFor("inbox_problem_$id")
        val e = live().single()
        assertEquals("My own name for it", e.merchant); assertEquals(125_000, e.amount_minor)
    }

    @Test
    fun anEntryTheUserDeletedIsNotBroughtBackByTheSameTextAndTheReasonIsClear() {
        saveSmsEntry(); runBlocking { container.write { it.store.delete(EntryId("kept")) } }
        ingest(9_000)
        launch(); openInbox(); val id = pendingId(); waitFor("inbox_$id")
        tap("inbox_add_$id"); waitFor("inbox_problem_$id")
        assertTrue(textOf("inbox_problem_$id").contains("deleted"), textOf("inbox_problem_$id"))
        assertEquals(emptyList(), live(), "still deleted")
    }
}

package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.toPlainString
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Refund
import app.expenses.core.storage.EncryptedDriver
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class EditDeleteTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val inr = Currency.INR
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() {
        runBlocking { container.close() }
        EncryptedDriver.reset(context)
    }

    @Before fun fresh() = resetApp()

    @After fun cleanUp() {
        compose.waitForIdle(); scenario?.close()
        resetApp()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        waitFor("add")
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    private fun waitForText(text: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    private fun waitGone(text: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty() }
    private fun waitSpend(text: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("month_spend") and hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    /** Puts an expense in the database before the screen opens, so a test starts with something to edit. */
    private fun seed(id: String, amount: Long, category: String?, note: String, atMillis: Long = System.currentTimeMillis(), source: String = "manual", ref: String? = null) = runBlocking {
        container.write { ok ->
            val splits = category?.let { listOf(CategorySplit(CategoryId(it), Money(amount, inr))) }.orEmpty()
            ok.store.save(Expense(EntryId(id), atMillis, AccountId("cash"), Money(amount, inr), splits), EntryDetails(merchant = note, source = source, sourceRef = ref))
        }
    }

    @Test
    fun tappingARowOpensTheEditorWithItsValuesAndSavingChangesTheTotal() {
        seed("e1", 10_000, "Food", "Cafe")
        launch(); waitSpend("₹100.00")
        compose.onNodeWithTag("row_Cafe").performClick()
        waitFor("amount")
        compose.onNodeWithTag("amount").assertTextEquals("Amount (₹)", "100.00")
        compose.onNodeWithTag("note").assertTextEquals("Note or merchant", "Cafe")
        compose.onNodeWithTag("amount").performTextClearance()
        compose.onNodeWithTag("amount").performTextInput("150.5")
        compose.onNodeWithText("Bills").performClick()
        compose.onNodeWithTag("save").performClick()
        waitSpend("₹150.50")
        waitForText("₹150.50")
        val stored: Map<String?, Long>? = runBlocking {
            container.write { ok -> ok.store.spendByCategory(inr).entries.associate { (k, v) -> k?.value to v.minorUnits } }
        }
        assertEquals(mapOf<String?, Long>("Bills" to 15_050L), stored)
    }

    @Test
    fun cancellingAnEditChangesNothing() {
        seed("e1", 10_000, "Food", "Cafe")
        launch(); waitSpend("₹100.00")
        compose.onNodeWithTag("row_Cafe").performClick()
        waitFor("amount")
        compose.onNodeWithTag("amount").performTextClearance()
        compose.onNodeWithTag("amount").performTextInput("999")
        compose.onNodeWithTag("cancel").performClick()
        waitFor("add")
        compose.onNodeWithTag("month_spend").assertTextEquals("₹100.00")
    }

    @Test
    fun swipingARowDeletesItAndUndoBringsItBack() {
        seed("e1", 10_000, "Food", "Cafe")
        launch(); waitSpend("₹100.00")
        compose.onNodeWithTag("row_Cafe").performTouchInput { swipeLeft() }
        waitSpend("₹0.00")
        waitForText("Undo")
        compose.onNodeWithText("Undo").performClick()
        waitSpend("₹100.00")
        compose.onNodeWithTag("row_Cafe").assertExists()
    }

    @Test
    fun anExpenseWithRefundsOpensReadOnlyWithAReason() {
        seed("e1", 10_000, "Food", "Cafe")
        runBlocking { container.write { ok -> ok.store.save(Refund(EntryId("r1"), System.currentTimeMillis(), AccountId("cash"), Money(2_000, inr), EntryId("e1"))) } }
        launch(); waitSpend("₹80.00")
        compose.onNodeWithTag("row_Cafe").performClick()
        waitFor("read_only_reason")
        compose.onNodeWithTag("read_only_reason").assertTextEquals("This expense has refunds. Delete the refunds first to edit it.")
    }

    // ---- the actions underneath, with cases the screen cannot easily reach ----

    private fun draft(id: String, minor: Long, category: String, note: String, account: String, at: Long) =
        app.expenses.core.domain.EntryDraft(id, app.expenses.core.domain.DraftKind.EXPENSE, Money(minor, inr).toPlainString(), category, emptyList(), account, "", note, at)

    @Test
    fun movingAnExpenseToAnotherMonthTakesItOutOfThisMonthsTotal() {
        seed("e1", 10_000, "Food", "Cafe")
        val now = System.currentTimeMillis()
        runBlocking {
            container.write { ok ->
                EntryActions(ok).save(draft("e1", 10_000, "Food", "Cafe", "cash", now - 70L * 86_400_000L))
                ok.store.spendByCategory(inr, from = now - 20L * 86_400_000L)
            }
        }.let { assertTrue(it!!.isEmpty(), "the moved expense must not count in a window that starts after its new date") }
        launch(); waitSpend("₹0.00")
    }

    @Test
    fun editingKeepsWhereAnImportedEntryCameFrom() {
        seed("e1", 10_000, "Food", "Cafe", source = "sms", ref = "UPI123456789012")
        runBlocking { container.write { ok -> EntryActions(ok).save(draft("e1", 12_000, "Food", "Cafe", "cash", System.currentTimeMillis())) } }
        val row = runBlocking { container.write { it.db.schemaQueries.entryById("e1").executeAsOne() } }!!
        assertEquals("sms", row.source)
        assertEquals("UPI123456789012", row.source_ref)
        assertEquals(12_000L, row.amount_minor)
    }

    @Test
    fun deletedEntriesAreNotEditableAndSharedOnesAreExplained() {
        seed("e1", 10_000, "Food", "Cafe")
        runBlocking {
            container.write { ok ->
                val a = EntryActions(ok)
                a.delete("e1")
                assertEquals(null, a.editable("e1"))
                assertFailsWith<IllegalArgumentException> { a.save(draft("e1", 5, "Food", "", "cash", 1L)) }
                a.restore("e1")
                assertTrue(a.editable("e1") is Editable.Form)
            }
        }
    }
}

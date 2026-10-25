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
import app.expenses.core.domain.AccountLook
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The first-week checklist on Home. It starts on the first entry, ticks each step as it becomes true, opens the screen for a step when tapped, hides itself when
 * everything is done, after 14 days or when closed, and is never shown on a ledger that already had entries before it existed. Written without a device to run on.
 */
@RunWith(AndroidJUnit4::class)
class FirstWeekUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val day = 86_400_000L

    @Before fun fresh() { runBlocking { container.startFresh() } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick() }
    private fun textOf(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text }
    /** The card is folded when it first shows: wait for it, then open it unless it is already open. */
    private fun openCard() { compose.waitUntil(10_000) { has("first_week_card") }; if (!has("first_week_item_BUDGET")) tap("first_week_toggle"); waitFor("first_week_item_BUDGET") }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun setting(key: String) = runBlocking { container.write { it.templates.setting(key) } }
    private fun put(key: String, value: String) { runBlocking { container.write { it.templates.putSetting(key, value) } } }
    private fun anEntry() { runBlocking { container.write { it.store.save(Expense(EntryId("e1"), System.currentTimeMillis(), AccountId("bank"), Money(10_000, Currency.INR)), EntryDetails(merchant = "Tea")) } } }
    /** A ledger in its first week: an entry, and the clock started [daysAgo] days back. */
    private fun inFirstWeek(daysAgo: Long = 1) { anEntry(); put("first_week_started", (System.currentTimeMillis() - daysAgo * day).toString()) }

    @Test
    fun theCardTicksTheEntryLeavesTheRestOpenAndEachRowOpensItsScreen() {
        inFirstWeek()
        launch(); openCard()
        assertTrue(textOf("first_week_progress").startsWith("1 of "), textOf("first_week_progress"))
        waitFor("first_week_done_ENTRY")
        listOf("ACCOUNT_DIGITS", "BUDGET", "CAPTURE", "BACKUP").forEach { waitFor("first_week_todo_$it") }
        tap("first_week_item_ACCOUNT_DIGITS"); waitFor("accounts_list"); tap("back"); openCard()
        tap("first_week_item_BUDGET"); waitFor("budget_back"); tap("budget_back"); openCard()
        tap("first_week_item_BACKUP"); waitFor("data_back"); tap("data_back"); openCard()
        tap("first_week_item_CAPTURE"); waitFor("capture_back"); tap("capture_back"); openCard()
        tap("first_week_item_ENTRY"); waitFor("amount") // the add form
    }

    @Test
    fun stepsTickThemselvesAndTheCardGoesWhenAllAreDone() {
        inFirstWeek()
        runBlocking { container.write { ok ->
            ok.looks.save(AccountLook("bank", last4 = "1234"))
            ok.budgets.setLimit(null, Money(500_000, Currency.INR))
        } }
        launch(); openCard()
        waitFor("first_week_done_ACCOUNT_DIGITS"); waitFor("first_week_done_BUDGET"); waitFor("first_week_done_ENTRY")
        assertTrue(textOf("first_week_progress").startsWith("3 of "), textOf("first_week_progress"))
        put("capture_notif_on", "1"); put("backup_last_at", System.currentTimeMillis().toString())
        launch(); waitFor("add")
        compose.waitUntil(10_000) { !has("first_week_card") }
    }

    @Test
    fun closingTheCardIsForGood() {
        inFirstWeek()
        launch(); openCard()
        tap("first_week_dismiss"); waitGone("first_week_card")
        assertEquals("1", setting("first_week_dismissed"))
        launch(); waitFor("add"); compose.waitForIdle()
        assertTrue(!has("first_week_card"), "it stays closed")
    }

    @Test
    fun theTitleFoldsTheCardToOneLine() {
        inFirstWeek()
        launch(); compose.waitUntil(10_000) { has("first_week_card") }
        waitGone("first_week_item_BUDGET") // it starts folded, so it never pushes the list down
        assertTrue(textOf("first_week_progress").contains("tap to open"))
        tap("first_week_toggle"); waitFor("first_week_item_BUDGET")
        tap("first_week_toggle"); waitGone("first_week_item_BUDGET")
    }

    @Test
    fun afterTwoWeeksTheCardIsNotShown() {
        inFirstWeek(daysAgo = 15)
        launch(); compose.waitForIdle()
        assertTrue(!has("first_week_card"))
    }

    @Test
    fun aLedgerThatAlreadyHadEntriesWhenTheCardAppearedNeverGetsIt() {
        anEntry() // before the app has looked at the ledger once
        launch(); compose.waitForIdle()
        assertTrue(!has("first_week_card"))
        assertEquals("0", setting("first_week_started"))
    }

    @Test
    fun aNewLedgerGetsTheCardOnceItsFirstEntryIsThere() {
        launch(); compose.waitForIdle()
        assertTrue(!has("first_week_card"), "no entry yet")
        assertEquals("pending", setting("first_week_started"))
        anEntry()
        launch(); openCard()
        assertTrue((setting("first_week_started")?.toLongOrNull() ?: 0L) > System.currentTimeMillis() - day, "the clock started with that first entry")
    }
}

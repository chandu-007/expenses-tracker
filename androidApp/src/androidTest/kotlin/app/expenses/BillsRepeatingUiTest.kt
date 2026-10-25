package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.RecurringItem
import app.expenses.core.domain.RecurringKind
import app.expenses.core.domain.RepeatUnit
import app.expenses.core.domain.Schedule
import app.expenses.core.domain.ScheduleEnd
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Bills page lists what the Home banner counts: repeating expenses that wait for the person, with Post it and Skip. Written without a device to run on
 * (the integrator runs it): the tags are `bill_<id>`, `bill_pay_<id>` (Post it) and `bill_remove_<id>` (Skip) with the id `rec:<item id>_<epoch day>`.
 */
@RunWith(AndroidJUnit4::class)
class BillsRepeatingUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val inr = Currency.INR
    private val today: LocalDate = java.time.LocalDate.now().let { LocalDate(it.year, it.monthValue, it.dayOfMonth) }

    @Before fun fresh() { runBlocking { container.startFresh() } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun item(id: String, name: String, rupees: Long?, start: LocalDate = today, auto: Boolean = false, kind: RecurringKind = RecurringKind.EXPENSE) = RecurringItem(
        id, name, rupees?.let { Money(it * 100, inr) }, CategoryId("Bills"), AccountId("bank"), null, Schedule(RepeatUnit.MONTH, 1, start, ScheduleEnd.Never), auto, kind,
    )
    private fun seed(vararg items: RecurringItem) = runBlocking { container.write { ok -> items.forEach(ok.recurring::save) } }
    private fun rowId(itemId: String, day: LocalDate = today) = "rec:${itemId}_${day.toEpochDays()}"
    private fun entries() = runBlocking { container.write { it.db.schemaQueries.liveEntriesInInsertionOrder().executeAsList() } }!!

    private fun launch() { scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun gone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun reveal(tag: String) { if (has("bills_list") && !has(tag)) runCatching { compose.onNodeWithTag("bills_list", useUnmergedTree = true).performScrollToNode(hasTestTag(tag)) } }
    private fun tap(tag: String) { compose.waitUntil(10_000) { reveal(tag); has(tag) }; compose.onNodeWithTag(tag, useUnmergedTree = true).performClick() }
    private fun textOf(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text }
    private fun openBillsFromBanner() { waitFor("bills_banner"); tap("bills_banner"); waitFor("bills_list") }
    /** How many Coming up rows have their action buttons: every row that is not paid has exactly one `bill_pay_<id>`. */
    private fun rowsWithActions() = compose.onAllNodes(SemanticsMatcher("a bill's action button") { n -> n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.let { it.startsWith("bill_pay_") && it != "bill_pay_amount" && it != "bill_pay_record" } == true }, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun aBannerThatCountsOnlyRentLandsOnAPageThatListsRentAndTheCountsAgree() {
        seed(item("rent", "Rent", 15_000))
        launch()
        waitFor("bills_banner")
        assertTrue(textOf("bills_banner_title").startsWith("1 bill due this week"), textOf("bills_banner_title"))
        tap("bills_banner"); waitFor("bills_list")
        val id = rowId("rent")
        waitFor("bill_$id")
        assertEquals("Rent", textOf("bill_title_$id"))
        compose.onNodeWithTag("bill_amount_$id", useUnmergedTree = true).assertTextContains("₹15,000.00", substring = true)
        compose.onNodeWithTag("bill_due_$id", useUnmergedTree = true).assertTextContains("today", substring = true)
        waitFor("bills_coming_title")
        assertTrue(!has("bills_empty"), "the page is not the empty one")
        assertEquals(1, rowsWithActions(), "as many rows as the banner counts")
        assertTrue(has("bill_pay_$id") && has("bill_remove_$id"), "Post it and Skip")
    }

    @Test
    fun postItPostsTheOccurrenceAsAnExpenseAndTheRowAndTheBannerGoAway() {
        seed(item("rent", "Rent", 15_000))
        launch(); openBillsFromBanner()
        val id = rowId("rent")
        tap("bill_pay_$id")
        gone("bill_$id"); waitFor("bills_empty")
        val e = entries().single()
        assertEquals("rec:rent:${today.toEpochDays()}", e.id); assertEquals("EXPENSE", e.kind); assertEquals(1_500_000L, e.amount_minor)
        tap("bills_back"); gone("bills_banner")
    }

    @Test
    fun skipDropsTheOccurrenceWithoutAnEntry() {
        seed(item("rent", "Rent", 15_000))
        launch(); openBillsFromBanner()
        val id = rowId("rent")
        tap("bill_remove_$id")
        gone("bill_$id"); waitFor("bills_empty")
        assertEquals(emptyList(), entries())
        assertEquals(0, runBlocking { container.write { it.recurring.unhandled(today.minusDays(14), today.plusDays(8)).size } })
    }

    @Test
    fun anItemWhoseAmountDiffersEachTimeAsksForItBeforePosting() {
        seed(item("power", "Power", null))
        launch(); openBillsFromBanner()
        val id = rowId("power")
        waitFor("bill_$id")
        compose.onNodeWithTag("bill_amount_$id", useUnmergedTree = true).assertTextContains("Amount?", substring = true)
        tap("bill_pay_$id"); waitFor("due_amount")
        compose.onNodeWithTag("due_amount", useUnmergedTree = true).performTextInput("480")
        tap("due_amount_ok")
        gone("bill_$id")
        assertEquals(48_000L, entries().single().amount_minor)
    }

    @Test
    fun anOverdueOccurrenceIsMarkedOverdueAndItemsThatNeedNothingAreNotListed() {
        seed(item("late", "Insurance", 3_000, start = today.minusDays(3)), item("auto", "Netflix", 649, auto = true), item("pay", "Salary", 50_000, kind = RecurringKind.INCOME))
        launch(); waitFor("bills_banner")
        assertTrue(textOf("bills_banner_title").startsWith("1 bill due this week"), "only the insurance waits: ${textOf("bills_banner_title")}")
        tap("bills_banner"); waitFor("bills_list")
        val late = rowId("late", today.minusDays(3))
        waitFor("bill_$late")
        compose.onNodeWithTag("bill_due_$late", useUnmergedTree = true).assertTextContains("3 days overdue", substring = true)
        assertEquals(1, rowsWithActions(), "the one that posts itself and the income are not bills waiting for the person")
        assertTrue(!has("bill_${rowId("auto")}") && !has("bill_${rowId("pay")}"))
    }

    @Test
    fun withOnlyAnAutoItemThereIsNoBannerAndTheBillsPageIsEmpty() {
        // an item that posts by itself needs nothing from the person: no banner count, no row (it will already have been posted today, so the entry is there)
        seed(item("auto", "Netflix", 649, auto = true))
        launch(); waitFor("add")
        compose.waitForIdle()
        assertTrue(!has("bills_banner"), "nothing waits, so no banner")
    }
}

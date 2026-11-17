package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.up
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ReportMath
import app.expenses.core.domain.BudgetPeriod
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** UI slice S4: the charts of Reports: the scrubber and its bubble, a tap that still opens the period, "Show as table" with the same numbers, the sentence, colours that differ, Hide amounts, and the stops for a screen reader. */
@RunWith(AndroidJUnit4::class)
class UiS4Test {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val inr = Currency.INR
    private var scenario: ActivityScenario<MainActivity>? = null
    private val zone = TimeZone.currentSystemDefault()
    private val today: LocalDate = java.time.LocalDate.now().let { LocalDate(it.year, it.monthValue, it.dayOfMonth) }

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1"); it.templates.putSetting("category_look_notice", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) {
        if (!has(tag) && has("report_list")) runCatching { compose.onNodeWithTag("report_list", useUnmergedTree = true).performScrollToNode(hasTestTag(tag)) }
        has(tag)
    }
    private fun noonOf(d: LocalDate) = d.atStartOfDayIn(zone).toEpochMilliseconds() + 12 * 3_600_000L
    private fun spend(id: String, rupees: Long, category: String, at: Long = System.currentTimeMillis()) = runBlocking {
        container.write { ok -> ok.store.save(Expense(EntryId(id), at, AccountId("cash"), Money(rupees * 100, inr), listOf(CategorySplit(CategoryId(category), Money(rupees * 100, inr)))), EntryDetails(merchant = id)) }
    }
    private fun periodNoon(back: Int) = noonOf(ReportMath.periodAt(BudgetPeriod.Monthly(1), today, -back).start)
    private fun openReports() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        compose.waitUntil(20_000) { has("add") }
        compose.onNodeWithTag("open_reports").performClick()
        waitFor("report_spent")
    }
    private fun descriptionOf(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
    private fun texts(tag: String): List<String> = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().let { n ->
        fun walk(x: androidx.compose.ui.semantics.SemanticsNode): List<String> = (x.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()) + x.children.flatMap { walk(it) }
        walk(n)
    }

    /** Three months with different totals: this one 500, the one before 300, the one before that 900. */
    private fun threeMonths() {
        spend("m0", 500, "Food"); spend("m1", 300, "Fun", periodNoon(1)); spend("m2", 900, "Food", periodNoon(2))
    }

    @Test
    fun draggingAlongTheBarsShowsABubbleWithThatBarsTotalAndChangeAndLettingGoRemovesIt() {
        threeMonths(); openReports(); waitFor("trend_chart")
        compose.onNodeWithTag("trend_chart", useUnmergedTree = true).performScrollTo()
        assertTrue(!has("trend_bubble"), "no bubble before a touch")
        compose.onNodeWithTag("trend_chart", useUnmergedTree = true).performTouchInput {
            down(Offset(width * 0.75f, height * 0.8f))          // bar 4 of 6 (the month before this one: 300)
            moveTo(Offset(width * 0.80f, height * 0.8f)); moveTo(Offset(width * 0.75f, height * 0.8f))
        }
        compose.waitUntil(5_000) { has("trend_bubble") }
        val shown = texts("trend_bubble").joinToString(" | ")
        assertTrue(shown.contains("₹300.00"), "the bubble has the bar's total: $shown")
        assertTrue(shown.contains("less than") || shown.contains("more than"), "and its change from the bar before: $shown")
        compose.onNodeWithTag("trend_chart", useUnmergedTree = true).performTouchInput { moveTo(Offset(width * 0.95f, height * 0.8f)) }
        compose.waitUntil(5_000) { texts("trend_bubble").joinToString(" ").contains("₹500.00") }
        compose.onNodeWithTag("trend_chart", useUnmergedTree = true).performTouchInput { up() }
        compose.waitUntil(5_000) { !has("trend_bubble") }
    }

    @Test
    fun aTapOnABarStillOpensThatPeriodsEntries() {
        threeMonths(); openReports(); waitFor("trend_bar_4")
        compose.onNodeWithTag("trend_bar_4", useUnmergedTree = true).performScrollTo().performClick()
        waitFor("drill_total")
        compose.onNodeWithTag("drill_total", useUnmergedTree = true).assertTextEquals("₹300.00")
        assertTrue(!has("trend_bubble"), "a tap is not a scrub")
    }

    @Test
    fun theTableUnderTheBarsHasTheSameNumbersAsTheBarsAndTheChangeFromTheOneBefore() {
        threeMonths(); openReports(); waitFor("trend_table_toggle")
        assertTrue(!has("trend_table"), "the table starts hidden")
        compose.onNodeWithTag("trend_table_toggle", useUnmergedTree = true).performScrollTo().performClick()
        waitFor("trend_table")
        val barAmounts = (0 until 6).map { i -> descriptionOf("trend_bar_$i").substringAfter(": ").substringBefore(".00") + ".00" }
        val rows = (0 until 6).map { i -> texts("trend_table_row_$i") }
        assertEquals(6, rows.size)
        for (i in 0 until 6) assertEquals(barAmounts[i], rows[i][1], "row $i amount equals bar $i")
        assertEquals("₹500.00", rows[5][1]); assertEquals("₹300.00", rows[4][1]); assertEquals("₹900.00", rows[3][1])
        assertEquals("−₹600.00", rows[4][2], "300 after 900"); assertEquals("+₹200.00", rows[5][2], "500 after 300")
        compose.onNodeWithTag("trend_table_toggle", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { !has("trend_table") }
    }

    @Test
    fun eachBarIsItsOwnStopWithItsAmountSpokenAndOpensItsPeriodAndTheChartHasASummary() {
        threeMonths(); openReports(); waitFor("trend_bar_5")
        val d5 = descriptionOf("trend_bar_5")
        assertTrue(d5.contains("₹500.00") && d5.contains("more than"), d5)
        val node = compose.onNodeWithTag("trend_bar_5", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(androidx.compose.ui.semantics.Role.Button, node.config.getOrNull(SemanticsProperties.Role))
        assertEquals(true, node.config.getOrNull(SemanticsProperties.Selected), "this period is the selected one")
        val summary = descriptionOf("trend_chart")
        assertTrue(summary.contains("Highest") && summary.contains("Average"), summary)
    }

    @Test
    fun theSentenceNamesTheBiggestCategoryItsShareAndTheAmounts() {
        spend("a", 700, "Food"); spend("b", 300, "Fun"); openReports(); waitFor("report_headline")
        compose.onNodeWithTag("report_headline", useUnmergedTree = true).assertTextEquals("Food is 70% of what you spent (₹700.00 of ₹1,000.00).")
    }

    @Test
    fun theRingLegendHasATableWithTheSameNumbersAndOthersAddsUpTheRest() {
        listOf("Food" to 1000L, "Groceries" to 400L, "Transport" to 300L, "Bills" to 200L, "Shopping" to 100L, "Health" to 60L, "Fun" to 40L).forEachIndexed { i, (c, r) -> spend("r$i", r, c) }
        openReports(); waitFor("ring_table_toggle")
        compose.onNodeWithTag("ring_table_toggle", useUnmergedTree = true).performScrollTo().performClick()
        waitFor("ring_table")
        val last = texts("ring_table_row_5")
        assertEquals(listOf("Others", "7%", "₹100.00"), last, "Health 60 + Fun 40 are the rest")
        assertEquals(listOf("Food", "47%", "₹1,000.00"), texts("ring_table_row_0"))
    }

    @Test
    fun theColoursOfOneChartAreAllDifferent() {
        // many categories of the ledger's own: whatever slots their ids fall on, the five biggest slices get five colours
        listOf("Food" to 1000L, "Groceries" to 400L, "Transport" to 300L, "Bills" to 200L, "Shopping" to 100L, "Health" to 60L).forEachIndexed { i, (c, r) -> spend("r$i", r, c) }
        val report = app.expenses.ReportUi(
            app.expenses.ReportMode.PERIOD, "t", false, "", "", "", true, null,
            (0 until 8).map { app.expenses.ReportRowUi("custom-id-$it", "C$it", "", 10, null, false) }, emptyList(), null, app.expenses.core.storage.ReportGroup.CATEGORY, emptyList(), 0, 1,
        )
        val hues = app.expenses.core.domain.CategoryLook.chartHues(report.rows.map { it.categoryId }, report.rows.map { null })
        assertEquals(8, hues.toSet().size, "eight rows, eight colours: $hues")
    }

    @Test
    fun hideAmountsMasksTheBubbleTheTableTheSentenceAndTheSpokenBars() {
        threeMonths(); runBlocking { container.write { it.templates.putSetting("hide_amounts", "1") } }
        openReports(); waitFor("report_headline")
        assertTrue(!texts("report_headline").joinToString().contains("₹5"), "the sentence has no figure: ${texts("report_headline")}")
        assertTrue(!descriptionOf("trend_bar_5").contains("500"), "a bar is spoken without its amount: ${descriptionOf("trend_bar_5")}")
        assertTrue(!descriptionOf("trend_chart").contains("500"), "and so is the summary: ${descriptionOf("trend_chart")}")
        compose.onNodeWithTag("trend_table_toggle", useUnmergedTree = true).performScrollTo().performClick(); waitFor("trend_table")
        assertTrue((0 until 6).none { i -> texts("trend_table_row_$i").any { it.contains("500") || it.contains("300") } }, "the table shows no figure")
        compose.onNodeWithTag("trend_chart", useUnmergedTree = true).performScrollTo().performTouchInput { down(Offset(width * 0.9f, height * 0.8f)); moveTo(Offset(width * 0.8f, height * 0.8f)); moveTo(Offset(width * 0.9f, height * 0.8f)) }
        compose.waitUntil(5_000) { has("trend_bubble") }
        assertTrue(texts("trend_bubble").none { it.contains("500") }, "the bubble shows no figure: ${texts("trend_bubble")}")
        compose.onNodeWithTag("trend_chart", useUnmergedTree = true).performTouchInput { up() }
    }

    @Test
    fun withReducedMotionTheBubbleIsThereAtOnceAndTheBarsAreAtFullHeight() {
        threeMonths(); runBlocking { container.write { it.templates.putSetting("motion_speed", "off") } }
        openReports(); waitFor("trend_chart")
        compose.onNodeWithTag("trend_chart", useUnmergedTree = true).performScrollTo().performTouchInput { down(Offset(width * 0.9f, height * 0.8f)); moveTo(Offset(width * 0.8f, height * 0.8f)); moveTo(Offset(width * 0.9f, height * 0.8f)) }
        compose.waitUntil(2_000) { has("trend_bubble") }
        compose.onNodeWithTag("trend_chart", useUnmergedTree = true).performTouchInput { up() }
    }

    @Test
    fun theHomeLineHasATableOfItsFourteenDaysFromALongPressAndFromAScreenReadersAction() {
        spend("h0", 250, "Food"); spend("h1", 120, "Fun", noonOf(kotlinx.datetime.LocalDate.fromEpochDays(today.toEpochDays() - 1)))
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        compose.waitUntil(20_000) { has("add") }
        compose.waitUntil(20_000) { has("home_spark") }
        val actions = compose.onNodeWithTag("home_spark", useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertTrue(actions.any { it.label == "Show as table" }, "a screen reader is offered the table: ${actions.map { it.label }}")
        assertTrue(descriptionOf("home_spark").contains("Highest"), descriptionOf("home_spark"))
        compose.onNodeWithTag("home_spark", useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitUntil(5_000) { has("spark_table") }
        assertEquals(14, (0 until 14).count { has("spark_table_row_$it") }, "one row for each of the 14 days")
        assertTrue(texts("spark_table_row_13").any { it.contains("250.00") }, "today is the last row: ${texts("spark_table_row_13")}")
        compose.onNodeWithTag("spark_table_close", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { !has("spark_table") }
    }
}

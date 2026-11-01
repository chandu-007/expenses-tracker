package app.expenses

import android.content.res.Configuration
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Appearances
import app.expenses.core.domain.Currency
import app.expenses.core.domain.DeltaDirection
import app.expenses.core.domain.Money
import app.expenses.core.domain.Template
import app.expenses.core.domain.WidgetBudget
import app.expenses.core.domain.WidgetCategoryBar
import app.expenses.core.domain.WidgetDelta
import app.expenses.core.domain.WidgetInsights
import app.expenses.core.domain.WidgetRecent
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.widget.QuickAddWidget
import app.expenses.widget.WidgetButton
import app.expenses.widget.WidgetContent
import app.expenses.widget.WidgetModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The redesigned widget: what each size draws from the model, what it reads from the database, and that its colours read. Invented data only. */
@RunWith(AndroidJUnit4::class)
class WidgetDesignTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val inr = Currency.INR

    @Before fun fresh() { runBlocking { container.startFresh() } }
    @After fun cleanUp() { runBlocking { container.startFresh() } }

    private val chai = WidgetButton("a", "Chai", "₹20.00")
    private val auto = WidgetButton("b", "Auto", "₹50.00")
    private val lunch = WidgetButton("c", "Lunch", "₹120.00")
    private val insights = WidgetInsights(
        delta = WidgetDelta(DeltaDirection.LESS, "↓", "12%", "12% less than last month so far"),
        budget = WidgetBudget(750, false, "₹5K left of ₹20K"),
        categories = listOf(WidgetCategoryBar("Food", "₹4K", 400), WidgetCategoryBar("Bills", "₹3K", 300), WidgetCategoryBar("Travel", "₹1.5K", 150)),
        recent = listOf(WidgetRecent("Greencart", "-₹482.00", "Today", false), WidgetRecent("Salary", "+₹50,000.00", "Yesterday", true)),
    )
    private fun shown(canToggle: Boolean = true, ins: WidgetInsights = insights, nothingYet: Boolean = false) =
        WidgetModel(listOf(chai, auto, lunch), null, needsApp = false, monthSpend = "₹12,345.00", revealed = true, canToggle = canToggle, insights = ins, nothingYet = nothingYet)
    private fun hidden(canToggle: Boolean = true) = WidgetModel(listOf(chai, auto, lunch), null, needsApp = false, monthSpend = "₹••••", revealed = false, canToggle = canToggle)

    // ---- what each size draws ----

    @Test
    fun theTallWidgetShowsTheComparisonTheBudgetTheCategoriesAndTheLatestEntriesWhenTheTotalIsShown() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(320.dp, 294.dp))
        provideComposable { WidgetContent(shown()) }
        onNode(hasText("Spent this month")).assertExists(); onNode(hasText("₹12,345.00")).assertExists()
        onNode(hasText(" 12%")).assertExists(); onNode(hasContentDescription("12% less than last month so far")).assertExists()
        onNode(hasText("₹5K left of ₹20K")).assertExists()
        onNode(hasText("Where it went")).assertExists(); onNode(hasText("Food")).assertExists(); onNode(hasText("₹4K")).assertExists(); onNode(hasText("Bills")).assertExists()
        onNode(hasText("Latest")).assertExists(); onNode(hasText("Greencart")).assertExists(); onNode(hasText("-₹482.00")).assertExists(); onNode(hasText("Today")).assertExists()
        for (n in listOf("Chai", "Auto", "Lunch")) onNode(hasText(n)).assertExists()
        onNode(hasContentDescription("Hide the amount")).assertExists(); onNode(hasText("+ Add")).assertExists()
    }

    @Test
    fun aVeryWideWidgetSpellsTheComparisonOut() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(420.dp, 146.dp))
        provideComposable { WidgetContent(shown()) }
        onNode(hasText(" 12% vs last month")).assertExists()
    }

    @Test
    fun aNarrowFourCellWidgetLeavesTheComparisonOutOfItsHeader() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(250.dp, 146.dp))
        provideComposable { WidgetContent(shown()) }
        onNode(hasText(" 12%")).assertDoesNotExist(); onNode(hasText("₹12,345.00")).assertExists(); onNode(hasText("Chai")).assertExists()
    }

    @Test
    fun theMediumWidgetKeepsItsChipsAndTheComparisonAndLeavesTheListsToTheTallOne() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(320.dp, 110.dp))
        provideComposable { WidgetContent(shown()) }
        onNode(hasText(" 12%")).assertExists(); onNode(hasText("Chai")).assertExists(); onNode(hasText("Lunch")).assertExists()
        onNode(hasText("Where it went")).assertDoesNotExist(); onNode(hasText("Latest")).assertDoesNotExist(); onNode(hasText("₹5K left of ₹20K")).assertDoesNotExist()
    }

    @Test
    fun withTheTotalHiddenNothingButTheChipsAndAQuietNoteIsDrawn() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(320.dp, 294.dp))
        provideComposable { WidgetContent(hidden()) }
        onNode(hasText("₹••••")).assertExists(); onNode(hasContentDescription("Show the amount")).assertExists()
        onNode(hasText("Tap the eye to see this month in detail")).assertExists()
        for (t in listOf("Where it went", "Latest", "Greencart", "Food", "₹5K left of ₹20K")) onNode(hasText(t)).assertDoesNotExist()
        onNode(hasText("Chai")).assertExists()
    }

    @Test
    fun whenTheAppForcesTheTotalHiddenThereIsNoEyeAndTheNoteSaysSo() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(320.dp, 294.dp))
        provideComposable { WidgetContent(hidden(canToggle = false)) }
        onNode(hasContentDescription("Show the amount")).assertDoesNotExist(); onNode(hasContentDescription("Hide the amount")).assertDoesNotExist()
        onNode(hasText("Amounts are hidden")).assertExists()
    }

    @Test
    fun aMonthWithNoSpendingSaysSoWhenTheTotalIsShown() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(320.dp, 200.dp))
        provideComposable { WidgetContent(WidgetModel(listOf(chai), null, needsApp = false, monthSpend = "₹0.00", revealed = true, canToggle = true, nothingYet = true)) }
        onNode(hasText("Nothing spent yet this month")).assertExists(); onNode(hasText("₹0.00")).assertExists()
    }

    @Test
    fun theSquareWidgetHasTheEyeAndTheTinyOneDoesNot() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(110.dp, 110.dp))
        provideComposable { WidgetContent(hidden()) }
        onNode(hasText("This month")).assertExists(); onNode(hasContentDescription("Show the amount")).assertExists(); onNode(hasContentDescription("Add an expense")).assertExists()
    }

    @Test
    fun theTinyWidgetHasNoEyeButKeepsTheFigureAndAdd() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(110.dp, 60.dp))
        provideComposable { WidgetContent(hidden()) }
        onNode(hasText("This month")).assertExists(); onNode(hasContentDescription("Show the amount")).assertDoesNotExist(); onNode(hasContentDescription("Add an expense")).assertExists()
    }

    @Test
    fun aBudgetPastItsLimitIsSaidInWordsAndNotOnlyInRed() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(320.dp, 294.dp))
        provideComposable { WidgetContent(shown(ins = insights.copy(budget = WidgetBudget(1000, true, "₹3.5K over ₹20K")))) }
        onNode(hasText("₹3.5K over ₹20K")).assertExists()
    }

    // ---- what it reads ----

    private fun spend(id: String, merchant: String?, category: String, minor: Long, at: Long) = runBlocking {
        container.write { ok -> ok.store.save(Expense(EntryId(id), at, AccountId("cash"), Money(minor, inr), listOf(CategorySplit(CategoryId(category), Money(minor, inr)))), app.expenses.core.storage.EntryDetails(merchant = merchant)) }
    }

    private fun seedTwoMonths() {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        fun noon(d: java.time.LocalDate) = d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val thisFirst = today.withDayOfMonth(1); val lastFirst = thisFirst.minusMonths(1)
        // this month: 800 on the 1st (food), 200 now (bills); last month: 1,000 on its 1st, which is inside "the same days" even on the 1st, and 5,000 on its last day, which is not (unless today is the last day of a month)
        spend("t1", "Greencart", "Food", 80_000, noon(thisFirst).coerceAtMost(System.currentTimeMillis() - 60_000))
        spend("t2", "Power Co", "Bills", 20_000, System.currentTimeMillis() - 1_000)
        spend("l1", "Old", "Food", 100_000, noon(lastFirst))
        spend("l2", "Late", "Food", 500_000, noon(lastFirst.withDayOfMonth(lastFirst.lengthOfMonth())))
        runBlocking { container.write { it.budgets.setLimit(null, Money(500_000, inr)) } }
    }

    @Test
    fun whenTheTotalIsShownTheWidgetReadsTheComparisonBudgetCategoriesAndLatestEntries() {
        seedTwoMonths()
        runBlocking { container.write { it.templates.putSetting("widget_show_total", "1") } }
        val d = runBlocking { QuickAddWidget().load(context) }!!
        assertTrue(d.revealed && d.canToggle); assertEquals("₹1,000.00", d.monthSpend)
        val i = d.insights
        val today = java.time.LocalDate.now()
        // the comparison counts last month only up to today's day number: the 1,000 on its 1st, never the 5,000 on its last day (except when today is itself the last day)
        val lastLen = today.withDayOfMonth(1).minusMonths(1).lengthOfMonth()
        assertNotNull(i.delta, "last month had spending up to this day")
        if (today.dayOfMonth < lastLen) assertEquals(DeltaDirection.SAME, i.delta!!.direction, "1,000 against 1,000")
        assertEquals("₹4K left of ₹5K", i.budget!!.text)
        assertEquals(200, i.budget!!.permille)
        assertEquals(listOf("Food", "Bills"), i.categories.map { it.name }); assertEquals(listOf(800, 200), i.categories.map { it.permille })
        assertEquals(listOf("Power Co", "Greencart"), i.recent.take(2).map { it.title }); assertEquals("-₹200.00", i.recent.first().amountText)
    }

    @Test
    fun whenTheTotalIsHiddenNoneOfThatIsReadAtAll() {
        seedTwoMonths()
        // not shown yet (the default), then shown but the app hides amounts
        assertEquals(WidgetInsights.NONE, runBlocking { QuickAddWidget().load(context) }!!.insights)
        runBlocking { container.write { it.templates.putSetting("widget_show_total", "1"); it.templates.putSetting("hide_amounts", "1") } }
        val d = runBlocking { QuickAddWidget().load(context) }!!
        assertEquals(WidgetInsights.NONE, d.insights); assertTrue(!d.revealed && !d.canToggle); assertTrue(d.monthSpend.contains("••••"))
    }

    @Test
    fun aMonthWithoutSpendingIsSaidToBeEmptyAndHasNoComparison() {
        runBlocking { container.write { it.templates.putSetting("widget_show_total", "1") } }
        val d = runBlocking { QuickAddWidget().load(context) }!!
        assertTrue(d.nothingYet); assertEquals("₹0.00", d.monthSpend); assertEquals(null, d.insights.delta); assertEquals(emptyList(), d.insights.categories)
    }

    // ---- colours ----

    @Test
    fun everyPairOfWidgetColoursThatTextIsDrawnOnReadsAtFourAndAHalfToOneInLightAndDark() {
        fun colors(night: Boolean): (String) -> Int {
            val conf = Configuration(context.resources.configuration)
            conf.uiMode = (conf.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
            val c = context.createConfigurationContext(conf)
            return { name -> c.getColor(c.resources.getIdentifier(name, "color", context.packageName)) and 0xFFFFFF }
        }
        // on Android 12+ these resources are the phone's Material You colours, which are designed to pair; the brand palette is what is checked here, wherever it is in use
        for (night in listOf(false, true)) {
            val c = colors(night)
            val pairs = listOf(
                "widget_text" to "widget_surface", "widget_muted" to "widget_surface", "widget_text" to "widget_container", "widget_muted" to "widget_container",
                "widget_on_accent" to "widget_accent", "widget_on_accent_container" to "widget_accent_container", "widget_positive" to "widget_container", "widget_negative" to "widget_container",
                "widget_positive" to "widget_surface", "widget_negative" to "widget_surface", "widget_text" to "widget_undo",
            )
            for ((fg, bg) in pairs) assertTrue(Appearances.contrast(c(fg), c(bg)) >= 4.5, "${if (night) "dark" else "light"}: $fg on $bg is ${Appearances.contrast(c(fg), c(bg))}")
        }
    }
}

package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.ExampleMonth
import app.expenses.core.domain.Guide
import app.expenses.core.storage.BackupFile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * UI slice S6, first run (U-10, owner decision D8): the welcome, the dimmed Example month under it, and the opt-in tour. The hard rule is that the example is only a drawing: while it is on the screen the
 * ledger, its totals, Search, a backup, the widgets, the budget and the notices know nothing of it.
 */
@RunWith(AndroidJUnit4::class)
class UiS6FirstRunTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    // a fresh install: the welcome is not put away, and the guide has never been seen
    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting(GuideSettings.SEEN, "") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String, ms: Long = 20_000) = compose.waitUntil(ms) { has(tag) }
    private fun gone(tag: String, ms: Long = 20_000) = compose.waitUntil(ms) { !has(tag) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun tap(tag: String) { waitFor(tag); runCatching { node(tag).performScrollTo() }; node(tag).performClick() }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun setting(key: String) = runBlocking { container.write { it.templates.setting(key) } }
    private fun entries() = runBlocking { container.write { it.db.schemaQueries.entryCount().executeAsOne() } }!!
    private fun onStop(n: Int, of: Int) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("guide_counter") and hasText("$n of $of", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun textsOf(n: SemanticsNode): List<String> =
        (n.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()) + n.children.flatMap { textsOf(it) }
    private fun describedBy(n: SemanticsNode): List<String> =
        (n.config.getOrNull(SemanticsProperties.ContentDescription) ?: emptyList()) + n.children.flatMap { describedBy(it) }

    @Test
    fun aFreshLedgerShowsTheWelcomeAndTheExampleWithNoMonthCardNoBudgetPromptAndNoTourOnItsOwn() {
        launch(); waitFor("welcome"); waitFor("example_month"); waitFor("example_ribbon"); waitFor("example_hide")
        for (tag in listOf("welcome_add", "welcome_import", "welcome_tour", "welcome_privacy", "welcome_backup", "welcome_skip")) assertTrue(has(tag), tag)
        node("welcome_skip").assertIsDisplayed() // the example is below the buttons: Skip is on the first screen
        assertTrue(compose.onAllNodes(hasText("Let's note your first expense"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodes(hasText("Stays on this phone. No account. Nothing is sent anywhere."), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertEquals(ExampleMonth.rows.size, ExampleMonth.rows.indices.count { has("example_row_$it") }, "every example row is drawn")
        for (tag in listOf("month_spend", "budget_summary", "home_hero", "budget_set_prompt", "safe_to_spend", "home_safe_to_spend_line", "hero_pace_line", "home_header", "home_hide_toggle")) assertFalse(has(tag), "$tag says nothing above the welcome")
        assertFalse(has("guide"), "no tour on a first install")
        compose.waitUntil(10_000) { setting(GuideSettings.SEEN) == Guide.VERSION.toString() } // recorded as seen, so a later update offers only what is new
        launch(); waitFor("welcome"); assertFalse(has("guide"))
    }

    @Test
    fun showMeAroundStartsTheFiveStopTourAndItsEndOffersTheCaptureSetup() {
        launch(); tap("welcome_tour"); waitFor("guide")
        val stops = Guide.shortPlan().steps
        assertEquals(5, stops.size)
        for ((i, step) in stops.withIndex()) {
            onStop(i + 1, of = 5)
            node("guide_title").assertTextContains(step.title, substring = true)
            if (i < stops.lastIndex) tap("guide_next") else tap("guide_done")
        }
        gone("guide")
        waitFor("setup_screen"); tap("setup_close")
        assertEquals(Guide.VERSION.toString(), setting(GuideSettings.SEEN))
        assertEquals(0L, entries())
    }

    @Test
    fun whileTheExampleIsOnScreenNothingOfItIsInTheLedgerItsTotalsSearchABackupTheWidgetsTheBudgetOrTheNotices() {
        launch(); waitFor("example_month"); waitFor("example_row_0")
        // the ledger and its totals
        assertEquals(0L, entries())
        runBlocking {
            container.write { ok ->
                assertFalse(ok.db.schemaQueries.entryCountEver().executeAsOne(), "no entry has ever existed")
                assertTrue(ok.store.spendByCategory(Currency.INR).isEmpty(), "spending by category is empty")
                assertTrue(ok.budgets.limits().isEmpty(), "no budget exists")
                assertTrue(ok.templates.list().isEmpty(), "no quick entry exists")
            }
        }
        // a backup, through the function the Data screen's share button uses
        val saved = runBlocking { DataTransfer(AndroidPlatform(context), container).backupForSharing(null) }
        val data = BackupFile.decode(saved.first).second
        assertTrue(data.entries.isEmpty() && data.templates.isEmpty() && data.budgets.isEmpty() && data.recurring.isEmpty(), "the backup holds no entries, quick entries, budgets or repeating items")
        assertEquals(0, saved.second.counts.entries)
        // the widgets
        val quick = runBlocking { app.expenses.widget.QuickAddWidget().load(context) }!!
        assertTrue(quick.buttons.isEmpty() && quick.nothingYet, "the quick add widget has nothing")
        assertNull(runBlocking { app.expenses.widget.TodayWidget().loadToday(context) }!!.perDayLine, "the Today widget has no budget line")
        // the notices: the check posts nothing by itself and says nothing of the example, and it is the same after the example is hidden
        fun notices() = runBlocking {
            container.write { ok ->
                val r = ok.notifications.check(kotlinx.datetime.LocalDate(2026, 6, 15), 12 * 60, kotlinx.datetime.TimeZone.UTC)
                assertTrue(r.autoPosted.isEmpty())
                r.notices.map { it.key to (it.title + " " + it.text) }
            }
        }!!
        val before = notices()
        for ((_, words) in before) for (m in ExampleMonth.merchants) assertFalse(words.contains(m), "a notice names the example merchant $m")
        // Search finds none of the example merchants
        tap("open_search"); waitFor("search_text")
        node("search_text").performTextInput(ExampleMonth.merchants.first())
        waitFor("search_none")
        tap("search_back")
        waitFor("example_month")
        assertEquals(0L, entries())
        tap("example_hide"); gone("example_month")
        assertEquals(before, notices(), "hiding the example changes nothing the notices see")
    }

    @Test
    fun hideExampleRemovesItKeepsTheWelcomeAndItStaysGoneAfterARestart() {
        launch(); waitFor("example_month")
        tap("example_hide"); gone("example_month"); waitFor("welcome")
        assertEquals("1", setting("example_hidden"))
        assertNull(setting("welcome_dismissed"), "hiding the example is not skipping the welcome")
        launch(); waitFor("welcome"); assertFalse(has("example_month"))
    }

    @Test
    fun theFirstRealEntryRemovesTheExampleAndTheLedgerHoldsExactlyThatEntry() {
        launch(); waitFor("example_month")
        tap("welcome_add"); waitFor("amount")
        node("amount").performTextInput("250"); tap("save")
        compose.waitUntil(10_000) { entries() == 1L }
        gone("example_month"); gone("welcome"); waitFor("month_spend")
        val minor = runBlocking { container.write { ok -> ok.db.schemaQueries.liveEntriesInInsertionOrder().executeAsList().map { it.amount_minor } } }!!
        assertEquals(listOf(25_000L), minor, "exactly the entry that was typed")
    }

    @Test
    fun tappingAGhostRowOpensAddFilledInAndTheFormSavesNothingByItself() {
        launch(); waitFor("example_row_0")
        val first = ExampleMonth.rows.first()
        tap("example_row_0"); waitFor("amount")
        node("amount").assertTextContains("30", substring = true)
        node("note").assertTextContains(first.merchant, substring = true)
        compose.waitForIdle()
        assertEquals(0L, entries(), "opening the form saved nothing")
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitFor("example_month")
        assertEquals(0L, entries(), "leaving the form saved nothing either")
    }

    @Test
    fun theRibbonAndHideExampleAreTheOnlyExampleWordsAScreenReaderGetsAndTheDrawingIsCleared() {
        launch(); waitFor("example_ribbon")
        // the merged tree is what a screen reader walks: the ghost rows and the ghost month card are not in it
        assertFalse(compose.onAllNodes(hasTestTag("example_row_0")).fetchSemanticsNodes().isNotEmpty(), "a ghost row is not exposed")
        val block = compose.onNodeWithTag("example_month").fetchSemanticsNode()
        assertEquals(setOf("Example · yours replaces this", "Hide example"), textsOf(block).toSet(), "only the ribbon and the button have words")
        assertTrue(describedBy(block).contains("Example month, not your data"), "the ribbon says it is not the person's data")
    }

    @Test
    fun withHideAmountsOnTheExampleShowsNoFigure() {
        runBlocking { container.write { it.templates.putSetting("hide_amounts", "1") } }
        launch(); waitFor("example_month")
        val words = textsOf(compose.onNodeWithTag("example_month", useUnmergedTree = true).fetchSemanticsNode())
        assertTrue(words.isNotEmpty() && words.none { w -> w.any { it.isDigit() } }, "no digit in the example while amounts are hidden: $words")
    }
}

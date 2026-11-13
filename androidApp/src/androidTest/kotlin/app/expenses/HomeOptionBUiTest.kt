package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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
import app.expenses.core.domain.ledger.Income
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Home Option B (Slice 112, step 2): the short month card with the change chip, Budget left and Safe to spend side by side (each saying where its a-day figure comes from), the trip card's progress bar,
 * smaller account tiles with a card's due words, whole-unit headline figures with the exact amount for a screen reader, Hide amounts hiding the bars, the graph and the chip, and where the first entry starts.
 * Base fixes are in HomeBaseUiTest.  (Old doc follows.) Home's base fixes (Slice 112, step 1), shared by every layout: every nudge has one look and ONE close button, the Add button sits in the middle of the bottom bar instead of floating
 * over the page, the day headings carry what was spent that day, and the cut-down layout at large text (200% on 360 and 393 dp wide): nothing wider than the window, the accounts
 * keep their balances, the entry rows keep their titles and the "No category" card is not a one-word column. The display is changed through `settings` and `wm` and put back after each test. Invented data.
 */
@RunWith(AndroidJUnit4::class)
class HomeOptionBUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val inr = Currency.INR
    private val ua = instrumentation.uiAutomation

    private fun shell(cmd: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(ua.executeShellCommand(cmd)).use { it.readBytes() }
        if (cmd.startsWith("wm ")) Thread.sleep(1_500) else Thread.sleep(2_000)
    }

    @Before fun fresh() {
        shell("settings put system font_scale 1.0")
        androidx.work.WorkManager.getInstance(context).cancelAllWork().result.get()
        runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } }
    }
    @After fun cleanUp() {
        compose.waitForIdle(); scenario?.close()
        shell("settings put system font_scale 1.0"); shell("wm size reset"); shell("wm density reset"); SystemDialogs.dismissAppErrorDialog()
        runBlocking { container.startFresh() }
    }

    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(20_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(20_000) { !has(tag) }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick() }
    private fun <T> db(block: (AppContainer.Opened.Ok) -> T): T = runBlocking { container.write(block) }!!
    private fun density() = context.resources.displayMetrics.density
    private fun at(offset: Long) = LocalDate.now().plusDays(offset).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun epochDay(offset: Long) = LocalDate.now().plusDays(offset).toEpochDay()
    private fun textOf(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.let { c -> c.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty() }
    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.fetchSemanticsNode().boundsInRoot
    /** Where a touch counts: a 40 dp close button is given 48 dp of touch area. */
    private fun touch(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.fetchSemanticsNode().touchBoundsInRoot
    private fun mergedText(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = false).fetchSemanticsNode().config.let { c -> c.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty() }
    private fun entry(id: String, offset: Long, merchant: String, minor: Long, category: String? = "Food", account: String = "cash") = db { ok ->
        val splits = category?.let { listOf(CategorySplit(CategoryId(it), Money(minor, inr))) } ?: emptyList()
        ok.store.save(Expense(EntryId(id), at(offset), AccountId(account), Money(minor, inr), splits), EntryDetails(merchant = merchant))
    }

    /** The node is inside the window sideways, and (for a control) at least 48 dp high and wide. */
    private fun fits(tag: String, control: Boolean = false) {
        waitFor(tag)
        val b = bounds(tag)
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(b.left >= root.left - 1f && b.right <= root.right + 1f, "$tag is wider than the window: $b in $root")
        if (control) { val t = touch(tag); assertTrue(t.height >= 47f * density() && t.width >= 47f * density(), "$tag has a touch area of ${t.width / density()} x ${t.height / density()} dp") }
    }


    private fun seedTypical() {
        db { ok ->
            ok.store.saveAccount(app.expenses.core.domain.ledger.Account(AccountId("hdfc"), "HDFC Regalia", app.expenses.core.domain.ledger.AccountType.CREDIT_CARD, inr))
            ok.cards.save(app.expenses.core.domain.CardTerms("hdfc", statementDay = 1, dueDay = 5)); ok.cards.setCreditLimit("hdfc", 30_000_000)
            ok.budgets.setLimit(null, Money(6_000_000, inr))
            ok.templates.putSetting("safe_to_spend_on_home", "1")
            ok.templates.save(app.expenses.core.domain.Template("t1", "Chai", Money(2_000, inr), CategoryId("Food"), AccountId("cash"), null))
        }
        entry("a", 0, "BookMyShow", 41_110, "Fun", "bank"); entry("b", 0, "Netflix", 70_150, "Bills", "bank"); entry("c", -1, "Starbucks", 43_650, "Food", "hdfc")
        entry("old", -40, "Last month shop", 100_000, "Food", "bank")
        db { ok -> ok.store.save(Income(EntryId("sal"), at(-2), AccountId("bank"), Money(18_500_000, inr), CategoryId("Other")), EntryDetails(merchant = "Employer")) }
    }

    /** A statement of the card that closed 9 days ago with a purchase in it, due in 6 days: the small tile then says "owed · due in 6 d" (day numbers are kept to 28, as the card form keeps them). */
    private fun cardDueSoon() {
        db { ok -> ok.cards.save(app.expenses.core.domain.CardTerms("hdfc", statementDay = LocalDate.now().plusDays(-9).dayOfMonth.coerceAtMost(28), dueDay = LocalDate.now().plusDays(6).dayOfMonth.coerceAtMost(28))) }
        entry("billed", -12, "Billed card spend", 50_000, "Food", "hdfc")
    }

    private fun waitTiles() { waitFor("budget_summary"); waitFor("home_safe_to_spend_line"); Thread.sleep(2_000) }

    @Test
    fun budgetLeftAndSafeToSpendSitSideBySideAndSayWhereTheirADayFiguresComeFrom() {
        seedTypical(); launch(); waitTiles()
        val b = bounds("budget_summary"); val s = bounds("home_safe_to_spend_line")
        assertTrue(b.right <= s.left + 1f && kotlin.math.abs(b.top - s.top) < 2f && kotlin.math.abs(b.height - s.height) < 2f, "two tiles in one row, the same height: $b $s")
        assertTrue(textOf("budget_per_day").endsWith("a day from your budget"), textOf("budget_per_day"))
        assertTrue(textOf("home_safe_to_spend_line_per_day").endsWith("from your balances"), textOf("home_safe_to_spend_line_per_day"))
        // the exact amounts are what is read (a screen reader, these tests); the figures are drawn in whole units
        assertTrue(Regex("""₹[\d,]+\.\d\d left of ₹60,000\.00""").matches(textOf("budget_overall_note")), textOf("budget_overall_note"))
        assertTrue(Regex("""₹[\d,]+\.\d\d safe to spend.*""").matches(textOf("home_safe_to_spend_line_text")), textOf("home_safe_to_spend_line_text"))
        tap("home_safe_to_spend_line_text"); waitFor("safe_to_spend_dialog"); tap("safe_to_spend_close")
        tap("budget_summary"); waitFor("budget_row_overall")
    }

    @Test
    fun theMonthCardHasTheChipTheSentenceAndTheGraphAndTheExactFigureForAScreenReader() {
        seedTypical(); launch(); waitFor("month_spend"); Thread.sleep(2_000)
        assertEquals("₹1,549.10", textOf("month_spend"), "the exact month figure is what is read")
        waitFor("month_compare_pill"); assertTrue(textOf("month_compare_pill").startsWith("▲ +") || textOf("month_compare_pill").startsWith("▼ −"), textOf("month_compare_pill"))
        assertTrue(textOf("month_compare").contains("last month so far") && !textOf("month_compare").contains("▲"), textOf("month_compare"))
        assertTrue(has("home_spark"), "the small graph is there at normal text")
        assertTrue(bounds("month_spend").top < bounds("month_compare_pill").bottom + 60f * density(), "the chip is on the card's first rows, beside the label")
    }

    @Test
    fun hideAmountsAlsoHidesTheBarsTheGraphAndTheChip() {
        seedTypical(); db { ok -> ok.trips.save(app.expenses.core.domain.Trip("goa", "Goa trip", "Trip: Goa", epochDay(-1), epochDay(4), true, 300_000)) }
        db { it.templates.putSetting("hide_amounts", "1") }
        launch(); waitTiles(); waitFor("home_trip_card")
        assertFalse(has("home_spark"), "the graph's shape shows the figures")
        assertFalse(has("month_compare_pill"), "the chip's percent too")
        assertFalse(has("home_trip_progress"), "the trip's bar")
        assertFalse(has("home_trip_budget"))
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo), useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "no bar of the budget either")
        assertFalse(textOf("budget_overall_note").contains("60,000"), "the budget's figures are masked: ${textOf("budget_overall_note")}")
        assertFalse(textOf("home_safe_to_spend_line_text").contains(Regex("""\d{3}""")), textOf("home_safe_to_spend_line_text"))
    }

    @Test
    fun theTripCardHasAProgressBarAndSaysHowMuchOfTheBudgetIsSpent() {
        seedTypical()
        db { ok -> ok.trips.save(app.expenses.core.domain.Trip("goa", "Goa trip", "Trip: Goa", epochDay(-2), epochDay(3), true, 300_000)) }
        db { ok -> ok.store.save(Expense(EntryId("t1"), at(0), AccountId("bank"), Money(900_000, inr)), EntryDetails(merchant = "Hotel", tripTag = true)) }
        launch(); waitFor("home_trip_card"); waitFor("home_trip_progress"); waitFor("home_trip_budget")
        // 6 days of 3,000 = 18,000; 9,000 spent: 50%
        assertEquals("50% of ₹18,000.00 trip budget", textOf("home_trip_budget"))
        assertTrue(textOf("home_trip_line").contains("day 3 of 6"), textOf("home_trip_line"))
        assertFalse(has("inbox_banner")); fits("home_trip_card")
    }

    @Test
    fun accountTilesAreSmallAndTheCardTileSaysOwedAndWhenItIsDue() {
        seedTypical(); cardDueSoon(); launch(); waitFor("balance_chip_HDFC Regalia"); Thread.sleep(2_000)
        val t = bounds("balance_chip_HDFC Regalia")
        assertTrue(t.width <= 120f * density() && t.height <= 80f * density(), "a small tile: ${t.width / density()} x ${t.height / density()} dp")
        assertTrue(bounds("balance_chip_Bank").height <= 80f * density())
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("owed", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "the card says owed")
        assertTrue(textOf("balance_chip_HDFC Regalia").contains("due in"), "the tile reads out when the card is due too: ${textOf("balance_chip_HDFC Regalia")}")
        tap("balance_chip_Bank"); waitFor("account_filter_note")
    }

    /** The tile's three lines (name, balance, "owed · due in 6 d") must be drawn whole at the text sizes below the cut-down layout (150%): the owed line's text layout must not overflow the height it was given (a clamped node has in-bounds bounds even when its text is cut, as seen at 130% on a screenshot). */
    private fun tileKeepsItsLinesInsideAtFontScale(scale: String) {
        seedTypical(); cardDueSoon(); shell("settings put system font_scale $scale")
        launch(); waitFor("balance_chip_HDFC Regalia"); Thread.sleep(2_000)
        val tile = bounds("balance_chip_HDFC Regalia")
        val node = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher("the owed line") { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.startsWith("owed") } }, useUnmergedTree = true).fetchSemanticsNodes()
            .firstOrNull { it.boundsInRoot.left >= tile.left - 1f && it.boundsInRoot.right <= tile.right + 1f && it.boundsInRoot.top >= tile.top - 1f && it.boundsInRoot.top <= tile.bottom }
        assertNotNull(node, "the card tile draws its owed line")
        val layouts = ArrayList<androidx.compose.ui.text.TextLayoutResult>()
        node.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        assertTrue(layouts.isNotEmpty() && node.size.height >= layouts[0].multiParagraph.height.toInt() - 1, "at font $scale the owed line is cut: node ${node.size.height} px high, text ${layouts.firstOrNull()?.multiParagraph?.height} px, tile ${tile.height / density()} dp")
        val bar = 3f * density()
        assertTrue(node.boundsInRoot.bottom <= tile.bottom - bar + 1f, "at font $scale the owed line ends at ${(node.boundsInRoot.bottom - tile.top) / density()} dp of a ${tile.height / density()} dp tile (the limit bar takes the last 3 dp)")
        assertTrue(bounds("balance_chip_Bank").height <= tile.height + 1f)
    }

    @Test fun theCardTileKeepsItsLinesInsideAt130Percent() = tileKeepsItsLinesInsideAtFontScale("1.3")
    @Test fun theCardTileKeepsItsLinesInsideAt149Percent() = tileKeepsItsLinesInsideAtFontScale("1.49")

    @Test
    fun theFirstEntryStartsHighOnAPhoneWithATypicalMonth() {
        seedTypical(); launch(); waitTiles(); waitFor("day_header_${epochDay(0)}")
        val list = bounds("list"); val h = bounds("day_header_${epochDay(0)}")
        val topDp = (h.top - list.top) / density()
        android.util.Log.i("HomeOptionB", "first day heading at $topDp dp of ${(list.bottom - list.top) / density()}")
        assertTrue(topDp <= 600f, "the first day heading starts at $topDp dp (it was about 680 dp before Option B)")
    }

    @Test
    fun atLargeTextTheTwoTilesStackAndTheGraphIsGone() {
        seedTypical(); shell("settings put system font_scale 2.0")
        launch(); waitTiles()
        val b = bounds("budget_summary"); val s = bounds("home_safe_to_spend_line")
        assertTrue(b.bottom <= s.top + 1f && b.left == s.left, "stacked, full width: $b $s")
        assertFalse(has("home_spark")); fits("budget_summary"); fits("home_safe_to_spend_line")
    }

    @Test
    fun inATwoPaneListPaneTheGraphGoesSoEntriesStartSooner() {
        seedTypical()
        app.expenses.ui.WideLayoutTestHook.widthDp = 1000; app.expenses.ui.WideLayoutTestHook.heightDp = 700
        try { launch(); waitFor("wide_list_pane"); waitFor("month_spend"); assertFalse(has("home_spark")) } finally { app.expenses.ui.WideLayoutTestHook.widthDp = null; app.expenses.ui.WideLayoutTestHook.heightDp = null }
    }
}

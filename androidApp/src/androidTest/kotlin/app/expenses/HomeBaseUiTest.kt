package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
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
 * Home's base fixes (Slice 112, step 1), shared by every layout: every nudge has one look and ONE close button, the Add button sits in the middle of the bottom bar instead of floating
 * over the page, the day headings carry what was spent that day, and the cut-down layout at large text (200% on 360 and 393 dp wide): nothing wider than the window, the accounts
 * keep their balances, the entry rows keep their titles and the "No category" card is not a one-word column. The display is changed through `settings` and `wm` and put back after each test. Invented data.
 */
@RunWith(AndroidJUnit4::class)
class HomeBaseUiTest {
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

    // ---- one look, one close button ---------------------------------------------------------------------------------------------------------------------------

    /** The nudge's card and its tag for the close button, in the order of docs/HOME_NUDGES.md; each is seeded alone. */
    private val closers = mapOf(
        "inbox_banner" to "nudge_notnow_inbox", "categorise_nudge" to "categorise_nudge_later", "setup_nudge" to "nudge_notnow_setup", "first_week_card" to "first_week_dismiss",
    )

    private fun seedNudge(card: String) {
        when (card) {
            "inbox_banner" -> runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", "Rs.1,250.00 debited from A/c XX1234 on 05-10-26 to VPA shop@okhdfc (UPI Ref 628374659283). Not you? Call 18002586161", System.currentTimeMillis() - 5_000) }
            "categorise_nudge" -> for (i in 0 until 4) entry("u$i", -i.toLong(), "Plain $i", 10_000L + i, category = null)
            "setup_nudge" -> db { it.templates.putSetting("capture_setup", "OFFERED:PERMISSION") }
            "first_week_card" -> { entry("fw", 0, "Tea", 10_000); db { it.templates.putSetting("first_week_dismissed", "0"); it.templates.putSetting("first_week_started", (System.currentTimeMillis() - 86_400_000L).toString()) } }
        }
    }

    @Test
    fun everyNudgeIsOneCardWithOneCloseButtonOfAtLeast48dpAndTheCloseTakesItAway() {
        for ((card, close) in closers) {
            runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } }
            seedNudge(card)
            launch(); waitFor(card); waitFor(close)
            // ONE close button: a button with a close description inside the card, and no other control that puts it away (no "Not now" / "Hide" text buttons)
            val closeB = touch(close)
            val inside = compose.onNodeWithTag(card, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(closeB.left >= inside.left - 1f && closeB.right <= inside.right + 1f && closeB.top >= inside.top - 1f && closeB.bottom <= inside.bottom + 1f, "$close is inside $card: $closeB in $inside")
            assertTrue(closeB.width >= 47f * density() && closeB.height >= 47f * density(), "$close is a 48 dp target: ${closeB.width / density()} x ${closeB.height / density()}")
            val texts = compose.onAllNodes(androidx.compose.ui.test.hasText("Not now", substring = false).or(androidx.compose.ui.test.hasText("Hide", substring = false)), useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue(texts.isEmpty(), "$card: no second way out written as a text button")
            // every nudge has the same corner and tint: its card is as wide as the page minus the 16 dp margins
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            assertTrue(kotlin.math.abs(inside.left - 16f * density()) < 2f && kotlin.math.abs(root.right - inside.right - 16f * density()) < 2f, "$card has the same margins as the others: $inside")
            tap(close)
            waitGone(card)
        }
    }

    @Test
    fun theSetupCardsCloseIsADayAndTheBarThatFollowsOffersHidingItForGood() {
        db { it.templates.putSetting("capture_setup", "OFFERED:PERMISSION") }
        launch(); waitFor("setup_nudge"); tap("nudge_notnow_setup"); waitGone("setup_nudge")
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Hide for good")).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(db { it.templates.setting("nudge_snoozed:setup") }.orEmpty().endsWith("|1"), "the close put it off for a day")
        assertEquals("OFFERED:PERMISSION", db { it.templates.setting("capture_setup") }, "and did not hide it for good")
    }

    // ---- Add in the bar -----------------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun addSitsInTheMiddleOfTheBottomBarAndCoversNothingOfThePage() {
        entry("a", 0, "Tea", 4_500); entry("b", -1, "Metro", 50_000)
        launch(); waitFor("nav_home"); waitFor("list")
        val add = bounds("add"); val home = bounds("nav_home"); val more = bounds("more_menu"); val search = bounds("open_search"); val list = bounds("list")
        assertTrue(touch("add").width >= 47f * density() && touch("add").height >= 47f * density(), "Add is a 48 dp target: ${touch("add").width / density()} x ${touch("add").height / density()}")
        assertTrue(kotlin.math.abs(add.top - home.top) < 2f * density(), "Add is in the same bar as Home: ${add.top} against ${home.top}")
        assertTrue(add.left > search.right - 1f && add.right < more.left + 1f, "Add is between Search and More, not at the edge")
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs((add.left + add.right) / 2f - (root.left + root.right) / 2f) < 4f * density(), "Add is in the middle of the bar")
        assertTrue(list.bottom <= add.top + 1f, "the list ends where the bar begins, so Add floats over no row: list ${list.bottom}, Add ${add.top}")
        tap("add"); waitFor("amount")
    }

    @Test
    fun addIsInTheBarOnSearchAndReportsToo() {
        // the box takes the focus and the keyboard comes up on an emulator boot that has one; the bar is hidden while it is up, so Done closes it first
        launch(); tap("open_search"); waitFor("search_text"); runCatching { compose.onNodeWithTag("search_text", useUnmergedTree = true).performImeAction() }; tap("bar_add"); waitFor("amount")
        tap("cancel"); waitFor("add")
        tap("open_reports"); waitFor("bar_add"); tap("bar_add"); waitFor("amount")
    }

    // ---- day totals ---------------------------------------------------------------------------------------------------------------------------------------------

    @Test
    fun theDayHeadingsCarryWhatWasSpentThatDayExpensesOnlyAndMaskedWhileAmountsAreHidden() {
        entry("a", 0, "Tea", 4_500); entry("b", 0, "Lunch", 12_000); entry("c", -1, "Metro", 50_000)
        db { ok -> ok.store.save(Income(EntryId("pay"), at(-2), AccountId("bank"), Money(5_000_000, inr), CategoryId("Other")), EntryDetails(merchant = "Employer")) }
        launch(); waitFor("day_total_${epochDay(0)}")
        assertEquals("-₹165.00", textOf("day_total_${epochDay(0)}"))
        assertEquals("-₹500.00", textOf("day_total_${epochDay(-1)}"))
        assertFalse(has("day_total_${epochDay(-2)}"), "a day of income only has no total")
        // the row no longer repeats the day: its second line is the account and the category
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("Cash · Food"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "the row says the account and the category")
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText(LocalDate.now().year.toString(), substring = true), useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "and does not repeat the date")
        db { it.templates.putSetting("hide_amounts", "1") }
        launch(); waitFor("day_total_${epochDay(0)}")
        assertFalse(textOf("day_total_${epochDay(0)}").any { it.isDigit() }, "Hide amounts masks the day total: ${textOf("day_total_${epochDay(0)}")}")
    }

    // ---- large text -----------------------------------------------------------------------------------------------------------------------------------------------

    private fun seedBusy() {
        db { ok ->
            ok.store.saveAccount(app.expenses.core.domain.ledger.Account(AccountId("hdfc"), "HDFC Regalia Credit Card", app.expenses.core.domain.ledger.AccountType.CREDIT_CARD, inr))
            ok.budgets.setLimit(null, Money(6_000_000, inr))
        }
        entry("a", 0, "BookMyShow", 41_100, "Fun", "bank"); entry("b", 0, "Netflix", 70_100, "Bills", "bank"); entry("c", -1, "Starbucks coffee and a long name", 43_600, "Food", "hdfc")
        for (i in 0 until 4) entry("u$i", -3L - i, "Plain $i", 10_000L + i, category = null)
    }

    private fun largeTextChecks() {
        launch(); waitFor("month_spend"); waitFor("balances"); Thread.sleep(2_500) // the slow figures (balances, the no-category count) arrive after the page
        // the month card: the figure fits and nothing is wider than the window
        fits("month_spend"); fits("balances")
        // accounts are one row that opens into rows, each with its balance, none off the edge and each a 48 dp target
        tap("balances_toggle"); waitFor("balance_chip_Cash")
        for (n in listOf("Cash", "Bank", "HDFC Regalia Credit Card")) { fits("balance_chip_$n", control = true) }
        assertTrue(textOf("balance_chip_HDFC Regalia Credit Card").contains("owed"), "the credit card tile keeps what is owed: ${textOf("balance_chip_HDFC Regalia Credit Card")}")
        // the nudge: words wide enough to read, the button and the close at least 48 dp, and not a column taller than a screen
        waitFor("categorise_nudge"); fits("categorise_nudge"); fits("categorise_nudge_review", control = true); fits("categorise_nudge_later", control = true)
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val title = bounds("categorise_nudge_text")
        assertTrue(title.width >= root.width * 0.45f, "the sentence has room (not a one-word column): ${title.width / density()} dp of ${root.width / density()}")
        assertTrue(bounds("categorise_nudge").height <= 330f * density(), "the card is ${bounds("categorise_nudge").height / density()} dp")
        // entries: the title is not cut to a few letters
        compose.onNodeWithTag("list", useUnmergedTree = true).performScrollToNode(hasTestTag("row_BookMyShow"))
        fits("row_BookMyShow"); fits("amount_BookMyShow")
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("BookMyShow"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "the title is whole")
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("Bank · Fun"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "account and category are on the row, not only the date")
    }

    @Test
    fun at200PercentTextOn393dpNothingRunsOffTheEdgeAndTheCardsKeepTheirWords() {
        seedBusy()
        shell("settings put system font_scale 2.0")
        largeTextChecks()
    }

    @Test
    fun at200PercentTextOn360dpNothingRunsOffTheEdgeAndTheCardsKeepTheirWords() {
        seedBusy()
        shell("settings put system font_scale 2.0"); shell("wm density 480")
        largeTextChecks()
    }

    @Test
    fun theBottomBarKeepsItsLabelsApartAtTwiceTheTextSize() {
        shell("settings put system font_scale 2.0")
        launch(); waitFor("nav_home")
        val tabs = listOf("nav_home", "open_search", "add", "open_reports", "more_menu").map { bounds(it) }
        for ((a, b) in tabs.zipWithNext()) assertTrue(a.right <= b.left + 1f, "the places of the bar do not overlap: $a and $b")
        assertNotNull(tabs.firstOrNull())
        for (b in tabs) assertTrue(b.height >= 47f * density(), "each place is a 48 dp target")
    }
}

package app.expenses

import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick

import android.content.Intent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import kotlin.test.assertTrue

/**
 * UI S0 on a screen: the Filters panel and the Add form move (they used to cut in and out), the keyboard leaves with the Add form, a bulk bar action has an icon, and the four cards that broke at 200% text
 * (Reports amounts, Add's attach buttons, More's backup nudge, Search's chips) on a 393 and a 360 dp wide window. The display is changed through `settings` and `wm` and put back after each test. Invented data.
 */
@RunWith(AndroidJUnit4::class)
class UiS0LayoutTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val ua = instrumentation.uiAutomation
    private val inr = Currency.INR

    private fun shell(cmd: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(ua.executeShellCommand(cmd)).use { it.readBytes() }
        if (cmd.startsWith("wm ")) Thread.sleep(1_500) else Thread.sleep(2_000)
    }

    @Before fun fresh() {
        shell("settings put system font_scale 1.0")
        androidx.work.WorkManager.getInstance(context).cancelAllWork().result.get()
        runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1"); it.templates.putSetting("first_week_dismissed", "1") } }
    }
    @After fun cleanUp() {
        compose.waitForIdle(); scenario?.close()
        shell("settings put system font_scale 1.0"); shell("wm size reset"); shell("wm density reset"); SystemDialogs.dismissAppErrorDialog()
        runBlocking { container.startFresh() }
    }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(20_000) { has(tag) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun tap(tag: String) { waitFor(tag); runCatching { node(tag).performScrollTo() }; node(tag).performClick() }
    private fun bounds(tag: String): Rect = node(tag).also { runCatching { it.performScrollTo() } }.fetchSemanticsNode().boundsInRoot
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun dp(v: Float) = v * context.resources.displayMetrics.density
    private fun screenWidth() = context.resources.displayMetrics.widthPixels.toFloat()
    private fun spend(id: String, rupees: Long, merchant: String) = runBlocking {
        container.write { ok -> ok.store.save(Expense(EntryId(id), System.currentTimeMillis(), AccountId("cash"), Money(rupees * 100, inr), listOf(CategorySplit(CategoryId("Food"), Money(rupees * 100, inr)))), EntryDetails(merchant = merchant)) }
    }

    // ---- motion ----

    @Test
    fun theFiltersPanelSlidesUpWhileItFadesInAndLeavesInsteadOfCuttingInAndOut() {
        spend("a", 100, "Cafe"); launch()
        tap("open_search"); waitFor("search_filters_button")
        compose.mainClock.autoAdvance = false
        node("search_filters_button").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.waitUntil(5_000) { has("filters_panel") }
        var lowest = 0f
        repeat(4) { lowest = maxOf(lowest, node("filters_panel").fetchSemanticsNode().boundsInRoot.top); compose.mainClock.advanceTimeByFrame() }
        compose.mainClock.advanceTimeBy(600)
        val settled = node("filters_panel").fetchSemanticsNode().boundsInRoot.top
        assertTrue(lowest > settled + dp(8f), "the panel is still rising in the first frames: top $lowest, settled $settled")
        // closing: it is still there a frame later (it used to vanish at once) and gone once the exit has run
        node("filters_close").performClick()
        compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeByFrame()
        assertTrue(has("filters_panel"), "the panel is still drawn while it leaves")
        compose.mainClock.advanceTimeBy(600)
        compose.mainClock.autoAdvance = true
        compose.waitUntil(5_000) { !has("filters_panel") }
    }

    private fun imeVisible(): Boolean { var v = false; scenario!!.onActivity { a -> v = ViewCompat.getRootWindowInsets(a.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }; return v }

    @Test
    fun theKeyboardGoesDownWithTheAddFormNotAfterIt() {
        launch(); tap("add"); waitFor("amount")
        compose.waitUntil(15_000) { imeVisible() }
        val tapped = System.currentTimeMillis()
        node("cancel").performClick()
        compose.waitUntil(5_000) { !imeVisible() }
        val took = System.currentTimeMillis() - tapped
        // the form takes at most 380 ms to leave; the keyboard used to wait for that to end (and its own 250 ms after it): it now starts at once. The API 26 emulator draws and animates the keyboard far more slowly
        // (1.7 s seen), so there the check is only that it goes at all in good time; the strict figure is for Android 11 and later
        val limit = if (android.os.Build.VERSION.SDK_INT >= 30) 750 else 2_500
        assertTrue(took < limit, "the keyboard was away $took ms after Cancel (limit $limit)")
        waitFor("add")
    }

    // ---- the bulk bar ----

    @Test
    fun everyActionOnTheBulkBarHasAnIconAndKeepsItsWord() {
        spend("a", 100, "Cafe"); spend("b", 200, "Shop"); launch()
        waitFor("row_Cafe")
        node("row_Cafe").performTouchInput { longClick() }
        waitFor("bulk_bar")
        // each button holds an icon beside its word: it is wider than the word alone by the icon, its gap and the padding
        for (tag in listOf("bulk_category", "bulk_delete", "bulk_more")) assertTrue(hasIconIn(tag), "$tag has an icon beside its word")
    }

    private fun hasIconIn(tag: String): Boolean {
        val n = node(tag).fetchSemanticsNode()
        // the label is a Row of an Icon (no semantics of its own: contentDescription null) and a Text; the button is wider than the word alone
        val word = n.children.flatMap { it.children + it }.firstOrNull { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text) != null }
        val textWidth = word?.boundsInRoot?.width ?: return false
        return n.boundsInRoot.width >= textWidth + dp(8f + 8f + 18f + 6f) - 2f
    }

    // ---- 200% text, 393 and 360 dp ----

    private fun twice(at360: Boolean) { shell("settings put system font_scale 2.0"); if (at360) shell("wm density 480") }

    private fun reportsAmountsStayOnOneLine() {
        runBlocking { container.write { ok -> ok.store.save(Income(EntryId("inc"), System.currentTimeMillis(), AccountId("bank"), Money(18_500_000, inr)), EntryDetails(merchant = "Salary")) } }
        spend("a", 1234, "Cafe"); launch()
        tap("open_reports"); waitFor("report_income")
        for (tag in listOf("report_spent", "report_income", "report_net")) {
            val b = bounds(tag)
            assertTrue(b.right <= screenWidth() + 1f && b.left >= -1f, "$tag is inside the window: $b")
            assertTrue(b.height < dp(75f), "$tag is on one line, not broken in the middle of the number: height ${b.height / context.resources.displayMetrics.density} dp")
        }
    }

    @Test fun reportsAmountsOn393dpAt200Percent() { twice(false); reportsAmountsStayOnOneLine() }
    @Test fun reportsAmountsOn360dpAt200Percent() { twice(true); reportsAmountsStayOnOneLine() }

    private fun attachButtonsAreWholeButtons() {
        spend("a", 100, "Cafe"); launch(); tap("row_Cafe"); waitFor("attachment_add")
        val b = bounds("attachment_add")
        assertTrue(b.right <= screenWidth() + 1f, "the button is inside the window: $b")
        assertTrue(b.height < dp(110f), "'Attach a photo or file' is a button, not a tall oval: ${b.height / context.resources.displayMetrics.density} dp high")
        assertTrue(b.width > dp(150f), "and wide enough to read: ${b.width / context.resources.displayMetrics.density} dp")
    }

    @Test fun attachButtonOn393dpAt200Percent() { twice(false); attachButtonsAreWholeButtons() }
    @Test fun attachButtonOn360dpAt200Percent() { twice(true); attachButtonsAreWholeButtons() }

    private fun backupNudgeKeepsItsWords() {
        runBlocking { container.write { ok -> repeat(12) { i -> ok.store.save(Expense(EntryId("e$i"), System.currentTimeMillis() - i * 1000L, AccountId("cash"), Money(10_000L + i, inr)), EntryDetails(merchant = "Shop $i")) } } }
        launch(); waitFor("more_badge"); compose.openMore(); waitFor("backup_nudge")
        val card = bounds("backup_nudge"); val back = bounds("nudge_backup"); val later = bounds("nudge_later")
        assertTrue(card.right <= screenWidth() + 1f && back.right <= card.right + 1f && later.right <= card.right + 1f, "the buttons are inside the card")
        // under the words, not beside them: the words keep the card's width (they were one word per line)
        assertTrue(back.top >= card.top + dp(40f), "'Back up now' is below the title")
        assertTrue(card.height < dp(260f), "the card is not a tall column of broken words: ${card.height / context.resources.displayMetrics.density} dp")
    }

    @Test fun backupNudgeOn393dpAt200Percent() { twice(false); backupNudgeKeepsItsWords() }
    @Test fun backupNudgeOn360dpAt200Percent() { twice(true); backupNudgeKeepsItsWords() }

    private fun searchChipsWrapAndSummaryStaysWhole() {
        spend("a", 100, "Swiggy"); launch(); tap("open_search"); waitFor("search_text")
        node("search_text").performTextInput("Swiggy"); waitFor("search_count")
        val strip = bounds("search_active_chips")
        for (tag in listOf("search_range_THIS_PERIOD", "search_kind_expense", "search_kind_income")) {
            val b = node(tag).fetchSemanticsNode().boundsInRoot
            assertTrue(b.right <= screenWidth() + 1f, "$tag is not cut by the edge of the window: $b")
        }
        assertTrue(strip.right <= screenWidth() + 1f)
    }

    @Test fun searchChipsOn393dpAt200Percent() { twice(false); searchChipsWrapAndSummaryStaysWhole() }
    @Test fun searchChipsOn360dpAt200Percent() { twice(true); searchChipsWrapAndSummaryStaysWhole() }
}

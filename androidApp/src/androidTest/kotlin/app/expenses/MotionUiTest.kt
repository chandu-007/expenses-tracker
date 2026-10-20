package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.BillStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The motion and finishing touches: the number pad, the amount in words, the 14-day line, Reduce motion and the paid-bill check. */
@RunWith(AndroidJUnit4::class)
class MotionUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun gone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun tap(tag: String) { waitFor(tag); runCatching { node(tag).performScrollTo() }; node(tag).performClick() }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun spend(id: String, minor: Long, daysAgo: Long = 0) = runBlocking { container.write { it.store.save(Expense(EntryId(id), System.currentTimeMillis() - daysAgo * 86_400_000L - 3_600_000, AccountId("cash"), Money(minor, Currency.INR))) } }

    @Test
    fun theNumberPadIsOffByDefaultTypesIntoTheSameAmountFieldAndTheKeyboardStillWorks() {
        launch(); tap("add"); waitFor("amount")
        assertTrue(!has("number_pad"), "the system keyboard is the default")
        tap("amount_pad_toggle"); waitFor("number_pad")
        for (k in listOf("pad_1", "pad_2", "pad_dot", "pad_5")) tap(k)
        node("amount").assertTextContains("12.5", substring = true)
        tap("pad_back"); node("amount").assertTextContains("12.", substring = true)
        tap("amount_pad_toggle"); gone("number_pad")
        node("amount").performTextClearance(); node("amount").performTextInput("99"); node("amount").assertTextContains("99", substring = true)
    }

    @Test
    fun theAmountIsReadInWordsInTheIndianSystemFromOneThousandAndNotBefore() {
        launch(); tap("add"); waitFor("amount")
        node("amount").performTextInput("999"); assertTrue(!has("amount_words"))
        node("amount").performTextClearance(); node("amount").performTextInput("124500.50")
        waitFor("amount_words")
        node("amount_words").assertTextEquals("one lakh twenty-four thousand five hundred rupees and fifty paise")
    }

    @Test
    fun theFourteenDayLineAppearsOnlyOnceThereIsSpendingToDraw() {
        launch(); compose.waitForIdle(); Thread.sleep(1_200)
        assertTrue(!has("home_spark"), "nothing spent: no line, and no empty space reserved for it")
        spend("a", 25_000, 1); spend("b", 40_000, 3); launch()
        compose.waitUntil(15_000) { has("home_spark") }
    }

    @Test
    fun reduceMotionIsASwitchUnderAppearanceThatIsRememberedAndMakesScreensChangeAtOnce() {
        launch(); compose.openMore(); tap("open_appearance"); waitFor("motion_off")
        tap("motion_off"); compose.waitUntil(10_000) { runBlocking { container.write { it.templates.setting("reduce_motion") } } == "1" }
        tap("appearance_back"); compose.closeMore(); waitFor("add")
        launch(); compose.openMore(); tap("open_appearance"); waitFor("motion_off")
        assertEquals("1", runBlocking { container.write { it.templates.setting("reduce_motion") } }, "still on after the app was reopened")
    }

    @Test
    fun theSpeedChoiceIsRememberedStretchesEveryDurationAndOffStillWins() {
        launch(); compose.openMore(); tap("open_appearance"); waitFor("motion_slow")
        tap("motion_slow")
        compose.waitUntil(10_000) { runBlocking { container.write { it.templates.setting("motion_speed") } } == "slow" }
        compose.waitUntil(10_000) { app.expenses.ui.Motion.scale == 2.8f }
        assertTrue(app.expenses.ui.Motion.EMPHASIZED > 800, "a screen move takes about a second at Slow")
        assertEquals("0", runBlocking { container.write { it.templates.setting("reduce_motion") } })
        tap("motion_off"); compose.waitUntil(10_000) { runBlocking { container.write { it.templates.setting("reduce_motion") } } == "1" }
        tap("motion_quick"); compose.waitUntil(10_000) { app.expenses.ui.Motion.scale == 1f }
        assertEquals("0", runBlocking { container.write { it.templates.setting("reduce_motion") } }, "choosing a speed turns Off back off")
    }

    @Test
    fun theMonthTotalReadsTheNewFigureAtOnceEvenThoughItsDigitsRollOnTheScreen() {
        launch(); spend("x", 123_456)
        // the semantics carry the final text immediately: a test (or a screen reader) never reads a half-rolled number
        runBlocking { container.write { } }
        tap("add"); waitFor("amount"); node("amount").performTextInput("500"); tap("save"); waitFor("add")
        compose.waitUntil(10_000) { runCatching { node("month_spend").assertTextEquals("₹1,734.56"); true }.getOrDefault(false) }
    }

    @Test
    fun payingABillShowsAPaidCheckAndTheBillMovesToPaid() {
        val due = LocalDate.now().plusDays(8).format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", "Your HDFC Bank Credit Card ending 4821 statement is generated. Total Amt Due Rs.5,000.00, Min Amt Due Rs.250.00, Due Date $due.", System.currentTimeMillis()) }
        val id = runBlocking { container.write { it.bills.list(BillStatus.FOUND).single().id.also { b -> it.bills.track(b) } } }!!
        launch(); compose.openMore(); tap("open_bills"); waitFor("bills_list")
        tap("bill_pay_$id"); waitFor("bill_pay_amount"); tap("bill_pay_ok")
        waitFor("bills_paid_title"); waitFor("bill_paid_check_$id")
    }

    private fun top(tag: String) = node(tag).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun theAddFormRisesInFromBelowOverHomeRatherThanAppearingAtOnceAndReduceMotionMakesItInstant() {
        launch(); compose.mainClock.autoAdvance = false
        node("add").performClick()
        compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeBy(48)
        compose.waitUntil(5_000) { has("amount") }
        compose.mainClock.advanceTimeBy(80)
        val early = top("amount")
        compose.mainClock.advanceTimeBy(2_000)
        val settled = top("amount")
        compose.mainClock.autoAdvance = true
        assertTrue(early > settled + 40f, "the form is still on its way up 50 ms in: top $early, settled $settled")
        // with Reduce motion on, it is in place at once
        node("cancel").performClick(); waitFor("add")
        runBlocking { container.write { it.templates.putSetting("reduce_motion", "1") } }; launch()
        compose.mainClock.autoAdvance = false
        node("add").performClick(); compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeBy(48)
        compose.waitUntil(5_000) { has("amount") }
        val reduced = top("amount"); compose.mainClock.advanceTimeBy(2_000); val reducedSettled = top("amount")
        compose.mainClock.autoAdvance = true
        assertEquals(reducedSettled, reduced, 1f, "under Reduce motion the form does not travel")
    }

    @Test
    fun draggingAFingerAlongTheLineNamesTheDayAndItsAmountAndLettingGoHidesItAgain() {
        spend("a", 25_000, 1); spend("b", 40_000, 3); launch()
        compose.waitUntil(15_000) { has("home_spark") }; compose.waitForIdle()
        fun tooltip() = compose.onAllNodes(androidx.compose.ui.test.hasText(" · ₹", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        assertTrue(!tooltip(), "nothing is shown until a finger is on the line")
        node("home_spark").performTouchInput { down(androidx.compose.ui.geometry.Offset(width * 0.8f, height / 2f)); moveBy(androidx.compose.ui.geometry.Offset(-40f, 0f)) }
        compose.waitUntil(5_000) { tooltip() }
        node("home_spark").performTouchInput { up() }
        compose.waitUntil(5_000) { !tooltip() }
    }

    @Test
    fun aScreenThatIsAnimatingOutNoLongerAnswersTheBackButton() {
        launch(); tap("add"); waitFor("amount")
        var enabledWhileOpen = false
        scenario!!.onActivity { enabledWhileOpen = it.onBackPressedDispatcher.hasEnabledCallbacks() }
        assertTrue(enabledWhileOpen, "the form answers Back while it is showing")
        compose.mainClock.autoAdvance = false
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeBy(60)
        var enabledWhileLeaving = true
        scenario!!.onActivity { enabledWhileLeaving = it.onBackPressedDispatcher.hasEnabledCallbacks() }
        compose.mainClock.autoAdvance = true
        assertTrue(!enabledWhileLeaving, "the form is still drawn on its way out but a second Back must not reach it")
        waitFor("add")
    }

    @Test
    fun theBarShowsEveryTabAndTheGlassSlidesToTheOneOpenedInsteadOfJumping() {
        spend("a", 25_000, 1); launch(); waitFor("nav_home")
        compose.mainClock.autoAdvance = false
        node("open_reports").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.mainClock.autoAdvance = true
        waitFor("report_list")
        assertTrue(has("nav_home") && has("open_search") && has("more_menu"), "Reports carries the same bar")
        node("open_search").performClick(); waitFor("search_back")
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4").close() // the keyboard hides the bar; its Back key closes it
        waitFor("open_reports"); assertTrue(has("open_reports"), "and so does Search once the keyboard is away")
        node("more_menu").performClick(); waitFor("more_page")
        node("nav_home").performClick(); waitFor("add")
    }

    @Test
    fun scrollingTheEntriesUpFoldsTheTopOfHomeAndScrollingBackOpensItAgain() {
        for (i in 1..14) spend("e$i", 10_000L + i, i.toLong())
        launch(); compose.waitUntil(15_000) { has("home_spark") }; compose.waitForIdle()
        fun sparkHeight() = node("list").fetchSemanticsNode().boundsInRoot.top.toInt() // where the entries start: the folded top gives them room
        val open = sparkHeight()
        node("list").performTouchInput { swipeUp(startY = bottom - 50f, endY = top + 50f, durationMillis = 200) }
        compose.waitForIdle()
        assertTrue(sparkHeight() < open - 100, "the top folded away while the entries were scrolled up: they now start higher")
        repeat(4) { node("list").performTouchInput { swipeDown(startY = top + 50f, endY = bottom - 50f, durationMillis = 200) } }
        compose.waitForIdle()
        assertEquals(open, sparkHeight(), "and it is back in full at the top of the list")
    }
}

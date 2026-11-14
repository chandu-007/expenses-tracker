package app.expenses

import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextContains
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.ui.Feel
import app.expenses.ui.Motion
import app.expenses.ui.ReportsScreen
import app.expenses.ui.hapticConstant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** UI slice S0: the motion tiers, the haptic meanings, the Reports skeleton, the Budget row in More. */
@RunWith(AndroidJUnit4::class)
class UiS0MotionAndHapticsTest {
    private val saved = Motion.scale
    @After fun restore() { Motion.scale = saved }

    @Test
    fun feedbackIsNeverScaledAndActionsAreCappedWhileArrivalsStayRelaxedAtTheDefaultSpeed() {
        Motion.scale = Motion.scaleOf("relaxed")
        assertEquals(1.8f, Motion.scale)
        assertEquals(90, Motion.FEEDBACK)
        assertEquals(150, Motion.CHIP); assertEquals(250, Motion.TAB); assertEquals(400, Motion.PUSH); assertEquals(380, Motion.ADD_FORM)
        assertEquals(396, Motion.STANDARD, "arrivals are still 1.8x"); assertEquals(576, Motion.EMPHASIZED); assertEquals(828, Motion.GLIDE)
        assertEquals(100, Motion.REDUCED_MS)
    }

    @Test
    fun atQuickTheTiersAreAsShortAsTheSpeedMakesThemAndTheCapsOnlyBiteAbove() {
        Motion.scale = Motion.scaleOf("quick")
        assertEquals(120, Motion.CHIP, "Quick is shorter than the 150 cap"); assertEquals(220, Motion.TAB); assertEquals(320, Motion.PUSH)
        assertEquals(380, Motion.ADD_FORM, "the add form's glide is 460 at Quick: capped")
        assertEquals(90, Motion.FEEDBACK)
    }

    @Test
    fun atSlowTheCapsGrowWithTheSpeedThePersonChoseAndFeedbackStillDoesNot() {
        Motion.scale = Motion.scaleOf("slow")
        val f = 2.8f / 1.8f
        assertEquals((150 * f).toInt(), Motion.CHIP); assertEquals((250 * f).toInt(), Motion.TAB); assertEquals((400 * f).toInt(), Motion.PUSH); assertEquals((380 * f).toInt(), Motion.ADD_FORM)
        assertTrue(Motion.ADD_FORM > 380 && Motion.STANDARD > Motion.TAB)
        assertEquals(90, Motion.FEEDBACK)
    }

    @Test
    fun undoIsASoftNeutralTapNotTheFailureBuzzAndTheLimitIsTheOneWarning() {
        for (sdk in listOf(26, 29, 30, 34)) {
            assertNotEquals(HapticFeedbackConstants.REJECT, hapticConstant(Feel.UNDO, sdk), "Undo is not a rejection (API $sdk)")
            assertNotEquals(hapticConstant(Feel.LIMIT, sdk), hapticConstant(Feel.UNDO, sdk), "Undo and the limit feel different (API $sdk)")
            assertNotEquals(hapticConstant(Feel.SAVE, sdk), hapticConstant(Feel.UNDO, sdk))
        }
        assertEquals(HapticFeedbackConstants.GESTURE_END, hapticConstant(Feel.UNDO, 30)); assertEquals(HapticFeedbackConstants.KEYBOARD_TAP, hapticConstant(Feel.UNDO, 29))
        assertEquals(HapticFeedbackConstants.REJECT, hapticConstant(Feel.LIMIT, 30)); assertEquals(HapticFeedbackConstants.LONG_PRESS, hapticConstant(Feel.LIMIT, 26))
        assertEquals(HapticFeedbackConstants.CONFIRM, hapticConstant(Feel.SAVE, 34)); assertEquals(HapticFeedbackConstants.CONFIRM, hapticConstant(Feel.PAID, 34)); assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, hapticConstant(Feel.SAVE, 28))
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, hapticConstant(Feel.TICK, 26))
        for (sdk in listOf(26, 34)) assertEquals(HapticFeedbackConstants.LONG_PRESS, hapticConstant(Feel.SELECT, sdk), "starting a selection is the long-press feel")
    }
}

@RunWith(AndroidJUnit4::class)
class UiS0ReportsSkeletonTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun beforeTheFirstReportIsReadyTheChromeAndASkeletonAreShownNotTheWordLoading() {
        compose.setContent { ReportsScreen(report = null, onBack = {}, onMode = {}, onStep = {}, onDrill = { _, _, _, _ -> }, onCloseDrill = {}, onOpenEntry = {}) }
        compose.onNodeWithTag("reports_skeleton", useUnmergedTree = true).assertExists()
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("Loading", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "no 'Loading…' text")
        compose.onNodeWithTag("reports_back", useUnmergedTree = true).assertExists() // the page's own bar is there
        assertTrue(compose.onAllNodes(hasTestTag("report_list"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }
}

@RunWith(AndroidJUnit4::class)
class UiS0MoreBudgetRowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    @Test
    fun moreHasABudgetRowWithALiveLineThatOpensTheBudget() {
        runBlocking { container.write { it.budgets.setLimit(null, app.expenses.core.domain.Money(6_000_000, app.expenses.core.domain.Currency.INR)) } }
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("add"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.openMore()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("open_budget"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("open_budget").performScrollTo().assertTextContains("of ₹60,000", substring = true)
        compose.onNodeWithTag("open_budget").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("budget_summary"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() || compose.onAllNodes(hasTestTag("budget_back"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }
}

/** F-12: "Mark paid" offers Undo, and Undo puts the bill back (and removes the entry it recorded). F-10: the add buttons are extended and never cover a row's amount. */
@RunWith(AndroidJUnit4::class)
class UiS0BillUndoTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(15_000) { has(tag) }

    @Test
    fun markingABillPaidOffersUndoAndUndoBringsTheBillBackWithNothingRecorded() {
        val due = java.time.LocalDate.now().plusDays(8).format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", "Your HDFC Bank Credit Card ending 4821 statement is generated. Total Amt Due Rs.5,000.00, Min Amt Due Rs.250.00, Due Date $due.", System.currentTimeMillis()) }
        val id = runBlocking { container.write { it.bills.list(app.expenses.core.storage.BillStatus.FOUND).single().id.also { b -> it.bills.track(b) } } }!!
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add")
        compose.openMore(); compose.onNodeWithTag("open_bills", useUnmergedTree = true).performScrollTo().performClick(); waitFor("bills_list")
        compose.onNodeWithTag("bill_pay_$id", useUnmergedTree = true).performClick(); waitFor("bill_pay_amount")
        compose.onNodeWithTag("bill_pay_ok", useUnmergedTree = true).performClick()
        waitFor("bills_paid_title")
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Undo"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Undo", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { runBlocking { container.write { it.bills.get(id)?.status } } == app.expenses.core.storage.BillStatus.TRACKED }
        assertEquals(0L, runBlocking { container.write { it.db.schemaQueries.entryCount().executeAsOne() } }, "what the payment recorded is gone with it")
    }
}

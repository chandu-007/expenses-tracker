package app.expenses

import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.HapticFeedbackConstants
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.ui.BackProbe
import app.expenses.ui.Feel
import app.expenses.ui.Motion
import app.expenses.ui.OdometerProbe
import app.expenses.ui.OpeningScreen
import app.expenses.ui.hapticConstant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** UI S7: the one haptic vocabulary, as a table of what each [Feel] is on each Android version. Pure mapping: nothing is played. */
@RunWith(AndroidJUnit4::class)
class UiS7HapticTableTest {
    @Test
    fun everyFeelHasAConstantOnEveryVersionAndTheTableIsTheOneInTheDesign() {
        for (sdk in listOf(26, 29, 30, 33, 34, 36)) for (feel in Feel.entries) hapticConstant(feel, sdk) // none throws, every one is mapped
        // press: a very light segment tick from Android 14, a clock tick before
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, hapticConstant(Feel.PRESS, 33)); assertEquals(HapticFeedbackConstants.SEGMENT_TICK, hapticConstant(Feel.PRESS, 34))
        // a finished thing: the confirm from Android 11, a virtual key before; the same for a save and a paid bill
        for (sdk in listOf(26, 30, 34)) assertEquals(hapticConstant(Feel.SAVE, sdk), hapticConstant(Feel.PAID, sdk))
        assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, hapticConstant(Feel.SAVE, 29)); assertEquals(HapticFeedbackConstants.CONFIRM, hapticConstant(Feel.SAVE, 30))
        // undo is soft and neutral, never the rejection; the one warning is the rejection from Android 11
        assertEquals(HapticFeedbackConstants.GESTURE_END, hapticConstant(Feel.UNDO, 30)); assertEquals(HapticFeedbackConstants.KEYBOARD_TAP, hapticConstant(Feel.UNDO, 29))
        assertEquals(HapticFeedbackConstants.REJECT, hapticConstant(Feel.WARN, 30)); assertEquals(HapticFeedbackConstants.LONG_PRESS, hapticConstant(Feel.WARN, 29))
        // a swipe's commit point has its own pair on Android 14; a step along a chart is a frequent tick there; a long press and a key are the platform's
        assertEquals(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE, hapticConstant(Feel.THRESHOLD, 34)); assertEquals(HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE, hapticConstant(Feel.THRESHOLD_OFF, 34))
        assertEquals(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK, hapticConstant(Feel.STEP, 34)); assertEquals(HapticFeedbackConstants.CLOCK_TICK, hapticConstant(Feel.STEP, 33))
        for (sdk in listOf(26, 34)) { assertEquals(HapticFeedbackConstants.LONG_PRESS, hapticConstant(Feel.LONG_PRESS, sdk)); assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, hapticConstant(Feel.KEY, sdk)) }
    }

    @Test
    fun theFeelsThatMeanDifferentThingsFeelDifferentWhereTheSystemHasThePatterns() {
        for (sdk in listOf(30, 34)) {
            val m = mapOf("undo" to Feel.UNDO, "warn" to Feel.WARN, "save" to Feel.SAVE, "long" to Feel.LONG_PRESS).mapValues { hapticConstant(it.value, sdk) }
            assertEquals(m.size, m.values.toSet().size, "undo, the warning, a save and a long press are four different patterns (API $sdk): $m")
        }
        assertNotEquals(hapticConstant(Feel.PRESS, 34), hapticConstant(Feel.STEP, 34), "a press and a step along a chart are not the same tick on Android 14")
        assertTrue(Feel.entries.size == 10)
    }
}

/** UI S7 on the device: the Haptics and Simplify rows under Appearance, the odometer roll, the first frame, the bar's edge and the back gesture's preview. */
@RunWith(AndroidJUnit4::class)
class UiS7DeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() {
        runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1") } }
        OdometerProbe.rolls = 0; OdometerProbe.fades = 0; OdometerProbe.last = ""
    }
    @After fun cleanUp() {
        compose.waitForIdle(); scenario?.close()
        shell("settings put secure high_text_contrast_enabled 0"); shell("settings put secure contrast_level 0.0")
        runBlocking { container.startFresh() }
    }

    private fun shell(cmd: String) { android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(cmd)).use { it.readBytes() } }
    private fun swipe(x0: Float, y0: Float, x1: Float, y1: Float, steps: Int) {
        val ua = instrumentation.uiAutomation
        val down = SystemClock.uptimeMillis()
        fun event(action: Int, x: Float, y: Float) = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0).also { it.source = InputDevice.SOURCE_TOUCHSCREEN }
        ua.injectInputEvent(event(MotionEvent.ACTION_DOWN, x0, y0), true)
        // the system decides at the first movement that this is a back gesture: a small first move, a pause, then the swipe
        ua.injectInputEvent(event(MotionEvent.ACTION_MOVE, x0 + 40f, y0), true); Thread.sleep(150)
        for (i in 1..steps) { Thread.sleep(16); val f = i / steps.toFloat(); ua.injectInputEvent(event(MotionEvent.ACTION_MOVE, x0 + (x1 - x0) * f, y0 + (y1 - y0) * f), true) }
        Thread.sleep(400) // hold at the end: the gesture is judged from where the finger is when it lifts
        ua.injectInputEvent(event(MotionEvent.ACTION_UP, x1, y1), true)
    }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun gone(tag: String) { try { compose.waitUntil(10_000) { !has(tag) } } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
        val tags = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.TestTag), useUnmergedTree = true).fetchSemanticsNodes().map { it.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag] }
        throw AssertionError("$tag is still there; the screen has: ${tags.take(40)}", e)
    } }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun tap(tag: String) { waitFor(tag); runCatching { node(tag).performScrollTo() }; node(tag).performClick() }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun setting(key: String) = runBlocking { container.write { it.templates.setting(key) } }
    private fun put(key: String, value: String) { runBlocking { container.write { it.templates.putSetting(key, value) } } }
    private fun spend(id: String, minor: Long) = runBlocking { container.write { it.store.save(Expense(EntryId(id), System.currentTimeMillis() - 3_600_000, AccountId("cash"), Money(minor, Currency.INR))) } }
    private fun monthText() = runCatching { node("month_spend").fetchSemanticsNode().config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString { it.text } }.getOrNull()
    private fun waitProbe(what: String, cond: () -> Boolean) {
        try { compose.waitUntil(10_000, cond) } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("$what: rolls ${OdometerProbe.rolls}, fades ${OdometerProbe.fades}, last '${OdometerProbe.last}', month figure '${monthText()}', entries ${runBlocking { container.write { it.db.schemaQueries.entryCount().executeAsOne() } }}", e)
        }
    }
    private fun addFive00() { tap("add"); waitFor("amount"); node("amount").performTextInput("500"); tap("save"); waitFor("add") }
    private fun appearance() { compose.openMore(); tap("open_appearance"); waitFor("haptics_row") }

    @Test
    fun hapticsAreOnByDefaultAndTheSwitchTurnsThemOffAndOnAndIsRemembered() {
        launch(); appearance()
        node("haptics_row").assertIsOn()
        tap("haptics_row"); compose.waitUntil(10_000) { setting("haptics") == "0" }
        node("haptics_row").assertIsOff()
        tap("appearance_back"); compose.closeMore(); launch(); appearance()
        node("haptics_row").assertIsOff()
        tap("haptics_row"); compose.waitUntil(10_000) { setting("haptics") == "1" }
        node("haptics_row").assertIsOn()
    }

    @Test
    fun simplifyForThisPhoneIsAutomaticByDefaultAndTheChoiceSetsQuickMotionAndSaysWhy() {
        launch(); appearance()
        waitFor("simplify_line"); node("simplify_line").assertTextContains("Automatic", substring = true)
        tap("simplify_on"); compose.waitUntil(10_000) { setting("simplify_mode") == "on" }
        node("simplify_line").assertTextContains("you switched it on", substring = true)
        compose.waitUntil(10_000) { Motion.scale == 1f }
        tap("simplify_off"); compose.waitUntil(10_000) { setting("simplify_mode") == "off" }
        node("simplify_line").assertTextContains("switched simplifying off", substring = true)
        compose.waitUntil(10_000) { Motion.scale == 1.8f }
        tap("simplify_auto"); compose.waitUntil(10_000) { setting("simplify_mode") == "auto" }
        node("simplify_line").assertTextContains("Automatic", substring = true)
    }

    @Test
    fun theMonthDigitsRollOnlyWhenTheFigureChangesAndOnlyTheDigitsThatChanged() {
        spend("x", 123_456)
        launch(); compose.waitForIdle(); Thread.sleep(1_500)
        assertEquals(0, OdometerProbe.rolls, "nothing rolls when Home opens")
        addFive00()
        waitProbe("no roll after the add") { OdometerProbe.rolls >= 1 }
        // ₹1,235 (or ₹1,234) becomes ₹1,735 (or ₹1,734): only the hundreds digit moved
        assertEquals("3:2>7", OdometerProbe.last)
        compose.waitUntil(10_000) { runCatching { node("month_spend").assertTextEquals("₹1,734.56"); true }.getOrDefault(false) }
    }

    @Test
    fun underReduceMotionAChangeIsAShortCrossFadeNotARoll() {
        put("reduce_motion", "1"); spend("x", 123_456)
        launch(); compose.waitForIdle(); Thread.sleep(1_000)
        addFive00()
        waitProbe("no cross-fade after the add") { OdometerProbe.fades >= 1 }
        assertEquals(0, OdometerProbe.rolls)
    }

    @Test
    fun nothingRollsWhileAmountsAreHiddenOrOnAPhoneSimplifiedForWeakHardware() {
        put("hide_amounts", "1"); spend("x", 123_456)
        launch(); compose.waitForIdle(); Thread.sleep(1_000)
        addFive00(); compose.waitForIdle(); Thread.sleep(1_200)
        assertEquals(0, OdometerProbe.rolls + OdometerProbe.fades, "a masked figure has no digits to roll")
        put("hide_amounts", "0"); put("simplify_mode", "on")
        launch(); compose.waitForIdle(); Thread.sleep(1_000)
        addFive00(); compose.waitForIdle(); Thread.sleep(1_200)
        assertEquals(0, OdometerProbe.rolls + OdometerProbe.fades, "a simplified phone shows the new figure without a roll")
    }

    @Test
    fun theBarHasASoftEdgeAboveItAndAnOpaqueBarWithAVisibleEdgeUnderLessTransparency() {
        launch(); waitFor("bar_glass"); assertTrue(!has("bar_opaque"))
        shell("settings put secure high_text_contrast_enabled 1"); shell("settings put secure contrast_level 1.0")
        launch(); waitFor("bar_opaque"); assertTrue(!has("bar_glass"))
    }

    @Test
    fun theBackGestureShowsAPreviewThatFollowsTheFingerAndBackStillGoesBack() {
        assumeTrue("the finger-following back gesture is Android 14 and later", Build.VERSION.SDK_INT >= 34)
        launch(); appearance()
        BackProbe.reset()
        val m = context.resources.displayMetrics
        swipe(2f, m.heightPixels / 2f, m.widthPixels * 0.75f, m.heightPixels / 2f, 50)
        Thread.sleep(1_500)
        assertTrue(!has("appearance_back"), "the gesture did not go back: progress ${BackProbe.maxProgress}, committed ${BackProbe.committed}, cancelled ${BackProbe.cancelled}")
        assertTrue(BackProbe.maxProgress > 0.05f, "the gesture reported its progress (${BackProbe.maxProgress})")
        assertEquals(1, BackProbe.committed)
        // the Back button (all at once) still goes back
        compose.openMore(); tap("open_appearance"); waitFor("haptics_row")
        shell("input keyevent KEYCODE_BACK"); gone("appearance_back")
    }
}

/** UI S7: the first frame is the frame of Home with nothing to find or announce, and no spinner. */
@RunWith(AndroidJUnit4::class)
class UiS7OpeningTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun theOpeningFrameDrawsHomesBarAndMonthCardWithoutTagsOrSemantics() {
        compose.setContent { androidx.compose.material3.MaterialTheme { OpeningScreen() } }
        compose.waitForIdle()
        for (tag in listOf("add", "nav_home", "open_search", "open_reports", "more_menu", "month_spend", "bar_glass", "bar_opaque")) compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).assertCountEquals(0)
        // a screen reader gets the merged tree: the words of the ghost bar are cleared from it
        compose.onAllNodes(hasText("Home")).assertCountEquals(0)
        compose.onAllNodes(hasText("Search")).assertCountEquals(0)
        compose.onAllNodes(hasProgressBarRangeInfo(androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate), useUnmergedTree = true).assertCountEquals(0)
    }
}

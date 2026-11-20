package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.PaceLevel
import app.expenses.core.domain.Template
import app.expenses.core.domain.WidgetAccess
import app.expenses.core.domain.WidgetBudget
import app.expenses.core.domain.WidgetInsights
import app.expenses.core.domain.WidgetPace
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.widget.HiddenBy
import app.expenses.widget.QuickAddAction
import app.expenses.widget.QuickAddWidget
import app.expenses.widget.TEMPLATE_ID
import app.expenses.widget.TODAY_HIDDEN
import app.expenses.widget.TodayContent
import app.expenses.widget.TodayData
import app.expenses.widget.TodayWidget
import app.expenses.widget.ToggleTotalAction
import app.expenses.widget.WidgetButton
import app.expenses.widget.WidgetContent
import app.expenses.widget.WidgetModel
import app.expenses.widget.WidgetUndo
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val inr = Currency.INR
private val chaiTemplate = Template("chai", "Chai", Money(2_000, inr), CategoryId("Food"), AccountId("cash"), "Tea stall")

/** UI S8 (U-23): what the redesigned widgets draw from their model, at the launchers' sizes. Invented data only; no launcher needed. */
@RunWith(AndroidJUnit4::class)
class UiS8WidgetsTest {
    private val pace = WidgetPace(PaceLevel.A_LITTLE_AHEAD, "A little ahead", 533)
    private val budget = WidgetBudget(600, false, "₹24K left of ₹60K", pace, "₹24,000.00 left of ₹60,000.00. A little ahead. Today is 53 percent of the way through the period.")
    private val chai = WidgetButton("chai", "Chai", "₹20.00")
    private fun today(revealed: Boolean, hiddenBy: HiddenBy? = null, chip: WidgetButton? = chai) = TodayData(
        spentToday = if (revealed) "₹1,240.00" else "₹••••", perDayLine = if (revealed) "₹1,565.00 a day keeps you on pace" else "₹•••• a day keeps you on pace",
        revealed = revealed, canToggle = hiddenBy == null, hiddenBy = hiddenBy, budget = if (revealed) budget else null, chip = if (revealed || hiddenBy == HiddenBy.WIDGETS_OFF) null else chip,
    )

    @Test
    fun shownTheTodayWidgetIsOneNumberThePaceLineAndThePaceBarWithTheExactAmountsSpoken() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(170.dp, 170.dp))
        provideComposable { TodayContent(today(revealed = true), 170, 170) }
        onNode(hasText("₹1,240.00")).assertExists()
        onNode(hasText("₹1,565.00 a day keeps you on pace")).assertExists()
        onNode(hasContentDescription(budget.spoken)).assertExists()
        onNode(hasContentDescription("Hide the amount")).assertExists(); onNode(hasContentDescription("Add an expense")).assertExists()
        onNode(hasText("Chai")).assertDoesNotExist()
        onNode(hasText(TODAY_HIDDEN)).assertDoesNotExist()
    }

    @Test
    fun maskedTheTodayWidgetSaysSoAndKeepsAQuickEntrySoItStillCaptures() = runGlanceAppWidgetUnitTest {
        // the chip needs 64 dp of free width in the bottom row beside the eye and Add (48 dp each): 184 dp of widget at the least (a 3 x 2 cell widget is wider); at 170 dp it is left out
        setAppWidgetSize(DpSize(190.dp, 170.dp))
        provideComposable { TodayContent(today(revealed = false), 190, 170) }
        onNode(hasText("₹••••")).assertExists(); onNode(hasText(TODAY_HIDDEN)).assertExists()
        onNode(hasText("Chai")).assertExists()
        onNode(hasText("₹20.00")).assertDoesNotExist() // the chip is the name only
        onNode(hasContentDescription("Show the amount")).assertExists(); onNode(hasContentDescription("Add an expense")).assertExists()
        onNode(hasText("₹•••• a day keeps you on pace")).assertDoesNotExist()
        onNode(hasContentDescription(budget.spoken)).assertDoesNotExist()
    }

    @Test
    fun theNarrowestTodayWidgetKeepsTheEyeAndAddAndLeavesTheChipOut() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(110.dp, 110.dp))
        provideComposable { TodayContent(today(revealed = false), 110, 110) }
        onNode(hasText("Chai")).assertDoesNotExist()
        onNode(hasContentDescription("Show the amount")).assertExists(); onNode(hasContentDescription("Add an expense")).assertExists()
    }

    @Test
    fun withWidgetsNotAllowedTheTodayWidgetSaysSoAndShowsNoChipNoEyeAndNoFigure() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(170.dp, 170.dp))
        provideComposable { TodayContent(today(revealed = false, hiddenBy = HiddenBy.WIDGETS_OFF), 170, 170) }
        onNode(hasText(HiddenBy.WIDGETS_OFF.caption)).assertExists()
        onNode(hasText("Chai")).assertDoesNotExist()
        onNode(hasContentDescription("Show the amount")).assertDoesNotExist()
        onNode(hasContentDescription("Add an expense")).assertExists()
    }

    @Test
    fun afterAChipTapTheTodayWidgetOffersUndo() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(170.dp, 170.dp))
        provideComposable { TodayContent(today(revealed = false), 170, 170, WidgetUndo("e1", "✓ Chai ₹20 added")) }
        onNode(hasText("✓ Chai ₹20 added")).assertExists(); onNode(hasText("Undo")).assertExists()
    }

    @Test
    fun theFourByTwoAddsThePaceWordAndBarBesideItsChips() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(320.dp, 160.dp))
        val model = WidgetModel(listOf(chai, WidgetButton("auto", "Auto", "₹80.00")), null, needsApp = false, monthSpend = "₹36,000.00", revealed = true, canToggle = true,
            insights = WidgetInsights(null, budget, emptyList(), emptyList()))
        provideComposable { WidgetContent(model) }
        onNode(hasText("₹36,000.00")).assertExists()
        onNode(hasText("₹24K left of ₹60K")).assertExists(); onNode(hasText("A little ahead")).assertExists()
        onNode(hasContentDescription(budget.spoken)).assertExists()
        onNode(hasText("Chai")).assertExists(); onNode(hasText("Auto")).assertExists()
    }

    @Test
    fun withWidgetsNotAllowedTheCardSaysWhyAndDoesNotAskForQuickEntries() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(320.dp, 294.dp))
        provideComposable { WidgetContent(WidgetModel(emptyList(), null, needsApp = false, monthSpend = "₹••••", revealed = false, canToggle = false, hiddenBy = HiddenBy.WIDGETS_OFF)) }
        onNode(hasText(HiddenBy.WIDGETS_OFF.note)).assertExists()
        onNode(hasText("Save a quick entry in the app to see it here")).assertDoesNotExist()
        onNode(hasContentDescription("Show the amount")).assertDoesNotExist()
        onNode(hasText("+ Add")).assertExists()
    }
}

/** UI S8: what the widgets read and do while "Allow widgets" is off, through the real loading code and callbacks. */
@RunWith(AndroidJUnit4::class)
class UiS8WidgetDataTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val glanceId = AppWidgetId(4248)

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.save(chaiTemplate) } } }
    @After fun cleanUp() { runBlocking { container.startFresh() } }

    private fun put(key: String, value: String) = runBlocking { container.write { it.templates.putSetting(key, value) } }
    private fun entries() = runBlocking { container.write { ok -> ok.db.schemaQueries.recentEntries(20).executeAsList() }!! }

    @Test
    fun widgetsAreAllowedByDefaultAndOffTheyReadNoQuickEntryNoFigureAndNoEye() {
        val on = runBlocking { QuickAddWidget().load(context) }!!
        assertEquals(listOf("chai"), on.buttons.map { it.templateId }); assertTrue(on.canToggle)
        val onToday = runBlocking { TodayWidget().loadToday(context) }!!
        assertEquals("chai", onToday.chip?.templateId, "a masked Today widget keeps the first quick entry")
        put(WidgetAccess.SETTING, "0")
        val off = runBlocking { QuickAddWidget().load(context) }!!
        assertTrue(off.buttons.isEmpty() && !off.canToggle && !off.revealed && off.monthSpend.none { it.isDigit() }, "$off")
        assertEquals(HiddenBy.WIDGETS_OFF, off.hiddenBy)
        val offToday = runBlocking { TodayWidget().loadToday(context) }!!
        assertNull(offToday.chip); assertNull(offToday.perDayLine); assertEquals(HiddenBy.WIDGETS_OFF, offToday.hiddenBy)
    }

    @Test
    fun withWidgetsNotAllowedAChipStillOnTheScreenAddsNothingAndTheEyeChangesNothing() {
        put(WidgetAccess.SETTING, "0")
        val before = entries().size
        runBlocking { QuickAddAction().onAction(context, glanceId, actionParametersOf(TEMPLATE_ID to "chai")) }
        assertEquals(before, entries().size, "the tap is refused")
        runBlocking { ToggleTotalAction().onAction(context, glanceId, actionParametersOf()) }
        assertTrue(runBlocking { container.write { it.templates.setting("widget_show_total") } } != "1", "the eye does not turn the figures on")
        put(WidgetAccess.SETTING, "1")
        runBlocking { QuickAddAction().onAction(context, glanceId, actionParametersOf(TEMPLATE_ID to "chai")) }
        assertEquals(before + 1, entries().size, "allowed again, the chip adds")
    }
}

/** UI S8 (N-20): the quick entries as the app icon's launcher shortcuts, and their tap. */
@RunWith(AndroidJUnit4::class)
class UiS8ShortcutsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1"); it.templates.save(chaiTemplate) } } }
    @After fun cleanUp() {
        compose.waitForIdle(); scenario?.close()
        runBlocking { container.startFresh() }
        ShortcutManagerCompat.removeAllDynamicShortcuts(context)
    }

    private fun put(key: String, value: String) = runBlocking { container.write { it.templates.putSetting(key, value) } }
    private fun dynamic() = ShortcutManagerCompat.getDynamicShortcuts(context).filter { it.id.startsWith("quick_") }
    private fun count() = runBlocking { container.write { it.db.schemaQueries.entryCount().executeAsOne() } }!!

    @Test
    fun theQuickEntriesBecomeShortcutsWithTheAmountOnlyWhenNothingHidesItAndNoneWhenWidgetsAreOff() {
        runBlocking { LauncherShortcuts.publish(context) }
        assertEquals(listOf("quick_chai"), dynamic().map { it.id })
        assertEquals("Add Chai ₹20", dynamic().single().longLabel.toString()); assertEquals("Chai", dynamic().single().shortLabel.toString())
        put("hide_amounts", "1")
        runBlocking { LauncherShortcuts.publish(context) }
        assertEquals("Add Chai", dynamic().single().longLabel.toString(), "Hide amounts: no amount on the launcher")
        put(WidgetAccess.SETTING, "0")
        runBlocking { LauncherShortcuts.publish(context) }
        assertTrue(dynamic().isEmpty(), "widgets not allowed: no shortcut")
    }

    private fun launchShortcut() {
        scenario = ActivityScenario.launch(Intent(LauncherShortcuts.ACTION_QUICK_ADD).setClassName(context, "app.expenses.MainActivity").putExtra(LauncherShortcuts.EXTRA_TEMPLATE_ID, "chai"))
    }

    @Test
    fun aShortcutTapRecordsTheEntryOnceAndIsRefusedWhileWidgetsAreOff() {
        val before = count()
        launchShortcut()
        compose.waitUntil(10_000) { count() == before + 1 }
        scenario?.recreate() // a rotation must not add it again
        compose.waitForIdle(); Thread.sleep(1_000)
        assertEquals(before + 1, count())
        scenario?.close()
        put(WidgetAccess.SETTING, "0")
        launchShortcut()
        compose.waitForIdle(); Thread.sleep(1_500)
        assertEquals(before + 1, count(), "refused while widgets are off")
    }
}

/** UI S8 on the device: the long press on Add (N-20) and the "Allow widgets" switch (GLOBAL I31). */
@RunWith(AndroidJUnit4::class)
class UiS8DeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun tap(tag: String) { waitFor(tag); runCatching { node(tag).performScrollTo() }; node(tag).performClick() }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun setting(key: String) = runBlocking { container.write { it.templates.setting(key) } }
    private fun count() = runBlocking { container.write { it.db.schemaQueries.entryCount().executeAsOne() } }!!

    @Test
    fun aLongPressOnAddOffersTheQuickEntriesAndOneTapRecordsIt() {
        runBlocking { container.write { it.templates.save(chaiTemplate) } }
        launch()
        val before = count()
        node("add").performTouchInput { longClick() }
        waitFor("bar_quick_menu")
        tap("bar_quick_chai")
        compose.waitUntil(10_000) { count() == before + 1 }
        compose.waitUntil(10_000) { !has("bar_quick_menu") }
        assertTrue(!has("amount"), "a quick entry does not open the Add form")
    }

    @Test
    fun withNoQuickEntryALongPressOnAddOpensNoMenuAndOpensTheFormLikeATap() {
        launch()
        // with no quick entry saved the long press is only a press: it opens the Add form like a tap (Compose's clickable acts on release) and there is no menu
        node("add").performTouchInput { longClick() }
        waitFor("amount")
        assertTrue(!has("bar_quick_menu"))
    }

    @Test
    fun allowWidgetsIsOnByDefaultAndTheSwitchOnTheAppLockScreenTurnsItOffAndOn() {
        launch()
        compose.openMore(); tap("open_security"); waitFor("widgets_allowed_switch")
        runCatching { node("widgets_allowed_switch").performScrollTo() }
        node("widgets_allowed_switch").assertIsOn()
        tap("widgets_allowed_switch"); compose.waitUntil(10_000) { setting(WidgetAccess.SETTING) == "0" }
        node("widgets_allowed_switch").assertIsOff()
        tap("widgets_allowed_switch"); compose.waitUntil(10_000) { setting(WidgetAccess.SETTING) == "1" }
        node("widgets_allowed_switch").assertIsOn()
    }
}

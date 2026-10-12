package app.expenses

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.Template
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.widget.LAST_AT
import app.expenses.widget.LAST_ID
import app.expenses.widget.LAST_TEXT
import app.expenses.widget.QuickAddAction
import app.expenses.widget.QuickAddReceiver
import app.expenses.widget.QuickAddWidget
import app.expenses.widget.TEMPLATE_ID
import app.expenses.widget.UNDO_ENTRY_ID
import app.expenses.widget.UNDO_WINDOW_MILLIS
import app.expenses.widget.UndoAction
import app.expenses.widget.WidgetButton
import app.expenses.widget.WidgetContent
import app.expenses.widget.WidgetModel
import app.expenses.widget.WidgetUndo
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class QuickAddWidgetTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val inr = Currency.INR
    private val glanceId = AppWidgetId(4242)

    private fun resetApp() {
        runBlocking { container.startFresh() }
    }

    @Before fun fresh() = resetApp()
    @After fun cleanUp() = resetApp()

    private fun saveChai() = runBlocking {
        container.write { ok -> ok.templates.save(Template("chai", "Chai", Money(2_000, inr), CategoryId("Food"), AccountId("cash"), "Tea stall")) }
    }

    private fun recentEntries() = runBlocking { container.write { ok -> ok.db.schemaQueries.recentEntries(20).executeAsList() }!! }

    // ---- what the widget shows ----

    private val chai = WidgetButton("a", "Chai", "₹20.00")
    private val auto = WidgetButton("b", "Auto", "₹50.00")
    private val many = listOf(chai, auto, WidgetButton("c", "Lunch", "₹120.00"), WidgetButton("d", "Metro", "₹30.00"), WidgetButton("e", "Coffee", "₹90.00"), WidgetButton("f", "Parking", "₹40.00"))

    @Test
    fun theDefaultSizeShowsThisMonthsSpendingTheAddPillAndTheFirstChipsWithNoUndoLine() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(250.dp, 110.dp))
        provideComposable { WidgetContent(WidgetModel(listOf(chai, auto), null, needsApp = false, monthSpend = "₹12,345.00")) }
        onNode(hasText("Spent this month")).assertExists()
        onNode(hasText("₹12,345.00")).assertExists()
        onNode(hasText("+ Add")).assertExists()
        onNode(hasText("Chai")).assertExists(); onNode(hasText("₹20.00")).assertExists()
        onNode(hasText("Auto")).assertExists()
        onNode(hasText("Undo")).assertDoesNotExist()
    }

    @Test
    fun aLargerWidgetShowsMoreChipsAndASmallerOneFewerOrNone() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(250.dp, 250.dp))
        provideComposable { WidgetContent(WidgetModel(many, null, needsApp = false, monthSpend = "₹1.00")) }
        for (n in listOf("Chai", "Auto", "Lunch", "Metro", "Coffee", "Parking")) onNode(hasText(n)).assertExists()
    }

    @Test
    fun aSmallWidgetKeepsTheSpendingAndAShortAddButtonAndDropsTheChips() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(110.dp, 60.dp))
        provideComposable { WidgetContent(WidgetModel(many, WidgetUndo("e1", "Added Chai"), needsApp = false, monthSpend = "₹500.00")) }
        onNode(hasText("₹500")).assertExists()   // no ".00" at this size
        onNode(hasText("+")).assertExists()
        onNode(hasText("Chai")).assertDoesNotExist()
        onNode(hasText("Undo")).assertDoesNotExist()
    }

    @Test
    fun aWideShortWidgetKeepsTheSpendingAndPutsOneChipAndTheAddPillInTheRow() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(250.dp, 60.dp))
        provideComposable { WidgetContent(WidgetModel(many, null, needsApp = false, monthSpend = "₹500.00")) }
        onNode(hasText("₹500.00")).assertExists(); onNode(hasText("+ Add")).assertExists()
        onNode(hasText("Chai")).assertExists()
        onNode(hasText("₹20.00")).assertDoesNotExist()   // the chip is the name only here, so the spending keeps its room
        onNode(hasText("Auto")).assertDoesNotExist()
    }

    @Test
    fun showsTheUndoLineAfterATapWhenThereIsRoom() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(250.dp, 250.dp))
        provideComposable { WidgetContent(WidgetModel(listOf(chai), WidgetUndo("e1", "Added Chai ₹20.00"), needsApp = false, monthSpend = "₹20.00")) }
        onNode(hasText("Added Chai ₹20.00")).assertExists()
        onNode(hasText("Undo")).assertExists()
    }

    @Test
    fun explainsAnEmptyWidget() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(250.dp, 110.dp))
        provideComposable { WidgetContent(WidgetModel(emptyList(), null, needsApp = false, monthSpend = "₹0.00")) }
        onNode(hasText("Save a quick entry in the app to see it here")).assertExists()
    }

    @Test
    fun asksTheUserToOpenTheAppWhenTheDataIsLocked() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(250.dp, 110.dp))
        provideComposable { WidgetContent(WidgetModel(emptyList(), null, needsApp = true)) }
        onNode(hasText("Open the app to unlock your data")).assertExists()
    }

    @Test
    fun theLayoutRulesFollowTheSizeAndNeverShowMoreChipsThanThereAreOrRoomFor() {
        fun l(w: Int, h: Int, chips: Int, undo: Boolean = false) = app.expenses.widget.WidgetLayout.of(DpSize(w.dp, h.dp), chips, undo)
        assertEquals(0, l(110, 60, 6).showChips); assertEquals(false, l(110, 60, 6).showUndo); assertEquals(0, l(170, 60, 6).showChips, "narrower than about four cells is the small layout")
        assertEquals(1, l(250, 60, 6).showChips); assertEquals(true, l(250, 60, 6).chipsInHeader); assertEquals(2, l(360, 60, 6).showChips, "two chips only when the width allows"); assertEquals(1, l(360, 60, 1).showChips)
        assertEquals(2, l(250, 110, 6).showChips, "one row of two fits the default size"); assertEquals(false, l(250, 110, 6).chipsInHeader)
        assertEquals(6, l(250, 250, 6).showChips); assertEquals(3, l(250, 250, 3).showChips)
        assertEquals(6, l(250, 200, 6).showChips); assertEquals(4, l(250, 200, 6, undo = true).showChips, "the undo line takes a row's room")
        assertEquals(true, l(250, 250, 0).showHint); assertEquals(false, l(250, 250, 2).showHint)
    }

    @Test
    fun theUndoLineIsOfferedOnlyInsideTheUndoWindow() {
        val buttons = listOf(WidgetButton("a", "Chai"))
        val prefs = mutablePreferencesOf(LAST_ID to "e1", LAST_TEXT to "Added Chai", LAST_AT to 1_000_000L)
        assertEquals(WidgetUndo("e1", "Added Chai"), QuickAddWidget.model(buttons, prefs, 1_000_000L + UNDO_WINDOW_MILLIS).undo)
        assertNull(QuickAddWidget.model(buttons, prefs, 1_000_000L + UNDO_WINDOW_MILLIS + 1).undo)
        assertNull(QuickAddWidget.model(buttons, prefs, 999_999L).undo, "a clock that went backwards must not resurrect an old undo")
        assertNull(QuickAddWidget.model(buttons, mutablePreferencesOf(), 1_000_000L).undo)
        assertTrue(QuickAddWidget.model(null, prefs, 0).needsApp)
    }

    // ---- what the widget reads ----

    @Test
    fun theMonthFigureCountsOnlyThisCalendarMonthAndFollowsHideAmounts() {
        saveChai()
        val now = System.currentTimeMillis()
        val lastMonth = java.time.LocalDate.now().withDayOfMonth(1).minusDays(3).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        runBlocking {
            container.write { ok ->
                ok.store.save(app.expenses.core.domain.ledger.Expense(app.expenses.core.domain.ledger.EntryId("now"), now, AccountId("cash"), Money(123_400, inr), emptyList()))
                ok.store.save(app.expenses.core.domain.ledger.Expense(app.expenses.core.domain.ledger.EntryId("old"), lastMonth, AccountId("cash"), Money(999_900, inr), emptyList()))
            }
        }
        val fresh = runBlocking { QuickAddWidget().load(context) }!!
        assertTrue(fresh.monthSpend.none { it.isDigit() } && !fresh.revealed && fresh.canToggle, "the total is hidden until the person shows it from the widget: ${fresh.monthSpend}")
        runBlocking { container.write { it.templates.putSetting("widget_show_total", "1") } }
        val shown = runBlocking { QuickAddWidget().load(context) }!!
        assertEquals("₹1,234.00", shown.monthSpend, "this month's 1,234 only, not last month's 9,999")
        assertTrue(shown.revealed)
        assertEquals(listOf(WidgetButton("chai", "Chai", "₹20.00")), shown.buttons)
        runBlocking { container.write { it.templates.putSetting("hide_amounts", "1") } }
        val hidden = runBlocking { QuickAddWidget().load(context) }!!
        assertTrue(!hidden.monthSpend.any { it.isDigit() }, "the app's Hide amounts wins over the widget's switch: ${hidden.monthSpend}")
        assertTrue(!hidden.canToggle && !hidden.revealed, "no eye is offered when the app forces it hidden")
        assertTrue(hidden.buttons.single().detail.none { it.isDigit() }, hidden.buttons.single().detail)
    }

    @Test
    fun theMonthTotalIsMaskedWhenAmountsAreHiddenOrTheAppLockIsOn() {
        assertEquals("₹1,234.00", QuickAddWidget.monthFigure("₹1,234.00", hide = false, locked = false, revealed = true))
        for ((h, l) in listOf(true to false, false to true, true to true)) assertTrue(QuickAddWidget.monthFigure("₹1,234.00", h, l, revealed = true).none { it.isDigit() }, "hide=$h locked=$l")
        assertTrue(QuickAddWidget.monthFigure("₹1,234.00", hide = false, locked = false, revealed = false).none { it.isDigit() }, "not shown until the person shows it")
    }

    @Test
    fun theEyeOnTheWidgetFlipsTheTotalForEveryCopyAndPersists() {
        saveChai()
        runBlocking { container.write { ok -> ok.store.save(app.expenses.core.domain.ledger.Expense(app.expenses.core.domain.ledger.EntryId("now"), System.currentTimeMillis(), AccountId("cash"), Money(50_000, inr), emptyList())) } }
        assertTrue(runBlocking { QuickAddWidget().load(context) }!!.monthSpend.none { it.isDigit() })
        runBlocking { app.expenses.widget.ToggleTotalAction().onAction(context, glanceId, actionParametersOf()) }
        assertEquals("₹500.00", runBlocking { QuickAddWidget().load(context) }!!.monthSpend)
        runBlocking { app.expenses.widget.ToggleTotalAction().onAction(context, glanceId, actionParametersOf()) }
        assertTrue(runBlocking { QuickAddWidget().load(context) }!!.monthSpend.none { it.isDigit() }, "a second tap hides it again")
    }

    private fun eyeCase(model: WidgetModel, present: String, absent: String? = null) = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(250.dp, 110.dp))
        provideComposable { WidgetContent(model) }
        if (absent == null) onNode(androidx.glance.testing.unit.hasContentDescription(present)).assertExists() else onNode(androidx.glance.testing.unit.hasContentDescription(present)).assertDoesNotExist()
    }

    @Test fun theEyeOffersToShowAHiddenTotal() = eyeCase(WidgetModel(listOf(chai), null, needsApp = false, monthSpend = "₹••••", revealed = false, canToggle = true), "Show the amount")
    @Test fun theEyeOffersToHideAShownTotal() = eyeCase(WidgetModel(listOf(chai), null, needsApp = false, monthSpend = "₹500.00", revealed = true, canToggle = true), "Hide the amount")
    @Test fun noEyeIsDrawnWhenTheAppForcesTheTotalHidden() = eyeCase(WidgetModel(listOf(chai), null, needsApp = false, monthSpend = "₹••••", revealed = false, canToggle = false), "Show the amount", absent = "yes")

    @Test
    fun aWidgetPlacedFromEachPickerEntryIsFoundWhenTheWidgetIsUpdated() {
        val manager = AppWidgetManager.getInstance(context)
        val host = AppWidgetHost(context, 4244)
        val ids = ArrayList<Int>()
        try {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val receivers = listOf(QuickAddReceiver::class.java, app.expenses.widget.QuickAddSmallReceiver::class.java, app.expenses.widget.QuickAddWideReceiver::class.java, app.expenses.widget.QuickAddLargeReceiver::class.java)
            var allBound = true
            for (r in receivers) {
                val id = host.allocateAppWidgetId(); ids += id
                val bound = if (android.os.Build.VERSION.SDK_INT >= 29) {
                    automation.adoptShellPermissionIdentity(android.Manifest.permission.BIND_APPWIDGET)
                    try { manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, r)) } finally { automation.dropShellPermissionIdentity() }
                } else {
                    java.io.FileInputStream(automation.executeShellCommand("cmd appwidget grantbind --package ${context.packageName} --user 0").fileDescriptor).use { it.readBytes() }
                    manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, r))
                }
                allBound = allBound && bound
            }
            assumeTrue("binding was not allowed on this device", allBound)
            val found = runBlocking { GlanceAppWidgetManager(context).getGlanceIds(QuickAddWidget::class.java) }.map { (it as AppWidgetId).appWidgetId }
            assertTrue(found.containsAll(ids), "an update of the widget must reach every copy, whichever picker entry it came from: placed $ids, found $found")
        } finally { ids.forEach { host.deleteAppWidgetId(it) } }
    }

    @Test
    fun theFourStartingSizesAreAllListedAndTheirTargetCellsAreRightOnNewerAndroid() {
        val manager = AppWidgetManager.getInstance(context)
        val names = manager.getInstalledProvidersForPackage(context.packageName, null).map { it.provider.className.substringAfterLast('.') }.toSet()
        assertTrue(names.containsAll(setOf("QuickAddReceiver", "QuickAddSmallReceiver", "QuickAddWideReceiver", "QuickAddLargeReceiver")), names.toString())
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val cells = manager.getInstalledProvidersForPackage(context.packageName, null).associate { it.provider.className.substringAfterLast('.') to (it.targetCellWidth to it.targetCellHeight) }
            assertEquals(listOf(2 to 1, 4 to 1, 4 to 2, 4 to 4), listOf("QuickAddSmallReceiver", "QuickAddWideReceiver", "QuickAddReceiver", "QuickAddLargeReceiver").map { cells.getValue(it) })
        }
    }

    // ---- what a tap does, against the real encrypted database ----

    @Test
    fun tappingAQuickEntryRecordsTheExpenseAndUndoTakesItBack() {
        saveChai()
        runBlocking { QuickAddAction().onAction(context, glanceId, actionParametersOf(TEMPLATE_ID to "chai")) }
        val entry = recentEntries().single()
        assertEquals(2_000L, entry.amount_minor)
        assertEquals("Tea stall", entry.merchant)
        assertEquals(2_000L, runBlocking { container.write { it.store.spendByCategory(inr).values.single().minorUnits } })

        runBlocking { UndoAction().onAction(context, glanceId, actionParametersOf(UNDO_ENTRY_ID to entry.id)) }
        assertTrue(recentEntries().isEmpty())
        assertTrue(runBlocking { container.write { it.store.spendByCategory(inr).isEmpty() } }!!)
    }

    @Test
    fun widgetTapsLeaveTheSearchIndexMergedSoSegmentsNeverPileUp() {
        saveChai()
        repeat(6) { runBlocking { QuickAddAction().onAction(context, glanceId, actionParametersOf(TEMPLATE_ID to "chai")) } }
        assertEquals(6, recentEntries().size)
        val moreToMerge = runBlocking { container.write { ok -> app.expenses.core.storage.FtsTuning.mergeStep(ok.driver) } }
        assertEquals(false, moreToMerge, "after widget taps the index should already be merged")
    }

    @Test
    fun anUndoThatIsTooOldIsRefusedAndTheEntryStays() {
        saveChai()
        val oldId = runBlocking {
            container.write { ok -> EntryActions(ok, nowMillis = { System.currentTimeMillis() - UNDO_WINDOW_MILLIS - 60_000 }).quickAdd("chai")!!.entryId }
        }!!
        runBlocking { UndoAction().onAction(context, glanceId, actionParametersOf(UNDO_ENTRY_ID to oldId)) }
        assertEquals(listOf(oldId), recentEntries().map { it.id })
    }

    @Test
    fun tappingAMissingQuickEntryChangesNothing() {
        runBlocking { QuickAddAction().onAction(context, glanceId, actionParametersOf(TEMPLATE_ID to "ghost")) }
        assertTrue(recentEntries().isEmpty())
    }

    // ---- the system side ----

    @Test
    fun theSystemKnowsTheWidgetAndItCanBeBoundAndRendered() {
        val manager = AppWidgetManager.getInstance(context)
        val provider = manager.getInstalledProvidersForPackage(context.packageName, null).singleOrNull { it.provider.className.endsWith("QuickAddReceiver") }
        assertNotNull(provider, "the system does not list the widget provider")
        assertTrue(provider.minWidth >= 250 || provider.minWidth > 0)

        val host = AppWidgetHost(context, 4243)
        val id = host.allocateAppWidgetId()
        try {
            // Binding a widget needs BIND_APPWIDGET, which apps do not hold but the shell does. From Android 10 the test can
            // borrow that permission; on older versions it asks the shell to grant this package the right to bind.
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val bound = if (android.os.Build.VERSION.SDK_INT >= 29) {
                automation.adoptShellPermissionIdentity(android.Manifest.permission.BIND_APPWIDGET)
                try {
                    manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, QuickAddReceiver::class.java))
                } finally {
                    automation.dropShellPermissionIdentity()
                }
            } else {
                val fd = automation.executeShellCommand("cmd appwidget grantbind --package ${context.packageName} --user 0")
                java.io.FileInputStream(fd.fileDescriptor).use { it.readBytes() } // wait for the command to finish
                fd.close()
                manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, QuickAddReceiver::class.java))
            }
            assumeTrue("binding was not allowed on this device even after asking the shell; verified on newer Android only", bound)
            saveChai()
            runBlocking {
                val gid = GlanceAppWidgetManager(context).getGlanceIdBy(id)
                QuickAddWidget().update(context, gid)
            }
            assertNotNull(manager.getAppWidgetInfo(id))
        } finally {
            host.deleteAppWidgetId(id)
        }
    }
}

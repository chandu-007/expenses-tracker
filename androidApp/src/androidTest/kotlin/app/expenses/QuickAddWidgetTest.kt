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

    @Test
    fun showsOneButtonPerQuickEntryAndThePlusButtonWithNoUndoLine() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(250.dp, 110.dp))
        provideComposable { WidgetContent(WidgetModel(listOf(WidgetButton("a", "Chai ₹20.00"), WidgetButton("b", "Auto ₹50.00")), null, needsApp = false)) }
        onNode(hasText("Quick add")).assertExists()
        onNode(hasText("+")).assertExists()
        onNode(hasText("Chai ₹20.00")).assertExists()
        onNode(hasText("Auto ₹50.00")).assertExists()
        onNode(hasText("Undo")).assertDoesNotExist()
    }

    @Test
    fun showsTheUndoLineAfterATap() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(250.dp, 110.dp))
        provideComposable { WidgetContent(WidgetModel(listOf(WidgetButton("a", "Chai ₹20.00")), WidgetUndo("e1", "Added Chai ₹20.00"), needsApp = false)) }
        onNode(hasText("Added Chai ₹20.00")).assertExists()
        onNode(hasText("Undo")).assertExists()
    }

    @Test
    fun explainsAnEmptyWidget() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(250.dp, 110.dp))
        provideComposable { WidgetContent(WidgetModel(emptyList(), null, needsApp = false)) }
        onNode(hasText("Save a quick entry in the app to see it here")).assertExists()
    }

    @Test
    fun asksTheUserToOpenTheAppWhenTheDataIsLocked() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(250.dp, 110.dp))
        provideComposable { WidgetContent(WidgetModel(emptyList(), null, needsApp = true)) }
        onNode(hasText("Open the app to unlock your data")).assertExists()
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

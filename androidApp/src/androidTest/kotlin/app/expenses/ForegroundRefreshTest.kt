package app.expenses

import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.AppWidgetId
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.Template
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.widget.QuickAddAction
import app.expenses.widget.TEMPLATE_ID
import app.expenses.widget.UNDO_ENTRY_ID
import app.expenses.widget.UndoAction
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What was written while the screen was away is on the screen the moment the app comes back. The view model lives on while the activity is stopped, so a quick add or Undo from the home-screen
 * widget (the callback runs in the app's own process and writes through the same connection) used to leave Home, the list and the totals as they were until some other action rebuilt them.
 * The activity is moved to STOPPED (`CREATED` in the scenario's words), the widget's own callbacks write, and the activity is moved back to RESUMED: nothing is navigated or tapped.
 */
@RunWith(AndroidJUnit4::class)
class ForegroundRefreshTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val glanceId = AppWidgetId(4247)
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() {
        runBlocking {
            container.startFresh()
            container.write { ok -> ok.templates.save(Template("chai", "Chai", Money(2_000, Currency.INR), CategoryId("Food"), AccountId("cash"), "Tea stall")) }
        }
    }

    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun launch(): LedgerViewModel {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        compose.waitUntil(15_000) { ready() != null }
        return vm()
    }

    private fun vm(): LedgerViewModel { var v: LedgerViewModel? = null; scenario!!.onActivity { v = ViewModelProvider(it)[LedgerViewModel::class.java] }; return v!! }
    private fun ready() = runCatching { vm().state.value as? UiState.Ready }.getOrNull()
    private fun entryIds() = runBlocking { container.write { ok -> ok.db.schemaQueries.recentEntries(20).executeAsList().map { it.id } }!! }
    private fun away() { scenario!!.moveToState(Lifecycle.State.CREATED); compose.waitForIdle() }
    private fun back() { scenario!!.moveToState(Lifecycle.State.RESUMED) }
    private fun quickAddFromTheWidget() = runBlocking { QuickAddAction().onAction(context, glanceId, actionParametersOf(TEMPLATE_ID to "chai")) }

    @Test
    fun aQuickAddFromTheWidgetWhileTheScreenWasAwayIsInTheListAndTheTotalAsSoonAsTheAppIsBack() {
        launch()
        assertEquals(0, ready()!!.rows.size)
        away()
        quickAddFromTheWidget()
        assertEquals(0, ready()!!.rows.size, "the screen holds its old picture while it is away (this is the situation under test)")
        back()
        compose.waitUntil(10_000) { ready()?.rows?.size == 1 && ready()?.periodSpendMinor == 2_000L }
        // and it is drawn, with nothing opened or tapped
        compose.waitUntil(10_000) { compose.onAllNodesWithText("₹20.00", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun anUndoFromTheWidgetWhileTheScreenWasAwayTakesTheRowOutWhenTheAppIsBack() {
        launch()
        away()
        quickAddFromTheWidget()
        val id = entryIds().single()
        back()
        compose.waitUntil(10_000) { ready()?.rows?.size == 1 }
        away()
        runBlocking { UndoAction().onAction(context, glanceId, actionParametersOf(UNDO_ENTRY_ID to id)) }
        assertEquals(1, ready()!!.rows.size, "still the old picture while away")
        back()
        compose.waitUntil(10_000) { ready()?.rows?.isEmpty() == true && ready()?.periodSpendMinor == 0L }
    }

    @Test
    fun comingBackWithNothingChangedDoesNotRebuildTheScreen() {
        val vm = launch()
        away(); back()           // the first return may follow the index tidy-up the app does when it leaves: let it settle
        compose.waitForIdle(); Thread.sleep(1_500)
        val settled = vm.refreshCount
        away(); back()
        compose.waitForIdle(); Thread.sleep(1_500)
        assertEquals(settled, vm.refreshCount, "nothing was written while the screen was away, so nothing is rebuilt on return")
        away()
        quickAddFromTheWidget()
        back()
        compose.waitUntil(10_000) { vm.refreshCount > settled }
        assertTrue(ready()!!.rows.size == 1)
    }
}

package app.expenses

import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.storage.bulkLoad
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The list has a heading for each day, and more entries are loaded when one of the last ten *entries* (not list positions) comes into view. */
@RunWith(AndroidJUnit4::class)
class HomeListTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() { runBlocking { container.startFresh() } }
    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp() }

    @Test
    fun moreEntriesLoadOnlyWhenOneOfTheLastTenEntriesIsReachedEvenWithADayHeadingBetweenEveryTwo() {
        runBlocking { container.write { ok -> ok.db.bulkLoad(ok.driver) { for (i in 0 until 400) ok.db.schemaQueries.insertEntry("e$i", "EXPENSE", 1_700_000_000_000L + i * 86_400_000L, "bank", null, 100, "INR", null, "Shop $i", null, "manual", null) } } }
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        var vm: LedgerViewModel? = null
        scenario!!.onActivity { vm = ViewModelProvider(it)[LedgerViewModel::class.java] }
        compose.waitUntil(10_000) { (vm!!.state.value as? UiState.Ready)?.rows?.size == 50 }
        val rows = { (vm!!.state.value as UiState.Ready).rows.size }
        // each entry is on its own day, so list positions are about twice the entries: position 60 is entry 30 of 50, far from the last ten
        compose.onNodeWithTag("list").performScrollToIndex(60); compose.waitForIdle(); Thread.sleep(1_000)
        assertEquals(50, rows(), "not loaded yet: the position was counted as an entry")
        compose.onNodeWithTag("list").performScrollToIndex(96); compose.waitForIdle()
        compose.waitUntil(10_000) { rows() > 50 }
        assertTrue(rows() <= 150, "paged on, did not run away: ${rows()}")
    }
}

package app.expenses

import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.EncryptedDriver
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Destroys and re-creates the main screen many times with a lazy list of results showing, which is where an unexplained crash inside the
 * Compose runtime (a negative index in the slot table while an activity is destroyed) has twice been seen. This is the quickest known way
 * to try to reproduce it; see docs/PHASE1_PROGRESS.md.
 */
@RunWith(AndroidJUnit4::class)
class RecreateStressTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun resetApp() {
        runBlocking { container.close() }
        EncryptedDriver.reset(context)
    }

    @Before fun fresh() = resetApp()

    @After fun cleanUp() {
        scenario?.close()
        resetApp()
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun theMainScreenSurvivesBeingDestroyedAndRecreatedManyTimesWithSearchResultsShowing() {
        runBlocking {
            container.write { ok ->
                repeat(40) { i -> ok.store.save(Expense(EntryId("e$i"), System.currentTimeMillis() - i * 1_000L, AccountId("cash"), Money(100L + i, Currency.INR)), EntryDetails(merchant = "Shop $i")) }
            }
        }
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        waitFor("add")
        compose.onNodeWithTag("open_search").performClick()
        waitFor("search_text")
        compose.onNodeWithTag("search_text").performTextInput("shop")
        waitFor("search_results")
        repeat(RECREATIONS) { n ->
            scenario!!.recreate()
            waitFor("search_results")
            if (n % 25 == 0) android.util.Log.w("STRESS", "recreated $n times")
        }
    }

    private companion object { const val RECREATIONS = 150 }
}

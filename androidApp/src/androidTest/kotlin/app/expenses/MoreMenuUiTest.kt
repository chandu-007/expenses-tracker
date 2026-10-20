package app.expenses

import android.content.Intent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlainViewModel : ViewModel()

/** The More page, and the activity's view model factory that the system fingerprint prompt depends on. */
@RunWith(AndroidJUnit4::class)
class MoreMenuUiTest {
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
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun back() { scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() } }

    @Test
    fun backFromAPlaceOpenedInMoreReturnsToMoreAndBackFromMoreReturnsHome() {
        launch(); compose.openMore(); waitFor("more_page")
        for ((open, inside) in listOf("manage_accounts" to "accounts_list", "open_calendar" to "cal_prev", "open_appearance" to "appearance_back", "open_data" to "create_backup")) {
            tap(open); waitFor(inside)
            back(); waitFor("more_page")
            assertTrue(!has(inside), "$open closed")
        }
        back(); waitFor("add"); gone("more_page")
    }

    @Test
    fun theSearchBoxListsOnlyTheMatchingRowsAndClearingBringsEverythingBack() {
        launch(); compose.openMore(); waitFor("more_search")
        node("more_search").performTextInput("lock")
        compose.waitUntil(10_000) { has("open_security") && !has("manage_accounts") }
        node("more_search").performTextInput("zzzz")
        waitFor("more_nothing")
        tap("more_search_clear")
        waitFor("manage_accounts"); waitFor("open_security")
    }

    @Test
    fun toReviewAndBillsAreBigCardsOnlyWhileSomethingWaitsAndNeverShownTwice() {
        launch(); compose.openMore(); waitFor("open_inbox"); waitFor("open_bills")
        // nothing waiting: each is an ordinary row, once
        assertEquals(1, compose.onAllNodes(hasTestTag("open_inbox"), useUnmergedTree = true).fetchSemanticsNodes().size)
        scenario!!.onActivity { }
        runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", "Rs.250.00 debited from a/c XX1234 on 20-10-26 to VPA shop@okhdfc (UPI Ref 555555555555). Not you? Call 18002586161", 5_000) }
        launch(); compose.openMore(); waitFor("open_inbox")
        assertEquals(1, compose.onAllNodes(hasTestTag("open_inbox"), useUnmergedTree = true).fetchSemanticsNodes().size, "the card replaces the row")
    }

    @Test
    fun theFingerprintPromptCanBeBuiltOnTheMainActivity() {
        // The prompt keeps a view model of its own, created through the activity's default factory. The app once gave that factory only the screen's
        // view model, so turning on the app lock crashed. The tests elsewhere use a fake prompt, which never reached this.
        launch()
        var built = false
        scenario!!.onActivity { a -> BiometricAuthenticator().authenticate(a, "Test", null) { }; built = true }
        assertTrue(built)
        var other: ViewModel? = null
        scenario!!.onActivity { a -> other = ViewModelProvider(a)[PlainViewModel::class.java] }
        assertTrue(other != null, "a view model that is not the screen's is created by the usual factory")
        scenario!!.onActivity { a -> assertTrue(ViewModelProvider(a)[LedgerViewModel::class.java] === ViewModelProvider(a)[LedgerViewModel::class.java]) }
    }
}

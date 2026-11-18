package app.expenses

import android.content.Intent
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Guide
import app.expenses.core.domain.GuideStep
import app.expenses.core.domain.GuideTarget
import app.expenses.ui.TourSpots
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The interactive tour: it opens each real screen, can be skipped at any point, is chosen on a first install ("Show me around", five stops) and offered after an update, and reopens from More (every stop). */
@RunWith(AndroidJUnit4::class)
class GuideUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun fresh() { runBlocking { container.startFresh() } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun setting(key: String) = runBlocking { container.write { it.templates.setting(key) } }
    private fun put(key: String, value: String) { runBlocking { container.write { it.templates.putSetting(key, value) } } }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun gone(tag: String) = compose.waitUntil(10_000) { !has(tag) }
    private fun tap(tag: String) { waitFor(tag); val n = compose.onNodeWithTag(tag, useUnmergedTree = true); runCatching { n.performScrollTo() }; n.performClick() } // a tile on the More page may be below the fold
    private fun launch(action: String? = null): ActivityScenario<MainActivity> { compose.waitForIdle(); scenario?.close(); return ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).also { if (action != null) it.action = action }).also { scenario = it } }
    private fun firstVisit() = put(GuideSettings.SEEN, "")
    /** The whole tour, asked for from More (the home screen's month card is shown, so the welcome has been put away). */
    private fun startFullTour() { put("welcome_dismissed", "1"); launch(); waitFor("add"); compose.openMore(); tap("open_guide"); waitFor("guide") }
    private fun onStop(n: Int, of: Int = Guide.steps.size) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("guide_counter") and androidx.compose.ui.test.hasText("$n of $of", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    /** A tag that only the real screen a stop is about has. */
    private fun screenTag(t: GuideTarget?) = when (t) {
        null -> "month_spend" // home: the month card (the Add button is in the bar, which the More page has too)
        GuideTarget.ADD -> "amount"; GuideTarget.SEARCH -> "search_back"; GuideTarget.REPORTS -> "reports_back"; GuideTarget.CALENDAR -> "calendar_back"
        GuideTarget.CAPTURE -> "capture_back"; GuideTarget.INBOX -> "inbox_back"; GuideTarget.BILLS -> "bills_back"; GuideTarget.ACCOUNTS -> "back"
        GuideTarget.CATEGORIES -> "categories_back"; GuideTarget.BUDGET -> "budget_back"; GuideTarget.RECURRING -> "recurring_back"; GuideTarget.LOANS -> "loans_back"
        GuideTarget.NOTIFICATIONS -> "notif_back"; GuideTarget.PRIVACY -> "privacy_back"; GuideTarget.DATA -> "data_back"
        GuideTarget.SUBSCRIPTIONS -> "subscriptions_back"; GuideTarget.GOALS -> "goals_back"
        GuideTarget.MORE -> "open_data" // the More page
    }
    private val everyScreenTag get() = (GuideTarget.entries.map { screenTag(it) } + "month_spend").toSet()

    @Test
    fun theFullTourFromMoreOpensTheRealScreenForEveryStopAndOnlyThatOneAndHighlightsWhatItPointsAt() {
        startFullTour()
        for ((i, step) in Guide.steps.withIndex()) {
            onStop(i + 1)
            compose.onNodeWithTag("guide_title", useUnmergedTree = true).assertTextContains(step.title, substring = true)
            val own = screenTag(step.target)
            compose.waitUntil(10_000) { has(own) }
            // the screens the tour left are closed: none of the other stops' screens is showing behind it
            val others = everyScreenTag - own
            for (t in others) assertTrue(!has(t), "stop ${step.id} shows '$own', but '$t' is open too")
            step.spot?.let { s -> compose.waitUntil(10_000) { compose.runOnIdle { TourSpots.bounds[s] != null } } }
            if (i < Guide.steps.lastIndex) tap("guide_next") else tap("guide_done")
        }
        gone("guide")
        // a tour asked for by the person records nothing and does not lead into the guided setup (only the first-run tour does)
        assertEquals(Guide.VERSION.toString(), setting(GuideSettings.SEEN)); assertNull(setting(GuideSettings.OFF))
        waitFor("month_spend"); assertTrue(everyScreenTag.minus("month_spend").none { has(it) }, "finishing leaves the app on the home screen")
        assertTrue(!has("setup_screen"))
        launch(); waitFor("add"); assertTrue(!has("guide"), "finished: it does not come back")
    }

    @Test
    fun aFirstInstallIsOfferedNoTourOnItsOwnAndItIsRecordedAsSeenSoUpdatesOfferOnlyWhatIsNew() {
        firstVisit(); launch(); waitFor("welcome"); waitFor("welcome_tour")
        assertTrue(!has("guide"), "no tour on a first install: it is opt-in")
        compose.waitUntil(10_000) { setting(GuideSettings.SEEN) == Guide.VERSION.toString() }
        launch(); waitFor("welcome_tour"); assertTrue(!has("guide"))
    }

    @Test
    fun skipOnTheFirstStopOfTheChosenTourEndsItAtOnceAndItIsNotOfferedAgain() {
        firstVisit(); launch(); waitFor("welcome_tour")
        tap("welcome_tour"); waitFor("guide"); onStop(1, of = Guide.shortPlan().steps.size)
        tap("guide_skip"); gone("guide"); waitFor("add")
        assertEquals(Guide.VERSION.toString(), setting(GuideSettings.SEEN))
        launch(); waitFor("add"); assertTrue(!has("guide"))
    }

    @Test
    fun skippingFromInsideAScreenReturnsToTheHomeScreenAndRecordsIt() {
        startFullTour()
        val bills = Guide.steps.indexOfFirst { it.id == "bills" }
        repeat(bills) { i -> tap("guide_next"); onStop(i + 2) }
        waitFor("bills_back")
        tap("guide_skip"); gone("guide"); gone("bills_back"); waitFor("add")
        assertEquals(Guide.VERSION.toString(), setting(GuideSettings.SEEN))
    }

    @Test
    fun tryItEndsTheTourAndLeavesYouOnTheScreenItWasShowing() {
        startFullTour()
        val bills = Guide.steps.indexOfFirst { it.id == "bills" }
        repeat(bills) { i -> tap("guide_next"); onStop(i + 2) }
        waitFor("bills_back"); tap("guide_try"); gone("guide")
        waitFor("bills_back")
        assertEquals(Guide.VERSION.toString(), setting(GuideSettings.SEEN), "going to try it counts as having seen it")
    }

    @Test
    fun backReturnsToTheEarlierStopAndItsScreen() {
        startFullTour()
        tap("guide_next"); tap("guide_next"); onStop(3); waitFor("amount") // the add form
        tap("guide_back"); onStop(2); gone("amount"); waitFor("add")
    }

    @Test
    fun anUpdateOffersTheNewStopsAndTheChoiceNotToBeShownAfterUpdatesIsKept() {
        put(GuideSettings.SEEN, "0") // an older version of the tour was seen
        launch(); waitFor("guide"); waitFor("guide_whatsnew")
        tap("guide_never"); tap("guide_skip"); gone("guide")
        assertEquals("1", setting(GuideSettings.OFF)); assertEquals(Guide.VERSION.toString(), setting(GuideSettings.SEEN))
        put(GuideSettings.SEEN, "0") // and another update later
        launch(); waitFor("add"); assertTrue(!has("guide"), "the person asked not to be shown the tour after updates")
    }

    @Test
    fun anUpdateWithoutTheChoiceShowsWhatIsNewAndSkipDoesNotSwitchTheOptionOff() {
        put(GuideSettings.SEEN, "0")
        launch(); waitFor("guide_whatsnew"); tap("guide_skip"); gone("guide")
        assertNull(setting(GuideSettings.OFF))
    }

    @Test
    fun theTourIsNotShownToSomeoneWhoHasSeenThisVersionOrWhoStartedOver() {
        launch(); waitFor("add"); assertTrue(!has("guide")) // @Before wiped the ledger, which counts as knowing the app
        put(GuideSettings.SEEN, Guide.VERSION.toString())
        launch(); waitFor("add"); assertTrue(!has("guide"))
    }

    @Test
    fun moreOffersTheTourAnytimeStartingFromHomeWithoutTheUpdateChoiceAndClosingItChangesNothingStored() {
        put("welcome_dismissed", "1")
        put(GuideSettings.SEEN, "0"); put(GuideSettings.OFF, "1") // an old version seen, and updates switched off: nothing is shown on its own
        launch(); waitFor("add"); assertTrue(!has("guide")); compose.openMore(); tap("open_guide"); waitFor("guide")
        waitFor("month_spend") // the More page was closed: the tour starts on the home screen, where the month card is (the Add button is in the bar of the More page too)
        assertTrue(!has("guide_never"), "no update choice when it was asked for")
        tap("guide_skip"); gone("guide")
        assertEquals("0", setting(GuideSettings.SEEN), "looking at the tour by choice records nothing"); assertEquals("1", setting(GuideSettings.OFF))
    }

    @Test
    fun openingTheAppToAddAnExpenseNeverLandsInTheTourAndTheTourWaitsForAPlainLaunch() {
        put(GuideSettings.SEEN, "0") // an update is waiting to be shown
        launch(MainActivity.ACTION_ADD_EXPENSE)
        waitFor("amount"); assertTrue(!has("guide"), "a widget or tile tap goes straight to the add form")
        assertEquals("0", setting(GuideSettings.SEEN), "and the tour is not counted as seen")
        launch(); waitFor("guide")
    }

    @Test
    fun aBillNotificationOpensBillsNotTheTour() {
        put(GuideSettings.SEEN, "0"); launch(MainActivity.ACTION_OPEN_BILLS)
        waitFor("bills_back"); assertTrue(!has("guide"))
        launch(); waitFor("guide")
    }

    @Test
    fun aTapOnTheWidgetWhileTheTourIsShowingTakesYouToTheAddFormAndTheTourWaits() {
        put(GuideSettings.SEEN, "0"); launch(); waitFor("guide")
        scenario!!.onActivity { a -> a.startActivity(Intent(a, MainActivity::class.java).setAction(MainActivity.ACTION_ADD_EXPENSE).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
        waitFor("amount"); gone("guide")
        assertEquals("0", setting(GuideSettings.SEEN))
    }

    @Test
    fun someoneWhoSawTheFirstTourIsOfferedOnlyTheStopsAddedSinceAndCanAskForTheWholeTour() {
        put(GuideSettings.SEEN, "1")
        launch(); waitFor("guide_whatsnew")
        val fresh = Guide.steps.filter { it.since > 1 }
        assertTrue(fresh.isNotEmpty())
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("guide_counter") and androidx.compose.ui.test.hasText("1 of ${fresh.size}", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("guide_title", useUnmergedTree = true).assertTextContains(fresh.first().title, substring = true)
        tap("guide_show_all"); onStop(1)
        assertTrue(Guide.steps.size > fresh.size)
        tap("guide_skip"); gone("guide")
        assertEquals(Guide.VERSION.toString(), setting(GuideSettings.SEEN))
    }

    @Test
    fun someoneWhoSawVersionTwoIsOfferedExactlyTheNewStopsInTourOrder() {
        put(GuideSettings.SEEN, "2")
        launch(); waitFor("guide_whatsnew")
        val fresh = Guide.steps.filter { it.since > 2 }
        assertEquals(listOf("subscriptions", "goals", "smart_alerts", "sharing", "year"), fresh.map { it.id })
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("guide_counter") and androidx.compose.ui.test.hasText("1 of ${fresh.size}", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        for ((i, step) in fresh.withIndex()) {
            compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("guide_counter") and androidx.compose.ui.test.hasText("${i + 1} of ${fresh.size}", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("guide_title", useUnmergedTree = true).assertTextContains(step.title, substring = true)
            compose.waitUntil(10_000) { has(screenTag(step.target)) }
            if (i < fresh.lastIndex) tap("guide_next") else tap("guide_done")
        }
        gone("guide"); assertEquals(Guide.VERSION.toString(), setting(GuideSettings.SEEN))
    }
}


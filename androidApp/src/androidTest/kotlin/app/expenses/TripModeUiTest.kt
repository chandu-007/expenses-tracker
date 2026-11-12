package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.SearchDates
import app.expenses.core.domain.Trip
import app.expenses.core.domain.formatted
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Refund
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Travel mode on a device: a trip made in the form (with the Material3 date picker), an expense dated inside it tagged by the add form and the tag removable for good, Home's card, the summary's
 * figures, "See all entries", "Tag earlier entries" with its Undo, the offer after creating a trip, overlap and form messages, deleting a trip, ending it today, a captured text, Hide amounts.
 * Invented data only. Days are relative to today (local), so the test runs on any date.
 */
@RunWith(AndroidJUnit4::class)
class TripModeUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val inr = Currency.INR
    private val zone = ZoneId.systemDefault()

    @Before fun fresh() { runBlocking { container.startFresh() } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(15_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(15_000) { !has(tag) }
    private fun hasText(text: String) = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitText(text: String) = compose.waitUntil(15_000) { hasText(text) }
    /** A tap on a control that is there can be lost while the screen is still settling: it is repeated until what it does has happened. */
    private fun tapUntil(tag: String, done: () -> Boolean) {
        waitFor(tag)
        var tries = 0
        while (!done() && tries < 6) { runCatching { compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick() }; tries++; runCatching { compose.waitUntil(2_500) { done() } } }
        compose.waitUntil(5_000) { done() }
    }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick() }
    private fun type(tag: String, text: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).performTextClearance(); compose.onNodeWithTag(tag, useUnmergedTree = true).performTextInput(text) }
    private fun textOf(tag: String): String = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.let { c ->
        (c.getOrNull(SemanticsProperties.EditableText)?.text ?: c.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text })
    }
    /** The text of a control whose label is a child (a chip, a button): read from the merged tree. */
    private fun mergedText(tag: String): String = compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun <T> db(block: (AppContainer.Opened.Ok) -> T): T = runBlocking { container.write(block) }!!

    private fun epochDay(offset: Long) = LocalDate.now().plusDays(offset).toEpochDay()
    private fun at(offset: Long, hour: Int = 12) = LocalDate.now().plusDays(offset).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun noonOfThisMonth(day: Int) = LocalDate.now().withDayOfMonth(day).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private fun trip(name: String, from: Long, to: Long, tag: String = "Trip: $name", enabled: Boolean = true, budget: Long? = null) =
        db { ok -> ok.trips.save(Trip(name.lowercase(), name, tag, epochDay(from), epochDay(to), enabled, budget)) }
    private fun spend(id: String, merchant: String, at: Long, rupees: Long = 100, category: String = "Food", tripTag: Boolean = false, vararg tags: String) = db { ok ->
        ok.store.save(
            Expense(EntryId(id), at, AccountId("cash"), Money(rupees * 100, inr), listOf(CategorySplit(CategoryId(category), Money(rupees * 100, inr)))),
            EntryDetails(merchant = merchant, tags = tags.toList().ifEmpty { null }, tripTag = tripTag),
        )
    }
    private fun tagsOf(id: String) = db { ok -> ok.store.tagsOf(EntryId(id)) }
    private fun openTrips() { compose.openMore(); tap("open_trips"); waitFor("trips_list") }

    /** Material3 gives a day cell the whole date as its text ("Saturday, October 10, 2026"), not just its number. */
    private fun dayCell(day: Int) = SemanticsMatcher("day $day") { n ->
        n.config.contains(SemanticsActions.OnClick) && n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { Regex(" $day, \\d{4}$").containsMatchIn(it.text) }
    }

    /** Opens the picker of [field] ("trip_start" or "trip_end") and chooses [day] of the month it shows (the current one, or the one of the first day already chosen). */
    private fun pick(field: String, day: Int) {
        tap(field); waitFor("trip_date_ok")
        compose.waitUntil(15_000) { compose.onAllNodes(dayCell(day)).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(dayCell(day)).onFirst().performClick()
        tap("trip_date_ok"); waitGone("trip_date_ok")
    }

    // ---- the form ----

    @Test
    fun aTripMadeInTheFormIsKeptWithTheTagTheNameSuggestedAndAppearsInTheList() {
        launch(); openTrips(); waitFor("trips_empty")
        tap("trip_new"); waitFor("trip_name")
        type("trip_name", "Goa")
        compose.waitUntil(10_000) { textOf("trip_tag") == "Trip: Goa" }
        type("trip_budget", "1500")
        pick("trip_start", 10); pick("trip_end", 12)
        waitText("3 days")
        tap("trip_save"); waitFor("trip_row_Goa")
        val t = db { it.trips.all().single() }
        assertEquals("Goa", t.name); assertEquals("Trip: Goa", t.tag); assertTrue(t.enabled)
        assertEquals(LocalDate.now().withDayOfMonth(10).toEpochDay(), t.startDay); assertEquals(LocalDate.now().withDayOfMonth(12).toEpochDay(), t.endDay)
        assertEquals(150_000L, t.dailyBudgetMinor)
        compose.onNodeWithTag("trip_status_Goa", useUnmergedTree = true).fetchSemanticsNode()
    }

    @Test
    fun theFormSaysWhatIsWrongInPlainWordsUnderTheFieldItBelongsTo() {
        launch(); openTrips(); tap("trip_new")
        tap("trip_save"); waitFor("trip_problem_name")
        compose.onNodeWithTag("trip_problem_name", useUnmergedTree = true).assertTextEquals("Give the trip a name, for example Goa.")
        type("trip_name", "Goa"); waitGone("trip_problem_name")
        tap("trip_save"); waitFor("trip_problem_dates")
        compose.onNodeWithTag("trip_problem_dates", useUnmergedTree = true).assertTextEquals("Choose the first day of the trip.")
        pick("trip_start", 12); pick("trip_end", 10)
        tap("trip_save")
        compose.waitUntil(10_000) { has("trip_problem_dates") && textOf("trip_problem_dates").startsWith("The last day is before") }
        compose.onNodeWithTag("trip_problem_dates", useUnmergedTree = true).assertTextEquals("The last day is before the first day. Change one of them.")
        type("trip_tag", "x".repeat(31)); pick("trip_end", 14)
        tap("trip_save"); waitFor("trip_problem_tag")
        compose.onNodeWithTag("trip_problem_tag", useUnmergedTree = true).assertTextEquals("A tag can have up to 30 characters. Make it shorter.")
        assertTrue(db { it.trips.count() } == 0, "nothing was kept")
    }

    @Test
    fun aTripOverlappingATripThatIsOnIsRefusedAndNamesTheOtherOne() {
        db { ok -> ok.trips.save(Trip("goa", "Goa", "Trip: Goa", LocalDate.now().withDayOfMonth(10).toEpochDay(), LocalDate.now().withDayOfMonth(14).toEpochDay())) }
        launch(); openTrips(); tap("trip_new")
        type("trip_name", "Mumbai"); pick("trip_start", 12); pick("trip_end", 16)
        tap("trip_save"); waitFor("trip_problem_dates")
        val a = LocalDate.now().withDayOfMonth(10).toEpochDay(); val b = LocalDate.now().withDayOfMonth(14).toEpochDay()
        compose.onNodeWithTag("trip_problem_dates", useUnmergedTree = true).assertTextEquals("These dates overlap your trip \"Goa\" (${SearchDates.summary(a, b)}), which is on. Change the dates, or turn that trip off.")
        assertEquals(1, db { it.trips.count() })
        // turned off, the new trip may share the days
        tap("trip_enabled_row"); tap("trip_save"); waitFor("trip_row_Mumbai")
        assertEquals(false, db { ok -> ok.trips.all().single { it.name == "Mumbai" }.enabled })
    }

    // ---- the add form and the tag that stays removed ----

    @Test
    fun anExpenseAddedInsideTheTripShowsTheChipIsTaggedWhenSavedAndTheTagStaysOffOnceRemovedWhateverIsEditedAfter() {
        trip("Goa", -1, 1)
        launch(); tap("add"); waitFor("trip_chip")
        compose.waitUntil(10_000) { mergedText("trip_chip").contains("Tagged for Goa trip") }
        type("amount", "250"); type("note", "Lunch"); tap("save"); waitFor("add")
        val id = db { it.db.schemaQueries.recentEntries(5).executeAsList().first { r -> r.amount_minor == 25_000L }.id }
        assertEquals(listOf("Trip: Goa"), tagsOf(id))

        // an edit of the note leaves the tag; the tag taken off in the editor stays off through the next edits
        tapUntil("row_Lunch") { has("note") }; type("note", "Lunch at the beach"); tap("save"); waitFor("add")
        assertEquals(listOf("Trip: Goa"), tagsOf(id))
        tapUntil("row_Lunch at the beach") { has("tag_chip_Trip: Goa") }; tapUntil("tag_chip_Trip: Goa") { !has("tag_chip_Trip: Goa") }; tap("save"); waitFor("add")
        assertTrue(tagsOf(id).isEmpty(), "taken off")
        tapUntil("row_Lunch at the beach") { has("note") }; type("note", "Beach lunch"); tap("save"); waitFor("add")
        assertTrue(tagsOf(id).isEmpty(), "and it stays off after the note is edited: the tag is added once, when the entry is made")
        assertTrue(!has("trip_chip"), "the editor of an entry that exists does not offer it")
    }

    @Test
    fun theChipTakesTheTagOffForThisEntryAndBringsItBack() {
        trip("Goa", -1, 1)
        launch(); tap("add"); waitFor("trip_chip")
        tap("trip_chip"); compose.waitUntil(10_000) { mergedText("trip_chip").startsWith("Tag it for") }
        type("amount", "75"); type("note", "Taxi"); tap("save"); waitFor("add")
        val taxi = db { it.db.schemaQueries.recentEntries(5).executeAsList().first { r -> r.amount_minor == 7_500L }.id }
        assertTrue(tagsOf(taxi).isEmpty(), "the person said no for this one")
        tap("add"); waitFor("trip_chip"); tap("trip_chip"); tap("trip_chip") // off and on again
        compose.waitUntil(10_000) { mergedText("trip_chip").startsWith("Tagged for") }
        type("amount", "60"); type("note", "Coffee"); tap("save"); waitFor("add")
        assertEquals(listOf("Trip: Goa"), tagsOf(db { it.db.schemaQueries.recentEntries(5).executeAsList().first { r -> r.amount_minor == 6_000L }.id }))
    }

    @Test
    fun noChipIsOfferedOutsideTheTripOrWhenTheTripIsOffAndNothingIsTagged() {
        trip("Past", -10, -5); trip("Off", -1, 1, enabled = false)
        launch(); tap("add"); waitFor("amount")
        assertTrue(!has("trip_chip"), "no trip that is on covers today")
        type("amount", "40"); type("note", "Snack"); tap("save"); waitFor("add")
        assertTrue(tagsOf(db { it.db.schemaQueries.recentEntries(5).executeAsList().single().id }).isEmpty())
    }

    // ---- Home ----

    @Test
    fun homeShowsOneCompactLineAboutTheTripThatIsOnTodayAndNothingWhenNoTripIsOn() {
        trip("Goa", -1, 2)
        spend("a", "Dinner", at(0), 100, tripTag = true); spend("b", "Boat", at(-1), 150, tripTag = true); spend("c", "Elsewhere", at(-30), 999)
        launch(); waitFor("home_trip_card")
        val spent = Money(25_000, inr).formatted()
        compose.onNodeWithTag("home_trip_line", useUnmergedTree = true).assertTextEquals("Trip: Goa · day 2 of 4 · $spent spent")
        tap("home_trip_card"); waitFor("trip_summary"); waitFor("trip_total")
        compose.onNodeWithTag("trip_total", useUnmergedTree = true).assertTextEquals(spent)
        tap("trip_summary_back"); waitFor("home_trip_card")

        // a trip that is over, one still to come and one that is switched off have no card
        db { ok -> ok.trips.save(ok.trips.get("goa")!!.copy(enabled = false)) }
        launch(); waitFor("list"); compose.waitForIdle(); assertTrue(!has("home_trip_card"), "a trip that is off has no card")
        db { ok -> ok.trips.save(ok.trips.get("goa")!!.copy(enabled = true, startDay = epochDay(-9), endDay = epochDay(-4))) }
        launch(); waitFor("list"); compose.waitForIdle(); assertTrue(!has("home_trip_card"), "a trip that is over has no card")
        db { ok -> ok.trips.save(ok.trips.get("goa")!!.copy(startDay = epochDay(3), endDay = epochDay(8))) }
        launch(); waitFor("list"); compose.waitForIdle(); assertTrue(!has("home_trip_card"), "a trip still to come has no card")
        db { ok -> ok.trips.save(ok.trips.get("goa")!!.copy(startDay = epochDay(0), endDay = epochDay(0))) }
        launch(); waitFor("home_trip_card")
        compose.onNodeWithTag("home_trip_line", useUnmergedTree = true).assertTextContains("day 1 of 1", substring = true)
    }

    @Test
    fun theCardIsNotANudgeSoOneNudgeIsStillAllThatShowsAndTheOthersAreOneLine() {
        trip("Goa", -1, 1)
        for (i in 1..3) db { ok -> ok.store.save(Expense(EntryId("nocat$i"), at(-3), AccountId("cash"), Money(10_000, inr), emptyList()), EntryDetails(merchant = "No category $i")) }
        runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", bankText(LocalDate.now(), "628374659777"), System.currentTimeMillis()) }
        launch(); waitFor("home_trip_card"); waitFor("inbox_banner")
        assertTrue(!has("categorise_nudge"), "the first nudge of the order is the only card")
        waitFor("home_more_nudges"); assertTrue(hasText("Also waiting"), "what else waits is the one line under it, and the trip is not in it")
        assertTrue(!textOf("home_more_nudges").contains("Trip", ignoreCase = true))
    }

    @Test
    fun withHideAmountsOnTheCardAndTheSummaryShowNoAmount() {
        trip("Goa", -1, 1)
        spend("a", "Dinner", at(0), 123, tripTag = true)
        db { it.templates.putSetting("hide_amounts", "1") }
        launch(); waitFor("home_trip_card")
        val line = textOf("home_trip_line")
        assertTrue("••••" in line && !line.contains("123"), "the amount is dots: $line")
        tap("home_trip_card"); waitFor("trip_total")
        assertTrue("••••" in textOf("trip_total") && !textOf("trip_total").contains("123"), textOf("trip_total"))
    }

    // ---- the summary ----

    @Test
    fun theSummaryAddsUpTheTripsDaysCategoriesAverageAndBudgetAndAReadoutPerBar() {
        trip("Goa", -2, 2, budget = 100_000)
        spend("f1", "Cafe", at(-2), 400, "Food", tripTag = true); spend("s1", "Cab", at(-2), 3_000, "Transport", tripTag = true)
        spend("f2", "Thali", at(-1), 600, "Food", tripTag = true); spend("f3", "Chai", at(0), 200, "Food", tripTag = true)
        db { ok -> ok.store.save(Refund(EntryId("r1"), at(0, 15), AccountId("cash"), Money(50_000, inr), EntryId("s1")), EntryDetails(merchant = "Cab refund", tripTag = true)) }
        spend("other", "Not this trip", at(0), 777, "Food") // inside the days but not tagged
        spend("far", "Old", at(-40), 888, "Food", tripTag = true) // tagged, dated outside the days: in the total, shown apart
        launch(); openTrips(); tap("trip_open_Goa"); waitFor("trip_total")
        compose.onNodeWithTag("trip_total", useUnmergedTree = true).assertTextEquals(Money(458_800, inr).formatted())
        compose.onNodeWithTag("trip_outside", useUnmergedTree = true).assertTextEquals("Outside the trip days ${Money(88_800, inr).formatted()} · 1 entry")
        compose.onNodeWithTag("trip_days", useUnmergedTree = true).assertTextContains("Day 3 of 5", substring = true)
        compose.onNodeWithTag("trip_average", useUnmergedTree = true).assertTextContains("Average ${Money(123_333, inr).formatted()} a day", substring = true)
        compose.onNodeWithTag("trip_average", useUnmergedTree = true).assertTextContains("6 entries", substring = true)
        compose.onNodeWithTag("trip_budget_line", useUnmergedTree = true).assertTextContains("Daily budget ${Money(100_000, inr).formatted()}", substring = true)
        compose.onNodeWithTag("trip_budget_status", useUnmergedTree = true).assertTextEquals("${Money(70_000, inr).formatted()} over")
        assertTrue(mergedText("trip_cat_Transport").contains(Money(250_000, inr).formatted()), mergedText("trip_cat_Transport"))
        assertTrue(mergedText("trip_cat_Food").contains(Money(208_800, inr).formatted()), mergedText("trip_cat_Food"))
        for (i in 0 until 5) waitFor("trip_bar_$i")
        assertTrue(!has("trip_bar_5"))
        tap("trip_bar_0"); compose.waitUntil(10_000) { textOf("trip_bar_readout").contains(Money(340_000, inr).formatted()) } // the first day: 400 and 3,000
        tap("trip_bar_2"); compose.waitUntil(10_000) { textOf("trip_bar_readout").contains("(today)") && textOf("trip_bar_readout").contains(Money(30_000, inr).formatted()) } // today: 200 less the refund of 500
    }

    @Test
    fun seeAllEntriesOpensSearchWithTheTripsTagAndBackReturnsToTheSummary() {
        trip("Goa", -2, 2)
        spend("in1", "Tagged lunch", at(0), 100, tripTag = true); spend("in2", "Tagged boat", at(-1), 200, tripTag = true)
        spend("untagged", "Untagged shop", at(0), 300); spend("far", "Tagged but far", at(-40), 400, "Food", false, "Trip: Goa")
        launch(); openTrips(); tap("trip_open_Goa"); waitFor("trip_see_all"); tap("trip_see_all")
        waitFor("search_text"); waitFor("search_active_chip_tag")
        assertTrue(!has("search_active_chip_dates"), "the tag alone: the same entries as the page's total")
        waitFor("result_in1"); waitFor("result_in2"); waitFor("result_far")
        assertTrue(!has("result_untagged"), "only what carries the tag")
        tap("search_back"); waitFor("trip_summary")
    }

    // ---- tagging earlier entries ----

    @Test
    fun tagEarlierEntriesCountsThemTagsOnlyExpensesWithoutTheTagAndUndoTakesThemOffExactly() {
        trip("Goa", -2, 2)
        spend("e1", "Early one", at(-2), 100); spend("e2", "Early two", at(-1), 100, "Food", false, "Food run"); spend("e3", "Early three", at(0), 100)
        spend("has", "Already tagged", at(0), 100, tripTag = true); spend("far", "Outside", at(-40), 100)
        db { ok -> ok.store.save(app.expenses.core.domain.ledger.Income(EntryId("inc"), at(0), AccountId("cash"), Money(500_000, inr), CategoryId("income:other")), EntryDetails(merchant = "Pay")) }
        launch(); openTrips(); tap("trip_open_Goa"); waitFor("trip_tag_earlier")
        compose.waitUntil(10_000) { mergedText("trip_tag_earlier") == "Tag 3 earlier entries" }
        tap("trip_tag_earlier"); waitFor("trip_tag_ok")
        compose.onNodeWithTag("trip_tag_title", useUnmergedTree = true).assertTextEquals("Tag 3 earlier entries from these dates?")
        tap("trip_tag_ok")
        waitText("Tagged 3 entries")
        for (id in listOf("e1", "e2", "e3")) assertTrue("Trip: Goa" in tagsOf(id), id)
        assertEquals(listOf("Food run", "Trip: Goa"), tagsOf("e2"))
        assertTrue(tagsOf("far").isEmpty() && tagsOf("inc").isEmpty(), "outside the days, and not an expense")
        compose.waitUntil(15_000) { !has("trip_tag_earlier") }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(15_000) { has("trip_tag_earlier") }
        assertTrue(tagsOf("e1").isEmpty() && tagsOf("e3").isEmpty()); assertEquals(listOf("Food run"), tagsOf("e2"))
        assertEquals(listOf("Trip: Goa"), tagsOf("has"), "the one that had the tag before keeps it")
        compose.waitUntil(10_000) { mergedText("trip_tag_earlier") == "Tag 3 earlier entries" }
    }

    @Test
    fun makingATripOffersToTagTheEarlierEntriesOnItsDaysAndNothingIsTaggedUnlessTheAnswerIsYes() {
        spend("p1", "Earlier one", noonOfThisMonth(10), 100); spend("p2", "Earlier two", noonOfThisMonth(11), 100); spend("p3", "Not in the days", noonOfThisMonth(20), 100)
        launch(); openTrips(); tap("trip_new")
        type("trip_name", "Goa"); pick("trip_start", 10); pick("trip_end", 12)
        tap("trip_save"); waitFor("trip_offer_title")
        compose.onNodeWithTag("trip_offer_title", useUnmergedTree = true).assertTextEquals("Tag 2 earlier entries from these dates?")
        assertTrue(tagsOf("p1").isEmpty() && tagsOf("p2").isEmpty(), "asked, not done")
        tap("trip_offer_no"); waitGone("trip_offer_title")
        assertTrue(tagsOf("p1").isEmpty() && tagsOf("p2").isEmpty(), "not now: nothing is tagged")
        // growing the days offers only the days that were added: the 20th
        tap("trip_edit_Goa"); waitFor("trip_end"); pick("trip_end", 20)
        tap("trip_save"); waitFor("trip_offer_title")
        compose.onNodeWithTag("trip_offer_title", useUnmergedTree = true).assertTextEquals("Tag 1 earlier entry from these dates?")
        tap("trip_offer_yes"); waitText("Tagged 1 entry")
        assertEquals(listOf("Trip: Goa"), tagsOf("p3")); assertTrue(tagsOf("p1").isEmpty() && tagsOf("p2").isEmpty(), "the 10th and 11th were in the days when it was made, and the person said not now")
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(15_000) { tagsOf("p3").isEmpty() }
    }

    // ---- delete, end today ----

    @Test
    fun deletingATripAsksAndKeepsTheTagsOnTheEntries() {
        trip("Goa", -1, 1)
        spend("a", "Dinner", at(0), 100, tripTag = true)
        launch(); openTrips(); tap("trip_delete_Goa"); waitFor("trip_delete_note")
        compose.onNodeWithTag("trip_delete_note", useUnmergedTree = true).assertTextContains("stays on the entries it was put on", substring = true)
        tap("trip_delete_keep"); waitGone("trip_delete_note"); assertEquals(1, db { it.trips.count() })
        tap("trip_delete_Goa"); tap("trip_delete_ok"); waitGone("trip_row_Goa")
        assertEquals(0, db { it.trips.count() })
        assertEquals(listOf("Trip: Goa"), tagsOf("a"))
        waitFor("trips_empty")
    }

    @Test
    fun endTripTodayAsksThenMakesTodayTheLastDay() {
        trip("Goa", -1, 3)
        launch(); openTrips(); tap("trip_open_Goa"); waitFor("trip_end_today"); tap("trip_end_today"); waitFor("trip_end_ok")
        tap("trip_end_cancel"); waitGone("trip_end_ok"); assertEquals(epochDay(3), db { it.trips.get("goa")!!.endDay })
        tap("trip_end_today"); tap("trip_end_ok")
        compose.waitUntil(15_000) { db { it.trips.get("goa")!!.endDay } == epochDay(0) }
        waitGone("trip_end_today")
        compose.onNodeWithTag("trip_days", useUnmergedTree = true).assertTextContains("Day 2 of 2", substring = true)
    }

    // ---- a captured text confirmed inside the dates ----

    private fun bankText(on: LocalDate, ref: String, rupees: String = "1,250.00") =
        "Rs.$rupees debited from A/c XX1234 on ${on.format(DateTimeFormatter.ofPattern("dd-MM-yy"))} to VPA shop@okhdfc (UPI Ref $ref). Not you? Call 18002586161"

    @Test
    fun aCapturedTextAddedFromToReviewInsideTheTripIsTaggedAndOneOutsideIsNot() {
        trip("Goa", -1, 1)
        runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", bankText(LocalDate.now(), "628374659001"), System.currentTimeMillis()) }
        runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", bankText(LocalDate.now().minusDays(30), "628374659002", "310.00"), System.currentTimeMillis() - 1_000) }
        launch(); compose.openMore(); tap("open_inbox"); waitFor("inbox_back")
        val items = db { ok -> ok.inbox.list().associate { it.amountMinor to it.id } }
        waitFor("inbox_${items.getValue(125_000L)}")
        tap("inbox_add_${items.getValue(125_000L)}")
        compose.waitUntil(15_000) { db { ok -> ok.db.schemaQueries.recentEntries(10).executeAsList().any { it.amount_minor == 125_000L } } }
        waitFor("inbox_${items.getValue(31_000L)}"); tap("inbox_add_${items.getValue(31_000L)}")
        compose.waitUntil(15_000) { db { ok -> ok.db.schemaQueries.recentEntries(10).executeAsList().any { it.amount_minor == 31_000L } } }
        val rows = db { ok -> ok.db.schemaQueries.recentEntries(10).executeAsList() }
        assertEquals(listOf("Trip: Goa"), tagsOf(rows.first { it.amount_minor == 125_000L }.id), "dated today: inside")
        assertTrue(tagsOf(rows.first { it.amount_minor == 31_000L }.id).isEmpty(), "dated 30 days ago, by the bank's own date: outside")
    }

    @Test
    fun addAllTagsTheClearOnesThatAreInsideTheTrip() {
        trip("Goa", -1, 1)
        db { it.templates.putSetting("capture_acct:HDFC Bank:1234", "bank") }
        runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", bankText(LocalDate.now(), "628374659011", "100.00"), System.currentTimeMillis()) }
        runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", bankText(LocalDate.now().minusDays(20), "628374659012", "200.00"), System.currentTimeMillis() - 1_000) }
        launch(); compose.openMore(); tap("open_inbox"); waitFor("inbox_back")
        tap("inbox_add_all"); waitFor("inbox_add_all_ok"); tap("inbox_add_all_ok")
        compose.waitUntil(20_000) { db { ok -> ok.db.schemaQueries.recentEntries(10).executeAsList().size == 2 } }
        val rows = db { ok -> ok.db.schemaQueries.recentEntries(10).executeAsList() }
        assertEquals(listOf("Trip: Goa"), tagsOf(rows.first { it.amount_minor == 10_000L }.id))
        assertTrue(tagsOf(rows.first { it.amount_minor == 20_000L }.id).isEmpty())
    }

    @Test
    fun aTagCannotBelongToTwoTripsAndTheFormNamesTheOtherOne() {
        trip("Goa", -20, -15) // past
        launch(); openTrips(); tap("trip_new")
        type("trip_name", "Goa again"); type("trip_tag", " trip: GOA "); pick("trip_start", 10); pick("trip_end", 12)
        tap("trip_save"); waitFor("trip_problem_tag")
        assertTrue(textOf("trip_problem_tag").startsWith("The tag \"Trip: Goa\" already belongs to your trip \"Goa\""), textOf("trip_problem_tag"))
        assertEquals(1, db { it.trips.count() })
    }

    @Test
    fun aTagThatIsAlreadyOnEntriesIsSaidBeforeTheTripIsKeptAndSavedOnlyOnUseItAnyway() {
        spend("old1", "Earlier one", at(-50), 100, "Food", false, "Holiday"); spend("old2", "Earlier two", at(-60), 100, "Food", false, "holiday")
        launch(); openTrips(); tap("trip_new")
        type("trip_name", "Holiday"); type("trip_tag", "Holiday"); pick("trip_start", 10); pick("trip_end", 12)
        tap("trip_save"); waitFor("trip_tag_used")
        assertEquals("The tag \"Holiday\" is already on 2 entries. They will count in this trip's total.", textOf("trip_tag_used"))
        assertEquals(0, db { it.trips.count() }, "nothing is kept until the person confirms")
        tap("trip_tag_choose_other"); waitGone("trip_tag_used"); assertEquals(0, db { it.trips.count() })
        tap("trip_save"); waitFor("trip_tag_used"); tap("trip_tag_use_anyway"); waitFor("trip_row_Holiday")
        assertEquals("Holiday", db { it.trips.all().single().tag })
        tap("trip_open_Holiday"); waitFor("trip_outside")
        compose.onNodeWithTag("trip_outside", useUnmergedTree = true).assertTextEquals("Outside the trip days ${Money(20_000, inr).formatted()} · 2 entries")
    }
}

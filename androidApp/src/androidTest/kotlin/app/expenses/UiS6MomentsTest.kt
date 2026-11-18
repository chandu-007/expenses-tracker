package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.NudgePause
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.Bill
import app.expenses.core.storage.BillStatus
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * UI slice S6 (moments): the drawn tick on Save and Paid, the Calendar's no-spend ring and its summary line, the occasional milestone card (opt-in, once, the caps),
 * and Pause nudges (suggestions wait, a due bill does not, kept over a restart). Nothing here may say "streak".
 */
@RunWith(AndroidJUnit4::class)
class UiS6MomentsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val inr = Currency.INR
    private var scenario: ActivityScenario<MainActivity>? = null
    private val zone = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.now()
    private val todayEpochDay get() = today.toEpochDay()

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1"); it.templates.putSetting("first_week_dismissed", "1"); it.templates.putSetting("category_look_notice", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String, ms: Long = 20_000) = compose.waitUntil(ms) { has(tag) }
    private fun gone(tag: String, ms: Long = 20_000) = compose.waitUntil(ms) { !has(tag) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun tap(tag: String) { waitFor(tag); runCatching { node(tag).performScrollTo() }; node(tag).performClick() }
    private fun launch() { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add") }
    private fun textOf(tag: String) = node(tag).fetchSemanticsNode().config.let { c -> c.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty() }
    private fun descriptionOf(tag: String) = node(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
    private fun setting(key: String) = runBlocking { container.write { it.templates.setting(key) } }
    private fun put(key: String, value: String) = runBlocking { container.write { it.templates.putSetting(key, value) } }
    private fun millisOf(date: LocalDate) = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    /** Every word and description on screen now, in one string. */
    private fun everyWord(): String {
        fun walk(n: SemanticsNode): List<String> =
            (n.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()) + (n.config.getOrNull(SemanticsProperties.ContentDescription) ?: emptyList()) + n.children.flatMap { walk(it) }
        return walk(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()).joinToString("\n")
    }
    private fun assertNoStreak(where: String) = assertFalse(everyWord().contains("streak", ignoreCase = true), "no streak word on $where")

    private fun spendOn(id: String, date: LocalDate, rupees: Long = 100) = runBlocking {
        container.write { ok -> ok.store.save(Expense(EntryId(id), millisOf(date), AccountId("cash"), Money(rupees * 100, inr)), EntryDetails(merchant = id)) }
    }
    private fun uncategorised(n: Int) = repeat(n) { i -> spendOn("u$i", today.minusDays(40L + i)) }
    private fun dueBill() = runBlocking {
        container.write { ok ->
            ok.bills.add(Bill("due1", "SMS", "AX-BESCOM", "BESCOM", "UTILITY", "BESCOM bill", 184_250, null, "INR", today.plusDays(1).toString(), null, null, false, System.currentTimeMillis(), null, BillStatus.TRACKED, null, null, "key-due1"))
        }
    }
    private fun openNotifications() { compose.openMore(); tap("open_notifications"); waitFor("pause_nudges_switch") }
    private fun backToHome() { tap("notif_back"); compose.closeMore(); waitFor("add") }
    private fun saveOneEntry() { launch(); tap("add"); waitFor("amount"); node("amount").performTextInput("125"); tap("save") }

    // ---- Save and Paid ----

    @Test
    fun savingAnEntryDrawsATickBesideTheWordsThatSayItWasAdded() {
        saveOneEntry()
        waitFor("save_success_check"); waitFor("save_success_text")
        val words = textOf("save_success_text")
        assertTrue(words.startsWith("Added") && words.contains("125.00"), "the result is in words next to the tick: $words")
        assertTrue(has("save_success_check"), "the tick is there")
    }

    @Test
    fun underReduceMotionTheSaveTickIsThereToo() {
        put("reduce_motion", "1")
        saveOneEntry()
        waitFor("save_success_check"); waitFor("save_success_text")
        assertTrue(textOf("save_success_text").startsWith("Added"))
    }


    private fun payABill() {
        val due = LocalDate.now().plusDays(8).format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        runBlocking { Capture.ingest(container, Capture.SMS, "AX-HDFCBK-S", "Your HDFC Bank Credit Card ending 4821 statement is generated. Total Amt Due Rs.5,000.00, Min Amt Due Rs.250.00, Due Date $due.", System.currentTimeMillis()) }
        val id = runBlocking { container.write { it.bills.list(BillStatus.FOUND).single().id.also { b -> it.bills.track(b) } } }!!
        launch(); compose.openMore(); tap("open_bills"); waitFor("bills_list")
        tap("bill_pay_$id"); waitFor("bill_pay_amount"); tap("bill_pay_ok")
        waitFor("bills_paid_title"); waitFor("bill_paid_check_$id")
    }

    @Test fun payingABillStillShowsItsPaidCheck() = payABill()

    @Test
    fun payingABillUnderReduceMotionShowsTheCheckCompleteAtOnce() {
        put("reduce_motion", "1")
        payABill()
    }

    // ---- Calendar ----

    private fun openCalendar() { launch(); compose.openMore(); tap("open_calendar"); waitFor("cal_month") }

    @Test
    fun theCalendarRingsPastQuietDaysFromTheFirstEntryAndSaysHowManyInWords() {
        val prev = today.minusMonths(1)
        val length = YearMonth.from(prev).lengthOfMonth()
        spendOn("a", prev.withDayOfMonth(10)); spendOn("b", prev.withDayOfMonth(12))
        openCalendar(); tap("cal_prev"); waitFor("cal_day_11")
        waitFor("calendar_no_spend_11")
        assertTrue(has("calendar_no_spend_13") && has("calendar_no_spend_$length"), "every quiet day after the first entry has a ring")
        assertFalse(has("calendar_no_spend_10") || has("calendar_no_spend_12"), "a day with spending has none")
        assertFalse(has("calendar_no_spend_9") || has("calendar_no_spend_1"), "before the first entry nothing is said")
        assertTrue(descriptionOf("cal_day_11").contains("no spending"), "the cell says it in words: ${descriptionOf("cal_day_11")}")
        waitFor("calendar_no_spend_summary")
        assertTrue(textOf("calendar_no_spend_summary").startsWith("${length - 11} no-spend days in "), textOf("calendar_no_spend_summary"))
    }

    @Test
    fun thisMonthCountsOnlyDaysAlreadyOverAndSaysThisMonth() {
        spendOn("first", today.withDayOfMonth(1))
        openCalendar()
        assertFalse(has("calendar_no_spend_${today.dayOfMonth}"), "today is not over: no ring")
        if (today.dayOfMonth >= 3) {
            waitFor("calendar_no_spend_summary")
            assertEquals("${today.dayOfMonth - 2} no-spend ${if (today.dayOfMonth - 2 == 1) "day" else "days"} this month", textOf("calendar_no_spend_summary"))
            assertTrue(has("calendar_no_spend_2"))
        } else assertFalse(has("calendar_no_spend_summary"), "no day of this month is over yet")
    }

    @Test
    fun anEmptyLedgerHasNoRingsAndNoSummary() {
        openCalendar(); tap("cal_prev"); waitFor("cal_day_11")
        assertFalse(has("calendar_no_spend_11") || has("calendar_no_spend_summary"))
    }

    // ---- Milestones ----

    @Test
    fun milestonesAreOffByDefaultAndTheSwitchSaysWhatItDoes() {
        spendOn("one", today)
        launch(); Thread.sleep(2_500) // the figures that decide it settle a moment after Home opens
        assertFalse(has("milestone_card"), "opt-in: nothing without the switch")
        openNotifications()
        assertEquals(null, setting("milestones_on"))
        val words = everyWord()
        assertTrue(words.contains("Occasional milestones"), "the switch is named")
        assertFalse(words.contains("streak", ignoreCase = true))
    }

    @Test
    fun withTheSwitchOnAFirstEntryShowsTheCardOnceAndThanksRemovesItForGood() {
        put("milestones_on", "1")
        spendOn("one", today)
        launch(); waitFor("milestone_card")
        assertEquals("Your first entry is noted.", textOf("milestone_text"))
        tap("milestone_thanks"); gone("milestone_card")
        assertTrue(setting("milestones_seen").orEmpty().startsWith("first_entry@$todayEpochDay"), "kept as seen: ${setting("milestones_seen")}")
        launch(); Thread.sleep(2_500)
        assertFalse(has("milestone_card"), "once ever")
    }

    @Test
    fun theSwitchInNotificationsTurnsItOnAndOffAndOffTakesTheCardAway() {
        spendOn("one", today)
        launch(); openNotifications()
        tap("milestones_switch"); compose.waitUntil(10_000) { setting("milestones_on") == "1" }
        backToHome(); waitFor("milestone_card")
        openNotifications(); tap("milestones_switch"); compose.waitUntil(10_000) { setting("milestones_on") == "0" }
        backToHome(); gone("milestone_card")
    }

    @Test
    fun theCapsHold() {
        put("milestones_on", "1")
        spendOn("one", today)
        // fourteen days between two: the last was three days ago
        put("milestones_seen", "x@${todayEpochDay - 3}")
        launch(); Thread.sleep(2_500)
        assertFalse(has("milestone_card"), "too soon after the last")
        // six in the year already
        put("milestones_seen", (1..6).joinToString(",") { "m$it@${todayEpochDay - 15L * it}" })
        launch(); Thread.sleep(2_500)
        assertFalse(has("milestone_card"), "six in a year is the most")
        // the last was twenty days ago and fewer than six: it may come
        put("milestones_seen", "x@${todayEpochDay - 20}")
        launch(); waitFor("milestone_card")
    }

    @Test
    fun aMilestoneNeverHoldsAnAmountEvenWithAmountsShown() {
        put("milestones_on", "1"); put("hide_amounts", "1")
        spendOn("one", today, rupees = 1_234)
        launch(); waitFor("milestone_card")
        val words = textOf("milestone_text")
        assertFalse(words.contains("₹") || words.contains("•") || words.any { it.isDigit() }, words)
    }

    @Test
    fun aMonthThatEndedUnderBudgetIsSaidOnceInWordsWithoutTheAmount() {
        put("milestones_on", "1")
        // an overall budget and a first entry long ago, little spent in the month that just ended
        runBlocking { container.write { it.budgets.setLimit(null, Money(100_000_00, inr)) } }
        val prev = today.minusMonths(1).withDayOfMonth(5)
        spendOn("kept", prev, rupees = 400)
        // many entries so the first-entry moment does not apply
        repeat(12) { spendOn("pad$it", prev.minusMonths(2), rupees = 1) }
        launch(); waitFor("milestone_card")
        val words = textOf("milestone_text")
        assertTrue(words.endsWith(" ended under budget."), words)
        assertFalse(words.contains("₹") || words.any { it.isDigit() }, "no amount: $words")
        tap("milestone_thanks"); gone("milestone_card")
        assertTrue(setting("milestones_seen").orEmpty().contains("under_budget:"))
    }

    // ---- Pause nudges ----

    @Test
    fun pausingHidesTheSuggestionCardsButADueBillStillShowsAndItSurvivesARestart() {
        uncategorised(3); dueBill()
        launch(); waitFor("bills_banner"); waitFor("home_more_nudges") // the bill is the card; the missing categories wait in the line under it
        openNotifications()
        assertFalse(has("pause_nudges_status"), "not paused")
        tap("pause_nudges_week")
        waitFor("pause_nudges_status")
        assertTrue(textOf("pause_nudges_status").startsWith("Paused until "), textOf("pause_nudges_status"))
        backToHome()
        waitFor("bills_banner"); assertFalse(has("home_more_nudges"), "the suggestions wait: nothing is waiting under the card")
        launch(); waitFor("bills_banner")
        assertFalse(has("home_more_nudges"), "still paused after a restart")
        assertTrue((setting(NudgePause.SETTING)?.toLongOrNull() ?: 0L) > System.currentTimeMillis())
        openNotifications(); waitFor("pause_nudges_status")
    }

    @Test
    fun theCategoriseCardIsHiddenWhilePausedAndComesBackWhenTurnedOn() {
        uncategorised(3)
        launch(); waitFor("categorise_nudge")
        openNotifications(); tap("pause_nudges_forever"); waitFor("pause_nudges_status")
        assertEquals("Paused", textOf("pause_nudges_status"))
        backToHome(); gone("categorise_nudge")
        openNotifications(); tap("pause_nudges_switch"); gone("pause_nudges_status")
        backToHome(); waitFor("categorise_nudge")
    }

    @Test
    fun aMonthPauseEndsByItselfAndAPausedMilestoneWaits() {
        uncategorised(3)
        put(NudgePause.SETTING, (System.currentTimeMillis() - 1_000).toString())
        launch(); waitFor("categorise_nudge") // a pause that is over is no pause
        put("milestones_on", "1"); put(NudgePause.SETTING, NudgePause.FOREVER.toString())
        launch(); Thread.sleep(2_500)
        assertFalse(has("milestone_card") || has("categorise_nudge"), "paused: no milestone and no suggestion")
    }

    // ---- No streak, anywhere ----

    @Test
    fun noScreenOfThisSliceSaysStreakOrKeepsACount() {
        put("milestones_on", "1")
        spendOn("one", today)
        launch(); waitFor("milestone_card"); assertNoStreak("Home with the milestone card")
        openNotifications(); assertNoStreak("Notifications")
        backToHome()
        compose.openMore(); tap("open_calendar"); waitFor("cal_month"); assertNoStreak("Calendar")
    }
}

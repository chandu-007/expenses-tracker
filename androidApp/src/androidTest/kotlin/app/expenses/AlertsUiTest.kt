package app.expenses

import android.app.NotificationManager
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.expenses.core.domain.CardTerms
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.Notice
import app.expenses.core.domain.NoticeKind
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The alert settings, the card's credit limit, the buttons on notifications and what they do. */
@RunWith(AndroidJUnit4::class)
class AlertsUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null
    private val inr = Currency.INR

    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1") } } }
    @After fun cleanUp() {
        compose.waitForIdle(); scenario?.close()
        context.getSystemService(NotificationManager::class.java).cancelAll()
        WorkManager.getInstance(context).cancelAllWorkByTag(NoticeActionReceiver.SNOOZE_TAG).result.get()
        runBlocking { container.startFresh() }
    }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(10_000) { has(tag) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
    private fun tap(tag: String) { waitFor(tag); runCatching { node(tag).performScrollTo() }; node(tag).performClick() }
    private fun launch(intent: Intent = Intent(context, MainActivity::class.java)) { compose.waitForIdle(); scenario?.close(); scenario = ActivityScenario.launch(intent) }
    private fun <T> db(block: (AppContainer.Opened.Ok) -> T): T = runBlocking { container.write(block) }!!
    private fun <T> dbOrNull(block: (AppContainer.Opened.Ok) -> T?): T? = runBlocking { container.write(block) }
    private fun prefs() = db { it.notifications.prefs() }
    private fun textOf(tag: String): String = node(tag).fetchSemanticsNode().config.let { c -> if (c.contains(SemanticsProperties.EditableText)) c[SemanticsProperties.EditableText].text else c[SemanticsProperties.Text].joinToString(" ") { it.text } }

    private val manager get() = context.getSystemService(NotificationManager::class.java)
    /** A notification is put on screen a moment after it is posted. */
    private fun waitShown(key: String) = compose.waitUntil(10_000) { manager.activeNotifications.any { it.id == key.hashCode() } }

    private fun seedCard(): String {
        db { ok ->
            ok.store.saveAccount(Account(AccountId("amex"), "Amex", AccountType.CREDIT_CARD, inr))
            ok.cards.save(CardTerms("amex", statementDay = if (java.time.LocalDate.now().dayOfMonth == 14) 13 else 14, dueDay = 5))
            ok.store.save(Expense(EntryId("big"), System.currentTimeMillis() - 60L * 86_400_000, AccountId("amex"), Money(40_000, inr)), EntryDetails(merchant = "Phone"))
        }
        return "amex"
    }

    private fun bill(id: String) = Bill(id, "SMS", "AX-BESCOM", "BESCOM", "UTILITY", "BESCOM bill", 184_250, null, "INR", java.time.LocalDate.now().plusDays(1).toString(), null, null, false,
        System.currentTimeMillis(), null, BillStatus.TRACKED, null, null, "key-$id")

    @Test
    fun theNewSwitchesAndWarningLevelAreSavedAndTheBillOnesFollowTheBillSwitch() {
        launch(); compose.openMore(); tap("open_notifications"); waitFor("notif_near_80")
        node("notif_near_80").assertIsSelected()
        tap("notif_near_50"); compose.waitUntil(10_000) { prefs().budgetNearPercent == 50 }
        tap("notif_pace"); tap("notif_wrapup"); tap("notif_ahead3"); tap("notif_ahead1"); tap("notif_card_statement"); tap("notif_card_limit")
        compose.waitUntil(10_000) { prefs().let { !it.budgetPace && !it.budgetWrapUp && !it.billAhead3 && !it.billAhead1 && !it.cardStatement && !it.cardLimit } }
        node("notif_near_50").assertIsSelected()
        tap("notif_bills"); compose.waitUntil(10_000) { !prefs().bills }
        node("notif_ahead3").assertIsNotEnabled(); node("notif_card_statement").assertIsNotEnabled(); node("notif_card_limit").assertIsNotEnabled()
        tap("notif_budgets"); compose.waitUntil(10_000) { !prefs().budgets }
        node("notif_pace").assertIsNotEnabled(); node("notif_near_70").assertIsNotEnabled()
        tap("notif_budgets"); compose.waitUntil(10_000) { prefs().budgets }
        node("notif_pace").assertIsEnabled()
    }

    @Test
    fun aCreditLimitIsEnteredOnTheCardKeptShownAgainAndRemovedWithItsBillingDates() {
        seedCard()
        launch(); compose.openMore(); tap("manage_accounts"); waitFor("accounts_stack"); tap("accounts_stack"); waitFor("accounts_stack_close"); tap("account_Amex"); tap("card_dates"); waitFor("card_limit")
        node("card_limit").performTextInput("100000"); tap("card_save")
        compose.waitUntil(10_000) { dbOrNull { it.cards.creditLimit("amex") } == 10_000_000L }
        tap("account_Amex"); tap("card_dates"); waitFor("card_limit")
        assertEquals("100000.00", textOf("card_limit").let { if (it == "100000") "100000.00" else it }, "the limit is shown again")
        node("card_limit").performTextClearance(); tap("card_save")
        compose.waitUntil(10_000) { dbOrNull { it.cards.creditLimit("amex") } == null }
        tap("account_Amex"); tap("card_dates"); node("card_limit").performTextInput("5000"); tap("card_save"); compose.waitUntil(10_000) { dbOrNull { it.cards.creditLimit("amex") } == 500_000L }
        tap("account_Amex"); tap("card_dates"); tap("card_clear")
        compose.waitUntil(10_000) { dbOrNull { it.cards.creditLimit("amex") } == null && dbOrNull { it.cards.terms("amex") } == null }
    }

    @Test
    fun payNowOnACardNoticeOpensTheAddFormAsATransferToTheCardWithWhatIsLeft() {
        val card = seedCard()
        db { it.store.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr)) }
        launch(Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_PAY_CARD).putExtra(MainActivity.EXTRA_CARD_ID, card))
        waitFor("amount")
        assertEquals("400.00", textOf("amount"), "what is left on the statement")
        node("to_chip_Amex").assertIsSelected()
        assertTrue(!(runCatching { node("account_chip_Amex").assertIsSelected() }.isSuccess), "paid from another account, not from the card itself")
    }

    @Test
    fun aBillNoticeCarriesMarkPaidAndInThreeHoursAndACardNoticeCarriesPayNow() {
        val shown = Notifier.show(context, listOf(
            Notice("billtrack:t:today", NoticeKind.BILL_TRACKED, "BESCOM bill is due today", "₹1,842.50", ref = "t"),
            Notice("card:amex:1:due", NoticeKind.CARD_DUE, "Amex bill is due today", "Pay ₹400.00", ref = "amex"),
            Notice("budget:*:1:NEAR", NoticeKind.BUDGET_NEAR, "Everything is near its limit", "₹100.00 left"),
        ))
        assertEquals(3, shown.size)
        listOf("billtrack:t:today", "card:amex:1:due", "budget:*:1:NEAR").forEach { waitShown(it) }
        val active = manager.activeNotifications
        fun actionsOf(key: String) = active.first { it.id == key.hashCode() }.notification.actions?.map { it.title.toString() }.orEmpty()
        val now = java.time.LocalTime.now()
        assertEquals(listOf("Mark paid", app.expenses.core.domain.Snooze.label(app.expenses.core.domain.Snooze.laterMode(now.hour * 60 + now.minute)), "Tomorrow"), actionsOf("billtrack:t:today"))
        assertEquals(listOf("Pay now"), actionsOf("card:amex:1:due"))
        assertEquals(emptyList(), actionsOf("budget:*:1:NEAR"), "a budget notice has no buttons")
    }

    @Test
    fun markPaidOnTheNoticeMarksTheTrackedBillPaidAndClearsTheNoticeAndInThreeHoursQueuesTheReminder() {
        db { it.bills.add(bill("t")) }
        val notice = Notice("billtrack:t:today", NoticeKind.BILL_TRACKED, "BESCOM bill is due today", "₹1,842.50", ref = "t")
        Notifier.show(context, listOf(notice))
        waitShown(notice.key)
        context.sendBroadcast(Intent(context, NoticeActionReceiver::class.java).setAction(NoticeActionReceiver.ACTION_SNOOZE).putExtra(NoticeActionReceiver.EXTRA_REF, "t")
            .putExtra(NoticeActionReceiver.EXTRA_TITLE, notice.title).putExtra(NoticeActionReceiver.EXTRA_TEXT, notice.text).putExtra(NoticeActionReceiver.EXTRA_KIND, notice.kind.name).putExtra(NoticeActionReceiver.EXTRA_NOTIFICATION_ID, notice.key.hashCode()))
        compose.waitUntil(10_000) { WorkManager.getInstance(context).getWorkInfosByTag(NoticeActionReceiver.SNOOZE_TAG).get().any { it.state == WorkInfo.State.ENQUEUED } }
        compose.waitUntil(10_000) { manager.activeNotifications.none { it.id == notice.key.hashCode() } }
        assertEquals(BillStatus.TRACKED, db { it.bills.get("t")!!.status }, "snoozing does not pay it")
        Notifier.show(context, listOf(notice)); waitShown(notice.key)
        context.sendBroadcast(Intent(context, NoticeActionReceiver::class.java).setAction(NoticeActionReceiver.ACTION_MARK_PAID).putExtra(NoticeActionReceiver.EXTRA_REF, "t").putExtra(NoticeActionReceiver.EXTRA_NOTIFICATION_ID, notice.key.hashCode()))
        compose.waitUntil(10_000) { db { it.bills.get("t")!!.status } == BillStatus.PAID }
        compose.waitUntil(10_000) { manager.activeNotifications.none { it.id == notice.key.hashCode() } }
        assertEquals(0, db { it.db.schemaQueries.entryCount().executeAsOne() }, "marking paid records nothing in the ledger")
    }

    private fun spendAt(id: String, millis: Long, minor: Long) = db { ok -> ok.store.save(Expense(EntryId(id), millis, AccountId("cash"), Money(minor, inr)), EntryDetails(merchant = id)) }
    private fun millisOf(date: java.time.LocalDate) = date.atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun homeSetsThisMonthAgainstLastMonthAtTheSamePointAndSaysNothingWithNothingToCompare() {
        val today = java.time.LocalDate.now()
        launch(); waitFor("month_spend")
        assertTrue(!has("month_compare"), "no spending at all: nothing to compare")
        scenario!!.close()
        spendAt("now", millisOf(today), 250_000)
        launch(); waitFor("month_spend")
        assertTrue(!has("month_compare"), "nothing was spent last month: nothing to compare with")
        scenario!!.close()
        spendAt("before", millisOf(today.withDayOfMonth(1).minusMonths(1)), 100_000)  // the first day of last month is always inside the comparable days
        spendAt("later", millisOf(today.withDayOfMonth(1).minusMonths(1).withDayOfMonth(28)), 900_000) // the 28th may be beyond the point reached, and must not count when it is
        launch(); waitFor("month_compare")
        val t = textOf("month_compare")
        if (today.dayOfMonth < 28) assertEquals("▲ ₹1,500.00 more than last month so far (+150%)", t) else assertTrue(t.startsWith("▼") || t.startsWith("▲"), t)
    }

    @Test
    fun theBillsBannerCountsTheBillsDueWithinAWeekIncludingOverdueOnes() {
        db { it.bills.add(bill("b1")); it.bills.add(bill("b2").copy(dueDate = java.time.LocalDate.now().minusDays(2).toString(), dedupeKey = "k2")); it.bills.add(bill("b3").copy(dueDate = java.time.LocalDate.now().plusDays(12).toString(), dedupeKey = "k3")) }
        launch(); waitFor("bills_banner")
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("2 bills due this week"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun theWeekLineAlsoCountsRepeatingItemsAndCardDueDatesAndCountsATrackedCardBillOnlyOnce() {
        val today = java.time.LocalDate.now()
        db { ok ->
            ok.store.saveAccount(Account(AccountId("amex"), "Amex", AccountType.CREDIT_CARD, inr))
            ok.store.save(Expense(EntryId("big"), System.currentTimeMillis() - 60L * 86_400_000, AccountId("amex"), Money(40_000, inr)), EntryDetails(merchant = "Phone"))
            // due within the week whatever today's date is (or overdue, when that wraps past the 28th, which also counts)
            ok.cards.save(CardTerms("amex", statementDay = 1, dueDay = today.plusDays(3).dayOfMonth.coerceAtMost(28)))
            ok.recurring.save(app.expenses.core.domain.RecurringItem("rent", "Rent", Money(500_000, inr), app.expenses.core.domain.ledger.CategoryId("Bills"), AccountId("cash"), null,
                app.expenses.core.domain.Schedule(app.expenses.core.domain.RepeatUnit.MONTH, 1, kotlinx.datetime.LocalDate(today.plusDays(1).year, today.plusDays(1).monthValue, today.plusDays(1).dayOfMonth)), false))
        }
        launch(); waitFor("bills_banner")
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("2 bills due this week"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("5,400.00", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() } // 5,000 rent + 400 left on the card
        scenario!!.close()
        // the card's own bill, tracked, is the same thing: it is counted once, not twice
        db { ok -> ok.bills.add(bill("cardbill").copy(kind = "CREDIT_CARD", dueDate = java.time.LocalDate.of(today.year, today.month, today.plusDays(3).dayOfMonth.coerceAtMost(28)).toString(), dedupeKey = "cb", amountMinor = 40_000)) }
        launch(); waitFor("bills_banner")
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("2 bills due this week"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun food(id: String, millis: Long, minor: Long) = db { ok -> ok.store.save(Expense(EntryId(id), millis, AccountId("cash"), Money(minor, inr), listOf(app.expenses.core.domain.ledger.CategorySplit(app.expenses.core.domain.ledger.CategoryId("Food"), Money(minor, inr)))), EntryDetails(merchant = id)) }

    @Test
    fun homeNamesTheCategoryRunningWellAboveItsUsualOnlyWithTwoEarlierMonthsToJudgeBy() {
        val today = java.time.LocalDate.now()
        food("now", millisOf(today), 300_000)
        food("m1", millisOf(today.withDayOfMonth(1).minusMonths(1)), 100_000)
        launch(); waitFor("month_spend")
        assertTrue(!has("home_insight"), "one earlier month is too little to call anything usual")
        scenario!!.close()
        food("m2", millisOf(today.withDayOfMonth(1).minusMonths(2)), 100_000)
        launch(); waitFor("home_insight")
        assertEquals("Food is 200% above your usual (₹3,000.00 against ₹1,000.00 by now)", textOf("home_insight"))
    }
}


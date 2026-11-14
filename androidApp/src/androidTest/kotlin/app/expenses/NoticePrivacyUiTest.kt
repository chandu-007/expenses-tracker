package app.expenses

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import app.expenses.core.domain.CardTerms
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.Notice
import app.expenses.core.domain.NoticeKind
import app.expenses.core.domain.NoticePrivacy
import app.expenses.core.domain.NotificationPrefs
import app.expenses.core.domain.RecurringItem
import app.expenses.core.domain.RepeatUnit
import app.expenses.core.domain.Schedule
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.Bill
import app.expenses.core.storage.BillStatus
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * F-01 on a phone: the notices the real background check puts in the shade, read back from the system, with Hide amounts off, on, and with only the App lock on.
 * Invented data: a repeating bill due today, a budget over its limit, last week's spending (the recap), a card due soon, a bill found in a message and a tracked bill.
 */
@RunWith(AndroidJUnit4::class)
class NoticePrivacyUiTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as ExpensesApp
    private val container get() = app.container
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val inr = Currency.INR
    private val digitOrRupee = Regex("[0-9₹]")

    @Before fun fresh() {
        if (android.os.Build.VERSION.SDK_INT >= 33) InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        WorkManager.getInstance(context).cancelAllWork().result.get() // the app's own hourly check must not show a notice in the middle of a test
        manager.cancelAll()
        setLock(false)
        runBlocking { container.startFresh() }
    }

    @After fun cleanUp() {
        manager.cancelAll()
        setLock(false)
        runBlocking { container.startFresh() }
    }

    private fun setLock(on: Boolean) {
        context.getSharedPreferences("app_lock", Context.MODE_PRIVATE).edit().putBoolean("enabled", on).commit()
        app.lock = AppLock(context)
    }

    private fun seed(hide: Boolean) {
        val today = java.time.LocalDate.now()
        val monday = today.minusDays(today.dayOfWeek.ordinal.toLong())
        fun millis(d: java.time.LocalDate) = d.atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        runBlocking {
            container.write { ok ->
                ok.templates.putSetting("hide_amounts", if (hide) "1" else "0")
                ok.notifications.setPrefs(NotificationPrefs(quietEnabled = false, billHour = 0, recap = true))
                ok.recurring.save(RecurringItem("Rent", "Rent", Money(1_500_000, inr), CategoryId("Bills"), AccountId("bank"), null, Schedule(RepeatUnit.MONTH, 1, LocalDate(today.year, today.monthValue, today.dayOfMonth)), false))
                ok.budgets.setLimit(null, Money(1_000_000, inr))
                ok.store.save(Expense(EntryId("over"), millis(today), AccountId("cash"), Money(1_240_000, inr), listOf(CategorySplit(CategoryId("Food"), Money(1_240_000, inr)))), EntryDetails(merchant = "Big shop"))
                ok.store.save(Expense(EntryId("lastweek"), millis(monday.minusDays(3)), AccountId("cash"), Money(345_000, inr), listOf(CategorySplit(CategoryId("Food"), Money(345_000, inr)))), EntryDetails(merchant = "Groceries"))
                ok.store.saveAccount(Account(AccountId("amex"), "Amex", AccountType.CREDIT_CARD, inr))
                ok.store.save(Expense(EntryId("phone"), millis(today.minusDays(60)), AccountId("amex"), Money(4_000_000, inr), listOf(CategorySplit(CategoryId("Food"), Money(4_000_000, inr)))), EntryDetails(merchant = "Phone"))
                ok.cards.save(CardTerms("amex", statementDay = today.minusDays(10).dayOfMonth.coerceAtMost(28), dueDay = today.plusDays(2).dayOfMonth.coerceAtMost(28), remindDaysBefore = 10))
                ok.bills.add(Bill("t", "SMS", "AX-BESCOM", "BESCOM", "UTILITY", "BESCOM bill", 184_250, null, "INR", today.plusDays(1).toString(), null, null, false, System.currentTimeMillis(), null, BillStatus.TRACKED, null, null, "key-t"))
                ok.bills.add(Bill("f", "SMS", "AX-HDFCBK", "HDFC", "CREDIT_CARD", "HDFC Bank credit card", 1_234_500, 61_700, "INR", today.plusDays(9).toString(), "0862", null, false, System.currentTimeMillis(), null, BillStatus.FOUND, null, null, "key-f"))
            }
        }
    }

    /** What the system holds for our notices: title, text and the lock-screen version of each (the group summary the system adds has no title). */
    private data class Shown(val title: String, val text: String, val visibility: Int, val publicTitle: String?, val publicText: String?)

    private fun shade(): List<Shown> {
        val deadline = System.currentTimeMillis() + 10_000
        while (manager.activeNotifications.count { it.notification.extras.getCharSequence(Notification.EXTRA_TITLE) != null } < 6 && System.currentTimeMillis() < deadline) Thread.sleep(100)
        return manager.activeNotifications.filter { it.notification.extras.getCharSequence(Notification.EXTRA_TITLE) != null }.map { n ->
            val e = n.notification.extras; val p = n.notification.publicVersion?.extras
            Shown(e.getCharSequence(Notification.EXTRA_TITLE).toString(), e.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(), n.notification.visibility, p?.getCharSequence(Notification.EXTRA_TITLE)?.toString(), p?.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        }
    }

    private fun check() = runBlocking { NotificationWork.checkNow(context, container) }

    @Test
    fun withNothingHiddenTheNoticesCarryTheirAmountsAndEachHasALockScreenVersion() {
        seed(hide = false)
        assertEquals(6, check(), "recap, budget, card, bill due, tracked bill and found bill")
        val all = shade()
        assertEquals(6, all.size, "$all")
        assertTrue(all.any { "₹" in it.text && it.title.startsWith("Over budget") }, "the budget notice has its amounts: $all")
        assertTrue(all.any { "₹" in it.title && it.title.startsWith("Last week") }, "the recap's title has the amount: $all")
        assertTrue(all.any { it.title.startsWith("Amex") && "₹" in it.text }, "$all")
        // every one is private on a lock screen that hides sensitive content, with a public version in our words that has no amount
        for (s in all) {
            assertEquals(Notification.VISIBILITY_PRIVATE, s.visibility, "$s")
            assertNotNull(s.publicTitle, "$s"); assertEquals(NoticePrivacy.PUBLIC_TEXT, s.publicText, "$s")
            assertTrue(!digitOrRupee.containsMatchIn(s.publicTitle!!) && !digitOrRupee.containsMatchIn(s.publicText!!), "$s")
        }
        assertEquals(setOf("A bill was found", "A bill needs attention", "A card needs attention", "A budget needs attention", "Your weekly recap"), all.map { it.publicTitle }.toSet())
    }

    private fun assertNoAmounts(all: List<Shown>, what: String) {
        assertEquals(6, all.size, "$what: $all")
        for (s in all) {
            // a bill found in a message keeps its due date ("due 25 Oct"), and a title may say "in 2 days": neither is an amount
            val text = s.text.replace(Regex("due \\d{1,2} [A-Z][a-z]{2}"), "")
            assertTrue(!digitOrRupee.containsMatchIn(text), "$what: text '${s.text}' (${s.title})")
            assertTrue("₹" !in s.title && !Regex("\\d,\\d|\\d\\.\\d\\d").containsMatchIn(s.title), "$what: title '${s.title}'")
            assertNotNull(s.publicTitle, "$what: $s"); assertEquals(Notification.VISIBILITY_PRIVATE, s.visibility)
        }
        assertTrue(all.any { it.title == "Your weekly recap" && it.text == "Open the app to see how last week went" }, "$what: $all")
        assertTrue(all.any { it.title.startsWith("Over budget") && it.text == "Open the app to see how much is left" }, "$what: $all")
        assertTrue(all.any { it.title.startsWith("Amex") && it.text == "Open the app to see what to pay" }, "$what: $all")
        assertTrue(all.any { it.title.startsWith("Rent") && it.text == "Open the app to post or skip it" }, "$what: $all")
    }

    @Test
    fun withHideAmountsOnNoNoticeHasAnAmountInItsText() {
        seed(hide = true)
        assertEquals(6, check())
        assertNoAmounts(shade(), "Hide amounts on")
    }

    @Test
    fun withTheAppLockOnAndHideAmountsOffNoNoticeHasAnAmountEither() {
        seed(hide = false); setLock(true)
        assertEquals(6, check())
        assertNoAmounts(shade(), "App lock on")
        assertTrue(runBlocking { container.write { it.templates.setting("hide_amounts") } } != "1", "the lock did not turn Hide amounts on")
    }

    @Test
    fun aSnoozedReminderComesBackWithoutItsAmountOnceHideAmountsIsOn() {
        seed(hide = false)
        runBlocking { container.write { it.templates.putSetting("hide_amounts", "1") } } // turned on after the notice was first shown (with its amount) and snoozed
        // the same work the "In 3 hours" button queues, with no delay
        NoticeActionReceiver.schedule(context, "t", "BESCOM bill due tomorrow", "₹1,842.50 · Mark it paid when it is done", NoticeKind.BILL_TRACKED.name, delayMinutes = 0)
        val deadline = System.currentTimeMillis() + 15_000
        while (manager.activeNotifications.none { it.notification.extras.getCharSequence(Notification.EXTRA_TITLE) != null } && System.currentTimeMillis() < deadline) Thread.sleep(100)
        Thread.sleep(500)
        val shown = manager.activeNotifications.filter { it.notification.extras.getCharSequence(Notification.EXTRA_TITLE) != null }
        assertEquals(1, shown.size, "$shown")
        assertEquals("Mark it paid when it is done", shown.single().notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }
}

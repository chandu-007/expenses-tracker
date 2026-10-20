package app.expenses

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.NotificationPrefs
import app.expenses.core.domain.RecurringItem
import app.expenses.core.domain.RepeatUnit
import app.expenses.core.domain.Schedule
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.EntryDetails
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class NotificationsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val inr = Currency.INR
    private var scenario: ActivityScenario<MainActivity>? = null
    private val today: LocalDate = java.time.LocalDate.now().let { LocalDate(it.year, it.monthValue, it.dayOfMonth) }

    private fun resetApp() {
        runBlocking { container.startFresh() }
        manager.cancelAll()
    }

    /** The less frequent places are in the "More" menu on the list. */
    private fun openFromMenu(tag: String) {
        compose.openMore()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag).also { runCatching { it.performScrollTo() } }.performClick()
    }

    @Before fun fresh() {
        // Android 13+ asks for this at runtime; older versions have no such permission.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        // The app schedules its own background check when the process starts; it could run in the middle of a test and show a notice first, so these tests drive the check themselves.
        androidx.work.WorkManager.getInstance(context).cancelAllWork().result.get()
        resetApp()
    }

    @After fun cleanUp() {
        compose.waitForIdle(); scenario?.close()
        resetApp()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        waitFor("add")
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    /** The system posts notifications asynchronously, so wait for the shade to show exactly [expected] instead of reading it once. */
    private fun assertShade(vararg expected: String) {
        val want = expected.sorted()
        val deadline = System.currentTimeMillis() + 10_000
        while (titles() != want && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertEquals(want, titles())
    }

    private fun titles(): List<String> = manager.activeNotifications.mapNotNull { it.notification.extras.getCharSequence("android.title")?.toString() }.sorted()

    /** Settings that let a check send immediately, whatever the time of day. */
    private fun anytime() = runBlocking { container.write { it.notifications.setPrefs(NotificationPrefs(quietEnabled = false, billHour = 0)) } }
    private fun bill(id: String, auto: Boolean = false) = runBlocking {
        container.write { it.recurring.save(RecurringItem(id, id, Money(10_000, inr), CategoryId("Bills"), AccountId("bank"), null, Schedule(RepeatUnit.MONTH, 1, today), auto)) }
    }
    private fun check() = runBlocking { NotificationWork.checkNow(context, container) }
    private fun prefs() = runBlocking { container.write { it.notifications.prefs() } }!!

    @Test
    fun aBillDueTodayIsShownOnceInTheNotificationShadeAndNotAgain() {
        anytime(); bill("Rent")
        assertEquals(1, check())
        assertShade("Rent due today")
        manager.cancelAll()
        assertEquals(0, check(), "already shown, so it is not shown again even though the user cleared it")
        assertShade()
    }

    @Test
    fun aBudgetGoingOverIsAlertedOnce() {
        anytime()
        runBlocking {
            container.write { ok ->
                ok.budgets.setLimit(null, Money(100_000, inr))
                ok.store.save(Expense(EntryId("big"), System.currentTimeMillis(), AccountId("cash"), Money(120_000, inr), listOf(CategorySplit(CategoryId("Food"), Money(120_000, inr)))), EntryDetails(merchant = "big"))
            }
        }
        assertEquals(1, check())
        assertShade("Over budget: Everything")
        assertEquals(0, check())
    }

    @Test
    fun theBackgroundCheckPostsAutomaticItemsAndTheyAreNotRemindedAbout() {
        anytime(); bill("Netflix", auto = true); bill("Rent")
        assertEquals(1, check())
        assertShade("Rent due today")
        assertEquals(1L, runBlocking { container.write { it.db.schemaQueries.entryCount().executeAsOne() } }, "Netflix was posted by the check")
    }

    @Test
    fun quietHoursAndSwitchesHoldEverythingBack() {
        bill("Rent")
        val now = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
        runBlocking { container.write { it.notifications.setPrefs(NotificationPrefs(quietEnabled = true, quietStartMinute = now, quietEndMinute = (now + 60) % 1440, billHour = 0)) } }
        assertEquals(0, check(), "inside quiet hours")
        runBlocking { container.write { it.notifications.setPrefs(NotificationPrefs(bills = false, quietEnabled = false, billHour = 0)) } }
        assertEquals(0, check(), "bill reminders switched off")
        anytime()
        assertEquals(1, check(), "nothing was lost while it was held back")
    }

    @Test
    fun theTestButtonShowsANotification() {
        launch()
        openFromMenu("open_notifications")
        waitFor("notif_test")
        compose.onNodeWithTag("notif_test").performClick()
        compose.waitUntil(10_000) { titles().contains("Test notification") }
    }

    @Test
    fun manyQuickChangesToANotificationTimeAreAllCountedNoneLost() {
        launch()
        val vm = androidx.lifecycle.ViewModelProvider(run { var a: MainActivity? = null; scenario!!.onActivity { a = it }; a!! })[LedgerViewModel::class.java]
        repeat(5) { vm.updateNotificationPrefs { p -> p.copy(quietStartMinute = Math.floorMod(p.quietStartMinute + 60, 1440)) } }
        compose.waitUntil(10_000) { prefs().quietStartMinute == 3 * 60 } // 22:00 plus five hours, wrapping past midnight
        repeat(3) { vm.updateNotificationPrefs { p -> p.copy(billHour = Math.floorMod(p.billHour - 1, 24)) } }
        compose.waitUntil(10_000) { prefs().billHour == 6 }
        assertEquals(true, prefs().quietEnabled, "unrelated choices are untouched")
    }

    @Test
    fun switchesAndQuietHoursAreSavedAndWrapAroundMidnight() {
        launch()
        openFromMenu("open_notifications")
        waitFor("notif_bills")
        compose.onNodeWithTag("notif_quiet_start").assertTextEquals("22:00")
        compose.onNodeWithTag("notif_quiet_end").assertTextEquals("07:00")
        compose.onNodeWithTag("notif_bills").performClick()
        compose.waitUntil(10_000) { !prefs().bills }
        compose.onNodeWithTag("notif_bill_hour_up").assertIsNotEnabled()
        compose.onNodeWithTag("notif_bills").performClick()
        compose.waitUntil(10_000) { prefs().bills }
        compose.onNodeWithTag("notif_bill_hour_up").assertIsEnabled()
        repeat(3) { compose.onNodeWithTag("notif_quiet_start_up").performClick() }
        compose.waitUntil(10_000) { prefs().quietStartMinute == 60 }
        compose.onNodeWithTag("notif_quiet_start").assertTextEquals("01:00")
        repeat(8) { compose.onNodeWithTag("notif_quiet_end_down").performClick() }
        compose.waitUntil(10_000) { prefs().quietEndMinute == 23 * 60 }
        compose.onNodeWithTag("notif_quiet").performClick()
        compose.waitUntil(10_000) { !prefs().quietEnabled }
        compose.onNodeWithTag("notif_quiet_start_up").assertIsNotEnabled()
    }

    @Test
    fun aCreditCardBillWithSomethingLeftIsRemindedInTheBillsChannelAndNotOnceItIsPaid() {
        anytime()
        runBlocking {
            container.write { ok ->
                ok.store.saveAccount(app.expenses.core.domain.ledger.Account(AccountId("amex"), "Amex", app.expenses.core.domain.ledger.AccountType.CREDIT_CARD, inr))
                ok.store.save(Expense(EntryId("big"), System.currentTimeMillis() - 60L * 86_400_000, AccountId("amex"), Money(40_000, inr), listOf(CategorySplit(CategoryId("Food"), Money(40_000, inr)))), EntryDetails(merchant = "Phone"))
                // due within the next days whatever today's date is (and "overdue" when that wraps past the 28th, which also reminds)
                val target = java.time.LocalDate.now().plusDays(2).dayOfMonth.coerceAtMost(28)
                ok.cards.save(app.expenses.core.domain.CardTerms("amex", statementDay = 1, dueDay = target, remindDaysBefore = 10))
            }
        }
        assertEquals(1, check())
        // the system posts asynchronously: wait for it instead of reading the shade once
        val deadline = System.currentTimeMillis() + 10_000
        while (manager.activeNotifications.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        val shown = manager.activeNotifications.single()
        assertTrue(shown.notification.extras.getCharSequence("android.title").toString().startsWith("Amex bill"), shown.notification.extras.getCharSequence("android.title").toString())
        assertEquals(Notifier.channelOf(app.expenses.core.domain.NoticeKind.BILL_DUE), shown.notification.channelId, "with the bill reminders, not the budget alerts")
        manager.cancelAll()
        runBlocking { container.write { it.store.save(app.expenses.core.domain.ledger.Transfer(EntryId("pay"), System.currentTimeMillis(), AccountId("bank"), AccountId("amex"), Money(40_000, inr))) } }
        assertEquals(0, check(), "paid in full: nothing to remind")
    }

    @Test
    fun theWeeklyRecapIsOffByDefaultTurnsOnFromTheSettingsAndIsShownOnceInItsOwnChannel() {
        launch(); openFromMenu("open_notifications"); waitFor("notif_recap")
        assertEquals(false, prefs().recap, "off until the person turns it on")
        compose.onNodeWithTag("notif_recap").performScrollTo().performClick()
        compose.waitUntil(10_000) { prefs().recap }
        runBlocking { container.write { it.notifications.setPrefs(NotificationPrefs(quietEnabled = false, billHour = 0, recap = true)) } }
        val lastWeek = app.expenses.core.domain.WeeklyRecap.recappedWeek(kotlinx.datetime.LocalDate(today.year, today.month, today.day))
        val at = java.time.LocalDate.of(lastWeek.start.year, lastWeek.start.monthNumber, lastWeek.start.dayOfMonth).plusDays(2).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        runBlocking { container.write { ok -> ok.store.save(Expense(EntryId("w"), at, AccountId("bank"), Money(123_400, inr), listOf(CategorySplit(CategoryId("Food"), Money(123_400, inr)))), EntryDetails(merchant = "Dinner")) } }
        assertEquals(1, check())
        assertShade("Last week: ₹1,234.00 spent") // waits for the shade, so the read below is safe
        assertEquals(Notifier.CHANNEL_RECAP, manager.activeNotifications.single().notification.channelId, "its own channel, so it can be muted on its own")
        assertEquals(0, check(), "once for that week")
    }
}

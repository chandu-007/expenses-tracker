@file:OptIn(ExperimentalForeignApi::class)

package app.expenses

import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.Money
import app.expenses.core.domain.Notice
import app.expenses.core.domain.NoticeKind
import app.expenses.core.domain.NotificationPrefs
import app.expenses.core.domain.RecurringItem
import app.expenses.core.domain.RepeatUnit
import app.expenses.core.domain.Schedule
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.storage.NotificationService
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSDateComponents
import platform.Foundation.NSLog
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenNotificationSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.darwin.dispatch_time
import kotlin.time.Clock

/**
 * Reminders on iPhone: local notifications only (no server, no push). Android's work does the same three steps every hour: let
 * [NotificationService.check] post what is set to post by itself and decide what is due (it applies the preferences, quiet hours and the
 * record of what was already shown), show those notices, record the ones the system accepted. [runCheck] is that, and the Swift host
 * (iosApp/Sources/Notifications.swift) calls it when the app comes forward, when it goes to the background, and from a `BGAppRefreshTask`.
 *
 * iOS decides when a background refresh runs, and for an app that is rarely opened it may be rarely or never. So the check also hands the
 * system the bill and card reminders of the next few days ahead of time ([NotificationService.plannedFor], at the reminder hour, as
 * calendar notifications): those arrive at the right time even when the app never runs. They are replaced by every check, so a bill posted
 * in the app takes its pending reminder with it; budget alerts, the weekly recap and bills found in a message need the app to run.
 *
 * The permission is asked from the Notifications screen ([requestPermission]); until it is granted the check still posts automatic items.
 */
object IosNotifications {
    /** Raised when a notification that belongs on the Bills page is tapped; the iPhone root host exposes it as `billsRequests`. */
    val billsRequests = MutableStateFlow(0)

    private var host: IosHostServices? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var running: Job? = null
    private var lastForegroundCheck = 0L

    /** Called once by the Swift host at launch, before the first check. */
    fun install(host: IosHostServices) {
        this.host = host
        IosNotificationCenter.registerCategories()
    }

    /**
     * The whole check, blocking until done: never on the main thread. Returns how many notifications were shown. A failure is logged and
     * counts as nothing shown.
     */
    fun runCheck(): Int = kotlinx.coroutines.runBlocking { checkNow() }

    /** [runCheck] on a background thread; [done] gets the count (0 on failure or when cancelled). */
    fun checkAsync(done: (Int) -> Unit) {
        running = scope.launch { done(checkNow()) }
    }

    /** The system said the background time is up. */
    fun cancelCheck() { running?.cancel() }

    /** The app came forward: check, but not again within 20 seconds (a permission prompt or Face ID makes the app active again at once). */
    fun onForeground() {
        val now = nowMillis()
        if (now - lastForegroundCheck < 20_000) return
        lastForegroundCheck = now
        checkAsync { }
    }

    /** A notification was tapped. Bill notices that wait on the Bills page open it (as on Android); everything else only opens the app. */
    fun opened(key: String) {
        if (key.startsWith("billfound:") || key.startsWith("billtrack:")) billsRequests.value += 1
    }

    /** Asks for permission (the system dialog, once), or opens this app's notification settings when it was refused. [onDone] runs on the main thread. */
    fun requestPermission(onDone: () -> Unit) = IosNotificationCenter.request {
        checkAsync { }
        onDone()
    }

    private suspend fun checkNow(): Int {
        val h = host ?: run { NSLog("Expenses: notifications check before install"); return 0 }
        return try {
            val tz = TimeZone.currentSystemDefault()
            val now = Clock.System.now().toLocalDateTime(tz)
            val minute = now.hour * 60 + now.minute
            IosCore.get(h).container.write { ok ->
                IosNotificationCenter.reconcile(ok.notifications)
                val result = ok.notifications.check(now.date, minute, tz)
                val hide = ok.templates.setting("hide_amounts") == "1"
                val shown = IosNotificationCenter.show(result.notices.map { masked(it, hide) }, privateKeys(result.notices, hide))
                // masked() keeps the key, so the record of what was shown is the same as without masking
                ok.notifications.markShown(shown)
                IosNotificationCenter.planAhead(ok.notifications, now.date, minute, tz, hide)
                shown.size
            } ?: 0
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            NSLog("Expenses: notifications check failed: %@", e.stackTraceToString())
            0
        }
    }

    // ---- what a notification may say ----

    /**
     * With Hide amounts on, Android's check already leaves the amount out of bills found in a message and tracked bills. The other kinds
     * (a repeating expense, a card, a budget, the recap) carry figures in their text there; on iPhone those are replaced by a plain line too,
     * so Hide amounts holds for every reminder (the more careful reading of the setting).
     */
    internal fun masked(n: Notice, hide: Boolean): Notice {
        if (!hide) return n
        return when (n.kind) {
            NoticeKind.BILL_DUE -> n.copy(text = "Open the app to post or skip it")
            NoticeKind.CARD_DUE -> n.copy(text = "Open the app to see what to pay")
            NoticeKind.BUDGET_NEAR, NoticeKind.BUDGET_OVER -> n.copy(text = "Open the app to see how much is left")
            NoticeKind.BUDGET_PACE -> n.copy(text = "Open the app to see how your spending is going")
            NoticeKind.BUDGET_WRAPUP -> n.copy(text = "Open the app to see how last month went")
            NoticeKind.CARD_STATEMENT, NoticeKind.CARD_LIMIT, NoticeKind.CARD_CLEARED -> n.copy(text = "Open the app to see your card")
            NoticeKind.WEEKLY_RECAP -> n.copy(title = "Your weekly recap", text = "Open the app to see how last week went")
            NoticeKind.BILL_FOUND, NoticeKind.BILL_TRACKED -> n
        }
    }

    /**
     * Notices whose text is hidden on a lock screen that is set to hide previews (iOS's "Show Previews" setting, as Android's lock-screen
     * version): bills found in a message and tracked bills always (Android does the same), and everything when Hide amounts is on.
     */
    internal fun privateKeys(notices: List<Notice>, hide: Boolean): Set<String> =
        notices.filter { hide || it.kind == NoticeKind.BILL_FOUND || it.kind == NoticeKind.BILL_TRACKED }.map { it.key }.toSet()

    // ---- tests ----

    /**
     * Test entry (the Swift host calls it only for the launch argument `-notify-debug-run`): turns bill reminders on with no quiet hours, adds
     * two made-up repeating bills, one due today and one due in two days, runs the check and, a moment later, reports what the system holds.
     */
    fun debugSeedAndCheck(done: (String) -> Unit) {
        scope.launch {
            val h = host ?: return@launch done("no host")
            val tz = TimeZone.currentSystemDefault()
            val today = Clock.System.now().toLocalDateTime(tz).date
            IosCore.get(h).container.write { ok ->
                ok.notifications.setPrefs(NotificationPrefs(bills = true, budgets = false, quietEnabled = false, billHour = 0, recap = false))
                val currency = BaseCurrency.current
                ok.store.saveAccount(Account(AccountId("notify-debug"), "Notify debug", AccountType.BANK, currency))
                ok.recurring.save(RecurringItem("notify-debug-due", "Made-up rent", Money(150_000, currency), null, AccountId("notify-debug"), null, Schedule(RepeatUnit.MONTH, 1, today)))
                ok.recurring.save(RecurringItem("notify-debug-soon", "Made-up insurance", Money(90_000, currency), null, AccountId("notify-debug"), null, Schedule(RepeatUnit.MONTH, 1, today.plus(2, DateTimeUnit.DAY))))
            }
            val shown = checkNow()
            delay(1_500) // the system delivers an immediate notification a moment after it accepts it
            done("allowed=${IosNotificationCenter.allowed()} shown=$shown pending=${IosNotificationCenter.pendingIds().orEmpty().sorted()} delivered=${IosNotificationCenter.deliveredIds().orEmpty().sorted()}")
        }
    }
}

/** UNUserNotificationCenter behind blocking calls (the [Platform] interface is synchronous, as Android's is). Their callbacks come on the system's own queue, never the main one. */
internal object IosNotificationCenter {
    private const val CATEGORY_OPEN = "open"
    private const val CATEGORY_PRIVATE = "private"
    private const val AHEAD_KEY = "notif_ahead"
    private const val AHEAD_DAYS = 7
    private val center get() = UNUserNotificationCenter.currentNotificationCenter()
    private val defaults get() = NSUserDefaults.standardUserDefaults

    /** Runs [start] and waits (at most [timeoutMs]) for the callback it was given; null when it never came. */
    private fun <T : Any> await(timeoutMs: Long = 2_000, start: ((T) -> Unit) -> Unit): T? {
        val sem = dispatch_semaphore_create(0)
        var result: T? = null
        start { result = it; dispatch_semaphore_signal(sem) }
        return if (dispatch_semaphore_wait(sem, dispatch_time(DISPATCH_TIME_NOW, timeoutMs * 1_000_000L)) == 0L) result else null
    }

    private fun status(): Long? = await<Long> { back -> center.getNotificationSettingsWithCompletionHandler { back(it?.authorizationStatus ?: UNAuthorizationStatusNotDetermined) } }

    /** Whether the system will show our notifications: allowed, provisional (delivered quietly) or ephemeral all count. */
    fun allowed(): Boolean = status().let { it == UNAuthorizationStatusAuthorized || it == UNAuthorizationStatusProvisional || it == UNAuthorizationStatusEphemeral }

    /** The system dialog the first time; after a refusal only Settings can change it, so this app's page there is opened. [onDone] runs on the main thread. */
    fun request(onDone: () -> Unit) {
        center.getNotificationSettingsWithCompletionHandler { settings ->
            when (settings?.authorizationStatus ?: UNAuthorizationStatusNotDetermined) {
                UNAuthorizationStatusNotDetermined ->
                    center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { _, _ -> onMain(onDone) }
                UNAuthorizationStatusDenied -> onMain {
                    NSURL.URLWithString(UIApplicationOpenNotificationSettingsURLString)?.let { UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any>(), null) }
                    onDone()
                }
                else -> onMain(onDone)
            }
        }
    }

    /** "private": with the lock screen set to hide previews, only the app name and "Open the app to see it" show, as Android's lock-screen version. */
    fun registerCategories() {
        val open = UNNotificationCategory.categoryWithIdentifier(CATEGORY_OPEN, emptyList<Any>(), emptyList<Any>(), "", 0uL)
        val private = UNNotificationCategory.categoryWithIdentifier(CATEGORY_PRIVATE, emptyList<Any>(), emptyList<Any>(), "Open the app to see it", 0uL)
        center.setNotificationCategories(setOf(open, private))
    }

    private fun content(n: Notice, hidden: Boolean) = UNMutableNotificationContent().apply {
        setTitle(n.title)
        setBody(n.text)
        // the recap is the quiet kind (Android's low-importance channel)
        setSound(if (n.kind == NoticeKind.WEEKLY_RECAP) null else UNNotificationSound.defaultSound)
        setThreadIdentifier(when (n.kind) {
            NoticeKind.BUDGET_NEAR, NoticeKind.BUDGET_OVER -> "budgets"
            NoticeKind.WEEKLY_RECAP -> "recap"
            else -> "bills"
        })
        setCategoryIdentifier(if (hidden) CATEGORY_PRIVATE else CATEGORY_OPEN)
    }

    private fun add(requests: List<UNNotificationRequest>): Set<String> {
        if (requests.isEmpty()) return emptySet()
        val sem = dispatch_semaphore_create(0)
        val ok = BooleanArray(requests.size) // one slot per request: the callbacks may arrive together on the system's queue
        requests.forEachIndexed { i, r ->
            center.addNotificationRequest(r) { error ->
                ok[i] = error == null
                dispatch_semaphore_signal(sem)
            }
        }
        for (i in requests.indices) if (dispatch_semaphore_wait(sem, dispatch_time(DISPATCH_TIME_NOW, 3_000_000_000L)) != 0L) break
        return requests.filterIndexed { i, _ -> ok[i] }.map { it.identifier }.toSet()
    }

    /** Shows each notice at once (the identifier is the notice key, so one shown again replaces itself) and returns those the system accepted. Empty when notifications are not allowed. */
    fun show(notices: List<Notice>, privateKeys: Set<String> = emptySet()): List<Notice> {
        if (notices.isEmpty() || !allowed()) return emptyList()
        val kinds = notices.associateBy { it.key }
        val accepted = add(notices.map { UNNotificationRequest.requestWithIdentifier(it.key, content(it, it.kind.isBillPrivate() || it.key in privateKeys), null) })
        return accepted.mapNotNull { kinds[it] }
    }

    private fun NoticeKind.isBillPrivate() = this == NoticeKind.BILL_FOUND || this == NoticeKind.BILL_TRACKED

    fun pendingIds(): List<String>? = await<List<String>> { back -> center.getPendingNotificationRequestsWithCompletionHandler { back(it.orEmpty().filterIsInstance<UNNotificationRequest>().map { r -> r.identifier }) } }

    fun deliveredIds(): List<String>? = await<List<String>> { back -> center.getDeliveredNotificationsWithCompletionHandler { back(it.orEmpty().filterIsInstance<UNNotification>().map { n -> n.request.identifier }) } }

    // ---- ahead of time ----

    /** key to the time (ms since 1970) it was set to fire, one per line, for the reminders handed to the system ahead of time. */
    private fun loadAhead(): Map<String, Long> = defaults.stringForKey(AHEAD_KEY).orEmpty().lines().mapNotNull { l ->
        val i = l.indexOf('\t')
        if (i <= 0) null else l.substring(i + 1).toLongOrNull()?.let { l.substring(0, i) to it }
    }.toMap()

    private fun saveAhead(items: Map<String, Long>) = defaults.setObject(items.entries.joinToString("\n") { "${it.key}\t${it.value}" }, forKey = AHEAD_KEY)

    /**
     * A reminder handed to the system earlier whose time has passed and that is no longer waiting there has been shown (or dismissed): it is
     * recorded as shown, so the check does not show it a second time. The system cannot say more than that.
     */
    fun reconcile(service: NotificationService) {
        val ahead = loadAhead().takeIf { it.isNotEmpty() } ?: return
        val pending = pendingIds()?.toSet() ?: return
        val now = nowMillis()
        val fired = ahead.filter { (key, at) -> at <= now && key !in pending }.keys
        if (fired.isNotEmpty()) service.markShown(fired.map { Notice(it, NoticeKind.BILL_DUE, "", "") })
    }

    /**
     * Replaces the reminders waiting in the system by those the next [AHEAD_DAYS] days call for, each at the reminder hour (the end of quiet hours
     * when those cover it). Without permission or with bill reminders off, none are kept. The same notice is planned for several days (an overdue
     * bill, a heads-up): the earliest day wins.
     */
    fun planAhead(service: NotificationService, today: LocalDate, minute: Int, tz: TimeZone, hide: Boolean) {
        center.removePendingNotificationRequestsWithIdentifiers(loadAhead().keys.toList())
        saveAhead(emptyMap())
        val prefs = service.prefs()
        if (!prefs.bills || !allowed()) return
        val at = if (prefs.isQuiet(prefs.billHour * 60)) prefs.quietEndMinute else prefs.billHour * 60
        if (prefs.isQuiet(at)) return // nothing can be shown at any hour of the day: nothing to schedule
        val seen = mutableSetOf<String>()
        val planned = mutableListOf<Pair<Notice, LocalDate>>()
        for (offset in 0..AHEAD_DAYS) {
            if (offset == 0 && minute >= at) continue // this day's reminder hour has passed: the check itself decides what to show now
            val day = today.plus(offset, DateTimeUnit.DAY)
            for (n in service.plannedFor(day, at, tz)) if (seen.add(n.key)) planned += n to day
        }
        val requests = planned.map { (n, day) ->
            val masked = IosNotifications.masked(n, hide)
            val parts = NSDateComponents().also { c -> c.year = day.year.toLong(); c.month = day.monthNumber.toLong(); c.day = day.dayOfMonth.toLong(); c.hour = (at / 60).toLong(); c.minute = (at % 60).toLong() }
            UNNotificationRequest.requestWithIdentifier(n.key, content(masked, n.kind.isBillPrivate() || hide), UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(parts, false))
        }
        val accepted = add(requests)
        saveAhead(planned.filter { it.first.key in accepted }.associate { (n, day) -> n.key to day.atTime(at / 60, at % 60).toInstant(tz).toEpochMilliseconds() })
    }
}

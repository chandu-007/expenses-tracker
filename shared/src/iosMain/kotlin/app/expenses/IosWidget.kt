package app.expenses

import app.expenses.core.domain.AmountMask
import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.Money
import app.expenses.core.domain.formatted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSUserDefaults
import kotlin.time.Clock

/**
 * Links that open the app from outside it: the Today widget's "+ Add" is `expenses://add`. The Swift host calls [openAdd] from `onOpenURL`; the Add screen
 * opens through [IosRootHost.addRequests] exactly as Android's launcher shortcut does.
 */
object IosLinks {
    internal val addRequests = MutableStateFlow(0)
    val addCount: StateFlow<Int> get() = addRequests
    fun openAdd() { addRequests.value += 1 }

    internal val payRequests = MutableStateFlow<PayCardRequest?>(null)
    val payCount: StateFlow<PayCardRequest?> get() = payRequests

    /** Pay now on a card notice: the Add screen opens as a transfer to card [accountId]. */
    fun payCard(accountId: String) { payRequests.value = PayCardRequest((payRequests.value?.n ?: 0) + 1, accountId) }
}

/**
 * What the Today widget draws (Swift, WidgetKit, in iosApp/Widget): today's spending and what the budget leaves for each remaining day, as the Android widget
 * shows them. A widget cannot open the app's database, so the app writes these few strings into the App Group's preferences whenever its data changes
 * and asks the system to redraw ([IosHostServices.reloadWidgets]).
 *
 * Privacy, as on Android: with Hide amounts or the App lock on, nothing but masked text is written. Where it differs: Android's widget asks the database
 * when it draws; here the real figures sit in the App Group's preferences (inside this app's own protected storage, not shared with other apps) for as
 * long as neither of those is on, and the eye on the widget only decides whether they are shown.
 */
internal object IosWidgetData {
    const val GROUP = "group.app.expenses.tracker.ios"

    suspend fun publish(container: AppContainer, lockEnabled: Boolean) {
        val defaults = NSUserDefaults(suiteName = GROUP) ?: return
        container.write { ok ->
            val hide = ok.templates.setting("hide_amounts") == "1"
            val canToggle = !hide && !lockEnabled
            val zone = TimeZone.currentSystemDefault()
            val today = Clock.System.now().toLocalDateTime(zone).date
            val currency = BaseCurrency.current
            val startOfDay = today.atStartOfDayIn(zone).toEpochMilliseconds()
            val spent = ok.store.spendByCategory(currency, from = startOfDay).values.fold(Money.zero(currency)) { a, m -> a + m }
            val perDay = ok.budgets.status(today, zone, currency).safeToSpend?.takeIf { it.perDay.minorUnits > 0 }?.perDay
            fun real(m: Money) = if (canToggle) m.formatted() else AmountMask.mask(m.formatted())
            defaults.setObject(real(spent), forKey = "today_spent")
            defaults.setObject(perDay?.let { real(it) + " a day left" } ?: "", forKey = "today_per_day")
            defaults.setObject(AmountMask.mask(spent.formatted()), forKey = "today_spent_masked")
            defaults.setObject(perDay?.let { AmountMask.mask(it.formatted()) + " a day left" } ?: "", forKey = "today_per_day_masked")
            defaults.setBool(canToggle, forKey = "can_toggle")
            defaults.setObject(nowMillis().toString(), forKey = "published_at")
        }
        // the app's own data decided; the system redraws the widget from what was just written
    }
}

/** Test entry for the Swift unit tests: writes what the widget reads now, as the app does when its data changes ([lockEnabled] stands in for the app lock). */
object IosWidgetDebug {
    suspend fun publishNow(host: IosHostServices, lockEnabled: Boolean) = IosWidgetData.publish(IosCore.get(host).container, lockEnabled)

    /** Turns Hide amounts on or off, as its switch in More does. */
    suspend fun setHideAmounts(host: IosHostServices, on: Boolean) { IosCore.get(host).container.write { it.templates.putSetting("hide_amounts", if (on) "1" else "0") } }
}

/**
 * Words handed over by the Share extension ("Share to Expenses" from Messages, Mail or any app). The extension only queues them in the App Group's preferences;
 * the app reads the queue whenever it comes forward and judges each text as a pasted one ([MessageIngest.ingest] with no sender): a bank transaction goes to To review,
 * a bill to Bills, anything else is dropped with only a count kept.
 */
internal object IosSharedTexts {
    private const val KEY = "shared_texts"

    /** Reads and clears the queue. Returns how many texts were taken. */
    suspend fun drain(container: AppContainer): Int {
        val defaults = NSUserDefaults(suiteName = IosWidgetData.GROUP)
        val queued = defaults.stringArrayForKey(KEY)?.filterIsInstance<String>().orEmpty()
        if (queued.isEmpty()) return 0
        defaults.removeObjectForKey(KEY)
        for (text in queued) MessageIngest.ingest(container, null, text)
        return queued.size
    }
}

/** Test entry: queues a text the way the Share extension does, and drains it the way the app does. */
object IosSharedTextsDebug {
    fun queue(text: String) {
        val defaults = NSUserDefaults(suiteName = IosWidgetData.GROUP)
        defaults.setObject((defaults.stringArrayForKey("shared_texts").orEmpty() + text), forKey = "shared_texts")
    }

    suspend fun drain(host: IosHostServices): Int = IosSharedTexts.drain(IosCore.get(host).container)
}

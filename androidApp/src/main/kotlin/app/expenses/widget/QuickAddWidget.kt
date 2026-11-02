package app.expenses.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import android.util.Log
import app.expenses.BuildConfig
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.ImageProvider
import androidx.glance.layout.size
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.SizeMode
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Box
import androidx.glance.layout.Spacer
import androidx.glance.layout.width
import androidx.compose.ui.unit.DpSize
import app.expenses.R
import app.expenses.core.domain.AmountMask
import app.expenses.core.domain.RecentInput
import app.expenses.core.domain.WidgetInsights
import app.expenses.core.domain.WidgetSummary
import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.Money
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.expenses.EntryActions
import app.expenses.ExpensesApp
import app.expenses.MainActivity
import app.expenses.AppContainer
import app.expenses.core.domain.formatted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/** One quick-entry button on the widget: [label] is its name, [detail] its amount. */
data class WidgetButton(val templateId: String, val label: String, val detail: String = "")

/** The "Added … · Undo" line, shown for a short time after a tap so a mistaken tap can be taken back. */
data class WidgetUndo(val entryId: String, val text: String)

/**
 * Why the app keeps the month's figure masked whatever the eye says, in the words the widget shows so that a masked widget never looks broken. [caption] replaces "This month" (short: it has to
 * fit above the figure of a 2 x 2), [note] is the full sentence the bigger sizes add. Tapping either opens the app.
 */
enum class HiddenBy(val caption: String, val note: String) {
    APP_LOCK("App lock is on", "Amounts stay hidden while the App lock is on. Tap to open the app."),
    HIDE_AMOUNTS("Amounts hidden", "Hide amounts is on in the app (More). Tap to open the app."),
}

/**
 * Everything the widget draws. Kept separate from Glance so it can be built and tested without a launcher. [monthSpend] is what has been spent this calendar month, already masked when amounts are hidden.
 * [insights] are the comparison, budget, categories and recent entries the bigger sizes add; they are empty unless the total is shown ([revealed]). [nothingYet] is true when this month has no spending.
 * [needsApp] is true when the database could not be read at all (the widget then says so and a tap opens the app).
 */
data class WidgetModel(
    val buttons: List<WidgetButton>, val undo: WidgetUndo?, val needsApp: Boolean, val monthSpend: String? = null,
    /** Whether the total is shown in full; false when it is masked. [canToggle] is false when the app's own Hide amounts or the App lock forces it masked, so the eye is not offered. */
    val revealed: Boolean = false, val canToggle: Boolean = false,
    val insights: WidgetInsights = WidgetInsights.NONE, val nothingYet: Boolean = false,
    /** Set exactly when [canToggle] is false because of a setting of the app: the widget then says which one. */
    val hiddenBy: HiddenBy? = null,
)

/** What the widget reads from the database. */
data class WidgetData(val buttons: List<WidgetButton>, val monthSpend: String, val revealed: Boolean, val canToggle: Boolean, val insights: WidgetInsights = WidgetInsights.NONE, val nothingYet: Boolean = false, val hiddenBy: HiddenBy? = null)

/**
 * One tag for everything the widgets log, so that a widget that does nothing on a phone can be diagnosed from `adb logcat -s ExpensesWidget`. It never logs an amount, a name or any text from the ledger:
 * only which action ran, how it ended and how long it took.
 */
internal object WidgetLog {
    const val TAG = "ExpensesWidget"
    fun i(message: String) { Log.i(TAG, message) }
    fun w(message: String) { Log.w(TAG, message) }
    fun e(message: String, t: Throwable? = null) { Log.e(TAG, message, t) }

    /** How long this process has lived: near zero means the tap itself started the app's process (nothing was warm), which is when a slow start matters. */
    fun processAgeMs(): Long = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
}

internal fun hiddenBy(hide: Boolean, locked: Boolean): HiddenBy? = if (locked) HiddenBy.APP_LOCK else if (hide) HiddenBy.HIDE_AMOUNTS else null

/** The widget's own switch for showing the month's total. Off unless the person turns it on from the widget: not everyone wants spending on the home screen. */
internal const val SETTING_WIDGET_SHOW_TOTAL = "widget_show_total"

/**
 * How long the "✓ Chai ₹20 added  Undo" line stays on the widget after a tap: long enough to read it and to take back a slip, short enough that the widget goes back to being calm. The line is drawn only
 * while now is before the moment stored with it ([LAST_AT] plus this), so every redraw (the eye, the app leaving the screen, the hourly check) drops it by itself; [scheduleUndoExpiry] makes sure one
 * redraw happens right after it runs out even when nothing else does. A tap on Undo after that is refused ([EntryActions.undo] checks the entry's age) and says so.
 */
const val UNDO_WINDOW_MILLIS = 10_000L
private const val MAX_BUTTONS = 6

val TEMPLATE_ID = ActionParameters.Key<String>("template_id")
val UNDO_ENTRY_ID = ActionParameters.Key<String>("undo_entry_id")
internal val LAST_ID = stringPreferencesKey("last_id")
internal val LAST_TEXT = stringPreferencesKey("last_text")
internal val LAST_AT = longPreferencesKey("last_at")
/** Counts how often [refreshWidgets] asked this widget to read the database again. See [reloadedOnRefresh]. */
internal val REFRESHED = longPreferencesKey("refreshed")

/**
 * What a widget draws from the database, read again whenever [refreshWidgets] bumps [REFRESHED] in its state.
 *
 * Glance keeps a widget's composition alive for about 45 seconds after it draws (longer while taps keep arriving). An update that finds it alive does NOT run
 * `provideGlance` again: it only re-reads the widget's Glance state and recomposes. Data read once in `provideGlance` therefore stays as it was, and a tap on the
 * eye (which changes the database, then asks for an update) redrew the widget with the old, still masked figures. Reading again here, keyed on the state that
 * every refresh changes, makes the redraw show what the database says whether or not a composition was alive. [firstStamp] is the counter as it was before [first]
 * was read, so a refresh that lands between that read and the first composition is not lost.
 */
@Composable
internal fun <T> reloadedOnRefresh(first: T, firstStamp: Long?, load: suspend () -> T): T {
    val stamp = currentState<Preferences>()[REFRESHED]
    var value by remember { mutableStateOf(first) }
    LaunchedEffect(stamp) { if (stamp != firstStamp) value = load() }
    return value
}

/**
 * A home-screen widget: this month's spending, an Add button, and the saved quick entries as one-tap chips; the taller sizes add how the month compares with the last, the budget and
 * where the money went. It is drawn for the exact size it has been given ([SizeMode.Exact]), so bars are true to scale and the layout follows every resize ([WidgetSummary.plan]).
 */
class QuickAddWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val stamp = getAppWidgetState<Preferences>(context, id)[REFRESHED] // read first: whatever is written after this is noticed by [reloadedOnRefresh]
        val first = load(context)
        provideContent {
            val prefs = currentState<Preferences>()
            val loaded = reloadedOnRefresh(first, stamp) { load(context) }
            WidgetContent(model(loaded?.buttons, prefs, System.currentTimeMillis(), loaded?.monthSpend, loaded?.revealed ?: false, loaded?.canToggle ?: false, loaded?.insights ?: WidgetInsights.NONE, loaded?.nothingYet ?: false, loaded?.hiddenBy))
        }
    }

    /**
     * The quick entries and this month's spending, read through the write lock like every other read of the database. Null when the data is not available (the key is gone, or the database
     * could not be opened or read): the widget then says "Open the app" instead of showing Glance's "Problem loading widget", and the reason is in the log (tag [WidgetLog.TAG]).
     */
    internal suspend fun load(context: Context): WidgetData? {
        val container = (context.applicationContext as ExpensesApp).container
        val data = try {
            container.write { ok ->
                val hide = ok.templates.setting("hide_amounts") == "1"
                fun shown(t: String) = if (hide) AmountMask.mask(t) else t
                val buttons = ok.templates.list().filter { it.amount != null }.take(MAX_BUTTONS).map { WidgetButton(it.id, it.name, shown(it.amount!!.formatted())) }
                val zone = java.time.ZoneId.systemDefault()
                val today = java.time.LocalDate.now(zone)
                val first = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val currency = BaseCurrency.current
                val byCategory = ok.store.spendByCategory(currency, from = first)
                val spent = byCategory.values.fold(Money.zero(currency)) { a, m -> a + m }
                // with the App lock on, the total of everything spent is not shown on the home screen, which anyone holding the unlocked phone can see
                val locked = (context.applicationContext as ExpensesApp).lock.settings.value.enabled
                val canToggle = !hide && !locked
                val revealed = canToggle && ok.templates.setting(SETTING_WIDGET_SHOW_TOTAL) == "1"
                // the rest of what the bigger sizes show is read only when it may be shown at all: a hidden widget costs what it always did
                val insights = if (revealed) insights(ok, today, zone, currency, spent, byCategory) else WidgetInsights.NONE
                WidgetData(buttons, monthFigure(spent.formatted(), hide, locked, revealed), revealed, canToggle, insights, nothingYet = spent.minorUnits <= 0L && byCategory.isEmpty(), hiddenBy = hiddenBy(hide, locked))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            WidgetLog.e("reading the widget's figures failed", e)
            return null
        }
        if (data == null) WidgetLog.w("the widget's figures could not be read: the database key is unavailable")
        return data
    }

    /** The comparison with last month up to the same day, the overall budget, the top categories and the newest entries: a few indexed reads, all after the total itself was allowed to show. */
    private fun insights(ok: AppContainer.Opened.Ok, today: java.time.LocalDate, zone: java.time.ZoneId, currency: app.expenses.core.domain.Currency, spent: Money, byCategory: Map<app.expenses.core.domain.ledger.CategoryId?, Money>): WidgetInsights {
        val lastFirst = today.withDayOfMonth(1).minusMonths(1)
        val sameDays = minOf(today.dayOfMonth, lastFirst.lengthOfMonth())
        val lastFrom = lastFirst.atStartOfDay(zone).toInstant().toEpochMilli()
        val lastUntil = lastFirst.plusDays(sameDays.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val lastSoFar = ok.store.spendByCategory(currency, from = lastFrom, until = lastUntil).values.fold(Money.zero(currency)) { a, m -> a + m }
        val categories = ok.categories.list()
        val names = categories.associate { it.id to it.name }
        val parents = categories.filter { it.parentId != null }.associate { it.id to it.parentId }
        val bar = WidgetSummary.topCategories(byCategory.mapKeys { it.key?.value }, parents, names, max = WidgetSummary.Room.MAX_CATEGORIES)
        val kToday = kotlinx.datetime.LocalDate(today.year, today.monthValue, today.dayOfMonth)
        val overall = ok.budgets.status(kToday, kotlinx.datetime.TimeZone.currentSystemDefault(), currency).progress.firstOrNull { it.categoryId == null }
        val recent = ok.db.schemaQueries.recentEntries(12).executeAsList().filter { it.currency == currency.code }
            .map { RecentInput(it.kind, it.merchant ?: it.note, it.category_id?.let(names::get), Money(it.amount_minor, currency), it.occurred_at) }
        return WidgetSummary.insights(
            revealed = true, delta = WidgetSummary.delta(spent, lastSoFar), budget = WidgetSummary.budget(overall?.limit, overall?.spent ?: Money.zero(currency)),
            categories = bar, recent = WidgetSummary.recent(recent, System.currentTimeMillis(), kotlinx.datetime.TimeZone.currentSystemDefault(), WidgetSummary.Room.MAX_RECENT),
        )
    }

    companion object {
        /** The month's total as the widget may show it: masked when amounts are hidden, and also when the App lock is on, since anyone holding the unlocked phone can read the home screen. */
        fun monthFigure(spent: String, hide: Boolean, locked: Boolean, revealed: Boolean = true): String = if (hide || locked || !revealed) AmountMask.mask(spent) else spent

        /** Builds what to draw. The undo line is shown only while it is still allowed to work. */
        fun model(buttons: List<WidgetButton>?, prefs: Preferences, now: Long, monthSpend: String? = null, revealed: Boolean = false, canToggle: Boolean = false, insights: WidgetInsights = WidgetInsights.NONE, nothingYet: Boolean = false, hiddenBy: HiddenBy? = null): WidgetModel {
            val id = prefs[LAST_ID]
            val at = prefs[LAST_AT]
            val undo = if (id != null && at != null && now - at in 0..UNDO_WINDOW_MILLIS) WidgetUndo(id, prefs[LAST_TEXT] ?: "Added") else null
            return WidgetModel(buttons.orEmpty(), undo, needsApp = buttons == null, monthSpend = monthSpend, revealed = revealed, canToggle = canToggle, insights = insights, nothingYet = nothingYet, hiddenBy = hiddenBy)
        }
    }
}

class QuickAddReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}

// The same widget offered at four starting sizes, so the picker lists each; every one can still be resized, and the layout follows whatever size it ends up.
class QuickAddSmallReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = QuickAddWidget() }
class QuickAddWideReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = QuickAddWidget() }
class QuickAddLargeReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = QuickAddWidget() }

internal fun openIntent() = Intent().setClassName(BuildConfig.APPLICATION_ID, "app.expenses.MainActivity")

internal fun addIntent() = Intent().setClassName(BuildConfig.APPLICATION_ID, "app.expenses.MainActivity").setAction(MainActivity.ACTION_ADD_EXPENSE)

/**
 * Runs the body of one widget tap and turns an exception into a line in the log (tag [WidgetLog.TAG]) and a null result, instead of a tap that silently does nothing. The log has which tap it was, how
 * old the process was (near zero: the tap started the app's process) and how long it took; never an amount or a name.
 */
private suspend fun <T> guarded(what: String, step: Boolean = false, block: suspend () -> T): T? {
    val started = SystemClock.elapsedRealtime()
    if (!step) WidgetLog.i("$what: tapped (process age ${WidgetLog.processAgeMs()} ms)")
    return try {
        block().also { if (!step) WidgetLog.i("$what: finished in ${SystemClock.elapsedRealtime() - started} ms") }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        WidgetLog.e("$what: failed after ${SystemClock.elapsedRealtime() - started} ms", e)
        null
    }
}

/** Tap on a quick-entry button: records the expense and remembers it so it can be undone for [UNDO_WINDOW_MILLIS]. */
class QuickAddAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val templateId = parameters[TEMPLATE_ID]
        if (templateId == null) { WidgetLog.w("chip: tapped without a quick entry id, nothing done"); return }
        val container = (context.applicationContext as ExpensesApp).container
        // with Hide amounts on the confirmation (the widget's undo line and the message) says "₹••••" like the chips do
        val added = guarded("chip") { container.write { ok -> EntryActions(ok).quickAdd(templateId, maskAmount = ok.templates.setting("hide_amounts") == "1") } }
        if (added != null) {
            guarded("chip: remembering the undo", step = true) {
                // one change of this widget's state: the confirmation line AND the order to read the new total, so the total and the line arrive together
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[LAST_ID] = added.entryId
                    prefs[LAST_TEXT] = "✓ ${added.name} ${added.amount.removeSuffix(".00")} added"
                    prefs[LAST_AT] = System.currentTimeMillis()
                    prefs[REFRESHED] = (prefs[REFRESHED] ?: 0L) + 1
                }
            }
            guarded("chip: scheduling the undo line's end", step = true) { scheduleUndoExpiry(context) }
        } else WidgetLog.w("chip: nothing was added (the quick entry is gone, or the database could not be opened)")
        refreshWidgets(context)
        toast(context, added?.text ?: "Could not add this entry. Open the app.")
        // Someone who only uses the widget never opens the app, so tidy the search index here, after the confirmation is
        // already on screen. Without this, unmerged index segments pile up until SQLite forces a 50-100 ms merge in the
        // middle of a save (measured at about 1,000 unmerged saves). Bounded, and does nothing if there is nothing to merge.
        if (added != null) guarded("chip: tidying the search index", step = true) { container.maintain(budgetMillis = WIDGET_MAINTENANCE_MILLIS) }
    }
}

private const val UNDO_EXPIRY_WORK = "widget-undo-expiry"

/**
 * Redraws the widgets just after the Undo line has run out, without the app having to run. Two ways, either is enough: while the app's process still lives (it usually does for a while after a tap)
 * a coroutine waits the exact time; and a one-time WorkManager job with that delay (no new permission: the system runs it when it can, normally within seconds) covers the process being gone by then.
 * A second tap replaces the job. Redraws are idempotent, so both running does no harm.
 */
private fun scheduleUndoExpiry(context: Context) {
    val app = context.applicationContext as ExpensesApp
    val after = UNDO_WINDOW_MILLIS + 500L
    app.appScope.launch { delay(after); runCatching { refreshWidgets(app) } }
    WorkManager.getInstance(app).enqueueUniqueWork(UNDO_EXPIRY_WORK, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<WidgetUndoExpiryWorker>().setInitialDelay(after + 500L, TimeUnit.MILLISECONDS).build())
}

/** The one-time job behind [scheduleUndoExpiry]: draws the widgets again. */
class WidgetUndoExpiryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetLog.i("undo line: the time is up, redrawing (process age ${WidgetLog.processAgeMs()} ms)")
        runCatching { refreshWidgets(applicationContext) }
        return Result.success()
    }
}

/** Redraws every widget of the app (quick add and Today) from the database. Called whenever what they show may have changed. */
suspend fun refreshWidgets(context: Context) {
    refresh(QuickAddWidget(), context)
    refresh(TodayWidget(), context)
}

/** Bumps [REFRESHED] in each copy's state, which is what makes a widget with a live composition read the database again, then asks for the redraw. One widget failing must not stop the other. */
private suspend fun refresh(widget: GlanceAppWidget, context: Context) {
    try {
        GlanceAppWidgetManager(context).getGlanceIds(widget.javaClass).forEach { id -> updateAppWidgetState(context, id) { it[REFRESHED] = (it[REFRESHED] ?: 0L) + 1 } }
        widget.updateAll(context)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        WidgetLog.e("redrawing ${widget.javaClass.simpleName} failed", e)
    }
}

/** How long the widget may spend merging the search index after a tap. */
const val WIDGET_MAINTENANCE_MILLIS = 300L

/**
 * Tap on the eye: flips whether the month's total is shown, for every copy of both widgets, and redraws them.
 *
 * Design: a shown total stays shown until the eye is tapped again; it is not put back after a while or when the screen locks. The person chose to show it on
 * their own home screen, and re-masking on a timer would hide it again while they are still reading it. What does mask it regardless is the App lock and the
 * app's own Hide amounts, both of which also take the eye away (see [QuickAddWidget.load]).
 */
class ToggleTotalAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val container = (context.applicationContext as ExpensesApp).container
        val nowShown = guarded("eye") {
            container.write { ok ->
                val show = ok.templates.setting(SETTING_WIDGET_SHOW_TOTAL) != "1"
                ok.templates.putSetting(SETTING_WIDGET_SHOW_TOTAL, if (show) "1" else "0")
                show
            }
        }
        if (nowShown == null) { WidgetLog.w("eye: the setting could not be changed (the database could not be opened)"); toast(context, "Could not read your data. Open the app.") }
        else WidgetLog.i("eye: the total is now ${if (nowShown) "shown" else "hidden"}")
        refreshWidgets(context)
    }
}

/** Tap on Undo: takes back the last widget entry, but only within [UNDO_WINDOW_MILLIS]. */
class UndoAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entryId = parameters[UNDO_ENTRY_ID]
        if (entryId == null) { WidgetLog.w("undo: tapped without an entry id, nothing done"); return }
        val container = (context.applicationContext as ExpensesApp).container
        val undone = guarded("undo") { container.write { ok -> EntryActions(ok).undo(entryId, UNDO_WINDOW_MILLIS) } } == true
        guarded("undo: forgetting the undo", step = true) { updateAppWidgetState(context, glanceId) { prefs -> prefs.remove(LAST_ID); prefs.remove(LAST_TEXT); prefs.remove(LAST_AT) } }
        refreshWidgets(context)
        toast(context, if (undone) "Undone" else "Too late to undo from here")
    }
}

/** A short message on the screen; never lets a refusal to show one (some phones restrict messages from the background) undo the tap that is already done. */
private suspend fun toast(context: Context, text: String) {
    try {
        withContext(Dispatchers.Main) { Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        WidgetLog.w("the message could not be shown: ${e.javaClass.simpleName}")
    }
}

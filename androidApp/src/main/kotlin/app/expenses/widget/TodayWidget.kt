package app.expenses.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.layout.height
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.expenses.BuildConfig
import app.expenses.ExpensesApp
import app.expenses.MainActivity
import app.expenses.R
import app.expenses.core.domain.Accent
import app.expenses.core.domain.AmountMask
import app.expenses.core.domain.Appearances
import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.Money
import app.expenses.core.domain.formatted
import app.expenses.core.domain.WidgetAccess
import app.expenses.core.domain.WidgetBudget
import app.expenses.core.domain.WidgetFeeds
import app.expenses.core.domain.WidgetSummary
import androidx.glance.currentState
import androidx.glance.layout.width

/**
 * What the Today widget draws: today's spending and what is left to spend each day, masked under the same rules as the other widget. UI S8: [budget] is the overall limit's pace bar (only while
 * the figures are shown), and [chip] the first quick entry, offered while the figures are masked so that a masked widget still captures (its name only, never an amount).
 */
data class TodayData(
    val spentToday: String, val perDayLine: String?, val revealed: Boolean, val canToggle: Boolean, val hiddenBy: HiddenBy? = null, val accent: Accent = Appearances.ACCENTS.first(),
    val budget: WidgetBudget? = null, val chip: WidgetButton? = null,
)

/** Under this many dp of free width in the bottom row, the Today widget leaves the quick-entry chip out: a chip narrower than its name is no use. */
private const val TODAY_CHIP_ROOM = 64

/**
 * A small home-screen widget: what has been spent today, and what the budget leaves for each remaining day. Both figures follow the same privacy rules as
 * the month total on the quick-add widget: hidden unless the person turned them on from the eye, and hidden whenever Hide amounts or the App lock is on.
 * UI S8 (U-23): shown, it is one number, the pace line ("₹1,565.00 a day keeps you on pace") and the budget's pace bar; masked, it says so and keeps a quick-entry chip, so it is never an empty card.
 */
class TodayWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: androidx.glance.appwidget.SizeMode = androidx.glance.appwidget.SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val stamp = getAppWidgetState<Preferences>(context, id)[REFRESHED] // read first, as the quick add widget does
        val first = loadToday(context)
        provideContent {
            // the chip's "✓ Chai ₹20 added · Undo" line, kept in this widget's own state by the chip's tap exactly as on the quick add widget
            val undo = QuickAddWidget.model(emptyList(), currentState<Preferences>(), System.currentTimeMillis()).undo
            TodayContent(reloadedOnRefresh(first, stamp) { loadToday(context) }, androidx.glance.LocalSize.current.width.value.toInt(), androidx.glance.LocalSize.current.height.value.toInt(), undo)
        }
    }

    /** Today's figures, or null when the database could not be read (then the widget says "Open the app"; the reason is in the log, tag [WidgetLog.TAG]). */
    internal suspend fun loadToday(context: Context): TodayData? {
        val app = context.applicationContext as ExpensesApp
        val data = try {
            app.container.write { ok ->
                val hide = ok.templates.setting("hide_amounts") == "1"
                val allowed = WidgetAccess.allowed(ok.templates.setting(WidgetAccess.SETTING))
                val locked = app.lock.settings.value.enabled
                val canToggle = allowed && !hide && !locked
                val revealed = canToggle && ok.templates.setting(SETTING_WIDGET_SHOW_TOTAL) == "1"
                val zone = java.time.ZoneId.systemDefault()
                val date = java.time.LocalDate.now(zone)
                val currency = BaseCurrency.current
                val startOfDay = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val spent = ok.store.spendByCategory(currency, from = startOfDay).values.fold(Money.zero(currency)) { a, m -> a + m }
                val kToday = kotlinx.datetime.LocalDate(date.year, date.monthValue, date.dayOfMonth)
                val status = ok.budgets.status(kToday, kotlinx.datetime.TimeZone.currentSystemDefault(), currency)
                val perDay = status.safeToSpend?.takeIf { it.perDay.minorUnits > 0 }?.perDay
                fun figure(t: String) = QuickAddWidget.monthFigure(t, hide, locked, revealed)
                // the bar is a figure too (its length is the share spent): read only when the figures may be shown
                val budget = if (!revealed) null else status.progress.firstOrNull { it.categoryId == null }?.let { p ->
                    WidgetSummary.budget(p.limit, p.spent, WidgetFeeds.pace(p.spent, p.limit, status.period, kToday, status.safeToSpend?.upcoming))
                }
                val chip = if (!allowed || revealed) null else ok.templates.list().firstOrNull { it.amount != null }?.let { WidgetButton(it.id, it.name) }
                TodayData(
                    figure(spent.formatted()), if (!allowed) null else WidgetFeeds.perDayLine(perDay)?.let(::figure), revealed, canToggle, hiddenBy(hide, locked, off = !allowed),
                    Appearances.decode(ok.templates.setting(SETTING_APPEARANCE)).accent, budget, chip,
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            WidgetLog.e("reading the Today widget's figures failed", e)
            return null
        }
        if (data == null) WidgetLog.w("the Today widget's figures could not be read: the database key is unavailable")
        return data
    }
}

class TodayReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TodayWidget() }

/**
 * The Today widget's card. Shown: caption, today's figure, the pace line and (with the height for it) the budget's pace bar. Masked: the figure masked, a line saying so (or which setting of the app
 * hides it), and the first quick entry as a chip beside the eye and Add when the width allows. For the ten seconds after a chip's tap ([undo]) the bottom row is the "added · Undo" line.
 */
@Composable
internal fun TodayContent(data: TodayData?, widthDp: Int, heightDp: Int, undo: WidgetUndo? = null) {
    // Two 48 dp targets (the eye and Add) side by side take 96 of the 110 dp this widget is at the least, so the bottom row has the full width and the texts keep their own 14 dp margin;
    // the whole card (anything that is not a control) opens the app.
    val accent = data?.accent ?: Appearances.ACCENTS.first()
    Column(GlanceModifier.fillMaxSize().appWidgetBackground().background(ImageProvider(R.drawable.widget_bg)).clickable(actionStartActivity(openIntent())).padding(vertical = 2.dp)) {
        Column(GlanceModifier.padding(horizontal = 14.dp)) {
            Text("Spent today", style = TextStyle(color = WidgetColors.muted, fontSize = 11.sp), maxLines = 1)
            val text = data?.spentToday ?: "Open the app"
            Text(text, style = TextStyle(color = WidgetColors.text, fontSize = amountSp(text, widthDp - 28f, 22f, 14f).sp, fontWeight = FontWeight.Bold), maxLines = 1)
            val note = data?.let { it.hiddenBy?.caption ?: if (!it.revealed) TODAY_HIDDEN else null }
            note?.let { Text(it, style = TextStyle(color = WidgetColors.muted, fontSize = 11.sp), maxLines = 1, modifier = GlanceModifier.padding(top = 2.dp)) }
            if (data != null && data.revealed) {
                if (heightDp >= 128) data.perDayLine?.let { Text(it, style = TextStyle(color = WidgetColors.muted, fontSize = 11.sp), maxLines = 2, modifier = GlanceModifier.padding(top = 2.dp)) }
                if (heightDp >= 150) data.budget?.let { PaceBar(widthDp - 28f, it, accent, words = false) }
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        val pad = ((widthDp - 2 * TARGET) / 2).coerceIn(0, 8)
        if (undo != null) Box(GlanceModifier.fillMaxWidth().padding(horizontal = pad.dp)) { Undo(undo, top = 0.dp) }
        else Row(GlanceModifier.fillMaxWidth().padding(horizontal = pad.dp), verticalAlignment = Alignment.CenterVertically) {
            val eye = data != null && data.canToggle
            if (eye) Eye(data!!.revealed)
            val chip = data?.chip?.takeIf { widthDp - 2 * pad - TARGET - 8 - (if (eye) TARGET else 0) >= TODAY_CHIP_ROOM }
            if (chip != null) {
                Spacer(GlanceModifier.width(4.dp)); Chip(chip, GlanceModifier.defaultWeight(), showDetail = false); Spacer(GlanceModifier.width(4.dp))
            } else Spacer(GlanceModifier.defaultWeight())
            AddButton(wide = false, accent = accent)
        }
    }
}

/** What the masked Today widget says when only its own eye hides the figures (the app's settings name themselves, see [HiddenBy]). */
internal const val TODAY_HIDDEN = "Amounts hidden"

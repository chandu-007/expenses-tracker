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
import app.expenses.core.domain.AmountMask
import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.Money
import app.expenses.core.domain.formatted

/** What the Today widget draws: today's spending and what is left to spend each day, masked under the same rules as the other widget. */
data class TodayData(val spentToday: String, val perDayLine: String?, val revealed: Boolean, val canToggle: Boolean, val hiddenBy: HiddenBy? = null)

/**
 * A small home-screen widget: what has been spent today, and what the budget leaves for each remaining day. Both figures follow the same privacy rules as
 * the month total on the quick-add widget: hidden unless the person turned them on from the eye, and hidden whenever Hide amounts or the App lock is on.
 */
class TodayWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: androidx.glance.appwidget.SizeMode = androidx.glance.appwidget.SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val stamp = getAppWidgetState<Preferences>(context, id)[REFRESHED] // read first, as the quick add widget does
        val first = loadToday(context)
        provideContent { TodayContent(reloadedOnRefresh(first, stamp) { loadToday(context) }, androidx.glance.LocalSize.current.width.value.toInt(), androidx.glance.LocalSize.current.height.value.toInt()) }
    }

    /** Today's figures, or null when the database could not be read (then the widget says "Open the app"; the reason is in the log, tag [WidgetLog.TAG]). */
    internal suspend fun loadToday(context: Context): TodayData? {
        val app = context.applicationContext as ExpensesApp
        val data = try {
            app.container.write { ok ->
                val hide = ok.templates.setting("hide_amounts") == "1"
                val locked = app.lock.settings.value.enabled
                val canToggle = !hide && !locked
                val revealed = canToggle && ok.templates.setting(SETTING_WIDGET_SHOW_TOTAL) == "1"
                val zone = java.time.ZoneId.systemDefault()
                val date = java.time.LocalDate.now(zone)
                val currency = BaseCurrency.current
                val startOfDay = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val spent = ok.store.spendByCategory(currency, from = startOfDay).values.fold(Money.zero(currency)) { a, m -> a + m }
                val perDay = ok.budgets.status(kotlinx.datetime.LocalDate(date.year, date.monthValue, date.dayOfMonth), kotlinx.datetime.TimeZone.currentSystemDefault(), currency).safeToSpend?.takeIf { it.perDay.minorUnits > 0 }?.perDay
                fun figure(t: String) = QuickAddWidget.monthFigure(t, hide, locked, revealed)
                TodayData(figure(spent.formatted()), perDay?.let { figure(it.formatted()) + " a day left" }, revealed, canToggle, hiddenBy(hide, locked))
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

@Composable
private fun TodayContent(data: TodayData?, widthDp: Int, heightDp: Int) {
    val open = actionStartActivity(openIntent())
    Column(GlanceModifier.fillMaxSize().appWidgetBackground().background(ImageProvider(R.drawable.widget_bg)).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text("Spent today", style = TextStyle(color = WidgetColors.muted, fontSize = 11.sp), maxLines = 1, modifier = GlanceModifier.clickable(open))
        val text = data?.spentToday ?: "Open the app"
        Text(text, style = TextStyle(color = WidgetColors.text, fontSize = amountSp(text, 100f, 22f, 14f).sp, fontWeight = FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.clickable(open))
        data?.hiddenBy?.let { Text(it.caption, style = TextStyle(color = WidgetColors.muted, fontSize = 11.sp), maxLines = 1, modifier = GlanceModifier.padding(top = 2.dp).clickable(open)) }
        if (heightDp >= 128) data?.perDayLine?.let { Text(it, style = TextStyle(color = WidgetColors.muted, fontSize = 11.sp), maxLines = 2, modifier = GlanceModifier.padding(top = 2.dp).clickable(open)) }
        Spacer(GlanceModifier.defaultWeight())
        // the eye and Add share the bottom row: at two cells wide there is not room for them beside the caption
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val t = if (widthDp < 120) 40.dp else 44.dp
            if (data != null && data.canToggle) Eye(data.revealed, t)
            Spacer(GlanceModifier.defaultWeight())
            AddButton(wide = false, target = t)
        }
    }
}

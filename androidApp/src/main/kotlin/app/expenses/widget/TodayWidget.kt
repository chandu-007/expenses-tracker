package app.expenses.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
data class TodayData(val spentToday: String, val perDayLine: String?, val revealed: Boolean, val canToggle: Boolean)

/**
 * A small home-screen widget: what has been spent today, and what the budget leaves for each remaining day. Both figures follow the same privacy rules as
 * the month total on the quick-add widget: hidden unless the person turned them on from the eye, and hidden whenever Hide amounts or the App lock is on.
 */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadToday(context)
        provideContent { TodayContent(data) }
    }

    internal suspend fun loadToday(context: Context): TodayData? {
        val app = context.applicationContext as ExpensesApp
        return app.container.write { ok ->
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
            TodayData(figure(spent.formatted()), perDay?.let { figure(it.formatted()) + " a day left" }, revealed, canToggle)
        }
    }
}

class TodayReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = TodayWidget() }

@Composable
private fun TodayContent(data: TodayData?) {
    val text = ColorProvider(day = androidx.compose.ui.graphics.Color(0xFF1C1B1F), night = androidx.compose.ui.graphics.Color(0xFFE6E1E5))
    val muted = ColorProvider(day = androidx.compose.ui.graphics.Color(0xFF625B71), night = androidx.compose.ui.graphics.Color(0xFFCAC4D0))
    val onAccent = ColorProvider(day = androidx.compose.ui.graphics.Color(0xFFFFFFFF), night = androidx.compose.ui.graphics.Color(0xFF381E72))
    val open = actionStartActivity(android.content.Intent().setClassName(BuildConfig.APPLICATION_ID, "app.expenses.MainActivity"))
    val add = actionStartActivity(android.content.Intent().setClassName(BuildConfig.APPLICATION_ID, "app.expenses.MainActivity").setAction(MainActivity.ACTION_ADD_EXPENSE))
    Column(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(12.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Spent today", style = TextStyle(color = muted, fontSize = 11.sp), modifier = GlanceModifier.clickable(open))
            if (data != null && data.canToggle) Image(
                ImageProvider(if (data.revealed) R.drawable.ic_widget_eye_off else R.drawable.ic_widget_eye),
                contentDescription = if (data.revealed) "Hide the amount" else "Show the amount", colorFilter = ColorFilter.tint(muted),
                modifier = GlanceModifier.padding(start = 8.dp).size(20.dp).clickable(actionRunCallback<ToggleTotalAction>()),
            )
        }
        Text(data?.spentToday ?: "Open the app", style = TextStyle(color = text, fontSize = 22.sp, fontWeight = FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.padding(top = 2.dp).clickable(open))
        data?.perDayLine?.let { Text(it, style = TextStyle(color = muted, fontSize = 12.sp), maxLines = 2, modifier = GlanceModifier.padding(top = 2.dp).clickable(open)) }
        Spacer(GlanceModifier.defaultWeight())
        Box(GlanceModifier.background(ImageProvider(R.drawable.widget_pill)).padding(horizontal = 16.dp, vertical = 8.dp).clickable(add), contentAlignment = Alignment.Center) {
            Text("+ Add", style = TextStyle(color = onAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }
    }
}

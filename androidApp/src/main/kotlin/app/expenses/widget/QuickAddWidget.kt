package app.expenses.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
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

/** One quick-entry button on the widget: [label] is its name, [detail] its amount. */
data class WidgetButton(val templateId: String, val label: String, val detail: String = "")

/** The "Added … · Undo" line, shown for a short time after a tap so a mistaken tap can be taken back. */
data class WidgetUndo(val entryId: String, val text: String)

/** Everything the widget draws. Kept separate from Glance so it can be built and tested without a launcher. [monthSpend] is what has been spent this calendar month, already masked when amounts are hidden. */
data class WidgetModel(val buttons: List<WidgetButton>, val undo: WidgetUndo?, val needsApp: Boolean, val monthSpend: String? = null)

/** How long after a tap the widget will still undo it. */
const val UNDO_WINDOW_MILLIS = 120_000L
private const val MAX_BUTTONS = 6

val TEMPLATE_ID = ActionParameters.Key<String>("template_id")
val UNDO_ENTRY_ID = ActionParameters.Key<String>("undo_entry_id")
internal val LAST_ID = stringPreferencesKey("last_id")
internal val LAST_TEXT = stringPreferencesKey("last_text")
internal val LAST_AT = longPreferencesKey("last_at")

/** The sizes the widget is drawn for; the launcher picks the largest of these that fits the space the person gave it. */
internal val SMALL = DpSize(110.dp, 60.dp)
internal val WIDE = DpSize(250.dp, 60.dp)
internal val MEDIUM = DpSize(250.dp, 110.dp)
internal val LARGE = DpSize(250.dp, 250.dp)

/** A home-screen widget: this month's spending, an Add button, and the saved quick entries as one-tap chips. It adapts to the size it is resized to. */
class QuickAddWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val loaded = load(context)
        provideContent {
            val prefs = currentState<Preferences>()
            WidgetContent(model(loaded?.first, prefs, System.currentTimeMillis(), loaded?.second))
        }
    }

    /** The quick entries and this month's spending, read through the write lock like every other read of the database. Null when the data is not available. */
    internal suspend fun load(context: Context): Pair<List<WidgetButton>, String?>? {
        val container = (context.applicationContext as ExpensesApp).container
        return container.write { ok ->
            val hide = ok.templates.setting("hide_amounts") == "1"
            fun shown(t: String) = if (hide) AmountMask.mask(t) else t
            val buttons = ok.templates.list().filter { it.amount != null }.take(MAX_BUTTONS).map { WidgetButton(it.id, it.name, shown(it.amount!!.formatted())) }
            val zone = java.time.ZoneId.systemDefault()
            val first = java.time.LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val currency = BaseCurrency.current
            val spent = ok.store.spendByCategory(currency, from = first).values.fold(Money.zero(currency)) { a, m -> a + m }
            buttons to shown(spent.formatted())
        }
    }

    companion object {
        /** Builds what to draw. The undo line is shown only while it is still allowed to work. */
        fun model(buttons: List<WidgetButton>?, prefs: Preferences, now: Long, monthSpend: String? = null): WidgetModel {
            val id = prefs[LAST_ID]
            val at = prefs[LAST_AT]
            val undo = if (id != null && at != null && now - at in 0..UNDO_WINDOW_MILLIS) WidgetUndo(id, prefs[LAST_TEXT] ?: "Added") else null
            return WidgetModel(buttons.orEmpty(), undo, needsApp = buttons == null, monthSpend = monthSpend)
        }
    }
}

class QuickAddReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}

// the same values as res/values/widget_colors.xml and its night version, which the shapes use (Glance's resource colour provider is not public API)
private val textColor = ColorProvider(day = Color(0xFF1C1B1F), night = Color(0xFFE6E1E5))
private val mutedColor = ColorProvider(day = Color(0xFF625B71), night = Color(0xFFCAC4D0))
private val onAccent = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF381E72))

/** How the content is laid out for a given size: what to show, so a small widget is not a squeezed big one. */
internal data class WidgetLayout(val showChips: Int, val chipsInHeader: Boolean, val showUndo: Boolean, val showHint: Boolean, val compactHeader: Boolean) {
    companion object {
        fun of(size: DpSize, chipCount: Int, hasUndo: Boolean): WidgetLayout {
            val compact = size.width < 180.dp || size.height < 90.dp
            if (size.width < 180.dp) return WidgetLayout(0, false, false, false, true)
            // short and wide: the spending, one chip (two when there is plenty of width) and the Add pill share one row
            if (size.height < 100.dp) return WidgetLayout(minOf(if (size.width >= 340.dp) 2 else 1, chipCount), true, false, false, true)
            // padding 20 dp, header 42 dp, each chip row 42 dp (34 and a gap of 8), the undo line 42 dp
            val room = size.height.value - 20f - 42f - (if (hasUndo) 42f else 0f)
            val rows = (room / 42f).toInt().coerceIn(0, 3)
            return WidgetLayout(minOf(rows * 2, chipCount), false, hasUndo, chipCount == 0 && rows > 0, compact)
        }
    }
}

@Composable
fun WidgetContent(model: WidgetModel) {
    val size = LocalSize.current
    val layout = WidgetLayout.of(size, model.buttons.size, model.undo != null)
    Column(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(10.dp)) {
        Header(model, layout, size)
        when {
            model.needsApp -> Text("Open the app to unlock your data", style = TextStyle(color = mutedColor, fontSize = 12.sp), modifier = GlanceModifier.padding(top = 6.dp))
            layout.showHint -> Text("Save a quick entry in the app to see it here", style = TextStyle(color = mutedColor, fontSize = 12.sp), modifier = GlanceModifier.padding(top = 6.dp))
            !layout.chipsInHeader && layout.showChips > 0 -> model.buttons.take(layout.showChips).chunked(2).forEach { pair ->
                Row(GlanceModifier.fillMaxWidth().padding(top = 8.dp)) {
                    Chip(pair[0], GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(8.dp))
                    if (pair.size > 1) Chip(pair[1], GlanceModifier.defaultWeight()) else Spacer(GlanceModifier.defaultWeight())
                }
            }
        }
        if (layout.showUndo) model.undo?.let { u ->
            Row(GlanceModifier.fillMaxWidth().padding(top = 8.dp).background(ImageProvider(R.drawable.widget_undo_bg)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(u.text, style = TextStyle(color = textColor, fontSize = 12.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                Text("Undo", style = TextStyle(color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.padding(start = 8.dp).clickable(actionRunCallback<UndoAction>(actionParametersOf(UNDO_ENTRY_ID to u.entryId))))
            }
        }
    }
}

/** The top row: this month's spending (tap to open the app) and the Add pill; on a short, wide widget the first chips sit between them. */
@Composable
private fun Header(model: WidgetModel, layout: WidgetLayout, size: DpSize) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(openIntent()))) {
            val tiny = size.width < 180.dp
            // the smallest size has room for the figure only: no caption, and no ".00" so a five-digit amount still fits
            if (!tiny) Text(if (model.monthSpend == null) "Expenses" else "Spent this month", style = TextStyle(color = mutedColor, fontSize = 11.sp), maxLines = 1)
            Text(
                (model.monthSpend ?: "Quick add").let { if (tiny) it.removeSuffix(".00") else it },
                style = TextStyle(color = textColor, fontSize = if (tiny) 13.sp else if (layout.compactHeader) 17.sp else 22.sp, fontWeight = FontWeight.Bold), maxLines = 1,
            )
        }
        if (layout.chipsInHeader) model.buttons.take(layout.showChips).forEach { b ->
            Spacer(GlanceModifier.width(6.dp))
            Chip(b, GlanceModifier, showDetail = size.width >= 340.dp)
        }
        Spacer(GlanceModifier.width(if (size.width < 180.dp) 4.dp else 6.dp))
        Box(
            GlanceModifier.background(ImageProvider(R.drawable.widget_pill)).padding(horizontal = if (size.width < 180.dp) 9.dp else if (layout.compactHeader) 12.dp else 16.dp, vertical = 8.dp).clickable(actionStartActivity(addIntent())),
            contentAlignment = Alignment.Center,
        ) { Text(if (size.width < 180.dp) "+" else "+ Add", style = TextStyle(color = onAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)) }
    }
}

@Composable
private fun Chip(b: WidgetButton, modifier: GlanceModifier, showDetail: Boolean = true) {
    // one line: the name, then the amount, so a chip is the same height at every size
    Row(
        modifier.background(ImageProvider(R.drawable.widget_chip_bg)).padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(actionRunCallback<QuickAddAction>(actionParametersOf(TEMPLATE_ID to b.templateId))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(b.label, style = TextStyle(color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1, modifier = GlanceModifier.defaultWeight())
        if (showDetail && b.detail.isNotEmpty()) Text(b.detail, style = TextStyle(color = mutedColor, fontSize = 12.sp), maxLines = 1, modifier = GlanceModifier.padding(start = 6.dp))
    }
}

private fun openIntent() = Intent().setClassName("app.expenses", "app.expenses.MainActivity")

private fun addIntent() = Intent().setClassName("app.expenses", "app.expenses.MainActivity").setAction(MainActivity.ACTION_ADD_EXPENSE)

/** Tap on a quick-entry button: records the expense and remembers it so it can be undone for a couple of minutes. */
class QuickAddAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val templateId = parameters[TEMPLATE_ID] ?: return
        val container = (context.applicationContext as ExpensesApp).container
        val added = container.write { ok -> EntryActions(ok).quickAdd(templateId) }
        if (added != null) {
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[LAST_ID] = added.entryId
                prefs[LAST_TEXT] = added.text
                prefs[LAST_AT] = System.currentTimeMillis()
            }
        }
        QuickAddWidget().updateAll(context)
        toast(context, added?.text ?: "Could not add this entry. Open the app.")
        // Someone who only uses the widget never opens the app, so tidy the search index here, after the confirmation is
        // already on screen. Without this, unmerged index segments pile up until SQLite forces a 50-100 ms merge in the
        // middle of a save (measured at about 1,000 unmerged saves). Bounded, and does nothing if there is nothing to merge.
        if (added != null) container.maintain(budgetMillis = WIDGET_MAINTENANCE_MILLIS)
    }
}

/** How long the widget may spend merging the search index after a tap. */
const val WIDGET_MAINTENANCE_MILLIS = 300L

/** Tap on Undo: takes back the last widget entry, but only within [UNDO_WINDOW_MILLIS]. */
class UndoAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entryId = parameters[UNDO_ENTRY_ID] ?: return
        val container = (context.applicationContext as ExpensesApp).container
        val undone = container.write { ok -> EntryActions(ok).undo(entryId, UNDO_WINDOW_MILLIS) } == true
        updateAppWidgetState(context, glanceId) { prefs -> prefs.remove(LAST_ID); prefs.remove(LAST_TEXT); prefs.remove(LAST_AT) }
        QuickAddWidget().updateAll(context)
        toast(context, if (undone) "Undone" else "Too late to undo from here")
    }
}

private suspend fun toast(context: Context, text: String) = withContext(Dispatchers.Main) {
    Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
}

package app.expenses.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Button
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
import androidx.glance.color.ColorProvider
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

/** One quick-entry button on the widget. */
data class WidgetButton(val templateId: String, val label: String)

/** The "Added … · Undo" line, shown for a short time after a tap so a mistaken tap can be taken back. */
data class WidgetUndo(val entryId: String, val text: String)

/** Everything the widget draws. Kept separate from Glance so it can be built and tested without a launcher. */
data class WidgetModel(val buttons: List<WidgetButton>, val undo: WidgetUndo?, val needsApp: Boolean)

/** How long after a tap the widget will still undo it. */
const val UNDO_WINDOW_MILLIS = 120_000L
private const val MAX_BUTTONS = 4

val TEMPLATE_ID = ActionParameters.Key<String>("template_id")
val UNDO_ENTRY_ID = ActionParameters.Key<String>("undo_entry_id")
internal val LAST_ID = stringPreferencesKey("last_id")
internal val LAST_TEXT = stringPreferencesKey("last_text")
internal val LAST_AT = longPreferencesKey("last_at")

/** A home-screen widget that records an expense from a saved quick entry with one tap. */
class QuickAddWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val templates = loadButtons(context)
        provideContent {
            val prefs = currentState<Preferences>()
            WidgetContent(model(templates, prefs, System.currentTimeMillis()))
        }
    }

    private suspend fun loadButtons(context: Context): List<WidgetButton>? {
        val container = (context.applicationContext as ExpensesApp).container
        // through the write lock like every other read of the database, so a close or a fresh start cannot shut it underneath this
        return container.write { ok ->
            ok.templates.list().filter { it.amount != null }.take(MAX_BUTTONS)
                .map { WidgetButton(it.id, "${it.name} ${it.amount!!.formatted()}") }
        }
    }

    companion object {
        /** Builds what to draw. The undo line is shown only while it is still allowed to work. */
        fun model(buttons: List<WidgetButton>?, prefs: Preferences, now: Long): WidgetModel {
            val id = prefs[LAST_ID]
            val at = prefs[LAST_AT]
            val undo = if (id != null && at != null && now - at in 0..UNDO_WINDOW_MILLIS) WidgetUndo(id, prefs[LAST_TEXT] ?: "Added") else null
            return WidgetModel(buttons.orEmpty(), undo, needsApp = buttons == null)
        }
    }
}

class QuickAddReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}

private val background = ColorProvider(day = Color(0xFFFFFBFE), night = Color(0xFF1C1B1F))
private val foreground = ColorProvider(day = Color(0xFF1C1B1F), night = Color(0xFFE6E1E5))

@Composable
fun WidgetContent(model: WidgetModel) {
    Column(GlanceModifier.fillMaxSize().background(background).padding(12.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Quick add", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = foreground), modifier = GlanceModifier.defaultWeight())
            Button("+", onClick = actionStartActivity(addIntent()))
        }
        when {
            model.needsApp -> Text("Open the app to unlock your data", style = TextStyle(color = foreground), modifier = GlanceModifier.padding(top = 8.dp))
            model.buttons.isEmpty() -> Text("Save a quick entry in the app to see it here", style = TextStyle(color = foreground), modifier = GlanceModifier.padding(top = 8.dp))
            else -> model.buttons.forEach { b ->
                Button(
                    text = b.label,
                    onClick = actionRunCallback<QuickAddAction>(actionParametersOf(TEMPLATE_ID to b.templateId)),
                    modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
        model.undo?.let { u ->
            Row(GlanceModifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(u.text, style = TextStyle(color = foreground, fontSize = 12.sp), modifier = GlanceModifier.defaultWeight())
                Button("Undo", onClick = actionRunCallback<UndoAction>(actionParametersOf(UNDO_ENTRY_ID to u.entryId)))
            }
        }
    }
}

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

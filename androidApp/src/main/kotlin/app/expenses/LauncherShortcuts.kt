package app.expenses

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.expenses.core.domain.QuickShortcuts
import app.expenses.core.domain.WidgetAccess
import kotlinx.coroutines.CancellationException

/**
 * The saved quick entries as the app icon's long-press shortcuts (UI S8, backlog N-20): "Add Chai ₹20" records the entry in one tap, as a widget chip does. No new library or permission: dynamic
 * shortcuts are `androidx.core` (already a dependency) and need nothing in the manifest; the static "Add expense" (res/xml/shortcuts.xml) stays as it was.
 *
 * Privacy follows the widgets: none while "Allow widgets" is off, and no amount in the words while Hide amounts or the App lock is on ([QuickShortcuts.labels]). A shortcut the person pinned to the
 * home screen cannot be taken away by the app: its words are updated with the others, it says only "Quick entry" while widgets are off, and its tap is refused then (see
 * [LedgerViewModel.quickAddFromShortcut]).
 *
 * Off the save path: published when the app's screen leaves (with the widgets) and when one of the settings it follows changes, on a background thread; nothing is called when the list is unchanged
 * (the launcher limits how often a background app may change it).
 */
object LauncherShortcuts {
    const val ACTION_QUICK_ADD = "app.expenses.action.QUICK_ADD"
    const val EXTRA_TEMPLATE_ID = "template_id"
    private const val PREFIX = "quick_"

    suspend fun publish(context: Context) {
        val app = context.applicationContext as ExpensesApp
        try {
            val (allowed, labels) = app.container.write { ok ->
                val allowed = WidgetAccess.allowed(ok.templates.setting(WidgetAccess.SETTING))
                allowed to QuickShortcuts.labels(ok.templates.list(), allowed, hide = ok.templates.setting("hide_amounts") == "1", locked = app.lock.settings.value.enabled)
            } ?: return
            val wanted = labels.mapIndexed { i, l ->
                ShortcutInfoCompat.Builder(app, PREFIX + l.templateId).setShortLabel(l.short).setLongLabel(l.long).setRank(i)
                    .setIcon(IconCompat.createWithResource(app, R.drawable.ic_add)).setIntent(intent(l.templateId)).build()
            }
            val current = ShortcutManagerCompat.getDynamicShortcuts(app)
            if (current.map { Triple(it.id, it.shortLabel.toString(), it.longLabel?.toString()) } != wanted.map { Triple(it.id, it.shortLabel.toString(), it.longLabel?.toString()) }) {
                ShortcutManagerCompat.setDynamicShortcuts(app, wanted)
            }
            // pinned ones live on in the launcher: give them the same words, or plain words while widgets are off (their entry may also have been deleted: then the tap says so)
            val pinned = ShortcutManagerCompat.getShortcuts(app, ShortcutManagerCompat.FLAG_MATCH_PINNED).filter { it.id.startsWith(PREFIX) }
            if (pinned.isNotEmpty()) {
                val byId = wanted.associateBy { it.id }
                val updates = pinned.mapNotNull { p ->
                    val w = byId[p.id] ?: if (!allowed) ShortcutInfoCompat.Builder(app, p.id).setShortLabel("Quick entry").setLongLabel("Quick entry")
                        .setIcon(IconCompat.createWithResource(app, R.drawable.ic_add)).setIntent(intent(p.id.removePrefix(PREFIX))).build() else null
                    w?.takeIf { it.shortLabel.toString() != p.shortLabel.toString() || it.longLabel?.toString() != p.longLabel?.toString() }
                }
                if (updates.isNotEmpty()) ShortcutManagerCompat.updateShortcuts(app, updates)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // a launcher that refuses (rate limit, no shortcut support) must never break the app; nothing personal is logged
            android.util.Log.w("ExpensesShortcuts", "the launcher shortcuts were not updated: ${e.javaClass.simpleName}")
        }
    }

    private fun intent(templateId: String) = Intent(ACTION_QUICK_ADD).setClassName(BuildConfig.APPLICATION_ID, "app.expenses.MainActivity").putExtra(EXTRA_TEMPLATE_ID, templateId)
}

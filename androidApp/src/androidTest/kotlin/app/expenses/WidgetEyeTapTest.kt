package app.expenses

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.TextView
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.Template
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.widget.QuickAddReceiver
import app.expenses.widget.QuickAddWidget
import app.expenses.widget.TodayReceiver
import app.expenses.widget.TodayWidget
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * The eye on the home-screen widgets, end to end: the widget is placed through the real system AppWidget service, its RemoteViews are
 * delivered to a host view exactly as a launcher receives them, and the eye is tapped by firing the PendingIntent the launcher would fire.
 * Invented data only.
 */
@RunWith(AndroidJUnit4::class)
class WidgetEyeTapTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val manager = AppWidgetManager.getInstance(context)
    private val host = AppWidgetHost(context, 4245)
    private var widgetId = -1
    private var hostView: AppWidgetHostView? = null
    private var scenario: androidx.test.core.app.ActivityScenario<MainActivity>? = null

    @Before fun fresh() {
        runBlocking { container.startFresh() }
        runBlocking {
            container.write { ok ->
                ok.templates.save(Template("chai", "Chai", Money(2_000, Currency.INR), CategoryId("Food"), AccountId("cash"), "Tea stall"))
                ok.store.save(app.expenses.core.domain.ledger.Expense(app.expenses.core.domain.ledger.EntryId("now"), System.currentTimeMillis(), AccountId("cash"), Money(50_000, Currency.INR), emptyList()))
            }
        }
    }

    @After fun cleanUp() {
        runCatching { inst.runOnMainSync { host.stopListening() } }
        if (widgetId >= 0) host.deleteAppWidgetId(widgetId)
        runCatching { scenario?.close() }
        runBlocking { container.startFresh() }
    }

    private fun tryBind(id: Int, provider: ComponentName): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val automation = inst.uiAutomation
            automation.adoptShellPermissionIdentity(android.Manifest.permission.BIND_APPWIDGET)
            try { manager.bindAppWidgetIdIfAllowed(id, provider) } finally { automation.dropShellPermissionIdentity() }
        } else manager.bindAppWidgetIdIfAllowed(id, provider)

    /** Android before 10 has no shell `cmd appwidget`: the system's own "create widget" dialog is accepted instead. */
    private fun bindThroughDialog(id: Int, provider: ComponentName) {
        // the system dialog names the app asking from its calling package, so it has to be started for a result from an activity
        scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity { it.startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider), 4245) }
        val deadline = System.currentTimeMillis() + 45_000
        while (System.currentTimeMillis() < deadline) {
            // the system can fail these queries while a window is opening or closing
            val create = runCatching { inst.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Create")?.firstOrNull { it.className?.toString()?.contains("Button") == true } }.getOrNull()
            if (create != null) { create.performAction(AccessibilityNodeInfo.ACTION_CLICK); return }
            Thread.sleep(500)
        }
    }

    /** Places the widget and returns its host view, or skips the test when this device will not let a test place one. */
    private fun place(receiver: Class<*>): AppWidgetHostView {
        val provider = ComponentName(context, receiver)
        widgetId = host.allocateAppWidgetId()
        if (!tryBind(widgetId, provider)) {
            bindThroughDialog(widgetId, provider)
            val deadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < deadline && !tryBind(widgetId, provider)) Thread.sleep(300)
        }
        assumeTrue("this device did not allow placing a widget from a test", manager.getAppWidgetInfo(widgetId) != null)
        val size = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250); putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 250)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 250); putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 250)
        }
        manager.updateAppWidgetOptions(widgetId, size)
        inst.runOnMainSync {
            hostView = host.createView(context, widgetId, manager.getAppWidgetInfo(widgetId))
            host.startListening()
        }
        return hostView!!
    }

    private fun views(root: View, out: MutableList<View> = ArrayList()): List<View> {
        out += root
        if (root is ViewGroup) for (i in 0 until root.childCount) views(root.getChildAt(i), out)
        return out
    }

    private fun snapshot(): Pair<List<String>, List<String>> {
        var texts = emptyList<String>(); var descriptions = emptyList<String>()
        inst.runOnMainSync {
            val all = hostView?.let { views(it) }.orEmpty()
            texts = all.filterIsInstance<TextView>().map { it.text.toString() }
            descriptions = all.mapNotNull { it.contentDescription?.toString() }
        }
        return texts to descriptions
    }

    private fun waitFor(what: String, millis: Long = 25_000, condition: (List<String>, List<String>) -> Boolean) {
        val deadline = System.currentTimeMillis() + millis
        var last = snapshot()
        while (System.currentTimeMillis() < deadline) {
            last = snapshot()
            if (condition(last.first, last.second)) return
            Thread.sleep(300)
        }
        throw AssertionError("$what: the widget showed ${last.first} with descriptions ${last.second}")
    }

    /** Fires the PendingIntent behind the view with this content description, as a tap on it from the launcher would. */
    private fun tap(description: String) {
        var done = false
        inst.runOnMainSync {
            val v = hostView?.let { views(it) }.orEmpty().firstOrNull { it.contentDescription?.toString() == description }
            // Glance wraps a clickable image in a frame that carries the click; a finger lands on the image and the touch is handled by that frame
            var target: View? = v
            while (target != null && !target.hasOnClickListeners()) target = target.parent as? View
            if (target != null) done = target.performClick()
        }
        assertTrue(done, "nothing on the widget answered a tap on '$description'")
    }

    private fun total(texts: List<String>) = texts.any { it == "₹500.00" }

    @Test
    fun theEyeOnTheQuickAddWidgetShowsTheAmountAndAnotherTapHidesItAgain() {
        place(QuickAddReceiver::class.java)
        runBlocking { QuickAddWidget().update(context, GlanceAppWidgetManager(context).getGlanceIdBy(widgetId)) }
        waitFor("masked at first, with the eye offered") { t, d -> "Spent this month" in t && "Show the amount" in d && !total(t) && "₹20.00" in t }
        tap("Show the amount")
        waitFor("the eye shows the amount") { t, d -> total(t) && "Hide the amount" in d }
        tap("Hide the amount")
        waitFor("a second tap hides it again") { t, d -> !total(t) && "Show the amount" in d }
    }

    @Test
    fun theEyeOnTheTodayWidgetShowsTheAmountToo() {
        place(TodayReceiver::class.java)
        runBlocking { TodayWidget().update(context, GlanceAppWidgetManager(context).getGlanceIdBy(widgetId)) }
        waitFor("masked at first") { t, d -> "Spent today" in t && "Show the amount" in d && t.none { it.startsWith("₹") && it.any(Char::isDigit) } }
        tap("Show the amount")
        waitFor("the eye shows today's amount") { t, d -> "Hide the amount" in d && t.any { it.startsWith("₹") && it.any(Char::isDigit) } }
    }

    @Test
    fun aTotalThatIsAlreadyShownIsOnTheWidgetWithAnEyeToHideIt() {
        runBlocking { container.write { it.templates.putSetting("widget_show_total", "1") } }
        place(QuickAddReceiver::class.java)
        runBlocking { QuickAddWidget().update(context, GlanceAppWidgetManager(context).getGlanceIdBy(widgetId)) }
        waitFor("the amount is on the widget") { t, d -> "Spent this month" in t && total(t) && "Hide the amount" in d }
    }

    @Test
    fun theAppsHideAmountsKeepsTheTotalMaskedAndOffersNoEye() {
        runBlocking { container.write { it.templates.putSetting("widget_show_total", "1"); it.templates.putSetting("hide_amounts", "1") } }
        place(QuickAddReceiver::class.java)
        runBlocking { QuickAddWidget().update(context, GlanceAppWidgetManager(context).getGlanceIdBy(widgetId)) }
        waitFor("masked, no eye") { t, d -> "Spent this month" in t && !total(t) && "Show the amount" !in d && "Hide the amount" !in d && t.none { it.startsWith("₹") && it.any(Char::isDigit) } }
    }
}

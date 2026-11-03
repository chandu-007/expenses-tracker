package app.expenses

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Accent
import app.expenses.core.domain.Appearances
import app.expenses.core.domain.DeltaDirection
import app.expenses.core.domain.WidgetBudget
import app.expenses.core.domain.WidgetCategoryBar
import app.expenses.core.domain.WidgetDelta
import app.expenses.core.domain.WidgetInsights
import app.expenses.core.domain.WidgetRecent
import app.expenses.widget.HiddenBy
import app.expenses.widget.WidgetButton
import app.expenses.widget.WidgetContent
import app.expenses.widget.WidgetModel
import app.expenses.widget.WidgetUndo
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a finger can hit on the widgets, measured on the real views the launcher would get (the widget is composed to RemoteViews, inflated and laid out at an exact size, like a launcher does),
 * and what the accent looks like in them (rendered to pixels, and a few of them written to `files/widget_shots` of the app so a person can look). Invented data only.
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(AndroidJUnit4::class)
class WidgetTargetsTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val context = inst.targetContext
    private val density = context.resources.displayMetrics.density

    private fun themed(night: Boolean): android.content.Context {
        val conf = Configuration(context.resources.configuration)
        conf.uiMode = (conf.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        return context.createConfigurationContext(conf)
    }

    /** The widget drawn for [wDp] x [hDp] exactly as a launcher would lay it out, with the phone in light or dark mode. */
    private fun render(wDp: Int, hDp: Int, model: WidgetModel, night: Boolean = false): View {
        val ctx = themed(night)
        val rv = runBlocking { GlanceRemoteViews().compose(ctx, DpSize(wDp.dp, hDp.dp)) { WidgetContent(model) } }.remoteViews
        lateinit var view: View
        val wPx = (wDp * density).roundToInt(); val hPx = (hDp * density).roundToInt()
        inst.runOnMainSync {
            view = rv.apply(ctx, FrameLayout(ctx))
            view.measure(View.MeasureSpec.makeMeasureSpec(wPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(hPx, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, wPx, hPx)
        }
        return view
    }

    private class Target(val what: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val w get() = right - left; val h get() = bottom - top
        fun overlaps(o: Target) = left < o.right && o.left < right && top < o.bottom && o.top < bottom
        override fun toString() = "$what [${left},${top} ${w}x${h}px]"
    }

    /** Every view that answers a tap, with where it is in the widget; what it is called comes from the content description or the text inside it. */
    private fun targets(root: View): List<Target> {
        val out = ArrayList<Target>()
        fun label(v: View): String {
            v.contentDescription?.let { return it.toString() }
            if (v is android.widget.TextView && v.text.isNotEmpty()) return v.text.toString()
            if (v is ViewGroup) for (i in 0 until v.childCount) label(v.getChildAt(i)).takeIf { it != "?" }?.let { return it }
            return "?"
        }
        fun walk(v: View, ox: Int, oy: Int) {
            val l = ox + v.left; val t = oy + v.top
            if (v.hasOnClickListeners()) out += Target(label(v), l, t, l + v.width, t + v.height)
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), l, t)
        }
        walk(root, -root.left, -root.top)
        return out
    }

    // ---- touch targets ----

    private val chai = WidgetButton("a", "Chai", "₹20.00")
    private val oneLetter = WidgetButton("b", "A", "₹5.00")
    private val long = WidgetButton("c", "A very long quick entry name", "₹120.00")
    private val insights = WidgetInsights(
        delta = WidgetDelta(DeltaDirection.LESS, "↓", "12%", "12% less than last month so far"),
        budget = WidgetBudget(750, false, "₹5K left of ₹20K"),
        categories = listOf(WidgetCategoryBar("Food", "₹4K", 400), WidgetCategoryBar("Bills", "₹3K", 300), WidgetCategoryBar("Travel", "₹1.5K", 150)),
        recent = listOf(WidgetRecent("Greencart", "-₹482.00", "Today", false), WidgetRecent("Salary", "+₹50,000.00", "Yesterday", true)),
    )
    private val chips = listOf(chai, oneLetter, long, WidgetButton("d", "Metro", "₹30.00"))

    private val models = mapOf(
        "masked" to WidgetModel(chips, null, needsApp = false, monthSpend = "₹••••", revealed = false, canToggle = true),
        "asking" to WidgetModel(chips, null, needsApp = false, monthSpend = "₹••••", revealed = false, canToggle = true, armed = true),
        "shown with undo" to WidgetModel(chips, WidgetUndo("e1", "✓ Chai ₹20 added"), needsApp = false, monthSpend = "₹12,345.00", revealed = true, canToggle = true, insights = insights),
        "shown no undo" to WidgetModel(chips, null, needsApp = false, monthSpend = "₹12,345.00", revealed = true, canToggle = true, insights = insights),
        "locked" to WidgetModel(chips, null, needsApp = false, monthSpend = "₹••••", revealed = false, canToggle = false, hiddenBy = HiddenBy.APP_LOCK),
        "unreadable" to WidgetModel(emptyList(), null, needsApp = true),
    )

    private val widths = listOf(110, 130, 149, 150, 170, 179, 180, 250, 320, 420)
    private val heights = listOf(56, 60, 80, 99, 100, 110, 146, 200, 294)

    @Test
    fun everyControlOnEveryWidgetSizeIsAtLeast48DpInBothDirectionsInsideTheWidgetAndApartFromTheOthers() {
        val min = (48 * density).roundToInt() - 1 // a pixel of rounding
        val problems = ArrayList<String>()
        var controls = 0
        for ((name, model) in models) for (w in widths) for (h in heights) {
            val view = render(w, h, model)
            val wPx = view.width; val hPx = view.height
            // the card itself opens the app: it is the one target that is the whole widget, and it is not a control
            val all = targets(view)
            val card = all.filter { it.w >= wPx - 2 && it.h >= hPx - 2 }
            val found = all - card.toSet()
            controls += found.size
            for (t in found) {
                if (t.w < min || t.h < min) problems += "$name $w x $h: $t is under 48 dp"
                if (t.left < -1 || t.top < -1 || t.right > wPx + 1 || t.bottom > hPx + 1) problems += "$name $w x $h: $t sticks out of the ${wPx}x${hPx}px widget"
            }
            for (i in found.indices) for (j in i + 1 until found.size) if (found[i].overlaps(found[j])) problems += "$name $w x $h: ${found[i]} overlaps ${found[j]}"
            if (card.isEmpty()) problems += "$name $w x $h: nothing opens the app from the card itself"
        }
        assertTrue(controls > 1000, "the measurement found only $controls controls: is it looking at the views?")
        assertTrue(problems.isEmpty(), "${problems.size} problems, for instance:\n" + problems.take(25).joinToString("\n"))
    }

    @Test
    fun theControlsAreTheEyeAddTheChipsUndoAndOnTheSmallestWidgetTheFigure() {
        fun names(w: Int, h: Int, m: String) = targets(render(w, h, models.getValue(m))).filter { it.w < (w * density).roundToInt() - 2 }.map { it.what }
        // 2 x 1: the figure (the reveal) and Add, no eye
        assertEquals(listOf("Show the amount", "Add an expense"), names(110, 60, "masked"))
        assertEquals(listOf("Tap again to show the amount", "Add an expense"), names(110, 60, "asking"))
        assertEquals(listOf("Hide the amount", "Add an expense"), names(110, 60, "shown no undo"))
        assertEquals(listOf("Add an expense"), names(110, 60, "locked"), "with the figure forced masked there is nothing to reveal")
        // 2 x 2: the eye and Add, side by side at the narrowest
        assertEquals(listOf("Show the amount", "Add an expense"), names(110, 110, "masked"))
        // 4 x 2 with undo: Undo, the chips, the eye and Add
        val big = names(320, 294, "shown with undo")
        assertTrue("Undo" in big && "Hide the amount" in big && "+ Add" in big && "Chai" in big, big.toString())
    }

    // ---- the accent, as pixels ----

    private fun bitmapOf(view: View): Bitmap {
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF7A7A7A.toInt()) // the home screen behind the card
        inst.runOnMainSync { view.draw(c) }
        return bmp
    }

    private fun count(bmp: Bitmap, rgb: Int, tolerance: Int = 2): Int {
        var n = 0
        val px = IntArray(bmp.width * bmp.height); bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        for (p in px) if (abs(((p shr 16) and 0xFF) - ((rgb shr 16) and 0xFF)) <= tolerance && abs(((p shr 8) and 0xFF) - ((rgb shr 8) and 0xFF)) <= tolerance && abs((p and 0xFF) - (rgb and 0xFF)) <= tolerance) n++
        return n
    }

    private fun save(bmp: Bitmap, name: String) {
        val dir = File(context.getExternalFilesDir(null), "widget_shots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun everyAccentReachesThePixelsOfAddAndTheBarsInLightAndDarkAndNoOtherAccentDoes() {
        val sizes = listOf(110 to 60, 110 to 110, 320 to 146, 320 to 294)
        for (night in listOf(false, true)) for (accent in Appearances.ACCENTS) for ((w, h) in sizes) {
            val model = models.getValue(if (h >= 294) "shown no undo" else "masked").copy(accent = accent)
            val bmp = bitmapOf(render(w, h, model, night))
            val want = Appearances.primaryFor(accent, night)
            val mode = if (night) "dark" else "light"
            val ownPixels = count(bmp, want)
            // the round Add is 40 dp across (about 1,250 dp squared), the pill wider; a few hundred pixels at least, on any density
            assertTrue(ownPixels > 200 * density * density / 2, "$mode ${accent.key} at $w x $h: only $ownPixels pixels of the accent ${"%06X".format(want)}")
            for (other in Appearances.ACCENTS.filter { it.key != accent.key }) {
                val o = Appearances.primaryFor(other, night)
                if (Appearances.contrast(o, want) < 1.2) continue // colours too alike to tell apart by pixels
                assertEquals(0, count(bmp, o, tolerance = 0), "$mode ${accent.key} at $w x $h shows ${other.key}'s colour")
            }
            if (w == 320 && h == 146 || w == 110 && h == 60) save(bmp, "${accent.key}_${mode}_${w}x$h")
            if (h == 294) save(bmp, "${accent.key}_${mode}_${w}x$h")
        }
    }

    @Test
    fun theTextOnTheAddButtonIsDrawnInTheColourThatReadsOnEveryAccent() {
        for (night in listOf(false, true)) for (accent: Accent in Appearances.ACCENTS) {
            val bg = Appearances.primaryFor(accent, night)
            val fg = Appearances.onColor(bg)
            assertTrue(Appearances.contrast(fg, bg) >= 4.5, "${if (night) "dark" else "light"} ${accent.key}: ${"%06X".format(fg)} on ${"%06X".format(bg)} is ${Appearances.contrast(fg, bg)}")
        }
    }
}

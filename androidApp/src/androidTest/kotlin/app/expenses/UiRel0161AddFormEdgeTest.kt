package app.expenses

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 0.16.1: the Add form scrolls under the pinned keypad, and the top edge of the next control used to show as a thin strip just above the pad. At 393 and 360 dp, and at 200% text, the
 * form is scrolled a few pixels at a time over its whole length and the strip of the screen just above the keypad is read from a real screenshot: it must be one colour at every position.
 */
@RunWith(AndroidJUnit4::class)
class UiRel0161AddFormEdgeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun sh(cmd: String) { instrumentation.uiAutomation.executeShellCommand(cmd).close(); Thread.sleep(700) }
    private fun resetDisplay() { sh("wm size reset"); sh("wm density reset"); sh("settings put system font_scale 1.0") }
    @Before fun fresh() { runBlocking { container.startFresh(); container.write { it.templates.putSetting("welcome_dismissed", "1"); it.templates.putSetting("first_week_dismissed", "1"); it.templates.putSetting("category_look_notice", "1") } } }
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetDisplay(); runBlocking { container.startFresh() } }

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(20_000) { has(tag) }
    private fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)

    private fun close(a: Int, b: Int) = (0..2).all { k -> kotlin.math.abs(((a shr (8 * k)) and 0xff) - ((b shr (8 * k)) and 0xff)) <= 4 } // a rounding step is not a control

    private fun stripIsClean(label: String): String? {
        compose.waitForIdle(); Thread.sleep(400)
        val padTop = node("number_pad").fetchSemanticsNode().positionOnScreen.y
        val density = context.resources.displayMetrics.density
        val shot = instrumentation.uiAutomation.takeScreenshot()
        val bottom = padTop.toInt() - 1
        val top = bottom - (12 * density).toInt()
        // the app's window is not always the whole screenshot (API 26 draws a narrower window with black either side): the colour is read inside it, at its own edges
        var left = 0; while (left < shot.width && shot.getPixel(left, bottom) == android.graphics.Color.BLACK) left++
        var right = shot.width - 1; while (right > left && shot.getPixel(right, bottom) == android.graphics.Color.BLACK) right--
        left += 2; right -= 2
        val reference = shot.getPixel(left, bottom)
        fun fail(msg: String): String { java.io.File(context.getExternalFilesDir(null), "edge_failure_${label.replace(Regex("[^A-Za-z0-9]+"), "_")}.png").outputStream().use { shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; return msg }
        for (y in top..bottom) {
            var x = left
            while (x < right) { if (!close(shot.getPixel(x, y), reference)) return fail("$label: something shows just above the keypad at ($x, $y): ${Integer.toHexString(shot.getPixel(x, y))} against ${Integer.toHexString(reference)}"); x += 3 }
        }
        return null
    }

    private fun sweep(widthDp: Int, fontScale: String) {
        sh("wm density 420"); sh("wm size ${(widthDp * 2.625f).toInt()}x2340"); sh("settings put system font_scale $fontScale")
        scenario?.close()
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java)); waitFor("add")
        node("add").performClick(); waitFor("number_pad"); waitFor("form_scroll")
        var steps = 0
        var failure: String? = null
        while (steps < 80 && failure == null) {
            failure = stripIsClean("$widthDp dp, font $fontScale, scrolled step $steps")
            val range = node("form_scroll").fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)!!
            if (range.value() >= range.maxValue() - 0.5f) break
            val action = node("form_scroll").fetchSemanticsNode().config[SemanticsActions.ScrollBy].action!!
            compose.runOnUiThread { action.invoke(0f, 9f) } // a few pixels: every position of every control's top edge passes the strip
            steps++
        }
        assertEquals(null, failure, failure)
        assertTrue(steps > 0, "the form scrolled at $widthDp dp, font $fontScale")
    }

    @Test fun at393DpNoControlShowsAsAStripAboveTheKeypad() = sweep(393, "1.0")
    @Test fun at360DpNoControlShowsAsAStripAboveTheKeypad() = sweep(360, "1.0")
    @Test fun atTwiceTheTextSizeNoControlShowsAsAStripAboveTheKeypad() = sweep(393, "2.0")
}

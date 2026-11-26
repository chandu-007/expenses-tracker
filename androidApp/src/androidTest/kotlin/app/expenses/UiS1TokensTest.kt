package app.expenses

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.expenses.ui.Details
import app.expenses.ui.LocalHideAmounts
import app.expenses.ui.MoneyText
import app.expenses.ui.NumberStyle
import app.expenses.ui.Segment
import app.expenses.ui.SegmentedChoice
import app.expenses.ui.Text
import app.expenses.ui.appShapes
import app.expenses.ui.appTypography
import app.expenses.ui.moneyAnnotated
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * UI S1 on the device: tabular figures (every digit as wide as the others, in the hero and chart styles always and in every theme style when the theme asks for it, as it does on the iPhone), the hero figure with the sign and the paise small while the text is the exact amount and a
 * hidden amount stays masked, the segmented control as a radio group that keeps its tags, the "Details" disclosure.
 */
@RunWith(AndroidJUnit4::class)
class UiS1TokensTest {
    @get:Rule val compose = createComposeRule()

    /** Slice 130: the bundled fonts carry tabular digits themselves (frozen into the files), so no style asks for `tnum`: every digit is as wide as the others in Inter and in Space Grotesk at every weight in use. */
    @Test
    fun everyDigitIsAsWideAsTheOthersInTheBundledFontsWithoutAskingForTabularFigures() {
        val fonts = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ExpensesApp).platform.fonts
        assertTrue(fonts != null, "the fonts in assets/fonts load")
        val widths = mutableMapOf<String, Int>()
        compose.setContent {
            MaterialTheme(typography = appTypography(allTabular = false, fonts = fonts)) {
                val tm = rememberTextMeasurer()
                val t = MaterialTheme.typography
                LaunchedEffect(Unit) {
                    val rows = listOf("inter400" to t.bodyLarge, "inter600" to t.titleMedium, "inter700" to t.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), "sg600" to t.headlineMedium, "sg700" to t.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), "sg500" to t.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
                    for ((name, style) in rows) for (digits in listOf("1111", "8888", "0000", "1,11,111.00", "8,88,888.00")) widths["$name:$digits"] = tm.measure(digits, style.copy(fontSize = 32.sp, fontFeatureSettings = null)).size.width
                }
            }
        }
        compose.waitUntil(10_000) { widths.size == 30 }
        fun same(a: Int?, b: Int?, what: String) = assertTrue(a != null && b != null && kotlin.math.abs(a - b) <= 0.01f * a, "$what: $a px against $b px")
        for (style in listOf("inter400", "inter600", "inter700", "sg600", "sg700", "sg500")) {
            same(widths["$style:1111"], widths["$style:8888"], "$style: 1111 and 8888"); same(widths["$style:1111"], widths["$style:0000"], "$style: 1111 and 0000")
            same(widths["$style:1,11,111.00"], widths["$style:8,88,888.00"], "$style: a lakh figure keeps its width whatever the digits")
        }
    }

    @Test
    fun everyDigitIsAsWideAsTheOthersInTheHeroAndInTheThemesStyles() {
        val widths = mutableMapOf<String, Int>()
        compose.setContent {
            MaterialTheme(typography = appTypography(allTabular = true), shapes = appShapes()) {
                val tm = rememberTextMeasurer()
                val t = MaterialTheme.typography
                val hero = NumberStyle.Hero; val chart = NumberStyle.Chart
                LaunchedEffect(Unit) {
                    for ((name, style) in listOf("hero" to hero, "body" to t.bodyLarge, "label" to t.labelMedium, "title" to t.titleLarge, "chart" to chart, "display" to t.displaySmall)) {
                        for (digits in listOf("1111", "8888", "0000", "1,11,111.00", "8,88,888.00")) widths["$name:$digits"] = tm.measure(digits, style.copy(fontSize = 32.sp)).size.width
                    }
                }
            }
        }
        compose.waitUntil(10_000) { widths.size == 30 }
        // equal to within the kerning of the font ("11" is a kerned pair in the Roboto of Android 8: 197 against 201 px of 32 sp text); proportional digits differ by a third (the iPhone's font without `tnum`: 179 against 246)
        fun sameWidth(a: Int?, b: Int?, what: String) = assertTrue(a != null && b != null && kotlin.math.abs(a - b) <= 0.05f * a, "$what: $a px against $b px")
        for (style in listOf("hero", "body", "label", "title", "chart", "display")) {
            sameWidth(widths["$style:1111"], widths["$style:8888"], "$style: 1111 and 8888 are the same width (tabular figures)")
            sameWidth(widths["$style:1111"], widths["$style:0000"], "$style: 1111 and 0000 are the same width")
            sameWidth(widths["$style:1,11,111.00"], widths["$style:8,88,888.00"], "$style: a lakh figure keeps its width whatever the digits")
        }
    }

    @Test
    fun theHeroFigureDrawsASmallSignAndQuietPaiseButReadsTheExactAmount() {
        compose.setContent { MaterialTheme(typography = appTypography(allTabular = true)) { Column { MoneyText("₹1,85,000.50", Modifier.testTag("hero")); MoneyText("₹31,822", Modifier.testTag("whole")) } } }
        compose.onNodeWithTag("hero").assertTextEquals("₹1,85,000.50")
        compose.onNodeWithTag("whole").assertTextEquals("₹31,822")
        val spans = moneyAnnotated("₹1,85,000.50", androidx.compose.ui.graphics.Color.Gray).spanStyles
        assertEquals(2, spans.size, "the sign and the paise are the two small parts")
        assertEquals("₹", moneyAnnotated("₹1,85,000.50", androidx.compose.ui.graphics.Color.Gray).text.substring(spans[0].start, spans[0].end))
        assertEquals(".50", moneyAnnotated("₹1,85,000.50", androidx.compose.ui.graphics.Color.Gray).text.substring(spans[1].start, spans[1].end))
        assertEquals(1, moneyAnnotated("₹31,822", androidx.compose.ui.graphics.Color.Gray).spanStyles.size, "a whole-rupee figure has only the small sign")
        val negative = moneyAnnotated("-₹649.50", androidx.compose.ui.graphics.Color.Gray)
        assertEquals("₹", negative.text.substring(negative.spanStyles[0].start, negative.spanStyles[0].end), "the minus is not made small, only the currency sign")
    }

    @Test
    fun aHiddenAmountIsMaskedAndNeverSplitUp() {
        compose.setContent { CompositionLocalProvider(LocalHideAmounts provides true) { MaterialTheme { MoneyText("₹1,85,000.50", Modifier.testTag("hero")) } } }
        val shown = compose.onNodeWithTag("hero").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.joinToString("") { it.text }
        assertTrue(!shown.any { it.isDigit() }, "no digit of a hidden amount is on screen: '$shown'")
    }

    @Test
    fun theSegmentedControlIsARadioGroupThatKeepsTheTagOfEverySegment() {
        var chosen by mutableStateOf("b")
        compose.setContent {
            MaterialTheme(typography = appTypography(allTabular = true)) {
                SegmentedChoice(listOf(Segment("a", "Light", "seg_a"), Segment("b", "Dark", "seg_b"), Segment("c", "System", "seg_c")), chosen, { chosen = it })
            }
        }
        compose.onNodeWithTag("seg_b", useUnmergedTree = true).assertIsSelected()
        compose.onNodeWithTag("seg_a", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("a", chosen)
        compose.onNodeWithTag("seg_a", useUnmergedTree = true).assertIsSelected()
        val role = compose.onNodeWithTag("seg_c", useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role)
        assertEquals(androidx.compose.ui.semantics.Role.RadioButton, role)
        val height = compose.onNodeWithTag("seg_a", useUnmergedTree = true).fetchSemanticsNode().size.height
        val density = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        assertTrue(height >= (48 * density).toInt() - 1, "a segment is a 48 dp touch target: $height px at density $density")
    }

    @Test
    fun detailsOpensAndClosesAndSaysWhichItIs() {
        compose.setContent { MaterialTheme { Details("details") { Text("the long explanation", Modifier.testTag("body")) } } }
        assertTrue(compose.onAllNodes(hasTestTag("body")).fetchSemanticsNodes().isEmpty(), "closed: the explanation is not there")
        assertEquals("Collapsed", compose.onNodeWithTag("details").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        compose.onNodeWithTag("details").performClick()
        compose.onNodeWithTag("body").assertTextEquals("the long explanation")
        assertEquals("Expanded", compose.onNodeWithTag("details").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        compose.onNodeWithTag("details").performClick()
        assertTrue(compose.onAllNodes(hasTestTag("body")).fetchSemanticsNodes().isEmpty(), "closed again")
    }
}

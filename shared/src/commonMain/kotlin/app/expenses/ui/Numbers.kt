package app.expenses.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.expenses.core.domain.MoneyParts

/**
 * Numbers have a voice (UI S1). Digits drawn with tabular figures (`tnum`) are all as wide as each other, so a figure that changes does not jitter and right-aligned amounts line up. Android's Roboto already does this;
 * the iPhone's system font does not unless it is asked (measured on the simulator: "1111" 179 px against "8888" 246 px without, 242 and 242 with). So on the iPhone every text style is asked ([Platform.proportionalDigits]);
 * on Android only the hero figure and the chart labels are (a global `tnum` made every list frame dearer there: `docs/PERFORMANCE.md`, UI S1).
 */
const val TABULAR_FIGURES = "tnum"

private fun TextStyle.tabular() = copy(fontFeatureSettings = TABULAR_FIGURES)

/**
 * Material's fifteen styles in the design's two voices (calm and clear): Space Grotesk, semi-bold, for display and headline styles (screen titles, figures), Inter for everything else; the sizes
 * stay Material's except the screen title (headline medium, 26 sp). Tabular figures on every style when [allTabular] (the iPhone); on Android only on the figure styles ([NumberStyle]) and the hero,
 * because a global `tnum` made every list frame dearer there. Without [fonts] the system font stays.
 */
fun appTypography(allTabular: Boolean, fonts: app.expenses.AppFonts? = null): Typography {
    val d = Typography()
    fun TextStyle.style(display: Boolean = false, weight: FontWeight? = null): TextStyle {
        var t = this
        if (fonts != null) t = t.copy(fontFamily = if (display) fonts.display else fonts.text, fontWeight = weight ?: fontWeight)
        return if (allTabular) t.tabular() else t
    }
    val sg = FontWeight.SemiBold
    return Typography(
        displayLarge = d.displayLarge.style(true, sg), displayMedium = d.displayMedium.style(true, sg), displaySmall = d.displaySmall.style(true, sg),
        headlineLarge = d.headlineLarge.style(true, sg), headlineMedium = d.headlineMedium.copy(fontSize = 26.sp, lineHeight = 32.sp).style(true, sg), headlineSmall = d.headlineSmall.style(true, sg),
        titleLarge = d.titleLarge.style(false, sg), titleMedium = d.titleMedium.style(false, sg), titleSmall = d.titleSmall.style(false, sg),
        bodyLarge = d.bodyLarge.style(), bodyMedium = d.bodyMedium.style(), bodySmall = d.bodySmall.style(),
        labelLarge = d.labelLarge.style(false, sg), labelMedium = d.labelMedium.style(false, FontWeight.Medium), labelSmall = d.labelSmall.style(false, FontWeight.Medium),
    )
}

/** The display family (Space Grotesk) for the hero figure, where the theme provides it; null on a platform with the system font. */
val LocalDisplayFamily = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.ui.text.font.FontFamily?> { null }

/** The four number roles: a hero figure and a chart label (11 sp) always tabular; a figure in a list row and in a label take the theme's style (tabular on the iPhone, see [appTypography]). */
object NumberStyle {
    /** The hero: 44 sp semi-bold in the display family (the design's "Figure", Space Grotesk 46/600, a little smaller so 200% text still fits), tight, tabular. The sign and the paise are drawn small by [MoneyText]; at large text the screens cap it as they always did. */
    val Hero @Composable get() = TextStyle(fontFamily = LocalDisplayFamily.current, fontSize = 44.sp, lineHeight = 50.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, fontFeatureSettings = TABULAR_FIGURES)
    /** A row's amount: body large, semi-bold, tabular. */
    val Row @Composable get() = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES)
    val Label @Composable get() = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR_FIGURES)
    val Chart = TextStyle(fontSize = 11.sp, fontFeatureSettings = TABULAR_FIGURES)
}

/** The one heading above a day's entries (and its total): label large, in the muted text colour. There were three label styles for this. */
object Heading {
    val Day @Composable get() = MaterialTheme.typography.labelLarge
}

/** [text] as a hero figure: the minus full size, the currency sign at 60% size, the paise at 60% and quieter, the whole part full size. Built AFTER masking, and what a screen reader reads is the string itself (spans change no words). */
fun moneyAnnotated(text: String, paiseColor: Color, smallSign: Boolean = true): AnnotatedString {
    val p = MoneyParts.split(text)
    if (p.symbol.isEmpty() && p.paise.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(p.sign)
        if (p.symbol.isNotEmpty()) { if (smallSign) withStyle(SpanStyle(fontSize = 0.6.em)) { append(p.symbol) } else append(p.symbol) }
        append(p.body)
        if (p.paise.isNotEmpty()) withStyle(SpanStyle(fontSize = 0.6.em, color = paiseColor)) { append(p.paise) }
    }
}

/**
 * An amount drawn as a hero figure. Hidden amounts (Hide amounts) are masked first and come out as plain text. With [minFontSize] the figure stays on one line at any text size: it takes the largest size (1 sp steps, from the
 * style's down to [minFontSize]) at which it fits the width on offer; the sign and the paise are in em, so they follow. (Compose's own auto-size ignores the spans of a text, which is why this does not use it.)
 * With [rolling] the digits that change roll (UI S7, see [OdometerText]): for a figure that changes while it is on screen (the month's spending, a net worth), not for one in a list.
 */
@Composable
fun MoneyText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = NumberStyle.Hero,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    minFontSize: TextUnit = TextUnit.Unspecified,
    rolling: Boolean = false,
    /** See [OdometerText]'s `memoryKey`. */
    memoryKey: String? = null,
) {
    val shown = privately(text)
    val ink = color.takeOrElse { androidx.compose.material3.LocalContentColor.current }
    val built = remember(shown, ink) { moneyAnnotated(shown, paiseColor = ink.copy(alpha = 0.72f)) }
    if (minFontSize == TextUnit.Unspecified) {
        if (rolling) OdometerText(text, modifier = modifier, style = style, color = color, maxLines = maxLines, money = true, memoryKey = memoryKey)
        else androidx.compose.material3.Text(built, modifier = modifier, color = color, style = style, maxLines = maxLines)
        return
    }
    val measurer = rememberTextMeasurer()
    BoxWithConstraints {
        val widthPx = constraints.maxWidth
        val fitted = remember(built, widthPx, style, minFontSize) {
            var size = style.fontSize.value
            while (size > minFontSize.value && measurer.measure(built, style.copy(fontSize = size.sp), maxLines = 1, softWrap = false).size.width > widthPx) size -= 1f
            size.sp
        }
        if (rolling) OdometerText(text, modifier = modifier, style = style.copy(fontSize = fitted), color = color, maxLines = maxLines, softWrap = false, money = true, memoryKey = memoryKey)
        else androidx.compose.material3.Text(built, modifier = modifier, color = color, style = style.copy(fontSize = fitted), maxLines = maxLines, softWrap = false)
    }
}

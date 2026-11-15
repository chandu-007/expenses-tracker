package app.expenses.ui

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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.expenses.core.domain.MoneyParts

/**
 * Numbers have a voice (UI S1). Every digit is drawn with tabular figures (`tnum`): every digit as wide as the others, so a figure that changes does not jitter and right-aligned amounts line up. Android's Roboto
 * already does this; on the iPhone the system font does not unless it is asked (measured on the simulator: "1111" 179 px against "8888" 246 px without, 242 and 242 with). It is asked of every text style here.
 */
const val TABULAR_FIGURES = "tnum"

private fun TextStyle.tabular() = copy(fontFeatureSettings = TABULAR_FIGURES)

/** Material's twelve styles, each with tabular figures: the theme's typography. */
fun appTypography(): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.tabular(), displayMedium = d.displayMedium.tabular(), displaySmall = d.displaySmall.tabular(),
        headlineLarge = d.headlineLarge.tabular(), headlineMedium = d.headlineMedium.tabular(), headlineSmall = d.headlineSmall.tabular(),
        titleLarge = d.titleLarge.tabular(), titleMedium = d.titleMedium.tabular(), titleSmall = d.titleSmall.tabular(),
        bodyLarge = d.bodyLarge.tabular(), bodyMedium = d.bodyMedium.tabular(), bodySmall = d.bodySmall.tabular(),
        labelLarge = d.labelLarge.tabular(), labelMedium = d.labelMedium.tabular(), labelSmall = d.labelSmall.tabular(),
    )
}

/** The four number roles: a hero figure, a figure in a list row, a figure in a label, a figure on a chart (11 sp). */
object NumberStyle {
    /** The hero: 40 sp semi-bold, tight, tabular. The sign and the paise are drawn small by [MoneyText]; at large text the screens cap it as they always did. */
    val Hero = TextStyle(fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp, fontFeatureSettings = TABULAR_FIGURES)
    /** A row's amount: body large, semi-bold. */
    val Row @Composable get() = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
    val Label @Composable get() = MaterialTheme.typography.labelMedium
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

/** An amount drawn as a hero figure. Hidden amounts (Hide amounts) are masked first and come out as plain text. */
@Composable
fun MoneyText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = NumberStyle.Hero,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    autoSize: androidx.compose.foundation.text.TextAutoSize? = null,
) {
    val shown = privately(text)
    val ink = color.takeOrElse { androidx.compose.material3.LocalContentColor.current }
    val built = remember(shown, ink) { moneyAnnotated(shown, paiseColor = ink.copy(alpha = 0.72f)) }
    androidx.compose.material3.Text(built, modifier = modifier, color = color, style = style, maxLines = maxLines, autoSize = autoSize)
}

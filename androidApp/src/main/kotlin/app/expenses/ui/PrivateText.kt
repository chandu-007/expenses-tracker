package app.expenses.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import app.expenses.core.domain.AmountMask

/** True while "hide amounts" is on. */
val LocalHideAmounts = compositionLocalOf { false }

/** [text] as it should be read now: with every amount hidden when privacy is on. */
@Composable
fun privately(text: String): String = if (LocalHideAmounts.current) AmountMask.mask(text) else text

/**
 * The text every screen in this package uses, in place of Material's: the same, except that amounts are hidden while "hide amounts" is on.
 * Because screens call this and not Material's `Text`, a new screen cannot forget to hide its amounts. Typed input fields are not affected.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current,
) {
    androidx.compose.material3.Text(
        text = privately(text), modifier = modifier, color = color, fontSize = fontSize, fontWeight = fontWeight, textAlign = textAlign,
        overflow = overflow, maxLines = maxLines, style = style,
    )
}

/** For text that is not drawn with [Text], such as a screen reader description: the same masking, with the setting passed in. */
fun privatelyPlain(text: String, hide: Boolean): String = if (hide) AmountMask.mask(text) else text

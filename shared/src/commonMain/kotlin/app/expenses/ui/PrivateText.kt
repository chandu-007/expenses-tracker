package app.expenses.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.withStyle
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

/** True while the compact density is on: lists use tighter rows. */
val LocalCompact = compositionLocalOf { false }

/** The vertical padding of a list row: [comfortable] normally, about two thirds of it when compact (never below 4 dp, so the row stays easy to hit). */
@Composable
fun rowPadding(comfortable: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp = if (LocalCompact.current) maxOf(androidx.compose.ui.unit.Dp(4f), comfortable * 2 / 3) else comfortable

/**
 * [text] with every place [query] occurs picked out (bold on a soft background), for search results. While "hide amounts" is on, or with no query, it is
 * the plain masked [Text]: the highlight must never be a way round the mask.
 */
@Composable
fun MatchText(text: String, query: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, maxLines: Int = Int.MAX_VALUE, style: TextStyle = LocalTextStyle.current) {
    val q = query.trim()
    if (LocalHideAmounts.current || q.isEmpty() || !text.contains(q, ignoreCase = true)) { Text(text, modifier, color = color, maxLines = maxLines, style = style); return }
    val mark = androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, background = androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
    val built = androidx.compose.ui.text.buildAnnotatedString {
        var from = 0
        while (true) {
            val at = text.indexOf(q, from, ignoreCase = true)
            if (at < 0) { append(text.substring(from)); break }
            append(text.substring(from, at)); withStyle(mark) { append(text.substring(at, at + q.length)) }; from = at + q.length
        }
    }
    androidx.compose.material3.Text(built, modifier = modifier, color = color, maxLines = maxLines, style = style)
}


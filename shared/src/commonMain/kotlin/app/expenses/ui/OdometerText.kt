package app.expenses.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import app.expenses.core.domain.Odometer
import kotlin.concurrent.Volatile

/**
 * What the odometer did, for the tests: how many times digits rolled, how many times a change was shown as a short cross-fade (Reduce motion), and which digits ([last]: "place:from>to", right to left) rolled last.
 * Nothing in the app reads it.
 */
object OdometerProbe {
    @Volatile var rolls = 0
    @Volatile var fades = 0
    @Volatile var last = ""
}

private class Glyphs(val index: Int, val from: TextLayoutResult?, val to: TextLayoutResult)

/**
 * A figure whose digits roll when it changes (UI S7, U-15). Only the digits that changed move: the old one slides out and the new one slides in from below when the figure grew, from above when it fell, inside the
 * digit's own cell, clipped to it. The rest of the text does not move. It rolls ONLY when the drawn text changes while it is on screen: never when a screen opens (there is no old figure), never while amounts are hidden
 * (a masked figure has no digits), never on a phone simplified for weak hardware ([LocalSimplified]: the figure just changes). Under Reduce motion a change is a short cross-fade of the changed digits, with no travel.
 *
 * The text is drawn by an ordinary `Text`, so what a screen reader and the tests read is the text itself; the changed digits are only made transparent for the length of the roll, and the sliding digits are painted over
 * them in the DRAW phase (the progress is read there, so nothing recomposes while it runs). With [money] the text is one amount, drawn as a hero figure (small currency sign, quiet paise) and compared place by place from
 * the right; otherwise it is a line of words with an amount in it ("₹46,733 left") and rolls only when the line has the same length.
 */
@Composable
fun OdometerText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    money: Boolean = false,
) {
    val shown = privately(text)
    val ink = color.takeOrElse { LocalContentColor.current }
    val base = if (fontWeight != null) style.copy(fontWeight = fontWeight) else style
    val reduce = LocalReduceMotion.current
    val simplified = LocalSimplified.current
    val previous = remember { arrayOfNulls<String>(1) }
    val roll = remember(shown, simplified) {
        if (simplified) null else previous[0]?.let { old -> if (money) Odometer.between(old, shown) else Odometer.sameLength(old, shown) }
    }
    SideEffect { previous[0] = shown }
    val progress = remember(shown) { Animatable(if (roll == null) 1f else 0f) }
    var doneFor by remember { mutableStateOf(shown) }
    LaunchedEffect(shown) {
        if (roll != null) {
            if (reduce) OdometerProbe.fades++ else OdometerProbe.rolls++
            OdometerProbe.last = roll.cells.joinToString(",") { "${it.index}:${it.from}>${it.to}" }
            progress.animateTo(1f, tween(if (reduce) Motion.REDUCED_MS else Motion.STANDARD, easing = Motion.EaseOut))
        }
        doneFor = shown
    }
    val rolling = roll != null && doneFor != shown
    val built = remember(shown, ink, money) { if (money) moneyAnnotated(shown, paiseColor = ink.copy(alpha = 0.72f)) else AnnotatedString(shown) }
    val display = if (rolling && roll != null) buildAnnotatedString {
        append(built)
        roll.cells.forEach { addStyle(SpanStyle(color = Color.Transparent), it.index, it.index + 1) }
    } else built
    val measurer = rememberTextMeasurer(cacheSize = 4)
    val glyphs = remember(roll, base, ink) {
        roll?.cells?.map { c ->
            Glyphs(c.index, if (c.from == ' ') null else measurer.measure(c.from.toString(), base.copy(color = ink), maxLines = 1, softWrap = false), measurer.measure(c.to.toString(), base.copy(color = ink), maxLines = 1, softWrap = false))
        }
    }
    val layoutHolder = remember { arrayOfNulls<TextLayoutResult>(1) }
    androidx.compose.material3.Text(
        display,
        modifier = modifier.drawWithContent {
            drawContent()
            val l = layoutHolder[0]
            if (rolling && roll != null && glyphs != null && l != null) drawRoll(roll.up, glyphs, l, progress.value, reduce, ink)
        },
        color = color, style = base, maxLines = maxLines, softWrap = softWrap,
        onTextLayout = { layoutHolder[0] = it },
    )
}

private fun DrawScope.drawRoll(up: Boolean, glyphs: List<Glyphs>, layout: TextLayoutResult, p: Float, reduce: Boolean, ink: Color) {
    val dir = if (up) 1f else -1f
    val length = layout.layoutInput.text.length
    for (g in glyphs) {
        if (g.index >= length) continue
        val box = layout.getBoundingBox(g.index)
        val travel = if (reduce) 0f else box.height
        clipRect(box.left, box.top, box.right, box.bottom) {
            g.from?.let { drawText(it, color = ink, topLeft = Offset(box.left, box.top - dir * p * travel), alpha = 1f - p) }
            drawText(g.to, color = ink, topLeft = Offset(box.left, box.top + dir * (1f - p) * travel), alpha = p)
        }
    }
}

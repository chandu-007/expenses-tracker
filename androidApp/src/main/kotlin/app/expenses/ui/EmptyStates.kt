package app.expenses.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.CornerRadius

/** Which friendly picture an empty screen shows. */
enum class EmptyKind { HOME, SEARCH, BILLS, INBOX, LOANS, REPORTS, GENERIC }

/**
 * A small line-art picture for an empty screen: a stroke and a soft filled shape, drawn with Canvas only. Decorative (hidden from screen readers and tests);
 * fades and grows in the first time it is shown, unless Reduce motion is on.
 */
@Composable
fun EmptyArt(kind: EmptyKind, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(reduce) {
        if (reduce) progress.snapTo(1f)
        else progress.animateTo(1f, Motion.tween(false, Motion.STANDARD, Motion.EaseOut))
    }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val outline = MaterialTheme.colorScheme.outline
    Canvas(
        modifier
            .size(120.dp)
            .clearAndSetSemantics { }
            .graphicsLayer {
                val p = progress.value
                alpha = p
                val s = 0.85f + 0.15f * p
                scaleX = s
                scaleY = s
            },
    ) {
        val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val soft = primary.copy(alpha = 0.14f)
        when (kind) {
            EmptyKind.HOME -> wallet(stroke, soft, primary, secondary)
            EmptyKind.SEARCH -> search(stroke, soft, primary, outline)
            EmptyKind.BILLS -> receipt(stroke, soft, primary, outline)
            EmptyKind.INBOX -> tray(stroke, soft, primary, outline)
            EmptyKind.LOANS -> exchange(stroke, soft, primary, secondary)
            EmptyKind.REPORTS -> ring(stroke, soft, primary, secondary, outline)
            EmptyKind.GENERIC -> sparkle(stroke, soft, primary, secondary)
        }
    }
}

private fun DrawScope.u(v: Float): Float = v * size.minDimension / 120f
private fun DrawScope.p(x: Float, y: Float) = Offset(u(x), u(y))

private fun DrawScope.wallet(stroke: Stroke, soft: Color, primary: Color, secondary: Color) {
    val body = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(u(16f), u(36f), u(104f), u(98f), CornerRadius(u(12f))))
    }
    drawPath(body, soft)
    drawPath(body, primary, style = stroke)
    drawLine(primary, p(28f, 36f), p(80f, 20f), stroke.width, StrokeCap.Round)
    // clasp
    drawRoundRect(primary, p(78f, 56f), Size(u(26f), u(22f)), CornerRadius(u(8f)), style = stroke)
    drawCircle(primary, u(2.5f), p(91f, 67f))
    // coin
    drawCircle(secondary.copy(alpha = 0.25f), u(13f), p(46f, 22f))
    drawCircle(secondary, u(13f), p(46f, 22f), style = stroke)
    drawLine(secondary, p(46f, 16f), p(46f, 28f), stroke.width, StrokeCap.Round)
}

private fun DrawScope.search(stroke: Stroke, soft: Color, primary: Color, outline: Color) {
    // the list behind
    val sheet = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(u(18f), u(16f), u(78f), u(100f), CornerRadius(u(8f))))
    }
    drawPath(sheet, soft)
    drawPath(sheet, outline, style = stroke)
    for (y in listOf(34f, 50f, 66f, 82f)) drawLine(outline, p(30f, y), p(58f, y), stroke.width, StrokeCap.Round)
    // magnifier
    drawCircle(Color.Transparent, u(20f), p(76f, 66f))
    drawCircle(primary.copy(alpha = 0.16f), u(20f), p(76f, 66f))
    drawCircle(primary, u(20f), p(76f, 66f), style = stroke)
    drawLine(primary, p(91f, 81f), p(106f, 98f), stroke.width * 1.4f, StrokeCap.Round)
}

private fun DrawScope.receipt(stroke: Stroke, soft: Color, primary: Color, outline: Color) {
    val path = Path().apply {
        moveTo(u(28f), u(14f))
        lineTo(u(92f), u(14f))
        lineTo(u(92f), u(104f))
        lineTo(u(80f), u(96f))
        lineTo(u(68f), u(104f))
        lineTo(u(56f), u(96f))
        lineTo(u(44f), u(104f))
        lineTo(u(32f), u(96f))
        lineTo(u(28f), u(98f))
        close()
    }
    drawPath(path, soft)
    drawPath(path, outline, style = stroke)
    drawLine(outline, p(42f, 32f), p(78f, 32f), stroke.width, StrokeCap.Round)
    drawLine(outline, p(42f, 46f), p(66f, 46f), stroke.width, StrokeCap.Round)
    val check = Path().apply {
        moveTo(u(44f), u(70f))
        lineTo(u(55f), u(81f))
        lineTo(u(76f), u(60f))
    }
    drawPath(check, primary, style = Stroke(width = stroke.width * 1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.tray(stroke: Stroke, soft: Color, primary: Color, outline: Color) {
    val tray = Path().apply {
        moveTo(u(14f), u(64f))
        lineTo(u(32f), u(30f))
        lineTo(u(88f), u(30f))
        lineTo(u(106f), u(64f))
        lineTo(u(106f), u(92f))
        lineTo(u(14f), u(92f))
        close()
    }
    drawPath(tray, soft)
    drawPath(tray, primary, style = stroke)
    val lip = Path().apply {
        moveTo(u(14f), u(64f))
        lineTo(u(44f), u(64f))
        lineTo(u(50f), u(76f))
        lineTo(u(70f), u(76f))
        lineTo(u(76f), u(64f))
        lineTo(u(106f), u(64f))
    }
    drawPath(lip, primary, style = stroke)
    drawLine(outline, p(60f, 10f), p(60f, 20f), stroke.width, StrokeCap.Round)
    drawLine(outline, p(44f, 14f), p(48f, 22f), stroke.width, StrokeCap.Round)
    drawLine(outline, p(76f, 14f), p(72f, 22f), stroke.width, StrokeCap.Round)
}

private fun DrawScope.exchange(stroke: Stroke, soft: Color, primary: Color, secondary: Color) {
    drawCircle(soft, u(44f), p(60f, 60f))
    arrow(primary, stroke, from = p(26f, 44f), to = p(94f, 44f))
    arrow(secondary, stroke, from = p(94f, 76f), to = p(26f, 76f))
}

private fun DrawScope.arrow(color: Color, stroke: Stroke, from: Offset, to: Offset) {
    drawLine(color, from, to, stroke.width, StrokeCap.Round)
    val dir = if (to.x > from.x) -1f else 1f
    val head = Path().apply {
        moveTo(to.x + dir * u(12f), to.y - u(10f))
        lineTo(to.x, to.y)
        lineTo(to.x + dir * u(12f), to.y + u(10f))
    }
    drawPath(head, color, style = stroke)
}

private fun DrawScope.ring(stroke: Stroke, soft: Color, primary: Color, secondary: Color, outline: Color) {
    val c = p(60f, 60f)
    val r = u(38f)
    val thick = u(14f)
    drawCircle(soft, r, c, style = Stroke(width = thick))
    drawCircle(outline, r + thick / 2, c, style = Stroke(width = stroke.width * 0.7f))
    drawCircle(outline, r - thick / 2, c, style = Stroke(width = stroke.width * 0.7f))
    val topLeft = Offset(c.x - r, c.y - r)
    drawArc(primary, -90f, 100f, false, topLeft, Size(r * 2, r * 2), style = Stroke(width = thick * 0.5f, cap = StrokeCap.Round))
    drawArc(secondary, 30f, 60f, false, topLeft, Size(r * 2, r * 2), style = Stroke(width = thick * 0.5f, cap = StrokeCap.Round))
}

private fun DrawScope.sparkle(stroke: Stroke, soft: Color, primary: Color, secondary: Color) {
    fun star(cx: Float, cy: Float, r: Float): Path = Path().apply {
        moveTo(u(cx), u(cy - r))
        quadraticTo(u(cx), u(cy), u(cx + r), u(cy))
        quadraticTo(u(cx), u(cy), u(cx), u(cy + r))
        quadraticTo(u(cx), u(cy), u(cx - r), u(cy))
        quadraticTo(u(cx), u(cy), u(cx), u(cy - r))
        close()
    }
    val big = star(54f, 62f, 38f)
    drawPath(big, soft)
    drawPath(big, primary, style = stroke)
    drawPath(star(94f, 26f, 14f), secondary, style = stroke)
    drawPath(star(98f, 92f, 9f), secondary, style = stroke)
}

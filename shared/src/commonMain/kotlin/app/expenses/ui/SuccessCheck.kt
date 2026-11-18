package app.expenses.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * UI S6: the one drawn tick the app answers a finished thing with (saved, paid, "All cleared", "Backup done"): a check mark that draws itself over 280 ms, once, and stays.
 * Under Reduce motion it is there complete. The tick is the picture only: whoever shows it says the result in words beside it, and puts the haptic ([Feel.SAVE]) where the action happens.
 * Nothing here loops: the animation runs once per [key] and stops.
 */
@Composable
fun SuccessCheck(modifier: Modifier = Modifier, size: Dp = 20.dp, color: Color = MaterialTheme.colorScheme.primary, key: Any = Unit) {
    val reduce = LocalReduceMotion.current
    val progress = remember(key) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(key, reduce) { if (reduce) progress.snapTo(1f) else progress.animateTo(1f, tween(280, easing = Motion.EaseOut)) }
    Canvas(modifier.size(size)) {
        val path = Path().apply {
            moveTo(this@Canvas.size.width * 0.15f, this@Canvas.size.height * 0.55f)
            lineTo(this@Canvas.size.width * 0.4f, this@Canvas.size.height * 0.8f)
            lineTo(this@Canvas.size.width * 0.85f, this@Canvas.size.height * 0.25f)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val part = Path()
        measure.getSegment(0f, measure.length * progress.value, part, true)
        drawPath(part, color, style = Stroke(width = (size.value / 8f).coerceAtLeast(2f).dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

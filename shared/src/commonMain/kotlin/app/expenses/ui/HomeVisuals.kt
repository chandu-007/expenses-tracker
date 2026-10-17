package app.expenses.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.expenses.BudgetLineUi
import app.expenses.SparkUi
import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.BudgetState
import app.expenses.core.domain.Money
import app.expenses.core.domain.formatted
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The month total that rolls from its old value to the new one. What is painted counts through the in-between amounts, but the semantics (what tests and
 * screen readers read) carry the final [finalText] at all times. Nothing rolls on first composition, under Reduce motion, or while amounts are hidden.
 */
@Composable
fun RollingAmount(
    finalText: String,
    minor: Long,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = MaterialTheme.typography.displaySmall,
    fontWeight: FontWeight? = null,
) {
    val reduce = LocalReduceMotion.current
    val roll = remember { Animatable(minor.toFloat()) }
    LaunchedEffect(minor) {
        if (reduce) roll.snapTo(minor.toFloat())
        else roll.animateTo(minor.toFloat(), tween(Motion.EMPHASIZED, easing = Motion.EaseOut))
    }
    val shown = roll.value.roundToLong().takeIf { roll.value != minor.toFloat() }
        ?.let { Money(it, BaseCurrency.current).formatted() } ?: finalText
    val announced = privately(finalText)
    Text(
        shown,
        modifier = modifier.clearAndSetSemantics { text = AnnotatedString(announced) },
        color = color, style = style, fontWeight = fontWeight,
    )
}

/**
 * The last 14 days of spending as a smooth line with a soft area under it and a dot on today. It draws in once from the left when first shown; a finger
 * dragged sideways over it shows a guide, a dot and a pill with that day's amount. Takes no height when there is nothing to show.
 */
@Composable
fun HomeSpark(spark: SparkUi, color: Color, modifier: Modifier = Modifier) {
    if (spark.isEmpty) return
    val reduce = LocalReduceMotion.current
    val feel = rememberFeel()
    val days = spark.days
    val n = days.size
    val reveal = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { if (reduce) reveal.snapTo(1f) else reveal.animateTo(1f, tween(Motion.EMPHASIZED, easing = Motion.EaseOut)) }

    var active by remember { mutableStateOf(false) }
    var touched by remember { mutableIntStateOf(-1) }
    var shownIndex by remember { mutableIntStateOf(-1) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            if (reduce) fade.snapTo(1f) else fade.animateTo(1f, tween(Motion.QUICK))
        } else {
            if (reduce) fade.snapTo(0f) else fade.animateTo(0f, tween(Motion.QUICK))
            shownIndex = -1
        }
    }

    val description = privately("Spending over the last ${n} days")
    Box(
        modifier.fillMaxWidth().height(56.dp).testTag("home_spark").semantics { contentDescription = description }
            .pointerInput(n) {
                fun indexAt(x: Float): Int = if (n <= 1 || size.width <= 0) 0 else ((x / size.width) * (n - 1)).roundToInt().coerceIn(0, n - 1)
                fun move(x: Float) {
                    val i = indexAt(x)
                    if (i != touched) { touched = i; shownIndex = i; feel(Feel.TICK) }
                }
                detectHorizontalDragGestures(
                    onDragStart = { at -> active = true; touched = -1; move(at.x) },
                    onDragEnd = { active = false },
                    onDragCancel = { active = false },
                    onHorizontalDrag = { change, _ -> move(change.position.x); change.consume() },
                )
            },
    ) {
        val maxMinor = (days.maxOfOrNull { it.minor } ?: 1L).coerceAtLeast(1L).toFloat()
        Canvas(Modifier.fillMaxSize()) {
            val inset = 4.dp.toPx()
            val top = 18.dp.toPx()
            val bottom = size.height - 4.dp.toPx()
            val w = size.width - inset * 2
            val h = bottom - top
            fun px(i: Int) = inset + (if (n <= 1) 0.5f else i / (n - 1).toFloat()) * w
            fun py(i: Int) = bottom - (days[i].minor.coerceAtLeast(0L) / maxMinor) * h
            val line = Path().apply {
                moveTo(px(0), py(0))
                for (i in 1 until n) {
                    val midX = (px(i - 1) + px(i)) / 2f
                    cubicTo(midX, py(i - 1), midX, py(i), px(i), py(i))
                }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(px(n - 1), bottom)
                lineTo(px(0), bottom)
                close()
            }
            clipRect(right = size.width * reveal.value) {
                drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0f)), startY = top, endY = bottom))
                drawPath(line, color.copy(alpha = 0.95f), style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (reveal.value >= 1f) drawCircle(color, radius = 4.dp.toPx(), center = Offset(px(n - 1), py(n - 1)))
            val i = shownIndex
            if (i in 0 until n && fade.value > 0f) {
                val a = fade.value
                drawLine(color.copy(alpha = 0.55f * a), Offset(px(i), top - 6.dp.toPx()), Offset(px(i), bottom), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(color.copy(alpha = a), radius = 5.dp.toPx(), center = Offset(px(i), py(i)))
            }
        }
        val i = shownIndex
        if (i in 0 until n) {
            Text(
                days[i].label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                        layout(p.width, p.height) {
                            val inset = 4.dp.toPx()
                            val cx = inset + (if (n <= 1) 0.5f else i / (n - 1).toFloat()) * (constraints.maxWidth - inset * 2)
                            p.place((cx - p.width / 2f).coerceIn(0f, (constraints.maxWidth - p.width).coerceAtLeast(0).toFloat()).roundToInt(), 0)
                        }
                    }
                    .graphicsLayer { alpha = fade.value }
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** Colour of a budget state: calm when fine, amber when near, red when over. Matches the budget screen. */
@Composable
private fun budgetStateColor(state: BudgetState): Color = when (state) {
    BudgetState.OK -> MaterialTheme.colorScheme.primary
    BudgetState.NEAR -> Color(0xFFE08A00)
    BudgetState.OVER -> MaterialTheme.colorScheme.error
}

/** [BudgetBar] that fills to its new value and shifts colour smoothly; instant under Reduce motion, and not animated on first composition. */
@Composable
fun AnimatedBudgetBar(line: BudgetLineUi, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val progress by animateFloatAsState((line.percent / 100f).coerceIn(0f, 1f), Motion.tween(reduce, Motion.STANDARD), label = "budget_fill")
    val color by animateColorAsState(budgetStateColor(line.state), Motion.tween(reduce, Motion.STANDARD), label = "budget_color")
    LinearProgressIndicator(progress = { progress }, color = color, modifier = modifier.fillMaxWidth())
}

/** Performs the limit haptic once when [over] turns true after the first composition. */
@Composable
fun LimitCrossedFeel(over: Boolean) {
    val feel = rememberFeel()
    var was by remember { mutableStateOf(over) }
    LaunchedEffect(over) {
        if (over && !was) feel(Feel.LIMIT)
        was = over
    }
}

package app.expenses.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.customActions
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
import app.expenses.BudgetUi
import app.expenses.SafeToSpendUi
import androidx.compose.foundation.layout.Row
import app.expenses.core.domain.PaceMath
import app.expenses.core.domain.PaceLevel
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import app.expenses.core.domain.Money
import app.expenses.core.domain.formatted
import app.expenses.core.domain.wholeAmounts
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The month total, whose digits roll when it changes (UI S7: only the digits that changed, see [OdometerText]; it used to count through every amount in between). What is painted is the drawn figure; the semantics
 * (what tests and screen readers read) carry the exact [finalText] at all times. Nothing rolls on first composition, under Reduce motion (a short cross-fade), while amounts are hidden, or on a phone simplified for weak hardware.
 */
@Composable
fun RollingAmount(
    finalText: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = NumberStyle.Hero,
    fontWeight: FontWeight? = null,
    /** Draw whole units ("₹31,822") while the semantics keep the exact [finalText] ("₹31,822.40"): what a screen reader says and what tests read is never rounded. */
    whole: Boolean = false,
) {
    val announced = privately(finalText)
    // the sign at 60% and the paise quiet (UI S1); the semantics keep the exact text
    MoneyText(
        if (whole) finalText.wholeAmounts() else finalText,
        modifier = modifier.clearAndSetSemantics { text = AnnotatedString(announced) },
        color = color, style = if (fontWeight != null) style.copy(fontWeight = fontWeight) else style,
        rolling = true, memoryKey = "home_month",
    )
}

/**
 * The last 14 days of spending as a smooth line with a soft area under it and a dot on today. It draws in once from the left when first shown; a finger
 * dragged sideways over it shows a guide, a dot and a pill with that day's amount. Takes no height when there is nothing to show.
 */
@Composable
fun HomeSpark(spark: SparkUi, color: Color, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 56.dp) {
    if (spark.isEmpty) return
    val reduce = LocalReduceMotion.current
    val feel = rememberFeel()
    val days = spark.days
    val n = days.size
    val ghost = spark.ghost.takeIf { it.size == n }.orEmpty()
    // saved with the list item: scrolled off and back, the line is already drawn instead of drawing in again
    var played by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    // simplified for a weak phone (UI S7): the line is simply there, it does not draw in
    val still = reduce || LocalSimplified.current
    val reveal = remember { Animatable(if (still || played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (still) reveal.snapTo(1f) else if (!played) reveal.animateTo(1f, tween(Motion.EMPHASIZED, easing = Motion.EaseOut))
        played = true
    }

    var active by remember { mutableStateOf(false) }
    var touched by remember { mutableIntStateOf(-1) }
    var shownIndex by remember { mutableIntStateOf(-1) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            if (reduce) fade.snapTo(1f) else fade.animateTo(1f, tween(Motion.CHIP))
        } else {
            if (reduce) fade.snapTo(0f) else fade.animateTo(0f, tween(Motion.CHIP))
            shownIndex = -1
        }
    }

    var showTable by remember { mutableStateOf(false) }
    // a screen reader hears the highest day and today, and can open the same numbers as a table (a long press does it for everyone else)
    val highest = days.maxByOrNull { it.minor }
    val description = privately("Spending over the last $n days." + (highest?.takeIf { it.minor > 0 }?.let { " Highest: ${it.label}." } ?: "") + " Today: ${days.last().label}.")
    Box(
        modifier.fillMaxWidth().height(height).testTag("home_spark").semantics {
            contentDescription = description
            customActions = listOf(androidx.compose.ui.semantics.CustomAccessibilityAction("Show as table") { showTable = true; true })
        }
            .chartScrub(n, onScrub = { i -> active = true; touched = i; shownIndex = i; feel(Feel.STEP) }, onEnd = { active = false }, indexAt = ::pointIndex)
            .pointerInput(n) { detectTapGestures(onLongPress = { showTable = true }) },
    ) {
        val maxMinor = maxOf(days.maxOfOrNull { it.minor } ?: 1L, ghost.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
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
            // last period, at the same days: a thin dashed line under this one (UI S5); it draws in with the line and is only ever read in the draw phase
            val ghostPath = if (ghost.isEmpty()) null else Path().apply {
                fun gy(i: Int) = bottom - (ghost[i].coerceAtLeast(0L) / maxMinor) * h
                moveTo(px(0), gy(0))
                for (i in 1 until n) { val midX = (px(i - 1) + px(i)) / 2f; cubicTo(midX, gy(i - 1), midX, gy(i), px(i), gy(i)) }
            }
            clipRect(right = size.width * reveal.value) {
                if (ghostPath != null) drawPath(ghostPath, color.copy(alpha = 0.55f), style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
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
                days[i].label + (if (ghost.isNotEmpty()) " · ${spark.ghostName} ${Money(ghost[i], BaseCurrency.current).formatted()}" else ""),
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
    if (showTable) androidx.compose.material3.AlertDialog(
        onDismissRequest = { showTable = false },
        title = { Text("Spending over the last $n days") },
        text = { ChartTable(if (ghost.isEmpty()) listOf("Day", "Spent") else listOf("Day", "Spent", spark.ghostName.replaceFirstChar { it.uppercase() }), days.mapIndexed { k, d -> TableLine(listOf(d.label.substringBefore(" · "), d.label.substringAfter(" · ", "")) + (if (ghost.isEmpty()) emptyList() else listOf(Money(ghost[k], BaseCurrency.current).formatted())), d.label.replace(" · ", ": ") + (if (ghost.isEmpty()) "" else ", ${spark.ghostName} ${Money(ghost[k], BaseCurrency.current).formatted()}")) }, "spark_table", Modifier.heightIn(max = 420.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { showTable = false }, modifier = Modifier.testTag("spark_table_close")) { Text("Close") } },
    )
}

/** Colour of a line's pace (UI S5): calm when on pace, amber when ahead of it (a projected overshoot), red only when over the limit. The word beside it says the same, so colour is never the only signal. */
@Composable
fun paceColor(pace: PaceLevel): Color = when (pace) {
    PaceLevel.EARLY, PaceLevel.ON_PACE -> MaterialTheme.colorScheme.primary
    PaceLevel.A_LITTLE_AHEAD, PaceLevel.AHEAD -> Signal.watch
    PaceLevel.OVER -> MaterialTheme.colorScheme.error
}

/**
 * The bar of a limit (UI S5): it fills to its new value and shifts colour smoothly (instant under Reduce motion, not animated on first composition) and, when [elapsed] is given, a thin tick marks how much
 * of the period has gone ("today"); [todayLabel] writes the word over the tick. Colour follows the pace ([paceColor]). The fill and the tick are read while drawing.
 */
@Composable
fun AnimatedBudgetBar(line: BudgetLineUi, modifier: Modifier = Modifier, elapsed: Float = -1f, todayLabel: Boolean = false) {
    val reduce = LocalReduceMotion.current
    // Hide amounts: the bar loses its length (the fill would show the figure), the track and the tick for today stay: the tick is the date, not money
    val hide = LocalHideAmounts.current
    val progress by animateFloatAsState(if (hide) 0f else (line.percent / 100f).coerceIn(0f, 1f), Motion.tweenOrSnap(reduce, Motion.STANDARD), label = "budget_fill")
    val color by animateColorAsState(paceColor(line.pace), Motion.tweenOrSnap(reduce, Motion.STANDARD), label = "budget_color")
    val tick = MaterialTheme.colorScheme.onSurface
    Box(Modifier.fillMaxWidth().padding(top = if (todayLabel && elapsed >= 0f) 14.dp else 0.dp)) {
        LinearProgressIndicator(progress = { progress }, color = color, trackColor = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier.fillMaxWidth())
        if (elapsed >= 0f) androidx.compose.foundation.layout.Spacer(
            Modifier.matchParentSize().drawBehind {
                val x = size.width * elapsed.coerceIn(0f, 1f)
                drawLine(tick.copy(alpha = 0.8f), Offset(x, -3.dp.toPx()), Offset(x, size.height + 3.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            },
        )
        if (todayLabel && elapsed >= 0f) Text(
            "today", style = NumberStyle.Chart, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            modifier = Modifier.layout { m, c ->
                val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
                layout(0, 0) { p.place((c.maxWidth * elapsed.coerceIn(0f, 1f) - p.width / 2f).coerceIn(0f, (c.maxWidth - p.width).coerceAtLeast(0).toFloat()).roundToInt(), -p.height - 5.dp.roundToPx()) }
            },
        )
    }
}

/**
 * The line at the foot of the month card (UI S5, owner decision D7): ONE "a day" figure and how long it has to last: "₹1,565 a day · 16 days to payday", from the budget; without a budget the balances' figure
 * ("about ₹16,213 a day from your balances"); over the limit, the amount over. On the left "Day 14 of 30". The figure is drawn in whole units and the node says the exact one. Hide amounts masks it ([Text]).
 */
@Composable
fun HeroPaceLine(budget: BudgetUi, safe: SafeToSpendUi?, onHero: Color, stacked: Boolean, modifier: Modifier = Modifier) {
    val overall = budget.overall
    val hasLimit = overall.limit != null
    val over = hasLimit && overall.pace == PaceLevel.OVER
    val perDay = budget.perDay
    val right: String?
    val exact: String?
    when {
        over -> { right = PaceMath.word(PaceLevel.OVER, overall.note.removeSuffix(" over").wholeAmounts()); exact = PaceMath.word(PaceLevel.OVER, overall.note.removeSuffix(" over")) }
        hasLimit && perDay != null && !budget.billsUseItAll -> { right = PaceMath.heroLine(perDay.wholeAmounts(), budget.daysLeft, budget.endWord); exact = PaceMath.heroLine(perDay, budget.daysLeft, budget.endWord) }
        hasLimit && budget.billsUseItAll -> { right = "bills still to come use what is left"; exact = right }
        safe?.perDay != null -> { right = "${safe.perDay.wholeAmounts()} from your balances"; exact = "${safe.perDay} from your balances" }
        else -> { right = null; exact = null }
    }
    val left = if (hasLimit && budget.periodDays > 0) "Day ${budget.daysIn} of ${budget.periodDays}" else null
    if (left == null && right == null) return
    val ink = onHero
    val leftText: @Composable () -> Unit = { if (left != null) Text(left, style = MaterialTheme.typography.labelMedium, color = ink, maxLines = 1, modifier = Modifier.testTag("hero_day_of")) }
    val rightText: @Composable (Modifier) -> Unit = { mod ->
        if (right != null) {
            val spoken = privately(exact!!)
            Text(right, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = ink, modifier = mod.testTag("hero_pace_line").clearAndSetSemantics { text = AnnotatedString(spoken) })
        }
    }
    if (stacked) Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) { leftText(); rightText(Modifier) }
    else Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        leftText(); Box(Modifier.weight(1f)); rightText(Modifier)
    }
}

/** Performs the limit haptic once when [over] turns true after the first composition. */
@Composable
fun LimitCrossedFeel(over: Boolean) {
    val feel = rememberFeel()
    var was by remember { mutableStateOf(over) }
    LaunchedEffect(over) {
        if (over && !was) feel(Feel.WARN)
        was = over
    }
}

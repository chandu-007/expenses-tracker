package app.expenses.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.expenses.BudgetLineUi
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
fun AnimatedBudgetBar(line: BudgetLineUi, modifier: Modifier = Modifier, elapsed: Float = -1f, todayLabel: Boolean = false, onTable: (() -> Unit)? = null, onTap: (() -> Unit)? = null, tapLabel: String? = null) {
    val reduce = LocalReduceMotion.current
    // Hide amounts: the bar loses its length (the fill would show the figure), the track and the tick for today stay: the tick is the date, not money
    val hide = LocalHideAmounts.current
    val progress by animateFloatAsState(if (hide) 0f else (line.percent / 100f).coerceIn(0f, 1f), Motion.tweenOrSnap(reduce, Motion.STANDARD), label = "budget_fill")
    val color by animateColorAsState(paceColor(line.pace), Motion.tweenOrSnap(reduce, Motion.STANDARD), label = "budget_color")
    val tick = MaterialTheme.colorScheme.onSurface
    // what the bar says to a screen reader: how much of the limit is used and how much of the period has gone (not the amounts); with Hide amounts it says only that they are hidden
    // spent, the limit and the share of the period gone: "Spent ₹100.00 of ₹10,000.00, 1% of the limit used, 90% of the period gone"; with Hide amounts only that they are hidden
    val said = if (hide) "Amounts hidden" else "Spent ${line.spent}" + (line.limit?.let { " of $it" } ?: "") + ", ${line.percent}% of the limit used" + (if (elapsed >= 0f) ", ${(elapsed.coerceIn(0f, 1f) * 100).roundToInt()}% of the period gone" else "")
    Box(Modifier.fillMaxWidth().padding(top = if (todayLabel && elapsed >= 0f) 14.dp else 0.dp).testTag("pace_bar").semantics {
        stateDescription = said
        // a table of the same numbers, for a screen reader (an action) and for a long press, as the charts have ("Show as table")
        if (onTable != null) customActions = listOf(androidx.compose.ui.semantics.CustomAccessibilityAction("Show as table") { onTable(); true })
    }.then(if (onTable != null) Modifier.combinedClickable(onClickLabel = tapLabel, onClick = { onTap?.invoke() }, onLongClickLabel = "Show as table", onLongClick = onTable) else Modifier)) {
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


/**
 * The pace bar's numbers as a table ("Show as table", from a long press or a screen reader's action): what is spent, the limit, what is left (or over), how much of the period has gone and the status, one stop each.
 * Hide amounts masks the figures ([Text] and [privatelyPlain]).
 */
@Composable
fun PaceTableDialog(line: BudgetLineUi, budget: app.expenses.BudgetUi, onClose: () -> Unit) {
    val hide = LocalHideAmounts.current
    val over = line.state == app.expenses.core.domain.BudgetState.OVER
    val gone = if (budget.periodDays > 0) "${(budget.elapsed.coerceIn(0f, 1f) * 100).roundToInt()}% (day ${budget.daysIn} of ${budget.periodDays})" else ""
    val rows = buildList {
        add(listOf("Spent", line.spent))
        line.limit?.let { add(listOf("Limit", it)) }
        add(listOf(if (over) "Over by" else "Left", line.note.removeSuffix(" left").removeSuffix(" over")))
        if (gone.isNotEmpty()) add(listOf("Period gone", gone))
        add(listOf("Status", statusWord(line, budget.elapsed)))
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Spending against the limit") },
        text = { ChartTable(listOf("", ""), rows.map { TableLine(it, "${it[0]} ${it[1]}") }, "pace_table") },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onClose, modifier = Modifier.testTag("pace_table_close")) { Text("Close") } },
    )
}

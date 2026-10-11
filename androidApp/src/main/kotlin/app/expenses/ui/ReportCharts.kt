package app.expenses.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.expenses.ReportMode
import app.expenses.ReportUi

/** Colours for slices and bars whose category has none of its own. Mid-tone, so they read on both the light and the dark theme. */
private val PALETTE = listOf(0xFF5C6BC0, 0xFF26A69A, 0xFFFFA726, 0xFFEC407A, 0xFF66BB6A, 0xFF29B6F6, 0xFF8D6E63, 0xFFAB47BC).map { Color(it) }
private val OTHERS = Color(0xFF9E9E9E)

/** The colour of the [index]th thing in a report: its own when it has one, otherwise one from the palette. */
fun reportColor(own: Long?, index: Int): Color = own?.let { Color(it) } ?: PALETTE[index % PALETTE.size]

/** One slice of the breakdown ring. [share] is a whole percent; [onClick] is null for the "others" slice. */
data class Slice(val key: String, val name: String, val share: Int, val color: Color, val onClick: (() -> Unit)?)

/**
 * The top of the page: what was spent (large), how it compares with the period before as a chip, and income and what is left as two tiles. The three
 * tappable areas open the entries behind each figure, as the plain text did.
 */
@Composable
fun ReportSummary(report: ReportUi, onSpent: () -> Unit, onIncome: () -> Unit) {
    val on = MaterialTheme.colorScheme.onPrimary
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().heroBackground().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onSpent).testTag("report_spent_card")) {
            Text("Spent", style = MaterialTheme.typography.labelLarge, color = on.copy(alpha = 0.8f))
            Text(report.spent, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = on, modifier = Modifier.testTag("report_spent"))
            report.comparison?.let { c ->
                // the words say "more" or "less"; the chip's colour only repeats that, so it is never the sole signal
                val more = c.contains("more")
                Text(
                    c, style = MaterialTheme.typography.labelMedium, color = on,
                    modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background((if (more) Color(0xFFE57373) else Color(0xFF66BB6A)).copy(alpha = 0.40f)).padding(horizontal = 10.dp, vertical = 4.dp).testTag("report_comparison"),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(on.copy(alpha = 0.14f)).clickable(onClick = onIncome).padding(12.dp).testTag("report_income_card")) {
                Text("Income", style = MaterialTheme.typography.labelMedium, color = on.copy(alpha = 0.8f))
                Text(report.income, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = on, modifier = Modifier.testTag("report_income"))
            }
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(on.copy(alpha = 0.14f)).padding(12.dp)) {
                Text("Income minus spending", style = MaterialTheme.typography.labelMedium, color = on.copy(alpha = 0.8f))
                Text(report.net, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = if (report.netPositive) on else Color(0xFFFFB4AB), modifier = Modifier.testTag("report_net"))
            }
        }
    }
}

/**
 * Where it went, as a ring: the biggest five slices and the rest together, with the total in the middle, and a legend that opens each one. The ring
 * sweeps in when the page opens. Nothing is drawn when there is nothing to show; the list below says so.
 */
@Composable
fun BreakdownRing(slices: List<Slice>, totalText: String, label: String) {
    val shown = slices.filter { it.share > 0 }
    if (shown.isEmpty()) return
    val top = shown.take(5)
    // shares are whole percents rounded down, so the rest is what the five biggest leave of 100 (never less than the rest's own sum): the legend adds up
    val rest = if (shown.size > 5) maxOf(shown.drop(5).sumOf { it.share }, 100 - top.sumOf { it.share }) else 0
    val all = top + if (rest > 0) listOf(Slice("others", "Others", rest, OTHERS, null)) else emptyList()
    val sweep = remember(all) { Animatable(0f) }
    LaunchedEffect(all) { sweep.animateTo(1f, tween(700)) }
    val track = MaterialTheme.colorScheme.surfaceVariant
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).testTag("breakdown"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(148.dp).semantics { contentDescription = "Breakdown: " + all.joinToString(", ") { "${it.name} ${it.share}%" } }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(148.dp)) {
                val stroke = 22.dp.toPx()
                val inset = stroke / 2
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                val total = all.sumOf { it.share }.coerceAtLeast(1)
                var start = -90f
                all.forEach { s ->
                    val full = 360f * s.share / total
                    val gap = if (all.size > 1) 2f else 0f
                    drawArc(s.color, start, ((full - gap) * sweep.value).coerceAtLeast(0f), false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Butt))
                    start += full
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(totalText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(horizontal = 18.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            all.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).let { m -> s.onClick?.let { m.clickable(onClick = it) } ?: m }.padding(vertical = 6.dp, horizontal = 4.dp).testTag("breakdown_${s.key}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).background(s.color, CircleShape))
                    Text(s.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    Text("${s.share}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

/** The bars over time: rounded, growing in, the selected one in the accent colour, and a line under the chart naming it. Tapping a bar opens that period. */
@Composable
fun TrendChart(report: ReportUi, onBar: (Long, Long) -> Unit) {
    val hide = LocalHideAmounts.current
    Column(Modifier.fillMaxWidth().padding(16.dp).testTag("trend")) {
        Text(if (report.mode == ReportMode.PERIOD) "Last six periods" else "Spending by month", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            report.trend.forEachIndexed { i, bar ->
                val h by animateFloatAsState(bar.fraction, tween(500), label = "bar")
                Column(
                    Modifier.weight(1f).clickable { onBar(bar.startDay, bar.endDay) }.testTag("trend_bar_$i").semantics { contentDescription = privatelyPlain("${bar.label}: ${bar.amount}", hide) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.height(96.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.fillMaxWidth().height((96 * h).dp.coerceAtLeast(if (bar.fraction > 0f) 4.dp else 2.dp)).background(if (bar.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.30f), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)))
                    }
                    Text(bar.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, fontWeight = if (bar.isSelected) FontWeight.Bold else null, color = if (bar.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        report.trend.firstOrNull { it.isSelected }?.let { Text("${it.label}: ${it.amount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
    }
}

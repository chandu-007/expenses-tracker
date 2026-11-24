package app.expenses.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag as semanticsTestTag
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.expenses.ReportMode
import app.expenses.ReportUi
import app.expenses.TrendBarUi
import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.CategoryLook
import app.expenses.core.domain.Money
import app.expenses.core.domain.compact
import app.expenses.core.domain.formatted
import app.expenses.core.domain.wholeAmounts
import app.expenses.core.domain.ReportSentence
import app.expenses.core.storage.ReportGroup
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** What is left over when more than five things share a ring: grey, so it never competes with a colour of its own. */
private val OTHERS = Color(0xFF9E9E9E)

/**
 * The colours of one report's chart (UI S4): from the 12 category slot hues (the first seven colour-blind-safe), a category's colour is its own choice or its stable slot and, when two things in the
 * same report would share one, the next free slot ([CategoryLook.chartHues]); the rows below the ring, the ring and the bars read the same map, so a colour means one thing on the page.
 */
class ChartColors(private val byKey: Map<String, Color>) {
    operator fun get(key: String?): Color = byKey[key ?: "none"] ?: OTHERS
}

@Composable
fun rememberChartColors(report: ReportUi): ChartColors = remember(report.rows, report.groupRows, report.group) {
    if (report.group == ReportGroup.CATEGORY) {
        val hues = CategoryLook.chartHues(report.rows.map { it.categoryId }, report.rows.map { r -> r.colorArgb?.let { (it and 0xFFFFFF).toInt() } })
        ChartColors(report.rows.mapIndexed { i, r -> (r.categoryId ?: "none") to Color(0xFF000000.toInt() or hues[i]) }.toMap())
    } else {
        val hues = CategoryLook.chartHues(report.groupRows.map { null }, report.groupRows.map { null })
        ChartColors(report.groupRows.mapIndexed { i, r -> (r.key ?: "none") to Color(0xFF000000.toInt() or hues[i]) }.toMap())
    }
}

/** One slice of the breakdown ring. [share] is a whole percent; [onClick] is null for the "others" slice. */
data class Slice(val key: String, val name: String, val share: Int, val color: Color, val amount: String, val minor: Long, val onClick: (() -> Unit)?)

private fun money(minor: Long): String = Money(minor, BaseCurrency.current).formatted()

/** A share of the total for display: a real but tiny share reads "<1%", not "0%". */
internal fun shareText(share: Int): String = if (share < 1) "<1%" else "$share%"

/**
 * An amount line drawn in whole units ("₹24,558", not "₹24,558.00") with its exact text kept for a screen reader and for the tests (the same pattern as `SpokenForLine`); Hide amounts masks it either way
 * (the shared [Text] masks the drawn text, [privately] the exact one). Text without a decimal amount is drawn as it is.
 */
@Composable
fun WholeText(
    text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, fontWeight: FontWeight? = null, textAlign: TextAlign? = null, maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip, style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current,
    autoSize: androidx.compose.foundation.text.TextAutoSize? = null,
) {
    val exact = privately(text)
    Text(
        text.wholeAmounts(), modifier.clearAndSetSemantics { this.text = androidx.compose.ui.text.AnnotatedString(exact) }, color = color, fontWeight = fontWeight, textAlign = textAlign,
        maxLines = maxLines, overflow = overflow, style = style, autoSize = autoSize,
    )
}

/**
 * The top of the page: ONE card that answers three questions in a glance (UI redesign R6): how much was spent (large, "so far" while the period is still going on) with the comparison against the same point of
 * the period before; how much came in and how much of it was kept; and, for the budget period being lived, where the spending stands against the budget (a thin bar, a tick for how much of the period has gone,
 * and the pace in words; it opens the Budget). Amounts are drawn in whole units and the nodes say the exact ones. The tappable areas open the entries behind each figure; the tint of the pill only repeats its words.
 */
@Composable
fun ReportSummary(report: ReportUi, onSpent: () -> Unit, onIncome: () -> Unit, onBudget: () -> Unit = {}) {
    val ink = MaterialTheme.colorScheme.onSurface
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(Radius.M).background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onSpent).testTag("report_spent_card")) {
            Text(if (report.open) "Spent so far" else "Spent", style = MaterialTheme.typography.labelLarge, color = quiet)
            MoneyText(report.spent, style = NumberStyle.Hero.copy(fontSize = 32.sp, lineHeight = 38.sp), color = ink, maxLines = 1, minFontSize = 12.sp, rolling = true, modifier = Modifier.testTag("report_spent"))
            report.comparison?.let { c ->
                // the words say "more" or "less"; the tint only repeats that, so it is never the sole signal
                val tint = when (report.comparisonMore) { true -> MaterialTheme.colorScheme.error; false -> Color(0xFF2E7D32); null -> quiet }
                WholeText(
                    c, style = MaterialTheme.typography.labelMedium, color = ink,
                    modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(50)).background(tint.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp).testTag("report_comparison"),
                )
            }
        }
        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // "Income ₹82,000   Saved ₹32,695 (40%)": two groups that wrap by whole words (a group's own words too), so at 200% text each is its own line and no amount breaks in the middle
        androidx.compose.foundation.layout.FlowRow(
            Modifier.fillMaxWidth().clickable(onClick = onIncome).testTag("report_income_card"),
            horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Income", style = MaterialTheme.typography.bodyMedium, color = quiet)
                WholeText(report.income, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1, autoSize = amountFit(14), modifier = Modifier.testTag("report_income"))
            }
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (report.netPositive) "Saved" else "Over by", style = MaterialTheme.typography.bodyMedium, color = quiet)
                WholeText(if (report.netPositive) report.net else report.shortBy, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (report.netPositive) ink else MaterialTheme.colorScheme.error, maxLines = 1, autoSize = amountFit(14), modifier = Modifier.testTag("report_net"))
                report.savedPercent?.let { Text("(${shareText(it)})", style = MaterialTheme.typography.bodyMedium, color = quiet) }
            }
        }
        report.budget?.let { b ->
            // only for the budget period being lived: "On pace · ₹25,695 left of ₹75,000 ›", the bar under it with a tick for today
            val line = (if (b.pace == app.expenses.core.domain.PaceLevel.OVER) paceWord(b) + " of " + b.limit else paceWord(b) + " · " + b.note + " of " + b.limit)
            Column(Modifier.fillMaxWidth().clickable(onClick = onBudget).testTag("report_budget_line"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WholeText(line, style = MaterialTheme.typography.labelLarge, color = ink, modifier = Modifier.weight(1f))
                    Text("›", style = MaterialTheme.typography.titleMedium, color = quiet, modifier = Modifier.padding(start = 8.dp).clearAndSetSemantics {})
                }
                if (!LocalHideAmounts.current) AnimatedBudgetBar(b, Modifier.height(6.dp).clip(RoundedCornerShape(50)), elapsed = report.budgetElapsed)
            }
        }
    }
}

// ---- "Show as table": the accessible twin of every chart ----

/** One line of a chart's table: the [cells] drawn, and what a screen reader says for the whole line ([spoken]). */
class TableLine(val cells: List<String>, val spoken: String)

/** The button under a chart that shows or hides its table. It says its state, so a screen reader knows whether the table is there. */
@Composable
fun TableToggle(shown: Boolean, onToggle: () -> Unit, tag: String, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onToggle,
        modifier = modifier.heightIn(min = 48.dp).testTag(tag).semantics { role = Role.Switch; stateDescription = if (shown) "Table shown" else "Table hidden" },
    ) {
        Icon(Icons.Outlined.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(if (shown) "Hide table" else "Show as table", modifier = Modifier.padding(start = 8.dp))
    }
}

/** The same numbers as the chart above, as rows: [heads] over [lines], the first column the name, the others right-aligned. Each line is one stop for a screen reader. */
@Composable
fun ChartTable(heads: List<String>, lines: List<TableLine>, tag: String, modifier: Modifier = Modifier) {
    val hide = LocalHideAmounts.current
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    // from 140% text the columns do not fit side by side (an amount broke in the middle of its digits): each line is its name over its figures, which wrap as whole words
    val stacked = LocalDensity.current.fontScale >= 1.4f
    Column(modifier.fillMaxWidth().testTag(tag)) {
        if (!stacked) Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics { heading() }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            heads.forEachIndexed { c, h -> Text(h, style = MaterialTheme.typography.labelMedium, color = quiet, textAlign = if (c == 0) TextAlign.Start else TextAlign.End, modifier = Modifier.weight(if (c == 0) 0.8f else 1f)) }
        }
        lines.forEachIndexed { i, line ->
            val rowMod = Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) { contentDescription = privatelyPlain(line.spoken, hide) }.testTag("${tag}_row_$i")
            if (stacked) Column(rowMod) {
                Text(line.cells.first(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    line.cells.drop(1).forEachIndexed { c, cell -> if (cell.isNotEmpty()) Text("${heads.getOrElse(c + 1) { "" }} $cell".trim(), style = MaterialTheme.typography.bodyMedium, color = quiet, maxLines = 1) }
                }
            } else Row(rowMod, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                line.cells.forEachIndexed { c, cell -> Text(cell, style = MaterialTheme.typography.bodyMedium, color = if (c == 0) MaterialTheme.colorScheme.onSurface else quiet, textAlign = if (c == 0) TextAlign.Start else TextAlign.End, modifier = Modifier.weight(if (c == 0) 0.8f else 1f)) }
            }
            if (i < lines.lastIndex) androidx.compose.material3.HorizontalDivider()
        }
    }
}

// ---- the ring ----

/**
 * The ring of "Where it went" (UI redesign R5): a small ring on the left (about 132 dp; 112 dp above its key from 140% text) and its key on the right: the three biggest slices, each with its share and amount, and a
 * link to all of them. The ranked list below it is the one list of the page. The ring shows the five biggest and "Others"; touching a slice (or a key row) lifts it and the same row of the list is marked ([picked]
 * is the page's, so the list can mark it too). "Show as table" lists every slice, exact, and the ring is also one description for a screen reader. The middle says the total once, in whole units.
 */
@Composable
fun BreakdownRing(slices: List<Slice>, totalText: String, label: String, picked: String?, onPick: (String?) -> Unit, allWord: String, onShowAll: () -> Unit) {
    val shown = slices.filter { it.share > 0 }
    if (shown.isEmpty()) return
    val top = shown.take(5)
    // shares are whole percents rounded down, so the rest is what the five biggest leave of 100 (never less than the rest's own sum): the key adds up
    val rest = if (shown.size > 5) maxOf(shown.drop(5).sumOf { it.share }, 100 - top.sumOf { it.share }) else 0
    val restMinor = shown.drop(5).sumOf { it.minor }
    val all = top + if (rest > 0) listOf(Slice("others", "Others", rest, OTHERS, money(restMinor), restMinor, null)) else emptyList()
    val reduce = LocalReduceMotion.current
    val sweep = remember(all) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(all, reduce) { if (reduce) sweep.snapTo(1f) else sweep.animateTo(1f, tween(Motion.CHART)) }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val pulls = all.map { s -> animateFloatAsState(if (picked == s.key) 1f else 0f, Motion.tweenOrSnap(reduce, Motion.CHIP, Motion.EaseOut), label = "pull") }
    val fades = all.map { s -> animateFloatAsState(if (picked != null && picked != s.key) 0.45f else 1f, Motion.tweenOrSnap(reduce, Motion.CHIP, Motion.EaseOut), label = "dim") }
    fun toggle(key: String) { onPick(if (picked == key) null else key) }
    var table by rememberSaveable { mutableStateOf(false) }
    val stacked = LocalDensity.current.fontScale >= 1.4f
    val ringSize = if (stacked) 112.dp else 132.dp
    val ring: @Composable () -> Unit = {
        Box(
            Modifier.size(ringSize).semantics { contentDescription = "Breakdown: " + all.joinToString(", ") { "${it.name} ${if (it.share < 1) "less than 1%" else "${it.share}%"}" } }.pointerInput(all) {
                detectTapGestures { tap ->
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val dx = tap.x - cx
                    val dy = tap.y - cy
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist < cx * 0.45f || dist > cx) { onPick(null); return@detectTapGestures }
                    var deg = (atan2(dx.toDouble(), -dy.toDouble()) * 180.0 / PI).toFloat()
                    if (deg < 0f) deg += 360f
                    val total = all.sumOf { it.share }.coerceAtLeast(1)
                    var acc = 0f
                    for (s in all) {
                        acc += 360f * s.share / total
                        if (deg <= acc) { toggle(s.key); break }
                    }
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(ringSize)) {
                val stroke = size.width * 0.105f
                val pull = size.width * 0.04f
                val inset = stroke / 2 + pull
                val arc = Size(size.width - 2 * inset, size.height - 2 * inset)
                val radius = arc.width / 2f
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                val total = all.sumOf { it.share }.coerceAtLeast(1)
                // a round cap reaches half a stroke beyond the end of its arc: the arc is shortened by that at both ends and by a 3 dp gap, so the visible gap between two slices is that gap
                val capDeg = (stroke / 2f / radius) * 180f / PI.toFloat()
                val gapDeg = (3.dp.toPx() / radius) * 180f / PI.toFloat()
                var start = -90f
                all.forEachIndexed { i, s ->
                    val full = 360f * s.share / total
                    val mid = ((start + full / 2f).toDouble() * PI / 180.0)
                    val out = pull * pulls[i].value
                    val shift = Offset((cos(mid) * out).toFloat(), (sin(mid) * out).toFloat())
                    if (all.size == 1) {
                        drawArc(s.color, start, 360f * sweep.value, false, Offset(inset, inset) + shift, arc, style = Stroke(stroke))
                    } else {
                        val visible = full * sweep.value - gapDeg - 2 * capDeg
                        // a slice too thin for its caps is a dot once the sweep is complete
                        val drawn = if (visible > 0f) visible else if (sweep.value >= 0.999f) 0.01f else 0f
                        if (drawn > 0f) drawArc(s.color.copy(alpha = fades[i].value), start + gapDeg / 2f + capDeg, drawn, false, Offset(inset, inset) + shift, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    start += full
                }
            }
            // the middle is drawn in whole units ("₹27,167", not "₹27,167.00"): the screen reader and the table still have the exact amount
            val hideAmounts = LocalHideAmounts.current
            val exact = privatelyPlain("$label $totalText", hideAmounts)
            // from 140% text the hole of the 112 dp ring cannot hold a figure at that size (it was cut): the total is in the card above, so the middle is left empty and the screen reader still hears it
            if (stacked) Box(Modifier.clearAndSetSemantics { contentDescription = exact })
            else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 14.dp).clearAndSetSemantics { contentDescription = exact }) {
                Text(label, style = NumberStyle.Chart, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(totalText.wholeAmounts(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, autoSize = amountFit(16))
            }
        }
    }
    val key: @Composable (Modifier) -> Unit = { mod ->
        Column(mod, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            all.take(3).forEach { s ->
                val source = remember { MutableInteractionSource() }
                val pressed by source.collectIsPressedAsState()
                LaunchedEffect(pressed) { if (pressed) toggle(s.key) }
                val base = Modifier.fillMaxWidth().clip(Radius.S).background(if (picked == s.key) s.color.copy(alpha = 0.10f) else Color.Transparent)
                val tappable = s.onClick?.let { base.clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), onClick = it) }
                    ?: base.pointerInput(s.key) { detectTapGestures(onPress = { toggle(s.key) }) }
                Row(tappable.padding(vertical = 6.dp, horizontal = 4.dp).testTag("breakdown_${s.key}"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(10.dp).background(s.color, CircleShape))
                    if (stacked) Column(Modifier.weight(1f)) {
                        Text(s.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(shareText(s.share), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            WholeText(s.amount, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text(s.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(shareText(s.share), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        WholeText(s.amount, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 1)
                    }
                }
            }
            if (shown.size > 3) TextButton(onClick = onShowAll, modifier = Modifier.testTag("breakdown_all")) { Text("All ${shown.size} $allWord ›") }
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("breakdown")) {
        if (stacked) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { ring(); key(Modifier.fillMaxWidth()) }
        else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { ring(); key(Modifier.weight(1f)) }
        TableToggle(table, { table = !table }, "ring_table_toggle", Modifier.align(Alignment.Start))
        if (table) ChartTable(
            listOf("Name", "Share", "Amount"),
            all.map { TableLine(listOf(it.name, shareText(it.share), it.amount), "${it.name}: ${it.amount}, ${if (it.share < 1) "less than 1%" else "${it.share}%"} of the total") },
            "ring_table",
        )
    }
}

// ---- the bars ----

/** The room above the tallest bar for the scrub bubble at the normal text size (it grows with the text up to 200%, since the bubble is two lines of text). */
private val BUBBLE_ROOM = 44.dp
private val BARS_HEIGHT = 96.dp

/**
 * The bars over time (UI S4): drawn on one canvas with round ends, one accent (the period being read at full strength, the others at a strength that stays readable on light and dark: 3 to 1 against the
 * page), a dashed line at the average of the FINISHED periods, and a scrubber: a finger dragged along them shows a bubble with that bar's total and its change from the bar before, with a tick for each
 * bar passed; a tap on a bar still opens that period's entries. A period still going on is drawn as a dashed outline ("so far"): it is not a finished total, so it is left out of the average and has no
 * change from the bar before. The selected bar and the tallest carry their figure (short, cut never rounded up) without a touch. Nothing animates in the drag, and the bars grow in once, from one animation
 * read only while drawing (at once under Reduce motion). Each bar is also its own stop for a screen reader, with its amount and change spoken, and "Show as table" lists them.
 */
@Composable
fun TrendChart(report: ReportUi, onBar: (Long, Long) -> Unit) {
    val bars = report.trend
    val n = bars.size
    if (n == 0) return
    val hide = LocalHideAmounts.current
    val reduce = LocalReduceMotion.current
    val feel = rememberFeel()
    val accent = MaterialTheme.colorScheme.primary
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val grow = remember { Animatable(if (reduce) 1f else 0f) }
    val shape = bars.map { it.fraction to it.minor }
    LaunchedEffect(shape, reduce) { if (reduce) grow.snapTo(1f) else { grow.snapTo(0f); grow.animateTo(1f, tween(Motion.BAR, easing = FastOutSlowInEasing)) } }
    // read once, when the bars have finished growing: the figures on the bars then appear (reading the animation itself here would recompose the chart on every frame)
    val grown by remember { androidx.compose.runtime.derivedStateOf { grow.value >= 0.999f } }
    var scrub by remember { mutableIntStateOf(-1) }
    var table by rememberSaveable { mutableStateOf(false) }
    // the average is of finished periods: a month half lived would pull it down
    val withData = bars.filter { it.minor > 0 && !it.open }
    val average = if (withData.size >= 2) withData.sumOf { it.minor } / withData.size else 0L
    val averageFraction = if (average > 0) withData.map { it.fraction }.average().toFloat() else 0f
    /** "so far" for the open bar, plain for the rest. */
    fun nameOf(b: TrendBarUi) = if (b.open) "${b.label} so far" else b.label
    // a bar's change from the bar before; none for an open bar (a part against a whole), nor for the bar after it
    fun changeOf(i: Int): String? {
        val prev = bars.getOrNull(i - 1) ?: return null
        if (bars[i].open || prev.open) return null
        val d = bars[i].minor - prev.minor
        return ReportSentence.change(money(abs(d)), d.compareTo(0L), prev.label)
    }
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 2f)
    val room = BUBBLE_ROOM * fontScale
    val title = if (report.mode == ReportMode.PERIOD) "Last six periods" else "Spending by month"
    val unitWord = if (report.mode != ReportMode.PERIOD || report.periodModeLabel == "Month") "months" else "periods"
    val highest = bars.maxByOrNull { it.minor }
    val tallest = bars.indexOf(highest)
    val summary = title + ", bar chart. " + (highest?.takeIf { it.minor > 0 }?.let { "Highest: ${nameOf(it)}, ${it.amount}. " } ?: "") + (if (average > 0) "Average ${money(average)}" + (if (bars.any { it.open }) " of the finished periods" else "") + ". " else "") + "Each bar opens its period's entries; the table lists them."
    Column(Modifier.fillMaxWidth().padding(16.dp).testTag("trend")) {
        val stacked = LocalDensity.current.fontScale >= 1.4f
        val titleText: @Composable (Modifier) -> Unit = { m -> Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = m.semantics { heading() }) }
        // the average is named beside the title, not on the chart, where its label ran into the first bar
        val averageKey: @Composable () -> Unit = {
            if (average > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("trend_average")) {
                Canvas(Modifier.size(width = 16.dp, height = 2.dp)) { drawLine(quiet, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))) }
                Text("avg ${Money(average, BaseCurrency.current).compact()}" + if (bars.any { it.open }) ", full $unitWord" else "", style = NumberStyle.Chart, color = quiet, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { titleText(Modifier); averageKey() }
        else Row(verticalAlignment = Alignment.CenterVertically) { titleText(Modifier.weight(1f)); averageKey() }
        Box(
            Modifier.fillMaxWidth().padding(top = 8.dp).height(room + BARS_HEIGHT).testTag("trend_chart")
                .chartScrub(n, onScrub = { i -> scrub = i; feel(Feel.STEP) }, onEnd = { scrub = -1 }, onTap = { i -> bars.getOrNull(i)?.let { onBar(it.startDay, it.endDay) } })
                .semantics { contentDescription = privatelyPlain(summary, hide) },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val slot = size.width / n
                val barW = minOf(slot * 0.62f, 44.dp.toPx())
                val maxH = BARS_HEIGHT.toPx()
                val baseY = size.height
                val r = minOf(barW / 2f, 8.dp.toPx())
                val focus = scrub
                bars.forEachIndexed { i, b ->
                    val h = (maxH * b.fraction * grow.value).coerceAtLeast(if (b.fraction > 0f) 4.dp.toPx() else 2.dp.toPx())
                    val topLeft = Offset(slot * i + (slot - barW) / 2f, baseY - h)
                    if (b.open) {
                        // so far: a light fill and a dashed outline, never the solid bar of a finished total
                        val on = focus < 0 || i == focus
                        drawRoundRect(accent.copy(alpha = if (on) 0.18f else 0.10f), topLeft, Size(barW, h), CornerRadius(r, r))
                        drawRoundRect(accent.copy(alpha = if (on) 1f else 0.5f), topLeft, Size(barW, h), CornerRadius(r, r), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx()))))
                    } else {
                        val alpha = if (focus >= 0) (if (i == focus) 1f else if (b.isSelected) 0.7f else 0.5f) else (if (b.isSelected) 1f else 0.6f)
                        drawRoundRect(accent.copy(alpha = alpha), topLeft, Size(barW, h), CornerRadius(r, r))
                    }
                }
                if (averageFraction > 0f && grow.value >= 0.999f) {
                    val y = baseY - maxH * averageFraction
                    drawLine(quiet.copy(alpha = 0.75f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                }
            }
            // a stop for each bar: what a screen reader reads, and what a test finds; the touch itself is handled by the chart above (a tap opens the bar's period, a drag scrubs)
            Row(Modifier.fillMaxSize()) {
                bars.forEachIndexed { i, b ->
                    Box(
                        Modifier.weight(1f).fillMaxHeight().testTag("trend_bar_$i").semantics {
                            contentDescription = privatelyPlain(ReportSentence.trendBar(nameOf(b), b.amount, changeOf(i)?.let { "$it." }), hide)
                            role = Role.Button
                            selected = b.isSelected
                            onClick(label = "Open the entries of ${b.label}") { onBar(b.startDay, b.endDay); true }
                        },
                    ) {
                        // the figure on the selected bar and on the tallest (one label when they are the same), short and cut never rounded up; hidden while a finger scrubs, when the bubble speaks
                        if (grown && scrub < 0 && b.minor > 0 && (b.isSelected || i == tallest)) Text(
                            Money(b.minor, BaseCurrency.current).compact(), style = NumberStyle.Chart, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.TopCenter).wrapContentWidth(Alignment.CenterHorizontally, unbounded = true)
                                .offset(y = room + BARS_HEIGHT * (1f - b.fraction) - 16.dp * fontScale)
                                // on the page colour, so the dashed average line never runs through the figure
                                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp)).padding(horizontal = 2.dp).clearAndSetSemantics { semanticsTestTag = "trend_value_$i" }, // the bar says it all to a screen reader
                        )
                    }
                }
            }
            val i = scrub
            if (i in 0 until n) {
                val b = bars[i]
                Column(
                    Modifier.testTag("trend_bubble")
                        .layout { measurable, constraints ->
                            val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                            layout(p.width, p.height) {
                                val cx = (constraints.maxWidth / n) * (i + 0.5f)
                                p.place((cx - p.width / 2f).coerceIn(0f, (constraints.maxWidth - p.width).coerceAtLeast(0).toFloat()).roundToInt(), 0)
                            }
                        }
                        .clip(Radius.S).background(MaterialTheme.colorScheme.inverseSurface).padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("${nameOf(b)} · ${b.amount}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.inverseOnSurface, maxLines = 1)
                    changeOf(i)?.let { Text(it, style = NumberStyle.Chart, color = MaterialTheme.colorScheme.inverseOnSurface, maxLines = 1) }
                }
            }
        }
        // the bar labels: a tick on the axis each, at the size of a label (11 sp); the stops above carry the words for a screen reader
        Row(Modifier.fillMaxWidth().padding(top = 4.dp).clearAndSetSemantics {}) {
            bars.forEach { b ->
                Text(b.label, style = NumberStyle.Chart, maxLines = 1, textAlign = TextAlign.Center, fontWeight = if (b.isSelected) FontWeight.Bold else null, color = if (b.isSelected) accent else quiet, modifier = Modifier.weight(1f))
            }
        }
        // with nothing spent in any of the periods there is nothing to read, scrub or list: only the empty bars and their labels stay
        val any = highest != null && highest.minor > 0
        val selected = bars.firstOrNull { it.isSelected }
        if (any) Text(
            (selected?.let { "${nameOf(it)}: ${it.amount}. " } ?: "") + "Slide along the bars to read each one; touch one to open it.",
            style = MaterialTheme.typography.bodySmall, color = quiet, modifier = Modifier.padding(top = 8.dp).testTag("trend_hint"),
        )
        if (any) TableToggle(table, { table = !table }, "trend_table_toggle", Modifier.align(Alignment.Start))
        if (any && table) ChartTable(
            listOf("Period", "Spent", "Change"),
            bars.mapIndexed { i, b ->
                val prev = bars.getOrNull(i - 1)
                val d = if (prev == null || b.open || prev.open) null else b.minor - prev.minor
                val change = d?.let { (if (it > 0) "+" else if (it < 0) "−" else "") + money(abs(it)) } ?: ""
                TableLine(listOf(nameOf(b), b.amount, change), ReportSentence.trendBar(nameOf(b), b.amount, changeOf(i)?.let { "$it." }))
            },
            "trend_table",
        )
    }
}

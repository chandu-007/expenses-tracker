package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import app.expenses.core.storage.ReportGroup
import app.expenses.GroupRowUi
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import app.expenses.core.domain.ReportSentence
import app.expenses.DrillLineUi
import app.expenses.DrillUi
import app.expenses.ReportMode
import app.expenses.ReportRowUi
import app.expenses.ReportUi
import app.expenses.core.storage.DrillTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    tabBar: @Composable () -> Unit = {},
    report: ReportUi?,
    onBack: () -> Unit,
    onMode: (ReportMode) -> Unit,
    onStep: (Int) -> Unit,
    onDrill: (DrillTarget, startDay: Long?, endDay: Long?, label: String?) -> Unit,
    onCloseDrill: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onGroup: (ReportGroup) -> Unit = {},
    onRange: (startDay: Long, endDay: Long) -> Unit = { _, _ -> },
    /** A new report is being worked out (over a big ledger it takes a moment): a thin bar shows after a short wait, while the old figures stay. */
    working: Boolean = false,
) {
    var pickingRange by rememberSaveable { mutableStateOf<String?>(null) }
    var slow by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(working) { slow = false; if (working) { kotlinx.coroutines.delay(WORKING_SHOWS_AFTER_MS); slow = true } }
    val drill = report?.drill
    if (drill != null) {
        DrillScreen(drill, onCloseDrill, onOpenEntry)
        return
    }
    pickingRange?.let { which ->
        val initial = if (which == "from") report?.customStartDay else report?.customEndDay?.minus(1)
        val state = rememberDatePickerState(initialSelectedDateMillis = initial?.times(86_400_000L))
        DatePickerDialog(
            onDismissRequest = { pickingRange = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val day = ms.floorDiv(86_400_000L)
                        val s = report?.customStartDay ?: day; val e = (report?.customEndDay ?: (day + 1)) - 1
                        if (which == "from") onRange(day, e) else onRange(s, day)
                    }
                    pickingRange = null
                }, modifier = Modifier.testTag("range_ok")) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { pickingRange = null }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
    Scaffold(bottomBar = { tabBar() }, topBar = { ScreenHeader("Reports", onBack, "reports_back") }) { padding ->
        // a screen reader hears "Reports loaded" only when the wait was long (over a second): a page that comes at once needs no announcement
        var waitedLong by remember { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(report == null) { if (report == null) { kotlinx.coroutines.delay(ANNOUNCE_AFTER_MS); waitedLong = true } }
        if (report == null) {
            ReportsSkeleton(padding)
            return@Scaffold
        }
        if (waitedLong) Box(Modifier.size(1.dp).semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite; contentDescription = "Reports loaded" }.testTag("reports_loaded_announcement"))
        val colors = rememberChartColors(report)
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("report_list"), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "tip") { TipSlot("reports") }
            item {
                SegmentedChoice(
                    listOf(Segment(ReportMode.PERIOD, "By period", "mode_period"), Segment(ReportMode.YEAR, "By year", "mode_year"), Segment(ReportMode.FINANCIAL_YEAR, "Financial year", "mode_fy"), Segment(ReportMode.CUSTOM, "Any dates", "mode_custom")),
                    report.mode, onMode, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (report.mode == ReportMode.CUSTOM) Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickingRange = "from" }, modifier = Modifier.testTag("custom_from")) { Text("From ${dayLabel(report.customStartDay)}") }
                    OutlinedButton(onClick = { pickingRange = "to" }, modifier = Modifier.testTag("custom_to")) { Text("To ${dayLabel(report.customEndDay - 1)}") }
                }
                SegmentedChoice(
                    listOf(ReportGroup.CATEGORY to "Category", ReportGroup.ACCOUNT to "Account", ReportGroup.TAG to "Tag", ReportGroup.MERCHANT to "Merchant").map { (g, label) -> Segment(g, label, "group_${g.name}") },
                    report.group, onGroup, Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (report.mode != ReportMode.CUSTOM) TextButton(onClick = { onStep(-1) }, modifier = Modifier.testTag("report_prev").semantics { contentDescription = "Earlier" }) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
                    Text(report.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).testTag("report_title"), maxLines = 1)
                    if (report.mode != ReportMode.CUSTOM) TextButton(onClick = { onStep(1) }, enabled = report.canGoNext, modifier = Modifier.testTag("report_next").semantics { contentDescription = "Later" }) { Text("›", style = MaterialTheme.typography.headlineSmall) }
                }
            }
            item {
                // one sentence that says the point before the chart does (UI S4)
                val top = if (report.group == ReportGroup.CATEGORY) report.rows.firstOrNull { !it.negative }?.let { Triple(it.name, it.share, it.amount) } else report.groupRows.firstOrNull { !it.negative }?.let { Triple(it.name, it.share, it.amount) }
                val sentence = top?.let { ReportSentence.headline(report.group.name.lowercase(), it.first, it.second, it.third, report.spent) }.orEmpty()
                if (sentence.isNotEmpty()) Text(sentence, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("report_headline"))
            }
            item { ReportSummary(report, onSpent = { onDrill(DrillTarget.All, null, null, null) }, onIncome = { onDrill(DrillTarget.Income, null, null, null) }) }
            item { TrendChart(report) { start, end -> onDrill(DrillTarget.All, start, end, null) } }
            item {
                val slices = if (report.group == ReportGroup.CATEGORY) report.rows.filter { !it.negative }.map { r ->
                    Slice(r.categoryId ?: "none", r.name, r.share, colors[r.categoryId], r.amount, r.minor, r.name) { onDrill(if (r.categoryId == null) DrillTarget.Uncategorised else DrillTarget.InCategory(r.categoryId), null, null, null) }
                } else report.groupRows.filter { !it.negative }.map { r ->
                    Slice(r.key ?: "none", r.name, r.share, colors[r.key], r.amount, r.minor, null) {
                        onDrill(when (report.group) { ReportGroup.ACCOUNT -> DrillTarget.InAccount(r.key!!); ReportGroup.TAG -> r.key?.let { DrillTarget.WithTag(it) } ?: DrillTarget.Untagged; else -> DrillTarget.AtMerchant(r.key.orEmpty()) }, null, null, r.name)
                    }
                }
                BreakdownRing(slices, report.spent, "Spent")
            }
            item {
                HorizontalDivider()
                Text("By ${report.group.name.lowercase()}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp).testTag("report_group_title"))
                if (report.group == ReportGroup.ACCOUNT) Text("Your share of each expense, filed under the account it was paid from. A shared expense shows only what you consumed, so this is not the amount that left the account.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp).testTag("report_account_note"))
                if (report.group == ReportGroup.TAG) Text("An entry with several tags counts under each, so these can add up to more than the total.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                if (report.group == ReportGroup.MERCHANT) Text("Merchants are grouped by the name as typed, ignoring capitals. Names you counted as one shop (More, Capture, Category rules) are added together.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                if (if (report.group == ReportGroup.CATEGORY) report.rows.isEmpty() else report.groupRows.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) { EmptyArt(EmptyKind.REPORTS) }
                    Text("Nothing spent in this period. Pick another period above.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp).testTag("report_empty"))
                }
            }
            if (report.group != ReportGroup.CATEGORY) itemsIndexed(report.groupRows, key = { _, r -> r.key ?: "\u0000none" }) { _, row ->
                GroupReportRow(row, colors[row.key]) {
                    val target = when (report.group) {
                        ReportGroup.ACCOUNT -> DrillTarget.InAccount(row.key!!)
                        ReportGroup.TAG -> row.key?.let { DrillTarget.WithTag(it) } ?: DrillTarget.Untagged
                        else -> DrillTarget.AtMerchant(row.key.orEmpty())
                    }
                    onDrill(target, null, null, row.name)
                }
                HorizontalDivider()
            }
            if (report.group == ReportGroup.CATEGORY) itemsIndexed(report.rows, key = { _, r -> r.categoryId ?: "" }) { _, row ->
                CategoryReportRow(row, indent = false, color = colors[row.categoryId], onDrill = onDrill)
                row.children.forEach { child -> CategoryReportRow(child, indent = true, color = colors[row.categoryId].copy(alpha = 0.7f), onDrill = onDrill) }
                HorizontalDivider()
            }
        }
        if (slow) LinearProgressIndicator(Modifier.padding(padding).fillMaxWidth().testTag("reports_working"))
    }
}

private const val WORKING_SHOWS_AFTER_MS = 250L
private const val ANNOUNCE_AFTER_MS = 1_000L

@Composable
private fun CategoryReportRow(row: ReportRowUi, indent: Boolean, color: Color, onDrill: (DrillTarget, Long?, Long?, String?) -> Unit) {
    val target = if (row.categoryId == null) DrillTarget.Uncategorised else DrillTarget.InCategory(row.categoryId)
    Column(
        Modifier.fillMaxWidth().clickable { onDrill(target, null, null, null) }.padding(start = if (indent) 44.dp else 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
            .testTag("report_row_${row.categoryId ?: "none"}"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Text(row.name, style = if (indent) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
            Text(row.amount, style = MaterialTheme.typography.bodyLarge, color = if (row.negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text("${row.share}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp).widthIn(min = 40.dp))
        }
        LinearProgressIndicator(progress = { row.share / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)), color = color, trackColor = color.copy(alpha = 0.15f), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {})
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrillScreen(drill: DrillUi, onBack: () -> Unit, onOpenEntry: (String) -> Unit) {
    Scaffold(topBar = { ScreenHeader(drill.title, onBack, "drill_back") }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(drill.subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(drill.total, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("drill_total"))
                    Text("${drill.lines.size} ${if (drill.lines.size == 1) "entry" else "entries"}" + if (drill.truncated) " (the most recent; there are more)" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("drill_count"))
                }
                HorizontalDivider()
            }
            itemsIndexed(drill.lines, key = { _, it -> it.entryId }) { index, line ->
                DrillRow(line, CardPos.of(index == 0, index == drill.lines.lastIndex), onOpenEntry, Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun DrillRow(line: DrillLineUi, pos: CardPos, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.entryCardClip(pos).fillMaxWidth().entryCardFill(pos).clickable { onOpen(line.entryId) }.padding(horizontal = 16.dp, vertical = rowPadding(10.dp)).testTag("drill_row_${line.entryId}"), verticalAlignment = Alignment.CenterVertically) {
        CategoryAvatar(line.category, line.title, Modifier.padding(end = 14.dp))
        Column(Modifier.weight(1f)) {
            Text(line.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(listOf(line.date, line.category).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(line.amount, style = MaterialTheme.typography.bodyLarge, color = if (line.negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}

private fun dayLabel(epochDay: Long): String = app.expenses.formatEpochDay(epochDay, "d MMM yyyy")

@Composable
private fun GroupReportRow(row: GroupRowUi, color: Color, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = rowPadding(10.dp)).testTag("report_group_row_${row.key ?: "none"}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
            Text(row.amount, style = MaterialTheme.typography.bodyLarge, color = if (row.negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text("${row.share}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp).widthIn(min = 40.dp))
        }
        LinearProgressIndicator(progress = { row.share / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)), color = color, trackColor = color.copy(alpha = 0.15f), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {})
    }
}

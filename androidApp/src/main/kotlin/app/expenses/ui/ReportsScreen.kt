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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import app.expenses.DrillLineUi
import app.expenses.DrillUi
import app.expenses.ReportMode
import app.expenses.ReportRowUi
import app.expenses.ReportUi
import app.expenses.core.storage.DrillTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    report: ReportUi?,
    onBack: () -> Unit,
    onMode: (ReportMode) -> Unit,
    onStep: (Int) -> Unit,
    onDrill: (DrillTarget, startDay: Long?, endDay: Long?, label: String?) -> Unit,
    onCloseDrill: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onGroup: (ReportGroup) -> Unit = {},
    onRange: (startDay: Long, endDay: Long) -> Unit = { _, _ -> },
) {
    var pickingRange by rememberSaveable { mutableStateOf<String?>(null) }
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
                        val day = Math.floorDiv(ms, 86_400_000L)
                        val s = report?.customStartDay ?: day; val e = (report?.customEndDay ?: (day + 1)) - 1
                        if (which == "from") onRange(day, e) else onRange(s, day)
                    }
                    pickingRange = null
                }, modifier = Modifier.testTag("range_ok")) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingRange = null }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Reports") }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("reports_back")) { Text("Back") } }) }) { padding ->
        if (report == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("Loading…") }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = report.mode == ReportMode.PERIOD, onClick = { onMode(ReportMode.PERIOD) }, label = { Text("By period") }, modifier = Modifier.testTag("mode_period"))
                    FilterChip(selected = report.mode == ReportMode.YEAR, onClick = { onMode(ReportMode.YEAR) }, label = { Text("By year") }, modifier = Modifier.testTag("mode_year"))
                    FilterChip(selected = report.mode == ReportMode.FINANCIAL_YEAR, onClick = { onMode(ReportMode.FINANCIAL_YEAR) }, label = { Text("Financial year") }, modifier = Modifier.testTag("mode_fy"))
                    FilterChip(selected = report.mode == ReportMode.CUSTOM, onClick = { onMode(ReportMode.CUSTOM) }, label = { Text("Any dates") }, modifier = Modifier.testTag("mode_custom"))
                }
                if (report.mode == ReportMode.CUSTOM) Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickingRange = "from" }, modifier = Modifier.testTag("custom_from")) { Text("From ${dayLabel(report.customStartDay)}") }
                    OutlinedButton(onClick = { pickingRange = "to" }, modifier = Modifier.testTag("custom_to")) { Text("To ${dayLabel(report.customEndDay - 1)}") }
                }
                FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ReportGroup.CATEGORY to "Category", ReportGroup.ACCOUNT to "Account", ReportGroup.TAG to "Tag", ReportGroup.MERCHANT to "Merchant").forEach { (g, label) ->
                        FilterChip(selected = report.group == g, onClick = { onGroup(g) }, label = { Text(label) }, modifier = Modifier.testTag("group_${g.name}"))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (report.mode != ReportMode.CUSTOM) TextButton(onClick = { onStep(-1) }, modifier = Modifier.testTag("report_prev").semantics { contentDescription = "Earlier" }) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
                    Text(report.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).testTag("report_title"), maxLines = 1)
                    if (report.mode != ReportMode.CUSTOM) TextButton(onClick = { onStep(1) }, enabled = report.canGoNext, modifier = Modifier.testTag("report_next").semantics { contentDescription = "Later" }) { Text("›", style = MaterialTheme.typography.headlineSmall) }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Column(Modifier.fillMaxWidth().clickable { onDrill(DrillTarget.All, null, null, null) }.testTag("report_spent_card")) {
                        Text("Spent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(report.spent, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("report_spent"))
                        report.comparison?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("report_comparison")) }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Column(Modifier.weight(1f).clickable { onDrill(DrillTarget.Income, null, null, null) }.testTag("report_income_card")) {
                            Text("Income", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(report.income, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("report_income"))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Income minus spending", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(report.net, style = MaterialTheme.typography.titleMedium, color = if (report.netPositive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error, modifier = Modifier.testTag("report_net"))
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(top = 8.dp))
            }
            item { TrendChart(report) { start, end -> onDrill(DrillTarget.All, start, end, null) } }
            item {
                HorizontalDivider()
                Text("By ${report.group.name.lowercase()}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp).testTag("report_group_title"))
                if (report.group == ReportGroup.TAG) Text("An entry with several tags counts under each, so these can add up to more than the total.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                if (report.group == ReportGroup.MERCHANT) Text("Merchants are grouped by the name as typed, ignoring capitals.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                if (if (report.group == ReportGroup.CATEGORY) report.rows.isEmpty() else report.groupRows.isEmpty()) Text("Nothing spent in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp).testTag("report_empty"))
            }
            if (report.group != ReportGroup.CATEGORY) items(report.groupRows, key = { it.key ?: "\u0000none" }) { row ->
                GroupReportRow(row) {
                    val target = when (report.group) {
                        ReportGroup.ACCOUNT -> DrillTarget.InAccount(row.key!!)
                        ReportGroup.TAG -> row.key?.let { DrillTarget.WithTag(it) } ?: DrillTarget.Untagged
                        else -> DrillTarget.AtMerchant(row.key.orEmpty())
                    }
                    onDrill(target, null, null, row.name)
                }
                HorizontalDivider()
            }
            if (report.group == ReportGroup.CATEGORY) items(report.rows, key = { it.categoryId ?: "" }) { row ->
                CategoryReportRow(row, indent = false, onDrill = onDrill)
                row.children.forEach { child -> CategoryReportRow(child, indent = true, onDrill = onDrill) }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun TrendChart(report: ReportUi, onBar: (Long, Long) -> Unit) {
    val hide = LocalHideAmounts.current
    Column(Modifier.fillMaxWidth().padding(16.dp).testTag("trend")) {
        Text(if (report.mode == ReportMode.PERIOD) "Last six periods" else "Spending by month", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            report.trend.forEachIndexed { i, bar ->
                Column(
                    Modifier.weight(1f).clickable { onBar(bar.startDay, bar.endDay) }.testTag("trend_bar_$i").semantics { contentDescription = privatelyPlain("${bar.label}: ${bar.amount}", hide) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.height(88.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier.fillMaxWidth().height((88 * bar.fraction).dp.coerceAtLeast(if (bar.fraction > 0f) 3.dp else 1.dp))
                                .background(if (bar.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                        )
                    }
                    Text(bar.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun CategoryReportRow(row: ReportRowUi, indent: Boolean, onDrill: (DrillTarget, Long?, Long?, String?) -> Unit) {
    val target = if (row.categoryId == null) DrillTarget.Uncategorised else DrillTarget.InCategory(row.categoryId)
    Column(
        Modifier.fillMaxWidth().clickable { onDrill(target, null, null, null) }.padding(start = if (indent) 44.dp else 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
            .testTag("report_row_${row.categoryId ?: "none"}"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(row.colorArgb?.let { Color(it) } ?: Color.Transparent, CircleShape))
            Text(row.name, style = if (indent) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
            Text(row.amount, style = MaterialTheme.typography.bodyLarge, color = if (row.negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text("${row.share}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp).size(width = 40.dp, height = 18.dp))
        }
        LinearProgressIndicator(progress = { row.share / 100f }, modifier = Modifier.fillMaxWidth(), color = row.colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrillScreen(drill: DrillUi, onBack: () -> Unit, onOpenEntry: (String) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(drill.title, maxLines = 1) }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("drill_back")) { Text("Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(drill.subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(drill.total, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("drill_total"))
                    Text("${drill.lines.size} ${if (drill.lines.size == 1) "entry" else "entries"}" + if (drill.truncated) " (the most recent; there are more)" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("drill_count"))
                }
                HorizontalDivider()
            }
            items(drill.lines, key = { it.entryId }) { line ->
                DrillRow(line, onOpenEntry)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun DrillRow(line: DrillLineUi, onOpen: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onOpen(line.entryId) }.padding(horizontal = 16.dp, vertical = 10.dp).testTag("drill_row_${line.entryId}"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(line.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(listOf(line.date, line.category).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(line.amount, style = MaterialTheme.typography.bodyLarge, color = if (line.negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    }
}

private fun dayLabel(epochDay: Long): String = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy").format(java.time.LocalDate.ofEpochDay(epochDay))

@Composable
private fun GroupReportRow(row: GroupRowUi, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp).testTag("report_group_row_${row.key ?: "none"}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
            Text(row.amount, style = MaterialTheme.typography.bodyLarge, color = if (row.negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text("${row.share}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp).size(width = 40.dp, height = 18.dp))
        }
        LinearProgressIndicator(progress = { row.share / 100f }, modifier = Modifier.fillMaxWidth())
    }
}

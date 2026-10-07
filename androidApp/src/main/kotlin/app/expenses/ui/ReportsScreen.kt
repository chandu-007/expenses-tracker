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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
    onDrill: (DrillTarget, startDay: Long?, endDay: Long?) -> Unit,
    onCloseDrill: () -> Unit,
    onOpenEntry: (String) -> Unit,
) {
    val drill = report?.drill
    if (drill != null) {
        DrillScreen(drill, onCloseDrill, onOpenEntry)
        return
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Reports") }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("reports_back")) { Text("Back") } }) }) { padding ->
        if (report == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("Loading…") }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = report.mode == ReportMode.PERIOD, onClick = { onMode(ReportMode.PERIOD) }, label = { Text("By period") }, modifier = Modifier.testTag("mode_period"))
                    FilterChip(selected = report.mode == ReportMode.YEAR, onClick = { onMode(ReportMode.YEAR) }, label = { Text("By year") }, modifier = Modifier.testTag("mode_year"))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onStep(-1) }, modifier = Modifier.testTag("report_prev").semantics { contentDescription = "Earlier" }) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
                    Text(report.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).testTag("report_title"), maxLines = 1)
                    TextButton(onClick = { onStep(1) }, enabled = report.canGoNext, modifier = Modifier.testTag("report_next").semantics { contentDescription = "Later" }) { Text("›", style = MaterialTheme.typography.headlineSmall) }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Column(Modifier.fillMaxWidth().clickable { onDrill(DrillTarget.All, null, null) }.testTag("report_spent_card")) {
                        Text("Spent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(report.spent, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("report_spent"))
                        report.comparison?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("report_comparison")) }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Column(Modifier.weight(1f).clickable { onDrill(DrillTarget.Income, null, null) }.testTag("report_income_card")) {
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
            item { TrendChart(report) { start, end -> onDrill(DrillTarget.All, start, end) } }
            item {
                HorizontalDivider()
                Text("By category", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
                if (report.rows.isEmpty()) Text("Nothing spent in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp).testTag("report_empty"))
            }
            items(report.rows, key = { it.categoryId ?: "" }) { row ->
                CategoryReportRow(row, indent = false, onDrill = onDrill)
                row.children.forEach { child -> CategoryReportRow(child, indent = true, onDrill = onDrill) }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun TrendChart(report: ReportUi, onBar: (Long, Long) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp).testTag("trend")) {
        Text(if (report.mode == ReportMode.YEAR) "Spending by month" else "Last six periods", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            report.trend.forEachIndexed { i, bar ->
                Column(
                    Modifier.weight(1f).clickable { onBar(bar.startDay, bar.endDay) }.testTag("trend_bar_$i").semantics { contentDescription = "${bar.label}: ${bar.amount}" },
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
private fun CategoryReportRow(row: ReportRowUi, indent: Boolean, onDrill: (DrillTarget, Long?, Long?) -> Unit) {
    val target = if (row.categoryId == null) DrillTarget.Uncategorised else DrillTarget.InCategory(row.categoryId)
    Column(
        Modifier.fillMaxWidth().clickable { onDrill(target, null, null) }.padding(start = if (indent) 44.dp else 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
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

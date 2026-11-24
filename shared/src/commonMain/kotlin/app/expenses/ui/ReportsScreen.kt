package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.semantics.heading
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    /** The budget line of the hero opens the Budget. */
    onBudget: () -> Unit = {},
    /** A new report is being worked out (over a big ledger it takes a moment): a thin bar shows after a short wait, while the old figures stay. */
    working: Boolean = false,
) {
    var pickingRange by rememberSaveable { mutableStateOf<String?>(null) }
    var slow by remember { mutableStateOf(false) }
    // the ranked list shows the biggest few; "Show all" opens it. The ring and the list share the slice being looked at, and a category's subcategories are collapsed until asked for
    var showAll by rememberSaveable(report?.group) { mutableStateOf(false) }
    var picked by remember(report?.group, report?.title) { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val reduce = LocalReduceMotion.current
    val allWord = when (report?.group) { ReportGroup.ACCOUNT -> "accounts"; ReportGroup.TAG -> "tags"; ReportGroup.MERCHANT -> "merchants"; else -> "categories" }
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
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("report_list"), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "tip") { TipSlot("reports") }
            // the mode row: one short row ("Month | Year | FY | Dates"); from 140% text a single menu button, so the figure is on the first screen
            item(key = "mode") {
                if (LocalDensity.current.fontScale >= 1.4f) ModeMenu(report, onMode) else SegmentedChoice(
                    listOf(Segment(ReportMode.PERIOD, report.periodModeLabel, "mode_period"), Segment(ReportMode.YEAR, "Year", "mode_year"), Segment(ReportMode.FINANCIAL_YEAR, "FY", "mode_fy"), Segment(ReportMode.CUSTOM, "Dates", "mode_custom")),
                    report.mode, onMode, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                // the two date buttons wrap onto their own lines at large text (side by side the second was squeezed to one letter a line)
                if (report.mode == ReportMode.CUSTOM) FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickingRange = "from" }, modifier = Modifier.testTag("custom_from")) { Text("From ${dayLabel(report.customStartDay)}") }
                    OutlinedButton(onClick = { pickingRange = "to" }, modifier = Modifier.testTag("custom_to")) { Text("To ${dayLabel(report.customEndDay - 1)}") }
                }
            }
            // the period and its arrows stay at the top while the page scrolls, so a long list never takes the way to another period out of reach
            stickyHeader(key = "period") {
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (report.mode != ReportMode.CUSTOM) TextButton(onClick = { onStep(-1) }, modifier = Modifier.testTag("report_prev").semantics { contentDescription = "Earlier" }) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
                    Column(Modifier.weight(1f)) {
                        Text(report.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("report_title"), maxLines = 1)
                        if (report.open) Text("so far · day ${report.openDay} of ${report.periodDays}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("report_open_note"), maxLines = 1)
                    }
                    if (report.mode != ReportMode.CUSTOM) TextButton(onClick = { onStep(1) }, enabled = report.canGoNext, modifier = Modifier.testTag("report_next").semantics { contentDescription = "Later" }) { Text("›", style = MaterialTheme.typography.headlineSmall) }
                }
            }
            item { ReportSummary(report, onSpent = { onDrill(DrillTarget.All, null, null, null) }, onIncome = { onDrill(DrillTarget.Income, null, null, null) }, onBudget = onBudget) }
            item { TrendChart(report) { start, end -> onDrill(DrillTarget.All, start, end, null) } }
            // the grouping changes only what is below it (the ring and the list), so it sits here and not at the top of the page
            item(key = "groups") {
                Text("Where it went", style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).semantics { heading() }.testTag("report_where"))
                SegmentedChoice(
                    listOf(ReportGroup.CATEGORY to "Category", ReportGroup.ACCOUNT to "Account", ReportGroup.TAG to "Tag", ReportGroup.MERCHANT to "Merchant").map { (g, label) -> Segment(g, label, "group_${g.name}") },
                    report.group, onGroup, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            // one sentence that says the point of the list (UI S4), under the grouping it is about
            item(key = "headline") {
                val top = if (report.group == ReportGroup.CATEGORY) report.rows.firstOrNull { !it.negative }?.let { Triple(it.name, it.share, it.amount) } else report.groupRows.firstOrNull { !it.negative }?.let { Triple(it.name, it.share, it.amount) }
                val sentence = top?.let { ReportSentence.headline(report.group.name.lowercase(), it.first, it.second, it.third) }.orEmpty()
                if (sentence.isNotEmpty()) WholeText(sentence, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("report_headline"))
            }
            item(key = "ring") {
                val slices = if (report.group == ReportGroup.CATEGORY) report.rows.filter { !it.negative }.map { r ->
                    Slice(r.categoryId ?: "none", r.name, r.share, colors[r.categoryId], r.amount, r.minor, r.name) { onDrill(if (r.categoryId == null) DrillTarget.Uncategorised else DrillTarget.InCategory(r.categoryId), null, null, null) }
                } else report.groupRows.filter { !it.negative }.map { r ->
                    Slice(r.key ?: "none", r.name, r.share, colors[r.key], r.amount, r.minor, null) {
                        onDrill(when (report.group) { ReportGroup.ACCOUNT -> DrillTarget.InAccount(r.key!!); ReportGroup.TAG -> r.key?.let { DrillTarget.WithTag(it) } ?: DrillTarget.Untagged; else -> DrillTarget.AtMerchant(r.key.orEmpty()) }, null, null, r.name)
                    }
                }
                BreakdownRing(
                    slices, report.spent, "Spent", picked, { picked = it }, allWord,
                    onShowAll = { showAll = true; scope.launch { if (reduce) listState.scrollToItem(RANKED_LIST_ITEM) else listState.animateScrollToItem(RANKED_LIST_ITEM) } },
                )
            }
            // the one list of the page: every row is one stop; the biggest few are shown and the rest are one tap away
            item(key = "ranked") {
                Column {
                    if (report.group == ReportGroup.ACCOUNT) Text("Your share of each expense, filed under the account it was paid from. A shared expense shows only what you consumed, so this is not the amount that left the account.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    if (report.group == ReportGroup.TAG) Text("An entry with several tags counts under each, so these can add up to more than the total.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    if (report.group == ReportGroup.MERCHANT) Text("Merchants are grouped by the name as typed, ignoring capitals. Names you counted as one shop (More, Capture, Category rules) are added together.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    if (if (report.group == ReportGroup.CATEGORY) report.rows.isEmpty() else report.groupRows.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) { EmptyArt(EmptyKind.REPORTS) }
                        Text("Nothing spent in this period. Pick another period above.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp).testTag("report_empty"))
                    }
                }
            }
            if (report.group != ReportGroup.CATEGORY) itemsIndexed(report.groupRows.let { if (showAll) it else it.take(RANKED_SHOWN) }, key = { _, r -> r.key ?: "\u0000none" }) { _, row ->
                val key = row.key ?: "none"
                RankedRow(
                    name = row.name, tileName = row.name, amount = row.amount, share = row.share, negative = row.negative, color = colors[row.key], marked = picked == key,
                    tag = "report_group_row_${row.key ?: "none"}", children = emptyList(), expanded = false, onToggle = {}, indent = false, onClick = {
                        val target = when (report.group) {
                            ReportGroup.ACCOUNT -> DrillTarget.InAccount(row.key!!)
                            ReportGroup.TAG -> row.key?.let { DrillTarget.WithTag(it) } ?: DrillTarget.Untagged
                            else -> DrillTarget.AtMerchant(row.key.orEmpty())
                        }
                        onDrill(target, null, null, row.name)
                    },
                )
                HorizontalDivider()
            }
            if (report.group == ReportGroup.CATEGORY) itemsIndexed(report.rows.let { if (showAll) it else it.take(RANKED_SHOWN) }, key = { _, r -> r.categoryId ?: "" }) { _, row ->
                val id = row.categoryId ?: "none"
                val open = id in expanded
                RankedRow(
                    name = row.name, tileName = row.name, amount = row.amount, share = row.share, negative = row.negative, color = colors[row.categoryId], marked = picked == id,
                    tag = "report_row_$id", children = row.children, expanded = open, onToggle = { expanded = if (open) expanded - id else expanded + id }, indent = false,
                    onClick = { onDrill(if (row.categoryId == null) DrillTarget.Uncategorised else DrillTarget.InCategory(row.categoryId), null, null, null) },
                )
                if (open) row.children.forEach { child ->
                    RankedRow(
                        name = child.name, tileName = child.name, amount = child.amount, share = child.share, negative = child.negative, color = colors[row.categoryId].copy(alpha = 0.7f), marked = false,
                        tag = "report_row_${child.categoryId ?: "none"}", children = emptyList(), expanded = false, onToggle = {}, indent = true,
                        onClick = { onDrill(if (child.categoryId == null) DrillTarget.Uncategorised else DrillTarget.InCategory(child.categoryId), null, null, null) },
                    )
                }
                HorizontalDivider()
            }
            val count = if (report.group == ReportGroup.CATEGORY) report.rows.size else report.groupRows.size
            if (count > RANKED_SHOWN) item(key = "show_all") {
                TextButton(onClick = { showAll = !showAll }, modifier = Modifier.padding(horizontal = 8.dp).testTag("report_show_all")) { Text(if (showAll) "Show fewer" else "Show all $count $allWord") }
            }
        }
        if (slow) LinearProgressIndicator(Modifier.padding(padding).fillMaxWidth().testTag("reports_working"))
    }
}

/** From 140% text the four segments no longer fit one row: the mode is one button that opens a menu of the same four, with the same tags. */
@Composable
private fun ModeMenu(report: ReportUi, onMode: (ReportMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val items = listOf(ReportMode.PERIOD to (report.periodModeLabel to "mode_period"), ReportMode.YEAR to ("Year" to "mode_year"), ReportMode.FINANCIAL_YEAR to ("Financial year" to "mode_fy"), ReportMode.CUSTOM to ("Any dates" to "mode_custom"))
    val current = items.first { it.first == report.mode }.second.first
    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.testTag("mode_menu").semantics { contentDescription = "Report by: $current. Change" }) { Text("$current ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { (mode, named) -> DropdownMenuItem(text = { Text(named.first) }, onClick = { open = false; onMode(mode) }, modifier = Modifier.testTag(named.second)) }
        }
    }
}

private const val WORKING_SHOWS_AFTER_MS = 250L
private const val ANNOUNCE_AFTER_MS = 1_000L

/** How many rows of the ranked list show before "Show all", and where the list begins among the page's items (the ring's "All N" scrolls there). */
private const val RANKED_SHOWN = 6
private const val RANKED_LIST_ITEM = 8

/**
 * One row of the ranked list (UI redesign R5): the tile in the colour of its slice of the ring, the name, the amount and share, and a bar in the same colour. The row opens its entries; a category with
 * subcategories has a chevron that shows or hides them (read out as expanded or collapsed). [marked] is the slice being looked at in the ring.
 */
@Composable
private fun RankedRow(
    name: String, tileName: String, amount: String, share: Int, negative: Boolean, color: Color, marked: Boolean, tag: String,
    children: List<ReportRowUi>, expanded: Boolean, onToggle: () -> Unit, indent: Boolean, onClick: () -> Unit,
) {
    val stacked = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f
    Row(Modifier.fillMaxWidth().background(if (marked) color.copy(alpha = 0.10f) else Color.Transparent), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clickable(onClick = onClick).padding(start = if (indent) 44.dp else 16.dp, end = if (children.isEmpty()) 16.dp else 0.dp, top = rowPadding(10.dp), bottom = rowPadding(10.dp)).testTag(tag),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CategoryAvatar(tileName, name, size = if (indent) 28.dp else 36.dp, tint = color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = if (indent) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = if (stacked) 3 else 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    WholeText(amount, style = MaterialTheme.typography.bodyLarge, color = if (negative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                    Text(shareText(share), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp).widthIn(min = 40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
                LinearProgressIndicator(progress = { share / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)), color = color, trackColor = color.copy(alpha = 0.15f), strokeCap = androidx.compose.ui.graphics.StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {})
            }
        }
        if (children.isNotEmpty()) Box(
            Modifier.size(48.dp).clickable(onClick = onToggle).testTag(tag.replace("report_row_", "report_row_toggle_")).semantics {
                contentDescription = (if (expanded) "Hide " else "Show ") + children.size + (if (children.size == 1) " subcategory of " else " subcategories of ") + name
                stateDescription = if (expanded) "Expanded" else "Collapsed"
                role = androidx.compose.ui.semantics.Role.Button
            },
            contentAlignment = Alignment.Center,
        ) { Text(if (expanded) "▾" else "▸", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics {}) }
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

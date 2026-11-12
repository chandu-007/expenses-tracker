package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.expenses.ScreenUndo
import app.expenses.TripBarUi
import app.expenses.TripCategoryUi
import app.expenses.TripSummaryUi
import app.expenses.core.domain.TripSummaryMath

/**
 * One trip at a glance: what was spent on its tagged entries inside its days (refunds taken off), the days, the average a day, a bar for each day (a week for a long trip), where it went by category,
 * the daily budget when there is one, and what to do next: see every entry (Search, already set to the tag and the days), tag earlier entries (counted, with an Undo bar), end the trip today.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripSummaryScreen(
    state: TripSummaryUi?,
    undo: ScreenUndo?,
    onBack: () -> Unit,
    onSeeAll: (tag: String, startDay: Long, endDay: Long) -> Unit,
    onTagEarlier: (id: String) -> Unit,
    onEndToday: (id: String) -> Unit,
    onUndo: (String) -> Unit,
    onUndoGone: (String) -> Unit,
) {
    var confirmTag by rememberSaveable { mutableStateOf(false) }
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    val snackbar = rememberUndoSnackbar(undo, onUndo, onUndoGone)
    if (state != null && confirmTag) AlertDialog(
        onDismissRequest = { confirmTag = false },
        title = { Text("Tag ${state.untaggedCount} earlier ${if (state.untaggedCount == 1) "entry" else "entries"} from these dates?", modifier = Modifier.testTag("trip_tag_title")) },
        text = {
            Text(
                "${if (state.untaggedCount == 1) "1 expense" else "${state.untaggedCount} expenses"} dated ${state.datesText} ${if (state.untaggedCount == 1) "does" else "do"} not have the tag \"${state.tag}\". Only expenses are tagged, never income, transfers or refunds. An expense you took the tag off yourself is tagged again. You can undo it right after.",
                modifier = Modifier.testTag("trip_tag_note"),
            )
        },
        confirmButton = { TextButton(onClick = { confirmTag = false; onTagEarlier(state.id) }, modifier = Modifier.heightIn(min = 48.dp).testTag("trip_tag_ok")) { Text("Tag them") } },
        dismissButton = { TextButton(onClick = { confirmTag = false }, modifier = Modifier.heightIn(min = 48.dp).testTag("trip_tag_cancel")) { Text("Not now") } },
    )
    if (state != null && confirmEnd) AlertDialog(
        onDismissRequest = { confirmEnd = false }, title = { Text("End the trip today?") },
        text = { Text("The last day of \"${state.name}\" becomes today. Entries from tomorrow on are not tagged for it. Nothing already tagged changes.", modifier = Modifier.testTag("trip_end_note")) },
        confirmButton = { TextButton(onClick = { confirmEnd = false; onEndToday(state.id) }, modifier = Modifier.heightIn(min = 48.dp).testTag("trip_end_ok")) { Text("End trip") } },
        dismissButton = { TextButton(onClick = { confirmEnd = false }, modifier = Modifier.heightIn(min = 48.dp).testTag("trip_end_cancel")) { Text("Keep going") } },
    )
    Scaffold(
        topBar = { AreaTopBar(state?.name ?: "Trip", Area.MONEY, Icons.Outlined.Flight, onBack, "trip_summary_back") },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state == null) {
            Text("This trip is no longer there.", modifier = Modifier.padding(padding).padding(16.dp).testTag("trip_summary_gone"))
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp).testTag("trip_summary"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // the figures
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Spent on this trip", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(state.totalText, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.testTag("trip_total"))
                Text("${state.datesText} · ${state.daysText}${if (!state.enabled) " · Off" else ""}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.testTag("trip_days"))
                Text(
                    (state.averageText?.let { "Average $it a day · " } ?: "") + "${state.entryCount} ${if (state.entryCount == 1) "entry" else "entries"}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.testTag("trip_average"),
                )
            }
            state.budgetLine?.let { line ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("trip_budget_line"))
                    state.budgetStatus?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = if (state.budgetOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("trip_budget_status")) }
                }
            }
            Text(
                "Expenses tagged \"${state.tag}\" on these days. A refund counts with the purchase it reverses.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp).testTag("trip_summary_note"),
            )
            if (state.capped) Text("Showing the newest part of a very long trip.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp).testTag("trip_capped"))

            // each day
            if (state.bars.isNotEmpty()) {
                Text(if (state.endDay - state.startDay + 1 > TripSummaryMath.DAILY_BARS_UP_TO) "Each week" else "Each day", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() })
                TripBars(state.bars)
            }

            // where it went
            if (state.categories.isNotEmpty()) {
                Text("Where it went", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() })
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("trip_categories"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.categories.forEachIndexed { i, c -> TripCategoryRow(c, i) }
                }
            } else if (state.entryCount == 0) {
                Text("Nothing is tagged for this trip on its days yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp).testTag("trip_summary_empty"))
            }

            // what to do next
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSeeAll(state.tag, state.startDay, state.endDay) }, modifier = Modifier.heightIn(min = 48.dp).testTag("trip_see_all")) { Text("See all entries") }
                if (state.untaggedCount > 0) OutlinedButton(onClick = { confirmTag = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("trip_tag_earlier")) { Text("Tag ${state.untaggedCount} earlier ${if (state.untaggedCount == 1) "entry" else "entries"}") }
                if (state.canEndToday) OutlinedButton(onClick = { confirmEnd = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("trip_end_today")) { Text("End trip today") }
            }
        }
    }
}

/** A bar for each day (or week): tap one to read its amount. The row scrolls sideways when there are more than fit; each bar is a button of at least 48 dp. */
@Composable
private fun TripBars(bars: List<TripBarUi>) {
    var selected by rememberSaveable { mutableStateOf(-1) }
    val hide = LocalHideAmounts.current
    val today = MaterialTheme.colorScheme.tertiary
    val past = MaterialTheme.colorScheme.primary
    val future = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("trip_bars"), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            bars.forEachIndexed { i, b ->
                Column(
                    Modifier.width(48.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { selected = if (selected == i) -1 else i }
                        .semantics { contentDescription = privatelyPlain(b.description + if (b.isToday) " (today)" else "", hide) }.testTag("trip_bar_$i"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.height(112.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.width(22.dp).fillMaxHeight(b.fraction.coerceIn(0.03f, 1f)).clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)).background(if (b.isFuture) future else if (b.isToday) today else past))
                    }
                    Text(b.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.heightIn(min = 32.dp).padding(top = 4.dp))
                }
            }
        }
        val b = bars.getOrNull(selected)
        Text(
            b?.description?.let { it + if (b.isToday) " (today)" else "" } ?: "Tap a bar to read its amount.",
            style = MaterialTheme.typography.bodyMedium, color = if (b == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.heightIn(min = 24.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag("trip_bar_readout"),
        )
    }
}

@Composable
private fun TripCategoryRow(c: TripCategoryUi, index: Int) {
    val color = reportColor(c.colorArgb, index)
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("trip_cat_${c.name}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Text(c.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(c.amountText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = if (c.negative) MaterialTheme.colorScheme.error else Color.Unspecified)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(progress = { c.share / 100f }, color = color, modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)))
            Text("${c.share}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

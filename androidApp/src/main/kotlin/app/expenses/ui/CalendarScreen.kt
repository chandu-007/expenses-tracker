package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.expenses.CalendarCellUi
import app.expenses.CalendarUi
import app.expenses.core.domain.formatted

/**
 * A month of daily spending, Monday first. Heavier days are shaded darker (relative to the heaviest day of the month), a dot marks a day
 * with a bill still to come, and tapping a day lists its entries and bills below, where an entry opens for editing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(state: CalendarUi?, onBack: () -> Unit, onShift: (Int) -> Unit, onSelect: (Long) -> Unit, onOpenEntry: (String) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Calendar") }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("calendar_back")) { Text("Back") } }) }) { pad ->
        if (state == null) return@Scaffold
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onShift(-1) }, modifier = Modifier.testTag("cal_prev").semantics { contentDescription = "Previous month" }) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("cal_month"))
                    Text("Spent ${state.total}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("cal_total"))
                }
                TextButton(onClick = { onShift(1) }, modifier = Modifier.testTag("cal_next").semantics { contentDescription = "Next month" }) { Text("›", style = MaterialTheme.typography.headlineMedium) }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            val slots: List<CalendarCellUi?> = List(state.blanks) { null } + state.cells
            slots.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    (week + List(7 - week.size) { null }).forEach { cell ->
                        if (cell == null) Box(Modifier.weight(1f).height(56.dp)) else Cell(cell, cell.epochDay == state.selectedEpochDay, Modifier.weight(1f)) { onSelect(cell.epochDay) }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            if (state.selectedEpochDay == null) {
                Text("Tap a day to see what was spent and what is due.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp).testTag("cal_hint"))
            } else {
                Text(state.dayTitle, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 8.dp).testTag("cal_day_title"))
                Text("Spent ${state.dayTotal}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp).testTag("cal_day_total"))
                state.dayBills.forEach { Text("Due: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp).testTag("cal_bill")) }
                if (state.dayLines.isEmpty() && state.dayBills.isEmpty()) Text("Nothing on this day.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp).testTag("cal_empty_day"))
                state.dayLines.forEach { l ->
                    Row(Modifier.fillMaxWidth().clickable { onOpenEntry(l.entryId) }.padding(horizontal = 8.dp, vertical = 10.dp).testTag("cal_line_${l.title}"), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            l.categoryName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Text(l.amount.formatted(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(c: CalendarCellUi, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shade = MaterialTheme.colorScheme.primary.copy(alpha = c.level * 0.18f)
    val hide = LocalHideAmounts.current
    val description = "${c.day}, " + (if (c.spentMinor == 0L) "nothing spent" else "spent ${c.spent}") + (if (c.bills > 0) ", ${c.bills} ${if (c.bills == 1) "bill" else "bills"} due" else "")
    Column(
        modifier.height(56.dp).padding(1.dp).clip(RoundedCornerShape(6.dp)).background(shade)
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)) else Modifier)
            .clickable(onClick = onClick).padding(2.dp).testTag("cal_day_${c.day}").semantics { contentDescription = privatelyPlain(description, hide) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(c.day.toString(), style = MaterialTheme.typography.labelMedium, color = if (c.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Text(c.spent, fontSize = 10.sp, maxLines = 1, modifier = Modifier.testTag("cal_amount_${c.day}"))
        if (c.bills > 0) Text("•", color = MaterialTheme.colorScheme.tertiary, fontSize = 12.sp, modifier = Modifier.testTag("cal_bill_dot_${c.day}"))
    }
}

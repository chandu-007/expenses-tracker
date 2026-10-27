package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.expenses.core.domain.FirstWeekItem
import app.expenses.core.domain.FirstWeekStep

/**
 * The checklist on Home for the first week (see [app.expenses.core.domain.FirstWeek]): a few steps that make the app work by itself, each ticked when it is true and
 * each a tap away from the screen where it is done. Nothing is drawn when [items] is empty. "Hide" closes it for good; the title folds it to one line for now.
 */
@Composable
fun FirstWeekCard(items: List<FirstWeekItem>, onOpen: (FirstWeekStep) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    // Folded to one line until opened: the card must not push the entry just added below the fold of a small screen.
    var folded by rememberSaveable { mutableStateOf(true) }
    val done = items.count { it.done }
    Card(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag("first_week_card"), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f).compositeOver(MaterialTheme.colorScheme.surface)),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { folded = !folded }.padding(vertical = 6.dp).testTag("first_week_toggle")) {
                    Text("Getting started", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("$done of ${items.size} done" + if (folded) " · tap to open" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("first_week_progress"))
                }
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("first_week_dismiss")) { Text("Hide") }
            }
            if (!folded) items.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpen(item.step) }.padding(vertical = 8.dp, horizontal = 2.dp).testTag("first_week_item_${item.step.name}"), // 48 dp: a finished step has no second line and was 44
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Tick(item.done, Modifier.testTag(if (item.done) "first_week_done_${item.step.name}" else "first_week_todo_${item.step.name}"))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (item.done) null else FontWeight.SemiBold, color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        if (!item.done) Text(item.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** A round tick box: filled with a check once done, an empty ring before. Drawn here, so it needs no icon beyond the check. */
@Composable
private fun Tick(done: Boolean, modifier: Modifier = Modifier) {
    val ring = MaterialTheme.colorScheme.outline
    Box(
        modifier.size(24.dp).clip(CircleShape).then(if (done) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier.border(1.5.dp, ring, CircleShape)),
        contentAlignment = Alignment.Center,
    ) { if (done) Icon(Icons.Outlined.Check, contentDescription = "Done", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp)) }
}

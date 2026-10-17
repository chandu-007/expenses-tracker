package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.expenses.LoanEntryUi
import app.expenses.LoanRowUi
import app.expenses.LoansUi

/**
 * What the person has lent and borrowed. The list shows everyone with what they owe you (green) or what you owe them (red); a person's page shows the
 * history with them and the shortcuts that fill in the add form: "I gave", "I got" and "Settle up" (the whole amount, the other way).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(
    ui: LoansUi,
    onBack: () -> Unit,
    onSelect: (String?) -> Unit,
    onNew: (personId: String?, gave: Boolean, amountMinor: Long) -> Unit,
    onOpenEntry: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val selected = ui.selected
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selected?.name ?: "Lent and borrowed") },
                navigationIcon = { TextButton(onClick = { if (selected != null) onSelect(null) else onBack() }, modifier = Modifier.testTag("loans_back")) { Text("Back") } },
            )
        },
    ) { padding ->
        if (selected == null) {
            LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("loans_list"), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                item {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Total("Owed to you", ui.owedToYou, amountColor(Flow3.IN), Modifier.weight(1f).testTag("loans_owed"))
                            Total("You owe", ui.youOwe, amountColor(Flow3.OUT), Modifier.weight(1f).testTag("loans_owe"))
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onNew(null, true, 0) }, modifier = Modifier.testTag("loan_give")) { Text("I gave") }
                            OutlinedButton(onClick = { onNew(null, false, 0) }, modifier = Modifier.testTag("loan_got_btn")) { Text("I got") }
                        }
                    }
                }
                if (ui.rows.isEmpty()) item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyArt(EmptyKind.LOANS, Modifier.padding(top = 8.dp))
                        Text(
                            "Nobody yet. When you lend money, or borrow some, add it here and the app keeps track of who owes what, including part payments.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp).testTag("loans_empty"),
                        )
                    }
                }
                itemsIndexed(ui.rows, key = { _, it -> it.id }) { index, r ->
                  val pos = CardPos.of(index == 0, index == ui.rows.lastIndex)
                  Column(Modifier.animateItem().entryCardClip(pos)) {
                    Row(Modifier.fillMaxWidth().entryCardFill(pos).clickable { onSelect(r.id) }.padding(horizontal = 16.dp, vertical = 14.dp).testTag("loan_row_${r.name}"), verticalAlignment = Alignment.CenterVertically) {
                        CategoryAvatar(r.name, r.name, Modifier.padding(end = 14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Text(r.state, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("loan_state_${r.name}"))
                        }
                        if (r.minor != 0L) Text(r.amountText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = amountColor(if (r.minor > 0) Flow3.IN else Flow3.OUT), modifier = Modifier.testTag("loan_amount_${r.name}"))
                    }
                  }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("loan_detail")) {
                item {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Total(
                            when { selected.minor > 0 -> "${selected.name} owes you"; selected.minor < 0 -> "You owe ${selected.name}"; else -> "Settled up" },
                            if (selected.minor == 0L) "No balance" else selected.amountText,
                            if (selected.minor > 0) amountColor(Flow3.IN) else if (selected.minor < 0) amountColor(Flow3.OUT) else MaterialTheme.colorScheme.onSurface,
                            Modifier.fillMaxWidth().testTag("loan_balance"),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onNew(selected.id, true, 0) }, modifier = Modifier.testTag("loan_give")) { Text("I gave") }
                            OutlinedButton(onClick = { onNew(selected.id, false, 0) }, modifier = Modifier.testTag("loan_got_btn")) { Text("I got") }
                            if (selected.minor != 0L) TextButton(onClick = { onNew(selected.id, selected.minor < 0, kotlin.math.abs(selected.minor)) }, modifier = Modifier.testTag("loan_settle")) {
                                Text(if (selected.minor > 0) "${selected.name} paid it back" else "I paid it back")
                            }
                        }
                    }
                }
                itemsIndexed(ui.history, key = { _, it -> it.id }) { index, e -> HistoryRow(e, CardPos.of(index == 0, index == ui.history.lastIndex), onOpenEntry, Modifier.animateItem()) }
                if (selected.entries == 0) item {
                    TextButton(onClick = { onRemove(selected.id) }, modifier = Modifier.padding(8.dp).testTag("loan_remove")) { Text("Remove ${selected.name} from the list") }
                }
            }
        }
    }
}

@Composable
private fun Total(label: String, amount: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    // one thing for a screen reader and a test: "Owed to you ₹5,000.00"
    Column(modifier.semantics(mergeDescendants = true) {}.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.10f)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(amount, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun HistoryRow(e: LoanEntryUi, pos: CardPos, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.entryCardClip(pos)) {
        Row(Modifier.fillMaxWidth().entryCardFill(pos).clickable { onOpen(e.id) }.padding(horizontal = 16.dp, vertical = 12.dp).testTag("loan_history_${e.id}"), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (e.gave) "You gave" else "You got", style = MaterialTheme.typography.bodyLarge)
                Text(listOf(e.dateText, e.account, e.note).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(signedAmount(e.amountText, if (e.gave) Flow3.OUT else Flow3.IN), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = amountColor(if (e.gave) Flow3.OUT else Flow3.IN))
        }
    }
}

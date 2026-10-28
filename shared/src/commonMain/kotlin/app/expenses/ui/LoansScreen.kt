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
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    /** For "Got it back": the accounts the money can come into. */
    accounts: List<app.expenses.AccountUi> = emptyList(),
    onGotItBack: (entryId: String, accountId: String, amount: String, done: (String?) -> Unit) -> Unit = { _, _, _, _ -> },
    onNotExpecting: (String) -> Unit = {},
    /** "Got X back · Undo" and "Not expecting it · Undo", and the bar going away. */
    undo: app.expenses.ScreenUndo? = null,
    onUndo: (String) -> Unit = {},
    onUndoGone: (String) -> Unit = {},
) {
    val selected = ui.selected
    val undoHost = rememberUndoSnackbar(undo, onUndo, onUndoGone)
    var gettingBack by rememberSaveable { mutableStateOf<String?>(null) }
    ui.waiting.firstOrNull { it.id == gettingBack }?.let { w -> GotItBackDialog(w, accounts, onGotItBack, onClose = { gettingBack = null }) }
    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(undoHost) },
        topBar = {
            AreaTopBar(selected?.name ?: "Lent and borrowed", Area.MONEY, Icons.Outlined.SwapHoriz, { if (selected != null) onSelect(null) else onBack() }, "loans_back")
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
                if (ui.waiting.isNotEmpty()) {
                    item(key = "waiting_head") {
                        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 4.dp).testTag("waiting_section")) {
                            Text("Waiting to get back", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("waiting_title"))
                            Text("Expenses you said you expect money back for. The expense counts as spending until the money comes in.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    itemsIndexed(ui.waiting, key = { _, it -> "w" + it.id }) { index, w ->
                        val pos = CardPos.of(index == 0, index == ui.waiting.lastIndex)
                        Column(Modifier.animateItem().entryCardClip(pos)) {
                            Column(Modifier.fillMaxWidth().entryCardFill(pos).padding(horizontal = 16.dp, vertical = 12.dp).testTag("waiting_row_${w.title}")) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(w.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                        Text(listOf(w.whoText, w.dateText).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("waiting_who_${w.title}"))
                                        Text(w.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("waiting_detail_${w.title}"))
                                    }
                                    Text(w.remainingText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = amountColor(Flow3.IN), modifier = Modifier.testTag("waiting_amount_${w.title}"))
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { gettingBack = w.id }, modifier = Modifier.testTag("waiting_got_${w.title}")) { Text("Got it back") }
                                    TextButton(onClick = { onNotExpecting(w.id) }, modifier = Modifier.testTag("waiting_close_${w.title}")) { Text("Not expecting it any more") }
                                }
                            }
                        }
                    }
                    item(key = "waiting_gap") { androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 16.dp)) }
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

/**
 * Money came back for an expense: where it came into (the expense's own account to start with) and how much (what is still expected to start with, part of it is fine).
 * It is recorded as a refund of the expense, so the spending goes down by that much.
 */
@Composable
private fun GotItBackDialog(w: app.expenses.WaitingRowUi, accounts: List<app.expenses.AccountUi>, onGot: (String, String, String, (String?) -> Unit) -> Unit, onClose: () -> Unit) {
    var account by rememberSaveable(w.id) { mutableStateOf(w.accountId.ifEmpty { accounts.firstOrNull()?.id.orEmpty() }) }
    var amount by rememberSaveable(w.id) { mutableStateOf(w.remainingPlain) }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.testTag("got_dialog"),
        title = { Text("Got it back: ${w.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = amount, onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) { amount = v; problem = null } },
                    label = { Text("Amount that came back") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("got_amount"),
                )
                Text("Came into", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { a -> androidx.compose.material3.FilterChip(selected = a.id == account, onClick = { account = a.id }, label = { Text(a.name) }, modifier = Modifier.testTag("got_account_${a.name}")) }
                }
                Text("Part of it is fine: the rest stays waiting. It is recorded as a refund of the expense.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("got_problem")) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { busy = true; onGot(w.id, account, amount) { p -> busy = false; if (p == null) onClose() else problem = p } },
                enabled = account.isNotEmpty() && !busy, modifier = Modifier.testTag("got_ok"),
            ) { Text("Record") }
        },
        dismissButton = { TextButton(onClick = onClose, modifier = Modifier.testTag("got_cancel")) { Text("Cancel") } },
    )
}

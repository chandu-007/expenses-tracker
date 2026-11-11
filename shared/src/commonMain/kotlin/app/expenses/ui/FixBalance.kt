package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.expenses.AccountRowUi
import app.expenses.core.domain.BalanceFix
import app.expenses.core.domain.BalanceFixText
import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.Money
import app.expenses.core.domain.formatted
import app.expenses.core.domain.ledger.AccountType

/**
 * "Fix this balance": one amount field (what the bank app or the card statement says), the difference worked out as it is typed, and what fixing does in plain words. The difference
 * moves the account's opening balance and records no entry (see [app.expenses.core.storage.BalanceFixes]); the view model does it and the page shows its Undo. [onFixed] is called
 * once the balance is fixed and the page already shows it; the owner of the dialog closes it (and the account page that opened it) there.
 */
@Composable
internal fun FixBalanceDialog(a: AccountRowUi, onFix: (id: String, amount: String, other: Boolean, done: (String?) -> Unit) -> Unit, onClose: () -> Unit, onFixed: () -> Unit) {
    val type = AccountType.valueOf(a.type)
    val currency = BaseCurrency.current
    val money = { minor: Long -> Money(minor, currency).formatted() }
    var amount by rememberSaveable(a.id) { mutableStateOf("") }
    var other by rememberSaveable(a.id) { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val read = BalanceFix.read(amount, currency)
    val stated = (read as? BalanceFix.Input.Amount)?.let { BalanceFix.statedBalance(type, it.minor, other) }
    val plan = stated?.let { BalanceFix.plan(a.balanceMinor, it) }
    val (usual, otherLabel) = BalanceFixText.choices(type)
    val liability = type.isLiability
    val canFix = plan != null && !plan.matches && !busy
    AlertDialog(
        modifier = Modifier.padding(bottom = dialogKeyboardRoom()),
        onDismissRequest = onClose,
        title = { Text(BalanceFixText.TITLE, modifier = Modifier.testTag("fix_balance_title").semantics { heading() }) },
        text = {
            // As in Edit account: the room for the keyboard is fixed, the content scrolls inside it, and Fix balance and Cancel stay in view.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (liability) Text(a.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) // a card's sentence does not name it; the others' do
                Text(BalanceFixText.current(a.name, type, a.balanceMinor, money), style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("fix_balance_current"))
                Text(BalanceFixText.asks(type), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    amount, { amount = it; problem = null },
                    label = { Text(BalanceFixText.fieldLabel(type)) },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("fix_balance_amount"),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !other, onClick = { other = false; problem = null }, label = { Text(usual) }, modifier = Modifier.testTag(if (liability) "fix_balance_owe" else "fix_balance_has"))
                    FilterChip(selected = other, onClick = { other = true; problem = null }, label = { Text(otherLabel) }, modifier = Modifier.testTag(if (liability) "fix_balance_credit" else "fix_balance_overdrawn"))
                }
                // what typing means, as it is typed: nothing yet, a refusal in plain words, a balance that already matches, or the difference and its effect
                val refusal = (read as? BalanceFix.Input.Bad)?.message ?: if (read is BalanceFix.Input.Amount && plan == null) BalanceFix.TOO_LARGE else problem
                when {
                    refusal != null -> Text(refusal, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("fix_balance_problem"))
                    plan == null -> Text("Type the amount to see the difference.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("fix_balance_diff"))
                    plan.matches -> Text(BalanceFix.ALREADY_MATCHES, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("fix_balance_diff"))
                    else -> {
                        Text(BalanceFixText.difference(type, plan.deltaMinor, money), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("fix_balance_diff"))
                        Text(BalanceFixText.effect(a.name, type, plan.statedMinor, money), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("fix_balance_effect"))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { busy = true; onFix(a.id, amount, other) { p -> busy = false; if (p == null) onFixed() else problem = p } },
                enabled = canFix, modifier = Modifier.testTag("fix_balance_confirm"),
            ) { Text(BalanceFixText.CONFIRM) }
        },
        dismissButton = { TextButton(onClick = onClose, modifier = Modifier.testTag("fix_balance_cancel")) { Text("Cancel") } },
    )
}

/**
 * The hint on the accounts page for a credit card that shows extra money, which after a payment is the sign of spending made before the app was used: one sentence, a button that opens
 * [FixBalanceDialog], and Dismiss, which the app remembers (it does not come back for this card).
 */
@Composable
internal fun FixBalanceHint(a: AccountRowUi, onFix: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(16.dp, 8.dp, 16.dp, 0.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)).padding(start = 16.dp, top = 10.dp, end = 8.dp, bottom = 2.dp)
            .testTag("fix_balance_hint"),
    ) {
        Text(a.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(BalanceFixText.HINT, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.testTag("fix_balance_hint_text"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("fix_balance_hint_dismiss")) { Text("Dismiss") }
            TextButton(onClick = onFix, modifier = Modifier.testTag("fix_balance_hint_open")) { Text(BalanceFixText.TITLE) }
        }
    }
}

/**
 * What the Edit account dialog shows about the balance: what it is now, the button that opens [FixBalanceDialog], and the lines of the fixes made before ("Balance fixed: ... on 11 Nov 2026",
 * the newest first, at most [SHOWN] of them).
 */
@Composable
internal fun FixBalanceSection(a: AccountRowUi, onOpen: () -> Unit) {
    val money = { minor: Long -> Money(minor, BaseCurrency.current).formatted() }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Balance now", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(BalanceFixText.describe(AccountType.valueOf(a.type), a.balanceMinor, money), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("fix_balance_now"))
            }
            TextButton(onClick = onOpen, modifier = Modifier.testTag("fix_balance_open")) { Text(BalanceFixText.TITLE) }
        }
        a.fixLines.take(SHOWN).forEachIndexed { i, line -> Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("fix_balance_history_$i")) }
        if (a.fixLines.size > SHOWN) Text("${a.fixLines.size - SHOWN} earlier fixes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("fix_balance_history_more"))
    }
}

private const val SHOWN = 5

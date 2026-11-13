package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.expenses.AccountRowUi

/**
 * Every active account with its balance in one scrolling line; a card or loan shows what is owed. Tapping one narrows Home's list to it (tap again for all); the chosen one has an outline.
 * At large text (150% and up) the tiles would run off the edge or lose their balance, so the strip becomes one "Accounts" row that opens into a list ([LargeAccounts]).
 */
@Composable
fun BalancesStrip(accounts: List<AccountRowUi>, selectedId: String? = null, onSelect: (String) -> Unit, onOpenAccounts: () -> Unit = {}) {
    if (accounts.isEmpty()) return
    if (isLargeText()) { LargeAccounts(accounts, selectedId, onSelect, onOpenAccounts); return }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp).testTag("balances"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        accounts.forEach { a ->
            val chosen = a.id == selectedId
            AccountTile(a, compact = true, onClick = { onSelect(a.id) }, modifier = Modifier.width(196.dp).height(100.dp).then(if (chosen) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)) else Modifier))
        }
    }
}

/**
 * The accounts at large text: one "Accounts" row that says them all in a line (names and balances, wrapped, never cut to one word) and opens into one row for each account, each at least 48 dp
 * high, name above balance. Tapping an account narrows Home to it exactly as a tile does (the rows have the tiles' tags); "All accounts" opens the accounts page. Folded until the person opens it.
 */
@Composable
private fun LargeAccounts(accounts: List<AccountRowUi>, selectedId: String?, onSelect: (String) -> Unit, onOpenAccounts: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val summary = accounts.joinToString(" · ") { a -> a.name + " " + (if (a.owed) "owed " else "") + a.balanceText }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).testTag("balances"),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClickLabel = if (open) "Fold the accounts" else "Show each account") { open = !open }.padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp).testTag("balances_toggle"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Accounts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (!open) Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            androidx.compose.material3.Text(if (open) "▴" else "▾", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
        }
        if (open) {
            accounts.forEach { a ->
                val chosen = a.id == selectedId
                val label = privately(a.name + (if (a.owed) " · owed" else "") + " " + a.balanceText)
                Column(
                    // the tag comes before clearAndSetSemantics: what follows it is cleared (the tile's own order)
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onSelect(a.id) }.testTag("balance_chip_${a.name}").padding(horizontal = 16.dp, vertical = 8.dp)
                        .then(if (chosen) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
                        .clearAndSetSemantics { text = AnnotatedString(label); this.selected = chosen },
                ) {
                    Text(a.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                    Text((if (a.owed) "owed " else "") + a.balanceText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
            }
            TextButton(onClick = onOpenAccounts, modifier = Modifier.heightIn(min = 48.dp).padding(start = 4.dp).testTag("balances_manage")) { Text("All accounts") }
        }
    }
}

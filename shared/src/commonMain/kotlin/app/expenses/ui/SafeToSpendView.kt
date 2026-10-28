package app.expenses.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.expenses.SafeToSpendUi
import app.expenses.core.domain.SafeToSpendText

/**
 * The one line for the balance-based safe to spend: what can be spent until the end of the period after what the cards owe and what is due. Tapping it opens
 * the breakdown ([SafeToSpendDialog]). [tag] is its test tag (the accounts page and Home each have one). [compact] takes less room above and below, for the accounts page,
 * where the cards under it must stay in view.
 */
@Composable
fun SafeToSpendLine(ui: SafeToSpendUi, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    Column(modifier.fillMaxWidth().clickable(onClick = onClick).semantics(mergeDescendants = true) { }.padding(horizontal = 16.dp, vertical = if (compact) 4.dp else 8.dp).testTag(tag)) {
        Text(
            ui.headline, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            color = if (ui.nothingSpare) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag(tag + "_text"),
        )
        Text(
            listOfNotNull(ui.perDay, "after card bills and what is due · tap for details").joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The breakdown of the safe-to-spend figure: each line of the sum, the horizon, what is not counted, that it is an estimate, and the switch for showing it on Home. */
@Composable
fun SafeToSpendDialog(ui: SafeToSpendUi, onClose: () -> Unit, onShowOnHome: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.testTag("safe_to_spend_dialog"),
        title = { Text(ui.headline, modifier = Modifier.testTag("safe_to_spend_headline")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Looking ahead to ${ui.horizonWhy}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("safe_to_spend_horizon"))
                ui.rows.forEach { r ->
                    Row(Modifier.fillMaxWidth().testTag("safe_to_spend_row_${r.part.name}"), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(r.label, style = MaterialTheme.typography.bodyMedium)
                            Text(r.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(r.amountText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp).testTag("safe_to_spend_amount_${r.part.name}"))
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (ui.nothingSpare) "Nothing spare" else "Safe to spend", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(ui.spendableText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("safe_to_spend_total"))
                }
                ui.perDay?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("safe_to_spend_per_day")) }
                ui.shortfall?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("safe_to_spend_short")) }
                ui.setAsideHint?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("safe_to_spend_set_aside_hint")) }
                ui.notCounted?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("safe_to_spend_not_counted")) }
                Text(SafeToSpendText.EXPLAIN, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("safe_to_spend_explain"))
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Show this under the budget on Home", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 12.dp))
                    Switch(checked = ui.homeVisible, onCheckedChange = onShowOnHome, modifier = Modifier.testTag("safe_to_spend_home_switch"))
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose, modifier = Modifier.testTag("safe_to_spend_close")) { Text("Close") } },
    )
}

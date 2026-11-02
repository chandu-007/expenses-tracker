package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.expenses.SkippedSenderUi
import app.expenses.core.parsers.TrustedSenders

/** "1 text skipped", "3 texts skipped". */
internal fun skippedCountText(n: Long): String = if (n == 1L) "1 text skipped" else "$n texts skipped"

/**
 * The block under the trusted-senders editor: owns the "Added NAME" notice (the row it came from is gone by then) and hands [rows] to [SkippedSendersList]. [stored] is the saved
 * trusted-senders value, so the notice is worded exactly as the editor's own (`TrustedSenders.add`); [onTrust] saves the name and refreshes the list.
 */
@Composable
internal fun SkippedSendersSection(stored: String, rows: List<SkippedSenderUi>, onTrust: (String) -> Unit) {
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    SkippedSendersList(rows, notice) { root -> notice = senderAddMessage(TrustedSenders.add(stored, root)); onTrust(root) }
}

/**
 * "Senders we skipped": the sender NAMES (headers such as ICICIT) the app skipped because they are not on the list of banks, each with how many of its texts were skipped and when the
 * last one came, and one button that trusts the name from then on. Only names that may be a bank are shown (see `SkippedSenders`). No word of any text is known here. Shown only when there
 * is a row or a [notice] to read. State-hoisted: nothing here is saved.
 */
@Composable
internal fun SkippedSendersList(rows: List<SkippedSenderUi>, notice: String?, onTrust: (String) -> Unit) {
    if (rows.isEmpty() && notice == null) return
    Column(Modifier.fillMaxWidth().testTag("skipped_senders"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Senders we skipped", style = MaterialTheme.typography.titleMedium)
        Text(
            "Banks send alerts from names like ICICIT. If one of these is your bank, tap Treat as my bank; only new texts from it will be read. We never read the texts of other senders.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("skipped_explain"),
        )
        notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("skipped_added_notice")) }
        rows.forEach { r ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(r.root, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("skipped_sender_${r.root}"))
                    Text(skippedCountText(r.count), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("skipped_count_${r.root}"))
                    Text("Last one: ${dateTimeLabel(r.lastAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("skipped_last_${r.root}"))
                    OutlinedButton(onClick = { onTrust(r.root) }, modifier = Modifier.heightIn(min = 48.dp).testTag("skipped_trust_${r.root}")) { Text("Treat ${r.root} as my bank") }
                }
            }
        }
    }
}

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

/** What the hint line says about the direction of the skipped texts. */
internal fun skippedHintText(moneyOut: Boolean): String = if (moneyOut) "Looks like a bank text about money out. Masked outline of the wording:" else "Looks like a bank text about money in. Masked outline of the wording:"

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
 * "Texts we skipped from unknown senders": each sender whose payment-looking texts were skipped because its name is not on the list, with how many, a short masked outline of the
 * wording and one button that trusts the name from then on. Shown only when there is a row or a [notice] to read. State-hoisted: nothing here is saved.
 */
@Composable
internal fun SkippedSendersList(rows: List<SkippedSenderUi>, notice: String?, onTrust: (String) -> Unit) {
    if (rows.isEmpty() && notice == null) return
    Column(Modifier.fillMaxWidth().testTag("skipped_senders"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Texts we skipped from unknown senders", style = MaterialTheme.typography.titleMedium)
        Text(
            "These came from names that are not on the list of banks, so they were not read as transactions. Only senders whose texts look like a payment are shown, and only a masked outline of the wording, never the message.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("skipped_added_notice")) }
        rows.forEach { r ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(r.root, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("skipped_sender_${r.root}"))
                    Text(skippedCountText(r.count), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("skipped_count_${r.root}"))
                    Text(skippedHintText(r.moneyOut), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("skipped_hint_${r.root}"))
                    Text(r.outline, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("skipped_shape_${r.root}"))
                    OutlinedButton(onClick = { onTrust(r.root) }, modifier = Modifier.heightIn(min = 48.dp).testTag("skipped_trust_${r.root}")) { Text("Treat ${r.root} as my bank") }
                }
            }
        }
        if (rows.isNotEmpty()) Text(
            "The skipped texts were not kept, so only new texts from a name you trust will be read.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("skipped_not_kept"),
        )
    }
}

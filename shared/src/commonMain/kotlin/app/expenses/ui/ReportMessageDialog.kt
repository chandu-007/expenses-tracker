package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.expenses.core.domain.FeedbackMail
import app.expenses.core.parsers.MessageRedactor
import app.expenses.core.parsers.MessageReport

/** What an item already in To review was read as, for the report: nothing is classified again, and its sender is the bank's name, not a header. */
class ReadAs(val senderText: String, val verdict: String)

/**
 * "Share this message for a fix": the person pastes (or is shown) a message the app did not understand, sees **exactly** the redacted text that would be shared (account and phone
 * numbers, UPI names, links' paths, people's names and, unless asked to keep them, amounts are taken out on this phone: [MessageRedactor]), and decides to copy it or open their mail app
 * with it filled in. Nothing leaves the phone from here and nothing is stored: what is typed is forgotten when the window closes.
 *
 * [initialSender] is the header a text came from (empty when unknown), [initialBody] the words when the app has them (a message in To review); for a message that was ignored the app kept only
 * a count, so the person pastes it. [read] says how an item in To review was read; null classifies the pasted text with [extraRoots] as the capture rules would.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportMessageDialog(initialSender: String, initialBody: String, extraRoots: Set<String>, read: ReadAs? = null, onClose: () -> Unit) {
    val platform = LocalPlatform.current
    var sender by remember { mutableStateOf(initialSender) }
    var body by remember { mutableStateOf(initialBody) }
    var keepAmounts by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val version = platform.appVersionText.ifEmpty { "unknown" }
    val system = platform.osVersionText.ifEmpty { "unknown" }
    val report = if (body.isBlank()) "" else if (read != null) MessageReport.compose(read.senderText, read.verdict, body, keepAmounts, version, system)
    else MessageReport.build(sender.trim().ifEmpty { null }, body, keepAmounts, version, system, extraRoots)
    AlertDialog(
        modifier = Modifier.imePadding(),
        onDismissRequest = onClose,
        title = { Text("Share this message for a fix") },
        text = {
            ResizeDialogForKeyboard()
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Nothing is sent from here. Below is exactly what would be shared, with account and phone numbers, UPI names, people's names and link details already taken out on this phone. You copy it or open your mail app yourself.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (read == null) OutlinedTextField(sender, { sender = it; note = null }, label = { Text("Who sent it (the name shown in your messages)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("report_sender"))
                if (initialBody.isEmpty()) {
                    OutlinedTextField(body, { body = it; note = null }, label = { Text("The message") }, minLines = 3, modifier = Modifier.fillMaxWidth().testTag("report_body"))
                    if (platform.supportsShortcutsCapture) OutlinedButton(onClick = { platform.readClipboardText()?.let { body = it; note = null } }, modifier = Modifier.testTag("report_paste")) { Text("Paste") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Keep the amounts")
                        Text("Off: amounts show only their shape (Rs.N,NNN.NN). Turn on if the amounts' format matters. Only the amount of the payment stays; balances, limits and dues are always hidden.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = keepAmounts, onCheckedChange = { keepAmounts = it; note = null }, modifier = Modifier.testTag("report_keep_amounts"))
                }
                if (report.isNotEmpty()) {
                    Text("What would be shared", style = MaterialTheme.typography.labelMedium)
                    // Material's Text, not the masking one: this box must show the words that will be copied, kept amounts included
                    androidx.compose.material3.Text(
                        report, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(12.dp).testTag("report_preview"),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { note = if (platform.copyText(report)) "Copied. Paste it where you want to send it." else "Copying is not available here: use Send by email." }, modifier = Modifier.testTag("report_copy")) { Text("Copy") }
                        OutlinedButton(
                            onClick = { note = if (platform.openExternalUrl(FeedbackMail.buildReport(version, system, report))) null else FEEDBACK_NO_MAIL_APP },
                            modifier = Modifier.testTag("report_mail"),
                        ) { Text("Send by email") }
                    }
                    note?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("report_note")) }
                } else Text("Paste the message to see what would be shared.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("report_empty"))
            }
        },
        confirmButton = { TextButton(onClick = onClose, modifier = Modifier.testTag("report_close")) { Text("Close") } },
    )
}

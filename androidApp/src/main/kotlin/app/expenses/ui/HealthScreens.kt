package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.expenses.core.domain.CaptureHealthReport
import app.expenses.core.domain.CaptureStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun whenText(ms: Long?): String = ms?.let { DateTimeFormatter.ofPattern("d MMM, h:mm a").format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) } ?: "never"

/** What capture has done in the last 30 days, as counts, and what to try when it seems silent. Never shows the words of a text. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureHealthScreen(report: CaptureHealthReport?, onAddSender: (String) -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("How capture is doing") }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("health_back")) { Text("Back") } }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (report == null) { Text("Loading…"); return@Column }
            val (headline, advice) = when (report.status) {
                CaptureStatus.OFF -> "Capture is off" to "Turn on Read bank texts in Capture from texts to find transactions in your bank messages."
                CaptureStatus.PERMISSION_MISSING -> "Android has not allowed texts" to "Capture is switched on, but Android has not given this app permission to receive texts, so none can arrive. Turn the switch off and on again to be asked, or allow it in the phone's app settings."
                CaptureStatus.NOTHING_SEEN -> "No texts seen yet" to "Capture is on and allowed, but no text has arrived in the last 30 days. If your bank has sent you texts, check that the phone is not stopping this app in the background (battery saver) and that you have not force-stopped it: Android does not deliver texts to an app that was force-stopped until you open it."
                CaptureStatus.QUIET -> "Quiet for a week or more" to "Texts were seen before, but none in the last 7 days. That is normal if you have not used your bank; if you have, check battery saver and that the app was not force-stopped."
                CaptureStatus.WORKING -> "Capture is working" to "Texts are arriving and being read on this phone."
            }
            Text(headline, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("health_headline"))
            Text(advice, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("health_advice"))
            HorizontalDivider()
            Text("Last 30 days", style = MaterialTheme.typography.labelMedium)
            listOf("Found as transactions" to report.captured, "Same text seen again" to report.duplicates, "One-time passwords ignored" to report.otps, "Texts from people ignored" to report.personal,
                "Bank notices ignored (not a payment)" to report.notices, "Declined payments ignored" to report.declined).forEach { (label, n) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(n.toString(), modifier = Modifier.testTag("health_count_$label")) }
            }
            Text("Last transaction found: ${whenText(report.lastCapturedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Last text of any kind seen: ${whenText(report.lastSeenAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (report.senders.isNotEmpty()) {
                HorizontalDivider(); Text("Banks", style = MaterialTheme.typography.labelMedium)
                report.senders.forEach { s -> Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("health_sender_${s.root}"), horizontalArrangement = Arrangement.SpaceBetween) { Text(s.name ?: s.root); Text("${s.captured} found, ${s.ignored} ignored") } }
            }
            if (report.unknownSenders.isNotEmpty()) {
                HorizontalDivider()
                Text("Senders not on the list", style = MaterialTheme.typography.labelMedium)
                Text("Texts from these names were not read. If one is your bank, add it and its transactions will be found from the next text on. Only the sender name is shown here, never the message.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                report.unknownSenders.take(12).forEach { s ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${s.root} (${s.ignored})")
                        OutlinedButton(onClick = { onAddSender(s.root) }, modifier = Modifier.testTag("health_add_${s.root}")) { Text("This is my bank") }
                    }
                }
            }
        }
    }
}

/** One thing the app is allowed to do on this phone. [granted] is read from Android now, not assumed. */
data class PermissionRow(val name: String, val granted: Boolean, val why: String)

/** What this app can see on the phone, what it does with it, and what leaves the phone (read from the app's own declared permissions). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(permissions: List<PermissionRow>, canUseInternet: Boolean, onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Privacy: what this app can see") }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("privacy_back")) { Text("Back") } }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (canUseInternet) "This app is allowed to use the internet." else "This app has no permission to use the internet.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("privacy_internet"))
            Text(if (canUseInternet) "Check what it sends in the phone's settings." else "Nothing you enter or capture can leave this phone through this app: your entries, texts and amounts stay here. Backups and exports are files you choose to save or share yourself.", style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
            Text("Permissions it asks for", style = MaterialTheme.typography.labelMedium)
            permissions.forEach { p ->
                Column(Modifier.fillMaxWidth().testTag("privacy_${p.name}"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(p.name, style = MaterialTheme.typography.bodyLarge); Text(if (p.granted) "Allowed" else "Not allowed", modifier = Modifier.testTag("privacy_state_${p.name}")) }
                    Text(p.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (permissions.isEmpty()) Text("It asks for none.")
            HorizontalDivider()
            Text("You can take any permission back in the phone's settings; the app keeps working without it, except the feature that needs it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

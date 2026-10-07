package app.expenses.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.expenses.Notifier
import app.expenses.core.domain.NotificationPrefs

private fun hourLabel(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

/** Moves a time of day by whole hours, wrapping around midnight. */
private fun shiftHours(minute: Int, hours: Int) = Math.floorMod(minute + hours * 60, NotificationPrefs.MINUTES_PER_DAY)

/** [onShift] receives -1 or +1 hours; the change is applied to the stored time, not the one shown, so quick taps are never lost. */
@Composable
private fun TimeRow(label: String, minute: Int, tag: String, enabled: Boolean, onShift: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { onShift(-1) }, enabled = enabled, modifier = Modifier.testTag("${tag}_down").semantics { contentDescription = "$label, one hour earlier" }) { Text("−") }
        Text(hourLabel(minute), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag(tag))
        TextButton(onClick = { onShift(1) }, enabled = enabled, modifier = Modifier.testTag("${tag}_up").semantics { contentDescription = "$label, one hour later" }) { Text("+") }
    }
}

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(prefs: NotificationPrefs, onChange: ((NotificationPrefs) -> NotificationPrefs) -> Unit, onSendTest: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(Notifier.canShow(context)) }
    // The user may change this in system settings and come back, so look again every time the screen is resumed.
    LifecycleResumeEffect(Unit) { allowed = Notifier.canShow(context); onPauseOrDispose { } }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = Notifier.canShow(context) }

    Scaffold(topBar = { TopAppBar(title = { Text("Notifications") }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("notif_back")) { Text("Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!allowed) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("notif_blocked")) {
                    Text("Notifications are turned off for this app, so nothing below will reach you.", color = MaterialTheme.colorScheme.error)
                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 33) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        },
                        modifier = Modifier.testTag("notif_allow"),
                    ) { Text("Allow notifications") }
                }
            }
            SwitchRow("Bill reminders", "Once, on the day a repeating expense is due and again if it is overdue", prefs.bills, "notif_bills") { on -> onChange { p -> p.copy(bills = on) } }
            TimeRow("Remind me from", prefs.billHour * 60, "notif_bill_hour", prefs.bills) { d -> onChange { p -> p.copy(billHour = Math.floorMod(p.billHour + d, 24)) } }
            HorizontalDivider()
            SwitchRow("Budget alerts", "Once when a budget reaches 80% of its limit, and once when it goes over", prefs.budgets, "notif_budgets") { on -> onChange { p -> p.copy(budgets = on) } }
            HorizontalDivider()
            SwitchRow("Quiet hours", "Nothing is shown in this window; it arrives when it ends", prefs.quietEnabled, "notif_quiet") { on -> onChange { p -> p.copy(quietEnabled = on) } }
            TimeRow("From", prefs.quietStartMinute, "notif_quiet_start", prefs.quietEnabled) { d -> onChange { p -> p.copy(quietStartMinute = shiftHours(p.quietStartMinute, d)) } }
            TimeRow("Until", prefs.quietEndMinute, "notif_quiet_end", prefs.quietEnabled) { d -> onChange { p -> p.copy(quietEndMinute = shiftHours(p.quietEndMinute, d)) } }
            HorizontalDivider()
            Text("This app only sends the two kinds above. It never sends promotions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "The app checks about once an hour in the background, so an alert can arrive up to an hour late. Your phone's battery saver can delay it further.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onSendTest, modifier = Modifier.testTag("notif_test")) { Text("Send a test notification") }
        }
    }
}

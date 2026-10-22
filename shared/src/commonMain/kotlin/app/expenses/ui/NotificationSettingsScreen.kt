package app.expenses.ui

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.expenses.core.domain.NotificationPrefs

private fun hourLabel(minute: Int) = "${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}"

/** Moves a time of day by whole hours, wrapping around midnight. */
private fun shiftHours(minute: Int, hours: Int) = (minute + hours * 60).mod(NotificationPrefs.MINUTES_PER_DAY)

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
private fun SwitchRow(title: String, detail: String, checked: Boolean, tag: String, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) { // room before the switch, so a long line at a large text size never runs into it
            Text(title, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, modifier = Modifier.testTag(tag))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(prefs: NotificationPrefs, onChange: ((NotificationPrefs) -> NotificationPrefs) -> Unit, onSendTest: () -> Unit, onBack: () -> Unit) {
    val platform = LocalPlatform.current
    var allowed by remember { mutableStateOf(platform.notificationsAllowed()) }
    // The user may change this in system settings and come back, so look again every time the screen is resumed.
    LifecycleResumeEffect(Unit) { allowed = platform.notificationsAllowed(); onPauseOrDispose { } }
    val askPermission = rememberNotificationPermissionRequest { allowed = platform.notificationsAllowed() }

    Scaffold(topBar = { AreaTopBar("Notifications", Area.CAPTURE, Icons.Outlined.Notifications, onBack, "notif_back") }) { padding ->
        if (!platform.supportsNotifications) {
            Text(NOT_ON_IPHONE, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(padding).padding(16.dp).testTag("notif_unavailable"))
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!allowed) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("notif_blocked")) {
                    Text("Notifications are turned off for this app, so nothing below will reach you.", color = MaterialTheme.colorScheme.error)
                    OutlinedButton(
                        onClick = askPermission,
                        modifier = Modifier.testTag("notif_allow"),
                    ) { Text("Allow notifications") }
                }
            }
            SwitchRow("Bill reminders", "On the day a bill or repeating expense is due, then once a day for a week while it is overdue", prefs.bills, "notif_bills") { on -> onChange { p -> p.copy(bills = on) } }
            TimeRow("Remind me from", prefs.billHour * 60, "notif_bill_hour", prefs.bills) { d -> onChange { p -> p.copy(billHour = (p.billHour + d).mod(24)) } }
            SwitchRow("3 days before a bill is due", "A heads-up for a bill you are tracking", prefs.billAhead3, "notif_ahead3", enabled = prefs.bills) { on -> onChange { p -> p.copy(billAhead3 = on) } }
            SwitchRow("The day before", "One more reminder the day before", prefs.billAhead1, "notif_ahead1", enabled = prefs.bills) { on -> onChange { p -> p.copy(billAhead1 = on) } }
            HorizontalDivider()
            Text("Credit cards", style = MaterialTheme.typography.titleSmall)
            SwitchRow("When a statement is made", "What is owed, the minimum and the due date (set billing dates on the card under Accounts)", prefs.cardStatement, "notif_card_statement", enabled = prefs.bills) { on -> onChange { p -> p.copy(cardStatement = on) } }
            SwitchRow("Near the credit limit", "At 80% and at 100% of the limit you enter on the card. Also says when a bill has been paid in full", prefs.cardLimit, "notif_card_limit", enabled = prefs.bills) { on -> onChange { p -> p.copy(cardLimit = on) } }
            HorizontalDivider()
            SwitchRow("Budget alerts", "When a budget reaches the level below, and once when it goes over", prefs.budgets, "notif_budgets") { on -> onChange { p -> p.copy(budgets = on) } }
            Text("Warn me at", color = if (prefs.budgets) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(50, 70, 80, 90).forEach { percent ->
                    FilterChip(selected = prefs.budgetNearPercent == percent, onClick = { onChange { p -> p.copy(budgetNearPercent = percent) } }, enabled = prefs.budgets, label = { Text("$percent%") }, modifier = Modifier.testTag("notif_near_$percent"))
                }
            }
            SwitchRow("Spending ahead of the month", "Before the limit is near: for example 60% used with 30% of the month gone", prefs.budgetPace, "notif_pace", enabled = prefs.budgets) { on -> onChange { p -> p.copy(budgetPace = on) } }
            SwitchRow("Month-end note", "In the first days of a month: how last month went, if you stayed under the overall budget", prefs.budgetWrapUp, "notif_wrapup", enabled = prefs.budgets) { on -> onChange { p -> p.copy(budgetWrapUp = on) } }
            HorizontalDivider()
            SwitchRow("Weekly recap", "Once a week, early in the week, from the hour above: how much you spent last week, against the week before, and where most of it went. Off until you turn it on", prefs.recap, "notif_recap") { on -> onChange { p -> p.copy(recap = on) } }
            HorizontalDivider()
            SwitchRow("Quiet hours", "Nothing is shown in this window; it arrives when it ends", prefs.quietEnabled, "notif_quiet") { on -> onChange { p -> p.copy(quietEnabled = on) } }
            TimeRow("From", prefs.quietStartMinute, "notif_quiet_start", prefs.quietEnabled) { d -> onChange { p -> p.copy(quietStartMinute = shiftHours(p.quietStartMinute, d)) } }
            TimeRow("Until", prefs.quietEndMinute, "notif_quiet_end", prefs.quietEnabled) { d -> onChange { p -> p.copy(quietEndMinute = shiftHours(p.quietEndMinute, d)) } }
            HorizontalDivider()
            Text("This app only sends the kinds above (and card due dates with the bill reminders). It never sends promotions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                platform.notificationTimingNote ?: "The app checks about once an hour in the background, so an alert can arrive up to an hour late. Your phone's battery saver can delay it further.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onSendTest, modifier = Modifier.testTag("notif_test")) { Text("Send a test notification") }
        }
    }
}

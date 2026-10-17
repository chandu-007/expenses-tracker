package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.expenses.core.domain.LockSettings
import app.expenses.core.domain.LockTimeout

/** Shown instead of everything else while the app is locked. Nothing about the ledger is drawn here. */
@Composable
fun LockedScreen(message: String?, onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp).testTag("locked_screen"), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Locked", style = MaterialTheme.typography.headlineMedium)
            Text("Your expenses are hidden until you unlock.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            message?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.testTag("lock_message")) }
            Button(onClick = onUnlock, modifier = Modifier.testTag("unlock")) { Text("Unlock") }
        }
    }
}

private val TIMEOUT_LABELS = listOf(
    LockTimeout.IMMEDIATELY to "Every time I leave",
    LockTimeout.ONE_MINUTE to "After 1 minute",
    LockTimeout.FIVE_MINUTES to "After 5 minutes",
    LockTimeout.FIFTEEN_MINUTES to "After 15 minutes",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    settings: LockSettings,
    availability: () -> LockAvailability,
    message: String?,
    onEnable: (LockTimeout) -> Unit,
    onDisable: () -> Unit,
    onTimeout: (LockTimeout) -> Unit,
    onBack: () -> Unit,
) {
    val openSecuritySettings = rememberOpenSecuritySettings()
    val platform = LocalPlatform.current
    var available by remember { mutableStateOf(availability()) }
    // The user may set a screen lock in system settings and come back, so look again every time this screen is resumed.
    LifecycleResumeEffect(Unit) { available = availability(); onPauseOrDispose { } }
    var chosen by rememberSaveable { mutableStateOf(LockTimeout.ONE_MINUTE) }
    val shown = if (settings.enabled) settings.timeout else chosen

    Scaffold(topBar = { TopAppBar(title = { Text("App lock") }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("security_back")) { Text("Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!platform.supportsAppLock) Text(NOT_ON_IPHONE, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("lock_unavailable"))
            else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Lock the app")
                    Text("Ask for your fingerprint, face or phone PIN when the app is opened.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { on -> if (on) onEnable(chosen) else onDisable() },
                    enabled = settings.enabled || available == LockAvailability.READY,
                    modifier = Modifier.testTag("lock_switch"),
                )
            }
            if (platform.supportsAppLock && !settings.enabled && available != LockAvailability.READY) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("lock_needs_screen_lock")) {
                    Text(
                        if (available == LockAvailability.NO_SCREEN_LOCK) "Your phone has no screen lock yet. Set a PIN, pattern, password or fingerprint in its settings; the app lock uses it."
                        else "This phone cannot ask for a fingerprint or PIN right now.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (available == LockAvailability.NO_SCREEN_LOCK) {
                        OutlinedButton(onClick = { openSecuritySettings() }, modifier = Modifier.testTag("open_system_security")) { Text("Open phone settings") }
                    }
                }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("security_message")) }
            Text("Lock again", style = MaterialTheme.typography.labelMedium)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TIMEOUT_LABELS.forEach { (t, label) ->
                    FilterChip(
                        selected = shown == t,
                        onClick = { chosen = t; if (settings.enabled) onTimeout(t) },
                        label = { Text(label) }, modifier = Modifier.testTag("lock_timeout_${t.name}"),
                    )
                }
            }
            Text(
                "While locked the screens are blank, and this app is hidden from the recent-apps view and screenshots. " +
                    "The home-screen widget, the quick-settings tile and notifications are not behind the lock: they only add an expense or show a short alert. " +
                    "Your data is always encrypted on the phone, whether or not the lock is on.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

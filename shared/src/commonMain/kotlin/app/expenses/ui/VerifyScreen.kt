package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.expenses.core.domain.PermissionClaims

/**
 * "Verify it yourself": the app's privacy claims as things a person can check on the phone. On Android the first section is computed from the installed app's own manifest
 * ([app.expenses.Platform.declaredPermissions]), so a permission the build declares can never be listed as one it does not have; on iPhone, which has no such list, it states the
 * app's own facts and points to the App Privacy Report. [asked] is the app's own list of what it asks for and why (the Privacy screen's rows).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyScreen(asked: List<PermissionRow>, onBack: () -> Unit) {
    val platform = LocalPlatform.current
    val declared = remember { platform.declaredPermissions() }
    Scaffold(topBar = { ScreenHeader("Verify it yourself", onBack, "verify_back") }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("verify_screen"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Do not take our word for it. Each check below is one you can make on this phone, in a minute.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                VerifyCard("Permissions we do not have", Modifier.testTag("verify_not_have")) {
                    if (declared != null) {
                        val claims = PermissionClaims.of(declared)
                        Text(PermissionClaims.summary(claims), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("verify_summary"))
                        claims.notRequested.forEach { c -> NotAskedRow(c) }
                        Text("This list is read from the app installed on this phone just now, not written in advance. Android shows the same under Settings, Apps, Expenses, Permissions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("There is no account and no sign-in.", style = MaterialTheme.typography.bodyMedium)
                        Text("Your entries are kept in the app's own storage on this iPhone, and the app does not send them anywhere.", style = MaterialTheme.typography.bodyMedium)
                        Text("iPhone keeps a record of what each app did. To read it, open Settings, then Privacy & Security, then App Privacy Report. Turn it on, use the app for a few days, then look under App Network Activity.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("verify_privacy_report"))
                    }
                }

                VerifyCard("Permissions we do ask for, and why", Modifier.testTag("verify_ask")) {
                    if (asked.isEmpty()) Text("It asks for none.", style = MaterialTheme.typography.bodyMedium)
                    asked.forEach { p ->
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(p.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                VerifyCard("Check it yourself with airplane mode", Modifier.testTag("verify_airplane")) {
                    Text("1. Turn on airplane mode in your phone's quick settings.", style = MaterialTheme.typography.bodyMedium)
                    Text("2. Open this app. Add an entry, look at Reports, save a backup file.", style = MaterialTheme.typography.bodyMedium)
                    Text("3. Everything works as it did. Nothing in this app waits for a connection, because it does not use one.", style = MaterialTheme.typography.bodyMedium)
                }

                VerifyCard("Free to use", Modifier.testTag("verify_free")) {
                    Text("Expenses is free. There are no ads, no subscriptions and no in-app purchases, and you do not need an account.", style = MaterialTheme.typography.bodyMedium)
                }

                VerifyCard("One more thing", Modifier.testTag("verify_fraud")) {
                    Text("We never ask for your OTP, PIN or password. Neither does your bank by text or call.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("If someone does, stop and call your bank on the number printed on your card.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** A titled card; the title is a heading for screen readers. */
@Composable
private fun VerifyCard(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().standardCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        content()
    }
}

/** One kind of access the app does not ask for: a tick and its name, read out as "Contacts: not requested". */
@Composable
private fun NotAskedRow(c: PermissionClaims.Claim) {
    Row(Modifier.fillMaxWidth().testTag("verify_not_${c.id}").clearAndSetSemantics { contentDescription = "${c.label}: not requested" }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(c.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

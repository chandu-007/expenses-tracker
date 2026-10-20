package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.expenses.CaptureSettingsUi
import app.expenses.PasteOutcome

/** Where the words come from on a platform with no access to texts: the steps of the Shortcuts automation are [SHORTCUTS_STEPS]. Shown only where `Platform.supportsShortcutsCapture` is true. */
internal val SHORTCUTS_STEPS = listOf(
    "Open the Shortcuts app (the button below does it), tap + to make a new shortcut, and name it Capture bank message.",
    "Tap Add Action, search for Expenses, and choose Capture bank message.",
    "In the action, tap Message and choose Shortcut Input (its Content). Optionally tap Sender and choose Shortcut Input, then Sender.",
    "Tap Edit, then Automation (on iOS 27 an automation belongs to a shortcut). Choose the Message trigger.",
    "Under Message Contains, type words your bank's texts always have, for example: debited. Leave Sender empty. Add a second shortcut for credited if you want those too.",
    "To make it run without asking: open the shortcut's Info (i) button, tap Privacy, and turn on Allow Running When Locked. If the action asks to be allowed, choose Always Allow.",
    "Tap Done. From now on a text containing those words is handed to this app, which reads it on this phone.",
)

/**
 * "Capture from texts" on a phone where the system gives no access to texts (iPhone): what is possible instead (a Shortcuts automation that hands each bank
 * text to the app), the steps to set it up, how to test it, what happens to the words, and the limits stated plainly. The sender-names, rules and health
 * entries of the Android screen are kept because they work the same.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShortcutsCaptureScreen(settings: CaptureSettingsUi, onHeaders: (String) -> Unit, onOpenRules: () -> Unit, onOpenHealth: () -> Unit, onBack: () -> Unit) {
    val platform = LocalPlatform.current
    var headers by rememberSaveable(settings.extraHeaders) { mutableStateOf(settings.extraHeaders) }
    Scaffold(topBar = { AreaTopBar("Capture from texts", Area.CAPTURE, Icons.Outlined.Sms, onBack, "capture_back") }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Bank texts on iPhone", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("shortcuts_title"))
            Text("iPhone does not let any app read your text messages. What it does allow is an automation in the Shortcuts app that runs when a message arrives and hands that message to this app. Set it up once and bank texts land in To review by themselves, with nothing added to your ledger until you say so.", style = MaterialTheme.typography.bodyMedium)
            Text("Set it up", style = MaterialTheme.typography.labelMedium)
            Column(Modifier.testTag("shortcuts_steps"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SHORTCUTS_STEPS.forEachIndexed { i, step -> Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("shortcuts_step_${i + 1}")) }
            }
            Button(onClick = { platform.openExternalUrl("shortcuts://") }, modifier = Modifier.testTag("shortcuts_open")) { Text("Open Shortcuts") }
            Text("Add expense and Capture bank message also appear in Shortcuts and in Siri without any setup, and Add expense can be put on the Action button.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text("Send a test message", style = MaterialTheme.typography.labelMedium)
            Text("Have someone send you (or send yourself from another phone) a text such as: Rs.250.00 debited from a/c XX1234 on 18-10-26 at TEST SHOP. Ref 123456789012. -HDFC Bank. A minute later it should be in To review. If you filled in Sender, a text from a phone number counts as a text from a person and is ignored, which \"How capture is doing\" shows as a count: leave Sender empty to test with a friend's phone. You can also copy any bank message and press Paste a bank message in To review.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("shortcuts_test"))
            HorizontalDivider()
            Text("What happens to the words", style = MaterialTheme.typography.labelMedium)
            Text("The words of a text that is not a bank transaction (a person's message, a one-time password, an offer) are thrown away as soon as they are looked at: only a count is kept, shown in How capture is doing. The words of a transaction or a bill are kept until you add or skip it; after that only the amount, date and reference stay. Everything is read on this phone and nothing is sent anywhere.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("shortcuts_privacy"))
            HorizontalDivider()
            Text("What iPhone does not allow", style = MaterialTheme.typography.labelMedium)
            Text("This depends on iOS, not on this app: it may show a banner when the automation runs, even with Run Immediately. It works only for messages in the Messages app (SMS, iMessage, RCS), not for WhatsApp or other apps. Messages from unknown senders, or ones iOS files under Unknown Senders or Junk, may not start the automation at all, and a bank's alphabetic sender is often treated that way. It cannot run before you have unlocked the phone once after a restart. If a transaction is missing, copy its text and use Paste a bank message in To review.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("shortcuts_limits"))
            HorizontalDivider()
            Text("Your bank's sender name", style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(headers, { headers = it }, label = { Text("Extra sender names (one per line)") }, modifier = Modifier.fillMaxWidth().testTag("capture_headers"))
            Text("Used when the automation passes Sender. Only senders on the built-in list of banks and billers are read. If yours is missing, add the name that appears after the first dash, for example HDFCBK from AX-HDFCBK-S.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { onHeaders(headers) }, modifier = Modifier.testTag("capture_headers_save")) { Text("Save names") }
            HorizontalDivider()
            OutlinedButton(onClick = onOpenRules, modifier = Modifier.testTag("open_rules")) { Text("Category rules") }
            OutlinedButton(onClick = onOpenHealth, modifier = Modifier.testTag("open_health")) { Text("How capture is doing") }
        }
    }
}

/** What the paste box shows, kept above the list so it survives the list changing from "nothing to review" to "1 to review". */
internal class PasteState {
    var result by mutableStateOf<PasteOutcome?>(null)
    var typing by mutableStateOf(false)
    var typed by mutableStateOf("")
}

@Composable
internal fun rememberPasteState(): PasteState = remember { PasteState() }

/**
 * To review, iPhone: reads the clipboard when the button is pressed (the only time it is read; iOS may ask the person to allow the paste), and has a text box for
 * when the clipboard is empty or the person would rather type or paste there. [onPaste] gets the words, or null for "the clipboard", and calls back with what was done.
 */
@Composable
internal fun PasteBox(onPaste: (String?, (PasteOutcome) -> Unit) -> Unit, state: PasteState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onPaste(null) { r -> state.result = r; if (r.clipboardEmpty) state.typing = true } }, modifier = Modifier.testTag("inbox_paste")) { Text("Paste a bank message") }
        if (state.typing) {
            OutlinedTextField(state.typed, { state.typed = it }, label = { Text("Bank message") }, minLines = 3, modifier = Modifier.fillMaxWidth().testTag("inbox_paste_field"))
            Button(onClick = { onPaste(state.typed) { r -> state.result = r; if (r.kept) state.typed = "" } }, enabled = state.typed.isNotBlank(), modifier = Modifier.testTag("inbox_paste_add")) { Text("Add this message") }
        } else TextButton(onClick = { state.typing = true }, modifier = Modifier.testTag("inbox_paste_type")) { Text("Type or paste it here instead") }
        state.result?.let { r ->
            Text(r.message + (r.detail?.let { ": $it" } ?: ""), fontWeight = if (r.kept) FontWeight.Bold else null, modifier = Modifier.testTag("inbox_paste_result"))
        }
        Text("Read on this phone only. Anything that is not a bank transaction is thrown away; only a count is kept.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

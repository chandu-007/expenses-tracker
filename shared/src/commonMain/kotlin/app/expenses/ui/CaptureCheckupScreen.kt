package app.expenses.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.expenses.CheckupUi
import app.expenses.MessageCheckUi
import app.expenses.core.domain.CaptureCheckup
import app.expenses.core.domain.CheckAct
import app.expenses.core.domain.CheckAction
import app.expenses.core.domain.CheckLevel
import app.expenses.core.domain.CheckRow

/**
 * Google Play's rule for the text permission: a prominent disclosure, answered with a tap, before Android's own permission question. One dialog for every place that asks (Capture, the checkup, the
 * guided setup), so the words and the two answers are the same everywhere.
 */
@Composable
internal fun SmsDisclosure(onAgree: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("Allow Expenses to read your bank texts?", modifier = Modifier.testTag("sms_disclosure")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This app uses the permission to receive text messages (RECEIVE_SMS) for one purpose: to find the payments, credits and bills in texts from your bank, card or biller, and put them in a list for you to approve, so you can track your money.")
                Text("Each text is read on this phone, and the sender is checked first. Texts from people, one-time passwords and senders that are not a known bank or biller are ignored and thrown away: only a count is kept. Nothing is sent anywhere: the app has no internet access at all.")
                Text("You can turn this off in this screen at any time. Android will ask you next; if you say no, the app works as before and you can add entries by hand.")
            }
        },
        confirmButton = { TextButton(onClick = onAgree, modifier = Modifier.testTag("sms_disclosure_agree")) { Text("Agree and continue") } },
        dismissButton = { TextButton(onClick = onDecline, modifier = Modifier.testTag("sms_disclosure_decline")) { Text("No thanks") } },
    )
}

/** The two prominent buttons at the top of the capture screens: the checkup ("Why isn't capture working?") and the guided setup. Nothing is drawn for one that is null. */
@Composable
internal fun CheckupButtons(onOpenCheckup: (() -> Unit)?, onOpenSetup: (() -> Unit)?) {
    if (onOpenCheckup == null && onOpenSetup == null) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        onOpenCheckup?.let { Button(onClick = it, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("open_capture_checkup")) { Text("Why isn't capture working?") } }
        onOpenSetup?.let { OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("open_capture_setup")) { Text("Set up step by step") } }
    }
}

/** The tag of a row's button: `capture_checkup_action_<row>_<ACT>`, with the name it acts on after a second underscore (Treat ICICIT as my bank). */
internal fun checkupActionTag(rowId: String, a: CheckAction) = "capture_checkup_action_${rowId}_${a.act.name}" + if (a.arg.isNotEmpty()) "_${a.arg}" else ""

/**
 * "Why isn't capture working?": one screen that checks every link between a bank's text and a line in To review, says in plain words what to fix, and puts a one-tap button on each thing that can be
 * fixed. Every line says its state in words ("OK", "Fix this", "Note") as well as by its icon and colour. Below the list, "Check a message" shows how the app would read a text the person pastes,
 * without saving anything. [ui] is null while it is being worked out.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun CaptureCheckupScreen(
    ui: CheckupUi?, result: MessageCheckUi?,
    /** Words shared from another app: they start in the text box (the caller has already had them checked). */
    initialText: String = "",
    onAct: (CheckAct, String) -> Unit, onTick: (key: String, on: Boolean) -> Unit,
    onCheck: (sender: String, text: String) -> Unit, onClearResult: () -> Unit, onBack: () -> Unit,
) {
    // what the person pastes lives here and nowhere else: not saved with the screen's state, not in the database, gone when the screen is
    var sender by remember { mutableStateOf("") }
    var text by remember { mutableStateOf(initialText) }
    DisposableEffect(Unit) { onDispose { onClearResult() } }
    val platform = LocalPlatform.current
    Scaffold(topBar = { AreaTopBar("Why isn't capture working?", Area.CAPTURE, Icons.Outlined.Sms, onBack, "capture_checkup_back") }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("capture_checkup"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ui == null) { Text("Checking…", modifier = Modifier.testTag("capture_checkup_loading")); return@Column }
            val head = ui.headline
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background((if (head.attention == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error).copy(alpha = 0.12f)).padding(14.dp)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }.testTag("capture_checkup_status"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(if (head.attention == 0) Icons.Outlined.CheckCircle else Icons.Outlined.Warning, contentDescription = null, tint = if (head.attention == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(head.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }.testTag("capture_checkup_headline"))
                    if (head.notes > 0) Text(if (head.notes == 1) "1 note to read below. Nothing is broken." else "${head.notes} notes to read below. Nothing is broken.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("capture_checkup_notes"))
                }
            }
            Text("Each line below was checked just now, on this phone. Nothing is sent anywhere, and no message is read except one you paste at the bottom.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (ui.rows.isEmpty()) Text("This phone has no way to bring in bank messages, so there is nothing to check.", modifier = Modifier.testTag("capture_checkup_none"))
            ui.rows.forEach { r -> CheckRowView(r, onAct, onTick) }

            // ---- check a message ----
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Check a message", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }.testTag("capture_checkup_check_title"))
                Text(
                    "Paste a bank text here to see how the app would read it: who sent it, what amount, which account and category. Nothing is saved, counted or sent, and the text is gone when you leave this screen. This app never looks at your messages by itself.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("capture_checkup_check_intro"),
                )
                OutlinedTextField(
                    sender, { sender = it }, label = { Text("Sender name (if you know it, like AX-HDFCBK-S)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("capture_checkup_sender"),
                )
                OutlinedTextField(
                    text, { text = it }, label = { Text("The text") }, minLines = 3, maxLines = 8, keyboardOptions = KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth().testTag("capture_checkup_message"),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { platform.readClipboardText()?.let { text = it } }, modifier = Modifier.heightIn(min = 48.dp).testTag("capture_checkup_paste")) { Text("Paste") }
                    Button(onClick = { onCheck(sender, text) }, enabled = text.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp).testTag("capture_checkup_check")) { Text("Check") }
                    OutlinedButton(onClick = { sender = ""; text = ""; onClearResult() }, enabled = text.isNotEmpty() || sender.isNotEmpty() || result != null, modifier = Modifier.heightIn(min = 48.dp).testTag("capture_checkup_clear")) { Text("Clear") }
                }
                if (result != null) CheckResultView(result, onAct)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CheckRowView(r: CheckRow, onAct: (CheckAct, String) -> Unit, onTick: (String, Boolean) -> Unit) {
    val (icon, tint) = when (r.level) {
        CheckLevel.GOOD -> Icons.Outlined.CheckCircle to MaterialTheme.colorScheme.primary
        CheckLevel.ATTENTION -> Icons.Outlined.Warning to MaterialTheme.colorScheme.error
        CheckLevel.NOTE -> Icons.Outlined.Info to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(14.dp).testTag("capture_checkup_row_${r.id}"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                // the state in words, so it never rests on a colour or an icon alone
                Text(CaptureCheckup.levelWord(r.level), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = tint, modifier = Modifier.testTag("capture_checkup_level_${r.id}"))
            }
            Text(r.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("capture_checkup_title_${r.id}"))
            if (r.detail.isNotBlank()) Text(r.detail, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("capture_checkup_detail_${r.id}"))
        }
        r.tick?.let { t ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = t.ticked, role = Role.Checkbox, onValueChange = { onTick(t.key, it) }).testTag("capture_checkup_tick_${t.key}"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(checked = t.ticked, onCheckedChange = null)
                Text(t.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
        if (r.actions.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            r.actions.forEachIndexed { i, a ->
                val mod = Modifier.heightIn(min = 48.dp).testTag(checkupActionTag(r.id, a))
                if (i == 0 && r.level == CheckLevel.ATTENTION) Button(onClick = { onAct(a.act, a.arg) }, modifier = mod) { Text(a.label) }
                else OutlinedButton(onClick = { onAct(a.act, a.arg) }, modifier = mod) { Text(a.label) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun CheckResultView(r: MessageCheckUi, onAct: (CheckAct, String) -> Unit) {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(r) { requester.bringIntoView() }
    val tint = when (r.verdict) { "ignore" -> MaterialTheme.colorScheme.onSurfaceVariant; else -> MaterialTheme.colorScheme.primary }
    Column(
        Modifier.fillMaxWidth().bringIntoViewRequester(requester).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(14.dp).testTag("capture_checkup_result"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            Icon(if (r.verdict == "ignore") Icons.Outlined.Info else Icons.Outlined.CheckCircle, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(r.headline, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = tint, modifier = Modifier.testTag("capture_checkup_result_headline"))
        }
        r.lines.forEach { (label, value) -> ResultLine(label, value, "capture_checkup_result_") }
        Text(r.explain, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("capture_checkup_result_explain"))
        if (r.trustRoot != null) {
            Text("If you treat ${r.trustRoot} as your bank, the app would read it like this:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp).testTag("capture_checkup_result_if"))
            r.ifAdded.forEach { (label, value) -> ResultLine(label, value, "capture_checkup_result_if_") }
            Button(onClick = { onAct(CheckAct.TRUST_SENDER, r.trustRoot) }, modifier = Modifier.heightIn(min = 48.dp).testTag("capture_checkup_result_trust")) { Text("Treat ${r.trustRoot} as my bank") }
        }
        Text("Nothing was saved, counted or sent.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("capture_checkup_result_note"))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultLine(label: String, value: String, tagPrefix: String) {
    // label and value are two texts that wrap under each other at a large font, so neither is cut off
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(tagPrefix + label.lowercase().replace(' ', '_'))) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

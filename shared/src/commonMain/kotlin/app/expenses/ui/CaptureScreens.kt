package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import app.expenses.core.domain.formatted
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Inbox
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
import androidx.compose.ui.unit.dp
import app.expenses.AccountUi
import app.expenses.CaptureSettingsUi
import app.expenses.CategoryUi
import app.expenses.InboxRowUi

/**
 * Transactions found in texts, waiting to be added to the ledger or skipped. Nothing here counts anywhere until it is added.
 * The top says how much is waiting and offers "Add all" for the items that need no decision; each card leads with who, how much and where it will go,
 * so the usual answer is one tap, and everything else (the original message, flipping the direction, naming the payee) is one step further in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    rows: List<InboxRowUi>, accounts: List<AccountUi>, categories: List<CategoryUi>,
    onConfirm: (id: String, accountId: String, categoryId: String, treatment: String, fromId: String, toId: String, refundOf: String, done: (String?) -> Unit) -> Unit, onDismiss: (String) -> Unit, onFlip: (String) -> Unit, onName: (String, String) -> Unit, onBack: () -> Unit,
    /** Where bank messages can be pasted (iPhone): shows the paste button and box. Null on Android, so that screen is as it was. */
    onPaste: ((String?, (app.expenses.PasteOutcome) -> Unit) -> Unit)? = null,
) {
    var confirmingAll by rememberSaveable { mutableStateOf(false) }
    val paste = if (onPaste != null) rememberPasteState() else null
    // those that need no decision: not a repeat, not a related entry, an account the person has used for this bank card before (or the only account there is),
    // and an ordinary payment (a transfer or refund asks to be looked at)
    val easy = rows.filter { it.duplicateNote == null && it.relatedNote == null && (it.rememberedAccount || accounts.size == 1) && it.accountId.isNotEmpty() && it.treatment == "PAYMENT" }
    val refused = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    val cur = app.expenses.core.domain.BaseCurrency.current
    fun total(list: List<InboxRowUi>, credit: Boolean) = app.expenses.core.domain.Money(list.filter { (it.direction == "CREDIT") == credit }.sumOf { it.amountMinor }, cur).formatted()
    if (confirmingAll) AlertDialog(
        onDismissRequest = { confirmingAll = false }, title = { Text("Add ${easy.size} items?") },
        text = { Text("Each one goes where its card says: ${total(easy, false)} out and ${total(easy, true)} in. Items that look like repeats, transfers or refunds, or whose account is not yet known, are left for you to check.") },
        confirmButton = { TextButton(onClick = { confirmingAll = false; refused.clear(); easy.forEach { r -> onConfirm(r.id, r.accountId, r.categoryId, "PAYMENT", "", "", "") { p -> if (p != null) refused += p } } }, modifier = Modifier.testTag("inbox_add_all_ok")) { Text("Add ${easy.size}") } },
        dismissButton = { TextButton(onClick = { confirmingAll = false }) { Text("Cancel") } },
    )
    Scaffold(topBar = { AreaTopBar("To review", Area.CAPTURE, Icons.Outlined.Inbox, onBack, "inbox_back") }) { pad ->
        if (rows.isEmpty() && onPaste != null) {
            LazyColumn(Modifier.padding(pad).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp)) {
                item(key = "empty") {
                    Column {
                        EmptyArt(EmptyKind.INBOX, Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally).padding(bottom = 8.dp))
                        Text("Nothing to review.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("inbox_empty"))
                        Text("Bank texts that look like transactions will appear here for you to add or skip. Nothing is added to your ledger by itself. Set this up in More > Capture from texts, or paste a message below.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item(key = "paste") { PasteBox(onPaste, paste!!) }
            }
        } else if (rows.isEmpty()) {
            Column(Modifier.padding(pad).padding(24.dp).fillMaxSize()) {
                EmptyArt(EmptyKind.INBOX, Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally).padding(bottom = 8.dp))
                Text("Nothing to review.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("inbox_empty"))
                Text("Bank texts that look like transactions will appear here for you to add or skip. Nothing is added to your ledger by itself.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("inbox_list"), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            if (onPaste != null) item(key = "paste") { PasteBox(onPaste, paste!!, Modifier.animateItem()) }
            item(key = "summary") {
                Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${rows.size} to review", style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.testTag("inbox_count"))
                    Text("${total(rows, false)} out · ${total(rows, true)} in. Check each one, or add the clear ones together.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (refused.isNotEmpty()) Text("${refused.size} could not be added: ${refused.first()}", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("inbox_add_all_problem"))
                    if (easy.size >= 2) Button(onClick = { confirmingAll = true }, modifier = Modifier.testTag("inbox_add_all")) { Text("Add all ${easy.size} clear ones") }
                }
            }
            items(rows, key = { it.id }) { r -> InboxCard(r, accounts, categories, onConfirm, onDismiss, onFlip, onName, Modifier.animateItem()) }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun InboxCard(
    r: InboxRowUi, accounts: List<AccountUi>, categories: List<CategoryUi>,
    onConfirm: (String, String, String, String, String, String, String, (String?) -> Unit) -> Unit, onDismiss: (String) -> Unit, onFlip: (String) -> Unit, onName: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var asPayment by rememberSaveable(r.id) { mutableStateOf(false) }
    var fromAcct by rememberSaveable(r.id) { mutableStateOf(r.fromAccountId) }
    var toAcct by rememberSaveable(r.id) { mutableStateOf(r.toAccountId) }
    val paired = r.treatment != "PAYMENT" && !asPayment
    var account by rememberSaveable(r.id) { mutableStateOf(r.accountId) }
    var category by rememberSaveable(r.id) { mutableStateOf(r.categoryId) }
    var changing by rememberSaveable(r.id) { mutableStateOf(false) }
    var naming by rememberSaveable(r.id) { mutableStateOf(false) }
    var showText by rememberSaveable(r.id) { mutableStateOf(false) }
    var problem by remember(r.id) { mutableStateOf<String?>(null) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val credit = r.direction == "CREDIT"
    val flow = if (credit) Flow3.IN else Flow3.OUT
    androidx.compose.material3.Card(
        modifier.fillMaxWidth().testTag("inbox_${r.id}"), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // who, how much
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                CategoryAvatar(r.payee ?: r.issuer, r.payee ?: r.issuer, Modifier.padding(end = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.payee ?: r.issuer, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(
                        "${if (credit) "Money in" else "Money out"} · ${r.dateText} · ${r.issuer}" + (r.last4?.let { " · ending $it" } ?: "") + when (r.payeeKind) { "person" -> " · ${if (credit) "from" else "to"} a person"; "business" -> " · ${if (credit) "from" else "to"} a business"; else -> "" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("inbox_meta_${r.id}"),
                    )
                }
                Text(signedAmount(r.amountText, flow), style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = amountColor(flow), modifier = Modifier.padding(start = 8.dp).testTag("inbox_amount_${r.id}"))
            }
            // warnings first: a repeat or a related entry changes what the right action is
            r.relatedNote?.let { Callout(it, MaterialTheme.colorScheme.secondary, Modifier.testTag("inbox_related_${r.id}")) }
            r.duplicateNote?.let { Callout(it, MaterialTheme.colorScheme.tertiary, Modifier.testTag("inbox_duplicate_${r.id}")) }
            // where it will go: the decision, with its own Change next to it
            Column(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (paired && r.treatment == "TRANSFER") {
                    val nameOf = { id: String -> accounts.firstOrNull { it.id == id }?.name ?: "choose" }
                    Text("Recorded as a ${r.treatmentLabel}: from ${nameOf(fromAcct)} to ${nameOf(toAcct)}. It moves money between your own accounts, so it is not spending.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("inbox_pair_${r.id}"))
                    if (changing) {
                        Text("From", style = MaterialTheme.typography.labelSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { accounts.forEach { a -> FilterChip(selected = a.id == fromAcct, onClick = { fromAcct = a.id }, label = { Text(a.name) }, modifier = Modifier.testTag("inbox_from_${r.id}_${a.id}")) } }
                        Text("To", style = MaterialTheme.typography.labelSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { accounts.forEach { a -> FilterChip(selected = a.id == toAcct, onClick = { toAcct = a.id }, label = { Text(a.name) }, modifier = Modifier.testTag("inbox_to_${r.id}_${a.id}")) } }
                    }
                } else if (paired && r.treatment == "REFUND") {
                    Text("Recorded as a ${r.treatmentLabel}, so the original expense is reduced instead of this counting as income.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("inbox_pair_${r.id}"))
                } else {
                    val accountName = accounts.firstOrNull { it.id == account }?.name ?: "choose an account"
                    val categoryName = categories.firstOrNull { it.id == category }?.name
                    val why = if (category == r.categoryId && r.categoryNote.isNotEmpty()) " (${r.categoryNote})" else ""
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Goes to $accountName" + (if (!credit) " · " + (categoryName ?: "no category") + why else ""), style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, modifier = Modifier.weight(1f).testTag("inbox_goes_${r.id}"))
                        TextButton(onClick = { changing = !changing }, modifier = Modifier.testTag("inbox_change_${r.id}")) { Text(if (changing) "Done" else "Change") }
                    }
                    if (changing) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { accounts.forEach { a -> FilterChip(selected = a.id == account, onClick = { account = a.id }, label = { Text(a.name) }, modifier = Modifier.testTag("inbox_account_${r.id}_${a.id}")) } }
                        if (!credit) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            offeredCategories(categories, income = false, keep = category).take(24).forEach { c -> FilterChip(selected = c.id == category, onClick = { category = c.id }, label = { Text(c.name) }, modifier = Modifier.testTag("inbox_category_${r.id}_${c.id}")) }
                        }
                    }
                }
                if (paired) TextButton(onClick = { changing = !changing }, modifier = Modifier.testTag("inbox_change_${r.id}")) { Text(if (changing) "Done" else "Change") }
            }
            problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("inbox_problem_${r.id}")) }
            // the answer: one big Add (the label says what it will do) and Skip
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm); onConfirm(r.id, account, if (credit) "" else category, if (paired) r.treatment else "PAYMENT", fromAcct, toAcct, r.refundOfEntry) { p -> problem = p } },
                    enabled = account.isNotEmpty() || (paired && r.treatment != "PAYMENT"), modifier = Modifier.weight(1f).testTag("inbox_add_${r.id}"),
                ) { Text(if (r.duplicateNote != null) "Add anyway" else if (paired) "Add as ${r.treatmentLabel}" else "Add") }
                OutlinedButton(onClick = { onDismiss(r.id) }, modifier = Modifier.testTag("inbox_skip_${r.id}")) { Text("Skip") }
            }
            // everything less usual, in small buttons that wrap on a narrow screen
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (r.duplicateNote != null) TextButton(onClick = { onDismiss(r.id) }, modifier = Modifier.testTag("inbox_same_${r.id}")) { Text("Same one: skip") }
                if (r.treatment != "PAYMENT") TextButton(onClick = { asPayment = !asPayment }, modifier = Modifier.testTag("inbox_plain_${r.id}")) { Text(if (asPayment) "Use ${r.treatmentLabel}" else if (credit) "Add as income instead" else "Add as expense instead") }
                TextButton(onClick = { onFlip(r.id) }, modifier = Modifier.testTag("inbox_flip_${r.id}")) { Text(if (credit) "It was money out" else "It was money in") }
                if (r.nameable) TextButton(onClick = { naming = !naming }, modifier = Modifier.testTag("inbox_name_btn_${r.id}")) { Text(if (r.payee.isNullOrBlank()) "Name this payee" else "Rename") }
                TextButton(onClick = { showText = !showText }, modifier = Modifier.testTag("inbox_text_toggle_${r.id}")) { Text(if (showText) "Hide message" else "Show message") }
            }
            if (naming && r.nameable) {
                var nameText by rememberSaveable(r.id) { mutableStateOf("") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.OutlinedTextField(nameText, { nameText = it }, label = { Text(r.payee?.let { "A name for $it" } ?: "A name for this payee") }, singleLine = true, modifier = Modifier.weight(1f).testTag("inbox_name_${r.id}"))
                    Button(onClick = { onName(r.id, nameText); naming = false }, modifier = Modifier.testTag("inbox_name_ok_${r.id}")) { Text("Save") }
                }
                Text("Every later payment to or from the same address shows this name.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (showText) {
                Column(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(r.body, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("inbox_text_${r.id}"))
                    Text("Read automatically: please check it before adding.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** A note with a coloured edge, for the things that change what the right action is (a likely repeat, a related entry). */
@Composable
private fun Callout(text: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.12f)).padding(12.dp))
}

/** Turning SMS capture on or off, and what it does and does not do, before Android asks for the permission. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureSettingsScreen(
    settings: CaptureSettingsUi, permissionGranted: Boolean, notifAccess: Boolean, onToggle: (Boolean) -> Unit, onToggleNotif: (Boolean) -> Unit, onToggleEmail: (Boolean) -> Unit, onOpenAccessSettings: () -> Unit, onHeaders: (String) -> Unit, onOpenRules: () -> Unit, onOpenHealth: () -> Unit, onBack: () -> Unit,
    /** Runs a text through the same reading as a message from the Shortcuts automation (iPhone's setup page uses it for "Send a test now"). */
    onTestMessage: ((String, (app.expenses.PasteOutcome) -> Unit) -> Unit)? = null,
) {
    if (LocalPlatform.current.supportsShortcutsCapture) { ShortcutsCaptureScreen(settings, onHeaders, onOpenRules, onOpenHealth, onBack, onTestMessage); return }
    var headers by rememberSaveable(settings.extraHeaders) { mutableStateOf(settings.extraHeaders) }
    // the Google Play build has no SMS permission: only the payment-app notifications are offered there (every other build keeps the text switch)
    val readsTexts = LocalPlatform.current.supportsSmsCapture
    // Google Play's rule for the text permission: a prominent disclosure, answered with a tap, before Android's own permission question
    var disclosing by rememberSaveable { mutableStateOf(false) }
    if (disclosing) AlertDialog(
        onDismissRequest = { disclosing = false },
        title = { Text("Allow Expenses to read your bank texts?", modifier = Modifier.testTag("sms_disclosure")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This app uses the permission to receive text messages (RECEIVE_SMS) for one purpose: to find the payments, credits and bills in texts from your bank, card or biller, and put them in a list for you to approve, so you can track your money.")
                Text("Each text is read on this phone, and the sender is checked first. Texts from people, one-time passwords and senders that are not a known bank or biller are ignored and thrown away: only a count is kept. Nothing is sent anywhere: the app has no internet access at all.")
                Text("You can turn this off in this screen at any time. Android will ask you next; if you say no, the app works as before and you can add entries by hand.")
            }
        },
        confirmButton = { TextButton(onClick = { disclosing = false; onToggle(true) }, modifier = Modifier.testTag("sms_disclosure_agree")) { Text("Agree and continue") } },
        dismissButton = { TextButton(onClick = { disclosing = false }, modifier = Modifier.testTag("sms_disclosure_decline")) { Text("No thanks") } },
    )
    Scaffold(topBar = { AreaTopBar(if (readsTexts) "Capture from texts" else "Capture from payment apps", Area.CAPTURE, Icons.Outlined.Sms, onBack, "capture_back") }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (readsTexts) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Read bank texts", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = settings.smsOn && permissionGranted, onCheckedChange = { on -> if (on && !permissionGranted) disclosing = true else onToggle(on) }, modifier = Modifier.testTag("capture_sms_switch"))
            }
            Text("What this does", style = MaterialTheme.typography.labelMedium)
            Text("When a text arrives from a bank or card sender, this app reads it on your phone and, if it is a payment or a credit, puts it in your To review list. If it says a bill is due (a card statement, electricity, water, gas, mobile, broadband, an EMI, an insurance premium), it is shown on the Bills page and you are told. You decide whether to add either.", style = MaterialTheme.typography.bodyMedium)
            Text("What it does not do", style = MaterialTheme.typography.labelMedium)
            Text("Texts from people, one-time passwords and anything from a sender that is not on the list below are ignored without being read; the sender is checked first. The list is banks, cards and a few billers (for example BESCOM, Airtel, Jio, LIC); a bill's text is kept only until you add or skip it. Nothing is sent anywhere: the reading happens on this phone. A text is kept only until you add or skip it; after that only the amount, date and reference stay.", style = MaterialTheme.typography.bodyMedium)
            if (settings.smsOn && !permissionGranted) Text("Android has not allowed this app to receive texts. Turn the switch on again to be asked.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("capture_permission_missing"))
            HorizontalDivider()
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Read payment app notifications", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = settings.notifOn && notifAccess, onCheckedChange = onToggleNotif, modifier = Modifier.testTag("capture_notif_switch"))
            }
            Text("Reads the notification a payment app shows after a payment (Google Pay, PhonePe, Paytm, BHIM) and puts it in To review. Notifications from every other app are never read. This needs the phone's Notification access, which you turn on in its settings.", style = MaterialTheme.typography.bodyMedium)
            if (settings.notifOn && !notifAccess) Text("Notification access is not given to this app yet.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("capture_notif_access_missing"))
            OutlinedButton(onClick = onOpenAccessSettings, modifier = Modifier.testTag("capture_notif_settings")) { Text("Open notification access settings") }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Read bank alert emails from Gmail and Outlook notifications", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = settings.emailOn && notifAccess, onCheckedChange = onToggleEmail, modifier = Modifier.testTag("email_capture_switch"))
            }
            Text("Off by default. When a bank sends you an email, Gmail, Outlook or Yahoo Mail shows a notification, and this reads that notification (the sender's name, the subject and the start of the message the email app shows) on this phone. Only a notification from a bank or card issuer on the built-in list (or a name you add below) whose words read like a payment, a credit or a bill is kept, and it goes to To review for you to add or skip. Every other email, one-time passwords and mail from people are ignored. Nothing is sent anywhere. The email app's own notification has to show the text: Gmail shows the subject and the start of the message, so an alert whose amount comes later in the email, or a phone set to hide notification contents, is missed. This uses the same Notification access as above.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("email_capture_note"))
            if (settings.emailOn && !notifAccess) Text("Notification access is not given to this app yet.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("email_capture_access_missing"))
            HorizontalDivider()
            Text("Your bank's sender name", style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(headers, { headers = it }, label = { Text("Extra sender names (one per line)") }, modifier = Modifier.fillMaxWidth().testTag("capture_headers"))
            Text("Only senders on the built-in list of banks and billers are read. If yours is missing, add the name that appears after the first dash, for example HDFCBK from AX-HDFCBK-S.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { onHeaders(headers) }, modifier = Modifier.testTag("capture_headers_save")) { Text("Save names") }
            HorizontalDivider()
            OutlinedButton(onClick = onOpenRules, modifier = Modifier.testTag("open_rules")) { Text("Category rules") }
            OutlinedButton(onClick = onOpenHealth, modifier = Modifier.testTag("open_health")) { Text("How capture is doing") }
        }
    }
}

/** The person's own rules: "when the payee or text mentions X, use this category", tried top to bottom, tested against past entries before saving. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CaptureRulesScreen(
    rules: List<app.expenses.CaptureRuleUi>, categories: List<CategoryUi>,
    onAdd: (pattern: String, categoryId: String, done: (String?) -> Unit) -> Unit, onRemove: (String) -> Unit, onMove: (String, Boolean) -> Unit,
    onTest: (String, (Long) -> Unit) -> Unit, onBack: () -> Unit,
) {
    var pattern by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var tested by remember { mutableStateOf<Long?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { AreaTopBar("Category rules", Area.CAPTURE, Icons.Outlined.Label, onBack, "rules_back") }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("rules_list"), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("When a captured payment mentions these words, it gets this category. Rules are tried from the top; the first match wins, and a rule beats what the app learned from your earlier choices.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            itemsIndexed(rules, key = { _, r -> r.id }) { i, r ->
                Row(Modifier.animateItem().fillMaxWidth().testTag("rule_${r.pattern}"), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text("\"${r.pattern}\"", style = MaterialTheme.typography.bodyLarge); Text("→ ${r.categoryName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(onClick = { onMove(r.id, true) }, enabled = i > 0, modifier = Modifier.testTag("rule_up_${r.pattern}")) { Text("Up") }
                    TextButton(onClick = { onMove(r.id, false) }, enabled = i < rules.lastIndex, modifier = Modifier.testTag("rule_down_${r.pattern}")) { Text("Down") }
                    TextButton(onClick = { onRemove(r.id) }, modifier = Modifier.testTag("rule_remove_${r.pattern}")) { Text("Remove") }
                }
            }
            item { HorizontalDivider() }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("New rule", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(pattern, { pattern = it; tested = null; problem = null }, label = { Text("Payee or text contains") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("rule_pattern"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // a rule only applies to captured payments, which are expenses
                        offeredCategories(categories, income = false).take(24).forEach { c -> FilterChip(selected = c.id == category, onClick = { category = c.id; problem = null }, label = { Text(c.name) }, modifier = Modifier.testTag("rule_category_${c.id}")) }
                    }
                    tested?.let { Text(if (it == 0L) "No past entry mentions this." else "It would have matched $it of your past entries.", modifier = Modifier.testTag("rule_tested")) }
                    problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("rule_problem")) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onTest(pattern) { tested = it } }, enabled = pattern.isNotBlank(), modifier = Modifier.testTag("rule_test")) { Text("Test on my history") }
                        Button(onClick = { onAdd(pattern, category) { p -> if (p == null) { pattern = ""; tested = null } else problem = p } }, enabled = pattern.isNotBlank() && category.isNotEmpty(), modifier = Modifier.testTag("rule_add")) { Text("Add rule") }
                    }
                }
            }
        }
    }
}

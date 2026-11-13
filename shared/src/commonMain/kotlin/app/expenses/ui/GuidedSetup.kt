package app.expenses.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.expenses.AccountRowUi
import app.expenses.SetupUi
import app.expenses.core.domain.CaptureSetup
import app.expenses.core.domain.CaptureWay
import app.expenses.core.domain.SetupState
import app.expenses.core.domain.SetupStep
import app.expenses.core.parsers.BankChoices
import app.expenses.core.parsers.TrustedSenders

/** What the setup knows about the phone right now: the permission and switches, as the host and the settings say. */
class SetupFacts(
    val way: CaptureWay, val smsPermission: Boolean, val smsOn: Boolean, val notifOn: Boolean, val notifAccess: Boolean, val messageReceived: Boolean, val extraHeaders: String,
)

/** Every action the setup can take. All of them are things the Capture screen does already: the setup only walks the person through them. */
class SetupActions(
    val onStep: (SetupStep) -> Unit, val onLeave: (SetupStep) -> Unit, val onAllowTexts: () -> Unit, val onTurnOnPaymentApps: () -> Unit,
    val onOpenShortcuts: () -> Unit, val onOpenCaptureSteps: () -> Unit, val onChooseBank: (String, Boolean) -> Unit, val onHeaders: (String) -> Unit,
    val onSaveDigits: (row: AccountRowUi, digits: String, done: (String?) -> Unit) -> Unit, val onOpenAccounts: () -> Unit, val onOpenCheckup: () -> Unit, val onDone: () -> Unit,
)

/**
 * "Set up reading bank texts": four short steps after the first tour, each one a calm page with one thing to do and a way to skip it. 1 the permission (Android; on the iPhone the Shortcuts
 * automation), 2 the banks you use, 3 your accounts with their last 3 or 4 digits, 4 a summary and the checkup. Nothing here is new machinery: the permission is the one the Capture screen asks
 * for, the sender box is the Capture screen's own editor, the digits are saved by the same call as the Accounts page's form. Leaving at any step keeps the place ("Continue" on Home).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GuidedSetupScreen(step: SetupStep, setup: SetupUi, facts: SetupFacts, accounts: List<AccountRowUi>, actions: SetupActions) {
    var disclosing by rememberSaveable { mutableStateOf(false) }
    if (disclosing) SmsDisclosure(onAgree = { disclosing = false; actions.onAllowTexts() }, onDecline = { disclosing = false })
    Scaffold(topBar = { AreaTopBar("Set up reading bank texts", Area.CAPTURE, Icons.Outlined.Sms, { actions.onLeave(step) }, "setup_close", backLabel = "Close") }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("setup_screen"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(CaptureSetup.stepWord(step), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("setup_progress"))
            LinearProgressIndicator(progress = { step.number / SetupStep.COUNT.toFloat() }, modifier = Modifier.fillMaxWidth())
            Column(Modifier.fillMaxWidth().testTag("setup_step_${step.name.lowercase()}"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (step) {
                    SetupStep.PERMISSION -> PermissionStep(facts, actions) { disclosing = true }
                    SetupStep.BANKS -> BanksStep(setup, facts, actions)
                    SetupStep.ACCOUNTS -> AccountsStep(setup, accounts, actions)
                    SetupStep.DONE -> DoneStep(setup, facts, accounts, actions)
                }
            }
            // Back, Skip this step, Next: wrap under each other at a large font; never pinned, so nothing hides behind a keyboard
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (step == SetupStep.DONE) {
                    Button(onClick = actions.onDone, modifier = Modifier.heightIn(min = 48.dp).testTag("setup_done")) { Text("Done") }
                    if (CaptureSetup.stepBefore(step) != step) OutlinedButton(onClick = { actions.onStep(CaptureSetup.stepBefore(step)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("setup_back_step")) { Text("Back") }
                } else {
                    Button(onClick = { actions.onStep(CaptureSetup.stepAfter(step)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("setup_next")) { Text("Next") }
                    TextButton(onClick = { actions.onStep(CaptureSetup.stepAfter(step)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("setup_skip_step")) { Text("Skip this step") }
                    if (step != SetupStep.PERMISSION) OutlinedButton(onClick = { actions.onStep(CaptureSetup.stepBefore(step)) }, modifier = Modifier.heightIn(min = 48.dp).testTag("setup_back_step")) { Text("Back") }
                }
            }
            if (step != SetupStep.DONE) Text("You can stop here and carry on later: Home will say Continue, and More, Capture has Set up step by step. Nothing in this setup is needed for the app to work.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("setup_no_pressure"))
        }
    }
}

/** A line with its state in words as well as an icon: "Allowed" or "Not yet". */
@Composable
private fun StateLine(done: Boolean, doneText: String, todoText: String, tag: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.semantics(mergeDescendants = true) {}.testTag(tag)) {
        Icon(if (done) Icons.Outlined.CheckCircle else Icons.Outlined.Info, contentDescription = null, tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Text(if (done) doneText else todoText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionStep(f: SetupFacts, a: SetupActions, askForTexts: () -> Unit) {
    when (f.way) {
        CaptureWay.TEXTS -> {
            Title("Let the app see your bank texts")
            Body("Your bank sends a text for every payment. With your OK, this app can read those texts on this phone and list the payments for you to approve. Only texts from banks and billers on a list are read; texts from people and one-time passwords are ignored. Nothing leaves this phone.")
            val on = f.smsPermission && f.smsOn
            StateLine(on, "Reading bank texts is on", if (f.smsPermission) "Allowed by Android, but the switch is off" else "Not allowed yet", "setup_permission_state")
            if (!on) Button(onClick = { if (f.smsPermission) a.onAllowTexts() else askForTexts() }, modifier = Modifier.heightIn(min = 48.dp).testTag("setup_allow_texts")) { Text(if (f.smsPermission) "Turn it on" else "Allow reading texts") }
            Small("Android asks you next. If you say no, nothing breaks: you can add entries by hand, and ask again from More, Capture.")
        }
        CaptureWay.APPS_ONLY -> {
            Title("Read payment app alerts")
            Body("This version reads the alert that Google Pay, PhonePe, Paytm or BHIM shows after a payment, and lists it for you to approve. For that, Android must let this app see notifications. Alerts from every other app are never read.")
            val on = f.notifOn && f.notifAccess
            StateLine(on, "Reading payment apps is on", if (f.notifOn) "Switched on, but notification access is not given" else "Not on yet", "setup_permission_state")
            if (!on) Button(onClick = a.onTurnOnPaymentApps, modifier = Modifier.heightIn(min = 48.dp).testTag("setup_allow_apps")) { Text("Turn on and give access") }
            Small("Android opens its list of apps that may see notifications: choose Expenses and come back.")
        }
        CaptureWay.SHORTCUTS -> {
            Title("Hand your bank texts to the app")
            Body("iPhone does not let any app read your messages. What it allows is an automation in the Shortcuts app that runs when a bank text arrives and passes that text to this app. It takes a few minutes, once.")
            StateLine(f.messageReceived, "A message has reached the app", "No message has reached the app yet", "setup_permission_state")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = a.onOpenShortcuts, modifier = Modifier.heightIn(min = 48.dp).testTag("setup_open_shortcuts")) { Text("Open Shortcuts") }
                OutlinedButton(onClick = a.onOpenCaptureSteps, modifier = Modifier.heightIn(min = 48.dp).testTag("setup_open_steps")) { Text("See the steps") }
            }
            Small("You can skip this and use Paste a bank message in To review instead.")
        }
        CaptureWay.NONE -> { Title("Nothing to set up here"); Body("This phone has no way to bring in bank messages. You can still add every payment by hand.") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BanksStep(setup: SetupUi, f: SetupFacts, a: SetupActions) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    var notListed by rememberSaveable { mutableStateOf(false) }
    Title("Which banks do you use?")
    Body("Tap each one. The app reads the texts of every bank on this list already; picking yours lets the checkup tell you if a bank's texts are not arriving.")
    val shown = if (showAll) BankChoices.all else BankChoices.all.take(BankChoices.POPULAR)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEach { c ->
            val on = c.issuer in setup.banks
            FilterChip(selected = on, onClick = { a.onChooseBank(c.issuer, !on) }, label = { Text(c.issuer) }, modifier = Modifier.heightIn(min = 48.dp).testTag("setup_bank_${c.issuer}"))
        }
        // a chosen bank that is further down the list stays visible
        if (!showAll) BankChoices.all.drop(BankChoices.POPULAR).filter { it.issuer in setup.banks }.forEach { c ->
            FilterChip(selected = true, onClick = { a.onChooseBank(c.issuer, false) }, label = { Text(c.issuer) }, modifier = Modifier.heightIn(min = 48.dp).testTag("setup_bank_${c.issuer}"))
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (BankChoices.all.size > BankChoices.POPULAR) OutlinedButton(onClick = { showAll = !showAll }, modifier = Modifier.heightIn(min = 48.dp).testTag("setup_more_banks")) { Text(if (showAll) "Show fewer banks" else "More banks (${BankChoices.all.size - BankChoices.POPULAR})") }
        OutlinedButton(onClick = { notListed = !notListed }, modifier = Modifier.heightIn(min = 48.dp).testTag("setup_bank_not_listed")) { Text("My bank is not listed") }
    }
    if (setup.banks.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.semantics(mergeDescendants = true) {}.testTag("setup_bank_names")) {
        setup.banks.forEach { issuer -> Text("$issuer texts from ${BankChoices.senderNamesOf(issuer).joinToString(" or ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (notListed) Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("setup_not_listed_box")) {
        Body("Type the name your bank's texts come from. It is shown at the top of the text, like AX-HDFCBK-S: the part after the first dash (HDFCBK) is the name.")
        TrustedSendersEditor(f.extraHeaders, a.onHeaders)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountsStep(setup: SetupUi, accounts: List<AccountRowUi>, a: SetupActions) {
    Title("Add your accounts")
    Body("A bank text says something like A/c XX1234. When an account has those last 3 or 4 digits, each text finds its account by itself.")
    val banks = accounts.filter { !it.archived && (it.type == "BANK" || it.type == "CREDIT_CARD") }
    if (banks.isEmpty()) {
        Body(if (setup.banks.isEmpty()) "You have no bank account or card yet." else "You picked ${setup.banks.joinToString(", ")}. Add an account for each, with its last 3 or 4 digits.")
    }
    banks.forEach { row -> DigitsRow(row, a) }
    OutlinedButton(onClick = a.onOpenAccounts, modifier = Modifier.heightIn(min = 48.dp).testTag("setup_open_accounts")) { Text(if (banks.isEmpty()) "Add an account" else "Add or change accounts") }
    Small("This opens your Accounts page. Come back with Back.")
}

/** One bank account or card: its digits in words, or a box to add them (3 or 4 digits, as the bank prints them). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DigitsRow(row: AccountRowUi, a: SetupActions) {
    var typed by rememberSaveable(row.id) { mutableStateOf("") }
    var problem by rememberSaveable(row.id) { mutableStateOf<String?>(null) }
    val has = !row.last4.isNullOrBlank()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(12.dp).testTag("setup_account_${row.id}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(row.name, style = MaterialTheme.typography.titleSmall)
        StateLine(has, "Digits ending ${row.last4}", "No digits yet", "setup_account_state_${row.id}")
        if (!has) {
            OutlinedTextField(
                typed, { typed = it.filter { c -> c.isDigit() }.take(4); problem = null }, label = { Text("Last 3 or 4 digits") }, singleLine = true, isError = problem != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("setup_digits_${row.id}"),
            )
            problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("setup_digits_problem_${row.id}")) }
            OutlinedButton(onClick = { a.onSaveDigits(row, typed) { p -> problem = p; if (p == null) typed = "" } }, enabled = typed.length >= 3, modifier = Modifier.heightIn(min = 48.dp).testTag("setup_digits_save_${row.id}")) { Text("Save the digits") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DoneStep(setup: SetupUi, f: SetupFacts, accounts: List<AccountRowUi>, a: SetupActions) {
    Title("All set")
    val banks = accounts.filter { !it.archived && (it.type == "BANK" || it.type == "CREDIT_CARD") }
    val withDigits = banks.count { !it.last4.isNullOrBlank() }
    val reading = when (f.way) {
        CaptureWay.TEXTS -> f.smsPermission && f.smsOn
        CaptureWay.APPS_ONLY -> f.notifOn && f.notifAccess
        CaptureWay.SHORTCUTS -> f.messageReceived
        CaptureWay.NONE -> false
    }
    Column(Modifier.fillMaxWidth().testTag("setup_summary"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StateLine(reading, when (f.way) { CaptureWay.SHORTCUTS -> "A message has reached the app"; CaptureWay.APPS_ONLY -> "Reading payment apps is on"; else -> "Reading bank texts is on" }, when (f.way) { CaptureWay.SHORTCUTS -> "No message has reached the app yet"; CaptureWay.APPS_ONLY -> "Payment apps are not being read yet"; else -> "Reading bank texts is not on yet" }, "setup_summary_reading")
        StateLine(setup.banks.isNotEmpty(), "Banks: ${setup.banks.joinToString(", ")}", "No bank picked", "setup_summary_banks")
        StateLine(banks.isNotEmpty() && withDigits == banks.size, "Accounts: $withDigits of ${banks.size} have their digits", if (banks.isEmpty()) "No bank account added yet" else "Accounts: $withDigits of ${banks.size} have their digits", "setup_summary_accounts")
        val added = TrustedSenders.parse(f.extraHeaders)
        if (added.isNotEmpty()) StateLine(true, "Names you added: ${added.joinToString(", ")}", "", "setup_summary_names")
    }
    Body("The checkup looks at every link between a bank's text and your To review list, and tells you what to fix. Open it now, or any time from More, Capture.")
    Button(onClick = a.onOpenCheckup, modifier = Modifier.heightIn(min = 48.dp).testTag("setup_open_checkup")) { Text("Check that everything works") }
}

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() }.testTag("setup_title"))

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge)

@Composable
private fun Small(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

/**
 * The Home card for the guided setup (see [CaptureSetup.nudgeCount]): where the person is, one button to start or carry on, and the close, which puts it off for a day. Hiding it for good is offered by the
 * caller right after the close ("Hidden for today. Hide for good"): the setup stays in More, Capture. The look is every nudge's ([NudgeCard]).
 */
@Composable
fun SetupNudgeCard(state: SetupState, onOpen: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    NudgeCard(
        tag = "setup_nudge", title = "Set up reading bank texts", titleTag = "setup_nudge_title", line = CaptureSetup.cardLine(state), lineTag = "setup_nudge_line",
        actionLabel = CaptureSetup.cardButton(state), actionTag = "setup_nudge_open", onAction = onOpen,
        dismissTag = "nudge_notnow_setup", dismissLabel = "Not now: hide for today", onDismiss = onNotNow, modifier = modifier,
    )
}

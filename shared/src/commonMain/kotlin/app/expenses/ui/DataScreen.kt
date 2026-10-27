package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import app.expenses.BackupKeys
import app.expenses.core.domain.AttachmentRules
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.FlowRow
import app.expenses.core.domain.AutoExportFrequency
import app.expenses.AutoExportUi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.expenses.DataUi
import app.expenses.fileCountText
import app.expenses.todayLocal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(
    state: DataUi,
    onBack: () -> Unit,
    onExportCsv: (String) -> Unit,
    onExportXlsx: (String) -> Unit = {},
    onExportFinancialYear: (uri: String, yearsBack: Int) -> Unit = { _, _ -> },
    autoExport: AutoExportUi? = null,
    onAutoFolder: (String) -> Unit = {},
    onAutoFrequency: (AutoExportFrequency) -> Unit = {},
    onAutoNow: () -> Unit = {},
    onBackup: (String, String?) -> Unit,
    canShareBackup: Boolean = false,
    onShareBackup: (name: String, passphrase: String?) -> Unit = { _, _ -> },
    onOpenBackup: (String) -> Unit,
    onImportStatement: (String) -> Unit,
    onUnlock: (String) -> Unit,
    onRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    onShown: () -> Unit = {},
    onIncludeFiles: (Boolean) -> Unit = {},
    autoRunning: Boolean = false,
) {
    val platform = LocalPlatform.current
    // how many photos and files a backup would carry is read when the screen opens
    LaunchedEffect(Unit) { onShown() }
    var pass by rememberSaveable { mutableStateOf("") }
    var pass2 by rememberSaveable { mutableStateOf("") }
    var openPass by rememberSaveable { mutableStateOf("") }
    val encrypting = pass.isNotEmpty()
    val passProblem = when {
        !encrypting -> null
        pass.length < platform.backupMinPassphrase -> "At least ${platform.backupMinPassphrase} characters"
        pass != pass2 -> "The two passphrases are different"
        else -> null
    }
    val today = todayLocal()
    val pickers = rememberFilePickers()

    state.pending?.let { p ->
        AlertDialog(
            onDismissRequest = onCancelRestore,
            title = { Text("Restore this backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("restore_preview")) {
                    Text("Made ${p.madeAt}. It holds ${p.entriesInFile} entries.")
                    Text("${p.newEntries} entries are new to this phone and will be added" + if (p.otherNewItems > 0) ", along with ${p.otherNewItems} other items (categories, budgets, repeating expenses…)." else ".")
                    Text("${p.alreadyHereEntries} are already here and will be left exactly as they are. Nothing is overwritten or deleted.")
                    if (p.filesInFile > 0) {
                        Text(
                            "It also holds ${fileCountText(p.filesInFile)} (${AttachmentRules.sizeText(p.filesBytes)}): ${if (p.newFiles == 1) "1 is" else "${p.newFiles} are"} not on this phone and will be put back. Each is checked as it is restored; a damaged one is skipped and counted, never stopping the rest.",
                            modifier = Modifier.testTag("restore_files"),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = onRestore, enabled = !state.busy, modifier = Modifier.testTag("restore_confirm")) { Text("Restore") } },
            dismissButton = { TextButton(onClick = onCancelRestore, modifier = Modifier.testTag("restore_cancel")) { Text("Cancel") } },
        )
    }
    if (state.needsPassphrase) {
        AlertDialog(
            onDismissRequest = onCancelRestore,
            title = { Text("Passphrase") },
            text = {
                OutlinedTextField(
                    value = openPass, onValueChange = { openPass = it }, label = { Text("Passphrase") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().testTag("open_passphrase"),
                )
            },
            confirmButton = { TextButton(onClick = { onUnlock(openPass); openPass = "" }, enabled = openPass.isNotEmpty(), modifier = Modifier.testTag("open_unlock")) { Text("Open") } },
            dismissButton = { TextButton(onClick = onCancelRestore) { Text("Cancel") } },
        )
    }

    Scaffold(topBar = { AreaTopBar("Your data", Area.PRIVACY, Icons.Outlined.Backup, onBack, "data_back") }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SigningNotice()
            if (state.busy) CircularProgressIndicator(Modifier.testTag("data_busy"))
            state.status?.let {
                Text(it, color = if (state.statusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("data_status"))
            }

            Text("Spreadsheet", style = MaterialTheme.typography.titleMedium)
            Text("Every entry as an Excel workbook (amounts are numbers, so they add up) or a plain CSV file. Free, always. For moving to another phone use a backup: a spreadsheet leaves things out.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { pickers.createDocument("expenses-$today.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") { it?.let(onExportXlsx) } }, enabled = !state.busy, modifier = Modifier.testTag("export_xlsx")) { Text("Export as Excel (.xlsx)") }
            OutlinedButton(onClick = { pickers.createDocument("expenses-$today.csv", "text/csv") { it?.let(onExportCsv) } }, enabled = !state.busy, modifier = Modifier.testTag("export_csv")) { Text("Export as CSV") }
            Text("Financial year summary", style = MaterialTheme.typography.titleSmall)
            Text("One page for your records or an accountant: income, spending, spending by category and by month for April to March. A summary, not tax advice.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickers.createDocument("expenses-financial-year-this.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") { it?.let { uri -> onExportFinancialYear(uri, 0) } } }, enabled = !state.busy, modifier = Modifier.testTag("export_fy_this")) { Text("This year") }
                OutlinedButton(onClick = { pickers.createDocument("expenses-financial-year-last.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") { it?.let { uri -> onExportFinancialYear(uri, 1) } } }, enabled = !state.busy, modifier = Modifier.testTag("export_fy_last")) { Text("Last year") }
            }
            HorizontalDivider()

            if (autoExport != null && !platform.supportsAutoExport) {
                Text("Automatic copies to your own folder", style = MaterialTheme.typography.titleMedium)
                Text(NOT_ON_IPHONE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("auto_unavailable"))
                HorizontalDivider()
            } else if (autoExport != null) {
                Text("Automatic copies to your own folder", style = MaterialTheme.typography.titleMedium)
                Text("A backup and an Excel file are saved into a folder you choose, such as one on your drive, and only the newest ${platform.autoExportKeep} of each are kept. They are not encrypted, so choose a folder only you can reach. This is the safety net for losing or resetting the phone. They carry your photos and files too while \"Include photos and files\" under Backup is on and together they are under ${AttachmentRules.sizeText(BackupKeys.AUTO_FILES_LIMIT_BYTES)}; over that the copies are made without them and the line below says so.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { pickers.openFolder { it?.let(onAutoFolder) } }, modifier = Modifier.testTag("auto_folder")) { Text(autoExport.folderName?.let { "Folder: $it" } ?: "Choose a folder") }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AutoExportFrequency.entries.forEach { f -> FilterChip(selected = autoExport.frequency == f, onClick = { onAutoFrequency(f) }, label = { Text(f.label) }, modifier = Modifier.testTag("auto_freq_${f.name}")) }
                }
                autoExport.lastText?.let { Text("Last saved $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("auto_last")) }
                autoExport.status?.let { Text(it, color = if (autoExport.statusOk) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("auto_status")) }
                OutlinedButton(onClick = onAutoNow, enabled = autoExport.folderName != null && !autoRunning, modifier = Modifier.testTag("auto_now")) { Text(if (autoRunning) "Saving a copy…" else "Save a copy now") }
                HorizontalDivider()
            }

            Text("Bring in a bank statement", style = MaterialTheme.typography.titleMedium)
            Text("Import a CSV, Excel or PDF file downloaded from your bank or card, or a photo or screenshot of a statement (read on this phone, nothing is sent anywhere). The way a bank's file was read is remembered for next time. You check how it was read before anything is saved, and importing the same file twice adds nothing new.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { pickers.openDocument(listOf("*/*")) { it?.let(onImportStatement) } }, enabled = !state.busy, modifier = Modifier.testTag("import_statement")) { Text("Import a statement (CSV, Excel, PDF or photo)") }
            HorizontalDivider()

            Text("Backup", style = MaterialTheme.typography.titleMedium)
            Text(
                "Everything in one file: entries, categories, budgets, repeating expenses, and the photos and files attached to entries unless you switch that off below. Keep it somewhere safe, like your Google Drive or email to yourself. " +
                    "Uninstalling the app deletes the data on this phone, so make a backup first.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Include photos and files", modifier = Modifier.weight(1f))
                Switch(checked = state.files.include, onCheckedChange = onIncludeFiles, modifier = Modifier.testTag("backup_files_switch"))
            }
            val files = state.files
            Text(
                when {
                    files.count == 0 -> "No photos or files are attached to entries yet. When there are, this switch decides whether a backup carries them."
                    files.include -> "${fileCountText(files.count)}, ${AttachmentRules.sizeText(files.bytes)} together, will be in the backup, so the file is that much larger. A restore brings ${if (files.count == 1) "it" else "them"} back. A backup with photos or files needs this version of the app or a newer one to restore; one without them opens in any version."
                    else -> "${fileCountText(files.count)}, ${AttachmentRules.sizeText(files.bytes)} together, will stay out of the backup: a restore brings back your entries without their receipts, and if you lose this phone the files are lost too."
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("backup_files_info"),
            )
            OutlinedTextField(
                value = pass, onValueChange = { pass = it }, label = { Text("Passphrase (optional)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                supportingText = { Text("Leave empty for no encryption. If you set one and forget it, the backup cannot be opened.") },
                modifier = Modifier.fillMaxWidth().testTag("backup_pass"),
            )
            if (encrypting) {
                OutlinedTextField(
                    value = pass2, onValueChange = { pass2 = it }, label = { Text("Type it again") }, singleLine = true, isError = passProblem != null,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = { passProblem?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth().testTag("backup_pass2"),
                )
            }
            OutlinedButton(onClick = { pickers.createDocument("expenses-backup-$today.expbak", "application/octet-stream") { it?.let { uri -> onBackup(uri, pass.ifEmpty { null }) } } }, enabled = !state.busy && passProblem == null, modifier = Modifier.testTag("create_backup")) { Text("Save backup to a file") }
            Text("Choose Google Drive in the list that opens to keep the file in your Drive.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("backup_drive_hint"))
            if (canShareBackup) {
                OutlinedButton(onClick = { onShareBackup("expenses-backup-$today.expbak", pass.ifEmpty { null }) }, enabled = !state.busy && passProblem == null, modifier = Modifier.testTag("share_backup")) { Text("Send backup to…") }
                Text("Opens the share sheet: Google Drive, email, WhatsApp. Sending is not confirmed here, so it does not count as a backup made." + (if (state.files.include && state.files.count > 0) " Photos and files go in up to ${AttachmentRules.sizeText(BackupKeys.SHARE_FILES_LIMIT_BYTES)}; a bigger set is left out of what is sent (the message says so), so use Save backup to a file to keep them." else "") + if (pass.isEmpty()) " Without a passphrase the file is not encrypted, and it leaves this phone through the app you choose: set one above first if that matters." else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()

            Text("Restore", style = MaterialTheme.typography.titleMedium)
            Text(
                "Choose a backup file. It is checked first, and you see what would be added before anything changes. " +
                    "Restoring never creates duplicates and never changes what is already on this phone.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { pickers.openDocument(listOf("*/*")) { it?.let(onOpenBackup) } }, enabled = !state.busy, modifier = Modifier.testTag("open_backup")) { Text("Choose a backup file") }
        }
    }
}

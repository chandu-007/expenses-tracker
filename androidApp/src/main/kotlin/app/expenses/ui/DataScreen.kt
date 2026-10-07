package app.expenses.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.expenses.BackupCrypto
import app.expenses.DataUi
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(
    state: DataUi,
    onBack: () -> Unit,
    onExportCsv: (Uri) -> Unit,
    onBackup: (Uri, String?) -> Unit,
    onOpenBackup: (Uri) -> Unit,
    onUnlock: (String) -> Unit,
    onRestore: () -> Unit,
    onCancelRestore: () -> Unit,
) {
    var pass by rememberSaveable { mutableStateOf("") }
    var pass2 by rememberSaveable { mutableStateOf("") }
    var openPass by rememberSaveable { mutableStateOf("") }
    val encrypting = pass.isNotEmpty()
    val passProblem = when {
        !encrypting -> null
        pass.length < BackupCrypto.MIN_PASSPHRASE -> "At least ${BackupCrypto.MIN_PASSPHRASE} characters"
        pass != pass2 -> "The two passphrases are different"
        else -> null
    }
    val today = LocalDate.now()
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let(onExportCsv) }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { it?.let { uri -> onBackup(uri, pass.ifEmpty { null }) } }
    val openPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(onOpenBackup) }

    state.pending?.let { p ->
        AlertDialog(
            onDismissRequest = onCancelRestore,
            title = { Text("Restore this backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("restore_preview")) {
                    Text("Made ${p.madeAt}. It holds ${p.entriesInFile} entries.")
                    Text("${p.newEntries} entries are new to this phone and will be added" + if (p.otherNewItems > 0) ", along with ${p.otherNewItems} other items (categories, budgets, repeating expenses…)." else ".")
                    Text("${p.alreadyHereEntries} are already here and will be left exactly as they are. Nothing is overwritten or deleted.")
                }
            },
            confirmButton = { TextButton(onClick = onRestore, modifier = Modifier.testTag("restore_confirm")) { Text("Restore") } },
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

    Scaffold(topBar = { TopAppBar(title = { Text("Your data") }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("data_back")) { Text("Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (state.busy) CircularProgressIndicator(Modifier.testTag("data_busy"))
            state.status?.let {
                Text(it, color = if (state.statusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("data_status"))
            }

            Text("Spreadsheet", style = MaterialTheme.typography.titleMedium)
            Text("Every entry as a CSV file you can open in Excel or Google Sheets. Free, always.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { csvPicker.launch("expenses-$today.csv") }, enabled = !state.busy, modifier = Modifier.testTag("export_csv")) { Text("Export as CSV") }
            HorizontalDivider()

            Text("Backup", style = MaterialTheme.typography.titleMedium)
            Text(
                "Everything in one file: entries, categories, budgets, repeating expenses. Keep it somewhere safe, like your drive or email to yourself. " +
                    "Uninstalling the app deletes the data on this phone, so make a backup first.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            OutlinedButton(onClick = { backupPicker.launch("expenses-backup-$today.expbak") }, enabled = !state.busy && passProblem == null, modifier = Modifier.testTag("create_backup")) { Text("Create backup") }
            HorizontalDivider()

            Text("Restore", style = MaterialTheme.typography.titleMedium)
            Text(
                "Choose a backup file. It is checked first, and you see what would be added before anything changes. " +
                    "Restoring never creates duplicates and never changes what is already on this phone.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { openPicker.launch(arrayOf("*/*")) }, enabled = !state.busy, modifier = Modifier.testTag("open_backup")) { Text("Choose a backup file") }
        }
    }
}

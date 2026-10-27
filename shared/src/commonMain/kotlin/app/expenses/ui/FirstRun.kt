package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.expenses.core.domain.BackupNudge

/**
 * Shown in place of the empty list on a fresh install. One main action (add the first expense), a way in for people who already have data
 * (a bank statement, or a backup from another phone), and one honest sentence about where the data lives. No steps, no tutorial: it goes
 * away by itself with the first entry, or with Skip.
 */
@Composable
fun WelcomeCard(onAdd: () -> Unit, onImportOrRestore: () -> Unit, onSkip: () -> Unit) {
    // an item of Home's scrolling page, so Skip stays reachable on a short screen or with large text
    Column(Modifier.fillMaxWidth().padding(24.dp).testTag("welcome"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Welcome", style = MaterialTheme.typography.headlineSmall)
        Text("Add your first expense: an amount is all it takes. You can sort it into a category later.", style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().testTag("welcome_add")) { Text("Add my first expense") }
        OutlinedButton(onClick = onImportOrRestore, modifier = Modifier.fillMaxWidth().testTag("welcome_import")) { Text("I already have data: import or restore") }
        Text(
            "Everything stays on this phone. There is no account, and nothing is sent anywhere. That also means uninstalling the app deletes it, " +
                "so make a backup from More, then Your data, now and then.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("welcome_privacy"),
        )
        TextButton(onClick = onSkip, modifier = Modifier.testTag("welcome_skip")) { Text("Skip") }
    }
}

/** A single dismissible reminder, shown only when [BackupNudge] says so. */
@Composable
fun BackupNudgeCard(onBackUp: () -> Unit, onLater: () -> Unit) {
    // one slim bar: what and why on the left, the two answers on the right (it used to be a tall card with a paragraph)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer).padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp).testTag("backup_nudge"),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Back up your expenses", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("They live only on this phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Button(onClick = onBackUp, modifier = Modifier.testTag("nudge_backup")) { Text("Back up now") }
        TextButton(onClick = onLater, modifier = Modifier.testTag("nudge_later")) { Text("Later") }
    }
}

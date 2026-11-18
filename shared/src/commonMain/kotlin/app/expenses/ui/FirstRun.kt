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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.expenses.core.domain.BackupNudge

/**
 * Shown in place of the empty list on a fresh install. One main action (add the first expense), a way in for people who already have data (a bank statement, or a backup from another phone), an opt-in tour,
 * and two short, honest lines about where the data lives and what that means for a backup. No steps, no tutorial: it goes away by itself with the first entry, or with Skip. The Example month is drawn
 * under it (see [ExampleMonthBlock]), so Skip is on the first screen.
 */
@Composable
fun WelcomeCard(onAdd: () -> Unit, onImportOrRestore: () -> Unit, onSkip: () -> Unit, onTour: () -> Unit = {}) {
    // an item of Home's scrolling page, so Skip stays reachable on a short screen or with large text
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).testTag("welcome"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Let's note your first expense", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text("Type an amount. That's it. Sort it later.", style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().testTag("welcome_add")) { Text("Add my first expense") }
        OutlinedButton(onClick = onImportOrRestore, modifier = Modifier.fillMaxWidth().testTag("welcome_import")) { Text("I already have data") }
        TextButton(onClick = onTour, modifier = Modifier.fillMaxWidth().testTag("welcome_tour")) { Text("Show me around") }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Stays on this phone. No account. Nothing is sent anywhere.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("welcome_privacy"))
            Text(
                "Uninstalling the app deletes it, so back up now and then: More, then Your data.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("welcome_backup"),
            )
        }
        TextButton(onClick = onSkip, modifier = Modifier.testTag("welcome_skip")) { Text("Skip") }
    }
}

/** A single dismissible reminder, shown only when [BackupNudge] says so. */
@Composable
fun BackupNudgeCard(onBackUp: () -> Unit, onLater: () -> Unit) {
    // one slim bar: what and why on the left, the two answers on the right (it used to be a tall card with a paragraph). From 140% text the two buttons go under the words, as the nudge cards do:
    // beside them the words were squeezed to one word per line.
    val stacked = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f
    val words: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            Text("Back up your expenses", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text("They live only on this phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
    val buttons: @Composable () -> Unit = {
        Button(onClick = onBackUp, modifier = Modifier.testTag("nudge_backup")) { Text("Back up now") }
        TextButton(onClick = onLater, modifier = Modifier.testTag("nudge_later")) { Text("Later") }
    }
    val bar = Modifier.fillMaxWidth().padding(vertical = 6.dp).inlineCardSolid(MaterialTheme.colorScheme.primaryContainer).testTag("backup_nudge")
    if (stacked) Column(bar.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)) {
        words(Modifier)
        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) { buttons() }
    } else Row(bar.padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        words(Modifier.weight(1f))
        buttons()
    }
}

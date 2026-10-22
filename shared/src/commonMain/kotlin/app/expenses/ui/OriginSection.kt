package app.expenses.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.expenses.OriginUi

/**
 * "Where this came from": the original words an entry was read from (a text, a notification, an email notification, a statement line) and when they arrived.
 * Shown only for an entry that has an origin. Uses this package's [Text], so amounts in the words are hidden while "hide amounts" is on.
 */
@Composable
fun OriginSection(origin: OriginUi, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag("entry_origin"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Where this came from", style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("entry_origin_title"))
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(origin.kindLabel, style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("entry_origin_kind"))
                Text(origin.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("entry_origin_text"))
                if (origin.whenText.isNotEmpty()) Text(origin.whenText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("entry_origin_when"))
            }
        }
    }
}

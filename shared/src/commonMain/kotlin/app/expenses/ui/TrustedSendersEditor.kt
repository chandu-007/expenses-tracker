package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.expenses.core.parsers.TrustedSenders
import app.expenses.core.parsers.TrustedSenders.AddResult

/** What to tell the person after a try at adding [typed]; the three kinds of "no" are plain sentences, the yes names the sender. */
internal fun senderAddMessage(r: AddResult): String = when (r) {
    is AddResult.Added -> "Added ${r.name}"
    is AddResult.Duplicate -> "Already added"
    is AddResult.BuiltIn -> "${r.issuer} is already read"
    AddResult.Empty -> "Type a sender name first"
    AddResult.Invalid -> "Use 3 to 12 letters or digits, like MYBANK"
}

/**
 * The sender names the person added by hand: a box and an Add button, a line of feedback under them (inline, no snackbar, so it is there for a screen reader and
 * for the next look), and every saved name as a chip with its own remove button. [stored] is the saved setting value; [onSave] gets the whole new value.
 * Nothing is rescanned when a name goes: it only stops being read from then on.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun TrustedSendersEditor(stored: String, onSave: (String) -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    val names = TrustedSenders.parse(stored)
    fun submit() {
        when (val r = TrustedSenders.add(stored, typed)) {
            is AddResult.Added -> { onSave(r.stored); typed = ""; error = null; notice = senderAddMessage(r) }
            else -> { notice = null; error = senderAddMessage(r) }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            typed, { typed = it; error = null; notice = null },
            label = { Text("Sender name") }, singleLine = true, isError = error != null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth().testTag("capture_headers"),
        )
        OutlinedButton(onClick = { submit() }, modifier = Modifier.testTag("capture_headers_save")) { Text("Add") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("sender_input_error")) }
        notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("sender_added_notice")) }
        if (names.isNotEmpty()) {
            Text("Added by you", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                names.forEach { name ->
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.testTag("sender_chip_$name")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 12.dp))
                            IconButton(onClick = { onSave(TrustedSenders.remove(stored, name)); notice = null }, modifier = Modifier.size(48.dp).testTag("sender_remove_$name")) {
                                Icon(Icons.Outlined.Close, contentDescription = "Remove $name", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

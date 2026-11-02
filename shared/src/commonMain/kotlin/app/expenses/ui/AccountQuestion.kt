package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.expenses.AccountUi
import app.expenses.InboxRowUi

/** The kinds of account a waiting text can start: a bank account, or a credit card (its type cannot change once it has entries, so it is chosen here, not guessed). */
private val NEW_ACCOUNT_TYPES = listOf("BANK" to "Bank account", "CREDIT_CARD" to "Credit card")

/**
 * "Which account is this?" on a card in To review, shown while the account for the text is not known: the digits fit no account, fit several (the card says which), or the text prints none. One
 * tap on an account puts the text there AND remembers it for this bank and these digits, so the cards waiting from the same bank follow at once and later texts start with it (Add all and
 * swipe then take them). "New account ending 123" opens a small form (name and type filled in from the text) that makes the account with those digits and uses it.
 *
 * [onChoose] gets the account's id. [onCreate] gets the name and type and answers null, or why not (shown under the form).
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun AccountQuestion(
    r: InboxRowUi, accounts: List<AccountUi>,
    onChoose: (String) -> Unit, onCreate: (name: String, type: String, done: (String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var creating by rememberSaveable(r.id) { mutableStateOf(false) }
    var name by rememberSaveable(r.id) { mutableStateOf(r.newAccountName) }
    var type by rememberSaveable(r.id) { mutableStateOf(r.newAccountType) }
    var problem by remember(r.id) { mutableStateOf<String?>(null) }
    val offered = if (r.accountChoices.isEmpty()) accounts else accounts.filter { it.id in r.accountChoices }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f)).padding(12.dp).testTag("inbox_account_ask_${r.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Which account is this?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("inbox_account_title_${r.id}"))
        Text(r.accountAsk, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("inbox_account_why_${r.id}"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            offered.forEach { a -> FilterChip(selected = false, onClick = { onChoose(a.id) }, label = { Text(a.name) }, modifier = Modifier.testTag("inbox_pick_${r.id}_${a.id}")) }
            AssistChip(
                onClick = { creating = !creating },
                label = { Text(r.last4?.let { "New account ending $it" } ?: "New account") }, modifier = Modifier.testTag("inbox_new_account_${r.id}"),
            )
        }
        Text("I will remember this for ${r.issuer}${r.last4?.let { " ending $it" } ?: ""}, so the next texts need no question.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (creating) {
            OutlinedTextField(name, { name = it; problem = null }, label = { Text("Name of the new account") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("inbox_new_name_${r.id}"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NEW_ACCOUNT_TYPES.forEach { (key, label) -> FilterChip(selected = type == key, onClick = { type = key; problem = null }, label = { Text(label) }, modifier = Modifier.testTag("inbox_new_type_${r.id}_$key")) }
            }
            if (r.last4 != null) Text("It carries the digits ${r.last4}, so later texts find it by themselves.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("inbox_new_problem_${r.id}")) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onCreate(name, type) { p -> problem = p } }, enabled = name.isNotBlank(), modifier = Modifier.testTag("inbox_new_create_${r.id}")) { Text("Create and use it") }
                TextButton(onClick = { creating = false; problem = null }, modifier = Modifier.testTag("inbox_new_cancel_${r.id}")) { Text("Cancel") }
            }
        }
    }
}

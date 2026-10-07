package app.expenses.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.expenses.AccountUi
import app.expenses.CategoryUi

/** The selected entries as a list of ids, in the order they were picked. */
fun List<String>.toggled(id: String): List<String> = if (id in this) this - id else this + id

/**
 * What can be done to the entries selected by long-pressing a row: set a category, move to an account, delete. Anything that cannot take
 * the change (a split expense for a category, say) is skipped and the result says how many; one Undo takes the whole change back.
 */
@Composable
fun BulkBar(
    count: Int,
    categories: List<CategoryUi>,
    accounts: List<AccountUi>,
    onCategory: (String) -> Unit,
    onAccount: (String) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var menu by remember { mutableStateOf<String?>(null) }
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().testTag("bulk_bar")) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$count selected", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(end = 8.dp).testTag("bulk_count"))
            Box {
                TextButton(onClick = { menu = "category" }, modifier = Modifier.testTag("bulk_category")) { Text("Category") }
                DropdownMenu(expanded = menu == "category", onDismissRequest = { menu = null }) {
                    categories.filter { !it.hidden }.forEach { c -> DropdownMenuItem(text = { Text(displayName(categories, c)) }, onClick = { menu = null; onCategory(c.id) }, modifier = Modifier.testTag("bulk_cat_${c.id}")) }
                }
            }
            Box {
                TextButton(onClick = { menu = "account" }, modifier = Modifier.testTag("bulk_account")) { Text("Account") }
                DropdownMenu(expanded = menu == "account", onDismissRequest = { menu = null }) {
                    accounts.forEach { a -> DropdownMenuItem(text = { Text(a.name) }, onClick = { menu = null; onAccount(a.id) }, modifier = Modifier.testTag("bulk_acc_${a.name}")) }
                }
            }
            TextButton(onClick = onDelete, modifier = Modifier.testTag("bulk_delete")) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onCancel, modifier = Modifier.testTag("bulk_cancel")) { Text("Cancel") }
        }
    }
}

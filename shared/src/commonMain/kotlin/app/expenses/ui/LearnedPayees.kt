package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.expenses.CategoryUi
import app.expenses.LearnedUi

/**
 * "What the app has learned": the payees whose category the app now starts with because the person chose it, with how many times in a row, and what the person can do about each: change
 * the category, forget it, or file the earlier expenses of that payee that have no category yet (one change, with Undo). Nothing here is read from anyone but the person's own choices.
 */
internal fun LazyListScope.learnedSection(
    learned: List<LearnedUi>, categories: List<CategoryUi>,
    onForget: (String) -> Unit, onChange: (key: String, categoryId: String, done: (String?) -> Unit) -> Unit, onFile: (String) -> Unit,
) {
    item(key = "learned_head") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HorizontalDivider()
            Text("What the app has learned", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("learned_title"))
            Text(
                "When you choose a category for a payee (or, for money you receive, a payer) in To review, or change one later, the app starts with it next time. Your rules above still come first. Forget one to start again from the starter list.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (learned.isEmpty()) Text("Nothing yet. It fills in as you add things from To review.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("learned_empty"))
        }
    }
    items(learned, key = { "learned_" + it.key }) { l -> LearnedRow(l, categories, onForget, onChange, onFile) }
}

@Composable
private fun LearnedRow(l: LearnedUi, categories: List<CategoryUi>, onForget: (String) -> Unit, onChange: (String, String, (String?) -> Unit) -> Unit, onFile: (String) -> Unit) {
    var picking by remember(l.key) { mutableStateOf(false) }
    var problem by remember(l.key) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().testTag("learned_${l.key}"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(l.label, style = MaterialTheme.typography.bodyLarge)
        Text(
            (if (l.income) "Money in · " else "") + "→ ${l.categoryName}" + if (l.uses >= 2) " · chosen ${l.uses} times in a row" else " · chosen once",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("learned_what_${l.key}"),
        )
        problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { picking = true }, modifier = Modifier.testTag("learned_change_${l.key}")) { Text("Change") }
            TextButton(onClick = { onForget(l.key) }, modifier = Modifier.testTag("learned_forget_${l.key}")) { Text("Forget") }
            if (l.waiting > 0) OutlinedButton(onClick = { onFile(l.key) }, modifier = Modifier.testTag("learned_file_${l.key}")) {
                Text("File ${l.waiting} earlier ${if (l.income) (if (l.waiting == 1) "income" else "incomes") else (if (l.waiting == 1) "expense" else "expenses")} with no category under ${l.categoryName}")
            }
        }
    }
    if (picking) CategoryPickerDialog(offeredCategories(categories, income = l.income, keep = l.categoryId), l.categoryId, onPick = { id -> picking = false; onChange(l.key, id) { p -> problem = p } }, onClose = { picking = false })
}

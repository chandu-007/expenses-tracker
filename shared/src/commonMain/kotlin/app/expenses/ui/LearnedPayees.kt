package app.expenses.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
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
    learned: List<LearnedUi>, categories: List<CategoryUi>, open: Boolean, onToggle: () -> Unit,
    onForget: (String) -> Unit, onChange: (key: String, categoryId: String, done: (String?) -> Unit) -> Unit, onFile: (String) -> Unit,
    /** The names the person counts as one shop ("Same shop as..."), with how to make and remove them; null where the screen is without them. */
    shops: AliasControls? = null,
) {
    item(key = "learned_head") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).heightIn(min = 48.dp).testTag("learned_toggle"), verticalAlignment = Alignment.CenterVertically) {
                Text("What the app has learned" + if (learned.isEmpty()) "" else " (${learned.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).testTag("learned_title"))
                if (learned.isNotEmpty()) Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (open) "Fold the list" else "Open the list")
            }
            Text(
                "When you choose a category for a payee (or, for money you receive, a payer) in To review, or change one later, the app starts with it next time. Your rules above still come first. Forget one to start again from the starter list.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (learned.isEmpty()) Text("Nothing yet. When you choose a category for a shop in To review, the app remembers it here.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("learned_empty"))
        }
    }
    if (shops != null) item(key = "alias_block") { AliasBlock(shops, learned) }
    if (open) items(learned, key = { "learned_" + it.key }) { l -> LearnedRow(l, categories, onForget, onChange, onFile, learned, shops) }
}

@Composable
private fun LearnedRow(l: LearnedUi, categories: List<CategoryUi>, onForget: (String) -> Unit, onChange: (String, String, (String?) -> Unit) -> Unit, onFile: (String) -> Unit, all: List<LearnedUi>, shops: AliasControls?) {
    var joining by remember(l.key) { mutableStateOf(false) }
    var picking by remember(l.key) { mutableStateOf(false) }
    var problem by remember(l.key) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().testTag("learned_${l.key}"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(l.label, style = MaterialTheme.typography.bodyLarge)
        Text(
            (if (l.income) "Money in · " else "") + "→ ${l.categoryName}" + if (l.uses >= 2) " · chosen ${l.uses} times in a row" else " · chosen once",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("learned_what_${l.key}"),
        )
        if (l.sameAs.isNotEmpty()) Text("Counts as ${l.sameAs}: what is learned for it is used for this name too.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("learned_sameas_${l.key}"))
        problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { picking = true }, modifier = Modifier.testTag("learned_change_${l.key}")) { Text("Change") }
            TextButton(onClick = { onForget(l.key) }, modifier = Modifier.testTag("learned_forget_${l.key}")) { Text("Forget") }
            if (shops != null && !l.income) TextButton(onClick = { joining = true }, modifier = Modifier.testTag("learned_same_${l.key}")) { Text("Same shop as…") }
            if (l.waiting > 0) OutlinedButton(onClick = { onFile(l.key) }, modifier = Modifier.testTag("learned_file_${l.key}")) {
                Text("File ${l.waiting} earlier ${if (l.income) (if (l.waiting == 1) "income" else "incomes") else (if (l.waiting == 1) "expense" else "expenses")} with no category under ${l.categoryName}")
            }
        }
    }
    if (picking) CategoryPickerDialog(offeredCategories(categories, income = l.income, keep = l.categoryId), l.categoryId, onPick = { id -> picking = false; onChange(l.key, id) { p -> problem = p } }, onClose = { picking = false })
    if (joining && shops != null) SameShopDialog(l.label, all.filter { !it.income && it.key != l.key && it.sameAs.isEmpty() }.map { it.label }.distinct().take(SAME_SHOP_SUGGESTIONS), shops.onJoin, onClose = { joining = false })
}

/** How many other learned names the "Same shop as..." dialog offers as chips; the rest are typed. */
private const val SAME_SHOP_SUGGESTIONS = 6

/** The names the person counts as one shop, each with Remove, and the way to join two that are not tied to a learned row. Always shown when there is something to say, so a name that was never learned can be joined. */
@Composable
private fun AliasBlock(shops: AliasControls, learned: List<LearnedUi>) {
    var joining by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Names counted as one shop" + if (shops.aliases.isEmpty()) "" else " (${shops.aliases.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("alias_title"))
        if (shops.aliases.isEmpty()) Text("Swiggy and Swiggy Instamart are two names of one company. Count two names as one shop and what the app learns, a search, subscriptions and the Merchant report treat them as one. Your entries are not changed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("alias_empty"))
        shops.aliases.forEach { a -> AliasRow(a, shops.onRemove) }
        OutlinedButton(onClick = { joining = true }, modifier = Modifier.testTag("alias_add")) { Text("Count two names as one shop") }
    }
    if (joining) SameShopDialog(null, learned.filter { !it.income && it.sameAs.isEmpty() }.map { it.label }.distinct().take(SAME_SHOP_SUGGESTIONS), shops.onJoin, onClose = { joining = false })
}

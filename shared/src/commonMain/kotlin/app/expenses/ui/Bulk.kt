package app.expenses.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.expenses.AccountUi
import app.expenses.CategoryUi
import app.expenses.RowUi
import app.expenses.core.domain.CategoryChoices
import app.expenses.core.domain.CategoryKind

/** The selected entries as a list of ids, in the order they were picked. */
fun List<String>.toggled(id: String): List<String> = if (id in this) this - id else this + id

/** What the selected entries are, as far as a category goes: which kind of category fits all of them. */
enum class BulkSelection { EXPENSES, INCOME, MIXED }

/**
 * Whether the [selected] entries (looked up in [rows]) are all expenses, all income, or a mix. Transfers, refunds and the like take no category
 * and do not count; with nothing to go by the answer is [BulkSelection.MIXED], which offers every category.
 */
fun bulkSelectionOf(rows: List<RowUi>, selected: List<String>): BulkSelection {
    val ids = selected.toHashSet()
    var expenses = false; var income = false
    for (r in rows) {
        if (r.id !in ids) continue
        when (r.kind) { "EXPENSE" -> expenses = true; "INCOME" -> income = true }
    }
    return when { expenses && !income -> BulkSelection.EXPENSES; income && !expenses -> BulkSelection.INCOME; else -> BulkSelection.MIXED }
}

/**
 * What can be done to the entries selected by long-pressing a row (or from search, "Select all"): set a category, move to an account, tag, delete.
 * Anything that cannot take the change (a split expense for a category, income for an expense category, say) is skipped and the result says how
 * many; one Undo takes the whole change back. [selection] says what kind of entries are selected, so the category list can show which ones fit.
 */
@Composable
fun BulkBar(
    count: Int,
    categories: List<CategoryUi>,
    accounts: List<AccountUi>,
    onCategory: (String) -> Unit,
    onAccount: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    tags: List<String>,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    selection: BulkSelection = BulkSelection.MIXED,
    /** Makes the selected expenses transfers to the chosen account (a cash withdrawal, money moved to another account); null where the screen does not offer it. */
    onMakeTransfer: ((String) -> Unit)? = null,
) {
    var newTag by remember { mutableStateOf(false) }
    if (newTag) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newTag = false }, title = { Text("Add a tag to these entries") },
            text = { OutlinedTextField(text, { text = it }, label = { Text("Tag") }, singleLine = true, modifier = Modifier.testTag("bulk_tag_input")) },
            confirmButton = { TextButton(onClick = { onAddTag(text); newTag = false }, enabled = text.isNotBlank(), modifier = Modifier.testTag("bulk_tag_ok")) { Text("Add") } },
            dismissButton = { TextButton(onClick = { newTag = false }) { Text("Cancel") } },
        )
    }
    var menu by remember { mutableStateOf<String?>(null) }
    var pickingCategory by rememberSaveable { mutableStateOf(false) }
    if (pickingCategory) BulkCategoryDialog(categories, selection, onPick = { pickingCategory = false; onCategory(it) }, onClose = { pickingCategory = false })
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().testTag("bulk_bar")) {
        // two lines: the count with Cancel always in reach on the first, the actions on the second (one line ran past the edge on a narrow screen and hid Cancel)
        Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("$count selected", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = 4.dp).testTag("bulk_count"))
            TextButton(onClick = onCancel, modifier = Modifier.testTag("bulk_cancel")) { Text("Cancel") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = { pickingCategory = true }, modifier = Modifier.testTag("bulk_category")) { Text("Set category") }
            Box {
                TextButton(onClick = { menu = "account" }, modifier = Modifier.testTag("bulk_account")) { Text("Account") }
                DropdownMenu(expanded = menu == "account", onDismissRequest = { menu = null }) {
                    accounts.forEach { a -> DropdownMenuItem(text = { Text(a.name) }, onClick = { menu = null; onAccount(a.id) }, modifier = Modifier.testTag("bulk_acc_${a.name}")) }
                }
            }
            Box {
                TextButton(onClick = { menu = "tag" }, modifier = Modifier.testTag("bulk_tag")) { Text("Tag") }
                DropdownMenu(expanded = menu == "tag", onDismissRequest = { menu = null }) {
                    DropdownMenuItem(text = { Text("New tag…") }, onClick = { menu = null; newTag = true }, modifier = Modifier.testTag("bulk_tag_new"))
                    tags.take(10).forEach { t -> DropdownMenuItem(text = { Text("Add $t") }, onClick = { menu = null; onAddTag(t) }, modifier = Modifier.testTag("bulk_tag_add_$t")) }
                    tags.take(10).forEach { t -> DropdownMenuItem(text = { Text("Remove $t") }, onClick = { menu = null; onRemoveTag(t) }, modifier = Modifier.testTag("bulk_tag_remove_$t")) }
                }
            }
            // Delete comes before the rarer "Make transfer": the row scrolls sideways, and what runs past a ~360dp phone's edge must be the action used least, never Delete
            TextButton(onClick = onDelete, modifier = Modifier.testTag("bulk_delete")) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            if (onMakeTransfer != null && selection != BulkSelection.INCOME) Box {
                TextButton(onClick = { menu = "transfer" }, modifier = Modifier.testTag("bulk_transfer")) { Text("Make transfer") }
                DropdownMenu(expanded = menu == "transfer", onDismissRequest = { menu = null }) {
                    Text("To", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    accounts.forEach { a -> DropdownMenuItem(text = { Text(a.name) }, onClick = { menu = null; onMakeTransfer(a.id) }, modifier = Modifier.testTag("bulk_transfer_to_${a.name}")) }
                }
            }
        }
        }
    }
}

/** One line of the category list: [level] 1 is a subcategory, shown indented; [pickable] is false for a hidden parent that is shown only as a heading for its subcategories. */
private class PickRow(val category: CategoryUi, val level: Int, val label: String, val pickable: Boolean)

/** The categories a bulk change offers, in groups by what they are for. */
private enum class PickGroup(val heading: String, val tag: String, val kind: CategoryKind) {
    EXPENSE("Expense categories", "expense", CategoryKind.EXPENSE),
    INCOME("Income categories", "income", CategoryKind.INCOME),
    BOTH("Expense and income categories", "both", CategoryKind.BOTH),
}

/**
 * Every category that is not hidden, under a heading for expenses, for income (and, for older categories both have used, for both), a parent
 * with its subcategories indented. The group that does not fit what is selected is dimmed and cannot be picked. With more than twelve
 * categories a search box narrows them to one flat list of the names that match.
 */
@Composable
private fun BulkCategoryDialog(categories: List<CategoryUi>, selection: BulkSelection, onPick: (String) -> Unit, onClose: () -> Unit) {
    val offered = remember(categories) { categories.filter { !it.hidden } }
    val names = remember(categories) { categories.associate { it.id to displayName(categories, it) } }
    var typed by rememberSaveable { mutableStateOf("") }
    val matches = remember(offered, typed) { if (typed.isBlank()) null else CategoryChoices.search(offered.map { it.id to names.getValue(it.id) }, typed).toHashSet() }
    val groups = PickGroup.entries.mapNotNull { g ->
        val rows = if (matches != null) offered.filter { it.kind == g.kind && it.id in matches }.map { PickRow(it, 0, names.getValue(it.id), true) }
        else categories.filter { it.parentId == null && it.kind == g.kind }.flatMap { top ->
            val children = offered.filter { it.parentId == top.id }.map { PickRow(it, 1, it.name, true) }
            (if (!top.hidden) listOf(PickRow(top, 0, top.name, true)) else if (children.isNotEmpty()) listOf(PickRow(top, 0, top.name, false)) else emptyList()) + children
        }
        if (rows.isEmpty()) null else g to rows
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Set category") },
        text = {
            Column {
                if (offered.size > 12) OutlinedTextField(typed, { typed = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("bulk_category_search"))
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()).testTag("bulk_category_list")) {
                    groups.forEach { (g, rows) ->
                        val fits = when (g) {
                            PickGroup.EXPENSE -> selection != BulkSelection.INCOME
                            PickGroup.INCOME -> selection != BulkSelection.EXPENSES
                            PickGroup.BOTH -> true
                        }
                        val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        Text(g.heading, style = MaterialTheme.typography.labelLarge, color = if (fits) MaterialTheme.colorScheme.primary else dim, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp).testTag("bulk_category_heading_${g.tag}"))
                        if (!fits) Text(
                            if (g == PickGroup.INCOME) "The selected entries are expenses" else "The selected entries are income",
                            style = MaterialTheme.typography.bodySmall, color = dim, modifier = Modifier.testTag("bulk_category_note_${g.tag}"),
                        )
                        rows.forEach { r ->
                            val weight = if (r.level == 0 && typed.isBlank()) FontWeight.SemiBold else null
                            val color = if (fits && r.pickable) Color.Unspecified else dim
                            if (r.pickable) Text(
                                r.label, style = MaterialTheme.typography.bodyLarge, fontWeight = weight, color = color,
                                modifier = Modifier.fillMaxWidth().clickable(enabled = fits) { onPick(r.category.id) }.padding(start = if (r.level == 1) 24.dp else 0.dp, top = 12.dp, bottom = 12.dp).testTag("bulk_category_option_${r.category.id}"),
                            ) else Text(r.label, style = MaterialTheme.typography.bodyLarge, fontWeight = weight, color = dim, modifier = Modifier.padding(top = 12.dp, bottom = 12.dp))
                        }
                    }
                    if (groups.isEmpty()) Text("No category matches.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose, modifier = Modifier.testTag("bulk_category_close")) { Text("Cancel") } },
    )
}

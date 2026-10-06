package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.expenses.CategoryUi
import app.expenses.core.domain.Category
import app.expenses.core.domain.CategoryRules

/** Colours offered for categories. Optional: a category without one is simply shown without a dot. */
val CATEGORY_COLORS: List<Long> = listOf(0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF00ACC1, 0xFF1E88E5, 0xFF8E24AA, 0xFF6D4C41)

/** The shared rules need the plain domain type; the screens carry a little extra (usage). */
internal fun List<CategoryUi>.asDomain(): List<Category> = mapIndexed { i, c -> Category(c.id, c.name, c.parentId, c.colorArgb, i, c.hidden) }

/** How a category is named where there is little room: "Food › Cafe" for a subcategory. */
fun displayName(all: List<CategoryUi>, c: CategoryUi): String =
    c.parentId?.let { pid -> all.firstOrNull { it.id == pid }?.let { "${it.name} › ${c.name}" } } ?: c.name

@Composable
fun ColorDot(colorArgb: Long?, size: Int = 12) {
    if (colorArgb != null) Box(Modifier.size(size.dp).background(Color(colorArgb), CircleShape))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    categories: List<CategoryUi>,
    onBack: () -> Unit,
    onCreate: (name: String, parentId: String?, colorArgb: Long?) -> Unit,
    onUpdate: (id: String, name: String, colorArgb: Long?, hidden: Boolean, parentId: String?) -> Unit,
    onMove: (id: String, direction: Int) -> Unit,
    onMerge: (fromId: String, intoId: String) -> Unit,
    onDelete: (id: String) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val domain = categories.asDomain()

    val editedCategory = categories.firstOrNull { it.id == editing }
    if (editedCategory != null) {
        CategoryEditorScreen(
            category = editedCategory, categories = categories, domain = domain,
            onSave = { name, color, hidden, parent -> onUpdate(editedCategory.id, name, color, hidden, parent); editing = null },
            onMerge = { into -> onMerge(editedCategory.id, into); editing = null },
            onDelete = { onDelete(editedCategory.id); editing = null },
            onBack = { editing = null },
        )
        return
    }

    if (creating) {
        CategoryNameDialog(
            title = "New category",
            categories = categories,
            allowParent = true,
            onConfirm = { name, parent -> onCreate(name, parent, null); creating = false },
            onDismiss = { creating = false },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Categories") }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("categories_back")) { Text("Back") } }) },
        floatingActionButton = { FloatingActionButton(onClick = { creating = true }, modifier = Modifier.testTag("new_category")) { Text("+", style = MaterialTheme.typography.headlineMedium) } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(categories, key = { it.id }) { c ->
                val level = if (c.parentId == null) 0 else 1
                val siblings = categories.filter { it.parentId == c.parentId }
                val index = siblings.indexOfFirst { it.id == c.id }
                Row(
                    Modifier.fillMaxWidth().clickable { editing = c.id }.padding(start = (16 + 28 * level).dp, end = 8.dp, top = 6.dp, bottom = 6.dp).testTag("cat_row_${c.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ColorDot(c.colorArgb, 14)
                    if (c.colorArgb != null) Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        val note = buildList {
                            if (c.hidden) add("hidden")
                            add(if (c.usage == 0L) "not used yet" else "${c.usage} ${if (c.usage == 1L) "item" else "items"}")
                        }.joinToString(" · ")
                        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { onMove(c.id, -1) }, enabled = index > 0, modifier = Modifier.testTag("up_${c.id}").semantics { contentDescription = "Move ${c.name} up" }) { Text("▲") }
                    TextButton(onClick = { onMove(c.id, 1) }, enabled = index < siblings.lastIndex, modifier = Modifier.testTag("down_${c.id}").semantics { contentDescription = "Move ${c.name} down" }) { Text("▼") }
                }
                HorizontalDivider()
            }
        }
    }
}

/**
 * A short dialog that asks only for a name, so it stays above the keyboard. The name is checked as the user types, and the
 * keyboard's "done" key confirms. [allowParent] adds a one-line choice of parent, in a dropdown so the dialog stays short.
 */
@Composable
fun CategoryNameDialog(
    title: String,
    categories: List<CategoryUi>,
    allowParent: Boolean,
    onConfirm: (name: String, parentId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var parentId by rememberSaveable { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val domain = remember(categories) { categories.asDomain() }
    val problem = CategoryRules.checkName(domain, name, parentId)
    val canSave = name.isNotBlank() && problem == null
    val tops = categories.filter { it.parentId == null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true,
                    isError = name.isNotEmpty() && problem != null, supportingText = { if (name.isNotEmpty() && problem != null) Text(problem) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canSave) onConfirm(name, parentId) }),
                    modifier = Modifier.fillMaxWidth().testTag("cat_name"),
                )
                if (allowParent && tops.isNotEmpty()) {
                    Box {
                        OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("parent_menu")) {
                            Text("Inside: " + (tops.firstOrNull { it.id == parentId }?.name ?: "Top level"))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Top level") }, onClick = { parentId = null; menuOpen = false }, modifier = Modifier.testTag("parent_item_none"))
                            tops.forEach { t -> DropdownMenuItem(text = { Text(t.name) }, onClick = { parentId = t.id; menuOpen = false }, modifier = Modifier.testTag("parent_item_${t.id}")) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name, parentId) }, enabled = canSave, modifier = Modifier.testTag("cat_confirm")) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Edits one category on a full screen, like the expense form: the Save button is pinned above the keyboard and the rest
 * scrolls. (A dialog was tried first: it does not shrink for the keyboard, which pushed Save and Delete out of reach.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryEditorScreen(
    category: CategoryUi,
    categories: List<CategoryUi>,
    domain: List<Category>,
    onSave: (name: String, colorArgb: Long?, hidden: Boolean, parentId: String?) -> Unit,
    onMerge: (intoId: String) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable(category.id) { mutableStateOf(category.name) }
    var color by rememberSaveable(category.id) { mutableStateOf(category.colorArgb) }
    var hidden by rememberSaveable(category.id) { mutableStateOf(category.hidden) }
    var parentId by rememberSaveable(category.id) { mutableStateOf(category.parentId) }
    var merging by rememberSaveable(category.id) { mutableStateOf(false) }
    val nameError = CategoryRules.checkName(domain, name, parentId, excludeId = category.id)
    val parentError = if (parentId != category.parentId) CategoryRules.checkParent(domain, category.id, parentId) else null
    val deleteError = CategoryRules.checkDelete(domain, category.id, category.usage)
    val hasChildren = categories.any { it.parentId == category.id }

    if (merging) {
        AlertDialog(
            onDismissRequest = { merging = false },
            title = { Text("Merge \"${category.name}\" into…") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Everything in \"${category.name}\" moves to the category you choose, and \"${category.name}\" is removed. This can't be undone.")
                    categories.filter { CategoryRules.checkMerge(domain, category.id, it.id) == null }.forEach { target ->
                        TextButton(onClick = { onMerge(target.id) }, modifier = Modifier.fillMaxWidth().testTag("merge_into_${target.id}")) { Text(displayName(categories, target)) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { merging = false }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Edit category") }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("cat_cancel")) { Text("Cancel") } }) },
        bottomBar = {
            Button(
                onClick = { onSave(name, color, hidden, parentId) },
                enabled = nameError == null && parentError == null,
                modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("cat_save"),
            ) { Text("Save") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true,
                isError = nameError != null, supportingText = { if (nameError != null) Text(nameError) },
                modifier = Modifier.fillMaxWidth().testTag("cat_name"),
            )
            Text("Colour", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { color = null }, modifier = Modifier.testTag("swatch_none")) { Text(if (color == null) "None ✓" else "None") }
                CATEGORY_COLORS.forEachIndexed { i, c ->
                    Box(
                        Modifier.size(36.dp).background(Color(c), CircleShape)
                            .border(if (color == c) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { color = c }.testTag("swatch_$i").semantics { contentDescription = "Colour ${i + 1}" },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = hidden, onCheckedChange = { hidden = it }, modifier = Modifier.testTag("cat_hidden"))
                Spacer(Modifier.size(12.dp))
                Column { Text("Hidden"); Text("Not offered when adding an expense; existing entries keep it", style = MaterialTheme.typography.bodySmall) }
            }
            if (!hasChildren) {
                Text("Inside", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = parentId == null, onClick = { parentId = null }, label = { Text("Top level") }, modifier = Modifier.testTag("edit_parent_none"))
                    categories.filter { it.parentId == null && it.id != category.id }.forEach { t ->
                        FilterChip(selected = parentId == t.id, onClick = { parentId = t.id }, label = { Text(t.name) }, modifier = Modifier.testTag("edit_parent_${t.id}"))
                    }
                }
                if (parentError != null) Text(parentError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { merging = true }, modifier = Modifier.testTag("cat_merge")) { Text("Merge into…") }
                OutlinedButton(onClick = onDelete, enabled = deleteError == null, modifier = Modifier.testTag("cat_delete")) { Text("Delete") }
            }
            if (deleteError != null) Text(deleteError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("cat_delete_reason"))
        }
    }
}

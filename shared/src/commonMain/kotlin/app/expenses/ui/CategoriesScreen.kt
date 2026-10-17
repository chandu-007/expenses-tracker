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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.expenses.CategoryUi
import app.expenses.core.domain.Category
import app.expenses.core.domain.CategoryKind
import app.expenses.core.domain.CategoryRules

/** Colours offered for categories. Optional: a category without one is simply shown without a dot. */
val CATEGORY_COLORS: List<Long> = listOf(0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF00ACC1, 0xFF1E88E5, 0xFF8E24AA, 0xFF6D4C41)

/** The shared rules need the plain domain type; the screens carry a little extra (usage). */
internal fun List<CategoryUi>.asDomain(): List<Category> = mapIndexed { i, c -> Category(c.id, c.name, c.parentId, c.colorArgb, i, c.hidden, c.kind) }

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
    onCreate: (name: String, parentId: String?, colorArgb: Long?, kind: CategoryKind) -> Unit,
    onUpdate: (id: String, name: String, colorArgb: Long?, hidden: Boolean, parentId: String?) -> Unit,
    onMove: (id: String, direction: Int) -> Unit,
    onMerge: (fromId: String, intoId: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onSetKind: (id: String, kind: CategoryKind, onDone: (String?) -> Unit) -> Unit = { _, _, done -> done(null) },
    onAddSuggested: (Map<String, Set<String>>) -> Unit = {},
    /** Names the person told the app to stop offering a category for: (key, name). */
    stopped: List<Pair<String, String>> = emptyList(),
    onAskAgain: (String) -> Unit = {},
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var suggesting by rememberSaveable { mutableStateOf(false) }
    var incomeTab by rememberSaveable { mutableStateOf(false) }
    if (suggesting) {
        PlatformBackHandler(true) { suggesting = false }
        SuggestedCategoriesScreen(categories, onAdd = { onAddSuggested(it); suggesting = false }, onBack = { suggesting = false })
        return
    }
    val domain = categories.asDomain()

    val editedCategory = categories.firstOrNull { it.id == editing }
    if (editedCategory != null) {
        CategoryEditorScreen(
            category = editedCategory, categories = categories, domain = domain,
            onSave = { name, color, hidden, parent -> onUpdate(editedCategory.id, name, color, hidden, parent); editing = null },
            onMerge = { into -> onMerge(editedCategory.id, into); editing = null },
            onDelete = { onDelete(editedCategory.id); editing = null },
            onSetKind = { kind, done -> onSetKind(editedCategory.id, kind, done) },
            onBack = { editing = null },
        )
        return
    }

    if (creating) {
        CategoryNameDialog(
            title = "New category",
            categories = categories,
            allowParent = true,
            tabIncome = incomeTab,
            onConfirm = { name, parent -> onCreate(name, parent, null, if (incomeTab) CategoryKind.INCOME else CategoryKind.EXPENSE); creating = false },
            onDismiss = { creating = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("categories_back")) { Text("Back") } },
                actions = { if (!incomeTab) TextButton(onClick = { suggesting = true }, modifier = Modifier.testTag("suggested_categories")) { Text("Suggested") } },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { creating = true }, modifier = Modifier.testTag("new_category")) { Text("+", style = MaterialTheme.typography.headlineMedium) } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
        KindTabs(incomeTab = incomeTab, onSelect = { incomeTab = it })
        val shown = categories.filter { it.kind.serves(incomeTab) }
        LazyColumn(Modifier.fillMaxSize().testTag("categories_list"), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(shown, key = { it.id }) { c ->
                val level = if (c.parentId == null) 0 else 1
                val siblings = shown.filter { it.parentId == c.parentId }
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
                            if (c.kind == CategoryKind.BOTH) add("both")
                            add(if (c.usage == 0L) "not used yet" else "${c.usage} ${if (c.usage == 1L) "item" else "items"}")
                        }.joinToString(" · ")
                        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { onMove(c.id, -1) }, enabled = index > 0, modifier = Modifier.testTag("up_${c.id}").semantics { contentDescription = "Move ${c.name} up" }) { Text("▲") }
                    TextButton(onClick = { onMove(c.id, 1) }, enabled = index < siblings.lastIndex, modifier = Modifier.testTag("down_${c.id}").semantics { contentDescription = "Move ${c.name} down" }) { Text("▼") }
                }
                HorizontalDivider()
            }
            if (stopped.isNotEmpty()) {
                item(key = "stopped_heading") {
                    Column(Modifier.padding(16.dp, 20.dp, 16.dp, 4.dp)) {
                        Text("Suggestions turned off", style = MaterialTheme.typography.titleSmall)
                        Text("After you file an entry, the app offers the same category to earlier entries with the same name. For these names you chose \"Stop asking\".", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(stopped, key = { "stopped_${it.first}" }) { (key, name) ->
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 80.dp).testTag("similar_off_$key"), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
                        TextButton(onClick = { onAskAgain(key) }, modifier = Modifier.testTag("similar_ask_again_$key")) { Text("Ask again") }
                    }
                    HorizontalDivider()
                }
            }
        }
        }
    }
}

/** The two lists of categories. A simple pair of segments; the selected one is filled. */
@Composable
private fun KindTabs(incomeTab: Boolean, onSelect: (Boolean) -> Unit) {
    val indicator = androidx.compose.animation.core.animateFloatAsState(if (incomeTab) 1f else 0f, Motion.spring(), label = "catTab")
    Box(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(24.dp))) {
            listOf(false to "Expenses", true to "Income").forEach { (inc, label) ->
                val on = incomeTab == inc
                Box(
                    Modifier.weight(1f).padding(3.dp)
                        .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(21.dp))
                        .clickable { onSelect(inc) }.padding(vertical = 10.dp)
                        .testTag(if (inc) "cat_tab_income" else "cat_tab_expense")
                        .semantics { selected = on },
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge) }
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
    /** When set, only top-level categories of this list's kind are offered as parent (false = expenses, true = income). */
    tabIncome: Boolean? = null,
    onConfirm: (name: String, parentId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var parentId by rememberSaveable { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val domain = remember(categories) { categories.asDomain() }
    val problem = CategoryRules.checkName(domain, name, parentId)
    val canSave = name.isNotBlank() && problem == null
    val tops = categories.filter { it.parentId == null && (tabIncome == null || it.kind.serves(tabIncome)) }
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
    onSetKind: (kind: CategoryKind, onDone: (String?) -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    var kindProblem by remember(category.id) { mutableStateOf<String?>(null) }
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
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).testTag("cat_save"),
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
            if (category.parentId == null) {
                Text("Used for", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(CategoryKind.EXPENSE to "Expenses", CategoryKind.INCOME to "Income", CategoryKind.BOTH to "Both").forEach { (k, label) ->
                        FilterChip(selected = category.kind == k, onClick = { if (category.kind != k) onSetKind(k) { kindProblem = it } else kindProblem = null }, label = { Text(label) }, modifier = Modifier.testTag("cat_kind_${k.name}"))
                    }
                }
                Text("A category can change only while nothing of the other kind uses it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                kindProblem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("cat_kind_problem")) }
            } else {
                Text("Follows ${categories.firstOrNull { it.id == category.parentId }?.name ?: "its parent"}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("cat_kind_follows"))
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

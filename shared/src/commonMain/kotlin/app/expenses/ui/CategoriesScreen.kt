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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material.icons.outlined.DragHandle
import app.expenses.core.domain.formatted
import app.expenses.CategoryUi
import app.expenses.core.domain.Category
import app.expenses.core.domain.CategoryKind
import app.expenses.core.domain.CategoryRules

/** Colours offered for categories. Optional: a category without one is simply shown without a dot. */
/** The colours offered for a category: the 12 slot hues (UI S3), the first seven being the colour-blind-safe Okabe-Ito ones. "None" keeps the slot the app gives it. */
val CATEGORY_COLORS: List<Long> = app.expenses.core.domain.CategoryLook.SLOT_RGB.map { 0xFF000000L or it.toLong() }

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
    onUpdate: (id: String, name: String, colorArgb: Long?, hidden: Boolean, parentId: String?, glyph: String?) -> Unit,
    onMove: (id: String, direction: Int) -> Unit,
    onMerge: (fromId: String, intoId: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onSetKind: (id: String, kind: CategoryKind, onDone: (String?) -> Unit) -> Unit = { _, _, done -> done(null) },
    onAddSuggested: (Map<String, Set<String>>) -> Unit = {},
    /** Names the person told the app to stop offering a category for: (key, name). */
    stopped: List<Pair<String, String>> = emptyList(),
    onAskAgain: (String) -> Unit = {},
    /** True once for a ledger that had categories before they got glyphs and colours (UI S3): a line at the top says so until the person taps "Got it". */
    lookNotice: Boolean = false,
    onLookNoticeSeen: () -> Unit = {},
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
            onSave = { name, color, hidden, parent, glyph -> onUpdate(editedCategory.id, name, color, hidden, parent, glyph); editing = null },
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
            ScreenHeader(
                "Categories", onBack, "categories_back",
                actions = { if (!incomeTab) TextButton(onClick = { suggesting = true }, modifier = Modifier.testTag("suggested_categories")) { Text("Suggested") } },
            )
        },
        floatingActionButton = { androidx.compose.material3.ExtendedFloatingActionButton(onClick = { creating = true }, modifier = Modifier.testTag("new_category"), icon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Add, contentDescription = null) }, text = { Text("New category") }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
        TipSlot("categories")
        if (lookNotice) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).inlineCard(MaterialTheme.colorScheme.primary).padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp).testTag("look_notice"), verticalAlignment = Alignment.CenterVertically) {
            Text("New look: every category now has an icon and a colour of its own. Tap a category to change them.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onLookNoticeSeen, modifier = Modifier.heightIn(min = 48.dp).testTag("look_notice_ok")) { Text("Got it") }
        }
        KindTabs(incomeTab = incomeTab, onSelect = { incomeTab = it })
        val shown = categories.filter { it.kind.serves(incomeTab) }
        // this period's spend per category (UI S5): the mini bars are relative to the biggest one on the page; colours differ within the page ([CategoryLook.chartHues])
        val biggest = shown.maxOfOrNull { it.spentMinor } ?: 0L
        val hues = remember(shown) { app.expenses.core.domain.CategoryLook.chartHues(shown.map { it.id }, shown.map { c -> c.colorArgb?.let { (it and 0xFFFFFF).toInt() } }) }
        val feel = rememberFeel()
        // where each row is on the screen, for a drag: read only when a finger is down (a plain map, not state)
        val bounds = remember { HashMap<String, Pair<Float, Float>>() }
        var dragging by remember { mutableStateOf<String?>(null) }
        var dragDy by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
        LazyColumn(Modifier.fillMaxSize().testTag("categories_list"), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(shown, key = { it.id }) { c ->
                val level = if (c.parentId == null) 0 else 1
                val siblings = shown.filter { it.parentId == c.parentId }
                val index = siblings.indexOfFirst { it.id == c.id }
                val lifted = dragging == c.id
                val stacked = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f
                val rowModifier =
                    Modifier.fillMaxWidth()
                        .onGloballyPositioned { bounds[c.id] = it.positionInRoot().y to it.size.height.toFloat() }
                        .zIndex(if (lifted) 1f else 0f)
                        .graphicsLayer { translationY = if (lifted) dragDy else 0f; shadowElevation = if (lifted) 12f else 0f }
                        .background(if (lifted) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                        .clickable { editing = c.id }.padding(start = (16 + 28 * level).dp, end = 4.dp, top = 6.dp, bottom = 6.dp).testTag("cat_row_${c.id}")
                val info: @Composable () -> Unit = {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        val note = buildList {
                            if (c.hidden) add("hidden")
                            if (c.kind == CategoryKind.BOTH) add("both")
                            if (c.spentMinor > 0 && !incomeTab) add(app.expenses.core.domain.Money(c.spentMinor, app.expenses.core.domain.BaseCurrency.current).formatted() + " this period")
                            add(if (c.usage == 0L) "not used yet" else "${c.usage} ${if (c.usage == 1L) "item" else "items"}")
                        }.joinToString(" · ")
                        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("cat_note_${c.id}"))
                        if (!incomeTab && c.spentMinor > 0 && biggest > 0 && !LocalHideAmounts.current) {
                            val hue = rgbColor(hues[shown.indexOf(c)])
                            Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(4.dp).clip(androidx.compose.foundation.shape.CircleShape).background(hue.copy(alpha = 0.18f)).testTag("cat_bar_${c.id}")) {
                                Box(Modifier.fillMaxWidth((c.spentMinor.toFloat() / biggest).coerceIn(0.02f, 1f)).fillMaxHeight().background(hue, androidx.compose.foundation.shape.CircleShape))
                            }
                        }
                    }
                }
                val controls: @Composable () -> Unit = {
                    // the handle: press and hold, then drag to another place among the same level's categories (the arrows stay: a drag is not for everyone, WCAG 2.5.7)
                    Box(
                        Modifier.size(44.dp).testTag("drag_${c.id}").semantics { contentDescription = "Drag to move ${c.name}, or use the arrows" }
                            .pointerInput(c.id, index, siblings.size) {
                                var last = index
                                fun target(): Int {
                                    val me = bounds[c.id] ?: return index
                                    val centre = me.first + me.second / 2f + dragDy
                                    var best = index; var bestD = Float.MAX_VALUE
                                    siblings.forEachIndexed { i, sib -> bounds[sib.id]?.let { (top, h) -> val d = kotlin.math.abs(top + h / 2f - centre); if (d < bestD) { bestD = d; best = i } } }
                                    return best
                                }
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragging = c.id; dragDy = 0f; last = index; feel(Feel.LONG_PRESS) },
                                    onDrag = { change, amount -> change.consume(); dragDy += amount.y; val t = target(); if (t != last) { last = t; feel(Feel.STEP) } },
                                    onDragEnd = { val steps = target() - index; dragging = null; dragDy = 0f; repeat(kotlin.math.abs(steps)) { onMove(c.id, if (steps < 0) -1 else 1) } },
                                    onDragCancel = { dragging = null; dragDy = 0f },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.DragHandle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(onClick = { onMove(c.id, -1) }, enabled = index > 0, contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.widthIn(min = 40.dp).testTag("up_${c.id}").semantics { contentDescription = "Move ${c.name} up" }) { Text("▲") }
                    TextButton(onClick = { onMove(c.id, 1) }, enabled = index < siblings.lastIndex, contentPadding = PaddingValues(horizontal = 6.dp), modifier = Modifier.widthIn(min = 40.dp).testTag("down_${c.id}").semantics { contentDescription = "Move ${c.name} down" }) { Text("▼") }
                }
                // from 140% text the handle and the arrows go under the words: beside them the note had room for a word a line
                if (stacked) Column(rowModifier) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryTilePreview(c.id, c.name, c.colorArgb, c.glyph, size = 36.dp, modifier = Modifier.padding(end = 12.dp))
                        info()
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) { controls() }
                } else Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
                    CategoryTilePreview(c.id, c.name, c.colorArgb, c.glyph, size = 36.dp, modifier = Modifier.padding(end = 12.dp))
                    info()
                    controls()
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
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, Radius.M)) {
            listOf(false to "Expenses", true to "Income").forEach { (inc, label) ->
                val on = incomeTab == inc
                Box(
                    Modifier.weight(1f).padding(3.dp)
                        .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent, Radius.M)
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
    onSave: (name: String, colorArgb: Long?, hidden: Boolean, parentId: String?, glyph: String?) -> Unit,
    onMerge: (intoId: String) -> Unit,
    onDelete: () -> Unit,
    onSetKind: (kind: CategoryKind, onDone: (String?) -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    var kindProblem by remember(category.id) { mutableStateOf<String?>(null) }
    var name by rememberSaveable(category.id) { mutableStateOf(category.name) }
    var color by rememberSaveable(category.id) { mutableStateOf(category.colorArgb) }
    var glyph by rememberSaveable(category.id) { mutableStateOf(category.glyph) }
    var hidden by rememberSaveable(category.id) { mutableStateOf(category.hidden) }
    var parentId by rememberSaveable(category.id) { mutableStateOf(category.parentId) }
    var merging by rememberSaveable(category.id) { mutableStateOf(false) }
    // the category picked in the merge list, asked about once more before anything moves (a wrong tap in a list of names cannot be taken back)
    var mergeTarget by rememberSaveable(category.id) { mutableStateOf<String?>(null) }
    val nameError = CategoryRules.checkName(domain, name, parentId, excludeId = category.id)
    val parentError = if (parentId != category.parentId) CategoryRules.checkParent(domain, category.id, parentId) else null
    val deleteError = CategoryRules.checkDelete(domain, category.id, category.usage)
    val hasChildren = categories.any { it.parentId == category.id }

    val confirming = mergeTarget?.let { id -> categories.firstOrNull { it.id == id } }
    if (merging && confirming != null) {
        AlertDialog(
            onDismissRequest = { mergeTarget = null },
            title = { Text("Merge into \"${displayName(categories, confirming)}\"?") },
            text = { Text("Everything in \"${category.name}\" moves to \"${displayName(categories, confirming)}\", and \"${category.name}\" is removed. This can't be undone.", modifier = Modifier.testTag("merge_confirm_text")) },
            confirmButton = { TextButton(onClick = { onMerge(confirming.id) }, modifier = Modifier.testTag("merge_confirm")) { Text("Merge") } },
            dismissButton = { TextButton(onClick = { mergeTarget = null }, modifier = Modifier.testTag("merge_back")) { Text("Choose another") } },
        )
    } else if (merging) {
        AlertDialog(
            onDismissRequest = { merging = false },
            title = { Text("Merge \"${category.name}\" into…") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Everything in \"${category.name}\" moves to the category you choose, and \"${category.name}\" is removed. This can't be undone.")
                    categories.filter { CategoryRules.checkMerge(domain, category.id, it.id) == null }.forEach { target ->
                        TextButton(onClick = { mergeTarget = target.id }, modifier = Modifier.fillMaxWidth().testTag("merge_into_${target.id}")) { Text(displayName(categories, target)) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { merging = false }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = { ScreenHeader("Edit category", onBack, "cat_cancel", backLabel = "Cancel") },
        bottomBar = {
            Button(
                onClick = { onSave(name, color, hidden, parentId, glyph) },
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
            // the tile as it will look, with the glyph and colour chosen below
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryTilePreview(category.id, name.ifBlank { category.name }, color, glyph)
                Text("How it looks in lists", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
            Text("Icon", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { glyph = null }, modifier = Modifier.testTag("glyph_auto")) { Text(if (glyph == null) "Automatic ✓" else "Automatic") }
                app.expenses.core.domain.CategoryLook.GLYPHS.forEach { g ->
                    Box(
                        Modifier.size(48.dp).clip(Radius.S).background(if (glyph == g) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .border(if (glyph == g) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, Radius.S)
                            .selectable(selected = glyph == g, role = androidx.compose.ui.semantics.Role.RadioButton, onClick = { glyph = g })
                            .testTag("glyph_$g").semantics { contentDescription = g },
                        contentAlignment = Alignment.Center,
                    ) { Icon(categoryGlyph(g), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp)) }
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

/** A category's tile drawn from its own parts (before they are saved): the glyph the person chose or the one its name suggests, on the colour they chose or its slot. */
@Composable
internal fun CategoryTilePreview(id: String, name: String, colorArgb: Long?, glyph: String?, size: androidx.compose.ui.unit.Dp = 48.dp, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tile = remember(id, colorArgb, dark) { if (colorArgb != null) app.expenses.core.domain.CategoryLook.tileOfHue((colorArgb and 0xFFFFFF).toInt(), dark) else app.expenses.core.domain.CategoryLook.tileFor(app.expenses.core.domain.CategoryLook.slot(id), dark) }
    Box(modifier.size(size).clip(if (size >= 36.dp) Radius.M else Radius.S).background(rgbColor(tile.container)).testTag("cat_tile_$id"), contentAlignment = Alignment.Center) {
        Icon(categoryGlyph(glyph ?: app.expenses.core.domain.CategoryLook.defaultGlyph(name)), contentDescription = null, tint = rgbColor(tile.glyph), modifier = Modifier.size(size * 0.56f))
    }
}

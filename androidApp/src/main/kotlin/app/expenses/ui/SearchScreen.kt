package app.expenses.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.expenses.CategoryUi
import app.expenses.RowUi
import app.expenses.SearchInput
import app.expenses.SearchKind
import app.expenses.SearchRange
import app.expenses.SearchUi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    tabBar: @Composable () -> Unit = {},
    state: SearchUi,
    categories: List<CategoryUi>,
    onChange: (SearchInput) -> Unit,
    onBack: () -> Unit,
    onOpen: (RowUi) -> Unit,
    accounts: List<app.expenses.AccountUi>,
    onBulkCategory: (List<String>, String) -> Unit,
    onBulkAccount: (List<String>, String) -> Unit,
    onBulkDelete: (List<String>) -> Unit,
    tags: List<String>,
    onBulkAddTag: (List<String>, String) -> Unit,
    onBulkRemoveTag: (List<String>, String) -> Unit,
    onUndo: (app.expenses.UndoUi) -> Unit,
    saved: List<app.expenses.SavedSearchUi>,
    onSaveSearch: (String, SearchInput, (String?) -> Unit) -> Unit,
    onDeleteSaved: (String) -> Unit,
) {
    var savingName by rememberSaveable { mutableStateOf<String?>(null) }
    var savingProblem by remember { mutableStateOf<String?>(null) }
    var deletingSaved by remember { mutableStateOf<app.expenses.SavedSearchUi?>(null) }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    androidx.activity.compose.BackHandler(enabled = selected.isNotEmpty()) { selected = emptyList() }
    var text by rememberSaveable { mutableStateOf("") }
    var min by rememberSaveable { mutableStateOf("") }
    var max by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var kind by rememberSaveable { mutableStateOf(SearchKind.ANY) }
    var range by rememberSaveable { mutableStateOf(SearchRange.ANY) }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    var tagMenu by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun changed(t: String = text, lo: String = min, hi: String = max, c: String? = categoryId, k: SearchKind = kind, r: SearchRange = range, g: String? = tag) {
        text = t; min = lo; max = hi; categoryId = c; kind = k; range = r; tag = g
        onChange(SearchInput(t, lo, hi, c, k, r, g))
    }
    fun changedAll(i: SearchInput) = changed(t = i.text, lo = i.minRupees, hi = i.maxRupees, c = i.categoryId, k = i.kind, r = i.range, g = i.tag)
    // The screen may be re-created (rotation) with its inputs restored: search again so the results match what is shown.
    LaunchedEffect(Unit) { if (text.isNotEmpty() || min.isNotEmpty() || max.isNotEmpty() || categoryId != null || kind != SearchKind.ANY || range != SearchRange.ANY || tag != null) onChange(SearchInput(text, min, max, categoryId, kind, range, tag)) }

    savingName?.let { n ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { savingName = null; savingProblem = null }, title = { Text("Save this search") },
            text = {
                Column {
                    OutlinedTextField(n, { savingName = it; savingProblem = null }, label = { Text("Name") }, singleLine = true, modifier = Modifier.testTag("saved_name"))
                    savingProblem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("saved_problem")) }
                }
            },
            confirmButton = { TextButton(onClick = { onSaveSearch(n, SearchInput(text, min, max, categoryId, kind, range, tag)) { p -> if (p == null) { savingName = null; savingProblem = null } else savingProblem = p } }, modifier = Modifier.testTag("saved_ok")) { Text("Save") } },
            dismissButton = { TextButton(onClick = { savingName = null; savingProblem = null }) { Text("Cancel") } },
        )
    }
    deletingSaved?.let { d ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deletingSaved = null }, title = { Text("Remove \"${d.name}\"?") }, text = { Text("Only the saved search goes. No entries are touched.") },
            confirmButton = { TextButton(onClick = { onDeleteSaved(d.id); deletingSaved = null }, modifier = Modifier.testTag("saved_delete_ok")) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { deletingSaved = null }) { Text("Keep") } },
        )
    }
    Scaffold(
        bottomBar = {
            if (selected.isNotEmpty()) BulkBar(
                selected.size, categories, accounts,
                onCategory = { c -> onBulkCategory(selected, c); selected = emptyList() },
                onAccount = { a -> onBulkAccount(selected, a); selected = emptyList() },
                onAddTag = { t -> onBulkAddTag(selected, t); selected = emptyList() },
                onRemoveTag = { t -> onBulkRemoveTag(selected, t); selected = emptyList() },
                tags = tags,
                onDelete = { onBulkDelete(selected); selected = emptyList() },
                onCancel = { selected = emptyList() },
            )
            else tabBar()
        },
        topBar = { TopAppBar(title = { Text("Search") }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("search_back")) { Text("Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = text, onValueChange = { changed(t = it) }, label = { Text("Merchant or note") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus).testTag("search_text"),
            )
            if (saved.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                saved.forEach { s ->
                    LongPressChip(label = s.name, onClick = { changedAll(s.input) }, onLongClick = { deletingSaved = s }, modifier = Modifier.testTag("saved_${s.name}"))
                }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = min, onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) changed(lo = v) }, label = { Text("From ₹") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f).testTag("search_min"))
                OutlinedTextField(value = max, onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) changed(hi = v) }, label = { Text("To ₹") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f).testTag("search_max"))
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    FilterChip(selected = categoryId != null, onClick = { menu = true }, label = { Text(categories.firstOrNull { it.id == categoryId }?.let { displayName(categories, it) } ?: "Any category") }, modifier = Modifier.testTag("search_category"))
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Any category") }, onClick = { menu = false; changed(c = null) }, modifier = Modifier.testTag("search_cat_any"))
                        categories.forEach { c -> DropdownMenuItem(text = { Text(displayName(categories, c)) }, onClick = { menu = false; changed(c = c.id) }, modifier = Modifier.testTag("search_cat_${c.id}")) }
                    }
                }
                if (tags.isNotEmpty() || tag != null) Box {
                    FilterChip(selected = tag != null, onClick = { tagMenu = true }, label = { Text(tag?.let { "Tag: $it" } ?: "Any tag") }, modifier = Modifier.testTag("search_tag"))
                    DropdownMenu(expanded = tagMenu, onDismissRequest = { tagMenu = false }) {
                        DropdownMenuItem(text = { Text("Any tag") }, onClick = { tagMenu = false; changed(g = null) }, modifier = Modifier.testTag("search_tag_any"))
                        tags.forEach { t -> DropdownMenuItem(text = { Text(t) }, onClick = { tagMenu = false; changed(g = t) }, modifier = Modifier.testTag("search_tag_$t")) }
                    }
                }
                FilterChip(selected = kind == SearchKind.EXPENSE, onClick = { changed(k = if (kind == SearchKind.EXPENSE) SearchKind.ANY else SearchKind.EXPENSE) }, label = { Text("Expenses") }, modifier = Modifier.testTag("search_kind_expense"))
                FilterChip(selected = kind == SearchKind.INCOME, onClick = { changed(k = if (kind == SearchKind.INCOME) SearchKind.ANY else SearchKind.INCOME) }, label = { Text("Income") }, modifier = Modifier.testTag("search_kind_income"))
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(SearchRange.LAST_30_DAYS to "Last 30 days", SearchRange.THIS_PERIOD to "This period", SearchRange.THIS_YEAR to "This year").forEach { (r, label) ->
                    FilterChip(selected = range == r, onClick = { changed(r = if (range == r) SearchRange.ANY else r) }, label = { Text(label) }, modifier = Modifier.testTag("search_range_${r.name}"))
                }
            }
            if (state.hasConditions && state.problem == null) TextButton(onClick = { savingName = "" }, modifier = Modifier.padding(horizontal = 8.dp).testTag("save_search")) { Text("Save this search") }
            HorizontalDivider()
            when {
                state.problem != null -> Text(state.problem, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp).testTag("search_problem"))
                !state.hasConditions -> SearchEmpty("Type a merchant or note, or choose an amount, category or dates.", "search_hint")
                state.searching -> Text("Searching…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp).testTag("search_working"))
                state.results.isEmpty() -> SearchEmpty("Nothing found.", "search_none")
                else -> {
                    state.notice?.let { n ->
                        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(n, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).testTag("search_notice"))
                            state.undo?.let { u -> TextButton(onClick = { onUndo(u) }, modifier = Modifier.testTag("search_undo")) { Text("Undo") } }
                        }
                    }
                    Text(
                        "${state.results.size} ${if (state.results.size == 1) "result" else "results"}" + if (state.truncated) ", the most recent. Narrow the search to see others." else "",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("search_count"),
                    )
                    LazyColumn(Modifier.fillMaxSize().testTag("search_results"), contentPadding = PaddingValues(bottom = 24.dp)) {
                        itemsIndexed(state.results, key = { _, it -> it.seq }) { index, row ->
                            val isSelected = row.id in selected
                            val pos = CardPos.of(first = index == 0, last = index == state.results.lastIndex)
                            Row(
                                Modifier.animateItem().entryCardClip(pos).fillMaxWidth().entryCardFill(pos, isSelected)
                                    .combinedClickable(onClick = { if (selected.isNotEmpty()) selected = selected.toggled(row.id) else onOpen(row) }, onLongClick = { selected = selected.toggled(row.id) })
                                    .padding(horizontal = 16.dp, vertical = rowPadding(12.dp)).testTag("result_${row.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (selected.isNotEmpty()) androidx.compose.material3.Checkbox(checked = isSelected, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp)) else CategoryAvatar(row.category, row.title, Modifier.padding(end = 14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                    Text(row.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                                val flow = flowOf(row.kind, row.inflow)
                                Text(signedAmount(row.amount, flow), style = MaterialTheme.typography.bodyLarge, color = amountColor(flow))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The hint or "nothing found" line under a small picture; scrolls if the keyboard leaves little room, so the text is always reachable. */
@Composable
private fun SearchEmpty(text: String, tag: String) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyArt(EmptyKind.SEARCH, Modifier.padding(top = 16.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp).testTag(tag))
    }
}

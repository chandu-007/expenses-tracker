package app.expenses.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUi,
    categories: List<CategoryUi>,
    onChange: (SearchInput) -> Unit,
    onBack: () -> Unit,
    onOpen: (RowUi) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var min by rememberSaveable { mutableStateOf("") }
    var max by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var kind by rememberSaveable { mutableStateOf(SearchKind.ANY) }
    var range by rememberSaveable { mutableStateOf(SearchRange.ANY) }
    var menu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun changed(t: String = text, lo: String = min, hi: String = max, c: String? = categoryId, k: SearchKind = kind, r: SearchRange = range) {
        text = t; min = lo; max = hi; categoryId = c; kind = k; range = r
        onChange(SearchInput(t, lo, hi, c, k, r))
    }
    // The screen may be re-created (rotation) with its inputs restored: search again so the results match what is shown.
    LaunchedEffect(Unit) { if (text.isNotEmpty() || min.isNotEmpty() || max.isNotEmpty() || categoryId != null || kind != SearchKind.ANY || range != SearchRange.ANY) onChange(SearchInput(text, min, max, categoryId, kind, range)) }

    Scaffold(topBar = { TopAppBar(title = { Text("Search") }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("search_back")) { Text("Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = text, onValueChange = { changed(t = it) }, label = { Text("Merchant or note") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus).testTag("search_text"),
            )
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
                FilterChip(selected = kind == SearchKind.EXPENSE, onClick = { changed(k = if (kind == SearchKind.EXPENSE) SearchKind.ANY else SearchKind.EXPENSE) }, label = { Text("Expenses") }, modifier = Modifier.testTag("search_kind_expense"))
                FilterChip(selected = kind == SearchKind.INCOME, onClick = { changed(k = if (kind == SearchKind.INCOME) SearchKind.ANY else SearchKind.INCOME) }, label = { Text("Income") }, modifier = Modifier.testTag("search_kind_income"))
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(SearchRange.LAST_30_DAYS to "Last 30 days", SearchRange.THIS_PERIOD to "This period", SearchRange.THIS_YEAR to "This year").forEach { (r, label) ->
                    FilterChip(selected = range == r, onClick = { changed(r = if (range == r) SearchRange.ANY else r) }, label = { Text(label) }, modifier = Modifier.testTag("search_range_${r.name}"))
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            when {
                state.problem != null -> Text(state.problem, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp).testTag("search_problem"))
                !state.hasConditions -> Text("Type a merchant or note, or choose an amount, category or dates.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp).testTag("search_hint"))
                state.searching -> Text("Searching…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp).testTag("search_working"))
                state.results.isEmpty() -> Text("Nothing found.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp).testTag("search_none"))
                else -> {
                    Text(
                        "${state.results.size} ${if (state.results.size == 1) "result" else "results"}" + if (state.truncated) ", the most recent. Narrow the search to see others." else "",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("search_count"),
                    )
                    LazyColumn(Modifier.fillMaxSize().testTag("search_results"), contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(state.results, key = { it.seq }) { row ->
                            Row(Modifier.fillMaxWidth().clickable { onOpen(row) }.padding(horizontal = 16.dp, vertical = 12.dp).testTag("result_${row.id}"), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                    Text(row.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                                Text(if (row.inflow) "+${row.amount}" else row.amount, style = MaterialTheme.typography.bodyLarge, color = if (row.inflow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

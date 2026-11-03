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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberDatePickerState
import app.expenses.core.domain.SearchDates
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.expenses.CategoryUi
import app.expenses.core.domain.formatted
import app.expenses.RowUi
import app.expenses.SearchInput
import app.expenses.SearchKind
import app.expenses.SearchRange
import app.expenses.SearchUi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
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
    onBulkTransfer: ((List<String>, String) -> Unit)? = null,
    tags: List<String>,
    onBulkAddTag: (List<String>, String) -> Unit,
    onBulkRemoveTag: (List<String>, String) -> Unit,
    onUndo: (app.expenses.UndoUi) -> Unit,
    saved: List<app.expenses.SavedSearchUi>,
    onSaveSearch: (String, SearchInput, (String?) -> Unit) -> Unit,
    onDeleteSaved: (String) -> Unit,
    recent: List<String> = emptyList(),
    onRemember: (String) -> Unit = {},
    onForgetRecent: (String) -> Unit = {},
    onClearRecent: () -> Unit = {},
    /** An account to search inside from the start (Home's "Search this account"); the person may change it. */
    initialAccountId: String? = null,
) {
    var savingName by rememberSaveable { mutableStateOf<String?>(null) }
    var savingProblem by remember { mutableStateOf<String?>(null) }
    var deletingSaved by remember { mutableStateOf<app.expenses.SavedSearchUi?>(null) }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    PlatformBackHandler(enabled = selected.isNotEmpty()) { selected = emptyList() }
    val focusManager = LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    // Done on the keyboard, choosing entries and scrolling the results all put the keyboard away, so the results can be seen
    val dismissKeyboard = { focusManager.clearFocus(); keyboard?.hide() }
    var text by rememberSaveable { mutableStateOf("") }
    val hideOnScroll = remember(keyboard) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset { if (available.y != 0f) { keyboard?.hide(); focusManager.clearFocus() }; return androidx.compose.ui.geometry.Offset.Zero }
        }
    }
    // whatever made the results change (an Undo, a bulk change, a refresh), a selection never holds an entry that is not listed
    LaunchedEffect(state.results) {
        if (selected.isNotEmpty()) { val listed = state.results.mapTo(HashSet()) { it.id }; if (selected.any { it !in listed }) selected = selected.filter { it in listed } }
    }
    // choosing entries is done with the keyboard out of the way
    LaunchedEffect(selected.isNotEmpty()) { if (selected.isNotEmpty()) { keyboard?.hide(); focusManager.clearFocus() } }
    // whatever made the results change (an Undo, a bulk change, a refresh), a selection never holds an entry that is not listed
    var min by rememberSaveable { mutableStateOf("") }
    var max by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var kind by rememberSaveable { mutableStateOf(SearchKind.ANY) }
    var range by rememberSaveable { mutableStateOf(SearchRange.ANY) }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    // chosen days (since 1970-01-01, both included, either may be open) and the one account searched in
    var fromDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var toDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var accountId by rememberSaveable { mutableStateOf(initialAccountId) }
    var pickingDay by rememberSaveable { mutableStateOf<String?>(null) }
    val dateProblem = SearchDates.problem(fromDay, toDay)
    var tagMenu by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    // Search from the tab starts with the cursor in the box. Opened from an account the list of that account is already there to read, and a keyboard over it hid more than half of it: the box is one tap away.
    // Coming back from an entry opened out of the results (the search is still running) is the same: the results are what is wanted, not the keyboard.
    LaunchedEffect(Unit) { if (initialAccountId == null && !state.hasConditions) focus.requestFocus() }
    /** [typing]: the change is the text or an amount being typed, so the keyboard stays; any other change (a chip, a day, a saved search) is the person looking at results, so the keyboard goes. */
    fun changed(t: String = text, lo: String = min, hi: String = max, c: String? = categoryId, k: SearchKind = kind, r: SearchRange = range, g: String? = tag, fd: Long? = fromDay, td: Long? = toDay, ac: String? = accountId, typing: Boolean = false) {
        if (!typing) dismissKeyboard()
        selected = emptyList() // the results are about to be different: a selection made on the old ones would be of entries no longer listed
        text = t; min = lo; max = hi; categoryId = c; kind = k; range = r; tag = g; fromDay = fd; toDay = td; accountId = ac
        onChange(SearchInput(t, lo, hi, c, k, r, g, fd, td, ac))
    }
    fun changedAll(i: SearchInput) = changed(t = i.text, lo = i.minRupees, hi = i.maxRupees, c = i.categoryId, k = i.kind, r = i.range, g = i.tag, fd = i.fromDay, td = i.toDay, ac = i.accountId)
    // The screen may be re-created (rotation) with its inputs restored: search again so the results match what is shown.
    LaunchedEffect(Unit) { if (text.isNotEmpty() || min.isNotEmpty() || max.isNotEmpty() || categoryId != null || kind != SearchKind.ANY || range != SearchRange.ANY || tag != null || fromDay != null || toDay != null || accountId != null) onChange(SearchInput(text, min, max, categoryId, kind, range, tag, fromDay, toDay, accountId)) }

    pickingDay?.let { which ->
        // the date picker counts days as UTC midnights
        val picker = rememberDatePickerState(initialSelectedDateMillis = (if (which == "from") fromDay else toDay)?.times(86_400_000L))
        DatePickerDialog(
            onDismissRequest = { pickingDay = null },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { ms -> val d = ms.floorDiv(86_400_000L); if (which == "from") changed(r = SearchRange.ANY, fd = d) else changed(r = SearchRange.ANY, td = d) }
                    pickingDay = null
                }, modifier = Modifier.testTag("search_date_ok")) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDay = null }) { Text("Cancel") } },
        ) { DatePicker(state = picker) }
    }

    savingName?.let { n ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { savingName = null; savingProblem = null }, title = { Text("Save this search") },
            text = {
                Column {
                    OutlinedTextField(n, { savingName = it; savingProblem = null }, label = { Text("Name") }, singleLine = true, modifier = Modifier.testTag("saved_name"))
                    savingProblem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("saved_problem")) }
                }
            },
            confirmButton = { TextButton(onClick = { onSaveSearch(n, SearchInput(text, min, max, categoryId, kind, range, tag, fromDay, toDay, accountId)) { p -> if (p == null) { savingName = null; savingProblem = null } else savingProblem = p } }, modifier = Modifier.testTag("saved_ok")) { Text("Save") } },
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
                selection = bulkSelectionOf(state.results, selected),
                onMakeTransfer = onBulkTransfer?.let { go -> { to -> go(selected, to); selected = emptyList() } },
            )
            else tabBar()
        },
        topBar = {
            if (selected.isNotEmpty()) TopAppBar(
                title = { Text("Choosing entries") },
                navigationIcon = { IconButton(onClick = { selected = emptyList() }, modifier = Modifier.testTag("search_selection_clear")) { Icon(Icons.Outlined.Close, contentDescription = "Stop choosing") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            )
            else TopAppBar(title = { Text("Search") }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("search_back")) { Text("Back") } })
        }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // one rounded bar: a magnifier, what is typed, and a way to clear it
            OutlinedTextField(
                value = text, onValueChange = { changed(t = it, typing = true) }, singleLine = true,
                placeholder = { Text("Merchant or note") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = { if (text.isNotEmpty()) IconButton(onClick = { changed(t = "", typing = true) }, modifier = Modifier.testTag("search_clear")) { Icon(Icons.Outlined.Close, contentDescription = "Clear the search") } },
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onRemember(text); dismissKeyboard() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).focusRequester(focus).testTag("search_text"),
            )
            // The saved searches, the filters, the dates and the amounts: the first thing of the page that scrolls, so the keyboard goes away as soon as the page is dragged and
            // the results can come up under the search box (with the keyboard up, a typed search brings them up by itself, see below).
            val filters: @Composable () -> Unit = {
            if (saved.isNotEmpty()) ChipRow(Modifier.padding(vertical = 4.dp)) {
                saved.forEach { s ->
                    LongPressChip(label = s.name, onClick = { changedAll(s.input) }, onLongClick = { deletingSaved = s }, leading = Icons.Outlined.Star, modifier = Modifier.testTag("saved_${s.name}"))
                }
            }
            // wraps, so every chip (every account too) is in sight: a row that scrolled sideways left the account chips beyond the edge with nothing to say so
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Box {
                    FilterChip(selected = categoryId != null, onClick = { menu = true }, label = { Text(categories.firstOrNull { it.id == categoryId }?.let { displayName(categories, it) } ?: "Any category") }, trailingIcon = { Icon(if (categoryId != null) Icons.Outlined.Close else Icons.Outlined.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) }, modifier = Modifier.testTag("search_category"))
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Any category") }, onClick = { menu = false; changed(c = null) }, modifier = Modifier.testTag("search_cat_any"))
                        categories.forEach { c -> DropdownMenuItem(text = { Text(displayName(categories, c)) }, onClick = { menu = false; changed(c = c.id) }, modifier = Modifier.testTag("search_cat_${c.id}")) }
                    }
                }
                if (tags.isNotEmpty() || tag != null) Box {
                    FilterChip(selected = tag != null, onClick = { tagMenu = true }, label = { Text(tag?.let { "Tag: $it" } ?: "Any tag") }, trailingIcon = { Icon(if (tag != null) Icons.Outlined.Close else Icons.Outlined.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) }, modifier = Modifier.testTag("search_tag"))
                    DropdownMenu(expanded = tagMenu, onDismissRequest = { tagMenu = false }) {
                        DropdownMenuItem(text = { Text("Any tag") }, onClick = { tagMenu = false; changed(g = null) }, modifier = Modifier.testTag("search_tag_any"))
                        tags.forEach { t -> DropdownMenuItem(text = { Text(t) }, onClick = { tagMenu = false; changed(g = t) }, modifier = Modifier.testTag("search_tag_$t")) }
                    }
                }
                FilterChip(selected = kind == SearchKind.EXPENSE, onClick = { changed(k = if (kind == SearchKind.EXPENSE) SearchKind.ANY else SearchKind.EXPENSE) }, label = { Text("Expenses") }, modifier = Modifier.testTag("search_kind_expense"))
                FilterChip(selected = kind == SearchKind.INCOME, onClick = { changed(k = if (kind == SearchKind.INCOME) SearchKind.ANY else SearchKind.INCOME) }, label = { Text("Income") }, modifier = Modifier.testTag("search_kind_income"))
                // one account (what was paid from it and what was moved into it), or any
                if (accounts.size > 1 || accountId != null) {
                    FilterChip(selected = accountId == null, onClick = { changed(ac = null) }, label = { Text("Any account") }, modifier = Modifier.testTag("search_account_any"))
                    accounts.forEach { a -> FilterChip(selected = accountId == a.id, onClick = { changed(ac = if (accountId == a.id) null else a.id) }, label = { Text(a.name) }, modifier = Modifier.testTag("search_account_${a.name}")) }
                }
            }
            // a row that scrolls sideways fades out at the edge that has more behind it
            ChipRow(Modifier.padding(vertical = 4.dp)) {
                if (state.hasConditions && state.problem == null) androidx.compose.material3.AssistChip(
                    onClick = { savingName = "" }, label = { Text("Save this search") }, leadingIcon = { Icon(Icons.Outlined.StarBorder, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("save_search"),
                )
                listOf(SearchRange.LAST_30_DAYS to "Last 30 days", SearchRange.THIS_PERIOD to "This period", SearchRange.THIS_YEAR to "This year").forEach { (r, label) ->
                    FilterChip(selected = range == r, onClick = { changed(r = if (range == r) SearchRange.ANY else r, fd = null, td = null) }, label = { Text(label) }, trailingIcon = if (range == r) ({ Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp)) }) else null, modifier = Modifier.testTag("search_range_${r.name}"))
                }
                // specific days: either end may be left open; the first and the last day are both included
                DayField("From", fromDay, onPick = { dismissKeyboard(); pickingDay = "from" }, onClear = { changed(fd = null) }, tag = "search_date_from")
                DayField("To", toDay, onPick = { dismissKeyboard(); pickingDay = "to" }, onClear = { changed(td = null) }, tag = "search_date_to")
            }
            if (dateProblem != null) Text(dateProblem, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp).testTag("search_date_problem"))
            else SearchDates.summary(fromDay, toDay)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp).testTag("search_date_summary")) }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = min, onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) changed(lo = v, typing = true) }, label = { Text("From ₹") }, singleLine = true, shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done), keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { dismissKeyboard() }), modifier = Modifier.weight(1f).testTag("search_min"))
                OutlinedTextField(value = max, onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) changed(hi = v, typing = true) }, label = { Text("To ₹") }, singleLine = true, shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done), keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { dismissKeyboard() }), modifier = Modifier.weight(1f).testTag("search_max"))
            }
            HorizontalDivider(Modifier.padding(top = 4.dp))
            }
            // One list for everything under the search box (the filters are its first item), so the page keeps its place from one keystroke to the next
            // (while the next search runs the results give way to "Searching…" for a moment; the foot below keeps the page where it was).
            val rows = state.results
            val showResults = dateProblem == null && state.problem == null && state.hasConditions && !state.searching && rows.isNotEmpty()
            val listedIds = remember(rows) { rows.map { it.id } }
            val allSelected = listedIds.isNotEmpty() && selected.size >= listedIds.size && selected.toHashSet().containsAll(listedIds)
            val spentMinor = remember(rows) { rows.filter { it.kind == "EXPENSE" }.sumOf { it.minor } }
            // rows under a heading for their day, like the home list
            val listItems = remember(rows) { buildList<Any> { var last = Long.MIN_VALUE; rows.forEach { r -> if (r.day != last) { add(r.day); last = r.day }; add(r) } } }
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            // Typing with the keyboard up: the filters would fill the room the keyboard leaves and hide the results behind it, so the page moves up to the results (the search box stays
            // where it is, and a drag down brings the filters back, with the keyboard put away). Each time results show while the keyboard is up and the page is at its top.
            val keyboardOpen = rememberKeyboardOpen()
            LaunchedEffect(keyboardOpen, showResults, text) {
                if (keyboardOpen && showResults && text.isNotBlank() && listState.firstVisibleItemIndex == 0) listState.animateScrollToItem(1)
            }
            // what a bulk change just did, and its Undo, are the first thing under the filters: with the page moved up for the keyboard (and then the room under it taken back) they
            // were left above the screen
            LaunchedEffect(state.notice) { if (state.notice != null && showResults) listState.animateScrollToItem(1) }
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            // with the keyboard up the list gets a foot as tall as the room left, so a short list of results can still be moved up under the box
            LazyColumn(Modifier.fillMaxSize().nestedScroll(hideOnScroll).testTag("search_results"), state = listState, contentPadding = PaddingValues(bottom = if (keyboardOpen) maxHeight else 24.dp)) {
                item(key = "search_filters", contentType = "filters") { Column { filters() } }
                when {
                    dateProblem != null -> {} // the message under the dates says it
                    state.problem != null -> item(key = "search_problem", contentType = "message") { Text(state.problem, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp).testTag("search_problem")) }
                    !state.hasConditions -> item(key = "search_start", contentType = "start") {
                        SearchStart(
                            recent = recent, categories = categories,
                            onPick = { t -> changed(t = t) }, onForget = onForgetRecent, onClearRecent = onClearRecent,
                            onTry = { i -> changedAll(i.copy(accountId = accountId)) },
                        )
                    }
                    showResults -> {
                        if (state.notice != null) item(key = "search_notice", contentType = "notice") {
                            state.notice?.let { n ->
                                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(n, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).testTag("search_notice"))
                                    state.undo?.let { u -> TextButton(onClick = { onUndo(u) }, modifier = Modifier.testTag("search_undo")) { Text("Undo") } }
                                }
                            }
                        }
                        item(key = "search_summary", contentType = "summary") {
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${rows.size} ${if (rows.size == 1) "result" else "results"}" +
                                        if (state.truncated) ", the most recent. Narrow the search to see others." else if (spentMinor > 0) " · ${app.expenses.core.domain.Money(spentMinor, app.expenses.core.domain.BaseCurrency.current).formatted()} spent" else "",
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f).testTag("search_count"),
                                )
                                // every listed result, not only the ones composed on screen
                                if (listedIds.isNotEmpty()) TextButton(
                                    onClick = { selected = if (allSelected) emptyList() else listedIds },
                                    modifier = Modifier.testTag("search_select_all"),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                ) { Text(if (allSelected) "Clear selection" else "Select all ${listedIds.size}") }
                            }
                        }
                        itemsIndexed(listItems, key = { i, it -> if (it is RowUi) it.seq else "day$i" }, contentType = { _, it -> if (it is RowUi) "row" else "day" }) { index, item ->
                        if (item !is RowUi) { DayHeader(item as Long); return@itemsIndexed }
                        val row = item
                        val isSelected = row.id in selected
                        val pos = CardPos.of(first = listItems.getOrNull(index - 1) !is RowUi, last = listItems.getOrNull(index + 1) !is RowUi)
                        Row(
                            Modifier.animateItem().entryCardClip(pos).fillMaxWidth().entryCardFill(pos, isSelected)
                                .combinedClickable(onClick = { if (selected.isNotEmpty()) selected = selected.toggled(row.id) else { onRemember(text); onOpen(row) } }, onLongClick = { selected = selected.toggled(row.id) })
                                .padding(horizontal = 16.dp, vertical = rowPadding(12.dp)).testTag("result_${row.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selected.isNotEmpty()) androidx.compose.material3.Checkbox(checked = isSelected, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp)) else CategoryAvatar(row.category, row.title, Modifier.padding(end = 14.dp))
                            Column(Modifier.weight(1f)) {
                                MatchText(row.title, text, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                MatchText(row.subtitle, text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            val flow = flowOf(row.kind, row.inflow)
                            Text(signedAmount(row.amount, flow), style = MaterialTheme.typography.bodyLarge, color = amountColor(flow))
                        }
                    }
                    }
                    state.searching -> item(key = "search_working", contentType = "message") { Text("Searching…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp).testTag("search_working")) }
                    else -> item(key = "search_empty", contentType = "message") { SearchEmpty("No entries match. Try fewer words, or remove a filter.", "search_none", onClear = { changedAll(SearchInput()) }) }
                }
            }
            }
        }
    }
}

/**
 * A row of chips that scrolls sideways, fading out at an edge that has more chips behind it, so a chip cut off by the screen's edge is a visible hint to
 * scroll and not something that is simply missing.
 */
@Composable
private fun ChipRow(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val scroll = rememberScrollState()
    val fade = 36.dp
    Row(
        modifier.fillMaxWidth()
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val w = fade.toPx()
                if (scroll.canScrollForward) drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = size.width - w, endX = size.width), blendMode = androidx.compose.ui.graphics.BlendMode.DstOut)
                if (scroll.canScrollBackward) drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = 0f, endX = w), blendMode = androidx.compose.ui.graphics.BlendMode.DstOut)
            }
            .horizontalScroll(scroll).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = content,
    )
}

/** One end of the chosen days: a button showing the day (or "From"/"To" while open) that opens the picker, and a way to clear it. */
@Composable
private fun DayField(label: String, day: Long?, onPick: () -> Unit, onClear: () -> Unit, tag: String) {
    OutlinedButton(onClick = onPick, modifier = Modifier.testTag(tag)) {
        Text(if (day == null) "$label date" else "$label ${SearchDates.summary(day, day)}")
    }
    if (day != null) IconButton(onClick = onClear, modifier = Modifier.testTag(tag + "_clear")) { Icon(Icons.Outlined.Close, contentDescription = "Clear the $label date") }
}

/** What shows before anything is typed: the hint, the words searched for lately, and a few searches to start from. */
@Composable
private fun SearchStart(recent: List<String>, categories: List<CategoryUi>, onPick: (String) -> Unit, onForget: (String) -> Unit, onClearRecent: () -> Unit, onTry: (SearchInput) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Type a merchant or note, or choose an amount, category or dates.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("search_hint"))
        if (recent.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("RECENT SEARCHES", style = MaterialTheme.typography.labelSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = onClearRecent, modifier = Modifier.testTag("recent_clear")) { Text("Clear") }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
                recent.forEach { r ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(r) }.padding(start = 14.dp).testTag("recent_$r"), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Text(r, maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 12.dp))
                        IconButton(onClick = { onForget(r) }, modifier = Modifier.testTag("recent_forget_$r")) { Icon(Icons.Outlined.Close, contentDescription = "Forget \"$r\"", modifier = Modifier.size(18.dp)) }
                    }
                }
            }
        }
        Text("TRY", style = MaterialTheme.typography.labelSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        TryCard("Expenses this period", "Everything you spent, this period", Icons.Outlined.TrendingDown, "try_expenses") { onTry(SearchInput(kind = SearchKind.EXPENSE, range = SearchRange.THIS_PERIOD)) }
        TryCard("Income this year", "Money that came in", Icons.Outlined.TrendingUp, "try_income") { onTry(SearchInput(kind = SearchKind.INCOME, range = SearchRange.THIS_YEAR)) }
        categories.firstOrNull { it.name == "Other" }?.let { c ->
            TryCard("Entries in \"Other\"", "A good place to tidy up", Icons.Outlined.Label, "try_other") { onTry(SearchInput(categoryId = c.id)) }
        }
    }
}

@Composable
private fun TryCard(title: String, line: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tag: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)).clickable(onClick = onClick).padding(14.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

/** The "nothing found" line under a small picture; scrolls if the keyboard leaves little room, so the text is always reachable. */
@Composable
private fun SearchEmpty(text: String, tag: String, onClear: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyArt(EmptyKind.SEARCH, Modifier.padding(top = 16.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp).testTag(tag))
        OutlinedButton(onClick = onClear, modifier = Modifier.testTag("search_none_clear")) { Text("Clear the search") }
    }
}

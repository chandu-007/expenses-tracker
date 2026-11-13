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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
    /** A tag and days to search with from the start (a trip's "See all entries"); the person may change them. */
    initialTag: String? = null,
    initialFromDay: Long? = null,
    initialToDay: Long? = null,
    /**
     * What the person has typed and chosen here (the words, the filters, the entries ticked, whether the Filters panel is open, the scroll position). Kept by the caller, outside the screen, so that
     * a window that changes size (a foldable folded or unfolded: the open entry then goes beside the results or over them) takes the screen down and brings it back as it was. By default
     * the screen keeps its own, saved with the page.
     */
    fields: SearchFields = rememberSaveable(saver = SearchFields.Saver) { SearchFields(initialAccountId, initialTag, initialFromDay, initialToDay) },
    /** The box takes the focus when this changes while the page is showing (Ctrl+F from a hardware keyboard while Search is already open). */
    focusTick: Int = 0,
) {
    var savingName by rememberSaveable { mutableStateOf<String?>(null) }
    var savingProblem by remember { mutableStateOf<String?>(null) }
    var deletingSaved by remember { mutableStateOf<app.expenses.SavedSearchUi?>(null) }
    var selected by fields.selected
    // the Filters panel (SearchFilters.kt) is open
    var filtersOpen by fields.filtersOpen
    // registered only while they apply: a back handler is called in the order its owner was first composed, not by what is enabled, so one registered at the start would sit under the entry open beside the results
    // (two panes) and Back would close that entry before the selection or the Filters panel
    if (selected.isNotEmpty()) PlatformBackHandler(enabled = true) { selected = emptyList() }
    val focusManager = LocalFocusManager.current
    // measurement builds only (see Platform.trace): when the page is first composed, when anything the search is made of changes, and when results are shown
    val platform = LocalPlatform.current
    LaunchedEffect(Unit) { platform.trace("search: composed") }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    // Done on the keyboard, choosing entries and scrolling the results all put the keyboard away, so the results can be seen
    val dismissKeyboard = { focusManager.clearFocus(); keyboard?.hide() }
    var text by fields.text
    val hideOnScroll = remember(keyboard) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset { if (available.y != 0f) { keyboard?.hide(); focusManager.clearFocus() }; return androidx.compose.ui.geometry.Offset.Zero }
        }
    }
    // whatever made the results change (an Undo, a bulk change, a refresh), a selection never holds an entry that is not listed
    LaunchedEffect(state.results) {
        if (state.results.isNotEmpty()) platform.trace("search: results")
        if (selected.isNotEmpty()) { val listed = state.results.mapTo(HashSet()) { it.id }; if (selected.any { it !in listed }) selected = selected.filter { it in listed } }
    }
    // choosing entries is done with the keyboard out of the way
    LaunchedEffect(selected.isNotEmpty()) { if (selected.isNotEmpty()) { keyboard?.hide(); focusManager.clearFocus(); filtersOpen = false } }
    if (filtersOpen) PlatformBackHandler(enabled = true) { filtersOpen = false }
    // whatever made the results change (an Undo, a bulk change, a refresh), a selection never holds an entry that is not listed
    var min by fields.min
    var max by fields.max
    var categoryId by fields.categoryId
    var kind by fields.kind
    var range by fields.range
    var tag by fields.tag
    // chosen days (since 1970-01-01, both included, either may be open) and the one account searched in
    var fromDay by fields.fromDay
    var toDay by fields.toDay
    var accountId by fields.accountId
    var pickingDay by rememberSaveable { mutableStateOf<String?>(null) }
    val dateProblem = SearchDates.problem(fromDay, toDay)
    val focus = remember { FocusRequester() }
    // Search from the tab starts with the cursor in the box. Opened from an account the list of that account is already there to read, and a keyboard over it hid more than half of it: the box is one tap away.
    // Coming back from an entry opened out of the results (the search is still running) is the same: the results are what is wanted, not the keyboard.
    LaunchedEffect(Unit) { if (initialAccountId == null && initialTag == null && !state.hasConditions) focus.requestFocus() }
    val firstTick = remember { focusTick }
    LaunchedEffect(focusTick) { if (focusTick != firstTick) focus.requestFocus() }
    /** [typing]: the change is the text or an amount being typed, so the keyboard stays; any other change (a chip, a day, a saved search) is the person looking at results, so the keyboard goes. */
    fun changed(t: String = text, lo: String = min, hi: String = max, c: String? = categoryId, k: SearchKind = kind, r: SearchRange = range, g: String? = tag, fd: Long? = fromDay, td: Long? = toDay, ac: String? = accountId, typing: Boolean = false) {
        if (!typing) dismissKeyboard()
        if (t != text || lo != min || hi != max || c != categoryId || k != kind || r != range || g != tag || fd != fromDay || td != toDay || ac != accountId) platform.trace("search: input")
        selected = emptyList() // the results are about to be different: a selection made on the old ones would be of entries no longer listed
        text = t; min = lo; max = hi; categoryId = c; kind = k; range = r; tag = g; fromDay = fd; toDay = td; accountId = ac
        onChange(SearchInput(t, lo, hi, c, k, r, g, fd, td, ac))
    }
    fun changedAll(i: SearchInput) = changed(t = i.text, lo = i.minRupees, hi = i.maxRupees, c = i.categoryId, k = i.kind, r = i.range, g = i.tag, fd = i.fromDay, td = i.toDay, ac = i.accountId)
    // The screen may be re-created (rotation, back from an entry, a fold) with its inputs restored: search again only when the view model has lost the results (the process was recreated), so a result list that is
    // still held is not blanked and the "Searching…" line does not flash.
    LaunchedEffect(Unit) { if (!state.hasConditions && (text.isNotEmpty() || min.isNotEmpty() || max.isNotEmpty() || categoryId != null || kind != SearchKind.ANY || range != SearchRange.ANY || tag != null || fromDay != null || toDay != null || accountId != null)) onChange(SearchInput(text, min, max, categoryId, kind, range, tag, fromDay, toDay, accountId)) }

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
    val input = SearchInput(text, min, max, categoryId, kind, range, tag, fromDay, toDay, accountId)
    val rows = state.results
    val showResults = dateProblem == null && state.problem == null && state.hasConditions && !state.searching && rows.isNotEmpty()
    val listedIds = remember(rows) { rows.map { it.id } }
    val allSelected = listedIds.isNotEmpty() && selected.size >= listedIds.size && selected.toHashSet().containsAll(listedIds)
    val spentMinor = remember(rows) { rows.filter { it.kind == "EXPENSE" }.sumOf { it.minor } }
    // rows under a heading for their day, like the home list
    val listItems = remember(rows) { buildList<Any> { var last = Long.MIN_VALUE; rows.forEach { r -> if (r.day != last) { add(r.day); last = r.day }; add(r) } } }
    val listState = fields.listState
    // On a two-pane window the results are in a list pane only 340 to 480 dp wide (Search used to have up to 960 dp there): with larger text a row says what it is on two lines instead of cutting the
    // name short. A phone's one pane is as it was.
    val resultLines = if (rememberIsWide() && androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.15f) 2 else 1
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.semantics { if (filtersOpen) hideFromAccessibility() },
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
            else TopAppBar(title = { Text("Search") }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp).testTag("search_back")) { Text("Back") } })
        }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // What stays in place (it does not scroll away): the box and the Filters button, the one row of filters, and the summary line. Under them only the results move.
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // one rounded bar: a magnifier, what is typed, and a way to clear it
                OutlinedTextField(
                    value = text, onValueChange = { changed(t = it, typing = true) }, singleLine = true,
                    placeholder = { Text("Search entries", maxLines = 1) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = { if (text.isNotEmpty()) IconButton(onClick = { changed(t = "", typing = true) }, modifier = Modifier.testTag("search_clear")) { Icon(Icons.Outlined.Close, contentDescription = "Clear the search") } },
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onRemember(text); dismissKeyboard() }),
                    modifier = Modifier.weight(1f).focusRequester(focus).testTag("search_text"),
                )
                FiltersButton(input.filterCount(), onClick = { dismissKeyboard(); filtersOpen = true })
            }
            FilterStrip(input, categories, accounts, onEdit = { changedAll(it) }, modifier = Modifier.padding(bottom = 2.dp))
            if (dateProblem != null || state.problem != null || state.searching || (state.hasConditions && rows.isNotEmpty())) Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp).testTag("search_summary"), verticalAlignment = Alignment.CenterVertically) {
                when {
                    dateProblem != null || state.problem != null -> Text(dateProblem ?: state.problem.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).testTag("search_problem"))
                    state.searching -> Text("Searching…", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).testTag("search_working"))
                    else -> {
                        Text(
                            "${rows.size} ${if (rows.size == 1) "result" else "results"}" +
                                if (state.truncated) ", the most recent. Narrow the search to see others." else if (spentMinor > 0) " · ${app.expenses.core.domain.Money(spentMinor, app.expenses.core.domain.BaseCurrency.current).formatted()} spent" else "",
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }.testTag("search_count"),
                        )
                        // every listed result, not only the ones composed on screen
                        if (listedIds.isNotEmpty()) TextButton(
                            onClick = { selected = if (allSelected) emptyList() else listedIds },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("search_select_all"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        ) { Text(if (allSelected) "Clear selection" else "Select all ${listedIds.size}") }
                    }
                }
            }
            // what a bulk change just did, and its Undo
            state.notice?.let { n ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(n, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).testTag("search_notice"))
                    state.undo?.let { u -> TextButton(onClick = { onUndo(u) }, modifier = Modifier.heightIn(min = 48.dp).testTag("search_undo")) { Text("Undo") } }
                }
            }
            HorizontalDivider()
            // the results (or, before anything is asked, the start page): the only part that scrolls; a drag puts the keyboard away
            LazyColumn(Modifier.fillMaxSize().nestedScroll(hideOnScroll).testTag("search_results"), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                when {
                    dateProblem != null || state.problem != null -> {} // the summary line says it
                    !state.hasConditions -> item(key = "search_start", contentType = "start") {
                        SearchStart(
                            recent = recent, categories = categories, saved = saved,
                            onPick = { t -> changed(t = t) }, onForget = onForgetRecent, onClearRecent = onClearRecent,
                            onTry = { i -> changedAll(i.copy(accountId = accountId)) },
                            onUseSaved = { i -> changedAll(i) }, onRemoveSaved = { deletingSaved = it },
                        )
                    }
                    showResults -> {
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
                                MatchText(row.title, text, style = MaterialTheme.typography.bodyLarge, maxLines = resultLines)
                                MatchText(row.subtitle, text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = resultLines)
                            }
                            val flow = flowOf(row.kind, row.inflow)
                            Text(signedAmount(row.amount, flow), style = MaterialTheme.typography.bodyLarge, color = amountColor(flow))
                        }
                    }
                    }
                    state.searching -> {} // "Searching…" is in the summary line
                    else -> item(key = "search_empty", contentType = "message") {
                        SearchEmpty(
                            filters = input.filterCount() > 0, words = text.isNotBlank(),
                            onClearFilters = { changedAll(input.withoutFilters()) }, onClearAll = { changedAll(SearchInput()) },
                        )
                    }
                }
            }
        }
    }
    if (filtersOpen) FiltersPanel(
        input = input, state = state, dateProblem = dateProblem, canSave = state.hasConditions && state.problem == null,
        categories = categories, tags = tags, accounts = accounts, saved = saved,
        onEdit = { i, typing -> changed(t = i.text, lo = i.minRupees, hi = i.maxRupees, c = i.categoryId, k = i.kind, r = i.range, g = i.tag, fd = i.fromDay, td = i.toDay, ac = i.accountId, typing = typing) },
        onUseSaved = { i -> filtersOpen = false; changedAll(i) }, onRemoveSaved = { deletingSaved = it }, onSave = { savingName = "" },
        onPickDay = { which -> dismissKeyboard(); pickingDay = which }, onDone = { dismissKeyboard() }, onClose = { filtersOpen = false },
    )
    }
}

/**
 * A row of chips that scrolls sideways, fading out at an edge that has more chips behind it, so a chip cut off by the screen's edge is a visible hint to
 * scroll and not something that is simply missing.
 */
@Composable
internal fun ChipRow(modifier: Modifier = Modifier, resetKey: Any? = null, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val scroll = rememberScrollState()
    // back to the first chip when [resetKey] changes (a filter came or went), so what is on is in sight
    LaunchedEffect(resetKey) { if (scroll.value != 0) scroll.animateScrollTo(0) }
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

/** What shows before anything is typed: the hint, the words searched for lately, and a few searches to start from. */
@Composable
private fun SearchStart(
    recent: List<String>, categories: List<CategoryUi>, saved: List<app.expenses.SavedSearchUi>, onPick: (String) -> Unit, onForget: (String) -> Unit, onClearRecent: () -> Unit, onTry: (SearchInput) -> Unit,
    onUseSaved: (SearchInput) -> Unit, onRemoveSaved: (app.expenses.SavedSearchUi) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Type a merchant or note, or open Filters to choose dates, an account, a category or an amount.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("search_hint"))
        // the searches kept by name: one tap runs one (the same ones are under Filters, where they are made and removed; their tags there begin with filters_)
        if (saved.isNotEmpty()) {
            Text("SAVED SEARCHES", style = MaterialTheme.typography.labelSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                saved.forEach { s -> LongPressChip(label = s.name, onClick = { onUseSaved(s.input) }, onLongClick = { onRemoveSaved(s) }, leading = Icons.Outlined.Star, modifier = Modifier.heightIn(min = 48.dp).testTag("saved_${s.name}")) }
            }
        }
        if (recent.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("RECENT SEARCHES", style = MaterialTheme.typography.labelSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = onClearRecent, modifier = Modifier.heightIn(min = 48.dp).testTag("recent_clear")) { Text("Clear") }
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

/**
 * The "nothing found" page under a small picture: what to do next is one tap. With filters on, "Clear filters" takes them off and keeps the words; with words as well, "Clear the search" takes
 * everything off (with only words typed, that is the one button).
 */
@Composable
private fun SearchEmpty(filters: Boolean, words: Boolean, onClearFilters: () -> Unit, onClearAll: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyArt(EmptyKind.SEARCH, Modifier.padding(top = 16.dp))
        Text("No entries match", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp).testTag("search_none"))
        Text(
            if (filters) "Try fewer words, or take a filter off." else "Try fewer words or a different spelling.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp),
        )
        if (filters) androidx.compose.material3.Button(onClick = onClearFilters, modifier = Modifier.heightIn(min = 48.dp).testTag(if (words) "search_none_clear_filters" else "search_none_clear")) { Text("Clear filters") }
        if (words && filters) TextButton(onClick = onClearAll, modifier = Modifier.heightIn(min = 48.dp).testTag("search_none_clear")) { Text("Clear the search") }
        else if (words) OutlinedButton(onClick = onClearAll, modifier = Modifier.heightIn(min = 48.dp).testTag("search_none_clear")) { Text("Clear the search") }
    }
}

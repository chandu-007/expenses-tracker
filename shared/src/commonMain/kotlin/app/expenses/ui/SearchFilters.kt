package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.expenses.AccountUi
import app.expenses.CategoryUi
import app.expenses.SavedSearchUi
import app.expenses.SearchInput
import app.expenses.SearchKind
import app.expenses.SearchRange
import app.expenses.SearchUi
import app.expenses.core.domain.SearchDates

/*
 * The parts of the Search page that are about filters (Slice 106): the Filters button with its count, the one row of quick and active filters, and the Filters panel.
 *
 * The panel changes the search LIVE (every choice is one ordinary search, the same query and debounce as before, so there is no scan of its own): the number on its button is the
 * number of the results behind it. Apply only closes it; Reset puts the filters back as they were when it opened; Clear all removes every filter (the typed words stay).
 */

/** How many separate filters are on: the dates (a chosen period or chosen days count as one), the account, the category, the tag, the amounts (one), and the kind. The words typed are not a filter. */
internal fun SearchInput.filterCount(): Int =
    (if (range != SearchRange.ANY || fromDay != null || toDay != null) 1 else 0) + (if (accountId != null) 1 else 0) + (if (categoryId != null) 1 else 0) +
        (if (tag != null) 1 else 0) + (if (minRupees.isNotBlank() || maxRupees.isNotBlank()) 1 else 0) + (if (kind != SearchKind.ANY) 1 else 0)

/** The same words, with every filter taken off. */
internal fun SearchInput.withoutFilters(): SearchInput = SearchInput(text = text)

/** [this] with the filters of [o] (the words stay [this]'s own). */
internal fun SearchInput.withFiltersOf(o: SearchInput): SearchInput = copy(
    minRupees = o.minRupees, maxRupees = o.maxRupees, categoryId = o.categoryId, kind = o.kind, range = o.range, tag = o.tag, fromDay = o.fromDay, toDay = o.toDay, accountId = o.accountId,
)

/** "₹100 – ₹250", "From ₹100" or "Up to ₹250", for the chip of an amount filter. */
internal fun amountFilterLabel(min: String, max: String): String {
    val a = min.trim(); val b = max.trim()
    return when {
        a.isNotEmpty() && b.isNotEmpty() -> "₹$a – ₹$b"
        a.isNotEmpty() -> "From ₹$a"
        else -> "Up to ₹$b"
    }
}

/**
 * The Filters button beside the search box, with the number of filters that are on. From a larger text size (150%) it is the icon alone, so the box keeps its room; either way
 * a screen reader hears "Filters, 2 active".
 */
@Composable
internal fun FiltersButton(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val compact = LocalDensity.current.fontScale > 1.4f
    val description = if (count == 0) "Filters" else "Filters, $count active"
    Box(modifier) {
        if (compact) FilledTonalIconButton(onClick, Modifier.size(56.dp).semantics { contentDescription = description }.testTag("search_filters_button")) { Icon(Icons.Outlined.Tune, contentDescription = null) }
        else FilledTonalButton(onClick, Modifier.height(56.dp).semantics { contentDescription = description }.testTag("search_filters_button"), contentPadding = PaddingValues(horizontal = 16.dp)) {
            Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(20.dp))
            Text("Filters", modifier = Modifier.padding(start = 8.dp))
        }
        // the number is in the button's own description ("Filters, 2 active"), so the badge is not read a second time
        if (count > 0) Badge(Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp).clearAndSetSemantics { testTag = "search_filters_badge" }) { Text(count.toString()) }
    }
}

/** A chip that is on must look on: a filled container in the app's colour (not the faint grey of the default) and a border. */
@Composable
private fun filterColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer, selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

@Composable
private fun filterBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, selectedBorderColor = MaterialTheme.colorScheme.primary, selectedBorderWidth = 1.dp)

/** One choice of a group in the panel: with a tick when it is the one chosen. */
@Composable
private fun ChoiceChip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) },
        leadingIcon = if (selected) ({ Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }) else null,
        colors = filterColors(), border = filterBorder(selected), modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun RemovableChip(label: String, tag: String, description: String, onRemove: () -> Unit) {
    FilterChip(
        selected = true, onClick = onRemove, label = { Text(label, maxLines = 1) },
        trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp)) },
        colors = filterColors(), border = filterBorder(true), modifier = Modifier.semantics { contentDescription = description }.testTag(tag),
    )
}

@Composable
private fun QuickChip(label: String, on: Boolean, tag: String, onClick: () -> Unit) {
    FilterChip(
        selected = on, onClick = onClick, label = { Text(label, maxLines = 1) },
        trailingIcon = if (on) ({ Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp)) }) else null,
        colors = filterColors(), border = filterBorder(on), modifier = Modifier.testTag(tag),
    )
}

/**
 * THE one row under the box. Every filter that is on is a chip with a cross that takes it off (dates, account, category, tag, amounts, and this period / expenses / income), and they come first,
 * so what is on is the first thing seen; the chips of the three most used filters that are off follow, to turn them on in one tap. Nothing is said twice: a filter is a chip here, on or off,
 * or it is off. The row scrolls sideways and fades at an edge with more behind it; it goes back to its start when a filter comes or goes, so the first ones are in sight.
 */
@Composable
internal fun FilterStrip(input: SearchInput, categories: List<CategoryUi>, accounts: List<AccountUi>, onEdit: (SearchInput) -> Unit, modifier: Modifier = Modifier) {
    val onPeriod = input.range == SearchRange.THIS_PERIOD
    val onExpense = input.kind == SearchKind.EXPENSE
    val onIncome = input.kind == SearchKind.INCOME
    val daysChosen = input.fromDay != null || input.toDay != null
    val dates = SearchDates.summary(input.fromDay, input.toDay)
    val amount = if (input.minRupees.isNotBlank() || input.maxRupees.isNotBlank()) amountFilterLabel(input.minRupees, input.maxRupees) else null
    ChipRow(modifier.testTag("search_active_chips"), resetKey = input.filterCount()) {
        if (onPeriod) QuickChip("This period", true, "search_range_THIS_PERIOD") { onEdit(input.copy(range = SearchRange.ANY)) }
        if (onExpense) QuickChip("Expenses", true, "search_kind_expense") { onEdit(input.copy(kind = SearchKind.ANY)) }
        if (onIncome) QuickChip("Income", true, "search_kind_income") { onEdit(input.copy(kind = SearchKind.ANY)) }
        if (!daysChosen) when (input.range) {
            SearchRange.LAST_30_DAYS -> RemovableChip("Last 30 days", "search_active_chip_range", "Remove filter: last 30 days") { onEdit(input.copy(range = SearchRange.ANY)) }
            SearchRange.THIS_YEAR -> RemovableChip("This year", "search_active_chip_range", "Remove filter: this year") { onEdit(input.copy(range = SearchRange.ANY)) }
            else -> {}
        }
        dates?.let { s -> RemovableChip(s, "search_active_chip_dates", "Remove filter: $s") { onEdit(input.copy(fromDay = null, toDay = null)) } }
        input.accountId?.let { id ->
            val name = accounts.firstOrNull { it.id == id }?.name ?: "Account"
            RemovableChip(name, "search_active_chip_account", "Remove filter: account $name") { onEdit(input.copy(accountId = null)) }
        }
        input.categoryId?.let { id ->
            val name = categories.firstOrNull { it.id == id }?.let { displayName(categories, it) } ?: "Category"
            RemovableChip(name, "search_active_chip_category", "Remove filter: category $name") { onEdit(input.copy(categoryId = null)) }
        }
        input.tag?.let { t -> RemovableChip("Tag: $t", "search_active_chip_tag", "Remove filter: tag $t") { onEdit(input.copy(tag = null)) } }
        amount?.let { l -> RemovableChip(l, "search_active_chip_amount", "Remove filter: amount $l") { onEdit(input.copy(minRupees = "", maxRupees = "")) } }
        if (!onPeriod) QuickChip("This period", false, "search_range_THIS_PERIOD") { onEdit(input.copy(range = SearchRange.THIS_PERIOD, fromDay = null, toDay = null)) }
        if (!onExpense) QuickChip("Expenses", false, "search_kind_expense") { onEdit(input.copy(kind = SearchKind.EXPENSE)) }
        if (!onIncome) QuickChip("Income", false, "search_kind_income") { onEdit(input.copy(kind = SearchKind.INCOME)) }
    }
}

/** One end of the chosen days: a button showing the day (or "From"/"To" while open) that opens the picker, and a way to clear it. */
@Composable
private fun DayField(label: String, day: Long?, onPick: () -> Unit, onClear: () -> Unit, tag: String) {
    OutlinedButton(onClick = onPick, modifier = Modifier.heightIn(min = 48.dp).testTag(tag)) {
        Text(if (day == null) "$label date" else "$label ${SearchDates.summary(day, day)}")
    }
    if (day != null) IconButton(onClick = onClear, modifier = Modifier.testTag(tag + "_clear")) { Icon(Icons.Outlined.Close, contentDescription = "Clear the $label date") }
}

@Composable
private fun PanelSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
        content()
    }
}

/** A menu button showing what is chosen (or [any]), for the long lists: categories and tags. */
@Composable
private fun PickerField(value: String, tag: String, on: Boolean, content: @Composable (close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag), shape = RoundedCornerShape(12.dp)) {
            Text(value, modifier = Modifier.weight(1f), maxLines = 1, color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) { content { open = false } }
    }
}

/**
 * The Filters panel: over the page (not a window, so the keyboard and taps behave as on the page): a sheet from the bottom on a phone, a card at the side (at most 480 dp) on a wide window.
 * [input] is the search as it is now; every change goes to [onEdit] (`typing`: an amount is being typed, keep the keyboard) and the search runs at once, so the button shows the real
 * number of results. Back, the cross, the dimmed page and Apply all close it, keeping what is chosen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FiltersPanel(
    wide: Boolean, input: SearchInput, state: SearchUi, dateProblem: String?, canSave: Boolean,
    categories: List<CategoryUi>, tags: List<String>, accounts: List<AccountUi>, saved: List<SavedSearchUi>,
    onEdit: (SearchInput, Boolean) -> Unit, onUseSaved: (SearchInput) -> Unit, onRemoveSaved: (SavedSearchUi) -> Unit, onSave: () -> Unit,
    onPickDay: (String) -> Unit, onDone: () -> Unit, onClose: () -> Unit,
) {
    val opened = remember { input }
    val count = input.filterCount()
    val changed = input.withFiltersOf(opened) != input
    val label = when {
        dateProblem != null || state.problem != null -> "Show results"
        state.searching -> "Searching…"
        !state.hasConditions -> "Show results"
        state.truncated -> "Show the ${state.results.size} most recent"
        else -> "Show ${state.results.size} ${if (state.results.size == 1) "result" else "results"}"
    }
    fun edit(i: SearchInput) = onEdit(i, false)
    BoxWithConstraints(Modifier.fillMaxSize().semantics { paneTitle = "Filters"; isTraversalGroup = true }) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (wide) 0.20f else 0.40f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close filters", onClick = onClose).testTag("filters_scrim"))
        val place = if (wide) Modifier.align(Alignment.TopEnd).padding(16.dp).fillMaxWidth(0.62f).widthIn(max = 480.dp).heightIn(max = maxHeight - 32.dp)
        else Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxHeight * 0.9f)
        Surface(
            place.pointerInput(Unit) { detectTapGestures { } }.testTag("filters_panel"),
            shape = if (wide) RoundedCornerShape(28.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, shadowElevation = 12.dp,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Filters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                    TextButton(onClick = { edit(input.withoutFilters()) }, enabled = count > 0, modifier = Modifier.heightIn(min = 48.dp).testTag("filters_clear")) { Text("Clear all") }
                    IconButton(onClick = onClose, modifier = Modifier.testTag("filters_close")) { Icon(Icons.Outlined.Close, contentDescription = "Close filters") }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (saved.isNotEmpty() || canSave) PanelSection("Saved searches") {
                        if (saved.isNotEmpty()) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                saved.forEach { s -> LongPressChip(label = s.name, onClick = { onUseSaved(s.input) }, onLongClick = { onRemoveSaved(s) }, leading = Icons.Outlined.Star, modifier = Modifier.heightIn(min = 48.dp).testTag("filters_saved_${s.name}")) }
                            }
                            Text("Press and hold one to remove it.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (canSave) OutlinedButton(onClick = onSave, modifier = Modifier.heightIn(min = 48.dp).testTag("save_search")) {
                            Icon(Icons.Outlined.StarBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Save this search", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    PanelSection("Dates") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                            listOf(Triple(SearchRange.THIS_PERIOD, "This period", "filters_range_THIS_PERIOD"), Triple(SearchRange.LAST_30_DAYS, "Last 30 days", "search_range_LAST_30_DAYS"), Triple(SearchRange.THIS_YEAR, "This year", "search_range_THIS_YEAR")).forEach { (r, l, tag) ->
                                ChoiceChip(l, input.range == r, tag) { edit(input.copy(range = if (input.range == r) SearchRange.ANY else r, fromDay = null, toDay = null)) }
                            }
                        }
                        // specific days: either end may be left open; the first and the last day are both included
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                            DayField("From", input.fromDay, onPick = { onPickDay("from") }, onClear = { edit(input.copy(fromDay = null)) }, tag = "search_date_from")
                            DayField("To", input.toDay, onPick = { onPickDay("to") }, onClear = { edit(input.copy(toDay = null)) }, tag = "search_date_to")
                        }
                        if (dateProblem != null) Text(dateProblem, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("search_date_problem"))
                        else SearchDates.summary(input.fromDay, input.toDay)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("search_date_summary")) }
                    }
                    PanelSection("Type") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                            listOf(Triple(SearchKind.ANY, "Any", "filters_kind_any"), Triple(SearchKind.EXPENSE, "Expenses", "filters_kind_expense"), Triple(SearchKind.INCOME, "Income", "filters_kind_income")).forEach { (k, l, tag) ->
                                ChoiceChip(l, input.kind == k, tag) { edit(input.copy(kind = k)) }
                            }
                        }
                    }
                    // one account (what was paid from it and what was moved into it), or any
                    if (accounts.size > 1 || input.accountId != null) PanelSection("Account") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                            ChoiceChip("Any account", input.accountId == null, "search_account_any") { edit(input.copy(accountId = null)) }
                            accounts.forEach { a -> ChoiceChip(a.name, input.accountId == a.id, "search_account_${a.name}") { edit(input.copy(accountId = if (input.accountId == a.id) null else a.id)) } }
                        }
                    }
                    PanelSection("Category") {
                        PickerField(categories.firstOrNull { it.id == input.categoryId }?.let { displayName(categories, it) } ?: "Any category", "search_category", input.categoryId != null) { close ->
                            DropdownMenuItem(text = { Text("Any category") }, onClick = { close(); edit(input.copy(categoryId = null)) }, modifier = Modifier.testTag("search_cat_any"))
                            categories.forEach { c -> DropdownMenuItem(text = { Text(displayName(categories, c)) }, onClick = { close(); edit(input.copy(categoryId = c.id)) }, modifier = Modifier.testTag("search_cat_${c.id}")) }
                        }
                    }
                    if (tags.isNotEmpty() || input.tag != null) PanelSection("Tag") {
                        PickerField(input.tag?.let { "Tag: $it" } ?: "Any tag", "search_tag", input.tag != null) { close ->
                            DropdownMenuItem(text = { Text("Any tag") }, onClick = { close(); edit(input.copy(tag = null)) }, modifier = Modifier.testTag("search_tag_any"))
                            tags.forEach { t -> DropdownMenuItem(text = { Text(t) }, onClick = { close(); edit(input.copy(tag = t)) }, modifier = Modifier.testTag("search_tag_$t")) }
                        }
                    }
                    PanelSection("Amount") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = input.minRupees, onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) onEdit(input.copy(minRupees = v), true) }, label = { Text("From ₹") }, singleLine = true, shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { onDone() }), modifier = Modifier.weight(1f).testTag("search_min"))
                            OutlinedTextField(value = input.maxRupees, onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) onEdit(input.copy(maxRupees = v), true) }, label = { Text("To ₹") }, singleLine = true, shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { onDone() }), modifier = Modifier.weight(1f).testTag("search_max"))
                        }
                        state.problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("filters_problem")) }
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { onEdit(input.withFiltersOf(opened), false) }, enabled = changed, modifier = Modifier.heightIn(min = 48.dp).testTag("filters_reset")) { Text("Reset") }
                    Button(onClick = onClose, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("filters_apply")) { Text(label, modifier = Modifier.testTag("filters_count")) }
                }
            }
        }
    }
}

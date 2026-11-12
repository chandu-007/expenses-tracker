package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.expenses.ScreenUndo
import app.expenses.TripOfferUi
import app.expenses.TripRowUi
import app.expenses.TripSaveOutcome
import app.expenses.TripsUi
import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.SearchDates
import app.expenses.core.domain.TripField
import app.expenses.core.domain.TripPhase
import app.expenses.core.domain.TripProblem
import app.expenses.core.domain.TripRules
import app.expenses.core.domain.symbolOrCode
import app.expenses.todayLocal

/** What the form hands the view model: a trip as typed (the rules in [TripRules.build] decide whether it can be kept). */
typealias TripSave = (id: String?, name: String, tag: String, startDay: Long?, endDay: Long?, budget: String, enabled: Boolean, confirmUsedTag: Boolean, done: (TripSaveOutcome) -> Unit) -> Unit

/**
 * Travel mode: the trips (on now, coming up, past), and the form that makes or changes one. While a trip is on, a new expense dated inside it gets the trip's tag by itself; the person can take the tag off any
 * entry. Creating a trip, or making its dates cover earlier entries, offers (never does by itself) to tag those entries, with an Undo bar. Deleting a trip keeps the tags on the entries.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(
    state: TripsUi?,
    tags: List<String>,
    offer: TripOfferUi?,
    undo: ScreenUndo?,
    onBack: () -> Unit,
    onSave: TripSave,
    onDelete: (String) -> Unit,
    onOpen: (String) -> Unit,
    onAcceptOffer: (TripOfferUi) -> Unit,
    onDismissOffer: () -> Unit,
    onUndo: (String) -> Unit,
    onUndoGone: (String) -> Unit,
) {
    // null: the list; "": a new trip; an id: that trip's form
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val rows = state?.rows.orEmpty()
    BackHandler(enabled = editing != null) { editing = null }
    val e = editing
    if (e != null) {
        TripForm(row = rows.firstOrNull { it.id == e }, key = e, tags = tags, onSave = onSave, onClose = { editing = null })
        return
    }
    deleting?.let { id ->
        rows.firstOrNull { it.id == id }?.let { t ->
            AlertDialog(
                onDismissRequest = { deleting = null }, title = { Text("Delete the trip \"${t.name}\"?") },
                text = { Text("Only the trip goes. The tag \"${t.tag}\" stays on the entries it was put on, so Search and Reports still show them. No entry is changed.", modifier = Modifier.testTag("trip_delete_note")) },
                confirmButton = { TextButton(onClick = { onDelete(t.id); deleting = null }, modifier = Modifier.heightIn(min = 48.dp).testTag("trip_delete_ok")) { Text("Delete trip") } },
                dismissButton = { TextButton(onClick = { deleting = null }, modifier = Modifier.heightIn(min = 48.dp).testTag("trip_delete_keep")) { Text("Keep it") } },
            )
        } ?: run { deleting = null }
    }
    offer?.let { o ->
        AlertDialog(
            onDismissRequest = onDismissOffer,
            title = { Text("Tag ${o.count} earlier ${if (o.count == 1) "entry" else "entries"} from these dates?", modifier = Modifier.testTag("trip_offer_title")) },
            text = {
                Text(
                    "${if (o.count == 1) "1 expense" else "${o.count} expenses"} dated on the days of \"${o.tripName}\" ${if (o.count == 1) "does" else "do"} not have the tag \"${o.tag}\" yet. Only expenses are tagged, never income, transfers or refunds, and nothing is tagged unless you say yes. You can undo it right after.",
                    modifier = Modifier.testTag("trip_offer_note"),
                )
            },
            confirmButton = { TextButton(onClick = { onAcceptOffer(o) }, modifier = Modifier.heightIn(min = 48.dp).testTag("trip_offer_yes")) { Text("Tag them") } },
            dismissButton = { TextButton(onClick = onDismissOffer, modifier = Modifier.heightIn(min = 48.dp).testTag("trip_offer_no")) { Text("Not now") } },
        )
    }
    val snackbar = rememberUndoSnackbar(undo, onUndo, onUndoGone)
    Scaffold(
        topBar = { AreaTopBar("Travel mode", Area.MONEY, Icons.Outlined.Flight, onBack, "trips_back") },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state == null) return@Scaffold
        val today = todayLocal().toEpochDays().toLong()
        val now = rows.filter { it.phase == TripPhase.ACTIVE }
        val next = rows.filter { it.phase == TripPhase.UPCOMING }.sortedBy { it.startDay }
        val past = rows.filter { it.phase == TripPhase.PAST }
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("trips_list"), contentPadding = PaddingValues(bottom = if (undo != null) 96.dp else 24.dp)) { // room under the last row for the Undo bar, so it never covers a button
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (rows.isEmpty()) {
                        Text(
                            "Going somewhere? Make a trip with its first and last day, and every expense you add on those days gets the trip's tag by itself. Later, Search and Reports show everything the trip cost. You can take the tag off any entry.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("trips_empty"),
                        )
                    }
                    Button(onClick = { editing = "" }, modifier = Modifier.heightIn(min = 48.dp).testTag("trip_new")) { Text("New trip") }
                }
            }
            tripSection("On now", "trips_section_now", now, today, onOpen, { editing = it }, { deleting = it })
            tripSection("Coming up", "trips_section_next", next, today, onOpen, { editing = it }, { deleting = it })
            tripSection("Past", "trips_section_past", past, today, onOpen, { editing = it }, { deleting = it })
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.tripSection(
    title: String, tag: String, rows: List<TripRowUi>, today: Long, onOpen: (String) -> Unit, onEdit: (String) -> Unit, onDelete: (String) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = tag) { Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() }.testTag(tag)) }
    items(rows, key = { it.id }) { r -> TripRow(r, onOpen, onEdit, onDelete) }
}

@Composable
private fun TripRow(r: TripRowUi, onOpen: (String) -> Unit, onEdit: (String) -> Unit, onDelete: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).testTag("trip_row_${r.name}")) {
        Column(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Open the summary of ${r.name}", role = Role.Button) { onOpen(r.id) }.heightIn(min = 48.dp).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp).testTag("trip_open_${r.name}"),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(r.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(r.datesText, style = MaterialTheme.typography.bodyMedium)
            Text(r.statusText, style = MaterialTheme.typography.bodyMedium, color = if (r.phase == TripPhase.ACTIVE && r.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("trip_status_${r.name}"))
            Text("Tag \"${r.tag}\"", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("trip_tag_${r.name}"))
        }
        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onEdit(r.id) }, modifier = Modifier.heightIn(min = 48.dp).testTag("trip_edit_${r.name}")) { Text("Edit") }
            TextButton(onClick = { onDelete(r.id) }, modifier = Modifier.heightIn(min = 48.dp).testTag("trip_delete_${r.name}")) { Text("Delete") }
        }
    }
}

/** A day (since 1970-01-01) as the form's button shows it: "3 Nov 2026". */
private fun dayText(day: Long): String = SearchDates.summary(day, day).orEmpty()

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TripForm(row: TripRowUi?, key: String, tags: List<String>, onSave: TripSave, onClose: () -> Unit) {
    var name by rememberSaveable(key) { mutableStateOf(row?.name.orEmpty()) }
    var tag by rememberSaveable(key) { mutableStateOf(row?.tag.orEmpty()) }
    // while the person has not typed in the tag box, it follows the name: "Trip: Goa"
    var tagTouched by rememberSaveable(key) { mutableStateOf(row != null) }
    var start by rememberSaveable(key) { mutableStateOf(row?.startDay) }
    var end by rememberSaveable(key) { mutableStateOf(row?.endDay) }
    var budget by rememberSaveable(key) { mutableStateOf(row?.budgetPlain.orEmpty()) }
    var enabled by rememberSaveable(key) { mutableStateOf(row?.enabled ?: true) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<TripProblem?>(null) }
    var saving by remember { mutableStateOf(false) }
    var usedBy by remember { mutableStateOf<Long?>(null) }
    fun submit(confirm: Boolean) {
        if (saving) return
        saving = true
        onSave(row?.id, name, tag, start, end, budget, enabled, confirm) { outcome -> saving = false; usedBy = outcome.tagUsedBy; if (outcome.problem == null && outcome.tagUsedBy == null) onClose() else problem = outcome.problem }
    }
    val suggestion = TripRules.suggestedTag(name)
    fun problemFor(vararg fields: TripField) = problem?.takeIf { it.field in fields }

    picking?.let { which ->
        // the date picker counts days as UTC midnights
        val initial = (if (which == "start") start else end) ?: start ?: todayLocal().toEpochDays().toLong()
        val picker = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initial * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { ms -> val d = ms.floorDiv(86_400_000L); if (which == "start") start = d else end = d; problem = null }
                    picking = null
                }, modifier = Modifier.heightIn(min = 48.dp).testTag("trip_date_ok")) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel") } },
        ) { DatePicker(state = picker) }
    }

    Scaffold(topBar = { AreaTopBar(if (row == null) "New trip" else "Edit trip", Area.MONEY, Icons.Outlined.Flight, onClose, "trip_form_back", backLabel = "Cancel") }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).testTag("trip_form"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                name, { v -> name = v; problem = null; if (!tagTouched) tag = TripRules.suggestedTag(v).orEmpty() },
                label = { Text("Trip name") }, placeholder = { Text("Goa") }, singleLine = true, isError = problemFor(TripField.NAME) != null,
                modifier = Modifier.fillMaxWidth().testTag("trip_name"),
            )
            problemFor(TripField.NAME)?.let { Text(it.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("trip_problem_name")) }

            OutlinedTextField(
                tag, { v -> tag = v; tagTouched = true; problem = null },
                label = { Text("Tag for this trip's expenses") }, singleLine = true, isError = problemFor(TripField.TAG) != null,
                supportingText = { Text("A tag is a word you can search and report by. Type any tag you like.") },
                modifier = Modifier.fillMaxWidth().testTag("trip_tag"),
            )
            problemFor(TripField.TAG)?.let { Text(it.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("trip_problem_tag")) }
            val offered = (listOfNotNull(suggestion) + tags).filter { !it.equals(tag, ignoreCase = true) }.distinctBy { it.lowercase() }.take(6)
            if (offered.isNotEmpty()) {
                Text("Or use", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    offered.forEach { t -> AssistChip(onClick = { tag = t; tagTouched = true; problem = null }, label = { Text(t) }, modifier = Modifier.testTag("trip_tag_use_$t")) }
                }
            }

            Text("Days", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = "start" }, modifier = Modifier.heightIn(min = 48.dp).testTag("trip_start")) { Text(start?.let { "First day: ${dayText(it)}" } ?: "Choose the first day") }
                OutlinedButton(onClick = { picking = "end" }, modifier = Modifier.heightIn(min = 48.dp).testTag("trip_end")) { Text(end?.let { "Last day: ${dayText(it)}" } ?: "Choose the last day") }
            }
            val s = start; val en = end
            if (s != null && en != null && en >= s) Text("${en - s + 1} ${if (en - s == 0L) "day" else "days"}, both the first and the last included", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("trip_days_count"))
            problemFor(TripField.START, TripField.END, TripField.DATES)?.let { Text(it.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("trip_problem_dates")) }

            OutlinedTextField(
                budget, { v -> if (v.all { it.isDigit() || it == '.' || it == ',' }) { budget = v; problem = null } },
                label = { Text("Daily budget (${BaseCurrency.current.symbolOrCode()}), optional") }, singleLine = true, isError = problemFor(TripField.BUDGET) != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("trip_budget"),
            )
            problemFor(TripField.BUDGET)?.let { Text(it.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("trip_problem_budget")) }

            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = enabled, role = Role.Switch) { enabled = it; problem = null }.testTag("trip_enabled_row"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Tag new expenses on these days", style = MaterialTheme.typography.bodyLarge)
                    Text("Turn this off to stop tagging without deleting the trip.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = null, modifier = Modifier.testTag("trip_enabled"))
            }

            usedBy?.let { n ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("The tag \"${(app.expenses.core.domain.TagRules.normalize(tag).orEmpty())}\" is already on $n ${if (n == 1L) "entry" else "entries"}. They will count in this trip's total.", color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.testTag("trip_tag_used"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { usedBy = null; submit(true) }, modifier = Modifier.heightIn(min = 48.dp).testTag("trip_tag_use_anyway")) { Text("Use it anyway") }
                        TextButton(onClick = { usedBy = null }, modifier = Modifier.heightIn(min = 48.dp).testTag("trip_tag_choose_other")) { Text("Choose another tag") }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { submit(false) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("trip_save"),
                ) { Text("Save trip") }
                TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp).testTag("trip_cancel")) { Text("Cancel") }
            }
        }
    }
}

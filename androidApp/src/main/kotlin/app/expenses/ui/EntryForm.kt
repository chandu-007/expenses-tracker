package app.expenses.ui

import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.symbolOrCode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import app.expenses.core.domain.CategoryChoices
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.expenses.AccountUi
import app.expenses.CategoryUi
import app.expenses.core.domain.Currency
import app.expenses.core.domain.DraftKind
import app.expenses.core.domain.DraftResult
import app.expenses.core.domain.EntryDraft
import app.expenses.core.domain.EntryDrafts
import app.expenses.core.domain.Money
import app.expenses.core.domain.SplitDraft
import app.expenses.core.domain.TagRules
import app.expenses.core.domain.formatted
import app.expenses.core.domain.toPlainString
import app.expenses.core.domain.ledger.EntryId
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Saves a draft that [EntryDrafts.build] accepted. */
typealias DraftSave = (draft: EntryDraft, saveAsTemplate: Boolean) -> Unit

private val DraftSaver = listSaver<EntryDraft, Any?>(
    save = { d -> listOf(d.id, d.kind.name, d.amountText, d.category, d.accountId, d.toAccountId, d.note, d.occurredAt, d.refundsEntry, d.refundableMinor, d.splits.flatMap { listOf(it.category, it.amountText) }, d.tags, d.personId, d.gave) },
    restore = { l ->
        @Suppress("UNCHECKED_CAST") val parts = (l[10] as List<String>).chunked(2).map { SplitDraft(it[0], it[1]) }
        EntryDraft(l[0] as String?, DraftKind.valueOf(l[1] as String), l[2] as String, l[3] as String, parts, l[4] as String, l[5] as String, l[6] as String, l[7] as Long, l[8] as String?, l[9] as Long, @Suppress("UNCHECKED_CAST") (l[11] as List<String>), l[12] as String, l[13] as Boolean)
    },
)

/**
 * One form for every kind of entry. [kinds] is what the user may switch between (several when adding, one when editing). A split is two
 * or more category parts that must add up to the amount; "left to assign" is shown as the user types. The Save button is enabled only
 * when [EntryDrafts.build] accepts the draft, and otherwise the form says why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryFormScreen(
    title: String,
    start: EntryDraft,
    kinds: List<DraftKind>,
    accounts: List<AccountUi>,
    categories: List<CategoryUi>,
    onCreateCategory: (name: String, parentId: String?, onCreated: (String) -> Unit) -> Unit,
    showTemplateOption: Boolean,
    onSave: DraftSave,
    onBack: () -> Unit,
    refundOf: String? = null,
    onRefund: (() -> Unit)? = null,
    onHistory: (() -> Unit)? = null,
    allTags: List<String> = emptyList(),
    attachments: List<app.expenses.AttachmentUi> = emptyList(),
    onAttach: (android.net.Uri) -> Unit = {},
    onOpenAttachment: (app.expenses.AttachmentUi) -> Unit = {},
    onRemoveAttachment: (app.expenses.AttachmentUi) -> Unit = {},
    attachmentProblem: String? = null,
    people: List<app.expenses.PersonUi> = emptyList(),
    onCreatePerson: (name: String, onCreated: (String) -> Unit) -> Unit = { _, _ -> },
) {
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(start) }
    var saveAsTemplate by rememberSaveable { mutableStateOf(false) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var creatingCategory by rememberSaveable { mutableStateOf(false) }
    var pickingCategory by rememberSaveable { mutableStateOf(false) }
    var creatingPerson by rememberSaveable { mutableStateOf(false) }
    val currency = BaseCurrency.current
    val built = EntryDrafts.build(draft, EntryId("preview"), currency)
    val amount = app.expenses.core.domain.parseAmount(draft.amountText, currency)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (start.amountText.isEmpty()) focus.requestFocus() }
    if (pickingCategory) CategoryPickerDialog(
        categories.filter { !it.hidden || it.id == draft.category }, draft.category,
        onPick = { id -> draft = draft.copy(category = if (draft.kind == DraftKind.INCOME && id == draft.category) "" else id); pickingCategory = false },
        onClose = { pickingCategory = false },
    )
    val zone = ZoneId.systemDefault()
    val dateLabel = DateTimeFormatter.ofPattern("EEE, d MMM yyyy").withZone(zone).format(Instant.ofEpochMilli(draft.occurredAt))
    val split = EntryDrafts.isSplit(draft)
    val activeAccounts = accounts

    if (creatingPerson) {
        var name by rememberSaveable { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { creatingPerson = false }, title = { Text("Who is it?") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.testTag("new_person_name")) },
            confirmButton = { TextButton(onClick = { onCreatePerson(name) { id -> draft = draft.copy(personId = id) }; creatingPerson = false }, enabled = name.isNotBlank(), modifier = Modifier.testTag("new_person_ok")) { Text("Add") } },
            dismissButton = { TextButton(onClick = { creatingPerson = false }) { Text("Cancel") } },
        )
    }
    if (creatingCategory) {
        CategoryNameDialog(
            title = "New category", categories = categories, allowParent = false,
            onConfirm = { name, parent -> onCreateCategory(name, parent) { id -> draft = draft.copy(category = id) }; creatingCategory = false },
            onDismiss = { creatingCategory = false },
        )
    }
    if (pickingDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = Instant.ofEpochMilli(draft.occurredAt).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { utc ->
                        val picked = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        val time = Instant.ofEpochMilli(draft.occurredAt).atZone(zone).toLocalTime()
                        draft = draft.copy(occurredAt = picked.atTime(time).atZone(zone).toInstant().toEpochMilli())
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = picker) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("cancel")) { Text("Cancel") } }) },
        // Pinned above the keyboard so Save is always reachable, however small the screen.
        bottomBar = {
            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            Button(
                onClick = { if (built is DraftResult.Ok) { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm); onSave(draft, saveAsTemplate) } },
                enabled = built is DraftResult.Ok,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).testTag("save"),
            ) { Text("Save") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (kinds.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    kinds.forEach { k ->
                        FilterChip(
                            selected = draft.kind == k,
                            onClick = {
                                // Switching kind keeps what is typed; a category only makes sense for expenses and income.
                                draft = draft.copy(kind = k, splits = if (k == DraftKind.EXPENSE) draft.splits else emptyList(), toAccountId = if (k == DraftKind.TRANSFER) draft.toAccountId.ifEmpty { activeAccounts.firstOrNull { it.id != draft.accountId }?.id.orEmpty() } else draft.toAccountId)
                            },
                            label = { Text(k.label) }, modifier = Modifier.testTag("kind_${k.name}"),
                        )
                    }
                }
            }
            if (refundOf != null) Text("Refund of $refundOf", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("refund_of"))
            OutlinedTextField(
                value = draft.amountText,
                onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) draft = draft.copy(amountText = v) },
                label = { Text("Amount (${BaseCurrency.current.symbolOrCode()})") },
                isError = draft.amountText.isNotEmpty() && amount == null,
                supportingText = {
                    if (draft.amountText.isNotEmpty() && amount == null) Text("Enter an amount with at most 2 decimals")
                    else if (draft.kind == DraftKind.REFUND) Text("Up to ${Money(draft.refundableMinor, currency).formatted()} can still be refunded")
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { if (it.isFocused) app.expenses.StartupTrace.mark("add: amount focused") }.tourSpot("amount").testTag("amount"),
            )
            OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.testTag("date")) { Text(dateLabel) }

            if (draft.kind == DraftKind.LOAN) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = draft.gave, onClick = { draft = draft.copy(gave = true) }, label = { Text("I gave") }, modifier = Modifier.testTag("loan_gave"))
                    FilterChip(selected = !draft.gave, onClick = { draft = draft.copy(gave = false) }, label = { Text("I got") }, modifier = Modifier.testTag("loan_got"))
                }
                Text(if (draft.gave) "Gave to (lending, or paying someone back)" else "Got from (borrowing, or being paid back)", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    people.forEach { p -> FilterChip(selected = p.id == draft.personId, onClick = { draft = draft.copy(personId = p.id) }, label = { Text(p.name) }, modifier = Modifier.testTag("person_chip_${p.name}")) }
                    AssistChip(onClick = { creatingPerson = true }, label = { Text("+ New person") }, modifier = Modifier.testTag("new_person_chip"))
                }
                Text("Lending or borrowing is not spending or income. It moves your account and what this person owes you, which the Lent and borrowed page keeps.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (draft.kind == DraftKind.EXPENSE && !split || draft.kind == DraftKind.INCOME) {
                Text(if (draft.kind == DraftKind.INCOME) "Category (optional)" else "Category", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Hidden categories are not offered, except one already chosen (an entry being edited keeps its category).
                    val offered = categories.filter { !it.hidden || it.id == draft.category }
                    val picks = remember(offered, draft.category) { CategoryChoices.pick(offered.map { it.id }, draft.category.ifEmpty { null }) }
                    val byId = remember(categories) { categories.associateBy { it.id } }
                    picks.shown.mapNotNull { byId[it] }.forEach { c ->
                        FilterChip(
                            selected = c.id == draft.category,
                            onClick = { draft = draft.copy(category = if (draft.kind == DraftKind.INCOME && c.id == draft.category) "" else c.id) },
                            label = { Text(displayName(categories, c)) },
                            leadingIcon = { CategoryAvatar(c.name, c.name, size = 24.dp) },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("category_chip_${c.id}"),
                        )
                    }
                    if (picks.more > 0) AssistChip(onClick = { pickingCategory = true }, label = { Text("More (${picks.more})") }, modifier = Modifier.testTag("more_categories"))
                    AssistChip(onClick = { creatingCategory = true }, label = { Text("+ New") }, modifier = Modifier.testTag("new_category_chip"))
                    if (draft.kind == DraftKind.EXPENSE) {
                        AssistChip(onClick = { draft = draft.copy(splits = listOf(SplitDraft(draft.category, ""), SplitDraft("", ""))) }, label = { Text("Split") }, modifier = Modifier.testTag("split_start"))
                    }
                }
            }

            if (split) SplitEditor(draft, categories, currency) { draft = it }

            val fromLabel = when (draft.kind) { DraftKind.TRANSFER -> "From"; DraftKind.INCOME -> "Received in"; DraftKind.REFUND -> "Refunded to"; DraftKind.EXPENSE -> "Paid from"; DraftKind.LOAN -> if (draft.gave) "Paid from" else "Received in" }
            Text(fromLabel, style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                activeAccounts.forEach { a -> FilterChip(selected = a.id == draft.accountId, onClick = { draft = draft.copy(accountId = a.id) }, label = { Text(a.name) }, modifier = Modifier.testTag("account_chip_${a.name}")) }
            }
            if (draft.kind == DraftKind.TRANSFER) {
                Text("To", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeAccounts.forEach { a -> FilterChip(selected = a.id == draft.toAccountId, onClick = { draft = draft.copy(toAccountId = a.id) }, label = { Text(a.name) }, modifier = Modifier.testTag("to_chip_${a.name}")) }
                }
                Text("Moving money between your own accounts is not spending or income: use it for an ATM withdrawal, paying a card bill, or topping up a wallet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = draft.note, onValueChange = { draft = draft.copy(note = it) },
                label = { Text(if (draft.kind == DraftKind.EXPENSE) "Note or merchant" else "Note") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("note"),
            )
            TagsEditor(draft.tags, allTags) { draft = draft.copy(tags = it) }
            AttachmentsSection(draft.id != null, attachments, attachmentProblem, onAttach, onOpenAttachment, onRemoveAttachment)
            if (built is DraftResult.Problem && (draft.amountText.isNotEmpty() || draft.kind == DraftKind.TRANSFER || draft.kind == DraftKind.LOAN || split)) {
                Text(built.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("form_problem"))
            }
            if (showTemplateOption && draft.kind == DraftKind.EXPENSE && !split) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = saveAsTemplate, onCheckedChange = { saveAsTemplate = it }, modifier = Modifier.testTag("save_template"))
                    Text("Save as a quick entry")
                }
            }
            if (onHistory != null) {
                OutlinedButton(onClick = onHistory, modifier = Modifier.testTag("history_open")) { Text("History of this entry") }
            }
            if (onRefund != null) {
                OutlinedButton(onClick = onRefund, modifier = Modifier.testTag("refund_start")) { Text("Record a refund for this expense") }
            }
        }
    }
}

@Composable
private fun SplitEditor(draft: EntryDraft, categories: List<CategoryUi>, currency: Currency, update: (EntryDraft) -> Unit) {
    val left = EntryDrafts.leftToAssign(draft, currency)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Split across categories", style = MaterialTheme.typography.labelMedium)
        draft.splits.forEachIndexed { i, part ->
            var open by remember { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    OutlinedButton(onClick = { open = true }, modifier = Modifier.testTag("split_category_$i")) {
                        Text(categories.firstOrNull { it.id == part.category }?.let { displayName(categories, it) } ?: "Category")
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        categories.filter { !it.hidden || it.id == part.category }.forEach { c ->
                            DropdownMenuItem(text = { Text(displayName(categories, c)) }, onClick = { open = false; update(draft.copy(splits = draft.splits.toMutableList().also { it[i] = part.copy(category = c.id) })) }, modifier = Modifier.testTag("split_pick_${i}_${c.id}"))
                        }
                    }
                }
                OutlinedTextField(
                    value = part.amountText,
                    onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) update(draft.copy(splits = draft.splits.toMutableList().also { it[i] = part.copy(amountText = v) })) },
                    label = { Text(BaseCurrency.current.symbolOrCode()) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f).testTag("split_amount_$i"),
                )
                TextButton(onClick = { update(draft.copy(splits = draft.splits.filterIndexed { k, _ -> k != i }.let { if (it.size < 2) emptyList() else it }, category = if (draft.splits.size <= 2) draft.splits.getOrNull(1 - i)?.category.orEmpty() else draft.category)) }, modifier = Modifier.testTag("split_remove_$i")) { Text("Remove") }
            }
        }
        left?.let { l ->
            Text(
                when { l == 0L -> "All assigned"; l > 0 -> "${Money(l, currency).formatted()} left to assign"; else -> "${Money(-l, currency).formatted()} too much" },
                style = MaterialTheme.typography.bodySmall, color = if (l < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("split_left"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { update(draft.copy(splits = draft.splits + SplitDraft("", left?.takeIf { it > 0 }?.let { Money(it, currency).toPlainString() } ?: ""))) }, modifier = Modifier.testTag("split_add")) { Text("Add a part") }
        }
    }
}

/** Tags on this entry (tap one to take it off), the most-used existing tags to add with a tap, and a field for a new one. */
@Composable
private fun TagsEditor(tags: List<String>, suggestions: List<String>, onChange: (List<String>) -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    fun add(text: String) {
        val why = TagRules.checkAdd(tags, text)
        if (why != null) { problem = why; return }
        onChange(tags + TagRules.normalize(text)!!); typed = ""; problem = null
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tags (optional)", style = MaterialTheme.typography.labelMedium)
        if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { t -> FilterChip(selected = true, onClick = { onChange(tags - t) }, label = { Text("$t ×") }, modifier = Modifier.testTag("tag_chip_$t")) }
        }
        val offer = suggestions.filter { s -> tags.none { it.equals(s, ignoreCase = true) } }.take(8)
        if (offer.isNotEmpty() && tags.size < TagRules.MAX_PER_ENTRY) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            offer.forEach { s -> AssistChip(onClick = { add(s) }, label = { Text("+ $s") }, modifier = Modifier.testTag("tag_suggest_$s")) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = typed, onValueChange = { typed = it; problem = null }, label = { Text("New tag") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (typed.isNotBlank()) add(typed) }),
                modifier = Modifier.weight(1f).testTag("tag_input"),
            )
            TextButton(onClick = { add(typed) }, enabled = typed.isNotBlank(), modifier = Modifier.testTag("tag_add")) { Text("Add") }
        }
        problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("tag_problem")) }
    }
}

/** Receipts and other files on an existing entry. They are kept on this phone only (not in a backup yet), and the section says so. */
@Composable
private fun AttachmentsSection(saved: Boolean, items: List<app.expenses.AttachmentUi>, problem: String?, onAttach: (android.net.Uri) -> Unit, onOpen: (app.expenses.AttachmentUi) -> Unit, onRemove: (app.expenses.AttachmentUi) -> Unit) {
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { it?.let(onAttach) }
    var removing by remember { mutableStateOf<app.expenses.AttachmentUi?>(null) }
    removing?.let { a ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { removing = null }, title = { Text("Remove \"${a.name}\"?") }, text = { Text("The file is deleted from this phone. The entry stays.") },
            confirmButton = { TextButton(onClick = { onRemove(a); removing = null }, modifier = Modifier.testTag("attachment_remove_ok")) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep") } },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Receipts and files", style = MaterialTheme.typography.labelMedium)
        if (!saved) { Text("Save the entry first, then you can attach a photo or file to it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("attachments_hint")); return }
        items.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().testTag("attachment_${a.name}")) {
                TextButton(onClick = { onOpen(a) }, modifier = Modifier.weight(1f).testTag("attachment_open_${a.name}")) { Text("${a.name} · ${a.sizeText}", maxLines = 1) }
                TextButton(onClick = { removing = a }, modifier = Modifier.testTag("attachment_delete_${a.name}")) { Text("Remove") }
            }
        }
        problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("attachment_problem")) }
        OutlinedButton(onClick = { picker.launch(arrayOf("image/*", "application/pdf", "*/*")) }, modifier = Modifier.testTag("attachment_add")) { Text("Attach a photo or file") }
        Text("Kept on this phone only. A backup does not include them yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Every category in a searchable list, for the ones that did not fit among the chips. With nothing typed they are grouped under their parent (the
 * parent first, its subcategories indented); once something is typed they are one flat list of "Parent › Child" names that match.
 */
@Composable
private fun CategoryPickerDialog(categories: List<CategoryUi>, selected: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    val named = remember(categories) { categories.map { it.id to displayName(categories, it) } }
    val found = remember(named, typed) { CategoryChoices.search(named, typed) }
    val nameOf = remember(named) { named.toMap() }
    val byId = remember(categories) { categories.associateBy { it.id } }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("All categories") },
        text = {
            Column {
                OutlinedTextField(typed, { typed = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("category_search"))
                LazyColumn(Modifier.heightIn(max = 360.dp).testTag("category_list")) {
                    items(found, key = { it }) { id ->
                        val c = byId[id]
                        val grouped = typed.isBlank() && c != null
                        val child = grouped && c!!.parentId != null
                        Text(
                            (if (grouped) c!!.name else nameOf.getValue(id)) + if (id == selected) "  ✓" else "",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (grouped && !child) androidx.compose.ui.text.font.FontWeight.SemiBold else null,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(id) }.padding(start = if (child) 24.dp else 0.dp, top = 12.dp, bottom = 12.dp).testTag("category_option_$id"),
                        )
                    }
                    if (found.isEmpty()) item { Text("No category matches.", modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}

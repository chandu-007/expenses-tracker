package app.expenses.ui

import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.BillEntries
import app.expenses.core.domain.CategoryKind
import app.expenses.core.domain.AmountWords
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import app.expenses.core.domain.BillSplit
import app.expenses.core.domain.parseAmount
import app.expenses.core.domain.EntryDraft
import app.expenses.core.domain.EntryDrafts
import app.expenses.core.domain.Money
import app.expenses.core.domain.SplitDraft
import app.expenses.core.domain.TagRules
import app.expenses.core.domain.formatted
import app.expenses.core.domain.toPlainString
import app.expenses.core.domain.ledger.EntryId
import app.expenses.dateOfMillis
import app.expenses.formatMillis
import app.expenses.systemZone

/**
 * The categories to offer when choosing one for something that is [income] (or an expense, when not): the ones that serve it and are not hidden,
 * plus [keep], the one already chosen (an entry being edited keeps its category even if it no longer serves or was hidden).
 */
internal fun offeredCategories(all: List<CategoryUi>, income: Boolean, keep: String? = null): List<CategoryUi> =
    all.filter { (it.kind.serves(income) && !it.hidden) || it.id == keep }

/** The category a draft of this kind should be on: [current] if it serves, else [last] if it serves and is visible, else the first visible one that serves, else none. */
internal fun categoryFor(all: List<CategoryUi>, income: Boolean, current: String, last: String): String {
    fun serving(id: String) = all.firstOrNull { it.id == id }?.takeIf { it.kind.serves(income) }
    return serving(current)?.id ?: serving(last)?.takeIf { !it.hidden }?.id ?: all.firstOrNull { it.kind.serves(income) && !it.hidden }?.id.orEmpty()
}

/** The account an expense being edited was probably taken out to as cash: the person's only account of type CASH, when it is not the account the expense was paid from. Null otherwise. */
internal fun cashWithdrawalAccount(accounts: List<AccountUi>, fromId: String): AccountUi? = accounts.filter { it.type == "CASH" }.singleOrNull()?.takeIf { it.id != fromId }

/**
 * The draft after the kind chip [k] is tapped. Adding: what is typed is kept; a category that does not serve the new kind is swapped for the last one used; a transfer starts on
 * another account. Editing ([start] is the saved entry) is the same, and besides: going back to the saved type puts back everything the saved entry had (category, split, accounts),
 * so flipping through the chips never loses any of it; an income that becomes a transfer is money that arrived in its account (the account is the "To"), a transfer that becomes
 * income arrived in its "To" account; and an expense that becomes a transfer goes to the only Cash account when there is one (a cash withdrawal).
 */
internal fun switchedKind(draft: EntryDraft, start: EntryDraft, k: DraftKind, categories: List<CategoryUi>, accounts: List<AccountUi>, lastExpenseCategory: String, lastIncomeCategory: String): EntryDraft {
    val editing = start.id != null
    if (editing && k != draft.kind && k == start.kind) return draft.copy(kind = k, category = start.category, splits = start.splits, accountId = start.accountId, toAccountId = start.toAccountId)
    // Switching kind keeps what is typed; a category only makes sense for expenses and income, and each has its own: one that does not serve the new kind is swapped for the last one used.
    val swapped = when {
        k == draft.kind -> draft.category
        k == DraftKind.INCOME -> categoryFor(categories, true, draft.category, lastIncomeCategory)
        k == DraftKind.EXPENSE || k == DraftKind.REFUND -> categoryFor(categories, false, draft.category, lastExpenseCategory)
        else -> draft.category
    }
    var from = draft.accountId; var to = draft.toAccountId
    if (editing && k != draft.kind) {
        if (draft.kind == DraftKind.INCOME && k == DraftKind.TRANSFER) { to = draft.accountId; from = accounts.firstOrNull { it.id != draft.accountId }?.id.orEmpty() }
        if (draft.kind == DraftKind.TRANSFER && k == DraftKind.INCOME) from = draft.toAccountId.ifEmpty { draft.accountId }
        if (draft.kind == DraftKind.EXPENSE && k == DraftKind.TRANSFER) cashWithdrawalAccount(accounts, draft.accountId)?.let { to = it.id }
    }
    return draft.copy(kind = k, category = swapped, splits = if (k == DraftKind.EXPENSE) draft.splits else emptyList(), accountId = from, toAccountId = if (k == DraftKind.TRANSFER) to.ifEmpty { accounts.firstOrNull { it.id != from }?.id.orEmpty() } else to)
}

/** What saving will do when the type of the entry being edited was changed: said under the chips. */
internal fun kindChangeNote(from: DraftKind, to: DraftKind, wasSplit: Boolean, cashName: String?): String {
    fun noun(k: DraftKind) = when (k) { DraftKind.EXPENSE -> "expense"; DraftKind.INCOME -> "income"; DraftKind.TRANSFER -> "transfer"; else -> k.label.lowercase() }
    val into = when (to) { DraftKind.EXPENSE -> "an expense"; DraftKind.INCOME -> "income"; DraftKind.TRANSFER -> "a transfer"; else -> to.label.lowercase() }
    return "Saving changes this ${noun(from)} into $into. You can undo it right after." +
        (if (cashName != null) " $cashName is chosen as the account the money went to, as for a cash withdrawal." else "") +
        (if (wasSplit && to != DraftKind.EXPENSE) " The category split will be removed." else "")
}

/** Saves a draft that [EntryDrafts.build] accepted. */
typealias DraftSave = (draft: EntryDraft, saveAsTemplate: Boolean) -> Unit

private val DraftSaver = listSaver<EntryDraft, Any?>(
    save = { d -> listOf(d.id, d.kind.name, d.amountText, d.category, d.accountId, d.toAccountId, d.note, d.occurredAt, d.refundsEntry, d.refundableMinor, d.splits.flatMap { listOf(it.category, it.amountText) }, d.tags, d.personId, d.gave, d.splitWith, d.foreignCode, d.foreignAmount, d.foreignRate, d.splitAmounts, d.paidBy, d.expectBack, d.expectFrom, d.expectPersonId, d.expectAmount) },
    restore = { l ->
        @Suppress("UNCHECKED_CAST") val parts = (l[10] as List<String>).chunked(2).map { SplitDraft(it[0], it[1]) }
        EntryDraft(l[0] as String?, DraftKind.valueOf(l[1] as String), l[2] as String, l[3] as String, parts, l[4] as String, l[5] as String, l[6] as String, l[7] as Long, l[8] as String?, l[9] as Long, @Suppress("UNCHECKED_CAST") (l[11] as List<String>), l[12] as String, l[13] as Boolean, @Suppress("UNCHECKED_CAST") (l[14] as List<String>), l[15] as String, l[16] as String, l[17] as String, @Suppress("UNCHECKED_CAST") (l[18] as List<String>), l[19] as String, l[20] as Boolean, l[21] as String, l[22] as String, l[23] as String)
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
    /** Creates a category of the given kind: an expense or refund form asks for an expense category, an income form for an income one. */
    onCreateCategory: (name: String, parentId: String?, kind: CategoryKind, onCreated: (String) -> Unit) -> Unit,
    showTemplateOption: Boolean,
    onSave: DraftSave,
    onBack: () -> Unit,
    refundOf: String? = null,
    onRefund: (() -> Unit)? = null,
    onHistory: (() -> Unit)? = null,
    allTags: List<String> = emptyList(),
    attachments: List<app.expenses.AttachmentUi> = emptyList(),
    onAttach: (String) -> Unit = {},
    onOpenAttachment: (app.expenses.AttachmentUi) -> Unit = {},
    onRemoveAttachment: (app.expenses.AttachmentUi) -> Unit = {},
    attachmentProblem: String? = null,
    /** Why saving a changed type was refused (the entry is tied to something that would be left behind): shown under the form while the type is still changed. */
    kindChangeProblem: String? = null,
    people: List<app.expenses.PersonUi> = emptyList(),
    /** The rate used last for each foreign currency (units of the ledger's currency for one), to start the next entry in it from. */
    lastRates: Map<String, String> = emptyMap(),
    onCreatePerson: (name: String, onCreated: (String) -> Unit) -> Unit = { _, _ -> },
    /** The expense and income categories used last: where a draft lands when the kind is switched. */
    lastExpenseCategory: String = "",
    lastIncomeCategory: String = "",
    /** Where the entry was read from (a text, notification, email, statement line); null for one typed by hand. */
    origin: app.expenses.OriginUi? = null,
    /** Saves several new expenses at once (the items of a bill, each its own entry) and the photo the bill was read from; null where the form cannot (an edit). */
    onSaveEach: ((drafts: List<EntryDraft>, photoUri: String?) -> Unit)? = null,
) {
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(start) }
    var saveAsTemplate by rememberSaveable { mutableStateOf(false) }
    // editing: the only Cash account was chosen for a cash withdrawal by the type change itself, not by the person (said in the note under the chips)
    var cashPicked by rememberSaveable { mutableStateOf(false) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var creatingCategory by rememberSaveable { mutableStateOf(false) }
    var pickingCategory by rememberSaveable { mutableStateOf(false) }
    var creatingPerson by rememberSaveable { mutableStateOf(false) }
    var splitMode by rememberSaveable { mutableStateOf(if (start.paidBy.isNotEmpty()) 2 else if (start.splitAmounts.isNotEmpty()) 1 else 0) } // 0 equally, 1 by typed amounts, 2 a friend paid
    val currency = BaseCurrency.current
    val built = EntryDrafts.build(draft, EntryId("preview"), currency)
    val amount = app.expenses.core.domain.parseAmount(draft.amountText, currency)
    val focus = remember { FocusRequester() }
    var padOn by remember { mutableStateOf(false) }
    val reduceMotion = LocalReduceMotion.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val platform = LocalPlatform.current
    LaunchedEffect(padOn) { if (padOn) keyboard?.hide() }
    LaunchedEffect(Unit) { if (start.amountText.isEmpty()) focus.requestFocus() }
    val forIncome = draft.kind == DraftKind.INCOME
    if (pickingCategory) CategoryPickerDialog(
        offeredCategories(categories, forIncome, draft.category), draft.category,
        onPick = { id -> draft = draft.copy(category = if (draft.kind == DraftKind.INCOME && id == draft.category) "" else id); pickingCategory = false },
        onClose = { pickingCategory = false },
    )
    val zone = systemZone()
    val dateLabel = formatMillis(draft.occurredAt, "EEE, d MMM yyyy", zone)
    val split = EntryDrafts.isSplit(draft)
    val activeAccounts = accounts

    if (creatingPerson) {
        var name by rememberSaveable { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { creatingPerson = false }, title = { Text("Who is it?") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.testTag("new_person_name")) },
            confirmButton = { TextButton(onClick = { onCreatePerson(name) { id -> draft = if (draft.kind == DraftKind.EXPENSE && splitMode == 2) draft.copy(paidBy = id) else if (draft.kind == DraftKind.EXPENSE) draft.copy(splitWith = (draft.splitWith + id).distinct(), splitAmounts = if (splitMode == 1) draft.splitAmounts + "" else draft.splitAmounts) else draft.copy(personId = id) }; creatingPerson = false }, enabled = name.isNotBlank(), modifier = Modifier.testTag("new_person_ok")) { Text("Add") } },
            dismissButton = { TextButton(onClick = { creatingPerson = false }) { Text("Cancel") } },
        )
    }
    if (creatingCategory) {
        CategoryNameDialog(
            title = "New category", categories = categories, allowParent = false,
            onConfirm = { name, parent -> onCreateCategory(name, parent, if (forIncome) CategoryKind.INCOME else CategoryKind.EXPENSE) { id -> draft = draft.copy(category = id) }; creatingCategory = false },
            onDismiss = { creatingCategory = false },
        )
    }
    if (pickingDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = utcMidnightMillis(dateOfMillis(draft.occurredAt, zone)))
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { utc ->
                        val time = localTimeOf(draft.occurredAt, zone)
                        draft = draft.copy(occurredAt = millisOf(pickerDate(utc), time, zone))
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = picker) }
    }

    Surface(
        modifier = Modifier.fillMaxSize().then(if (start.id != null) Modifier.sharedContainer("entry:${start.id}") else Modifier),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)).testTag("sheet_handle"))
                }
                TopAppBar(
                    title = { Text(title) }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("cancel")) { Text("Cancel") } },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                )
            }
        },
        // Pinned above the keyboard so Save is always reachable, however small the screen.
        bottomBar = {
            val feel = rememberFeel()
            Column {
                AnimatedVisibility(
                    visible = padOn,
                    enter = if (reduceMotion) EnterTransition.None else expandVertically(tween(Motion.STANDARD, easing = Motion.EaseOut)) + fadeIn(tween(Motion.STANDARD)),
                    exit = if (reduceMotion) ExitTransition.None else shrinkVertically(tween(Motion.QUICK)) + fadeOut(tween(Motion.QUICK)),
                ) {
                    NumberPad { key -> draft = draft.copy(amountText = padPress(draft.amountText, key, currency.minorUnitDigits)) }
                }
                Button(
                    onClick = { if (built is DraftResult.Ok) { feel(Feel.SAVE); onSave(draft, saveAsTemplate) } },
                    enabled = built is DraftResult.Ok,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).testTag("save"),
                ) { Text("Save") }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (kinds.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    kinds.forEach { k ->
                        FilterChip(
                            selected = draft.kind == k,
                            onClick = {
                                if (k != draft.kind) cashPicked = start.id != null && k == DraftKind.TRANSFER && draft.kind == DraftKind.EXPENSE && cashWithdrawalAccount(activeAccounts, draft.accountId) != null
                                draft = switchedKind(draft, start, k, categories, activeAccounts, lastExpenseCategory, lastIncomeCategory)
                            },
                            label = { Text(k.label) }, modifier = Modifier.testTag("kind_${k.name}"),
                        )
                    }
                }
                if (start.id != null && draft.kind != start.kind) Text(
                    kindChangeNote(start.kind, draft.kind, start.splits.size >= 2, activeAccounts.firstOrNull { it.id == draft.toAccountId }?.name?.takeIf { cashPicked && draft.kind == DraftKind.TRANSFER }),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("kind_change_note"),
                )
                if (kindChangeProblem != null && start.id != null && draft.kind != start.kind) Text(kindChangeProblem, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("kind_change_problem"))
            }
            if (refundOf != null) Text("Refund of $refundOf", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("refund_of"))
            Column {
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
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { if (it.isFocused) platform.trace("add: amount focused") }.tourSpot("amount").testTag("amount"),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val words = amount?.takeIf { currency.minorUnitDigits == 2 && it.minorUnits >= 100_000L && !LocalHideAmounts.current }?.let { AmountWords.of(it.minorUnits) }
                    if (words != null) {
                        Text(words, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(start = 16.dp).testTag("amount_words"))
                    } else Spacer(Modifier.weight(1f))
                    TextButton(onClick = { padOn = !padOn }, modifier = Modifier.testTag("amount_pad_toggle")) { Text(if (padOn) "Keyboard" else "Pad") }
                }
            }
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
                    val offered = offeredCategories(categories, forIncome, draft.category)
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
                    activeAccounts.forEach { a -> FilterChip(selected = a.id == draft.toAccountId, onClick = { cashPicked = false; draft = draft.copy(toAccountId = a.id) }, label = { Text(a.name) }, modifier = Modifier.testTag("to_chip_${a.name}")) }
                }
                Text("Moving money between your own accounts is not spending or income: use it for an ATM withdrawal, paying a card bill, or topping up a wallet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // a new expense paid in another currency (travel): what was paid there and the rate you worked out; the app never looks a rate up
            if (draft.kind == DraftKind.EXPENSE && draft.id == null) {
                var abroad by rememberSaveable { mutableStateOf(draft.foreignCode.isNotEmpty()) }
                FilterChip(selected = abroad, onClick = { abroad = !abroad; if (!abroad) draft = draft.copy(foreignCode = "", foreignAmount = "", foreignRate = "") }, label = { Text("Paid in another currency") }, modifier = Modifier.testTag("foreign_toggle"))
                if (abroad) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        app.expenses.core.domain.CurrencyChoices.ALL.filter { it.code != currency.code }.forEach { c ->
                            FilterChip(selected = draft.foreignCode == c.code, onClick = { draft = draft.copy(foreignCode = c.code, foreignRate = draft.foreignRate.ifEmpty { lastRates[c.code].orEmpty() }) }, label = { Text(c.code) }, modifier = Modifier.testTag("foreign_cur_${c.code}"))
                        }
                    }
                    if (draft.foreignCode.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(draft.foreignAmount, { v -> if (v.all { it.isDigit() || it == '.' }) draft = draft.copy(foreignAmount = v) }, label = { Text("Paid (${draft.foreignCode})") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f).testTag("foreign_amount"))
                            OutlinedTextField(draft.foreignRate, { v -> if (v.all { it.isDigit() || it == '.' }) draft = draft.copy(foreignRate = v) }, label = { Text("1 ${draft.foreignCode} = ${currency.code}") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f).testTag("foreign_rate"))
                        }
                        val converted = app.expenses.core.domain.ForeignAmount.convert(draft.foreignAmount, draft.foreignRate, app.expenses.core.domain.CurrencyChoices.byCode(draft.foreignCode), currency)?.first
                        // the amount above follows what is worked out here
                        androidx.compose.runtime.LaunchedEffect(converted) { if (converted != null && draft.amountText != converted.toPlainString()) draft = draft.copy(amountText = converted.toPlainString()) }
                        Text(
                            converted?.let { "That is ${it.formatted()} from your account, at the rate you chose." } ?: "Enter what you paid and your rate. The rate is yours: the app does not look rates up.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("foreign_summary"),
                        )
                    }
                }
            }

            // a new expense shared with friends: split equally, split by amounts you type, or paid by a friend (then the amount is your share)
            if (draft.kind == DraftKind.EXPENSE && draft.id == null && !split) {
                var sharing by rememberSaveable { mutableStateOf(draft.splitWith.isNotEmpty() || draft.paidBy.isNotEmpty()) }
                val mode = splitMode
                FilterChip(selected = sharing, onClick = { sharing = !sharing; if (!sharing) draft = draft.copy(splitWith = emptyList(), splitAmounts = emptyList(), paidBy = "") }, label = { Text("Split with friends") }, modifier = Modifier.testTag("split_toggle"))
                if (sharing) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Equally" to 0, "My amounts" to 1, "A friend paid" to 2).forEach { (label, m) ->
                            FilterChip(selected = mode == m, onClick = { splitMode = m; draft = draft.copy(splitWith = emptyList(), splitAmounts = emptyList(), paidBy = "") }, label = { Text(label) }, modifier = Modifier.testTag("split_mode_$m"))
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        people.forEach { p ->
                            if (mode == 2) FilterChip(selected = draft.paidBy == p.id, onClick = { draft = draft.copy(paidBy = if (draft.paidBy == p.id) "" else p.id) }, label = { Text(p.name) }, modifier = Modifier.testTag("split_payer_${p.name}"))
                            else {
                                val at = draft.splitWith.indexOf(p.id)
                                FilterChip(
                                    selected = at >= 0,
                                    onClick = {
                                        draft = if (at >= 0) draft.copy(splitWith = draft.splitWith - p.id, splitAmounts = if (mode == 1) draft.splitAmounts.filterIndexed { i, _ -> i != at } else emptyList())
                                        else draft.copy(splitWith = draft.splitWith + p.id, splitAmounts = if (mode == 1) draft.splitAmounts + "" else emptyList())
                                    },
                                    label = { Text(p.name) }, modifier = Modifier.testTag("split_person_${p.name}"),
                                )
                            }
                        }
                        AssistChip(onClick = { creatingPerson = true }, label = { Text("+ New person") }, modifier = Modifier.testTag("split_new_person"))
                    }
                    if (mode == 1) draft.splitWith.forEachIndexed { i, id ->
                        val name = people.firstOrNull { it.id == id }?.name.orEmpty()
                        OutlinedTextField(
                            draft.splitAmounts.getOrElse(i) { "" }, { v -> if (v.all { it.isDigit() || it == '.' }) draft = draft.copy(splitAmounts = draft.splitAmounts.toMutableList().also { l -> while (l.size <= i) l += ""; l[i] = v }) },
                            label = { Text("$name owes") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("split_amount_$name"),
                        )
                    }
                    val owed = draft.splitAmounts.map { parseAmount(it, currency)?.minorUnits ?: 0L }
                    val shares = amount?.let { BillSplit.shares(it.minorUnits, draft.splitWith.size) }
                    val mineCustom = amount?.let { BillSplit.mineAfter(it.minorUnits, owed) }
                    Text(
                        when {
                            mode == 2 -> if (draft.paidBy.isEmpty()) "Choose who paid. Enter your share of the bill as the amount; you will owe them that, and no account of yours moves." else "You owe ${people.firstOrNull { it.id == draft.paidBy }?.name.orEmpty()} ${amount?.formatted() ?: "your share"}. No account of yours moves."
                            draft.splitWith.isEmpty() -> "Choose who shares this bill."
                            mode == 1 -> if (mineCustom == null) "Enter the whole bill, then what each friend owes. Your part is what is left." else "You pay ${Money(mineCustom, currency).formatted()} of it. The whole bill leaves your account."
                            shares == null -> "Enter the whole bill to see each share."
                            else -> "You pay ${Money(shares.second, currency).formatted()} of it; each of the ${draft.splitWith.size} owes ${Money(shares.first, currency).formatted()}. The whole bill leaves your account."
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("split_summary"),
                    )
                }
            }

            // "I expect this back": a friend or an employer will pay it back. Not for a shared expense (what friends owe is already tracked there).
            val shared = draft.splitWith.isNotEmpty() || draft.paidBy.isNotEmpty()
            LaunchedEffect(shared) { if (shared && draft.expectBack) draft = draft.copy(expectBack = false, expectFrom = "", expectPersonId = "", expectAmount = "") }
            // (an entry being edited that was not an expense and is being made one cannot be expected back: that is set on an expense once it exists)
            if (draft.kind == DraftKind.EXPENSE && !shared && (start.id == null || start.kind == DraftKind.EXPENSE)) {
                FilterChip(selected = draft.expectBack, onClick = { draft = if (draft.expectBack) draft.copy(expectBack = false, expectFrom = "", expectPersonId = "", expectAmount = "") else draft.copy(expectBack = true) }, label = { Text("I expect this back") }, modifier = Modifier.testTag("expect_toggle"))
                if (draft.expectBack) {
                    if (people.isNotEmpty()) {
                        Text("From (optional)", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            people.forEach { p -> FilterChip(selected = p.id == draft.expectPersonId, onClick = { draft = if (p.id == draft.expectPersonId) draft.copy(expectPersonId = "") else draft.copy(expectPersonId = p.id, expectFrom = "") }, label = { Text(p.name) }, modifier = Modifier.testTag("expect_person_${p.name}")) }
                        }
                    }
                    OutlinedTextField(
                        value = draft.expectFrom, onValueChange = { v -> draft = draft.copy(expectFrom = v.take(80), expectPersonId = if (v.isNotBlank()) "" else draft.expectPersonId) },
                        label = { Text(if (people.isEmpty()) "From (optional)" else "Or type who (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("expect_from"),
                    )
                    val expectInvalid = draft.expectAmount.isNotEmpty() && amount != null && app.expenses.core.domain.ExpectMath.expected(draft.expectAmount, amount.minorUnits) { parseAmount(it, currency)?.minorUnits } == null
                    OutlinedTextField(
                        value = draft.expectAmount, onValueChange = { v -> if (v.all { it.isDigit() || it == '.' }) draft = draft.copy(expectAmount = v) },
                        label = { Text("Amount expected back (${currency.symbolOrCode()}), empty for all of it") }, singleLine = true, isError = expectInvalid,
                        supportingText = { if (expectInvalid) Text("More than zero and no more than the expense") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("expect_amount"),
                    )
                    Text(
                        "It stays in Waiting to get back until you record the money coming in. The expense still counts as spending in your reports until then; when you record it, it is a refund and the spending goes down.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("expect_note"),
                    )
                }
            }

            OutlinedTextField(
                value = draft.note, onValueChange = { draft = draft.copy(note = it) },
                label = { Text(if (draft.kind == DraftKind.EXPENSE) "Note or merchant" else "Note") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("note"),
            )
            TagsEditor(draft.tags, allTags) { draft = draft.copy(tags = it) }
            AttachmentsSection(draft.id != null, attachments, attachmentProblem, onAttach, onOpenAttachment, onRemoveAttachment, extra = {
                if (draft.kind == DraftKind.EXPENSE) {
                    // each item as its own entry: only for a new plain expense (one account and one category, paid in the ledger's currency, not shared)
                    val eachNotice = when {
                        onSaveEach == null || draft.id != null -> null
                        split || draft.foreignCode.isNotEmpty() || EntryDrafts.isSplit(draft) -> "Items cannot be saved one by one for a shared or foreign-currency expense."
                        draft.category.isBlank() || draft.accountId.isBlank() -> "Choose a category and an account on the form first to save the items one by one."
                        else -> null
                    }
                    val canAddEach = onSaveEach != null && draft.id == null && eachNotice == null
                    BillItemsReader(
                        amountEmpty = draft.amountText.isBlank(), note = draft.note, canAttach = attachments.isEmpty(),
                        canAddEach = canAddEach, eachNotice = eachNotice,
                        onAddEach = { items, rest, photo -> onSaveEach?.invoke(BillEntries.drafts(draft, items, rest, currency), photo) },
                    ) { amountText, note, photo ->
                        draft = draft.copy(amountText = amountText ?: draft.amountText, note = note)
                        photo?.let(onAttach)
                    }
                }
            })
            if (built is DraftResult.Problem && (draft.amountText.isNotEmpty() || draft.kind == DraftKind.TRANSFER || draft.kind == DraftKind.LOAN || split)) {
                Text(built.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("form_problem"))
            }
            if (showTemplateOption && draft.kind == DraftKind.EXPENSE && !split) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = saveAsTemplate, onCheckedChange = { saveAsTemplate = it }, modifier = Modifier.testTag("save_template"))
                    Text("Save as a quick entry")
                }
            }
            if (origin != null) OriginSection(origin)
            if (onHistory != null) {
                OutlinedButton(onClick = onHistory, modifier = Modifier.testTag("history_open")) { Text("History of this entry") }
            }
            if (onRefund != null && draft.kind == start.kind) {
                OutlinedButton(onClick = onRefund, modifier = Modifier.testTag("refund_start")) { Text("Record a refund for this expense") }
            }
        }
    }
    }
}

/** The pad's keys in reading order: 1-9, then dot, 0, backspace. */
private val PadKeys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "<")

/** Applies one pad key to the amount text with the same rules as typing: digits and one dot, at most [decimals] after the dot. */
private fun padPress(text: String, key: String, decimals: Int): String = when (key) {
    "<" -> text.dropLast(1)
    "." -> if (decimals == 0 || '.' in text) text else if (text.isEmpty()) "0." else "$text."
    else -> if ('.' in text && text.substringAfter('.').length >= decimals) text else if (text == "0") key else text + key
}

@Composable
private fun NumberPad(onKey: (String) -> Unit) {
    val feel = rememberFeel()
    val reduce = LocalReduceMotion.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("number_pad"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PadKeys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val pressed by source.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        if (pressed && !reduce) 0.92f else 1f,
                        if (reduce) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "padKey",
                    )
                    val tag = when (key) { "." -> "pad_dot"; "<" -> "pad_back"; else -> "pad_$key" }
                    Box(
                        Modifier.weight(1f).height(48.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                            .clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(interactionSource = source, indication = null) { feel(Feel.TICK); onKey(key) }
                            .semantics { contentDescription = when (key) { "<" -> "Backspace"; "." -> "Decimal point"; else -> key } }
                            .testTag(tag),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (key == "<") "⌫" else key, style = MaterialTheme.typography.titleLarge) }
                }
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
                        offeredCategories(categories, false, part.category).forEach { c ->
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

/** Receipts and other files on an existing entry. They are kept on this phone and go into a backup unless the person switched that off under Your data; the section says so. */
@Composable
private fun AttachmentsSection(saved: Boolean, items: List<app.expenses.AttachmentUi>, problem: String?, onAttach: (String) -> Unit, onOpen: (app.expenses.AttachmentUi) -> Unit, onRemove: (app.expenses.AttachmentUi) -> Unit, extra: @Composable () -> Unit = {}) {
    val picker = rememberFilePickers()
    var removing by remember { mutableStateOf<app.expenses.AttachmentUi?>(null) }
    // a photo whose screen was recreated while the camera was open arrives here
    LaunchedEffect(picker.recoveredPhoto) { picker.recoveredPhoto?.let { onAttach(it); picker.consumeRecoveredPhoto() } }
    removing?.let { a ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { removing = null }, title = { Text("Remove \"${a.name}\"?") }, text = { Text("The file is deleted from this phone. The entry stays.") },
            confirmButton = { TextButton(onClick = { onRemove(a); removing = null }, modifier = Modifier.testTag("attachment_remove_ok")) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep") } },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Receipts and files", style = MaterialTheme.typography.labelMedium)
        items.forEach { a ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().testTag("attachment_${a.name}")) {
                TextButton(onClick = { onOpen(a) }, modifier = Modifier.weight(1f).testTag("attachment_open_${a.name}")) { Text("${a.name} · ${a.sizeText}", maxLines = 1) }
                TextButton(onClick = { removing = a }, modifier = Modifier.testTag("attachment_delete_${a.name}")) { Text("Remove") }
            }
        }
        problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("attachment_problem")) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (picker.canTakePhoto) OutlinedButton(onClick = { picker.takePhoto { it?.let(onAttach) } }, modifier = Modifier.testTag("attachment_camera")) { Text("Take a photo") }
            OutlinedButton(onClick = { picker.openDocument(listOf("image/*", "application/pdf", "*/*")) { it?.let(onAttach) } }, modifier = Modifier.testTag("attachment_add")) { Text("Attach a photo or file") }
        }
        extra()
        Text(if (saved) "Kept on this phone, and included in a backup unless you switch that off under Your data." else "Optional. Added to the entry when you save it. Kept on this phone, and included in a backup unless you switch that off under Your data.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("attachments_hint"))
    }
}

/**
 * Every category in a searchable list, for the ones that did not fit among the chips. With nothing typed they are grouped under their parent (the
 * parent first, its subcategories indented); once something is typed they are one flat list of "Parent › Child" names that match.
 */
@Composable
internal fun CategoryPickerDialog(categories: List<CategoryUi>, selected: String, onPick: (String) -> Unit, onClose: () -> Unit) {
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

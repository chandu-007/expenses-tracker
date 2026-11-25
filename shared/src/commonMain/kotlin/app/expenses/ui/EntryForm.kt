package app.expenses.ui

import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.BillEntries
import app.expenses.core.domain.CategoryKind
import app.expenses.core.domain.AmountWords
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import app.expenses.core.domain.AmountMath
import app.expenses.core.domain.DesignTokens
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.text

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
    save = { d -> listOf(d.id, d.kind.name, d.amountText, d.category, d.accountId, d.toAccountId, d.note, d.occurredAt, d.refundsEntry, d.refundableMinor, d.splits.flatMap { listOf(it.category, it.amountText) }, d.tags, d.personId, d.gave, d.splitWith, d.foreignCode, d.foreignAmount, d.foreignRate, d.splitAmounts, d.paidBy, d.expectBack, d.expectFrom, d.expectPersonId, d.expectAmount, d.noTripTag) },
    restore = { l ->
        @Suppress("UNCHECKED_CAST") val parts = (l[10] as List<String>).chunked(2).map { SplitDraft(it[0], it[1]) }
        EntryDraft(l[0] as String?, DraftKind.valueOf(l[1] as String), l[2] as String, l[3] as String, parts, l[4] as String, l[5] as String, l[6] as String, l[7] as Long, l[8] as String?, l[9] as Long, @Suppress("UNCHECKED_CAST") (l[11] as List<String>), l[12] as String, l[13] as Boolean, @Suppress("UNCHECKED_CAST") (l[14] as List<String>), l[15] as String, l[16] as String, l[17] as String, @Suppress("UNCHECKED_CAST") (l[18] as List<String>), l[19] as String, l[20] as Boolean, l[21] as String, l[22] as String, l[23] as String, l[24] as Boolean)
    },
)

/**
 * One form for every kind of entry, on the S1 tokens and amount first (UI S2): the amount big at the top with maths (`450+800` is worked out as it is typed) and the app's pad under it, a row of
 * six category tiles (the most used first) and "More", the note, the account and one "More" chip for what is rarely needed (split, paid in another currency, shared with friends, expect back, tags, receipts,
 * save as a quick entry). The big button says what it will do ("Add ₹1,250 · Food"). [kinds] is what the user may switch between (several when adding, one when editing). A split is two or more category parts
 * that must add up to the amount; "left to assign" is shown as the user types. The button is enabled only when [EntryDrafts.build] accepts the draft, and otherwise the form says why.
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
    /** A category for an income that has none, from what the app knows of the payer (a rule, their own earlier choices, the words of the text); one tap puts it on the entry. */
    suggestedCategory: app.expenses.SuggestedCategoryUi? = null,
    /** Saves several new expenses at once (the items of a bill, each its own entry) and the photo the bill was read from; null where the form cannot (an edit). */
    onSaveEach: ((drafts: List<EntryDraft>, photoUri: String?) -> Unit)? = null,
    /** What was typed so far, when this form replaces another instance of the same entry (the window turned wide or narrow): it starts from this instead of [start]. */
    restored: EntryDraft? = null,
    /** Told of every change of the draft, so [restored] can be given to the next instance. */
    onDraft: ((EntryDraft) -> Unit)? = null,
    /** Travel mode: the trips that are on. A new expense dated inside one shows "Tagged for Goa trip" (the tag is added when it is saved) with a cross that takes it off for this entry. */
    tripWindows: List<app.expenses.TripWindowUi> = emptyList(),
    /** What the person usually puts a payee like the note typed in under ("Usually Food for Chai"), or null: asked a moment after the note stops changing. */
    usualCategory: (suspend (note: String) -> UsualCategoryUi?)? = null,

) {
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(restored ?: start) }
    // a SideEffect, not a LaunchedEffect: it runs in the frame that drew the change, before anything that follows it (a fold) can take this form out of the composition
    if (onDraft != null) SideEffect { onDraft(draft) }
    var saveAsTemplate by rememberSaveable { mutableStateOf(false) }
    // editing: the only Cash account was chosen for a cash withdrawal by the type change itself, not by the person (said in the note under the chips)
    var cashPicked by rememberSaveable { mutableStateOf(false) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var creatingCategory by rememberSaveable { mutableStateOf(false) }
    var pickingCategory by rememberSaveable { mutableStateOf(false) }
    var creatingPerson by rememberSaveable { mutableStateOf(false) }
    var splitMode by rememberSaveable { mutableStateOf((restored ?: start).let { if (it.paidBy.isNotEmpty()) 2 else if (it.splitAmounts.isNotEmpty()) 1 else 0 }) } // 0 equally, 1 by typed amounts, 2 a friend paid
    val currency = BaseCurrency.current
    // what is typed in the amount: a number, or maths ("450+800"); the draft holds what it comes to, so everything else (saving, the split, the checks) sees one plain amount
    var expr by rememberSaveable { mutableStateOf((restored ?: start).amountText) }
    val maths = AmountMath.evaluate(expr, currency.minorUnitDigits)
    val exprComplete = maths is AmountMath.Outcome.Plain || (maths is AmountMath.Outcome.Value && maths.complete)
    val commitAmount: (String) -> Unit = { text ->
        val out = AmountMath.evaluate(text, currency.minorUnitDigits)
        expr = text
        draft = draft.copy(amountText = when (out) { is AmountMath.Outcome.Plain -> out.text; is AmountMath.Outcome.Value -> out.text; AmountMath.Outcome.Error -> "" })
    }
    // an amount set from outside (the foreign rate, a bill read from a photo, a restored draft) replaces what was typed
    LaunchedEffect(draft.amountText) {
        val shown = AmountMath.evaluate(expr, currency.minorUnitDigits).let { when (it) { is AmountMath.Outcome.Plain -> it.text; is AmountMath.Outcome.Value -> it.text; AmountMath.Outcome.Error -> "" } }
        if (draft.amountText != shown) expr = draft.amountText
    }
    val built = EntryDrafts.build(draft, EntryId("preview"), currency)
    val amount = app.expenses.core.domain.parseAmount(draft.amountText, currency)
    val focus = remember { FocusRequester() }
    // the app's number pad is what a new entry opens with (the amount takes the focus at once, with no keyboard); the keyboard is one tap away, and a field that is tapped brings it
    var padOn by rememberSaveable { mutableStateOf(restored == null && start.id == null && start.amountText.isEmpty()) }
    var focusAmount by remember { mutableIntStateOf(0) }
    val reduceMotion = LocalReduceMotion.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val platform = LocalPlatform.current
    LaunchedEffect(padOn) { if (padOn) keyboard?.hide() }
    LaunchedEffect(focusAmount) { if (focusAmount > 0) focus.requestFocus() }
    val keyboardOpen = rememberKeyboardOpen()
    LaunchedEffect(keyboardOpen) { if (keyboardOpen) padOn = false }
    // the keyboard leaves WITH the form: it used to wait until the form had finished sliding away (about 0.6 s at the default speed) and hung on the screen over Home. A form that starts to leave (Save, Cancel, Back)
    // puts the keyboard away at once and gives up the focus, so both go down together. (The app's own number pad is part of the form and slides with it.)
    val leaving = LocalLeavingScreen.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(leaving) { if (leaving) { keyboard?.hide(); focusManager.clearFocus() } }
    LaunchedEffect(Unit) { if (start.amountText.isEmpty()) { if (padOn) platform.trace("add: amount focused") else focus.requestFocus() } }
    // the first frame holds what is needed at once (the header, the kind, the amount, the category tiles); the pad, the note, the accounts and "More" are composed a frame later, while the sheet is still arriving
    var rest by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos { }; rest = true }
    val forIncome = draft.kind == DraftKind.INCOME
    if (pickingCategory) CategoryPickerDialog(
        offeredCategories(categories, forIncome, draft.category), draft.category,
        onPick = { id -> draft = draft.copy(category = if (draft.kind == DraftKind.INCOME && id == draft.category) "" else id); pickingCategory = false },
        onClose = { pickingCategory = false },
        onNew = { pickingCategory = false; creatingCategory = true },
    )
    val zone = systemZone()
    val dateLabel = shortDateLabel(draft.occurredAt, zone)
    val split = EntryDrafts.isSplit(draft)
    val activeAccounts = accounts
    // what is asked for only now and then is behind one "More": open at once for an entry that already has any of it
    var moreOpen by rememberSaveable { mutableStateOf((restored ?: start).let { it.tags.isNotEmpty() || it.expectBack || it.foreignCode.isNotEmpty() || it.splitWith.isNotEmpty() || it.paidBy.isNotEmpty() || EntryDrafts.isSplit(it) }) }
    LaunchedEffect(attachments.isNotEmpty()) { if (attachments.isNotEmpty()) moreOpen = true }
    // "Usually Food for Chai": what earlier entries with this note were put under, asked a moment after the typing stops
    var usual by remember { mutableStateOf<UsualCategoryUi?>(null) }
    if (usualCategory != null) LaunchedEffect(draft.note, draft.kind) {
        usual = null
        if (draft.kind == DraftKind.EXPENSE && !EntryDrafts.isSplit(draft) && draft.note.trim().length >= 3) { kotlinx.coroutines.delay(350); usual = usualCategory(draft.note) }
    }

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
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = picker) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = Sheet.Shape,
        color = MaterialTheme.colorScheme.surface,
    ) {
    // a phone-width column in the middle of a wide window (the form was one very wide column with a keypad 1,000 dp wide on an unfolded foldable)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Scaffold(
        modifier = Modifier.widthIn(max = 560.dp).fillMaxHeight(),
        containerColor = Color.Transparent,
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                SheetHandle(Modifier.testTag("sheet_handle"))
                CappedFontScale(HEADER_TEXT_SCALE) { Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("cancel")) { Icon(Icons.Outlined.Close, contentDescription = "Cancel") }
                    // a new entry of any kind is "New entry" (the kind is the segmented choice under it); one kind only (a loan, a refund) says which
                    val heading = if (start.id == null && refundOf == null && title == "Add") (if (kinds.size > 1) "New entry" else "New " + draft.kind.label.lowercase()) else title
                    Text(heading, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).semantics { heading() })
                    // the app's pad or the phone's keyboard for the amount: one tap either way
                    IconButton(onClick = { if (padOn) { padOn = false; focusAmount++ } else padOn = true }, modifier = Modifier.testTag("amount_pad_toggle")) {
                        Icon(if (padOn) Icons.Outlined.Keyboard else Icons.Outlined.Dialpad, contentDescription = if (padOn) "Keyboard" else "Number pad")
                    }
                } }
            }
        },
        // Pinned above the keyboard so Save is always reachable, however small the screen: the pad and the button are one layer that rises together.
        bottomBar = {
            val feel = rememberFeel()
            Column {
                if (built is DraftResult.Problem && (draft.amountText.isNotEmpty() || draft.kind == DraftKind.TRANSFER || draft.kind == DraftKind.LOAN || split)) {
                    Text(built.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("form_problem"))
                }
                AnimatedVisibility(
                    visible = padOn && rest,
                    enter = if (reduceMotion) fadeIn(tween(Motion.REDUCED_MS)) else expandVertically(tween(Motion.TAB, easing = Motion.EaseOut)) + fadeIn(tween(Motion.TAB)),
                    exit = if (reduceMotion) fadeOut(tween(Motion.REDUCED_MS)) else shrinkVertically(tween(Motion.CHIP)) + fadeOut(tween(Motion.CHIP)),
                ) {
                    NumberPad { key -> commitAmount(AmountMath.press(expr, key, currency.minorUnitDigits)) }
                }
                val categoryName = categories.firstOrNull { it.id == draft.category }?.let { displayName(categories, it) }
                val echo = saveLabel(editing = start.id != null, kind = draft.kind, amount = amount?.takeIf { exprComplete }, split = split, categoryName = categoryName, currency = currency)
                Button(
                    onClick = { if (built is DraftResult.Ok && exprComplete) { feel(Feel.SAVE); onSave(draft, saveAsTemplate) } },
                    enabled = built is DraftResult.Ok && exprComplete,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 52.dp).testTag("save"),
                ) { Text(echo, maxLines = 2, textAlign = TextAlign.Center) }
            }
        },
    ) { padding ->
        // 0.16.1: the form scrolls under the pinned pad (or keyboard) and Save, so the top edge of whatever comes next could show as a sliver just above them; the last few dp of the
        // scrolling area fade out (the content's own pixels, whatever the colour behind), so a control is cut off softly or not at all, never as a thin strip
        Column(Modifier.fillMaxSize().padding(padding).fadeBottomEdge(FORM_EDGE_FADE_DP.dp).testTag("form_scroll").verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (kinds.size > 1) {
                SegmentedChoice(
                    kinds.map { Segment(it, it.label, "kind_${it.name}") }, draft.kind,
                    { k ->
                        if (k != draft.kind) cashPicked = start.id != null && k == DraftKind.TRANSFER && draft.kind == DraftKind.EXPENSE && cashWithdrawalAccount(activeAccounts, draft.accountId) != null
                        draft = switchedKind(draft, start, k, categories, activeAccounts, lastExpenseCategory, lastIncomeCategory)
                    },
                )
                if (start.id != null && draft.kind != start.kind) Text(
                    kindChangeNote(start.kind, draft.kind, start.splits.size >= 2, activeAccounts.firstOrNull { it.id == draft.toAccountId }?.name?.takeIf { cashPicked && draft.kind == DraftKind.TRANSFER }),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("kind_change_note"),
                )
                if (kindChangeProblem != null && start.id != null && draft.kind != start.kind) Text(kindChangeProblem, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("kind_change_problem"))
            }
            if (refundOf != null) Text("Refund of $refundOf", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("refund_of"))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val hide = LocalHideAmounts.current
                // the running sum: while the pad types a sum ("450+120"), the sum is the small line and what it comes to is the big figure (never while amounts are hidden: then the typed sum is all there is)
                val running = (maths as? AmountMath.Outcome.Value)?.takeIf { padOn && !hide && amount != null && AmountMath.hasOperator(expr) }
                AmountHero(
                    expr = expr, onExpr = { v -> if (v.length <= AmountMath.MAX_LENGTH && v.all(AmountMath::isAllowed)) commitAmount(AmountMath.normalise(v)) },
                    symbol = currency.symbolOrCode(), label = "Amount (${currency.symbolOrCode()})", error = maths == AmountMath.Outcome.Error || (draft.amountText.isNotEmpty() && amount == null),
                    onFocus = { padOn = false },
                    result = running?.let { amount?.formatted() }, resultComplete = running?.complete == true,
                    fieldModifier = Modifier.focusRequester(focus).onFocusChanged { if (it.isFocused) platform.trace("add: amount focused") }.tourSpot("amount").testTag("amount"),
                )
                if (maths == AmountMath.Outcome.Error) Text("This cannot be worked out", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.testTag("amount_problem"))
                else if (draft.amountText.isNotEmpty() && amount == null) Text("Enter an amount with at most ${currency.minorUnitDigits} decimals", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.testTag("amount_problem"))
                else if (draft.kind == DraftKind.REFUND) Text("Up to ${Money(draft.refundableMinor, currency).formatted()} can still be refunded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                // typed with the keyboard, the sum stays big as typed and what it comes to is said under it
                if (running == null && maths is AmountMath.Outcome.Value && maths.complete && amount != null && !hide) Text("= ${amount.formatted()}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("amount_result"))
                val words = amount?.takeIf { currency.minorUnitDigits == 2 && it.minorUnits >= 100_000L && !hide }?.let { AmountWords.of(it.minorUnits) }
                if (words != null) Text(words, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.testTag("amount_words"))
            }
            // N-01: one line typed as it is said ("chai 20", "auto ke 80 diye"); its preview fills this form when tapped and never saves. A new expense only. It is composed a frame after the form opens
            // (the first frame keeps only its room, so nothing under it moves), so the amount-first pad and the first frame are as they were for people who ignore it.
            val smart = start.id == null && refundOf == null && draft.kind == DraftKind.EXPENSE && !split
            if (smart && !rest) Spacer(Modifier.fillMaxWidth().height(SMART_LINE_ROOM_DP.dp))
            if (smart && rest) SmartEntryField(categories, activeAccounts, currency, draft.occurredAt, usualCategory) { f ->
                commitAmount(f.amountText)
                draft = draft.copy(
                    note = f.note.ifEmpty { draft.note }, category = f.categoryId ?: draft.category, accountId = f.accountId ?: draft.accountId,
                    occurredAt = f.daysAgo?.let { smartDateMillis(it, draft.occurredAt, zone) } ?: draft.occurredAt,
                )
            }

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
                // Hidden categories are not offered, except one already chosen (an entry being edited keeps its category).
                val offered = offeredCategories(categories, forIncome, draft.category)
                val usualHere = usual?.takeIf { u -> draft.kind == DraftKind.EXPENSE && u.categoryId != draft.category && categories.any { it.id == u.categoryId } }
                val suggestion = suggestedCategory?.takeIf { draft.kind == DraftKind.INCOME && draft.category.isEmpty() && categories.any { c -> c.id == it.categoryId } }
                CategoryTiles(
                    categories = categories, offered = offered, selected = draft.category, optional = draft.kind == DraftKind.INCOME,
                    onPick = { id -> draft = draft.copy(category = if (draft.kind == DraftKind.INCOME && id == draft.category) "" else id) },
                    onMore = { pickingCategory = true },
                    usualLabel = usualHere?.let { u -> "Usually ${categories.first { it.id == u.categoryId }.let { c -> displayName(categories, c) }} for ${draft.note.trim()}" },
                    onUsual = { usualHere?.let { u -> draft = draft.copy(category = u.categoryId) } },
                    suggestionLabel = suggestion?.let { "Suggested: ${it.name} (${it.note})" + if (it.sure) "" else ", a guess" },
                    onSuggestion = { suggestion?.let { draft = draft.copy(category = it.categoryId) } },
                )
            }

            if (split) SplitEditor(draft, categories, currency) { draft = it }

            if (rest) {
            // the account, the date and the trip: one row of chips under the categories, each opening its own choice (composed with the rest, a frame after the form opens)
            val fromLabel = when (draft.kind) { DraftKind.TRANSFER -> "From"; DraftKind.INCOME -> "Received in"; DraftKind.REFUND -> "Refunded to"; DraftKind.EXPENSE -> "Paid from"; DraftKind.LOAN -> if (draft.gave) "Paid from" else "Received in" }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountPickChip(fromLabel, activeAccounts, draft.accountId) { id -> draft = draft.copy(accountId = id) }
                AssistChip(
                    onClick = { pickingDate = true }, label = { Text(dateLabel, maxLines = 1) },
                    leadingIcon = { Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("date"),
                )
                if (start.id == null && draft.kind == DraftKind.EXPENSE) {
                    // the trip is found by the entry's own date, which the person may change: the chip follows it
                    val day = dateOfMillis(draft.occurredAt, zone).toEpochDays().toLong()
                    tripWindows.firstOrNull { day >= it.startDay && day <= it.endDay }?.let { t -> TripTagChip(t, on = !draft.noTripTag) { draft = draft.copy(noTripTag = !draft.noTripTag) } }
                }
            }
            OutlinedTextField(
                value = draft.note, onValueChange = { draft = draft.copy(note = it) },
                label = { Text(if (draft.kind == DraftKind.EXPENSE) "Note or merchant" else "Note") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("note"),
            )

            if (draft.kind == DraftKind.TRANSFER) {
                Text("To", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeAccounts.forEach { a -> FilterChip(selected = a.id == draft.toAccountId, onClick = { cashPicked = false; draft = draft.copy(toAccountId = a.id) }, label = { Text(a.name) }, modifier = Modifier.testTag("to_chip_${a.name}")) }
                }
                Text("Moving money between your own accounts is not spending or income: use it for an ATM withdrawal, paying a card bill, or topping up a wallet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            AssistChip(
                onClick = { moreOpen = !moreOpen },
                label = { Text(if (moreOpen) "Fewer options" else "More") },
                leadingIcon = { Icon(Icons.Outlined.MoreHoriz, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("form_more").semantics { stateDescription = if (moreOpen) "Expanded" else "Collapsed" },
            )
            if (moreOpen) Column(Modifier.fillMaxWidth().testTag("form_more_panel"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (draft.kind == DraftKind.EXPENSE && !split) AssistChip(onClick = { draft = draft.copy(splits = listOf(SplitDraft(draft.category, ""), SplitDraft("", ""))) }, label = { Text("Split across categories") }, modifier = Modifier.testTag("split_start"))
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
            if (showTemplateOption && draft.kind == DraftKind.EXPENSE && !split) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = saveAsTemplate, onCheckedChange = { saveAsTemplate = it }, modifier = Modifier.testTag("save_template"))
                    Text("Save as a quick entry")
                }
            }

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
}

/** The height of the soft edge above the pinned keypad and Save: fully opaque at the top of it, fully clear for the last [FORM_EDGE_CLEAR_DP], so a control's top edge that peeks out there is not seen. */
internal const val FORM_EDGE_FADE_DP = 28

/** The last part of that edge that is cleared completely: a control that only just comes into view there is not drawn. */
internal const val FORM_EDGE_CLEAR_DP = 16

/** Fades what is drawn in the last [height] of this node's bottom edge to nothing (the content is masked, so it works on any background, light, dark or black). */
internal fun Modifier.fadeBottomEdge(height: androidx.compose.ui.unit.Dp): Modifier = this
    .graphicsLayer(compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val h = height.toPx().coerceAtMost(size.height)
        val top = size.height - h
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = top, endY = size.height - FORM_EDGE_CLEAR_DP.dp.toPx()),
            topLeft = androidx.compose.ui.geometry.Offset(0f, top), size = androidx.compose.ui.geometry.Size(size.width, h), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
    }


/** What earlier entries with the note typed in were put under: the category and how many agreed ("Usually Food for Chai"). */
data class UsualCategoryUi(val categoryId: String, val times: Int)

/** "Today", "Yesterday" or "Sat, 15 Nov": the entry's date as the chip in the form's header says it. */
internal fun shortDateLabel(millis: Long, zone: kotlinx.datetime.TimeZone): String {
    val day = dateOfMillis(millis, zone).toEpochDays().toLong()
    val today = dateOfMillis(app.expenses.nowMillis(), zone).toEpochDays().toLong()
    return when (today - day) { 0L -> "Today"; 1L -> "Yesterday"; else -> formatMillis(millis, "EEE, d MMM", zone) }
}

/** What the big button says it will do: "Add ₹1,250 · Food", "Move ₹500", "Save" for an entry being edited. */
internal fun saveLabel(editing: Boolean, kind: DraftKind, amount: Money?, split: Boolean, categoryName: String?, currency: Currency): String {
    if (editing) return "Save"
    val a = amount?.takeIf { it.minorUnits > 0 }?.formatted() ?: return "Add"
    return when {
        kind == DraftKind.TRANSFER -> "Move $a"
        kind == DraftKind.REFUND -> "Refund $a"
        kind == DraftKind.LOAN -> "Add $a"
        split -> "Add $a · Split"
        categoryName != null -> "Add $a · $categoryName"
        else -> "Add $a"
    }
}

/**
 * The amount, big and in the middle: the currency sign small before it, the figure in the hero number style (tabular, tightening as a long figure grows), a cursor. It is a real text field (the tests, a hardware
 * keyboard and the system keyboard type into it) that the pad also types into. Above it, small, is its name ("Amount (₹)"), or while the pad types a sum, the sum itself ("450 + 120") with [result], what it
 * comes to, as the big figure (the typed sum stays in the field, drawn invisibly under it: a tap on it brings the keyboard and the sum back). A screen reader always hears the name and the typed text.
 */
@Composable
private fun AmountHero(
    expr: String, onExpr: (String) -> Unit, symbol: String, label: String, error: Boolean,
    onFocus: () -> Unit, result: String?, resultComplete: Boolean, fieldModifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val big = result ?: expr
    val size = when { big.length > 18 -> 26.sp; big.length > 11 -> 32.sp; else -> 40.sp }
    val style = NumberStyle.Hero.copy(fontSize = size, lineHeight = size * 1.15f, color = if (error) colors.error else colors.onSurface, textAlign = TextAlign.Center)
    CappedFontScale(LARGE_TEXT_SCALE) {
        BasicTextField(
            value = expr, onValueChange = onExpr, singleLine = true, textStyle = style, cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = fieldModifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocus() },
            decorationBox = { inner ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (result != null) Text(
                        spacedSum(expr), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("amount_sum").clearAndSetSemantics { text = androidx.compose.ui.text.AnnotatedString(label) },
                    ) else Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                        if (result == null) Text(symbol, style = style.copy(fontSize = size * 0.6f, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = colors.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp, bottom = 4.dp).clearAndSetSemantics { })
                        Box(contentAlignment = Alignment.Center) {
                            if (result != null) {
                                Text(result, style = style, maxLines = 1, modifier = Modifier.testTag(if (resultComplete) "amount_result" else "amount_running"))
                                Box(Modifier.graphicsLayer { alpha = 0f }) { inner() }
                            } else {
                                if (expr.isEmpty()) Text("0", style = style, color = colors.onSurface.copy(alpha = 0.3f), modifier = Modifier.clearAndSetSemantics { })
                                inner()
                            }
                        }
                    }
                }
            },
        )
    }
}

/** A sum as the line over the figure says it: "450 + 120", with a true minus sign. */
internal fun spacedSum(expr: String): String = buildString {
    AmountMath.normalise(expr).forEach { c -> if (c in AmountMath.OPS) append(' ').append(if (c == AmountMath.MINUS) '−' else c).append(' ') else append(c) }
}.trim()

/** The room the smart line takes, kept free on the form's first frame (the line itself comes a frame later) so the categories under it do not move. */
internal const val SMART_LINE_ROOM_DP = 56

/**
 * The pad's keys: the digits with "00" and the dot, and the column of operations on the right, backspace at its top ("<"). Divide has no key of its own (the design has four in the column):
 * it is a long press on ×, which shows a small ÷ in its corner and offers "Divide" to a screen reader.
 */
private val PadRows = listOf(listOf("7", "8", "9", "<"), listOf("4", "5", "6", "+"), listOf("1", "2", "3", "-"), listOf("00", "0", ".", "×"))

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun NumberPad(onKey: (String) -> Unit) {
    val feel = rememberFeel()
    val reduce = LocalReduceMotion.current
    CappedFontScale(HEADER_TEXT_SCALE) {
        // UI S9 (U-24): never wider than [WindowLayout.PAD_MAX_WIDTH_DP], centred (a 1,000 dp keypad on an unfolded foldable is a hard target); a phone's pad is narrower than that and unchanged
        Column(Modifier.fillMaxWidth().wrapContentWidth().widthIn(max = app.expenses.core.domain.WindowLayout.PAD_MAX_WIDTH_DP.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("number_pad"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PadRows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { key ->
                        val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        val pressed by source.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            if (pressed && !reduce) 0.92f else 1f,
                            if (reduce) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                            label = "padKey",
                        )
                        val op = key in listOf("<", "×", "-", "+")
                        val tag = when (key) { "." -> "pad_dot"; "<" -> "pad_back"; "×" -> "pad_times"; "-" -> "pad_minus"; "+" -> "pad_plus"; else -> "pad_$key" }
                        val words = when (key) { "." -> "Decimal point"; "<" -> "Backspace"; "×" -> "Times"; "-" -> "Minus"; "+" -> "Plus"; "00" -> "Double zero"; else -> key }
                        val colors = MaterialTheme.colorScheme
                        Box(
                            Modifier.weight(1f).height(48.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                                .clip(Radius.M).background(if (op) colors.secondaryContainer else colors.surfaceVariant)
                                .combinedClickable(
                                    interactionSource = source, indication = null,
                                    onLongClickLabel = if (key == "×") "Divide" else null,
                                    onLongClick = if (key == "×") ({ feel(Feel.KEY); onKey("÷") }) else null,
                                ) { feel(Feel.KEY); onKey(key) }
                                .semantics { contentDescription = words; role = Role.Button }
                                .testTag(tag),
                            contentAlignment = Alignment.Center,
                        ) {
                            val ink = if (op) colors.onSecondaryContainer else colors.onSurface
                            if (key == "<") Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = null, tint = ink)
                            else Text(if (key == "-") "−" else key, style = MaterialTheme.typography.titleLarge, color = ink)
                            if (key == "×") Text("÷", style = MaterialTheme.typography.labelSmall, color = ink, modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 6.dp).clearAndSetSemantics { })
                        }
                    }
                }
            }
        }
    }
}

/** How many category tiles fit side by side: four, three from 115% text and two from 160%, so a word of a name ("Groceries") is never cut. */
@Composable
private fun tileColumns(): Int = androidx.compose.ui.platform.LocalDensity.current.fontScale.let { s -> when { s >= 1.6f -> 2; s >= 1.15f -> 3; else -> 4 } }

/**
 * The categories as a grid of tiles: the seven most used (the chosen one always among them) and "All N" (every category this kind can take), which opens the full list with search, a dialog that
 * composes only when it is opened.
 */
@Composable
private fun CategoryTiles(
    categories: List<CategoryUi>, offered: List<CategoryUi>, selected: String, optional: Boolean,
    onPick: (String) -> Unit, onMore: () -> Unit,
    usualLabel: String?, onUsual: () -> Unit, suggestionLabel: String?, onSuggestion: () -> Unit,
) {
    val picks = remember(offered, selected) { CategoryChoices.tiles(offered.map { it.id to it.uses }, selected.ifEmpty { null }) }
    val byId = remember(categories) { categories.associateBy { it.id } }
    val columns = tileColumns()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (optional) Text("Category (optional)", style = MaterialTheme.typography.labelMedium)
        if (suggestionLabel != null) AssistChip(onClick = onSuggestion, label = { Text(suggestionLabel, maxLines = 2) }, modifier = Modifier.testTag("income_suggestion"))
        if (usualLabel != null) AssistChip(onClick = onUsual, label = { Text(usualLabel, maxLines = 2) }, modifier = Modifier.testTag("usual_category"))
        val cells: List<CategoryUi?> = picks.shown.mapNotNull { byId[it] } + null // null: the "All N" tile
        cells.chunked(columns).forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                line.forEach { c ->
                    if (c != null) CategoryTile(c, displayName(categories, c), c.id == selected, Modifier.weight(1f)) { onPick(c.id) }
                    else AllCategoriesTile(offered.size, Modifier.weight(1f), onMore)
                }
                repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The last tile: "All 23", with a dashed outline (it is not a category), opening every category in a list with search. */
@Composable
private fun AllCategoriesTile(count: Int, modifier: Modifier, onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    Column(
        modifier.heightIn(min = 76.dp)
            .drawWithContent {
                drawContent()
                val r = DesignTokens.RADIUS_M.dp.toPx()
                drawRoundRect(
                    outline, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .clip(Radius.M).clickable(onClickLabel = "Show all categories", onClick = onClick).padding(horizontal = 4.dp, vertical = 8.dp).testTag("more_categories"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.MoreHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text("All $count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun CategoryTile(c: CategoryUi, name: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.heightIn(min = 76.dp).clip(Radius.M)
            .background(if (selected) colors.primaryContainer else colors.surfaceVariant.copy(alpha = 0.5f))
            .then(if (selected) Modifier.border(1.dp, colors.primary, Radius.M) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp).testTag("category_chip_${c.id}"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            CategoryAvatar(c.name, c.name, size = 32.dp)
            // not colour alone: the chosen tile has a check as well as its outline
            if (selected) Box(Modifier.align(Alignment.BottomEnd).size(16.dp).clip(Radius.Full).background(colors.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(12.dp))
            }
        }
        Text(name, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else null)
    }
}

/**
 * "Bank ▾": the account the entry is paid from (or received in), a chip that opens the list of accounts. The chip says the account's name; a screen reader hears what it is for as well ("Paid from: Bank").
 * Each account in the list is `account_chip_<name>` and is marked when it is the chosen one.
 */
@Composable
private fun AccountPickChip(label: String, accounts: List<AccountUi>, selected: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val name = accounts.firstOrNull { it.id == selected }?.name
    Box {
        AssistChip(
            onClick = { open = true }, label = { Text(name ?: "Choose an account", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.semantics { stateDescription = "$label: ${name ?: "none chosen"}" }.testTag("account_pick"),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            accounts.forEach { a ->
                DropdownMenuItem(
                    text = { Text(a.name) }, onClick = { open = false; onPick(a.id) },
                    leadingIcon = if (a.id == selected) ({ Icon(Icons.Outlined.Check, contentDescription = null) }) else null,
                    modifier = Modifier.semantics { this.selected = a.id == selected }.testTag("account_chip_${a.name}"),
                )
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
                when { l == 0L -> "All assigned"; l > 0 -> "${Money(l, currency).formatted()} left to assign"; else -> "${Money(-l, currency).formatted()} over" },
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
        // side by side while there is room; at large text each is a full-width button on its own line (the second used to collapse into a tall oval)
        val big = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
        val wide = if (big) Modifier.fillMaxWidth() else Modifier
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (picker.canTakePhoto) OutlinedButton(onClick = { picker.takePhoto { it?.let(onAttach) } }, modifier = wide.testTag("attachment_camera")) { Text("Take a photo") }
            OutlinedButton(onClick = { picker.openDocument(listOf("image/*", "application/pdf", "*/*")) { it?.let(onAttach) } }, modifier = wide.testTag("attachment_add")) { Text("Attach a photo or file") }
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
internal fun CategoryPickerDialog(categories: List<CategoryUi>, selected: String, onPick: (String) -> Unit, onClose: () -> Unit, onNew: (() -> Unit)? = null) {
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
        dismissButton = onNew?.let { n -> { TextButton(onClick = n, modifier = Modifier.testTag("new_category_chip")) { Text("+ New category") } } },
    )
}

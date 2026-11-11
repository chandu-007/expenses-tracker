package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import app.expenses.core.domain.formatted
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.expenses.AccountUi
import app.expenses.CaptureSettingsUi
import app.expenses.CategoryUi
import app.expenses.InboxRowUi
import kotlinx.coroutines.launch

/**
 * Transactions found in texts, waiting to be added to the ledger or skipped. Nothing here counts anywhere until it is added.
 * The top says how much is waiting and offers "Add all" for the items that need no decision; each card leads with who, how much and where it will go,
 * so the usual answer is one tap, and everything else (the original message, flipping the direction, naming the payee) is one step further in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    rows: List<InboxRowUi>, accounts: List<AccountUi>, categories: List<CategoryUi>,
    onConfirm: (id: String, accountId: String, categoryId: String, treatment: String, fromId: String, toId: String, refundOf: String, done: (String?) -> Unit) -> Unit, onDismiss: (String) -> Unit, onFlip: (String) -> Unit, onName: (String, String) -> Unit, onBack: () -> Unit,
    /** Where bank messages can be pasted (iPhone): shows the paste button and box. Null on Android, so that screen is as it was. */
    onPaste: ((String?, (app.expenses.PasteOutcome) -> Unit) -> Unit)? = null,
    /** "Skipped X · Undo" after a skip: what to offer, taking it back, and the bar going away. */
    undo: app.expenses.ScreenUndo? = null, onUndo: (String) -> Unit = {}, onUndoGone: (String) -> Unit = {},
    /** Two waiting texts of one transfer added as ONE transfer: the money-out item, the money-in item, the account it left and the account it reached. */
    onConfirmPair: (outId: String, intoId: String, fromId: String, toId: String, done: (String?) -> Unit) -> Unit,
    /** "These are separate": the two texts of a combined card stay two items. */
    onApart: (outId: String, intoId: String) -> Unit,
    /** "Make this a transfer": the waiting item replaces the earlier expense or income it is the other half of (item, that entry, from account, to account). */
    onConvert: (itemId: String, earlierId: String, fromId: String, toId: String, done: (String?) -> Unit) -> Unit,
    /** "Which account is this?": the account the person picked for a waiting text (item, account), remembered for its bank and digits. */
    onChooseAccount: (itemId: String, accountId: String) -> Unit = { _, _ -> },
    /** "New account ending 123": makes an account (name, type) with the digits the text prints and uses it (item, name, type, answer: null or why not). */
    onCreateAccount: (itemId: String, name: String, type: String, done: (String?) -> Unit) -> Unit = { _, _, _, done -> done("Not available here.") },
    /** "Mark paid" on a bill payment card: the waiting text, the bill it pays ("" none; "card:<account>" a statement worked out from billing dates), the account it was paid from, the card account ("" when the app tracks none) and the entry that already records the same payment ("" none); answer: null or why not. */
    onBillPaid: (itemId: String, billId: String, payFromId: String, cardId: String, useEntryId: String, done: (String?) -> Unit) -> Unit = { _, _, _, _, _, done -> done("Not available here.") },
    /** "Add to that transfer": the waiting text is the other leg of an entry that is already in the ledger, so it joins that entry instead of becoming a second one (item, entry; answer: null or why not). */
    onJoin: (itemId: String, entryId: String, done: (String?) -> Unit) -> Unit = { _, _, done -> done("Not available here.") },
    /** The empty list's one first step: the Capture screen, where reading texts is turned on. Null where there is none. */
    onSetUpCapture: (() -> Unit)? = null,
    /** The empty list's other first step: "Why isn't capture working?", the checkup. Null where there is none. */
    onOpenCheckup: (() -> Unit)? = null,
    /** "Same shop as..." on a card (under Change): makes the payee's name count as another shop's (from, to; answer: null or why not). Null where there is none. */
    onSameShop: ((from: String, to: String, done: (String?) -> Unit) -> Unit)? = null,
) {
    var confirmingAll by rememberSaveable { mutableStateOf(false) }
    val undoHost = rememberUndoSnackbar(undo, onUndo, onUndoGone)
    val paste = if (onPaste != null) rememberPasteState() else null
    // those that need no decision: not a repeat, not a related entry, an account the person has used for this bank card before (or the only account there is),
    // and an ordinary payment (a transfer or refund asks to be looked at)
    // (and not one that might be half of a transfer: that is looked at, not added in bulk)
    // (and a category that is only a guess from a name, a payment to a person, or a marketplace is looked at: it is never added in bulk or by a swipe; no category at all is fine, it stays Uncategorised)
    val easy = rows.filter { it.duplicateNote == null && it.relatedNote == null && it.otherLegEntryId.isEmpty() && (it.rememberedAccount || accounts.size == 1) && it.accountId.isNotEmpty() && it.convertEntryId.isEmpty() && it.transferHint.isEmpty() && (it.treatment == "PAYMENT" && it.certainty != "check" || it.treatment == "TRANSFER" && it.transferSure && it.fromAccountId.isNotEmpty() && it.toAccountId.isNotEmpty()) }
    val refused = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    // the other waiting payees, offered by "Same shop as..." on a card
    val waitingNames = remember(rows) { rows.mapNotNull { it.payeeName.takeIf { n -> n.isNotEmpty() } }.distinct() }
    // the waiting texts whose account is not known (they are never "clear"): how many, on how many bank cards, and where the first one is, for the one-tap way to answer
    val needAccount = rows.count { it.accountAsk.isNotEmpty() && it.pairedItemId.isEmpty() && it.treatment == "PAYMENT" }
    val needAccountCards = rows.filter { it.accountAsk.isNotEmpty() && it.pairedItemId.isEmpty() && it.treatment == "PAYMENT" }.map { it.issuer + "|" + it.last4 }.toSet().size
    val firstAsking = rows.indexOfFirst { it.accountAsk.isNotEmpty() && it.pairedItemId.isEmpty() && it.treatment == "PAYMENT" }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val easyIds = easy.map { it.id }.toSet()
    // the first card of each bank card (issuer and digits) that asks "Which account is this?" carries the question; a bill payment card asks it too (the paying account), the others follow it
    val askLeaders = rows.filter { it.accountAsk.isNotEmpty() && it.pairedItemId.isEmpty() && (it.treatment == "PAYMENT" || it.treatment == "BILL_PAYMENT") }.distinctBy { it.issuer + "|" + it.last4 }.mapTo(HashSet()) { it.id }
    // items from the same payee (or, with no payee, the same bank): "Add all from <payee>" on their cards adds the ones among them that need no decision
    val byPayee = rows.groupBy { inboxGroupKey(it) }
    // a clear card is added as it stands: an expense or income, or the transfer it starts as (only a rule the person made three times with no exception starts one that may be added in bulk)
    fun addTogether(list: List<InboxRowUi>) { refused.clear(); list.forEach { r -> onConfirm(r.id, r.accountId, r.categoryId, r.treatment, r.fromAccountId, r.toAccountId, "") { p -> if (p != null) refused += p } } }
    val cur = app.expenses.core.domain.BaseCurrency.current
    val transfers = easy.filter { it.treatment == "TRANSFER" }
    val plain = easy.filter { it.treatment != "TRANSFER" }
    // a combined transfer card is money out of one account and money in to another: it counts on both sides
    fun total(list: List<InboxRowUi>, credit: Boolean) = app.expenses.core.domain.Money(list.filter { (it.direction == "CREDIT") == credit || (credit && it.pairedItemId.isNotEmpty()) }.sumOf { it.amountMinor }, cur).formatted()
    if (confirmingAll) AlertDialog(
        onDismissRequest = { confirmingAll = false }, title = { Text("Add ${easy.size} items?") },
        text = {
            val bare = plain.count { it.direction != "CREDIT" && it.categoryId.isEmpty() }
            Text(
                "Each one goes where its card says: ${total(plain, false)} out and ${total(plain, true)} in." +
                    (if (transfers.isNotEmpty()) " ${transfers.size} ${if (transfers.size == 1) "is a transfer" else "are transfers"} between your own accounts (${app.expenses.core.domain.Money(transfers.sumOf { it.amountMinor }, cur).formatted()}): not spending or income." else "") +
                    " Items that look like repeats, transfers or refunds, whose account is not yet known, or whose category is only a guess, are left for you to check." +
                    (if (needAccount > 0) " $needAccount ${if (needAccount == 1) "has" else "have"} no account yet: answer \"Which account is this?\" on its card." else "") +
                    if (bare > 0) " $bare of these have no category yet: they are added as Uncategorised, and you can file them from Reports later." else "",
                modifier = Modifier.testTag("inbox_add_all_text"),
            )
        },
        confirmButton = { TextButton(onClick = { confirmingAll = false; refused.clear(); easy.forEach { r -> onConfirm(r.id, r.accountId, r.categoryId, r.treatment, r.fromAccountId, r.toAccountId, "") { p -> if (p != null) refused += p } } }, modifier = Modifier.testTag("inbox_add_all_ok")) { Text("Add ${easy.size}") } },
        dismissButton = { TextButton(onClick = { confirmingAll = false }) { Text("Cancel") } },
    )
    Scaffold(topBar = { AreaTopBar("To review", Area.CAPTURE, Icons.Outlined.Inbox, onBack, "inbox_back") }, snackbarHost = { androidx.compose.material3.SnackbarHost(undoHost) }) { pad ->
        if (rows.isEmpty() && onPaste != null) {
            LazyColumn(Modifier.padding(pad).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp)) {
                item(key = "empty") {
                    Column {
                        EmptyArt(EmptyKind.INBOX, Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally).padding(bottom = 8.dp))
                        Text("Nothing to review.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("inbox_empty"))
                        Text("Payments found in your bank texts wait here for you to add or skip. Nothing is added until you say so. You can also paste a message below.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (onSetUpCapture != null) OutlinedButton(onClick = onSetUpCapture, modifier = Modifier.padding(top = 8.dp).testTag("inbox_empty_setup")) { Text("Set up reading texts") }
                    }
                }
                item(key = "paste") { PasteBox(onPaste, paste!!) }
            }
        } else if (rows.isEmpty()) {
            Column(Modifier.padding(pad).padding(24.dp).fillMaxSize()) {
                EmptyArt(EmptyKind.INBOX, Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally).padding(bottom = 8.dp))
                Text("Nothing to review.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("inbox_empty"))
                Text("Payments found in your bank texts wait here for you to add or skip. Nothing is added until you say so.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (onSetUpCapture != null) OutlinedButton(onClick = onSetUpCapture, modifier = Modifier.padding(top = 8.dp).testTag("inbox_empty_setup")) { Text("Set up reading texts") }
            }
        } else LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("inbox_list"), state = listState, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            if (onPaste != null) item(key = "paste") { PasteBox(onPaste, paste!!, Modifier.animateItem()) }
            item(key = "summary") {
                Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${rows.sumOf { if (it.pairedItemId.isNotEmpty()) 2 else 1 }} to review", style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.testTag("inbox_count"))
                    Text("${total(rows, false)} out · ${total(rows, true)} in. Check each one, or add the clear ones together.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (refused.isNotEmpty()) Text("${refused.size} could not be added: ${refused.first()}", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("inbox_add_all_problem"))
                    if (easy.size >= 2) Button(onClick = { confirmingAll = true }, modifier = Modifier.testTag("inbox_add_all")) { Text("Add all ${easy.size} clear ones") }
                    if (easy.isNotEmpty()) Text("Clear ones can also be swiped: right to add, left to skip.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("inbox_swipe_hint"))
                    if (needAccount > 0) {
                        Text(
                            "$needAccount waiting ${if (needAccount == 1) "text needs" else "texts need"} an account first, so ${if (needAccount == 1) "it is" else "they are"} left out of Add all and swipe. Answer \"Which account is this?\" once for each bank card; the others of the same card follow.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.testTag("inbox_needs_account"),
                        )
                        OutlinedButton(onClick = { scope.launch { listState.animateScrollToItem(firstAsking + (if (onPaste != null) 1 else 0) + 1) } }, modifier = Modifier.testTag("inbox_fix_accounts")) { Text("Choose the account (${needAccountCards})") }
                    }
                }
            }
            itemsIndexed(rows, key = { _, it -> it.id }) { index, r ->
                val same = byPayee[inboxGroupKey(r)].orEmpty()
                val progress = inboxProgress(rows, index)
                if (r.pairedItemId.isNotEmpty()) TransferPairCard(r, accounts, onConfirmPair, onApart, Modifier.animateItem(), progress)
                else InboxCard(
                    r, accounts, categories, onConfirm, onDismiss, onFlip, onName, Modifier.animateItem(),
                    swipeable = r.id in easyIds,
                    payeeGroup = if (same.size >= 2) same.filter { it.id in easyIds } else emptyList(), onAddPayee = ::addTogether,
                    onConvert = onConvert, onChooseAccount = onChooseAccount, onCreateAccount = onCreateAccount, onBillPaid = onBillPaid, onJoin = onJoin, askHere = r.id in askLeaders, progress = progress,
                )
            }
                    onSameShop = onSameShop, sameShopNames = waitingNames,
        }
    }
}

/** What items are grouped by for "Add all from <payee>": the payee, or the bank when the text named none. */
private fun inboxGroupKey(r: InboxRowUi): String = (r.payee?.takeIf { it.isNotBlank() } ?: r.issuer).trim().lowercase()
/**
 * "3 of 12": where the card at [index] is among the waiting items (the count at the top of the screen counts the two texts of a transfer card as two, so a transfer card says "5 and 6 of 14").
 */
internal fun inboxProgress(rows: List<InboxRowUi>, index: Int): String {
    val before = rows.take(index).sumOf { if (it.pairedItemId.isNotEmpty()) 2 else 1 }
    val total = rows.sumOf { if (it.pairedItemId.isNotEmpty()) 2 else 1 }
    return if (rows[index].pairedItemId.isNotEmpty()) "${before + 1} and ${before + 2} of $total" else "${before + 1} of $total"
}


@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun InboxCard(
    r: InboxRowUi, accounts: List<AccountUi>, categories: List<CategoryUi>,
    onConfirm: (String, String, String, String, String, String, String, (String?) -> Unit) -> Unit, onDismiss: (String) -> Unit, onFlip: (String) -> Unit, onName: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    /** Needs no decision (see [InboxScreen]): can be swiped, right to add as the Add button would, left to skip. Other cards do not move; neither does one whose type the person changed from the one it started as. */
    swipeable: Boolean = false,
    /** The items from this card's payee that need no decision, when two or more pending items share the payee; empty otherwise (no button). */
    payeeGroup: List<InboxRowUi> = emptyList(), onAddPayee: (List<InboxRowUi>) -> Unit = {},
    onConvert: (String, String, String, String, (String?) -> Unit) -> Unit,
    onChooseAccount: (String, String) -> Unit = { _, _ -> },
    onCreateAccount: (String, String, String, (String?) -> Unit) -> Unit = { _, _, _, done -> done("Not available here.") },
    onBillPaid: (String, String, String, String, String, (String?) -> Unit) -> Unit = { _, _, _, _, _, done -> done("Not available here.") },
    onJoin: (String, String, (String?) -> Unit) -> Unit = { _, _, done -> done("Not available here.") },
    /** Whether this card carries the "Which account is this?" question: only the first waiting card of a bank card (issuer and digits) does; the others say they follow it. */
    askHere: Boolean = true,
    /** Where this card is in the list, "3 of 12". */
    progress: String = "",
) {
    var moreOptions by rememberSaveable(r.id) { mutableStateOf(false) }
    /** "Same shop as..." under Change (see [InboxScreen.onSameShop]) and the other waiting payees it offers as chips. */
    onSameShop: ((from: String, to: String, done: (String?) -> Unit) -> Unit)? = null, sameShopNames: List<String> = emptyList(),
    // null: the card is as the app proposed it; "TRANSFER" or "PLAIN": what the person chose with the type chips (or "Add as expense instead")
    var mode by rememberSaveable(r.id) { mutableStateOf<String?>(null) }
    // "Not this" on a bill payment card, and "It is a different payment" on a card that is the other leg of a transfer: the card is an ordinary one
    var notBill by rememberSaveable(r.id) { mutableStateOf(false) }
    var asNew by rememberSaveable(r.id) { mutableStateOf(false) }
    var reporting by remember(r.id) { mutableStateOf(false) }
    if (reporting) ReportMessageDialog(
        "", r.body, emptySet(),
        ReadAs(r.issuer.ifBlank { "unknown" } + " (an item in To review)", "added to To review as money ${if (r.direction == "CREDIT") "in" else "out"}, read by " + (if (r.confidence == "BANK_RULE") "the bank's own rule" else "the generic rules (low confidence)")),
    ) { reporting = false }
    var fromAcct by rememberSaveable(r.id) { mutableStateOf(r.fromAccountId) }
    var toAcct by rememberSaveable(r.id) { mutableStateOf(r.toAccountId) }
    // the other end of a transfer the person makes of a card that started as spending or income, and the choices on a bill payment card
    var otherPick by rememberSaveable(r.id) { mutableStateOf<String?>(null) }
    var billPick by rememberSaveable(r.id) { mutableStateOf<String?>(null) }
    var fromPick by rememberSaveable(r.id) { mutableStateOf<String?>(null) }
    var cardPick by rememberSaveable(r.id) { mutableStateOf<String?>(null) }
    val billPay = r.billPay
    val billMode = r.treatment == "BILL_PAYMENT" && billPay != null && !notBill
    val legMode = r.otherLegEntryId.isNotEmpty() && !asNew && !billMode
    val proposed = r.treatment == "TRANSFER" || r.treatment == "REFUND"
    val eff = mode ?: when (r.treatment) { "TRANSFER" -> "TRANSFER"; "REFUND" -> "REFUND"; else -> "PLAIN" }
    // what the app proposed besides spending or income (a cash withdrawal, a card payment, a refund, a transfer from what the person did before), still chosen
    val paired = proposed && eff == r.treatment
    // a transfer the person chose on a card that started as something else
    val pickedTransfer = eff == "TRANSFER" && r.treatment != "TRANSFER"
    // what the person picked on this card; until they do, the card follows the account the app has (so one chosen for this bank on another card shows here at once)
    var pickedAccount by rememberSaveable(r.id) { mutableStateOf<String?>(null) }
    val account by rememberUpdatedState(pickedAccount ?: r.accountId)
    // what the person picked on this card, if anything; until they do, the card follows the app's suggestion, so a rule added or a choice made on another card shows here at once
    var picked by rememberSaveable(r.id) { mutableStateOf<String?>(null) }
    val category by rememberUpdatedState(picked ?: r.categoryId)
    var pickingCategory by rememberSaveable(r.id) { mutableStateOf(false) }
    var changing by rememberSaveable(r.id) { mutableStateOf(false) }
    var naming by rememberSaveable(r.id) { mutableStateOf(false) }
    var showText by rememberSaveable(r.id) { mutableStateOf(false) }
    var problem by remember(r.id) { mutableStateOf<String?>(null) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val credit = r.direction == "CREDIT"
    val flow = if (credit) Flow3.IN else Flow3.OUT
    val other = (otherPick ?: r.otherAccountId).takeIf { it != account }.orEmpty()
    val nameOf = { id: String -> accounts.firstOrNull { it.id == id }?.name ?: "choose an account" }
    // the swipe's lambda is made once: it reads the row and the account through State, so it always sees the card as it is now
    val rNow by rememberUpdatedState(r)
    var snapBack by remember { mutableStateOf(0) }
    val swipe = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                // the same as the Add button on this card (a card left as it started: an expense, income or the strong transfer it starts as); when it is refused the card slides back and says why
                SwipeToDismissBoxValue.StartToEnd -> {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                    onConfirm(rNow.id, account, category, if (rNow.treatment == "TRANSFER") "TRANSFER" else "PAYMENT", rNow.fromAccountId, rNow.toAccountId, rNow.refundOfEntry) { p -> if (p != null) { problem = p; snapBack++ } }
                    true
                }
                SwipeToDismissBoxValue.EndToStart -> { onDismiss(rNow.id); true }
                else -> false
            }
        },
    )
    LaunchedEffect(snapBack) { if (snapBack > 0) swipe.snapTo(SwipeToDismissBoxValue.Settled) }
    // approving by a swipe is for a card left in the type it started as; a card whose type the person changed, a bill payment and the other leg of a transfer are answered with their buttons
    val canSwipe = swipeable && mode == null && !billMode && !legMode
    SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = canSwipe,
        enableDismissFromEndToStart = canSwipe,
        modifier = modifier,
        backgroundContent = {
            val adding = swipe.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val skipping = swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart
            if (canSwipe && (adding || skipping)) Box(
                Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                    .background(if (adding) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 24.dp),
                contentAlignment = if (adding) androidx.compose.ui.Alignment.CenterStart else androidx.compose.ui.Alignment.CenterEnd,
            ) { Text(if (adding) "Add" else "Skip", style = MaterialTheme.typography.titleMedium, color = if (adding) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) }
        },
    ) {
    androidx.compose.material3.Card(
        Modifier.fillMaxWidth().testTag("inbox_${r.id}"), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // where this card is in the list: "3 of 12"
            Text(progress, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("inbox_progress_${r.id}"))
            // who, how much
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                CategoryAvatar(r.payee ?: r.issuer, r.payee ?: r.issuer, Modifier.padding(end = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.payee ?: r.issuer, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(
                        "${if (credit) "Money in" else "Money out"} · ${r.dateText} · ${r.issuer}" + (r.last4?.let { " · ending $it" } ?: "") + when (r.payeeKind) { "person" -> " · ${if (credit) "from" else "to"} a person"; "business" -> " · ${if (credit) "from" else "to"} a business"; else -> "" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("inbox_meta_${r.id}"),
                    )
                }
                Text(signedAmount(r.amountText, flow), style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = amountColor(flow), modifier = Modifier.padding(start = 8.dp).testTag("inbox_amount_${r.id}"))
            }
            // warnings first: a repeat or a related entry changes what the right action is
            r.relatedNote?.let { Callout(it, MaterialTheme.colorScheme.secondary, Modifier.testTag("inbox_related_${r.id}")) }
            r.duplicateNote?.let { Callout(it, MaterialTheme.colorScheme.tertiary, Modifier.testTag("inbox_duplicate_${r.id}")) }
            // the other half of this is already in the ledger as a transfer: this text joins it, so the move is recorded once (and shows both texts as where it came from)
            if (legMode) Callout(r.otherLegNote, MaterialTheme.colorScheme.secondary, Modifier.testTag("inbox_otherleg_${r.id}"))
            // the other half of this is already in the ledger as an expense or income: offer to make the two one transfer (the earlier entry goes, and Undo brings it back)
            if (r.convertEntryId.isNotEmpty()) {
                Callout(r.convertNote, MaterialTheme.colorScheme.secondary, Modifier.testTag("inbox_convert_note_${r.id}"))
                TextButton(onClick = { onConvert(r.id, r.convertEntryId, r.fromAccountId, r.toAccountId) { p -> problem = p } }, modifier = Modifier.testTag("inbox_convert_${r.id}")) { Text("Make it a transfer") }
                // the Undo lives in memory (it needs the text, which adding an item forgets), so it is offered only while this screen is open: say so
                Text("You can undo this until you leave this screen.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("inbox_convert_undo_note_${r.id}"))
            }
            if (r.transferHint.isNotEmpty()) Callout(r.transferHint, MaterialTheme.colorScheme.secondary, Modifier.testTag("inbox_transfer_hint_${r.id}"))
            if (r.setupHint.isNotEmpty()) Callout(r.setupHint, MaterialTheme.colorScheme.secondary, Modifier.testTag("inbox_setup_digits_${r.id}"))
            // the account is not known (the digits fit none or several, or none are printed): ask once, remember the answer for this bank and these digits (the other waiting cards of it follow)
            // The one question that matters most comes first: which account (when it is not known, Add waits for it), else the category (when it is only a guess). What explains or varies
            // them is under "More options" below Add, so most cards are a line of facts, one answer and Add.
            val asksAccount = r.accountAsk.isNotEmpty() && pickedAccount == null && !paired && !legMode
            if (asksAccount) {
                if (askHere) AccountQuestion(r, accounts, onChoose = { id -> onChooseAccount(r.id, id) }, onCreate = { name, type, done -> onCreateAccount(r.id, name, type, done) })
                else Text("Which account is this? Answer on the first ${r.issuer}${r.last4?.let { " ending $it" } ?: ""} card above: this one follows.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.testTag("inbox_account_follow_${r.id}"))
            }
            // the other categories this payee has been in: shown at once only while the category is the open question (a guess to check, no account question first)
            val plainCard = !billMode && !legMode && !paired && !pickedTransfer
            val alternativesNow = picked == null && r.alternatives.isNotEmpty() && !changing && plainCard
            val alternativesInline = alternativesNow && r.certainty == "check" && !asksAccount
            val accountDetail = asksAccount && askHere
            val hasMore = (alternativesNow && !alternativesInline) || accountDetail
            // the type: Expense, Income or Transfer. Starts as the text says (a cash withdrawal or a card payment: a transfer); Expense and Income turn a text read the wrong way round
            if (!billMode && !legMode) {
                val pick: (String) -> Unit = { k: String ->
                    when {
                        k == "TRANSFER" -> mode = "TRANSFER"
                        // the card stays a refund when Income is chosen on it: a refund is money in
                        k == "INCOME" && eff == "REFUND" -> mode = null
                        else -> { if ((k == "INCOME") != credit) onFlip(r.id); mode = "PLAIN" }
                    }
                }
                KindChips(r.id, eff == "TRANSFER", credit || eff == "REFUND", pick)
            }
            if (billMode && billPay != null) BillPaymentBox(
                r, billPay, accounts, billPick ?: billPay.billId, { billPick = it }, fromPick ?: billPay.payFromId, { fromPick = it },
                cardPick ?: billPay.cardAccountId, { cardPick = it },
            ) else if (!legMode) {
            // where it will go: the decision, with its own Change next to it
            Column(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (paired && r.treatment == "TRANSFER") {
                    Text("Recorded as a ${r.treatmentLabel}: from ${nameOf(fromAcct)} to ${nameOf(toAcct)}. It moves money between your own accounts, so it is not spending.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("inbox_pair_${r.id}"))
                    if (r.transferNote.isNotEmpty()) Text(r.transferNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.testTag("inbox_transfer_learned_${r.id}"))
                    if (changing) {
                        Text("From", style = MaterialTheme.typography.labelSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { accounts.forEach { a -> FilterChip(selected = a.id == fromAcct, onClick = { fromAcct = a.id }, label = { Text(a.name) }, modifier = Modifier.testTag("inbox_from_${r.id}_${a.id}")) } }
                        Text("To", style = MaterialTheme.typography.labelSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { accounts.forEach { a -> FilterChip(selected = a.id == toAcct, onClick = { toAcct = a.id }, label = { Text(a.name) }, modifier = Modifier.testTag("inbox_to_${r.id}_${a.id}")) } }
                    }
                } else if (paired && r.treatment == "REFUND") {
                    Text("Recorded as a ${r.treatmentLabel}, so the original expense is reduced instead of this counting as income.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("inbox_pair_${r.id}"))
                } else if (pickedTransfer) {
                    // a transfer the person chose: the text's own account is known (or asked above); the only other question is the account at the other end
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text((if (credit) "Transfer into ${nameOf(account)}" else "Transfer out of ${nameOf(account)}"), style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, modifier = Modifier.weight(1f).testTag("inbox_goes_${r.id}"))
                        TextButton(onClick = { changing = !changing }, modifier = Modifier.testTag("inbox_change_${r.id}")) { Text(if (changing) "Done" else "Change") }
                    }
                    if (changing) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { accounts.forEach { a -> FilterChip(selected = a.id == account, onClick = { pickedAccount = a.id }, label = { Text(a.name) }, modifier = Modifier.testTag("inbox_account_${r.id}_${a.id}")) } }
                    val choices = accounts.filter { it.id in r.otherChoices && it.id != account }
                    if (choices.isEmpty()) Text("A transfer needs two accounts. Add the other one in More, Accounts first.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("inbox_other_none_${r.id}"))
                    else {
                        Text(if (credit) "From account" else "To account", style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("inbox_other_label_${r.id}"))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { choices.forEach { a -> FilterChip(selected = a.id == other, onClick = { otherPick = a.id }, label = { Text(a.name) }, modifier = Modifier.testTag("inbox_other_${r.id}_${a.id}")) } }
                        if (other.isNotEmpty() && otherPick == null && r.otherWhy.isNotEmpty()) Text("Suggested: ${r.otherWhy}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("inbox_other_why_${r.id}"))
                    }
                    Text("It moves money between your own accounts, so it is not spending or income.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("inbox_transfer_note_${r.id}"))
                } else {
                    val accountName = accounts.firstOrNull { it.id == account }?.name ?: "choose an account"
                    val categoryName = categories.firstOrNull { it.id == category }?.name
                    val why = if (picked == null && category == r.categoryId && r.categoryNote.isNotEmpty() && r.categoryReason.isEmpty()) " (${r.categoryNote})" else ""
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Goes to $accountName · " + (categoryName ?: "no category") + why, style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, modifier = Modifier.weight(1f).testTag("inbox_goes_${r.id}"))
                        TextButton(onClick = { changing = !changing }, modifier = Modifier.testTag("inbox_change_${r.id}")) { Text(if (changing) "Done" else "Change") }
                    }
                    if (picked == null && r.certainty == "check" && category.isNotEmpty()) Text("This is a guess: please check it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.testTag("inbox_check_${r.id}"))
                    if (alternativesInline) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        r.alternatives.forEach { id -> categories.firstOrNull { it.id == id }?.let { c -> AssistChip(onClick = { picked = c.id }, label = { Text("Or ${c.name}") }, modifier = Modifier.testTag("inbox_alt_${r.id}_${c.id}")) } }
                    // a suggestion that rests on the amount says why, with its counts ("You chose Subscriptions for Amazon amounts like ₹199 (4 times)")
                    if (picked == null && category == r.categoryId && r.categoryReason.isNotEmpty()) Text(r.categoryReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("inbox_why_${r.id}"))
                    }
                    // money in with nothing to go on: the first income categories as chips right on the card (a person's category is optional, so this stays one line and Add still works without a choice)
                    if (credit && picked == null && category.isEmpty() && !changing && !paired) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        offeredCategories(categories, income = true).take(CREDIT_QUICK_CHIPS).forEach { c -> AssistChip(onClick = { picked = c.id }, label = { Text(c.name) }, modifier = Modifier.testTag("inbox_income_${r.id}_${c.id}")) }
                    }
                    if (changing) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { accounts.forEach { a -> FilterChip(selected = a.id == account, onClick = { pickedAccount = a.id }, label = { Text(a.name) }, modifier = Modifier.testTag("inbox_account_${r.id}_${a.id}")) } }
                        run {
                            val offered = offeredCategories(categories, income = credit, keep = category)
                        if (onSameShop != null && r.payeeName.isNotEmpty()) {
                            var joining by remember(r.id) { mutableStateOf(false) }
                            TextButton(onClick = { joining = true }, modifier = Modifier.testTag("inbox_same_shop_${r.id}")) { Text("Same shop as…") }
                            if (joining) SameShopDialog(r.payeeName, sameShopNames.filter { n -> !n.equals(r.payeeName, ignoreCase = true) }.take(SAME_SHOP_CARD_SUGGESTIONS), onSameShop, onClose = { joining = false })
                        }
                            // the first few as chips (and the chosen one, wherever it is); every other one is behind "More", in a list with search, as on the Add screen
                            val chips = (offered.take(CATEGORY_CHIPS) + offered.filter { it.id == category }).distinctBy { it.id }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                chips.forEach { c -> FilterChip(selected = c.id == category, onClick = { picked = c.id }, label = { Text(c.name) }, modifier = Modifier.testTag("inbox_category_${r.id}_${c.id}")) }
                                if (offered.size > CATEGORY_CHIPS) AssistChip(onClick = { pickingCategory = true }, label = { Text("More (${offered.size - chips.size})") }, modifier = Modifier.testTag("inbox_more_categories_${r.id}"))
                            }
                            if (pickingCategory) CategoryPickerDialog(offered, category, onPick = { id -> picked = id; pickingCategory = false }, onClose = { pickingCategory = false })
                        }
                    }
                }
                if (paired) TextButton(onClick = { changing = !changing }, modifier = Modifier.testTag("inbox_change_${r.id}")) { Text(if (changing) "Done" else "Change") }
            }
            }
            problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("inbox_problem_${r.id}")) }
            // the answer: one big Add (the label says what it will do) and Skip; a bill payment asks "Mark paid", the other leg of a transfer joins it
            if (billMode && billPay != null) {
                val billId = billPick ?: billPay.billId
                val from = fromPick ?: billPay.payFromId
                val card = cardPick ?: billPay.cardAccountId
                val sameHere = billPay.sameEntryId.isNotEmpty()
                val isCard = billPay.isCard
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    when {
                        billId.isNotEmpty() -> Button(
                            onClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm); onBillPaid(r.id, billId, from, card, billPay.sameEntryId) { p -> problem = p } },
                            enabled = from.isNotEmpty() || sameHere, modifier = Modifier.weight(1f).testTag("inbox_billpay_add_${r.id}"),
                        ) { Text("Mark paid") }
                        sameHere -> Button(
                            onClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm); onJoin(r.id, billPay.sameEntryId) { p -> problem = p } },
                            modifier = Modifier.weight(1f).testTag("inbox_billpay_join_${r.id}"),
                        ) { Text("Same payment: add to it") }
                        else -> Button(
                            onClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm); onBillPaid(r.id, "", from, card, "") { p -> problem = p } },
                            enabled = from.isNotEmpty(), modifier = Modifier.weight(1f).testTag("inbox_billpay_record_${r.id}"),
                        ) { Text(if (isCard) "Record a card bill payment of ${r.amountText}" else "Record the bill payment of ${r.amountText}") }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { notBill = true }, modifier = Modifier.testTag("inbox_billpay_not_${r.id}")) { Text("Not this") }
                    OutlinedButton(onClick = { onDismiss(r.id) }, modifier = Modifier.testTag("inbox_billpay_ignore_${r.id}")) { Text("Ignore") }
                }
            } else if (legMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm); onJoin(r.id, r.otherLegEntryId) { p -> problem = p } },
                        modifier = Modifier.weight(1f).testTag("inbox_join_${r.id}"),
                    ) { Text("Add to that transfer") }
                    OutlinedButton(onClick = { onDismiss(r.id) }, modifier = Modifier.testTag("inbox_skip_${r.id}")) { Text("Skip") }
                }
                TextButton(onClick = { asNew = true }, modifier = Modifier.testTag("inbox_leg_apart_${r.id}")) { Text("It is a different payment") }
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                        // a transfer the person chose goes from the text's account to the other (or the other way for money in); one the app proposed goes where its card says
                        val from = if (pickedTransfer) (if (credit) other else account) else fromAcct
                        val to = if (pickedTransfer) (if (credit) account else other) else toAcct
                        onConfirm(r.id, account, category, if (paired) r.treatment else if (pickedTransfer) "TRANSFER" else "PAYMENT", from, to, r.refundOfEntry) { p -> problem = p }
                    },
                    enabled = if (pickedTransfer) account.isNotEmpty() && other.isNotEmpty() else account.isNotEmpty() || paired, modifier = Modifier.weight(1f).testTag("inbox_add_${r.id}"),
                ) { Text(if (r.duplicateNote != null) "Add anyway" else if (pickedTransfer) "Add as transfer" else if (paired) "Add as ${r.treatmentLabel}" else "Add") }
                OutlinedButton(onClick = { onDismiss(r.id) }, modifier = Modifier.testTag("inbox_skip_${r.id}")) { Text("Skip") }
            }
            // what is not needed to answer: the other categories, and why the account is asked. One toggle, the same place on every card that has any of it (right under Add and Skip).
            if (hasMore) {
                TextButton(onClick = { moreOptions = !moreOptions }, modifier = Modifier.testTag("inbox_more_options_${r.id}")) { Text(if (moreOptions) "Fewer options" else "More options") }
                if (moreOptions) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (accountDetail) AccountQuestionDetail(r)
                    if (alternativesNow && !alternativesInline) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        r.alternatives.forEach { id -> categories.firstOrNull { it.id == id }?.let { c -> AssistChip(onClick = { picked = c.id }, label = { Text("Or ${c.name}") }, modifier = Modifier.testTag("inbox_alt_${r.id}_${c.id}")) } }
                    }
                }
            }
            // everything less usual, in small buttons that wrap on a narrow screen
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (r.duplicateNote != null) TextButton(onClick = { onDismiss(r.id) }, modifier = Modifier.testTag("inbox_same_${r.id}")) { Text("Same one: skip") }
                if (proposed) TextButton(onClick = { mode = if (eff == "PLAIN") null else "PLAIN" }, modifier = Modifier.testTag("inbox_plain_${r.id}")) { Text(if (eff == "PLAIN") "Use ${r.treatmentLabel}" else if (credit) "Add as income instead" else "Add as expense instead") }
                if (!billMode && !legMode) TextButton(onClick = { onFlip(r.id) }, modifier = Modifier.testTag("inbox_flip_${r.id}")) { Text(if (credit) "It was money out" else "It was money in") }
                if (payeeGroup.isNotEmpty() && !billMode && !legMode) TextButton(onClick = { onAddPayee(payeeGroup) }, modifier = Modifier.testTag("inbox_add_payee_${r.id}")) { Text("Add all from ${r.payee?.takeIf { it.isNotBlank() } ?: r.issuer} (${payeeGroup.size})") }
                if (r.nameable && !billMode && !legMode) TextButton(onClick = { naming = !naming }, modifier = Modifier.testTag("inbox_name_btn_${r.id}")) { Text(if (r.payee.isNullOrBlank()) "Name this payee" else "Rename") }
                TextButton(onClick = { showText = !showText }, modifier = Modifier.testTag("inbox_text_toggle_${r.id}")) { Text(if (showText) "Hide message" else "Show message") }
            }
            if (naming && r.nameable) {
                var nameText by rememberSaveable(r.id) { mutableStateOf("") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.OutlinedTextField(nameText, { nameText = it }, label = { Text(r.payee?.let { "A name for $it" } ?: "A name for this payee") }, singleLine = true, modifier = Modifier.weight(1f).testTag("inbox_name_${r.id}"))
                    Button(onClick = { onName(r.id, nameText); naming = false }, modifier = Modifier.testTag("inbox_name_ok_${r.id}")) { Text("Save") }
                }
                Text("Every later payment to or from the same address shows this name.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (showText) {
                Column(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(r.body, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("inbox_text_${r.id}"))
                    Text("Read automatically: please check it before adding.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { reporting = true }, modifier = Modifier.testTag("inbox_report_${r.id}")) { Text("Share this message for a fix") }
                }
            }
        }
    }
    }
}

/** The type of an item in To review: Expense, Income or Transfer. [transfer] says Transfer is chosen; otherwise [credit] picks Income or Expense. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun KindChips(id: String, transfer: Boolean, credit: Boolean, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("inbox_kinds_$id")) {
        listOf("EXPENSE" to "Expense", "INCOME" to "Income", "TRANSFER" to "Transfer").forEach { (k, label) ->
            val selected = if (transfer) k == "TRANSFER" else k == (if (credit) "INCOME" else "EXPENSE")
            FilterChip(selected = selected, onClick = { onPick(k) }, label = { Text(label) }, modifier = Modifier.testTag("inbox_kind_${id}_$k"))
        }
    }
}

/**
 * What a card for a bill payment text asks and offers: the question ("Mark the Axis card bill as paid?"), the open bills it may pay (or that none matched), whether the same payment is already in the ledger, which
 * account the money left, which card it went to, and how it is recorded. The buttons are on the card itself.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BillPaymentBox(
    r: InboxRowUi, bill: app.expenses.BillPayUi, accounts: List<AccountUi>,
    billId: String, onBill: (String) -> Unit, from: String, onFrom: (String) -> Unit, card: String, onCard: (String) -> Unit,
) {
    val nameOf = { id: String -> accounts.firstOrNull { it.id == id }?.name ?: "choose an account" }
    Column(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(bill.kindLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.testTag("inbox_billpay_label_${r.id}"))
        Text(bill.question, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("inbox_billpay_question_${r.id}"))
        if (bill.sameNote.isNotEmpty()) Callout(bill.sameNote, MaterialTheme.colorScheme.tertiary, Modifier.testTag("inbox_billpay_same_${r.id}"))
        if (bill.matches.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                bill.matches.forEach { m ->
                    FilterChip(
                        selected = billId == m.billId, onClick = { onBill(m.billId) },
                        label = { Text("${m.title} · ${m.amountText} · ${m.dueText}" + when (m.fit) { "total" -> " · the total"; "minimum" -> " · the minimum"; "part" -> " · part of it"; else -> "" }) },
                        modifier = Modifier.testTag("inbox_billpay_pick_${r.id}_${m.billId}"),
                    )
                }
                FilterChip(selected = billId.isEmpty(), onClick = { onBill("") }, label = { Text("None of these") }, modifier = Modifier.testTag("inbox_billpay_pick_${r.id}_none"))
            }
        } else Text("No open bill matches this payment, so there is nothing to mark paid. It can still be recorded.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("inbox_billpay_nomatch_${r.id}"))
        if (bill.sameEntryId.isNotEmpty() && billId.isNotEmpty()) {
            Text("The payment is already in your ledger, so nothing new is recorded: the bill is marked paid by that entry.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("inbox_billpay_records_${r.id}"))
        } else if (bill.sameEntryId.isEmpty()) {
            if (!bill.received) Text("Paid from", style = MaterialTheme.typography.labelMedium)
            else Text("Which account paid it?", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.filter { it.id in bill.payFromChoices }.forEach { a -> FilterChip(selected = a.id == from, onClick = { onFrom(a.id) }, label = { Text(a.name) }, modifier = Modifier.testTag("inbox_billpay_from_${r.id}_${a.id}")) }
            }
            if (bill.isCard && bill.cardChoices.isNotEmpty()) {
                Text("Which card", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.filter { it.id in bill.cardChoices }.forEach { a -> FilterChip(selected = a.id == card, onClick = { onCard(a.id) }, label = { Text(a.name) }, modifier = Modifier.testTag("inbox_billpay_card_${r.id}_${a.id}")) }
                    FilterChip(selected = card.isEmpty(), onClick = { onCard("") }, label = { Text("None of these") }, modifier = Modifier.testTag("inbox_billpay_card_${r.id}_none"))
                }
            }
            Text(
                if (card.isNotEmpty()) "Recorded as a transfer from ${nameOf(from)} to ${nameOf(card)}, not as spending."
                else "Recorded as an expense in Bills, paid from ${nameOf(from)}" + if (bill.isCard) " (the app has no account for this card)." else ".",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("inbox_billpay_records_${r.id}"),
            )
        }
    }
}

/**
 * One card for the two texts of one move of money between the person's own accounts ("Transfer from Main to Savings ₹45,250.75"): the text that says money left one account and the text
 * that says it arrived in another. Add records ONE transfer (neither text is also an expense or income); "These are separate" keeps them as two items. Both original texts are one tap away.
 */
@Composable
private fun TransferPairCard(r: InboxRowUi, accounts: List<AccountUi>, onConfirmPair: (String, String, String, String, (String?) -> Unit) -> Unit, onApart: (String, String) -> Unit, modifier: Modifier = Modifier, progress: String = "") {
    val nameOf = { id: String -> accounts.firstOrNull { it.id == id }?.name ?: "your account" }
    var showText by rememberSaveable(r.id) { mutableStateOf(false) }
    var problem by remember(r.id) { mutableStateOf<String?>(null) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    androidx.compose.material3.Card(
        modifier.fillMaxWidth().testTag("inbox_${r.id}"), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(progress, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("inbox_progress_${r.id}"))
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                CategoryAvatar(r.payee ?: "Transfer", r.payee ?: "Transfer", Modifier.padding(end = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Transfer from ${nameOf(r.fromAccountId)} to ${nameOf(r.toAccountId)} ${r.amountText}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("inbox_transfer_${r.id}"))
                    Text(
                        "Between your own accounts · ${r.dateText}" + (r.payee?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("inbox_meta_${r.id}"),
                    )
                }
            }
            Text(
                (if (r.pairBasis == "NAMED_ACCOUNT") "Two texts, one move of money: one names the other account's digits. " else "Two texts, one move of money: the same amount went out of one account and into another within minutes. ") +
                    "Added as one transfer, it is neither spending nor income.",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("inbox_transfer_why_${r.id}"),
            )
            problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("inbox_problem_${r.id}")) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm); onConfirmPair(r.id, r.pairedItemId, r.fromAccountId, r.toAccountId) { p -> problem = p } },
                    enabled = r.fromAccountId.isNotEmpty() && r.toAccountId.isNotEmpty(), modifier = Modifier.weight(1f).testTag("inbox_transfer_add_${r.id}"),
                ) { Text("Add transfer") }
                OutlinedButton(onClick = { onApart(r.id, r.pairedItemId) }, modifier = Modifier.testTag("inbox_transfer_apart_${r.id}")) { Text("These are separate") }
            }
            TextButton(onClick = { showText = !showText }, modifier = Modifier.testTag("inbox_transfer_show_${r.id}")) { Text(if (showText) "Hide messages" else "Show both messages") }
            if (showText) Column(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Money out of ${nameOf(r.fromAccountId)}: ${r.body}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("inbox_transfer_text_out_${r.id}"))
                Text("Money into ${nameOf(r.toAccountId)}: ${r.pairedBody}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("inbox_transfer_text_in_${r.id}"))
            }
        }
    }
}

/** A note with a coloured edge, for the things that change what the right action is (a likely repeat, a related entry). */
@Composable
private fun Callout(text: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.12f)).padding(12.dp))
}

/** Turning SMS capture on or off, and what it does and does not do, before Android asks for the permission. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureSettingsScreen(
    settings: CaptureSettingsUi, permissionGranted: Boolean, notifAccess: Boolean, onToggle: (Boolean) -> Unit, onToggleNotif: (Boolean) -> Unit, onToggleEmail: (Boolean) -> Unit, onOpenAccessSettings: () -> Unit, onHeaders: (String) -> Unit, onOpenRules: () -> Unit, onOpenHealth: () -> Unit, onBack: () -> Unit,
    /** Runs a text through the same reading as a message from the Shortcuts automation (iPhone's setup page uses it for "Send a test now"). */
    onTestMessage: ((String, (app.expenses.PasteOutcome) -> Unit) -> Unit)? = null,
    /** Payment-looking texts skipped from senders not on the list, with a one-tap "Treat NAME as my bank" ([SkippedSendersSection]); empty when there are none. */
    skipped: List<app.expenses.SkippedSenderUi> = emptyList(), onTrustSkipped: (String) -> Unit = {},
) {
    if (LocalPlatform.current.supportsShortcutsCapture) { ShortcutsCaptureScreen(settings, onHeaders, onOpenRules, onOpenHealth, onBack, onTestMessage, skipped, onTrustSkipped); return }
    // the Google Play build has no SMS permission: only the payment-app notifications are offered there (every other build keeps the text switch)
    val readsTexts = LocalPlatform.current.supportsSmsCapture
    // Google Play's rule for the text permission: a prominent disclosure, answered with a tap, before Android's own permission question
    var disclosing by rememberSaveable { mutableStateOf(false) }
    if (disclosing) AlertDialog(
        onDismissRequest = { disclosing = false },
        title = { Text("Allow Expenses to read your bank texts?", modifier = Modifier.testTag("sms_disclosure")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This app uses the permission to receive text messages (RECEIVE_SMS) for one purpose: to find the payments, credits and bills in texts from your bank, card or biller, and put them in a list for you to approve, so you can track your money.")
                Text("Each text is read on this phone, and the sender is checked first. Texts from people, one-time passwords and senders that are not a known bank or biller are ignored and thrown away: only a count is kept. Nothing is sent anywhere: the app has no internet access at all.")
                Text("You can turn this off in this screen at any time. Android will ask you next; if you say no, the app works as before and you can add entries by hand.")
            }
        },
        confirmButton = { TextButton(onClick = { disclosing = false; onToggle(true) }, modifier = Modifier.testTag("sms_disclosure_agree")) { Text("Agree and continue") } },
        dismissButton = { TextButton(onClick = { disclosing = false }, modifier = Modifier.testTag("sms_disclosure_decline")) { Text("No thanks") } },
    )
    Scaffold(topBar = { AreaTopBar(if (readsTexts) "Capture from texts" else "Capture from payment apps", Area.CAPTURE, Icons.Outlined.Sms, onBack, "capture_back") }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Capture finds payments in the alerts your phone gets and lists them in To review. Nothing is added until you approve it.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("capture_intro"))
            CaptureSection("Set up", "What this app may read. Each one is off until you turn it on.", "setup") {
                if (readsTexts) {
                    CaptureSource("Read bank texts", "Finds payments, credits and bills in texts from your bank, card or biller. Read on this phone, never sent anywhere.", settings.smsOn && permissionGranted, { on -> if (on && !permissionGranted) disclosing = true else onToggle(on) }, "capture_sms_switch", "sms", notes = {
                        if (settings.smsOn && !permissionGranted) Text("Android has not allowed this app to receive texts. Turn the switch on again to be asked.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("capture_permission_missing"))
                    }) {
                        Text("What this does", style = MaterialTheme.typography.labelMedium)
                        Text("When a text arrives from a bank or card sender, this app reads it on your phone and, if it is a payment or a credit, puts it in your To review list. If it says a bill is due (a card statement, electricity, water, gas, mobile, broadband, an EMI, an insurance premium), it is shown on the Bills page and you are told. You decide whether to add either.", style = MaterialTheme.typography.bodyMedium)
                        Text("What it does not do", style = MaterialTheme.typography.labelMedium)
                        Text("Texts from people, one-time passwords and anything from a sender that is not on the list below are ignored without being read; the sender is checked first. The list is banks, cards and a few billers (for example BESCOM, Airtel, Jio, LIC); a bill's text is kept only until you add or skip it. Nothing is sent anywhere: the reading happens on this phone. A text is kept only until you add or skip it; after that only the amount, date and reference stay.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                CaptureSource("Read payment app notifications", "Finds payments in Google Pay, PhonePe, Paytm and BHIM alerts. Needs the phone's Notification access.", settings.notifOn && notifAccess, onToggleNotif, "capture_notif_switch", "notif", notes = {
                    if (settings.notifOn && !notifAccess) Text("Notification access is not given to this app yet.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("capture_notif_access_missing"))
                    OutlinedButton(onClick = onOpenAccessSettings, modifier = Modifier.testTag("capture_notif_settings")) { Text("Open notification access settings") }
                }) {
                    Text("Reads the notification a payment app shows after a payment (Google Pay, PhonePe, Paytm, BHIM) and puts it in To review. Notifications from every other app are never read. This needs the phone's Notification access, which you turn on in its settings.", style = MaterialTheme.typography.bodyMedium)
                }
                CaptureSource("Read bank alert emails", "Off by default. Finds bank alert emails from the notification Gmail or Outlook shows. Uses the same Notification access.", settings.emailOn && notifAccess, onToggleEmail, "email_capture_switch", "email", notes = {
                    if (settings.emailOn && !notifAccess) Text("Notification access is not given to this app yet.", color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("email_capture_access_missing"))
                }) {
                    Text("Off by default. When a bank sends you an email, Gmail, Outlook or Yahoo Mail shows a notification, and this reads that notification (the sender's name, the subject and the start of the message the email app shows) on this phone. Only a notification from a bank or card issuer on the built-in list (or a name you add below) whose words read like a payment, a credit or a bill is kept, and it goes to To review for you to add or skip. Every other email, one-time passwords and mail from people are ignored. Nothing is sent anywhere. The email app's own notification has to show the text: Gmail shows the subject and the start of the message, so an alert whose amount comes later in the email, or a phone set to hide notification contents, is missed. This uses the same Notification access as above.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("email_capture_note"))
                }
                Collapsible("Bank sender names", "senders", startOpen = true) {
                    Text("Only senders on the built-in list of banks and billers are read. If your bank's texts are missing, add its name here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TrustedSendersEditor(settings.extraHeaders, onHeaders)
                    Text("Add the name that appears after the first dash, for example HDFCBK from AX-HDFCBK-S.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SkippedSendersSection(settings.extraHeaders, skipped, onTrustSkipped)
                }
            }
            CaptureSection("Rules", "Your own words for putting a payment in a category, and what the app learned from your choices.", "rules") {
                OutlinedButton(onClick = onOpenRules, modifier = Modifier.testTag("open_rules")) { Text("Category rules") }
            }
            CaptureSection("Health", "Whether texts and alerts are reaching the app, and what was skipped and why.", "health") {
                OutlinedButton(onClick = onOpenHealth, modifier = Modifier.testTag("open_health")) { Text("How capture is doing") }
            }
        }
    }
}

/** One part of the capture screens: a heading, a line saying what it is, then its controls. */
@Composable
internal fun CaptureSection(title: String, what: String, tag: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("capture_section_$tag"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHead(title, what, tag)
        content()
    }
}

/** The heading of a part of a screen, and one line saying what the part is. */
@Composable
internal fun SectionHead(title: String, what: String, tag: String) {
    Column(Modifier.fillMaxWidth().testTag("section_$tag"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
        Text(what, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** More learned choices than this start folded. */
internal const val LEARNED_OPEN_MAX = 8

/** A group that folds: its [title] with an arrow, and [content] while open. [startOpen] is only where it starts; what the person chose is kept while the screen lives. */
@Composable
internal fun Collapsible(title: String, tag: String, startOpen: Boolean = false, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    var open by rememberSaveable(tag) { mutableStateOf(startOpen) }
    Column(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.heightIn(min = 48.dp).semantics(mergeDescendants = true) { stateDescription = if (open) "Open" else "Folded" }.testTag("capture_group_$tag"), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
        }
        if (open) Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

/** One thing capture can read: its title with the switch, one line saying what it is, what must be said at once ([notes]) and the long explanation folded under "Details". */
@Composable
private fun CaptureSource(
    title: String, summary: String, on: Boolean, onChange: (Boolean) -> Unit, switchTag: String, key: String,
    notes: @Composable () -> Unit, details: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(checked = on, onCheckedChange = onChange, modifier = Modifier.testTag(switchTag))
        }
        Text(summary, style = MaterialTheme.typography.bodyMedium)
        notes()
        var open by rememberSaveable(key) { mutableStateOf(false) }
        TextButton(onClick = { open = !open }, modifier = Modifier.testTag("capture_details_$key")) { Text(if (open) "Hide details" else "Details: what it reads and what it does not") }
        if (open) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { details() }
    }
}

/** The person's own rules: "when the payee or text mentions X, use this category", tried top to bottom, tested against past entries before saving. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
    /** The names the person counts as one shop, shown under what the app has learned (see [AliasControls]); null where there is none. */
    shops: AliasControls? = null,
fun CaptureRulesScreen(
    rules: List<app.expenses.CaptureRuleUi>, categories: List<CategoryUi>,
    onAdd: (pattern: String, categoryId: String, done: (String?) -> Unit) -> Unit, onRemove: (String) -> Unit, onMove: (String, Boolean) -> Unit,
    onTest: (String, (Long) -> Unit) -> Unit, onBack: () -> Unit,
    /** What the app learned from the person's own choices (see [learnedSection]); empty by default so a caller without it shows the screen as it was. */
    learned: List<app.expenses.LearnedUi>? = null, onForgetLearned: (String) -> Unit = {}, onChangeLearned: (String, String, (String?) -> Unit) -> Unit = { _, _, _ -> }, onFileLearned: (String) -> Unit = {},
    learnedUndo: app.expenses.ScreenUndo? = null, onUndoLearned: (String) -> Unit = {}, onUndoLearnedGone: (String) -> Unit = {},
) {
    val undoHost = rememberUndoSnackbar(learnedUndo, onUndoLearned, onUndoLearnedGone)
    var pattern by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    // null: a short list is open and a long one folded, until the person opens or folds it
    var learnedOpen by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var tested by remember { mutableStateOf<Long?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { AreaTopBar("Category rules", Area.CAPTURE, Icons.Outlined.Label, onBack, "rules_back") }, snackbarHost = { androidx.compose.material3.SnackbarHost(undoHost) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("rules_list"), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionHead("Your rules", "When a captured payment (or money you receive) mentions these words, it gets this category. Rules are tried from the top; the first match wins, and a rule beats what the app learned from your earlier choices.", "rules_mine")
                    if (rules.isEmpty()) Text("No rules yet. Add the first one below, for example the word Swiggy and the category Food.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("rules_empty"))
                }
            }
            itemsIndexed(rules, key = { _, r -> r.id }) { i, r ->
                Row(Modifier.animateItem().fillMaxWidth().testTag("rule_${r.pattern}"), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text("\"${r.pattern}\"", style = MaterialTheme.typography.bodyLarge); Text("→ ${r.categoryName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(onClick = { onMove(r.id, true) }, enabled = i > 0, modifier = Modifier.testTag("rule_up_${r.pattern}")) { Text("Up") }
                    TextButton(onClick = { onMove(r.id, false) }, enabled = i < rules.lastIndex, modifier = Modifier.testTag("rule_down_${r.pattern}")) { Text("Down") }
                    TextButton(onClick = { onRemove(r.id) }, modifier = Modifier.testTag("rule_remove_${r.pattern}")) { Text("Remove") }
                }
            }
            item { HorizontalDivider() }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHead("New rule", "A word from the payee or the text, and the category it should get. Test it on your past entries before you add it.", "rules_new")
                    OutlinedTextField(pattern, { pattern = it; tested = null; problem = null }, label = { Text("Payee or text contains") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("rule_pattern"))
                    // a rule for money out takes an expense category and one for money in an income category: an expense rule never fires on a credit, nor an income rule on a payment
                    var moneyIn by rememberSaveable { mutableStateOf(false) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !moneyIn, onClick = { if (moneyIn) { moneyIn = false; category = ""; problem = null } }, label = { Text("Money out") }, modifier = Modifier.testTag("rule_kind_out"))
                        FilterChip(selected = moneyIn, onClick = { if (!moneyIn) { moneyIn = true; category = ""; problem = null } }, label = { Text("Money in") }, modifier = Modifier.testTag("rule_kind_in"))
                    }
                    val offered = offeredCategories(categories, income = moneyIn)
                    val chips = (offered.take(CATEGORY_CHIPS) + offered.filter { it.id == category }).distinctBy { it.id }
                    var pickingCategory by rememberSaveable { mutableStateOf(false) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chips.forEach { c -> FilterChip(selected = c.id == category, onClick = { category = c.id; problem = null }, label = { Text(c.name) }, modifier = Modifier.testTag("rule_category_${c.id}")) }
                        if (offered.size > CATEGORY_CHIPS) AssistChip(onClick = { pickingCategory = true }, label = { Text("More (${offered.size - chips.size})") }, modifier = Modifier.testTag("rule_more_categories"))
                    }
                    if (pickingCategory) CategoryPickerDialog(offered, category, onPick = { id -> category = id; problem = null; pickingCategory = false }, onClose = { pickingCategory = false })
                    tested?.let { Text(if (it == 0L) "No past entry mentions this." else "It would have matched $it of your past entries.", modifier = Modifier.testTag("rule_tested")) }
                    problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("rule_problem")) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { // wraps at a large font: a Row squeezed "Add rule" to a letter-wide sliver
                        OutlinedButton(onClick = { onTest(pattern) { tested = it } }, enabled = pattern.isNotBlank(), modifier = Modifier.testTag("rule_test")) { Text("Test on my history") }
                        Button(onClick = { onAdd(pattern, category) { p -> if (p == null) { pattern = ""; tested = null } else problem = p } }, enabled = pattern.isNotBlank() && category.isNotEmpty(), modifier = Modifier.testTag("rule_add")) { Text("Add rule") }
                    }
                }
            }
            if (learned != null) learnedSection(learned, categories, learnedOpen ?: (learned.size <= LEARNED_OPEN_MAX), { learnedOpen = !(learnedOpen ?: (learned.size <= LEARNED_OPEN_MAX)) }, onForgetLearned, onChangeLearned, onFileLearned, shops)
        }
    }

/** How many other waiting payees "Same shop as..." on a card offers as chips; the rest are typed. */
private const val SAME_SHOP_CARD_SUGGESTIONS = 6
}

/** How many categories show as chips on a captured item and in the rule form; the rest are behind "More". */
private const val CATEGORY_CHIPS = 10

/** How many income categories show right on a money-in card that has no suggestion; Change shows the rest. */
private const val CREDIT_QUICK_CHIPS = 4

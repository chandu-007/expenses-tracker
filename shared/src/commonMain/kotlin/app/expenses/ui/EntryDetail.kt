package app.expenses.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.expenses.AccountUi
import app.expenses.AttachmentUi
import app.expenses.CategoryUi
import app.expenses.Editable
import app.expenses.OriginUi
import app.expenses.PersonUi
import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.BillSplit
import app.expenses.core.domain.DraftKind
import app.expenses.core.domain.EntryDraft
import app.expenses.core.domain.EntryDrafts
import app.expenses.core.domain.Money
import app.expenses.core.domain.formatted
import app.expenses.core.domain.parseAmount
import app.expenses.dateOfMillis
import app.expenses.formatMillis
import app.expenses.systemZone

/** How an expense compares with what the person usually pays the same payee: the difference in minor units (above is positive) and the payee as the person wrote it. */
data class UsualAmountUi(val diffMinor: Long, val payee: String)

/**
 * "At Barbeque Nation this year · 4 visits · ₹4,860": the expenses to one payee this calendar year (the entry itself included), their number and their sum as the app writes money; [fromDay] and [toDay]
 * (epoch days, the first of January and today) are the dates "See all" searches between.
 */
data class PayeeYearUi(val payee: String, val visits: Int, val totalText: String, val fromDay: Long, val toDay: Long)

/** What the detail needs to put an entry back as a new one ("Duplicate"): the amount, category, account and note of an expense or income. Null for the kinds that have no sensible copy. */
fun EntryDraft.duplicable(): Boolean = id != null && (kind == DraftKind.EXPENSE || kind == DraftKind.INCOME) && !EntryDrafts.isSplit(this) && foreignCode.isEmpty()

/** Whether "Split with someone" is offered for this saved entry: a plain expense ([EntryDrafts.shareSaved]) that is not already expected back. */
fun EntryDraft.shareable(): Boolean = id != null && kind == DraftKind.EXPENSE && !EntryDrafts.isSplit(this) && foreignCode.isEmpty() && paidBy.isEmpty() && !expectBack

/** The kind's own flow, for the sign and colour of the amount. */
internal fun EntryDraft.flow(): Flow3 = when (kind) {
    DraftKind.EXPENSE -> Flow3.OUT
    DraftKind.INCOME, DraftKind.REFUND -> Flow3.IN
    DraftKind.TRANSFER -> Flow3.MOVE
    DraftKind.LOAN -> if (gave) Flow3.OUT else Flow3.IN
}

/** "Mon, 23 Nov · 11:30 am" (the year is said when it is not this one): the entry's day and time as the detail's line under the amount says them. */
internal fun detailWhen(millis: Long, zone: kotlinx.datetime.TimeZone, thisYear: Int): String {
    val year = dateOfMillis(millis, zone).year
    val time = localTimeOf(millis, zone)
    val day = formatMillis(millis, if (year == thisYear) "EEE, d MMM" else "EEE, d MMM yyyy", zone)
    val hour = (time.hour % 12).let { if (it == 0) 12 else it }
    return "$day · $hour:${time.minute.toString().padStart(2, '0')} ${if (time.hour < 12) "am" else "pm"}"
}

/**
 * The detail of an entry (the redesign): tapping an entry opens this, not the editor. The tile, the merchant, the amount big and "Expense · Mon, 23 Nov · 11:30 am"; a card of the facts (category, the
 * account, tags, note) where each row with a chevron changes that one fact in its own small editor and saves it at once ([onSaveField]); "At <payee> this year · 4 visits · ₹4,860 · See all", read once
 * the detail is on screen (never on its first frame), hidden under two visits; then the actions (Split with someone, Record a refund, Duplicate, Edit history) and Delete. Edit (the pencil at the top)
 * opens the whole form as before. The tapped row grows into it ([sharedContainer]); Back leaves it. Amounts are drawn with this package's [Text] and [MoneyText], so Hide amounts masks them, and the
 * spoken amount is the exact one.
 */
@Composable
fun EntryDetailScreen(
    form: Editable.Form,
    categories: List<CategoryUi>,
    accounts: List<AccountUi>,
    people: List<PersonUi>,
    attachments: List<AttachmentUi>,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    onDuplicate: (() -> Unit)?,
    onDelete: () -> Unit,
    onHistory: (() -> Unit)?,
    onRefund: (() -> Unit)?,
    onOpenAttachment: (AttachmentUi) -> Unit,
    /** How the amount compares with what the person usually pays the same payee, asked once the detail is on screen (it never delays the open); null where the screen has no such source. */
    usualAmount: (suspend (entryId: String?, note: String, amountMinor: Long) -> UsualAmountUi?)? = null,
    /** Saves the entry with one fact changed (a chevron row); the detail stays open. Null: the rows open the whole form instead. */
    onSaveField: ((EntryDraft) -> Unit)? = null,
    /** The tags already in use, offered in the tags editor. */
    allTags: List<String> = emptyList(),
    /** The payee's visits this year, asked once the detail is on screen; null where there is no such source. */
    payeeYear: (suspend (note: String) -> PayeeYearUi?)? = null,
    /** "See all": the payee's entries this year (Search on the name between the two days). */
    onSeeAll: (PayeeYearUi) -> Unit = {},
    /** "Split with someone": saves the expense shared with the friends chosen (their names, for what is said after). Null where it cannot be offered. */
    onShare: ((EntryDraft, List<String>) -> Unit)? = null,
    onCreatePerson: (name: String, onCreated: (String) -> Unit) -> Unit = { _, _ -> },
    /** Why the last change from this screen was refused, or null. */
    problem: String? = null,
) {
    val d = form.draft
    val currency = BaseCurrency.current
    val zone = systemZone()
    val flow = d.flow()
    val amountText = parseAmount(d.amountText, currency)?.formatted() ?: d.amountText
    val insight by produceState<UsualAmountUi?>(null, d.id, d.amountText, d.note) {
        value = if (usualAmount != null && d.kind == DraftKind.EXPENSE && !EntryDrafts.isSplit(d)) usualAmount(d.id, d.note, parseAmount(d.amountText, currency)?.minorUnits ?: 0L) else null
    }
    // the year at this payee: one indexed lookup, started once the detail is on screen (produceState runs after the first frame), so it never delays the open
    val year by produceState<PayeeYearUi?>(null, d.id, d.note, d.amountText) {
        value = if (payeeYear != null && d.kind == DraftKind.EXPENSE && d.note.isNotBlank()) payeeYear(d.note) else null
    }
    fun account(id: String) = accounts.firstOrNull { it.id == id }?.name ?: "—"
    val categoryName = categories.firstOrNull { it.id == d.category }?.let { displayName(categories, it) }
    val personName = people.firstOrNull { it.id == d.personId }?.name
    val title = when (d.kind) {
        DraftKind.TRANSFER -> "${account(d.accountId)} to ${account(d.toAccountId)}"
        DraftKind.LOAN -> personName ?: d.kind.label
        else -> d.note.trim().ifEmpty { d.kind.label }
    }
    val thisYear = dateOfMillis(app.expenses.nowMillis(), zone).year
    val split = EntryDrafts.isSplit(d)
    // the one fact being changed: "category", "from", "to", "tags", "note" or "share"
    var editingField by rememberSaveable(d.id) { mutableStateOf<String?>(null) }
    val save: (EntryDraft) -> Unit = { changed -> editingField = null; if (changed != d) onSaveField?.invoke(changed) }
    when (editingField) {
        "category" -> CategoryPickerDialog(
            offeredCategories(categories, d.kind == DraftKind.INCOME, d.category), d.category,
            onPick = { id -> save(d.copy(category = id)) }, onClose = { editingField = null },
        )
        "from" -> AccountPickDialog(fromLabel(d), accounts, d.accountId, { id -> save(d.copy(accountId = id)) }, { editingField = null })
        "to" -> AccountPickDialog("To", accounts, d.toAccountId, { id -> save(d.copy(toAccountId = id)) }, { editingField = null })
        "tags" -> TagsDialog(d.tags, allTags, { tags -> save(d.copy(tags = tags)) }, { editingField = null })
        "note" -> NoteDialog(d.note, { note -> save(d.copy(note = note)) }, { editingField = null })
        "share" -> ShareDialog(d, people, onCreatePerson, onShare = { shared, names -> editingField = null; onShare?.invoke(shared, names) }, onClose = { editingField = null })
    }
    val editable = onSaveField != null
    Surface(Modifier.fillMaxSize().sharedContainer("entry:${d.id}"), shape = Sheet.Shape, color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            SheetHandle(Modifier.testTag("sheet_handle"))
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back")) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                Spacer(Modifier.weight(1f))
                // the whole form, as before (each fact also has its own editor below)
                IconButton(onClick = onEdit, modifier = Modifier.testTag("detail_edit")) { Icon(Icons.Outlined.Edit, contentDescription = "Edit") }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // the tile, the merchant, the amount and what it was, in the middle
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EntryTile(title, if (d.kind == DraftKind.EXPENSE || d.kind == DraftKind.INCOME) categoryName.orEmpty() else "", d.kind.name, size = 64.dp)
                    Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp).semantics { heading() }.testTag("detail_title"))
                    CappedFontScale(LARGE_TEXT_SCALE) {
                        MoneyText(signedAmount(amountText, flow), style = NumberStyle.Hero, color = amountColor(flow), minFontSize = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp), modifier = Modifier.testTag("detail_amount"))
                    }
                    Text("${d.kind.label} · ${detailWhen(d.occurredAt, zone, thisYear)}", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("detail_sub"))
                    insight?.let { u ->
                        val reduce = LocalReduceMotion.current
                        AnimatedVisibility(
                            visibleState = remember { MutableTransitionState(false).apply { targetState = true } },
                            enter = if (reduce) EnterTransition.None else expandVertically(tween(Motion.CHIP)) + fadeIn(tween(Motion.CHIP)),
                        ) {
                            Row(
                                Modifier.padding(top = 8.dp).clip(Radius.Full).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                val gap = Money(if (u.diffMinor < 0) -u.diffMinor else u.diffMinor, currency).formatted()
                                Text("$gap ${if (u.diffMinor > 0) "above" else "below"} your usual at ${u.payee}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.testTag("detail_insight"))
                            }
                        }
                    }
                }

                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("detail_problem")) }

                // the facts: each row with a chevron changes that one fact
                SoftCard(Modifier.fillMaxWidth()) {
                    if (d.kind == DraftKind.EXPENSE || d.kind == DraftKind.INCOME) {
                        val value = if (split) d.splits.joinToString(" · ") { p -> (categories.firstOrNull { it.id == p.category }?.let { displayName(categories, it) } ?: "No category") + " " + (parseAmount(p.amountText, currency)?.formatted() ?: p.amountText) } else categoryName ?: "None"
                        FactRow(
                            "Category", value, "detail_category", rowTag = "detail_category_change",
                            onClick = if (split || !editable) onEdit else ({ editingField = "category" }), clickLabel = "Change the category",
                            lead = if (!split && categoryName != null) ({ CategoryAvatar(categoryName, categoryName, size = 24.dp) }) else null,
                        )
                    }
                    FactRow(fromLabel(d), account(d.accountId), "detail_account", rowTag = "detail_account_change", onClick = if (editable) ({ editingField = "from" }) else null, clickLabel = "Change the account")
                    if (d.kind == DraftKind.TRANSFER) FactRow("To", account(d.toAccountId), "detail_to_account", rowTag = "detail_to_account_change", onClick = if (editable) ({ editingField = "to" }) else null, clickLabel = "Change the account it went to")
                    if (d.kind == DraftKind.LOAN && personName != null) FactRow(if (d.gave) "Gave to" else "Got from", personName, "detail_person")
                    FactRow(
                        "Tags", d.tags.joinToString(", ").ifEmpty { "Add tags" }, "detail_tags", rowTag = "detail_tags_change",
                        onClick = if (editable) ({ editingField = "tags" }) else null, clickLabel = "Change the tags", link = d.tags.isEmpty(),
                        content = if (d.tags.isEmpty()) null else ({ m ->
                            FlowRow(m, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                d.tags.forEach { t -> Text(t, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(Radius.Full).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 4.dp)) }
                            }
                        }),
                    )
                    FactRow(
                        "Note", d.note.trim().ifEmpty { "Add a note" }, "detail_note", rowTag = "detail_note_change",
                        onClick = if (editable) ({ editingField = "note" }) else null, clickLabel = "Change the note", link = d.note.isBlank(),
                    )
                    if (d.expectBack) FactRow("Expecting back", listOfNotNull(people.firstOrNull { it.id == d.expectPersonId }?.name ?: d.expectFrom.takeIf { it.isNotBlank() }, d.expectAmount.takeIf { it.isNotBlank() }?.let { parseAmount(it, currency)?.formatted() }).joinToString(" · ").ifEmpty { "all of it" }, "detail_expect")
                    if (d.kind == DraftKind.REFUND && form.refundOf != null) FactRow("Refund of", form.refundOf, "detail_refund_of")
                }

                // this payee this year: shown once read, only from two visits
                year?.let { y ->
                    Row(
                        Modifier.fillMaxWidth().clip(Radius.M).background(MaterialTheme.colorScheme.surfaceVariant).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp).testTag("detail_payee_year"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                            Text("At ${y.payee} this year", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${y.visits} visits · ${y.totalText}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("detail_payee_year_figures"))
                        }
                        TextButton(onClick = { onSeeAll(y) }, modifier = Modifier.heightIn(min = 48.dp).testTag("detail_payee_see_all")) { Text("See all", fontWeight = FontWeight.SemiBold) }
                    }
                }

                form.origin?.let { DetailOrigin(it) }
                if (attachments.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Receipts and files", style = MaterialTheme.typography.titleSmall)
                    attachments.forEach { a -> TextButton(onClick = { onOpenAttachment(a) }, modifier = Modifier.testTag("attachment_open_${a.name}")) { Text("${a.name} · ${a.sizeText}", maxLines = 1) } }
                }

                // the actions, one list card
                val canShare = onShare != null && d.shareable()
                if (canShare || onRefund != null || onDuplicate != null || onHistory != null) SoftCard(Modifier.fillMaxWidth()) {
                    if (canShare) ActionRow(Icons.Outlined.People, "Split with someone", "detail_split") { editingField = "share" }
                    if (onRefund != null) ActionRow(Icons.Outlined.Replay, "Record a refund", "refund_start", onRefund)
                    if (onDuplicate != null) ActionRow(Icons.Outlined.ContentCopy, "Duplicate", "detail_duplicate", onDuplicate)
                    if (onHistory != null) ActionRow(Icons.Outlined.History, "Edit history", "history_open", onHistory)
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(Radius.M).clickable(role = Role.Button, onClick = onDelete).testTag("detail_delete"),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete entry", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** What the account row is called for this kind: Paid from, Received in, From... */
private fun fromLabel(d: EntryDraft): String = when (d.kind) {
    DraftKind.TRANSFER -> "From"; DraftKind.INCOME -> "Received in"; DraftKind.REFUND -> "Refunded to"; DraftKind.LOAN -> if (d.gave) "Paid from" else "Received in"; DraftKind.EXPENSE -> "Paid from"
}

/**
 * One row of the facts card: the name small on the left, the value, and a chevron when a tap changes it ([onClick]). [link] draws the value as an invitation in the accent ("Add a note"). At large text
 * the name goes above the value, so neither is squeezed. [content] replaces the plain value (the tags as pills); the value's own text stays the node [tag] for a screen reader and the tests.
 */
@Composable
private fun FactRow(
    label: String, value: String, tag: String, rowTag: String? = null, onClick: (() -> Unit)? = null, clickLabel: String? = null, link: Boolean = false,
    lead: (@Composable () -> Unit)? = null, content: (@Composable (Modifier) -> Unit)? = null,
) {
    val stacked = LocalDensity.current.fontScale >= 1.4f
    val base = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    val clickable = if (onClick != null) base.clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick) else base
    Row(
        clickable.semantics(mergeDescendants = true) {}.padding(horizontal = 16.dp, vertical = 10.dp).then(if (rowTag != null) Modifier.testTag(rowTag) else Modifier),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val valueText: @Composable (Modifier) -> Unit = { m ->
            Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                lead?.invoke()
                if (content != null) {
                    content(Modifier.weight(1f, fill = false).clearAndSetSemantics { })
                    // the words of the pills, for a screen reader and the tests (the pills are drawn above)
                    Text(value, modifier = Modifier.size(0.dp).testTag(tag))
                } else Text(
                    value, style = MaterialTheme.typography.bodyLarge, fontWeight = if (link) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (link) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag(tag),
                )
            }
        }
        if (stacked) Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            valueText(Modifier)
        } else {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(min = 96.dp, max = 120.dp))
            valueText(Modifier.weight(1f))
        }
        if (onClick != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One action of the list card: an icon and its words, the whole row one touch target. */
@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** The accounts as a list to choose one from ("Paid from"); choosing saves at once. */
@Composable
private fun AccountPickDialog(title: String, accounts: List<AccountUi>, selected: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose, title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                accounts.forEach { a ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.RadioButton) { onPick(a.id) }.testTag("detail_account_option_${a.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = a.id == selected, onClick = null)
                        Text(a.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}

/** The tags of the entry in the form's own tags editor; Save keeps them. */
@Composable
private fun TagsDialog(start: List<String>, suggestions: List<String>, onSave: (List<String>) -> Unit, onClose: () -> Unit) {
    var tags by rememberSaveable { mutableStateOf(start) }
    AlertDialog(
        onDismissRequest = onClose, title = { Text("Tags") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { TagsEditor(tags, suggestions) { tags = it } } },
        confirmButton = { TextButton(onClick = { onSave(tags) }, modifier = Modifier.testTag("detail_tags_save")) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

/** The note (for an expense, what it was or where: the name the entry goes by). */
@Composable
private fun NoteDialog(start: String, onSave: (String) -> Unit, onClose: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(start) }
    AlertDialog(
        onDismissRequest = onClose, title = { Text("Note") },
        text = { OutlinedTextField(text, { text = it.take(200) }, label = { Text("Note or merchant") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("detail_note_input")) },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }, modifier = Modifier.testTag("detail_note_save")) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

/**
 * "Split with someone": the form's "Split with friends" for an expense already saved. The friends who shared it (a new one can be added), equally or by what each owes (yours is what is left), and
 * what that means in one line. Saving keeps the expense as it is and records what each friend owes you.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ShareDialog(d: EntryDraft, people: List<PersonUi>, onCreatePerson: (String, (String) -> Unit) -> Unit, onShare: (EntryDraft, List<String>) -> Unit, onClose: () -> Unit) {
    val currency = BaseCurrency.current
    var friends by rememberSaveable { mutableStateOf(listOf<String>()) }
    var typedMode by rememberSaveable { mutableStateOf(false) }
    var owed by rememberSaveable { mutableStateOf(listOf<String>()) }
    var naming by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    val amount = parseAmount(d.amountText, currency)?.minorUnits
    val shared = d.copy(splitWith = friends, splitAmounts = if (typedMode) friends.indices.map { owed.getOrElse(it) { "" } } else emptyList())
    val ok = friends.isNotEmpty() && EntryDrafts.shareSaved(shared, currency) is app.expenses.core.domain.DraftResult.Ok
    AlertDialog(
        onDismissRequest = onClose, title = { Text("Split with someone") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(false to "Equally", true to "My amounts").forEach { (m, label) ->
                        FilterChip(selected = typedMode == m, onClick = { typedMode = m; owed = emptyList() }, label = { Text(label) }, modifier = Modifier.testTag("share_mode_${if (m) 1 else 0}"))
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    people.forEach { p ->
                        val at = friends.indexOf(p.id)
                        FilterChip(
                            selected = at >= 0,
                            onClick = { if (at >= 0) { friends = friends - p.id; owed = owed.filterIndexed { i, _ -> i != at } } else { friends = friends + p.id; owed = owed + "" } },
                            label = { Text(p.name) }, modifier = Modifier.testTag("share_person_${p.name}"),
                        )
                    }
                    AssistChip(onClick = { naming = true }, label = { Text("+ New person") }, modifier = Modifier.testTag("share_new_person"))
                }
                if (naming) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(newName, { newName = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f).testTag("share_new_person_name"))
                    TextButton(onClick = { val n = newName.trim(); onCreatePerson(n) { id -> friends = friends + id; owed = owed + "" }; naming = false; newName = "" }, enabled = newName.isNotBlank(), modifier = Modifier.testTag("share_new_person_ok")) { Text("Add") }
                }
                if (typedMode) friends.forEachIndexed { i, id ->
                    val name = people.firstOrNull { it.id == id }?.name.orEmpty()
                    OutlinedTextField(
                        owed.getOrElse(i) { "" }, { v -> if (v.all { it.isDigit() || it == '.' }) owed = owed.toMutableList().also { l -> while (l.size <= i) l += ""; l[i] = v } },
                        label = { Text("$name owes") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("share_amount_$name"),
                    )
                }
                val each = amount?.let { BillSplit.shares(it, friends.size) }
                val mine = amount?.let { BillSplit.mineAfter(it, owed.map { o -> parseAmount(o, currency)?.minorUnits ?: 0L }) }
                Text(
                    when {
                        friends.isEmpty() -> "Choose who shared this bill. What they owe you is kept under Lent and borrowed."
                        typedMode -> if (mine == null) "Enter what each friend owes. Your part is what is left." else "You pay ${Money(mine, currency).formatted()} of it. The whole bill already left your account."
                        each == null -> "That amount is too small to share."
                        else -> "You pay ${Money(each.second, currency).formatted()} of it; each of the ${friends.size} owes ${Money(each.first, currency).formatted()}."
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("share_summary"),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onShare(shared, friends.mapNotNull { id -> people.firstOrNull { it.id == id }?.name }) }, enabled = ok, modifier = Modifier.testTag("share_ok")) { Text("Split") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

/** "Read from": a card naming where the entry came from (a text, a notification, an email, a statement line) with a "Show text" line that opens the words the entry was read from ([OriginSection]). */
@Composable
private fun DetailOrigin(origin: OriginUi) {
    var shown by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().inlineCard(MaterialTheme.colorScheme.primary).padding(horizontal = 16.dp, vertical = 8.dp).testTag("detail_origin_card"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(origin.kindLabel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).testTag("detail_origin_kind"))
            TextButton(onClick = { shown = !shown }, modifier = Modifier.heightIn(min = 48.dp).testTag("entry_origin_show")) { Text(if (shown) "Hide text" else "Show text") }
        }
        if (shown) OriginSection(origin)
    }
}

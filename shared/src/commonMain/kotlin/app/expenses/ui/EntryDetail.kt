package app.expenses.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.runtime.produceState
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.expenses.AccountUi
import app.expenses.AttachmentUi
import app.expenses.CategoryUi
import app.expenses.Editable
import app.expenses.OriginUi
import app.expenses.PersonUi
import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.DraftKind
import app.expenses.core.domain.EntryDraft
import app.expenses.core.domain.EntryDrafts
import app.expenses.core.domain.Money
import app.expenses.core.domain.formatted
import app.expenses.core.domain.parseAmount
import app.expenses.formatMillis
import app.expenses.systemZone

/** How an expense compares with what the person usually pays the same payee: the difference in minor units (above is positive) and the payee as the person wrote it. */
data class UsualAmountUi(val diffMinor: Long, val payee: String)

/** What the detail needs to put an entry back as a new one ("Duplicate"): the amount, category, account and note of an expense or income. Null for the kinds that have no sensible copy. */
fun EntryDraft.duplicable(): Boolean = id != null && (kind == DraftKind.EXPENSE || kind == DraftKind.INCOME) && !EntryDrafts.isSplit(this) && foreignCode.isEmpty()

/** The kind's own flow, for the sign and colour of the amount. */
internal fun EntryDraft.flow(): Flow3 = when (kind) {
    DraftKind.EXPENSE -> Flow3.OUT
    DraftKind.INCOME, DraftKind.REFUND -> Flow3.IN
    DraftKind.TRANSFER -> Flow3.MOVE
    DraftKind.LOAN -> if (gave) Flow3.OUT else Flow3.IN
}

/**
 * The read-only detail of an entry (UI S2): tapping an entry opens this, not the editor. A glyph tile and the merchant, the amount big, its date and time, a card of the facts (category with "Change", account,
 * note, tags and what the kind adds), where it was read from, its history, and the actions: Edit (the form, as before), Duplicate and Delete (Home offers Undo). The tapped row grows into it
 * ([sharedContainer]); Back or Close leaves it. Amounts are drawn with this package's [Text] and [MoneyText], so Hide amounts masks them, and the spoken amount is the exact one.
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
) {
    val d = form.draft
    val currency = BaseCurrency.current
    val zone = systemZone()
    val flow = d.flow()
    val amountText = parseAmount(d.amountText, currency)?.formatted() ?: d.amountText
    val insight by produceState<UsualAmountUi?>(null, d.id, d.amountText, d.note) {
        value = if (usualAmount != null && d.kind == DraftKind.EXPENSE && !EntryDrafts.isSplit(d)) usualAmount(d.id, d.note, parseAmount(d.amountText, currency)?.minorUnits ?: 0L) else null
    }
    fun account(id: String) = accounts.firstOrNull { it.id == id }?.name ?: "—"
    val categoryName = categories.firstOrNull { it.id == d.category }?.let { displayName(categories, it) }
    val personName = people.firstOrNull { it.id == d.personId }?.name
    val title = when (d.kind) {
        DraftKind.TRANSFER -> "${account(d.accountId)} to ${account(d.toAccountId)}"
        DraftKind.LOAN -> personName ?: d.kind.label
        else -> d.note.trim().ifEmpty { d.kind.label }
    }
    val sub = listOfNotNull(
        when { EntryDrafts.isSplit(d) -> "Split"; d.kind == DraftKind.EXPENSE || d.kind == DraftKind.INCOME -> categoryName ?: "No category"; else -> d.kind.label },
        account(d.accountId).takeIf { d.kind != DraftKind.TRANSFER },
    ).joinToString(" · ")
    Surface(Modifier.fillMaxSize().sharedContainer("entry:${d.id}"), shape = Sheet.Shape, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            SheetHandle(Modifier.testTag("sheet_handle"))
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back")) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                Text(d.kind.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // the tile, the merchant, what it was and the amount in the middle, as the mock-up has it
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EntryTile(title, if (d.kind == DraftKind.EXPENSE || d.kind == DraftKind.INCOME) categoryName.orEmpty() else "", d.kind.name, size = 56.dp)
                    Text(title, style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp).testTag("detail_title"))
                    Text(sub, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("detail_sub"))
                    CappedFontScale(LARGE_TEXT_SCALE) {
                        SpokenAmount(amountText, flow, Modifier.padding(top = 8.dp)) { MoneyText(signedAmount(amountText, flow), style = NumberStyle.Hero, color = amountColor(flow), minFontSize = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp), modifier = Modifier.testTag("detail_amount")) }
                    }
                    Text(formatMillis(d.occurredAt, "EEE, d MMM yyyy · HH:mm", zone), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("detail_date"))
                    insight?.let { u ->
                        val reduce = LocalReduceMotion.current
                        AnimatedVisibility(
                            visibleState = remember { MutableTransitionState(false).apply { targetState = true } },
                            enter = if (reduce) EnterTransition.None else expandVertically(tween(Motion.CHIP)) + fadeIn(tween(Motion.CHIP)),
                        ) {
                            Row(
                                Modifier.padding(top = 8.dp).clip(Radius.Full).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                val gap = Money(if (u.diffMinor < 0) -u.diffMinor else u.diffMinor, currency).formatted()
                                Text("$gap ${if (u.diffMinor > 0) "above" else "below"} your usual at ${u.payee}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.testTag("detail_insight"))
                            }
                        }
                    }
                }

                Column(Modifier.fillMaxWidth().standardCard().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (d.kind == DraftKind.EXPENSE || d.kind == DraftKind.INCOME) {
                        FactRow("Category", if (EntryDrafts.isSplit(d)) d.splits.joinToString(" · ") { p -> (categories.firstOrNull { it.id == p.category }?.let { displayName(categories, it) } ?: "No category") + " " + (parseAmount(p.amountText, currency)?.formatted() ?: p.amountText) } else categoryName ?: "None", "detail_category", action = "Change" to onEdit)
                    }
                    FactRow(when (d.kind) { DraftKind.TRANSFER -> "From"; DraftKind.INCOME -> "Received in"; DraftKind.REFUND -> "Refunded to"; DraftKind.LOAN -> if (d.gave) "Paid from" else "Received in"; else -> "Account" }, account(d.accountId), "detail_account")
                    if (d.kind == DraftKind.TRANSFER) FactRow("To", account(d.toAccountId), "detail_to_account")
                    if (d.kind == DraftKind.LOAN && personName != null) FactRow(if (d.gave) "Gave to" else "Got from", personName, "detail_person")
                    if (d.kind != DraftKind.EXPENSE && d.kind != DraftKind.LOAN && d.note.isNotBlank()) FactRow("Note", d.note.trim(), "detail_note")
                    if (d.kind == DraftKind.LOAN && d.note.isNotBlank()) FactRow("Note", d.note.trim(), "detail_note")
                    if (d.tags.isNotEmpty()) FactRow("Tags", d.tags.joinToString(", "), "detail_tags")
                    if (d.expectBack) FactRow("Expecting back", listOfNotNull(people.firstOrNull { it.id == d.expectPersonId }?.name ?: d.expectFrom.takeIf { it.isNotBlank() }, d.expectAmount.takeIf { it.isNotBlank() }?.let { parseAmount(it, currency)?.formatted() }).joinToString(" · ").ifEmpty { "all of it" }, "detail_expect")
                    if (d.kind == DraftKind.REFUND && form.refundOf != null) FactRow("Refund of", form.refundOf, "detail_refund_of")
                }

                form.origin?.let { DetailOrigin(it) }
                if (attachments.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Receipts and files", style = MaterialTheme.typography.titleSmall)
                    attachments.forEach { a -> TextButton(onClick = { onOpenAttachment(a) }, modifier = Modifier.testTag("attachment_open_${a.name}")) { Text("${a.name} · ${a.sizeText}", maxLines = 1) } }
                }
                if (onHistory != null) OutlinedButton(onClick = onHistory, modifier = Modifier.testTag("history_open")) { Text("History of this entry") }
                if (onRefund != null) OutlinedButton(onClick = onRefund, modifier = Modifier.testTag("refund_start")) { Text("Record a refund for this expense") }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onEdit, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("detail_edit")) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Edit")
                }
                if (onDuplicate != null) FilledTonalIconButton(onClick = onDuplicate, modifier = Modifier.size(52.dp).testTag("detail_duplicate")) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Duplicate") }
                FilledTonalIconButton(onClick = onDelete, modifier = Modifier.size(52.dp).testTag("detail_delete"), colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

/** One line of the facts card: the name small and the value, with an optional link at the end. */
@Composable
private fun FactRow(label: String, value: String, tag: String, action: Pair<String, () -> Unit>? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag(tag))
        }
        if (action != null) TextButton(onClick = action.second, modifier = Modifier.heightIn(min = 48.dp).testTag("${tag}_change")) { Text(action.first) }
    }
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

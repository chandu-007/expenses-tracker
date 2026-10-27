package app.expenses.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import app.expenses.core.domain.BillEntries
import app.expenses.core.domain.parseAmount
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.BillItem
import app.expenses.core.domain.BillItems
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.formatted
import app.expenses.core.domain.toPlainString
import app.expenses.ioDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the person chose in [BillItemsDialog]: the [items] they ticked, the [amountMinor] to use (the printed total when every line is used, else the sum of the
 * ticked lines), and the photo [photoUri] the text was read from (null when the dialog was given text directly).
 */
class BillItemsChoice(
    val items: List<BillItem>, val amountMinor: Long, val photoUri: String?,
    /** True: each of [items] (and [restMinor], when above zero) is to be saved as an entry of its own; false: one entry for [amountMinor]. */
    val eachEntry: Boolean = false, val restMinor: Long = 0,
)

/**
 * "Read items from a photo" on the Add form: a button that picks or takes a picture of a bill, reads its text on this phone (the platform's own text recogniser;
 * nothing is sent anywhere), and shows the items found in [BillItemsDialog]. [amountEmpty] says whether the amount field is still empty (only then does the
 * bill's total fill it), [note] is what is already in the note field, [canAttach] whether the photo can still be kept as the entry's bill photo.
 * [onApply] gets the amount text to put in the amount field (null: leave it), the new note, and the photo to attach (null: do not).
 */
@Composable
internal fun BillItemsReader(
    amountEmpty: Boolean, note: String, canAttach: Boolean,
    /** Whether the form can save each item as its own entry (a new plain expense with an account and a category chosen); with a reason when it cannot. */
    canAddEach: Boolean = false, eachNotice: String? = null,
    /** The person chose "each item as its own entry": the items, what the bill holds beyond them, and the photo. */
    onAddEach: (items: List<BillItem>, restMinor: Long, photoUri: String?) -> Unit = { _, _, _ -> },
    onApply: (amountText: String?, note: String, photoUri: String?) -> Unit,
) {
    val platform = LocalPlatform.current
    val picker = rememberFilePickers()
    val scope = rememberCoroutineScope()
    var choosing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var read by remember { mutableStateOf<Pair<String, String>?>(null) } // the recognised text and the photo it came from

    fun readPhoto(uri: String?) {
        choosing = false
        if (uri == null || busy) return // a cancelled picker hands back null: nothing started, nothing to reset
        busy = true; problem = null
        scope.launch {
            // `busy` always comes back, whatever the reading does (an exception, or an Error such as running out of memory on a very large picture); leaving the form
            // cancels this scope with the form, so a result never opens the list of items on a form that is gone
            try {
                // recognition and reading the file are slow: off the main thread
                val result = withContext(ioDispatcher) {
                    try {
                        val bytes = platform.readBytes(uri)
                        if (!platform.looksLikeImage(bytes)) Result.failure(IllegalArgumentException("That is not a picture. Choose a photo of the bill."))
                        else Result.success(platform.recognizeText(bytes))
                    } catch (e: CancellationException) { throw e } catch (e: Throwable) { Result.failure(e) }
                }
                result.onSuccess { read = it to uri }.onFailure { problem = (it as? IllegalArgumentException)?.message ?: "Could not read that photo." }
            } finally {
                busy = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = { if (picker.canTakePhoto) choosing = !choosing else picker.openDocument(listOf("image/*")) { readPhoto(it) } }, enabled = !busy, modifier = Modifier.testTag("bill_items_read")) {
            Text(if (busy) "Reading the photo…" else "Read items from a photo")
        }
        // reading takes a second or more on a large photo: a thin moving bar says the phone is working
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth().testTag("bill_items_working"))
            Text("Reading the bill on this phone. Nothing is sent anywhere.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // The two ways to get the picture are shown in place, not in a dialog: on an iPhone a dialog is a screen of its own, and the camera or the Files picker
        // opened over it left that screen and the list of items to be shown after it fighting over what was on top (the list appeared and was gone).
        if (choosing) {
            Text("The words in the photo are read on this phone. Nothing is sent anywhere.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker.takePhoto { readPhoto(it) } }, modifier = Modifier.testTag("bill_items_camera")) { Text("Take a photo") }
                OutlinedButton(onClick = { picker.openDocument(listOf("image/*")) { readPhoto(it) } }, modifier = Modifier.testTag("bill_items_pick")) { Text("Choose a photo") }
            }
        }
        problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("bill_items_problem")) }
    }
    read?.let { (text, uri) ->
        BillItemsDialog(
            text, willSetAmount = amountEmpty, photoUri = uri, onClose = { read = null },
            canAddEach = canAddEach, eachNotice = eachNotice, onCopyText = { platform.copyText(text) },
            onApply = { choice ->
                read = null
                if (choice.eachEntry) { onAddEach(choice.items, choice.restMinor, choice.photoUri); return@BillItemsDialog }
                onApply(
                    if (amountEmpty && choice.amountMinor > 0) Money(choice.amountMinor, BaseCurrency.current).toPlainString() else null,
                    BillItems.note(choice.items, BaseCurrency.current, note),
                    if (canAttach) choice.photoUri else null,
                )
            },
        )
    }
}

/**
 * The items read from a bill's [text], each with a checkbox (all ticked to start), the total, and Apply. Used by [BillItemsReader]; the text is passed in
 * so a test can show the dialog without a photo or a recogniser (public for that reason: the tests live in another module). With nothing readable it says so plainly and only offers Close.
 */
@Composable
fun BillItemsDialog(
    text: String, willSetAmount: Boolean, photoUri: String?, onClose: () -> Unit, onApply: (BillItemsChoice) -> Unit, currency: Currency = BaseCurrency.current,
    /** Offer "each item as its own entry" besides "one entry for the whole bill"; [eachNotice] says why it is not offered, when it is not. */
    canAddEach: Boolean = false, eachNotice: String? = null,
    /** Puts the text the recogniser read on the clipboard (to send a bill that reads badly for the reader to be improved); null: no such button. Returns whether it worked. */
    onCopyText: (() -> Boolean)? = null,
) {
    var copied by remember { mutableStateOf(false) }
    val parsed = remember(text, currency) { BillItems.parse(text, currency) }
    // the lines can be corrected here (a name or a price the recogniser got wrong) before they become a note or entries
    var items by remember(parsed) { mutableStateOf(parsed.items) }
    var ticked by remember(parsed) { mutableStateOf(parsed.items.indices.toSet()) }
    var editing by remember(parsed) { mutableStateOf<Int?>(null) }
    var each by remember(parsed) { mutableStateOf(false) }
    var addRest by remember(parsed) { mutableStateOf(true) }
    if (parsed.isEmpty) {
        AlertDialog(
            onDismissRequest = onClose, title = { Text("Nothing readable") },
            text = {
                Column {
                    Text("No items or total could be read from that photo. Try again with the bill flat and well lit, or type the amount yourself.", modifier = Modifier.testTag("bill_items_none"))
                    CopyReadText(onCopyText, copied) { copied = true }
                }
            },
            confirmButton = { TextButton(onClick = onClose, modifier = Modifier.testTag("bill_items_close")) { Text("Close") } },
        )
        return
    }
    val bill = parsed.copy(items = items)
    val use = BillItems.amountFor(bill, ticked)
    val chosen = bill.items.filterIndexed { i, _ -> i in ticked }
    val rest = if (each && addRest) BillEntries.restMinor(bill, ticked) else 0L
    val restAvailable = BillEntries.restMinor(bill, ticked)
    AlertDialog(
        onDismissRequest = onClose, title = { Text("Items on the bill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bill.items.isEmpty()) Text("Only the total could be read.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("bill_items_total_only"))
                LazyColumn(Modifier.heightIn(max = 280.dp).testTag("bill_items_list")) {
                    itemsIndexed(bill.items) { i, item ->
                        if (editing == i) { ItemEditor(item, currency, i, onDone = { fixed -> if (fixed != null) items = items.toMutableList().also { it[i] = fixed }; editing = null }); return@itemsIndexed }
                        val on = i in ticked
                        Row(Modifier.fillMaxWidth().clickable { ticked = if (on) ticked - i else ticked + i }.testTag("bill_item_$i"), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp).testTag("bill_item_check_$i"))
                            Text(item.name + if (item.quantity > 1) " × ${item.quantity}" else "", maxLines = 2, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                            Text(Money(item.amountMinor, currency).formatted(), modifier = Modifier.padding(start = 8.dp))
                            TextButton(onClick = { editing = i }, modifier = Modifier.testTag("bill_item_edit_$i").semantics { contentDescription = "Edit ${item.name}" }) { Text("Edit") } // a screen reader must tell the buttons apart
                        }
                    }
                }
                bill.printedTotalMinor?.let { printed -> Text("Total on the bill: ${Money(printed, currency).formatted()}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("bill_items_printed_total")) }
                if (canAddEach && bill.items.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = !each, onClick = { each = false }, role = Role.RadioButton).testTag("bill_items_mode_one"), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !each, onClick = null, modifier = Modifier.padding(end = 8.dp))
                        Text("One entry for the whole bill", modifier = Modifier.padding(vertical = 6.dp))
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = each, onClick = { each = true }, role = Role.RadioButton).testTag("bill_items_mode_each"), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = each, onClick = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Each item as its own entry", modifier = Modifier.padding(vertical = 6.dp))
                    }
                    if (each && restAvailable > 0) Row(Modifier.fillMaxWidth().clickable { addRest = !addRest }.testTag("bill_items_rest"), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = addRest, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Also add tax and other charges, ${Money(restAvailable, currency).formatted()}, so the entries add up to the bill", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp))
                    }
                } else if (eachNotice != null && bill.items.isNotEmpty()) Text(eachNotice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("bill_items_each_notice"))
                CopyReadText(onCopyText, copied) { copied = true }
                if (each) {
                    Text("Entries to add: ${chosen.size + if (rest > 0) 1 else 0}, ${Money(chosen.sumOf { it.amountMinor } + rest, currency).formatted()} in all", style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("bill_items_total"))
                    Text(
                        "Each gets the account, date, category and tags on the form, with the item as its note. The bill's photo goes with the first. One Undo takes them all back.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("Amount to use: ${Money(use, currency).formatted()}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("bill_items_total"))
                    Text(
                        (if (willSetAmount) "It goes in the amount field. " else "The amount you already typed stays. ") + "The ticked names and prices go in the note.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(BillItemsChoice(chosen, use, photoUri, eachEntry = each, restMinor = rest)) },
                enabled = chosen.isNotEmpty() || bill.items.isEmpty(), modifier = Modifier.testTag("bill_items_apply"),
            ) { Text(if (each) "Add ${chosen.size + if (rest > 0) 1 else 0} entries" else "Use these") }
        },
        dismissButton = { TextButton(onClick = onClose, modifier = Modifier.testTag("bill_items_close")) { Text("Cancel") } },
    )
}

/** One line of the bill being corrected: its name and its price. [onDone] gets the corrected line, or null when cancelled. */
@Composable
private fun ItemEditor(item: BillItem, currency: Currency, index: Int, onDone: (BillItem?) -> Unit) {
    var name by remember(item) { mutableStateOf(item.name) }
    var amount by remember(item) { mutableStateOf(Money(item.amountMinor, currency).toPlainString()) }
    val parsed = parseAmount(amount, currency)
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Item") }, modifier = Modifier.fillMaxWidth().testTag("bill_item_name_$index"))
        OutlinedTextField(amount, { v -> if (v.all { it.isDigit() || it == '.' }) amount = v }, singleLine = true, label = { Text("Price") }, isError = parsed == null || parsed.minorUnits <= 0, modifier = Modifier.fillMaxWidth().testTag("bill_item_amount_$index"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onDone(null) }, modifier = Modifier.testTag("bill_item_cancel_$index")) { Text("Cancel") }
            TextButton(
                onClick = { onDone(item.copy(name = name.trim().ifEmpty { item.name }, amountMinor = parsed!!.minorUnits)) },
                enabled = parsed != null && parsed.minorUnits > 0, modifier = Modifier.testTag("bill_item_done_$index"),
            ) { Text("Done") }
        }
    }
}

/** "Copy the text that was read": for a bill that is read badly, the text the phone found is what is needed to improve the reader. Nothing is sent by the app. */
@Composable
private fun CopyReadText(onCopy: (() -> Boolean)?, copied: Boolean, onCopied: () -> Unit) {
    if (onCopy == null) return
    TextButton(onClick = { if (onCopy()) onCopied() }, modifier = Modifier.testTag("bill_items_copy")) { Text(if (copied) "Copied" else "Copy the text that was read") }
}

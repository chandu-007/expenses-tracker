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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
class BillItemsChoice(val items: List<BillItem>, val amountMinor: Long, val photoUri: String?)

/**
 * "Read items from a photo" on the Add form: a button that picks or takes a picture of a bill, reads its text on this phone (the platform's own text recogniser;
 * nothing is sent anywhere), and shows the items found in [BillItemsDialog]. [amountEmpty] says whether the amount field is still empty (only then does the
 * bill's total fill it), [note] is what is already in the note field, [canAttach] whether the photo can still be kept as the entry's bill photo.
 * [onApply] gets the amount text to put in the amount field (null: leave it), the new note, and the photo to attach (null: do not).
 */
@Composable
internal fun BillItemsReader(amountEmpty: Boolean, note: String, canAttach: Boolean, onApply: (amountText: String?, note: String, photoUri: String?) -> Unit) {
    val platform = LocalPlatform.current
    val picker = rememberFilePickers()
    val scope = rememberCoroutineScope()
    var choosing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var read by remember { mutableStateOf<Pair<String, String>?>(null) } // the recognised text and the photo it came from

    fun readPhoto(uri: String?) {
        choosing = false
        if (uri == null) return
        busy = true; problem = null
        scope.launch {
            // recognition and reading the file are slow: off the main thread
            val result = withContext(ioDispatcher) {
                try {
                    val bytes = platform.readBytes(uri)
                    if (!platform.looksLikeImage(bytes)) Result.failure(IllegalArgumentException("That is not a picture. Choose a photo of the bill."))
                    else Result.success(platform.recognizeText(bytes))
                } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
            }
            busy = false
            result.onSuccess { read = it to uri }.onFailure { problem = (it as? IllegalArgumentException)?.message ?: "Could not read that photo." }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = { if (picker.canTakePhoto) choosing = true else picker.openDocument(listOf("image/*")) { readPhoto(it) } }, enabled = !busy, modifier = Modifier.testTag("bill_items_read")) {
            Text(if (busy) "Reading the photo…" else "Read items from a photo")
        }
        problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("bill_items_problem")) }
    }
    if (choosing) AlertDialog(
        onDismissRequest = { choosing = false }, title = { Text("Photo of the bill") },
        text = { Text("The words in the photo are read on this phone. Nothing is sent anywhere.") },
        confirmButton = { TextButton(onClick = { picker.takePhoto { readPhoto(it) } }, modifier = Modifier.testTag("bill_items_camera")) { Text("Take a photo") } },
        dismissButton = { TextButton(onClick = { picker.openDocument(listOf("image/*")) { readPhoto(it) } }, modifier = Modifier.testTag("bill_items_pick")) { Text("Choose a photo") } },
    )
    read?.let { (text, uri) ->
        BillItemsDialog(
            text, willSetAmount = amountEmpty, photoUri = uri, onClose = { read = null },
            onApply = { choice ->
                read = null
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
fun BillItemsDialog(text: String, willSetAmount: Boolean, photoUri: String?, onClose: () -> Unit, onApply: (BillItemsChoice) -> Unit, currency: Currency = BaseCurrency.current) {
    val bill = remember(text, currency) { BillItems.parse(text, currency) }
    var ticked by remember(bill) { mutableStateOf(bill.items.indices.toSet()) }
    if (bill.isEmpty) {
        AlertDialog(
            onDismissRequest = onClose, title = { Text("Nothing readable") },
            text = { Text("No items or total could be read from that photo. Try again with the bill flat and well lit, or type the amount yourself.", modifier = Modifier.testTag("bill_items_none")) },
            confirmButton = { TextButton(onClick = onClose, modifier = Modifier.testTag("bill_items_close")) { Text("Close") } },
        )
        return
    }
    val use = BillItems.amountFor(bill, ticked)
    val chosen = bill.items.filterIndexed { i, _ -> i in ticked }
    AlertDialog(
        onDismissRequest = onClose, title = { Text("Items on the bill") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bill.items.isEmpty()) Text("Only the total could be read.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("bill_items_total_only"))
                LazyColumn(Modifier.heightIn(max = 320.dp).testTag("bill_items_list")) {
                    itemsIndexed(bill.items) { i, item ->
                        val on = i in ticked
                        Row(Modifier.fillMaxWidth().clickable { ticked = if (on) ticked - i else ticked + i }.testTag("bill_item_$i"), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp).testTag("bill_item_check_$i"))
                            Text(item.name + if (item.quantity > 1) " × ${item.quantity}" else "", maxLines = 2, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                            Text(Money(item.amountMinor, currency).formatted(), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                bill.printedTotalMinor?.let { printed -> Text("Total on the bill: ${Money(printed, currency).formatted()}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("bill_items_printed_total")) }
                Text("Amount to use: ${Money(use, currency).formatted()}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("bill_items_total"))
                Text(
                    (if (willSetAmount) "It goes in the amount field. " else "The amount you already typed stays. ") + "The ticked names and prices go in the note.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(BillItemsChoice(chosen, use, photoUri)) }, enabled = chosen.isNotEmpty() || bill.items.isEmpty(), modifier = Modifier.testTag("bill_items_apply")) { Text("Use these") }
        },
        dismissButton = { TextButton(onClick = onClose, modifier = Modifier.testTag("bill_items_close")) { Text("Cancel") } },
    )
}

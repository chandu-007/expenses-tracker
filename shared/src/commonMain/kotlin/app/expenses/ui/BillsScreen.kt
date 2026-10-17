package app.expenses.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.expenses.AccountUi
import app.expenses.BillRowUi
import app.expenses.BillsUi

private val AMBER = Color(0xFFB26A00)

/**
 * Bills found in your messages and the ones you are tracking. A bill found in a message waits here (and in a notification) until you add it or skip it;
 * adding it starts reminders; marking it paid records the payment, or only notes that it is done. Nothing is entered in your books until you say so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(
    ui: BillsUi, accounts: List<AccountUi>, onBack: () -> Unit,
    onAdd: (String) -> Unit, onSkip: (String) -> Unit, onSetDue: (String, Long) -> Unit, onUnpay: (String) -> Unit,
    onPay: (id: String, payFromId: String, cardId: String, amount: String, record: Boolean, done: (String?) -> Unit) -> Unit,
) {
    var paying by rememberSaveable { mutableStateOf<String?>(null) }
    var dating by rememberSaveable { mutableStateOf<String?>(null) }
    val all = ui.found + ui.coming
    // the bills already paid when the page opened do not celebrate again; one that becomes paid while it is open does
    val baselinePaid = remember(ui.loaded) { if (ui.loaded) ui.paid.map { it.id }.toSet() else null }
    all.firstOrNull { it.id == paying }?.let { PayDialog(it, accounts, onPay, onClose = { paying = null }) }
    dating?.let { id ->
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { dating = null },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { ms -> onSetDue(id, ms.floorDiv(86_400_000L)) }; dating = null }, modifier = Modifier.testTag("bill_date_ok")) { Text("OK") } },
            dismissButton = { TextButton(onClick = { dating = null }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Bills") }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("bills_back")) { Text("Back") } }) }) { pad ->
        if (ui.loaded && all.isEmpty() && ui.paid.isEmpty()) {
            Column(Modifier.padding(pad).padding(24.dp).fillMaxSize()) {
                EmptyArt(EmptyKind.BILLS, Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))
                Text("No bills yet.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("bills_empty"))
                Text(
                    "When a text or a payment app's notification says a bill is due (a credit card statement, electricity, water, gas, mobile, broadband, an EMI or an insurance premium), it appears here and you are told. " +
                        "Turn on Capture from texts in More if you have not.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("bills_list"), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ui.found.isNotEmpty()) {
                item(key = "found") { Section("Found in your messages", "Add a bill to be reminded before it is due.", "bills_found_title", Modifier.animateItem()) }
                items(ui.found, key = { "f" + it.id }) { BillCard(it, onAdd = onAdd, onSkip = onSkip, onPay = { paying = it }, onSetDate = { dating = it }, onUnpay = onUnpay, modifier = Modifier.animateItem()) }
            }
            if (ui.coming.isNotEmpty()) {
                item(key = "coming") { Section("Coming up", "Sorted by due date. You are reminded three days before, on the day, and if it is overdue.", "bills_coming_title", Modifier.animateItem()) }
                items(ui.coming, key = { "c" + it.id }) { BillCard(it, onAdd = onAdd, onSkip = onSkip, onPay = { paying = it }, onSetDate = { dating = it }, onUnpay = onUnpay, modifier = Modifier.animateItem()) }
            }
            if (ui.paid.isNotEmpty()) {
                item(key = "paid") { Section("Paid", null, "bills_paid_title", Modifier.animateItem()) }
                items(ui.paid, key = { "p" + it.id }) { BillCard(it, onAdd = onAdd, onSkip = onSkip, onPay = { paying = it }, onSetDate = { dating = it }, onUnpay = onUnpay, modifier = Modifier.animateItem(), flourish = baselinePaid != null && it.id !in baselinePaid) }
            }
        }
    }
}

@Composable
private fun Section(title: String, note: String?, tag: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.testTag(tag))
        if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BillCard(b: BillRowUi, onAdd: (String) -> Unit, onSkip: (String) -> Unit, onPay: (String) -> Unit, onSetDate: (String) -> Unit, onUnpay: (String) -> Unit, modifier: Modifier = Modifier, flourish: Boolean = false) {
    var showText by rememberSaveable(b.id) { mutableStateOf(false) }
    val found = b.status == "FOUND"; val paid = b.status == "PAID"
    val tint = when { b.overdue && !paid -> MaterialTheme.colorScheme.error; b.soon && !paid -> AMBER; else -> MaterialTheme.colorScheme.primary }
    Card(
        modifier.fillMaxWidth().testTag("bill_${b.id}"), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = if (paid) 0.04f else 0.08f).compositeOver(MaterialTheme.colorScheme.surface)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryAvatar(b.title, b.title, Modifier.padding(end = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, modifier = Modifier.testTag("bill_title_${b.id}"))
                    if (paid) Text(b.paidText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else Text(listOf(b.dueText, b.dueIn).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = if (b.overdue || b.soon) tint else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (b.overdue || b.soon) FontWeight.SemiBold else null, modifier = Modifier.testTag("bill_due_${b.id}"))
                }
                Box(Modifier.padding(start = 8.dp)) {
                    Text(b.amountText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("bill_amount_${b.id}"))
                    if (paid && flourish) PaidCheck(b.id, Modifier.align(Alignment.CenterStart).offset(x = (-22).dp))
                }
            }
            if (!paid) {
                b.minDueText?.let { Text("Minimum due $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("bill_min_${b.id}")) }
                if (b.autoDebit) Text("This is taken from your account automatically. Add it to keep the money there and to see it here until the debit arrives.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)).padding(10.dp).testTag("bill_auto_${b.id}"))
            }
            when {
                found -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { onAdd(b.id) }, modifier = Modifier.weight(1f).testTag("bill_add_${b.id}")) { Text("Add to my bills") }
                    OutlinedButton(onClick = { onSkip(b.id) }, modifier = Modifier.testTag("bill_skip_${b.id}")) { Text("Skip") }
                }
                paid -> TextButton(onClick = { onUnpay(b.id) }, modifier = Modifier.testTag("bill_unpay_${b.id}")) { Text("Not paid after all") }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { onPay(b.id) }, modifier = Modifier.weight(1f).testTag("bill_pay_${b.id}")) { Text("Mark paid") }
                    OutlinedButton(onClick = { onSkip(b.id) }, modifier = Modifier.testTag("bill_remove_${b.id}")) { Text("Remove") }
                }
            }
            if (!paid) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (b.dueEpochDay == null || b.overdue) TextButton(onClick = { onSetDate(b.id) }, modifier = Modifier.testTag("bill_setdate_${b.id}")) { Text(if (b.dueEpochDay == null) "Set the due date" else "Change the due date") }
                if (found && b.body.isNotEmpty()) TextButton(onClick = { showText = !showText }, modifier = Modifier.testTag("bill_text_toggle_${b.id}")) { Text(if (showText) "Hide message" else "Show message") }
            }
            if (showText) Text(b.body, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(12.dp).testTag("bill_text_${b.id}"))
        }
    }
}

/** Marking a bill paid: what was paid, from which account, and whether to enter it in your expenses. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PayDialog(
    b: BillRowUi, accounts: List<AccountUi>,
    onPay: (String, String, String, String, Boolean, (String?) -> Unit) -> Unit, onClose: () -> Unit,
) {
    var amount by rememberSaveable(b.id) { mutableStateOf(b.amountPlain) }
    var from by rememberSaveable(b.id) { mutableStateOf(b.payFromId) }
    var card by rememberSaveable(b.id) { mutableStateOf(b.cardAccountId) }
    var record by rememberSaveable(b.id) { mutableStateOf(true) }
    var problem by remember(b.id) { mutableStateOf<String?>(null) }
    val cards = accounts.filter { it.type == "CREDIT_CARD" }
    val isCard = b.kind == "CREDIT_CARD"
    val feel = rememberFeel()
    AlertDialog(
        onDismissRequest = onClose, title = { Text("Mark ${b.title} paid") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = record, onCheckedChange = { record = it }, modifier = Modifier.testTag("bill_pay_record"))
                    Text("Enter this payment in my expenses")
                }
                if (record) {
                    OutlinedTextField(amount, { v -> if (v.all { it.isDigit() || it == '.' }) amount = v }, label = { Text("Amount paid") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("bill_pay_amount"))
                    if (b.minDuePlain != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = amount == b.amountPlain, onClick = { amount = b.amountPlain }, label = { Text("Total ${b.amountText}") }, modifier = Modifier.testTag("bill_pay_total"))
                        FilterChip(selected = amount == b.minDuePlain, onClick = { amount = b.minDuePlain }, label = { Text("Minimum ${b.minDueText}") }, modifier = Modifier.testTag("bill_pay_min"))
                    }
                    Text("Paid from", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { accounts.filter { it.type != "CREDIT_CARD" || !isCard }.forEach { a -> FilterChip(selected = a.id == from, onClick = { from = a.id }, label = { Text(a.name) }, modifier = Modifier.testTag("bill_pay_from_${a.name}")) } }
                    if (isCard && cards.isNotEmpty()) {
                        Text("Which card", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { cards.forEach { a -> FilterChip(selected = a.id == card, onClick = { card = a.id }, label = { Text(a.name) }, modifier = Modifier.testTag("bill_pay_card_${a.name}")) } }
                        Text("It is recorded as a transfer to the card, not as spending.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (!isCard) Text("It is recorded as an expense in Bills.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else Text("Use this when you already entered the payment, or will add it from the bank's text.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("bill_pay_problem")) }
            }
        },
        confirmButton = { TextButton(onClick = { onPay(b.id, from, if (isCard) card else "", amount, record) { p -> if (p == null) { feel(Feel.PAID); onClose() } else problem = p } }, modifier = Modifier.testTag("bill_pay_ok")) { Text(if (record) "Record and mark paid" else "Mark paid") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

/** A small check that draws itself beside a bill's amount the moment it becomes paid, then leaves (about 600 ms). Not shown under Reduce motion. */
@Composable
private fun PaidCheck(id: String, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    var visible by remember(id) { mutableStateOf(!reduce) }
    if (!visible) return
    val progress = remember(id) { Animatable(0f) }
    LaunchedEffect(id) {
        progress.animateTo(1f, tween(400, easing = Motion.EaseOut))
        delay(200)
        visible = false
    }
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(18.dp).testTag("bill_paid_check_$id")) {
        val path = Path().apply {
            moveTo(size.width * 0.15f, size.height * 0.55f)
            lineTo(size.width * 0.4f, size.height * 0.8f)
            lineTo(size.width * 0.85f, size.height * 0.25f)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val part = Path()
        measure.getSegment(0f, measure.length * progress.value, part, true)
        drawPath(part, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

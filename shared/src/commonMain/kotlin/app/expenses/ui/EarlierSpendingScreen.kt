package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.expenses.AccountUi
import app.expenses.CategoryUi
import app.expenses.core.domain.EarlierSpending
import app.expenses.epochDay
import app.expenses.formatDate
import app.expenses.localDateOfEpochDay
import app.expenses.todayLocal

/** How many category chips the fast form shows before "More". */
private const val EARLIER_CHIPS = 8

/** How many of the entries added in this visit are listed. */
private const val EARLIER_LISTED = 5

/**
 * "Bring in earlier spending": for a person who started the app after already spending, so balances and cards look wrong. One calm page with the three ways to catch up and the order to try them in,
 * and no new importer: the first opens the statement importer that already exists (More, Your data), the second is a fast form that keeps its date and category between entries so the next one is only
 * an amount, the third opens Accounts, where the opening balance and "Fix this balance" are. Texts cannot be searched back in time, and the page says why.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EarlierSpendingScreen(
    accounts: List<AccountUi>, categories: List<CategoryUi>,
    onImportStatement: () -> Unit, onOpenAccounts: () -> Unit,
    onAdd: (amount: String, categoryId: String, accountId: String, epochDay: Long, note: String, done: (String?) -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { AreaTopBar("Bring in earlier spending", Area.MONEY, Icons.Outlined.History, onBack, "earlier_spending_back") }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("earlier_spending"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Spent money before you started using the app?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }.testTag("earlier_spending_title"))
            Text(
                "Then some balances and card amounts may look wrong. Here are three ways to catch up, best first. Do as many as you like, in any order, and stop whenever you want.",
                style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("earlier_spending_intro"),
            )
            Text("Best order: 1, then 2 for what the statement missed, then 3 so the balances match your bank app.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("earlier_spending_order"))

            Way("1. Import a bank or card statement", "earlier_spending_way_statement") {
                Text("The most exact way. Download a statement from your bank or card app as a CSV, Excel or PDF file (or take a photo of one), choose it here, and check how it was read before anything is saved. Importing the same file twice adds nothing new.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onImportStatement, modifier = Modifier.heightIn(min = 48.dp).testTag("earlier_spending_import")) { Text("Choose a statement") }
            }
            Way("2. Add earlier spending by hand", "earlier_spending_way_manual") { QuickAdd(accounts, categories, onAdd) }
            Way("3. Set your balances", "earlier_spending_way_balances") {
                Text("If an account already held money, or a card already owed some, when you started, give it that opening balance. Or use Fix this balance on an account to match what your bank app says today: it moves the opening balance and adds no entry.", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onOpenAccounts, modifier = Modifier.heightIn(min = 48.dp).testTag("earlier_spending_balances")) { Text("Open Accounts") }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)).padding(14.dp).semantics(mergeDescendants = true) {}.testTag("earlier_spending_no_scan"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("What the app cannot do: read your old texts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("The app never looks through the messages already on your phone. It reads a bank text only as it arrives, from the time you set capture up. That keeps your inbox private; it also means older spending has to come from a statement or from you.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Way(title: String, tag: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(14.dp).testTag(tag), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        content()
    }
}

/**
 * The fast form: the date starts on the last day of last month and stays where it was put, the category and the account stay too, so each following entry is an amount and one tap. What was added in
 * this visit is listed under it, so it is plain that the entry went in.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuickAdd(accounts: List<AccountUi>, categories: List<CategoryUi>, onAdd: (String, String, String, Long, String, (String?) -> Unit) -> Unit) {
    var day by rememberSaveable { mutableStateOf(EarlierSpending.lastMonthEnd(todayLocal()).epochDay()) }
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var account by rememberSaveable { mutableStateOf("") }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingCategory by rememberSaveable { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val added = remember { mutableStateListOf<String>() }
    var addedCount by rememberSaveable { mutableStateOf(0) }
    val offered = offeredCategories(categories, income = false)
    val chips = (offered.take(EARLIER_CHIPS) + offered.filter { it.id == category }).distinctBy { it.id }
    val accountId = account.ifEmpty { accounts.firstOrNull()?.id.orEmpty() }
    val date = localDateOfEpochDay(day)
    fun save() {
        if (busy) return
        busy = true; problem = null
        val shown = amount.trim()
        onAdd(amount, category, accountId, day, note) { p ->
            busy = false
            if (p == null) {
                val name = offered.firstOrNull { it.id == category }?.name
                added.add(0, "$shown on ${formatDate(date, "d MMM yyyy")}" + (name?.let { " · $it" } ?: ""))
                addedCount++
                amount = ""; note = ""
            } else problem = p
        }
    }

    Text("Each entry is an expense on the day you choose. The date, category and account stay as they are, so the next one is only an amount.", style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("earlier_spending_date")) { Text("Date: ${formatDate(date, "EEE, d MMM yyyy")}") }
    OutlinedTextField(
        amount, { amount = it; problem = null }, label = { Text("Amount") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { if (amount.isNotBlank()) save() }),
        modifier = Modifier.fillMaxWidth().testTag("earlier_spending_amount"),
    )
    if (offered.isNotEmpty()) {
        Text("Category", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { c -> FilterChip(selected = c.id == category, onClick = { category = if (category == c.id) "" else c.id }, label = { Text(c.name) }, modifier = Modifier.heightIn(min = 48.dp).testTag("earlier_spending_category_${c.id}")) }
            if (offered.size > chips.size) AssistChip(onClick = { pickingCategory = true }, label = { Text("More (${offered.size - chips.size})") }, modifier = Modifier.heightIn(min = 48.dp).testTag("earlier_spending_more_categories"))
        }
    }
    if (accounts.size > 1) {
        Text("Account", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            accounts.forEach { a -> FilterChip(selected = a.id == accountId, onClick = { account = a.id }, label = { Text(a.name) }, modifier = Modifier.heightIn(min = 48.dp).testTag("earlier_spending_account_${a.id}")) }
        }
    }
    OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("earlier_spending_note"))
    problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("earlier_spending_problem")) }
    Button(onClick = { save() }, enabled = amount.isNotBlank() && accountId.isNotEmpty() && !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("earlier_spending_add")) { Text("Add this expense") }
    if (accounts.isEmpty()) Text("Add an account first (step 3): an expense needs somewhere to come from.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("earlier_spending_no_account"))
    if (addedCount > 0) Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("earlier_spending_added"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(if (addedCount == 1) "1 expense added in this visit" else "$addedCount expenses added in this visit", style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("earlier_spending_added_count"))
        added.take(EARLIER_LISTED).forEachIndexed { i, line -> Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("earlier_spending_added_$i")) }
    }

    if (pickingDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = utcMidnightMillis(date))
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = { TextButton(onClick = { picker.selectedDateMillis?.let { day = pickerDate(it).epochDay() }; pickingDate = false }, modifier = Modifier.testTag("earlier_spending_date_ok")) { Text("OK") } },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = picker) }
    }
    if (pickingCategory) CategoryPickerDialog(offered, category, onPick = { id -> category = id; pickingCategory = false }, onClose = { pickingCategory = false })
}

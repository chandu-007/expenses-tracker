package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.expenses.AccountUi
import app.expenses.CategoryUi
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.SmartEntry
import app.expenses.core.domain.formatted
import app.expenses.dateOfMillis
import app.expenses.systemZone
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus

/** What the smart line's preview puts in the form when tapped: the amount as plain digits, the note, and the category, account and day it found (null: leave the form's). */
internal class SmartFill(val amountText: String, val note: String, val categoryId: String?, val accountId: String?, val daysAgo: Int?)

/** [daysAgo] days before today at the time of day [formMillis] has: the date a smart line's "yesterday" puts on the form. */
internal fun smartDateMillis(daysAgo: Int, formMillis: Long, zone: TimeZone): Long =
    millisOf(dateOfMillis(app.expenses.nowMillis(), zone).minus(daysAgo, DateTimeUnit.DAY), localTimeOf(formMillis, zone), zone)

/**
 * N-01, the smart line on the Add form ("chai 20", "auto ke 80 diye", "uber 245.50 yesterday", "rent 18000 hdfc"): read as it is typed ([SmartEntry], pure and quick), with a
 * preview chip ("₹20.00 · Chai · Food · Today") that fills the form when tapped. It NEVER saves: the form's own button does, after the person has seen the form filled in.
 * The category is what the person usually puts this payee under (their own history, asked a moment after the typing stops, as the form's "Usually Food for X") or else a
 * built-in word ("chai" is Food). Amounts in the chip are drawn by [Text], so Hide amounts masks them. People who ignore the line use the form exactly as before.
 */
@Composable
internal fun SmartEntryField(
    categories: List<CategoryUi>,
    accounts: List<AccountUi>,
    currency: Currency,
    formDateMillis: Long,
    usualCategory: (suspend (note: String) -> UsualCategoryUi?)?,
    onFill: (SmartFill) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val reading = remember(text, accounts, currency) {
        if (text.isBlank()) null else SmartEntry.read(text, currency.minorUnitDigits, accounts.map { SmartEntry.AccountName(it.id, it.name) })
    }
    // the person's own history first, asked a moment after the note stops changing (an indexed lookup, off the main thread in the view model)
    var usual by remember { mutableStateOf<String?>(null) }
    val note = reading?.note.orEmpty()
    if (usualCategory != null) LaunchedEffect(note) {
        usual = null
        if (note.length >= 3) { kotlinx.coroutines.delay(350); usual = usualCategory(note)?.categoryId }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val zone = systemZone()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = text, onValueChange = { if (it.length <= SmartEntry.MAX_LENGTH + 40) text = it },
            label = { Text("Or type it: chai 20, auto 80 yesterday") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth().testTag("smart_entry"),
        )
        val r = reading ?: return@Column
        val minor = r.amountMinor
        if (r.usable && minor != null) {
            val offered = offeredCategories(categories, income = false)
            val categoryId = SmartEntry.pickCategory(usual, r.categoryNames, offered.map { it.id to it.name })
            val categoryName = categories.firstOrNull { it.id == categoryId }?.let { displayName(categories, it) }
            val accountName = accounts.firstOrNull { it.id == r.accountId }?.name
            val day = shortDateLabel(r.daysAgo?.let { smartDateMillis(it, formDateMillis, zone) } ?: formDateMillis, zone)
            val label = listOfNotNull(Money(minor, currency).formatted(), r.note.ifEmpty { null }, categoryName, day, accountName).joinToString(" · ")
            val fill: () -> Unit = {
                onFill(SmartFill(app.expenses.core.domain.AmountMath.plain(minor, currency.minorUnitDigits), r.note, categoryId, r.accountId, r.daysAgo))
                text = ""
                keyboard?.hide()
            }
            AssistChip(
                onClick = fill, label = { Text(label, maxLines = 2) },
                modifier = Modifier.testTag("smart_preview").semantics { onClick("Fill the form") { fill(); true } },
            )
        } else {
            val hint = when (r.problem) {
                SmartEntry.Problem.NO_AMOUNT -> "Add an amount, like chai 20"
                SmartEntry.Problem.TWO_AMOUNTS -> "Two numbers: mark the amount, like ₹40"
                SmartEntry.Problem.BAD_AMOUNT -> "That amount cannot be read safely. Type it like 1250 or 1,250.50"
                SmartEntry.Problem.TOO_LONG -> "Keep it short: what, and how much"
                SmartEntry.Problem.EMPTY, null -> null
            }
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp).testTag("smart_hint"))
        }
        if (r.dateUnclear) Text(
            "That day can mean more than one date (kal is yesterday or tomorrow): set it with the date button",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp).testTag("smart_date_hint"),
        )
    }
}

package app.expenses.ui

import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.symbolOrCode
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.expenses.BudgetLineUi
import app.expenses.BudgetUi
import app.expenses.core.domain.BudgetPeriod
import app.expenses.core.domain.BudgetState
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.parseAmount
import kotlinx.datetime.DayOfWeek

@Composable
private fun stateColor(state: BudgetState): Color = when (state) {
    BudgetState.OK -> MaterialTheme.colorScheme.primary
    BudgetState.NEAR -> Color(0xFFE08A00)
    BudgetState.OVER -> MaterialTheme.colorScheme.error
}

/** A bar for how much of a limit is used. The text beside it carries the meaning too, so colour is never the only signal. */
@Composable
fun BudgetBar(line: BudgetLineUi, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { (line.percent / 100f).coerceIn(0f, 1f) },
        color = stateColor(line.state),
        modifier = modifier.fillMaxWidth(),
    )
}

/** The budget as a card under the spending total. Tapping it opens the budget screen. */
@Composable
fun BudgetSummary(budget: BudgetUi, onOpen: () -> Unit) {
    val overall = budget.overall
    val tint = if (overall.limit == null) MaterialTheme.colorScheme.primary else stateColor(overall.state)
    androidx.compose.material3.Card(
        onClick = onOpen,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.10f).compositeOver(MaterialTheme.colorScheme.surface)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag("budget_summary"),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (overall.limit == null) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Set a budget for ${budget.periodName}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).testTag("budget_set_prompt"))
                    Text("Set", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 16.dp, vertical = 8.dp))
                }
                Text("Pick a limit and see what is left, and what you can spend a day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    overall.note + " of " + overall.limit,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (overall.state == BudgetState.OVER) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("budget_overall_note"),
                )
                AnimatedBudgetBar(overall, Modifier.height(10.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(50)))
                if (budget.perDay != null && !budget.billsUseItAll) {
                    Text("about ${budget.perDay} a day", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("budget_per_day"))
                } else if (budget.billsUseItAll) {
                    Text("bills use it all", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("budget_bills_use_all"))
                }
                if (budget.categoriesNeedingAttention > 0) {
                    Text(
                        if (budget.categoriesNeedingAttention == 1) "1 category budget is near or over its limit" else "${budget.categoriesNeedingAttention} category budgets are near or over their limit",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val WEEKDAYS = listOf(
    DayOfWeek.MONDAY to "Mon", DayOfWeek.TUESDAY to "Tue", DayOfWeek.WEDNESDAY to "Wed", DayOfWeek.THURSDAY to "Thu",
    DayOfWeek.FRIDAY to "Fri", DayOfWeek.SATURDAY to "Sat", DayOfWeek.SUNDAY to "Sun",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    budget: BudgetUi,
    onBack: () -> Unit,
    onSetLimit: (categoryId: String?, amount: Money) -> Unit,
    onClearLimit: (categoryId: String?) -> Unit,
    onSetPeriod: (BudgetPeriod) -> Unit,
    onShiftStartDay: (Int) -> Unit,
    onShiftCustomDays: (Int) -> Unit = {},
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var editingOverall by rememberSaveable { mutableStateOf(false) }
    val target = if (editingOverall) budget.overall else budget.categories.firstOrNull { it.categoryId == editing }
    if (target != null) {
        LimitDialog(
            line = target,
            periodName = budget.periodName,
            onSave = { amount -> onSetLimit(target.categoryId, amount); editing = null; editingOverall = false },
            onRemove = { onClearLimit(target.categoryId); editing = null; editingOverall = false },
            onDismiss = { editing = null; editingOverall = false },
        )
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Budget") }, navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag("budget_back")) { Text("Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Period", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = budget.period is BudgetPeriod.Monthly, onClick = { if (budget.period !is BudgetPeriod.Monthly) onSetPeriod(BudgetPeriod.Monthly(1)) },
                            label = { Text("Monthly") }, modifier = Modifier.testTag("period_monthly"),
                        )
                        FilterChip(
                            selected = budget.period is BudgetPeriod.Weekly, onClick = { if (budget.period !is BudgetPeriod.Weekly) onSetPeriod(BudgetPeriod.Weekly(DayOfWeek.MONDAY)) },
                            label = { Text("Weekly") }, modifier = Modifier.testTag("period_weekly"),
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = budget.period is BudgetPeriod.BiWeekly,
                            onClick = { if (budget.period !is BudgetPeriod.BiWeekly) onSetPeriod(BudgetPeriod.BiWeekly(FORTNIGHT_FROM_THIS_MONDAY())) },
                            label = { Text("Every 2 weeks") }, modifier = Modifier.testTag("period_biweekly"),
                        )
                        FilterChip(
                            selected = budget.period is BudgetPeriod.SemiMonthly, onClick = { if (budget.period !is BudgetPeriod.SemiMonthly) onSetPeriod(BudgetPeriod.SemiMonthly) },
                            label = { Text("Twice a month") }, modifier = Modifier.testTag("period_semimonthly"),
                        )
                        FilterChip(
                            selected = budget.period is BudgetPeriod.EveryDays, onClick = { if (budget.period !is BudgetPeriod.EveryDays) onSetPeriod(BudgetPeriod.EveryDays(10, TODAY())) },
                            label = { Text("Custom length") }, modifier = Modifier.testTag("period_custom"),
                        )
                        FilterChip(
                            selected = budget.period is BudgetPeriod.Yearly, onClick = { if (budget.period !is BudgetPeriod.Yearly) onSetPeriod(BudgetPeriod.Yearly()) },
                            label = { Text("Financial year") }, modifier = Modifier.testTag("period_yearly"),
                        )
                    }
                    when (val p = budget.period) {
                        is BudgetPeriod.Monthly -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Starts on day ", style = MaterialTheme.typography.bodyMedium)
                            TextButton(
                                onClick = { onShiftStartDay(-1) }, enabled = p.startDay > 1,
                                modifier = Modifier.testTag("start_day_down").semantics { contentDescription = "Start one day earlier" },
                            ) { Text("−") }
                            Text(p.startDay.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("start_day"))
                            TextButton(
                                onClick = { onShiftStartDay(1) }, enabled = p.startDay < BudgetPeriod.Monthly.MAX_START_DAY,
                                modifier = Modifier.testTag("start_day_up").semantics { contentDescription = "Start one day later" },
                            ) { Text("+") }
                        }
                        is BudgetPeriod.EveryDays -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Every ", style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { onShiftCustomDays(-1) }, enabled = p.days > 1, modifier = Modifier.testTag("custom_days_down").semantics { contentDescription = "One day shorter" }) { Text("−") }
                            Text(p.days.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("custom_days"))
                            TextButton(onClick = { onShiftCustomDays(1) }, enabled = p.days < BudgetPeriod.EveryDays.MAX_DAYS, modifier = Modifier.testTag("custom_days_up").semantics { contentDescription = "One day longer" }) { Text("+") }
                            Text(" days, counted from the day you chose this", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        is BudgetPeriod.BiWeekly -> Text("Two-week periods, counted from the week you chose this.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        BudgetPeriod.SemiMonthly -> Text("The 1st to the 15th, and the 16th to the end of the month.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        is BudgetPeriod.Yearly -> Text(if (p.startMonth == 4) "April to March, the Indian financial year." else "A year from month ${p.startMonth}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        is BudgetPeriod.Weekly -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WEEKDAYS.forEach { (day, label) ->
                                FilterChip(selected = p.startsOn == day, onClick = { onSetPeriod(BudgetPeriod.Weekly(day)) }, label = { Text(label) }, modifier = Modifier.testTag("weekday_$label"))
                            }
                        }
                    }
                    Text("Current period: ${budget.range}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("period_range"))
                }
                HorizontalDivider()
            }
            item {
                BudgetRow(budget.overall, headline = true, onClick = { editingOverall = true })
                if (budget.perDay != null) {
                    val days = "${budget.daysLeft} ${if (budget.daysLeft == 1) "day" else "days"}"
                    Text(
                        when {
                            budget.billsUseItAll -> "Bills still to come (${budget.upcoming}) use up what is left of your limit."
                            budget.upcoming != null -> "After ${budget.upcoming} of repeating bills still to come, you can spend about ${budget.perDay} a day for the $days left."
                            else -> "You can spend about ${budget.perDay} a day for the $days left. Repeating expenses without a fixed amount are not counted."
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp).testTag("safe_to_spend"),
                    )
                }
                HorizontalDivider()
                Text("By category", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
            }
            items(budget.categories, key = { it.categoryId ?: "" }) { line ->
                BudgetRow(line, headline = false, onClick = { editing = line.categoryId })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun BudgetRow(line: BudgetLineUi, headline: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp).testTag("budget_row_${line.categoryId ?: "overall"}"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(line.name, style = if (headline) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(line.limit ?: "No limit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (line.limit != null) {
            BudgetBar(line)
            Row(Modifier.fillMaxWidth()) {
                Text("${line.spent} spent", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(line.note, style = MaterialTheme.typography.bodySmall, color = if (line.state == BudgetState.OVER) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A short dialog, so it stays above the keyboard: one amount field and the buttons. */
@Composable
private fun LimitDialog(line: BudgetLineUi, periodName: String, onSave: (Money) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    // The current limit starts fully selected, so typing a new amount replaces it instead of being inserted into it.
    var field by rememberSaveable(line.categoryId, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(line.limitPlain, TextRange(0, line.limitPlain.length)))
    }
    val text = field.text
    val amount = parseAmount(text, BaseCurrency.current)?.takeIf { it.minorUnits > 0 }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Limit for ${line.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = field, onValueChange = { v -> if (v.text.all { it.isDigit() || it == '.' }) field = v },
                    label = { Text("Amount (${BaseCurrency.current.symbolOrCode()}) $periodName") }, singleLine = true,
                    isError = text.isNotEmpty() && amount == null,
                    supportingText = { if (text.isNotEmpty() && amount == null) Text("Enter an amount above zero, with at most 2 decimals") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { amount?.let(onSave) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("limit_amount"),
                )
                if (line.limit != null) TextButton(onClick = onRemove, modifier = Modifier.testTag("limit_remove")) { Text("Remove limit") }
            }
        },
        confirmButton = { TextButton(onClick = { amount?.let(onSave) }, enabled = amount != null, modifier = Modifier.testTag("limit_save")) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The Monday of this week: the fortnights are counted from it. */
private fun FORTNIGHT_FROM_THIS_MONDAY(): kotlinx.datetime.LocalDate {
    val today = java.time.LocalDate.now()
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    return kotlinx.datetime.LocalDate(monday.year, monday.monthValue, monday.dayOfMonth)
}

private fun TODAY(): kotlinx.datetime.LocalDate = java.time.LocalDate.now().let { kotlinx.datetime.LocalDate(it.year, it.monthValue, it.dayOfMonth) }

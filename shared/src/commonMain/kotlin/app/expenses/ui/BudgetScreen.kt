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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import app.expenses.core.domain.wholeAmounts
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
import androidx.compose.foundation.layout.heightIn
import app.expenses.core.domain.PaceLevel
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.outlined.Edit

@Composable
private fun stateColor(state: BudgetState): Color = when (state) {
    BudgetState.OK -> MaterialTheme.colorScheme.primary
    BudgetState.NEAR -> Signal.watch
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

/**
 * The budget on Home. With a limit it is a TILE (Option B: it sits beside the safe-to-spend tile, see [HomeMoneyTiles]): what is left in whole units over a small "of the limit", the bar, and what that
 * allows a day, labelled "from your budget" (the safe-to-spend tile's a-day figure is a different thing: "from your balances"). Without a limit it is the old "Set a budget" prompt. Tapping it opens the
 * budget screen. The figures are drawn in whole units; the node with the tag `budget_overall_note` says the exact sentence ("₹4,000.00 left of ₹5,000.00") to a screen reader and to the tests. While Hide
 * amounts is on every figure is masked and the bar, whose length would show the figure, is not drawn.
 */
@Composable
fun BudgetSummary(budget: BudgetUi, onOpen: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), spokenFor: app.expenses.SpokenForUi? = null) {
    val overall = budget.overall
    val tint = if (overall.limit == null) MaterialTheme.colorScheme.primary else stateColor(overall.state)
    val hide = LocalHideAmounts.current
    androidx.compose.material3.Card(
        onClick = onOpen,
        shape = Radius.M,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.10f).compositeOver(MaterialTheme.colorScheme.surface)),
        modifier = modifier.testTag("budget_summary"),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (overall.limit == null) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Set a budget for ${budget.periodName}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).testTag("budget_set_prompt"))
                    Text("Set", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 16.dp, vertical = 8.dp))
                }
                Text("Pick a limit and see what is left, and what you can spend a day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val exact = privately(overall.note + " of " + overall.limit)
                // one node says the exact sentence; what is drawn is the whole-unit figure over "of the limit" (both lines masked by Text)
                Column(Modifier.testTag("budget_overall_note").clearAndSetSemantics { text = androidx.compose.ui.text.AnnotatedString(exact) }) {
                    // the digits of what is left roll when it changes (UI S7)
                    OdometerText(
                        overall.note.wholeAmounts(), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = if (overall.state == BudgetState.OVER) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        memoryKey = "home_budget_left",
                    )
                    Text("of " + overall.limit.wholeAmounts(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!hide) AnimatedBudgetBar(overall, Modifier.height(8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(50)), elapsed = budget.elapsed, todayLabel = true)
                // UI S5: where the spending stands against "today" in words (the figure a day moved to the Home card's top: one "a day" figure, not two)
                Text(paceWord(overall), style = MaterialTheme.typography.bodySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = paceColor(overall.pace), modifier = Modifier.testTag("budget_pace_word"))
                if (budget.billsUseItAll) Text("bills use it all", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("budget_bills_use_all"))
                // N-18: how much of what is left the bills still due before the period ends will take (a tap lists them)
                spokenFor?.let { SpokenForLine(it, "spoken_for_home") }
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

/**
 * N-18, "already spoken for": "₹2,100 of what's left is already spoken for by 3 bills due", drawn in whole units (the node says the exact amounts), a tap lists the bills.
 * Hide amounts masks every figure ([Text] and [privately]); the line has no bar or size that would show one.
 */
@Composable
fun SpokenForLine(ui: app.expenses.SpokenForUi, tag: String, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val exact = privately(ui.line)
    Text(
        ui.line.wholeAmounts(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.clip(Radius.XS).clickable(onClickLabel = "Show the bills") { open = true }.heightIn(min = 48.dp)
            .wrapContentHeight(Alignment.CenterVertically).testTag(tag).semantics(mergeDescendants = true) { } // its own stop for a screen reader inside the tappable budget tile
            .clearAndSetSemantics {
                text = androidx.compose.ui.text.AnnotatedString(exact)
                role = androidx.compose.ui.semantics.Role.Button
                onClick("Show the bills") { open = true; true }
            },
    )
    if (open) SpokenForDialog(ui) { open = false }
}

/** The bills behind the line: name, due day and amount, each one stop for a screen reader; name over figures at large text. */
@Composable
private fun SpokenForDialog(ui: app.expenses.SpokenForUi, onClose: () -> Unit) {
    val large = isLargeText()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Already spoken for") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("spoken_for_list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(ui.line, style = MaterialTheme.typography.bodyMedium)
                ui.items.forEachIndexed { i, item ->
                    val name: @Composable (Modifier) -> Unit = { m ->
                        Column(m) {
                            Text(item.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                            Text("Due ${item.due}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val amount: @Composable () -> Unit = { Text(item.amount, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
                    val row = Modifier.fillMaxWidth().testTag("spoken_for_row_$i").semantics(mergeDescendants = true) { }
                    if (large) Column(row) { name(Modifier); amount() }
                    else Row(row, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { name(Modifier.weight(1f)); amount() }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onClose, modifier = Modifier.testTag("spoken_for_close")) { Text("Close") } },
    )
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
    onRollover: (Boolean) -> Unit = {},
    /** N-18: the bills still due this period against what is left (null: none due, or not worked out yet). */
    spokenFor: app.expenses.SpokenForUi? = null,
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var editingOverall by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var showNoLimit by rememberSaveable { mutableStateOf(false) }
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
    if (settings) BudgetSettingsDialog(budget, onSetPeriod, onShiftStartDay, onShiftCustomDays, onRollover) { settings = false }
    val withLimit = budget.categories.filter { it.limit != null }
    val withoutLimit = budget.categories.filter { it.limit == null }
    Scaffold(topBar = {
        ScreenHeader("Budget", onBack, "budget_back", actions = {
            androidx.compose.material3.IconButton(onClick = { settings = true }, modifier = Modifier.testTag("budget_settings")) {
                androidx.compose.material3.Icon(Icons.Outlined.Edit, contentDescription = "Budget settings: period, carry over, start day")
            }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "tip") { TipSlot("budget") }
            item(key = "ring") { BudgetRingCard(budget, spokenFor, onClick = { editingOverall = true }) }
            item(key = "limits_title") {
                Text("LIMITS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() })
                if (withLimit.isEmpty()) Text(
                    if (budget.overall.limit == null) "Set a limit for everything above, or for one category below." else "No category has a limit yet. Pick one below to give it a limit of its own.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).testTag("budget_no_limits_hint"),
                )
            }
            items(withLimit, key = { "l" + (it.categoryId ?: "") }) { line ->
                LimitRow(line, budget.elapsed, onClick = { editing = line.categoryId })
                HorizontalDivider(Modifier.padding(start = 68.dp))
            }
            if (withoutLimit.isNotEmpty()) item(key = "no_limit_toggle") {
                val n = withoutLimit.size
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).clip(Radius.M).clickable(onClickLabel = if (showNoLimit) "Hide them" else "Show them") { showNoLimit = !showNoLimit }
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(horizontal = 14.dp, vertical = 12.dp).testTag("budget_no_limit_toggle"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (showNoLimit) "Categories without a limit" else if (n == 1) "1 more category has no limit" else "$n more categories have no limit", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(if (showNoLimit) "Hide" else "Show", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (showNoLimit) items(withoutLimit, key = { "n" + (it.categoryId ?: "") }) { line ->
                Row(Modifier.fillMaxWidth().clickable { editing = line.categoryId }.padding(horizontal = 16.dp, vertical = 10.dp).testTag("budget_row_${line.categoryId ?: "overall"}"), verticalAlignment = Alignment.CenterVertically) {
                    CategoryAvatar(line.name, line.name, Modifier.padding(end = 12.dp), size = 32.dp)
                    Text(line.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("No limit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(Modifier.padding(start = 60.dp))
            }
        }
    }
}

/**
 * The top of the Budget screen (UI S5): a ring of how much of the limit is used with a tick where "today" is, the amount left in its middle, the state in words and the one sentence. The ring is drawn
 * in the draw phase from one animated value; while Hide amounts is on it is not drawn (its sweep would show the figure). Without an overall limit it is a short invitation to set one. Tapping it
 * opens the limit of everything (`budget_row_overall`).
 */
@Composable
private fun BudgetRingCard(budget: BudgetUi, spokenFor: app.expenses.SpokenForUi?, onClick: () -> Unit) {
    val overall = budget.overall
    val hide = LocalHideAmounts.current
    val stacked = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().clip(Radius.L).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .clickable(onClickLabel = "Change the limit", onClick = onClick).padding(16.dp).testTag("budget_row_overall"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (overall.limit == null) {
            Text("No limit for everything yet", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("budget_ring_empty"))
            Text("Pick one amount for ${budget.periodName} and see what is left, and what you can spend a day.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Set a limit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            return@Column
        }
        val reduce = LocalReduceMotion.current
        val fill by androidx.compose.animation.core.animateFloatAsState((overall.percent / 100f).coerceIn(0f, 1f), Motion.tweenOrSnap(reduce, Motion.STANDARD), label = "budget_ring")
        val color by androidx.compose.animation.animateColorAsState(paceColor(overall.pace), Motion.tweenOrSnap(reduce, Motion.STANDARD), label = "budget_ring_color")
        val track = MaterialTheme.colorScheme.surface
        val tick = MaterialTheme.colorScheme.onSurface
        val amount = overall.note.substringBeforeLast(' ')
        val word = overall.note.substringAfterLast(' ')
        val exact = privately(overall.note + " of " + overall.limit)
        val ringBox: @Composable () -> Unit = {
            Box(Modifier.size(136.dp).testTag("budget_ring"), contentAlignment = Alignment.Center) {
                if (!hide) androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val stroke = 14.dp.toPx()
                    val inset = stroke / 2 + 4.dp.toPx()
                    val arc = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset)
                    drawArc(track, 0f, 360f, false, androidx.compose.ui.geometry.Offset(inset, inset), arc, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                    if (fill > 0f) drawArc(color, -90f, 360f * fill, false, androidx.compose.ui.geometry.Offset(inset, inset), arc, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    // "today": a short line across the ring at the share of the period gone
                    val a = (-90.0 + 360.0 * budget.elapsed.coerceIn(0f, 1f)) * kotlin.math.PI / 180.0
                    val r = arc.width / 2f
                    val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                    val inner = r - stroke * 0.8f
                    val outer = r + stroke * 0.8f
                    drawLine(tick.copy(alpha = 0.85f), androidx.compose.ui.geometry.Offset(c.x + (kotlin.math.cos(a) * inner).toFloat(), c.y + (kotlin.math.sin(a) * inner).toFloat()), androidx.compose.ui.geometry.Offset(c.x + (kotlin.math.cos(a) * outer).toFloat(), c.y + (kotlin.math.sin(a) * outer).toFloat()), strokeWidth = 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp).testTag("budget_overall_note").clearAndSetSemantics { text = androidx.compose.ui.text.AnnotatedString(exact) }) {
                    Text(amount.wholeAmounts(), style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 1, autoSize = amountFit(22), color = if (overall.pace == PaceLevel.OVER) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    Text(word, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val words: @Composable (Modifier) -> Unit = { mod ->
            Column(mod, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    paceWord(overall), style = MaterialTheme.typography.labelLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = paceColor(overall.pace),
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(paceColor(overall.pace).copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp).testTag("budget_pace_word"),
                )
                if (budget.perDay != null) {
                    val days = "${budget.daysLeft} ${if (budget.daysLeft == 1) "day" else "days"}"
                    Text(
                        when {
                            budget.billsUseItAll -> "Bills still to come (${budget.upcoming}) use up what is left of your limit."
                            budget.upcoming != null -> "After ${budget.upcoming} of repeating bills still to come, you can spend about ${budget.perDay} a day for the $days left."
                            else -> "You can spend about ${budget.perDay} a day for the $days left. Repeating expenses without a fixed amount are not counted."
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("safe_to_spend"),
                    )
                }
                spokenFor?.let { SpokenForLine(it, "spoken_for_budget") }

                Text(overall.spent.wholeAmounts() + " of " + overall.limit.wholeAmounts() + " · " + budget.range, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("budget_range_line"))
            }
        }
        if (stacked) { Box(Modifier.align(Alignment.CenterHorizontally)) { ringBox() }; words(Modifier.fillMaxWidth()) }
        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { ringBox(); words(Modifier.weight(1f)) }
    }
}

/** One limit as a row (UI S5): the category's tile, "spent of limit", the bar with its tick for today and the state in words with what is left. */
@Composable
private fun LimitRow(line: BudgetLineUi, elapsed: Float, onClick: () -> Unit) {
    val hide = LocalHideAmounts.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp).testTag("budget_row_${line.categoryId ?: "overall"}"), verticalAlignment = Alignment.Top) {
        CategoryAvatar(line.name, line.name, Modifier.padding(end = 12.dp, top = 2.dp), size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // from 140% text the name has a line to itself: beside "₹906.00 of ₹15,000.00" it had room for two letters
            if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) Column {
                Text(line.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                Text("${line.spent} of ${line.limit}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(line.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 2)
                Text("${line.spent} of ${line.limit}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
            }
            if (!hide) AnimatedBudgetBar(line, Modifier.height(6.dp).clip(RoundedCornerShape(50)), elapsed = elapsed)
            Row(Modifier.fillMaxWidth()) {
                Text(paceWord(line), style = MaterialTheme.typography.bodySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = paceColor(line.pace), modifier = Modifier.testTag("budget_row_word_${line.categoryId}"))
                if (line.pace != PaceLevel.OVER) Text(" · ${line.note}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Period, carry over and the start day, behind the pencil (UI S5): they are set once, the limits are read every day. */
@Composable
private fun BudgetSettingsDialog(
    budget: BudgetUi, onSetPeriod: (BudgetPeriod) -> Unit, onShiftStartDay: (Int) -> Unit, onShiftCustomDays: (Int) -> Unit, onRollover: (Boolean) -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("budget_settings_dialog"),
        title = { Text("Budget settings") },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Carry over what is left")
                        Text(if (budget.rollover && budget.carriedText.isNotEmpty()) "${budget.carriedText} came over from last time, and is in the limit below." else "Whatever was not spent last time is added to this time's limit. It does not pile up beyond one period.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("budget_carried"))
                    }
                    androidx.compose.material3.Switch(checked = budget.rollover, onCheckedChange = onRollover, modifier = Modifier.testTag("budget_rollover"))
                }
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("budget_settings_done")) { Text("Done") } },
    )
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
private fun FORTNIGHT_FROM_THIS_MONDAY(): kotlinx.datetime.LocalDate = thisMonday()

private fun TODAY(): kotlinx.datetime.LocalDate = app.expenses.todayLocal()

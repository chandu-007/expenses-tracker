package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.expenses.UiState
import app.expenses.core.domain.BudgetState
import app.expenses.core.domain.PaceMath
import app.expenses.core.domain.wholeAmounts

/**
 * Home's one card (Slice 133, boards Main, HomeOver, HomeHidden, LargeText, HomeDark): "Left to spend" with the status chip, the figure, "of ₹75,000 · ₹49,305 spent · 8 days to go", the pace bar with
 * its tick for today, how this period stands against the last at the same point ("▼ ₹492 less than October by today") and the two figures that say what can be spent: a day from the budget, and what
 * the balances allow. No gradient and no graph. Everything is read from what Home already holds (the budget status, the balances' safe-to-spend line and the comparison are worked out with the other
 * figures once, not when the page opens). The figure stops growing at 54 sp ([MoneyText]).
 *
 * Over the limit the card is the over state: "₹2,140 over", "Over by ₹2,140", a full red bar and a line that what is spent now is all over. Without a budget limit (or with the Budget block
 * hidden in Customise home) it is "Spent this month" with the figure spent, the comparison, and a "Set a budget" prompt in place of the pace; the balances' figure stays when it is on. With Hide amounts every figure
 * is `₹••••` and the bar is empty ([AnimatedBudgetBar]). Tags: `home_hero` (the card) and `budget_summary` (its top part, which opens the budget), `month_spend` (the amount spent, exact to a screen reader),
 * `budget_overall_note` (the exact "left of" sentence), `budget_pace_word` (the chip), `hero_pace_line` (the a-day figure from the budget, or the over line), `month_compare`, `home_safe_to_spend_line`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeHero(state: UiState.Ready, showBudget: Boolean, onOpenBudget: () -> Unit, onOpenSafe: () -> Unit, large: Boolean, modifier: Modifier = Modifier) {
    val budget = state.budget
    val overall = budget.overall
    val hasLimit = showBudget && overall.limit != null
    val over = hasLimit && overall.state == BudgetState.OVER
    val safe = state.safeToSpend?.takeIf { it.homeVisible && showBudget }
    val ink = MaterialTheme.colorScheme.onSurface
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    val cardModifier = if (over) Modifier.inlineCard(Signal.over, strength = 0.07f) else Modifier.standardCard()
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).tourSpot("hero").then(cardModifier)
            .testTag("home_hero")
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // the top of the card (label, chip, figure, bar, comparison) opens the budget as one stop; the two figures below and the lines under them keep their own taps and reading
        Column(Modifier.fillMaxWidth().clip(Radius.XS).clickable(onClickLabel = "Open the budget", onClick = onOpenBudget).testTag("budget_summary"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val label = when { over -> "Over budget"; hasLimit -> "Left to spend"; else -> "Spent ${budget.periodName}" }
        val chip: @Composable () -> Unit = { if (hasLimit) StatusChip(overall, "budget_pace_word", elapsed = budget.elapsed) }
        if (!large) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = quiet, maxLines = 1, modifier = Modifier.weight(1f))
            chip()
        } else {
            Text(label, style = MaterialTheme.typography.labelLarge, color = quiet, maxLines = 1)
            chip()
        }
        if (hasLimit) {
            // one node says the exact sentence ("₹4,000.00 left of ₹5,000.00") to a screen reader and the tests; what is drawn is in whole units
            val exact = privately(overall.note + " of " + overall.limit)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Column(Modifier.testTag("budget_overall_note").clearAndSetSemantics { text = AnnotatedString(exact) }) {
                val figure = overall.note.removeSuffix(" left").removeSuffix(" over")
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RollingAmount(figure, color = if (over) Signal.over else ink, whole = true)
                    if (over) Text("over", style = MaterialTheme.typography.titleMedium, color = Signal.over, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            run {
                // "of ₹75,000 · ₹49,305 spent · 8 days to go": the spent figure has its own node, read exactly (outside the node above, whose exact sentence hides what is inside it)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    val limitExact = privately("of " + overall.limit!! + " ·")
                    Text("of " + overall.limit.wholeAmounts() + " ·", style = MaterialTheme.typography.bodyMedium, color = quiet, modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(limitExact) })
                    val spentExact = privately(state.periodSpend)
                    Text(state.periodSpend.wholeAmounts() + (if (!over && budget.periodDays > 0) " spent ·" else " spent"), style = MaterialTheme.typography.bodyMedium, color = quiet, modifier = Modifier.testTag("month_spend").clearAndSetSemantics { text = AnnotatedString(spentExact) })
                    if (!over && budget.periodDays > 0) Text((if (budget.daysLeft <= 1) "last day" else "${budget.daysLeft} days to go"), style = MaterialTheme.typography.bodyMedium, color = quiet, modifier = Modifier.testTag("hero_days_left"))
                }
            }
            }
            AnimatedBudgetBar(overall, Modifier.height(8.dp).clip(RoundedCornerShape(50)), elapsed = budget.elapsed, todayLabel = true)
        } else {
            RollingAmount(state.periodSpend, modifier = Modifier.testTag("month_spend"), color = ink, whole = true)
        }
        val compare = state.spark.compare
        if (compare.isNotEmpty()) {
            val less = compare.contains("less than")
            val shown = (if (less) "▼ " else if (compare.contains("more than")) "▲ " else "") + compare.wholeAmounts()
            val compareExact = privately(compare)
            Text(
                shown, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                color = if (less || !compare.contains("more than")) MaterialTheme.colorScheme.primary else Signal.watch,
                modifier = Modifier.testTag("month_compare").clearAndSetSemantics { text = AnnotatedString(compareExact) },
            )
        }
        if (!large && state.spark.insight.isNotEmpty()) Text(state.spark.insight, style = MaterialTheme.typography.bodySmall, color = quiet, modifier = Modifier.testTag("home_insight"))
        if (over) {
            // "Over by ₹2,140.00" is the chip; the line says what that means for what is spent next
            val exactOver = privately(PaceMath.word(overall.pace, overall.note.removeSuffix(" over")))
            Text("Everything you spend now is over the limit.", style = MaterialTheme.typography.bodyMedium, color = Signal.over, modifier = Modifier.testTag("hero_pace_line").clearAndSetSemantics { text = AnnotatedString(exactOver) })
        }
        if (!hasLimit && showBudget) BudgetPrompt(budget.periodName)
        }
        val perDay = budget.perDay
        val budgetCell = hasLimit && !over && (perDay != null || budget.billsUseItAll)
        if (budgetCell || safe != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val cells: @Composable (Modifier) -> Unit = { m ->
                if (budgetCell) {
                    if (perDay != null && !budget.billsUseItAll) {
                        val exactLine = privately(PaceMath.heroLine(perDay, budget.daysLeft, budget.endWord))
                        Column(m.testTag("hero_pace_line").clearAndSetSemantics { text = AnnotatedString(exactLine) }) {
                            Text(perDay.wholeAmounts(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
                            Text("a day from your budget", style = MaterialTheme.typography.bodySmall, color = quiet)
                        }
                    } else Column(m.testTag("hero_pace_line")) {
                        Text("bills still to come use what is left", style = MaterialTheme.typography.bodySmall, color = quiet)
                    }
                }
                if (safe != null) {
                    val headlineExact = privately(safe.headline)
                    Column(m.clip(Radius.XS).clickable(onClickLabel = "Show how safe to spend is worked out", onClick = onOpenSafe).semantics(mergeDescendants = true) { }.testTag("home_safe_to_spend_line").heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically)) {
                        // "₹1,78,198 safe to spend until 30 Nov": the figure big, the rest as its caption (the node still says the whole sentence)
                        val cut = safe.headline.indexOf(" safe to spend")
                        val figure = if (cut > 0) safe.headline.substring(0, cut) else safe.headline
                        val rest = if (cut > 0) safe.headline.substring(cut + 1) else ""
                        OdometerText(
                            figure.wholeAmounts(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            color = if (safe.nothingSpare) MaterialTheme.colorScheme.error else ink,
                            modifier = Modifier.testTag("home_safe_to_spend_line_text").clearAndSetSemantics { text = AnnotatedString(headlineExact) },
                            memoryKey = "home_safe_to_spend_line",
                        )
                        Text(if (rest.isNotEmpty()) "$rest · from your balances" else "from your balances", style = MaterialTheme.typography.bodySmall, color = quiet)
                        Text("How is this worked out?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("home_safe_to_spend_line_how"))
                    }
                }
            }
            if (large) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { cells(Modifier.fillMaxWidth()) }
            else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { cells(Modifier.weight(1f)) }
        }
        if (hasLimit) {
            if (budget.billsUseItAll && perDay != null) Text("bills use it all", style = MaterialTheme.typography.bodySmall, color = quiet, modifier = Modifier.testTag("budget_bills_use_all"))
            // N-18: how much of what is left the bills still due before the period ends will take (a tap lists them)
            state.spokenFor?.let { SpokenForLine(it, "spoken_for_home") }
            if (budget.categoriesNeedingAttention > 0) {
                Text(
                    if (budget.categoriesNeedingAttention == 1) "1 category budget is near or over its limit" else "${budget.categoriesNeedingAttention} category budgets are near or over their limit",
                    style = MaterialTheme.typography.bodySmall, color = quiet,
                )
            }
        }
    }
}

/** Without a limit the card asks for one: "Set a budget for this month" and a small Set button; the whole card opens the budget screen. */
@Composable
private fun BudgetPrompt(periodName: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Set a budget for $periodName", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).testTag("budget_set_prompt"))
            Text("Set", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 16.dp, vertical = 8.dp))
        }
        Text("Pick a limit and see what is left, and what you can spend a day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

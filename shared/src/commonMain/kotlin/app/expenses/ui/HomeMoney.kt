package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import app.expenses.core.domain.wholeAmounts
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.expenses.BudgetUi
import app.expenses.SafeToSpendUi

/**
 * The two money tiles under the month card (Home, Option B): "Budget left" (what is left of the limit, its bar, and what that allows a day) and "Safe to spend" (what the balances allow until
 * the end of the period, and what that is a day), side by side, each saying where its a-day figure comes from ("from your budget", "from your balances"): the two figures are different things and
 * were 70 dp apart with nothing to tell them apart. Without a budget limit the old "Set a budget" prompt is drawn; without the safe-to-spend line (it is off, or has nothing to say) the budget tile is
 * the whole row. At large text the tiles stack. Hide amounts: every figure is masked by [Text], and the bar, which would show the figure's size, is not drawn.
 */
@Composable
fun HomeMoneyTiles(budget: BudgetUi, safe: SafeToSpendUi?, onOpenBudget: () -> Unit, onOpenSafe: () -> Unit) {
    val margin = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    if (budget.overall.limit == null) {
        BudgetSummary(budget, onOpenBudget)
        safe?.let { SafeToSpendTile(it, "home_safe_to_spend_line", onOpenSafe, margin) }
        return
    }
    if (safe == null) { BudgetSummary(budget, onOpenBudget, margin); return }
    if (isLargeText()) {
        Column(margin, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BudgetSummary(budget, onOpenBudget, Modifier.fillMaxWidth())
            SafeToSpendTile(safe, "home_safe_to_spend_line", onOpenSafe, Modifier.fillMaxWidth())
        }
    } else {
        Row(margin.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BudgetSummary(budget, onOpenBudget, Modifier.weight(1f).fillMaxHeight())
            SafeToSpendTile(safe, "home_safe_to_spend_line", onOpenSafe, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** The balance-based safe to spend as a tile: the headline (what can be spent until when), then what that is a day and that it comes from the balances. Tapping it opens the breakdown. */
@Composable
fun SafeToSpendTile(ui: SafeToSpendUi, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tint = if (ui.nothingSpare) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(tint.copy(alpha = 0.08f).compositeOver(MaterialTheme.colorScheme.surface))
            .clickable(onClickLabel = "Show how safe to spend is worked out", onClick = onClick).semantics(mergeDescendants = true) { }.testTag(tag)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val headlineExact = privately(ui.headline)
        val perDayExact = privately("${ui.perDay} from your balances")
        // drawn in whole units; the node says the exact headline ("₹23,000.00 safe to spend until 30 Nov") to a screen reader and to the tests
        Text(
            ui.headline.wholeAmounts(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            color = if (ui.nothingSpare) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(tag + "_text").clearAndSetSemantics { text = AnnotatedString(headlineExact) },
        )
        val perDay = ui.perDay
        Text(
            if (perDay != null) "${perDay.wholeAmounts()} from your balances" else "from your balances, after card bills and what is due",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(tag + "_per_day").then(if (perDay != null) Modifier.clearAndSetSemantics { text = AnnotatedString(perDayExact) } else Modifier),
        )
    }
}

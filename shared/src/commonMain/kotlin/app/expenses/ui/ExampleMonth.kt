package app.expenses.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.expenses.core.domain.Currency
import app.expenses.core.domain.ExampleMonth
import app.expenses.core.domain.ExampleRow
import app.expenses.core.domain.Money
import app.expenses.core.domain.formatted
import app.expenses.core.domain.wholeAmounts

/**
 * The Example month under the welcome card (UI U-10): the month card and a few rows drawn the way real ones are, at half strength, with a ribbon that says it is an example. It is a drawing of the
 * constants in [ExampleMonth]: nothing here reads or writes the ledger, so no total, widget, backup, report or notification can ever include it.
 *
 * A screen reader gets the ribbon ("Example month, not your data") and "Hide example", and nothing of the drawing itself. A tap on an expense row opens the Add form with that row's amount and
 * merchant filled in ([onRow]); the form saves nothing by itself. The salary row is only drawn: the Add form starts as an expense.
 */
@Composable
fun ExampleMonthBlock(currency: Currency, onRow: (ExampleRow) -> Unit, onHide: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).testTag("example_month")) {
        Box(Modifier.padding(horizontal = 16.dp).inlineCardSolid(MaterialTheme.colorScheme.secondaryContainer).semantics(mergeDescendants = true) { contentDescription = "Example month, not your data" }.testTag("example_ribbon")) {
            Text("Example · yours replaces this", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        }
        Column(Modifier.alpha(0.5f).clearAndSetSemantics { }) {
            GhostMonthCard(currency)
            val last = ExampleMonth.rows.lastIndex
            ExampleMonth.rows.forEachIndexed { i, row -> GhostRow(i, row, CardPos.of(first = i == 0, last = i == last), currency, onRow) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onHide, modifier = Modifier.testTag("example_hide")) { Text("Hide example") }
        }
    }
}

/** The month card as Home draws it: the hero background, the label, the figure in whole units, and a dashed line of the last 14 days (left out at large text and while amounts are hidden, as the real graph is). */
@Composable
private fun GhostMonthCard(currency: Currency) {
    val onHero = MaterialTheme.colorScheme.onPrimary
    val large = isLargeText()
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).heroBackground()) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 14.dp)) {
            Text("Spent this month", style = MaterialTheme.typography.labelLarge, color = onHero.copy(alpha = 0.85f), maxLines = 1)
            CappedFontScale(LARGE_TEXT_SCALE) {
                MoneyText(
                    Money(ExampleMonth.spentMinor, currency).formatted().wholeAmounts(),
                    modifier = Modifier.padding(top = 2.dp), color = onHero, style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                )
            }
            if (!large && !LocalHideAmounts.current) GhostLine(onHero, Modifier.padding(top = 8.dp))
        }
    }
}

/** The last 14 days of the example as a dashed line: it reads as "not real" without a word. Drawn once. */
@Composable
private fun GhostLine(color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val points = ExampleMonth.dailyMinor
    val top = (points.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Canvas(modifier.fillMaxWidth().height(40.dp)) {
        val pad = 2.dp.toPx()
        val path = Path()
        points.forEachIndexed { i, v ->
            val x = size.width * i / (points.size - 1)
            val y = size.height - pad - (size.height - 2 * pad) * v.toFloat() / top
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))))
    }
}

/** One row, the same look as an entry on Home (tile, title, detail, amount), with the amount stacked under the words at large text. */
@Composable
private fun GhostRow(index: Int, row: ExampleRow, pos: CardPos, currency: Currency, onRow: (ExampleRow) -> Unit) {
    val large = isLargeText()
    val flow = if (row.income) Flow3.IN else Flow3.OUT
    val detail = row.categoryName // a real row on Home says its account and category; the day is the heading above it, which the example has no use for
    val amount: @Composable () -> Unit = {
        MoneyText(signedAmount(Money(row.minor, currency).formatted(), flow), style = NumberStyle.Row, color = amountColor(flow))
    }
    Row(
        Modifier.entryCardClip(pos).entryCardFill(pos).testTag("example_row_$index")
            .clickable(enabled = !row.income) { onRow(row) }
            .padding(horizontal = 16.dp, vertical = rowPadding(12.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tileSize = if (large) 32.dp else 40.dp
        Box(Modifier.padding(end = if (large) 10.dp else 14.dp)) { EntryTile(row.merchant, row.categoryName, if (row.income) "INCOME" else "EXPENSE", size = tileSize) }
        if (!large) {
            Column(Modifier.weight(1f)) {
                Text(row.merchant, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            amount()
        } else {
            Column(Modifier.weight(1f)) {
                Text(row.merchant, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                amount()
            }
        }
    }
}

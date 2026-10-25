package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.expenses.AccountRowUi
import app.expenses.CardRowUi

/** A credit card has a summary to show once it has billing dates (the account's [AccountRowUi.card] is set then). */
internal val AccountRowUi.hasCardSummary get() = type == "CREDIT_CARD" && card?.cycleText?.isNotEmpty() == true

/** Room the card summary adds under the usual height of a tile: three short lines, and a bar with its words when a limit was entered. Two lines each at the tile's width. */
internal fun cardSummaryExtra(c: CardRowUi): Dp = 60.dp + (if (c.limitText.isNotEmpty()) 44.dp else 0.dp)

/**
 * What a credit card has charged since its statement, how much of the limit is used (a bar and the words, only when a limit was entered) and what is left to pay
 * with its due date and the days left. The words are made in the domain ([app.expenses.core.domain.CardSummaryText]); this only lays them out.
 *
 * [tags] is the three test tags (spend, limit, due line) so the account dialog and the tile never share one. [ink] is the text colour; the bar takes it too unless
 * [bar] is given.
 */
@Composable
internal fun CardSummaryLines(c: CardRowUi, tags: Triple<String, String, String>, ink: Color, modifier: Modifier = Modifier, barColor: (Float) -> Color = { ink }, trackColor: Color = ink.copy(alpha = 0.25f)) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(c.cycleText, color = ink, style = MaterialTheme.typography.bodySmall, maxLines = 2, modifier = Modifier.testTag(tags.first))
        if (c.limitText.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.testTag(tags.second + "_group")) {
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(trackColor).testTag(tags.second + "_bar")) {
                Box(Modifier.fillMaxWidth(c.limitFraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(barColor(c.limitFraction)))
            }
            Text(c.limitText, color = ink, style = MaterialTheme.typography.bodySmall, maxLines = 2, modifier = Modifier.testTag(tags.second))
        }
        Text(c.dueLineText, color = ink, style = MaterialTheme.typography.bodySmall, maxLines = 2, modifier = Modifier.testTag(tags.third))
    }
}

/** The bar's colour in the account dialog, by how full it is: calm, amber from 80% of the limit, red at it. */
@Composable
internal fun limitBarColors(): (Float) -> Color {
    val error = MaterialTheme.colorScheme.error
    val calm = MaterialTheme.colorScheme.primary
    return { fraction -> if (fraction >= 1f) error else if (fraction >= 0.8f) Color(0xFFB26A00) else calm }
}

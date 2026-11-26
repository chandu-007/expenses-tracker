package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.expenses.BudgetLineUi
import app.expenses.core.domain.PaceLevel
import app.expenses.core.domain.PaceMath
import app.expenses.core.domain.wholeAmounts

/*
 * The shared pieces of the redesign (rule 7 of docs/REDESIGN.md and the boards): the status chip, the one set of four words every bar in the app uses, and (in HomeVisuals) the pace bar
 * that loses its length under Hide amounts. The amber nudge card is `NudgeSurface`, the entry row and the account tile keep their own files and use the sign rule of HomeStyle.
 */

/** The four words of a budget line's state: "On pace", "Well under", "A little ahead", "Over by ₹1,655" (the amount in whole units). [elapsed] is how much of the period has gone (0 to 1), or -1 when not known. */
fun statusWord(line: BudgetLineUi, elapsed: Float = -1f): String =
    PaceMath.statusWord(line.pace, PaceMath.isWellUnder(line.percent / 100f, elapsed), line.note.removeSuffix(" over").wholeAmounts())

/** The colour of a state: teal for on pace and well under, amber for a little ahead, red only for over. The word says the same, so colour is never the only signal. */
@Composable
fun statusColor(level: PaceLevel): Color = paceColor(level)

/** The soft fill behind a status chip: the accent's, the amber's or the red's own soft colour. */
@Composable
fun statusSoft(level: PaceLevel): Color = when (level) {
    PaceLevel.EARLY, PaceLevel.ON_PACE -> MaterialTheme.colorScheme.primaryContainer
    PaceLevel.A_LITTLE_AHEAD, PaceLevel.AHEAD -> Signal.watchSoft
    PaceLevel.OVER -> Signal.overSoft
}

/** The status chip: the state's word in its colour on its soft fill, a pill. [tag] is the test tag of the word. */
@Composable
fun StatusChip(line: BudgetLineUi, tag: String, modifier: Modifier = Modifier, elapsed: Float = -1f, style: TextStyle = MaterialTheme.typography.labelLarge) {
    Text(
        statusWord(line, elapsed), style = style, fontWeight = FontWeight.SemiBold, color = statusColor(line.pace),
        modifier = modifier.clip(RoundedCornerShape(50)).background(statusSoft(line.pace)).padding(horizontal = 10.dp, vertical = 4.dp).testTag(tag),
    )
}

package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * Three cards, no fourth (UI S1):
 *  - the hero card ([heroBackground], Home and Reports: 28 dp, the accent gradient);
 *  - the standard card ([standardCard]: 20 dp, a flat tint of the surface, no shadow) for a group of rows, a balance strip, a list on More or Search;
 *  - the inline card ([inlineCard]: 20 dp, a wash of one colour) for a nudge, a tile that carries a state, a notice.
 * Before this there were nine ways to draw a card (three tints, four radii, Material's `Card` and drawn ones).
 */
@Composable
fun standardCardColor(): Color = if (LocalHighContrast.current) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceContainerLow // UI S9: high contrast drops the see-through tint

/** A standard card: clipped to 20 dp and tinted; the caller adds its own padding. */
@Composable
fun Modifier.standardCard(): Modifier = this.clip(Radius.M).background(standardCardColor()).border(androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), Radius.M)

/** An inline card in a wash of [tint] (about 10% of it over the surface, so the text on it keeps its contrast in both themes). */
@Composable
fun Modifier.inlineCard(tint: Color, strength: Float = 0.10f): Modifier =
    this.clip(Radius.M).background(tint.copy(alpha = strength).compositeOver(MaterialTheme.colorScheme.surface))

/** An inline card in a solid role colour (the backup nudge, the trip total), not a wash. */
fun Modifier.inlineCardSolid(color: Color): Modifier = this.clip(Radius.M).background(color)

/**
 * "Details": a page leads with one sentence and keeps its long explanations behind this line (U-18). A disclosure widget: a 48 dp row that says "Details" and whether it is open, a screen reader hears "Details, collapsed" and
 * "Show details" as the action. What is behind it stays out of the way until asked for; nothing in it is a warning (red is for a real danger, never for an explanation).
 */
@Composable
fun Details(tag: String, modifier: Modifier = Modifier, summary: String = "Details", content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.S)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(onClickLabel = (if (open) "Hide " else "Show ") + summary.lowercase(), role = Role.Button) { open = !open }
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }.testTag(tag),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(summary, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        if (open) content()
    }
}

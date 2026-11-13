package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/*
 * One look for every card Home asks something of (the "nudges", docs/HOME_NUDGES.md): the same tinted surface, the same corner, a title and one supporting line, ONE button on the right
 * and ONE way to put it off, a close button (48 dp). Only the words, the tags and what the buttons do differ. The order, "one at a time" and what each card remembers are the nudges' own
 * and are unchanged (HomeNudges). At large text (150% and up) the button moves under the words so nothing is squeezed into a one-word column.
 */

/** The surface every nudge sits on: the app's accent at 10% over the page, 20 dp corners, 16 dp from the screen edge. Carries the test tag of the card. */
@Composable
internal fun NudgeSurface(tag: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, onClickLabel: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f).compositeOver(MaterialTheme.colorScheme.surface)
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(20.dp)).background(tint)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
            .testTag(tag) // before the padding: the node is the whole card
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        content = content,
    )
}

/** The close button every nudge has: 48 dp, the same cross. [description] says what it does for this card (for the day, for a week, for good). */
@Composable
internal fun NudgeClose(tag: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.testTag(tag)) { Icon(Icons.Outlined.Close, contentDescription = description) }
}

/**
 * A nudge: [title] with an optional [line] under it, a [actionLabel] button and the close button. [onAction] is what the button does; with [wholeCardOpens] the rest of the card does it too.
 * The tags are the card's ([tag], [titleTag], [lineTag], [actionTag]) and its close button's ([dismissTag]); [dismissLabel] is what a screen reader says for the cross.
 */
@Composable
fun NudgeCard(
    tag: String, title: String, titleTag: String, line: String?, lineTag: String?,
    actionLabel: String, actionTag: String, onAction: () -> Unit,
    dismissTag: String, dismissLabel: String, onDismiss: () -> Unit,
    wholeCardOpens: Boolean = false, modifier: Modifier = Modifier,
) {
    val large = isLargeText()
    val words: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() }.testTag(titleTag))
            if (!line.isNullOrEmpty()) Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = if (lineTag != null) Modifier.testTag(lineTag) else Modifier)
        }
    }
    NudgeSurface(tag, modifier, onClick = if (wholeCardOpens) onAction else null, onClickLabel = if (wholeCardOpens) actionLabel else null) {
        if (!large) Row(verticalAlignment = Alignment.CenterVertically) {
            words(Modifier.weight(1f).padding(vertical = 8.dp))
            Spacer(Modifier.width(8.dp))
            NudgeButton(actionLabel, actionTag, onAction)
            NudgeClose(dismissTag, dismissLabel, onDismiss)
        } else {
            Row(verticalAlignment = Alignment.Top) {
                words(Modifier.weight(1f).padding(top = 12.dp, bottom = 4.dp))
                NudgeClose(dismissTag, dismissLabel, onDismiss)
            }
            NudgeButton(actionLabel, actionTag, onAction, Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun NudgeButton(label: String, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, contentPadding = PaddingValues(horizontal = 18.dp), modifier = modifier.heightIn(min = 48.dp).testTag(tag)) { Text(label, maxLines = 1) }
}

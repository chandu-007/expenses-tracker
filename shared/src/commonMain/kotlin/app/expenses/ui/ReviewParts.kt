package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.expenses.core.domain.CaptureHeartbeat

/*
 * UI S6 (U-11): the parts of the review ritual that more than one screen uses: the "Change" toggle on a To review card, the thin progress bar with "N to go" (To review and Categorise),
 * the calm closing picture, the small "not reviewed" dot on a row and the capture heartbeat chip on Home.
 */

/** The one "Change" control of a To review card: it opens what is not the usual answer (type, account, category, name, the message). It says "Done" while open. */
@Composable
fun ChangeToggle(id: String, changing: Boolean, onToggle: () -> Unit) {
    TextButton(onClick = onToggle, modifier = Modifier.testTag("inbox_change_$id").semantics { stateDescription = if (changing) "Options shown" else "Options hidden" }) { Text(if (changing) "Done" else "Change") }
}

/**
 * A thin bar and "3 to go": [togo] items are still waiting out of [total] this visit began with. The bar says how far through the person is; the words say how many are left and are
 * announced when they change (a polite live region); the bar carries the same in its state description. No animation: the figure and the bar change together.
 */
@Composable
fun ReviewProgress(togo: Int, total: Int, modifier: Modifier = Modifier, wordsTag: String = "review_togo") {
    val all = maxOf(total, togo)
    val done = (all - togo).coerceAtLeast(0)
    val words = "$togo to go"
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { if (all <= 0) 0f else (done.toFloat() / all).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(Radius.XS).semantics { stateDescription = "$done of $all reviewed, $words" }.testTag("review_progress"),
        )
        Text(words, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(wordsTag))
    }
}

/**
 * Everything is reviewed: the drawn tick (once, complete under Reduce motion) and one calm sentence. The tick is decoration (it has no description); the sentence says the result and is announced.
 * [hadWork] is true when the person cleared the list during this visit, which is when the SAVE feel is given. [iPhone] words the sentence for a phone that gets texts through a Shortcut.
 */
@Composable
fun ReviewAllCleared(iPhone: Boolean, hadWork: Boolean, modifier: Modifier = Modifier) {
    val feel = rememberFeel()
    LaunchedEffect(Unit) { if (hadWork) feel(Feel.SAVE) }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SuccessCheck(size = 48.dp)
        Text(
            if (iPhone) "All cleared. The next bank text will land here." else "All cleared. The next text will land here.",
            style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("review_all_cleared"),
        )
    }
}

/**
 * The small dot on a row that came from a text, notification or imported statement and has not been reviewed yet (an expense with no category). 8 dp in the accent colour with a ring in the
 * row's own colour so it reads on any tile; the words "not reviewed" are in its description, so a screen reader hears them with the row. Put it inside a [Box] at a corner of the tile.
 */
@Composable
fun UnreviewedDot(id: String, modifier: Modifier = Modifier) {
    Box(
        modifier.size(10.dp).background(MaterialTheme.colorScheme.surface, CircleShape).padding(1.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
            .semantics { contentDescription = "not reviewed" }.testTag("row_unreviewed_$id"),
    )
}


/**
 * Home's capture heartbeat: "Last bank text seen yesterday 8:14 pm". After a quiet stretch it turns amber (never red) and says so in words on a second line. One tap opens "Why isn't capture
 * working?". Information, not a request: it is not a Home nudge (docs/HOME_NUDGES.md). It holds a time of day and no amount.
 */
@Composable
fun CaptureHeartbeatChip(chip: CaptureHeartbeat.Chip, onOpen: () -> Unit) {
    val quiet = chip.state == CaptureHeartbeat.State.QUIET
    val tint = if (quiet) Signal.watch else MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(Radius.S).background(tint.copy(alpha = if (quiet) 0.16f else 0.08f))
            .clickable(onClickLabel = "Check why capture may not be working", role = Role.Button, onClick = onOpen)
            .heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics { stateDescription = chip.stateWords }.testTag("capture_heartbeat"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Sms, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(chip.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("capture_heartbeat_text"))
            chip.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("capture_heartbeat_note")) }
        }
    }
}

/**
 * The To review screen with nothing in it. Once the list has been read ([loaded]) that is the closing state: the drawn tick and "All cleared. The next text will land here." (the tag `inbox_empty` is on
 * it, as it was on the old "Nothing to review."). Before the list has been read, an empty list says nothing about what is waiting, so the old plain wording stands and the tick is never drawn.
 * Under it, the way to set capture up and the way to find out why it is quiet.
 */
@Composable
fun InboxEmptyState(loaded: Boolean, iPhone: Boolean, hadWork: Boolean, onSetUpCapture: (() -> Unit)?, onOpenCheckup: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (loaded) ReviewAllCleared(iPhone, hadWork, Modifier.testTag("inbox_empty"))
        else {
            EmptyArt(EmptyKind.INBOX, Modifier.align(Alignment.CenterHorizontally))
            Text("Nothing to review.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("inbox_empty"))
        }
        Text(
            "Payments found in your bank texts wait here for you to add or skip. Nothing is added until you say so." + if (iPhone) " You can also paste a message below." else "",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onSetUpCapture != null) OutlinedButton(onClick = onSetUpCapture, modifier = Modifier.testTag("inbox_empty_setup")) { Text("Set up reading texts") }
        if (onOpenCheckup != null) OutlinedButton(onClick = onOpenCheckup, modifier = Modifier.heightIn(min = 48.dp).testTag("inbox_empty_checkup")) { Text("Why isn't capture working?") }
    }
}

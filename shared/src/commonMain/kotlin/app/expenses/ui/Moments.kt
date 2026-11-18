package app.expenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.expenses.core.domain.NudgePause

/*
 * UI S6, success micro-moments (U-16) and the calm things around them (owner decisions D2 and D16): the tick beside "saved", the one milestone card, and the Pause nudges and milestones rows
 * of Notifications. No streak, no counter, no confetti, no red, nothing that loops. Words always say the result; the drawn tick is decoration and is not read out.
 */

/** What a snackbar says and whether it is the answer to a save: with [tick] the bar draws [SuccessCheck] beside the message ([StatusSnackbar]). */
internal class StatusVisuals(
    override val message: String,
    override val actionLabel: String?,
    override val duration: SnackbarDuration,
    val tick: Boolean,
) : SnackbarVisuals {
    override val withDismissAction: Boolean get() = false
}

/** The bar of Home's snackbar host: the usual bar, or, for a saved entry, the same bar with the drawn tick before the message (complete at once under Reduce motion). */
@Composable
internal fun StatusSnackbar(data: SnackbarData) {
    val visuals = data.visuals
    if ((visuals as? StatusVisuals)?.tick != true) { Snackbar(data); return }
    val label = visuals.actionLabel
    val action: (@Composable () -> Unit)? = if (label != null) {
        @Composable { TextButton(onClick = { data.performAction() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inversePrimary)) { Text(label) } }
    } else null
    Snackbar(modifier = Modifier.padding(12.dp), actionOnNewLine = isLargeText(), action = action) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SuccessCheck(Modifier.testTag("save_success_check"), size = 18.dp, color = MaterialTheme.colorScheme.inversePrimary, key = data)
            Spacer(Modifier.width(10.dp))
            Text(visuals.message, Modifier.weight(1f).testTag("save_success_text"))
        }
    }
}

/**
 * The one calm milestone card of Home (see `Milestones`): a drawn tick, one line and a "Thanks" button. It is not a nudge (it asks for nothing, has no close and no put-off, and is not in
 * "Also waiting"); "Thanks" is the only way it goes, and it is not shown again. The line holds no amount, so nothing here needs hiding under Hide amounts.
 */
@Composable
internal fun MilestoneCard(text: String, onThanks: () -> Unit, modifier: Modifier = Modifier) {
    val large = isLargeText()
    NudgeSurface("milestone_card", modifier) {
        if (!large) Row(verticalAlignment = Alignment.CenterVertically) {
            SuccessCheck(Modifier.padding(end = 12.dp), size = 22.dp, key = text)
            Text(text, Modifier.weight(1f).padding(vertical = 12.dp).testTag("milestone_text"), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onThanks, modifier = Modifier.heightIn(min = 48.dp).testTag("milestone_thanks")) { Text("Thanks") }
        } else {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 12.dp, end = 12.dp)) {
                SuccessCheck(Modifier.padding(end = 12.dp), size = 22.dp, key = text)
                Text(text, Modifier.weight(1f).testTag("milestone_text"), style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onThanks, modifier = Modifier.heightIn(min = 48.dp).testTag("milestone_thanks")) { Text("Thanks") }
        }
    }
}

/**
 * Notifications, top of the page: "Pause nudges" (a switch and three lengths, with a line that says until when) and "Occasional milestones" (the opt-in, off by default).
 * [pausedUntil] is the stored time (0: not paused, [NudgePause.FOREVER]: until turned back on). What a pause covers is written in [NudgePause].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PauseAndMilestones(pausedUntil: Long, onPause: (NudgePause.Choice?) -> Unit, milestonesOn: Boolean, onMilestones: (Boolean) -> Unit) {
    val now = app.expenses.nowMillis()
    val status = NudgePause.status(pausedUntil, now, app.expenses.systemZone())
    val paused = status != null
    // the length that is on: from the stored time, so a restart shows the same choice
    val chosen = when {
        !paused -> null
        pausedUntil == NudgePause.FOREVER -> NudgePause.Choice.FOREVER
        pausedUntil - now > 8 * 86_400_000L -> NudgePause.Choice.MONTH
        else -> NudgePause.Choice.WEEK
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SwitchRow(
            "Pause nudges", "Quiets the suggestions on Home and in More, and the notices that only suggest something. A bill that is due or overdue still shows.",
            paused, "pause_nudges_switch",
        ) { on -> onPause(if (on) NudgePause.Choice.WEEK else null) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = chosen == NudgePause.Choice.WEEK, onClick = { onPause(NudgePause.Choice.WEEK) }, label = { Text("1 week") }, modifier = Modifier.testTag("pause_nudges_week"))
            FilterChip(selected = chosen == NudgePause.Choice.MONTH, onClick = { onPause(NudgePause.Choice.MONTH) }, label = { Text("1 month") }, modifier = Modifier.testTag("pause_nudges_month"))
            FilterChip(selected = chosen == NudgePause.Choice.FOREVER, onClick = { onPause(NudgePause.Choice.FOREVER) }, label = { Text("Until I turn it back on") }, modifier = Modifier.testTag("pause_nudges_forever"))
        }
        if (status != null) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("pause_nudges_status"))
    }
    SwitchRow(
        "Occasional milestones", "A calm note on Home a few times a year, such as your first backup. Nothing daily, no counting, and never a notification.",
        milestonesOn, "milestones_switch", onChange = onMilestones,
    )
}

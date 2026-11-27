package app.expenses.ui

import app.expenses.core.domain.PaceMath
import app.expenses.core.domain.PaceLevel
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.unit.dp
import app.expenses.TripCardUi
import app.expenses.core.domain.wholeAmounts
import app.expenses.TripWindowUi

/**
 * Home's card while a trip is on: "Trip: Goa · day 3 of 7 · ₹4,500 spent" with a progress bar under it (Option B). With a daily budget the bar is how much of the whole trip budget is spent and a small line says it
 * ("58% of ₹18,000 trip budget"; the bar turns red past the budget); without one it is how far through the days the trip is. It is information, not a request, so it is not one of the nudges (docs/HOME_NUDGES.md):
 * it is never put off and does not count in "Also waiting". The whole card opens the trip's summary. Amounts are hidden while Hide amounts is on (this package's [Text]), and so are the bar and the percent (they would show the figure).
 */
@Composable
fun TripHomeCard(card: TripCardUi, onOpen: () -> Unit) {
    val hide = LocalHideAmounts.current
    val over = (card.budgetPercent ?: 0) > 100
    val fraction = card.budgetPercent?.let { (it / 100f).coerceIn(0f, 1f) } ?: if (card.length > 0) (card.dayNumber.toFloat() / card.length).coerceIn(0f, 1f) else 0f
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(Radius.M).background(MaterialTheme.colorScheme.surfaceContainerLow, Radius.M).border(androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), Radius.M)
            .clickable(onClickLabel = "Open the trip summary", role = Role.Button, onClick = onOpen)
            .heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 8.dp).testTag("home_trip_card"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // the board's line: the plane in the accent, "Goa trip · day 3 of 6" and what the trip has cost at the right; the node still says the whole line ("Goa trip · Day 3 of 6 · ₹12,400.00 spent")
        val lineExact = privately(card.line)
        val big = isLargeText()
        val titleShown = card.title.removePrefix("Trip: ")
        val amountShown = card.spentText.removeSuffix(" spent").wholeAmounts()
        Row(Modifier.testTag("home_trip_line").clearAndSetSemantics { text = androidx.compose.ui.text.AnnotatedString(lineExact) }, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Flight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            if (!big) {
                Text(titleShown, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Text(" · " + card.dayText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.weight(1f))
                Text(amountShown, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
            } else {
                // from 150% text the figure goes under the words (rule 4 of the redesign), the words wrap instead of being cut
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("$titleShown · ${card.dayText}", style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(amountShown, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        run {
            // UI S5: with a trip budget the bar has a tick for how many of the trip's days are gone ("today") and the line below says where the spending stands against it in words
            val pct = card.budgetPercent
            val elapsed = if (pct != null && card.length > 0) (card.dayNumber.toFloat() / card.length).coerceIn(0f, 1f) else -1f
            val level = if (pct != null && card.length > 0) PaceMath.level(pct.toLong(), 100L, elapsed, card.dayNumber, card.length) else null
            val tick = MaterialTheme.colorScheme.onSecondaryContainer
            Box(Modifier.fillMaxWidth()) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { if (hide) 0f else fraction }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(Radius.XS).testTag("home_trip_progress"),
                    color = if (over) MaterialTheme.colorScheme.error else level?.let { paceColor(it) } ?: MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.18f),
                )
                if (elapsed >= 0f) Spacer(Modifier.matchParentSize().drawBehind { val x = size.width * elapsed; drawLine(tick.copy(alpha = 0.8f), androidx.compose.ui.geometry.Offset(x, -2.dp.toPx()), androidx.compose.ui.geometry.Offset(x, size.height + 2.dp.toPx()), strokeWidth = 2.dp.toPx()) })
            }
            if (!hide && card.budgetPercent != null && card.budgetText.isNotEmpty()) {
                Text("${card.budgetPercent}% of ${card.budgetText} trip budget", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.testTag("home_trip_budget"))
                if (level != null && level != PaceLevel.EARLY) Text(PaceMath.word(level), style = MaterialTheme.typography.bodySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.testTag("home_trip_pace"))
            }
        }
    }
}

/** "Goa trip", or just the name when it already says trip ("Goa trip", "Trip to Goa"): how the form's chip names the trip. */
fun tripChipName(name: String): String = if (name.contains("trip", ignoreCase = true)) name else "$name trip"

/**
 * The add form's hint when the date falls inside a trip that is on: "Tagged for Goa trip" with a cross that takes the tag off for this entry; once taken off the chip offers it back
 * ("Tag it for Goa trip"). The tag is added when the entry is saved (and only then); an entry saved without it stays without it, whatever is edited later.
 */
@Composable
fun TripTagChip(trip: TripWindowUi, on: Boolean, showNote: Boolean = true, onToggle: () -> Unit) {
    val name = tripChipName(trip.name)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(
            selected = on, onClick = onToggle,
            label = { Text(if (on) "Tagged for $name" else "Tag it for $name") },
            leadingIcon = { Icon(Icons.Outlined.Flight, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = if (on) ({ Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp)) }) else null,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer, selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            border = FilterChipDefaults.filterChipBorder(enabled = true, selected = on, selectedBorderColor = MaterialTheme.colorScheme.primary, selectedBorderWidth = 1.dp),
            modifier = Modifier.semantics { contentDescription = if (on) "Tagged for $name with the tag ${trip.tag}. Double tap to take the tag off this entry." else "Not tagged for $name. Double tap to tag this entry with ${trip.tag}." }.testTag("trip_chip"),
        )
        if (showNote) Text(
            if (on) "The tag \"${trip.tag}\" is added when you save." else "Saved without the trip tag.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp).testTag("trip_chip_note"),
        )
    }
}

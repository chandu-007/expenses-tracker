package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import app.expenses.TripCardUi
import app.expenses.TripWindowUi

/**
 * Home's card while a trip is on: "Trip: Goa · day 3 of 7 · ₹4,500 spent". It is information, not a request, so it is not one of the nudges (docs/HOME_NUDGES.md): it sits right under the month card, is
 * never put off, and does not count in "Also waiting". The whole card opens the trip's summary. Amounts are hidden while Hide amounts is on (this package's [Text]).
 */
@Composable
fun TripHomeCard(card: TripCardUi, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClickLabel = "Open the trip summary", role = Role.Button, onClick = onOpen)
            .heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 8.dp).testTag("home_trip_card"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Flight, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(card.line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.testTag("home_trip_line"))
    }
}

/** "Goa trip", or just the name when it already says trip ("Goa trip", "Trip to Goa"): how the form's chip names the trip. */
fun tripChipName(name: String): String = if (name.contains("trip", ignoreCase = true)) name else "$name trip"

/**
 * The add form's hint when the date falls inside a trip that is on: "Tagged for Goa trip" with a cross that takes the tag off for this entry; once taken off the chip offers it back
 * ("Tag it for Goa trip"). The tag is added when the entry is saved (and only then); an entry saved without it stays without it, whatever is edited later.
 */
@Composable
fun TripTagChip(trip: TripWindowUi, on: Boolean, onToggle: () -> Unit) {
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
        Text(
            if (on) "The tag \"${trip.tag}\" is added when you save." else "Saved without the trip tag.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp).testTag("trip_chip_note"),
        )
    }
}

package app.expenses.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SelectableChipElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The colours of a chip that is on: the accent's container with its own text colour, not the faint grey-lavender of Material's default, so on and off are told apart at a glance (F-09). The colours that are
 * not about being on (off, disabled) are Material's own.
 */
@Composable
fun accentChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer, selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

/** A chip that is on has a 1 dp outline in the accent; one that is off keeps Material's. */
@Composable
fun accentChipBorder(enabled: Boolean, selected: Boolean): BorderStroke? =
    FilterChipDefaults.filterChipBorder(enabled = enabled, selected = selected, selectedBorderColor = MaterialTheme.colorScheme.primary, selectedBorderWidth = 1.dp)

/**
 * The app's one filter chip, the same call as Material's: a chip that is on is an accent container with a bold label and, when it has no icon of its own (a category's avatar, a removable chip's cross), a
 * check mark, so on and off differ by more than a shade (WCAG 1.4.1). Every screen that used `androidx.compose.material3.FilterChip` now uses this one (same package, no import), so they all agree.
 */
@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: Shape = FilterChipDefaults.shape,
    colors: SelectableChipColors = accentChipColors(),
    elevation: SelectableChipElevation? = FilterChipDefaults.filterChipElevation(),
    border: BorderStroke? = accentChipBorder(enabled, selected),
    interactionSource: MutableInteractionSource? = null,
) {
    androidx.compose.material3.FilterChip(
        selected = selected, onClick = onClick, modifier = modifier, enabled = enabled,
        label = { CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontWeight = if (selected) FontWeight.Bold else LocalTextStyle.current.fontWeight)) { label() } },
        leadingIcon = leadingIcon ?: if (selected && trailingIcon == null) ({ Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }) else null,
        trailingIcon = trailingIcon, shape = shape, colors = colors, elevation = elevation, border = border, interactionSource = interactionSource,
    )
}

package app.expenses.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A chip you tap to use and press and hold to manage. Material's own chips handle taps themselves and never pass a long press on to a
 * wrapper, so this one is built on `combinedClickable` directly. The long press is also offered to accessibility services.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LongPressChip(label: String, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier, leading: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        shape = shape, color = Color.Transparent, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.defaultMinSize(minHeight = 40.dp).clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button)
            .semantics { onLongClick(label = "Remove") { onLongClick(); true } },
    ) {
        androidx.compose.foundation.layout.Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (leading != null) androidx.compose.material3.Icon(leading, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 6.dp).size(16.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

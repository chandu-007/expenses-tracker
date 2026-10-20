package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** The five areas of the More page. Each has its own colour, shared by the rows that open its screens, so the app tells where you are at a glance. */
enum class Area(val top: Color, val bottom: Color) {
    MONEY(Color(0xFF1E88E5), Color(0xFF1565C0)),
    CAPTURE(Color(0xFF00897B), Color(0xFF00695C)),
    LOOK(Color(0xFF8E24AA), Color(0xFF6A1B9A)),
    PRIVACY(Color(0xFF43A047), Color(0xFF2E7D32)),
    HELP(Color(0xFFFB8C00), Color(0xFFEF6C00)),
}

/** The top bar of a screen opened from More: the area's icon tile and colour wash beside the title, the same Back button (with its [backTag]) as before. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreaTopBar(title: String, area: Area, icon: ImageVector, onBack: () -> Unit, backTag: String, backLabel: String = "Back", actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(area.top, area.bottom))), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                // a long title ("Privacy: what this app can see") takes a smaller size and a second line rather than being cut off
                Text(title, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = if (title.length > 22) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge)
            }
        },
        navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.testTag(backTag)) { Text(backLabel) } },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = area.top.copy(alpha = 0.10f).compositeOver(MaterialTheme.colorScheme.background)),
    )
}

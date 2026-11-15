package app.expenses.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag

/** The five areas of the More page. Each has its own colour, shared by the rows that open its screens, so the app tells where you are at a glance. */
enum class Area(val top: Color, val bottom: Color) {
    MONEY(Color(0xFF1E88E5), Color(0xFF1565C0)),
    CAPTURE(Color(0xFF00897B), Color(0xFF00695C)),
    LOOK(Color(0xFF8E24AA), Color(0xFF6A1B9A)),
    PRIVACY(Color(0xFF43A047), Color(0xFF2E7D32)),
    HELP(Color(0xFFFB8C00), Color(0xFFEF6C00)),
}

/**
 * THE header of every screen that is not a tab (UI S1): a back arrow (48 dp, with its [backTag]; [backLabel] is what a screen reader says: "Back", "Close", "Cancel"), the title and at most two actions on the right. No icon
 * tile, no area colour wash, no "Back" in words: one look on all 40 screens. A long title ("Privacy: what this app can see") takes a smaller size and a second line rather than being cut off. Like the bottom bar its text
 * stops growing at 130% of the phone's text size (the page below it goes on to 200%): at 200% a title beside a word action ("Categories" and "Suggested") has no room, and the bar is chrome, not content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, backTag: String, backLabel: String = "Back", actions: @Composable RowScope.() -> Unit = {}) {
    CappedFontScale(HEADER_TEXT_SCALE) {
        TopAppBar(
            title = { Text(title, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = if (title.length > 22) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag(backTag)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = backLabel) }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
    }
}

/** The largest text scale the header draws at (the phone's setting still applies to everything below it). */
const val HEADER_TEXT_SCALE = 1.3f

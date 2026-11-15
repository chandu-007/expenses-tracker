package app.expenses.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.expenses.core.domain.DesignTokens

/**
 * The app's design tokens (UI S1): a delta on the Material 3 theme, not a rewrite. Spacing steps on a 4 dp grid, four corner radii and the pill, three elevations, one sheet. The numbers live in
 * [DesignTokens] (core, tested); this file makes them `Dp` and shapes. A new screen uses these and never invents a radius or a gap.
 */
object Space {
    val XS: Dp = 4.dp
    val S: Dp = 8.dp
    val M: Dp = 12.dp
    val L: Dp = 16.dp
    val XL: Dp = 20.dp
    val XXL: Dp = 24.dp
    val SECTION: Dp = 32.dp
}

/** Corner radii: [XS] bars and inner chips, [S] tiles, inputs and chips, [M] cards, rows, nudges and pills of a bar, [L] hero cards, sheets and the wallet cards; [Full] pills and avatars. */
object Radius {
    val XS = RoundedCornerShape(DesignTokens.RADIUS_XS.dp)
    val S = RoundedCornerShape(DesignTokens.RADIUS_S.dp)
    val M = RoundedCornerShape(DesignTokens.RADIUS_M.dp)
    val L = RoundedCornerShape(DesignTokens.RADIUS_L.dp)
    val Full = RoundedCornerShape(50)
    val SheetTop = RoundedCornerShape(topStart = DesignTokens.RADIUS_L.dp, topEnd = DesignTokens.RADIUS_L.dp)
}

/** Elevation: 0 a flat tinted card (never a shadow on a list row), 1 chrome (a tonal 3 dp: the bar, headers), 2 a held object (an 8 dp shadow: wallet cards, a dragged row). */
object Elevation {
    val Flat: Dp = 0.dp
    val Chrome: Dp = 3.dp
    val Held: Dp = 8.dp
}

/** One sheet: 28 dp top corners, a handle, a 32% scrim, slide and fade (Filters, the pickers, the Add form). */
object Sheet {
    val Shape = Radius.SheetTop
    val Scrim: Color = Color.Black.copy(alpha = DesignTokens.SCRIM_PERCENT / 100f)
}

/** The Material shapes with the app's radii, so a dialog, a card, a text field or a snackbar that takes its shape from the theme is one of the four steps. */
fun appShapes() = Shapes(
    extraSmall = RoundedCornerShape(DesignTokens.RADIUS_XS.dp),
    small = RoundedCornerShape(DesignTokens.RADIUS_XS.dp),
    medium = RoundedCornerShape(DesignTokens.RADIUS_S.dp),
    large = RoundedCornerShape(DesignTokens.RADIUS_M.dp),
    extraLarge = RoundedCornerShape(DesignTokens.RADIUS_L.dp),
)

/** The grabber at the top of a sheet: 32 by 4 dp, decorative (it says nothing to a screen reader; the sheet has its own Close). */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(top = Space.S), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 32.dp, height = 4.dp).clip(Radius.Full).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
    }
}

package app.expenses.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * How far the top of the home screen (the month's total and the account cards) has folded away: 0 is fully open, 1 is as small as it gets. It follows the
 * finger: scrolling the entries up folds it, and scrolling back to the top opens it again, so the list gets the room while it is being read and the figures
 * return when you are back at the start. It is direct manipulation, not an animation, so it is the same under every motion setting.
 */
@Stable
class HeaderFold {
    var fraction by mutableFloatStateOf(0f)

    /** Folds by up to [distancePx] of finger travel, before the list moves; unfolds only with what the list had left over at its very top. */
    fun connection(distancePx: Float) = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y >= 0f || fraction >= 1f) return Offset.Zero
            val next = (fraction + (-available.y) / distancePx).coerceAtMost(1f)
            val used = (next - fraction) * distancePx
            fraction = next
            return Offset(0f, -used)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (available.y <= 0f || fraction <= 0f) return Offset.Zero
            val next = (fraction - available.y / distancePx).coerceAtLeast(0f)
            val used = (fraction - next) * distancePx
            fraction = next
            return Offset(0f, used)
        }
    }
}

/** Takes less and less room as [fold] closes, fading as it goes; its content keeps its size and is cut off from the bottom, as a drawer closing would. */
fun Modifier.foldAway(fold: HeaderFold): Modifier = this
    .clipToBounds()
    .layout { measurable, constraints ->
        val p = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        val h = (p.height * (1f - fold.fraction)).roundToInt()
        layout(p.width, h) { p.placeRelative(0, 0) }
    }
    .graphicsLayer { alpha = (1f - fold.fraction * 1.4f).coerceIn(0f, 1f) }

package app.expenses.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.ui.composed
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * THE scrubber of every chart (UI S4): a finger dragged sideways over [count] marks reports the mark under it through [onScrub] (once per mark, at the start and each time it changes; the caller
 * gives the felt tick), [onEnd] when the finger lifts or the drag is taken away, and a tap reports the mark under it through [onTap] (a bar opens its period). [indexAt] turns an x position and the
 * width into a mark: the default is equal slots (bars), a line passes its own spacing. Nothing here animates, so Reduce motion has nothing to switch off; it reads no state, so a drag recomposes
 * only what the caller shows (the bubble) and redraws what the caller draws.
 */
fun Modifier.chartScrub(
    count: Int,
    onScrub: (Int) -> Unit,
    onEnd: () -> Unit,
    onTap: ((Int) -> Unit)? = null,
    indexAt: (x: Float, width: Float, count: Int) -> Int = ::slotIndex,
): Modifier = composed {
    val scrub by rememberUpdatedState(onScrub)
    val end by rememberUpdatedState(onEnd)
    val tap by rememberUpdatedState(onTap)
    val at by rememberUpdatedState(indexAt)
    Modifier
        .pointerInput(count) { if (tap != null) detectTapGestures { tap?.invoke(at(it.x, size.width.toFloat(), count)) } }
        .pointerInput(count) {
            var last = -1
            detectHorizontalDragGestures(
                onDragStart = { p -> last = at(p.x, size.width.toFloat(), count); scrub(last) },
                onDragEnd = { last = -1; end() },
                onDragCancel = { last = -1; end() },
                onHorizontalDrag = { change, _ ->
                    val i = at(change.position.x, size.width.toFloat(), count)
                    if (i != last) { last = i; scrub(i) }
                    change.consume()
                },
            )
        }
}

/** Equal slots, as bars sit: the slot the x position falls in. */
fun slotIndex(x: Float, width: Float, count: Int): Int = if (count <= 1 || width <= 0f) 0 else ((x / width) * count).toInt().coerceIn(0, count - 1)

/** Marks on a line, the first at the left edge and the last at the right: the nearest one. */
fun pointIndex(x: Float, width: Float, count: Int): Int = if (count <= 1 || width <= 0f) 0 else roundHalfUp((x / width) * (count - 1)).coerceIn(0, count - 1)

private fun roundHalfUp(v: Float): Int = kotlin.math.floor(v + 0.5f).toInt()

package app.expenses.ui

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.composed

/**
 * Where on the screen the controls the tour can point at are right now. A screen marks a control with [tourSpot]; the tour reads the rectangle to cut a
 * window in its dimmed layer. Positions are in screen coordinates, so the tour's own window can use them whatever its offset.
 */
object TourSpots {
    val bounds = mutableStateMapOf<String, Rect>()
}

/** Marks this control as one the tour can highlight under [id]. Costs one position callback; the entry goes away when the control leaves the screen. */
fun Modifier.tourSpot(id: String): Modifier = composed {
    DisposableEffect(id) { onDispose { TourSpots.bounds.remove(id) } }
    onGloballyPositioned { c -> if (c.isAttached) TourSpots.bounds[id] = Rect(c.positionOnScreen(), Size(c.size.width.toFloat(), c.size.height.toFloat())) }
}

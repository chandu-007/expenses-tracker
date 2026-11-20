package app.expenses.ui

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/*
 * UI S9 (U-24): a mouse, a trackpad and a keyboard on a large window (a tablet, a foldable with a keyboard, DeX, an iPad-sized window), with Compose's own pointer and focus APIs only.
 * - Hover: every row and button that is clickable draws Material's hover state layer by itself (the ripple indication handles hover as well as press), so nothing is added for it.
 * - Right-click = long press ([secondaryClick]): the same action, on the same rows and chips that have a long press (select an entry, remove a quick entry or a saved search).
 * - Focus ring ([focusRing]): a 2 dp ring in the accent around what has the keyboard's focus (Tab, the arrow keys), drawn only while focused; touch never focuses these.
 */

/** True on a two-pane window, where a mouse, a trackpad or a keyboard is likely: only there do rows and chips carry [secondaryClick] and [focusRing] (a phone's list does no extra work for them). */
val LocalLargeWindow = androidx.compose.runtime.compositionLocalOf { false }

/**
 * A press of the mouse's secondary (right) button does [onSecondary], as a long press does. It is caught first and consumed, so the row's own click does not also run; touches and the primary button
 * pass through untouched (a touch has no secondary button).
 */
fun Modifier.secondaryClick(onSecondary: () -> Unit): Modifier = composed {
    val action by rememberUpdatedState(onSecondary)
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                    event.changes.forEach { it.consume() }
                    action()
                }
            }
        }
    }
}

/** A visible ring around this element while it has the keyboard's focus. Put it before the clickable it rings, so it sees that element's focus. */
fun Modifier.focusRing(shape: Shape = Radius.M): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val ring = MaterialTheme.colorScheme.primary
    this.onFocusEvent { focused = it.isFocused || it.hasFocus }.then(if (focused) Modifier.border(2.dp, ring, shape) else Modifier)
}

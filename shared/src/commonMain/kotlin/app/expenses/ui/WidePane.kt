package app.expenses.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.expenses.Editable
import app.expenses.core.domain.WindowLayout

/*
 * Two panes on a wide window (a tablet, an unfolded foldable, a landscape tablet, a big split-screen or free-form window): Home on the left, the entry being opened on the
 * right. Every other screen keeps one pane, centred at most [WIDE_SCREEN_MAX_WIDTH] wide. In a window that is too small for two panes ([WindowLayout]: under 600 dp wide or
 * under 480 dp high: every phone, a phone on its side, the cover screen of a foldable, half of a split screen) nothing here does anything: [WideFrame] composes its content
 * directly, so those windows are laid out exactly as on a phone.
 *
 * The decision is made from the WINDOW's size in dp, again at every change of it (fold, unfold, rotation, split-screen, resize), and is a pure function in `core/domain`
 * ([WindowLayout.of]) with its tests. It was 840 dp wide at first, which no book-style foldable reaches (Galaxy Z Fold 5 opened: 690 x 829 dp).
 *
 * NOT VERIFIED ON REAL HARDWARE: the sizes are those of the published screens and of emulators set to them (`wm size` / `wm density`), not of a real Galaxy Z Fold.
 */

/** The widest the two-pane layout is drawn. Wider windows get margins either side. */
val WIDE_PANES_MAX_WIDTH: Dp = 1280.dp

/** The widest a single-pane screen is drawn on a wide window (More pages, reports, search, accounts...): they read fine wide, but not at 1280 dp. */
val WIDE_SCREEN_MAX_WIDTH: Dp = 960.dp

/**
 * Lets a test pretend the window is [widthDp] wide and [heightDp] high (null, the default, means the real window; a null height is the real one even when the width is
 * forced). The Compose tests launch the real activity on a phone-sized emulator and cannot resize its display, so they set this to see the two panes: the layout is then
 * computed for the real (narrow) width, squeezed but complete, and only the decision to use two panes follows the forced size. Public because the tests live in another
 * module; the app never sets it.
 */
object WideLayoutTestHook {
    var widthDp: Int? by mutableStateOf<Int?>(null)
    var heightDp: Int? by mutableStateOf<Int?>(null)
    /** A fold to pretend the window has (null: the real one, from [LocalHinge]). */
    var hinge: Hinge? by mutableStateOf<Hinge?>(null)
}

/**
 * A fold that separates the window into two parts (a foldable half open: like a book with the fold running top to bottom, or in table-top posture with it running across), in dp of the window
 * ([left], [top], [right], [bottom]). Flat and closed folds are not given: only the one that splits what is drawn.
 */
class Hinge(val left: Float, val top: Float, val right: Float, val bottom: Float, val vertical: Boolean) {
    val widthDp get() = right - left
}

/** The fold now in the window, if any; given by the platform (Android: the window layout info of the activity). Nothing on a phone, a tablet or iOS. */
val LocalHinge = androidx.compose.runtime.compositionLocalOf<Hinge?> { null }

@Composable
private fun currentHinge(): Hinge? = WideLayoutTestHook.hinge ?: LocalHinge.current

/** The root of the app, no taller than the part of the window above a fold that runs across it (table-top posture): the lower half is the table side. Otherwise the whole height. */
@Composable
fun Modifier.aboveHinge(): Modifier {
    val hinge = currentHinge()
    return if (hinge != null && !hinge.vertical && hinge.top > 0f) height(hinge.top.dp) else fillMaxHeight()
}

/**
 * The window's size in dp as the platform's own configuration reports it, or null where [LocalWindowInfo] is to be used (iOS). Android: `screenWidthDp` / `screenHeightDp`, which follow the
 * window on every Android version: on Android 8 `LocalWindowInfo.containerSize` stayed at the size the window had when it opened after a live resize (a foldable unfolding, a DeX window being dragged),
 * so the layout never changed (seen on the API 26 emulator).
 */
@Composable
internal expect fun platformWindowSizeDp(): androidx.compose.ui.unit.DpSize?

/** True while the window is big enough for two panes ([WindowLayout.of]). Only a change of the answer recomposes the caller, not every pixel of a window resize. */
@Composable
fun rememberIsWide(): Boolean {
    val info = LocalWindowInfo.current
    val density = LocalDensity.current
    val hinge = currentHinge()
    val platformSize = platformWindowSizeDp()
    val wide by remember(info, density, hinge, platformSize) {
        derivedStateOf {
            val width = WideLayoutTestHook.widthDp?.toFloat() ?: platformSize?.width?.value ?: (info.containerSize.width / density.density)
            val height = WideLayoutTestHook.heightDp?.toFloat() ?: platformSize?.height?.value ?: (info.containerSize.height / density.density)
            WindowLayout.of(width, WindowLayout.usableHeightDp(height, hinge?.takeIf { !it.vertical }?.top)) == WindowLayout.TWO_PANE
        }
    }
    return wide
}

private class HeldEditable(var value: Editable?)

/**
 * The frame every screen of the app host is drawn in. [key] is the screen being drawn ([ScreenKeys]); [editing] is the entry being opened, if any, and
 * [editor] draws it.
 * - not [wide]: [content] as it is, in the same place of the composition as when wide (only modifiers differ, so a window that changes size does not rebuild the screen);
 * - [wide], on Home: [content] (Home) in a list-sized pane, and the entry editor, or a placeholder, in the pane beside it; while the More page is
 *   showing ([moreOpen]) and no entry is open, there is nothing to put beside it, so it takes the window as one centred page, at most [WIDE_SCREEN_MAX_WIDTH] wide;
 * - [wide], on Search: the same as Home: the results in the list pane, the entry (or the placeholder) beside them;
 * - [wide], any other screen: [content] centred, at most [WIDE_SCREEN_MAX_WIDTH] wide.
 */
@Composable
fun WideFrame(wide: Boolean, key: Int, editing: Editable?, editor: @Composable (Editable) -> Unit, moreOpen: Boolean = false, content: @Composable () -> Unit) {
    // [content] sits in the SAME place of the composition whether the window is wide or not (the first Box of the same Row, or the inner Box of the same two Boxes): only
    // modifiers change. So when the window changes size (a foldable unfolded or folded, a split screen resized) the screen is not rebuilt: its scroll position, typed text and
    // selection stay. (Moving it between different places would start it again, and Compose's own "movable content" crashed with a focused text field.)
    // Home and Search are the two screens that have an entry beside them; every other screen is one centred page
    if (key != ScreenKeys.HOME && key != ScreenKeys.SEARCH) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(if (wide) Modifier.widthIn(max = WIDE_SCREEN_MAX_WIDTH).fillMaxWidth().fillMaxHeight() else Modifier.fillMaxSize()) { content() }
        }
        return
    }
    // the More page with no entry open: one centred page, not a list-sized strip beside an empty "Select an entry". [content] stays the first child of the
    // Row in every case, so Home keeps its scroll position and selection when More is opened and closed. (More belongs to Home: Search has no such page.)
    val alone = wide && key == ScreenKeys.HOME && moreOpen && editing == null
    val hinge = currentHinge()?.takeIf { it.vertical }
    Row(Modifier.fillMaxSize(), horizontalArrangement = if (wide) Arrangement.Center else Arrangement.Start) {
        Box(Modifier.then(if (wide) Modifier.listPaneWidth(alone, hinge).testTag("wide_list_pane") else Modifier.fillMaxWidth()).fillMaxHeight()) { content() }
        if (wide && !alone) {
            // a fold between the panes (half open like a book): the list on one side, the entry on the other, nothing drawn across it
            if (hinge != null && hinge.widthDp > 0f) androidx.compose.foundation.layout.Spacer(Modifier.width(hinge.widthDp.dp))
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.weight(1f).fillMaxHeight().testTag("wide_detail_pane")) { DetailPane(editing, editor) }
        }
    }
}

/** The list pane's width inside the pane area it is measured in ([WindowLayout.listPaneWidthDp]), or the whole page's ([alone]: at most [WIDE_SCREEN_MAX_WIDTH]). No subcomposition, unlike BoxWithConstraints. */
private fun Modifier.listPaneWidth(alone: Boolean, hinge: Hinge?): Modifier = layout { measurable, constraints ->
    val area = constraints.maxWidth
    val areaDp = area / density
    // the fold counts only where the panes fill the window (it is a window position): not in a window wider than the panes are drawn
    val hingeLeft = hinge?.takeIf { it.right <= areaDp + 0.5f }?.left
    val width = if (alone) minOf(area, WIDE_SCREEN_MAX_WIDTH.roundToPx()) else WindowLayout.listPaneWidthDp(areaDp, hingeLeft, hinge?.widthDp ?: 0f).dp.roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(width, placeable.height) { placeable.place(0, 0) }
}

/** The right-hand pane: the editor of [editing] fading in, or the calm placeholder while nothing is open. */
@Composable
private fun DetailPane(editing: Editable?, editor: @Composable (Editable) -> Unit) {
    val reduce = LocalReduceMotion.current
    // the editor fades out after [editing] is already null, so it keeps drawing the last entry for that moment (as the full-screen editor does)
    val held = remember { HeldEditable(editing) }
    if (editing != null) held.value = editing
    if (editing == null) WidePlaceholder()
    AnimatedVisibility(
        visible = editing != null,
        enter = fadeIn(Motion.tween<Float>(reduce, Motion.TAB)),
        exit = fadeOut(Motion.tween<Float>(reduce, Motion.CHIP)),
    ) {
        val leaving = transition.targetState == EnterExitState.PostExit
        // Home's row and the editor carry the same shared-container key; with both drawn at once that would be two claims on one element. The pane has its own fade instead.
        CompositionLocalProvider(LocalSharedTransitionScope provides null, LocalAnimatedVisibilityScope provides null, LocalLeavingScreen provides leaving) {
            Box(Modifier.fillMaxSize().then(if (leaving) Modifier.clearAndSetSemantics { } else Modifier)) {
                held.value?.let { e ->
                    // a different entry is a new editor, never the old one with its typed state
                    val identity = when (e) { is Editable.Form -> listOf(e.draft.id, e.draft.kind, e.draft.refundsEntry); is Editable.ReadOnly -> listOf(e.id) }
                    androidx.compose.runtime.key(identity) { editor(e) }
                }
            }
        }
    }
}

@Composable
private fun WidePlaceholder() {
    Box(Modifier.fillMaxSize().testTag("wide_placeholder"), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EmptyArt(EmptyKind.GENERIC, Modifier.size(120.dp))
            Text("Select an entry", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
        }
    }
}

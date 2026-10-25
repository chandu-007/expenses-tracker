package app.expenses.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.expenses.Editable

/**
 * Two panes on a wide window (a tablet, an unfolded foldable, a landscape tablet): Home on the left, the entry being opened on the right. Every other
 * screen keeps one pane, centred at most [WIDE_SCREEN_MAX_WIDTH] wide. Below [WIDE_BREAKPOINT_DP] (every phone, a small tablet held upright) nothing here
 * does anything: [WideFrame] composes its content directly, so those windows are laid out exactly as before.
 *
 * ASSUMED, NOT VERIFIED ON REAL HARDWARE: the widths below are judgement values (a 10" tablet in landscape is about 1280 dp, an unfolded foldable
 * about 840 to 900 dp); no tablet was available when this was written.
 */
const val WIDE_BREAKPOINT_DP = 840

/** The widest the two-pane layout is drawn. Wider windows get margins either side. */
val WIDE_PANES_MAX_WIDTH: Dp = 1280.dp

/** The widest a single-pane screen is drawn on a wide window (More pages, reports, search, accounts...): they read fine wide, but not at 1280 dp. */
val WIDE_SCREEN_MAX_WIDTH: Dp = 960.dp

private val LIST_PANE_MIN = 400.dp
private val LIST_PANE_MAX = 480.dp

/**
 * Lets a test pretend the window is [widthDp] wide (null, the default, means the real window). The Compose tests launch the real activity on a phone-sized
 * emulator and cannot resize its display, so they set this to see the two panes: the layout is then computed for the real (narrow) width, squeezed but
 * complete, and only the decision to use two panes follows the forced width. Public because the tests live in another module; the app never sets it.
 */
object WideLayoutTestHook {
    var widthDp: Int? by mutableStateOf<Int?>(null)
}

/** True while the window is at least [WIDE_BREAKPOINT_DP] wide. Only a change of the answer recomposes the caller, not every pixel of a window resize. */
@Composable
fun rememberIsWide(): Boolean {
    val info = LocalWindowInfo.current
    val density = LocalDensity.current
    val wide by remember(info, density) {
        derivedStateOf {
            val width = WideLayoutTestHook.widthDp ?: (info.containerSize.width / density.density).toInt()
            width >= WIDE_BREAKPOINT_DP
        }
    }
    return wide
}

private class HeldEditable(var value: Editable?)

/**
 * The frame every screen of the app host is drawn in. [key] is the screen being drawn ([ScreenKeys]); [editing] is the entry being opened, if any, and
 * [editor] draws it.
 * - not [wide]: [content] as it is, nothing around it;
 * - [wide], on Home: [content] (Home) in a list-sized pane, and the entry editor, or a placeholder, in the pane beside it;
 * - [wide], any other screen: [content] centred, at most [WIDE_SCREEN_MAX_WIDTH] wide.
 */
@Composable
fun WideFrame(wide: Boolean, key: Int, editing: Editable?, editor: @Composable (Editable) -> Unit, content: @Composable () -> Unit) {
    if (!wide) { content(); return }
    if (key != ScreenKeys.HOME) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = WIDE_SCREEN_MAX_WIDTH).fillMaxWidth().fillMaxHeight()) { content() }
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val listWidth = (maxWidth * 0.42f).coerceIn(minOf(LIST_PANE_MIN, maxWidth / 2), LIST_PANE_MAX)
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(listWidth).fillMaxHeight().testTag("wide_list_pane")) { content() }
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.weight(1f).fillMaxHeight().testTag("wide_detail_pane")) { DetailPane(editing, editor) }
        }
    }
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
        enter = fadeIn(Motion.tween<Float>(reduce, Motion.STANDARD)),
        exit = fadeOut(Motion.tween<Float>(reduce, Motion.QUICK)),
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

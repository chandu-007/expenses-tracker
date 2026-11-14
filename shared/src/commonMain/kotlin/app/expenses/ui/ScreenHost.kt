package app.expenses.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics

/** True inside a screen that is leaving: it is still drawn for its exit, but must no longer answer the Back button. */
val LocalLeavingScreen = androidx.compose.runtime.compositionLocalOf { false }

/** The platform back handler (Android Back, iOS edge swipe) that stands down while its screen is animating out, so a Back press goes to the screen that is now showing. */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) = PlatformBackHandler(enabled && !LocalLeavingScreen.current, onBack)

private val TABS = setOf(ScreenKeys.HOME, ScreenKeys.SEARCH, 16)

/** The keys of the screens the host treats specially; every other screen is an ordinary push. */
object ScreenKeys {
    const val HOME = -1
    /** Search: on a two-pane window the results are in the list pane and the entry opened from them beside it, as on Home. */
    const val SEARCH = 14
    /** The edit form: it grows out of the row that was tapped. */
    const val EDIT_FORM = 1
    /** The add form: it rises over home like a sheet. */
    const val ADD_FORM = 21
}

/**
 * Shows the one screen for [screenKey] and moves between screens with the app's motion language (see [Motion]):
 * - opening a screen: it comes forward (a short scale and fade) over the one it covers;
 * - the add form: rises from the bottom over a home that settles back;
 * - the edit form: the tapped row grows into it (a container transform, through [sharedContainer]), and shrinks back on return;
 * - going back to home: the screen leaving shrinks away and home returns.
 * Under Reduce motion every change is a short cross-fade (nothing travels). The durations are the tiers of [Motion]: a tab change at most 250 ms, a screen pushed at most 400 ms, the add form at most 380 ms
 * (the keyboard rises with it: the amount takes focus at once), whatever the speed chosen under Appearance. While a screen is leaving it is still drawn but hidden from accessibility and the tests, so there is never
 * a second copy of a screen's controls to find.
 *
 * [content] is given the key it should draw: the leaving screen draws its own key, not the new one.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ScreenHost(screenKey: Int, content: @Composable (key: Int) -> Unit) {
    val reduce = LocalReduceMotion.current
    // the debug-only frame logger starts a measuring window at each change of screen (FrameProbe.onScreenChange is null in every build without one)
    val previousKey = androidx.compose.runtime.remember { intArrayOf(screenKey) }
    androidx.compose.runtime.LaunchedEffect(screenKey) {
        if (previousKey[0] != screenKey) { FrameProbe.onScreenChange?.invoke(previousKey[0], screenKey); previousKey[0] = screenKey }
    }
    SharedTransitionLayout {
        AnimatedContent(
            targetState = screenKey,
            modifier = Modifier.fillMaxSize(),
            label = "screen",
            transitionSpec = {
                val from = initialState; val to = targetState
                if (reduce) {
                    fadeIn(tween(Motion.REDUCED_MS)) togetherWith fadeOut(tween(Motion.REDUCED_MS))
                } else when {
                    from in TABS && to in TABS -> fadeIn(tween(Motion.TAB)) togetherWith fadeOut(tween(Motion.TAB)) // the bar stays put while the page changes
                    to == ScreenKeys.ADD_FORM ->
                        (slideInVertically(tween(Motion.ADD_FORM, easing = Motion.EaseOut)) { it } + fadeIn(tween(Motion.TAB))) togetherWith
                            (scaleOut(tween(Motion.ADD_FORM, easing = Motion.EaseOut), targetScale = 0.94f) + fadeOut(tween(Motion.ADD_FORM)))
                    from == ScreenKeys.ADD_FORM ->
                        (scaleIn(tween(Motion.ADD_FORM, easing = Motion.EaseOut), initialScale = 0.94f) + fadeIn(tween(Motion.ADD_FORM))) togetherWith
                            (slideOutVertically(tween(Motion.ADD_FORM, easing = Motion.EaseOut)) { it } + fadeOut(tween(Motion.ADD_FORM)))
                    to == ScreenKeys.EDIT_FORM || from == ScreenKeys.EDIT_FORM ->
                        fadeIn(tween(Motion.TAB)) togetherWith fadeOut(tween(Motion.TAB)) // the shared container carries the movement
                    to == ScreenKeys.HOME ->
                        (scaleIn(tween(Motion.PUSH, easing = Motion.EaseOut), initialScale = 1.04f) + fadeIn(tween(Motion.TAB))) togetherWith
                            (scaleOut(tween(Motion.TAB, easing = Motion.EaseIn), targetScale = 0.94f) + fadeOut(tween(Motion.TAB)))
                    else ->
                        (scaleIn(tween(Motion.PUSH, easing = Motion.EaseOut), initialScale = 0.94f) + fadeIn(tween(Motion.TAB))) togetherWith
                            fadeOut(tween(Motion.CHIP))
                }.also {
                    // the screen being opened is drawn above; the screen being left, on the way back, is drawn above
                    it.targetContentZIndex = if (to == ScreenKeys.HOME && from != ScreenKeys.EDIT_FORM) -1f else 1f
                }
            },
        ) { key ->
            val leaving = transition.targetState == EnterExitState.PostExit
            CompositionLocalProvider(LocalSharedTransitionScope provides this@SharedTransitionLayout, LocalAnimatedVisibilityScope provides this, LocalLeavingScreen provides leaving) {
                Box(Modifier.fillMaxSize().then(if (leaving) Modifier.clearAndSetSemantics { } else Modifier)) { content(key) }
            }
        }
    }
}

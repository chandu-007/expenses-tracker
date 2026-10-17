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

/** [androidx.activity.compose.BackHandler] that stands down while its screen is animating out, so a Back press goes to the screen that is now showing. */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) = androidx.activity.compose.BackHandler(enabled && !LocalLeavingScreen.current, onBack)

private val TABS = setOf(ScreenKeys.HOME, 14, 16)

/** The keys of the screens the host treats specially; every other screen is an ordinary push. */
object ScreenKeys {
    const val HOME = -1
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
 * Under Reduce motion every change is instant. While a screen is leaving it is still drawn but hidden from accessibility and the tests, so there is never
 * a second copy of a screen's controls to find.
 *
 * [content] is given the key it should draw: the leaving screen draws its own key, not the new one.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ScreenHost(screenKey: Int, content: @Composable (key: Int) -> Unit) {
    val reduce = LocalReduceMotion.current
    SharedTransitionLayout {
        AnimatedContent(
            targetState = screenKey,
            modifier = Modifier.fillMaxSize(),
            label = "screen",
            transitionSpec = {
                val from = initialState; val to = targetState
                if (reduce) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else when {
                    from in TABS && to in TABS -> fadeIn(tween(Motion.STANDARD)) togetherWith fadeOut(tween(Motion.STANDARD)) // the bar stays put while the page changes
                    to == ScreenKeys.ADD_FORM ->
                        (slideInVertically(tween(Motion.GLIDE, easing = Motion.EaseOut)) { it } + fadeIn(tween(Motion.STANDARD))) togetherWith
                            (scaleOut(tween(Motion.GLIDE, easing = Motion.EaseOut), targetScale = 0.94f) + fadeOut(tween(Motion.GLIDE)))
                    from == ScreenKeys.ADD_FORM ->
                        (scaleIn(tween(Motion.GLIDE, easing = Motion.EaseOut), initialScale = 0.94f) + fadeIn(tween(Motion.GLIDE))) togetherWith
                            (slideOutVertically(tween(Motion.GLIDE, easing = Motion.EaseOut)) { it } + fadeOut(tween(Motion.GLIDE)))
                    to == ScreenKeys.EDIT_FORM || from == ScreenKeys.EDIT_FORM ->
                        fadeIn(tween(Motion.STANDARD)) togetherWith fadeOut(tween(Motion.STANDARD)) // the shared container carries the movement
                    to == ScreenKeys.HOME ->
                        (scaleIn(tween(Motion.EMPHASIZED, easing = Motion.EaseOut), initialScale = 1.04f) + fadeIn(tween(Motion.STANDARD))) togetherWith
                            (scaleOut(tween(Motion.STANDARD, easing = Motion.EaseIn), targetScale = 0.94f) + fadeOut(tween(Motion.STANDARD)))
                    else ->
                        (scaleIn(tween(Motion.EMPHASIZED, easing = Motion.EaseOut), initialScale = 0.94f) + fadeIn(tween(Motion.STANDARD))) togetherWith
                            fadeOut(tween(Motion.QUICK))
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

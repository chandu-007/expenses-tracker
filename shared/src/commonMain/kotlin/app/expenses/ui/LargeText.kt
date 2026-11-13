package app.expenses.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** The text size (the phone's font scale, 1.0 = normal) from which Home draws its cut-down layout: no graph, no small lines, buttons under the words, accounts in one row. */
const val LARGE_TEXT_SCALE = 1.5f

/** True at [LARGE_TEXT_SCALE] and above. Follows the phone's setting (Android's font size, iPhone's text size). */
@Composable
fun isLargeText(): Boolean = LocalDensity.current.fontScale >= LARGE_TEXT_SCALE - 0.001f

/**
 * [content] with the text scale held at [max] at most (the layout dimensions are untouched). For the places where growing with the text only breaks the page: the big figure of the month card
 * (it stops growing at 150%: 54 sp of a 36 sp figure) and the labels of the bottom bar (130%).
 */
@Composable
fun CappedFontScale(max: Float, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, minOf(d.fontScale, max)), content = content)
}

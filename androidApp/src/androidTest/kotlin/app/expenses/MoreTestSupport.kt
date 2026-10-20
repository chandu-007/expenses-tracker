package app.expenses

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick

/** Shows the More page. The More button toggles, and the page stays open under a screen opened from it, so this taps only when the page is not already showing. */
fun ComposeTestRule.openMore() {
    fun has(tag: String) = onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    waitUntil(10_000) { has("more_menu") || has("more_page") }
    if (!has("more_page")) onNodeWithTag("more_menu", useUnmergedTree = true).performClick()
    waitUntil(10_000) { has("more_page") }
}

/** Leaves the More page for Home (the More button again), if it is showing. */
fun ComposeTestRule.closeMore() {
    fun has(tag: String) = onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    if (has("more_page")) onNodeWithTag("more_menu", useUnmergedTree = true).performClick()
    waitUntil(10_000) { !has("more_page") }
}

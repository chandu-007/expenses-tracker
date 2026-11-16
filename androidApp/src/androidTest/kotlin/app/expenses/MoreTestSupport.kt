package app.expenses

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo

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

private fun ComposeTestRule.hasTag(tag: String) = onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

/**
 * Opens the editor of an entry from its row on Home. Tapping an entry shows its detail first (UI S2) and Edit opens the form; a read-only entry and a refund have no detail and show their own screen.
 * Waits until the form, the read-only reason or the refund's form is showing.
 */
fun ComposeTestRule.openEntryEditor(rowTag: String) {
    waitUntil(10_000) { hasTag(rowTag) }
    runCatching { onNodeWithTag(rowTag, useUnmergedTree = true).performScrollTo() }
    onNodeWithTag(rowTag, useUnmergedTree = true).performClick()
    waitUntil(15_000) { hasTag("detail_edit") || hasTag("amount") || hasTag("read_only_reason") }
    if (hasTag("detail_edit")) {
        onNodeWithTag("detail_edit", useUnmergedTree = true).performClick()
        waitUntil(15_000) { hasTag("amount") }
    }
}

/** Opens the entry's detail from its row (no Edit). */
fun ComposeTestRule.openEntryDetail(rowTag: String) {
    waitUntil(10_000) { hasTag(rowTag) }
    runCatching { onNodeWithTag(rowTag, useUnmergedTree = true).performScrollTo() }
    onNodeWithTag(rowTag, useUnmergedTree = true).performClick()
    waitUntil(15_000) { hasTag("detail_edit") }
}

/** Opens the form's "More" options (split, another currency, friends, expect back, tags, receipts, quick entry) unless they are open already. */
fun ComposeTestRule.openFormMore() {
    waitUntil(10_000) { hasTag("form_more") }
    if (!hasTag("form_more_panel")) {
        runCatching { onNodeWithTag("form_more", useUnmergedTree = true).performScrollTo() }
        onNodeWithTag("form_more", useUnmergedTree = true).performClick()
    }
    waitUntil(10_000) { hasTag("form_more_panel") }
}

/** Chooses a category in the form: its tile when it is one of the tiles, else through "More". */
fun ComposeTestRule.pickCategory(id: String) {
    if (!hasTag("category_chip_$id")) {
        runCatching { onNodeWithTag("more_categories", useUnmergedTree = true).performScrollTo() }
        onNodeWithTag("more_categories", useUnmergedTree = true).performClick()
        waitUntil(10_000) { hasTag("category_option_$id") }
        onNodeWithTag("category_option_$id", useUnmergedTree = true).performClick()
        waitUntil(10_000) { !hasTag("category_option_$id") }
    } else {
        runCatching { onNodeWithTag("category_chip_$id", useUnmergedTree = true).performScrollTo() }
        onNodeWithTag("category_chip_$id", useUnmergedTree = true).performClick()
    }
}

/** After the view model was told to open an entry (not a tap on its row): shows the form, whether the detail came first (UI S2) or the entry has none (a read-only one, a refund). */
fun ComposeTestRule.continueToForm() {
    waitUntil(15_000) { hasTag("detail_edit") || hasTag("amount") || hasTag("read_only_reason") }
    if (hasTag("detail_edit")) {
        onNodeWithTag("detail_edit", useUnmergedTree = true).performClick()
        waitUntil(15_000) { hasTag("amount") }
    }
}

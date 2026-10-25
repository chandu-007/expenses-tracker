package app.expenses

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.expenses.core.domain.Currency
import app.expenses.ui.BillItemsChoice
import app.expenses.ui.BillItemsDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The dialog that lists the items read from a bill photo. It is shown here with the recognised text given directly, so no photo and no recogniser are involved
 * (the reading itself is covered by BillItemsTest in core/domain, on invented bills). Written to run on a device; not run when written.
 */
@RunWith(AndroidJUnit4::class)
class BillItemsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    // an invented bill
    private val bill = "GREEN LEAF MART\nMilk 500ml 27.00\nBrown Bread 40.00\nEggs (6) 54.00\nSub Total 121.00\nGST 5% 6.00\nTOTAL 127.00\nCash 200.00\nChange 73.00"

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun textOf(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }
    private fun tap(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).performClick()

    private var applied: BillItemsChoice? = null
    private var closed = false
    private fun show(text: String, willSetAmount: Boolean = true) {
        compose.setContent { MaterialTheme { BillItemsDialog(text, willSetAmount, photoUri = "content://photo/1", onClose = { closed = true }, onApply = { applied = it }, currency = Currency.INR) } }
    }

    @Test
    fun itemsAreListedAllTickedAndTheAmountToUseIsTheTotalOnTheBill() {
        show(bill)
        compose.waitUntil(10_000) { has("bill_items_list") }
        for (i in 0..2) assertTrue(has("bill_item_$i"), "item $i is listed")
        assertTrue(!has("bill_item_3"), "tax, totals and payment lines are not items")
        assertTrue(textOf("bill_items_printed_total").contains("₹127.00"), textOf("bill_items_printed_total"))
        assertTrue(textOf("bill_items_total").contains("₹127.00"), "every line used: the printed total, which includes the tax: ${textOf("bill_items_total")}")
        tap("bill_items_apply")
        val choice = applied!!
        assertEquals(listOf("Milk 500ml", "Brown Bread", "Eggs (6)"), choice.items.map { it.name })
        assertEquals(12_700, choice.amountMinor)
        assertEquals("content://photo/1", choice.photoUri)
    }

    @Test
    fun untickingAnItemRemovesItFromTheTotalAndTheChoice() {
        show(bill)
        compose.waitUntil(10_000) { has("bill_item_1") }
        tap("bill_item_1") // Brown Bread
        compose.waitUntil(10_000) { textOf("bill_items_total").contains("₹81.00") }  // 27.00 + 54.00
        tap("bill_items_apply")
        val choice = applied!!
        assertEquals(listOf("Milk 500ml", "Eggs (6)"), choice.items.map { it.name })
        assertEquals(8_100, choice.amountMinor)
    }

    @Test
    fun withNothingTickedThereIsNothingToApply() {
        show(bill)
        compose.waitUntil(10_000) { has("bill_item_0") }
        for (i in 0..2) tap("bill_item_$i")
        compose.waitUntil(10_000) { textOf("bill_items_total").contains("₹0.00") }
        tap("bill_items_apply")
        compose.waitForIdle()
        assertNull(applied, "Use these is disabled when no item is ticked")
        tap("bill_items_close"); compose.waitUntil(10_000) { closed }
    }

    @Test
    fun aTypedAmountIsSaidToStay() {
        show(bill, willSetAmount = false)
        compose.waitUntil(10_000) { has("bill_items_total") }
        compose.onNodeWithTag("bill_items_total", useUnmergedTree = true).assertExists()
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("The amount you already typed stays.", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun aPhotoWithNothingReadableSaysSoPlainlyAndOnlyOffersClose() {
        show("smudge\n????\n   ")
        compose.waitUntil(10_000) { has("bill_items_none") }
        assertTrue(!has("bill_items_apply") && !has("bill_items_list"))
        tap("bill_items_close"); compose.waitUntil(10_000) { closed }
        assertNull(applied)
    }

    @Test
    fun aBillWithOnlyATotalCanStillSetTheAmount() {
        show("TOTAL 250.00")
        compose.waitUntil(10_000) { has("bill_items_total_only") }
        assertTrue(textOf("bill_items_total").contains("₹250.00"))
        tap("bill_items_apply")
        assertEquals(25_000, applied!!.amountMinor); assertTrue(applied!!.items.isEmpty())
    }
}

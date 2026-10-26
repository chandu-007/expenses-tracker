package app.expenses

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
    private var copies = 0
    private fun show(text: String, willSetAmount: Boolean = true, canAddEach: Boolean = false, eachNotice: String? = null) {
        compose.setContent {
            MaterialTheme {
                BillItemsDialog(text, willSetAmount, photoUri = "content://photo/1", onClose = { closed = true }, onApply = { applied = it }, currency = Currency.INR, canAddEach = canAddEach, eachNotice = eachNotice, onCopyText = { copies++; true })
            }
        }
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

    @Test
    fun eachItemAsItsOwnEntryIsOfferedWithTheTaxAsOneMoreAndChosenWithTheRest() {
        show(bill, canAddEach = true)
        compose.waitUntil(10_000) { has("bill_items_mode_each") }
        assertTrue(has("bill_items_mode_one") && !has("bill_items_rest"), "the tax line is offered only once each item is chosen")
        tap("bill_items_mode_each")
        compose.waitUntil(10_000) { has("bill_items_rest") }
        assertTrue(textOf("bill_items_total").contains("Entries to add: 4") && textOf("bill_items_total").contains("₹127.00"), textOf("bill_items_total"))
        tap("bill_items_apply")
        val choice = applied!!
        assertTrue(choice.eachEntry)
        assertEquals(listOf("Milk 500ml", "Brown Bread", "Eggs (6)"), choice.items.map { it.name })
        assertEquals(600L, choice.restMinor, "127.00 on the bill less 121.00 of items")
    }

    @Test
    fun leavingTheTaxOutMakesOneEntryLessAndThePlainChoiceStaysOneEntry() {
        show(bill, canAddEach = true)
        compose.waitUntil(10_000) { has("bill_items_mode_each") }
        tap("bill_items_mode_each"); compose.waitUntil(10_000) { has("bill_items_rest") }
        tap("bill_items_rest")
        compose.waitUntil(10_000) { textOf("bill_items_total").contains("Entries to add: 3") }
        assertTrue(textOf("bill_items_total").contains("₹121.00"), textOf("bill_items_total"))
        tap("bill_items_apply")
        assertEquals(0L, applied!!.restMinor)
        applied = null
    }

    @Test
    fun theWholeBillIsTheDefaultEvenWhenEachItemIsOffered() {
        show(bill, canAddEach = true)
        compose.waitUntil(10_000) { has("bill_items_apply") }
        tap("bill_items_apply")
        assertTrue(!applied!!.eachEntry, "one entry for the whole bill unless the person chooses otherwise")
        applied = null; closed = false
    }

    @Test
    fun aReasonIsGivenInsteadOfTheChoiceWhenItemsCannotBeSavedOneByOne() {
        show(bill, canAddEach = false, eachNotice = "Choose a category and an account on the form first to save the items one by one.")
        compose.waitUntil(10_000) { has("bill_items_each_notice") }
        assertTrue(!has("bill_items_mode_each"))
    }

    @Test
    fun aMisreadLineCanBeCorrectedBeforeItIsUsed() {
        show(bill, canAddEach = true)
        compose.waitUntil(10_000) { has("bill_item_edit_1") }
        tap("bill_item_edit_1")
        compose.waitUntil(10_000) { has("bill_item_name_1") }
        compose.onNodeWithTag("bill_item_name_1", useUnmergedTree = true).performTextClearance()
        compose.onNodeWithTag("bill_item_name_1", useUnmergedTree = true).performTextInput("Whole Wheat Bread")
        compose.onNodeWithTag("bill_item_amount_1", useUnmergedTree = true).performTextClearance()
        compose.onNodeWithTag("bill_item_amount_1", useUnmergedTree = true).performTextInput("45.50")
        tap("bill_item_done_1")
        compose.waitUntil(10_000) { !has("bill_item_name_1") }
        compose.waitUntil(10_000) { textOf("bill_items_total").contains("₹127.00") } // the printed total still stands for the whole bill
        tap("bill_items_apply")
        val choice = applied!!
        assertEquals("Whole Wheat Bread", choice.items[1].name)
        assertEquals(4_550L, choice.items[1].amountMinor)
    }

    @Test
    fun theTextThatWasReadCanBeCopiedForABillThatReadsBadly() {
        show(bill)
        compose.waitUntil(10_000) { has("bill_items_copy") }
        tap("bill_items_copy")
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("Copied"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, copies)
    }

    @Test
    fun theTextCanBeCopiedWhenNothingWasReadToo() {
        show("smudge\n????\n   ")
        compose.waitUntil(10_000) { has("bill_items_copy") }
        tap("bill_items_copy")
        assertEquals(1, copies)
    }
}

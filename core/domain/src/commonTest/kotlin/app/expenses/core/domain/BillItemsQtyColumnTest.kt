package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A real restaurant bill (the text the iPhone's recogniser gave; shop and person details left out) whose heading line "Total Qty." made the reader note a
 * quantity column. In the iPhone app that note, kept in a nullable Boolean, crashed the app (Kotlin/Native ThrowInvalidReceiverTypeException) when the bill was
 * read. This test passes on the old code in the native unit-test runner, so it does not prove the crash; the crash and its end were seen with the app's own
 * framework in the simulator. It still guards the reading of the bill.
 */
class BillItemsQtyColumnTest {
    private val bill = """Piot No: 8,7,6 Svy no: 351 PEDDA AMBERPET
Date: 01/10/26 20:50 Dine In: G14
item Qly. Price Amount
Zafrani Chicken Biryani 1 349.00 349.00
(regular)
Water Bottle 28.50 28.50
Curd Rice 199.00 199.00
Sub
Total Qty. 3 Total 576.50
Round oft -0.32
Grand Total 605.00
Thank You VIsIt Agaln.!!"""

    @Test
    fun aBillWithATotalQtyLineIsReadWithoutFailing() {
        val read = BillItems.parse(bill)
        assertEquals(listOf("Zafrani Chicken Biryani", "Water Bottle", "Curd Rice"), read.items.map { it.name })
        assertEquals(listOf(34_900L, 2_850L, 19_900L), read.items.map { it.amountMinor })
        assertEquals(60_500L, read.printedTotalMinor)
    }
}

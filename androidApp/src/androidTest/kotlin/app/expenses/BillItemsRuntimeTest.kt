package app.expenses

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.expenses.core.domain.BillItems
import app.expenses.core.domain.Currency
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * The bill-photo reader's line parser on a real Android runtime. It used `MutableList.removeLast()`, which compiled against Android's newer library binds to the JDK 21 member
 * (`java.util.List.removeLast`, API 35): the Android host test on JDK 17 failed with NoSuchMethodError. This runs the same lines (a unit price left in the name, a percent, an operator) on whatever
 * Android the device has, so a crash on an older one would show here. The bills are invented.
 */
@RunWith(AndroidJUnit4::class)
class BillItemsRuntimeTest {
    @Test
    fun aUnitPriceAPercentAndAnOperatorAfterTheNameAreDroppedWithoutACrash() {
        val bill = BillItems.parse(
            """
            Milk 500ml   2 x 27.00   54.00
            Bread        1 x 40.00   40.00
            Soap 18%     30.00
            Pens         x3  10.00   30.00
            """.trimIndent(),
            Currency.INR,
        )
        assertTrue(bill.items.size >= 3, "the lines are read: ${bill.items.map { it.name }}")
    }
}

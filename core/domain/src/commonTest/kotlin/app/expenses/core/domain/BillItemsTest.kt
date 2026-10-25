package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The bills below are invented (shop names, items, prices): they follow how such bills commonly print, not any real receipt. */
class BillItemsTest {
    private fun items(text: String, currency: Currency = Currency.INR) = BillItems.parse(text, currency).items.map { Triple(it.name, it.amountMinor, it.quantity) }

    @Test fun plainGroceryBillWithTotalAndTax() {
        val bill = BillItems.parse(
            """
            GREEN LEAF MART
            12, Station Road, Anytown
            Ph: 98765 43210
            GSTIN: 29ABCDE1234F1Z5
            Bill No: 4471    Date: 12/10/2026 18:42
            Milk 500ml            27.00
            Brown Bread           40.00
            Eggs (6)              54.00
            Sub Total            121.00
            CGST 2.5%              3.03
            SGST 2.5%              3.03
            Round off             -0.06
            TOTAL                127.00
            Cash                 200.00
            Change                73.00
            Thank you! Visit again
            """.trimIndent(),
        )
        assertEquals(listOf("Milk 500ml", "Brown Bread", "Eggs (6)"), bill.items.map { it.name })
        assertEquals(listOf(2700L, 4000L, 5400L), bill.items.map { it.amountMinor })
        assertEquals(12700L, bill.printedTotalMinor)
        assertEquals(12100L, bill.itemsTotalMinor)
    }

    @Test fun rupeeSignsAndCommasAndRs() {
        val got = items(
            """
            Basmati Rice 5kg   ₹ 1,250.00
            Ghee 1L            Rs.640.50
            Sugar              Rs 48
            Tea 250g           INR 120/-
            Gold Ring Box      ₹1,23,456.00
            """.trimIndent(),
        )
        assertEquals(
            listOf(Triple("Basmati Rice 5kg", 125000L, 1), Triple("Ghee 1L", 64050L, 1), Triple("Sugar", 4800L, 1), Triple("Tea 250g", 12000L, 1), Triple("Gold Ring Box", 12345600L, 1)),
            got,
        )
    }

    @Test fun quantityTimesUnitPriceThenLineTotal() {
        val got = items(
            """
            Milk 500ml   2 x 27.00   54.00
            Bread        1 x 40.00   40.00
            Pens         x3  10.00   30.00
            """.trimIndent(),
        )
        assertEquals(listOf(Triple("Milk 500ml", 5400L, 2), Triple("Bread", 4000L, 1), Triple("Pens", 3000L, 3)), got)
    }

    @Test fun quantityAndUnitPriceWithoutLineTotalIsMultiplied() {
        assertEquals(listOf(Triple("Mango", 9000L, 3)), items("Mango 3 x 30.00"))
        assertEquals(listOf(Triple("Mango", 9000L, 3)), items("Mango 30.00 x 3"))
        assertEquals(listOf(Triple("Mango", 9000L, 3)), items("Mango 3x 30"))
    }

    @Test fun xQuantityGluedToNumber() {
        assertEquals(listOf(Triple("Samosa", 6000L, 2)), items("Samosa x2   60.00"))
        // a quantity marker straight before the one amount makes it a unit price
        assertEquals(listOf(Triple("Samosa", 12000L, 2)), items("Samosa 2x   60.00"))
    }

    @Test fun rateAndQuantityColumnsThatMultiply() {
        assertEquals(listOf(Triple("Notebook", 9000L, 3)), items("Notebook 30.00 3 90.00"))
        assertEquals(listOf(Triple("Notebook", 9000L, 3)), items("Notebook 3 30.00 90.00"))
    }

    @Test fun qtyColumnBeforeNameNeedsAHeader() {
        val withHeader = items("Qty Item Amount\n2 Tomato 30.00\n1 Onion 20.00")
        assertEquals(listOf(Triple("Tomato", 3000L, 2), Triple("Onion", 2000L, 1)), withHeader)
        // with no header a leading number could be part of the name ("7 Up"), so it stays
        assertEquals(listOf(Triple("7 Up", 4000L, 1)), items("7 Up   40.00"))
    }

    @Test fun qtyColumnAfterNameWithHeader() {
        assertEquals(listOf(Triple("Bread", 8000L, 2)), items("Item Qty Amount\nBread 2 80.00").take(1))
    }

    @Test fun nameOnOneLineAmountsOnTheNext() {
        val got = items(
            """
            Amul Gold Milk 1L
              2 x 66.00    132.00
            Aashirvaad Atta 5kg
              1 @ 245.00   245.00
            Bananas
            62.50
            """.trimIndent(),
        )
        assertEquals(listOf(Triple("Amul Gold Milk 1L", 13200L, 2), Triple("Aashirvaad Atta 5kg", 24500L, 1), Triple("Bananas", 6250L, 1)), got)
    }

    @Test fun restaurantBill() {
        val bill = BillItems.parse(
            """
            The Spice Garden
            Table 7   Guests 4
            Order #881 21:15
            Paneer Tikka       1  240.00
            Butter Naan x4     120.00
            Dal Makhani        2 x 180.00   360.00
            Fresh Lime Soda    2 x 60.00
            Service Charge 5%   37.00
            GST 5%              37.80
            Grand Total        794.80
            """.trimIndent(),
        )
        assertEquals(
            listOf(Triple("Paneer Tikka 1", 24000L, 1), Triple("Butter Naan", 12000L, 4), Triple("Dal Makhani", 36000L, 2), Triple("Fresh Lime Soda", 12000L, 2), Triple("Service Charge", 3700L, 1)),
            bill.items.map { Triple(it.name, it.amountMinor, it.quantity) },
        )
        assertEquals(79480L, bill.printedTotalMinor)
    }

    @Test fun westernReceiptWithTaxLettersAndCommaDecimals() {
        val got = items(
            """
            FRESH CORNER
            ORGANIC BANANAS    2.10 F
            MILK 2% 1GAL       3.49 F
            LIGHT BULB 4PK     6,99 T
            """.trimIndent(),
            Currency.USD,
        )
        assertEquals(listOf(Triple("ORGANIC BANANAS", 210L, 1), Triple("MILK 2% 1GAL", 349L, 1), Triple("LIGHT BULB 4PK", 699L, 1)), got)
    }

    @Test fun dotLeadersAndSerialNumbersAndItemCodes() {
        val got = items(
            """
            1. Cold Coffee ........ 120.00
            2) Veg Sandwich....... 90.00
            890123 Detergent Powder 1kg   215.00
            """.trimIndent(),
        )
        assertEquals(listOf(Triple("Cold Coffee", 12000L, 1), Triple("Veg Sandwich", 9000L, 1), Triple("Detergent Powder 1kg", 21500L, 1)), got)
    }

    @Test fun totalOnTheNextLine() {
        val bill = BillItems.parse("Notebook   45.00\nPen   10.00\nNET PAYABLE\n55.00")
        assertEquals(5500L, bill.printedTotalMinor)
        assertEquals(2, bill.items.size)
    }

    @Test fun totalFlavours() {
        for (line in listOf("Total: Rs. 94.00", "Grand Total ₹94", "TOTAL AMOUNT 94.00", "Net Amount 94.00", "Amount Payable 94.00", "Bill Total 94.00", "To Pay 94.00", "Total (incl. GST) 94.00", "TOTAL 3 94.00")) {
            val bill = BillItems.parse("Milk 54.00\nBread 40.00\n$line")
            assertEquals(9400L, bill.printedTotalMinor, line)
            assertEquals(2, bill.items.size, line)
        }
    }

    @Test fun otherFiguresWithTotalInThemAreNotTheTotal() {
        val bill = BillItems.parse("Milk 54.00\nTotal Qty: 1\nTotal Items 1\nTotal Tax 2.70\nSub Total 54.00\nTotal Savings 5.00\nTOTAL 56.70")
        assertEquals(5670L, bill.printedTotalMinor)
        assertEquals(listOf("Milk"), bill.items.map { it.name })
    }

    @Test fun lastTotalWins() {
        assertEquals(10000L, BillItems.parse("Milk 54.00\nTOTAL 54.00\nGrand Total 100.00").printedTotalMinor)
    }

    @Test fun paymentAndTaxAndDiscountLinesAreNotItems() {
        val got = items(
            """
            Soap  30.00
            Discount  -5.00
            You saved 5.00
            GST 18%  4.50
            VAT 12.5% 3.00
            Cess 1.00
            Cash tendered 50.00
            Card 25.00
            UPI Paid 25.00
            Change due 0.00
            Rounding 0.10
            """.trimIndent(),
        )
        assertEquals(listOf(Triple("Soap", 3000L, 1)), got)
    }

    @Test fun headerAndFooterJunkIsIgnored() {
        val got = items(
            """
            SUNRISE BAKERY
            Shop 4, Lake View Plaza 400001
            Tel 022 2345 6789
            Mobile: 9876543210
            FSSAI Lic 12345678901234
            Invoice 2231
            Date 05-10-2026 Time 10:32
            www.sunrisebakery.example
            help@sunrisebakery.example
            Croissant   85.00
            Thanks for shopping 
            Visit us again 2
            """.trimIndent(),
        )
        assertEquals(listOf(Triple("Croissant", 8500L, 1)), got)
    }

    @Test fun phoneNumbersAndBigNumbersAreNotPrices() {
        assertTrue(items("Call us 9876543210").isEmpty())
        assertTrue(items("Mystery item 99,99,99,999.00").isEmpty())
        assertTrue(items("Gold bar 10,00,001.00").isEmpty()) // above ten lakh for a single line
    }

    @Test fun linesDearerThanThePrintedTotalAreDropped() {
        val bill = BillItems.parse("Milk 54.00\nPincode area 560001\nBread 40.00\nTOTAL 94.00")
        assertEquals(listOf("Milk", "Bread"), bill.items.map { it.name })
    }

    @Test fun junkGivesNothing() {
        for (text in listOf("", "   \n\n  ", "!!!! ???? ....", "hello world", "12345", "Thank you", "Total", "₹ ₹ ₹", "x2 x3")) {
            val bill = BillItems.parse(text)
            assertTrue(bill.isEmpty, "'$text' -> $bill")
        }
    }

    @Test fun onlyATotalIsStillSomething() {
        val bill = BillItems.parse("TOTAL 250.00")
        assertTrue(bill.items.isEmpty())
        assertEquals(25000L, bill.printedTotalMinor)
        assertTrue(!bill.isEmpty)
    }

    @Test fun nonLatinNames() {
        assertEquals(listOf(Triple("दूध 500ml", 2700L, 1), Triple("चाय", 12000L, 1)), items("दूध 500ml  27.00\nचाय  120.00"))
    }

    @Test fun noDecimalCurrency() {
        assertEquals(listOf(Triple("Ramen", 980L, 1), Triple("Tea", 150L, 1)), items("Ramen   ¥980\nTea  150", Currency("JPY", 0)).map { Triple(it.first, it.second, it.third) })
    }

    @Test fun tooManyDecimalsForTheCurrencyIsNotAPrice() {
        assertTrue(items("Fuel 3.455").isEmpty())
        assertNull(BillItems.parse("Fuel 3.455").printedTotalMinor)
    }

    @Test fun badGroupingIsNotAPrice() {
        assertTrue(items("Thing 1,2345.00").isEmpty())
        assertTrue(items("Thing 12,3.00").isEmpty())
    }

    @Test fun identicalLinesAreBothKept() {
        assertEquals(2, items("Tea 10.00\nTea 10.00").size)
    }

    @Test fun amountForUsesPrintedTotalOnlyWhenEverythingIsUsed() {
        val bill = BillItems.parse("Milk 54.00\nBread 40.00\nTOTAL 100.00")
        assertEquals(10000L, BillItems.amountFor(bill, setOf(0, 1)))
        assertEquals(5400L, BillItems.amountFor(bill, setOf(0)))
        assertEquals(0L, BillItems.amountFor(bill, emptySet()))
        val noTotal = BillItems.parse("Milk 54.00\nBread 40.00")
        assertEquals(9400L, BillItems.amountFor(noTotal, setOf(0, 1)))
    }

    @Test fun noteListsNamesAndPrices() {
        val bill = BillItems.parse("Milk 54.00\nBread 2 x 40.00 80.00")
        assertEquals("Milk 54.00, Bread x2 80.00", BillItems.note(bill.items))
        assertEquals("Weekly shop · Milk 54.00, Bread x2 80.00", BillItems.note(bill.items, existing = " Weekly shop "))
        assertEquals("", BillItems.note(emptyList()))
    }

    @Test fun noteIsCappedWithAMoreCount() {
        val many = (1..40).map { BillItem("Item number $it", 1000L + it) }
        val note = BillItems.note(many, limit = BillItems.NOTE_LIMIT)
        assertTrue(note.length <= BillItems.NOTE_LIMIT, note)
        assertTrue(note.startsWith("Item number 1 10.01, Item number 2 10.02"), note)
        assertTrue(Regex(", \\+\\d+ more$").containsMatchIn(note), note)
        // the count is right: everything not listed is counted
        val listed = note.substringBefore(", +").split(", ").size
        assertEquals(40 - listed, note.substringAfter(", +").substringBefore(" more").toInt())
    }

    @Test fun noteWithOneHugeNameIsCutToTheLimit() {
        val note = BillItems.note(listOf(BillItem("x".repeat(500), 100L)), limit = 50)
        assertEquals(50, note.length)
    }

    @Test fun noteKeepsALongExistingNoteUnchanged() {
        val existing = "y".repeat(BillItems.NOTE_LIMIT)
        assertEquals(existing, BillItems.note(listOf(BillItem("Milk", 5400L)), existing = existing))
    }
}

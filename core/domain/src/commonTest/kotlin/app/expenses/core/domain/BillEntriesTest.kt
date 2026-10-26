package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class BillEntriesTest {
    private val bill = ParsedBill(
        listOf(BillItem("Veg Biryani", 18_000), BillItem("Masala Dosa", 12_000, quantity = 2), BillItem("Filter Coffee", 4_000)),
        printedTotalMinor = 35_700,
    )
    private val base = EntryDraft(null, DraftKind.EXPENSE, category = "Food", accountId = "bank", occurredAt = 1_000L, tags = listOf("Dinner"))

    @Test fun theRestIsWhatTheTotalHoldsBeyondTheLines() {
        assertEquals(1_700L, BillEntries.restMinor(bill, setOf(0, 1, 2)))
    }

    @Test fun withALineLeftOutOrNoTotalOrADiscountThereIsNoRest() {
        assertEquals(0L, BillEntries.restMinor(bill, setOf(0, 1)))
        assertEquals(0L, BillEntries.restMinor(bill.copy(printedTotalMinor = null), setOf(0, 1, 2)))
        assertEquals(0L, BillEntries.restMinor(bill.copy(printedTotalMinor = 30_000), setOf(0, 1, 2)))
        assertEquals(0L, BillEntries.restMinor(ParsedBill(emptyList(), 5_000), emptySet()))
    }

    @Test fun eachLineBecomesAnEntryWithTheFormsAccountDateCategoryAndTags() {
        val drafts = BillEntries.drafts(base, bill.items, 1_700, Currency.INR)
        assertEquals(listOf("Veg Biryani", "Masala Dosa ×2", "Filter Coffee", BillEntries.REST_NAME), drafts.map { it.note })
        assertEquals(listOf("180.00", "120.00", "40.00", "17.00"), drafts.map { it.amountText })
        assertEquals(357_00L, drafts.sumOf { parseAmount(it.amountText, Currency.INR)!!.minorUnits }, "the entries add up to the bill")
        drafts.forEach {
            assertEquals("Food", it.category); assertEquals("bank", it.accountId); assertEquals(1_000L, it.occurredAt); assertEquals(listOf("Dinner"), it.tags)
            assertEquals(null, it.id); assertEquals(DraftKind.EXPENSE, it.kind)
        }
    }

    @Test fun withoutARestThereIsNoExtraEntry() {
        assertEquals(3, BillEntries.drafts(base, bill.items, 0, Currency.INR).size)
    }
}

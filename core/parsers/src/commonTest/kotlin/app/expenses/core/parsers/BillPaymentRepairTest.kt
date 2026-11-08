package app.expenses.core.parsers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Which bills made by the old mistake may be moved to To review, and which are left alone. Texts, names and amounts are invented. */
class BillPaymentRepairTest {
    private val owner = "Hi Asha, Axis Bank Credit Card bill payment of Rs. 12,345.67 was successful! T&C -Kotak811"
    private val amount = 1_234_567L

    private fun judge(
        body: String? = owner, source: String = "SMS", status: String = "FOUND", amountMinor: Long = amount, dueDate: String? = null, entryId: String? = null, paidAt: Long? = null,
    ) = BillPaymentRepair.notice(source, status, body, amountMinor, dueDate, entryId, paidAt)

    @Test
    fun aFoundBillMadeFromTheOwnersTextIsRepairable() {
        val n = judge()
        assertNotNull(n); assertEquals(BillPaymentSide.PAID, n.side); assertEquals("Axis Bank", n.cardBank); assertEquals(amount, n.amountMinor)
    }

    @Test
    fun aBillTheUserHasActedOnIsLeftAlone() {
        assertNull(judge(status = "TRACKED"), "added to their bills: theirs now")
        assertNull(judge(status = "PAID")); assertNull(judge(status = "DISMISSED"))
        assertNull(judge(entryId = "an-entry"), "a payment is linked")
        assertNull(judge(paidAt = 5L))
    }

    @Test
    fun aDueDateThatIsNotTheTextsOwnDateWasTypedByThePersonAndTheBillIsLeftAlone() {
        val dated = "Axis Bank Credit Card bill payment of Rs. 12,345.67 was successful on 05-11-2026!"
        assertNotNull(judge(body = dated, dueDate = "2026-11-05"), "the parser took the payment's own date for the due date")
        assertNull(judge(body = dated, dueDate = "2026-12-01"), "another date: edited")
        assertNull(judge(dueDate = "2026-12-01"), "a text with no date cannot explain a due date")
    }

    @Test
    fun onlyATextBillWithItsWordsAndTheSameAmountQualifies() {
        assertNull(judge(source = "NOTIFICATION"), "a notification is not repaired")
        assertNull(judge(body = null)); assertNull(judge(body = "  "))
        assertNull(judge(amountMinor = amount + 1), "the bill's amount is not the amount paid")
    }

    @Test
    fun aRealBillOrATextThatAlsoAsksForMoneyIsNeverMoved() {
        val statement = "Your Axis Bank credit card statement of Rs 12,345.67 is generated, total amount due Rs 12,345.67, due date 25-Nov-2026"
        assertNull(judge(body = statement, dueDate = "2026-11-25"))
        val both = "Payment of Rs 12,345.67 was successful towards your Axis Bank Credit Card. Total amount due Rs 12,345.67 is due on 25-Nov-2026"
        assertNull(judge(body = both), "it also asks for money: left as a bill")
        assertNull(judge(body = "Pay your Axis Bank credit card bill of Rs 12,345.67 by 25-Nov-2026"))
    }
}

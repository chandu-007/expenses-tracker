package app.expenses.core.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Which open bill a payment confirmation pays. Every bill, bank, digit and amount here is invented. */
class BillPaymentMatchTest {
    private val day = LocalDate(2026, 11, 8)

    private fun card(id: String, issuer: String, last4: String?, total: Long, min: Long? = null, due: LocalDate? = LocalDate(2026, 11, 15), received: LocalDate = LocalDate(2026, 10, 26)) =
        OpenBill(id, "CREDIT_CARD", issuer, last4, null, "$issuer credit card ${last4.orEmpty()}".trim(), total, min, "INR", due, received)

    private fun utility(id: String, kind: String, issuer: String, biller: String?, total: Long, due: LocalDate? = LocalDate(2026, 11, 12)) =
        OpenBill(id, kind, issuer, null, biller, "${biller ?: issuer} bill", total, null, "INR", due, LocalDate(2026, 10, 30))

    private fun ids(h: List<BillPaymentMatch.Hit>) = h.map { it.bill.id }

    @Test
    fun theOwnersShapeFindsTheAxisCardBillNotTheKotakOne() {
        val bills = listOf(card("axis", "Axis Bank", "1234", 1_234_567, 61_728), card("kotak", "Kotak Mahindra Bank", "9876", 1_234_567))
        val hits = BillPaymentMatch.candidates("CREDIT_CARD", "Axis Bank", null, null, 1_234_567, "INR", day, bills)
        assertEquals(listOf("axis"), ids(hits))
        assertEquals(BillPaymentMatch.Fit.TOTAL, hits.single().fit)
    }

    @Test
    fun theTotalTheMinimumAndAnythingBetweenPayACardBillButLessOrMuchMoreDoesNot() {
        val bills = listOf(card("a", "HDFC Bank", "0862", 1_000_000, 50_000))
        fun fit(amount: Long) = BillPaymentMatch.candidates("CREDIT_CARD", "HDFC Bank", "0862", null, amount, "INR", day, bills).singleOrNull()?.fit
        assertEquals(BillPaymentMatch.Fit.TOTAL, fit(1_000_000))
        assertEquals(BillPaymentMatch.Fit.TOTAL, fit(1_000_099), "a rupee over is rounding")
        assertEquals(BillPaymentMatch.Fit.MINIMUM, fit(50_000))
        assertEquals(BillPaymentMatch.Fit.PART, fit(300_000))
        assertEquals(null, fit(49_000), "under the minimum is not this bill's payment")
        assertEquals(null, fit(1_000_200), "more than the total is not")
    }

    @Test
    fun theSameDigitsAtAnotherBankAreAnotherCardAndTheDigitsDecideWhenBothHaveThem() {
        val bills = listOf(card("hdfc", "HDFC Bank", "1234", 500_000), card("icici", "ICICI Bank", "1234", 500_000), card("hdfc2", "HDFC Bank", "7777", 500_000))
        assertEquals(listOf("hdfc"), ids(BillPaymentMatch.candidates("CREDIT_CARD", "HDFC Bank", "1234", null, 500_000, "INR", day, bills)))
        assertEquals(listOf("icici"), ids(BillPaymentMatch.candidates("CREDIT_CARD", "ICICI Bank", "1234", null, 500_000, "INR", day, bills)))
        // three digits of a card whose digits the bill holds as four
        assertEquals(listOf("hdfc"), ids(BillPaymentMatch.candidates("CREDIT_CARD", null, "234", null, 500_000, "INR", day, listOf(card("hdfc", "HDFC Bank", "1234", 500_000)))))
        // the bank alone, with two cards of it open, lists both (the person picks)
        assertEquals(listOf("hdfc", "hdfc2"), ids(BillPaymentMatch.candidates("CREDIT_CARD", "HDFC Bank", null, null, 500_000, "INR", day, bills)).sorted())
    }

    @Test
    fun aPaymentThatNamesNoCardPaysACardBillOnlyWhenThereIsExactlyOneOpen() {
        val one = listOf(card("only", "SBI Card", null, 250_000))
        assertEquals(listOf("only"), ids(BillPaymentMatch.candidates("CREDIT_CARD", null, null, null, 250_000, "INR", day, one)))
        val two = one + card("second", "Axis Bank", null, 250_000)
        assertEquals(emptyList(), ids(BillPaymentMatch.candidates("CREDIT_CARD", null, null, null, 250_000, "INR", day, two)))
    }

    @Test
    fun aCardPaymentNeverPaysAUtilityAndAUtilityPaymentNeverPaysACard() {
        val bills = listOf(card("card", "Axis Bank", "1234", 70_682), utility("jio", "MOBILE", "Jio", "Jio", 70_682))
        assertEquals(listOf("card"), ids(BillPaymentMatch.candidates("CREDIT_CARD", "Axis Bank", "1234", null, 70_682, "INR", day, bills)))
        assertEquals(listOf("jio"), ids(BillPaymentMatch.candidates("MOBILE", null, null, "Jio", 70_682, "INR", day, bills)))
    }

    @Test
    fun aUtilityBillIsPaidInFullFromTheSameBillerWithinARupeeOrOnePerCent() {
        val bills = listOf(utility("jio", "MOBILE", "Jio", "Jio", 70_682), utility("bescom", "ELECTRICITY", "BESCOM", "BESCOM", 70_682))
        assertEquals(listOf("jio"), ids(BillPaymentMatch.candidates("MOBILE", null, null, "Jio", 70_682, "INR", day, bills)))
        assertEquals(listOf("jio"), ids(BillPaymentMatch.candidates("MOBILE", null, null, "Jio", 70_700, "INR", day, bills)), "18 paise off")
        assertEquals(emptyList(), ids(BillPaymentMatch.candidates("MOBILE", null, null, "Jio", 60_000, "INR", day, bills)), "a different amount is not this bill")
        assertEquals(listOf("bescom"), ids(BillPaymentMatch.candidates("ELECTRICITY", null, null, "BESCOM", 70_682, "INR", day, bills)))
        // no biller named: nothing is guessed
        assertEquals(emptyList(), ids(BillPaymentMatch.candidates("MOBILE", null, null, null, 70_682, "INR", day, bills)))
    }

    @Test
    fun theDateWindowIsFortyFiveDaysBeforeTheDueDateToThirtyAfterAndSixtyDaysFromTheTextWhenThereIsNoDueDate() {
        val due = LocalDate(2026, 12, 25)
        val dated = listOf(card("d", "Axis Bank", "1234", 100_000, due = due))
        fun at(d: LocalDate) = ids(BillPaymentMatch.candidates("CREDIT_CARD", "Axis Bank", "1234", null, 100_000, "INR", d, dated))
        assertEquals(listOf("d"), at(LocalDate(2026, 11, 10)))
        assertEquals(emptyList(), at(LocalDate(2026, 11, 9)), "46 days before")
        assertEquals(listOf("d"), at(LocalDate(2027, 1, 24)))
        assertEquals(emptyList(), at(LocalDate(2027, 1, 25)), "31 days after")
        val undated = listOf(card("u", "Axis Bank", "1234", 100_000, due = null, received = LocalDate(2026, 10, 1)))
        assertEquals(listOf("u"), ids(BillPaymentMatch.candidates("CREDIT_CARD", "Axis Bank", "1234", null, 100_000, "INR", LocalDate(2026, 11, 30), undated)))
        assertEquals(emptyList(), ids(BillPaymentMatch.candidates("CREDIT_CARD", "Axis Bank", "1234", null, 100_000, "INR", LocalDate(2026, 12, 2), undated)))
    }

    @Test
    fun theBestComesFirstTheExactTotalThenTheNearestDueDateAndTheAnswerDoesNotDependOnTheOrder() {
        val near = card("near", "Axis Bank", "1234", 100_000, due = LocalDate(2026, 11, 10))
        val far = card("far", "Axis Bank", "1234", 100_000, due = LocalDate(2026, 12, 20))
        val partOnly = card("part", "Axis Bank", "1234", 400_000, min = 40_000, due = LocalDate(2026, 11, 9))
        val hits = BillPaymentMatch.candidates("CREDIT_CARD", "Axis Bank", "1234", null, 100_000, "INR", day, listOf(far, partOnly, near))
        assertEquals(listOf("near", "far", "part"), ids(hits))
        assertEquals(ids(hits), ids(BillPaymentMatch.candidates("CREDIT_CARD", "Axis Bank", "1234", null, 100_000, "INR", day, listOf(near, partOnly, far))))
    }

    @Test
    fun aBillInAnotherCurrencyIsNeverMatched() {
        val usd = card("usd", "Axis Bank", "1234", 100_000).copy(currency = "USD")
        assertTrue(BillPaymentMatch.candidates("CREDIT_CARD", "Axis Bank", "1234", null, 100_000, "INR", day, listOf(usd)).isEmpty())
    }

    @Test
    fun aBankWithNoDistinctiveWordInItsNameMatchesByTheWholeName() {
        val yes = card("yes", "Yes Bank", null, 80_000)
        assertEquals(listOf("yes"), ids(BillPaymentMatch.candidates("CREDIT_CARD", "Yes Bank", null, null, 80_000, "INR", day, listOf(yes))))
        assertEquals(emptyList(), ids(BillPaymentMatch.candidates("CREDIT_CARD", "Axis Bank", null, null, 80_000, "INR", day, listOf(yes))))
    }
}

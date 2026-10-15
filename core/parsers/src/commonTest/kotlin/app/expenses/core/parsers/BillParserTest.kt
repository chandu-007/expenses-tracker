package app.expenses.core.parsers

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** All the texts here are made up in the common Indian shapes, except where a test says it is from the public corpus. */
class BillParserTest {
    private val today = LocalDate(2026, 10, 10)
    private fun bill(text: String, root: String? = null) = BillParser.parse(text, root, today)
    private fun must(text: String, root: String? = null) = assertNotNull(bill(text, root), text)

    @Test
    fun aCreditCardStatementGivesTheTotalTheMinimumTheDueDateAndTheCardDigits() {
        val b = must("Your HDFC Bank Credit Card ending 0862 statement is generated. Total Amt Due Rs.12,345.00, Min Amt Due Rs.617.00, Due Date 25-Oct-2026.", "HDFCBK")
        assertEquals(BillKind.CREDIT_CARD, b.kind); assertEquals(1_234_500L, b.amountMinor); assertEquals(61_700L, b.minDueMinor)
        assertEquals(LocalDate(2026, 10, 25), b.dueDate); assertEquals("0862", b.accountLast4); assertEquals(false, b.autoDebit)
    }

    @Test
    fun theThreePublicCorpusCardBillsParseWithBareNumbersAndDayMonthWording() {
        val equitasDue = must("Payment of Equitas Credit Card 0000 is due on 10/07/26. Min due Rs 1234.56 Total due Rs 12345.67. Non payment is reported to Credit Bureau. Pls ignore if paid")
        assertEquals(1_234_567L, equitasDue.amountMinor); assertEquals(123_456L, equitasDue.minDueMinor); assertEquals(LocalDate(2026, 7, 10), equitasDue.dueDate); assertEquals("0000", equitasDue.accountLast4)
        val statement = must("Statement for your Equitas Credit Card 0000 is generated. Total Due: 12345.67 Min Due: 1234.56 Due by: 09/06/26. Pls pay by due date.")
        assertEquals(1_234_567L, statement.amountMinor); assertEquals(LocalDate(2026, 6, 9), statement.dueDate)
        val onecard = must("Hi, Hope you love using your BOBCARD One Credit Card! Your April-2026 bill of Rs.7,777.77 is ready. Please pay by 05 May, 2026 through the OneCard app.  View statement: 1crd.in/OneCrd/bill")
        assertEquals(777_777L, onecard.amountMinor); assertEquals(LocalDate(2026, 5, 5), onecard.dueDate); assertEquals(BillKind.CREDIT_CARD, onecard.kind)
    }

    @Test
    fun anElectricityBillGivesTheAmountTheDateAndTheConsumerNumber() {
        val b = must("Dear Customer, your BESCOM electricity bill of Rs 1,842.50 for consumer no 1234567890 is due on 15-10-2026. Pay online to avoid disconnection.")
        assertEquals(BillKind.ELECTRICITY, b.kind); assertEquals(184_250L, b.amountMinor); assertEquals(LocalDate(2026, 10, 15), b.dueDate)
        assertEquals("1234567890", b.reference); assertEquals("BESCOM", b.biller); assertNull(b.minDueMinor)
    }

    @Test
    fun waterGasMobileBroadbandDthInsuranceAndLoanBillsAreEachRecognisedByTheirWords() {
        fun kind(t: String) = must(t).kind
        assertEquals(BillKind.WATER, kind("Your water bill of Rs 420 is due on 20 Oct. Pay at the Jal Board portal."))
        assertEquals(BillKind.GAS, kind("Your PNG gas bill of Rs 650.00 is payable by 18-Oct-2026."))
        assertEquals(BillKind.MOBILE, kind("Your Airtel postpaid bill of Rs 799 is due on 12 Oct 2026. Pay now."))
        assertEquals(BillKind.BROADBAND, kind("ACT Fibernet: your broadband bill Rs 1,049 is due by 14-Oct-2026."))
        assertEquals(BillKind.DTH, kind("Tata Play: Rs 350 is due on 16-10-2026 for your DTH account."))
        assertEquals(BillKind.INSURANCE, kind("Your LIC premium of Rs 12,000 is due on 28-10-2026. Policy no 123456789."))
        assertEquals(BillKind.LOAN_EMI, kind("Your loan EMI of Rs 5,400 is due on 05-11-2026. Keep your account funded."))
        assertEquals(BillKind.OTHER, kind("Maintenance bill of Rs 2,500 is payable by 10-11-2026."))
    }

    @Test
    fun anAutoDebitAnnouncementIsABillMarkedAsAutomatic() {
        val b = must("Rs.5,400.00 will be debited from your A/c XX1234 on 05-11-2026 towards loan EMI.")
        assertEquals(true, b.autoDebit); assertEquals(540_000L, b.amountMinor); assertEquals(LocalDate(2026, 11, 5), b.dueDate); assertEquals(BillKind.LOAN_EMI, b.kind)
    }

    @Test
    fun aDateWithNoYearTakesTheNearestComingOneAndAPastOneRollsToNextYear() {
        assertEquals(LocalDate(2026, 10, 25), must("Your electricity bill of Rs 900 is due on 25 Oct.").dueDate)
        assertEquals(LocalDate(2027, 1, 5), must("Your electricity bill of Rs 900 is due on 5 Jan.").dueDate, "a January date seen in October is next January")
        assertEquals(LocalDate(2026, 9, 28), must("Your electricity bill of Rs 900 was due on 28 Sep. Pay now.").dueDate, "a date only 12 days past stays in this year (an overdue bill)")
    }

    @Test
    fun theStatementDateIsNotTheDueDateAndAMessageWithNoDueDateStillGivesTheBill() {
        assertEquals(LocalDate(2026, 10, 25), must("Credit card ending 4321: statement date 05-10-2026, total amount due Rs 3,000, due date 25-10-2026.").dueDate)
        assertNull(must("Your electricity bill of Rs 900 is ready. Statement date 05-10-2026.").dueDate, "the only date is the statement date, so there is no due date")
        assertNull(must("Your water bill of Rs 300 is due. Please pay.").dueDate)
    }

    @Test
    fun aMessageThatSaysADueAmountButMentionsNoCurrencyMarkIsFoundByItsLabel() {
        assertEquals(500_000L, must("Credit card 1234 Total Due: 5000 Min Due: 250 Due by: 20/10/26").amountMinor)
    }

    @Test
    fun aPaymentThatWasMadeIsNotABill() {
        assertNull(bill("Payment of Rs 12,345.00 received towards your HDFC Bank Credit Card ending 0862. Thank you."))
        assertNull(bill("Thank you for your payment of Rs 1,842 towards your electricity bill. Receipt no 12345678."))
        assertNull(bill("Your bill of Rs 900 has been paid successfully on 10-10-2026."))
        assertNull(bill("Rs 900 has been debited from your account towards your electricity bill due 15-10-2026."))
    }

    @Test
    fun notABillOtpsOffersBalancesAndOrdinaryAlertsAreLeftAlone() {
        assertNull(bill("123456 is the OTP to pay your bill of Rs 900 due on 15-10-2026"))
        assertNull(bill("Pre-approved loan up to Rs 5,00,000. Apply now. EMI starts Rs 4,999 due soon"))
        assertNull(bill("Rs.249.00 debited from A/c XX1234 on 10-10-26 to VPA swiggy@icici (UPI Ref 628374659283). Not you? Call 18002586161"))
        assertNull(bill("Rs.250.00 credited to A/c XX1234 on 10-10-26. Avl Bal Rs 12,000.00"))
        assertNull(bill("Your login attempt failed due to a wrong PIN."))
        assertNull(bill("Your bill is ready. Visit our website."), "no amount: nothing to track")
        assertNull(bill("Your account balance is low."))
    }

    @Test
    fun aBalanceOrLimitIsNotTakenForTheAmountDue() {
        val b = must("Your credit card bill is ready. Avl limit Rs 90,000. Please pay Rs 4,500 by 20-10-2026.")
        assertEquals(450_000L, b.amountMinor)
    }

    @Test
    fun aMinimumAloneBecomesTheAmountAndAMinimumAboveTheTotalIsIgnored() {
        assertEquals(61_700L, must("Credit card ending 4321 minimum due Rs 617 is due on 20-10-2026.").amountMinor)
        assertNull(must("Credit card ending 4321 total due Rs 500, minimum due Rs 900, due on 20-10-2026.").minDueMinor)
    }
}

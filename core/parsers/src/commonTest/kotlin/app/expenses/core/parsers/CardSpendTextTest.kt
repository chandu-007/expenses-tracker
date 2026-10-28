package app.expenses.core.parsers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A spend on a credit card, including one paid by UPI on the card, is a debit that names the CARD's digits, so it lands on the card account and not on the bank
 * account (whose digits are different). The texts are invented, in the wording banks use.
 */
class CardSpendTextTest {
    private fun alert(text: String, sender: String = "AX-HDFCBK-S") = (CaptureClassifier.classify(sender, text) as CaptureOutcome.Transaction).alert

    @Test
    fun aCardSpendIsADebitWithTheCardsLastFourDigits() {
        val a = alert("Rs 500.00 spent on HDFC Bank Card XX1234 at SWIGGY on 05-10-26. Avl limit Rs 90,000.00")
        assertEquals(Direction.DEBIT, a.direction); assertEquals(50_000, a.amountMinor); assertEquals("1234", a.accountLast4)
    }

    @Test
    fun aUpiPaymentOnACreditCardIsADebitWithTheCardsDigitsAndIsMarkedAsUpi() {
        val a = alert("Rs.500.00 spent on HDFC Bank Credit Card XX1234 via UPI to swiggy@okhdfc on 05-10-26. UPI Ref 628374659283")
        assertEquals(Direction.DEBIT, a.direction); assertEquals(50_000, a.amountMinor); assertEquals("1234", a.accountLast4)
        assertTrue(a.viaUpi)
    }

    @Test
    fun theSameSpendOnTheBankAccountNamesTheAccountsOwnDigits() {
        val a = alert("Rs.500.00 debited from A/c XX5678 on 05-10-26 to VPA swiggy@okhdfc (UPI Ref 628374659283). Not you? Call 18002586161")
        assertEquals(Direction.DEBIT, a.direction); assertEquals("5678", a.accountLast4)
        assertTrue(a.viaUpi)
    }

    @Test
    fun aSpendOnACardWithTheNumberWrittenInFullIsStillTheLastFour() {
        assertEquals("1234", alert("INR 500.00 spent using ICICI Bank Credit Card XX1234 on 05-Oct-26 at SWIGGY.", "VM-ICICIB-S").accountLast4)
        assertEquals("1234", alert("Rs 500.00 spent on HDFC Bank Card ending 1234 at SWIGGY on 05-10-26").accountLast4)
    }
}

package app.expenses.core.parsers

import kotlin.test.Test
import kotlin.test.assertEquals

class MessageKindTest {
    private fun kind(text: String, d: Direction) = KindRules.of(text, d)

    @Test
    fun paymentsToACardAreCardPaymentsFromEitherSide() {
        for (t in listOf(
            "Thank you for the payment of Rs.12,345.00 towards Equitas Credit Card 0000, this has been credited to your account on 07/06/26.",
            "DEAR HDFCBANK CARDMEMBER, PAYMENT OF Rs. 100.00 RECEIVED TOWARDS YOUR CREDIT CARD ENDING WITH 0000 ON 17-5-2026.",
            "Repayment of Rs.3,750 received for the slice credit card. The amount has been credited to your card account",
            "Your slice UPI credit card bill of Rs.2,500.00 has been paid successfully via autopay.",
            "Thank you for payment of INR 3,000.00 towards your FIRST Wealth Credit Card XX0000 on 02 May 2026.",
        )) for (d in Direction.entries) assertEquals(MessageKind.CARD_PAYMENT, kind(t, d), t)
    }

    @Test
    fun anAtmWithdrawalNeedsWithdrawalWordsNotJustTheWordAtm() {
        assertEquals(MessageKind.ATM_WITHDRAWAL, kind("Rs.5,000.00 withdrawn at ATM 12345 on 05-10-26 from A/c XX1234", Direction.DEBIT))
        assertEquals(MessageKind.ATM_WITHDRAWAL, kind("INR 2,000 ATM WDL at SBI ATM on 05-10-26", Direction.DEBIT))
        assertEquals(MessageKind.ATM_WITHDRAWAL, kind("Cash withdrawal of Rs 10,000 from your account", Direction.DEBIT))
        assertEquals(MessageKind.ATM_WITHDRAWAL, kind("Rs.2,000.00 debited from A/c XX1234 on 05-10-26. Info: ATM-CASH. Avl Bal Rs.100", Direction.DEBIT), "the narration form of an ATM withdrawal")
        assertEquals(MessageKind.PAYMENT, kind("Rs.800 spent on your HDFC Debit/ATM Card xx1234 at SWIGGY", Direction.DEBIT), "an ATM-type card used in a shop is a payment")
        assertEquals(MessageKind.PAYMENT, kind("Rs.5,000.00 withdrawn at ATM", Direction.CREDIT), "a credit is never a withdrawal")
    }

    @Test
    fun aRefundOrAReversalIsOnlyEverACredit() {
        assertEquals(MessageKind.REFUND, kind("Refund of Rs.254.00 credited to your card ending 0000 from AMAZON", Direction.CREDIT))
        assertEquals(MessageKind.REFUND, kind("Transaction Reversed! On HDFC Bank DEBIT/ATM Card xx0000 Amt: Rs.2", Direction.CREDIT))
        assertEquals(MessageKind.PAYMENT, kind("Refund initiated for your order, Rs.254.00", Direction.DEBIT))
        assertEquals(MessageKind.PAYMENT, kind("Rs.254.00 credited to your account by Ravi", Direction.CREDIT))
    }

    @Test
    fun aReversedAtmDebitIsARefundNotAWithdrawalAndOrdinaryPaymentsStayPayments() {
        assertEquals(MessageKind.REFUND, kind("Rs.2,000 ATM withdrawal reversed to your account", Direction.CREDIT))
        assertEquals(MessageKind.PAYMENT, kind("Rs.1,250.00 debited from A/c XX1234 to VPA shop@okhdfc (UPI Ref 628374659283)", Direction.DEBIT))
        assertEquals(MessageKind.PAYMENT, kind("INR 2,345.00 debited from your A/C towards Debit Card Purchase.", Direction.DEBIT))
    }

    @Test
    fun aPurchaseMadeWithACardIsNotAPaymentToTheCard() {
        assertEquals(MessageKind.PAYMENT, kind("Payment of Rs.500.00 made to SWIGGY using your HDFC credit card ending 1234 on 05-10-26", Direction.DEBIT))
        assertEquals(MessageKind.PAYMENT, kind("Rs.500.00 spent on your SBI Credit Card ending 1234 at AMAZON", Direction.DEBIT))
    }

    @Test
    fun aWithdrawalTextThatMentionsAReversalIsNotAWithdrawalYet() {
        assertEquals(MessageKind.PAYMENT, kind("Rs.2,000.00 withdrawn at ATM 4455 on 05-10-26 from A/c XX1234. Cash not dispensed, reversal will be processed", Direction.DEBIT))
    }

    @Test
    fun theShortCcWordingCountsAsACardPayment() {
        assertEquals(MessageKind.CARD_PAYMENT, kind("Rs.15,000.00 debited from A/c XX1234 on 05-10-26 for CC payment", Direction.DEBIT))
        assertEquals(MessageKind.CARD_PAYMENT, kind("Rs.15,000.00 debited from A/c XX1234 for CC Bill", Direction.DEBIT))
    }
}

package app.expenses.core.parsers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PayeeGuessTest {
    @Test
    fun aVpaIsTheNameWhenOneIsPresentWhoeverTheDirection() {
        assertEquals("shop@okhdfc", PayeeGuess.of("Rs.1,250.00 debited from A/c XX1234 to VPA shop@okhdfc (UPI Ref 628374659283)"))
        assertEquals("ravi.k@ybl", PayeeGuess.of("Rs 500 credited to A/c XX1234 from VPA Ravi.K@ybl on 05-10-26"))
        assertEquals("zomato@icici", PayeeGuess.of("Sent Rs.300.00 from HDFC Bank A/c *1234 To zomato@icici On 05/10/26"))
    }

    @Test
    fun aNamedMerchantAfterAtOrToIsTakenUpToTheNextFixedWord() {
        assertEquals("AMAZON PAY", PayeeGuess.of("Rs.499.00 spent on card ending 4321 at AMAZON PAY on 05-Oct-26. Avl limit Rs.50,000"))
        assertEquals("Swiggy", PayeeGuess.of("INR 250.00 spent using HDFC Bank Card x4321 at Swiggy on 05-10-26"))
        assertEquals("BigBasket", PayeeGuess.of("Your a/c XX1234 debited Rs.800 towards BigBasket via UPI"))
    }

    @Test
    fun theUpiPathNameIsUsedWhenThereIsNoVpa() {
        assertEquals("RAVI KUMAR", PayeeGuess.of("Rs.200 debited A/c XX1234 UPI/P2A/628374659283/RAVI KUMAR/ok"))
    }

    @Test
    fun wordsThatAreNotNamesAndTextWithNoPayeeGiveNothing() {
        assertNull(PayeeGuess.of("Rs.100 debited from your account on 05-10-26"))
        assertNull(PayeeGuess.of("Rs.100 credited to your a/c XX1234 on 05-10-26"))
        assertNull(PayeeGuess.of("Rs.5,000 withdrawn at ATM 12345 on 05-10-26"), "digits are not a name")
        assertNull(PayeeGuess.of(""))
    }

    @Test
    fun aPaymentAppNotificationGivesThePersonsNameFromItsTitleOrLeadingSentence() {
        assertEquals("Chandu", PayeeGuess.of("Chandu: Chandu. sent ₹10 to you."), "the owner's real PhonePe wording")
        assertEquals("Ravi Kumar", PayeeGuess.of("Ravi Kumar: Received ₹250 in your account"))
        assertEquals("Anita", PayeeGuess.of("Anita paid you ₹120 for lunch"))
        assertEquals("Anita Rao", PayeeGuess.of("Anita Rao: paid you Rs.120"))
    }

    @Test
    fun aHeadingOrABankIsNotTakenForAPerson() {
        assertNull(PayeeGuess.of("Payment successful: ₹75 paid"))
        assertNull(PayeeGuess.of("Money received: ₹75 added"))
        assertNull(PayeeGuess.of("HDFC Bank: ₹75 debited"))
        assertNull(PayeeGuess.of("Dear Customer: ₹75 debited"))
        assertNull(PayeeGuess.of("Paid ₹75: to a shop"), "a title with an amount is not a name")
        assertEquals("Chandu", PayeeGuess.of("Chandu: Rs.75 debited to VPA 9704849321@yescred"), "a person's name in the title beats a bare phone-number address")
        assertEquals("shop@okhdfc", PayeeGuess.of("Rs.75 debited to VPA shop@okhdfc"), "with no title the address is still the name")
    }

    @Test
    fun aCreditTextThatSaysSentByNamesTheSender() {
        assertEquals("RAVI KUMAR", PayeeGuess.of("Rs.250.00 received in A/c XX1234 on 10-10-26. Sent by RAVI KUMAR via UPI Ref 628374659001"))
        assertEquals("Anita Rao", PayeeGuess.of("Rs 75 credited. Transferred by Anita Rao on 05-10-26"))
        assertNull(PayeeGuess.of("Rs.499.00 paid by your card on 05-10-26"), "\"paid by your card\" is not a person")
        assertNull(PayeeGuess.of("Rs.100 credited. Sent by 9876543210 via UPI"), "a number is not a name")
    }
}

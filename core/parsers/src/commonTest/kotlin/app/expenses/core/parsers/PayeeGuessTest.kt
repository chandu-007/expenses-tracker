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
}

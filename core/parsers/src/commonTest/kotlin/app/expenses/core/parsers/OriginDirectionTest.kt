package app.expenses.core.parsers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Which way money went in the words an entry keeps (invented texts): what tells a text for the same transaction from a refund of it once the entry's type has been changed. */
class OriginDirectionTest {
    @Test
    fun aBankTextIsReadBySenderAndWords() {
        assertEquals(true, OriginDirection.outgoing("AX-HDFCBK-S", "Rs.1,250.00 debited from A/c XX1234 on 05-10-26 to VPA shop@okhdfc (UPI Ref 628374659283). Not you? Call 18002586161", email = false))
        assertEquals(false, OriginDirection.outgoing("AX-HDFCBK-S", "Rs.5,000.00 credited to your A/c XX1234 on 05-10-26 by NEFT from ACME (Ref 428374659283)", email = false))
        // a payment app's notification
        assertEquals(true, OriginDirection.outgoing("GPAY", "Paid ₹250 to Swiggy", email = false))
        assertEquals(false, OriginDirection.outgoing("PHONEPE", "Received ₹500 from Ravi Kumar", email = false))
        // a refund text is money in
        assertEquals(false, OriginDirection.outgoing("AX-HDFCBK-S", "Refund of Rs.399.00 credited to A/c XX1234 on 06-10-26 (Ref 428374659283)", email = false))
    }

    @Test
    fun anEmailIsReadWithoutItsSafetyNoticeAndWithNoBankHeader() {
        val body = "Rs. 900.00 was debited from your account XX4321 on 07-10-26 (UPI Ref 528374659283). Never share your OTP, password or card details with anyone."
        assertEquals(true, OriginDirection.outgoing("GMAIL", body, email = true))
        assertEquals(false, OriginDirection.outgoing("OUTLOOK", "Rs. 900.00 has been credited to your account XX4321 on 07-10-26. Do not share your OTP with anyone.", email = true))
    }

    @Test
    fun wordsThatCannotBeReadGiveNothingSoTheCallerFallsBackToTheKind() {
        assertNull(OriginDirection.outgoing("AX-HDFCBK-S", "Your OTP is 482913 for a transaction of Rs.500.00", email = false))
        assertNull(OriginDirection.outgoing("AX-HDFCBK-S", "Transaction of Rs.500.00 on card ending 4321 was declined due to insufficient funds.", email = false))
        assertNull(OriginDirection.outgoing(null, "hello there", email = false))
        assertNull(OriginDirection.outgoing("AX-HDFCBK-S", "", email = false))
    }
}

package app.expenses.core.parsers

import kotlin.test.Test
import kotlin.test.assertEquals

class PayeeKindTest {
    @Test
    fun aMobileNumberAddressIsAPersonWithOrWithoutTheCountryCode() {
        for (v in listOf("9876543210@ybl", "919876543210@okaxis", "+919876543210@paytm", "98765-43210@ybl", "6000000000@upi")) assertEquals(PayeeKind.PERSON, PayeeKinds.of(v), v)
        assertEquals(PayeeKind.UNKNOWN, PayeeKinds.of("5876543210@ybl"), "not a mobile number: numbers start 6 to 9")
        assertEquals(PayeeKind.UNKNOWN, PayeeKinds.of("987654321@ybl"), "nine digits")
        assertEquals(PayeeKind.UNKNOWN, PayeeKinds.of("98765432101@ybl"), "eleven digits")
    }

    @Test
    fun wellKnownMerchantQrShapesAreBusinessesAndAnythingElseIsLeftUnknown() {
        for (v in listOf("paytmqr281005050101abc@paytm", "BHARATPE.9000123456@fbpe", "razorpay.swiggy@icici", "q123456789@ybl", "merchant.coffee@okaxis", "gpay-1234@okhdfcbank")) assertEquals(PayeeKind.BUSINESS, PayeeKinds.of(v), v)
        for (v in listOf("ravi.kumar@ybl", "shop@okhdfc", "swiggy@icici", "q12@ybl")) assertEquals(PayeeKind.UNKNOWN, PayeeKinds.of(v), "$v could be either")
    }

    @Test
    fun noAddressMeansUnknown() {
        assertEquals(PayeeKind.UNKNOWN, PayeeKinds.of(null)); assertEquals(PayeeKind.UNKNOWN, PayeeKinds.of("")); assertEquals(PayeeKind.UNKNOWN, PayeeKinds.of("Swiggy")); assertEquals(PayeeKind.UNKNOWN, PayeeKinds.of("9876543210"), "a bare number is not an address")
    }
}

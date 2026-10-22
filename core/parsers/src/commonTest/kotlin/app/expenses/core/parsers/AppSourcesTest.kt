package app.expenses.core.parsers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppSourcesTest {
    @Test
    fun onlyTheListedPaymentAppsAreReadAndAnythingElseIsNot() {
        assertEquals("GPAY", AppSources.nameOf("com.google.android.apps.nbu.paisa.user")); assertEquals("PHONEPE", AppSources.nameOf("com.phonepe.app"))
        assertNull(AppSources.nameOf("com.whatsapp")); assertNull(AppSources.nameOf("com.google.android.apps.messaging")); assertNull(AppSources.nameOf(""))
        assertEquals("TEST", AppSources.nameOf("x.y", mapOf("x.y" to "TEST")))
    }

    @Test
    fun theTitleAndTheTextAreJoinedWithoutRepeatingOneInsideTheOther() {
        assertEquals("Paid ₹250. to Swiggy", AppSources.words("Paid ₹250", "to Swiggy"))
        assertEquals("Paid ₹250 to Swiggy", AppSources.words("Paid ₹250", "Paid ₹250 to Swiggy"), "the title is inside the text")
        assertEquals("Paid ₹250 to Swiggy", AppSources.words("Paid ₹250 to Swiggy", "to Swiggy"), "the text is inside the title")
        assertEquals("Paid ₹250 to Swiggy", AppSources.words("Paid ₹250", "short", "Paid ₹250 to Swiggy"), "the expanded text wins over the short one")
        assertEquals("Hello", AppSources.words(null, "Hello")); assertEquals("", AppSources.words(null, null)); assertEquals("A", AppSources.words("A", "A"))
    }

    @Test
    fun theirNamesAreOnTheCaptureListSoTheClassifierAcceptsThem() {
        for (root in AppSources.PACKAGES.values) assertTrue(root in SenderRules.BUILT_IN, root)
        val r = CaptureClassifier.classify("GPAY", "Paid ₹250 to Swiggy")
        assertTrue(r is CaptureOutcome.Transaction, r.toString())
        assertEquals(Direction.DEBIT, (r as CaptureOutcome.Transaction).alert.direction); assertEquals(25_000, r.alert.amountMinor); assertEquals("Google Pay", r.issuer)
    }

    @Test
    fun typicalPaymentAppWordingIsReadInTheRightDirectionAndOtherNotificationsAreNotTransactions() {
        fun dir(t: String) = (CaptureClassifier.classify("PHONEPE", t) as? CaptureOutcome.Transaction)?.alert?.direction
        assertEquals(Direction.DEBIT, dir("Paid ₹1,250 to Swiggy"))
        assertEquals(Direction.CREDIT, dir("Received ₹500 from Ravi Kumar"))
        assertEquals(Direction.CREDIT, dir("₹500 received from Ravi"))
        assertEquals(Direction.CREDIT, dir("Chandu. sent ₹10 to you."), "the owner's real PhonePe wording for money in")
        assertEquals(Direction.CREDIT, dir("Anita paid you ₹120 for lunch")); assertEquals(Direction.CREDIT, dir("Ravi sent you Rs.50"))
        assertEquals(Direction.DEBIT, dir("You paid ₹120 to Anita")); assertEquals(Direction.DEBIT, dir("Sent ₹500 to Ravi"))
        assertNull(dir("Get 10% cashback on your next recharge!"))
        assertNull(dir("Your OTP is 123456"))
        assertNull(dir("Payment of ₹250 failed"))
    }
}

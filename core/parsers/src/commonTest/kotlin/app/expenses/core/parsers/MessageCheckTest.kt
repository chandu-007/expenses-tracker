package app.expenses.core.parsers

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** All texts, names and numbers here are invented, in the shapes of the public sample corpus. */
class MessageCheckTest {
    private val today = LocalDate(2026, 11, 11)
    private val debit = "Rs.1,250.00 debited from A/c XX1234 on 05-10-26 to VPA shop@okhdfc (UPI Ref 628374659283). Not you? Call 18002586161"
    private fun check(sender: String?, body: String, extra: Set<String> = emptySet()) = MessageCheck.read(sender, body, extra, today)

    @Test
    fun aBankTextFromAListedSenderIsReadFieldByField() {
        val r = check("AX-HDFCBK-S", debit)
        assertEquals(MessageCheck.Verdict.TRANSACTION, r.verdict)
        assertEquals(MessageCheck.SenderStatus.ON_LIST, r.sender); assertEquals("HDFCBK", r.senderRoot); assertEquals("HDFC Bank", r.issuer)
        assertEquals(true, r.moneyOut); assertEquals(125_000L, r.amountMinor); assertEquals("INR", r.currency)
        assertEquals("1234", r.accountDigits); assertTrue(r.hasReference)
        assertFalse(r.bankFromWords, "the sender named the bank")
        assertNotNull(r.rawPayee)
    }

    @Test
    fun aNameTheUserAddedIsOnTheirList() {
        val r = check("AX-MYBANK-S", debit, setOf("MYBANK"))
        assertEquals(MessageCheck.SenderStatus.ADDED_BY_YOU, r.sender)
        assertEquals(MessageCheck.Verdict.TRANSACTION, r.verdict)
    }

    @Test
    fun aNameOnNoListIsSkippedUnreadAndTheOfferToTrustItShowsWhatItWouldBe() {
        val r = check("AX-SHOPXX-S", debit)
        assertEquals(MessageCheck.Verdict.IGNORED, r.verdict); assertEquals(IgnoreReason.NOT_ALLOWLISTED, r.ignoreReason)
        assertEquals(MessageCheck.SenderStatus.NOT_ON_LIST, r.sender); assertEquals("SHOPXX", r.senderRoot)
        assertNull(r.amountMinor, "nothing of the words is taken from a name that is not trusted")
        val would = assertNotNull(r.ifAdded, "what it would be if the name were trusted")
        assertEquals(MessageCheck.Verdict.TRANSACTION, would.verdict); assertEquals(125_000L, would.amountMinor)
        assertNull(check("AX-HDFCBK-S", debit).ifAdded, "a listed sender has no such offer")
    }

    @Test
    fun aPhoneNumberIsAPersonAndNeverRead() {
        val r = check("+919876543210", debit)
        assertEquals(MessageCheck.SenderStatus.NOT_A_HEADER, r.sender)
        assertEquals(IgnoreReason.PERSONAL_SENDER, r.ignoreReason); assertNull(r.amountMinor); assertNull(r.ifAdded)
    }

    @Test
    fun noSenderMeansTheWordsMustNameABankOrLookLikeAnAlert() {
        val named = check(null, "HDFC Bank: $debit")
        assertEquals(MessageCheck.SenderStatus.NOT_GIVEN, named.sender); assertEquals("HDFC Bank", named.issuer); assertTrue(named.bankFromWords)
        assertEquals(MessageCheck.Verdict.TRANSACTION, named.verdict)
        val shaped = check("", "Rs.250.00 debited from a/c XX1234 on 05-10-26 at TEST SHOP. Ref 123456789012.")
        assertEquals(MessageCheck.Verdict.TRANSACTION, shaped.verdict); assertNull(shaped.issuer, "no bank could be told from the words")
        val friend = check(null, "I sent you Rs 500 for lunch yesterday")
        assertEquals(MessageCheck.Verdict.IGNORED, friend.verdict); assertEquals(IgnoreReason.NOT_A_TRANSACTION, friend.ignoreReason)
    }

    @Test
    fun anOtpABillADeclinedPaymentAndACreditAreEachTold() {
        assertEquals(IgnoreReason.OTP, check("AX-HDFCBK-S", "123456 is the OTP for your transaction of Rs.1,250.00 at AMAZON. Do not share it with anyone.").ignoreReason)
        assertEquals(IgnoreReason.DECLINED, check("AX-HDFCBK-S", "Transaction of Rs.500.00 on card ending 4321 was declined due to insufficient funds.").ignoreReason)
        val bill = check("AX-HDFCBK-S", "Your HDFC Bank credit card statement is generated. Total amount due Rs. 4,500.00 by 12-11-2026.")
        assertEquals(MessageCheck.Verdict.BILL, bill.verdict); assertEquals(BillKind.CREDIT_CARD, bill.billKind); assertEquals(450_000L, bill.amountMinor)
        val credit = check("AX-HDFCBK-S", "Update! INR 85,000.00 deposited in HDFC Bank A/c XX1234 on 01-OCT-26 for NEFT Cr-HDFC0000001-ACME SOFTWARE PVT LTD-SALARY. Avl bal INR 1,20,000.00.")
        assertEquals(MessageCheck.Verdict.TRANSACTION, credit.verdict); assertEquals(false, credit.moneyOut)
    }

    @Test
    fun theSameTextIsJudgedLikeRealIntake() {
        // the dry run and the real intake share PasteRules.classify, so for any sender and words they agree
        val cases = listOf("AX-HDFCBK-S" to debit, "AX-SHOPXX-S" to debit, "+919876543210" to debit, "" to debit, "" to "HDFC Bank: $debit", "" to "hello there")
        for ((sender, body) in cases) {
            val real = PasteRules.classify(sender, body, emptySet(), today)
            val dry = check(sender, body)
            when (real) {
                null -> assertEquals(IgnoreReason.NOT_A_TRANSACTION, dry.ignoreReason, "$sender / $body")
                is CaptureOutcome.Ignored -> assertEquals(real.reason, dry.ignoreReason, "$sender / $body")
                is CaptureOutcome.Transaction -> assertEquals(MessageCheck.Verdict.TRANSACTION, dry.verdict, "$sender / $body")
                is CaptureOutcome.Bill -> assertEquals(MessageCheck.Verdict.BILL, dry.verdict, "$sender / $body")
            }
        }
    }

    @Test
    fun digitsAreMaskedToTheLastTwoForTheDisplay() {
        assertEquals("••34", MessageCheck.maskedDigits("1234"))
        assertEquals("•34", MessageCheck.maskedDigits("034"))
        assertEquals("••", MessageCheck.maskedDigits("12"))
        assertNull(MessageCheck.maskedDigits(null)); assertNull(MessageCheck.maskedDigits("")); assertNull(MessageCheck.maskedDigits("XX"))
        // a masked value never holds the digits it hides
        assertFalse(MessageCheck.maskedDigits("987654")!!.contains("98"))
    }

    @Test
    fun everyIgnoreReasonHasAPlainSentenceWithNoNumbersInIt() {
        for (reason in IgnoreReason.entries) for (sender in MessageCheck.SenderStatus.entries) {
            val s = MessageCheck.ignoredBecause(reason, sender)
            assertTrue(s.length > 20 && s.endsWith("."), s)
            assertTrue(s.none { it.isDigit() }, s)
        }
    }

    @Test
    fun theReadingHoldsNoStoreAndNoClockSoItCannotSaveAnything() {
        // the function is pure: reading the same words twice gives the same answer, and a second call has no memory of the first (nothing was counted or kept)
        assertEquals(check("AX-HDFCBK-S", debit), check("AX-HDFCBK-S", debit))
    }
}

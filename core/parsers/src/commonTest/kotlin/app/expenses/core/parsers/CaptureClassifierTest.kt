package app.expenses.core.parsers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptureClassifierTest {
    private val debit = "Rs.1,250.00 debited from A/c XX1234 on 05-10-26 to VPA shop@okhdfc (UPI Ref 628374659283). Not you? Call 18002586161"

    @Test
    fun theHeaderRootIgnoresThePrefixAndSuffixAndCase() {
        for (s in listOf("AX-HDFCBK-S", "VM-HDFCBK", "jd-hdfcbk-t", "BZ-HDFCBK-P", "HDFCBK", " AD-HDFCBK-G ")) assertEquals("HDFCBK", SenderRules.headerRoot(s), s)
        assertEquals("SBIINB", SenderRules.headerRoot("VK-SBIINB-S"))
    }

    @Test
    fun aPersonalNumberOrNothingHasNoHeader() {
        for (s in listOf("+919876543210", "9876543210", "+91 98765 43210", "", "   ", "1800-258-6161", "12", "bad sender!", "a".repeat(40))) assertNull(SenderRules.headerRoot(s), "'$s'")
    }

    @Test
    fun aTransactionFromAnAllowlistedHeaderIsCapturedWithItsIssuer() {
        val r = CaptureClassifier.classify("AX-HDFCBK-S", debit) as CaptureOutcome.Transaction
        assertEquals("HDFC Bank", r.issuer); assertEquals("HDFCBK", r.headerRoot)
        assertEquals(125_000, r.alert.amountMinor); assertEquals(Direction.DEBIT, r.alert.direction); assertEquals("1234", r.alert.accountLast4)
        assertEquals(Confidence.GENERIC, r.alert.confidence)
    }

    @Test
    fun theSenderIsCheckedBeforeTheBodyIsReadSoAPersonOrAnUnknownHeaderIsNeverParsed() {
        assertEquals(CaptureOutcome.Ignored(IgnoreReason.PERSONAL_SENDER, null), CaptureClassifier.classify("+919876543210", debit))
        assertEquals(CaptureOutcome.Ignored(IgnoreReason.NOT_ALLOWLISTED, "SHOPXX"), CaptureClassifier.classify("AX-SHOPXX-S", debit))
        // the same text that would be captured from a bank is ignored from anyone else, even a convincing lookalike
        assertEquals(CaptureOutcome.Ignored(IgnoreReason.NOT_ALLOWLISTED, "HDFCBKK"), CaptureClassifier.classify("AX-HDFCBKK-S", debit))
    }

    @Test
    fun anOtpFromABankIsIgnoredAndCountedAsAnOtpNeverAsATransaction() {
        val otp = "123456 is the OTP for your transaction of Rs.1,250.00 at AMAZON. Do not share it with anyone."
        assertEquals(CaptureOutcome.Ignored(IgnoreReason.OTP, "HDFCBK"), CaptureClassifier.classify("AX-HDFCBK-S", otp))
    }

    @Test
    fun noticesAndDeclinedPaymentsAreIgnoredWithTheirOwnReason() {
        // a statement used to be ignored as a due notice; it is now a bill to add (see BillParserTest)
        assertTrue(CaptureClassifier.classify("AX-HDFCBK-S", "Your HDFC Bank credit card statement is generated. Total amount due Rs. 4,500.00 by 12-11-2026.") is CaptureOutcome.Bill)
        assertEquals(CaptureOutcome.Ignored(IgnoreReason.NOT_A_TRANSACTION, "HDFCBK"), CaptureClassifier.classify("AX-HDFCBK-S", "Dear customer, update your KYC to continue banking."))
        assertEquals(CaptureOutcome.Ignored(IgnoreReason.DECLINED, "HDFCBK"), CaptureClassifier.classify("AX-HDFCBK-S", "Transaction of Rs.500.00 on card ending 4321 was declined due to insufficient funds."))
    }

    @Test
    fun aHeaderThePersonAddedIsCapturedUnderItsOwnNameAndTheBuiltInListIsNotOverridden() {
        assertEquals("MYCOOP", (CaptureClassifier.classify("VM-MYCOOP-S", debit, setOf("mycoop")) as CaptureOutcome.Transaction).issuer)
        assertEquals("HDFC Bank", (CaptureClassifier.classify("VM-HDFCBK-S", debit, setOf("hdfcbk")) as CaptureOutcome.Transaction).issuer)
    }

    @Test
    fun everyBuiltInRootIsAValidUpperCaseHeaderWithAName() {
        for ((root, name) in SenderRules.BUILT_IN) { assertEquals(root, SenderRules.headerRoot("AX-$root-S")); assertTrue(name.isNotBlank()) }
    }

    @Test
    fun moneyThatMovedStaysATransactionEvenWhenTheTextAlsoQuotesWhatIsDue() {
        val credited = "Rs.10,000.00 credited to your HDFC Bank Credit Card ending 1234 on 15-10-26. Total Amt Due Rs 5,000, Due Date 25-10-26."
        assertTrue(CaptureClassifier.classify("AX-HDFCBK-S", credited, today = kotlinx.datetime.LocalDate(2026, 10, 15)) is CaptureOutcome.Transaction)
        val spent = "Rs.900.00 spent on your HDFC Bank Credit Card ending 1234 at SHOP on 15-10-26. Total Amt Due Rs 5,000, Due Date 25-10-26."
        assertTrue(CaptureClassifier.classify("AX-HDFCBK-S", spent, today = kotlinx.datetime.LocalDate(2026, 10, 15)) is CaptureOutcome.Transaction)
    }

    @Test
    fun aPromisedDebitIsStillABill() {
        val emi = "Rs.5,400.00 will be debited from your A/c XX1234 on 14-11-26 towards loan EMI."
        assertTrue(CaptureClassifier.classify("AX-HDFCBK-S", emi, today = kotlinx.datetime.LocalDate(2026, 10, 15)) is CaptureOutcome.Bill)
    }
}

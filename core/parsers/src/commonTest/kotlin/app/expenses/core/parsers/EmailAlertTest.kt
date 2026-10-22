package app.expenses.core.parsers

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Bank alert emails as an email app's notification shows them. **Every wording here is plausible, written from how Indian banks word their alerts; none was copied
 * from a real email**, and how Gmail, Outlook or Yahoo fill a notification's fields was not checked against a real device either. Direction is decided by the very
 * same [GenericAlertParser] as for texts (nothing about emails is re-invented), so these tests pin that the email wordings come out the right way round.
 */
class EmailAlertTest {
    private val today = LocalDate(2026, 10, 22)
    private fun classify(name: String, body: String, extra: Set<String> = emptySet()) = CaptureClassifier.classifyEmail(listOf(name), body, extra, today)
    private fun dir(name: String, body: String) = (classify(name, body) as? CaptureOutcome.Transaction)?.alert?.direction

    @Test
    fun aDebitEmailIsMoneyOut() {
        val r = classify("HDFC Bank", "HDFC Bank. Rs. 1,250.00 is debited from your account XX1234 on 21-10-2026 to VPA shop@upi. UPI Ref No 629312345678.")
        assertTrue(r is CaptureOutcome.Transaction, r.toString())
        assertEquals(Direction.DEBIT, r.alert.direction); assertEquals(125_000, r.alert.amountMinor); assertEquals("1234", r.alert.accountLast4)
        assertEquals("HDFC Bank", r.issuer); assertEquals("HDFCBK", r.headerRoot); assertEquals("629312345678", r.alert.reference)
    }

    @Test
    fun aCreditEmailIsMoneyIn() {
        assertEquals(Direction.CREDIT, dir("ICICI Bank Alerts", "Your a/c XX1234 has been credited with INR 5,000.00 on 21-Oct-26. Info: NEFT-SALARY."))
        assertEquals(Direction.CREDIT, dir("Axis Bank", "Dear Customer, you have received ₹500 in your account XX9876 from RAVI KUMAR through UPI."))
        assertEquals(Direction.CREDIT, dir("Kotak Mahindra Bank", "Rs.500 sent to you by Ravi via UPI. Ref 629312345678"))
        assertEquals(Direction.CREDIT, dir("State Bank of India", "Dear Customer, Rs 2,000 has been deposited in your account XX4455."))
    }

    @Test
    fun aCardSpendEmailIsMoneyOutAndARefundIsMoneyIn() {
        val spend = classify("HDFC Bank Credit Cards", "Transaction alert: INR 899.00 spent on your Credit Card XX4321 at AMAZON on 21-10-2026.")
        assertTrue(spend is CaptureOutcome.Transaction, spend.toString())
        assertEquals(Direction.DEBIT, spend.alert.direction); assertEquals(89_900, spend.alert.amountMinor); assertEquals("4321", spend.alert.accountLast4)
        assertEquals(Direction.CREDIT, dir("HDFC Bank Credit Cards", "Refund of Rs 300 has been credited to your Credit Card XX4321 for AMAZON."))
        assertEquals(Direction.CREDIT, dir("American Express", "A reversal of INR 1,200.00 for your Card ending 31005 at UBER has been processed."))
    }

    @Test
    fun theSubjectAloneHasNoAmountSoTheWordsCarryTheSnippetToo() {
        // Gmail shows the subject and the start of the message: the words are the sender, the subject and the expanded text
        val words = AppSources.words("HDFC Bank", "Transaction alert for your Credit Card", null) // only the subject: nothing to read
        assertNull((classify("HDFC Bank", words) as? CaptureOutcome.Transaction))
        val joined = AppSources.emailNotes("HDFC Bank", "Transaction alert for your Credit Card", "Transaction alert for your Credit Card\nDear Customer, Rs. 899.00 was spent on your card XX4321 at AMAZON.", null, "me@gmail.com").single()
        assertEquals(Direction.DEBIT, (classify(joined.senderNames.first(), joined.words) as CaptureOutcome.Transaction).alert.direction)
        assertTrue(!joined.words.contains("me@gmail.com"), "the receiving account is never kept")
    }

    @Test
    fun boilerplateBeforeTheAmountDoesNotHideIt() {
        val body = "Important information regarding your account. Please do not share your OTP, password or card details with anyone, including bank staff. " +
            "HDFC Bank never asks for them. Dear Customer, Rs. 450.00 has been debited from your account XX1234 on 21-10-2026 at SWIGGY."
        assertEquals(Direction.DEBIT, dir("HDFC Bank", body))
    }

    @Test
    fun aNeverShareYourOtpNoticeDoesNotMakeAnAlertAnOtpButARealOtpEmailStaysIgnored() {
        val notice = "Never share your OTP with anyone. "
        assertEquals(Direction.DEBIT, dir("ICICI Bank", "Rs 99.00 spent on your card XX1111 at CAFE. $notice"))
        assertEquals(CaptureOutcome.Ignored(IgnoreReason.OTP, "ICICIB"), classify("ICICI Bank", "482913 is the OTP for your payment of Rs 99.00 at CAFE. $notice"))
    }

    @Test
    fun anEmailFromNoListedBankIsNeverRead() {
        val debit = "Rs. 1,250.00 is debited from your account XX1234 on 21-10-2026."
        for (name in listOf("Ravi Kumar", "Swiggy", "Amazon Pay Offers", "no-reply@shop.example", "", "slice", "Your Friend at Work"))
            assertEquals(CaptureOutcome.Ignored(IgnoreReason.NOT_ALLOWLISTED, null), classify(name, debit), name)
        assertEquals(CaptureOutcome.Ignored(IgnoreReason.NOT_ALLOWLISTED, null), classify("Google Pay", debit), "a payment app is not a bank sender of email")
    }

    @Test
    fun anAddressInTheWordsOrTheNameOfABankIsEnough() {
        for (name in listOf("HDFC Bank", "hdfc bank alerts", "alerts@hdfcbank.net", "HDFCBank InstaAlerts", "HDFC BANK - Credit Cards"))
            assertEquals("HDFCBK", SenderRules.emailIssuer(name), name)
        assertEquals("ICICIB", SenderRules.emailIssuer("credit_cards@icicibank.com")); assertEquals("AXISBK", SenderRules.emailIssuer("Axis Bank Ltd"))
        assertEquals("SBICRD", SenderRules.emailIssuer("SBI Card")); assertEquals("SBIINB", SenderRules.emailIssuer("State Bank of India")); assertEquals("SBIINB", SenderRules.emailIssuer("SBI"))
        assertEquals("UNIONB", SenderRules.emailIssuer("Union Bank of India")); assertEquals("BOIIND", SenderRules.emailIssuer("Bank of India"))
        assertNull(SenderRules.emailIssuer("Business Class")); assertNull(SenderRules.emailIssuer("xhdfcx")); assertNull(SenderRules.emailIssuer("Chandrashekhar"))
    }

    @Test
    fun aNameThePersonAddedAMatchesTheEmailSenderToo() {
        val debit = "Rs. 250.00 spent on your card XX1111 at SHOP."
        assertEquals(CaptureOutcome.Ignored(IgnoreReason.NOT_ALLOWLISTED, null), classify("MyCoop Bank", debit))
        val r = classify("MyCoop Bank", debit, setOf("mycoop"))
        assertTrue(r is CaptureOutcome.Transaction, r.toString()); assertEquals("MYCOOP", r.issuer)
        assertNull(SenderRules.emailIssuer("Co-op", setOf("co")), "a name shorter than four letters is not matched")
    }

    @Test
    fun oneTimePasswordEmailsAndPlainBankNewsAreIgnored() {
        val otp = classify("HDFC Bank", "Your OTP for the transaction of Rs. 1,250.00 at AMAZON is 482913. Do not share it.")
        assertEquals(CaptureOutcome.Ignored(IgnoreReason.OTP, "HDFCBK"), otp)
        val news = classify("HDFC Bank", "Introducing our new savings account with higher interest. Visit your nearest branch.")
        assertEquals(CaptureOutcome.Ignored(IgnoreReason.NOT_A_TRANSACTION, "HDFCBK"), news)
    }

    @Test
    fun aStatementEmailGoesToBillsNotToTransactions() {
        val r = classify("HDFC Bank Credit Cards", "Your HDFC Bank Credit Card ending 0862 statement is generated. Total Amt Due Rs.12,345.00, Min Amt Due Rs.617.00, Due Date 25-Oct-2026.")
        assertTrue(r is CaptureOutcome.Bill, r.toString()); assertEquals(BillKind.CREDIT_CARD, r.notice.kind); assertEquals(1_234_500L, r.notice.amountMinor)
    }

    @Test
    fun aDeclinedPaymentEmailIsIgnored() {
        assertEquals(CaptureOutcome.Ignored(IgnoreReason.DECLINED, "ICICIB"), classify("ICICI Bank", "Transaction of Rs.500.00 on your card ending 4321 was declined due to insufficient funds."))
    }

    @Test
    fun eachLineOfAnInboxStyleNotificationIsOneEmailReadAlone() {
        val notes = AppSources.emailNotes("3 new messages", null, null, listOf("HDFC Bank  Rs. 120.00 debited from a/c XX1234", "Zomato  Your order is on the way", "ICICI Bank  Rs 500 credited to your a/c XX5678"), null)
        assertEquals(3, notes.size)
        assertEquals(listOf("HDFCBK", null, "ICICIB"), notes.map { n -> n.senderNames.firstNotNullOfOrNull { SenderRules.emailIssuer(it) } })
        val outcomes = notes.map { CaptureClassifier.classifyEmail(it.senderNames, it.words, emptySet(), today) }
        assertEquals(Direction.DEBIT, (outcomes[0] as CaptureOutcome.Transaction).alert.direction)
        assertEquals(CaptureOutcome.Ignored(IgnoreReason.NOT_ALLOWLISTED, null), outcomes[1])
        assertEquals(Direction.CREDIT, (outcomes[2] as CaptureOutcome.Transaction).alert.direction)
    }

    @Test
    fun theWordsAreCappedAtTwoThousandAndANotificationWithNothingGivesNoEmail() {
        val long = "Rs. 10.00 debited " + "x".repeat(5000)
        assertEquals(AppSources.EMAIL_WORDS_CAP, AppSources.emailNotes("HDFC Bank", "s", long, null, null).single().words.length)
        assertEquals(emptyList(), AppSources.emailNotes(null, "", null, emptyList(), null))
        assertEquals("HDFC Bank. Rs. 10 debited", AppSources.emailNotes("HDFC Bank", "Rs. 10 debited", "Rs. 10 debited", null, null).single().words, "the subject inside the expanded text is not repeated")
    }

    @Test
    fun theEmailAppsAreNamedAndOnlyThePaymentAppsAreOnTheOldList() {
        assertEquals("GMAIL", AppSources.nameOf("com.google.android.gm")); assertEquals("OUTLOOK", AppSources.nameOf("com.microsoft.office.outlook")); assertEquals("YAHOO", AppSources.nameOf("com.yahoo.mobile.client.android.mail"))
        assertEquals("GMAIL", AppSources.emailNameOf("com.google.android.gm")); assertNull(AppSources.emailNameOf("com.phonepe.app")); assertNull(AppSources.emailNameOf("com.whatsapp"))
        assertTrue("com.google.android.gm" !in AppSources.PACKAGES, "payment apps and email apps are two lists with two switches")
        assertTrue(AppSources.isEmailName("gmail") && AppSources.isEmailName(" OUTLOOK ") && !AppSources.isEmailName("GPAY") && !AppSources.isEmailName("HDFCBK"))
    }
}

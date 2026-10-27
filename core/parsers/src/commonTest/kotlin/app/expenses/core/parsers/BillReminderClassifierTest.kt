package app.expenses.core.parsers

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 0.7.1: a bill reminder is a bill, never a debit. The first text is the one that reached an iPhone as a debit in To review: "Kindly ignore, if already paid" made the transaction
 * parser read "paid" as money going out, and a parsed alert skipped the bill rules. The phone number is invented.
 */
class BillReminderClassifierTest {
    private val today = LocalDate(2026, 10, 22)
    private val extra = setOf("WATERX", "GASXXX", "DTHXXX")
    private val jio = "Your bill dated 21-10-2026 for JioHome connection with JioFixedVoice Number +914000000000 is due for payment on 30-10-2026.\n" +
        "Total amount payable : Rs.706.82.\nTo pay your bill, click www.jio.com/r/Vbu7k5JBt\nOverdue charges will be applicable, in case of delay in payment. Kindly ignore, if already paid."

    private fun bill(sender: String, body: String): CaptureOutcome.Bill = assertIs<CaptureOutcome.Bill>(CaptureClassifier.classify(sender, body, extra, today), "'$body'")
    private fun txn(sender: String, body: String): CaptureOutcome.Transaction = assertIs<CaptureOutcome.Transaction>(CaptureClassifier.classify(sender, body, extra, today), "'$body'")

    @Test
    fun theJioHomeReminderIsABillWithBillerAmountAndDueDate() {
        val b = bill("JIOPAY", jio)
        assertEquals("Jio", b.notice.biller); assertEquals(70_682L, b.notice.amountMinor); assertEquals(LocalDate(2026, 10, 30), b.notice.dueDate); assertEquals(BillKind.BROADBAND, b.notice.kind)
        assertEquals("Jio", b.issuer)
        // and the transaction parser alone no longer reads it as a debit
        assertIs<ParseResult.NotATransaction>(GenericAlertParser.parse(jio, "JIOPAY"))
        // from the same sender with a prefix and suffix
        assertIs<CaptureOutcome.Bill>(CaptureClassifier.classify("JD-JIOPAY-S", jio, extra, today))
    }

    @Test
    fun theSameReminderWithoutTheIgnoreLineOrWithOtherWordingIsStillABill() {
        bill("JIOPAY", jio.substringBefore(" Kindly ignore"))
        bill("JIOPAY", jio.replace("Kindly ignore, if already paid.", "Please ignore this message if you have already made the payment."))
        bill("JIOPAY", jio.replace("Kindly ignore, if already paid.", "If payment is already made, please disregard this reminder."))
        bill("JIOPAY", jio.replace("Kindly ignore, if already paid.", "Ignore if paid."))
    }

    @Test
    fun billReminderTextsOfEveryKindBecomeBills() {
        data class Case(val sender: String, val body: String, val kind: BillKind, val minor: Long, val due: LocalDate?)
        val cases = listOf(
            Case("AIRTEL", "Dear customer, your Airtel postpaid bill of Rs.799.00 is generated. Pay by 25-10-2026 to avoid late fee. Ignore if already paid.", BillKind.MOBILE, 79_900, LocalDate(2026, 10, 25)),
            Case("BESCOM", "BESCOM: Your electricity bill of Rs.1,234.00 for consumer no 1234567890 is due on 25-10-2026. Kindly ignore, if already paid.", BillKind.ELECTRICITY, 123_400, LocalDate(2026, 10, 25)),
            Case("WATERX", "Your water bill of Rs 420.00 is due on 28-10-2026. Please pay at the Jal Board portal. Ignore if paid.", BillKind.WATER, 42_000, LocalDate(2026, 10, 28)),
            Case("GASXXX", "Your PNG gas bill of Rs 650.00 is payable by 28-10-2026. Please pay on time. If you have already paid, ignore this.", BillKind.GAS, 65_000, LocalDate(2026, 10, 28)),
            Case("DTHXXX", "Tata Play: Rs 350 is due on 26-10-2026 for your DTH account. Pay now to avoid disconnection. Kindly ignore if already paid.", BillKind.DTH, 35_000, LocalDate(2026, 10, 26)),
            Case("HDFCBK", "HDFC Bank Credit Card XX0862: Total amount due Rs.12,345.00, minimum amount due Rs.617.00. Pay by 25-10-2026. Please ignore if already paid.", BillKind.CREDIT_CARD, 1_234_500, LocalDate(2026, 10, 25)),
            Case("LICIND", "LIC: Premium of Rs 12,000 for policy 123456789 is due on 28-10-2026. Please pay to keep your policy active. Ignore if already paid.", BillKind.INSURANCE, 1_200_000, LocalDate(2026, 10, 28)),
            Case("HDFCBK", "HDFC Bank: Your loan EMI of Rs 8,500 is due on 05-11-2026. Please keep sufficient balance in A/c XX1234.", BillKind.LOAN_EMI, 850_000, LocalDate(2026, 11, 5)),
            Case("JIOPAY", "Payment of Rs.706.82 is due on 30-10-2026 for your Jio bill. Pay now at jio.com.", BillKind.MOBILE, 70_682, LocalDate(2026, 10, 30)),
            Case("AIRTEL", "Your Airtel bill is overdue. Amount payable Rs.1,102.50. Please pay immediately to avoid disconnection. Kindly ignore if already paid.", BillKind.MOBILE, 110_250, null),
            Case("ICICIB", "ICICI Bank Credit Card XX4321 statement is generated. Total Amount Due Rs.5,000.00. Minimum Amount Due Rs.250.00. Payment due date 20-10-2026.", BillKind.CREDIT_CARD, 500_000, LocalDate(2026, 10, 20)),
        )
        for (c in cases) {
            val b = bill(c.sender, c.body)
            assertEquals(c.kind, b.notice.kind, c.body); assertEquals(c.minor, b.notice.amountMinor, c.body); assertEquals(c.due, b.notice.dueDate, c.body)
        }
    }

    @Test
    fun realMoneyMovementsStayTransactions() {
        val upi = txn("AX-HDFCBK-S", "Rs.200.00 debited from A/c XX1234 on 21-10-26 to VPA shop@okhdfc (UPI Ref 628374659283). Not you? Call 18002586161")
        assertEquals(Direction.DEBIT, upi.alert.direction)
        assertEquals(Direction.DEBIT, txn("JIOPAY", "Rs 706.82 paid to Jio via UPI. Ref 628374659283").alert.direction)
        assertEquals(Direction.DEBIT, txn("SBIINB", "Your a/c is debited by Rs.349.00 on 21-10-26 and credited to a/c no. XXXXXX1234 (UPI Ref no 628374659283)").alert.direction)
        assertEquals(Direction.DEBIT, txn("HDFCBK", "Spent Rs.1,499.00 on HDFC Bank Credit Card XX0862 at AMAZON on 21-10-2026. Avl limit Rs.50,000.").alert.direction)
        assertEquals(Direction.DEBIT, txn("ICICIB", "ICICI Bank Acct XX123 debited for Rs 2,500.00 on 21-Oct-26; Info: NEFT-RENT. Available Bal is Rs 10,000.").alert.direction)
        // a receipt is never a bill; the transaction rules keep deciding what it is, exactly as before
        assertIs<CaptureOutcome.Transaction>(CaptureClassifier.classify("JIOPAY", "Payment of Rs.706.82 received for your Jio bill. Thank you.", extra, today))
        assertEquals(Direction.DEBIT, txn("JIOPAY", "Payment of Rs.706.82 successful for your Jio bill.").alert.direction)
        // a card payment acknowledgement with no movement word was never a transaction (no direction); it must not turn into a bill
        assertIs<CaptureOutcome.Ignored>(CaptureClassifier.classify("HDFCBK", "Thank you for your payment of Rs.5,000.00 towards your HDFC Bank Credit Card XX0862 on 21-10-2026.", extra, today))
        assertEquals(Direction.CREDIT, txn("HDFCBK", "Payment of Rs 12,345.00 received towards your HDFC Bank Credit Card ending 0862. Thank you.").alert.direction)
    }

    @Test
    fun aTextThatMixesADebitWithABillStaysATransaction() {
        val t = txn("AX-HDFCBK-S", "Rs.500.00 debited from A/c XX1234 on 21-10-26. Your Jio bill of Rs.1200 is due on 25-10-2026.")
        assertEquals(Direction.DEBIT, t.alert.direction); assertEquals(50_000L, t.alert.amountMinor)
        val u = txn("JIOPAY", "Rs 706.82 paid to Jio on 21-10-2026. Your next bill of Rs 706.82 is due on 21-11-2026. Ref 628374659283")
        assertEquals(70_682L, u.alert.amountMinor)
        // a debit that is only promised is still a bill
        assertIs<CaptureOutcome.Bill>(CaptureClassifier.classify("HDFCBK", "Rs.5,400.00 will be debited from your A/c XX1234 on 05-11-2026 towards loan EMI.", extra, today))
    }

    @Test
    fun aStatementThatQuotesPaymentsReceivedIsABillNotACredit() {
        val b = bill("HDFCBK", "HDFC Bank Credit Card XX0862 statement is generated. Total amount due Rs.5,000.00, minimum amount due Rs.250.00, due date 25-10-2026. Payments received after 05-10-2026 are not shown.")
        assertEquals(500_000L, b.notice.amountMinor)
    }

    @Test
    fun theSameBillTwiceHasTheSameWordsAndNoAskIsNotABill() {
        assertEquals(bill("JIOPAY", jio).notice, bill("JIOPAY", jio).notice)
        assertTrue(CaptureClassifier.classify("JIOPAY", "Dear customer, update your KYC to keep enjoying services.", extra, today) is CaptureOutcome.Ignored)
    }
}

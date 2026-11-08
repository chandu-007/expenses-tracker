package app.expenses.core.parsers

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Bill-payment confirmations ([BillPayments]) and what [CaptureClassifier] makes of them. The owner's report: "Axis Bank Credit Card bill payment of Rs. 12,345.67 was successful!" (sent by
 * Kotak, the bank the money left) was read as a NEW credit card bill. Every text here is invented. The false friends keep the class the classifier gave them before bill payments were read:
 * their expected outcomes were recorded by running the unchanged classifier first.
 */
class BillPaymentsTest {
    private val today = LocalDate(2026, 11, 8)

    private class Pos(val id: String, val sender: String, val body: String, val side: BillPaymentSide, val kind: BillKind, val amount: Long, val cardBank: String?, val cardLast4: String?, val biller: String?, val payerLast4: String?)

    private class Friend(val id: String, val sender: String, val body: String, val expected: String, val amount: String?, val kind: String?)

    private val positives = listOf(
        Pos("P01", "AX-KOTAKB-S", "Hi Asha Test, Axis Bank Credit Card bill payment of Rs. 12,345.67 was successful! T&C -Kotak811", BillPaymentSide.PAID, BillKind.CREDIT_CARD, 1234567L, "Axis Bank", null, null, null),
        Pos("P02", "AX-KOTAKB-S", "Hi Test User, HDFC Bank Credit Card bill payment of Rs 4,500 was successful! T&C -Kotak811", BillPaymentSide.PAID, BillKind.CREDIT_CARD, 450000L, "HDFC Bank", null, null, null),
        Pos("P03", "VM-HDFCBK-S", "Payment of Rs.8,200.00 received towards your HDFC Bank Credit Card ending 1234 on 05-11-2026. Thank you!", BillPaymentSide.RECEIVED, BillKind.CREDIT_CARD, 820000L, "HDFC Bank", "1234", null, null),
        Pos("P04", "JD-ICICIB-T", "Thank you for your payment of Rs 15,000.00 towards your ICICI Bank credit card XX4321 on 06-Nov-26.", BillPaymentSide.RECEIVED, BillKind.CREDIT_CARD, 1500000L, "ICICI Bank", "4321", null, null),
        Pos("P05", "BP-SBICRD-S", "Your SBI Card payment of Rs 23,456.00 was successful. Thank you.", BillPaymentSide.RECEIVED, BillKind.CREDIT_CARD, 2345600L, "SBI Card", null, null, null),
        Pos("P06", "BP-SBICRD-S", "Your SBI Card payment was successful! Amount Rs.5000 credited to your card ending 4321.", BillPaymentSide.RECEIVED, BillKind.CREDIT_CARD, 500000L, "SBI Card", "4321", null, null),
        Pos("P07", "PHONEPE", "Your credit card bill payment of Rs.7,350 to HDFC Bank Credit Card XX5678 was successful. UPI Ref 628374659283.", BillPaymentSide.PAID, BillKind.CREDIT_CARD, 735000L, "HDFC Bank", "5678", null, null),
        Pos("P08", "PAYTM", "Credit card bill payment successful! Rs 3,999.00 paid for your ICICI Bank credit card ending 8765.", BillPaymentSide.PAID, BillKind.CREDIT_CARD, 399900L, "ICICI Bank", "8765", null, null),
        Pos("P09", "PHONEPE", "CRED credit card bill payment of Rs 9,000 for your Axis Bank card is successful.", BillPaymentSide.PAID, BillKind.CREDIT_CARD, 900000L, "Axis Bank", null, null, null),
        Pos("P10", "AX-AXISBK-S", "Rs 12,000.00 credited to your Axis Bank Credit Card XX1234 on 07-11-2026. Thank you for the payment.", BillPaymentSide.RECEIVED, BillKind.CREDIT_CARD, 1200000L, "Axis Bank", "1234", null, null),
        Pos("P11", "VM-JIOPAY-S", "Payment of Rs.706.82 successful for your Jio bill.", BillPaymentSide.PAID, BillKind.MOBILE, 70682L, null, null, "Jio", null),
        Pos("P12", "AX-BESCOM-S", "Your BESCOM bill payment of Rs 1,340.00 for consumer no 1234567890 is successful.", BillPaymentSide.PAID, BillKind.ELECTRICITY, 134000L, null, null, "BESCOM", null),
        Pos("P13", "AX-KOTAKB-S", "Bill payment of Rs 1,340.00 for BESCOM successful. Ref: 7364528190", BillPaymentSide.PAID, BillKind.ELECTRICITY, 134000L, null, null, "BESCOM", null),
        Pos("P14", "AX-KOTAKB-S", "Your electricity bill payment of Rs 2,150.50 is successful.", BillPaymentSide.PAID, BillKind.ELECTRICITY, 215050L, null, null, null, null),
        Pos("P15", "AX-KOTAKB-S", "Credit card payment of Rs 6,000.00 has been processed.", BillPaymentSide.PAID, BillKind.CREDIT_CARD, 600000L, null, null, null, null),
        Pos("P16", "AX-AXISBK-S", "We have received your payment of Rs 4,000.00 on your Axis Bank Credit Card ending 4455.", BillPaymentSide.RECEIVED, BillKind.CREDIT_CARD, 400000L, "Axis Bank", "4455", null, null),
        Pos("P17", "AD-AIRTEL-S", "Your Airtel postpaid bill payment of Rs.499.00 was successful.", BillPaymentSide.PAID, BillKind.MOBILE, 49900L, null, null, "Airtel", null),
        Pos("P18", "VM-HDFCBK-S", "Rs.15,000.00 credited to your card XX9012 on 08-Nov-2026.", BillPaymentSide.RECEIVED, BillKind.CREDIT_CARD, 1500000L, null, "9012", null, null),
        Pos("P19", "VM-HDFCBK-S", "Payment of Rs. 1,23,456.00 received towards your HDFC Bank Credit Card ending 1234.", BillPaymentSide.RECEIVED, BillKind.CREDIT_CARD, 12345600L, "HDFC Bank", "1234", null, null),
        Pos("P20", "AX-KOTAKB-S", "Hi Test, ICICI Bank Credit Card bill payment of INR 1,23,456.00 was successful! T&C -Kotak811", BillPaymentSide.PAID, BillKind.CREDIT_CARD, 12345600L, "ICICI Bank", null, null, null),
        Pos("P21", "AX-KOTAKB-S", "Hi Test, SBI Card bill payment of ₹5,000.50 was successful! T&C", BillPaymentSide.PAID, BillKind.CREDIT_CARD, 500050L, "SBI Card", null, null, null),
        Pos("P22", "AX-KOTAKB-S", "Hi Test, American Express Card bill payment of Rs.5000 was successful!-Kotak811", BillPaymentSide.PAID, BillKind.CREDIT_CARD, 500000L, "American Express", null, null, null),
        Pos("P23", "BP-ICICIT-S", "Rs 20,000.00 has been credited to your ICICI Bank Credit Card XX3456 towards bill payment. Ref No 5566778899", BillPaymentSide.RECEIVED, BillKind.CREDIT_CARD, 2000000L, "ICICI Bank", "3456", null, null),
        Pos("P24", "VM-HDFCBK-S", "Dear Customer, Rs.2,500.00 debited from A/c XX1234 towards credit card bill payment, successful.", BillPaymentSide.PAID, BillKind.CREDIT_CARD, 250000L, null, null, null, "1234"),
        Pos("P25", "AX-AXISBK-S", "Bill payment of Rs.1,999.00 from your A/c XX0987 to Tata Play was successful.", BillPaymentSide.PAID, BillKind.DTH, 199900L, null, null, "Tata Play", "0987"),
        Pos("P26", "AX-KOTAKB-S", "Bill payment of Rs 799.00 for LIC premium was successful.", BillPaymentSide.PAID, BillKind.INSURANCE, 79900L, null, null, "LIC", null),
        Pos("P27", "AX-KOTAKB-S", "Your payment of Rs 8,500.00 towards loan EMI received.", BillPaymentSide.RECEIVED, BillKind.LOAN_EMI, 850000L, null, null, null, null),
        Pos("P28", "AX-KOTAKB-S", "Bill payment of Rs.650.00 for ACT Fibernet is successful on 07-Nov-2026. Ref: 4455667788", BillPaymentSide.PAID, BillKind.BROADBAND, 65000L, null, null, "ACT Fibernet", null),
        Pos("P29", "AX-KOTAKB-S", "Thank you! Your gas bill payment of Rs 312.00 to Mahanagar Gas was successful.", BillPaymentSide.PAID, BillKind.GAS, 31200L, null, null, "Mahanagar Gas", null),
        Pos("P30", "AX-KOTAKB-S", "Water bill payment of Rs 220.00 was successful.", BillPaymentSide.PAID, BillKind.WATER, 22000L, null, null, null, null),
        Pos("P31", "VM-HDFCBK-S", "Thank you for your payment of Rs.3,000 towards your HDFC Bank credit card ending 1234.", BillPaymentSide.RECEIVED, BillKind.CREDIT_CARD, 300000L, "HDFC Bank", "1234", null, null),
        Pos("P32", "JD-ICICIB-T", "Dear Customer, your credit card bill payment of Rs 5,400.00 from A/c XX123 was successful.", BillPaymentSide.PAID, BillKind.CREDIT_CARD, 540000L, null, null, null, "123"),
        Pos("P33", "PHONEPE", "Payment of Rs 12,000.00 to Axis Bank Credit Card is successful. Ref 4455667788", BillPaymentSide.PAID, BillKind.CREDIT_CARD, 1200000L, "Axis Bank", null, null, null),
        Pos("P34", "PAYTM", "Credit card bill payment of Rs.4,321.00 for card ending 1111 was successful via Paytm UPI.", BillPaymentSide.PAID, BillKind.CREDIT_CARD, 432100L, null, "1111", null, null),
        Pos("P35", "AX-KOTAKB-S", "Hi Test, Axis Bank Credit Card bill payment of Rs. 800.00 has been received. T&C -Kotak811", BillPaymentSide.RECEIVED, BillKind.CREDIT_CARD, 80000L, "Axis Bank", null, null, null),
        Pos("P36", "AX-AXISBK-S", "Payment of Rs 45,000.00 successful for your Axis Bank credit card XX3366.", BillPaymentSide.PAID, BillKind.CREDIT_CARD, 4500000L, "Axis Bank", "3366", null, null),
        Pos("P37", "AX-KOTAKB-S", "Dear Customer, bill payment of Rs.2,000 for HDFC Bank Credit Card ending 4321 done successfully! T&C", BillPaymentSide.PAID, BillKind.CREDIT_CARD, 200000L, "HDFC Bank", "4321", null, null),
        Pos("P38", "AX-KOTAKB-S", "INR 25,000.00 bill payment successful for your SBI Card ending 4321.", BillPaymentSide.PAID, BillKind.CREDIT_CARD, 2500000L, "SBI Card", "4321", null, null),
        Pos("P39", "AX-KOTAKB-S", "Bill payment of Rs.1,00,000.00 to Axis Bank Credit Card XX4001 successful. Date: 2026-11-07", BillPaymentSide.PAID, BillKind.CREDIT_CARD, 10000000L, "Axis Bank", "4001", null, null),
        Pos("P40", "PHONEPE", "Your credit card bill payment of Rs 540 is successful. Ref no. 112233445566.", BillPaymentSide.PAID, BillKind.CREDIT_CARD, 54000L, null, null, null, null),
        Pos("P41", "AX-KOTAKB-S", "Bill payment of Rs.5000 was successful for HDFC Bank Credit Card ending 1234 from A/c XX7788.", BillPaymentSide.PAID, BillKind.CREDIT_CARD, 500000L, "HDFC Bank", "1234", null, "7788"),
        Pos("P42", "VM-JIOPAY-S", "Your Jio bill payment of Rs.299.00 using HDFC Bank Credit Card XX2468 was successful.", BillPaymentSide.PAID, BillKind.MOBILE, 29900L, null, null, "Jio", "2468"),
        Pos("P43", "VM-KOTAKB-S", "Hi Test, Axis Bank Credit Card bill payment of Rs. 12,345.67 was successful! T&C -Kotak811", BillPaymentSide.PAID, BillKind.CREDIT_CARD, 1234567L, "Axis Bank", null, null, null),
        Pos("P44", "AX-KOTAKB-S", "Hi Test, HDFC Bank Credit Card bill payment of Rs. 54,321.00 was successful! T&C -Kotak811", BillPaymentSide.PAID, BillKind.CREDIT_CARD, 5432100L, "HDFC Bank", null, null, null),
        Pos("P45", "PHONEPE", "Hi Test, Axis Bank Credit Card bill payment of Rs. 12,345.67 was successful! T&C", BillPaymentSide.PAID, BillKind.CREDIT_CARD, 1234567L, "Axis Bank", null, null, null),
        Pos("P46", "PAYTM", "Hi Test, ICICI Bank Credit Card bill payment of Rs. 8,765.43 was successful! T&C", BillPaymentSide.PAID, BillKind.CREDIT_CARD, 876543L, "ICICI Bank", null, null, null),
    )

    private val friends = listOf(
        Friend("F01", "AX-AXISBK-S", "Your Axis Bank credit card bill of Rs 12,345.67 is generated, due on 25-Nov-2026.", "BILL", "1234567", "CREDIT_CARD"),
        Friend("F02", "AX-KOTAKB-S", "Your bill payment of Rs 2,000.00 is due on 25-Nov-2026.", "BILL", "200000", "OTHER"),
        Friend("F03", "VM-JIOPAY-S", "Pay your bill of Rs 706.82 by 30-Nov-2026.", "BILL", "70682", "OTHER"),
        Friend("F04", "VM-HDFCBK-S", "HDFC Bank Credit Card statement: Total amount due Rs 15,000.00, minimum due Rs 750.00, due date 25-Nov-2026.", "BILL", "1500000", "CREDIT_CARD"),
        Friend("F05", "PHONEPE", "Pay your credit card bill on PhonePe and win cashback up to Rs 500!", "IGNORED_NOT_A_TRANSACTION", null, null),
        Friend("F06", "AX-KOTAKB-S", "OTP 123456 for your bill payment of Rs 2,000.00. Do not share it with anyone.", "IGNORED_OTP", null, null),
        Friend("F07", "AX-KOTAKB-S", "Your bill payment of Rs 2,000.00 failed. Please try again.", "IGNORED_DECLINED", null, null),
        Friend("F08", "AX-KOTAKB-S", "Hi Test, Axis Bank Credit Card bill payment of Rs. 12,345.67 was unsuccessful! T&C -Kotak811", "IGNORED_DECLINED", null, null),
        Friend("F09", "AX-KOTAKB-S", "Your bill payment of Rs 2,000.00 is pending. We will update you shortly.", "BILL", "200000", "OTHER"),
        Friend("F10", "AX-KOTAKB-S", "Your bill payment of Rs 3,000.00 will be processed on 10-Nov-2026.", "BILL", "300000", "OTHER"),
        Friend("F11", "AX-KOTAKB-S", "Auto-pay of Rs.199.00 for NETFLIX was successful.", "DEBIT", null, null),
        Friend("F12", "AX-KOTAKB-S", "Auto-pay of Rs.199.00 for NETFLIX is scheduled on 05-12-2026.", "BILL", "19900", "OTHER"),
        Friend("F13", "AX-KOTAKB-S", "Rs.1,499.00 will be debited from A/c XX1234 on 05-12-2026 towards your loan EMI.", "BILL", "149900", "LOAN_EMI"),
        Friend("F14", "AX-KOTAKB-S", "Rs.450.00 debited from A/c XX1234 on 05-11-26 to VPA shop@upi. UPI Ref 628374659283.", "DEBIT", null, null),
        Friend("F15", "AX-KOTAKB-S", "Rs.5,000.00 credited to A/c XX1234 on 05-11-26 by UPI. UPI Ref 628374659283.", "CREDIT", null, null),
        Friend("F16", "AX-KOTAKB-S", "Payment of Rs 500.00 to RAVI KUMAR via UPI successful. UPI Ref 628374659283.", "IGNORED_NOT_A_TRANSACTION", null, null),
        Friend("F17", "VM-JIOPAY-S", "Your Jio bill of Rs 706.82 is due on 30-11-2026. Kindly ignore, if already paid.", "BILL", "70682", "MOBILE"),
        Friend("F18", "VM-HDFCBK-S", "Statement for Nov: Total amount due Rs 3,200.00, Min due Rs 200.00, Due date 28-Nov-2026.", "BILL", "320000", "CREDIT_CARD"),
        Friend("F19", "AX-BESCOM-S", "Your bill amount of Rs 1,340.00 for consumer no 1234567890 is generated.", "BILL", "134000", "ELECTRICITY"),
        Friend("F20", "AX-AXISBK-S", "Refund of Rs 799.00 credited to your Axis Bank Credit Card XX1234.", "CREDIT", null, null),
        Friend("F21", "VM-HDFCBK-S", "Rs 450.00 reversed to your card XX1234 on 06-11-26.", "CREDIT", null, null),
        Friend("F22", "AX-AXISBK-S", "Rs 482.35 spent using Axis Bank Card XX1234 at ACME STORES on 05-11-26.", "DEBIT", null, null),
        Friend("F23", "AX-KOTAKB-S", "Payment of Rs 500.00 received from RAVI KUMAR via UPI. UPI Ref 628374659283.", "CREDIT", null, null),
        Friend("F24", "AX-KOTAK811-S", "Hi Test, Axis Bank Credit Card bill payment of Rs. 12,345.67 was successful! T&C -Kotak811", "IGNORED_NOT_ALLOWLISTED", null, null),
        Friend("F25", "CRED", "Your HDFC Bank credit card bill payment of Rs 4,000.00 was successful.", "IGNORED_NOT_ALLOWLISTED", null, null),
        Friend("F26", "BP-ICICIT-S", "Rs 482.00 paid to ACME STORES using ICICI Bank Card XX1234 on 05-Nov-26.", "DEBIT", null, null),
        Friend("F27", "VM-HDFCBK-S", "Payment of Rs 899.00 successful at ACME STORES using HDFC Bank Credit Card XX1234.", "DEBIT", null, null),
        Friend("F28", "AX-KOTAKB-S", "Credit card bill payment failed. Rs 5,000.00 not debited.", "IGNORED_DECLINED", null, null),
        Friend("F29", "AX-KOTAKB-S", "Bill payment of Rs 700.00 declined due to insufficient funds.", "IGNORED_DECLINED", null, null),
        Friend("F30", "BP-SBICRD-S", "Your SBI Card statement for Nov is ready. Total due Rs 9,000.00, pay by 25-Nov-2026.", "BILL", "900000", "CREDIT_CARD"),
        Friend("F31", "AX-AXISBK-S", "Reminder: payment of Rs 4,500.00 on your Axis Bank credit card is due on 25-Nov-2026. Kindly ignore if already paid.", "BILL", "450000", "CREDIT_CARD"),
        Friend("F32", "AX-KOTAKB-S", "Your autopay mandate of Rs 999.00 for SPOTIFY has been created.", "IGNORED_NOT_A_TRANSACTION", null, null),
        Friend("F33", "AX-KOTAKB-S", "OTP for credit card bill payment of Rs 3,000.00 is 445566.", "IGNORED_OTP", null, null),
        Friend("F34", "AX-KOTAKB-S", "Hi Test, Axis Bank Credit Card bill payment of Rs. 12,345.67 failed! T&C -Kotak811", "IGNORED_DECLINED", null, null),
        Friend("F35", "AX-KOTAKB-S", "Bill payment of Rs 2,000.00 is scheduled for 12-Nov-2026.", "BILL", "200000", "OTHER"),
        Friend("F36", "AD-AIRTEL-S", "Your Airtel postpaid bill of Rs 499.00 is due on 30-11-2026. Pay now.", "BILL", "49900", "MOBILE"),
        Friend("F37", "VM-JIOPAY-S", "Pay your electricity bill via PhonePe and get Rs 50 cashback!", "IGNORED_NOT_A_TRANSACTION", null, null),
        Friend("F38", "AX-KOTAKB-S", "Rs.10,000.00 credited to A/c XX1234 on 05-11-26. Salary credit.", "CREDIT", null, null),
        Friend("F39", "AX-BESCOM-S", "Your electricity bill of Rs 1,340.00 is generated. Bill payment due on 30-11-2026.", "BILL", "134000", "ELECTRICITY"),
        Friend("F40", "VM-HDFCBK-S", "Your credit card bill of Rs 22,000.00 is ready. Pay by 28-Nov-2026 to avoid late fee.", "BILL", "2200000", "CREDIT_CARD"),
        Friend("F41", "VM-KOTAKB-S", "Hi Test, Axis Bank Credit Card bill payment of Rs. 12,345.67 failed! T&C -Kotak811", "IGNORED_DECLINED", null, null),
        Friend("F42", "PHONEPE", "Your credit card bill payment of Rs 4,321.00 could not be processed. Please try again.", "IGNORED_DECLINED", null, null),
        Friend("F43", "PAYTM", "Hi Test, Axis Bank Credit Card bill payment of Rs. 12,345.67 was unsuccessful! T&C", "IGNORED_DECLINED", null, null),
        Friend("F44", "AX-KOTAKB-S", "Your bill payment of Rs 2,000.00 failed. Bill is due on 25-Nov-2026.", "BILL", "200000", "OTHER"),
    )

    private fun classOf(o: CaptureOutcome): String = when (o) {
        is CaptureOutcome.Transaction -> o.alert.direction.name
        is CaptureOutcome.Bill -> "BILL"
        is CaptureOutcome.Ignored -> "IGNORED_" + o.reason.name
    }

    @Test
    fun everyConfirmationIsReadWithItsSideKindAmountCardAndBillerAndIsATransactionNotABill() {
        assertTrue(positives.size >= 30, "at least 30 variants: ${positives.size}")
        val wrong = mutableListOf<String>()
        for (p in positives) {
            val n = BillPayments.read(p.body)
            if (n == null) { wrong += "${p.id}: not read"; continue }
            val got = listOf(n.side, n.kind, n.amountMinor, n.cardBank, n.cardLast4, n.biller, n.payerLast4)
            val want = listOf(p.side, p.kind, p.amount, p.cardBank, p.cardLast4, p.biller, p.payerLast4)
            if (got != want) wrong += "${p.id}: got $got want $want"
            val o = CaptureClassifier.classify(p.sender, p.body, emptySet(), today)
            if (o !is CaptureOutcome.Transaction) wrong += "${p.id}: classified as ${classOf(o)}"
            else if (o.alert.direction != (if (p.side == BillPaymentSide.PAID) Direction.DEBIT else Direction.CREDIT) || o.alert.amountMinor != p.amount) wrong += "${p.id}: alert ${o.alert}"
        }
        assertEquals(emptyList(), wrong)
    }

    @Test
    fun theOwnersTextIsMoneyOutFromTheSendersBankToAnAxisCardNotANewBill() {
        val body = "Hi Asha Test, Axis Bank Credit Card bill payment of Rs. 12,345.67 was successful! T&C -Kotak811"
        val n = assertNotNull(BillPayments.read(body))
        assertEquals(BillPaymentNotice(BillPaymentSide.PAID, BillKind.CREDIT_CARD, 1234567L, "INR", cardBank = "Axis Bank"), n)
        val o = assertNotNull(CaptureClassifier.classify("AX-KOTAKB-S", body, emptySet(), today) as? CaptureOutcome.Transaction)
        assertEquals("Kotak Mahindra Bank", o.issuer)
        assertEquals(Direction.DEBIT, o.alert.direction)
        assertEquals(1234567L, o.alert.amountMinor)
        assertEquals(Confidence.GENERIC, o.alert.confidence)
        // the card's bank is exposed, so a paste with no header (which names Axis and the signature Kotak) can pick the OTHER bank as the paying one
        assertEquals("Axis Bank", n.cardBank)
        assertEquals("read as a bill payment confirmation (money out)", MessageReport.verdict("AX-KOTAKB-S", body).substringBefore(" by ").take(52))
    }

    @Test
    fun theBillParserAloneNoLongerReturnsABillForAConfirmation() {
        for (p in positives.filter { it.kind == BillKind.CREDIT_CARD || it.body.contains("bill payment", ignoreCase = true) }) {
            val bill = BillParser.parse(p.body, null, today)
            assertNull(bill, "${p.id} must not be a bill: $bill")
        }
        assertNull(BillParser.parse("Hi Test, Axis Bank Credit Card bill payment of Rs. 12,345.67 was successful! T&C -Kotak811", null, today))
        assertNull(BillParser.parse("Your bill payment of Rs 2,500.00 has been received.", null, today))
        assertNull(BillParser.parse("Bill payment of Rs.2,500 is successful", null, today))
    }

    @Test
    fun theFalseFriendsKeepTheirClassAndAreNeverReadAsAPayment() {
        assertTrue(friends.size >= 20, "at least 20 false friends: ${friends.size}")
        val wrong = mutableListOf<String>()
        for (f in friends) {
            val o = CaptureClassifier.classify(f.sender, f.body, emptySet(), today)
            if (classOf(o) != f.expected) wrong += "${f.id}: ${classOf(o)} want ${f.expected}"
            if (o is CaptureOutcome.Bill && (o.notice.amountMinor.toString() != f.amount || o.notice.kind.name != f.kind)) wrong += "${f.id}: bill ${o.notice.amountMinor} ${o.notice.kind}"
            if (f.expected != "IGNORED_NOT_ALLOWLISTED" && BillPayments.read(f.body) != null) wrong += "${f.id}: read as a payment"
        }
        assertEquals(emptyList(), wrong)
    }

    @Test
    fun everyOutcomeClassIsExercisedByTheFalseFriendsAndThePayments() {
        val seen = (friends.map { it.expected } + positives.map { "PAYMENT" }).toSet()
        assertTrue(seen.containsAll(listOf("BILL", "DEBIT", "CREDIT", "PAYMENT", "IGNORED_OTP", "IGNORED_DECLINED", "IGNORED_NOT_A_TRANSACTION", "IGNORED_NOT_ALLOWLISTED")), "classes seen: $seen")
        assertEquals(CaptureOutcome.Ignored(IgnoreReason.PERSONAL_SENDER, null), CaptureClassifier.classify("+919876543210", positives[0].body, emptySet(), today))
    }

    @Test
    fun aPaymentWordedByAnAccountKeepsTheGenericAlertFieldsAndOneTheGenericParserMissesIsBuiltFromTheNotice() {
        // generic parser reads "debited": the alert is its own (account digits, direction)
        val debit = CaptureClassifier.classify("VM-HDFCBK-S", "Dear Customer, Rs.2,500.00 debited from A/c XX1234 towards credit card bill payment, successful.", emptySet(), today) as CaptureOutcome.Transaction
        assertEquals("1234", debit.alert.accountLast4)
        assertEquals(Direction.DEBIT, debit.alert.direction)
        // "has been received" is a credit word to the generic parser too; "credit card payment ... processed" has no direction word at all: built from the notice
        val built = CaptureClassifier.classify("AX-KOTAKB-S", "Credit card payment of Rs 6,000.00 has been processed.", emptySet(), today) as CaptureOutcome.Transaction
        assertEquals(Direction.DEBIT, built.alert.direction)
        assertEquals(600000L, built.alert.amountMinor)
        assertEquals("INR", built.alert.currency)
        // generic parser says CREDIT ("received") but the notice is PAID? never: sides follow the words, so a "received" confirmation is a CREDIT on the card
        val received = CaptureClassifier.classify("VM-HDFCBK-S", "Payment of Rs.8,200.00 received towards your HDFC Bank Credit Card ending 1234 on 05-11-2026. Thank you!", emptySet(), today) as CaptureOutcome.Transaction
        assertEquals(Direction.CREDIT, received.alert.direction)
        assertEquals("1234", received.alert.accountLast4)
        assertEquals(LocalDate(2026, 11, 5), received.alert.date)
        val upiRef = BillPayments.read("Your credit card bill payment of Rs.7,350 to HDFC Bank Credit Card XX5678 was successful. UPI Ref 628374659283.")
        assertEquals("628374659283", upiRef?.reference)
    }

    @Test
    fun anEmailAboutABillPaymentFollowsTheSameRule() {
        val o = CaptureClassifier.classifyEmail(listOf("Kotak Mahindra Bank"), "Hi Test, Axis Bank Credit Card bill payment of Rs. 12,345.67 was successful! T&C -Kotak811", emptySet(), today)
        assertTrue(o is CaptureOutcome.Transaction && o.alert.direction == Direction.DEBIT, "$o")
    }

    @Test
    fun cardBankNamesAreTheNamesBankRulesUsesAndThePayersBankIsNeverTheCardBank() {
        val roots = BankRules.ALL.map { it.issuer }.toSet()
        for ((_, name) in BillPayments.issuers) assertTrue(name in roots, "$name is not a BankRules issuer")
        // paid FROM an HDFC card or a Kotak account: the card paid is the ICICI one
        assertEquals("ICICI Bank", BillPayments.read("Bill payment of Rs 900.00 from HDFC Bank A/c XX1234 to ICICI Bank Credit Card ending 4321 was successful.")?.cardBank)
        assertEquals("Axis Bank", BillPayments.read("Bill payment of Rs 900.00 to Axis Bank Credit Card ending 4321 was successful! Regards Kotak Mahindra Bank")?.cardBank)
        assertNull(BillPayments.read("Credit card bill payment of Rs 900.00 was successful. T&C -Kotak811")?.cardBank)
        assertNull(BillPayments.read("CRED credit card bill payment of Rs 900.00 was successful.")?.cardBank)
    }
}

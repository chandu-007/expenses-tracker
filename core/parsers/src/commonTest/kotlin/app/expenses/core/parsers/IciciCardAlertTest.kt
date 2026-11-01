package app.expenses.core.parsers

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ICICI Bank's credit card alert comes from the header ICICIT ("BP-ICICIT-S"), not ICICIB, which sends the account alerts. In 0.9.0 ICICIT was not on the list, so a tester's
 * card spend was dropped at the sender check (NOT_ALLOWLISTED) before its words were read. The parser itself reads this wording; the texts here are invented (card digits, amounts,
 * shops and phone numbers), in the shape the bank sends: "INR x spent using ICICI Bank Card XXnnnn on dd-Mon-yy on SHOP. Avl Limit: INR y. If not you, call ...".
 */
class IciciCardAlertTest {
    private val today = LocalDate(2026, 11, 1)
    private val spend = "INR 482.35 spent using ICICI Bank Card XX4417 on 01-Nov-26 on GREENCART. Avl Limit: INR 1,16,387.86. If not you, call 1800 2662/SMS BLOCK 4417 to 9000000000."

    private fun classify(sender: String, body: String, extra: Set<String> = emptySet()) = CaptureClassifier.classify(sender, body, extra, today)
    private fun tx(sender: String, body: String) = assertIs<CaptureOutcome.Transaction>(classify(sender, body), "$sender: $body")

    @Test
    fun theCardSpendFromTheTestersHeaderIsATransactionWithEveryFieldRead() {
        val t = tx("BP-ICICIT-S", spend)
        assertEquals("ICICI Bank", t.issuer); assertEquals("ICICIT", t.headerRoot)
        val a = t.alert
        assertEquals(Direction.DEBIT, a.direction)
        assertEquals(48_235L, a.amountMinor, "the spend, not the 1,16,387.86 limit")
        assertEquals("INR", a.currency); assertNull(a.originalCurrency)
        assertEquals("4417", a.accountLast4, "the card's last four, not the number after SMS BLOCK")
        assertEquals(LocalDate(2026, 11, 1), a.date)
        assertEquals("GREENCART", PayeeGuess.of(spend))
    }

    @Test
    fun everyFormOfTheHeaderGetsThrough() {
        for (sender in listOf("BP-ICICIT-S", "AX-ICICIT-S", "VM-ICICIT-T", "JD-ICICIT-P", "TX-ICICIT-G", "bp-icicit-s", "VK-ICICIT", "ICICIT", "  BP-ICICIT-S ")) {
            assertEquals("ICICIT", SenderRules.headerRoot(sender), sender)
            assertEquals("ICICI Bank", tx(sender, spend).issuer, sender)
        }
        // the account alerts' header still works, and both are one bank
        assertEquals("ICICI Bank", tx("JD-ICICIB-S", spend).issuer)
        assertEquals(SenderRules.BUILT_IN["ICICIB"], SenderRules.BUILT_IN["ICICIT"])
    }

    @Test
    fun theWordingVariantsAreAllReadTheSameWay() {
        val variants = listOf(
            "INR 482.35 spent using ICICI Bank Card XX4417 on 01-Nov-26 on GREENCART. Avl Limit: INR 1,16,387.86. If not you, call 1800 2662/SMS BLOCK 4417 to 9000000000.",
            "Rs 482.35 spent using ICICI Bank Card XX4417 on 01-Nov-26 on GREENCART. Avl Limit: Rs 1,16,387.86. If not you, call 1800 2662/SMS BLOCK 4417 to 9000000000.",
            "INR 482.35 spent on ICICI Bank Card XX4417 on 01-Nov-26 at GREENCART. Avl Limit: INR 1,16,387.86. To dispute, call 18002662/SMS BLOCK 4417 to 9000000000.",
            "Rs 482.35 spent on ICICI Bank Card XX4417 on 01-Nov-26 at GREENCART. Avl Lmt: Rs 1,16,387.86. To dispute, call 18002662/SMS BLOCK 4417 to 9000000000.",
            "INR 482.35 spent using ICICI Bank Credit Card XX4417 on 01-Nov-26 on GREENCART. Available Limit: INR 1,16,387.86. If not you, call 1800 2662.",
            "INR 482.35 spent using ICICI Bank Card XX4417 on 01-Nov-26 on GREENCART. Available Credit Limit: INR 1,16,387.86.",
            "INR 482.35 spent using ICICI Bank Card XX4417 on 01-Nov-2026 on GREENCART. Avl Limit INR 1,16,387.86",
        )
        for (v in variants) {
            val a = tx("BP-ICICIT-S", v).alert
            assertEquals(Direction.DEBIT, a.direction, v); assertEquals(48_235L, a.amountMinor, v); assertEquals("4417", a.accountLast4, v)
            assertEquals(LocalDate(2026, 11, 1), a.date, v)
            assertEquals("GREENCART", PayeeGuess.of(v), v)
        }
    }

    @Test
    fun theLimitIsNeverTakenForTheAmountWhateverItsWordingOrPlace() {
        // the limit first, the spend after
        assertEquals(48_235L, tx("BP-ICICIT-S", "Avl Limit: INR 1,16,387.86. INR 482.35 spent using ICICI Bank Card XX4417 on 01-Nov-26 on GREENCART.").alert.amountMinor)
        for (word in listOf("Avl Limit", "Avl Lmt", "Avail Limit", "Available Limit", "Available Credit Limit", "Avl Credit Limit", "Total Limit")) {
            assertEquals(48_235L, tx("BP-ICICIT-S", "INR 482.35 spent using ICICI Bank Card XX4417 on 01-Nov-26 on GREENCART. $word: INR 1,16,387.86.").alert.amountMinor, word)
        }
    }

    @Test
    fun theShopAfterTheDateIsNamedWhateverItsShape() {
        fun shop(name: String) = PayeeGuess.of("INR 99.00 spent using ICICI Bank Card XX4417 on 01-Nov-26 on $name. Avl Limit: INR 1,000.00.")
        assertEquals("GREENCART", shop("GREENCART"))
        assertEquals("ONYX BAR", shop("ONYX BAR"))
        assertEquals("AMAZON.IN", shop("AMAZON.IN"))
        assertEquals("BIG BASKET STORE", shop("BIG BASKET STORE"))
        assertNull(shop("ATM 12345"), "a name with digits is a terminal, not a shop (the same rule as every other wording)")
        // numeric dates read the same, and a text with no shop names nobody
        assertEquals("GREENCART", PayeeGuess.of("Rs 99.00 spent using ICICI Bank Card XX4417 on 01-11-26 on GREENCART. Avl Limit: Rs 1,000.00."))
        assertNull(PayeeGuess.of("Rs 99.00 debited from your account on 01-11-26 on account of charges. Avl Bal: Rs 1,000.00."), "\"on account of\" is wording, not a shop")
    }

    @Test
    fun aRefundOrAPaymentOnTheCardKeepsItsDirection() {
        val refund = tx("BP-ICICIT-S", "GREENCART refund of Rs 2.00 credited to ICICI Bank Credit Card XX4417 on 06-NOV-26. Revised total due Rs 9,999.00, minimum due Rs 499.00").alert
        assertEquals(Direction.CREDIT, refund.direction); assertEquals(200L, refund.amountMinor); assertEquals("4417", refund.accountLast4)
        val paid = classify("BP-ICICIT-S", "Payment of Rs 1,234.00 has been received on your ICICI Bank Credit Card XX4417 through Bharat Bill Payment System on 21-NOV-26.")
        assertEquals(Direction.CREDIT, assertIs<CaptureOutcome.Transaction>(paid).alert.direction)
    }

    @Test
    fun aTextFromTheHeaderThatIsNotATransactionStaysUncaptured() {
        // an OTP, even one that quotes the amount and the card
        assertEquals(IgnoreReason.OTP, assertIs<CaptureOutcome.Ignored>(classify("BP-ICICIT-S", "482913 is the OTP for INR 482.35 spent using ICICI Bank Card XX4417 at GREENCART. Do not share it. If not you, call 1800 2662.")).reason)
        // "If not you" with no amount, an offer with no spend, a card promotion, a statement notice
        for (body in listOf(
            "Dear Customer, if not you, call 1800 2662 to block your ICICI Bank Card XX4417.",
            "Your ICICI Bank Credit Card XX4417 has been activated for online use. If not you, call 1800 2662.",
            "Get 5% cashback on your ICICI Bank Card at GREENCART. Visit icici.co/ICICIT/x to know more. T&C apply.",
            "ICICI Bank Credit Card XX4417: your statement is generated. Total Amt Due Rs 1,234.00, Min Amt Due Rs 123.00, Due Date 20-11-2026.",
        )) {
            assertTrue(classify("BP-ICICIT-S", body) !is CaptureOutcome.Transaction, body)
        }
        // a declined card payment is not a spend
        assertEquals(IgnoreReason.DECLINED, assertIs<CaptureOutcome.Ignored>(classify("BP-ICICIT-S", "Transaction of INR 482.35 on ICICI Bank Card XX4417 at GREENCART declined due to insufficient funds.")).reason)
    }

    @Test
    fun aSimilarTextFromAnUnknownHeaderOrAPersonIsStillNeverRead() {
        assertEquals(IgnoreReason.NOT_ALLOWLISTED, assertIs<CaptureOutcome.Ignored>(classify("BP-ICICIX-S", spend)).reason, "a header that only looks like ICICI's is not on the list")
        assertEquals(IgnoreReason.NOT_ALLOWLISTED, assertIs<CaptureOutcome.Ignored>(classify("BP-ICICITX-S", spend)).reason)
        assertEquals(IgnoreReason.PERSONAL_SENDER, assertIs<CaptureOutcome.Ignored>(classify("+919000000000", spend)).reason)
        // the person can still add a header the list misses
        assertEquals("ICICIX", assertIs<CaptureOutcome.Transaction>(classify("BP-ICICIX-S", spend, setOf("ICICIX"))).issuer)
    }

    @Test
    fun theSameWordingFromOtherBanksHeadersIsStillReadAsBefore() {
        for ((sender, root) in listOf("AX-HDFCBK-S" to "HDFCBK", "VM-SBICRD-S" to "SBICRD", "AX-AXISBK-S" to "AXISBK", "JD-ICICIB-S" to "ICICIB")) {
            val t = tx(sender, spend); assertEquals(root, t.headerRoot)
            assertEquals(48_235L, t.alert.amountMinor, sender); assertEquals("4417", t.alert.accountLast4, sender)
        }
    }
}

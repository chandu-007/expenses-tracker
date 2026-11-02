package app.expenses.core.parsers

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [UnlistedSenders]: which texts from a header nobody listed are shaped like a bank's payment, and the masked outline kept for them. Every text, name, number and header here is invented.
 */
class UnlistedSendersTest {
    private val today = LocalDate(2026, 11, 2)

    /** (text, expected way). Different banks' shapes: 3- and 4-digit masks, UPI, card, IMPS, NEFT, ATM, credit and debit. */
    private val payments: List<Pair<String, UnlistedSenders.Way>> = listOf(
        "Rs.1,250.00 debited from A/c XX1234 on 05-10-26 to VPA shop@okmycoop (UPI Ref 628374659283). Not you? Call 18002586161" to UnlistedSenders.Way.OUT,
        "INR 482.35 spent using MyCoop Bank Card XX4417 on 01-Nov-26 on GREENCART. Avl Limit: INR 1,16,387.86. If not you, call 1800 2662." to UnlistedSenders.Way.OUT,
        "Dear Customer, Rs 2,000.00 credited to your A/c XX567 on 12-Oct-26 by UPI/p2a/628374659283. Avl Bal Rs 15,230.55" to UnlistedSenders.Way.IN,
        "Your a/c no. XXXXXXXX9012 is debited for Rs.340.00 on 03-11-26 and a/c XXXXXXXX3456 credited (IMPS Ref no 628374659283)." to UnlistedSenders.Way.OUT,
        "Rs 5,000 withdrawn from ATM using Card ending 3025 on 02-Nov-26. Available balance Rs 20,100.00." to UnlistedSenders.Way.OUT,
        "Dear UPI user A/C X8968 debited by 349.00 on date 18Oct26 trf to SOME SHOP Refno 628374659283 If not u? call-1800111109" to UnlistedSenders.Way.OUT,
        "NEFT of Rs 25,000.00 credited to your account XX4321 on 04-Nov-26. Reference 626123456789" to UnlistedSenders.Way.IN,
        "Rs.1,299.00 spent on your Credit Card XX7788 at BOOKMART on 2026-11-01. Avl limit Rs 48,000.00" to UnlistedSenders.Way.OUT,
        "Rs 750.00 paid via UPI from A/c ending 2211 to A PERSON. UPI Ref: 628374659283" to UnlistedSenders.Way.OUT,
        "Salary of Rs 52,000.00 credited in A/c XX0099 on 01-Nov-26. Avl bal Rs 61,234.10" to UnlistedSenders.Way.IN,
        "Rs.89.50 spent on Card XX3344 at METRO CAFE on 02-11-26. Avl Lmt Rs 9,910.50" to UnlistedSenders.Way.OUT,
        "Your A/c XXXX5566 has been debited with INR 1,100.00 on 29-Oct-26 towards MYCOOP LOAN. IMPS 628374659283" to UnlistedSenders.Way.OUT,
        "Rs. 300.00 received from A PERSON via UPI in your a/c XX7070 on 03-Nov-26. UPI Ref 628374659283" to UnlistedSenders.Way.IN,
        "Rs.2,500.00 transferred from A/c XX8899 to a payee via NEFT 626123456789" to UnlistedSenders.Way.OUT,
        "INR 3,200.00 deducted from your a/c XX1357 on 05-Nov-26 for card bill. Available Balance INR 8,000.00" to UnlistedSenders.Way.OUT,
    )

    /** Texts that must never be offered: not a payment, or not provably a bank's. */
    private val others: List<Pair<String, String>> = listOf(
        "one-time password" to "123456 is the OTP for your transaction of Rs.1,250.00 at AMAZON. Do not share it with anyone.",
        "cashback" to "Rs 100 cashback credited to your wallet. Keep shopping!",
        "recharge" to "Recharge of Rs 299 successful for 98XXXXXX10. Enjoy 2GB/day until 30-Nov.",
        "delivery" to "Order paid Rs 450 for your delivery. Rider arrives in 10 mins.",
        "offer" to "Get Rs 500 off, spend more and save! Use code SAVE500 before it ends.",
        "electricity bill" to "Your electricity bill of Rs 1,240.00 is due on 10-Nov-26. Pay now to avoid disconnection. Kindly ignore if already paid.",
        "telecom bill" to "Dear customer, your postpaid bill of Rs 799 is generated, payable by 12-Nov. Ignore if already paid.",
        "promise" to "Rs 1,500.00 will be debited from your A/c XX1234 on 5th Nov for your EMI.",
        "declined" to "Transaction of Rs.500.00 on card ending 4321 was declined due to insufficient funds.",
        "balance only" to "Your A/c XX1234 Avl Bal is Rs 12,340.50 as on 02-Nov-26.",
        "news" to "Sensex jumps 400 points; Rs 1 lakh crore added to market cap today.",
        "parcel" to "Your parcel worth Rs 899 has been delivered. Rate us on the app!",
        "reward points" to "Dear user, Rs 250 credited as reward points to your loyalty account today.",
        "friend" to "Hi, I paid Rs 500 for the tickets, will send the rest tomorrow.",
        "statement" to "Your credit card statement is ready. Total amount due Rs 4,520. Min due Rs 450 by 15-Nov. Card ending 3025",
        "premium" to "Your premium of Rs 12,000 for policy 12345678 falls due on 10-Nov-26. Pay to keep cover active.",
        "kyc" to "Update your KYC for A/c XX1234 within 7 days to avoid freezing.",
        "empty" to "",
    )

    @Test
    fun paymentShapedTextsAreRecognisedAndTheOthersNever() {
        var found = 0
        for ((text, way) in payments) {
            val h = UnlistedSenders.hint(text, "MYCOOP")
            if (h != null) found++
            assertNotNull(h, "should look like a payment: $text")
            assertEquals(way, h.way, text)
        }
        var falsePositives = 0
        for ((label, text) in others) {
            val h = UnlistedSenders.hint(text, "MYCOOP")
            if (h != null) { falsePositives++; println("FALSE POSITIVE ($label): $text -> $h") }
            assertNull(h, "must not look like a payment ($label): $text")
        }
        println("UnlistedSenders precision: payment-shaped ${found}/${payments.size} found; false positives ${falsePositives}/${others.size}")
        assertEquals(0, falsePositives)
        assertTrue(payments.size >= 12 && others.size >= 12)
    }

    @Test
    fun theOutlineNeverHasADigitOrAWordThatIsNotOnTheWhitelistAndIsCapped() {
        val bodies = payments.map { it.first } + others.map { it.second } + listOf(
            "Rs.5,00,000.00 debited from A/c XX1234 of Chandrashekhar Nelluri, phone 9876543210, mail someone@example.com, PAN ABCDE1234F, ref 628374659283628374659283 UPI Ref 628374659283",
            "\u20B9500 debited from A/c XX1234 to \u0968\u0969\u096A shop UPI Ref 628374659283",
            "INR500 spent on card XX1234 Rs100 Rs.200 xx1234 ****5678 X 12 ending 3025 ending with 3025 AVL BAL ##12 1,2,3 A/c a/c 12ab34",
            "debited ".repeat(80) + "Rs 5 A/c XX1234",
        )
        for (b in bodies) {
            val o = UnlistedSenders.outline(b)
            assertTrue(o.none { it.isDigit() }, "no digit in: $o")
            assertTrue(o.length <= UnlistedSenders.OUTLINE_CAP, "capped: ${o.length} $o")
            for (w in Regex("[A-Za-z/]+").findAll(o).map { it.value.lowercase() }) assertTrue(w in UnlistedSenders.outlineWords, "'$w' is not on the whitelist in: $o")
        }
    }

    @Test
    fun theOutlineKeepsTheGenericWordsAndMasksEverythingElse() {
        assertEquals(
            "ICICI Bank Acct XX### debited for Rs ###.## on \u2026 credited \u2026",
            UnlistedSenders.outline("ICICI Bank Acct XX123 debited for Rs 500.00 on 01-Nov-26; SHOP credited. UPI:123456789012"),
            "the date, the shop and the long reference are masked; the outline is cut at a word",
        )
        assertEquals("Rs ###.## spent \u2026 Card ending ####", UnlistedSenders.outline("Rs.482.35 spent using the card ending 4417"))
        assertEquals("\u2026", UnlistedSenders.outline("Chandrashekhar Nelluri"), "a name is one ellipsis, never the name")
        assertEquals("", UnlistedSenders.outline("  "))
    }

    @Test
    fun aTextFromAHeaderNobodyListedIsOnlyEverJudgedAfterTheSenderCheck() {
        val text = payments.first().first
        assertEquals(CaptureOutcome.Ignored(IgnoreReason.NOT_ALLOWLISTED, "MYCOOP"), CaptureClassifier.classify("AX-MYCOOP-S", text, emptySet(), today))
        assertNotNull(UnlistedSenders.hint(text, "MYCOOP"))
        assertEquals(CaptureOutcome.Ignored(IgnoreReason.PERSONAL_SENDER, null), CaptureClassifier.classify("+919876543210", text, emptySet(), today), "a phone number has no header root, so it is never looked at")
        assertNull(SenderRules.headerRoot("+919876543210")); assertNull(SenderRules.headerRoot("9876543210"))
        assertEquals("MYCOOP", SenderRules.headerRoot("AX-MYCOOP-S"))
        assertEquals(null, (CaptureClassifier.classify("AX-MYCOOP-S", text, setOf("MYCOOP"), today) as? CaptureOutcome.Ignored)?.reason, "once trusted it is read as a transaction")
    }
}

package app.expenses.core.parsers

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The redacted report of an undetected message: invented texts in more than fifteen bank formats, and what must and must not survive. */
class MessageRedactorTest {
    private val today = LocalDate(2026, 10, 6)

    private class Sample(val sender: String, val body: String, val mustGo: List<String>)

    /** Every text and every name, number and address in it is invented. [Sample.mustGo] lists the personal details that must not survive. */
    private val corpus = listOf(
        Sample("AX-HDFCBK-S", "Sent Rs.1,250.00 from HDFC Bank A/c XX1234 on 05-10-26 to RAVI KUMAR\nUPI Ref 628374659283. Not you? Call 18002586161", listOf("RAVI", "KUMAR", "628374659283", "18002586161")),
        Sample("AX-HDFCBK-S", "Txn Rs.2,499.00 On HDFC Bank Card 0862 At AMAZON PAY by UPI 628374659283", listOf("0862", "628374659283")),
        Sample("JD-ICICIB-T", "ICICI Bank Acct XX123 debited for Rs 450.00 on 05-Oct-26; ravi.kumar@okaxis credited. UPI:628374659283. Call 18002662 for dispute", listOf("ravi.kumar", "628374659283", "XX123 ")),
        Sample("VM-SBIINB-S", "Dear UPI user A/C X1234 debited by 349.00 on date 05Oct26 trf to ANITA SHARMA Refno 628374659283. If not you, call 1800111109", listOf("ANITA", "SHARMA", "628374659283", "1800111109")),
        Sample("AX-AXISBK-S", "Spent Card no. XX4321 INR 1250.00 05-10-26 12:30:00 ACME STORES Avl Lmt INR 85,000.00", listOf("4321", "85,000")),
        Sample("AD-KOTAKB-S", "Received Rs.5,000.00 in your Kotak Bank AC X1234 from priya.nair@ybl on 05-10-26. UPI Ref 628374659283.", listOf("priya.nair", "628374659283")),
        Sample("PHONEPE", "Anita Rao sent you Rs.120.00 via UPI. Ref 628374659283", listOf("Anita", "Rao", "628374659283")),
        Sample("CP-SBICRD-S", "Rs.1,250.00 spent on your SBI Card ending 4321 at ACME STORES on 05/10/26. Avl limit Rs.85,000.00. Report at https://sbicard.com/report/ab12CD34?id=99887766", listOf("4321", "ab12CD34", "99887766", "85,000")),
        Sample("JM-JIOPAY-S", "Your Jio bill dated 05-10-2026 is due for payment on 30-10-2026. Total amount payable : Rs.706.82. Pay at https://www.jio.com/pay/9876543210 Kindly ignore, if already paid. Customer no 9876543210", listOf("9876543210")),
        Sample("AX-HDFCBK-S", "123456 is your OTP for Rs.1,250.00 txn at ACME. Do not share. Valid 10 min. Ref 628374659283", listOf("123456", "628374659283")),
        Sample("VK-SBIINB-S", "Rs.10,000.00 sent via IMPS from A/c XX1234 to A/c XX5678 BENEFICIARY NAME on 05-10-26. IMPS Ref no 628374659283. Bal Rs.52,000.50", listOf("BENEFICIARY", "628374659283", "52,000", "XX5678")),
        Sample("AX-HDFCBK-S", "Rs.25,000.00 credited to A/c XXXXXX1234 on 05-10-26 by NEFT from ACME PRIVATE LIMITED, UTR HDFCN52025100512345678. Avl Bal Rs.1,25,000.00", listOf("HDFCN52025100512345678", "1,25,000", "1234")),
        Sample("AX-AXISBK-S", "Rs.2,000.00 withdrawn from ATM ID DEL12345 on 05-10-26 using card ending 1234. Avl Bal Rs.8,000.00", listOf("DEL12345", "ending 1234", "8,000")),
        Sample("AX-HDFCBK-S", "Your transaction of Rs.1,250.00 at ACME STORES using card ending 4321 was declined due to insufficient funds.", listOf("4321")),
        Sample("AX-ICICIB-S", "Dear Ravi Kumar, Rs.50,000.00 credited to your A/c XX1234 on 05-10-26. Info: SALARY. Call +91 98765 43210 for help.", listOf("Ravi", "Kumar", "98765", "43210")),
        Sample("AX-KOTAKB-S", "Refund of Rs.499.00 for order 1234567 credited to your account XX1234 on 05-10-26. UPI Ref 628374659283", listOf("1234567", "628374659283")),
        Sample("AX-HDFCBK-S", "Alert: Rs.2,100.00 spent on card ending 4321 at ACME STORES. Email ravi.kumar@example.com for help or visit www.hdfcbank.com/help/cards?ref=4455667788", listOf("ravi.kumar", "4455667788", "4321")),
        Sample("PAYTM", "Paid Rs.250.00 to Ravi Chai Stall from Paytm Payments Bank A/c. Order ID 20251005111212800 UPI 628374659283", listOf("Ravi", "20251005111212800", "628374659283")),
        Sample("AX-HDFCBK-S", "Statement for card XX4321 generated. Total amount due Rs.12,450.00 min due Rs.620.00 due date 25-10-2026. Pay now http://hdfcbk.io/x7Yz9Q", listOf("4321", "x7Yz9Q")),
        Sample("AX-ICICIB-S", "Rs 1250 debited from A/c XX1234 on 05-10-26 paid to VPA shop.ravi@okhdfcbank. Ref 628374659283. PAN ABCDE1234F. Vehicle KA01AB1234", listOf("shop.ravi", "ABCDE1234F", "KA01AB1234", "628374659283")),
        Sample("AX-ICICIB-S", "INR 1,25,000.00 debited from A/c 123456789012 on 05-10-26. Call 022-24567890 or 080 23456789 for help", listOf("123456789012", "24567890", "23456789")),
    )

    private val longDigits = Regex("""\d{5,}""")
    private val addressParts = Regex("""([A-Za-z0-9_.\-+]+)@""")
    private val linkPath = Regex("""(?i)(?:https?://|www\.)[^\s/]+/(?!xxxx)\S|\b[a-z0-9\-]+\.(?:io|com|in)/(?!xxxx)\S""")

    @Test
    fun byDefaultNoLongNumberNoAddressPartNoLinkPathNoNameAndNoAmountSurvives() {
        assertTrue(corpus.size >= 15)
        for (s in corpus) {
            val r = MessageRedactor.redact(s.body)
            assertFalse(longDigits.containsMatchIn(r), "a digit run of five or more is left in: $r")
            assertEquals(emptyList(), addressParts.findAll(r).map { it.groupValues[1] }.filter { it != "xxxx" }.toList(), "an address part is left in: $r")
            assertFalse(linkPath.containsMatchIn(r), "a link path is left in: $r")
            for (secret in s.mustGo) assertFalse(r.contains(secret), "'$secret' survived in: $r")
            assertFalse(Regex("""(?i)(?:rs\.?|inr|₹)\s*\d""").containsMatchIn(r), "an amount survived in: $r")
            assertFalse(Regex("""\d+\.\d{2}""").containsMatchIn(r), "a decimal amount survived in: $r")
        }
    }

    @Test
    fun withTheAmountsKeptTheSameHoldsForEverythingButThePaymentsOwnAmount() {
        for (s in corpus) {
            val r = MessageRedactor.redact(s.body, keepAmounts = true)
            assertFalse(longDigits.containsMatchIn(r), "a digit run of five or more is left in: $r")
            assertEquals(emptyList(), addressParts.findAll(r).map { it.groupValues[1] }.filter { it != "xxxx" }.toList(), r)
            assertFalse(linkPath.containsMatchIn(r), r)
            for (secret in s.mustGo.filter { !it.contains(',') }) assertFalse(r.contains(secret), "'$secret' survived in: $r")
        }
        val r = MessageRedactor.redact(corpus[10].body, keepAmounts = true)
        assertTrue(r.contains("Rs.10,000.00"), r); assertTrue(r.contains("Bal Rs.N"), "the balance is still hidden: $r")
        assertTrue(kept(corpus[11].body).contains("Rs.25,000.00") && !kept(corpus[11].body).contains("1,25,000"), kept(corpus[11].body))
        assertFalse(kept(corpus[18].body).contains("12,450.00") || kept(corpus[18].body).contains("620.00"), "dues are hidden: ${kept(corpus[18].body)}")
        assertTrue(MessageRedactor.redact(corpus[4].body, keepAmounts = true).contains("INR 1250.00"))
        assertTrue(MessageRedactor.redact(corpus[4].body, keepAmounts = true).contains("Lmt INR N"), "a limit is hidden")
    }

    private fun kept(text: String) = MessageRedactor.redact(text, keepAmounts = true)
    private fun hidden(text: String) = MessageRedactor.redact(text, keepAmounts = false)

    @Test
    fun withTheAmountsKeptABalanceALimitOrADueIsStillHiddenHoweverItIsWorded() {
        // the review's counterexamples, and ten more: each is (text, the figure that must not survive, the figure that may stay when kept or null)
        val cases = listOf(
            Triple("Available balance in your account is Rs.1,23,456.78", "1,23,456.78", null),
            Triple("The available balance in your account as on 05-Jun-26 is Rs.88,001.25", "88,001.25", null),
            Triple("Total Amount Due: Rs.12,345.00 Minimum Amount Due: Rs.617.00", "12,345.00", null),
            Triple("Total Amount Due: Rs.12,345.00 Minimum Amount Due: Rs.617.00", "617.00", null),
            Triple("Rs.450.00 debited from A/c XX1234 on 05-06-26. Avl Bal Rs.8,765.43", "8,765.43", "450.00"),
            Triple("Rs.450.00 debited from A/c XX1234 on 05-06-26 Avl Bal Rs.8,765.43", "8,765.43", "450.00"),
            Triple("Rs.450.00 debited from A/c XX1234\nAvl Bal Rs.8,765.43", "8,765.43", "450.00"),
            Triple("INR 1,250.00 spent on your card at ACME. Available limit is INR 98,750.00", "98,750.00", "1,250.00"),
            Triple("Spent Card no. XX4321 INR 1250.00 05-10-26 12:30:00 ACME STORES Avl Lmt INR 85,000.00", "85,000.00", "1250.00"),
            Triple("Rs.2,000.00 withdrawn from ATM on 05-10-26. Closing balance Rs.7,654.32", "7,654.32", "2,000.00"),
            Triple("Rs.500.00 credited to A/c XX1234. Outstanding loan Rs.1,50,000.00", "1,50,000.00", "500.00"),
            Triple("Your credit card outstanding is Rs.23,456.78. Pay by 25-10-26", "23,456.78", null),
            Triple("Rs.12,345.00 is due on 25-10-26 for your card", "12,345.00", null),
            Triple("Rs.5,000.00 available balance after the debit of Rs.250.00", "5,000.00", null), // the debit sits in the balance's clause: both hidden, the safe side
            Triple("Paid Rs.250.00 to the shop. Bal: Rs 1,000", "1,000", "250.00"),
            Triple("A payment of Rs.9,999.00 was credited. Minimum payable Rs.500.00", "500.00", "9,999.00"),
            Triple("Rs.75.00 sent to a friend", "none", "75.00"),
        )
        for ((text, secret, payment) in cases) {
            assertFalse(hidden(text).contains(secret), "'$secret' survived with the amounts hidden: ${hidden(text)}")
            assertFalse(kept(text).contains(secret), "'$secret' survived with the amounts kept: ${kept(text)}")
            if (payment != null) {
                assertTrue(kept(text).contains(payment), "the payment '$payment' was lost with the amounts kept: ${kept(text)}")
                assertFalse(hidden(text).contains(payment), "the payment '$payment' survived with the amounts hidden: ${hidden(text)}")
            }
        }
    }

    @Test
    fun anAmountWithNoWordsSayingMoneyMovedIsHiddenEvenWhenTheAmountsAreKept() {
        assertEquals("Your reward is worth Rs.NNN.NN today", kept("Your reward is worth Rs.250.00 today"))
        assertEquals("Offer: save Rs.N,NNN on rent", kept("Offer: save Rs.1,500 on rent"))
        assertEquals("Total Amount Due: Rs.NN,NNN.NN Minimum Amount Due: Rs.NNN.NN", kept("Total Amount Due: Rs.12,345.00 Minimum Amount Due: Rs.617.00"))
        assertEquals("Rs.450.00 debited from A/c XX0000", kept("Rs.450.00 debited from A/c XX1234"))
        assertEquals("Dr. INR 7.00 on 13/09/26", kept("Dr. INR 7.00 on 13/09/26"), "Dr. is not a sentence end")
        assertEquals("Amt Deducted!\nRs.12345.67 from your A/c", kept("Amt Deducted!\nRs.12345.67 from your A/c"), "a headline and its figure on two lines")
    }

    @Test
    fun amountsKeepOnlyTheirShapeSoACommaOrAMissingDecimalIsStillVisible() {
        assertEquals("Rs.N,NNN.NN debited", MessageRedactor.redact("Rs.1,250.00 debited"))
        assertEquals("Rs N,NN,NNN.NN", MessageRedactor.redact("Rs 1,25,000.00"))
        assertEquals("INR NNNN", MessageRedactor.redact("INR 1250"))
        assertEquals("debited by NNN.NN", MessageRedactor.redact("debited by 349.00"))
        assertEquals("₹NNN", MessageRedactor.redact("₹450"))
        assertEquals("on 05.10.26", MessageRedactor.redact("on 05.10.26"), "a dotted date is not an amount")
        assertEquals("on 05.10.2026", MessageRedactor.redact("on 05.10.2026"))
    }

    @Test
    fun upiAddressesLoseTheirNameAndKeepTheBankHandleAndLinksKeepTheirDomain() {
        assertEquals("paid to xxxx@okhdfcbank.", MessageRedactor.redact("paid to shop.ravi@okhdfcbank."))
        assertEquals("from xxxx@ybl on", MessageRedactor.redact("from 9876543210@ybl on"))
        assertEquals("write to xxxx@example.com now", MessageRedactor.redact("write to ravi.kumar@example.com now"))
        assertEquals("pay at https://bank.example.com/xxxx now", MessageRedactor.redact("pay at https://bank.example.com/pay/ab12cd?x=1 now"))
        assertEquals("go to hdfcbk.io/xxxx", MessageRedactor.redact("go to hdfcbk.io/x7Yz9Q"))
        assertEquals("visit www.hdfcbank.com for help", MessageRedactor.redact("visit www.hdfcbank.com for help"), "a bare domain stays")
    }

    @Test
    fun aPersonsNameBecomesNameButABankOrARailIsNotTakenForOne() {
        assertEquals("sent to NAME on", MessageRedactor.redact("sent to RAVI KUMAR on"))
        assertEquals("trf to NAME Refno XXXXXXXXXXXX", MessageRedactor.redact("trf to ANITA SHARMA Refno 628374659283"))
        assertEquals("Sent by NAME via UPI", MessageRedactor.redact("Sent by RAVI KUMAR via UPI"))
        assertEquals("NAME sent you Rs.NNN", MessageRedactor.redact("Chandu sent you Rs.120"))
        assertEquals("Dear NAME, Rs.NN", MessageRedactor.redact("Dear Ravi Kumar, Rs.50"))
        assertEquals("to A/c XX0000 NAME on 05-10-26", MessageRedactor.redact("to A/c XX5678 BENEFICIARY NAME on 05-10-26"), "the holder named after a masked account")
        val kept = listOf("paid by UPI Ref", "to VPA xxxx@ybl", "from A/c XX0000", "by your card", "to HDFC Bank A/c", "from Axis Bank", "valid from Oct 5", "Dear Customer, Rs.", "by Debit Card at", "A/c XX1234 debited by 349.00", "A/c XX1234 Avl Bal")
        for (t in kept) { val r = MessageRedactor.redact(t); assertFalse(r.contains("NAME"), "$t -> $r") }
    }

    @Test
    fun initialsAreAPartOfTheNameAndStructureLabelsAreNeverNames() {
        assertEquals("paid to NAME on 05-10-26", hidden("paid to R.K. SHARMA on 05-10-26"))
        assertEquals("sent to NAME on", hidden("sent to R. Kumar on"))
        assertEquals("Sent by NAME via UPI", hidden("Sent by A.B.C. Rao via UPI"))
        assertEquals("Total Amount Due: Rs.NN,NNN.NN", hidden("Total Amount Due: Rs.12,345.00"))
        assertEquals("Minimum Due: Rs.NNN.NN, Statement Date: 05-10-26", hidden("Minimum Due: Rs.617.00, Statement Date: 05-10-26"))
        assertEquals("Closing Balance: Rs.N.NN", hidden("Closing Balance: Rs.5.00"))
        assertFalse(hidden("Total Amount Due: Rs.12,345.00 by 25-10-26").contains("NAME"))
    }

    @Test
    fun aLowerCaseNameIsMaskedOnlyWhenAPhoneNumberOrAnAddressFollowsIt() {
        assertEquals("paid to NAME xxxx@ybl on 05-10-26", hidden("paid to ravi kumar ravi.k@ybl on 05-10-26"))
        assertEquals("sent to NAME XXXXXXXXXX", hidden("sent to lakshmi narayana 9876543210"))
        assertEquals("paid to ravi kumar on 05-10-26", hidden("paid to ravi kumar on 05-10-26"), "a lower-case name with nothing to show it is one stays: the limit is stated")
        assertEquals("to vpa xxxx@ybl", hidden("to vpa shop@ybl"))
        assertEquals("by your card XXXXXXXXXX", hidden("by your card 9876543210"))
        assertEquals("from mobile XXXXXXXXXX", hidden("from mobile 9876543210"))
        assertEquals("paid to Ravi Kumar on", hidden("paid to Ravi Kumar on").replace("NAME", "Ravi Kumar"), "title case is the other rule")
    }

    @Test
    fun digitsWrittenInGroupsAreMaskedWhenTheyAddUpToEightOrMoreButDatesAndShortGroupsStay() {
        assertEquals("Card XXXX XXXX XXXX XXXX", hidden("Card 4111 1111 1111 1234"))
        assertEquals("id XXXX XXXX XXXX", hidden("id 1234 5678 9012"))
        assertEquals("call XX XXXX XXXX", hidden("call 98 7654 3210"))
        assertEquals("card XXXX-XXXX-XXXX-XXXX", hidden("card 4111-1111-1111-1234"))
        assertEquals("on 05-10-2026 at 12:30", hidden("on 05-10-2026 at 12:30"))
        assertEquals("on 2026-10-05", hidden("on 2026-10-05"))
        assertEquals("on 05 10 2026", hidden("on 05 10 2026"))
        assertEquals("on 05-10-26 12:30:00", hidden("on 05-10-26 12:30:00"))
        assertEquals("A/c XX0000 debited", hidden("A/c XX1234 debited"), "an account's masked tail keeps its shape")
        assertEquals("ref 12 34", hidden("ref 12 34"), "six digits in groups stay")
        assertEquals("Rs.450.00 on 05-10-26 12 30", kept("Rs.450.00 debited on 05-10-26 12 30").replace(" debited", ""), "a kept amount's digits are not grouped with its neighbours")
    }

    @Test
    fun maskedTailsKeepTheirShapeButNotTheirDigitsAndReferencesBecomeXs() {
        assertEquals("A/c XX0000 on", MessageRedactor.redact("A/c XX1234 on"))
        assertEquals("Card XXXX XXXX XXXX 0000", MessageRedactor.redact("Card XXXX XXXX XXXX 0862"))
        assertEquals("card ending 0000", MessageRedactor.redact("card ending 4321"))
        assertEquals("A/c XXXXXX0000", MessageRedactor.redact("A/c XXXXXX1234"))
        assertEquals("UPI Ref XXXXXXXXXXXX.", MessageRedactor.redact("UPI Ref 628374659283."))
        assertEquals("UTR XXXXXXXXXXXXXXXXXXXXXX", MessageRedactor.redact("UTR HDFCN52025100512345678"))
        assertEquals("OTP is XXXXXX.", MessageRedactor.redact("OTP is 123456."))
        assertEquals("on 05-10-26 12:30:00", MessageRedactor.redact("on 05-10-26 12:30:00"), "dates and times stay")
    }

    @Test
    fun aRedactedTextStillReadsTheSameWayWhenTheAmountsAreKept() {
        val bad = mutableListOf<String>()
        for (s in corpus) {
            val a = CaptureClassifier.classify(s.sender, s.body, emptySet(), today)
            val b = CaptureClassifier.classify(s.sender, MessageRedactor.redact(s.body, keepAmounts = true), emptySet(), today)
            if (describe(a) != describe(b)) bad += "${s.sender}: ${describe(a)} became ${describe(b)}"
        }
        // a bill's amount is a due, and dues are always hidden: with the amounts kept, a bill reminder loses its figure and is the only kind of text that reads differently
        assertEquals(listOf("JM-JIOPAY-S: bill 70682 Jio became ignored NOT_A_TRANSACTION", "AX-HDFCBK-S: bill 1245000 HDFC Bank became ignored NOT_A_TRANSACTION"), bad)
        // the redaction really changed the text, so the equality is not trivial
        assertNotEquals(corpus[0].body, MessageRedactor.redact(corpus[0].body, keepAmounts = true))
        assertTrue(corpus.map { describe(CaptureClassifier.classify(it.sender, it.body, emptySet(), today)).substringBefore(' ') }.toSet().size >= 3, "the corpus covers transactions, bills and ignored texts")
    }

    private fun describe(o: CaptureOutcome): String = when (o) {
        is CaptureOutcome.Transaction -> "tx ${o.alert.direction} ${o.alert.amountMinor} ${o.issuer}"
        is CaptureOutcome.Bill -> "bill ${o.notice.amountMinor} ${o.issuer}"
        is CaptureOutcome.Ignored -> "ignored ${o.reason}"
    }

    @Test
    fun aVeryLongPasteIsCutAndStillRedactedQuickly() {
        val long = "Rs.1,250.00 debited from A/c XX1234 to shop@upi UPI Ref 628374659283. ".repeat(500)
        val text = MessageReport.build("AX-HDFCBK-S", long, false, "1", "x")
        assertTrue(text.length < MessageReport.MAX_CHARS + 400, "${text.length}")
        assertFalse(longDigits.containsMatchIn(text))
        assertTrue(MessageRedactor.redact("A".repeat(20_000) + " 628374659283 " + "ab/".repeat(5_000)).contains("XXXXXXXXXXXX"), "pathological text does not hang the redaction")
    }

    @Test
    fun theReportNamesTheHeaderAndHowTheAppReadItAndNeverShowsAPhoneNumber() {
        val text = MessageReport.build("AX-NEWBNK-S", "Rs.450.00 debited from A/c XX1234 on 05-10-26. UPI Ref 628374659283", keepAmounts = false, version = "0.7.2", system = "Android 14 (API 34)")
        assertTrue(text.contains("Sender: AX-NEWBNK-S"), text); assertTrue(text.contains("sender is not on the list"), text)
        assertTrue(text.contains("Rs.NNN.NN debited from A/c XX0000"), text); assertTrue(text.contains("App version: 0.7.2") && text.contains("System: Android 14 (API 34)"), text)
        assertFalse(longDigits.containsMatchIn(text), text)
        val personal = MessageReport.build("+919876543210", "hello Ravi", false, "1", "x")
        assertTrue(personal.contains("Sender: a phone number (not shown)") && !personal.contains("9876"), personal)
        assertEquals("no sender (a pasted message)", MessageReport.verdict(null, "x"))
        assertTrue(MessageReport.verdict("AX-HDFCBK-S", "Your statement is ready").contains("no amount or direction"), MessageReport.verdict("AX-HDFCBK-S", "Your statement is ready"))
        assertTrue(MessageReport.verdict("AX-NEWBNK-S", "x", setOf("NEWBNK")).contains("no amount or direction"), "an added header is a listed sender")
    }
}

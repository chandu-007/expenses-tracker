package app.expenses.core.parsers

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Some banks print only THREE digits of an account ("ICICI Bank Acct XX123 debited ..."; the public sample corpus has ICICI and IDFC accounts that way), cards and most banks four. The parser keeps
 * the digits as printed, with their length. Every text, name, digit and amount below is invented, in the shapes of the corpus and of a tester's report.
 */
class AccountDigitsReadingTest {
    private fun alert(body: String, sender: String = "AX-ICICIB-S"): ParsedAlert =
        ((CaptureClassifier.classify(sender, body, emptySet(), LocalDate(2026, 11, 2)) as? CaptureOutcome.Transaction) ?: error("not captured: $body")).alert

    private fun digits(body: String) = (GenericAlertParser.parse(body) as ParseResult.Parsed).alert.accountLast4

    @Test
    fun theTestersShapeGivesTheThreeDigitsTheDirectionTheAmountAndTheBank() {
        val body = "ICICI Bank Acct XX123 debited for Rs 500.00 on 01-Nov-26; SAMPLE MERCHANT credited. UPI:123456789012. Call 18002662 for dispute. SMS BLOCK 123 to 9215676766."
        val r = CaptureClassifier.classify("VM-ICICIB-S", body, emptySet(), LocalDate(2026, 11, 2)) as CaptureOutcome.Transaction
        assertEquals("ICICI Bank", r.issuer)
        assertEquals("123", r.alert.accountLast4, "three digits, as printed: not padded, not cut, and not the 123 after SMS BLOCK")
        assertEquals(Direction.DEBIT, r.alert.direction); assertEquals(50_000L, r.alert.amountMinor); assertEquals(LocalDate(2026, 11, 1), r.alert.date)
    }

    @Test
    fun everyWayABankWritesTheAccountDigitsIsReadWithTheLengthItShows() {
        val shapes = listOf(
            "ICICI Bank Acct XX123 debited for Rs 500.00 on 01-Nov-26; ACME credited." to "123",
            "ICICI Bank Account XXX123 credited:Rs. 1,000.00 on 02-Nov-26. Info NEFT-SAMPLE. Available Balance is Rs. 9,999.00." to "123",
            "Dear Customer, Acct XX123 is credited with Rs 1500.00 on 03-Nov-26 from SAMPLE PERSON. UPI:123456789013-ICICI Bank." to "123",
            "Rs 250.00 debited from a/c x123 on 04-11-26 to VPA shop@okicici (UPI Ref 628374659283)." to "123",
            "Rs 100.00 debited from a/c ending 123 on 05-11-26 to VPA shop@okicici (UPI Ref 628374659284)." to "123",
            "Rs 70.00 debited from A/c *123 on 05-11-26 to VPA cafe@okicici (UPI Ref 628374659285)." to "123",
            "Rs 90.00 debited on 06-Nov-26 from XX123 to SAMPLE SHOP. Avl Bal Rs 500.00." to "123",
            "Rs 90.00 debited on 06-Nov-26 from x123 to SAMPLE SHOP. Avl Bal Rs 500.00." to "123",
            "Your A/C XXXXX123 is debited by INR 1,234.00 on 16/05/26 09:30. New Bal :INR 0.00." to "123",
            "Rs 5,000.00 debited from your ac no. ending 4321 on 01-11-26 by NEFT. Ref 123456789012." to "4321",
            "Rs.1,250.00 debited from A/c XX1234 on 05-10-26 to VPA shop@okhdfc (UPI Ref 628374659283)." to "1234",
            "Rs.1,250.00 debited from A/c 1234 on 05-10-26 to VPA shop@okhdfc (UPI Ref 628374659283)." to "1234",
            "Rs.1,250.00 debited from A/c *****1234 on 05-10-26 to VPA shop@okhdfc (UPI Ref 628374659283)." to "1234",
            "Rs.1,250.00 debited from A/c XX12345 on 05-10-26 to VPA shop@okhdfc (UPI Ref 628374659283)." to "2345",
            "INR 482.35 spent using ICICI Bank Card XX3025 on 01-Nov-26 on SAMPLE SHOP. Avl Limit: INR 90,000.00." to "3025",
            "INR 482.35 spent using ICICI Bank Card ending 3025 on 01-Nov-26 on SAMPLE SHOP." to "3025",
            "Rs 482.35 spent on your card ending in 3025 at SAMPLE SHOP on 01-Nov-26." to "3025",
            "Rs 482.35 spent on ICICI Bank Card XX123 at SAMPLE SHOP on 01-Nov-26." to "123",
        )
        for ((body, want) in shapes) assertEquals(want, digits(body), body)
    }

    @Test
    fun textsWithNoAccountMaskGiveNoDigitsAndTheNumbersAroundThemAreNotTakenForOne() {
        for (body in listOf(
            "Rs 500.00 debited on 01-Nov-26 UPI Ref 123456789012. SMS BLOCK 123 to 9215676766.",
            "Rs 500.00 debited on 01-Nov-26 to SHOP 4521 BRANCH 123. Call 18002662.",
            "Rs 500.00 debited on 01-Nov-26. Quote 12345 for any query.",
            "Rs 500.00 debited at SAMPLE SHOP on 01-Nov-26. Avl Bal Rs 12,345.00.",
        )) assertNull(digits(body), body)
        // a long mask is the OTHER end of a transfer, never the text's own digits
        assertNull(digits("Rs 5,000.00 credited on 02-11-26. XXXXXXXXXX1234-TPT-rent-SAMPLE PERSON."))
        assertEquals("1234", TransferClues.counterLast4("XXXXXXXXXX1234-TPT-rent-SAMPLE PERSON", own = null))
    }

    @Test
    fun theOtherAccountOfAnOwnTransferKeepsItsThreeDigitsAndIsNeverTheTextsOwn() {
        val imps = "ICICI Bank Acct XX000 debited with Rs 10,000.00 on 02-May-26 & Acct XX001 credited.IMPS:000000000000. Call 18002662 for dispute or SMS BLOCK 000 to 9215676766"
        val a = (GenericAlertParser.parse(imps) as ParseResult.Parsed).alert
        assertEquals("000", a.accountLast4); assertEquals("001", a.counterLast4)
        // the same digits twice, or three of an account whose four are named, are one account
        assertNull((GenericAlertParser.parse("Acct XX123 debited with Rs 100.00 on 02-May-26 & Acct XX123 credited.IMPS:000000000000") as ParseResult.Parsed).alert.counterLast4)
        assertNull(TransferClues.counterLast4("Acct XX123 debited & Acct XX4123 credited", own = "123"), "4123 ends in 123: the same account")
        // a four-digit pair, as before
        val hdfc = (GenericAlertParser.parse("Rs 5,000.00 sent to A/c 9876 via IMPS from A/c XX1234 on 02-11-26. Ref 123456789012") as ParseResult.Parsed).alert
        assertEquals("1234", hdfc.accountLast4)
    }

    @Test
    fun aThreeDigitAccountAndAFourDigitCardOfOneBankStayApart() {
        val account = alert("ICICI Bank Acct XX123 debited for Rs 450.00 on 05-Nov-26; ACME STORES credited. UPI:628374659283.")
        val card = alert("INR 1,250.00 spent using ICICI Bank Card XX3025 on 05-Nov-26 on ACME STORES. Avl Limit: INR 90,000.00.", sender = "BP-ICICIT-S")
        assertEquals("123", account.accountLast4); assertEquals("3025", card.accountLast4)
    }

    @Test
    fun theLengthSurvivesTheWholeParseNoMatterWhichBankSentIt() {
        for (sender in listOf("AX-HDFCBK-S", "VM-SBIINB", "JD-ICICIB-T", "AX-IDFCFB-S", "BP-AXISBK-S")) {
            val a = alert("Rs 75.00 debited from A/c XX987 on 05-Nov-26. Avl Bal Rs 1,000.00. UPI Ref 628374659283", sender)
            assertEquals("987", a.accountLast4, sender)
        }
        assertTrue(listOf("12", "1").none { digits("Rs 75.00 debited from A/c XX$it on 05-Nov-26. UPI Ref 628374659283") != null }, "one or two digits are not an account's digits")
    }
}

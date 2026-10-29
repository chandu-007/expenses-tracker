package app.expenses.core.parsers

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The two texts HDFC Bank sends for one move of money between a person's own accounts, with invented digits (1234 is the account the money left, 5678 the one
 * it reached) and an invented amount. The wording is the shape of the bank's public sample texts (the corpus), not a copy of anyone's message.
 */
class OwnTransferTextsTest {
    private val out = "Amt Deducted!\nRs.45,250.75 from your HDFC Bank A/c XX1234 for Money Transfer via HDFC Bank Online Banking.\nNot you?Call 18002586161/SMS BLOCK OB to 7308080808"
    private val inn = "Update! INR 45,250.75 deposited in HDFC Bank A/c XX5678 on 29-OCT-26 for XXXXXXXXXX1234-TPT-Holiday Fund Remembe-SAMPLE TRADERS PRIVATE LIMITED.Avl bal INR 3,21,654.32. Cheque deposits in A/C are subject to clearing"
    private val today = LocalDate(2026, 10, 29)

    private fun tx(body: String) = (CaptureClassifier.classify("AX-HDFCBK-S", body, emptySet(), today) as CaptureOutcome.Transaction).alert

    @Test
    fun theDebitIsMoneyOutOfTheSourceAccountAndTheBankIsNotAPayee() {
        val a = tx(out)
        assertEquals(Direction.DEBIT, a.direction); assertEquals(4_525_075, a.amountMinor); assertEquals("INR", a.currency)
        assertEquals("1234", a.accountLast4); assertNull(a.counterLast4, "the debit does not name the other account")
        assertNull(a.date, "the debit carries no date: the day it arrived stands in")
        assertNull(PayeeGuess.of(out), "no payee is named")
    }

    @Test
    fun theCreditIsMoneyIntoTheDestinationAndTheBalanceIsNotTheAmount() {
        val a = tx(inn)
        assertEquals(Direction.CREDIT, a.direction)
        assertEquals(4_525_075, a.amountMinor, "the balance 'Avl bal INR 3,21,654.32' is not the amount")
        assertEquals("5678", a.accountLast4)
        assertEquals("1234", a.counterLast4, "the narration names the account the money came from")
        assertEquals(LocalDate(2026, 10, 29), a.date)
    }

    @Test
    fun theNarrationGivesACleanTitleNotTheWholeString() {
        assertEquals("XXXXXXXXXX1234-TPT-Holiday Fund Remembe-SAMPLE TRADERS PRIVATE LIMITED", PayeeGuess.narration(inn))
        assertEquals("Holiday Fund Remembe", PayeeGuess.of(inn))
        assertNull(PayeeGuess.narration(out))
    }

    @Test
    fun bothTextsCarryTheTransferWordsAndAPlainPaymentDoesNot() {
        assertTrue(TransferClues.hasTransferWords(out)); assertTrue(TransferClues.hasTransferWords(inn))
        assertFalse(TransferClues.hasTransferWords("Rs.250.00 debited from A/c XX1234 on 05-10-26 to VPA swiggy@icici (UPI Ref 100000000101)"))
        assertFalse(TransferClues.hasTransferWords("Spent Rs.100 On HDFC Bank Card 0000 At URBAN BISTRO On 2026-05-16:15:55:55"))
    }

    @Test
    fun theOtherAccountsDigitsAreFoundInEveryWordingTheBankUses() {
        // table: text, own digits, the other account's digits
        val table = listOf(
            Triple("IMPS INR 1,00,000.00\nsent from HDFC Bank A/c XX1234 on 26-05-26\nTo A/c xxxxxxxxxx5678\nRef-000000000001", "1234", "5678"),
            Triple("HDFC Bank:Rs. 1.00 debited from a/c *1234 on 07/09/26 to a/c **5678 (UPI Ref No. 000000000000). Not you?", "1234", "5678"),
            Triple("Rs.500 credited to A/c XX5678 on 05-10-26 from A/c XX1234", "5678", "1234"),
            Triple("Update! INR 100.00 deposited in HDFC Bank A/c XX5678 on 16-MAY-26 for FT- CUSTOMER NAME-XXXXXXXXXX1234 - REMITTER NAME.Avl bal INR 200.00", "5678", "1234"),
        )
        for ((text, own, other) in table) {
            val a = tx(text)
            assertEquals(own, a.accountLast4, text); assertEquals(other, a.counterLast4, text)
        }
    }

    @Test
    fun aPlainPaymentOrACardSpendNamesNoOtherAccount() {
        for (t in listOf(
            "Rs.1,250.00 debited from A/c XX1234 on 05-10-26 to VPA shop@okhdfc (UPI Ref 628374659283). Not you? Call 18002586161",
            "Spent Rs.10290 On HDFC Bank Card 0000 At EAZYDINE0000000 On 2026-05-02:22:26:01.Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 0000 to 7308080808",
            "Rs.5,000.00 withdrawn at ATM 12345 on 05-10-26 from A/c XX1234",
        )) assertNull(tx(t).counterLast4, t)
        // a masked card number is the card itself, never "the other account"
        assertNull(TransferClues.counterLast4("Rs.200 spent on card xxxxxxxxxxxx4321 at SHOP", "4321"))
    }

    @Test
    fun narrationsOfEveryShapeGiveACleanTitle() {
        val table = listOf(
            "Update! INR 500.00 deposited in HDFC Bank A/c XX5678 on 29-OCT-26 for XXXXXXXXXX1234-TPT-Rent share-ANITA RAO.Avl bal INR 9.00" to "Rent share",
            "Update! INR 500.00 deposited in HDFC Bank A/c XX5678 on 29-OCT-26 for XXXXXXXXXX1234-TPT--ANITA RAO.Avl bal INR 9.00" to "ANITA RAO",
            "Update! INR 100.00 deposited in HDFC Bank A/c XX5678 on 29-JUN-26 for NEFT Cr-SAMPLE0INBX01-State-Bank Remitter-Customer Name-SAMPLEH00000000000.Avl bal INR 200.00" to "State-Bank Remitter",
            "Update! INR 100.00 deposited in HDFC Bank A/c XX5678 on 29-JUN-26 for NEFT Cr-SAMPLE0INBX01-Sample Remitter Inc-Customer Name-SAMPLEH00000000000.Avl bal INR 200.00" to "Sample Remitter Inc",
            "Rs.450.00 debited from A/c XX1234 on 05-10-26. Info: UPI-RAVI KUMAR-ravi@okaxis-123456789012. Avl Bal Rs.100" to "RAVI KUMAR",
            "Rs.450.00 debited from A/c XX1234 on 05-10-26. Info: IMPS-123456789012-ANITA RAO. Avl Bal Rs.100" to "ANITA RAO",
            "Rs.450.00 debited from A/c XX1234 on 05-10-26. Info: NEFT-HDFCN123456-PRIYA SHARMA. Avl Bal Rs.100" to "PRIYA SHARMA",
            "Rs.1,999.00 debited from A/c XX1234 on 05-10-26. Info: ACH D- TATA CAPITAL-123456. Avl Bal Rs.100" to "TATA CAPITAL",
            "Rs.499.00 debited from A/c XX1234 on 05-10-26. Info: SI-HDFC LIFE-998877. Avl Bal Rs.100" to "HDFC LIFE",
            "Rs.450.00 debited from A/c XX1234 on 05-10-26. Info: POS 400000XXXXXX1234 CITY BAKERY. Avl Bal Rs.100" to null,
            "Rs.2,000.00 debited from A/c XX1234 on 05-10-26. Info: ATM-CASH. Avl Bal Rs.100" to "ATM cash",
            "Update! INR 100.00 deposited in HDFC Bank A/c XX5678 on 16-MAY-26 for FT- ANITA RAO-XXXXXXXXXX1234 - RAVI KUMAR.Avl bal INR 200.00" to "RAVI KUMAR",
            "Your A/c XXXXXXX5678 has been credited with Rs. 2,500.00 on 21-08-2026. Info: NEFT/IN00000000000000/SAMPLE NAME.\nNew bal: Rs. 99,999.99. Team IDFC FIRST Bank" to "SAMPLE NAME",
        )
        for ((text, title) in table) assertEquals(title, PayeeGuess.of(text), text)
    }

    @Test
    fun wordingThatMerelyMentionsTheRailIsNotANarration() {
        // "for NEFT txn via ..." has no hyphenated parts: nothing to read, so the payee stays unknown
        assertNull(PayeeGuess.narration("Amt Deducted! Rs.12345.67 from your HDFC Bank A/c XX1234 for NEFT txn via HDFC Bank Online Banking Not you?Call 18002586161"))
        assertNull(PayeeGuess.of("Amt Deducted! Rs.12345.67 from your HDFC Bank A/c XX1234 for NEFT txn via HDFC Bank Online Banking Not you?Call 18002586161"))
    }

    @Test
    fun theAccountTheTextIsAboutIsTheOneMoneyLeftOrReachedWhateverOrderTheTextNamesThem() {
        // own digits, other account's digits
        val table = listOf(
            Triple("Rs 5000 sent to A/c 9876 via IMPS from A/c XX1234 on 05-10-26. Ref 628374659283", "1234", "9876"),
            Triple("Rs.5,000.00 transferred to A/c xxxxxxxxxx9876 from your A/c XX1234 on 05-10-26", "1234", "9876"),
            Triple("Rs 5000 credited to A/c XX5678 on 05-10-26 from A/c 1234 via IMPS", "5678", "1234"),
            Triple("Rs 5000 credited from A/c 1234 to your A/c XX5678 on 05-10-26", "5678", "1234"),
            Triple("IMPS INR 1,00,000.00 sent from HDFC Bank A/c XX1234 on 26-05-26 To A/c xxxxxxxxxx5678", "1234", "5678"),
            Triple("Rs.1.00 debited from a/c *1234 on 07/09/26 to a/c **5678 (UPI Ref No. 000000000000)", "1234", "5678"),
        )
        for ((text, own, other) in table) { val a = tx(text); assertEquals(own, a.accountLast4, text); assertEquals(other, a.counterLast4, text) }
        // one account only: unchanged
        assertEquals("1234", tx("Rs.250.00 debited from A/c XX1234 on 05-10-26 to VPA swiggy@icici (UPI Ref 100000000101)").accountLast4)
    }

    @Test
    fun theRailsDoNotMakeAnIncomingTextLookLikeAnOwnAccountTransfer() {
        assertFalse(TransferClues.hasTransferWords("Update! INR 50,000.00 deposited in HDFC Bank A/c XX5678 on 29-OCT-26 for NEFT Cr-SAMPLE0INBX01-Acme Payroll-Customer-REF123.Avl bal INR 9.00"))
        assertFalse(TransferClues.hasTransferWords("IMPS INR 1,00,000.00 sent from HDFC Bank A/c XX1234 on 26-05-26 To A/c xxxxxxxxxx9999 Ref-000000000001"))
    }
}

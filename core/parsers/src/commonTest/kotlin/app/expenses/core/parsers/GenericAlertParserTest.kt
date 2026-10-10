package app.expenses.core.parsers

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Synthetic messages written for these tests, modelled on the layouts seen in public alert samples. */
class GenericAlertParserTest {
    private fun parsed(body: String): ParsedAlert = assertIs<ParseResult.Parsed>(GenericAlertParser.parse(body)).alert
    private fun notTx(body: String): String = assertIs<ParseResult.NotATransaction>(GenericAlertParser.parse(body)).reason

    @Test
    fun otpMessagesAreRefusedBeforeAnythingElseIsRead() {
        assertEquals("otp", notTx("123456 is your OTP for payment of Rs.1,500.00 at SHOP. Do not share it."))
        assertEquals("otp", notTx("Use one time password 4821 to approve Rs 200 debited from A/c XX1234"))
    }

    @Test
    fun balanceAndLimitFiguresAreNeverTakenAsTheAmount() {
        val a = parsed("Rs.250.00 debited from A/c XX1234 on 05-09-26. Avl Bal Rs.9,999.99")
        assertEquals(25_000L, a.amountMinor)
        val b = parsed("Spent INR 480.00 on card XX9876 at CAFE. Avl Limit: INR 50,000.00")
        assertEquals(48_000L, b.amountMinor)
        assertEquals(Direction.DEBIT, b.direction)
    }

    @Test
    fun directionFollowsTheFirstDirectionWordSoDrBeforeCrIsADebit() {
        assertEquals(Direction.DEBIT, parsed("Rs.230.00 Dr. from A/C XXXXXX1234 and Cr. to someone@bank. Ref:123456789012").direction)
        assertEquals(Direction.CREDIT, parsed("Rs 1,000.00 credited to A/c XX1234 on 05-Sep-26 from RAHUL. UPI Ref 123456789012").direction)
        assertEquals(Direction.CREDIT, parsed("Alert! Rs. 262.56 refunded by SHOP on 17/MAY/2026 to your card ending 4321").direction)
    }

    @Test
    fun anOutgoingPaymentDescribedAsCreditedToTheBeneficiaryIsADebit() {
        assertEquals(Direction.DEBIT, parsed("NEFT of Rs. 2,500.00 from your A/c XX1234 has been credited to the beneficiary account on 21-08-2026").direction)
    }

    @Test
    fun declinedPaymentsAreReportedAsDeclinedNotAsSpending() {
        assertEquals(Direction.DECLINED, parsed("Txn of Rs.637.70 thru A/C XX1234 on 18-8-26 to SHOP failed due to INSUFFICIENT FUNDS").direction)
    }

    @Test
    fun indianGroupingColonAfterRsAndCompactDatesAreUnderstood() {
        val a = parsed("Dear Customer, A/c X4321-credited by Rs:2,00,000.50 on 28Jul26 transfer from RAHUL Ref No 123456789012")
        assertEquals(20_000_050L, a.amountMinor)
        assertEquals(LocalDate(2026, 7, 28), a.date)
        assertEquals("4321", a.accountLast4)
        assertEquals("123456789012", a.reference)
    }

    @Test
    fun dateFormatsAreRecognised() {
        assertEquals(LocalDate(2026, 5, 17), parsed("Sent Rs.100.00 From A/C *0000 On 17/05/26 Ref 000000000000").date)
        assertEquals(LocalDate(2026, 9, 6), parsed("Rs.1.00 Dr. from A/C XX1234. AvlBal:Rs5.00(2026:09:06 09:56:33)").date)
        assertEquals(LocalDate(2026, 9, 7), parsed("Rs. 10.00 spent on your credit card xx0000 at SHOP on 07-Sep-26.").date)
        assertNull(parsed("Rs.100.00 spent on card XX0000 at SHOP on 23-05").date) // no year: not guessed
    }

    @Test
    fun foreignSpendKeepsTheRupeesChargedAndTheOriginalAmount() {
        val a = parsed("USD 12.00 | Rs. 1,000.00 spent on your credit card xx0000 at SHOP INC NEW YORK US on 07-Sep-26.")
        assertEquals("INR", a.currency)
        assertEquals(100_000L, a.amountMinor)
        assertEquals("USD", a.originalCurrency)
        assertEquals(1_200L, a.originalAmountMinor)
    }

    @Test
    fun dueNoticesAndMessagesWithoutADirectionAreNotTransactions() {
        listOf(
            "Payment of Credit Card 1234 is due on 10/07/26. Min due Rs 500.00 Total due Rs 5000.00.",
            "Your limit is now Rs 50,000.00 and your card ending 1234 is active.",
            "Login failed on your mobile banking app. Call us if this was not you.",
            "Rs 500.00 offer on your next order. Apply now.",
        ).forEach { assertIs<ParseResult.NotATransaction>(GenericAlertParser.parse(it), it) }
    }

    @Test
    fun aLayoutWithNoDirectionWordAndNotACardUseIsLeftUnrecognisedRatherThanGuessed() {
        // Guessing a sign would silently corrupt the books. The one exception is HDFC's card alert ("Txn Rs.X On HDFC Bank Card N At shop"), which a real user
        // text showed is how a card purchase is worded; it is read as money out and, like every generic result, still waits in the review inbox.
        assertEquals("no direction", notTx("Txn Rs.100.00 pending with your branch ref 000000000000 on 23-05"))
        assertEquals("no direction", notTx("Rs.100.00 UPI 000000000000 On 23-05 shop"))
    }

    @Test
    fun upiReferencesAndMasksAreExtractedFromCommonLayouts() {
        val a = parsed("INR 726.00 debited A/c no. XX1234 05-09-26, 10:19:49 UPI/P2M/987654321098/RAHUL SHARMA Not you?")
        assertEquals("987654321098", a.reference)
        assertEquals("1234", a.accountLast4)
        assertTrue(a.viaUpi)
    }

    @Test
    fun aCreditWordedWithAPassiveDebitWordIsStillACredit() {
        fun dir(t: String) = (GenericAlertParser.parse(t) as ParseResult.Parsed).alert.direction
        assertEquals(Direction.CREDIT, dir("Sent by RAVI KUMAR via UPI: Rs.200.00 received in your account. Ref 123456789012"))
        assertEquals(Direction.CREDIT, dir("Paid by SWIGGY: INR 500 credited to A/c XX1234. UPI Ref 123456789012"))
        assertEquals(Direction.CREDIT, dir("Transferred by AMIT from HDFC: Rs 75 deposited. Ref 987654321098"))
    }

    @Test
    fun aDebitThatSaysPaidByYourCardIsStillADebit() {
        fun dir(t: String) = (GenericAlertParser.parse(t) as ParseResult.Parsed).alert.direction
        assertEquals(Direction.DEBIT, dir("Rs.499.00 paid by your card ending 4321 at AMAZON on 05-10-26"))
        assertEquals(Direction.DEBIT, dir("Rs.499.00 sent by you to RAVI via UPI Ref 123456789012"), "no credit word: unchanged")
        assertEquals(Direction.DEBIT, dir("Rs.100 debited from A/c XX1 and sent to shop, credited to the beneficiary account"), "beneficiary rule intact")
    }

    @Test
    fun anHdfcCardTxnAlertWithNoDebitWordIsAnExpenseWithItsCardDigitsReferenceAndMerchant() {
        val body = "Txn Rs.20.00\nOn HDFC Bank Card 0862\nAt paytm.s2x4b6x@pty\nby UPI 664912339598\nOn 10-10\nNot You?\nCall 18002586161/SMS BLOCK CC 0862 to 7308080808"
        val a = (GenericAlertParser.parse(body) as ParseResult.Parsed).alert
        assertEquals(Direction.DEBIT, a.direction); assertEquals(2000, a.amountMinor); assertEquals("0862", a.accountLast4); assertEquals("664912339598", a.reference)
        assertEquals("paytm.s2x4b6x@pty", PayeeGuess.of(body))
        assertIs<CaptureOutcome.Transaction>(CaptureClassifier.classify("AX-HDFCBK-S", body))
    }

    @Test
    fun aTxnTextThatIsNotACardUseOrHasACreditWordIsNotTurnedIntoAnExpense() {
        assertIs<ParseResult.NotATransaction>(GenericAlertParser.parse("Txn Rs.20.00 pending at your branch. Visit us"), "no card: still no direction")
        assertEquals(Direction.CREDIT, (GenericAlertParser.parse("Txn Rs.500.00 credited to your HDFC Bank Card 0862 at 10-10") as ParseResult.Parsed).alert.direction, "a credit word still decides")
    }
}

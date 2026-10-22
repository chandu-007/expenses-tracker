package app.expenses.core.domain

import app.expenses.core.domain.StatementTextParser.Convention
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * All statement text here is SYNTHETIC: invented to mirror the layouts Indian bank and card statements are described as having, after text has been taken
 * out of a PDF or recognised from a photo. None is a real statement and none has been checked against a real bank's statement.
 */
class StatementTextParserTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate(y, m, day)

    /** The rows after the header as `date | description | amount | type`. */
    private fun rows(r: StatementTextParser.Result) = r.table.rows.drop(1).map { it.joinToString(" | ") }

    /** The whole path a person's file takes: text, table, the detector's guess, the importer's parse. */
    private fun import(text: String, convention: Convention? = null): ParsedStatement {
        val r = StatementTextParser.parse(text, convention)
        val m = StatementDetector.detect(r.table.rows)
        assertEquals(AmountLayout.AMOUNT_WITH_TYPE, m.layout)
        assertEquals(null, m.missing())
        return StatementParser.parse(r.table.rows, m, 2, { r.table.lines[it] }, { r.rawLines.getOrNull(it) })
    }

    // ---- credit card statements ----

    private val card = """
        HDFC Bank Credit Card Statement
        Statement Date: 12/10/2025
        Total Amount Due 5,432.10
        Minimum Amount Due 270.00
        Credit Limit 2,00,000.00
        Date Transaction Description Amount (Rs.)
        12/09/2025 AMAZON PAY INDIA MUMBAI 1,299.00
        13/09/2025 SWIGGY BANGALORE 450.50
        14/09/2025 PAYMENT RECEIVED - THANK YOU 5,000.00 Cr
        15/09/2025 AMAZON REFUND MUMBAI 799.00 Cr
        Page 1 of 2
        Reward Points Earned 120
    """.trimIndent()

    @Test
    fun cardStatementPurchasesAreSpendingCrIsMoneyBackAndPaymentsToTheCardAreLeftOut() {
        val r = StatementTextParser.parse(card)
        assertEquals(Convention.CARD, r.convention, "the words say it is a card statement")
        assertEquals(
            listOf("12/09/2025 | AMAZON PAY INDIA MUMBAI | 1299.00 | Dr", "13/09/2025 | SWIGGY BANGALORE | 450.50 | Dr", "15/09/2025 | AMAZON REFUND MUMBAI | 799.00 | Cr"),
            rows(r),
        )
        assertEquals(1, r.cardPayments)
        assertEquals(StatementTextParser.HEADER, r.table.rows[0])
    }

    @Test
    fun cardPaymentWordingsAreAllSkippedAndNothingElseIs() {
        val text = """
            Credit Card Statement
            01/09/2025 PAYMENT RECEIVED - THANK YOU 1,000.00 Cr
            02/09/2025 PAYMENT THANK YOU 2,000.00 Cr
            03/09/2025 BBPS PAYMENT 3,000.00 Cr
            04/09/2025 BBPS Payment received 4,000.00
            05/09/2025 NEFT PAYMENT RECEIVED 5,000.00
            06/09/2025 CASHBACK CREDIT 50.00 Cr
            07/09/2025 REVERSAL MERCHANT XYZ 99.00 Cr
            08/09/2025 PAYMENT GATEWAY FEE 10.00
        """.trimIndent()
        val r = StatementTextParser.parse(text)
        assertEquals(5, r.cardPayments)
        assertEquals(listOf("06/09/2025 | CASHBACK CREDIT | 50.00 | Cr", "07/09/2025 | REVERSAL MERCHANT XYZ | 99.00 | Cr", "08/09/2025 | PAYMENT GATEWAY FEE | 10.00 | Dr"), rows(r))
    }

    @Test
    fun aCardPaymentIsNotIncomeWhateverMarkOrNoMarkItHas() {
        for (line in listOf("PAYMENT RECEIVED 5,000.00", "PAYMENT RECEIVED 5,000.00 CR", "PAYMENT RECEIVED 5,000.00-", "PAYMENT RECEIVED - THANK YOU 5,000.00 C")) {
            val r = StatementTextParser.parse("Credit Card Statement\n01/09/2025 $line\n02/09/2025 SHOP 10.00", Convention.CARD)
            assertEquals(1, r.cardPayments, line)
            assertEquals(listOf("02/09/2025 | SHOP | 10.00 | Dr"), rows(r), line)
        }
    }

    @Test
    fun onABankStatementPaymentReceivedIsRealMoneyInAndKept() {
        val r = StatementTextParser.parse("01/09/2025 PAYMENT RECEIVED FROM RAVI 5,000.00 Cr\n02/09/2025 SHOP 10.00 Dr", Convention.BANK)
        assertEquals(0, r.cardPayments)
        assertEquals(listOf("01/09/2025 | PAYMENT RECEIVED FROM RAVI | 5000.00 | Cr", "02/09/2025 | SHOP | 10.00 | Dr"), rows(r))
    }

    @Test
    fun theChosenAccountSetsTheConventionAndTheWordsDoWhenNothingWasChosen() {
        val plain = "01/09/2025 SHOP 10.00\n02/09/2025 REFUND 5.00-"
        assertEquals(Convention.BANK, StatementTextParser.parse(plain).convention)
        assertEquals(Convention.CARD, StatementTextParser.parse(plain, Convention.CARD).convention)
        assertEquals(Convention.CARD, StatementTextParser.parse("Total Amount Due 10.00\n$plain").convention)
        // the same text, the other way round: a minus is a refund on a card and spending on a bank account
        assertEquals(listOf("01/09/2025 | SHOP | 10.00 | Dr", "02/09/2025 | REFUND | 5.00 | Cr"), rows(StatementTextParser.parse(plain, Convention.CARD)))
        assertEquals(listOf("01/09/2025 | SHOP | 10.00 | Cr", "02/09/2025 | REFUND | 5.00 | Dr"), rows(StatementTextParser.parse(plain, Convention.BANK)))
    }

    @Test
    fun sbiCardStyleSingleLetterMarks() {
        val r = StatementTextParser.parse("Credit Card Statement\n12 Sep 25 AMAZON PAY MUMBAI 1,299.00 D\n13 Sep 25 REFUND AMAZON 500.00 C")
        assertEquals(listOf("12 Sep 25 | AMAZON PAY MUMBAI | 1299.00 | Dr", "13 Sep 25 | REFUND AMAZON | 500.00 | Cr"), rows(r))
    }

    // ---- dates ----

    @Test
    fun everyCommonDateWrittenAtTheStartOfALine() {
        val lines = listOf(
            "12/09/2025 A 10.00 Dr", "12-09-2025 A 10.00 Dr", "12.09.2025 A 10.00 Dr", "12/09/25 A 10.00 Dr", "12 Sep 2025 A 10.00 Dr", "12-Sep-25 A 10.00 Dr",
            "12Sep2025 A 10.00 Dr", "12 September 2025 A 10.00 Dr", "12-Sep-2025 A 10.00 Dr", "2025-09-12 A 10.00 Dr", "Sep 12, 2025 A 10.00 Dr",
            "12/09/2025 16:45 A 10.00 Dr", "12/09/2025 16:45:10 A 10.00 Dr", "12/09/2025 13/09/2025 A 10.00 Dr", "12/09/2025 04:45 PM A 10.00 Dr",
        )
        for (line in lines) {
            val p = import(line)
            assertEquals(listOf(d(2025, 9, 12)), p.txns.map { it.date }, line)
            assertEquals("A", p.txns.single().description, line)
            assertEquals(1000L, p.txns.single().minor, line)
            assertEquals(Flow.SPENDING, p.txns.single().flow, line)
        }
    }

    @Test
    fun linesWithoutARealDateAreNotTransactions() {
        val r = StatementTextParser.parse("31/02/2025 A 10.00 Dr\n45/13/2025 B 10.00 Dr\nNo 12 of the list 10.00\n12345 SHOP 10.00 Dr\nHello")
        assertEquals(emptyList(), rows(r))
    }

    @Test
    fun twoDigitYearsAreReadThroughTheSameRulesAsCsvDates() {
        val p = import("Statement of Account\n01/03/26 SHOP 10.00 Dr\n02/03/26 SALARY 100.00 Cr")
        assertEquals(listOf(d(2026, 3, 1), d(2026, 3, 2)), p.txns.map { it.date })
    }

    @Test
    fun monthFirstDatesAreDetectedAsMonthFirst() {
        val r = StatementTextParser.parse("Sep 12, 2025 SHOP 10.00 Dr\nSep 13, 2025 SHOP 20.00 Dr")
        val m = StatementDetector.detect(r.table.rows)
        assertEquals(DateOrder.MDY, m.dateOrder)
        assertEquals(listOf(d(2025, 9, 12), d(2025, 9, 13)), StatementParser.parse(r.table.rows, m).txns.map { it.date })
    }

    // ---- amounts and marks ----

    @Test
    fun amountsWithCurrencySignsAndMarksInEveryCommonWriting() {
        val text = """
            01/09/2025 A ₹ 1,299.00 Dr
            02/09/2025 B Rs. 500.00 Cr
            03/09/2025 C INR 250.00 DR
            04/09/2025 D ₹1,23,456.78 Dr
            05/09/2025 E 75.5 Dr
            06/09/2025 F 100.00CR
            07/09/2025 G (40.00) Dr
            08/09/2025 H 12.00 Debit
            09/09/2025 I 13.00 Credit
            10/09/2025 J 1,299.00 Dr.
            11/09/2025 K - 499.00 Dr
        """.trimIndent()
        val r = StatementTextParser.parse(text, Convention.BANK)
        assertEquals(
            listOf(
                "01/09/2025 | A | 1299.00 | Dr", "02/09/2025 | B | 500.00 | Cr", "03/09/2025 | C | 250.00 | Dr", "04/09/2025 | D | 123456.78 | Dr", "05/09/2025 | E | 75.50 | Dr",
                "06/09/2025 | F | 100.00 | Cr", "07/09/2025 | G | 40.00 | Dr", "08/09/2025 | H | 12.00 | Dr", "09/09/2025 | I | 13.00 | Cr", "10/09/2025 | J | 1299.00 | Dr",
                "11/09/2025 | K | 499.00 | Dr",
            ),
            rows(r),
        )
    }

    @Test
    fun aDashInTheDescriptionIsNotAMinusSign() {
        val r = StatementTextParser.parse("Credit Card Statement\n01/09/2025 NETFLIX - 499.00")
        assertEquals(listOf("01/09/2025 | NETFLIX | 499.00 | Dr"), rows(r))
    }

    @Test
    fun aTrailingMinusIsMoneyBackOnACardAndSpendingOnABank() {
        val text = "01/09/2025 SHOP 1,299.00\n02/09/2025 REVERSAL 250.00-"
        assertEquals(listOf("01/09/2025 | SHOP | 1299.00 | Dr", "02/09/2025 | REVERSAL | 250.00 | Cr"), rows(StatementTextParser.parse(text, Convention.CARD)))
        assertEquals(listOf("01/09/2025 | SHOP | 1299.00 | Cr", "02/09/2025 | REVERSAL | 250.00 | Dr"), rows(StatementTextParser.parse(text, Convention.BANK)))
    }

    @Test
    fun whenNothingInTheStatementSaysWhichWayMoneyWentEveryAmountIsSpendingAndTheNoteSaysSo() {
        val r = StatementTextParser.parse("01/09/2025 SHOP 10.00\n02/09/2025 OTHER 20.00", Convention.BANK)
        assertEquals(listOf("01/09/2025 | SHOP | 10.00 | Dr", "02/09/2025 | OTHER | 20.00 | Dr"), rows(r))
        assertTrue(r.note.contains("does not say"), r.note)
    }

    @Test
    fun foreignCurrencyLinesUseTheRupeeAmount() {
        val text = """
            Credit Card Statement
            14/09/2025 NETFLIX.COM USD 12.00 1,019.40
            15/09/2025 AMAZON US 12.00 USD 1,019.40
            16/09/2025 BOOKING.COM EUR 100.50 9,500.25 Dr
            17/09/2025 REFUND UBER GBP 5.00 500.00 Cr
        """.trimIndent()
        assertEquals(
            listOf(
                "14/09/2025 | NETFLIX.COM USD 12.00 | 1019.40 | Dr", "15/09/2025 | AMAZON US USD 12.00 | 1019.40 | Dr", "16/09/2025 | BOOKING.COM EUR 100.50 | 9500.25 | Dr",
                "17/09/2025 | REFUND UBER GBP 5.00 | 500.00 | Cr",
            ),
            rows(StatementTextParser.parse(text)),
        )
    }

    @Test
    fun anAmountThatCannotBeReadSafelyIsPassedOnAsWrittenAndReportedNotGuessed() {
        val p = import("Statement of Account\n01/09/2025 SHOP 1,29.00 Dr\n02/09/2025 OK 10.00 Dr")
        assertEquals(1, p.txns.size)
        assertEquals(1, p.problems.size)
        assertTrue(p.problems.single().reason.contains("1,29.00"), p.problems.single().reason)
    }

    // ---- what is not a transaction ----

    @Test
    fun totalsBalancesDuesPagesAndPointsAreSkipped() {
        val text = """
            Statement of Account
            Account Statement for the period 01/09/2025 to 30/09/2025
            Opening Balance 10,000.00
            01/09/2025 Balance B/F 10,000.00
            Date Narration Chq/Ref Withdrawal Deposit Balance
            02/09/2025 SHOP 100.00 Dr
            Total Debits 100.00
            Total Amount Due 5,432.10
            Minimum Amount Due 270.00
            Closing Balance 9,900.00
            Page 2 of 3
            Reward Points 120
            Payment Due Date 05/10/2025
            03/09/2025 Closing Balance 9,900.00
        """.trimIndent()
        assertEquals(listOf("02/09/2025 | SHOP | 100.00 | Dr"), rows(StatementTextParser.parse(text, Convention.BANK)))
    }

    @Test
    fun aHeaderThatWrapsOverSeveralLinesIsNotPartOfTheLineBeforeIt() {
        val text = """
            12/09/2025 SWIGGY BANGALORE 450.50 Dr
            Page 1 of 2
            Date
            Transaction
            Details
            Amount
            13/09/2025 ZOMATO 100.00 Dr
        """.trimIndent()
        assertEquals(listOf("12/09/2025 | SWIGGY BANGALORE | 450.50 | Dr", "13/09/2025 | ZOMATO | 100.00 | Dr"), rows(StatementTextParser.parse(text)))
    }

    @Test
    fun aHeaderLineRightAfterATransactionIsNotAppendedToIt() {
        val text = "12/09/2025 SWIGGY 450.50 Dr\nDate Narration Withdrawal Deposit Balance\n13/09/2025 ZOMATO 100.00 Dr"
        assertEquals(listOf("12/09/2025 | SWIGGY | 450.50 | Dr", "13/09/2025 | ZOMATO | 100.00 | Dr"), rows(StatementTextParser.parse(text)))
    }

    // ---- wrapped lines ----

    @Test
    fun aLineWithNoDateContinuesTheDescriptionAboveIt() {
        val text = """
            12/09/2025 UPI/DR/123456789/AMAZON PAY
            INDIA MUMBAI 1,299.00 Dr
            13/09/2025 NEFT TO RAVI KUMAR 500.00 Dr
            REF 998877 HDFC0001234
            14/09/2025 SWIGGY 90.00 Dr
        """.trimIndent()
        val r = StatementTextParser.parse(text)
        assertEquals(
            listOf(
                "12/09/2025 | UPI/DR/123456789/AMAZON PAY INDIA MUMBAI | 1299.00 | Dr", "13/09/2025 | NEFT TO RAVI KUMAR REF 998877 HDFC0001234 | 500.00 | Dr",
                "14/09/2025 | SWIGGY | 90.00 | Dr",
            ),
            rows(r),
        )
        assertEquals("12/09/2025 UPI/DR/123456789/AMAZON PAY\nINDIA MUMBAI 1,299.00 Dr", r.rawLines[1], "the raw text keeps both lines")
        assertEquals(3, r.table.lines[2], "the line number is the first line of the transaction in the text")
    }

    @Test
    fun aContinuationIsNotAppendedForeverOrFromFooterProse() {
        val text = """
            12/09/2025 SHOP 10.00 Dr
            MUMBAI
            MH
            IN
            FOURTH LINE
            This is a computer generated statement and does not require signature
        """.trimIndent()
        assertEquals(listOf("12/09/2025 | SHOP MUMBAI MH IN | 10.00 | Dr"), rows(StatementTextParser.parse(text)))
        assertEquals(listOf("12/09/2025 | SHOP | 10.00 | Dr"), rows(StatementTextParser.parse("12/09/2025 SHOP 10.00 Dr\nThis is a computer generated statement and does not require signature")))
    }

    @Test
    fun aLineWithAmountsAndNoDateAfterATransactionIsATotalNotAContinuation() {
        val r = StatementTextParser.parse("12/09/2025 SHOP 10.00 Dr\nSomething 99.00\n13/09/2025 OTHER 5.00 Dr")
        assertEquals(listOf("12/09/2025 | SHOP | 10.00 | Dr", "13/09/2025 | OTHER | 5.00 | Dr"), rows(r))
    }

    // ---- bank statements with a running balance ----

    private val bank = """
        Statement of Account
        Date Narration Chq/Ref Withdrawal Deposit Balance
        01/09/2025 Opening Balance 10,000.00
        02/09/2025 UPI-SWIGGY-123 450.00 9,550.00
        03/09/2025 SALARY ACME CORP 50,000.00 59,550.00
        04/09/2025 ATM WDL 5,000.00 54,550.00
    """.trimIndent()

    @Test
    fun aBankStatementWithoutMarksIsReadFromItsRunningBalance() {
        val r = StatementTextParser.parse(bank)
        assertEquals(Convention.BANK, r.convention)
        assertEquals(listOf("02/09/2025 | UPI-SWIGGY-123 | 450.00 | Dr", "03/09/2025 | SALARY ACME CORP | 50000.00 | Cr", "04/09/2025 | ATM WDL | 5000.00 | Dr"), rows(r))
        assertTrue(r.note.contains("balance"), r.note)
    }

    @Test
    fun withoutAnOpeningBalanceOnlyTheFirstLineIsUnknownAndIsReportedNotGuessed() {
        val text = bank.lines().filterNot { it.contains("Opening") }.joinToString("\n")
        val r = StatementTextParser.parse(text)
        assertEquals(listOf("02/09/2025 | UPI-SWIGGY-123 | 450.00 | ?", "03/09/2025 | SALARY ACME CORP | 50000.00 | Cr", "04/09/2025 | ATM WDL | 5000.00 | Dr"), rows(r))
        val p = import(text)
        assertEquals(2, p.txns.size)
        assertEquals(1, p.problems.size)
        assertTrue(p.problems.single().reason.contains("neither debit nor credit"), p.problems.single().reason)
    }

    @Test
    fun aBalanceThatDoesNotAddUpIsNotUsedToGuess() {
        val text = "Opening Balance 10,000.00\n02/09/2025 SHOP 450.00 9,000.00"
        assertEquals(listOf("02/09/2025 | SHOP | 450.00 | ?"), rows(StatementTextParser.parse(text)))
    }

    @Test
    fun debitAndCreditColumnsThenBalance() {
        val text = """
            Date Narration Withdrawal Deposit Balance
            02/09/2025 UPI-SWIGGY-123 450.00 0.00 9,550.00
            03/09/2025 SALARY ACME CORP 0.00 50,000.00 59,550.00
            04/09/2025 BOTH 10.00 10.00 59,550.00
        """.trimIndent()
        assertEquals(listOf("02/09/2025 | UPI-SWIGGY-123 | 450.00 | Dr", "03/09/2025 | SALARY ACME CORP | 50000.00 | Cr", "04/09/2025 | BOTH | 10.00 | ?"), rows(StatementTextParser.parse(text)))
    }

    @Test
    fun aMarkOnTheAmountBeatsTheBalanceAndAMarkOnTheBalanceIsItsSign() {
        val text = "Opening Balance 1,000.00 Cr\n02/09/2025 SHOP 1,500.00 Dr 500.00 Dr\n03/09/2025 SALARY 2,000.00 Cr 1,500.00 Cr"
        assertEquals(listOf("02/09/2025 | SHOP | 1500.00 | Dr", "03/09/2025 | SALARY | 2000.00 | Cr"), rows(StatementTextParser.parse(text)))
        // an overdrawn account: the balance is below zero, and the next line is read against it
        val over = "Opening Balance 1,000.00 Cr\n02/09/2025 SHOP 1,500.00 500.00 Dr\n03/09/2025 SALARY 2,000.00 1,500.00 Cr"
        assertEquals(listOf("02/09/2025 | SHOP | 1500.00 | Dr", "03/09/2025 | SALARY | 2000.00 | Cr"), rows(StatementTextParser.parse(over)))
    }

    @Test
    fun valueDateAtTheEndOfTheDescriptionIsDropped() {
        val r = StatementTextParser.parse("01/09/25 UPI-AMAZON 0000123456789 02/09/25 500.00 Dr")
        assertEquals(listOf("01/09/25 | UPI-AMAZON 0000123456789 | 500.00 | Dr"), rows(r))
    }

    // ---- through the importer ----

    @Test
    fun theTableReachesTheImporterWithTheRawLineAsItsSourceText() {
        val p = import(card)
        assertEquals(emptyList(), p.problems)
        assertEquals(3, p.txns.size)
        assertEquals(StatementTxn(p.txns[0].row, d(2025, 9, 12), Flow.SPENDING, 129900, "AMAZON PAY INDIA MUMBAI", "12/09/2025 AMAZON PAY INDIA MUMBAI 1,299.00"), p.txns[0])
        assertEquals("15/09/2025 AMAZON REFUND MUMBAI 799.00 Cr", p.txns[2].sourceText)
        assertEquals(Flow.INCOME, p.txns[2].flow)
        assertEquals(7, p.txns[0].row, "reported by the line in the text, which is where the person can find it")
    }

    @Test
    fun csvRowsKeepTheirCellsAsSourceTextAndAnEmptyTrailingCellIsLeftOff() {
        val rows = listOf(listOf("Date", "Narration", "Debit", "Credit"), listOf("01/10/2026", "UPI-SWIGGY", "250.00", ""))
        val m = StatementDetector.detect(rows)
        assertEquals("01/10/2026, UPI-SWIGGY, 250.00", StatementParser.parse(rows, m).txns.single().sourceText)
    }

    @Test
    fun aDatedLineWithNoAmountIsCountedAsSkippedAndTextWithNothingGivesNoRows() {
        val p = import("Statement of Account\n01/09/2025 SOMETHING WITHOUT AN AMOUNT\n02/09/2025 SHOP 10.00 Dr")
        assertEquals(1, p.txns.size)
        assertEquals(1, p.ignoredRows)
        assertEquals(0, StatementTextParser.parse("Hello\nThis is not a statement\n12345").rowCount)
        assertEquals(0, StatementTextParser.parse("").rowCount)
    }

    @Test
    fun theAmountIsPlainDigitsSoTheImporterNeverMeetsIndianGroupingOrLakhs() {
        val p = import("Statement of Account\n01/09/2025 SHOP 12,34,567.89 Dr")
        assertEquals(123_456_789L, p.txns.single().minor)
    }

    @Test
    fun textMixedWithOcrNoiseStillGivesTheTransactionLines() {
        val text = """
            ~~~ HDFC BANK ~~~
            Date   |   Narration   |   Amount
            12/09/2025 | AMAZON PAY INDIA | 1,299.00 | Dr
            ----------------------------------
            13/09/2025   SWIGGY      450.50    Dr
        """.trimIndent()
        assertEquals(listOf("12/09/2025 | AMAZON PAY INDIA | 1299.00 | Dr", "13/09/2025 | SWIGGY | 450.50 | Dr"), rows(StatementTextParser.parse(text)))
    }
}

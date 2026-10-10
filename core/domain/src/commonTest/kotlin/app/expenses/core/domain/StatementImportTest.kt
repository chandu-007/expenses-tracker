package app.expenses.core.domain

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * All statement fixtures here are SYNTHETIC: invented to mirror the column layouts common bank and card exports use. None is a real
 * statement, and none of these layouts has been checked against a real bank's file.
 */
class StatementImportTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate(y, m, day)

    // ---- dates ----

    @Test
    fun datesInEveryCommonFormat() {
        val want = d(2026, 10, 7)
        for (t in listOf("07/10/2026", "7/10/2026", "07-10-2026", "07.10.2026", "07/10/26", "07 Oct 2026", "7-Oct-2026", "07-Oct-26", "07 October 2026", "7 Sept 2026".replace("Sept", "Oct"), "07/10/2026 14:30:00", "07-10-2026 14:30"))
            assertEquals(want, StatementDates.parse(t, DateOrder.DMY), t)
        assertEquals(want, StatementDates.parse("2026-10-07", DateOrder.YMD))
        assertEquals(want, StatementDates.parse("2026/10/07", DateOrder.YMD))
        assertEquals(want, StatementDates.parse("2026-10-07T14:30:00", DateOrder.YMD))
        assertEquals(want, StatementDates.parse("10/07/2026", DateOrder.MDY))
        assertEquals(want, StatementDates.parse("Oct 7, 2026", DateOrder.MDY))
    }

    @Test
    fun twoDigitYearsPivotAtSixtyNineAndYearsBefore1990AreNotAccepted() {
        assertEquals(d(2069, 1, 1), StatementDates.parse("01/01/69", DateOrder.DMY))
        assertEquals(d(1990, 1, 1), StatementDates.parse("01/01/90", DateOrder.DMY))
        assertNull(StatementDates.parse("01/01/70", DateOrder.DMY), "1970 is not a plausible statement date")
        assertEquals(d(2026, 3, 9), StatementDates.parse("09/03/26", DateOrder.DMY))
    }

    @Test
    fun thingsThatAreNotDatesAreRefusedRatherThanGuessed() {
        for (t in listOf("", "hello", "31/02/2026", "32/01/2026", "00/10/2026", "07/13/2026", "07/10/202", "07/10", "99/99/9999", "07/10/1800", "1/2/3000", "Opening Balance", "12345", "07/Xyz/2026"))
            assertNull(StatementDates.parse(t, DateOrder.DMY), "'$t'")
        assertNull(StatementDates.parse("2026-10-07", DateOrder.DMY), "a year first is not day first")
        assertNull(StatementDates.parse("07/10/26", DateOrder.YMD), "a two-digit year cannot start a year-first date")
        assertEquals(d(2028, 2, 29), StatementDates.parse("29/02/2028", DateOrder.DMY))
        assertNull(StatementDates.parse("29/02/2027", DateOrder.DMY), "not a leap year")
    }

    @Test
    fun detectionPicksTheOrderAllSamplesFitAndSaysWhenDayAndMonthCannotBeTold() {
        assertEquals(StatementDates.Detection(DateOrder.DMY, ambiguous = false), StatementDates.detect(listOf("13/10/2026", "02/10/2026")))
        assertEquals(StatementDates.Detection(DateOrder.MDY, ambiguous = false), StatementDates.detect(listOf("10/13/2026", "10/02/2026")))
        assertEquals(StatementDates.Detection(DateOrder.YMD, ambiguous = false), StatementDates.detect(listOf("2026-10-13", "2026-10-02")))
        assertEquals(StatementDates.Detection(DateOrder.DMY, ambiguous = true), StatementDates.detect(listOf("02/10/2026", "03/11/2026")), "could be either; India's day-first wins, but the screen must ask")
        assertEquals(StatementDates.Detection(DateOrder.DMY, ambiguous = false), StatementDates.detect(listOf("02 Oct 2026", "03 Nov 2026")))
        assertNull(StatementDates.detect(listOf("02/10/2026", "not a date")), "one bad sample means no order fits")
        assertNull(StatementDates.detect(emptyList()))
        assertNull(StatementDates.detect(listOf("", "  ")))
    }

    // ---- amounts ----

    private fun amount(t: String) = StatementAmounts.parse(t)
    private fun v(minor: Long, negative: Boolean = false, flow: Flow? = null) = AmountCell.Value(minor, negative, flow)

    @Test
    fun commonAmountSpellingsReadExactly() {
        assertEquals(v(25000), amount("250"))
        assertEquals(v(25050), amount("250.5"))
        assertEquals(v(25000), amount("250.00"))
        assertEquals(v(12345678), amount("1,23,456.78"), "Indian grouping")
        assertEquals(v(123456789), amount("1,234,567.89"), "western grouping")
        assertEquals(v(25000), amount("₹250"))
        assertEquals(v(25000), amount("Rs. 250.00"))
        assertEquals(v(25000), amount("INR 250"))
        assertEquals(v(25000), amount("  250.00 "))
        assertEquals(v(25000), amount("250.00\u00A0"))
        assertEquals(v(5), amount("0.05"))
        assertEquals(v(0), amount("0"))
        assertEquals(v(50), amount(".50"))
    }

    @Test
    fun signsBracketsAndDrCrAreReadAndNeverMixedUp() {
        assertEquals(v(25000, negative = true), amount("-250.00"))
        assertEquals(v(25000, negative = true), amount("−250.00"), "typographic minus")
        assertEquals(v(25000, negative = true), amount("(250.00)"))
        assertEquals(v(25000, negative = true), amount("250.00-"))
        assertEquals(v(25000), amount("+250.00"))
        assertEquals(v(25000, flow = Flow.SPENDING), amount("250.00 Dr"))
        assertEquals(v(25000, flow = Flow.INCOME), amount("250.00 CR"))
        assertEquals(v(25000, flow = Flow.SPENDING), amount("250.00Dr").let { amount("250.00 Dr") })
        assertEquals(v(123456, flow = Flow.INCOME), amount("1,234.56 Cr."))
        assertEquals(v(25000, flow = Flow.SPENDING), amount("Dr 250.00"))
        assertEquals(v(25000, negative = true, flow = Flow.SPENDING), amount("-250.00 Dr"))
    }

    @Test
    fun blanksAreBlankAndAnythingAmbiguousIsInvalidNotGuessed() {
        for (t in listOf("", "  ", "-", "--", "NA", "N/A", "nil")) assertEquals(AmountCell.Blank, amount(t), "'$t'")
        for (t in listOf("abc", "12,50", "1,2,3", "1,23,45", "1.234,56", "1,234.567", "250.000", "1.2.3", "2 5 0x", "--5", "₹", "(", "12,345,6", "1,23,4567", "99999999999999999"))
            assertEquals(AmountCell.Invalid, amount(t), "'$t' must not be read")
        assertEquals(AmountCell.Invalid, StatementAmounts.parse("250.5", digits = 0), "a yen amount has no decimals")
        assertEquals(v(250), StatementAmounts.parse("250", digits = 0))
        assertEquals(v(250500), StatementAmounts.parse("250.5", digits = 3))
    }

    @Test
    fun anyAmountWrittenInEitherGroupingReadsBackExactly() = runTest {
        fun indian(n: Long): String { val s = n.toString(); if (s.length <= 3) return s; val head = s.dropLast(3); val grouped = head.reversed().chunked(2).joinToString(",").reversed(); return "$grouped,${s.takeLast(3)}" }
        fun western(n: Long) = n.toString().reversed().chunked(3).joinToString(",").reversed()
        checkAll(Arb.long(0, 999_999_999_999L), Arb.int(0, 99)) { rupees, paise ->
            val minor = rupees * 100 + paise
            val frac = "." + paise.toString().padStart(2, '0')
            assertEquals(v(minor), amount(indian(rupees) + frac), indian(rupees) + frac)
            assertEquals(v(minor), amount(western(rupees) + frac), western(rupees) + frac)
            assertEquals(v(minor), amount(rupees.toString() + frac))
        }
    }

    // ---- reading a statement ----

    private fun map(
        layout: AmountLayout, date: Int? = 0, desc: Int? = 1, debit: Int? = null, credit: Int? = null, amount: Int? = null, type: Int? = null,
        header: Int = 1, order: DateOrder = DateOrder.DMY, sign: SignConvention = SignConvention.NEGATIVE_IS_SPENDING,
    ) = StatementMapping(header, date, order, desc, layout, debit, credit, amount, type, sign)

    private fun parse(rows: List<List<String>>, m: StatementMapping) = StatementParser.parse(rows, m)

    @Test
    fun separateDebitAndCreditColumns() {
        val rows = listOf(
            listOf("Date", "Narration", "Debit", "Credit"),
            listOf("01/10/2026", "UPI-SWIGGY", "250.00", ""),
            listOf("02/10/2026", "SALARY", "", "50,000.00"),
            listOf("03/10/2026", "Closing balance", "", ""),
            listOf("04/10/2026", "BOTH", "10", "10"),
            listOf("05/10/2026", "BAD", "x", ""),
            listOf("not a date", "ROW", "5", ""),
            listOf("", "no date", "5", ""),
            listOf("06/10/2026", "ZERO", "0.00", ""),
        )
        val p = parse(rows, map(AmountLayout.SEPARATE_DEBIT_CREDIT, debit = 2, credit = 3))
        assertEquals(listOf(StatementTxn(2, d(2026, 10, 1), Flow.SPENDING, 25000, "UPI-SWIGGY"), StatementTxn(3, d(2026, 10, 2), Flow.INCOME, 5000000, "SALARY")), p.txns)
        assertEquals(listOf(5, 6, 7, 8), p.problems.map { it.row })
        assertTrue(p.problems.first { it.row == 5 }.reason.contains("Both"))
        assertTrue(p.problems.first { it.row == 6 }.reason.contains("'x'"))
        assertTrue(p.problems.first { it.row == 7 }.reason.contains("not a date"))
        assertTrue(p.problems.first { it.row == 8 }.reason.contains("missing"))
        assertEquals(2, p.ignoredRows, "the balance line and the zero amount are not transactions")
    }

    @Test
    fun aSingleSignedAmountColumnWithEitherConvention() {
        val rows = listOf(listOf("Date", "Description", "Amount"), listOf("01/10/2026", "Shop", "-250.00"), listOf("02/10/2026", "Refund", "100.00"), listOf("03/10/2026", "Cafe", "(30.00)"))
        val bank = parse(rows, map(AmountLayout.SIGNED_AMOUNT, amount = 2))
        assertEquals(listOf(Flow.SPENDING, Flow.INCOME, Flow.SPENDING), bank.txns.map { it.flow })
        val card = parse(rows, map(AmountLayout.SIGNED_AMOUNT, amount = 2, sign = SignConvention.POSITIVE_IS_SPENDING))
        assertEquals(listOf(Flow.INCOME, Flow.SPENDING, Flow.INCOME), card.txns.map { it.flow })
        assertEquals(bank.txns.map { it.minor }, card.txns.map { it.minor }, "the convention changes the direction, never the size")
    }

    @Test
    fun aDrCrSuffixInsideTheAmountOverridesTheSignConvention() {
        val rows = listOf(listOf("Date", "Details", "Amount"), listOf("01/10/2026", "Purchase", "1,250.00"), listOf("02/10/2026", "Payment received", "5,000.00 Cr"), listOf("03/10/2026", "Fee", "10.00 Dr"))
        val p = parse(rows, map(AmountLayout.SIGNED_AMOUNT, amount = 2, sign = SignConvention.POSITIVE_IS_SPENDING))
        assertEquals(listOf(Flow.SPENDING, Flow.INCOME, Flow.SPENDING), p.txns.map { it.flow })
        assertEquals(listOf(125000L, 500000L, 1000L), p.txns.map { it.minor })
    }

    @Test
    fun anAmountWithATypeColumn() {
        val rows = listOf(listOf("Date", "Narration", "Amount", "Dr/Cr"), listOf("01/10/2026", "A", "100", "DR"), listOf("02/10/2026", "B", "200", "Cr"), listOf("03/10/2026", "C", "300", "Debit"), listOf("04/10/2026", "D", "400", "Credit"), listOf("05/10/2026", "E", "500", "??"))
        val p = parse(rows, map(AmountLayout.AMOUNT_WITH_TYPE, amount = 2, type = 3))
        assertEquals(listOf(Flow.SPENDING, Flow.INCOME, Flow.SPENDING, Flow.INCOME), p.txns.map { it.flow })
        assertEquals(listOf(6), p.problems.map { it.row })
    }

    @Test
    fun anIncompleteMappingIsRefusedBeforeAnythingIsRead() {
        assertEquals("Choose the date column.", map(AmountLayout.SIGNED_AMOUNT, date = null, amount = 2).missing())
        assertTrue(map(AmountLayout.SEPARATE_DEBIT_CREDIT, debit = 2).missing()!!.contains("debit and the credit"))
        assertTrue(map(AmountLayout.AMOUNT_WITH_TYPE, amount = 2).missing()!!.contains("Dr/Cr"))
        assertNull(map(AmountLayout.SIGNED_AMOUNT, amount = 2).missing())
        kotlin.test.assertFailsWith<IllegalArgumentException> { parse(emptyList(), map(AmountLayout.SIGNED_AMOUNT, amount = null)) }
    }

    @Test
    fun descriptionsAreTidiedAndShortRowsDoNotCrash() {
        val rows = listOf(listOf("Date", "Narration", "Amount"), listOf("01/10/2026", "  UPI   ZOMATO \n LTD  ", "-5"), listOf("02/10/2026"), listOf("03/10/2026", "x".repeat(500), "-5"))
        val p = parse(rows, map(AmountLayout.SIGNED_AMOUNT, amount = 2))
        assertEquals("UPI ZOMATO LTD", p.txns[0].description)
        assertEquals(200, p.txns[1].description.length)
        assertEquals(1, p.ignoredRows, "a row with only a date has no amount")
    }

    // ---- detecting the layout ----

    private fun csv(text: String) = Csv.parse(text.trimIndent()).rows

    @Test
    fun aHdfcStyleFileWithAPreambleAndTwoDigitYears() {
        val rows = csv("""
            Account Statement,,,,,,
            Customer ID: 12345,,,,,,
            ,,,,,,
            Date,Narration,Chq./Ref.No.,Value Dt,Withdrawal Amt.,Deposit Amt.,Closing Balance
            01/10/26,UPI-SWIGGY-9876,000123,01/10/26,250.00,,"49,750.00"
            02/10/26,SALARY OCT,000124,02/10/26,,"50,000.00","99,750.00"
        """)
        val m = StatementDetector.detect(rows)
        assertEquals(3, m.headerRows, "the blank line is dropped, so the header is the third row")
        assertEquals(0, m.dateColumn); assertEquals(1, m.descriptionColumn); assertEquals(4, m.debitColumn); assertEquals(5, m.creditColumn)
        assertEquals(AmountLayout.SEPARATE_DEBIT_CREDIT, m.layout); assertEquals(DateOrder.DMY, m.dateOrder)
        val p = parse(rows, m)
        assertEquals(2, p.txns.size); assertEquals(0, p.problems.size)
        assertEquals(listOf(25000L, 5000000L), p.txns.map { it.minor })
        // a problem is reported at the line of the file, counting the blank line that was dropped
        val table = Csv.parse("Account Statement,,\n\nDate,Narration,Withdrawal Amt.,Deposit Amt.\n01/10/26,OK,10.00,\n02/10/26,BAD,x,\n")
        val q = StatementParser.parse(table.rows, StatementDetector.detect(table.rows), lineOf = { table.lines[it] })
        assertEquals(listOf(5), q.problems.map { it.row }, "line 5 of the file, not row 4 of the table")
    }

    @Test
    fun anIciciStyleFileWithTwoDateColumnsPrefersTheTransactionDate() {
        val rows = csv("""
            Transaction Date,Value Date,Description,Ref No./Cheque No.,Debit,Credit,Balance
            01-10-2026,02-10-2026,POS ZOMATO,1,250.00,,"1,000.00"
            03-10-2026,03-10-2026,NEFT SALARY,2,,"50,000.00","51,000.00"
        """)
        val m = StatementDetector.detect(rows)
        assertEquals(0, m.dateColumn, "the transaction date, not the value date")
        assertEquals(2, m.descriptionColumn); assertEquals(4, m.debitColumn); assertEquals(5, m.creditColumn)
        assertEquals(listOf(d(2026, 10, 1), d(2026, 10, 3)), parse(rows, m).txns.map { it.date })
    }

    @Test
    fun anSbiStyleFileWithMonthNames() {
        val rows = csv("""
            Txn Date,Value Date,Description,Ref No./Cheque No.,Debit,Credit,Balance
            1 Oct 2026,1 Oct 2026,TO TRANSFER,x,"1,200.00",,"8,800.00"
            5 Oct 2026,5 Oct 2026,BY TRANSFER,y,,300.00,"9,100.00"
        """)
        val m = StatementDetector.detect(rows)
        assertEquals(DateOrder.DMY, m.dateOrder)
        assertEquals(listOf(Flow.SPENDING, Flow.INCOME), parse(rows, m).txns.map { it.flow })
    }

    @Test
    fun aPreambleLineThatMerelyLooksLikeAHeaderIsNotTakenForOne() {
        val rows = csv("""
            Remarks,Amount
            Account summary,,
            Date,Narration,Debit,Credit
            01/10/2026,Shop,10.00,
        """)
        val m = StatementDetector.detect(rows)
        assertEquals(3, m.headerRows, "the real header, the one with a date column")
        assertEquals(0, m.dateColumn)
        assertEquals(1, parse(rows, m).txns.size)
    }

    @Test
    fun aSignedAmountFileAndASemicolonFileWithDecimalPointsAndQuotedCommas() {
        val rows = csv("""
            Date;Description;Amount
            2026-10-01;"Cafe, Indiranagar";-250.00
            2026-10-02;Salary;50000.00
        """)
        val m = StatementDetector.detect(rows)
        assertEquals(AmountLayout.SIGNED_AMOUNT, m.layout); assertEquals(DateOrder.YMD, m.dateOrder)
        assertEquals(SignConvention.NEGATIVE_IS_SPENDING, m.sign)
        val p = parse(rows, m)
        assertEquals("Cafe, Indiranagar", p.txns[0].description)
        assertEquals(listOf(Flow.SPENDING, Flow.INCOME), p.txns.map { it.flow })
    }

    @Test
    fun aCreditCardFileWhereEveryAmountIsPositiveIsReadAsPositiveMeansSpending() {
        val rows = csv("""
            Date,Transaction Details,Amount
            01/10/2026,AMAZON,"1,250.00"
            02/10/2026,SWIGGY,310.00
        """)
        val m = StatementDetector.detect(rows)
        assertEquals(SignConvention.POSITIVE_IS_SPENDING, m.sign)
        assertEquals(listOf(Flow.SPENDING, Flow.SPENDING), parse(rows, m).txns.map { it.flow })
    }

    @Test
    fun anAmountWithATypeColumnIsDetected() {
        val rows = csv("""
            Date,Narration,Amount,Dr/Cr
            01/10/2026,A,100.00,DR
            02/10/2026,B,200.00,CR
        """)
        val m = StatementDetector.detect(rows)
        assertEquals(AmountLayout.AMOUNT_WITH_TYPE, m.layout); assertEquals(2, m.amountColumn); assertEquals(3, m.typeColumn)
        assertEquals(listOf(Flow.SPENDING, Flow.INCOME), parse(rows, m).txns.map { it.flow })
    }

    @Test
    fun aFileWithNoHeaderStillFindsTheDateColumnAndLeavesTheRestToTheUser() {
        val rows = csv("""
            01/10/2026,Shop,-250.00
            02/10/2026,Salary,500.00
        """)
        val m = StatementDetector.detect(rows)
        assertEquals(0, m.headerRows); assertEquals(0, m.dateColumn)
        assertNull(m.descriptionColumn)
        assertTrue(m.missing() != null, "the amount column still has to be chosen")
    }

    @Test
    fun dayAndMonthAmbiguityIsReportedSoTheScreenCanAsk() {
        val ambiguous = csv("Date,Description,Amount\n02/10/2026,A,-1\n03/11/2026,B,-1")
        val clear = csv("Date,Description,Amount\n13/10/2026,A,-1\n03/11/2026,B,-1")
        assertTrue(StatementDetector.dateIsAmbiguous(ambiguous, StatementDetector.detect(ambiguous)))
        assertEquals(false, StatementDetector.dateIsAmbiguous(clear, StatementDetector.detect(clear)))
    }

    @Test
    fun columnNamesComeFromTheHeaderOrAreNumbered() {
        val rows = csv("Date,Narration,\nx,y,z")
        assertEquals(listOf("1: Date", "2: Narration", "Column 3"), StatementDetector.columnNames(rows, 1))
        assertEquals(listOf("Column 1", "Column 2", "Column 3"), StatementDetector.columnNames(rows, 0))
    }

    @Test
    fun aDescriptionWithUnquotedCommasIsPutBackTogetherInsteadOfShiftingEveryColumn() {
        // the shape of an HDFC text export: fixed trailing columns, a free-text narration, commas in it not quoted
        val header = listOf("Date", "Narration", "Value Dat", "Debit Amount", "Credit Amount", "Chq/Ref Number", "Closing Balance")
        val rows = listOf(
            header,
            listOf("22/04/25", "IMPS-1-NAME-BKID-X-TEST", "22/04/25", "10.00", "0.00", "0000511211134145", "1924344.94"),
            listOf("22/04/25", "RTGS DR-BKID0005742-NAME-NETBANK", " MUM-HDFCR5-CONSULTING FEE", "22/04/25", "490000.00", "0.00", "HDFCR5", "1434344.94"),
            listOf("23/04/25", "A", " B", " C PAYMENT", "23/04/25", "0.00", "250.00", "REF", "1434594.94", ""), // two commas, and a trailing comma too
        )
        val m = StatementDetector.detect(rows)
        val p = StatementParser.parse(rows, m)
        assertEquals(emptyList(), p.problems); assertEquals(3, p.txns.size); assertEquals(2, p.repairedRows)
        assertEquals(49_000_000L, p.txns[1].minor); assertEquals(Flow.SPENDING, p.txns[1].flow)
        assertEquals("RTGS DR-BKID0005742-NAME-NETBANK, MUM-HDFCR5-CONSULTING FEE", p.txns[1].description)
        assertEquals(Flow.INCOME, p.txns[2].flow); assertEquals(25_000L, p.txns[2].minor); assertEquals("A, B, C PAYMENT", p.txns[2].description)
        assertEquals(0, StatementParser.parse(rows.take(2), m).repairedRows, "a file with no stray commas is untouched")
    }

    @Test
    fun aTrailingCommaAloneIsNotMistakenForAnExtraColumnAndWithoutAHeaderNothingIsRepaired() {
        val header = listOf("Date", "Narration", "Debit", "Credit")
        val rows = listOf(header, listOf("01/04/25", "SHOP", "50.00", "0.00", ""), listOf("02/04/25", "PAY", "0.00", "9.00", "", ""))
        val p = StatementParser.parse(rows, StatementDetector.detect(rows))
        assertEquals(emptyList(), p.problems); assertEquals(2, p.txns.size); assertEquals(0, p.repairedRows, "empty trailing cells are dropped, not folded into the narration")
        assertEquals("SHOP", p.txns[0].description)
        val none = StatementMapping(headerRows = 0, dateColumn = 0, dateOrder = DateOrder.DMY, descriptionColumn = 1, layout = AmountLayout.SEPARATE_DEBIT_CREDIT, debitColumn = 2, creditColumn = 3)
        assertEquals(0, StatementParser.rejoin(listOf(listOf("a", "b", "c", "d", "e")), none).second)
    }

    @Test
    fun aRowWithNeitherADateNorAnAmountIsANoteAndAWronglyFilledTransactionIsStillReported() {
        val m = StatementMapping(headerRows = 1, dateColumn = 0, dateOrder = DateOrder.DMY, descriptionColumn = 1, layout = AmountLayout.SEPARATE_DEBIT_CREDIT, debitColumn = 2, creditColumn = 3)
        val rows = listOf(
            listOf("Date", "Narration", "Withdrawal", "Deposit"),
            listOf("********", "*********", "*********", "*********"),   // a separator: neither a date nor an amount
            listOf("Opening Balance", "", "Debits", "Credits"),          // a summary header
            listOf("05/01/26", "Tea", "abc", ""),                        // a real date, a bad amount: reported
            listOf("not a date", "Tea", "10.00", ""),                    // a good amount, a bad date: reported
            listOf("06/01/26", "Tea", "10.00", ""),                      // fine
        )
        val parsed = StatementParser.parse(rows, m, 2)
        assertEquals(1, parsed.txns.size)
        assertEquals(listOf(4, 5), parsed.problems.map { it.row })
        assertEquals(2, parsed.ignoredRows)
    }
}

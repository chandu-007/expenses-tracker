package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Statement text is SYNTHETIC with invented numbers, laid out the way the layouts were described; not checked against a real statement. */
class StatementSummaryTest {
    private val savings = """
        STATEMENT SUMMARY :-
        Opening Balance  Dr Count  Cr Count  Debits  Credits  Closing Bal
        100,000.00  43  12  565,536.57  855,479.90  389,943.33
    """.trimIndent()

    private val card = """
        PAYMENTS/CREDITS PURCHASES/DEBIT
        C38,012.00 + C39,883.60
    """.trimIndent()

    @Test fun savingsSummary() {
        assertEquals(StatementSummary.Summary(56553657, 85547990, 43, 12), StatementSummary.find(savings))
    }

    @Test fun cardSummary() {
        assertEquals(StatementSummary.Summary(3988360, 3801200), StatementSummary.find(card))
    }

    @Test fun totalLines() {
        assertEquals(StatementSummary.Summary(123400, 500000), StatementSummary.find("Total Debits: Rs. 1,234.00\nTotal Credits 5,000.00"))
        assertNull(StatementSummary.find("Total Debits\nNarration Date"))
    }

    @Test fun noSummaryOrNumbersThatDoNotLineUp() {
        assertNull(StatementSummary.find("12/09/2025 SWIGGY 450.00 Dr"))
        assertNull(StatementSummary.find("Opening Balance  Dr Count  Cr Count  Debits  Credits  Closing Bal\n1.00  2"))
    }

    private val summary = StatementSummary.Summary(56553657, 85547990, 43, 12)

    @Test fun equalTotalsSayNothing() {
        assertNull(StatementSummary.check(summary, 56553657, 43, 85547990, 12))
        assertNull(StatementSummary.check(null, 1, 1, 1, 1))
    }

    @Test fun differentTotalsSayWhat() {
        val note = StatementSummary.check(summary, 55000000, 41, 85547990, 12)
        assertEquals("The statement says 43 debits totalling ₹5,65,536.57 but 41 were found (₹5,50,000.00). Some lines may be missing: check the preview.", note)
    }

    @Test fun totalsWithoutCounts() {
        val note = StatementSummary.check(StatementSummary.Summary(1000, null), 900, 1, 0, 0)
        assertNotNull(note)
        assertEquals("The statement says debits total ₹10.00 but the lines found add up to ₹9.00. Some lines may be missing: check the preview.", note)
    }

    @Test fun parserChecksAgainstItsOwnText() {
        val text = "HDFC Bank Credit Card Statement\n$card\nDate Transaction Description Amount\n" +
            "12/09/2025 SHOP ONE MUMBAI 39,883.60\n14/09/2025 PAYMENT RECEIVED - THANK YOU 38,012.00 Cr\n"
        val r = StatementTextParser.parse(text, StatementTextParser.Convention.CARD)
        assertEquals(3801200, r.cardPaymentMinor)
        val txns = StatementParser.parse(r.table.rows, StatementDetector.detect(r.table.rows), 2).txns
        assertNull(r.check(txns), "purchases match and the skipped payment makes up the credits")
        assertNotNull(r.check(txns.drop(1)))
    }
}

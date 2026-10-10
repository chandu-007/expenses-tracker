package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The fixtures hold made-up data only: `big.xls` has a bank-style preamble, a header, a row of asterisks, 150 rows, a summary block and two rows holding only spaces; `mini.xls` is a two-row sheet in the compound file's mini stream. */
class XlsReaderTest {
    private fun bytes(name: String) = XlsReaderTest::class.java.getResourceAsStream("/xls/$name")!!.readBytes()
    private val big by lazy { XlsReader.read(bytes("big.xls")) }

    @Test
    fun aBankStatementWorkbookKeepsItsPreambleHeaderAndEveryRowInOrder() {
        assertEquals("Demo Bank Ltd.", big[0][0])
        assertEquals(listOf("Date", "Narration", "Chq./Ref.No.", "Value Dt", "Withdrawal Amt.", "Deposit Amt.", "Closing Balance"), big[7].take(7))
        assertEquals("********", big[8][0])
        assertEquals(9 + 150 + 3, big.size, "preamble and header, 150 transactions, the summary and footer rows")
        assertEquals("** End Of Statement **", big.last()[0])
    }

    @Test
    fun textAcrossTheSharedStringTableContinuationKeepsUnicodeAndStaysInItsOwnRow() {
        // 150 long strings do not fit one record: the table carries on in CONTINUE records, where a string can be cut anywhere
        for (i in 0 until 150) assertTrue(big[9 + i][1].startsWith("UPI-MERCHANT ${i.toString().padStart(3, '0')} ₹ é తెలుగు payment ref "), "row $i: ${big[9 + i][1]}")
        assertTrue(big[9 + 149][1].endsWith("x".repeat(40)))
    }

    @Test
    fun numbersComeOutAsPlainNumbersAndRealDateCellsAsIsoDates() {
        val spend = (0 until 150 step 2).map { big[9 + it][4] }; val income = (1 until 150 step 2).map { big[9 + it][5] }
        assertEquals(listOf("1234.5", "12345678.9", "250000"), spend.take(3), "no exponent form for a large amount, no .0 on a whole one")
        assertEquals(listOf("100", "0.05", "99.99"), income.take(3))
        assertEquals("1000", big[9][6]); assertEquals("1149", big[9 + 149][6])
        assertEquals(listOf("-250", "-250.75"), listOf(big[9 + 3][6], big[9 + 4][6]), "a negative whole number (stored compactly) keeps its sign")
        assertEquals("2026-01-01", big[9][7], "a cell with a date format is a date, not 46023")
        assertEquals("", big[9][5], "the other side of a debit is blank")
    }

    @Test
    fun theDetectorAndParserImportTheBankLayoutWithOnlyTheTransactionRows() {
        val mapping = StatementDetector.detect(big)
        assertEquals(null, mapping.missing())
        val parsed = StatementParser.parse(big, mapping, 2)
        assertEquals(150, parsed.txns.size)
        assertEquals(emptyList(), parsed.problems, "the asterisk row and the summary block are not transactions and not problems")
        assertEquals(31_492_283_500L, parsed.txns.filter { it.flow == Flow.SPENDING }.sumOf { it.minor }, "withdrawals, to the paisa")
        assertEquals(500_100L, parsed.txns.filter { it.flow == Flow.INCOME }.sumOf { it.minor }, "deposits, to the paisa")
    }

    @Test
    fun aSmallWorkbookStoredInTheMiniStreamReadsToo() {
        assertEquals(listOf(listOf("Date", "Narration", "Withdrawal Amt.", "Deposit Amt."), listOf("05/01/26", "Chai", "45.5", ""), listOf("06/01/26", "Salary ₹", "", "50000")), XlsReader.read(bytes("mini.xls")))
    }

    @Test
    fun otherFilesAreRefusedWithAReasonAndDamageNeverEscapesAsACrash() {
        assertFalse(XlsReader.looksLikeXls("Date,Amount\n1,2".encodeToByteArray()))
        assertTrue(XlsReader.looksLikeXls(bytes("mini.xls")))
        assertEquals("This is not an Excel (.xls) file.", assertFailsWith<XlsReader.Unreadable> { XlsReader.read("Date,Amount".encodeToByteArray()) }.message)
        val good = bytes("big.xls")
        for (cut in listOf(600, 2_000, 9_000, good.size / 2)) {
            val e = runCatching { XlsReader.read(good.copyOf(cut)) }.exceptionOrNull()
            assertTrue(e == null || e is XlsReader.Unreadable, "a file cut at $cut bytes must be refused or read, not crash with $e")
        }
        val scrambled = good.copyOf().also { for (i in 600 until it.size step 7) it[i] = (it[i] + 13).toByte() }
        val e = runCatching { XlsReader.read(scrambled) }.exceptionOrNull()
        assertTrue(e == null || e is XlsReader.Unreadable, "scrambled contents must not crash with $e")
    }

    @Test
    fun aWebPageOrXmlSavedWithAnExcelNameIsRecognisedAndPlainCsvIsNot() {
        assertTrue(XlsReader.looksLikeWebPageOrXml("<html><table><tr><td>Date</td></tr></table></html>".encodeToByteArray()))
        assertTrue(XlsReader.looksLikeWebPageOrXml("\n  <?xml version=\"1.0\"?><Workbook/>".encodeToByteArray()))
        assertTrue(XlsReader.looksLikeWebPageOrXml(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "<table>".encodeToByteArray()), "with a byte order mark")
        assertFalse(XlsReader.looksLikeWebPageOrXml("Date,Narration,Amount\n01/01/26,<b>x</b>,5".encodeToByteArray()), "a CSV that mentions a tag later is still a CSV")
        assertFalse(XlsReader.looksLikeWebPageOrXml(ByteArray(0)))
    }
}

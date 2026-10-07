package app.expenses.core.domain

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CsvTest {
    private fun rows(text: String, d: Char? = null) = Csv.parse(text, d).rows

    @Test
    fun plainRowsWithAnyLineEnding() {
        val want = listOf(listOf("a", "b", "c"), listOf("1", "2", "3"))
        assertEquals(want, rows("a,b,c\r\n1,2,3\r\n"))
        assertEquals(want, rows("a,b,c\n1,2,3"))
        assertEquals(want, rows("a,b,c\r1,2,3\r"))
    }

    @Test
    fun quotedFieldsKeepCommasQuotesAndLineBreaks() {
        val t = Csv.parse("Date,Narration,Amount\n01/10/2026,\"Cafe, \"\"The\"\" Best\",250.00\n02/10/2026,\"line1\nline2\",9\n")
        assertEquals(listOf("01/10/2026", "Cafe, \"The\" Best", "250.00"), t.rows[1])
        assertEquals(listOf("02/10/2026", "line1\nline2", "9"), t.rows[2])
        assertFalse(t.damaged)
    }

    @Test
    fun aByteOrderMarkBlankLinesAndSpacesAreHandled() {
        assertEquals(listOf(listOf("Date", "Amount"), listOf("x", "1")), rows("\uFEFFDate , Amount \n\n  \n x ,1\n"))
        assertEquals(listOf(listOf(" keep ", "b")), rows("\" keep \",b"), "spaces inside quotes are kept")
        assertEquals(listOf(listOf("a", "", "")), rows("a,,"), "empty cells stay as cells")
    }

    @Test
    fun theSeparatorIsDetectedForSemicolonTabAndPipeFiles() {
        assertEquals(';', Csv.parse("a;b;c\n1;2;3\n").delimiter)
        assertEquals('\t', Csv.parse("a\tb\tc\n1\t2\t3\n").delimiter)
        assertEquals('|', Csv.parse("a|b|c\n1|2|3\n").delimiter)
        assertEquals(',', Csv.parse("a,b,c\n1,2,3\n").delimiter)
        assertEquals(',', Csv.parse("just one column\nanother\n").delimiter)
        assertEquals(';', Csv.parse("a;b\n\"1,5\";\"2,5\"\n3;4\n").delimiter, "commas inside quotes are not separators")
        assertEquals(listOf("1,5", "2,5"), Csv.parse("a;b\n\"1,5\";\"2,5\"\n").rows[1])
    }

    @Test
    fun commasInsideQuotedCellsNeverDecideTheSeparator() {
        // every line has two commas inside quotes and one real semicolon: counting the commas would wrongly win
        val text = "\"1,5\";\"2,5\"\n\"3,5\";\"4,5\"\n\"5,5\";\"6,5\"\n"
        assertEquals(';', Csv.detectDelimiter(text))
        assertEquals(listOf(listOf("1,5", "2,5"), listOf("3,5", "4,5"), listOf("5,5", "6,5")), Csv.parse(text).rows)
    }

    @Test
    fun anUnclosedQuoteIsFlaggedAsDamagedNotSilentlyAccepted() {
        val t = Csv.parse("a,b\n1,\"never closed\n2,3\n")
        assertTrue(t.damaged)
        assertEquals("a", t.rows[0][0])
    }

    @Test
    fun eachRowRemembersTheLineOfTheFileItStartsOnEvenAfterBlankLinesAndLineBreaksInQuotes() {
        val t = Csv.parse("h1,h2\n\n\na,b\n\"x\ny\nz\",c\r\nlast,row\n")
        assertEquals(listOf("h1", "a", "x\ny\nz", "last"), t.rows.map { it[0] })
        assertEquals(listOf(1, 4, 5, 8), t.lines, "a quoted cell spanning lines 5 to 7 pushes the next row to line 8")
        assertEquals(listOf(1, 2), Csv.parse("a\r\nb\r\n").lines, "CRLF is one line break")
        assertEquals(listOf(1, 2), Csv.parse("a\rb\r").lines, "so is a bare CR")
    }

    @Test
    fun emptyInputGivesNoRows() {
        assertEquals(emptyList(), rows(""))
        assertEquals(emptyList(), rows("\n\n"))
        assertFalse(Csv.parse("").damaged)
    }

    private fun quote(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    @Test
    fun anythingWrittenAsQuotedCsvReadsBackExactly() = runTest {
        // the export side quotes cells with commas, quotes and line breaks; whatever it can write must read back identically
        val alphabet = listOf('a', 'B', '7', ' ', ',', ';', '\t', '|', '"', '\n', '\r', '₹')
        val cell = Arb.list(Arb.element(alphabet), 0..10).map { it.joinToString("") }
        checkAll(Arb.list(Arb.list(cell, 1..4), 1..5), Arb.int(0, 3)) { table, which ->
            val d = listOf(',', ';', '\t', '|')[which]
            val text = table.joinToString("\r\n") { r -> r.joinToString(d.toString()) { quote(it) } }
            val expected = table.filter { r -> r.any { it.isNotEmpty() } }
            assertEquals(expected, Csv.parse(text, d).rows)
        }
    }
}

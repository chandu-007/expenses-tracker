package app.expenses.core.storage

import java.io.ByteArrayInputStream
import java.util.zip.CRC32
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The workbook is read back with the JDK's own zip and XML readers, which share no code with the writer. */
class XlsxExportTest {
    private fun parts(bytes: ByteArray): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z -> generateSequence { z.nextEntry }.forEach { e -> out[e.name] = z.readBytes().decodeToString() } } // ZipInputStream checks each CRC
        return out
    }

    private fun xml(s: String) = DocumentBuilderFactory.newInstance().also { it.isNamespaceAware = true }.newDocumentBuilder().parse(ByteArrayInputStream(s.encodeToByteArray()))

    /** Reads sheet1 back into rows of (value, isNumber), skipping empty cells by their reference. */
    private fun cells(sheet: String): List<Map<String, Pair<String, Boolean>>> {
        val doc = xml(sheet); val rows = doc.getElementsByTagName("row")
        return (0 until rows.length).map { r ->
            val cs = (rows.item(r) as org.w3c.dom.Element).getElementsByTagName("c")
            (0 until cs.length).associate { i -> val c = cs.item(i) as org.w3c.dom.Element; c.getAttribute("r").filter { it.isLetter() } to (c.textContent to (c.getAttribute("t") != "inlineStr")) }
        }
    }

    @Test
    fun theFileIsAValidZipOfTheFivePartsAndEveryPartIsWellFormedXml() {
        val p = parts(XlsxExport.build(listOf(listOf("A", "B"), listOf("x", "1"))))
        assertEquals(listOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/worksheets/sheet1.xml"), p.keys.toList())
        p.values.forEach { xml(it) }
        assertTrue(p.getValue("xl/workbook.xml").contains("name=\"Expenses\""))
    }

    @Test
    fun numbersAreNumericCellsTextIsTextAndEmptyCellsAreSkippedWithTheRightColumnLetters() {
        val rows = listOf(listOf("Date", "Amount", "Note", "Share"), listOf("2026-10-07", "1234.50", "", "-20"), listOf("2026-10-08", "12abc", "x", ""))
        val c = cells(parts(XlsxExport.build(rows, numericColumns = setOf(1, 3))).getValue("xl/worksheets/sheet1.xml"))
        assertEquals("1234.50" to true, c[1]["B"]); assertEquals("-20" to true, c[1]["D"]); assertEquals(null, c[1]["C"], "an empty cell is not written")
        assertEquals("12abc" to false, c[2]["B"], "a non-number in a numeric column stays text rather than breaking the file")
        assertEquals("Amount" to false, c[0]["B"], "the header is text even in a numeric column")
        assertEquals("2026" to false, cells(parts(XlsxExport.build(listOf(listOf("x", "2026"), listOf("a", "5")), setOf(1))).getValue("xl/worksheets/sheet1.xml"))[0]["B"], "even a header that looks like a number")
        assertEquals(listOf("A", "B", "Z", "AA", "AB", "AZ", "BA"), listOf(0, 1, 25, 26, 27, 51, 52).map { XlsxExport.columnName(it) })
    }

    @Test
    fun markupCharactersUnicodeLineBreaksAndSpacesSurviveAndFormulaLookingTextStaysText() {
        val tricky = listOf("=HYPERLINK(\"x\")", "Café ₹ 日本", "a & b < c > d \"q\" 'r'", "  padded  ", "line1\nline2", "bad\u0001char")
        val c = cells(parts(XlsxExport.build(listOf(tricky))).getValue("xl/worksheets/sheet1.xml"))[0]
        assertEquals(listOf("=HYPERLINK(\"x\")", "Café ₹ 日本", "a & b < c > d \"q\" 'r'", "  padded  ", "line1\nline2", "badchar"), listOf("A", "B", "C", "D", "E", "F").map { c.getValue(it).first })
        assertTrue(listOf("A", "B", "C", "D", "E", "F").all { !c.getValue(it).second }, "all text, so the formula is never evaluated")
    }

    @Test
    fun theCrcMatchesTheJdkOnRandomDataAndALargeSheetStaysValid() {
        val rnd = Random(3)
        repeat(50) { val d = rnd.nextBytes(rnd.nextInt(0, 5000)); assertEquals(CRC32().also { it.update(d) }.value.toInt(), XlsxExport.crc32(d)) }
        val rows = listOf(listOf("n", "v")) + (1..20_000).map { listOf("row $it", "$it.25") }
        val c = cells(parts(XlsxExport.build(rows, setOf(1))).getValue("xl/worksheets/sheet1.xml"))
        assertEquals(20_001, c.size); assertEquals("20000.25" to true, c.last()["B"])
    }
}

package app.expenses

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.expenses.core.storage.XlsxExport
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class XlsxReaderTest {
    /** A workbook laid out the way Excel writes one: compressed parts, shared strings, a styles part with date formats, gaps, a rich-text string. */
    private fun excelLike(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.setLevel(Deflater.BEST_COMPRESSION)
            fun put(name: String, text: String) { z.putNextEntry(ZipEntry(name)); z.write(text.encodeToByteArray()); z.closeEntry() }
            put("[Content_Types].xml", "<Types/>")
            put("xl/workbook.xml", """<workbook xmlns="x" xmlns:r="r"><sheets><sheet name="Statement" sheetId="1" r:id="rId7"/><sheet name="Other" sheetId="2" r:id="rId8"/></sheets></workbook>""")
            put("xl/_rels/workbook.xml.rels", """<Relationships><Relationship Id="rId8" Target="worksheets/sheet1.xml"/><Relationship Id="rId7" Target="worksheets/sheet2.xml"/></Relationships>""")
            put("xl/sharedStrings.xml", """<sst><si><t>Date</t></si><si><t>Narration</t></si><si><t>Withdrawal Amt.</t></si><si><t>Deposit Amt.</t></si><si><r><t>UPI-</t></r><r><t>SWIGGY</t></r><rPh><t>ignored</t></rPh></si><si><t>Salary &amp; bonus</t></si></sst>""")
            put("xl/styles.xml", """<styleSheet><numFmts><numFmt numFmtId="164" formatCode="dd/mm/yyyy"/><numFmt numFmtId="165" formatCode="#,##0.00"/></numFmts><cellXfs><xf numFmtId="0"/><xf numFmtId="164"/><xf numFmtId="165"/><xf numFmtId="14"/></cellXfs></styleSheet>""")
            put("xl/worksheets/sheet2.xml", """<worksheet><sheetData>
                <row r="1"><c r="A1" t="inlineStr"><is><t>Account Statement</t></is></c></row>
                <row r="3"><c r="A3" t="s"><v>0</v></c><c r="B3" t="s"><v>1</v></c><c r="C3" t="s"><v>2</v></c><c r="D3" t="s"><v>3</v></c></row>
                <row r="4"><c r="A4" s="1"><v>46301</v></c><c r="B4" t="s"><v>4</v></c><c r="C4" s="2"><v>1250.5</v></c></row>
                <row r="5"><c r="A5" s="3"><v>46302.5</v></c><c r="B5" t="s"><v>5</v></c><c r="D5" s="2"><v>50000</v></c></row>
                <row r="6"><c r="A6"><v>46303</v></c><c r="B6" t="str"><v>formula text</v></c><c r="C6" t="b"><v>1</v></c><c r="D6" t="e"><v>#DIV/0!</v></c></row>
                <row r="7"/></sheetData></worksheet>""")
            put("xl/worksheets/sheet1.xml", """<worksheet><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>wrong sheet</t></is></c></row></sheetData></worksheet>""")
        }
        return out.toByteArray()
    }

    @Test
    fun anExcelStyleWorkbookIsReadWithTheFirstSheetSharedStringsDatesGapsAndRichText() {
        val rows = XlsxReader.read(excelLike())
        assertEquals(listOf("Account Statement"), rows[0])
        val body = rows.drop(1)
        assertEquals(listOf("Date", "Narration", "Withdrawal Amt.", "Deposit Amt."), body[0])
        assertEquals(listOf("2026-10-06", "UPI-SWIGGY", "1250.5"), body[1], "a date-formatted number becomes a date; rich text is joined; phonetic hints are dropped")
        assertEquals(listOf("2026-10-07", "Salary & bonus", "", "50000"), body[2], "a built-in date format works, the time of day is dropped, and a gap stays a gap")
        assertEquals(listOf("46303", "formula text", "TRUE", ""), body[3], "a number with no date format stays a number; booleans and errors are tame")
    }

    @Test
    fun theAppsOwnWorkbookReadsBackToTheRowsThatWereWritten() {
        val rows = listOf(listOf("Date", "Amount", "Note"), listOf("2026-10-07", "12.50", "Café & <b>"), listOf("2026-10-08", "", "x"), listOf("2026-10-09", "5", ""))
        assertEquals(rows, XlsxReader.read(XlsxExport.build(rows, setOf(1))).map { r -> r.padEnd(3) })
    }
    private fun List<String>.padEnd(n: Int) = this + List((n - size).coerceAtLeast(0)) { "" }

    @Test
    fun aZipThatIsNotAWorkbookAndANonZipAreRefusedWithAReason() {
        val plain = ByteArrayOutputStream().also { o -> ZipOutputStream(o).use { z -> z.putNextEntry(ZipEntry("a.txt")); z.write("hi".encodeToByteArray()); z.closeEntry() } }.toByteArray()
        assertTrue(assertFailsWith<XlsxReader.NotAWorkbook> { XlsxReader.read(plain) }.message!!.contains("not an Excel workbook"))
        assertTrue(!XlsxReader.looksLikeZip("Date,Amount\n1,2".encodeToByteArray()))
        assertTrue(XlsxReader.looksLikeZip(plain))
        assertFailsWith<XlsxReader.NotAWorkbook> { XlsxReader.read(plain.copyOf(plain.size / 2).also { it[0] = 'P'.code.toByte(); it[1] = 'K'.code.toByte() }) }
    }

    @Test
    fun theUnpackedSizeLimitIsEnforcedAtTheLimitItIsGiven() {
        val big = XlsxExport.build(List(200) { listOf("row $it", "some text to make it longer") })
        assertTrue(XlsxReader.read(big).size == 200)
        assertTrue(assertFailsWith<XlsxReader.NotAWorkbook> { XlsxReader.read(big, maxUnpacked = 2_000) }.message!!.contains("far more"))
    }

    @Test
    fun aZipBombIsRefusedBeforeItFillsMemory() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("xl/workbook.xml")); z.write("<workbook/>".encodeToByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml")); val block = ByteArray(1 shl 20) { ' '.code.toByte() }; repeat(30) { z.write(block) }; z.closeEntry()
        }
        assertTrue(out.size() < 1_000_000, "it compresses to almost nothing: ${out.size()}")
        assertTrue(assertFailsWith<XlsxReader.NotAWorkbook> { XlsxReader.read(out.toByteArray()) }.message!!.contains("far more"))
    }
}

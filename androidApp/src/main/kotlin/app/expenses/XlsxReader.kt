package app.expenses

import android.util.Xml
import app.expenses.core.domain.ExcelDates
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Reads the first sheet of an Excel workbook (.xlsx) into rows of text, for a statement downloaded as Excel. Dates (numbers shown with a date
 * format) become `yyyy-MM-dd`; everything else is the text the cell shows, or the number as stored. Formulas are not evaluated: the last value
 * Excel saved is used. Refuses anything that unpacks to more than [MAX_UNPACKED] bytes (a zip bomb) or is not a workbook.
 */
object XlsxReader {
    const val MAX_UNPACKED = 20L * 1024 * 1024

    class NotAWorkbook(message: String) : Exception(message)

    fun looksLikeZip(bytes: ByteArray) = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    fun read(bytes: ByteArray, maxUnpacked: Long = MAX_UNPACKED): List<List<String>> {
        val parts = unpack(bytes, maxUnpacked)
        val workbook = parts["xl/workbook.xml"] ?: throw NotAWorkbook("This is a zip file, but not an Excel workbook.")
        val sheetPath = firstSheetPath(workbook, parts["xl/_rels/workbook.xml.rels"]) ?: parts.keys.filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }.minOrNull()
            ?: throw NotAWorkbook("The workbook has no sheet.")
        val sheet = parts[sheetPath] ?: throw NotAWorkbook("The workbook's first sheet is missing.")
        val shared = parts["xl/sharedStrings.xml"]?.let(::sharedStrings).orEmpty()
        val styles = parts["xl/styles.xml"]?.let(::dateStyles).orEmpty()
        return cells(sheet, shared, styles)
    }

    private fun unpack(bytes: ByteArray, maxUnpacked: Long): Map<String, ByteArray> {
        val wanted = { n: String -> n == "xl/workbook.xml" || n == "xl/_rels/workbook.xml.rels" || n == "xl/sharedStrings.xml" || n == "xl/styles.xml" || (n.startsWith("xl/worksheets/") && n.endsWith(".xml")) }
        val out = HashMap<String, ByteArray>(); var total = 0L
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory || !wanted(e.name)) continue
                    val buf = java.io.ByteArrayOutputStream(); val chunk = ByteArray(16 * 1024)
                    while (true) { val n = z.read(chunk); if (n < 0) break; total += n; if (total > maxUnpacked) throw NotAWorkbook("This file unpacks to far more than a statement should."); buf.write(chunk, 0, n) }
                    out[e.name] = buf.toByteArray()
                }
            }
        } catch (e: NotAWorkbook) { throw e } catch (e: OutOfMemoryError) { throw NotAWorkbook("This file unpacks to far more than a statement should.") } catch (e: java.io.IOException) { throw NotAWorkbook("This file is damaged or is not a workbook.") }
        return out
    }

    private fun parser(xml: ByteArray) = Xml.newPullParser().also { it.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false); it.setInput(ByteArrayInputStream(xml), "UTF-8") }

    private fun firstSheetPath(workbook: ByteArray, rels: ByteArray?): String? {
        var rid: String? = null
        parser(workbook).let { p -> while (p.next() != XmlPullParser.END_DOCUMENT) if (p.eventType == XmlPullParser.START_TAG && p.name == "sheet" && rid == null) rid = p.getAttributeValue(null, "r:id") }
        val id = rid ?: return null
        var target: String? = null
        if (rels != null) parser(rels).let { p -> while (p.next() != XmlPullParser.END_DOCUMENT) if (p.eventType == XmlPullParser.START_TAG && p.name == "Relationship" && p.getAttributeValue(null, "Id") == id) target = p.getAttributeValue(null, "Target") }
        val t = target ?: return null
        return if (t.startsWith("/")) t.removePrefix("/") else "xl/$t"
    }

    /** Each `<si>` is one string, made of its `<t>` runs; phonetic hints (`<rPh>`) are not part of the text. */
    private fun sharedStrings(xml: ByteArray): List<String> {
        val out = ArrayList<String>(); val p = parser(xml); var inSi = false; var phonetic = 0; var inT = false; val sb = StringBuilder()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) { "si" -> { inSi = true; sb.setLength(0) }; "rPh" -> phonetic++; "t" -> inT = inSi && phonetic == 0 }
                XmlPullParser.TEXT -> if (inT) sb.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) { "t" -> inT = false; "rPh" -> phonetic--; "si" -> { out += sb.toString(); inSi = false } }
            }
        }
        return out
    }

    /** For each cell style index (the `s` of a cell), whether it shows a date. */
    private fun dateStyles(xml: ByteArray): List<Boolean> {
        val codes = HashMap<Int, String>(); val xfs = ArrayList<Int>(); val p = parser(xml); var inCellXfs = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType == XmlPullParser.START_TAG) when (p.name) {
                "numFmt" -> codes[p.getAttributeValue(null, "numFmtId").toInt()] = p.getAttributeValue(null, "formatCode")
                "cellXfs" -> inCellXfs = true
                "xf" -> if (inCellXfs) xfs += (p.getAttributeValue(null, "numFmtId") ?: "0").toInt()
            } else if (p.eventType == XmlPullParser.END_TAG && p.name == "cellXfs") inCellXfs = false
        }
        return xfs.map { ExcelDates.isDateFormat(it, codes[it]) }
    }

    private fun columnIndex(ref: String): Int { var n = 0; for (c in ref) { if (!c.isLetter()) break; n = n * 26 + (c.uppercaseChar() - 'A' + 1) }; return n - 1 }

    private fun cells(sheet: ByteArray, shared: List<String>, dateStyle: List<Boolean>): List<List<String>> {
        val rows = ArrayList<MutableList<String>>(); val p = parser(sheet)
        var row: MutableList<String>? = null; var col = 0; var type = ""; var style = -1
        var inV = false; var inT = false; val v = StringBuilder()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> row = ArrayList()
                    "c" -> { col = p.getAttributeValue(null, "r")?.let(::columnIndex) ?: ((row?.size) ?: 0); type = p.getAttributeValue(null, "t").orEmpty(); style = p.getAttributeValue(null, "s")?.toIntOrNull() ?: -1; v.setLength(0) }
                    "v" -> inV = true
                    "t" -> inT = type == "inlineStr"
                }
                XmlPullParser.TEXT -> if (inV || inT) v.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v" -> inV = false
                    "t" -> inT = false
                    "c" -> {
                        val r = row ?: continue
                        val raw = v.toString()
                        val text = when (type) {
                            "s" -> shared.getOrElse(raw.trim().toIntOrNull() ?: -1) { "" }
                            "inlineStr", "str" -> raw
                            "b" -> if (raw == "1") "TRUE" else "FALSE"
                            "e" -> ""
                            else -> if (style in dateStyle.indices && dateStyle[style]) raw.toDoubleOrNull()?.let(ExcelDates::serialToIso) ?: raw else raw
                        }
                        while (r.size < col) r += ""
                        if (r.size == col) r += text else r[col] = text
                    }
                    "row" -> { rows += row ?: mutableListOf(); row = null }
                }
            }
        }
        while (rows.isNotEmpty() && rows.last().all { it.isBlank() }) rows.removeAt(rows.size - 1)
        return rows
    }
}

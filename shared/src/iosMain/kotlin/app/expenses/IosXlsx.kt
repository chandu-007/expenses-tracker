package app.expenses

import app.expenses.core.domain.ExcelDates
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSXMLParser
import platform.Foundation.NSXMLParserDelegateProtocol
import platform.darwin.NSObject
import platform.zlib.Z_BUF_ERROR
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.ZLIB_VERSION
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream

/**
 * The iPhone twin of the Android XlsxReader: the first sheet of an Excel workbook (.xlsx) as rows of text. Same rules: dates (numbers shown with a
 * date format) become `yyyy-MM-dd`, formulas are not evaluated, and anything that unpacks to more than [MAX_UNPACKED] bytes is refused. The zip is
 * read here (zlib does the inflating) and the XML with Foundation's NSXMLParser.
 */
internal object IosXlsx {
    const val MAX_UNPACKED = 20L * 1024 * 1024

    private class NotAWorkbook(message: String) : Exception(message)

    fun read(bytes: ByteArray): List<List<String>> {
        try {
            return readSheet(bytes)
        } catch (e: NotAWorkbook) {
            throw IllegalArgumentException(e.message)
        }
    }

    private fun readSheet(bytes: ByteArray): List<List<String>> {
        val parts = unzip(bytes)
        val workbook = parts["xl/workbook.xml"] ?: throw NotAWorkbook("This is a zip file, but not an Excel workbook.")
        val sheetPath = firstSheetPath(workbook, parts["xl/_rels/workbook.xml.rels"]) ?: parts.keys.filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }.minOrNull()
            ?: throw NotAWorkbook("The workbook has no sheet.")
        val sheet = parts[sheetPath] ?: throw NotAWorkbook("The workbook's first sheet is missing.")
        val shared = parts["xl/sharedStrings.xml"]?.let(::sharedStrings).orEmpty()
        val styles = parts["xl/styles.xml"]?.let(::dateStyles).orEmpty()
        return cells(sheet, shared, styles)
    }

    // ---- zip ----

    private fun u16(b: ByteArray, at: Int) = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)
    private fun u32(b: ByteArray, at: Int) = u16(b, at).toLong() or (u16(b, at + 2).toLong() shl 16)

    /** The wanted parts of the workbook by name. Reads the central directory, so a stored size of zero in a local header (streamed zips) does not matter. */
    private fun unzip(zip: ByteArray): Map<String, ByteArray> {
        val damaged = NotAWorkbook("This file is damaged or is not a workbook.")
        val wanted = { n: String -> n == "xl/workbook.xml" || n == "xl/_rels/workbook.xml.rels" || n == "xl/sharedStrings.xml" || n == "xl/styles.xml" || (n.startsWith("xl/worksheets/") && n.endsWith(".xml")) }
        var eocd = zip.size - 22
        while (eocd >= 0 && eocd >= zip.size - 22 - 65_535 && u32(zip, eocd) != 0x06054b50L) eocd--
        if (eocd < 0 || u32(zip, eocd) != 0x06054b50L) throw damaged
        val count = u16(zip, eocd + 10)
        var at = u32(zip, eocd + 16).toInt()
        val out = HashMap<String, ByteArray>()
        var total = 0L
        repeat(count) {
            if (at < 0 || at + 46 > zip.size || u32(zip, at) != 0x02014b50L) throw damaged
            val method = u16(zip, at + 10)
            val compressed = u32(zip, at + 20)
            val size = u32(zip, at + 24)
            val nameLen = u16(zip, at + 28); val extraLen = u16(zip, at + 30); val commentLen = u16(zip, at + 32)
            val local = u32(zip, at + 42).toInt()
            if (at + 46 + nameLen > zip.size) throw damaged
            val name = zip.decodeToString(at + 46, at + 46 + nameLen)
            at += 46 + nameLen + extraLen + commentLen
            if (name.endsWith("/") || !wanted(name)) return@repeat
            if (size > MAX_UNPACKED || total + size > MAX_UNPACKED) throw NotAWorkbook("This file unpacks to far more than a statement should.")
            if (local < 0 || local + 30 > zip.size || u32(zip, local) != 0x04034b50L) throw damaged
            val dataStart = local + 30 + u16(zip, local + 26) + u16(zip, local + 28)
            if (dataStart + compressed > zip.size) throw damaged
            val data = when (method) {
                0 -> zip.copyOfRange(dataStart, dataStart + compressed.toInt())
                8 -> inflate(zip, dataStart, compressed.toInt(), size.toInt(), MAX_UNPACKED - total) ?: throw damaged
                else -> throw damaged
            }
            total += data.size
            out[name] = data
        }
        return out
    }

    /** Raw-deflate data to bytes, or null when it is not valid; refuses to produce more than [limit] bytes. */
    @OptIn(ExperimentalForeignApi::class)
    private fun inflate(src: ByteArray, from: Int, length: Int, expected: Int, limit: Long): ByteArray? = memScoped {
        val strm = alloc<z_stream>()
        if (inflateInit2_(strm.ptr, -15, ZLIB_VERSION, sizeOf<z_stream>().toInt()) != Z_OK) return@memScoped null
        try {
            var out = ByteArray(maxOf(expected, 1024))
            var produced = 0
            src.usePinned { input ->
                strm.next_in = input.addressOf(from).reinterpret()
                strm.avail_in = length.convert()
                while (true) {
                    if (produced == out.size) {
                        if (out.size.toLong() >= limit) throw NotAWorkbook("This file unpacks to far more than a statement should.")
                        out = out.copyOf(minOf(out.size.toLong() * 2, limit + 1).toInt())
                    }
                    var rc = 0
                    out.usePinned { o ->
                        strm.next_out = o.addressOf(produced).reinterpret()
                        strm.avail_out = (out.size - produced).convert()
                        rc = inflate(strm.ptr, Z_NO_FLUSH)
                        produced = out.size - strm.avail_out.toInt()
                    }
                    if (produced > limit) throw NotAWorkbook("This file unpacks to far more than a statement should.")
                    if (rc == Z_STREAM_END) break
                    if (rc != Z_OK && rc != Z_BUF_ERROR) return@memScoped null
                    if (rc == Z_BUF_ERROR && strm.avail_in == 0u) return@memScoped null
                }
            }
            out.copyOf(produced)
        } finally {
            inflateEnd(strm.ptr)
        }
    }

    // ---- xml ----

    private class Events(
        val onStart: (String, Map<String, String>) -> Unit,
        val onText: (String) -> Unit,
        val onEnd: (String) -> Unit,
    ) : NSObject(), NSXMLParserDelegateProtocol {
        override fun parser(parser: NSXMLParser, didStartElement: String, namespaceURI: String?, qualifiedName: String?, attributes: Map<Any?, *>) {
            onStart(didStartElement, attributes.entries.associate { (k, v) -> k.toString() to v.toString() })
        }

        override fun parser(parser: NSXMLParser, didEndElement: String, namespaceURI: String?, qualifiedName: String?) = onEnd(didEndElement)
        override fun parser(parser: NSXMLParser, foundCharacters: String) = onText(foundCharacters)
    }

    private fun parse(xml: ByteArray, onStart: (String, Map<String, String>) -> Unit, onText: (String) -> Unit = {}, onEnd: (String) -> Unit = {}) {
        val parser = NSXMLParser(data = xml.toNSData())
        val events = Events(onStart, onText, onEnd)
        parser.delegate = events
        parser.shouldProcessNamespaces = false
        if (!parser.parse() && parser.parserError != null) throw NotAWorkbook("This file is damaged or is not a workbook.")
    }

    private fun firstSheetPath(workbook: ByteArray, rels: ByteArray?): String? {
        var rid: String? = null
        parse(workbook, { name, a -> if (name == "sheet" && rid == null) rid = a["r:id"] })
        val id = rid ?: return null
        var target: String? = null
        if (rels != null) parse(rels, { name, a -> if (name == "Relationship" && a["Id"] == id) target = a["Target"] })
        val t = target ?: return null
        return if (t.startsWith("/")) t.removePrefix("/") else "xl/$t"
    }

    /** Each `<si>` is one string, made of its `<t>` runs; phonetic hints (`<rPh>`) are not part of the text. */
    private fun sharedStrings(xml: ByteArray): List<String> {
        val out = ArrayList<String>(); var inSi = false; var phonetic = 0; var inT = false; val sb = StringBuilder()
        parse(xml,
            onStart = { name, _ -> when (name) { "si" -> { inSi = true; sb.clear() }; "rPh" -> phonetic++; "t" -> inT = inSi && phonetic == 0 } },
            onText = { if (inT) sb.append(it) },
            onEnd = { name -> when (name) { "t" -> inT = false; "rPh" -> phonetic--; "si" -> { out += sb.toString(); inSi = false } } })
        return out
    }

    /** For each cell style index (the `s` of a cell), whether it shows a date. */
    private fun dateStyles(xml: ByteArray): List<Boolean> {
        val codes = HashMap<Int, String>(); val xfs = ArrayList<Int>(); var inCellXfs = false
        parse(xml,
            onStart = { name, a -> when (name) {
                "numFmt" -> codes[a["numFmtId"]!!.toInt()] = a["formatCode"].orEmpty()
                "cellXfs" -> inCellXfs = true
                "xf" -> if (inCellXfs) xfs += (a["numFmtId"] ?: "0").toInt()
            } },
            onEnd = { if (it == "cellXfs") inCellXfs = false })
        return xfs.map { ExcelDates.isDateFormat(it, codes[it]) }
    }

    private fun columnIndex(ref: String): Int { var n = 0; for (c in ref) { if (!c.isLetter()) break; n = n * 26 + (c.uppercaseChar() - 'A' + 1) }; return n - 1 }

    private fun cells(sheet: ByteArray, shared: List<String>, dateStyle: List<Boolean>): List<List<String>> {
        val rows = ArrayList<MutableList<String>>()
        var row: MutableList<String>? = null; var col = 0; var type = ""; var style = -1
        var inV = false; var inT = false; val v = StringBuilder()
        parse(sheet,
            onStart = { name, a -> when (name) {
                "row" -> row = ArrayList()
                "c" -> { col = a["r"]?.let(::columnIndex) ?: (row?.size ?: 0); type = a["t"].orEmpty(); style = a["s"]?.toIntOrNull() ?: -1; v.clear() }
                "v" -> inV = true
                "t" -> inT = type == "inlineStr"
            } },
            onText = { if (inV || inT) v.append(it) },
            onEnd = { name -> when (name) {
                "v" -> inV = false
                "t" -> inT = false
                "c" -> row?.let { r ->
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
            } })
        while (rows.isNotEmpty() && rows.last().all { it.isBlank() }) rows.removeAt(rows.size - 1)
        return rows
    }
}

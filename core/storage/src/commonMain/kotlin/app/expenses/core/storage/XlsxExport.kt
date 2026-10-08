package app.expenses.core.storage

/**
 * A minimal .xlsx workbook (one sheet) written without any library: an Office Open XML package is a zip of a few small XML parts. Amount-like
 * columns are written as numbers (so sums and sorting work), everything else as text. The zip is "stored" (not compressed), which keeps this
 * in plain Kotlin that also builds for iOS; spreadsheets read it normally and the file is larger than a compressed one would be.
 */
object XlsxExport {
    /** Zero-based columns written as numbers when the cell parses as a plain number. */
    fun build(rows: List<List<String>>, numericColumns: Set<Int> = emptySet(), sheetName: String = "Expenses"): ByteArray {
        val parts = listOf(
            "[Content_Types].xml" to CONTENT_TYPES,
            "_rels/.rels" to ROOT_RELS,
            "xl/workbook.xml" to workbook(sheetName),
            "xl/_rels/workbook.xml.rels" to WORKBOOK_RELS,
            "xl/worksheets/sheet1.xml" to sheet(rows, numericColumns),
        )
        return zipStored(parts.map { it.first to it.second.encodeToByteArray() })
    }

    internal fun columnName(index: Int): String {
        var n = index; val sb = StringBuilder()
        do { sb.append('A' + n % 26); n = n / 26 - 1 } while (n >= 0)
        return sb.reverse().toString()
    }

    private val number = Regex("-?\\d+(\\.\\d+)?")

    private fun sheet(rows: List<List<String>>, numericColumns: Set<Int>): String {
        val sb = StringBuilder(rows.size * 120 + 200)
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""").append("\n")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { r, row ->
            sb.append("<row r=\"").append(r + 1).append("\">")
            row.forEachIndexed { c, cell ->
                if (cell.isEmpty()) return@forEachIndexed
                val ref = columnName(c) + (r + 1)
                if (r > 0 && c in numericColumns && number.matches(cell)) sb.append("<c r=\"").append(ref).append("\"><v>").append(cell).append("</v></c>")
                else sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(escape(cell)).append("</t></is></c>")
            }
            sb.append("</row>")
        }
        return sb.append("</sheetData></worksheet>").toString()
    }

    /** XML text: the five markup characters escaped, and characters XML 1.0 cannot hold at all (most control characters) dropped. */
    internal fun escape(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) when {
            ch == '&' -> sb.append("&amp;"); ch == '<' -> sb.append("&lt;"); ch == '>' -> sb.append("&gt;"); ch == '"' -> sb.append("&quot;"); ch == '\'' -> sb.append("&apos;")
            ch.code < 0x20 && ch != '\t' && ch != '\n' && ch != '\r' -> Unit
            ch.code == 0xFFFE || ch.code == 0xFFFF -> Unit
            else -> sb.append(ch)
        }
        return sb.toString()
    }

    private fun workbook(name: String) = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="${escape(name)}" sheetId="1" r:id="rId1"/></sheets></workbook>"""

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>"""
    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
    private const val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>"""

    // ---- a stored zip ----

    private val crcTable = IntArray(256) { n -> var c = n; repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }; c }

    internal fun crc32(data: ByteArray): Int { var c = -1; for (b in data) c = crcTable[(c xor b.toInt()) and 0xFF] xor (c ushr 8); return c.inv() }

    private fun zipStored(files: List<Pair<String, ByteArray>>): ByteArray {
        val out = ArrayList<Byte>()
        fun u16(v: Int) { out += v.toByte(); out += (v ushr 8).toByte() }
        fun u32(v: Int) { u16(v and 0xFFFF); u16(v ushr 16) }
        val offsets = ArrayList<Int>()
        for ((name, data) in files) {
            offsets += out.size
            val n = name.encodeToByteArray()
            u32(0x04034b50); u16(20); u16(0x0800); u16(0); u16(0); u16(0x21) // version, UTF-8 names, stored, time 0, date 1980-01-01
            u32(crc32(data)); u32(data.size); u32(data.size); u16(n.size); u16(0)
            n.forEach { out += it }; data.forEach { out += it }
        }
        val centralStart = out.size
        files.forEachIndexed { i, (name, data) ->
            val n = name.encodeToByteArray()
            u32(0x02014b50); u16(20); u16(20); u16(0x0800); u16(0); u16(0); u16(0x21)
            u32(crc32(data)); u32(data.size); u32(data.size); u16(n.size); u16(0); u16(0); u16(0); u16(0); u32(0); u32(offsets[i])
            n.forEach { out += it }
        }
        val centralSize = out.size - centralStart
        u32(0x06054b50); u16(0); u16(0); u16(files.size); u16(files.size); u32(centralSize); u32(centralStart); u16(0)
        return out.toByteArray()
    }
}

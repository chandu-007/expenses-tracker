package app.expenses.core.domain

/**
 * Reads the first sheet of an old-format Excel workbook (.xls, "Excel 97-2003", BIFF8 inside an OLE2 container) into rows of text, the way the .xlsx
 * reader does: dates (numbers shown with a date format) become `yyyy-MM-dd`, a number is the number as stored, formulas give the value Excel last saved.
 * Written without a library: an .xls is a small file system (the compound file) holding a stream of records. Banks still hand out statements in this format.
 * Refuses a password-protected file, an Excel 5/95 file, and anything that is not a workbook, each with a message a person can act on.
 */
object XlsReader {
    class Unreadable(message: String) : Exception(message)

    private val SIGNATURE = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())
    private const val END_OF_CHAIN = -2
    private const val FREE = -1

    /** Some banks save a web page or an XML sheet under an .xls name. It is text starting with `<`, not a workbook, and this reader cannot use it. */
    fun looksLikeWebPageOrXml(bytes: ByteArray): Boolean {
        var i = 0
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) i = 3
        while (i < bytes.size && i < 64 && (bytes[i].toInt() == ' '.code || bytes[i].toInt() == '\n'.code || bytes[i].toInt() == '\r'.code || bytes[i].toInt() == '\t'.code)) i++
        return i < bytes.size && bytes[i] == '<'.code.toByte()
    }

    const val WEB_PAGE_MESSAGE = "This file has an Excel name but is really a web page or XML, which the app cannot read. Open it in Excel (or a browser), save it as .xlsx or CSV, and import that."

    fun looksLikeXls(bytes: ByteArray): Boolean = bytes.size >= 512 && SIGNATURE.indices.all { bytes[it] == SIGNATURE[it] }

    fun read(bytes: ByteArray): List<List<String>> {
        if (!looksLikeXls(bytes)) throw Unreadable("This is not an Excel (.xls) file.")
        val stream = try { workbookStream(bytes) } catch (e: Unreadable) { throw e } catch (e: RuntimeException) { throw Unreadable("This Excel file is damaged and could not be read.") }
        return try { sheet(stream) } catch (e: Unreadable) { throw e } catch (e: RuntimeException) { throw Unreadable("This Excel file is damaged and could not be read.") }
    }

    // ---- the compound file ----

    private fun u16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun i32(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun workbookStream(file: ByteArray): ByteArray {
        val sectorSize = 1 shl u16(file, 0x1E)
        val miniSize = 1 shl u16(file, 0x20)
        if (sectorSize != 512 && sectorSize != 4096) throw Unreadable("This Excel file is damaged and could not be read.")
        val sectorCount = (file.size - sectorSize) / sectorSize
        fun sectorAt(n: Int): Int { require(n >= 0 && n < sectorCount) { "sector out of range" }; return (n + 1) * sectorSize }

        // the file allocation table: the list of its own sectors is in the header, and in further sectors when the file is large
        val fatSectors = ArrayList<Int>()
        for (i in 0 until 109) { val s = i32(file, 0x4C + 4 * i); if (s >= 0) fatSectors += s }
        var difat = i32(file, 0x44); var guard = 0
        while (difat >= 0 && guard++ <= sectorCount) {
            val o = sectorAt(difat)
            for (i in 0 until sectorSize / 4 - 1) { val s = i32(file, o + 4 * i); if (s >= 0) fatSectors += s }
            difat = i32(file, o + sectorSize - 4)
        }
        val fat = IntArray(fatSectors.size * (sectorSize / 4))
        fatSectors.forEachIndexed { k, s -> val o = sectorAt(s); for (i in 0 until sectorSize / 4) fat[k * (sectorSize / 4) + i] = i32(file, o + 4 * i) }

        fun chain(start: Int): List<Int> {
            val out = ArrayList<Int>(); var s = start
            while (s >= 0 && out.size <= sectorCount) { out += s; s = if (s < fat.size) fat[s] else END_OF_CHAIN }
            return out
        }
        fun readChain(start: Int, size: Long?): ByteArray {
            val parts = chain(start); val out = ByteArray(parts.size * sectorSize)
            parts.forEachIndexed { k, s -> file.copyInto(out, k * sectorSize, sectorAt(s), sectorAt(s) + sectorSize) }
            return if (size != null && size < out.size) out.copyOf(size.toInt()) else out
        }

        val directory = readChain(i32(file, 0x30), null)
        class Entry(val name: String, val type: Int, val start: Int, val size: Long)
        val entries = (0 until directory.size / 128).map { i ->
            val o = i * 128; val len = (u16(directory, o + 0x40) / 2 - 1).coerceIn(0, 31)
            Entry(String(CharArray(len) { c -> u16(directory, o + 2 * c).toChar() }), directory[o + 0x42].toInt(), i32(directory, o + 0x74), i32(directory, o + 0x78).toLong() and 0xFFFFFFFFL)
        }
        val root = entries.firstOrNull { it.type == 5 } ?: throw Unreadable("This Excel file is damaged and could not be read.")
        val book = entries.firstOrNull { it.type == 2 && it.name == "Workbook" }
            ?: if (entries.any { it.type == 2 && it.name == "Book" }) throw Unreadable("This is an Excel 5 or 95 file. Open it in Excel, save it as .xlsx or CSV, and import that.")
            else throw Unreadable("This file has no Excel workbook in it.")

        val cutoff = i32(file, 0x38)
        if (book.size >= cutoff) return readChain(book.start, book.size)
        // small streams live in the mini stream: the root entry's own data, cut into 64-byte pieces and chained by the mini table
        val miniStream = readChain(root.start, root.size)
        val miniTable = readChain(i32(file, 0x3C), null).let { t -> IntArray(t.size / 4) { i32(t, 4 * it) } }
        val out = ByteArray(((book.size + miniSize - 1) / miniSize).toInt() * miniSize); var s = book.start; var k = 0
        while (s >= 0 && k * miniSize < out.size) { miniStream.copyInto(out, k * miniSize, s * miniSize, minOf(miniStream.size, (s + 1) * miniSize)); k++; s = if (s < miniTable.size) miniTable[s] else END_OF_CHAIN }
        return out.copyOf(book.size.toInt())
    }

    // ---- the workbook records ----

    private class Rec(val id: Int, val data: ByteArray, val continues: List<ByteArray>)

    private fun records(s: ByteArray, from: Int = 0): Sequence<Pair<Int, Rec>> = sequence {
        var o = from
        while (o + 4 <= s.size) {
            val id = u16(s, o); val len = u16(s, o + 2)
            if (o + 4 + len > s.size) break
            val data = s.copyOfRange(o + 4, o + 4 + len)
            var next = o + 4 + len; val cont = ArrayList<ByteArray>()
            // a record too long for one block carries on in CONTINUE records (0x3C)
            while (next + 4 <= s.size && u16(s, next) == 0x3C) { val l = u16(s, next + 2); if (next + 4 + l > s.size) break; cont += s.copyOfRange(next + 4, next + 4 + l); next += 4 + l }
            yield(o to Rec(id, data, cont)); o = next
        }
    }

    private class Cursor(val segs: List<ByteArray>) {
        var seg = 0; var off = 0
        private fun settle() { while (seg < segs.size - 1 && off >= segs[seg].size) { seg++; off = 0 } }
        fun u8(): Int { settle(); return segs[seg][off++].toInt() and 0xFF }
        fun u16() = u8() or (u8() shl 8)
        fun i32() = u16() or (u16() shl 16)
        fun skip(n: Int) { var left = n; while (left > 0) { settle(); val take = minOf(left, segs[seg].size - off); if (take <= 0) throw Unreadable("This Excel file is damaged and could not be read."); off += take; left -= take } }
        fun atEnd(): Boolean { settle(); return seg >= segs.size - 1 && off >= segs[seg].size }

        /** [count] characters of a string whose first flag byte (bit 0: two bytes a character) is [firstWide]; a CONTINUE boundary inside it restarts with a new flag byte. */
        fun chars(count: Int, firstWide: Boolean): String {
            val sb = StringBuilder(); var wide = firstWide; var left = count
            while (left > 0) {
                if (off >= segs[seg].size) { if (seg >= segs.size - 1) throw Unreadable("This Excel file is damaged and could not be read."); seg++; off = 0; wide = (u8() and 1) == 1 }
                val width = if (wide) 2 else 1
                val take = minOf(left, (segs[seg].size - off) / width)
                if (take == 0) { off = segs[seg].size; continue }
                for (i in 0 until take) { val lo = segs[seg][off].toInt() and 0xFF; val hi = if (wide) segs[seg][off + 1].toInt() and 0xFF else 0; sb.append(((hi shl 8) or lo).toChar()); off += width }
                left -= take
            }
            return sb.toString()
        }
    }

    /** A unicode string whose length is [lenBytes] long, then a flag byte, then the optional rich-text and phonetic counts, the characters and their trailers. */
    private fun unicodeString(c: Cursor, lenBytes: Int): String {
        val n = if (lenBytes == 1) c.u8() else c.u16()
        val flags = c.u8()
        val runs = if (flags and 0x08 != 0) c.u16() else 0
        val ext = if (flags and 0x04 != 0) c.i32() else 0
        val text = c.chars(n, flags and 1 == 1)
        c.skip(runs * 4 + ext)
        return text
    }

    private fun plain(d: Double): String {
        if (d == d.toLong().toDouble() && kotlin.math.abs(d) < 1e15) return d.toLong().toString()
        val s = d.toString()
        val e = s.indexOf('E').takeIf { it >= 0 } ?: return s
        // an exponent form ("1.23456789E7") written out in full, so a large amount stays readable as a number
        val exp = s.substring(e + 1).toInt(); val neg = s.startsWith("-"); val mant = s.substring(if (neg) 1 else 0, e)
        val digits = mant.replace(".", ""); val point = mant.indexOf('.').let { if (it < 0) mant.length else it } + exp
        val body = when {
            point <= 0 -> "0." + "0".repeat(-point) + digits
            point >= digits.length -> digits + "0".repeat(point - digits.length)
            else -> digits.substring(0, point) + "." + digits.substring(point)
        }.let { if (it.contains('.')) it.trimEnd('0').trimEnd('.') else it }
        return (if (neg) "-" else "") + body
    }

    private fun rk(v: Int): Double {
        val n = if (v and 2 != 0) (v shr 2).toDouble() else Double.fromBits((v.toLong() and 0xFFFFFFFC.toLong()) shl 32)
        return if (v and 1 != 0) n / 100.0 else n
    }

    private fun sheet(s: ByteArray): List<List<String>> {
        var bof = true; var version = 0
        val formats = HashMap<Int, String>(); val xfFormat = ArrayList<Int>(); var sst = emptyList<String>(); var date1904 = false
        var sheetOffset = -1
        for ((_, r) in records(s)) {
            val d = r.data
            when (r.id) {
                0x0809 -> { if (bof) { version = u16(d, 0); bof = false } }
                0x002F -> throw Unreadable("This Excel file is password-protected. Remove the password in Excel, then import it.")
                0x0022 -> date1904 = u16(d, 0) == 1
                0x041E -> { val c = Cursor(listOf(d) + r.continues); val idx = c.u16(); formats[idx] = unicodeString(c, 2) }
                0x00E0 -> xfFormat += u16(d, 2)
                0x0085 -> if (sheetOffset < 0 && d.size > 6 && (d[5].toInt() and 0xFF) == 0) sheetOffset = i32(d, 0)
                0x00FC -> {
                    val c = Cursor(listOf(d) + r.continues); c.i32(); val unique = c.i32(); val out = ArrayList<String>(unique.coerceAtMost(100_000))
                    for (i in 0 until unique) { if (c.atEnd()) break; out += unicodeString(c, 2) }
                    sst = out
                }
                0x000A -> break
            }
        }
        if (version != 0x0600) throw Unreadable("This is an Excel 5 or 95 file. Open it in Excel, save it as .xlsx or CSV, and import that.")
        if (sheetOffset < 0) throw Unreadable("This workbook has no sheet to import.")

        val isDate = { xf: Int -> xf in xfFormat.indices && xfFormat[xf].let { f -> ExcelDates.isDateFormat(f, formats[f]) } }
        val cells = HashMap<Int, HashMap<Int, String>>(); var maxRow = -1; var maxCol = -1
        fun put(row: Int, col: Int, text: String) { if (row !in 0..1_048_575 || col !in 0..16_383) return; cells.getOrPut(row) { HashMap() }[col] = text; if (row > maxRow) maxRow = row; if (col > maxCol) maxCol = col }
        fun number(row: Int, col: Int, xf: Int, v: Double) {
            put(row, col, if (isDate(xf)) ExcelDates.serialToIso(if (date1904) v + 1462.0 else v) ?: plain(v) else plain(v))
        }
        var depth = 0; var pendingFormula: Pair<Int, Int>? = null
        for ((_, r) in records(s, sheetOffset)) {
            val d = r.data
            when (r.id) {
                0x0809 -> depth++
                0x000A -> { depth--; if (depth <= 0) break }
                0x00FD -> put(u16(d, 0), u16(d, 2), sst.getOrElse(i32(d, 6)) { "" })
                0x0204 -> { val c = Cursor(listOf(d.copyOfRange(6, d.size)) + r.continues); put(u16(d, 0), u16(d, 2), unicodeString(c, 2)) }
                0x0203 -> number(u16(d, 0), u16(d, 2), u16(d, 4), Double.fromBits((i32(d, 6).toLong() and 0xFFFFFFFFL) or (i32(d, 10).toLong() shl 32)))
                0x027E -> number(u16(d, 0), u16(d, 2), u16(d, 4), rk(i32(d, 6)))
                0x00BD -> { val row = u16(d, 0); val first = u16(d, 2); val n = (d.size - 6) / 6; for (i in 0 until n) number(row, first + i, u16(d, 4 + 6 * i), rk(i32(d, 6 + 6 * i))) }
                0x0006 -> {
                    val row = u16(d, 0); val col = u16(d, 2); val xf = u16(d, 4)
                    if (u16(d, 12) == 0xFFFF) { if ((d[6].toInt() and 0xFF) == 0) pendingFormula = row to col else if ((d[6].toInt() and 0xFF) == 1) put(row, col, if (d[8].toInt() != 0) "TRUE" else "FALSE") }
                    else number(row, col, xf, Double.fromBits((i32(d, 6).toLong() and 0xFFFFFFFFL) or (i32(d, 10).toLong() shl 32)))
                }
                0x0207 -> pendingFormula?.let { (row, col) -> val c = Cursor(listOf(d) + r.continues); put(row, col, unicodeString(c, 2)); pendingFormula = null }
            }
        }
        if (maxRow < 0) return emptyList()
        val rows = (0..maxRow).map { row -> val m = cells[row]; List(maxCol + 1) { col -> m?.get(col).orEmpty() } }.toMutableList()
        // rows are as wide as the sheet; the last ones that are blank are not rows of the statement
        while (rows.isNotEmpty() && rows.last().all { it.isBlank() }) rows.removeAt(rows.size - 1)
        return rows.map { r -> r.dropLastWhile { it.isEmpty() }.let { t -> if (t.isEmpty()) emptyList() else r } }
    }
}

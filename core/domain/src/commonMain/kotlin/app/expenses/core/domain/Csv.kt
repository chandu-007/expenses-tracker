package app.expenses.core.domain

/**
 * The rows of a parsed CSV. [lines] gives, for each row, the line of the file it starts on (counting from 1, including the blank lines
 * that were dropped), so a message about a row can point at the row the user sees in a spreadsheet or text editor. [damaged] is set when
 * a quoted field was never closed, which usually means the file is cut off or not really a CSV.
 */
data class CsvTable(val rows: List<List<String>>, val delimiter: Char, val damaged: Boolean, val lines: List<Int> = rows.indices.map { it + 1 })

/**
 * A forgiving reader for the CSV files banks export (RFC 4180 plus the usual real-world habits): a byte-order mark, comma, semicolon,
 * tab or pipe as the separator, quoted fields with doubled quotes and line breaks inside them, CRLF, LF or CR line endings, and blank
 * lines. Cells are trimmed of surrounding spaces unless they were quoted. Nothing is interpreted here; a cell is just text.
 */
object Csv {
    private val DELIMITERS = listOf(',', ';', '\t', '|')

    fun parse(text: String, delimiter: Char? = null): CsvTable {
        val clean = text.removePrefix("\uFEFF")
        val d = delimiter ?: detectDelimiter(clean)
        val rows = ArrayList<List<String>>()
        val lines = ArrayList<Int>()
        var line = 1        // the physical line the reader is on
        var rowLine = 1     // the line the current row started on
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false      // inside quotes right now
        var wasQuoted = false   // this cell was quoted, so keep its spaces
        var damaged = false
        var i = 0

        fun endCell() {
            row.add(if (wasQuoted) cell.toString() else cell.toString().trim())
            cell.setLength(0); wasQuoted = false
        }
        fun endRow() {
            endCell()
            if (row.any { it.isNotEmpty() }) { rows.add(row); lines.add(rowLine) }
            row = ArrayList()
            rowLine = line
        }

        while (i < clean.length) {
            val c = clean[i]
            if (quoted) {
                when {
                    c == '"' && i + 1 < clean.length && clean[i + 1] == '"' -> { cell.append('"'); i++ }
                    c == '"' -> quoted = false
                    else -> { cell.append(c); if (c == '\n') line++ }
                }
            } else when {
                c == '"' && cell.isBlank() -> { quoted = true; wasQuoted = true; cell.setLength(0) }
                c == d -> endCell()
                c == '\r' -> { if (i + 1 < clean.length && clean[i + 1] == '\n') i++; line++; endRow() }
                c == '\n' -> { line++; endRow() }
                else -> cell.append(c)
            }
            i++
        }
        if (quoted) damaged = true
        if (cell.isNotEmpty() || row.isNotEmpty() || wasQuoted) endRow()
        return CsvTable(rows, d, damaged, lines)
    }

    /**
     * Picks the separator that splits the first lines into the same number of cells, most often and most consistently (quoted text is
     * skipped over). Falls back to a comma.
     */
    fun detectDelimiter(text: String): Char {
        val lines = text.lineSequence().filter { it.isNotBlank() }.take(20).toList()
        if (lines.isEmpty()) return ','
        fun count(line: String, d: Char): Int { var q = false; var n = 0; for (ch in line) { if (ch == '"') q = !q else if (ch == d && !q) n++ }; return n }
        return DELIMITERS.maxByOrNull { d ->
            val counts = lines.map { count(it, d) }
            val common = counts.groupingBy { it }.eachCount().maxByOrNull { it.value }
            // consistent, and more than zero per line
            if (common == null || common.key == 0) 0 else common.value * 1000 + common.key
        }?.takeIf { d -> lines.any { count(it, d) > 0 } } ?: ','
    }
}

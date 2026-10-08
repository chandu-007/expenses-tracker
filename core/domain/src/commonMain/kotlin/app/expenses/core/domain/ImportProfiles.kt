package app.expenses.core.domain

/**
 * A bank's statement layout, remembered so the next file from the same bank needs no choosing. The key is the file's header row (its
 * column titles, lower-cased, spaces tidied): two files with the same titles in the same order are read the same way. The mapping is stored
 * as one line of text.
 */
object ImportProfiles {
    fun signature(headerRow: List<String>): String? {
        val cells = headerRow.map { it.trim().lowercase().replace(Regex("\\s+"), " ") }
        return if (cells.count { it.isNotEmpty() } < 2) null else "import_profile:" + cells.joinToString("|")
    }

    fun encode(m: StatementMapping): String = listOf(
        "h=${m.headerRows}", "d=${m.dateColumn ?: ""}", "o=${m.dateOrder.name}", "s=${m.descriptionColumn ?: ""}", "l=${m.layout.name}",
        "db=${m.debitColumn ?: ""}", "cr=${m.creditColumn ?: ""}", "a=${m.amountColumn ?: ""}", "t=${m.typeColumn ?: ""}", "g=${m.sign.name}",
    ).joinToString(";")

    /** The saved mapping, or null when the text is not one this version wrote (so a bad or future value is ignored, never trusted). */
    fun decode(text: String?, columns: Int): StatementMapping? {
        if (text == null) return null
        return try {
            val f = text.split(';').associate { it.substringBefore('=') to it.substringAfter('=') }
            fun col(k: String) = f[k]?.takeIf { it.isNotEmpty() }?.toInt()?.also { require(it in 0 until columns) }
            val m = StatementMapping(
                headerRows = f.getValue("h").toInt().also { require(it >= 0) }, dateColumn = col("d"), dateOrder = DateOrder.valueOf(f.getValue("o")), descriptionColumn = col("s"),
                layout = AmountLayout.valueOf(f.getValue("l")), debitColumn = col("db"), creditColumn = col("cr"), amountColumn = col("a"), typeColumn = col("t"),
                sign = SignConvention.valueOf(f.getValue("g")),
            )
            m.takeIf { it.missing() == null }
        } catch (e: Exception) { null }
    }
}

/** Excel stores dates as numbers (days since 1899-12-30, with the 1900 leap-year bug) and marks them only by the cell's number format. */
object ExcelDates {
    private val QUOTED = Regex("\"[^\"]*\"")
    private val BRACKETED = Regex("\\[[^\\]]*\\]")
    private val ESCAPED = Regex("\\\\.")
    private val builtInDateIds = setOf(14, 15, 16, 17, 18, 19, 20, 21, 22, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 45, 46, 47, 50, 51, 52, 53, 54, 55, 56, 57, 58)

    /** Whether a number format makes a cell a date: a built-in date id, or a custom code that shows day, month or year (outside quotes and brackets). */
    fun isDateFormat(numFmtId: Int, code: String?): Boolean {
        if (code == null) return numFmtId in builtInDateIds
        val stripped = code.replace(QUOTED, "").replace(BRACKETED, "").replace(ESCAPED, "").lowercase()
        return stripped.any { it == 'd' || it == 'y' } || (stripped.contains('m') && !stripped.contains("0") && !stripped.contains("#"))
    }

    /** A serial number as `yyyy-MM-dd` (any time of day dropped), or null when it is not a plausible date (before 1900 or after 9999). */
    fun serialToIso(serial: Double): String? {
        if (serial.isNaN() || serial < 1.0 || serial >= 2_958_466.0) return null
        var days = serial.toLong()
        if (days == 60L) return "1900-02-29" // Excel's phantom day; kept as Excel shows it
        if (days > 60) days -= 1
        // days is now counted from 1899-12-31 as day 1 = 1900-01-01
        val date = kotlinx.datetime.LocalDate.fromEpochDays((days - 25_568).toInt()) // 1900-01-01 is epoch day -25567
        return date.toString()
    }
}

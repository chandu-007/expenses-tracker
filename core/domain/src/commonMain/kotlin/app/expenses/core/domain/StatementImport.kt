package app.expenses.core.domain

import kotlinx.datetime.LocalDate

/** The order of day, month and year in a statement's dates. */
enum class DateOrder { DMY, MDY, YMD }

/** How a statement says whether money went out or came in. */
enum class AmountLayout { SEPARATE_DEBIT_CREDIT, SIGNED_AMOUNT, AMOUNT_WITH_TYPE }

/** For a single signed amount column: which sign is spending. Bank accounts usually show spending as negative, credit cards as positive. */
enum class SignConvention { NEGATIVE_IS_SPENDING, POSITIVE_IS_SPENDING }

enum class Flow { SPENDING, INCOME }

/** What a cell holds as an amount. A cell that is empty or a dash is [Blank]; one that looks like a number but cannot be read safely is [Invalid]. */
sealed interface AmountCell {
    data object Blank : AmountCell
    data object Invalid : AmountCell

    /** [minor] is the size in minor units (never negative). [negative] means a minus sign, brackets or a trailing minus; [flow] is set when the cell itself said Dr or Cr. */
    data class Value(val minor: Long, val negative: Boolean, val flow: Flow?) : AmountCell
}

object StatementDates {
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val SPLIT = Regex("[\\s/\\-.,:T]+")
    private const val MIN_YEAR = 1990
    private const val MAX_YEAR = 2100

    /** The date at the start of [text] in the given [order], ignoring anything after it (a time of day). Null when it is not a valid date. */
    fun parse(text: String, order: DateOrder): LocalDate? {
        val tokens = text.trim().split(SPLIT).filter { it.isNotEmpty() }
        if (tokens.size < 3) return null
        val (a, b, c) = tokens
        val (dayText, monthText, yearText) = when (order) {
            DateOrder.DMY -> Triple(a, b, c)
            DateOrder.MDY -> Triple(b, a, c)
            DateOrder.YMD -> Triple(c, b, a)
        }
        val day = dayText.toIntOrNull()?.takeIf { dayText.length <= 2 } ?: return null
        val month = monthText.toIntOrNull() ?: MONTHS.indexOfFirst { monthText.length >= 3 && monthText.lowercase().startsWith(it) }.takeIf { it >= 0 }?.plus(1) ?: return null
        val rawYear = yearText.toIntOrNull() ?: return null
        val year = when (yearText.length) {
            2 -> if (rawYear <= 69) 2000 + rawYear else 1900 + rawYear
            4 -> rawYear
            else -> return null
        }
        if (order == DateOrder.YMD && yearText.length != 4) return null
        if (year !in MIN_YEAR..MAX_YEAR || month !in 1..12) return null
        return try { LocalDate(year, month, day) } catch (e: IllegalArgumentException) { null }
    }

    /** What [detect] found: the order every sample fits, and whether another order also fits (day and month could be swapped). */
    data class Detection(val order: DateOrder, val ambiguous: Boolean)

    /** The order in which every non-blank sample is a valid date, preferring day-month-year when day and month cannot be told apart. Null if none fits. */
    fun detect(samples: List<String>): Detection? {
        val s = samples.map { it.trim() }.filter { it.isNotEmpty() }.take(200)
        if (s.isEmpty()) return null
        val fits = listOf(DateOrder.DMY, DateOrder.YMD, DateOrder.MDY).filter { o -> s.all { parse(it, o) != null } }
        return fits.firstOrNull()?.let { Detection(it, ambiguous = fits.size > 1) }
    }
}

object StatementAmounts {
    private val INDIAN_GROUPING = Regex("^\\d{1,3}(,\\d{2})*,\\d{3}$")
    private val WESTERN_GROUPING = Regex("^\\d{1,3}(,\\d{3})+$")
    private val DIRECTION = Regex("(?i)(?:^|\\s)(dr|cr|debit|credit)\\.?\\s*$|^(dr|cr|debit|credit)\\.?\\s")
    private val CURRENCY = Regex("(?i)₹|\\brs\\.?|\\binr\\b")
    private val NO_VALUE = setOf("-", "--", "---", "na", "n/a", "nil", "null")

    fun parse(text: String, digits: Int = 2): AmountCell {
        var t = text.replace('\u00A0', ' ').trim()
        if (t.isEmpty() || t.lowercase() in NO_VALUE) return AmountCell.Blank
        var flow: Flow? = null
        DIRECTION.find(t)?.let { m ->
            val word = (m.groups[1] ?: m.groups[2])!!.value.lowercase()
            flow = if (word == "dr" || word == "debit") Flow.SPENDING else Flow.INCOME
            t = t.removeRange(m.range).trim()
        }
        t = t.replace(CURRENCY, "").replace(" ", "").trim()
        var negative = false
        if (t.startsWith("(") && t.endsWith(")")) { negative = true; t = t.substring(1, t.length - 1) }
        if (t.startsWith("-") || t.startsWith("−")) { negative = true; t = t.substring(1) } else if (t.startsWith("+")) t = t.substring(1)
        if (t.endsWith("-")) { negative = !negative; t = t.dropLast(1) }
        if (t.isEmpty()) return AmountCell.Invalid
        val whole = t.substringBefore('.').ifEmpty { if ('.' in t) "0" else "" }
        val fraction = if ('.' in t) t.substringAfter('.') else ""
        if (fraction.contains(',') || fraction.any { !it.isDigit() } || fraction.length > digits || t.count { it == '.' } > 1) return AmountCell.Invalid
        val groupedOk = whole.all { it.isDigit() } || INDIAN_GROUPING.matches(whole) || WESTERN_GROUPING.matches(whole)
        val digitsOnly = whole.replace(",", "")
        if (!groupedOk || digitsOnly.isEmpty() || digitsOnly.length > 15) return AmountCell.Invalid
        val minor = (digitsOnly + fraction.padEnd(digits, '0')).toLongOrNull() ?: return AmountCell.Invalid
        return AmountCell.Value(minor, negative, flow)
    }
}

data class StatementMapping(
    val headerRows: Int,
    val dateColumn: Int?,
    val dateOrder: DateOrder,
    val descriptionColumn: Int?,
    val layout: AmountLayout,
    val debitColumn: Int? = null,
    val creditColumn: Int? = null,
    val amountColumn: Int? = null,
    val typeColumn: Int? = null,
    val sign: SignConvention = SignConvention.NEGATIVE_IS_SPENDING,
) {
    /** What still has to be chosen before the file can be read, or null when the mapping is complete. */
    fun missing(): String? = when {
        dateColumn == null -> "Choose the date column."
        layout == AmountLayout.SEPARATE_DEBIT_CREDIT && (debitColumn == null || creditColumn == null) -> "Choose the debit and the credit column."
        layout == AmountLayout.SIGNED_AMOUNT && amountColumn == null -> "Choose the amount column."
        layout == AmountLayout.AMOUNT_WITH_TYPE && (amountColumn == null || typeColumn == null) -> "Choose the amount column and the Dr/Cr column."
        else -> null
    }
}

data class StatementTxn(val row: Int, val date: LocalDate, val flow: Flow, val minor: Long, val description: String)

/** A row that looked like a transaction but could not be read. [row] is its line number in the file's table, counting from 1. */
data class StatementProblem(val row: Int, val reason: String)

/** [ignoredRows] are rows that are not transactions (opening and closing balances, totals, notes) or have no amount. */
/** [repairedRows] counts rows whose description held unquoted commas and was put back together (see [StatementParser.rejoin]). */
data class ParsedStatement(val txns: List<StatementTxn>, val problems: List<StatementProblem>, val ignoredRows: Int, val repairedRows: Int = 0)

object StatementParser {
    private val DEBIT_WORDS = Regex("(?i)^(dr|d|debit|withdrawal|withdraw|out|paid out)\\b.*|^debit.*")
    private val CREDIT_WORDS = Regex("(?i)^(cr|c|credit|deposit|in|paid in)\\b.*|^credit.*")

    /**
     * Some banks (HDFC's text export is one) write the narration without quotes, so a comma inside it ("NETBANK, MUM") splits it and every column after
     * it moves one place right: the debit then reads as a date and the row is refused. A row with more cells than the header has is put back together by
     * folding the surplus cells into the description column; cells that are empty at the end (a trailing comma) are dropped first, since those are not
     * part of any text. Returns the rows and how many were repaired.
     */
    fun rejoin(rows: List<List<String>>, m: StatementMapping): Pair<List<List<String>>, Int> {
        val desc = m.descriptionColumn
        if (m.headerRows == 0 || desc == null) return rows to 0
        val width = rows[m.headerRows - 1].size
        if (desc >= width) return rows to 0
        var repaired = 0
        val out = rows.mapIndexed { i, r ->
            if (i < m.headerRows || r.size <= width) return@mapIndexed r
            val trimmed = r.dropLastWhile { it.isBlank() }.let { if (it.size < width) it + List(width - it.size) { "" } else it }
            if (trimmed.size <= width) return@mapIndexed trimmed
            val surplus = trimmed.size - width
            repaired++
            trimmed.subList(0, desc) + trimmed.subList(desc, desc + surplus + 1).joinToString(",") + trimmed.subList(desc + surplus + 1, trimmed.size)
        }
        return out to repaired
    }

    /** [lineOf] turns a row's position in [rows] (from 0) into the line shown to the user; by default the position plus one. */
    fun parse(inputRows: List<List<String>>, m: StatementMapping, digits: Int = 2, lineOf: (Int) -> Int = { it + 1 }): ParsedStatement {
        require(m.missing() == null) { m.missing()!! }
        val (rows, repairedRows) = rejoin(inputRows, m)
        val txns = ArrayList<StatementTxn>()
        val problems = ArrayList<StatementProblem>()
        var ignored = 0
        fun cell(r: List<String>, c: Int?) = c?.let { r.getOrNull(it) }.orEmpty().trim()

        for ((index, r) in rows.withIndex()) {
            if (index < m.headerRows) continue
            val line = lineOf(index)
            val dateText = cell(r, m.dateColumn)
            val description = cell(r, m.descriptionColumn).replace(Regex("\\s+"), " ").take(MAX_DESCRIPTION)

            // one amount cell per layout, turned into (flow, minor), or blank, or a reason it cannot be read
            var blank = false
            var reason: String? = null
            var flow: Flow? = null
            var minor = 0L
            when (m.layout) {
                AmountLayout.SEPARATE_DEBIT_CREDIT -> {
                    val d = StatementAmounts.parse(cell(r, m.debitColumn), digits)
                    val c = StatementAmounts.parse(cell(r, m.creditColumn), digits)
                    val dv = (d as? AmountCell.Value)?.takeIf { it.minor > 0 }
                    val cv = (c as? AmountCell.Value)?.takeIf { it.minor > 0 }
                    when {
                        d is AmountCell.Invalid -> reason = "The debit '${cell(r, m.debitColumn)}' is not an amount."
                        c is AmountCell.Invalid -> reason = "The credit '${cell(r, m.creditColumn)}' is not an amount."
                        dv != null && cv != null -> reason = "Both the debit and the credit are filled in."
                        dv != null -> { flow = Flow.SPENDING; minor = dv.minor }
                        cv != null -> { flow = Flow.INCOME; minor = cv.minor }
                        else -> blank = true
                    }
                }
                AmountLayout.SIGNED_AMOUNT -> when (val a = StatementAmounts.parse(cell(r, m.amountColumn), digits)) {
                    AmountCell.Blank -> blank = true
                    AmountCell.Invalid -> reason = "The amount '${cell(r, m.amountColumn)}' is not an amount."
                    is AmountCell.Value -> if (a.minor == 0L) blank = true else {
                        minor = a.minor
                        flow = a.flow ?: when (m.sign) {
                            SignConvention.NEGATIVE_IS_SPENDING -> if (a.negative) Flow.SPENDING else Flow.INCOME
                            SignConvention.POSITIVE_IS_SPENDING -> if (a.negative) Flow.INCOME else Flow.SPENDING
                        }
                    }
                }
                AmountLayout.AMOUNT_WITH_TYPE -> when (val a = StatementAmounts.parse(cell(r, m.amountColumn), digits)) {
                    AmountCell.Blank -> blank = true
                    AmountCell.Invalid -> reason = "The amount '${cell(r, m.amountColumn)}' is not an amount."
                    is AmountCell.Value -> if (a.minor == 0L) blank = true else {
                        val type = cell(r, m.typeColumn)
                        minor = a.minor
                        flow = when {
                            DEBIT_WORDS.matches(type) -> Flow.SPENDING
                            CREDIT_WORDS.matches(type) -> Flow.INCOME
                            else -> { reason = "The type '$type' is neither debit nor credit."; null }
                        }
                    }
                }
            }

            val date = if (dateText.isEmpty()) null else StatementDates.parse(dateText, m.dateOrder)
            when {
                blank -> ignored++ // a balance line, a total, a note: no amount, so not a transaction
                reason != null -> if (dateText.isEmpty() && description.isEmpty()) ignored++ else problems += StatementProblem(line, reason)
                dateText.isEmpty() -> problems += StatementProblem(line, "The date is missing.")
                date == null -> problems += StatementProblem(line, "The date '$dateText' is not a date in the chosen format.")
                else -> txns += StatementTxn(line, date, flow!!, minor, description)
            }
        }
        return ParsedStatement(txns, problems, ignored, repairedRows)
    }

    private const val MAX_DESCRIPTION = 200
}

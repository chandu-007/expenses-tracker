package app.expenses.core.domain

/**
 * Turns the text read out of a statement PDF, or recognised from a photo of one, into a [CsvTable] with the header `Date,Description,Amount,Type`, so the
 * screen that reads CSV and Excel statements (the detector, the preview, the column choices, the importer) runs unchanged on it.
 *
 * What the text looks like is an assumption: **no real bank statement or card statement was available when this was written.** The layouts handled are the
 * common ones in Indian statements as described by their makers and users: a line that starts with a date (`12/09/2025`, `12-09-25`, `12 Sep 2025`,
 * `12-Sep-25`, `Sep 12, 2025`, `2025-09-12`, with an optional time and an optional second date), then the description, then the amount, with `Dr`/`Cr`
 * after it or a minus sign, and on a bank statement usually the running balance after that. Other layouts give fewer rows, and the preview, which the person must
 * check, shows what was read; lines that start with a date but cannot be read are reported by the importer, never guessed.
 *
 * Money in or out is decided here, once per line, from what the statement says, and nothing else:
 *  1. a `Dr`/`Cr` (or `D`/`C`, `Debit`/`Credit`) mark on the amount, the same marks the CSV reader accepts;
 *  2. a bank statement's running balance: the amount is money in when the balance went up by exactly that amount since the line before, out when it went
 *     down by it. With three amounts the first two are the debit and credit columns (one of them 0.00) and the third the balance;
 *  3. a minus sign or brackets, read with the sign convention of the statement: on a bank statement a minus is spending, on a credit card statement a minus
 *     is a credit (a refund);
 *  4. no mark at all: a credit card statement shows purchases without a mark, so an unmarked amount is spending; on any other statement an unmarked amount is
 *     income, unless nothing at all in the statement says which way money went (no mark, no minus), in which case every amount is spending. That is the rule
 *     [StatementDetector] applies to a signed CSV column.
 * A line whose direction none of those settles is passed on with the type `?`, and the importer reports it as a row that could not be read.
 *
 * On a credit card statement a line such as "PAYMENT RECEIVED - THANK YOU" is the person paying the card's bill from their bank, not income, so it is left
 * out and counted in [Result.cardPayments]. Refunds, reversals and cashback credits are kept as income.
 */
object StatementTextParser {
    enum class Convention { BANK, CARD }

    /**
     * [table] has the header row and one row per dated line; [rawLines] holds, for each row of [table] (the header's is empty), the statement text it was
     * read from, and [cardPayments] counts the payments to the card that were left out. [note] says in words how the direction was read.
     */
    data class Result(val table: CsvTable, val rawLines: List<String>, val cardPayments: Int, val convention: Convention, val note: String) {
        /** Rows after the header. Some of them may have no amount or no direction; the importer counts those separately. */
        val rowCount: Int get() = table.rows.size - 1
    }

    val HEADER = listOf("Date", "Description", "Amount", "Type")

    // ---- recognising the parts of a line ----

    private const val MON = "(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*"
    private val DATE_NUM = Regex("^(\\d{1,2}[/.\\-]\\d{1,2}[/.\\-](?:\\d{4}|\\d{2}))(?!\\d)")
    private val DATE_ISO = Regex("^(\\d{4}-\\d{2}-\\d{2})(?!\\d)")
    private val DATE_DMON = Regex("^(\\d{1,2})[\\s.\\-/]*($MON)[\\s.,\\-/']*(\\d{4}|\\d{2})(?!\\d)", RegexOption.IGNORE_CASE)
    private val DATE_MONDY = Regex("^($MON)\\.?\\s+(\\d{1,2}),?\\s+(\\d{4})(?!\\d)", RegexOption.IGNORE_CASE)
    private val TIME = Regex("^\\s*\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s*[ap]\\.?m\\.?)?(?![\\d:])", RegexOption.IGNORE_CASE)
    private val TRAILING_DATE = Regex("\\s+\\d{1,2}[/.\\-]\\d{1,2}[/.\\-](?:\\d{4}|\\d{2})$")

    private const val CUR = "(?:₹|rs\\.?|inr)"
    private const val NUMBER = "\\d[\\d,]*\\.\\d{1,2}"
    private const val TOKEN = "[-+−]?\\(?(?:$CUR\\s*)?[-+−]?$NUMBER\\)?[-−]?(?:\\s*(?:dr|cr|d|c|debit|credit)\\b\\.?)?"
    private val TAIL = Regex("(?:^|\\s)($TOKEN)\\s*$", RegexOption.IGNORE_CASE)
    private val MARKER = Regex("\\s*(dr|cr|d|c|debit|credit)\\b\\.?$", RegexOption.IGNORE_CASE)
    private val NUMBER_RE = Regex(NUMBER)

    private const val CODES = "USD|EUR|GBP|AED|SGD|AUD|CAD|CHF|JPY|HKD|THB|MYR|NZD|SAR|QAR|KWD|BHD|OMR|CNY|ZAR|SEK|NOK|DKK|KRW|IDR|PHP|LKR|NPR|BDT|TRY|RUB"
    private val FOREIGN_BEFORE = Regex("\\b($CODES)\\s*(\\d[\\d,]*\\.\\d{1,2})", RegexOption.IGNORE_CASE)
    private val FOREIGN_AFTER = Regex("(\\d[\\d,]*\\.\\d{1,2})\\s*\\b($CODES)\\b", RegexOption.IGNORE_CASE)
    private val FOLDED = Regex("\\b([A-Za-z]{3})_(\\d)")

    /** Lines that are never transactions, even when they start with a date: balances carried over, totals and what is due. */
    private val SUMMARY = Regex(
        "\\b(opening|closing)\\s*(balance|bal)\\b|\\bbalance\\s*(brought|carried|b/?f|c/?f)|\\b(brought|carried)\\s*forward\\b|\\bb/f\\b|\\bc/f\\b|" +
            "\\bprevious\\s*(statement\\s*)?(balance|dues?)\\b|\\b(total|minimum)\\s*(amount\\s*)?dues?\\b|\\bgrand\\s*total\\b|\\bsub\\s*total\\b|" +
            "\\btotal\\s*(purchases?|debits?|credits?|payments?|spends?|withdrawals?|deposits?|amount|of|dr|cr)\\b|\\bnet\\s*(outstanding|amount\\s*due)\\b",
        RegexOption.IGNORE_CASE,
    )

    /** Lines of the page furniture that only make sense without a date: reward points, limits, the statement period, page numbers. */
    private val FURNITURE = Regex(
        "\\breward\\s*points?\\b|\\bpoints\\s*(earned|balance|expiring|summary)\\b|\\bcredit\\s*limit\\b|\\bavailable\\s*(credit|cash|limit)\\b|" +
            "\\bstatement\\s*(date|period|summary)\\b|\\b(account|payment)\\s*summary\\b|\\bpayment\\s*due\\b|\\bpage\\s*(no\\.?\\s*)?\\d+\\s*(of|/)?\\s*\\d*|\\bpage\\s*no\\b",
        RegexOption.IGNORE_CASE,
    )
    private val OPENING = Regex("\\b(opening\\s*(balance|bal)|balance\\s*(brought|b/?f)|brought\\s*forward|b/f|previous\\s*balance)\\b", RegexOption.IGNORE_CASE)
    private val PROSE = Regex(
        "statement|generated|signature|www\\.|https?:|@|customer\\s*care|toll|registered|gstin|important|terms|address|e-?mail|please|kindly|contact|" +
            "pin\\s*code|continued|\\bend of\\b|\\bnote\\b|\\bnominee\\b|\\bcin\\b|\\bifsc\\b|\\bmicr\\b",
        RegexOption.IGNORE_CASE,
    )
    private val HEADER_WORDS = setOf(
        "date", "dt", "description", "narration", "particulars", "particular", "details", "detail", "transaction", "transactions", "txn", "tran", "amount", "amt",
        "balance", "bal", "debit", "debits", "credit", "credits", "withdrawal", "withdrawals", "deposit", "deposits", "dr", "cr", "ref", "no", "chq", "cheque",
        "value", "type", "merchant", "name", "reward", "points", "sr", "in", "of", "and", "inr", "rs", "number", "remarks", "posting", "post", "trans",
    )
    private val WORD = Regex("[A-Za-z]{2,}")
    private val HEADER_FILLER = setOf("in", "of", "and", "no", "name", "number", "type", "sr")
    private val PAYMENT_TO_CARD = Regex(
        "\\bpayment\\s*(received|recd|rec'?d|recvd|thank|-\\s*thank|thanks)|thank\\s*you\\s*for\\s*(your\\s*)?payment|\\bbbps\\b.*\\bpayment|\\bpayment\\b.*\\bbbps\\b|" +
            "\\bbppy\\b|\\bcc\\s*payment\\b|\\bcredit\\s*card\\s*payment\\b",
        RegexOption.IGNORE_CASE,
    )
    private val CARD_WORDS = Regex(
        "\\b(total|minimum)\\s*(amount\\s*)?dues?\\b|\\bcredit\\s*limit\\b|\\bpayment\\s*due\\s*date\\b|\\bavailable\\s*credit\\b|\\bcredit\\s*card\\b|\\bcard\\s*(statement|no|number)\\b",
        RegexOption.IGNORE_CASE,
    )

    private const val MAX_CONTINUATION = 3
    private const val MAX_CONTINUATION_CHARS = 70

    /** Whether the words of the statement say it is a credit card statement. */
    fun looksLikeCardStatement(text: String): Boolean = CARD_WORDS.containsMatchIn(text)

    // ---- reading ----

    private class Tok(val minor: Long, val negative: Boolean, val marker: Flow?, val raw: String, val valid: Boolean)

    private class Item(val date: String, val firstLine: Int, var description: String, val raw: MutableList<String>, var tokens: List<Tok>, var continued: Int = 0)

    private class Opening(val balance: Long)

    private fun normalise(line: String): String = line.replace(Regex("[\\u00A0\\u2007\\u202F\\t|]+"), " ").replace(Regex("\\s+"), " ").trim()

    /** The date at the start of [line] as written (month names spaced out), and how many characters it took; null when the line does not start with a real date. */
    private fun leadingDate(line: String): Pair<String, Int>? {
        val (text, length) = when {
            DATE_ISO.find(line) != null -> DATE_ISO.find(line)!!.let { it.groupValues[1] to it.value.length }
            DATE_NUM.find(line) != null -> DATE_NUM.find(line)!!.let { it.groupValues[1] to it.value.length }
            DATE_DMON.find(line) != null -> DATE_DMON.find(line)!!.let { "${it.groupValues[1]} ${it.groupValues[2]} ${it.groupValues[3]}" to it.value.length }
            DATE_MONDY.find(line) != null -> DATE_MONDY.find(line)!!.let { "${it.groupValues[1]} ${it.groupValues[2]} ${it.groupValues[3]}" to it.value.length }
            else -> return null
        }
        val real = DateOrder.entries.any { StatementDates.parse(text, it) != null }
        return if (real) text to length else null
    }

    private fun token(raw: String): Tok {
        val marker = MARKER.find(raw)
        val flow = marker?.let { if (it.groupValues[1].lowercase().startsWith("d")) Flow.SPENDING else Flow.INCOME }
        val body = if (marker != null) raw.removeRange(marker.range) else raw
        val negative = body.any { it == '-' || it == '−' || it == '(' }
        val digits = NUMBER_RE.find(body)?.value
        val cell = digits?.let { StatementAmounts.parse(it) }
        return if (cell is AmountCell.Value) Tok(cell.minor, negative, flow, raw.trim(), valid = true) else Tok(0, negative, flow, raw.trim(), valid = false)
    }

    /** Takes up to three amounts off the end of [body]: the description that is left, and the amounts in the order they were written. */
    private fun peel(body: String): Pair<String, List<Tok>> {
        // the foreign amount is tied to its currency code with an underscore so it is not taken for the rupee amount; "12.00 USD 1,019.40" is done first, so that
        // the code is not tied to the rupee amount after it
        val tied = FOREIGN_AFTER.replace(" $body") { "${it.groupValues[2]}_${it.groupValues[1]}" }
        var rest = FOREIGN_BEFORE.replace(tied) { "${it.groupValues[1]}_${it.groupValues[2]}" }
        val tokens = ArrayList<Tok>()
        while (tokens.size < 3) {
            val m = TAIL.find(rest) ?: break
            tokens.add(0, token(m.groupValues[1]))
            rest = rest.substring(0, m.range.first)
        }
        return rest.trim() to tokens
    }

    private fun isHeader(line: String): Boolean {
        if (line.all { !it.isLetterOrDigit() }) return true
        val words = WORD.findAll(line).map { it.value.lowercase() }.toList()
        // "IN" or "OF" alone is the end of a place name that wrapped, not a heading
        return words.isNotEmpty() && words.all { it in HEADER_WORDS } && words.any { it !in HEADER_FILLER }
    }

    private fun cleanDescription(text: String): String {
        var d = FOLDED.replace(text) { "${it.groupValues[1]} ${it.groupValues[2]}" }.replace(Regex("\\s+"), " ").trim()
        d = TRAILING_DATE.replace(d, "").trim()
        return d.trim('-', ':', ',', '/', ' ', '*').trim()
    }

    private fun amountText(minor: Long): String = "${minor / 100}.${(minor % 100).toString().padStart(2, '0')}"

    /**
     * Reads [text] line by line. [convention] says how an amount with no mark is read; when null it is a credit card statement if the words in [text] say
     * so ([looksLikeCardStatement]) and a bank statement otherwise. Pass [Convention.CARD] when the person chose a credit card account.
     */
    fun parse(text: String, convention: Convention? = null): Result {
        val effective = convention ?: if (looksLikeCardStatement(text)) Convention.CARD else Convention.BANK
        val card = effective == Convention.CARD
        val events = ArrayList<Any>()
        var pending: Item? = null
        fun flush() { pending?.let { events += it }; pending = null }

        for ((index, rawLine) in text.lines().withIndex()) {
            val raw = rawLine.trim()
            val line = normalise(rawLine)
            if (line.isEmpty()) continue
            val date = leadingDate(line)
            val summary = SUMMARY.containsMatchIn(line)
            if (summary || (date == null && FURNITURE.containsMatchIn(line))) {
                flush()
                if (summary && OPENING.containsMatchIn(line)) {
                    val (_, tokens) = peel(line)
                    tokens.singleOrNull()?.takeIf { it.valid }?.let { events += Opening(if (it.marker == Flow.SPENDING || it.negative) -it.minor else it.minor) }
                }
                continue
            }
            if (date != null) {
                flush()
                var rest = line.substring(date.second)
                // a time of day, then a second date (the value or posting date), as many statements print both
                while (true) {
                    val time = TIME.find(rest)
                    if (time != null) { rest = rest.substring(time.range.last + 1); continue }
                    val second = leadingDate(rest.trim())
                    if (second != null) { rest = rest.trim().substring(second.second); continue }
                    break
                }
                val (description, tokens) = peel(rest.trim())
                pending = Item(date.first, index + 1, description, mutableListOf(raw), tokens)
                continue
            }
            // no date: the next line of a description that wrapped, unless it is page furniture
            val p = pending ?: continue
            if (isHeader(line) || PROSE.containsMatchIn(line) || line.length > MAX_CONTINUATION_CHARS || p.continued >= MAX_CONTINUATION) { flush(); continue }
            val (description, tokens) = peel(line)
            if (p.tokens.isEmpty() && tokens.isNotEmpty()) {
                p.tokens = tokens
                p.description = "${p.description} $description"
            } else if (tokens.isNotEmpty()) {
                flush() // another line with amounts and no date: a total or a note, not part of this one
                continue
            } else p.description = "${p.description} $description"
            p.raw += raw
            p.continued++
        }
        flush()

        val items = events.filterIsInstance<Item>()
        val anyDirection = items.any { i -> i.tokens.firstOrNull()?.let { it.marker != null || it.negative } == true }
        val rows = ArrayList<List<String>>()
        val raws = ArrayList<String>()
        val lineNumbers = ArrayList<Int>()
        rows += HEADER; raws += ""; lineNumbers += 0
        var payments = 0
        var previousBalance: Long? = null
        var usedBalance = false

        for (e in events) {
            if (e is Opening) { previousBalance = e.balance; continue }
            val item = e as Item
            val description = cleanDescription(item.description)
            if (card && PAYMENT_TO_CARD.containsMatchIn(description)) { payments++; continue }
            val tokens = item.tokens
            var amount: Tok? = null
            var flow: Flow? = null
            when (tokens.size) {
                0 -> {}
                1 -> {
                    val t = tokens[0]; amount = t
                    flow = t.marker ?: when {
                        card -> if (t.negative) Flow.INCOME else Flow.SPENDING
                        anyDirection -> if (t.negative) Flow.SPENDING else Flow.INCOME
                        else -> Flow.SPENDING
                    }
                }
                2 -> if (!card) {
                    val (a, b) = tokens
                    amount = a
                    val balance = if (b.valid) (if (b.marker == Flow.SPENDING || b.negative) -b.minor else b.minor) else null
                    val before = previousBalance
                    flow = a.marker ?: if (balance != null && before != null && a.valid) {
                        when (balance - before) { a.minor -> Flow.INCOME; -a.minor -> Flow.SPENDING; else -> null }
                    } else null
                    if (balance != null) { previousBalance = balance; usedBalance = true }
                } else amount = tokens[0]
                else -> {
                    val (debit, credit, balance) = tokens
                    val debitUsed = debit.valid && debit.minor > 0
                    val creditUsed = credit.valid && credit.minor > 0
                    when {
                        debitUsed && !creditUsed -> { amount = debit; flow = Flow.SPENDING }
                        creditUsed && !debitUsed -> { amount = credit; flow = Flow.INCOME }
                        else -> amount = debit
                    }
                    if (balance.valid) { previousBalance = if (balance.marker == Flow.SPENDING || balance.negative) -balance.minor else balance.minor; usedBalance = true }
                }
            }
            val amountCell = when {
                amount == null -> ""
                amount.valid -> amountText(amount.minor)
                else -> amount.raw
            }
            val type = when {
                amount == null -> ""
                flow == Flow.SPENDING -> "Dr"
                flow == Flow.INCOME -> "Cr"
                else -> "?"
            }
            rows += listOf(item.date, description, amountCell, type)
            raws += item.raw.joinToString("\n")
            lineNumbers += item.firstLine
        }

        val note = when {
            card -> "Read as a credit card statement: amounts with no mark are spending; Cr or a minus is money back. Payments of the card's bill are left out."
            usedBalance -> "Money in or out was read from the Dr/Cr marks and from the running balance."
            anyDirection -> "Money in or out was read from the Dr/Cr marks and minus signs."
            else -> "The statement does not say which lines are money in or out, so every amount is read as spending."
        }
        return Result(CsvTable(rows, ',', false, lineNumbers), raws, payments, effective, note)
    }
}

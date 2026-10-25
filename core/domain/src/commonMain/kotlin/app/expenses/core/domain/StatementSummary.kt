package app.expenses.core.domain

/**
 * The totals a statement prints about itself, read from its text so the import can say when fewer lines were found than the statement counted.
 *
 * ASSUMED layouts (no real statement was available; they come from descriptions of them, so check against yours):
 *  - a savings-account summary: a heading line naming `Opening Balance`, `Dr Count`, `Cr Count`, `Debits`, `Credits`, `Closing Bal` and, on the next line, one
 *    number for each heading in the same order;
 *  - a credit card summary: a heading line naming `Payments/Credits` and `Purchases/Debit` (optionally with an opening balance, charges and total dues) and
 *    on the next line one amount for each heading, in order, with `+`, `-`, `=` between them;
 *  - lines such as `Total Debits 1,234.00` and `Total Credits 5,000.00` (also withdrawals/deposits).
 * A summary is used only when its numbers line up with the headings; otherwise none is reported, never a guess.
 */
object StatementSummary {
    /** [debitMinor] is money out (purchases on a card), [creditMinor] money in (payments and refunds on a card); either may be unknown. */
    data class Summary(val debitMinor: Long?, val creditMinor: Long?, val debitCount: Int? = null, val creditCount: Int? = null)

    private val NUM = Regex("\\d[\\d,]*(?:\\.\\d{1,2})?")
    private val AMOUNT = Regex("\\d[\\d,]*\\.\\d{2}(?!\\d)")

    private fun minor(text: String): Long? = (StatementAmounts.parse(text.replace(",", "")) as? AmountCell.Value)?.minor

    private fun normalise(line: String): String = line.replace(Regex("[\\u00A0\\u2007\\u202F\\t|]+"), " ").replace(Regex("\\s+"), " ").trim()

    private enum class Col { OPENING, DR_COUNT, CR_COUNT, DEBITS, CREDITS, CLOSING }
    private val SAVINGS_HEADS = listOf(
        Col.OPENING to Regex("opening\\s*bal(?:ance)?", RegexOption.IGNORE_CASE),
        Col.DR_COUNT to Regex("dr\\s*count", RegexOption.IGNORE_CASE),
        Col.CR_COUNT to Regex("cr\\s*count", RegexOption.IGNORE_CASE),
        Col.DEBITS to Regex("debits?\\b", RegexOption.IGNORE_CASE),
        Col.CREDITS to Regex("credits?\\b", RegexOption.IGNORE_CASE),
        Col.CLOSING to Regex("closing\\s*bal(?:ance)?", RegexOption.IGNORE_CASE),
    )

    private enum class CardCol { OPENING, PAYMENTS, PURCHASES, CHARGES, DUES }
    private val CARD_HEADS = listOf(
        CardCol.OPENING to Regex("(?:opening|previous)\\s*(?:bal(?:ance)?|dues?)", RegexOption.IGNORE_CASE),
        CardCol.PAYMENTS to Regex("payments?\\s*/?\\s*credits?", RegexOption.IGNORE_CASE),
        CardCol.PURCHASES to Regex("purchases?\\s*/?\\s*debits?", RegexOption.IGNORE_CASE),
        CardCol.CHARGES to Regex("finance\\s*charges?", RegexOption.IGNORE_CASE),
        CardCol.DUES to Regex("total\\s*dues?", RegexOption.IGNORE_CASE),
    )

    /** Headings found on [line] in the order they are written, or empty. */
    private fun <T> heads(line: String, defs: List<Pair<T, Regex>>): List<T> =
        defs.mapNotNull { (c, re) -> re.find(line)?.let { it.range.first to c } }.sortedBy { it.first }.map { it.second }

    fun find(text: String): Summary? {
        val lines = text.lines().map { normalise(it) }
        for ((i, line) in lines.withIndex()) {
            if (line.isEmpty()) continue
            val next = lines.drop(i + 1).take(2).firstOrNull { it.isNotEmpty() }

            val sav = heads(line, SAVINGS_HEADS)
            if (next != null && Col.DEBITS in sav && Col.CREDITS in sav && sav.size >= 4) {
                val numbers = NUM.findAll(next).map { it.value }.toList()
                if (numbers.size == sav.size && next.none { it.isLetter() }) {
                    fun at(c: Col) = sav.indexOf(c).takeIf { it >= 0 }?.let { numbers[it] }
                    val debit = at(Col.DEBITS)?.let(::minor); val credit = at(Col.CREDITS)?.let(::minor)
                    if (debit != null && credit != null) return Summary(debit, credit, at(Col.DR_COUNT)?.toIntOrNull(), at(Col.CR_COUNT)?.toIntOrNull())
                }
            }

            val card = heads(line, CARD_HEADS)
            if (next != null && CardCol.PAYMENTS in card && CardCol.PURCHASES in card) {
                val amounts = AMOUNT.findAll(next.replace(Regex("(^|\\s)[C₹]\\s?(?=\\d)"), "$1")).map { it.value }.toList()
                if (amounts.size == card.size) {
                    val credit = minor(amounts[card.indexOf(CardCol.PAYMENTS)]); val debit = minor(amounts[card.indexOf(CardCol.PURCHASES)])
                    if (debit != null && credit != null) return Summary(debit, credit)
                }
            }
        }
        return totalsLines(lines)
    }

    private val TOTAL_DEBITS = Regex("total\\s*(?:debits?|withdrawals?|dr)\\b[^\\d\\n]{0,25}(\\d[\\d,]*\\.\\d{2})(?!\\d)", RegexOption.IGNORE_CASE)
    private val TOTAL_CREDITS = Regex("total\\s*(?:credits?|deposits?|cr)\\b[^\\d\\n]{0,25}(\\d[\\d,]*\\.\\d{2})(?!\\d)", RegexOption.IGNORE_CASE)

    private fun totalsLines(lines: List<String>): Summary? {
        var debit: Long? = null
        var credit: Long? = null
        for (line in lines) {
            if (debit == null) TOTAL_DEBITS.find(line)?.let { debit = minor(it.groupValues[1]) }
            if (credit == null) TOTAL_CREDITS.find(line)?.let { credit = minor(it.groupValues[1]) }
        }
        return if (debit == null && credit == null) null else Summary(debit, credit)
    }

    /**
     * What was found against what the statement printed, as one sentence for the person, or null when they agree (or the statement printed nothing to compare).
     * [foundCredit] on a card statement must already include the payments to the card that the import left out.
     */
    fun check(summary: Summary?, foundDebitMinor: Long, foundDebitCount: Int, foundCreditMinor: Long, foundCreditCount: Int): String? {
        if (summary == null) return null
        fun money(m: Long) = Money(m, Currency.INR).formatted()
        fun side(word: String, printed: Long?, printedCount: Int?, found: Long, foundCount: Int): String? {
            if (printed == null) return null
            val same = printed == found && (printedCount == null || printedCount == foundCount)
            if (same) return null
            return if (printedCount != null) "The statement says $printedCount $word totalling ${money(printed)} but $foundCount were found (${money(found)})."
            else "The statement says $word total ${money(printed)} but the lines found add up to ${money(found)}."
        }
        val parts = listOfNotNull(
            side("debits", summary.debitMinor, summary.debitCount, foundDebitMinor, foundDebitCount),
            side("credits", summary.creditMinor, summary.creditCount, foundCreditMinor, foundCreditCount),
        )
        return if (parts.isEmpty()) null else parts.joinToString(" ") + " Some lines may be missing: check the preview."
    }
}

package app.expenses.core.domain

/**
 * Guesses how a bank statement is laid out from its header row and its first rows of data, so most files import in one tap. A guess is
 * only a starting point: the screen shows what was understood and lets the user change it before anything is saved.
 */
object StatementDetector {
    private val DATE = listOf("transaction date", "txn date", "tran date", "trans date", "posting date", "post date", "date", "value date", "value dt", "txn dt")
    private val DESCRIPTION = listOf("description", "narration", "particulars", "transaction details", "transaction remarks", "transaction description", "details", "remarks", "merchant", "payee")
    private val DEBIT = listOf("debit", "debit amount", "debit amt", "withdrawal", "withdrawal amt", "withdrawals", "withdrawal amount", "dr", "dr amount", "paid out")
    private val CREDIT = listOf("credit", "credit amount", "credit amt", "deposit", "deposit amt", "deposits", "deposit amount", "cr", "cr amount", "paid in")
    private val AMOUNT = listOf("amount", "transaction amount", "txn amount", "amt", "amount inr", "amount in inr")
    private val TYPE = listOf("dr cr", "cr dr", "debit credit", "credit debit", "transaction type", "txn type", "type", "drcr", "crdr")
    private const val HEADER_SEARCH_ROWS = 40
    private const val SAMPLE_ROWS = 100

    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    /** The first column whose header equals one of [keys] (earlier keys win), or starts with one followed by a space ("withdrawal amt inr"). */
    private fun find(header: List<String>, keys: List<String>, exclude: Set<Int> = emptySet()): Int? {
        val h = header.map(::norm)
        for (k in keys) h.indexOfFirst { it == k }.takeIf { it >= 0 && it !in exclude }?.let { return it }
        for (k in keys) h.indices.firstOrNull { i -> i !in exclude && h[i].startsWith("$k ") }?.let { return it }
        return null
    }

    private fun score(row: List<String>): Int =
        listOf(DATE, DESCRIPTION, DEBIT, CREDIT, AMOUNT, TYPE).count { keys -> find(row, keys) != null }

    fun detect(rows: List<List<String>>): StatementMapping {
        val headerIndex = rows.take(HEADER_SEARCH_ROWS).indexOfFirst { score(it) >= 2 && find(it, DATE) != null }
        if (headerIndex < 0) return guessWithoutHeader(rows)
        val header = rows[headerIndex]
        val data = rows.drop(headerIndex + 1).take(SAMPLE_ROWS)
        val date = find(header, DATE)
        val used = listOfNotNull(date).toMutableSet()
        val description = find(header, DESCRIPTION, used)?.also { used += it }
        // The Dr/Cr column first: a header such as "Dr/Cr" would otherwise be taken for the debit column, which it starts like.
        val type = find(header, TYPE, used)?.also { used += it }
        val debit = find(header, DEBIT, used)?.also { used += it }
        val credit = find(header, CREDIT, used)?.also { used += it }
        val amount = find(header, AMOUNT, used)?.also { used += it }
        val layout = when {
            debit != null && credit != null -> AmountLayout.SEPARATE_DEBIT_CREDIT
            amount != null && type != null -> AmountLayout.AMOUNT_WITH_TYPE
            amount != null -> AmountLayout.SIGNED_AMOUNT
            else -> AmountLayout.SEPARATE_DEBIT_CREDIT
        }
        val detected = date?.let { c -> StatementDates.detect(data.mapNotNull { it.getOrNull(c) }) }
        // A signed column with no negative numbers at all is most likely a credit card, where purchases are positive.
        val sign = if (layout == AmountLayout.SIGNED_AMOUNT && amount != null) {
            val cells = data.mapNotNull { r -> (StatementAmounts.parse(r.getOrNull(amount).orEmpty()) as? AmountCell.Value) }
            if (cells.isNotEmpty() && cells.none { it.negative || it.flow != null }) SignConvention.POSITIVE_IS_SPENDING else SignConvention.NEGATIVE_IS_SPENDING
        } else SignConvention.NEGATIVE_IS_SPENDING
        return StatementMapping(
            headerRows = headerIndex + 1, dateColumn = date, dateOrder = detected?.order ?: DateOrder.DMY, descriptionColumn = description,
            layout = layout, debitColumn = debit.takeIf { layout == AmountLayout.SEPARATE_DEBIT_CREDIT || debit != null }, creditColumn = credit,
            amountColumn = amount, typeColumn = type, sign = sign,
        )
    }

    /** No recognisable header: find the column that reads as dates and leave the rest for the user to choose. */
    private fun guessWithoutHeader(rows: List<List<String>>): StatementMapping {
        val width = rows.maxOfOrNull { it.size } ?: 0
        val sample = rows.take(SAMPLE_ROWS)
        val dateColumn = (0 until width).firstOrNull { c -> StatementDates.detect(sample.mapNotNull { it.getOrNull(c) }) != null }
        val detected = dateColumn?.let { c -> StatementDates.detect(sample.mapNotNull { it.getOrNull(c) }) }
        return StatementMapping(headerRows = 0, dateColumn = dateColumn, dateOrder = detected?.order ?: DateOrder.DMY, descriptionColumn = null, layout = AmountLayout.SIGNED_AMOUNT)
    }

    /** The names of the columns for a picker: the header text where there is a header row, otherwise "Column 3". */
    fun columnNames(rows: List<List<String>>, headerRows: Int): List<String> {
        val width = rows.maxOfOrNull { it.size } ?: 0
        val header = rows.getOrNull(headerRows - 1)?.takeIf { headerRows > 0 }
        return (0 until width).map { i -> header?.getOrNull(i)?.takeIf { it.isNotBlank() }?.let { "${i + 1}: $it" } ?: "Column ${i + 1}" }
    }

    /** Whether the first samples read as dates in two orders, so the user should check the day and month. */
    fun dateIsAmbiguous(rows: List<List<String>>, m: StatementMapping): Boolean {
        val c = m.dateColumn ?: return false
        return StatementDates.detect(rows.drop(m.headerRows).take(SAMPLE_ROWS).mapNotNull { it.getOrNull(c) })?.ambiguous == true
    }
}

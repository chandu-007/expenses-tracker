package app.expenses.core.storage

import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.OwnExport
import app.expenses.core.domain.toPlainString
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Entries as a spreadsheet-friendly CSV (RFC 4180, UTF-8 with a byte-order mark so Excel reads rupee signs and other scripts correctly).
 * One row per live entry, oldest first. Amounts are plain numbers without symbols or grouping; the Type column says which way the money
 * went. A split expense lists its categories joined with " + ". The JSON backup is the complete, lossless export; this one is for reading.
 *
 * Text cells that would start a formula in a spreadsheet (a leading `=`, `+`, `-`, `@`, tab or carriage return) are prefixed with an
 * apostrophe, so a merchant called `=HYPERLINK(...)` shows as text instead of running. Numbers are never changed.
 */
class CsvExport(private val db: ExpensesDb) {
    fun bytes(zone: TimeZone): ByteArray = BOM + build(zone).encodeToByteArray()

    fun build(zone: TimeZone): String {
        val q = db.schemaQueries
        val digits = q.selectCurrencies().executeAsList().associate { it.code to it.digits.toInt() }
        val categories = q.allCategories().executeAsList().associate { it.id to it.name }
        val accounts = q.allAccounts().executeAsList().associate { it.id to it.name }
        val splits = q.allSplits().executeAsList().groupBy { it.entry_id }
        val shares = q.allShares().executeAsList().groupBy { it.entry_id }
        val tags = q.allEntryTags().executeAsList().groupBy({ it.entry_id }, { it.tag })
        fun money(minor: Long, code: String) = Money(minor, Currency(code, digits[code] ?: 2)).toPlainString()
        val rows = mutableListOf(HEADER)
        for (e in q.liveEntriesInInsertionOrder().executeAsList().sortedWith(compareBy({ it.occurred_at }, { it.seq }))) {
            val local = Instant.fromEpochMilliseconds(e.occurred_at).toLocalDateTime(zone)
            val heldByOthers = shares[e.id].orEmpty().filter { it.person_id != null }.sumOf { it.amount_minor }
            val yourShare = when (e.kind) {
                "EXPENSE" -> money(e.amount_minor - heldByOthers, e.currency)
                "FRIEND_PAID" -> money(e.amount_minor, e.currency)
                else -> ""
            }
            val category = when (e.kind) {
                "INCOME" -> e.category_id?.let { categories[it] ?: it }.orEmpty()
                else -> splits[e.id].orEmpty().joinToString(" + ") { categories[it.category_id] ?: it.category_id }
            }
            rows += listOf(
                local.date.toString(), "%02d:%02d".format(local.hour, local.minute), TYPE_NAMES[e.kind] ?: e.kind,
                money(e.amount_minor, e.currency), e.currency, yourShare,
                text(category), text(e.account_id?.let { accounts[it] ?: it }.orEmpty()), text(e.counter_account_id?.let { accounts[it] ?: it }.orEmpty()),
                text(e.person_id.orEmpty()), text(e.merchant.orEmpty()), text(e.note.orEmpty()), text(tags[e.id].orEmpty().joinToString(", ")), text(e.source),
            )
        }
        return encode(rows)
    }

    companion object {
        val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val HEADER = OwnExport.HEADER
        private val TYPE_NAMES = mapOf("EXPENSE" to "Expense", "INCOME" to "Income", "TRANSFER" to "Transfer", "REFUND" to "Refund", "FRIEND_PAID" to "Paid by friend", "SETTLEMENT" to "Settlement")

        /** RFC 4180: cells with a comma, quote or line break are quoted and inner quotes doubled; rows end with CRLF. */
        fun encode(rows: List<List<String>>): String = rows.joinToString("") { row -> row.joinToString(",") { quote(it) } + "\r\n" }

        private fun quote(cell: String) =
            if (cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + cell.replace("\"", "\"\"") + "\"" else cell

        /** Stops a text cell from being read as a formula. */
        fun text(cell: String): String = if (cell.isNotEmpty() && cell[0] in "=+-@\t\r") "'$cell" else cell
    }
}

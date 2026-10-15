package app.expenses.core.parsers

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The bill parser against the public sample corpus: none of the transaction alerts may be taken for a bill, and the corpus's own bill texts must be. */
class BillCorpusTest {
    private fun resource(path: String): String = checkNotNull(javaClass.classLoader.getResourceAsStream("corpus/$path")) { "missing corpus/$path" }.readBytes().decodeToString()
    private val rows = resource("expected.tsv").lines().filter { it.isNotBlank() }.map { it.split("\t") }
    private val today = LocalDate(2026, 6, 1)

    @Test
    fun noTransactionAlertInTheCorpusIsTakenForABill() {
        val transactions = rows.filter { it[1] == "1" }
        val wrong = transactions.filter { BillParser.parse(resource(it[0]), null, today) != null }.map { it[0] }
        assertTrue(transactions.size > 100, "the corpus was read: ${transactions.size}")
        assertEquals(emptyList(), wrong, "these transaction alerts were taken for bills")
    }

    @Test
    fun theCorpusBillsAreFoundAndTheOtherNonTransactionsAreNot() {
        val found = rows.filter { it[1] == "0" }.filter { BillParser.parse(resource(it[0]), null, today) != null }.map { it[0].substringAfterLast('/') }.sorted()
        assertEquals(listOf("payment_due.txt", "statement_generated.txt", "statement_ready.txt"), found)
    }
}

package app.expenses.core.parsers

import java.io.File
import kotlin.test.Test

/**
 * Opt-in measurement, not a unit test: set SMS_CORPUS_DIR to a folder holding `expected.tsv` and the message
 * files it names (columns: file, is-transaction, direction, amount in paise, date, reference, last4, channel).
 * The corpus is not stored in this repository. Prints agreement per field and the first mismatches.
 */
class CorpusReportTest {
    private class Row(val file: String, val isTx: Boolean, val direction: String, val amount: String, val date: String, val ref: String, val last4: String)

    @Test
    fun report() {
        val dir = System.getenv("SMS_CORPUS_DIR")?.let(::File)?.takeIf { it.isDirectory } ?: return println("CORPUS skipped: SMS_CORPUS_DIR not set")
        val rows = File(dir, "expected.tsv").readLines().filter { it.isNotBlank() }.map {
            val c = it.split("\t")
            Row(c[0], c[1] == "1", c[2], c[3], c[4], c[5], c[6])
        }
        val positives = rows.filter { it.isTx }
        val negatives = rows.filter { !it.isTx }
        val fields = listOf("recognised", "direction", "amount", "date", "reference", "account")
        val hit = fields.associateWith { 0 }.toMutableMap()
        val perBank = sortedMapOf<String, IntArray>() // [messages, all five fields right]
        val misses = mutableListOf<String>()
        for (r in positives) {
            val bank = r.file.substringBefore('/')
            val stat = perBank.getOrPut(bank) { IntArray(2) }
            stat[0]++
            val res = GenericAlertParser.parse(File(dir, r.file).readText())
            if (res !is ParseResult.Parsed) { misses += "${r.file}: not recognised (${(res as ParseResult.NotATransaction).reason})"; continue }
            val a = res.alert
            hit["recognised"] = hit.getValue("recognised") + 1
            val ok = linkedMapOf(
                "direction" to (a.direction.name.lowercase() == r.direction),
                "amount" to (a.amountMinor.toString() == r.amount),
                "date" to (r.date.isEmpty() || a.date?.toString() == r.date),
                "reference" to (r.ref.isEmpty() || a.reference == r.ref),
                "account" to (r.last4.isEmpty() || a.accountLast4 == r.last4),
            )
            ok.forEach { (k, v) -> if (v) hit[k] = hit.getValue(k) + 1 }
            if (ok.values.all { it }) stat[1]++
            else misses += "${r.file}: wrong ${ok.filterValues { !it }.keys} got dir=${a.direction} amt=${a.amountMinor} date=${a.date} ref=${a.reference} acct=${a.accountLast4} | want ${r.direction} ${r.amount} ${r.date} ${r.ref} ${r.last4}"
        }
        val rejected = negatives.count { GenericAlertParser.parse(File(dir, it.file).readText()) is ParseResult.NotATransaction }
        println("CORPUS messages: ${rows.size} (${positives.size} transactions, ${negatives.size} not transactions)")
        for (f in fields) println("CORPUS field $f: ${hit.getValue(f)}/${positives.size}")
        println("CORPUS non-transactions rejected: $rejected/${negatives.size}")
        println("CORPUS fully right: ${perBank.values.sumOf { it[1] }}/${positives.size}")
        perBank.forEach { (b, s) -> println("CORPUS bank $b: ${s[1]}/${s[0]} fully right") }
        misses.take(200).forEach { println("CORPUS miss $it") }
        println("CORPUS total misses: ${misses.size}")
    }
}

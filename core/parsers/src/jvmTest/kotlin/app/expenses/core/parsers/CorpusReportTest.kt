package app.expenses.core.parsers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scores the generic parser against the public sample corpus in `src/jvmTest/resources/corpus` (see its SOURCE.md).
 * Those samples are sanitised, may not match what banks send today, and the answer key is another library's output,
 * so every figure here is "synthetic, unvalidated". The assertions are regression floors for the generic layer, not
 * an accuracy claim. Per-bank rules, when they exist, get their own fixtures and tests.
 */
class CorpusReportTest {
    private class Row(val file: String, val isTx: Boolean, val direction: String, val amount: String, val date: String, val ref: String, val last4: String)

    private fun resource(path: String): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream("corpus/$path")) { "missing corpus/$path" }.readBytes().decodeToString()

    private val rows = resource("expected.tsv").lines().filter { it.isNotBlank() }.map {
        val c = it.split("\t")
        Row(c[0], c[1] == "1", c[2], c[3], c[4], c[5], c[6])
    }

    @Test
    fun genericParserOnTheSampleCorpus() {
        val positives = rows.filter { it.isTx }
        val negatives = rows.filter { !it.isTx }
        val perBank = sortedMapOf<String, IntArray>() // issuer -> [messages, all five fields right, recognised]
        var recognised = 0
        var fullyRight = 0
        val confidentlyWrong = mutableListOf<String>() // recognised, but wrong direction or wrong amount
        var wrongAmounts = 0
        val unrecognised = mutableListOf<String>()
        val fieldMisses = mutableListOf<String>()
        val fieldHits = linkedMapOf("reference" to 0, "account" to 0, "date" to 0)

        for (r in positives) {
            val stat = perBank.getOrPut(r.file.substringBefore('/')) { IntArray(3) }
            stat[0]++
            val res = GenericAlertParser.parse(resource(r.file))
            if (res !is ParseResult.Parsed) { unrecognised += "${r.file} (${(res as ParseResult.NotATransaction).reason})"; continue }
            val a = res.alert
            assertEquals(Confidence.GENERIC, a.confidence, "generic parses must never claim a bank-rule level of trust")
            recognised++; stat[2]++
            val directionOk = a.direction.name.lowercase() == r.direction
            val amountOk = a.amountMinor.toString() == r.amount
            if (!amountOk) wrongAmounts++
            if (!directionOk || !amountOk) confidentlyWrong += "${r.file}: got ${a.direction} ${a.amountMinor}, want ${r.direction} ${r.amount}"
            val dateOk = r.date.isEmpty() || a.date?.toString() == r.date
            val refOk = r.ref.isEmpty() || a.reference == r.ref
            val acctOk = r.last4.isEmpty() || a.accountLast4 == r.last4
            if (dateOk) fieldHits["date"] = fieldHits.getValue("date") + 1
            if (refOk) fieldHits["reference"] = fieldHits.getValue("reference") + 1
            if (acctOk) fieldHits["account"] = fieldHits.getValue("account") + 1
            if (directionOk && amountOk && dateOk && refOk && acctOk) { fullyRight++; stat[1]++ }
            else fieldMisses += "${r.file}: " + listOfNotNull("direction".takeIf { !directionOk }, "amount".takeIf { !amountOk }, "date".takeIf { !dateOk }, "reference".takeIf { !refOk }, "account".takeIf { !acctOk }).joinToString()
        }
        val rejected = negatives.count { GenericAlertParser.parse(resource(it.file)) is ParseResult.NotATransaction }

        println("CORPUS (synthetic, unvalidated) ${rows.size} messages: ${positives.size} transactions, ${negatives.size} not")
        println("CORPUS recognised $recognised/${positives.size}; fully right $fullyRight/${positives.size}; fields right of recognised: $fieldHits")
        println("CORPUS confidently wrong (direction or amount, of recognised): ${confidentlyWrong.size}")
        confidentlyWrong.forEach { println("CORPUS   wrong: $it") }
        println("CORPUS unrecognised ${unrecognised.size}; non-transactions rejected $rejected/${negatives.size}")
        println("CORPUS per issuer (variants, fully right, recognised): " + perBank.entries.joinToString { "${it.key}=${it.value[0]}/${it.value[1]}/${it.value[2]}" })
        fieldMisses.forEach { println("CORPUS   field miss: $it") }

        // Regression floors for the generic layer (not accuracy claims) ...
        assertTrue(recognised >= 125, "recognised fell to $recognised")
        assertTrue(fullyRight >= 85, "fully right fell to $fullyRight")
        assertEquals(negatives.size - 1, rejected, "non-transaction rejection changed")
        // ... and a ceiling on silent errors: at most the two sign errors found so far, none on amount.
        assertTrue(confidentlyWrong.size <= 2, "confidently wrong results grew to ${confidentlyWrong.size}: $confidentlyWrong")
        assertEquals(0, wrongAmounts, "a recognised message produced a wrong amount: $confidentlyWrong")
    }
}

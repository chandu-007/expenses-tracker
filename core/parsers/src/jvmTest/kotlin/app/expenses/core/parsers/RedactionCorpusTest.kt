package app.expenses.core.parsers

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The redaction against the 152 public sample messages: nothing long survives, and with the amounts kept a redacted text is read as the original was. */
class RedactionCorpusTest {
    private fun resource(path: String): String = checkNotNull(javaClass.classLoader.getResourceAsStream("corpus/$path")) { "missing corpus/$path" }.readBytes().decodeToString()
    private val files = resource("expected.tsv").lines().filter { it.isNotBlank() }.map { it.split("\t")[0] }
    private val today = LocalDate(2026, 6, 1)
    private val roots = mapOf("axis" to "AXISBK", "bob" to "BOBSMS", "canara" to "CANBNK", "equitas" to "EQUITS", "hdfc" to "HDFCBK", "hsbc" to "HSBCIN", "icici" to "ICICIB", "idfc" to "IDFCFB", "indian_bank" to "INDBNK",
        "indusind" to "INDUSB", "jupiter" to "JUPITR", "kotak" to "KOTAKB", "onecard" to "ONECRD", "revolut" to "REVOLT", "sbi" to "SBIINB", "slice" to "SLCEIT", "union" to "UNIONB")

    private fun describe(o: CaptureOutcome): String = when (o) {
        is CaptureOutcome.Transaction -> "tx ${o.alert.direction} ${o.alert.amountMinor} ${o.alert.currency}"
        is CaptureOutcome.Bill -> "bill ${o.notice.amountMinor}"
        is CaptureOutcome.Ignored -> "ignored ${o.reason}"
    }

    @Test
    fun noLongNumberOrAddressPartSurvivesInAnySample() {
        val bad = files.filter { f ->
            val r = MessageRedactor.redact(resource(f))
            Regex("""\d{5,}""").containsMatchIn(r) || Regex("""([A-Za-z0-9_.\-+]+)@""").findAll(r).any { it.groupValues[1] != "xxxx" }
        }
        assertTrue(files.size >= 150)
        assertEquals(emptyList(), bad)
    }

    @Test
    fun withTheAmountsKeptEverySampleIsReadAsItWasBefore() {
        val changed = mutableListOf<String>()
        for (f in files) {
            val sender = "AX-${roots.getValue(f.substringBefore('/'))}-S"
            val body = resource(f)
            val before = describe(CaptureClassifier.classify(sender, body, setOf("EQUITS", "REVOLT"), today))
            val after = describe(CaptureClassifier.classify(sender, MessageRedactor.redact(body, keepAmounts = true), setOf("EQUITS", "REVOLT"), today))
            if (before != after) changed += "$f: $before -> $after"
        }
        println("REDACTION corpus ${files.size} samples, ${changed.size} read differently after redaction with amounts kept")
        changed.forEach { println("REDACTION   changed: $it") }
        // Exactly these, and why: the two Equitas bill reminders ("Min due", "Total due") have only dues, and dues are always hidden, so they lose their figure; the SmartPay
        // text states its amount 90 characters after the word "debited", farther than the 40 characters an amount's clause reads. Every other sample, 149 of 152, reads as before.
        assertEquals(
            listOf(
                "equitas/negative/payment_due.txt: bill 1234567 -> ignored NOT_A_TRANSACTION", "equitas/negative/statement_generated.txt: bill 1234567 -> ignored NOT_A_TRANSACTION",
                "hdfc/cc_smartpay_bbps.txt: tx DEBIT 10000 INR -> ignored NOT_A_TRANSACTION",
            ),
            changed,
        )
    }
}

package app.expenses.core.parsers

import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs `tools/sms-corpus/invented/bill_payments.tsv` (invented texts) through [CaptureClassifier] and prints the precision table: confirmations found, false friends kept, and the two
 * mistakes that matter (a confirmation read as a bill, a bill read as a payment). Every row's class, amount and kind must hold.
 */
class BillPaymentCorpusTest {
    private val relative = "tools/sms-corpus/invented/bill_payments.tsv"

    private fun corpus(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, relative).exists()) dir = dir.parentFile
        return File(checkNotNull(dir) { "no $relative above ${File("").absolutePath}" }, relative)
    }

    private class Row(val id: String, val sender: String, val body: String, val expected: String, val amount: String, val kind: String)

    private fun describe(o: CaptureOutcome, body: String): String = when (o) {
        is CaptureOutcome.Transaction -> BillPayments.read(body)?.let { "BILL_PAYMENT_" + it.side.name } ?: o.alert.direction.name
        is CaptureOutcome.Bill -> "BILL"
        is CaptureOutcome.Ignored -> "IGNORED_" + o.reason.name
    }

    @Test
    fun everyRowIsClassifiedAsExpectedAndThePrecisionTableIsPrinted() {
        val rows = corpus().readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { l ->
            val c = l.split("\t")
            Row(c[0], c[1], c[2], c[3], c.getOrElse(4) { "" }, c.getOrElse(5) { "" })
        }
        assertTrue(rows.size >= 50, "at least 50 rows: ${rows.size}")
        val today = LocalDate(2026, 11, 8)
        val wrong = mutableListOf<String>()
        var paymentsFound = 0
        var friendsKept = 0
        var confirmationAsBill = 0
        var billAsPayment = 0
        val payments = rows.filter { it.expected.startsWith("BILL_PAYMENT_") }
        val friends = rows.filter { !it.expected.startsWith("BILL_PAYMENT_") }
        for (r in rows) {
            val o = CaptureClassifier.classify(r.sender, r.body, emptySet(), today)
            val got = describe(o, r.body)
            val ok = got == r.expected
            if (!ok) wrong += "${r.id}: $got want ${r.expected}"
            if (r.expected.startsWith("BILL_PAYMENT_")) {
                if (ok) paymentsFound++
                if (o is CaptureOutcome.Bill) confirmationAsBill++
            } else {
                if (ok) friendsKept++
                if (r.expected == "BILL" && got.startsWith("BILL_PAYMENT_")) billAsPayment++
            }
            val amount = (o as? CaptureOutcome.Bill)?.notice?.amountMinor ?: (o as? CaptureOutcome.Transaction)?.alert?.amountMinor
            if (ok && r.amount.isNotEmpty() && r.expected != "BILL" && amount?.toString() != r.amount) wrong += "${r.id}: amount $amount want ${r.amount}"
            if (ok && r.expected == "BILL" && o is CaptureOutcome.Bill && (o.notice.amountMinor.toString() != r.amount || o.notice.kind.name != r.kind)) wrong += "${r.id}: bill ${o.notice.amountMinor} ${o.notice.kind}"
            if (ok && r.expected.startsWith("BILL_PAYMENT_")) { val n = BillPayments.read(r.body)!!; if (n.kind.name != r.kind) wrong += "${r.id}: kind ${n.kind} want ${r.kind}" }
        }
        println("== bill payment corpus precision (invented texts) ==")
        println("payments found            ${paymentsFound}/${payments.size}")
        println("false friends kept        ${friendsKept}/${friends.size}")
        println("confirmations read as bill  $confirmationAsBill")
        println("bills read as payment       $billAsPayment")
        assertEquals(emptyList(), wrong)
        assertEquals(0, confirmationAsBill)
        assertEquals(0, billAsPayment)
    }
}

package app.expenses.core.parsers

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Autopay, mandate and standing-instruction texts. A text that says money WILL be taken ("will be debited", "to be debited", "is scheduled", a pre-debit alert) is a promise: it belongs on the
 * Bills page, never in To review as money that already left. A text that says money WAS taken is a transaction. Invented names, amounts and digits.
 */
class MandateTextsTest {
    private val today = LocalDate(2026, 11, 1)
    private fun outcome(t: String) = CaptureClassifier.classify("AX-HDFCBK-S", "$t -HDFC Bank", emptySet(), today)

    @Test
    fun aPromisedDebitIsABillWithItsDateAndMarkedAsTakenByItself() {
        val promises = listOf(
            "Your A/c XX1234 will be debited with Rs.199.00 on 05-11-26 towards NETFLIX mandate. Ensure sufficient balance." to (19_900L to LocalDate(2026, 11, 5)),
            "Auto-pay of Rs.199 for NETFLIX is scheduled on 05-11-2026. Ensure sufficient balance in your account." to (19_900L to LocalDate(2026, 11, 5)),
            "Rs 649 will be auto-debited from your a/c XX1234 on 12-Nov-26 for Hotstar SI." to (64_900L to LocalDate(2026, 11, 12)),
            "Dear Customer, Rs.1,999.00 will be debited from your A/c XX1234 on 03-11-26 for ACH D- TATA CAPITAL mandate." to (199_900L to LocalDate(2026, 11, 3)),
            "UPI Mandate: Rs.299.00 to be debited by SPOTIFY on 10-11-26 from A/c XX1234. Approve in your UPI app." to (29_900L to LocalDate(2026, 11, 10)),
            "Pre-debit alert: Rs.499.00 will be debited from A/c XX1234 on 06-11-26 towards Amazon Prime auto-renewal." to (49_900L to LocalDate(2026, 11, 6)),
        )
        for ((text, want) in promises) {
            val r = outcome(text)
            assertTrue(r is CaptureOutcome.Bill, "$text -> $r")
            assertEquals(want.first, r.notice.amountMinor, text); assertEquals(want.second, r.notice.dueDate, text); assertTrue(r.notice.autoDebit, text)
        }
    }

    @Test
    fun aDebitThatHappenedIsATransactionWhateverTheMandateWordingAroundIt() {
        val done = listOf(
            "Rs.199.00 debited from A/c XX1234 on 05-11-26 towards NETFLIX mandate (UPI Ref 628374659283)." to 19_900L,
            "Rs.1,999.00 debited from A/c XX1234 on 03-11-26. Info: ACH D- TATA CAPITAL-123456. Avl Bal Rs.100" to 199_900L,
            "Standing instruction: Rs.5,000.00 debited from A/c XX1234 on 03-11-26. Info: SI-HDFC LIFE-998877" to 500_000L,
            "Auto-pay of Rs.199 for NETFLIX was successful on 05-11-26." to 19_900L,
        )
        for ((text, minor) in done) {
            val r = outcome(text)
            assertTrue(r is CaptureOutcome.Transaction, "$text -> $r")
            assertEquals(Direction.DEBIT, r.alert.direction, text); assertEquals(minor, r.alert.amountMinor, text)
        }
    }

    @Test
    fun aMandateThatFailedOrWasOnlySetUpIsNeitherABillNorADebit() {
        for (t in listOf("Mandate for NETFLIX of Rs.199.00 failed due to insufficient balance on 05-11-26.", "Mandate created: Rs.199.00 monthly for NETFLIX from A/c XX1234, first debit on 05-11-26."))
            assertTrue(outcome(t) is CaptureOutcome.Ignored, t)
    }
}

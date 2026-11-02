package app.expenses

import app.expenses.core.domain.SkippedHint
import app.expenses.core.parsers.UnlistedSenders
import app.expenses.core.storage.CaptureEventOutcome
import app.expenses.core.storage.recordSkippedHint

/**
 * What both ingest paths (Android `Capture.ingest`, iPhone [MessageIngest.ingest]) do with a text from an alphanumeric header that is not on the list. The words are looked at ONCE,
 * in memory, to see whether they are shaped like a bank's payment ([UnlistedSenders.hint]); the text is never parsed into anything that is kept. Kept: the event the caller records
 * (a count, with [CaptureEventOutcome.UNLISTED_PAYMENT] for a payment-shaped one) and, for that case only, ONE redacted outline per sender in the settings table. Never the words.
 */
object UnlistedCapture {
    /**
     * The outcome to record for a skipped text from [root]: [CaptureEventOutcome.UNLISTED_PAYMENT] after keeping the redacted outline, or [CaptureEventOutcome.NOT_ALLOWLISTED] when
     * the text is not payment-shaped (an OTP, an offer, a reminder). The caller must only pass a root from an alphanumeric header, never a phone number, an email or a text with no sender.
     * Call inside the write lock; the caller records the event.
     */
    fun judge(ok: AppContainer.Opened.Ok, now: Long, root: String, body: String): CaptureEventOutcome {
        val hint = UnlistedSenders.hint(body, root) ?: return CaptureEventOutcome.NOT_ALLOWLISTED
        ok.templates.recordSkippedHint(SkippedHint(root, now, hint.way == UnlistedSenders.Way.OUT, hint.outline))
        return CaptureEventOutcome.UNLISTED_PAYMENT
    }
}

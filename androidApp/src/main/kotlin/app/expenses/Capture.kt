package app.expenses

import app.expenses.core.parsers.CaptureClassifier
import app.expenses.core.parsers.CaptureOutcome
import app.expenses.core.parsers.IgnoreReason
import app.expenses.core.parsers.SenderRules
import app.expenses.core.storage.CaptureEventOutcome
import app.expenses.core.storage.InboxItem
import app.expenses.core.storage.InboxStatus
import java.security.MessageDigest
import java.util.UUID

/**
 * Turns one text or notification into, at most, one pending item in the review inbox. The sender is judged before the body is read; a text
 * that is not a transaction from an allowlisted header leaves **only a count** (no words, no number) in capture health.
 */
object Capture {
    const val SMS = "SMS"
    const val NOTIFICATION = "NOTIFICATION"
    const val SETTING_NOTIF_ON = "capture_notif_on"
    const val SETTING_EXTRA_ROOTS = "capture_extra_roots"
    const val SETTING_SMS_ON = "capture_sms_on"

    /** Bumped whenever capture changed the inbox, so an open screen can refresh. */
    val changes = kotlinx.coroutines.flow.MutableStateFlow(0)

    /** The same text delivered twice has the same fingerprint (same source, sender, time the phone stamped it, and words). */
    fun fingerprint(source: String, sender: String, receivedAt: Long, body: String): String {
        val text = "$source|${sender.trim().uppercase()}|$receivedAt|${body.replace(Regex("\\s+"), " ").trim()}"
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /** A payment app often posts one notification twice a moment apart; the same words from the same app within this long are that repost, not a second payment. */
    const val REPOST_WINDOW_MS = 10_000L

    suspend fun ingest(container: AppContainer, source: String, sender: String, body: String, receivedAt: Long): CaptureEventOutcome =
        container.write { ok ->
            val extra = ok.templates.setting(SETTING_EXTRA_ROOTS).orEmpty().split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            val now = System.currentTimeMillis()
            val outcome = when (val r = CaptureClassifier.classify(sender, body, extra)) {
                is CaptureOutcome.Ignored -> {
                    val event = when (r.reason) {
                        IgnoreReason.PERSONAL_SENDER -> CaptureEventOutcome.PERSONAL_SENDER
                        IgnoreReason.NOT_ALLOWLISTED -> CaptureEventOutcome.NOT_ALLOWLISTED
                        IgnoreReason.OTP -> CaptureEventOutcome.OTP
                        IgnoreReason.NOT_A_TRANSACTION -> CaptureEventOutcome.NOT_A_TRANSACTION
                        IgnoreReason.DECLINED -> CaptureEventOutcome.DECLINED
                    }
                    ok.inbox.recordEvent(now, r.headerRoot, event)
                    return@write event
                }
                is CaptureOutcome.Transaction -> {
                    val a = r.alert
                    val item = InboxItem(
                        UUID.randomUUID().toString(), source, sender.trim(), r.issuer, receivedAt, a.direction.name, a.amountMinor, a.currency, a.date?.toString(), a.reference,
                        a.accountLast4, a.viaUpi, a.confidence.name, body.replace(Regex("\\s+"), " ").trim().take(600), InboxStatus.PENDING, null,
                    )
                    val repost = source == NOTIFICATION && item.body?.let { ok.inbox.hasSameTextNear(source, item.sender, it, receivedAt, REPOST_WINDOW_MS) } == true
                    val added = !repost && ok.inbox.add(item, fingerprint(source, sender, receivedAt, body))
                    if (added) CaptureEventOutcome.CAPTURED else CaptureEventOutcome.DUPLICATE
                }
            }
            ok.inbox.recordEvent(now, SenderRules.headerRoot(sender), outcome)
            if (outcome == CaptureEventOutcome.CAPTURED) changes.value += 1
            outcome
        } ?: CaptureEventOutcome.NOT_A_TRANSACTION
}

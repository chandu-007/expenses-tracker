package app.expenses

import app.expenses.core.parsers.CaptureClassifier
import app.expenses.core.storage.BillStatus
import app.expenses.core.storage.Bill
import app.expenses.core.parsers.BillNotice
import app.expenses.core.parsers.BillKind
import app.expenses.core.parsers.CaptureOutcome
import app.expenses.core.parsers.IgnoreReason
import app.expenses.core.parsers.AppSources
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
    const val SMS = CaptureKeys.SMS
    const val NOTIFICATION = CaptureKeys.NOTIFICATION
    const val SETTING_NOTIF_ON = CaptureKeys.SETTING_NOTIF_ON
    const val SETTING_EXTRA_ROOTS = CaptureKeys.SETTING_EXTRA_ROOTS
    const val SETTING_SMS_ON = CaptureKeys.SETTING_SMS_ON
    const val SETTING_EMAIL_ON = CaptureKeys.SETTING_EMAIL_ON

    /** Bumped whenever capture changed the inbox, so an open screen can refresh. */
    val changes = kotlinx.coroutines.flow.MutableStateFlow(0)

    /** The same text delivered twice has the same fingerprint (same source, sender, time the phone stamped it, and words). */
    fun fingerprint(source: String, sender: String, receivedAt: Long, body: String): String {
        val text = "$source|${sender.trim().uppercase()}|$receivedAt|${body.replace(Regex("\\s+"), " ").trim()}"
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /** A payment app often posts one notification twice a moment apart; the same words from the same app within this long are that repost, not a second payment. */
    const val REPOST_WINDOW_MS = 10_000L

    /** The same, for an email app's notification, which is re-posted when its group of mails changes. */
    const val EMAIL_REPOST_WINDOW_MS = 60 * 60_000L

    /** How a bill reads: "HDFC Bank credit card 0862", "BESCOM electricity bill". */
    fun billTitle(n: BillNotice, issuer: String): String =
        if (n.kind == BillKind.CREDIT_CARD) "$issuer credit card" + (n.accountLast4?.let { " $it" } ?: "") else "${n.biller ?: issuer} ${n.kind.label}"

    private fun billOf(r: CaptureOutcome.Bill, source: String, sender: String, receivedAt: Long, body: String, arrived: java.time.LocalDate): Bill {
        val n = r.notice
        // the same bill reminded again (same kind, sender, card or consumer, due date and amount) has the same key; with no date, the month it arrived stands in
        val key = listOf(n.kind.name, r.headerRoot, n.accountLast4 ?: n.reference ?: "", n.dueDate?.toString() ?: "nodate:${arrived.year}-${arrived.monthValue}", n.amountMinor).joinToString("|")
        return Bill(
            UUID.randomUUID().toString(), source, sender.trim(), r.issuer, n.kind.name, billTitle(n, r.issuer), n.amountMinor, n.minDueMinor, n.currency, n.dueDate?.toString(), n.accountLast4, n.reference,
            n.autoDebit, receivedAt, body.replace(Regex("\\s+"), " ").trim().take(600), BillStatus.FOUND, null, null, key,
        )
    }

    /** The most words of a transaction text or notification kept on its pending item: an email's alert is long, and the original wording is shown as where an entry came from. */
    const val BODY_CAP = AppSources.EMAIL_WORDS_CAP

    suspend fun ingest(container: AppContainer, source: String, sender: String, body: String, receivedAt: Long): CaptureEventOutcome =
        ingestWith(container, source, sender, body, receivedAt, emailNames = null)

    /**
     * One email read from an email app's notification ([emailApp] is GMAIL, OUTLOOK or YAHOO; see [AppSources.EMAIL_PACKAGES]). The bank is judged from the email's sender
     * names ([senderNames]) before the words are read; an email from none of the listed banks is dropped without leaving even a count. The pending item keeps [emailApp] as its
     * sender and the bank as its issuer; the words are kept up to [BODY_CAP].
     */
    suspend fun ingestEmail(container: AppContainer, emailApp: String, senderNames: List<String>, body: String, receivedAt: Long): CaptureEventOutcome =
        ingestWith(container, NOTIFICATION, emailApp, body, receivedAt, emailNames = senderNames)

    private suspend fun ingestWith(container: AppContainer, source: String, sender: String, body: String, receivedAt: Long, emailNames: List<String>?): CaptureEventOutcome =
        container.write { ok ->
            val extra = ok.templates.setting(SETTING_EXTRA_ROOTS).orEmpty().split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            val now = System.currentTimeMillis()
            val zone = java.time.ZoneId.systemDefault()
            val arrived = java.time.Instant.ofEpochMilli(receivedAt).atZone(zone).toLocalDate()
            val today = kotlinx.datetime.LocalDate(arrived.year, arrived.monthValue, arrived.dayOfMonth)
            val verdict = if (emailNames == null) CaptureClassifier.classify(sender, body, extra, today) else CaptureClassifier.classifyEmail(emailNames, body, extra, today)
            val outcome = when (val r = verdict) {
                is CaptureOutcome.Bill -> {
                    val b = billOf(r, source, sender, receivedAt, body, arrived)
                    if (ok.bills.add(b)) CaptureEventOutcome.BILL else CaptureEventOutcome.DUPLICATE
                }
                is CaptureOutcome.Ignored -> {
                    // an email from nobody on the list is any email at all: not even counted
                    if (emailNames != null && r.reason == IgnoreReason.NOT_ALLOWLISTED && r.headerRoot == null) return@write CaptureEventOutcome.NOT_ALLOWLISTED
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
                        a.accountLast4, a.viaUpi, a.confidence.name, body.replace(Regex("\\s+"), " ").trim().take(BODY_CAP), InboxStatus.PENDING, null,
                    )
                    // a mail app re-posts a notification when its group changes, minutes or hours later: the same words from the same app within an hour are that, not a second alert
                    val window = if (emailNames != null) EMAIL_REPOST_WINDOW_MS else REPOST_WINDOW_MS
                    val repost = source == NOTIFICATION && item.body?.let { ok.inbox.hasSameTextNear(source, item.sender, it, receivedAt, window) } == true
                    val added = !repost && ok.inbox.add(item, fingerprint(source, sender, receivedAt, body))
                    if (added) CaptureEventOutcome.CAPTURED else CaptureEventOutcome.DUPLICATE
                }
            }
            ok.inbox.recordEvent(now, (verdict as? CaptureOutcome.Transaction)?.headerRoot ?: (verdict as? CaptureOutcome.Bill)?.headerRoot, outcome)
            if (outcome == CaptureEventOutcome.CAPTURED || outcome == CaptureEventOutcome.BILL) changes.value += 1
            outcome
        } ?: CaptureEventOutcome.NOT_A_TRANSACTION
}

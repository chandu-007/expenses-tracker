package app.expenses

import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.formatted
import app.expenses.core.parsers.BillKind
import app.expenses.core.parsers.BankRules
import app.expenses.core.parsers.BillNotice
import app.expenses.core.parsers.BillPayments
import app.expenses.core.parsers.BillPaymentRepair
import app.expenses.core.parsers.CaptureClassifier
import app.expenses.core.parsers.CaptureOutcome
import app.expenses.core.parsers.Direction
import app.expenses.core.parsers.IgnoreReason
import app.expenses.core.parsers.PasteRules
import app.expenses.core.parsers.SenderRules
import app.expenses.core.storage.Bill
import app.expenses.core.storage.BillStatus
import app.expenses.core.storage.CaptureEventOutcome
import app.expenses.core.storage.InboxItem
import app.expenses.core.storage.InboxStatus
import app.expenses.core.storage.Sha256
import app.expenses.core.storage.countSkippedSender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.number
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * What happened to one message: [outcome] (null when the database could not be opened), a short sentence for a person ([message]) and, for a transaction or
 * a bill that was kept, what it was ([detail], for example "Debit of ₹450.00 from HDFC Bank"). Never holds the words of the message.
 */
class MessageResult(val outcome: CaptureEventOutcome?, val message: String, val detail: String? = null) {
    /** A new item is waiting in To review or on the Bills page. */
    val kept get() = outcome == CaptureEventOutcome.CAPTURED || outcome == CaptureEventOutcome.BILL
}

/** What pasting a message did, for the To review screen: [message] and [detail] as in [MessageResult]; [clipboardEmpty] when there was nothing to read. */
class PasteOutcome(val message: String, val detail: String?, val kept: Boolean, val clipboardEmpty: Boolean)

/**
 * Turns one message that reached the app without an operating-system hook (iPhone: a Shortcuts automation hands it over, or the person pastes it) into, at
 * most, one pending item in the review inbox. It is the logic of the Android `Capture.ingest` written without java.*, with the same promises: the sender is
 * judged before the body is read; a text that is not a transaction from a known sender leaves **only a count** (no words, no number) in capture health; a
 * bank transaction becomes a pending [InboxItem]; a bill becomes a [Bill]. The Android class itself is untouched.
 *
 * Differences that exist because nothing here knows when a message arrived or who sent it for sure:
 *  - There is no sender for a pasted text, and a Shortcuts automation may not pass one. Without a sender the body has to carry its own evidence that it is a
 *    bank message ([PasteRules]); texts that do not are counted and dropped without being parsed.
 *  - The time is the time the message reached the app, and the fingerprint is a calendar day plus the words (not the sender), so the same message handed over
 *    twice, by the automation and again by a paste, is one item.
 */
object MessageIngest {
    /** Bumped whenever a message changed the inbox or the bills, so an open screen can refresh. */
    val changes = MutableStateFlow(0)

    /** The root recorded in capture health for a text that came without a sender. */
    const val NO_SENDER_ROOT = PasteRules.NO_SENDER_ROOT

    /** The issuer shown for a transaction whose bank could not be told from the words. */
    const val UNKNOWN_ISSUER = PasteRules.UNKNOWN_ISSUER

    /** The same words on the same day are the same message, whoever handed them over. */
    fun fingerprint(source: String, receivedAt: Long, body: String): String {
        val text = "$source|${dateOfMillis(receivedAt)}|${normalised(body)}"
        return Sha256.hex(text.encodeToByteArray())
    }

    private fun normalised(body: String) = body.replace(Regex("\\s+"), " ").trim()

    /** How a bill reads: "HDFC Bank credit card 0862", "BESCOM electricity bill". */
    fun billTitle(n: BillNotice, issuer: String): String =
        if (n.kind == BillKind.CREDIT_CARD) "$issuer credit card" + (n.accountLast4?.let { " $it" } ?: "") else "${n.biller ?: issuer} ${n.kind.label}"

    @OptIn(ExperimentalUuidApi::class)
    private fun billOf(r: CaptureOutcome.Bill, source: String, sender: String, receivedAt: Long, body: String): Bill {
        val n = r.notice
        val arrived = dateOfMillis(receivedAt)
        // the same bill reminded again (same kind, sender, card or consumer, due date and amount) has the same key; with no date, the month it arrived stands in
        val key = listOf(n.kind.name, r.headerRoot, n.accountLast4 ?: n.reference ?: "", n.dueDate?.toString() ?: "nodate:${arrived.year}-${arrived.month.number}", n.amountMinor).joinToString("|")
        return Bill(
            Uuid.random().toString(), source, sender, r.issuer, n.kind.name, billTitle(n, r.issuer), n.amountMinor, n.minDueMinor, n.currency, n.dueDate?.toString(), n.accountLast4, n.reference,
            n.autoDebit, receivedAt, normalised(body).take(600), BillStatus.FOUND, null, null, key,
        )
    }

    /**
     * Looks at one message. [sender] is the name or number it came from, or null/blank when unknown. With a sender, it is judged first, exactly as on Android.
     * Without one, [PasteRules] decides from the words whether it is a bank message at all. Works on the database through [container]'s write lock.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun ingest(container: AppContainer, sender: String?, body: String, receivedAt: Long = nowMillis(), source: String = CaptureKeys.SMS, emailNames: List<String>? = null): MessageResult {
        val text = body.take(MAX_BODY)
        val named = sender?.trim().orEmpty()
        return container.write { ok ->
            val extra = ok.templates.setting(CaptureKeys.SETTING_EXTRA_ROOTS).orEmpty().split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            val now = nowMillis()
            val arrived = dateOfMillis(receivedAt)
            val verdict: CaptureOutcome
            val storedSender: String
            if (emailNames != null) {
                // an email: the bank is found from the sender's names, and an email from nobody on the list is any email at all, so it is not even counted
                storedSender = named
                verdict = CaptureClassifier.classifyEmail(emailNames, text, extra, arrived)
                if (verdict is CaptureOutcome.Ignored && verdict.reason == IgnoreReason.NOT_ALLOWLISTED && verdict.headerRoot == null) return@write MessageResult(CaptureEventOutcome.NOT_ALLOWLISTED, wording(CaptureEventOutcome.NOT_ALLOWLISTED))
            } else {
                storedSender = named
                verdict = classifyText(named, text, extra, arrived)
                if (verdict === NO_EVIDENCE) {
                    // not read any further: it is counted, and nothing of it is kept
                    ok.inbox.recordEvent(now, NO_SENDER_ROOT, CaptureEventOutcome.NOT_A_TRANSACTION)
                    return@write MessageResult(CaptureEventOutcome.NOT_A_TRANSACTION, wording(CaptureEventOutcome.NOT_A_TRANSACTION))
                }
            }
            var detail: String? = null
            val outcome = when (verdict) {
                is CaptureOutcome.Bill -> {
                    val b = billOf(verdict, source, storedSender, receivedAt, text)
                    if (ok.bills.add(b)) CaptureEventOutcome.BILL.also { detail = "${b.title}, ${Money(b.amountMinor, Currency(b.currency, 2)).formatted()}" } else CaptureEventOutcome.DUPLICATE
                }
                is CaptureOutcome.Ignored -> {
                    val event = when (verdict.reason) {
                        IgnoreReason.PERSONAL_SENDER -> CaptureEventOutcome.PERSONAL_SENDER
                        IgnoreReason.NOT_ALLOWLISTED -> CaptureEventOutcome.NOT_ALLOWLISTED
                        IgnoreReason.OTP -> CaptureEventOutcome.OTP
                        IgnoreReason.NOT_A_TRANSACTION -> CaptureEventOutcome.NOT_A_TRANSACTION
                        IgnoreReason.DECLINED -> CaptureEventOutcome.DECLINED
                    }
                    ok.inbox.recordEvent(now, verdict.headerRoot ?: if (named.isEmpty()) NO_SENDER_ROOT else null, event)
                    // a text with a sender name that is not on the list: only the NAME is counted (see SkippedSenders), for the list in More, Capture; never a word of the text
                    verdict.headerRoot?.let { root -> if (event == CaptureEventOutcome.NOT_ALLOWLISTED && emailNames == null && source == CaptureKeys.SMS && named.isNotEmpty()) ok.templates.countSkippedSender(root, SenderRules.headerSuffix(named), now) }
                    return@write MessageResult(event, wording(event))
                }
                is CaptureOutcome.Transaction -> {
                    val a = verdict.alert
                    val item = InboxItem(
                        Uuid.random().toString(), source, storedSender, verdict.issuer, receivedAt, a.direction.name, a.amountMinor, a.currency, a.date?.toString(), a.reference,
                        a.accountLast4, a.viaUpi, a.confidence.name, normalised(text).take(BODY_CAP), InboxStatus.PENDING, null,
                    )
                    if (ok.inbox.add(item, fingerprint(source, receivedAt, text))) CaptureEventOutcome.CAPTURED.also {
                        val money = Money(a.amountMinor, Currency(a.currency, 2)).formatted()
                        // a payment of a bill is said so ("Bill payment of ₹12,345.67 from Kotak Mahindra Bank"), not as a plain debit
                        detail = if (BillPayments.read(text) != null) "Bill payment of $money from ${verdict.issuer}" else (if (a.direction == Direction.CREDIT) "Credit of " else "Debit of ") + money + " from ${verdict.issuer}"
                    } else CaptureEventOutcome.DUPLICATE
                }
            }
            ok.inbox.recordEvent(now, if (verdict is CaptureOutcome.Bill) verdict.headerRoot else (verdict as? CaptureOutcome.Transaction)?.headerRoot, outcome)
            if (outcome == CaptureEventOutcome.CAPTURED || outcome == CaptureEventOutcome.BILL) changes.value += 1
            MessageResult(outcome, wording(outcome), detail)
        } ?: MessageResult(null, "Expenses could not open its data, so nothing was captured.")
    }

    /** What [classifyText] returns for a text that came with no sender and carries no evidence of being a bank's: counted, never read. */
    private val NO_EVIDENCE = CaptureOutcome.Ignored(IgnoreReason.NOT_A_TRANSACTION, null)

    /**
     * The verdict for a text with [sender] (blank when the message came without one): judged by its header when there is one, otherwise by the evidence in its words
     * ([PasteRules]). [NO_EVIDENCE] when a text with no sender has none.
     */
    private fun classifyText(sender: String, text: String, extra: Set<String>, arrived: kotlinx.datetime.LocalDate): CaptureOutcome =
        PasteRules.classify(sender, text, extra, arrived) ?: NO_EVIDENCE

    /**
     * Texts that an earlier version filed as a debit or credit in To review but that the rules now read as a bill (0.7.1: "Your bill ... is due ... Total amount payable ... Kindly ignore, if already paid" was
     * filed as a debit). Each still-PENDING text item (SMS or Shortcuts source, never an email or a payment-app notification, and never one the person already confirmed or skipped) is judged again from its
     * stored sender and words by the same rules as a new message; one that is now a bill becomes a [Bill] (the same dedupe key as a new one, so a bill already on the Bills page is not doubled) and the item is
     * skipped, which keeps its fingerprint so the same text is not captured twice. Idempotent: a skipped item is no longer pending, and an item that is still a transaction is not touched. Returns how many
     * items became bills. Call inside the write lock. (Bills made WRONGLY from a payment's confirmation are not moved here: that rewrites bills the person may have seen, so it is their tap, see [repairableBills].)
     */
    fun reclassifyWaiting(ok: AppContainer.Opened.Ok): Int {
        val extra = ok.templates.setting(CaptureKeys.SETTING_EXTRA_ROOTS).orEmpty().split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        var converted = 0
        for (item in ok.inbox.list(InboxStatus.PENDING, 5000)) {
            val text = item.body?.takeIf { item.source == CaptureKeys.SMS } ?: continue
            val verdict = classifyText(item.sender.trim(), text, extra, dateOfMillis(item.receivedAt))
            if (verdict !is CaptureOutcome.Bill) continue
            ok.bills.add(billOf(verdict, item.source, item.sender, item.receivedAt, text))
            if (ok.inbox.dismiss(item.id)) converted++
        }
        if (converted > 0) changes.value += 1
        return converted
    }

    /**
     * The FOUND bills that an earlier version made from the confirmation of a payment ("Axis Bank Credit Card bill payment of Rs. 12,345.67 was successful!"), as the pending item each would become in To review
     * (with the fingerprint of its text). Narrow by [BillPaymentRepair]: a bill the person added, paid, skipped or edited, or whose words also ask for money, is not listed. Reads the FOUND bills only (at most
     * 200), never the ledger. Nothing is changed; the person's tap on "Move to To review" does it ([BillRepairs.move]).
     */
    @OptIn(ExperimentalUuidApi::class)
    fun repairableBills(ok: AppContainer.Opened.Ok): List<Triple<Bill, InboxItem, String>> {
        val found = ok.bills.list(BillStatus.FOUND, 200)
        if (found.isEmpty()) return emptyList()
        val extra by lazy { ok.templates.setting(CaptureKeys.SETTING_EXTRA_ROOTS).orEmpty().split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }.toSet() }
        return found.mapNotNull { b ->
            BillPaymentRepair.notice(b.source, b.status.name, b.body, b.amountMinor, b.dueDate, b.entryId, b.paidAt) ?: return@mapNotNull null
            val text = b.body ?: return@mapNotNull null
            val verdict = classifyText(b.sender.trim(), text, extra, dateOfMillis(b.receivedAt)) as? CaptureOutcome.Transaction ?: return@mapNotNull null
            val a = verdict.alert
            val item = InboxItem(
                Uuid.random().toString(), b.source, b.sender, verdict.issuer, b.receivedAt, a.direction.name, a.amountMinor, a.currency, a.date?.toString(), a.reference,
                a.accountLast4, a.viaUpi, a.confidence.name, normalised(text).take(BODY_CAP), InboxStatus.PENDING, null,
            )
            Triple(b, item, fingerprint(b.source, b.receivedAt, text))
        }
    }

    /** The sentence for each outcome: what a Shortcuts dialog says and what the paste box shows. */
    fun wording(outcome: CaptureEventOutcome): String = when (outcome) {
        CaptureEventOutcome.CAPTURED -> "Captured, review it in Expenses"
        CaptureEventOutcome.BILL -> "Captured a bill, see Bills in Expenses"
        CaptureEventOutcome.DUPLICATE -> "Already captured: the same message was seen before"
        CaptureEventOutcome.PERSONAL_SENDER -> "Ignored: a text from a person"
        CaptureEventOutcome.NOT_ALLOWLISTED -> "Ignored: the sender is not on the list of banks"
        CaptureEventOutcome.OTP -> "Ignored: a one-time password"
        CaptureEventOutcome.NOT_A_TRANSACTION -> "Ignored: not a bank transaction"
        CaptureEventOutcome.DECLINED -> "Ignored: a declined payment"
    }

    /** A message is at most this long when it is read: bank texts are a few hundred characters, and a pasted page of something else is not one. */
    const val MAX_BODY = 4000

    /** The words kept on a pending item, as on Android: an email can be long. */
    const val BODY_CAP = 2000
}

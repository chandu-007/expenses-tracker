package app.expenses

import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.formatted
import app.expenses.core.parsers.BillKind
import app.expenses.core.parsers.BillNotice
import app.expenses.core.parsers.CaptureClassifier
import app.expenses.core.parsers.CaptureOutcome
import app.expenses.core.parsers.Direction
import app.expenses.core.parsers.IgnoreReason
import app.expenses.core.parsers.SenderRules
import app.expenses.core.storage.Bill
import app.expenses.core.storage.BillStatus
import app.expenses.core.storage.CaptureEventOutcome
import app.expenses.core.storage.InboxItem
import app.expenses.core.storage.InboxStatus
import app.expenses.core.storage.Sha256
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
    const val NO_SENDER_ROOT = "PASTED"

    /** The issuer shown for a transaction whose bank could not be told from the words. */
    const val UNKNOWN_ISSUER = "Bank message"

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
            } else if (named.isNotEmpty()) {
                storedSender = named
                verdict = CaptureClassifier.classify(named, text, extra, arrived)
            } else {
                storedSender = ""
                when (val evidence = PasteRules.evidence(text)) {
                    is PasteRules.Evidence.Bank -> verdict = CaptureClassifier.classify(evidence.root, text, extra, arrived)
                    PasteRules.Evidence.AlertWords -> verdict = when (val r = CaptureClassifier.classify(NO_SENDER_ROOT, text, extra + NO_SENDER_ROOT, arrived)) {
                        is CaptureOutcome.Transaction -> r.copy(issuer = UNKNOWN_ISSUER)
                        is CaptureOutcome.Bill -> r.copy(issuer = UNKNOWN_ISSUER)
                        is CaptureOutcome.Ignored -> r
                    }
                    PasteRules.Evidence.None -> {
                        // not read any further: it is counted, and nothing of it is kept
                        ok.inbox.recordEvent(now, NO_SENDER_ROOT, CaptureEventOutcome.NOT_A_TRANSACTION)
                        return@write MessageResult(CaptureEventOutcome.NOT_A_TRANSACTION, wording(CaptureEventOutcome.NOT_A_TRANSACTION))
                    }
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
                    return@write MessageResult(event, wording(event))
                }
                is CaptureOutcome.Transaction -> {
                    val a = verdict.alert
                    val item = InboxItem(
                        Uuid.random().toString(), source, storedSender, verdict.issuer, receivedAt, a.direction.name, a.amountMinor, a.currency, a.date?.toString(), a.reference,
                        a.accountLast4, a.viaUpi, a.confidence.name, normalised(text).take(BODY_CAP), InboxStatus.PENDING, null,
                    )
                    if (ok.inbox.add(item, fingerprint(source, receivedAt, text))) CaptureEventOutcome.CAPTURED.also {
                        detail = (if (a.direction == Direction.CREDIT) "Credit of " else "Debit of ") + Money(a.amountMinor, Currency(a.currency, 2)).formatted() + " from ${verdict.issuer}"
                    } else CaptureEventOutcome.DUPLICATE
                }
            }
            ok.inbox.recordEvent(now, if (verdict is CaptureOutcome.Bill) verdict.headerRoot else (verdict as? CaptureOutcome.Transaction)?.headerRoot, outcome)
            if (outcome == CaptureEventOutcome.CAPTURED || outcome == CaptureEventOutcome.BILL) changes.value += 1
            MessageResult(outcome, wording(outcome), detail)
        } ?: MessageResult(null, "Expenses could not open its data, so nothing was captured.")
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

/**
 * When a message comes without a sender (a pasted text, or a Shortcuts automation that did not pass one), the words have to say it is a bank's. The rule is
 * deliberately strict, because the allowlist of senders cannot protect here:
 *  1. **A known bank, card issuer or biller is named in the message** (HDFC Bank, SBI, ICICI Bank, BESCOM, ...; or its header such as HDFCBK): then the message is judged as if it came from that header.
 *  2. Otherwise only if it has **all of**: a rupee amount (Rs, INR, ₹), a word that says money moved (debited, credited, withdrawn, deposited, spent) and an account or card
 *     mask (a/c XX1234, card ending 1234). A friend's "I sent you Rs 500" has none of the second and third.
 * Anything else is counted as "not a bank transaction" and is not parsed or kept. What passes is still only put in To review: nothing reaches the ledger until the person adds it.
 */
object PasteRules {
    sealed interface Evidence {
        data class Bank(val root: String) : Evidence
        data object AlertWords : Evidence
        data object None : Evidence
    }

    private val amount = Regex("""(?i)(?:rs\.?|inr|₹)\s*[0-9]|\b(?:debited|credited|spent|withdrawn)\s+(?:by|for|of|with)\s+[0-9][0-9,]*\.[0-9]{2}\b""")
    private val moved = Regex("""(?i)\b(?:debited|credited|withdrawn|deposited|spent)\b""")
    private val mask = Regex("""(?i)(?:\b(?:a/?c|acct|account|card)\b\s*(?:no\.?)?\s*(?:ending(?:\s+in|\s+with)?)?\s*[:*]?\s*[x*]*[0-9]{3,6}\b|\bending\s+(?:in\s+|with\s+)?[0-9]{4}\b|\b[x*]{2,}[0-9]{3,6}\b)""")

    /** Names that are also ordinary words are matched in capitals only ("slice" the issuer is "SLICE"). */
    private val plainWords = setOf("slice")

    private val banks: List<Pair<String, Regex>> by lazy {
        // the root itself (HDFCBK) and its display name (HDFC Bank); the first built-in root that matches wins
        SenderRules.BUILT_IN.flatMap { (root, name) ->
            listOf(root to Regex("""\b${Regex.escape(root)}\b""", RegexOption.IGNORE_CASE), root to wordsOf(name, name.lowercase() in plainWords))
        }
    }

    private fun wordsOf(name: String, capitalsOnly: Boolean): Regex =
        Regex("""\b${Regex.escape(if (capitalsOnly) name.uppercase() else name)}\b""", if (capitalsOnly) emptySet() else setOf(RegexOption.IGNORE_CASE))

    fun evidence(body: String): Evidence {
        banks.firstOrNull { (_, re) -> re.containsMatchIn(body) }?.let { return Evidence.Bank(it.first) }
        return if (amount.containsMatchIn(body) && moved.containsMatchIn(body) && mask.containsMatchIn(body)) Evidence.AlertWords else Evidence.None
    }
}

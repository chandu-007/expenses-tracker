package app.expenses.core.parsers

import kotlinx.datetime.LocalDate

enum class Direction { DEBIT, CREDIT, DECLINED }

/**
 * How far a parse can be trusted. Only a rule written and tested for one specific bank layout may reach
 * [BANK_RULE], which is the only level the review inbox may auto-confirm (FR-CAP-12). Everything the generic
 * parser produces is [GENERIC]: on 144 public samples it read 2 outgoing payments with the wrong sign, so a
 * generic result must always be shown to the user before it reaches the books.
 */
enum class Confidence { GENERIC, BANK_RULE }

/** What one bank alert says. Amounts are in minor units (paise). Everything is parsed on the device. */
data class ParsedAlert(
    val direction: Direction,
    val amountMinor: Long,
    val currency: String,
    val date: LocalDate?,
    val reference: String?,
    val accountLast4: String?,
    val viaUpi: Boolean,
    /** For foreign-currency spends the alert shows both amounts; [amountMinor] is the rupees charged, this is what was spent abroad. */
    val originalCurrency: String? = null,
    val originalAmountMinor: Long? = null,
    val confidence: Confidence = Confidence.GENERIC,
)

sealed interface ParseResult {
    data class Parsed(val alert: ParsedAlert) : ParseResult

    /** The message was understood well enough to know it is not a transaction (OTP, due notice, ...). */
    data class NotATransaction(val reason: String) : ParseResult
}

/**
 * Bank-independent extractor for Indian transaction alerts. It exists to measure how far one set of
 * generic rules gets before per-bank rules are needed; see docs/PHASE0_PROGRESS.md for the numbers.
 * It never looks at anything but the message body, and it refuses OTP messages before reading further.
 */
object GenericAlertParser {
    private val otp = Regex("""(?i)\botp\b|one[\s-]?time\s+pass|\bcvv\b|\bpin\b\s+is""")
    private val dueNotice = Regex("""(?i)\bis\s+due\b|\bamount\s+payable\b|\bdue\s+(?:date|on|by)\b|min(?:imum)?\s+(?:amount\s+)?due|total\s+(?:amount\s+)?due|statement\s+(?:is\s+)?(?:generated|ready)|login""")
    private val amountToken = Regex("""(?i)(rs\.?|inr|₹)[\s:]*([0-9][0-9,]*(?:\.[0-9]{1,2})?)|\b([A-Z]{3})\s*([0-9][0-9,]*\.[0-9]{2})\b|\b(?:debited|credited|spent|paid|received|sent|withdrawn|deducted)\s+(?:by|for|of|with)\s+([0-9][0-9,]*\.[0-9]{2})\b""")
    private val balanceContext = Regex("""(?i)(?:bal|avl|avail|limit|due|outstanding|minimum)[a-z .:]*$""")
    private val declined = Regex("""(?i)\b(?:declined|unsuccessful|insufficient\s+funds?)\b|\b(?:txn|transaction|payment)\b[^.]{0,40}\bfailed\b|\bfailed\b[^.]{0,20}\b(?:txn|transaction)\b""")
    private val creditWords = Regex("""(?i)\b(?:credited|received|deposited|refund(?:ed)?|reversed|reversal|added|earned)\b|\bCr\b\.?""")
    /** "sent by RAVI", "paid by RAVI": the other person did it, so the money came in. The words are taken out before the direction is read. */
    private val doneByOther = Regex("""(?i)\b(?:sent|paid|transferred)\s+by\b""")
    /** "Chandu. sent ₹10 to you", "Anita paid you ₹120", "Ravi sent you Rs.50": the other person paid, so the money came in. Read as "received" before the direction is decided. */
    private val doneToYou = Regex("""(?i)\b(?:sent|paid|transferred)\s+(?:to\s+you\b|you\b|(?:₹|rs\.?|inr)\s*[0-9][0-9,]*(?:\.[0-9]{1,2})?\s+to\s+you\b)""")
    private val debitWords = Regex("""(?i)\b(?:debited|spent|sent|paid|withdrawn|purchase|transferred|deducted|used|redeemed)\b|\bDr\b\.?""")
    /** Roots with [BankRule.cardTxnLayout] only (HDFC Bank: HDFCBK and HDFCBN): its card alert has no "debited" or "spent": "Txn Rs.20.00 On HDFC Bank Card 0862 At <merchant> by UPI <ref>". A card used at a merchant is money out. */
    private val cardTxn = Regex("""(?is)^\s*txn\b.*\bcard\b.*\bat\b""")
    /** "Payment of Rs.706.82 successful for your Jio bill": the person paid, with no debit word in the text. */
    private val paymentSuccessful = Regex("""(?i)\bpayment\s+of\s+(?:rs\.?|inr|₹)\s*[0-9][0-9,]*(?:\.[0-9]{1,2})?\s+(?:is\s+|was\s+|has\s+been\s+)?(?:successful|successfully)\b""")
    /** "credited to the beneficiary account" describes an outgoing payment seen from the sender's bank. */
    private val outgoingCreditPhrase = Regex("""(?i)\bbeneficiary\b""")
    private val accountMask = Regex("""(?i)(?:a/?c|acct|account)\s*(?:no\.?)?\s*[:*]?\s*[x*]*(\d{3,6})(?:\b|-)""")
    private val cardMask = Regex("""(?i)(?:card\s*(?:no\.?)?|ending(?:\s+in)?)\s*[:*]?\s*[x*]*(\d{4})\b""")
    private val refAfterWord = Regex("""(?i)(?:ref(?:erence)?(?:\s*(?:no\.?|number))?|txn\s*no|transaction\s+(?:no|number)|trxn\s*no|rrn|utr(?:\s*no)?)\.?\s*[:\-]?\s*([a-z]*\d[a-z0-9]{8,21})\b""")
    private val refAfterUpi = Regex("""(?i)\bupi\s+(\d{9,18})\b""")
    private val refInUpiPath = Regex("""(?i)upi/[a-z0-9]+/(\d{9,18})""")
    private val upiWord = Regex("""(?i)\bupi\b|@[a-z]{2,}""")

    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val ymd = Regex("""\b(20\d{2})[-:/](\d{1,2})[-:/](\d{1,2})\b""")
    private val dMonY = Regex("""(?i)\b(\d{1,2})[-/ ]([a-z]{3})[a-z]*[-/ ,]*(\d{2,4})\b""")
    private val compactDate = Regex("""(?i)\b(\d{1,2})([a-z]{3})(\d{2,4})\b""")
    private val dmy = Regex("""\b(\d{1,2})[-/](\d{1,2})[-/](\d{2,4})\b""")

    /**
     * [senderRoot] is the bank's header root when the text came from one ([SenderRules]); a bank-specific wording is applied only for that bank, so a rule
     * learned from one bank's text never decides what another bank's text means.
     */
    fun parse(body: String, senderRoot: String? = null): ParseResult {
        // "Kindly ignore, if already paid" is not a payment: the words are read without it
        val text = PaidDisclaimer.strip(body.replace(Regex("\\s+"), " ").trim())
        if (otp.containsMatchIn(text)) return ParseResult.NotATransaction("otp")
        val amounts = amounts(text)
        val charged = amounts.firstOrNull { it.first == "INR" } ?: amounts.firstOrNull() ?: return ParseResult.NotATransaction("no amount")
        val original = amounts.firstOrNull()?.takeIf { it.first != charged.first }
        val (currencyCode, amount) = charged
        if (dueNotice.containsMatchIn(text) && !creditWords.containsMatchIn(text) && !debitWords.containsMatchIn(text)) {
            return ParseResult.NotATransaction("due notice")
        }
        val readAs = doneToYou.replace(text, " received ")
        val directional = doneByOther.replace(readAs, " by ")
        val direction = when {
            declined.containsMatchIn(text) -> Direction.DECLINED
            else -> {
                val c = creditWords.find(readAs)?.range?.first
                // the passive wording only settles a text that also has a credit word: "paid by your card at X" alone is still a debit
                val d = (if (c != null) debitWords.find(directional) else debitWords.find(readAs))?.range?.first
                when {
                    c == null && d == null -> if ((senderRoot?.let { BankRules.byRoot[it]?.cardTxnLayout } == true && cardTxn.containsMatchIn(text)) || paymentSuccessful.containsMatchIn(text)) Direction.DEBIT else return ParseResult.NotATransaction("no direction")
                    c != null && outgoingCreditPhrase.containsMatchIn(readAs) -> Direction.DEBIT
                    c == null -> Direction.DEBIT
                    d == null -> Direction.CREDIT
                    c < d -> Direction.CREDIT
                    else -> Direction.DEBIT
                }
            }
        }
        return ParseResult.Parsed(
            ParsedAlert(
                direction = direction,
                amountMinor = amount,
                currency = currencyCode,
                date = findDate(text),
                reference = refAfterWord.find(text)?.groupValues?.get(1) ?: refInUpiPath.find(text)?.groupValues?.get(1) ?: refAfterUpi.find(text)?.groupValues?.get(1),
                accountLast4 = (accountMask.find(text) ?: cardMask.find(text))?.groupValues?.get(1)?.takeLast(4),
                viaUpi = upiWord.containsMatchIn(text),
                originalCurrency = original?.first,
                originalAmountMinor = original?.second,
            ),
        )
    }

    /**
     * Every amount that is not a balance, limit or due figure, in reading order, with its currency code. Rupee
     * markers map to INR; a bare three-letter code before a decimal amount (USD 12.00) is taken as that currency.
     * Minor units assume two decimal places; the app's currency table decides the real exponent when it stores the
     * entry.
     */
    private fun amounts(text: String): List<Pair<String, Long>> = amountToken.findAll(text).mapNotNull { m ->
        val before = text.substring(maxOf(0, m.range.first - 14), m.range.first)
        when {
            balanceContext.containsMatchIn(before) -> null
            m.groupValues[2].isNotEmpty() -> "INR" to toMinor(m.groupValues[2])
            // SBI's wording has no currency mark: "debited by 349.00"
            m.groupValues[5].isNotEmpty() -> "INR" to toMinor(m.groupValues[5])
            else -> m.groupValues[3] to toMinor(m.groupValues[4])
        }
    }.toList()

    private fun toMinor(s: String): Long {
        val clean = s.replace(",", "")
        val whole = clean.substringBefore('.')
        val frac = clean.substringAfter('.', "").padEnd(2, '0').take(2)
        return whole.toLong() * 100 + frac.toLong()
    }

    private fun findDate(text: String): LocalDate? {
        ymd.find(text)?.let { m -> date(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { return it } }
        dMonY.find(text)?.let { m ->
            val month = months.indexOf(m.groupValues[2].lowercase()) + 1
            if (month > 0) date(year(m.groupValues[3]), month, m.groupValues[1].toInt())?.let { return it }
        }
        for (m in compactDate.findAll(text)) {
            val month = months.indexOf(m.groupValues[2].lowercase()) + 1
            if (month > 0) date(year(m.groupValues[3]), month, m.groupValues[1].toInt())?.let { return it }
        }
        dmy.find(text)?.let { m -> date(year(m.groupValues[3]), m.groupValues[2].toInt(), m.groupValues[1].toInt())?.let { return it } }
        return null
    }

    private fun year(s: String): Int = if (s.length == 2) 2000 + s.toInt() else s.toInt()

    private fun date(y: Int, m: Int, d: Int): LocalDate? = try {
        LocalDate(y, m, d)
    } catch (_: IllegalArgumentException) {
        null
    }
}

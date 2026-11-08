package app.expenses.core.parsers

import kotlinx.datetime.LocalDate

/** Who says the money moved: the sender's own customer paying ([PAID]: it left an account or wallet), or the biller or card issuer saying it got the payment ([RECEIVED]). */
enum class BillPaymentSide { PAID, RECEIVED }

/**
 * A text that confirms a bill or a credit card bill was **paid**. It is neither a bill to pay (nothing is due any more) nor spending (the purchases were the spending): for a card it
 * is money moving between the person's own accounts, for a utility it is the bill settled.
 *
 * @property cardBank the issuer of the CARD paid, named as [BankRules] names issuers ("Axis Bank", "HDFC Bank", "SBI Card", "American Express"), when the words name it next to the word card.
 * It is NOT the sender: "Axis Bank Credit Card bill payment of Rs. 100 was successful! T&C -Kotak811" says Axis (the card) and signs as Kotak (the paying bank, so that is the other bank).
 * CRED, PhonePe, Paytm and Amazon Pay are never a card bank. Null for a utility, and when the words name no card issuer.
 * @property cardLast4 the card's digits as printed (three or four), for a card only.
 * @property biller Jio, BESCOM... ([BillParser]'s table) when the words name one; for a card it is null.
 * @property payerLast4 digits of the account (or the card, for a utility paid with a card) the money left, when the words say so: side [BillPaymentSide.PAID] only.
 */
data class BillPaymentNotice(
    val side: BillPaymentSide,
    val kind: BillKind,
    val amountMinor: Long,
    val currency: String = "INR",
    val cardBank: String? = null,
    val cardLast4: String? = null,
    val biller: String? = null,
    val payerLast4: String? = null,
    val reference: String? = null,
    val date: LocalDate? = null,
)

/**
 * Reads the confirmation of a bill payment from the words alone. **Why it exists:** "Axis Bank Credit Card bill payment of Rs. 12,345.67 was successful!" carries the words of a bill
 * ("bill ... of") and none of a debit, so it was read as a NEW BILL of Rs 12,345.67. A confirmation is a payment that happened: [read] finds those, and [CaptureClassifier] asks it
 * BEFORE the bill rule.
 *
 * A sentence counts when it has all three of: a payment subject ("bill payment", "credit card payment", "<Bank> Card payment", "payment of Rs X towards/for ... bill, card, EMI, premium, loan",
 * "Rs X credited to your card", "thank you for your payment" and "we have received your payment" next to a card or bill, "you have paid Rs X towards ... bill"), a confirmation word (successful,
 * received, processed, completed, credited, done) and an amount. A sentence that asks for money (due, pay by, pay your), promises it (will be, scheduled, pending, initiated) or offers a reward
 * (cashback, offer, win, refund, reversal) never counts, and neither does a text with a one-time password or a failed, declined or unsuccessful payment. Amounts are masked first, so the
 * "." of "Rs." does not end a sentence. Who is speaking decides the side: "received" / "credited" / "thank you for your payment", and "<Bank> Card payment ... successful" (the card's own
 * issuer), are [BillPaymentSide.RECEIVED]; everything else that went through ("bill payment ... successful", "credit card payment ... processed") is [BillPaymentSide.PAID].
 * Only the body is read; whether the sender may be read at all is decided before this runs.
 */
object BillPayments {
    private const val A = "\uE000"
    private const val B = "\uE001"

    /** The card companies a card bill is paid to, as words ("sbi card" is also written "sbicard"). The display names are in [issuers]. */
    private const val ISSUER = """(?:axis|hdfc|icici|sbi|kotak(?:\s*811|\s+mahindra)?|amex|american\s+express|citi(?:bank)?|idfc(?:\s+first)?|indusind|rbl|yes\s+bank|pnb|federal\s+bank|hsbc|standard\s+chartered|bob|canara|union\s+bank|idbi|bandhan|onecard|slice)"""
    private const val ISSUER_CARD = """$ISSUER(?:\s*bank)?\s*(?:credit\s*)?card"""
    private const val OBJECT = """(?:bill|credit\s*card|\bcc\b|emi|premium|loan|instal+ment|$ISSUER_CARD)"""
    private const val CARD_WORD = """(?:credit\s*card|\bcc\b|$ISSUER_CARD)"""

    private val whitespace = Regex("\\s+")
    private val otp = Regex("""(?i)\botp\b|one[\s-]?time\s+pass|\bcvv\b|\bpin\b\s+is""")
    private val failure = Regex("""(?i)\b(?:fail(?:ed|ure)?|unsuccessful|declin\w*|reject\w*|insufficient|unable|cancel+ed)\b|\bnot\s+(?:been\s+)?(?:successful|received|processed|completed|credited|made|debited)\b|\bwas\s+not\b|\bcould\s*n[o']?t\b""")

    private val amount = Regex("""(?i)(?:\b(?:rs|inr)\.?|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
    private val balanceBefore = Regex("""(?i)(?:bal|avl|avail|limit|due|outstanding|minimum)[a-z .:]*$""")
    private val sentenceEnd = Regex("""[.!?;]+(?:\s+|$)""")

    /** A sentence that asks for money, promises it, or sells something: never a confirmation. */
    private val notAConfirmation = Regex("""(?i)\b(?:pending|in\s+progress|initiated|scheduled)\b|\b(?:will|shall)\s+be\b|\bto\s+be\b|\bis\s+due\b|\bdue\s+(?:date|on|by)\b|\bpay\s+(?:your|the|now|by|before)\b|\bwin\b|\boffers?\b|\bcashback\b|\brefund\w*|\brevers\w*|\bwaiver\b""")
    private val confirmation = Regex("""(?i)\b(?:successful(?:ly)?|received|processed|completed|credited|done|made)\b|\bthank\s*you\s+for\b""")

    /** The card (or account) a bill was paid WITH is not the card paid: "using HDFC Bank Credit Card XX1234" is taken out before the subject is judged. */
    private val instrument = Regex("""(?i)\b(?:using|via|with|through|by|from)\s+(?:your\s+)?(?:(?!towards\b|for\b|to\b)[\w&]+\s+){0,3}(?:credit\s*card|debit\s*card|card|cc)\b""")
    private val debitCard = Regex("""(?i)\bdebit\s*card\b""")

    private val billPayment = Regex("""(?i)\b(?:bill|emi|premium|loan|dues?|instal+ment)\s+pay(?:ment)?s?\b|\bcard\s+bill\s+pay|\b$CARD_WORD\s+(?:bill\s+)?pay(?:ment)?s?\b""")
    private val ownCardPayment = Regex("""(?i)\b$ISSUER_CARD\s+pay(?:ment)?s?\b""")
    private val paymentOf = Regex("""(?i)\bpay(?:ment)?\s+of\s+$A[^.]{0,80}?(?:\b(?:towards|for)\b[^.]{0,40}?\b$OBJECT|\bto\s+(?:your\s+)?(?:[\w&]+\s+){0,2}$CARD_WORD)""")
    private val creditedToCard = Regex("""(?i)$A\s+(?:has\s+been\s+|was\s+|is\s+)?credited\s+to\s+(?:your\s+)?(?:(?!debit\b)[\w&]+\s+){0,3}(?:card|cc)\b""")
    private val thankYou = Regex("""(?i)\bthank\s*you\s+for\s+(?:your\s+)?(?:bill\s+)?payment\b|\breceived\s+your\s+(?:bill\s+)?payment\b""")
    private val youPaid = Regex("""(?i)\byou(?:'ve|\s+have)\s+(?:successfully\s+)?paid\s+$A\s+(?:towards|for)\b[^.]{0,40}?\b$OBJECT""")
    private val objectIn = Regex("""(?i)\b$OBJECT""")
    private val cardTarget = Regex("""(?i)\bcard\b|\bcc\b""")
    private val receivedWords = Regex("""(?i)\breceived\b|\bcredited\b|\bthank\s*you\s+for\b""")
    private val receivedFromSomeone = Regex("""(?i)\breceived\s+from\s+(?!your\b)""")

    private val cardDigitsAfterCard = Regex("""(?i)\b(?:credit\s*card|card|cc)\b[^.\d]{0,25}?(?:ending(?:\s+(?:in|with))?|no\.?|number|[x*]+)\s*[:#]?\s*[x*]*(\d{3,4})(?!\d)""")
    private val endingDigits = Regex("""(?i)\bending\s*(?:in|with)?\s*[:*]?\s*[x*]*(\d{3,4})(?!\d)""")
    private val accountWord = Regex("""(?i)\ba/?c\b|\bacct\b|\baccount\b""")
    private val accountDigits = Regex("""(?i)\b(?:a/?c|acct|account)\s*(?:no\.?|number)?\s*(?:ending(?:\s+(?:in|with))?)?\s*[:*]?\s*[x*]*(\d{3,4})(?!\d)""")
    private val reference = Regex("""(?i)\b(?:ref(?:erence)?|rrn|utr|txn|transaction)\.?\s*(?:no\.?|number|id)?\s*[:\-#]?\s*([a-z]*\d[a-z0-9]{7,21})\b|\bupi\s+(\d{9,18})\b""")

    /** What the signature of a text is: the words after "T&C", "Regards" or a trailing "-Name". Taken out before a card's bank is looked for, so "-Kotak811" is never the card's bank. */
    private val signature = Regex("""(?i)\bt\s*&\s*cs?\b.*$|\b(?:regards|team|thanks)\b.*$|\s[-–—~]\s*[A-Za-z0-9][A-Za-z0-9 ]{1,24}$|[!.]\s*[-–—~]\s*[A-Za-z0-9][A-Za-z0-9 ]{1,24}$""")
    private val afterIssuerAccount = Regex("""(?i)^(?:\s*bank)?\s*(?:a/?c|acct|account)\b""")
    private val afterIssuerCard = Regex("""(?i)^(?:\s*bank)?\s*(?:credit\s*)?(?:card|cc)\b""")
    private val beforeIssuerInstrument = Regex("""(?i)\b(?:from|using|via|with|through|by)\s+(?:your\s+)?$""")

    /** The display name of each card company, as [BankRules] writes issuers; a test keeps them equal. */
    internal val issuers: List<Pair<Regex, String>> = listOf(
        Regex("""(?i)\baxis\b""") to "Axis Bank", Regex("""(?i)\bhdfc\b""") to "HDFC Bank", Regex("""(?i)\bicici\b""") to "ICICI Bank", Regex("""(?i)\bsbi\s*card\b|\bsbi\b""") to "SBI Card",
        Regex("""(?i)\bkotak(?:\s*811|\s+mahindra)?\b""") to "Kotak Mahindra Bank", Regex("""(?i)\bamex\b|\bamerican\s+express\b""") to "American Express",
        Regex("""(?i)\bciti(?:bank)?\b""") to "Citibank", Regex("""(?i)\bidfc(?:\s+first)?\b""") to "IDFC FIRST Bank", Regex("""(?i)\bindusind\b""") to "IndusInd Bank", Regex("""(?i)\brbl\b""") to "RBL Bank",
        Regex("""(?i)\byes\s+bank\b""") to "Yes Bank", Regex("""(?i)\bpnb\b""") to "Punjab National Bank", Regex("""(?i)\bfederal\s+bank\b""") to "Federal Bank", Regex("""(?i)\bhsbc\b""") to "HSBC",
        Regex("""(?i)\bstandard\s+chartered\b""") to "Standard Chartered", Regex("""(?i)\bbob(?:\s*card)?\b""") to "Bank of Baroda", Regex("""(?i)\bcanara\b""") to "Canara Bank",
        Regex("""(?i)\bunion\s+bank\b""") to "Union Bank of India", Regex("""(?i)\bidbi\b""") to "IDBI Bank", Regex("""(?i)\bbandhan\b""") to "Bandhan Bank", Regex("""(?i)\bonecard\b""") to "OneCard",
        Regex("""(?i)\bslice\b""") to "slice",
    )

    private fun toMinor(s: String): Long {
        val clean = s.replace(",", "")
        return clean.substringBefore('.').toLong() * 100 + clean.substringAfter('.', "").padEnd(2, '0').take(2).toLong()
    }

    /** The bill payment [body] confirms, or null when it is not a confirmation of one. */
    fun read(body: String): BillPaymentNotice? {
        val text = PaidDisclaimer.strip(body.replace(whitespace, " ").trim())
        if (otp.containsMatchIn(text) || failure.containsMatchIn(text)) return null
        val amounts = ArrayList<Long>()
        val masked = amount.replace(text) { m ->
            if (balanceBefore.containsMatchIn(text.substring(maxOf(0, m.range.first - 14), m.range.first))) B else { amounts += toMinor(m.groupValues[1]); A }
        }
        if (amounts.isEmpty()) return null

        var start = 0
        var before = 0
        val cuts = sentenceEnd.findAll(masked).map { it.range.last + 1 }.toList() + masked.length
        for (end in cuts) {
            val sentence = masked.substring(start, end)
            start = end
            val here = sentence.count { it == A[0] }
            val index = before
            before += here
            val found = judge(sentence, masked) ?: continue
            val value = amounts.getOrNull(index).takeIf { here > 0 } ?: amounts.first()
            if (value <= 0) continue
            return build(found, value, text, masked)
        }
        return null
    }

    private class Found(val side: BillPaymentSide, val card: Boolean)

    /** Whether one sentence of the masked text confirms a bill payment, and from which side. */
    private fun judge(sentence: String, whole: String): Found? {
        if (notAConfirmation.containsMatchIn(sentence)) return null
        val own = debitCard.replace(sentence, " ")
        val s = instrument.replace(own, " ")
        val wholeKept = instrument.replace(debitCard.replace(whole, " "), " ")
        val thanks = thankYou.containsMatchIn(s)
        val subject = billPayment.containsMatchIn(s) || paymentOf.containsMatchIn(s) || creditedToCard.containsMatchIn(s) || (thanks && objectIn.containsMatchIn(wholeKept)) || youPaid.containsMatchIn(s)
        if (!subject) return null
        if (!youPaid.containsMatchIn(s) && !creditedToCard.containsMatchIn(s) && !confirmation.containsMatchIn(s)) return null
        val card = cardTarget.containsMatchIn(if (thanks) wholeKept else s)
        val someoneElse = receivedFromSomeone.containsMatchIn(s) && !card
        val received = creditedToCard.containsMatchIn(s) || (receivedWords.containsMatchIn(s) && !someoneElse) || (ownCardPayment.containsMatchIn(s) && !billPaymentWord.containsMatchIn(s))
        if (someoneElse && !received) return null
        return Found(if (received) BillPaymentSide.RECEIVED else BillPaymentSide.PAID, card)
    }

    private val billPaymentWord = Regex("""(?i)\bbill\s+pay""")

    private fun build(found: Found, value: Long, text: String, masked: String): BillPaymentNotice {
        val kind = if (found.card) BillKind.CREDIT_CARD else BillParser.kindOf(text, allowCard = false)
        val cardDigits = cardDigits(masked)
        val payer = if (found.side == BillPaymentSide.PAID) accountDigits.find(masked)?.groupValues?.get(1) ?: if (!found.card) cardDigits else null else null
        return BillPaymentNotice(
            side = found.side,
            kind = kind,
            amountMinor = value,
            cardBank = if (found.card) cardBank(masked) else null,
            cardLast4 = if (found.card) cardDigits else null,
            biller = if (found.card) null else BillParser.billerIn(text),
            payerLast4 = payer,
            reference = reference.find(masked)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }?.takeIf { it.isNotEmpty() },
            date = GenericAlertParser.findDate(text),
        )
    }

    /** The digits the text prints for a card ("Credit Card XX1234", "card ending 4321"); never an account's ("from A/c XX5678"). */
    private fun cardDigits(masked: String): String? {
        for (m in cardDigitsAfterCard.findAll(masked)) {
            if (masked.regionStartsDebit(m.range.first) || accountWord.containsMatchIn(m.value)) continue
            return m.groupValues[1]
        }
        for (m in endingDigits.findAll(masked)) {
            if (accountWord.containsMatchIn(masked.substring(maxOf(0, m.range.first - 16), m.range.first))) continue
            return m.groupValues[1]
        }
        return null
    }

    private fun String.regionStartsDebit(at: Int): Boolean = substring(maxOf(0, at - 6), at).contains("debit", ignoreCase = true)

    /**
     * The bank whose card was paid: a bank named right before the word card (and not the bank the money was paid FROM, nor the signature), else the first bank named that is not an
     * account's. Null when the words name none.
     */
    private fun cardBank(masked: String): String? {
        val text = signature.replace(masked, "")
        class Mention(val at: Int, val end: Int, val name: String)
        val mentions = issuers.flatMap { (re, name) -> re.findAll(text).map { Mention(it.range.first, it.range.last + 1, name) } }.sortedBy { it.at }
        fun fromInstrument(m: Mention) = beforeIssuerInstrument.containsMatchIn(text.substring(maxOf(0, m.at - 14), m.at))
        val ahead = { m: Mention -> text.substring(m.end) }
        return mentions.firstOrNull { afterIssuerCard.containsMatchIn(ahead(it)) && !fromInstrument(it) }?.name
            ?: mentions.firstOrNull { afterIssuerCard.containsMatchIn(ahead(it)) }?.name
            ?: mentions.firstOrNull { !afterIssuerAccount.containsMatchIn(ahead(it)) && !fromInstrument(it) }?.name
    }
}

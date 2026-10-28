package app.expenses.core.parsers

/**
 * Turns the words of a bank text into something a person can safely send to the developer when the app did not understand it: the **structure** of the message with everything
 * that identifies a person, an account or a payment taken out. Pure text in, text out: nothing is stored or sent from here, and the person reads the result before choosing to copy
 * or mail it.
 *
 * What is removed (default): every run of five or more digits (account, card, phone, reference and UTR numbers, IFSC and PAN shapes) becomes `X`s of the same length; a masked tail
 * of three or four digits after `A/c`, `Card`, `ending` or `XX` becomes zeros (`XX0000`: the shape without which card); the part before the `@` of a UPI address or email becomes
 * `xxxx`; the path and query of a link are dropped, the domain stays; a person's name after `to`, `from`, `by`, `Dear` or leading the text becomes `NAME`; a one-time code becomes `X`s;
 * and every amount keeps only its shape, `Rs.N,NNN.NN`, because a comma or a missing decimal is often the whole parser bug. With [keepAmounts] **only the amount of the payment itself**
 * stays as it was: an amount is kept when the words around it (back to the start of the sentence, at most 40 characters, and forward the same way) say money moved ("debited",
 * "credited", "spent", "paid", "received", "sent", "withdrawn", "deducted", "purchase", "txn", "refund" ...) and none of them speaks of a balance, limit, outstanding or closing figure or a
 * due ("avl", "available", "bal", "limit", "outstanding", "closing", "due", "minimum", "payable"): those win, so balances, limits and dues are **always** masked, and so is any amount whose
 * wording is not recognised. A bill's amount is a due, so with the amounts kept a bill reminder still has its figure hidden. Words, dates and times, the bank's header and the order of
 * the sentences are never touched.
 */
object MessageRedactor {
    /** Placeholders keep amounts out of the digit rules while they are being processed; private-use characters, so no real text has them. */
    private const val OPEN = '\uE000'
    private const val CLOSE = '\uE001'

    /** A currency-marked amount (group 1 the marker, 2 the number) or, failing that, a bare decimal such as "349.00" (group 3). */
    private val amountToken = Regex("""(?i)((?:rs\.?|inr|₹)\s*[:\-]?\s*)(\d[\d,]*(?:\.\d{1,2})?)|\b(\d[\d,]*\.\d{2})\b""")

    /** Words that say a figure is a balance, limit, outstanding or closing amount or a due: any one of them in an amount's clause masks it, whatever else the clause says. */
    private val notThePayment = Regex("""(?i)\b(?:bal|avl|avail|lmt|limit|outstanding|closing|due|dues|minimum|payable)""")

    /** Words that say money moved (or was tried): an amount is kept only when its clause has one. */
    private val moneyMoved = Regex("""(?i)\b(?:debit|credit|spen[dt]|paid|receiv|sent\b|withdr|deduct|purchas|txn|trxn|transact|transf|refund|revers|payment|declin|fail|unsuccess|used|mandate|redeem|deposit|earn|added|clear)|\b(?:dr|cr)\b""")

    /** How far, in characters, the words around an amount are read for its clause. */
    private const val CLAUSE = 40

    /** How far after an amount a balance or due word still describes it. */
    private const val AFTER = 15

    private val link = Regex("""(?i)\b((?:[a-z][a-z0-9+.\-]*://|www\.)[^\s/?#]+|(?:[a-z0-9\-]+\.)+(?:com|in|co|io|net|org|me|ly|app|link|page|to|bank|ai)(?=[/?#]))(?:[/?#]\S*)?""")
    private val address = Regex("""[A-Za-z0-9_][\w.\-+]*@([A-Za-z][A-Za-z0-9\-]*(?:\.[A-Za-z][A-Za-z0-9\-]*)*)""")
    private val tollFree = Regex("""\b1[89]00[\s-]?\d{3}[\s-]?\d{3,4}\b""")
    private val landline = Regex("""\b0\d{2,4}[\s-]\d{6,8}\b|\b0\d{2,4}[\s-]\d{3,4}[\s-]\d{3,4}\b""")
    /** "UPI/P2A/628374659283/RAHUL SHARMA/BANK": a bank's slash-separated reference lists the other person right after the number. */
    private val slashName = Regex("""(?i)\b((?:upi|neft|imps|rtgs)/(?:[A-Za-z0-9\-]+/)*?[A-Za-z0-9\-]*\d{5,}[A-Za-z0-9\-]*/)([A-Za-z][A-Za-z .'\-]{1,40}?)(?=/|\n|$)""")
    private val otpCode = Regex("""(?i)\b((?:otp|code|pin|cvv|password|passcode)\b[^0-9\n]{0,25})(\d{3,8})\b""")
    private val pan = Regex("""\b[A-Z]{5}\d{4}[A-Z]\b""")
    private val plate = Regex("""\b[A-Z]{2}\d{2}[A-Z]{1,3}\d{4}\b""")
    private val maskedTail = Regex("""((?:[xX*]{2,}[\s-]*)+)(\d{3,6})\b""")
    private val accountTail = Regex("""(?i)\b((?:a/?c|acct?|account|card|ending(?:\s+(?:in|with))?)\s*(?:no\.?|number)?\s*[:\-]?\s*)(\d{3,4})\b""")
    private val referenceValue = Regex("""(?i)\b((?:ref(?:erence)?|rrn|utr|txn|trxn|transaction|order|chq|cheque|imps|neft|rtgs|id)\b\.?\s*(?:no\.?|number|id)?\s*[:\-#]?\s*)([A-Za-z0-9]{6,})""")
    private val digitRun = Regex("""\d{5,}""")
    /** Digits written in groups ("4111 1111 1111 1234", "98 7654 3210", "1234-5678-9012"): a card, Aadhaar or phone number whose groups are each too short for [digitRun]. */
    private val spacedDigits = Regex("""\b\d{2,6}(?: \d{2,6}){1,5}\b""")
    private val dashedDigits = Regex("""\b\d{2,6}(?:-\d{2,6}){1,5}\b""")
    /** A date written as day, month and year in either order and with either separator: eight digits in groups, but not a number. */
    private val dateShape = Regex("""\d{1,2}[ -]\d{1,2}[ -]\d{2,4}|\d{4}[ -]\d{1,2}[ -]\d{1,2}""")

    private val nameTrigger = Regex("""(?i)\b(?:to|from|by)\s+""")
    /** "to ravi kumar 9876543210" or "from lakshmi narayana xxxx@ybl": plain lower-case words that stand right before a phone number or an address are the person's name. */
    private val lowerNameBeforeContact = Regex("""(?i)\b(?:to|from|by)\s+((?:[a-z][a-z'\-]*\s+){1,4})(?=xxxx@|\+?\d[\d -]{8,})""")
    /** A masked account number, then words in capitals: IMPS and NEFT texts name the account holder there ("to A/c XX5678 RAVI KUMAR on"). */
    private val afterAccount = Regex("""(?i)[xX*]{2,}\d{3,6}\s+""")
    /** "due to INSUFFICIENT FUNDS": a reason, not an addressee. */
    private val notAnAddressee = setOf("due", "according", "thanks", "subject", "up", "owing", "prior", "next", "close", "similar", "relating", "equal", "pursuant", "thank")
    private val dear = Regex("""\b(?:Dear|DEAR|Hi|Hello)\s+(?:(?:Mr|Mrs|Ms|Shri|Smt|Dr)\.?\s+)?""")
    private val title = Regex("""\b(?:Mr|Mrs|Ms|Shri|Smt|Sri)\.?\s+""")
    private val leadingName = Regex("""^([A-Za-z][A-Za-z .'\-]{1,30}?)\.?\s+(?:sent|paid)\s+(?:you\s+)?(?:₹|rs\.?|inr)""", RegexOption.IGNORE_CASE)
    private val titleName = Regex("""^([A-Za-z][A-Za-z .'\-]{1,30}?):\s""")

    /** Words after "to / from / by" that are not a person: rails, instruments, filler, greetings that are not names. Banks' own words are added from [BankRules]. */
    private val notNames: Set<String> = setOf(
        "upi", "vpa", "a", "ac", "acct", "account", "card", "bank", "you", "your", "the", "us", "me", "self", "imps", "neft", "rtgs", "atm", "pos", "emi", "nach", "ecs", "rs", "inr", "usd",
        "date", "dt", "on", "at", "via", "ref", "reference", "txn", "debit", "credit", "customer", "cardmember", "member", "user", "sir", "madam", "client", "valued", "wallet", "phonepe",
        "paytm", "gpay", "google", "pay", "bhim", "amazon", "payment", "payments", "transaction", "alert", "dear", "info", "notice", "update", "reminder", "money", "received", "sent", "paid",
        "insufficient", "funds", "fund", "unsuccessful", "declined", "exceeded", "invalid", "incorrect", "blocked", "expired", "refno", "utr", "rrn", "id", "avl", "bal", "balance", "available", "limit", "call", "not", "if", "dated", "debited", "credited", "cr", "dr", "successful", "failed", "bill", "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec", "january", "february", "march", "april", "june", "july", "august",
        "total", "amount", "amt", "due", "dues", "minimum", "min", "statement", "outstanding", "payable", "closing", "opening", "summary", "details", "detail", "status", "mode", "type", "channel", "merchant",
        "location", "branch", "remarks", "narration", "description", "purpose", "savings", "current", "salary", "my", "an", "number", "no",
        "september", "october", "november", "december", "mon", "tue", "wed", "thu", "fri", "sat", "sun", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "recharge", "refund", "offer", "cashback", "otp", "code", "online", "mobile", "net", "banking", "internet", "app", "text", "sms", "email", "mail",
    ) + BankRules.ALL.flatMap { r -> (r.issuer.lowercase().split(' ', '-', '.') + r.root.lowercase()).filter { it.isNotEmpty() } }

    /** A capitalised word that is not a rail, a bank, a month or the shape of a masked amount ("NNN"). */
    private fun isNameWord(w: String, capsOnly: Boolean = false): Boolean {
        val bare = w.trimEnd('.', '-')
        return bare.isNotEmpty() && bare[0].isUpperCase() && bare.lowercase() !in notNames && bare.none { it.isDigit() } && !bare.all { it == 'N' || it == '.' || it == ',' } && (!capsOnly || (bare.length >= 2 && bare.all { it.isUpperCase() || it == '\'' || it == '-' }))
    }

    /** Digit groups joined by single spaces or dashes that add up to eight digits or more become `X`s (the separators stay), unless they are a date. */
    private fun groupedDigits(text: String): String {
        fun mask(m: MatchResult): String {
            val digits = m.value.count { it.isDigit() }
            return if (digits >= 8 && !dateShape.matches(m.value)) m.value.map { if (it.isDigit()) 'X' else it }.joinToString("") else m.value
        }
        return dashedDigits.replace(spacedDigits.replace(text, ::mask), ::mask)
    }

    /** The text with the person's details taken out, as described on [MessageRedactor]. */
    fun redact(body: String, keepAmounts: Boolean = false): String {
        val kept = ArrayList<String>()
        var t = body.replace("\r\n", "\n").trim()
        t = amounts(t, keepAmounts, kept)
        t = link.replace(t) { m -> m.groupValues[1] + if (m.value.length > m.groupValues[1].length) "/xxxx" else "" }
        t = address.replace(t) { m -> "xxxx@" + m.groupValues[1] }
        t = slashName.replace(t) { m -> m.groupValues[1] + "NAME" }
        t = tollFree.replace(t, "XXXXXXXXXX")
        t = landline.replace(t, "XXXXXXXXXX")
        t = names(t)
        t = otpCode.replace(t) { m -> m.groupValues[1] + "X".repeat(m.groupValues[2].length) }
        t = pan.replace(t, "XXXXXXXXXX")
        t = plate.replace(t, "XXXXXXXXXX")
        t = groupedDigits(t)
        t = maskedTail.replace(t) { m -> m.groupValues[1] + (if (m.groupValues[2].length <= 4) "0" else "X").repeat(m.groupValues[2].length) }
        t = accountTail.replace(t) { m -> m.groupValues[1] + "0".repeat(m.groupValues[2].length) }
        t = referenceValue.replace(t) { m -> if (m.groupValues[2].any { it.isDigit() }) m.groupValues[1] + "X".repeat(m.groupValues[2].length) else m.value }
        t = digitRun.replace(t) { "X".repeat(it.value.length) }
        for ((i, original) in kept.withIndex()) t = t.replace("$OPEN$i$CLOSE", original)
        return t
    }

    private fun shape(number: String): String = number.map { if (it.isDigit()) 'N' else it }.joinToString("")

    /**
     * Whether the character at [i] ends a sentence: a ";" or "?", or a "." that follows a word of three letters or more or a digit and is followed by white space or the end
     * ("Rs.500", "Dr. INR 7.00" and "A/c." do not end one). Line breaks and "!" do not: a bank's headline ("Amt Deducted!") and its figure are often on two lines.
     */
    private fun breaksAt(text: String, i: Int): Boolean = when (text[i]) {
        ';', '?' -> true
        '.' -> (i + 1 >= text.length || text[i + 1].isWhitespace()) && (i >= 1 && text[i - 1].isDigit() || (i >= 3 && text[i - 1].isLetter() && text[i - 2].isLetter() && text[i - 3].isLetter()))
        else -> false
    }

    private class Amount(val start: Int, val end: Int, val prefix: String, val number: String)

    /**
     * Masks every amount to its shape; with [keep], leaves the ones that are the payment itself (see [MessageRedactor]). The clause of an amount is the words before it, back to the
     * start of its sentence, the previous amount or [CLAUSE] characters, and the words after it up to the end of its sentence, the next amount or [CLAUSE] characters.
     */
    private fun amounts(text: String, keep: Boolean, kept: MutableList<String>): String {
        val found = ArrayList<Amount>()
        for (m in amountToken.findAll(text)) {
            val s = m.range.first
            val e = m.range.last + 1
            if (m.groups[3] != null) {
                // a dotted date ("05.06.26") or a time ("12.30.15") is not an amount
                val dotted = (s >= 2 && text[s - 1] == '.' && text[s - 2].isDigit()) || (e + 1 < text.length && text[e] == '.' && text[e + 1].isDigit())
                if (dotted) continue
                found += Amount(s, e, "", m.groupValues[3])
            } else found += Amount(s, e, m.groupValues[1], m.groupValues[2])
        }
        val out = StringBuilder()
        var last = 0
        for ((i, a) in found.withIndex()) {
            out.append(text, last, a.start).append(a.prefix)
            if (keep && isThePayment(text, a, found.getOrNull(i - 1)?.end ?: 0, found.getOrNull(i + 1)?.start ?: text.length)) { kept += a.number; out.append(OPEN).append(kept.size - 1).append(CLOSE) }
            else out.append(shape(a.number))
            last = a.end
        }
        out.append(text, last, text.length)
        return out.toString()
    }

    private fun isThePayment(text: String, a: Amount, previousEnd: Int, nextStart: Int): Boolean {
        var from = maxOf(previousEnd, a.start - CLAUSE)
        for (k in a.start - 1 downTo from) if (breaksAt(text, k)) { from = k + 1; break }
        var to = minOf(nextStart, a.end + CLAUSE)
        for (k in a.end until to) if (breaksAt(text, k)) { to = k; break }
        val before = text.substring(from, a.start)
        val after = text.substring(a.end, to)
        if (notThePayment.containsMatchIn(before)) return false
        // a word after the amount only speaks for it when it comes first and close ("Rs.5,000 is due on", "Rs.88 available balance"), not when it belongs to the next figure of the text
        val labelled = notThePayment.find(after.take(AFTER))?.range?.first
        val moved = moneyMoved.find(after)?.range?.first
        if (labelled != null && (moved == null || labelled < moved)) return false
        return moneyMoved.containsMatchIn(before) || moved != null
    }

    /** Names of people: after "to / from / by", after a greeting, and leading a payment app's text. */
    private fun names(text: String): String {
        var t = text
        t = maskAfter(t, nameTrigger)
        t = lowerNameBeforeContact.replace(t) { m -> if (m.groupValues[1].trim().split(Regex("\\s+")).any { it.lowercase() in notNames }) m.value else m.value.replace(m.groupValues[1], "NAME ") }
        t = maskAfter(t, dear)
        t = maskAfter(t, title)
        t = maskAfter(t, afterAccount, capsOnly = true)
        leadingName.find(t)?.let { m -> val name = m.groupValues[1]; if (name.split(' ', '.').filter { it.isNotEmpty() }.all { isNameWord(it) || it.length == 1 }) t = t.replaceRange(m.groups[1]!!.range, "NAME") }
        titleName.find(t)?.let { m -> val name = m.groupValues[1]; if (name.split(' ', '.').filter { it.isNotEmpty() }.all { isNameWord(it) }) t = t.replaceRange(m.groups[1]!!.range, "NAME") }
        return t
    }

    /** After every match of [trigger], up to five capitalised words that are not a rail or a bank become NAME. */
    private fun maskAfter(text: String, trigger: Regex, capsOnly: Boolean = false): String {
        val out = StringBuilder()
        var last = 0
        for (m in trigger.findAll(text)) {
            val start = m.range.last + 1
            if (start < last) continue
            if (trigger === nameTrigger && text.substring(0, m.range.first).trimEnd().substringAfterLast(' ').lowercase().trim(',', '.') in notAnAddressee) continue
            var i = start
            var words = 0
            var end = start
            while (words < 5 && i < text.length) {
                var j = i
                while (j < text.length && (text[j].isLetter() || text[j] == '\'' || text[j] == '-')) j++
                if (j == i) break
                val word = text.substring(i, j).trimEnd('-')
                val initial = word.length == 1 && word[0].isUpperCase() && j < text.length && text[j] == '.'
                if (!(initial || isNameWord(word, capsOnly)) || (j < text.length && (text[j] == '/' || text[j] == '@'))) break
                words++; end = i + word.length
                // a full stop ends the sentence, unless it follows an initial ("R.K. SHARMA", "R. Kumar"): then the name goes on
                if (j < text.length && text[j] == '.') {
                    if (word.length != 1) break
                    j++
                    end = j
                }
                i = j
                while (i < text.length && text[i] == ' ') i++
            }
            if (words == 0) continue
            out.append(text, last, start).append("NAME"); last = end
        }
        out.append(text, last, text.length)
        return out.toString()
    }
}

/** What the app made of a text, in words for the report. Read from the sender and words with the very rules capture uses. */
object MessageReport {
    /** A bank text is a few hundred characters; a longer paste (a whole email) is cut here so the report stays a size a person reads and a mail app takes. */
    const val MAX_CHARS = 4000

    /** One line saying how the app read the text: the first thing a fix needs to know. */
    fun verdict(sender: String?, body: String, extraRoots: Set<String> = emptySet()): String {
        if (sender.isNullOrBlank()) return "no sender (a pasted message)"
        return when (val o = CaptureClassifier.classify(sender, body, extraRoots)) {
            is CaptureOutcome.Transaction -> "read as ${if (o.alert.direction == Direction.CREDIT) "money in" else "money out"} by the " + (if (o.alert.confidence == Confidence.BANK_RULE) "bank's own rule" else "generic rules (low confidence)")
            is CaptureOutcome.Bill -> "read as a bill to pay"
            is CaptureOutcome.Ignored -> when (o.reason) {
                IgnoreReason.PERSONAL_SENDER -> "sent from a phone number, so never read"
                IgnoreReason.NOT_ALLOWLISTED -> "sender is not on the list, so not read"
                IgnoreReason.OTP -> "taken for a one-time password"
                IgnoreReason.NOT_A_TRANSACTION -> "from a listed sender but no amount or direction was found"
                IgnoreReason.DECLINED -> "taken for a declined payment"
            }
        }
    }

    /** The sender as it may be shown: a registered header is not personal, a phone number is never shown. */
    fun senderText(sender: String?): String = when {
        sender.isNullOrBlank() -> "none (pasted)"
        SenderRules.headerRoot(sender) == null -> "a phone number (not shown)"
        else -> sender.trim()
    }

    /** The whole report for a message and the sender it came from: how the app reads it is worked out here. [version] and [system] are the app's and the phone's (no device model, no ledger data). */
    fun build(sender: String?, body: String, keepAmounts: Boolean, version: String, system: String, extraRoots: Set<String> = emptySet()): String =
        compose(senderText(sender), verdict(sender, body, extraRoots), body, keepAmounts, version, system)

    /** The report with the sender and the reading already put in words (an item in To review was read by capture long ago, so it is not classified again). */
    fun compose(senderText: String, verdict: String, body: String, keepAmounts: Boolean, version: String, system: String): String = buildString {
        appendLine("A bank message the app did not understand or read wrongly (details removed on this phone before sharing).")
        appendLine("Sender: $senderText")
        appendLine("The app: $verdict")
        appendLine("Amounts: ${if (keepAmounts) "only the amount of the payment kept; balances, limits and dues hidden" else "hidden, only their shape is shown"}")
        appendLine("---")
        appendLine(MessageRedactor.redact(body.take(MAX_CHARS), keepAmounts))
        appendLine("---")
        append("App version: $version\nSystem: $system")
    }
}

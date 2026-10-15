package app.expenses.core.parsers

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** What a bill is for. [label] is how it reads in a sentence ("Your electricity bill"). */
enum class BillKind(val label: String) {
    CREDIT_CARD("credit card bill"), ELECTRICITY("electricity bill"), WATER("water bill"), GAS("gas bill"), MOBILE("mobile bill"),
    BROADBAND("broadband bill"), DTH("DTH bill"), INSURANCE("insurance premium"), LOAN_EMI("loan EMI"), OTHER("bill"),
}

/**
 * A bill found in a text or a notification: something to pay, not something that was paid. [amountMinor] is the total due; [minDueMinor] is the minimum
 * a credit card asks for. [dueDate] is null when the message gives none. [autoDebit] marks a message that says the amount will be taken from the account
 * by itself (an EMI, a premium): nothing needs paying, but the account needs the money. [reference] is a consumer or customer number, never a card number.
 */
data class BillNotice(
    val kind: BillKind,
    val amountMinor: Long,
    val minDueMinor: Long?,
    val dueDate: LocalDate?,
    val accountLast4: String?,
    val reference: String?,
    val biller: String?,
    val autoDebit: Boolean,
    val currency: String = "INR",
)

/**
 * Finds a bill in the words of a message. It is deliberately strict: a text must say that something is **due or payable** and carry an amount, and it must
 * not say that a payment was **made**. A payment that went through is a transaction, which the transaction parser reads. Like the transaction parser it
 * looks only at the message body; whether the sender may be read at all is decided before this runs.
 */
object BillParser {
    private val otp = Regex("""(?i)\botp\b|one[\s-]?time\s+pass|\bcvv\b|\bpin\b\s+is""")

    /** A payment that happened: this is a receipt, not a bill. "Payments received after 05-Oct are not shown" is not one. */
    private val completed = Regex(
        """(?i)\b(?:payment|amount)\b[^.]{0,60}\b(?:was|has\s+been|is)\s+(?:successfully\s+)?(?:received|credited|processed|made)\b|\bpayment\s+of\b[^.]{0,40}\b(?:received|successful)\b""" +
            """|thank\s*you\s*for\s*(?:your\s*)?payment|\bhas\s+been\s+(?:paid|debited)\b|\bpaid\s+successfully""",
    )
    private val junk = Regex("""(?i)pre[- ]?approved|loan\s+up\s+to|instant\s+loan|\blucky\b|congratulations|\bwin\b""")

    /** Something is owed or will be taken: due, payable, a bill or statement that is ready, a pay-by date, an EMI or premium falling due, an auto-debit. */
    private val cue = Regex(
        """(?i)\b(?:due|payable|overdue)\b(?!\s+to\b)|\bpay(?:ment)?\s+(?:by|before|on\s+or\s+before)\b|\blast\s+date\b|\bbill\b[^.]{0,40}\b(?:generated|ready|amount|of)\b""" +
            """|\bstatement\b[^.]{0,40}\b(?:generated|ready)\b|\bwill\s+be\s+(?:auto[- ]?)?debited\b|\bauto[- ]?debit\b|\bscheduled\s+(?:to\s+be\s+)?debit""",
    )
    /** An explicit deadline or a promised debit: what separates "pay Rs 900 by 25-Oct" from a spend alert that quotes "Due Rs 5000" as a balance. */
    private val deadline = Regex(
        """(?i)\bdue\s*(?:date|on|by)\b|\bis\s+due\b|\bpay(?:ment)?\s+(?:by|before|on\s+or\s+before)\b|\blast\s+date\b|\bwill\s+be\s+(?:auto[- ]?)?debited\b|\bscheduled\s+(?:to\s+be\s+)?debit""",
    )
    private val futureDebit = Regex("""(?i)will\s+be\s+(?:auto[- ]?)?debited|scheduled\s+(?:to\s+be\s+)?debit""")

    /** Words of something that already happened to money. A text with these is a transaction unless it also states a deadline. */
    private val happened = Regex("""(?i)\b(?:spent|debited|credited|cleared|refund(?:ed)?|withdrawn|used\s+at|purchase|charged)\b""")

    private val autoDebitWords = Regex("""(?i)will\s+be\s+(?:auto[- ]?)?debited|auto[- ]?debit|standing\s+instruction|mandate|scheduled\s+(?:to\s+be\s+)?debit""")

    private const val NUM = """([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    private const val CUR = """(?:rs\.?|inr|₹)"""
    private val minDue = Regex("""(?i)(?:min(?:imum)?\.?\s*(?:amt\.?|amount)?\s*due|\bmad\b|\bmin\.?\s*(?:amt|payment))\s*[:\-]?\s*(?:is\s*)?(?:$CUR\s*)?$NUM""")
    private val totalDue = Regex(
        """(?i)(?:total\s*(?:amt\.?|amount)?\s*(?:due|payable)|\btad\b|(?:amount|amt\.?)\s*(?:due|payable)|bill\s*(?:amount|amt\.?)|net\s*payable|outstanding(?:\s*(?:amount|amt\.?|balance))?)""" +
            """\s*[:\-]?\s*(?:is\s*)?(?:$CUR\s*)?$NUM""",
    )
    private val ofAmount = Regex("""(?i)\b(?:bill|statement|emi|premium|instal+ment|dues?|payment)\b[^.0-9]{0,30}?\b(?:of|for|is|amount)\b\s*[:\-]?\s*$CUR\s*$NUM""")
    private val anyAmount = Regex("""(?i)$CUR\s*$NUM""")
    private val balanceBefore = Regex("""(?i)(?:bal(?:ance)?|avl|avail(?:able)?|limit)[a-z .:]*$""")

    private val last4 = Regex("""(?i)\b(?:card|cc|a/?c|account|acct)\b\s*(?:no\.?|number|ending(?:\s+(?:in|with))?)?\s*[:#]?\s*[x*]*(\d{4})\b""")
    private val consumer = Regex("""(?i)\b(?:consumer|customer|cust|connection|ca|account|k|service)\s*(?:no|number|id|num)\.?\s*[:#\-]?\s*([A-Za-z0-9]{5,16})\b""")

    private val monthNames = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val textDate = Regex("""(?i)\b(\d{1,2})(?:st|nd|rd|th)?[-/ .]([a-z]{3,9})\.?(?:[-/ ,.]+(\d{4}|\d{2})\b)?""")
    private val numDate = Regex("""\b(\d{1,2})[-/.](\d{1,2})[-/.](\d{4}|\d{2})\b""")
    private val shortDate = Regex("""\b(\d{1,2})[-/](\d{1,2})\b""")
    private val dueLabel = Regex("""(?i)(?:due(?:\s*(?:date|on|by))?|pay(?:ment)?(?:\s*(?:by|before|on))?|last\s+date|on\s+or\s+before|before|by|debited\s+on|debit\s+on|on)\s*[:\-]?\s*$""")
    private val notDueLabel = Regex("""(?i)(?:statement|bill|billing|invoice|issued?|reading|generated)\s*(?:date|on)?\s*[:\-]?\s*$|\bdated\s*[:\-]?\s*$""")

    private val kindCard = Regex("""(?i)credit\s*card|\bcard\b[^.]{0,20}(?:ending|xx|\d{4})|\bstatement\b|\bcc\b""")
    private val kindElectric = Regex("""(?i)electric|power\s*(?:bill|supply|corp)|\bkwh\b|\bunits?\s+consumed|discom|bescom|msedcl|tneb|tangedco|\bbses\b|\bconsumer\s*(?:no|number)""")
    private val kindWater = Regex("""(?i)water\s*(?:bill|board|supply|charges)|jal\s*board|bwssb""")
    private val kindGas = Regex("""(?i)\bpng\b|piped\s*gas|gas\s*bill|\bgas\b[^.]{0,20}\bbill|mahanagar|indraprastha""")
    private val kindMobile = Regex("""(?i)postpaid|mobile\s*bill|(?:jio|airtel|vodafone|\bvi\b|bsnl)[^.]{0,30}\bbill""")
    private val kindBroadband = Regex("""(?i)broadband|fib(?:re|er)|wi-?fi|internet\s*bill|act\s*fibernet""")
    private val kindDth = Regex("""(?i)\bdth\b|tata\s*(?:play|sky)|dish\s*tv|\bd2h\b|sun\s*direct""")
    private val kindInsurance = Regex("""(?i)\bpremium\b|\bpolicy\b|insurance|\blic\b""")
    private val kindLoan = Regex("""(?i)\bemi\b|instal+ment|\bloan\b""")

    private val billers = listOf(
        Regex("""(?i)\bbescom\b""") to "BESCOM", Regex("""(?i)\bmsedcl\b""") to "MSEDCL", Regex("""(?i)\b(?:tneb|tangedco)\b""") to "TNEB", Regex("""(?i)\bbses\b""") to "BSES",
        Regex("""(?i)tata\s*power""") to "Tata Power", Regex("""(?i)adani""") to "Adani Electricity", Regex("""(?i)\bjio\b""") to "Jio", Regex("""(?i)\bairtel\b""") to "Airtel",
        Regex("""(?i)vodafone|\bvi\b""") to "Vi", Regex("""(?i)\bbsnl\b""") to "BSNL", Regex("""(?i)act\s*fibernet""") to "ACT Fibernet", Regex("""(?i)tata\s*(?:play|sky)""") to "Tata Play",
        Regex("""(?i)dish\s*tv""") to "Dish TV", Regex("""(?i)\blic\b""") to "LIC", Regex("""(?i)hdfc\s*life""") to "HDFC Life", Regex("""(?i)mahanagar""") to "Mahanagar Gas",
        Regex("""(?i)indraprastha""") to "Indraprastha Gas",
    )

    private fun toMinor(s: String): Long {
        val clean = s.replace(",", "")
        return clean.substringBefore('.').toLong() * 100 + clean.substringAfter('.', "").padEnd(2, '0').take(2).toLong()
    }

    /** True when [body] is a message about something to pay. Cheap; the same checks as [parse] without reading the details. */
    fun looksLikeBill(body: String): Boolean = parse(body, null, LocalDate(2000, 1, 1)) != null

    /**
     * The bill in [body], or null when it is not one. [today] only resolves a due date written without a year: the nearest such date that is not
     * more than about six weeks past.
     */
    fun parse(body: String, senderRoot: String?, today: LocalDate): BillNotice? {
        val text = body.replace(Regex("\\s+"), " ").trim()
        if (otp.containsMatchIn(text) || completed.containsMatchIn(text) || junk.containsMatchIn(text) || !cue.containsMatchIn(text)) return null
        if (happened.containsMatchIn(futureDebit.replace(text, "")) && !deadline.containsMatchIn(text)) return null

        val min = minDue.find(text)?.let { toMinor(it.groupValues[1]) }?.takeIf { it > 0 }
        val total = totalDue.find(text)?.let { toMinor(it.groupValues[1]) }?.takeIf { it > 0 }
            ?: ofAmount.find(text)?.let { toMinor(it.groupValues[1]) }?.takeIf { it > 0 }
            ?: anyAmount.findAll(text).firstNotNullOfOrNull { m ->
                val v = toMinor(m.groupValues[1])
                v.takeIf { it > 0 && it != min && !balanceBefore.containsMatchIn(text.substring(maxOf(0, m.range.first - 14), m.range.first)) }
            }
        // a text that gives only the minimum: that is the figure it asks for
        val amount = total ?: min ?: return null
        val minimum = if (total != null) min?.takeIf { it < total } else null

        val kind = when {
            kindCard.containsMatchIn(text) && !kindElectric.containsMatchIn(text) -> BillKind.CREDIT_CARD
            kindElectric.containsMatchIn(text) -> BillKind.ELECTRICITY
            kindWater.containsMatchIn(text) -> BillKind.WATER
            kindGas.containsMatchIn(text) -> BillKind.GAS
            kindDth.containsMatchIn(text) -> BillKind.DTH
            kindBroadband.containsMatchIn(text) -> BillKind.BROADBAND
            kindMobile.containsMatchIn(text) -> BillKind.MOBILE
            kindInsurance.containsMatchIn(text) -> BillKind.INSURANCE
            kindLoan.containsMatchIn(text) -> BillKind.LOAN_EMI
            else -> BillKind.OTHER
        }
        val card = last4.find(text)?.groupValues?.get(1)
        val ref = if (kind == BillKind.CREDIT_CARD) null else consumer.find(text)?.groupValues?.get(1)?.takeIf { it != card && it.any { c -> c.isDigit() } }
        return BillNotice(
            kind, amount, minimum, dueDate(text, today), card, ref, billers.firstOrNull { it.first.containsMatchIn(text) }?.second, autoDebitWords.containsMatchIn(text),
        )
    }

    /** One date written in the text: where it starts, and the date itself with the year filled in when the text left it out. */
    private data class Found(val start: Int, val date: LocalDate)

    private fun dates(text: String, today: LocalDate): List<Found> {
        val out = ArrayList<Found>()
        for (m in textDate.findAll(text)) {
            val month = monthNames.indexOf(m.groupValues[2].lowercase().take(3)) + 1
            if (month == 0 || !monthNames.any { m.groupValues[2].lowercase().startsWith(it) }) continue
            resolve(m.groupValues[1].toInt(), month, m.groupValues[3].takeIf { it.isNotEmpty() }, today)?.let { out += Found(m.range.first, it) }
        }
        for (m in numDate.findAll(text)) resolve(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3], today)?.let { out += Found(m.range.first, it) }
        // "On 10-10": a day and a month with no year, taken only straight after a due-style label so a stray "1-2" is not a date
        for (m in shortDate.findAll(text)) {
            if (out.any { m.range.first in it.start..(it.start + 12) }) continue
            if (dueLabel.containsMatchIn(text.substring(maxOf(0, m.range.first - 18), m.range.first))) resolve(m.groupValues[1].toInt(), m.groupValues[2].toInt(), null, today)?.let { out += Found(m.range.first, it) }
        }
        return out.sortedBy { it.start }
    }

    private fun resolve(day: Int, month: Int, year: String?, today: LocalDate): LocalDate? {
        fun make(y: Int) = runCatching { LocalDate(y, month, day) }.getOrNull()
        if (year != null) return make(if (year.length == 2) 2000 + year.toInt() else year.toInt())
        val thisYear = make(today.year) ?: return null
        // no year: this year's date unless it is already well past, then next year's
        return if (thisYear.daysUntil(today) > 45) make(today.year + 1) else thisYear
    }

    /** The date the money is due: the one written after a due-style label, else the only date there is (when it is not a statement or bill date). */
    private fun dueDate(text: String, today: LocalDate): LocalDate? {
        val all = dates(text, today)
        for (f in all) {
            val before = text.substring(maxOf(0, f.start - 26), f.start)
            if (notDueLabel.containsMatchIn(before)) continue
            if (dueLabel.containsMatchIn(before)) return f.date
        }
        val usable = all.filter { !notDueLabel.containsMatchIn(text.substring(maxOf(0, it.start - 26), it.start)) }
        return usable.singleOrNull()?.date
    }

    /** Days from [today] to [date], negative once it has passed. */
    fun daysUntil(today: LocalDate, date: LocalDate): Int = today.daysUntil(date)

}

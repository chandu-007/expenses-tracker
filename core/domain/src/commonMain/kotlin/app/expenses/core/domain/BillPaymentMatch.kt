package app.expenses.core.domain

import kotlinx.datetime.LocalDate

/**
 * A bill the person has not paid yet, as far as matching a payment confirmation goes. [kind] is the bill parser's kind name ("CREDIT_CARD", "ELECTRICITY", "MOBILE" ...), [issuer] who sent it
 * (a card's own bank for a card statement), [last4] the card digits it names, [biller] the utility ("Jio"). [minDueMinor] is the least a card asks for. [id] "card:<account>" is a card
 * statement the app works out from its billing dates (no text behind it), which a payment settles by being recorded.
 */
data class OpenBill(
    val id: String, val kind: String, val issuer: String, val last4: String?, val biller: String?, val title: String,
    val amountMinor: Long, val minDueMinor: Long?, val currency: String, val dueDate: LocalDate?, val receivedOn: LocalDate,
)

/**
 * Which open bill a payment confirmation ("Axis Bank credit card bill payment of Rs 12,345.67 was successful") pays. Pure: the caller hands over the bills, the payment, and the day it
 * was made. It decides in three steps and never guesses between different bills:
 *  1. **Whose bill**: a card payment pays a card bill; the card is named by its digits when both sides have them (they must be [AccountDigits.same]), else by the bank's name when both have one
 *     ([BankNames.sameBrand]); a payment that names neither matches a card bill only when exactly one card bill is open. A utility payment pays a bill of the same kind (or one with no kind) from
 *     the same biller (or bank/issuer name when there is no biller).
 *  2. **How much**: a card bill is paid by its total, its minimum, or anything between (a rupee over the total is rounding); a payment under the minimum or far over the total is not this
 *     bill's payment. Any other bill is paid in full: the amount differs by at most a rupee or one per cent.
 *  3. **When**: the payment is within a month and a half before the due date to a month after it; a bill with no due date is paid within 60 days after its text arrived.
 * The best is the exact total, then the exact minimum, then a part payment; then the nearest due date; the answer is the same whatever order [bills] come in (ties by id).
 */
object BillPaymentMatch {
    enum class Fit { TOTAL, MINIMUM, PART, FULL }

    data class Hit(val bill: OpenBill, val fit: Fit)

    private const val CARD = "CREDIT_CARD"

    /**
     * The open bills [paid] pays, best first. [cardBank] and [cardLast4] are what the confirmation names about the card; [biller] the utility; [kind] its bill kind name; [amountMinor] and
     * [currency] what was paid and [on] the day it was paid.
     */
    fun candidates(kind: String, cardBank: String?, cardLast4: String?, biller: String?, amountMinor: Long, currency: String, on: LocalDate, bills: List<OpenBill>): List<Hit> {
        val open = bills.filter { it.currency == currency }
        val isCard = kind == CARD
        val sameKind = open.filter { if (isCard) it.kind == CARD else it.kind != CARD && (it.kind == kind || kind == "OTHER" || it.kind == "OTHER") }
        // a card payment that names neither its digits nor its bank pays a card bill only when there is exactly one to pay
        val cardUnnamed = isCard && cardLast4 == null && cardBank == null
        val whose = sameKind.filter { b ->
            val bankSays = cardBank?.let { BankNames.sameBrand(it, b.issuer) || b.issuer.equals(it, ignoreCase = true) }
            if (isCard) {
                val digitsSay = if (cardLast4 != null && b.last4 != null) AccountDigits.same(cardLast4, b.last4) else null
                when {
                    digitsSay == false -> false
                    // the same digits at another bank are another card
                    bankSays == false && BankNames.wordsOf(b.issuer).isNotEmpty() -> false
                    digitsSay == true || bankSays == true -> true
                    cardLast4 != null || cardBank != null -> false
                    else -> true
                }
            } else when {
                biller != null && b.biller != null -> biller.equals(b.biller, ignoreCase = true)
                biller != null -> BankNames.sameBrand(biller, b.issuer) || b.issuer.equals(biller, ignoreCase = true)
                else -> bankSays == true
            }
        }.let { if (cardUnnamed && it.size != 1) emptyList() else it }
        val tolerance = maxOf(100L, amountMinor / 100)
        return whose.mapNotNull { b ->
            val fit = when {
                isCard && amountMinor == b.amountMinor -> Fit.TOTAL
                isCard && b.minDueMinor != null && amountMinor == b.minDueMinor -> Fit.MINIMUM
                isCard && amountMinor <= b.amountMinor + 100 && amountMinor >= (b.minDueMinor ?: b.amountMinor) - 100 -> if (amountMinor >= b.amountMinor - 100) Fit.TOTAL else Fit.PART
                !isCard && kotlin.math.abs(amountMinor - b.amountMinor) <= tolerance -> Fit.FULL
                else -> return@mapNotNull null
            }
            val d = b.dueDate
            val day = on.toEpochDays().toLong()
            val inTime = if (d != null) day >= d.toEpochDays().toLong() - 45 && day <= d.toEpochDays().toLong() + 30
            else day >= b.receivedOn.toEpochDays().toLong() && day <= b.receivedOn.toEpochDays().toLong() + 60
            if (inTime) Hit(b, fit) else null
        }.sortedWith(compareBy<Hit>({ it.fit.ordinal }, { it.bill.dueDate?.let { d -> kotlin.math.abs(d.toEpochDays().toLong() - on.toEpochDays().toLong()) } ?: Long.MAX_VALUE }, { it.bill.id }))
    }
}

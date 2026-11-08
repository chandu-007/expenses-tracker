package app.expenses.core.parsers

import kotlinx.datetime.LocalDate

/**
 * Which bills an EARLIER version made from the confirmation of a payment ("Axis Bank Credit Card bill payment of Rs. 12,345.67 was successful!" was filed as a new bill), so that the person can move
 * them to To review as bill payments. It decides from what the bill still holds, and is deliberately narrow: nothing is touched unless every one of these is true, each of which a bill made by that
 * mistake satisfies and a bill the person has acted on does not:
 *  - it is still FOUND: the person has not added it to their bills, paid it, or skipped it (a tracked bill is theirs now);
 *  - it came from a text (SMS) and still holds the words it was made from;
 *  - no payment is linked to it and it has no paid time;
 *  - the words read, with today's rules, as the confirmation of a payment ([BillPayments]) and NOT as something that asks for money ([BillParser]): a text that is both is left alone;
 *  - the bill's amount is the amount the words say was paid;
 *  - its due date is empty, or is the one date the words carry (the parser took the payment's own date for a due date): any other date was typed by the person.
 * Pure: the caller reads the bill, this judges it. Nothing is stored or changed here.
 */
object BillPaymentRepair {
    /** The payment confirmation the bill was wrongly made from, or null when the bill must be left alone (see the class). [dueDate] is `yyyy-MM-dd` or null. */
    fun notice(source: String, status: String, body: String?, amountMinor: Long, dueDate: String?, entryId: String?, paidAt: Long?): BillPaymentNotice? {
        if (source != "SMS" || status != "FOUND" || entryId != null || paidAt != null) return null
        val text = body?.takeIf { it.isNotBlank() } ?: return null
        val notice = BillPayments.read(text) ?: return null
        if (BillParser.parse(text, null, LocalDate(2000, 1, 1)) != null || BillParser.asksForMoney(text)) return null
        if (notice.amountMinor != amountMinor) return null
        if (dueDate != null && dueDate != notice.date?.toString()) return null
        return notice
    }
}

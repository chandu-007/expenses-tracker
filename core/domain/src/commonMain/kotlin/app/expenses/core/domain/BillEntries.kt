package app.expenses.core.domain

/**
 * "Each item as its own entry": turns the lines of a read bill into one expense draft per line, from the draft the person was filling in (the same account,
 * date, category and tags), so a bill of five items becomes five entries that add up to what was paid.
 */
object BillEntries {
    /** What the bill's own total holds that the ticked lines do not (tax, service charge, rounding): shown as one more entry when it is wanted. Zero when it cannot be told. */
    fun restMinor(bill: ParsedBill, ticked: Set<Int>): Long {
        val printed = bill.printedTotalMinor ?: return 0
        // only when every line is used: with some left out, the total says nothing about the rest
        if (bill.items.isEmpty() || ticked.size != bill.items.size) return 0
        val rest = printed - bill.itemsTotalMinor
        return if (rest > 0) rest else 0
    }

    /** The name an entry gets from a line: the item, and its quantity when more than one. */
    fun noteOf(item: BillItem): String = item.name + if (item.quantity > 1) " ×${item.quantity}" else ""

    /** One draft per line of [items] (and one for [restMinor] when above zero, named [REST_NAME]), each a copy of [base] with its own amount and note. */
    fun drafts(base: EntryDraft, items: List<BillItem>, restMinor: Long, currency: Currency): List<EntryDraft> {
        val lines = items.map { noteOf(it) to it.amountMinor } + if (restMinor > 0) listOf(REST_NAME to restMinor) else emptyList()
        return lines.map { (note, minor) -> base.copy(id = null, kind = DraftKind.EXPENSE, amountText = Money(minor, currency).toPlainString(), note = note) }
    }

    const val REST_NAME = "Tax and other charges"
}

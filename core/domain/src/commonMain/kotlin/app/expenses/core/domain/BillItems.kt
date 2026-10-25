package app.expenses.core.domain

/** One line of a shop bill: [amountMinor] is what the line cost in all (price times [quantity]). */
data class BillItem(val name: String, val amountMinor: Long, val quantity: Int = 1)

/**
 * What was read from the text of a bill. [printedTotalMinor] is the grand total the bill itself shows (null when none was found); it includes tax and
 * discounts, so it can differ from [itemsTotalMinor], the sum of the lines.
 */
data class ParsedBill(val items: List<BillItem>, val printedTotalMinor: Long?) {
    val itemsTotalMinor: Long get() = items.sumOf { it.amountMinor }

    /** Nothing at all could be used: no line items and no printed total. */
    val isEmpty: Boolean get() = items.isEmpty() && printedTotalMinor == null
}

/**
 * Reads the items of a shop or restaurant bill from the text a recogniser found in a photo of it (rows rebuilt by [OcrRows]). Pure: no I/O, no clock.
 *
 * A line is an item when it ends in an amount and has a name: `Milk 500ml  54.00`, `Milk  2 x 27.00  54.00`, `Milk x2 54.00`, `Rice 1kg  Rs.120/-`,
 * `Bananas` on one line with `2 @ 1.50  3.00` on the next. Quantity and unit-price columns are dropped from the name; the last amount on the line is
 * what the line cost. Totals, tax, discounts, change, payment lines and the printed header and footer (phone, GST number, date, thanks) are not items;
 * the bill's own grand total is kept separately. Amounts accept ₹, Rs, INR and /-, and thousands commas (1,234.50 and 1,23,456.00).
 *
 * Assumed, not seen on real bills (none were available when this was written): the layouts in BillItemsTest were invented from how such bills commonly print.
 * A bill whose lines are laid out differently reads as fewer items, never as wrong ones silently applied: the person ticks what to use.
 */
object BillItems {
    /** The longest note the bill reader will write. */
    const val NOTE_LIMIT = 240

    /** No single line of a bill is more than this many whole currency units (10 lakh): anything above is a phone number, a bill number or a misread. */
    private const val MAX_ITEM_MAJOR = 1_000_000L
    private const val MAX_NAME = 60

    private val OPS = setOf("x", "X", "×", "*", "@")
    private val LEADER = Regex("[._=·•~]{2,}|-{3,}")
    private val NON_LETTERS = Regex("[^\\p{L}]+")
    private val DATE = Regex("\\b\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4}\\b")
    private val TIME = Regex("\\b\\d{1,2}:\\d{2}\\b")

    /** Words that make a line something other than an item, wherever they stand in it. */
    private val NOT_ITEM_WORDS = setOf(
        "tax", "gst", "cgst", "sgst", "igst", "vat", "cess", "gstin", "change", "tender", "tendered", "savings", "saving", "discount", "subtotal", "round", "rounding", "roundoff",
        "upi", "visa", "mastercard", "rupay", "paid", "saved", "invoice", "fssai", "thank", "thanks", "www", "http", "https", "email",
    )

    /** Words that make a line a header or payment line when they start it. */
    private val NOT_ITEM_FIRST = setOf(
        "bill", "receipt", "order", "table", "token", "counter", "ph", "phone", "tel", "mob", "mobile", "date", "time", "name", "customer", "cashier", "server", "waiter",
        "pin", "address", "hsn", "visit", "items", "item", "welcome", "no", "cash", "card", "cheque", "wallet", "credit", "debit", "gst", "tin", "cin", "pan",
    )

    /** Next to "total", these make it another figure than the grand total ("total tax", "total qty", "sub total"). */
    private val TOTAL_NEIGHBOURS = setOf(
        "sub", "tax", "taxes", "gst", "cgst", "sgst", "igst", "vat", "cess", "discount", "savings", "saving", "qty", "quantity", "items", "item", "paid", "tendered", "cash",
        "card", "points", "mrp", "round", "rounded", "tip", "charges", "charge",
    )
    private val TOTAL_BLOCKERS = setOf("cash", "card", "upi", "tender", "tendered", "change", "paid")
    private val QTY_WORDS = setOf("qty", "quantity", "qnty")
    private val NAME_HEADS = setOf("item", "items", "description", "particulars", "name", "product")
    private val HEADER_WORDS = setOf("item", "items", "description", "particulars", "name", "product", "rate", "price", "amount", "amt", "mrp", "total", "value")

    private enum class Kind { ITEM, TOTAL, SKIP }

    fun parse(text: String, currency: Currency = Currency.INR): ParsedBill {
        val digits = currency.minorUnitDigits
        var one = 1L
        repeat(digits) { one *= 10 }
        val itemCap = MAX_ITEM_MAJOR * one
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

        // a header with Qty in it says which side of the name the quantity column is on
        var qtyColumn: Boolean? = null // null: no quantity column; true: before the name; false: after it
        for (l in lines) {
            val words = words(l)
            if (words.any { it in QTY_WORDS } && words.count { it in HEADER_WORDS } >= 1) {
                val q = words.indexOfFirst { it in QTY_WORDS }
                val n = words.indexOfFirst { it in NAME_HEADS }
                qtyColumn = n >= 0 && q < n
                break
            }
        }

        val items = ArrayList<BillItem>()
        var printed: Long? = null
        var pendingName: String? = null
        var awaitingTotal = false
        for (line in lines) {
            val tokens = tokens(line)
            val lower = line.lowercase()
            val words = words(line)
            val lastAmount = tokens.lastOrNull()?.let { amount(it, digits) }
            when (kind(words, lower)) {
                Kind.SKIP -> { pendingName = null; awaitingTotal = false }
                Kind.TOTAL -> {
                    pendingName = null
                    val a = tokens.asReversed().firstNotNullOfOrNull { amount(it, digits) }
                    if (a != null && a > 0 && a <= itemCap * 10) { printed = a; awaitingTotal = false }
                    else awaitingTotal = a == null && words.none { it in QTY_WORDS || it in NAME_HEADS || it == "rate" || it == "price" }
                }
                Kind.ITEM -> {
                    if (lastAmount == null) {
                        pendingName = null; awaitingTotal = false
                        if (letters(line) >= 2 && line.length <= MAX_NAME && tokens.isNotEmpty()) pendingName = clean(tokens)
                        continue
                    }
                    if (tokens.size == 1 && awaitingTotal) {
                        awaitingTotal = false
                        if (lastAmount > 0 && lastAmount <= itemCap * 10) printed = lastAmount
                        continue
                    }
                    awaitingTotal = false
                    if (tokens.any { it.length >= 8 && it.all { c -> c in '0'..'9' } }) { pendingName = null; continue } // a phone number, a bill number
                    val read = readItem(tokens, digits, qtyColumn)
                    if (read == null) { pendingName = null; continue }
                    val name = read.name.ifEmpty { pendingName.orEmpty() }
                    pendingName = null
                    if (letters(name) < 2 || read.total <= 0 || read.total > itemCap) continue
                    items += BillItem(name.take(MAX_NAME).trim(), read.total, read.qty)
                }
            }
        }
        // a line dearer than the whole bill is a misreading (a number from the header taken for a price)
        val total = printed
        val kept = if (total != null && items.any { it.amountMinor > total }) items.filter { it.amountMinor <= total }.ifEmpty { items } else items
        return ParsedBill(kept, printed)
    }

    /**
     * What the entry's amount should be: the bill's printed total when every line is used (it includes tax and discounts, and is what was paid), otherwise
     * the sum of the lines that were [ticked] (by position in [ParsedBill.items]).
     */
    fun amountFor(bill: ParsedBill, ticked: Set<Int>): Long {
        val chosen = bill.items.filterIndexed { i, _ -> i in ticked }
        val printed = bill.printedTotalMinor
        return if (printed != null && chosen.size == bill.items.size) printed else chosen.sumOf { it.amountMinor }
    }

    /**
     * The lines as a note: `Milk 54.00, Bread x2 80.00`, after any [existing] note and a separator, never longer than [limit]; lines that do not fit are
     * replaced by `+N more`. A note that is already as long as the limit is returned unchanged.
     */
    fun note(items: List<BillItem>, currency: Currency = Currency.INR, existing: String = "", limit: Int = NOTE_LIMIT): String {
        val head = existing.trim()
        val prefix = if (head.isEmpty()) "" else "$head · "
        if (items.isEmpty() || prefix.length >= limit) return head
        val parts = items.map { i -> i.name + (if (i.quantity > 1) " x${i.quantity}" else "") + " " + Money(i.amountMinor, currency).toPlainString() }
        for (k in parts.size downTo 1) {
            val more = if (k < parts.size) ", +${parts.size - k} more" else ""
            val text = prefix + parts.take(k).joinToString(", ") + more
            if (text.length <= limit) return text
        }
        return (prefix + parts.first()).take(limit).trimEnd()
    }

    private fun words(line: String): List<String> = line.lowercase().split(NON_LETTERS).filter { it.isNotEmpty() }

    private fun letters(s: String) = s.count { it.isLetter() }

    private fun kind(words: List<String>, lower: String): Kind {
        if (DATE.containsMatchIn(lower) || TIME.containsMatchIn(lower)) return Kind.SKIP
        // only numbers: the amounts of an item whose name was on the line above, or the total below its label
        if (words.isEmpty()) return if (lower.any { it in '0'..'9' }) Kind.ITEM else Kind.SKIP
        if (isTotalLine(words)) return Kind.TOTAL
        // any other figure with "total" in it (sub total, total tax, total items, the column heading) is not an item and not the grand total
        if ("total" in words || words.any { it in QTY_WORDS }) return Kind.SKIP
        if (words.any { it in NOT_ITEM_WORDS } || words.first() in NOT_ITEM_FIRST) return Kind.SKIP
        if (lower.contains('@') && lower.contains('.') && lower.none { it == ' ' }) return Kind.SKIP
        return Kind.ITEM
    }

    private fun isTotalLine(words: List<String>): Boolean {
        if (words.any { it in TOTAL_BLOCKERS }) return false
        if ("sub" in words && "total" in words) return false
        if (words.any { it == "grandtotal" || it == "payable" }) return true
        for ((i, w) in words.withIndex()) {
            val before = words.getOrNull(i - 1)
            val after = words.getOrNull(i + 1)
            if (w == "total" && before !in TOTAL_NEIGHBOURS && after !in TOTAL_NEIGHBOURS) return true
            if (w == "amount" && (after == "payable" || after == "due" || before == "net" || before == "bill")) return true
            if (w == "pay" && before == "to") return true
            if (w == "due" && before == "balance") return true
        }
        return false
    }

    private class Read(val name: String, val total: Long, val qty: Int)

    /** The name, the cost of the line and the quantity from the tokens of a line that ends in an amount; null when the tokens do not make a line. */
    private fun readItem(all: List<String>, digits: Int, qtyColumn: Boolean?): Read? {
        val t = all
        val last = amount(t.last(), digits) ?: return null
        val body = t.dropLast(1)
        var qty = 1
        var total = last
        var name: List<String>
        val a1 = body.lastOrNull()
        when {
            // "Milk 2 x 27.00": no separate line total, the one amount is the unit price
            body.size >= 2 && a1 in OPS && isQty(body[body.size - 2]) -> {
                qty = body[body.size - 2].toInt(); total = mul(last, qty) ?: return null; name = body.dropLast(2)
            }
            // "Milk 27.00 x 2": unit price and quantity, no line total
            t.size >= 3 && t[t.size - 2] in OPS && isQty(t.last()) && amount(t[t.size - 3], digits) != null && !isQty(t[t.size - 3]) -> {
                qty = t.last().toInt(); total = mul(amount(t[t.size - 3], digits)!!, qty) ?: return null; name = t.dropLast(3)
            }
            // "Milk 2 x 27.00 54.00" / "Milk 27.00 x 2 54.00"
            body.size >= 3 && body[body.size - 2] in OPS && amount(body.last(), digits) != null && isQty(body[body.size - 3]) -> {
                qty = body[body.size - 3].toInt(); name = body.dropLast(3)
            }
            body.size >= 3 && body[body.size - 2] in OPS && isQty(body.last()) && amount(body[body.size - 3], digits) != null -> {
                qty = body.last().toInt(); name = body.dropLast(3)
            }
            // "Milk x2 54.00"
            body.size >= 2 && body[body.size - 2] in OPS && isQty(body.last()) -> { qty = body.last().toInt(); name = body.dropLast(2) }
            // "Milk 27.00 2 54.00" or "Milk 2 27.00 54.00": quantity and unit price that multiply to the line
            body.size >= 2 && productMatches(body[body.size - 2], body.last(), last, digits) -> {
                val x = body[body.size - 2]; val y = body.last()
                qty = listOf(x, y).filter { isQty(it) }.minOf { it.toInt() }; name = body.dropLast(2)
            }
            qtyColumn == false && body.size >= 2 && isQty(body.last()) -> { qty = body.last().toInt(); name = body.dropLast(1) }
            else -> name = body
        }
        var n = name.toMutableList()
        // a unit price left standing in the name ("Milk 500ml 27.00 54.00")
        while (n.size > 1 && n.last().contains('.') && amount(n.last(), digits) != null) n.removeLast()
        while (n.size > 1 && n.last().endsWith("%")) n.removeLast()
        if (n.isNotEmpty() && n.last() in OPS) n.removeLast()
        // a serial number ("1." or "2)") or an item code before the name
        if (n.size > 1 && n[0].length > 1 && (n[0].endsWith('.') || n[0].endsWith(')')) && n[0].dropLast(1).all { it in '0'..'9' }) n = n.drop(1).toMutableList()
        if (n.size > 1 && n[0].length >= 4 && n[0].all { it in '0'..'9' } && n.drop(1).any { letters(it) > 0 }) n = n.drop(1).toMutableList()
        // "2x Milk" or "2 x Milk" before the name; "2 Milk" only where the header says the quantity comes first
        if (n.size >= 3 && isQty(n[0]) && n[1] in OPS) { qty = n[0].toInt(); n = n.drop(2).toMutableList() }
        else if (qtyColumn == true && n.size >= 2 && isQty(n[0]) && letters(n[1]) > 0) { qty = n[0].toInt(); n = n.drop(1).toMutableList() }
        return Read(clean(n), total, qty)
    }

    private fun mul(minor: Long, qty: Int): Long? = if (minor > Long.MAX_VALUE / 1000) null else minor * qty

    private fun isQty(s: String): Boolean = s.length in 1..3 && s.all { it in '0'..'9' } && s.toInt() in 1..999

    /** Whether [a] and [b] are a quantity and a unit price whose product is [total] (within rounding and a small discount). */
    private fun productMatches(a: String, b: String, total: Long, digits: Int): Boolean {
        val x = amount(a, digits) ?: return false
        val y = amount(b, digits) ?: return false
        fun ok(qty: String, price: Long): Boolean {
            if (!isQty(qty)) return false
            val q = qty.toInt()
            val diff = kotlin.math.abs(price * q - total)
            return diff <= maxOf(q.toLong(), total / 50)
        }
        return ok(a, y) || ok(b, x)
    }

    private fun clean(tokens: List<String>): String =
        tokens.joinToString(" ").trim().trim('.', ':', '-', '_', '=', '*', '#', ',', ';', '|', ' ')

    /** The pieces of a line: currency marks removed, "x2", "2x" and "@27" split into operator and number, dot leaders and stray punctuation dropped. */
    private fun tokens(line: String): List<String> {
        val out = ArrayList<String>()
        for (raw in LEADER.replace(line.replace('\u00A0', ' ').replace('\t', ' '), " ").split(' ')) {
            var t = raw.trim()
            if (t.isEmpty()) continue
            t = t.trimStart('₹', '¥', '$', '€', '£')
            val low = t.lowercase()
            if (low == "rs" || low == "rs." || low == "inr" || low == "/-") continue
            t = when {
                low.startsWith("rs.") && low.length > 3 && low[3].isDigit() -> t.substring(3)
                low.startsWith("rs") && low.length > 2 && low[2].isDigit() -> t.substring(2)
                low.startsWith("inr") && low.length > 3 && low[3].isDigit() -> t.substring(3)
                else -> t
            }
            if (t.endsWith("/-")) t = t.dropLast(2)
            if (t.isEmpty() || t == "-" || t == "." || t.all { it in ":;|," }) continue
            when {
                t.length in 2..4 && t[0] in "xX×" && t.drop(1).all { it in '0'..'9' } -> { out += "x"; out += t.drop(1) }
                t.length in 2..4 && t.last() in "xX×" && t.dropLast(1).all { it in '0'..'9' } -> { out += t.dropLast(1); out += "x" }
                t.length > 1 && t[0] == '@' -> { out += "@"; out += t.drop(1) }
                else -> out += t
            }
        }
        // a tax-category letter after the amount ("54.00 B")
        if (out.size >= 3 && out[out.size - 2].all { it in '0'..'9' || it == ',' || it == '.' } && out.last().length == 1 && (out.last()[0].isUpperCase() || out.last()[0] in "*#")) out.removeAt(out.size - 1)
        return out
    }

    /** The amount in [token] in minor units, or null when it is not one (negative, letters, more decimals than the currency has, wrong thousands grouping). */
    private fun amount(token: String, digits: Int): Long? {
        val t = token.trimEnd(',', '.', ':', ';')
        if (t.isEmpty() || t.any { it !in '0'..'9' && it != ',' && it != '.' } || t.count { it == '.' } > 1) return null
        var whole = t.substringBefore('.')
        var fraction = if (t.contains('.')) t.substringAfter('.') else ""
        if (t.contains('.') && (fraction.isEmpty() || fraction.length > digits)) return null
        if (!t.contains('.') && digits == 2 && whole.count { it == ',' } == 1 && whole.substringAfter(',').length == 2 && whole.substringBefore(',').all { it in '0'..'9' } && whole.substringBefore(',').isNotEmpty()) {
            // a decimal comma: 12,50
            fraction = whole.substringAfter(','); whole = whole.substringBefore(',')
        } else if (whole.contains(',')) {
            val groups = whole.split(',')
            if (groups[0].length !in 1..3 || groups.drop(1).any { it.length !in 2..3 || !it.all { c -> c in '0'..'9' } } || groups.last().length != 3 || !groups[0].all { it in '0'..'9' }) return null
            whole = groups.joinToString("")
        }
        if (whole.isEmpty() || whole.length > 12 || !whole.all { it in '0'..'9' }) return null
        // a plain run of six or more digits is a pin code, a bill or an account number, not a price (prices that big are printed with commas or decimals)
        if (!t.contains('.') && !t.contains(',') && whole.length >= 6) return null
        return (whole + fraction.padEnd(digits, '0')).toLongOrNull()
    }
}

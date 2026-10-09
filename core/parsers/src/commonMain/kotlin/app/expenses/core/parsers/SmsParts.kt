package app.expenses.core.parsers

/** One piece of text the phone handed over in a single delivery, with the time the phone stamped it. */
data class SmsPart(val body: String, val at: Long)

/**
 * Turns the pieces of one delivery from one sender into the messages they really are.
 *
 * A long text arrives as several pieces that must be joined; but two separate alerts can also arrive together (a burst of payments), and joining those
 * would read only the first amount, so two payments of 100 became one of 100. A piece that is a whole transaction on its own (an amount and a direction)
 * is therefore kept as its own message; a piece that is not (a tail such as "Avl bal Rs 5,000. Not you? Call ...") is joined onto the one before it.
 */
object SmsParts {
    fun messages(parts: List<SmsPart>): List<SmsPart> {
        if (parts.size < 2) return parts
        val out = ArrayList<SmsPart>()
        for (p in parts) {
            val standsAlone = GenericAlertParser.parse(p.body) is ParseResult.Parsed
            if (out.isEmpty() || standsAlone) out += p
            else out[out.lastIndex] = out.last().copy(body = out.last().body + p.body)
        }
        return out
    }
}

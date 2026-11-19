package app.expenses.core.domain

/**
 * UI S7 (U-15): which digits of an amount roll when it changes. Only the whole-number digits of the new figure that differ from the old one, place by place from the right, roll: a "₹31,822"
 * that becomes "₹31,870" rolls two digits and leaves the 3, the 1 and the comma alone. The sign, the currency sign, the separators and the paise change at once. This works on the strings that are
 * DRAWN (after masking and rounding to whole units), so a figure that is masked or does not change in the drawn digits rolls nothing, and nothing rolls when a screen is opened (there is no old figure then).
 *
 * Pure: no clock, no drawing. The drawing is `ui/OdometerText.kt`.
 */
object Odometer {
    /** One digit that rolls: its place in the NEW text, the digit it shows now ([from], a space when the figure grew a digit) and the one it becomes ([to]). */
    data class Cell(val index: Int, val from: Char, val to: Char)

    /** The digits to roll, and the direction: [up] when the figure grew (the new digit arrives from below), false when it fell. */
    data class Roll(val cells: List<Cell>, val up: Boolean)

    /** The roll from [old] to [new], or null when nothing rolls (same text, no digits, another currency sign, or only separators and paise changed). */
    fun between(old: String, new: String): Roll? {
        if (old == new) return null
        val o = MoneyParts.split(old)
        val n = MoneyParts.split(new)
        if (o.body.none { it.isDigit() } || n.body.none { it.isDigit() }) return null
        if (o.symbol != n.symbol) return null
        // where the new body starts in the new text: after the minus and the currency sign
        val bodyStart = n.sign.length + n.symbol.length
        val cells = ArrayList<Cell>()
        var i = 1
        while (i <= n.body.length) {
            val nc = n.body[n.body.length - i]
            val oc = if (i <= o.body.length) o.body[o.body.length - i] else ' '
            if (nc.isDigit() && oc != nc && (oc.isDigit() || oc == ' ')) cells += Cell(bodyStart + n.body.length - i, oc, nc)
            i++
        }
        if (cells.isEmpty()) return null
        return Roll(cells, up = signedValueGrew(o, n))
    }

    /** Whether the signed whole-number value of [n] is larger than that of [o]: by length, then digit by digit, with the minus sign turning it round. */
    private fun signedValueGrew(o: MoneyParts, n: MoneyParts): Boolean {
        val od = o.body.filter { it.isDigit() }.trimStart('0')
        val nd = n.body.filter { it.isDigit() }.trimStart('0')
        val oNeg = o.sign == "-" && od.isNotEmpty()
        val nNeg = n.sign == "-" && nd.isNotEmpty()
        if (oNeg != nNeg) return !nNeg
        val magnitude = if (nd.length != od.length) nd.length.compareTo(od.length) else nd.compareTo(od)
        return if (nNeg) magnitude < 0 else magnitude > 0
    }
}

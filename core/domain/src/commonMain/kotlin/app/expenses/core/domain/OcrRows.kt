package app.expenses.core.domain

/** One line of text a recogniser found: where its left edge and vertical centre are (any consistent unit, y growing downwards) and how tall it is. */
data class OcrPiece(val text: String, val left: Float, val centre: Float, val height: Float)

/**
 * A statement is a table, and a text recogniser reports its cells as separate lines. This puts them back into rows by their height on the page: lines whose
 * centres are within half the median line height of each other are one row, read left to right, rows top to bottom.
 */
object OcrRows {
    fun group(pieces: List<OcrPiece>): String {
        val sorted = pieces.filter { it.text.isNotBlank() }.sortedBy { it.centre }
        if (sorted.isEmpty()) return ""
        val median = sorted.map { it.height.coerceAtLeast(0.0001f) }.sorted()[sorted.size / 2]
        val rows = ArrayList<MutableList<OcrPiece>>()
        var centre = 0f
        for (p in sorted) {
            val row = rows.lastOrNull()
            if (row != null && kotlin.math.abs(p.centre - centre) <= median / 2f) {
                row += p
                centre = row.map { it.centre }.average().toFloat()
            } else {
                rows += mutableListOf(p)
                centre = p.centre
            }
        }
        return rows.joinToString("\n") { r -> r.sortedBy { it.left }.joinToString(" ") { it.text } }
    }
}

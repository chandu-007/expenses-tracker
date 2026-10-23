package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class OcrRowsTest {
    @Test
    fun cellsOnTheSameHeightBecomeOneRowLeftToRightAndRowsRunTopToBottom() {
        val pieces = listOf(
            OcrPiece("450.50 Dr", 300f, 101f, 10f), OcrPiece("SWIGGY BANGALORE", 110f, 100f, 10f), OcrPiece("12/09/2026", 10f, 99f, 10f),
            OcrPiece("50000.00 Cr", 300f, 122f, 10f), OcrPiece("13/09/2026", 10f, 121f, 10f), OcrPiece("SALARY ACME", 110f, 120f, 10f),
        )
        assertEquals("12/09/2026 SWIGGY BANGALORE 450.50 Dr\n13/09/2026 SALARY ACME 50000.00 Cr", OcrRows.group(pieces.shuffled(kotlin.random.Random(3))))
    }

    @Test
    fun aLineSlightlyBelowAnotherButMoreThanHalfALineAwayIsTheNextRow() {
        assertEquals("a\nb", OcrRows.group(listOf(OcrPiece("a", 0f, 100f, 10f), OcrPiece("b", 0f, 106f, 10f))))
        assertEquals("", OcrRows.group(listOf(OcrPiece("  ", 0f, 1f, 1f))))
    }
}

package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OdometerTest {
    @Test
    fun onlyTheDigitsThatChangedRollAndTheOthersStay() {
        val r = Odometer.between("₹31,822", "₹31,870")!!
        // the text is "₹31,870": index 0 is the sign, 1 the 3, 2 the 1, 3 the comma, 4 the 8, 5 the 7, 6 the 0
        assertEquals(listOf(Odometer.Cell(6, '2', '0'), Odometer.Cell(5, '2', '7')), r.cells)
        assertTrue(r.up)
    }

    @Test
    fun nothingRollsWhenTheDrawnTextIsTheSame() {
        assertNull(Odometer.between("₹31,822", "₹31,822"))
        assertNull(Odometer.between("₹••••", "₹••••"))
    }

    @Test
    fun aMaskedFigureRollsNothingBecauseItHasNoDigits() {
        assertNull(Odometer.between("₹31,822", "₹••••"))
        assertNull(Odometer.between("₹••••", "₹31,822"))
    }

    @Test
    fun theFigureFallingRollsTheOtherWay() {
        val r = Odometer.between("₹1,000", "₹900")!!
        assertFalse(r.up)
        // 1,000 -> 900: the 9 arrives where a 0 was in the hundreds; the old thousands digit is simply gone, so no cell is drawn for it
        assertEquals(listOf(Odometer.Cell(1, '0', '9')), r.cells)
    }

    @Test
    fun aFigureThatGrowsADigitRollsTheNewDigitInFromNothing() {
        val r = Odometer.between("₹999", "₹1,000")!!
        assertTrue(r.up)
        assertTrue(r.cells.any { it.from == ' ' && it.to == '1' })
    }

    @Test
    fun theCurrencySignTheSeparatorsAndThePaiseChangeAtOnce() {
        assertNull(Odometer.between("₹31,822", "$31,870"))
        // only the paise differ: nothing in the whole-number body rolls
        assertNull(Odometer.between("₹31,822.40", "₹31,822.55"))
    }

    @Test
    fun aMinusSignTurnsTheDirectionRound() {
        // -₹900 -> -₹1,000 is a smaller (more negative) figure: it fell
        assertFalse(Odometer.between("-₹900", "-₹1,000")!!.up)
        assertTrue(Odometer.between("-₹1,000", "-₹900")!!.up)
        // from zero to money out: fell; from money out to zero: grew
        assertFalse(Odometer.between("₹0", "-₹1,000")!!.up)
    }

    @Test
    fun aSentenceOfTheSameLengthRollsItsChangedDigitsAndNothingElse() {
        val r = Odometer.sameLength("₹46,733 left", "₹46,640 left")!!
        assertEquals(setOf(Odometer.Cell(4, '7', '6'), Odometer.Cell(5, '3', '4'), Odometer.Cell(6, '3', '0')), r.cells.toSet())
        assertFalse(r.up, "the first digit to change went from 7 to 6")
        assertTrue(Odometer.sameLength("₹1,000 left", "₹1,010 left")!!.up)
    }

    @Test
    fun aSentenceThatChangesLengthOrWordsOrIsMaskedRollsNothing() {
        assertNull(Odometer.sameLength("₹999 left", "₹1,000 left"))
        assertNull(Odometer.sameLength("₹46,733 left", "₹46,733 left"))
        assertNull(Odometer.sameLength("₹46,733 left", "₹46,740 over"))
        assertNull(Odometer.sameLength("₹46,733 left", "₹•••••• left"))
    }
}

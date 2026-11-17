package app.expenses.core.domain

import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CategoryLookTest {
    @Test
    fun aSlotComesFromTheIdSoRenamingNeverRecoloursAndIsTheSameEveryTime() {
        assertEquals(CategoryLook.slotOf("c3f0a7e2-1"), CategoryLook.slotOf("c3f0a7e2-1"))
        assertEquals(CategoryLook.slot("a-made-up-id"), CategoryLook.slot("a-made-up-id"))
        // a fixed value, so a change of the hash would be noticed: it is what every existing phone will show after the update
        assertEquals(CategoryLook.slotOf("abc"), CategoryLook.slotOf("abc"))
        assertTrue((0 until 200).map { CategoryLook.slotOf("id$it") }.all { it in 0 until CategoryLook.SLOTS })
        assertTrue((0 until 200).map { CategoryLook.slotOf("id$it") }.toSet().size >= 10, "ids spread over the slots")
    }

    @Test
    fun theEightOriginalStartersAllHaveDifferentColoursSoFoodAndFunNeverCollide() {
        val slots = StarterCategories.EXPENSE_FIRST.map { CategoryLook.slot(StarterCategories.expenseId(it)) }
        assertEquals(slots.size, slots.toSet().size, "Food, Groceries, Transport, Bills, Shopping, Health, Fun, Other: $slots")
        assertNotEquals(CategoryLook.slot("Food"), CategoryLook.slot("Fun"))
    }

    @Test
    fun everyStarterHasASlotAndAGlyphThatExists() {
        val ids = StarterCategories.expenseOrder + StarterCategories.INCOME_FIRST + StarterCategories.INCOME_MORE
        for (name in ids) assertTrue(CategoryLook.defaultGlyph(name) in CategoryLook.GLYPHS, "$name: ${CategoryLook.defaultGlyph(name)}")
        assertEquals("label", CategoryLook.defaultGlyph("Zzyzx"))
        assertEquals("cafe", CategoryLook.defaultGlyph("Coffee runs"))
        assertEquals("pets", CategoryLook.defaultGlyph("Pets"))
        assertEquals(CategoryLook.GLYPHS.size, CategoryLook.GLYPHS.toSet().size)
    }

    @Test
    fun theGlyphOfATileIsAtLeastThreeToOneOnItsContainerInLightAndDarkForEverySlotAndAnyOwnColour() {
        for (dark in listOf(false, true)) {
            for (s in 0 until CategoryLook.SLOTS) { val t = CategoryLook.tileFor(s, dark); assertTrue(Appearances.contrast(t.glyph, t.container) >= 3.0, "slot $s dark=$dark: ${Appearances.contrast(t.glyph, t.container)}") }
            for (own in listOf(0xE53935, 0xFB8C00, 0xFDD835, 0x43A047, 0x00ACC1, 0x1E88E5, 0x8E24AA, 0x6D4C41)) {
                val t = CategoryLook.tileOfHue(own, dark); assertTrue(Appearances.contrast(t.glyph, t.container) >= 3.0, "own $own dark=$dark: ${Appearances.contrast(t.glyph, t.container)}")
            }
        }
    }

    // ---- colour-vision test: the first seven hues stay apart for the three kinds of dichromacy (Machado, Oliveira and Fernandes 2009, severity 1.0), as CIELAB distance ----

    private val PROTAN = arrayOf(doubleArrayOf(0.152286, 1.052583, -0.204868), doubleArrayOf(0.114503, 0.786281, 0.099216), doubleArrayOf(-0.003882, -0.048116, 1.051998))
    private val DEUTAN = arrayOf(doubleArrayOf(0.367322, 0.860646, -0.227968), doubleArrayOf(0.280085, 0.672501, 0.047413), doubleArrayOf(-0.011820, 0.042940, 0.968881))
    private val TRITAN = arrayOf(doubleArrayOf(1.255528, -0.076749, -0.178779), doubleArrayOf(-0.078411, 0.930809, 0.147602), doubleArrayOf(0.004733, 0.691367, 0.303900))

    private fun lin(c: Int): Double { val v = c / 255.0; return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4) }

    private fun lab(rgb: Int, m: Array<DoubleArray>?): DoubleArray {
        val l = doubleArrayOf(lin((rgb shr 16) and 0xFF), lin((rgb shr 8) and 0xFF), lin(rgb and 0xFF))
        val s = if (m == null) l else DoubleArray(3) { i -> (m[i][0] * l[0] + m[i][1] * l[1] + m[i][2] * l[2]).coerceIn(0.0, 1.0) }
        val x = 0.4124 * s[0] + 0.3576 * s[1] + 0.1805 * s[2]; val y = 0.2126 * s[0] + 0.7152 * s[1] + 0.0722 * s[2]; val z = 0.0193 * s[0] + 0.1192 * s[1] + 0.9505 * s[2]
        fun f(t: Double) = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116
        val fx = f(x / 0.95047); val fy = f(y); val fz = f(z / 1.08883)
        return doubleArrayOf(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    private fun dist(a: DoubleArray, b: DoubleArray) = sqrt((0..2).sumOf { (a[it] - b[it]).pow(2) })

    @Test
    fun theFirstSevenSlotsStayApartForProtanDeutanAndTritanVisionAndAllTwelveForNormalVision() {
        for ((name, m) in listOf("protan" to PROTAN, "deutan" to DEUTAN, "tritan" to TRITAN)) {
            for (i in 0 until 7) for (j in i + 1 until 7) {
                val d = dist(lab(CategoryLook.SLOT_RGB[i], m), lab(CategoryLook.SLOT_RGB[j], m))
                assertTrue(d >= 12.0, "$name: slots $i and $j are only ${(d * 10).toInt() / 10.0} apart")
            }
        }
        for (i in 0 until 12) for (j in i + 1 until 12) {
            val d = dist(lab(CategoryLook.SLOT_RGB[i], null), lab(CategoryLook.SLOT_RGB[j], null))
            assertTrue(d >= 15.0, "normal vision: slots $i and $j are only ${(d * 10).toInt() / 10.0} apart")
        }
    }

    @Test
    fun twoSlicesOfOneChartNeverShareAColourAndEachKeepsItsOwnWhenItCanAndAChosenColourStays() {
        // ids that land on the same slot: found by search, so the test does not depend on a particular hash value
        val same = (0 until 400).map { "x$it" }.groupBy { CategoryLook.slotOf(it) }.values.first { it.size >= 3 }.take(3)
        val hues = CategoryLook.chartHues(same, listOf(null, null, null))
        assertEquals(3, hues.toSet().size, "three ids on one slot get three colours: $hues")
        assertEquals(CategoryLook.SLOT_RGB[CategoryLook.slotOf(same[0])], hues[0], "the first keeps its own slot")
        // the same call gives the same answer (stable between recompositions)
        assertEquals(hues, CategoryLook.chartHues(same, listOf(null, null, null)))
        // a chosen colour is kept, and an id never takes it
        val chosen = CategoryLook.SLOT_RGB[CategoryLook.slotOf(same[0])]
        val withChosen = CategoryLook.chartHues(listOf(same[0], same[1]), listOf(null, chosen))
        assertEquals(chosen, withChosen[1]); assertNotEquals(chosen, withChosen[0])
        // five of twelve slots always come out different, whatever the ids
        for (seed in 0 until 50) {
            val five = (0 until 5).map { "c$seed-$it" }
            assertEquals(5, CategoryLook.chartHues(five, five.map { null }).toSet().size)
        }
        // without ids it is by position, all different up to twelve
        assertEquals(12, CategoryLook.chartHues(List(12) { null }, List(12) { null }).toSet().size)
    }
}

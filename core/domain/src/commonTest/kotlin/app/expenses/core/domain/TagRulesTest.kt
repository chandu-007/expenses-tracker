package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TagRulesTest {
    @Test
    fun typedTextIsTidiedIntoATagOrNothing() {
        assertEquals("Goa trip", TagRules.normalize("  #Goa,   trip "))
        assertEquals("80C", TagRules.normalize("##80C"))
        assertNull(TagRules.normalize("  # , "))
        assertNull(TagRules.normalize(""))
        assertEquals(30, TagRules.normalize("x".repeat(50))!!.length)
        assertEquals("a b", TagRules.normalize("a, b"), "a comma never survives, it would break the lists tags are stored in")
    }

    @Test
    fun aListKeepsTheFirstSpellingDropsRepeatsIgnoringCaseAndStopsAtTen() {
        assertEquals(listOf("Trip", "Tax"), TagRules.clean(listOf("Trip", "trip", " ", "#TRIP", "Tax")))
        assertEquals((1..10).map { "t$it" }, TagRules.clean((1..15).map { "t$it" }))
    }

    @Test
    fun addingIsRefusedWithAReason() {
        assertEquals("Type a tag.", TagRules.checkAdd(emptyList(), " # "))
        assertEquals("That tag is already on this entry.", TagRules.checkAdd(listOf("Trip"), "trip"))
        assertEquals("An entry can have up to 10 tags.", TagRules.checkAdd((1..10).map { "t$it" }, "new"))
        assertNull(TagRules.checkAdd((1..9).map { "t$it" }, "new"))
    }

    @Test
    fun storedFormIsSortedAndRoundTrips() {
        assertEquals("a,B,c", TagRules.joined(listOf("c", "a", "B")))
        assertEquals(listOf("a", "B", "c"), TagRules.split("a,B,c")); assertEquals(emptyList(), TagRules.split(null)); assertEquals(emptyList(), TagRules.split(""))
    }
}

package app.expenses.core.domain

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HomeLayoutTest {
    private val all = HomeSection.entries

    @Test
    fun theDefaultShowsEverythingInTheDeclaredOrder() {
        assertEquals(all, HomeLayout.DEFAULT.visible()); assertEquals(HomeLayout.DEFAULT, HomeLayout.decode(null)); assertEquals(HomeLayout.DEFAULT, HomeLayout.decode("  "))
    }

    @Test
    fun movingStopsAtTheEndsAndHidingKeepsThePlace() {
        val l = HomeLayout.DEFAULT
        assertEquals(l, l.move(all.first(), -1)); assertEquals(l, l.move(all.last(), 1))
        val down = l.move(HomeSection.BUDGET, 1)
        assertEquals(listOf(HomeSection.BILLS, HomeSection.BUDGET), down.order.take(2))
        val hid = down.withHidden(HomeSection.BILLS, true)
        assertEquals(listOf(HomeSection.BUDGET, HomeSection.BALANCES, HomeSection.QUICK_ENTRIES), hid.visible())
        assertEquals(down, hid.withHidden(HomeSection.BILLS, false), "showing it again puts it back where it was")
        assertEquals(listOf(HomeSection.BILLS, HomeSection.BALANCES, HomeSection.QUICK_ENTRIES, HomeSection.BUDGET), l.move(HomeSection.BUDGET, 3).order, "several places at once")
        assertEquals(l, l.move(HomeSection.BUDGET, 3).move(HomeSection.BUDGET, -3))
    }

    @Test
    fun readingIsForgivingUnknownNamesAndRepeatsAreDroppedAndNewSectionsAreAddedAtTheEndAndShown() {
        val l = HomeLayout.decode("QUICK_ENTRIES,FUTURE_THING,BUDGET,QUICK_ENTRIES;FUTURE_THING,BILLS")
        assertEquals(listOf(HomeSection.QUICK_ENTRIES, HomeSection.BUDGET, HomeSection.BILLS, HomeSection.BALANCES), l.order)
        assertEquals(setOf(HomeSection.BILLS), l.hidden)
        val old = HomeLayout.decode("BUDGET,BILLS;") // written before BALANCES and QUICK_ENTRIES existed
        assertTrue(HomeSection.BALANCES in old.visible() && HomeSection.QUICK_ENTRIES in old.visible(), "an update's new block is not silently hidden")
        assertEquals(HomeLayout.DEFAULT, HomeLayout.decode("garbage;;;more"))
    }

    @Test
    fun anyLayoutRoundTripsThroughItsTextAndAnyArbitraryTextDecodesToAValidLayout() = runTest {
        checkAll(Arb.list(Arb.int(0, 3), 0..8), Arb.list(Arb.int(0, 3), 0..4)) { moves, hides ->
            var l = HomeLayout.DEFAULT
            moves.forEachIndexed { i, m -> l = l.move(all[m], if (i % 2 == 0) 1 else -1) }
            hides.forEach { l = l.withHidden(all[it], true) }
            assertEquals(l, HomeLayout.decode(l.encode()))
            assertEquals(all.toSet(), l.order.toSet())
        }
        checkAll(Arb.string(0..60)) { text -> assertEquals(all.size, HomeLayout.decode(text).order.size) }
    }

    @Test
    fun aLayoutMustListEverySectionOnce() {
        assertFailsWith<IllegalArgumentException> { HomeLayout(all.dropLast(1), emptySet()) }
        assertFailsWith<IllegalArgumentException> { HomeLayout(all + all.first(), emptySet()) }
    }
}

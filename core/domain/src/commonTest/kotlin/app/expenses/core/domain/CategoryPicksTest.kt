package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class CategoryPicksTest {
    private val ids = (1..30).map { "c$it" }

    @Test
    fun fewCategoriesAreAllShownAndManyAreCutWithTheRestCounted() {
        assertEquals(CategoryPicks(ids.take(8), 0), CategoryChoices.pick(ids.take(8), null))
        assertEquals(CategoryPicks(ids.take(24), 0), CategoryChoices.pick(ids.take(24), null), "exactly the limit: no More")
        assertEquals(CategoryPicks(ids.take(24), 6), CategoryChoices.pick(ids, null))
        assertEquals(CategoryPicks(emptyList(), 0), CategoryChoices.pick(emptyList(), null))
    }

    @Test
    fun theChosenCategoryIsAlwaysShownEvenBeyondTheCutAndCountsOnlyOnce() {
        assertEquals(CategoryPicks(ids.take(24) + "c27", 5), CategoryChoices.pick(ids, "c27"))
        assertEquals(CategoryPicks(ids.take(24), 6), CategoryChoices.pick(ids, "c3"), "already among the first: nothing added")
        assertEquals(CategoryPicks(ids.take(24), 6), CategoryChoices.pick(ids, "gone"), "a selection that is not offered is not invented")
        assertEquals(CategoryPicks(listOf("c1", "c2", "c30"), 27), CategoryChoices.pick(ids, "c30", limit = 2))
    }

    @Test
    fun searchMatchesEveryWordAnywhereInTheNameIgnoringCase() {
        val names = listOf("a" to "Food", "b" to "Eating out › Dinner", "c" to "Groceries", "d" to "Food delivery")
        assertEquals(listOf("a", "d"), CategoryChoices.search(names, "food"))
        assertEquals(listOf("b"), CategoryChoices.search(names, "OUT dinner"))
        assertEquals(listOf("d"), CategoryChoices.search(names, "  food   deliv "))
        assertEquals(listOf("a", "b", "c", "d"), CategoryChoices.search(names, "   "), "nothing typed: everything")
        assertEquals(emptyList(), CategoryChoices.search(names, "zzz"))
    }
}

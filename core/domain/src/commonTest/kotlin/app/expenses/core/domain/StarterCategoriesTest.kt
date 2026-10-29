package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StarterCategoriesTest {
    private val everyExpense = StarterCategories.EXPENSE_FIRST + StarterCategories.EXPENSE_MORE
    private val everyIncome = StarterCategories.INCOME_FIRST + StarterCategories.INCOME_MORE

    @Test
    fun nothingIsListedTwiceAndEveryNameIsAValidCategoryName() {
        assertEquals(everyExpense.size, everyExpense.map { it.lowercase() }.toSet().size)
        assertEquals(everyIncome.size, everyIncome.map { it.lowercase() }.toSet().size)
        assertEquals(emptySet(), everyExpense.map { it.lowercase() }.toSet() intersect everyIncome.map { it.lowercase() }.toSet(), "an expense and an income starter never share a name")
        for (n in everyExpense + everyIncome) assertEquals(null, CategoryRules.checkName(emptyList(), n, null), n)
        val ids = everyExpense.map(StarterCategories::expenseId) + everyIncome.map(StarterCategories::incomeId)
        assertEquals(ids.size, ids.toSet().size, "every id is distinct")
        assertEquals(StarterCategories.EXPENSE_FIRST, StarterCategories.EXPENSE_FIRST.map(StarterCategories::expenseId), "the original expense starters keep their names as ids")
        assertEquals(listOf("income:salary", "income:interest", "income:giftsreceived", "income:refundsandcashback", "income:otherincome"), StarterCategories.INCOME_FIRST.map(StarterCategories::incomeId))
    }

    @Test
    fun theNewerListIsFocusedWithinTwelveToTwentyAndPositionsFollowTheOriginals() {
        val added = StarterCategories.EXPENSE_MORE.size + StarterCategories.INCOME_MORE.size
        assertTrue(added in 12..20, "$added newer starters")
        assertEquals((0 until everyExpense.size).toList(), everyExpense.map(StarterCategories::expensePosition), "expense positions are the list order, the original eight first")
        assertEquals(everyExpense, StarterCategories.expenseOrder)
        assertEquals((100 until 100 + everyIncome.size).toList(), everyIncome.map(StarterCategories::incomePosition))
    }

    @Test
    fun everyNewerStarterIsOfferedInSuggestedCategoriesSoAnExistingInstallCanAddItInOneStep() {
        val expenseGroups = CategoryCatalog.groups.filter { !it.income }.map { it.name.lowercase() }
        val incomeGroups = CategoryCatalog.groups.filter { it.income }.map { it.name.lowercase() }
        for (n in everyExpense) assertTrue(n.lowercase() in expenseGroups, "$n is not in the catalog")
        for (n in everyIncome) assertTrue(n.lowercase() in incomeGroups, "$n is not in the catalog as income")
        // an existing install with only the originals: ticking the newer groups plans exactly the missing ones, with the right kind
        val have = StarterCategories.EXPENSE_FIRST.map { Category(it, it, null, null, 0, false) } + StarterCategories.INCOME_FIRST.map { Category("income:" + it, it, null, null, 100, false, CategoryKind.INCOME) }
        val picks = (StarterCategories.EXPENSE_MORE + StarterCategories.INCOME_MORE).associateWith { emptySet<String>() }
        val plan = CategoryCatalog.plan(have, picks)
        assertEquals((StarterCategories.EXPENSE_MORE + StarterCategories.INCOME_MORE).toSet(), plan.filter { it.child == null }.map { it.parent }.toSet())
        assertEquals(StarterCategories.INCOME_MORE.toSet(), plan.filter { it.income && it.child == null }.map { it.parent }.toSet())
        assertTrue(plan.filter { it.parent in StarterCategories.EXPENSE_MORE }.none { it.income })
        // a new install already has them all: nothing is left to add at the top level
        val fresh = have + StarterCategories.EXPENSE_MORE.map { Category(StarterCategories.expenseId(it), it, null, null, 0, false) } + StarterCategories.INCOME_MORE.map { Category(StarterCategories.incomeId(it), it, null, null, 100, false, CategoryKind.INCOME) }
        assertEquals(emptyList(), CategoryCatalog.plan(fresh, picks).filter { it.child == null })
    }
}

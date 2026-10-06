package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CategoryRulesTest {
    private fun c(id: String, name: String = id, parent: String? = null, pos: Int = 0) = Category(id, name, parent, null, pos, false)
    private val tree = listOf(c("food", "Food"), c("travel", "Travel"), c("cafe", "Cafe", "food"), c("other-f", "Other", "food"))

    @Test fun namesAreTrimmedAndSpacesCollapsed() {
        assertEquals("Eating out", CategoryRules.normalizeName("  Eating    out "))
    }

    @Test fun emptyAndOverlongNamesAreRejected() {
        assertNotNull(CategoryRules.checkName(tree, "   ", null))
        assertNotNull(CategoryRules.checkName(tree, "x".repeat(41), null))
        assertNull(CategoryRules.checkName(tree, "x".repeat(40), null))
    }

    @Test fun namesAreUniqueAmongSiblingsIgnoringCase() {
        assertNotNull(CategoryRules.checkName(tree, "food", null))
        assertNotNull(CategoryRules.checkName(tree, " FOOD ", null))
        assertNotNull(CategoryRules.checkName(tree, "cafe", "food"))
        assertNull(CategoryRules.checkName(tree, "Other", "travel"), "the same name under a different parent is allowed")
        assertNull(CategoryRules.checkName(tree, "Cafe", null), "a top-level name may equal a subcategory name elsewhere")
    }

    @Test fun renamingToTheOwnNameIsNotAClash() {
        assertNull(CategoryRules.checkName(tree, "FOOD", null, excludeId = "food"))
    }

    @Test fun parentRules() {
        assertNull(CategoryRules.checkParent(tree, "travel", null))
        assertNotNull(CategoryRules.checkParent(tree, "travel", "travel"))
        assertNotNull(CategoryRules.checkParent(tree, "travel", "nope"))
        assertNotNull(CategoryRules.checkParent(tree, "travel", "cafe"), "only one level deep")
        assertNotNull(CategoryRules.checkParent(tree, "food", "travel"), "food has subcategories, so it cannot become one")
        assertNull(CategoryRules.checkParent(tree, "cafe", "travel"))
    }

    @Test fun mergeRules() {
        assertNull(CategoryRules.checkMerge(tree, "travel", "food"))
        assertNull(CategoryRules.checkMerge(tree, "cafe", "food"), "a subcategory can merge into its parent")
        assertNotNull(CategoryRules.checkMerge(tree, "food", "travel"), "food still has subcategories")
        assertNotNull(CategoryRules.checkMerge(tree, "travel", "travel"))
        assertNotNull(CategoryRules.checkMerge(tree, "travel", "nope"))
    }

    @Test fun deleteRules() {
        assertNull(CategoryRules.checkDelete(tree, "travel", 0))
        assertEquals("This category is used by 1 item. Merge it into another category instead", CategoryRules.checkDelete(tree, "travel", 1))
        assertEquals("This category is used by 3 items. Merge it into another category instead", CategoryRules.checkDelete(tree, "travel", 3))
        assertNotNull(CategoryRules.checkDelete(tree, "food", 0))
        assertNotNull(CategoryRules.checkDelete(tree, "nope", 0))
    }

    @Test fun neighboursAreFoundAmongSiblingsOnly() {
        val all = listOf(c("a", pos = 0), c("b", pos = 1), c("c", pos = 2), c("a1", parent = "a", pos = 0))
        assertEquals("b", CategoryRules.neighbour(all, "a", 1)?.id)
        assertEquals("a", CategoryRules.neighbour(all, "b", -1)?.id)
        assertNull(CategoryRules.neighbour(all, "a", -1))
        assertNull(CategoryRules.neighbour(all, "c", 1))
        assertNull(CategoryRules.neighbour(all, "a1", 1), "a lone subcategory has no sibling")
        assertFailsWith<IllegalArgumentException> { CategoryRules.neighbour(all, "a", 2) }
    }

    @Test fun displayOrderPutsEachParentBeforeItsChildrenAndKeepsOrphans() {
        val all = listOf(c("b", pos = 1), c("a", pos = 0), c("b2", parent = "b", pos = 1), c("b1", parent = "b", pos = 0), c("a1", parent = "a"), c("lost", parent = "gone"))
        assertEquals(listOf("a", "a1", "b", "b1", "b2", "lost"), CategoryRules.inDisplayOrder(all).map { it.id })
        assertEquals(all.size, CategoryRules.inDisplayOrder(all).size)
    }
}

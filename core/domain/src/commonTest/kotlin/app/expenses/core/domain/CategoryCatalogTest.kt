package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CategoryCatalogTest {
    private fun cat(id: String, name: String, parent: String? = null) = Category(id, name, parent, null, 0, false)

    @Test
    fun theCatalogIsConsistentWithTheCategoryRules() {
        val names = CategoryCatalog.groups.map { it.name.lowercase() }
        assertEquals(names.size, names.toSet().size, "a group name appears once")
        for (g in CategoryCatalog.groups) {
            assertEquals(null, CategoryRules.checkName(emptyList(), g.name, null), g.name)
            assertEquals(g.children.size, g.children.map { it.lowercase() }.toSet().size, "${g.name} repeats a subcategory")
            for (c in g.children) assertEquals(null, CategoryRules.checkName(emptyList(), c, "p"), "${g.name} › $c")
        }
        // every default category the app starts with is in the catalog, so its subcategories attach to it instead of making a twin
        for (d in listOf("Food", "Groceries", "Transport", "Bills", "Shopping", "Health", "Fun", "Other")) assertTrue(d.lowercase() in names, d)
        assertEquals(CategoryCatalog.types.size, CategoryCatalog.types.toSet().size)
    }

    @Test
    fun aPickAddsTheGroupWhenItIsMissingThenOnlyTheSubcategoriesNotYetThere() {
        val none = CategoryCatalog.plan(emptyList(), mapOf("Rent" to setOf("House rent")))
        assertEquals(listOf(PlannedCategory("Rent", null), PlannedCategory("Rent", "House rent")), none)
        // Food exists (any capitalisation) with "restaurants" already under it: only the other wanted one is planned, under the existing parent
        val have = listOf(cat("f", "food"), cat("r", "restaurants", "f"))
        assertEquals(listOf(PlannedCategory("Food", "Food delivery")), CategoryCatalog.plan(have, mapOf("Food" to setOf("Restaurants", "Food delivery"))))
        // a group picked with no subcategories adds just the group; picking nothing adds nothing
        assertEquals(listOf(PlannedCategory("Home", null)), CategoryCatalog.plan(emptyList(), mapOf("Home" to emptySet())))
        assertEquals(emptyList(), CategoryCatalog.plan(emptyList(), emptyMap()))
    }

    @Test
    fun theSameNameUnderAnotherParentDoesNotCountAsPresentAndUnknownNamesAreIgnored() {
        val have = listOf(cat("t", "Travel"), cat("h", "Hotels", "t"))
        // "Hotels" exists under Travel, not under Home: but Home has no "Hotels" in the catalog, so only the real clash matters
        assertEquals(emptyList(), CategoryCatalog.plan(have, mapOf("Travel" to setOf("Hotels"))))
        assertEquals(listOf(PlannedCategory("Travel", "Flights")), CategoryCatalog.plan(have, mapOf("Travel" to setOf("Hotels", "Flights"))))
        assertEquals(emptyList(), CategoryCatalog.plan(emptyList(), mapOf("Not a group" to setOf("x")), ))
        assertEquals(listOf(PlannedCategory("Rent", null)), CategoryCatalog.plan(emptyList(), mapOf("Rent" to setOf("Invented child"))), "a child the catalog does not have is not created")
        // a subcategory with a catalog group's name is not that top-level category: "Gifts" under Family does not stand in for the group "Gifts and donations"
        assertEquals(listOf(PlannedCategory("Rent", null)), CategoryCatalog.plan(listOf(cat("x", "Rent", "someone")), mapOf("Rent" to emptySet())))
    }
}

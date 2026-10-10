package app.expenses.core.domain

/** One suggested top-level category with the subcategories it usually has. [type] is only the heading it is shown under; it is not stored on a category. */
data class CatalogGroup(val type: String, val name: String, val children: List<String>)

/** A category the person chose to add: [child] null means the top-level [parent] itself. */
data class PlannedCategory(val parent: String, val child: String?)

/**
 * A ready-made set of categories for a person in India, grouped by kind, so a new user need not invent them. Nothing is added until the person
 * picks it; a name that is already there (any capitalisation) is never added twice, and a group whose name matches a category the person already has
 * (the default "Food", say) adds its subcategories to that one.
 */
object CategoryCatalog {
    val groups: List<CatalogGroup> = listOf(
        CatalogGroup("Daily life", "Food", listOf("Restaurants", "Snacks and tea", "Food delivery", "Sweets and bakery")),
        CatalogGroup("Daily life", "Groceries", listOf("Vegetables and fruit", "Milk and dairy", "Supermarket", "Kirana store")),
        CatalogGroup("Daily life", "Transport", listOf("Fuel", "Cab and auto", "Bus and metro", "Parking and tolls", "Vehicle service")),
        CatalogGroup("Daily life", "Personal care", listOf("Salon and grooming", "Gym and fitness", "Cosmetics")),
        CatalogGroup("Home", "Bills", listOf("Electricity", "Water", "Cooking gas", "Mobile recharge", "Internet and broadband", "TV and OTT")),
        CatalogGroup("Home", "Rent", listOf("House rent", "Maintenance charges")),
        CatalogGroup("Home", "Home", listOf("Furniture and appliances", "Repairs", "Maid and help", "Decor")),
        CatalogGroup("Lifestyle", "Shopping", listOf("Clothes", "Electronics", "Footwear and bags", "Online shopping")),
        CatalogGroup("Lifestyle", "Fun", listOf("Movies", "Subscriptions", "Games and hobbies", "Outings")),
        CatalogGroup("Lifestyle", "Travel", listOf("Flights", "Trains and buses", "Hotels", "Trips and tours")),
        CatalogGroup("Health and learning", "Health", listOf("Medicines", "Doctor", "Tests and labs", "Health insurance")),
        CatalogGroup("Health and learning", "Education", listOf("School and college fees", "Books and courses", "Tuition")),
        CatalogGroup("Money", "Finance", listOf("Loan EMI", "Insurance", "Investments", "Taxes", "Bank charges and fees")),
        CatalogGroup("Money", "Gifts and donations", listOf("Gifts", "Donations", "Festivals and events")),
        CatalogGroup("People", "Family", listOf("Kids", "Parents", "Pets")),
        CatalogGroup("People", "Other", emptyList()),
    )

    /** The headings in the order they are shown. */
    val types: List<String> get() = groups.map { it.type }.distinct()

    /**
     * What has to be created so that every pick exists: for each [picks] entry (a group name and the subcategories wanted), the top-level category when
     * [existing] has none with that name, then each wanted subcategory it has none of under that parent. Parents come before their children. Names that
     * are not in the catalog are ignored, so a stale selection cannot create anything the screen did not offer.
     */
    fun plan(existing: List<Category>, picks: Map<String, Set<String>>): List<PlannedCategory> {
        val out = ArrayList<PlannedCategory>()
        for (g in groups) {
            val wanted = picks[g.name] ?: continue
            val parent = existing.firstOrNull { it.parentId == null && it.name.equals(g.name, ignoreCase = true) }
            val have = parent?.let { p -> existing.filter { it.parentId == p.id }.map { it.name.lowercase() }.toSet() }.orEmpty()
            val children = g.children.filter { it in wanted && it.lowercase() !in have }
            if (parent == null) out += PlannedCategory(g.name, null)
            children.forEach { out += PlannedCategory(g.name, it) }
        }
        return out
    }
}

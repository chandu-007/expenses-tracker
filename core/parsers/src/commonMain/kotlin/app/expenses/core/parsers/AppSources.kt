package app.expenses.core.parsers

/**
 * The apps whose notifications may be read when the person turns notification capture on, and the name each is captured under (the same names as
 * [SenderRules.BUILT_IN] holds for them). Anything else posting a notification is never looked at. **The package names are public knowledge of
 * the apps' identifiers; the wording of their notifications was not checked against real ones.**
 */
object AppSources {
    val PACKAGES: Map<String, String> = mapOf(
        "com.google.android.apps.nbu.paisa.user" to "GPAY",
        "com.phonepe.app" to "PHONEPE",
        "net.one97.paytm" to "PAYTM",
        "in.org.npci.upiapp" to "BHIM",
    )

    /** The capture name for a notification posted by [packageName], or null when that app is not one that is read. */
    fun nameOf(packageName: String, extra: Map<String, String> = emptyMap()): String? = PACKAGES[packageName] ?: extra[packageName]

    /** Joins a notification's title and text, which apps split between the two ("Paid ₹250" / "to Swiggy"), dropping repeats. */
    fun words(title: CharSequence?, text: CharSequence?, bigText: CharSequence? = null): String {
        val parts = listOfNotNull(title, bigText ?: text).map { it.toString().trim() }.filter { it.isNotEmpty() }
        // a part wholly inside another says nothing new; of two equal parts the first stays
        val kept = parts.filterIndexed { i, p -> parts.withIndex().none { (j, q) -> j != i && q.contains(p) && (q.length > p.length || j < i) } }
        return kept.joinToString(". ")
    }
}

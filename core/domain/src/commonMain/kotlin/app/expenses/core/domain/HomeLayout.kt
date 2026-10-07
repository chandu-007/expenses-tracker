package app.expenses.core.domain

/** The blocks of the home screen between the headline spend and the list of entries. */
enum class HomeSection(val label: String) {
    BUDGET("Budget and safe to spend"),
    BILLS("Bills waiting"),
    BALANCES("Account balances"),
    QUICK_ENTRIES("Quick entries"),
}

/**
 * Which home blocks are shown and in what order. Stored as text so it survives a backup. Reading is forgiving on purpose: unknown names
 * (from a newer or older version) are dropped, repeats are ignored, and a block this text does not mention is added at the end and shown,
 * so an update that adds a block never makes it silently invisible.
 */
data class HomeLayout(val order: List<HomeSection>, val hidden: Set<HomeSection>) {
    init {
        require(order.toSet() == HomeSection.entries.toSet() && order.size == HomeSection.entries.size) { "Every section appears exactly once" }
    }

    fun visible(): List<HomeSection> = order.filter { it !in hidden }

    fun withHidden(section: HomeSection, hide: Boolean) = copy(hidden = if (hide) hidden + section else hidden - section)

    /** Moves a section one place up (negative) or down (positive); at either end it stays. */
    fun move(section: HomeSection, delta: Int): HomeLayout {
        val i = order.indexOf(section); val j = (i + delta).coerceIn(0, order.size - 1)
        if (i == j) return this
        val list = order.toMutableList(); list.removeAt(i); list.add(j, section)
        return copy(order = list)
    }

    fun encode(): String = order.joinToString(",") { it.name } + ";" + hidden.sortedBy { it.ordinal }.joinToString(",") { it.name }

    companion object {
        val DEFAULT = HomeLayout(HomeSection.entries.toList(), emptySet())

        fun decode(text: String?): HomeLayout {
            if (text.isNullOrBlank()) return DEFAULT
            val parts = text.split(';')
            fun names(s: String?) = s.orEmpty().split(',').mapNotNull { n -> HomeSection.entries.firstOrNull { it.name == n.trim() } }
            val order = names(parts.getOrNull(0)).distinct().let { given -> given + HomeSection.entries.filter { it !in given } }
            return HomeLayout(order, names(parts.getOrNull(1)).toSet())
        }
    }
}

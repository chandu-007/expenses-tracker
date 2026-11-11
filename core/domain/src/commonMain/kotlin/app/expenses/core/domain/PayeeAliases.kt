package app.expenses.core.domain

/**
 * One alias the person made: the shop [from] (a [PayeeKey], written [fromLabel]) is the same shop as [to] ([toLabel]). It is an overlay on how names are COUNTED (what the app learned, what earlier
 * entries say, a search for the name, the subscription finder and the merchant report); the text of an entry is never rewritten.
 */
data class PayeeAlias(val from: String, val to: String, val fromLabel: String = from, val toLabel: String = to)

/**
 * The person's "same shop as" aliases, as an immutable table. [resolve] turns a payee key into the key of the shop it counts as; two names with one resolution are one shop. Chains are followed
 * (A counts as B, B as C: A is C) with a limit and no cycle can be made ([problemFor]). The built-in same-merchant table ([MerchantAliases]) is already inside [PayeeKey.of]; this is the person's
 * own, on top of it. Pure; the store reads the rows and builds one.
 */
class PayeeAliases(aliases: Collection<PayeeAlias> = emptyList()) {
    private val byFrom: Map<String, PayeeAlias> = aliases.associateBy { it.from }
    val isEmpty: Boolean get() = byFrom.isEmpty()

    /** The aliases, by the name that counts as another, in a stable order for a list. */
    val all: List<PayeeAlias> get() = byFrom.values.sortedWith(compareBy({ it.fromLabel.lowercase() }, { it.from }))

    /** [key] with every alias followed to the shop it counts as; [key] itself when it has none. At most [MAX_HOPS] steps, so a table that was hand-made with a cycle still ends. */
    fun resolve(key: String): String {
        var k = key
        var n = 0
        while (n < MAX_HOPS) { k = byFrom[k]?.to ?: return k; n++ }
        return k
    }

    /** The members of each shop (every key that resolves to it, the shop itself not included), built once. */
    private val groups: Map<String, List<String>> by lazy {
        val out = HashMap<String, MutableList<String>>()
        for (from in byFrom.keys) { val head = resolve(from); if (head != from) out.getOrPut(head) { ArrayList(2) } += from }
        out.mapValues { (_, v) -> v.sorted() }
    }

    /** Whether [key] is a shop that has other names counting as it, or is itself such a name. */
    fun inGroup(key: String): Boolean = key in byFrom || key in groups

    /** The other keys that count as the same shop as [key] (the shop itself and the other names), the shop first; empty when it has none. */
    fun membersOf(key: String): List<String> = group(key).filter { it != key }

    /** Every key of the shop [key] stands for: the shop itself first, then the names that count as it. */
    fun group(key: String): List<String> { val head = resolve(key); return listOf(head) + groups[head].orEmpty() }

    fun sameShop(a: String, b: String): Boolean = resolve(a) == resolve(b)

    /** How the shop [key] resolves to is written ("Swiggy"): the label the person's alias gave it, else null. */
    fun labelOf(key: String): String? {
        val head = resolve(key)
        return byFrom.values.firstOrNull { it.to == head }?.toLabel
    }

    /** The written names of every member of [key]'s shop that the person's aliases know, the shop's own first. Used to find the entries of the other names (a search, the history of a payee). */
    fun labelsOf(key: String): List<String> {
        val head = resolve(key)
        val out = LinkedHashSet<String>()
        byFrom.values.firstOrNull { it.to == head }?.let { out += it.toLabel }
        for (m in groups[head].orEmpty()) byFrom[m]?.let { out += it.fromLabel }
        return out.toList()
    }

    /** The table with [alias] in it (replacing what [alias].from counted as before). Does not check; see [problemFor]. */
    fun with(alias: PayeeAlias): PayeeAliases = PayeeAliases(byFrom.values.filter { it.from != alias.from } + alias)

    fun without(from: String): PayeeAliases = if (from !in byFrom) this else PayeeAliases(byFrom.values.filter { it.from != from })

    /** What is wrong with making [from] count as [to], or null. Replacing an alias [from] already has is allowed; making one shop count as itself, or a cycle, is not. */
    fun problemFor(from: String, to: String, fromLabel: String = from, toLabel: String = to): String? {
        if (from.length < 2 || to.length < 2) return "Type the name of a shop."
        if (byFrom[from]?.to == to) return "$fromLabel already counts as $toLabel."
        // without the alias [from] has now: if [to] still ends up at [from] they are one shop already (or the new alias would be a cycle)
        if (from == to || without(from).resolve(to) == from) return "$fromLabel and $toLabel already count as one shop."
        return null
    }

    companion object {
        val NONE = PayeeAliases()
        const val MAX_HOPS = 8

        /** The setting a stored alias lives in: [PREFIX] and the key that counts as another. */
        const val PREFIX = "payee_alias:"

        /** One stored value: the key it counts as and the two written names, tab-separated (a name never holds a tab). */
        fun encode(a: PayeeAlias): String = listOf(a.to, clean(a.fromLabel), clean(a.toLabel)).joinToString("\t")

        fun decode(settingKey: String, value: String): PayeeAlias? {
            val from = settingKey.removePrefix(PREFIX).takeIf { it.length >= 2 } ?: return null
            val parts = value.split('\t')
            val to = parts.getOrNull(0)?.takeIf { it.length >= 2 && it != from } ?: return null
            return PayeeAlias(from, to, parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: from, parts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: to)
        }

        private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').trim()
    }
}

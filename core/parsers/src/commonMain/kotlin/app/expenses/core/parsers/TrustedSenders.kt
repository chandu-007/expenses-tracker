package app.expenses.core.parsers

/**
 * The sender names a person added by hand ("trusted senders"), kept in ONE setting string: the names separated by new lines. Older versions also let the
 * person type commas or spaces, so reading accepts any of comma, semicolon and whitespace between names; writing always uses new lines. A name is a header root
 * as [SenderRules.headerRoot] sees it (3 to 12 letters and digits), and capture compares it without regard to case, so names show and are kept upper-case.
 */
object TrustedSenders {
    /** What adding one typed name did. [Added.stored] is the whole new setting value to save; the other results change nothing. */
    sealed interface AddResult {
        data class Added(val name: String, val stored: String) : AddResult
        data class Duplicate(val name: String) : AddResult
        data class BuiltIn(val name: String, val issuer: String) : AddResult
        data object Empty : AddResult
        data object Invalid : AddResult
    }

    private fun isSeparator(c: Char) = c == ',' || c == ';' || c.isWhitespace()

    /** The names in a stored value, upper-case, in the order first written, without blanks or repeats. Anything odd that an old version stored is kept as a name so it can be removed. */
    fun parse(stored: String?): List<String> {
        if (stored.isNullOrBlank()) return emptyList()
        val out = LinkedHashSet<String>()
        val cur = StringBuilder()
        for (c in stored + "\n") {
            if (isSeparator(c)) { if (cur.isNotEmpty()) { out += cur.toString().uppercase(); cur.clear() } } else cur.append(c)
        }
        return out.toList()
    }

    /** The name [typed] stands for ("  ax-mybank-s " is MYBANK), or null when it is not a header root. */
    fun normalise(typed: String): String? = SenderRules.headerRoot(typed.trim())

    fun add(stored: String?, typed: String): AddResult {
        if (typed.isBlank()) return AddResult.Empty
        val name = normalise(typed) ?: return AddResult.Invalid
        SenderRules.BUILT_IN[name]?.let { return AddResult.BuiltIn(name, it) }
        val have = parse(stored)
        if (name in have) return AddResult.Duplicate(name)
        return AddResult.Added(name, serialize(have + name))
    }

    /** The setting value without [name] (any case); every other name stays. */
    fun remove(stored: String?, name: String): String = serialize(parse(stored).filter { !it.equals(name.trim(), ignoreCase = true) })

    fun serialize(names: List<String>): String = names.joinToString("\n")
}

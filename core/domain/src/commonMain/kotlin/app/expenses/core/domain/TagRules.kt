package app.expenses.core.domain

/**
 * Tags are short free-text labels, many per entry, for trips, projects, tax items and anything else ("goa-trip", "80C", "reimbursable").
 * They are compared ignoring case; the first spelling used is kept everywhere so "Trip" and "trip" are one tag.
 * A tag has no commas (the history and the CSV list tags with commas) and is at most [MAX_LENGTH] characters; an entry has at most [MAX_PER_ENTRY].
 */
object TagRules {
    const val MAX_LENGTH = 30
    const val MAX_PER_ENTRY = 10

    /** What was typed as a tag, tidied: a leading `#` and spaces at the ends dropped, commas and runs of spaces made single spaces. Null when nothing is left. */
    fun normalize(text: String): String? =
        text.replace(',', ' ').trim().trimStart('#').replace(Regex("\\s+"), " ").trim().take(MAX_LENGTH).trim().ifEmpty { null }

    /** A tidied list: each tag normalised, repeats (ignoring case) dropped, at most [MAX_PER_ENTRY] kept in the order given. */
    fun clean(tags: List<String>): List<String> {
        val seen = HashSet<String>(); val out = ArrayList<String>()
        for (t in tags) { val n = normalize(t) ?: continue; if (seen.add(n.lowercase())) out += n }
        return out.take(MAX_PER_ENTRY)
    }

    /** Why adding [text] to [current] is refused, or null. */
    fun checkAdd(current: List<String>, text: String): String? {
        val n = normalize(text) ?: return "Type a tag."
        if (current.any { it.equals(n, ignoreCase = true) }) return "That tag is already on this entry."
        if (current.size >= MAX_PER_ENTRY) return "An entry can have up to $MAX_PER_ENTRY tags."
        return null
    }

    /** Stored as one text for the change log: the tags in alphabetical order, joined by commas. */
    fun joined(tags: List<String>): String = tags.sortedBy { it.lowercase() }.joinToString(",")

    fun split(joined: String?): List<String> = joined.orEmpty().split(',').filter { it.isNotEmpty() }
}

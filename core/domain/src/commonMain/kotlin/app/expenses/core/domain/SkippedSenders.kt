package app.expenses.core.domain

/**
 * What is kept about a sender name that is not on the list of banks: its header root (`ICICIT` for `BP-ICICIT-S`), how many texts it sent, when the last one arrived, and which category
 * suffixes its headers carried (`S`, `T`, `P`, `G`, or `-` for a header with none). That is all: the sender's NAME. No word of any message, no amount, no digit, nothing from the text.
 */
data class SkippedCounter(val root: String, val count: Long, val lastAt: Long, val suffixes: String)

/** One row of "Senders we skipped". */
data class SkippedSenderRow(val root: String, val count: Long, val lastAt: Long)

/**
 * Pure logic of the "Senders we skipped" list, which helps a person who is missing bank texts to see which sender names the app skipped and to trust one of them. Everything here is about the
 * header only. The counters live in the settings table (`skipped_sender:<ROOT>`), are device-local (not on the backup whitelist, so no backup carries them) and at most [MAX_ROOTS] are kept.
 *
 * Which senders are worth listing is judged by the header's category suffix, which India's registered headers carry: `T` (transactional) and `S` (service or bank alert) may be a bank;
 * `P` (promotional) and `G` (government) are not and are hidden. A header with no suffix, or a letter that is none of these, cannot be classified: it is listed only once it has sent
 * [UNCLASSIFIED_MIN_COUNT] texts. A phone number or an email never reaches this list: only alphanumeric headers are counted.
 */
object SkippedSenders {
    /** Settings key prefix: `skipped_sender:<ROOT>`. */
    const val KEY_PREFIX = "skipped_sender:"

    /** At most this many sender names are kept; the one that has been quiet longest goes first. */
    const val MAX_ROOTS = 20

    /** Only the last 30 days are counted and listed (what capture health looks back over, too). */
    const val WINDOW_MS = 30L * 86_400_000

    /** A header with no usable suffix is listed only after this many texts. */
    const val UNCLASSIFIED_MIN_COUNT = 3

    /** The suffix recorded for a header that has none. */
    const val NO_SUFFIX = '-'

    fun key(root: String) = KEY_PREFIX + root.trim().uppercase()

    /** `<count>|<lastAt epoch ms>|<suffixes>`. */
    fun encode(c: SkippedCounter): String = "${c.count}|${c.lastAt}|${c.suffixes}"

    /** The counter a settings row stands for, or null when the key is not a counter key or the value is not what [encode] writes (a damaged row is ignored, never shown). */
    fun decode(key: String, value: String): SkippedCounter? {
        if (!key.startsWith(KEY_PREFIX)) return null
        val root = key.removePrefix(KEY_PREFIX).takeIf { it.isNotEmpty() } ?: return null
        val parts = value.split('|')
        if (parts.size != 3) return null
        val count = parts[0].toLongOrNull()?.takeIf { it > 0 } ?: return null
        val at = parts[1].toLongOrNull() ?: return null
        if (parts[2].isEmpty() || !parts[2].all { it == NO_SUFFIX || it in 'A'..'Z' }) return null
        return SkippedCounter(root, count, at, parts[2])
    }

    fun decodeAll(rows: List<Pair<String, String>>): List<SkippedCounter> = rows.mapNotNull { (k, v) -> decode(k, v) }

    /**
     * The counter after one more text from [root] with category suffix [suffix] (null: none) arrived at [now]. [before] is what was kept; a sender quiet for more than [WINDOW_MS] starts again
     * from one, so the count is always "in the last 30 days" or close to it.
     */
    fun counted(before: SkippedCounter?, root: String, suffix: Char?, now: Long): SkippedCounter {
        val s = suffix?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: NO_SUFFIX
        val fresh = before == null || before.lastAt < now - WINDOW_MS
        val kept = if (fresh) "" else before!!.suffixes
        val suffixes = (kept + s).toSortedSet().joinToString("")
        return SkippedCounter(root.trim().uppercase(), if (fresh) 1 else before!!.count + 1, now, suffixes)
    }

    /** The roots whose counters to delete so that no more than [cap] stay: the quietest first (ties by name). [keep] is never dropped, so the sender just counted survives. */
    fun toDrop(counters: List<SkippedCounter>, keep: String, cap: Int = MAX_ROOTS): List<String> {
        if (counters.size <= cap) return emptyList()
        return counters.filter { it.root != keep }.sortedWith(compareBy<SkippedCounter> { it.lastAt }.thenBy { it.root }).take(counters.size - cap).map { it.root }
    }

    /** What a header's suffixes say: it may be a bank (`T` or `S` seen), is not one (`P` or `G` and no `T` or `S`), or cannot be told. */
    enum class Kind { MAYBE_BANK, NOT_A_BANK, UNCLASSIFIED }

    fun kindOf(suffixes: String): Kind = when {
        suffixes.any { it == 'T' || it == 'S' } -> Kind.MAYBE_BANK
        suffixes.any { it == 'P' || it == 'G' } -> Kind.NOT_A_BANK
        else -> Kind.UNCLASSIFIED
    }

    /** Whether [c] is worth listing: a header that may be a bank, or one that cannot be classified but has sent at least [UNCLASSIFIED_MIN_COUNT] texts. */
    fun listed(c: SkippedCounter): Boolean = when (kindOf(c.suffixes)) {
        Kind.MAYBE_BANK -> true
        Kind.NOT_A_BANK -> false
        Kind.UNCLASSIFIED -> c.count >= UNCLASSIFIED_MIN_COUNT
    }

    /**
     * The rows to list: counters seen in the last [WINDOW_MS] that are [listed] and not in [excluded] (the built-in list and the senders the person trusts, upper case). Most texts first,
     * then the most recent, then by name.
     */
    fun rows(counters: List<SkippedCounter>, excluded: Set<String>, now: Long): List<SkippedSenderRow> =
        counters.filter { it.root !in excluded && it.lastAt >= now - WINDOW_MS && listed(it) }
            .map { SkippedSenderRow(it.root, it.count, it.lastAt) }
            .sortedWith(compareByDescending<SkippedSenderRow> { it.count }.thenByDescending { it.lastAt }.thenBy { it.root })
}

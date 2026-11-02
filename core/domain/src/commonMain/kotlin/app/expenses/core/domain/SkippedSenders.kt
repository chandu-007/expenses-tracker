package app.expenses.core.domain

/**
 * The one redacted outline kept for a sender whose payment-shaped texts were skipped because the sender is not on the list: when the newest one arrived, whether it looked like money out
 * or in, and a masked outline of its wording (never a digit, never a word that is not generic payment vocabulary: see `UnlistedSenders` in core:parsers). The text itself is kept nowhere.
 */
data class SkippedHint(val root: String, val at: Long, val out: Boolean, val outline: String)

/** One row of "Texts we skipped from unknown senders". */
data class SkippedSenderRow(val root: String, val count: Long, val lastAt: Long, val out: Boolean, val outline: String)

/**
 * Pure logic of the skipped-senders list: how a hint is stored in the settings table, which hints to drop to stay within [MAX_HINTS], and which senders to list. The settings keys are
 * device-local (they are not on the backup whitelist, so they never enter a backup).
 */
object SkippedSenders {
    /** The outcome name capture records for a payment-shaped text from an unlisted alphanumeric header (see `CaptureEventOutcome.UNLISTED_PAYMENT`). */
    const val OUTCOME = "UNLISTED_PAYMENT"

    /** Settings key prefix: `skipped_hint:<ROOT>`. */
    const val KEY_PREFIX = "skipped_hint:"

    /** At most this many hints are kept; the oldest go first. */
    const val MAX_HINTS = 20

    /** Only the last 30 days of skipped texts are listed (what capture health looks back over, too). */
    const val WINDOW_MS = 30L * 86_400_000

    fun key(root: String) = KEY_PREFIX + root.trim().uppercase()

    /** `<epochMillis>|<OUT or IN>|<outline>`. */
    fun encode(h: SkippedHint): String = "${h.at}|${if (h.out) "OUT" else "IN"}|${h.outline}"

    /** The hint a settings row stands for, or null when the key is not a hint key or the value is not what [encode] writes (a damaged row is ignored, never shown). */
    fun decode(key: String, value: String): SkippedHint? {
        if (!key.startsWith(KEY_PREFIX)) return null
        val root = key.removePrefix(KEY_PREFIX).takeIf { it.isNotEmpty() } ?: return null
        val parts = value.split('|', limit = 3)
        if (parts.size != 3) return null
        val at = parts[0].toLongOrNull() ?: return null
        val out = when (parts[1]) { "OUT" -> true; "IN" -> false; else -> return null }
        return SkippedHint(root, at, out, parts[2])
    }

    /** The hints held in settings rows (key to value), damaged ones left out. */
    fun decodeAll(rows: List<Pair<String, String>>): List<SkippedHint> = rows.mapNotNull { (k, v) -> decode(k, v) }

    /** The roots whose hints to delete so that no more than [cap] stay: the oldest first (ties by name). [keep] is never dropped, so the hint just written survives. */
    fun toDrop(hints: List<SkippedHint>, keep: String, cap: Int = MAX_HINTS): List<String> {
        if (hints.size <= cap) return emptyList()
        return hints.filter { it.root != keep }.sortedWith(compareBy<SkippedHint> { it.at }.thenBy { it.root }).take(hints.size - cap).map { it.root }
    }

    /**
     * The rows to list. A sender is listed when it has skipped payment-shaped texts in the last [WINDOW_MS] ([counts] of [OUTCOME]) AND a hint no older than that, and it is not in
     * [excluded] (the built-in list and the senders the person trusts, upper case). Most skipped texts first, then by name.
     */
    fun rows(counts: List<CaptureCount>, hints: List<SkippedHint>, excluded: Set<String>, now: Long): List<SkippedSenderRow> {
        val newest = hints.groupBy { it.root }.mapValues { (_, l) -> l.maxBy { it.at } }
        return counts.filter { it.outcome == OUTCOME && it.root.isNotEmpty() && it.root !in excluded && it.count > 0 && it.lastAt >= now - WINDOW_MS }
            .groupBy { it.root }
            .mapNotNull { (root, list) ->
                val hint = newest[root]?.takeIf { it.at >= now - WINDOW_MS } ?: return@mapNotNull null
                SkippedSenderRow(root, list.sumOf { it.count }, list.maxOf { it.lastAt }, hint.out, hint.outline)
            }
            .sortedWith(compareByDescending<SkippedSenderRow> { it.count }.thenBy { it.root })
    }
}

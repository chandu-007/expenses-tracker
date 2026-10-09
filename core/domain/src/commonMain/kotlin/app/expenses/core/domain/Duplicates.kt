package app.expenses.core.domain

/** An entry already in the ledger that a captured transaction might be, reduced to what matching needs. [day] is the local calendar day as an epoch-day number. */
data class DupEntry(val id: String, val amountMinor: Long, val day: Long, val reference: String?, val label: String)

enum class DupStrength {
    /** The bank's own reference is the same: it is the same transaction. */
    SAME_REFERENCE,

    /** Same amount and direction within a day of each other, but no shared reference: probably the same, for a person to decide. */
    LIKELY,
}

data class DupMatch(val entry: DupEntry, val strength: DupStrength)

/** A pending item as far as matching two pending items goes. */
data class PendingKey(val id: String, val direction: String, val amountMinor: Long, val receivedAt: Long, val reference: String?, val sender: String = id, val fromApp: Boolean = false)

object Duplicates {
    /** How far apart (in days) two dates may be and still count as the same day's payment: banks date a transaction when it posts, people enter it when it happens. */
    const val DAY_WINDOW = 1L

    /** Two texts about the same payment arrive together: a bank's alert and an app's, or a bank's alert and its own reminder. */
    const val PENDING_WINDOW_MS = 10 * 60_000L

    /**
     * The entry a captured transaction most likely is, if any. [entries] are those with the same amount and direction; the choice is the one with the
     * same reference, otherwise the nearest day (ties by [DupEntry.id], so the answer is the same every time). Null when nothing is within [DAY_WINDOW].
     */
    fun best(day: Long, reference: String?, entries: List<DupEntry>): DupMatch? {
        val ref = reference?.trim()?.takeIf { it.isNotEmpty() }
        if (ref != null) entries.filter { it.reference.equals(ref, ignoreCase = true) }.minByOrNull { it.id }?.let { return DupMatch(it, DupStrength.SAME_REFERENCE) }
        // two different bank references are two payments: the same amount a minute apart is not a repeat of the first
        return entries.filter { kotlin.math.abs(it.day - day) <= DAY_WINDOW && !(ref != null && it.reference?.trim()?.isNotEmpty() == true && !it.reference.trim().equals(ref, ignoreCase = true)) }.minWithOrNull(compareBy<DupEntry>({ kotlin.math.abs(it.day - day) }, { it.id }))?.let { DupMatch(it, DupStrength.LIKELY) }
    }

    /**
     * For each pending item, the older pending item it probably repeats: same direction and amount, received within [PENDING_WINDOW_MS], and not a
     * different bank reference (two payments of the same amount with different references are two payments) and, for two texts from one sender, only when the
     * reference is the same: one bank rarely texts twice about one payment, but a person often pays the same amount twice. The exception is a payment app's
     * notifications ([PendingKey.fromApp]): an app often posts the same notification twice, so two from one app are a likely repeat. The older item is the original.
     */
    fun repeatsOfPending(items: List<PendingKey>): Map<String, String> {
        val out = HashMap<String, String>()
        val sorted = items.sortedWith(compareBy({ it.receivedAt }, { it.id }))
        for ((i, later) in sorted.withIndex()) {
            val earlier = sorted.subList(0, i).lastOrNull { e ->
                e.direction == later.direction && e.amountMinor == later.amountMinor && later.receivedAt - e.receivedAt <= PENDING_WINDOW_MS &&
                    !(e.reference != null && later.reference != null && !e.reference.equals(later.reference, ignoreCase = true)) &&
                    (e.sender != later.sender || (e.fromApp && later.fromApp) || (e.reference != null && later.reference != null))
            }
            if (earlier != null) out[later.id] = earlier.id
        }
        return out
    }
}

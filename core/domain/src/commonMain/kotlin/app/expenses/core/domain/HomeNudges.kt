package app.expenses.core.domain

/**
 * The things Home can ask the person to look at, in the order they are put first: the enum order IS the priority order, highest first. Home shows ONE of them at a time
 * (the highest that is present and not put off); the rest stay one tap away (the line under the card opens More, whose rows carry the same counts).
 *
 * 1. [BILLS_DUE]: a bill is due this week or is overdue. It costs money (a late fee, a lapsed policy) and has a date, so it comes before everything.
 * 2. [INBOX]: texts and alerts waiting in To review. Each is money to place in the books, and the list grows with every alert.
 * 3. [BILLS_NEW]: bills found in messages, not yet added to be tracked (nothing due soon).
 * 4. [WAITING]: money expected back for expenses (a loan, a shared bill).
 * 5. [CATEGORISE]: expenses with no category. Tidying; nothing is lost by waiting.
 * 6. [SETUP]: "Set up reading bank texts" (the guided first-run setup, [CaptureSetup]), offered after the first tour and kept until it is finished, hidden or capture is running. A new install has nothing above it; it comes before the checklist because it is the one step that makes the checklist's other ticks happen by themselves.
 * 7. [FIRST_WEEK]: the "Getting started" checklist. Last: it is for someone with nothing else to do yet, and a person with real things waiting should see those.
 *
 * Not in this list, on purpose: the budget block and its safe-to-spend line (part of the Budget section the person arranges, not a card that asks for something), Due today
 * (the Bills section: posting a repeating item is the section's own job), the Welcome card (what the empty Home is made of) and the backup reminder and the iPhone signing
 * notice (More page only, see [moreNotice]).
 *
 * Each can be put off. [INBOX], [BILLS_DUE], [BILLS_NEW] and [WAITING] by "Not now": gone for [SNOOZE_HOURS] hours, or until more is waiting than when it was put off ([NudgeSnooze]);
 * [CATEGORISE] has its own week ([UncategorisedNudge.SNOOZE_DAYS]); [SETUP] by "Not now" (a day, like the others) or Hide (for good); [FIRST_WEEK] by Hide, for good.
 */
enum class HomeNudge(val key: String, val label: String) {
    BILLS_DUE("bills_due", "Bills due"),
    INBOX("inbox", "To review"),
    BILLS_NEW("bills_new", "New bills"),
    WAITING("waiting", "Money to get back"),
    CATEGORISE("categorise", "Expenses with no category"),
    SETUP("setup", "Set up reading bank texts"),
    FIRST_WEEK("first_week", "Getting started"),
}

/** When a nudge was put off: [atMillis], and how many things it was about then ([count]). Stored as `atMillis|count` under one setting per nudge. */
data class NudgeSnooze(val atMillis: Long, val count: Int) {
    fun encode(): String = "$atMillis|$count"

    companion object {
        fun decode(raw: String?): NudgeSnooze? {
            val parts = raw?.split('|') ?: return null
            if (parts.size != 2) return null
            val at = parts[0].toLongOrNull() ?: return null
            val n = parts[1].toIntOrNull() ?: return null
            return NudgeSnooze(at, n)
        }
    }
}

object HomeNudges {
    /** How long "Not now" keeps a nudge away unless more arrives. */
    const val SNOOZE_HOURS = 24L
    private const val HOUR = 3_600_000L

    /** The settings key a nudge's put-off is kept under. */
    fun settingKey(n: HomeNudge) = "nudge_snoozed:" + n.key

    /** Whether [snooze] still hides a nudge that is now about [countNow] things: inside its hours, and nothing more than before. A clock gone backwards counts as inside. */
    fun isPutOff(snooze: NudgeSnooze?, countNow: Int, nowMillis: Long): Boolean {
        if (snooze == null) return false
        if (countNow > snooze.count) return false
        return nowMillis - snooze.atMillis < SNOOZE_HOURS * HOUR
    }

    /**
     * What Home shows, in order: the nudges in [present] (nudge to the number of things it is about; a count of 0 or less is not present) that are not put off in [snoozes].
     * The first is the card; the others are the "also waiting" line.
     */
    fun visible(present: Map<HomeNudge, Int>, snoozes: Map<HomeNudge, NudgeSnooze>, nowMillis: Long): List<HomeNudge> =
        HomeNudge.entries.filter { n -> (present[n] ?: 0) > 0 && !isPutOff(snoozes[n], present.getValue(n), nowMillis) }

    /** The one nudge to draw as a card, or null. */
    fun card(visible: List<HomeNudge>): HomeNudge? = visible.firstOrNull()

    /** The line under the card: what else is waiting, by name, in order; empty when nothing else is. */
    fun alsoWaiting(visible: List<HomeNudge>): List<HomeNudge> = visible.drop(1)

    /** The nudges whose count says how much is waiting (a checklist or the capture setup has no such count). */
    private val COUNTED = setOf(HomeNudge.BILLS_DUE, HomeNudge.INBOX, HomeNudge.BILLS_NEW, HomeNudge.WAITING, HomeNudge.CATEGORISE)

    /** "Also waiting: To review (3), Expenses with no category (5). See More", or null when nothing else is. Counted names carry their count so the line says how much waits. */
    fun alsoWaitingLine(visible: List<HomeNudge>, counts: Map<HomeNudge, Int>): String? {
        val rest = alsoWaiting(visible)
        if (rest.isEmpty()) return null
        return "Also waiting: " + rest.joinToString(", ") { n -> if (n in COUNTED && (counts[n] ?: 0) > 0) "${n.label} (${counts[n]})" else n.label } + ". See More"
    }

    /** On the More page one notice at a time: the iPhone build's expiry warning outranks the backup reminder (a build that stops opening makes a backup moot). */
    enum class MoreNotice { SIGNING, BACKUP }

    fun moreNotice(signingShowing: Boolean, backupDue: Boolean): MoreNotice? = when { signingShowing -> MoreNotice.SIGNING; backupDue -> MoreNotice.BACKUP; else -> null }
}

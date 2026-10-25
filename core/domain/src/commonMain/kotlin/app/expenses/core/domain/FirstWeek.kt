package app.expenses.core.domain

/** The five things worth doing in the first week, in the order they are shown. */
enum class FirstWeekStep { ACCOUNT_DIGITS, BUDGET, CAPTURE, BACKUP, ENTRY }

/** How this phone brings messages in, which decides the wording (and the tick) of the capture step. [NONE]: the platform has no way, so the step is left out. */
enum class FirstWeekCapture { NONE, SWITCH, SHORTCUT }

/** One line of the checklist. */
data class FirstWeekItem(val step: FirstWeekStep, val title: String, val note: String, val done: Boolean)

/** What the checklist is worked out from. Times are epoch milliseconds. */
data class FirstWeekInputs(
    /** When the first entry was noticed, or null while there is none. */
    val startedAtMillis: Long?,
    val nowMillis: Long,
    /** The person closed the card. */
    val dismissed: Boolean,
    /** An account that is not archived carries its last four digits. */
    val accountWithDigits: Boolean,
    /** Any budget limit is set. */
    val budgetSet: Boolean,
    val capture: FirstWeekCapture,
    /** [FirstWeekCapture.SWITCH]: a text, notification or email switch is on. [FirstWeekCapture.SHORTCUT]: a message (a test or a real one) has reached the app. */
    val captureOn: Boolean,
    /** A backup was made, or automatic copies to a folder were chosen. */
    val backupDone: Boolean,
    /** At least one entry exists, typed or captured. */
    val hasEntry: Boolean,
)

/**
 * The checklist on Home for the first week: a few steps that make the app work by itself, each ticked as soon as it is true. It exists for [DAYS] days from the first
 * entry, and goes away when everything is done or the person closes it. Never fewer than the steps the platform can do: no capture step where there is no capture.
 */
object FirstWeek {
    const val DAYS = 14
    private const val DAY_MS = 86_400_000L

    /** The lines to show, or empty when the card should not be on Home at all (closed, not started, past [DAYS] days, or everything is done). */
    fun items(i: FirstWeekInputs): List<FirstWeekItem> {
        val started = i.startedAtMillis ?: return emptyList()
        if (i.dismissed) return emptyList()
        // a clock that went backwards counts as the first day rather than hiding the card
        val age = (i.nowMillis - started).coerceAtLeast(0)
        if (age >= DAYS * DAY_MS) return emptyList()
        val all = buildList {
            add(FirstWeekItem(FirstWeekStep.ACCOUNT_DIGITS, "Add an account with its last four digits", "The digits let a bank text find its account by itself", i.accountWithDigits))
            add(FirstWeekItem(FirstWeekStep.BUDGET, "Set a budget", "A limit for the month, so Home can tell you how you are doing", i.budgetSet))
            when (i.capture) {
                FirstWeekCapture.SWITCH -> add(FirstWeekItem(FirstWeekStep.CAPTURE, "Turn on capture", "Texts or payment notifications become entries for you to review", i.captureOn))
                FirstWeekCapture.SHORTCUT -> add(FirstWeekItem(FirstWeekStep.CAPTURE, "Set up capture and send a test", "A Shortcuts automation hands bank texts to the app", i.captureOn))
                FirstWeekCapture.NONE -> Unit
            }
            add(FirstWeekItem(FirstWeekStep.BACKUP, "Make a backup", "Or pick a folder for automatic copies: the data lives only on this phone", i.backupDone))
            add(FirstWeekItem(FirstWeekStep.ENTRY, "Add or capture an entry", "Typed by you or read from a message", i.hasEntry))
        }
        return if (all.all { it.done }) emptyList() else all
    }
}

package app.expenses.core.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/**
 * UI S6 (U-11): the small chip that says when the last bank text reached the app, so a capture that has silently stopped is noticed in days, not months.
 * Only one timestamp is stored (`capture_last_seen_at`, epoch milliseconds); the wording and the state are worked out here from it, [nowMillis] and the phone's zone.
 * Calm by design: after a quiet stretch the state is [State.QUIET] (drawn amber, never red) and the wording stays the same plain sentence.
 */
object CaptureHeartbeat {
    /** More than this many days with no bank text, while capture is on, is a quiet stretch. */
    const val QUIET_DAYS = 5

    private const val DAY_MS = 86_400_000L

    enum class State { FRESH, QUIET }

    /**
     * [text] is the sentence on the chip; [note] a second line, only after a quiet stretch, that says so in words (colour is never the only cue) and what a tap does;
     * [stateWords] is the state for a screen reader.
     */
    data class Chip(val text: String, val state: State, val note: String?, val stateWords: String)

    /**
     * The chip, or null when it should not show: capture is off, or no bank text was ever seen ([lastSeenMillis] null).
     * [shortcut] words it for the iPhone, where only a message that came through a Shortcut (or was pasted) is ever counted.
     */
    fun chip(nowMillis: Long, lastSeenMillis: Long?, captureOn: Boolean, zone: TimeZone, shortcut: Boolean = false, quietDays: Int = QUIET_DAYS): Chip? {
        if (!captureOn || lastSeenMillis == null) return null
        val then = Instant.fromEpochMilliseconds(lastSeenMillis).toLocalDateTime(zone)
        val today = Instant.fromEpochMilliseconds(nowMillis).toLocalDateTime(zone).date
        val daysAgo = today.toEpochDays().toLong() - then.date.toEpochDays().toLong()
        val clock = clock(then.hour, then.minute)
        val whenText = when {
            daysAgo <= 0L -> "today $clock"
            daysAgo == 1L -> "yesterday $clock"
            else -> "on ${then.date.dayOfMonth} ${MONTHS[then.date.month.number - 1]}, $clock"
        }
        val text = if (shortcut) "Last bank text through Shortcuts $whenText" else "Last bank text seen $whenText"
        val quiet = nowMillis - lastSeenMillis > quietDays * DAY_MS
        val state = if (quiet) State.QUIET else State.FRESH
        val days = (nowMillis - lastSeenMillis) / DAY_MS
        return if (quiet) Chip(text, state, "No bank text for $days days. Tap to find out why.", "Quiet: no bank text for $days days")
        else Chip(text, state, null, "Seen recently")
    }

    /** "8:14 pm", "12:05 am". */
    private fun clock(hour: Int, minute: Int): String {
        val h = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
        return "$h:${minute.toString().padStart(2, '0')} ${if (hour < 12) "am" else "pm"}"
    }

    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
}

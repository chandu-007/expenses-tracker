package app.expenses.core.domain

/** When the last backup was made and how many entries it held. */
data class LastBackup(val atMillis: Long, val entryCount: Long)

/**
 * Decides when to remind the user to make a backup. The app keeps everything on the phone, so a lost or reset phone loses everything; the
 * reminder is shown only once there is something worth protecting and never nags: it can be put off, and then stays quiet for a while.
 *
 * - No backup was ever made: remind once there are [FIRST_BACKUP_ENTRIES] entries.
 * - A backup exists: remind only when at least [NEW_ENTRIES_SINCE] entries were added since it and it is at least [STALE_DAYS] old.
 * - Put off ("later"): stay quiet for [SNOOZE_DAYS] days.
 *
 * Times are epoch milliseconds. A clock that seems to have gone backwards is treated as "not yet" rather than as a reason to remind.
 */
object BackupNudge {
    const val FIRST_BACKUP_ENTRIES = 10L
    const val NEW_ENTRIES_SINCE = 20L
    const val STALE_DAYS = 30L
    const val SNOOZE_DAYS = 14L
    private const val DAY = 86_400_000L

    fun shouldShow(liveEntries: Long, lastBackup: LastBackup?, snoozedAtMillis: Long?, nowMillis: Long): Boolean {
        if (snoozedAtMillis != null && nowMillis - snoozedAtMillis < SNOOZE_DAYS * DAY) return false
        return if (lastBackup == null) liveEntries >= FIRST_BACKUP_ENTRIES
        else liveEntries - lastBackup.entryCount >= NEW_ENTRIES_SINCE && nowMillis - lastBackup.atMillis >= STALE_DAYS * DAY
    }
}

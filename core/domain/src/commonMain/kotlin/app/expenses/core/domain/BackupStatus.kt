package app.expenses.core.domain

/** The one line at the top of Your data that says how safe the data is: when the last backup was made and how much has been added since. No amounts are in it. */
object BackupStatus {
    const val NONE = "No backup yet"

    /**
     * [lastAt] is when the last backup was made (epoch ms; null if never), [countAtBackup] how many entries it held, [liveCount] how many entries there are now (null while that is
     * still being counted: the line then says only when). "Last backup: 3 days ago · 12 entries added since".
     */
    fun text(now: Long, lastAt: Long?, countAtBackup: Long, liveCount: Long?): String {
        if (lastAt == null) return NONE
        val lead = "Last backup: ${RelativeTime.ago(lastAt, now)}"
        if (liveCount == null) return lead
        val added = liveCount - countAtBackup
        return when {
            added <= 0L -> "$lead \u00B7 nothing new since"
            added == 1L -> "$lead \u00B7 1 entry added since"
            else -> "$lead \u00B7 $added entries added since"
        }
    }
}

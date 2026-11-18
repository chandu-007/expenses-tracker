package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BackupStatusTest {
    private val day = 86_400_000L
    private val now = 1_000 * day

    @Test
    fun withNoBackupItSaysSo() {
        assertEquals("No backup yet", BackupStatus.text(now, null, 0, 40))
        assertEquals("No backup yet", BackupStatus.text(now, null, 0, null))
    }

    @Test
    fun aBackupSaysWhenAndHowManyEntriesWereAddedSince() {
        assertEquals("Last backup: 3 days ago \u00B7 12 entries added since", BackupStatus.text(now, now - 3 * day, 100, 112))
        assertEquals("Last backup: yesterday \u00B7 1 entry added since", BackupStatus.text(now, now - day - 1, 100, 101))
    }

    @Test
    fun nothingAddedOrSomeDeletedSaysNothingNew() {
        assertEquals("Last backup: just now \u00B7 nothing new since", BackupStatus.text(now, now - 5_000, 7, 7))
        assertEquals("Last backup: 2 days ago \u00B7 nothing new since", BackupStatus.text(now, now - 2 * day, 50, 45))
    }

    @Test
    fun whileTheEntriesAreStillBeingCountedItSaysOnlyWhen() {
        assertEquals("Last backup: 3 days ago", BackupStatus.text(now, now - 3 * day, 100, null))
    }

    @Test
    fun aClockThatWentBackwardsReadsAsJustNow() {
        assertEquals("Last backup: just now \u00B7 3 entries added since", BackupStatus.text(now, now + day, 10, 13))
    }

    @Test
    fun theLineNeverCarriesAnAmountOrAWarningWord() {
        val t = BackupStatus.text(now, now - 40 * day, 10, 500)
        assertFalse(t.contains("\u20B9") || t.contains("!") || t.contains("overdue", ignoreCase = true) || t.contains("warning", ignoreCase = true), t)
    }
}

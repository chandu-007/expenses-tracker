package app.expenses.core.domain

import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupNudgeTest {
    private val day = 86_400_000L
    private val now = 1_000 * day
    private fun show(entries: Long, last: LastBackup? = null, snoozed: Long? = null, at: Long = now) = BackupNudge.shouldShow(entries, last, snoozed, at)

    @Test
    fun withNoBackupItWaitsForTenEntries() {
        assertFalse(show(0)); assertFalse(show(9))
        assertTrue(show(10)); assertTrue(show(500))
    }

    @Test
    fun afterABackupItNeedsBothEnoughNewEntriesAndEnoughTime() {
        val last = LastBackup(now - 31 * day, 100)
        assertFalse(show(119, last), "19 new entries")
        assertTrue(show(120, last), "20 new entries and 31 days")
        assertFalse(show(500, LastBackup(now - 29 * day, 100)), "many entries but only 29 days")
        assertTrue(show(500, LastBackup(now - 30 * day, 100)), "exactly 30 days")
        assertFalse(show(100, last), "old, but nothing new to lose")
        assertFalse(show(50, last), "fewer entries than at the backup (some were deleted) never reminds")
    }

    @Test
    fun puttingItOffKeepsItQuietForFourteenDaysThenItMayReturn() {
        assertFalse(show(50, snoozed = now - 1 * day))
        assertFalse(show(50, snoozed = now - 14 * day + 1))
        assertTrue(show(50, snoozed = now - 14 * day))
        val last = LastBackup(now - 90 * day, 0)
        assertFalse(show(500, last, snoozed = now - 3 * day))
        assertTrue(show(500, last, snoozed = now - 20 * day))
    }

    @Test
    fun aClockThatWentBackwardsNeverCausesAReminder() {
        assertFalse(show(500, LastBackup(now + 10 * day, 0)), "the backup is 'in the future'")
        assertFalse(show(50, snoozed = now + 5 * day), "a snooze from the future still counts as recent")
    }

    @Test
    fun anyInputsGiveAnAnswerAndNeverRemindBelowTheFloors() = runTest {
        checkAll(Arb.long(0, 100_000), Arb.long(0, 100_000), Arb.long(0, 3_000 * day)) { entries, atCount, backupAt ->
            val none = show(entries)
            assertEquals(entries >= 10, none, "no backup: exactly the floor")
            val with = show(entries, LastBackup(backupAt, atCount))
            if (with) { assertTrue(entries - atCount >= 20); assertTrue(now - backupAt >= 30 * day) }
        }
    }
}

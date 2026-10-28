package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoExportRulesTest {
    private val day = 86_400_000L
    private val hour = 3_600_000L
    private val t = 1_000 * day

    @Test
    fun offIsNeverDueAndTheFirstRunIsDueAtOnceOtherwise() {
        assertFalse(AutoExportRules.isDue(AutoExportFrequency.OFF, null, t)); assertFalse(AutoExportRules.isDue(AutoExportFrequency.OFF, 0, t))
        for (f in AutoExportFrequency.entries.filter { it != AutoExportFrequency.OFF }) assertTrue(AutoExportRules.isDue(f, null, t), f.name)
    }

    @Test
    fun eachFrequencyWaitsItsDaysWithTwoHoursOfSlackAndANeverTheClockGoingBackwards() {
        for (f in listOf(AutoExportFrequency.DAILY, AutoExportFrequency.WEEKLY, AutoExportFrequency.MONTHLY)) {
            val span = f.days * day
            assertFalse(AutoExportRules.isDue(f, t, t + span - 2 * hour - 1), "${f.name} a moment too early")
            assertTrue(AutoExportRules.isDue(f, t, t + span - 2 * hour), "${f.name} at the slack boundary")
            assertTrue(AutoExportRules.isDue(f, t, t + span * 3))
            assertFalse(AutoExportRules.isDue(f, t, t - 1), "clock set back")
            assertFalse(AutoExportRules.isDue(f, t, t))
        }
    }

    @Test
    fun onlyOurOwnOlderFilesAreChosenForDeletionAndTheNewestAreKept() {
        val names = listOf(
            "expenses-backup-2026-10-01.expbak", "expenses-backup-2026-10-03.expbak", "expenses-backup-2026-10-02.expbak", "expenses-backup-2026-10-03 (1).expbak",
            "expenses-backup-2026-09-30.expbak", "my-notes.txt", "expenses-backup-final.expbak", "expenses-2026-10-01.xlsx", "expenses-backup-2026-10-01.expbak.bak",
        )
        val gone = AutoExportRules.filesToDelete(names, "expenses-backup", "expbak", keep = 3)
        assertEquals(setOf("expenses-backup-2026-10-01.expbak", "expenses-backup-2026-09-30.expbak"), gone.toSet())
        assertEquals(emptyList(), AutoExportRules.filesToDelete(names, "expenses-backup", "expbak", keep = 10))
        assertEquals(4, AutoExportRules.filesToDelete(names, "expenses-backup", "expbak", keep = 1).size, "keeps at least one, never all")
        assertEquals(4, AutoExportRules.filesToDelete(names, "expenses-backup", "expbak", keep = 0).size, "a keep of zero is treated as one")
        assertEquals(emptyList(), AutoExportRules.filesToDelete(listOf("expenses.expbak", "expenses-backup-2026-10-01.expbak.txt"), "expenses-backup", "expbak", 1))
    }

    @Test
    fun countOursCountsOnlyFilesNamedLikeOurCopies() {
        val names = listOf("expenses-2026-10-01.xlsx", "expenses-2026-10-02 (1).xlsx", "expenses-backup-2026-10-01.expbak", "expenses-2026-10-03.xlsx.part", "my-notes.xlsx", "expenses-final.xlsx")
        assertEquals(2, AutoExportRules.countOurs(names, "expenses", "xlsx"))
        assertEquals(1, AutoExportRules.countOurs(names, "expenses-backup", "expbak"))
        assertEquals(0, AutoExportRules.countOurs(emptyList(), "expenses", "xlsx"))
        assertEquals(setOf("expenses-2026-10-01.xlsx", "expenses-2026-10-02 (1).xlsx"), AutoExportRules.ourNames(names, "expenses", "xlsx").toSet(), "only ours, never my-notes.xlsx or a .part")
    }
}

package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.SkippedCounter
import app.expenses.core.domain.SkippedSenders
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The header-only counters of skipped senders: counted, capped at 20, cleared on trust, and in no backup. All names are invented. */
class SkippedSenderStoreTest {
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private val templates = TemplateStore(db, HlcClock("n1")) { 5_000L }

    @Test
    fun eachTextAddsOneToItsSenderAndRemembersTheSuffixesAndNothingElseIsStored() {
        templates.countSkippedSender("MYCOOP", 'S', 1_000); templates.countSkippedSender("MYCOOP", 'T', 2_000); templates.countSkippedSender("OTHERCO", null, 3_000)
        assertEquals("2|2000|ST", templates.setting("skipped_sender:MYCOOP")); assertEquals("1|3000|-", templates.setting("skipped_sender:OTHERCO"))
        assertEquals(setOf(SkippedCounter("MYCOOP", 2, 2_000, "ST"), SkippedCounter("OTHERCO", 1, 3_000, "-")), templates.skippedCounters().toSet())
        assertEquals(listOf("skipped_sender:MYCOOP" to "2|2000|ST", "skipped_sender:OTHERCO" to "1|3000|-"), templates.settingsWithPrefix("skipped_sender:").sortedBy { it.first }, "only the name, a count, a time and suffix letters")
    }

    @Test
    fun atMostTwentyAreKeptTheQuietestGoAndTheOneJustCountedStays() {
        for (i in 1..20) templates.countSkippedSender("R$i", 'S', i * 100L)
        assertEquals(20, templates.skippedCounters().size)
        templates.countSkippedSender("NEWEST", 'S', 5_000)
        val roots = templates.skippedCounters().map { it.root }.toSet()
        assertEquals(20, roots.size); assertTrue("NEWEST" in roots && "R1" !in roots && "R2" in roots)
        templates.countSkippedSender("LATE", 'S', 1)
        assertTrue("LATE" in templates.skippedCounters().map { it.root }, "the one just counted stays even if it is the quietest"); assertEquals(20, templates.skippedCounters().size)
    }

    @Test
    fun trustingASenderForgetsItsCounterAndNoOtherSetting() {
        templates.putSetting("capture_extra_roots", "MYCOOP"); templates.countSkippedSender("MYCOOP", 'S', 1); templates.countSkippedSender("OTHERCO", 'S', 2)
        templates.clearSkippedSender("MYCOOP")
        assertNull(templates.setting("skipped_sender:MYCOOP")); assertEquals(listOf("OTHERCO"), templates.skippedCounters().map { it.root }); assertEquals("MYCOOP", templates.setting("capture_extra_roots"))
    }

    @Test
    fun aBackupCarriesNoCounter() {
        assertFalse(BackupStore.SETTINGS.any { it.startsWith("skipped_sender") }, "the whitelist of settings a backup carries")
        templates.countSkippedSender("MYCOOP", 'S', 1)
        assertTrue(SkippedSenders.KEY_PREFIX !in BackupStore.SETTINGS)
        val d = Device("a"); d.populate(); d.templates.countSkippedSender("MYCOOP", 'S', 1)
        val (_, data) = BackupFile.decode(BackupFile.encode(d.backup.export(), 1L))
        assertTrue(data.settings.keys.none { it.startsWith("skipped_") }, "no skipped_ key in the file: ${data.settings.keys}")
    }
}

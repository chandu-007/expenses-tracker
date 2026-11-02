package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.CaptureCount
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.domain.SkippedHint
import app.expenses.core.domain.SkippedSenders
import app.expenses.core.storage.db.ExpensesDb
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The redacted outline kept per skipped sender: written, replaced by the newest, capped at 20, cleared on trust, and never in a backup. All names and texts are invented. */
class SkippedHintsTest {
    private val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
    private val clock = HlcClock("n1")
    private val templates = TemplateStore(db, clock) { 5_000L }
    private val inbox = InboxStore(db)
    private fun hint(root: String, at: Long, out: Boolean = true, outline: String = "Acct XX### debited") = SkippedHint(root, at, out, outline)

    @Test
    fun aHintIsKeptUnderItsOwnKeyAndANewerOneReplacesIt() {
        templates.recordSkippedHint(hint("MYCOOP", 1_000, true, "Acct XX### debited"))
        assertEquals("1000|OUT|Acct XX### debited", templates.setting("skipped_hint:MYCOOP"))
        templates.recordSkippedHint(hint("MYCOOP", 2_000, false, "Rs ###.## credited"))
        assertEquals(listOf(hint("MYCOOP", 2_000, false, "Rs ###.## credited")), templates.skippedHints())
    }

    @Test
    fun atMostTwentyAreKeptTheOldestAreDroppedAndTheJustWrittenOneIsAlwaysKept() {
        for (i in 1..20) templates.recordSkippedHint(hint("R$i", i * 100L))
        assertEquals(20, templates.skippedHints().size)
        templates.recordSkippedHint(hint("NEWEST", 5_000))
        val roots = templates.skippedHints().map { it.root }.toSet()
        assertEquals(20, roots.size); assertTrue("NEWEST" in roots); assertTrue("R1" !in roots && "R2" in roots)
        templates.recordSkippedHint(hint("LATE-BUT-OLD", 1))
        assertTrue("LATE-BUT-OLD" in templates.skippedHints().map { it.root }, "the one just written stays even if it is the oldest")
        assertEquals(20, templates.skippedHints().size)
    }

    @Test
    fun trustingASenderForgetsItsHintAndNoOtherSetting() {
        templates.putSetting("capture_extra_roots", "MYCOOP")
        templates.recordSkippedHint(hint("MYCOOP", 1)); templates.recordSkippedHint(hint("OTHERCO", 2))
        templates.clearSkippedHint("MYCOOP")
        assertNull(templates.setting("skipped_hint:MYCOOP")); assertEquals(listOf("OTHERCO"), templates.skippedHints().map { it.root })
        assertEquals("MYCOOP", templates.setting("capture_extra_roots"))
    }

    @Test
    fun theListComesFromTheEventCountsAndTheHintsAndTrustingEmptiesIt() {
        val now = 100 * 86_400_000L
        inbox.recordEvent(now - 1_000, "MYCOOP", CaptureEventOutcome.UNLISTED_PAYMENT); inbox.recordEvent(now - 500, "MYCOOP", CaptureEventOutcome.UNLISTED_PAYMENT)
        inbox.recordEvent(now - 500, "PROMO1", CaptureEventOutcome.NOT_ALLOWLISTED)
        templates.recordSkippedHint(hint("MYCOOP", now - 500))
        fun rows(excluded: Set<String>) = SkippedSenders.rows(
            db.schemaQueries.captureEventCounts(now - SkippedSenders.WINDOW_MS).executeAsList().map { CaptureCount(it.sender_root, it.outcome, it.n, it.last_at ?: 0L) }, templates.skippedHints(), excluded, now,
        )
        assertEquals(listOf("MYCOOP" to 2L), rows(emptySet()).map { it.root to it.count })
        assertEquals(emptyList(), rows(setOf("MYCOOP")))
        templates.clearSkippedHint("MYCOOP")
        assertEquals(emptyList(), rows(emptySet()))
    }

    @Test
    fun noBackupCarriesAHint() {
        val ledger = LedgerStore(db, clock) { 5_000L }
        val backup = BackupStore(db, clock, { 5_000L }, ledger, templates, RecurringStore(db, clock, { 5_000L }, ledger))
        templates.recordSkippedHint(hint("MYCOOP", 1)); templates.putSetting("capture_extra_roots", "MYCOOP")
        val data = backup.export()
        assertTrue(data.settings.keys.none { it.startsWith(SkippedSenders.KEY_PREFIX) }, "device-local: not on the backup whitelist")
        assertEquals("MYCOOP", data.settings["capture_extra_roots"], "the trusted senders themselves do travel")
        assertTrue(BackupStore.SETTINGS.none { it.startsWith(SkippedSenders.KEY_PREFIX) })
    }
}

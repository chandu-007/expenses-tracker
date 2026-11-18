package app.expenses.core.storage

import app.expenses.core.domain.ExampleMonth
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The first screen draws an Example month (see [ExampleMonth]). It is only drawn: a ledger that has never had an entry has none, however the example looks, and nothing a person can export or total ever
 * mentions an example merchant.
 */
class ExampleMonthNeverStoredTest {
    @Test
    fun aFreshLedgerHasNoEntriesNoTotalsAndAnExportWithNoneOfTheExampleMerchantsWhileTheExampleExists() {
        assertTrue(ExampleMonth.rows.isNotEmpty() && ExampleMonth.spentMinor > 0, "the example exists")
        val device = Device("fresh")
        assertEquals(0L, device.db.schemaQueries.entryCount().executeAsOne(), "no live entries")
        assertFalse(device.db.schemaQueries.entryCountEver().executeAsOne(), "no entry has ever existed: the welcome would show")
        val backup = device.backup.export()
        assertEquals(emptyList(), backup.entries, "a backup of a fresh ledger holds no entries")
        val csv = CsvExport(device.db).build(TimeZone.UTC)
        for (merchant in ExampleMonth.merchants) {
            assertTrue(backup.entries.none { it.merchant == merchant }, "the backup holds no entry for the example merchant $merchant")
            assertFalse(csv.contains(merchant), "the CSV export does not mention the example merchant $merchant")
        }
        assertEquals(1, csv.trim().lines().size, "the CSV export is the header line only")
    }
}

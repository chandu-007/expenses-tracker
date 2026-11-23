package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.storage.db.ExpensesDb
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** N-23, "Test a backup": the file is read whole and restored into a scratch database; the person's own database is never written. Invented data only. */
class RestoreTrialTest {
    private val pass = "correct horse battery"
    private val noLegacy: (ByteArray, String) -> ByteArray = { _, _ -> error("not an EXPENC1 file") }

    private fun scratch() = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })

    /** The live device: one of everything, plus two photos. */
    private fun live(vault: MapVault): Device {
        val d = Device("live")
        d.populate()
        val att = AttachmentStore(d.db, d.clock) { 2L }
        val rnd = Random(3)
        for ((id, entry, size) in listOf(Triple("p1", "coffee", 70_000), Triple("p2", "dinner", 12))) {
            val bytes = rnd.nextBytes(size)
            vault.files[id] = bytes
            att.add(id, entry, "$id.jpg", "image/jpeg", size.toLong(), Sha256.hex(bytes))
        }
        return d
    }

    private fun backup(d: Device, vault: MapVault, passphrase: String?): ByteArray {
        val data = d.backup.export(vault::size)
        val out = ByteArraySink()
        BackupTransfer.write(data, data.attachments.map { a -> BackupFileSource(a) { vault.open(a.id)!! } }, 1L, passphrase?.let { SealWith(it, JdkSeal) }, out)
        return out.toByteArray()
    }

    /** Everything the device keeps, as the bytes a backup of it would be: equal bytes mean nothing changed. */
    private fun snapshot(d: Device): ByteArray = BackupFile.encode(d.backup.export(), 1L)

    private fun open(bytes: ByteArray, passphrase: String?) = RestoreTrial.open(ByteArraySource(bytes), passphrase, JdkSeal, noLegacy)

    @Test
    fun aPlainBackupOpensAndRestoresIntoTheScratchDatabaseWithTheSameCounts() {
        val vault = MapVault(); val d = live(vault)
        val opened = assertIs<RestoreTrial.Opened.Ready>(open(backup(d, vault, null), null))
        assertEquals(2, opened.files); assertEquals(0, opened.filesBad)
        val counts = RestoreTrial.restoreInto(scratch(), opened.data) { 9_000L }
        assertEquals(d.entries(), counts.entries)
        assertEquals(d.db.schemaQueries.selectAccounts().executeAsList().size, counts.accounts)
        assertEquals(d.db.schemaQueries.recentEntries(1).executeAsList().single().occurred_at, counts.lastEntryAt)
    }

    @Test
    fun aSealedBackupAsksForThePassphraseAndOpensWithIt() {
        val vault = MapVault(); val d = live(vault)
        val bytes = backup(d, vault, pass)
        assertIs<RestoreTrial.Opened.NeedsPassphrase>(open(bytes, null))
        val opened = assertIs<RestoreTrial.Opened.Ready>(open(bytes, pass))
        assertEquals(2, opened.files); assertEquals(0, opened.filesBad)
        assertEquals(d.entries(), RestoreTrial.restoreInto(scratch(), opened.data) { 9_000L }.entries)
    }

    @Test
    fun aWrongPassphraseIsSaidPlainly() {
        val vault = MapVault(); val d = live(vault)
        val e = assertFailsWith<BackupException> { open(backup(d, vault, pass), "not the passphrase") }
        assertEquals("The passphrase is wrong, or the file is damaged.", e.message)
    }

    @Test
    fun aCorruptOrCutFileIsSaidPlainly() {
        val vault = MapVault(); val d = live(vault)
        assertFailsWith<BackupException> { open(Random(1).nextBytes(5_000), null) }
        assertFailsWith<BackupException> { open(ByteArray(0), null) }
        val plain = backup(d, vault, null)
        assertFailsWith<BackupException>("cut inside the entries") { open(plain.copyOf(400), null) }
        val sealed = backup(d, vault, pass)
        assertFailsWith<BackupException>("sealed, cut inside the entries") { open(sealed.copyOf(300), pass) }
    }

    @Test
    fun aDamagedPhotoIsCountedAndTheEntriesStillOpen() {
        val vault = MapVault(); val d = live(vault)
        val bytes = backup(d, vault, null)
        bytes[bytes.size - 1000] = (bytes[bytes.size - 1000].toInt() xor 0xFF).toByte() // inside the bigger photo, which is stored before the small one
        val opened = assertIs<RestoreTrial.Opened.Ready>(open(bytes, null))
        assertEquals(2, opened.files); assertEquals(1, opened.filesBad)
        assertEquals(d.entries(), RestoreTrial.restoreInto(scratch(), opened.data) { 9_000L }.entries)
    }

    @Test
    fun theOldestSealedKindGoesThroughTheDecryptItIsGiven() {
        val vault = MapVault(); val d = Device("old").also { it.populate() }
        val plain = BackupFile.encode(d.backup.export(), 1L)
        val legacy = "EXPENC1\n".encodeToByteArray() + plain
        val decrypt: (ByteArray, String) -> ByteArray = { b, p -> if (p == pass) b.copyOfRange(8, b.size) else throw BackupException("The passphrase is wrong, or the file is damaged.") }
        assertIs<RestoreTrial.Opened.NeedsPassphrase>(RestoreTrial.open(ByteArraySource(legacy), null, JdkSeal, decrypt))
        assertFailsWith<BackupException> { RestoreTrial.open(ByteArraySource(legacy), "wrong", JdkSeal, decrypt) }
        val opened = assertIs<RestoreTrial.Opened.Ready>(RestoreTrial.open(ByteArraySource(legacy), pass, JdkSeal, decrypt))
        assertEquals(d.entries(), RestoreTrial.restoreInto(scratch(), opened.data) { 9_000L }.entries)
        assertTrue(vault.files.isEmpty())
    }

    @Test
    fun theLiveStoreIsUntouchedByATest() {
        val vault = MapVault(); val d = live(vault)
        val bytes = backup(d, vault, pass)
        val before = snapshot(d)
        val settingsBefore = d.db.schemaQueries.settingsWithPrefix("").executeAsList()
        val filesBefore = vault.files.mapValues { it.value.copyOf() }
        val opened = assertIs<RestoreTrial.Opened.Ready>(open(bytes, pass))
        val target = scratch()
        RestoreTrial.restoreInto(target, opened.data) { 9_000L }
        assertContentEquals(before, snapshot(d), "the live ledger is exactly as it was")
        assertEquals(settingsBefore, d.db.schemaQueries.settingsWithPrefix("").executeAsList(), "and its settings")
        assertEquals(filesBefore.keys, vault.files.keys); filesBefore.forEach { (k, v) -> assertContentEquals(v, vault.files[k]) }
        assertTrue(target.schemaQueries.allAttachmentIds().executeAsList().isEmpty(), "no photo is recorded in the scratch database: none was stored")
    }

    @Test
    fun aDatabaseThatHoldsAnythingIsRefusedAndLeftAsItWas() {
        val vault = MapVault(); val d = live(vault)
        val opened = assertIs<RestoreTrial.Opened.Ready>(open(backup(d, vault, null), null))
        val before = snapshot(d)
        assertFailsWith<IllegalStateException> { RestoreTrial.restoreInto(d.db, opened.data) { 9_000L } }
        assertContentEquals(before, snapshot(d))
        // a database with only an account (the app's first start makes Cash and Bank) is not a scratch one either
        val withAccount = scratch()
        LedgerStore(withAccount, app.expenses.core.domain.sync.HlcClock("x")) { 1L }.saveAccount(app.expenses.core.domain.ledger.Account(app.expenses.core.domain.ledger.AccountId("cash"), "Cash", app.expenses.core.domain.ledger.AccountType.CASH, app.expenses.core.domain.Currency.INR))
        assertFailsWith<IllegalStateException> { RestoreTrial.restoreInto(withAccount, opened.data) { 9_000L } }
    }

    @Test
    fun anEmptyBackupRestoresToNothing() {
        val empty = Device("empty")
        val opened = assertIs<RestoreTrial.Opened.Ready>(open(BackupFile.encode(empty.backup.export(), 1L), null))
        val counts = RestoreTrial.restoreInto(scratch(), opened.data) { 9_000L }
        assertEquals(0L, counts.entries); assertEquals(null, counts.lastEntryAt)
        assertEquals("Opens fine: 0 entries, 0 accounts.", RestoreTrial.summary(counts, 0, 0) { "never" })
    }

    @Test
    fun theSentence() {
        val c = RestoreTrial.Counts(1_240, 6, 1L)
        assertEquals("Opens fine: 1,240 entries, 6 accounts, last entry 21 Nov.", RestoreTrial.summary(c, 0, 0) { "21 Nov" })
        assertEquals("Opens fine: 1 entry, 1 account, last entry 21 Nov. 12 photos and files read back whole.", RestoreTrial.summary(RestoreTrial.Counts(1, 1, 1L), 12, 0) { "21 Nov" })
        assertEquals("Opens fine: 1,00,000 entries, 6 accounts, last entry 21 Nov. 2 of 12 photos and files are damaged or missing; the entries are fine.", RestoreTrial.summary(RestoreTrial.Counts(100_000, 6, 1L), 12, 2) { "21 Nov" })
    }
}

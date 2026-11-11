package app.expenses.core.storage

import app.expenses.core.domain.BalanceFix
import app.expenses.core.domain.BalanceFixEntry
import app.expenses.core.domain.BalanceFixLog
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A fixed balance and its history line in backups: the plain file, `EXPENC2` and `EXPENC3`, old files, and restoring over a phone that has its own. Invented data. */
class BalanceFixBackupTest {
    private val pass = "correct horse battery"

    /** A device with a card (owes 3,000 by its entries, fixed to 5,500 owed), a bank fixed from 25,000 to 24,100, and the account ids the app makes at first start. */
    private fun fixedDevice(node: String = "a"): Device {
        val d = Device(node)
        d.ledger.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, d.inr, openingMinor = 25_000_00))
        d.ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, d.inr))
        d.ledger.saveAccount(Account(AccountId("card-1"), "Axis Card", AccountType.CREDIT_CARD, d.inr))
        d.ledger.save(Expense(EntryId("e1"), 1_000L, AccountId("card-1"), d.m(3_000_00)))
        val fixes = BalanceFixes(d.db, d.ledger)
        fixes.fix("card-1", -5_500_00, d.inr, 1_700_000_000_000)
        fixes.fix("bank", 24_100_00, d.inr, 1_700_000_100_000)
        return d
    }

    private fun payloadText(d: Device) = BackupFile.encode(d.backup.export(), 1L).decodeToString()

    @Test
    fun theExportCarriesTheFixesAndAnEmptyDeviceRestoresTheSameBalancesAndTheSameLines() {
        val a = fixedDevice()
        val data = a.backup.export()
        assertEquals(
            setOf(BackupBalanceFix("card-1", 1_700_000_000_000, -3_000_00, -5_500_00), BackupBalanceFix("bank", 1_700_000_100_000, 25_000_00, 24_100_00)),
            data.balanceFixes.toSet(),
        )
        val b = Device("b")
        b.backup.restore(BackupFile.decode(BackupFile.encode(data, 1L)).second)
        assertEquals(a.ledger.balances(), b.ledger.balances(), "every balance, the fixed openings included")
        assertEquals(-5_500_00L, b.ledger.balances().getValue(AccountId("card-1")).minorUnits)
        assertEquals(a.backup.export(), b.backup.export(), "exporting the restored device gives the same backup")
        assertEquals(BalanceFixes(a.db, a.ledger).allHistory(), BalanceFixes(b.db, b.ledger).allHistory())
        assertEquals(listOf(BalanceFixEntry(1_700_000_000_000, -3_000_00, -5_500_00)), BalanceFixes(b.db, b.ledger).history("card-1"))
    }

    @Test
    fun aBackupWithNoFixesIsExactlyAsItWasBeforeAndAFileFromBeforeRestoresUnchanged() {
        val a = Device("a"); a.populate()
        assertFalse("balanceFixes" in payloadText(a), "no key at all when there is nothing to say: an older reader sees the file it always saw")
        // a file written before the key existed, read now: the section is empty and everything else is as it was
        val old = BackupFile.decode(BackupFile.encode(a.backup.export(), 1L)).second
        assertEquals(emptyList(), old.balanceFixes)
        val b = Device("b")
        b.backup.restore(old)
        assertEquals(a.backup.export(), b.backup.export())
        assertEquals(a.ledger.balances(), b.ledger.balances())
    }

    @Test
    fun aFileWithTheKeyIsReadByAnOlderReaderThatIgnoresUnknownKeys() {
        val text = payloadText(fixedDevice())
        assertTrue("\"balanceFixes\"" in text)
        // an older app's data class has no such field: decoding the same payload while ignoring unknown keys gives back its accounts (what Json { ignoreUnknownKeys } does in BackupFile)
        val older = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<OlderBackupData>(text.substringAfter('\n'))
        assertEquals(setOf("bank", "cash", "card-1"), older.accounts.map { it.id }.toSet())
        assertEquals(-2_500_00L, older.accounts.first { it.id == "card-1" }.openingMinor, "the fixed opening balance is in the file the way every opening balance always was")
    }

    @kotlinx.serialization.Serializable private data class OlderBackupData(val accounts: List<BackupAccount> = emptyList())

    @Test
    fun restoringTwiceAddsNothingAndAnAccountAlreadyHereKeepsItsOwnOpeningBalanceAndHistory() {
        val a = fixedDevice()
        val data = a.backup.export()
        val b = Device("b")
        b.backup.restore(data); val once = b.backup.export(); b.backup.restore(data)
        assertEquals(once, b.backup.export(), "twice is once")
        // a phone that has the same card with its own opening balance, and no fix lines: nothing of the backup's is put on it
        val c = Device("c")
        c.ledger.saveAccount(Account(AccountId("card-1"), "Axis Card", AccountType.CREDIT_CARD, c.inr, openingMinor = -1_000_00))
        c.backup.restore(data)
        assertEquals(-1_000_00L, c.db.schemaQueries.accountById("card-1").executeAsOne().opening_minor, "an opening balance set here is kept")
        assertTrue(BalanceFixes(c.db, c.ledger).history("card-1").isEmpty(), "a line about the backup's balance would be untrue here")
    }

    @Test
    fun aPhoneThatHasOnlyTheAccountsTheAppMakesAtFirstStartTakesTheOpeningBalanceOfTheBackupsAndItsFixLine() {
        val a = fixedDevice()
        val data = a.backup.export()
        // the app's first start: Cash and Bank with nothing in them
        val b = Device("b")
        b.ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, b.inr))
        b.ledger.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, b.inr))
        b.backup.restore(data)
        assertEquals(24_100_00L, b.ledger.balances().getValue(AccountId("bank")).minorUnits, "the bank balance comes back, the fix with it")
        assertEquals(listOf(BalanceFixEntry(1_700_000_100_000, 25_000_00, 24_100_00)), BalanceFixes(b.db, b.ledger).history("bank"))
        assertEquals(a.ledger.balances(), b.ledger.balances())
        b.backup.restore(data)
        assertEquals(a.ledger.balances(), b.ledger.balances(), "a second restore changes nothing")
    }

    @Test
    fun theFirstStartRuleOnlyAppliesToAnUntouchedAccountOnAPhoneWithNoEntries() {
        val data = fixedDevice().backup.export()
        fun phone(setup: Device.() -> Unit): Device = Device("p").also { d ->
            d.ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, d.inr))
            d.ledger.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, d.inr))
            d.setup()
        }
        fun bankOpening(d: Device) = d.db.schemaQueries.accountById("bank").executeAsOne().opening_minor
        // an opening balance of its own
        phone { ledger.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr, openingMinor = 5_00)) }.let { it.backup.restore(data); assertEquals(5_00L, bankOpening(it)) }
        // an entry on this phone already
        phone { ledger.save(Expense(EntryId("mine"), 5L, AccountId("cash"), m(10_00))) }.let { it.backup.restore(data); assertEquals(0L, bankOpening(it)) }
        // renamed or of another kind: not the app's own untouched one
        phone { ledger.saveAccount(Account(AccountId("bank"), "Bank", AccountType.WALLET, inr)) }.let { it.backup.restore(data); assertEquals(0L, bankOpening(it)) }
        // an account that is not one of the two the app makes
        val other = Device("o")
        other.ledger.saveAccount(Account(AccountId("savings"), "Savings", AccountType.BANK, other.inr))
        val with = Device("w").also { it.ledger.saveAccount(Account(AccountId("savings"), "Savings", AccountType.BANK, it.inr, openingMinor = 9_00)) }
        other.backup.restore(with.backup.export())
        assertEquals(0L, other.db.schemaQueries.accountById("savings").executeAsOne().opening_minor, "only cash and bank")
    }

    @Test
    fun anUnreadableOrOversizedListIsCutDownToWhatReadsAndTheNewestTwentyKeptAndLinesOfOtherAccountsAreLeftOut() {
        val a = Device("a")
        a.ledger.saveAccount(Account(AccountId("w"), "Wallet", AccountType.WALLET, a.inr))
        val data = a.backup.export().copy(balanceFixes = (1..25).map { BackupBalanceFix("w", it * 1_000L, it * 10L, it * 10L + 5) } +
            listOf(BackupBalanceFix("w", -5, 1, 2), BackupBalanceFix("w", 9, 7, 7), BackupBalanceFix("w", 9, BalanceFix.LIMIT + 1, 0), BackupBalanceFix("nobody", 5, 1, 2)))
        val b = Device("b")
        b.backup.restore(data)
        val kept = BalanceFixes(b.db, b.ledger).history("w")
        assertEquals((6..25).map { it * 1_000L }, kept.map { it.atMillis }, "the newest twenty of the readable lines, oldest first")
        assertTrue(BalanceFixes(b.db, b.ledger).history("nobody").isEmpty())
        assertNull(b.db.schemaQueries.settingValue(BalanceFixLog.key("nobody")).executeAsOneOrNull())
    }

    @Test
    fun aDeletedAccountsLinesAreNotExportedAndTheHintFlagsAreNeverInABackup() {
        val d = Device("d")
        d.ledger.saveAccount(Account(AccountId("w"), "Wallet", AccountType.WALLET, d.inr))
        d.ledger.saveAccount(Account(AccountId("x"), "Other", AccountType.WALLET, d.inr))
        val fixes = BalanceFixes(d.db, d.ledger)
        fixes.fix("w", 5_00, d.inr, 1L); fixes.fix("x", 6_00, d.inr, 2L)
        d.ledger.deleteAccount(AccountId("x"))
        val data = d.backup.export()
        assertEquals(listOf("w"), data.balanceFixes.map { it.accountId })
        assertTrue(data.settings.keys.none { it.startsWith(BalanceFixes.HINT_PREFIX) || it.startsWith(BalanceFixLog.PREFIX) }, "settings carry neither the lines nor the hint flags")
    }

    // ---- sealed files: the line is inside the payload, so the layers carry it as they carry everything ----

    private fun write(d: Device, seal: SealWith?): ByteArray {
        val data = d.backup.export()
        val out = ByteArraySink()
        BackupTransfer.write(data, emptyList(), 1L, seal, out)
        return out.toByteArray()
    }

    private fun open(bytes: ByteArray, passphrase: String?): BackupData {
        val src = if (BackupSeal.isSealed(bytes)) BackupSeal.source(ByteArraySource(bytes), JdkSeal) { salt, it -> JdkSeal.deriveKey(passphrase!!, salt, it) } else ByteArraySource(bytes)
        return BackupFile.open(src).data
    }

    @Test
    fun aPlainFileAnEXPENC2FileAndAnEXPENC3CopyAllRestoreTheFixedBalances() {
        val a = fixedDevice()
        val plain = write(a, null)
        assertTrue(!BackupSeal.isSealed(plain) && !BackupSeal.isLegacySealed(plain))
        val sealed2 = write(a, SealWith(pass, JdkSeal))
        assertEquals("EXPENC2\n", sealed2.copyOf(8).decodeToString())
        val items = HashMap<String, ByteArray>()
        val store = object : SecretStore {
            override fun put(name: String, secret: ByteArray) { items[name] = secret.copyOf() }
            override fun read(name: String): SecretRead = items[name]?.let { SecretRead.Found(it.copyOf()) } ?: SecretRead.NotFound
            override fun delete(name: String) { items.remove(name) }
        }
        AutoCopyProtection.enable(store, JdkSeal, pass, 100_000)
        val kept = (AutoCopyProtection.plan(true, store, JdkSeal) as AutoCopyProtection.Plan.Sealed).seal
        val sealed3 = write(a, kept)
        assertEquals("EXPENC3\n", sealed3.copyOf(8).decodeToString())
        for ((label, bytes) in listOf("plain" to plain, "EXPENC2" to sealed2, "EXPENC3" to sealed3)) {
            val b = Device("b")
            b.backup.restore(open(bytes, pass))
            assertEquals(a.ledger.balances(), b.ledger.balances(), "$label: balances")
            assertEquals(BalanceFixes(a.db, a.ledger).allHistory(), BalanceFixes(b.db, b.ledger).allHistory(), "$label: history lines")
        }
        // the line is not readable in a sealed file
        assertTrue("balanceFixes" !in sealed2.toString(Charsets.ISO_8859_1) && "balanceFixes" !in sealed3.toString(Charsets.ISO_8859_1))
        assertTrue("balanceFixes" in plain.decodeToString())
    }

    @Test
    fun theLegacySingleBlockLayerWrapsTheSameVersionOneFileSoItCarriesTheLineToo() {
        // EXPENC1 seals the whole plain version 1 file (each platform's own cipher, tested on the device); what it holds is exactly this
        val a = fixedDevice()
        val plainV1 = BackupFile.encode(a.backup.export(), 1L)
        val header = plainV1.decodeToString().substringBefore('\n')
        assertTrue("\"version\":1" in header, header)
        val b = Device("b")
        b.backup.restore(BackupFile.decode(plainV1).second)
        assertEquals(a.ledger.balances(), b.ledger.balances())
    }
}

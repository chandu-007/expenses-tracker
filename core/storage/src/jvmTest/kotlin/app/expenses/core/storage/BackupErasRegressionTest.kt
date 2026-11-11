package app.expenses.core.storage

import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Income
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Backups written by 0.9.0, 0.10.4 and 0.11.0 share one format (no `replaced`, no `balanceFixes`; `expects` from 0.11.0). Hand-made payloads of each era (test resources, invented data) must restore as they
 * always did: the same entries are live, the entry that repeats another's bank reference is skipped, a second restore adds nothing, a re-export carries none of the newer keys, and the three eras can be
 * restored one after another on one phone. A differential test pins the rule that decides which incoming entries are skipped against a plain model of the old rule.
 */
class BackupErasRegressionTest {
    private val inr = Currency.INR
    private val eras = listOf(Era("0.9.0", "e09-", 12), Era("0.10.4", "e10-", 12), Era("0.11.0", "e11-", 13))

    private class Era(val version: String, val prefix: String, val entryCount: Int) {
        val resource = "backup-payload-$version.json"

        /** The entries of the file that are live after a restore on an empty phone: all but the one that repeats the bank reference of `exp1`. */
        val liveIds: Set<String> get() = (listOf("exp1", "cashx", "inc", "trf", "refund", "settle", "friend", "shared", "upi", "inc2", "mail") + (if (version == "0.11.0") listOf("usd") else emptyList())).map { prefix + it }.toSet()
        val duplicate get() = prefix + "dup"
    }

    private fun payloadText(era: Era): String = checkNotNull(javaClass.classLoader.getResourceAsStream(era.resource)) { "missing ${era.resource}" }.readBytes().decodeToString()

    /** The file as a backup writes it: a header line (version 1, with the real size and hash of the payload), a newline, the payload; read back with the real reader. */
    private fun read(era: Era): BackupData {
        val payload = payloadText(era).encodeToByteArray()
        val header = Json.encodeToString(BackupHeader.serializer(), BackupHeader(BackupFile.FORMAT, 1, 1L, payload.size, Sha256.hex(payload)))
        val (h, data) = BackupFile.decode(header.encodeToByteArray() + byteArrayOf('\n'.code.toByte()) + payload)
        assertEquals(1, h.version)
        assertEquals(payload.size, h.payloadBytes)
        return data
    }

    private fun Device.liveIds(): Set<String> = db.schemaQueries.liveEntriesInInsertionOrder().executeAsList().map { it.id }.toSet()

    @Test
    fun eachEraRestoresIntoAFreshPhoneAsItAlwaysDid() {
        for (era in eras) {
            val data = read(era)
            assertEquals(era.entryCount, data.entries.size, era.version)
            assertTrue(data.replaced.isEmpty() && data.balanceFixes.isEmpty(), era.version)
            val phone = Device("fresh_" + era.version)
            val report = phone.backup.restore(data)
            assertEquals(era.liveIds, phone.liveIds(), era.version)
            assertFalse(era.duplicate in phone.liveIds(), era.version)
            // the report counts what the file holds that the phone did not have when the restore began, so the entry that repeats a reference of another entry of the same file is in the count
            assertEquals(era.entryCount, report.added.entries, era.version)
            assertEquals(0, report.alreadyHere.entries, era.version)
            assertEquals(3, report.added.accounts, era.version)
            assertEquals(0, report.replacedNotRestored, era.version)
            assertEquals(0, report.replacedRemoved, era.version)
            // the first holder of the reference is the one that stayed
            assertEquals(era.prefix + "exp1", phone.db.schemaQueries.entryBySourceRef("sms", "REF-${era.version}-1001").executeAsOne().id, era.version)
            assertNull(phone.db.schemaQueries.entryById(era.duplicate).executeAsOneOrNull(), era.version)
        }
    }

    @Test
    fun eachEraKeepsWhatReallyDifferedBetweenThem() {
        val keys = eras.map { era -> read(era).merchantMap.map { it.key }.toSet() }
        assertEquals(setOf("swiggybangaloreka", "amazonpayindia", "bigbasketcom"), keys[0])
        assertEquals(setOf("swiggy", "amazon", "bigbasket"), keys[1])
        assertEquals(keys[1], keys[2])
        for ((i, era) in eras.withIndex()) {
            val data = read(era)
            val phone = Device("differ_" + era.version)
            phone.backup.restore(data)
            val out = phone.backup.export()
            assertEquals(keys[i], out.merchantMap.map { it.key }.toSet(), era.version)
            assertEquals(data.accountLooks.associate { it.accountId to it.last4 }, out.accountLooks.associate { it.accountId to it.last4 }, era.version)
            assertEquals(data.expects.map { it.entryId }, out.expects.map { it.entryId }, era.version)
            assertEquals(data.origins.map { it.entryId }, out.origins.map { it.entryId }, era.version)
            assertEquals(data.bills.map { it.id }, out.bills.map { it.id }, era.version)
            assertEquals(data.savedSearches.map { it.accountId }, out.savedSearches.map { it.accountId }, era.version)
            assertEquals(data.recurring.map { it.kind to it.toAccountId }.toSet(), out.recurring.map { it.kind to it.toAccountId }.toSet(), era.version)
            assertEquals(data.budgets.size, out.budgets.size, era.version)
            assertEquals(data.budgetPeriod, out.budgetPeriod, era.version)
            assertEquals(data.recurringDone.size, out.recurringDone.size, era.version)
            assertEquals(data.templates.map { it.id }, out.templates.map { it.id }, era.version)
        }
        assertEquals("821", read(eras[1]).accountLooks.single { it.accountId == "card" }.last4)
        assertEquals("4821", read(eras[0]).accountLooks.single { it.accountId == "card" }.last4)
        assertTrue(read(eras[0]).expects.isEmpty() && read(eras[1]).expects.isEmpty() && read(eras[2]).expects.isNotEmpty())
    }

    @Test
    fun restoringTheSameFileAgainAddsNothing() {
        for (era in eras) {
            val data = read(era)
            val phone = Device("twice_" + era.version)
            phone.backup.restore(data)
            val before = phone.liveIds()
            val again = phone.backup.restore(data)
            assertEquals(before, phone.liveIds(), era.version)
            assertEquals(0, again.added.entries, era.version)
            assertEquals(era.entryCount, again.alreadyHere.entries, era.version)
            assertEquals(0, again.added.accounts, era.version)
            assertEquals(0, again.added.categories, era.version)
            assertEquals(0, again.added.templates, era.version)
            assertEquals(0, again.added.budgets, era.version)
            assertEquals(0, again.added.recurring, era.version)
            assertEquals(0, again.replacedNotRestored, era.version)
            assertEquals(0, again.replacedRemoved, era.version)
        }
    }

    @Test
    fun aReExportOfARestoredEraCarriesNoRecordOfReplacedEntriesAndNoBalanceFixes() {
        for (era in eras) {
            val phone = Device("export_" + era.version)
            phone.backup.restore(read(era))
            val exported = phone.backup.export()
            assertEquals(era.liveIds, exported.entries.map { it.id }.toSet(), era.version)
            val text = BackupFile.encode(exported, 1L).decodeToString()
            val payload = text.substringAfter('\n')
            assertFalse("\"replaced\"" in payload, era.version)
            assertFalse("\"balanceFixes\"" in payload, era.version)
            assertFalse("\"replaced\"" in text.substringBefore('\n'), era.version)
            // and what it wrote restores into another fresh phone to the same ledger
            val other = Device("export2_" + era.version)
            other.backup.restore(BackupFile.decode(BackupFile.encode(exported, 1L)).second)
            assertEquals(era.liveIds, other.liveIds(), era.version)
            assertEquals(exported.entries.associate { it.id to it.amountMinor }, other.backup.export().entries.associate { it.id to it.amountMinor }, era.version)
        }
    }

    @Test
    fun theThreeErasRestoredOneAfterAnotherOnOnePhoneLoseNothing() {
        val phone = Device("all_eras")
        val expected = HashSet<String>()
        for (era in eras) {
            val report = phone.backup.restore(read(era))
            expected += era.liveIds
            assertEquals(expected, phone.liveIds(), era.version)
            assertEquals(0, report.replacedNotRestored, era.version)
            assertEquals(0, report.replacedRemoved, era.version)
            assertEquals(era.entryCount, report.added.entries, era.version)
        }
        assertEquals(34, phone.liveIds().size)
        // once more, in the other order: nothing is added and nothing goes
        for (era in eras.reversed()) {
            val report = phone.backup.restore(read(era))
            assertEquals(0, report.added.entries, era.version)
            assertEquals(expected, phone.liveIds(), era.version)
        }
        val keys = phone.backup.export().merchantMap.map { it.key }.toSet()
        assertTrue(setOf("swiggybangaloreka", "swiggy", "amazon", "bigbasket").all { it in keys })
    }

    /** The rule as it was before records of replaced entries: one entry at a time, in file order; entries this restore added count. */
    private class OldRule(ids: Set<String>, live: Set<String>, refs: Set<Pair<String, String>>) {
        val ids = ids.toMutableSet()
        val live = live.toMutableSet()
        val refs = refs.toMutableSet()
        var added = 0

        fun restore(entries: List<BackupEntry>) {
            for (e in entries) {
                val ref = e.sourceRef?.let { e.source to it }
                if (e.id in ids || ref != null && ref in refs) continue
                ids += e.id; live += e.id; if (ref != null) refs += ref
                added++
            }
        }
    }

    @Test
    fun withoutRecordsARestoreSkipsExactlyWhatTheOldRuleSkipped() {
        val sources = listOf("sms", "upi")
        val pool = (0 until 15).map { "x$it" }
        for (seed in 1..200) {
            val rnd = Random(seed)
            val phone = Device("diff_$seed")
            phone.ledger.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, inr))
            val ids = HashSet<String>()
            val refs = HashSet<Pair<String, String>>()
            // the phone: some live entries, some deleted, with references from the same small pool the file draws from
            for (id in pool.shuffled(rnd).take(rnd.nextInt(0, 9))) {
                val source = sources[rnd.nextInt(sources.size)]
                val ref = if (rnd.nextInt(3) == 0) null else "r${rnd.nextInt(8)}"
                val pair = ref?.let { source to it }
                if (pair != null && pair in refs) continue
                val amount = Money(rnd.nextLong(100, 100_000), inr)
                val entry = if (rnd.nextBoolean()) Expense(EntryId(id), 1_000L + rnd.nextInt(1000), AccountId("bank"), amount, listOf(CategorySplit(CategoryId("Food"), amount)))
                else Income(EntryId(id), 1_000L + rnd.nextInt(1000), AccountId("bank"), amount)
                phone.ledger.save(entry, EntryDetails(source = source, sourceRef = ref))
                ids += id
                if (pair != null) refs += pair
                if (rnd.nextInt(3) == 0) phone.ledger.delete(EntryId(id))
            }
            val liveBefore = phone.liveIds()
            val incoming = (0 until rnd.nextInt(0, 13)).map { n ->
                val kind = if (rnd.nextBoolean()) "EXPENSE" else "INCOME"
                val amount = rnd.nextLong(100, 100_000)
                BackupEntry(
                    id = pool[rnd.nextInt(pool.size)], kind = kind, occurredAt = 5_000L + n, accountId = "bank", amountMinor = amount, currency = "INR",
                    source = sources[rnd.nextInt(sources.size)], sourceRef = if (rnd.nextInt(3) == 0) null else "r${rnd.nextInt(8)}",
                    splits = if (kind == "EXPENSE") listOf(BackupSplit("Food", amount)) else emptyList(),
                )
            }
            val data = BackupData(
                currencies = listOf(BackupCurrency("INR", 2)),
                accounts = listOf(BackupAccount("bank", "Bank", "BANK", "INR")),
                entries = incoming,
            )
            val oracle = OldRule(ids, liveBefore, refs)
            oracle.restore(incoming)
            val report = phone.backup.restore(data)
            val liveAfter = phone.liveIds()
            assertEquals(oracle.live, liveAfter, "seed $seed")
            assertEquals(oracle.added, liveAfter.size - liveBefore.size, "seed $seed")
            assertEquals(0, report.replacedNotRestored, "seed $seed")
            assertEquals(0, report.replacedRemoved, "seed $seed")
        }
    }
}

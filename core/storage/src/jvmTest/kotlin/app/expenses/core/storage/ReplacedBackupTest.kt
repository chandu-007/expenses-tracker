package app.expenses.core.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.Entry
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Income
import app.expenses.core.domain.ledger.Refund
import app.expenses.core.domain.ledger.Transfer
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The record of replaced entries (docs/BACKUP_FORMAT.md, "Changing an entry's type"): a restore never brings back an entry that a type change replaced, in any order of restores, on a fresh
 * phone or one that already has the newer or the older state; a chain, a deleted replacement, Undo, a pair, a text that made a transfer, files, photos, refunds and things the person built on
 * since are all kept exact. Invented data.
 */
class ReplacedBackupTest {
    private val inr = Currency.INR
    private val amount = 2_000_00L

    private class Phone(val d: Device) {
        val changes = KindChanges(d.db, d.ledger, OriginStore(d.db), ExpectStore(d.db), AttachmentStore(d.db, d.clock) { 5_000L })
        val attachments = AttachmentStore(d.db, d.clock) { 5_000L }
        val q get() = d.db.schemaQueries
        val live get() = q.liveEntriesInInsertionOrder().executeAsList().map { it.id }
        fun row(id: String) = q.entryById(id).executeAsOneOrNull()
        fun export() = d.backup.export()
        fun records() = q.allReplaced().executeAsList().associate { it.entry_id to it.replaced_by }
    }

    private fun phone(): Phone = Phone(Device("p" + System.nanoTime())).also {
        it.d.ledger.saveAccount(Account(AccountId("main"), "Main", AccountType.BANK, inr))
        it.d.ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, inr))
    }

    private fun m(v: Long = amount) = Money(v, inr)
    private fun expense(id: String, at: Long = 1_000) = Expense(EntryId(id), at, AccountId("main"), m(), listOf(CategorySplit(CategoryId("Cash"), m())))
    private fun transfer(id: String, at: Long = 1_000) = Transfer(EntryId(id), at, AccountId("main"), AccountId("cash"), m())
    private fun income(id: String, at: Long = 1_000) = Income(EntryId(id), at, AccountId("main"), m(), CategoryId("income:salary"))
    private fun Phone.save(e: Entry, ref: String? = null) = d.ledger.save(e, EntryDetails(merchant = "ATM", source = "sms", sourceRef = ref))
    private fun Phone.change(from: String, to: Entry) = changes.change(from, to, "ATM", emptyList())

    /** Every non-empty sequence of distinct indices below [n]: the orders in which some of [n] backups can be restored one after the other. */
    private fun orders(n: Int): List<List<Int>> {
        val out = ArrayList<List<Int>>()
        fun grow(prefix: List<Int>) { if (prefix.isNotEmpty()) out += prefix; for (i in 0 until n) if (i !in prefix) grow(prefix + i) }
        grow(emptyList())
        return out
    }

    /** A, replaced by B (a transfer), replaced by C (an income): the backups made before the change, after the first and after the second, and the ids. */
    private fun chain(ref: String?): Triple<Phone, List<BackupData>, List<String>> {
        val p = phone()
        p.save(expense("A"), ref)
        val b0 = p.export()
        p.change("A", transfer("B")); val b1 = p.export()
        p.change("B", income("C")); val b2 = p.export()
        return Triple(p, listOf(b0, b1, b2), listOf("A", "B", "C"))
    }

    @Test
    fun everyOrderOfTheBackupsOfAChainOnAFreshPhoneEndsWithTheLatestEntryAloneAndRestoringAgainChangesNothing() {
        for (ref in listOf(null, "UPI-C")) {
            val (_, backups, ids) = chain(ref)
            assertEquals(1 + 3 + 6 + 0, orders(3).count { it.size <= 3 } - 5, "the orders: 3 singles, 6 pairs, 6 triples")
            for (order in orders(3)) {
                val q = phone()
                for (i in order) q.d.backup.restore(backups[i])
                assertEquals(listOf(ids[order.max()]), q.live, "reference $ref, restored in order $order")
                for (i in order) assertEquals(0, q.d.backup.restore(backups[i]).added.entries, "a second time adds nothing ($order, $i)")
                assertEquals(listOf(ids[order.max()]), q.live, "reference $ref, restored twice in order $order")
                if (ref != null) assertEquals(ref, q.row(ids[order.max()])!!.source_ref, "the live entry holds the bank reference")
                // the records the phone keeps for a backup it passes on
                assertTrue(q.records().keys.none { it in q.live }, "a live entry is never recorded as replaced ($order)")
            }
        }
    }

    @Test
    fun onThePhoneThatMadeTheChangesEveryOrderOfTheBackupsAddsNothingAndKeepsTheLastEntry() {
        for (ref in listOf(null, "UPI-D")) {
            val (p, backups, _) = chain(ref)
            for (order in orders(3)) {
                for (i in order) assertEquals(0, p.d.backup.restore(backups[i]).added.entries)
                assertEquals(listOf("C"), p.live, "reference $ref, order $order")
            }
            assertEquals(mapOf("A" to "B", "B" to "C"), p.records(), "the records are unchanged")
        }
    }

    @Test
    fun theRecordsOfAChainKeepEveryLinkSoALaterBackupStillNamesTheFirstEntry() {
        val (p, backups, _) = chain("UPI-E")
        assertEquals(emptyList(), backups[0].replaced)
        assertEquals(listOf(BackupReplaced("A", "B", "CHANGED")), backups[1].replaced)
        assertEquals(setOf(BackupReplaced("A", "B", "CHANGED"), BackupReplaced("B", "C", "CHANGED")), backups[2].replaced.toSet())
        assertEquals(listOf("C"), backups[2].entries.map { it.id })
        assertEquals("UPI-E", p.row("C")!!.source_ref, "the reference travelled A to B to C")
    }

    @Test
    fun deletingTheReplacementDoesNotLetTheOriginalComeBackFromAnOldBackup() {
        val p = phone(); p.save(expense("A"), "UPI-F")
        val old = p.export()
        p.change("A", transfer("B")); val mid = p.export()
        p.d.ledger.delete(EntryId("B")); val later = p.export()
        assertEquals(emptyList(), later.entries); assertEquals(listOf(BackupReplaced("A", "B", "CHANGED")), later.replaced, "the record outlives the deleted replacement")
        // the newer backup first (or after the one with the replacement), then the old one: the original stays out, and nothing else comes
        for (first in listOf(listOf(later), listOf(mid, later), listOf(later, mid))) {
            val q = phone(); first.forEach { q.d.backup.restore(it) }
            val r = q.d.backup.restore(old)
            assertEquals(1, r.replacedNotRestored, "A is the entry a type change replaced"); assertEquals(0, r.added.entries)
            assertTrue("A" !in q.live, "the original did not come back")
        }
        // chain ending in a deletion: the whole chain is out
        val c = phone(); c.save(expense("A")); val a0 = c.export()
        c.change("A", transfer("B")); val a1 = c.export(); c.change("B", income("C")); c.d.ledger.delete(EntryId("C")); val a2 = c.export()
        val q = phone(); q.d.backup.restore(a2); q.d.backup.restore(a0); q.d.backup.restore(a1)
        assertEquals(emptyList(), q.live)
        // the old backup first and the later one after it: the original is not taken away (nothing says that what replaced it is live: the restore never deletes what it cannot replace)
        val s = phone(); s.d.backup.restore(old)
        val r2 = s.d.backup.restore(later)
        assertEquals(listOf("A"), s.live); assertEquals(0, r2.replacedRemoved); assertNull(s.d.ledger.replaced.by("A"), "and no record is kept of a live entry")
        // Undo of the deletion on the maker: the replacement is live again and the record stays
        p.d.ledger.restore(EntryId("B")); assertEquals("B", p.d.ledger.replaced.by("A"))
    }

    @Test
    fun undoOfAChangeRemovesTheRecordSoRestoringTheOriginalIsLegitimateAgain() {
        val p = phone(); p.save(expense("A"), "UPI-G")
        val old = p.export()
        val c = p.change("A", transfer("B")); val during = p.export()
        assertEquals("B", p.d.ledger.replaced.by("A"))
        p.changes.undo(c)
        assertNull(p.d.ledger.replaced.by("A")); assertEquals(emptyMap(), p.records())
        val ops = p.q.opsSince("", 100_000).executeAsList().filter { it.entity == "entry_replaced" && it.row_id == "A" }
        assertEquals(listOf("B", null), ops.map { it.value_ }, "the record and its removal are two logged changes of one field")
        assertTrue(ops.all { it.field_ == "replaced_by" })
        val after = p.export()
        assertEquals(emptyList(), after.replaced); assertEquals(listOf("A"), after.entries.map { it.id })
        // the backup made after the Undo restores A on a fresh phone
        val fresh = phone(); fresh.d.backup.restore(after); assertEquals(listOf("A"), fresh.live)
        // the stale backup made during the change, given to the phone that undid it: A stays, B (deleted here) stays deleted, no record comes back
        val r = p.d.backup.restore(during)
        assertEquals(listOf("A"), p.live); assertNull(p.d.ledger.replaced.by("A")); assertEquals(0, r.replacedRemoved); assertEquals(0, r.added.entries)
        assertEquals(emptyList(), p.export().replaced)
        // change again, to another entry: the new record, and the stale one does not undo it
        p.change("A", income("C"))
        assertEquals("C", p.d.ledger.replaced.by("A"))
        p.d.backup.restore(during); p.d.backup.restore(old)
        assertEquals(listOf("C"), p.live); assertEquals("C", p.d.ledger.replaced.by("A"))
        // the original of an Undo is back and a backup of it restores onto a phone that had the old state
        val q = phone(); q.d.backup.restore(old); q.d.backup.restore(after); assertEquals(listOf("A"), q.live)
    }

    @Test
    fun undoingTheLatestOfTwoChangesKeepsTheEarlierRecord() {
        val p = phone(); p.save(expense("A"))
        p.change("A", transfer("B")); val c2 = p.change("B", income("C"))
        p.changes.undo(c2)
        assertEquals(mapOf("A" to "B"), p.records()); assertEquals(listOf("B"), p.live)
        assertEquals(listOf(BackupReplaced("A", "B", "CHANGED")), p.export().replaced)
    }

    @Test
    fun aRestoredReplacementCanBeChangedAgainAndItsOwnRecordsStayConsistent() {
        // restored from the newer backup onto a fresh phone: B live, record A to B. The person changes B to C2 here, then restores the oldest backup (A stays out) and the newest (its C is
        // another replacement of the B that this phone has already replaced by C2: it is not added next to it).
        val (_, backups, _) = chain(null)
        val q = phone(); q.d.backup.restore(backups[1])
        val c = q.change("B", income("C2"))
        assertEquals(mapOf("A" to "B", "B" to "C2"), q.records())
        q.d.backup.restore(backups[0]); q.d.backup.restore(backups[2])
        assertEquals(listOf("C2"), q.live, "A is out, B is not brought back, and the newest backup's C is a second replacement of B: held back")
        assertEquals(mapOf("A" to "B", "B" to "C2"), q.records(), "this phone's own record of B wins")
        q.changes.undo(c)
        assertEquals(mapOf("A" to "B"), q.records()); assertEquals(listOf("B"), q.live)
    }

    @Test
    fun aRefundOfAnExpenseDeletedBeforeTheChangeDoesNotMakeAnOlderRestoreFail() {
        val p = phone(); p.save(expense("A"))
        p.d.ledger.save(Refund(EntryId("R"), 1_500, AccountId("main"), m(50_00), EntryId("A")), EntryDetails())
        val old = p.export(); assertEquals(setOf("A", "R"), old.entries.map { it.id }.toSet())
        p.d.ledger.delete(EntryId("R")); p.change("A", transfer("B")); val new = p.export()
        val q = phone(); q.d.backup.restore(new)
        val r = q.d.backup.restore(old) // used to fail on the refund: its expense is not coming
        assertEquals(listOf("B"), q.live); assertEquals(2, r.replacedNotRestored, "the replaced expense and its refund"); assertEquals(0, r.added.entries)
        assertEquals(2, q.d.backup.preview(old).replacedNotRestored)
        // the old backup first: the expense is live with its (restored) refund, the person's own build-up: it stays, and so does the older state
        val s = phone(); s.d.backup.restore(old)
        val r2 = s.d.backup.restore(new)
        assertEquals(setOf("A", "R"), s.live.toSet()); assertEquals(0, r2.replacedRemoved); assertEquals(0, r2.added.entries, "the transfer is held back, so the two are not both live")
        assertNull(s.d.ledger.replaced.by("A"))
    }

    @Test
    fun anEntryThePersonHasBuiltOnSinceIsNeverTakenAway() {
        val (_, old, new) = oldAndNew("UPI-H")
        // money expected back
        val a = phone(); a.d.backup.restore(old); ExpectStore(a.d.db).set("A", "Ravi", null, 50_000)
        val ra = a.d.backup.restore(new)
        assertEquals(listOf("A"), a.live); assertEquals(0, ra.replacedRemoved); assertEquals(0, ra.added.entries)
        // a refund recorded here
        val b = phone(); b.d.backup.restore(old); b.d.ledger.save(Refund(EntryId("R2"), 1_500, AccountId("main"), m(10_00), EntryId("A")), EntryDetails())
        val rb = b.d.backup.restore(new)
        assertEquals(setOf("A", "R2"), b.live.toSet()); assertEquals(0, rb.replacedRemoved)
        // the entry of a repeating item
        val c = phone(); c.d.backup.restore(old); c.q.putDone("rent", 20_000, "POSTED", "A")
        assertEquals(0, c.d.backup.restore(new).replacedRemoved); assertEquals(listOf("A"), c.live)
        // an entry that pays a bill
        val d = phone(); d.d.backup.restore(old)
        d.q.insertBill("bill1", "sms", "", "Power", "ELECTRICITY", "Power", 100, null, "INR", null, null, null, 0, 1, null, "PAID", "A", 1, "k1")
        assertEquals(0, d.d.backup.restore(new).replacedRemoved); assertEquals(listOf("A"), d.live)
    }

    private fun oldAndNew(ref: String?): Triple<Phone, BackupData, BackupData> {
        val p = phone(); p.save(expense("A"), ref)
        val old = p.export()
        p.change("A", transfer("B"))
        return Triple(p, old, p.export())
    }

    @Test
    fun theFilesOfAnEntryThatIsTakenAwayGoToItsReplacement() {
        val (_, old, new) = oldAndNew("UPI-I")
        val q = phone(); q.d.backup.restore(old)
        q.attachments.add("f1", "A", "receipt.jpg", "image/jpeg", 10, "h1"); q.attachments.add("f2", "A", "bill.pdf", "application/pdf", 20, "h2")
        val r = q.d.backup.restore(new)
        assertEquals(1, r.replacedRemoved); assertEquals(listOf("B"), q.live)
        assertEquals(listOf("f1", "f2"), q.attachments.list("B").map { it.id }.sorted(), "the receipts are on the transfer, as a type change moves them")
        assertEquals(emptyList(), q.attachments.list("A"))
        // the move is logged like the one a type change makes
        assertTrue(q.q.opsSince("", 100_000).executeAsList().any { it.entity == "attachment" && it.row_id == "f1" && it.field_ == "entry_id" && it.value_ == "B" })
    }

    @Test
    fun aFileOfTheReplacementThatIsAlreadyAttachedHereIsNotStoredTwice() {
        // the replacement's backup carries the same file ids (a type change moves the attachments): the ones the phone already holds on the removed entry are simply there on the replacement
        val bytes = ByteArray(10) { it.toByte() }; val sha = Sha256.hex(bytes)
        val p = phone(); p.save(expense("A"), "UPI-J"); p.attachments.add("f1", "A", "receipt.jpg", "image/jpeg", 10, sha)
        val vault = MapVault(); vault.files["f1"] = bytes
        val old = p.d.backup.export(vault::size)
        p.change("A", transfer("B"))
        val newData = p.d.backup.export(vault::size)
        assertEquals(listOf("f1"), newData.attachments.map { it.id })
        val q = phone(); q.d.backup.restore(old)
        q.attachments.add("f1", "A", "receipt.jpg", "image/jpeg", 10, sha); q.attachments.add("f9", "A", "x.jpg", "image/jpeg", 1, "h9")
        val plan = q.d.backup.attachmentsPlan(newData)
        assertEquals(0, plan.wanted.size + plan.noEntry, "f1 is attached here already (to the entry that is taken away); nothing to store: $plan"); assertEquals(1, plan.alreadyHere)
        q.d.backup.restore(newData, emptySet())
        assertEquals(listOf("B"), q.live); assertEquals(listOf("f1", "f9"), q.attachments.list("B").map { it.id }.sorted())
    }

    @Test
    fun anArrivingEntryWhoseReferenceAnUnrelatedLiveEntryHoldsIsNotAddedAndTheOriginalStays() {
        val (_, _, new) = oldAndNew("UPI-K")
        val q = phone(); q.save(expense("A")); q.save(expense("Z"), "UPI-K")
        val r = q.d.backup.restore(new)
        assertEquals(setOf("A", "Z"), q.live.toSet(), "nothing says the transfer and the unrelated entry are the same: the unrelated one keeps the reference, the transfer is not added, and A is not removed for nothing")
        assertEquals(0, r.replacedRemoved); assertEquals(0, r.added.entries); assertEquals(1, r.alreadyHere.entries); assertNull(q.d.ledger.replaced.by("A"))
    }

    @Test
    fun aHandMadeFileCannotMakeItsOwnLiveEntriesDisappearAndALoopOfRecordsChangesNothing() {
        val data = BackupData(
            currencies = listOf(BackupCurrency("INR", 2)),
            accounts = listOf(BackupAccount("main", "Main", "BANK", "INR"), BackupAccount("cash", "Cash", "CASH", "INR")),
            entries = listOf(BackupEntry("A", "EXPENSE", 1, "main", amountMinor = amount, currency = "INR", splits = listOf(BackupSplit("Cash", amount)))),
            replaced = listOf(BackupReplaced("A", "B"), BackupReplaced("B", "A"), BackupReplaced("X", "X"), BackupReplaced("", "Y")),
        )
        val q = phone(); val r = q.d.backup.restore(data)
        assertEquals(listOf("A"), q.live, "A is listed live and as replaced in one file: the file's own entry wins"); assertEquals(1, r.added.entries); assertEquals(0, r.replacedNotRestored)
        assertEquals(mapOf("B" to "A"), q.records(), "only a record that makes sense is kept (B replaced by A: A is live, B is not)")
        // a loop between two entries that are live on the phone and not in the file
        val r2 = phone(); r2.save(expense("P")); r2.save(expense("Q"))
        val loop = BackupData(replaced = listOf(BackupReplaced("P", "Q"), BackupReplaced("Q", "P")))
        val rep = r2.d.backup.restore(loop)
        assertEquals(setOf("P", "Q"), r2.live.toSet()); assertEquals(0, rep.replacedRemoved)
    }

    @Test
    fun aRecordAlreadyOnThePhoneWinsOverADifferentOneInTheFile() {
        val p = phone(); p.d.ledger.replaced.mark("A", "B", "CHANGED")
        val file = BackupData(replaced = listOf(BackupReplaced("A", "Z", "CHANGED"), BackupReplaced("N", "M", "MATCHED")))
        p.d.backup.restore(file)
        assertEquals(mapOf("A" to "B", "N" to "M"), p.records())
        assertEquals("MATCHED", p.q.replacedOf("N").executeAsOne().reason)
    }

    @Test
    fun aReasonThisAppDoesNotKnowIsKeptAndAnEmptyOneBecomesChanged() {
        val p = phone()
        p.d.backup.restore(BackupData(replaced = listOf(BackupReplaced("A", "B", "SOMETHING_NEW_AND_VERY_VERY_LONG_INDEED"), BackupReplaced("C", "D", "  "))))
        assertEquals("SOMETHING_NEW_AND_VERY_V", p.q.replacedOf("A").executeAsOne().reason); assertEquals("CHANGED", p.q.replacedOf("C").executeAsOne().reason)
    }

    @Test
    fun theRecordsOfABackupAreAdoptedSoABackupMadeFromARestoredPhoneCarriesThemOn() {
        val (_, backups, _) = chain("UPI-L")
        val q = phone(); q.d.backup.restore(backups[2])
        assertEquals(mapOf("A" to "B", "B" to "C"), q.records())
        val again = q.export()
        assertEquals(setOf(BackupReplaced("A", "B", "CHANGED"), BackupReplaced("B", "C", "CHANGED")), again.replaced.toSet())
        // restored on a third phone with the oldest backup, in the other order, from the second-hand file
        val r = phone(); r.d.backup.restore(backups[0]); r.d.backup.restore(again)
        assertEquals(listOf("C"), r.live)
    }

    @Test
    fun theRecordsStayOutOfABackupOnceTheEntryIsLiveAgainWhateverBroughtItBack() {
        val p = phone(); p.save(expense("A"))
        p.change("A", transfer("B"))
        p.d.ledger.delete(EntryId("B"))
        p.d.ledger.restore(EntryId("A")) // brought back by hand (no Undo of the change involved)
        assertEquals(emptyMap(), p.records()); assertEquals(emptyList(), p.export().replaced)
        assertEquals(setOf("A"), p.live.toSet())
    }

    // ---- the preview says what the restore does ----

    @Test
    fun thePreviewAndTheRestoreAgreeForEveryOrderOfAChain() {
        for (ref in listOf(null, "UPI-M")) {
            val (_, backups, _) = chain(ref)
            for (order in orders(3)) {
                val q = phone()
                for (i in order) {
                    val preview = q.d.backup.preview(backups[i]); val report = q.d.backup.restore(backups[i])
                    assertEquals(preview.added, report.added.copy(attachments = preview.added.attachments), "added, order $order step $i")
                    assertEquals(preview.alreadyHere, report.alreadyHere, "already here, order $order step $i")
                    assertEquals(preview.replacedNotRestored, report.replacedNotRestored); assertEquals(preview.replacedRemoved, report.replacedRemoved)
                }
            }
        }
    }

    // ---- every file layout carries the records ----

    private fun openSealed(bytes: ByteArray, pass: String): BackupData =
        BackupFile.open(BackupSeal.source(ByteArraySource(bytes), JdkSeal) { salt, it -> JdkSeal.deriveKey(pass, salt, it) }).data

    @Test
    fun theRecordsTravelInEveryFileLayoutAndTheRestoredPhoneHasOnlyTheNewEntry() {
        val pass = "correct horse battery"
        val salt = ByteArray(16) { (it * 3 + 1).toByte() }
        val kept = KeptKey(100_000, salt, JdkSeal.deriveKey(pass, salt, 100_000))
        val want = BackupReplaced("A", "B", "CHANGED")
        for (withFiles in listOf(false, true)) {
            val vault = MapVault()
            val p = phone(); p.save(expense("A"), "UPI-N")
            if (withFiles) { vault.files["f1"] = ByteArray(5_000) { it.toByte() }; p.attachments.add("f1", "A", "r.jpg", "image/jpeg", 5_000, Sha256.hex(vault.files.getValue("f1"))) }
            val old = p.d.backup.export(if (withFiles) vault::size else null)
            p.change("A", transfer("B"))
            fun write(seal: SealWith?): ByteArray {
                val data = p.d.backup.export(if (withFiles) vault::size else null)
                val out = ByteArraySink()
                BackupTransfer.write(data, data.attachments.map { a -> BackupFileSource(a) { vault.open(a.id)!! } }, 1L, seal, out)
                return out.toByteArray()
            }
            val plain = write(null)
            assertTrue("\"version\":${if (withFiles) 2 else 1}" in plain.decodeToString(0, 200), "the header says the version it always said")
            assertTrue("\"replaced\":[{\"entryId\":\"A\",\"replacedBy\":\"B\",\"reason\":\"CHANGED\"}]" in plain.decodeToString(), "the record is in the payload text (files $withFiles)")
            if (!withFiles) assertEquals(BackupFile.encode(p.d.backup.export(), 1L).toList(), plain.toList(), "a file without files is the version 1 file")
            // the file of the older ledger has no such key at all: it is what 0.11.0 wrote
            assertFalse("\"replaced\"" in BackupFile.encode(old.copy(attachments = emptyList()), 1L).decodeToString())
            for ((name, bytes) in listOf("plain" to plain, "EXPENC2" to write(SealWith(pass, JdkSeal)), "EXPENC3" to write(SealWith.withKey(kept, JdkSeal)))) {
                val sealed = BackupSeal.isSealed(bytes)
                assertEquals(name != "plain", sealed, name)
                assertEquals(name == "EXPENC3", BackupSeal.isKeyedSealed(bytes), name)
                val data = if (sealed) openSealed(bytes, pass) else BackupFile.open(ByteArraySource(bytes)).data
                assertEquals(listOf(want), data.replaced, "$name, files $withFiles")
                // restored onto a phone that has the old state, and onto one that restores the old state afterwards
                val first = phone(); first.d.backup.restore(old); first.d.backup.restore(data)
                val second = phone(); second.d.backup.restore(data); second.d.backup.restore(old)
                assertEquals(listOf("B"), first.live, "$name, files $withFiles, old then new"); assertEquals(listOf("B"), second.live, "$name, files $withFiles, new then old")
            }
        }
    }

    @Test
    fun theJavaReferenceReadsAFileWithRecordsAndRefusesAContradictoryOne() {
        val javaBin = File(System.getProperty("java.home"), "bin/java").path
        val interop = listOf(File("../../tools/backup_interop/Interop.java"), File("tools/backup_interop/Interop.java")).first { it.exists() }.path
        val (_, _, new) = oldAndNew("UPI-O")
        fun run(vararg args: String): Pair<Int, String> {
            val proc = ProcessBuilder(listOf(javaBin, interop) + args).redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText()
            return proc.waitFor() to out
        }
        val file = File.createTempFile("kotlin-replaced", ".expbak")
        try {
            file.writeBytes(BackupFile.encode(new, 1L))
            val (code, out) = run("verify2", "-", file.path, "replaced=1")
            assertEquals(0, code, out); assertTrue("1 replaced records" in out, out)
            assertNotEquals(0, run("verify2", "-", file.path, "replaced=2").first, "a wrong expected count fails")
            // a record about an entry the same file lists as live: the reference refuses it
            val bad = new.copy(replaced = listOf(BackupReplaced("B", "A")))
            file.writeBytes(BackupFile.encode(bad, 1L))
            val (badCode, badOut) = run("verify2", "-", file.path)
            assertNotEquals(0, badCode, badOut)
        } finally { file.delete() }
    }

    // ---- scale ----

    /** A phone whose clock moves on at every reading, as a real one's does (a fixed one overflows the logical clock's counter after tens of thousands of changes in one millisecond). */
    private class TimedPhone {
        val db = ExpensesDb(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { ExpensesDb.Schema.create(it) })
        private val clock = HlcClock("big")
        private var t = 1_000L
        private val now: () -> Long = { ++t }
        val ledger = LedgerStore(db, clock, now)
        val backup = BackupStore(db, clock, now, ledger, TemplateStore(db, clock, now), RecurringStore(db, clock, now, ledger))
        val live get() = db.schemaQueries.liveEntriesInInsertionOrder().executeAsList().map { it.id }
        init { ledger.saveAccount(Account(AccountId("main"), "Main", AccountType.BANK, Currency.INR)) }
    }

    @Test
    fun aRestoreOf20000EntriesAnd2000RecordsCostsAboutAsMuchAsOneWithoutRecords() {
        fun bigData(withRecords: Boolean): BackupData {
            val entries = (0 until 20_000).map { i ->
                BackupEntry("n$i", "EXPENSE", 1_000L + i, "main", amountMinor = 100L + i, currency = "INR", merchant = "Shop ${i % 97}", source = "sms", sourceRef = if (i % 3 == 0) "ref-$i" else null, splits = listOf(BackupSplit("Food", 100L + i)))
            }
            // 2,000 originals replaced by entries that are in the file (every tenth), 200 of them in chains of two
            val records = if (!withRecords) emptyList() else (0 until 2_000).map { i -> BackupReplaced("old$i", "n${i * 10}", "CHANGED") } + (0 until 200).map { i -> BackupReplaced("older$i", "old$i", "CHANGED") }
            return BackupData(currencies = listOf(BackupCurrency("INR", 2)), accounts = listOf(BackupAccount("main", "Main", "BANK", "INR")), entries = entries, replaced = records)
        }
        fun timed(data: BackupData, prepare: (TimedPhone) -> Unit = {}): Pair<Long, TimedPhone> {
            val q = TimedPhone(); prepare(q)
            val t0 = System.nanoTime(); val preview = q.backup.preview(data); val t1 = System.nanoTime(); q.backup.restore(data); val t2 = System.nanoTime()
            assertEquals(20_000, preview.added.entries + preview.alreadyHere.entries + preview.replacedNotRestored)
            println("  preview ${(t1 - t0) / 1_000_000} ms, restore ${(t2 - t1) / 1_000_000} ms")
            return ((t2 - t1) / 1_000_000) to q
        }
        val plainMs = timed(bigData(false)).first
        // the 2,000 replaced originals are live on the phone and are taken away by the restore, their references handed to the replacements: the most work a plan can have
        val withMs = timed(bigData(true)) { q ->
            for (i in 0 until 2_000) q.ledger.save(Expense(EntryId("old$i"), 500L + i, AccountId("main"), m(500L + i), listOf(CategorySplit(CategoryId("Food"), m(500L + i)))), EntryDetails(source = "sms", sourceRef = if ((i * 10) % 3 == 0) "ref-${i * 10}" else null))
        }
        assertEquals(20_000, withMs.second.live.size, "the 2,000 originals went and the 20,000 arrived")
        assertTrue(withMs.second.live.none { it.startsWith("old") })
        println("restore of 20,000 entries: $plainMs ms without records, ${withMs.first} ms with 2,200 records of which 2,000 take away a live entry")
        assertTrue(withMs.first < plainMs * 3 + 3_000, "records cost little: $plainMs ms without, ${withMs.first} ms with")
        // the same file again onto a phone that has it all: every entry is there already, the 2,200 records are adopted, nothing is removed
        val again = timed(bigData(true)) { q -> q.backup.restore(bigData(true)) }
        assertTrue(again.first < plainMs * 3 + 3_000, "a second restore: ${again.first} ms")
        // the export of 2,000 records
        val maker = TimedPhone()
        for (i in 0 until 2_000) maker.ledger.replaced.mark("o$i", "r$i", "CHANGED")
        val t0 = System.nanoTime(); val exported = maker.backup.export(); val exportMs = (System.nanoTime() - t0) / 1_000_000
        assertEquals(2_000, exported.replaced.size); println("export of 2,000 records: $exportMs ms")
        assertTrue(exportMs < 2_000, "$exportMs ms")
        val bytes = BackupFile.encode(exported, 1L).size
        assertTrue(bytes < 400_000, "2,000 records are well under half a megabyte: $bytes bytes")
    }

    // ---- a model of the whole thing: random lives of a phone, random restores onto fresh phones ----

    @Test
    fun randomLivesAndRestoresNeverDoubleCountNeverLoseAnEntryWithoutARecordAndNeverRemoveAnEntryWithNothingLiveAfterIt() {
        var removedTotal = 0; var notRestoredTotal = 0; var restores = 0
        for (seed in 1..60) {
            val rnd = Random(seed)
            val p = phone()
            var n = 0
            val parent = HashMap<String, String>() // the new entry to the one it replaced (dropped when the change is undone)
            var last: KindChanges.Changed? = null
            val backups = ArrayList<BackupData>()
            fun fresh(kind: Int, id: String): Entry = when (kind) { 0 -> expense(id); 1 -> income(id); else -> transfer(id) }
            fun kindOf(id: String) = p.row(id)!!.kind
            repeat(40) {
                val live = p.live.filter { kindOf(it) in setOf("EXPENSE", "INCOME", "TRANSFER") }
                when (rnd.nextInt(12)) {
                    0, 1 -> { val id = "e${n++}"; p.save(fresh(rnd.nextInt(3), id), if (rnd.nextBoolean()) "ref-$id" else null); last = null }
                    2, 3, 4, 5 -> if (live.isNotEmpty()) {
                        val from = live.random(rnd)
                        val kinds = listOf("EXPENSE", "INCOME", "TRANSFER") - kindOf(from)
                        val to = kinds.random(rnd); val id = "e${n++}"
                        last = p.change(from, fresh(listOf("EXPENSE", "INCOME", "TRANSFER").indexOf(to), id)); parent[id] = from
                    }
                    6 -> if (live.isNotEmpty()) { p.d.ledger.delete(EntryId(live.random(rnd))); last = null }
                    7 -> last?.let { c -> if (p.row(c.newId)?.deleted == 0L) { p.changes.undo(c); parent.remove(c.newId) }; last = null }
                    else -> backups += p.export()
                }
            }
            backups += p.export()
            fun family(id: String): String { var x = id; while (parent[x] != null) x = parent.getValue(x); return x }
            for (round in 0 until 12) {
                val order = List(1 + rnd.nextInt(6)) { rnd.nextInt(backups.size) }
                val q = phone()
                val everLive = HashSet<String>(); val fileRecords = ArrayList<BackupReplaced>()
                for (i in order) {
                    val before = q.live.toSet()
                    val report = q.d.backup.restore(backups[i])
                    removedTotal += report.replacedRemoved; notRestoredTotal += report.replacedNotRestored; restores++
                    backups[i].entries.forEach { everLive += it.id }; fileRecords += backups[i].replaced
                    val now = q.live.toSet()
                    val context = "seed $seed, restores $order, step $i"
                    // never two live entries of one family
                    val byFamily = now.groupBy { family(it) }
                    assertTrue(byFamily.values.all { it.size == 1 }, "two live entries of one family: $byFamily ($context)")
                    // an entry that was live and is not now has something live after it in the chain of records this phone keeps (a removed entry's record is kept, so the chain is whole)
                    val chain = q.records()
                    for (gone in before - now) {
                        var next = chain[gone]; var found = false; val seen = HashSet<String>()
                        while (next != null && seen.add(next)) { if (next in now) { found = true; break }; next = chain[next] }
                        assertTrue(found, "$gone disappeared with nothing live after it ($context); chain $chain; live now $now")
                    }
                    // an entry that a backup carried live and that is not live now has a record (it was replaced), or is the second replacement of an entry whose first replacement this phone knows (a branch: held back)
                    for (id in everLive - now) {
                        val branched = fileRecords.any { it.replacedBy == id && chain[it.entryId].let { known -> known != null && known != id } }
                        val ref = order.firstNotNullOfOrNull { j -> backups[j].entries.firstOrNull { it.id == id }?.sourceRef }
                        // or it is the same bank transaction as a live entry here (the duplicate-reference guard, unchanged)
                        val sameTransaction = ref != null && q.q.entryBySourceRef("sms", ref).executeAsOneOrNull()?.let { it.deleted == 0L } == true
                        if (!(id in chain || branched || sameTransaction)) {
                            val msg = "$id is not live and nothing says why ($context); live now $now; records $chain; row ${q.row(id)?.let { it.deleted to it.source_ref }}; ref $ref held by ${ref?.let { q.q.entryBySourceRef("sms", it).executeAsOneOrNull()?.let { h -> h.id to h.deleted } }}; files ${order.map { j -> backups[j].entries.map { it.id to it.sourceRef } to backups[j].replaced.map { it.entryId + ">" + it.replacedBy } }}"
                            File("/tmp/bt_model_fail.txt").writeText(msg); throw AssertionError(msg)
                        }
                    }
                }
                // doing the same restores again changes nothing
                val after = q.live.toSet()
                for (i in order) q.d.backup.restore(backups[i])
                assertEquals(after, q.live.toSet(), "seed $seed, restores $order: restoring again changed the ledger")
            }
        }
        println("the model: $restores restores, $removedTotal entries taken away, $notRestoredTotal left out")
        assertTrue(removedTotal > 100 && notRestoredTotal > 100, "the model really exercises both rules: $removedTotal taken away, $notRestoredTotal left out")
    }

    // ---- what a restore does to the books ----

    @Test
    fun balancesAfterTheRestoreAreThoseOfThePhoneThatMadeTheChange() {
        val (p, old, new) = oldAndNew("UPI-P")
        for (order in listOf(listOf(old, new), listOf(new, old), listOf(new), listOf(old, new, old))) {
            val q = phone(); order.forEach { q.d.backup.restore(it) }
            assertEquals(p.d.ledger.balances(), q.d.ledger.balances(), "balances after restoring ${order.size} backups")
        }
    }
}

package app.expenses.core.storage

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The primitives the sealed layout needs, from the JDK (the apps use javax.crypto on Android and CryptoKit on iPhone). */
object JdkSeal : SealPrimitives {
    override fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)).encoded

    override fun seal(key: ByteArray, nonce: ByteArray, aad: ByteArray, plain: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce)); updateAAD(aad) }.doFinal(plain)

    override fun unseal(key: ByteArray, nonce: ByteArray, aad: ByteArray, sealed: ByteArray): ByteArray? = try {
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce)); updateAAD(aad) }.doFinal(sealed)
    } catch (e: java.security.GeneralSecurityException) { null }

    override fun randomBytes(count: Int) = ByteArray(count).also { SecureRandom().nextBytes(it) }
}

/** Attachment files kept in memory. */
class MapVault : FileVault {
    val files = HashMap<String, ByteArray>()
    override fun size(id: String) = files[id]?.size?.toLong() ?: -1L
    override fun open(id: String): ByteSource? = files[id]?.let { ByteArraySource(it) }
    override fun create(id: String): ByteSink {
        val out = ByteArraySink()
        return object : ByteSink {
            override fun write(buf: ByteArray, off: Int, len: Int) = out.write(buf, off, len)
            override fun close() { files[id] = out.toByteArray() }
        }
    }
    override fun delete(id: String) { files.remove(id) }
}

class BackupFilesTest {
    private val pass = "correct horse battery"
    private val rnd = Random(7)

    /** Entries from [Device.populate], with files attached: a boundary-sized one, a tiny one, a bigger one, over three entries. */
    private fun deviceWithFiles(vault: MapVault, node: String = "a"): Device {
        val d = Device(node)
        d.populate()
        val att = AttachmentStore(d.db, d.clock) { 2L }
        fun attach(id: String, entry: String, name: String, mime: String, size: Int) {
            val bytes = rnd.nextBytes(size)
            vault.files[id] = bytes
            att.add(id, entry, name, mime, size.toLong(), Sha256.hex(bytes))
        }
        attach("a1", "coffee", "receipt.jpg", "image/jpeg", 100_000)
        attach("a2", "coffee", "bill.pdf", "application/pdf", 64 * 1024)
        attach("a3", "dinner", "note.txt", "text/plain", 1)
        attach("a4", "split", "warranty.png", "image/png", 200_001)
        return d
    }

    private class Written(val bytes: ByteArray, val saved: SavedBackup)

    private fun write(d: Device, vault: MapVault, passphrase: String?): Written {
        val data = d.backup.export(vault::size)
        val out = ByteArraySink()
        val saved = BackupTransfer.write(data, data.attachments.map { a -> BackupFileSource(a) { vault.open(a.id)!! } }, 1L, passphrase?.let { SealWith(it, JdkSeal) }, out)
        return Written(out.toByteArray(), saved)
    }

    private class Restored(val report: RestoreReport, val files: FilesRestored)

    /** What the app does with a backup file: open, work out what to take, put files in, record them. */
    private fun restore(into: Device, vault: MapVault, bytes: ByteArray, passphrase: String?): Restored {
        val plain = if (BackupSeal.isSealed(bytes)) BackupSeal.source(ByteArraySource(bytes), JdkSeal) { salt, it -> JdkSeal.deriveKey(passphrase!!, salt, it) } else ByteArraySource(bytes)
        val reader = BackupFile.open(plain)
        val wanted = into.backup.attachmentsWanted(reader.data)
        val files = BackupTransfer.storeFiles(reader, wanted, vault)
        val report = into.backup.restore(reader.data, files.stored)
        (files.stored - report.attached).forEach(vault::delete)
        return Restored(report, files)
    }

    private fun ids(d: Device) = d.db.schemaQueries.allAttachmentIds().executeAsList().sorted()

    @Test
    fun aBackupWithFilesRestoresThemByteForByteUnderTheirOwnIdsAndEntries() {
        val av = MapVault(); val a = deviceWithFiles(av)
        val w = write(a, av, null)
        val b = Device("b"); val bv = MapVault()
        val r = restore(b, bv, w.bytes, null)
        val att = AttachmentStore(b.db, b.clock) { 3L }
        assertEquals(4, r.report.added.attachments); assertEquals(0, r.files.damaged + r.files.missing + r.files.notStored)
        assertEquals(listOf("a1", "a2", "a3", "a4"), ids(b))
        assertEquals(av.files.keys, bv.files.keys)
        for ((id, bytes) in av.files) assertEquals(bytes.toList(), bv.files.getValue(id).toList(), "file $id is identical")
        assertEquals(listOf("a1", "a2"), att.list("coffee").map { it.id }.sorted())
        att.get("a2")!!.let { assertEquals("bill.pdf", it.name); assertEquals("application/pdf", it.mime); assertEquals(65_536L, it.sizeBytes); assertEquals(Sha256.hex(av.files.getValue("a2")), it.sha256) }
        assertEquals(1, att.list("dinner").size)
        // the entries came back as before
        assertEquals(a.entries(), b.entries())
    }

    @Test
    fun anEncryptedBackupWithFilesRestoresWithThePassphraseAndShowsNothingWithout() {
        val av = MapVault(); val a = deviceWithFiles(av)
        val w = write(a, av, pass)
        assertTrue(BackupSeal.isSealed(w.bytes))
        val text = w.bytes.toString(Charsets.ISO_8859_1)
        assertTrue("Cafe" !in text && "receipt.jpg" !in text && "expenses-backup" !in text, "neither the entries nor the file names are readable")
        val first = av.files.getValue("a1").copyOfRange(0, 64)
        assertTrue(indexOf(w.bytes, first) < 0, "the bytes of a photo are not readable in the file")
        assertTrue(assertFailsWith<BackupException> { restore(Device("x"), MapVault(), w.bytes, "wrong passphrase!") }.message!!.contains("passphrase is wrong"))
        val b = Device("b"); val bv = MapVault()
        val r = restore(b, bv, w.bytes, pass)
        assertEquals(4, r.report.added.attachments)
        for ((id, bytes) in av.files) assertEquals(bytes.toList(), bv.files.getValue(id).toList())
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..hay.size - needle.size) { for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer; return i }
        return -1
    }

    @Test
    fun aBackupWithoutFilesIsExactlyTheOldVersionOneFileAndAnOldFileStillRestores() {
        val av = MapVault(); val a = deviceWithFiles(av)
        val without = BackupFile.encode(a.backup.export(), 1L)
        val sink = ByteArraySink()
        BackupFile.write(a.backup.export(), emptyList(), 1L, sink)
        assertEquals(without.toList(), sink.toByteArray().toList(), "no files: written the same way as before")
        val (header, data) = BackupFile.decode(without)
        assertEquals(1, header.version); assertEquals(emptyList(), data.attachments)
        assertTrue("attachments" !in without.decodeToString(), "the payload does not even mention them")
        // a file as the previous release wrote it (no attachments key) restores; the entries come back and no file is expected
        val old = ("""{"format":"expenses-backup","version":1,"createdAt":1,"payloadBytes":PB,"sha256":"SHA"}""")
        val payload = """{"currencies":[{"code":"INR","digits":2}],"accounts":[{"id":"cash","name":"Cash","type":"CASH","currency":"INR"}],"entries":[{"id":"x","kind":"EXPENSE","occurredAt":1,"accountId":"cash","amountMinor":500,"currency":"INR","splits":[{"categoryId":"Food","amountMinor":500}]}]}"""
        val file = (old.replace("PB", payload.encodeToByteArray().size.toString()).replace("SHA", Sha256.hex(payload.encodeToByteArray())) + "\n" + payload).encodeToByteArray()
        val b = Device("b"); val bv = MapVault()
        val r = restore(b, bv, file, null)
        assertEquals(1, r.report.added.entries); assertEquals(0, r.report.added.attachments); assertTrue(bv.files.isEmpty())
        // and the version 1 file with files is refused by a reader that only knows version 1 with the message it already had
        val withFiles = write(a, av, null).bytes
        assertEquals(2, BackupFile.decode(withFiles).first.version)
    }

    @Test
    fun aDamagedFileIsSkippedAndCountedAndTheRestOfTheRestoreCarriesOn() {
        for (passphrase in listOf<String?>(null, pass)) {
            val av = MapVault(); val a = deviceWithFiles(av)
            val w = write(a, av, passphrase)
            val bytes = w.bytes.copyOf()
            // a byte inside the second file's bytes: files are stored one after the other at the end, so work back from the end
            val sizes = a.backup.export(av::size).attachments.map { it.sizeBytes }
            // plain: the files are the last bytes of the file, one after the other; sealed: the last chunk lies inside the last file
            val at = if (passphrase == null) bytes.size - sizes.sum().toInt() + sizes[0].toInt() + 100 else bytes.size - 100
            bytes[at] = (bytes[at] + 1).toByte()
            val b = Device("b"); val bv = MapVault()
            val r = restore(b, bv, bytes, passphrase)
            assertEquals(1, r.files.damaged, "exactly one file damaged ($passphrase): damaged=${r.files.damaged} missing=${r.files.missing} notStored=${r.files.notStored} notWanted=${r.files.notWanted} attached=${r.report.attached} size=${bytes.size} at=$at")
            assertEquals(3, r.report.attached.size)
            assertEquals(r.report.attached, bv.files.keys, "no damaged bytes are kept")
            assertEquals(a.entries(), b.entries(), "all entries restored")
            for (id in r.report.attached) assertEquals(av.files.getValue(id).toList(), bv.files.getValue(id).toList())
        }
    }

    @Test
    fun aCutOffFileRestoresTheEntriesAndTheFilesThatArrivedAndCountsTheRestAsMissing() {
        val av = MapVault(); val a = deviceWithFiles(av)
        val w = write(a, av, null)
        val cut = w.bytes.copyOf(w.bytes.size - 100_000) // the last file (200,001 bytes) is half there
        val b = Device("b"); val bv = MapVault()
        val r = restore(b, bv, cut, null)
        assertEquals(1, r.files.missing); assertEquals(3, r.report.attached.size); assertEquals(a.entries(), b.entries())
        assertTrue("a4" !in bv.files && "a4" !in ids(b))
        // sealed and cut: the same
        val sealed = write(a, av, pass).bytes
        val b2 = Device("b2"); val bv2 = MapVault()
        val r2 = restore(b2, bv2, sealed.copyOf(sealed.size - 100_000), pass)
        assertEquals(a.entries(), b2.entries()); assertTrue(r2.files.missing >= 1 && r2.report.attached.size < 4)
        for (id in r2.report.attached) assertEquals(av.files.getValue(id).toList(), bv2.files.getValue(id).toList())
    }

    @Test
    fun aFileAlreadyHereIsNeverOverwrittenAndRestoringTwiceAddsNothing() {
        val av = MapVault(); val a = deviceWithFiles(av)
        val w = write(a, av, null)
        val b = Device("b"); val bv = MapVault()
        restore(b, bv, w.bytes, null)
        val before = bv.files.mapValues { it.value.copyOf() }
        bv.files["a1"] = byteArrayOf(9, 9, 9) // something else under the same id: a restore must not replace the file of an attachment that exists
        val again = restore(b, bv, w.bytes, null)
        assertEquals(0, again.report.added.attachments); assertEquals(4, again.report.alreadyHere.attachments)
        assertEquals(listOf<Byte>(9, 9, 9), bv.files.getValue("a1").toList())
        assertEquals(before.keys, bv.files.keys)
        assertEquals(listOf("a1", "a2", "a3", "a4"), ids(b))
    }

    @Test
    fun aFileTheOwnerRemovedHereIsNotBroughtBackAndOneOfADeletedEntryIsLeftOut() {
        val av = MapVault(); val a = deviceWithFiles(av)
        val w = write(a, av, null)
        val b = Device("b"); val bv = MapVault()
        restore(b, bv, w.bytes, null)
        val att = AttachmentStore(b.db, b.clock) { 3L }
        att.delete("a3"); bv.delete("a3") // removed by the person
        b.ledger.delete(app.expenses.core.domain.ledger.EntryId("split")) // the entry of a4 deleted here
        att.delete("a4"); bv.delete("a4")
        // delete the rows of a1, a2 as well, so they are missing and wanted
        att.delete("a1"); att.delete("a2"); bv.delete("a1"); bv.delete("a2")
        val again = restore(b, bv, w.bytes, null)
        assertEquals(listOf<String>(), ids(b), "removed files stay removed, and a file of a deleted entry is not attached")
        assertEquals(0, again.report.attached.size)
        assertTrue(bv.files.isEmpty(), "and no stray bytes are left behind")
    }

    @Test
    fun theLimitsHoldOnRestoreAnOversizedFileAnUnsafeIdAnEleventhFileAndADuplicateAreNotTaken() {
        // a hand-made version 2 file: the list says what it says, the bytes follow
        val small = ByteArray(10) { it.toByte() }
        fun att(id: String, entry: String, size: Long, sha: String = Sha256.hex(small)) = BackupAttachment(id, entry, "f.bin", "x/y", size, sha, 5L)
        val many = (0 until 11).map { att("m$it", "e", 10) }
        val list = many + att("../escape", "e2", 10) + att("big", "e2", 16L * 1024 * 1024) + att("dup", "e2", 10) + att("dup", "e2", 10) + att("sha", "e2", 10, "nothex")
        val data = BackupData(
            currencies = listOf(BackupCurrency("INR", 2)), accounts = listOf(BackupAccount("cash", "Cash", "CASH", "INR")),
            entries = listOf("e", "e2").map { BackupEntry(it, "EXPENSE", 1, "cash", amountMinor = 100, currency = "INR", splits = listOf(BackupSplit("Food", 100))) },
        )
        val sources = list.filter { it.sizeBytes == 10L }.map { a -> BackupFileSource(a) { ByteArraySource(small) } }
        val out = ByteArraySink()
        BackupFile.write(data, sources, 1L, out)
        val b = Device("b"); val bv = MapVault()
        val r = restore(b, bv, out.toByteArray(), null)
        val att = AttachmentStore(b.db, b.clock) { 3L }
        assertEquals(10, att.list("e").size, "at most ten per entry")
        assertEquals(listOf("dup"), att.list("e2").map { it.id }, "one copy of a duplicate; no unsafe id, no bad checksum text")
        assertTrue("../escape" !in bv.files && "m10" !in bv.files && "big" !in bv.files)
        assertEquals(11, r.report.attached.size)
    }

    @Test
    fun anEntryThatAlreadyHasFilesOnlyTakesWhatFitsUnderTheLimit() {
        val av = MapVault(); val a = deviceWithFiles(av)
        val w = write(a, av, null) // coffee has two, dinner one, split one
        val b = Device("b"); val bv = MapVault()
        b.populate()
        val att = AttachmentStore(b.db, b.clock) { 2L }
        repeat(9) { att.add("own$it", "coffee", "own$it.txt", "text/plain", 3, "h") } // nine of ten places taken
        val r = restore(b, bv, w.bytes, null)
        assertEquals(setOf("a1", "a3", "a4"), r.report.attached, "of coffee's two only the first fits (the list is in order); the other entries are not affected")
        assertEquals(10, att.list("coffee").size)
        assertTrue("a2" !in bv.files, "and the file that did not fit is not kept")
    }

    @Test
    fun anEntryLosesNothingWhenItsFilesAreNotInTheBackupAndTheTotalsCountOnlyLiveEntries() {
        val av = MapVault(); val a = deviceWithFiles(av)
        val att = AttachmentStore(a.db, a.clock) { 3L }
        val totals = a.backup.attachmentTotals()
        assertEquals(4, totals.count); assertEquals(100_000L + 65_536 + 1 + 200_001, totals.bytes)
        a.ledger.delete(app.expenses.core.domain.ledger.EntryId("split"))
        assertEquals(3, a.backup.attachmentTotals().count, "a deleted entry's file is not in a backup")
        av.delete("a2") // a file missing on this phone is left out of the list instead of failing the backup
        val data = a.backup.export(av::size)
        assertEquals(listOf("a1", "a3"), data.attachments.map { it.id })
        assertEquals(emptyList(), a.backup.export().attachments, "no switch, no files")
        assertEquals(4, ids(a).size, "the records themselves are untouched")
    }

    @Test
    fun aFileThatDoesNotMatchItsOwnRecordIsWrittenAsItIsAndTheBackupIsStillChecked() {
        val av = MapVault(); val a = deviceWithFiles(av)
        av.files["a1"]!![5] = (av.files["a1"]!![5] + 1).toByte() // bit rot on this phone: same size, different bytes
        val data = a.backup.export(av::size)
        val out = ByteArraySink()
        val saved = BackupTransfer.write(data, data.attachments.map { x -> BackupFileSource(x) { av.open(x.id)!! } }, 1L, null, out)
        assertEquals(setOf("a1"), saved.written.mismatched)
        BackupTransfer.check(ByteArraySource(out.toByteArray()), null, saved) // does not fail: the damage was already on the phone
        val b = Device("b"); val bv = MapVault()
        val r = restore(b, bv, out.toByteArray(), null)
        assertEquals(1, r.files.damaged); assertEquals(3, r.report.attached.size)
    }

    @Test
    fun theCheckAfterWritingFindsAFileThatWasNotSavedCorrectly() {
        val av = MapVault(); val a = deviceWithFiles(av)
        for (passphrase in listOf<String?>(null, pass)) {
            val w = write(a, av, passphrase)
            BackupTransfer.check(ByteArraySource(w.bytes), passphrase?.let { SealWith(it, JdkSeal) }, w.saved)
            val bad = w.bytes.copyOf().also { it[it.size - 1000] = (it[it.size - 1000] + 1).toByte() }
            assertFailsWith<BackupException> { BackupTransfer.check(ByteArraySource(bad), passphrase?.let { SealWith(it, JdkSeal) }, w.saved) }
            assertFailsWith<BackupException> { BackupTransfer.check(ByteArraySource(w.bytes.copyOf(w.bytes.size - 7)), passphrase?.let { SealWith(it, JdkSeal) }, w.saved) }
        }
    }

    /** A file store whose files fail in the ways a phone's can: [failOpen] throws when opened, [failAfter] throws after that many bytes, [vanished] is not there, [shorter] ends early. */
    private class FlakyVault(val inner: MapVault, val failOpen: Set<String> = emptySet(), val failAfter: Map<String, Int> = emptyMap(), val vanished: Set<String> = emptySet(), val shorter: Map<String, Int> = emptyMap()) : FileVault by inner {
        override fun open(id: String): ByteSource? {
            if (id in failOpen) throw java.io.IOException("open failed: EIO (Input/output error)")
            if (id in vanished) return null
            val src = inner.open(id) ?: return null
            val limit = failAfter[id]
            val short = shorter[id]
            if (limit == null && short == null) return src
            return object : ByteSource {
                var seen = 0
                override fun read(buf: ByteArray, off: Int, len: Int): Int {
                    if (short != null && seen >= short) return -1
                    if (limit != null && seen >= limit) throw java.io.IOException("read failed: EIO (Input/output error)")
                    val n = src.read(buf, off, minOf(len, (limit ?: short ?: Int.MAX_VALUE) - seen).coerceAtLeast(1))
                    if (n > 0) seen += n
                    return n
                }
                override fun close() = src.close()
            }
        }
    }

    @Test
    fun aFileThatCannotBeReadWhileTheBackupIsWrittenNeverFailsTheBackupAndTheRestIsComplete() {
        val cases = mapOf(
            "cannot be opened" to { v: MapVault -> FlakyVault(v, failOpen = setOf("a2")) },
            "vanished" to { v: MapVault -> FlakyVault(v, vanished = setOf("a2")) },
            "fails in the middle" to { v: MapVault -> FlakyVault(v, failAfter = mapOf("a2" to 1000)) },
            "ends early" to { v: MapVault -> FlakyVault(v, shorter = mapOf("a2" to 5000)) },
        )
        for ((what, makeVault) in cases) {
            for (passphrase in listOf<String?>(null, pass)) {
                val av = MapVault(); val a = deviceWithFiles(av)
                val vault = makeVault(av)
                val data = a.backup.export(av::size) // planned while the file was fine, as the app plans it
                val out = ByteArraySink()
                val saved = BackupTransfer.write(data, data.attachments.map { x -> BackupFileSource(x) { vault.open(x.id) ?: throw BackupException("gone") } }, 1L, passphrase?.let { SealWith(it, JdkSeal) }, out)
                assertEquals(setOf("a2"), saved.written.unreadable, "$what: only that file is reported")
                assertEquals(out.size.toLong(), saved.bytes, "$what: the whole backup was written, zeros standing in for what could not be read")
                BackupTransfer.check(ByteArraySource(out.toByteArray()), passphrase?.let { SealWith(it, JdkSeal) }, saved) // the write is checked as usual: the known-bad file is expected not to match
                val b = Device("b"); val bv = MapVault()
                val r = restore(b, bv, out.toByteArray(), passphrase)
                assertEquals(a.entries(), b.entries(), "$what: every entry is in the backup")
                assertEquals(setOf("a1", "a3", "a4"), r.report.attached, what)
                assertEquals(1, r.files.damaged, "$what: the unreadable file is skipped on restore and counted")
                for (id in r.report.attached) assertEquals(av.files.getValue(id).toList(), bv.files.getValue(id).toList(), "$what: $id")
                assertTrue("a2" !in bv.files)
            }
        }
    }

    @Test
    fun filesThatCannotBeOpenedAreLeftOutOfTheListBeforeTheBackupIsWrittenAndCounted() {
        val av = MapVault(); val a = deviceWithFiles(av)
        val data = a.backup.export(av::size)
        val (kept, left) = BackupTransfer.leaveOutUnreadable(data, FlakyVault(av, failOpen = setOf("a3"), vanished = setOf("a2")))
        assertEquals(2, left); assertEquals(listOf("a1", "a4"), kept.attachments.map { it.id })
        assertEquals(data.entries, kept.entries, "entries are never affected")
        assertEquals(0, BackupTransfer.leaveOutUnreadable(data, av).second)
    }

    @Test
    fun aWriteThatCannotBeFinishedReleasesTheOutputAndAFullDiskIsSaidPlainly() {
        class Tracking(val failOnWrite: Int = Int.MAX_VALUE, val failOnClose: Boolean = false) : ByteSink {
            var writes = 0; var closed = false; var aborted = false
            override fun write(buf: ByteArray, off: Int, len: Int) { if (++writes >= failOnWrite) throw java.io.IOException("write failed: ENOSPC (No space left on device)") }
            override fun close() { if (failOnClose) throw java.io.IOException("close failed: ENOSPC (No space left on device)"); closed = true }
            override fun abort() { aborted = true }
        }
        // the last sealed chunk cannot be written: the stream underneath is aborted, not left open
        val t = Tracking(failOnWrite = 2)
        val sealed = BackupSeal.sink(t, pass, JdkSeal, iterations = 100_000, chunkBytes = 4096)
        sealed.write(ByteArray(100), 0, 100)
        assertFailsWith<java.io.IOException> { sealed.close() }
        assertTrue(t.aborted && !t.closed)
        // a backup whose output fails to close is aborted
        val c = Tracking(failOnClose = true)
        val d = Device("d"); d.populate()
        assertFailsWith<java.io.IOException> { BackupFile.write(d.backup.export(), emptyList(), 1L, c) }
        assertTrue(c.aborted)
        // and the words of a full disk, from either system, become one plain sentence
        val plain = java.io.IOException("write failed: ENOSPC (No space left on device)").asWriteProblem()
        assertTrue(plain is BackupException && plain.message!!.contains("not enough space"), plain.message)
        assertTrue(RuntimeException("wrapper", java.io.IOException("The file couldn\u2019t be saved because there isn\u2019t enough space.")).asWriteProblem() is BackupException)
        val other = java.io.IOException("connection reset")
        assertTrue(other.asWriteProblem() === other, "anything else is left as it is")
    }

    @Test
    fun aSecondRestoreThatStoredTheSameFilesNeverRemovesWhatTheFirstRecorded() {
        val av = MapVault(); val a = deviceWithFiles(av)
        val file = write(a, av, null).bytes
        val b = Device("b"); val bv = MapVault()
        fun open() = BackupFile.open(ByteArraySource(file))
        // both restores work out what is wanted before either has written, and both store the files
        val r1 = open(); val r2 = open()
        val wanted1 = b.backup.attachmentsWanted(r1.data); val wanted2 = b.backup.attachmentsWanted(r2.data)
        val stored1 = BackupTransfer.storeFiles(r1, wanted1, bv).stored
        val stored2 = BackupTransfer.storeFiles(r2, wanted2, bv).stored
        // the first commits, then the second commits (and finds the rows there: it records nothing)
        val rep1 = b.backup.restore(r1.data, stored1)
        val rep2 = b.backup.restore(r2.data, stored2)
        assertEquals(4, rep1.attached.size); assertEquals(0, rep2.attached.size)
        BackupTransfer.discardUnrecorded(stored2, b.backup.recordedAttachments(stored2), bv)
        assertEquals(av.files.keys, bv.files.keys, "every file the first restore recorded is still there")
        for ((id, bytes) in av.files) assertEquals(bytes.toList(), bv.files.getValue(id).toList())
        assertEquals(listOf("a1", "a2", "a3", "a4"), ids(b))
        // and a restore that really failed, with nothing recorded, does remove what it stored
        val c = Device("c"); val cv = MapVault()
        val r3 = open()
        val stored3 = BackupTransfer.storeFiles(r3, c.backup.attachmentsWanted(r3.data), cv).stored
        BackupTransfer.discardUnrecorded(stored3, c.backup.recordedAttachments(stored3), cv)
        assertTrue(cv.files.isEmpty(), "files with no record are removed")
    }

    @Test
    fun onlyOneRestoreRunsAtATimeAndASecondCallerIsToldAtOnce() {
        val flight = SingleFlight()
        val inside = java.util.concurrent.CountDownLatch(1); val release = java.util.concurrent.CountDownLatch(1)
        var first: String? = null
        val t = Thread { first = flight.runOrElse({ "busy" }) { inside.countDown(); release.await(); "ran" } }
        t.start(); inside.await()
        assertTrue(flight.isBusy)
        assertEquals("busy", flight.runOrElse({ "busy" }) { "ran twice" }, "the second caller does not wait and does not run")
        release.countDown(); t.join()
        assertEquals("ran", first); assertTrue(!flight.isBusy, "released afterwards")
        // released after a failure too
        assertFailsWith<IllegalStateException> { flight.runOrElse({ "busy" }) { error("boom") } }
        assertEquals("ran", flight.runOrElse({ "busy" }) { "ran" })
    }

    private fun craft(vararg entries: Pair<BackupAttachment, ByteArray>): ByteArray {
        val data = BackupData(
            currencies = listOf(BackupCurrency("INR", 2)), accounts = listOf(BackupAccount("cash", "Cash", "CASH", "INR")),
            entries = listOf("e", "e2").map { BackupEntry(it, "EXPENSE", 1, "cash", amountMinor = 100, currency = "INR", splits = listOf(BackupSplit("Food", 100))) },
        )
        val out = ByteArraySink()
        BackupFile.write(data, entries.map { (m, bytes) -> BackupFileSource(m) { ByteArraySource(bytes) } }, 1L, out)
        return out.toByteArray()
    }

    private fun meta(id: String, size: Long, bytes: ByteArray, entry: String = "e", mime: String = "x/y") = BackupAttachment(id, entry, "f.bin", mime, size, Sha256.hex(bytes), 5L)

    @Test
    fun aRepeatedIdIsTakenOnceAndNeverRemovesTheFileKeptForTheFirst() {
        val one = ByteArray(10) { it.toByte() }; val two = ByteArray(10) { (it + 50).toByte() }
        // the second of two records with one id is cut off: the first stays complete, with its row
        val whole = craft(meta("x", 10, one) to one, meta("x", 10, two) to two)
        val b = Device("b"); val bv = MapVault()
        val r = restore(b, bv, whole.copyOf(whole.size - 4), null)
        assertEquals(setOf("x"), r.report.attached); assertEquals(one.toList(), bv.files.getValue("x").toList(), "the file kept for the first record is not deleted")
        assertEquals(listOf("x"), ids(b)); assertEquals(0, r.files.missing, "the repeat is not counted as a missing file")
        assertEquals(1, r.files.notWanted)
        // the first record is not valid (size 0) and the second is: the file goes to the second, and the row is the second's
        val c = Device("c"); val cv = MapVault()
        val rc = restore(c, cv, craft(meta("y", 0, ByteArray(0)) to ByteArray(0), meta("y", 10, two) to two), null)
        assertEquals(setOf("y"), rc.report.attached)
        assertEquals(two.toList(), cv.files.getValue("y").toList())
        assertEquals(10L, AttachmentStore(c.db, c.clock) { 3L }.get("y")!!.sizeBytes, "the row says what the file is")
        // two valid records: only the first is kept, the second changes nothing
        val d = Device("d"); val dv = MapVault()
        restore(d, dv, craft(meta("z", 10, one) to one, meta("z", 10, two) to two), null)
        assertEquals(one.toList(), dv.files.getValue("z").toList())
    }

    @Test
    fun idsAreLowerCasedWhereTheyBecomeFileNamesAndOnlyPlainMimeTypesAreKept() {
        val bytes = ByteArray(10) { it.toByte() }
        val file = craft(
            meta("ABC-1", 10, bytes, mime = "Image/JPEG") to bytes,
            meta("def-2", 10, bytes, mime = "text/html\" onload=\"x") to bytes,
            meta("ghi-3", 10, bytes, mime = "") to bytes,
            meta("jkl-4", 10, bytes, mime = "application/vnd.ms-excel+xml") to bytes,
        )
        val b = Device("b"); val bv = MapVault()
        val r = restore(b, bv, file, null)
        val att = AttachmentStore(b.db, b.clock) { 3L }
        assertEquals(setOf("abc-1", "def-2", "ghi-3", "jkl-4"), r.report.attached, "recorded under the lower-case id")
        assertEquals(bv.files.keys, r.report.attached, "and stored under it, so the row and the file agree")
        assertEquals("image/jpeg", att.get("abc-1")!!.mime)
        assertEquals("application/octet-stream", att.get("def-2")!!.mime); assertEquals("application/octet-stream", att.get("ghi-3")!!.mime)
        assertEquals("application/vnd.ms-excel+xml", att.get("jkl-4")!!.mime)
        // an id that differs only in case from one already here is the same attachment: it is left alone and its file is not overwritten
        bv.files["abc-1"] = byteArrayOf(7)
        val again = restore(b, bv, file, null)
        assertEquals(emptySet(), again.report.attached); assertEquals(listOf<Byte>(7), bv.files.getValue("abc-1").toList())
    }

    @Test
    fun theRestorePlanSaysWhyFilesAreLeftAlone() {
        val small = ByteArray(10) { it.toByte() }
        val many = (0 until 11).map { meta("m$it", 10, small) to small }
        val file = craft(*(many + listOf(meta("../escape", 10, small, "e2") to small, meta("dup", 10, small, "e2") to small, meta("dup", 10, small, "e2") to small, meta("nope", 10, small, "gone") to small)).toTypedArray())
        val b = Device("b")
        val plan = b.backup.attachmentsPlan(BackupFile.decode(file).second)
        assertEquals(10 + 1, plan.wanted.size) // ten for e, dup
        assertEquals(1, plan.noRoom); assertEquals(1, plan.invalid); assertEquals(1, plan.repeated); assertEquals(1, plan.noEntry)
        assertEquals(0, plan.alreadyHere + plan.removedHere)
    }

    @Test
    fun aLowerPayloadLimitIsHonouredOnReadAndWriteAndAnOpenOutputIsAbortedWhenItIsExceeded() {
        val d = Device("a"); d.populate()
        val good = BackupFile.encode(d.backup.export(), 1L)
        val before = BackupFile.maxPayloadBytes
        try {
            BackupFile.maxPayloadBytes = 1000
            assertTrue(assertFailsWith<BackupException> { BackupFile.decode(good) }.message!!.contains("more than this phone can open"))
            assertFailsWith<BackupException> { BackupFile.encode(d.backup.export(), 1L) }
            class Out : ByteSink { var wrote = 0; var aborted = false; override fun write(buf: ByteArray, off: Int, len: Int) { wrote += len }; override fun close() {}; override fun abort() { aborted = true } }
            val out = Out()
            assertFailsWith<BackupException> { BackupFile.write(d.backup.export(), emptyList(), 1L, out) }
            assertTrue(out.aborted && out.wrote == 0, "the output that was already open is aborted, nothing was written")
        } finally { BackupFile.maxPayloadBytes = before }
        assertEquals(d.backup.export(), BackupFile.decode(good).second)
    }

    @Test
    fun whenThePhoneRunsOutOfMemoryReadingOrWritingTheBackupSaysSoInsteadOfCrashing() {
        class Boom : ByteSource { override fun read(buf: ByteArray, off: Int, len: Int): Int = throw OutOfMemoryError("test"); override fun close() {} }
        val line = """{"format":"expenses-backup","version":1,"createdAt":1,"payloadBytes":10,"sha256":"x"}""" + "\n"
        val src = object : ByteSource {
            var first = true
            override fun read(buf: ByteArray, off: Int, len: Int): Int { if (first) { first = false; val b = line.encodeToByteArray(); b.copyInto(buf, off); return b.size }; throw OutOfMemoryError("test") }
            override fun close() {}
        }
        assertTrue(assertFailsWith<BackupException> { BackupFile.open(src) }.message!!.contains("too large to open on this phone"))
        assertTrue(assertFailsWith<BackupException> { BackupFile.open(Boom()) }.message!!.contains("too large to open on this phone"))
    }

    @Test
    fun aHeaderThatClaimsMoreThanThePayloadLimitIsRefusedWithoutAskingForTheMemory() {
        val line = """{"format":"expenses-backup","version":1,"createdAt":1,"payloadBytes":${BackupFile.MAX_PAYLOAD_BYTES + 1L},"sha256":"x"}""" + "\n{}"
        assertTrue(assertFailsWith<BackupException> { BackupFile.decode(line.encodeToByteArray()) }.message!!.contains("over 128 MB"))
        val v2 = """{"format":"expenses-backup","version":2,"createdAt":1,"payloadBytes":2000000000,"sha256":"x"}""" + "\n{}"
        assertTrue(assertFailsWith<BackupException> { BackupFile.decode(v2.encodeToByteArray()) }.message!!.contains("not valid"))
        // a payload at the limit is still read (the declared length is checked before any is allocated, and a short file is "incomplete", not an out-of-memory)
        val ok = """{"format":"expenses-backup","version":1,"createdAt":1,"payloadBytes":${BackupFile.MAX_PAYLOAD_BYTES},"sha256":"x"}""" + "\n{}"
        assertTrue(assertFailsWith<BackupException> { BackupFile.decode(ok.encodeToByteArray()) }.message!!.contains("incomplete"))
    }

    // ---- the sealed layout ----

    private fun seal(plain: ByteArray, chunk: Int = 4096, passphrase: String = pass): ByteArray {
        val out = ByteArraySink()
        val sink = BackupSeal.sink(out, passphrase, JdkSeal, iterations = 100_000, chunkBytes = chunk)
        // written in odd pieces, to cross chunk boundaries
        var i = 0
        val r = Random(3)
        while (i < plain.size) { val n = minOf(r.nextInt(1, 3000), plain.size - i); sink.write(plain, i, n); i += n }
        sink.close()
        return out.toByteArray()
    }

    private fun unseal(sealed: ByteArray, passphrase: String = pass, lenient: Boolean = false): ByteArray {
        val src = BackupSeal.source(ByteArraySource(sealed), JdkSeal) { salt, it -> JdkSeal.deriveKey(passphrase, salt, it) }
        src.skipDamage = lenient
        return src.readAll()
    }

    @Test
    fun sealedBytesComeBackExactlyForLengthsAroundTheChunkSize() {
        for (n in listOf(0, 1, 4095, 4096, 4097, 8192, 8193, 40_000)) {
            val plain = rnd.nextBytes(n)
            val sealed = seal(plain)
            assertEquals(32 + n + (n / 4096 + (if (n % 4096 == 0 && n > 0) 0 else 1)) * 16, sealed.size, "length $n: header, bytes, one tag per chunk")
            assertEquals(plain.toList(), unseal(sealed).toList(), "length $n")
        }
        assertNotEquals(seal(ByteArray(10)).toList(), seal(ByteArray(10)).toList(), "a fresh salt every time")
    }

    @Test
    fun aSealedStreamRefusesAWrongPassphraseTruncationReorderingAndAChangedHeader() {
        val plain = rnd.nextBytes(20_000)
        val sealed = seal(plain)
        assertTrue(assertFailsWith<BackupException> { unseal(sealed, "wrong passphrase") }.message!!.contains("passphrase is wrong"))
        // whole chunks dropped from the end: refused (not silently shorter)
        val chunk = 4096 + 16
        assertTrue(assertFailsWith<BackupException> { unseal(sealed.copyOf(32 + chunk * 3)) }.message!!.contains("cut short"))
        assertTrue(assertFailsWith<BackupException> { unseal(sealed.copyOf(sealed.size - 5)) }.message!!.contains("damaged") )
        // two chunks swapped
        val swapped = sealed.copyOf()
        sealed.copyInto(swapped, 32, 32 + chunk, 32 + 2 * chunk); sealed.copyInto(swapped, 32 + chunk, 32, 32 + chunk)
        assertFailsWith<BackupException> { unseal(swapped) }
        // header fields are authenticated: a changed salt, iteration count, or chunk size fails
        for (at in listOf(9, 14, 29)) assertFailsWith<BackupException> { unseal(sealed.copyOf().also { it[at] = (it[at] + 1).toByte() }) }
        // not a sealed file
        assertTrue(assertFailsWith<BackupException> { unseal("hello".encodeToByteArray()) }.message!!.contains("not a backup"))
        // a weakened iteration count is refused outright
        assertEquals("This backup file is damaged.", assertFailsWith<BackupException> { unseal(sealed.copyOf().also { it[8] = 0; it[9] = 0; it[10] = 0; it[11] = 1 }) }.message)
    }

    @Test
    fun whenToldToSkipDamageASealedStreamHandsOverZerosForABadChunkAndCarriesOn() {
        val plain = rnd.nextBytes(20_000)
        val sealed = seal(plain)
        val chunk = 4096 + 16
        val bad = sealed.copyOf().also { it[32 + chunk + 100] = (it[32 + chunk + 100] + 1).toByte() } // inside chunk 1
        assertFailsWith<BackupException> { unseal(bad) }
        val got = unseal(bad, lenient = true)
        assertEquals(plain.size, got.size)
        assertEquals(plain.copyOfRange(0, 4096).toList(), got.copyOfRange(0, 4096).toList())
        assertEquals(List(4096) { 0.toByte() }, got.copyOfRange(4096, 8192).toList())
        assertEquals(plain.copyOfRange(8192, plain.size).toList(), got.copyOfRange(8192, plain.size).toList())
        // damage to the very first chunk is never skipped: nothing after it can be trusted to belong to this passphrase
        assertFailsWith<BackupException> { unseal(sealed.copyOf().also { it[40] = (it[40] + 1).toByte() }, lenient = true) }
    }

    @Test
    fun theIncrementalHashAgreesWithTheJdkWhereverTheInputIsSplit() {
        val md = MessageDigest.getInstance("SHA-256")
        for (n in listOf(0, 1, 55, 56, 57, 63, 64, 65, 119, 120, 1000, 100_000)) {
            val data = rnd.nextBytes(n)
            val d = Sha256.Digest()
            var i = 0
            while (i < n) { val k = minOf(rnd.nextInt(1, 90), n - i); d.update(data, i, k); i += k }
            assertEquals(md.digest(data).joinToString("") { "%02x".format(it) }, d.finishHex(), "length $n")
        }
    }

    // ---- an independent reader and writer (tools/backup_interop/Interop.java, written from docs/BACKUP_FORMAT.md with only the JDK) ----

    private fun repoFile(path: String) = listOf(java.io.File("../../$path"), java.io.File(path)).first { it.exists() }

    private fun pattern(k: Int, size: Int) = ByteArray(size) { (it * 31 + k * 7).toByte() }

    @Test
    fun filesMadeByTheJavaReferenceOpenInTheKotlinReaderPlainAndSealed() {
        for ((name, passphrase) in listOf("reference-v2.expbak" to null, "android-made-v2.expbak" to pass)) {
            val bytes = repoFile("iosApp/Tests/Fixtures/$name").readBytes()
            val plain = if (BackupSeal.isSealed(bytes)) BackupSeal.source(ByteArraySource(bytes), JdkSeal) { salt, it -> JdkSeal.deriveKey(passphrase!!, salt, it) } else ByteArraySource(bytes)
            val reader = BackupFile.open(plain)
            assertEquals(2, reader.header.version); assertEquals(4, reader.data.attachments.size); assertEquals(2, reader.data.entries.size)
            val got = HashMap<String, ByteArray>()
            val results = ArrayList<FileResult>()
            reader.readFiles({ a -> val out = ByteArraySink(); object : ByteSink { override fun write(buf: ByteArray, off: Int, len: Int) = out.write(buf, off, len); override fun close() { got[a.id] = out.toByteArray() } } }, { _, r -> results += r })
            assertEquals(List(4) { FileResult.OK }, results, name)
            reader.data.attachments.forEachIndexed { i, a -> assertEquals(pattern(i + 1, a.sizeBytes.toInt()).toList(), got.getValue(a.id).toList(), "$name ${a.id}") }
        }
    }

    @Test
    fun theJavaReferenceVerifiesWhatKotlinWritesPlainAndSealed() {
        val javaBin = java.io.File(System.getProperty("java.home"), "bin/java").path
        val interop = repoFile("tools/backup_interop/Interop.java").path
        val sizes = intArrayOf(70_000, 65_536, 1, 3_000)
        val vault = MapVault()
        val metas = sizes.mapIndexed { i, size -> pattern(i + 1, size).also { vault.files["att-${i + 1}"] = it }.let { BackupAttachment("att-${i + 1}", "e1", "file${i + 1}.bin", "application/octet-stream", size.toLong(), Sha256.hex(it), 1L) } }
        val data = BackupData(currencies = listOf(BackupCurrency("INR", 2)), accounts = listOf(BackupAccount("cash", "Cash", "CASH", "INR")),
            entries = listOf(BackupEntry("e1", "EXPENSE", 1, "cash", amountMinor = 500, currency = "INR", splits = listOf(BackupSplit("Food", 500)))))
        for (passphrase in listOf<String?>(null, pass)) {
            val file = java.io.File.createTempFile("kotlin-made", ".expbak")
            try {
                file.outputStream().use { os -> BackupTransfer.write(data, metas.map { m -> BackupFileSource(m) { vault.open(m.id)!! } }, 1L, passphrase?.let { SealWith(it, JdkSeal) }, object : ByteSink {
                    override fun write(buf: ByteArray, off: Int, len: Int) = os.write(buf, off, len)
                    override fun close() {}
                }) }
                val p = ProcessBuilder(javaBin, interop, "verify2", passphrase ?: "-", file.path, "pattern").redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText()
                assertEquals(0, p.waitFor(), "the Java reference rejects the file (${if (passphrase == null) "plain" else "sealed"}): $out")
                assertTrue("4 files verified" in out && "sample pattern matches" in out, out)
            } finally { file.delete() }
        }
    }

    // ---- a lot of data in a small amount of memory ----

    /** Files that exist only as a pattern: opening one generates it, creating one only hashes what arrives. Nothing is held, so memory use shows what the backup code holds. */
    private class PatternVault(val sizes: Map<String, Long>) : FileVault {
        val stored = HashMap<String, Pair<Long, String>>()
        override fun size(id: String) = sizes[id] ?: -1L
        override fun open(id: String): ByteSource = object : ByteSource {
            private var at = 0L
            private val total = sizes.getValue(id)
            override fun read(buf: ByteArray, off: Int, len: Int): Int {
                if (at >= total) return -1
                val n = minOf(len.toLong(), total - at).toInt()
                for (i in 0 until n) buf[off + i] = pattern(id, at + i)
                at += n
                return n
            }
            override fun close() {}
        }
        override fun create(id: String): ByteSink = object : ByteSink {
            val h = HashingSink(DiscardSink)
            override fun write(buf: ByteArray, off: Int, len: Int) = h.write(buf, off, len)
            override fun close() { stored[id] = h.count to h.hex() }
        }
        override fun delete(id: String) { stored.remove(id) }
        fun pattern(id: String, at: Long): Byte = ((at * 31 + id.hashCode() + (at shr 8)) and 0xFF).toByte()
    }

    private fun bigBackup(megabytes: Int, passphrase: String?): String {
        val per = 15L * 1024 * 1024 - 12_345 // a little under the limit per file
        val count = ((megabytes.toLong() * 1024 * 1024) / per + 1).toInt()
        val vault = PatternVault((0 until count).associate { "f$it" to per })
        val a = Device("a"); a.populate()
        val ids = listOf("coffee", "dinner", "split", "hotel", "refund", "salary", "atm", "friend", "settle")
        val store = AttachmentStore(a.db, a.clock) { 2L }
        // the checksum of a generated file is computed by reading it once, as the phone did when the file was added
        for (i in 0 until count) {
            val h = Sha256.Digest(); val src = vault.open("f$i"); val buf = ByteArray(COPY_BUFFER)
            while (true) { val n = src.read(buf, 0, buf.size); if (n < 0) break; h.update(buf, 0, n) }
            store.add("f$i", ids[i % ids.size], "f$i.bin", "x/y", per, h.finishHex())
        }
        val file = java.io.File.createTempFile("big-backup", ".expbak")
        try {
            val data = a.backup.export(vault::size)
            val t0 = System.nanoTime()
            val saved = file.outputStream().buffered(COPY_BUFFER).use { os ->
                BackupTransfer.write(data, data.attachments.map { x -> BackupFileSource(x) { vault.open(x.id) } }, 1L, passphrase?.let { SealWith(it, JdkSeal) }, object : ByteSink {
                    override fun write(buf: ByteArray, off: Int, len: Int) = os.write(buf, off, len)
                    override fun close() = os.flush()
                })
            }
            val t1 = System.nanoTime()
            fun source() = object : ByteSource {
                private val i = file.inputStream().buffered(COPY_BUFFER)
                override val length get() = file.length()
                override fun read(buf: ByteArray, off: Int, len: Int) = i.read(buf, off, len)
                override fun close() = i.close()
            }
            BackupTransfer.check(source(), passphrase?.let { SealWith(it, JdkSeal) }, saved)
            val t2 = System.nanoTime()
            val b = Device("b"); val restoreVault = PatternVault(emptyMap())
            val plain = if (passphrase != null) BackupSeal.source(source(), JdkSeal) { salt, it -> JdkSeal.deriveKey(passphrase, salt, it) } else source()
            val reader = BackupFile.open(plain)
            val files = BackupTransfer.storeFiles(reader, b.backup.attachmentsWanted(reader.data), restoreVault)
            b.backup.restore(reader.data, files.stored)
            val t3 = System.nanoTime()
            assertEquals(count, files.stored.size); assertEquals(0, files.damaged + files.missing + files.notStored)
            for (i in 0 until count) assertEquals(per, restoreVault.stored.getValue("f$i").first)
            return "${data.attachments.sumOf { it.sizeBytes } / 1024 / 1024} MB in $count files, backup file ${file.length() / 1024 / 1024} MB" + (if (passphrase != null) " sealed" else "") +
                ": write ${(t1 - t0) / 1_000_000} ms, read-back check ${(t2 - t1) / 1_000_000} ms, restore ${(t3 - t2) / 1_000_000} ms, max heap ${Runtime.getRuntime().maxMemory() / 1024 / 1024} MB"
        } finally {
            file.delete()
        }
    }

    @Test
    fun manyMegabytesOfFilesGoThroughAWriteACheckAndARestoreWithoutBeingHeld() {
        val mb = System.getenv("BACKUP_BIG_MB")?.toIntOrNull() ?: 48
        val plain = bigBackup(mb, null)
        val sealed = bigBackup(maxOf(mb / 2, 16), pass)
        println("BIG BACKUP: $plain")
        println("BIG BACKUP: $sealed")
    }
}

package app.expenses.core.storage

import java.io.File
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Protected automatic copies: the key made from a passphrase and kept in a secret store, the copies sealed with it (`EXPENC3`), and the passphrase alone opening them. */
class AutoCopyProtectionTest {
    private val pass = "correct horse battery"
    private val rounds = 100_000 // the least a reader accepts: the real count (600,000) is the same code, only slower

    /** What a platform's secure storage does, in memory; [failPut] and [failGet] play a store that refuses or can no longer be read. */
    private class FakeStore(var failPut: Boolean = false, var failGet: Boolean = false) : SecretStore {
        val items = HashMap<String, ByteArray>()
        var deleted = 0
        override fun put(name: String, secret: ByteArray) { if (failPut) throw IllegalStateException("the store refuses"); items[name] = secret.copyOf() }
        /** [failGet] is a store that cannot be read right now (the iPhone before its first unlock). */
        override fun read(name: String): SecretRead = if (failGet) SecretRead.Unavailable else items[name]?.let { SecretRead.Found(it.copyOf()) } ?: SecretRead.NotFound
        override fun delete(name: String) { deleted++; items.remove(name) }
    }

    private fun plan(store: FakeStore?, protect: Boolean = true) = AutoCopyProtection.plan(protect, store, JdkSeal)

    private fun sealWith(store: FakeStore) = (plan(store) as AutoCopyProtection.Plan.Sealed).seal

    private class Written(val bytes: ByteArray, val saved: SavedBackup)

    private val rnd = Random(11)

    private fun deviceWithFiles(vault: MapVault): Device {
        val d = Device("a")
        d.populate()
        val att = AttachmentStore(d.db, d.clock) { 2L }
        for ((id, entry, name, size) in listOf(Four("a1", "coffee", "receipt.jpg", 100_000), Four("a2", "dinner", "note.txt", 1))) {
            val bytes = rnd.nextBytes(size)
            vault.files[id] = bytes
            att.add(id, entry, name, "application/octet-stream", size.toLong(), Sha256.hex(bytes))
        }
        return d
    }

    private data class Four(val id: String, val entry: String, val name: String, val size: Int)

    private fun write(d: Device, vault: MapVault, seal: SealWith, withFiles: Boolean = true): Written {
        val data = d.backup.export(if (withFiles) vault::size else null)
        val out = ByteArraySink()
        val saved = BackupTransfer.write(data, data.attachments.map { a -> BackupFileSource(a) { vault.open(a.id)!! } }, 1L, seal, out)
        return Written(out.toByteArray(), saved)
    }

    private fun open(bytes: ByteArray, passphrase: String): BackupReader =
        BackupFile.open(BackupSeal.source(ByteArraySource(bytes), JdkSeal) { salt, it -> JdkSeal.deriveKey(passphrase, salt, it) })

    private fun header(bytes: ByteArray) = bytes.copyOfRange(0, 48)
    private fun masterSalt(bytes: ByteArray) = bytes.copyOfRange(12, 28)
    private fun fileSalt(bytes: ByteArray) = bytes.copyOfRange(28, 44)

    // ---- HMAC (the per-file key) ----

    private fun hex(b: ByteArray) = b.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    @Test
    fun hmacMatchesTheStandardVectorsAndTheJdkForEveryKeyAndMessageSize() {
        // RFC 4231, test cases 1, 2 and 6 (a key longer than the block is hashed first)
        assertEquals("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7", hex(HmacSha256.mac(ByteArray(20) { 0x0b }, "Hi There".encodeToByteArray())))
        assertEquals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843", hex(HmacSha256.mac("Jefe".encodeToByteArray(), "what do ya want for nothing?".encodeToByteArray())))
        assertEquals("60e431591ee0b67f0d8a26aacbf5b77f8e0bc6213728c5140546040f0ee37f54", hex(HmacSha256.mac(ByteArray(131) { 0xaa.toByte() }, "Test Using Larger Than Block-Size Key - Hash Key First".encodeToByteArray())))
        for (keySize in listOf(1, 31, 32, 33, 63, 64, 65, 200)) for (messageSize in listOf(0, 1, 55, 56, 63, 64, 65, 300)) {
            val key = rnd.nextBytes(keySize); val message = rnd.nextBytes(messageSize)
            val jdk = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(message)
            assertEquals(hex(jdk), hex(HmacSha256.mac(key, message)), "key $keySize, message $messageSize")
        }
    }

    // ---- the key and the store ----

    @Test
    fun enablingMakesTheKeyFromThePassphraseKeepsItAndNeverKeepsThePassphrase() {
        val store = FakeStore()
        AutoCopyProtection.enable(store, JdkSeal, pass, rounds)
        val kept = AutoCopyProtection.load(store)!!
        assertEquals(rounds, kept.iterations)
        assertEquals(KeptKey.SALT_BYTES, kept.salt.size)
        assertEquals(hex(JdkSeal.deriveKey(pass, kept.salt, rounds)), hex(kept.key), "the key is what the passphrase gives with that salt")
        assertEquals(1, store.items.size)
        val blob = store.items.getValue(AutoCopyProtection.SECRET)
        assertTrue(indexOf(blob, pass.encodeToByteArray()) < 0, "the passphrase is not in what is kept")
        assertEquals(1 + 4 + 16 + 32 + 4, blob.size)
        // a salt of its own every time
        AutoCopyProtection.enable(store, JdkSeal, pass, rounds)
        assertNotEquals(hex(kept.salt), hex(AutoCopyProtection.load(store)!!.salt))
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..hay.size - needle.size) { for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer; return i }
        return -1
    }

    @Test
    fun aTooShortPassphraseOrAStoreThatRefusesChangesNothing() {
        val store = FakeStore()
        assertFailsWith<BackupException> { AutoCopyProtection.enable(store, JdkSeal, "short", rounds) }
        assertTrue(store.items.isEmpty())
        AutoCopyProtection.enable(store, JdkSeal, pass, rounds)
        val before = store.items.getValue(AutoCopyProtection.SECRET).toList()
        store.failPut = true
        assertTrue(assertFailsWith<BackupException> { AutoCopyProtection.enable(store, JdkSeal, "another passphrase", rounds) }.message!!.contains("Nothing was changed"))
        assertEquals(before, store.items.getValue(AutoCopyProtection.SECRET).toList(), "the key that was kept is still there")
        // a store that says yes but does not give it back is caught too
        val liar = object : SecretStore { override fun put(name: String, secret: ByteArray) {}; override fun read(name: String): SecretRead = SecretRead.NotFound; override fun delete(name: String) {} }
        assertFailsWith<BackupException> { AutoCopyProtection.enable(liar, JdkSeal, pass, rounds) }
    }

    private fun why(p: AutoCopyProtection.Plan) = (p as AutoCopyProtection.Plan.CannotProtect).why

    @Test
    fun protectionOnNeverTurnsIntoAPlainCopyWhenTheKeyCannotBeReadAndSaysWhichOfFourReasonsItIs() {
        val store = FakeStore()
        assertTrue(plan(store, protect = false) is AutoCopyProtection.Plan.Plain, "off: plain, whatever the store holds")
        assertTrue(plan(null, protect = false) is AutoCopyProtection.Plan.Plain)
        assertEquals(AutoCopyProtection.Why.NOT_FOUND, why(plan(store)), "on, no key kept")
        assertEquals(AutoCopyProtection.Why.NO_STORE, why(plan(null)), "on, no secure storage")
        AutoCopyProtection.enable(store, JdkSeal, pass, rounds)
        assertTrue(plan(store) is AutoCopyProtection.Plan.Sealed)
        val kept = store.items.getValue(AutoCopyProtection.SECRET).toList()
        store.failGet = true
        assertEquals(AutoCopyProtection.Why.UNAVAILABLE, why(plan(store)), "on, the key cannot be read right now (the iPhone before its first unlock)")
        store.failGet = false
        assertEquals(kept, store.items.getValue(AutoCopyProtection.SECRET).toList(), "an unreadable moment changes nothing: the key is neither wiped nor recreated")
        assertEquals(0, store.deleted)
        assertTrue(plan(store) is AutoCopyProtection.Plan.Sealed, "and it works again once it can be read")
        val good = store.items.getValue(AutoCopyProtection.SECRET)
        for (bad in listOf(good.copyOf(good.size - 1), good + byteArrayOf(0), good.copyOf().also { it[0] = 1 }, ByteArray(0), good.copyOf().also { it[1] = 0; it[2] = 0; it[3] = 0; it[4] = 1 })) {
            store.items[AutoCopyProtection.SECRET] = bad
            assertEquals(AutoCopyProtection.Why.CORRUPT, why(plan(store)), "a changed or damaged key is not used")
        }
        val throwing = object : SecretStore { override fun put(name: String, secret: ByteArray) {}; override fun read(name: String): SecretRead = throw IllegalStateException("keystore"); override fun delete(name: String) {} }
        assertEquals(AutoCopyProtection.Why.UNAVAILABLE, why(AutoCopyProtection.plan(true, throwing, JdkSeal)), "a store that throws is not a store without a key")
    }

    @Test
    fun everySingleBitFlipOfTheKeyRecordIsRefusedAndTheRecordIsFiftySevenBytes() {
        val store = FakeStore()
        AutoCopyProtection.enable(store, JdkSeal, pass, rounds)
        val good = store.items.getValue(AutoCopyProtection.SECRET)
        assertEquals(57, good.size); assertEquals(2, good[0].toInt())
        assertTrue(KeptKey.decode(good) != null)
        var flips = 0
        for (byte in good.indices) for (bit in 0 until 8) {
            val bad = good.copyOf().also { it[byte] = (it[byte].toInt() xor (1 shl bit)).toByte() }
            assertNull(KeptKey.decode(bad), "byte $byte bit $bit")
            store.items[AutoCopyProtection.SECRET] = bad
            assertEquals(AutoCopyProtection.Why.CORRUPT, why(plan(store)))
            flips++
        }
        assertEquals(456, flips)
        // the old 53-byte record (never shipped) is not read
        assertNull(KeptKey.decode(good.copyOf(53).also { it[0] = 1 }))
    }

    @Test
    fun disablingForgetsTheKeyAndIsHarmlessWhenThereIsNone() {
        val store = FakeStore()
        AutoCopyProtection.enable(store, JdkSeal, pass, rounds)
        AutoCopyProtection.disable(store)
        assertTrue(store.items.isEmpty()); assertNull(AutoCopyProtection.load(store))
        AutoCopyProtection.disable(store); AutoCopyProtection.disable(null)
        val throwing = object : SecretStore { override fun put(name: String, secret: ByteArray) {}; override fun read(name: String): SecretRead = SecretRead.NotFound; override fun delete(name: String) = throw IllegalStateException("keystore") }
        AutoCopyProtection.disable(throwing)
    }

    // ---- the copies ----

    @Test
    fun aCopyWrittenWithTheKeptKeyIsSealedAndOpensWithJustThePassphrase() {
        val vault = MapVault(); val d = deviceWithFiles(vault)
        val store = FakeStore()
        AutoCopyProtection.enable(store, JdkSeal, pass, rounds)
        val w = write(d, vault, sealWith(store))
        assertTrue(BackupSeal.isSealed(w.bytes) && BackupSeal.isKeyedSealed(w.bytes) && !BackupSeal.isLegacySealed(w.bytes))
        assertEquals("EXPENC3\n", w.bytes.copyOf(8).decodeToString())
        val text = w.bytes.toString(Charsets.ISO_8859_1)
        assertTrue("Cafe" !in text && "receipt.jpg" !in text && "expenses-backup" !in text, "neither the entries nor the file names are readable")
        // restored the way "Open backup" does it: the header gives the salt and rounds, the passphrase gives the key
        val reader = open(w.bytes, pass)
        assertEquals(d.backup.export(vault::size).entries.map { it.id }.sorted(), reader.data.entries.map { it.id }.sorted())
        val got = HashMap<String, ByteArray>()
        val results = ArrayList<FileResult>()
        reader.readFiles({ a -> val out = ByteArraySink(); object : ByteSink { override fun write(buf: ByteArray, off: Int, len: Int) = out.write(buf, off, len); override fun close() { got[a.id] = out.toByteArray() } } }, { _, r -> results += r })
        assertEquals(List(2) { FileResult.OK }, results)
        for ((id, bytes) in vault.files) assertEquals(bytes.toList(), got.getValue(id).toList(), "file $id is identical")
        // and the write was read back the way a run checks it, with the kept key and no passphrase
        BackupTransfer.check(ByteArraySource(w.bytes), sealWith(store), w.saved)
    }

    @Test
    fun aCopyWithNoFilesIsSealedToo() {
        val d = Device("a").also { it.populate() }
        val store = FakeStore()
        AutoCopyProtection.enable(store, JdkSeal, pass, rounds)
        val w = write(d, MapVault(), sealWith(store), withFiles = false)
        assertTrue(BackupSeal.isKeyedSealed(w.bytes))
        val reader = open(w.bytes, pass)
        assertEquals(1, reader.header.version)
        assertTrue(reader.data.entries.isNotEmpty())
        BackupTransfer.check(ByteArraySource(w.bytes), sealWith(store), w.saved)
    }

    /** A file that, like a real one, refuses to be read once it has been closed. */
    private class RefusesReadsWhenClosed(bytes: ByteArray) : ByteSource {
        private val inner = ByteArraySource(bytes)
        private var closed = false
        override val length: Long get() = inner.length
        override fun read(buf: ByteArray, off: Int, len: Int): Int { check(!closed) { "The file couldn't be opened because it doesn't exist." }; return inner.read(buf, off, len) }
        override fun close() { closed = true }
    }

    @Test
    fun theReadBackOfACopyWithNoFilesWorksOnAFileThatCannotBeReadAfterItIsClosedAndStillNoticesATruncatedFile() {
        val d = Device("a").also { it.populate() }
        val store = FakeStore()
        AutoCopyProtection.enable(store, JdkSeal, pass, rounds)
        val seal = sealWith(store)
        val w = write(d, MapVault(), seal, withFiles = false)
        BackupTransfer.check(RefusesReadsWhenClosed(w.bytes), seal, w.saved)
        assertFailsWith<BackupException> { BackupTransfer.check(RefusesReadsWhenClosed(w.bytes.copyOf(w.bytes.size - 1)), seal, w.saved) }
    }

    @Test
    fun aWrongPassphraseOrADamagedCopyIsRefused() {
        val vault = MapVault(); val d = deviceWithFiles(vault)
        val store = FakeStore()
        AutoCopyProtection.enable(store, JdkSeal, pass, rounds)
        val w = write(d, vault, sealWith(store))
        assertTrue(assertFailsWith<BackupException> { open(w.bytes, "wrong passphrase!") }.message!!.contains("passphrase is wrong"))
        // any change to the header (the key's salt and rounds, the copy's own salt, the chunk size) is caught: it is authenticated data
        for (at in listOf(9, 13, 28, 44)) {
            val bad = w.bytes.copyOf().also { it[at] = (it[at] + 1).toByte() }
            assertFailsWith<BackupException>("byte $at") { open(bad, pass) }
        }
        // cut short: the first part (entries) still reads, the files that lie across the cut do not come back whole
        val cut = ArrayList<FileResult>()
        open(w.bytes.copyOf(w.bytes.size - 5), pass).readFiles({ DiscardSink }) { _, r -> cut += r }
        assertTrue(cut.any { it != FileResult.OK }, "a copy that was cut off is noticed: $cut")
        assertFailsWith<BackupException>("a header and nothing else") { open(w.bytes.copyOf(48), pass) }
        assertFailsWith<BackupException>("not even a header") { open(w.bytes.copyOf(30), pass) }
        // a kept key that does not belong to the copy is not accepted as having checked it
        val other = FakeStore().also { AutoCopyProtection.enable(it, JdkSeal, pass, rounds) }
        assertFailsWith<BackupException> { BackupTransfer.check(ByteArraySource(w.bytes), sealWith(other), w.saved) }
    }

    @Test
    fun everyCopyCarriesTheSameKeySaltAndRoundsButASaltOfItsOwnSoNoNonceIsUsedTwiceUnderOneKey() {
        val vault = MapVault(); val d = deviceWithFiles(vault)
        val store = FakeStore()
        AutoCopyProtection.enable(store, JdkSeal, pass, rounds)
        val kept = AutoCopyProtection.load(store)!!
        val one = write(d, vault, sealWith(store)); val two = write(d, vault, sealWith(store))
        // the header names the salt and rounds the key was made with, so the passphrase alone finds it again
        for (w in listOf(one, two)) {
            assertEquals(kept.salt.toList(), masterSalt(w.bytes).toList())
            assertEquals(rounds, java.nio.ByteBuffer.wrap(w.bytes, 8, 4).int)
        }
        // each copy has its own salt, so its own key: the same plain bytes seal to different bytes, chunk by chunk
        assertNotEquals(fileSalt(one.bytes).toList(), fileSalt(two.bytes).toList())
        assertNotEquals(one.bytes.copyOfRange(48, 48 + 4096).toList(), two.bytes.copyOfRange(48, 48 + 4096).toList(), "the first chunk of two copies of the same data differs")
        assertNotEquals(hex(BackupSeal.fileKey(kept.key, fileSalt(one.bytes))), hex(BackupSeal.fileKey(kept.key, fileSalt(two.bytes))))
        // both open with the one passphrase
        open(one.bytes, pass); open(two.bytes, pass)
    }

    @Test
    fun aNewPassphraseGivesANewSaltAndKeyAndOldCopiesKeepTheOldPassphrase() {
        val vault = MapVault(); val d = deviceWithFiles(vault)
        val store = FakeStore()
        AutoCopyProtection.enable(store, JdkSeal, pass, rounds)
        val old = write(d, vault, sealWith(store))
        val oldKept = AutoCopyProtection.load(store)!!
        val newPass = "a different passphrase"
        AutoCopyProtection.enable(store, JdkSeal, newPass, rounds)
        val newKept = AutoCopyProtection.load(store)!!
        assertNotEquals(oldKept.salt.toList(), newKept.salt.toList()); assertNotEquals(hex(oldKept.key), hex(newKept.key))
        val fresh = write(d, vault, sealWith(store))
        assertEquals(newKept.salt.toList(), masterSalt(fresh.bytes).toList())
        open(fresh.bytes, newPass)
        assertFailsWith<BackupException> { open(fresh.bytes, pass) }
        open(old.bytes, pass)
        assertFailsWith<BackupException> { open(old.bytes, newPass) }
    }

    @Test
    fun aBackupSealedWithAPassphraseIsStillEXPENC2AndOpensTheSameWay() {
        val vault = MapVault(); val d = deviceWithFiles(vault)
        val manual = write(d, vault, SealWith(pass, JdkSeal))
        assertEquals("EXPENC2\n", manual.bytes.copyOf(8).decodeToString())
        assertTrue(BackupSeal.isSealed(manual.bytes) && !BackupSeal.isKeyedSealed(manual.bytes) && !BackupSeal.isLegacySealed(manual.bytes))
        BackupTransfer.check(ByteArraySource(manual.bytes), SealWith(pass, JdkSeal), manual.saved)
        open(manual.bytes, pass)
        assertTrue(!BackupSeal.isSealed("{\"format\":\"expenses".encodeToByteArray()) && !BackupSeal.isKeyedSealed("EXPENC2\n".encodeToByteArray()))
    }

    @Test
    fun theSettingThatSaysProtectionIsOnIsNotPartOfAnyBackupAndNeitherIsAnythingAboutTheKey() {
        val d = Device("a").also { it.populate() }
        d.templates.putSetting("auto_export_protect", "1"); d.templates.putSetting("auto_export_folder", "content://tree/x")
        val written = d.backup.export(null).settings
        assertTrue("auto_export_protect" !in written && "auto_export_folder" !in written, "settings in a backup: ${written.keys}")
        assertTrue(BackupStore.SETTINGS.none { it.startsWith("auto_") }, "no automatic-copy setting is carried by a backup")
    }

    // ---- other implementations ----

    private fun repoFile(path: String) = listOf(File("../../$path"), File(path)).first { it.exists() }

    private fun pattern(k: Int, size: Int) = ByteArray(size) { (it * 31 + k * 7).toByte() }

    @Test
    fun anAutomaticCopyMadeByTheJavaReferenceFromTheDocumentOpensInTheKotlinReader() {
        val bytes = repoFile("iosApp/Tests/Fixtures/reference-v3.expbak").readBytes()
        assertTrue(BackupSeal.isKeyedSealed(bytes))
        val reader = open(bytes, pass)
        assertEquals(2, reader.header.version); assertEquals(4, reader.data.attachments.size); assertEquals(2, reader.data.entries.size)
        val got = HashMap<String, ByteArray>()
        val results = ArrayList<FileResult>()
        reader.readFiles({ a -> val out = ByteArraySink(); object : ByteSink { override fun write(buf: ByteArray, off: Int, len: Int) = out.write(buf, off, len); override fun close() { got[a.id] = out.toByteArray() } } }, { _, r -> results += r })
        assertEquals(List(4) { FileResult.OK }, results)
        reader.data.attachments.forEachIndexed { i, a -> assertEquals(pattern(i + 1, a.sizeBytes.toInt()).toList(), got.getValue(a.id).toList(), a.id) }
        assertTrue(assertFailsWith<BackupException> { open(bytes, "wrong passphrase!") }.message!!.contains("passphrase is wrong"))
    }

    @Test
    fun theJavaReferenceOpensWhatKotlinWritesWithAKeptKey() {
        val javaBin = File(System.getProperty("java.home"), "bin/java").path
        val interop = repoFile("tools/backup_interop/Interop.java").path
        val sizes = intArrayOf(70_000, 65_536, 1, 3_000)
        val vault = MapVault()
        val metas = sizes.mapIndexed { i, size -> pattern(i + 1, size).also { vault.files["att-${i + 1}"] = it }.let { BackupAttachment("att-${i + 1}", "e1", "file${i + 1}.bin", "application/octet-stream", size.toLong(), Sha256.hex(it), 1L) } }
        val data = BackupData(currencies = listOf(BackupCurrency("INR", 2)), accounts = listOf(BackupAccount("cash", "Cash", "CASH", "INR")),
            entries = listOf(BackupEntry("e1", "EXPENSE", 1, "cash", amountMinor = 500, currency = "INR", splits = listOf(BackupSplit("Food", 500)))))
        val store = FakeStore()
        AutoCopyProtection.enable(store, JdkSeal, pass, 600_000) // the real count: the reference reads what the app really writes
        val file = File.createTempFile("kotlin-auto", ".expbak")
        try {
            file.outputStream().use { os ->
                BackupTransfer.write(data, metas.map { m -> BackupFileSource(m) { vault.open(m.id)!! } }, 1L, sealWith(store), object : ByteSink {
                    override fun write(buf: ByteArray, off: Int, len: Int) = os.write(buf, off, len)
                    override fun close() {}
                })
            }
            val proc = ProcessBuilder(javaBin, interop, "verify2", pass, file.path, "pattern").redirectErrorStream(true).start()
            val out = proc.inputStream.bufferedReader().readText()
            assertEquals(0, proc.waitFor(), out)
            assertTrue(out.contains("4 files verified"), out)
        } finally { file.delete() }
    }

    @Test
    fun theDocumentDescribesTheAutomaticCopyLayoutAndItsLimits() {
        val doc = repoFile("docs/BACKUP_FORMAT.md").readText()
        for (needle in listOf("EXPENC3", "expenses-auto-copy-file-key", "Automatic copies", "Keystore", "Keychain", "unlocked phone", "HMAC-SHA256")) assertTrue(needle in doc, "docs/BACKUP_FORMAT.md does not mention $needle")
        if (!doc.contains("two salts")) fail("the document does not say the header has two salts")
    }
}

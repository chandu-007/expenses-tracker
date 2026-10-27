package app.expenses

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.AutoExportFrequency
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.BackupException
import app.expenses.core.storage.BackupFile
import app.expenses.core.storage.EntryDetails
import app.expenses.core.storage.Sha256
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Backups that carry photos and files, on the real encrypted database and the app's real private file folder. */
@RunWith(AndroidJUnit4::class)
class BackupFilesTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ExpensesApp).container
    private val actions get() = DataActions(context, container)
    private val platform by lazy { AndroidPlatform(context) }
    private val vault get() = platform.attachmentFiles
    private val attachmentDir = File(context.filesDir, "attachments")
    private var scenario: ActivityScenario<MainActivity>? = null
    private val made = mutableListOf<File>()

    private fun resetApp() {
        runBlocking { container.startFresh(); container.write { it.templates.putSetting("first_week_dismissed", "1") } }
        attachmentDir.deleteRecursively()
    }

    @Before fun fresh() = resetApp()
    @After fun cleanUp() { compose.waitForIdle(); scenario?.close(); resetApp(); made.forEach { it.delete() } }

    private fun target(name: String): Uri = Uri.fromFile(File(context.cacheDir, name).also { made += it })
    private fun fileOf(uri: Uri) = File(uri.path!!)
    private fun <T> db(block: (AppContainer.Opened.Ok) -> T): T = runBlocking { container.write(block) }!!

    /** The bytes of a test file: the same for the same [seed], so a restored file can be compared with what was attached. */
    private fun bytesOf(seed: Int, size: Int) = ByteArray(size) { (it * 31 + seed * 7 + (it shr 9)).toByte() }

    /** Entries Cafe and Taxi, with files attached through the app's own file store (the same path an attached photo takes). Returns id to bytes. */
    private fun seed(sizes: List<Int> = listOf(5_000, 100_000, 1_300_000)): Map<String, ByteArray> {
        db { ok ->
            for ((i, m) in listOf("Cafe", "Taxi").withIndex()) ok.store.save(Expense(EntryId("e$i"), 1_000L + i, AccountId("cash"), Money(10_000L + i, Currency.INR)), EntryDetails(merchant = m))
        }
        val out = LinkedHashMap<String, ByteArray>()
        sizes.forEachIndexed { i, size ->
            val bytes = bytesOf(i + 1, size)
            val src = Uri.fromFile(File(context.cacheDir, "src-$i.bin").also { it.writeBytes(bytes); made += it })
            val id = "att-$i"
            val copied = vault.copyIn(id, src.toString())
            db { ok -> ok.attachments.add(id, if (i == 0) "e0" else "e1", "receipt$i.${if (i == 1) "pdf" else "jpg"}", if (i == 1) "application/pdf" else "image/jpeg", copied.sizeBytes, copied.sha256) }
            out[id] = bytes
        }
        return out
    }

    private fun storedBytes(id: String): ByteArray = File(attachmentDir, id).readBytes()

    /** Big files made by streaming a pattern through the store's own writer, so the test never holds one (the test process has a 48 MB heap). Returns id to the SHA-256 of what was written. */
    private fun seedBig(count: Int, size: Int): Map<String, String> {
        db { ok -> ok.store.save(Expense(EntryId("e0"), 1_000L, AccountId("cash"), Money(10_000L, Currency.INR)), EntryDetails(merchant = "Cafe")) }
        val out = LinkedHashMap<String, String>()
        val block = bytesOf(5, 1 shl 16)
        for (n in 0 until count) {
            val id = "att-$n"
            val sink = vault.create(id); val digest = Sha256.Digest()
            var left = size
            while (left > 0) { val k = minOf(left, block.size); block[0] = n.toByte(); digest.update(block, 0, k); sink.write(block, 0, k); left -= k }
            sink.close()
            val hex = digest.finishHex()
            db { ok -> ok.attachments.add(id, "e0", "big$n.bin", "application/octet-stream", size.toLong(), hex) }
            out[id] = hex
        }
        return out
    }

    private fun shaOfStored(id: String): String {
        val d = Sha256.Digest(); val buf = ByteArray(1 shl 16)
        File(attachmentDir, id).inputStream().use { i -> while (true) { val n = i.read(buf); if (n < 0) break; d.update(buf, 0, n) } }
        return d.finishHex()
    }
    private fun rows() = db { ok -> listOf("e0", "e1").flatMap { ok.attachments.list(it) } }.sortedBy { it.id }
    private fun entryCount() = db { it.db.schemaQueries.entryCount().executeAsOne() }
    private fun setting(key: String): String? = runBlocking { container.write { it.templates.setting(key) } }

    // ---- the whole path: backup, wipe, restore ----

    @Test
    fun aBackupWithFilesSurvivesWipingTheAppAndEveryFileComesBackIdenticalAndOpenableThroughTheProvider() {
        val files = seed()
        val uri = target("with-files.expbak")
        val saved = runBlocking { actions.backup(uri, null) }
        assertEquals(3, saved.files.included); assertEquals(files.values.sumOf { it.size }.toLong(), saved.files.includedBytes); assertNotNull(saved.files)
        assertEquals(2, BackupFile.decode(fileOf(uri).readBytes()).first.version, "a backup with files is version 2")
        assertTrue(fileOf(uri).length() > files.values.sumOf { it.size }, "the bytes are in the file")

        resetApp() // what uninstalling does: the database and the private files are gone
        assertEquals(emptySet(), attachmentDir.list().orEmpty().toSet())
        val opened = runBlocking { actions.open(uri, null) } as DataActions.Opened.Ready
        assertEquals(3, opened.report.added.attachments); assertEquals(0L, entryCount(), "opening changes nothing"); assertEquals(emptySet(), attachmentDir.list().orEmpty().toSet())
        val restored = runBlocking { actions.restore(opened) }
        assertEquals(3, restored.report.attached.size); assertEquals(0, restored.files!!.damaged + restored.files!!.missing + restored.files!!.notStored)
        assertEquals(files.keys, attachmentDir.list().orEmpty().toSet(), "the files are back under their own ids")
        for ((id, bytes) in files) {
            assertEquals(bytes.toList(), storedBytes(id).toList(), "$id byte for byte")
            assertEquals(bytes.toList(), context.contentResolver.openInputStream(vault.shareUri(id).let(Uri::parse))!!.use { it.readBytes() }.toList(), "$id opens through the provider, as a viewer reads it")
        }
        val back = rows()
        assertEquals(files.keys.toList(), back.map { it.id })
        assertEquals(listOf("receipt0.jpg", "receipt1.pdf", "receipt2.jpg"), back.map { it.name }); assertEquals("application/pdf", back[1].mime)
        for (a in back) { assertEquals(files.getValue(a.id).size.toLong(), a.sizeBytes); assertEquals(Sha256.hex(files.getValue(a.id)), a.sha256) }
        assertEquals(listOf("att-0"), db { it.attachments.list("e0") }.map { it.id }); assertEquals(2, db { it.attachments.list("e1") }.size)
        assertEquals(emptySet(), db { it.attachments.allIds() } - files.keys, "no record without a file")

        // restoring the same file again adds nothing and replaces nothing
        val again = runBlocking { actions.restore(runBlocking { actions.open(uri, null) } as DataActions.Opened.Ready) }
        assertEquals(0, again.report.attached.size); assertEquals(3, rows().size)
    }

    @Test
    fun aSealedBackupWithFilesNeedsThePassphraseHidesTheFilesAndRestoresThem() {
        val files = seed()
        val uri = target("sealed-files.expbak")
        val saved = runBlocking { actions.backup(uri, "correct horse") }
        assertTrue(saved.encrypted); assertEquals(3, saved.files.included)
        val raw = fileOf(uri).readBytes()
        assertEquals("EXPENC2\n", raw.copyOf(8).decodeToString(), "a backup with files is sealed in pieces (EXPENC2)")
        assertTrue(raw.size > files.values.sumOf { it.size })
        val text = raw.toString(Charsets.ISO_8859_1)
        assertTrue("Cafe" !in text && "receipt" !in text, "neither entries nor file names are readable")
        assertEquals(-1, indexOf(raw, files.getValue("att-1").copyOf(64)), "the bytes of a file are not readable")

        resetApp()
        assertTrue(runBlocking { actions.open(uri, null) } is DataActions.Opened.NeedsPassphrase)
        assertTrue(assertFailsWith<BackupException> { runBlocking { actions.open(uri, "nope nope nope") } }.message!!.contains("passphrase is wrong"))
        val ready = runBlocking { actions.open(uri, "correct horse") } as DataActions.Opened.Ready
        val restored = runBlocking { actions.restore(ready) }
        assertEquals(3, restored.report.attached.size)
        for ((id, bytes) in files) assertEquals(bytes.toList(), storedBytes(id).toList())
        assertEquals(3, rows().size)
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..hay.size - needle.size) { for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer; return i }
        return -1
    }

    @Test
    fun withTheSwitchOffTheBackupHasNoFilesIsTheOldVersionOneFileAndRestoresEntriesWithoutThem() {
        seed()
        db { it.templates.putSetting(BackupKeys.INCLUDE_FILES, "0") }
        for (pass in listOf<String?>(null, "correct horse")) {
            val uri = target("no-files-${pass != null}.expbak")
            val saved = runBlocking { actions.backup(uri, pass) }
            assertEquals(0, saved.files.included); assertEquals(3, saved.files.onPhone)
            assertTrue(saved.files.note!!.contains("switched them off"), saved.files.note)
            val raw = fileOf(uri).readBytes()
            assertTrue(raw.size < 100_000, "no file bytes in it (${raw.size})")
            if (pass == null) { assertEquals(1, BackupFile.decode(raw).first.version); assertEquals(emptyList(), BackupFile.decode(raw).second.attachments) }
            else assertEquals("EXPENC1\n", raw.copyOf(8).decodeToString(), "sealed exactly as before, so every older version of the app opens it")
        }
        val uri = target("no-files-false.expbak")
        resetApp()
        val restored = runBlocking { actions.restore(runBlocking { actions.open(uri, null) } as DataActions.Opened.Ready) }
        assertEquals(null, restored.files, "the backup has no files")
        assertEquals(2L, entryCount()); assertEquals(0, rows().size); assertEquals(emptySet(), attachmentDir.list().orEmpty().toSet())
    }

    @Test
    fun aDamagedFileInTheBackupIsSkippedAndCountedAndTheEntriesAndOtherFilesStillComeBack() {
        val files = seed()
        val uri = target("damaged-file.expbak")
        runBlocking { actions.backup(uri, null) }
        val raw = fileOf(uri).readBytes()
        val sizes = files.values.map { it.size }
        val filesStart = raw.size - sizes.sum()
        // the order is entry, time, id: att-0, att-1, att-2. Damage a byte in the middle of the second file.
        raw[filesStart + sizes[0] + sizes[1] / 2] = (raw[filesStart + sizes[0] + sizes[1] / 2] + 1).toByte()
        fileOf(uri).writeBytes(raw)
        resetApp()
        val restored = runBlocking { actions.restore(runBlocking { actions.open(uri, null) } as DataActions.Opened.Ready) }
        assertEquals(1, restored.files!!.damaged)
        assertEquals(setOf("att-0", "att-2"), restored.report.attached)
        assertEquals(setOf("att-0", "att-2"), attachmentDir.list().orEmpty().toSet(), "the damaged bytes are not kept")
        assertEquals(2L, entryCount())
        for (id in listOf("att-0", "att-2")) assertEquals(files.getValue(id).toList(), storedBytes(id).toList())
        assertEquals(listOf("att-0", "att-2"), rows().map { it.id })
    }

    @Test
    fun aBackupCutOffInTheMiddleOfTheLastFileKeepsTheEntriesAndTheFilesThatArrived() {
        val files = seed()
        val uri = target("cut.expbak")
        runBlocking { actions.backup(uri, null) }
        fileOf(uri).let { f -> f.writeBytes(f.readBytes().let { it.copyOf(it.size - files.getValue("att-2").size / 2) }) }
        resetApp()
        val restored = runBlocking { actions.restore(runBlocking { actions.open(uri, null) } as DataActions.Opened.Ready) }
        assertEquals(1, restored.files!!.missing); assertEquals(setOf("att-0", "att-1"), restored.report.attached)
        assertEquals(2L, entryCount()); assertEquals(setOf("att-0", "att-1"), attachmentDir.list().orEmpty().toSet())
    }

    @Test
    fun aFileMissingOnThePhoneIsLeftOutOfTheBackupWithANoteInsteadOfFailingIt() {
        val files = seed()
        File(attachmentDir, "att-1").delete()
        val uri = target("one-missing.expbak")
        val saved = runBlocking { actions.backup(uri, null) }
        assertEquals(2, saved.files.included); assertEquals(3, saved.files.onPhone); assertEquals(1, saved.files.missing)
        assertTrue(saved.files.notes().single().contains("1 photo or file missing from this phone"), saved.files.notes().toString())
        assertEquals(listOf("att-0", "att-2"), BackupFile.decode(fileOf(uri).readBytes()).second.attachments.map { it.id })
        assertEquals(3, files.size)
    }

    // ---- limits ----

    @Test
    fun forTheShareSheetFilesGoInUpToTheLimitAndAreLeftOutAboveItWithTheReasonSaid() {
        val transfer = DataTransfer(platform, container)
        seed(listOf(5_000, 100_000))
        val small = runBlocking { transfer.backupForSharing(null) }
        assertEquals(2, small.second.files.included); assertEquals(2, BackupFile.decode(small.first).first.version)
        resetApp()
        seed(listOf(14_000_000, 14_000_000)) // 28 MB together: over the 25 MB the share sheet path takes
        val big = runBlocking { transfer.backupForSharing(null) }
        assertEquals(0, big.second.files.included); assertEquals(2, big.second.files.onPhone)
        assertTrue(big.second.files.note!!.contains("over the 25.0 MB limit"), big.second.files.note)
        assertEquals(1, BackupFile.decode(big.first).first.version); assertTrue(big.first.size < 100_000)
        // a saved backup has no such limit: the same files all go in
        val uri = target("big.expbak")
        assertEquals(2, runBlocking { actions.backup(uri, null) }.files.included)
    }

    // ---- the screen ----

    private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(tag: String) = compose.waitUntil(15_000) { has(tag) }
    private fun waitGone(tag: String) = compose.waitUntil(15_000) { !has(tag) }
    private fun waitText(tag: String, text: String) = compose.waitUntil(30_000) { compose.onAllNodes(hasTestTag(tag) and hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun tap(tag: String) { waitFor(tag); compose.onNodeWithTag(tag, useUnmergedTree = true).also { runCatching { it.performScrollTo() } }.performClick() }
    private fun viewModel(): LedgerViewModel { var vm: LedgerViewModel? = null; scenario!!.onActivity { vm = ViewModelProvider(it)[LedgerViewModel::class.java] }; return vm!! }
    private fun launchData() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        waitFor("add")
        compose.openMore()
        waitFor("open_data")
        tap("open_data")
        waitFor("open_backup")
    }

    @Test
    fun theDataScreenShowsTheSizeOfTheFilesTheSwitchChangesWhatABackupCarriesAndARestoreShowsAndReportsTheFiles() {
        val files = seed()
        launchData()
        val size = app.expenses.core.domain.AttachmentRules.sizeText(files.values.sumOf { it.size }.toLong())
        waitText("backup_files_info", "3 photos and files, $size together, will be in the backup")
        assertEquals(null, setting(BackupKeys.INCLUDE_FILES), "on by default: nothing stored")
        tap("backup_files_switch")
        waitText("backup_files_info", "stay out of the backup")
        assertEquals("0", setting(BackupKeys.INCLUDE_FILES), "the choice is kept, so automatic copies follow it too")
        val off = target("ui-off.expbak")
        viewModel().createBackup(off.toString(), null)
        waitText("data_status", "Backup saved and checked")
        waitText("data_status", "photos and files are not in it: you switched them off")
        assertEquals(1, BackupFile.decode(fileOf(off).readBytes()).first.version)

        tap("backup_files_switch")
        waitText("backup_files_info", "will be in the backup")
        val on = target("ui-on.expbak")
        viewModel().createBackup(on.toString(), null)
        waitText("data_status", "3 photos and files ($size)")
        assertEquals(2, BackupFile.decode(fileOf(on).readBytes()).first.version)

        // restore on a wiped phone: the preview names the files, the result counts them
        resetApp()
        viewModel().openBackup(off.toString())
        waitText("data_status", "Backup checked: it is complete and not damaged.")
        viewModel().cancelRestore()
        viewModel().openBackup(on.toString())
        waitText("data_status", "Backup checked: the entries are complete. Photos and files are checked when you restore.")
        waitFor("restore_preview"); waitText("restore_files", "3 photos and files ($size): 3 are not on this phone and will be put back")
        tap("restore_confirm")
        waitText("data_status", "3 photos and files were put back")
        assertEquals(files.keys, attachmentDir.list().orEmpty().toSet())
        for ((id, bytes) in files) assertEquals(bytes.toList(), storedBytes(id).toList())
        // the same backup again: nothing is put back, and the screen says why instead of saying nothing
        viewModel().openBackup(on.toString())
        waitFor("restore_confirm"); tap("restore_confirm")
        waitText("data_status", "0 photos and files were put back; not put back: 3 already here")
        assertEquals(files.keys, attachmentDir.list().orEmpty().toSet())
    }

    @Test
    fun aDamagedFileIsCountedOnTheScreenAndNeverStopsTheRestore() {
        val files = seed()
        val uri = target("ui-damaged.expbak")
        runBlocking { actions.backup(uri, null) }
        val raw = fileOf(uri).readBytes()
        raw[raw.size - 10] = (raw[raw.size - 10] + 1).toByte() // inside the last file
        fileOf(uri).writeBytes(raw)
        resetApp()
        launchData()
        viewModel().openBackup(uri.toString())
        waitFor("restore_confirm"); tap("restore_confirm")
        waitText("data_status", "2 photos and files were put back; 1 damaged in the backup (skipped)")
        waitText("data_status", "Restored: 2 entries added")
        assertEquals(setOf("att-0", "att-1"), attachmentDir.list().orEmpty().toSet())
        assertEquals(3, files.size)
    }

    @Test
    fun aSecondTapOnRestoreDoesNothingTheButtonIsOffWhileItRunsAndNoPhotoIsLost() {
        val files = seedBig(3, 14_000_000)
        val uri = target("double-tap.expbak")
        runBlocking { actions.backup(uri, null) }
        resetApp()
        launchData()
        viewModel().openBackup(uri.toString())
        waitFor("restore_confirm")
        tap("restore_confirm")
        compose.onNodeWithTag("restore_confirm", useUnmergedTree = true).assertIsNotEnabled() // busy: the dialog stays up for the whole restore, with the button off
        viewModel().confirmRestore(); viewModel().confirmRestore() // taps that get through anyway are ignored
        waitText("data_status", "3 photos and files were put back")
        assertEquals(files.keys, attachmentDir.list().orEmpty().toSet())
        for ((id, sha) in files) assertEquals(sha, shaOfStored(id), "$id is still there, whole")
        assertEquals(files.keys.toList(), db { ok -> ok.attachments.list("e0") }.map { it.id }, "every record has its file")
        assertEquals(1L, entryCount())
    }

    @Test
    fun twoRestoresStartedTogetherRunOneAtATimeAndTheSecondIsToldAtOnce() {
        val files = seedBig(3, 14_000_000)
        val uri = target("two-restores.expbak")
        runBlocking { actions.backup(uri, null) }
        resetApp()
        val ready = runBlocking { actions.open(uri, null) } as DataActions.Opened.Ready
        val outcomes = java.util.Collections.synchronizedList(mutableListOf<Result<DataTransfer.Restored>>())
        val threads = (1..2).map { Thread { outcomes += runCatching { runBlocking { actions.restore(ready) } } }.also { it.start() } }
        threads.forEach { it.join() }
        assertTrue(outcomes.count { it.isSuccess } >= 1, "one of them ran")
        for (r in outcomes.filter { it.isFailure }) assertTrue((r.exceptionOrNull() as BackupException).message!!.contains("already running"), r.exceptionOrNull().toString())
        assertEquals(files.keys, attachmentDir.list().orEmpty().toSet(), "no file was removed")
        assertEquals(files.keys.toList(), db { ok -> ok.attachments.list("e0") }.map { it.id })
        for ((id, sha) in files) assertEquals(sha, shaOfStored(id))
    }

    @Test
    fun aFileThatCannotBeReadIsLeftOutTheBackupStillSucceedsAndTheMessageSaysSo() {
        val files = seed()
        assertTrue(File(attachmentDir, "att-1").setReadable(false), "the test makes one file unreadable")
        launchData()
        val uri = target("one-unreadable.expbak")
        viewModel().createBackup(uri.toString(), null)
        waitText("data_status", "Backup saved and checked")
        waitText("data_status", "1 photo or file could not be read and was left out; the rest of the backup is complete")
        val data = BackupFile.decode(fileOf(uri).readBytes()).second
        assertEquals(2, data.entries.size, "the entries are always backed up")
        assertEquals(listOf("att-0", "att-2"), data.attachments.map { it.id })
        File(attachmentDir, "att-1").setReadable(true)
        resetApp()
        val restored = runBlocking { actions.restore(runBlocking { actions.open(uri, null) } as DataActions.Opened.Ready) }
        assertEquals(setOf("att-0", "att-2"), restored.report.attached); assertEquals(2L, entryCount())
        for (id in listOf("att-0", "att-2")) assertEquals(files.getValue(id).toList(), storedBytes(id).toList())
    }

    @Test
    fun theScreenSaysOnePhotoOrFileInTheSingular() {
        seed(listOf(5_000))
        launchData()
        waitText("backup_files_info", "1 photo or file, 4 KB together, will be in the backup")
        waitText("backup_files_info", "A restore brings it back")
    }

    // ---- automatic copies ----

    /** A folder on the app's own storage that streams, renames, and can be told to fail part way through a file or to wait inside one (a run that is cut off, a slow run). */
    private class DirFolder(val dir: File, val rename: Boolean = true) : ExportFolder {
        /** "ok" renames; "refuse" returns null; "other" renames to a different name than asked for (as a provider that adds " (1)"). */
        @Volatile var renameMode = "ok"
        /** When set, a write under this exact name fails (to make the fallback fail). */
        @Volatile var failWritesNamed: String? = null
        @Volatile var failAfterBytes: Long? = null
        @Volatile var gate: java.util.concurrent.CountDownLatch? = null
        val inside = java.util.concurrent.CountDownLatch(1)
        override fun names() = dir.list().orEmpty().toList()
        override fun write(name: String, mime: String, bytes: ByteArray): String? { File(dir, name).writeBytes(bytes); return name }
        override fun read(name: String) = File(dir, name).takeIf { it.exists() }?.readBytes()
        override fun delete(name: String) = File(dir, name).delete()
        override fun openWrite(name: String, mime: String): FolderWriter? {
            if (name == failWritesNamed) throw java.io.IOException("write failed: ENOSPC (No space left on device)")
            val out = File(dir, name).outputStream().buffered()
            return object : FolderWriter {
                var written = 0L
                override val name = name
                override fun write(buf: ByteArray, off: Int, len: Int) {
                    gate?.let { inside.countDown(); it.await() }
                    failAfterBytes?.let { if (written + len > it) { out.flush(); throw java.io.IOException("write failed: ENOSPC (No space left on device)") } }
                    out.write(buf, off, len); written += len
                }
                override fun close() = out.close()
                override fun abort() = out.close() // the half file stays, as after a cut-off
            }
        }
        override fun openRead(name: String) = File(dir, name).takeIf { it.exists() }?.let { InputStreamSource(it.inputStream().buffered(), it.length()) }
        override val canRename get() = rename
        override fun rename(name: String, newName: String): String? = when (renameMode) {
            "refuse" -> null
            "other" -> (newName + " (1)").let { if (File(dir, name).renameTo(File(dir, it))) it else null }
            else -> if (File(dir, name).renameTo(File(dir, newName))) newName else null
        }
    }

    @Test
    fun anAutomaticCopyCarriesTheFilesWhenTheSwitchIsOnAndLeavesThemOutWhenItIsOff() {
        val files = seed()
        db { it.templates.putSetting(AutoExport.S_FREQ, AutoExportFrequency.WEEKLY.name); it.templates.putSetting(AutoExport.S_FOLDER, "content://tree/x") }
        val dir = File(context.cacheDir, "auto-files").also { it.deleteRecursively(); it.mkdirs() }
        try {
            val t0 = 1_900_000_000_000L
            val r = runBlocking { AutoExport.run(container, DirFolder(dir), t0, force = true) }
            assertTrue(r.ran && r.ok, r.text); assertTrue(r.text.contains("with 3 photos and files"), r.text)
            val backup = File(dir, dir.list()!!.single { it.endsWith(".expbak") }).readBytes()
            val (header, data) = BackupFile.decode(backup)
            assertEquals(2, header.version); assertEquals(files.keys.toList(), data.attachments.map { it.id })
            // restorable from that very file
            resetApp()
            val uri = target("from-auto.expbak"); fileOf(uri).writeBytes(backup)
            assertEquals(3, runBlocking { actions.restore(runBlocking { actions.open(uri, null) } as DataActions.Opened.Ready) }.report.attached.size)
            for ((id, bytes) in files) assertEquals(bytes.toList(), storedBytes(id).toList())

            // (the wipe took the settings too)
            db { it.templates.putSetting(AutoExport.S_FREQ, AutoExportFrequency.WEEKLY.name); it.templates.putSetting(AutoExport.S_FOLDER, "content://tree/x"); it.templates.putSetting(BackupKeys.INCLUDE_FILES, "0") }
            val off = runBlocking { AutoExport.run(container, DirFolder(dir), t0 + 86_400_000L * 8, force = true) }
            assertTrue(off.ok && off.text.contains("you switched them off"), off.text)
            val newest = dir.list()!!.filter { it.endsWith(".expbak") }.sorted().last()
            assertEquals(1, BackupFile.decode(File(dir, newest).readBytes()).first.version)
        } finally { dir.deleteRecursively() }
    }

    private fun autoSetup() = db { it.templates.putSetting(AutoExport.S_FREQ, AutoExportFrequency.WEEKLY.name); it.templates.putSetting(AutoExport.S_FOLDER, "content://tree/x") }
    private fun autoDir(name: String) = File(context.cacheDir, name).also { it.deleteRecursively(); it.mkdirs() }

    @Test
    fun aCopyIsWrittenUnderATemporaryNameAndOnlyAFailedOneNeverCostsTheGoodCopyOfTheSameDay() {
        val files = seed()
        autoSetup()
        val dir = autoDir("auto-temp")
        try {
            val folder = DirFolder(dir)
            val t0 = 1_900_000_000_000L
            // a leftover from a run that was killed is not a copy, is not counted, and is removed
            File(dir, "expenses-backup-2030-01-01.expbak.part").writeText("half")
            val first = runBlocking { AutoExport.run(container, folder, t0, force = true) }
            assertTrue(first.ok, first.text)
            assertEquals(2, dir.list()!!.size, "a backup and a workbook, no .part left: ${dir.list()!!.toList()}")
            assertTrue(dir.list()!!.none { it.endsWith(".part") })
            val backupName = dir.list()!!.single { it.endsWith(".expbak") }
            val good = File(dir, backupName).readBytes()
            assertEquals(3, BackupFile.decode(good).second.attachments.size)
            // the same day again, and the disk fills part way through: the good copy is still there, byte for byte, and nothing half is named like a copy
            folder.failAfterBytes = 200_000
            val failed = runBlocking { AutoExport.run(container, folder, t0 + 60_000L, force = true) }
            assertTrue(failed.ran && !failed.ok, failed.text); assertTrue(failed.text.contains("not enough space"), failed.text)
            assertEquals(good.toList(), File(dir, backupName).readBytes().toList(), "the copy made earlier the same day is untouched")
            assertEquals(setOf(backupName) + dir.list()!!.filter { it.endsWith(".xlsx") }, dir.list()!!.toSet(), "no half file is left: ${dir.list()!!.toList()}")
            // and a folder that cannot rename keeps the old order but still removes a failed partial
            val plain = autoDir("auto-plain"); val noRename = DirFolder(plain, rename = false)
            noRename.failAfterBytes = 200_000
            val bad = runBlocking { AutoExport.run(container, noRename, t0 + 8 * 86_400_000L, force = true) }
            assertTrue(!bad.ok); assertEquals(emptyList(), plain.list()!!.filter { it.endsWith(".expbak") }, "the failed partial is removed")
            plain.deleteRecursively()
        } finally { dir.deleteRecursively() }
        assertEquals(3, files.size)
    }

    @Test
    fun aRenameThatFailsOrChangesTheNameFallsBackWithoutEverLosingTheDaysCopy() {
        seed(); autoSetup()
        val dir = autoDir("auto-fallback")
        try {
            val folder = DirFolder(dir)
            val t0 = 1_900_000_000_000L
            assertTrue(runBlocking { AutoExport.run(container, folder, t0, force = true) }.ok)
            val backupName = dir.list()!!.single { it.endsWith(".expbak") }
            val first = File(dir, backupName).readBytes()
            for (mode in listOf("refuse", "other")) {
                folder.renameMode = mode
                val r = runBlocking { AutoExport.run(container, folder, t0 + 60_000L, force = true) }
                assertTrue(r.ok, "$mode: ${r.text}")
                val names = dir.list()!!.toList()
                assertEquals(listOf(backupName), names.filter { it.endsWith(".expbak") || it.contains(".expbak ") || it.endsWith(".part") || it.endsWith(".old") }, "$mode: exactly one copy of the day, under its real name, no leftovers: $names")
                assertEquals(3, BackupFile.decode(File(dir, backupName).readBytes()).second.attachments.size, mode)
            }
            // rename refused AND the fallback write fails: the verified temporary copy (or the earlier copy of the day) is kept
            folder.renameMode = "refuse"; folder.failWritesNamed = backupName
            val bad = runBlocking { AutoExport.run(container, folder, t0 + 120_000L, force = true) }
            assertTrue(!bad.ok, bad.text)
            val keep = dir.list()!!.toList()
            val survivor = listOf(backupName, backupName + ".part", backupName + ".old").firstOrNull { it in keep }
            assertTrue(survivor != null, "the day's copy is not lost: $keep")
            assertEquals(3, BackupFile.decode(File(dir, survivor!!).readBytes()).second.attachments.size, "and it is a whole, verified backup")
            assertEquals(true, first.isNotEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun onlyOneAutomaticCopyIsWrittenAtATimeAndTheSecondCallerIsToldAtOnce() {
        seed()
        autoSetup()
        val dir = autoDir("auto-single")
        try {
            val folder = DirFolder(dir)
            val gate = java.util.concurrent.CountDownLatch(1)
            folder.gate = gate
            var first: AutoExport.Result? = null
            val t = Thread { first = runBlocking { AutoExport.run(container, folder, 1_900_000_000_000L, force = true) } }
            t.start()
            assertTrue(folder.inside.await(60, java.util.concurrent.TimeUnit.SECONDS), "the first run is inside its write")
            assertTrue(AutoExportRunner.running.value, "the screen is told a copy is being saved")
            val second = runBlocking { AutoExport.run(container, folder, 1_900_000_000_000L, force = true) }
            assertTrue(!second.ran && second.text.contains("already being saved"), second.text)
            assertEquals(null, setting(AutoExport.S_STATUS), "the refused caller records nothing")
            folder.gate = null; gate.countDown(); t.join()
            assertTrue(first!!.ok, first!!.text); assertTrue(!AutoExportRunner.running.value)
            assertEquals(2, dir.list()!!.size)
        } finally { dir.deleteRecursively() }
    }

    // ---- how much is held at once ----

    @Test
    fun manyMegabytesOfFilesGoThroughABackupAndARestoreWithoutBeingHeldInMemory() {
        val mb = InstrumentationRegistry.getArguments().getString("bigMb")?.toIntOrNull() ?: 60
        val per = 14 * 1024 * 1024
        val count = (mb * 1024 * 1024L / per).toInt().coerceAtLeast(2)
        // 14 MB files made by streaming one pattern through the store's own writer, so the test itself never holds a file either; an entry holds at most ten, so they are spread over entries
        val ids = (0 until count).map { "big-$it" }
        db { ok -> for (e in 0..(count - 1) / 10) ok.store.save(Expense(EntryId("big$e"), 1L + e, AccountId("cash"), Money(100, Currency.INR)), EntryDetails(merchant = "Big $e")) }
        val block = bytesOf(3, 1 shl 16)
        for ((n, id) in ids.withIndex()) {
            val sink = vault.create(id); val digest = Sha256.Digest()
            var left = per
            while (left > 0) { val k = minOf(left, block.size); block[0] = n.toByte(); digest.update(block, 0, k); sink.write(block, 0, k); left -= k }
            sink.close()
            db { ok -> ok.attachments.add(id, "big${n / 10}", "$id.bin", "application/octet-stream", per.toLong(), digest.finishHex()) }
        }
        val total = per.toLong() * count
        val peak = java.util.concurrent.atomic.AtomicLong()
        val running = java.util.concurrent.atomic.AtomicBoolean(true)
        val sampler = Thread { while (running.get()) { val rt = Runtime.getRuntime(); peak.accumulateAndGet(rt.totalMemory() - rt.freeMemory()) { a, b -> maxOf(a, b) }; Thread.sleep(20) } }.also { it.start() }
        val uri = target("big.expbak")
        val t0 = System.nanoTime()
        val saved = try { runBlocking { actions.backup(uri, "correct horse") } } finally { }
        val t1 = System.nanoTime()
        assertTrue(saved.files.included >= 1)
        val includedBytes = saved.files.includedBytes
        resetApp()
        val restored = try { runBlocking { actions.restore(runBlocking { actions.open(uri, "correct horse") } as DataActions.Opened.Ready) } } finally { running.set(false); sampler.join() }
        val t2 = System.nanoTime()
        assertEquals(saved.files.included, restored.report.attached.size, "every file came back")
        assertEquals(0, restored.files!!.damaged + restored.files!!.missing + restored.files!!.notStored)
        assertEquals(includedBytes, restored.report.attached.sumOf { File(attachmentDir, it).length() })
        val peakMb = peak.get() / 1024 / 1024
        android.util.Log.i("BackupFilesTest", "BIG: ${includedBytes / 1024 / 1024} MB in ${saved.files.included} files (asked $mb MB, on record ${total / 1024 / 1024} MB), sealed backup ${fileOf(uri).length() / 1024 / 1024} MB; backup ${(t1 - t0) / 1_000_000} ms, restore ${(t2 - t1) / 1_000_000} ms; peak heap in use $peakMb MB of ${Runtime.getRuntime().maxMemory() / 1024 / 1024} MB")
        assertTrue(peakMb < 96, "the heap in use stayed small ($peakMb MB) for ${includedBytes / 1024 / 1024} MB of files")
    }
}

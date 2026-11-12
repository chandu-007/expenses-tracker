package app.expenses.core.storage

import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.Trip
import app.expenses.core.domain.TripRules
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Trips in backups: an optional key, absent when there are none, read by older readers as nothing, restored as a merge, made safe against a file made by someone else; sealed files carry it like everything. Invented data. */
class TripBackupTest {
    private val inr = Currency.INR
    private val pass = "correct horse battery"
    private fun day(y: Int, mo: Int, d: Int) = LocalDate(y, mo, d).toEpochDays().toLong()
    private fun payloadText(d: Device) = BackupFile.encode(d.backup.export(), 1L).decodeToString()
    private fun roundTrip(d: Device): BackupData = BackupFile.decode(BackupFile.encode(d.backup.export(), 1L)).second

    private val goa = Trip("goa", "Goa", "Trip: Goa", day(2026, 11, 3), day(2026, 11, 9), true, 150_050L)
    private val kerala = Trip("kerala", "Kerala", "Trip: Kerala", day(2026, 12, 20), day(2026, 12, 27), false, null)
    private val past = Trip("past", "Pune", "Trip: Pune", day(2026, 2, 1), day(2026, 2, 3))

    private fun deviceWithTrips(node: String = "a"): Device {
        val d = Device(node)
        d.ledger.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, inr))
        d.ledger.save(Expense(EntryId("lunch"), 5_000, AccountId("cash"), Money(10_000, inr), listOf(CategorySplit(CategoryId("Food"), Money(10_000, inr)))), EntryDetails(merchant = "Lunch", tags = listOf("Trip: Goa")))
        for (t in listOf(goa, kerala, past)) d.ledger.trips.save(t)
        return d
    }

    @Test
    fun aBackupCarriesTheTripsAndAnEmptyPhoneRestoresThemExactlyWithTheTagsOnTheirEntries() {
        val a = deviceWithTrips()
        val text = payloadText(a)
        assertTrue("\"trips\"" in text)
        val b = Device("b")
        val report = b.backup.restore(roundTrip(a))
        assertEquals(3, report.tripsAdded)
        assertEquals(a.ledger.trips.all(), b.ledger.trips.all())
        assertEquals(a.backup.export(), b.backup.export(), "exporting the restored phone gives the same backup")
        assertEquals(listOf("Trip: Goa"), b.ledger.tagsOf(EntryId("lunch")))
        assertEquals(150_050L, b.ledger.trips.get("goa")!!.dailyBudgetMinor); assertFalse(b.ledger.trips.get("kerala")!!.enabled)
    }

    @Test
    fun aBackupOfAPhoneWithNoTripsIsExactlyAsItWasAndOneFromBeforeRestoresUnchanged() {
        val a = Device("a"); a.populate()
        assertFalse("trips" in payloadText(a), "no key at all when there is nothing to say: an older reader sees the file it always saw")
        val old = roundTrip(a)
        assertEquals(emptyList(), old.trips)
        val b = Device("b")
        val report = b.backup.restore(old)
        assertEquals(0, report.tripsAdded)
        assertEquals(a.backup.export(), b.backup.export())
        assertEquals(0, b.ledger.trips.count())
    }

    @Test
    fun aFileWithTripsIsReadByAnOlderReaderThatIgnoresUnknownKeys() {
        val text = payloadText(deviceWithTrips())
        val older = Json { ignoreUnknownKeys = true }.decodeFromString<OlderBackupData>(text.substringAfter('\n'))
        assertEquals(setOf("cash"), older.accounts.map { it.id }.toSet())
        assertEquals(listOf("lunch"), older.entries.map { it.id })
        assertEquals(listOf("Trip: Goa"), older.entries.single().tags, "the tag is on the entry the way every tag always was")
    }

    @kotlinx.serialization.Serializable private data class OlderBackupData(val accounts: List<BackupAccount> = emptyList(), val entries: List<BackupEntry> = emptyList())

    @Test
    fun restoringTwiceAddsNothingAndATripAlreadyHereKeepsItsOwnFields() {
        val a = deviceWithTrips()
        val b = Device("b")
        b.ledger.trips.save(goa.copy(name = "Goa (mine)", endDay = day(2026, 11, 6), dailyBudgetMinor = null))
        val first = b.backup.restore(roundTrip(a))
        assertEquals(2, first.tripsAdded, "the trip with the same id is left as it is")
        assertEquals("Goa (mine)", b.ledger.trips.get("goa")!!.name); assertEquals(day(2026, 11, 6), b.ledger.trips.get("goa")!!.endDay)
        val before = b.ledger.trips.all()
        assertEquals(0, b.backup.restore(roundTrip(a)).tripsAdded)
        assertEquals(before, b.ledger.trips.all())
    }

    @Test
    fun aTripThatWouldOverlapATripThatIsOnHereIsKeptSwitchedOff() {
        val a = Device("a"); a.ledger.trips.save(goa)
        val b = Device("b"); b.ledger.trips.save(Trip("mine", "Mumbai", "Trip: Mumbai", day(2026, 11, 8), day(2026, 11, 12)))
        b.backup.restore(roundTrip(a))
        assertEquals(false, b.ledger.trips.get("goa")!!.enabled, "kept, so nothing is lost, but it does not tag beside the other")
        assertEquals(true, b.ledger.trips.get("mine")!!.enabled)
        assertTrue(b.ledger.trips.all().filter { it.enabled }.let { on -> on.indices.all { i -> on.indices.all { j -> i == j || on[i].endDay < on[j].startDay || on[j].endDay < on[i].startDay } } }, "never two that are on and share a day")
    }

    @Test
    fun twoTripsWithTheSameTagAreKeptButTheLaterOneIsSwitchedOffAndOneAlreadyHereWinsOverTheFile() {
        val file = BackupData(trips = listOf(
            BackupTrip("a", "First", "Trip: X", day(2026, 3, 1), day(2026, 3, 3)),
            BackupTrip("b", "Second", " trip: x ", day(2026, 6, 1), day(2026, 6, 3)), // no overlap, same tag
            BackupTrip("c", "Third", "Trip: Y", day(2026, 9, 1), day(2026, 9, 3)),
        ))
        val d = Device("b"); d.backup.restore(file)
        assertEquals(listOf(true, false, true), listOf("a", "b", "c").map { d.ledger.trips.get(it)!!.enabled }, "kept, nothing lost, but only one trip owns the tag")
        // a phone that already has a trip with the tag: the file's trip with it comes in off
        val e = Device("e"); e.ledger.trips.save(Trip("mine", "Mine", "Trip: Y", day(2028, 1, 1), day(2028, 1, 2)))
        e.backup.restore(file)
        assertEquals(false, e.ledger.trips.get("c")!!.enabled); assertEquals(true, e.ledger.trips.get("a")!!.enabled)
    }

    @Test
    fun aHostileListIsMadeSafeAtMostTwoHundredTripsEveryOneValidAndNoTwoThatAreOnShareADay() {
        val rnd = Random(20261112)
        val junk = (0 until 5_000).map { i ->
            val start = rnd.nextLong(-5_000_000L, 60_000L); val len = rnd.nextLong(-400L, 800L)
            BackupTrip(
                id = if (i % 7 == 0) "dup" else if (i % 11 == 0) "" else "t$i",
                name = when (i % 5) { 0 -> ""; 1 -> "   "; 2 -> "x".repeat(rnd.nextInt(1, 2_000)); 3 -> "Trip\n\t$i,\"quoted\""; else -> "Name $i" },
                tag = when (i % 4) { 0 -> ""; 1 -> "a,b,c"; 2 -> "t".repeat(rnd.nextInt(1, 500)); else -> "tag $i" },
                startDay = start, endDay = start + len, enabled = rnd.nextBoolean(),
                dailyBudgetMinor = when (i % 3) { 0 -> null; 1 -> -rnd.nextLong(); else -> rnd.nextLong() },
            )
        } + (0 until 300).map { i -> BackupTrip("good$i", "Good $i", "Good $i", day(2026, 1, 1) + i * 3L, day(2026, 1, 1) + i * 3L + 1, true, 1_000L + i) }
        val b = Device("b")
        val report = b.backup.restore(BackupData(trips = junk))
        val kept = b.ledger.trips.all()
        assertEquals(kept.size, report.tripsAdded)
        assertTrue(kept.size <= TripRules.MAX_TRIPS, "at most ${TripRules.MAX_TRIPS}: ${kept.size}")
        assertEquals(kept.size, kept.map { it.id }.toSet().size, "no id twice")
        for (t in kept) {
            assertEquals(null, TripRules.problemOf(t.copy(enabled = false), emptyList()), "$t")
            assertTrue(t.name.length <= 40 && t.tag.length <= 30 && !t.tag.contains(',') && !t.name.contains('\n'))
            assertTrue(t.dailyBudgetMinor == null || t.dailyBudgetMinor in 1..TripRules.MAX_BUDGET_MINOR)
        }
        val on = kept.filter { it.enabled }
        for (i in on.indices) for (j in i + 1 until on.size) assertTrue(on[i].endDay < on[j].startDay || on[j].endDay < on[i].startDay, "overlap: ${on[i]} ${on[j]}")
        assertTrue(kept.isNotEmpty(), "the good ones came through")
        // the same list restored again changes nothing
        assertEquals(0, b.backup.restore(BackupData(trips = junk)).tripsAdded)
    }

    @Test
    fun aListOfAHundredThousandRepeatedTripsIsCutShortAndDoesNotSlowTheRestoreDown() {
        val one = BackupTrip("same", "Goa", "Trip: Goa", day(2026, 11, 3), day(2026, 11, 9))
        val b = Device("b")
        val started = System.nanoTime()
        val report = b.backup.restore(BackupData(trips = List(100_000) { one }))
        assertEquals(1, report.tripsAdded)
        assertTrue((System.nanoTime() - started) / 1_000_000 < 10_000, "a damaged file must not hang the restore")
    }

    @Test
    fun aTripWithMissingDaysMakesTheWholeFileUnreadableAndNothingIsChanged() {
        val payload = """{"currencies":[],"trips":[{"id":"x","name":"Goa","tag":"Goa"}]}""".encodeToByteArray()
        val header = Json.encodeToString(BackupHeader.serializer(), BackupHeader(BackupFile.FORMAT, 1, 1L, payload.size, Sha256.hex(payload)))
        assertFailsWith<BackupException> { BackupFile.decode(header.encodeToByteArray() + byteArrayOf('\n'.code.toByte()) + payload) }
    }

    @Test
    fun aPlainFileAnEXPENC2FileAndAnEXPENC3CopyAllRestoreTheTrips() {
        val a = deviceWithTrips()
        fun write(seal: SealWith?): ByteArray { val out = ByteArraySink(); BackupTransfer.write(a.backup.export(), emptyList(), 1L, seal, out); return out.toByteArray() }
        fun open(bytes: ByteArray): BackupData {
            val src = if (BackupSeal.isSealed(bytes)) BackupSeal.source(ByteArraySource(bytes), JdkSeal) { salt, it -> JdkSeal.deriveKey(pass, salt, it) } else ByteArraySource(bytes)
            return BackupFile.open(src).data
        }
        val plain = write(null)
        val sealed2 = write(SealWith(pass, JdkSeal))
        assertEquals("EXPENC2\n", sealed2.copyOf(8).decodeToString())
        val items = HashMap<String, ByteArray>()
        val store = object : SecretStore {
            override fun put(name: String, secret: ByteArray) { items[name] = secret.copyOf() }
            override fun read(name: String): SecretRead = items[name]?.let { SecretRead.Found(it.copyOf()) } ?: SecretRead.NotFound
            override fun delete(name: String) { items.remove(name) }
        }
        AutoCopyProtection.enable(store, JdkSeal, pass, 100_000)
        val sealed3 = write((AutoCopyProtection.plan(true, store, JdkSeal) as AutoCopyProtection.Plan.Sealed).seal)
        assertEquals("EXPENC3\n", sealed3.copyOf(8).decodeToString())
        for ((label, bytes) in listOf("plain" to plain, "EXPENC2" to sealed2, "EXPENC3" to sealed3)) {
            val b = Device("b")
            assertEquals(3, b.backup.restore(open(bytes)).tripsAdded, label)
            assertEquals(a.ledger.trips.all(), b.ledger.trips.all(), label)
        }
        assertTrue("trips" in plain.decodeToString() && "trips" !in sealed2.toString(Charsets.ISO_8859_1) && "trips" !in sealed3.toString(Charsets.ISO_8859_1))
    }

    @Test
    fun theJavaReferenceReadsTheTripsAndRefusesFilesThatBreakTheDocumentedRules() {
        val javaBin = File(System.getProperty("java.home"), "bin/java").path
        val interop = listOf(File("../../tools/backup_interop/Interop.java"), File("tools/backup_interop/Interop.java")).first { it.exists() }.path
        fun run(data: BackupData, vararg args: String): Pair<Int, String> {
            val file = File.createTempFile("kotlin-trips", ".expbak")
            try {
                file.writeBytes(BackupFile.encode(data, 1L))
                val proc = ProcessBuilder(listOf(javaBin, interop, "verify2", "-", file.path) + args).redirectErrorStream(true).start()
                val out = proc.inputStream.bufferedReader().readText()
                return proc.waitFor() to out
            } finally { file.delete() }
        }
        val data = deviceWithTrips().backup.export()
        val (code, out) = run(data, "trips=3")
        assertEquals(0, code, out); assertTrue("3 trips" in out, out)
        assertNotEquals(0, run(data, "trips=2").first, "a wrong expected count fails")
        assertEquals(0, run(BackupData(), "trips=0").first, "a file with no trips has none")
        val ok = data.trips
        val bad = listOf(
            "overlap of two that are on" to ok + BackupTrip("clash", "Clash", "c", goa.startDay + 1, goa.endDay + 1),
            "last day before the first" to ok + BackupTrip("neg", "N", "t", goa.endDay, goa.startDay),
            "days outside 2000 to 2099" to ok + BackupTrip("old", "Old", "t", 100, 105),
            "longer than 366 days" to ok + BackupTrip("long", "Long", "t", day(2030, 1, 1), day(2031, 1, 2)),
            "a name with a line break" to ok + BackupTrip("nl", "Two\nlines", "t", day(2028, 1, 1), day(2028, 1, 2)),
            "a tag with a comma" to ok + BackupTrip("comma", "C", "a,b", day(2028, 1, 1), day(2028, 1, 2)),
            "a name of 41 characters" to ok + BackupTrip("wide", "x".repeat(41), "t", day(2028, 1, 1), day(2028, 1, 2)),
            "a budget of zero" to ok + BackupTrip("zero", "Z", "t", day(2028, 1, 1), day(2028, 1, 2), true, 0L),
            "the same id twice" to ok + ok.first(),
        )
        for ((what, trips) in bad) assertNotEquals(0, run(data.copy(trips = trips)).first, what)
        // two that are on may not share a day, but one that is off may
        assertEquals(0, run(data.copy(trips = ok + BackupTrip("off", "Off", "t", goa.startDay, goa.endDay, false))).first)
    }
}

package app.expenses.core.storage

import app.expenses.core.domain.Currency
import app.expenses.core.domain.SearchDates
import app.expenses.core.domain.Trip
import app.expenses.core.domain.TripLine
import app.expenses.core.domain.TripRules
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.TimeZone

/**
 * Trips (Travel mode): kept in the small table `trip`, each change logged like any other field change. A trip is a name, the one tag its expenses get, its first and last day (both included, counted
 * since 1970-01-01 as the Search dates are), whether it is on, and an optional daily budget. [LedgerStore.save] asks [tripAt] when it creates an entry with [EntryDetails.tripTag]; the check is one read
 * of the few enabled rows and a comparison with their days, never a scan of entries. Not thread-safe: use through the process-wide write lock.
 *
 * [zone] is the calendar the days of a trip are read in: the phone's current zone (a day is a calendar day wherever the phone is), unless a test says otherwise.
 */
class TripStore(private val db: ExpensesDb, private val clock: HlcClock, private val nowMillis: () -> Long, private val zone: () -> TimeZone) {
    private val q get() = db.schemaQueries

    /** Every trip, the latest first. */
    fun all(): List<Trip> = q.allTrips().executeAsList().map(::of)

    /** The trips that are on, the earliest first. */
    fun enabled(): List<Trip> = q.enabledTrips().executeAsList().map(::of)

    fun get(id: String): Trip? = q.tripById(id).executeAsOneOrNull()?.let(::of)

    /** How many live entries carry [tag] (ignoring capitals): what a trip with that tag would count in its total. */
    fun tagUses(tag: String): Long = q.tagUseCount(tag).executeAsOne()

    fun count(): Int = q.tripCount().executeAsOne().toInt()

    /** The trip that tags a new expense made at [occurredAt]: the enabled trip whose days include the day of [occurredAt] in the phone's calendar, or null (the usual answer: there are no trips). */
    fun tripAt(occurredAt: Long): Trip? {
        val rows = q.enabledTrips().executeAsList()
        if (rows.isEmpty()) return null
        return TripRules.tripAt(rows.map(::of), occurredAt, zone())
    }

    /** The trips, on or off, whose days include the day of [occurredAt]: for a refund, which inherits the tag of the trip its expense was made in. */
    fun tripsCovering(occurredAt: Long): List<Trip> = all().filter { TripRules.covers(it, occurredAt, zone()) }

    /**
     * Adds or changes a trip, all or nothing. Throws [IllegalArgumentException] with a sentence for the person when the trip breaks a rule: its days (see [TripRules.problemOf]) or, when it is on, an overlap
     * with another trip that is on (the message names that trip). The tags already on entries are never touched by this.
     */
    fun save(trip: Trip) {
        db.transaction {
            val before = q.tripById(trip.id).executeAsOneOrNull()
            require(before != null || count() < TripRules.MAX_TRIPS) { "You can keep up to ${TripRules.MAX_TRIPS} trips. Delete an old one first." }
            TripRules.problemOf(trip, all().filter { it.id != trip.id })?.let { throw IllegalArgumentException(it) }
            q.upsertTrip(trip.id, trip.name, trip.tag, trip.startDay, trip.endDay, if (trip.enabled) 1L else 0L, trip.dailyBudgetMinor)
            val was = before?.let(::of)
            fun log(field: String, old: String?, new: String?) { if (was == null || old != new) q.insertOp(clock.now(nowMillis()).encode(), "trip", trip.id, field, new) }
            log("name", was?.name, trip.name); log("tag", was?.tag, trip.tag)
            log("start_day", was?.startDay?.toString(), trip.startDay.toString()); log("end_day", was?.endDay?.toString(), trip.endDay.toString())
            log("enabled", was?.enabled?.let { if (it) "1" else "0" }, if (trip.enabled) "1" else "0")
            log("daily_budget_minor", was?.dailyBudgetMinor?.toString(), trip.dailyBudgetMinor?.toString())
        }
    }

    /** Removes a trip. The tags it put on entries stay: they are ordinary tags. */
    fun delete(id: String) {
        db.transaction {
            if (q.tripById(id).executeAsOneOrNull() != null) {
                q.deleteTrip(id)
                q.insertOp(clock.now(nowMillis()).encode(), "trip", id, "deleted", "1")
            }
        }
    }

    /** What was spent on [trip]: the lines of ALL entries carrying its tag (any date: a prepaid flight, a late refund), newest first, at most [LINE_LIMIT]; [capped] says there were more (the figures are then of the newest part). */
    class Lines(val lines: List<TripLine>, val capped: Boolean)

    fun lines(trip: Trip, currency: Currency, limit: Int = LINE_LIMIT): Lines {
        val rows = q.tripLines(trip.tag, currency.code, limit + 1L).executeAsList()
        return Lines(rows.take(limit).map { TripLine(it.entry_id, it.occurred_at, it.category_id, it.mine_minor) }, rows.size > limit)
    }

    /** What was spent on [trip] and on how many entries: two numbers in one read, for the Home card. */
    fun totals(trip: Trip, currency: Currency): Pair<Long, Int> {
        val r = q.tripTotals(trip.tag, currency.code).executeAsOne()
        return (r.mine ?: 0L) to r.entries.toInt()
    }

    /** The live expenses on the [days] (inclusive ranges of epoch days) that do not carry [trip]'s tag, oldest first. */
    fun untagged(trip: Trip, days: List<LongRange>): List<String> {
        val z = zone()
        val out = ArrayList<String>()
        for (r in days) if (!r.isEmpty()) out += q.untaggedExpensesBetween(SearchDates.fromMillis(r.first, z), SearchDates.untilMillis(r.last, z), trip.tag).executeAsList()
        return out
    }

    /**
     * Puts the trips of a backup on this phone (a restore merges by id: a trip already here is left as it is). Each is made fit with [TripRules.fromFile] and dropped when it cannot be a trip; one that is on
     * and overlaps a trip here that is on is kept but switched off, so the two never both tag; at most [TripRules.MAX_TRIPS] are kept in all and at most [MAX_READ] of the file are read. Returns how many were added.
     */
    fun restore(list: List<BackupTrip>): Int {
        var added = 0
        val here = all().toMutableList()
        for (b in list.asSequence().take(MAX_READ)) {
            if (here.size >= TripRules.MAX_TRIPS) break
            val t = TripRules.fromFile(b.id, b.name, b.tag, b.startDay, b.endDay, b.enabled, b.dailyBudgetMinor) ?: continue
            if (here.any { it.id == t.id }) continue
            val kept = if (TripRules.overlapping(t, here) != null || TripRules.tagTaken(t, here) != null) t.copy(enabled = false) else t
            q.upsertTrip(kept.id, kept.name, kept.tag, kept.startDay, kept.endDay, if (kept.enabled) 1L else 0L, kept.dailyBudgetMinor)
            q.insertOp(clock.now(nowMillis()).encode(), "trip", kept.id, "name", kept.name)
            here += kept; added++
        }
        return added
    }

    private fun of(r: app.expenses.core.storage.db.Trip) = Trip(r.id, r.name, r.tag, r.start_day, r.end_day, r.enabled == 1L, r.daily_budget_minor)

    companion object {
        /** The most lines of one trip read for its summary: far more than a trip holds; a longer list is cut and flagged. */
        const val LINE_LIMIT = 20_000

        /** A restore reads at most this many trips of a file, kept or not: a damaged file with millions of them is cut short. */
        const val MAX_READ = 20_000
    }
}

/**
 * "Tag N earlier entries": the explicit, counted action that puts a trip's tag on expenses that were made before the trip was entered (or before its dates grew to cover them). Never automatic. Only
 * expenses are offered, and only those without the tag. An exact Undo takes the tag off exactly the entries this put it on.
 */
class TripTagging(private val ledger: LedgerStore) {
    /** What would be tagged: the expenses of [days] that lack the trip's tag. */
    class Offer(val tripId: String, val tag: String, val ids: List<String>) { val count: Int get() = ids.size }

    /** What was tagged ([tagged], for Undo) and how many expenses were left out because they already hold ten tags. */
    class Applied(val tripId: String, val tag: String, val tagged: List<String>, val full: Int)

    /** The expenses [days] of [trip] holds that do not carry its tag. With [days] null, all of the trip's days. */
    fun offer(trip: Trip, days: List<LongRange>? = null): Offer = Offer(trip.id, trip.tag, ledger.trips.untagged(trip, days ?: listOf(trip.startDay..trip.endDay)))

    /** Tags every entry of [offer] that is still a live expense without the tag and with room for it, in one step. */
    fun apply(offer: Offer): Applied {
        val tagged = ArrayList<String>(); var full = 0
        ledger.atomically {
            for (id in offer.ids) when (ledger.addTag(EntryId(id), offer.tag, expenseOnly = true)) {
                LedgerStore.TagAdd.ADDED -> tagged += id
                LedgerStore.TagAdd.FULL -> full++
                else -> Unit
            }
        }
        return Applied(offer.tripId, offer.tag, tagged, full)
    }

    /** Takes the tag off exactly the entries [applied] tagged (one the person has taken it off since is left as it is). Returns how many lost it. */
    fun undo(applied: Applied): Int {
        var n = 0
        ledger.atomically { for (id in applied.tagged.asReversed()) if (ledger.removeTag(EntryId(id), applied.tag)) n++ }
        return n
    }
}

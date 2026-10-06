package app.expenses.core.storage

import app.expenses.core.domain.Currency
import app.expenses.core.domain.DueItem
import app.expenses.core.domain.Money
import app.expenses.core.domain.RecurringItem
import app.expenses.core.domain.RepeatUnit
import app.expenses.core.domain.Schedule
import app.expenses.core.domain.ScheduleEnd
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Repeating expenses. An occurrence is identified by its due date, and the entry it posts has a fixed id (`rec:<item>:<day>`), so
 * posting the same occurrence twice cannot create two entries, and editing a schedule never re-posts or forgets one already dealt with.
 * Edits change future occurrences only: entries already posted are ordinary entries and stay as they are.
 *
 * Only occurrences from the last [DUE_WINDOW_DAYS] days count as due; older ones nobody dealt with are no longer offered.
 *
 * Not thread-safe: call through the process-wide write lock.
 */
class RecurringStore(
    private val db: ExpensesDb,
    private val clock: HlcClock,
    private val nowMillis: () -> Long,
    private val ledger: LedgerStore,
) {
    private val q get() = db.schemaQueries

    fun save(requested: RecurringItem) {
        db.transaction {
            val item = requested.copy(category = CategoryId(db.resolveCategory(requested.category.value)))
            val before = q.recurringById(item.id).executeAsOneOrNull()
            require(before == null || before.deleted == 0L) { "That repeating expense was deleted" }
            val currency = item.amount?.currency ?: Currency.INR
            q.insertCurrency(currency.code, currency.minorUnitDigits.toLong())
            val end = item.schedule.end
            q.upsertRecurring(
                item.id, item.name.trim(), item.amount?.minorUnits, currency.code, item.category.value, item.account.value, item.merchant,
                item.schedule.unit.name, item.schedule.every.toLong(), item.schedule.start.toEpochDays(),
                when (end) { ScheduleEnd.Never -> "never"; is ScheduleEnd.OnOrBefore -> "date"; is ScheduleEnd.AfterCount -> "count" },
                when (end) { ScheduleEnd.Never -> null; is ScheduleEnd.OnOrBefore -> end.date.toEpochDays(); is ScheduleEnd.AfterCount -> end.count.toLong() },
                if (item.auto) 1 else 0,
            )
            val after = fieldsOf(item.copy(name = item.name.trim()))
            val prior = before?.let { fieldsOf(itemOf(it)) }
            for ((field, value) in after) if (prior == null || prior[field] != value) q.insertOp(clock.now(nowMillis()).encode(), "recurring", item.id, field, value)
        }
    }

    fun delete(id: String) {
        db.transaction {
            val row = q.recurringById(id).executeAsOneOrNull() ?: return@transaction
            if (row.deleted == 1L) return@transaction
            q.softDeleteRecurring(id)
            q.insertOp(clock.now(nowMillis()).encode(), "recurring", id, "deleted", "1")
        }
    }

    fun list(): List<RecurringItem> = q.liveRecurring().executeAsList().map(::itemOf)

    /** Occurrences due on or before [today] that nobody has posted or skipped, oldest first. */
    fun due(today: LocalDate): List<DueItem> = unhandled(today.minus(DUE_WINDOW_DAYS, DateTimeUnit.DAY), today.plus(1, DateTimeUnit.DAY))

    /** Occurrences with `from <= date < until` that are still waiting, across all items, oldest first. */
    fun unhandled(from: LocalDate, until: LocalDate): List<DueItem> =
        list().flatMap { item ->
            val done = q.doneDays(item.id, from.toEpochDays()).executeAsList().toSet()
            item.schedule.occurrences(from, until).filter { it.date.toEpochDays() !in done }.map { DueItem(item, it.date) }
        }.sortedWith(compareBy({ it.date }, { it.item.name.lowercase() }))

    /**
     * Posts one occurrence as an expense dated on its due day and returns the entry id, or null if it was already posted or skipped.
     * [amount] overrides the item's amount (required when the item has none).
     */
    fun post(id: String, date: LocalDate, zone: TimeZone, amount: Money? = null): String? = db.transactionWithResult {
        val item = live(id)
        requireOccurrence(item, date)
        if (q.doneRow(id, date.toEpochDays()).executeAsOneOrNull() != null) return@transactionWithResult null
        val paid = amount ?: item.amount ?: throw IllegalArgumentException("Enter an amount for ${item.name}")
        require(paid.minorUnits > 0) { "The amount must be positive" }
        val entryId = "rec:$id:${date.toEpochDays()}"
        // An occurrence that was posted, undone and is now posted again reuses its entry, which undo only marked as deleted.
        if (q.entryById(entryId).executeAsOneOrNull()?.deleted == 1L) ledger.restore(EntryId(entryId))
        ledger.save(
            Expense(EntryId(entryId), date.atStartOfDayIn(zone).toEpochMilliseconds(), item.account, paid, listOf(CategorySplit(item.category, paid))),
            EntryDetails(merchant = item.merchant ?: item.name, source = "recurring", sourceRef = "$id:${date.toEpochDays()}"),
        )
        markDone(id, date, "posted", entryId)
        entryId
    }

    fun skip(id: String, date: LocalDate) {
        db.transaction {
            val item = live(id)
            requireOccurrence(item, date)
            if (q.doneRow(id, date.toEpochDays()).executeAsOneOrNull() == null) markDone(id, date, "skipped", null)
        }
    }

    /** Takes back a post or a skip: the entry (if any) is deleted and the occurrence is due again. */
    fun undo(id: String, date: LocalDate) {
        db.transaction {
            val row = q.doneRow(id, date.toEpochDays()).executeAsOneOrNull() ?: return@transaction
            row.entry_id?.let { ledger.delete(EntryId(it)) }
            q.deleteDone(id, date.toEpochDays())
            q.insertOp(clock.now(nowMillis()).encode(), "recurring_done", "$id:${date.toEpochDays()}", "outcome", null)
        }
    }

    /** Posts every due occurrence of items set to post by themselves. Returns what was posted, with the entry ids. */
    fun autoPost(today: LocalDate, zone: TimeZone): List<Pair<DueItem, String>> = db.transactionWithResult {
        due(today).filter { it.item.auto }.mapNotNull { d -> post(d.item.id, d.date, zone)?.let { d to it } }
    }

    // ---- internals ----

    private fun live(id: String): RecurringItem =
        q.recurringById(id).executeAsOneOrNull()?.takeIf { it.deleted == 0L }?.let(::itemOf) ?: throw IllegalArgumentException("That repeating expense no longer exists")

    private fun requireOccurrence(item: RecurringItem, date: LocalDate) =
        require(item.schedule.occurrences(date, date.plus(1, DateTimeUnit.DAY)).isNotEmpty()) { "${item.name} is not due on that date" }

    private fun markDone(id: String, date: LocalDate, outcome: String, entryId: String?) {
        q.putDone(id, date.toEpochDays(), outcome, entryId)
        q.insertOp(clock.now(nowMillis()).encode(), "recurring_done", "$id:${date.toEpochDays()}", "outcome", outcome)
    }

    private fun itemOf(r: app.expenses.core.storage.db.Recurring): RecurringItem {
        val start = LocalDate.fromEpochDays(r.start_day)
        val end = when (r.end_kind) {
            "date" -> ScheduleEnd.OnOrBefore(LocalDate.fromEpochDays(r.end_value!!))
            "count" -> ScheduleEnd.AfterCount(r.end_value!!.toInt())
            else -> ScheduleEnd.Never
        }
        val digits = q.selectCurrencies().executeAsList().firstOrNull { it.code == r.currency }?.digits?.toInt() ?: 2
        return RecurringItem(
            r.id, r.name, r.amount_minor?.let { Money(it, Currency(r.currency, digits)) }, CategoryId(r.category_id), AccountId(r.account_id), r.merchant,
            Schedule(RepeatUnit.valueOf(r.unit), r.every.toInt(), start, end), r.auto == 1L,
        )
    }

    private fun fieldsOf(i: RecurringItem): Map<String, String?> {
        val end = i.schedule.end
        return mapOf(
            "name" to i.name, "amount_minor" to i.amount?.minorUnits?.toString(), "currency" to (i.amount?.currency?.code ?: Currency.INR.code),
            "category_id" to i.category.value, "account_id" to i.account.value, "merchant" to i.merchant,
            "unit" to i.schedule.unit.name, "every" to i.schedule.every.toString(), "start_day" to i.schedule.start.toEpochDays().toString(),
            "end_kind" to when (end) { ScheduleEnd.Never -> "never"; is ScheduleEnd.OnOrBefore -> "date"; is ScheduleEnd.AfterCount -> "count" },
            "end_value" to when (end) { ScheduleEnd.Never -> null; is ScheduleEnd.OnOrBefore -> end.date.toEpochDays().toString(); is ScheduleEnd.AfterCount -> end.count.toString() },
            "auto" to if (i.auto) "1" else "0",
        )
    }

    companion object {
        const val DUE_WINDOW_DAYS = 60
    }
}

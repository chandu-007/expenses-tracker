package app.expenses.core.storage

import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.RecurringItem
import app.expenses.core.domain.RecurringKind
import app.expenses.core.domain.RepeatUnit
import app.expenses.core.domain.Schedule
import app.expenses.core.domain.ScheduleEnd
import app.expenses.core.domain.Template
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.Entry as DomainEntry
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.FriendPaid
import app.expenses.core.domain.ledger.Income
import app.expenses.core.domain.ledger.PersonId
import app.expenses.core.domain.ledger.Refund
import app.expenses.core.domain.ledger.Settlement
import app.expenses.core.domain.ledger.SettlementDirection
import app.expenses.core.domain.ledger.Share
import app.expenses.core.domain.ledger.Transfer
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.LocalDate

/** How many of each kind of thing, for a restore preview or result. */
data class BackupCounts(val accounts: Int = 0, val categories: Int = 0, val entries: Int = 0, val templates: Int = 0, val budgets: Int = 0, val recurring: Int = 0)

/** What a restore does: [added] is new to this device, [alreadyHere] is left exactly as it is. */
data class RestoreReport(val added: BackupCounts, val alreadyHere: BackupCounts)

/**
 * Reads everything the app keeps into a [BackupData] and puts one back.
 *
 * Restoring is a merge by id: anything whose id is already on this device (live or deleted) is left untouched, everything else is
 * added, all in one transaction, so restoring twice or restoring over live data never duplicates anything, never overwrites an edit
 * made since the backup, and never brings back something the user deleted. If anything fails nothing is changed.
 *
 * Not thread-safe: call through the process-wide write lock.
 */
class BackupStore(
    private val db: ExpensesDb,
    private val clock: HlcClock,
    private val nowMillis: () -> Long,
    private val ledger: LedgerStore,
    private val templates: TemplateStore,
    private val recurring: RecurringStore,
) {
    private val q get() = db.schemaQueries

    fun export(): BackupData {
        val splits = q.allSplits().executeAsList().groupBy { it.entry_id }
        val shares = q.allShares().executeAsList().groupBy { it.entry_id }
        val tags = q.allEntryTags().executeAsList().groupBy({ it.entry_id }, { it.tag })
        return BackupData(
            currencies = q.selectCurrencies().executeAsList().map { BackupCurrency(it.code, it.digits.toInt()) },
            accounts = q.allAccounts().executeAsList().map { BackupAccount(it.id, it.name, it.type, it.currency, it.opening_minor, it.archived == 1L) },
            // Live categories, and merged ones (as tombstones) so an edit that still uses an old id lands on its target after a restore.
            categories = q.allCategories().executeAsList().filter { it.deleted == 0L || it.merged_into != null }
                .map { BackupCategory(it.id, it.name, it.parent_id, it.color, it.position.toInt(), it.hidden == 1L, it.deleted == 1L, it.merged_into) },
            entries = q.liveEntriesInInsertionOrder().executeAsList().map { r ->
                BackupEntry(
                    r.id, r.kind, r.occurred_at, r.account_id, r.counter_account_id, r.amount_minor, r.currency, r.category_id, r.person_id,
                    r.to_amount_minor, r.to_currency, r.direction, r.refunds_entry, r.original_amount_minor, r.original_currency,
                    r.merchant, r.note, r.source, r.source_ref,
                    splits[r.id].orEmpty().map { BackupSplit(it.category_id, it.amount_minor) },
                    shares[r.id].orEmpty().map { BackupShare(it.person_id, it.amount_minor) },
                    tags[r.id].orEmpty(),
                )
            },
            templates = q.allTemplates().executeAsList().map { BackupTemplate(it.id, it.name, it.amount_minor, it.currency, it.category_id, it.account_id, it.merchant) },
            budgets = q.allBudgets().executeAsList().map { BackupBudget(it.scope, it.limit_minor, it.currency) },
            budgetPeriod = q.budgetPeriod().executeAsOneOrNull()?.let { BackupBudgetPeriod(it.kind, it.start_value) },
            recurring = q.liveRecurring().executeAsList().map {
                BackupRecurring(it.id, it.name, it.amount_minor, it.currency, it.category_id, it.account_id, it.merchant, it.unit, it.every.toInt(), it.start_day, it.end_kind, it.end_value, it.auto == 1L, it.kind, it.to_account_id)
            },
            savedSearches = q.liveSavedSearches().executeAsList().map { BackupSavedSearch(it.id, it.name, it.text, it.min_rupees, it.max_rupees, it.category_id, it.kind, it.range_kind, it.tag) },
            cardTerms = q.allCardTerms().executeAsList().map { BackupCardTerms(it.account_id, it.statement_day.toInt(), it.due_day.toInt(), it.min_due_percent.toInt(), it.remind_days.toInt()) },
            recurringDone = q.allRecurringDone().executeAsList().map { BackupDone(it.recurring_id, it.day, it.outcome, it.entry_id) },
            settings = SETTINGS.mapNotNull { k -> q.settingValue(k).executeAsOneOrNull()?.let { k to it } }.toMap(),
        )
    }

    /** What [restore] would do, without writing anything. */
    fun preview(data: BackupData): RestoreReport {
        val present = BackupCounts(
            accounts = data.accounts.count { q.accountById(it.id).executeAsOneOrNull() != null },
            categories = data.categories.count { q.categoryById(it.id).executeAsOneOrNull() != null },
            entries = data.entries.count { q.entryById(it.id).executeAsOneOrNull() != null },
            templates = data.templates.count { q.templateById(it.id).executeAsOneOrNull() != null },
            budgets = data.budgets.count { q.budgetByScope(it.scope).executeAsOneOrNull() != null },
            recurring = data.recurring.count { q.recurringById(it.id).executeAsOneOrNull() != null },
        )
        val total = BackupCounts(data.accounts.size, data.categories.size, data.entries.size, data.templates.size, data.budgets.size, data.recurring.size)
        return RestoreReport(
            added = BackupCounts(total.accounts - present.accounts, total.categories - present.categories, total.entries - present.entries, total.templates - present.templates, total.budgets - present.budgets, total.recurring - present.recurring),
            alreadyHere = present,
        )
    }

    /** Merges [data] into this device in one transaction. Throws, with nothing changed, if the data breaks a ledger rule. */
    fun restore(data: BackupData): RestoreReport = db.transactionWithResult {
        val report = preview(data)
        val digits = (data.currencies.associate { it.code to it.digits } + q.selectCurrencies().executeAsList().associate { it.code to it.digits.toInt() })
        fun currency(code: String) = Currency(code, digits[code] ?: error("The backup does not say how many decimal places $code has"))
        data.currencies.forEach { q.insertCurrency(it.code, it.digits.toLong()) }

        for (a in data.accounts) if (q.accountById(a.id).executeAsOneOrNull() == null) ledger.saveAccount(Account(AccountId(a.id), a.name, AccountType.valueOf(a.type), currency(a.currency), a.openingMinor, a.archived))
        // Parents before children, so a subcategory never refers to a category that is not there yet.
        for (c in data.categories.sortedBy { it.parentId != null }) {
            val here = q.categoryById(c.id).executeAsOneOrNull()
            when {
                here == null -> q.insertCategoryRaw(c.id, c.name, c.parentId, c.color, c.position.toLong(), if (c.hidden) 1 else 0, if (c.deleted) 1 else 0, c.mergedInto)
                // A fresh install already has the default categories. One nothing has touched takes the backup's version (renamed,
                // recoloured, hidden, or merged away); one that has been used or changed on this device is left alone.
                isUntouchedDefault(here) -> q.replaceCategory(c.name, c.parentId, c.color, c.position.toLong(), if (c.hidden) 1 else 0, if (c.deleted) 1 else 0, c.mergedInto, c.id)
            }
        }
        for (e in data.entries) if (q.entryById(e.id).executeAsOneOrNull() == null) ledger.save(toDomain(e, ::currency), EntryDetails(e.merchant, e.note, e.source, e.sourceRef, e.tags))
        for (t in data.templates) if (q.templateById(t.id).executeAsOneOrNull() == null) {
            templates.save(Template(t.id, t.name, t.amountMinor?.let { Money(it, currency(t.currency)) }, CategoryId(t.categoryId), AccountId(t.accountId), t.merchant))
        }
        for (b in data.budgets) if (q.budgetByScope(b.scope).executeAsOneOrNull() == null) writeBudget(db, clock, nowMillis, b.scope, Money(b.limitMinor, currency(b.currency)))
        data.budgetPeriod?.let { if (q.budgetPeriod().executeAsOneOrNull() == null) q.putBudgetPeriod(it.kind, it.startValue) }
        for (r in data.recurring) if (q.recurringById(r.id).executeAsOneOrNull() == null) recurring.save(toRecurring(r, ::currency))
        for (d in data.recurringDone) if (q.doneRow(d.recurringId, d.day).executeAsOneOrNull() == null) q.putDone(d.recurringId, d.day, d.outcome, d.entryId)
        // Added as they are (a search with the same name as one here is kept too, rather than the restore refusing).
        for (s in data.savedSearches) if (q.savedSearchById(s.id).executeAsOneOrNull() == null) q.upsertSavedSearch(s.id, s.name, s.text, s.minRupees, s.maxRupees, s.categoryId, s.kind, s.range, s.tag)
        // Only for a card that exists here and has no terms yet; the terms of a card that was not restored have nothing to attach to.
        for (c in data.cardTerms) if (q.cardTermsOf(c.accountId).executeAsOneOrNull() == null && q.accountById(c.accountId).executeAsOneOrNull()?.let { it.deleted == 0L && it.type == "CREDIT_CARD" } == true) q.upsertCardTerms(c.accountId, c.statementDay.toLong(), c.dueDay.toLong(), c.minDuePercent.toLong(), c.remindDaysBefore.toLong())
        for ((k, v) in data.settings) if (k in SETTINGS && q.settingValue(k).executeAsOneOrNull() == null) q.putSetting(k, v)
        report
    }

    private fun isUntouchedDefault(row: app.expenses.core.storage.db.Category): Boolean {
        val index = CategoryStore.DEFAULTS.indexOf(row.id)
        return index >= 0 && row.name == row.id && row.parent_id == null && row.color == null && row.position == index.toLong() && row.hidden == 0L &&
            row.deleted == 0L && row.merged_into == null && q.categoryUsage(row.id).executeAsOne() == 0L && q.budgetByScope(row.id).executeAsOneOrNull() == null
    }

    private fun toDomain(e: BackupEntry, currency: (String) -> Currency): DomainEntry {
        fun money(minor: Long, code: String) = Money(minor, currency(code))
        val id = EntryId(e.id)
        val amount = money(e.amountMinor, e.currency)
        fun account() = AccountId(e.accountId ?: throw IllegalArgumentException("Entry ${e.id} has no account"))
        val splits = e.splits.map { CategorySplit(CategoryId(it.categoryId), money(it.amountMinor, e.currency)) }
        return when (e.kind) {
            "EXPENSE" -> Expense(id, e.occurredAt, account(), amount, splits, e.shares.map { Share(it.personId?.let(::PersonId), money(it.amountMinor, e.currency)) },
                e.originalAmountMinor?.let { money(it, e.originalCurrency ?: throw IllegalArgumentException("Entry ${e.id} has no original currency")) })
            "INCOME" -> Income(id, e.occurredAt, account(), amount, e.categoryId?.let(::CategoryId))
            "TRANSFER" -> Transfer(id, e.occurredAt, account(), AccountId(e.counterAccountId ?: throw IllegalArgumentException("Entry ${e.id} has no second account")), amount,
                money(e.toAmountMinor ?: e.amountMinor, e.toCurrency ?: e.currency))
            "REFUND" -> Refund(id, e.occurredAt, account(), amount, EntryId(e.refundsEntry ?: throw IllegalArgumentException("Entry ${e.id} does not say what it refunds")))
            "FRIEND_PAID" -> FriendPaid(id, e.occurredAt, PersonId(e.personId ?: throw IllegalArgumentException("Entry ${e.id} has no payer")), amount, splits)
            "SETTLEMENT" -> Settlement(id, e.occurredAt, PersonId(e.personId ?: throw IllegalArgumentException("Entry ${e.id} has no person")), account(), amount,
                SettlementDirection.valueOf(e.direction ?: throw IllegalArgumentException("Entry ${e.id} has no direction")))
            else -> throw IllegalArgumentException("Entry ${e.id} has an unknown kind '${e.kind}'")
        }
    }

    private fun toRecurring(r: BackupRecurring, currency: (String) -> Currency): RecurringItem {
        val end = when (r.endKind) {
            "date" -> ScheduleEnd.OnOrBefore(LocalDate.fromEpochDays(r.endValue ?: throw IllegalArgumentException("${r.name} has no end date")))
            "count" -> ScheduleEnd.AfterCount((r.endValue ?: throw IllegalArgumentException("${r.name} has no count")).toInt())
            else -> ScheduleEnd.Never
        }
        return RecurringItem(
            r.id, r.name, r.amountMinor?.let { Money(it, currency(r.currency)) }, r.categoryId.ifEmpty { null }?.let(::CategoryId), AccountId(r.accountId), r.merchant,
            Schedule(RepeatUnit.valueOf(r.unit), r.every, LocalDate.fromEpochDays(r.startDay), end), r.auto, RecurringKind.valueOf(r.kind), r.toAccountId?.let(::AccountId),
        )
    }

    companion object {
        /** The only settings a backup carries: the notification choices. What was already shown is per device and is not restored. */
        val SETTINGS = listOf("notif_bills", "notif_budgets", "notif_quiet_on", "notif_quiet_start", "notif_quiet_end", "notif_bill_hour", "home_layout", "hide_amounts", "appearance", "base_currency")
    }
}

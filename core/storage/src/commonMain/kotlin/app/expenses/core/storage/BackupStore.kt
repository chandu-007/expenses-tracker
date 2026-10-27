package app.expenses.core.storage

import app.expenses.core.domain.AttachmentRules
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
data class BackupCounts(val accounts: Int = 0, val categories: Int = 0, val entries: Int = 0, val templates: Int = 0, val budgets: Int = 0, val recurring: Int = 0, val attachments: Int = 0)

/** What a restore does: [added] is new to this device, [alreadyHere] is left exactly as it is. [attached] are the ids of the files that were recorded as attachments by a restore. */
data class RestoreReport(val added: BackupCounts, val alreadyHere: BackupCounts, val attached: Set<String> = emptySet())

/**
 * Which files of a backup a restore would take ([wanted], by the ids this phone keeps) and how many it would leave alone, by reason: attached here already,
 * removed here earlier, for an entry that is not here or is deleted, no room (ten per entry), not valid (unsafe id, size, checksum text), or a repeat of an id already taken.
 */
data class AttachmentPlan(
    val wanted: Set<String> = emptySet(), val alreadyHere: Int = 0, val removedHere: Int = 0, val noEntry: Int = 0, val noRoom: Int = 0, val invalid: Int = 0, val repeated: Int = 0,
)

/** The photos and files on record for entries that are not deleted: how many, and their size together. */
data class AttachmentTotals(val count: Int, val bytes: Long)

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

    /** What a backup with files would carry. */
    fun attachmentTotals(): AttachmentTotals = q.liveAttachmentTotals().executeAsOne().let { AttachmentTotals(it.n.toInt(), it.bytes ?: 0L) }

    /**
     * Everything the app keeps. With [fileSize] (the size of a stored file by id, or a negative number when it is not on this phone) the photos and files
     * whose bytes are there, with the size on record, are listed in [BackupData.attachments]; the caller then supplies their bytes ([BackupFile.write]).
     */
    fun export(fileSize: ((String) -> Long)? = null): BackupData {
        val splits = q.allSplits().executeAsList().groupBy { it.entry_id }
        val shares = q.allShares().executeAsList().groupBy { it.entry_id }
        val tags = q.allEntryTags().executeAsList().groupBy({ it.entry_id }, { it.tag })
        return BackupData(
            currencies = q.selectCurrencies().executeAsList().map { BackupCurrency(it.code, it.digits.toInt()) },
            accounts = q.allAccounts().executeAsList().map { BackupAccount(it.id, it.name, it.type, it.currency, it.opening_minor, it.archived == 1L) },
            // Live categories, and merged ones (as tombstones) so an edit that still uses an old id lands on its target after a restore.
            categories = q.allCategories().executeAsList().filter { it.deleted == 0L || it.merged_into != null }
                .map { BackupCategory(it.id, it.name, it.parent_id, it.color, it.position.toInt(), it.hidden == 1L, it.deleted == 1L, it.merged_into, it.kind) },
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
            savedSearches = q.liveSavedSearches().executeAsList().map { BackupSavedSearch(it.id, it.name, it.text, it.min_rupees, it.max_rupees, it.category_id, it.kind, it.range_kind, it.tag, it.from_day, it.to_day, it.account_id) },
            cardTerms = q.allCardTerms().executeAsList().map { BackupCardTerms(it.account_id, it.statement_day.toInt(), it.due_day.toInt(), it.min_due_percent.toInt(), it.remind_days.toInt()) },
            accountLooks = q.allAccountLooks().executeAsList().map { BackupAccountLook(it.account_id, it.network, it.tier, it.last4) },
            captureRules = q.allCaptureRules().executeAsList().map { BackupCaptureRule(it.id, it.position.toInt(), it.pattern, it.category_id) },
            merchantMap = q.allMerchantMap().executeAsList().map { BackupMerchant(it.key, it.category_id, it.uses.toInt()) },
            people = q.allPeople().executeAsList().map { BackupPerson(it.id, it.name, it.deleted == 1L) },
            bills = q.allBills().executeAsList().map { BackupBill(it.id, it.issuer, it.kind, it.title, it.amount_minor, it.min_due_minor, it.currency, it.due_date, it.account_last4, it.reference, it.auto_debit == 1L, it.received_at, it.status, it.paid_at, it.dedupe_key) },
            origins = q.allLiveOrigins().executeAsList().map { BackupOrigin(it.entry_id, it.kind, it.sender, it.text, it.received_at) },
            recurringDone = q.allRecurringDone().executeAsList().map { BackupDone(it.recurring_id, it.day, it.outcome, it.entry_id) },
            attachments = if (fileSize == null) emptyList() else q.liveAttachments().executeAsList().filter { fileSize(it.id) == it.size_bytes }.map { BackupAttachment(it.id, it.entry_id, it.name, it.mime, it.size_bytes, it.sha256, it.created_at) },
            settings = (SETTINGS + app.expenses.core.domain.CurrencyChoices.ALL.map { RATE_PREFIX + it.code } + q.allCardTerms().executeAsList().map { CARD_LIMIT_PREFIX + it.account_id }).mapNotNull { k -> q.settingValue(k).executeAsOneOrNull()?.takeIf { it.isNotEmpty() }?.let { k to it } }.toMap(),
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
        val files = attachmentsPlan(data)
        return RestoreReport(
            added = BackupCounts(total.accounts - present.accounts, total.categories - present.categories, total.entries - present.entries, total.templates - present.templates, total.budgets - present.budgets, total.recurring - present.recurring, files.wanted.size),
            alreadyHere = present.copy(attachments = files.alreadyHere),
        )
    }

    /** The ids (as this phone keeps them: [BackupAttachment.localId]) of the files of [data] this device would take. See [attachmentsPlan]. */
    fun attachmentsWanted(data: BackupData): Set<String> = attachmentsPlan(data).wanted

    /**
     * Which files of [data] this device would take, and why it would leave the others alone. A file is taken when it is valid ([BackupAttachment.isRestorable]),
     * is the first with its id (a repeated id is left alone, whatever its bytes), is not attached here already, belongs to an entry that is (or, from this same
     * backup, will be) here and not deleted, has room under the limit per entry, and was not removed by the person since. A restore never brings back what was deleted.
     */
    fun attachmentsPlan(data: BackupData): AttachmentPlan {
        if (data.attachments.isEmpty()) return AttachmentPlan()
        val incoming = data.entries.mapTo(HashSet()) { it.id }
        val perEntry = HashMap<String, Int>()
        val seen = HashSet<String>()
        val wanted = LinkedHashSet<String>()
        var alreadyHere = 0; var removedHere = 0; var noEntry = 0; var noRoom = 0; var invalid = 0; var repeated = 0
        for (a in data.attachments) {
            if (!a.isRestorable()) { invalid++; continue }
            val id = a.localId()
            if (!seen.add(id)) { repeated++; continue }
            if (q.attachmentById(id).executeAsOneOrNull() != null) { alreadyHere++; continue }
            val entry = q.entryById(a.entryId).executeAsOneOrNull()
            if (if (entry == null) a.entryId !in incoming else entry.deleted != 0L) { noEntry++; continue }
            if (q.attachmentRemoved(id).executeAsOne() > 0L) { removedHere++; continue }
            val have = perEntry.getOrPut(a.entryId) { q.attachmentsOf(a.entryId).executeAsList().size }
            if (have >= AttachmentRules.MAX_PER_ENTRY) { noRoom++; continue }
            perEntry[a.entryId] = have + 1
            wanted += id
        }
        return AttachmentPlan(wanted, alreadyHere, removedHere, noEntry, noRoom, invalid, repeated)
    }

    /** Which of [ids] have an attachment record now, whoever wrote it: what a restore checks before removing a stored file that its own transaction did not record. */
    fun recordedAttachments(ids: Collection<String>): Set<String> = ids.filterTo(HashSet()) { q.attachmentById(it).executeAsOneOrNull() != null }

    /**
     * Merges [data] into this device in one transaction. Throws, with nothing changed, if the data breaks a ledger rule. [storedFiles] are the ids of listed
     * files whose bytes were already put in the app's file store (checked against their checksum); each is recorded as an attachment of its entry, and
     * [RestoreReport.attached] says which were, so the caller can remove any stored file that was not.
     */
    fun restore(data: BackupData, storedFiles: Set<String> = emptySet()): RestoreReport = db.transactionWithResult {
        val report = preview(data)
        val digits = (data.currencies.associate { it.code to it.digits } + q.selectCurrencies().executeAsList().associate { it.code to it.digits.toInt() })
        fun currency(code: String) = Currency(code, digits[code] ?: error("The backup does not say how many decimal places $code has"))
        data.currencies.forEach { q.insertCurrency(it.code, it.digits.toLong()) }

        for (a in data.accounts) if (q.accountById(a.id).executeAsOneOrNull() == null) ledger.saveAccount(Account(AccountId(a.id), a.name, AccountType.valueOf(a.type), currency(a.currency), a.openingMinor, a.archived))
        // Parents before children, so a subcategory never refers to a category that is not there yet.
        for (c in data.categories.sortedBy { it.parentId != null }) {
            val here = q.categoryById(c.id).executeAsOneOrNull()
            when {
                here == null -> q.insertCategoryRaw(c.id, c.name, c.parentId, c.color, c.position.toLong(), if (c.hidden) 1 else 0, if (c.deleted) 1 else 0, c.mergedInto, c.kind)
                // A fresh install already has the default categories. One nothing has touched takes the backup's version (renamed,
                // recoloured, hidden, or merged away); one that has been used or changed on this device is left alone.
                isUntouchedDefault(here) -> q.replaceCategory(c.name, c.parentId, c.color, c.position.toLong(), if (c.hidden) 1 else 0, if (c.deleted) 1 else 0, c.mergedInto, c.kind, c.id)
            }
        }
        // people before the entries that refer to them; one already here keeps its own name
        for (p in data.people) q.insertPersonRaw(p.id, p.name, if (p.deleted) 1 else 0)
        // a bill still waiting (FOUND) is not restored: it was never added, and its message is not in the file; a restore keeps what the person acted on
        for (b in data.bills.filter { it.status != BillStatus.FOUND.name }) q.insertBill(b.id, "backup", "", b.issuer, b.kind, b.title, b.amountMinor, b.minDueMinor, b.currency, b.dueDate, b.accountLast4, b.reference, if (b.autoDebit) 1L else 0L, b.receivedAt, null, b.status, null, b.paidAt, b.dedupeKey)
        for (e in data.entries) if (q.entryById(e.id).executeAsOneOrNull() == null) ledger.save(toDomain(e, ::currency), EntryDetails(e.merchant, e.note, e.source, e.sourceRef, e.tags))
        // an entry's origin is restored with the entry that was just added; one already here keeps its own
        for (o in data.origins) if (q.entryById(o.entryId).executeAsOneOrNull() != null && q.originOf(o.entryId).executeAsOneOrNull() == null && o.text.isNotBlank()) q.putOrigin(o.entryId, o.kind, o.sender, o.text, o.receivedAt)
        for (t in data.templates) if (q.templateById(t.id).executeAsOneOrNull() == null) {
            templates.save(Template(t.id, t.name, t.amountMinor?.let { Money(it, currency(t.currency)) }, CategoryId(t.categoryId), AccountId(t.accountId), t.merchant))
        }
        for (b in data.budgets) if (q.budgetByScope(b.scope).executeAsOneOrNull() == null) writeBudget(db, clock, nowMillis, b.scope, Money(b.limitMinor, currency(b.currency)))
        data.budgetPeriod?.let { if (q.budgetPeriod().executeAsOneOrNull() == null) q.putBudgetPeriod(it.kind, it.startValue) }
        for (r in data.recurring) if (q.recurringById(r.id).executeAsOneOrNull() == null) recurring.save(toRecurring(r, ::currency))
        for (d in data.recurringDone) if (q.doneRow(d.recurringId, d.day).executeAsOneOrNull() == null) q.putDone(d.recurringId, d.day, d.outcome, d.entryId)
        // Added as they are (a search with the same name as one here is kept too, rather than the restore refusing).
        for (s in data.savedSearches) if (q.savedSearchById(s.id).executeAsOneOrNull() == null) q.upsertSavedSearch(s.id, s.name, s.text, s.minRupees, s.maxRupees, s.categoryId, s.kind, s.range, s.tag, s.fromDay, s.toDay, s.accountId)
        // Rules and learned payees are added as they are where this phone has none with the same id or key (a restore never overrides a choice made here).
        val haveRules = q.allCaptureRules().executeAsList().map { it.id }.toSet()
        for (r in data.captureRules) if (r.id !in haveRules && q.categoryById(r.categoryId).executeAsOneOrNull() != null) q.upsertCaptureRule(r.id, r.position.toLong(), r.pattern, r.categoryId)
        val haveKeys = q.allMerchantMap().executeAsList().map { it.key }.toSet()
        for (m in data.merchantMap) if (m.key !in haveKeys && q.categoryById(m.categoryId).executeAsOneOrNull() != null) q.learnMerchant(m.key, m.categoryId)
        // Only for a card that exists here and has no terms yet; the terms of a card that was not restored have nothing to attach to.
        for (c in data.cardTerms) if (q.cardTermsOf(c.accountId).executeAsOneOrNull() == null && q.accountById(c.accountId).executeAsOneOrNull()?.let { it.deleted == 0L && it.type == "CREDIT_CARD" } == true) q.upsertCardTerms(c.accountId, c.statementDay.toLong(), c.dueDay.toLong(), c.minDuePercent.toLong(), c.remindDaysBefore.toLong())
        // Only for an account that exists here and has no look yet; names this version does not know, and digits that are not four, are dropped.
        for (l in data.accountLooks) if (q.accountLookOf(l.accountId).executeAsOneOrNull() == null && q.accountById(l.accountId).executeAsOneOrNull()?.deleted == 0L) {
            val network = l.network?.takeIf { n -> app.expenses.core.domain.CardNetwork.entries.any { it.name == n } }
            val tier = l.tier?.takeIf { t -> app.expenses.core.domain.CardTier.entries.any { it.name == t } }
            val digits = l.last4?.takeIf { app.expenses.core.domain.AccountLooks.parseLast4(it).isSuccess }
            if (network != null || tier != null || digits != null) q.upsertAccountLook(l.accountId, network, tier, digits)
        }
        for ((k, v) in data.settings) if ((k in SETTINGS || k.startsWith(CARD_LIMIT_PREFIX) || k.startsWith(RATE_PREFIX)) && q.settingValue(k).executeAsOneOrNull() == null) q.putSetting(k, v)
        // Files last, once their entries are here. Each is checked again inside this transaction: it may have been taken, or its entry may be deleted here.
        val attached = LinkedHashSet<String>()
        for (a in data.attachments) {
            // the record that passed the check is the one the file was stored for: the first valid one with this id, as in BackupTransfer.storeFiles
            val id = a.localId()
            if (!a.isRestorable() || id !in storedFiles || id in attached || q.attachmentById(id).executeAsOneOrNull() != null) continue
            if (q.entryById(a.entryId).executeAsOneOrNull()?.deleted != 0L) continue
            if (q.attachmentsOf(a.entryId).executeAsList().size >= AttachmentRules.MAX_PER_ENTRY) continue
            q.insertAttachment(id, a.entryId, AttachmentRules.safeName(a.name), a.safeMime(), a.sizeBytes, a.sha256, if (a.createdAt > 0) a.createdAt else nowMillis())
            q.insertOp(clock.now(nowMillis()).encode(), "attachment", id, "entry_id", a.entryId)
            attached += id
        }
        report.copy(added = report.added.copy(attachments = attached.size), attached = attached)
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
        val SETTINGS = listOf("notif_bills", "notif_budgets", "notif_quiet_on", "notif_quiet_start", "notif_quiet_end", "notif_bill_hour", "home_layout", "hide_amounts", "appearance", "base_currency", "capture_extra_roots",
            "goals", "budget_rollover", "notif_recap", "notif_near_percent", "notif_pace", "notif_wrapup", "notif_ahead3", "notif_ahead1", "notif_card_statement", "notif_card_limit")

        /** A card's credit limit is kept as `card_limit:<account id>`; a backup carries one for each card that has billing dates. */
        const val CARD_LIMIT_PREFIX = "card_limit:"

        /** The rate chosen last for a foreign currency, kept as `rate_<CODE>`. */
        const val RATE_PREFIX = "rate_"
    }
}

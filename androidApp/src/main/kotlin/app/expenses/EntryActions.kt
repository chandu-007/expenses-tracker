package app.expenses

import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.Currency
import app.expenses.core.domain.DraftKind
import app.expenses.core.domain.DraftResult
import app.expenses.core.domain.EntryDraft
import app.expenses.core.domain.HistoryFormat
import app.expenses.core.domain.HistoryLine
import app.expenses.core.domain.HistoryRestore
import app.expenses.core.domain.RevisionKind
import app.expenses.core.domain.EntryDrafts
import app.expenses.core.domain.Money
import app.expenses.core.domain.SplitDraft
import app.expenses.core.domain.TagRules
import app.expenses.core.domain.toPlainString
import app.expenses.core.domain.formatted
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.storage.EntryDetails
import java.util.UUID

private const val LAST_CATEGORY = "last_category"
private const val LAST_ACCOUNT = "last_account"

/** The income category used last, remembered apart from [LAST_CATEGORY] so a new income starts on an income category. */
internal const val LAST_INCOME_CATEGORY = "last_income_category"

/** An entry's history for the screen. */
data class HistoryUi(val entryId: String, val title: String, val revisions: List<HistoryRevisionUi>)
data class HistoryRevisionUi(val index: Int, val whenText: String, val what: String, val lines: List<HistoryLine>, val canRestore: Boolean)

/** What the edit screen may do with an entry. */
sealed interface Editable {
    /** An entry the form can edit (or, for a refund, create). [refundOf] is the title of the refunded expense; [canRefund] offers a refund from this expense. */
    data class Form(val draft: EntryDraft, val title: String, val refundOf: String? = null, val canRefund: Boolean = false) : Editable

    /** Shown read-only, with the reason it cannot be edited here yet, so nothing is changed by accident. */
    data class ReadOnly(val id: String, val title: String, val reason: String) : Editable
}

/**
 * The few things users do to the ledger in one step. Shared by the screens and the widget so both behave the same.
 * Call only from [AppContainer.write], which serialises writers.
 */
class EntryActions(private val ok: AppContainer.Opened.Ok, private val nowMillis: () -> Long = System::currentTimeMillis) {
    /** An expense that was just recorded: its id for undo, and the text to show the user. */
    data class Added(val entryId: String, val text: String)

    /** Records an expense and remembers its category and account as the next defaults. Returns its id. */
    fun recordExpense(amount: Money, category: String, note: String?, accountId: String, occurredAt: Long = nowMillis()): String {
        val id = UUID.randomUUID().toString()
        ok.store.save(
            Expense(EntryId(id), occurredAt, AccountId(accountId), amount, listOf(CategorySplit(CategoryId(category), amount))),
            EntryDetails(merchant = note),
        )
        ok.templates.putSettingIfChanged(LAST_CATEGORY, category)
        ok.templates.putSettingIfChanged(LAST_ACCOUNT, accountId)
        return id
    }

    /** Records the expense of a saved quick entry. Null if it does not exist or has no fixed amount. */
    fun quickAdd(templateId: String): Added? {
        val t = ok.templates.list().firstOrNull { it.id == templateId } ?: return null
        val amount = t.amount ?: return null
        val id = recordExpense(amount, t.category.value, t.merchant ?: t.name, t.account.value)
        ok.templates.recordUse(templateId)
        return Added(id, "Added ${t.name} ${amount.formatted()}")
    }

    /**
     * Takes back an entry. With [maxAgeMillis], only if it was recorded that recently, so a stale Undo button cannot
     * delete something the user has long since accepted. Returns whether anything was undone.
     */
    fun undo(entryId: String, maxAgeMillis: Long? = null): Boolean {
        val row = ok.db.schemaQueries.entryById(entryId).executeAsOneOrNull() ?: return false
        if (row.deleted == 1L) return false
        if (maxAgeMillis != null && nowMillis() - row.occurred_at > maxAgeMillis) return false
        ok.store.delete(EntryId(entryId))
        return true
    }

    /** Decides how an entry can be edited. Null if it does not exist or is deleted. */
    fun editable(entryId: String): Editable? {
        val q = ok.db.schemaQueries
        val row = q.entryById(entryId).executeAsOneOrNull()?.takeIf { it.deleted == 0L } ?: return null
        val title = row.merchant ?: row.note ?: row.kind.lowercase().replaceFirstChar { it.uppercase() }
        fun readOnly(reason: String) = Editable.ReadOnly(entryId, title, reason)
        if (row.currency != BaseCurrency.current.code) return readOnly("Only entries in ${BaseCurrency.current.code} can be edited here for now.")
        val base = EntryDraft(entryId, DraftKind.EXPENSE, Money(row.amount_minor, BaseCurrency.current).toPlainString(), "", emptyList(), row.account_id.orEmpty(), "", row.merchant.orEmpty(), row.occurred_at, tags = ok.store.tagsOf(EntryId(entryId)))
        return when (row.kind) {
            "EXPENSE" -> {
                val splits = q.splitsOf(entryId).executeAsList()
                when {
                    q.sharesOf(entryId).executeAsList().isNotEmpty() -> readOnly("This expense is shared with other people, which can't be edited here yet.")
                    row.original_amount_minor != null -> readOnly("This expense has a foreign-currency amount, which can't be edited here yet.")
                    q.hasLiveRefunds(entryId).executeAsOne() -> readOnly("This expense has refunds. Delete the refunds first to edit it.")
                    else -> Editable.Form(
                        base.copy(
                            category = if (splits.size == 1) splits[0].category_id else "",
                            splits = if (splits.size > 1) splits.map { SplitDraft(it.category_id, Money(it.amount_minor, BaseCurrency.current).toPlainString()) } else emptyList(),
                        ),
                        title = "Edit expense", canRefund = true,
                    )
                }
            }
            "INCOME" -> Editable.Form(base.copy(kind = DraftKind.INCOME, category = row.category_id.orEmpty()), "Edit income")
            "TRANSFER" -> Editable.Form(base.copy(kind = DraftKind.TRANSFER, toAccountId = row.counter_account_id.orEmpty()), "Edit transfer")
                .takeIf { row.to_currency == null || row.to_currency == BaseCurrency.current.code && row.to_amount_minor == row.amount_minor }
                ?: readOnly("This transfer is between currencies, which can't be edited here yet.")
            "REFUND" -> {
                val target = row.refunds_entry!!
                val refundable = refundableMinor(target, exceptRefund = entryId)
                Editable.Form(base.copy(kind = DraftKind.REFUND, refundsEntry = target, refundableMinor = refundable), "Edit refund", refundOf = titleOf(target))
            }
            "SETTLEMENT" -> Editable.Form(base.copy(kind = DraftKind.LOAN, personId = row.person_id.orEmpty(), gave = row.direction == "PAID_TO_PERSON", note = row.note.orEmpty()), "Edit lent or borrowed")
            else -> readOnly("This kind of entry can't be edited here yet.")
        }
    }

    private fun titleOf(entryId: String): String {
        val r = ok.db.schemaQueries.entryById(entryId).executeAsOneOrNull() ?: return "an expense"
        return r.merchant ?: r.note ?: "an expense"
    }

    /** What is still refundable on an expense: its amount less the other live refunds (the one being edited is excluded). */
    private fun refundableMinor(expenseId: String, exceptRefund: String?): Long {
        val q = ok.db.schemaQueries
        val total = q.entryById(expenseId).executeAsOneOrNull()?.amount_minor ?: return 0L
        return total - q.refundedExcept(expenseId, exceptRefund.orEmpty()).executeAsOne()
    }

    /** The form for refunding [expenseId]: its account and what is still refundable. Null if it is not a live expense. */
    fun startRefund(expenseId: String): Editable.Form? {
        val row = ok.db.schemaQueries.entryById(expenseId).executeAsOneOrNull()?.takeIf { it.deleted == 0L && it.kind == "EXPENSE" && it.currency == BaseCurrency.current.code } ?: return null
        val remaining = refundableMinor(expenseId, null)
        return Editable.Form(
            EntryDraft(null, DraftKind.REFUND, "", "", emptyList(), row.account_id.orEmpty(), "", "", nowMillis(), expenseId, remaining),
            "Refund", refundOf = titleOf(expenseId),
        )
    }

    /**
     * Saves a draft as a new entry ([EntryDraft.id] null) or over an existing one. When editing, the note, source and source reference
     * already stored with the entry are kept, so editing an expense captured from an SMS does not lose where it came from.
     * Throws [IllegalArgumentException] with a reason the user can read.
     */
    fun save(draft: EntryDraft): String {
        val existing = draft.id?.let { ok.db.schemaQueries.entryById(it).executeAsOneOrNull()?.takeIf { r -> r.deleted == 0L } ?: throw IllegalArgumentException("This entry no longer exists") }
        val id = draft.id ?: UUID.randomUUID().toString()
        val entry = when (val r = EntryDrafts.build(draft, EntryId(id), BaseCurrency.current)) {
            is DraftResult.Ok -> r.entry
            is DraftResult.Problem -> throw IllegalArgumentException(r.message)
        }
        // a lent or borrowed entry is titled by the person's name (so search finds it) and keeps what was typed as its note
        val loan = draft.kind == DraftKind.LOAN
        val text = if (loan) ok.people.get(draft.personId)?.name else draft.note.trim().ifEmpty { null }
        val typed = draft.note.trim().ifEmpty { null }
        ok.store.save(entry, if (existing != null) EntryDetails(merchant = text, note = if (loan) typed else existing.note, source = existing.source, sourceRef = existing.source_ref, tags = draft.tags) else EntryDetails(merchant = text, note = if (loan) typed else null, tags = draft.tags))
        // Correcting a captured entry later, in the ordinary editor, teaches the same lesson as choosing in the inbox: next time that payee starts there.
        if (existing?.source == "sms" && draft.kind == DraftKind.EXPENSE && !EntryDrafts.isSplit(draft) && draft.category.isNotEmpty()) ok.captureRules.learn(text, draft.category)
        if (draft.kind == DraftKind.EXPENSE) {
            val category = if (EntryDrafts.isSplit(draft)) "" else draft.category
            if (category.isNotEmpty()) {
                ok.templates.putSettingIfChanged(LAST_CATEGORY, category)
                ok.templates.putSettingIfChanged(LAST_ACCOUNT, draft.accountId)
            }
        }
        if (draft.kind == DraftKind.INCOME && draft.category.isNotEmpty()) ok.templates.putSettingIfChanged(LAST_INCOME_CATEGORY, draft.category)
        return id
    }

    /** What happened to an entry, newest first, worded for a person. Null if it never existed. */
    fun history(entryId: String): HistoryUi? {
        val revisions = ok.store.history(EntryId(entryId)).takeIf { it.isNotEmpty() } ?: return null
        val q = ok.db.schemaQueries
        val accounts = q.allAccounts().executeAsList().associate { it.id to it.name }
        val categories = q.allCategories().executeAsList().associate { it.id to it.name }
        val zone = java.time.ZoneId.systemDefault()
        val day = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy").withZone(zone)
        val stamp = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(zone)
        val fmt = HistoryFormat(BaseCurrency.current, { accounts[it] ?: "an account that was removed" }, { categories[it] ?: it }, { day.format(java.time.Instant.ofEpochMilli(it)) })
        val canRestore = editable(entryId) is Editable.Form
        val title = (editable(entryId) as? Editable.Form)?.title ?: "Entry"
        return HistoryUi(
            entryId, title,
            revisions.mapIndexed { i, r ->
                HistoryRevisionUi(
                    i, stamp.format(java.time.Instant.ofEpochMilli(r.atMillis)),
                    when (r.kind) { RevisionKind.CREATED -> "Created"; RevisionKind.EDITED -> "Edited"; RevisionKind.DELETED -> "Deleted"; RevisionKind.RESTORED -> "Brought back" },
                    fmt.lines(r.changes), // for the creation these are all the fields, each with no earlier value,
                    canRestore = canRestore && r.kind == RevisionKind.EDITED,
                )
            }.reversed(),
        )
    }

    /** Puts back the version of an entry as it was before revision [index] (an edit). It is saved as a new edit, so nothing is lost either way. */
    fun restoreVersion(entryId: String, index: Int) {
        val revisions = ok.store.history(EntryId(entryId))
        val r = revisions.getOrNull(index)?.takeIf { it.kind == RevisionKind.EDITED } ?: throw IllegalArgumentException("That change can't be undone any more.")
        val form = editable(entryId) as? Editable.Form ?: throw IllegalArgumentException("This entry can't be edited here.")
        save(HistoryRestore.apply(form.draft, r.before, BaseCurrency.current))
    }

    /**
     * What a bulk change did. [undo] puts everything back; [skipped] counts selected entries the change does not apply to, with [why].
     * [undoDrafts] are the earlier versions of changed entries, [undoRestore] the ids to bring back after a delete (in the order to restore them).
     */
    data class BulkResult(val changed: Int, val skipped: Int, val why: String?, val undoDrafts: List<EntryDraft> = emptyList(), val undoRestore: List<String> = emptyList()) {
        fun text(what: String): String {
            if (changed == 0 && skipped == 0) return "Nothing to change."
            val head = if (changed == 0) "Nothing changed." else "$what $changed ${if (changed == 1) "entry" else "entries"}."
            return if (skipped == 0) head else "$head $skipped skipped${why?.let { ": $it" }.orEmpty()}."
        }
    }

    /**
     * Sets the category of every selected entry that can take one (a plain expense or an income); split, shared, refund and transfer entries are skipped.
     * So is an entry the category is not for: an expense category is not applied to income, nor an income category to an expense (a category made for
     * both takes either). All or nothing.
     */
    fun bulkSetCategory(ids: List<String>, category: String): BulkResult {
        val kind = ok.categories.list().firstOrNull { it.id == category }?.kind
        return bulkEdit(ids, "split, shared, transfers and refunds keep their own categories") { d ->
            val income = d.kind == DraftKind.INCOME
            when {
                !(income || d.kind == DraftKind.EXPENSE && !EntryDrafts.isSplit(d)) -> Skip
                d.category == category -> null
                kind != null && !kind.serves(income) -> SkipFor(if (income) "income can't use an expense category" else "an expense can't use an income category")
                else -> d.copy(category = category)
            }
        }
    }

    /** Moves every selected entry to [account] (a transfer's "from" account). A transfer that would then go from an account to itself is skipped. All or nothing. */
    fun bulkSetAccount(ids: List<String>, account: String): BulkResult = bulkEdit(ids, "a transfer can't start and end in the same account, and shared or foreign-currency entries can't be edited here") { d ->
        if (d.accountId == account) null else d.copy(accountId = account)
    }

    /** Adds [tag] to every selected entry that can be edited here (up to ten tags each). All or nothing. */
    fun bulkAddTag(ids: List<String>, tag: String): BulkResult = bulkEdit(ids, "shared or foreign-currency entries can't be edited here, and an entry holds up to ${TagRules.MAX_PER_ENTRY} tags") { d ->
        val t = TagRules.normalize(tag) ?: return@bulkEdit Skip
        when {
            d.tags.any { it.equals(t, ignoreCase = true) } -> null
            d.tags.size >= TagRules.MAX_PER_ENTRY -> Skip
            else -> d.copy(tags = d.tags + t)
        }
    }

    /** Removes [tag] (ignoring case) from every selected entry that has it. */
    fun bulkRemoveTag(ids: List<String>, tag: String): BulkResult = bulkEdit(ids, "shared or foreign-currency entries can't be edited here") { d ->
        if (d.tags.none { it.equals(tag, ignoreCase = true) }) null else d.copy(tags = d.tags.filterNot { it.equals(tag, ignoreCase = true) })
    }

    private object Skip

    /** Skipped for its own reason, named in the result next to the general one. */
    private class SkipFor(val reason: String)

    private fun bulkEdit(ids: List<String>, why: String, change: (EntryDraft) -> Any?): BulkResult {
        val old = ArrayList<EntryDraft>(); val new = ArrayList<EntryDraft>(); var skipped = 0
        val reasons = LinkedHashSet<String>(); var general = false
        for (id in ids.distinct()) {
            val form = editable(id) as? Editable.Form
            if (form == null) { skipped++; general = true; continue }
            when (val r = change(form.draft)) {
                null -> Unit // already as wanted
                is EntryDraft -> if (EntryDrafts.build(r, EntryId(id), BaseCurrency.current) is DraftResult.Ok) { old += form.draft; new += r } else { skipped++; general = true }
                is SkipFor -> { skipped++; reasons += r.reason }
                else -> { skipped++; general = true }
            }
        }
        ok.store.atomically { new.forEach { save(it) } }
        if (general) reasons += why
        return BulkResult(new.size, skipped, reasons.joinToString("; ").takeIf { skipped > 0 }, undoDrafts = old)
    }

    /** Deletes the selected entries (each can be brought back). An expense with refunds goes only if its refunds are in the selection too. All or nothing. */
    fun bulkDelete(ids: List<String>): BulkResult {
        val q = ok.db.schemaQueries
        val live = ids.distinct().mapNotNull { id -> q.entryById(id).executeAsOneOrNull()?.takeIf { it.deleted == 0L } }
        val (refunds, others) = live.partition { it.kind == "REFUND" }
        val deleted = ArrayList<String>(); var skipped = ids.distinct().size - live.size
        ok.store.atomically {
            for (r in refunds + others) {
                if (r.kind == "EXPENSE" && q.hasLiveRefunds(r.id).executeAsOne()) { skipped++; continue }
                ok.store.delete(EntryId(r.id)); deleted += r.id
            }
        }
        return BulkResult(deleted.size, skipped, "an expense with refunds that are not selected can't be deleted".takeIf { skipped > 0 }, undoRestore = deleted.reversed())
    }

    /** Deletes an entry (it can be restored). Refused, with a message, while an expense still has refunds. */
    fun delete(entryId: String) = ok.store.delete(EntryId(entryId))

    fun restore(entryId: String) = ok.store.restore(EntryId(entryId))
}

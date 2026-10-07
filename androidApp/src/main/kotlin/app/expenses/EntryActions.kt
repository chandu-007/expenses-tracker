package app.expenses

import app.expenses.core.domain.Currency
import app.expenses.core.domain.DraftKind
import app.expenses.core.domain.DraftResult
import app.expenses.core.domain.EntryDraft
import app.expenses.core.domain.EntryDrafts
import app.expenses.core.domain.Money
import app.expenses.core.domain.SplitDraft
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
        ok.templates.putSetting(LAST_CATEGORY, category)
        ok.templates.putSetting(LAST_ACCOUNT, accountId)
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
        if (row.currency != "INR") return readOnly("Only rupee entries can be edited here for now.")
        val base = EntryDraft(entryId, DraftKind.EXPENSE, Money(row.amount_minor, Currency.INR).toPlainString(), "", emptyList(), row.account_id.orEmpty(), "", row.merchant.orEmpty(), row.occurred_at)
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
                            splits = if (splits.size > 1) splits.map { SplitDraft(it.category_id, Money(it.amount_minor, Currency.INR).toPlainString()) } else emptyList(),
                        ),
                        title = "Edit expense", canRefund = true,
                    )
                }
            }
            "INCOME" -> Editable.Form(base.copy(kind = DraftKind.INCOME, category = row.category_id.orEmpty()), "Edit income")
            "TRANSFER" -> Editable.Form(base.copy(kind = DraftKind.TRANSFER, toAccountId = row.counter_account_id.orEmpty()), "Edit transfer")
                .takeIf { row.to_currency == null || row.to_currency == "INR" && row.to_amount_minor == row.amount_minor }
                ?: readOnly("This transfer is between currencies, which can't be edited here yet.")
            "REFUND" -> {
                val target = row.refunds_entry!!
                val refundable = refundableMinor(target, exceptRefund = entryId)
                Editable.Form(base.copy(kind = DraftKind.REFUND, refundsEntry = target, refundableMinor = refundable), "Edit refund", refundOf = titleOf(target))
            }
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
        val row = ok.db.schemaQueries.entryById(expenseId).executeAsOneOrNull()?.takeIf { it.deleted == 0L && it.kind == "EXPENSE" && it.currency == "INR" } ?: return null
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
        val entry = when (val r = EntryDrafts.build(draft, EntryId(id), Currency.INR)) {
            is DraftResult.Ok -> r.entry
            is DraftResult.Problem -> throw IllegalArgumentException(r.message)
        }
        val text = draft.note.trim().ifEmpty { null }
        ok.store.save(entry, if (existing != null) EntryDetails(merchant = text, note = existing.note, source = existing.source, sourceRef = existing.source_ref) else EntryDetails(merchant = text))
        if (draft.kind == DraftKind.EXPENSE) {
            val category = if (EntryDrafts.isSplit(draft)) "" else draft.category
            if (category.isNotEmpty()) {
                ok.templates.putSetting(LAST_CATEGORY, category)
                ok.templates.putSetting(LAST_ACCOUNT, draft.accountId)
            }
        }
        return id
    }

    /** Deletes an entry (it can be restored). Refused, with a message, while an expense still has refunds. */
    fun delete(entryId: String) = ok.store.delete(EntryId(entryId))

    fun restore(entryId: String) = ok.store.restore(EntryId(entryId))
}

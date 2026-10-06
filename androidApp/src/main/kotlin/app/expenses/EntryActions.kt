package app.expenses

import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
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
    /** An expense simple enough for the one-screen editor. [category] is empty when the expense has none. */
    data class Simple(val id: String, val amountText: String, val category: String, val accountId: String, val note: String, val occurredAt: Long) : Editable

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
        if (row.kind != "EXPENSE") return readOnly("Only expenses can be edited here for now.")
        val splits = q.splitsOf(entryId).executeAsList()
        return when {
            q.sharesOf(entryId).executeAsList().isNotEmpty() -> readOnly("This expense is shared with other people, which can't be edited here yet.")
            splits.size > 1 -> readOnly("This expense is split across several categories, which can't be edited here yet.")
            row.original_amount_minor != null -> readOnly("This expense has a foreign-currency amount, which can't be edited here yet.")
            q.hasLiveRefunds(entryId).executeAsOne() -> readOnly("This expense has refunds. Delete the refunds first to edit it.")
            row.currency != "INR" -> readOnly("Only rupee expenses can be edited here for now.")
            else -> Editable.Simple(
                id = entryId,
                amountText = Money(row.amount_minor, Currency.INR).toPlainString(),
                category = splits.firstOrNull()?.category_id.orEmpty(),
                accountId = row.account_id.orEmpty(),
                note = row.merchant.orEmpty(),
                occurredAt = row.occurred_at,
            )
        }
    }

    /**
     * Rewrites a simple expense. The note, source and source reference stored with it are kept as they were, so editing an
     * expense captured from an SMS does not lose where it came from. An empty [category] leaves it uncategorised.
     */
    fun edit(entryId: String, amount: Money, category: String, note: String?, accountId: String, occurredAt: Long) {
        val row = ok.db.schemaQueries.entryById(entryId).executeAsOneOrNull()?.takeIf { it.deleted == 0L }
            ?: throw IllegalArgumentException("This entry no longer exists")
        require(row.kind == "EXPENSE") { "Only expenses can be edited here" }
        val splits = if (category.isEmpty()) emptyList() else listOf(CategorySplit(CategoryId(category), amount))
        ok.store.save(
            Expense(EntryId(entryId), occurredAt, AccountId(accountId), amount, splits),
            EntryDetails(merchant = note, note = row.note, source = row.source, sourceRef = row.source_ref),
        )
        if (category.isNotEmpty()) {
            ok.templates.putSetting(LAST_CATEGORY, category)
            ok.templates.putSetting(LAST_ACCOUNT, accountId)
        }
    }

    /** Deletes an entry (it can be restored). Refused, with a message, while an expense still has refunds. */
    fun delete(entryId: String) = ok.store.delete(EntryId(entryId))

    fun restore(entryId: String) = ok.store.restore(EntryId(entryId))
}

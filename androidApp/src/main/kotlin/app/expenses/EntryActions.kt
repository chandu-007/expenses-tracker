package app.expenses

import app.expenses.core.domain.Money
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

/**
 * The few things users do to the ledger in one step. Shared by the screens and the widget so both behave the same.
 * Call only from [AppContainer.write], which serialises writers.
 */
class EntryActions(private val ok: AppContainer.Opened.Ok, private val nowMillis: () -> Long = System::currentTimeMillis) {
    /** An expense that was just recorded: its id for undo, and the text to show the user. */
    data class Added(val entryId: String, val text: String)

    /** Records an expense and remembers its category and account as the next defaults. Returns its id. */
    fun recordExpense(amount: Money, category: String, note: String?, accountId: String): String {
        val id = UUID.randomUUID().toString()
        ok.store.save(
            Expense(EntryId(id), nowMillis(), AccountId(accountId), amount, listOf(CategorySplit(CategoryId(category), amount))),
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
}

package app.expenses.core.storage

import app.expenses.core.domain.BaseCurrencyRules
import app.expenses.core.domain.CurrencyChoices
import app.expenses.core.domain.Currency
import app.expenses.core.domain.LedgerFacts
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.db.ExpensesDb

/** Reads and changes the ledger's base currency (see [BaseCurrencyRules] for when it may change). */
class BaseCurrencyStore(private val db: ExpensesDb, private val clock: HlcClock, private val nowMillis: () -> Long, private val templates: TemplateStore, private val ledger: LedgerStore) {
    private val q get() = db.schemaQueries

    fun current(): Currency = CurrencyChoices.byCode(templates.setting(SETTING))

    fun facts() = LedgerFacts(
        anyEntryEver = q.entryCountEver().executeAsOne(),
        budgets = q.allBudgets().executeAsList().size,
        repeatingItems = q.liveRecurring().executeAsList().size,
        quickEntries = q.liveTemplates().executeAsList().size,
        accountsWithOpeningBalance = q.allAccounts().executeAsList().count { it.opening_minor != 0L },
    )

    /** Null on success, otherwise the reason it was refused (nothing changes). Every account moves to the new currency in one transaction. */
    fun change(to: Currency): String? {
        require(CurrencyChoices.ALL.any { it == to }) { "${to.code} is not offered" }
        var refusal: String? = null
        db.transaction {
            refusal = BaseCurrencyRules.blocker(facts())
            if (refusal != null) return@transaction
            ledger.registerCurrency(to)
            q.allAccounts().executeAsList().filter { it.currency != to.code }.forEach { a ->
                q.retargetAccountCurrency(to.code, a.id)
                q.insertOp(clock.now(nowMillis()).encode(), "account", a.id, "currency", to.code)
            }
            templates.putSetting(SETTING, to.code)
        }
        return refusal
    }

    companion object { const val SETTING = "base_currency" }
}

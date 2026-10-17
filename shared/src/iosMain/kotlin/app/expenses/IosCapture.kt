package app.expenses

import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.DraftKind
import app.expenses.core.domain.EntryDraft
import app.expenses.core.domain.Money
import app.expenses.core.domain.formatted
import app.expenses.core.domain.parseAmount

/**
 * What the Swift App Intents call (iosApp/Sources/Intents.swift). Both run in this app's own process, started in the background by Shortcuts or Siri
 * with no window, so everything here goes through [IosCore]: one database connection per process, whether or not the screen is up. When the screen is up
 * it learns of the change through [MessageIngest.changes] (the platform's `captureChanges`).
 *
 * Only plain values cross to Swift: each reply is a few strings and numbers, never the words of a message.
 */
object IosCapture {
    /** The answer of "Capture bank message". [message] is the sentence to show or speak; [code] is the outcome's name (CAPTURED, BILL, DUPLICATE, OTP, ..., or FAILED when the database could not be opened). */
    class CaptureReply(val message: String, val code: String, val kept: Boolean, val pending: Long)

    /** The answer of "Add expense". [message] is the sentence to show or speak. */
    class ExpenseReply(val added: Boolean, val message: String)

    /**
     * One message from a Shortcuts automation: [sender] is optional (null or blank when the automation did not pass it), [message] its words. Runs the same
     * pipeline as the Android receiver ([MessageIngest.ingest]): sender first, then the words; a message that is not a bank transaction is counted and dropped.
     */
    suspend fun captureMessage(host: IosHostServices, sender: String?, message: String): CaptureReply = ingest(IosCore.get(host), sender, message)

    internal suspend fun ingest(core: IosCore, sender: String?, message: String): CaptureReply {
        val r = MessageIngest.ingest(core.container, sender, message)
        val pending = core.container.write { it.inbox.pendingCount() } ?: 0L
        return CaptureReply(r.message, r.outcome?.name ?: "FAILED", r.kept, pending)
    }

    /** How many items wait in To review. */
    suspend fun pendingCount(host: IosHostServices): Long = IosCore.get(host).container.write { it.inbox.pendingCount() } ?: 0L

    /**
     * Records an expense now, as the Add screen would: in [categoryName] when given (an expense category, any case; one that does not exist is refused, so a
     * misheard word is not quietly filed somewhere else), else the category used last; in the account used last. [amountText] may carry a ₹ or "Rs".
     */
    suspend fun addExpense(host: IosHostServices, amountText: String, note: String?, categoryName: String?): ExpenseReply {
        val core = IosCore.get(host)
        val cleaned = amountText.trim().replace("₹", "").replace(Regex("(?i)^\\s*(?:rs\\.?|inr)\\s*"), "").trim()
        val result = core.container.write { ok ->
            val categories = ok.categories.list().filter { !it.hidden && it.kind.forExpense }
            val wanted = categoryName?.trim().orEmpty()
            val category = when {
                wanted.isNotEmpty() -> categories.firstOrNull { it.name.equals(wanted, ignoreCase = true) } ?: return@write ExpenseReply(false, "There is no expense category called $wanted in Expenses.")
                else -> ok.templates.setting("last_category")?.let { id -> categories.firstOrNull { it.id == id } } ?: categories.firstOrNull()
            }
            val accounts = ok.db.schemaQueries.allAccounts().executeAsList().filter { it.archived == 0L }
            val accountId = ok.templates.setting("last_account")?.takeIf { id -> accounts.any { it.id == id } } ?: accounts.firstOrNull()?.id ?: "cash"
            try {
                EntryActions(ok).save(EntryDraft(null, DraftKind.EXPENSE, cleaned, category?.id.orEmpty(), accountId = accountId, note = note?.trim().orEmpty(), occurredAt = nowMillis()))
            } catch (e: IllegalArgumentException) {
                return@write ExpenseReply(false, e.message ?: "That could not be added.")
            }
            val money = parseAmount(cleaned, BaseCurrency.current)?.let { Money(it.minorUnits, BaseCurrency.current).formatted() } ?: cleaned
            ExpenseReply(true, "Added $money" + (category?.let { " to ${it.name}" } ?: ""))
        } ?: ExpenseReply(false, "Expenses could not open its data, so nothing was added.")
        if (result.added) MessageIngest.changes.value += 1 // an open Home shows it at once
        return result
    }
}

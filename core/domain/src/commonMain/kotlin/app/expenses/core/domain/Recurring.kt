package app.expenses.core.domain

import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId
import kotlinx.datetime.LocalDate

/**
 * An expense that repeats. A null [amount] means the amount differs each time (an electricity bill), so it is asked for when the
 * occurrence is confirmed and cannot be [auto]-posted. With [auto] false every occurrence waits for the user to post or skip it.
 */
data class RecurringItem(
    val id: String,
    val name: String,
    val amount: Money?,
    val category: CategoryId,
    val account: AccountId,
    val merchant: String?,
    val schedule: Schedule,
    val auto: Boolean = false,
) {
    init {
        require(name.isNotBlank()) { "A repeating expense needs a name" }
        require(amount == null || amount.minorUnits > 0) { "The amount must be positive" }
        require(!auto || amount != null) { "An item without a fixed amount cannot post by itself" }
    }
}

/** One occurrence that is waiting: [item] is due on [date]. */
data class DueItem(val item: RecurringItem, val date: LocalDate)

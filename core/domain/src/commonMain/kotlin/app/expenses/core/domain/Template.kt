package app.expenses.core.domain

import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.CategoryId

/**
 * A saved quick entry such as "Chai, 20, Cash". With an [amount] one tap records an expense; without one it fills in
 * the category, account and note and asks only for the amount.
 */
data class Template(
    val id: String,
    val name: String,
    val amount: Money?,
    val category: CategoryId,
    val account: AccountId,
    val merchant: String? = null,
) {
    init {
        require(id.isNotEmpty()) { "Template id must not be empty" }
        require(name.isNotBlank()) { "Template needs a name" }
        require(amount == null || amount.minorUnits > 0) { "Template amount must be positive" }
    }
}

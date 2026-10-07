package app.expenses.core.domain

import app.expenses.core.domain.ledger.Account

/** Rules for naming and editing accounts, shared by every screen and by restore so they cannot disagree. */
object AccountRules {
    const val MAX_NAME = 40

    fun normalizeName(name: String) = name.trim().replace(Regex("\\s+"), " ")

    /** Why [name] cannot be used for an account, or null. Names are unique among accounts, ignoring case and extra spaces, archived ones included. */
    fun checkName(all: List<Account>, name: String, excludeId: String? = null): String? {
        val clean = normalizeName(name)
        return when {
            clean.isEmpty() -> "Give the account a name."
            clean.length > MAX_NAME -> "Keep the name to $MAX_NAME characters."
            else -> all.firstOrNull { it.id.value != excludeId && normalizeName(it.name).equals(clean, ignoreCase = true) }
                ?.let { "You already have an account called \"${normalizeName(it.name)}\"." }
        }
    }

    /** Whether the account's balance reads as "owed": a card or loan with a negative balance. */
    fun isOwed(account: Account, balanceMinor: Long) = account.type.isLiability && balanceMinor < 0
}

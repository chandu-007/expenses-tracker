package app.expenses.core.domain

/**
 * The categories the app starts with, in one place. A **new** install gets all four lists; an install that already exists keeps exactly what it has (nothing is added behind a person's
 * back, because that would change their reports), and can add any of the newer ones in one step from More > Categories > Suggested categories ([CategoryCatalog]).
 *
 * The first eight expense names and the first five income names ([EXPENSE_FIRST], [INCOME_FIRST]) are the original starters: their ids are fixed (the expense ones equal their names) and
 * entries recorded before categories could be managed point at them. The others ([EXPENSE_MORE], [INCOME_MORE]) were chosen from the 372-merchant starter table (which spends mostly on food, groceries,
 * transport, bills, shopping, health and fun, with insurers, telecom, streaming and fuel mixed into the coarse "Bills", "Transport" and "Fun") and from how people in India split their own spending:
 * rent, utilities and mobile apart from the rest of "Bills"; insurance, loan EMIs, investments, taxes and bank charges as their own lines because they are large and regular; subscriptions apart
 * from entertainment; travel and fuel apart from daily transport; cash taken out; education, personal care and gifts.
 *
 * **Investments (SIPs, mutual funds, stocks) are a category here, not a transfer.** Money put into a fund leaves the person's accounts for good as far as this app can tell (it keeps no
 * holdings), so recording it as an expense in "Investments" keeps their cash flow and the reports true, and shows how much they put aside. A person who tracks the fund as an account
 * of their own can instead make it an account and record a transfer to it; the app supports both.
 */
object StarterCategories {
    val EXPENSE_FIRST = listOf("Food", "Groceries", "Transport", "Bills", "Shopping", "Health", "Fun", "Other")
    val EXPENSE_MORE = listOf(
        "Rent", "Utilities", "Internet and mobile", "Insurance", "Education", "Fuel", "Travel", "Subscriptions", "Investments", "Loan EMI", "Personal care", "Gifts and donations", "Taxes",
        "Fees and charges", "Cash withdrawal",
    )
    val INCOME_FIRST = listOf("Salary", "Interest", "Gifts received", "Refunds and cashback", "Other income")
    val INCOME_MORE = listOf("Dividends", "Rent received", "Reimbursement", "Freelance and business")

    private fun slug(name: String) = name.lowercase().filter { it.isLetterOrDigit() }

    /** The fixed id of a starter category, the same on every device: an original expense starter is its name; the newer expense ones are `cat:<name>`; income ones are `income:<name>`. */
    fun expenseId(name: String): String = if (name in EXPENSE_FIRST) name else "cat:" + slug(name)
    fun incomeId(name: String): String = "income:" + slug(name)

    /** The expense starters in the order a new install lists them: the original eight where they have always been (so the first chips of every form are unchanged), then the newer ones. */
    val expenseOrder: List<String> = EXPENSE_FIRST + EXPENSE_MORE

    /** Where a starter sits in the list, for the position it is created at and for telling an untouched one from a changed one: the expense ones from 0, the income ones from 100. */
    fun expensePosition(name: String): Int = expenseOrder.indexOf(name)
    fun incomePosition(name: String): Int = 100 + (INCOME_FIRST + INCOME_MORE).indexOf(name)

    /** The ids a new install adds on top of the originals, with the name and position each is created with. */
    val newExpense: List<Triple<String, String, Int>> get() = EXPENSE_MORE.map { Triple(expenseId(it), it, expensePosition(it)) }
    val newIncome: List<Triple<String, String, Int>> get() = INCOME_MORE.map { Triple(incomeId(it), it, incomePosition(it)) }
}

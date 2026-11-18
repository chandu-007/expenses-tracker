package app.expenses.core.domain

/**
 * One line of the "Example month" a fresh install shows below the welcome card. Display-only: it has no id, no account and no entry kind, and nothing in the app accepts it where an
 * entry, a store or a total is expected. [minor] is in minor units of whatever currency the screen draws it in; [daysAgo] counts back from today (0 is today).
 */
data class ExampleRow(val merchant: String, val categoryName: String, val minor: Long, val daysAgo: Int, val income: Boolean = false)

/**
 * The made-up month drawn (dimmed, labelled "Example") under the welcome card so the first screen shows what the app will look like once it has data. These are constants for drawing and nothing else:
 * they are never written to the ledger, never counted in a total, a widget, a backup, a report or a notification, and are not built from `Expense`, `EntryId` or any store type.
 */
object ExampleMonth {
    /** Newest first, like Home's list. */
    val rows: List<ExampleRow> = listOf(
        ExampleRow("Chai", "Food", minor = 3_000, daysAgo = 0),
        ExampleRow("Auto", "Transport", minor = 14_000, daysAgo = 1),
        ExampleRow("Lunch", "Food", minor = 26_000, daysAgo = 2),
        ExampleRow("Groceries", "Groceries", minor = 186_000, daysAgo = 4),
        ExampleRow("Electricity", "Bills", minor = 142_000, daysAgo = 7),
        ExampleRow("Rent", "Rent", minor = 1_400_000, daysAgo = 10),
        ExampleRow("Salary", "Salary", minor = 5_200_000, daysAgo = 12, income = true),
    )

    /** The days the small line covers. */
    const val LINE_DAYS = 14

    /** What the month card says was spent: the rows that are spending, added up. */
    val spentMinor: Long = rows.filter { !it.income }.sumOf { it.minor }

    /** What came in. */
    val incomeMinor: Long = rows.filter { it.income }.sumOf { it.minor }

    /** Spending on each of the last [LINE_DAYS] days, oldest first (the last figure is today): the points of the dashed line. */
    val dailyMinor: List<Long> = List(LINE_DAYS) { i -> val ago = LINE_DAYS - 1 - i; rows.filter { !it.income && it.daysAgo == ago }.sumOf { it.minor } }

    /** The names an example row can have, for proving none of them ever reaches a real ledger. */
    val merchants: List<String> = rows.map { it.merchant }
}

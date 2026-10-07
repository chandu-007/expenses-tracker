package app.expenses.core.domain

/**
 * The header of this app's own CSV export. The export is for reading in a spreadsheet; it leaves out what a restore needs (refund links,
 * split amounts, opening balances, budgets), so it is not a way back in. Importing one as a bank statement is recognised and explained
 * instead of producing rows that make no sense; the JSON backup is the lossless way to move data between phones.
 */
object OwnExport {
    val HEADER = listOf("Date", "Time", "Type", "Amount", "Currency", "Your share", "Category", "Account", "To account", "Person", "Merchant", "Note", "Source")

    fun matches(rows: List<List<String>>): Boolean = rows.firstOrNull()?.map { it.trim() } == HEADER

    const val MESSAGE = "This file was exported from this app. A CSV export is for spreadsheets and does not keep everything, so it can't be imported back. " +
        "To move your data to another phone, make a backup (More, then Your data, then Back up) and restore it there."
}

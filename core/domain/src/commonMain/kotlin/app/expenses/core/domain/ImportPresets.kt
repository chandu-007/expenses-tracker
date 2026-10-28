package app.expenses.core.domain

/**
 * Recognises a file exported by another expense app by its column titles, and reads it by what those columns mean, so it imports in one tap with its categories,
 * accounts, notes and labels, instead of as a bank statement whose columns have to be chosen.
 *
 * What is known about each layout, and from where (**no real export of any of these was available; the layouts are those the apps and the people who read
 * their files describe, and a file that differs is simply not recognised and falls back to the generic reader**):
 *  - Money Manager (Realbyte): the columns of the import template in the maker's help centre, "Date, Account, Category, Subcategory, Note, Amount, Income/Expense,
 *    Description"; dates month first; the types are Income, Expense and Transfer-Out.
 *  - YNAB: the maker's file guide names Date, Payee, Memo, Outflow and Inflow; a register export also has Account and Category columns; the date follows the
 *    plan's setting, so it is detected.
 *  - Splitwise: Date, Description, Category, Cost and Currency, then a column per group member (a third-party description; sources disagree about the sign of the
 *    member columns, so they are not read). The cost is the whole bill, not the person's share.
 *  - Cashew: the columns of its CSV (account, amount, currency, title, note, date, income, type, category name, subcategory name...), as a third-party report tool
 *    documents them; the `income` column says which way, the amount's own sign is not relied on.
 *  - Spendee: Date, Wallet, Type, Category name, Amount, Currency, Note, Labels, Author, as a third-party converter's sample shows them.
 * Not here: Wallet by BudgetBakers (its sources disagree on the column titles and its export is a paid feature) and Axio/Walnut (no documented export format).
 *
 * A row that moves money between the person's own accounts (a transfer, a Cashew balance correction) is left out and counted, since the person's accounts here are
 * not those of the file; a row in another currency than the app's is left out and counted.
 */
object ImportPresets {
    enum class App(val label: String) { MONEY_MANAGER("Money Manager"), YNAB("YNAB"), SPLITWISE("Splitwise"), CASHEW("Cashew"), SPENDEE("Spendee") }

    /** The header of the table a recognised file is turned into: one row per transaction with an amount that has no sign and a Dr/Cr mark. */
    val CANONICAL_HEADER = listOf("Date", "Description", "Amount", "Type", "Category", "Subcategory", "Account", "Note", "Labels")

    /**
     * [table] is the recognised file as a table with the header [CANONICAL_HEADER] (rows of transactions only, with the line each came from in the file), [rawLines]
     * the file's own row for each of its rows (the header's is empty), [mapping] how to read it. The counts are of the rows left out: [transfers] between the
     * person's own accounts, [otherCurrency], [unrecognised] (an Income/Expense/Transfer value that is none of the known ones, in any language: such a row is never
     * guessed to be spending) and [settleUps] (Splitwise payments between friends, which settle a debt and are not spending).
     */
    class Read(
        val app: App, val table: CsvTable, val rawLines: List<String>, val mapping: StatementMapping,
        val transfers: Int, val otherCurrency: Int, val note: String?, val dateOrderKnown: Boolean, val unrecognised: Int = 0, val settleUps: Int = 0,
    ) {
        /** What was left out, in words, or null when nothing was. */
        val leftOut: String? get() = listOfNotNull(
            transfers.takeIf { it > 0 }?.let { "$it ${if (it == 1) "transfer between your own accounts" else "transfers between your own accounts"}" },
            otherCurrency.takeIf { it > 0 }?.let { "$it ${if (it == 1) "row" else "rows"} in another currency" },
            unrecognised.takeIf { it > 0 }?.let { "$it ${if (it == 1) "row" else "rows"} whose Income/Expense/Transfer value was not recognised" },
            settleUps.takeIf { it > 0 }?.let { "$it ${if (it == 1) "settle-up payment" else "settle-up payments"} between friends" },
        ).takeIf { it.isNotEmpty() }?.joinToString(", ")?.let { "Left out: $it." }
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    private class Columns(header: List<String>) {
        private val names = header.map(::norm)
        fun find(vararg keys: String): Int? = keys.firstNotNullOfOrNull { k -> names.indexOf(k).takeIf { it >= 0 } }
        fun has(vararg keys: String) = keys.all { find(it) != null }
    }

    private const val HEADER_SEARCH_ROWS = 15
    private const val SAMPLE_ROWS = 200

    /** The layout of [rows] when it is a recognised app's export, read with [digits] decimals for [baseCurrency]; null otherwise. */
    fun read(rows: List<List<String>>, lines: List<Int>, baseCurrency: String, digits: Int): Read? {
        for (h in 0 until minOf(HEADER_SEARCH_ROWS, rows.size)) {
            val c = Columns(rows[h])
            val app = when {
                c.has("date", "account", "category", "subcategory", "income expense", "amount") -> App.MONEY_MANAGER
                c.has("date", "payee", "memo", "outflow", "inflow") -> App.YNAB
                c.has("amount", "currency", "title", "date", "income", "category name") -> App.CASHEW
                c.has("date", "wallet", "category name", "amount", "currency") -> App.SPENDEE
                rows[h].map(::norm).take(5) == listOf("date", "description", "category", "cost", "currency") -> App.SPLITWISE
                else -> null
            } ?: continue
            return convert(app, c, rows, lines, h, baseCurrency, digits)
        }
        return null
    }

    private sealed interface Out {
        class Txn(val date: String, val description: String, val amount: String, val debit: Boolean, val category: String, val subcategory: String, val account: String, val note: String, val labels: String) : Out
        data object Transfer : Out
        data object OtherCurrency : Out
        data object Unrecognised : Out
        data object SettleUp : Out
    }

    private val SYMBOLS = Regex("[$€£¥₩₽]")

    private fun convert(app: App, c: Columns, rows: List<List<String>>, lines: List<Int>, header: Int, baseCurrency: String, digits: Int): Read {
        fun cell(r: List<String>, col: Int?) = col?.let { r.getOrNull(it) }.orEmpty().trim()
        /** Some apps write more decimals than the currency has ("154.35000000"); zeros beyond it are no part of the amount. */
        fun withoutExtraZeros(t: String): String {
            val dot = t.lastIndexOf('.')
            if (dot < 0) return t
            val extra = t.length - dot - 1 - digits
            return if (extra > 0 && t.takeLast(extra).all { it == '0' }) t.dropLast(extra).removeSuffix(".") else t
        }
        fun amountOf(text: String) = StatementAmounts.parse(withoutExtraZeros(text.replace(SYMBOLS, "").trim()), digits)
        /** An amount cell as plain digits, no sign, or the cell as it was when it cannot be read (the parser then reports it). */
        fun plain(text: String): String {
            val a = amountOf(text)
            return if (a is AmountCell.Value) {
                val unit = pow10(digits)
                if (digits == 0) a.minor.toString() else "${a.minor / unit}.${(a.minor % unit).toString().padStart(digits, '0')}"
            } else text
        }
        fun negative(text: String) = (amountOf(text) as? AmountCell.Value)?.negative == true
        fun positive(text: String) = (amountOf(text) as? AmountCell.Value)?.let { it.minor > 0 && !it.negative } == true

        val date = c.find("date"); val account = c.find("account"); val wallet = c.find("wallet")
        val category = c.find("category", "category name"); val sub = c.find("subcategory", "subcategory name"); val currency = c.find("currency")
        val noteCol = c.find("note"); val description = c.find("description"); val amount = c.find("amount", "cost")
        val type = c.find("income expense", "type"); val labels = c.find("labels")
        val payee = c.find("payee"); val memo = c.find("memo"); val outflow = c.find("outflow"); val inflow = c.find("inflow")
        val title = c.find("title"); val income = c.find("income")

        var transfers = 0; var other = 0; var unknown = 0; var settle = 0
        val kept = ArrayList<Int>(); val out = ArrayList<Out.Txn>()
        for (i in header + 1 until rows.size) {
            val r = rows[i]
            if (r.all { it.isBlank() }) continue
            val dateText = cell(r, date)
            val result: Out = when (app) {
                App.MONEY_MANAGER -> {
                    val t = cell(r, type).lowercase()
                    val primary = cell(r, noteCol); val secondary = cell(r, description)
                    when {
                        t.startsWith("transfer") -> Out.Transfer
                        // some exports carry a currency column: an amount in another currency is not read as this one's
                        cell(r, currency).isNotEmpty() && !cell(r, currency).equals(baseCurrency, ignoreCase = true) -> Out.OtherCurrency
                        !t.startsWith("income") && !t.startsWith("expense") -> Out.Unrecognised
                        else -> Out.Txn(
                            dateText, primary.ifEmpty { secondary }, plain(cell(r, amount)), t.startsWith("expense"), cell(r, category), cell(r, sub), cell(r, account),
                            if (primary.isNotEmpty() && secondary != primary) secondary else "", "",
                        )
                    }
                }
                App.YNAB -> {
                    val payeeText = cell(r, payee)
                    val out1 = cell(r, outflow); val in1 = cell(r, inflow)
                    when {
                        payeeText.startsWith("Transfer :") || payeeText.startsWith("Transfer:") -> Out.Transfer
                        positive(in1) && !positive(out1) -> Out.Txn(dateText, payeeText, plain(in1), false, cell(r, category), "", cell(r, account), cell(r, memo), "")
                        else -> Out.Txn(dateText, payeeText, plain(out1.ifBlank { in1 }), true, cell(r, category), "", cell(r, account), cell(r, memo), "")
                    }
                }
                App.SPLITWISE -> when {
                    cell(r, category).equals("Payment", ignoreCase = true) -> Out.SettleUp
                    cell(r, currency).isNotEmpty() && !cell(r, currency).equals(baseCurrency, ignoreCase = true) -> Out.OtherCurrency
                    else -> Out.Txn(dateText, cell(r, description), plain(cell(r, amount)), true, cell(r, category), "", "", "", "")
                }
                App.CASHEW -> {
                    val cat = cell(r, category)
                    when {
                        cell(r, currency).isNotEmpty() && !cell(r, currency).equals(baseCurrency, ignoreCase = true) -> Out.OtherCurrency
                        cat.equals("Balance Correction", ignoreCase = true) -> Out.Transfer
                        else -> Out.Txn(dateText, cell(r, title), plain(cell(r, amount)), !cell(r, income).equals("true", ignoreCase = true), cat, cell(r, sub), cell(r, account), cell(r, noteCol), "")
                    }
                }
                App.SPENDEE -> {
                    val t = cell(r, type).lowercase()
                    val a = cell(r, amount)
                    when {
                        t.contains("transfer") -> Out.Transfer
                        cell(r, currency).isNotEmpty() && !cell(r, currency).equals(baseCurrency, ignoreCase = true) -> Out.OtherCurrency
                        t.isNotEmpty() && !t.startsWith("income") && !t.startsWith("expense") -> Out.Unrecognised
                        else -> {
                            val debit = if (t.isNotEmpty()) t.startsWith("expense") else negative(a)
                            Out.Txn(dateText, cell(r, noteCol).ifEmpty { cell(r, category) }, plain(a), debit, cell(r, category), "", cell(r, wallet), "", cell(r, labels))
                        }
                    }
                }
            }
            when (result) {
                is Out.Txn -> { kept += i; out += result }
                Out.Transfer -> transfers++
                Out.OtherCurrency -> other++
                Out.Unrecognised -> unknown++
                Out.SettleUp -> settle++
            }
        }

        val canonical = ArrayList<List<String>>(out.size + 1).also { it += CANONICAL_HEADER }
        val canonicalLines = ArrayList<Int>(out.size + 1).also { it += lines.getOrElse(header) { header + 1 } }
        val raw = ArrayList<String>(out.size + 1).also { it += "" }
        for ((k, t) in out.withIndex()) {
            canonical += listOf(t.date, t.description, t.amount, if (t.debit) "Dr" else "Cr", t.category, t.subcategory, t.account, t.note, t.labels)
            canonicalLines += lines.getOrElse(kept[k]) { kept[k] + 1 }
            raw += rows[kept[k]].dropLastWhile { it.isBlank() }.joinToString(", ")
        }
        val documented = if (app == App.MONEY_MANAGER) DateOrder.MDY else null
        val samples = out.take(SAMPLE_ROWS).map { it.date }.filter { it.isNotBlank() }
        val order = documented?.takeIf { d -> samples.isNotEmpty() && samples.all { StatementDates.parse(it, d) != null } } ?: StatementDates.detect(samples)?.order ?: documented ?: DateOrder.DMY
        val mapping = StatementMapping(
            headerRows = 1, dateColumn = 0, dateOrder = order, descriptionColumn = 1, layout = AmountLayout.AMOUNT_WITH_TYPE, amountColumn = 2, typeColumn = 3,
            categoryColumn = 4, subcategoryColumn = 5, accountColumn = 6, noteColumn = 7, tagsColumn = 8,
        )
        val note = when (app) {
            App.SPLITWISE -> "Splitwise lists the whole cost of each bill, not your share of it. Settle-up payments between friends are left out. Check the amounts."
            App.YNAB -> "Transfers between your YNAB accounts are left out."
            else -> null
        }
        return Read(app, CsvTable(canonical, ',', false, canonicalLines), raw, mapping, transfers, other, note, dateOrderKnown = documented != null && order == documented, unrecognised = unknown, settleUps = settle)
    }

    private fun pow10(n: Int): Long { var v = 1L; repeat(n) { v *= 10 }; return v }
}

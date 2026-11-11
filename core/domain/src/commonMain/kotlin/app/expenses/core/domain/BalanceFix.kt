package app.expenses.core.domain

import app.expenses.core.domain.ledger.AccountType
import kotlin.math.absoluteValue

/**
 * One time the person told the app what an account really holds ("Fix this balance"): [beforeMinor] is the balance the app showed, [afterMinor] the one it shows now, both signed in minor
 * units (negative is owed on a card or loan), and [atMillis] when. The difference moved the account's opening balance and nothing else: no entry was made, so no spending or income
 * total, budget or report changed.
 */
data class BalanceFixEntry(val atMillis: Long, val beforeMinor: Long, val afterMinor: Long) {
    /** How far the balance (and the opening balance) moved. Never overflows: both amounts are within [BalanceFix.LIMIT]. */
    val deltaMinor: Long get() = afterMinor - beforeMinor
}

/**
 * What the app remembers of the fixes of one account: the newest [KEEP], kept as one line of text in a setting named [PREFIX] and the account's id (as a card's credit limit is), so no
 * table changed. A line is `at,before,after`; lines are separated by `;`. Anything that does not read as a line, or is out of range, is dropped.
 */
object BalanceFixLog {
    const val PREFIX = "balance_fix:"
    const val KEEP = 20

    fun key(accountId: String) = PREFIX + accountId

    fun parse(text: String?): List<BalanceFixEntry> = text.orEmpty().split(';').mapNotNull { line ->
        val p = line.trim().split(',')
        if (p.size != 3) return@mapNotNull null
        val at = p[0].toLongOrNull() ?: return@mapNotNull null
        val before = p[1].toLongOrNull() ?: return@mapNotNull null
        val after = p[2].toLongOrNull() ?: return@mapNotNull null
        if (at < 0 || before !in -BalanceFix.LIMIT..BalanceFix.LIMIT || after !in -BalanceFix.LIMIT..BalanceFix.LIMIT || before == after) null else BalanceFixEntry(at, before, after)
    }.takeLast(KEEP)

    fun format(entries: List<BalanceFixEntry>): String = entries.takeLast(KEEP).joinToString(";") { "${it.atMillis},${it.beforeMinor},${it.afterMinor}" }

    /** [entries] with [added] last (the newest), still at most [KEEP]. */
    fun add(entries: List<BalanceFixEntry>, added: BalanceFixEntry): List<BalanceFixEntry> = (entries + added).takeLast(KEEP)

    /** [entries] without the one made at [atMillis] (an Undo takes the line of the fix it undoes away). */
    fun remove(entries: List<BalanceFixEntry>, atMillis: Long): List<BalanceFixEntry> = entries.filterNot { it.atMillis == atMillis }
}

/**
 * The arithmetic of "Fix this balance". The person says what the bank app or the statement shows; the difference from what the app shows is added to the account's opening balance.
 * Every balance of the account, at every date, moves by that difference, and the entries are not touched.
 */
object BalanceFix {
    /**
     * The largest amount handled, in minor units: 2^53 - 1 (about 90 trillion rupees). The balance queries return a balance as a double, which holds every whole number up to there
     * exactly and nothing beyond, so a balance past it could not be fixed to the paisa. The amount field itself takes up to 17 digits (see [parseAmount]); a larger amount is refused in words.
     */
    const val LIMIT = 9_007_199_254_740_991L

    /**
     * The signed balance of an account when the bank app says [typedMinor] (never negative). [other] is the second choice of the screen: for a card or loan "in credit" (the bank owes you),
     * for any other account "overdrawn". Without it the amount is what is owed (card, loan) or what is held (the rest). Zero is zero either way.
     */
    fun statedBalance(type: AccountType, typedMinor: Long, other: Boolean): Long =
        if (type.isLiability != other) -typedMinor else typedMinor

    /** What the person typed, read: nothing yet, something that cannot be used (with the reason, in plain words) or an amount. */
    sealed interface Input {
        data object Empty : Input
        data class Bad(val message: String) : Input
        data class Amount(val minor: Long) : Input
    }

    private const val FOREIGN_SYMBOLS = "\$€£¥₩₽₿₹"

    /** Whether [t] starts or ends with the code of one of the currencies the app knows ("USD 50", "50 usd"). */
    private fun hasCurrencyCode(t: String): Boolean {
        val head = t.takeWhile { it.isLetter() }
        val tail = t.takeLastWhile { it.isLetter() }
        return CurrencyChoices.ALL.any { it.code.equals(head, ignoreCase = true) || it.code.equals(tail, ignoreCase = true) }
    }

    fun read(text: String, currency: Currency): Input {
        var t = text.trim()
        if (t.isEmpty()) return Input.Empty
        val own = currency.symbolOrCode()
        // the ledger's own symbol or code, before or after the number ("₹5,000", "Rs. 5000", "5000 INR"), is fine
        val ownWords = listOfNotNull(own, currency.code, if (currency.code == "INR") "Rs." else null, if (currency.code == "INR") "Rs" else null)
        for (w in ownWords) {
            if (t.startsWith(w, ignoreCase = true)) { t = t.substring(w.length).trim(); break }
            if (t.endsWith(w, ignoreCase = true)) { t = t.dropLast(w.length).trim(); break }
        }
        if (t.isEmpty()) return Input.Empty
        if (t.startsWith("-") || t.startsWith("−") || t.startsWith("(")) return Input.Bad("Type the amount without a minus sign. Use the choice below for \"in credit\" or \"overdrawn\".")
        if (t.any { it in FOREIGN_SYMBOLS && it.toString() != own } || hasCurrencyCode(t)) {
            return Input.Bad("This ledger is in $own, so only $own amounts can be used here.")
        }
        val parsed = parseAmount(t, currency)
            ?: return Input.Bad(if (currency.minorUnitDigits == 0) "Enter the amount as a number, like 25000" else "Enter the amount as a number, like 25000 or 1250.50")
        return Input.Amount(parsed.minorUnits)
    }

    /** The change an account's balance needs: [currentMinor] is what the app shows, [statedMinor] what the bank says. [deltaMinor] is how far the opening balance moves (zero: already right). */
    data class Plan(val currentMinor: Long, val statedMinor: Long) {
        val deltaMinor: Long get() = statedMinor - currentMinor
        val matches: Boolean get() = deltaMinor == 0L
    }

    /** The plan for the two balances, or null when either is beyond [LIMIT]. */
    fun plan(currentMinor: Long, statedMinor: Long): Plan? =
        if (currentMinor !in -LIMIT..LIMIT || statedMinor !in -LIMIT..LIMIT) null else Plan(currentMinor, statedMinor)

    /** The opening balance after moving it by [deltaMinor], or null when that would pass [LIMIT] (or the opening balance was already beyond it). */
    fun newOpening(openingMinor: Long, deltaMinor: Long): Long? {
        if (openingMinor !in -LIMIT..LIMIT || deltaMinor !in -2 * LIMIT..2 * LIMIT) return null
        val sum = openingMinor + deltaMinor
        return if (sum.absoluteValue > LIMIT) null else sum
    }

    const val TOO_LARGE = "That amount is too large for the app to hold."
    const val ALREADY_MATCHES = "Already matches: the app shows the same amount."
    const val EMPTY = "Type what your bank app or statement shows."
    const val NO_ACCOUNT = "That account no longer exists."
}

/** The words of "Fix this balance". [money] formats minor units in the ledger's currency (₹ with lakh and crore grouping for rupees). */
object BalanceFixText {
    const val TITLE = "Fix this balance"
    const val CONFIRM = "Fix balance"
    const val HINT = "Looks like some earlier spending is missing."

    /** What the person is asked, by kind of account. */
    fun asks(type: AccountType) = when (type) {
        AccountType.BANK -> "What does your bank app say?"
        AccountType.CASH -> "How much cash do you have right now?"
        AccountType.WALLET -> "What does the wallet app say?"
        AccountType.CREDIT_CARD -> "What does your card statement or bank app say?"
        AccountType.LOAN -> "What does your lender's statement or app say?"
    }

    /** The label of the one amount field: a card or loan asks what is owed, the rest what is held. */
    fun fieldLabel(type: AccountType) = when (type) {
        AccountType.BANK -> "Balance in your bank app"
        AccountType.CASH -> "Cash you have now"
        AccountType.WALLET -> "Balance in the wallet app"
        AccountType.CREDIT_CARD, AccountType.LOAN -> "Amount you owe now"
    }

    /** The two choices under the field: the usual one, then the other ("In credit" for a card or loan, "Overdrawn" for the rest). */
    fun choices(type: AccountType): Pair<String, String> =
        if (type.isLiability) "I owe" to "In credit (nothing owed)" else "It has money" to "Overdrawn"

    /** What the app thinks, in the app's own words for each kind of account: a card or loan owes, the others have. */
    fun current(name: String, type: AccountType, balanceMinor: Long, money: (Long) -> String): String = when {
        type.isLiability && balanceMinor < 0 -> "The app thinks you owe ${money(-balanceMinor)}"
        type.isLiability && balanceMinor == 0L -> "The app thinks you owe nothing"
        type.isLiability -> "The app thinks you owe nothing and have ${money(balanceMinor)} extra in credit"
        balanceMinor < 0 -> "The app thinks $name is overdrawn by ${money(-balanceMinor)}"
        else -> "The app thinks $name has ${money(balanceMinor)}"
    }

    /** A balance in words: "₹3,000.00 owed", "nothing owed", "₹500.00 in credit", "₹2,500.00", "₹500.00 overdrawn". */
    fun describe(type: AccountType, balanceMinor: Long, money: (Long) -> String): String = when {
        type.isLiability && balanceMinor < 0 -> "${money(-balanceMinor)} owed"
        type.isLiability && balanceMinor == 0L -> "nothing owed"
        type.isLiability -> "${money(balanceMinor)} in credit"
        balanceMinor < 0 -> "${money(-balanceMinor)} overdrawn"
        else -> money(balanceMinor)
    }

    /** "Difference: ₹1,234.00 (the app is missing spending of ₹1,234.00)": which way the app is off, in plain words. [deltaMinor] is what the bank says less what the app says; never zero here. */
    fun difference(type: AccountType, deltaMinor: Long, money: (Long) -> String): String {
        val size = money(deltaMinor.absoluteValue)
        val why = when {
            deltaMinor < 0 -> "the app is missing spending of $size"
            type.isLiability -> "the app is missing payments or refunds of $size"
            else -> "the app is missing money that came in: $size"
        }
        return "Difference: $size ($why)"
    }

    /** What fixing does, in plain words, so nothing here looks like spending. */
    fun effect(name: String, type: AccountType, afterMinor: Long, money: (Long) -> String): String =
        "After fixing, $name will show ${describe(type, afterMinor, money)}. " +
            "No spending or income is added, so your budgets and reports stay as they are." +
            if (type == AccountType.CREDIT_CARD) " The balance counts as owed since before your first entry here, so the statement and limit figures follow." else ""

    /** "Balance fixed: ₹3,000.00 owed → ₹5,500.00 owed": the Undo bar, and the start of a history line. */
    fun fixed(type: AccountType, beforeMinor: Long, afterMinor: Long, money: (Long) -> String): String =
        "Balance fixed: ${describe(type, beforeMinor, money)} → ${describe(type, afterMinor, money)}"

    /** One line of an account's history: "Balance fixed: ₹3,000.00 owed → ₹5,500.00 owed on 11 Nov 2026". */
    fun historyLine(type: AccountType, entry: BalanceFixEntry, dateText: String, money: (Long) -> String): String = fixed(type, entry.beforeMinor, entry.afterMinor, money) + " on $dateText"
}

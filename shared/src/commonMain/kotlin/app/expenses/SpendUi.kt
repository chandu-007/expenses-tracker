package app.expenses

import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.Currency
import app.expenses.core.domain.ExpectMath
import app.expenses.core.domain.Horizon
import app.expenses.core.domain.Money
import app.expenses.core.domain.Obligation
import app.expenses.core.domain.ObligationKind
import app.expenses.core.domain.SafeToSpendInput
import app.expenses.core.domain.SafeToSpendRow
import app.expenses.core.domain.SafeToSpendText
import app.expenses.core.domain.SetAside
import app.expenses.core.domain.SinkingFunds
import app.expenses.core.domain.SpendableMath
import app.expenses.core.domain.RecurringKind
import app.expenses.core.domain.formatted
import app.expenses.core.domain.toPlainString
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.storage.BillStatus
import kotlinx.datetime.LocalDate

/** The setting that decides whether the balance-based safe to spend shows under the budget on Home: "1" yes, "0" no, absent lets the app decide. */
internal const val SETTING_SAFE_HOME = "safe_to_spend_on_home"

/** The setting that takes the monthly set-aside for yearly bills off the safe-to-spend figure: "1" on, otherwise off. */
internal const val SETTING_SINKING_FUNDS = "sinking_funds_on"

/**
 * The balance-based safe to spend as the screens show it: the one line ([headline], [perDay]) and the breakdown dialog ([rows] and the notes under them).
 * [homeSetting] is what the person chose for the Home line (null: not chosen), [autoHome] what the app decides without a choice.
 */
data class SafeToSpendUi(
    val headline: String,
    val spendableText: String,
    val nothingSpare: Boolean,
    val perDay: String?,
    val rows: List<SafeToSpendRow>,
    val horizonWhy: String,
    val shortfall: String?,
    val setAsideHint: String?,
    val notCounted: String?,
    val homeSetting: Boolean?,
    val autoHome: Boolean,
) {
    /** Whether the line shows under the budget on Home. */
    val homeVisible: Boolean get() = homeSetting ?: autoHome
}

/** One expense still waiting for money to come back. [accountId] is where "Got it back" starts (the expense's own account while it is still in use), empty when there is none. */
data class WaitingRowUi(
    val id: String, val title: String, val whoText: String, val dateText: String, val remainingText: String, val remainingPlain: String, val detail: String, val accountId: String,
)

/** How many expenses are waiting for money to come back and what they add up to, for the Home chip. */
data class WaitingUi(val count: Int = 0, val totalText: String = "") { val isEmpty get() = count == 0 }

private val DAY = DatePattern("d MMM")

/**
 * Works the balance-based safe to spend out from what the screen already loads: every account's balance, the tracked bills and the repeating items.
 * Runs with the balances (the slow figures, never on the save path). Cards owe what their negative balance says; a tracked bill for a credit card is not taken off
 * again while a card account exists (its statement is already in what the card owes).
 */
fun safeToSpendUi(ok: AppContainer.Opened.Ok, accounts: List<app.expenses.core.storage.db.Account>, balances: Map<AccountId, Money>, today: LocalDate, inr: Currency): SafeToSpendUi {
    val q = ok.db.schemaQueries
    var assets = 0L; var assetCount = 0; var cardOwed = 0L; var cardsOwing = 0; var positiveAsset = false; var otherCurrencyAccounts = 0
    for (a in accounts) {
        val balance = balances[AccountId(a.id)]
        // money is never added across currencies; an account in another one is left out and said so
        if (balance != null && balance.currency != inr && a.type != "LOAN" && (a.archived == 0L || (a.type == "CREDIT_CARD" && balance.minorUnits < 0))) otherCurrencyAccounts++
        val bal = balance?.takeIf { it.currency == inr } ?: continue
        when (a.type) {
            "BANK", "CASH", "WALLET" -> if (a.archived == 0L) { assets += bal.minorUnits; assetCount++; if (bal.minorUnits > 0) positiveAsset = true }
            "CREDIT_CARD" -> if (bal.minorUnits < 0) { cardOwed += -bal.minorUnits; cardsOwing++ }
        }
    }
    // an archived card that still owes is in what the cards owe, so its bill is not taken off a second time either
    val hasCard = accounts.any { it.type == "CREDIT_CARD" && (it.archived == 0L || (balances[AccountId(it.id)]?.takeIf { b -> b.currency == inr }?.minorUnits ?: 0L) < 0) }
    val hasOverall = q.allBudgets().executeAsList().any { it.scope.isEmpty() && it.currency == inr.code }
    val horizonEnd = if (hasOverall) ok.budgets.period().containing(today).endExclusive else today.plusDays(SpendableMath.DEFAULT_HORIZON_DAYS.toLong())
    val obligations = ArrayList<Obligation>()
    var notCounted = 0
    for (b in ok.bills.list(BillStatus.TRACKED, 200)) {
        if (b.currency != inr.code || b.amountMinor <= 0 || (b.kind == "CREDIT_CARD" && hasCard)) continue
        val due = b.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (due == null) { notCounted++; continue }
        obligations += Obligation(b.title, b.amountMinor, due, if (b.kind == "LOAN_EMI") ObligationKind.EMI else ObligationKind.BILL)
    }
    val types = accounts.associate { it.id to it.type }
    for (d in ok.recurring.unhandled(today.minusDays(14), horizonEnd)) {
        val item = d.item
        // an expense leaves the accounts; a transfer into a loan account is an EMI leaving them; income and a transfer between own accounts do not
        val leaves = item.kind == RecurringKind.EXPENSE || (item.kind == RecurringKind.TRANSFER && types[item.toAccount?.value] == "LOAN")
        if (!leaves) continue
        val amount = item.amount
        if (amount == null) notCounted++ else if (amount.currency == inr) obligations += Obligation(item.name, amount.minorUnits, d.date, ObligationKind.REPEATING)
    }
    val setAside = ok.recurring.list().mapNotNull { i ->
        if (i.kind != RecurringKind.EXPENSE || i.amount?.currency != inr) return@mapNotNull null
        SinkingFunds.forItem(i.amount?.minorUnits, i.schedule, today)?.takeIf { it.due >= horizonEnd }?.let { SetAside(i.name, it.perMonthMinor) }
    }
    val result = SpendableMath.compute(
        SafeToSpendInput(today, horizonEnd, if (hasOverall) Horizon.BUDGET_PERIOD else Horizon.THIRTY_DAYS, assets, assetCount, cardOwed, cardsOwing, obligations, setAside, ok.templates.setting(SETTING_SINKING_FUNDS) == "1"),
        notCounted,
    )
    val money = { m: Long -> Money(m, inr).formatted() }
    val day = { d: LocalDate -> DAY.format(d) }
    return SafeToSpendUi(
        headline = SafeToSpendText.headline(result, money, day), spendableText = money(result.spendable), nothingSpare = result.nothingSpare,
        perDay = SafeToSpendText.perDay(result, money), rows = SafeToSpendText.rows(result, money, day), horizonWhy = SafeToSpendText.horizonWhy(result),
        shortfall = SafeToSpendText.shortfall(result, money), setAsideHint = SafeToSpendText.setAsideHint(result, money),
        notCounted = listOfNotNull(
            SafeToSpendText.notCounted(result),
            otherCurrencyAccounts.takeIf { it > 0 }?.let { if (it == 1) "1 account is in another currency and is not counted." else "$it accounts are in another currency and are not counted." },
        ).joinToString(" ").ifEmpty { null },
        homeSetting = when (ok.templates.setting(SETTING_SAFE_HOME)) { "1" -> true; "0" -> false; else -> null },
        // by itself the line shows only for someone who has money in an account and something coming up to take off it
        autoHome = positiveAsset && result.lines.size > 1,
    )
}

/** The waiting-to-get-back list as the Loans screen shows it. */
fun waitingRows(ok: AppContainer.Opened.Ok, digits: Map<String, Int>, activeAccounts: Set<String>): List<WaitingRowUi> {
    val dateFormat = DatePattern("d MMM yyyy")
    val people = ok.people.list().associate { it.id to it.name }
    return ok.expects.waiting().map { w ->
        val cur = Currency(w.currency, digits[w.currency] ?: 2)
        val remaining = Money(w.remainingMinor, cur)
        val who = ExpectMath.fromText(w.personId?.let { people[it] }, w.from)
        val detail = buildList {
            add(if (w.gotMinor == 0L) "${Money(w.expectedMinor, cur).formatted()} expected back" else "${Money(w.gotMinor, cur).formatted()} of ${Money(w.expectedMinor, cur).formatted()} back so far")
            if (w.expectedMinor < w.amountMinor) add("of ${Money(w.amountMinor, cur).formatted()} spent")
        }.joinToString(" · ")
        WaitingRowUi(w.entryId, w.title, who, dateFormat.format(w.occurredAt), remaining.formatted(), remaining.toPlainString(), detail, w.accountId?.takeIf { it in activeAccounts }.orEmpty())
    }
}

/** What the Home chip says: how many are waiting and the total still to come in the ledger's currency (those in another currency are counted but not added). */
fun waitingSummary(ok: AppContainer.Opened.Ok, inr: Currency): WaitingUi {
    val all = ok.expects.waiting()
    if (all.isEmpty()) return WaitingUi()
    val total = all.filter { it.currency == inr.code }.sumOf { it.remainingMinor }
    return WaitingUi(all.size, if (total > 0) Money(total, inr).formatted() else "")
}

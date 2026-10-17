package app.expenses.facade

import app.cash.sqldelight.db.SqlDriver
import app.expenses.core.domain.BaseCurrency
import app.expenses.core.domain.CategoryKind
import app.expenses.core.domain.Currency
import app.expenses.core.domain.Money
import app.expenses.core.domain.formatted
import app.expenses.core.domain.SimilarCandidate
import app.expenses.core.domain.SimilarEntries
import app.expenses.core.domain.ledger.Account
import app.expenses.core.domain.ledger.AccountId
import app.expenses.core.domain.ledger.AccountType
import app.expenses.core.domain.ledger.CategoryId
import app.expenses.core.domain.ledger.CategorySplit
import app.expenses.core.domain.ledger.EntryId
import app.expenses.core.domain.ledger.Expense
import app.expenses.core.domain.ledger.Income
import app.expenses.core.domain.sync.HlcClock
import app.expenses.core.storage.BaseCurrencyStore
import app.expenses.core.storage.CategoryStore
import app.expenses.core.storage.EntryDetails
import app.expenses.core.storage.LedgerStore
import app.expenses.core.storage.SchemaUpgrade
import app.expenses.core.storage.TemplateStore
import app.expenses.core.storage.db.ExpensesDb
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** An account as the screens show it. */
data class AccountInfo(val id: String, val name: String, val balanceText: String)

/** A category as the pickers show it; [income] says which kind of entry it is offered for. */
data class CategoryInfo(val id: String, val name: String, val parentId: String?, val income: Boolean, val expense: Boolean)

/** One entry of the list, already formatted. [dayText] is the date line the list groups by. */
data class EntryRow(val id: String, val title: String, val subtitle: String, val amountText: String, val income: Boolean, val dayText: String, val categoryName: String)

/** Earlier entries that look like one just filed; the person decides. [rows] are the ones not yet in [categoryName]. */
data class SimilarOffer(val label: String, val categoryId: String, val categoryName: String, val rows: List<EntryRow>)

/**
 * The whole app logic the iPhone screens need, over the same shared storage the Android app uses: one object, plain types, no coroutines, so Swift can call
 * it directly. Not thread-safe: call from one thread (the main thread is fine; every call is a short database read or write).
 */
class AppCore(driver: SqlDriver, nodeId: String, private val nowMillis: () -> Long, private val zone: TimeZone = TimeZone.currentSystemDefault()) {
    private val db: ExpensesDb
    private val store: LedgerStore
    private val templates: TemplateStore
    private val categoryStore: CategoryStore
    private val currency: Currency

    init {
        SchemaUpgrade.ensureLatest(driver)
        db = ExpensesDb(driver)
        val clock = HlcClock(nodeId)
        store = LedgerStore(db, clock, nowMillis)
        templates = TemplateStore(db, clock, nowMillis)
        BaseCurrency.current = BaseCurrencyStore(db, clock, nowMillis, templates, store).current()
        currency = BaseCurrency.current
        if (db.schemaQueries.selectAccounts().executeAsList().isEmpty()) {
            store.saveAccount(Account(AccountId("cash"), "Cash", AccountType.CASH, currency))
            store.saveAccount(Account(AccountId("bank"), "Bank", AccountType.BANK, currency))
        }
        categoryStore = CategoryStore(db, clock, nowMillis)
    }

    fun accounts(): List<AccountInfo> {
        val balances = store.balances()
        return db.schemaQueries.selectAccounts().executeAsList().filter { it.archived == 0L }
            .map { AccountInfo(it.id, it.name, (balances[AccountId(it.id)] ?: Money.zero(currency)).formatted()) }
    }

    fun categories(income: Boolean): List<CategoryInfo> =
        categoryStore.list().filter { !it.hidden && it.kind.serves(income) }.map { CategoryInfo(it.id, it.name, it.parentId, it.kind.forIncome, it.kind.forExpense) }

    /** Adds an expense or an income and returns its id. [amountText] is what the person typed ("1,250.50"); a bad amount throws IllegalArgumentException with the reason. */
    @Throws(Exception::class)
    fun add(income: Boolean, amountText: String, categoryId: String, accountId: String, note: String): String {
        val minor = parseMinor(amountText)
        require(minor > 0) { "Enter an amount greater than zero" }
        val id = EntryId(newId())
        val money = Money(minor, currency)
        val entry = if (income) Income(id, nowMillis(), AccountId(accountId), money, categoryId.takeIf { it.isNotEmpty() }?.let(::CategoryId))
        else Expense(id, nowMillis(), AccountId(accountId), money, listOf(CategorySplit(CategoryId(categoryId.ifEmpty { "Other" }), money)))
        store.save(entry, EntryDetails(merchant = note.trim().ifEmpty { null }))
        return id.value
    }

    @Throws(Exception::class)
    fun delete(entryId: String) = store.delete(EntryId(entryId))

    fun recent(limit: Int): List<EntryRow> {
        val names = categoryStore.list().associate { it.id to it.name }
        return db.schemaQueries.recentEntries(limit.toLong()).executeAsList().filter { it.kind == "EXPENSE" || it.kind == "INCOME" }.map { r ->
            val cat = if (r.kind == "INCOME") r.category_id.orEmpty() else db.schemaQueries.splitsOf(r.id).executeAsList().firstOrNull()?.category_id.orEmpty()
            val name = names[cat].orEmpty()
            EntryRow(
                r.id, r.merchant?.takeIf { it.isNotBlank() } ?: r.note ?: name.ifEmpty { if (r.kind == "INCOME") "Income" else "Expense" },
                listOf(dayText(r.occurred_at), name).filter { it.isNotEmpty() }.joinToString(" · "),
                Money(r.amount_minor, currency).formatted(), r.kind == "INCOME", dayText(r.occurred_at), name,
            )
        }
    }

    /** What went out this calendar month, formatted. */
    fun monthSpendText(): String {
        val today = Instant.fromEpochMilliseconds(nowMillis()).toLocalDateTime(zone).date
        val start = LocalDate(today.year, today.month, 1)
        val next = if (today.month.ordinal == 11) LocalDate(today.year + 1, 1, 1) else LocalDate(today.year, today.month.ordinal + 2, 1)
        val spent = store.spendByCategory(currency, from = start.atStartOfDayIn(zone).toEpochMilliseconds(), until = next.atStartOfDayIn(zone).toEpochMilliseconds())
            .values.fold(Money.zero(currency)) { a, m -> a + m }
        return spent.formatted()
    }

    // ---- similar entries ----

    /** After filing [entryId] under its category, the earlier entries with a similar name that are filed elsewhere, or null. */
    fun similarTo(entryId: String): SimilarOffer? {
        val e = db.schemaQueries.entryById(entryId).executeAsOneOrNull() ?: return null
        val label = (e.merchant ?: e.note).orEmpty().trim()
        val words = SimilarEntries.searchWords(label)
        if (words.isEmpty() || (e.kind != "EXPENSE" && e.kind != "INCOME")) return null
        val cat = if (e.kind == "INCOME") e.category_id.orEmpty() else db.schemaQueries.splitsOf(entryId).executeAsList().singleOrNull()?.category_id.orEmpty()
        if (cat.isEmpty()) return null
        val found = SimilarEntries.similar(
            label, cat, entryId,
            db.schemaQueries.similarCandidates(e.kind, words.joinToString(" OR ") { "\"" + it.replace("\"", "\"\"") + "\"" }).executeAsList()
                .map { SimilarCandidate(it.id, it.label, it.category_id.orEmpty(), it.amount_minor, it.occurred_at) },
        )
        if (found.isEmpty()) return null
        val names = categoryStore.list().associate { it.id to it.name }
        return SimilarOffer(label, cat, names[cat] ?: cat, found.map { EntryRow(it.id, it.label, dayText(it.occurredAt), Money(it.amountMinor, currency).formatted(), e.kind == "INCOME", dayText(it.occurredAt), names[it.categoryId].orEmpty()) })
    }

    /** Files the given earlier entries under [categoryId]; returns how many were changed. Entries of the other kind are skipped. */
    @Throws(Exception::class)
    fun fileUnder(entryIds: List<String>, categoryId: String): Int {
        val kind = categoryStore.list().firstOrNull { it.id == categoryId }?.kind ?: return 0
        var changed = 0
        store.atomically {
            for (id in entryIds) {
                val row = db.schemaQueries.entryById(id).executeAsOneOrNull() ?: continue
                val money = Money(row.amount_minor, currency)
                val details = EntryDetails(merchant = row.merchant, note = row.note, source = row.source, sourceRef = row.source_ref)
                when {
                    row.kind == "EXPENSE" && kind.forExpense && db.schemaQueries.splitsOf(id).executeAsList().size == 1 -> {
                        store.save(Expense(EntryId(id), row.occurred_at, AccountId(row.account_id.orEmpty()), money, listOf(CategorySplit(CategoryId(categoryId), money))), details); changed++
                    }
                    row.kind == "INCOME" && kind.forIncome -> {
                        store.save(Income(EntryId(id), row.occurred_at, AccountId(row.account_id.orEmpty()), money, CategoryId(categoryId)), details); changed++
                    }
                }
            }
        }
        return changed
    }

    // ---- helpers ----

    private fun parseMinor(text: String): Long {
        val clean = text.trim().replace(",", "").removePrefix("\u20B9")
        require(clean.isNotEmpty()) { "Enter an amount" }
        val parts = clean.split('.')
        require(parts.size <= 2 && parts[0].all { it.isDigit() } && (parts.size == 1 || parts[1].all { it.isDigit() })) { "The amount should be a number like 1250.50" }
        val digits = currency.minorUnitDigits
        val frac = (parts.getOrNull(1) ?: "")
        require(frac.length <= digits) { "At most $digits decimal places" }
        val whole = parts[0].ifEmpty { "0" }.toLong()
        var scale = 1L; repeat(digits) { scale *= 10 }
        return whole * scale + (frac.padEnd(digits, '0').ifEmpty { "0" }.toLong())
    }

    private fun dayText(millis: Long): String {
        val d = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date
        return "${d.day} ${d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${d.year}"
    }

    private fun newId(): String = kotlin.random.Random.nextBytes(8).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

}

/** The categories of one kind, for the pickers. */
fun CategoryKind.label(): String = when (this) { CategoryKind.EXPENSE -> "Expenses"; CategoryKind.INCOME -> "Income"; CategoryKind.BOTH -> "Both" }

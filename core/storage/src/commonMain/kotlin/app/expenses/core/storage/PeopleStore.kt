package app.expenses.core.storage

import app.expenses.core.storage.db.ExpensesDb
import kotlin.random.Random

data class Person(val id: String, val name: String)

/** What one person owes the user (positive) or is owed by the user (negative), in minor units of the base currency, from the lent-and-borrowed entries with them. */
data class LoanBalance(val person: Person, val minor: Long, val entries: Int, val lastAt: Long)

/**
 * The people the user lends to and borrows from. A person is a name with an id; the lent and borrowed entries refer to the id, so renaming
 * changes it everywhere. Not thread-safe: use through the process-wide write lock.
 */
class PeopleStore(private val db: ExpensesDb) {
    private val q get() = db.schemaQueries

    fun list(): List<Person> = q.livePeople().executeAsList().map { Person(it.id, it.name) }

    fun get(id: String): Person? = q.personById(id).executeAsOneOrNull()?.takeIf { it.deleted == 0L }?.let { Person(it.id, it.name) }

    /** The person with this name (any capitalisation, any spacing), made if there is none yet: typing "chandu" twice is one person. */
    fun create(name: String): Person = db.transactionWithResult {
        val clean = normalize(name)
        require(clean.isNotEmpty()) { "Enter a name." }
        require(clean.length <= MAX_NAME) { "Use a name of at most $MAX_NAME characters." }
        list().firstOrNull { it.name.equals(clean, ignoreCase = true) } ?: Person(newId(), clean).also { q.insertPerson(it.id, it.name) }
    }

    /** Takes a person off the list, only when no entry refers to them: otherwise their history would lose its name. */
    fun remove(id: String) = db.transaction {
        require(q.personUsage(id).executeAsOne() == 0L) { "This person has lent or borrowed entries, so they stay on the list." }
        q.removePerson(id)
    }

    /** Everyone on the list with what they owe or are owed in [currency]: those with the largest balance first, settled-up people after, then by name. */
    fun balances(currency: String): List<LoanBalance> {
        val by = q.loanBalances(currency).executeAsList().associateBy { it.person_id }
        return list().map { p -> val b = by[p.id]; LoanBalance(p, b?.balance ?: 0L, (b?.n ?: 0L).toInt(), b?.last_at ?: 0L) }
            .sortedWith(compareByDescending<LoanBalance> { kotlin.math.abs(it.minor) }.thenBy { it.person.name.lowercase() })
    }

    private fun normalize(name: String) = name.trim().replace(Regex("\\s+"), " ")
    private fun newId(): String = Random.nextBytes(8).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    companion object { const val MAX_NAME = 60 }
}

package app.expenses.core.parsers

import app.expenses.core.domain.Certainty
import app.expenses.core.domain.HistoryProfile
import app.expenses.core.domain.PayeeKey
import app.expenses.core.domain.StarterCategories
import app.expenses.core.domain.SuggestionSource
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "What does To review start a captured CREDIT with?" asked of the real code on an invented, hand-labelled corpus (`category-corpus/invented_credits.tsv`: every name, digit and amount is made up;
 * the labels are what a sensible person would choose, not any bank's data). Nothing learned and no history: what a new install does, then a simulation of what the person's own choices change.
 * Figures are for the invented corpus only, not an accuracy claim about real bank traffic.
 */
class CreditCategoryReportTest {
    private class Row(val tag: String, val sender: String, val expected: Set<String>, val body: String)

    private val rows = checkNotNull(javaClass.classLoader.getResourceAsStream("category-corpus/invented_credits.tsv")).readBytes().decodeToString().lines()
        .filter { it.isNotBlank() && !it.startsWith("#") }.map { l -> l.split("\t").let { Row(it[0], it[1], it[2].split('|').toSet(), it[3].replace("\\n", "\n")) } }

    private val names = StarterCategories.INCOME_FIRST + StarterCategories.INCOME_MORE
    private val usable = names.map { StarterCategories.incomeId(it) }.toSet()
    private val byName = names.associate { it.lowercase() to StarterCategories.incomeId(it) }
    private val idToName = names.associate { StarterCategories.incomeId(it) to it }

    private class Result(val row: Row, val read: Boolean, val credit: Boolean, val payee: String?, val got: String?, val certainty: Certainty?, val source: SuggestionSource?, val refundKind: Boolean)

    private fun advise(payee: String?, body: String, taught: CaptureCategory.Taught = CaptureCategory.Taught(), history: HistoryProfile? = null) =
        CaptureCategory.adviseIncome(payee, body, emptyList(), taught, usable, byName, history)

    private fun run(r: Row): Result {
        val outcome = CaptureClassifier.classify(r.sender, r.body, emptySet(), LocalDate(2026, 10, 5))
        if (outcome !is CaptureOutcome.Transaction) return Result(r, false, false, null, null, null, null, false)
        val a = outcome.alert
        val payee = PayeeGuess.of(r.body)
        if (a.direction != Direction.CREDIT) return Result(r, true, false, payee, null, null, null, false)
        val s = advise(payee, r.body)
        return Result(r, true, true, payee, s?.categoryId?.let { idToName[it] }, s?.certainty, s?.source, KindRules.of(r.body, a.direction) == MessageKind.REFUND)
    }

    private val results by lazy { rows.map(::run) }

    /** wrong = a category the label does not allow (a refund text that To review ties to its expense is allowed to carry no category as well as "Refunds and cashback"). */
    private fun verdict(x: Result): String = when {
        !x.read || !x.credit -> "not read as a credit"
        x.got == null -> if ("NONE" in x.row.expected || "REFUND" in x.row.expected && x.refundKind) "none (right)" else "none (missed)"
        x.got in x.row.expected -> "right"
        else -> "WRONG"
    }

    @Test
    fun printWhatToReviewStartsAnInventedCreditWith() {
        val rs = results
        val credits = rs.filter { it.credit }
        println("credit corpus: ${rs.size} messages, ${credits.size} read as a credit, ${rs.count { !it.read }} not read at all, ${rs.count { it.read && !it.credit }} read as a debit")
        println("a payer found in ${credits.count { it.payee != null }} of ${credits.size}")
        val byVerdict = credits.groupBy(::verdict).mapValues { it.value.size }.toSortedMap()
        println("verdicts: $byVerdict")
        println("sure suggestions: ${credits.count { it.certainty == Certainty.SURE }}; checks: ${credits.count { it.certainty == Certainty.CHECK }}")
        val labelled = credits.filter { "NONE" !in it.row.expected }
        println("of ${labelled.size} credits that have a right category: right ${labelled.count { it.got != null && it.got in it.row.expected }}, wrong ${labelled.count { it.got != null && it.got !in it.row.expected }}, none ${labelled.count { it.got == null }}")
        for (x in rs.filter { !it.read || !it.credit || verdict(it) == "WRONG" || verdict(it) == "none (missed)" }) println("  ${verdict(x)}: [${x.row.tag}] ${x.row.body.take(110).replace('\n', ' ')} -> payee=${x.payee} got=${x.got}")
    }

    @Test
    fun floorsOnTheInventedCreditCorpus() {
        val credits = results.filter { it.credit }
        assertTrue(results.all { it.read && it.credit }, "every invented credit text is read as a credit: " + results.filter { !it.read || !it.credit }.joinToString { it.row.tag + ":" + it.row.body.take(50) })
        assertEquals(emptyList(), credits.filter { verdict(it) == "WRONG" }.map { it.row.body }, "no wrong category, with or without a check mark")
        assertEquals(0, credits.count { it.certainty == Certainty.SURE }, "with nothing the person has chosen, nothing is sure: no wrong 'sure' by construction, and no sure at all")
        assertEquals(emptyList(), credits.filter { "NONE" in it.row.expected && it.got != null }.map { it.row.body }, "a credit from a person with no purpose in its words gets no guess")
        val labelled = credits.filter { "NONE" !in it.row.expected }
        assertTrue(labelled.count { it.got != null } * 100 >= labelled.size * 85, "at least 85% of the credits whose wording names a purpose get the right category as a check: ${labelled.count { it.got != null }} of ${labelled.size}")
        assertTrue(credits.filter { it.refundKind }.all { KindRules.of(it.row.body, Direction.CREDIT) == MessageKind.REFUND }, "refund and reversal wording is still read as a refund (tied to the expense it undoes), never turned into plain income here")
    }

    /**
     * The person's own choices, in the order they work through payers: every choice is learned under the payer's income key. A payer who is a person needs two choices to be sure; a company one;
     * a choice for one payer changes nothing for another; and the same name as an EXPENSE payee never picks up the income choice.
     */
    @Test
    fun learningFromThePersonsOwnIncomeChoices() {
        val taught = HashMap<String, String>(); val uses = HashMap<String, Int>()
        fun choose(payee: String, category: String) { val k = PayeeKey.incomeOf(payee)!!; if (taught[k] == category) uses[k] = (uses[k] ?: 1) + 1 else { taught[k] = category; uses[k] = 1 } }
        fun ask(payee: String, body: String = "credited") = advise(payee, body, CaptureCategory.Taught(taught, uses))
        val tenant = "tenant@ybl"; val acme = "ACME SOFTWARE PVT LTD"
        assertEquals(null, ask(tenant), "first time: nothing to go on")
        choose(tenant, StarterCategories.incomeId("Rent received"))
        val first = ask(tenant)!!
        assertEquals(StarterCategories.incomeId("Rent received"), first.categoryId); assertEquals(SuggestionSource.LEARNED, first.source)
        assertEquals(Certainty.CHECK, first.certainty, "a person chosen once is looked at again")
        choose(tenant, StarterCategories.incomeId("Rent received"))
        assertEquals(Certainty.SURE, ask(tenant)!!.certainty, "chosen twice in a row: sure"); assertEquals("you chose it 2 times for this payer", ask(tenant)!!.note)
        choose(acme, StarterCategories.incomeId("Salary"))
        assertEquals(Certainty.SURE, ask(acme)!!.certainty, "a company paid once and chosen once is sure")
        assertEquals(null, ask("another@ybl"), "another payer is unaffected")
        // a different choice replaces the old one and starts the count again
        choose(tenant, StarterCategories.incomeId("Gifts received"))
        assertEquals(StarterCategories.incomeId("Gifts received"), ask(tenant)!!.categoryId); assertEquals(Certainty.CHECK, ask(tenant)!!.certainty)
        // the income choice for a name is invisible to the expense side, and an expense choice to the income side
        val expenseTaught = CaptureCategory.Taught(taught.toMap(), uses.toMap())
        val usableExpense = StarterCategories.expenseOrder.map { StarterCategories.expenseId(it) }.toSet()
        val byNameExpense = StarterCategories.expenseOrder.associate { it.lowercase() to StarterCategories.expenseId(it) }
        assertEquals(null, CaptureCategory.advise("tenant@ybl", "x", emptyList(), expenseTaught, usableExpense, byNameExpense, null)?.takeIf { it.source == SuggestionSource.LEARNED }, "a payer's income choice is not an expense payee's category")
        assertEquals(null, advise("somebody@ybl", "credited", CaptureCategory.Taught(mapOf("somebody" to StarterCategories.expenseId("Food")), mapOf("somebody" to 5))), "an expense choice is not a payer's income category")
        // the words are a check, never above what the person chose
        assertEquals(SuggestionSource.LEARNED, advise(acme, "NEFT SALARY OCT", CaptureCategory.Taught(taught, uses))!!.source)
    }

    /** Their own earlier income from the same payer is a sure start with two agreeing entries and none against, as for expenses. */
    @Test
    fun theirOwnEarlierIncomeFromThePayerIsTheOnlyOtherWayToBeSure() {
        val salary = StarterCategories.incomeId("Salary")
        assertEquals(Certainty.SURE, advise("ACME SOFTWARE PVT LTD", "x", history = HistoryProfile(salary, 3, 0))!!.certainty)
        assertEquals(Certainty.CHECK, advise("ACME SOFTWARE PVT LTD", "x", history = HistoryProfile(salary, 1, 0))!!.certainty)
        assertEquals(Certainty.CHECK, advise("ACME SOFTWARE PVT LTD", "x", history = HistoryProfile(salary, 3, 1))!!.certainty, "history that disagrees with itself")
        assertEquals("3 of your earlier income entries", advise("ACME SOFTWARE PVT LTD", "x", history = HistoryProfile(salary, 3, 0))!!.note)
    }
}

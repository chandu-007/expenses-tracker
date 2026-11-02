package app.expenses.core.parsers

import app.expenses.core.domain.Certainty
import app.expenses.core.domain.StarterCategories
import kotlinx.datetime.LocalDate
import kotlin.test.Test

/**
 * "How often does To review start a captured text with the right category, a wrong one, or none?" asked of the real code on an invented, hand-labelled corpus
 * (`category-corpus/invented.tsv`: every name, digit and amount is made up; the labels are what a sensible person would choose, not any bank's data).
 * Nothing learned and no history: this is what a new install does. Figures are for the invented corpus only, not an accuracy claim about real bank traffic.
 */
class CategoryAccuracyReportTest {
    private class Row(val tag: String, val sender: String, val expected: Set<String>, val body: String)

    private val rows = checkNotNull(javaClass.classLoader.getResourceAsStream("category-corpus/invented.tsv")).readBytes().decodeToString().lines()
        .filter { it.isNotBlank() && !it.startsWith("#") }.map { l -> l.split("\t").let { Row(it[0], it[1], it[2].split('|').toSet(), it[3].replace("\\n", "\n")) } }

    private val names = StarterCategories.expenseOrder
    private val usable = names.map { StarterCategories.expenseId(it) }.toSet()
    private val byName = names.associate { it.lowercase() to StarterCategories.expenseId(it) }
    private val idToName = StarterCategories.expenseOrder.associate { StarterCategories.expenseId(it) to it.lowercase() }

    private class Result(val row: Row, val captured: Boolean, val credit: Boolean, val payee: String?, val got: String?, val note: String, val sure: Boolean = false)

    private fun run(r: Row): Result {
        val outcome = CaptureClassifier.classify(r.sender, r.body, emptySet(), LocalDate(2026, 10, 5))
        if (outcome !is CaptureOutcome.Transaction) return Result(r, false, false, null, null, outcome.toString())
        val a = outcome.alert
        val payee = PayeeGuess.of(r.body)
        if (a.direction != Direction.DEBIT) return Result(r, true, true, payee, null, "credit")
        val kind = KindRules.of(r.body, a.direction)
        val s = CaptureCategory.advise(payee, r.body, emptyList(), CaptureCategory.Taught(), usable, byName, null, toAPerson = PayeeKinds.of(payee) == PayeeKind.PERSON, personLike = PayeeKinds.of(payee) == PayeeKind.PERSON || PayeeKinds.looksLikePerson(payee))
        val got = s?.categoryId?.let { idToName[it] ?: it }
        val atm = got == null && kind == MessageKind.ATM_WITHDRAWAL
        return Result(r, true, false, payee, if (atm) "cash withdrawal" else got, s?.source?.name.orEmpty(), s?.certainty == Certainty.SURE)
    }

    private fun verdict(x: Result): String {
        val want = x.row.expected.map { it.lowercase() }.toSet()
        val none = "none" in want
        return when {
            !x.captured -> "NOT_CAPTURED"
            x.credit -> "CREDIT"
            x.got == null -> if (none) "RIGHT_NONE" else "MISSED"
            x.got.lowercase() in want -> "RIGHT"
            else -> "WRONG"
        }
    }

    @Test
    fun printAccuracyOnTheInventedCorpus() {
        val results = rows.map(::run)
        val v = results.map { it to verdict(it) }
        fun pct(n: Int, d: Int) = if (d == 0) "-" else "%.0f%%".format(100.0 * n / d)
        println("CATEGORY-ACCURACY invented corpus: ${rows.size} messages")
        val debits = v.filter { it.second != "CREDIT" && it.second != "NOT_CAPTURED" }
        println("CATEGORY-ACCURACY captured debits ${debits.size}; credits ${v.count { it.second == "CREDIT" }}; not captured ${v.count { it.second == "NOT_CAPTURED" }}")
        for (k in listOf("RIGHT", "RIGHT_NONE", "MISSED", "WRONG")) println("CATEGORY-ACCURACY   $k ${debits.count { it.second == k }} (${pct(debits.count { it.second == k }, debits.size)})")
        val wanting = debits.filter { "none" !in it.first.row.expected.map { e -> e.lowercase() } }
        println("CATEGORY-ACCURACY of ${wanting.size} debits that have a right category: right ${wanting.count { it.second == "RIGHT" }} (${pct(wanting.count { it.second == "RIGHT" }, wanting.size)}), wrong ${wanting.count { it.second == "WRONG" }} (${pct(wanting.count { it.second == "WRONG" }, wanting.size)}), none ${wanting.count { it.second == "MISSED" }} (${pct(wanting.count { it.second == "MISSED" }, wanting.size)})")
        val given = debits.filter { it.first.got != null }
        println("CATEGORY-ACCURACY of ${given.size} suggestions: sure ${given.count { it.first.sure }} (wrong ${given.count { it.first.sure && it.second == "WRONG" }}), to check ${given.count { !it.first.sure }} (wrong ${given.count { !it.first.sure && it.second == "WRONG" }}); sure and right ${given.count { it.first.sure && it.second == "RIGHT" }}")
        println("CATEGORY-ACCURACY payee found for ${debits.count { it.first.payee != null }} of ${debits.size} debits")
        for ((label, subset) in listOf("tuned (written before the fixes, then used to fix them)" to debits.filter { !it.first.row.tag.startsWith("holdout") }, "holdout (written after the code was frozen, never tuned on)" to debits.filter { it.first.row.tag.startsWith("holdout") })) {
            val w = subset.filter { "none" !in it.first.row.expected.map { e -> e.lowercase() } }
            println("CATEGORY-ACCURACY $label: ${subset.size} debits, ${w.size} with a right category: right ${w.count { it.second == "RIGHT" }} (${pct(w.count { it.second == "RIGHT" }, w.size)}), wrong ${w.count { it.second == "WRONG" }}, none ${w.count { it.second == "MISSED" }}; of all ${subset.size}: wrong ${subset.count { it.second == "WRONG" }} (sure ${subset.count { it.second == "WRONG" && it.first.sure }}), payee found ${subset.count { it.first.payee != null }}")
        }
        println("CATEGORY-ACCURACY by shape (tag: n | right / none-right / missed / wrong | no payee):")
        for ((tag, g) in v.filter { it.second != "CREDIT" }.groupBy { it.first.row.tag }) {
            println("CATEGORY-ACCURACY   %-24s %3d | %2d / %2d / %2d / %2d | %d".format(tag, g.size, g.count { it.second == "RIGHT" }, g.count { it.second == "RIGHT_NONE" }, g.count { it.second == "MISSED" }, g.count { it.second == "WRONG" }, g.count { it.first.payee == null }))
        }
        for ((x, verdictName) in v) if (verdictName == "WRONG" || verdictName == "MISSED" || verdictName == "NOT_CAPTURED") println("CATEGORY-ACCURACY   $verdictName [${x.row.tag}] payee=${x.payee} got=${x.got}(${x.note}) want=${x.row.expected} :: ${x.row.body.replace('\n', ' ').take(90)}")
    }

    /**
     * The owner's idea in numbers: the person goes through the corpus in order and, whenever the app had no right category, chooses the right one (what they do in To review). Each
     * choice is learned (under today's key, and for the comparison under the old key). Then: how many of the later sightings of a payee the app gets right with nothing learned, with
     * what was learned under the old key, and with what is learned under today's key.
     */
    @Test
    fun learningFromThePersonsOwnChoicesInTheOrderTheyWorkThroughTheCorpus() {
        val taughtNew = HashMap<String, String>(); val taughtOld = HashMap<String, String>()
        var repeats = 0; var rightCold = 0; var rightOld = 0; var rightNew = 0; var wrongNew = 0; var sureWrongNew = 0
        val seen = HashSet<String>(); val unknownAtFirst = HashSet<String>()
        var unkRepeats = 0; var unkOld = 0; var unkNew = 0; var unkCold = 0
        for (r in rows) {
            val x = run(r)
            if (!x.captured || x.credit) continue
            val want = r.expected.map { it.lowercase() }.toSet(); if ("none" in want) continue
            val payee = x.payee
            val keyNew = app.expenses.core.domain.PayeeKey.of(payee); val keyOld = app.expenses.core.domain.PayeeKey.legacy(payee)
            val repeat = keyNew != null && !seen.add(keyNew).also { _ -> }
            val cold = x.got?.lowercase() in want
            val old = taughtOld[keyOld]?.let { idToName[it] }?.lowercase() in want
            val advice = CaptureCategory.advise(payee, r.body, emptyList(), CaptureCategory.Taught(taughtNew, emptyMap()), usable, byName, null, toAPerson = PayeeKinds.of(payee) == PayeeKind.PERSON, personLike = PayeeKinds.looksLikePerson(payee))
            val withNew = advice?.categoryId?.let { idToName[it] }?.lowercase()
            if (!repeat && keyNew != null && !cold) unknownAtFirst += keyNew
            if (repeat && keyNew in unknownAtFirst) { unkRepeats++; if (cold) unkCold++; if (old || cold) unkOld++; if (withNew in want) unkNew++ }
            if (repeat) {
                repeats++; if (cold) rightCold++; if (old || cold) rightOld++; if (withNew in want) rightNew++
                if (withNew != null && withNew !in want) { wrongNew++; if (advice.certainty == Certainty.SURE) sureWrongNew++ }
            }
            // the person fixes what the app did not get right, and the choice is learned
            if (withNew == null || withNew !in want) {
                val choice = names.first { it.lowercase() in want }.let { StarterCategories.expenseId(it) }
                keyNew?.let { taughtNew[it] = choice }; keyOld?.let { taughtOld[it] = choice }
            }
        }
        fun pct(n: Int) = if (repeats == 0) "-" else "%.0f%%".format(100.0 * n / repeats)
        println("CATEGORY-ACCURACY learning from the person's own corrections: $repeats later sightings of a payee already seen; right with nothing learned $rightCold (${pct(rightCold)}), with the old key $rightOld (${pct(rightOld)}), with the new key $rightNew (${pct(rightNew)}); wrong with the new key $wrongNew (of which sure $sureWrongNew)")
        println("CATEGORY-ACCURACY   of those, the $unkRepeats later sightings of a payee the app did not know the first time: right with nothing learned $unkCold, with the old key $unkOld, with the new key $unkNew")
        kotlin.test.assertTrue(unkNew > unkOld && unkNew == unkRepeats, "every later sighting of a payee the person taught is right with today's key, and the old key missed some")
        kotlin.test.assertTrue(rightNew > rightOld && rightNew > rightCold, "learning with today's key helps more than the old key or nothing")
        kotlin.test.assertEquals(0, sureWrongNew, "a learned choice is not wrong unless the person chose differently for the same payee")
    }

    @Test
    fun floorsOnTheInventedCorpus() {
        val results = rows.map(::run)
        val v = results.map { it to verdict(it) }
        val debits = v.filter { it.second != "CREDIT" && it.second != "NOT_CAPTURED" }
        kotlin.test.assertEquals(0, v.count { it.second == "NOT_CAPTURED" }, "every invented debit is captured")
        kotlin.test.assertEquals(0, debits.count { it.second == "WRONG" }, "no wrong category on the invented corpus")
        val wanting = debits.filter { "none" !in it.first.row.expected.map { e -> e.lowercase() } }
        kotlin.test.assertTrue(wanting.count { it.second == "RIGHT" } * 100 >= wanting.size * 80, "at least 80% of the debits that have a right category get it (before this work: 54%)")
        kotlin.test.assertTrue(debits.count { it.first.payee != null } * 100 >= debits.size * 95, "a payee is read from 95% of the debits")
        kotlin.test.assertEquals(0, debits.count { it.first.sure && it.second == "WRONG" })
    }
}

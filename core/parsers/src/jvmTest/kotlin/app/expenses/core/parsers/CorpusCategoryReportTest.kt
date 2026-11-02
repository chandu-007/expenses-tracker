package app.expenses.core.parsers

import app.expenses.core.domain.Certainty
import app.expenses.core.domain.StarterCategories
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The public sample corpus (`corpus/`, see its SOURCE.md: sanitised, synthetic, not validated) asked "who was paid, and which category does a new install start with?". There is no answer key
 * for a category, so this prints what it finds (payee, category, sure or check) for a person to read, and asserts only counts that must not fall.
 */
class CorpusCategoryReportTest {
    private fun resource(path: String) = checkNotNull(javaClass.classLoader.getResourceAsStream("corpus/$path")) { "missing corpus/$path" }.readBytes().decodeToString()
    private val names = StarterCategories.expenseOrder
    private val usable = names.map { StarterCategories.expenseId(it) }.toSet()
    private val byName = names.associate { it.lowercase() to StarterCategories.expenseId(it) }
    private val idToName = names.associate { StarterCategories.expenseId(it) to it }

    @Test
    fun whoWasPaidAndWhichCategoryOnThePublicSampleCorpus() {
        val files = resource("expected.tsv").lines().filter { it.isNotBlank() }.map { it.split("\t") }.filter { it[1] == "1" && it[2] == "debit" }.map { it[0] }
        var parsed = 0; var payee = 0; var category = 0; var sure = 0; var check = 0; var noPayeeButBankNamed = 0
        val lines = ArrayList<String>()
        for (f in files) {
            val body = resource(f)
            val r = GenericAlertParser.parse(body) as? ParseResult.Parsed ?: continue
            if (r.alert.direction != Direction.DEBIT) continue
            parsed++
            val p = PayeeGuess.of(body)
            if (p != null) payee++ else noPayeeButBankNamed++
            val a = CaptureCategory.advise(p, body, emptyList(), CaptureCategory.Taught(), usable, byName, null, toAPerson = PayeeKinds.of(p) == PayeeKind.PERSON, personLike = PayeeKinds.looksLikePerson(p))
            if (a != null) { category++; if (a.certainty == Certainty.SURE) sure++ else check++ }
            lines += "${f.substringBefore('/')}/${f.substringAfter('/').removeSuffix(".txt")}: payee=${p ?: "-"} category=${a?.categoryId?.let { idToName[it] } ?: "-"} ${a?.certainty?.name?.lowercase().orEmpty()} ${a?.note.orEmpty()}"
        }
        println("CORPUS-CATEGORY public sample corpus: $parsed debits read; a payee found for $payee (${100 * payee / parsed}%); a category suggested for $category (${100 * category / parsed}%): sure $sure, to check $check")
        for (l in lines) println("CORPUS-CATEGORY   $l")
        assertEquals(true, parsed >= 60, "the corpus still reads its debits")
    }
}

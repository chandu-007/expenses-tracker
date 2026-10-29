package app.expenses.core.parsers

import app.expenses.core.domain.CategorySuggest
import app.expenses.core.domain.DefaultRules
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * "Is the merchant recognised and is a category suggested?" asked of the real code a captured text goes through in To review: [CaptureClassifier] (direction, amount, digits),
 * [PayeeGuess] (the name the card shows), [DefaultRules] (a clean merchant name and the starter category) and [CategorySuggest] (the person's rules, what they chose last time, their history,
 * the starter list, "a payment to a person"). The categories here are the ones a new install has; the texts have invented digits and amounts.
 */
class CapturePipelineTest {
    private val out = "Amt Deducted!\nRs.45,250.75 from your HDFC Bank A/c XX1234 for Money Transfer via HDFC Bank Online Banking.\nNot you?Call 18002586161/SMS BLOCK OB to 7308080808"
    private val into = "Update! INR 45,250.75 deposited in HDFC Bank A/c XX5678 on 29-OCT-26 for XXXXXXXXXX1234-TPT-Holiday Fund Remembe-SAMPLE TRADERS PRIVATE LIMITED.Avl bal INR 3,21,654.32. Cheque deposits in A/C are subject to clearing"
    private val usable = setOf("Food", "Groceries", "Transport", "Bills", "Shopping", "Health", "Fun", "Other")
    private val byName = usable.associateBy { it.lowercase() }

    /** What To review would derive for [body]: the title on the card and the category it starts with (null for none), as the view model does for a new person with no rules, no history. */
    private fun derive(body: String): Triple<String, String?, String> {
        val alert = (CaptureClassifier.classify("AX-HDFCBK-S", body, emptySet(), LocalDate(2026, 10, 29)) as CaptureOutcome.Transaction).alert
        val payee = PayeeGuess.of(body)
        val clean = payee?.let { DefaultRules.displayNameFor(it) ?: it }
        val suggestion = if (alert.direction == Direction.DEBIT) CategorySuggest.suggest(payee, body, emptyList(), emptyMap(), usable, byName, toAPerson = PayeeKinds.of(payee) == PayeeKind.PERSON) else null
        return Triple(clean ?: "HDFC Bank (no payee: the bank's name)", suggestion?.categoryId, "${alert.direction} ${alert.amountMinor} ${alert.currency} acct=${alert.accountLast4} counter=${alert.counterLast4} kind=${KindRules.of(body, alert.direction)}")
    }

    @Test
    fun theTwoRealShapedTextsAreRecognisedButNeitherNamesAMerchantAndNeitherGetsACategory() {
        val a = derive(out); val b = derive(into)
        println("PIPELINE sms1 (debit, source account): title='${a.first}' category=${a.second} read=${a.third}")
        println("PIPELINE sms2 (credit, destination account): title='${b.first}' category=${b.second} read=${b.third}")
        assertEquals("HDFC Bank (no payee: the bank's name)", a.first); assertNull(a.second, "a transfer has no category; a debit with no payee has no starter match")
        assertEquals("Holiday Fund Remembe", b.first); assertNull(b.second, "income carries no category suggestion; and the note is no known merchant")
        assertEquals("DEBIT 4525075 INR acct=1234 counter=null kind=PAYMENT", a.third)
        assertEquals("CREDIT 4525075 INR acct=5678 counter=1234 kind=PAYMENT", b.third)
    }

    @Test
    fun narrationsOfOtherShapesGiveACleanMerchantAndTheStarterCategoryWhenTheNameIsKnown() {
        // (text, title, category the starter list gives)
        val table = listOf(
            Triple("Rs.499.00 debited from A/c XX1234 on 05-10-26. Info: ACH D- NETFLIX-123456. Avl Bal Rs.100", "Netflix", "Fun"),
            Triple("Rs.199.00 debited from A/c XX1234 on 05-10-26. Info: UPI-SWIGGY-swiggy@icici-123456789012. Avl Bal Rs.100", "Swiggy", "Food"),
            Triple("Rs.450.00 debited from A/c XX1234 on 05-10-26. Info: IMPS-123456789012-ANITA RAO. Avl Bal Rs.100", "ANITA RAO", null),
            Triple("Rs.2,000.00 debited from A/c XX1234 on 05-10-26. Info: ATM-CASH. Avl Bal Rs.100", "ATM cash", null),
        )
        for ((text, title, category) in table) {
            val d = derive(text)
            println("PIPELINE '${text.substringAfter("Info: ").substringBefore(". Avl")}' -> title='${d.first}' category=${d.second}")
            assertEquals(title, d.first, text); assertEquals(category, d.second, text)
        }
    }
}

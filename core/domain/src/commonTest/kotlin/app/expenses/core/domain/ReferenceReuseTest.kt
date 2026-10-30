package app.expenses.core.domain

import app.expenses.core.domain.ReferenceReuse.Verdict.REVERSAL
import app.expenses.core.domain.ReferenceReuse.Verdict.SAME_TRANSACTION
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A bank reference already on an entry, carried by a second text: the same transaction again, or the payment being undone. The entry's type may have been changed by the person
 * (an expense that was really income, a transfer); then the entry's kind no longer says which way its first text pointed, and a text for the same transaction must not become a reversal.
 */
class ReferenceReuseTest {
    private data class Case(val what: String, val kind: String, val originalOutgoing: Boolean?, val textOutgoing: Boolean, val expected: ReferenceReuse.Verdict)

    @Test
    fun everyCombinationOfTheEntrysKindItsOriginalWordsAndTheNewText() {
        val cases = listOf(
            // untouched entries: the same direction is the same transaction told again, the other direction is the payment undone
            Case("a debit text again for an expense", "EXPENSE", true, true, SAME_TRANSACTION),
            Case("a credit text again for income", "INCOME", false, false, SAME_TRANSACTION),
            Case("a credit text for an expense it reverses", "EXPENSE", true, false, REVERSAL),
            Case("a debit text for the income it reverses", "INCOME", false, true, REVERSAL),
            Case("a refund entry from a credit text, a debit text undoes it", "REFUND", false, true, REVERSAL),
            Case("a refund entry from a credit text, the credit text again", "REFUND", false, false, SAME_TRANSACTION),
            // an expense the person changed to income: any text with its reference is the same transaction (the bug: the debit text again was taken as a reversal and recorded twice)
            Case("expense changed to income, the debit text arrives again", "INCOME", true, true, SAME_TRANSACTION),
            Case("expense changed to income, a credit text with the reference", "INCOME", true, false, SAME_TRANSACTION),
            // income changed to an expense
            Case("income changed to an expense, the credit text arrives again", "EXPENSE", false, false, SAME_TRANSACTION),
            Case("income changed to an expense, a debit text with the reference", "EXPENSE", false, true, SAME_TRANSACTION),
            // a transfer holds the transaction whichever way a text points, changed or not
            Case("expense changed to a transfer, the debit text again", "TRANSFER", true, true, SAME_TRANSACTION),
            Case("expense changed to a transfer, a credit text", "TRANSFER", true, false, SAME_TRANSACTION),
            Case("income changed to a transfer, the credit text again", "TRANSFER", false, false, SAME_TRANSACTION),
            Case("a transfer with no readable words", "TRANSFER", null, true, SAME_TRANSACTION),
            // no readable original words: the kind stands for the direction, as it did before types could be changed
            Case("no words, expense, credit text", "EXPENSE", null, false, REVERSAL),
            Case("no words, expense, debit text", "EXPENSE", null, true, SAME_TRANSACTION),
            Case("no words, income, debit text", "INCOME", null, true, REVERSAL),
            Case("no words, income, credit text", "INCOME", null, false, SAME_TRANSACTION),
        )
        val wrong = cases.filter { ReferenceReuse.verdict(it.kind, it.originalOutgoing, it.textOutgoing) != it.expected }.map { it.what }
        assertEquals(emptyList(), wrong)
    }
}

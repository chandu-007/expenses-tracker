package app.expenses.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AmountMathTest {
    private fun v(text: String, d: Int = 2) = AmountMath.evaluate(text, d)

    @Test fun aPlainAmountIsLeftAsTyped() {
        assertEquals(AmountMath.Outcome.Plain("450"), v("450"))
        assertEquals(AmountMath.Outcome.Plain("12."), v("12."), "a dot being typed is kept")
        assertEquals(AmountMath.Outcome.Plain(""), v(""))
    }

    @Test fun addingAndSubtracting() {
        assertEquals(AmountMath.Outcome.Value("1250", true), v("450+800"))
        assertEquals(AmountMath.Outcome.Value("1050", true), v("1200-150"))
        assertEquals(AmountMath.Outcome.Value("1250.5", true), v("450.25+800.25"))
    }

    @Test fun timesAndDivideComeFirstAndEveryAlphabetWorks() {
        assertEquals(AmountMath.Outcome.Value("531", true), v("450*1.18"))
        assertEquals(AmountMath.Outcome.Value("531", true), v("450×1.18"))
        assertEquals(AmountMath.Outcome.Value("531", true), v("450x1.18"))
        assertEquals(AmountMath.Outcome.Value("225", true), v("900/4"))
        assertEquals(AmountMath.Outcome.Value("225", true), v("900÷4"))
        assertEquals(AmountMath.Outcome.Value("1000", true), v("100+300*3"), "times before plus")
        assertEquals(AmountMath.Outcome.Value("109.5", true), v("100+19/2"), "divide before plus")
    }

    @Test fun roundingToTheMinorUnit() {
        assertEquals(AmountMath.Outcome.Value("33.33", true), v("100/3"))
        assertEquals(AmountMath.Outcome.Value("66.67", true), v("200/3"))
        assertEquals(AmountMath.Outcome.Value("0.01", true), v("0.005*1+0"), "half rounds up")
        assertEquals(AmountMath.Outcome.Value("33", true), v("100/3", 0))
        assertEquals(AmountMath.Outcome.Value("1.235", true), v("1.2345+0", 3))
    }

    @Test fun whileAnOperatorEndsItTheRestIsWorkedOutAndItIsNotComplete() {
        assertEquals(AmountMath.Outcome.Value("450", false), v("450+"))
        assertEquals(AmountMath.Outcome.Value("1250", false), v("450+800×"))
    }

    @Test fun whatCannotBeWorkedOut() {
        assertEquals(AmountMath.Outcome.Error, v("5/0"))
        assertEquals(AmountMath.Outcome.Error, v("100-200"), "a negative amount is not an amount")
        assertEquals(AmountMath.Outcome.Error, v("+5"))
        assertEquals(AmountMath.Outcome.Error, v("1..2+3"))
        assertEquals(AmountMath.Outcome.Error, v("9".repeat(40) + "+1"))
        assertEquals(AmountMath.Outcome.Value("0", true), v("100-100"))
    }

    @Test fun anOperatorKeyReplacesAnOperatorAndIsNotAcceptedFirst() {
        assertEquals("450+", AmountMath.withOperator("450", '+'))
        assertEquals("450-", AmountMath.withOperator("450+", '-'))
        assertEquals("", AmountMath.withOperator("", '+'))
        assertEquals("450×", AmountMath.withOperator("450.", '×'))
        assertTrue(AmountMath.hasOperator("450*2")); assertFalse(AmountMath.hasOperator("450.5"))
        assertEquals("1×2÷3-4", AmountMath.normalise("1*2/3−4"))
        assertTrue(AmountMath.isAllowed('x') && AmountMath.isAllowed('7') && !AmountMath.isAllowed('a'))
    }

    @Test fun thePadFollowsTheRulesOfTypingOnTheLastNumber() {
        fun keys(vararg k: String, d: Int = 2) = k.fold("") { acc, key -> AmountMath.press(acc, key, d) }
        assertEquals("450+800", keys("4", "5", "0", "+", "8", "0", "0"))
        assertEquals("0.5", keys(".", "5"), "a dot first starts 0.")
        assertEquals("12.34", keys("1", "2", ".", "3", "4", "5"), "at most two decimals")
        assertEquals("12", keys("1", "2", ".", d = 0), "no dot in a currency without decimals")
        assertEquals("7", keys("0", "7"), "no leading zero before a digit")
        assertEquals("5000", keys("5", "00", "0"))
        assertEquals("0", keys("0", "00"))
        assertEquals("450+0.5", keys("4", "5", "0", "+", ".", "5"), "the dot goes to the second number")
        assertEquals("450+1.50", keys("4", "5", "0", "+", "1", ".", "5", "0"))
        assertEquals("450", keys("4", "5", "0", "+", "<"), "backspace takes the operator off")
        assertEquals("45", keys("4", "5", "0", "<"))
        assertEquals("450×", keys("4", "5", "0", "+", "×"))
        assertEquals("", keys("+"))
        assertEquals(AmountMath.MAX_LENGTH, keys(*Array(40) { "9" }).length)
    }
}

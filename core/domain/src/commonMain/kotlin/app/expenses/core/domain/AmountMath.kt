package app.expenses.core.domain

import kotlin.math.abs
import kotlin.math.floor

/**
 * Maths in the amount (UI S2, N-02): "450+800", "1200-150", "450*1.18", "900/4" worked out as it is typed. Four operations, usual precedence (times and divide before plus and minus), no brackets,
 * decimals. The result is rounded to the currency's minor unit (half away from zero) and is what the entry is saved with; the typed expression is only for the screen.
 *
 * Keys and typing may use `+ - × ÷`, `* x X` for times and `/` for divide; [normalise] turns them into the four symbols of [OPS]. Arithmetic is done in `Double` (amounts here are far below 2^53 minor units,
 * so a result is exact to the last digit that is kept); the rounding to the minor unit is done on the scaled integer, never on the printed text.
 */
object AmountMath {
    const val PLUS = '+'
    const val MINUS = '-'
    const val TIMES = '×'
    const val DIVIDE = '÷'
    val OPS = charArrayOf(PLUS, MINUS, TIMES, DIVIDE)

    /** An amount may not be longer than this many characters as typed (an expression included). */
    const val MAX_LENGTH = 32

    /** What an expression is worth. */
    sealed interface Outcome {
        /** No operator: the text is a plain amount, kept exactly as typed (including a trailing dot while it is being typed). */
        data class Plain(val text: String) : Outcome

        /** An expression: [text] is the worked-out amount in plain form ("531", "33.33"). [complete] is false while it ends on an operator ("450+": [text] is what is there so far, 450). */
        data class Value(val text: String, val complete: Boolean) : Outcome

        /** Cannot be worked out: it divides by zero, comes to a negative amount, has an operator with nothing before it, or is too large. */
        data object Error : Outcome
    }

    /** True when [c] is a character the amount may hold: a digit, the dot, or one of the operators in any of its spellings. */
    fun isAllowed(c: Char): Boolean = c.isDigit() || c == '.' || c in "+-−×÷*/xX"

    /** The text with every spelling of an operator turned into the four of [OPS]. */
    fun normalise(text: String): String = buildString(text.length) {
        for (c in text) append(when (c) { '*', 'x', 'X' -> TIMES; '/' -> DIVIDE; '−' -> MINUS; else -> c })
    }

    fun hasOperator(text: String): Boolean = normalise(text).any { it in OPS }

    /** [text] with [key] pressed on the pad: an operator replaces an operator that ends it (so "450+" then "-" is "450-"), is not accepted first, and never doubles. */
    fun withOperator(text: String, op: Char): String {
        if (text.isEmpty()) return text
        val t = normalise(text)
        if (t.length >= MAX_LENGTH) return t
        return if (t.last() in OPS) t.dropLast(1) + op else if (t.last() == '.') t.dropLast(1) + op else t + op
    }

    /** The last number of [text] (what follows its last operator). */
    fun lastNumber(text: String): String = normalise(text).takeLastWhile { it !in OPS }

    /**
     * [text] after [key] is pressed on the amount pad (UI S2): a digit, "00", ".", "<" (backspace) or an operator. Digits and the dot go to the last number of an expression and follow the rules of typing one:
     * one dot, at most [decimals] after it, no leading zero before another digit, a dot with no digit before it starts "0.".
     */
    fun press(text: String, key: String, decimals: Int): String {
        val t = normalise(text)
        return when (key) {
            "<" -> t.dropLast(1)
            "+", "-", "×", "÷" -> withOperator(t, key[0])
            "00" -> press(press(t, "0", decimals), "0", decimals)
            "." -> {
                val n = lastNumber(t)
                if (decimals == 0 || '.' in n || t.length >= MAX_LENGTH) t else if (n.isEmpty()) "${t}0." else "$t."
            }
            else -> {
                val n = lastNumber(t)
                when {
                    t.length >= MAX_LENGTH -> t
                    '.' in n && n.substringAfter('.').length >= decimals -> t
                    n == "0" -> t.dropLast(1) + key
                    else -> t + key
                }
            }
        }
    }

    /** Works out [text] for a currency with [decimals] minor digits (0 to 3). */
    fun evaluate(text: String, decimals: Int): Outcome {
        val t = normalise(text)
        if (t.length > MAX_LENGTH || t.any { !(it.isDigit() || it == '.' || it in OPS) }) return Outcome.Error
        if (t.none { it in OPS }) return Outcome.Plain(text)
        if (t.first() in OPS) return Outcome.Error
        val complete = t.last() !in OPS
        val body = if (complete) t else t.dropLast(1)
        // split into numbers and operators
        val numbers = ArrayList<Double>(); val ops = ArrayList<Char>()
        var start = 0
        for (i in body.indices) if (body[i] in OPS) {
            numbers += number(body.substring(start, i)) ?: return Outcome.Error
            ops += body[i]; start = i + 1
        }
        numbers += number(body.substring(start)) ?: return Outcome.Error
        // times and divide first, left to right
        var i = 0
        while (i < ops.size) {
            if (ops[i] == TIMES || ops[i] == DIVIDE) {
                val r = if (ops[i] == TIMES) numbers[i] * numbers[i + 1] else if (numbers[i + 1] == 0.0) return Outcome.Error else numbers[i] / numbers[i + 1]
                numbers[i] = r; numbers.removeAt(i + 1); ops.removeAt(i)
            } else i++
        }
        var total = numbers[0]
        for (k in ops.indices) total = if (ops[k] == PLUS) total + numbers[k + 1] else total - numbers[k + 1]
        if (total < 0.0 || total.isNaN() || total.isInfinite() || total > MAX_VALUE) return Outcome.Error
        val scale = pow10(decimals)
        val minor = floor(total * scale + 0.5 + 1e-9).toLong()
        return Outcome.Value(plain(minor, decimals), complete)
    }

    private const val MAX_VALUE = 1.0e12

    private fun number(s: String): Double? {
        if (s.isEmpty() || s == "." || s.count { it == '.' } > 1) return null
        return s.toDoubleOrNull()?.takeIf { abs(it) <= MAX_VALUE }
    }

    private fun pow10(n: Int): Double { var r = 1.0; repeat(n) { r *= 10.0 }; return r }

    /** [minor] units as a plain amount with no trailing zeros after the dot: 53100 with 2 digits is "531", 3333 is "33.33". */
    fun plain(minor: Long, decimals: Int): String {
        if (decimals <= 0) return minor.toString()
        val scale = pow10(decimals).toLong()
        val whole = minor / scale; val frac = (minor % scale).toString().padStart(decimals, '0').trimEnd('0')
        return if (frac.isEmpty()) whole.toString() else "$whole.$frac"
    }
}

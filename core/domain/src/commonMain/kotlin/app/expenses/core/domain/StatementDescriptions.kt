package app.expenses.core.domain

/**
 * Tidies the description cell of a row read from the text of a statement PDF or photo ([StatementTextParser]); never used for CSV or Excel files.
 * The statement line is kept whole in [StatementTxn.sourceText], so nothing is lost by shortening what the list shows.
 *
 * ASSUMED, not seen on a real statement: the examples this was written from (an HDFC-style savings statement) wrap the narration at a fixed width, which splits
 * words ("SAV" / "INGS"), and write transfers as MODE-name-account/bank code-reference with `-` between the parts. Anything else is left as it was read.
 */
object StatementDescriptions {
    private const val MIN_WRAP_WIDTH = 20

    /**
     * Puts the lines of one description together. The bank cuts a narration at the width of its column, so a line that is as long as the longest of the
     * statement's wrapped lines ([width]; pass 0 when unknown) was cut wherever the width ran out, possibly inside a word: that line and the next are joined
     * with no space. Only when at least two lines in the statement reached that width ([filled]), since one long line can be a coincidence; otherwise a space,
     * which is the safe mistake. Lines that did not reach the width are also joined without a space when the two halves of the word make a word that the
     * statement spells out whole somewhere else ([known], upper case: "SAV" + "INGS" with "SAVINGS" printed elsewhere).
     */
    fun join(parts: List<String>, width: Int, filled: Int, known: Set<String> = emptySet()): String {
        val usable = parts.filter { it.isNotBlank() }
        if (usable.isEmpty()) return ""
        val sb = StringBuilder(usable[0])
        for (i in 1 until usable.size) {
            val before = usable[i - 1]
            val glue = width >= MIN_WRAP_WIDTH && filled >= 2 && before.length >= width - 1 &&
                before.last().isLetterOrDigit() && usable[i].first().isLetterOrDigit()
            val word = (TAIL_WORD.find(before)?.value.orEmpty() + HEAD_WORD.find(usable[i])?.value.orEmpty()).uppercase()
            val spelled = TAIL_WORD.containsMatchIn(before) && HEAD_WORD.containsMatchIn(usable[i]) && word in known
            if (!glue && !spelled) sb.append(' ')
            sb.append(usable[i])
        }
        return sb.toString()
    }

    private val TAIL_WORD = Regex("[A-Za-z]+$")
    private val HEAD_WORD = Regex("^[A-Za-z]+")

    /** The words of [text] (upper case, four letters or more) for [join]'s `known`. */
    fun words(text: String): Set<String> = Regex("[A-Za-z]{4,}").findAll(text).map { it.value.uppercase() }.toSet()

    private val MODE = Regex("^(UPI|NEFT|IMPS|RTGS|ACH|NACH|ECS|POS|TPT|FT|SI)(?:\\s+(?:CR|DR|D|C))?$", RegexOption.IGNORE_CASE)
    private val DIRECTION = Regex("^(CR|DR|D|C)$", RegexOption.IGNORE_CASE)
    private val IFSC = Regex("^[A-Za-z]{4}0[A-Za-z0-9]{6}$")
    private val ALNUM = Regex("^[A-Za-z0-9]+$")
    internal val MASKED_ACCOUNT = Regex("^[Xx*]{4,}\\d{3,4}$")
    private val MASKED_CARD = Regex("^(?:\\d{4}[Xx*]{4,}\\d{2,4}|[Xx*]{4,}\\d{2,4})$")
    private val POS_FORM = Regex("^(POS|ATW)\\s+(\\S+)\\s+(.+)$", RegexOption.IGNORE_CASE)

    /** A segment that carries no name: a bank code, a reference, a handle, the direction. */
    private fun isNoise(segment: String): Boolean {
        val s = segment.trim()
        if (s.isEmpty() || DIRECTION.matches(s)) return true
        if ('@' in s) return true // a UPI handle: the part after @ is the bank
        val compact = s.replace(" ", "")
        if (IFSC.matches(compact)) return true
        if (ALNUM.matches(compact) && compact.any { it.isDigit() } && (compact.length >= 8 || compact.all { it.isDigit() } && compact.length >= 6)) return true
        return false
    }

    private fun looksLikeName(s: String): Boolean = s.count { it.isLetter() } >= 2 && s.count { it.isDigit() } <= s.count { it.isLetter() }

    /**
     * The counterparty of a UPI/NEFT/IMPS/RTGS/ACH/NACH/ECS/POS narration ("UPI-SHOP NAME-shop@bank-HDFC0000001-123456789012-note" gives "SHOP NAME"), or [raw]
     * with its spaces tidied when no name can be found. Never empty unless [raw] is, and tidying a result again changes nothing.
     */
    fun tidy(raw: String): String {
        val cleaned = raw.replace(Regex("\\s+"), " ").trim()
        if (cleaned.isEmpty()) return cleaned
        POS_FORM.matchEntire(cleaned)?.let { m ->
            if (MASKED_CARD.matches(m.groupValues[2])) return m.groupValues[3].trim().ifEmpty { cleaned }
        }
        // HDFC words a credit's narration "XXXXXXXXXX1234-TPT-note-name": the sender's masked account number leads, then the mode
        val segments = cleaned.split('-', '/').map { it.trim() }.let { all -> if (all.size > 2 && MASKED_ACCOUNT.matches(all[0])) all.drop(1) else all }
        if (segments.size < 2 || !MODE.matches(segments[0])) return cleaned
        val name = segments.drop(1).firstOrNull { !isNoise(it) && looksLikeName(it) } ?: return cleaned
        return name
    }
}

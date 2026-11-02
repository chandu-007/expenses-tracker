package app.expenses.core.domain

/**
 * The income category the words of a captured CREDIT text point at ("Interest credited", "SALARY FOR OCT", "Dividend", "cashback"): the credit counterpart of [CategoryHints] and [PayeeWords].
 * Only clear wording counts. Money from a person ("credited by RAVI KUMAR") says nothing about what it was for, so it gets nothing from here: it is learned from what the person chooses, never
 * guessed. A text whose words point at two different categories ("interest on a refund") gives nothing. What this returns is always a guess for the person to check ([SuggestionSource.CREDIT_WORDS]
 * is never added by "Add all" or a swipe), and only used when the person has a category of that name.
 *
 * A refund or reversal of an earlier expense is NOT handled here as income: `KindRules` reads it as a refund and To review ties it to the expense it undoes (no double count). This only decides
 * the category of the rest: a refund or cashback with no expense to tie it to is income, in "Refunds and cashback".
 */
object IncomeWords {
    private val interest = Regex("""(?i)\binterest\b|\bint\.?\s*pd\b|\bint\.?\s*cr(?:edit(?:ed)?)?\b|\bsb\s*int\b""")
    private val salary = Regex("""(?i)\bsalary\b|\bpayroll\b|\bwages\b|\bsalaries\b|(?:^|[\s\-/:])sal(?=$|[\s\-/.:,])""")
    private val dividend = Regex("""(?i)\bdividend\b|\bdividends\b""")
    private val refund = Regex("""(?i)\bcash\s*back\b|\bcashback\b|\brefund(?:ed|s)?\b|\breversal\b|\breversed\b|\brevoked\b""")
    private val reimbursement = Regex("""(?i)\breimburse(?:d|ment|ments)?\b""")
    private val rent = Regex("""(?i)\brent\b(?!\s+(?:a|an)\b)""")
    private val gift = Regex("""(?i)\bgift\b|\bgifts\b|\bshagun\b|\bbirthday\b""")

    private val all = listOf(
        "Interest" to interest, "Salary" to salary, "Dividends" to dividend, "Refunds and cashback" to refund,
        "Reimbursement" to reimbursement, "Rent received" to rent, "Gifts received" to gift,
    )

    /** The income category name the words of [body] point at, or null when none does or when they point at more than one. */
    fun categoryNameFor(body: String): String? {
        val text = body.replace(Regex("\\s+"), " ")
        val hits = all.filter { (_, re) -> re.containsMatchIn(text) }.map { it.first }
        return hits.singleOrNull()
    }
}

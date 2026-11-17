package app.expenses.core.domain

/**
 * The one sentence above a report (UI S4): what the biggest part of the spending is, in words, so the page says its point before the chart does. Pure text, no formatting of its own:
 * the amounts arrive already formatted.
 */
object ReportSentence {
    /**
     * [kind] is what the report is grouped by: "category", "account", "tag" or "merchant". [topShare] is a whole percent of the spending; [topAmount] and [total] are formatted amounts (either may be
     * empty). An entry with several tags counts under each tag, so a tag's share is not a share of the whole and the sentence names the amount only.
     */
    fun headline(kind: String, topName: String?, topShare: Int, topAmount: String, total: String): String {
        val name = topName?.trim().orEmpty()
        if (name.isEmpty()) return ""
        val ofTotal = if (topAmount.isNotEmpty() && total.isNotEmpty()) " ($topAmount of $total)" else ""
        return when (kind) {
            "tag" -> if (topAmount.isNotEmpty()) "The most is under the tag $name: $topAmount." else "The most is under the tag $name."
            "account" -> "$topShare% of what you spent was paid from $name$ofTotal."
            "merchant" -> "$name is $topShare% of what you spent$ofTotal."
            else -> if (topShare >= 100) "All of what you spent went to $name$ofTotal." else "$name is $topShare% of what you spent$ofTotal."
        }
    }

    /** What a screen reader says for a trend bar: "November: ₹34,780. ₹22,350 more than October." */
    fun trendBar(label: String, amount: String, change: String?): String =
        if (change.isNullOrEmpty()) "$label: $amount" else "$label: $amount. $change"

    /** "₹22,350 more than Oct" / "₹4,070 less than Oct" / "the same as Oct" for two formatted amounts and their minor-unit difference ([diffAbs] formatted, [sign] = sign of this minus the previous). */
    fun change(diffAbsFormatted: String, sign: Int, previousLabel: String): String? = when {
        previousLabel.isEmpty() -> null
        sign > 0 -> "$diffAbsFormatted more than $previousLabel"
        sign < 0 -> "$diffAbsFormatted less than $previousLabel"
        else -> "The same as $previousLabel"
    }
}

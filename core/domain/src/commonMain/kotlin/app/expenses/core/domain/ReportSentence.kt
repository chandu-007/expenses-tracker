package app.expenses.core.domain

/**
 * The one sentence above a report (UI S4): what the biggest part of the spending is, in words, so the page says its point before the chart does. Pure text, no formatting of its own:
 * the amounts arrive already formatted.
 */
object ReportSentence {
    /**
     * [kind] is what the report is grouped by: "category", "account", "tag" or "merchant". [topShare] is a whole percent of the spending; [topAmount] is a formatted amount (may be
     * empty). The total is not repeated: the figure above says it. An entry with several tags counts under each tag, so a tag's share is not a share of the whole and the sentence names the amount only.
     */
    fun headline(kind: String, topName: String?, topShare: Int, topAmount: String): String {
        val name = topName?.trim().orEmpty()
        if (name.isEmpty()) return ""
        val share = if (topShare < 1) "less than 1%" else "$topShare%"
        return when (kind) {
            "tag" -> if (topAmount.isNotEmpty()) "The most is under the tag $name: $topAmount." else "The most is under the tag $name."
            "account" -> if (topAmount.isNotEmpty()) "$share of what you spent ($topAmount) was paid from $name." else "$share of what you spent was paid from $name."
            else -> when {
                topShare >= 100 -> if (topAmount.isNotEmpty()) "All of what you spent went to $name ($topAmount)." else "All of what you spent went to $name."
                topAmount.isNotEmpty() -> "$name took $topAmount, $share of what you spent."
                else -> "$name is $share of what you spent."
            }
        }
    }

    /** The comparison under the figure: [text] with its arrow and the percent, and which way it went ([more] null: the same). */
    class Comparison(val text: String, val more: Boolean?)

    /**
     * "▼ ₹492 less than Oct so far (<1%)" or, for a finished period, "▲ ₹200 more than Oct (+66%)". [previous] must already be like for like (see `ReportMath.samePointWindow`); [open] adds
     * "so far". [previousName] names the earlier period ("Oct", "2025"). Null when there is nothing to compare with. The percent is [PeriodCompare.pill]'s, so Home and Reports say the same.
     */
    fun comparison(current: Long, previous: Long, previousName: String, open: Boolean, format: (Long) -> String): Comparison? {
        if (previous <= 0) return null
        val diff = current - previous
        val soFar = if (open) " so far" else ""
        if (diff == 0L) return Comparison("Same as $previousName$soFar", null)
        val percent = PeriodCompare.pill(current, previous)?.drop(2).orEmpty()
        return if (diff > 0) Comparison("▲ ${format(diff)} more than $previousName$soFar ($percent)", true) else Comparison("▼ ${format(-diff)} less than $previousName$soFar ($percent)", false)
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

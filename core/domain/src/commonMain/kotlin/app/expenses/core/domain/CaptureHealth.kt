package app.expenses.core.domain

/** One group of capture events: how many texts from [root] ended in [outcome], and when the last one did. Words are never part of it. */
data class CaptureCount(val root: String, val outcome: String, val count: Long, val lastAt: Long)

enum class CaptureStatus {
    /** The switch is off. */
    OFF,

    /** The switch is on but Android has not allowed the app to receive texts, so nothing can arrive. */
    PERMISSION_MISSING,

    /** On and allowed, but not one text has been looked at in the period. */
    NOTHING_SEEN,

    /** On and allowed, texts were seen, but none recently: a quiet phone or a delivery problem, which the app cannot tell apart. */
    QUIET,
    WORKING,
}

data class SenderRow(val root: String, val name: String?, val captured: Long, val ignored: Long, val lastAt: Long)

data class CaptureHealthReport(
    val status: CaptureStatus, val captured: Long, val duplicates: Long, val otps: Long, val personal: Long, val notices: Long, val declined: Long,
    val lastCapturedAt: Long?, val lastSeenAt: Long?, val senders: List<SenderRow>, val unknownSenders: List<SenderRow>,
    /** Texts and notifications that were bills to pay. */
    val bills: Long = 0,
)

object CaptureHealth {
    /** No text for this long, after some were seen, is reported as quiet. */
    const val QUIET_AFTER_MS = 7L * 86_400_000

    /** Outcomes that mean the text was understood and used: a transaction or a bill. */
    private val FOUND = setOf("CAPTURED", "BILL")

    fun report(counts: List<CaptureCount>, now: Long, switchOn: Boolean, permission: Boolean, names: Map<String, String> = emptyMap()): CaptureHealthReport {
        fun sum(outcome: String) = counts.filter { it.outcome == outcome }.sumOf { it.count }
        val seen = counts.maxOfOrNull { it.lastAt }
        val status = when {
            !switchOn -> CaptureStatus.OFF
            !permission -> CaptureStatus.PERMISSION_MISSING
            seen == null -> CaptureStatus.NOTHING_SEEN
            now - seen > QUIET_AFTER_MS -> CaptureStatus.QUIET
            else -> CaptureStatus.WORKING
        }
        val rows = counts.groupBy { it.root }.map { (root, list) ->
            SenderRow(root, names[root], list.filter { it.outcome in FOUND }.sumOf { it.count }, list.filter { it.outcome !in FOUND }.sumOf { it.count }, list.maxOf { it.lastAt })
        }
        val known = rows.filter { it.name != null }.sortedWith(compareByDescending<SenderRow> { it.captured }.thenBy { it.root })
        val unknown = rows.filter { it.name == null && counts.any { c -> c.root == it.root && c.outcome == "NOT_ALLOWLISTED" } }.sortedWith(compareByDescending<SenderRow> { it.ignored }.thenBy { it.root })
        return CaptureHealthReport(
            status, sum("CAPTURED"), sum("DUPLICATE"), sum("OTP"), sum("PERSONAL_SENDER"), sum("NOT_A_TRANSACTION"), sum("DECLINED"),
            counts.filter { it.outcome in FOUND }.maxOfOrNull { it.lastAt }, seen, known, unknown, sum("BILL"),
        )
    }
}

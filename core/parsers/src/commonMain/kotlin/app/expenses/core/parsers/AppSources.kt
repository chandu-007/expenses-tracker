package app.expenses.core.parsers

/**
 * The apps whose notifications may be read when the person turns notification capture on, and the name each is captured under (the same names as
 * [SenderRules.BUILT_IN] holds for them). Anything else posting a notification is never looked at. **The package names are public knowledge of
 * the apps' identifiers; the wording of their notifications was not checked against real ones.**
 */
object AppSources {
    val PACKAGES: Map<String, String> = mapOf(
        "com.google.android.apps.nbu.paisa.user" to "GPAY",
        "com.phonepe.app" to "PHONEPE",
        "net.one97.paytm" to "PAYTM",
        "in.org.npci.upiapp" to "BHIM",
    )

    /**
     * The email apps whose notifications may be read **only when the person turns on email capture** (a separate switch from the payment apps). The name is the
     * sender stored on the pending item; the bank that wrote the email is found from the email's own sender name ([SenderRules.emailIssuer]). Package names are
     * public knowledge of the apps' identifiers; the wording of their notifications was not checked against real ones.
     */
    val EMAIL_PACKAGES: Map<String, String> = mapOf(
        "com.google.android.gm" to "GMAIL",
        "com.microsoft.office.outlook" to "OUTLOOK",
        "com.yahoo.mobile.client.android.mail" to "YAHOO",
    )

    /** The capture name for a notification posted by [packageName], or null when that app is not one that is read. Includes the email apps. */
    fun nameOf(packageName: String, extra: Map<String, String> = emptyMap()): String? = PACKAGES[packageName] ?: EMAIL_PACKAGES[packageName] ?: extra[packageName]

    /** The email app's capture name for [packageName], or null when it is not an email app (the payment apps are not). */
    fun emailNameOf(packageName: String): String? = EMAIL_PACKAGES[packageName]

    /** True for the sender name a pending item carries when it came from an email app's notification. */
    fun isEmailName(senderName: String): Boolean = senderName.trim().uppercase() in EMAIL_PACKAGES.values

    /** Joins a notification's title and text, which apps split between the two ("Paid ₹250" / "to Swiggy"), dropping repeats. */
    fun words(title: CharSequence?, text: CharSequence?, bigText: CharSequence? = null): String = joinDistinct(listOfNotNull(title, bigText ?: text))

    private fun joinDistinct(raw: List<CharSequence>): String {
        val parts = raw.map { it.toString().trim() }.filter { it.isNotEmpty() }
        // a part wholly inside another says nothing new; of two equal parts the first stays
        val kept = parts.filterIndexed { i, p -> parts.withIndex().none { (j, q) -> j != i && q.contains(p) && (q.length > p.length || j < i) } }
        return kept.joinToString(". ")
    }

    /** The most of an email's words that is kept on a pending item (and shown as where it came from). */
    const val EMAIL_WORDS_CAP = 2000

    /** One email read from a notification: the names that might say who sent it (tried in order), and its words as the notification showed them. */
    data class EmailNote(val senderNames: List<String>, val words: String)

    /**
     * The emails one email-app notification carries, as the notification's fields: [title] (the sender's display name in Gmail and Outlook), [text] (the subject),
     * [bigText] (the start of the message, when the app expands it), [lines] (an inbox-style notification that itself lists several emails, as older Gmail did) and
     * [subText] (the receiving account, which is never kept; it is tried only as a place a sender name might be).
     *
     * A notification with lines gives one email per line, each read alone: the title of such a notification ("3 new messages") is not a sender. Otherwise it is one
     * email whose words are the title, the subject and the expanded text, with nothing repeated. **How Gmail, Outlook and Yahoo fill these fields was not checked
     * against real notifications**; this is the documented Android layout.
     */
    fun emailNotes(title: CharSequence?, text: CharSequence?, bigText: CharSequence?, lines: List<CharSequence>?, subText: CharSequence?): List<EmailNote> {
        val one = lines.orEmpty().map { it.toString().trim() }.filter { it.isNotEmpty() }
        val head = title?.toString()?.trim().orEmpty()
        if (one.isNotEmpty()) return one.map { line -> EmailNote(listOf(line.take(SENDER_PREFIX), head).filter { it.isNotEmpty() }, line.take(EMAIL_WORDS_CAP)) }
        val words = joinDistinct(listOfNotNull(title, text, bigText)).take(EMAIL_WORDS_CAP)
        if (words.isBlank()) return emptyList()
        return listOf(EmailNote(listOf(head, subText?.toString()?.trim().orEmpty()).filter { it.isNotEmpty() }, words))
    }

    /** An inbox-style line reads "Sender  subject start": only its beginning can name the sender. */
    private const val SENDER_PREFIX = 60
}

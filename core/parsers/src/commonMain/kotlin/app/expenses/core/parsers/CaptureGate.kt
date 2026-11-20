package app.expenses.core.parsers

/**
 * Whether a posted notification may be read, decided **before any of its words are**: first from who posted it ([notificationSource]: a listed payment app, a listed
 * email app, or nothing), then from that source's own switch ([mayRead]). Only when both say yes does the listener pull the notification's title and text.
 */
object CaptureGate {
    enum class Kind { PAYMENT_APP, EMAIL_APP }

    /** A notification that comes from a source that is read, with the name it is captured under ([AppSources]). */
    data class Source(val kind: Kind, val name: String)

    /** The name a test post that does not stand in for an email app is captured under (test and measurement builds only). */
    const val TEST_POST_NAME = "GPAY"

    /**
     * The source of a notification posted by [packageName], or null when it is never read. [ownPackage] is this app: its own notifications (reminders carry amounts) are
     * never read, unless [testPost] (a test or measurement build's marked post, never true in a release build), which stands in for [testStandsFor] when that names an
     * email app and for a payment app otherwise.
     */
    fun notificationSource(packageName: String, ownPackage: String, testPost: Boolean, testStandsFor: String? = null): Source? {
        if (packageName == ownPackage && !testPost) return null
        val poster = if (testPost) testStandsFor ?: packageName else packageName
        AppSources.emailNameOf(poster)?.let { return Source(Kind.EMAIL_APP, it) }
        val name = (if (testPost) TEST_POST_NAME else AppSources.nameOf(packageName)) ?: return null
        return Source(Kind.PAYMENT_APP, name)
    }

    /** Whether [source]'s words may be read: a payment app's only while notification capture is on, an email app's only while the separate email switch is on. */
    fun mayRead(source: Source, paymentAppsOn: Boolean, emailOn: Boolean): Boolean = when (source.kind) {
        Kind.PAYMENT_APP -> paymentAppsOn
        Kind.EMAIL_APP -> emailOn
    }
}

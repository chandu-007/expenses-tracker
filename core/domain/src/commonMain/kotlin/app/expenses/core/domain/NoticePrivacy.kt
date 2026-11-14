package app.expenses.core.domain

/**
 * What a notice may say. A notification can be read by anyone who holds the phone, whatever the person set on the lock screen, so two things are decided here for both phones:
 *
 * - [masked]: when **Hide amounts** is on in the app, or the **App lock** is on, no notice carries an amount. Each kind that carries figures says a plain line instead
 *   ("Open the app to see how much is left"); the text of a bill found in a message is built without its amount by the notification service in the first place, and a tracked
 *   bill's text keeps only its hint.
 * - [publicTitle] and [PUBLIC_TEXT]: what the lock screen shows for a notice when the phone is set to hide sensitive content. Every kind that can carry an amount has one, so the phone's own
 *   placeholder (a bare app name) is never the only protection and the wording is ours on every Android version; a kind that carries none ([NoticeKind.CARD_CLEARED]) has none.
 *
 * Titles name the thing (a bill, a card, a budget), never an amount, except the weekly recap's, which is replaced by [RECAP_TITLE] when masked.
 */
object NoticePrivacy {
    /** The one line under every lock-screen title. */
    const val PUBLIC_TEXT = "Open the app to see it"

    /** The recap's own title says how much was spent; when masked it says only what it is. */
    const val RECAP_TITLE = "Your weekly recap"

    /** [n] as it may be shown when [hide] (Hide amounts or App lock): the words of the iPhone's masked notices, now shared by both phones. The key is kept, so what was shown is recorded as without masking. */
    fun masked(n: Notice, hide: Boolean): Notice {
        if (!hide) return n
        return when (n.kind) {
            NoticeKind.BILL_DUE -> n.copy(text = "Open the app to post or skip it")
            NoticeKind.CARD_DUE -> n.copy(text = "Open the app to see what to pay")
            NoticeKind.BUDGET_NEAR, NoticeKind.BUDGET_OVER -> n.copy(text = "Open the app to see how much is left")
            NoticeKind.BUDGET_PACE -> n.copy(text = "Open the app to see how your spending is going")
            NoticeKind.BUDGET_WRAPUP -> n.copy(text = "Open the app to see how last month went")
            NoticeKind.CARD_STATEMENT, NoticeKind.CARD_LIMIT, NoticeKind.CARD_CLEARED -> n.copy(text = "Open the app to see your card")
            NoticeKind.WEEKLY_RECAP -> n.copy(title = RECAP_TITLE, text = "Open the app to see how last week went")
            // "₹1,842.50 · Mark it paid when it is done": the hint is the last part (a reminder snoozed before Hide amounts was turned on still has the amount)
            NoticeKind.BILL_TRACKED -> n.copy(text = n.text.substringAfterLast(" · "))
            NoticeKind.BILL_FOUND -> n // built without the amount already, by the notification service
        }
    }

    /** The title the lock screen shows for a notice of [kind] when the phone hides sensitive content; null for a kind that carries no figure. */
    fun publicTitle(kind: NoticeKind): String? = when (kind) {
        NoticeKind.BILL_FOUND -> "A bill was found"
        NoticeKind.BILL_TRACKED, NoticeKind.BILL_DUE -> "A bill needs attention"
        NoticeKind.CARD_DUE, NoticeKind.CARD_STATEMENT, NoticeKind.CARD_LIMIT -> "A card needs attention"
        NoticeKind.BUDGET_NEAR, NoticeKind.BUDGET_OVER, NoticeKind.BUDGET_PACE -> "A budget needs attention"
        NoticeKind.BUDGET_WRAPUP -> "Your budget summary"
        NoticeKind.WEEKLY_RECAP -> RECAP_TITLE
        NoticeKind.CARD_CLEARED -> null
    }
}

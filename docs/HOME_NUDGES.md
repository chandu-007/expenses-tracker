# Home nudges: one card at a time

Home used to stack every card that had something to say: a bills banner, the To review banner, "money to get back", "N expenses have no category" and the "Getting started" checklist, one under another, above the budget and the entries. On a small screen that was most of the first page. Now **one** card is drawn, the first of the order below that is present and not put off. What else is waiting is one line under it ("Also waiting: Expenses with no category. See More"), and every one of them is still a card or a row on the More page.

Code: `HomeNudge`, `HomeNudges` and `NudgeSnooze` in `core/domain/.../HomeNudges.kt` (pure, host-tested by `HomeNudgesTest`); drawn by `EntryListScreen` in `shared/.../ui/Screens.kt`; the put-off times are read with the other small settings on every rebuild (no query over entries).

## The order (highest first; the enum order of `HomeNudge` is the order)
| # | Nudge | Present when | Put off by |
|---|---|---|---|
| 1 | Bills due (`BILLS_DUE`) | a tracked bill, a repeating item that waits for the person, or a card statement is due within a week or overdue | "Not now" (a close button on the card): 24 hours, or until more bills are due than when it was put off |
| 2 | To review (`INBOX`) | texts, alerts or e-mails wait in To review | same |
| 3 | New bills (`BILLS_NEW`) | bills were found in messages and nothing is due this week | same |
| 4 | Money to get back (`WAITING`) | expenses are marked as expected back | same |
| 5 | No category (`CATEGORISE`) | 3 or more expenses have none (capped at 500) | "Not now": its own 7 days (unchanged) |
| 6 | Getting started (`FIRST_WEEK`) | the first-week checklist has steps left | "Hide": for good (unchanged) |

Why this order: money and dates first (a due bill costs a fee or a lapsed policy), then what only the person can place in the books, then tidying, and the checklist last because it is for someone with nothing else to do yet. A card that is put off lets the next one through; one that is put off comes back after the day, or at once when more arrives (a new text, another bill due), so a put-off is never a way to miss something new.

## What is not a Home nudge, and why
- **The budget block and its safe-to-spend line**: part of the Budget section the person arranges in Customise home; it is a figure, not a request.
- **Due today** (the Bills section in Customise home): posting a repeating item is that section's own job.
- **The Welcome card** on an empty Home, which is what the empty page is made of.
- **The backup reminder** and **the iPhone build's "stops opening in N days" notice**: More page only (not on Home). More shows **one** of them: the signing notice outranks the backup reminder (`HomeNudges.moreNotice`; a build that stops opening makes a backup moot; the backup reminder is still in Your data).

## The More page
The cards at the top of More (at most two are drawn) follow the same order and are never put off: Bills (due this week, else new), To review, Money to get back, No category. A card put off on Home is therefore always one tap away there; each also has its row below.

## Tags
`inbox_banner`, `bills_banner`, `waiting_banner`, `categorise_nudge` (+ `categorise_nudge_review`, `categorise_nudge_later`), `first_week_card` are unchanged. New: `nudge_notnow_inbox`, `nudge_notnow_bills`, `nudge_notnow_waiting` (the close buttons), `home_more_nudges` (the "Also waiting" line), `open_categorise` (More row and card).

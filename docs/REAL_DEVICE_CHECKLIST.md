# Real-device checklist

What the automated tests cannot see. Everything below was built and passes on the emulator or simulator only. Tick each line on a real phone,
and write what you saw next to anything that is off. Nothing here has been measured on hardware.

## Android (phone and, if you have one, a tablet)

### Screen readers (TalkBack)
Turn on Settings, Accessibility, TalkBack. Swipe right through each screen.
- [ ] Home: the month total, each account tile and each entry row are read in a sensible order, with the amount and whether it is money out.
- [ ] Add form: every field says what it is; the category chips say selected or not; Save says whether it is enabled.
- [ ] Search: the From and To date fields say what they are and their value; the clear buttons say what they clear.
- [ ] To review: a card reads its payee, amount and the account it goes to. Swiping right or left to add or skip is not a TalkBack gesture, so
  check that the Add and Skip buttons on the same card are reachable.
- [ ] The first-week card reads "Getting started, 1 of 5 done" and each step says done or not done.
- [ ] Credit card dialog: "Card summary" reads the spend this cycle, the limit used and the due line.

### Large text and display size
Settings, Display, Font size and Display size at their largest.
- [ ] Home, Search and the account bar (This month, Last month, All, Search, Edit account, Show all) still reach every control by scrolling.
- [ ] Nothing important is cut off on the To review cards.

### Two panes (tablet or an unfolded foldable, 840 dp wide or more)
- [ ] Home sits on the left, 400 to 480 dp wide. "Select an entry" shows on the right until you tap a row.
- [ ] Tapping a row opens the editor on the right; Save and Cancel clear it; Back works.
- [ ] More, Reports, Search and the other screens are centred, about 960 dp wide.
- [ ] Rotate while an entry is open. Nothing typed is lost, or you are told.
- [ ] On a phone nothing looks different from before.

### Texts and bank alerts (Play build with the text permission)
- [ ] Allow text reading from More, Capture. The disclosure shows first, then Android's own permission.
- [ ] A real bank text appears in To review with the right account (the last four digits decide which).
- [ ] Add all from a payee when two or more waiting items come from the same shop.

### Photos and statements
- [ ] Read items from a photo: a real shop bill, good light, flat. See what it reads and what it misses.
- [ ] A PDF statement: the preview, then the line "The statement says ... Some lines may be missing" appears only when it should.
- [ ] Imported statement lines have short names (for example `ZERODHA BROKING LTD`), and the full line is under "Where this came from".

### Send feedback
- [ ] More, Send feedback opens your mail app with the version, system and phone model filled in and nothing from your ledger.

### Money and captures added in 0.8.0 (Android and iPhone)
- [ ] Safe to spend (Accounts, under Net worth; Home, under the budget card): with your real balances, cards and bills the figure and the "not counted" note make sense; the Home line can be switched on or off in the figure's own dialog.
- [ ] "I expect this back" on a real expense (a friend or your employer): it shows on Home and under Lent and borrowed; "Got it back" with a real refund and with part of the amount; Undo works.
- [ ] Repeating: switch on set-aside for yearly bills (sinking funds) and look at an EMI row's "payments left" against your real loan.
- [ ] Import a REAL credit card statement and a REAL bank statement that pays that card: the card-payment lines come up as transfers to the right card or as a choice, never silently to another card.
- [ ] Import a real export from Money Manager, YNAB, Splitwise, Cashew or Spendee (the presets were written from the apps' documented columns, no real export was seen).
- [ ] A bank text the app misses: To review, Report, the redacted message shows no name, number or balance, with "Keep the amounts" off and on; send it by mail.
- [ ] Protected copies: Your data, "Protect copies with a passphrase", on a real phone and a real folder (see the iPhone list for the lock/restart cases); restore the copy with the passphrase on a second phone.

### Speed
- [ ] With a year or more of real entries, Home opens and scrolls without a pause, and narrowing to one account is instant.

## iPhone

### Install
- [ ] A new build installed with `xcrun devicectl device install app` keeps the data you had.
- [ ] Settings, General, VPN and Device Management shows the new expiry date (free signing lasts 7 days).

### VoiceOver
Settings, Accessibility, VoiceOver. Swipe right through each screen.
- [ ] The same Home, Add, Search and To review checks as above.
- [ ] The Shortcuts setup screen reads Step 1, Step 2, Step 3 and the status line.

### Capture setup (More, Capture, Shortcuts)
- [ ] Step 1: Open Shortcuts opens the Shortcuts app; Copy the name copies "Capture bank message".
- [ ] Step 2: the automation is created following the steps; "Message Contains" takes the copied trigger words.
- [ ] Step 3: "Send a test now" turns the status line to "Last text received just now" in green.
- [ ] A real bank text then sets the line to a few minutes ago, and shows in To review.

### Widgets, sharing, files
- [ ] Today and Quick add widgets add an entry that shows up in the app.
- [ ] Share to Expenses from Photos or Files opens the import.
- [ ] Automatic copies to a Files folder write a new file when due.
- [ ] With "Protect copies with a passphrase" on (Your data): a copy is made while the phone is locked (after the first unlock since a restart) and is a sealed `.expbak` (opens only through Restore with the passphrase, on this phone and on a second one); after a restart and before the first unlock no copy is written, and never a plain one; turning it off makes plain copies again.
- [ ] Notification buttons (Mark paid, Later today, Tomorrow) work from the lock screen.

### Larger text and iPad (if you have one)
- [ ] Dynamic Type at the largest size: Home and the Shortcuts screen stay usable.
- [ ] iPad: the two-pane layout above. The width check uses the same API as the guide screen but has not been run on an iPad.

## What to send back
For anything that fails: the screen, what you tapped, what you expected, and the version from More. The Send feedback button writes that down for you.

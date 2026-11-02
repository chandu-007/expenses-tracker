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
- [ ] An ICICI Bank credit card spend (it comes from a header ending ICICIT, for example BP-ICICIT-S) appears in To review with the shop's name, the spend (not the available limit), the card's last four digits and the date in the text.
- [ ] More, Capture, Your bank's sender name: type a name (or paste a whole header such as AX-NAME-S) and tap Add: "Added NAME" shows, the box empties and the name appears as a chip below it; adding it again says "Already added"; the x on the chip ("Remove NAME" in TalkBack) takes it away. Leave the screen and come back: the chips are still there.
- [ ] The Today and Quick add home-screen widgets (long-press the home screen, Widgets): with Hide amounts and the App lock off, the total shows as dots with an eye next to the title. Tap the eye: the amount appears at once (also if you placed the widget or opened the app a moment before) and stays until you tap the eye again. Add an entry in the app: the widget's total follows within a few seconds.
- [ ] Widgets after the redesign (long-press the home screen, Widgets, "Spending"): all five are listed with a picture and a description. Place the 4 x 2 one: a rounded card with the month's figure (dots until you tap the eye), "+ Add" and up to three quick-entry chips. With the eye on: a small "↓ 3%" pill beside the caption (spending against last month up to the same day), and on the 4 x 4 a budget bar (if you set a monthly limit), "Where it went" bars and "Latest" entries. Resize one: it reflows without clipping. On Android 12 or newer the colours follow your wallpaper; switch the phone to dark mode (on Android 11 or older the widget is redrawn when the app is running, otherwise within the hour or when you open the app). With Hide amounts or the App lock on there is no eye and nothing beyond dots.
- [ ] **Widgets on a Samsung (One UI) phone, 0.10.3 or later** (0.10.0 to 0.10.2: the eye and the chips did nothing because of a build bug, fixed in 0.10.3; this list is what an emulator cannot show). (1) Settings, Battery, Background usage limits: the app must NOT be under "Sleeping apps" or "Deep sleeping apps" (One UI stops such an app's widgets from running; remove it with the three dots, "Remove"); also turn "Put unused apps to sleep" off or add the app to "Never sleeping apps". (2) Place the widget from Samsung's own widget picker (long-press the home screen, Widgets, "Expenses", "Spending: month and quick add"). (3) Tap the eye: the amount appears at once; tap again: dots. (4) Tap a quick-entry chip (save one first in the app: Add, "Save as a quick entry"): "Added NAME" shows on the widget with Undo, a message appears for a moment, and the entry is in the app's list with the right amount, category and account. (5) Swipe the app away from Recents (and, if you can, restart the phone) and tap the eye and a chip again: they must still work. (6) Turn Hide amounts on (More): the widget says "Amounts hidden", has no eye, a chip still adds and its message has no amount; turn the App lock on: it says "App lock is on", and a tap on the figure opens the app. (7) If anything does nothing: connect the phone and run `adb logcat -s ExpensesWidget GlanceAppWidget:E`: every tap leaves a line there (which tap, how old the app's process was, how long it took, never an amount), and a failure leaves an error with its reason.
- [ ] **The widget's confirmation and the app's freshness (0.10.3):** tap a quick-entry chip: one calm line "✓ Chai ₹20 added  Undo" appears at once with the new total, and goes by itself after about 10 seconds (a few seconds more is the phone being slow, not a fault) without you doing anything; the chips are not pushed out. Tap Undo inside the 10 seconds: the entry is taken back and the widget says so; after they are up the line is simply gone. With Hide amounts on the line has no amount. Then open the app straight after a chip tap (and after an Undo): Home, the list and the totals already show it, without going to Reports and back. Do the same after a bank text arrives while the app is in the background, and after you shared a text to the app on the iPhone.

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

### Transfer candidates (More, Transfer candidates; after 0.9.0)
- [ ] With your real history: the page opens within a second or two on a year or more of entries, says "Looking through your entries" first and then the groups. Nothing changes by opening it (look at Home before and after).
- [ ] Cash withdrawals: the rows are real ATM withdrawals (from SMS or statement imports); note any ATM withdrawal it missed and any row that is not one (a shop with ATM in its name, a card purchase). With exactly one Cash account the rows start ticked; with none, "Create a Cash account" asks first.
- [ ] A cash advance on a credit card is listed in the same group: converting it moves the money from the card to Cash (the card's owed amount stays, the spending line goes).
- [ ] Moves between your own accounts: real pairs (the debit on one bank account and the credit on the other, a few minutes apart) appear, with the two accounts named; a same-amount payment to a person that is not your own move does not. Converting one pair leaves both balances unchanged and removes the entry from spending and from income.
- [ ] Transfers to yourself: only entries that really say so (self transfer, to self, own account); none ticked, and you choose the other account.
- [ ] Make transfers, then Undo on the bar: the same entries come back with their dates, categories, notes and photos; Home's balances and the month total match what they were before.
- [ ] "Not a transfer" on a row, leave the page, kill the app and open it again: the row stays hidden; "Show the N you marked" brings it back.
- [ ] VoiceOver or TalkBack: each row reads as one tick box ("ATM cash, 3 Mar, Main bank to Cash, ₹3,000.00, checked"); the buttons are reachable; the amounts hide with Hide amounts.
- [ ] A tablet or an unfolded foldable: the page is one centred column, the same as Subscriptions.

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

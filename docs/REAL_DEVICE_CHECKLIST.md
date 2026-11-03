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
- [ ] A real bank text appears in To review with the right account (the digits decide which: three for banks that print only three, such as ICICI Bank accounts ("Acct XX123"), four for cards and most banks).
- [ ] **Three-digit account (needs a real ICICI Bank account text):** in More, Accounts, give the ICICI account the digits its texts show (3 digits; the field's line says "Some banks show only 3 digits; enter what your bank texts show"). A real ICICI account text then goes to that account with no question. Give another bank's account the same last three digits: the ICICI text still goes to the ICICI account and the other bank's text to its own. Give two ICICI accounts the same last three digits: the card says "... all end in 123, so I cannot tell which this is" and asks, and Add waits until you pick.
- [ ] **"Which account is this?" (a text whose digits no account carries, or a bank that prints none):** the card shows the account chips and "New account ending 123"; pick one: the card says where it goes, the other waiting cards of the same bank and digits follow at once, "Using X for ICICI Bank ending 123 · N more waiting texts follow" is offered with Undo (Undo puts the question back), "Add all" then counts them. "New account ending 123" opens a small form (name and type filled in; a credit card only when the text says credit card): "Create and use it" makes the account carrying the digits; Undo removes it again. Until a card is answered it is left out of Add all and swipe, and the top says how many and has "Choose the account".
- [ ] Add all from a payee when two or more waiting items come from the same shop.
- [ ] An ICICI Bank credit card spend (it comes from a header ending ICICIT, for example BP-ICICIT-S) appears in To review with the shop's name, the spend (not the available limit), the card's last four digits and the date in the text.
- [ ] More, Capture, Your bank's sender name: type a name (or paste a whole header such as AX-NAME-S) and tap Add: "Added NAME" shows, the box empties and the name appears as a chip below it; adding it again says "Already added"; the x on the chip ("Remove NAME" in TalkBack) takes it away. Leave the screen and come back: the chips are still there.
- [ ] **Senders we skipped (needs a real text from a sender name the list misses):** receive a real bank alert from a header that is not on the built-in list (the usual cause is a bank that uses a second header, like ICICIT before 0.10.1). More, Capture: under the sender chips a block "Senders we skipped" shows that sender NAME with how many of its texts were skipped and when the last one came (never a word of the text). Only names ending -T or -S are listed; a promotional (-P) or government (-G) name, a phone number or an email never is; a name with no suffix appears after its third text. Tap "Treat NAME as my bank": "Added NAME" shows, the name appears as a chip, the row goes, and the NEXT text from it reaches To review (the skipped ones are not kept). Check that no other sender's text was ever read: Capture health still shows only counts.
- [ ] The Today and Quick add home-screen widgets (long-press the home screen, Widgets): with Hide amounts and the App lock off, the total shows as dots with an eye next to the title. Tap the eye: the amount appears at once (also if you placed the widget or opened the app a moment before) and stays until you tap the eye again. Add an entry in the app: the widget's total follows within a few seconds.
- [ ] Widgets after the redesign (long-press the home screen, Widgets, "Spending"): all five are listed with a picture and a description. Place the 4 x 2 one: a rounded card with the month's figure (dots until you tap the eye), "+ Add" and up to three quick-entry chips. With the eye on: a small "↓ 3%" pill beside the caption (spending against last month up to the same day), and on the 4 x 4 a budget bar (if you set a monthly limit), "Where it went" bars and "Latest" entries. Resize one: it reflows without clipping. The "+ Add" button and the bars use the accent you chose in the app (More, Appearance), on every Android version; change the accent in the app and the widget changes colour within a second or two (try violet, teal, orange, in light and dark). Every tap target is at least 48 dp: try the eye, "+ Add", a chip and Undo with a thumb, on a 2 x 1, a 2 x 2 and a 4 x 1. On the smallest 2 x 1 there is no eye: tap the dots once and it says "Show?", tap again within five seconds and the amount shows; tap the amount once and it hides (a stray tap alone shows nothing). Switch the phone to dark mode (the widget is redrawn when the app is running, otherwise within the hour or when you open the app). With Hide amounts or the App lock on there is no eye and nothing beyond dots.
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

### Categories from bank messages (To review, after 0.10.3)
Use your own real texts. Nothing here is checked against a real bank yet: the numbers in the notes come from invented messages.
- [ ] To review: for each of your recent real bank texts note the payee shown on the card (title) and the category it starts with. Write down every card that has a **wrong** category (with the sender and the wording, not the numbers) and every one that shows the bank's name or "no category" although the text names a shop. Wrong ones matter most.
- [ ] Pick a category for one shop you have not used before (say a local grocery) and Add. Wait for the next text of that shop (a card text and a UPI text of the same shop is the best test): it starts with what you chose, and says "what you chose last time".
- [ ] Two waiting cards of one shop, written differently (the card name and the UPI address): choose a category on the first and Add; the second card's category changes to it without leaving the screen. A category you picked yourself on a card is not changed by that.
- [ ] A card marked "This is a guess: please check it" (a name like "tea stall", a payment to a person, Amazon or Paytm) is never added by "Add all" or by swiping; the "Add all" box says how many have no category. Check that "Add all" adds nothing under a category you would call wrong.
- [ ] Pay a friend by name or number twice with different reasons; the app does not settle on the first reason (it asks you to check until you chose the same twice).
- [ ] More, Capture, Category rules, scroll to "What the app has learned": your choices are listed with how many times; Change moves one; Forget removes one; "File N earlier expenses with no category" files them under that category and the Undo bar puts them back (Home totals before and after).
- [ ] Merge one category into another in Categories: the rules and learned choices that named it now name the other one (they used to stop working).
- [ ] Open Reports: spending with no category is shown as "Uncategorised", so a card added without a category is still counted, never lost.

### Money received, Categorise and Undo for rules (To review, after 0.10.4)
Use your own real credit texts (salary, interest, a refund, money from a friend). Nothing here is checked against a real bank yet: the numbers in the notes come from invented messages.
- [ ] To review: a **salary** credit shows "Salary (the words of the text)" and "This is a guess: please check it"; an **interest** credit "Interest"; a **dividend** credit "Dividends"; a cashback "Refunds and cashback". Write down every credit with a **wrong** category (the sender and the wording, not the numbers) and every one whose wording names a purpose but got none (a different bank's way of writing "salary" or "interest" is the likely miss).
- [ ] Money from a friend by UPI (no purpose in the words): the card shows four income chips (Salary, Interest, Gifts received, Refunds and cashback) and "no category"; one tap on a chip and Add. The next credit from the same friend starts with it, marked as a guess; after you choose it twice in a row it no longer asks you to check.
- [ ] A guess on a credit is never added by "Add all" or a swipe; a credit with no guess still is (as income with no category).
- [ ] A refund of something you bought (the amount and shop match an expense from the last 4 months): the card says "Recorded as a refund of ..." and adding it reduces that expense. It does not become income, and no income chips show. A refund with nothing to match is income in "Refunds and cashback".
- [ ] The same person as a payee and as a payer (you pay your landlord by UPI, and he sometimes pays you back): what you choose for the money you receive never changes the category of what you pay.
- [ ] Open an income that has no category from Home: a "Suggested: ..." chip appears when the app knows the payer; one tap puts it on, Save.
- [ ] More, Capture, Category rules: add a rule with **Money in** and an income category ("acme" -> Salary); it fires on a credit that mentions acme and never on a payment that does. Remove a rule: "Removed the rule ..." with **Undo** puts it back in the same place. Forget and Change on "What the app has learned" also have Undo; after Undo the "chosen N times in a row" is what it was.
- [ ] Home: with 3 or more expenses that have no category, a slim card "N expenses have no category" with **Review** and **Not now**. Not now hides it for a week (it comes back after 7 days). Review: one expense at a time, "N left", a suggestion you can tap, Skip, "All done" at the end and Undo on the last choice. Choosing for one payee makes the next expense of that payee suggest the same. On a tablet it is a full screen like the other pages.
- [ ] Home opens and saves as fast as before with a large ledger (the count is worked out a moment after the last save, not when Home opens).

### Quieter Home, To review and Capture (after 0.10.5)
- [ ] Home shows one card at a time (with texts waiting and expenses with no category, only To review; the "Also waiting" line opens More). The close button on a card hides it for today, and a new text brings it back.
- [ ] Search opened from an account (Home, tap an account, Search): the list is readable with no keyboard; all the account chips are in sight; tapping the box and typing keeps the results above the keyboard; dragging the list puts the keyboard away.
- [ ] Select an entry (long press): the bar shows Set category, Make transfer, Delete, More, none cut off at the right, with the largest font too; Tag and Account are under More.
- [ ] To review: each card says "1 of N"; the account question and the other categories are under "More options"; "This is a guess: please check it" is never hidden.
- [ ] More, Capture: Set up, Rules and Health each have a heading and a line; the long explanations are under "Details"; the bank sender names fold.
- [ ] An action on Bills, Accounts (archive, delete), Subscriptions (Not one, Remind me) or Notifications (Send a test) says what it did in a bar at the bottom; Your data goes back to the top to show the result of a backup.
- [ ] Category merge asks "Merge into X?" once more before anything moves.

### Speed
- [ ] With a year or more of real entries, Home opens and scrolls without a pause, and narrowing to one account is instant.

### Permissions given in system settings show on return (Android)
- [ ] More, Capture: turn on "Read payment app notifications"; the phone opens Notification access; turn this app on; press Back. The switch is on and the red "Notification access is not given" line is gone **without leaving the screen**. Do the same from "Open notification access settings". On Samsung One UI the access page may ask you to confirm a warning first.
- [ ] Still on Capture: leave the app (Home), turn the access off in system settings (Settings, Notifications, Advanced, Notification access), open the app again from Recents. The switch is off and the red line is back.
- [ ] More, Capture: turn on "Read bank texts" (Play build: not present), allow the permission in Android's dialog: the switch stays on at once. Take the permission away in Settings, Apps, Expenses, Permissions, and come back: the red "Android has not allowed" line shows.
- [ ] More, Notifications (Android 13+): with notifications turned off for the app the red notice shows; press "Allow notifications" or turn them on in system settings and return: the notice is gone at once.
- [ ] More, Privacy: the Allowed / Not allowed words follow the same changes on return.
- [ ] iPhone: More, Notifications: with notifications off in Settings, the notice shows; go to Settings, Expenses, Notifications, turn them on, come back: the notice is gone (not verified on a phone).

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

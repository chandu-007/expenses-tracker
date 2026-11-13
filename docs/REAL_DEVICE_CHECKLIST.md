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

### Two panes (tablet or an unfolded foldable: a window at least 600 dp wide and 480 dp high)
- [ ] Home sits on the left, 300 to 480 dp wide. "Select an entry" shows on the right until you tap a row.
- [ ] Tapping a row opens the editor on the right; Save and Cancel clear it; Back works.
- [ ] More, Reports, Search and the other screens are centred, about 960 dp wide at most. An entry opened from Search or the calendar covers that screen (there is no Home beside it) and Back returns to it.
- [ ] Rotate while an entry is open. Nothing typed is lost (the app is not rebuilt by a rotation, a fold or a window resize).
- [ ] On a phone nothing looks different from before. A phone on its side (about 800 x 360 dp) is still the phone layout.

### Samsung Galaxy Z Fold (Fold 3 to 7), by hand: only an emulator at the same sizes was used
- [ ] Folded (cover screen, about 344 to 411 dp wide): the phone layout, as on any phone.
- [ ] Unfold with the app open on Home: the two panes appear at once, no restart, the list keeps its scroll position. Fold again: the phone layout returns, still on the same screen.
- [ ] Open an entry on the cover screen, type a new amount without saving, unfold: the entry opens in the right-hand pane with the typed amount. Fold again: it is the full screen with the same amount.
- [ ] Unfolded, both ways up (upright and on its side): two panes, nothing clipped; Search, Reports, To review and More are one centred page.
- [ ] Keyboard up in the right-hand pane (amount, note): still two panes, the Save button above the keyboard.
- [ ] Half open like a book (fold running top to bottom): Home on one side of the crease, the entry on the other, nothing under the crease.
- [ ] Table-top (half open, crease running across): the app fills the half above the crease, nothing below it.
- [ ] Split screen (app next to another app): the half is a phone layout unless it is still 600 dp wide; pop-up view and DeX windows follow their size while you drag the edge.
- [ ] With a keyboard (a Fold with a keyboard case, DeX, a tablet): Ctrl+N opens the add screen and Ctrl+F opens Search from Home, Search or Reports (nothing happens on other pages or during the tour); Meta + / lists both.
- [ ] Search, unfolded: the results are on the left (about 340 to 480 dp), "Select an entry" on the right; tap a result and its editor opens on the right with the results, the words and the scroll position as they were; Save empties the pane and the line shows the new amount; Back closes the entry first and Search second. Filters opens as a sheet over the left side only; the open entry stays in sight.
- [ ] Search, unfolded: touch and hold a result to choose, then tap others: they are ticked (the editor does not open); the bar at the bottom works as on a phone.
- [ ] Search with words, a filter, a ticked entry and an open entry, then fold: one screen (the open entry, with what was typed); unfold: all of it is back; fold and press Back: the results with the words, the filter and the ticks.
- [ ] Keyboard (a case, DeX): Tab leaves the search box and goes through the Filters button to the first result, the arrow keys move between results, Enter opens it beside the list; Ctrl+F in Search puts the cursor in the box; Ctrl+N leaves Search and opens the add screen.
- [ ] Text at 1.3 and 2.0 in Search, unfolded: a result's name and its second line wrap on two lines in the left pane, nothing is cut short.
- [ ] Settings, Display, Screen zoom at the smallest and the largest: two panes while the window is 600 dp wide or more; at the largest zoom an upright Fold inner screen can fall below 600 dp and shows the phone layout (expected).
- [ ] Settings, Apps screen ratio / "Fit to app screen" does not letterbox the app on the inner screen.

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
- [ ] Search opened from an account (Home, tap an account, Search): the list is readable with no keyboard; the account is a chip under the box (tap it to search everywhere); tapping the box and typing keeps the results above the keyboard; dragging the list puts the keyboard away.
- [ ] Search page (Slice 106): the box and a Filters button side by side; one row of chips under them; one summary line ("N results · ₹ spent") that does not scroll away; Filters opens a sheet (a card at the side on a Fold or tablet, at most 480 dp) with Saved searches, Dates, Type, Account, Category, Tag, Amount; each choice changes the results behind it and the button counts them; the badge counts the filters that are on; a chip with a cross takes its filter off; Clear all, Reset and Apply do what they say; Back closes the sheet first. With TalkBack: the order is box, Filters ("Filters, 2 active"), the chips ("Remove filter: category Food"), the summary, the results; the page behind the sheet is not read. At font size 200% (Settings, Display) nothing is cut off and the Filters button is an icon. Hide amounts: the amounts in the summary and chips are dots.
- [ ] Select an entry (long press): the bar shows Set category, Make transfer, Delete, More, none cut off at the right, with the largest font too; Tag and Account are under More.
- [ ] To review: each card says "1 of N"; the account question and the other categories are under "More options"; "This is a guess: please check it" is never hidden.
- [ ] More, Capture: Set up, Rules and Health each have a heading and a line; the long explanations are under "Details"; the bank sender names fold.
- [ ] An action on Bills, Accounts (archive, delete), Subscriptions (Not one, Remind me) or Notifications (Send a test) says what it did in a bar at the bottom; Your data goes back to the top to show the result of a backup.
- [ ] Category merge asks "Merge into X?" once more before anything moves.

### The type on a To review card, and card bill payment texts (Slice 104; the owner's two tester reports; texts below are the SHAPES, with invented numbers: use your own real texts)
- [ ] A real bank text of a move between your own accounts (money out of one, in to another): on its To review card tap **Transfer**. For money out the card asks "To account", for money in "From account"; pick the account, **Add as transfer**. One transfer entry (not spending, not income) from the right account to the right one; open it: "Where this came from" shows the text. "Added as a transfer ... Undo" puts it back.
- [ ] Then the text the bank sends for the OTHER side of the same move: its card says "This looks like the other half of the transfer you added ..." with **Add to that transfer** (no second entry; the entry's "Where this came from" now shows both texts). If the second text arrives hours later, or you have not set the account's digits, it may show the ordinary card instead: write down which.
- [ ] An ATM cash withdrawal text with one Cash account starts as a Transfer to Cash; with two Cash accounts it starts as an expense and Transfer lets you pick which one.
- [ ] Make the same kind of text a transfer to the same account three times in a row (a sweep, a self-payment to your own UPI address): the next ones start as "a transfer ... Check it", and after the third consistent one "Add all" takes them (the dialog says how many are transfers). Record one as an expense instead and it is not taken in bulk again.
- [ ] **Owner's text 2 (invented numbers):** `Hi <NAME>, Axis Bank Credit Card bill payment of Rs. 12,345.67 was successful! T&C -Kotak811` from your Kotak header (note the exact header from the message: if it is not KOTAKB the text is skipped; add it under More, Capture, "This is my bank"). It must NOT appear on the Bills page as a new bill. In To review it is a **Bill payment** card asking "Mark the Axis card bill as paid?". With the Axis bill tracked (or found) the card lists it; **Mark paid** moves it to Paid on Bills and records the payment (a transfer from the Kotak account to your Axis card account if you have one, else an expense in Bills). **Undo** brings the bill back as it was. With no matching bill the card says so and offers "Record a card bill payment of ...".
- [ ] **Which sender sent the Kotak text? (the owner must look, nothing here can)** On the phone open the messages app, the conversation of that payment text, and read the NAME at the top exactly as shown (for example `AX-KOTAK811-S`, `VM-KOTAKB-S`, `KOTAKB`, or a plain phone number). The app reads `KOTAKB` only. If the name has another root (for example `KOTAK811`): More, Capture, "Senders we skipped" shows it with a count and "Treat KOTAK811 as my bank" (or type it under "Your bank's sender name"). Only texts that arrive AFTER that are read: the skipped text was not kept (by design, no word of a skipped text is ever stored), so the next payment text is the first to appear in To review. A phone number as the sender, or a name ending -P or -G (promotional), is not offered: write the name down for the next release. iPhone: the Shortcuts automation must pass the "Sender" with the text (otherwise the text is read by its words, where "Kotak811" now says Kotak); the sender root then goes under the same "Your bank's sender name".
- [ ] **Bills page card for old wrong bills (only if the Bills page still shows a "bill" that is really a payment confirmation from an older version):** the top of Bills shows a card "N bills below are really confirmations of payments ... made by an earlier version. Nothing is due. Move them to To review, where each can mark the bill it paid." with **Move to To review**. Nothing moves until you tap it. Tap: the bill leaves Bills and the text is in To review as a **Bill payment** card; **Undo** puts the bill back. A bill you added, edited, paid or that has a due date different from the text's date is never offered; write down any wrong bill that is not offered.
- [ ] Then Axis's own text of the same payment (`Payment of Rs 12,345.67 received towards your Axis Bank Credit Card ending 1234`): it must say "Already in your ledger" / "You already marked ... as paid" with **Same payment: add to it**, never a second payment or income.
- [ ] A real card statement text ("total amount due ... due date ...") still becomes a bill; a promo ("Pay your bill on the app and win cashback") and an OTP are still not captured.

### Fix this balance (Slice 105; the owner's credit card report; all amounts below are examples: use your own)
- [ ] **Your real credit card that shows "extra balance" after paying the bill.** More, Accounts: a line above the cards says "Looks like some earlier spending is missing." with the card's name, **Fix this balance** and **Dismiss**. Tap Fix this balance: it says "The app thinks you owe nothing and have ₹X extra in credit". Type what the card app says you owe now (usually 0) and read the "Difference" line: it must equal the figure you used to work out by hand. **Fix balance**: the card's tile shows the new balance at once (no old number first), the bar says "Balance fixed: ... → ..." with **Undo**.
- [ ] Open the card (tap it): "Balance now", the history line "Balance fixed: ... on <today>", and, if you set billing dates, the card summary (left to pay, limit used %) agrees with the card statement. Undo (before the bar goes) puts it exactly back; a second fix with the same amount after the Undo works again.
- [ ] A bank account whose balance in the bank app differs from the app: Fix this balance, type the bank app's balance, check the difference line says whether spending or money received is missing, fix it. Net worth and the Home balance strip show the corrected number. Reports, the month's spending and the budget did not move at all.
- [ ] The "Overdrawn" and "In credit (nothing owed)" choices; a ₹ sign and commas typed in the amount are accepted ("₹1,26,000"), a minus sign or a $ amount is refused in words; the same amount again says "Already matches".
- [ ] Hide amounts on: every amount in the dialog and the bar is hidden; what you type stays visible.
- [ ] A restore on a second phone (More, Your data): the fixed balances and the history lines are the same there, including the first start's Bank and Cash accounts.
- [ ] Two-pane / unfolded: the dialog stays a normal dialog width, never edge to edge.
- [ ] TalkBack: the dialog title is a heading; the difference and the effect are read after the amount; the chips say whether they are selected.

### Speed
- [ ] With a year or more of real entries, Home opens and scrolls without a pause, and narrowing to one account is instant.

### Permissions given in system settings show on return (Android)
- [ ] More, Capture: turn on "Read payment app notifications"; the phone opens Notification access; turn this app on; press Back. The switch is on and the red "Notification access is not given" line is gone **without leaving the screen**. Do the same from "Open notification access settings". On Samsung One UI the access page may ask you to confirm a warning first.
- [ ] Still on Capture: leave the app (Home), turn the access off in system settings (Settings, Notifications, Advanced, Notification access), open the app again from Recents. The switch is off and the red line is back.
- [ ] More, Capture: turn on "Read bank texts" (Play build: not present), allow the permission in Android's dialog: the switch stays on at once. Take the permission away in Settings, Apps, Expenses, Permissions, and come back: the red "Android has not allowed" line shows.
- [ ] More, Notifications (Android 13+): with notifications turned off for the app the red notice shows; press "Allow notifications" or turn them on in system settings and return: the notice is gone at once.
- [ ] More, Privacy: the Allowed / Not allowed words follow the same changes on return.
- [ ] iPhone: More, Notifications: with notifications off in Settings, the notice shows; go to Settings, Expenses, Notifications, turn them on, come back: the notice is gone (not verified on a phone).

### Why isn't capture working?, the guided setup and earlier spending (Slice 107; use your own bank, account and texts)
- [ ] More, Capture: the top button "Why isn't capture working?" opens one page. Its first line says "Everything looks fine" or "N things need attention", and every row says OK, Fix this or Note in words (not only a colour or an icon).
- [ ] With the text permission NOT given (Settings, Apps, Expenses, Permissions, Text messages: Don't allow): the row "Reading bank texts is not allowed" has "Allow reading texts" (the same disclosure and Android's question) and "Open app settings". Allow it in Settings, press Back: the row turns OK **without leaving the page**.
- [ ] Turn on "Read payment app notifications" in Capture and leave Notification access off: the row says so and "Give notification access" opens Android's list. Turn it on and come back: after a couple of seconds the row is OK. Switch access off and on again, or wait for Android to drop the link: "Reconnect" brings it back.
- [ ] Android 13 and later: with reminders not allowed, "Allow reminders" asks Android; after allowing, the row is OK.
- [ ] Battery row: change Settings, Apps, Expenses, Battery between Optimised, Unrestricted and Restricted: the row follows when you come back (Restricted is "Fix this"). **Samsung only**: the line "Samsung: Deep sleeping apps" shows the path Settings, Battery, Background usage limits, Deep sleeping apps; the app cannot see that list, so tick it yourself; the tick stays after closing the app.
- [ ] With an account named after your bank (for example "HDFC Savings") and a real text from that bank received since you turned capture on: "HDFC Bank: texts from HDFCBK or HDFCBN recognised". For a bank you have not had a text from yet: the note "No text from ... seen yet". If the bank texts you from another name: More, Capture, "Senders we skipped" should list it, and the checkup row "N sender names were skipped" offers "Treat NAME as my bank".
- [ ] An account of your bank with no last digits shows "needs its last digits"; add the 3 or 4 digits it prints in its texts, come back: OK.
- [ ] "Check a message": paste a real bank text (with its sender name if you know it, like AX-HDFCBK-S) and press Check. It says what the app would do, the bank, money out or in, the amount, the payee, which of your accounts it matches and the category it would suggest; only the last two digits of the account are shown. Nothing appears in To review or Bills, and the capture counts on this page do not change. Leave the page and come back: the text and the answer are gone. Try a text from a name that is on no list: it says it would be skipped and offers "Treat NAME as my bank".
- [ ] Select a text in your messages app, Share, Expenses (Android): the page opens with it already checked.
- [ ] More, Set up reading bank texts (or Home's card after the first tour): four steps, each skippable; Close at any step and Home says "Continue" at that step; "Not now" hides the card for a day and "Hide" for good; the setup stays in More.
- [ ] More, Bring in earlier spending: the three ways in order; "Choose a statement" opens the file picker and then the importer you know; the fast form starts on the last day of last month, keeps the date and category, and each added expense shows in the list on that day; "Open Accounts" opens your accounts.
- [ ] Largest text size (Settings, Display, Font size) and, on a tablet or an unfolded foldable, the same three pages: nothing is cut off, every button can be reached by scrolling.
- [ ] iPhone: the same page shows "No message has reached the app yet" until an automation delivers one, then the checklist of three Shortcuts lines you tick, and "Check a message" with Paste. (Not verified on a phone.)

### Travel mode (More, Travel mode; Slice 110; use a real trip or invented dates, and your own amounts)
Only an emulator was used. Make a trip whose days include today (More, Travel mode, New trip: name "Goa", first day yesterday, last day in three days, a daily budget).
- [ ] The tag box fills itself with "Trip: Goa" as you type the name; typing in it stops that. The days show "5 days, both the first and the last included".
- [ ] Add an expense: **Tagged for Goa trip** with a cross is under the date; Save; open the entry in the editor: the tag "Trip: Goa" is on it. Take the tag off in the editor, change the note, save: the tag does not come back.
- [ ] Add another and tap the cross on the chip first: it is saved without the tag; the chip said "Tag it for Goa trip" and brought it back when tapped again. Change the date to a day outside the trip: the chip goes.
- [ ] A quick entry on Home and the widget's chip inside the days are tagged; one dated outside the days is not (the iPhone widget replays its taps with their own time).
- [ ] A bank text (or notification) of today confirmed in To review (Add, and Add all) is tagged; one dated earlier than the trip is not. A refund of a tagged expense is tagged.
- [ ] Home: one line "Trip: Goa · day 2 of 5 · ₹… spent" right under the month card, one tap opens the page; with a To review nudge waiting the nudge still shows once with "Also waiting" and the trip line is above it. The line is gone after the last day, and when the trip is switched off.
- [ ] The trip's page: total, "Day 2 of 5", average a day, the budget "₹… under/over", a bar for each day (tap one: its amount; today marked), categories. **See all entries** opens Search with the tag and the days chosen; Back returns to the page.
- [ ] Make a trip for days that already have expenses: "Tag N earlier entries from these dates?" shows the right N; Not now tags nothing; Yes then Undo puts everything back exactly. Widening the days asks only about the new days.
- [ ] Two trips with the same days: the second is refused with a sentence that names the first. Delete a trip: the tags stay on the entries.
- [ ] Hide amounts: the Home line, the page and the bars show no amount. App lock: the page is behind the lock like every screen.
- [ ] TalkBack: the chip says "Tagged for Goa trip with the tag Trip: Goa. Double tap to take the tag off this entry"; each bar says its day and amount; the Home line reads as one button.
- [ ] Largest text size and (if you have one) a tablet or unfolded foldable: the Home line wraps, the form and the page scroll, nothing is cut off, buttons are easy to hit.
- [ ] A backup of a ledger with a trip, restored on another phone (or after Start fresh): the trip and the tags are back; the message says "1 trip was added."
- [ ] A phone that changes time zone (flight mode, a trip abroad): entries made after landing are dated in the new zone; say what you saw.

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

### Travel mode (Slice 110, same screens as Android; shared code)
- [ ] The checklist above, on the iPhone: the date picker, the chip in the add form, Home's line and the page; the Shortcuts "add expense" inside a trip's days is tagged.

### Larger text and iPad (if you have one)
- [ ] Dynamic Type at the largest size: Home and the Shortcuts screen stay usable.
- [ ] iPad: the two-pane layout above, now in both ways up (an iPad mini upright is 744 pt wide). The size comes from the same window API as the guide screen but has not been run on an iPad. iPhones, in both ways up, stay one pane.

## Same shop as… and amounts (Slice 108)
- [ ] More, Capture, Category rules: under "What the app has learned" the block "Names counted as one shop" is there. "Count two names as one shop" with Swiggy Instamart and Swiggy: a bar "Swiggy Instamart now counts as Swiggy" with Undo; Remove on its row brings "Swiggy Instamart is its own shop again" with Undo.
- [ ] To review, a card of a shop with a name that is another's: Change, then "Same shop as…", pick one of the other waiting names or type it. Search for one of the two names: the entries of both show. Reports, Merchant: one row for the two names, tapping it lists both names' entries and they add up to the figure.
- [ ] A shop you really pay for different things (a marketplace, a wallet): after you have filed a few entries of about ₹199 as one category and of about ₹2,500 as another, a new text of about ₹199 says "You chose … for … amounts like ₹199 (N times)" under the category and starts as a check; it is only added in bulk when 3 or more agree and none is different. Tell us when it is wrong.
- [ ] Back up and restore on a second phone: the joins ("Names counted as one shop") come back with the backup.

## What to send back
For anything that fails: the screen, what you tapped, what you expected, and the version from More. The Send feedback button writes that down for you.

## Home, Option B (Slice 112)
- [ ] Home with your real data: the month card is short, the figure is in whole rupees, the chip ("▲ +12%") and the sentence agree with Reports; Budget left and Safe to spend sit side by side and each a-day figure says where it comes from.
- [ ] Tap the month figure with TalkBack or VoiceOver on: it reads the exact amount with paise, not the rounded one. Same for Budget left, Safe to spend and an account tile.
- [ ] One nudge at a time, always the same look, one cross to close it. Close "Set up reading bank texts": a bar offers "Hide for good". Close "Getting started": it is gone for good.
- [ ] Add is in the middle of the bottom bar on Home, Search, Reports and More, never over the list; Ctrl+N (keyboard) still works.
- [ ] Each day heading shows what you spent that day (income and transfers are not in it); the last day on the screen has no total until you scroll and more loads.
- [ ] Hide amounts: no budget bar, no trip bar, no graph, no chip; every figure shows dots.
- [ ] Text size 150% and 200% (and the largest the phone offers): no card runs off the edge, the account row opens, entry titles show whole, the nudge button is under the words.
- [ ] A credit card tile says "owed" and when it is due; with a limit entered a thin bar shows how much is used.
- [ ] Fold / tablet: the list pane shows the first entries without scrolling; "Select an entry" on the right.
- [ ] iPhone: the same page; the bottom bar has Add in the middle; the shortcut and share-sheet paths still open Add.

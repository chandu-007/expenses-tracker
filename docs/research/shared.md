# Shared / Group / Household Expense Apps — Market Research (as of 2026-10-05)

Scope: Splitwise, Tricount (bunq), Settle Up, Splid, Spliit (OSS), Kittysplit, Honeydue, Zeta, Goodbudget household sharing, Monarch & YNAB household sharing, India-first splitters (Splitkaro, UPI-native newcomers, GPay/PhonePe split), receipt-itemizer newcomers (splitty, Tab, etc.), and how general trackers handle (or ignore) shared spends.

Method notes: Reddit pages are blocked for direct reads; Reddit evidence below comes from search snippets/titles and third-party roundups and is labeled. Many "Splitwise alternative" blogs are written by competitors — their factual claims are used only when corroborated by primary sources (official help pages, store listings, press). Ratings are as displayed on the store page on 2026-10-05 unless stated.

---

## Apps covered

### Summary table

| App | Platforms | Pricing (2026) | Rating (store, count) | Data entry model | Standout features |
|---|---|---|---|---|---|
| **Splitwise** | iOS, Android, Web | Free (daily expense cap ~3–5/day, unskippable ads, 10-s wait); Pro $4.99/mo or ~$40/yr **per user**; India launched at ₹149/mo or ₹999/yr (Dec 2023) | Play (IN) 4.0★, ~195K reviews, 10M+ installs; App Store US 4.0★, 29K | Manual; Pro: receipt OCR + itemization; US-only card transaction import (Pro) | Debt simplification, network effect, multi-payer, recurring bills, edit history, comments, Venmo/PayPal (US), Paytm (India) settle integrations |
| **Tricount (bunq)** | iOS, Android (web discontinued) | Free, "no ads, no limits"; Premium discontinued; monetized via bunq card/bank upsell | App Store US 4.9★, 7.3K; Play 4.8★, ~195K, 10M+ | Manual; bunq account payments can be imported | Free unlimited, offline, multi-currency, payment requests, works w/ non-app users (2026 update) |
| **Settle Up** (Step Up Labs) | iOS, Android, Web | Free w/ ads; Premium (personal) or **Group Premium** (one-time per group); India ad-free one-time ₹3,999 (2023) | Play 4.7★, ~50K, 1M+; App Store US 4.8★, 1.8K | Manual; itemized "items from a long bill" | Offline, weights/default shares, multiple payers, incomes, link/QR join, read-only access, CSV export free, widgets |
| **Splid** | iOS (iPhone), Android | Free; one-time IAP for Excel export & extras | Play 4.9★, ~87K, 1M+; App Store US 4.9★, 4.1K | Manual | No sign-up, offline-first groups, 150+ currencies w/ auto-convert, PDF summaries, indie dev |
| **Spliit** (OSS) | Web/PWA, official iOS (SwiftUI); no official Android (community "Spliit2Go" offline client, Sep 2026) | Free, no ads, donation-funded; self-hostable (MIT) | GitHub ~3.0K stars, 237 open issues | Manual; AI receipt scan (beta) | No account, open source, recurring expenses (beta), categories, search, receipts, 24+ languages |
| **Kittysplit** | Web (any browser), iOS, Android | Free for basic event; "Super Kitty" ~€3 one-time **per group** | Play 4.9★, 548 reviews, 10K+ | Manual | No-app/no-account link sharing, change history, fair group-level one-time pricing |
| **Honeydue** | iOS, Android (US-centric) | Free (optional tips; IAP $0.99–$9.99) | App Store US 4.5★, ~10K | Bank-sync (Plaid/Finicity) + manual | Couples: choose what to share, bill reminders, per-transaction chat/emoji, split expenses |
| **Zeta** | (Shut down) | Joint accounts closed by 2025-05-09; asset-acquired by Acorns 2025-06-24 | n/a | Was joint bank account + tracker | Yours/mine/ours buckets, bill reserve; now defunct |
| **Goodbudget** | iOS, Android, Web | Free: 20 envelopes, 1 account, 2 devices, 1 yr history; Premium $10/mo or $80/yr (bank sync added Aug 2024) | App Store US 4.6★, 13K | Manual (bank sync only Premium, US) | Real-time shared envelopes between partners' devices |
| **Monarch** | iOS, Android, Web | No free tier; Core $14.99/mo or $99.99/yr; Plus $199/yr (2026) | App Store ~113K ratings (per AppImp) | Bank-sync | Household (partner at no extra cost); "Shared Views" yours/mine/ours labels (Oct 2025) |
| **YNAB / YNAB Together** | iOS, Android, Web | $14.99/mo or $109/yr; up to 6 people on one subscription | — | Bank-sync (US/CA/UK/EU) + manual | Shared budgets; private budgets per member; no native splitting/IOUs |
| **Splitkaro** (India) | iOS, Android, Web | Free unlimited expenses; Premium (~₹37.5/mo billed annually per third-party) for search, analytics, custom categories, PDF export; 3 free scans/mo | Play 4.4★, ~8.9K, 100K+ | Manual + imports from Zomato/Swiggy/Zepto/Blinkit, bank/card statements; UPI app payments | Personal + group in one app, "your share" accounting, Splitwise import, item-wise splitting |
| **GPay / PhonePe split** (India) | Android/iOS | Free | — | Inside UPI payment app | One-shot split + UPI request; GPay tracks "owed by/to you" in group chats |
| **Venmo Groups** (US) | iOS/Android | Free | — | Inside P2P app | Up to 30 members, running balances, settle in-app (launched Nov 2023) |
| **splitty / Tab / receipt-itemizers** | iOS (mostly), US-centric | Free/freemium | splitty App Store US 4.6★ (83) | Photo OCR → assign items | Per-item, tax/tip allocation, Venmo/Cash App/PayPal request links |
| **BudgetBakers ShareCost** (new, Mar 2026) | iOS, Android | Free (5 groups); Premium for unlimited groups, no ads, export | new | Manual + bank import via Wallet app | First mainstream personal-tracker vendor to ship a companion splitter |

### Profiles

**Splitwise** — The category default since 2011 (Providence, RI). Groups, friendships, equal/unequal/%/shares splits, multiple payers, recurring bills, debt simplification, comments, edit history, 100+ currencies, offline entry ([Play listing](https://play.google.com/store/apps/details?id=com.Splitwise.SplitwiseMobile&hl=en_IN)). In Dec 2023 it introduced a **daily expense limit** on the free tier ([official KB](https://feedback.splitwise.com/knowledgebase/articles/2010350-why-am-i-seeing-an-expense-limit)) plus a 10-second wait/ads; News18 measured a 4-expense cap and the 10-s cooldown on both platforms and reported Pro at ₹149/mo or ₹999/yr in India, $5/mo or $40/yr in the US ([News18, 2023-12-28](https://www.news18.com/tech/splitwise-now-limits-number-of-expenses-you-can-add-for-free-but-here-are-4-apps-that-dont-8720041.html)). Pro now bundles unlimited expenses, no ads, receipt scanning, itemization, currency conversion, charts, search, default splits and US-only card transaction import ([splitwise.com/pro](https://www.splitwise.com/pro)). Pro is per-user, so a household pays twice ([HippoSplit review, 2026-08/09](https://hipposplit.com/blog/splitwise-review/)). Current India price is inconsistently reported by third parties (₹999/yr vs. ₹2,499/yr vs. ~₹399/mo) — **[unverified]**; check in-app.

**Tricount (bunq)** — Belgian app (founded 2010) acquired by Dutch neobank bunq in May 2022, bringing 5.4M users ([TechCrunch 2022-05-03](https://techcrunch.com/2022/05/03/bunq-to-acquire-group-expenses-app-tricount/); [bunq 2021 annual report](https://static.bunq.com/website/documents/bunq-report-annual-2021-en.pdf)). Now claims 17–21M users and positions itself as "free & unlimited – no subscription… easier than Splitwise" ([App Store US](https://apps.apple.com/us/app/tricount-split-settle-bills/id349866256)). bunq **discontinued Premium**, removing CSV/PDF export, "Personal mode", statistics and saved custom splits (some later partially restored), compensating payers with 6 months of bunq Pro ([Tricount Help](https://help.tricount.com/articles/what-happened-with-tricount-premium)). Web version discontinued (dev response, Jan 2024, [Play](https://play.google.com/store/apps/details?id=com.tribab.tricount.android&hl=en_US)). Tricount tallied €16.4B shared in 2024 ([bunq press, 2025-01-30](https://press.bunq.com/246589-from-roommates-to-road-trips-tricount-tallies-16-4-billion-shared-in-2024/)). Funded by in-app bunq card offers in a dedicated tab ([BananaSplit guide](https://www.bananasplitapp.com/blog/what-is-tricount-how-does-it-work/)); Play still flags "Contains ads".

**Settle Up** — Czech-origin (Step Up Labs). Android/iOS/web, offline, all currencies with real-time rates, weights per member, multiple payers, incomes, "items from a long bill", link/QR sharing, read-only access, notifications, transfer minimization; CSV export free. Premium adds no ads, receipt photos, categories, **recurring transactions**, Excel export ([Play](https://play.google.com/store/apps/details?id=cz.destil.settleup&hl=en_IN)). Unique **Group Premium** (one-time, applies to everyone in a group). Third-party listing of Group Premium prices ($5.99/week … $149.99 lifetime) is **[unverified]**.

**Splid** — Solo indie dev (Munich). No sign-up, offline groups or optional sync, 150+ currencies auto-converted, minimal payments, PDF summaries free, Excel via IAP ([Play](https://play.google.com/store/apps/details?id=splid.teamturtle.com.splid&hl=en_IN); [splid.app](https://splid.app/)). Highly rated; Play last updated Aug 2025 (slow cadence).

**Spliit** — MIT-licensed Next.js app by Sébastien Castiel; "No ads. No account. Open Source. Forever Free." Groups, uneven splits, categories, search, receipt images, AI scan (beta), recurring (beta), reimbursements; donation-funded on Open Collective ([spliit.app](https://spliit.app/); [GitHub](https://github.com/spliit-app/spliit)). Official iOS app (SwiftUI, iCloud). No official Android; a community offline-capable client "Spliit2Go" was announced 2026-09-27 ([issue #658](https://github.com/spliit-app/spliit/issues/658)).

**Kittysplit** — Berlin, since 2012. Browser-first: share a link, anyone participates without app/account; optional accounts since v2.0. Free basic events; "Super Kitty" (~€3 once per group) adds receipt photos, foreign currency conversion, default shares, read-only access, category insights ([Play](https://play.google.com/store/apps/details?id=com.kittysplit&hl=en_IN); [kittysplit.com](https://www.kittysplit.com/en/splid-alternative)).

**Honeydue** — US couples app (WalletIQ). Link bank accounts, "choose what you want to share", bills, budgets, splits, activity, chat ([honeydue.com](https://www.honeydue.com/)). Free; US-only on App Store per [AppImp](https://appimp.app/apps/honeydue-couples-finance), where "Support" appears in 35.7% of reviews vs 12.2% Finance norm.

**Zeta** — Couples joint-banking + budgeting app; told users to close accounts by 2025-05-09 ([College Investor](https://thecollegeinvestor.com/24184/zeta-review/)); asset-acquired by Acorns 2025-06-24 ([PR Newswire](https://www.prnewswire.com/news-releases/acorns-accelerates-family-product-strategy-with-acquisition-of-couples-app-zeta-302488755.html)). Lesson: couples-finance startups are fragile; users fear data/account loss.

**Goodbudget** — Envelope budgeting with household sync. Free: 10+10 envelopes, 1 account, 2 devices, 1 yr history; Premium $10/mo or $80/yr and bank sync introduced Aug 2024 ([Goodbudget blog](https://goodbudget.com/blog/2024/08/changes-to-goodbudgets-plan-offerings/); [billing help](https://goodbudget.com/blog/help_category/billing)). No IOU/split concept — shares one budget, not debts.

**Monarch** — Bank-sync aggregator; partner included at no extra cost. Oct 2025 "Shared Views": label accounts/transactions mine/theirs/ours and filter perspectives, but "we don't currently offer a way to budget separately" ([Monarch blog](https://www.monarch.com/blog/shared-views)). Help center: all accounts are visible to household members ([Shared Views help](https://help.monarch.com/hc/en-us/articles/42228648365076-Shared-Views-in-Monarch)). Pricing: Core $99.99/yr, Plus $199/yr in 2026 ([Mezzi](https://www.mezzi.com/blog/monarch-raised-prices-budgeting-app-right-tool-investor)).

**YNAB Together** — Up to six people on one $109/yr subscription ([YNAB Together terms](https://www.ynab.com/terms/ynab-together); [NerdWallet](https://www.nerdwallet.com/finance/learn/ynab-app-review)). Per-budget sharing/privacy. No IOU tracking: users bolt on Splitwise with "phantom" accounts or third-party sync tools ([splitwiseforynab.com guide](https://www.splitwiseforynab.com/blog/how-to-use-splitwise-with-ynab-definitive-guide)).

**Splitkaro (India)** — Positions as "Free, and free means free… unlimited expenses"; personal + group tracking in one app ("The bill was $120 but your share was $24. Splitkaro counts the $24"), Splitwise balance import, item-wise splits, imports from Zepto/Zomato/Swiggy/Blinkit, bank/card statement import ([Play](https://play.google.com/store/apps/details?id=com.bsquare.splitkaro&hl=en_IN)). Premium gates search, custom categories, analytics, PDF export, dark mode; 3 free scans/month (dev reply, Sep 2026).

**GPay / PhonePe** — GPay India supports "Split an expense" in group chats and tracks Owed by you/Owed to you/Settled ([Google help](https://support.google.com/pay/india/answer/12025420?hl=en)); PhonePe has Split bill with status tracking ([NewsBytes](https://www.newsbytesapp.com/news/science/how-to-split-bills-with-friends-using-phonepe-on-android/story)). Neither does debt simplification across many expenses, categories, or personal-share accounting.

**Venmo Groups** — US, launched 2023-11-14, up to 30 members, running balances, settle any time ([PayPal Newsroom](https://newsroom.paypal-corp.com/2023-11-14-Introducing-Venmo-Groups)). A Splitwise reviewer: "Venmo groups ate Splitwise's lunch when it comes to the basic concept of entering an expense." ([App Store US](https://apps.apple.com/us/app/splitwise/id458023433?see-all=reviews)).

**Receipt-itemizer newcomers (splitty, Tab, Billy, BillSplit AI, etc.)** — Snap receipt → assign items → tax/tip proportionally → Venmo/Cash App/PayPal request ([splitty App Store](https://apps.apple.com/us/app/splitty-split-bills-tip/id1499263885)). One-off restaurant tools; no persistent group ledger. Splitwise's own "Plates" itemizer is frozen since 2013 ([splitty vs Plates](https://splittyapp.com/vs/plates/), competitor source).

**BudgetBakers ShareCost** — Wallet (personal tracker) maker launched a separate splitter in Mar 2026 with bank-transaction import via Wallet ([BudgetBakers blog](https://budgetbakers.com/en/blog/2026-03-introducing-sharecost-smarter-way-split-expenses/)). Validates the personal↔group bridge — but as **two apps**, not one.

---

## Drawbacks by app

### Splitwise
- **Daily expense cap on free tier — very common, the #1 complaint (2023→2026).**
  - "Limiting the app to 3 transactions a day is an absolute joke and a slap in the face to all your exisiting customers." ([App Store NZ](https://apps.apple.com/nz/app/splitwise/id458023433?platform=iphone&see-all=reviews))
  - "after I added 4 (just 4!) expenses I was met with a pop-up saying that I had reached the maximum" ([App Store US](https://apps.apple.com/us/app/splitwise/id458023433?see-all=reviews))
  - "you are out of luck if your group needs to split more than 5 transactions on a vacation" (Play, 2026-08-31, [Play](https://play.google.com/store/apps/details?id=com.Splitwise.SplitwiseMobile&hl=en_IN))
  - Batch-entry users hit it hardest: "most people input all their transactions at the end of the month or at the end of a trip" ([App Store US](https://apps.apple.com/us/app/splitwise/id458023433?see-all=reviews))
  - Reddit thread "The downfall of Splitwise" (r/personalfinance): "limit of 3 transactions per day and a requirement to watch a 10 second video ad" ([search snippet](https://www.reddit.com/r/personalfinance/comments/186v5wj/the_downfall_of_splitwise/))
  - Official workaround is to "ask a friend to add the expenses on your behalf" ([KB](https://feedback.splitwise.com/knowledgebase/articles/2010350-why-am-i-seeing-an-expense-limit)).
- **Unskippable ads / 10-second waits — very common.** "The 10 second waits and the 3 payment daily limit means that I literally cannot use this app as intended." ([App Store NZ](https://apps.apple.com/nz/app/splitwise/id458023433?platform=iphone&see-all=reviews))
- **Previously-free features moved behind paywall; no notice — common.** "updates - that they don't notify you of - where basic functionalities are now a premium feature… receipts - this is now a premium feature" ([App Store NZ](https://apps.apple.com/nz/app/splitwise/id458023433?platform=iphone&see-all=reviews)); "they paywalled nearly everything useful" ([App Store US](https://apps.apple.com/us/app/splitwise/id458023433?see-all=reviews)).
- **Currency conversion is Pro-only; conversion isn't automatic for later expenses — recurring (travelers).** "I need to get PRO to convert requests before paying them." ([App Store US](https://apps.apple.com/us/app/splitwise/id458023433?see-all=reviews)); KB: future expenses "will not be automatically converted – you'll need to come back and click the 'Convert' button again" ([KB](https://feedback.splitwise.com/knowledgebase/articles/301146-can-splitwise-do-currency-conversion-between-multi)).
- **Pro priced per user — recurring.** "Pro is per user… Two partners who both want Pro pay twice." ([HippoSplit](https://hipposplit.com/blog/splitwise-review/))
- **Forced sign-up / can't just be the bookkeeper for non-users — recurring.** "I can't add bills if somebody who owes me doesn't want to sign up." ([App Store NZ](https://apps.apple.com/nz/app/splitwise/id458023433?platform=iphone&see-all=reviews)); a 14-person family trip organizer: "not interested in joining Splitwise just to do math" ([App Store US](https://apps.apple.com/us/app/splitwise/id458023433?see-all=reviews)).
- **Entry UX: loses draft on app switch, sum-mismatch blocks, confusing "shares" — occasional but vivid.** "anytime I switch over to my calculator app… it closes out of an expense I'm working on… no way to save a draft" ([App Store US](https://apps.apple.com/us/app/splitwise/id458023433?see-all=reviews)).
- **Itemization limits — occasional.** Can't itemize "from an existing photo"; itemized items only 50/50 or 100/0, wants custom % ([App Store US](https://apps.apple.com/us/app/splitwise/id458023433?see-all=reviews)).
- **Payment integrations flaky/limited — recurring.** Venmo integration "disabled due to a bug on Venmo's side" (dev reply); Splitwise Pay holds funds "pending… like 5 days"; "no way to connect payment options like Zelle despite being one of the most requested features for almost 10 years" ([App Store US](https://apps.apple.com/us/app/splitwise/id458023433?see-all=reviews)). In-app payments only Venmo/PayPal (US) and Paytm (India) — **no UPI** ([Play listing](https://play.google.com/store/apps/details?id=com.Splitwise.SplitwiseMobile&hl=en_IN)). Dev to NZ user: "we don't yet offer any in your region."
- **Promotional push notifications — occasional, trust-damaging.** "vague push notification telling me I was 'almost' done… it turned out to be an advertisement" (Play, 2026-08-18).
- **Bank link wanted outside US — recurring wish.** "would be nice if the app could be connected to my cheque account… without having to create manual entries" (App Store NZ).
- **No personal expense tracking — long-standing request.** "Track Personal Expenses" idea: **835 votes**, "under review" since 2014; official advice: make a group with only yourself ([UserVoice](https://feedback.splitwise.com/forums/162446-general/suggestions/4084332-track-personal-expenses)). User: "I have to manage shared home budget in Splitwise and personal finances in a different app."
- India: X/Twitter backlash Dec 2023 — "5$ every month to do division addition subtraction and multiplication, nah" ([News18](https://www.news18.com/tech/splitwise-now-limits-number-of-expenses-you-can-add-for-free-but-here-are-4-apps-that-dont-8720041.html)); DesiDime thread: "asking to buy premium to do many things like add 3+ expense in a day, add photo in a txn" ([DesiDime](https://www.desidime.com/discussions/splitwise-alternatives)).
- Trustpilot reportedly 1.8/5 with 65% one-star (Mar 2026) — via competitor blog ([split-circle](https://split-circle.com/en/blog/splitwise-daily-limit)); **[unverified]** (Trustpilot returned 403).

### Tricount (bunq)
- **Sync/data loss after bunq-era updates — common in 2024–2026.** "When I hit refresh, my entire expenses tracking simply vanished." (Play, 2025-06-16); "the sync of the expenses just doesn't work properly… make expenses disappear and re-appear" (Play, 2026-01-03) ([Play](https://play.google.com/store/apps/details?id=com.tribab.tricount.android&hl=en_US)); forced update mid-trip "completely erased all of the data from a weeklong trip" (App Store US, 2024-10-12; dev admitted data "wasn't uploaded to our servers") ([App Store US](https://apps.apple.com/us/app/tricount-split-settle-bills/id349866256)).
- **Removed paid features (export, stats, personal mode, custom splits) — common among long-time users.** "Without the ability to export data, the app is useless to me." "they remove the .csv export… That makes the app completely useless" ([App Store CA](https://apps.apple.com/ca/app/tricount-split-settle-bills/id349866256?platform=iphone&see-all=reviews)); paid users "never got my money back."
- **Custom ratio splits regressed — recurring.** "we now have to press all the way of for odd distribution like 53 and 47… it does not keep anymore the ratio" (App Store CA).
- **No overview/export to audit splits — recurring.** "not having an export or big picture view of the expenses is becoming a dealbreaker… every traveler have to open every expense" (App Store US, 2025-08-21).
- **Web version killed — occasional.** "no longer being able to use it on the web takes away a lot of the interest" (Play, 2023-12-26).
- **Can't change default currency after creation — occasional** (App Store CA, dev acknowledged).
- **Support quality — occasional but severe**: "only ever contacted by a trainee" over two weeks (App Store US).
- **Bank upsell** — bunq card shown in its own tab ([BananaSplit](https://www.bananasplitapp.com/blog/what-is-tricount-how-does-it-work/)); Play label "Contains ads" despite "no ads" claim.

### Settle Up
- **Ads invasive / UI dated / pricey — recurring.** "The add are also way to invasive… it's way too expensive for what it's offering"; "it take multiple clicks and it's also not intuitive how to 'settle up'" ([App Store US](https://apps.apple.com/us/app/settle-up-group-expenses/id737534985?see-all=reviews)); "the app looks a little dated" (same).
- **Recurring transactions, categories, receipt photos are Premium — common friction for roommates** ([Play listing](https://play.google.com/store/apps/details?id=cz.destil.settleup&hl=en_IN)).
- **Balance view confusion (settled vs unsettled history) — occasional.** "giving me only a net summary of ALL debts, including those already settled… no way to tell what we actually have paid" (App Store US); wants "a unique icon for transactions that were settled" (Play, 2025-12-27).
- **Missing split nuances — occasional.** Fixed-amount-then-share-rest, per-person adjustments, family "paying groups" (App Store US).
- **Account deletion friction — occasional (older).** "Vendor refused to cancel my premium account… wait 30 days" (Play, 2022).
- **No UPI** ([FairShare blog](https://fairshareapp.co.in/blog/top-5-splitwise-alternatives-india.html), competitor source).

### Splid
- **Ordering/time issues — occasional.** "You can edit the date but not the time… they won't be in the correct order" ([App Store UK](https://apps.apple.com/gb/app/splid-split-group-bills/id991473495?see-all=reviews)).
- **No receipts photos, no recurring, no identity-aware view — recurring wishes.** "Ability to store photo receipts"; "The ability to identify as one of the people… just show the bills that affect me"; "An option for recurring expenses"; notifications when "a transaction has been deleted" (App Store UK).
- **No payment integration — occasional.** "some payment integration with UK banks… would close the loop" (App Store UK).
- **Dual-currency recording for card spends — occasional.** Wants "2 fields. One in the local currency… the other settlement value from the credit card" (App Store UK); also US user asks to show local + home currency simultaneously ([App Store US](https://apps.apple.com/us/app/splid-split-group-bills/id991473495?platform=iphone&see-all=reviews)).
- **Free-tier limit encountered — rare.** "we weren't allowed to enter more expenses after a certain point?" (App Store US, 2026) — **[unverified]** whether a cap exists.
- Splid Play data-safety says data "may share… with third parties" ([Play](https://play.google.com/store/apps/details?id=splid.teamturtle.com.splid&hl=en_IN)).

### Spliit
- **No official Android app; web not offline — recurring among OSS users.** "Spliit has no Android app yet (#511)"; built Spliit2Go because "I travel a lot in places with little or no connectivity" ([issue #658](https://github.com/spliit-app/spliit/issues/658)); "There should be one Android app if exists for IOS" ([issue #511](https://github.com/spliit-app/spliit/issues/511)).
- **Exchange-rate breakage — occasional.** "Exchange rates broken: api.frankfurter.app redirects with missing CORS headers" ([issues](https://github.com/spliit-app/spliit/issues), Apr 2026).
- **Settlement suggestions unstable — occasional.** "Suggested reimbursements are not stable. They should not change after a repayment." ([#178](https://github.com/spliit-app/spliit/issues/178)).
- **Sustainability depends on donations; receipt-scan costs paid by donors** ([spliit.app](https://spliit.app/)). No notifications/accounts (link-based).

### Kittysplit
- **No recurring bills — recurring wish (couples/households).** "If I could make one feature request it would be for recurring payments… rent and bills" (Play, 2026-06-20; dev says planned) ([Play](https://play.google.com/store/apps/details?id=com.kittysplit&hl=en_IN)).
- **Link-as-account fragility, no running balance across events, no reminders** — per competitor analysis ([famzam](https://famzam.app/kittysplit-alternative)) — **[competitor claim]**: "A split that lives entirely behind a link is one deleted chat message away from being unrecoverable."
- Multi-currency and receipt photos only in paid Super Kitty (Play listing). Small user base (10K+ installs).

### Honeydue
- **Bank sync failures — very common.** "would be great if the connection to the bank didn't fail every few days"; "it lost touch with our primary bank… almost two full weeks" ([App Store US](https://apps.apple.com/us/app/honeydue-couples-finance/id1157633945?platform=iphone&see-all=reviews)).
- **Support gone dark / neglect — common.** "support team seems to have gone completely dark"; "Pending transactions stay pending forever… 'failed to load' on app start"; fear it will shut down like Honeyfi (App Store US). AppImp: support mentioned in 35.7% of reviews ([AppImp](https://appimp.app/apps/honeydue-couples-finance)).
- **Can't see "my true total" (personal + my share of joint) — explicit and important.** "can't figure out how to see what my total expenses have been for the month (aka my individual expenses + our split joint expenses)" (App Store US).
- **Category rigidity, transaction ordering, no pending — occasional** (App Store US).
- US-only; not sold in India or 39 other storefronts ([AppImp](https://appimp.app/apps/honeydue-couples-finance)).

### Zeta
- **Shut down** — 30-day notice to move funds by 2025-05-09 ([College Investor](https://thecollegeinvestor.com/24184/zeta-review/)). Pre-shutdown reviewers called it "still a work in progress… lack of high-end budgeting features" (same).

### Goodbudget (household sharing)
- **Free limits (1 account, 2 devices, 20 envelopes) — recurring.** "The one-account limit is where most people hit a wall" ([FinCompareLab](https://www.fincomparelab.com/reviews/goodbudget-review/)).
- **Both partners must enter everything manually — recurring.** "As long as you, and your spouse, enter EVERY transaction, it's a dream" ([App Store US](https://apps.apple.com/us/app/goodbudget-budget-planner/id471112395?see-all=reviews)).
- **Missing receipt photos, in-app search/reconcile, dated UI — recurring.** "would love… take a picture of the receipt"; "there needs to be a SEARCH feature in the app"; "I feel like I've gone back in time like 15 years in terms of the UI" (App Store US).
- **No IOU/split model** — shares one pot, can't express "you owe me".

### Monarch (household)
- **No account-level privacy — notable for couples with separate finances.** "You cannot hide bank accounts or transactions in Monarch. Once you invite your partner… they will be able to view all" ([Help](https://help.monarch.com/hc/en-us/articles/20926382202004-Monarch-for-Couples-and-Households)); "all accounts are visible to all household members… We're exploring adding this" ([Shared Views help](https://help.monarch.com/hc/en-us/articles/42228648365076-Shared-Views-in-Monarch)).
- **No separate budgets for mine/ours** ("we don't currently offer a way to budget separately") ([blog](https://www.monarch.com/blog/shared-views)).
- **No native IOU/Splitwise** → community Chrome extensions/scripts (Monarch-Splitwise, MonarchWise) ([GitHub](https://github.com/tevanburen/Monarch-Splitwise); [MonarchWise](https://github.com/alex-shen1/MonarchWise)).
- Price $99.99–$199/yr, no free tier; "top complaint in 2026 reviews is sync dropouts" ([Spentio, competitor](https://spentioapp.com/blog/best-monarch-money-alternatives-2026)) **[competitor claim]**.

### YNAB / YNAB Together
- **Privacy asymmetry — occasional.** "as 'group manager' I can mark my budgets as private, but I can view the other members budgets. I think this is uncomfortable" ([r/ynab snippet](https://www.reddit.com/r/ynab/comments/z8dwbj/ynab_together_privacy)).
- **Separate-but-shared requires multiple budgets & accounts** — "if you want your budget to stay private, and his budget to also stay private… you'll both need… your own individual accounts" ([r/ynab snippet](https://www.reddit.com/r/ynab/comments/146tb3v/shared_subscription_or_separate)).
- **Shared expenses inflate categories; manual phantom-account workaround** — "When you pay $150 for groceries and split it 50/50 in Splitwise, YNAB still shows $150"; downsides: "Double entry for every single shared expense… Miss one transaction and your accounts won't reconcile" ([splitwiseforynab](https://www.splitwiseforynab.com/blog/how-to-use-splitwise-with-ynab-definitive-guide)).
- Price creep: $50 → $84 → $99 → $109/yr ([FinCompareLab](https://www.fincomparelab.com/guides/ynab-pricing/)). No multi-currency budgets for expats ([borderlessbudget](https://borderlessbudget.com/blog/ynab-for-expats-review)).

### Splitkaro (India)
- **Crashes / lost data / UI clipping — occasional.** "the app crashes so when you reload it everything is lost… submit button is unreachable since it's below my button bar" (Play, 2026-09-08) ([Play](https://play.google.com/store/apps/details?id=com.bsquare.splitkaro&hl=en_IN)).
- **Paywalled basics — occasional.** "Cannot see even th[e group total]…" (AppBrain-cited review, Dec 2025, [AppBrain](https://www.appbrain.com/app/splitkaro-best-expense-app/com.bsquare.splitkaro)); scans capped at 3/month free.
- **Permission/security unease with food-app imports — occasional.** "what's stopping them from ordering something using my id and taking the otp from SMS" (Play review, via search snippet).
- Mobile-number-only login complaint (via search summary) **[unverified quote]**.

### GPay / PhonePe split
- One-shot splits; "no concept of a persistent group, no debt simplification" ([Niptao, competitor](https://niptao.app/en/blog/google-pay-split-bill-alternative)); no categories, no personal-share accounting, no trip currency.

### Venmo Groups
- US-only; ties you to Venmo; no itemization/receipt OCR or budgeting. **[INFERENCE from feature list]** ([PayPal Newsroom](https://newsroom.paypal-corp.com/2023-11-14-Introducing-Venmo-Groups)).

### Receipt itemizers (splitty, Tab)
- **OCR errors on tax/tip/subtotal lines — recurring.** Tab "included the 'subtotal', 'tax', and 'total' amounts as line items… added a 58 dollar tip"; can't assign 2 of a grouped item to one person ([Tab reviews](https://apps.apple.com/us/app/tab-the-simple-bill-splitter/id595068606?see-all=reviews)).
- **Organizer does all the work; no self-claim link — occasional.** splitty "requires the person organizing the group to do all the work… unlike alternatives which allow the leader to send a link" ([splitty reviews](https://apps.apple.com/us/app/splitty-split-bills-tip/id1499263885?see-all=reviews)).
- **Forced accounts to keep history — occasional.** Tab: "Bills are now DELETED once you leave the app, unless you sign up for a totally unnecessary account" (Tab reviews).
- **Share-out text missing tax/tip; payment rails limited (Venmo-only, not Zelle/PayPal)** — splitty reviews.
- Not connected to any persistent ledger or personal tracker.

### General personal trackers (Spendee, Wallet, etc.)
- **Spendee shared wallets: sync problems and slowness** — "adding one expense takes 30s" ([App Store](https://apps.apple.com/us/app/expense-budget-app-spendee/id635861140)); "would appreciate the filter for who spent how much money - when sharing the wallet with family" ([Capterra](https://www.capterra.com/p/238829/Spendee/)).
- **Wallet (BudgetBakers): no split/IOU**; Group Sharing is Premium (owner pays) and only one sharing group — user asks "allow more than one group sharing to be created (for friends, for families, for partners)" ([Play](https://play.google.com/store/apps/details?id=com.droid4you.application.wallet&hl=en_US)); splitting pushed to separate ShareCost app ([blog](https://budgetbakers.com/en/blog/2026-03-introducing-sharecost-smarter-way-split-expenses/)).
- **Walnut (India) once combined SMS auto-tracking + bill split (2016)** ([Walnut blog](https://www.getwalnut.com/blog/walnut-launches-bill-split-p2p-money-transfers-visa-icici-bank)); it pivoted to lending as axio (acquired by Amazon Sep 2025, [Tracxn](https://tracxn.com/d/companies/axio/__7ZJXjN0_0G6wspYYJaKGGh60BYXXdYtIzafg8MMFSqI)) — leaving a gap for SMS-tracking + splitting in India.

---

## Integrating personal tracking with group splits (the double-entry problem)

**The problem.** Splitters track *debts*; personal trackers track *spend*. When you pay ₹4,000 for a dinner of four, your bank/SMS tracker records ₹4,000 "Food", but your true consumption is ₹1,000 and you hold a ₹3,000 receivable. When a friend pays for you, the tracker records nothing even though you consumed ₹1,000. Nearly every app gets one side wrong:

| Approach | Example | What breaks |
|---|---|---|
| Splitter only | Splitwise, Tricount, Splid | No personal budget; "Track Personal Expenses" has 835 votes and Splitwise's answer since 2014 is "make a new group… with yourself as the only member" ([UserVoice](https://feedback.splitwise.com/forums/162446-general/suggestions/4084332-track-personal-expenses)); in a solo group "it does not show you the amount and says 'No Balance'" (same thread, 2023). Tricount *removed* "Personal mode" ([Help](https://help.tricount.com/articles/what-happened-with-tricount-premium)). |
| Tracker only | YNAB, Monarch, Goodbudget, Wallet | Category totals inflated by others' shares: "If you split $1,500 in shared expenses each month, your YNAB reports are inflated by $9,000 per year" ([splitwiseforynab](https://www.splitwiseforynab.com/blog/ynab-for-couples-separate-finances-complete-guide)). |
| Both apps, manual | Splitwise + any tracker | "Double entry for every single shared expense… Easy to fall behind… Miss one transaction and your accounts won't reconcile" ([splitwiseforynab](https://www.splitwiseforynab.com/blog/how-to-use-splitwise-with-ynab-definitive-guide)). Guides tell users to "log only your net 35 SGD" share ([CashJot](https://www.cashjot.com/blog/tricount-splitwise-or-expense-tracker)) — error-prone. |
| Both apps, glued by scripts | splitwise_for_ynab, ynab-splitwise, MonarchWise, Monarch-Splitwise Chrome ext. | Power-user only; US bank-sync dependent; breaks when APIs change ([GitHub danthareja](https://github.com/danthareja/splitwise_for_ynab); [MonarchWise](https://github.com/alex-shen1/MonarchWise)). The existence of 6+ community integrations is itself evidence of demand. |
| Vendor-built two-app bridge | BudgetBakers Wallet + ShareCost (Mar 2026); Tricount + bunq payment import (Oct 2025) | Still two apps / bank lock-in. |
| One app, both | Splitkaro ("Splitkaro counts the $24"), Honeydue (couples only) | Splitkaro has stability/paywall complaints; Honeydue users still can't see "my individual expenses + our split joint expenses" ([App Store](https://apps.apple.com/us/app/honeydue-couples-finance/id1157633945?platform=iphone&see-all=reviews)). |
| Payment app as splitter | GPay/PhonePe (India), Venmo Groups (US) | Splits exist where money moves, but no personal budget, categories, or multi-expense simplification. |

**What users explicitly ask for:**
- Splitwise user wanting it to replace their budget app: "Splitwise could be the only application to manage everything together" ([UserVoice](https://feedback.splitwise.com/forums/162446-general/suggestions/4084332-track-personal-expenses)).
- Splitwise user: "connected to my cheque account and each transactions (if needed) could be split staright up without having to create manual entries" ([App Store NZ](https://apps.apple.com/nz/app/splitwise/id458023433?platform=iphone&see-all=reviews)).
- Splitwise Pro user: charts unclear whether they show "what Both people spent… or… what I specifically spent after splitting" ([App Store US](https://apps.apple.com/us/app/splitwise/id458023433?see-all=reviews)).
- Honeydue: "my individual expenses + our split joint expenses" total; "swipe to split an expense" (App Store US).
- Splid: record the card-settled amount in home currency alongside the local-currency expense (App Store UK) — i.e., reconcile split to the actual bank charge.

**Design implications for the new app [INFERENCE from evidence above]:**
1. **One ledger, two views.** Every transaction stores *paid by* (cash-flow) and *consumed by* (shares). Personal analytics default to "my share" (consumption), with a toggle for "cash out of my accounts"; receivables/payables are a separate balance, not spend.
2. **Split from the transaction feed.** Swipe/one-tap "split" on any auto-captured spend (India: SMS/UPI notification parsing; elsewhere: bank sync/CSV/email receipts) so shared spend is entered once — never in two apps.
3. **Friend-paid-for-me** creates a consumption entry + payable without any bank movement; **settlement** via UPI deep link is recorded as a transfer (no category), exactly mirroring the YNAB phantom-account logic but automatic.
4. **Non-user participants** (placeholder people, shareable read-only link, optional claim) — a top Splitwise/Tab/splitty complaint.
5. **Multi-currency per expense with actual card-settled amount** for trips; personal totals in home currency.
6. **Privacy scopes**: personal (private), shared-with-partner (ours), group (trip/flat). Partner sees only "ours" + optionally balances — addresses Monarch/YNAB privacy gaps.

---

## Cross-cutting pain points (ranked by severity × frequency)

1. **Paywalls on core actions / bait-and-switch monetization (very high).** Splitwise daily caps + ads + feature removal; Tricount removed paid features users had bought; Splitkaro gates search/totals; per-user pricing for a group activity. Users explicitly resent paying for "division addition subtraction and multiplication" ([News18](https://www.news18.com/tech/splitwise-now-limits-number-of-expenses-you-can-add-for-free-but-here-are-4-apps-that-dont-8720041.html)).
2. **Double entry between personal tracker and splitter (high, structural).** No mainstream app does both well; workarounds are manual phantom accounts or scripts (see section above).
3. **Sync reliability & data loss (high).** Tricount (2024–2026), Honeydue bank links, Spendee shared wallets, Splitkaro crash. Trips are the worst moment to lose data.
4. **Settlement friction / payment rails (high in India).** Splitwise has no UPI; Venmo/PayPal US-only and flaky; users manually copy UPI IDs ([FairShare, competitor](https://fairshareapp.co.in/blog/top-5-splitwise-alternatives-india.html)). Zelle long-requested in US.
5. **Multi-currency trips (medium-high).** Conversion paywalled (Splitwise) or set-once currency (Tricount); card FX vs app FX mismatch (Splid users).
6. **Onboarding friction for others (medium-high).** Forcing every member to install/sign up; organizers want to be sole bookkeeper or share a link.
7. **Itemized bills (medium).** Pro-only in Splitwise; OCR misreads tax/tip/totals; quantity splits; custom % per item; self-claim links.
8. **Couples privacy: yours/mine/ours (medium).** Monarch shows all accounts; YNAB Together asymmetric; Honeydue "choose what to share" but neglected; Zeta shut down.
9. **Recurring household bills (medium).** Paywalled in Settle Up; absent in Kittysplit/Splid; Spliit beta.
10. **Offline on trips (medium).** Praised in Splid/Settle Up/Tricount; Spliit web lacks it (community built Spliit2Go).
11. **Auditability / overview (medium).** Tricount: no export/overview; Settle Up: settled vs unsettled unclear; Tab: no audit trail; Splid: delete notifications missing.
12. **Notifications misuse (low-medium).** Splitwise promotional pushes disguised as reminders; conversely Kittysplit has no reminders at all.

---

## What users love (keep these)

- **Debt simplification / minimal transfers** — "minimises the cross transactions is marvellous" ([Splitwise NZ](https://apps.apple.com/nz/app/splitwise/id458023433?platform=iphone&see-all=reviews)); Splid/Settle Up "minimal payments".
- **Fast add-expense flow** — Settle Up: "the fastest expense adding framework, because you can start by tapping the name of the person who paid" ([App Store US](https://apps.apple.com/us/app/settle-up-group-expenses/id737534985?see-all=reviews)).
- **Weights/default shares for couples & families** — "a married couple can have an assigned value of 2" (Settle Up); "'variable shares' for couples is wonderful" (Settle Up Play).
- **No sign-up / link sharing / view-only links** — Splid "no registration or signups"; Settle Up "send the info to people without them having to sign up"; Kittysplit any-browser links.
- **Offline-first** — Splid "perfect when you're traveling"; Settle Up/Tricount offline.
- **Fair monetization** — Kittysplit: "Monitisation is really fair… one time payment of a few bucks for the entire group"; Settle Up Group Premium "one time cost, benefits all transactions within the group".
- **Auto currency conversion with daily rates** — Splid "€ spend converted to £ was great".
- **Export/summaries** — Splid "summary PDF is smashing"; Settle Up "exporting transactions is not behind a paywall".
- **Accurate receipt OCR + per-item assignment, shared items, tax/tip pro-rata** — splitty/Tab praise.
- **In-context partner communication** — Honeydue "comment and react to individual transactions makes a chore fun".
- **Privacy / no data hunger** — Splid "Doesn't seem to want your data, no need for an account"; Spliit "No ads. No account."
- **Calculator in amount field; "pencil in" estimates** — Tricount math in amount field (App Store US 2025).
- **Running tab between partners that balances over time** — Tricount couples "most of the time everything balances anyway".

---

## Unmet needs / feature wishes (explicit requests)

1. **Personal expense tracking inside the splitter; true "my share" totals** — Splitwise UserVoice (835 votes); Honeydue "individual + split joint"; Splitwise charts ambiguity.
2. **Auto-capture + one-tap split from bank/SMS** — "connected to my cheque account… split staright up" (Splitwise NZ). (India: SMS/UPI parsing [INFERENCE].)
3. **Native settlement rails: UPI (India), Zelle (US), UK bank links/IBAN** — Splitwise US, Splid UK reviewers; India UPI gap ([FairShare](https://fairshareapp.co.in/blog/top-5-splitwise-alternatives-india.html)).
4. **Non-member participants / organizer-as-bookkeeper** — Splitwise US 14-person family trip; Tab "Asking them to join the bill… is way beyond what they would be willing to do."
5. **Self-claim itemization link** (participants tick their own items) — splitty review.
6. **Itemize from existing photo; custom % per item; quantity splits; multiple tax lines** — Splitwise US, Tab, splitty reviews.
7. **Recurring bills for rent/utilities** — Kittysplit, Splid reviews.
8. **Dual-currency entries (local + card-settled home currency); editable group currency** — Splid UK/US, Tricount CA.
9. **Search & full export (CSV/PDF/Excel) free** — Tricount (export removal backlash), Goodbudget (in-app search).
10. **"Me" identity view & per-person breakdown** — Splid "just show the bills that affect me"; Tricount "tap on someone name… see all his shares".
11. **Activity/delete notifications & audit trail** — Splid deletes; Tab audit trail.
12. **Drafts / no lost entries; time editing / correct ordering** — Splitwise draft loss; Splid time.
13. **Prepaid kitty / shared pot balance** — Splid Play "a separate kitty that can be for any prepaid balances."
14. **Settled-vs-open marker** — Settle Up Play "unique icon for transactions that were settled."
15. **Fixed amount for one person + split remainder; per-person adjustments; family sub-groups** — Settle Up US review.
16. **Multiple sharing groups (partner, family, friends) in a personal tracker** — Wallet Play review.
17. **Privacy controls for couples (hide accounts, private budgets)** — Monarch help admits gap; r/ynab discomfort.
18. **Android + offline for OSS option** — Spliit #511/#658.

---

## Sources

- https://feedback.splitwise.com/knowledgebase/articles/2010350-why-am-i-seeing-an-expense-limit
- https://www.splitwise.com/pro
- https://play.google.com/store/apps/details?id=com.Splitwise.SplitwiseMobile&hl=en_IN
- https://apps.apple.com/us/app/splitwise/id458023433?see-all=reviews
- https://apps.apple.com/nz/app/splitwise/id458023433?platform=iphone&see-all=reviews
- https://feedback.splitwise.com/forums/162446-general/suggestions/4084332-track-personal-expenses
- https://feedback.splitwise.com/knowledgebase/articles/301146-can-splitwise-do-currency-conversion-between-multi
- https://www.news18.com/tech/splitwise-now-limits-number-of-expenses-you-can-add-for-free-but-here-are-4-apps-that-dont-8720041.html
- https://www.reddit.com/r/personalfinance/comments/186v5wj/the_downfall_of_splitwise/ (snippet only)
- https://www.desidime.com/discussions/splitwise-alternatives
- https://hipposplit.com/blog/splitwise-review/
- https://split-circle.com/en/blog/splitwise-daily-limit
- https://thehisaab.com/blog/splitwise-free-plan-limits
- https://fairshareapp.co.in/blog/top-5-splitwise-alternatives-india.html
- https://niptao.app/en/blog/google-pay-split-bill-alternative
- https://techcrunch.com/2022/05/03/bunq-to-acquire-group-expenses-app-tricount/
- https://static.bunq.com/website/documents/bunq-report-annual-2021-en.pdf
- https://press.bunq.com/246589-from-roommates-to-road-trips-tricount-tallies-16-4-billion-shared-in-2024/
- https://help.tricount.com/articles/what-happened-with-tricount-premium
- https://apps.apple.com/us/app/tricount-split-settle-bills/id349866256
- https://apps.apple.com/ca/app/tricount-split-settle-bills/id349866256?platform=iphone&see-all=reviews
- https://play.google.com/store/apps/details?id=com.tribab.tricount.android&hl=en_US
- https://www.bananasplitapp.com/blog/what-is-tricount-how-does-it-work/
- https://play.google.com/store/apps/details?id=cz.destil.settleup&hl=en_IN
- https://apps.apple.com/us/app/settle-up-group-expenses/id737534985?see-all=reviews
- https://settleup.io/tips
- https://splid.app/
- https://play.google.com/store/apps/details?id=splid.teamturtle.com.splid&hl=en_IN
- https://apps.apple.com/us/app/splid-split-group-bills/id991473495?platform=iphone&see-all=reviews
- https://apps.apple.com/gb/app/splid-split-group-bills/id991473495?see-all=reviews
- https://github.com/spliit-app/spliit
- https://spliit.app/
- https://github.com/spliit-app/spliit/issues/658
- https://github.com/spliit-app/spliit/issues/511
- https://github.com/spliit-app/spliit/issues/178
- https://github.com/spliit-app/spliit/issues
- https://play.google.com/store/apps/details?id=com.kittysplit&hl=en_IN
- https://www.kittysplit.com/en/splid-alternative
- https://famzam.app/kittysplit-alternative
- https://www.honeydue.com/
- https://apps.apple.com/us/app/honeydue-couples-finance/id1157633945?platform=iphone&see-all=reviews
- https://appimp.app/apps/honeydue-couples-finance
- https://thecollegeinvestor.com/24184/zeta-review/
- https://www.prnewswire.com/news-releases/acorns-accelerates-family-product-strategy-with-acquisition-of-couples-app-zeta-302488755.html
- https://goodbudget.com/blog/2024/08/changes-to-goodbudgets-plan-offerings/
- https://goodbudget.com/blog/help_category/billing
- https://apps.apple.com/us/app/goodbudget-budget-planner/id471112395?see-all=reviews
- https://www.fincomparelab.com/reviews/goodbudget-review/
- https://www.monarch.com/blog/shared-views
- https://help.monarch.com/hc/en-us/articles/20926382202004-Monarch-for-Couples-and-Households
- https://help.monarch.com/hc/en-us/articles/42228648365076-Shared-Views-in-Monarch
- https://www.mezzi.com/blog/monarch-raised-prices-budgeting-app-right-tool-investor
- https://spentioapp.com/blog/best-monarch-money-alternatives-2026
- https://github.com/tevanburen/Monarch-Splitwise
- https://github.com/alex-shen1/MonarchWise
- https://www.ynab.com/terms/ynab-together
- https://www.nerdwallet.com/finance/learn/ynab-app-review
- https://www.fincomparelab.com/guides/ynab-pricing/
- https://www.reddit.com/r/ynab/comments/z8dwbj/ynab_together_privacy (snippet only)
- https://www.reddit.com/r/ynab/comments/146tb3v/shared_subscription_or_separate (snippet only)
- https://www.splitwiseforynab.com/blog/how-to-use-splitwise-with-ynab-definitive-guide
- https://www.splitwiseforynab.com/blog/ynab-for-couples-separate-finances-complete-guide
- https://github.com/danthareja/splitwise_for_ynab
- https://www.cashjot.com/blog/tricount-splitwise-or-expense-tracker
- https://borderlessbudget.com/blog/ynab-for-expats-review
- https://play.google.com/store/apps/details?id=com.bsquare.splitkaro&hl=en_IN
- https://www.appbrain.com/app/splitkaro-best-expense-app/com.bsquare.splitkaro
- https://support.google.com/pay/india/answer/12025420?hl=en
- https://support.google.com/pay/india/answer/11420982?hl=en
- https://www.newsbytesapp.com/news/science/how-to-split-bills-with-friends-using-phonepe-on-android/story
- https://newsroom.paypal-corp.com/2023-11-14-Introducing-Venmo-Groups
- https://apps.apple.com/us/app/splitty-split-bills-tip/id1499263885?see-all=reviews&platform=iphone
- https://apps.apple.com/us/app/tab-the-simple-bill-splitter/id595068606?see-all=reviews
- https://splittyapp.com/vs/plates/
- https://budgetbakers.com/en/blog/2026-03-introducing-sharecost-smarter-way-split-expenses/
- https://play.google.com/store/apps/details?id=com.droid4you.application.wallet&hl=en_US
- https://support.budgetbakers.com/hc/en-us/articles/7149394922002-Everything-about-Group-Sharing
- https://apps.apple.com/us/app/expense-budget-app-spendee/id635861140
- https://www.capterra.com/p/238829/Spendee/
- https://www.getwalnut.com/blog/walnut-launches-bill-split-p2p-money-transfers-visa-icici-bank
- https://tracxn.com/d/companies/axio/__7ZJXjN0_0G6wspYYJaKGGh60BYXXdYtIzafg8MMFSqI

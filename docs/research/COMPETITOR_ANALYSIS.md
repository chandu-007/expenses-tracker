# Competitor analysis: expense trackers, India and global (update of the 2026-10-05 pass)

**Research date: 2026-10-28** (all "accessed" dates below are this date unless stated). This file updates, and does not replace, `docs/MARKET_RESEARCH.md` and `docs/research/{india,global-budgeting,manual-trackers,reviews,shared,tech}.md` (written 2026-10-05). Where a fact here is not re-checked, it says "(earlier pass)".

**How to read the tags**
- **[V]** verified on a primary source (the app's own site, store listing, help page, regulator or vendor docs) read on 2026-10-28.
- **[S]** reported by a secondary source (blog, review site, news) read on 2026-10-28; the source is linked.
- **[E]** carried over from the earlier pass (2026-10-05), not re-read today.
- **[I]** inference by me; not stated by any source.
- **[U#]** unverified; listed in section 9. Nothing marked [U#] should be quoted as fact.

**Evidence limits.** Reddit pages could not be read directly (search snippets only). Play pages were read through a text reader, so rating and review counts are the values shown to an Indian (en_IN) visitor on 2026-10-28; Play shows recency-weighted ratings that can differ from the scraper "all-time" figures in `india.md`. Package names I guessed for Money Manager, Goodbudget, PocketGuard, Toshl, Moneyview and Kuvera returned 404, so those listings were not re-read ([U8]).

**Second pass on our own column (2026-10-28, HEAD 825b814).** Cells and sentences about our app in sections 3.1, 4, 6, 7 and 8 were re-read against the code and corrected where the 5 Oct claims were stale; corrected cells carry **[V code]**. Evidence per item is in `docs/research/OUR_FEATURES.md` section 17b. Wrong "we do not have it" claims found: net worth (one figure exists on Accounts), safe-to-spend (budget-based exists), a merchant list (97 names exist), card-payment transfers on capture (exist), refund matching from texts (exists), the Play SMS "not yet obtained" wording (owner reports accepted).

---

## 1. What changed since the 2026-10-05 pass (read this first)

| # | Change or correction | Evidence | Effect on us |
|---|---|---|---|
| 1 | **Fold's published price fell.** FAQ (updated Oct 2026) says Fold Plus is **₹999/yr, ₹799/quarter, ₹349/month (App Store), lifetime "Believer" ₹24,999**; 7-day free trial. The earlier pass had list ₹2,999/yr. Fold also states "it does not read your SMS", and that card spends come from Gmail card-alert emails. | [V] https://fold.money/faq (accessed 2026-10-28) | Fold has gone to the privacy-by-AA position and is cheaper than assumed. Our price (free) is not a differentiator against it; **no-cloud** and **cash/SMS capture** are. |
| 2 | **Fold's own claim of 4.5 on both stores does not match Play.** Play (en_IN) shows **3.1 stars, 1.43K reviews, 1L+ downloads, updated 26 Oct 2026**. The FAQ says "rated 4.5 on both the App Store and Google Play (October 2026)". | [V] https://play.google.com/store/apps/details?hl=en_IN&id=money.fold.marble vs https://fold.money/faq | Company claim vs store value differ; I did not read the App Store page ([U2]). |
| 3 | **Google Play SMS policy: money-management exception is still there**, listed exactly as "SMS-based money management: for example, apps that track and manage budget" with eligible permissions `READ_SMS, RECEIVE_MMS, RECEIVE_SMS, RECEIVE_WAP_PUSH`. The July 2026 preview only changes `READ_CALL_LOG` account verification (effective 27 Jan 2027). The exception is described as **temporary**, "subject to Google Play review and approval". | [V] https://support.google.com/googleplay/android-developer/answer/17225965 | Our SMS plan (RECEIVE_SMS only) is policy-consistent today. The "temporary" wording is a standing risk (section 3.1). |
| 4 | **AA network fees are about to change.** Sahamati deferred the AA network's "new standard pricing model" from 1 Nov to **1 Dec 2026**, "giving lenders and FIUs another month of free data access". | [S] https://www.business-standard.com/finance/news/account-aggregator-network-s-new-pricing-model-rollout-postponed-by-a-month-126102601261_1.html and https://bfsi.eletsonline.com/sahamati-defers-account-aggregator-pricing-model-december-1-fiu-fip-fees (headlines/snippets only; article bodies not captured, [U9]) | Another reason a free, no-server app should not depend on AA. |
| 5 | **RBI issued consolidated AA Directions on 28 Nov 2025** ("Non-Banking Financial Companies - Account Aggregator Directions, 2025"). | [S] search result https://rbidocs.rbi.org.in/rdocs/notification/PDFs/368MDA3A72C813679452CB0A291E6B300DB59.PDF (read, but the text I extracted did not state FIU conditions; [U9]) | No change found to the FIU eligibility rule below. |
| 6 | **DPDP dates confirmed**: Rules notified **13 Nov 2025**; Consent Manager (Rule 4) from **13 Nov 2026**; main duties (Rules 3, 5-16, 22-23; most of Act sections 3-17) from **13 May 2027**. | [S] https://dpdpact.net/dpdp-rules-2025 (cites the Gazette and MeitY; Gazette PDF not read) | Same as `india.md`. |
| 7 | **New Indian on-device SMS trackers keep arriving.** "Mera Kharcha / Mera SMS" announces an all-new launch for **1 Nov 2026 (Android), 15 Nov (iOS)** with 29 app languages, voice entry in Hindi (Premium) and optional SMS tracking; PennyWise ("no sign-up", reads UPI/bank SMS), SpendCtrl ("data stays on your device") and Spenrol are live on Play. | [V] https://merakharcha.in/ ; [S] search listings (PennyWise https://play.google.com/store/apps/details?hl=en-US&id=com.pennywiseai.tracker, SpendCtrl https://www.spendctrl.com/) | The "private SMS tracker" niche is already crowded with indies; see SWOT. |
| 8 | **axio is still alive and still broken for many**: Play shows 3.8 stars, 2.66L reviews, 1Cr+ downloads, updated 15 Jul 2026. Recent reviews (Aug 2026): SMS detection stopped from 2 Aug, duplicated credit-card spend and bill payment, app refusing to open until auto time sync is on. | [V] https://play.google.com/store/apps/details?id=com.daamitt.walnut.app&hl=en_IN | Answers "is Walnut still operating": **yes, as axio** (owned by CapFloat, a lender, loans 14%-35% p.a.). |
| 9 | **Apple free-signing limits confirmed on Apple's own page**: Personal Team = up to 10 App IDs and 3 devices, which **expire after 7 days**, max 3 apps per device; provisioning profiles expire **7 days after issue**. | [V] https://developer.apple.com/help/account/basics/about-your-developer-account | Matches our iPhone test-build notice. |
| 10 | **FinArt** (closest paid, privacy-positioned competitor) updated on Play **28 Oct 2026** ("Critical bug fix for missing app notifications and duplicate accounts"), 4.3-4.4 stars, 6.2K reviews, 10L+. Its Play Data safety shows "Data can't be deleted" while the listing advertises a Private Mode. | [V] https://play.google.com/store/apps/details?id=com.finart&hl=en_IN | Our no-account/no-INTERNET claim is stronger and checkable. |

---

## 2. App-by-app profiles

Format: platforms; price; bank connection; SMS or notification parsing; cloud sync; multi-user; receipts/OCR; budgets/bills; reports; import/export; currency; widgets/watch; privacy; offline; ads; limits; review themes; status. Every row lists its source tag.

### 2.1 India

**axio (ex-Walnut)**: Android-only on Play (Axio Digital Pvt. Ltd., Pune) [V].
- *Price:* free; monetised by Pay Later, personal loans "14% to 35% p.a." with up to 2% processing fee, and fixed deposits [V Play listing].
- *Capture:* "SMS-based money management"; bank, card, wallet and Sodexo spends from SMS; manual entries; bill/receipt photos attached to transactions; tags, notes, search; utility bill reminders and card-due reminders [V].
- *Privacy:* Play Data safety: "may share Location and Personal info with third parties"; "may collect Location, Personal info and 5 others"; encrypted in transit; deletion on request [V]. Listing says it "does not read your personal SMS or upload any sensitive data" [V], but SMS also used for loan "fraud prevention" and FD "SIM-device binding" [V]. Account required [I: loan/FD products need sign-in; not checked].
- *Rating:* 3.8 stars, 2.66L reviews, 1Cr+ downloads, updated 15 Jul 2026 [V].
- *Review themes (Aug 2026):* undetected bank SMS since 2 Aug, rescan does not fix it, no support reply (12 found helpful); duplicated card spend and bill payment; app will not open until auto time sync is on [V Play reviews]. Older: loan upsell and lost categories on new phone [E].
- *Not found:* iOS app, web, family sharing [E].

**Fold**: Android and iPhone, web for Plus (QR login) [V https://fold.money/faq].
- *Price:* Fold Lite free (bank accounts, up to 5 credit cards, MFs, stocks, EPF, NPS, net worth, Spaces for 3, free Equifax score). Fold Plus **₹999/yr, ₹799/quarter, ₹349/month (App Store)**; Fold Believer lifetime ₹24,999 [V].
- *Capture:* Account Aggregator via two AAs (Finvu, CAMS Finserv); 26 banks (Oct 2026); daily fetch, 5 manual refreshes a month free, 15 on Plus; **no SMS reading**; credit cards from card-alert emails in a connected Gmail (free: 1 Gmail, Plus: 5; other mail via forwarding); 23 card issuers [V]. Joint accounts not supported [V].
- *Other:* 38 categories ("F1" engine) and merchant rules; Refund Center (mark a spend refundable, match refunds); Spaces for family (totals only, up to 3 free / 8 Plus); recurring/EMI calendar; "augmented balance" (bank minus card dues minus upcoming recurring); transaction groups exported as CSV; widgets on both OSs; iPhone lock-screen widgets; CSV export and web only on Plus; pay credit card bills via Bharat Connect [V].
- *Privacy:* data on AWS India, AES-256, ISO 27001; SEBI RIA INA000018072; "does not sell data" [V company claim]. Play Data safety: "No data shared with third parties" [V].
- *Rating:* **Play 3.1 stars, 1.43K reviews, 1L+, updated 26 Oct 2026** [V]; FAQ claims 4.5 on both stores [V claim; mismatch, see section 1].
- *Review themes:* add-on cards not tracked (ICICI Sapphiro), requests for RD tracking and free web [V Play reviews Dec 2025, Aug 2026].
- *Limits:* needs bank on AA, joint accounts excluded, no cash? (FAQ mentions custom entries for assets; manual spend entry for cash not confirmed) [U1].

**ET Money**: Android, iOS, web [E]. Play (updated 25 Oct 2026): 4.4 stars, 2.33L reviews, 1Cr+; title "ET Money Mutual Fund, SIF, SIP" [V]. The listing text does not pitch expense tracking [E]; whether the in-app expense manager still works is **unverified** [U3]. Data safety: "may share Location with third parties; may collect Personal info, Financial info and 7 others" [V]. Recent review: "slower and heavy, no new features, more efforts on pushing premium" (18 Jul 2026) [V].

**Moneyview**: loan-first super-app with a "Money Manager"; site claims "13M+" (money-manager page) [S https://moneyview.in/money-manager, fetched page resolved to the home page]. Money Manager parses bank SMS, supports cash entries, budgets, backup [E]. ISO 27001:2022 certified per its page [S]. Current SMS permission and ads/loan upsell on Play not re-read (guessed package 404) [U8]. Treat as a lead-generation surface [E].

**CRED (bills part)**: Play 4.7 stars, 30L reviews, 5Cr+, updated 27 Oct 2026 [V]. Credit-card bills, UPI, rewards; CRED Money (AA-based bank tracking; "won't be monetizing") launched Jul 2024 [E]. CRED's privacy policy says access to your email is via the email provider's mechanism and, if you authorise it, CRED "will securely store account particulars" for card tracking [V https://cred.club/legal/privacy, snippet]. Data safety: "may share Personal info, Financial info and 2 others with third parties" [V]. Reviews (Sep 2026): payment verification takes 3 days, no calling support; praised UI; "detects..." (truncated) [V]. Not a budgeting tool [I].

**Google Pay / PhonePe / Paytm spend views**: Play: GPay 3.9 stars, PhonePe 4.6, Paytm 4.8; all updated 24-27 Oct 2026 [V]. Paytm has "Spend Summary" (own UPI payments auto-categorised) [S https://paytm.com/blog/payments/paytm-spend-summary-upi-expense-tracker, from earlier pass]. A GPay reviewer wrote on 12 Sep 2026: "I cannot easily see how much I have spent in each payment category, such as fuel, groceries... This is one of the most important features" [V], i.e. GPay has no such view in that reviewer's experience. Each app sees only its own flows [E]. Whether PhonePe's "expense tracker" shown in a Medium post is current is [U10].

**FinArt** (Frienteer Software, Bathinda): Android + iPhone [V].
- *Capture:* bank SMS, bank-email, **bank-app notifications** on Android; iPhone: SMS (via Shortcuts, [E]), email, Apple Pay, PDF statements [E https://finart.app/best-expense-tracker-apps-india].
- *Price:* free 5-day trial then yearly subscription priced by country; Play reviewers ask for a monthly plan and call the yearly price too high (should be "₹500 max") [V Play reviews Jul/Aug 2026]. Developer says it prepares 6 months of existing data free at install so users can judge it [V]. Exact INR price not shown in what I read [U4].
- *Privacy:* "No mandatory email/phone registration"; **Private Mode** (transaction SMS/data not sent to FinArt servers); backup to your own Google Drive; does not connect to bank accounts [V]. Data safety: "No data shared with third parties"; "may collect Personal info, Financial info and 4 others"; encrypted in transit; **"Data can't be deleted"** [V].
- *Other:* family budget planner, family sync across devices, personal/business profiles, subscriptions, split expenses, multi-currency, custom month start, ad-free [V]. Support via WhatsApp (praised) [V].
- *Rating:* 4.3-4.4 stars, 6.2K reviews, 10L+, updated 28 Oct 2026 [V]. Complaint: "UI is too outdated" vs axio (13 Jul 2026) [V].

**Splitwise** (India usage is large: Play 1Cr+ downloads, 1.95L reviews, 4.0 stars, updated 17 Oct 2026 [V]).
- Free tier has a daily cap on expenses plus ads; Pro **$59.99/yr or $7.99/month** [S https://splitterup.app/blog/splitwise-pro-worth-it, updated 8 Oct 2026]; splitwise.com/pro lists "Unlimited expenses... no limits and no ads" but showed no price in the text I read [V]; INR price [U5].
- Features: any-currency expenses with offline entry, receipt OCR itemising, push feed [V Play listing]. No personal budgeting (835-vote request since 2014) [E].
- Reviews Aug 2026: "daily limit... like 5 requests", wait screens, push notification that was an ad [V].

**Others India:** INDmoney (Play 4.7, 4.64L reviews, 1Cr+, updated 26 Oct 2026; Data safety shares "Personal info, Financial info and 5 others") [V]; expense tracking is secondary [E]. Kuvera is MF investing (not expense) [E]; Play listing not re-read [U8]. Jupiter: neobank "Money" tab via AA [E]. **Fi Money: confirmed shut down** (banking services wound down 11 Mar 2026; customers to Federal Bank's FedMobile) [V https://techcrunch.com/2026/03/11/india-neobank-fi-winds-down-banking-services-on-its-platform ; https://fi.money/ banner "New savings accounts cannot be opened through the Fi app"]. Onemoney is a live RBI-licensed AA app (Play updated 20 Oct 2026, 1L+) [V]; it is the AA, not a budget app [I].

### 2.2 Global

**YNAB**: iOS, Android, web. **$14.99/month or $109/yr**, 34-day trial, no card needed on direct sign-up; sharing up to 6 people on one subscription; direct bank import for select US/CA/UK/EU banks, otherwise file import; **no multi-currency in one plan** [V https://www.ynab.com/pricing]. Play 4.6 stars, 25.4K reviews, 10L+, updated 20 Oct 2026; Data safety "may share Personal info and Device or other IDs with third parties" [V Play]. Review (18 Sep 2026): third return, "still can't work correctly with PayPal; can't add credit cards" [V]. No SMS parsing, no India banks [V pricing FAQ].

**Monarch Money**: iOS, Android, web. Core **$14.99/month or $99.99/yr** [V https://www.monarch.com/pricing]; Plus tier (forecasting, multiple income sources, investments depth) exists, $199/yr per earlier pass [E; the Plus price was not on the page text I read]. Play 4.8 stars, 26.2K reviews, 5L+, updated 21 Oct 2026; Data safety "may share Location, Personal info and 4 others" [V]. Review 5 Sep 2026: account-sync problems (e.g. Fidelity) [V]. US/CA only [E].

**Copilot Money**: iPhone, iPad, Mac, web; **no Android**. $13/month or $95/yr after a one-month trial [S Forbes Advisor https://www.forbes.com/advisor/banking/best-budgeting-apps; copilot.money's pricing text not captured, [U6]].

**Spendee**: Android, iOS, web. Free; Plus **$1.99/month or $14.99/yr**; Premium **$5.99/month or $35.99/yr**; 7-day trial on any plan [V https://www.spendee.com/pricing]. Play 4.0 stars, 63.7K reviews, 10L+, updated 22 Oct 2026; Data safety "no data shared with third parties", collects "Personal info, Financial info and 3 others"; lists an **AI Receipt Scanner (new)** [V]. Review 15 Sep 2026: UI change, freezes while categorising, "you're killing your app" [V].

**Wallet by BudgetBakers**: Android, iOS, web. Free manual tier; Premium for bank sync (15,000+ banks), group sharing; lifetime option listed at $29.99 on the App Store per USA Today [S]; official pricing page 404 [U6]. Play 4.6 stars, 3.88L reviews, 1Cr+, updated 14 Oct 2026; Data safety "may share Financial info with third parties"; multi-currency, automatic cloud sync, receipts and warranties [V]. Reviews: mis-categorised imports (29 Aug 2026), but praised lifetime option (31 Jul 2026) [V].

**Money Manager (Realbyte)**: Android, iOS, PC via Wi-Fi [E]. **Premium is a one-time purchase for lifetime use, not a subscription** [V https://help.realbyteapps.com/hc/en-us/articles/10669850586649]. Search snippets: Play 4.8 stars, 466K reviews, 10M+, "Contains ads" [S https://play.google.com/store/apps/details?hl=en_US&id=com.realbyteapps.moneymanagerfree]; iOS 4.8, 20K ratings [S App Store]; "Remove Ads" app $5.99 [S AppBrain]. Manual, offline-first, double-entry accounts, calendar, receipt photos, Excel backup [E]. Direct Play listing not re-read [U8].

**Monefy**: Android, iPhone; Play 4.0 stars, 1.95L reviews, 1Cr+, last updated **16 Aug 2026**; Data safety "may share App activity, App info and performance, Device or other IDs with third parties" [V]. Manual one-tap entry, Dropbox/Drive sync [E]. Ads on open and subscription [E]. Monefy site FAQ "Is Monefy free? What's included in the paid version?" was collapsed, price not captured [U7].

**Goodbudget**: envelope budgeting, free tier limits (20 envelopes, 2 devices) and Premium $80/yr; Play 3.2 [E]. Not re-read (listing 404/pricing page 500) [U8].

**PocketGuard**: Plus $12.99/month or $74.99/yr; "Leftover", subscription tracking; US/CA bank sync [E]. Pricing page returned 403 [U8].

**Mint → Credit Karma**: Mint shut 23 Mar 2024 [E]. Credit Karma (Intuit): Play 4.7 stars, 32.5L reviews, 5Cr+, updated 24 Oct 2026; Data safety shares "Personal info, App activity and Device or other IDs" [V]; no category budgets [E]. Not available as an expense tracker in India [I].

**Expensify**: Play 4.4 stars, 32.5K reviews, 10L+, updated 28 Oct 2026; SmartScan receipt scanning, distance, manual expenses; shares "Personal info, App activity and App info" [V]. Business reimbursement tool; consumer plan pricing not captured [U7].

**Zoho Expense**: Play 4.7 stars, 27.3K reviews, 10L+, updated 26 Oct 2026; Data safety "No data shared with third parties" [V]. India pricing page: a **Free plan** exists; paid plans have a **5-user minimum**; Premium support from ₹40,000/yr, Jumpstart from ₹49,999/yr; AI OCR in 38+ languages [V https://www.zoho.com/in/expense/pricing/]. Per-user price not captured [U7]. For business teams, not for personal budgeting [I].

**Money Lover**: Play 4.1 stars, 2.08L reviews, 50L+, updated 28 Oct 2026; Data safety "may share Personal info, App activity and 2 others with third parties" [V]. Prices ~ $3.99/month or $29.99/yr, lifetime $19.99, bank "Linked Wallet" sold separately [E]; the official premium page 404 [U7]. Old review: only one recurring transaction without paying $35 [V, 2021].

**Bluecoins**: Android, iOS. Play 4.3 stars, 40.9K reviews, 10L+, updated 21 Oct 2026; Data safety "No data shared... no data collected", "Data can't be deleted"? (line truncated, not confirmed) [V partial]. One-time Premium; SMS/notification auto-log, QIF/CSV, reconciliation [E]. v13 update drew complaints (29 May 2026); reminders not firing (20 Aug 2026); "push notification used to be great but no longer fills in the amount" [V].

**Cashew** (open source, Flutter): Play 4.8 stars, 28.1K reviews, 5L+, updated 16 Oct 2026; Data safety "no data shared, no data collected" [V]. Free core, Pro $11.99/yr or **$19.99 lifetime** [E]. Google Drive sync, works fully offline [V review]. GitHub: repo `jameskokoska/Cashew` pushed 2026-03-09, 4,743 stars, not archived; **latest tagged GitHub release is 5.3.4+396 from 2024-07-01** [V https://api.github.com/repos/jameskokoska/Cashew]: Play updates continue, but source releases lag.

**Actual Budget** (open source, local-first): latest release **v26.10.0 on 2026-10-02** [V GitHub API]. "Unabashedly local-first... works regardless of network"; optional E2EE sync; self-hosted or PikaPods [V https://actualbudget.org/]. Desktop/web/PWA; no native mobile [E]. Bank sync: SimpleFIN, GoCardless (closed to new signups Jul 2025) [E].

**Firefly III** (self-hosted PHP): latest release **v6.7.9 on 2026-10-11** [V GitHub API]. Self-hosted, free, rules, multi-currency, API [E]; docs site returned 403 [U8].

**Lunch Money**: **$10/month or $100/yr**, 30-day free trial, "price locked for life", all features included (Plaid sync, multi-currency, crypto, API) [V https://lunchmoney.app/pricing]. Earlier pass said minimum $60/yr from Mar 2026; the pricing page today shows $100/yr [V]: the earlier figure is outdated or describes a different cohort [U6].

**Toshl**: Free (2 budgets, CSV export, import from csv/ofx/qfx/qif/xls...), Pro (manual, unlimited), Medici (auto bank sync); 30-day trial; mobile + web [V https://toshl.com/pricing/]. Price figures were not in the text I read; earlier pass: Pro $19.99/yr, Medici $39.99/yr [E].

**Fina** (web): fina.money pricing page fetched but contained no readable text [U7]; earlier pass: $7-9.60/month [E]. **1Money**: status unknown [U7].

---

## 3. Regulatory and platform facts

### 3.1 Google Play SMS permission
- Policy (current page and July 2026 preview, accessed 2026-10-28): default SMS/Phone/Assistant handlers are the permitted uses; "SMS-based money management (for example, apps that track and manage budget)" is an **exception**: "Google Play may provide a **temporary** exception... when use of the permission enables the core app functionality... and there's currently no alternative method". Exceptions are "Subject to Google Play review and approval". [V https://support.google.com/googleplay/android-developer/answer/17225965]
- Compliance: spyware policy "prohibits exfiltration of data that is not related to policy compliant functionality. For example, personal loans or budgeting apps may not exfiltrate or share non-financial or personal SMS history of a user"; prominent disclosure and privacy policy required; changing use requires resubmitting the Permissions Declaration Form; undeclared use can lead to app removal or account termination. "Research (like market research based on SMS)" and "any transfer that results in a sale of this data" are invalid uses [V same page].
- **Our app:** requests only `RECEIVE_SMS` (not READ_SMS), has no INTERNET permission, a prominent disclosure screen and sender allowlist (`docs/PLAY_SMS_DECLARATION.md`, `OUR_FEATURES.md` 8 and 11). This is an unusually clean fit. **Status: the owner reports that Play accepted the SMS declaration and that testers use the SMS build; this is not visible in the repo** (no console export or approval message is checked in), so it is owner-reported, not verified here. The exception is described as temporary, so approval can still change [I on likelihood].
- **Notification access** (`BIND_NOTIFICATION_LISTENER_SERVICE`) is not in the SMS/Call Log table; Play requires its own justification [I; policy page for notification listeners not re-read, [U11]].
- Alternatives that do not need SMS permission: SMS Retriever/User Consent only deliver OTPs for your own app [V policy "Alternatives"], so cannot read bank alerts.
- **Rival evidence:** axio's listing declares SMS use for money manager, loan fraud prevention and FD SIM binding [V]; FinArt says SMS permission is "optional" [V]. Fold and CRED route around SMS via AA/email [V].

### 3.2 Apple limits
- **SMS and other apps' notifications:** no API for either; our `docs/IOS.md` documents this and the workaround [E, repo]. The workaround is Apple's own **Shortcuts "Message" communication trigger** (options: Sender, Message Contains) [V https://support.apple.com/guide/shortcuts/communication-triggers-apdd711f9dff/ios]. Whether "Run Immediately" works for every sender (e.g. unknown senders) is not stated on that page; the repo notes "Unknown Senders may not trigger" [E repo, OUR_FEATURES]. Real-device behaviour on iOS 27 untested per `OUR_FEATURES.md` [E].
- **Free signing:** Personal Team: 10 App IDs, 3 devices, 3 apps per device, **profiles expire 7 days after issue** and need rebuild/reinstall [V https://developer.apple.com/help/account/basics/about-your-developer-account]. A real distribution path needs a paid membership ($99/yr [I, standard Apple price, not re-checked]) or TestFlight [I].
- **App Store rule that finance apps must be published by a company, not an individual (guideline 5.1.1(ix))** [E tech.md; not re-read]: if we ever ship to the App Store, that matters.
- Competitor iOS reality: FinArt iPhone relies on SMS-via-Shortcuts, email, Apple Pay and PDF statements [E]; Fold uses AA and Gmail on both OSs [V]; Mera Kharcha announces an iOS build for 15 Nov [V].

### 3.3 RBI Account Aggregator: what a small developer must do
- **Who can be an FIU:** "Get registered and regulated by at least one of the Financial Service Regulators (FSR), namely RBI, SEBI, IRDAI, PFRDA", implement ReBIT API specs, test on RBI-approved AA sandboxes, then Sahamati certification [V https://sahamati.org.in/financial-information-user-fiu]. Federal Bank's page repeats: "FIU... is an entity registered with and regulated by any financial sector regulator" [V https://www.federal.bank.in/account-aggregator].
- **Examples that fit:** Fold is an FIU because it is a SEBI-registered Investment Adviser; Moneyview appears as an IRDAI corporate agent on Sahamati's list [E india.md]. Setu documents the Sahamati certification path for FIUs [V https://docs.setu.co/data/account-aggregator/licenses-and-go-live/go-live].
- **Pricing:** AAs publish models (per data fetch, per user, volume-based) [V https://finvu.in/pricing/index.html]; actual rates were not on the text I read [U9]. The network-wide standard pricing model rollout was deferred to 1 Dec 2026 [S, section 1 #4].
- **Our constraint:** an AA connection needs internet and a server-side FIU stack. Our app has **no INTERNET permission** by design. So AA is incompatible with the product's core promise, and a solo developer without a regulated-entity licence cannot be an FIU [I from the sources above]. A partner route (a licensed entity acting as FIU) would mean moving transaction data through someone else's servers; not recommended for this product [I].
- **Credit cards are not on AA**; Fold itself uses Gmail alert emails for cards [V Fold FAQ]. Our notification/email-notification route is the same idea but on-device.

### 3.4 DPDP Act and Rules
- Notified 13 Nov 2025; **Rule 4 Consent Managers 13 Nov 2026**; **13 May 2027** for most notice, consent, safeguards, rights and breach rules; Rule 6 lists "minimum reasonable security safeguards" (encryption or masking, access control, logs, backups); breach: inform affected persons and the Board "without delay", full details within **72 hours** [S https://dpdpact.net/dpdp-rules-2025, reviewed against Gazette by that site on 9 Aug 2026; Gazette not read by me].
- **On-device vs cloud:** the duties attach to a "Data Fiduciary" that determines purpose and means of **processing personal data**. [I] An app that never transmits data off the phone, with no developer-side access, arguably leaves the developer with little or no collected personal data; **I did not find a source that says so explicitly**, and the Act's definition of processing was not read. This needs a lawyer's confirmation before we market "DPDP-compliant by design". Cloud-sync products (Fold, axio, FinArt servers) face the full duty set: notice in 22 languages [E], per-purpose consent, withdrawal, erasure, 72-hour breach reporting [E, S].
- Marketing claim risk: Play Data safety must still be true. [V code] `docs/PLAY_LISTING.md:31` "Security practices: data encrypted at rest on the device." is true for the Android app (SQLCipher + Keystore: `EncryptedDriver.kt`); on iPhone the database has no app key (Data Protection only: `IosPlatform.kt:38`), and the automatic copies in a chosen folder are not encrypted (`DataScreen.kt:150`). `PRIVACY_POLICY.md:8` words the two platforms correctly.

---

## 4. Feature matrix

Legend: Y = yes, N = no, P = partial, ? = unverified, n/a = not applicable. Source tags per app are in section 2. "Ours" is from `docs/research/OUR_FEATURES.md` (code-verified for 0.7.1, not run on real devices); cells marked **[V code]** were corrected on 2026-10-28 by reading the code at HEAD 825b814 (see OUR_FEATURES 17b), because the 5 Oct pass was made before 0.6/0.7 shipped. Columns chosen as the ~10 most relevant: 2 India auto-capture (axio, Fold), 1 privacy-positioned paid (FinArt), 4 manual/offline (Money Manager, Wallet, Spendee, Cashew), 2 budgeters (YNAB, Monarch), Splitwise.

| Feature | Ours (0.7.1) | axio | Fold | FinArt | Money Manager | Wallet | Spendee | Cashew | YNAB | Monarch | Splitwise |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Android / iPhone | Y / Y (iPhone = free-signed test build) | Y / N [E] | Y / Y | Y / Y | Y / Y | Y / Y | Y / Y | Y / Y | Y / Y | Y / Y | Y / Y |
| Web / desktop | N / N | N [E] | Plus web | ? | PC via Wi-Fi [E] | Web | Web | Web/PWA [E] | Web | Web | Web |
| Price | Free (no IAP yet) | Free + loans | Free; Plus ₹999/yr; lifetime ₹24,999 | Trial then yearly sub (price [U4]) | Free w/ ads; one-time Premium | Free; Premium (price [U6]) | Free; $14.99 or $35.99/yr | Free; $11.99/yr or $19.99 lifetime [E] | $109/yr | $99.99/yr | Free w/ limits; $59.99/yr [S] |
| Needs account / sign-in | **No** | Yes [I] | Yes (phone, OTP) | **No** (optional) | No [E] | Yes for sync [I] | Yes [I] | No | Yes | Yes | Yes |
| Bank connection | None | None | **AA (26 banks)** + Gmail cards | None | None | Bank sync (Premium) | Bank sync (Premium) | None | Plaid-type (US/CA/UK/EU) | Plaid/MX/Finicity (US/CA) | None |
| Bank SMS parsing | **Y (Android SMS build; owner reports Play accepted the declaration, not visible in the repo)** | Y | **N** | Y | N [E] | N | N | N | N | N | N |
| Payment-app / bank-app notification parsing | **Y (GPay, PhonePe, Paytm, BHIM; mail apps opt-in)** | ? | N | **Y (bank apps)** | N | N | N | N | N | N | N |
| Email parsing | Via mail-app notifications (Android); Shortcuts email (iPhone) | N [E] | **Y (Gmail card alerts)** | Y | N | N | N | N | N | N | N |
| iPhone SMS route | Shortcuts automation + paste + share sheet | n/a | n/a (AA) | SMS (Shortcuts) [E] | N | N | N | N | N | N | N |
| PDF / photo statement import | **Y (PDF, scanned PDF, photo; totals check)** | N | N | PDF (iOS) [E] | N | CSV/XLS/OFX [S] | ? | CSV/Sheets [E] | File import | CSV | N |
| Receipt / bill-photo reading | [V code] **P**: items and printed total read on device, each item can be its own entry; merchant/date/tax not read | Attach photos only | Attach receipts | ? | Photo attach | Y (Premium) | **AI scanner** | N | N | Receipt import [E] | OCR itemising |
| Cloud sync / multi-device | **N** [V code] (backup restore merges by id; HLC op-log groundwork unread) | Y | Y | Y (own Drive or servers) | N (Wi-Fi/Drive backup) [E] | Y | Y | Drive | Y | Y | Y |
| Multi-user / family | **N** | N | Spaces (totals only) | Family sync | N | Group sharing (Premium) | Shared wallets | Shared via Drive [E] | Up to 6 on one sub | Household | **Groups** |
| Budgets | [V code] Overall + per-category limits, 6 period kinds (monthly/salary day, weekly, 2-weekly, twice a month, custom, financial year), one-period rollover switch, near/over/pace alerts, "left a day" after repeating bills | Y | Limited [I] | Y | Y | Y | Y | Y | **Zero-based** | Flex/category | N |
| Recurring / subscriptions / bills | Y (repeating, subscription finder, bills from SMS) | Bill reminders | Recurring calendar | Bills, subscriptions | Y | Planned payments | Y | Y | Scheduled | Recurring detect | Recurring expenses |
| Credit-card due / limit view | **Y (cycle, limit, due, minimum)** | Due reminders | **Y** (statement emails) | Y | Settlement dates [E] | ? | ? | ? | Y (card handling) | Y | N |
| Goals / net worth | [V code] Goals Y (target, due date, monthly amount); net worth **P** (one figure on Accounts, no history/assets) | N | **Net worth Y** | ? | Assets [E] | ? | ? | Goals | Goals | Net worth Y | N |
| Reports | [V code] Category/account/tag/merchant groups, by period/year/financial year/custom range, drill-in, compare, donut and bars, calendar | Y | Y | Y | Y | Y | Y | Y | Y | Y | Basic |
| Export / import | [V code] CSV, Excel, FY-summary Excel, encrypted/plain backup; generic CSV/Excel/PDF/photo import with detected columns and saved mappings; no PDF/JSON/OFX export | ? | CSV (Plus) | ? | Excel backup | CSV/XLS | ? | CSV/Sheets | CSV | CSV | CSV [I] |
| Multi-currency | Manual rate per entry, no live rates | ? | N [I] | Y | Y | **Y (150+ [E])** | Y | Y | One per plan | Limited [I] | **Y** |
| Widgets / watch | [V code] Android 5 widgets (standard 4x2, today 2x2, small 2x1, wide 4x1, large 4x4), Quick Settings tile, launcher shortcut; iPhone 2 widgets (Today, Quick add) and 3 App Intents; no lock-screen widget; no watch | ? | Widgets + iOS lock screen | ? | Quick-add widgets [E] | Widgets [E] | ? | Widgets [E] | Watch [E] | N [I] | N |
| Languages | **English only** [V code]: 9 externalised strings, 614 hard-coded `Text("` calls; Indian number grouping and lakh/crore words present | ? | English [I] | English [I] | Many [E] | Many [E] | Many [E] | Many [E] | English | English | Many [I] |
| Encrypted local DB | **Android SQLCipher; iPhone OS Data Protection only** | N [I] | n/a (server AES-256) | Not stated | N [I] | N | N | N | n/a | n/a | n/a |
| Internet permission | **None (Android manifest)** | Yes | Yes | Yes | Yes | Yes | Yes | Yes | Yes | Yes | Yes |
| Data sold / third-party sharing (Play Data safety) | **None collected** (declared) | **Shares Location, Personal info** | None shared | None shared | n/a | Shares Financial info | None shared | None shared/collected | **Shares Personal info, Device IDs** | **Shares Location, Personal info +4** | None shared |
| Ads | None | Loans upsell | None | None | Ads (free) | ? | ? | None | None | None | **Ads + daily cap (free)** |
| Offline | **Full** | Partial [I] | No (AA) | Private mode | Full | Partial | Partial | Full | Partial | No | Offline entry |
| Rating (Play en_IN, 28 Oct 2026) | not published | 3.8 (2.66L) | **3.1 (1.43K)** | 4.3 (6.2K) | 4.8 (466K) [S] | 4.6 (3.88L) | 4.0 (63.7K) | 4.8 (28.1K) | 4.6 (25.4K) | 4.8 (26.2K) | 4.0 (1.95L) |
| Last Play update seen | n/a | 15 Jul 2026 | 26 Oct 2026 | 28 Oct 2026 | ? | 14 Oct 2026 | 22 Oct 2026 | 16 Oct 2026 | 20 Oct 2026 | 21 Oct 2026 | 17 Oct 2026 |

Cell sourcing: Play rating/update rows, Data-safety row, price rows for Fold/YNAB/Monarch/Spendee/Lunch Money are [V]; Splitwise price [S]; Money Manager rating [S]; many "[E]" and "[I]" cells come from the earlier pass or from absence of a feature in the listing text and should be rechecked before being used in marketing.

---

## 5. Market segments and what users in India want

### 5.1 Segments (India)
1. **Lender-owned SMS trackers**: axio (Walnut), Moneyview. Mass reach (axio 1Cr+, Moneyview "13M+"), revenue from loans. Reviews are about broken SMS parsing and loan push [V axio reviews; E].
2. **AA-based PFMs**: Fold (and Jupiter, CRED Money, INDmoney). Reliable for bank balances, weak for cash, cards need email, bank outages [V Fold FAQ: "Some banks... occasionally stop responding to AA requests"].
3. **Paid privacy-positioned SMS/email/notification trackers**: FinArt. Same capture as axio without loans; paid [V].
4. **Indie on-device SMS trackers** (new in 2025-26): PennyWise, SpendCtrl, Spenrol, Mera Kharcha (launching 1 Nov 2026), Xyra, Trakio [V/S listings, E]. Free or cheap, "private", but little scale.
5. **Global manual trackers used in India**: Money Manager, Wallet, Monefy, Spendee, Cashew. Cash-friendly, no Indian bank awareness [E].
6. **UPI app spend views**: Paytm Spend Summary; GPay/PhonePe lack category views per user complaint [S/V].
7. **Splitters**: Splitwise, widely used, with daily caps and ads [V reviews Aug 2026].
8. **Business**: Zoho Expense (Indian vendor, free plan, 5-user minimum on paid), Expensify [V].

### 5.2 Evidence of what users want
| Want | Evidence (accessed 2026-10-28) |
|---|---|
| **SMS that works** (all banks, no misses/duplicates) | axio reviews Aug 2026: detection stopped, duplicates [V]; Vyaya's India guide: SMS parsing "breaks when a bank tweaks its message format, and captures nothing when you pay cash" [S https://vyaya.app/blog/best-expense-tracker-apps-india-2026]; MoonProduct: dual-SIM and format differences break parsers [S, E]. |
| **Cash included** | Same Vyaya article (cash trade-off) [S]; FinArt/Mera Kharcha market "UPI and cash" [V]. Earlier pass: cash invisible = PP-15 [E]. |
| **Credit-card tracking and due reminders** | CRED praised for card UX (review 10 Sep 2026) [V]; Fold built Gmail-based card tracking because cards are not on AA [V]; FinArt "never miss due bills" [V]. |
| **Bill reminders as adoption reason** | #1 adoption reason for FinArt users [E]; axio "payment reminders" in listing [V]. |
| **Category summary from UPI apps** | GPay review 12 Sep 2026 [V]. |
| **Privacy without losing automation** | Fold stresses "does not read SMS" and "does not sell data" [V]; FinArt Private Mode [V]; axio's Data safety shares Location and Personal info [V]; top FinArt reviewer feared OTP exposure [E]. 60% of 20 budgeting apps share data with third parties (Incogni) [E]. |
| **Hindi / regional languages and voice** | Mera Kharcha: 29 languages, Hindi voice (Premium) [V https://merakharcha.in/]. |
| **Support that answers** | CRED "no calling support" (12 Sep 2026) [V]; axio "no support from Axio" (10 Aug 2026) [V]; FinArt praised for WhatsApp support (5 Jul 2026) [V]. |
| **Monthly or cheaper pricing** | FinArt reviewers: yearly only, ₹500 max; Splitwise daily cap complaints [V]. |
| **AA** | Fold is the only mainstream AA-first PFM with scale in this set; its 3.1 Play rating (1.43K reviews) vs 4.5 claimed shows adoption and satisfaction are not proven [V]. Credit cards and joint accounts remain outside AA [V]. |
| **Data portability** | Fold paywalled CSV export and web [V]; Mint's shutdown and export window [E]. |

[I] Net: Indian users want automation and privacy at once; the SMS-on-device model delivers both, but only if parsing quality and cash/card correctness beat axio's.

---

## 6. SWOT: our app (0.7.1) against this market

Strengths are checked against `OUR_FEATURES.md`; none has been run on a real phone for capture timing (`REAL_DEVICE_CHECKLIST.md` pending).

**Strengths**
- **No INTERNET permission, no account, no ads, nothing collected**: no competitor in the matrix has this combination; Cashew declares no data collected but needs internet for sync [V]. The claim is verifiable from the manifest.
- **Capture breadth with human review**: SMS (RECEIVE_SMS only), payment-app notifications, mail-app notifications, Shortcuts/share/paste on iPhone, PDF/photo statements, bill photos; **nothing auto-posts** (`OUR_FEATURES` 8).
- **Credit-card model** (cycle, limit, due, minimum, pay-now transfer) and bills from SMS: close to Fold's card tracking without Gmail/server.
- **Encrypted backups with own passphrase**, free CSV/Excel export, auto copies to a folder.
- Large test base (1,363 `@Test`s; 180 commits) [E repo].

**Weaknesses**
- **No sync, no second device, no web, no watch** (ABSENT list 1-6). [V code] Groundwork exists (HLC op-log, unread) and a backup from another phone can be merged additively by restore (adds new items, never updates or deletes); edits and deletions do not travel. Money Manager has the same gap and gets sync-loss complaints [E]; Cashew/Actual solve it.
- **English only** vs Mera Kharcha's 29 languages [V].
- **iPhone is a free-signed test build** (7-day profile, 3 apps per device [V]); capture needs manual Shortcuts setup; **iPhone DB not app-encrypted** (Data Protection only) and the automatic copies are not encrypted [V code].
- **Play SMS build**: the owner reports Play accepted the SMS declaration and testers use the SMS build (owner-reported, not visible in the repo); notification-listener justification may still be asked [I].
- Parser: "about 1 in 65 directions misread on public samples" [E PHASE2 docs]; the 42-header sender allowlist is "written from public knowledge" and bank formats are code, not data [V code]; real-world parsing accuracy is unmeasured. A starter list of 97 merchant names exists, but no VPA/alias dictionary [V code].
- Partial or missing (checked in code, HEAD 825b814): net worth is one figure on Accounts with no history; no investments; no loan schedule; importers are generic (no presets); no redacted undetected-message report; statement import cannot make card-payment transfers [V code] (OUR_FEATURES 17b).
- Multi-currency is manual rates only (by design).

**Opportunities**
- axio and Fold have visible reliability/trust problems (axio SMS regressions, 3.8 stars; Fold 3.1 stars on Play) [V].
- Privacy-conscious users leaving loan-funnel apps; FinArt is the only paid alternative and has UI complaints [V].
- DPDP awareness from 13 May 2027 [S] may raise demand for on-device apps [I].
- Free iPhone test build is something no competitor offers; moving to TestFlight/App Store would open the iPhone audience (needs company publisher per guideline [E]).
- Low price pressure: FinArt's yearly-only model draws complaints; one-time or lifetime pricing is liked (Money Manager, Cashew $19.99, Wallet lifetime reviews) [V/E].

**Threats**
- Google can withdraw or deny the temporary SMS exception; SMS alerts are eroding (banks asked RBI to drop sub-₹100 alerts; sender-ID suffix changes broke axio for a bank) [E].
- Crowded indie niche: PennyWise, SpendCtrl, Mera Kharcha (1 Nov 2026), Spenrol [V/S].
- AA coverage growth (26 banks on Fold, joint accounts/cards pending) could remove the need for SMS for some users [V].
- Platform risk on iOS (Shortcuts behaviour, free-signing limits) [V].
- Review/competition from OS vendors (Apple Wallet Insights, ChatGPT Finances) [E, unverified today].
- A single maintainer: Ivy Wallet archived and Cashew release cadence show indie-risk [V GitHub API].

---

## 7. Opportunities and risks (action-oriented)

**Opportunities**
1. "Works without the internet and without your SMS leaving the phone" is provable (manifest, Data safety "no data collected"). Lead with it; add a one-screen "audit it yourself" explanation.
2. Quality of capture is where incumbents fail; the health screen with counts and "This is my bank" already exists [V code]; what is missing is the redacted undetected-message report and updatable bank rules (see improvement 1).
3. Credit-card-first features: India's card users are served by CRED (no budgeting) and Fold (email-based, Gmail required).
4. Migration paths: importers for Money Manager/Walnut-style CSVs, because Mint-like shutdowns made portability a buying criterion [E]. [V code] Generic CSV/Excel/PDF/photo import with auto-detected columns and saved mappings already exists; what is missing is app-specific presets and category/account columns.
5. Indian number formats already exist ([V code] `MoneyFormat.kt`: ₹12,34,567.89, ₹1.2L; amount in words in lakh/crore); a Hindi UI does not (English only), which is cheap differentiation against English-only Fold/FinArt/axio [I].

**Risks**
1. **Play SMS approval** (temporary exception, review per app). The owner reports Play accepted the declaration and testers use the SMS build; this is not visible in the repo (no approval letter or console state), so treat it as owner-reported. The exception is still described as temporary, so keep the non-SMS Play build (`bundlePlay`) as the fallback.
2. **Notification-listener scrutiny** on Play [I].
3. **iOS distribution**: free signing every 7 days; paid account/company publisher needed for App Store [V/E].
4. **Parser accuracy unmeasured**; a wrong auto-entry destroys trust [E PHASE2]. Review inbox mitigates.
5. **No sync** will lose users who change phones; encrypted backup + folder auto-copy is the only path.
6. **Claims drift**: `PLAY_LISTING.md:31` "Security practices: data encrypted at rest on the device." is true for the Android app it describes; `PRIVACY_POLICY.md:8` already separates Android (Keystore) from iPhone (Data Protection) [V code]. It would be false on an iPhone store listing; also say that the automatic copies in a chosen folder are not encrypted (`DataScreen.kt:150`).
7. **Regulatory**: DPDP position for an on-device app is unconfirmed [section 3.4]; get legal confirmation before marketing it.

---

## 8. Ranked improvements (15)

Effort: S = days, M = 1-3 weeks, L = a month or more. Value is my rating (H/M/L) of expected user value given the evidence; it is a judgement [I]. "Competitor" = who does it today.

| # | Improvement | Effort | Value | Evidence: who does it / what users ask |
|---|---|---|---|---|
| 1 | **Capture-quality loop: undetected-message report and updatable bank rules.** [V code] Counters and the health screen already exist (30-day counts per outcome, per-bank found/ignored, "Senders not on the list" with "This is my bank": `HealthScreens.kt:34-75`, `CaptureHealth.kt`). Missing: a redacted, user-shared sample of a message the parser did not understand (nothing builds or shares one), sender/format rules as data instead of Kotlin (42 built-in headers in `SenderRules.kt:26-38`, formats as regexes), and any measure of real accuracy | M | **H** | axio users report detection stopping on 2 Aug 2026 with no fix [V]; sender suffix change broke a bank [E]; our parser "about 1 in 65 misread" [E PHASE2]. Play requires no exfiltration, so a report must be a file the person shares [V policy]. The owner's testers now run the SMS build, so this is the next thing they will hit. |
| 2 | **Close the card double-count gaps that remain.** [V code] Capture side EXISTS: a card-payment text becomes a suggested transfer, a plain credit into a card is "payment received on the card", Mark paid / Pay now record a transfer, and a bank text plus a payment-app or email alert for one spend are paired (`LedgerViewModel.kt:1810-1817,1796`; `Duplicates.kt:58-75`; `PairingUiTest`). Missing: statement import has no transfer path (`StatementImporter.kt:57,70`: only EXPENSE or INCOME), so a bank statement's "CREDIT CARD / CC PAYMENT" debit is imported as spending while the card statement's purchases are also imported; the suggestion needs both a bank and a credit-card account to exist; a UPI payment made from a credit card has no special handling (account comes from last-4 digits) | S-M | **H** | axio review: card spends and bill payments duplicated [V]; Fold links a card payment to its bill so it is not spending [V]. |
| 3 | **Balance-based "augmented balance"** (account balance minus card dues minus found and upcoming bills). [V code] Budget-based "can spend about X a day" EXISTS on Home, the Budget screen and both Today widgets, after subtracting repeating expenses with a fixed amount still due (`BudgetMath.kt:74-89`, `BudgetStore.kt:110-114`). Missing: it needs an overall limit, ignores card dues and bills found in messages, and no screen subtracts dues from balances | S | M-H | Fold "augmented balance" (bank minus card dues minus upcoming recurring) [V]; Copilot/PocketGuard "free to spend/leftover" [E]. |
| 4 | **Grow the merchant/VPA knowledge.** [V code] A starter list of 97 merchant names in 7 categories EXISTS (`DefaultRules.kt`), plus user rules, learned payee to category, history, per-address naming, VPA person/business shape. Missing: a VPA-handle and alias to display-name map, a larger list, brand-name normalisation | M | M-H | Walnut reviewer renames `paytm@xytrq` to "MyGrocer" [V]; Fold's F1 engine with 38 categories [V]. |
| 5 | **Reimbursement tracking.** [V code] Refund linking EXISTS (manual "Record a refund", partial refunds tracked, To review matches a refund/reversal credit to a purchase within 120 days: `EntryActions.kt:161-170`, `LedgerViewModel.kt:1818-1825`), and so do splits with people, "A friend paid" and a "paid it back" settlement. Missing: an "expecting this back" mark on an expense, a list of pending reimbursements, auto-matching an incoming credit to a lent amount or a friend's share | M | M-H | Fold Refund Center (mark a spend refundable, match refunds) [V]; Splitwise lacks personal budget [E]. |
| 6 | **Hindi + 3-5 Indian-language UI.** [V code] ABSENT confirmed: 9 externalised strings (labels only), 614 `Text("` calls in `shared/.../ui` plus the English `Guide.kt`; no resources on iPhone. Indian number grouping and lakh/crore words EXIST (`MoneyFormat.kt`, `AmountWords.kt`). Step one is extracting strings | L | **H** | Mera Kharcha markets 29 languages and Hindi voice [V]. |
| 7 | **Encrypt what is still plain.** [V code] iPhone DB has no app key (`IosPlatform.kt:38,65,75`: Data Protection only); the automatic copies written to the chosen folder are NOT encrypted (`DataScreen.kt:150`) although manual backups can be. Cheap part: seal auto copies with the backup passphrase; larger part: SQLCipher or equivalent on iPhone. Also keep the per-platform wording ("encrypted at rest" is true for Android only) | S (auto copies) to M (iPhone DB) | M (trust) | FinArt/Fold market privacy [V]. |
| 8 | **Importers tuned to other apps.** [V code] Generic header detection with synonym lists and saved mappings per header row EXIST (`StatementDetector.kt:8-13`, `ImportProfiles`), plus Excel, PDF and photo statements. Missing: presets for Money Manager, Splitwise, YNAB, Walnut/axio exports; category, account, tag and currency columns (a file imports as date, description, amount, direction only) | M | M | Mint shutdown made portability a buying criterion [E]; Money Manager has Excel backup [E]; Fold paywalls export [V]. |
| 9 | **Annual bills, sinking funds, EMI schedules.** [V code] Yearly repeats EXIST (`RepeatUnit.YEAR`), goals compute a monthly amount to reach a date (`GoalMath.monthly`), EMI and premium texts are found as bills (`BillKind.LOAN_EMI`, `autoDebit`). Missing: spreading a yearly bill over months in budgets/safe-to-spend, a loan schedule with interest/principal, EMI detection from repeated debits | M | M | PocketGuard users ask for annual bills split over months [E]; Fold detects EMIs [V]. |
| 10 | **Multi-device story.** [V code] No sync. Groundwork only: `op_log` with HLC written by every store, read only by tests (`opsSince`). Today the one path is manual: restoring another phone's backup merges by id (new items added, nothing overwritten or deleted: `BackupStore.kt:52-54`, `BackupStoreTest.restoringIntoADeviceWithOtherDataMergesAndTheTotalsAddUp`), and auto copies can go to a folder. A real folder-based sync would ship ops as an encrypted file | L | M-H | Cashew (Drive) and Actual (E2EE sync) [V]; Money Manager lacks multi-device sync [E]. |
| 11 | **Net worth history and assets.** [V code] A single "Net worth" figure (sum of account balances, cards and loans negative) EXISTS on the Accounts screen (`AccountsScreen.kt:79-83`, `AccountsUiTest`). Missing: trend, manual assets, person balances and goals in it, a place on Home or Reports | S-M | L-M | Fold, CRED Money, Monarch net worth [V/E]. |
| 12 | **Receipt reading that fills merchant, date and tax.** [V code] Items and the printed grand total are read; "Tax and other charges" can be one more entry; the photo goes to the first entry (`BillItems.kt:10`, `BillEntries.kt`). Merchant, date and tax are skipped on purpose; plain attachments also exist | M | M | Spendee AI Receipt Scanner (new) [V]; Expensify SmartScan, Splitwise OCR itemising [V]. |
| 13 | **UPI-link settlement for shared expenses.** [V code] ABSENT: no `upi://` anywhere; people have no UPI id field. Recording "X paid it back" with the remaining balance EXISTS (`LoansScreen.kt:109`) | M | M | Splitwise is widely used in India but caps free use and has no UPI settlement [V/E]. [I: a `upi://` deep link needs no INTERNET permission] |
| 14 | **More free exports.** [V code] CSV (14 columns), Excel, Excel financial-year summary, and the lossless backup EXIST; no PDF, JSON, OFX/QIF, print or share on Reports, no export of a search result or one account | S | M | Toshl imports csv/ofx/qfx/qif [V]; Fold paywalls export [V]. |
| 15 | **More quick-add surfaces.** [V code] MOSTLY EXISTS: 5 Android widgets, Quick Settings tile, a launcher shortcut, 2 iPhone widgets, 3 App Intents with Siri phrases, and `expenses://add`. Missing: iPhone lock-screen/StandBy widgets and Control Center control (only systemSmall/systemMedium families), watch apps, Android Assistant action and share target | S-M | L-M | Fold lock-screen widgets [V]; Money Manager Quick-Add widgets [E]. |

Re-ranking note [V code]: the 5 Oct list put "card double counting" and "safe-to-spend" as if missing; both exist in part, so they are reshaped to the gaps that remain (2, 3). Net worth and quick-add were over-stated as absent and move down. Nothing from the earlier 15 was dropped; every row now says what exists and what is missing. Top 10 by value and effort: 1 capture-quality loop (M), 2 card double-count gaps in import (S-M), 3 balance-based augmented balance (S), 4 merchant/VPA knowledge (M), 5 reimbursement tracking (M), 6 Indian-language UI (L), 7 encrypt auto copies and iPhone DB (S to M), 8 importers (M), 9 annual bills and EMI (M), 10 multi-device (L).

Also on the critical path, not counted in the 15: **Play SMS declaration and closed test.** The owner reports Play accepted the declaration and testers use the SMS build; the acceptance and the closed-test status are not visible in the repo (`docs/PLAY_SMS_DECLARATION.md`, `docs/PLAY_LISTING.md` still describe the steps). Record the evidence in the repo.

---

## 9. Unverified items ([U#])

| ID | What is unverified | What I tried |
|---|---|---|
| U1 | Whether Fold supports cash/manual spend entry on the free plan; Fold's 4.5 App Store rating claim | FAQ read (no cash statement); App Store page not opened |
| U2 | Fold App Store rating (claim of 4.5) | Not opened |
| U3 | ET Money in-app expense manager still active | Play text has no expense mention; help page not re-read |
| U4 | FinArt exact INR price | Play and finart.app pages show "5-day trial, then subscription" only |
| U5 | Splitwise Pro price in INR | Third-party USD price only; official page shows no figure in fetched text |
| U6 | Copilot official price; Wallet Premium official price; Lunch Money's earlier "$60 minimum" vs today's $100/yr | copilot.money and budgetbakers pricing pages returned no price text or 404; used Forbes/USA Today |
| U7 | Monefy, Money Lover, Fina, Toshl, Expensify and Zoho (per-user) paid prices; 1Money status | Pages collapsed, 404, or no text |
| U8 | Current Play listings (rating/update) for Money Manager (Realbyte), Goodbudget, PocketGuard, Moneyview, Toshl, Kuvera; Firefly III docs | Guessed packages returned 404; docs 403 |
| U9 | RBI AA Directions 2025 FIU conditions; AA per-fetch rates; content of Sahamati fee model | PDF read but conditions not found in extracted text; Finvu page lists models only; news items by headline |
| U10 | PhonePe's current in-app expense tracker (Medium post only); Google Pay's current spend views | No primary page read |
| U11 | Play policy page for notification-listener (BIND_NOTIFICATION_LISTENER_SERVICE) justification | Not read |
| U12 | Reddit threads on SMS permission/privacy | Reddit blocked; only snippets seen |
| U13 | Bluecoins Data safety deletion line (truncated); Bluecoins SMS auto-log current state | Listing truncated; review text suggests the notification auto-fill degraded |
| U14 | DPDP position for an on-device-only app (not a Data Fiduciary?) | No source found; needs legal advice |
| U15 | Copilot has no Android app today | Earlier pass and an April 2026 review [S, https://pocketclear.app/blog/copilot-money-review-2026.html]; not re-checked on copilot.money |

(15 items above; U1-U15. Several contain more than one fact; counting each separate fact, the number of individual unverified claims is about 28.)

---

## 10. Sources (accessed 2026-10-28 unless stated)

Primary
- Google Play policy preview: https://support.google.com/googleplay/android-developer/answer/17225965
- Apple Developer account overview (Personal Team limits): https://developer.apple.com/help/account/basics/about-your-developer-account
- Apple Shortcuts communication triggers: https://support.apple.com/guide/shortcuts/communication-triggers-apdd711f9dff/ios
- Sahamati FIU requirements: https://sahamati.org.in/financial-information-user-fiu
- Finvu pricing models: https://finvu.in/pricing/index.html ; Setu FIU go-live: https://docs.setu.co/data/account-aggregator/licenses-and-go-live/go-live ; Federal Bank AA page: https://www.federal.bank.in/account-aggregator
- Fold FAQ: https://fold.money/faq
- Play listings (en_IN): axio https://play.google.com/store/apps/details?id=com.daamitt.walnut.app&hl=en_IN ; FinArt https://play.google.com/store/apps/details?id=com.finart&hl=en_IN ; and the same URL pattern for com.etmoney (id com.smartspends), CRED (com.dreamplug.androidapp), GPay, PhonePe, Paytm, Splitwise, YNAB, Monarch, Spendee, Wallet, Monefy, Expensify, Zoho Expense, Money Lover, Bluecoins, Cashew, INDmoney, Onemoney, Credit Karma, Fold
- Pricing: https://www.ynab.com/pricing ; https://www.monarch.com/pricing ; https://www.spendee.com/pricing ; https://lunchmoney.app/pricing ; https://toshl.com/pricing/ ; https://www.zoho.com/in/expense/pricing/ ; Money Manager help https://help.realbyteapps.com/hc/en-us/articles/10669850586649
- Mera Kharcha: https://merakharcha.in/
- GitHub API: https://api.github.com/repos/{jameskokoska/Cashew, actualbudget/actual, firefly-iii/firefly-iii, Ivy-Apps/ivy-wallet}
- Fi shutdown: https://techcrunch.com/2026/03/11/india-neobank-fi-winds-down-banking-services-on-its-platform ; https://fi.money/
- CRED privacy: https://cred.club/legal/privacy

Secondary
- DPDP: https://dpdpact.net/dpdp-rules-2025
- AA fee deferral: https://www.business-standard.com/finance/news/account-aggregator-network-s-new-pricing-model-rollout-postponed-by-a-month-126102601261_1.html ; https://bfsi.eletsonline.com/sahamati-defers-account-aggregator-pricing-model-december-1-fiu-fip-fees
- Splitwise price: https://splitterup.app/blog/splitwise-pro-worth-it
- Copilot price: https://www.forbes.com/advisor/banking/best-budgeting-apps
- Wallet price: https://www.usatoday.com/story/money/personalfinance/budget-and-spending/wallet-by-budgetbakers-review/91042751007
- India guides: https://vyaya.app/blog/best-expense-tracker-apps-india-2026 ; https://finart.app/best-expense-tracker-apps-india (FinArt's own comparison, biased)
- Other indie trackers: https://www.spendctrl.com/ ; https://play.google.com/store/apps/details?hl=en-US&id=com.pennywiseai.tracker

Repo
- `docs/research/OUR_FEATURES.md` (code-verified 0.7.1), `docs/PLAY_SMS_DECLARATION.md`, `docs/PLAY_LISTING.md`, `docs/IOS.md`, earlier research files under `docs/research/`.

# Indian finance, UPI and expense apps: UI/UX teardown and what we should learn (deltas only)

**Research date: 2026-11-13.** Scope: how Indian apps look, move, explain money and ask for trust, and what Indian users expect. Research only; no product code, version, emulator, Gradle or Xcode was touched. Written against origin/main `b899430` (0.13.0).

**Evidence tags.** **[V]** read on a primary source (app's own store listing, site, blog, help page, design-team post, regulator). **[S]** read on a secondary source (press, case study, comparison blog, student teardown). **[S\*]** secondary source seen only as a search snippet, page not opened (do not quote as fact). **[R]** verbatim user/review/forum quote (25 words or fewer, with date). **[I]** my inference, not stated by any source. **[code]** read from our repo. "Not found" means searched and nothing readable turned up. **No screenshot pixels were viewed by any researcher** (Play/App Store pages give image URLs only), so colours, chart styles, logo treatment and motion are known only where a text source describes them. Reddit was blocked on every read; only thread titles reached us. Details and per-topic gaps are in section 10.

**Delta rule.** Earlier work already covers pricing, Account Aggregator, Play SMS policy, DPDP, feature matrices, review mining and our own feature inventory. Section 1 lists what is not repeated. Every idea below is marked **NEW** (nothing like it ships in our app and no earlier doc proposed it) or **REFINEMENT** (extends something we ship or an earlier doc already lists).

---

## 1. Already researched / already in our app (not repeated)

**Earlier research (docs/research/*.md, MARKET_RESEARCH.md, COMPETITOR_ANALYSIS.md)**
- axio/Walnut SMS breakages (suffix change, duplicates, lost data on a new phone), loan funnel, 3.8 stars: `india.md`, `COMPETITOR_ANALYSIS.md` section 2.1.
- Fold (AA, no SMS, Gmail card alerts, Spaces totals-only, price changes, Play 3.1 vs claimed 4.5), Jupiter/CRED Money/INDmoney as AA apps, Fi shutdown 11 Mar 2026, Kuvera-by-CRED regression: `india.md`.
- Paytm Spend Summary exists; GPay lacks a category view per a Sept 2026 review; each UPI app sees only its own flows: `COMPETITOR_ANALYSIS.md` 2.1.
- FinArt (SMS/email/notification, Private Mode, "Data can't be deleted" label mismatch, dated UI), Splitwise caps/ads, Money Manager/Cashew/Wallet/Spendee facts: same files.
- UPI volumes, cash still growing, TRAI `-T/-S/-P/-G` header suffix, bank alert erosion, DPDP phasing (13 May 2027), RBI digital-lending rules, Play SMS exception wording: `india.md`, `COMPETITOR_ANALYSIS.md` section 3.
- Pain points PP-01 to PP-26, abandonment ranking, "no gamification built on streaks" decision, "zero promotional notifications": `MARKET_RESEARCH.md`, `reviews.md`, `PRODUCT_CONTEXT.md`.
- Ranked improvements 1-15 (capture-quality loop, card double-count in import, augmented balance, VPA/alias dictionary, reimbursements, Hindi UI (L), encrypt iPhone DB/copies, importers, annual bills/EMI, multi-device, net-worth history, receipt OCR, UPI settle link, more exports, lock-screen widgets): `COMPETITOR_ANALYSIS.md` section 8.

**Already in our app [code, from `OUR_FEATURES.md`, `HOME_OPTIONS.md`, `HOME_NUDGES.md`, `PHASE2_PROGRESS.md`]**
- **Slice 67 look-and-feel:** one motion language with a Motion setting (Off / Quick / Relaxed / Slow, follows the phone's remove-animations), haptics for save/undo/limit/paid/tick, container-transform from a tapped row into its editor, a lit translucent glass tab bar that glides on a spring (no true blur), rolling month total, animated budget bar, 14-day spending line you can drag, Apple-Wallet-style stack for accounts, amount read back in Indian words in the add form, rounded entry cards with a category tile.
- **Slices 46-59:** gradient hero card with blurred lights on Android 12+, frosted pill, Material icons on bar and More tiles, row-added glow, review cards that collapse, ring chart that sweeps in, bars that grow, "N to review" banner with the first three items.
- **Home Option B (Slice 112):** short month card with a `+158%` chip, Budget-left and Safe-to-spend tiles each naming their source, trip card with progress bar, ONE nudge in ONE look with ONE close button ordered bills > review > new bills > money back > no category > setup > first-week, smaller account tiles ("owed - due in 6 d", thin limit bar), quick entries, day totals in day headings, Add in the middle of the bottom bar, whole rupees on headline figures only, layout that drops extras at 150% text, fold two-pane.
- **Numbers:** Indian grouping (`₹12,34,567.89`), compact `₹950 / ₹1.2K / ₹12K / ₹1.2L / ₹3.4Cr` that **truncates, never rounds up** (so ₹99,99,999 reads `₹99.9L`, never `₹100L`), amount in lakh/crore words (`MoneyFormat.kt`, `AmountWords.kt`).
- **Capture and trust:** SMS (RECEIVE_SMS only, no READ_SMS, no INTERNET), payment-app and mail notifications, iPhone Shortcuts/share/paste, review inbox where nothing auto-posts, sender allowlist with "treat as my bank", capture health (counts, no message words), "Why isn't capture working?", "Check a message", guided setup, redacted "report a missed text", where-this-came-from on every entry, "Everything stays on this phone" first-run line.
- **People and cards:** lent/borrowed with per-person balance, shares, "I expect this back", refunds, credit-card cycle/limit/due/minimum, "Fix this balance", bills from texts, subscription finder, repeating items, six budget periods including monthly start day 1-28 and financial year, travel mode, goals, one net-worth figure.
- **Privacy surfaces:** Hide amounts (masks widgets, FLAG_SECURE), app lock, encrypted backup, widget eye and masked-widget reasons.
- **Not in our app [code]:** any Hindi/regional/Hinglish text (English only), voice entry, festival theme or festival budget, per-entry hide, assets (gold/PPF/chit/FD), tax tags, reminder messages to other people, UPI `upi://` link, category/merchant icons on rows (the tile is the category's first letter; `HOME_OPTIONS.md` Q11 left unchanged), recap screen (notification only), iPhone lock-screen widgets, bundled Devanagari font.

**Corrections to earlier docs found in this pass**
1. **Mera Kharcha and Trakio are not on-device.** Mera Kharcha never reads SMS itself; a separate "Mera SMS" app must be the default SMS app, and "eligible payment messages go to our server" after "Agree and turn on" [V https://merakharcha.in/auto-tracking]. Trakio sends "only the parsed transaction fields" to its servers after you confirm and requires an account [V https://www.trakio.co.in/features/sms-auto-detection]. `COMPETITOR_ANALYSIS.md` section 5.1 filed both in the "indie on-device" segment; only PennyWise (open source, no data collected) and BachatPe ("No internet permission ... Ever", 100+ installs) are genuinely on-device among those found [V https://play.google.com/store/apps/details?id=com.pennywiseai.tracker&hl=en_IN ; https://play.google.com/store/apps/details?id=com.tingleworks.bachatpe&hl=en_IN].
2. **Moneyview's Play listing no longer mentions expense tracking** (loans, UPI, gold; 4.8 stars, 4.32M reviews, updated 12 Nov 2026) and its Money Manager's SMS goes to Moneyview's servers: "We collect, transmit and store transactional SMS data from Your phone on Our secured moneyview server" [V https://play.google.com/store/apps/details?gl=IN&id=com.whizdm.moneyview.loans ; https://moneyview.in/privacy-policy-loans]. Its help FAQ still documents the tracker dashboard [V https://moneyview.in/money-view-app-faq]. ET Money's listing is also investing-only [V https://play.google.com/store/apps/details?id=com.smartspends&hl=en_IN].
3. **PhonePe "Watch spends"/Spends tracker not found** on any PhonePe primary page; the Medium/NextLeap pieces are concept redesigns, a Jan 2026 third-party guide says PhonePe "doesn't show monthly totals" [S https://moneytools.in/blog/how-to-see-monthly-expenses-in-phonepe]. This supports `COMPETITOR_ANALYSIS.md` [U10] as "treat as absent".
4. **New in the UPI apps:** Google Pay opt-in Gemini "Ask Google Pay" (29 Jul 2026, limited rollout, 10 Indian languages, pie chart by category) [V https://blog.google/intl/en-in/products/explore-communicate/introducing-ask-google-pay-and-expanding-everyday-credit ; https://support.google.com/pay/india/answer/17034014]; Paytm's Nov 2025 redesign with monthly spend summary, Hide Payments, Payment Search [S https://www.electronicpaymentsinternational.com/news/paytm-redesigned-app/]; BHIM 3.0 (Mar 2025) with "Spends analytics", Family Mode, 15+ languages [S https://www.business-standard.com/amp/finance/personal-finance/bhim-3-0-track-manage-split-expenses-with-support-in-15-languages-125032600331_1.html].
5. **axio ownership:** one secondary source says Amazon completed acquiring axio in Sep 2025 [S https://spenco.ai/blogs/walnut-app-what-happened-alternative]; earlier docs say CapFloat. Not confirmed on a primary source; do not cite either as current.

---

## 2. Top 15 lessons, ranked by impact on us

Impact = my judgement of effect on "people want to open it daily" and on SMS-permission trust, given what we already ship. Each lesson names its evidence and whether the follow-up is NEW or a REFINEMENT.

| # | Lesson | Evidence | Follow-up |
|---|---|---|---|
| 1 | **Trust comes from architecture a sceptic can verify, plus honest limits, not from badges.** Indian users learned "finance app + SMS/contacts" from the 2020-23 loan-app scandals (Google pulled 2,000+ loan apps in 2022) and from Walnut becoming a lending funnel. The OS dialog is identical for a bank-only reader and an everything-reader, so trust must be built around it. Only "no INTERNET permission" is checkable by the user. | "an app that only ever parses bank alerts and an app that reads everything request identical permission" [S https://tldrmoney.in/blog/is-it-safe-to-give-sms-permission-to-expense-apps]; TechCrunch scandal figures [S https://techcrunch.com/2022/08/26/loan-apps-abuse-harassment-suicide-indian-users-google-apple-india/]; top FinArt review, 137 helpful, fears OTP access [R `india.md`]; BachatPe "No internet permission... Ever", PennyWise open source [V]. Moneyview shows certificates (ISO 27001, PCI DSS) not on-device proof [V https://moneyview.in/security-money-view-app]. | REFINEMENT (we have the architecture and first-run line); NEW: in-app "Permissions we do not have" proof page and "airplane-mode test" (ideas 1, 2) |
| 2 | **Capture that silently stops is the unforgivable failure; a visible way to check and a manual fix is tolerated.** axio's newest reviews: "Sms are not picked up and have to do manual entry even when SMS is already there" with the ask "add a button to ask the app to check recent messages" (2 Sep 2026); "SMS, UPI scanning is broken... duplicated" (24 Jul 2026). Optimising battery stops SMS reading on many phones. | [R, V https://play.google.com/store/apps/details?id=com.daamitt.walnut.app&hl=en_IN]. dontkillmyapp ranks Xiaomi #2, OnePlus #3, Samsung #4, Oppo #8, Vivo #11, realme #12 for killing background work [V https://dontkillmyapp.com/]. **We cannot rescan an inbox: RECEIVE_SMS only, by design [code]. So our equivalent must be a heartbeat ("last bank text seen") and a catch-up via paste/import.** | REFINEMENT of Slice 107 and capture health; idea 3 |
| 3 | **Anything that smells like selling is the top complaint of every Indian finance app; restraint is itself the praise.** Ads/loan banners/pop-ups: PhonePe, Paytm/MobiKwik ("Contains ads"), Khatabook ("constantly spams loan options, but always says 'not eligible'"), IDFC First ("scammy pop up... it's about how much you value our trust"), axio ("big banners/ads for pay-later"). GPay's praise is "clean interface without promotional interruptions". Fold states the business model in the FAQ ("makes money from Fold Plus subscriptions, not from selling... you will never get spam or loan calls"). | [R/S] Khatabook Play 3 Nov 2026 [V https://play.google.com/store/apps/details?id=com.vaibhavkalpe.android.khatabook&hl=en_IN]; IDFC Play 10 Sep 2026 [V https://play.google.com/store/apps/details?id=com.idfcfirstbank.optimus&hl=en_IN]; Remitbee comparison [S https://www.remitbee.com/blog/money-transfer/remittance/best-digital-wallets-in-india]; Fold FAQ [V https://fold.money/faq]. | REFINEMENT (one-nudge rule already); NEW: one on-screen "How this app is paid for" line (idea 4) |
| 4 | **Hindi/Hinglish is open ground in trackers, and Indian users read English digits in a Hindi UI.** 98% of Indian internet users use Indic languages (alone or with English), 57% of urban users prefer them [V https://www.iamai.in/sites/default/files/research/Kantar_%20IAMAI%20report_2024_.pdf]. Google/Kantar playbook: keep Latin digits ("We are not used to reading numbers in Hindi. English is fine"), colloquial not literary words, translate errors and permission text, accept voice and transliterated typing [V https://services.google.com/fh/files/misc/app-website-localization-indian-users.pdf]. 8 of 9 checked iOS listings for wealth/card apps are English-only [V, `india-ui-c`]; Khatabook/OkCredit ship 11 languages incl. Hinglish [V https://okcredit.in/en/]; GPay added Hinglish in Jun 2022 [S https://inc42.com/buzz/google-pay-adds-hinglish-language-to-attract-indian-millennials-2]. | Hindi UI is prior item 6 (L). **NEW:** Hinglish as a first-class language, Latin-digits rule, glossary and localised permission primer (ideas 5, 9, 10, 11) |
| 5 | **Home must answer "where am I this month, and what can I spend today?" in one glance.** The clearest single-number heroes found: Spenrol "Rs13,980 remaining of Rs20,000... Rs822 left for today", BachatPe "On Track / Watch / Over", Jupiter reviewer wants "days remaining to salary", FinArt reviewer wants the current-month pie first, not an all-months graph. | [V] https://www.spenrol.com/expense-tracker-without-sms-access ; [V] https://play.google.com/store/apps/details?id=com.tingleworks.bachatpe&hl=en_IN ; [R] Jupiter iOS 12 Oct 2025 [V https://apps.apple.com/in/app/jupiter-cards-upi-banking/id1507748747] ; [R] FinArt Play 3 Nov 2026. | REFINEMENT (Home B has month + budget + safe-to-spend); ideas 14, 19 |
| 6 | **For salaried India the real months are the salary cycle, the card statement cycle and the EMI dates; the calendar month fits none.** RBI lets cardholders change billing cycle; Kotak/Jupiter advise a due date 5-10 days after salary. We ship monthly start day 1-28 and card cycles; "last working day" and cross-checks are missing. No first-party Indian user quote on salary-cycle demand was found. | [S\*] https://www.business-standard.com/amp/finance/personal-finance/change-your-credit-card-billing-cycle-to-manage-cash-flow-here-s-how-124032700290_1.html ; [S\*] https://www.kotak.bank.in/en/stories-in-focus/cards/credit-cards/change-credit-card-billing-cycle-cash-flow.html ; FinArt iOS reviewer asks for custom billing-cycle view [R https://apps.apple.com/in/app/finart-ai-expense-tracker/id6748001842]. | REFINEMENT; ideas 14-18 |
| 7 | **Money between people runs on the khata mental model: "You gave / You got", one page per person, a running balance that turns to "Settled", reminders signed and polite.** Khatabook and OkCredit reached 10Cr+/1Cr+ shopkeepers on it with 11 languages; OkCredit's benchmark is "If you can use WhatsApp, you will also be able to use OkCredit". The reminder is the killer feature (WhatsApp/SMS/UPI link, language per customer). Every personal-udhaar app is cloud or ad-funded; Klera pitches offline/no account but is vendor-claimed. | [V] https://khatabook.com/blog/khatabook-app-features/ ; [V] https://okcredit.in/en/ ; reminders [V https://khatabook.com/help/en-us/category/Y-tMaxAAAB0AwKXn] ; Klera [V, vendor claims https://kleraapp.com/udhaar-tracker-app/]. | REFINEMENT of Lent/borrowed; NEW: on-device reminder composer + UPI intent (ideas 27-29) |
| 8 | **Insight tone: monthly totals read as guilt; neutral, specific, confirm/dismiss wording works; titles stay short.** A small NextLeap GPay study (n=50 survey, 6 interviews) says monthly totals "trigger regret and guilt, not clarity"; a CRED interviewee: "I don't anyone telling me I don't have money in my bank! That would really stress me out." PhonePe's voice rules: tone mapped to consequence x actionability ("direct plus reassuring" for a failed payment), titles "always 5 words or less", art tone must match copy. Zerodha lists "pointless gamification and casino-like features" and manipulative notifications as dark patterns it avoids. | [S/R, small n] https://assets.nextleap.app/submissions/NL_googlepay-91c2bf66-272d-46f7-af26-945ba8c14747.pdf ; [S] https://medium.com/@kashwiaggarwal/enhancing-creds-user-engagement-bcee6cdf04a0 ; [V] https://design.phonepe.com/stories/phonepe-voice ; [V] https://zerodha.com/z-connect/updates/dark-patterns-in-finance-apps. | REFINEMENT (HOME_NUDGES tone, weekly recap); idea 30 |
| 9 | **Delight must never cost speed.** CRED's own teardown: "slight lag after clicks" especially with haptics; reviewers: "heavily focused on animations & videos rather than speedy & smooth UI/UX... memory-intensive" (CRED, Oct 2026) and "App animations are still jittery" (BHIM, Sep 2026). Google's Go guidance asks to reduce "animations and transitions" on low-RAM devices; ~57% of India's 2025 smartphone shipments were under $200 and 2-3 GB RAM phones are returning. CRED does prove delight sells: "A lot of people told us they joined CRED because they liked the animations". | [S] https://uxplanet.org/thoughts-on-creds-ui-revamp-apr-2022-6d2b4dcfcfc6 ; [R] `india.md` CRED Oct 2026 ; [R, V] BHIM Play 8 Sep 2026 https://play.google.com/store/apps/details?id=in.org.npci.upiapp&hl=en_IN ; [V] https://developer.android.com/docs/quality-guidelines/build-for-billions/device-capacity ; [V] IDC https://www.idc.com/resource-center/press-releases/india-smartphone-market-2025-2026 ; [V] https://lottiefiles.com/case-studies/cred. | REFINEMENT of Slice 67 (add low-RAM degrade, measure on a cheap phone); ideas 31, 32 |
| 10 | **Category and merchant identity: show icon + stable colour and a clean name, never a bare initial or dot; let people rename a UPI handle once.** Walnut's most-praised behaviour: "instead of paytm@xytrq i can name it as MyGrocer". PennyWise reviewer: "The categories should have icons. Like, currently there are just small dots." A Fold reviewer asks for merchant logos. Our row tile is the category's first letter (Food and Fun both "F"). Bank spend analysers misfile kirana/street QR payments into "Others" (vendor data). | [R, V] axio Play 22 Feb 2022 ; PennyWise Play 24 Sep 2026 ; Fold iOS 20 Mar ; [S\*] https://www.tapix.io/resources/post/why-mcc-codes-do-not-help-much-with-payment-categorization. `HOME_OPTIONS.md` finding 9. | REFINEMENT; ideas 34, 35 |
| 11 | **A festival layer should reward understanding, not transactions.** GPay's festival games (stamps 2019, Festival City 2023) ran on maps, shagun counters and capped, expiring scratch cards; reviewers call rewards "just for show", Slice users find the repeated game "frustrating". Jupiter's Diwali Quest and celebration animation was welcomed as seasonal delight. The spend spike is real: Dhanteras 2025 saw 754M UPI payments worth ₹1.02 lakh crore in a day; CAIT put Diwali 2025 sales at ₹6.05 lakh crore. | [V] https://support.google.com/pay/india/answer/14147019?hl=en ; [S] https://medium.productcoalition.com/dissecting-a-gamification-that-went-viral-google-pay-stamps-c9c58b339359 ; [V] Jupiter iOS release notes ; [S] https://www.thetechbag.com/blog/upi-october-record-1-lakh-crore-a-day ; [S\*] CAIT https://www.thehindubusinessline.com/economy/diwali-spending-peaks-with-540-lakh-crore-in-goods-65000-crore-in-services-sales-cait/article70185831.ece. | **NEW** (ideas 21-23) |
| 12 | **Number craft is a quiet differentiator (and ours is already careful).** CLDR en-IN: only the `standard` currency pattern gets 2-digit grouping; `accounting` and no-currency variants silently give Western 3-3-3. Compact: CLDR blesses `K/L/Cr`; Google's playbook warns "avoid abbreviation of the numbers e.g. ₹15k" in running text. Public bugs show `Rs100 lakh` just below ₹1 crore and duplicated commas past 1,000 crore. The store itself trains users on "25.6L", "10Cr+"; **no in-app competitor hero using 1.2L/3.4Cr was found**. | [V] https://raw.githubusercontent.com/unicode-org/cldr-json/main/cldr-json/cldr-numbers-full/main/en-IN/numbers.json ; [V] Google playbook URL above ; [S] https://github.com/theadiram/Hisaab/issues/5 ; [V] Play listings. | REFINEMENT (we truncate, so no `100L` bug; keep full grouped figures in heroes, compact only in tight spots; add tabular figures, ideas 12, 13) |
| 13 | **Cash and UPI-Lite are the holes in SMS capture; say so and make the manual path two taps.** Cash in circulation ≈₹42.8 trillion (15 May 2026), +11.5% YoY; cash/GDP 11.1% FY25 [S https://www.business-standard.com/india-news/india-s-upi-boom-cash-in-circulation-digital-payments-cash-to-gdp-ratio-rbi-npcl-126060200752_1.html]. UPI Lite payments up to ₹1,000 often carry no bank SMS [I; behaviour not verified on a primary source]. Mera Kharcha's honest copy ("Will every payment appear? No." / "It is not a live bank connection.") is the tone to copy [V https://merakharcha.in/auto-tracking]. | | REFINEMENT; ideas 19, 20 |
| 14 | **Indians expect a "net worth" that includes gold, EPF/PPF, FD/RD, SIP, chit, property.** INDmoney's model counts physical/digital gold, SGB, real estate (manual), NPS, EPFO, ESOPs; Fold added "Custom Entries" for manual assets on 4 Nov 2026; reviewers ask for land, vehicle and physical gold entries. Households hold ~30,000-35,000 tonnes of gold (NISM director, snippet). We show one figure on Accounts, no assets. Users track these as balance + maturity date + next instalment, not live prices [I]. | [V] https://www.indmoney.com/networth ; [V] https://apps.apple.com/in/app/fold-expense-tracker-budget/id1626994334 ; [S\*] https://www.moneycontrol.com/news/business/markets/india-s-rs-950-lakh-cr-worth-holdings-in-gold-real-estate-shows-challenge-of-shifting-savings-to-markets-nism-director-13852840.html. | NEW (ideas 25, 26); prior item 11 asks for history only |
| 15 | **Privacy from the people holding the phone next to you matters as much as privacy from servers.** Paytm "Hide Payments" (swipe left, view after PIN) is pitched for family phones and surprise gifts; Vyapar users ask to hide purchase rates from staff; Klera masks widget amounts while app lock is on; NN/g has an "App Lockers for Shared Phone Use in India" article (title only seen). Shared-device internet use was 20% in 2024 (Kantar-IAMAI). | [S] https://www.ciol.com/news/paytm-rolls-out-new-hide-payments-feature-for-upi-users-10785456 ; [R] Vyapar Play 28 Aug 2026 ; [V] Klera ; [V] Kantar-IAMAI above. | REFINEMENT (Hide amounts, lock, masked widgets exist); NEW per-entry hide (idea 33) |

---

## 3. Per-app teardown table

Columns: what home/summary looks like (text-derived, not seen); how auto-capture is presented and trust messaging; categories/logos; language; festival/widgets/accessibility; what Indian users praise and complain about. "NF" = not found. Ratings are Play (en_IN) as read 2026-11-13 unless stated.

### 3.1 UPI and payments super-apps

| App | Home / monthly summary | Auto-capture + trust messaging | Categories / logos | Language | Festival / widgets / a11y | Praise / complaints (voices) |
|---|---|---|---|---|---|---|
| **CRED** (4.7, 30L reviews) | Charcoal home (Nov 2023): "finance suite" + "offer oasis", "My Money" section, payment actions moved to bottom, Scan & Pay one swipe [S https://analyticsindiamag.com/cred-gets-a-new-design-upgrade-with-charcoal/]. NeoPOP: dark-first, sharp corners, serif headings, 3D icons, lit active tab [S UX Planet]. CRED Money (Jul 2024, AA) tab UI NF on a primary source. | Play copy: "Every credit information is encrypted, monitored and protected"; Data safety may share personal+financial info [V]. No SMS rationale on listing. A user: "why sacrifice our privacy of sharing mail" (iOS 19 Nov 2025). | NF | English only on iOS [V] | 79 catalogued interactions (swipe-to-pay, ticket print, coin flip, jackpot spin) [S 60fps.design]; widgets, festival, a11y NF | Praise UI ("looks good and sounds good"); complaints: clutter, "casino" rewards, lag, bot-only support, "faceless company" |
| **Google Pay** (iOS-IN 4.6, 1.9M) | Clean, whitespace, numeric-only amount input [S Medium UX comparison]; Money tab; history shows only GPay txns with This month/30/90-day filters [V https://support.google.com/pay/india/answer/7430307?hl=en-IN]; Ask Google Pay (Gemini, pie by category) opt-in, limited rollout | Personalisation OFF by default, chat auto-deleted after 42 days, "no training on your data", payment data stored in India [V https://support.google.com/pay/india/answer/17034014] | NF | "EN + 13 More" [V]; Hinglish since 2022 [S] | Festival City (Diwali 2023) map + shagun + scratch cards [V]; Rewind 2020 swipeable stories with badges [S] | Praise: no loan/insurance upsell vs PhonePe [S]; complaints: account suspensions, support, no custom date range |
| **PhonePe** (4.6, 1.44Cr, "Contains ads") | 3.0 redesign (early 2025): grouped layouts, frequent actions kept at top, QR scanner moved to a larger footer in the thumb zone, users made it a widget [V https://design.phonepe.com/stories/behind-phonepe-redesign]; in-app spend tracker NOT found | Trust = reliability and familiarity, not privacy [V]; listing carries a loan block | NF | 11 languages [S\*] | Scan widget; festival NF | Praise: fast, reliable; complaints: "so many ads and promotional banner right at the top" [S\* G2], loan pop-ups |
| **Paytm** (4.8, 2.46Cr, ads) | Nov 2025 redesign: UPI-first grouping, total balance across linked accounts, monthly spend summary with auto-categories, Payment Search, Excel/PDF statement [S] | Hide Payments (swipe left, PIN/biometric to view) [S]; "Only you control your transaction visibility" [V] | Auto-categories (shopping, bills, travel, utilities) [S]; layout NF | Hindi + ~11 regional [S\*] | Receive-money widget with coin sound [V]; Playback (AI rap of your month, Oct 2025 beta) [S] | One review wants the transfer note shown in the history row (249 helpful, 17 Sep 2026) [R] |
| **BHIM** (3.9, 19.3L) | BHIM 3.0: spends analytics dashboard, Family Mode, Action-needed assistant [S/V] | Data safety discloses more than Paytm/PhonePe (may share personal info) [V] | NF | 15+ (press), "12+" (store) | Light and dark mode [V] | "App animations are still jittery and not smooth... larger digits when typing an amount" (8 Sep 2026) [R] |
| **MobiKwik** (4.4, 3.6M, ads) | Leads with loans; LENS AA spend tracker (Sep 2023) [S]; layout NF | Data safety may share activity, device IDs [V] | NF | NF | Scratch cards capped per day [S] | "payment failures... don't show the failed transaction in the history" (1,502 helpful) [R] |
| **Amazon Pay UPI** | Lives in the shopping app; "cluttered, too many videos and ad banners" [S\*] | "safe, secure and supported by govt." [V] | NF | NF | NF | Praised for instant refunds [S] |

### 3.2 SMS and expense trackers (India-focused)

| App | Home / monthly summary | Auto-capture + trust messaging | Categories / logos | Language | Festival / widgets / a11y | Praise / complaints (voices) |
|---|---|---|---|---|---|---|
| **axio** (3.8, 2.67L) | Current layout not found; 2014 legacy: Overview/Bills/Stats/Map tabs [S]; agency audit: inconsistent screens, unclear legends, back/next nav confusion [S https://www.yellowslice.in/blog/axio-app-review-by-yellow-slice-how-to-make-it-better] | Listing: "It does not read your personal SMS or upload any sensitive data", then three SMS purposes incl. "Personal Loan: Fraud prevention" and "SIM-device binding"; Data safety shares name/location for Analytics [V] | Name billers once ("MyGrocer"), tags, receipts [V/R] | English | NF | Praise 2022 "neatly collects expenses from all sources"; Jul/Sep 2026: SMS not picked up, duplicates, app won't open; older: battery, ads (via snippets) |
| **Moneyview** (4.8, 4.32M, loans) | Money Manager dashboard: Bank Balance tile, Set Budget, swipeable graph, add cash txn [V FAQ] | "algorithms are incapable of decoding your personal or OTP messages"; ISO 27001/PCI badges; Truecaller verified calls; SMS stored on its server and used "to help you in getting a loan" [V] | NF | 7 languages (snippet), Hinglish ads | NF | Loan harassment, "1,000 calls", cannot delete data [R] |
| **ET Money** (4.4, 2.34L) | Investing-only listing; tracker NF | Old FAQ: "No, this is not dependent on the internet" (snippet) | NF | 8 languages (competitor claim) | NF | "your new ui is so irritating" (23 Aug 2026) [R] |
| **Bajaj Finserv/Finance** (4.7, 78L, ads) | Loans/UPI/EMI; no expense auto-capture claimed | NF | NF | NF | NF | "the app mainly shows the EMI amount instead of... the full product price" [R] |
| **FinArt** (4.3, 6.2K) | Line/pie/bar, income vs expense line, cash-in-hand, custom month start day | Verbatim: "SMS permission is optional and required only if you want to use AI-powered features..." + Private Mode + own Drive backup [V]; label mismatch (collects SMS for Analytics, "Data can't be deleted") | 18 predefined + unlimited custom with icon and colour; EMI/SIP default to "Others" [V] | iOS lists Hindi, Gujarati, Kannada, Malayalam, Tamil, Telugu [V] | NF | Praise: WhatsApp support; asks: current-month pie first, account view; "UI is too outdated" |
| **Mera Kharcha / Mera SMS** | Mock: "Good morning, Aarav", Day/Week/Month/Year tabs, "You spent Rs12,450... 12% less than last month", "Where it went" list [V marketing mock] | Separate default-SMS app; "Off by default... Agree and turn on"; SMS **sent to server**; "It is not anonymized before upload"; "Will every payment appear? No." [V] | NF | 29 languages; Hinglish voice "Auto ke Rs80 diye" (Premium + internet) [V] | Home widget [V] | Reviews NF (Play 404) |
| **Trakio** | Pending Review (Confirm/Edit/Dismiss); "Rescan SMS history" | "known bank sender IDs... Raw SMS text never leaves your phone. Only the parsed transaction fields are sent" [V]; account required | NF | 18 UI languages, SMS in 10 [V] | NF | Reviews NF |
| **PennyWise AI** (3.8, open source) | Categories shown as "small dots" [R] | Verbatim: "Reading bank SMS is how the app works... Everything happens on your device... source code is public (AGPL-3.0)... Would rather not grant it? You can still add transactions by hand" [V]; Data safety no data collected | 157 banks; dots without icons [R] | NF | NF | "It's not reading all messages" (6 Oct 2026) [R] |
| **BachatPe** (100+ installs) | "On Track / Watch / Over", safe-to-spend, spend-free-day calendar, 30/7/3-day bill reminders | "No internet permission... Ever", OTPs ignored, "Deny SMS permission and still use the app" [V] | 750+ Indian merchants | NF | NF | Too small for reviews |
| **Spenrol / SpendCtrl / Expanager** | Spenrol "Rs822 left for today", Required/Optional/Impulsive swipe cards, 8-second Swiggy/Zomato nudge; Expanager "Transaction Helpers" with Add button, Hindi + Malayalam among 21 languages | SpendCtrl guide: "Reading a message and keeping or sending a message are two very different things" [V/S]; Expanager Data safety "No data collected" [V] | Spenrol tags | Expanager 21 languages | NF | Vendor-written; reviews NF |
| **Expense Manager (Bishinews)** (4.3, 63K, ads) | Calendar with forecast "HOW MUCH MONEY I'LL HAVE ON ANY GIVEN DAY" [R] | "If you don't use this feature, go to phone Settings... switch off SMS permission" [V] | NF | Hindi among 12 | Widgets, PIN [V] | Dated/dense UI [I] |

### 3.3 Wealth, neobank, card apps

| App | Home / monthly summary | Auto-capture + trust messaging | Categories / logos | Language | Festival / widgets / a11y | Praise / complaints (voices) |
|---|---|---|---|---|---|---|
| **INDmoney** (4.7, 4.65L) | Net-worth-first dashboard; XIRR vs benchmark, projections ("Projections are indicative, not guaranteed"), Personal/Family toggle [V https://www.indmoney.com/networth] | Gmail ask with no priming flagged as a dark pattern [S GrowthX teardown]; "Does tracking an external account move my money? No." [V] | Manual entries for property/gold/ESOP [V] | English (iOS) | NF | "gives true picture of net worth" [R]; "feel like selling" [S], scripted support |
| **Jupiter** (iOS 4.7, Play 4.3) | Money tab: Networth / Spending (swipe left for earlier months) / tips; 2.0 (Apr 2024) light white + beige, "key numbers are in the spotlight" [V https://community.jupiter.money/t/jupiter-2-0-redesign-introducing-a-new-experience/51018] | Store lists each permission with its reason ("Detect suspicious UPI activity (required by NPCI)") [V] | Pots auto-route by merchant category [V] | English; key-fact sheets in more languages | Diwali Quest + celebration animation [V]; screen-reader onboarding fix Jul 2026 [V] | "People will go blind with this color scheme" and "need dark mode" [R 2024]; loan spam, support; warm release-note voice |
| **Fi Money** (shut Mar 2026) | Spend Analyzer by category/merchant/time; Gmail smart search; "money plant" rewards [V/S] | NF | NF | NF | Seasonal FIT rules [S] | Beloved design, died on economics [I] |
| **Fold** (Play 3.1 / iOS 4.5) | Widget stack: Net worth, Cash Flow, credit cards, recurring; Spending Summary widget retired 12 Nov 2026 [V] | "No password sharing, no SMS scraping"; read-only; Spaces totals-only [V] | Merchant-logo request [R] | NF | Home widgets on both OSs [V] | "Super smooth onboarding... within 5 minutes" [R]; AA outages |
| **Zerodha Kite + Coin** (4.6) | Invested/current/P&L triple on top, swipe Holdings/Positions, Day's P&L pinned [V https://kite.trade/docs/kite/kite-mobile/portfolio/] | "No gimmicks, spam, or annoying push notifications" [V] | NF | English; users write Hinglish [R] | Dark mode since 2019 [V]; 45.7 MB vs 170-200 MB peers [V] | "doesn't crash, lag, or freeze" [R]; counter-voice "Too much simplicity" [R] |
| **Groww** (Play 4.9, 25.6L) | Clean, beginner-friendly; native charts with back-to-latest arrow and dynamic decimals on zoom [S designer case study, self-reported] | NF | NF | English | NF | "made investing feel less intimidating" [R]; re-KYC lockouts |
| **Kuvera** (4.2) | Pre-CRED dense and clear; post-CRED backlash | NF | NF | English | NF | "Nobody cares about the fancy design aesthetics" (Apr) [R]; wants an "All investments vs Kuvera-only" toggle |
| **Slice** (4.4, 9.25L) | Bento Explore grid, 3D onboarding, "Play and Earn" game [S] | NF | NF | NF | Dark mode arrived by Jul 2026 [R] | Game fatigue: "repeated requirement to play the Fire Game... frustrating" (5 Nov 2026) [R] |
| **Uni / OneCard** | Uni: gold in grams and ₹; OneCard: Spends / Categories / Merchants lenses, "cleanest... app" [R] | NF | OneCard category/merchant lenses [V] | English | NF | Uni: cannot pay custom amount before bill generated [R] |
| **Kotak 811** (4.8) | One-view Assets vs Liabilities; "soft 3D visuals" [V/S] | Old app: SMS for "device activation and optional data access (with consent)" [V] | NF | NF | NF | "give us an option to maintain old ui" (2 Nov 2026) [R] |
| **Jar** (4.1, 20.8L) | Gullak metaphor, round-up to gold, grams shown | 2-3% buy-sell spread disclosed [V] | n/a | EN + 7 more [V] | Animating "save now" +30% completions (vendor-reported) [V Plotline] | Users still feel spread is hidden [R] |

### 3.4 Bank apps

| App | Home / spend view | Trust / language / voices |
|---|---|---|
| **HDFC Bank (new app)** (4.6, 14.1L) | Clean/minimal, day/night themes, "playful dashboard with account insights", scanner on login [S twimbit]; PayZapp Passbook with spend analysis | "No data shared"; fraud-awareness banners ("Vigil Aunty") [V https://www.hdfc.bank.in/]; "app is not responding", "hire a better UI/UX team" [R Oct 2026] |
| **ICICI iMobile** (4.7, 66.7L) | Discover/Budget/iFinance; new simpler home (4 Nov 2026) | Languages via a **separate** "Mera iMobile" app [S]; "extreme latency" [R] |
| **SBI YONO / Lite** (4.0 / 3.3) | YONO 2.0 (Dec 2025), Lite supports 13 languages, SBI Voice Assist [V/S]; no spend analyser found | Lite registration needs the user to send an encrypted SMS manually; "server not available" [R] |
| **Axis open** (4.6, 35.6L) | My Money: Net worth > Investments > Spends > Credit score > Protection; auto-categorised spend trend [V https://www.axis.bank.in/blogs/financial-planning/my-money-feature-by-axis-bank] | "Navigation... is confusing" (2022) [R]; language count conflicts (4 vs 11) |
| **Kotak (new) / 811** | New app "cleaner, sleeker" | Complaints: ads, forced new UI, sluggish [R 2 Nov 2026] |
| **IDFC First** (4.8, 29.9L) | "over 20 automatically categorized transaction types", FIRST.AI "Food spends last month?" [V] | "Doesn't open when the developer option is enabled" [R]; pop-up cross-sell = "low integrity" [R] |
| **Spend-analyser criticism** | Card-bill paid via CRED/Paytm misread as a refund, 2-3 day lag, wallet gateways bucketed "wallet" [S 2021 cardexpert]; only ~63% MCC accuracy (vendor) [S\*] | Opening for correctable categories and never hiding uncategorised money |

### 3.5 Ledgers, splitters, family, informal money

| App | UI facts | Trust / language / voices |
|---|---|---|
| **Khatabook** (4.4, 5.9L, 10Cr+) | Customer page of "a manual accounting book"; "You Gave / You Got" (red/green per one editorial source [S\*]); reminder via PDF/UPI/WhatsApp/SMS, bulk reminders; Hinglish language option [V] | 11 languages; privacy policy says contacts uploaded, installed-apps access, SMS for credit products [V https://khatabook.com/en/privacy/]; "messy interface", loan spam [R] |
| **OkCredit** (4.4, 4.3L) | Thread-style ledger with ticks, "Due" turning to "Settled", arrows + words not colour alone, Due Date / Call / Remind [V marketing mock]; uses उधार, जमा, बही खाता | 11 languages; ads and ₹99/month paywall over a user's own data draw the angriest reviews [R]; Kaccha Note: voice-first Hindi, only 28% of openers added an entry; "Their definition of simplicity includes trust and control" [S] |
| **Vyapar** (4.9) | Billing-first, offline, 9+ languages | Users want PIN/hide purchase rates from staff [R] |
| **Splitwise / Splid / Splitkaro** | Splid "No sign-up, offline, clean"; Splitkaro "Free means free", item-wise, imports Zepto/Zomato/Blinkit bills [V] | No UPI hand-off in Splitwise; a dev says third-party UPI payments cannot be verified [R https://www.desidime.com/discussions/splitwise-alternatives] |
| **Fold Spaces / FinArt family** | Totals only, per-item opt-in, "numbers leave with" the person [V] | Joint accounts unsupported (AA) |
| **Klera / Udhaari / Kameti apps** | Klera: "Three taps, no forms", friend-voice reminder, UPI settle link, masked widget [V vendor]; Kameti: members x cycles x draw/payout, <5 MB, Hindi+English [V] | "tool only, never moves money" is the norm; reviews NF |

### 3.6 Global trackers used in India

| App | Why Indians pick it / complaint |
|---|---|
| **Money Manager (Realbyte)** (4.6, 4.69L) | Manual, trusted old brand, Quick-Add widgets (Jul 2026), ads after paying [R]. No UPI/lakh complaint found. |
| **Cashew** (4.8, 28K) | Free, on-device, themes, widgets; custom number format shipped in 5.2.6 but lakh grouping not verified [V]. |
| **Wallet / Monefy / Spendee** | No India bank sync; Monefy "simple and aesthetically appealing", dark mode paywalled; Spendee redesign slowness [R]. No review-level UPI/lakh/INR complaint was found for any of them (searched). |

---

## 4. Where we stand (from `OUR_FEATURES.md`, [code])

**Ahead**
- Verifiable privacy stack: no INTERNET, RECEIVE_SMS only, no account, encrypted DB on Android, nothing auto-posts, redacted report. Only BachatPe (100+ installs) and PennyWise (3.8 stars, 187 reviews) approach it; every scaled Indian competitor sends data somewhere.
- Capture correctness UI that Indian apps lack: pairing of bank text + payment-app alert, own-account transfer cards, card-payment transfers, "Why isn't capture working?", "Check a message", confidence tiers (SURE/CHECK) that never auto-add a guess.
- Number craft: truncating compact figures, amount-in-words in lakh/crore, whole rupees only on headlines. No competitor in-app lakh/Cr shorthand found.
- Motion system with a user-controlled speed and OS override (Slice 67); no Indian finance app with such a setting was found (not searched exhaustively).
- Budget periods incl. salary day/fortnight/financial year; cards with cycle, limit, due, minimum; "I expect this back"; safe-to-spend from budget and from balances.
- Calm home with one nudge and no ads, loans or promotions (the exact thing reviewers beg for).

**Equal**
- Bill and card-due reminders (axio, CRED, FinArt all lead with them). Quick-add widgets (Realbyte, Monefy, Mera Kharcha). Review-before-add (Trakio, Mera Kharcha). Dark theme (Kite, Slice, BHIM). Widget with masked amounts (Klera).

**Behind**
- **Language:** English only against Khatabook/OkCredit (11), PhonePe (11), GPay (14), BHIM (15+), Mera Kharcha (29), FinArt iOS (10). No Hinglish, no voice.
- **Category/merchant identity on rows:** a letter tile where PennyWise/Monefy users already complain about dots; no merchant marks.
- **Assets and net worth:** one figure; INDmoney/Fold cover gold, EPF/NPS, property, ESOP and manual entries.
- **Festival and tax calendar layer:** none (GPay/Jupiter have festival moments; no tracker found has tax-tagged spend, but MoonProduct lists the wish).
- **Reminders to other people and UPI settle:** Khatabook/OkCredit/Klera make this the core loop; ours stops at recording a settlement.
- **Recap/Wrapped surface:** GPay Rewind (2021), Paytm Playback (2025); ours is a notification only.
- **Low-RAM adaptation:** Motion is a manual setting; no automatic degrade.
- **Real-device proof:** no real-device performance numbers; this gates every claim about being "far more responsive".

**UI elements Indian users expect that we lack** (each with evidence above): language switch incl. Hinglish; icon + colour category tiles; merchant marks; per-person khata cards with reminders; "days to payday"; cash-in-hand glance; billed vs unbilled on cards; EMI/due calendar; festival envelope; assets (gold/chit/PPF); tax/ITR dates; per-entry hide; recap stories; a visible "last bank text seen" heartbeat; a "no internet permission" proof page.

---

## 5. India-specific UX checklist

Status: ✅ shipped [code] · 🟡 partial · ❌ missing. Source for each is above.

| # | Item | Status | Note |
|---|---|---|---|
| 1 | Full grouped ₹ with en-IN pattern everywhere (`₹1,24,500`); compact only in tight spots | ✅ | Use CLDR `standard` pattern only; accounting/no-currency variants are Western-grouped [V CLDR]. Add a test for those variants if any export uses them. |
| 2 | `K / L / Cr` compact with truncation, boundary tests (99,99,999 → `₹99.9L`) | ✅ | Matches public bugs (`Rs100 lakh`) avoided. Google playbook dislikes `₹15k` in running text: keep compact out of sentences. |
| 3 | Amount in words (lakh/crore) while typing | ✅ | |
| 4 | ₹ glyph present in every bundled font; tabular figures; paise smaller or hidden for `.00` | 🟡 | Roboto/Material today; no tabular-figure or Devanagari-capable bundled font [I from `HOME_OPTIONS.md`]. |
| 5 | Latin digits in a Hindi UI | ❌ | No Hindi yet; rule goes into the language work [V Google playbook]. |
| 6 | Hindi + Hinglish + 3-5 regional languages; localised permission primer and errors | ❌ | Colloquial register, glossary (खर्च, आमदनी, बजट, किस्त), acronyms with meaning `EMI (आसान किस्त)`. |
| 7 | Voice entry in Hindi/Hinglish and transliterated search | ❌ | Voice users 140M, 55% rural [V Kantar-IAMAI]. |
| 8 | UPI-first mental model: "Paid to" label, VPA shown small, rename once, P2P vs merchant | 🟡 | Person-or-business shape and alias exist; no VPA dictionary. |
| 9 | Salary-day month, last-working-day, card statement/due cycles, EMI dates | 🟡 | Start day 1-28 and card cycles exist; no last-working-day, no payday-vs-due advice. |
| 10 | Cash as first-class: cash account, ATM withdrawal to cash, 2-tap cash add, cash-in-hand glance | 🟡 | Cash account and Transfer candidates exist; no Home glance. |
| 11 | UPI Lite and no-SMS gap stated honestly | ❌ | Verify Lite SMS behaviour on a real phone first [I]. |
| 12 | Festival awareness (Diwali, Eid, Onam, Pongal, Durga Puja, Christmas): envelope + last-year comparison, optional accent | ❌ | Only a "Festivals and events" starter category exists. |
| 13 | Tax: 80C/80D/80G/HRA tags (old regime only), advance-tax dates, ITR date | ❌ | New regime is default for FY 2026-27, so show only if opted in [S https://audcrix.com/blog/old-vs-new-tax-regime-2026-27-section-80c]. |
| 14 | Gold, chit/kameti, PPF/EPF/FD/RD/SIP as manual assets with maturity reminders | ❌ | |
| 15 | Low-end phones: ≤40 MB class install, ≤90 MB PSS, cold start <2 s, degrade animation on low RAM | 🟡 | Perf budgets exist; no auto degrade; no cheap-phone numbers. |
| 16 | OEM battery wizard (Xiaomi/Oppo/Vivo/Samsung/realme) with deep links | ✅/🟡 | "Why isn't capture working?" reads battery state and a Samsung checklist; other-OEM lists unverified. |
| 17 | Dark mode follows system; dark surfaces separate by lightness | ✅/🟡 | System/light/dark exist; "dark mode not accessibility-checked" per our own doc. |
| 18 | One-handed: primary actions in the lower 40% | ✅ | Add in the bottom bar; PhonePe made the same move for QR. |
| 19 | Large text without clipping | ✅ | Home B layout rules at 150%/200%. |
| 20 | Shared-phone privacy: hide amounts, app lock, masked widgets, per-entry hide | 🟡 | Per-entry hide missing. |
| 21 | Fraud awareness: static "bank never asks OTP, call 1930" tip; never ask for UPI PIN/password | ❌ | HDFC/Moneyview both run fraud-awareness content [V]. |
| 22 | Shared family finance by totals only, exit leaves no trace | ❌ | Without sync: share a snapshot card/PDF. |
| 23 | Human support path | 🟡 | Feedback opens a mail draft; FinArt WhatsApp support is praised [R]. |
| 24 | Data portability promise (export, backup) stated in-app | ✅ | Fi's and Walnut's changes make this a trust feature. |

---

## 6. Anti-patterns (seen in Indian apps; do not copy)

1. **Tracker as lead funnel:** SMS data feeds loan offers (Moneyview policy), "fraud prevention" as the SMS reason on a tracker (axio), calls after browsing (INDmoney) [V/R].
2. **Blanket SMS justification that bundles unrelated purposes** and a store label that contradicts the copy (axio shares name/location for Analytics; FinArt "Private Mode" next to "Data can't be deleted") [V].
3. **Mandatory SMS on Android, optional on iOS** (KreditBee policy table) [V https://www.kreditbee.in/privacy-policy].
4. **Credential-style badges as the only trust proof** for an app that has a server; badges are irrelevant to an on-device app [I].
5. **Ads over a user's own ledger** and paywall of existing data (OkCredit "force me to pay 99/month to access my data") [R].
6. **EMI-first price display** ("shows the EMI amount instead of the full product price", Bajaj) [R].
7. **Capped, expiring cashback games** (GPay stamps; Slice "Fire Game"; "rewards... just for show") [R/S].
8. **Redesign that removes dense at-a-glance data** (Kuvera, Jupiter Pots: "please do not break the ones that were already working"), or forces context mixing without a scope toggle [R].
9. **Forcing a new UI with no way back** (Kotak: "give us an option to maintain old ui") [R]. Our roadmap lists a classic-layout toggle (FR-SET-04).
10. **Same balance, two colour meanings** (a Khata clone shows "gave exceeds got" red in the ledger and green on the list) [S\*]; one palette for "owed to me / I owe" everywhere.
11. **Colour as the only signal of direction** [I]; OkCredit pairs arrows and words.
12. **Date scrubber without bounds** (Jupiter month nav reached "August 2031" and crashed) [V].
13. **Pop-up cross-sell inside a utility flow** ("scammy pop up", IDFC) [R].
14. **Bot-only support and unreachable humans** on every large app [R]; FinArt's WhatsApp support is a differentiator.
15. **Motion that delays the tap** (CRED haptics lag, BHIM jitter) [S/R].
16. **Release notes that say "bug fixes"**; Jupiter's warm second-person notes are the better brand surface [V].
17. **Hiding uncategorised money** or netting refunds so a chart omits ₹1.3L of a ₹1.5L month (OSS bug report, snippet) [S\*].

---

## 7. Trust-building playbook for SMS capture (India)

Everything the OS gives us is the same dialog every loan app uses, so trust is earned before, around and after it.

**Facts to build on**
- Google requires an in-app prominent disclosure right before the permission, with at least two options ("Agree"/"Not now"), a graceful decline path, text at about a 13-year-old reading level, "Why / What / How", not styled like the system dialog; it advises "Agree" over "Allow access" [V https://support.google.com/googleplay/android-developer/answer/11150561]. The review video must show the no-consent path.
- Data safety: data processed only on the device and not sent off it "does not need to be disclosed" as collected [V https://support.google.com/googleplay/android-developer/answer/10787469]. Expanager and PennyWise use this to show "No data collected".
- NN/g: giving a reason raised grants by 12%; a good reason vs the worst by 81%; "to offer a better user experience" is suspect; ask on user action, not at install [V https://www.nngroup.com/articles/permission-requests/] (US apps, directional only).
- Apple: pre-alert has one button labelled "Continue"; iOS has no SMS permission at all [V https://developer.apple.com/design/human-interface-guidelines/privacy]. So Android gets Agree + Not now; iOS gets a single Continue for notifications and an honest Shortcuts guide.
- **No India study of "Allow SMS" acceptance was found.** Indian-specific fear is evidenced indirectly (137-helpful FinArt review, Reddit thread titles such as "Anyone else stopped using finance apps that read your SMS?" [R title only]).

**Ranked tactics** (evidence in `india-ui-f`, summarised)
1. **Verifiable "nothing leaves this phone"** (no INTERNET, Data safety "no data collected", a screen that lists every outbound call, PennyWise-style). Ours: manifest-true on Android; iPhone has no network code found but unaudited [code].
2. **Opt-in, never at install**, tied to the moment of value: show manual entry and import working first; offer "Turn on bank-text capture" from a card (Hisab, Mera Kharcha "Off by default").
3. **Say what is NOT read in the same screen**, concretely: bank/UPI senders only; OTPs and personal chats skipped; originals kept (or not) on the phone and for how long.
4. **Our unique line:** "This app cannot read your inbox. It only sees a new text at the moment it arrives, and only keeps it if it is from a bank or payment sender." True because we hold RECEIVE_SMS and not READ_SMS [code via `COMPETITOR_ANALYSIS.md` 3.1]. No competitor can say it. **Unverified:** what Android's own dialog says for a RECEIVE_SMS-only manifest; check on a device so the primer matches it.
5. **Review-before-add + source badge + "show original"** on every captured entry (we have all three).
6. **First-class no-SMS path** (manual, import, paste, share, notification access as a separate, separately explained opt-in).
7. **State how the app is paid for** and never mention credit products (Fold FAQ model).
8. **Make exit easy:** "Stop reading texts" and "Delete what was read" in one place; revoking the permission does not delete anything already stored, so say so [S https://qrates.in/blog/why-loan-apps-ask-contact-sms-permission].
9. **Match Play label, listing copy and in-app copy word for word** (axio/FinArt mismatches are visible to exactly the user who reads labels).
10. **Localise the primer** (Hindi first). DPDP notices must be offered in English or Eighth Schedule languages [S, secondary; verify before relying].
11. **Fraud-awareness line** ("A bank never asks for your OTP. Report fraud: 1930") as in HDFC/Moneyview material.
12. **Human reachability** (WhatsApp/email with a named person) for the anxious.

**Draft primer copy (English, ≤ 13-year-old reading level; Hinglish line is a draft for a native-speaker review, not final).** Buttons: **Agree** / **Not now**.

- Title: "Let Expenses notice your bank texts?"
- Why: "So your bank and UPI payments appear in the app by themselves, and you only have to check them."
- What it reads: "Only new texts from banks and payment apps, the moment they arrive. It skips OTPs and chats with people."
- Where it goes: "Nowhere. This app has no internet permission, so it cannot send anything out. You can check this in Settings > Apps > Expenses > Permissions."
- What it cannot do: "It cannot read texts you already have."
- If you say no: "You can still add expenses yourself or import a statement. You can turn this on later."
- Hinglish draft: "Sirf bank aur payment ke naye SMS padhe jaayenge, OTP aur logon ke messages nahi. Kuch bhi phone se bahar nahi jaata."
- iPhone one-button page: "iPhone does not let any app read your Messages. Here is a 3-minute shortcut you control." [S pattern from https://github.com/aksh0609/upitrack and https://xyratrack.in/docs/iphone-automation: "Message Contains: Rs", run-immediately vs ask-first, forward-only, iOS 17+ for silent run]

**What nobody ships (white space):** an in-app "what I read / what I ignored" ledger by kind and time, never by content [I; not found in any app].

---

## 8. "Wow" ideas (36)

Each line: idea; inspired by; NEW or REFINEMENT; feasibility S (days) / M (1-3 weeks) / L (month+) [I]; privacy-safe on-device note. Numbers are referenced from sections 2 and 5.

### Trust and capture
1. **"Permissions we do NOT have" page** that reads the app's own manifest at runtime and shows a green list: no internet, no contacts, no location, no read-SMS. Inspired by BachatPe "No internet permission... Ever", PennyWise outbound-call list. **NEW**. S. Pure local `PackageManager` read; Android only, iPhone page states its own facts. [code: manifest]
2. **"Try it in airplane mode" card:** a 10-second guided check that shows the app still works offline. Inspired by Klera "verify in airplane mode in ten seconds". **NEW**. S. Nothing leaves; it only explains.
3. **Capture heartbeat chip:** "Last bank text seen: yesterday 8:14 pm" on Home/Capture, turning amber after a quiet stretch with a one-tap "Why isn't capture working?" Inspired by axio's silent-stop reviews and its asked-for rescan button (which we cannot offer). **REFINEMENT** of capture health `last seen`. S. Stores a timestamp, no text.
4. **One-line "How this app is paid for"** in More and in the primer: free, no ads, no loans, no data sale. Inspired by Fold's FAQ lines. **REFINEMENT** of the Privacy screen. S. Static copy.
5. **Primer in Hindi and Hinglish** with Agree / Not now and an in-app (not system-white) look. Inspired by Play prominent-disclosure rules, Google/Kantar playbook. **REFINEMENT** of the Slice 107 disclosure. S-M. Local strings.
6. **"What I read / what I ignored" ledger:** counts of read, skipped-as-OTP, skipped-as-person, skipped-unlisted-sender by day, never any words. Inspired by trust white space and UPI Track's "Not recognised" list. **REFINEMENT** of capture health counts. S-M. Counters only.
7. **One-tap "Stop reading texts and delete what was read"** with a plain note that revoking the OS permission alone keeps stored data. Inspired by Qrates note and Mera Kharcha "pause/remove". **NEW** (as a combined action). M. Local delete.
8. **Scam-awareness card** (static): "A bank never asks for your OTP, PIN or password. Report: 1930", shown once after the first capture and in More. Inspired by HDFC "Vigil Aunty", Moneyview fraud section. **NEW**. S. No network.

### Language and numbers
9. **Hinglish UI mode** (Roman-script Hindi labels, Latin digits) next to Hindi and English. Inspired by GPay Hinglish (2022), Khatabook/OkCredit Hinglish. **NEW**. L (string extraction of ~600 calls first; Hinglish copy is then cheap). Bundled strings.
10. **Voice entry that understands "chai 20", "auto ke 80 diye", "बीस रुपये चाय"** using the phone's on-device recogniser where available; fall back to typed with the same parser. Inspired by Mera Kharcha (server + Premium). **NEW** (absent item 11). M-L. Say clearly when the phone's recogniser is cloud-based; offer typed only.
11. **Transliteration-aware search** ("kapde", "doodh", "petrol" find Clothing, Milk, Fuel). Inspired by the Kanpur quote "I searched for Baccho ke kapde in English letters… no suggestions" [R]. **NEW**. M. Local alias table.
12. **Bundled font stack with ₹ + Devanagari and tabular figures**, so `1,24,500` and `98,000` align in lists. Inspired by Mukta/Noto notes; Indian grouping makes alignment irregular. **NEW**. M (APK size cost to measure). Local assets.
13. **Boundary-safe compact numbers kept as a shared tested primitive** and used on every chart axis; extend tests to `99,99,999.99` and `1,00,00,000`. Inspired by public `Rs100 lakh` bugs. **REFINEMENT** (our truncation already avoids it). S.

### Home, months, cards
14. **"Days to payday" line in the hero:** "₹1,698 a day · 9 days to payday". Inspired by a Jupiter reviewer's ask ("days remaining to salary"), Spenrol "left for today". **REFINEMENT** of safe-to-spend + monthly start day. S.
15. **Month-start presets:** 1st, 7th, "last working day", custom, asked once in first-run. Inspired by salary-cycle practice. **REFINEMENT** (start day 1-28 exists; "last working day" is NEW). S.
16. **"Your card due date lands 12 days before payday" tip** on the card page. Inspired by Kotak/Jupiter guidance on aligning statement and salary. **NEW**. S. Uses two numbers the person already entered.
17. **Billed vs unbilled split on a card tile** (statement day known): "Billed ₹18,400 due 5th · New since statement ₹6,200". Inspired by Fold concept critique, Uni "cannot pay custom amount before bill" complaint. **REFINEMENT** of card summary. S-M.
18. **EMI strip in "Coming up":** "EMIs ₹18,400 on 5th and 10th". Inspired by EMI culture and prior improvement 9. **REFINEMENT**. M. Local schedule.
19. **Cash-in-hand glance** on Home: "Cash ₹3,200 left", with an ATM-withdrawal prompt "₹5,000 taken out. Mark cash spends as you go?" Inspired by Moneyview FAQ cash management, Fold cash complaints. **REFINEMENT** of Cash account + Transfer candidates. S-M.
20. **Honest UPI Lite line:** "Small Lite payments may not send a text. Add them by hand if you want them." **NEW**. S. Verify Lite SMS behaviour on a real phone before shipping [I].

### Festivals, tax, assets
21. **Festival envelope:** optional "Diwali fund" with a budget, days left, and last year's same-festival total, nudged two weeks ahead; no rewards. Inspired by GPay Festival City (structure only), CAIT/UPI spike data. **NEW**. M. Needs a small bundled table of festival dates; the person picks which festivals.
22. **Festival accent and a 2-second celebration** (diya/rangoli motif, opt-in, never auto by region). Inspired by Jupiter's Diwali Quest animation. **NEW**. S-M. Local drawing; respects Motion Off.
23. **Same-festival year-on-year card** "Diwali 2025 ₹38,400 → this year ₹22,100 so far". No Indian precedent found; the nearest are GPay Rewind and Paytm Playback year/month recaps. **NEW**. M. Computed from local entries.
24. **Tax tags and ring** (80C, 80D, 80G, HRA rent) with "₹X of ₹1,50,000" shown only if the person chose the old regime; reminders on 15 Jun / 15 Sep / 15 Dec / 15 Mar and 31 Jul. Inspired by MoonProduct's wishlist, `OUR_FEATURES` "no tax tools". **NEW**. M. Local notifications; copy leads with 80C/80D but adds the new section number.
25. **Assets-lite:** gold (grams x 22K/24K, manual price), PPF/EPF/FD/RD/SIP (balance, rate, maturity date), property, with maturity reminders feeding net worth in lakh/crore. Inspired by INDmoney, Fold Custom Entries. **NEW** (prior item 11 is history only). M-L. All manual, no network.
26. **Kameti / chit tracker:** members x months x who-draws, payment calendar, "who is next" strip, "tracks only, never moves money". Inspired by Kameti apps. **NEW**. M. Local.

### People money and reminders
27. **Khata person page:** "You gave / You got", arrow + word + sign + colour, running balance that flips to "Settled ✓", thread-style history with ticks. Inspired by Khatabook/OkCredit. **REFINEMENT** of Lent/borrowed. S-M.
28. **Reminder composer:** a polite message in English/Hinglish/Hindi with three tones (soft, due-date, firm-but-kind) handed to the WhatsApp/SMS share sheet, signed with the person's own name. Inspired by Khatabook reminders, Klera "like a friend rather than a collection agency". **NEW**. M. Text is composed locally and shared by the user's own tap.
29. **UPI settle intent:** store a UPI id per person, open the payer's UPI app with amount and note prefilled, mark settled only after the person confirms. Inspired by Splitwise's gap and Klera. **REFINEMENT** of prior item 13. M. `upi://` is an intent, no INTERNET needed; payment cannot be verified [R DesiDime dev].

### Feel, tone, privacy
30. **Weekly/month-end "Wrapped" cards** (swipeable, neutral wording, "This may be because…", confirm/dismiss/correct on each insight; shareable with amounts blurred). Inspired by GPay Rewind 2020, Paytm Playback, NextLeap study. **REFINEMENT** (weekly recap is a notification only, ABSENT 29). M.
31. **Month-close moment:** a calm 2-second summary motion when a month ends under budget, after the action, never before a prompt. Inspired by CRED "reward first, then the ask". **REFINEMENT** of Slice 67 motion/haptics. S. No coins, no streaks (`PRODUCT_CONTEXT` decision).
32. **Low-RAM mode that turns off blur, wallet-stack spring and the 14-day line automatically** when `isLowRamDevice` or frames drop, with a note in Appearance. Inspired by Android Go guidance and BHIM/CRED jitter. **REFINEMENT** of the Motion setting. S-M.
33. **Per-entry "hide this one":** the row shows "Private" until unlock; totals still count it. Inspired by Paytm Hide Payments. **NEW**. S-M. Local flag; decide whether widgets and exports mask it.
34. **Category icon + colour tiles and brand-coloured monograms for the 250 known merchants** (no logo downloads, no trademark files): Inspired by PennyWise/Monefy icon requests and Fold's logo request. **REFINEMENT** (`HOME_OPTIONS` Q11, merchant table exists). S-M.
35. **Transfer note in the row:** show the person's note as a faint line under the title. Inspired by a 249-helpful Paytm review. **REFINEMENT** if rows do not already show it [not checked]. S.
36. **Bounded, snap-back-to-now date scrubber with dynamic decimals** on the 14-day line and calendar. Inspired by Jupiter's 2031 crash and Groww charts. **REFINEMENT**. S.

---

## 9. Suggested order (my judgement [I])

1. Cheap trust wins: ideas 1-4, 8 (days of work, no new data).
2. Language base: extract strings, then Hinglish/Hindi primer (5), Hinglish UI (9), voice (10), transliterated search (11). Hardest item, largest open ground.
3. Salary/card/EMI/cash cluster: 14-20.
4. Khata/reminder/UPI cluster: 27-29.
5. Festival + tax + assets: 21-26 (new surface, schedule by owner interest).
6. Polish and safety: 31, 32, 34, 36, 12.
7. Before any "most responsive app in India" claim: real-device cold start and scroll numbers on a 3 GB phone (`OUR_FEATURES` item 31).

---

## 10. Evidence-quality caveats (read before quoting)

- **No screenshots were viewed.** Every layout/colour/motion statement is text-derived (listing copy, release notes, design blogs). Where a source describes a screen, the tag says whether it is a marketing mock, a student concept or a primary design post.
- **Reddit unreadable** (blocked); Reddit items are titles or snippets only. Review voice comes from Play, App Store, DesiDime, G2/Trustpilot snippets. Play shows ~3 reviews per page, ranked "most relevant", some from 2020-23 (dated in the text).
- **Search-fallback summaries:** a rate limit made some answers come from a summariser; claims resting on them are marked [S\*] (CAIT, regional festival figures, Moneycontrol NISM, UPI Lite, RBI card-cycle, bank-lens counts). Open the linked page before citing publicly.
- **Student teardowns are opinion** (INDmoney, Kite, Groww, Fold concepts; the GPay NextLeap study is n=50 + 6 interviews). Groww chart statistics are the designer's own claims; Jar/Plotline uplifts and Slice "95% less cognitive load" are vendor/author claims.
- **Vendor-written comparisons** (Expanager, Spenrol, SpendCtrl, Klera, Niptao, FinArt blog, trei.dev) are biased toward their own product; quoted for design ideas, not market facts.
- **Not found at all:** any in-app competitor hero with `1.2L/3.4Cr`; any user complaint about Western number format or lakh in a global tracker; India-specific dark-mode or one-handed-use share (the 49% one-handed figure is a US-based Hoober study via a blog [S\*]); Hindi/regional UI in most wealth/card apps; battery-drain quotes for the SMS apps beyond old search-engine snippets; accessibility notes for almost every app (only Jupiter states a screen-reader fix); first-party quotes on salary-cycle demand; India-specific "Allow SMS" acceptance data; actual in-app SMS permission screens (only listing/policy/doc text).
- **Conflicts left unresolved:** Fold 3.1 (Play) vs 4.5 (own FAQ, iOS); Khatabook 11 vs 13 languages; Axis 4 vs 11 languages; IDFC 20 vs 51 categories; Kotak 811 aggregator rating; axio owner (CapFloat vs Amazon).
- **Play/Apple pages not readable:** Money View Money Manager listing, Mera Kharcha, Fi, Kotak/ICICI Mera iMobile, cred.club/neopop (404), PhonePe/Paytm App Store (429). GPay's Play page returned a US listing, so GPay review voice is from the India App Store.
- **Our own column:** all "we have it" claims are from `OUR_FEATURES.md`, `HOME_OPTIONS.md`, `PHASE2_PROGRESS.md` and spot reads of `MoneyFormat.kt`, `BudgetPeriod.kt`, `CategoryCatalog.kt` at `b899430`. None was run; items unconfirmed on a device are marked [not checked] or [I]. Ideas' feasibility letters are my estimates.
- **Policy dependence:** the Play SMS exception is described as temporary [V] and DPDP language duties come from secondary summaries; recheck both before launch copy is frozen.

---

## 11. Sources (primary first)

- Play/App Store listings read 2026-11-13: axio `com.daamitt.walnut.app`; CRED `com.dreamplug.androidapp`; BHIM `in.org.npci.upiapp`; PhonePe `com.phonepe.app`; Paytm `net.one97.paytm`; MobiKwik `com.mobikwik_new`; Moneyview `com.whizdm.moneyview.loans`; ET Money `com.smartspends`; FinArt `com.finart`; PennyWise `com.pennywiseai.tracker`; BachatPe `com.tingleworks.bachatpe`; Khatabook `com.vaibhavkalpe.android.khatabook`; OkCredit `in.okcredit.merchant`; HDFC `com.hdfcbank.android.now`; ICICI `com.csam.icici.bank.imobile`; Axis `com.axis.mobile`; IDFC `com.idfcfirstbank.optimus`; Jupiter, Groww, Kite, Kuvera, Slice, Jar, Fold (see inline URLs).
- Design teams: https://design.phonepe.com/stories/behind-phonepe-redesign ; https://design.phonepe.com/stories/phonepe-voice ; https://community.jupiter.money/t/jupiter-2-0-redesign-introducing-a-new-experience/51018 ; https://github.com/CRED-CLUB/neopop-web ; https://lottiefiles.com/case-studies/cred ; https://zerodha.com/z-connect/updates/dark-patterns-in-finance-apps ; https://nithinkamath.me/blog/introducing-kite-terminal-mode.
- Language/number/device: https://www.iamai.in/sites/default/files/research/Kantar_%20IAMAI%20report_2024_.pdf ; https://services.google.com/fh/files/misc/app-website-localization-indian-users.pdf ; https://raw.githubusercontent.com/unicode-org/cldr-json/main/cldr-json/cldr-numbers-full/main/en-IN/numbers.json ; https://design.google/library/new-wave-indian-type-design ; https://developer.android.com/docs/quality-guidelines/build-for-billions/device-capacity ; https://dontkillmyapp.com/ ; https://www.idc.com/resource-center/press-releases/india-smartphone-market-2025-2026.
- Trust/policy: https://support.google.com/googleplay/android-developer/answer/11150561 ; https://support.google.com/googleplay/android-developer/answer/10208820 ; https://support.google.com/googleplay/android-developer/answer/10787469 ; https://www.nngroup.com/articles/permission-requests/ ; https://developer.apple.com/design/human-interface-guidelines/privacy ; https://moneyview.in/security-money-view-app ; https://moneyview.in/privacy-policy-loans ; https://merakharcha.in/auto-tracking ; https://www.trakio.co.in/features/sms-auto-detection ; https://fold.money/faq ; https://www.kreditbee.in/privacy-policy ; https://techcrunch.com/2022/08/26/loan-apps-abuse-harassment-suicide-indian-users-google-apple-india/.
- Ledgers/festivals: https://khatabook.com/blog/khatabook-app-features/ ; https://okcredit.in/en/ ; https://kleraapp.com/udhaar-tracker-app/ ; https://support.google.com/pay/india/answer/14147019?hl=en ; https://support.google.com/pay/india/answer/17034014 ; https://www.thetechbag.com/blog/upi-october-record-1-lakh-crore-a-day.
- Secondary (opinion/vendor, see section 10): UX Planet CRED teardown, Digia CRED nudges, 60fps.design, NextLeap/Medium student studies, Yellow Slice axio audit, Remitbee wallet comparison, spenco/trei/Expanager/SpendCtrl guides, DesiDime threads.
- Working notes behind this file (not committed): per-topic research notes a-f held by the author.

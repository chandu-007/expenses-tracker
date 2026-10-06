# India expense-tracking landscape — market research (as of 2026-10-05)

Method: web research plus a scrape of the newest Google Play India reviews per app (300–1,000 per app, Oct 2026). I used `google-play-scraper` and tagged themes with regex. "Low" means ≤3★. The theme counts give a rough sense of frequency. They are not precise statistics. All quotes are verbatim, ≤25 words, and cut with "…" where needed. Unless another link is given, Play quotes come from the app's Play listing `https://play.google.com/store/apps/details?id=<pkg>&hl=en_IN`. Play ratings: where a value was visible on the listing page I quote it. Otherwise the number is the scraper's all-time score, marked "(scr)". Listing pages show recency-weighted values that can be lower. For example, axio shows 3.8 on its page and 4.46 in the scraper.

Frequency tags: **very common** (≥10% of low reviews in the sample, or repeated across sources); **recurring** (3–10%); **occasional** (<3%, but notable).

---

## Apps covered

### axio (formerly Walnut) — SMS tracker owned by a lender
Walnut was the original Indian SMS-parsing expense tracker. Capital Float acquired it in 2018 for about $30M ([Medianama](https://www.medianama.com/2018/08/223-capital-float-acquires-walnut/)), and it is now **axio**. The Play listing says: "axio App is an SMS-based money management app". It promotes **axio Pay Later, Personal Loans (14%–35% p.a.) and Fixed Deposits** alongside tracking. It also says SMS permission is used for "Personal Loan: Fraud prevention", "Money Manager" and "Fixed Deposit: SMS-based SIM-device binding" ([Play](https://play.google.com/store/apps/details?id=com.daamitt.walnut.app&hl=en_IN)). The Data safety section says the app "may share… Location and Personal info" with third parties. FinArt cites axio's FAQ as saying cross-device sync is not supported ([FinArt comparison, Jul 2026](https://finart.app/best-expense-tracker-apps-india)) [secondary source]. The listing was last updated 15 Jul 2026. There is no official iOS app. "Axio Money: Expense Tracker" on the App Store is from an unrelated developer, Sajid A Khokhar ([App Store](https://apps.apple.com/us/app/axio-money-expense-tracker/id6780629197)), which creates name confusion.

### Fold — Account-Aggregator-first PFM (the closest "modern" competitor)
Fold (Lunchbrake Classic Pvt Ltd) is a design-led app that tracks bank accounts, cards, MFs, stocks, EPF, NPS, FDs, insurance, credit score and net worth "using RBI's Account Aggregator framework… No password sharing, no SMS scraping" ([Play](https://play.google.com/store/apps/details?hl=en_US&id=money.fold.marble)). Fold is a **Sahamati-certified FIU** because it is a **SEBI-registered Investment Adviser (INA000018072)**. It uses Finvu and CAMSFinserv as AAs, and keeps its data on AWS India ([Fold blog](https://fold.money/blog/privacy-by-design)). AA does not cover credit cards, so Fold tracks them by **parsing email on the device** (shipped Jan 2024; same source). Other features: Spaces for families, Fold AI, MCP integration for AI tools ([fold.money/mcp](https://fold.money/mcp)), and a web app on Plus.
Pricing: **Fold Plus** launched 2 Oct 2025 at ₹2,999/yr regular, with early-bird prices of ₹1,499–₹2,009. A "Believer" lifetime plan was ₹49,999 regular and ₹24,999–₹33,499 early-bird. The free tier lost secondary-number banks, web access and export, and gets ads plus 5 manual refreshes a month ([Fold blog](https://fold.money/blog/price-of-free-software)). In 2026, users report ₹299/month, ₹999/yr and ₹25,000 lifetime [user-reported, verify in-app].

### ET Money — investment app that has dropped expense tracking from its pitch
ET Money is a 360 ONE-owned wealth app for MFs, SIF, NPS and insurance ([Wikipedia](http://en.wikipedia.org/wiki/ET_Money)). It once had an SMS-based "Expense Manager" ([ET Money help](https://www.etmoney.com/help/others/expense-manager)). Its **current Play description never mentions expense or SMS tracking**; it covers only investments and external MF tracking (Play `com.smartspends`, checked Oct 2026). Whether the in-app expense manager still works is **[unverified]**. The current monetisation is the "Genius" paid membership.

### Moneyview — lender with a bundled "Money Manager"
Moneyview is a loan-first super-app: personal loans, cards, UPI, FDs, plus "Track". It claims 12 Cr+ users ([moneyview.in](https://moneyview.in/)). Its Money Manager parses bank SMS and supports cash entries, budgets, backup/restore and category edits. Moneyview says it gets "partial account numbers… don't read OTP" ([FAQ](https://moneyview.in/money-view-app-faq)). Moneyview Ltd appears on Sahamati's FIU list as an IRDAI corporate agent, status "Live-enabled" ([Sahamati](https://sahamati.org.in/fip-fiu-in-account-aggregators-ecosystem)). Its Play reviews are about loans almost exclusively. The tracker is a lead-generation surface.

### INDmoney — investing app with net-worth and expense tracking
INDmoney is a US/Indian stocks and MF app that pitches "Track your entire net worth for free… assets, investments and expenses with insights… Link your bank accounts, credit cards…" (Play `in.indwealth`). Expense tracking is secondary, and recent reviews barely mention it.

### Jupiter — neobank with a "Money" tab (AA-based)
Jupiter (Amica Financial Technologies) offers a Federal/CSB Bank account, a UPI RuPay credit card and loans as a DLA/LSP. Its "Money" tab tracks spending categories, budgets, bills, credit cards and net worth through AAs like Finvu ([jupiter.money/money](https://jupiter.money/money)). It claims "30 Lakh+" users.

### Fi Money — shut down (cautionary tale)
Fi was a Federal Bank–partnered neobank known for money-insight features, with 3.5M+ customers and about $169M raised. In **Feb 2026** it pivoted to AI/B2B and laid off consumer teams ([Inc42](https://inc42.com/buzz/cash-strapped-fi-money-pivots-to-ai-led-b2b-play)). On **11 Mar 2026** it shut down banking services and told users to move to Federal Bank's FedMobile ([ET](https://economictimes.indiatimes.com/tech/technology/neobank-fi-money-shuts-down-banking-services/articleshow/129470782.cms)). The fi.money site now says "New savings accounts cannot be opened through the Fi app" ([fi.money](https://fi.money/)). Co-founder Sumit Gwalani left in May 2026 ([Inc42](https://inc42.com/buzz/fi-money-cofounder-sumit-gwalani-quits-amid-mounting-financial-struggles)). Tracxn lists Fi as "Deadpooled".

### CRED — CRED Money (AA-based bank tracking for affluent users)
CRED is a credit-card bill-pay, rewards and lending app. **CRED Money** launched in Jul 2024. It uses AA to consolidate bank accounts and recurring payments (SIP, rent, staff salaries), with merchant/category lookup and reminders. CRED said it "won't be monetizing the new feature" ([TechCrunch](https://techcrunch.com/2024/07/24/cred-launches-personal-finance-manager-for-indias-affluent)). The Play listing also promises "Spot hidden charges & duplicate spends" and "smart statements" for cards. Finvu names CRED as an anchor customer ([productgrowth.in](https://productgrowth.in/tools/banking-api/finvu/)).

### Paytm / PhonePe / Google Pay — built-in spend insights
- **Paytm "Spend Summary"** auto-categorises *Paytm* UPI payments into groceries, food, rent and similar categories, with month switching ([Paytm blog, Aug 2026](https://paytm.com/blog/payments/paytm-spend-summary-upi-expense-tracker)). Paytm also has "Hide Payments".
- **PhonePe and GPay** have payment history and bill reminders. The Play listings do not advertise budgeting [observed Oct 2026]. GPay users keep asking for a tracker (see Unmet needs).
- The structural limit is that each app sees only its own UPI flow. Users who mix apps get "multiple incomplete pictures" ([MoonProduct](https://www.moonproduct.in/insights/upi-expense-tracker-india-2026/)).

### Kuvera (by CRED) — MF investing and tracking, degraded after rebrand
CRED acquired Kuvera in Feb 2024 ([TechCrunch](https://techcrunch.com/2024/07/24/cred-launches-personal-finance-manager-for-indias-affluent)). It is a direct-MF, FD and US-stocks app with goal and portfolio tracking. It is **not an expense tracker**, but it matters as a trust signal. The 2026 "Kuvera by CRED" redesign triggered a wave of 1★ reviews (see Drawbacks).

### FinArt — paid, privacy-positioned SMS/email/notification tracker
FinArt (Frienteer Software) has been around since 2016 and claims a launch in that year. It reads bank SMS, email and **bank-app notifications** on Android. On iOS it uses SMS (presumably via Shortcuts), email, Apple Pay and PDF statements. It says it has "No bank login. No UPI PIN", is ad-free, has no lending, and offers a "Private Mode" that keeps data on the device with backups to the user's own Drive or iCloud ([FinArt](https://finart.app/best-expense-tracker-apps-india)). Pricing: 5-day trial, then a subscription priced by country. One user reports 50 → 90 AED/yr.

### Splitwise — group splitting; India use is common but monetisation is hostile
Splitwise is the default bill-splitting app for Indian flatmates and trips. It does not track overall spending. The free tier now caps expense entries per day, shows ads and has wait screens. Pro is a subscription.

### Money Manager (Realbyte) & Spendee — global manual apps used in India
**Money Manager** is manual double-entry with an optional ad-removal purchase and PC Connect. It is often recommended for Indian "disciplined manual" users ([FinArt list](https://finart.app/best-expense-tracker-apps-india)). **Spendee** is manual entry, wallets and bank sync (non-India), with a subscription. India-specific usage share is **[unverified]**; neither publishes country breakdowns.

### Bachat
"Bachat" (getbachat.in, 2019) and "Bachat App" (bachatapp.com, 2016) are both listed as **Deadpooled** on Tracxn ([1](https://platform.tracxn.com/a/d/company/5ced0cadf036855e46f84721/bachat), [2](https://platform.tracxn.com/a/d/company/58988956e4b08b8241307e7f/bachat%20app)). "Bachatt" (`app.bachatt.customer`) is a savings/MF app for the self-employed, not a tracker ([Play](https://play.google.com/store/apps/details?hl=en_IN&id=app.bachatt.customer)). "BachatAI" is waitlist-only ([bachatai.com](https://bachatai.com/)).

### 2024–2026 Indian newcomers (small, mostly indie)
| App | Model | Notes |
|---|---|---|
| Trakio | Android SMS parsing, "10 languages", web app | [trakio.co.in](https://www.trakio.co.in/) |
| MonAI | On-device AI reading UPI bank SMS, free | [monai.in](https://monai.in/) |
| ExpenseAI | UPI/bank SMS tracker | [expenseai.co.in](https://expenseai.co.in/) |
| UPI Tracker | Expenses plus a **UPI AutoPay mandate dashboard**, receipt snap | [upitracker.com](https://www.upitracker.com/) |
| Inly | Reads UPI, SMS and bank alerts | [inly.digital](https://inly.digital/) |
| Finzo, Xyra | **iPhone bank-SMS tracking via Shortcuts automation** | [finzo.money](https://finzo.money/), [xyratrack.in](https://xyratrack.in/) |
| SpendByMe | Claims automatic tracking on Android and iOS | [spendbyme.com](https://spendbyme.com/) |
| Finny | iOS AI tracker: Apple Pay auto-log, scan, speak | [getfinny.app](https://getfinny.app/) |
| mFinley | Local-first, CSV import, BYO-API-key AI | [moonproduct](https://www.moonproduct.in/insights/upi-expense-tracker-india-2026/) |
| Reddit/OSS | "free Walnut replacement that's 100% offline" (r/personalfinanceindia), TrackMyRupee "No SMS. No Bank Login." | [reddit](https://www.reddit.com/r/personalfinanceindia/comments/1t94luo/i_built_a_free_walnut_replacement_thats_100/), [GitHub](https://github.com/OmkarPathak/TrackMyRupee) |

The pattern: a wave of indie apps since 2025, all pitching "on-device, no loans, UPI-first." None has scale yet. The market is fragmented, and nobody has replaced Walnut.

### Summary table
| App | Platforms | Pricing | Play rating / installs (IN) | Data entry model | Standout features |
|---|---|---|---|---|---|
| axio (Walnut) | Android only | Free; monetised by loans, Pay Later, FDs | 3.8 page / 2.65L reviews / 1Cr+ | SMS parsing + manual | Bill reminders, merchant renaming, tags/receipts |
| Fold | Android, iOS, web (Plus) | Free + Plus (₹2,999 list; ₹1,499–2,009 early-bird; ₹299/mo user-reported) | 3.1 page / 1.38K / 1L+; iOS 4.5 (1.2k) | AA (banks/investments) + on-device email parsing (cards) | Net worth, EPF/NPS, Spaces, AI, MCP, refund if sync fails 10 days |
| ET Money | Android, iOS, web | Free + Genius subscription | 4.43 (scr) / 2.3L / 1Cr+ | (Legacy SMS expense manager, status unverified) | MF/SIF investing, external MF tracking |
| Moneyview | Android, iOS | Free (loan revenue) | 4.78 (scr) / 42L / 10Cr+ | SMS parsing + manual cash | Loans, credit tracker, UPI |
| INDmoney | Android, iOS, web | Free + paid tiers | 4.57 (scr) / 4.6L / 1Cr+ | Bank/card linking (AA) | US stocks, net worth, MCP for Claude |
| Jupiter | Android, iOS | Free (cards/loans revenue) | 4.31 (scr) / 6.1L / 1Cr+ | Own account + AA | Budgets, CC tracking, rewards |
| Fi | — | Shut down Mar 2026 | — | (was own account) | — |
| CRED (Money) | Android, iOS | Free | 4.76 (scr) / 30L / 5Cr+ | AA (banks) + card statements | Recurring payment detection, hidden-charge detection |
| Paytm / PhonePe / GPay | Android, iOS | Free | 4.67 / 4.43 / 4.34 (scr); 50Cr+–100Cr+ | Own UPI history only | Paytm Spend Summary |
| Kuvera | Android, iOS, web | Free | 4.38 (scr) / 19.7K / 10L+ | MF/CAS import | Direct MFs, tax harvesting |
| FinArt | Android, iOS | 5-day trial → subscription | 4.3 page / 6.2K / 10L+ | SMS + email + notifications (+ PDF/Apple Pay on iOS) | No ads/loans, private mode, family sync, human support |
| Splitwise | Android, iOS, web | Free (daily limits, ads) + Pro | 4.28 (scr) / 1.95L / 1Cr+ | Manual | Group splits, settle-up |
| Money Manager | Android, iOS, PC Connect | Free + ad-removal purchase | 4.65 (scr) / 4.66L / 1Cr+ | Manual | Double-entry, assets |
| Spendee | Android, iOS, web | Free + subscription | 4.33 (scr) / 63.7K / 10L+ | Manual + bank sync (non-IN) | Shared wallets |

---

## Drawbacks by app

### axio (Walnut)
Sample: 1,000 newest reviews (May–Oct 2026), 409 low. Theme hits among low reviews: loans 62, support 52, SMS/permission 39, missed transactions 39, bugs 36, sync/backup 11, categories 10, duplicates 5.
- **SMS detection silently breaks (very common).** "All of a sudden it stopped reading SMS. I reported many msgs for support." (2★, Jul 2026, 31 helpful). "Since May 3… debit sms started coming from AD-DCBANK-T. These are not getting tracked." (Jun 2026). This last one is a **TRAI header-suffix change breaking the parsers**. "Facing this problem since August 2nd, 2026… I have reported… but still I don't see any support" (Aug 2026).
- **Duplicates inflate spend (recurring).** "Almost all online transactions are captured twice or sometimes even 5 times" (Jul 2026, 11 helpful). "credit card spends & bill payments appear to be duplicated… This results into double the spends." (Jul 2026). Also: "it is getting difficult to track duplicated transactions… highlighting the duplicacy" (Aug 2026).
- **Data loss on phone change (recurring).** "installed the app but it never asked me to restore… my 7 years of expense tracking data is gone!" (May 2026). "lost all the categories I had created. It read the SMS's all over again" (Jun 2026).
- **Lending business contaminates the brand (very common).** "They run a shady operation. Despite the 25% interest rate…" (Sep 2026). "ABSOLUTE SCAM & PREDATORY APP!… fraudulent lending app" (Sep 2026). Amazon Pay Later disputes also come up.
- **Balance and category wrong (recurring).** "the same transaction amount is not deducted from the Balance section" (Jul 2026, 7 helpful). "all getting categorized as 'unknown'" (Jul 2026).
- **Bloated UI and no support (recurring).** "The new interface is too complicated and chaotic" (Jun 2026). "WhatsApp customer support has not b[een]…" (Aug 2026). An Indian blogger calls it "pretty nifty" but with "ads/marketing bloat" ([Substack](https://lavsmohan.substack.com/p/an-expense-tracker-that-doesnt-suck/comments)).
- **Android only, no multi-device sync** ([FinArt citing axio FAQ](https://finart.app/best-expense-tracker-apps-india)).

### Fold
Sample: 735 reviews, Nov 2023–Oct 2026, 177 low. Theme hits: bugs 26, AA/linking 18, missed transactions 16, cash 8, categories 8.
- **No manual or cash entry at first (very common, 2024–25).** "It's shocking that an expense tracking app doesn't have manual transaction tracking. If I pay someone with cash…" (2★, Jun 2024, 36 helpful). One 2026 user still asks for a "Way to manage cash accounts, not as transactions".
- **AA coverage and reliability gaps (very common).** "Key accounts like PPF (SBI) and local Co-op banks are missing" (Jan 2026, 19 helpful). "transactions are not getting tracked but the refresh is successful every day" (Oct 2025). "I get SMS from bank regarding the consent is approved but, app stays on same welcome screen!" (Nov 2024). Fold's own help pages list these as known issues: "SBI… Linking may only work during 5-8 AM". "HDFC Bank… ~12-hour delay". ICICI "doesn't share transactions data on weekends". Joint and current accounts are unsupported. Some banks show a "5.30 hrs offset" ([Fold Known Issues, Aug 2026](https://help.fold.money/hc/en-us/articles/17395308835986-Known-Issues-Banks); [linking help](https://help.fold.money/hc/en-us/articles/17257682061458-Why-can-t-Fold-find-or-link-my-bank-account)).
- **Price backlash and paywalled export (recurring, 2026).** "₹299/month is a ridiculous price for an app that was supposed to help us save money" (Apr 2026, 32 helpful). "Moving transaction export behind a paywall is a dealbreaker. It's my own bank data" (Jul 2026). "charging a massive ₹25,000 for a Lifetime" (Jun 2026).
- **Duplicates and wrong dates (recurring).** "transactions reflecting for incorrect dates and boy! these duplicate entries just ruined the experience" (Oct 2025). "expenses and credit card bills are double counted" (Dec 2024). Fold's help page admits "you may see duplicate transactions".
- **Credit-card edge cases (occasional).** Add-on cards fail: "ICICI Coral and Sapphiro… both statements come in one Coral branded statement" (Dec 2025). Payment source is unclear: "credit card payments through Google Pay… difficult to know whether… bank account or a credit card" (Jul 2026, 15 helpful).

### ET Money
Sample: 600 newest (Feb–Oct 2026), 227 low.
- **Paywalling of formerly free features (very common).** "every basic thing is getting added in the paid version and that is too very expensive" (Apr 2026). "Every time I login, existing features need new payment. Last time I paid 999…" (May 2026). "paywalled basic mutual fund strategy improvements inside an expensive higher plan" (Oct 2026, 16 helpful).
- **Sync and stability (recurring).** "Post-redemption, sync fails repeatedly" (Mar 2026). "not opening why?" (Aug 2026).
- **Expense tracking deprioritised.** It is gone from the Play description and essentially absent from reviews [inference from listing and review sample].

### Moneyview
Sample: 600 newest (Oct 2026) plus 400 "most relevant" (2026). Almost no reviews discuss the tracker; most are about lending.
- **Aggressive collections and calls (very common).** "I received over 1,000 calls" (Jul 2026). "still receiving 5–6 calls daily demanding payment" (Aug 2026, 11 helpful).
- **Can't delete data, AutoPay traps (recurring).** "I am unable to turn off the AutoPay, and I am also unable to delete my personal data" (Sep 2026).
- **Support (recurring).** "They only offer a call back option… which never happens" ([App Store](https://apps.apple.com/in/app/moneyview-personal-loan-app/id6468976019)).
- The tracker exists to feed loan underwriting and marketing [inference]. FinArt's comparison puts it this way: "Lending is the core business" ([FinArt](https://finart.app/best-expense-tracker-apps-india)).

### INDmoney
Sample: 600 newest, 133 low.
- **Marketing calls after browsing (recurring).** "exploring anything (like checking insurance) triggers non-stop marketing calls the next day… telemarketing harassment" (Sep 2026).
- **UPI reliability (recurring).** "a UPI payment went to 'Pending' and the money was debited… I had to pay twice" (Aug 2026).
- **Dark patterns (occasional).** "checkout page forces a popup stating all new investments will be made in a 'Regular Plan'" (Oct 2026).
- **Shallow analytics (occasional).** "Good for basic tracking, but lacks essential portfolio analytics" (Sep 2026).

### Jupiter
Sample: 600 newest, 365 low. Support 109 and loan 40 hits out of 570 low in the larger earlier sample.
- **Loan and credit-card spam (very common).** "Why are you sending loan notifications multiple times in a day? I don't want any loan." (Sep 2026). "will force u to buy a jupiter credit card" (Aug 2026). "Too much calls from IDFC bank for credit card and loans" (Sep 2026).
- **Support unreachable, login and UPI failures (very common).** "getting logged out automatically and can't log back in… 'Technical issue' error" (Aug 2026).
- **Fear of neobank shutdown (recurring, post-Fi).** "kiked this third party bank… any moment they can quit and take your money" (Aug 2026). A Reddit thread is titled "Jupiter no longer feels NeoBank, Experience after 7 Years" ([r/personalfinanceindia](https://www.reddit.com/r/personalfinanceindia/comments/1vwvwv1/jupiter_no_longer_feels_neobank_experience_after)).
- Tracking covers the Jupiter account plus AA-linked accounts only. Cash and other UPI handles get no special handling [inference from feature page].

### Fi Money
- **Shutdown and stranded users.** "some customers reported difficulty reaching Fi's customer support and said they had to rely on email" ([ET, Mar 2026](https://economictimes.indiatimes.com/tech/technology/neobank-fi-money-shuts-down-banking-services/articleshow/129470782.cms)). Community warnings came months earlier: "Fi Money is dying | Funds are only left for 10 months | Better to close the account" ([DesiDime, ~Aug 2025](https://www.desidime.com/discussions/fi-money-is-dying-funds-are-only-left-for-10-months-better-to-close-the-account)). Features were cut first: "Fi Money shuts down some features, trims workforce" ([Mint, Jul 2025](https://www.livemint.com/companies/start-ups/neobank-fi-money-shuts-down-some-features-trims-workforce-to-stay-afloat-funding-crunch-cost-cutting-11752136900892.html)).
- **Lesson:** users who relied on Fi's insights lost their history. Data export and portability are trust features.

### CRED (CRED Money)
Sample: 600 newest, 256 low. CRED Money itself is rarely mentioned.
- **Bloat, "casino" rewards, performance (recurring).** "The whole Rewards section with its casino vibe is getting tiresome. The app is filled with so much junk" (Oct 2026). "heavily focused on animations & videos rather than speedy & smooth UI/UX… memory-intensive" (Oct 2026).
- **UPI pending and refunds with bot-only support (recurring).** "UPI payment stayed pending for 24+ hours… automated responses with no direct contact option" (Oct 2026, 22 helpful).
- **Permission and privacy unease (occasional).** "the app asks for SMS permission during setup. If this is only for OTP… enter OTPs manually" (Oct 2026).
- **Lending (occasional).** "cred personal loan or cash never try please… humongous amount of intrest" (Oct 2026).
- Reddit thread titles show distrust: "Is cred or cheq good enought to give them my privacy?" and "Built a local credit card expense tracker because CRED frustrated me" ([r/CreditCardsIndia](https://www.reddit.com/r/CreditCardsIndia/comments/1uq0j5k/built_a_local_credit_card_expense_tracker_because/)).

### Paytm / PhonePe / Google Pay
- **Single-app silo.** "only covers transactions made via that app… multiple incomplete pictures" ([MoonProduct](https://www.moonproduct.in/insights/upi-expense-tracker-india-2026/)).
- **No real tracker in GPay (recurring requests).** See Unmet needs. Other GPay complaints: "Cash is debited but the payment is still being processed… support is useless… pre-made conversation with ai" (Apr 2026, 29 helpful).

### Kuvera (by CRED)
Sample: 400 newest (Aug–Oct 2026), 115 low. 45 low reviews mention CRED.
- **Post-acquisition degradation (very common).** "it was a perfect, user friendly… until the Cred update came. Now I cannot find anything" (Aug 2026, 23 helpful). "not even allowing me to login without taking WhatsApp consent from me" (Aug 2026, 18 helpful). "The new app is 5x slower, requires more user permissions, sends… ads without unsubscribe button" (Aug 2026).
- **Lost features.** "EPF/NPS tracking is missing" (Aug 2026). A Reddit thread is titled "Cred killed Kuvera. Where are you moving to?" ([r/personalfinanceindia](https://www.reddit.com/r/personalfinanceindia/comments/1shy50i/cred_killed_kuvera_where_are_you_moving_to/)).

### FinArt
Sample: 600 (Feb 2024–Oct 2026), 109 low.
- **Paywall and price hikes (very common).** "There are no features for you if you haven't paid for the subscription" (May 2024, 11 helpful). "suddenly nearly double the yearly subscription. it went from 50aed to 90aed. without giving even a notification" (Nov 2025).
- **SMS privacy fear (top-voted).** "allowing to read all the message means allowing otp access also. This looks dangerous." (1★, Mar 2024, **137 helpful**).
- **Missed transactions (recurring).** "NOT DETECTING ALL TRANSACTIONS… often misses big transactions too" (Aug 2025). "same expenses are added twice. once from App and one from SMS" (May 2024). This is the SMS + notification double-capture problem.
- **Dated UI, hangs (recurring).** "UI is too outdated. comparatively apps like Axios have much cleaner and modern look" (Jul 2026). "the app hangs, becomes unresponsive" (Jan 2026).

### Splitwise
Sample: 600 newest (Apr–Oct 2026), 387 low. Paywall 97, ads 32.
- **Daily entry limits and ads (very common).** "limiting free users to inputting just 3 expenses a day is terrible and money-hungry" (Jun 2026). "When inputting multiple expenses, you get an ad, each time." (May 2026). "With every iterative update, the free tier gets that much more unusable." (Apr 2026, 26 helpful).
- **Pricing structure (recurring).** "Paying for a subscription to have ONE trip pass per year!? That must be a joke." (May 2026).

### Money Manager (Realbyte)
Sample: 600 newest, 58 low (well liked).
- **Ads after purchase (recurring).** "Still getting ads months after purchasing app" (Sep 2026). "why do ads appear when I use the same account on a different phone? Should I buy it again?" (Sep 2026).
- **No multi-device or iOS–Android sync (recurring).** "would've been much better if it had multi device support and sync" (Sep 2026). Another user's premium "suddenly reverted to the free version" (Aug 2026).

### Spendee
Sample: 600 (Jun 2025–Oct 2026), 363 low. Bugs/slow 169.
- **Redesign regressions and slowness (very common).** "I have Pixel 8 Pro… WHY IS THIS APP SO SLOW??" (Sep 2026). "fake liquid glass on Android… didn't fix bugs" (Aug 2026).
- **Upsell nags and billing (recurring).** "every time I open the app there is an intrusive ad for 'limited time' subscription offer" (Sep 2025). "bank sync does not work and has not worked for over a year" (Jun 2025).

---

## Cross-cutting pain points (ranked by severity × frequency)

1. **Unreliable auto-capture: missed, duplicated and wrong-dated transactions.** This hits every automatic app. SMS apps break on bank format or header changes (axio's `-T` suffix case) and on OEM background limits (Samsung One UI 8.5 report). AA apps break on bank downtime, delays (HDFC ~12h), weekend gaps (ICICI) and timezone offsets. Duplicates come from SMS + notification + email, card spend + card bill payment, or AA re-fetches. Users then "Need a lot manual work", which defeats the purpose.
2. **The tracker is a funnel for loans, cards or investments.** axio, Moneyview, Jupiter, INDmoney, CRED and ET Money all monetise with credit or investment upsell, calls and notifications. Users connect this directly to distrust of SMS access. Lending complaints (25–35% APR, harassment calls) spill over onto the tracker brand.
3. **Privacy fear around SMS reading.** The top-voted FinArt review (137 helpful) is about OTP exposure. Users explicitly seek apps "without… SMS reading permissions, bank information linking" ([summary of Indian forum threads](https://www.teamblind.com/post/best-free-expense-tracker-app-kqt0fxjh)). Reddit threads are titled "Best app to track your monthly expenses without your personal data breaches" ([r/personalfinanceindia](https://www.reddit.com/r/personalfinanceindia/comments/1v4gfhz/best_app_to_track_your_monthly_expenses_without/)) and "Fold is the best expense tracker I've tried. But is it safe" ([r/personalfinanceindia](https://www.reddit.com/r/personalfinanceindia/comments/1kxm591/fold_is_the_best_expense_tracker_ive_tried_but_is/)).
4. **Data loss and lock-in.** Phone changes wipe history (axio). There is no cross-device sync (axio, Money Manager). Export is paywalled (Fold). Apps shut down or change hands (Fi, Kuvera→CRED, Walnut→axio). Fi's shutdown is fresh evidence that users can lose an app overnight.
5. **Aggressive paywalls and surprise price hikes.** Fold Plus, ET Money Genius, Splitwise daily caps, FinArt's doubled renewal, Spendee nags. Users will pay, but they reject paying for their *own data* or for basics that used to be free.
6. **Cash and offline spends ignored.** Fold launched without manual entry. AA and UPI-app insights cannot see cash. GPay users ask for "track all types of payments (Cash, Card, and UPI)". Cash in circulation is still growing at double digits ([RBI DG via CNBC-TV18, Aug 2026](https://www.cnbctv18.com/business/finance/rbi-flags-cash-paradox-currency-in-circulation-grows-despite-digital-payments-boom-19971770.htm)).
7. **UPI-specific misclassification.** UPI handles like `paytm@xytrq` or `razorpay@hdfcbank` don't map to merchant names. P2P transfers (rent, friends, settling a split) are counted as expenses. Failed-then-refunded UPI shows up as noise. Paying a credit card via a UPI app hides whether the money came from the bank or the card ([MoonProduct](https://www.moonproduct.in/insights/upi-expense-tracker-india-2026/); Fold review).
8. **Bloat and performance.** CRED's "casino vibe" and animation lag, Kuvera "5x slower", axio "chaotic", Spendee "so slow". This is a big opening for a *very responsive* app.
9. **Support is bots only.** Every large app (axio, CRED, GPay, Jupiter, Moneyview, Kuvera) draws "automated responses" complaints. FinArt sells "human customer support" as a differentiator.
10. **iOS gap.** iOS cannot read SMS, so axio, Moneyview and the SMS-era apps are Android-only. iPhone users rely on AA (Fold, CRED) or Shortcuts hacks (Finzo, Xyra, FinArt iOS). Apple forums note that Shortcuts message triggers historically didn't fire for bank short-code senders ([Apple Dev Forums](https://developer.apple.com/forums/thread/705659)) [may have changed in iOS 26/27; unverified].

## What users love (keep these)
- **Set-and-forget auto-capture.** "Hands down… the best app to track your expenses. Set and forget" (axio, before regressions). "It neatly collects expenses from all sources, credit card, UPI etc." (axio, 5★).
- **Merchant renaming and rules.** "instead of paytm@xytrq i can name it as MyGrocer" (axio, 5★).
- **Bill and credit-card due reminders.** The #1 adoption reason cited by FinArt. axio, Jupiter and CRED all lead with it.
- **Calm, clean design.** "Great app with a clean interface and good user experience" (Fold, 5★). "Liking my time with this app so far. The UI is so satisfying." (r/foldmoney).
- **One place for banks, cards and investments (net worth).** Jupiter testimonials: "I can see my total Net Worth across banks." Fold's EPF/NPS/insurance coverage.
- **No ads, no lending, human support.** FinArt and Fold's positioning, and users respond to it.
- **Fast manual entry for cash** (Money Manager loyalty: high rating, few complaints).
- **Fair refund policies.** Fold refunds if a connected instrument fails for 10 consecutive days.

## Unmet needs / feature wishes (explicit user requests)
- **"Track all types of payments (Cash, Card, and UPI)… automatically detect cash withdrawa[ls]"** (GPay, 4★, Jul 2026, 29 helpful; repeated by others with 15 helpful).
- **Monthly budget and category tracker inside the payment app.** "I would like to request a Monthly Budget & Expense Tracker feature in the Google Pay app" (Jul 2026). Also **Excel export**: "reports exported probably in excel form… every middle class man wants know how much he spent" (GPay, 14 helpful).
- **Payment-source attribution** (bank vs credit card vs UPI-on-credit vs cash), with grouping by method (Fold, 15 helpful).
- **Duplicate review queue.** "a separate tab for them or… highlighting the duplicacy" (axio).
- **Lossless backup and restore with categories, notes and attachments** (axio, multiple).
- **Cash accounts and offline transactions** (Fold, multiple). **PPF, co-op banks, RD, FD auto-sync** (Fold).
- **Add-on and family credit cards** (Fold).
- **Multi-device and cross-platform sync** (Money Manager, axio).
- **Couples and family shared budgets** (r/personalfinanceindia "Couple Expenses tracking suggestions?"; FinArt and Fold Spaces).
- **Free CSV export of one's own data** (Fold backlash).
- **iOS support for SMS-style auto-tracking** ("Launch app for IOS too", axio).
- **Tax-section tagging (80C/80D)** ([MoonProduct](https://www.moonproduct.in/insights/upi-expense-tracker-india-2026/)).
- **UPI AutoPay mandate visibility** (the UPI Tracker newcomer's thesis).

---

## India enablers & constraints

### RBI Account Aggregator (AA) framework
- **Structure.** AAs are RBI-licensed NBFC-AAs that route encrypted, consented data from **FIPs** (banks, AMCs/RTAs, insurers, NPS) to **FIUs** without being able to read it. The framework went live on 2 Sep 2021 ([PIB 2021](https://www.pib.gov.in/PressReleaseIframePage.aspx?PRID=1753713)). Main AAs include Finvu (Finfactor), OneMoney, Setu AA (Pine Labs), Anumati (Perfios), CAMS Finserv and Saafe (NeSL). There are 13+ licensed AAs in all ([productgrowth.in](https://productgrowth.in/tools/banking-api/finvu/)).
- **Scale (Sahamati, 2 Sep 2026).** **500M+ fulfilled consents, 1,100+ regulated entities live, 2.8B+ accounts enabled, 310M+ accounts linked, 600 crore+ data fetches.** FY26 AA-enabled lending was ₹3.82 lakh crore. "Lending continues to be the largest use case." **RBI recognised Sahamati as the SRO for AA in June 2026** ([Sahamati](https://sahamati.org.in/media-article/account-aggregator-crosses-500-million-consents-as-india-marks-5th-aa-foundation-day/); [RBI PR, 5 Jun 2026](https://rbi.org.in/Scripts/BS_PressReleaseDisplay.aspx?prid=62870)). Sahamati says 130+ FIPs are connected to one or more AAs ([FIP-AA mapping](https://sahamati.org.in/fip-aa-mapping)).
- **Data types live (Sahamati, last updated Dec 2024).** Live: deposits, RD, term deposits, SIP, equities, MF, ETF, REIT/InvIT, GST returns, general and life insurance, NPS. **EPF and PPF are "Proposed"**, and so are bonds and G-secs. **Credit cards are not a listed FI type** ([Sahamati FI types](https://sahamati.org.in/data-fi-types-available-on-aa/)). This is why Fold parses card emails ([Fold](https://fold.money/blog/privacy-by-design)). Sahamati says GST, CBDT and EPFO sources are next.
- **FIU eligibility.** An FIU must be **regulated by RBI, SEBI, IRDAI or PFRDA**: banks, NBFCs, payment-system entities, SEBI RIAs, RAs, PMS, brokers, IRDAI corporate agents and brokers, PFRDA PoPs ([Setu docs](https://docs.setu.co/data/account-aggregator/licenses-and-go-live/licenses); [DFS](https://financialservices.gov.in/account-aggregator-framework); [Digio](https://documentation.digio.in/fiu-tsp/how_to_become_a_fiu)). An unregulated startup **cannot onboard directly**: "If you're pre-seed and not yet registered, no AA can onboard you" ([productgrowth.in](https://productgrowth.in/tools/banking-api/finvu/)).
  - The common PFM route is a **SEBI Investment Adviser registration**. Fold did this (INA000018072). Sahamati's FIU list is full of small RIAs that are "Live", for example Neogentle Advisory, Salaryse Technologies and Worthos Technologies ([Sahamati FIU list, Aug 2026](https://sahamati.org.in/fip-fiu-in-account-aggregators-ecosystem)). Another option is partnering with a regulated FIU as a TSP [inference].
- **Cost.** **No AA publishes a rate card.** Pricing is negotiated enterprise pricing per consent or fetch ([Finvu pricing page](https://finvu.in/pricing/index.html); [productgrowth.in](https://productgrowth.in/tools/banking-api/finvu/)). Per-fetch unit prices are **[unverified]**. On top of that come RIA registration, compliance, ISO 27001 (Fold, CRED) and TSP fees (Setu, Digio, FinBox).
- **Consent UX and reliability.** Users approve a consent artefact (purpose, FI types, date range, frequency, expiry) on the AA's screen after a bank OTP. Sahamati and IDEO studied how to keep this flow free of "cognitive overload" ([Sahamati/IDEO PDF](https://sahamati.org.in/wp-content/uploads/2023/05/IDEO-LMM-Sahamati-Collab-on-AA-Flow-v1-compressed.pdf)). "OTP-based bank authentication still produces consent-stage drop-offs" ([productgrowth.in](https://productgrowth.in/tools/banking-api/finvu/)). Real-world quirks from Fold's help centre:
  - only individual savings accounts are supported (no joint or current)
  - the mobile number must match the bank's records
  - SBI linking works best 5–8 AM; Kotak before 9 AM or after 9 PM
  - HDFC data arrives ~12h late; ICICI sends nothing on weekends
  - duplicates and timestamp errors occur
  - Fold rations refreshes (free tier: 5/month), suggesting fetch costs or rate limits [inference]

### Google Play SMS / Call Log policy
- **Default rule.** SMS and Call Log permissions are restricted to apps registered as the **default SMS, Phone or Assistant handler** ([Play policy](https://support.google.com/googleplay/android-developer/answer/10208820?hl=en)).
- **Exception that matters.** A temporary exception lists **"SMS-based money management — e.g., apps that track and manage budget"**, with eligible permissions `READ_SMS, RECEIVE_MMS, RECEIVE_SMS, RECEIVE_WAP_PUSH`. It applies only when this is **core functionality** and "there's currently no alternative method". It is "Subject to Google Play review and approval" and requires the **Permissions Declaration Form** ([same](https://support.google.com/googleplay/android-developer/answer/10208820?hl=en)). Apps without a valid declaration "may be removed from Google Play".
- **Spyware/exfiltration clarification (10 Apr 2025).** Google clarified "restrictions related to financial apps accessing users' SMS history" ([announcement](https://support.google.com/googleplay/android-developer/answer/15899442?hl=en-GB)). The policy now says: "personal loans or budgeting apps may not exfiltrate or share non-financial or personal SMS history of a user". "Research (like market research based on SMS)" and "Any transfer that results in a sale of this data (including SDKs…)" are invalid uses. Prominent disclosure and consent are required ([policy](https://support.google.com/googleplay/android-developer/answer/10208820?hl=en)).
- **Upcoming (effective 27 Jan 2027).** Call-log account verification is removed as a use case. Use the SMS Retriever / Digital Credentials API for OTP ([preview](https://support.google.com/googleplay/android-developer/answer/17225965?hl=en)).
- **Personal loan app policy** was expanded to cover line-of-credit apps (Apr 2025 announcement). RBI DL Directions separately forbid lending apps from accessing contacts, call logs and media (see below). This is why axio lists *separate* SMS justifications per feature.
- **Impact.** An SMS-parsing tracker is viable on Android **only if** budgeting is the declared core function and parsing is filtered to financial senders. A strong design uploads nothing but parsed transactions, or nothing at all. iOS has no equivalent API.
- **Notification-listener parsing** (FinArt) is an alternative capture channel. It uses `NotificationListenerService` access granted by the user, which is not in the SMS permission group. Play's treatment of it falls under the general sensitive-permissions and prominent-disclosure rules **[unverified for specific finance guidance]**.

### UPI volume and how users track UPI
- **Aug 2026: record 24.51B transactions, ₹29.82 lakh crore.** Sept 2026: 24.07B, with daily average volume at a record **802M/day** ([NPCI](http://npci.org.in/product/upi/product-statistics); [Business Standard](https://www.business-standard.com/finance/news/upi-transactions-august-2026-record-volume-npci-126090100506_1.html); [TNIE](https://www.newindianexpress.com/business/2026/Oct/01/upi-transaction-volume-value-falls-by-15-to-rs-2937-lakh-crore-in-september-ahead-of-mdr)). H1 FY27 volume was 145B, up 27% ([The Hindu](https://www.thehindu.com/business/upi-volume-jumps-27-to-touch-145-bn-in-first-half/article71538372.ece)).
- **New: UPI MDR from 15 Oct 2026.** 0.4% on P2M payments above ₹2,000, paid by merchants, not consumers ([The Hindu](https://www.thehindu.com/business/Economy/mdr-apprehension-unlikely-to-spur-higher-cash-usage-rbi-deputy-governor/article71482058.ece)). This may nudge some merchants toward cash or surcharges [inference].
- **Typical user pattern.** 30–100 small UPI payments a week against 5–15 card transactions ([MoonProduct](https://www.moonproduct.in/insights/upi-expense-tracker-india-2026/)) [vendor estimate].
- **How users track today:**
  1. bank SMS parsed by axio, FinArt or Moneyview (Android)
  2. AA (Fold, CRED Money, Jupiter), which lags hours to a day
  3. the UPI app's own history (Paytm Spend Summary), siloed to one app
  4. spreadsheets or manual entry ([Substack](https://lavsmohan.substack.com/p/an-expense-tracker-that-doesnt-suck))
- **UPI on credit** (RuPay credit cards on UPI, credit lines) blurs bank-vs-card attribution.

### Bank SMS / email alert formats
- **Sender IDs.** Since **6 May 2025**, TRAI requires every commercial SMS header to carry a suffix: **-P promotional, -S service, -T transactional (OTPs, bank alerts), -G government**. The form is `<operator><circle>-<HEADER>-<suffix>`, for example `JD-ABCXYZ-S` ([ET, May 2025](https://m.economictimes.com/news/new-updates/fed-up-with-pesky-sms-now-you-can-spot-spam-promo-or-important-messages-by-checking-the-p-s-t-or-g-suffix/articleshow/121100846.cms); [TRAI PR 11/2025](https://trai.gov.in/sites/default/files/2025-02/PR_No.11of2025.pdf)). TRAI publishes the registered header list ([TRAI](http://www.trai.gov.in/node/7411)). Parsers must key on the *header root* (e.g., `DCBANK`), not the whole sender ID. axio broke when `VD-DCBANK-S` became `AD-DCBANK-T`.
- **Body formats** vary by bank and change without notice. They contain masked account or card numbers ("partial account numbers… as sent by banks", [Moneyview FAQ](https://moneyview.in/money-view-app-faq)), amount, UPI ref/RRN, payee VPA or merchant, and sometimes the balance. A typical template looks like "Rs.250.00 debited from A/c XX1234 on 03-Oct-26 to VPA xyz@okicici (UPI Ref 6271…)" [illustrative; not a verbatim bank SMS]. Some banks are moving alerts to email or app push, and SMS-only trackers then "silently miss transactions" ([FinArt](https://finart.app/best-expense-tracker-apps-india)) [vendor claim].
- **Email** is the only reliable real-time source for **credit cards** that is not SMS (Fold's approach: on-device Gmail parsing, with forwarding as an alternative). Users with non-Gmail setups hit loops: "I already have an email forwarding from Gmail… infinite loop" (Fold review).

### Credit-card statement tracking
- AA has no credit-card FI type (above). The options are SMS alerts, email alerts, monthly PDF statements, which are password-protected and in bank-specific layouts, or CRED/bank apps.
- Edge cases users hit: add-on cards merged into the primary statement (ICICI Coral/Sapphiro), card bill payments double-counted as spend, and EMI conversions [inference from duplicate complaints]. Credit-card spend was ₹1.97 trillion in April 2026 ([Angel One/RBI data](https://www.angelone.in/news/economy/india-s-credit-card-spends-rise-7-to-1-97-trillion-in-april-2026-rbi-data)).

### Cash usage
- RBI Deputy Governor Murmu (Aug 2026): "Currency in circulation continues to grow at double-digit rates even as cash's share of individual transactions declines". There are ~176B banknotes in circulation. "cash remains a significant mode of payment" ([CNBC-TV18](https://www.cnbctv18.com/business/finance/rbi-flags-cash-paradox-currency-in-circulation-grows-despite-digital-payments-boom-19971770.htm)). Any India tracker needs first-class cash: ATM withdrawal becomes a cash wallet, and cash spends draw it down. Moneyview's FAQ models exactly this ([FAQ](https://moneyview.in/money-view-app-faq)).

### DPDP Act 2023 & DPDP Rules 2025
- **The Rules were notified 13/14 Nov 2025** ([PIB](https://www.pib.gov.in/PressReleasePage.aspx?PRID=2190014&lang=2&reg=3)). Phasing: the Data Protection Board and core provisions apply immediately. **Consent-manager provisions come after 12 months (~Nov 2026)**. Most substantive fiduciary obligations apply **after 18 months, ~13 May 2027** ([Grant Thornton](https://www.grantthornton.in/globalassets/1.-member-firms/india/assets/pdfs/flyers/dpdpa-rules-detailed-brochure_final-25th-november-2025-1.pdf); [privybyidfy](https://www.privybyidfy.com/blog/dpdp-compliance-guide-2026-what-indian-enterprises-must-do-before-may-2027); KPMG roadmap: "Act Enforcement May 2027", [KPMG](https://assets.kpmg.com/content/dam/kpmgsites/in/pdf/2025/12/dpdp-act-and-rules-compliance-roadmap.pdf)). The 13 May 2027 date is computed by commentators "until officially confirmed" ([dpdprules.org](https://dpdprules.org/rules/1)).
- **Key obligations** (KPMG roadmap):
  - consent must be "freely given, specific, informed, unambiguous", with itemised notice and per-purpose consent
  - users can withdraw consent as easily as they gave it; rights to access, correction, erasure and nomination
  - breach notice to the Board and users "without delay", with details **within 72 hours**
  - personal data, logs and traffic retained **1 year**
  - grievance response **within 90 days**
  - consent managers keep consent records **7 years**
  - penalties **up to ₹250 crore** for security-safeguard failures; ₹200 crore for children's-data breaches; ₹150 crore for SDF breaches
  - notices must be available in English or any **Eighth Schedule language** (Act s.5(3)) [statutory provision; cite Act text when drafting]
- **Implication.** SMS or email parsing collects highly sensitive data, and purpose limitation is strict. Using tracker data to market loans needs separate, specific consent. That is exactly the practice users already hate.

### RBI Digital Lending Directions 2025 (if we ever add credit)
- RBI consolidated the rules on **8 May 2025** ([RBI](https://www.rbi.org.in/scripts/NotificationUser.aspx?Id=12848&Mode=0)). Para 12: DLAs must "desist from accessing mobile phone resources like file and media, contact list, call logs, telephony functions". Only one-time camera/mic/location access is allowed, for KYC. Data collection must be "need-based and with prior and explicit consent… having audit trail". Para 13: "all data is stored only in servers located within India". Data processed abroad must be deleted there and brought back **within 24 hours**. Lending apps must be reported to CIMS and appear in the RBI DLA directory ([PIB](https://www.pib.gov.in/PressReleasePage.aspx?PRID=2287195&lang=1&reg=3)).
- **Implication.** Embedding loans brings in RBI's DLA regime and India data localisation. It also breeds the distrust seen with axio and Moneyview. Our differentiator should be **no lending**.

### Regional languages & INR formatting
- India uses the **lakh/crore** grouping, e.g. ₹12,34,567.89. Media and users talk in "₹29.82 lakh crore", "1Cr+" and "2.65L reviews". Even Play shows "2.65L reviews" and "1Cr+" in the en_IN locale ([axio Play](https://play.google.com/store/apps/details?id=com.daamitt.walnut.app&hl=en_IN)). The UI should use `en-IN` grouping and compact L/Cr.
- Regional-language tracking is a stated differentiator only for small newcomers (Trakio "10 languages"; [trakio.co.in](https://www.trakio.co.in/)). Bank SMS are mostly English, but some banks send Hindi or regional templates [unverified per bank]. DPDP notices must be offered in scheduled languages on request (above).

---

## India-specific requirements (concrete implications for our product)

1. **Hybrid capture with no single point of failure.** Combine (a) Android SMS parsing under Play's "SMS-based money management" exception, (b) optional notification-listener capture, (c) optional on-device email parsing for credit cards, (d) statement PDF/CSV import (password-protected PDFs), and (e) fast manual and cash entry. Treat AA as a **phase-2 add-on**, because it needs SEBI RIA or other regulated FIU status, or a regulated partner, plus negotiated per-consent costs.
2. **On-device parsing by default.** Parse SMS and email on the phone and upload only structured transactions, or nothing at all with an optional E2E-encrypted backup to the user's own Drive or iCloud. Apply a sender allowlist using the TRAI `-T`/`-S` suffixes plus bank header roots. Never read OTPs or personal SMS. State this in the permission rationale screen; it directly answers the 137-upvote "OTP access" fear. Never use third-party SDKs that touch SMS.
3. **Parser resilience as a product feature:**
   - match on the header root, not the full sender ID
   - versioned parser rules shipped OTA (no app update needed)
   - a one-tap "this SMS wasn't detected" reporting path with visible turnaround
   - a monthly "coverage check" that compares the app's balance with the latest SMS balance
   - alerts when a bank's format changes
4. **De-duplication engine.** Match on UPI RRN or ref number, amount, timestamp window and account mask across SMS, notification, email and AA. Pair card spends with card bill payments so the bill is a *transfer*, not spend. Pair failed UPI payments with their refunds. Show a "possible duplicates" review queue instead of hiding entries.
5. **UPI-native categorisation:**
   - VPA→merchant mapping (`razorpay@`, `paytm-…@`, `@ok*`) with user rename rules that apply retroactively
   - P2P vs P2M detection, so transfers to people default to *transfer*, with an option to split them or mark them as rent
   - micro-payment roll-ups (chai, auto)
   - payment-source attribution: bank, credit card, RuPay-credit-on-UPI, wallet or cash
   - a view of **UPI AutoPay mandates and subscriptions**
6. **First-class cash.** ATM withdrawals create a cash balance, and quick cash entry draws it down. A home-screen widget or quick-add should save an entry in under 2 seconds.
7. **Credit-card module.** Statement and due-date tracking, add-on card handling, EMI tracking, and bill reminders. Bill reminders are the #1 adoption driver.
8. **iOS strategy.** iOS cannot read SMS. Offer (a) a guided Shortcuts automation for bank SMS (as Finzo, Xyra and FinArt do), (b) email parsing, (c) statement import, and (d) AA later. Make cross-platform sync a core feature; it's a top complaint for Money Manager and axio.
9. **No lending, no ads in core.** Make "we will never sell loans or your data" an explicit, auditable promise. Monetise through a fair subscription. Keep **CSV/Excel export, backup and restore free** forever. Announce price changes in advance with no silent renewal hikes, and offer a **lifetime option at a sane price**. Fold's ₹25k Believer plan drew backlash.
10. **Portability and longevity guarantees.** One-tap full export, documented open format, and an escrow-style "if we shut down, here's your data" promise. Fi, Walnut→axio and Kuvera→CRED make this a real trust lever.
11. **DPDP-ready from day one** (deadline ~May 2027, earlier for consent managers):
    - itemised, per-purpose consent screens in English plus Eighth Schedule languages
    - in-app withdrawal of consent and account deletion without contacting support
    - 72-hour breach runbook; 1-year log retention; 90-day grievance SLA with a named grievance officer
    - India data residency (AWS ap-south-1), which also eases any future AA/FIU path
12. **If pursuing AA.** Budget for SEBI RIA registration (Fold's path) or a partnership with a regulated FIU. Integrate through a multi-AA gateway (Setu, Finvu) for routing and fallbacks. Design around:
    - bank quirks such as weekend gaps, delayed data and linking-time windows
    - missing support for joint and current accounts
    - credit cards and EPF/PPF not being available via AA
    - visible "last synced" timestamps per account
13. **Performance as a wedge.** Competitors are criticised for bloat (CRED, Kuvera, Spendee, axio). Target cold start under 1s, offline-first local DB, instant search across years, and no heavy animations.
14. **INR and Indian conventions.** Use lakh/crore grouping and compact ₹1.2L / ₹3.4Cr. Default to calendar month with a salary-cycle month option. Support Indian financial-year (Apr–Mar) reports and tax tags (80C, 80D, HRA rent receipts).
15. **Family and couples.** Shared household budgets with per-member privacy are explicitly requested (FinArt and Fold Spaces exist but are paid). Splitwise's daily caps and ads open room for built-in splitting that settles via UPI deep links.
16. **Human support plus status transparency.** Publish a "known bank parser issues" page, as Fold does, with fix ETAs. Offer real-human support on a paid tier at minimum.

---

## Sources
- axio Play: https://play.google.com/store/apps/details?id=com.daamitt.walnut.app&hl=en_IN
- Axio Money (unrelated iOS): https://apps.apple.com/us/app/axio-money-expense-tracker/id6780629197
- Capital Float–Walnut: https://www.medianama.com/2018/08/223-capital-float-acquires-walnut/
- Fold Play: https://play.google.com/store/apps/details?hl=en_US&id=money.fold.marble
- Fold App Store: https://apps.apple.com/in/app/fold-money/id1626994334
- Fold pricing blog: https://fold.money/blog/price-of-free-software
- Fold privacy blog: https://fold.money/blog/privacy-by-design
- Fold known issues: https://help.fold.money/hc/en-us/articles/17395308835986-Known-Issues-Banks
- Fold linking help: https://help.fold.money/hc/en-us/articles/17257682061458-Why-can-t-Fold-find-or-link-my-bank-account
- Fold MCP: https://fold.money/mcp
- FinArt comparison: https://finart.app/best-expense-tracker-apps-india
- MoonProduct UPI guide: https://www.moonproduct.in/insights/upi-expense-tracker-india-2026/
- ET Money: http://en.wikipedia.org/wiki/ET_Money ; https://www.etmoney.com/help/others/expense-manager
- Moneyview: https://moneyview.in/ ; https://moneyview.in/money-view-app-faq ; https://apps.apple.com/in/app/moneyview-personal-loan-app/id6468976019
- Jupiter Money: https://jupiter.money/money
- Fi: https://economictimes.indiatimes.com/tech/technology/neobank-fi-money-shuts-down-banking-services/articleshow/129470782.cms ; https://fi.money/ ; https://inc42.com/buzz/cash-strapped-fi-money-pivots-to-ai-led-b2b-play ; https://inc42.com/buzz/fi-money-cofounder-sumit-gwalani-quits-amid-mounting-financial-struggles ; https://www.livemint.com/companies/start-ups/neobank-fi-money-shuts-down-some-features-trims-workforce-to-stay-afloat-funding-crunch-cost-cutting-11752136900892.html ; https://www.desidime.com/discussions/fi-money-is-dying-funds-are-only-left-for-10-months-better-to-close-the-account
- CRED Money: https://techcrunch.com/2024/07/24/cred-launches-personal-finance-manager-for-indias-affluent ; https://cred.club/money
- Paytm Spend Summary: https://paytm.com/blog/payments/paytm-spend-summary-upi-expense-tracker
- Kuvera: https://play.google.com/store/apps/details?hl=en_US&id=com.gooogle.android.kuvera.app ; https://www.reddit.com/r/personalfinanceindia/comments/1shy50i/cred_killed_kuvera_where_are_you_moving_to/
- Bachat: https://platform.tracxn.com/a/d/company/5ced0cadf036855e46f84721/bachat ; https://platform.tracxn.com/a/d/company/58988956e4b08b8241307e7f/bachat%20app ; https://bachatai.com/
- Newcomers: https://www.trakio.co.in/ ; https://monai.in/ ; https://expenseai.co.in/ ; https://www.upitracker.com/ ; https://inly.digital/ ; https://finzo.money/ ; https://xyratrack.in/ ; https://spendbyme.com/ ; https://getfinny.app/ ; https://github.com/OmkarPathak/TrackMyRupee
- Reddit threads: https://www.reddit.com/r/personalfinanceindia/comments/1kicolh/expense_tracking_app_any_axio_alternatives/ ; https://www.reddit.com/r/personalfinanceindia/comments/1t94luo/i_built_a_free_walnut_replacement_thats_100/ ; https://www.reddit.com/r/personalfinanceindia/comments/1kxm591/fold_is_the_best_expense_tracker_ive_tried_but_is/ ; https://www.reddit.com/r/personalfinanceindia/comments/1v4gfhz/best_app_to_track_your_monthly_expenses_without/ ; https://www.reddit.com/r/CreditCardsIndia/comments/1uq0j5k/built_a_local_credit_card_expense_tracker_because/ ; https://www.reddit.com/r/personalfinanceindia/comments/1vwvwv1/jupiter_no_longer_feels_neobank_experience_after
- Other Play listings used for review scraping: com.smartspends, in.indwealth, money.jupiter, com.dreamplug.androidapp, com.finart, com.realbyteapps.moneymanagerfree, com.cleevio.spendee, com.Splitwise.SplitwiseMobile, com.google.android.apps.nbu.paisa.user, com.phonepe.app, net.one97.paytm, com.whizdm.moneyview.loans
- Sahamati 500M consents: https://sahamati.org.in/media-article/account-aggregator-crosses-500-million-consents-as-india-marks-5th-aa-foundation-day/
- Sahamati FIP/FIU list: https://sahamati.org.in/fip-fiu-in-account-aggregators-ecosystem
- Sahamati FI types: https://sahamati.org.in/data-fi-types-available-on-aa/
- Sahamati FIP–AA mapping: https://sahamati.org.in/fip-aa-mapping
- Sahamati/IDEO consent flow: https://sahamati.org.in/wp-content/uploads/2023/05/IDEO-LMM-Sahamati-Collab-on-AA-Flow-v1-compressed.pdf
- RBI SRO recognition: https://rbi.org.in/Scripts/BS_PressReleaseDisplay.aspx?prid=62870
- Setu licenses: https://docs.setu.co/data/account-aggregator/licenses-and-go-live/licenses
- DFS AA framework: https://financialservices.gov.in/account-aggregator-framework
- Digio FIU guide: https://documentation.digio.in/fiu-tsp/how_to_become_a_fiu
- Finvu profile/pricing: https://productgrowth.in/tools/banking-api/finvu/ ; https://finvu.in/pricing/index.html
- Google Play SMS policy: https://support.google.com/googleplay/android-developer/answer/10208820?hl=en
- Play policy announcement 10 Apr 2025: https://support.google.com/googleplay/android-developer/answer/15899442?hl=en-GB
- Play policy preview (Jan 2027): https://support.google.com/googleplay/android-developer/answer/17225965?hl=en
- NPCI UPI stats: http://npci.org.in/product/upi/product-statistics
- UPI Aug 2026: https://www.business-standard.com/finance/news/upi-transactions-august-2026-record-volume-npci-126090100506_1.html
- UPI Sep 2026 / MDR: https://www.newindianexpress.com/business/2026/Oct/01/upi-transaction-volume-value-falls-by-15-to-rs-2937-lakh-crore-in-september-ahead-of-mdr
- UPI H1: https://www.thehindu.com/business/upi-volume-jumps-27-to-touch-145-bn-in-first-half/article71538372.ece
- UPI MDR: https://www.thehindu.com/business/Economy/mdr-apprehension-unlikely-to-spur-higher-cash-usage-rbi-deputy-governor/article71482058.ece
- Cash paradox: https://www.cnbctv18.com/business/finance/rbi-flags-cash-paradox-currency-in-circulation-grows-despite-digital-payments-boom-19971770.htm
- Credit card spends: https://www.angelone.in/news/economy/india-s-credit-card-spends-rise-7-to-1-97-trillion-in-april-2026-rbi-data
- TRAI SMS suffix: https://m.economictimes.com/news/new-updates/fed-up-with-pesky-sms-now-you-can-spot-spam-promo-or-important-messages-by-checking-the-p-s-t-or-g-suffix/articleshow/121100846.cms ; https://trai.gov.in/sites/default/files/2025-02/PR_No.11of2025.pdf ; http://www.trai.gov.in/node/7411
- DPDP: https://www.pib.gov.in/PressReleasePage.aspx?PRID=2190014&lang=2&reg=3 ; https://assets.kpmg.com/content/dam/kpmgsites/in/pdf/2025/12/dpdp-act-and-rules-compliance-roadmap.pdf ; https://www.grantthornton.in/globalassets/1.-member-firms/india/assets/pdfs/flyers/dpdpa-rules-detailed-brochure_final-25th-november-2025-1.pdf ; https://www.privybyidfy.com/blog/dpdp-compliance-guide-2026-what-indian-enterprises-must-do-before-may-2027 ; https://dpdprules.org/rules/1
- RBI Digital Lending Directions 2025: https://www.rbi.org.in/scripts/NotificationUser.aspx?Id=12848&Mode=0 ; https://www.pib.gov.in/PressReleasePage.aspx?PRID=2287195&lang=1&reg=3
- iOS Shortcuts limits: https://developer.apple.com/forums/thread/705659 ; https://support.apple.com/guide/shortcuts/transaction-trigger-apd65c67538a/ios
- Blogger/forum context: https://lavsmohan.substack.com/p/an-expense-tracker-that-doesnt-suck/comments ; https://www.teamblind.com/post/best-free-expense-tracker-app-kqt0fxjh

# Global (US/EU) bank-sync & budgeting apps: competitive research

Prepared 2026-10-05. Scope: YNAB, Monarch Money, Copilot Money, Rocket Money, Quicken Simplifi, Quicken Classic, EveryDollar, PocketGuard, Empower (formerly Personal Capital), Mint (shut down) and Credit Karma, Tiller, Lunch Money, Actual Budget, Goodbudget, Origin, plus new entrants from 2025–2026.

## Method & evidence notes
- **App Store data**: ratings and counts come from the Apple iTunes Lookup/Search API, US storefront, pulled 2026-10-05. For each app I also pulled the 250 most-recent US reviews from the App Store customer-reviews RSS feed (`itunes.apple.com/us/rss/customerreviews/...sortby=mostrecent`). Unless another link is given, quotes in "Drawbacks" are from these feeds. The listed URL is the app's public App Store reviews page, and review dates are mostly Jul–Oct 2026.
- **Google Play data**: ratings were scraped from the Play listing pages (`aria-label` star rating) on 2026-10-05.
- **Reddit**: reddit.com blocks direct reading, so Reddit sentiment comes from search snippets and secondary write-ups (e.g. NerdWallet's Reddit synthesis). These are flagged where used.
- **Recent-review negativity**: the "share of ≤2★ reviews among the 250 most recent US reviews" is a rough signal of current pain. Recent reviews skew negative compared with lifetime averages, because happy users are prompted in-app while angry users self-select. Read the figures comparatively, not absolutely.

| App (iOS id) | ≤2★ share of last 250 US reviews | Keyword hits within ≤2★ reviews (rough regex: sync/connect · categorization · price/charges · update/UI · support) |
|---|---|---|
| YNAB (1010865877) | 40% (101) | 21 · 11 · 29 · **63** · 9 |
| Monarch (1459319842) | 27% (68) | **24** · 9 · 22 · 16 · 18 |
| Copilot (1447330651) | 32% (81) | **26** · 13 · 28 · 26 · **29** |
| Rocket Money (1130616675) | **60%** (150) | 16 · 2 · **84** · 12 · 12 |
| Simplifi (1449777194) | 49% (123) | 32 · **22** · 38 · **47** · **33** |
| PocketGuard (949414211) | 57% (143) | **43** · 19 · **58** · 38 · **38** |
| EveryDollar (942571931) | 34% (84) | 14 · 5 · 25 · **40** · 15 |
| Origin (1637693312) | 15% (31 of 200) | 5 · 0 · 15 · 10 · 8 |
| Goodbudget (471112395) | (119 low among reviews dating back to 2025) | 21 · 5 · 33 · 27 · 11 |
| Quicken Classic companion (701067522) | (171 low) | **51** · 9 · 21 · **84** · 20 |

---

## Apps covered

### Summary table

| App | Platforms | Pricing (2026) | Rating | Data entry model | Standout features |
|---|---|---|---|---|---|
| **YNAB** | iOS, Android, Web, Apple Watch | $14.99/mo or $109/yr. 34-day trial; free for college students for 1 yr ([NerdWallet](https://www.nerdwallet.com/finance/learn/best-budget-apps)) | iOS 4.79 (61.8K), Play 4.6 (25.3K) | Bank sync (US/CA/EU/UK via aggregators), manual entry, file import | Zero-based "give every dollar a job" method; credit-card handling; YNAB Together (up to 5 users); loan simulator; huge education ecosystem |
| **Monarch Money** | iOS, Android, Web (Mac via iPad app) | Core $14.99/mo or $99.99/yr. **Plus $199/yr**, launched Apr 2026 ([Monarch](https://www.monarch.com/blog/monarch-plus)). 7-day trial | iOS 4.89 (112.7K), Play 4.8 (25.6K) | Bank sync through 3 providers (Plaid, Mastercard/Finicity, MX), manual entry, CSV/Mint import | Household sharing at no extra cost; flex vs category budgets; rules; reports; investments; AI assistant; Plus adds forecasting, business P&L/Schedule C, Morningstar fund analysis |
| **Copilot Money** | iPhone, iPad, Mac, **Web (since Dec 2025)**. **No Android** | $13/mo or $95/yr. 1-month trial; no free tier ([DollarScout](https://www.dollarscout.net/personal-finance/reviews/copilot)) | iOS 4.75 (30.5K) | Bank sync (Plaid + fallback), direct Apple Card/Venmo/Coinbase integrations, manual accounts | Best-in-class design; review queue; ML categorization; recurring detection; investments and net worth; US-only |
| **Rocket Money** | iOS, Android, Web | Free tier. Premium is pay-what-you-want at $7–14/mo. Premium+ adds the "Rowan" AI assistant. Bill negotiation charges **35–60% of first-year savings** ([Rocket Money](https://www.rocketmoney.com/learn/personal-finance/how-much-does-rocket-money-cost), [FAQ](https://www.rocketmoney.com/faq)) | iOS 4.48 (396K), Play 4.5 (140K) | Bank sync | Subscription detection and concierge cancellation; bill negotiation; credit score; automated savings |
| **Quicken Simplifi** | iOS, Android, Web | List ~$5.99–6.99/mo billed annually, often 50% off in year 1, with a ~100% jump at renewal ([checkthat.ai](https://checkthat.ai/brands/quicken/pricing), [frontdeskreview](https://frontdeskreview.com/software/budgeting-apps/quicken-simplifi)) | iOS 4.54 (11.8K), Play 4.6 (5.0K) | Bank sync (Quicken connectivity), manual entry | "Spending Plan" (what's left after bills); projected cash flow; watchlists; PCMag Editors' Choice ([PCMag](https://www.pcmag.com/reviews/simplifi-by-quicken)) |
| **Quicken Classic** | Windows/Mac desktop, plus a companion mobile/web app | Deluxe $2.99/mo yr-1, renewing at $4.99/mo. Premier $4.19/mo, renewing at $6.99/mo (+67% at renewal) ([geniusfirms](https://www.geniusfirms.com/blog/quicken-software-2026-full-pricing-and-plan-comparison/), [fincomparelab](https://www.fincomparelab.com/reviews/quicken-classic-review/)) | Companion app: iOS 4.64 (14.9K), **Play 2.5** (4.5K) | Bank sync (Direct Connect/Express Web Connect), QFX/CSV import, manual entry | Deepest feature set: investments with lots and cost basis, bill pay, reports, decades of history, local data file |
| **EveryDollar** (Ramsey) | iOS, Android, Web | Free (manual only). Premium $17.99/mo or $79.99/yr; 14-day trial ([EveryDollar help](https://everydollar.help.ramseysolutions.com/hc/en-us/articles/21544207900685-EveryDollar-Premium-Subscription-Cost)) | iOS 4.73 (84.1K), Play 4.3 (15.5K) | Manual (free); bank sync on Premium (Plaid / Mastercard) | Simple zero-based monthly budget; Jan 2026 relaunch added "margin finder", daily lessons, group coaching ([NerdWallet](https://www.nerdwallet.com/finance/learn/best-budget-apps)) |
| **PocketGuard** | iOS, Android, Web, Apple Watch | Plus $12.99/mo or $74.99/yr, plus a lifetime option; 7-day trial; no free tier ([NerdWallet](https://www.nerdwallet.com/finance/learn/best-budget-apps), [PocketGuard](https://pocketguard.com/blog/pocketguard-vs-ynab)) | iOS 4.58 (7.7K), Play 4.5 (2.9K) | Bank sync, import/export | "Leftover" (safe-to-spend); debt payoff planner; "Pace" overspending alert (iOS only); subscription tracking |
| **Empower** (ex-Personal Capital) | iOS, Android, Web | Free dashboard. Revenue from advisory (~0.49–0.89% AUM) ([Kubera](https://www.kubera.com/blog/personal-capital-alternative)) | iOS 4.78 (416K, combined Empower retirement app) | Bank/brokerage sync | Net worth, investment checkup, fee analyzer, retirement planner. The standalone dashboard was folded into the main Empower app in late 2025 |
| **Mint** (Intuit) | Shut down **March 23, 2024** ([CNBC](https://www.cnbc.com/select/mint-budgeting-app-is-going-away-here-are-some-alternatives)) | Was free and ad/referral-funded | — | Bank sync | Free all-in-one budgets, bills, net worth |
| **Credit Karma** (Mint's "successor") | iOS, Android, Web | Free; lead-gen/ads | iOS 4.84 (7.75M), Play 4.7 (3.24M) | Bank sync | Credit scores; net worth; spending insights. **No category budgets** ([getitplanned](https://www.getitplanned.com/blog/mint-alternatives-budgeting-apps-2026), [CNBC](https://www.cnbc.com/select/mint-budgeting-app-is-going-away-here-are-some-alternatives)) |
| **Tiller** | Google Sheets, Excel (still needs a Google sign-in) | $79/yr, annual only; 30-day trial ([Rob Berger](https://robberger.com/tiller-money-review/), [personalfinanceapp.net](https://personalfinanceapp.net/tools/tiller-money/)) | n/a (no meaningful app) | Bank feeds (Yodlee + added aggregators) into a spreadsheet | Full spreadsheet control; community templates; AutoCat rules |
| **Lunch Money** | Web-first. Native iOS/Android apps are newer (iOS id 6739028463) | Pay-what-you-want, minimum $60/yr from 15 Mar 2026, or $10/mo; early users locked at $50/yr ([borderlessbudget](https://borderlessbudget.com/compare/lunch-money-alternative), [College Investor](https://thecollegeinvestor.com/45433/lunch-money-review/)) | iOS 4.75 (55 US; 46 CA), Play 4.6 (~35–39) | Plaid sync, CSV import, developer API | **160+ currencies with historical FX per transaction**; rules engine; API; indie-built |
| **Actual Budget** | Desktop (Win/Mac/Linux), Web/PWA; self-hosted sync server | Free (MIT). Optional SimpleFIN ~$15/yr; hosting a few $/mo ([Finny review](https://getfinny.app/blog/actual-budget-review-2026), [openbankingtracker](https://www.openbankingtracker.com/guides/free-open-banking-apis)) | n/a | Manual, CSV/OFX/QIF import, SimpleFIN (US/CA), GoCardless (closed to new signups since Jul 2025), Enable Banking (EU, experimental) ([Actual docs](https://actualbudget.org/docs/advanced/bank-sync/gocardless/)) | Local-first, offline, instant; encrypted sync; YNAB-style envelopes; you own the data file |
| **Goodbudget** | iOS, Android, Web | Free: 20 envelopes, 1 account, 2 devices, 1 yr history. Premium $10/mo or $80/yr ([costbench](https://costbench.com/software/personal-finance/goodbudget/)) | iOS 4.63 (13.5K), **Play 3.2** (19.2K) | Manual by default; US bank sync on Premium | Envelope budgeting shared across household devices |
| **Origin** | iOS, Android, Web | $99/yr or ~$12.99/mo. Promo: $1 for first year ([spendalyst](https://spendalyst.com/blog/origin-financial-review)) | iOS 4.62 (3.9K) | Bank sync | SEC-registered AI advisor; budgeting + investing + tax filing + estate planning; partner access free |

### Profiles

**YNAB.** The category's "method" product: zero-based envelope budgeting where users assign current cash to categories before spending. It has a cult following ("Worth the learning curve", 5★ App Store review). The price went from $84 to $99 in 2021, then **$109/yr from 1 Aug 2024** for new users and 1 Sep 2024 for existing ones ([Subkept](https://subkept.com/price-hikes/ynab), [r/ynab](https://www.reddit.com/r/ynab/comments/1dsr77q/ynab_price_increasing_to_109_on_august_1_for_new)). Through 2025–2026 YNAB has shipped frequent UI changes, including swipe "transaction cards", a new plan view and plan reset. These dominate current 1★ reviews. Sync works but is not real-time; reviewers quote YNAB's help text saying imports can take up to three days.

**Monarch Money.** The main beneficiary of Mint's shutdown. Subscribers grew **20x** in the year after the shutdown announcement. Monarch raised $75M at an $850M valuation in May 2025 ([CNBC](https://www.cnbc.com/2025/05/23/personal-finance-app-monarch-raises-75-million.html)). It positions itself as an ad-free "Mint on steroids" with household collaboration, three data providers, rules, reports, investments, and an AI assistant. In April 2026 it launched **Monarch Plus ($199/yr)** with forecasting, business tracking and fund analysis, all desktop-web-first. In June 2026 its **MCP connector (for AI tools) was paused** after "one of our data providers" raised a data-portability question ([Monarch help](https://help.monarch.com/hc/en-us/articles/50207234679956-Monarch-MCP-Connector)). This is a live example of aggregators controlling what apps can do with user data.

**Copilot Money.** A design-led, Apple-native tracker known for its fast review queue and ML categorization. It launched a **web app in Dec 2025** ([Copilot help](https://help.copilot.money/en/articles/11780342-copilot-money-for-web)) but still has no Android app, is US-only, and the web app lacks full parity ([DollarScout](https://www.dollarscout.net/personal-finance/reviews/copilot)). 2026 reviews complain about slow feature velocity and an AI agent stuck on a beta waitlist.

**Rocket Money.** A subscription-cancellation and bill-negotiation app (formerly Truebill) with budgeting layered on. The free tier is capped at 2 budgets and 2 custom categories. Net worth and unlimited budgets need Premium ([FAQ](https://www.rocketmoney.com/faq)). It has the largest review base of the budgeting apps, but the **most negative recent sentiment (60% ≤2★ of recent reviews)**, driven by billing and "it couldn't cancel my subscription".

**Quicken Simplifi.** Quicken's modern cloud app and an Editors' Choice at PCMag. Its "Spending Plan" shows what is available after bills. Many ex-Mint users landed here. 2026 reviews report a regression in sync, recurring series and rule quality, plus bot-first support.

**Quicken Classic.** The 40-year-old desktop power tool, now subscription-only. Its local data file and investment-lot tracking are unmatched among mainstream apps. The mobile companion is weak (2.5★ on Play), and promo-to-renewal price jumps of ~67% are resented ([Quicken Community](https://community.quicken.com/discussion/7973246/annual-increase-in-quicken-costs)).

**EveryDollar.** Ramsey Solutions' zero-based app. The free tier is manual-only, and bank sync costs $79.99/yr or a steep $17.99/mo. It was relaunched in Jan 2026 with coaching content. A late-Sep 2026 update left many users stuck on a blank green screen, and features like Cash Flow moved behind the paywall.

**PocketGuard.** A "leftover to spend" app with a debt payoff planner. It has no free tier. Since 2024 its sync reliability and update quality have been criticized heavily (57% of recent reviews ≤2★). An Aug 2026 "Insights" redesign removed the month-over-month category comparison that users relied on.

**Empower (Personal Capital).** A free wealth and net-worth dashboard used as an advisory sales funnel. Users report cold calls once assets pass ~$100K ([Bogleheads](https://www.bogleheads.org/forum/viewtopic.php?t=412933), [Money Under 30](https://www.moneyunder30.com/investing/reviews/empower-personal-capital/)). In late 2025 the standalone dashboard was merged into the main Empower app in a "bumpy transition" ([Kubera, Aug 2026 update](https://www.kubera.com/blog/personal-capital-alternative)). Budgeting is minimal.

**Mint → Credit Karma.** Intuit announced on Nov 2, 2023 that Mint would close ([The Verge](https://www.theverge.com/2023/11/2/23943254/mint-intuit-shutting-down-credit-karma)), and it shut on **Mar 23, 2024**. Users were pushed to Credit Karma, which gained a net-worth feature and spending "insights". However, Intuit's own migration notes said Credit Karma **does not let you set monthly and category budgets** ([getitplanned](https://www.getitplanned.com/blog/mint-alternatives-budgeting-apps-2026)). Forbes Advisor said Mint users "may be disappointed". Wallet Hacks: "Credit Karma is NOT a good alternative to Mint" ([Wallet Hacks](https://wallethacks.com/migrating-from-mint-to-credit-karma-it-was-not-good/)). Users had a limited export window and lost budgets, bill reminders and goals. Departing users scattered to Monarch, Simplifi, Copilot, YNAB, Rocket Money and spreadsheets ([CNBC](https://www.cnbc.com/select/mint-budgeting-app-is-going-away-here-are-some-alternatives), [Engadget](https://www.engadget.com/apps/the-best-budgeting-apps-to-replace-mint-143047346.html)). Credit Karma remains a lead-generation product and is criticized for ads and solicitation ("basically a lead generator", App Store 2026).

**Tiller.** Bank feeds into Google Sheets or Excel for spreadsheet power users. Its value is entirely the feed. Known issues: Google's 2024 security change logs the add-on out if a sheet is left open overnight, fills time out on large sheets, and duplicate transactions occur ([Tiller help](https://help.tiller.com/en/articles/3278731-using-the-tiller-money-feeds-add-on-for-google-sheets)).

**Lunch Money.** An indie, web-first product with the strongest multicurrency support (160+ currencies, per-transaction historical FX), a rules engine and a public API. It is popular with expats and Canadians. Native mobile apps are recent. It inherits Plaid's weak coverage of Canadian banks.

**Actual Budget.** An open-source, local-first YNAB alternative that is fast, offline and owned by the user. The friction is self-hosting a sync server and bank sync. EU users lost the free GoCardless/Nordigen route in July 2025. The mobile experience is "the weaker half of the product" ([Finny review](https://getfinny.app/blog/actual-budget-review-2026)).

**Goodbudget.** A long-running envelope app aimed at couples, with a usable free tier. The UI is dated (Play rating 3.2), bank sync is US-only and Premium-only, and some recent updates wiped envelopes.

**Origin.** An AI-first "all-in-one" bundle covering budgeting, an SEC-registered AI advisor, investing, tax and estate planning. It markets a 98.3% CFP-exam score for its advisor — a self-reported claim from Origin's own blog ([Origin](https://useorigin.com/resources/blog/best-ai-financial-advisor-apps-in-2026-xen56)). Complaints centre on billing after cancellation, sync, and in-app upsell of a cash account.

---

## Drawbacks by app
Frequency tags: **very common** (recurring across many recent reviews and third-party sources), **recurring** (several independent mentions), **occasional** (a few mentions). Quotes are ≤25 words.

### YNAB ([App Store reviews](https://apps.apple.com/us/app/id1010865877?see-all=reviews))
- **Unwanted UI churn and bloat. Very common** (63 of 101 recent ≤2★ reviews mention updates/UI).
  - "They keep adding unnecessary taps, for no reason. No way to revert back to a useful UI." (1★, 2026-09-23)
  - "Way less information density. So hard to read" (1★, 2026-09-01)
  - "They're introducing dating-app-style cards to swipe for approving and rejecting transactions. Huge failure." (2★, 2026-09-24)
- **Price and value. Very common.** Successive hikes to $109/yr. "I have been a subscriber since 2016, back when the price was like $45 per year." ([r/ynab, Feb 2025](https://www.reddit.com/r/ynab/comments/1izcsa4/ynab_subscription_is_up)). NerdWallet's Reddit synthesis says YNAB's "biggest caveat being cost" ([NerdWallet](https://www.nerdwallet.com/finance/learn/best-budget-apps)).
- **Sync lag and drops. Recurring.**
  - "Bank connections drop, go stale, or import days late… imports can take up to three days" (1★, 2026-09-23)
  - "Repeatedly and randomly lost connections to accounts" (1★, 2026-09-01)
  - Reddit snippet: "bank sync only works about 10% of the time with our bank" ([r/ynab](https://www.reddit.com/r/ynab/comments/1vxrcq6/ynab_price_evolution))
- **No historical import on connect. Recurring.** "YNAB only starts pulling transactions from the day you connect your accounts." (2★, 2026-09-10)
- **Steep learning curve, especially credit cards. Very common** (third-party). "you do have to immerse yourself in a lot of YouTube videos" ([Trustpilot DE](https://de.trustpilot.com/review/ynab.com)). The Credit Card Payment category "grows and shrinks in ways that feel out of sync" ([budget-better](https://www.budget-better.com/post/ynab-shifts-how-you-see-credit-cards)). Weekly-paid users find it tedious: "asignando dinero a más de 15 cosas por semana es tedioso" (1★, 2026-08-21).
- **Not listening to power users; wants settings and options. Recurring.** "If the user base is split on an issue, then make it a setting they can choose." (1★, 2026-08-26)

### Monarch Money ([App Store](https://apps.apple.com/us/app/id1459319842?see-all=reviews), [Google Play](https://play.google.com/store/apps/details?id=com.monarchmoney.mobile&hl=en_US))
- **Connection breaks and re-auth chores. Very common.**
  - "managing broken connections is hell. Every time the connection breaks, you should create a new institution" ([Trustpilot UK snippet](https://uk.trustpilot.com/review/www.monarchmoney.com))
  - "the majority of my linked accounts don't work anymore." (2★, 2026-09-19)
  - "Too many accounts disconnect all the time. Basically you need to check it everyday" (1★, 2026-09-17)
- **Data loss and lock-in when an institution stops working. Recurring.** "told to track the transactions manually… Now I have look for an alternative and loss 3 years of data." (1★, 2026-09-18, Citi)
- **Duplicates or wrong balances after account changes. Recurring.** "double expense transactions, 403b moved brokerages now amount is double in reports." (1★, 2026-09-11). Play: "says it syncs, but the amounts are not accurate… only go back like 3 months" (Sep 2026).
- **Support is email-only and slow. Recurring.** "You can't talk to anyone on the phone… let a whole week go by before returning your message." (1★, 2026-09-10)
- **Categorization and rules are opaque. Recurring.** "auto-categorization is unreliable, rule priority is opaque, and category/budget organization is unnecessarily confusing" (2★, 2026-09-05)
- **Missing budgeting basics. Recurring.** No near-budget alerts ("not offer any sort of notifications or alerts when you're near your budget", 2026-09-12). Can't reorder or remove categories. Goals don't link to savings accounts. Debt payoff isn't integrated into the monthly budget (App Store reviews).
- **Price and short trial; users want a lifetime option. Recurring.** "trial is too short" (2026-09-05). "I would consider a lifetime subscription" (App Store). Plus at $199/yr splits the product.
- **Performance degradation. Occasional.** "I have to delete and reinstall the app every month or so to keep it running well." (2026-09-08)
- **Removed features. Occasional.** "MCP removed without warning." (2★, 2026-09-16; see the [MCP pause notice](https://help.monarch.com/hc/en-us/articles/50207234679956-Monarch-MCP-Connector))
- **Hard to cancel. Occasional.** "You can't cancel your subscription in app, you need to go to a browser" (2026-09-24)

### Copilot Money ([App Store](https://apps.apple.com/us/app/id1447330651?see-all=reviews))
- **Platform gap: no Android, US-only, web not at parity. Very common** (third-party). NerdWallet says to skip it if you "Are an Android or PC user" ([NerdWallet](https://www.nerdwallet.com/finance/learn/best-budget-apps)). An Android version "was discussed for 2024 but never shipped" ([receiptsync](https://receiptsync.net/blog/copilot-money-android-alternative)).
- **Connection failures. Very common.**
  - "Connections to Financial Institutions FAILED CONSTANTLY… ONLY TO FAIL AGAIN WITHIN DAYS." (1★, 2026-08-23)
  - "Apple Card transactions just don't work… after some random period they stop updating." (1★, 2026-07-20)
  - "you can easily wait 6 to 9 months for a fix" (2★, 2026-07-12)
- **Debits imported as credits. Recurring.** "imports all transactions from my bank as credits… most of 2026." (1★, 2026-07-29). The same bug appears in a Lunch Money review, which points to aggregator data issues.
- **Categorization regressed. Recurring**, despite this being Copilot's headline strength. "erroneously labeled as internal transfers when they are simply income" (2★, 2026-09-16). "now I have to fix the same transactions month after month" (1★, 2026-08-07).
- **Only ~3 months of history. Recurring.** "it only imported about three months of transaction history and there doesn't seem to be any option to import older" (2★, 2026-09-10)
- **Slow development and "AI" overpromised. Recurring.** "Nobody is able to get off their waitlist for the beta." (1★, 2026-09-28). "I've had copilot since 2021… no substantial new features, and I'm paying $96/year." (2026-09-15)
- **Support is chat-only and slow. Recurring.** "Support in-app doesn't actually respond anymore. Even the AI support chatbot no longer responds." (1★, 2026-09-16)
- **Weak for budgeting and couples. Occasional.** "Doesn't work well for a couple… do it their way or don't bother." (2026-08-12). "If you want to be able to see income-expenses=balance, this app isn't for you." (2026-07-29)
- **Pricing only shown after you link accounts. Occasional.** "makes you add all your accounts… and THEN they offer the pricing tiers." (1★, 2026-07-08)

### Rocket Money ([App Store](https://apps.apple.com/us/app/id1130616675?see-all=reviews))
- **Core promise underdelivers: can't cancel or doesn't find subscriptions. Very common.**
  - "Paid for premium and it legit can't cancel anything." (2026-09-27)
  - "It doesn't tell you what subscriptions you have you have to put them on yourself" (2026-10-01)
- **Billing and dark patterns. Very common** (84 of 150 ≤2★ mention charges or price).
  - "Apparently deleting your account doesn't stop the auto draft" (2026-10-02)
  - "secretly added me to premium without telling me" (2026-09-27)
- **Negotiation fee shock. Recurring.** The fee is 35–60% of first-year savings ([Rocket Money](https://www.rocketmoney.com/learn/personal-finance/how-much-does-rocket-money-cost)). "ended up increasing my bill… Then ended up charging me for their service anyways." (2026-09-27)
- **AI-bot support. Recurring.** "when you go to customer support all you get is AI Customer service." (2026-10-02)
- **Sync gaps never backfilled. Recurring.** "Once an account unlinks, the transactions stop. Re-syncing doesn't update them… lose 2 weeks worth of data" (2★, 2026-09-28)
- **Missing shared-bill splitting. Occasional.** "doesn't have an automated bill splitting feature… splits almost all of their recurring bills" (2026-10-02)
- **Ad fatigue. Occasional.** "Im so tired of rocket money ads appearing anywhere and everywhere." (2026-10-03)

### Quicken Simplifi ([App Store](https://apps.apple.com/us/app/id1449777194?see-all=reviews))
- **Buggy sync, recurring series and duplicates. Very common.**
  - "recurring series and bank synchronization… add erroneous transactions, delete correct transactions" (1★, 2026-10-02)
  - "Duplicate transaction glitches… syncing glitches (both between mobile-desktop and with banks)" (1★, 2026-09-14)
- **Re-auth every day; stale data. Recurring.** "it needs to re authorize all my banks every 24 hrs" (2026-09-01). "Takes days for financial information to be updated." (2026-08-23)
- **Categorization and rules broken. Recurring.** "Nothing is categorized correctly, even after I manually update the category multiple times. The rules are completely broken" (2026-06-27)
- **Mobile is a second-class citizen. Recurring.** "You can't create rules… on the mobile app. It has to be done on a web browser." (2026-09-16). "recently has turned abysmally slow" (2026-09-07)
- **Bot-first support. Recurring.** "They force you to use a bot for support" (2026-07-13). "they are closed and there's no way to send a message" (2026-09-28)
- **Seen as a poor Mint replacement. Recurring.** "Mint was the gold standard… Now you have to pay for products that don't come anywhere near" (2026-06-04)
- **Refund friction. Occasional.** "Extremely difficult to get a refund" (2026-07-15)

### Quicken Classic ([App Store companion](https://apps.apple.com/us/app/id701067522?see-all=reviews); [Play 2.5★](https://play.google.com/store/apps/details?id=com.quicken.qm2014&hl=en_US))
- **Mobile companion unusable. Very common.** "The mobile app is unusable… automatically does a full sync… and then never completes" (2★, 2026-08-25). Multiple 2026 reports say it won't open on iOS 26.
- **Cloud sync between desktop and mobile breaks. Recurring.** "Budget balances remaining are not updating… Have to go 'reset' app data after each transaction" (1★, 2026-04-05)
- **Connector fragility (2FA, wrong balances). Recurring.** "Balances it returns are absurd. Sometimes tens of thousands of dollars off." (1★, 2026-05-03)
- **Renewal price hikes. Very common.** "the subscription price in 2022 was $51.99 and now it is $101.88, nearly double?" ([Quicken Community](https://community.quicken.com/discussion/7973246/annual-increase-in-quicken-costs)). Renewal can be +67% over promo pricing ([fincomparelab](https://www.fincomparelab.com/reviews/quicken-classic-review/)).
- **Stagnation. Recurring.** "They haven't changed much in years." ([Slickdeals](https://slickdeals.net/f/18813901-quicken-classic-deluxe-1-year-subscription-windows-mac-key-card-quicken-classic-deluxe-42-00)). "No dark mode" (2026-03-22).

### EveryDollar ([App Store](https://apps.apple.com/us/app/id942571931?see-all=reviews))
- **Broken releases. Very common** (late Sep 2026). "now it will not Load past a green screen… If I delete and reinstall… I'll lose all my data." (2026-10-01)
- **Paywalling and price. Very common.** "Cash Flow feature now costs money to use!" (2026-09-17). Bank sync costs $17.99/mo. "don't shell out the $80 to link banks." (2026-07-28)
- **Sync drops and missing transactions. Recurring.** "random transactions don't appear at all in the app." (2026-07-31). "I'd have a better connection [with Plaid]… told me it wasn't working" (2026-07-30)
- **Clunky entry; more taps per update. Recurring.** "push five different buttons just to put an expense in." (2026-09-28). The new calculator is slow (2026-09-17).
- **Data loss on the free tier. Recurring.** "keeps logging me out and losing months of budgets" (2026-09-19)
- **Unresponsive support. Recurring.** "created multiple support tickets and literally no one gets back to you." (2026-08-22)

### PocketGuard ([App Store](https://apps.apple.com/us/app/id949414211?see-all=reviews))
- **Unreliable sync. Very common.**
  - "constantly struggles to pull recent transactions and will show transactions from years ago" (2026-09-24)
  - "transactions will disappear then a day or two later they will just reappear." (2026-09-22)
  - "can't successfully sync with American Express" (2026-04-13)
- **Redesign removed core comparisons. Recurring** (Aug 2026). "Removing the ability to easily compare spending among categories between the current and past months was the final straw." (2026-08-20)
- **Billing confusion and no free tier. Very common.** "marketed their subscription prices month by month, however charged me for a whole year." (2026-07-15). "no way around a paywall." (2026-06-23)
- **Slow support. Recurring.** "they wanted screenshots… refused to help without it." (2026-02-17). "After 6 weeks decided to delete" (2026-05-21)
- **Lifetime deal disappointment. Occasional.** "they have the lifetime option because it tricks you into thinking you're getting [value]" (2026-07-25)

### Empower ([App Store](https://apps.apple.com/us/app/id1001257338?see-all=reviews))
- **App quality decline since acquisition. Very common.** "Broken connections for months/years. Support nonexistent. Sad that it went downhill so much after Personal Capital was acquired" (2★, 2026-10-03). "Face ID has now been broken for months." (2026-09-14)
- **Forced migration to the new app causes login chaos. Recurring.** "force us to use the new version… I can no longer log in" (2026-09-12)
- **Sales calls. Very common.** "cold calls trying to sell their advice (I have had 2-3 in 2 years)" ([Bogleheads](https://www.bogleheads.org/forum/viewtopic.php?t=412933))
- **Weak budgeting. Recurring.** "the app seems to lack any budget planning tools" ([FinanceBuzz](https://financebuzz.com/personal-capital-review)). "one of the best features… now that it's gone, I'm considering finding something else" ([Financial Samurai](https://www.financialsamurai.com/personal-capital-review-new-features-ceo-meeting/))
- **Misleading performance math. Occasional.** "always adds the contributions and shows them as investment growth." (2026-09-16)
- **US-only.** Only US institutions, USD and US phone numbers ([Kubera](https://www.kubera.com/blog/personal-capital-alternative))

### Mint (shut down) → Credit Karma ([Credit Karma App Store](https://apps.apple.com/us/app/id519817714?see-all=reviews))
- **What users lost.** Free category budgets, bill reminders, goals and long transaction history. Credit Karma "lacks Mint's budgeting features" ([CNBC](https://www.cnbc.com/select/mint-budgeting-app-is-going-away-here-are-some-alternatives)). There is no category budgeting, no bill-pay or reminder tool, and no goals tracker ([trendingreview](https://trendingreview.com/credit-karma-vs-mint/)).
- **Migration pain.** A ~60-day export window with CSV-only exports ([savepointfinance](https://savepointfinance.com/blog/what-happens-to-your-data-when-a-finance-app-shuts-down)). Users moved to paid apps and had to rebuild categories, rules and history. Monarch offered Mint CSV import ([TechCrunch](https://techcrunch.com/2023/11/02/personal-finance-monarch-intuit-mint)). One App Store review describes the "decent amount of work involved in transferring Mint data". The lesson: "This is the peril of relying on free services" ([CNBC headline](https://www.cnbc.com/amp/2023/11/07/budgeting-app-mint-is-shutting-down-users-are-disappointed.html)).
- **Credit Karma today. Recurring complaints:**
  - Ads and spam: "Credit Karma is basically a lead generator" (2026-10-01)
  - Stale data: "sends alerts that an account was recently closed and the account in question was closed 2yrs ago" (2026-10-01)
  - Spending data glitches: "a glitch in the cash spending portion… doubled or tripled… resync… erased ALL of my spending data" (title "Mint was superior", 2026-09-27)

### Tiller
- **Feed reliability is the whole product, and it is uneven. Very common** (community).
  - "Tiller is nothing without bank feeds" ([Tiller Community](https://community.tiller.com/t/how-well-do-your-bank-feeds-work/19483))
  - "accurate and timely updates to my bank data is more the exception rather than the rule." (same thread)
- **Multi-step refresh. Recurring.** Refresh in the console, then open the sheet, then open the add-on, then fill. "The involved 4 steps… are a bit more cumbersome than even the Mint and CSV export" (same thread).
- **Google Sheets fragility. Recurring.** Logs out if left open overnight (since late summer 2024), fill timeouts on large sheets, duplicates ([Tiller help](https://help.tiller.com/en/articles/3278731-using-the-tiller-money-feeds-add-on-for-google-sheets), [Tiller blog](https://tiller.com/new-improvements-to-tiller-money-feeds-key-feature/)).
- **Only ~3 months of history on connect. Recurring.** "you should generally only expect to get three months of history" (community)
- **Excel is second-class. Occasional.** Still needs a Google sign-in ([CreditDonkey](https://www.creditdonkey.com/tiller-money-review.html)).
- **No mobile app**; annual billing only.

### Lunch Money ([App Store CA](https://apps.apple.com/ca/app/lunch-money/id6739028463?platform=iphone&see-all=reviews))
- **Canadian and neobank sync gaps via Plaid. Recurring.**
  - "the connection with CIBC hasn't been working… forces me do manually upload transactions as a CSV"
  - "My wealth simple accounts would constantly disconnect… report all the debit transactions as credits"
  - The developer acknowledges Canadian banks still use "older authentication methods while awaiting… open banking" ([support](https://support.lunchmoney.app/guides/automatic-imports/institution-specific-issues)).
- **Support "blames Plaid". Occasional.** "every time I reach out to customer support the script is just to blame Plaid."
- **Mobile is young; no offline capture. Recurring** (third-party). "lacks a native mobile app with offline support" ([Finny](https://getfinny.app/blog/best-multi-currency-expense-tracking-apps-2026); partly outdated now that native apps exist).
- **Subscription glitches. Occasional.** "3 months into the subscription Lunch Money shows no active subscription" (US 1★, 2026-03-12)

### Actual Budget
- **Self-hosting required for multi-device sync. Very common** (reviews). "'runs comfortably on a 512 MB VPS' is a sentence that assumes you know what a VPS is" ([Finny review](https://getfinny.app/blog/actual-budget-review-2026))
- **Bank sync is DIY and shrinking in the EU. Very common.** GoCardless stopped new Bank Account Data signups in Jul 2025 ([GoCardless](https://bankaccountdata.gocardless.com/new-signups-disabled)). Enable Banking support is experimental ([GitHub issue](https://github.com/azimul-kabir/actua/issues/832)). SimpleFIN costs ~$15/yr and covers ~25 institutions ([openbankingtracker](https://www.openbankingtracker.com/guides/free-open-banking-apis)).
- **Weak mobile; desktop-centric. Recurring.** "Actual is built for a weekly sit-down session, not for capture in the moment." (Finny review)
- **Same YNAB learning curve; you own backups.**

### Goodbudget ([App Store](https://apps.apple.com/us/app/id471112395?see-all=reviews); Play 3.2★)
- **Dated, desktop-dependent UX. Very common.** "The how-to videos only show it done from desktop" (2025-09-19). "No iPad Support" (2026-08-28). "Maddeningly complex app for no reason." (2026-07-07)
- **Data loss on update. Recurring.** "Updated the app and all my envelopes got deleted!" (2026-09-15). "envelopes and transactions were wiped out" (2025-05-11)
- **Paid, US-only sync, and it often fails. Recurring.** "Paid for the subscription and my bank won't even link." (2025-12-26). "You have to pay for a subscription to link / auto sync" (2025-10-01)
- **Reconciliation broke after a feature change. Occasional.** "they have destroyed the reconciliation process." (2026-09-12)
- **Envelope cap on the free tier feels like nickel-and-diming. Occasional.** "It would be better if they charged for the service not the envelope" (2025-12-12)

### Origin ([App Store](https://apps.apple.com/us/app/id1637693312?see-all=reviews))
- **Billing continues after cancel. Recurring.** "Canceled The Service and for weeks. they were still trying to take money" (2026-09-17). "Got charged nearly $100 for a subscription that I had canceled" (2026-09-18)
- **AI and upsell intrusiveness. Occasional.** "New ai update with it front and center is very annoying… unremovable advert for a cash account… after charging users $99" (2026-09-10)
- **Net-worth math skewed by linking new accounts. Occasional.** "net worth increased whenever a new account is linked" (2026-09-19)
- **Sync and support. Recurring.** "It often takes weeks to resolve the connection issue, only for it to break again the next day." (2026-09-02)
- **Laggy app and forced updates. Recurring.** "doesn't let you use the app… if you haven't updated it" (2026-06-29)
- **Paywall not disclosed. Occasional.** "There is no 'in-app purchases' tag… totally unusable without a subscription." (2026-09-26)

---

## Cross-cutting pain points (ranked by severity × frequency)

1. **Bank-sync unreliability (disconnects, stale data, missing or duplicate transactions, wrong signs). Universal; the #1 churn driver.** It appears in every sync app reviewed: Monarch, Copilot, YNAB, Simplifi, PocketGuard, EveryDollar, Rocket Money, Tiller, Lunch Money, Origin, Quicken and Empower. Users understand the aggregator is at fault ("if one of these apps don't work for you, none of them will", Origin 1★ review), but they still churn from the app. Specific failure modes:
   - Re-auth loops and 2FA prompts (including Monarch's 4 a.m. MFA texts)
   - Gaps that are never backfilled after reconnect (Rocket Money)
   - Debits imported as credits (Copilot, Lunch Money)
   - Doubling after brokerage moves (Monarch)
   - "Pending" handling and multi-day lag (YNAB, Tiller, Simplifi)
   - Losing years of history when an institution breaks (Monarch/Citi)

   Structural headwinds:
   - JPMorgan now charges aggregators for data access. Its Plaid deal was renewed Sept 2025, and deals covering "nearly all" data requests were reported in Nov 2025 ([JPMC](https://www.jpmorganchase.com/newsroom/press-releases/2025/jpmc-plaid-renewed-data-access-agreement), [CNBC](https://www.cnbc.com/2025/11/14/jpmorgan-chase-fintech-fees.html)).
   - The CFPB §1033 open-banking rule is enjoined and being rewritten, possibly to *allow* fees. A proposal went to OIRA on 6 Aug 2026 ([consumerfinancemonitor](https://www.consumerfinancemonitor.com/2026/08/06/cfpb-sends-new-section-1033-open-banking-proposal-to-oira-for-review/), [openbankingtracker](https://openbankingtracker.com/guides/section-1033-status)).
   - In the EU, the free GoCardless/Nordigen tier closed in Jul 2025.

   Expect sync costs to rise and coverage to stay patchy.
2. **Subscription fatigue, price hikes and billing dark patterns. Very common.**
   - Prices went up: YNAB $45 → $109, Quicken ~2x since 2022, Monarch added a $199 tier. "EVERYTHING is a subscription these days!" (Simplifi 1★).
   - Billing traps are worse than the price itself: charged after cancel or delete (Rocket Money, Origin, PocketGuard, Lunch Money), annual billing presented as monthly (PocketGuard), browser-only cancellation (Monarch), pricing revealed only after account linking (Copilot), promo-to-renewal jumps of 67–100% (Quicken).
   - Users repeatedly ask for **lifetime or one-time purchase** options (Monarch reviews), and some like pay-what-you-want (Lunch Money, Rocket Money).
3. **Update churn and UI regressions that remove workflows. Very common in 2026.**
   - YNAB's redesigns (63 of 101 ≤2★ cite UI)
   - PocketGuard's Aug 2026 Insights rewrite removed month-over-month comparison
   - EveryDollar's broken launch screen
   - Simplifi mobile slowdowns
   - Quicken companion crashes on iOS 26
   - Forced updates in Origin

   Users want **stability, density, and the option to keep the old view**.
4. **Customer support is slow, bot-first, or nonexistent. Recurring across Monarch, Copilot, Simplifi, Rocket Money, PocketGuard, EveryDollar, Origin and Empower.** No phone support, multi-day email loops, AI chatbots that deflect, and tickets auto-closed. When money data is wrong, users want a human who can fix it fast.
5. **Categorization and rule quality. Recurring**, worst in Simplifi and Copilot (regressions), with opaque rule priority in Monarch. Common failures: transfers mislabeled as income or spending, merchants recategorized every month, rules editable only on desktop (Simplifi). Users expect "AI" to have solved this: "There is no excuse in the age of AI" (Copilot 2★).
6. **Shallow history on connect (~3 months) and painful migration. Recurring** (Copilot, Monarch, Tiller; YNAB imports nothing before the connect date). Mint's closure made **data portability and longevity** a buying criterion. People fear losing years of history again.
7. **Learning curve and method rigidity. Recurring** for YNAB, Goodbudget, Actual and EveryDollar: credit-card mechanics, weekly-pay friction, the need for YouTube tutorials. Monarch and Copilot are easier but too shallow for zero-based or debt-payoff users ("do it their way or don't bother", Copilot).
8. **Platform gaps and mobile vs. desktop parity. Recurring.**
   - Copilot has no Android and its web app lacks parity.
   - Monarch Plus, Simplifi rules and Goodbudget envelope fills are desktop-first.
   - The Quicken and Actual mobile apps are weak.
   - Tiller has no app.
   - Lunch Money was web-first.

   Users want **full-power mobile** *and* **a real desktop/web app**.
9. **Couples and households. Recurring.** Monarch (free household seat) and YNAB Together (5 users) are praised, but users still want bill-splitting (Rocket Money) and per-person views (Origin offers member filters). Copilot is weak for couples. Goodbudget and Honeydue are popular niche picks for couples.
10. **Investments and net worth accuracy. Occasional–recurring.** Coinbase holdings missing (Copilot), Fidelity CMA misread as an investment account (Copilot), contributions counted as growth (Empower), net-worth jumps when linking accounts (Origin), no cost basis outside Quicken and Monarch Plus. Global and multicurrency net worth is poorly served (Empower is US-only; Kubera costs $250/yr).
11. **Ads, upsell and data monetization in "free" apps. Recurring** (Credit Karma, Empower advisor calls, Rocket Money ads, Origin cash-account promo). Paid apps win trust by saying "no ads, never sell data" (Monarch, Copilot).
12. **Geography and currency lock-in.** Almost all these apps are US-centric: Copilot, Empower and Rocket Money are US-only, and Goodbudget sync is US-only. Lunch Money is the multicurrency outlier. **None supports Indian banks**. [INFERENCE] India's RBI Account Aggregator rails are a differentiator not exploited by any app here — not researched in this slice.

---

## What users love (keep these)
- **A fast transaction "review inbox"** with mark-as-reviewed, bulk edit and rules applied retroactively. Monarch: "Rules are important because you can apply it historically". Copilot's review queue and tags are also praised.
- **Rules engine (if-this-then-that)**: "The 'rules' literally allows me to distinguish any type of transaction I want with a simple if, then function." (Monarch 5★)
- **All accounts plus net worth in one place**, including investments shown separately from cash (Monarch 5★, re Fidelity).
- **Recurring/bills calendar with auto-detection**: "automatically recording your recurring bills (which you had to manually input in Mint)" (Monarch 5★). Subscription detection (Rocket Money 5★: "subscriptions that I forgot about").
- **Household or partner collaboration at no extra cost** (Monarch, YNAB Together, Goodbudget "Keeps Spouses on the Same Page").
- **Proactive "give every dollar a job" planning, and getting a month ahead**: "it's extremely satisfying to get 30+ days ahead of my expenses" (YNAB 5★). Envelope discipline (Goodbudget 5★: "I was bankrupt and now I am the most financially stable person I know").
- **Safe-to-spend / leftover numbers** (PocketGuard Leftover, Simplifi Spending Plan, Copilot Free to Spend) and pace alerts (PocketGuard "Pace").
- **Beautiful, fast, low-clutter UI** (Copilot; Monarch "visually pleasing"). "So many apps try to do too much. I just want to review my transactions… and be alerted if anything is off." (Copilot 5★)
- **Conversational AI that helps with workflows**: "One thing I really like is the AI Chat bot" (Monarch). Rocket Money users like AI categorization.
- **Multicurrency with historical FX** (Lunch Money) and a public API (Lunch Money, Actual).
- **Local-first speed, offline use, data ownership** (Actual Budget: "Because the data is local, the app responds instantly").
- **Spreadsheet freedom** (Tiller; and "Google Sheets and Excel come up consistently" on Reddit, per [NerdWallet](https://www.nerdwallet.com/finance/learn/best-budget-apps)).
- **No ads and no data selling** (Monarch, Copilot positioning) and a fast product cadence (Monarch reviewers praise how quickly improvements ship).

## Unmet needs / feature wishes (explicit user requests)
- **Reliable sync with transparency**: per-institution health status (Monarch has a [connection status dashboard](https://www.monarch.com/connection-status)), automatic gap backfill after reconnect, multiple aggregator fallbacks, honest outage timelines (Tiller users asked for accurate "date started" info), and graceful CSV/statement import when sync fails.
- **Full history import on connect** (12+ months) and easy migration from other apps (Mint, YNAB, Copilot CSVs).
- **One-time or lifetime purchase, or fair pricing**: "I would really really like to request the one time buy" (Monarch). Also transparent pricing *before* setup, in-app cancellation, and refunds.
- **Budget alerts**: notifications when near or over a category budget (Monarch 1★: "not offer any sort of notifications or alerts").
- **Customization**: reorder, hide or delete categories (Monarch). Biweekly and weekly budgeting periods, semi-monthly income (YNAB, Origin). A custom month start. Settings to keep the old UI or density (YNAB).
- **Goals linked to real accounts** and spending *from* a goal (Monarch reviews, repeated). Sinking funds for trips.
- **Debt payoff integrated into the monthly budget**, not a separate simulator (Monarch 3★).
- **Multiple recurring items per merchant** (e.g. several Apple subscriptions) (Monarch).
- **Bill splitting for shared expenses and roommates** (Rocket Money). Per-person views for couples.
- **Historical transaction import** (YNAB, Copilot).
- **Natural-language Q&A over your own data**: "Hope they come out with some natural language reporting analysis so I can talk to copilot" (Copilot 5★). There is also demand for MCP/AI-tool access to one's own data (Monarch MCP backlash; ChatGPT Finances).
- **Gamification and rewards for good habits** (a Mint-era feature some miss) (Monarch 4★).
- **Android for Copilot-style polish**; tablet and iPad keyboard support (Goodbudget).
- **Accurate investment performance** that excludes contributions, plus cost basis (Empower, Monarch Plus).
- **Human support for data problems**, not bots.

## Notable new entrants and shifts (2024–2026)
- **ChatGPT "Finances" (OpenAI, May 15 2026)**: connects 12,000+ institutions via Plaid. Shows a dashboard of portfolio, spending, subscriptions and upcoming payments, with natural-language Q&A. Preview for Pro users in the US only. Follows OpenAI's Apr 2026 acquisition of the Hiro team ([TechCrunch](https://techcrunch.com/2026/05/15/openai-launches-chatgpt-for-personal-finance-will-let-you-connect-bank-accounts/)). This is a major threat to "insights-only" trackers.
- **Apple Wallet "Insights" (iOS 27, released Sep 14 2026)**: spending, recurring transactions and balances from Connected Cards institutions (incl. several UK banks), plus AI receipt-photo bill splitting ([MacRumors](https://www.macrumors.com/guide/ios-27-wallet/)). The OS is commoditizing basic tracking.
- **Monarch Plus** ($199/yr, Apr 2026): forecasting, business P&L/Schedule C, Morningstar analysis ([Monarch](https://www.monarch.com/blog/monarch-plus)). The **Monarch MCP** connector launched and was then paused (Jun 2026) under aggregator pressure.
- **Copilot web app** (Dec 2025). An AI agent is in beta with a waitlist (App Store reviews).
- **Rocket Money Premium+ with "Rowan"**, an AI assistant that "lives in your text messages" ([Rocket Money](https://www.rocketmoney.com/learn/personal-finance/best-ai-budgeting-apps)).
- **EveryDollar relaunch** (Jan 2026) and **PocketGuard "Pace"** (iOS) ([NerdWallet](https://www.nerdwallet.com/finance/learn/best-budget-apps)).
- **Origin** has an AI advisor with an SEC-registered compliance layer. Its $1 first-year promo is aggressive.
- **Long tail**: BudgetSmart AI, Waypoint Budget, SuperMoney's app (Finovate 2025), Finny (offline capture), Kubera ($250/yr global net worth). All are small and their traction is [unverified].
- **Infrastructure**: JPMorgan's data-access fees (2025); CFPB §1033 in limbo (2026); GoCardless BAD closed to new users (Jul 2025); Enable Banking is now the indie EU route.

---

## Sources
- https://apps.apple.com/us/app/id1010865877?see-all=reviews (YNAB)
- https://apps.apple.com/us/app/id1459319842?see-all=reviews (Monarch)
- https://apps.apple.com/us/app/id1447330651?see-all=reviews (Copilot)
- https://apps.apple.com/us/app/id1130616675?see-all=reviews (Rocket Money)
- https://apps.apple.com/us/app/id1449777194?see-all=reviews (Simplifi)
- https://apps.apple.com/us/app/id701067522?see-all=reviews (Quicken Classic companion)
- https://apps.apple.com/us/app/id942571931?see-all=reviews (EveryDollar)
- https://apps.apple.com/us/app/id949414211?see-all=reviews (PocketGuard)
- https://apps.apple.com/us/app/id1001257338?see-all=reviews (Empower)
- https://apps.apple.com/us/app/id519817714?see-all=reviews (Credit Karma)
- https://apps.apple.com/us/app/id471112395?see-all=reviews (Goodbudget)
- https://apps.apple.com/us/app/id1637693312?see-all=reviews (Origin)
- https://apps.apple.com/ca/app/lunch-money/id6739028463?platform=iphone&see-all=reviews
- https://play.google.com/store/apps/details?id=com.monarchmoney.mobile&hl=en_US
- https://play.google.com/store/apps/details?id=com.quicken.qm2014&hl=en_US
- https://www.nerdwallet.com/finance/learn/best-budget-apps
- https://www.dollarscout.net/personal-finance/reviews/copilot
- https://help.copilot.money/en/articles/11780342-copilot-money-for-web
- https://receiptsync.net/blog/copilot-money-android-alternative
- https://subkept.com/price-hikes/ynab
- https://www.reddit.com/r/ynab/comments/1dsr77q/ynab_price_increasing_to_109_on_august_1_for_new
- https://www.reddit.com/r/ynab/comments/1izcsa4/ynab_subscription_is_up
- https://www.reddit.com/r/ynab/comments/1vxrcq6/ynab_price_evolution
- https://de.trustpilot.com/review/ynab.com
- https://www.budget-better.com/post/ynab-shifts-how-you-see-credit-cards
- https://www.moneymindedme.com/blog/ynab-credit-card-handling
- https://uk.trustpilot.com/review/www.monarchmoney.com
- https://help.monarch.com/hc/en-us/articles/50207234679956-Monarch-MCP-Connector
- https://www.monarch.com/blog/monarch-plus
- https://www.monarch.com/connection-status
- https://help.monarch.com/hc/en-us/articles/360048393352-Guide-to-Connecting-Your-Accounts
- https://www.cnbc.com/2025/05/23/personal-finance-app-monarch-raises-75-million.html
- https://techcrunch.com/2023/11/02/personal-finance-monarch-intuit-mint
- https://www.rocketmoney.com/faq
- https://www.rocketmoney.com/learn/personal-finance/how-much-does-rocket-money-cost
- https://www.rocketmoney.com/learn/personal-finance/best-ai-budgeting-apps
- https://checkthat.ai/brands/quicken/pricing
- https://frontdeskreview.com/software/budgeting-apps/quicken-simplifi
- https://www.pcmag.com/reviews/simplifi-by-quicken
- https://www.geniusfirms.com/blog/quicken-software-2026-full-pricing-and-plan-comparison/
- https://www.fincomparelab.com/reviews/quicken-classic-review/
- https://community.quicken.com/discussion/7973246/annual-increase-in-quicken-costs
- https://slickdeals.net/f/18813901-quicken-classic-deluxe-1-year-subscription-windows-mac-key-card-quicken-classic-deluxe-42-00
- https://everydollar.help.ramseysolutions.com/hc/en-us/articles/21544207900685-EveryDollar-Premium-Subscription-Cost
- https://pocketguard.com/blog/pocketguard-vs-ynab
- https://www.kubera.com/blog/personal-capital-alternative
- https://www.bogleheads.org/forum/viewtopic.php?t=412933
- https://www.moneyunder30.com/investing/reviews/empower-personal-capital/
- https://financebuzz.com/personal-capital-review
- https://www.financialsamurai.com/personal-capital-review-new-features-ceo-meeting/
- https://www.cnbc.com/select/mint-budgeting-app-is-going-away-here-are-some-alternatives
- https://www.cnbc.com/amp/2023/11/07/budgeting-app-mint-is-shutting-down-users-are-disappointed.html
- https://www.theverge.com/2023/11/2/23943254/mint-intuit-shutting-down-credit-karma
- https://www.creditkarma.com/about/releases/intuit-credit-karma-welcomes-all-minters
- https://www.getitplanned.com/blog/mint-alternatives-budgeting-apps-2026
- https://trendingreview.com/credit-karma-vs-mint/
- https://wallethacks.com/migrating-from-mint-to-credit-karma-it-was-not-good/
- https://www.forbes.com/advisor/banking/credit-karma-app-review/
- https://savepointfinance.com/blog/what-happens-to-your-data-when-a-finance-app-shuts-down
- https://www.engadget.com/apps/the-best-budgeting-apps-to-replace-mint-143047346.html
- https://robberger.com/tiller-money-review/
- https://personalfinanceapp.net/tools/tiller-money/
- https://help.tiller.com/en/articles/3278731-using-the-tiller-money-feeds-add-on-for-google-sheets
- https://tiller.com/new-improvements-to-tiller-money-feeds-key-feature/
- https://community.tiller.com/t/how-well-do-your-bank-feeds-work/19483
- https://www.creditdonkey.com/tiller-money-review.html
- https://borderlessbudget.com/compare/lunch-money-alternative
- https://thecollegeinvestor.com/45433/lunch-money-review/
- https://support.lunchmoney.app/guides/automatic-imports/institution-specific-issues
- https://getfinny.app/blog/best-multi-currency-expense-tracking-apps-2026
- https://getfinny.app/blog/actual-budget-review-2026
- https://actualbudget.org/docs/advanced/bank-sync/gocardless/
- https://bankaccountdata.gocardless.com/new-signups-disabled
- https://www.openbankingtracker.com/guides/free-open-banking-apis
- https://github.com/azimul-kabir/actua/issues/832
- https://costbench.com/software/personal-finance/goodbudget/
- https://spendalyst.com/blog/origin-financial-review
- https://useorigin.com/resources/blog/best-ai-financial-advisor-apps-in-2026-xen56
- https://www.jpmorganchase.com/newsroom/press-releases/2025/jpmc-plaid-renewed-data-access-agreement
- https://www.cnbc.com/2025/11/14/jpmorgan-chase-fintech-fees.html
- https://fortune.com/2025/07/16/jpmorgan-chase-fees-fintechs-plaid-finicity-crypto-wall-street-citigroup-bank-of-america-wells-fargo
- https://www.consumerfinancemonitor.com/2026/08/06/cfpb-sends-new-section-1033-open-banking-proposal-to-oira-for-review/
- https://openbankingtracker.com/guides/section-1033-status
- https://www.pymnts.com/legal/bank-regulation/2026/cfpb-open-banking-rewrite-could-put-data-access-fees-at-center-of-section-1033-fight/
- https://techcrunch.com/2026/05/15/openai-launches-chatgpt-for-personal-finance-will-let-you-connect-bank-accounts/
- https://www.macrumors.com/guide/ios-27-wallet/
- https://www.wallstreetsurvivor.com/rocket-money-vs-monarch/
- https://www.teamblind.com/post/best-alternative-for-mint-fkwjeeda

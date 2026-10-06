# Why people abandon expense trackers, and what they want instead: cross-app review mining

Research date: 2026-10-05. Scope: App Store and Google Play 1–3★ reviews (2024-01 to 2026-10), Reddit, Hacker News, Trustpilot snippets, blogs and studies.

## Method and dataset (read this first)

- **App Store**: I pulled up to 500 of the most recent reviews per app from Apple's public customer-review RSS feed (`https://itunes.apple.com/{cc}/rss/customerreviews/page=N/id={ID}/sortby=mostrecent/json`) for 19 app/storefront pairs: YNAB, Monarch, Copilot, Rocket Money, EveryDollar, Goodbudget, Quicken Simplifi, PocketGuard, Spendee, Money Manager (US+IN), Emma, Buddy, Toshl, Money Lover, Budget app "Spending Tracker", Wallet by BudgetBakers (IN), axio (IN), and Fold (IN). That gave 6,523 reviews.
- **Google Play**: I pulled the 150 newest reviews at each of 1★, 2★ and 3★ for 13 apps, using the open-source `google-play-scraper`: axio/Walnut, Money Manager (Realbyte), YNAB, Monarch, Wallet, Cashew, Bluecoins, Spendee, Goodbudget, Money Lover, Rocket Money, PocketGuard and EveryDollar. That gave 5,485 reviews.
- **Coding**: I kept 1–3★ reviews dated 2024-01-01 to 2026-10-04 (n = 6,479). Of these, **4,820 have at least 80 characters** and form the "substantive" base. I tagged each review against 24 themes using keyword patterns. A review can carry several tags. The tagging is approximate, so for the biggest themes I also report a **strict** count from narrower phrases (for example "too expensive" or "disconnect"). The broad count is an upper bound and the strict count a lower bound.
- **Reddit**: Reddit blocks HTML scraping, so I read its public search RSS feeds for 32 subreddit/query pairs (r/ynab, r/MonarchMoney, r/copilotmoney, r/personalfinance, r/budget, r/povertyfinance, r/IndiaInvestments, r/india, r/androidapps, r/iosapps, r/selfhosted, r/Frugal, r/FinancialPlanning). These feeds return the opening post only, not the comments.
- **Hacker News**: full comment trees came from the Algolia HN API.
- **Trustpilot** blocked direct fetches (HTTP 403), so Trustpilot quotes come from search-engine snippets of trustpilot.com pages.
- **Citing individual store reviews**: store reviews have no permalinks. I cite them as `[Store · App · stars · date]` and link the app's review listing or feed.
- **Date range**: everything is 2024–2026 unless marked otherwise.

### Theme frequency in substantive 1–3★ reviews (n = 4,820; 2024-01 → 2026-10)

| Theme | Broad keyword match | Strict match | Notes |
|---|---|---|---|
| Price, subscription, paywall, billing | 31.8% (1,534) | 7.7% (371) | The broad match includes "paid premium but X is broken". It is the #1 tag in 11 of the 14 apps I broke out. |
| Bank sync / auto-capture breaks | 15.6% (752) | 5.7% (274) | Covers SMS-parse failures in India (axio: 23% of its low-star reviews mention SMS/UPI). |
| Crashes, bugs, slowness | 13.9% (672) | – | Spikes after OS releases and redesigns (e.g., Fold on iOS 27, Sept 2026). |
| Support unresponsive or useless | 11.7% (564) | – | Increasingly "AI bot" complaints at Monarch and Copilot. |
| Categorization wrong or rules don't stick | 9.7% (469) | 1.1% (51) | Strongly concentrated in auto-sync apps (Copilot 19%, Simplifi 18%). |
| Manual entry tedious | 7.8% (377) | – | |
| Login loops, forced logouts, MFA | 6.1% (295) | – | EveryDollar 16%, Rocket Money 14%. |
| Confusing UI, learning curve, clutter | 5.8% (280) | – | YNAB 16–21% (mostly redesign backlash). |
| Reports weak | 4.5% (216) | – | |
| Duplicates, transfers, credit-card double counting | 4.3% (206) | 2.0% duplicates alone (97) | |
| Export, import, lock-in | 4.1% (198) | – | |
| Ads | 3.8% (182) | – | Money Manager 16%. |
| Irregular or recurring expense handling | 3.6% (174) | – | |
| No web/desktop/tablet, or platform gap | 3.4% (165) | – | |
| Data loss, backup or restore failures | 3.0% (145) | – | Money Manager 12%, Bluecoins 13%. |
| Privacy, security, permissions | 2.3% (111) | – | |
| Family or partner sharing | 2.2% (104) | – | |
| Cash handling | 1.8% (87) | – | |
| Multi-currency | 1.4% (66) | – | |
| Refunds, reimbursements, splits | 1.3% (63) | – | Low frequency but high pain; see Reddit section. |
| Accessibility | 0.4% (20) | – | |
| Say they cancelled, uninstalled or switched | 10.2% (493) | – | |
| Say they fell back to a spreadsheet | 3.1% (148) | – | |

The split by app type makes the problems clearer:

- **Bank-sync apps** (YNAB, Monarch, Copilot, Rocket Money, EveryDollar, Simplifi, PocketGuard, Emma): price 34%, sync 14%, bugs 10%, support 10%, categorization 8%, login 7%.
- **Manual/SMS apps**: price 19%, bugs 14%, sync 12% (cloud sync between devices or SMS capture), support 9%, ads 4%, data loss 4%.
- **India-focused apps** (axio, Money Manager IN, Wallet IN, Money Lover IN, Fold): price 17%, sync/SMS 12%, support 9%, ads 5%, SMS/UPI 4%, data loss 4%.

An independent tally reaches the same conclusion. App Imp counted 6,581 App Store reviews across 14 budgeting apps in September 2026. Its summary reads: "UX: in 14 of 14 apps, 7.5% of reviews (4.3% in Finance apps)". Budgeting apps run above the finance-app norm on Billing (+3.3 points), UX (+3.2), Accuracy (+1.2) and Sync & data (+0.7). [appimp.app/markets/budgeting-apps](https://appimp.app/markets/budgeting-apps)

---

## Abandonment reasons (ranked)

The ranking weighs four things: (a) frequency in the review corpus; (b) severity, meaning whether it makes the product unusable or merely annoying; (c) whether people explicitly connect it to quitting ("cancelled", "deleted", "back to spreadsheet"); and (d) whether it recurs across Reddit, HN and blogs, not just in one app's reviews.

### 1. Automatic capture is unreliable (bank sync, SMS parsing), and then the app becomes a manual app you're paying for
Why #1: it is the most common *functional* complaint and the one most often tied directly to quitting. Users chose an auto-sync app specifically to escape manual entry, so a broken connection removes the whole value. It is very common across every source.

- "Starts working fine, then accounts stop updating, the bot blames plaid and the financial institution. But no fix." [App Store · Monarch · 1★ · 2026-06-01](https://apps.apple.com/us/app/id1459319842?see-all=reviews)
- "Eventually one of your bank accounts will have an issue with the integration. Once that happens, you're gone for." [App Store · Copilot · 1★ · 2026-01-15](https://apps.apple.com/us/app/id1447330651?see-all=reviews)
- "Every time I or my wife go to open the app we have to MFA 4 times… We didn't have this issue with Mint" [App Store · Simplifi · 1★ · 2024-09-30](https://apps.apple.com/us/app/id1449777194?see-all=reviews)
- "I'm SICK of having to delete and re add, upload transaction history… a connection that keeps breaking and it's so bad that it's no longer useful." [r/MonarchMoney, 2025-04-03](https://www.reddit.com/r/MonarchMoney/comments/1jq6ze4/best_monarch_alternatives/)
- "We tried Rocketmoney for a few months, but it kept disconnecting from our Bank of America account… and lead to the abandonment of the app." [r/budget, 2025-03-20](https://www.reddit.com/r/budget/comments/1jfpx50/newbie_budget_app_recommendations/)
- "Bank connections would randomly disconnect, so I'd have to re-authenticate every couple of weeks… (And the fact that I had to pay for flaky connections really annoyed me)" [r/budget, 2025-04-08](https://www.reddit.com/r/budget/comments/1jufr9q/what_frustrates_you_about_budgeting/)
- "Why would I pay 15$ a month for a 'budgeting' software that I have to MANUALLY INPUT EVERY TRANSACTION" [Trustpilot · YNAB (snippet)](https://uk.trustpilot.com/review/ynab.com?page=3)
- "my RBC connection is a total disaster. I have to re-authorize it almost every 24 hours." [Trustpilot · YNAB (snippet)](https://uk.trustpilot.com/review/ynab.com?page=3)
- "Most alternatives people are proposing need you manually enter every transaction you make, and that instantly makes it useless." [HN, paxys, 2023-11 (Mint shutdown thread)](https://news.ycombinator.com/item?id=38115065)
- **India (SMS capture)**: "SMS, UPI scanning is broken. My credit card spends & bill payments appear to be duplicated. Need a lot manual work." [Google Play · axio · 2026-07-24](https://play.google.com/store/apps/details?id=com.daamitt.walnut.app&hl=en_US)
- "Many SMS are not detected by app. Forcing the user to add manually. Whole purpose of Auto Detection… is not working" [Google Play · axio · 3★ · 2026-07-07](https://play.google.com/store/apps/details?id=com.daamitt.walnut.app&hl=en_US)
- "Bank sync does not work as soon as you pay. Hdfc sync has never worked. Please do not pay them." [App Store IN · Wallet · 1★ · 2024-10-23](https://apps.apple.com/in/app/id1032467659?see-all=reviews)
- **Regional coverage**: "my bank (mBank from Poland) is no longer supported by the new bank connection providers." [Trustpilot · YNAB (snippet)](https://www.trustpilot.com/review/ynab.com?page=103). YNAB also dropped TrueLayer "private beta" banks in 2024 and told a user "we don't offer discounts on our subscription price." [HN "Tell HN: YNAB will partly remove direct bank imports", 2024-03-21](https://news.ycombinator.com/item?id=39773819)
- **Open-source fallbacks break too**: "GoCardless has stopped offering their Bank Account Data API… This also pertains to Firefly III" [r/selfhosted, 2025-08-02](https://www.reddit.com/r/selfhosted/comments/1mfs6ro/actual_budget_cannot_sign_up_to_gocardless_bank/)
- **Structural cause**: Monarch's aggregation lead explains that "some data providers (Plaid) tell us when a pending transaction becomes a posted one. Others (MX, Finicity) don't." [r/MonarchMoney official post, 2026-07-08](https://www.reddit.com/r/MonarchMoney/comments/1uqy1ey/disappearing_edits_and_duplicate_transactions/). Plaid itself says mandatory periodic re-authentication "ultimately leads to consumer attrition." [Plaid blog, 2021](https://plaid.com/blog/how-to-make-re-authentication-better-our-response-to-the-fcas-consultation/)

### 2. Price doesn't match perceived value, plus repeated hikes and hostile billing
Why #2: it is the largest tag by volume (31.8% broad, 7.7% strict). Price becomes a quit trigger when it combines with #1 ("paying for broken sync") or with a stagnant product. It is very common, and very common on Reddit after each price hike.

- "$100 a year. For a budgeting app… I couldn't justify spending $100 on the tool I use to track every dollar I spend." [Okane blog, "Why I Left YNAB", 2026-02-03](https://okane.finance/blog/2026/02/03/why-i-left-ynab)
- **YNAB price history**: one-time $60 → $50/yr → $84 → $99 → $109 (2024), "even after YNAB4 users were initially told they'd keep the 'grandfathered' rate of $45." [fincomparelab](https://www.fincomparelab.com/guides/ynab-pricing/), [Fortune](https://fortune.com/article/ynab-pros-and-cons)
- "After 15 years with ynab… I cancelled my subscription today… found Actual Budget… All free." [r/ynab, 2026-01-31](https://www.reddit.com/r/ynab/comments/1qscdcd/after_15_years_with_ynab_ynab4_and_ynab5_i/)
- "In the last two months I just stopped… It coincides with the price increase email (that annoyed me - they pushed it too far)" [r/ynab, 2024-07-30](https://www.reddit.com/r/ynab/comments/1efl8s5/have_just_lost_the_love_for_ynab/)
- Monarch Plus (April 2026): "I didn't expect 200% price increase for these features." [r/MonarchMoney, 2026-04-21](https://www.reddit.com/r/MonarchMoney/comments/1ss0zlg/anybody_tried_origin_already/). On Monarch Plus at $199.99/yr vs Core at $99.99: "retirement planning should have been a core feature not tied to business on plus most likely will switch" [App Store · Monarch · 1★ · 2026-04-24](https://apps.apple.com/us/app/id1459319842?see-all=reviews)
- A silent renewal increase: "Monarch charged me $110… up 10%… I wasnt aware of the price going up and never saw any announcements" [r/MonarchMoney, 2025-12-27](https://www.reddit.com/r/MonarchMoney/comments/1pxcdnz/price_increase_ai_related/)
- "If you're not improving the app in any shape and form, why would anyone pay for a subscription?" [App Store · Copilot · 1★ · 2026-01-22](https://apps.apple.com/us/app/id1447330651?see-all=reviews)
- **The irony for low-income users**: "I'm broke, that's why I wanna budget… the first thing they hit me with is: pay $10 a month." [r/povertyfinance, 2025-09-23](https://www.reddit.com/r/povertyfinance/comments/1no5u62/why_do_budget_apps_charge_money_when_people_start/)
- **Billing practices** (recurring):
  - "Trial offer says 7 days. Was charged day 1 for the full year." [App Store · PocketGuard · 1★ · 2025-03-02](https://apps.apple.com/us/app/id949414211?see-all=reviews)
  - "I signed up for a one week free trial and cancelled before it was up. I was still charged the $99.99 yearly fee" [App Store · Monarch · 1★ · 2026-03-27](https://apps.apple.com/us/app/id1459319842?see-all=reviews)
  - "This app has you login, connect all your bank accounts, then tells you that it's a free trial and will charge you" [App Store · Copilot · 1★ · 2025-11-10](https://apps.apple.com/us/app/id1447330651?see-all=reviews)
- **Lifetime licences revoked or re-sold** (recurring): "they said you could buy it for life. Paid the almost $10… then it deletes the option. Only one available is the yearly charge." [App Store · Spending Tracker · 2★ · 2026-04-02](https://apps.apple.com/us/app/id1525179720?see-all=reviews). Also: "I bought the Lifetime Premium version, but now the app is asking me to pay again." [Google Play · Bluecoins · 1★ · 2026-06-02](https://play.google.com/store/apps/details?id=com.rammigsoftware.bluecoins&hl=en_US)
- **Paywalled basics in India**: "Paid ₹599 for the upgraded version… it just keeps showing 'Processing'" [App Store IN · Money Manager · 1★ · 2026-05-09](https://apps.apple.com/in/app/id560481810?see-all=reviews). "while the trial was on, none of the things ever failed once the trial was over, even normal things are feeling [failing]" [App Store IN · Fold · 1★ · 2026-08-25](https://apps.apple.com/in/app/id1626994334?see-all=reviews)

### 3. Logging fatigue and maintenance burden (the "second job" problem)
Why #3: only 7.8% of reviews name it directly, because people who quit from fatigue rarely write reviews. It dominates the "why I stopped" Reddit and blog genre, and it is backed by the strongest behavioral data (see Retention stats). Very common in forums; under-represented in stores.

- "Bhai kaun 5 dropdown menu select karke 50 rupaye ki chai log karta hai roz" ("Who selects 5 dropdowns every day to log a ₹50 chai?") [r/budget (India user), 2026-03-06](https://www.reddit.com/r/budget/comments/1rmmdx1/upi_microtransactions_were_silently_draining_my/)
- "I tried using traditional expense tracker apps (Moneyfy, Walnut, etc.) and even maintained an Excel sheet. But honestly, it felt like a chore." (same post)
- "Doing it manually is tough and I tried it for a month and eventually stopped tracking my expenses itself." [r/IndiaInvestments (iOS user; no SMS auto-capture on iOS), 2023-10-24](https://www.reddit.com/r/IndiaInvestments/comments/17feuei/fi_moneyjupiter_just_for_tracking_expenses_will/)
- "I meticulously tracked every penny… It was terrible and I don't want to live my life as a slave to a spreadsheet." [r/budget, 2025-08-22](https://www.reddit.com/r/budget/comments/1mx47i0/there_has_got_to_be_a_better_way/)
- "Decision fatigue kicks in, uncategorized transactions pile up, and your budget becomes another abandoned to-do item." [r/budget, 2025-06-27](https://www.reddit.com/r/budget/comments/1lm435j/the_only_4_budget_categories_you_actually_need/)
- "They all looked promising at first but most of them got deleted after a week or two." [r/budget, 2025-07-02](https://www.reddit.com/r/budget/comments/1lps837/3_reasons_why_budgeting_apps_dont_work_for_some/)
- "I work 11 hours a day and sometimes I just don't have the energy or time" [App Store · Money Manager · 2★ · 2025-08-26](https://apps.apple.com/us/app/id560481810?see-all=reviews)
- "I still spend between 20 and 30 minutes once a week to keep my beancount updated by manually entering the transactions" [HN, skwee357, 2025-03](https://news.ycombinator.com/item?id=43269460)
- "Way too manual work required. Doesn't learn." [Google Play · Spendee · 2★ · 2026-07-22](https://play.google.com/store/apps/details?id=com.cleevio.spendee&hl=en_US)
- **Merchant-level splitting is the worst case**: "Amazon Transactions are a nightmare… Way too much time wasted… Ready to throw in the towel" [r/ynab, 2025-12-20](https://www.reddit.com/r/ynab/comments/1prlhzd/amazon_transactions_are_a_nightmare/). "having to break down Amazon orders is a tedious nightmare." [HN, 2024-02](https://news.ycombinator.com/item?id=39393143)

### 4. Numbers can't be trusted (duplicates, wrong categories, transfers and credit-card payments counted as spending)
Why #4: it is less frequent than #1–3, but users describe it as a deal-breaker because a budget built on wrong totals is worse than none. This is a recurring theme, and it is rising in 2026 at Monarch and Copilot. See **Data-correctness problems** for the full breakdown.

- "Literally it became impossible to budget due to multiple duplicate transactions." [App Store · Monarch · 3★ · 2026-09-30](https://apps.apple.com/us/app/id1459319842?see-all=reviews)
- "I have been using this app blindfold since 3 years… I thought 100 times the excel is wrong" [App Store IN · Money Manager · 1★ · 2026-06-05](https://apps.apple.com/in/app/id560481810?see-all=reviews)
- "Ive been using mint but its just trash now. Cant connect to anything. Always mislabeling things… I have stopped budgeting." [r/personalfinance, 2020 (pre-2024, included for pattern)](https://www.reddit.com/r/personalfinance/comments/hysfhh/whats_a_good_free_budgeting_app_that_isnt_mint/)

### 5. Bugs, crashes, slowness, and redesigns that make the core loop worse
Why #5: 13.9% of reviews. Long-time users churn here because of regressions after updates, not first-run problems. Common.

- "Opening different menus, reports, changing categories, etc. happens so slowly with no feedback anything is happening." [App Store · Monarch · 2★ · 2026-08-15](https://apps.apple.com/us/app/id1459319842?see-all=reviews)
- "now it takes at least 30 seconds to load data." [r/MonarchMoney (Android), 2025-01-18](https://www.reddit.com/r/MonarchMoney/comments/1i4ix2f/android_app_is_sooooo_slow/)
- "UI updates… make the app slower (long animations, pauses…) and harder to use (hiding features, burying functionality)." [App Store · YNAB · 1★ · 2026-06-06](https://apps.apple.com/us/app/id1010865877?see-all=reviews)
- "can we please stop making UI updates that make things take more clicks to complete?" [r/ynab, 2025-07-18](https://www.reddit.com/r/ynab/comments/1m33ki0/please_stop/)
- "the last year or so has brought so many unnecessary updates and changes to the UI, I don't know if I can do it any longer." [r/ynab, 2026-09-23](https://www.reddit.com/r/ynab/comments/1wo4cxy/why_cant_they_just_leave_it_alone/)
- "Latest updates and the UI revamp is making the app turtle slow and unresponsive." [Google Play · Spendee · 2★ · 2026-09-21](https://play.google.com/store/apps/details?id=com.cleevio.spendee&hl=en_US)
- "Was a great app, but the new UI is very unintuitive and looks like vibecoded trash." [Google Play · Bluecoins · 2★ · 2026-08-15](https://play.google.com/store/apps/details?id=com.rammigsoftware.bluecoins&hl=en_US)
- Fold on iOS 27 (Sept 2026): a cluster of 1★ reviews within 3 days, e.g. "Latest version crashes on IOS 27, please fix" [App Store IN · Fold · 1★ · 2026-09-16](https://apps.apple.com/in/app/id1626994334?see-all=reviews)

### 6. The budgeting method or UX is too complex, or doesn't fit how people get paid
Why #6: 5.8% of reviews mention a confusing UI. On Reddit, the method mismatch (zero-based, monthly-income-upfront, rigid categories) is a frequent "why I quit YNAB/EveryDollar" story. Common.

- "I don't like budgeting apps that require you to allocate your entire monthly budget at once… I don't have all my money upfront" [r/personalfinance, 2026-04-17](https://www.reddit.com/r/personalfinance/comments/1so4ner/any_good_recommendations_for_a_budgeting_app/)
- "I'm a server so my paychecks are never exactly the same each week" [r/povertyfinance, 2026-06-16](https://www.reddit.com/r/povertyfinance/comments/1u7fjud/iso_budgeting_app/)
- "I have no interest in the 'every dollar gets a job', super micromanagement of my budget." [r/personalfinance, 2025-01-11](https://www.reddit.com/r/personalfinance/comments/1hykvfe/what_is_the_best_budget_app_that_is_not_zerobased/)
- "I tried unsuccessfully to use YNAB for 3 to 4 years (I would use it for one month then get overwhelmed and avoidant and then stop)." [r/ynab, 2026-07-03](https://www.reddit.com/r/ynab/comments/1umm2ry/anyone_else_having_hard_realizations_after_using/)
- "This app is fantastic if you have a lemonade stand… doesn't have hours to fight with an unintuitive interface" [Google Play · YNAB · 1★ · 2026-07-25](https://play.google.com/store/apps/details?id=com.youneedabudget.evergreen.app&hl=en_US)
- The Academy Bank survey (n > 300, 2025) found 30% say "irregular income" disrupts budgeting. [PR Newswire](https://www.prnewswire.com/news-releases/academy-bank-research-reveals-how-budgeting-apps-enable-financial-success--and-where-they-fall-short-302470595.html)

### 7. Support is unreachable, slow, or an AI bot
Why #7: 11.7% of reviews. It rarely causes churn by itself, but it turns #1 and #4 into exits. Common, and growing with AI-bot-first support.

- "Of course the AI support bot was useless… so now I have to audit and delete the duplicates" [r/MonarchMoney, 2026-05-29](https://www.reddit.com/r/MonarchMoney/comments/1tr3jkx/duplicate_transactionsi_am_growing_tired_of_this/)
- "Support ghosted me after promising to fix my Merrill account" [r/copilotmoney, 2026-06-26](https://www.reddit.com/r/copilotmoney/comments/1ufqulv/support_ghosted_me_after_promising_to_fix_my/)
- "Support just blames my bank and tells me to insert transactions manually 'in the meantime'" [App Store · EveryDollar · 2★ · 2026-05-04](https://apps.apple.com/us/app/id942571931?see-all=reviews)
- "Never have i ever received a proper support from the team here… continuing to use it only because i paid for the lifetime membership" [App Store IN · Wallet · 1★ · 2024-05-24](https://apps.apple.com/in/app/id1032467659?see-all=reviews)
- "I have reported… all the messages which are not detected… but still I don't see any support from Axio." [Google Play · axio · 2026-08-10](https://play.google.com/store/apps/details?id=com.daamitt.walnut.app&hl=en_US)

### 8. Emotional cost: guilt, shame, and "it makes me feel broke"
Why #8: it is invisible in store reviews but explicit in forums and behavioral research. The Irrational Labs RCT names "information aversion" and shame as core barriers. Recurring.

- "Why does YNAB make me feel broke when I have more money than I've ever had?" [r/ynab, 2024-04-09](https://www.reddit.com/r/ynab/comments/1bzt3dd/why_does_ynab_make_me_feel_broke_when_i_have_more/)
- "It has negative for debit so it makes it always feel like your doing something wrong" [App Store · YNAB · 1★ · 2026-01-22](https://apps.apple.com/us/app/id1010865877?see-all=reviews)
- "when someone does not follow their budget… they do not want to be reminded – or worse, feel shame" [Irrational Labs / Duke Common Cents Lab](https://irrationallabs.com/blog/money-budgeting-experiment/)

### 9. Lock-in and app death (shutdowns, data you can't take with you)
Why #9: it is infrequent but catastrophic. Mint's shutdown (3.6M MAU) and smaller app deaths permanently raised users' distrust. Recurring on HN and Reddit.

- Mint "had, Intuit claims, 3.6 million monthly active users as of 2021" and users got "a one-month window post-shutdown to download their data." [ZDNet, 2023-11-06](https://www.zdnet.com/home-and-office/intuit-kills-mint-and-personal-finance-app-users-are-unhappy/)
- "the data exported from Mint was total garbage- thousands of duplicate records with different dates and descriptions" [App Store · Simplifi · 1★ · 2025-01-22](https://apps.apple.com/us/app/id1449777194?see-all=reviews)
- "I use the 1Money Expense Tracker app… as premium (I had bought the lifetime license)… So I suppose the app shutdown?" [r/androidapps, 2025-01-09](https://www.reddit.com/r/androidapps/comments/1hxnq7h/1money_expense_tracker_app_shut_down/)
- "I've been using Vault for the past few years, but it's no longer available in the app stores and the app stopped working." [r/personalfinance, 2026-01-28](https://www.reddit.com/r/personalfinance/comments/1qpniwe/alternatively_for_calendar_money_tracking_app/)
- "It's practically impossible to export any useful data from it so forget about using Excel" [App Store · Monarch · 3★ · 2026-05-06](https://apps.apple.com/us/app/id1459319842?see-all=reviews)

### 10. Privacy and trust (data selling, trackers, lending upsells)
Why #10: only 2.3% of low-star reviews. It works mainly as a pre-adoption filter (people never install, or never link banks) rather than as a reason to churn. It is stronger in HN, India and self-hosted communities.

- "a service that charges $100+ / year has so many trackers that uBlock shows 87 elements blocked just in the account signup flow" [HN, geekifier, 2023-11 (Monarch signup)](https://news.ycombinator.com/item?id=38115877)
- "This is my fear for any finapp I try now… At what point will Monarch be acquired and/or begin to sell me things" [HN, adamredwoods](https://news.ycombinator.com/item?id=38121312)
- "FYI they share your data with Facebook/Meta. You must go through the dark UX patterns of disconnecting it" [App Store · Monarch · 2★ · 2026-06-12](https://apps.apple.com/us/app/id1459319842?see-all=reviews)
- "If you want your banking data handled by a company that couldn't be bothered to provide a basic security attestation (SOC 2…)… I deleted before connecting" [App Store · Copilot · 1★ · 2026-05-21](https://apps.apple.com/us/app/id1447330651?see-all=reviews)
- India: axio's Play listing pairs expense tracking with loans at "14% & go up to 35% per annum" and declares it "may share… Location and Personal info" with third parties. [Google Play · axio](https://play.google.com/store/apps/details?id=com.daamitt.walnut.app&hl=en_US). Moneyview "also runs an instant personal loan product… your transaction data feeds an underwriting engine." [fintrakk.com](https://fintrakk.com/best-personal-expense-tracker-app-india/)
- The Incogni study of 20 Android budgeting apps found "60%… share your data" and "1 in 4… share your financial information with third parties." [blog.incogni.com](https://blog.incogni.com/budgeting-apps-research/)

### 11. Fit gaps that silently exclude whole segments (multi-currency, family, cash, platform)
Why #11: each is small in volume (1–3%), but each is an absolute blocker for its segment: expats, couples, cash users, iOS users in India, and people outside the US/EU.

- "Multi currency is the one thing every app I try keeps getting wrong. It's not about calling an API for conversion rates" [HN, leononame, 2024-02](https://news.ycombinator.com/item?id=39394331)
- "Make a cross platform app so I can budget with my partner we both really like this app but he only likes using android." [App Store · Buddy · 2★ · 2025-09-24](https://apps.apple.com/us/app/id936422955?see-all=reviews)
- "There is no share option between friends and family, you should provide admin, view or edit option" [App Store IN · Money Manager · 1★ · 2026-05-21](https://apps.apple.com/in/app/id560481810?see-all=reviews)
- "My wife has never opened either and has vague anxiety… about going on vacations or making big purchases" [HN, leoqa, 2024-02](https://news.ycombinator.com/item?id=39398345)
- "I wish this app would work with cash cards like PayPal and CashApp bc I don't have a bank account." [Google Play · Rocket Money · 1★ · 2026-09-14](https://play.google.com/store/apps/details?id=com.truebill&hl=en_US)
- "The only problem i feel in YNAB is i need to manually do all the entries since I am from India" [HN, Manik_agg, 2023-12](https://news.ycombinator.com/item?id=38791914)

---

## Data-correctness problems

These are ordered by how often users report them and how much damage they do.

### A. Duplicate transactions (pending→posted, card replacement, shared cards, re-linking)
2.0% of substantive low-star reviews (strict). Concentrated in Monarch, Copilot, Simplifi, Buddy and EveryDollar; a recurring 2026 crisis at Monarch.
- "Too many false duplicates… duplicates of transactions show up all the time that have to be manually deleted" [App Store · Monarch · 3★ · 2026-07-29](https://apps.apple.com/us/app/id1459319842?see-all=reviews)
- "AMEX duplicates every transaction across both cards. Citi duplicates old + new card accounts." [App Store · Monarch · 2★ · 2026-07-29](https://apps.apple.com/us/app/id1459319842?see-all=reviews)
- "I'm close to canceling Monarch… Tired of having to manually delete them." [r/MonarchMoney, 2026-08-30](https://www.reddit.com/r/MonarchMoney/comments/1w2kkc7/random_duplicate_transactions/)
- "CC#1234 is replaced with CC#5678… #5678 comes in with all transactions of #1234. So now the transactions… are duplicated." [r/MonarchMoney, 2026-09-18](https://www.reddit.com/r/MonarchMoney/comments/1wjim5k/replacement_credit_card_new_number_all/)
- "I have a lot of duplicate charges on my account and no matter how many times I delete them, it stays." [App Store · Copilot · 3★ · 2026-01-01](https://apps.apple.com/us/app/id1447330651?see-all=reviews)
- "PayPal transactions populated as double charges. 'Deleting' unwanted transactions… did not result in them being deleted." [App Store · Copilot · 1★ · 2026-02-27](https://apps.apple.com/us/app/id1447330651?see-all=reviews)
- Root cause, from the vendor: when a provider "deletes a transaction and re-adds it with a new ID… any edits you made to the original can disappear with it." [r/MonarchMoney official, 2026-07-08](https://www.reddit.com/r/MonarchMoney/comments/1uqy1ey/disappearing_edits_and_duplicate_transactions/)

### B. Wrong categorization, and rules or corrections that don't stick
9.7% broad. Highest in Copilot (19%) and Simplifi (18%).
- "the automatic categorization of expenses is laughably bad… My autopay credit card payments get classified as car payments ('auto')." [Google Play · Monarch · 2★ · 2025-08-14](https://play.google.com/store/apps/details?id=com.monarchmoney.mobile&hl=en_US)
- "I tell it that every transaction labeled 'Safeway' is under the 'Meals' category. The next Safeway transaction may or may not get labeled properly." [r/MonarchMoney, 2026-01-15](https://www.reddit.com/r/MonarchMoney/comments/1qdf18q/noting_my_personal_pros_and_cons/)
- "At 3 months I still need to manually change each transaction" [App Store · Copilot · 2★ · 2026-09-04](https://apps.apple.com/us/app/id1447330651?see-all=reviews)
- "things that you need to recategorize multiple times before it sticks." [App Store · Monarch · 3★ · 2026-06-17](https://apps.apple.com/us/app/id1459319842?see-all=reviews)
- "Recategorize a transaction and it doesn't always stick." [App Store · Buddy · 1★ · 2024-05-22](https://apps.apple.com/us/app/id936422955?see-all=reviews)
- "I waited years for YNAB to automate categorisation. Today I stopped waiting." (the user turned to ChatGPT instead) [r/ynab, 2026-10-03](https://www.reddit.com/r/ynab/comments/1wwezeu/i_waited_years_for_ynab_to_automate/)

### C. Transfers between your own accounts counted as spending or income
- "it will count everything leaving an account as expenditure but if it goes into another of your accounts it will not cancel out." [r/povertyfinance, 2026-05-02 (Dollarwise)](https://www.reddit.com/r/povertyfinance/comments/1t1j7j7/if_you_are_here_please_dont_fall_into_trash/)
- "Automatic transaction imports often pulled in internal transfers I didn't want in my budget" [r/budget, 2025-04-08](https://www.reddit.com/r/budget/comments/1jufr9q/what_frustrates_you_about_budgeting/)
- "I keep seeing people say transfers will resolve themselves but they don't… maybe I don't need a budget." [r/ynab, 2026-06-05](https://www.reddit.com/r/ynab/comments/1txihyw/i_keep_seeing_people_say_transfers_will_resolve/)
- "The transactions for debt started coming over as income and vice versa." [App Store · Copilot · 1★ · 2026-01-15](https://apps.apple.com/us/app/id1447330651?see-all=reviews)
- "people have been having this issue [inverse transactions] for over two years" [r/MonarchMoney, 2026-01-29](https://www.reddit.com/r/MonarchMoney/comments/1qpwanb/inverse_transactions_plaid_issue_w_credit_union/)
- One user's explicit requirement: "Handling own transfers between banks (so they are not counted as expenses)" [r/personalfinance, 2025-12-27](https://www.reddit.com/r/personalfinance/comments/1px18hk/expense_tracker_app_search_and_review_of_bilance/)
- A Spendee user complained that a cash withdrawal could only be logged as an expense: "the app only allows me to register expense instead of transfer." [Google Play · Spendee · 1★ · 2026-09-16](https://play.google.com/store/apps/details?id=com.cleevio.spendee&hl=en_US)

### D. Credit-card payment double counting and confusing CC models
- "Credit card payments is a spending category filled with other spending categories? Pass." [Google Play · YNAB · 1★ · 2026-09-01](https://play.google.com/store/apps/details?id=com.youneedabudget.evergreen.app&hl=en_US)
- "There's no easy way to indicate credit card payments. I hate the transfer option!… I have to edit both transactions." [Google Play · Cashew · 3★ · 2026-03-04](https://play.google.com/store/apps/details?id=com.budget.tracker_app&hl=en_US)
- "It also fails to link my credit card payments to my checking account transactions" [Google Play · YNAB · 3★ · 2025-10-12](https://play.google.com/store/apps/details?id=com.youneedabudget.evergreen.app&hl=en_US)
- r/ynab runs a recurring "I don't understand credit cards" genre, e.g. "Every time I think I understand credit cards, I get confused again." [r/ynab, 2026-03-12](https://www.reddit.com/r/ynab/comments/1rrwi3g/yet_another_credit_card_question_how_the_heck_do/)
- In India the same issue shows up in SMS form: "My credit card spends & bill payments appear to be duplicated… This results into double the spends." [Google Play · axio · 2026-07-24](https://play.google.com/store/apps/details?id=com.daamitt.walnut.app&hl=en_US)

### E. Refunds, reimbursements, splits and shared expenses
Low volume in stores (1.3%), but r/ynab and r/MonarchMoney have dozens of "how do I handle reimbursement" threads.
- "If I pay for friends and they later reimburse me, Fold still counts the full transaction as my personal spending." [App Store IN · Fold · 2★ · 2026-05-16](https://apps.apple.com/in/app/id1626994334?see-all=reviews)
- "Partially/fully refunded expenses still only show the gross amt… shared rent makes the spending summary go kinda crazy." [App Store IN · Fold · 3★ · 2025-12-09](https://apps.apple.com/in/app/id1626994334?see-all=reviews)
- "an option to tag refunds to the original expense to reflect the net expense post refund" [Google Play · axio · 3★ · 2025-10-08](https://play.google.com/store/apps/details?id=com.daamitt.walnut.app&hl=en_US)
- "I can only categorise it [a refund] as an income which isn't a true reflection of the transaction or budget!" [Google Play · Spendee · 2★ · 2025-08-06](https://play.google.com/store/apps/details?id=com.cleevio.spendee&hl=en_US)
- "No negative entries is a big deal-breaker… for reimbursement and refunds" [Google Play · Wallet · 3★ · 2026-03-30](https://play.google.com/store/apps/details?id=com.droid4you.application.wallet&hl=en_IN)
- "Anyone else wish you could 'pair' transactions in YNAB for reimbursements?… I end up manually adding notes like 'waiting on $50 from Jane'" [r/ynab, 2025-10-15](https://www.reddit.com/r/ynab/comments/1o7kd3i/anyone_else_wish_you_could_pair_transactions_in/)
- "Losing category tracking on shared/reimbursed expenses — anyone solved this?" [r/MonarchMoney, 2026-07-11](https://www.reddit.com/r/MonarchMoney/comments/1ut7wpw/losing_category_tracking_on_sharedreimbursed/)
- "Shared card expense splitting so others' items aren't counted in my budget, plus a 'Pending Reimbursement' tag" [Google Play · Money Lover · 3★ · 2026-08-27](https://play.google.com/store/apps/details?id=com.bookmark.money&hl=en_IN)

### F. Recurring and irregular expenses misdetected or unsupported
- "Shows one-time payments from years past as monthly recurring charges." [App Store · Simplifi · 1★ · 2026-07-03](https://apps.apple.com/us/app/id1449777194?see-all=reviews)
- "this app just makes up bills that don't exist, and it doesn't give me any reminders about very obvious bills that DO exist" [Google Play · PocketGuard · 2★ · 2024-02-19](https://play.google.com/store/apps/details?id=com.pocketguard.android.app&hl=en_US)
- "I would love to add annual expenses that split over months. there are work-arounds but they don't work well." [Google Play · PocketGuard · 3★ · 2026-10-04](https://play.google.com/store/apps/details?id=com.pocketguard.android.app&hl=en_US)
- "Cannot roll funds over for quarterly/annual bills." [Google Play · EveryDollar · 2★ · 2024-03-25](https://play.google.com/store/apps/details?id=com.everydollar.android&hl=en_US); and "the Sinking Funds are so confusing." [App Store · EveryDollar · 3★ · 2026-01-01](https://apps.apple.com/us/app/id942571931?see-all=reviews)
- "The budgets can not be rolling (extra money from last month can not be added to the next)." [Google Play · Cashew · 3★ · 2025-07-06](https://play.google.com/store/apps/details?id=com.budget.tracker_app&hl=en_US)
- "It does not breakdown the total amount across the year like Mint does." [App Store · Buddy · 3★ · 2024-04-24](https://apps.apple.com/us/app/id936422955?see-all=reviews)
- Conversely, when apps do handle this, users credit them for life change: "I have no idea why it literally never occurred to me to plan for big (or small) irregular or yearly expenses before YNAB." [r/ynab, 2026-06-15](https://www.reddit.com/r/ynab/comments/1u68ker/ynab_success_almost_15_years_in/)

### G. Cash is invisible or second-class
- "it doesn't count cash spend as expenditure. Same with income." [Google Play · axio · 3★ · 2026-07-04](https://play.google.com/store/apps/details?id=com.daamitt.walnut.app&hl=en_US)
- "important features like Reimbursement and Cash in wallet options are missing" [Google Play · axio · 3★ · 2025-06-11](https://play.google.com/store/apps/details?id=com.daamitt.walnut.app&hl=en_US)
- "should show separate balance of cash and bank at the first page itself" [App Store IN · Money Manager · 2★ · 2026-10-01](https://apps.apple.com/in/app/id560481810?see-all=reviews)

### H. Multi-currency handled naively
- "if i change the rate of a currency all the history will be updated while it should reserve rate history" [Google Play · Wallet · 3★ · 2026-06-14](https://play.google.com/store/apps/details?id=com.droid4you.application.wallet&hl=en_IN)
- "each record i create is still using USD and when trying to change it it always ask me to put the exchange rate" [App Store · Spendee · 2★ · 2024-11-30](https://apps.apple.com/us/app/id635861140?see-all=reviews)
- "I want to change my currency from USD to JMD without it converting my entire history." [Google Play · Money Manager · 2★ · 2024-05-24](https://play.google.com/store/apps/details?id=com.realbyteapps.moneymanagerfree&hl=en_IN)
- "tracking each thing at its currency and being able to convert at price/at cost" [HN, 2024-02](https://news.ycombinator.com/item?id=39394331). YNAB users rely on a third-party plugin that periodically breaks: "Is the rmillan multi-currency conversion down?" [r/ynab, 2026-03-23](https://www.reddit.com/r/ynab/comments/1s1bcqn/is_the_rmillan_multicurrency_conversion_down/)

### I. Data loss and broken backup/restore
3.0% of low-star reviews; Money Manager 12%, Bluecoins 13%.
- "Sometimes all your recording suddenly gone after you record transaction, and it happens multiple time" [Google Play · Money Manager · 1★ · 2025-08-05](https://play.google.com/store/apps/details?id=com.realbyteapps.moneymanagerfree&hl=en_IN)
- "Data restoration doesn't work (I have backup files, but I can't restore)" [Google Play · Cashew · 1★ · 2026-05-26](https://play.google.com/store/apps/details?id=com.budget.tracker_app&hl=en_US)
- "one day it deleted all of my data for a whole year and complied all of my transactions into one month." [App Store · Buddy · 2★ · 2026-09-02](https://apps.apple.com/us/app/id936422955?see-all=reviews)
- "When I moved to a new phone, I lost all the categories I had created. It read the SMS's all over again" [Google Play · axio · 3★ · 2026-06-27](https://play.google.com/store/apps/details?id=com.daamitt.walnut.app&hl=en_US)
- "The most recent update wiped out all my existing transaction categories and currency settings" [Google Play · Spendee · 1★ · 2026-10-02](https://play.google.com/store/apps/details?id=com.cleevio.spendee&hl=en_US)

---

## UX/performance problems

1. **Slow core loop, no feedback** (common in 2025–26 for Monarch, Spendee and Bluecoins): "you tap a transaction, for instance, nothing happens, no loa[ding indicator]" [App Store · Monarch · 2★ · 2026-08-15](https://apps.apple.com/us/app/id1459319842?see-all=reviews). "typing, searching everything has become slow." [Google Play · Spendee · 2★ · 2026-10-03](https://play.google.com/store/apps/details?id=com.cleevio.spendee&hl=en_US). "recent updates seems to cause device heat up during search" [App Store IN · Money Manager · 3★ · 2026-08-30](https://apps.apple.com/in/app/id560481810?see-all=reviews)
2. **Redesigns that add taps** (common for YNAB in 2025–26): "Now have to give extra taps to add flags, and clear transactions." [Google Play · YNAB · 1★ · 2026-07-11](https://play.google.com/store/apps/details?id=com.youneedabudget.evergreen.app&hl=en_US). "the fact that you can no longer add a transaction when viewing an account is insane" [App Store · YNAB · 3★ · 2026-04-08](https://apps.apple.com/us/app/id1010865877?see-all=reviews). "Please give me an option to restore the old workflow." [Google Play · YNAB · 1★ · 2025-09-20](https://play.google.com/store/apps/details?id=com.youneedabudget.evergreen.app&hl=en_US)
3. **Adding an expense isn't a one-screen action**: "adding an expense should be a single screen task (for example, as in Money Manager or Bluecoins app)." [Google Play · Wallet · 3★ · 2026-07-04](https://play.google.com/store/apps/details?id=com.droid4you.application.wallet&hl=en_IN). "Its MAIN function is quickly adding income or expenses… The app basically needs 3 core buttons." [Google Play · PocketGuard · 1★ · 2026-05-05](https://play.google.com/store/apps/details?id=com.pocketguard.android.app&hl=en_US). A Wallet user says the add button "plus sign disappears" after minimizing the app. [Google Play · Wallet · 2★ · 2026-02-08](https://play.google.com/store/apps/details?id=com.droid4you.application.wallet&hl=en_IN)
4. **Auth friction**: "every 2 days it logs me out of the system." [Google Play · EveryDollar · 2★ · 2025-12-05](https://play.google.com/store/apps/details?id=com.everydollar.android&hl=en_US). "No autosave credentials, no face id, just constant manual labor just to sign into the app!" [App Store · EveryDollar · 2★ · 2026-02-08](https://apps.apple.com/us/app/id942571931?see-all=reviews). Rocket Money: "the app auto sign me out… never be able to log in after input password, OTP code couple times… unsubscribed" [Google Play · Rocket Money · 2★ · 2026-05-08](https://play.google.com/store/apps/details?id=com.truebill&hl=en_US)
5. **Feature parity gaps across mobile, web and tablet**: "I keep wanting to do things on mobile that are only available in the desktop version" [App Store · Monarch · 2★ · 2026-03-28](https://apps.apple.com/us/app/id1459319842?see-all=reviews). "I absolutely hate the iPad app's unnecessarily reduced capabilities" [App Store · Monarch · 3★ · 2026-06-19](https://apps.apple.com/us/app/id1459319842?see-all=reviews). "The app rarely syncs what I entered on my laptop." [Google Play · Cashew · 3★ · 2025-01-10](https://play.google.com/store/apps/details?id=com.budget.tracker_app&hl=en_US)
6. **Clutter and too many concepts**: "Too many bells and whistles at this point that just get in the way. I just want to track my spending" [App Store · YNAB · 1★ · 2026-01-21](https://apps.apple.com/us/app/id1010865877?see-all=reviews). "feels too clustered and complicated as it needs to be. ui is a bit too messy" [Google Play · Money Manager · 3★ · 2026-05-21](https://play.google.com/store/apps/details?id=com.realbyteapps.moneymanagerfree&hl=en_IN). "Every day accounts disconnect. The app tries to do too much for me and confuses me." [r/MonarchMoney, 2024-07-18](https://www.reddit.com/r/MonarchMoney/comments/1e6al65/this_app_is_overrated/)
7. **Reports that don't answer basic questions**: "Last update removed the ability click on budget category and see transactions. It only shows the totals" [App Store · Monarch · 1★ · 2026-08-29](https://apps.apple.com/us/app/id1459319842?see-all=reviews). "YNAB refuses to show me the sum of these targets… impossible to see if you income matches your lifestyle" [r/ynab, 2025-02-06](https://www.reddit.com/r/ynab/comments/1ij1nyp/what_am_i_missing_why_does_ynab_seem_to_actively/). "I cannot filter exact days and see how much I have spent that month." [App Store · PocketGuard · 1★ · 2024-07-27](https://apps.apple.com/us/app/id949414211?see-all=reviews)
8. **Ads and upsell nags**: "I just try to open the app and dozens of ads are popping." [Google Play · Money Manager · 1★ · 2026-04-18](https://play.google.com/store/apps/details?id=com.realbyteapps.moneymanagerfree&hl=en_IN). "Half the time, the first thing you see is the advertisement for premium." [Google Play · EveryDollar · 2★ · 2024-07-09](https://play.google.com/store/apps/details?id=com.everydollar.android&hl=en_US). "Too many prompts to rate each time when used." [App Store IN · axio · 3★ · 2026-08-31](https://apps.apple.com/in/app/id6780629197?see-all=reviews). YNAB subscribers complain of email spam for the CEO's book "despite me opting out." [r/ynab, 2026-08-11](https://www.reddit.com/r/ynab/comments/1vlor06/ynab_the_company_is_still_email_spamming_me_about/)
9. **Accessibility** (rare in reviews but serious; 0.4%):
   - "Please add a high contrast option for people who are colorblind! The 'funded' green… and 'underfunded' yellow… look virtually identical" [Google Play · YNAB · 3★ · 2024-01-10](https://play.google.com/store/apps/details?id=com.youneedabudget.evergreen.app&hl=en_US)
   - An accessibility audit of YNAB's 2025 Home screen found "critical issues where VO users simply cannot access some controls." [racheleditullio.com, 2025-09](https://racheleditullio.com/blog/2025/09/ynab-adds-a-home-screen-to-its-ios-mobile-app-but-is-it-accessible/)
   - On Monarch: "there seems to be some accessibility issues, and the dash[board]…" [AppleVis forum](https://www.applevis.com/forum/ios-ipados/accessible-budgeting-apps-iosipad)
   - "I cannot use all of YNABs calculator's buttons in a split transaction… The app should scale to the usable area" [Google Play · YNAB · 3★ · 2025-09-01](https://play.google.com/store/apps/details?id=com.youneedabudget.evergreen.app&hl=en_US)
10. **Setup/onboarding overwhelm**: "I feel like I need an onboarding with this app. There are so many things I can and can't do." [App Store · Monarch · 3★ · 2026-09-02](https://apps.apple.com/us/app/id1459319842?see-all=reviews). "There is no 'tour guide' so you have to try to fumble your way through." [App Store · EveryDollar · 1★ · 2025-10-19](https://apps.apple.com/us/app/id942571931?see-all=reviews). "Takes way too much time to set up, constantly having to re-log into accounts. And it doesn't do anything that a spreadsheet doesn't do" [App Store · Simplifi · 1★ · 2026-07-11](https://apps.apple.com/us/app/id1449777194?see-all=reviews)

---

## Business-model problems

1. **Subscription fatigue against a "simple" product.**
   - "the annual cost of this software service seem ridiculously high for how simple the software is… 100 USD is a lot for simple personal accounting software." [HN, cashsterling, 2023-11](https://news.ycombinator.com/item?id=38115483)
   - "I wish they had a lower tier price subscription for no bank syncing." [Trustpilot · YNAB (snippet)](https://www.trustpilot.com/review/ynab.com?page=11)
   - "Just let us pay a fee to buy it without more recurring costs." [Google Play · Money Manager · 1★ · 2025-12-07](https://play.google.com/store/apps/details?id=com.realbyteapps.moneymanagerfree&hl=en_IN)
2. **Charging for bank sync that breaks.** Users feel doubly cheated: "I had to pay for flaky connections" ([r/budget](https://www.reddit.com/r/budget/comments/1jufr9q/what_frustrates_you_about_budgeting/)), and "paid $100 and cant even use it because it cannot sync with my credit cards" [App Store · PocketGuard · 1★ · 2025-04-21](https://apps.apple.com/us/app/id949414211?see-all=reviews).
3. **Tier-splitting that moves the roadmap upmarket.** "When a software company introduces a higher tier and concentrates new feature development there, Core subscribers are being told…" [r/MonarchMoney, 2026-04-22](https://www.reddit.com/r/MonarchMoney/comments/1ss59eg/another_post_about_monarch_plus/). Monarch's CEO publicly revised the Plus plan within 48 hours after the backlash. [r/MonarchMoney, 2026-04-23](https://www.reddit.com/r/MonarchMoney/comments/1stvnik/updates_on_monarch_plus/)
4. **Trial and refund dark patterns.**
   - "Trial Subscription Cannot Be Cancelled… does not show up as having a subscription in the AppStore settings" [App Store · PocketGuard · 1★ · 2026-05-22](https://apps.apple.com/us/app/id949414211?see-all=reviews)
   - "Apple doesn't honor this policy and denies refund requests." [App Store · Simplifi · 1★ · 2026-03-23](https://apps.apple.com/us/app/id1449777194?see-all=reviews)
   - Rocket Money's Trustpilot rating is 3.5★, versus 4.5+★ in the stores, with cancellation complaints such as "In order to cancel subscriptions… you pay for a subscription with Rocket Money" [Trustpilot snippet](https://www.trustpilot.com/review/rocketmoney.com?page=2), [checkthat.ai](https://checkthat.ai/brands/rocket-money/reviews)
5. **Ad- and lending-funded "free" apps erode trust.** HN users call the trade-off out bluntly: "I assume with it being free, Intuit was probably selling my financial data." [HN, Volundr](https://news.ycombinator.com/item?id=38115136). Another: "Credit Karma is a better funnel to credit providers… They make money when they refer a customer" [HN, toomuchtodo](https://news.ycombinator.com/item?id=38115164). In India, the leading SMS trackers double as lenders (axio: loans "from as low as 14% & go up to 35% per annum"). [Google Play listing](https://play.google.com/store/apps/details?id=com.daamitt.walnut.app&hl=en_US). A Rocket Money reviewer says the app is "filled wit[h]" "sleezy, greasy attempts" [Google Play · Rocket Money · 3★ · 2026-07-11](https://play.google.com/store/apps/details?id=com.truebill&hl=en_US).
6. **Paying for premium and still getting ads, or losing premium on a new device.** "I bought premium, but sometime ads still appear" [Google Play · Money Manager · 1★ · 2026-01-22](https://play.google.com/store/apps/details?id=com.realbyteapps.moneymanagerfree&hl=en_IN); "when I changed the device then again ads were showing" [Google Play · Money Manager · 3★ · 2026-01-19](https://play.google.com/store/apps/details?id=com.realbyteapps.moneymanagerfree&hl=en_IN).
7. **Platform risk shapes what can be built.**
   - Google Play allows SMS read only under a restricted "SMS-based money management" exception that requires a declaration form, and some developers report rejection. [Play Console Help](https://support.google.com/googleplay/android-developer/answer/10208820?hl=en), [dev.to](https://dev.to/zeta_byte/ive-been-working-on-a-personal-finance-app-called-finvantage-and-i-recently-hit-a-roadblock-4mjh)
   - iOS has no SMS access at all ([r/IndiaInvestments](https://www.reddit.com/r/IndiaInvestments/comments/17feuei/fi_moneyjupiter_just_for_tracking_expenses_will/)).
   - Aggregators change terms (GoCardless BAD closed to new sign-ups: [r/selfhosted](https://www.reddit.com/r/selfhosted/comments/1mfs6ro/actual_budget_cannot_sign_up_to_gocardless_bank/); YNAB's TrueLayer change: [HN](https://news.ycombinator.com/item?id=39773819)).
8. **Self-hosted and spreadsheet alternatives are now credible price anchors.** "Now, I have all the functionalities I had with YNAB for just $2.65 a month." (Actual Budget on PikaPods plus SimpleFIN at $15/yr) [r/ynab, 2024-07-02](https://www.reddit.com/r/ynab/comments/1dta2it/i_know_that_ynab_saves_you_more_than_109_a_year/). Tiller ($79/yr, spreadsheet plus bank feed) is repeatedly recommended on HN. [HN](https://news.ycombinator.com/item?id=38115121)

---

## What users love (keep these)

From 4–5★ reviews (n = 1,984 substantive reviews, 2024–2026). Share of reviews mentioning each:
- "Easy/simple/intuitive": 23%
- Customization/categories: 17%
- Recurring bills/reminders: 11%
- "See where my money goes"/insight/charts: 8%
- Design: 7%
- Couples/shared use: 7%
- Net worth/investments: 7%
- Auto-sync/auto-categorize: 6%
- Fast entry: 6%
- No ads/one-time price: 3%

Examples:
- **Simplicity and speed**: "It's simple, fast, and very well organized — perfect for tracking daily expenses" [App Store · Spending Tracker · 5★ · 2025-11-09](https://apps.apple.com/us/app/id1525179720?see-all=reviews)
- **One-time or lifetime pricing**: "the one time fee for the premium version is great!" [App Store · Money Lover · 5★ · 2026-09-18](https://apps.apple.com/us/app/id486312413?see-all=reviews). Also: "They offer a lifetime subscription. You only pay one time it is $8.99." [App Store · Spending Tracker · 5★ · 2025-12-15](https://apps.apple.com/us/app/id1525179720?see-all=reviews)
- **No account, privacy-safe**: "No account required, seems privacy safe. Flexible setup, highly customizable." [App Store · Spending Tracker · 5★ · 2026-01-30](https://apps.apple.com/us/app/id1525179720?see-all=reviews)
- **No ads, no subscription** (India): "They haven't shown no ads nor a subscription plan. I will support this app till the end." [App Store IN · Fold · 5★ · 2025-09-03](https://apps.apple.com/in/app/id1626994334?see-all=reviews)
- **Learning categorization plus "safe to spend" after upcoming bills**: "Auto assigned categories and learning to self categorize after 30 days… shows only money left to use after all future recurring expenditures" [App Store · Copilot · 4★ · 2026-01-10](https://apps.apple.com/us/app/id1447330651?see-all=reviews)
- **Couples on one view without merging accounts**: "my partner has joined and we can see our finances together even though we haven't setup joint accounts" [App Store · Monarch · 5★ · 2026-05-04](https://apps.apple.com/us/app/id1459319842?see-all=reviews)
- **Receipt import**: "The new receipt import feature is great… really helps to get me and my wife on the same page" [App Store · Monarch · 5★ · 2026-08-17](https://apps.apple.com/us/app/id1459319842?see-all=reviews)
- **Per-transaction push notifications**: "I like that I get push notifications each time my card is charged" [App Store · Copilot · 3★ · 2026-01-01](https://apps.apple.com/us/app/id1447330651?see-all=reviews)
- **Planning for irregular expenses (sinking funds) changes lives**: the "Kitty Katastrophe" category paid a vet bill stress-free [r/ynab, 2026-03-14](https://www.reddit.com/r/ynab/comments/1rtq3gy/ynab_win/); also the r/ynab irregular-expenses success post cited in F above.
- **Rules engines** (Monarch's most-cited feature per its co-founder): "rules to rename, recategorize, add notes, require review… based on name, amount, category, account" [HN, Ozzie_osman (Monarch), 2023-11](https://news.ycombinator.com/item?id=38116024)
- **Manual entry as mindfulness**, for some users: "I manually input all transactions… it makes me more conscious of my spending." [HN, ixwt](https://news.ycombinator.com/item?id=38115843). "Personally, I add each transaction manually. It allows me to stay on top of my budget." [HN, mdhowle](https://news.ycombinator.com/item?id=39400546)
- **Notification/SMS capture for fast entry**: "anytime I get an SMS or notification from my bank… Waterfly automatically parses text… auto-fills transaction details" [HN, Roadeo, 2024-02](https://news.ycombinator.com/item?id=39393218)
- **Owning your data**: Okane's pitch is "Your budget is a Google Sheet. I can't lock you out of it." [okane.finance](https://okane.finance/blog/2026/02/03/why-i-left-ynab)

---

## Wishlist

Explicit requests, ranked by how often they appear across sources. Of 353 low- and high-star 2024–26 reviews that contain explicit wish phrasing ("I wish", "please add", "would love", etc.), these topics are mentioned:

| Topic | Mentions |
|---|---|
| Bank sync coverage/reliability | 74 |
| Recurring/subscriptions | 36 |
| AI/auto-categorization that learns | 29 |
| SMS/UPI auto-capture | 28 |
| Better reports/charts | 27 |
| Loans/lending/IOUs | 18 |
| Sharing/family | 18 |
| Web/desktop/iPad | 16 |
| Widget | 15 |
| Tags/sub-categories | 12 |
| Cash | 12 |
| Search/filter | 11 |
| Export/backup | 7 |
| Multi-currency | 5 |

1. **Capture that just works, with graceful fallback.** Users want auto-import from banks (US/EU), SMS, UPI and email (India) without paying for flaky links, and quick manual entry when import fails.
   - "I would suggest to track the e-mail for better understanding of expenses… the app should be smart enough to categorise" [Google Play · axio · 2★ · 2026-07-05](https://play.google.com/store/apps/details?id=com.daamitt.walnut.app&hl=en_US)
   - "what I really want is my financial institution(s) to send webhooks of my transactions, or even just allow for an automated download of the CSVs" [HN, tenacious_tuna](https://news.ycombinator.com/item?id=38115215)
   - "a low-fi solution is to receive an email from your bank for every transaction and extract the data" [HN, ashish01](https://news.ycombinator.com/item?id=43272394)
2. **Categorization that learns from corrections and asks only when unsure.** "I wanted it to pull transactions from my bank and then ask me how I want to categorize them and then learn from my choices." [Google Play · YNAB · 3★ · 2026-07-17](https://play.google.com/store/apps/details?id=com.youneedabudget.evergreen.app&hl=en_US). "In this age of AI, to not learn categories accurately…" [App Store · Monarch · 3★ · 2026-07-29](https://apps.apple.com/us/app/id1459319842?see-all=reviews)
3. **"Fastest possible way to categorize"**, plus logging in seconds (voice, natural language, widget, share sheet). "What I'm looking for the most… is the fastest possible way to categorize transactions." [r/ynab, 2024-02-04](https://www.reddit.com/r/ynab/comments/1aiy1u2/if_you_left_ynab_which_app_did_you_switch_to_and/). "Please add a quick option for Expense, income and Transfer. Even on the widget." [Google Play · Money Lover · 3★ · 2024-06-08](https://play.google.com/store/apps/details?id=com.bookmark.money&hl=en_IN). Indie apps are converging on voice and one-tap logging: "An expense tracker built around 3 second logging. Say 'spent 6.50 on a latte'" [r/iosapps, 2026-09-04](https://www.reddit.com/r/iosapps/comments/1w6qr7a/i_built_an_expense_tracker_where_you_can_log_into/)
4. **Link refunds and reimbursements to the original expense; show net spend; IOU tracking.** "lending money to friends as seperate option" [App Store IN · Fold · 1★ · 2026-09-17](https://apps.apple.com/in/app/id1626994334?see-all=reviews); Fold reviewers (2025-12-09, 2026-05-16); the YNAB "pair transactions" thread; Money Lover's "Pending Reimbursement" tag; Monarch's shared-expenses thread (all cited above). "Hi! Is it possible to add a feature that is 'reimburse'" [Google Play · Money Lover · 3★ · 2024-01-23](https://play.google.com/store/apps/details?id=com.bookmark.money&hl=en_IN)
5. **Transfers and credit-card payments handled automatically**, never counted twice, and showing what is owed on each card plus its due date. "Why isn't there an efficient way to track when credit card payments are due?… why can't they pull the statement balance and tell you when it's due" [r/copilotmoney, 2026-01-23](https://www.reddit.com/r/copilotmoney/comments/1qktp4w/credit_card_bill_tracking/)
6. **Irregular, annual and sinking-fund planning without workarounds**, plus a "safe to spend until next paycheck" view for variable income. "add annual expenses that split over months" (PocketGuard, above). "lets me input my monthly bills and their dates and will tell me how much money I need to make each week" [r/povertyfinance](https://www.reddit.com/r/povertyfinance/comments/1u7fjud/iso_budgeting_app/). "How do you figure out what you can actually afford to spend before your next paycheck?" [r/budget, 2026-08-28](https://www.reddit.com/r/budget/comments/1w0k5lo/how_do_you_figure_out_what_you_can_actually/)
7. **Recurring and subscription management that's accurate.** "I'd love a new top-level 'Subscription' model where subscriptions can be defined" [r/ynab, 2026-07-12](https://www.reddit.com/r/ynab/comments/1uul18j/feature_or_addon_idea_to_help_with_subscriptions/). "Why just only recurring expenses only why not recurring income option too" [App Store IN · Fold · 1★ · 2026-09-17](https://apps.apple.com/in/app/id1626994334?see-all=reviews). "Where is recurring expenses? subscription or yearly bill reminders?" [Google Play · Money Manager · 3★ · 2026-08-16](https://play.google.com/store/apps/details?id=com.realbyteapps.moneymanagerfree&hl=en_IN)
8. **Family/partner sharing with roles, cross-platform.** "you should provide admin, view or edit option" (Money Manager IN, above); "cross platform app so I can budget with my partner" (Buddy, above); "it will not sync correctly when sharing as a family." [App Store · Spending Tracker · 2★ · 2025-11-03](https://apps.apple.com/us/app/id1525179720?see-all=reviews). "Tried so many times to share the wallet with my wife… no success." [App Store · Spendee · 2★ · 2025-10-17](https://apps.apple.com/us/app/id635861140?see-all=reviews)
9. **Data ownership: export, local-first, sync between devices, open formats.** "Data kept locally, sync'd between devices using Syncthing. Can upload csv/excel sheets from bank" [r/androidapps, 2026-06-26](https://www.reddit.com/r/androidapps/comments/1ugaol9/expenses_tracker_not_as_easy_as_i_thought/). "full free with no ads and has cloud sync and also has multi OS support." [r/androidapps, 2026-08-16](https://www.reddit.com/r/androidapps/comments/1vq3hfu/expensebudget_tracker_app/). "Add an option to export or print out the budget" [App Store · Monarch · 5★ · 2026-06-14](https://apps.apple.com/us/app/id1459319842?see-all=reviews). "I wish I could import my historical statements… scraping transactions from PDF files" [HN, pimlottc](https://news.ycombinator.com/item?id=39399077)
10. **Real multi-currency** (per-transaction currency, historical rates), Euro/TL salary split, sharing with a spouse: "I would like something where I can even set the exchange rate at the time" [r/budget, 2024-04-07](https://www.reddit.com/r/budget/comments/1by2yz3/budgeting_with_my_salary_and_my_local_currency/)
11. **Cash wallet as a first-class account** (India especially): see the axio and Money Manager IN quotes in G.
12. **Simple mode or fewer decisions.** Users want an overall "one-number" budget or 3–4 buckets instead of dozens of categories ([r/budget](https://www.reddit.com/r/budget/comments/1lm435j/the_only_4_budget_categories_you_actually_need/), [r/ynab percentage system](https://www.reddit.com/r/ynab/comments/1uxh8hd/anyone_moved_from_zerobased_budgeting_to_a/)). "I like to do a plain budget of $4500, categories don't matter to me." [App Store · Copilot · 5★ · 2026-09-28](https://apps.apple.com/us/app/id1447330651?see-all=reviews)
13. **Stop changing the UI**; when it must change, offer a toggle. "The UX changes need to stop (or at least be toggleable)." [Google Play · YNAB · 2★ · 2026-07-16](https://play.google.com/store/apps/details?id=com.youneedabudget.evergreen.app&hl=en_US)
14. **Fair pricing**: a cheap no-sync tier, one-time purchase, no trial traps. See the Business-model quotes above.
15. **Merchant/receipt-level splitting** (Amazon, Costco): "stores like Costco that sell both household goods, groceries, clothes… I like to track each separately." [HN, mgr86](https://news.ycombinator.com/item?id=43269874)
16. **Accessibility basics**: colorblind-safe states, full VoiceOver/TalkBack labels, layouts that scale (YNAB quotes above).

---

## Retention stats

Each number is sourced, and some come with caveats. Treat third-party blog numbers as indicative.

| Metric | Value | Source | Confidence |
|---|---|---|---|
| Finance-app day-30 retention | **4.2%** | Business of Apps "Finance App Benchmarks" (fetch blocked; figure from search snippet) [link](https://www.businessofapps.com/data/finance-app-benchmarks) | Medium |
| Finance apps: day-1 ~22.7%, Statista day-30 5.8% | 22.7% → 5.8% | Sendbird, 2024-03-12 [link](https://sendbird.com/blog/app-retention-benchmarks-broken-down-by-industry) | Medium |
| Finance-app day-30 "activation" rate, North America, Q1 2026 | **17.6%** (highest of any industry) | eMarketer, 2026-07-02 (snippet; page 403) [link](https://www.emarketer.com/content/finance-apps-keep-users-engaged-longer-than-any-other-industry) | Medium. The metric definition differs from classic retention. |
| All-app benchmark | D1 26%, D7 13%, D14 10% | Adjust retention handbook [link](https://www.adjust.com/resources/guides/user-retention/) | High |
| Average fintech-app D1 retention ~22%; "30-day retention… between 4 and 12 percent… Budgeting and expense-tracking apps tend to sit at the lower end" | – | Winnie blog citing Adjust, 2026-03-18 [link](https://www.winnie-app.com/blog/finance-app-retention) | Low–medium (secondary, vendor blog) |
| 46% of apps uninstalled within 30 days (all categories) | 46% | AppsFlyer, cited by Winnie [link](https://www.winnie-app.com/blog/finance-app-retention) | Medium |
| US users uninstall 48% of apps within 30 days | 48% | Stream, 2026-01-22 [link](http://getstream.io/blog/app-retention-guide) | Medium |
| **Engagement with budget feature (RCT, n = 9,035, 13 weeks, 2019)** | **84% viewed their budget ≤5 times; "In all conditions, engagement declined over time"; budgeters spent 1.3–1.4× what they intended; no significant spending reduction** | Irrational Labs / Duke Common Cents Lab [link](https://irrationallabs.com/blog/money-budgeting-experiment/) | **High (primary)** |
| Access to real-time budget info can *increase* spending near period end | 3 field studies + 2 lab experiments | Ghosh & Huang, Think Forward Initiative [link](https://www.behavioraleconomics.com/the-budgeting-app-trap-when-spending-information-backfires/) | High (primary summary) |
| 45% of US adults use a budgeting app or digital tool; 86% cite overspending or income fluctuation as top obstacles; 30% irregular income | n > 300, 2025 | Academy Bank white paper (PR Newswire 2025-06-02) [link](https://www.prnewswire.com/news-releases/academy-bank-research-reveals-how-budgeting-apps-enable-financial-success--and-where-they-fall-short-302470595.html) | Medium (bank-commissioned) |
| Mint had ~3.6M MAU (2021) when shut down (2024-03) | – | ZDNet citing Intuit [link](https://www.zdnet.com/home-and-office/intuit-kills-mint-and-personal-finance-app-users-are-unhappy/) | High |
| Budgeting-app review complaint rates vs finance norm (UX 7.5% vs 4.3%; Billing +3.3 pts; Sync & data +0.7 pts) | 6,581 reviews, 14 apps, 2026-09-28 | App Imp [link](https://appimp.app/markets/budgeting-apps) | Medium |
| Mandatory 90-day re-auth "ultimately leads to consumer attrition" | qualitative, from the aggregator | Plaid, 2021 [link](https://plaid.com/blog/how-to-make-re-authentication-better-our-response-to-the-fcas-consultation/) | High (primary) |
| 60% of 20 popular budgeting apps share data with third parties; 1 in 4 share financial info | – | Incogni [link](https://blog.incogni.com/budgeting-apps-research/) | Medium |
| This study: 10.2% of substantive 1–3★ reviews say they cancelled, uninstalled or switched; 3.1% say they fell back to a spreadsheet | n = 4,820 | Own analysis (method above) | Medium |
| "67% of people who try budgeting apps quit within 30 days"; "Apptopia 2024… Day-30 retention for personal finance apps at 38%"; "Sensor Tower… losing 71% of DAU Day 1→30"; "Plaid… 34% of bank sync connections require re-authorization within 90 days… 68% of users choose to stop using the app"; "CFPB 2024… 67%… 'too much effort to maintain'" | – | Strategia-X blog, 2026-04-12 [link](https://www.strategia-x.com/blog/2026-04-12-why-budgeting-apps-fail-30-days-fintech-ux-data) | **[unverified]**: no primary links given, and I could not locate the cited Apptopia, Plaid or CFPB reports. Do not quote in investor materials without verification. |
| "72% of app users quit budgeting…"; "74% of Americans say they have a budget, but only 36% actively track spending (NerdWallet, 2025)" | – | aimoneyvault.app (snippet) [link](https://aimoneyvault.app/resources/articles/expense-tracking-statistics-2026) | **[unverified]**: secondary aggregation |

**Takeaway**: the hard primary evidence (the Irrational Labs RCT, Plaid's own statement, Adjust's benchmarks) all points the same way. Engagement with budgeting features decays within weeks even when friction is minimized, and every re-auth or manual step costs users. Single-digit day-30 retention is the norm for the category.

---

## Sources

**Store review datasets (pulled 2026-10-05)**
- App Store RSS feed pattern: https://itunes.apple.com/us/rss/customerreviews/page=1/id={ID}/sortby=mostrecent/json
- App Store review listings:
  - YNAB https://apps.apple.com/us/app/id1010865877?see-all=reviews
  - Monarch https://apps.apple.com/us/app/id1459319842?see-all=reviews
  - Copilot https://apps.apple.com/us/app/id1447330651?see-all=reviews
  - Rocket Money https://apps.apple.com/us/app/id1130616675?see-all=reviews
  - EveryDollar https://apps.apple.com/us/app/id942571931?see-all=reviews
  - Goodbudget https://apps.apple.com/us/app/id471112395?see-all=reviews
  - Simplifi https://apps.apple.com/us/app/id1449777194?see-all=reviews
  - PocketGuard https://apps.apple.com/us/app/id949414211?see-all=reviews
  - Spendee https://apps.apple.com/us/app/id635861140?see-all=reviews
  - Money Manager https://apps.apple.com/us/app/id560481810?see-all=reviews
  - Money Manager IN https://apps.apple.com/in/app/id560481810?see-all=reviews
  - Emma https://apps.apple.com/us/app/id1270062373?see-all=reviews
  - Buddy https://apps.apple.com/us/app/id936422955?see-all=reviews
  - Toshl https://apps.apple.com/us/app/id921590251?see-all=reviews
  - Money Lover https://apps.apple.com/us/app/id486312413?see-all=reviews
  - Spending Tracker https://apps.apple.com/us/app/id1525179720?see-all=reviews
  - Wallet IN https://apps.apple.com/in/app/id1032467659?see-all=reviews
  - axio IN https://apps.apple.com/in/app/id6780629197?see-all=reviews
  - Fold IN https://apps.apple.com/in/app/id1626994334?see-all=reviews
- Google Play listings:
  - axio https://play.google.com/store/apps/details?id=com.daamitt.walnut.app
  - Money Manager https://play.google.com/store/apps/details?id=com.realbyteapps.moneymanagerfree
  - YNAB https://play.google.com/store/apps/details?id=com.youneedabudget.evergreen.app
  - Monarch https://play.google.com/store/apps/details?id=com.monarchmoney.mobile
  - Wallet https://play.google.com/store/apps/details?id=com.droid4you.application.wallet
  - Cashew https://play.google.com/store/apps/details?id=com.budget.tracker_app
  - Bluecoins https://play.google.com/store/apps/details?id=com.rammigsoftware.bluecoins
  - Spendee https://play.google.com/store/apps/details?id=com.cleevio.spendee
  - Goodbudget https://play.google.com/store/apps/details?id=com.dayspringtech.envelopes
  - Money Lover https://play.google.com/store/apps/details?id=com.bookmark.money
  - Rocket Money https://play.google.com/store/apps/details?id=com.truebill
  - PocketGuard https://play.google.com/store/apps/details?id=com.pocketguard.android.app
  - EveryDollar https://play.google.com/store/apps/details?id=com.everydollar.android

**Reddit** (all URLs inline above). Key threads:
- https://www.reddit.com/r/budget/comments/1jufr9q/
- https://www.reddit.com/r/budget/comments/1rmmdx1/
- https://www.reddit.com/r/budget/comments/1jfpx50/
- https://www.reddit.com/r/budget/comments/1mx47i0/
- https://www.reddit.com/r/budget/comments/1lm435j/
- https://www.reddit.com/r/budget/comments/1lps837/
- https://www.reddit.com/r/ynab/comments/1qscdcd/
- https://www.reddit.com/r/ynab/comments/1efl8s5/
- https://www.reddit.com/r/ynab/comments/1dta2it/
- https://www.reddit.com/r/ynab/comments/1m33ki0/
- https://www.reddit.com/r/ynab/comments/1wo4cxy/
- https://www.reddit.com/r/ynab/comments/1txihyw/
- https://www.reddit.com/r/ynab/comments/1prlhzd/
- https://www.reddit.com/r/ynab/comments/1wwezeu/
- https://www.reddit.com/r/ynab/comments/1o7kd3i/
- https://www.reddit.com/r/ynab/comments/1bzt3dd/
- https://www.reddit.com/r/ynab/comments/1umm2ry/
- https://www.reddit.com/r/MonarchMoney/comments/1jq6ze4/
- https://www.reddit.com/r/MonarchMoney/comments/1tr3jkx/
- https://www.reddit.com/r/MonarchMoney/comments/1uqy1ey/
- https://www.reddit.com/r/MonarchMoney/comments/1w2kkc7/
- https://www.reddit.com/r/MonarchMoney/comments/1wjim5k/
- https://www.reddit.com/r/MonarchMoney/comments/1qdf18q/
- https://www.reddit.com/r/MonarchMoney/comments/1ss0zlg/
- https://www.reddit.com/r/MonarchMoney/comments/1ss59eg/
- https://www.reddit.com/r/MonarchMoney/comments/1pxcdnz/
- https://www.reddit.com/r/MonarchMoney/comments/1i4ix2f/
- https://www.reddit.com/r/MonarchMoney/comments/1e6al65/
- https://www.reddit.com/r/MonarchMoney/comments/1ut7wpw/
- https://www.reddit.com/r/copilotmoney/comments/1ufqulv/
- https://www.reddit.com/r/copilotmoney/comments/1qktp4w/
- https://www.reddit.com/r/povertyfinance/comments/1no5u62/
- https://www.reddit.com/r/povertyfinance/comments/1t1j7j7/
- https://www.reddit.com/r/povertyfinance/comments/1u7fjud/
- https://www.reddit.com/r/personalfinance/comments/1so4ner/
- https://www.reddit.com/r/personalfinance/comments/1px18hk/
- https://www.reddit.com/r/IndiaInvestments/comments/17feuei/
- https://www.reddit.com/r/androidapps/comments/1hxnq7h/
- https://www.reddit.com/r/androidapps/comments/1ugaol9/
- https://www.reddit.com/r/selfhosted/comments/1mfs6ro/
- https://www.reddit.com/r/iosapps/comments/1w6qr7a/

**Hacker News**
- Mint shutdown thread https://news.ycombinator.com/item?id=38114512
- Firefly III 2024 https://news.ycombinator.com/item?id=39392428
- Beancount 95% automatic https://news.ycombinator.com/item?id=43268454
- Ask HN: How do you budget https://news.ycombinator.com/item?id=38780485
- YNAB removes direct imports https://news.ycombinator.com/item?id=39773819

**Trustpilot** (snippets)
- https://uk.trustpilot.com/review/ynab.com?page=3
- https://www.trustpilot.com/review/ynab.com?page=11
- https://www.trustpilot.com/review/ynab.com?page=103
- https://www.trustpilot.com/review/rocketmoney.com?page=2

**Blogs, news and research**
- https://okane.finance/blog/2026/02/03/why-i-left-ynab
- https://www.fincomparelab.com/guides/ynab-pricing/
- https://fortune.com/article/ynab-pros-and-cons
- https://www.zdnet.com/home-and-office/intuit-kills-mint-and-personal-finance-app-users-are-unhappy/
- https://irrationallabs.com/blog/money-budgeting-experiment/
- https://www.behavioraleconomics.com/the-budgeting-app-trap-when-spending-information-backfires/
- https://www.prnewswire.com/news-releases/academy-bank-research-reveals-how-budgeting-apps-enable-financial-success--and-where-they-fall-short-302470595.html
- https://www.winnie-app.com/blog/finance-app-retention
- https://www.strategia-x.com/blog/2026-04-12-why-budgeting-apps-fail-30-days-fintech-ux-data (unverified claims)
- https://www.adjust.com/resources/guides/user-retention/
- https://sendbird.com/blog/app-retention-benchmarks-broken-down-by-industry
- https://www.businessofapps.com/data/finance-app-benchmarks
- https://www.emarketer.com/content/finance-apps-keep-users-engaged-longer-than-any-other-industry
- http://getstream.io/blog/app-retention-guide
- https://appimp.app/markets/budgeting-apps
- https://plaid.com/blog/how-to-make-re-authentication-better-our-response-to-the-fcas-consultation/
- https://blog.incogni.com/budgeting-apps-research/
- https://fintrakk.com/best-personal-expense-tracker-app-india/
- https://support.google.com/googleplay/android-developer/answer/10208820?hl=en
- https://dev.to/zeta_byte/ive-been-working-on-a-personal-finance-app-called-finvantage-and-i-recently-hit-a-roadblock-4mjh
- https://racheleditullio.com/blog/2025/09/ynab-adds-a-home-screen-to-its-ios-mobile-app-but-is-it-accessible/
- https://www.applevis.com/forum/ios-ipados/accessible-budgeting-apps-iosipad
- https://checkthat.ai/brands/rocket-money/reviews
- https://www.ynab.com/whats-new/more-banks-europe-edition
- https://budgetsyncer.com/sync/ynab-supported-banks-europe

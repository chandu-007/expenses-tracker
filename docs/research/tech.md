# Technology Landscape & 2025–2026 Trends for a Best-in-Class Expense Tracker

Research date: 2026-10-05. Every provider/tech claim has a URL. `[unverified]` = could not confirm from a primary/secondary source; `[INFERENCE]` = my reasoning from the cited facts, not a sourced claim. Vendor-authored numbers are flagged as vendor claims.

---

## 1. Bank data aggregation

### 1.1 Provider matrix

| Provider | Region | Pricing (what is verifiable) | Notes / reliability signals |
|---|---|---|---|
| **Plaid** | US/CA (+UK/EU) | Three plans: Pay-as-you-Go (no commitment, "standard pricing"), Growth (12-mo commitment, discounts), Custom. Limited Production: 200 free live API calls per product. Pricing models: one-time per connected account, monthly subscription per connected account (e.g. Transactions), or per-request. [plaid.com/pricing](https://plaid.com/pricing/) · Trial plan "Limited to 10 Items". [Plaid billing docs](https://plaid.com/docs/account/billing) · Exact per-item rates not public; a 2020 forum post cited ~$1.50 connect + $0.30/account/month (dated, `[unverified]` today). [Emma community](https://community.emma-app.com/t/plaid-is-expensive-how-do-you-survive/2051) | Largest US network. Also offers Enrich (merchant cleanup) billed per transaction sent. [Plaid Enrich docs](https://plaid.com/docs/enrich/) Plaid now ships MCP servers (Dashboard MCP etc.). [Plaid MCP docs](https://plaid.com/docs/resources/mcp) |
| **MX** | US | Not published; Vendr reports max contract ~$90k. [Vendr](https://www.vendr.com/buyer-guides/mx-technologies) | Monarch uses MX as one of 3 providers. [Monarch help](https://help.monarch.com/hc/en-us/articles/33707613533972-Understanding-Data-Providers-and-Connections) |
| **Finicity (Mastercard)** | US | Not published `[unverified]` | Monarch's status page has logged "Finicity connections are down" incidents. [Monarch status](https://status.monarch.com/cm7wduh1f001dhk2t9n5js5md) |
| **Akoya** | US | Not published | FDX-standard, tokenized, OAuth-only network; third-party profile claims 4,500+ FIs and direct partnerships with Chase, Wells, BofA, Citi, Fidelity. [api-evangelist/akoya](https://github.com/api-evangelist/akoya), [Fintable](https://fintable.io/coverage/providers/AKOYA) |
| **Yodlee** | Global | Not published; Plaid-vs-Yodlee analysis cites base platform fees ~$1–2k/month (secondary). [Monetizely](https://www.getmonetizely.com/articles/plaid-vs-yodlee-how-much-will-financial-data-apis-cost-your-fintech) | Sold by Envestnet to PE firm STG, closed 2 Sep 2025. [Envestnet PR](https://www.envestnet.com/newsroom/press-releases/envestnet-inc-completes-sale-yodlee-inc-stg) |
| **SimpleFIN Bridge** | US/CA | **User pays** $1.50/month or $15/year; up to 25 institutions and 25 apps. [bridge.simplefin.org](https://bridge.simplefin.org/) | Read-only "RSS for financial data" protocol. [simplefin.org](https://www.simplefin.org/index.html) Used by Actual Budget; a 2025 user: "$15/year compared to YNAB's $109/year". [racedorsey.com](https://racedorsey.com/posts/2025/you-need-an-actual-budget) |
| **GoCardless Bank Account Data (ex-Nordigen)** | EU/UK | Free tier — **closed to new signups** (Actual: "From July 2025 onwards, GoCardless has stopped accepting new Bank Account Data accounts"). [Actual docs](https://actualbudget.org/docs/advanced/bank-sync/gocardless), [GoCardless page](https://bankaccountdata.gocardless.com/new-signups-disabled) | Existing users continue; OSS projects migrating away. [Penge issue](https://github.com/autoditac/Penge/issues/120) |
| **Enable Banking** | EU (2,700+ banks / 30 countries) | Free "restricted production" only for accounts you link yourself; commercial otherwise. [enablebanking.com](https://enablebanking.com/), [Enable accounts API](https://enablebanking.com/accounts-api) | Now Actual's EU provider. [Actual bank-sync](https://actualbudget.org/docs/advanced/bank-sync) Most EU providers require own eIDAS QWAC/QSeal certs; a (vendor-affiliated) post cites €2k–10k/yr. [dev.to](https://dev.to/johnfrandsen/gocardless-bank-account-data-alternatives-what-to-use-when-signups-are-disabled-326d) |
| **TrueLayer** | UK/EU | "Talk to us" — not published. [truelayer.com/data](https://truelayer.com/data/) | — |
| **Salt Edge** | 5,000+ banks, 50+ countries | Not published `[unverified]`. [Salt Edge](https://www.saltedge.com/products/account_information) | Requires AISP licence or Salt Edge partner programme in PSD2 markets. [Salt Edge docs](https://docs.saltedge.com/account_information/v5) |
| **Basiq (Cuscal)** | Australia (CDR) | $0.50/user/month data + $0.25/user/month enrichment + platform access fee; **12-month minimum**. [basiq.io/pricing](https://www.basiq.io/pricing) | 135+ AU institutions. |
| **India AA – Setu, Finvu, OneMoney, CAMS** | India | Charged "per statement pulled from a FIP"; FIP fees "not yet determined by Sahamati" (2023 FAQ). [Setu support](https://support.setu.co/support/solutions/articles/81000410140-how-does-pricing-work-in-this-ecosystem-) | See 1.3. |

### 1.2 Reliability is the #1 structural problem
- Even Monarch, with a dedicated connectivity team, uses **three** aggregators (Plaid, Finicity, MX) and lets users switch providers per institution; it lists causes: bank security changes, refresh limits, banks blocking data, credential-based "legacy" connections breaking when a bank changes login. [Monarch help](https://help.monarch.com/hc/en-us/articles/33707613533972-Understanding-Data-Providers-and-Connections) It publishes a per-institution, per-provider connection-health dashboard. [Monarch connection status](https://www.monarch.com/connection-status)
- Banks impose refresh caps — Actual limits auto-sync to ≥15 min because "Banks limit how often accounts can be refreshed". [Actual bank-sync](https://actualbudget.org/docs/advanced/bank-sync)
- **Cost pressure rising in US:** JPMorgan secured deals to be paid by aggregators for data access (Plaid deal Sept 2025; others Nov 2025). [JPMC PR](https://www.jpmorganchase.com/newsroom/press-releases/2025/jpmc-plaid-renewed-data-access-agreement), [CNBC](https://www.cnbc.com/2025/11/14/jpmorgan-chase-fintech-fees.html), [Reuters](https://www.reuters.com/sustainability/boards-policy-regulation/jpmorgan-secures-deals-with-fintech-aggregators-over-fees-access-data-cnbc-2025-11-14) `[INFERENCE]` these fees will flow through to app developers' per-item costs.
- **Free indie tiers are disappearing** (GoCardless closed; Tink enterprise-only per a vendor-affiliated post). [dev.to](https://dev.to/johnfrandsen/gocardless-bank-account-data-alternatives-what-to-use-when-signups-are-disabled-326d)
- **Apple FinanceKit** gives on-device access to Apple Card / Apple Cash / Apple Savings transactions (iOS 17.4+) with user consent; supports background delivery. [Apple FinanceKit](https://developer.apple.com/documentation/financekit), [MacStories](https://www.macstories.net/linked/financekit-opens-real-time-apple-card-apple-cash-and-apple-savings-transaction-data-to-third-party-apps) Apple piloted UK open-banking balances in Wallet. [PaymentsJournal](https://www.paymentsjournal.com/apple-is-piloting-open-banking-in-the-uk) Whether FinanceKit now exposes UK bank accounts to third parties is `[unverified]`.

### 1.3 India: Account Aggregator (AA)
- Scale (Sahamati, 2 Sep 2026): 500M+ fulfilled consents, 1,100+ regulated entities live, 2.8B accounts enabled, 310M+ accounts linked; Sahamati recognised by RBI as SRO in June 2026. [Sahamati PR](https://sahamati.org.in/media-article/account-aggregator-crosses-500-million-consents-as-india-marks-5th-aa-foundation-day)
- **Gate for us:** to become an FIU you must be "registered and regulated by at least one of the Financial Service Regulators … RBI, SEBI, IRDAI, PFRDA", implement ReBIT specs, get certified by a Sahamati-empanelled auditor. [Sahamati FIU](https://sahamati.org.in/financial-information-user-fiu) Fold met this by registering as a **SEBI Investment Adviser** (INA000018072) and partnering with Finvu and CAMS. [Fold blog](https://fold.money/blog/privacy-by-design)
- **Coverage gap:** "Currently only asset based data is available (bank accounts, deposits, mutual funds, insurance policies, pension funds)". [Sahamati FIU FAQ](https://sahamati.org.in/financial-information-user-fiu) → **credit cards are not on AA**. Fold therefore tracks cards via email, "fetched and processed on the device, and only transaction-related data is sent to our servers". [Fold blog](https://fold.money/blog/privacy-by-design)
- New FIPs coming: GST, CBDT, EPFO. [Sahamati PR](https://sahamati.org.in/media-article/account-aggregator-crosses-500-million-consents-as-india-marks-5th-aa-foundation-day) RBI issued revised NBFC-AA Directions on 28 Nov 2025. [RBI directions PDF](https://avantiscdnprodstorage.blob.core.windows.net/legalupdatedocs/50396/Reserve-Bank-of-India-Non-Banking-Financial-Companies-Account-Aggregator-Directions-2025-Dec052025.pdf)

### 1.4 Regulation status
**US – CFPB §1033 (open banking):** Final rule Oct 2024 (compliance from 1 Apr 2026 for largest providers). A Kentucky federal court **enjoined** CFPB enforcement; Sixth Circuit appeal stayed while CFPB rewrites. Aug 2025 ANPR reconsidered fees, "representative" definition, privacy/security. Bloomberg Law reports the plan would permit bank fees after a free quota. NY bills A10640/S9483 (Mar 2026) propose a stricter state "mini-1033" banning fees. The GUARD Financial Data Act would codify screen-scraping as a parallel channel. [Consumer Finance Monitor, 26 Jun 2026](https://www.consumerfinancemonitor.com/2026/06/26/open-banking-regulation-in-2026-federal-regulation-resurfaces-as-states-bring-data-sharing-into-focus) On ~6 Aug 2026 the CFPB sent a "Personal Financial Data Rights Reconsideration" NPRM to OIRA; text not yet public. [Consumer Finance Monitor, 6 Aug 2026](https://www.consumerfinancemonitor.com/2026/08/06/cfpb-sends-new-section-1033-open-banking-proposal-to-oira-for-review), [CRS IF13117](https://www.congress.gov/crs_external_products/IF/HTML/IF13117.html)
→ Net: **no enforceable US data-access right as of Oct 2026; access costs likely to rise.**

**EU – PSD3/PSR:** Provisional political agreement 27 Nov 2025; ECON approved the agreed text 5 May 2026. [EP Legislative Train (search snippet)](https://www.europarl.europa.eu/legislative-train/carriage/payment-services-and-electronic-money-services-%28directive%29/report), [FSUG minutes Feb 2026](https://finance.ec.europa.eu/document/download/e0b77115-3e16-4b64-bcd4-48837372ee3f_en?filename=fsug-260226-minutes_en.pdf), [MoFo/JDSupra](https://www.jdsupra.com/legalnews/psd3-and-the-payment-services-4770204)
**EU – FIDA (open finance):** Trilogues stalled June 2025; Commission circulated a non-paper on finalisation options (2026) with a "simplification" narrowing of data scope; renewed movement reported May 2026; still not adopted. [Council WK 5041/2026](https://data.consilium.europa.eu/doc/document/WK-5041-2026-INIT/en/pdf), [Simmons & Simmons](https://www.simmons-simmons.com/en/publications/cmb852yp8012evch02bl6i90g/fida-simplification-is-the-buzzword), [Projective Group, 22 May 2026](https://www.projectivegroup.com/fida-back-in-motion-from-ambitious-to-workable), [Matheson](https://www.matheson.com/insights/the-third-payment-services-directive-and-payment-services-regulation)

---

## 2. Data capture

### 2.1 SMS parsing (India/Android)
- **Google Play allows it** as a temporary-exception use case: "SMS-based money management — e.g. apps that track and manage budget" → `READ_SMS, RECEIVE_SMS, RECEIVE_MMS, RECEIVE_WAP_PUSH`, subject to Play review + Permissions Declaration Form; must be "core functionality"; spyware policy: "budgeting apps may not exfiltrate or share non-financial or personal SMS history". [Play SMS policy](https://support.google.com/googleplay/android-developer/answer/10208820?hl=en-GB)
- **iOS forbids SMS reading.** Workaround: user-built Shortcuts "Message contains" personal automations that forward bank SMS to the app; sender filters unreliable for bank short-codes. [Finvot docs](https://finvot.app/en/docs/sms), [Pocket Clear](https://pocketclear.app/blog/auto-log-apple-pay-bank-sms-iphone-shortcuts.html), [Apple dev forum](https://developer.apple.com/forums/thread/705659). Community note: such shortcuts often extract amount/card but "*not* the merchant name". [Technofino](https://technofino.in/community/threads/expense-tracker-for-ios-mac-using-shortcuts-app.40920/)
- **Erosion risk:** Indian banks asked RBI (Oct 2025) to drop SMS alerts for txns < ₹100 (cost ~₹0.20/SMS), shifting to email/app/WhatsApp. [APAC News Network](https://apacnewsnetwork.com/2025/10/banks-seek-rbi-approval-to-stop-low-value-sms-alerts-for-customers/), [Upstox](https://upstox.com/news/business-news/latest-updates/banks-seek-waiver-from-sms-alert-for-transactions-below-100-report/article-183039/) RBI Authentication Directions 2025 move beyond SMS OTP from 1 Apr 2026. [Authsignal](https://www.authsignal.com/blog/articles/india-and-the-uae-are-phasing-out-sms-otp) → SMS-only trackers "silently miss transactions" as alerts move channels. [FinArt](https://finart.app/automatic-expense-tracker-iphone/)

### 2.2 Email receipt / statement parsing
- `gmail.readonly`, `gmail.metadata`, `gmail.modify` are **restricted** scopes. "If you store restricted scope data on servers (or transmit), then you must go through a security assessment." [Gmail scopes](https://developers.google.com/workspace/gmail/api/auth/scopes) Assessment is annual CASA (OWASP ASVS-based). [Google Cloud help](https://support.google.com/cloud/answer/13465431?hl=en)
- **Cost:** TAC Security CASA Basic $675/app/yr (site); developers report ~$540 minimum annually. [TAC CASA](https://casa.tacsecurity.com/site/home), [r/androiddev](https://www.reddit.com/r/androiddev/comments/1clk1ng/end_of_free_casa_tier_2_certification_for_google), [r/googlecloud](https://www.reddit.com/r/googlecloud/comments/1krsru9/finally_completed_google_casa_tier_2_assessment). Open-source helper: [casaready.org](https://casaready.org/).
- **On-device-only ambiguity:** Google pages conflict on whether a client-only app that never transmits mail data is exempt; Sept 2026 forum threads remain unanswered/hedged ("a CASA assessment may still be required"). [Google forum 398372](https://discuss.google.dev/t/gmail-restricted-scope-is-the-casa-assessment-required-when-mail-is-processed-on-device-and-only-a-derived-subscription-list-reaches-our-server/398372), [forum 340650](https://discuss.google.dev/t/is-casa-required-for-all-access-restricted-scopes/340650)
- **Alternative:** forwarding address — Monarch shipped "Email Forwarding" (July update) [Monarch blog](https://www.monarch.com/blog/july-product-update); Fold offers "manual email forwarding to a Fold email address". [Fold](https://fold.money/blog/privacy-by-design) `[INFERENCE]` A user-set Gmail filter → forward-to-app address avoids restricted scopes entirely.

### 2.3 Receipt OCR
- **On-device iOS 26:** Vision `RecognizeDocumentsRequest` extracts structured text/tables and explicitly targets "receipts, nutritional labels". [Apple docs](https://developer.apple.com/documentation/vision/recognizedocumentsrequest.md)
- **Cloud:** Veryfi claims 99.56% line-item accuracy on expense receipts and 3–5 s latency (vendor benchmarks). [Veryfi line-items](https://www.veryfi.com/technology/line-item-extraction-accuracy-benchmarks), [Veryfi invoice bench](https://www.veryfi.com/ai-insights/invoice-ocr-competitors-veryfi). Mindee publishes a benchmarking methodology/tool. [Mindee](https://www.mindee.com/blog/how-to-perform-a-meaningful-ocr-benchmark-a-receipt-ocr-case-study)
- Monarch added receipt scanning (Winter 2026 release). [Monarch winter release](https://www.monarch.com/blog/winter-release)

### 2.4 Voice / natural-language entry
- iOS 26 `SpeechAnalyzer`/`SpeechTranscriber`: on-device, system-managed models [WWDC25](https://developer.apple.com/videos/play/wwdc2025/277/); tests show it ~2× faster than Whisper. [MacRumors](https://www.macrumors.com/2025/06/18/apple-transcription-api-faster-than-whisper), [AppleInsider](https://appleinsider.com/articles/25/06/18/apple-intelligence-transcription-is-twice-as-fast-as-openais-whisper)
- Android ML Kit GenAI Speech Recognition: basic mode on most API 31+ devices; advanced (GenAI) mode Pixel 10/11 only. [ML Kit GenAI](https://developers.google.com/ml-kit/genai)
- Action button / Siri: "Log 34 dollars at the pharmacy" flows via App Intents/Shortcuts. [Finny](https://getfinny.app/blog/action-button-expense-tracker-iphone-15-16-pro)
- Chat-native entry is emerging via MCP ("Connect Claude or ChatGPT, then just say what you spent"). [MyFinance MCP](https://myfinance-mcp.com/)

### 2.5 LLM categorization (accuracy, cost, on-device)
- **Accuracy evidence:** Zero-shot GPT-4o 60.4% vs fine-tuned calibrated FinBERT 73.4% on SME bank-transaction categories. [arXiv 2508.05425](https://arxiv.org/html/2508.05425v1) Small proprietary models beat LLMs on transaction understanding. [arXiv 2509.25803](https://arxiv.org/pdf/2509.25803) A production LoRA-tuned Llama-3.1-8B hits ~97% F1 on merchant extraction; paper studies 270M–8B models. [arXiv 2606.08051](https://arxiv.org/pdf/2606.08051)
- **Consumer apps:** reviewers report ~90% first-pass accuracy for Monarch (with persistent "Refine Rules") and Copilot (per-user model, after ~30 reviewed txns). [Finny Monarch review](https://getfinny.app/blog/monarch-money-review-2026), [BudgetPeer](https://www.budgetpeer.com/blog/is-copilot-money-worth-it-an-honest-look-at-the-premium-budget-app). Monarch uses "third-party LLMs and our own in-house modeling". [Monarch AI help](https://help.monarch.com/hc/en-us/articles/37526856682260-AI-in-Monarch)
- **Cloud cost:** Gemini 2.5 Flash-Lite $0.10/M in, $0.40/M out (doubling 1 Jan 2027 per one tracker); GPT-5 nano $0.05/$0.40. [OpenRouter Flash-Lite](https://openrouter.ai/google/gemini-2.5-flash-lite), [Morph](https://www.morphllm.com/gemini-api-pricing), [OpenRouter GPT-5 nano](https://openrouter.ai/openai/gpt-5-nano). `[INFERENCE]` ~100 input + 10 output tokens/txn → ≈ $0.014 per 1,000 transactions; cost is negligible vs. aggregator fees — the issue is privacy, not price.
- **On-device:**
  - Apple Foundation Models (iOS/macOS 26+): ~3B on-device LLM, guided generation (structured output), tool calling, free inference, offline. [Apple docs](https://developer.apple.com/documentation/FoundationModels), [Apple newsroom](https://www.apple.com/newsroom/2025/09/apples-foundation-models-framework-unlocks-new-intelligent-app-experiences/). Model updated in iOS 26.4 ("improves instruction-following and tool-calling"), so outputs can change with OS updates. [Apple updates](https://developer.apple.com/documentation/Updates/FoundationModels) Finance app MoneyCoach already uses it for weekly insights and category suggestions. [TechCrunch](https://techcrunch.com/2025/10/03/how-developers-are-using-apples-local-ai-models-with-ios-26) Device eligibility (Apple Intelligence-capable devices only) `[unverified in this research]`.
  - Gemini Nano Prompt API (ML Kit GenAI): **narrow device list** (Pixel 9/10/11, Galaxy S26, select OnePlus/Xiaomi/vivo/OPPO flagships…); per-app inference quota; **foreground-only** ("BACKGROUND_USE_BLOCKED"). [ML Kit GenAI overview](https://developers.google.com/ml-kit/genai), [Prompt API](https://developers.google.com/ml-kit/genai/prompt/android)
  - `[INFERENCE]` In India most Android users are on mid-range devices outside the Nano list → on-device LLM must be an enhancement, never a dependency.

### 2.6 Merchant cleanup / enrichment
| API | Pricing | Notes |
|---|---|---|
| Plaid Enrich | per transaction, not public | [Plaid docs](https://plaid.com/docs/enrich/) |
| Ntropy | 2,000 free txns, then usage-based ("pennies per transaction") | [Ntropy](https://www.ntropy.com/txen), [Ntropy blog](https://www.ntropy.com/blog/build-ai-supplier-payment-agent) |
| Spade | free tier listed; claims 99.9% US/CA merchant coverage, 99%+ accuracy, P99 < 40 ms; $40M Series B Mar 2026 | [BusinessWire](https://www.businesswire.com/news/home/20260324472369/en/Spade-Announces-$40m-Series-B-Raise-to-Build-The-Data-and-AI-Platform-for-Modern-Finance), [SourceForge](https://sourceforge.net/software/product/Spade-API/) |
| Heron Data | custom, no free plan; SMB-lending focus | [SaaSworthy](https://www.saasworthy.com/product/heron-data-io/pricing) |
| Basiq enrichment (AU) | $0.25/user/month | [Basiq](https://www.basiq.io/pricing) |
None of these cover Indian UPI VPAs/merchant strings in their published claims `[INFERENCE]` → India enrichment likely needs our own dictionary + LLM.

---

## 3. Architecture for responsiveness

### 3.1 Local-first & sync engines
**How Actual Budget does it (proven PFM reference):** entire app logic runs on the client over local SQLite; optional server only relays CRDT messages and proxies bank sync. Each change is a CRDT message stamped with a **Hybrid Logical Clock**; a Merkle tree of timestamps finds divergence; clocks >1 min skewed are rejected. [James Long – Using CRDTs in the Wild](https://archive.jlongster.com/using-crdts-in-the-wild), [crdt-example-app](https://github.com/jlongster/crdt-example-app), [DeepWiki actual](https://deepwiki.com/actualbudget/actual). Optional **E2EE** with a password-derived key; server stores ciphertext. Caveat: bank-sync tokens are **not** E2EE-covered. [Actual sync docs](https://actualbudget.org/docs/getting-started/sync/), [Actual bank-sync](https://actualbudget.org/docs/advanced/bank-sync)

| Engine | Status (2025–26) | Fit for us |
|---|---|---|
| **PowerSync** | Postgres/Mongo/MySQL/SQL Server ↔ client SQLite; SDKs Flutter, RN/Expo, Kotlin MP, Swift, JS. Free (2 GB synced/mo, 50 peak clients), Pro from $49/mo, Team from $599/mo; Sept 2025 pricing moved to "data synced", peak-client overage raised to $30/1k. [PowerSync pricing](https://powersync.com/pricing), [pricing blog](https://powersync.com/blog/simplified-cloud-pricing-based-on-data-synced) (search summary listed KMP beta / Swift alpha; current maturity `[unverified]`) | Strong for mobile SQLite; server sees plaintext unless we encrypt fields client-side `[INFERENCE]`. |
| **ElectricSQL** | 1.0 GA Mar 2025 (read-path Postgres sync) [Electric blog](https://electric.ax/blog/2025/03/17/electricsql-1.0-released); **joined Databricks 11 Aug 2026; Electric Cloud winding down**, OSS stays OSS. [Electric blog](https://electric.ax/blog/2026/08/11/electric-joining-databricks), [Databricks](https://www.databricks.com/blog/electric-joins-databricks-bring-wasm-postgres-ai-agent-sandboxes); critical SQLi CVE-2026-40906 in `/v1/shape`. [TheHackerWire](https://www.thehackerwire.com/electricsql-v1-shape-critical-sql-injection-cve-2026-40906/) | Avoid for a new consumer app (vendor risk). |
| **Zero (Rocicorp)** | 1.0 June 2026, web sync engine with Postgres cache. [InfoQ](https://www.infoq.com/news/2026/06/zero-version-1/) | Web-centric. |
| **Replicache** | Maintenance mode; repo archived 10 Jun 2026. [replicache.dev](https://replicache.dev/), [GitHub](https://github.com/rocicorp/replicache) | Avoid. |
| **Triplit** | Co-founder joined Supabase Oct 2025; not integrated into Supabase; code further open-sourced. [Supabase blog](https://supabase.com/blog/triplit-joins-supabase) | Avoid (stewardship uncertain). |
| **Automerge 3** | Rust core; up to 100× memory reduction (Moby Dick 700 MB → 1.3 MB). [Ink & Switch](https://www.inkandswitch.com/newsletter/dispatch-011/), [HN](https://news.ycombinator.com/item?id=44777086) | Document CRDT; overkill for row-shaped ledgers `[INFERENCE]`. |
| **Evolu** | SQLite + CRDT + E2EE sync/backup ("post-quantum safe"), TypeScript/React Native. [evolu.dev](https://www.evolu.dev/), [npm](https://www.npmjs.com/package/evolu) | Closest off-the-shelf match to Actual's model for RN. |
| **Ditto** | Embedded CRDT DB with P2P mesh; $82M Series B Mar 2025. [SiliconANGLE](https://siliconangle.com/2025/03/12/ditto-raises-82m-funding-edge-database/) | Enterprise pricing `[unverified]`. |

### 3.2 Cross-platform framework performance
- **SynergyBoat 2025 benchmark** (iPhone 16 Plus, Galaxy Z Fold 6; agency blog, single study): TTFF Flutter 16.67 ms vs RN iOS 32.96 ms vs Swift native 41.37 ms; scroll test iOS dropped frames Flutter 0% vs RN 15.51%. [SynergyBoat](https://www.synergyboat.com/blog/flutter-vs-react-native-vs-native-performance-benchmark-2025)
- **React Native** improved: 0.82 fully New Architecture + experimental Hermes V1 [Creole Studios](https://www.creolestudios.com/react-native-0-82-new-architecture-update/); FlashList v2 rewrite: "up to 50% reduced blank area". [Shopify Engineering](https://shopify.engineering/flashlist-v2)
- **Compose Multiplatform iOS** stable since 1.8.0 (6 May 2025): JetBrains claims native-comparable startup, SwiftUI-par scrolling, +~9 MB size (no third-party benchmark found). Concurrent rendering default since 1.11 (May 2026). [JetBrains blog](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/), [CMP 1.11](https://blog.jetbrains.com/kotlin/2026/05/compose-multiplatform-1-11-0/)
- Contradictory memory claims exist across blogs; treat all cross-framework numbers as indicative only. [ideamagix](https://www.ideamagix.com/blog/flutter-vs-react-native-for-mobile-apps/), [appiko](https://appiko.co/blog/react-native-vs-flutter-2025)

### 3.3 Quick-entry surfaces (critical for "very responsive")
- iOS 17 interactive widgets (Button/Toggle backed by App Intents); iOS 18 **Control Center / Lock Screen / Action button controls** via `ControlWidget`; "the same intent powers a Control Center control, a Live Activity button, a Siri phrase, and a Shortcuts action". [WWDC24](https://developer.apple.com/videos/play/wwdc2024/10157/), [Blake Crosley](https://blakecrosley.com/blog/ios-26-widget-and-control-surface)
- **All cross-platform frameworks require native code for these**: Flutter `home_widget` "does not allow writing Widgets with Flutter itself" and Live Activities must be SwiftUI. [luksza.org](https://luksza.org/2026/add-a-native-home-screen-widget-to-your-flutter-app/), [live_activity_kit](https://pub.dev/packages/live_activity_kit). Expo Widgets can author iOS widgets/Live Activities from `@expo/ui/swift-ui` components only (no RN views/hooks). [Expo docs](https://docs.expo.dev/versions/latest/sdk/widgets/), [Expo blog](https://expo.dev/blog/home-screen-widgets-and-live-activities-in-expo)
- Android: Quick Settings `TileService`, one-tap add via `requestAddTileService()` (Android 13+) [Android docs](https://developer.android.com/develop/ui/views/quicksettings-tiles); Glance for widgets and Wear OS tiles. [Glance](https://developer.android.com/jetpack/androidx/releases/glance), [Wear OS UI](https://developer.android.com/training/wearables/user-interfaces)

### 3.4 End-to-end encryption approaches
1. **Actual-style**: password-derived key encrypts CRDT ops; server is a blind relay. [Actual sync](https://actualbudget.org/docs/getting-started/sync/)
2. **Evolu**: built-in E2EE relay sync. [evolu.dev](https://www.evolu.dev/)
3. **Server-DB sync (PowerSync)** + client-side field encryption `[INFERENCE]` — loses server-side querying/AI.
Trade-off `[INFERENCE]`: E2EE blocks server-side categorisation/LLM features and bank-token custody (Actual explicitly carves tokens out) → pair E2EE with on-device ML.

---

## 4. Security & compliance

- **Encryption at rest:** SQLCipher (AES-256, BSD-style Community Edition; must ship licence notice) — "as little as 5–15% overhead"; commercial builds up to 3–4× faster than Community. [Zetetic perf](https://www.zetetic.net/sqlcipher/performance/), [Community licence](https://www.zetetic.net/sqlcipher/community/). Active releases through 4.16 (May 2026); Android 16 KB page-size support added. [Zetetic blog](https://www.zetetic.net/blog/2026/05/12/sqlcipher-4.16.0-release/), [16KB](https://www.zetetic.net/blog/2025/06/26/sqlcipher-for-android-16kb-page-size-support/)
- **Biometric lock done right:** bind biometrics to a Keystore/Keychain key (Android `BiometricPrompt` + `CryptoObject`, Class 3; "A success callback is not security") [Android security](https://developer.android.com/security/fraud-prevention/authentication), [ptkd](https://ptkd.com/journal/android-biometricprompt-secure-usage); iOS Keychain items with biometric access control + Data Protection `NSFileProtectionComplete`. [Talsec](https://docs.talsec.app/appsec-articles/articles/ios-keychain-vs.-android-keystore)
- **India DPDP:** Rules notified 13 Nov 2025; Board rules immediate; consent-manager framework Nov 2026; substantive obligations (notices, purpose limitation, retention/erasure, security safeguards) by **13 May 2027**; breach report to Board within 72 h. [DLA Piper](https://www.dlapiperdataprotection.com/?t=law&c=IN), [Sansa Legal](https://www.sansalegal.com/post/dpdp-act-2023-and-rules-2025-phased-implementation-timeline-and-business-compliance-deadlines), [dcomply rules](https://dpdpa.dcomply.in/rules/), [ConsentOS](https://consentos.in/learn/breach-notification-requirements/)
- **GDPR** applies for EU users (standard; not separately researched here).
- **EU Accessibility Act** (applicable 28 Jun 2025) covers consumer banking services incl. their mobile apps; microenterprises (<10 staff, <€2M) exempt. [Loyens & Loeff](https://www.loyensloeff.com/insights/news--events/news/the-eu-accessibility-act-is-now-live-what-is-the-impact-on-the-financial-sector/) `[INFERENCE]` A read-only PFM likely isn't an "In-Scope Service", but meeting WCAG-level accessibility is cheap insurance and a differentiator.
- **App Store:** 5.1.1(ix) — apps in "banking and financial services" or "that require sensitive user information should be submitted by a legal entity… not by an individual developer"; 3.2.1(viii) money-management apps "should be submitted by the financial institution" (applies to institutions performing such services). [Apple guidelines](https://developer.apple.com/app-store/review/guidelines/) → **publish under a company account.**
- **Google Play:** Financial features declaration mandatory for every app (updates blocked from 30 Oct 2025 without it). [Play announcement](https://support.google.com/googleplay/android-developer/answer/16550159?hl=en), [Play declaration](https://support.google.com/googleplay/android-developer/answer/13849271?hl=en). SMS permissions need declaration form (see 2.1).
- **SOC 2:** Startups typically spend $25k–50k first year (Drata) / $35k–80k all-in Type II (ComplyJet); 6–14 months. "Table stakes for most banking partnerships." [Drata](https://drata.com/learn/soc-2/cost), [ComplyJet](https://www.complyjet.com/blog/soc-2-for-startups), [iomergent](https://iomergent.com/soc-2-for-fintech/). Aggregator onboarding often asks for it `[INFERENCE]`. Fold cites ISO 27001 + India-only AWS data residency. [Fold](https://fold.money/blog/privacy-by-design)
- **Data minimisation precedent:** Fold "doesn't read your emails or messages" for bank data; email for cards processed on-device. [Fold](https://fold.money/blog/privacy-by-design)

---

## 5. Trends (2025–2026)

- **AI assistants:** Monarch Winter 2026 release: AI Assistant, Weekly Recap, receipt scanning, reimagined goals, equity tracking. [Monarch](https://www.monarch.com/blog/winter-release) Cleo 3.0 (29 Jul 2025): two-way voice, long-term memory, agentic tool use. [Cleo blog](https://web.meetcleo.com/blog/Introducing-cleo-3-0), [BusinessWire](https://www.businesswire.com/news/home/20250729690058/en/Cleo-Becomes-the-First-AI-Money-Coach-That-Speaks-Thinks-and-Remembers). Rocket Money Premium+ includes "Rowan" AI assistant. [Plan & Multiply](https://www.planandmultiply.com/en/blog/rocket-money-budget-app-review-alternatives-2026)
- **MCP / bring-your-own-AI:** Plaid offers MCP servers [Plaid](https://plaid.com/docs/resources/mcp); many community Monarch MCP servers [GitHub](https://github.com/felixgalindo/monarch-money-mcp); Monarch's status page lists an "MCP" component (official server `[unverified]`). [Monarch status](https://status.monarch.com/cm7wduh1f001dhk2t9n5js5md)
- **Predictive cash flow / forecasting:** Monarch Forecasting (Plus tier) — net worth & cash flow projections, life events, scenarios, side-by-side compare; "built for directional accuracy, not false precision". [Monarch help](https://help.monarch.com/hc/en-us/articles/48344305092244-Forecasting-in-Monarch) Users still request account-balance projections. [r/MonarchMoney](https://www.reddit.com/r/MonarchMoney/comments/1qe3dlg/feature_request_account_balance_projections/)
- **FIRE / net worth:** ProjectionLab — free Basic (Monte Carlo, backtesting), Premium $129/yr, Pro $549/yr; privacy-first ("We never sell your data"); testimonials praise "I don't need to connect any financial accounts". [ProjectionLab pricing](https://projectionlab.com/pricing)
- **Subscription detection/cancellation:** Rocket Money concierge cancels (2–10 days, not all merchants); bill negotiation fee 35–60% of first-year savings. [WallStreetSurvivor](https://www.wallstreetsurvivor.com/rocket-money-review/), [Penny Hoarder](https://www.thepennyhoarder.com/budgeting/rocket-money-review/). US FTC click-to-cancel rule vacated 8 Jul 2025; ANPRM 13 Mar 2026; state laws (VA, LA, NYC Oct 2026) fill gap. [Crowell](https://www.crowell.com/en/insights/client-alerts/eighth-circuit-cancels-click-to-cancel), [Jones Day](https://www.jonesday.com/en/insights/2026/05/ftc-revives-clicktocancel-rule-new-risks-for-subscription-businesses), [Nolo](https://www.nolo.com/legal-encyclopedia/what-is-the-ftc-click-to-cancel-rule.html). **India:** NPCI OC-223 (7 Oct 2025) lets users view/manage/port all UPI AutoPay mandates from any UPI app (deadline 31 Dec 2025) and via upihelp.npci.org.in. [TeamLease RegTech](https://teamleaseregtech.com/updates/article/47798/npci-issued-a-notification-regarding-the-enhancement-of-upi-autopay/), [Whalesbook](https://www.whalesbook.com/news/English/tech/Tired-of-Surprise-Subscription-Bills-NPCIs-New-Portal-Makes-Managing-Autopay-Effortless/694c9931e10c98d078889f85)
- **Gamification:** Systematic reviews find points/badges/leaderboards most used in fintech [ScienceDirect 2026](https://www.sciencedirect.com/science/article/pii/S2214635026000420), [Ahmad et al. 2025](https://www.nasdaq.com/docs/2026/01/12/Gamification-of-Personal-Finance-A-Systematic-Literature-Review-%28Ahmad-et-al-2025%29.pdf); India-specific study: gamification moderates behavioural traits, still nascent in India. [Wiley FPR 2025](https://onlinelibrary.wiley.com/doi/full/10.1002/cfp2.70016). Practitioner caution: "Streaks aren't the secret to retention." [RevenueCat](https://www.revenuecat.com/blog/growth/gamification-in-apps-complete-guide)
- **Accessibility:** EAA live; Compose MP 1.8 ships VoiceOver/AssistiveTouch/Full Keyboard Access support. [Loyens & Loeff](https://www.loyensloeff.com/insights/news--events/news/the-eu-accessibility-act-is-now-live-what-is-the-impact-on-the-financial-sector/), [JetBrains](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/)
- **Privacy-first positioning:** Actual (local-first + E2EE), ProjectionLab (no linking, no data sale), Fold (AA-only, on-device email). Sources above.
- **On-device AI in finance apps:** MoneyCoach (insights + category suggestions on Apple FM). [TechCrunch](https://techcrunch.com/2025/10/03/how-developers-are-using-apples-local-ai-models-with-ios-26)

---

## Recommendations for our build

### R1. Core principle: local-first, "zero-wait" ledger
- Local SQLite (SQLCipher) is the source of truth; every UI read/write hits local DB; sync is background. Evidence: Actual proves the model for budgets with HLC-stamped CRDT ops + optional E2EE ([Actual](https://actualbudget.org/docs/getting-started/sync/), [jlongster](https://archive.jlongster.com/using-crdts-in-the-wild)); SQLCipher overhead 5–15% ([Zetetic](https://www.zetetic.net/sqlcipher/performance/)).
- **Sync option A (privacy-first, recommended):** Own op-log sync à la Actual (row/field LWW CRDT + HLC + Merkle diff) with E2EE relay. + No vendor risk (Electric/Replicache/Triplit churn shows the risk: [Electric](https://electric.ax/blog/2026/08/11/electric-joining-databricks), [Replicache](https://replicache.dev/)). − Must build it; server-side AI impossible on E2EE data → on-device ML.
- **Sync option B (speed-to-market):** PowerSync (Postgres ↔ SQLite, Flutter/RN/KMP/Swift SDKs, $49/mo Pro) ([PowerSync](https://powersync.com/pricing)). + Mature, multi-SDK. − Server holds plaintext unless we add field encryption; usage-metered.
- **Option C (RN only):** Evolu (SQLite+CRDT+E2EE built-in) ([Evolu](https://www.evolu.dev/)). − Smaller ecosystem `[INFERENCE]`.

### R2. Client stack
| Option | Pros (evidence) | Cons |
|---|---|---|
| **Flutter + native extensions** | Best startup/jank numbers in 2025 benchmark ([SynergyBoat](https://www.synergyboat.com/blog/flutter-vs-react-native-vs-native-performance-benchmark-2025)); PowerSync Dart SDK | Widgets/Live Activities/Controls must be SwiftUI/Kotlin anyway ([luksza](https://luksza.org/2026/add-a-native-home-screen-widget-to-your-flutter-app/)); Apple FM / Gemini Nano via platform channels |
| **KMP shared core + native UI (SwiftUI/Compose) or Compose MP** | Direct access to Foundation Models, App Intents, FinanceKit, Glance, Gemini Nano; CMP iOS stable ([JetBrains](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/)) | More platform code; smaller hiring pool `[INFERENCE]` |
| **React Native (New Arch, Hermes, FlashList v2) + Expo Widgets** | Largest JS talent pool; FlashList v2 gains ([Shopify](https://shopify.engineering/flashlist-v2)); Expo Widgets ([Expo](https://docs.expo.dev/versions/latest/sdk/widgets/)) | Worst iOS jank in the 2025 benchmark; Expo widgets limited to `@expo/ui/swift-ui` |
`[INFERENCE]` For a responsiveness-first app whose differentiators are OS surfaces (widgets, Control Center, Action button, QS tile, watch) and on-device AI, **KMP shared core with native UI** gives the most direct access; **Flutter** is the pragmatic alternative if team velocity dominates. Either way budget for native SwiftUI/Kotlin extension work.

### R3. Capture strategy (layered, ranked by friction)
1. **≤2-second manual entry everywhere:** App Intents-backed widget/Control Center/Action button/Siri; Android QS tile via `requestAddTileService()`, Glance widget, Wear tile ([WWDC24](https://developer.apple.com/videos/play/wwdc2024/10157/), [Android](https://developer.android.com/develop/ui/views/quicksettings-tiles)). Natural-language + voice parsed on-device (SpeechAnalyzer; Apple FM guided generation) with cloud nano-LLM fallback.
2. **India Android:** SMS parsing under Play's "SMS-based money management" exception, on-device only, never upload raw SMS ([Play policy](https://support.google.com/googleplay/android-developer/answer/10208820?hl=en-GB)). Plan for SMS erosion (<₹100 alerts) → also parse bank-app notifications/email `[INFERENCE from` [APAC News](https://apacnewsnetwork.com/2025/10/banks-seek-rbi-approval-to-stop-low-value-sms-alerts-for-customers/)`]`.
3. **India iOS:** guided Shortcuts "Message contains" automation + URL-scheme/App Intent ingest ([Finvot](https://finvot.app/en/docs/sms)).
4. **Email:** start with **forward-to-app address** (no restricted scopes); add Gmail API later only with on-device processing and budget ~$540–675/yr CASA ([TAC](https://casa.tacsecurity.com/site/home)) because exemption is unclear ([Google forum](https://discuss.google.dev/t/gmail-restricted-scope-is-the-casa-assessment-required-when-mail-is-processed-on-device-and-only-a-derived-subscription-list-reaches-our-server/398372)).
5. **Statements:** PDF/CSV import (universal fallback; Indian card statements arrive by email) `[INFERENCE]`.
6. **Receipts:** on-device Vision `RecognizeDocumentsRequest` first ([Apple](https://developer.apple.com/documentation/vision/recognizedocumentsrequest.md)); cloud (Veryfi/Mindee) optional premium.
7. **Bank sync (opt-in, phase 2):** US via SimpleFIN (user-paid $15/yr, cheap for us) and/or Plaid PAYG ([SimpleFIN](https://bridge.simplefin.org/), [Plaid](https://plaid.com/pricing/)); EU via Enable Banking ([Enable](https://enablebanking.com/)); iOS Apple Card via FinanceKit. Build a provider-abstraction layer + per-institution health telemetry like Monarch ([Monarch](https://help.monarch.com/hc/en-us/articles/33707613533972-Understanding-Data-Providers-and-Connections)). **India AA** only after obtaining FSR registration (e.g. SEBI RIA like Fold) — regulatory prerequisite, not a tech task ([Sahamati](https://sahamati.org.in/financial-information-user-fiu), [Fold](https://fold.money/blog/privacy-by-design)).

### R4. Categorisation pipeline
User rules (deterministic, instant) → per-user learned merchant map (Copilot-style; ~90% after ~30 corrections per reviews) → on-device LLM with guided generation where available → cloud nano-LLM on minimal, de-identified strings (≈$0.014/1k txns `[INFERENCE]`) → later a fine-tuned small model (evidence that fine-tuned small models beat zero-shot GPT-4o: [arXiv 2508.05425](https://arxiv.org/html/2508.05425v1), [2606.08051](https://arxiv.org/pdf/2606.08051)). Build India merchant/UPI-VPA dictionary in-house.

### R5. Security/compliance baseline
Company-entity developer accounts (App Store 5.1.1(ix)); Play Financial features + SMS declarations; SQLCipher + Keychain/Keystore-bound biometric unlock; data residency per region; DPDP-ready consent/erasure by May 2027; 72-h breach runbook; plan SOC 2 Type II ($25–80k, 6–14 mo) only when aggregator/bank partners require it. Sources in §4.

### R6. Differentiators the evidence supports
- On-device AI weekly recap & Q&A (Monarch/Cleo show demand; Apple FM makes it free/private) ([Monarch](https://www.monarch.com/blog/winter-release), [Apple](https://developer.apple.com/documentation/FoundationModels)).
- India subscription radar: detect UPI AutoPay mandates/recurring debits and deep-link to UPI Help / UPI app for cancel ([NPCI OC-223](https://teamleaseregtech.com/updates/article/47798/npci-issued-a-notification-regarding-the-enhancement-of-upi-autopay/)).
- Short-horizon balance/cash-flow forecast (users still ask for it beyond Monarch's long-range forecasting) ([Reddit](https://www.reddit.com/r/MonarchMoney/comments/1qe3dlg/feature_request_account_balance_projections/)).
- Optional MCP endpoint (local or E2EE-safe) for "bring your own AI" ([Plaid MCP](https://plaid.com/docs/resources/mcp)).
- No-link mode as first-class (ProjectionLab praise) ([ProjectionLab](https://projectionlab.com/pricing)).

---

## Sources
- https://plaid.com/pricing/
- https://plaid.com/docs/account/billing
- https://plaid.com/docs/enrich/
- https://plaid.com/docs/resources/mcp
- https://community.emma-app.com/t/plaid-is-expensive-how-do-you-survive/2051
- https://www.vendr.com/buyer-guides/mx-technologies
- https://help.monarch.com/hc/en-us/articles/33707613533972-Understanding-Data-Providers-and-Connections
- https://www.monarch.com/connection-status
- https://status.monarch.com/cm7wduh1f001dhk2t9n5js5md
- https://github.com/api-evangelist/akoya
- https://fintable.io/coverage/providers/AKOYA
- https://www.getmonetizely.com/articles/plaid-vs-yodlee-how-much-will-financial-data-apis-cost-your-fintech
- https://www.envestnet.com/newsroom/press-releases/envestnet-inc-completes-sale-yodlee-inc-stg
- https://bridge.simplefin.org/
- https://www.simplefin.org/index.html
- https://racedorsey.com/posts/2025/you-need-an-actual-budget
- https://actualbudget.org/docs/advanced/bank-sync/gocardless
- https://bankaccountdata.gocardless.com/new-signups-disabled
- https://github.com/autoditac/Penge/issues/120
- https://enablebanking.com/
- https://enablebanking.com/accounts-api
- https://actualbudget.org/docs/advanced/bank-sync
- https://actualbudget.org/docs/getting-started/sync/
- https://dev.to/johnfrandsen/gocardless-bank-account-data-alternatives-what-to-use-when-signups-are-disabled-326d
- https://truelayer.com/data/
- https://www.saltedge.com/products/account_information
- https://docs.saltedge.com/account_information/v5
- https://www.basiq.io/pricing
- https://support.setu.co/support/solutions/articles/81000410140-how-does-pricing-work-in-this-ecosystem-
- https://sahamati.org.in/media-article/account-aggregator-crosses-500-million-consents-as-india-marks-5th-aa-foundation-day
- https://sahamati.org.in/financial-information-user-fiu
- https://fold.money/blog/privacy-by-design
- https://avantiscdnprodstorage.blob.core.windows.net/legalupdatedocs/50396/Reserve-Bank-of-India-Non-Banking-Financial-Companies-Account-Aggregator-Directions-2025-Dec052025.pdf
- https://www.jpmorganchase.com/newsroom/press-releases/2025/jpmc-plaid-renewed-data-access-agreement
- https://www.cnbc.com/2025/11/14/jpmorgan-chase-fintech-fees.html
- https://www.reuters.com/sustainability/boards-policy-regulation/jpmorgan-secures-deals-with-fintech-aggregators-over-fees-access-data-cnbc-2025-11-14
- https://developer.apple.com/documentation/financekit
- https://www.macstories.net/linked/financekit-opens-real-time-apple-card-apple-cash-and-apple-savings-transaction-data-to-third-party-apps
- https://www.paymentsjournal.com/apple-is-piloting-open-banking-in-the-uk
- https://www.consumerfinancemonitor.com/2026/06/26/open-banking-regulation-in-2026-federal-regulation-resurfaces-as-states-bring-data-sharing-into-focus
- https://www.consumerfinancemonitor.com/2026/08/06/cfpb-sends-new-section-1033-open-banking-proposal-to-oira-for-review
- https://www.congress.gov/crs_external_products/IF/HTML/IF13117.html
- https://www.europarl.europa.eu/legislative-train/carriage/payment-services-and-electronic-money-services-%28directive%29/report
- https://finance.ec.europa.eu/document/download/e0b77115-3e16-4b64-bcd4-48837372ee3f_en?filename=fsug-260226-minutes_en.pdf
- https://www.jdsupra.com/legalnews/psd3-and-the-payment-services-4770204
- https://www.matheson.com/insights/the-third-payment-services-directive-and-payment-services-regulation
- https://data.consilium.europa.eu/doc/document/WK-5041-2026-INIT/en/pdf
- https://www.simmons-simmons.com/en/publications/cmb852yp8012evch02bl6i90g/fida-simplification-is-the-buzzword
- https://www.projectivegroup.com/fida-back-in-motion-from-ambitious-to-workable
- https://support.google.com/googleplay/android-developer/answer/10208820?hl=en-GB
- https://finvot.app/en/docs/sms
- https://pocketclear.app/blog/auto-log-apple-pay-bank-sms-iphone-shortcuts.html
- https://developer.apple.com/forums/thread/705659
- https://technofino.in/community/threads/expense-tracker-for-ios-mac-using-shortcuts-app.40920/
- https://apacnewsnetwork.com/2025/10/banks-seek-rbi-approval-to-stop-low-value-sms-alerts-for-customers/
- https://upstox.com/news/business-news/latest-updates/banks-seek-waiver-from-sms-alert-for-transactions-below-100-report/article-183039/
- https://www.authsignal.com/blog/articles/india-and-the-uae-are-phasing-out-sms-otp
- https://finart.app/automatic-expense-tracker-iphone/
- https://developers.google.com/workspace/gmail/api/auth/scopes
- https://support.google.com/cloud/answer/13465431?hl=en
- https://casa.tacsecurity.com/site/home
- https://www.reddit.com/r/androiddev/comments/1clk1ng/end_of_free_casa_tier_2_certification_for_google
- https://www.reddit.com/r/googlecloud/comments/1krsru9/finally_completed_google_casa_tier_2_assessment
- https://casaready.org/
- https://discuss.google.dev/t/gmail-restricted-scope-is-the-casa-assessment-required-when-mail-is-processed-on-device-and-only-a-derived-subscription-list-reaches-our-server/398372
- https://discuss.google.dev/t/is-casa-required-for-all-access-restricted-scopes/340650
- https://www.monarch.com/blog/july-product-update
- https://developer.apple.com/documentation/vision/recognizedocumentsrequest.md
- https://www.veryfi.com/technology/line-item-extraction-accuracy-benchmarks
- https://www.veryfi.com/ai-insights/invoice-ocr-competitors-veryfi
- https://www.mindee.com/blog/how-to-perform-a-meaningful-ocr-benchmark-a-receipt-ocr-case-study
- https://www.monarch.com/blog/winter-release
- https://developer.apple.com/videos/play/wwdc2025/277/
- https://www.macrumors.com/2025/06/18/apple-transcription-api-faster-than-whisper
- https://appleinsider.com/articles/25/06/18/apple-intelligence-transcription-is-twice-as-fast-as-openais-whisper
- https://developers.google.com/ml-kit/genai
- https://developers.google.com/ml-kit/genai/prompt/android
- https://getfinny.app/blog/action-button-expense-tracker-iphone-15-16-pro
- https://myfinance-mcp.com/
- https://arxiv.org/html/2508.05425v1
- https://arxiv.org/pdf/2509.25803
- https://arxiv.org/pdf/2606.08051
- https://getfinny.app/blog/monarch-money-review-2026
- https://www.budgetpeer.com/blog/is-copilot-money-worth-it-an-honest-look-at-the-premium-budget-app
- https://help.monarch.com/hc/en-us/articles/37526856682260-AI-in-Monarch
- https://openrouter.ai/google/gemini-2.5-flash-lite
- https://www.morphllm.com/gemini-api-pricing
- https://openrouter.ai/openai/gpt-5-nano
- https://developer.apple.com/documentation/FoundationModels
- https://www.apple.com/newsroom/2025/09/apples-foundation-models-framework-unlocks-new-intelligent-app-experiences/
- https://developer.apple.com/documentation/Updates/FoundationModels
- https://techcrunch.com/2025/10/03/how-developers-are-using-apples-local-ai-models-with-ios-26
- https://www.ntropy.com/txen
- https://www.ntropy.com/blog/build-ai-supplier-payment-agent
- https://www.businesswire.com/news/home/20260324472369/en/Spade-Announces-$40m-Series-B-Raise-to-Build-The-Data-and-AI-Platform-for-Modern-Finance
- https://sourceforge.net/software/product/Spade-API/
- https://www.saasworthy.com/product/heron-data-io/pricing
- https://archive.jlongster.com/using-crdts-in-the-wild
- https://github.com/jlongster/crdt-example-app
- https://deepwiki.com/actualbudget/actual
- https://powersync.com/pricing
- https://powersync.com/blog/simplified-cloud-pricing-based-on-data-synced
- https://electric.ax/blog/2025/03/17/electricsql-1.0-released
- https://electric.ax/blog/2026/08/11/electric-joining-databricks
- https://www.databricks.com/blog/electric-joins-databricks-bring-wasm-postgres-ai-agent-sandboxes
- https://www.thehackerwire.com/electricsql-v1-shape-critical-sql-injection-cve-2026-40906/
- https://www.infoq.com/news/2026/06/zero-version-1/
- https://replicache.dev/
- https://github.com/rocicorp/replicache
- https://supabase.com/blog/triplit-joins-supabase
- https://www.inkandswitch.com/newsletter/dispatch-011/
- https://news.ycombinator.com/item?id=44777086
- https://www.evolu.dev/
- https://www.npmjs.com/package/evolu
- https://siliconangle.com/2025/03/12/ditto-raises-82m-funding-edge-database/
- https://www.synergyboat.com/blog/flutter-vs-react-native-vs-native-performance-benchmark-2025
- https://www.creolestudios.com/react-native-0-82-new-architecture-update/
- https://shopify.engineering/flashlist-v2
- https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/
- https://blog.jetbrains.com/kotlin/2026/05/compose-multiplatform-1-11-0/
- https://www.ideamagix.com/blog/flutter-vs-react-native-for-mobile-apps/
- https://appiko.co/blog/react-native-vs-flutter-2025
- https://developer.apple.com/videos/play/wwdc2024/10157/
- https://blakecrosley.com/blog/ios-26-widget-and-control-surface
- https://luksza.org/2026/add-a-native-home-screen-widget-to-your-flutter-app/
- https://pub.dev/packages/live_activity_kit
- https://docs.expo.dev/versions/latest/sdk/widgets/
- https://expo.dev/blog/home-screen-widgets-and-live-activities-in-expo
- https://developer.android.com/develop/ui/views/quicksettings-tiles
- https://developer.android.com/jetpack/androidx/releases/glance
- https://developer.android.com/training/wearables/user-interfaces
- https://www.zetetic.net/sqlcipher/performance/
- https://www.zetetic.net/sqlcipher/community/
- https://www.zetetic.net/blog/2026/05/12/sqlcipher-4.16.0-release/
- https://www.zetetic.net/blog/2025/06/26/sqlcipher-for-android-16kb-page-size-support/
- https://developer.android.com/security/fraud-prevention/authentication
- https://ptkd.com/journal/android-biometricprompt-secure-usage
- https://docs.talsec.app/appsec-articles/articles/ios-keychain-vs.-android-keystore
- https://www.dlapiperdataprotection.com/?t=law&c=IN
- https://www.sansalegal.com/post/dpdp-act-2023-and-rules-2025-phased-implementation-timeline-and-business-compliance-deadlines
- https://dpdpa.dcomply.in/rules/
- https://consentos.in/learn/breach-notification-requirements/
- https://www.loyensloeff.com/insights/news--events/news/the-eu-accessibility-act-is-now-live-what-is-the-impact-on-the-financial-sector/
- https://developer.apple.com/app-store/review/guidelines/
- https://support.google.com/googleplay/android-developer/answer/16550159?hl=en
- https://support.google.com/googleplay/android-developer/answer/13849271?hl=en
- https://drata.com/learn/soc-2/cost
- https://www.complyjet.com/blog/soc-2-for-startups
- https://iomergent.com/soc-2-for-fintech/
- https://web.meetcleo.com/blog/Introducing-cleo-3-0
- https://www.businesswire.com/news/home/20250729690058/en/Cleo-Becomes-the-First-AI-Money-Coach-That-Speaks-Thinks-and-Remembers
- https://www.planandmultiply.com/en/blog/rocket-money-budget-app-review-alternatives-2026
- https://github.com/felixgalindo/monarch-money-mcp
- https://help.monarch.com/hc/en-us/articles/48344305092244-Forecasting-in-Monarch
- https://www.reddit.com/r/MonarchMoney/comments/1qe3dlg/feature_request_account_balance_projections/
- https://projectionlab.com/pricing
- https://www.wallstreetsurvivor.com/rocket-money-review/
- https://www.thepennyhoarder.com/budgeting/rocket-money-review/
- https://www.crowell.com/en/insights/client-alerts/eighth-circuit-cancels-click-to-cancel
- https://www.jonesday.com/en/insights/2026/05/ftc-revives-clicktocancel-rule-new-risks-for-subscription-businesses
- https://www.nolo.com/legal-encyclopedia/what-is-the-ftc-click-to-cancel-rule.html
- https://teamleaseregtech.com/updates/article/47798/npci-issued-a-notification-regarding-the-enhancement-of-upi-autopay/
- https://www.whalesbook.com/news/English/tech/Tired-of-Surprise-Subscription-Bills-NPCIs-New-Portal-Makes-Managing-Autopay-Effortless/694c9931e10c98d078889f85
- https://www.sciencedirect.com/science/article/pii/S2214635026000420
- https://www.nasdaq.com/docs/2026/01/12/Gamification-of-Personal-Finance-A-Systematic-Literature-Review-%28Ahmad-et-al-2025%29.pdf
- https://onlinelibrary.wiley.com/doi/full/10.1002/cfp2.70016
- https://www.revenuecat.com/blog/growth/gamification-in-apps-complete-guide

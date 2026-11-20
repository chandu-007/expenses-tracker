# Google Play: what to enter in Play Console

Package name on Play: **`io.github.chandu007.expenses`** (`app.expenses` is already taken on Play by another developer; the directly installed APK keeps `app.expenses`, so existing testers keep their data, but the Play app and the APK are two different apps on a phone). Build the file to upload with `./gradlew :androidApp:bundlePlaySms` (output `androidApp/build/outputs/bundle/playSms/androidApp-playSms.aab`): the release build WITH the SMS permission and the text receiver, under the accepted SMS declaration (`docs/PLAY_SMS_DECLARATION.md`, `docs/RELEASE_CHECKLIST.md` step 5). Upload that file and no other. `./gradlew :androidApp:bundlePlay` (the same build without the SMS permission and the text receiver, checked in the built manifest) is only the fallback if the declaration is ever withdrawn.

## Store listing (draft, English India)
- **App name** (30 max): `Expenses: private tracker`
- **Short description** (80 max): `Track spending, budgets and bills. Private: your data never leaves your phone.`
- **Full description** (4000 max):
  Expenses is a fast, private expense tracker built for how people in India spend: UPI, cards, cash and bills.
  - Add an expense in seconds, with Indian number grouping and the amount read back in words.
  - Accounts shown as cards; credit-card bill and due-date reminders.
  - Budgets for the month or per category, with a clear "left to spend".
  - Income and expense categories kept separate; search and change many entries at once.
  - Reports by category, account, tag and merchant; a calendar of spending.
  - Lent and borrowed money with people; repeating bills.
  - Optional: read payment-app notifications (Google Pay, PhonePe, Paytm, BHIM) into a review list. You approve every item; nothing is added by itself.
  - Export to CSV or Excel at any time, free. Encrypted backups with your own passphrase.
  - App lock with your fingerprint, face or screen lock. Hide amounts with one tap.
  Private by design: no account, no ads, no tracking, and the app has no internet permission, so it cannot send your data anywhere.

  **Draft for the owner (2026-11-21, not yet in Play Console; see `docs/RELEASE_READINESS.md` section 3).** The description above predates Slices 103 to 120 and does not mention text capture, which the SMS declaration needs the listing to state (`docs/PLAY_SMS_DECLARATION.md:28-31`).
  - Proposed short description (72 of 80): `Free, private expense tracker that reads your bank alerts on your phone.`
  - Add to the full description, near the top: "Reads your bank's text alerts (with your permission) to find payments and bills, on your phone only. Nothing is sent anywhere; every found item waits in To review until you approve it. Optional: payment-app notifications (Google Pay, PhonePe, Paytm, BHIM) and bank alert emails (Gmail, Outlook, Yahoo Mail)."
  - Add one line each: "Add with the amount first; the keypad does sums (450+800)." "Travel mode: a trip tags its own spending." "Charts you can drag to read, or view as a table." "Budget pace: see whether you are on pace, and a daily figure to month end or payday." "Every category has its own icon and colour." "Two panes on foldables and tablets." "Verify it yourself: the app shows the permissions it does not have." "Free: no ads, no subscriptions, no in-app purchases."
  - Replace "Accounts shown as cards" with "Accounts at a glance, with card dues ("owed · due in 6 d")".
  - Screenshots and the feature graphic: retake after the UI slices (every screen changed since 2026-11-14).
- **Category:** Finance. **Tags:** Expense tracker, Budgeting. **Contact email / website / privacy policy URL:** yours; policy text in `docs/PRIVACY_POLICY.md`, hostable page `docs/site/privacy.html`.
- **Graphics you must supply:** icon 512x512 PNG; feature graphic 1024x500; at least 2 phone screenshots (8 allowed). Screenshots can be taken on the emulator or the phone; I can generate them with made-up data if you ask.

## App content declarations
- **App access:** all functionality available without login. **Ads:** none. **Target audience:** 18 and over. **News app / government / health:** no.
- **Financial features:** the app only records what the person enters; it does not give loans, move money, trade, or give financial advice. Choose "My app doesn't provide any financial features" if offered, or the closest "personal finance management / tracker" answer.
- **Content rating questionnaire:** no violence, no sexual content, no user-generated content shared with others, no location, no purchases: expected rating Everyone / 3+. Answer truthfully when you see it.
- **Data safety form** (the app collects nothing):
  - Does the app collect or share user data? **No.**
  - Data encrypted in transit: not applicable (no transmission; no internet permission).
  - Can users request deletion: data is only on the device; they delete it in the app or by uninstalling.
  - Security practices: data encrypted at rest on the device.
- **Permissions declarations:**
  - **SMS / call log:** the ordinary build (`bundlePlay`) declares none. The build with text capture (`bundlePlaySms`) uses RECEIVE_SMS under Google's "SMS-based money management" exception: see `docs/PLAY_SMS_DECLARATION.md` for the declaration text, the listing wording, the demo video and the steps.
  - **Notification access (BIND_NOTIFICATION_LISTENER_SERVICE):** the app has a notification listener that is off until the person turns it on, and reads only four payment apps, and Gmail, Outlook and Yahoo Mail only after a second, separate opt-in switch (it keeps only an email that reads like a bank alert). Google may ask you to justify this. Use: "Core function: lets the user capture payments from payment-app notifications into a review list. Opt-in, explained on a screen before the system settings page opens, reads only Google Pay, PhonePe, Paytm and BHIM (and, behind a second opt-in switch, Gmail, Outlook and Yahoo Mail, keeping only emails that read like a bank transaction alert), ignores all others unread, nothing leaves the device." I have not been able to confirm that Google will accept this for an expense tracker; if it is rejected, the fallback is to remove the notification listener from the Play build too (a small manifest change like the SMS one).
  - **Exact alarms / full-screen / background location / photos:** none used.
- **Prominent disclosure:** the Capture screen explains what is read, before the switch and before Android's settings page; keep it unchanged.

## Release steps
The app is in production (0.10.6, code 39) and open testing is the testing track in use (0.13.0, code 42). For each release:
1. Update the **Store listing** if the release changes what it describes (above), and the hosted privacy policy BEFORE uploading (`docs/site/privacy.html`, published from `main` by GitHub Pages).
2. **Test and release > Testing > Open testing:** create a release, upload the `bundlePlaySms` `.aab` with the next unused `versionCode`, paste the release notes, send for review, then install it from Play on a phone and check it.
3. **Production:** promote the same release with a **staged rollout of about 20%**; watch Android vitals and reviews for 2 to 3 days, then raise to 50% and 100%; halt the rollout if crashes or ANRs rise.
4. Play App Signing: the keystore used by the bundle tasks is the upload key: keep `EXPENSES_STORE_FILE` safe and backed up. Registration of the package for Android developer verification is separate: see `docs/ANDROID_INSTALL.md`.

## Limits to know
- The ordinary Play build (`bundlePlay`) has no SMS reading (the SMS build, `bundlePlaySms`, does, once its declaration is approved); the "Capture from texts" tile reads "Capture from payment apps" there. Wording elsewhere that mentions texts (the To review help line, bills help, capture health) still says "texts" in the Play build.
- The app targets Android 16 (API 36); Play requires a recent target level each year: check the warning in Console before each upload.
- The `.aab` is signed with the same key as the APKs you already shared. If you instead let Play generate the app signing key, installs of the direct APK and the Play build will not update each other (different signatures).

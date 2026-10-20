# Google Play: what to enter in Play Console

Package name on Play: **`io.github.chandu007.expenses`** (`app.expenses` is already taken on Play by another developer; the directly installed APK keeps `app.expenses`, so existing testers keep their data, but the Play app and the APK are two different apps on a phone). Build the file to upload with `./gradlew :androidApp:bundlePlay` (output `androidApp/build/outputs/bundle/play/androidApp-play.aab`). It is the release build without the SMS permission and without the text receiver (checked in the built manifest); everything else is identical.

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
  - **SMS / call log:** none declared in this build, so no declaration form.
  - **Notification access (BIND_NOTIFICATION_LISTENER_SERVICE):** the app has a notification listener that is off until the person turns it on, and reads only four payment apps. Google may ask you to justify this. Use: "Core function: lets the user capture payments from payment-app notifications into a review list. Opt-in, explained on a screen before the system settings page opens, reads only Google Pay, PhonePe, Paytm and BHIM, ignores all others unread, nothing leaves the device." I have not been able to confirm that Google will accept this for an expense tracker; if it is rejected, the fallback is to remove the notification listener from the Play build too (a small manifest change like the SMS one).
  - **Exact alarms / full-screen / background location / photos:** none used.
- **Prominent disclosure:** the Capture screen explains what is read, before the switch and before Android's settings page; keep it unchanged.

## Release steps
1. Complete **Set up your app** and **Store listing** above.
2. **Test and release > Testing > Internal testing:** create a release, upload the `.aab`, accept Play App Signing (the keystore used by `bundlePlay` becomes your upload key: keep `EXPENSES_STORE_FILE` safe and backed up), add testers by email, and share the opt-in link. No waiting period.
3. **Closed testing** (needed to unlock production for a new personal account): at least **12 testers opted in for 14 days in a row**, then **Apply for production** on the Dashboard. See Google's page: https://support.google.com/googleplay/android-developer/answer/14151465
4. Registration of the package for Android developer verification (free limited-distribution or full account) is separate: see `docs/ANDROID_INSTALL.md`.

## Limits to know
- The Play build has no SMS reading; the "Capture from texts" tile reads "Capture from payment apps" there. Wording elsewhere that mentions texts (the To review help line, bills help, capture health) still says "texts" in the Play build.
- The app targets Android 16 (API 36); Play requires a recent target level each year: check the warning in Console before each upload.
- The `.aab` is signed with the same key as the APKs you already shared. If you instead let Play generate the app signing key, installs of the direct APK and the Play build will not update each other (different signatures).

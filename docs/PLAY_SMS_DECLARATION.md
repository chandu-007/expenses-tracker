# Google Play: SMS permission declaration (SMS-based money management)

Policy: https://support.google.com/googleplay/android-developer/answer/10208820 lists "SMS-based money management (for example, apps that track and manage budget)" as an exception that may use `RECEIVE_SMS`. Approval is per app, temporary and subject to review; nothing here guarantees it. The wording below is a draft: read it against the live form before sending.

## Which build
`./gradlew :androidApp:bundlePlaySms` makes `Expenses-<version>-play-sms.aab`: the Play build WITH the `RECEIVE_SMS` permission and the text receiver (the ordinary `bundlePlay` build has neither). It requests **only `RECEIVE_SMS`** (not READ_SMS, SEND_SMS or MMS/WAP push), so declare only that one. Do not upload it to production before the declaration is approved; upload it to Closed testing first (below).

## Declaration form (Play Console > App content > Sensitive app permissions > SMS and Call Log)
**Permission:** RECEIVE_SMS.

**Core functionality (the form asks for the feature that needs it):**
"Expenses is a personal expense and budget tracker. Its core feature is SMS-based money management: when a text arrives from a bank, card issuer or biller, the app reads it on the phone to find the transaction (amount, date, account) or the bill (amount and due date) and puts it in a review list. Nothing is added to the user's records until the user approves it. Without the text permission the user must type every transaction by hand."

**Which exception applies:** SMS-based money management.

**Why a less sensitive permission is not enough:** "Bank alerts arrive as ordinary texts. The SMS Retriever and SMS User Consent APIs only deliver one-time passwords for the app's own verification, not a bank's transaction alerts. Notification access is offered as an alternative for payment apps, but it does not cover bank texts."

**Data handling (state exactly this, and keep the app true to it):**
- Texts are read on the device only. The app has no INTERNET permission and cannot send anything off the device.
- The sender is checked against a built-in list of banks, cards and billers before the words are read. Texts from people, one-time passwords and unknown senders are discarded at once: only a count is kept (no words, no number).
- The words of a bank text are kept until the user adds or skips it; then only the amount, date and account remain (and, for an entry added from it, the original wording, which the user can see under "Where this came from").
- No text is used for advertising, sold, or shared.

**Prominent disclosure:** a screen shown before Android's permission question, with an explicit "Agree and continue" / "No thanks" (More > Capture from texts > turn on "Read bank texts"). Text in the app:
"Allow Expenses to read your bank texts? This app uses the permission to receive text messages (RECEIVE_SMS) for one purpose: to find the payments, credits and bills in texts from your bank, card or biller, and put them in a list for you to approve, so you can track your money. Each text is read on this phone, and the sender is checked first. Texts from people, one-time passwords and senders that are not a known bank or biller are ignored and thrown away: only a count is kept. Nothing is sent anywhere: the app has no internet access at all. You can turn this off in this screen at any time."

## Store listing (Google wants the feature stated in the description)
Add to the full description:
"Reads your bank's text alerts (with your permission) to find payments and bills, on your phone only. Nothing is sent anywhere. Every found item waits in a list until you approve it. Optional: also reads payment-app notifications and bank alert emails."
Short description may say: "Private expense tracker that reads your bank alerts on your phone."

## Privacy policy
`docs/PRIVACY_POLICY.md` and `docs/site/privacy.html` cover reading bank texts on Android, including the Play version after approval. Re-host the page and make sure the Play listing's URL shows the new text.

## Demo video (Google may ask)
Screen-record, about 1 minute, on a phone with the SMS build: (1) More > Capture from texts; (2) turn on "Read bank texts" and show the disclosure; tap Agree; (3) show Android's permission dialog and Allow; (4) send a test bank text to the phone (from another phone, or `adb emu sms send 'AX-HDFCBK-S' 'Rs.250.00 debited from a/c XX1234 on 18-10-26 to TEST SHOP. Ref 123456789012'` on an emulator); (5) show it in To review; add it; (6) show a text from a person is not captured (capture health counts it, no words); (7) More > Privacy shows the app has no internet access.

## Steps for you
1. Update the store listing description and privacy policy URL content (above).
2. Play Console > App content > Sensitive app permissions: submit the SMS declaration with the text above, and the demo video if the form asks.
3. Upload `Expenses-<version>-play-sms.aab` to **Closed testing** (a new release). Google reviews the declaration with it. Keep the normal Play build live until you hear back.
4. If approved, release the SMS build to production when the 12-tester/14-day closed test allows it. If rejected, read the reason, fix what it names, and resubmit; the ordinary build stays as the fallback.

# Installing the Android app outside Google Play (for testers)

## What a tester sees, and why
1. **"Install unknown apps"**: the first time an APK is opened from a browser, Drive or a file manager, Android asks to allow that app to install apps. One switch, once per source app.
2. **Play Protect "App not scanned" / "Blocked"**: Google Play Protect has never seen this app. Tap *More details > Install anyway* (or *Send for scanning* and then install). Nothing about this app is flagged as harmful; the prompt appears because the package is new and unsigned by Play. Turning Play Protect off is not needed and not recommended.
3. **"Restricted setting"** when granting **SMS** or **Notification access** (Android 13 and later): these two permissions are *hard restricted* for apps that did not come from an app store. The permission screen shows the switch greyed out with "For your security, this setting is currently unavailable". To lift it, once: *Settings > Apps > Expenses > the three dots (top right) > Allow restricted settings*, confirm with the phone's PIN or fingerprint, then go back and grant the permission. (On a few phones the three dots only appear after the first attempt to grant the permission.)

## Can any of this be skipped?
Honestly: not for a file installed directly. Android's own documentation says a hard-restricted permission "cannot be held by an app until the device's installer allowlists the permission for your app" (https://developer.android.com/about/versions/13/behavior-changes-13): Google Play and the phone makers' stores are such installers; a browser or file manager is not. There is no manifest flag, signing trick or setting the app can use to avoid it, and apps that claim to are exploiting bugs that Google keeps closing (Android 14 and 15 tightened this).
Things that make it easier (not skip it):
- **The app explains the step itself**: the Capture screen opens App info for the tester (use that, then *Allow restricted settings*).
- **An installer that uses Android's session installer** instead of opening the file directly (for example Obtainium or F-Droid-style clients) is reported to avoid the restricted-settings step on some Android versions. I have not tested this on any device, so do not promise it to testers.
- **Google Play** (internal or closed testing, no public listing) is the only route where permissions are granted without these steps. See below for what Play requires.

## Play Store without D-U-N-S
A D-U-N-S number is required only for an *organization* developer account. A **personal** account ($25 once) does not need one. Personal accounts created after 13 November 2023 must run a closed test with at least 12 testers for 14 days before they can publish to production; internal testing (up to 100 testers) has no such rule. Check the current rules at https://support.google.com/googleplay/android-developer before relying on this.
**The SMS permission is the real obstacle on Play**: Play allows SMS permissions only for a default SMS app and a short list of exceptions, and expense tracking is not on it; an app asking for RECEIVE_SMS is normally rejected. Capturing from **notifications** (Notification access) is not under that policy. If Play is the goal, plan for notification capture there and keep SMS capture for the directly installed build.

## Android developer verification (new in 2026)
From 30 September 2026 apps installed on certified devices in Brazil, Indonesia, Singapore and Thailand must come from a registered developer; the rest of the world follows in 2027 (https://developer.android.com/developer-verification). Registration is free for a **limited distribution account** (up to 20 devices, no government ID, Google account with 2-step verification and a payments profile) or $25 for a full account (government ID; D-U-N-S only for organizations). Install through `adb` is never affected. Register the package `app.expenses` and its signing key before a wide rollout.

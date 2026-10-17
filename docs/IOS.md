# iPhone app (first test build)

What it is: a SwiftUI app (`iosApp/`) over the same shared Kotlin storage and rules the Android app uses (`core/domain`, `core/storage`, through the small Swift-friendly facade in `core/facade`). Built with Xcode 27.1 / Kotlin/Native.

## What the first build does
Add an expense or an income (separate category lists for each), the list grouped by day with swipe to delete, the month's spending, Cash and Bank balances, a Categories tab (expense / income), and the similar-entries offer after filing an entry (Move all, Review one by one, Not now). Amounts use Indian grouping, as on Android.

## What it does not do yet
Everything else in the Android app: no SMS or notification capture (iOS does not allow it; the planned route is a Shortcuts bridge), no bills, budgets, reports, search, accounts management, categories editing, import or backup, no widget, no app lock, no tour, no motion work. The database is not encrypted with a separate key (iOS Data Protection encrypts the file while the phone is locked). It has not been run on a real iPhone by the developer session.

## Build
`tools/ios_build.sh test` runs 4 UI tests on the iPhone 17 simulator; `tools/ios_build.sh ipa` makes a signed build and `~/Desktop/Expenses-iOS-0.1.0-test.ipa`. Requires `brew install xcodegen`. The framework is built by `./gradlew :core:facade:assembleExpensesCoreXCFramework -PenableIos=true`.

## Installing on the iPhone (free Apple ID signing)
1. The build is signed with the Apple Development certificate of team 9V2G5RH624 and a provisioning profile that lists one device (UDID 00008150-000855380C99401C). It expires 7 days after it was made (a free account); after that rebuild and reinstall. A paid Apple Developer account makes it a year and allows TestFlight.
2. On the iPhone: Settings > Privacy & Security > Developer Mode > On (it restarts).
3. Install: connect the iPhone by cable, open Xcode > Window > Devices and Simulators, select the phone, press + under Installed Apps and choose the .ipa. (Or `xcrun devicectl device install app --device <name> Expenses.app`.)
4. First launch: Settings > General > VPN & Device Management > the developer app > Trust.

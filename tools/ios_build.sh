#!/bin/bash
# Builds the iPhone app: the shared Kotlin framework, the Xcode project, then either the simulator tests or a signed device build and an .ipa.
#   tools/ios_build.sh test     run the UI tests on the iPhone 17 simulator
#   tools/ios_build.sh ipa      signed Release build for the registered iPhone, packaged as ~/Desktop/Expenses-iOS-<version>-test.ipa
# Needs Xcode, `brew install xcodegen`, and the Apple Development certificate in the login keychain. TEAM is the Apple team id (OU of that certificate).
set -euo pipefail
cd "$(dirname "$0")/.."
TEAM="${TEAM:-9V2G5RH624}"
./gradlew :core:facade:assembleExpensesCoreXCFramework -PenableIos=true -q
(cd iosApp && DEVELOPMENT_TEAM="$TEAM" xcodegen generate -q)
case "${1:-test}" in
  test)
    xcodebuild test -project iosApp/Expenses.xcodeproj -scheme Expenses -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath /tmp/iosbuild CODE_SIGNING_ALLOWED=NO ;;
  ipa)
    xcodebuild -project iosApp/Expenses.xcodeproj -scheme Expenses -sdk iphoneos -configuration Release -derivedDataPath /tmp/iosdev -allowProvisioningUpdates build
    rm -rf /tmp/ipa_work && mkdir -p /tmp/ipa_work/Payload && cp -R /tmp/iosdev/Build/Products/Release-iphoneos/Expenses.app /tmp/ipa_work/Payload/
    (cd /tmp/ipa_work && zip -qry "$HOME/Desktop/Expenses-iOS-0.1.0-test.ipa" Payload) ;;
esac

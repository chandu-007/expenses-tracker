#!/bin/bash
# Builds the iPhone app: the shared Kotlin framework (Compose UI + view model, module :shared), the Xcode project, then one of:
#   tools/ios_build.sh test      unit tests (backup format) and UI tests on the iPhone 17 simulator; screenshots of failures stay in /tmp/ios-results.xcresult
#   tools/ios_build.sh interop   the backup file the iPhone test wrote (/tmp/ios-made.expbak) read back by the Java reference of the Android format
#   tools/ios_build.sh ipa       signed Release build for the registered iPhone, packaged as ~/Desktop/Expenses-iOS-<version>-test.ipa
#   tools/ios_build.sh build     only the framework and the project (device and simulator)
# Needs Xcode, `brew install xcodegen`, and the Apple Development certificate in the login keychain. TEAM is the Apple team id (OU of that certificate).
set -euo pipefail
cd "$(dirname "$0")/.."
TEAM="${TEAM:-9V2G5RH624}"
DEST='platform=iOS Simulator,name=iPhone 17,OS=26.5'
VERSION="$(sed -n 's/.*MARKETING_VERSION: "\(.*\)"/\1/p' iosApp/project.yml)"

framework() { ./gradlew :shared:assembleSharedXCFramework -PenableIos=true -q; }
project() { (cd iosApp && DEVELOPMENT_TEAM="$TEAM" xcodegen generate -q); }

mode="${1:-test}"
case "$mode" in
  test)
    framework; project
    rm -rf /tmp/ios-results.xcresult
    xcodebuild test -project iosApp/Expenses.xcodeproj -scheme Expenses -sdk iphonesimulator -destination "$DEST" -derivedDataPath /tmp/iosbuild \
      -resultBundlePath /tmp/ios-results.xcresult CODE_SIGNING_ALLOWED=NO ;;
  interop)
    java tools/backup_interop/Interop.java check "correct horse battery" /tmp/ios-made.expbak iosApp/Tests/Fixtures/plain.txt ;;
  build)
    framework; project
    xcodebuild build -project iosApp/Expenses.xcodeproj -scheme Expenses -sdk iphonesimulator -destination "$DEST" -derivedDataPath /tmp/iosbuild CODE_SIGNING_ALLOWED=NO
    xcodebuild build -project iosApp/Expenses.xcodeproj -scheme Expenses -sdk iphoneos -configuration Release -derivedDataPath /tmp/iosdev -allowProvisioningUpdates ;;
  ipa)
    framework; project
    xcodebuild -project iosApp/Expenses.xcodeproj -scheme Expenses -sdk iphoneos -configuration Release -derivedDataPath /tmp/iosdev -allowProvisioningUpdates build
    rm -rf /tmp/ipa_work && mkdir -p /tmp/ipa_work/Payload && cp -R /tmp/iosdev/Build/Products/Release-iphoneos/Expenses.app /tmp/ipa_work/Payload/
    (cd /tmp/ipa_work && zip -qry "$HOME/Desktop/Expenses-iOS-$VERSION-test.ipa" Payload) ;;
  *) echo "usage: $0 test|interop|ipa|build" >&2; exit 2 ;;
esac

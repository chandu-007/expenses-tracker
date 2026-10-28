#!/bin/bash
# Builds the iPhone app: the shared Kotlin framework (Compose UI + view model, module :shared), the Xcode project, then one of:
#   tools/ios_build.sh test      unit tests (backup format) and UI tests on the iPhone 17 simulator; screenshots of failures stay in /tmp/ios-results.xcresult
#   tools/ios_build.sh unit      framework, XcodeGen project, then ONLY the unit tests (ExpensesTests), signed ad hoc as the Keychain tests need; run it under /tmp/ios.lock
#   tools/ios_build.sh interop   the backup files the iPhone tests wrote (/tmp/ios-made.expbak, the two with photos and files /tmp/ios-made-v2*.expbak, the protected automatic copies /tmp/ios-auto-v3*.expbak) read back by the Java reference of the Android format
#   tools/ios_build.sh ipa       signed Release build for the registered iPhone, packaged as ~/Desktop/Expenses-iOS-<version>-test.ipa
#   tools/ios_build.sh build     only the framework and the project (device and simulator)
#   tools/ios_build.sh native    no app build: the core/domain and core/parsers unit tests as an OPTIMISED (release) Kotlin/Native binary on the simulator, to catch crashes only the release framework shows
# Needs Xcode, `brew install xcodegen`, and the Apple Development certificate in the login keychain. TEAM is the Apple team id (OU of that certificate).
set -euo pipefail
cd "$(dirname "$0")/.."
TEAM="${TEAM:-9V2G5RH624}"
DEST='platform=iOS Simulator,name=iPhone 17,OS=26.5'
VERSION="$(sed -n 's/.*MARKETING_VERSION: "\(.*\)"/\1/p' iosApp/project.yml)"

# The Kotlin/Native link runs inside the Gradle daemon, so that daemon needs the memory (gradle.properties keeps it small for Android builds).
framework() { ./gradlew -Dorg.gradle.jvmargs="-Xmx12g -Dfile.encoding=UTF-8" :shared:assembleSharedXCFramework -PenableIos=true -q; }
project() { (cd iosApp && DEVELOPMENT_TEAM="$TEAM" xcodegen generate -q); }

# The simulator build is signed ad hoc (no team, no certificate), not left unsigned: an unsigned simulator app is refused by the Keychain, which the tests of protected
# automatic copies use, and carries no entitlements (App Groups).
SIM_SIGN=(CODE_SIGN_IDENTITY=- CODE_SIGN_STYLE=Manual DEVELOPMENT_TEAM= CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=YES)

mode="${1:-test}"
case "$mode" in
  test)
    framework; project
    rm -rf /tmp/ios-results.xcresult
    xcodebuild test -project iosApp/Expenses.xcodeproj -scheme Expenses -sdk iphonesimulator -destination "$DEST" -derivedDataPath /tmp/iosbuild \
      -resultBundlePath /tmp/ios-results.xcresult "${SIM_SIGN[@]}" ;;
  unit)
    framework; project
    rm -rf /tmp/ios-unit.xcresult
    xcodebuild test -project iosApp/Expenses.xcodeproj -scheme Expenses -sdk iphonesimulator -destination "$DEST" -derivedDataPath /tmp/iosbuild \
      -resultBundlePath /tmp/ios-unit.xcresult "${SIM_SIGN[@]}" -only-testing:ExpensesTests ;;
  interop)
    java tools/backup_interop/Interop.java check "correct horse battery" /tmp/ios-made.expbak iosApp/Tests/Fixtures/plain.txt
    # backups with photos and files (version 2): the sealed and the plain file the iPhone test wrote, opened by the reference, every file's checksum and the sample bytes checked
    java tools/backup_interop/Interop.java verify2 "correct horse battery" /tmp/ios-made-v2.expbak pattern
    java tools/backup_interop/Interop.java verify2 - /tmp/ios-made-v2-plain.expbak pattern
    # an automatic copy that carries a file (written into the folder in pieces)
    java tools/backup_interop/Interop.java verify2 - /tmp/ios-auto-v2.expbak
    # protected automatic copies (EXPENC3, written from the Keychain key): without and with a file, opened by the reference with the passphrase alone
    java tools/backup_interop/Interop.java verify2 "correct horse battery" /tmp/ios-auto-v3.expbak
    java tools/backup_interop/Interop.java verify2 "correct horse battery" /tmp/ios-auto-v3-files.expbak ;;
  build)
    framework; project
    xcodebuild build -project iosApp/Expenses.xcodeproj -scheme Expenses -sdk iphonesimulator -destination "$DEST" -derivedDataPath /tmp/iosbuild CODE_SIGNING_ALLOWED=NO
    xcodebuild build -project iosApp/Expenses.xcodeproj -scheme Expenses -sdk iphoneos -configuration Release -derivedDataPath /tmp/iosdev -allowProvisioningUpdates ;;
  ipa)
    framework; project
    # With no cached profile Apple only issues a new one for a registered device, so the build is aimed at the paired phone (IOS_UDID, or the first physical iPhone devicectl lists);
    # without one it falls back to the generic device, which works only while a profile is cached.
    UDID="${IOS_UDID:-$(xcrun devicectl list devices 2>/dev/null | awk '/physical/ && /iPhone/ { for (i = 1; i <= NF; i++) if ($i ~ /^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{16}$/) { print $i; exit } }')}"
    if [ -n "$UDID" ]; then target=(-destination "id=$UDID"); else target=(-sdk iphoneos); fi
    xcodebuild -project iosApp/Expenses.xcodeproj -scheme Expenses "${target[@]}" -configuration Release -derivedDataPath /tmp/iosdev -allowProvisioningUpdates build
    rm -rf /tmp/ipa_work && mkdir -p /tmp/ipa_work/Payload && cp -R /tmp/iosdev/Build/Products/Release-iphoneos/Expenses.app /tmp/ipa_work/Payload/
    (cd /tmp/ipa_work && zip -qry "$HOME/Desktop/Expenses-iOS-$VERSION-test.ipa" Payload) ;;
  native)
    ./gradlew -Dorg.gradle.jvmargs="-Xmx12g -Dfile.encoding=UTF-8" :core:domain:iosSimulatorArm64ReleaseTest :core:parsers:iosSimulatorArm64ReleaseTest -PenableIos=true --console=plain 2>&1 | grep -v '^w: '
    echo "native release tests: passed" ;;
  *) echo "usage: $0 test|unit|interop|ipa|build|native" >&2; exit 2 ;;
esac

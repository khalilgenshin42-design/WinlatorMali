#!/usr/bin/env bash
set -Eeuo pipefail

cd "$(dirname "$0")/../.."
chmod +x ./gradlew

RUNTIME_ARGS=(
  -PepicRuntimeAbi=x86_64
  -PepicRuntimePrefix="$GITHUB_WORKSPACE/cpython/cross-build/x86_64-linux-android/prefix"
)

# Verify the actual APK archive before booting/running instrumentation. This
# catches Gradle source-set omissions separately from runtime extraction bugs.
./gradlew --no-daemon --stacktrace :epicbridge:assembleDebug "${RUNTIME_ARGS[@]}"
BRIDGE_APK="epicbridge/build/outputs/apk/debug/epicbridge-debug.apk"
test -s "$BRIDGE_APK"
APK_ENTRIES="$(unzip -Z1 "$BRIDGE_APK")"
require_apk_entry() {
  local expected="$1"
  if grep -Fqx -- "$expected" <<<"$APK_ENTRIES"; then
    echo "APK archive entry verified: $expected"
    return 0
  fi
  echo "ERROR: required APK archive entry is missing: $expected" >&2
  echo "Related archive entries:" >&2
  case "$expected" in
    assets/python/*) grep -E '^assets/python/' <<<"$APK_ENTRIES" | head -80 >&2 || true ;;
    lib/*) grep -E '^lib/' <<<"$APK_ENTRIES" | head -80 >&2 || true ;;
  esac
  return 1
}
require_apk_entry 'assets/python/lib/python3.15/zipfile/path_pkg/__init__.py'
require_apk_entry 'assets/python/lib/python3.15/os.py'
require_apk_entry 'lib/x86_64/libepic_python.so'

if ./gradlew --no-daemon --stacktrace :epicbridge:connectedDebugAndroidTest "${RUNTIME_ARGS[@]}"; then
  echo "Epic Bridge instrumentation passed."
else
  test_status=$?
  echo "===== Epic Bridge native/Python runtime diagnostics ====="
  # Include both Java and JNI tags. The previous filter omitted
  # EpicPythonRuntime, hiding extraction and System.loadLibrary failures.
  adb logcat -d -s EpicPythonRuntime:E EpicBridgePython:E python:E AndroidRuntime:E || true
  exit "$test_status"
fi

# Exercise the real cross-package Binder boundary under the production host
# package ID and a separate Android UID. Both debug APKs are signed by the
# same CI debug keystore; the host fixture requests the bridge signature
# permission and calls the exported provider through ContentResolver.
./gradlew --no-daemon --stacktrace \
  :epicbridge-host-test:assembleDebug \
  :epicbridge-host-test:assembleDebugAndroidTest

HOST_APK="epicbridge-host-test/build/outputs/apk/debug/epicbridge-host-test-debug.apk"
HOST_TEST_APK="epicbridge-host-test/build/outputs/apk/androidTest/debug/epicbridge-host-test-debug-androidTest.apk"
test -s "$HOST_APK"
test -s "$HOST_TEST_APK"

# connectedDebugAndroidTest may leave its target package absent after teardown.
# Explicitly install the companion APK before probing its provider authority.
adb install -r "$BRIDGE_APK"
adb install -r "$HOST_APK"
adb install -r "$HOST_TEST_APK"
adb shell pm path com.maliwinlator.epicbridge | grep -q '^package:'
adb shell pm path com.maliwinlator.app | grep -q '^package:'

# adb shell am instrument can return a successful shell exit status even when
# JUnit reports a failed test. Gate CI on the instrumentation/JUnit result too.
HOST_TEST_OUTPUT="$(adb shell am instrument -w -r \
  -e class com.maliwinlator.epicbridge.hosttest.EpicBridgeCrossPackageTest \
  com.maliwinlator.app.test/androidx.test.runner.AndroidJUnitRunner 2>&1)" || {
    printf '%s\\n' "$HOST_TEST_OUTPUT"
    exit 1
  }
printf '%s\\n' "$HOST_TEST_OUTPUT"
if grep -Fq 'FAILURES!!!' <<<"$HOST_TEST_OUTPUT" ||
   grep -Fq 'INSTRUMENTATION_STATUS_CODE: -2' <<<"$HOST_TEST_OUTPUT" ||
   ! grep -Fq 'OK (1 test)' <<<"$HOST_TEST_OUTPUT"; then
  echo "ERROR: cross-package Binder instrumentation did not report one passing test." >&2
  exit 1
fi
echo "Epic Bridge cross-package Binder integration passed (1/1)."

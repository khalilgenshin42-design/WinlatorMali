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

# Exercise the actual in-package provider/runtime merged into the host test APK.
# The test APK uses the production host application ID and embeds :epicruntime;
# no companion APK is installed, so this validates the single-package route.
./gradlew --no-daemon --stacktrace \
  :epicbridge-host-test:assembleDebug \
  :epicbridge-host-test:assembleDebugAndroidTest \
  -PepicRuntimeAbi=x86_64 \
  -PepicRuntimePrefix="$GITHUB_WORKSPACE/cpython/cross-build/x86_64-linux-android/prefix"

HOST_APK="epicbridge-host-test/build/outputs/apk/debug/epicbridge-host-test-debug.apk"
HOST_TEST_APK="epicbridge-host-test/build/outputs/apk/androidTest/debug/epicbridge-host-test-debug-androidTest.apk"
test -s "$HOST_APK"
test -s "$HOST_TEST_APK"

adb install -r "$HOST_APK"
adb install -r "$HOST_TEST_APK"
adb shell pm path com.maliwinlator.app | grep -q '^package:'

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
  echo "ERROR: embedded host runtime instrumentation did not report one passing test." >&2
  adb logcat -d -s EpicPythonRuntime:E EpicBridgePython:E python:E AndroidRuntime:E || true
  exit 1
fi
echo "Embedded Epic provider/JNI runtime integration passed (1/1)."

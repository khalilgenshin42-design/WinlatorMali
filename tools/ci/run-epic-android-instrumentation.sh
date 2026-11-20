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
unzip -Z1 "$BRIDGE_APK" | grep -Fx 'assets/python/lib/python3.15/zipfile/_path/__init__.py'
unzip -Z1 "$BRIDGE_APK" | grep -Fx 'assets/python/lib/python3.15/os.py'
unzip -Z1 "$BRIDGE_APK" | grep -Fx 'lib/x86_64/libepic_python.so'

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

#!/usr/bin/env bash
set -Eeuo pipefail

cd "$(dirname "$0")/../../maliwinlator"
chmod +x ./gradlew

if ./gradlew --no-daemon --stacktrace :epicbridge:connectedDebugAndroidTest \
  -PepicRuntimeAbi=x86_64 \
  -PepicRuntimePrefix="$GITHUB_WORKSPACE/cpython/cross-build/x86_64-linux-android/prefix"; then
  echo "Epic Bridge instrumentation passed."
else
  test_status=$?
  echo "===== Epic Bridge native/Python runtime diagnostics ====="
  adb logcat -d -s EpicBridgePython:E python:E AndroidRuntime:E || true
  exit "$test_status"
fi

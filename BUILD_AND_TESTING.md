# Build and testing — MaliWinlator based on Winlator Mali 1.2.1

## Baseline toolchain

- JDK 17
- Android SDK platform 34
- Android Build Tools 35.0.0
- Android NDK 29.0.14206865
- CMake 3.22.1
- ARM64 ABI

## Build

```bash
git submodule update --init --recursive
chmod +x gradlew
./gradlew :app:assembleStandardDebug --no-daemon --stacktrace
```

Expected APK directory: `app/build/outputs/apk/standard/debug/`.

The build's `preBuild` task downloads `imagefs.tzst` if missing, so CI needs access to the configured upstream release. Verify the downloaded asset and final APK before treating the build as reproducible.

## GitHub Actions

Workflow: `.github/workflows/maliwinlator-ci.yml`. It builds the standard debug flavor on pushes to development branches and on manual dispatch. It also checks that the commit's immediate parent is the exact upstream 1.2.1 release commit:

`dfcdd4aaeff6def126ad8173619790806a5830db`.

## Device smoke test

On Xiaomi 15T / Android 16:
1. Install the debug APK as a separate app (package ID `com.maliwinlator.app`); do not uninstall or overwrite the user's existing Winlator app.
2. Verify first-run root filesystem installation and storage behavior.
3. Create a fresh test container; verify Wine, Box64/FEXCore, graphics wrapper selection, and container restart.
4. Verify shortcuts, controller, audio, file manager, save handling, and logs.
5. Test OpenGL and Vulkan/DXVK titles separately.
6. Record version, container settings, wrapper versions, logs, temperature, and frame-time/FPS results.

## Release requirements

Record APK SHA-256, package ID, version code/name, signing certificate fingerprint, source commit, and toolchain. Keep debug and release artifacts separate. Do not claim device testing until installed and exercised on the target phone.

# Build and testing guide

## Baseline environment

The current source configuration specifies:
- JDK 17
- Android SDK platform 34
- Android Build Tools 35.0.0
- Android NDK 29.0.14206865
- CMake 3.22.1
- ARM64 ABI

## Baseline build

From a clean checkout of the development branch:

```bash
git submodule update --init --recursive
chmod +x gradlew
./gradlew :app:assembleStandardDebug --no-daemon --stacktrace
```

The expected standard debug APK is under:

```
app/build/outputs/apk/standard/debug/
```

This command is a planned baseline check, not a claim that the build has already succeeded. The app's `preBuild` dependency downloads `imagefs.tzst` if it is missing, so network access to the configured upstream release is required.

## GitHub Actions

The existing workflow is `.github/workflows/build-apk.yml` and is manually dispatched. Select:
- flavor: `standard`
- build type: `debug`

The workflow installs the Android toolchain, checks out submodules, builds the selected flavor, and uploads APKs as an artifact. Confirm the run and artifact before treating it as a completed build.

## Device smoke test

On Xiaomi 15T / Android 16:
1. Install the debug APK without uninstalling the user's current Winlator app unless a deliberate package migration is being tested. The standard flavor's package ID is `com.winlator.mali`.
2. Verify first-run root filesystem installation and storage permission behavior.
3. Create a fresh test container; verify Wine, Box64/FEXCore, graphics wrapper selection, and container restart.
4. Verify existing shortcut launch, controller input, audio, file manager, save handling, and logs.
5. Test an OpenGL title and a Vulkan/DXVK title separately.
6. Record app version, container settings, driver/wrapper versions, log bundle, temperature, and frame-time/FPS results.

## Release requirements

- Build from a clean commit with recorded toolchain versions.
- Verify all expected runtime assets and submodules are present.
- Record APK SHA-256, package ID, version code/name, and signing certificate fingerprint.
- Keep debug artifacts separate from signed release artifacts.
- Do not publish an APK as device-tested until it has been installed and exercised on the target device.

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


## Verified CI baseline artifact (2026-11-19)

- Run: [44584271920](https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44584271920).
- Commit: `d68e700c374c16c59a0812f7bec72a8bb5455a8a`.
- Build result: `BUILD SUCCESSFUL`; APK artifact upload succeeded.
- APK file: `app-standard-debug.apk`, 447,758,192 bytes.
- APK SHA-256: `e0de614ae5bf93d66d885a92f6de5dba649a7afcc88695e894855ee27fcab67a`.
- Artifact ZIP SHA-256: `388b073f6015d838a653115bb61815006c20457b88ebcfb99e8da6232cf6780e`.
- The APK includes the compressed root filesystem and runtime asset bundles, explaining its large size. It is an ARM64 debug APK.
- Device installation and launch are not yet tested.


## Isolated-storage rebuild (2026-11-19)

- Source commit: `d9da8b4446edfcdae9abf5a0efabe54096769c3c`.
- Changed fresh-install external default to `/storage/emulated/0/MaliWinlator`; shortcut export default follows it.
- CI run [44586902271](https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44586902271) completed successfully; the standard debug APK was built and uploaded.
- Artifact ZIP SHA-256: `44662ff6b8374d80535e3ae2963dfbf9bec8323ef3f7135e6ef4e7cf25235340`.
- Device smoke test is pending.

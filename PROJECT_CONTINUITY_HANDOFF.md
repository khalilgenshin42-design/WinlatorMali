# MaliWinlator — Project Continuity Handoff

Last updated: 2026-11-19
Active branch: `development/maliwinlator-1.2.1-bootstrap`
Repository: https://github.com/khalilgenshin42-design/WinlatorMali

## Purpose and scope

Build an independent Winlator Mali fork named **MaliWinlator**, based on **Winlator Mali 1.2.1**, preserving its Mali-specific runtime and existing Winlator features while adding Epic Games support.

This is separate from the user's PanVK-G720 driver research. Never replace Android's vendor/system Vulkan driver. Do not merge PanVK work into this repository.

## User/device validation

- Target device: Xiaomi 15T, Dimensity 8400 Ultra, Mali-G720 MC7, Android 16, 12 GB RAM, non-rooted.
- On 2026-11-19 the user explicitly reported that the latest MaliWinlator build was installed and **worked**.
- This confirms the user's installation/smoke test succeeded. Exact tested screens, container creation, game launch, and regression coverage were not specified; do not claim those additional tests.
- Ask for device logs only if a concrete runtime defect needs investigation. Continue non-device source/runtime work without requiring the user to repeat prior setup.

## Source baseline and branch

- Upstream: `GunaCharanTeja/WinlatorMali`.
- Required release tag: `bionic-mali-1.2.1`.
- Exact upstream release commit: `dfcdd4aaeff6def126ad8173619790806a5830db`.
- Fork: `khalilgenshin42-design/WinlatorMali`.
- Active branch: `development/maliwinlator-1.2.1-bootstrap`.
- Last known branch HEAD before this handoff update: `0f7cab56d115a7c3053f17b11bec4a0cd7b3e4b4`.
- Older `development/maliwinlator-bootstrap` based on 1.2 is incorrect; do not continue work there.
- Preserve upstream 1.2.1 history and fixes. Do not force-push or rewrite history.

## Current app identity / isolation

- App label: `MaliWinlator`.
- Standard flavor application ID: `com.maliwinlator.app`.
- Namespace remains upstream `com.winlator.cmod`.
- Fresh-install external default path: `/storage/emulated/0/MaliWinlator`.
- Shortcut export default follows that path.
- This app is intended to install alongside the original Winlator Mali app. Do not share or overwrite its data directory by default.

## Build evidence

### Initial baseline build
- CI run: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44584271920
- Commit: `d68e700c374c16c59a0812f7bec72a8bb5455a8a`.
- Gradle: `:app:assembleStandardDebug`, successful.
- APK SHA-256: `e0de614ae5bf93d66d885a92f6de5dba649a7afcc88695e894855ee27fcab67a`.
- This was superseded by the isolated-storage rebuild.

### Current tested build
- Source commit: `d9da8b4446edfcdae9abf5a0efabe54096769c3c`.
- CI run: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44586902271 — successful.
- Artifact name: `maliwinlator-1.2.1-standard-debug-d9da8b4446edfcdae9abf5a0efabe54096769c3c`.
- Artifact ZIP SHA-256: `44662ff6b8374d80535e3ae2963dfbf9bec8323ef3f7135e6ef4e7cf25235340`.
- Artifact: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44586902271/artifacts/14560983797
- ZIP contains `app-standard-debug.apk`; prior runtime tool limitations prevented a verified extracted-APK hash for this second artifact.
- User has now reported that this build works on the Xiaomi 15T.
- Docs-only commits followed the source build; the tested app source remains commit `d9da8b4446edfcdae9abf5a0efabe54096769c3c`.

## Epic Games product requirements

User selected both:
1. **Legendary CLI as the primary Epic integration** — authentication via its supported Epic flow, owned library, install/update/uninstall, launch through a selected Wine container, progress and cancellation.
2. **Official Epic Games Launcher as secondary fallback** — installed in a dedicated Wine container/prefix, independently tested; never mutate the user's default prefix.

Preserve existing containers, Wine/Proton, Box64/FEXCore, DXVK/VKD3D, graphics wrappers, controllers, saves, shortcuts, logs, and custom driver import. Epic games should create/update shortcuts only after install metadata and executable mapping are verified.

## Epic feasibility findings (verified before implementation)

- Current bundled rootfs inventory was inspected: no `python`, `python3`, or `pip` executable is present.
- Rootfs uses Android Bionic (`usr/lib/libc.so` resolves to `/system/lib64/libc.so`), not a conventional glibc distro.
- Upstream Legendary requires 64-bit Python 3.10+ and `requests`; its generic standalone Linux executable expects glibc >= 2.25. Do not bundle that Linux binary and assume compatibility.
- Existing source extension points:
  - `MainActivity`, `ContentsFragment`
  - `contents/ContentsManager`, `ContentProfile`, `ContentDownloads`, `ContentDownloadService`
  - `container/ContainerManager`, `Container`
  - `xenvironment/ImageFs`, `XEnvironment`, `GuestProgramLauncherComponent`
  - `core/ProcessHelper`
  - `ShortcutsFragment`, `Shortcut`
- `GuestProgramLauncherComponent` builds Wine/Box64 guest launch commands and environment; do not repurpose it as the Legendary host process runner.
- Termux's Android/Bionic CPython build recipes are useful technical references, but Termux packages are built for a fixed Termux prefix and are not assumed relocatable into this app.

## Architecture to implement

1. **Runtime feasibility first:** build or package a redistributable ARM64 Android/Bionic CPython >=3.10 runtime in an app-private component directory, independent of base imagefs and Wine prefixes. Verify relocatability and modules: `ssl`, `ctypes`, `zlib`, `sqlite3`, subprocess, DNS/HTTPS, CA trust, filesystem access, persistence.
2. Install a pinned Legendary version plus pinned Python dependencies in that isolated runtime. Verify `legendary --version`, safe auth initiation, and non-secret metadata commands before UI integration.
3. Use a dedicated Java process adapter: argument arrays (never shell-concatenated UI strings), explicit executable/cwd/environment, bounded stdout/stderr capture, exit status, cancellation, timeouts, and redaction of secrets.
4. Add components (names provisional): `EpicAccountManager`, `LegendaryClient`, `EpicLibraryRepository`, `EpicInstallService` (foreground service, notifications, progress/cancel/retry), `EpicGameLauncher`.
5. Keep catalog IDs separate from shortcut IDs; persist state/manifests atomically; preflight disk space; support resumable/retryable installs; validate expected files before success.
6. Store secrets only in app-private storage, preferably Android Keystore-backed encryption if compatible with the process boundary. Never log tokens, credentials, or authorization headers.
7. Keep Legendary separate and respect GPL-3.0 distribution/source-notice obligations; audit all bundled dependency licenses.
8. Add official launcher fallback in its own dedicated Wine prefix only after the primary path is viable. Test login, library, downloads, updates, launch, overlay, and persistence independently.

## Acceptance criteria

Legendary is not considered complete until the device test proves:
- account authentication and sign-out;
- owned-game listing;
- install with visible progress, cancellation, safe retry/resume;
- update and uninstall without deleting unrelated data;
- launch through selected container;
- correct shortcut creation and persistence after app restart.

The official launcher fallback has its own compatibility matrix and must not be claimed working merely because its installer runs.

## Engineering workflow / reporting rules

- Work only on `development/maliwinlator-1.2.1-bootstrap` until a milestone is tested.
- Inspect the current branch and relevant source before modifying.
- Make small, reviewable commits. Never run concurrent writes to the same GitHub file/branch.
- Update this file after every meaningful step: source changes, architecture decisions, build/CI results, test outcomes, blockers, and next actions.
- Update roadmap/design/build docs when relevant.
- Never commit API keys, Epic tokens, passwords, signing keys, keystores, or personal data.
- Never claim build, package, feature, or device test success without direct evidence.
- Keep debug builds separate from release builds and record source commit, artifact link, and hashes.
- User prefers autonomous progress and only being interrupted when device validation or an unavoidable decision is required.

## Current status and next actions

- [x] Correct 1.2.1 source baseline.
- [x] Separate app identity and default external data directory.
- [x] CI build and artifact upload.
- [x] User-confirmed successful installation/launch smoke test on target phone (2026-11-19; scope limited to "worked").
- [x] Epic integration architecture and runtime incompatibility audit.
- [ ] Build and validate relocatable Bionic CPython runtime.
- [ ] Package/test Legendary and prove authentication/library metadata.
- [ ] Implement Epic account/library UI and foreground install lifecycle.
- [ ] Integrate game launch/shortcuts with selected containers.
- [ ] Implement and independently validate official Epic Launcher fallback.
- [ ] Full regression and release signing/distribution audit.

Immediate next engineering task: inspect Android NDK/toolchain and app packaging constraints, then prototype a pinned CPython 3.10+ Bionic build with a MaliWinlator-specific relocatable prefix. Do not start Epic UI work before runtime proof.


## Engineering step log — 2026-11-19: Android CPython integration route

- Reviewed current Android build configuration: AGP 8.8.0, Java 17, compile SDK 34, NDK 29.0.14206865, CMake 3.22.1, ARM64 only. Existing native build uses `app/src/main/cpp/CMakeLists.txt` and a `winlator` shared library.
- Reviewed `ProcessHelper.java`: it logs command strings and complete environment arrays. It is unsuitable for Epic authentication because tokens/authorization values could leak into logs. Do not reuse it for Epic commands.
- Consulted official CPython Android build/packaging guidance. Preferred route: build CPython for Android and embed it via a dedicated JNI library; package standard library and Android-compatible extension modules in app assets, extract under app-private storage, and initialize Python through JNI. This is more appropriate than assuming a glibc executable can run in the Bionic environment.
- Legendary current metadata specifies Python >=3.10 and dependencies `requests`, `filelock`, `pycryptodomex`; exact pinned versions and Android ARM64 extension compatibility still need to be proven.
- Updated `EPIC_INTEGRATION_DESIGN.md` in commit `8ff81d323b4d148b0829d6af170a67805b00396e` with the embedded-runtime decision, JNI separation, secure invocation model and POC acceptance gates.
- No application source code or runtime feature has been changed yet. No new APK has been built from this architecture decision.

### Next actions

1. Pin a supported CPython release (initial candidate 3.12.x or 3.13.x) and inspect the official `Platforms/Android` build scripts for required host tools, NDK/API assumptions and output layout.
2. Design a reproducible GitHub Actions job that builds the Android CPython runtime for `arm64-v8a`, verifies JNI/extension artifacts, and packages them as an isolated app component.
3. Implement a minimal JNI smoke-test target before wiring Legendary.
4. Update this handoff with every resulting commit/build/test/blocker.


## Engineering step log — 2026-11-19: first dependency candidates

- Confirmed CPython's Android build tooling supports the `aarch64-linux-android` host triplet and produces a shared `libpython` suitable for embedding. Official build instructions use a host build Python, then cross-build the Android target, package the prefix, and test on an emulator or connected device.
- Selected **CPython 3.14.10** as the initial POC candidate (stable maintenance branch with current Android packaging guidance); exact source checksum and Android build-script revision must be pinned before implementation.
- Verified **Legendary 0.21.1** is the latest published stable release during this investigation. Its metadata specifies Python >=3.10, GPL-3.0-or-later, and `requests`, `filelock`, `pycryptodomex` dependencies. Pin exact source commit and dependency hashes.
- Updated `EPIC_INTEGRATION_DESIGN.md` in commit `8be610993adf73012ab33411ee0377622f9401d9` with the version candidates and ARM64 host triplet.
- These are reproducible-build candidates only. No CPython/Legendary Android binary has been built, no dependency import has been validated on device, and Epic UI/features remain unimplemented.

### Next actions

- Inspect CPython 3.14.10's Android platform script and official dependency bundle at the exact release tag; do not use the moving main branch for the production build.
- Create an isolated GitHub Actions proof-of-concept workflow that builds CPython for `aarch64-linux-android`, packages the Android prefix, records hashes, and uploads it separately from the APK.
- Add a minimal app-side JNI initialization probe and build/test it before introducing Legendary or changing navigation.


## Engineering step log — 2026-11-19: CPython pin correction

- Inspected exact CPython source tags, not just current documentation. The exact `v3.15.2` tag contains `Platforms/Android/README.md` and `Platforms/Android/__main__.py`; its Android build tooling explicitly lists `aarch64-linux-android`.
- The exact `v3.14.10` tag did not contain `Platforms/Android`. Therefore the earlier 3.14.10 candidate was withdrawn rather than assuming current documentation applied to that older source tag.
- Corrected initial CPython POC pin to **3.15.2** in commit `00e9b7d768935dd98a58580914c1e29a9c7cd72c`. Legendary 0.21.1 declares Python >=3.10, but compatibility with 3.15 and the native dependency set is not proven.
- No build or runtime test has occurred yet. Next: inspect the exact v3.15.2 Android build script's dependency versions, build requirements and package layout; then construct the CI POC around that exact tag and verify extension-module feasibility.


## Engineering step log — 2026-11-19: CPython Android ARM64 CI POC added

- Added `.github/workflows/epic-python-runtime-poc.yml` in commit `8d4689cf102c42e87b6f4d93a4242baf79e474cb`, then enabled push-on-workflow-change execution in commit `97b118726c5e187146123c0da12b6883a709195e`.
- Workflow checks out exact CPython tag `v3.15.2`, sets up Linux host build tools, Java 17 and Android SDK, then uses CPython's own `Platforms/Android` scripts to build and package `aarch64-linux-android`. It audits for `libpython` and Python 3.15 payloads, writes SHA-256/build metadata, and uploads a separate 14-day artifact. It does not alter the app APK or include Epic credentials.
- GitHub Actions run **44590472189** started from commit `97b118726c5e187146123c0da12b6883a709195e`: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44590472189
- At the time of this update, the job was in progress; only job setup had completed. No CPython build result or artifact is claimed yet.
- The workflow is a runtime build POC, not an app integration. The next gate after a successful build is to integrate the official JNI embedding pattern in a separate `epicpython` native target and smoke-test it on Android.

# MaliWinlator — Project Continuity Handoff

Last updated: 2026-11-20
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


## Engineering step log — 2026-11-19: diagnose and correct first CPython CI failure

- CPython POC run `44590472189` failed at the Android build command. The job successfully checked out CPython v3.15.2, installed host dependencies, verified the Android script and downloaded Android ARM64 dependency archives. Failure: `cross-build/build does not exist` when the workflow invoked `build aarch64-linux-android`.
- Retrieved and inspected the exact v3.15.2 `Platforms/Android/__main__.py` implementation. Its documented discrete pipeline requires creating the host build Python before configuring/building the target: `configure-build`, `make-build`, `configure-host HOST`, `make-host HOST`, then `package HOST`. The previous one-shot invocation did not create the build directory in this CI run.
- Corrected the workflow to execute those five stages explicitly in commit `3e1ea1fba0f2d2cc6ede3126813dbaa5687acb84`.
- Retry run **44590732396** was automatically queued from that commit: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44590732396. At this update it is queued; no result or artifact yet.
- Official CPython Android build docs confirm the build-Python/host-Python sequence and packaging command. Official Android usage docs confirm Python must be embedded via `libpython`/JNI, with runtime standard library and site-packages packaged into the app. See https://docs.python.org/3.15/using/android.html and https://github.com/python/cpython/blob/v3.15.2/Platforms/Android/README.md.

### Next actions

1. Monitor retry run `44590732396`; inspect job logs at each failing step rather than assuming success.
2. If the runtime package builds, audit its archive structure and ELF dependencies, then create an app-side JNI initialization probe.
3. If the build fails, record the actual compiler/configuration error and patch the build pipeline before adding any Epic UI.


## Engineering step log — 2026-11-19: JNI embedding integration audit

- Inspected the CPython v3.15.2 Android testbed source: `Platforms/Android/testbed/app/src/main/c/main_activity.c`, Kotlin runner, and Gradle packaging logic. The upstream reference initializes Python with `PyConfig`, `PyConfig_SetBytesArgv`, `PyConfig_SetBytesString(home)`, `Py_InitializeFromConfig`, then runs the requested module via `Py_RunMain`. It extracts the Python standard library assets into app-private storage and loads the native library.
- Inspected MaliWinlator native build: `app/src/main/cpp/CMakeLists.txt` currently builds `winlator` and `fakeinput`; it can host a separate `epicpython` JNI shared library linked to CPython without coupling to the renderer. Current Gradle config is ARM64-only and NDK/CMake based.
- Security implementation must differ from the CPython testbed's stdio-to-logcat convenience code: Legendary authentication output and exceptions must be captured/redacted; never route raw credentials or auth URLs/tokens into logcat. Use a dedicated Java manager/worker and controlled in-process argv.
- No JNI/app source has been added yet; this was a source design audit only.
- CPython retry workflow `44590732396` was still running at this point; check its latest status before continuing.



## Engineering step log — 2026-11-19: CPython Android ARM64 POC build succeeded

- Retry workflow **44590732396** completed with conclusion **success**. All 11 main steps passed, including the explicit CPython configure/build/package sequence, archive audit and artifact upload.
- Exact source: CPython tag **v3.15.2**, commit **5e58eaad7c58df58dfa8ea549c87996e01dd1077**; target **aarch64-linux-android**, app ABI **arm64-v8a**, NDK **27.3.13750724**.
- Build mode: Android shared `libpython` plus packaged Python prefix. The audit confirmed a `libpython*.so` entry and Python 3.15 runtime payload in the package. This is a package-content audit, **not yet an ELF dependency audit, JNI load test, Python initialization test, or on-device runtime test**.
- Artifact: `maliwinlator-cpython-3.15.2-android-arm64-poc`, ID **14564177384**, size **24,189,270 bytes**. ZIP SHA-256: `92d4e8e92729cb9d5947b9c983b53f97492cb2a69c3c5d8201389335b8718ada`. Download: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44590732396/artifacts/14564177384. Artifact expires **2026-12-03** (14-day retention); preserve or regenerate before expiry if needed.
- Run URL: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44590732396. Workflow source commit: `3e1ea1fba0f2d2cc6ede3126813dbaa5687acb84`.
- This supersedes the earlier statement that the retry was still running. It does not supersede the previous app APK smoke-test result; no new APK was built in this workflow.
- The CPython 3.15.2 pin remains a POC candidate only. Legendary 0.21.1 compatibility with CPython 3.15, plus availability/buildability of `requests`, `filelock`, and `pycryptodomex` on Android/Bionic, is still unproven.

### Next engineering gates

1. Inspect the complete runtime archive contents and ELF dynamic dependencies; identify all required Android system libraries and any missing/unsupported modules. Exclude CPython's test suite and development-only files from the eventual production payload.
2. Add a separate `epicpython` JNI shared-library target to the app's existing CMake build. Vendor or stage the pinned CPython runtime in CI reproducibly; do not commit generated binaries blindly.
3. Implement a minimal app-private extraction + JNI load + `PyConfig` initialization probe. Keep stdout/stderr controlled and redact secrets; do not use the existing verbose `ProcessHelper` logging path for Epic authentication.
4. Build a debug APK and validate Python initialization on the user's Xiaomi 15T before integrating Legendary. Then test Legendary and its dependencies independently, without Epic UI.
5. Only after runtime and dependency gates pass, add the Legendary service/API and UI. Keep official Epic Games Launcher support as a separate fallback prefix and later milestone.


## Engineering step log — 2026-11-19: inspect CPython runtime archive and Android embedding reference

- Downloaded and unpacked artifact **14564177384** locally for static inspection. The outer artifact ZIP contains the CPython release tarball and `runtime-build-info.txt`; the inner archive expands to about **93 MiB**. SHA-256 of the inner runtime tarball: `29d1711ca1c6d093d1cd81f5655e895e484227f577fc52a53fdb69f43301dcc9`.
- Runtime package contains `prefix/lib/libpython3.15.so`, `libpython3.so`, 67 extension-module shared objects, and CPython's packaged test suite. The test suite alone occupies about **41 MiB**; headers occupy about **6 MiB**. This is a development package, not an app-ready payload. A production staging step should omit `lib/python3.15/test`, headers, profiling/IDLE/ensurepip/dev-only assets unless a concrete runtime dependency requires them.
- The prefix's native dependency set includes Android system libraries `libc.so`, `libdl.so`, `liblog.so`, `libm.so`, `libz.so`, plus bundled `libpython3.15.so`, `libcrypto_python.so`, `libssl_python.so`, and `libsqlite3_python.so`. `libsqlite3.so` and `libsqlite3_python.so` contain `DT_RUNPATH=/usr/local/lib`; this needs explicit handling/audit before runtime load. No glibc dependency was observed in the inspected objects. This is static ELF metadata only, not a device linker test.
- Upstream CPython testbed reference is included in the package. Its CMake target includes `prefix/include/python3.15`, links `python3.15`, and its Gradle asset generation copies `prefix/lib/python3.15` into app assets while packaging `libpython*.*.so` and `lib*_python.so` into the ABI JNI library directory. This is the authoritative starting pattern; we must adapt it to MaliWinlator's own app-private storage, lifecycle and security requirements.
- CPython upstream Android build uses `ANDROID_API_LEVEL=24`, below MaliWinlator's minSdk 26. The app already filters to `arm64-v8a`; ABI alignment is consistent.
- Existing MaliWinlator native CMake has separate `winlator` and `fakeinput` targets; app Gradle uses NDK 29 while the CPython runtime was built with NDK 27.3.13750724. The runtime's own Android platform libraries are not NDK-private, but the mixed-toolchain boundary must be checked in the actual link/load test.
- Important correction: the successful POC workflow's archive audit only checks presence of libpython/Python 3.15 files and computes a checksum. It does **not** validate ELF closure, RUNPATH behavior, extension import, JNI initialization or Android runtime behavior.
- No app source was modified during this audit. No new APK was built.

### Updated next actions

1. Create a deterministic runtime-pruning/staging script and CI validation that records kept/removed files, validates every retained ELF machine/NEEDED/RUNPATH, and preserves only modules required by Legendary plus standard runtime needs.
2. Resolve the `/usr/local/lib` RUNPATH entries in a reproducible way (prefer correct build-time prefix/RUNPATH or deliberate runtime loader strategy; do not patch binaries ad hoc without validation).
3. Add a separate `epicpython` JNI target and Java/Kotlin manager with app-private extraction, one-time initialization, safe error reporting and no credential logging.
4. Integrate runtime delivery into the CI artifact graph with pinned hashes and retention/release strategy; do not depend on an expiring Actions artifact as a permanent Gradle download.
5. Build APK and run JNI/Python import smoke tests on Android before Legendary or Epic UI.


## Engineering step log — 2026-11-19: add managed Android runtime test gate

- Extended `.github/workflows/epic-python-runtime-poc.yml` in commit `de3c0eb49816f43d517f1a73dba23400242038cf` to run CPython's official packaged-runtime test command `python3 Platforms/Android test --managed maxVersion` after the Android ARM64 build/package stage and before artifact publication.
- This invokes CPython's own Android testbed on a managed Android emulator (rather than merely checking archive filenames). It is intended to expose Bionic loader, extension-module, Python initialization and Android API problems before app-side JNI integration.
- New run **44591964699**: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44591964699. At handoff update time it is **in progress**. No emulator test result is claimed yet.
- If the managed emulator cannot start because of runner/KVM constraints, inspect logs and decide whether to use the connected-device route or another supported test runner; do not silently remove the runtime test gate.


## Engineering step log — 2026-11-19: Legendary compatibility boundary review

- Reviewed Legendary 0.21.1 project metadata and current README. Its declared Python floor is **3.10+** and its required packages are `requests`, `filelock`, and `pycryptodomex`; its documented supported desktop platforms are Linux, Windows and macOS. Android/Bionic is **not** an officially listed target. The project's optional webview login dependencies are not required for the first CLI proof.
- Therefore CPython 3.15 satisfies the declared language-version floor, but that alone does not establish Android support. The key unknowns remain Android/Bionic compatibility of the Python runtime and each dependency, especially the native `pycryptodomex` extension.
- Current managed-emulator test workflow **44591964699** is still in progress at the CPython build stage (job **135800046501**) as of this update. Emulator test has not started; no pass/fail is claimed.
- Do not conflate a successful CPython emulator test with Legendary compatibility. The next gate after runtime validation is a separate pinned dependency build/import matrix, followed by a minimal Legendary `--version` and `list-games` smoke test with no credentials. Authentication should be tested only after secret-safe logging and storage are in place.

## Engineering step log — 2026-11-20: CPython emulator-test gate diagnosis and correction

- Monitored GitHub Actions run **44591964699** to completion. The ARM64 cross-build completed successfully, but the managed-emulator test step failed and correctly prevented runtime audit/artifact upload.
- Retrieved the completed job log. The test invocation reported `No matching Apks found for maxVersion`, `Starting 0 tests`, and `Unable to find instrumentation target package: org.python.testbed`. Therefore **no CPython tests ran**; this is not evidence of a CPython runtime crash or a successful Android runtime test.
- Inspected the exact CPython v3.15.2 `Platforms/Android/__main__.py` and `testbed/app/build.gradle.kts`. Upstream's own `ci` subcommand packages the Android prefix, unpacks that release package into a temporary directory, and runs the managed-device testbed from the packaged layout with fast-CI arguments. The previous workflow manually called `test` against the source-tree build and did not reproduce this upstream CI path.
- Corrected `.github/workflows/epic-python-runtime-poc.yml` in commit `eeb5ce9afd884500493d71167392579858847f14` to use the upstream-supported single command:
  `python3 Platforms/Android ci aarch64-linux-android --fast-ci`.
  The workflow will audit and upload the runtime only after that command succeeds. This change is a CI/test-harness correction; it does not alter MaliWinlator app source or claim Android runtime validation.
- Next gate: inspect the new CI run. If it passes, inspect the artifact and perform ELF/dependency/relocation checks before adding any JNI or Legendary code. If it fails, use the new log to distinguish emulator provisioning, packaging, linker, and Python test failures.

- Corrected workflow execution **44640556637** was triggered by the workflow fix commit `eeb5ce9afd884500493d71167392579858847f14`: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44640556637
- At the latest inspection, the job was still in progress during checkout. No result or artifact is claimed yet.

## Engineering step log — 2026-11-20: CPython managed-emulator ABI mismatch

- Corrected run **44640556637** completed unsuccessfully. Its log shows the managed emulator selected x86_64 (`NATIVE_ABI` follows the x86_64 GitHub runner), while the testbed Gradle configuration discovered only the ARM64 prefix and applied `ndk.abiFilters = arm64-v8a`. Gradle therefore reported `No matching Apks found for maxVersion`, started zero tests, and failed to find the instrumentation target. No Python runtime test executed.
- Updated workflow in commit `e02a93e8a15a3aa73234f0de5d07e6ab8fca1944`:
  1. Run upstream CPython `ci x86_64-linux-android --fast-ci` so the emulator and test APK ABI match.
  2. Separately configure, build, and package `aarch64-linux-android` for the MaliWinlator ARM64 production target.
  3. Audit and upload only the ARM64 package.
- This separates emulator test ABI from shipping ABI. It is not yet proof of ARM64 device runtime compatibility, and no Legendary package or UI has been integrated yet.
- Next gates: obtain a non-zero passing CPython emulator test run; inspect ARM64 package ELF closure and relocatability; build and import-test Legendary dependencies for Android/Bionic; implement an isolated JNI-managed runtime with secret-safe process invocation; only then implement the Epic library/auth/install/update/launch UI and validate on the Xiaomi 15T.

- Added an Android native-dependency investigation note to `EPIC_INTEGRATION_DESIGN.md` in commit `7c45fb48a7f82dfbd7e29886f4b146430ec62df4`: Legendary 0.21.1 specifically needs the `Cryptodome` namespace; PyCryptodome 4.x no longer releases `pycryptodomex`, while the 3.x line remains the candidate. The maintained python-for-android PyCryptodome recipe plus its source `.separate_namespace` switch is a candidate to build Android-compatible extension modules. This is not yet built or validated.
- Current corrected workflow **44641730025** is still running at upstream CPython CI emulator build/test. No pass or artifact claim yet.

- Added the Winlator storage/launch boundary to `EPIC_INTEGRATION_DESIGN.md` in commit `4e25958e0a56de69027d0b5f3415526beba4fecd`: default container drive F: maps Android shared storage, so Epic payloads can live under the dedicated MaliWinlator shared folder and remain visible to Wine. Legendary's host process cannot directly launch Wine; game launch must be bridged into the existing proot/X guest launcher, preserving wrapper, audio, input, and container setup.

- Added the verified Legendary 0.21.1 manual authorization and JSON command contract to `EPIC_INTEGRATION_DESIGN.md` in commit `03f1f042fcaf4dd155fd6b0afec73cf7f1d3010e`: system-browser login at `https://legendary.gl/epiclogin`, submit the returned authorizationCode via `auth --code`, use `list --json` and `list-installed --json`, keep argv and output secret-safe, and store Legendary data only in app-private storage.

- Important licensing architecture correction: the host WinlatorMali app is MIT-licensed; Legendary 0.21.1 is GPL-3.0-or-later. To avoid importing GPL Python code into the MIT host process/APK, `EPIC_INTEGRATION_DESIGN.md` now selects a separate GPL Android companion package (`com.maliwinlator.epicbridge`) with its own CPython/Legendary runtime and permission-protected IPC to the host Epic UI. Both APKs must share the release signing certificate for signature-protected IPC. This is a design boundary, not legal advice; preserve GPL notices and corresponding-source offer.

- Reconciled the earlier CPython/JNI design text with the new GPL isolation boundary in `EPIC_INTEGRATION_DESIGN.md` commit `ef713ac70e9e6081c99050fceb8e32ab3f7604cc`: the `epicpython` JNI target belongs in the separate companion package, not the MIT host app.

- Added Legendary 0.21.1 mobile download policy to `EPIC_INTEGRATION_DESIGN.md` commit `9506edff03c09f56077a168c6f5e1a94bd27ccee`: parse the downloader's native `= Progress: N%` logs; begin with four workers and a 256 MiB shared-memory budget; use non-interactive install flags, preflight disk space, and preserve resume/manifest validation.

- Recorded the separate companion storage-permission boundary in `EPIC_INTEGRATION_DESIGN.md` commit `666cd684de5736a383a5ac71bc09a12eb1e80206`: the companion needs its own all-files access to install into the shared MaliWinlator EpicGames directory; the host app's grant is not inherited across packages.

- Added a concrete online launch bridge in `EPIC_INTEGRATION_DESIGN.md` commit `aea986d074e071883c4e29fc07318fa617136776`: use `legendary launch <app> --json --no-wine` to obtain the structured launch parameters and transient environment, then validate/map the game path to Wine F: and hand off to the host's existing `XEnvironment` / `GuestProgramLauncherComponent`. Never persist or log transient Epic/EOS tokens.

## Engineering step log — 2026-11-20: GPL companion APK bootstrap

- Added a separate Gradle application module `:epicbridge` with application ID `com.maliwinlator.epicbridge`. It has its own launcher activity, storage-access state, and signature-protected ContentProvider at `com.maliwinlator.epicbridge.provider`.
- The provider currently reports `runtime_not_installed` and returns explicit not-ready errors for operations. This is an IPC/bootstrap shell only; it does not yet contain CPython, Legendary, authentication, or game-management functionality.
- Host app manifest now requests `com.maliwinlator.epicbridge.permission.ACCESS`; the companion defines it as signature-level. Both APKs must be built/signed together with the same certificate.
- Updated `.github/workflows/maliwinlator-ci.yml` in commit `09aa77dc678ced0959ebeceb5812d013b7d9ed0d` to build `:app:assembleStandardDebug` and `:epicbridge:assembleDebug` and upload both APKs.
- Next: validate the two-APK build, add the host-side provider client and Epic UI, then package the tested CPython/Legendary runtime into the companion. Do not distribute the current companion scaffold as a working Epic Store feature.

## Engineering step log — 2026-11-20: Epic host UI and companion installer scaffold

- Added the Epic Games navigation destination and `EpicFragment` setup/status screen. It detects the companion package, calls its status provider, and offers a user-confirmed companion APK installation flow.
- Added `EpicBridgeClient`: checks companion installation, calls the signature-protected provider, copies the embedded companion APK to cache, and opens Android's package installer. If Android requires unknown-app install permission, it opens the corresponding settings page first.
- Added the `:epicbridge` application module and configured the host app to embed its debug APK as `assets/epicbridge.apk`; this remains a separate Android package/aggregate, not an in-process GPL library. The host and companion are built together so their debug signatures match.
- Updated the APK CI to build both packages. Current build run **44642849562** is in progress: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44642849562
- Important limitation: the companion is still only a bootstrap shell. Its provider reports `runtime_not_installed`; it has no CPython/Legendary code yet. The Epic screen is not a functioning store at this stage.

- Added a companion-replacement path for debug/release signing-certificate changes: the Epic screen detects signature mismatch and asks the user to uninstall the stale companion before installing the companion APK embedded in the current host build. Commits `ae47e8500e06a837779d1bf2045efb57e73a9fa4` and `c5831785aae607f7ea222df65cca0ffc8de2824a`.

- Static navigation audit caught that the Epic menu ID was in the controller section cycle but the selection switch case had not been inserted (the earlier replacement missed CRLF line endings). Added the missing `main_menu_epic` route in commit `08a9893fa1b1e35860c14de1df7d37418a68eb3b`; verified the source now contains the case. A new APK CI run is expected for this fix.

- APK build run **44643302613** completed successfully at source commit `08a9893fa1b1e35860c14de1df7d37418a68eb3b`; artifact ID `14586424092`, containing the MaliWinlator standard debug APK and Epic Bridge debug APK. Artifact download: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44643302613/artifacts/14586424092
- Added a stricter CI gate in commit `c94a3e26481929bf5773f3982825a6e0d0d9d52e` to verify that the host APK embeds the exact companion APK, that the embedded APK is valid, that its package ID is correct, and that both APKs have the same SHA-256 signing certificate. This gate has not run yet.
- Tightened FileProvider URI selection by adding a dedicated cache-path mapping and made host-side provider status handling null-safe.

- Dual-APK build and new verification gate passed in run **44643844227** at commit `c94a3e26481929bf5773f3982825a6e0d0d9d52e`. The host APK embeds the exact companion APK, the embedded APK passes ZIP integrity and package-ID checks, and both APKs have identical SHA-256 signer certificates. Artifact ID `14587242782`, size 446,637,569 bytes: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44643844227/artifacts/14587242782
- This validates the installable companion packaging/signature boundary only. The companion still reports `runtime_not_installed`; no Epic login, library, downloads, or launch is available yet.

## Engineering step log — 2026-11-20: Android POSIX shared-memory blocker found

- Downloaded and inspected the prior CPython ARM64 POC artifact `14564177384` (run `44590732396`). Its package includes `multiprocessing/shared_memory.py` but **does not include** the native `_posixshmem` extension.
- This is a hard Legendary blocker: Legendary 0.21.1's downloader imports `multiprocessing.shared_memory.SharedMemory` in its manager and workers, so the API-24 CPython package cannot be used as-is.
- Updated `epic-python-runtime-poc.yml` in commits `daa3c7e4c2a9b94bce48cb40d18db2eda2d35460`, `61a0dec6e5529f51baaad41a7fbd1fa6096a36bf`, `9b11419768c301805e45c996cf5c73cbdb3054e2`, and `f7ba72398c0a56208b362add2cbc62e9db7d416f`:
  - Set Android API level to 26, matching MaliWinlator minSdk.
  - Require `_posixshmem` in the ARM64 runtime archive.
  - Add an x86_64 managed-emulator smoke test that creates POSIX shared memory, attaches from a multiprocessing child, verifies bytes, and cleans up.
  - Fix a shell/YAML audit line and guard the smoke-test process creation.
- The previous API-24 run **44641730025** was already running with the old workflow and does not validate this fix. The updated workflow will run separately after it completes. No CPython/Legendary runtime is approved for integration until API-26 shared-memory validation passes.

- Updated status of legacy API-24 run **44641730025**: upstream CPython's x86_64 managed-emulator `ci --fast-ci` step completed successfully. It is now building the separate ARM64 package. This proves the CPython 3.15.2 x86_64 emulator test suite passes, but does not clear the Android shared-memory blocker because the inspected API-24 ARM64 package lacked `_posixshmem`. The API-26 run `44644276392` is queued to validate the corrected target.

- Further inspection of the API-24 CPython package showed it also lacks `_multiprocessing`, not just `_posixshmem`. The API-26 package audit now requires both native extensions; the emulator smoke test exercises `multiprocessing.Queue`/SemLock and shared-memory attachment. Workflow commit `60b525a1dcdb7fa6653f467972c5016675a423ad`; design note commit `bb8397c84e1514166807cc9f10477b3cbadcf883`.

- Legacy API-24 run **44641730025** completed successfully, including upstream CPython x86_64 Android emulator fast-CI and ARM64 package audit. Artifact `14588280495` (24,189,595 bytes): https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44641730025/artifacts/14588280495. This is retained as build evidence only; do not ship it because its API-24 build omits `_posixshmem` and `_multiprocessing`.
- Corrected API-26 workflow run **44644433739** has now started. It adds the POSIX shared-memory/multiprocessing emulator smoke test and requires both native modules in the ARM64 package.

## Engineering step log — 2026-11-20: secret-safe Epic-to-Wine launch plumbing

- Added `ProcessHelper.execSecure` in commit `1fa9a2a0df1144fec05392a3753cdb209a7b371e` (exact source commit recorded in Git history): launches with a per-process environment overlay, redirects output to `/dev/null`, and never logs argv/environment or mutates global `EnvironmentManager`.
- Updated `GuestProgramLauncherComponent` to keep Epic secret environment variables separate from ordinary container environment, apply them only after Wine prefix setup, suppress command logging for secure launches, and clear its secret reference after handoff.
- Added `EpicLaunchPayloadStore`, a five-minute, one-shot in-memory store for launch args, working directory, and ephemeral environment. Updated `XServerDisplayActivity` to consume a token from the intent, apply launch metadata to the in-memory shortcut, and configure secure execution. No credentials are persisted in shortcut files.
- These are host-side launch safety primitives only. The companion does not yet return Legendary launch parameters, and end-to-end Epic game launch is not implemented.

- Hardened the payload lifecycle after the initial implementation: `EpicLaunchPayloadStore.get()` allows Activity recreation without consuming the secret, and `GuestProgramLauncherComponent` now consumes the payload only after prefix setup, immediately before the secure game process starts. Commits `fee0ac6bfccb0be0dd786c4630bfca3b1ed4eaa1`, `5bf809694f7e3719727b8cb3e58ec5d7c7bac373`, `f760fbf4aef34fb16cc1b2da256a75555cc07a0c`, and design update `78da45676ebc24d089a83448afb2152471a52c03`.

- APK CI run **44645158888** caught a Java scope error: `epicLaunchToken` was declared inside the startup block but referenced later during launcher setup. Corrected it to an Activity field in commit `e01c1d0327551f275393e89b9c08cf03b58bbb3e`. The next app CI run must confirm the fix.

- APK CI run **44645410357** found the next integration error: `XServerDisplayActivity` called `setEpicLaunchPayloadToken`, but the setter insertion had not landed in `GuestProgramLauncherComponent`. Added the missing setter in commit `d9401d7bb52ee63b60a11a705385e58f4a363b6e`. A new build will validate it.

- Added a safe Legendary launch policy in design commit `834f77fd93453ba92bb8018b075dd32da05078d8`: do not execute host wrappers or arbitrary `pre_launch_command`; the initial bridge rejects non-empty pre-launch/host launch commands and passes only the game executable/arguments through Wine.

- Secure Epic launch plumbing compiles successfully in APK run **44645674170** at commit `d9401d7bb52ee63b60a11a705385e58f4a363b6e`. The embedded-companion integrity, package ID, and shared signer verification also passed. Artifact ID `14587364935`, size 446,638,103 bytes: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44645674170/artifacts/14587364935
- This artifact still contains only the Epic companion bootstrap shell; it is not a working Epic Store runtime.

- Added `epicbridge/LICENSE` (full GPL-3.0 text copied from the pinned Legendary source) and `epicbridge/README.md` in commits `4f9b332c6f7822f48e638fb9244197e77016726e` and `19253af3eb3b1d2a5f6ec86dc6a257c52d87ae95`. The companion module is explicitly GPL-3.0-or-later; the host remains MIT. The README documents the separate-package/signature boundary and records that no runtime is bundled yet.

- Added the same release signing-config logic to the companion module in commit `ef0449db0f793f84d49610e38899016cad0ce03a`. Host and companion will use the project's release keystore when present, otherwise both use the debug signer; the CI signer gate continues to verify the actual built APKs.

- Resolved the exact Legendary source pin: commit `42f6bdeadde3a9526dc8eb713763476999ac217b`, message `release: 0.21.1 Lowlife`. The companion README's abbreviated pin should be expanded to this full hash before the runtime source is vendored/built.

- Follow-up APK CI run **44645970680** completed successfully at source commit `ef0449db0f793f84d49610e38899016cad0ce03a`, including the secure one-shot Epic launch payload path, GPL companion docs, matching release-signing configuration, embedded APK integrity, package-ID, and signer checks. Artifact ID `14589465267`, size 446,638,120 bytes: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44645970680/artifacts/14589465267

- Documentation-only pin update to the full Legendary 0.21.1 commit triggered APK CI run **44646484072**, which completed successfully including embedded companion integrity and signer verification. Artifact ID `14588732112`, size 446,638,088 bytes: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44646484072/artifacts/14588732112

## Engineering step log — 2026-11-20: Epic companion packaging and source pin

- Successful host/companion APK CI run **44646484072**, source commit `cc05504481babde64b0324d89d39427d2cc6453b`. Run: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44646484072
- Artifact `maliwinlator-1.2.1-standard-debug-cc05504481babde64b0324d89d39427d2cc6453b`, ID `14588732112`, reported size 446,638,088 bytes: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44646484072/artifacts/14588732112
- Build success confirms Gradle packages the separate companion APK asset and host APK successfully at this source revision. It does not validate companion installation on a phone, Epic authentication, Legendary execution, or any Store feature.
- `epicbridge/README.md` now records the full pinned Legendary 0.21.1 source commit `42f6bdeadde3a9526dc8eb713763476999ac217b`, CPython `v3.15.2`, ARM64 Android target and the signature-protected IPC contract.
- Current bridge provider deliberately reports `runtime_ready=false`; no Legendary/CPython runtime is shipped yet. Do not present the current APK as an implemented Epic Store.
- Source review confirms the host already has an `EpicFragment` shell, `EpicBridgeClient` companion installer/status API, and drawer navigation. The companion provider currently supports status/ping only and explicitly reports `runtime_ready=false`; it has no Legendary command operations yet. The existing screen accurately reports the unavailable runtime and exposes companion installation/storage settings, but is not an Epic Store/library UI. Next work remains runtime viability, provider command protocol, and then real account/library/install UI; do not expose nonfunctional auth/download buttons.

- The separate CPython Android runtime gate remains active in workflow run **44644433739**: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44644433739. The job is still in its upstream CPython build/package/test step; the managed Android shared-memory/multiprocessing smoke test has not started. Its result is therefore pending, not passed. The GitHub job-log endpoint currently returns BlobNotFound while the job is active; monitor job state and retrieve logs after completion.
- Handoff update commit: `a3d531b1cea7dbfd6a7512f63848fd25205f9fb3`. Continue updating this file after each meaningful code/build/test milestone.

## Engineering step log — 2026-11-20: Epic companion IPC caller restriction

- Hardened `EpicBridgeProvider.call()` in commits `56f24666e1384c12fbf2e36f2e464f7818b6c6bc` and cleanup `8bd865051c0d1203f8015693c945e77f96b70377`.
- The provider now checks the Binder caller UID's package list and permits calls only from the standard host package `com.maliwinlator.app`, in addition to the existing signature-level permission. Unauthorized callers receive a typed `CALLER_NOT_ALLOWED` response.
- This is source-level IPC hardening; no Android device test has yet exercised the cross-package signature permission or caller-UID check. The companion remains a status-only bootstrap and does not yet execute Legendary.
- The Epic CPython workflow run `44644433739` remains in progress at the upstream Android CI build step; the Android shared-memory/multiprocessing test has not started. No runtime compatibility result is claimed.

- Added host-side protocol-version validation to `EpicBridgeClient.getStatus()` in commit `d5105ce912bbf686d32aac62bb6da206d2ff43e0`. A stale/mismatched companion now produces a distinct `protocol_mismatch` state instead of being treated as a valid bridge response.
- The standard debug APK CI triggered by the IPC hardening is run **44647036824** and was still in progress at the last poll. It must complete successfully before treating the latest provider/client changes as build-verified.

- The earlier IPC-hardening build run `44647036824` was cancelled by the newer commit-triggered run; it is not a failure. Replacement build run **44647129110** at source commit `d5105ce912bbf686d32aac62bb6da206d2ff43e0` is in progress: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44647129110

## Engineering step log — 2026-11-20: CPython Android test findings and Legendary downloader blocker

- CPython managed-emulator workflow **44644433739** completed with failure after 17m20s: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44644433739
- The x86_64 Android build/configure stage completed and CPython ran 45,953 tests. The only unique failing test file was `test_grp`: Android's group database returned `gr_passwd=None`, while CPython's generic test expects a string. The rerun repeated the same platform-specific failure. `test_ssl`, `test_ctypes`, and `test_sqlite3` passed. This is not evidence of a general CPython runtime failure.
- More importantly, the Android target configure log shows `shm_open` and `shm_unlink` are unavailable from the Bionic sysroot, so CPython omits both `_posixshmem` and `_multiprocessing`. The upstream test run confirms `multiprocessing` tests are skipped because `_multiprocessing` cannot be imported. This is a real blocker for unmodified Legendary.
- Pinned Legendary 0.21.1 imports `multiprocessing.shared_memory.SharedMemory` in both `legendary/downloader/mp/workers.py` and `manager.py`; it also uses `multiprocessing.Process` and `multiprocessing.Queue` for the downloader and status/logging paths. Therefore merely disabling shared-memory tests or reducing worker count is not sufficient.
- Updated the CPython workflow to stop gating on the entire upstream suite's Android-specific `grp` mismatch and instead run a focused Android probe for TLS/CA/DNS/HTTPS, ctypes, SQLite, zlib, file locking, hashing and subprocess creation, then build the ARM64 package. Workflow changes: `1f927208c15f520acd4abfbcd42c6469eb05fa90`, probe: `969b2c0369683bd45f4b81f6795f81ffcd3a2abc`, trigger update: `fe4264cd0f4cb333d6e6cb9e4f74fceb2d6c3f4b`. A new run is expected from the workflow-path trigger; do not claim it passed until checked.
- Required next engineering task: implement and test an Android-specific thread-based Legendary downloader backend (thread-safe `queue.Queue`, worker `Thread` objects, and a bounded in-process `mmap` buffer instead of `multiprocessing` + POSIX named shared memory). Patch the CLI/core queue imports as well. Preserve the existing Linux upstream path; apply the adaptation only to the separately licensed Android companion's pinned Legendary source. Test cancellation, worker shutdown, progress accounting, bounded memory, resume and integrity before enabling installs.

## Engineering step log — 2026-11-20: Legendary Android thread-backend patch prototype

- Added `epicbridge/poc/patch_legendary_android.py`, a version-pinned source transformation for Legendary 0.21.1. It replaces the downloader manager/workers with threads, replaces multiprocessing queues with `queue.Queue`, uses a shared anonymous `mmap` buffer instead of POSIX named shared memory, removes process-only shutdown operations, and updates core/CLI queue imports.
- The Android patch caps download workers at four and the shared buffer at 512 MiB to avoid the upstream desktop defaults (up to 16 workers / 2 GiB buffer) being blindly applied on mobile. Stop now sends a termination sentinel to every downloader worker and one to the writer thread.
- Added CI steps to check out the exact Legendary source commit `42f6bdeadde3a9526dc8eb713763476999ac217b`, apply the patch, compile all Legendary Python modules, and assert no multiprocessing imports remain in the patched downloader/core/CLI paths. Workflow changes are in `ca389ce0f1f30dabca7eeb943f9e572ca007710e`; patch implementation commits: `c26c79e0526c36be669d122b57aad7fc3dce26de`, `7a22b801fb7f5b55064fa003211c74d954ea9e14`, `6aaad1eff5d531d9734e5a5899601afcf5cd25f5`, and `e940dbb4749a6d7b07990d40825369aa7e1bb580`.
- This is a prototype patch only. It has not yet been applied to the pinned Legendary tree in CI, imported on Android, or exercised with real downloads. The critical next checks are patch-anchor correctness, queue/thread shutdown, cancellation, bounded memory, resume/integrity behavior, and downloader throughput.

- Extended the CPython POC workflow with a host-Python smoke test for the patched Legendary package: installs pinned runtime dependencies, runs `python -m legendary --version`, imports the patched manager, verifies it is thread-backed, and tests the anonymous mmap buffer. This catches transformation/import/runtime regressions before the more expensive Android target build; it is not a substitute for Android emulator testing. Workflow update commit `53cc0090989ab37eefb104598e97d85ed067eacf`.

- Focused CPython emulator run **44647437428** failed before executing the smoke module because its checkout was pinned to commit `1f927208...`, which predates creation of `epicbridge/poc/site-packages/epic_runtime_smoke.py`. The CPython build/package itself completed; the failure was a missing test asset path, not a failed Python import/TLS/subprocess test. The next workflow run is triggered after the probe file and workflow path trigger were committed, so it should include the file. Run: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44647437428

- The APK build covering the Epic provider caller restriction, protocol-version validation, and current host launch plumbing passed in CI run **44648026511** at source commit `e940dbb4749a6d7b07990d40825369aa7e1bb580`. Artifact ID `14588214711`, size 446,639,159 bytes: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44648026511/artifacts/14588214711. This verifies compilation/package integration only; no device-side companion IPC test or Epic functionality test has been performed.

## Engineering step log — 2026-11-20: hardening the Android Legendary thread backend

- A source-level review of pinned Legendary 0.21.1 found two hazards in the first transformation prototype:
  1. The upstream downloader worker classes close their own SharedMemory handle when they exit. With threads all workers share one anonymous mmap object, so worker-side close would invalidate memory still in use by other workers. The patch now removes worker-side close calls; the manager owns and closes the mmap only after all workers have joined.
  2. The manager's KeyboardInterrupt path still contained Process.exitcode, terminate(), and multiprocessing queue close()/join_thread() calls. The patch now replaces process exit checks with thread liveness checks, makes queue cleanup compatible with queue.Queue, and changes the final exit(0) to a normal thread return.
- These changes are in patcher commit e0815b36d419aa88edb497f4a1d39548337be464. They are not runtime-validated until the host smoke CI passes.
- Corrected a stale CPython ARM64 package audit: Android Bionic lacks shm_open/shm_unlink, so _posixshmem and _multiprocessing are expected to be absent. The production package audit now asserts those modules are absent, and build metadata describes the Android thread backend rather than claiming POSIX shared-memory support. Workflow commit bf3b449f254a740a2658d02d280081586cf106b1.
- Current workflow run 44648144316 (source commit 53cc0090989ab37eefb104598e97d85ed067eacf) is building the x86_64 Android CPython runtime for the focused emulator probe and host Legendary smoke test. It does not yet include the latest patch hardening or corrected ARM64 audit. A new run will be triggered by the subsequent patcher commits.

- Additional cancellation review found that CTRL-C is delivered to Legendary's CLI main thread while the download manager is a separate process/thread. Once adapted to threads, the CLI must explicitly call the manager's cooperative stop method when join() is interrupted; otherwise non-daemon workers could keep the companion process alive. The Android patcher now wraps dlm.join() with KeyboardInterrupt handling, sends worker/writer sentinels via dlm.stop(), waits up to 60 seconds, then propagates the interrupt. Patcher commit: c2ae74c6cc05bbd7ce176303a5ac80aadb1581d0.

## Engineering step log — 2026-11-20: Android TLS trust-store finding

- Managed Android emulator run **44648144316** built the x86_64 Android CPython runtime successfully and launched the testbed successfully. The focused runtime probe then failed specifically at HTTPS certificate verification: OpenSSL/CPython's default CA lookup did not find the Android system trust store, producing `SSLCertVerificationError: unable to get local issuer certificate`. This is a real integration issue, not a CPython build failure.
- Legendary uses Requests, which uses certifi's CA bundle. The probe now stages pinned `certifi==2025.1.31` into the testbed site-packages and creates its SSL context with `certifi.where()`, matching the intended Requests trust path. Probe update commit `d5bff4f5f035a5fc49e36a950edc56e4ace50651`; workflow staging commit `d30b9f5b7f646bce41c11599fd95c17cf7b6cba5`.
- This fix has not yet been emulator-tested. A new POC workflow run will test TLS using the bundled CA bundle; only then can HTTPS support be marked validated.

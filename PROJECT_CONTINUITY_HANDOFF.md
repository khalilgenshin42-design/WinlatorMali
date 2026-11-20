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

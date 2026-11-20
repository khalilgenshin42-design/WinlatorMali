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

- Corrected the worker-side mmap close patch anchor after checking the pinned upstream source: only DLWorker closes its attached shared-memory handle; FileWorker does not. The transformation now expects exactly one close site, avoiding a false patch failure. Commit `2bdadb0403a822108ce92000f06569d634834797`.

- The failing Android probe executes checks sequentially. Before reaching TLS, it successfully passed 64-bit pointer size, hashlib SHA-256, zlib round-trip, and SQLite create/insert/select. The run therefore positively validates those four APIs on the Android emulator; fcntl locking and subprocess were not reached because TLS failed first.

- Standard MaliWinlator 1.2.1 debug APK CI completed successfully at run **44649327523**, source commit `2bdadb0403a822108ce92000f06569d634834797`. The job verified the embedded Epic Bridge APK and shared signer, then uploaded artifact ID `14590995035` (446,639,150 bytes). This validates the separate companion packaging/installer asset build path, not runtime readiness or Epic login/store operations.

- Tightened the CA validation to exercise the exact HTTP stack Legendary uses: the Android testbed now stages pinned Requests 2.32.4 plus certifi 2025.1.31, and the probe performs a real HTTPS GET through Requests rather than testing urllib alone. It also asserts the explicit SSL context remains in certificate-verification mode; TLS verification is never disabled. Probe commits `75eced23ab6acf665599f39324821d0bc50c096c`, `b86fe75d48ca3d8bdf8527cddc4c07cf3f230c6c`; workflow commit `67d3ada6dd4934d757eb2646f884629543347aa7`.

- Follow-up pre-fix run **44648878742** reproduced the same certificate-verification failure after the emulator successfully built and launched CPython; it was triggered from commit `c2ae74c6...`, before the certifi/Requests staging change. It is expected to fail and does not test the new fix. The first run containing the Requests + certifi staging is **44649787558**, currently in progress at commit `67d3ada6dd4934d757eb2646f884629543347aa7`.

## Engineering step log — 2026-11-20: ordered thread-backend cancellation

- A deeper shutdown review found a race in the initial thread adaptation: queuing the writer termination marker before the download-result handler has stopped could allow late writer tasks to be placed behind the marker. That risks leaving completed chunks unwritten on cancellation.
- Corrected the design: the public stop method now only sets the shared running flag and wakes condition waiters. The manager's cleanup joins its download-job producer and download-result handler first, drains only not-yet-started download jobs on cancellation, then sends downloader sentinels and finally the writer sentinel. This ensures no producer can enqueue writer work after the writer termination marker. Active workers finish their current I/O and are joined; resume metadata remains owned by Legendary's existing file writer.
- Transformation update commit `e5c76c993546f14d2901216fe71eea36e494feed`. This remains a prototype until the patcher and download/cancel tests pass CI.

- Follow-up review of Legendary's download scheduler found its inner producer loop did not re-check the shared running flag. A stop while waiting for a free shared-memory segment could therefore leave the producer spinning/retrying after cancellation. The Android patch now gates the inner scheduling loop on `self.running` as well as the outer loop. Commit `98642b6cbc5cc5c55f2f4713c477de849e80dee2`.

- A further resume-integrity review found the writer-result handler must remain alive after cancellation: it records completed-file hashes in Legendary's resume file and returns shared-memory segments to the pool. The Android patch now lets this handler run until the writer's explicit termination message, even after the download producer/result handler have stopped. Commit `2442b60c0ed06eb60ce902e0a8b794bedd72fb59`.

- Expanded the fast host-side Legendary smoke gate to import both downloader worker classes, assert the manager and workers are all thread-backed, exercise the manager's cooperative stop signal, and verify the anonymous mmap buffer. This catches compatibility regressions before waiting for the managed Android emulator test. Workflow update: `04e1e4215c272dc487ff086062b70e2c33cb172c`.

- Added `epicbridge/poc/requirements-android.txt` to pin the Python runtime dependency set for Legendary 0.21.1: Requests 2.32.4, certifi 2025.1.31, charset-normalizer 3.4.1, idna 3.10, urllib3 2.3.0, filelock 3.18.0, and pycryptodomex 3.23.0. Native dependencies still require Android/CPython 3.15 ARM64 builds; host Linux wheels must not be copied into the Android runtime. Commit `0a7c92f83ece40cf66ab39d957fd6ad5614e0b44`.

- Added the Android dependency lock file to the CPython/Legendary POC workflow path filters so changing dependency pins automatically reruns the validation pipeline. Workflow commit `ce12e689f1b146fb5944f3c5bad2b583924be69a`.

## Engineering step log — 2026-11-20: Android runtime probe passed; patch anchor corrected

- Managed Android emulator run **44649787558** passed the focused CPython runtime probe. The log printed `EPIC_ANDROID_RUNTIME_PREREQUISITES_OK`, confirming CPython 3.15.2 on Android can import Requests/certifi, complete HTTPS with certificate verification, use ctypes, SQLite, zlib, hashlib, fcntl file locks, and launch an Android system subprocess. This is the first successful end-to-end Android runtime probe.
- The same run then failed during Legendary patch application because the KeyboardInterrupt cleanup anchor omitted an upstream `child.join(timeout=5.0)` line. The exact pinned manager source was inspected and the patch now includes that line in the anchor and replaces the complete process cleanup block with thread join/liveness checks. Patcher correction commit `16f3c6fd941e371b92c285a4c84dc64c5cafeb13`.
- No Legendary patch or CLI smoke has passed yet; the next workflow run must prove the patch applies, all Python files compile, multiprocessing imports/process-only calls are gone, and the patched CLI imports/runs.

- Updated `PROJECT_ROADMAP.md` to reflect the current state: host/companion packaging and Android CPython runtime probe are validated; Legendary thread backend, ARM64 native dependency packaging, JNI integration, and all store operations remain open. Commit `1bd6c1c7fb6b26a71d2526f0eba8da8928850253`.

- Expanded the Android runtime probe to validate the exact anonymous mmap primitive required by the thread-based downloader: allocate a 1 MiB anonymous mapping, have eight Python threads write independent regions concurrently, join them, verify the bytes, and close the mapping. Probe commit `cc7db466ddb377517b17b0cc2070d4be4d2b2298`. This is not yet device-tested; the next CPython run will cover it.

- Pre-correction run **44651049229** again passed the full Android runtime probe (`EPIC_ANDROID_RUNTIME_PREREQUISITES_OK`) and then reproduced the known KeyboardInterrupt cleanup anchor mismatch. It was built from commit `ce12e689...`, before patcher fix `16f3c6fd...`; it does not test the corrected anchor.

- Inspected Legendary 0.21.1 launch implementation: `launch <app> --json` returns structured LaunchParameters before spawning, and `--no-wine` avoids Legendary launching a Wine process. This is the intended bridge into Winlator's existing guest launch pipeline; it preserves game parameters/environment/EOS values while using the selected Winlator container's Wine runtime. Also confirmed `Container.DEFAULT_DRIVES` maps Wine F: to Android external storage root, and Legendary install supports `--base-path`; target library is `/storage/emulated/0/MaliWinlator/EpicGames`. Source inspection only, not yet runtime-tested. Design update commit `1355408d6296af86f61adebf3eedbc509dc434e0`.

- The mmap-enhanced emulator run **44651854079** again passed the full Android runtime probe, but the patcher failed because its KeyboardInterrupt cleanup transformation was duplicated: an earlier patch block had already replaced the exact process-only cleanup anchor, so a later second replacement incorrectly expected it again. Removed the duplicate transformation while retaining the original process-to-thread conversion and the ordered cancellation changes. Fix commit `98cf4a622c88d8db588c45fa00809a499fa5650a`. The mmap-specific probe result is not yet separately confirmed in logs; the run printed the same aggregate success marker after the expanded probe.

## Engineering step log — 2026-11-20: remove PyCryptodomex from Android runtime dependency plan

- Source inspection of pinned Legendary 0.21.1 found only two production imports from PyCryptodomex, both AES-GCM decrypt-and-verify calls: encrypted chunk payloads and encrypted manifests. No other Cryptodome APIs are used.
- Avoided shipping/cross-compiling the large PyCryptodomex native extension set. Added an Android-only Python compatibility shim that exposes the exact `AES.MODE_GCM` / `AES.new(...).decrypt_and_verify(data, tag)` surface Legendary uses, delegating to a built-in `_epiccrypto` JNI module backed by Android JCA `Cipher.getInstance("AES/GCM/NoPadding")`. This preserves GCM authentication/tag verification and uses the platform crypto provider; no TLS/crypto verification is disabled.
- The Legendary patcher now replaces the two AES imports with `legendary.android_crypto.AES` and copies the shim into the pinned source tree. Removed PyCryptodomex from the Android runtime dependency lock. Added a host-only `_epiccrypto` compatibility test shim using PyCryptodomex to test the wrapper API against the NIST AES-128-GCM zero-key/zero-nonce known-answer vector; this host test dependency is not bundled on Android.
- Files/commits: shim `3125d7ad607fac7acb9c15450ba1663a2383fadb`; patcher `a2e4bf818bb73f8eb8cac91c9160d078ca231461`; host NIST vector test `e00edc8188c548105adb6f21860dc0f58cd3ee7a`; dependency lock `f1ca89ccf9a97a33b42980a73141dad7c7e0360e`.
- Important remaining work: implement the native `_epiccrypto` built-in module in the companion JNI library, validate the NIST vector on Android, then test actual encrypted manifest/chunk decryption. Until then, the Android JCA crypto path is a design/prototype, not implemented runtime functionality.

- Updated `PROJECT_ROADMAP.md` to reflect the Android JCA AES-GCM strategy and remove the obsolete requirement to cross-build PyCryptodomex native extensions. Roadmap commit `0ccd269d76309e5beb77af8f83cfb6403a3ecc32`.

## Engineering continuation — 2026-11-20 02:37 local: workflow failure triage and current branch audit

- Reviewed the user's screenshot and inspected the live repository rather than assuming the visible activity list represented a still-running GitHub job.
- Confirmed repository: `khalilgenshin42-design/WinlatorMali`; active branch: `development/maliwinlator-1.2.1-bootstrap`. The older `development/maliwinlator-bootstrap` branch remains the incorrect 1.2.0 line and must not receive work.
- Current active-branch HEAD at audit time: `785d72a5db03a07b5c6293b411c3065ddffe6538` (`feat: report Android AES-GCM readiness through Epic Bridge IPC`, 2026-11-20 01:30:30Z). This is newer than the failed POC run's source commit.
- Inspected GitHub Actions run **44651049229** (run number 30; source commit `ce12e689f1b146fb5944f3c5bad2b583924be69a`). The x86_64 Android CPython build, emulator launch, and runtime prerequisite smoke test all succeeded. The job failed only at `Apply and statically validate Android thread backend`; the host Legendary smoke, production ARM64 runtime build, package audit, and artifact upload were skipped.
- Exact failure: the older patcher expected a process-cleanup source anchor containing `child.exitcode is None` / `child.terminate()`, but the anchor was not found. This is a deterministic source-transformation mismatch, not an Android TLS or CPython failure.
- Inspected pinned Legendary 0.21.1 source at commit `42f6bdeadde3a9526dc8eb713763476999ac217b` and the current branch's `epicbridge/poc/patch_legendary_android.py`. The current patcher now anchors the KeyboardInterrupt cleanup block with the preceding `child.join(timeout=5.0)` lines and converts process exit/terminate checks to thread liveness checks. This is a later correction than the failed run's source commit; it has **not yet been validated by a successful CI run**.
- Current branch contains subsequent Android JCA AES-GCM work: compatibility shim, NIST known-answer test, and Epic Bridge status reporting commits. These commits establish implementation/test scaffolding only; no successful Android JNI/JCA cryptographic self-test or actual Epic encrypted manifest/chunk operation is claimed.
- Do not rerun the failed run as-is: it would rerun the stale `ce12e689...` revision. Next validation must execute the POC workflow against the current branch HEAD (or a new commit containing the final patcher), and must pass patch application, Python compileall, no remaining downloader multiprocessing imports, host Legendary CLI smoke, NIST AES-GCM vector, thread/mmap smoke, and ARM64 runtime packaging/audit.
- Epic login, owned-library enumeration, downloads/updates/uninstall, and Wine-container game launch remain unimplemented/unverified. The separate Epic Bridge APK packaging gate passed earlier (run **44649327523**); that is not proof of working Epic functionality.
- This update records evidence and blockers only. No app/system Vulkan driver, existing Wine prefix, credentials, or user data were modified.

## Engineering continuation — 2026-11-20: repair escaped-newline patcher anchors

- Follow-up inspection of the current `epicbridge/poc/patch_legendary_android.py` found a second deterministic defect beyond the stale cleanup anchor: 22 anchor strings contained double-escaped newline sequences (Python source `\\\\n`), which evaluate to literal backslash-plus-`n` rather than actual line breaks. Those transformations could never match the pinned Legendary source text.
- Normalized the affected patcher string literals to real newline escapes while preserving the actual source text being matched. Verified the patcher file contains zero remaining double-escaped newline sequences after normalization.
- Commit: `86d583f96fa479c8a7411e9a36fac1b245cb0620` — `fix: normalize Legendary patch anchors to real newlines`.
- This change touches a path explicitly watched by `.github/workflows/epic-python-runtime-poc.yml`, so it should trigger the Android CPython/Legendary POC workflow on the active development branch. At the time of this handoff update, no commit status/check result was yet visible; do not claim the new run has started or passed until a run/job is observed.
- Required next checks: patch applies cleanly to pinned Legendary 0.21.1; all patched modules compile; downloader modules no longer import multiprocessing; host CLI and thread/mmap/AES-GCM smoke tests pass; production ARM64 runtime packages and audits successfully. Any subsequent patch-anchor mismatch must be corrected against the exact pinned upstream source, not by weakening the expected-occurrence assertions.

- Validation triggered as expected: GitHub Actions run **44655229853** (run number 37) is now running from patcher-fix commit `86d583f96fa479c8a7411e9a36fac1b245cb0620`. At the latest observed poll, the job had checked out MaliWinlator and was checking out pinned CPython source; later steps were still pending. This is a live run, not a success. Track it before making further code changes.
- The handoff documentation update itself is commit `c7e71263f70398600ebd71b127a1873105d8c770`; it does not alter the source revision under test.

## Engineering continuation — 2026-11-20: CPython Android smoke passed; Legendary host packaging metadata fixed

- Run **44655229853** reached and passed the Android CPython runtime prerequisite smoke test. This verifies on the managed Android emulator: 64-bit runtime, zlib, anonymous mmap shared between threads, SQLite, TLS/CA trust via requests, advisory file locks, and Bionic subprocess creation.
- The Legendary source patch step also passed: `LEGENDARY_ANDROID_THREAD_BACKEND_PATCHED`; patched source passed `compileall` and the no-multiprocessing-import static check.
- The next host smoke step failed before the actual thread/AES test because the checked-out Legendary source had not been installed as a Python distribution. Its `legendary/__init__.py` resolves `__version__` via `importlib.metadata.version('legendary-gl')`, which raised `PackageNotFoundError`.
- Corrected `.github/workflows/epic-python-runtime-poc.yml` to install the checked-out pinned source with `python3 -m pip install --no-deps ./legendary` before running `python -m legendary --version` and the host backend smoke.
- Workflow correction commit: `66ec0a5687be74be8e23f26222060726de86ff24`. This workflow file is a configured push trigger, so a fresh POC run should start from this commit. It is not yet validated.
- The failure is packaging metadata only; no failure was observed in the Android runtime smoke or patcher transformation. Production ARM64 CPython packaging remains gated on the next successful host smoke.

## Engineering continuation — 2026-11-20: embedded Legendary runtime and Epic UI integration work

### Runtime POC evidence and correction
- Run **44656813830** (run 44) passed the managed Android x86_64 CPython prerequisite smoke and the pinned Legendary Android thread-backend patch/static validation.
- Its host smoke failed at the CLI version command: Legendary 0.21.1 has no `legendary.__main__`, so `python -m legendary --version` is not a valid invocation. The installed distribution metadata was present; the failure was not another metadata failure.
- Corrected the workflow to invoke `python -m legendary.cli --version` in commit `716fe32f9532ecd6ee515fcd29ff23658e6a5af6`.
- Run **44657178582** (run 45) was started from the previous workflow revision and is not sufficient to validate that correction. Run **46** is queued as **44657880743** and includes the corrected CLI invocation plus the dual-ABI runtime packaging changes.
- The POC workflow now stages pinned Legendary 0.21.1 and universal pure-Python dependencies into Android x86_64 and ARM64 CPython prefixes, then packages both runtimes for the downstream integration build. This packaging change has not yet passed CI.

### Implemented source work — validation still pending
- Added an embedded CPython JNI runtime in `epicbridge/src/main/c`, with an explicit no-runtime stub for bootstrap builds. Runtime-enabled builds link against the Android CPython prefix and package its shared libraries and standard library.
- Added a built-in `_epiccrypto` Python module whose AES-GCM decrypt operation calls the companion's Android JCA implementation through JNI. The existing JCA NIST known-answer self-test remains the Java-side check.
- Added `EpicPythonRuntime` extraction/initialization, persistent Python interpreter calls, and a Python command module for Epic account status, authorization-code login, owned-library enumeration (including non-asset items), install/uninstall, and Wine launch metadata.
- Kept Legendary account/config data under a separate app-private HOME directory. Runtime refreshes replace only the Python tree, preserving saved Epic account data.
- Added a runtime self-test for Legendary distribution metadata, requests/certifi TLS setup, JCA AES-GCM, anonymous mmap, and thread-worker shutdown, plus an Android instrumentation test. Neither the JNI runtime nor the instrumentation test has yet passed on an emulator.
- Added version-2 signed-provider IPC, host Epic tab controls, Epic sign-in code entry, library listing, install/uninstall actions, container selection, and launch handoff through `EpicLaunchPayloadStore` into Winlator's existing secure Wine launch path.
- Added a foreground `dataSync` service and partial wake lock for Epic installs, with job-state polling from the host UI. The install path is the shared Winlator directory `MaliWinlator/drive_c/Games/Epic`.
- Added Android 11 package visibility for the companion and separate storage-permission handling for the host and companion apps.
- Added `.github/workflows/epic-integration-build.yml` on the repository default branch. It is gated to successful dual-ABI POC runs (run number >=45), downloads that run's runtime artifact, runs the x86_64 Android instrumentation test, and builds the ARM64 MaliWinlator 1.2.1 APK with the runtime-enabled Epic Bridge embedded.

### Current validation and blockers
- Standard MaliWinlator 1.2.1 APK workflow run **44657783606** (run 115) is building the source/companion after the Java, provider, service, and CMake-stub changes; it has not yet completed.
- The production integration workflow must still validate: extraction of both runtime tarballs, CMake linking against `libpython3.15.so`, Android JNI loading, Legendary imports, JNI AES-GCM NIST vector, worker-thread lifecycle, and packaging of the embedded companion APK.
- After CI passes, the actual Xiaomi 15T test must still confirm Epic browser authentication, account/library retrieval, a real game download and install, and launch into a selected Winlator container. Do not describe Epic integration as ready until those device-level checks pass.
- Latest workflow fix commit: `716fe32f9532ecd6ee515fcd29ff23658e6a5af6`. Subsequent runtime/UI source commits are on `development/maliwinlator-1.2.1-bootstrap`; the default-branch integration workflow is on `bionic-mali-1.2`.

## Status refresh — 2026-11-20

- The Android TLS rerun **44649787558** did not reach the TLS probe: it failed earlier in the Legendary Android patcher because a source-anchor expected one exact worker-termination snippet, but the pinned Legendary 0.21.1 source had a different shape. This is a patcher-anchor mismatch, not an Android TLS result. The failed step was `Apply and statically validate Android thread backend`; see the workflow logs for run 44649787558.
- A newer CPython/runtime correction is in flight at run **44657999414**, commit `b50bca3eab40aa77dcb10f10041429435a9b27ac`, fixing JNI runtime detection to validate by actually loading the runtime rather than inspecting extracted files. At last check it was still installing the pinned Android toolchain; no pass is claimed.
- A second runtime smoke-test correction is queued in run **44657880743**, commit `716fe32f9532ecd6ee515fcd29ff23658e6a5af6`, changing the host-side version probe to invoke Legendary as a Python module. It was pending at last check.
- Current app-side work has moved beyond the earlier bootstrap: the Epic companion now has account/provider and UI integration, install/download service scaffolding, storage-permission handling, notification permission, and a launch handoff into Winlator's existing container launch path. These are implementation changes, not proof of successful Epic authentication or a completed game install.
- Latest source changes for Epic UI/storage/notifications are being built by run **44657999414**. The Xiaomi 15T has not yet been asked to test this in-progress build.
- Handoff refreshed after reviewing the latest failed runtime patcher logs and current CI runs. Epic Store is **not yet complete or device-validated**. Required remaining proof: Android ARM64 runtime package and JNI load; Legendary imports and AES-GCM crypto; actual Epic browser login/account/library; real game download/install; launch into selected container.


## Engineering continuation — 2026-11-20 03:05 local: CI artifact-audit regex repair

- Re-checked the live repository and confirmed the active branch is `development/maliwinlator-1.2.1-bootstrap`; the separate `development/maliwinlator-bootstrap` branch remains the obsolete 1.2 line.
- Compared the active 1.2.1 branch with `bionic-mali-1.2`: the development branch is 266 commits ahead and 8 commits behind, with the common base at the exact 1.2.1 release commit `dfcdd4aaeff6def126ad8173619790806a5830db`. Do not merge or rebase the divergent 1.2 default branch blindly; its integration workflow is intentionally hosted there and checks out the 1.2.1 development branch.
- Audited the downstream integration workflow on `bionic-mali-1.2`. It waits for successful CPython POC runs with run number >=45, downloads both x86_64 and ARM64 runtime archives, runs the Epic Bridge Android instrumentation test on x86_64, then builds the ARM64 host APK and verifies the embedded companion APK/runtime.
- Audited the active branch's CPython workflow and found four over-escaped dots in the x86_64 archive checks: the `libpython*.so` and Legendary `dist-info/METADATA` patterns used two literal backslashes before dots. Under grep -E this does not match the intended filenames and would reject a valid runtime package.
- Corrected all four over-escaped regexes to standard ERE escaped-dot patterns in commit `ead4aabacfe92d26dbb79439477deb03a0585748` (`ci: fix escaped regexes in runtime artifact audit`). The change is on the active 1.2.1 branch and matches the CPython workflow's push path filter, so it should start a fresh runtime POC run.
- Inspected prior downstream host APK workflow runs **44657783606** and **44657999414**: both were cancelled during `Build standard debug APK`; neither completed the companion embedding checks or artifact upload. They are not build successes. POC run **44657880743** had no jobs/artifacts visible at this audit; no runtime artifact is claimed from it.
- No Epic login, account, library, download, install, update, uninstall, or game-launch operation has been proven. Existing UI/IPC/service code remains implementation scaffolding until the next dual-ABI runtime and integration workflows pass.

### Immediate next validation gates

1. Observe the new CPython POC run triggered by `ead4aabacfe92d26dbb79439477deb03a0585748`; inspect its exact job steps and logs. Do not treat a queued run or empty jobs response as a pass.
2. Require the Android emulator runtime smoke, Legendary patch application/compile/static check, host CLI/thread/AES-GCM smoke, dual-ABI package build, and archive audit to pass.
3. Only then require the downstream integration workflow to extract both runtime packages, pass Android x86_64 JNI/instrumentation checks, build the ARM64 APK, and upload both APKs plus hashes.
4. After an installable artifact exists, ask the user only for Xiaomi 15T validation of Epic browser login, owned library, one real install, and launch through a selected container. The official Epic Launcher fallback remains a separate later milestone.


## Engineering continuation — 2026-11-20: Epic library merge hardening

- Re-verified that the CPython workflow file on `development/maliwinlator-1.2.1-bootstrap` contains the corrected archive-audit regular expressions from `ead4aabacfe92d26dbb79439477deb03a0585748`. GitHub's combined commit-status endpoint returned no status checks for that commit; this is not evidence of a passing build. The connector available in this session exposes workflow jobs/artifacts by run ID but no repository-wide workflow-run listing, so a new run ID could not be established from the available API.
- Performed a focused source audit of the Legendary runner, JNI runtime, Android provider/service, manifests, and app-side bridge client. Confirmed the host manifest declares the companion's signature permission and package visibility query. No device-level authentication or install test is claimed.
- Found a defensive edge case in `_library()`: non-asset DLC results could reference a catalog ID absent from the primary asset DLC map, causing a `KeyError`. The previous generator-based non-asset game merge also did not add newly appended names to the deduplication set, so duplicate non-asset records could pass through.
- Fixed both cases in commit `e7a9c90ddf3f1cc5a49f37275cc73483cddebee0` (`fix: merge non-asset Epic DLC catalogs defensively`): non-asset games are now appended with incremental deduplication, and DLC lists use `setdefault` before extension.
- This is a source-level correctness hardening only. It has not yet been exercised against Epic's live library API or by the Android runtime CI. The next validation should add/execute focused fake-catalog tests, then observe the dual-ABI CPython workflow and downstream Android instrumentation/APK pipeline.


## Engineering continuation — 2026-11-20: Epic library regression coverage

- Added focused host-side unit tests at `epicbridge/poc/test_epicbridge_runner.py` covering: non-asset DLC catalogs missing from the primary catalog map; duplicate non-asset game records; primary/non-asset duplicate suppression; case-insensitive game sorting; and merging primary plus non-asset DLC entries.
- Added the test file to the CPython workflow's push path filters and inserted a dedicated regression-test step before the patched-Legendary host smoke test. The test command sets `PYTHONPATH` to the bridge runner package and uses Python's standard-library unittest runner; it does not need Epic credentials or network access.
- Test addition commit: `6d8c08365c894bd40fcf51fad074ddb648b17526`. Workflow integration commit: `76cb60361b5900baab923357c6055e90ed2594ee`. Test fixture cleanup commit: `d390725c5d71c8398917ac53a1470ab0a71089b2`.
- These tests are committed but have not yet been executed by CI in this session. A source commit or workflow trigger is not a test pass. The full Android runtime and downstream APK gates remain outstanding.

### Next validation

1. Confirm the new workflow run executes the five library regression tests and inspect the actual job log.
2. If they pass, continue with patched Legendary worker shutdown/cancellation tests and the Android CPython emulator runtime smoke.
3. Require both x86_64 test-runtime and ARM64 production-runtime archives, hashes, JNI instrumentation, embedded companion verification, and final APK artifact upload before requesting phone-side installation.

## Engineering continuation — 2026-11-20: Epic library regression fixture correction

- Re-inspected the current branch's actual runner, regression tests, and CPython workflow. The repository checkout path in CI is `maliwinlator/`, so the workflow's runner/test paths and `PYTHONPATH` are consistent with its explicit checkout destination.
- Found and fixed a defect in the new fake Legendary core: its `get_game_and_dlc_list()` method incorrectly called `self.assertEqual()`, although the fake core is not a unittest case. It now raises a direct assertion if the runner requests a non-Windows platform.
- Fix commit: `225f114058551832def9467f55f72344cf87cb5d`.
- The test command runs before installing Legendary, but this is valid for these five focused tests: importing `epicbridge_runner` does not import Legendary; `_core()` is not called because each test supplies a fake core.
- This is a source-level fixture correction only. The tests have not yet been observed passing in GitHub Actions, and no Epic account/library/download/install/launch functionality is claimed as device-validated.

## Engineering continuation — 2026-11-20: downloader cancellation deadlock audit

- Audited the pinned Legendary 0.21.1 downloader source against the Android thread/mmap patcher. The manager's main progress loop originally remained `while processed_tasks < num_tasks`; the existing cooperative `stop()` only stopped helper threads, so a user interrupt could leave the manager loop waiting forever for work that had deliberately been cancelled.
- Added a strict patcher transformation to make that loop also test `self.running`. This lets cancellation fall through to the existing ordered shutdown path: stop helper threads, drain queued-but-not-started download work, signal downloader/writer workers, join threads, and release the manager-owned mmap.
- Commits: `cfd10042324a918c1bb918b3caf1758db282573f` (initial cancellation guard) and `17067817fd0647b45c6d0d46bd15e44b17534aaf` (corrected the patcher's newline anchor escaping).
- This closes a source-level deadlock found during review; it does not yet prove cancellation behavior under a live download. The CPython/Legendary workflow must apply the patch to the pinned source, compile it, and run worker lifecycle tests before the integration APK can be considered ready.

## Engineering continuation — 2026-11-20: host APK compile passed; runtime packaging validation underway

- The full MaliWinlator 1.2.1 standard debug APK workflow passed on source commit `17067817fd0647b45c6d0d46bd15e44b17534aaf`: run **44659494553** (run 132), conclusion **success**. This validates Java compilation, manifest merge, the Epic Bridge bootstrap companion build, embedded companion asset, and host/companion shared signing identity for the bootstrap build. It does **not** validate the runtime-enabled CPython JNI library.
- Run **44659380422** (run 51) passed the Android thread-backend patch application and host CLI smoke, then failed an Epic library regression test because the test supplied a DLC catalog without supplying its corresponding non-asset base game. The production aggregation code correctly associates DLC with the matching base-game catalog; the fixture was corrected to include that base game in commit `678a7668e4c8add0a64040aaf02799903c0fe20b`.
- Run **44659494559** (run 53) passed the earlier patcher/host smoke/regression stages and is currently compiling the x86_64 Android CPython runtime for the later instrumentation test. The POC workflow's redundant managed-emulator prerequisite step was removed; actual runtime execution is now performed by the downstream x86_64 Android instrumentation build, avoiding the previously stalled managed-emulator invocation.
- The POC workflow has a separate concurrency group for the package-only path. Older runs **44658409953** and **44659298555** remain in the legacy managed-emulator group; they are obsolete and are not the source of the current validation result.
- Additional Epic launch hardening now transfers Legendary's temporary Epic ownership-token file into the selected container's private image-root path, substitutes a Wine-visible `Z:\\home\\user-0\\tmp\\epic-secrets\\...` argument, and schedules cleanup. The companion removes its original temporary OVT file after transfer. Windows command-line arguments now use CRT-compatible backslash/quote escaping.
- Epic uninstall operations now use the same foreground data-sync service and wake-lock path as installs. The host UI separately verifies shared-storage access for both MaliWinlator and Epic Bridge.
- The default-branch integration workflow now caches the Android debug keystore for future installable updates, verifies host/companion signer equality, compares the new signer against the previous successful 1.2.1 artifact (run **44655229820**), and will publish a prerelease containing both APKs after instrumentation/build checks pass. If the signer differs from the previous APK, the prerelease notes will explicitly warn against uninstalling an existing installation before backing up its container data.
- No runtime-enabled APK has been published yet. Required remaining gates: successful dual-ABI CPython packaging and artifact upload; x86_64 Android instrumentation proving JNI load, CPython initialization, Legendary imports, JCA AES-GCM, mmap, and thread cleanup; ARM64 runtime-enabled MaliWinlator 1.2.1 APK build; signing comparison; then persistent prerelease upload.
- Device-specific Epic browser login, owned-library retrieval, a real game download/uninstall, and launch in a selected Wine container remain unverified until the runtime-enabled APK is installed and exercised on the Xiaomi 15T. Do not label the integration fully verified before those checks.

## Engineering continuation — 2026-11-20: Epic installer failure propagation

- Extended the pinned Legendary Android thread-backend patcher to prevent permanent CDN/chunk failures from being retried forever. Each failed chunk result now receives at most three manager-level attempts (in addition to the worker's bounded internal retries); exhaustion records a RuntimeError and stops the manager cooperatively.
- Changed writer failures from log-only fatal messages into an installation error stored on DLManager.error, followed by cooperative shutdown. The bridge's _install() already checks that error after joining the manager, so a failed file write is now returned to the Android UI rather than falsely reported as a successful install.
- Added the failure-propagation case to the embedded runtime self-test: it feeds a failed WriterTaskResult into fw_results_handler and requires the manager to stop and retain a RuntimeError.
- Commits: ddf760b07c239999696b28142cc428d7bf3c685a (bounded chunk retries and writer error propagation in patcher); dd5595a3a37345b29f6e159b01263474083f8d47 (runtime self-test coverage).
- The patcher and runtime test are committed on the active 1.2.1 branch. GitHub's combined status endpoint and commit-associated workflow endpoint currently return no checks/runs for these commits; no CI result is claimed. The next proof must apply the patch to the pinned Legendary commit, compile it, run the host smoke and Android instrumentation self-test, and complete the ARM64 packaging/integration workflow.

## Engineering continuation — 2026-11-20: make Android runtime prerequisite smoke an actual CI gate

- Audited the pinned CPython 3.15.2 Android build instructions against the active POC workflow. The workflow built and packaged the x86_64 test runtime, but did not actually invoke the committed third-party Android smoke script; the workflow metadata's statement that it was emulator-tested was therefore not backed by a step in the current YAML.
- Added a managed x86_64 Android emulator step after staging Legendary and its pure-Python dependencies. It now runs CPython's documented command form: Platforms/Android test --managed maxVersion --site-packages <staged site-packages> -- -m epic_runtime_smoke.
- The smoke covers 64-bit process, zlib, concurrent anonymous mmap, SQLite, CA/TLS, POSIX advisory locks, and Bionic subprocess creation. The step is required to pass before the ARM64 production runtime is built and uploaded.
- Workflow commit: 811e56367c254e6439170cf0ba1633a83b27bc64. Because the workflow file itself is in its push path filter, this commit should trigger a fresh POC run on the development branch.
- This is a CI-gate correction, not a test result. The new managed emulator step has not yet been observed passing; no runtime artifact or device-ready APK is claimed.

## Engineering continuation — 2026-11-20: pin downstream APK build to tested source SHA

- Audited the downstream workflow-run integration gate. It previously checked out the moving development branch after the CPython runtime workflow completed. If the branch advanced while the integration job was queued, the resulting APK could contain source that was not the source revision whose runtime had just passed CI.
- Changed the downstream checkout to use github.event.workflow_run.head_sha. The Android instrumentation test and ARM64 APK now build from the exact source commit that produced the CPython runtime artifact.
- Default-branch workflow commit: bc3c4dbc484e8ced1d02c52f0a5cd0f887e9b477 (on bionic-mali-1.2). This is intentionally a workflow-only update on the default branch; the app remains on development/maliwinlator-1.2.1-bootstrap.
- The workflow change is source-controlled but has not yet been exercised by a completed successful workflow_run. It does not constitute a packaged APK or a device-test invitation.

## Engineering continuation — 2026-11-20: Android ABI packaging, runtime refresh, and job lifecycle hardening

- Found a deterministic ABI packaging defect in Epic Bridge Gradle configuration: the selected CPython prefix could be x86_64 for emulator instrumentation, but every native runtime library was staged under jniLibs/${epicRuntimeAbi} as arm64-v8a. Changed the destination to the selected ABI; x86_64 instrumentation now receives x86_64 libpython/OpenSSL/SQLite libraries, while the production build still stages arm64-v8a.
- Removed the duplicate POST_NOTIFICATIONS declaration from the companion manifest.
- Found a race in EpicRuntimeService: when the last job completed, a new job could start before the main-thread completion callback ran; the old callback could then release the wake lock and remove the foreground notification while the new job was active. Added a main-thread active-job recheck and a stable foreground notification ID.
- Replaced the fixed runtime marker with a content identity generated during Gradle staging. The companion now compares the packaged runtime digest with its installed marker and refreshes stale extracted Python/Legendary/runner files on update, while keeping the user's separate Epic HOME/session directory intact. The marker is written through a synced temporary file and renamed only after extraction succeeds.
- Commits: 01c84ab6ea5c1472b7e2abb095b4e3f80adc7e2a (dynamic JNI ABI staging); aa2c6f8a225d43e17425a509af598802d90cf8e8 (manifest cleanup); e1908d740705a675e14217dfe6d492d3de045f56 (foreground service race); 0633ce2e72f73becf0093cdf40fba4c55a7e66fa (runtime asset identity generation); 02f853e9e22a3babdc57274cbce03f9e7f86dd92 (atomic content-aware extraction).
- These changes are source audits and fixes only. The Android emulator instrumentation and ARM64 APK pipeline must still prove the dynamic ABI package, native loading, runtime refresh, and service lifecycle before the build is device-testable.

## Engineering continuation — 2026-11-20: real Android JNI instrumentation and native ABI audits

- Added an actual AndroidJUnit4 instrumentation test in epicbridge/src/androidTest/java/com/maliwinlator/epicbridge/EpicRuntimeInstrumentedTest.java. It loads the packaged JNI library, initializes the embedded CPython runtime on the emulator, invokes the real Legendary runtime self-test, and asserts Legendary 0.21.1, Android JCA AES-GCM, JCA calls from a Python worker thread, thread-backed downloader/writer workers, and anonymous mmap.
- Expanded the CPython workflow path filter to trigger validation for any Epic Bridge source/build/test changes and the host Epic integration entry points. This ensures the next runtime artifact and downstream APK build use the same source SHA as the code under test.
- Expanded the downstream integration workflow to inspect the x86_64 companion APK after emulator instrumentation and the arm64-v8a companion APK after production build. It now requires the ABI-correct JNI library, libpython3.15.so, Epic runner module, and generated runtime identity file in each package.
- Disabled CPython signal-handler installation in JNI initialization because initialization may occur on an Android Binder or executor thread rather than the process main thread.
- Commits: ea234c0d2616469cf60f95d799f3a475365b7bae (Android instrumentation test); e18f24a9036b6f96d4866b9ab77c07d1821ad660 (source-trigger coverage); a114f91deac6a138da81af01c0270d6ee0ac03e5 (dual-ABI APK audits); 2919a5f11f1660be677b60a9c1fba150556d93b2 (JNI CPython signal setup).
- The latest development-branch commit e18f24a9036b6f96d4866b9ab77c07d1821ad660 changes the watched POC workflow paths and should trigger the next full runtime workflow. No successful run, runtime artifact, or APK has yet been observed for this exact source revision.

## Engineering continuation — 2026-11-20: final runtime recovery and mmap lifetime guards

- Tightened content-aware runtime extraction so a matching marker is not treated as sufficient when required extracted files are missing. The companion now verifies the CPython stdlib entry point and Epic runner before skipping extraction, allowing recovery from partial external deletion.
- Hardened the downloader's unexpected-exception cleanup: after cooperative worker joins, the manager now leaves the anonymous mmap open if any worker thread is still alive. This avoids invalidating a buffer still in use by a worker; the mapping remains referenced until those threads release it.
- Commits: f70e4bca9757ff68f0a2ec7ee312d49f47672a8a (runtime file-presence recovery); 32c87a7a6a0efe903da133b7d2c27c53086d7b56 (mmap lifetime guard).
- Both commits touch paths covered by the expanded Epic POC trigger, so the runtime/integration pipeline should validate the latest branch source SHA. No passing run has yet been observed.

## Engineering continuation — 2026-11-20: prevent stale ABI outputs between emulator and production builds

- Found a second-order Gradle issue after correcting the jniLibs destination: the integration workflow builds x86_64 instrumentation first and arm64-v8a production APK second in the same workspace. Without explicit task inputs, the Sync task could be considered up-to-date across an ABI property change and leave the wrong ABI's runtime libraries staged.
- Added epicRuntimeAbi and epicRuntimePrefix as explicit inputs to stageEpicPythonRuntime. Switching from the x86_64 test prefix to the ARM64 production prefix now invalidates staging and regenerates the generated assets/JNI library tree before the production APK is assembled.
- Commit: b9c3b397766b22b213325c7b4af48c356d92d429.
- This is a build-graph correctness fix; the two-ABI sequential Gradle workflow must still demonstrate the expected x86_64 and arm64-v8a contents in its artifact audits.

## Engineering continuation — 2026-11-20: Android runtime extraction and crypto negative-test coverage

- Hardened recursive AssetManager extraction: a missing file can no longer be mistaken for a directory after FileNotFoundException. A child is recursed only when AssetManager reports nested entries; otherwise extraction fails and the content marker is not committed.
- Extended the real Android instrumentation test to run the Java AES-GCM known-answer test including deliberate tag corruption, proving authentication failure is rejected as well as valid plaintext being recovered.
- Commits: 6aa922760df9ce560f3d5f6ce8d686bc23106539 (strict missing-asset handling); 649eb85de57b2fd322e531214dbbefc04b3ce382 (JCA positive/negative test).
- Both are under epicbridge/**, so the active POC workflow's source path filter should start validation from the updated branch SHA. The Android test and final ARM64 artifact audit are still pending observed CI success.

## Engineering continuation — 2026-11-20: require complete packaged runtime identity before advertising readiness

- Tightened EpicPythonRuntime.isPackaged() to require the generated .runtime-version asset in addition to the native library, CPython standard-library entry point, Legendary package, and bridge runner. The status endpoint can no longer report a runtime-ready package when the build-time content identity is absent.
- The extraction preflight also verifies the required Legendary crypto shim, requests, certifi CA bundle, and filelock files before reusing an existing extracted tree; a stale or incomplete tree is refreshed.
- Commit: cbcb4f3110f688036c2afb7998f560ace4a0c892.
- This final packaging/readiness check is part of the next Android instrumentation build. No runtime-ready result is claimed until that test passes on the managed x86_64 emulator.

## Engineering continuation — 2026-11-20: serialize JNI entry into the embedded interpreter

- Serialized EpicPythonRuntime.call() at the Java boundary. Provider requests and foreground install jobs can otherwise enter the same persistent CPython interpreter concurrently while downloader threads release the GIL for network I/O; the Java monitor now ensures one top-level bridge command at a time while Python's own worker threads continue normally inside an install.
- The synchronized call safely re-enters synchronized initialize() on the same Java monitor and does not affect JNI AES-GCM callbacks.
- Commit: 04b4ce63ddaa31d2851b77a1dc54f76a34865d5f.
- This source change triggers the Epic POC/integration validation pipeline; the Android runtime instrumentation still needs to pass.

## Engineering continuation — 2026-11-20: deterministic pure-Python staging

- Audited the two Android runtime prefix staging commands and found that pip's default bytecode compilation could create __pycache__ directories inside site-packages, conflicting with the ARM64 runtime packaging audit and wasting space in the embedded runtime.
- Added pip's --no-compile option to both x86_64 and ARM64 Legendary target installs. The runtime packages remain source-only and the existing no-__pycache__ audit is now deterministic.
- Commit: 9e5db51998bf5ab72a4a7c0482009d26cfa1cac7.
- This updates the watched CPython workflow, so the next POC run will include the corrected staging behavior.

## Engineering continuation — 2026-11-20: increment host package version for the Epic build

- Bumped the standard host APK versionCode from 23 to 24 and labeled the versionName "1.2.1 MaliWinlator Epic". This prevents Android from treating the Epic-enabled build as an equal-version reinstall when updating the previous 1.2.1 development APK.
- Commit: 3f0d4682e78422c398be857884b88d9a32cb60c6.
- The integration workflow still compares APK signing certificates before publication; versionCode advancement does not bypass Android's signature requirement.

## Engineering continuation — 2026-11-20: prevent worker-thread logging races

- Found a thread-backend regression in Legendary's original worker startup: both DLWorker and FileWorker cleared and replaced the process-wide root logger handlers, behavior that was safe only when workers were isolated processes. Under the Android thread backend, the two workers could race and overwrite application/manager logging configuration.
- Updated the patcher to remove per-worker root-handler reconfiguration and the now-unused QueueHandler import from workers.py. Thread workers inherit the manager/application root logger; the manager remains the only owner of any queue-based logging setup.
- Commit: 3e1f67185fc3c16e8fc0468c02defcef8c766ee5.
- This fix is included in the next Epic runtime POC run and Android worker lifecycle test.

## Engineering continuation — 2026-11-20: test worker logging isolation

- Extended the embedded runtime self-test to install a sentinel root logger handler, start and stop both Legendary worker threads, and assert that the global root handler list is unchanged. The sentinel handler is removed in a finally block even if the worker smoke fails.
- Commits: 511102a8bb878bfd40e1d32539ef89b6fd6a3736 (logging preservation assertion); 202a04ed50065faa0dd8af9544c861daa3aebb02 (guaranteed test cleanup).
- The latest changes touch epicbridge/** and therefore trigger the full CPython/Legendary POC workflow, followed by the SHA-pinned Android instrumentation and ARM64 APK workflow if the POC passes.

## Engineering continuation — 2026-11-20: no-op update handling

- Audited the install path for games already at the current manifest. Legendary can produce an empty task deque for a no-op update; its original result-handler startup would pop from that empty deque and fail before recognizing there was no work.
- The Android patcher now returns from run_real() before allocating mmap or starting worker threads when there are no file tasks. Added an embedded runtime self-test that runs an empty AnalysisResult through the manager thread and requires clean completion.
- Commits: 40cf8c650a5982c54b705dadfbf920f3cc0681d0 (no-op guard); 1d3f42355285844b222519ed3892b9bccec0ae30 (no-op runtime test).
- This source change is included in the next CPython/Legendary POC and downstream Android instrumentation build.

## Engineering continuation — 2026-11-20: zero-chunk install regression

- Found another valid Legendary manifest shape: an install/update may contain file operations but no chunk tasks (for example, empty files or deletion-only work). The original progress calculation divided by num_chunk_tasks unconditionally and could fail with ZeroDivisionError.
- The Android patcher now reports 100% chunk progress when the chunk count is zero while continuing to count file tasks normally.
- Added an end-to-end runtime self-test that runs a real FileWorker-backed, file-only DLManager job with a 4 KiB anonymous mmap and asserts that the empty output file is created successfully.
- Commits: bb9d05628bee554956bfe35be7f7881863e4c691 (zero-chunk progress guard); deae043c807b2466746d2abfd89f9ca340bc0552 (file-only manager runtime test).
- This test will execute as part of the Android JNI instrumentation test after the pinned Legendary patch is applied.

## Engineering continuation — 2026-11-20: use a portable runtime identity asset name

- Renamed the generated packaged runtime identity from a dot-prefixed asset to runtime-version.txt. This avoids relying on hidden-file handling in Android asset packaging while preserving the same SHA-256 content identity and extraction behavior.
- Updated EpicPythonRuntime readiness/extraction checks and the downstream x86_64/ARM64 APK asset audits to require the new filename.
- Commits: c1de59b253ebaf8569f5b428859a1e6e8ef6b880 (Gradle generation); bbeba780b1e65df77fdc0a8bce664e997abef691 (Java runtime); 0d65cbed5ea6625b7508071b752d4cd267f11afa (downstream artifact audit).
- This change is covered by the Epic source path filter and must be included in the next runtime/instrumentation run.

## Engineering continuation — 2026-11-20: fail closed on Python environment setup

- Changed EpicPythonRuntime.initialize() to abort when Android cannot set HOME or TMPDIR, instead of logging a warning and continuing with a potentially invalid Legendary configuration path. This prevents silent writes to an inaccessible default home directory and ensures the companion never reports a usable interpreter with misdirected account/session storage.
- Commit: cb4b2b5a651df36177485aee554a8b34cbf89b98.
- The updated initialization path will be exercised by the managed x86_64 Android instrumentation test.

## Engineering continuation — 2026-11-20: restore the missing managed emulator gate after Legendary staging

- Re-audited the current POC YAML rather than relying on the earlier handoff note and found that the managed x86_64 smoke step was absent from the live workflow file. The previous insertion had not survived subsequent workflow edits, so the metadata line claiming emulator validation was not backed by an executing step.
- Reinserted the test at the correct point: after the x86_64 runtime has Legendary and all pinned pure-Python dependencies staged, and before the ARM64 runtime is built. It copies epic_runtime_smoke.py into that staged site-packages tree and runs the upstream documented Platforms/Android test --managed maxVersion command.
- Corrected runtime-build metadata to describe the actual managed-emulator command.
- Commits: c7a02bef54249bce2a9448ef66fb5cf5da03ad6f (real smoke step); c08687643953043a0c432db3305b0430af67ab87 (metadata correction).
- The POC workflow itself is now the source of truth; the emulator step must visibly pass before the downstream Android JNI test and ARM64 APK build can proceed.

## Engineering continuation — 2026-11-20: stabilize the real x86_64 prerequisite smoke

- Revalidated the live POC workflow after subsequent edits and restored the managed Android test step after Legendary/dependency staging, not immediately after the bare CPython prefix build. The test therefore exercises the exact x86_64 prefix that is later packaged for Android instrumentation.
- Switched the network/TLS probe from the Legendary project website to stable https://example.com so the smoke validates DNS, CA trust, and TLS without depending on a third-party project's site uptime.
- Corrected the smoke script's invocation documentation to require the staged prefix site-packages directory, matching the workflow's actual copy-and-run sequence.
- Commits: c7a02bef54249bce2a9448ef66fb5cf5da03ad6f (managed emulator gate restored at correct stage); c08687643953043a0c432db3305b0430af67ab87 (build metadata); 4f9c61943551d6bf62123742dc055ea2c2771e40 (stable TLS endpoint); 2b95880eb5310ffb3550d3932f67adede4eb52f9 (invocation documentation).
- This is the latest POC workflow configuration. Its managed emulator run must complete successfully before any APK build or device-test handoff.

## Engineering continuation — 2026-11-20: connect the CPython POC to actual installable APK outputs

- Audited the active branch's real workflow rather than relying on the earlier handoff's description of a downstream integration workflow. The active branch did not contain `.github/workflows/epic-integration-build.yml`; the CPython workflow stopped after uploading runtime tarballs. Consequently, the existing pipeline could never produce the requested host/companion APK pair.
- Extended the active `.github/workflows/epic-python-runtime-poc.yml` to install the app's pinned Android SDK/NDK/CMake toolchain, run `:epicbridge:connectedDebugAndroidTest` against the separately built x86_64 CPython prefix, then switch the Gradle runtime ABI/prefix inputs and assemble the ARM64 `:app:assembleStandardDebug` target. The host build embeds the separately built companion APK.
- Added a dedicated 30-day artifact, `maliwinlator-1.2.1-epic-arm64-debug`, containing the host APK, companion APK, and SHA-256 manifest. The CPython runtime tarball artifact remains available separately for audit/recovery.
- Commit: 886daa7597e71b4abf676bdf1d9a5536d54b8822.
- This fixes a pipeline completeness defect; it is not evidence that the workflow has executed. A successful managed x86_64 instrumentation job, successful ARM64 Gradle build, artifact upload, and APK content/signature audit are still required before device handoff.

## Engineering continuation — 2026-11-20: fix the companion's host-package authorization mismatch

- A source audit of the provider boundary found a runtime-blocking identity mismatch: the companion authorized `com.winlator.cmod` (the Java namespace/upstream application ID), but the standard MaliWinlator flavor is installed as `com.maliwinlator.app`. The host had already declared the signature permission and companion package visibility, but provider-side UID package validation would reject the real host.
- Updated `EpicBridgeProvider.HOST_PACKAGE` to the actual standard flavor application ID and added an Android instrumentation regression test that accepts `com.maliwinlator.app` while rejecting the old namespace and an unrelated package.
- Commits: 03c3e416dbd013facacf501ba86c5295b086860a (authorization fix); dd3a84a9fd6f6047f6bab54579ac21b3a074e80f (instrumentation regression test).
- This is a source-level fix, not a tested result. The updated managed x86_64 instrumentation job must pass before the APKs are considered usable.

## Engineering continuation — 2026-11-20: CI toolchain license-pipe robustness

- Hardened the Android SDK license-acceptance step in the newly connected APK pipeline. With Bash `pipefail` enabled, the `yes | sdkmanager --licenses` pattern can report failure when `yes` receives SIGPIPE after sdkmanager has consumed its answers. The workflow now disables pipefail only around that pipeline and restores it before SDK package installation, preserving strict error handling for the actual build.
- Commit: 52163f8796b1f29e7e5e6e32fa5727584f9768f9.
- Static workflow review only; the managed emulator and APK build have not yet been observed passing.

## Engineering continuation — 2026-11-20: provision a real emulator for companion instrumentation

- Static review of the newly added `:epicbridge:connectedDebugAndroidTest` step found it had no connected Android device. The CPython prerequisite test uses CPython's own managed testbed emulator, but that emulator is shut down by the test command and is not available to Gradle's later connected-test task.
- Replaced the bare Gradle step with `reactivecircus/android-emulator-runner@v2`, explicitly provisioning an API 35 x86_64 Google APIs Pixel 6 emulator before running the companion instrumentation suite against the x86_64 CPython prefix.
- Commit: 3fc6a38d3920c029ee1e7f420eeb0f6fc0efa4f9.
- The emulator action and Gradle instrumentation remain unexecuted in observed evidence; the next workflow run must validate the action inputs and test results.

## Engineering continuation — 2026-11-20: make the manager exception-cleanup probe reach the intended path

- Auditing the runtime self-test against the pinned Legendary 0.21.1 source showed that `DLManager.run()` rejects a missing analysis result before entering its guarded `run_real()` call. The intentional exception probe had not set `analysis`, so it would test an uncaught precondition error rather than the Android thread-backend cleanup handler.
- Initialized the probe with an empty `AnalysisResult` before replacing `run_real()`; the test now exercises the intended exception capture, cooperative stop, and manager error slot.
- Commit: 76dd23eb2039516d555bf4fdda7850195c725f50.
- This is a test correctness fix; Android instrumentation remains pending.

## Engineering continuation — 2026-11-20: verify both APKs are installable as a matched pair

- Expanded the final CI audit after ARM64 Gradle assembly. It now verifies both APK signatures, asserts the expected package IDs (`com.maliwinlator.app` and `com.maliwinlator.epicbridge`), compares their signer SHA-256 fingerprints, checks the companion contains ARM64 `libepic_python.so` and `libpython3.15.so` plus the required runtime identity/stdlib/Legendary assets, and checks the host embeds the exact same companion APK bytes that are published separately.
- The workflow writes a SHA-256 manifest for the two APKs and publishes them together as `maliwinlator-1.2.1-epic-arm64-debug`.
- Commit: 4586dba1a5b4cba7209ffc2b8e13e759717234aa.
- These are CI assertions, not observed results. The artifact is not ready until the complete workflow passes and the APKs are actually available.

## Engineering continuation — 2026-11-20: correct cross-package game storage and Wine C: mapping

- Audited `ImageFs.find(context)` and `ContainerManager`: container prefixes live under the host app's private `files/imagefs/home/user-<id>` tree. The Epic Bridge is a separate Android package/UID and cannot write directly into that private directory, even though the host app can.
- Reworked Epic install storage to a per-container shared external directory, `/storage/emulated/0/MaliWinlator/EpicGames/container-<id>`, which both packages can access after the two explicit all-files-access grants.
- The host now creates/validates a symlink at the selected container's `.wine/drive_c/Games/Epic` pointing to that shared directory. This makes the same files available to Wine as `C:\\Games\\Epic` without exposing the host's private prefix to the companion.
- Installed-game metadata is mapped back to its owning container using the per-container shared directory. Update and launch automatically select that container; launch rejects a game directory outside its selected container's shared tree and converts its working directory to the corresponding Wine C: path.
- Commits: b58bc1b8184a7f997f28d928fbfaa02e900d235e (initial container ownership/path validation); 0b8eb2441f1541812e480f779f878c7b7c3ac914 (launch path guard); 8526603928a0e21f79c8fb8cc347da607c6f8ad9 (shared storage + Wine C: symlink mapping).
- Source-level architecture correction only. Symlink behavior, cross-package shared-storage access, install, and game launch still require emulator/device validation.

## Engineering continuation — 2026-11-20: preserve the actual Epic executable working directory

- Checked the launch contract against Legendary 0.21.1 `get_launch_parameters()`: `game_directory` is the install root, while `working_directory` is the executable's parent directory. The first C: mapping implementation used the install root for both.
- Updated the host to validate both paths against the same container-owned shared directory and translate Legendary's actual `working_directory` into the matching `C:\\Games\\Epic\\...` path. The executable itself remains checked using its real shared-storage path before the Winlator launch begins.
- Commit: f71f066f4641c5049a1f348a8f13c3f3efe9b2b5.
- Static contract correction only; game launch still needs runtime verification.

## Engineering continuation — 2026-11-20: make extracted CPython extension modules loadable

- Reviewed the CPython asset extraction path against the native extension loading model. The standard library's `lib-dynload/*.so` modules are copied out of APK assets into app-private files storage; a plain `FileOutputStream` does not preserve their executable mode.
- Updated extraction to mark shared-object extension files executable for the companion app's own UID and fail extraction if that permission cannot be set. The native CPython/libpython dependencies packaged under `jniLibs` remain handled by Android's native-library installer.
- Commit: b0709de06f88e1f4c4824e3c9ccd87008e39d56b.
- This is a loader-readiness hardening change; the Android runtime instrumentation must still prove that CPython imports its native modules successfully.

## Engineering continuation — 2026-11-20: recover previously extracted runtimes with bad extension permissions

- Closed the stale-extraction case introduced by the native-module permission fix: a matching content marker alone would otherwise allow an older extracted tree with non-executable `lib-dynload/*.so` files to be reused.
- Runtime preflight now verifies that the lib-dynload directory exists and every shared-object extension is executable. If any module fails that check, the companion refreshes the extracted tree and applies the corrected file mode.
- Commit: ce3e2197175203b881f42f90fcd03c049993e0a0.
- The check is source-reviewed but not yet exercised on Android.

## Engineering continuation — 2026-11-20: version the installable runtime companion

- Incremented the Epic Bridge companion package from versionCode 1 / versionName 0.1.0 to versionCode 2 / 0.2.0. This ensures a runtime-enabled companion can replace an earlier bootstrap/P0C companion as a normal Android package update, provided the signing certificate matches.
- Commit: 6d43399a252d3be1f42c30da69f50705cd4490e8.
- The host APK remains MaliWinlator 1.2.1 Epic, versionCode 24. The final CI audit verifies both package identities and signer equality.

## Engineering continuation — 2026-11-20: clarify container ownership after Epic install/update

- The Epic library success message now names the exact Winlator container that owns the shared game directory and instructs the user to select that container for launch. The library also auto-selects the owning container on launch/update based on Legendary's recorded install path.
- Commit: 9c97f674672e9282c58c6f2db0a2f0baade4275d.

## Engineering continuation — 2026-11-20: preserve pre-existing Wine path data during Epic link setup

- Hardened the per-container Wine C: symlink setup: if `.wine/drive_c/Games/Epic` is occupied by a regular file, setup now fails safely instead of deleting it. An existing real directory is replaced only when it is empty; non-empty directories and symlinks to other targets are preserved and reported.
- Commit: 87769948f07afc738de5f0a27837500171123f4a.

## Engineering continuation — 2026-11-20: assert downloader exception cleanup in Android instrumentation

- The embedded Python runtime self-test already reports a dedicated `download_failure_cleanup` result after forcing a manager exception and verifying the thread backend captures it and releases resources. Added an explicit Android instrumentation assertion for that result so this regression cannot silently remain untested.
- Commit: 855a47c393dd9e31068340d4fc1dcf03e2323525.

## Engineering continuation — 2026-11-20: fix JNI local-reference cleanup on Python worker threads

- Audited the native AES-GCM callback path used by Legendary's downloader threads and found that the success path detached a Python worker from the JVM before deleting its JNI local references. A detached thread's `JNIEnv*` is no longer valid, making the subsequent `DeleteLocalRef` calls unsafe and potentially fatal during chunk decryption.
- Reordered cleanup so all local references are deleted while the thread is still attached; only then is a thread attached by the callback detached. Binder/main Java threads remain attached by their original caller and are not detached.
- Commit: 4ce0cbb745e8c376fc1e13be35fc8b890414cca2.
- The dedicated worker-thread AES-GCM Android instrumentation assertion exercises this path; it must pass before runtime readiness can be claimed.

## Engineering continuation — 2026-11-20: audit out the native PyCryptodome dependency

- Verified the pinned Legendary 0.21.1 project metadata declares `pycryptodomex`, while the Android companion intentionally does not bundle its native extension: the two runtime AES-GCM imports in `models/chunk.py` and `models/manifest.py` are rewritten to the Android JCA shim.
- Added a workflow assertion that no Python source under the installed Legendary package still imports or references `Cryptodome`. This prevents a future upstream change from silently reintroducing an unavailable native crypto dependency into the Bionic runtime.
- Commit: ef6fca48413afef33ef006f0775ff5a74079f941.

## Engineering continuation — 2026-11-20: validate the Wine C: storage link before launching

- Added a launch-time preflight that revalidates or restores the selected container's `.wine/drive_c/Games/Epic` symlink before obtaining launch parameters. If the mapping was removed or redirected, the user receives an explicit error instead of a Wine launch against an inaccessible path.
- Commit: cf9fdba10bceb5a041895885cd7b70083dd207db.

## Engineering continuation — 2026-11-20: constrain remote Epic executable paths

- Added a canonical-path containment check after resolving Legendary's executable relative to its install directory. Launch now rejects metadata that resolves the executable outside the selected game's own install tree, including traversal or escaping symlink paths.
- Commit: 70115fb868deb384667618a0c4586e06c4c115b4.

## Engineering continuation — 2026-11-20: persist Epic job tracking across fragment/activity recreation

- Corrected the earlier UI promise that users could reopen the Epic tab to check a long-running install. The host now persists the active companion job ID in app-private SharedPreferences, polls the companion's durable job state, clears the ID only after a terminal result, and resumes polling when the Epic fragment is recreated or the app returns to the tab.
- The UI prevents a second install/uninstall from replacing the tracked active job ID. If the companion is absent, recovery waits until it is reinstalled rather than spinning on an unavailable provider.
- Commits: 4f3469b605f5078179c8f26d5d41d12a92f86a3b (persistent job state/recovery); 0a78c94c9824c6895cdc672718c6e246bcfbdb90 (companion availability guard).
- This improves long-running operation recovery; actual process-death recovery and service behavior still require Android validation.

## Engineering continuation — 2026-11-20: detect interrupted foreground installs after Android kills the service

- Added an in-process active-job registry to EpicRuntimeService. The provider now compares persisted `running` state with that registry and marks a job `interrupted` if the service process has disappeared, with a 30-second startup grace to avoid racing the initial `startForegroundService` launch.
- The host recognizes `interrupted` as a terminal state, clears its persisted tracking key, and tells the user to retry instead of polling a dead job forever. This does not attempt to resume a partially downloaded job itself; Legendary's existing resume metadata remains the recovery mechanism for a retry.
- Commits: 9955cdb568d8e9ffe23890f72eb17d0ffa12fb13 (service active-job registry); 0092cd0323f077e26a3b406232896031533f3cc2 (provider interruption detection); 61710bc44476985de62e872ece418b99e4000012 and 0ad3ef285eceaace62311c05b28c24d1c6d012eb (host retry handling); 70d941e1d863535bb280816ce49b544978dc4840 (startup grace).

## Engineering continuation — 2026-11-20: persist interruption state on graceful service destruction

- Added an `onDestroy()` safeguard that marks any still-running persisted Epic jobs as interrupted before shutting down the service executor and releasing its wake lock. Normal completion removes job IDs first, so completed jobs are not overwritten.
- Commit: 6be23b842150c1f2932104a1d60162c6988736d1.

## Engineering continuation — 2026-11-20: expire orphaned host-side Epic job IDs

- Added a 30-second unknown-state grace to the host job tracker. If the companion no longer has a record for a persisted job ID (for example, after the companion was reinstalled or the service failed before recording its job), the host now clears the orphaned ID and reports that the operation may not have started instead of polling indefinitely.
- Commit: 72d760aabd76a88ba05a956bf30b57ce8a577c13.

## Engineering continuation — 2026-11-20: preserve Epic prerequisite and compatibility metadata

- The install runner previously discarded the dictionary returned by Legendary's `core.install_game()`, which can identify a game's prerequisite installer. It now returns that metadata alongside install path/version and the existing anti-cheat/offline warnings.
- The Epic UI now surfaces prerequisite requirements and compatibility warnings instead of silently implying every installed title is immediately launch-ready. It explicitly notes that prerequisite installers are not yet launched automatically.
- Commits: 9a737c11a931ec0c60c5d5da4e939a947e8e60f2 (runner result); 69e0a5852c89933a7e069ef7958580492a0de70c (UI warnings).

## Signing compatibility correction — 2026-11-20

- The earlier versionCode note correctly prevents Android's version-code downgrade check, but it does **not** guarantee in-place updates from a prior GitHub Actions debug APK. Both host and companion currently use the runner-generated Android debug keystore; GitHub-hosted runners are ephemeral, so a later workflow run may sign with a different debug certificate.
- The final same-run audit proves only that the host and embedded/separate companion APKs share the same certificate. It does not prove continuity with a previous run.
- No stable private signing key is present in the repository, and a signing key must not be committed to this public source tree. Until a user-managed release keystore is configured securely, a debug APK signed by a different run may require uninstalling the previous debug package before installation. The first Epic artifact remains installable as a clean install.

## Engineering continuation — 2026-11-20: reject stale x86 libraries in the production companion APK

- Extended the final APK content audit to fail if the ARM64 companion APK accidentally retains x86, x86_64, or armeabi-v7a JNI libraries after the sequential emulator-test/production-ABI Gradle builds. The expected ARM64 JNI libraries remain explicitly required.
- Commit: 216be82e580811f02d832770019a5105b2b9ef06.

## Engineering continuation — 2026-11-20: retain one-shot Epic launch credentials through slow prefix setup

- Found a launch-time expiry race: the host previously passed only a payload token into `GuestProgramLauncherComponent`, which retrieved it after Wine prefix preparation. A first-run prefix update could exceed the payload store's five-minute TTL, dropping Epic's one-shot environment values before the game process started.
- `XServerDisplayActivity` now consumes the payload immediately and passes the in-memory payload object directly to the guest launcher. The launcher applies its environment only after prefix setup and then clears its reference. The command line and Wine working directory continue to be copied into the transient shortcut object, and secure launch logging remains enabled.
- Commits: 97bf155443009facbd68ff5cf7d5c07df977381e (activity consumes/transfers payload); bb4f2951cd53aa51304ce875d4796758f2107b98 (guest launcher retains payload through prefix update).
- Static launch-flow fix; actual first-run launch still needs a device test.

## Engineering continuation — 2026-11-20: align ownership-token file lifetime with prefix startup

- Extended the one-shot Epic ownership-token file cleanup delay from 15 to 30 minutes to match the stale-file sweep window and allow a slow first Wine prefix preparation to complete before the game reads its token. Files remain private to the host app UID and are removed by the delayed cleanup or the next launch's stale-file sweep.
- Commit: 1ee412644c1c423c246b558d6cb0d53890d20dbf.

## Engineering continuation — 2026-11-20: make CI outcome and artifact URL discoverable without a run link

- Added a best-effort end-of-workflow status publisher. After both APK and CPython runtime artifacts upload, the workflow writes `PROJECT_EPIC_CI_STATUS.md` to the development branch with the exact source SHA, run URL, result, run attempt, artifact URL, package IDs, and verification scope. A failure path writes a clear "do not install" report with the failed run URL.
- The status commit uses the workflow's scoped `contents: write` token and `[skip ci]`; status publication is best-effort and cannot turn a successful build into a failed build if branch writes are blocked. This removes the need for the user to locate or send an Actions run URL manually.
- Commits: eae82aeccff46b77f6d48d1bfac0f3ad7862ce0e (status report publisher); 1db92c18ab8b11b59125f85339d3caa28177036e (publish after all artifacts); 3ce5b92292a4ec533817ccf1735e5733852c0985 and d7a7371de5b492c86ff73c40a1ad3d2b454ffb41 (best-effort report handling).
- The publisher itself is not evidence of a passing build. Its report must be read after the workflow runs.

## Engineering continuation — 2026-11-20: bind CI verification to the exact triggering commit

- Changed the workflow checkout from the moving branch ref to `github.sha`. Each run now builds and tests the exact commit that triggered it, and the generated CI status record identifies that same source SHA. The final status publisher separately fetches the current branch head before committing its report.
- Commit: e60a60d5af11b7241486268a577ad4c92c7708a0.

## Engineering continuation — 2026-11-20: include APK checksums in the branch-published CI record

- Extended the successful CI status record to include the SHA-256 digest of each published APK, so the final install pair can be checked against the exact artifact bytes without extracting the workflow artifact first.
- Commit: b495bcef96cfdda6199ba372721fab84af6c2176.

## Engineering continuation — 2026-11-20: enforce companion all-files permission at the IPC boundary

- Added a provider-side permission gate for install/uninstall job creation on Android 11+. The companion now rejects these operations with a specific `STORAGE_PERMISSION_REQUIRED` response unless its own package has all-files access, rather than relying only on the host UI to prevent an eventual filesystem failure.
- Commit: 14532f916aaedc4e5118a6fde4eb477bdd355ebc.

## Engineering continuation — 2026-11-20: normalize invalid background-job responses

- Hardened the companion service result persistence: a null, empty, or malformed CPython/JNI response is now converted into a structured failed-job JSON result instead of storing an unreadable payload that would strand the host's status tracker.
- Commit: 3d192562e448815f8574cf93c4ca1e7beb447935.

## Engineering continuation — 2026-11-20: close the synchronous install IPC permission bypass

- Applied the same Android 11+ all-files-access gate to the legacy synchronous `install`/`uninstall` provider methods as to foreground job creation. Both public IPC routes now fail closed if the companion lacks storage permission.
- Commit: 001733aca7333d1cac4b92a1c5881d151a3c986a.

## Engineering continuation — 2026-11-20: allow concurrent AES-GCM work across Legendary download threads

- The JNI AES-GCM bridge previously held the CPython GIL while Android JCA performed chunk authentication/decryption. This serialized CPU-heavy decryptions despite the Android thread backend.
- Wrapped the Java JCA call in `Py_BEGIN_ALLOW_THREADS` / `Py_END_ALLOW_THREADS`, allowing other Python download/writer threads to progress while a chunk is being authenticated. The JNI local references remain thread-local and are cleaned up before any attached worker detaches.
- Commit: 921bb0647843c0b5d8d21ac4c73abfb417ab3c9a.
- Performance gain is expected but unmeasured; the Android worker-thread crypto test must validate safety before benchmarking.

## Engineering continuation — 2026-11-20: protect mmap lifetime on normal downloader shutdown too

- Extended the lingering-thread mmap guard from the exception path to normal/cancelled `run_real()` cleanup. The manager now closes/unlinks the anonymous mapping only after every worker and helper thread has exited; if any remain alive after their bounded joins, it leaves the mapping referenced rather than invalidating memory still in use.
- Commit: e58651d3324e66cb3792c3d446e60a109c622071.

## Engineering continuation — 2026-11-20: prevent a dead Legendary writer from hanging the result handler

- Added a watchdog in the patched writer-result handler. If its queue remains empty after the FileWorker thread has exited without sending its normal termination marker, the handler now records a manager error, requests cooperative shutdown, and exits instead of waiting forever.
- The normal mmap lifetime guard then keeps the buffer open if any other worker/helper remains alive.
- Commit: 3bd2c2e3b4b95c621da54cfb9678f638a0ce1311.

## Engineering continuation — 2026-11-20: detect downloader threads that die without returning a chunk result

- Added a corresponding watchdog to Legendary's download-result handler. If all DLWorker threads have exited unexpectedly while the manager is still running and an in-flight result is awaited, the manager records a hard error and stops cooperatively rather than waiting forever on an empty result queue.
- Normal cancellation is excluded from this error path; it is already signaled by `self.running = False`.
- Commit: aa191ecfadc23d264fac32380c93cf3fe1bc0dfb.

## Engineering continuation — 2026-11-20: fail fast if the Epic file-writer thread exits with pending work

- Added a main progress-loop watchdog for the final FileWorker thread. If the writer exits while Legendary still has unprocessed file tasks, the manager records an error and stops rather than waiting indefinitely for writer-result notifications that can never arrive.
- Commit: 7e7653580613964b455883ec5eb06816403116cb.

## Engineering continuation — 2026-11-20: detect premature writer-result handler exit

- Extended the main progress-loop watchdog to monitor the third helper thread (`fw_results_handler`) as well as the FileWorker itself. That handler should remain alive until the manager queues the writer termination marker after all file tasks finish; an early exit now produces a clear manager error instead of an unbounded wait.
- Commit: 0bb099742d72e84ba1bffb58d6c958a03ac97956.

## Engineering continuation — 2026-11-20: exercise LegendaryCore during runtime instrumentation

- Extended the embedded Android runtime self-test to import and instantiate the actual pinned `LegendaryCore` class through the same `_core()` singleton used by login/library/install operations, then read its installed-game metadata. This catches missing runtime dependencies or import regressions that a downloader-only smoke would miss.
- Commit: 939e16efbc14ccec035ad1fb3b6d244c6d140756.

## Engineering continuation — 2026-11-20: audit ARM64 ELF dependency closure inside the final APK

- Added a final-package ELF audit using the NDK's `llvm-readelf`. It extracts every ARM64 JNI library and CPython `lib-dynload/*.so` module from the companion APK, reads each `DT_NEEDED` entry, and requires the dependency to exist either in the APK's ARM64 native-library directory or the Android API 35 NDK system-library stubs.
- This catches missing OpenSSL, libffi, sqlite, zlib, libpython, or other shared-library dependencies before the artifact is handed to the device.
- Commits: 15509326e96d1e166514ca6a41709bd766b303ad (ELF closure audit); 76322d2d51a5f211a3e47208f19fb25e41ba5968 (corrected readelf dependency parser).

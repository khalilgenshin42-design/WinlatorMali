# MaliWinlator — Project Continuity Handoff

Last updated: 2026-11-25
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
- Last validated application source commit: `3390b95a717121d41bc79903c524120d75c3717d`. Latest handoff/documentation commit before this update: `abb0b00eba5ebabe648da15af6894fc10f5e4db7`.
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

## Engineering continuation — 2026-11-20: test corrupted-tag rejection through the actual JNI path

- Extended the CPython runtime self-test to flip one bit of the NIST AES-GCM authentication tag and require the native _epiccrypto → JNI → Android JCA path to raise a Python RuntimeError. Added an explicit Android instrumentation assertion for this result.
- The existing Java known-answer test already verifies JCA tag rejection directly; this new case verifies exception propagation through the C/JNI bridge used by Legendary.
- Commits: fe6dbfc1625c74cbc459e8ebc68b1011ca99df3f (runtime negative test); f0c12e728a5bf88edff5b8cb1cbaf1f80fc89601 (instrumentation assertion).

## Engineering continuation — 2026-11-20: report ELF dependency closure in the CI evidence summary

- Updated the generated CI status report's verification scope to explicitly include the final ARM64 ELF dependency-closure audit, alongside emulator tests, JNI instrumentation, APK signing, package identity, ABI, assets, and embedded companion byte-for-byte comparison.
- Commit: c208c3f908854bedfc0f12f4f41540901e80b0bc.

## Engineering continuation — 2026-11-20: isolate CI status publication from the APK build result

- Marked the successful-build status publisher as `continue-on-error`. The artifact upload and verification steps remain authoritative; a branch-protection or GitHub-token issue while publishing the convenience status file can no longer turn an otherwise verified APK build red.
- Commit: fef0e0e9caa5bb39ea9985d0cf77c42961bdd0d0.

## Engineering continuation — 2026-11-20: tolerate valid empty CPython asset directories

- Corrected a runtime extraction edge case: Android's AssetManager can return an empty listing for a valid empty directory. The previous strict missing-asset check treated that as a missing file and aborted extraction. Empty directories are now created normally; missing required files are still caught by the packaged-file preflight and the real CPython/Legendary instrumentation self-test.
- Commit: 848b1a0b4afc154d2b32a7e46ada26fb7034bce8.

## Engineering continuation — 2026-11-20: verify extracted runtime before committing its marker; correct extension matching

- Added a post-extraction completeness check before writing the runtime marker. The companion now verifies the CPython stdlib entry point, Legendary package, Android crypto shim, runner, lib-dynload directory, and executable mode of every extracted shared extension. An incomplete extraction is deleted and retried on the next initialization rather than being marked valid.
- Corrected over-escaped Java regular expressions in both the extraction chmod pass and the preflight/postflight checks. They now match actual `.so` and versioned `.so.*` filenames.
- Commits: 37f6fd141715a746c38d220aba9fa12daab78d4e (post-extraction verification); 933dc0bae1f31480eb6bb21607f02a91ecd44bde (regex correction).

## Engineering continuation — 2026-11-20: fail closed if stale runtime cleanup is incomplete

- Changed recursive runtime deletion to report failure if a directory cannot be listed or any stale file cannot be removed. Runtime extraction now aborts before staging a new tree if the previous incomplete CPython tree could not be fully removed, avoiding a mixed-version runtime being marked current.
- Commit: 04c4402c47013b02bd71769fc70caf42095b8cf0.

## Engineering continuation — 2026-11-20: narrow AssetManager directory detection to asset-open failures

- Refactored recursive extraction so only `AssetManager.open(childAsset)` failures are interpreted as directory entries. A `FileNotFoundException` from the destination `FileOutputStream` is now propagated as a real extraction failure instead of being misclassified as a directory and silently skipped.
- Commit: 8bf2eba3a2bc1563a62953bd0923f310bfb01679.

## Engineering continuation — 2026-11-20: tolerate installed entries with incomplete executable metadata

- Hardened installed-game serialization so a missing executable or install path in Legendary's metadata no longer crashes the entire installed-library response. Entries retain their title/path metadata, and launch validation still rejects a game with no usable executable.
- Commit: 8a2645f946dcbf2de83bccc6312ff5a5321e7d0e.

## Engineering continuation — 2026-11-20: harden Epic library display-title fallbacks

- Added stable app-name fallbacks for missing base-game and DLC display titles, including the sort key. A malformed or sparse catalog entry can no longer abort the entire Epic library response during sorting or JSON serialization.
- Commit: 71847ce5803022cb247e10c7a666a6609142931b.

## Engineering continuation — 2026-11-20: validate the packaged runtime identity format

- The companion now requires `runtime-version.txt` to contain exactly a lowercase 64-character SHA-256 hex digest before it will reuse or extract the runtime. A truncated/empty/corrupt identity asset can no longer be accepted as a valid package marker.
- Commit: e472a26e4ef7f94fb787027404a5826147d58333.

## Engineering continuation — 2026-11-20: include third-party native Python extensions in permission and ELF audits

- Expanded runtime preflight/postflight to recursively check executable mode for every `.so` under the entire CPython 3.15 tree, including any native modules nested inside site-packages rather than only top-level `lib-dynload`.
- Expanded the final APK ELF audit to extract the companion APK's full CPython 3.15 tree and verify dependencies for all shared extensions in both stdlib and site-packages.
- Commits: 0483c0f8af8837c74f184700db03e59652bdb670 (recursive runtime check); 7c7e909e23f11656ace53511d7f7393f68984cd1 (regex correction); 2ba045d4a6f620b24bbca329d01b38055ee2c5b9 (full-tree APK ELF scan).

## Engineering continuation — 2026-11-20: include versioned ELF sonames in dependency closure

- Expanded the final ELF scan from files ending exactly in `.so` to both `.so` and versioned `.so.*` names. This includes libraries such as versioned OpenSSL sonames that may be DT_NEEDED by CPython extension modules.
- Commit: e4bcffe7857bcf62fe98e0119af736555c5e31cb.

## Engineering continuation — 2026-11-20: durably save host job tracking before polling

- Changed the host's initial background-job ID persistence from asynchronous SharedPreferences `apply()` to synchronous `commit()` before polling starts. This narrows the window where Android could kill the host process after starting a long download but before the ID needed for automatic recovery reaches disk.
- Commit: e2f70a456cd968a440ef3ac9c4c5abc0003c2e3e.

## Engineering continuation — 2026-11-20: durably persist companion job state transitions

- Changed the companion service's queued/running, complete/failed, and interrupted SharedPreferences writes to synchronous commits. The host's durable job ID and the companion's job state/result now have a much smaller process-death window during long installs and service teardown.
- Commit: 3f593220f3a8f6f553386be426f34698360f6f01.

## Engineering continuation — 2026-11-20: require lib-dynload before committing extraction state

- Added an explicit post-extraction check that CPython's `lib-dynload` directory exists and can be listed before the runtime identity marker is committed. This complements the recursive shared-library permission check and prevents a missing extension directory from being accepted as a complete runtime.
- Commit: 00d2ef52ef6ef475f07fca473805187929df0136.

## Engineering continuation — 2026-11-20: publish verified APKs as a downloadable prerelease

- Added a per-source-commit GitHub prerelease after **both** the APK and CPython runtime artifacts upload successfully. The release contains the host APK, companion APK, and SHA-256 manifest; reruns update the same commit-tagged release assets.
- The generated branch CI status file now includes both the direct GitHub Releases URL and the Actions artifact URL. The prerelease notes explicitly label the APKs as experimental debug builds and document the possible signature mismatch with prior CI debug APKs.
- Changed SHA-256 generation to use relative APK filenames so the published manifest can be checked with `sha256sum -c SHA256SUMS` after extracting the release assets.
- Commits: 03bd358518eea4636a0c7aae404d4c2b94b004f2 (release publishing); ed83fb5282dc756f39b13309c68bae6682d020e2 (publish only after all uploads); 66a18c9a8399795de2f2af27c5533c0f776a9e53 (portable checksums).

## Engineering continuation — 2026-11-20: make GitHub CLI release context explicit

- Set `GH_REPO` explicitly for the release-publishing step because the repository checkout is nested under `maliwinlator/` while the APK output directory is at the workspace root. This avoids relying on GitHub CLI remote discovery from the workspace parent.
- Commit: 9f3f8b2ee5d9f1c49bf06dc957e1143629218f3a.

## Engineering continuation — 2026-11-20: make Epic background install/update jobs single-flight

- Closed a host/UI race in which two fast install/update taps could both reach the exported provider before the host persisted the first returned job ID. The provider now atomically reserves the one active Epic job slot before calling `startForegroundService`; a concurrent request receives `JOB_ALREADY_RUNNING`. If service startup throws, the reservation is released.
- Added an instrumentation test covering first reservation, rejection of a concurrent second job, release, and successful reuse of the slot.
- Commits: b1865f338a628739da2d8f3a7b5b40022fd267ad (atomic service reservation API); b49b8a47a7e1cca12f38ca110b7cc0d3aa43077a (provider gate and failure release); 69bbc6ffce99f28ae9bf7b9029ddbf17ea49b640 (instrumentation test).
- This is source-level hardening; the test has not yet been executed on an Android emulator/device.

## Engineering continuation — 2026-11-20: recover cleanly from unexpected Epic IPC failures

- Wrapped the host's background IPC dispatch in an exception boundary. A Binder/provider/runtime exception now becomes a structured `IPC_ERROR` result delivered to the fragment instead of silently killing the executor task and leaving the UI waiting forever.
- The catch path also clears the volatile polling flag while preserving the stored job ID, allowing the next Epic-tab resume to retry status tracking for a job that may still be active in the companion service.
- Commits: 91fcb2da009df9e7bc1707a391e2049fb147fff3 (IPC exception boundary); fe3dc476c9a350aa1e48db39a38900f8b3fda598 and f3d52b37737ea4403042b0e2f90948e5b9bbd69b (formatting); 938bb7d610d97ebae46cf3859648613e3fb48a6a (polling recovery).
- Android build/instrumentation remains pending; these changes are not yet device-verified.

## CI failure analysis — 2026-11-20: Legendary writer-result handler patch anchor

- Retrieved failed Actions run **44667199952** (source commit `9f3f8b2ee5d9f1c49bf06dc957e1143629218f3a`). CPython x86_64 Android runtime build passed; the first failure was step 12, "Apply and statically validate Android thread backend". No APKs or runtime artifacts were produced.
- Exact error: the patcher expected a 16-space-indented `except Empty: continue` block, but pinned Legendary 0.21.1 has that handler indented at 12 spaces inside `fw_results_handler`. The patch intent was correct; the source anchor indentation was wrong.
- Corrected the patcher's exact anchor and replacement indentation in commit `937947ced07a3b1ccac569b96e4f92061c6cef78`. This source change triggers a fresh full workflow run. It is not yet confirmed passing.
- Run log: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44667199952

## CI verification — 2026-11-20: standard 1.2.1 host APK build

- The independent standard-debug workflow triggered by source commit `937947ced07a3b1ccac569b96e4f92061c6cef78` completed its actual build, embedded Epic Bridge APK/signer audit, and APK upload steps successfully (run `44731340295`). This verifies the host Java/Gradle integration compiles and the companion asset is embedded with a matching signer.
- This is **not** the Epic-specific runtime package verification: the separate CPython/Legendary workflow `44731340221` is still building its Android runtime. No Epic companion ARM64 runtime APK is yet declared ready.

## Documentation — 2026-11-20: prepare the first device-test procedure

- Added `EPIC_GAMES_TESTING_GUIDE.md` with the install order for the host/companion pair, per-app all-files permission steps, runtime self-test, Epic browser sign-in, container selection, install/launch flow, limitations, and safe bug-report checklist.
- Commit: `753dd7df3e02b66872925146b87c479611df868a`.
- The guide explicitly says to use only the successful Epic ARM64 prerelease and not to treat an unverified or bootstrap-only artifact as ready.

## Static follow-up — 2026-11-20: verify the remaining Legendary patch anchors against the pinned source

- Independently compared all 41 literal `replace_once` anchors (including the corrected writer-result handler) against pristine Legendary commit `42f6bdeadde3a9526dc8eb713763476999ac217b`, applying the replacements in patch order. Every literal anchor matched its expected occurrence count.
- Also confirmed the separately named manager stop-method insertion anchor occurs exactly once. This is an anchor-count audit only; the official workflow still must run the patcher, Python compile/AST validation, host smoke, emulator instrumentation, and ARM64 package audit.

## CI failure analysis — 2026-11-20: AES shim API mismatch in host Legendary smoke

- The corrected patcher passed its full source transformation and AST/static-validation step. The Epic library aggregation regression tests also passed.
- The next failure was the host Legendary smoke at run `44731340221`, step 14: `ImportError: cannot import name 'AES' from legendary.android_crypto`. The shim exposed module-level `new()` and `MODE_GCM`, while Legendary's encrypted chunk/manifest readers import an `AES` namespace and call `AES.new(..., AES.MODE_GCM, ...)`.
- Added a compatible `AES` facade with `MODE_GCM` and `new()` delegating to the Android JCA-backed decrypt-only implementation. Commit: `2394c6509c96011a991b2959361b35f6da317837`.
- The failed run produced no Android runtime or APK artifact. A fresh workflow run is required to verify this API correction and continue through emulator instrumentation and ARM64 packaging.
- Failed run: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44731340221

## Engineering continuation — 2026-11-20: resume from live Epic runtime CI

- Confirmed the correct active work is in `khalilgenshin42-design/WinlatorMali`, branch `development/maliwinlator-1.2.1-bootstrap`. The branch head at this check is `2394c6509c96011a991b2959361b35f6da317837` (`fix: expose AES namespace expected by Legendary manifest readers`). Do not confuse commits on the repository default branch with this development branch.
- The earlier AES namespace mismatch is fixed. On workflow run **44731972181** (Epic runtime — CPython Android ARM64 POC, run #174), these stages have passed: x86_64 Android runtime build, Legendary 0.21.1 patch/static validation, Epic library aggregation regression tests, host Legendary smoke, staging into the x86_64 runtime, and the managed x86_64 Android emulator prerequisite smoke.
- At the last status check, the same workflow was still running at **Build the production Android ARM64 runtime** (step 17/26). Runtime audit, ARM64/host APK packaging, instrumentation, artifact upload, and prerelease publication were still pending. No Epic runtime artifacts had yet been uploaded.
- The independent standard 1.2.1 host build workflow **44731972310** completed successfully and uploaded artifact `maliwinlator-1.2.1-standard-debug-2394c6509c96011a991b2959361b35f6da317837` (SHA-256 `6c695f70192dd9e1d7079c6130f70e297425028d5fc0ee1e7874e3df5547b643`). This is the host APK build only; it is not the validated Epic host+companion pair and must not be represented as a ready Epic build.
- Current test instructions remain in `EPIC_GAMES_TESTING_GUIDE.md`. Install only the matched host/companion pair from a successful Epic runtime workflow after all validation and prerelease publication steps complete.
- Next action: poll run **44731972181** and inspect the first failing step if it fails; if ARM64 packaging and all later validations pass, inspect the prerelease assets and SHA-256 manifest, then hand the matched APK pair to the user for device testing. Do not rerun or duplicate the long ARM64 build while it is still active.

## Live CI update — 2026-11-20: production ARM64 runtime built and audited

- Follow-up poll of Epic workflow run **44731972181** confirms step 17, **Build the production Android ARM64 runtime**, completed successfully, and step 18, **Audit packaged runtime**, also completed successfully.
- The workflow has advanced to step 19, **Install MaliWinlator Android build toolchain**. Epic Bridge x86_64 instrumentation (step 20), ARM64 companion + host APK build (step 21), uploads (steps 22–23), and prerelease publication (step 24) remain pending.
- This is meaningful progress beyond the earlier x86_64 emulator smoke: the production ARM64 CPython runtime has now compiled and passed its package audit. It is still not a downloadable/validated Epic APK pair until all remaining steps succeed.
- Handoff documentation was updated in commit `30eaf220f8faa48d23239bdb31911810341879dc`; this follow-up records the newly completed ARM64 stages.

## Live CI update — 2026-11-20: Android build toolchain ready; instrumentation running

- Epic workflow run **44731972181** has now completed the Android SDK/NDK/CMake toolchain installation successfully and entered step 20, **Run Epic Bridge x86_64 Android instrumentation tests**.
- Steps 1–19 are all successful, including production ARM64 CPython runtime compilation and packaged-runtime audit, plus the managed x86_64 runtime smoke.
- Steps 21–24 (ARM64 APK build, artifact uploads, and prerelease publication) have not yet started. No Epic APK pair is published at this checkpoint.
- The workflow definition's release gate is correctly sequenced after Android JNI instrumentation, APK signature/package/ABI checks, ELF dependency closure, and exact embedded-companion byte comparison. Do not bypass these gates to distribute the baseline host-only APK.

## CI failure analysis and corrected retry — 2026-11-20

- Run **44731972181** failed only at workflow step 20, `Run Epic Bridge x86_64 Android instrumentation tests`. The emulator booted successfully, but `reactivecircus/android-emulator-runner@v2` executes its `script` using `/usr/bin/sh`; the script began with Bash-only `set -euo pipefail`, causing immediate exit: `/usr/bin/sh: 1: set: Illegal option -o pipefail`.
- No Android instrumentation assertions ran in that attempt. Therefore this failure is an identified CI shell portability defect, not evidence of an Epic Java/JNI functional failure.
- Corrected `.github/workflows/epic-python-runtime-poc.yml` emulator script to use POSIX `set -eu`. Commit: `f72c282ebb3918f2f5df6be336553cf0a607af69`.
- GitHub automatically started corrected workflow run **44733619638** from that commit. At the time of this note it is in progress at initial Android SDK setup; instrumentation, APK build, and publication are still pending.
- Previous successes remain: Legendary source patch/static validation, Epic library aggregation tests, host smoke including AES-GCM known-answer and worker lifecycle, managed x86_64 runtime smoke, production ARM64 CPython runtime build, runtime archive audit, and Android SDK/NDK toolchain installation.
- Do not install the host-only baseline APK or claim Epic integration is ready until corrected run completes instrumentation, produces both ARM64 APKs, passes signature/ABI/assets/ELF dependency and embedded-companion audits, and publishes the prerelease with SHA-256 sums.


## CI failure analysis and shell-scope correction — 2026-11-20

- Corrected retry **44733619638** passed all runtime build/audit stages and successfully booted its managed Android x86_64 emulator. It then failed before Gradle or any instrumentation assertion ran.
- Exact failure: `chmod: cannot access './gradlew': No such file or directory`. The emulator action invokes each newline-delimited script command in a separate `/usr/bin/sh -c` process, so `cd maliwinlator` did not persist into the following `chmod` command. This was another CI script orchestration defect, not an Epic Bridge test failure.
- Reworked the emulator script into one parenthesized shell command: `(cd maliwinlator && chmod +x ./gradlew && ./gradlew ... )`, keeping the directory change, wrapper permission fix, and instrumentation invocation in the same shell process.
- Fix commit: `4bc34b9b334b101f0d0675c20b92d6b5636935c5`. A new run should be triggered by the workflow-file path filter. Instrumentation, ARM64 APK packaging, APK audits, artifact upload, and prerelease publication remain unverified until that run passes.
- Failed run: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44733619638


## Live CI checkpoint — 2026-11-20: corrected emulator command reached instrumentation

- Corrected Epic runtime workflow run **44737481566** (run #176), source commit `4bc34b9b334b101f0d0675c20b92d6b5636935c5`, has passed steps 1–19, including x86_64 Android runtime build, Legendary patch/static validation, Epic library aggregation tests, host smoke, managed x86_64 emulator runtime prerequisite smoke, production ARM64 CPython runtime build, runtime archive audit, and Android SDK/NDK/CMake installation.
- The workflow has advanced to step 20, **Run Epic Bridge x86_64 Android instrumentation tests**. This is the first attempt to get beyond the two earlier emulator-script orchestration failures and actually execute the Gradle instrumentation task.
- At this checkpoint, the job remains `in_progress`; steps 21–24 (ARM64 host+companion APK build, APK audits, artifact uploads, and prerelease publication) have not started. No installable Epic APK pair is available yet.
- GitHub's job-log blob is not yet available while the job is active (log endpoint returned BlobNotFound); no instrumentation result should be inferred from that.
- Next action: poll run **44737481566**. If instrumentation fails, retrieve the completed job log and fix the first actual Gradle/test failure. If it passes, verify the ARM64 APK pair, signer/package/ABI/ELF checks, embedded-companion byte identity, SHA-256 manifest, uploaded artifacts, and prerelease assets before calling the build ready.
- Run URL: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44737481566


## CI failure analysis and diagnostic retry — 2026-11-20

- Epic runtime workflow run **44737481566** (run #176) completed with failure at step 20, :epicbridge:connectedDebugAndroidTest. All earlier steps 1–19 passed, including x86_64/ARM64 CPython runtime builds, Legendary static/host tests, managed emulator runtime prerequisite smoke, runtime archive audit, and Android toolchain setup.
- The emulator instrumentation task actually ran four tests. Two runtime tests failed with the same response: {"ok":false,"error":"PYTHON_RUNTIME_ERROR"}; the provider authorization and single-flight job reservation tests were not reported as failing. APK build/upload/release steps were skipped by the intended gate.
- The generic response was produced by epicbridge/src/main/c/epic_python.c when nativeCall() failed to obtain a Python Unicode result. The existing PyErr_Print() did not surface the root exception in the Gradle log, so the exact Python import/runtime exception remains unknown. Do not guess at the cause or weaken the test.
- Added native exception type/message reporting through Android logcat (EpicBridgePython) while retaining the generic structured failure response: commit bc0215074b86d1a407974cafccbc653cd9a006df.
- Updated the emulator instrumentation script to dump the relevant Android logcat tags if Gradle tests fail, and corrected the shell flow so the failure branch still runs under the action's errexit behavior: commits d78c753547752cd25c0c834b29343bdb8504a63e and 33ec6faf99d6d0003ac7d68d964029fe92e3a394.
- The workflow-file and native-source commits trigger a new full Epic runtime run. Next action: inspect the latest run and, if it reaches instrumentation, use the captured native exception to fix the actual runtime defect. Do not publish or install an APK pair until instrumentation and the full ARM64 packaging/audit/release chain pass.
- Failed run: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44737481566


## Live CI update — 2026-11-20: diagnostic Epic runtime retry queued and running

- The diagnostic source/workflow changes are now on the development branch. Latest Epic runtime workflow run is **44739838824** (run #179), source commit **33ec6faf99d6d0003ac7d68d964029fe92e3a394**.
- Run #179 is in progress; checkout and host Python/Java setup passed, Android SDK setup is running. The earlier duplicate workflow attempts from the intermediate commits were cancelled by the workflow concurrency group; run #179 contains both the native exception logging and failure-path logcat capture.
- A separate standard baseline APK workflow run **44739802087** was also triggered by the C source change. It is not the Epic CPython runtime release pipeline and must not be confused with the required matched host/companion APK pair.
- Run #179 must reach emulator instrumentation to expose the native CPython exception in the captured Android logcat. The next code change should be based on that concrete exception, not speculative changes to Python paths or packaging.
- Epic runtime run URL: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44739838824


## Live CI update — 2026-11-20: emulator prerequisite passed; ARM64 build running

- Polled Epic runtime workflow run **44739838824** (run #179), source commit **33ec6faf99d6d0003ac7d68d964029fe92e3a394**.
- Confirmed step 16, **Run Epic Android runtime prerequisite smoke on managed x86_64 emulator**, completed successfully. This clears the managed-emulator prerequisite stage that was previously pending.
- Step 17, **Build the production Android ARM64 runtime**, is now in progress. Steps 18–24 remain gated: runtime archive audit, Android build toolchain setup, x86_64 JNI/Python instrumentation (including the newly added native exception diagnostics), matched ARM64 host+companion APK build, uploads, and prerelease publication.
- No installable Epic APK pair has been produced or published at this checkpoint. The independent baseline host APK is not a substitute.
- Next action: poll run #179; once instrumentation completes, retrieve logcat diagnostics on failure and fix the concrete exception, or proceed to verify both APKs and all release audits if it passes.
- Run URL: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44739838824


## CI failure analysis and shell-script isolation fix — 2026-11-20

- Epic runtime run **44739838824** (run #179) completed with failure at step 20, before Gradle or any instrumentation test ran. Steps 1–19 passed, including managed x86_64 CPython smoke, production ARM64 CPython build, runtime archive audit, and Android SDK/NDK/CMake installation.
- Exact failure from the completed job log: `/usr/bin/sh: 1: Syntax error: end of file unexpected (expecting "fi")`. The Android emulator action runs each newline-delimited script command as a separate `/usr/bin/sh -c` process. The multiline `if/then/else/fi` block therefore could not be parsed as one shell program. The earlier parenthesized command fixed directory scope but did not solve this action-specific script execution model.
- This is a CI orchestration failure, **not** a CPython/JNI/Python runtime failure. The newly added exception diagnostics were not reached, so the original two runtime test failures from run #176 remain undiagnosed.
- Added `tools/ci/run-epic-android-instrumentation.sh`, a dedicated Bash script that runs Gradle and captures filtered logcat on failure in one process. Updated the emulator action to invoke it as a single command: `bash tools/ci/run-epic-android-instrumentation.sh`.
- Commits: `c5ffae24bfe84ed862750b9bf2e3afc6d5a511a7` (isolated Bash test/diagnostic script); `4956279731d299f4e9e361b9a12af44ad81d1925` (workflow invokes script).
- Expected new Epic runtime run is triggered by the workflow update. Next: verify it passes step 20 and retrieve the actual `EpicBridgePython` logcat exception if either CPython test still fails. Only then proceed to the ARM64 companion/host APK build and gated release audits.
- Failed run: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44739838824


## CI failure analysis and workspace-path correction — 2026-11-20

- Epic runtime run **44742212167** (run #180), source commit `4956279731d299f4e9e361b9a12af44ad81d1925`, passed the managed x86_64 CPython runtime smoke, production Android ARM64 CPython build, packaged-runtime audit, and Android SDK/NDK/CMake installation.
- It failed at step 20 before Gradle or any instrumentation assertion ran. Exact error: `bash: tools/ci/run-epic-android-instrumentation.sh: No such file or directory`. The emulator action starts its script from `$GITHUB_WORKSPACE`; the repository itself was checked out under `$GITHUB_WORKSPACE/maliwinlator`. The script existed, but the workflow invoked it without the checkout prefix.
- Corrected the workflow invocation to `bash maliwinlator/tools/ci/run-epic-android-instrumentation.sh`. The script itself changes directory relative to its own location, so it then runs Gradle from the checked-out project root and retains logcat diagnostics on genuine test failures.
- Fix commit: `022126e2768103da2c9140189ff3d815cbebaa6c` (`ci: invoke Epic instrumentation script from workspace root`). This triggers a new full workflow run.
- This is a CI path/orchestration failure, **not** evidence of a CPython/JNI regression. The original two `PYTHON_RUNTIME_ERROR` failures from run #176 remain undiagnosed because instrumentation has still not run with the diagnostic logging enabled.
- Run #180 produced no installable APK artifacts; its gated APK build/upload/release steps were skipped. Do not install a host-only baseline APK or claim Epic integration is ready.
- Next: follow the new workflow run; require the JNI instrumentation to actually execute, inspect `EpicBridgePython` logcat if it fails, then validate the matched ARM64 host+companion APKs, signer/package/ABI/ELF audits, embedded-companion byte identity, SHA-256 manifest, uploaded artifacts, and prerelease assets.
- Failed run: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44742212167


## CI failure analysis and instrumentation project-root correction — 2026-11-20

- Epic runtime run **44748235868** (run #181), source commit `022126e2768103da2c9140189ff3d815cbebaa6c`, completed with failure at step 20. The managed x86_64 emulator booted successfully, but the instrumentation helper failed before Gradle launched:
  `maliwinlator/tools/ci/run-epic-android-instrumentation.sh: line 4: cd: maliwinlator/tools/ci/../../maliwinlator: No such file or directory`.
- Root cause: the workflow invokes the helper from the workspace root, and the helper itself is located at `maliwinlator/tools/ci`. Its previous `../../maliwinlator` suffix incorrectly attempted to enter a nested `maliwinlator/maliwinlator` directory. Correct project root relative to the helper is simply `../..`.
- Important successful stages in run #181: x86_64 Android runtime build; Legendary Android thread-backend patch/static validation; Epic library aggregation tests; host smoke including AES-GCM and worker lifecycle; managed x86_64 emulator prerequisite smoke; **production Android ARM64 CPython runtime build**; **packaged ARM64 runtime audit**; Android SDK/NDK/CMake toolchain installation.
- Step 20 failed before Gradle or any JNI instrumentation assertion. This is a helper path defect, not a CPython/JNI test result. The earlier two `PYTHON_RUNTIME_ERROR` failures from run #176 are still not diagnosed.
- Corrected `tools/ci/run-epic-android-instrumentation.sh` to use `cd "$(dirname "$0")/../.."`. Commit: `dab879b1c0ccc181c1bc558dae00220151981ea2` (`ci: correct Epic instrumentation project root`).
- The workflow path filter already includes `tools/ci/`, so this helper change triggers a fresh Epic runtime run. Next: verify the helper reaches Gradle; if instrumentation fails, retrieve the captured `EpicBridgePython` logcat exception and fix the actual runtime defect. If it passes, proceed through the gated matched ARM64 APK build, signer/package/ABI/ELF checks, exact embedded-companion byte comparison, SHA-256 manifest, artifact upload, and prerelease publication.
- Run #181 URL: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44748235868
- No APK pair was built or published by run #181. Do not install or announce an Epic-ready build yet.


## CI trigger correction — 2026-11-20

- Follow-up inspection found the Epic workflow's `push.paths` filter did **not** include `tools/ci/run-epic-android-instrumentation.sh`. The earlier note that the helper path was already covered was incorrect; the path match was not present in the workflow YAML.
- Added the exact helper path to the workflow trigger list so future edits to this script reliably start the Epic runtime pipeline.
- Workflow trigger fix commit: `db4785dc4e20bd8fc925bc53d9a404ba02d9ff24` (`ci: trigger Epic runtime on instrumentation helper changes`).
- The helper path correction itself is commit `dab879b1c0ccc181c1bc558dae00220151981ea2`. The workflow trigger correction is a separate commit because the first helper-only push did not start the pipeline.
- Next: confirm a new run starts from the trigger-fix commit and reaches the Gradle instrumentation task. No APK pair has been produced yet.


## Epic CI run #182 — JNI compile failure and correction (2026-11-20)
- Run #182: [GitHub Actions](https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44750111414), source SHA `db4785dc4e20bd8fc925bc53d9a404ba02d9ff24`.
- Steps 1–19 passed, including the production ARM64 CPython/Legendary runtime build and runtime audit. Step 20 reached the Android Gradle instrumentation build, then failed while compiling `epicbridge/src/main/c/epic_python.c`; the JNI instrumentation tests did not execute and APK packaging/release steps were correctly skipped.
- Exact compiler error: line 245 had an over-escaped C string literal, `"{\\\\\"ok\\\\\":false,...}"`, producing `error: expected ')'`. This was introduced in the earlier JNI exception-diagnostics change, not an emulator/runtime issue.
- Corrected the fallback JSON literal to standard C escaping: `"{\\\"ok\\\":false,\\\"error\\\":\\\"PYTHON_RUNTIME_ERROR\\\"}"`.
- Fix commit: `f2809334eb322efaa9037dd18c2d72d981539901` — `fix: correct JSON fallback string escaping in JNI bridge`.
- No installable APK artifacts or release were produced by run #182. Next: rerun the same workflow on the fix commit; verify the x86_64 JNI instrumentation tests, then ARM64 APK build, signer/package/ABI/ELF/embedded-byte audits, artifact upload, and prerelease. Do not call Epic integration ready until all gates pass and the APK pair is available.


## Epic runtime run #183 — Android stdlib package omission and corrective build (2026-11-20)

- Run #183: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44752220023, source commit `f2809334eb322efaa9037dd18c2d72d981539901`.
- Production ARM64 CPython 3.15.2 runtime compilation and its archive audit passed. The Android x86_64 JNI instrumentation then failed inside the companion runtime with the concrete exception `ModuleNotFoundError: No module named 'zipfile._path'`. This is a missing CPython standard-library package in the staged Android runtime, not a Legendary API or JNI linkage failure.
- CPython v3.15.2 defines `Lib/zipfile/_path/` as a package directory (`__init__.py`, `_functools.py`, `glob.py`). The Android packaging pipeline had not preserved that package in its runtime prefix.
- Corrected the Epic workflow to explicitly stage the pinned CPython source package into both x86_64 instrumentation and ARM64 production prefixes before invoking `Platforms/Android package`. Commit: `00a8b762124f6f27cd0bc9a29dbf87f575d7a1bc`.
- Added managed Android runtime smoke assertions for `import zipfile._path`, `importlib.metadata.version('legendary-gl')`, and `importlib.resources.files('legendary')` so the missing module is caught before JNI instrumentation. Commit: `fa0dc92018676693ee943f66eb7cede0ff9faf09`.
- Run #184 (source commit `00a8b762...`) was superseded/cancelled by the immediate smoke-test commit. Run #185 is the authoritative full retry: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44755104580. At this handoff update it is in progress; no matched ARM64 Epic APKs have been produced or released.
- Next gate: confirm the new managed-emulator smoke actually imports `zipfile._path`, then JNI instrumentation, ARM64 host+companion build, signature/package/ABI/ELF/embedded-byte audits, artifact upload and prerelease. Only after all gates pass should the APK pair be handed over for on-device validation.

- Follow-up on authoritative run #185 (2026-11-20): the repaired x86_64 managed-emulator runtime prerequisite smoke passed; production ARM64 CPython 3.15.2 runtime build and packaged-runtime audit also passed. CI has moved to Android toolchain installation, with JNI instrumentation and paired APK build/upload/release still pending. This is a real green milestone for both runtime architectures, but not yet a usable app release.


## Epic runtime run #185 — JNI instrumentation still missing zipfile package (2026-11-20)

- Authoritative run **44755104580** completed with failure at step 20, `:epicbridge:connectedDebugAndroidTest`; ARM64 APK build, artifact upload, and prerelease publication were correctly skipped. [Run #185](https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44755104580).
- The managed CPython x86_64 prerequisite smoke passed, and the production ARM64 CPython runtime build plus archive audit passed. The JNI companion instrumentation still failed twice with `ModuleNotFoundError: No module named 'zipfile._path'`.
- The previous correction copied only `Lib/zipfile/_path` into each prefix. That was insufficient to prove the parent package and child module are co-located in the exact runtime tree consumed by Gradle and JNI.
- Updated both x86_64 test and ARM64 production staging to replace the prefix's `lib/python3.15/zipfile` directory with the complete pinned CPython 3.15.2 `Lib/zipfile` package, and added archive-content assertions for both `zipfile/__init__.py` and `zipfile/_path/__init__.py`. Workflow fix commit: `28f643d752c81767f2b4eed21f192a58be53ad09`.
- Added a Gradle `stageEpicPythonRuntime` fail-fast check for both package files in the generated APK asset tree. This prevents a future JNI test from silently running with an incomplete staged stdlib. Commit: `fdd46187102f5b0856d5cfc0e0a881fa9ea4d339`.
- The workflow is now triggered for a fresh full retry by these commits. Next gate: confirm the archive assertions, Gradle staging assertion, and JNI instrumentation pass; only then continue to matched ARM64 APK build, signature/package/ABI/ELF and embedded-companion audits, artifact upload, and prerelease.
- No installable Epic APK pair has been produced. Do not install the baseline host-only APK or claim Epic integration is ready.


## Epic runtime follow-up — #187 exposed embedded interpreter path mismatch (2026-11-20)

- Run **44758018218** failed in the x86_64 Android instrumentation step with `ModuleNotFoundError: No module named 'zipfile._path'`; the managed CPython prerequisite smoke, ARM64 runtime build, runtime archive audit, and Android toolchain install all passed. APK build and release steps were skipped. [Run #187](https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44758018218).
- The earlier source-prefix and tar-archive checks were insufficient: they did not prove the embedded CPython initialized by JNI could resolve the staged standard-library package. The failure is now being treated as a runtime import-path issue, not merely a missing source file.
- Added explicit `sys.path` initialization in JNI immediately after CPython startup: prepend the app-private `$PYTHONHOME/lib/python3.15` standard library and its `site-packages` directory before any bridge imports. Added bounded path construction and failure handling. Commits: `be04d705886acfd2c939517501f28af7c077046c`, `92f93243c707a4b161ce58e11919ffcc314b557c`.
- Java runtime preflight and post-extraction validation now require both `zipfile/__init__.py` and `zipfile/_path/__init__.py`, forcing refresh of incomplete previously extracted runtime data. Commit: `99d0dafebc5f1f7252d01eb879fc19981bf5794`.
- Python runtime self-test now imports `zipfile._path`, checks `importlib.resources.files('legendary')`, and reports module origins/search paths; Android instrumentation asserts that both module origins are under the extracted CPython 3.15 standard library. Commits: `4edb704acaa5a2b2c8356e80e9e2627b34559aab`, `ef6d376e2a65b6c63a2a63bc58cca37db83d740c`.
- Next gate: full Android instrumentation must demonstrate both module origins resolve from the app-private extracted runtime. If this fails, use returned diagnostic search paths and module origins to correct the precise CPython home/path setup. No APK pair is available yet; keep all install/release claims blocked until instrumentation, ARM64 APK signing/package/ABI/ELF audits, companion-byte identity, artifact upload and prerelease all pass.


## Epic runtime run #192 — CPython JNI initialization remains unavailable (2026-11-20)

- Run **44778405080** reached Android instrumentation after successful x86_64/ARM64 CPython builds, managed-emulator prerequisite smoke, Legendary host tests, and runtime archive audit. It failed at step 20; the paired Epic APK build and release steps were correctly skipped. [Run #192](https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44778405080).
- Exact test output: two CPython runtime tests returned `RUNTIME_NOT_READY` / `The bundled Android CPython runtime is unavailable`; the other two instrumentation tests did not report failures. This run no longer reproduces the earlier visible `zipfile._path` import exception, but it does not yet prove the native interpreter initialized.
- Root-cause diagnostics were not actually captured: `tools/ci/run-epic-android-instrumentation.sh` filtered logcat for `EpicBridgePython`, `python`, and `AndroidRuntime`, but Java runtime extraction/initialization messages use the separate `EpicPythonRuntime` tag. This omission hid the actionable Java-side reason for `initialize()` returning false.
- Corrected the helper logcat filter to include `EpicPythonRuntime:E` before the next retry. Commit `5dfb36dcaee2fc7d49b43a47a8932ea3857e2ce8`.
- Next: rerun and inspect the newly captured Java + JNI diagnostics. Determine whether the failure is asset extraction, runtime preflight, native library loading, or `Py_InitializeFromConfig`; do not make speculative path changes or weaken the instrumentation assertions. No Epic APK pair is available from run #192.
- Separate host APK workflow run **44778405079** passed and uploaded baseline artifact **14651890118**, but it is not the Epic-enabled paired release and must not be treated as such.

## Epic runtime run #193 — extraction preflight failure isolated (2026-11-20)

- Run **44780743921** (source commit `5dfb36dcaee2fc7d49b43a47a8932ea3857e2ce8`) passed the x86_64 managed-runtime prerequisite smoke, production ARM64 CPython build, and runtime archive audit, but failed during Android JNI instrumentation. [Run #193](https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44780743921).
- The corrected logcat filter successfully exposed the Java-side cause: `EpicPythonRuntime.initialize()` failed during post-extraction validation with the generic error `Extracted CPython runtime is incomplete or contains a non-executable shared extension`. CPython native initialization was not reached. The prior `zipfile._path` and `sys.path` hypotheses were not the failure reported by this run.
- Replaced the opaque combined preflight with explicit missing-file diagnostics and a recursive pass that marks every extracted CPython `.so` shared library executable before verifying permissions. Failures now identify the exact missing file, unreadable directory, or library whose execute bit could not be set.
- Fix commit: `fecc1fa8dfd1a3b101d00d15c11df64fe72ed270` (`fix: diagnose and repair extracted CPython library permissions`).
- Next gate: the new workflow must pass the Android x86_64 JNI instrumentation. If it fails, the new exception text should distinguish a missing runtime asset from a chmod/filesystem permission problem. Managed x86_64 smoke and ARM64 archive audit are already green, but **no Epic-enabled APK pair was built or released by run #193**. Do not install the host-only baseline APK or claim Epic is ready.

## Epic runtime run #194 — upstream Android testbed dependency resolution blocked (2026-11-20)

- Run **44783162888** (source commit `fecc1fa8dfd1a3b101d00d15c11df64fe72ed270`) failed before the managed emulator started. The failure is **not** a repeat of the CPython extraction check and provides no verdict on the permission-repair change.
- The pinned CPython v3.15.2 Android testbed Gradle configuration failed while resolving its buildscript classpath. Gradle reported multiple published Maven dependencies as unavailable, including `org.jetbrains.kotlin:kotlin-stdlib:2.3.20`, `kotlin-reflect:2.3.20`, `org.jetbrains:annotations:13.0`, `org.apache.httpcomponents:httpmime:4.5.6`, `commons-io:2.16.1`, and ASM 9.9. These are upstream Android Gradle Plugin 9.3.1 transitive dependencies, not MaliWinlator source dependencies.
- Verified the Kotlin 2.3.20 artifact is published on Maven Central; therefore the failure is consistent with repository/network resolution trouble during this run, not an invalid Kotlin version. Do not pin/downgrade CPython's upstream Android Gradle Plugin or alter its testbed just to bypass a transient resolution failure.
- Requested a retry of the failed workflow job **136450907387** to distinguish a transient Maven repository failure from a persistent infrastructure problem.
- Next: if retry gets past dependency resolution, resume the managed x86_64 runtime smoke and inspect the newly explicit extraction diagnostics if initialization fails. No Epic APK pair was built by run #194.


## 2026-11-20 — CPython nested-asset extraction fix

- CPython Android build retry job `136455278919` in workflow run `44783162888` passed the previously blocked Maven dependency resolution and reached the managed Android x86_64 emulator.
- Instrumentation failed during Java-side runtime extraction, before CPython native initialization. Exact missing extracted path: `files/epic-runtime/python/lib/python3.15/zipfile/_path/__init__.py`. The runtime archive audit had already verified this path exists in the upstream package, so the failure was narrowed to Android APK asset traversal/extraction rather than CPython build staging.
- Root cause hypothesis: extractor attempted `AssetManager.open(childAsset)` before checking whether the child is a directory. Android asset directory entries are exposed via `AssetManager.list()`; opening a directory is not a reliable directory test and can lead to a directory entry being materialized as a file, skipping descendants.
- Fix committed in `8de605ca765dd8f9dba589638b7cddf387f0ed4f`: `EpicPythonRuntime.extractAssetDirectory()` now checks `AssetManager.list(childAsset)` first, recursively handles non-empty directories, explicitly creates empty directories, and opens only leaf assets as files. `isPackaged()` now also requires the nested `zipfile/_path/__init__.py` APK asset, so packaging omission and extraction failure are distinguished earlier.
- Next gate: rerun the Android instrumentation workflow and confirm both the packaged-asset preflight and post-extraction checks pass, then reach the CPython JNI/self-test assertions. No Epic APK is ready for user installation until the full CI artifact and signing/ABI/dependency audits pass.


## 2026-11-20 — Runtime prefix silently omitted from instrumentation APK

- Workflow run **44786427882** reached managed Android instrumentation after the recursive asset extraction change. CPython x86_64 and ARM64 archives were successfully built, and the archive listing explicitly contained `zipfile/__init__.py` and `zipfile/_path/__init__.py`.
- Instrumentation failed earlier than extraction: `EpicPythonRuntime.isPackaged()` returned false because the test APK did not contain the required CPython assets and JNI library. The test failures were the packaging preflight assertions, not Python import or JNI initialization failures. The managed emulator also briefly reported ADB offline during cold boot, but Gradle ultimately ran all four tests; two failed on missing packaged runtime.
- The Gradle module previously allowed a missing `epicRuntimePrefix` to silently continue: the staging task skipped runtime files and CMake deliberately built `epic_python_stub.c`. This allowed a misleadingly successful Gradle task graph to produce a nonfunctional companion APK for instrumentation.
- Fix committed in this change: resolve `epicRuntimePrefix` explicitly from `rootProject.findProperty` first; make `stageEpicPythonRuntime` fail closed unless the CPython headers, shared library, stdlib and nested zipfile package exist; print the selected prefix/ABI; and fail if generated Python assets were not staged. This prevents stub-only companion packaging from passing as a runtime build.
- The separate standard MaliWinlator 1.2.1 debug APK workflow **44786427834** succeeded, including embedded companion byte comparison and shared signer validation. It is a standard build, not a verified Epic-enabled release.
- Next gate: rerun the CPython workflow. Confirm its log prints the correct x86_64 prefix and ABI, verify `libepic_python.so` links against the real `libpython3.15.so`, then pass all Android instrumentation tests before ARM64 APK packaging/release. No Epic-enabled APK pair is ready yet.


## 2026-11-20 — Configuration-cache compatibility in fail-closed staging

- Run **44788442078** reached the corrected `stageEpicPythonRuntime` preflight, proving the runtime packaging guard is active, but the task failed before checking the prefix contents.
- Exact Gradle error at `epicbridge/build.gradle:97`: `Cannot reference a Gradle script object from a Groovy closure as these are not supported with the configuration cache.` The cause was calling the script's `file(...)` helper and `logger` from the task action closure.
- Replaced those script-object calls with `new File(epicRuntimePrefix)` and `System.out.println(...)`, retaining the strict prefix validation and no-stub fail-closed behavior. No configuration-cache disablement or relaxation of runtime checks.
- Standard baseline APK workflow **44788442108** succeeded again, including embedded companion byte-match and shared signer validation.
- Next: rerun Epic workflow, confirm prefix validation and runtime asset staging complete, then inspect Android instrumentation results. No Epic APK pair is ready.

- Follow-up trigger audit: the Epic workflow's `push.paths` did not explicitly include `epicbridge/build.gradle`, so the Gradle staging correction alone did not launch the Epic workflow (only the baseline workflow ran). Added the Gradle file to the Epic workflow path filters to ensure future packaging/build-logic changes automatically run the Android instrumentation gate.


## 2026-11-20 — Epic runtime APK packaging diagnostics
- Epic workflow run #44790782332 (run ID 44790782332, job 136477585874) completed with failure at Android x86_64 instrumentation. CPython x86_64 and ARM64 runtime builds/archive audits completed; Gradle executed `:epicbridge:stageEpicPythonRuntime`, then Android tests failed because `EpicPythonRuntime.isPackaged()` returned false (assets/JNI not all visible from the installed test target APK).
- This is now narrowed to the final APK packaging boundary, not CPython source compilation or zipfile archive contents. The workflow's ARM64 APK audit has not yet run because instrumentation is an earlier gate.
- Added explicit per-entry diagnostics in `EpicPythonRuntime.isPackaged()`: it now reports whether `libepic_python` or a native dependency failed to load, or the exact missing APK asset (stdlib, Legendary package, bridge runner, zipfile._path, or runtime identity). Commit: `1c2a5210ec774d8586f11a8d47ef3aaf68060d88`.
- Next: rerun the Epic workflow, read the emitted `EpicPythonRuntime` logcat line, then fix the precise source-set/JNI/assets packaging defect. Keep the instrumentation gate strict; do not ship stub or partial runtime APKs.


## 2026-11-20 Epic runtime staging correction (commit 7c58e88a68cc97ceff541a25d6e2a38520873d65)
- Epic workflow run #44793157660 failed Android instrumentation; standard debug APK workflow #44793157665 succeeded, including embedded companion byte match and shared signer audit.
- Exact Android asset failure from emulator logcat: `python/lib/python3.15/zipfile/_path/__init__.py` missing from companion APK. Other earlier preflight assets were found. Managed x86_64 and ARM64 CPython runtime archive audits both showed the path, so the defect is between runtime-prefix staging and Android asset packaging, not Legendary import/runtime behavior.
- Updated both x86_64 and ARM64 CPython workflow staging commands from `cp -a` to `cp -aL` for CPython's `Lib/zipfile` package, then added explicit checks that `zipfile/_path` and its `__init__.py` are not symlinks. This forces dereferencing if upstream ships the relocated helper as a symlink, which Android asset packaging may omit. Commit: `7c58e88a68cc97ceff541a25d6e2a38520873d65`.
- Next gate: confirm fresh workflow #447... passes CPython runtime packaging, Android instrumentation, then ARM64 APK signature/ABI/ELF/asset/embedded-byte audits. Do not distribute prior APKs as Epic-enabled; they are not runtime-verified.


## 2026-11-20 second CPython asset-staging diagnosis (commit 388540ea99190a57db9408c1132babc2ba0d691d)
- Follow-up Epic run #44795609873 failed the same Android instrumentation gate. Logcat confirms the same exact missing APK asset: `assets/python/lib/python3.15/zipfile/_path/__init__.py`. The source CPython prefix and both runtime tarballs contain the file, and workflow checks confirm the staged source is a real directory/file (not symlinks). Therefore the prior symlink-only theory was incomplete: the missing file is lost during Gradle runtime asset staging/packaging.
- Added a dedicated Gradle `from(.../zipfile/_path)` copy specification in `epicbridge/build.gradle`, targeting the exact generated asset path `assets/python/lib/python3.15/zipfile/_path`, in addition to the full stdlib copy. Commit: `388540ea99190a57db9408c1132babc2ba0d691d`.
- Re-run Android instrumentation. If this still fails, inspect the generated Gradle asset merge output and APK archive before emulator startup; add an explicit APK zip-entry assertion for the nested _path package (not just os.py/Legendary/JNI) so packaging fails at build time with a precise cause.


## 2026-11-20 — CPython runtime staging duplicate-source correction
- Epic runtime workflow #44801339991 failed before instrumentation could run: Gradle task `:epicbridge:stageEpicPythonRuntime` rejected `assets/python/lib/python3.15/zipfile/_path/glob.py` as a duplicate.
- Evidence: both CPython x86_64 and ARM64 runtime archives contain `zipfile/_path/__init__.py`; the failure is caused by the explicit `zipfile/_path` staging source overlapping the full Python stdlib staging source, not by a missing source file.
- Fixed `epicbridge/build.gradle` in commit `f73f5990726b34719177b153a55d03906e7dbd83` by setting `duplicatesStrategy = DuplicatesStrategy.EXCLUDE` on the Sync task. This preserves the broad stdlib copy while allowing the explicit path source to fill any omitted nested files.
- Standard host APK workflow #44801340002 passed, including embedded companion APK byte-match and shared signer audit. It is not evidence that Epic runtime works.
- Next gate: rerun Epic instrumentation; verify actual staged assets and APK contents, then confirm Android CPython initializes and imports `zipfile._path` and Legendary. No Epic-enabled APK is approved yet.


## 2026-11-20 — Verification retry after duplicate staging fix
- Standard MaliWinlator 1.2.1 baseline workflow #44803653869 passed: debug APK build, embedded Epic Bridge byte-match/shared signer verification, and artifact upload all succeeded. This is a baseline build only; it does not validate the Epic runtime.
- Epic workflow #44803653839 passed source checkout, CPython Android x86_64 build, and archive staging, but failed before running Android instrumentation because the upstream CPython Android testbed Gradle build could not resolve its classpath dependencies (including Kotlin stdlib 2.3.20). The failure occurred during testbed Gradle configuration, before runtime instrumentation; it is not evidence that the CPython APK asset is still missing.
- Retried only failed jobs for #44803653839 using GitHub Actions rerun. Await the retry before changing runtime packaging or interpreting this as a runtime regression.
- The duplicate staging fix remains commit `f73f5990726b34719177b153a55d03906e7dbd83`; no Epic runtime pass or device acceptance has been established.


## 2026-11-20 — isolate generated-asset source registration (commits 05809d3bf2aa3b9a3721d56332132f8d045839be and 4cd0f3e900938033ca50c14c84b848024ab05493)
- Epic retry #44803653839 (attempt 2, job 136525145938) reached the Android emulator. The failure is still the exact missing APK asset `python/lib/python3.15/zipfile/_path/__init__.py`; the runtime packaging preflight fails before extraction or CPython initialization. This rules out a Python import/GIL/JNI runtime defect as the current blocker.
- The staged Gradle Sync task's own `doLast` verifies the nested file exists under `build/generated/epicRuntime/assets`, but Android's `AssetManager` cannot open it from the assembled instrumentation APK. The most likely boundary is registration of the generated assets source directory. Changed `sourceSets.main.assets.srcDir` from a mapped Gradle Provider to the explicit concrete path `${buildDir}/generated/epicRuntime/assets` (commit `05809d3bf2aa3b9a3721d56332132f8d045839be`).
- Hardened `tools/ci/run-epic-android-instrumentation.sh`: it now assembles the x86_64 companion APK first and directly asserts the zipfile helper, stdlib asset, and JNI library entries exist in the actual APK archive before starting connected instrumentation. This separates Gradle source registration/packaging from Android runtime behavior and should fail with a direct archive assertion rather than a vague AssetManager failure (commit `4cd0f3e900938033ca50c14c84b848024ab05493`).
- These changes have been pushed; wait for the fresh Epic workflow. No Epic runtime pass or installable Epic APK is approved yet.


## 2026-11-20 — Android asset-root correction (commit 7d11591d8b701d7e6be4f9388fc8240a49e52bac)
- Epic workflow #44806803150 completed CPython x86_64 and ARM64 builds, archive audits, and managed x86_64 Android runtime prerequisite smoke successfully. It then failed at the stricter APK archive assertion in `tools/ci/run-epic-android-instrumentation.sh`, before connected instrumentation. The companion APK assembled successfully, but the expected `assets/python/lib/python3.15/zipfile/_path/__init__.py` entry was not present.
- Root cause from Gradle source-root layout: `epicbridge/build.gradle` registers `${buildDir}/generated/epicRuntime/assets` as the Android assets source root, but the Sync task was writing files beneath an additional `assets/` child. That causes the APK to contain the wrong nested path (`assets/assets/python/...`) while runtime code correctly requests `python/...` from AssetManager.
- Corrected the Sync destination paths to stage stdlib, zipfile._path, and site-packages directly beneath `generated/epicRuntime/assets/python/...` (JNI remains under `generated/epicRuntime/jniLibs/<abi>`). Existing APK zip-entry assertions remain strict. Commit: `7d11591d8b701d7e6be4f9388fc8240a49e52bac`.
- Next: run the full Epic workflow and require the APK zip-entry assertions, connected Android instrumentation, ARM64 APK build, signer/ABI/ELF/asset/embedded companion audits, and release artifact upload to pass. No Epic-enabled APK is approved yet; no device acceptance testing has occurred.


## 2026-11-20 — Corrected Gradle Sync destination diagnosis (commit 85a5d85d74e278c5025a6ca3bd077e0b078b6748)
- Follow-up run #44809094454 failed earlier than APK packaging: `stageEpicPythonRuntime` threw `Epic CPython runtime assets were not staged` at build.gradle line 137. The prior change removed the `assets/` destination prefix while the task still writes into `build/generated/epicRuntime` and the Android asset source root remains `build/generated/epicRuntime/assets`. Therefore the staged Python tree landed outside the registered asset source root, making the doLast check fail. The previous handoff statement that this prefix should be removed was incorrect.
- Restored `assets/python/...` destinations for stdlib, zipfile/_path, and site-packages. With Sync output rooted at `build/generated/epicRuntime`, these paths place files beneath the registered `build/generated/epicRuntime/assets` source root, while JNI remains in the sibling `jniLibs/<abi>` tree. Commit: `85a5d85d74e278c5025a6ca3bd077e0b078b6748`.
- The earlier #448068 failure's APK-level missing-entry cause still needs confirmation against the corrected task output; next run must inspect the generated directory and APK zip entries. Do not infer that asset staging is fixed until both assertions pass and Android instrumentation succeeds.


## 2026-11-20 — Epic runtime CI retry after Maven Central rate limiting
- Standard MaliWinlator 1.2.1 debug APK workflow #44811089384 completed successfully.
- Epic runtime workflow #44811089521 checked out source commit `85a5d85d74e278c5025a6ca3bd077e0b078b6748`; x86_64 CPython runtime build, pinned Legendary checkout/patch validation, library aggregation tests, host Python smoke, and staging Legendary into the x86_64 test runtime all passed.
- The managed x86_64 Android emulator prerequisite smoke failed before runtime execution because Gradle could not resolve Android testbed dependencies from Maven Central: repeated HTTP 429 Too Many Requests responses. Production ARM64 runtime build, APK packaging, and instrumentation were skipped. This is an external dependency-resolution failure, not a runtime pass or runtime defect diagnosis.
- Reran the failed GitHub Actions job (job ID `136547498390`) to retry dependency resolution. Await its result before touching workflow/source files.
- No installable Epic APK is verified. The asset ZIP-entry assertions remain unresolved until the Android instrumentation packaging stage executes successfully.


## 2026-11-20 — APK archive assertion diagnostics
- Retried Epic runtime workflow #44811089521 (attempt 2). The Maven Central 429 issue cleared. CPython x86_64 runtime build, managed emulator prerequisite smoke, production ARM64 runtime build, and runtime audit all passed.
- Instrumentation job then built `epicbridge-debug.apk` successfully, but exited immediately after `:epicbridge:assembleDebug` and before `connectedDebugAndroidTest`. Therefore the failure is in the three subsequent ZIP-entry assertions, not emulator startup. The previous report that this failure was caused by the instrumentation emulator going offline was incorrect; the offline messages occurred transiently in the earlier prerequisite smoke and that step ultimately passed.
- Updated `tools/ci/run-epic-android-instrumentation.sh` at commit `f3604947263e561dc827a677129989bbd5a260e0` to print a distinct pass/fail for each required APK entry and list related entries when one is absent. Checks remain strict for the CPython zipfile helper, stdlib `os.py`, and x86_64 JNI library.
- Next: allow the workflow triggered by this diagnostic change to run, identify the exact absent archive entry, correct its staging/packaging source, then run instrumentation before proceeding to ARM64 host/companion APK audit.
- No Epic APK is installable/verified yet.


## 2026-11-20 Epic APK asset-merger diagnosis (run 44814450227)
- Latest Epic workflow run `44814450227` failed in the x86_64 instrumentation job after `:epicbridge:assembleDebug` succeeded. The explicit archive audit now reports the first missing entry: `assets/python/lib/python3.15/zipfile/_path/__init__.py`.
- The log confirms the CPython x86_64 prefix contains both `zipfile/__init__.py` and `zipfile/_path/__init__.py`; the runtime package tarball also contains the complete `zipfile/_path` directory. Gradle `stageEpicPythonRuntime` passes its own preflight and post-copy check for the staged `_path/__init__.py`, while the assembled APK omits it. Thus the remaining defect is downstream of the runtime build and Gradle staging: Android asset merging/packaging is dropping this underscore-prefixed directory, not a missing CPython source file or failed CPython build.
- Confirmed native CMake build and `:epicbridge:assembleDebug` succeed for x86_64. Instrumentation is not reached because archive audit correctly blocks the incomplete APK.
- Next experiment: avoid relying on an underscore-prefixed directory as an Android asset-source path. Stage the `zipfile/_path` package into a deterministic archive with a non-underscore asset filename, verify that archive entry in the APK, then extract it into the app-private CPython `lib/python3.15/zipfile/_path` directory before Python initialization. Preserve file hashes and add instrumentation assertions for all extracted helper modules. Do not relax the APK audit or mark the companion installable until extraction and Python import tests pass.
- Workflow link: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44814450227


## 2026-11-20 CPython zipfile asset-path workaround
- Run `44814450227` proved the runtime prefix and staged output both contain `zipfile/_path/__init__.py`, but the APK omitted that directory after Android asset packaging. The APK audit stopped before instrumentation.
- Implemented an isolated packaging workaround: Gradle excludes the original `zipfile/_path` subtree from the broad stdlib copy and stages it under the neutral asset path `zipfile/path_pkg`. The final APK audit now checks `assets/python/lib/python3.15/zipfile/path_pkg/__init__.py`.
- `EpicPythonRuntime.isPackaged()` validates the neutral asset path. During extraction, the bridge renames app-private `zipfile/path_pkg` to CPython's required `zipfile/_path` before runtime completeness checks and Python initialization. The runtime marker hash changes with the staged asset path, forcing a refresh of previously extracted incomplete runtimes.
- Commits: `701ab4f` Gradle staging, `c166e0b` runtime restore, `7364056` archive audit. The three intermediate CI runs were cancelled by subsequent pushes; no validation result exists for this combined source state yet.
- Next gate: run one workflow on the final combined commit; require APK archive audit, extraction/instrumentation imports (`zipfile._path`), and ARM64 production packaging to pass. Do not distribute either APK until the full audit passes.


## 2026-11-21 AES-GCM instrumentation finding and correction
- Run `44816407487` passed all APK archive-entry assertions, installed/launched the x86_64 bridge on Android API 35 emulator, and reached all 4 instrumentation tests. Two runtime tests failed at the negative AES-GCM authentication check.
- The NIST AES-128-GCM positive known-answer vector had already succeeded; the traceback showed failure only when deliberately passing a corrupted tag. JNI maps the Android JCA authentication exception to Python `ValueError`, but `epicbridge_runner._runtime_self_test()` incorrectly caught only `RuntimeError`. The production crypto path was not shown to be failing; the self-test expected the wrong exception type.
- Fixed the negative-tag assertion to catch the shim's documented `ValueError`, preserving the security requirement that a corrupted tag must be rejected. Commit: `84c1d67`.
- Next: run the full Epic workflow again. Require positive NIST vector, corrupted-tag rejection, worker-thread JCA callback, runtime imports, manager lifecycle tests, ARM64 production runtime, companion APK audit, and host APK audit. Do not call this ready until all pass.


## 2026-11-21 — CPython Android prerequisite CI retry
- Epic runtime run `44835831594` at source commit `84c1d677878d1f5bfb0bf98a639a98839cb02334` failed before production ARM64 runtime build or Epic Bridge instrumentation. The x86_64 CPython build, Legendary patch validation, library aggregation regression tests, host Python smoke test, and Legendary staging all passed.
- The failing step was the managed x86_64 Android runtime prerequisite smoke. Gradle dependency resolution was rate-limited by Maven Central HTTP 429 responses (37+ dependencies), not by a source/test assertion. The previous AES-GCM negative-tag correction was therefore not exercised in this run.
- The standard MaliWinlator 1.2.1 debug build run `44835831756` succeeded.
- Requested GitHub to rerun failed jobs for run `44835831594`; await that retry before changing source. No Epic APK artifact was produced or verified; do not install.


## 2026-11-21 — Epic ARM64 host build: missing adrenotools submodule
- Retry attempt 2 of Epic runtime run `44835831594` passed the prior Maven dependency-resolution obstacle and progressed through the managed x86_64 instrumentation stage, but the final ARM64 host APK configure failed at `app/src/main/cpp/CMakeLists.txt:8`: `app/src/main/cpp/adrenotools` had no `CMakeLists.txt`.
- Root cause: Epic workflow's MaliWinlator checkout omitted Git submodules, while the normal APK workflow explicitly uses `submodules: recursive`. `.gitmodules` maps `app/src/main/cpp/adrenotools` to `https://github.com/Pipetto-crypto/libadrenotools.git`.
- Fixed workflow checkout to use `submodules: recursive` and `fetch-depth: 1`. Commit: `e358ccafb0ad8d5f2c3aabf13993e18bb6a772a4`.
- This is a CI checkout fix, not a driver/runtime code change. Re-run Epic workflow to validate recursive submodule checkout, ARM64 host build, APK signer/package/ABI/ELF checks, and artifact upload. No Epic APK is currently verified for installation.

## 2026-11-21 — Epic Android runtime and ARM64 APK CI passed (run 44838701495)
- Epic workflow run #44838701495 (run 213) completed successfully on source commit `e358ccafb0ad8d5f2c3aabf13993e18bb6a772a4`. The recursive-submodule checkout correction is validated; all 32 job steps completed successfully (failure-status publisher skipped as expected).
- Passed gates: CPython 3.15.2 x86_64 runtime build; pinned Legendary 0.21.1 checkout and Android thread-backend static validation; Epic library aggregation regressions; host Legendary smoke; managed x86_64 emulator runtime prerequisite smoke; production Android ARM64 runtime build and audit; Epic Bridge x86_64 Android instrumentation; ARM64 Epic companion + MaliWinlator host APK build; installable artifact uploads; prerelease publication.
- Published prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-e358ccafb0ad . Assets: `app-standard-debug.apk`, `epicbridge-debug.apk`, and `SHA256SUMS`. Debug test APKs only; the release notes state a prior debug certificate may require uninstalling an older debug package. Workflow and archive artifact checks passed; no Xiaomi 15T device acceptance test has yet been performed.
- Workflow: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44838701495 . Host APK SHA-256: `e115bc1d27513d67526c7da3dd5a76a1dafbcb71cfc7835bfb82fb6788e4e773`; companion APK SHA-256: `f96eef82d5dc051ec0ab4e1dc7532ef0030fc854e1d73f65ae3d923b7a54aa76`.
- Next engineering work: inspect release contents and begin source hardening before device testing. First sanitize exception/result handling so private filesystem paths and secrets cannot escape through job status; then design a thread-safe progress/cancellation path across Legendary DLManager, Python/JNI, provider/service, and host UI. Existing synchronized Java runtime calls serialize commands, so cancellation must not be bolted on as a concurrent provider call without resolving GIL/monitor behavior. Preserve current green build and rerun full CI after each source change.
- Acceptance still outstanding: install both APKs on the Xiaomi 15T, verify bridge readiness, Epic auth/sign-out, owned library, install/update/uninstall with progress/cancel/retry, launch through selected Wine container, persistence/shortcuts, and separately validate any official Epic Launcher fallback. Do not call the integration fully implemented until these device tests pass.

## 2026-11-21 — Sanitize Epic failure responses and logs
- After successful full CI/release run #44838701495, hardened the three error surfaces that could expose private paths or request details.
- `epicbridge_runner.py`: command failures now return stable `EPIC_OPERATION_FAILED` and a generic user-facing message; no raw exception string or traceback is serialized. Diagnostic log retains only the bridge command and exception class.
- `EpicRuntimeService.java`: unexpected job exceptions no longer log a Throwable/stack trace or persist `getMessage()`; persisted failure is stable and generic.
- `EpicBridgeProvider.java`: background-job startup failure no longer returns Android's raw exception message.
- Commits: `b29d828` Python response/log sanitization; `c873c61` service failure sanitization; `fa73130` provider startup sanitization. These source commits have triggered a new Epic workflow; inspect its result before further source edits.
- Security note: this deliberately sacrifices detailed exception text in host-visible job results. If richer diagnostics are needed later, add an allowlisted error taxonomy and structured redacted diagnostics rather than restoring arbitrary exception messages/tracebacks.
- Next: require full CI again, then add tests asserting paths/tokens are absent from failure JSON and logs. After that design progress/cancel as a thread-safe end-to-end feature; do not bypass the synchronized Java runtime/JNI GIL constraints.

## 2026-11-21 — Security regression tests and progress/cancel architecture review
- Full Epic run #44841240777 on `fa73130240d0ba91875bba108d43f283c961d1c4` completed successfully: 32/32 steps, including CPython x86_64 + ARM64 runtime builds, managed emulator smoke, bridge instrumentation, ARM64 APK build, and artifact upload. Artifact `maliwinlator-1.2.1-epic-arm64-debug` uploaded (507,991,600 bytes). This validates compilation/instrumentation, not Xiaomi 15T acceptance.
- Added host regression coverage in `epicbridge/poc/test_epicbridge_runner.py` for command failures and malformed JSON payloads: assert generic `EPIC_OPERATION_FAILED` response and no private path, exception class, or traceback leakage. Commits `f3b5f25` and follow-up `d8d34ba` refine malformed-payload test case.
- Architecture review: install path currently does `dlm.start(); dlm.join()` synchronously in the Python command. Android provider `job_status` only reads SharedPreferences; service holds one worker executor. `EpicPythonRuntime.call` is synchronized and native JNI dispatch enters CPython; adding a second provider-to-Python cancellation call would block on the Java monitor and is unsafe to treat as an immediate cancel.
- Legendary Android patch already converts DLManager and workers to threads, with cooperative `stop()`, shared condition notifications, queue sentinels and bounded joins. Existing manager progress is emitted via Legendary UIUpdate/status_queue in the upstream manager; it is not currently surfaced through Epic job status or UI.
- Recommended safe design before implementing controls: keep the install Python call as the sole owner of the DLManager; use an app-private per-job control/status file or other lock-safe shared state as the cross-layer channel. DLManager's main loop periodically checks a cancellation flag and calls its existing cooperative stop path; it writes atomic progress snapshots (download bytes, processed files, percentage, phase) for Java's job_status to read. Java cancellation only flips the per-job flag; it must not make a second synchronized JNI call. Ensure helper/writer threads drain/terminate before shared mmap closes, and report a distinct cancelled terminal state. This design still needs careful implementation and unit/instrumentation coverage.
- No progress/cancel source implementation is claimed yet. Next CI run should validate the two new security tests; then implement file-backed control/status incrementally with bounded polling, atomic writes, cancellation race tests, thread teardown tests, and UI progress/cancel. Maintain a single active install job invariant.


## 2026-11-21 — Security regression CI passed (run 44843099083)
- Epic runtime workflow #44843099083 completed successfully on source commit `d8d34ba5f23a1b2c91bb3279ed88d0858d77c890`. All 32 steps completed; the failure-status publisher was skipped as expected. The two new Python security regression tests ran within the successful Epic library aggregation regression step.
- Confirmed passing gates include CPython 3.15.2 x86_64 + ARM64 runtime builds/audits, Legendary 0.21.1 Android thread-backend validation, host Legendary smoke, managed x86_64 emulator runtime prerequisite smoke, Epic Bridge x86_64 Android instrumentation, ARM64 host/companion APK build, and prerelease/artifact uploads.
- Epic artifacts: `maliwinlator-1.2.1-epic-arm64-debug` (507,991,296 bytes; artifact ID 14682087664) and `maliwinlator-cpython-3.15.2-android-arm64-poc` (49,956,684 bytes; artifact ID 14682331754). Run: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44843099083 .
- Standard APK workflow #44843099072 also passed all 13 steps on the same source commit; artifact `maliwinlator-1.2.1-standard-debug-d8d34ba5f23a1b2c91bb3279ed88d0858d77c890` (446,688,471 bytes; artifact ID 14682225663). Run: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44843099072 .
- These are CI build/instrumentation results, not Xiaomi 15T device acceptance. The existing Epic feature gaps remain: no end-to-end progress UI, cancellation UI, full auth/library/install acceptance, or validated game launch. Do not mark Epic complete.
- Next implementation: build the file-backed per-job control/status channel described above, with single-owner DLManager, atomic progress snapshots, cooperative cancellation, distinct terminal cancellation state, race/teardown tests, then expose provider/service/UI controls. Preserve synchronized JNI ownership; do not call Python concurrently to cancel.


## 2026-11-21 — First Epic install progress/cancellation implementation
- Source commits on `development/maliwinlator-1.2.1-bootstrap`: Python job control `5ad4d56`, phase snapshot fix `c1e68da`, regression tests `843d078`, Android private job-file provisioning `d3110b9`, provider cancel/status IPC `b6e815a`, host progress/cancel UI `e56f6a3`, resume-button fix `7b6f4d0`.
- `epicbridge_runner.py` now polls an app-private control JSON file while the Legendary Android thread-based download manager runs. A cancellation request calls the downloader's cooperative `stop()`, then waits for the manager to finish its worker/mmap teardown before returning a distinct `cancelled` result. It does not invoke a competing JNI/Python call.
- The runner publishes atomic, sanitized status snapshots (phase and optional Legendary UIUpdate progress/speeds) via temp-file + fsync + replace. Only known scalar fields are serialized; status snapshots do not include game paths or secrets. A transient or malformed control file fails closed (does not cancel).
- EpicRuntimeService creates per-job control/status files under its private `files/epic_jobs` directory and injects their paths only for install jobs. EpicBridgeProvider exposes `cancel_job` by writing the control flag and returns bounded progress JSON from the status file. The host Epic tab polls progress and now shows a cancel action; it restores that action when resuming an active job. Terminal `cancelled` is recognized separately from success/failure.
- Added Python regression coverage for atomic sanitized status snapshots and a cancelled install skipping `core.install_game`. These changes have NOT yet passed CI; the next action is to inspect the new Epic + standard workflows and diagnose any failures. No device validation has occurred.
- Remaining validation before calling this production-ready: inspect CI logs; add/verify Android provider/service cancellation instrumentation; prove status queue progress values against pinned Legendary 0.21.1; test cancellation before/during/after downloader startup and during worker teardown; verify service-death/restart semantics; test on Xiaomi 15T with a real install/update and ensure no mmap/worker leak or partial-install corruption. Cancellation is cooperative and may take time while in-flight network/write operations unwind.

- Follow-up static review caught and corrected Java string escaping in the control/status JSON writers. The committed Java literals now emit valid JSON objects (single escaped quotes in source); this correction is in final source commit `1ecb0425a892e29d64f7c682aae302fbebc445d9`. Earlier workflow runs were cancelled by subsequent commits; final-source CI is now queued as Epic run #44846128113 and baseline run #44846128234. Treat only those final-source runs as authoritative.

- Additional safety refinement: the service persists each job's command, and provider IPC now permits cancellation only for `install` jobs (uninstall is not interruptible). Job status exposes `can_cancel`; the host UI displays the cancel control only while a cancellable install is active, including resumed jobs. Commits: `ffc2a22`, `d2d05f0`, `7bb3db5`.
- These changes supersede the previously listed final-source runs. Current authoritative CI is Epic run #44846229536 and standard APK run #44846229545, both targeting `7bb3db5229cfaa7b0c70ebf406cbc2a0fbcd1aec`.

- Static concurrency review found that the host's single-thread IO executor is occupied by the long-running `waitForJob` poll loop. Cancellation IPC is now dispatched on a separate short-lived `EpicJobCancel` thread so the request cannot queue behind the poller. Commit `ff97d26`. This supersedes CI target `7bb3db5`; wait for the newest run pair before treating the implementation as build-validated.

- Final source tip for this implementation pass is `ff97d26b5f19901f5858f2330465f296a4e5b0d8`. Current validation pair: Epic run #44846313318 (https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44846313318) and baseline run #44846313298 (https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44846313298). Both were still running at the latest poll; do not treat older cancelled runs as evidence.


## 2026-11-21 — Progress/cancellation CI passed; atomic control-file hardening in progress
- Final-source Epic workflow #44846313318 and standard APK workflow #44846313298 both completed successfully on ff97d26b5f19901f5858f2330465f296a4e5b0d8.
- Epic CI completed all 32 steps: pinned CPython x86_64 + ARM64 runtime build/audit; Legendary 0.21.1 thread-backend validation; library aggregation regressions; host runtime smoke; managed Android x86_64 prerequisite smoke; four Android instrumentation tests; ARM64 host/companion APK build; package/signature/ABI/native dependency checks; artifact upload and prerelease publication. The instrumentation run explicitly reports 4/4 tests finished and BUILD SUCCESSFUL.
- Standard APK CI completed all 13 steps, including embedded companion APK and shared-signer audit.
- Artifacts: Epic ARM64 debug bundle maliwinlator-1.2.1-epic-arm64-debug (artifact ID 14684401186, 507,997,953 bytes, SHA-256 971220f48217d3b77a7060e3fda67dd42597ee9af564cfd749373f7c5ee1bc0a); CPython ARM64 POC artifact ID 14683433510; standard debug APK artifact ID 14681974697. Runs: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44846313318 and https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44846313298.
- Important coverage boundary: the four Android tests exercise runtime packaging/imports, JCA AES-GCM positive and corrupted-tag behavior, worker-thread crypto callback, Legendary thread backend, downloader failure cleanup, anonymous mmap, host authorization, and single-job reservation. They do NOT yet exercise provider-to-service cancellation IPC, progress snapshots, cancellation races, or UI behavior. The new Python tests cover sanitized atomic status snapshots and cooperative cancellation of a mocked downloader; no real Epic network install or Xiaomi 15T acceptance has been performed.
- Static review identified a cancellation reliability race: provider previously truncated and rewrote the control JSON in place. If the Python poller read during the write, its fail-closed parser could ignore that transient malformed file and miss the request. Replaced this with a same-directory temp file, fsync, and atomic rename. Also narrowed provider can_cancel to an actually active running install job.
- New source commits: d4a992e atomic cancel publication and bec8983 Android instrumentation coverage for atomic replacement/no temp-file residue. These changes supersede the previously green source tip; wait for the new Epic and standard CI pair before considering them validated.
- Next: inspect CI for the atomic cancellation change, then add end-to-end Android provider/service tests for install-only cancellation authorization, terminal/non-active rejection, progress JSON exposure and cancel state. Validate service teardown and job restart semantics. Device acceptance on Xiaomi 15T remains mandatory for Epic login/library/install/cancel/update/uninstall/launch. Debug artifacts are not production releases.


## 2026-11-21 — Cancellation boundary race fix
- The atomic cancellation revision `bec8983` passed the standard MaliWinlator APK workflow (#44848591941). Epic runtime workflow #44848591984 was still running at last poll; it must be checked for final success before treating the atomic-file instrumentation test as validated.
- Further review of `_install()` found two remaining cancellation windows: a request could arrive during metadata/installation analysis before `dlm.start()`, or after the final downloader worker exited but before the `while dlm.is_alive()` loop observed the request. In the first case unnecessary worker/mmap startup could still occur; in the second case installation could proceed despite a cancellation request.
- Added a pre-start cancellation gate (returns cancelled without starting downloader threads) and a post-join cancellation check (skips `core.install_game`). Added Python regression tests for both boundaries. Source commit `fe60411`; tests commit `13678fd`.
- These commits supersede the green/active CI source tips and trigger fresh Epic runtime and standard APK workflows. No device acceptance has been performed; cancellation remains cooperative and waits for worker teardown before downloader-owned mmap state is released.


## 2026-11-21 — Correct stale cancellation regression fixture
- Epic runtime run #44848813612 on source commit `13678fd50ca639eb29adb0f77187dae51d5bd615` failed one Python regression: `test_cancelled_install_stops_downloader_and_skips_install_game`. The implementation's new pre-start cancellation gate correctly returned before starting the downloader because the test had pre-populated the control file with `cancel=true`; the old fixture still expected the previous behavior (start downloader, then cancel). This was a stale test setup, not evidence that cooperative stop was broken.
- Updated the fixture to model the intended in-flight scenario: first cancellation poll returns false (pre-start gate), second returns true after the fake downloader starts. The test still asserts `stop()` was called, `install_game` was skipped, and the terminal snapshot is cancelled. Commit: `b6a6cb9b8945c41e4b8027f2e0bff52e0bdcec34`.
- The standard APK workflow #44848813634 was still in progress at last inspection. The failed Epic workflow published its failure status automatically. New Epic + standard workflows for the test correction must pass before this source revision is validated. No APK from the failed Epic run is installable/verified; no Xiaomi 15T device acceptance has been performed.


- Follow-up source review found the new Android instrumentation test used `File` without importing `java.io.File`. Added the missing import in commit `61a9033868bab26f397c0d66e830bf123c5732b1`. This was caught by static inspection before relying on the pending CI result; it supersedes the just-started CI pair at `b6a6cb9`. Fresh Epic and standard workflows for the latest branch head must pass. Provider/service end-to-end cancellation IPC coverage remains a gap; current Android test only validates atomic control-file replacement, not the actual authorized provider call path.


## 2026-11-21 — Wire actual Legendary progress updates into Epic jobs
- Inspection of the exact pinned Legendary 0.21.1 source (`42f6bdeadde3a9526dc8eb713763476999ac217b`) found that `DLManager.status_queue` is only populated when `LegendaryCore.prepare_download(status_q=...)` receives a queue. Epic's runner had not supplied one, so its new progress UI could only show phase changes; download percentage and throughput would never arrive. The pinned `UIUpdate` fields are confirmed as `progress`, `download_speed`, `write_speed`, `read_speed`, and `memory_usage`.
- Fixed by passing a thread-safe `queue.Queue()` as `status_q` to `prepare_download`. The Android patch already changes Legendary core's Queue import from `multiprocessing.Queue` to `queue.Queue`, matching the thread-based manager. Commit: `5e9eca3aca8e3071e9c7c5ceb16d47a3704a0f04`.
- Added a regression test that asserts a queue is injected, fake Legendary `UIUpdate` metrics are drained, and the progress snapshot includes the percentage and download speed. Commit: `fad0cd61037a9019dbf75818801e7d3bce1138be`.
- This supersedes the CI runs for earlier commits. Fresh Epic runtime and standard APK workflows must pass on the latest source. Progress is now wired in source but not yet CI-validated or device-validated. Provider/service cancellation IPC authorization and service restart behavior remain test gaps; no Xiaomi 15T acceptance test has been performed.


## 2026-11-21 — Fix progress queue import scope after CI
- Latest Epic runtime workflow #44849647302 failed its Python regression suite: all four install-control tests raised `NameError: Queue is not defined` when `_install` called `prepare_download(status_q=Queue())`. Root cause: `Queue` was imported locally inside `_runtime_self_test()`, not at module scope. This is a real implementation failure, not a flaky test.
- Added module-level `from queue import Queue` in commit `9af13111d5e67ec85c279c5719e720314616a3c9`. The standard APK workflow #44849647306 passed all 13 steps, but it does not validate the Epic Python runtime and does not clear the Epic failure. The runtime workflow #44849647302 failed; no Epic APK from it is installable/verified.
- New Epic runtime and standard APK CI must run on the import fix. The progress queue regression test remains enabled. No device acceptance has been performed.


## 2026-11-21 — Correct Epic progress regression fixture
- Latest Epic runtime run #44850116987 failed only in `test_legendary_status_queue_is_connected_and_progress_is_published`; the test supplied `job_status_path` without the paired `job_control_path`. Production `_install()` correctly rejects mismatched control/status paths. The earlier diagnosis of a progress-queue implementation fault was incorrect; the module-level Queue import fix worked and the failure was a stale/incomplete test fixture.
- Standard APK workflow #44850117068 passed all 13 steps.
- Updated the progress test to supply both temporary control and status paths, preserving the production invariant. Commit: `8f4f0dd7eb7f374c3fc63082bcd440b49b730a84`.
- Fresh Epic runtime and standard APK CI must pass on this revision. No Epic artifact from failed run #44850116987 is verified for installation. Xiaomi 15T device acceptance remains outstanding.


## 2026-11-21 — Progress queue fixture correction passed full CI
- Source revision `8f4f0dd7eb7f374c3fc63082bcd440b49b730a84` corrected the progress regression fixture to supply both paired private job paths (`job_control_path` and `job_status_path`).
- Epic runtime workflow #44850573339 completed successfully: 32/32 steps. Managed Android x86_64 instrumentation ran five tests and passed; CPython 3.15.2 x86_64 + ARM64 runtime packaging/audits, Legendary 0.21.1 tests, ARM64 host/companion APK build, signer/ABI/native dependency audits, and artifact uploads also passed. Run: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44850573339 .
- Standard APK workflow #44850573454 completed successfully: 13/13 steps. Run: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44850573454 .
- Artifacts: Epic debug bundle `maliwinlator-1.2.1-epic-arm64-debug`, artifact ID 14685766210, 507,998,374 bytes, archive SHA-256 `bd824708210cda4729867e0af42ea6f0c1249cf4470e4743b918cb8d25c99983`; CPython ARM64 POC artifact ID 14686180188, 49,958,118 bytes, SHA-256 `6967f649f7c46b960abd7cb9ecd09f279b298f3daf951c1c022cc27455474855`; standard debug bundle artifact ID 14684818389, 446,694,569 bytes, SHA-256 `451a877a0c3c3157c06ce57efd1b494560454c8de4954e2bd432f68023f2cea0`.
- These remain debug/test artifacts and managed-emulator CI evidence, not real Xiaomi 15T acceptance. Current Android instrumentation still does not execute an authenticated host-UID provider call through the full provider-to-service cancellation path, nor does it validate actual download progress/cancel races or service process-death behavior. Do not call Epic complete.
- Next: add focused instrumentation for provider job-state/cancel gating and progress JSON parsing, improve service interruption cleanup semantics, then rerun Epic + standard CI. Device acceptance still needs login/signout, library, install/progress/cancel/retry, update, uninstall, selected-container launch, persistence/shortcuts, and separate fallback-launcher validation.


## 2026-11-21 — CI passed; harden bounded progress snapshot reads
- Epic runtime workflow #44850573339 passed 32/32 steps on source `8f4f0dd7eb7f374c3fc63082bcd440b49b730a84`; managed Android x86_64 instrumentation ran 5 tests successfully. Standard APK workflow #44850573454 passed 13/13 steps. Both produced debug artifacts; details and SHA-256 values are recorded above in the session report. This validates emulator/build gates only, not Xiaomi 15T device acceptance.
- Follow-up review found provider progress reads used a file-length precheck followed by an unbounded read. A concurrent file growth could bypass the precheck and return a larger-than-intended snapshot. Extracted `readProgressSnapshot(File)`, which enforces the 8192-byte cap while streaming and treats missing/read-failure/oversized snapshots as best-effort absence. Provider now uses that helper.
- Added Android instrumentation for normal snapshot reads, >8192-byte rejection, and missing-file handling. Commits: `576666c` provider helper and bounded read; `1ae9b96` instrumentation; `8b298eb` corrected the test's literal JSON fixture after source inspection.
- These changes are not CI-validated yet. Fresh Epic runtime and standard APK workflows should run on the latest branch tip. Provider-to-service cancellation IPC and real device network install remain untested; do not mark Epic complete.


## Engineering step log — 2026-11-21: bounded progress snapshot CI and reservation race coverage

- The bounded progress snapshot reader and its instrumentation test were validated by Epic runtime workflow **44852218634** (32/32 steps, success) and standard APK workflow **44852218596** (13/13 steps, success). The Epic workflow includes the packaged CPython/Legendary ARM64 and x86_64 runtime checks and the instrumented test stage.
- Added an instrumentation stress test, `concurrentJobReservationsAdmitExactlyOneWinner()`, which releases 16 competing threads simultaneously against the single-job reservation gate and verifies exactly one reservation wins and that the slot is reusable after release.
- Test commit: `9f7d0ae1a3600dc6ad9f77598bc70d1dc03aef7f`.
- This new concurrency test has **not yet run in CI**; the next CI pair must validate it. The previous passing runs do not include this latest test commit.
- Lifecycle review note: `EpicRuntimeService.onDestroy()` marks persisted running jobs as interrupted, clears in-memory reservations, interrupts the executor, and releases the wake lock. This is process/service teardown recovery, not proof that an in-flight native Python call can be cooperatively cancelled by Java. The established cancellation path remains the private atomic control-file signal polled by Python; do not introduce a second JNI cancellation call while the synchronized runtime call owns the Python GIL.
- Authorization note: the provider enforces the exact host package by Binder UID package lookup and also declares a signature-level provider permission. Existing instrumentation only unit-tests the package allowlist helper; it does not yet exercise a real cross-package Binder call under the host UID.
- No Xiaomi 15T device acceptance was performed in this step. The passing workflows produce debug/test artifacts, not a production-signed release.

### Next actions
1. Run and inspect Epic runtime plus standard APK workflows for commit `9f7d0ae1a3600dc6ad9f77598bc70d1dc03aef7f`.
2. If green, record test count/artifact evidence, then continue reviewing service restart and cancellation edge cases.
3. Keep real device validation gates explicit: login, library, install/cancel/retry, update, uninstall, launch, and persistence.


## Engineering step log — 2026-11-21: concurrency stress test passed

- Epic runtime workflow **44854159631** passed **32/32** steps on source commit `9f7d0ae1a3600dc6ad9f77598bc70d1dc03aef7f`. The job log explicitly reports `Epic Bridge instrumentation passed.`; the new 16-contender reservation stress test is included in that instrumentation suite.
- Standard APK workflow **44854159589** passed **13/13** steps on the same source commit.
- Epic debug APK artifact: [workflow artifact 14687550549](https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44854159631/artifacts/14687550549), 507,998,244 bytes, archive SHA-256 `ede2dc731a88333733d64e003aa893d1c637ba4561cd7821dbaece88815a8c04`.
- Separate CPython 3.15.2 Android ARM64 POC artifact: [workflow artifact 14687451137](https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44854159631/artifacts/14687451137), 49,955,763 bytes, archive SHA-256 `57e07e6c17a65f8eaf1ab94eb45c39c342e88b6d82e52d07113fb2a36340d856`.
- CI also published prerelease [maliwinlator-1.2.1-epic-9f7d0ae1a360](https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-9f7d0ae1a360). These are debug/test outputs; the CI result is not Xiaomi 15T acceptance and not a production-signed release.
- The workflow automatically published `PROJECT_EPIC_CI_STATUS.md` in commit `e1e7f21` after the successful build.
- No device-side Epic login/library/download/game-launch test was performed in this step.

### Next engineering focus
1. Inspect the actual CI instrumentation invocation/report to record exact test cases and improve provider/service lifecycle coverage.
2. Investigate service stop/restart edge cases and ensure stale persisted running state cannot be mistaken for a live job.
3. Device acceptance remains required for cross-package provider permission, Epic authentication, library, install/cancel/retry, update/uninstall, launch and persistence.


## Engineering step log — 2026-11-21: unify cancellation eligibility

- Refactored cancellation gating into `EpicRuntimeService.isJobCancellable(jobId, state, command)`. Both provider `cancel_job` and `job_status.can_cancel` now use the same rule: the job must be in the in-memory active set, persisted state must be `running`, and command must be `install`.
- Added instrumentation coverage for active versus inactive reservations and running, queued, complete, cancelled, and uninstall states.
- Source commits: service `814c58d75ea184b77eb9cb16a003a3da32f44788`; provider `113707fc922bb9c7327d79d1d6af0d51cbcc9721`; instrumentation `d6c13af90df73429da17bdb74cee6e4965496ae8`.
- This is a consistency/testability refactor, not proof of real cross-process cancellation or downloader teardown. Await CI on the latest source commit before treating it as validated.


## 2026-11-21 — Cancellation eligibility validation passed CI

- Latest source commit `d6c13af90df73429da17bdb74cee6e4965496ae8` passed Epic runtime workflow **44856059185** (32/32 steps, success) and standard APK workflow **44856059211** (13/13 steps, success).
- The Epic job log confirms the Android Gradle build completed successfully. The available log excerpt does not establish that the new cancellation-eligibility instrumentation test was actually executed; retain that as a test-coverage verification item rather than claiming it passed.
- Epic ARM64 debug bundle: artifact **14688426677**, 507,997,301 bytes, archive SHA-256 `b29b92bf2de6de0073e254d32a4dd499860d3d41ec84515c8e64d4f81e6082be` (expires 2026-12-21).
- CPython 3.15.2 Android ARM64 POC: artifact **14688322562**, 49,959,133 bytes, archive SHA-256 `1661742bd524f7661204c767e2738aa2189535ac66b27cb377a5663034da96a3` (expires 2026-12-05).
- Run links: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44856059185 and https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44856059211.
- These are CI/debug artifacts, not a production-signed release and not Xiaomi 15T Epic acceptance. Real provider-to-service cancellation, live network install, and service process-death recovery remain unverified.
- Next review: verify the test task/report is part of the Epic workflow (and that `cancellationEligibilityRequiresAnActiveRunningInstall` ran), then inspect provider Binder authorization and service restart semantics.


### CI log clarification — instrumentation execution confirmed

- Follow-up inspection of the completed Epic job log found the managed Android emulator summary: **15 Tests, 8/8 completed, 0 skipped, 0 failed**, followed by `Epic Bridge instrumentation passed.` This confirms the instrumentation stage did execute on the latest source commit `d6c13af90df73429da17bdb74cee6e4965496ae8`; the earlier note above saying the available excerpt did not establish execution is superseded.
- The suite-level report does not print individual JUnit method names in the captured summary, so retain the 8/8 aggregate as the precise evidence rather than asserting a named test from that line alone.


## 2026-11-21 — Add deterministic stale-job recovery coverage

- Reviewed provider authorization and lifecycle handling. The companion provider is exported only behind the signature-level `com.maliwinlator.epicbridge.permission.ACCESS` permission, the host declares that permission, and provider code additionally allowlists the host package resolved from the Binder calling UID. The service itself remains non-exported. A real cross-package Binder authorization test is still a separate gap.
- Extracted the provider's process-recovery rule into package-visible pure predicate `shouldMarkJobInterrupted(state, startedAt, now, active)`. It preserves the existing 30-second startup grace and only marks an orphaned persisted `running` job interrupted after the grace period when the in-memory active set does not contain it.
- Added deterministic instrumentation coverage for exact grace-period boundary, expired grace, active job, terminal state, missing start timestamp, and backwards clock input.
- Source commits: provider refactor `ab490c0ab93bcd4a930f56b81b2a6d61f05e713d`; instrumentation test `933e66926e62271725f788b821cf8b8b1cb06cc8`.
- These commits supersede the previously green CI source tip. Await new Epic runtime and standard APK workflows before claiming this regression test is validated. This is testability/edge-case hardening, not proof of process-death recovery under real Android lifecycle pressure.
- Next: verify both workflows, then prioritize a genuine host-to-provider Binder authorization test and real install cancellation/process-death acceptance. Xiaomi 15T Epic login/library/download/launch acceptance remains outstanding.


## 2026-11-21 — Stale-job recovery CI passed; UID allowlist coverage expanded

- CI for source commit `933e66926e62271725f788b821cf8b8b1cb06cc8` completed successfully: Epic runtime workflow **44904004951** and standard APK workflow **44904005022** both succeeded. Epic workflow log explicitly printed `Epic Bridge instrumentation passed.` after successful Android instrumentation; ARM64 debug bundle artifact **14707426901** (507,997,580 bytes, SHA-256 `88eabeb69853b57ca222b879bb1c9b27de6be925659869c1bfc6f0bfc32298b7`) and CPython ARM64 POC artifact **14707242287** (49,959,552 bytes, SHA-256 `2f20b4032cb5ab4bed70adb99d61531f2721ef30ca53a41a326ab688f3f1e8ad`) were uploaded.
- Extracted provider UID-package authorization loop into `isAllowedHostUidPackages(String[])`, preserving the requirement that the calling UID package list contain exactly the trusted host package. Added instrumentation coverage for mixed package lists, no host package, empty arrays, and null arrays.
- New source commits: provider refactor `12da81fb3c0b3311a560b58043c41518fe87d0af`; instrumentation test `3da114bcd56a6ec11525d4255a2f71ab98d8990f`. These are newer than the green CI source and must pass fresh CI.
- Security boundary note: this unit-level allowlist coverage does not substitute for a cross-package Binder call from an independently installed host UID. Provider manifest remains exported behind the signature permission and provider repeats caller package validation; host manifest requests that permission. A real host-to-provider instrumentation test is still a separate integration gap.
- Device acceptance remains outstanding: Xiaomi 15T Epic authentication, library sync, real network install/cancel/retry, update/uninstall, and launching from the selected Wine container.


### Follow-up test refinement

- Extended `providerAuthorizesUidOnlyWhenItsPackageSetContainsTheHost` to cover the single-package host UID case in addition to mixed package sets, untrusted-only sets, empty sets, and null. Source commit `e2243e72c8d45a73100d068817fe11186eb5787c` is the latest code change and requires fresh CI. This remains unit-level package-list coverage, not a separate-process Binder integration test.


## 2026-11-21 — Add genuine cross-package Epic provider Binder test

- Added a test-only Android application module `epicbridge-host-test` whose application ID is exactly `com.maliwinlator.app`, requests the companion's signature permission, and runs instrumentation under a separate host UID. The test calls `content://com.maliwinlator.epicbridge.provider` using the host process ContentResolver and asserts protocol v2, provider acceptance, and packaged runtime readiness.
- Updated `tools/ci/run-epic-android-instrumentation.sh` to build/install the host fixture and its instrumentation APK after the companion instrumentation suite, then invoke the test via `am instrument`. The test validates both Android signature permission enforcement and the provider's Binder UID package allowlist, assuming both debug APKs are signed by the same CI debug keystore.
- Added module to `settings.gradle`. New source commits: settings `4101bbfd2e1f56805e1b4b752e07add83435aa2e`; host module files `15d039a4a9678b14b0dd49a70871fe16a2daf130`, `3abacebba11baec29bf829b95875fabfb2628e68`, `83b9ce675d015ddba57bec6bbe6de0a13bf5c253`, `44caeffdd7a71b43d9f0e7ad6449a638e0032a8b`; CI script `b0341272254c1621865d7f0ad096ab5b767f00b5`.
- This is a CI-only host identity fixture, not the production host APK and not a release artifact. Fresh CI must confirm the provider accepts the distinct host UID; if installation or instrumentation fails, diagnose exact signer/manifest/task issue before treating it as a regression.


## 2026-11-21 — Cross-package Binder test exposed and corrected CI false-positive

- Epic workflow **44906105551** and standard APK workflow **44906052301** both completed successfully as GitHub jobs. Standard build passed 13/13 steps.
- The Epic job's companion instrumentation passed, but the newly added host-UID test did **not** pass: signedHostUidCanCallExportedEpicProvider failed with IllegalArgumentException: Unknown authority com.maliwinlator.epicbridge.provider. The script nevertheless printed its success banner because adb shell am instrument returned a successful shell status despite JUnit's FAILURES!!! result. Therefore this run is **not** evidence of cross-package Binder success.
- Root cause candidate identified from the log: after Gradle's :epicbridge:connectedDebugAndroidTest stage, the companion package was not available to the separately installed host fixture at the point of the provider call. The test script had built the companion APK but did not explicitly reinstall it before invoking the host fixture.
- Corrected tools/ci/run-epic-android-instrumentation.sh in commit **085dd2a9934d8cd6ac372e56fa6f0c343949af14**: explicitly install the companion APK before host fixture/test APK installation; assert both companion and host packages resolve through pm path; capture instrumentation output and require the JUnit success marker OK (1 test), rejecting FAILURES!!! and failed instrumentation status codes. CI can no longer report this integration test as passed solely because adb's process exit status is zero.
- Later validation: workflow **44924071336** passed the signed host-to-companion Binder instrumentation test (`signedHostUidCanCallExportedEpicProvider`, 1/1). See the CI validation entry below.
- Xiaomi 15T Epic acceptance remains outstanding; no authenticated login, owned library, download/cancel/retry, update/uninstall, or selected-container launch has been demonstrated.


## 2026-11-21 — Cross-package Binder integration passed corrected CI

- Epic runtime workflow **44907363258** completed successfully on source commit **085dd2a9934d8cd6ac372e56fa6f0c343949af14**: **32/32** steps, no failed steps. Run: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44907363258 .
- The managed Android emulator log confirms the companion instrumentation suite passed, then the separate host-UID fixture ran `signedHostUidCanCallExportedEpicProvider` and returned **`OK (1 test)`**. The script emitted **`Epic Bridge cross-package Binder integration passed (1/1).`** only after checking the JUnit output. This validates the test fixture's signed host package identity, signature-level provider permission, exported provider lookup, Binder calling UID package allowlist, protocol v2 response, and runtime readiness assertion in CI.
- This explicitly closes the earlier CI false-positive: run **44906105551** had reported a successful job despite JUnit failure `Unknown authority`; the corrected script now reinstalls both packages, asserts both package paths, captures instrumentation output, and rejects JUnit failure markers.
- Artifacts from the successful run: Epic ARM64 debug bundle artifact **14708507801**, 507,997,375 bytes, archive SHA-256 `05f93fbe8af2dc5778a9a5c6473133aa04a8aed655c0d079c0fb7045b29278c9` (expires 2026-12-21); CPython 3.15.2 Android ARM64 POC artifact **14708522700**, 49,960,373 bytes, archive SHA-256 `318445593fa6f714205468f4e15206d59bec97914c915698cf813747a36dc24b` (expires 2026-12-05).
- These are CI/debug outputs, not production-signed release artifacts and not Xiaomi 15T device acceptance. The integration test validates a status/provider call, not the complete install cancellation path or authenticated Epic download lifecycle.
- Next engineering focus: inspect and test provider-to-service job lifecycle/cancellation through the now-validated cross-package boundary, strengthen real downloader cancellation/restart coverage, and continue device acceptance. Required device gates remain Epic login/signout, owned library, install progress/cancel/retry, update, uninstall, selected-container game launch, shortcut persistence, and independent official-launcher fallback validation.


## 2026-11-21 — Make Epic cancellation phase-aware (validated by subsequent CI)

- Code review found that the provider considered every active `running` install cancellable, including the final `installing` phase after Legendary's downloader thread had exited. A late cancel could therefore be accepted even though the worker was no longer in the cooperative downloader stop loop.
- Changed `EpicRuntimeService.isJobCancellable` to require an active running install plus progress phase `preparing` or `downloading`. The provider now parses the bounded progress snapshot for both `cancel_job` and `job_status.can_cancel`; missing/malformed snapshots and phases `stopping`/`installing` fail closed. Python publishes `installing` before its final cancellation-file check and returns cancelled if a request was already present.
- Added instrumentation coverage for allowed and rejected phase boundaries, and for progress phase parsing (valid, missing, and malformed snapshots).
- Commits: service helper `5fd117ba58156fd8f0da0495b2f6035a7bda2dbd`; provider gating `25a1da1d298b3f1ee0308d20bc2e41e425ca035e`; phase boundary tests `b02298167c3d07a74a8a9ced124b81a7836adf22`; Python final-phase check `503226338b1fb06d590fec1c73a9a45799613bab`; progress phase parser tests `de53a8c5cd0c63ee0b5ef3ca15a5e8f1815992be`.
- Subsequent validation: Epic runtime workflow **44924071336** passed host regressions and Android instrumentation on source commit `3390b95a717121d41bc79903c524120d75c3717d`; standard APK workflow **44924071347** also passed. The later JNI `flock(2)` arbitration section below supersedes the earlier non-atomic phase-aware implementation.
- Next: inspect CI results, then add deterministic tests for cancellation during metadata preparation and downloader shutdown; retain device validation for actual network transfer, cancellation, retry/resume, and service lifecycle.


### Follow-up: cancellation before downloader allocation

- Continued review found that `_install()` validated the cancellation paths and checked the control file only after `core.prepare_download()`. That call constructs the Legendary downloader and can allocate shared-memory/mmap resources even when the host has already requested cancellation during metadata lookup.
- Moved paired control/status path validation to the beginning of `_install()`, added a pre-metadata cancellation check, and re-checks immediately after game metadata lookup but before `prepare_download()`. Cancellation already waiting therefore returns without constructing downloader state or allocating its shared buffer.
- Extended the Android runtime self-test with a fake core whose `prepare_download()` raises if called; a pre-signalled cancel must return `cancelled` and publish a cancelled status snapshot. The fake does not contact Epic or start worker threads.
- Source commit: `412e39a377c00885d6421499a000d3f3c8e50629`. This extends the phase-aware cancellation work above and is not CI-validated yet; a fresh Epic runtime workflow on this source tip must execute the expanded runtime self-test.


- Refined the runtime self-test to cover both early-cancel cases: an already-signalled request skips metadata lookup entirely, while a request created during fake metadata lookup is observed before `prepare_download()`. The fake asserts that downloader/shared-memory setup is never reached in either case.
- Test refinement commit: `75cbdc478c09527be5fc7530e5ccf6ce8317e4be`; fresh Epic runtime CI is required on this exact source tip.

## 2026-11-21 — Correct downloader-cancellation regression test after early guards

- Epic runtime CI run `44909466762` failed in the host-side Legendary regression suite, specifically `test_cancelled_install_stops_downloader_and_skips_install_game`, at the assertion that the fake downloader's `stop()` had been called. The standard Android APK workflow `44909466750` passed all 13 steps.
- Root cause: the test's mocked `_cancel_requested` sequence `[False, True]` was written before `_install()` gained two earlier cancellation gates (before metadata and after metadata). The second `True` now correctly cancels before downloader creation, so the assertion about stopping an active downloader was no longer exercising its intended scenario.
- Updated the test sequence to `[False, False, False, True]`: initial guard clear, post-metadata guard clear, post-preparation guard clear, then cancellation in the active downloader loop. The separate metadata-stage fake-core self-tests remain responsible for proving cancellation before downloader allocation.
- Test-only commit: `6706768df9db4dd2d436182d23120c6db49efe6e`. Fresh Epic CI is required; no claim of validation until its regression suite and Android instrumentation pass.


- Follow-up source review caught that the first test edit had matched the earlier, similarly shaped `test_cancel_arriving_as_downloader_exits_skips_install_game` instead of the intended active-downloader test. Restored the earlier test's two-poll sequence and applied the four-poll sequence only inside `test_cancelled_install_stops_downloader_and_skips_install_game`. This prevents moving the mismatch to a neighboring regression test.
- Corrected test commit: `afde4d0fd38f51c90007fb574063e71f4bc32dfa`. The workflow already running for commit `6706768...` cannot validate this later correction; await the newly triggered run for `afde4d0...`.

## 2026-11-21 — Make downloader cancellation stop idempotent

- Review of the active download loop found that once the control file requested cancellation, `_install()` called `DLManager.stop()` on every 500 ms poll until the workers exited. The stop method should be a one-shot request; repeated calls are unnecessary and may not be idempotent across Legendary implementations.
- Changed the loop to issue `stop()` only on the first observed cancellation, while continuing to join/poll until all workers have exited before allowing mmap/shared-buffer cleanup. Added a regression test with a fake downloader that remains alive for three join cycles; it asserts exactly one `stop()` call, three joins, and no `install_game()` call.
- Commits: runner `6be3ed712daa7c028d86576ad0579f93b156af10`; regression test `c0d994cf1da36182ad2ad7ddab1c035acd8a880a`. CI validation pending.

## 2026-11-21 — Fix Android instrumentation compile failure in cancellation test suite

- Epic runtime CI **44912860300** failed after the host-side Python tests passed, the x86_64 runtime prerequisite smoke test passed, and the ARM64 CPython runtime build completed. The failure was isolated to Java compilation of `:epicbridge:compileDebugAndroidTestJavaWithJavac`.
- Compiler diagnostics identified one malformed escaped JSON fixture in `EpicRuntimeInstrumentedTest.progressSnapshotReaderAcceptsBoundedDataAndRejectsOversizedFiles()`: the Java source contained two backslashes before embedded quotes, terminating the string incorrectly. Corrected it to the normal Java JSON literal `"{\"state\":\"running\"}"`.
- Fix commit: **4c1a01c56a802d8bc628d1829f00f7f4145c1f2b**. Fresh Epic CI is required; this is a source-level correction, not yet a passing build.
- The same review cycle confirmed the pending provider/Python TOCTOU issue: separate progress and control files do not make the transition to `installing` atomic with provider cancellation acceptance. Do not report that race fixed. Next design work should introduce one shared synchronization protocol and prove Java/Python lock interoperability on Android before relying on it.


## 2026-11-21 — Fix stale cancellation helper calls in Android instrumentation

- Fresh Epic runtime CI **44914838447** reached Android instrumentation compilation after host Python regression tests, x86_64 Android runtime prerequisite smoke, ARM64 CPython build, and runtime package audit all passed. Compilation then failed with two Java errors in `EpicRuntimeInstrumentedTest.java`: two legacy three-argument calls to `isJobCancellable(jobId, "running", "install")` remained after the helper was changed to require an explicit phase argument.
- Corrected both stale calls to pass `null`, preserving their intended assertions that a missing phase is not cancellable. Source commit: **8f95bd006b14b625bba5f6981209dcca61d0c43d**.
- Standard APK workflow **44914838450** passed **13/13** steps. Epic workflow **44914838447** failed only at Android instrumentation Java compilation; do not treat the Epic APK as validated or install any artifact from that run. Fresh Epic CI on commit 8f95bd0 is required.
- The separate provider/Python cancellation phase-transition TOCTOU race remains unresolved and must not be represented as fixed. Next: verify the fresh instrumentation compile and test results, then design a single atomic phase/cancel arbitration protocol with deterministic Android coverage.


## 2026-11-21 — Epic ARM64 runtime CI green after instrumentation signature fix

- Fresh Epic runtime workflow **44916107616** completed successfully: **32/32 steps**, including host Python regression tests, managed Android x86_64 runtime/JNI instrumentation, ARM64 CPython 3.15.2 packaging, ARM64 host+companion APK assembly, embedded companion byte comparison, and package/signature/ABI/assets/ELF dependency-closure audits.
- The Android instrumentation emitted **`Epic Bridge instrumentation passed.`** The dedicated cross-package Binder fixture executed `signedHostUidCanCallExportedEpicProvider` and returned **`OK (1 test)`**; the workflow also explicitly emitted `Epic Bridge cross-package Binder integration passed (1/1).`
- Standard baseline APK workflow **44916107607** also passed **13/13 steps**.
- ARM64 Epic debug artifact: `maliwinlator-1.2.1-epic-arm64-debug`, artifact ID **14711797992**, archive SHA-256 **5f6ff9658838bc5017fd235d03fc781a0629e28a5518d92c7da3c8ede729b4a2**. Artifact URL: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44916107616/artifacts/14711797992
- ARM64 CPython 3.15.2 POC artifact ID **14711289492**, archive SHA-256 **e9240e9fcb6fc339b15670685a9afbbafeae86fb62263837d2b5c9e8ce7fef85**.
- CI published prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-8f95bd006b14
- These are CI/debug artifacts only. Real Xiaomi 15T validation remains outstanding: install both APKs with matching signatures, companion runtime initialization, Epic login/sign-out, library sync, install progress/cancel/retry, update, uninstall, and launch through the selected Wine container. Do not call Epic integration complete before those tests pass.
- The cancellation phase-transition TOCTOU was subsequently addressed with JNI Linux `flock(2)` for Java and Python `fcntl.flock()` on the same per-job lock inode. The interoperability test passed on the managed Android emulator in workflow **44924071336**; the physical-device download/cancel lifecycle remains unverified.

- The published release's individual APK SHA-256 values were independently confirmed from the release asset metadata and generated `PROJECT_EPIC_CI_STATUS.md`: host `app-standard-debug.apk` **57d55b77c331b054dea5f9ed4d25cc955f18a290723a99113faa84b6bc7860e8**; companion `epicbridge-debug.apk` **e0e70155e59679dda2954252d49cdd5effa09d227593270ab469782beecde700**. These differ from the Actions ZIP artifact digest above (which hashes the artifact archive, not either APK).


## 2026-11-21 — Implement shared-lock arbitration for Epic install cancellation (CI validated; device acceptance pending)

- Closed the previously documented provider/Python cancellation TOCTOU design gap in source: added package-private EpicJobLock, which opens a per-job lock inode and takes Linux flock(LOCK_EX) through JNI in libepic_python. Android's Java Os API does not expose flock(). The service creates the lock file in its private epic_jobs directory, passes its absolute path to the install runtime, and persists that path with the job metadata.
- EpicBridgeProvider.cancel_job now acquires the same lock before re-reading the progress phase, active job state, command, and control path. It publishes the atomic cancellation control snapshot while still holding the lock.
- Python _install() now requires control/status/lock paths as a complete absolute-path set for managed installs. After downloader shutdown and worker-error checks, it takes fcntl.flock(LOCK_EX) on the same inode, checks the cancellation control, and publishes phase=installing before releasing the lock. Therefore the two outcomes are serialized: provider wins and cancellation is visible before installation, or Python wins and the provider observes installing and rejects cancellation.
- Added a Python thread-exclusion regression test and an Android instrumentation test that holds the Java lock while the embedded CPython runtime attempts to acquire it, using a marker file to prove Python reached the blocked acquisition before Java unlocks. Added a narrow internal job_lock_probe command for that instrumentation test; it does not initialize Legendary or access Epic credentials.
- Commits currently on the branch: lock helper cebf5267d5ae35913915e22842d84e9b4f903643; service path plumbing e53acf34535649974ad23849cc09e6735d40fbf1 and lock-file creation correction a1ab3152ea49e402272c15dd1b9f1ec7721e5dd1; provider arbitration d98c85c4505e4327133b46c9795466a70502ceac; Python lock protocol 0820b2849bc557d4a4dafba20e67aa88e48947f8 and test probe b79a973540cb106e8dff9ae46586147cae9a7cb1 / 8e68a4fffd2970787be5a946f48c015437534c0b; Python regression tests ba9acd51c527921078f9b8714f0595b2604af087 and fixture completion 8db41f4d00f3cc5c1b365c4ddb3074c2cfc7052f; Android interoperability test af494bf7b4f57aa58b102b7f2b1fd383064141ee.
- Validation completed in Epic runtime workflow **44924071336**: host Python regression tests passed; Android instrumentation passed all 11 tests, including `javaAndEmbeddedPythonFlockInteroperate`; ARM64 packaging and static audits passed. Standard APK workflow **44924071347** passed 13/13 steps. This proves the shared lock primitive interoperates and the tested phase arbitration works in the managed emulator; it does not prove live Xiaomi 15T download cancellation or service process-death behavior.
- Real Xiaomi 15T acceptance is still required for actual download cancellation, transition to installation, retry, update/uninstall, and game launch. No vendor/system Vulkan driver replacement is involved.


### Android SDK compile correction for flock constants

- The first standard APK workflow for the lock change, run 44922375771 on commit 8db41f4d00f3cc5c1b365c4ddb3074c2cfc7052f, failed at :epicbridge:compileDebugJavaWithJavac because Android SDK OsConstants does not define LOCK_EX or LOCK_UN. This was a compile-time API-name issue; the log did not reach instrumentation.
- Updated EpicJobLock to use the Linux flock operation values (LOCK_EX=2, LOCK_UN=8), while retaining OsConstants.O_CREAT and O_RDWR for open flags. Correction commit af211785b11efa54e3a660978d7839cac9961062. Fresh Epic runtime and baseline workflows must validate this correction; Java/Python flock interoperability remains unproven until the instrumented test passes.


### JNI flock implementation correction (2026-11-21; validated by workflow 44924071336)

- Fresh standard APK workflow #44922520808 on af211785b11efa54e3a660978d7839cac9961062 found that Android's android.system.Os also lacks a flock(FileDescriptor,int) method, not just named LOCK_EX/LOCK_UN constants. The compile log explicitly reported missing Os.flock at EpicJobLock.java lines 28 and 39. The first correction was therefore incomplete.
- Replaced the Java Os-based attempt with native Linux flock(2) in the existing libepic_python JNI library. epic_python.c now opens the lock inode with O_CREAT|O_RDWR|O_CLOEXEC, acquires LOCK_EX with EINTR retry, and releases LOCK_UN/closes the descriptor through matching JNI methods. EpicPythonRuntime ensures libepic_python is loaded before exposing package-private acquire/release wrappers; EpicJobLock now holds the native file descriptor as a long. This is the same kernel flock primitive used by Python fcntl.flock, not Java FileChannel record locking.
- Native implementation commit 174ded71564387ce9fb2120e2cc0ad9f2f10f22f; Java runtime wrappers commit 7caa039f0c6755229ca8aa5c169d3aae566c578b; EpicJobLock replacement commit 39fb38c842968f4cca0c88897e6a17cb59b64990.
- Prior Epic runtime workflow #44922520805 had reached the Android instrumentation stage before the corrected commits superseded it. The run was cancelled by subsequent commits; no instrumentation result from that earlier run is accepted as proof. This implementation subsequently compiled and passed Java↔CPython flock contention instrumentation on the managed Android emulator in workflow 44924071336. The release and remaining physical-device acceptance scope are recorded below.


## 2026-11-21 — Validate JNI/Python flock cancellation arbitration and publish Epic build

- Epic runtime workflow **44924071336** completed successfully on source commit **3390b95a717121d41bc79903c524120d75c3717d**: **32/32 steps**, including host Python regressions, managed x86_64 Android runtime smoke, Android instrumentation, ARM64 CPython packaging, ARM64 APK assembly, and package/signature/ABI/assets/ELF dependency-closure audits. Workflow: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44924071336
- Android instrumentation reported **`Epic Bridge instrumentation passed.`** All **11** Epic Bridge Android instrumentation tests completed. In particular, `javaAndEmbeddedPythonFlockInteroperate` passed: Java held the per-job lock through JNI `flock(2)`, the embedded Python runtime reached and blocked on `fcntl.flock(LOCK_EX)`, and then acquired the lock after Java released it. This is direct managed-emulator evidence that both runtimes arbitrate on the same kernel lock primitive/inode.
- The separate signed host-to-companion Binder instrumentation also passed: `signedHostUidCanCallExportedEpicProvider` returned success (1/1 test).
- Standard APK workflow **44924071347** passed **13/13** steps on the same source commit.
- CI published ARM64 debug prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-3390b95a7171. Actions Epic APK artifact ID **14715660547**: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44924071336/artifacts/14715660547. CPython ARM64 POC artifact ID **14715507049**; ZIP SHA-256 **71c821a870f445cedc6416c3b6b405d6fd69b457e27ab5da9618baa2db3929f8**.
- CI-generated `PROJECT_EPIC_CI_STATUS.md` records host APK SHA-256 **13f6916b200024b910b2bc2cd00b721484af017ca2b2f09f2f3bdeb53a1cffc9** and companion APK SHA-256 **2797eea77bd98713dcdde0a5abba5484640f6055acb811dfe35351785b5f5b41**. These are individual APK hashes, not the Actions artifact ZIP hash. Both packages use matching debug signing certificates; these are not production-signed release builds.
- The workflow published CI status commit **c82ef83** (`ci: publish verified Epic APK artifact [skip ci]`) after building source commit 3390b95a. Treat 3390b95a as the exact application source revision validated by this run; c82ef83 adds the CI status report only.
- **What this proves:** Android JNI/native lock support compiles and loads in the managed emulator; native `flock(2)` and Python `fcntl.flock` interoperate; the provider/Python lock contention test passes; ARM64 packaging and static audits pass.
- **What this does not prove:** real Xiaomi 15T behavior under a live Epic download, cancel/retry, service process death, huge game installation, actual Epic account authentication, library sync, game launch, or the official launcher fallback. The cancellation race fix is now validated at the lock-interoperability/instrumentation layer, but the complete physical-device download/cancel lifecycle still needs acceptance testing.
- Next: inspect the generated status and release assets, then device-test the two APKs on the Xiaomi 15T. Preserve the existing app data and install the matching host/companion debug pair. Exercise runtime self-test, login/sign-out, library, a small install, cancel during active download, confirm cancellation before install, retry, update/uninstall, and launch through a selected Wine container. Collect redacted logs only for failures. Do not declare Epic integration complete until all required device acceptance gates pass.

## 2026-11-21 — Android 16 foreground-service compatibility audit

- Reviewed the companion manifest and Gradle configuration: `EpicRuntimeService` is declared as a non-exported `dataSync` foreground service, the required `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` permissions are present, and the companion currently targets API 34.
- Android's six-hour-per-24-hour `dataSync` foreground-service timeout is documented for apps targeting API 35 or higher. It is therefore not currently activated solely by this companion's target SDK 34, but must be addressed before raising targetSdk to 35+: implement the API-35 `Service.onTimeout(int,int)` path and stop safely, or redesign long transfers around an appropriate user-initiated data-transfer/job API. Reference: https://developer.android.com/develop/background-work/services/fgs/timeout
- Android 16 additionally applies runtime quotas to background jobs started from foreground services regardless of target SDK; the current Legendary transfer loop runs inside the foreground service rather than delegating transfer work to WorkManager/JobScheduler. Reassess if that execution model changes. Reference: https://developer.android.com/develop/background-work/services/fgs/changes
- Lifecycle review reconfirmed that `onDestroy()` marks persisted active jobs interrupted and calls `shutdownNow()`, but Java interruption does not prove an in-flight JNI/CPython call or downloader worker has stopped immediately. Managed-emulator lock tests do not substitute for physical Xiaomi 15T process-death and long-download testing.

## 2026-11-21 — Live Epic download notification progress (first implementation)

- Real-device feedback from the Xiaomi 15T: three small Epic titles installed and launched successfully, but Winlator becomes extremely laggy during downloads and the companion foreground notification only says that it is downloading, with no percentage. The notification is visible when opening the companion directly; therefore notification permission/visibility is not the primary defect. The missing progress indicator is an implementation gap.
- Added a one-second main-thread progress poll to `EpicRuntimeService`. It reads the existing atomically-written per-job status snapshot and refreshes the ongoing foreground notification with the current phase, percentage, and download speed when available. It uses an indeterminate progress bar when no numeric percentage is present and removes the poll when the job finishes.
- The existing Python runner already drains Legendary's `UIUpdate` status queue and atomically publishes `progress`, `download_speed`, `write_speed`, and `read_speed`; the service previously ignored these snapshots and only displayed a static notification. The host Epic tab also polls provider status every 1.5 seconds and updates its status label when a progress snapshot exists.
- Implementation commit: `0bbefc7e48d56abdf5ed82844ae4fd886b8e0b6d`. Both the standard APK workflow **44946308620** and Epic runtime workflow **44946308614** passed on this exact commit. This is build/CI validation only; the updated companion notification has not yet been reinstalled and checked on the Xiaomi 15T.
- Download responsiveness remains an open issue. The Android Legendary patch caps worker count at four and shared buffer at 512 MiB; the runner currently requests up to four workers and 512 MiB. The shared buffer is mmap-backed, so its configured capacity is not equivalent to immediately committed physical RAM. Do not blindly lower the buffer or worker count without measuring throughput, memory pressure, and UI responsiveness. Next optimization step: inspect the Android shared-buffer implementation and Legendary worker scheduling, then add reproducible synthetic tests/benchmarks before changing defaults.
- Device acceptance already demonstrated account recognition, library refresh, container selection, and three successful game installs/launches. Still outstanding: progress percentage/speed on the notification, live Epic-tab progress, cancellation on-device, retry/update/uninstall, process-death recovery, and download responsiveness.

## 2026-11-21 — Conservative Android Epic download defaults

- Follow-up to Xiaomi 15T acceptance: user reports severe Winlator UI/device lag during Epic downloads even for three lightweight titles, although all three downloads/installations completed and games launched successfully. The notification itself was visible from the companion but had no progress percentage.
- Reviewed the Android-specific Legendary patch: the shared transfer buffer is a single anonymous mmap, so the configured capacity is virtual mapping capacity and not necessarily fully resident RAM. Nevertheless, the host UI explicitly requested four worker threads and 512 MiB for every install, the maximum allowed by the runner. That is an aggressive fixed default for a phone-first app and can increase concurrent network, decompression, memory-touch, and storage pressure.
- Changed the normal Android install request to **2 download workers and 256 MiB**; changed the runner fallback defaults to the same values. Explicit callers may still request up to four workers and 512 MiB, preserving a path for later throughput benchmarks. This is a conservative responsiveness experiment, not yet a proven optimum.
- Added a Python regression assertion that the default runner path passes two workers and a 256 MiB shared buffer to Legendary's prepare_download(). Commits: runner defaults `c0e52a3b50748bdd5f2f6b0160c756cff03422ee`; host install payload `4306ca72a526a278501dbb517e111abb38ba7dc5`; regression assertion `0f0f47ac25628cd83b23bb2522bfce94aad303b1`.
- Required validation: Epic runtime workflow host regression + Android packaging/instrumentation and standard APK build must pass on the final source revision. Physical Xiaomi 15T retest must compare responsiveness and install completion against the previous four-worker/512-MiB build; record download time, UI responsiveness, and any failures. Do not claim performance improvement until that A/B test is complete.

## 2026-11-21 — Downloader-default regression test correction

- The standard APK build passed on commit `0f0f47ac25628cd83b23bb2522bfce94aad303b1`, but Epic runtime workflow **44948674574** failed one newly-added assertion. Inspection of the exact Legendary `prepare_download()` call showed the test used the wrong keyword name and units: the runner passes `max_shm=256` (MiB), not `max_shared_memory=268435456` (bytes). This was a test defect; no production-code failure was indicated by that assertion.
- Corrected the regression assertion to require `max_shm == 256`. Fix commit: `e9108152ca45e62773bd13cb2e6fa1dcbfddba26`.
- Revalidation is running: Epic runtime workflow **44949169654** and standard APK workflow **44949169656** on the corrected commit. Do not distribute the optimization APK until both pass.

## 2026-11-21 — Xiaomi 15T Epic progress/responsiveness test build ready

- Corrected revision `e9108152ca45e62773bd13cb2e6fa1dcbfddba26` passed both required workflows: Epic runtime/CPython ARM64 workflow **44949169654** and standard APK workflow **44949169656**. Epic runtime workflow includes host Python regressions, managed Android runtime/instrumentation, ARM64 packaging and APK audits.
- Matching debug-signed host + companion APKs published at release tag `maliwinlator-1.2.1-epic-e9108152ca45`: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-e9108152ca45 . Assets: `app-standard-debug.apk`, `epicbridge-debug.apk`, `SHA256SUMS`. This build includes notification snapshot polling and the conservative two-worker/256-MiB downloader defaults. APKs are test builds, not production-signed.
- CI artifact IDs: Epic paired APK archive `14725387799` (SHA-256 `adb1185c0a2666fa7f827ef9036732bf440b631ab92597789d38cf0e36900592`); standard APK archive `14725305591` (SHA-256 `3e8bf3391cdec57b73495aa335f5ab5fae0d573a9ee9d153512ddeece08078d9`). These are workflow archive hashes, not individual APK hashes; use release `SHA256SUMS` for APK verification.
- Xiaomi 15T has already demonstrated Epic sign-in, library refresh, container selection, and three lightweight game installs and launches. User observed severe Winlator lag during downloads and no percentage in the static notification. The new build is specifically for acceptance-testing notification progress and responsiveness; improvements are not confirmed until the user retests.
- Device test plan: install both APKs from the same release over the existing versions (do not uninstall/clear app data); open Epic Bridge directly once, then Winlator Epic tab; select the existing container; install one small game while watching the notification and Winlator responsiveness. Record whether notification shows a determinate percentage/speed or indeterminate bar, whether Epic UI remains responsive, and whether installation completes. Keep existing games and containers intact.


## 2026-11-21 — Single-package Epic runtime architecture audit

- Continued repository inspection after the Xiaomi 15T result: progress percentage is now visible in the companion notification, downloads/installations complete, but Winlator remains severely laggy during transfers. This is a confirmed UI/device responsiveness defect; the two-worker/256-MiB experiment did not resolve it. Do not attribute the lag solely to cross-package IPC without profiling: the current host polls provider status every 1.5 seconds, the service polls a status file every second, and the Python/Legendary transfer + file-write/decompression work is the more likely load source.
- Confirmed build structure on branch `development/maliwinlator-1.2.1-bootstrap`: `:epicbridge` is currently an **Android application** module (`com.android.application`, application ID `com.maliwinlator.epicbridge`), not a library. `:app` packages its separately built debug APK as `assets/epicbridge.apk` through `embedEpicBridgeApk`; the host UI installs/uninstalls that APK and communicates through `EpicBridgeClient` / exported signature-protected ContentProvider.
- The companion module owns the complete runtime packaging pipeline: CPython 3.15 ARM64 prefix staging, `assets/python`, `libepic_python.so` plus dependent shared libraries, JNI `flock(2)` support, Legendary Python packages, `EpicRuntimeService`, and private `epic_jobs` state under its own `getFilesDir()`. A correct one-package migration must move all of these together; moving only the service or provider would leave the native runtime/assets unavailable or create mismatched private paths.
- Migration direction: convert the runtime module to an Android library and depend on it from `:app`; merge its Java, JNI, Python assets, permissions, and service manifest into each host flavor. Run the foreground runtime service in a dedicated host-package process (for example `:epic_runtime`) so Legendary/CPython never execute on the Winlator UI thread. Replace the companion installer and cross-package provider permission with an internal, non-exported same-package API; keep job snapshots/locks in host app-private storage and preserve the existing atomic `flock` cancellation protocol. The official Epic Launcher fallback remains a separate guest/container option, not a separate bridge APK.
- Important flavor constraint: `:app` defines standard, Ludashi, and AnTuTu application IDs. Internal provider/service authorities and package checks must use `${applicationId}` or be removed, not hard-code `com.maliwinlator.app`; the library must not introduce a competing application ID. Keep the host package's existing namespace and all existing app data paths stable.
- No source migration has been committed yet. This entry records verified architecture and constraints, not an implementation claim. Next implementation work: map every host `EpicBridgeClient` call site and existing Epic instrumentation dependency; convert the module and manifests in a small compile-tested step; then remove the companion installer/provider boundary and add tests proving runtime assets/JNI load from the host APK, service isolation, lock interoperability, job status, cancellation, and all three host flavors. Retain the old companion path until the unified APK passes CI and Xiaomi 15T acceptance.
- Latest validated source revision remains `e9108152ca45e62773bd13cb2e6fa1dcbfddba26`; workflows **44949169654** (Epic runtime) and **44949169656** (standard APK) passed. The user's latest device report confirms notification percentage and successful installs, but not a responsiveness improvement.


### Host-side Epic call-site audit (same migration)

- Located the actual UI entry point at `app/src/main/java/com/winlator/cmod/EpicFragment.java` (the earlier guessed `EpicGamesFragment` path does not exist). It is the only host integration surface identified so far; `EpicBridgeClient` is imported there.
- The UI's long-running install/uninstall command path is already dispatched to the fragment's single-thread `io` executor; `waitForJob()` polls every 1.5 seconds on that worker, and cancellation deliberately uses a separate short-lived thread so it cannot queue behind the polling worker. Do not move these operations onto the main thread during migration.
- `refreshStatus()` currently performs `EpicBridgeClient.getStatus()` synchronously from the fragment lifecycle path, so the provider's packaging/native-library readiness probe can block the UI during tab initialization. This is a real avoidable main-thread IPC/asset-probe cost. The unified implementation should make readiness checks asynchronous and cache immutable packaging/crypto readiness after runtime-process startup.
- The current UX still includes companion-specific states and actions: install/replace APK at `installBridge()`, open companion activity at `openBridge()`, and dual-app storage-access messaging. In the one-package design, replace these with host-only runtime readiness and the host app's own all-files-access settings intent; do not retain a dummy companion launcher or APK-install flow.
- Job IDs are persisted by the host fragment in host SharedPreferences while the companion currently stores job state in its own SharedPreferences. During migration these stores must be consolidated deliberately (or read legacy state once) so in-progress jobs are not silently lost on upgrade. Existing installed games, Epic auth data, and container associations must remain intact; do not change the guest install paths.
- The runtime provider currently uses a hard-coded host package check for `com.maliwinlator.app`, while the host has three application ID flavors. The internal runtime API should validate same-UID/same-package access without breaking Ludashi/AnTuTu builds, or remove the provider in favor of a private bound service with explicit caller checks.


## 2026-11-21 — First implementation slice: isolated Epic runtime library

- Created a new Gradle Android library module `:epicruntime` (commits `32e93275`, `d936c61c`) that compiles the existing Epic runtime Java sources from `:epicbridge` while excluding its companion-only `MainActivity`. It reuses the pinned CPython staging output and CMake JNI source instead of duplicating the runtime implementation.
- The library manifest declares `EpicRuntimeService` and `EpicBridgeProvider` as non-exported components in a dedicated `:epic_runtime` process. Its provider authority is parameterized as `${applicationId}.epicprovider` so the eventual host integration can support standard/Ludashi/AnTuTu application IDs. This is an initial library packaging target; it is not yet merged into `:app` and must not be treated as a usable one-APK build.
- Added the Android library plugin alias and module inclusion (`9a268f4f`, `d380d231`), and updated Epic runtime CI (`d43593f9`) to assemble the AAR with the same ARM64 CPython prefix used by the companion and audit that it contains Python assets, `libepic_python.so`, and `EpicRuntimeService` classes.
- Existing companion APK and host app build paths are intentionally unchanged in this first slice. This preserves the current working two-APK device fallback while the library target is independently compiled and inspected.
- CI runs started: Epic runtime workflow **44956104170** (source `d43593f9`) and baseline APK workflow **44956097926** (source `d380d231`). The Epic workflow is still running at the time of this note; AAR packaging is not yet verified. Earlier runs triggered by intermediate commits were cancelled by subsequent pushes and are not test evidence.
- Next gated steps after AAR CI passes: update provider same-UID authorization and runtime authority lookup for all app flavors; switch the host Epic client to the private in-package provider; migrate EpicFragment away from companion install/launcher flows and dual-app storage messaging; add `:epicruntime` as an app dependency; then audit manifest permission merges, duplicate classes/assets/native libs, all three flavor APKs, runtime-process startup and JNI/Python extraction. Do not remove the companion fallback until those checks and Xiaomi 15T acceptance pass.

- Follow-up authorization change `ef68ae60`: `EpicBridgeProvider.isAuthorizedHost()` now accepts same-UID calls (required for a future non-exported provider inside any host flavor) while preserving the existing explicit `com.maliwinlator.app` package gate for cross-package calls to the companion. The helper's old package-list predicate remains unchanged for existing tests. This does not yet switch the host client to the new authority.


## Engineering step log — 2026-11-21: Gradle plugin classpath conflict

- The latest CI attempts failed before producing an installable APK. Both the standard host build and Epic runtime instrumentation reported: `InvalidPluginRequestException` because the Android Gradle plugin was already on the classpath with an unknown version.
- Root cause was confirmed in `app/build.gradle`: it declared AGP 8.8.0 through a legacy module-level `buildscript` classpath and then requested `com.android.application` through the plugins DSL, while the root build already declared the version-catalog plugin alias with `apply false`. This mixed resolution path made Gradle see an already-loaded plugin without a version.
- Fixed in commit `652fcf0626792bf14f6e5a80d167a04940c4e0ca`: removed the redundant module `buildscript` classpath block and changed the app plugin declaration to `alias(libs.plugins.android.application)`, matching the root version catalog and the other modules. The unused Kotlin plugin classpath declaration was removed with the legacy block; no Kotlin plugin was applied by this module.
- Failed CI runs: [standard APK #44956174302](https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44956174302) and [Epic runtime #44956174310](https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44956174310). Neither run produced a verified artifact. Do not install artifacts from them.
- Next gate: rerun both workflows and confirm the host APK and Epic runtime AAR/instrumentation build. The single-package runtime is still a separate library module and is not yet wired into the host app; the companion APK remains the current fallback. Device download-lag improvement remains unproven.


## Engineering step log — 2026-11-22: second Gradle plugin-resolution diagnosis

- Follow-up CI runs after commit `652fcf0` completed but still failed. The earlier diagnosis was incomplete: removing the app module's legacy AGP classpath fixed that module's mixed declaration, but did not register the Android library plugin for the new `:epicruntime` module.
- Exact failure in both [standard APK run #44957755949](https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44957755949) and [Epic runtime run #44957755882](https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44957755882): `epicruntime/build.gradle:1`, plugin request `com.android.library:8.8.0`, rejected because AGP was already on the classpath with an unknown version. The Epic run had progressed through CPython packaging and x86_64 runtime build; it failed when the Android instrumentation build configured the library module. The host build also configured all included modules and hit this same configuration error.
- Corrected root plugin registration in commit `aa9295cfdde7a918fe791abd2f492cc1bf747fad`: root `build.gradle` now declares both `libs.plugins.android.application` and `libs.plugins.android.library` with `apply false`. This makes the exact AGP version available to both application and library subprojects through the version catalog. The previous app-only plugin declaration remains in place; no module is applying a different AGP version.
- Neither failed run produced a verified installable artifact. Do not install artifacts from these runs. The next gate is rerunning both workflows after this root plugin registration change.
- The single-package `:epicruntime` module remains unintegrated into `:app`; the separately installed companion remains fallback. Device download responsiveness is not yet fixed or demonstrated by the module split.


## 2026-11-22 — Epic AAR CI audit false-negative corrected

- Rechecked the two latest validation workflows after the Gradle plugin registration change. Standard host APK workflow **44959995628** passed and completed `:app:assembleStandardDebug`.
- Epic runtime workflow **44960034527** reached the final Android build/audit step. CPython x86_64 and ARM64 packaging, managed Android instrumentation, `:epicruntime:assembleDebug`, and `:app:assembleStandardDebug` all completed; the AAR contained `assets/python/lib/python3.15/os.py` and `jni/arm64-v8a/libepic_python.so`.
- Exact failure cause: CI ran `unzip -l epicruntime-debug.aar | grep EpicRuntimeService`. AAR Java bytecode is nested in `classes.jar`, so the outer AAR listing exposes only `classes.jar`, not individual `.class` paths. The runtime class was therefore not proven missing; the audit inspected the wrong archive layer.
- Corrected the workflow in commit **6d01678bffc005ea4b24e587000719f1a4ab1343**: extract `classes.jar` from the AAR and inspect it with `jar tf`, explicitly requiring both `com/maliwinlator/epicbridge/EpicRuntimeService.class` and `EpicBridgeProvider.class`. This strengthens the audit instead of weakening/removing the check.
- The failed run's own CI status update commit was **187805d**. No artifact from run **44960034527** is considered installable or verified. The passing standard APK is also not a one-package Epic build: `:epicruntime` is still not wired into `:app`.
- Next gate: rerun Epic runtime workflow on the AAR-audit fix and require a green result. If it passes, inspect the AAR and merged manifests before integrating the library into the host application. Preserve the companion APK fallback until the unified host build and Xiaomi 15T device acceptance both pass. No claim is made that the download-lag problem is solved.


## 2026-11-22 — Host runtime dependency build and CI workflow recovery

- Added `implementation project(':epicruntime')` to `app/build.gradle` in commit **868139dffd985d96c27fe0ff41a9e78359d3ef2f**. This makes the host application depend on the runtime Android library; it does **not** yet switch `EpicBridgeClient` or `EpicFragment` to use the in-package provider/service. The companion app and current cross-package UI path remain intact as the fallback.
- Standard host APK workflow **44964489199** passed on that source commit and uploaded artifact **14730514861**. This proves `:app:assembleStandardDebug` compiles with the library dependency.
- Epic runtime workflow **44964489203** on the same commit completed all substantive build/test/package steps successfully: CPython x86_64 and ARM64 runtime builds, managed x86_64 Android runtime smoke, Epic Bridge Android JNI instrumentation, `:epicruntime:assembleDebug`, and host/companion Gradle APK assembly. Both APK and CPython artifacts uploaded:
  - paired debug APK archive: artifact **14731726432**, SHA-256 `69891d50c83e5f15154e3d6cad0ac47a18c504039f5ac2460243055b498d14f7`;
  - CPython runtime archive: artifact **14731352655**.
- The only failed substantive step in run **44964489203** was GitHub prerelease publication. `gh release create` returned HTTP 403, `Resource not accessible by integration`, despite the workflow declaring `permissions: contents: write`. This is a GitHub Actions integration permission boundary, not an application build failure. The artifact exists, but it must not be described as a published GitHub Release.
- The run's automated failure-status writer incorrectly labelled the APK artifact unverified because it keys off the final job result, which included the release step failure. Corrected `PROJECT_EPIC_CI_STATUS.md` in commit **b8570067a76b2fd5fce2335d1b9db3603a44ee30** with the actual step-level result and artifact references.
- Important validation gap: run **44964489203** used the previous valid workflow revision, which did not audit that the host APK actually merged `libepic_python.so`, the Python stdlib assets, and the flavor-specific provider authority. The host build passed, but merged host packaging was not independently verified in that run.
- During an attempted expansion to audit all three app flavors, workflow-file edits became malformed. The two immediate runs **44964492709** and **44964541417** failed before creating jobs; they were workflow parse/registration failures, not Gradle builds. Inspection found a fragment of the failed-status shell block accidentally inserted into the build script around the flavor loop (including a stray `done`), corrupting the YAML's embedded shell content.
- Recovered the workflow from the last known-good revision **6d01678bffc005ea4b24e587000719f1a4ab1343** in commit **a1429e24ffb1c21e50464108935b9f426fe21251**, and marked Release publication `continue-on-error: true` so a GitHub Release API permission denial will no longer turn a successfully built/uploaded artifact into a red build. The first recovery run **44967357829** was superseded/cancelled by the next small workflow update.
- Added a focused host merge audit in commit **0d9fe5228b5f39a16e12dbb88fbac90e7194fe85**. It now explicitly requires the host APK to contain `lib/arm64-v8a/libepic_python.so`, `assets/python/lib/python3.15/os.py`, and the merged `com.maliwinlator.app.epicprovider` manifest authority. This is intentionally limited to the Standard flavor for now; expand to Ludashi/AnTuTu only after the standard packaging gate is green and inspect the actual flavor application IDs before adding checks.
- Current validation run **44967380029** is queued/running on commit **0d9fe522**. It must pass before claiming host runtime assets/manifest are merged correctly. The source module is still not the active Epic UI runtime until the client/provider/service migration, process startup tests, same-package authorization checks, and lifecycle/cancellation tests are completed.
- Device status: no new Xiaomi 15T test has been run for this dependency-only build. Download-induced Winlator lag remains unresolved; packaging the runtime into the host APK is not evidence of a responsiveness fix. Keep the companion fallback and do not remove it or replace the system/vendor Vulkan driver.


## 2026-11-22 — Epic readiness probe moved off the Winlator UI thread

- Reviewed the actual host call path after wiring `:epicruntime` as a Gradle dependency. `EpicFragment.refreshStatus()` synchronously called `EpicBridgeClient.isInstalled()` and `getStatus()` from `onResume()`; `getStatus()` crosses into the companion provider, which performs runtime/native packaging readiness checks. This was an avoidable main-thread package-manager/IPC/asset-probe path and could contribute to tab-open jank.
- Implemented in commit **8ba1fe25de08582554c83fdd3627da3a5e9ebd73** (`app/src/main/java/com/winlator/cmod/EpicFragment.java`): run package/provider readiness checks on a dedicated single-thread `statusIo` executor, independent of the `io` executor that can be occupied for the entire duration of an install/uninstall job. Deliver results back to the main thread; discard stale refresh generations and callbacks after the view is removed; stop the status executor when the fragment is destroyed.
- This change preserves the current companion authority and UI behavior. It does **not** yet move Epic execution into the host package and does not prove that download-time lag is fixed; it only removes a synchronous readiness probe from the UI thread.
- CI for this change is in progress: standard APK workflow **44967487255** (active job **137039691323**) and Epic runtime workflow **44967487180** (queued behind the earlier CPython build). The host-merge audit workflow **44967380029** is still running its CPython stage at the time of this entry. Require the final builds/audit to pass before device testing.


## 2026-11-22 — First host-to-embedded Epic runtime routing implementation

- Corrected the previous Epic workflow audit failure. The failure was a shell-ordering bug: the host APK audit invoked `$BUILD_TOOLS/aapt` before assigning `BUILD_TOOLS="$ANDROID_HOME/build-tools/35.0.0"`. The same build log proves Gradle succeeded and the host APK contained `lib/arm64-v8a/libepic_python.so`, `assets/python/lib/python3.15/os.py`, and both runtime Java classes. Fixed by moving the assignment before the first use in commit **00ecbe22546e6004e86df64a51f6701e9ab0d82c**. The rerun must complete the manifest check and remaining package/signature/ELF audits.
- Implemented the first actual host routing migration in commit **297bccbfde4a0a713d13a66b66dda1462d52e1b6**, `app/src/main/java/com/winlator/cmod/epic/EpicBridgeClient.java`:
  - Resolve the embedded provider as `<context.getPackageName()>.epicprovider`, matching the manifest merger's `${applicationId}.epicprovider` authority for Standard, Ludashi, and AnTuTu flavors.
  - Attempt the embedded same-UID private provider first for readiness and every Epic command.
  - Use the separately installed `com.maliwinlator.epicbridge.provider` only when the embedded provider is unavailable or the host is an older build without it.
  - `isInstalled()` now treats a valid embedded provider status as installed, so the existing EpicFragment can use its existing UI and command flow without requiring the companion package.
- This is a meaningful code migration but not yet a completed single-package migration: the old companion installation/replace UI and fallback are intentionally retained. No old data is migrated or deleted. The new path still needs Android instrumentation on the **host app** proving that its non-exported provider starts in `:epic_runtime`, loads CPython/JNI, can start and query jobs, cancels correctly, and survives process recreation. Verify the three product-flavor authorities in merged manifests.
- Standard APK workflow **44969054012** and Epic runtime/host audit workflow **44969054077** were running against source commit **297bccbf** at the time this entry was written. Wait for both results before device testing.
- The prior Epic run **44967487180** failed only because of the undefined `BUILD_TOOLS` shell variable. The same log confirms the Android ARM64 runtime build, emulator prerequisites, instrumentation, AAR assembly, host Gradle build, and initial merged-host asset/class checks all succeeded; the failure occurred exactly at the first `aapt` invocation. It did not upload artifacts because the audit stopped before artifact staging.
- The Standard APK workflow **44967487255** passed on commit **8ba1fe25**, validating the off-main-thread readiness probe.


## Engineering step log — 2026-11-22: embedded Epic runtime CI verification and failover hardening

- Verified Standard APK workflow **44969054012** completed successfully on source commit `297bccbfde4a0a713d13a66b66dda1462d52e1b6`. Artifact: `maliwinlator-1.2.1-standard-debug-297bccbfde4a0a713d13a66b66dda1462d52e1b6` (Actions artifact ID `14732703614`).
- Verified Epic runtime workflow **44969054077** completed successfully on the same source commit. Its steps passed: managed x86_64 runtime smoke, Android JNI instrumentation, ARM64 runtime packaging, ARM64 library build, host APK build, artifact upload and prerelease publication. Epic APK artifact ID `14733705814`; prerelease tag `maliwinlator-1.2.1-epic-297bccbfde4a`.
- CI's published APK SHA-256 values: host `a0356eb28332e3b65ef90d9ea07793d4bb8e3f90939c8612d765d7149a80eace`; companion `5282fac96a65561e1ce0b08fb54a494b97ab1befa421551897b6205586e396d1`.
- These results prove build/package/runtime smoke gates, **not** end-to-end host-provider execution on the Xiaomi 15T. Existing Android JNI instrumentation runs against the Epic Bridge test target; a host-side instrumentation test is still needed to prove the app's merged provider starts in `:epic_runtime` and that the host can complete status/account/job operations with no companion installed.
- Source review confirmed the host's merged library manifest declares a non-exported provider at `${applicationId}.epicprovider` and a non-exported `:epic_runtime` service; all three flavor application IDs are substituted by the manifest merger. Standard flavor is `com.maliwinlator.app`.
- Found and fixed a migration failover gap in `EpicBridgeClient`: previously, any protocol-valid embedded-provider response prevented fallback even when the embedded runtime explicitly reported `runtime_not_installed` / `RUNTIME_NOT_READY`. Commit `fc824e9ff68e37a4a475e6ac71b7d0bd3cdde363` now falls back to the separately installed companion only for explicit runtime-unavailable states/errors; legitimate Epic/account/job errors remain with the embedded provider and are not silently retried elsewhere.
- No claim that download-time UI lag is fixed. The provider routing and fallback change has not yet completed CI at the time of this entry. No new phone-test request until the next build passes and the host-side provider test gap is addressed.

### Next actions
1. Verify CI for `fc824e9`; inspect any failure logs.
2. Add a host-app integration test that calls the merged private provider through `ContentResolver`, verifies protocol/runtime status and confirms the provider/service process identity; exercise account status and job status without installing the companion.
3. Extend package/manifest audit across Standard, Ludashi and AnTuTu variants before removing companion fallback or embedded companion APK asset.
4. Only then issue a new device test build. Preserve the current companion fallback until on-device acceptance succeeds.


## Engineering step log — 2026-11-22: convert host fixture to test the embedded runtime

- Inspected the existing `epicbridge-host-test` fixture and found it still tested the old exported companion authority `com.maliwinlator.epicbridge.provider`. That test did not validate the new embedded provider at `${applicationId}.epicprovider`.
- Converted the fixture to depend on `:epicruntime`, retaining the production host application ID `com.maliwinlator.app`. The test now calls the merged private embedded provider, asserts protocol 2 and `runtime_ready`, invokes `account_status` (forcing the JNI/CPython path), parses the returned JSON, and checks that the provider is running in `:epic_runtime`.
- Reworked `tools/ci/run-epic-android-instrumentation.sh`: it builds the host fixture with the x86_64 CPython prefix, installs only the host fixture and its test APK (not the companion), and requires exactly one passing embedded-runtime test. The previous companion Binder test is superseded by this stronger in-package test.
- Commits: `30a9f2f8a54db3e748572014d1213eceadbc5d90` (fixture dependency), `84f9e3ea8a281127af07a57ebd9a3ef50c2c187a` (embedded-provider/JNI/process test), `3382c0fd531f23fd0bb0d8d459c5ca1f70300127` (CI script).
- This is a source-level test improvement only. The new instrumentation has not run yet. The next Epic CI result is the gate; if it fails, fix the actual build/test issue before issuing a device APK.

### Next actions
1. Run the updated Epic workflow and diagnose the first failing stage, if any.
2. Confirm host instrumentation passes with the companion APK absent.
3. Expand merged manifest/package audits to all production flavors.
4. Keep companion fallback and its installable asset until host instrumentation and Xiaomi 15T acceptance pass.


## Engineering step log — 2026-11-22: host-only embedded runtime test passed; flavor audit added

- Verified the actual log output for Epic workflow **44970863361**, job **137050182429**. The new host integration test ran on the managed Android 15 emulator and reported:
  - Test: `embeddedProviderStartsInRuntimeProcessAndInitializesPython`
  - Result: `OK (1 test)`
  - CI assertion: `Embedded Epic provider/JNI runtime integration passed (1/1).`
- The host fixture installed only `com.maliwinlator.app` plus its instrumentation APK. It did **not** install `com.maliwinlator.epicbridge`. The test reached the embedded `.epicprovider`, observed protocol 2 and `runtime_ready=true`, called `account_status` through the JNI/CPython runtime, parsed the JSON response, and confirmed the provider process name was `com.maliwinlator.app:epic_runtime`.
- The same workflow passed all 11 existing Epic Bridge Android instrumentation tests, ARM64 runtime/AAR/host build, APK audits, and published release `maliwinlator-1.2.1-epic-3382c0fd531f`. Artifact ID `14734190839`; workflow URL: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44970863361.
- This is the first verified no-companion proof for the embedded provider/JNI/account-status path. It does not yet test real Epic authentication, actual game downloads, cancellation under device pressure, or Xiaomi 15T responsiveness.
- Added all-flavor package/manifest audit in workflow commit **4742809f49c7af9167354316452d3ff9fa2360c2**. It now assembles Standard (`com.maliwinlator.app`), Ludashi (`com.ludashi.benchmark`), and AnTuTu (`com.antutu.ABenchMark`) debug variants, then verifies each APK's package ID, matching `.epicprovider` authority, embedded ARM64 CPython JNI library and Python assets. This workflow is queued as run **44974595003**; no result yet.
- Updated CI status file is generated by the successful Epic workflow and records SHA-256 values for the Standard host and companion APKs. The published release is debug-signed and experimental; it is not a production-signed build.
- Download-induced UI lag remains unverified/unfixed. The embedded runtime moves Python/download execution to the dedicated `:epic_runtime` process, but that process still shares the device's CPU, memory bandwidth and storage I/O. Device-side profiling is required before changing downloader worker counts, mmap limits, or UI progress cadence.

### Next actions
1. Complete run **44974595003** and fix any actual flavor build/manifest audit failure.
2. Add embedded-provider tests for start/status/cancel job control and process restart/recovery; do not use real Epic credentials in CI.
3. Review stale UI text that says storage access is required in “both apps”; tailor messaging to embedded vs companion mode without breaking fallback.
4. After the all-flavor audit passes, provide the current release build for Xiaomi 15T testing, with explicit instructions to preserve existing game/container data and not replace the system Vulkan driver.
5. Profile download-time jank on-device (UI thread traces, CPU utilization, storage throughput, memory pressure and notification update frequency). Do not claim IPC is the root cause without measurement.


## Follow-up — flavor audit and same-package storage messaging (2026-11-22)

- Extended Epic workflow in commit `4742809f49c7af9167354316452d3ff9fa2360c2` to assemble all three production host flavors and audit the application ID, merged `.epicprovider` authority, ARM64 `libepic_python.so`, and Python stdlib asset for each:
  - Standard: `com.maliwinlator.app`
  - Ludashi: `com.ludashi.benchmark`
  - AnTuTu: `com.antutu.ABenchMark`
- Added status field `host_embedded` in provider commit `fec273d3b5d4c4c68e4540c06b3bb6ca72666434`. It identifies whether the runtime is inside the host package (same Android app storage permission) or in the standalone companion package (separate permission grant).
- Updated `EpicFragment` in commit `61c6213cf0c7ddda95a82408849db8854b0a2c1a` so embedded mode no longer tells users to grant shared-storage permission in “both apps”; companion fallback still retains the two-package guidance.
- Strengthened the host-only integration test in commit `1d8f8650b71b078bb11ca8009595761154e8629b` to assert `host_embedded=true`.
- Run `44974595003` tests the first all-flavor audit on commit `4742809`; later commits `61c6213` and `1d8f865` supersede it and trigger the current build/test runs `44974633713` (Epic) and `44974633711` (Standard). Results are pending at this point.
- Do not use the older release build as proof of these latest UI/storage-scope changes. The previous successful host-only instrumentation passed before this follow-up change; the new assertion must pass before publishing another device build.


### CI trigger correction

- The flavor-audit workflow initially started on commit `61c6213`, before the final `host_embedded` assertion in the host instrumentation test was included. To ensure the full latest source is exercised, updated the instrumentation script's success marker in commit `b0ae58aaf8b6efca96b9f74ece5775b5952ee54d`; this deliberately retriggers the Epic workflow on the latest branch head and includes the test assertion plus all-flavor audit.
- Latest authoritative run: **44974686442**, queued on `b0ae58a`. Earlier run **44974633713** and baseline run **44974633711** are from `61c6213` and should not be treated as final validation for the newest test assertion.


## Engineering step log — 2026-11-22: CI retry for Maven Central rate limiting

- Inspected failed authoritative all-flavor run **44974686442** (source `b0ae58aaf8b6efca96b9f74ece5775b5952ee54d`). The only job failed in the upstream CPython Android managed-emulator smoke step before instrumentation or MaliWinlator APK assembly. Gradle could not resolve multiple Android build dependencies because Maven Central returned HTTP **429 Too Many Requests** (including protobuf, Gson and Tink artifacts).
- This is an external dependency-repository rate limit, not evidence of an embedded-provider test failure. The smoke test did not run, so the all-flavor package audit remains unverified and no APK from this run is installable.
- Updated `.github/workflows/epic-python-runtime-poc.yml` in commit **e4a758b9481c5d0c143dd11539343c7b7b9d2da3**. The managed Android smoke step now retries up to three times after the initial attempt **only when its captured Gradle output explicitly contains HTTP 429 / Too Many Requests**, with bounded delays of 20, 40 and 60 seconds. Other failures remain fail-fast and preserve their original exit status.
- The workflow change should automatically trigger a new run because the workflow file is in its own push path filter. Track the new run before claiming success. The prior successful embedded-provider test (run **44970863361**) still proves the host-only provider/JNI/CPython path, but it does not cover the newer `host_embedded=true` assertion or the three-flavor packaging audit.

### Next actions
1. Poll the workflow triggered by commit `e4a758b`; verify the retry step, host-only assertion, all three flavors, ARM64 packaging and APK audits.
2. If Maven returns 429 again after the bounded retries, reduce redundant Gradle invocations / use an approved mirror or dependency cache; do not weaken tests or mark the run successful.
3. Keep the existing companion fallback and embedded companion asset until the all-flavor build and Xiaomi 15T device acceptance pass.
4. Once a verified artifact exists, test on the Xiaomi 15T with existing Wine containers and game data preserved. Measure download-time UI jank separately; embedded IPC alone is not proof that the lag is fixed.


## Engineering step log — 2026-11-22: embedded runtime CI passed; progress UI deduplication

- Authoritative Epic runtime workflow **44975450275** completed successfully on source `e4a758b9481c5d0c143dd11539343c7b7b9d2da3` after the bounded Maven Central 429 retry was added. All 31 executed steps passed, including CPython x86_64 managed Android smoke, Legendary Android-thread backend regression/smoke tests, ARM64 CPython packaging, 11 Epic Bridge Android instrumentation tests, ARM64 Gradle build, APK signature/package/ABI/assets/ELF dependency-closure audits, and prerelease publication.
- The host build command assembled `:app:assembleStandardDebug`, `:app:assembleLudashiDebug`, and `:app:assembleAntutuDebug`; the workflow audited each flavor's package ID, `<applicationId>.epicprovider` authority, `libepic_python.so`, and Python assets. The downloadable release currently includes the Standard host APK plus the companion fallback APK; Ludashi and AnTuTu were build-audited but not attached to that release.
- Release: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-e4a758b9481c. Actions artifact: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44975450275/artifacts/14736042580. Artifact ID `14736042580`; artifact ZIP SHA-256 `991db6571f323cef085c76ed8840277fcbdda39b7551f4a72faa24fd119cc3f3`. The build is debug-signed and experimental.
- This CI success verifies emulator/runtime/package integration, not actual Epic account login/downloads or Xiaomi 15T frame responsiveness. Do not state the download-lag issue is fixed without on-device profiling.
- Started UI queue reduction in `EpicFragment.java`: commit `652b0b7235a4a23f523381ddb4dca48dd24c2d99` suppresses duplicate progress-label posts and repeated cancel-button visibility posts while preserving the 1.5-second background status poll. A follow-up Java lambda compile-safety correction is commit `211f4aa896b8073b6b374dcb18b2bfd646b50f37`.
- Validation for the UI change is running: Epic runtime workflow **44976916865** and baseline APK workflow **44976916817** target `211f4aa`. An earlier superseded Epic workflow on `652b0b7` is also visible as **44976908929**; use the `211f4aa` runs as authoritative because they include the compile-safety correction.

### Next actions
1. Require both current `211f4aa` workflows to pass before treating the progress-update optimization as build-verified.
2. Publish/offer the verified Standard host APK and retain companion fallback until Xiaomi 15T device acceptance. Back up existing containers and game data before installation; never replace the Android system Vulkan driver.
3. Device test login/library/install/update/cancel/retry/uninstall/launch and measure UI jank during downloads. Collect `dumpsys gfxinfo`, logcat timestamps, CPU/memory pressure and storage throughput; compare same game and network conditions with the previous two-app build.
4. If jank persists, instrument downloader CPU/decompression and storage writes, notification cadence, and provider calls. Do not tune thread count/mmap based on guesswork.

## Engineering step log — 2026-11-22: verify progress UI deduplication

- Verified the corrected UI-update source commit **211f4aa896b8073b6b374dcb18b2bfd646b50f37** in both authoritative workflows:
  - Epic runtime/integration workflow **44976916865**: success, all 31 steps completed. This includes managed x86_64 CPython/Android smoke, Legendary regression/smoke tests, ARM64 CPython packaging, Android instrumentation, ARM64 Gradle host/runtime build, APK audits and prerelease publication.
  - Standard APK workflow **44976916817**: success, all 12 steps completed, including the standard debug APK build and embedded Epic Bridge/shared-signer audit.
- Epic workflow published prerelease **maliwinlator-1.2.1-epic-211f4aa896b8** and Actions artifact **14736417170**. The generated `PROJECT_EPIC_CI_STATUS.md` records host APK SHA-256 `1b021898009471e3c99dbc7878665b020a6f78be0ff6b4cecef3affee9ba138e` and companion APK SHA-256 `f0ac8832a2aed5ac483a3b6d87d82804eacf08ed3f803e675b52981b56f0405c`.
- This validates Java compilation, Android packaging, embedded runtime instrumentation and CI audits for the source containing progress-label and cancel-button update deduplication. It does **not** demonstrate reduced frame jank during an actual download on the Xiaomi 15T, nor does it validate real Epic account/download flows.
- The previously published `e4a758b` prerelease predates the UI deduplication commits; use the newer `211f4aa` prerelease for device testing.
- Retain companion fallback and existing app/game data. Do not uninstall the companion until embedded mode has passed device acceptance. Do not modify or replace the Android vendor Vulkan driver.

### Next actions

1. Install the `211f4aa` Standard experimental APK as an in-place update, preserving app data and existing Wine containers; keep the companion fallback installed for now.
2. Reproduce one lightweight game download under the same conditions as the previous two-app test. Check whether Winlator remains responsive and whether progress/cancel UI behaves correctly.
3. If lag persists, collect a short timestamped logcat and `dumpsys gfxinfo`/frame stats during the download, plus available CPU/memory/storage observations. Then isolate whether the bottleneck is UI-thread work, downloader CPU/decompression, storage I/O, memory pressure, notification updates or provider IPC before changing architecture or worker limits.
4. After the device check, continue with cancellation/retry/update/uninstall and selected-container launch acceptance. Keep the current CI success separate from those device-level acceptance criteria.

### Static IPC review — 2026-11-22

- Reviewed the current `EpicFragment.waitForJob()` and `EpicBridgeClient` call path after the embedded-runtime migration. The host first calls its own `<applicationId>.epicprovider` through `ContentResolver.call()`; that provider runs in the same app package's dedicated `:epic_runtime` process. Normal embedded operation therefore uses same-UID, cross-process Binder IPC, **not companion-app IPC**. Companion IPC is only a fallback when the embedded provider/runtime is unavailable.
- The UI worker polls job status every **1.5 seconds**. Progress text is now posted to the main thread only when the displayed phase/percentage label changes, and cancel-button visibility updates are deduplicated. This is a low-rate status channel; source inspection alone does not prove IPC is or is not responsible for the observed device lag.
- No further polling-rate reduction or downloader concurrency/mmap change is justified without device measurements. The next useful evidence is a same-condition test of the new embedded build, followed by timestamped UI/frame, CPU, memory and storage observations if jank persists.

## Engineering step log — 2026-11-22: foreground notification deduplication

- Reviewed `EpicRuntimeService.updateProgressNotification()`. The service's one-second foreground polling task read and parsed the status JSON, rebuilt a Notification, and called `NotificationManager.notify()` on every tick even if the user-visible phase and integer percentage were unchanged. It also included a changing download-speed string, which could force needless notification updates.
- In commit **e067cb7f04dbb1d2505c3516ac241995ffdd97f5**, added a cached notification key based on the displayed phase and integer percentage. The service now skips `NotificationManager.notify()` when that key is unchanged, resets the cache when a new job begins, and removes the volatile speed string from the notification so updates are driven by meaningful visible progress changes. The notification still shows the phase and percentage.
- This reduces repeated Android notification publication; the service still samples the progress file once per second. It is a targeted optimization, not proof of a device-level performance improvement or a diagnosis of the original lag.
- CI triggered on the new source commit:
  - Epic runtime workflow **44979607565**.
  - Standard APK workflow **44979607640**.
  Both were queued when this entry was written. Do not call the change build-verified until both complete successfully.
- Device profiling remains necessary if Winlator still becomes unresponsive: compare frame jank, CPU/decompression, memory pressure, storage writes, notification update count and provider call timing during the same download workload.

### Next actions

1. Verify both workflows for commit `e067cb7`; fix any actual compile or packaging failure.
2. If successful, update `PROJECT_EPIC_CI_STATUS.md` and publish/record the new experimental artifact and SHA-256.
3. Have the user test the new build on the Xiaomi 15T only after the CI gate passes; retain companion fallback and preserve all existing app/container data.

### Notification deduplication regression coverage

- Added a pure decision helper `EpicRuntimeService.shouldPostProgressNotification(previousKey, nextKey)` and an Android instrumentation regression test covering initial publication, unchanged progress suppression, percentage changes, phase changes, and null next-state handling.
- Test/source commits: `c4abadb87c1573a27593faa5e38eebbc1a4b2128` and `3e9c2172246a7ba3f9b611656107718ed29ad64c`. The latter is the authoritative latest source SHA.
- Latest validation runs on `3e9c217`: Epic runtime workflow **44979673157** and Standard APK workflow **44979673164**. Both were pending at this update. Earlier `e067cb7` runs **44979607565** and **44979607640** were still running but are superseded by the latest commit and must not be used as final validation.



## Notification deduplication — CI verified and device build published (2026-11-22)

- Authoritative source commit: **3e9c2172246a7ba3f9b611656107718ed29ad64c**.
- Epic runtime/instrumentation workflow **44979673157** completed successfully; all 31 steps passed, including managed x86_64 runtime smoke, Legendary regression tests, ARM64 CPython packaging, Android instrumentation, ARM64 host/runtime assembly, APK audits, and prerelease publication.
- Standard APK workflow **44979673164** completed successfully; all 12 steps passed, including standard debug APK assembly and embedded Epic Bridge/shared-signer verification.
- Notification regression coverage in the Epic workflow validates initial notification publication, suppression when visible phase/percentage is unchanged, publication on percentage or phase change, and null next-state handling.
- Published experimental prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-3e9c2172246a
- Standard host APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-3e9c2172246a/app-standard-debug.apk
- Companion fallback APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-3e9c2172246a/epicbridge-debug.apk
- Release SHA-256: host APK `31710ef65262fec687585587399c5790bd5d323885a5924d27e0064010fbca4f`; companion APK `5c75aae3024d87cf0111488c198921dc5ddfbdff820326113e3fe3a70f48ea95`.
- Actions artifacts: Epic package artifact ID **14737623195**; standalone standard APK artifact ID **14737401181**. The latter was independently built and verified by workflow 44979673164.
- These are experimental debug-signed artifacts. CI proves build/package and instrumentation behavior, not real Epic login/download acceptance or reduced jank on the Xiaomi 15T.

### Next acceptance gate

1. Install the new Standard APK as an in-place update, preserving app data and Wine containers. Keep the companion APK installed as fallback; do not remove it yet.
2. Repeat one small game download under comparable network/storage conditions. Record whether the Winlator UI remains responsive, notification percentage advances, and cancellation remains available.
3. If lag remains, collect timestamped logcat and frame-jank data during that exact download before changing downloader worker count, mmap sizing, or IPC design. Include CPU/memory pressure and storage activity where available.
4. Continue functional acceptance for login, library, install/cancel/retry, update, uninstall, and launch in the selected Wine container. Do not treat CI as device acceptance.


## Xiaomi 15T Epic-page re-entry crash — investigation and first fix (2026-11-22)

### Reproduction reported by user

- On the new build, foreground download notification and percentage are visible.
- Winlator is responsive while navigating away from Epic to container/settings screens.
- Returning to the Epic page while a game is downloading or installing causes the Epic page to freeze and then Winlator crashes.
- The failure is specifically correlated with Epic-page re-entry during an active job. The exact Android crash/ANR stack trace has not yet been collected; do not claim a confirmed root cause.

### Source review findings

- EpicFragment.onResume() called resumePendingJob(), whose EpicBridgeClient.isInstalled() synchronously executes ContentResolver.call() against the embedded provider. This was an avoidable Binder/provider call on the UI thread.
- refreshStatus() could submit account_status through EpicPythonRuntime.call() even while Legendary's install/download operation was occupying the serialized embedded Python runtime. That status query is unnecessary during an active job and can queue behind the long-running runtime call.
- EpicFragment.onDestroy() shut down statusIo but did not shut down the fragment-owned io executor. A permanently destroyed fragment could therefore leave its waitForJob() polling worker alive while a newly created Epic fragment starts another monitor for the same persisted job.

### First targeted fix

- Commit 4c5ea1d1ced8db0c69251a9fbf0bf444ddda9c28 (fix: isolate Epic page resume from active runtime jobs):
  1. Skip the account_status JNI/Python command when a persisted Epic job ID is present; let the job monitor own the progress/status UI until terminal state.
  2. Remove the synchronous EpicBridgeClient.isInstalled() provider call from resumePendingJob() on the UI thread. The first job_status call validates provider access from the IO worker.
  3. Shut down the fragment-owned IO executor on permanent fragment destruction, interrupting only the UI monitor. The foreground service remains the owner of the actual download/install and its persisted job ID is retained for a replacement fragment to resume monitoring.
- This is a source-level mitigation based on the observed lifecycle and runtime serialization paths, not yet a proven crash fix. The exact crash root cause remains unconfirmed pending device logcat/ANR evidence.

### CI validation in progress

- Epic runtime workflow 45017702452: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45017702452
- Standard APK workflow 45017702441: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45017702441
- Both target source commit 4c5ea1d1ced8db0c69251a9fbf0bf444ddda9c28; do not publish or call it verified until both pass.

### Next steps

1. Poll both workflows and fix any concrete compile/instrumentation/package failure.
2. If both pass, publish a new experimental build and ask for a controlled repeat: start download, navigate away, return to Epic, repeat several times while install continues.
3. If it still crashes, collect adb logcat -b crash -d, relevant AndroidRuntime/ActivityManager lines, and dumpsys activity exit-info com.maliwinlator.app after the failure. Capture an ANR trace if Android reports an ANR. These distinguish main-thread ANR, Java exception, native crash, and process kill/OOM.
4. Do not change downloader worker counts, mmap capacity, or remove companion fallback until the failure mode is measured.


## Epic re-entry mitigation — CI and published test build (2026-11-22)

- Source commit: `4c5ea1d1ced8db0c69251a9fbf0bf444ddda9c28`.
- Epic runtime workflow **45017702452**: SUCCESS — https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45017702452
- Standard APK workflow **45017702441**: SUCCESS — https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45017702441
- Epic runtime verification included managed x86_64 runtime tests, Android JNI instrumentation, ARM64 Gradle assembly, APK signature/package/ABI/assets audits, ELF dependency closure, and embedded companion comparison.
- Standard APK artifact: `maliwinlator-1.2.1-standard-debug-4c5ea1d1ced8db0c69251a9fbf0bf444ddda9c28` (artifact ID **14752427077**).
- Epic ARM64 package artifact ID **14752258938**; runtime artifact ID **14752368485**.
- Published prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-4c5ea1d1ced8
- Host APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-4c5ea1d1ced8/app-standard-debug.apk
- Companion fallback APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-4c5ea1d1ced8/epicbridge-debug.apk
- Release SHA-256: host APK `75c03a420e5fb7fd8c59f899fc5299d045c65d8f7d55b8cfe828d172ebd4a04c`; companion APK `27bbc15fb5b3cb897a717983ba72655d3d6ac49a6ed9a7bba61a0b27d6af0de4`.
- These are debug-signed experimental builds. CI success is not proof that the Xiaomi 15T re-entry crash is fixed. Device acceptance is still pending.

### Device retest instructions

1. Keep the existing app data, Wine containers, installed games, and companion fallback. Attempt to install the new host APK as an in-place update. If Android reports a signature/package conflict, stop and report the exact message; do not uninstall or clear data.
2. Start a small download, navigate away from Epic, then return while the download is active. Repeat once during installation if possible.
3. Report whether the page opens, progress remains current, Winlator remains responsive, and the notification continues updating.
4. If it still freezes/crashes, capture `adb logcat -b crash -d`, relevant `AndroidRuntime` / `ActivityManager` lines, and `adb shell dumpsys activity exit-info com.maliwinlator.app`. Include ANR traces if Android reports an ANR.

### Follow-up engineering gate

If the failure persists, inspect whether the Epic fragment is retained on the navigation back stack (so `onDestroy()` does not run on tab changes), and whether multiple job observers are active. Then inspect synchronous `loadContainers()` work in `onResume()`. Do not change worker counts, mmap sizing, or IPC architecture without crash/jank evidence.


## Epic page re-entry regression — device acceptance closed (2026-11-22)

- Tested build: source commit `4c5ea1d1ced8db0c69251a9fbf0bf444ddda9c28`, published tag `maliwinlator-1.2.1-epic-4c5ea1d1ced8`.
- Device: user's Xiaomi 15T / Android 16.
- User confirmed the page remains smooth with no freeze or lag when returning to Epic while a game is actively downloading and while it is actively installing.
- Mark the Epic-page re-entry freeze/lag regression **device-accepted and closed** for this build. Do not continue changing the downloader, IPC, worker count, or memory mapping to chase this resolved symptom absent a new reproducible report.
- CI workflows for this commit both passed: Epic runtime/instrumentation `45017702452` and standard APK `45017702441`.
- Direct APK asset URL (preferred for this user's device): https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-4c5ea1d1ced8/app-standard-debug.apk
- Remaining acceptance work is broader Epic feature coverage: authentication/session persistence, library refresh, install/cancel/retry, update, uninstall, and launching an installed game in the selected container. These are separate from the now-closed page re-entry issue.


## Engineering step log — 2026-11-22: Epic UI source audit and visual-reference blocker

- User supplied four Google Drive image file links intended to show the GameHub Epic UI. The direct `uc?export=download&id=...` URLs were attempted through the available retrieval paths, but Google Drive remained inaccessible from this environment. No screenshot pixels were inspected; do not claim a visual comparison or begin a screenshot-faithful redesign based on assumptions.
- Audited the current `EpicFragment.java` implementation on the active branch. The screen is programmatically assembled from a vertical `ScrollView`/ `LinearLayout`, with plain text status, setup/login controls, a container spinner, a library refresh button, and vertically stacked text-only game rows. Each row currently has title/installed state and Install or Launch/Update/Uninstall actions. No game artwork, card grid, category/navigation rail, or per-game visual hierarchy is rendered by this fragment.
- Confirmed the existing UI actions call the current Legendary bridge operations and rely on established storage/container path validation, install/update/uninstall behavior, and foreground job monitoring. Any redesign must preserve these methods and state transitions; avoid replacing the backend or changing worker/IPC concurrency while addressing presentation.
- No source code was changed and no APK was built for this visual audit. The exact GameHub target styling remains unverified until the reference images are reachable through an image URL the inspection environment can retrieve.
- Next: obtain viewable direct image URLs (a public image host such as Postimages/ImgBB, or images embedded in a publicly accessible webpage), then compare the GameHub and MaliWinlator screens. In parallel, inspect app theme/resource conventions and split the Epic UI into testable view-building components before modifying the screen.


## Epic storefront redesign — first implementation pass (2026-11-22)

- User approved a GameHub-inspired Epic page direction: premium dark presentation, artwork-led game library, clear install/play actions, and dedicated Discover, Library, Downloads, and Account sections.
- Source changes on `development/maliwinlator-1.2.1-bootstrap`:
  - `4bf7c5d0414fb3c01e9c33923d906c064b0a60f5` — replace the plain Epic list screen with a dark storefront shell, Discover hero, section navigation, Account setup panel, searchable two-column library cards, and per-game Play/Install/Update/Remove actions.
  - `5573bcb624002e1a26605bf0433fe1860a39e942` — surface the existing foreground job monitor's progress label in the Downloads section; terminal job states are reflected there as well.
- Existing Legendary commands, container ownership checks, storage validation, foreground service, saved job ID, and IPC flow were intentionally retained. No downloader worker counts, runtime IPC protocol, or install-path policy was changed.
- The current Legendary library response contains `app_name`, `title`, `version`, `is_dlc`, `third_party_store`, and DLC metadata; it does not currently expose verified cover-art URLs. The first UI pass therefore uses deterministic abstract gradient/monogram cover tiles rather than inventing remote artwork sources.
- This is a first presentation implementation, not yet a verified APK or device-tested UI. GitHub Actions Epic runtime and Android packaging validation must pass before publishing a build. Review compiler output and fix any issues before asking the user to install.
- Known follow-up: selected navigation styling and true game artwork should be added only after a trustworthy metadata/artwork source and cache strategy are established. Validate the search/card layout, downloads status refresh on tab re-entry, storage/setup controls, and all existing game actions on the Xiaomi 15T.



- Follow-up UI polish commit `f2a920f270404ea7cd714f275f0b8b0c3372534f`: active section now receives a distinct selected-tab treatment. This is included in the current branch tip.
- Validation runs launched for the immediately preceding source commit `5573bcb624002e1a26605bf0433fe1860a39e942` (standard APK run **45039925553**, Epic runtime run **45039925583**). Because the active-tab polish landed after those runs started, their results do not validate commit `f2a920f`; rerun or otherwise validate the exact branch tip before publishing.


## Epic artwork integration — second implementation pass (2026-11-22)

- The first GameHub-inspired storefront commit `f2a920f270404ea7cd714f275f0b8b0c3372534f` passed both pipelines:
  - Standard APK: run **45040046995**, success.
  - Epic runtime/instrumentation + ARM64 packaging: run **45040047012**, success. All steps passed, including Android runtime prerequisite smoke, Epic library aggregation regression tests, Android instrumentation tests, ARM64 runtime packaging/audit, and GitHub prerelease publication.
- Published test build: `maliwinlator-1.2.1-epic-f2a920f27040`. Direct main APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-f2a920f27040/app-standard-debug.apk. Companion APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-f2a920f27040/epicbridge-debug.apk.
- Follow-up artwork commits:
  - `1a4706f2812b7f1e71449efd026a3aa924e6ac47` — Epic Bridge now extracts one preferred HTTPS cover image from Legendary catalog `metadata.keyImages` (portrait box art first, then wide/thumbnail fallbacks); adds deterministic self-test coverage for priority and rejection of non-HTTPS image URLs.
  - `25664d0cadacb0844fb333526cc218f4d2ecfb39` — game cards load catalog artwork with the existing Glide dependency, automatic memory/disk caching, crop-to-card, and gradient/monogram fallback. URL host validation is restricted to HTTPS Epic Games / Unreal Engine domains.
- Latest validation runs are associated with the artwork commits: standard APK run **45042169506** (in progress at handoff update), Epic runtime run **45042169424** (pending), plus the earlier Epic runtime run **45042166515** for Python artwork extraction commit **1a4706f** (in progress). Do not publish the artwork version until the exact latest commit `25664d0` passes both workflows.
- Device verification is still required for real artwork visibility, cache reuse/offline fallback, library scroll performance, and all existing install/play/update/uninstall flows.


## Epic artwork build published and CI-verified (2026-11-22)

- Exact artwork source commit `25664d0cadacb0844fb333526cc218f4d2ecfb39` passed both required pipelines:
  - Standard APK workflow **45042169506** — success.
  - Epic runtime / Android instrumentation / ARM64 packaging workflow **45042169424** — success. Managed x86_64 runtime smoke, JNI instrumentation, ARM64 build, package/signature/ABI/assets/ELF dependency-closure audits, and prerelease publication all passed.
- The separate earlier runtime run **45042166515** for extraction commit `1a4706f` was cancelled; it is superseded by the successful exact-artwork-commit pipeline above.
- Published prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-25664d0cadac
- Direct APK assets:
  - Main: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-25664d0cadac/app-standard-debug.apk
  - Companion: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-25664d0cadac/epicbridge-debug.apk
  - Checksums: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-25664d0cadac/SHA256SUMS
- Release notes identify these as debug-signed test APKs. If the debug signing certificate differs from the currently installed build, Android may require uninstalling the old debug package first; back up any important app/container data before doing so.
- Device acceptance remains pending: verify actual Epic catalog artwork, scrolling/cache/offline fallback, and core Epic account/library/install/update/uninstall/launch flows on Xiaomi 15T. Previously accepted active download/install Epic-page re-entry responsiveness fix remains a regression guard.


## Epic library re-entry cache fix (2026-11-22)

- User device acceptance of artwork build: actual Epic cover art renders; Library scrolling and navigation away/back during an active download remain smooth. New issue reported: every return to Epic triggers a full library reload.
- Root cause confirmed in source: `MainActivity.onNavigationItemSelected()` constructs `new EpicFragment()` on every Epic selection, and `show()` uses FragmentTransaction.replace(). The Fragment-local `cachedGames` / `libraryLoaded` fields are therefore discarded when navigating to Containers; the next Epic Fragment sees an empty cache and calls `library` again.
- Fix implementation:
  - `5936ed418063f646b80242c9f42267aa297a7c7e` updates `EpicFragment` to hydrate and publish library data through an Activity-scoped ViewModel, and render cached cards immediately when a replacement Fragment is created.
  - `b5e0e84632e2be443e105a748f9bf35c73ccf1ac` adds `EpicLibraryViewModel`, retaining game catalog JSON plus installed app IDs and install paths for the lifetime of MainActivity.
  - Explicit Refresh and successful install/update/uninstall/login flows still call the existing refresh path; this change does not alter Legendary, authentication, downloader workers, or foreground-job IPC.
- CI for exact combined source head `b5e0e84632e2be443e105a748f9bf35c73ccf1ac` has started:
  - Standard APK workflow **45053802111**: pending at handoff update.
  - Epic runtime / Android instrumentation / ARM64 workflow **45053802067**: pending at handoff update.
  - The first standard run on intermediate Fragment-only commit **5936ed4** was cancelled when the ViewModel commit landed; it is superseded by the two exact-head workflows above.
- Do not distribute a new APK until both workflows for `b5e0e846` succeed. Device acceptance remains pending for immediate cached re-entry, explicit Refresh, and cache updates after install/update/uninstall.


## Epic library cache fix CI complete and published (2026-11-22)

- User confirmed on-device acceptance of previous artwork build: real cover artwork displays; scrolling and leaving/re-entering Epic while downloads run remain smooth. User then reported library reloaded every time they returned to Epic.
- Root cause was confirmed and fixed with Activity-scoped ViewModel cache; see commits `5936ed4` and `b5e0e84` above.
- Exact combined source head `b5e0e84632e2be443e105a748f9bf35c73ccf1ac` passed both required workflows:
  - Standard APK workflow **45053802111** — success.
  - Epic runtime / Android instrumentation / ARM64 packaging workflow **45053802067** — success, including managed x86_64 runtime smoke, Legendary aggregation regression tests, Android JNI instrumentation, ARM64 packaging, APK audits and prerelease publication.
- Published prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-b5e0e84632e2
- Direct main APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-b5e0e84632e2/app-standard-debug.apk
- Direct companion APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-b5e0e84632e2/epicbridge-debug.apk
- SHA-256: main APK `259002ec0ac0230901974ec42b6d636e4f02c41f46c52d233cf443b6058ba980`; companion `256d1926b443066cb934e1591b968dc3c3665cab8eb3fea371bdc7c6c66ae43a`.
- Device test now required: while MainActivity remains alive, visit Epic, switch to Containers, return to Epic and confirm cards appear immediately without library loading request/spinner. Also confirm manual Refresh still refreshes and installed state updates after install/update/uninstall. Process death/relaunch is expected to reload.
- Debug signing caveat: these are debug-signed APKs; a certificate mismatch may require uninstalling the prior package. Back up container data before any uninstall.


## Epic active-job recovery hardening (2026-11-23)

- Active branch: `development/maliwinlator-1.2.1-bootstrap`.
- Source fix commit: `ce08bb6ff1eb65572762123857603addf3f31b35` — `fix: retain Epic job IDs across transient status gaps`.
- Source audit found that `EpicFragment.waitForJob()` removed the persisted `active_job_id` after 30 seconds of provider state `unknown`. That could make the Downloads UI report no active operation even while the foreground service/notification remained active.
- The fix no longer treats `unknown` as proof of completion and retains the saved job ID. It marks the result as pending rather than failed, and does not tell the user to retry an install while the original operation may still be running.
- Entering Downloads now reattaches a job-status observer when a saved job ID exists. Reconnect and provider-error states are also published to the Downloads status label.
- No downloader worker counts, Python runtime concurrency, foreground-service behavior, notification behavior, IPC protocol, or install paths were changed.
- CI/device validation: not yet recorded for this commit. The available combined commit-status query returned no status entries; do not distribute this source as a verified APK until the exact commit has passed the standard APK and Epic runtime/instrumentation/ARM64 workflows.
- Next: verify both workflows for `ce08bb6`; then device-test a small download while navigating away and back, open Downloads after reconnect, and confirm the progress UI remains aligned with the notification. If the provider continues returning `unknown`, inspect the service's persisted job preferences and service lifecycle before changing more code.


## Epic provider-affinity correction (2026-11-23)

- Additional source commit: `6bf0b9f73aa7cdf6d6855a05fd135b2153379c9f` — `fix: pin Epic jobs to their originating provider`.
- Follow-up hardening commit: `47758e3c7d19d81df2f5564ff41aa6c01d49d08e` — `fix: recover Epic job routing when a provider disappears`.
- Source audit found a provider-routing hazard in `EpicBridgeClient.call()`: a request could fall back to the separately installed companion when the embedded provider was unavailable, while a later `job_status` request could be answered by the embedded provider. These providers run separate services and maintain separate `epic_runtime_jobs` SharedPreferences, so the non-originating provider can validly answer `state=unknown` for a job that is active in the other provider.
- The client now records the provider authority returned by `start_job` in host-private `epic_bridge_jobs` preferences and uses that same authority for subsequent status and cancellation requests. For older jobs without an affinity record, it probes the alternate provider when the first provider reports `unknown`; cancellation similarly probes the registries before routing.
- If the recorded provider becomes unavailable, the client can switch to the alternate provider only when that provider returns a usable response. Terminal job states clear the provider-affinity record.
- Together with `ce08bb6`, Downloads retains the saved job ID across transient `unknown` status and reconnects its observer when the Downloads section is opened.
- This is a source-level correction to a real cross-provider consistency hazard. It is not yet proof that the Xiaomi 15T notification/UI mismatch is fixed; CI and device acceptance remain required.
- No downloader worker counts, Legendary runtime serialization, notification implementation, storage mapping, or Wine install paths were changed.
- Validation status: no combined commit-status entries were returned for `47758e3` at the time of this update. Do not publish an APK until both the standard APK and Epic runtime/instrumentation/ARM64 workflows pass for the exact branch head.
- Next: obtain CI results for the current branch head, review compile/instrumentation logs, then test an active small download while switching between Discover/Library/Downloads and while the provider is restarted or temporarily unavailable. Confirm that the notification and Downloads UI report the same operation and progress.

- Additional compatibility commit: `e7d852dbb7913e22cbff65cfb5f55e6519c70d31` — persist provider affinity when recovering cancellation routing for a legacy job that predates the new mapping.


## CI validation update (2026-11-23, source commit e7d852d)

- Exact source commit under test: `e7d852dbb7913e22cbff65cfb5f55e6519c70d31`.
- Standard MaliWinlator 1.2.1 APK workflow run `45205610114` completed successfully. Steps included standard debug APK build and verification that the embedded Epic Bridge APK exactly matches the separately built bridge APK and shares the host signer. Artifact: `maliwinlator-1.2.1-standard-debug-e7d852dbb7913e22cbff65cfb5f55e6519c70d31` (GitHub artifact ID `14834860041`, SHA-256 `5e247b22adf5f03c775c937194e6cf6a8fec0edf8ed87da6f6cb5877a0197326`, 446,734,346 bytes, expires 2026-12-07).
- Epic CPython/runtime workflow run `45205610044` was still in progress at last inspection. Completed successfully through x86_64 runtime build, Legendary Android-thread-backend static validation, Epic library aggregation tests, host smoke tests, x86_64 site-packages staging, managed x86_64 emulator runtime prerequisite smoke, ARM64 runtime build, and packaged-runtime audit.
- Current in-progress step: `Run Epic Bridge x86_64 Android instrumentation tests`. The ARM64 Epic runtime/companion/host APK packaging and prerelease publication steps have not yet started.
- Do not treat the successful standard APK artifact as the final Epic-enabled test package. Wait for the Epic runtime workflow to pass and publish its own installable APK artifacts.


## Epic job recovery and provider-affinity CI complete (2026-11-23)

- Exact source head `e7d852dbb7913e22cbff65cfb5f55e6519c70d31` passed the standard build workflow `45205610114` and the full Epic runtime workflow `45205610044`.
- Epic workflow completed successfully: CPython 3.15.2 x86_64 instrumentation runtime build; Legendary Android-thread-backend validation; Epic library aggregation tests; host smoke tests; managed x86_64 emulator runtime prerequisite smoke; production ARM64 runtime build and audit; Epic Bridge Android instrumentation; ARM64 host/companion APK packaging; APK upload and prerelease publication.
- Published test release: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-e7d852dbb791
- Main installable ARM64 debug APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-e7d852dbb791/app-standard-debug.apk
- Companion APK (normally already embedded in the main app; install separately only if specifically needed): https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-e7d852dbb791/epicbridge-debug.apk
- Main APK SHA-256: `233437f1d0acc009a85dd77401855e24d993563fecc1dd4aa5e962a1f50a9822`.
- Companion APK SHA-256: `d7dcc69527b6e259f7a87459d19f9ef0ce319b010668cbe7a2ed920b5d22d688`.
- Epic runtime artifact `maliwinlator-1.2.1-epic-arm64-debug` (artifact ID `14833329172`) SHA-256: `a1ca70499ebc9ac00a8e97faf2b60b04c02f0433dc0af9db3794a55a82505da4`; expires 2026-12-23. CPython ARM64 POC artifact ID `14834334073` is not the installable app.
- This is a debug-signed experimental test build, not a production release. A different debug certificate may require uninstalling the previous debug app; back up container data first.
- Device acceptance is still pending. Test a small Epic download/install while navigating between Epic and other Winlator pages; verify progress and status remain consistent between notification and Downloads UI, including after returning to Epic and after reopening Downloads. Do not claim the provider-affinity correction is device-verified until the Xiaomi 15T test passes.


## Engineering step log — 2026-11-23: Downloads re-entry crash and live-state rendering

- User device report after installing the Epic build from source commit `e7d852dbb7913e22cbff65cfb5f55e6519c70d31`: foreground notification works, but opening Downloads during an active download still crashes Winlator; when the screen remains visible it stays on “Reconnecting to the saved Epic operation…” and does not show the current operation or percentage.
- Inspected the exact `EpicFragment.showSection("downloads")` implementation. Confirmed a deterministic Android view-parent defect: `pageContainer.removeAllViews()` detaches the Downloads panel from the page but leaves the reusable `downloadsStatusText` attached to that detached panel. Re-entering Downloads creates a new panel and attempts to add the same TextView while it still has a parent, which can throw `IllegalStateException: The specified child already has a parent`.
- Fixed the view lifecycle by explicitly removing `downloadsStatusText` from its previous `ViewGroup` before attaching it to the newly created Downloads panel.
- Fixed the no-snapshot UI gap: job polling now renders a visible state label for `queued`, `running`, and `cancelling` even before a Python progress snapshot exists; when a snapshot exists it continues to show phase and percentage.
- Source commit: `d88a6bfb73f32665bb5b26ad9af262c74e63b4b7` (`fix: prevent Epic Downloads re-entry crash and render live job state`).
- This is a source-level diagnosis/fix only. It has not yet passed CI or been tested on the Xiaomi 15T. The remaining device-level question is whether the bridge returns a known job state and whether Legendary continuously updates the progress snapshot; the foreground notification alone does not prove that the host UI's provider query succeeds.

### Next actions

1. Run the standard APK and Epic runtime workflows for commit `d88a6bfb73f32665bb5b26ad9af262c74e63b4b7`; inspect any failures before publishing an APK.
2. Add regression coverage for repeated Downloads entry and progress rendering where feasible.
3. If the UI still reports reconnecting after this build, capture filtered Android logs for `EpicFragment`, `EpicBridgeProvider`, and `EpicRuntimeService` while the notification is active; never include Epic tokens or authorization data.
4. Do not mark the crash/progress issue device-fixed until the Xiaomi 15T test confirms it.


## 2026-11-23 validation result — Downloads UI correction

- Source commit `d88a6bfb73f32665bb5b26ad9af262c74e63b4b7` passed both CI workflows: standard debug APK workflow `45221637699` and Epic runtime/Android instrumentation workflow `45221637577`.
- Published experimental ARM64 debug release: [maliwinlator-1.2.1-epic-d88a6bfb73f3](https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-d88a6bfb73f3).
- Main APK: `app-standard-debug.apk`, 510,892,640 bytes, SHA-256 `2e5ca21a2ce8d3d201090d2a944f5c3781ca6853a1df40131eefffd42c684feb`. Embedded Epic Bridge is included; companion APK is separately attached as a fallback package and is not normally required for the embedded setup.
- CI evidence includes x86_64 managed-emulator runtime smoke, Android JNI instrumentation (LegendaryCore, AES-GCM positive/negative cases, worker-thread, mmap and downloader lifecycle), ARM64 APK packaging, embedded companion byte comparison, package/signature/ABI/assets/ELF dependency closure audits.
- The release is a debug-signed experimental test build, not a production release. It may require uninstalling an older debug package if signing certificates differ; back up containers before any uninstall.
- The view-parent crash fix and fallback status label have passed CI, but user-device verification is still outstanding. In particular, the UI percentage depends on provider status/progress snapshot delivery; a fallback label cannot guarantee the percentage is available.


## 2026-11-23 — Epic Library / game-details redesign

User approved replacing the separate Library and Downloads destinations with a unified Library and per-game details flow. Implementation is in progress on `development/maliwinlator-1.2.1-bootstrap`.

### Implemented in source
- Changed Epic navigation to `Library` and `Account`; removed the separate Discover and Downloads navigation destinations.
- Library cards now open a per-game details screen instead of embedding separate Install/Play buttons in every card.
- Added a game details view with trusted Epic artwork (existing allow-listed artwork URL handling), title/status, install or launch action, progress/status panel, progress bar, and Update/Uninstall action for installed games.
- Install action changes immediately to a starting state and then to Cancel download while the saved foreground job is active. Cancel routes through the existing `cancel_job` provider API; the existing foreground service and Legendary worker remain the download engine.
- Added Library filters: All games, Installed, Ready to install, Downloading.
- Persisted the active app name beside the saved job ID so the app can restore the corresponding game's details screen after fragment recreation/library reload.
- Added explicit Achievements, Playtime, and Cloud saves information areas. Current implementation clearly says these values/support are not exposed or verified by the existing Epic integration; no synthetic values or unsupported cloud-save behavior are claimed.
- Existing account sign-in, storage permissions, and container destination selection remain in Account.

### Source commits
- `048aa327750f5c83ab2e57bbfbf88475597c32cb` — unified Library navigation and per-game details screen.
- `45d4ccaaac05c7c4302622815421ef095dafae66` — game-page install/cancel state, saved app association, progress display integration.
- `6ed60c1820778ad29c93ce834829b7760dbbb00f` — determinate/indeterminate progress bar.
- `e5585743c5499b351005e729230c5f2d3e63ac6a` — restore active game's detail page after library reload and add install-button press animation.
- `db7c04d0070c5faa033f081014556f21e8fd7863` — Library status filters.

### Validation / limitations
- CI workflows are running for the latest source revision; no APK should be distributed until both relevant workflows complete successfully.
- The current UI progress bar consumes the existing provider's `progress_json.progress` field and displays a pulsing indeterminate bar when only a job state/phase is available. It does not invent download size, speed, or ETA.
- Achievements, playtime, and cloud-save sync still require separate capability discovery and backend/API implementation. The UI currently labels these as unavailable/unverified.
- Device validation is still required on the Xiaomi 15T, especially installing, canceling, leaving/re-entering the game page, and checking progress updates.


## 2026-11-23 — Epic achievements / cloud-save backend audit

### Confirmed build state
- Current branch head at audit start: `f6574e1a53d571f81f885a9ac7aae6bfac9d70e9` (documentation commits are ahead of the source build).
- Source commit `db7c04d0070c5faa033f081014556f21e8fd7863` passed standard APK CI run `45230482494` and Epic runtime/ARM64 package CI run `45230482434`.
- Epic package release: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-db7c04d0070c
- Epic Actions artifact: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45230482434/artifacts/14848870030 (artifact name `maliwinlator-1.2.1-epic-arm64-debug`).
- Epic CI job completed successfully, including CPython 3.15.2 Android x86_64/ARM64 runtime packaging, managed emulator runtime smoke, JNI instrumentation, host APK and embedded companion packaging, package/ABI/ELF audits. This is not Xiaomi 15T device validation.

### Backend capability findings
- Pinned Legendary 0.21.1 already has Epic Games Store cloud-save primitives: remote save listing, requesting upload links, download/upload, local-vs-remote state comparison, and CLI `sync-saves` behavior. Do not implement a parallel undocumented datastorage protocol while this maintained implementation is available.
- Legendary's game metadata includes per-title cloud-save support flags (including Windows/macOS variants); use the Windows flag for this Winlator integration and do not infer support from the mere presence of remote files.
- Legendary's Epic API client includes `get_game_achievements(namespace)` and `get_game_achievements_user(namespace)` methods for Epic Store achievement definitions and the signed-in user's achievement state. This is distinct from relying on EOS Social Overlay.
- Epic documentation states that the EOS Social Overlay does not support Linux ARM64. That limitation does not invalidate Legendary's separate Epic Store GraphQL achievement queries. Game-side achievement unlocks still depend on the title's own supported integration and valid game auth/session context; the launcher must not fake unlocks.
- EOS Player Data Storage is not interchangeable with Epic Games Launcher Cloud Saves. Epic's own documentation explicitly distinguishes the launcher-managed service from game-side EOS Player Data Storage.
- Playtime is not currently exposed by this branch's Legendary library response or EpicBridge runner. Do not fabricate it or use install/launch timestamps as playtime. Investigate an authoritative Epic account endpoint before displaying it.

### Current code gap
- `epicbridge_runner.py` currently exposes login/status/library/installed/install/uninstall/launch_info and runtime tests only. It has no game-services/achievements/cloud-save command.
- `EpicBridgeProvider` currently does not allow achievements or cloud-save read/sync methods through its command switch.
- `EpicFragment.showGameDetails()` still renders static explanatory text for achievements, playtime and cloud saves.

### Next implementation sequence
1. Add a read-only per-title capability/details query using pinned Legendary APIs: confirm title metadata, query EGS achievement definitions and user state, query remote cloud-save metadata only for titles marked cloud-save-supported, and return bounded sanitized JSON.
2. Add provider allow-list and host bridge call support for that read-only command; test malformed app names, unauthenticated state, unsupported titles, missing namespace, empty data, API errors and large responses.
3. Render actual achievements and cloud-save availability/status in game details. Keep playtime explicitly unavailable until a trustworthy source is verified.
4. Add cloud-save preview and safe sync flow using Legendary's own state comparison. Before any download/upload, create a local backup and present a conflict decision; never silently overwrite newer local or remote data. Run sync before launch / after game exit only after process lifecycle and save-path handling are validated.
5. Add Android runtime tests and CI; then request Xiaomi 15T device validation.

### Safety / compatibility rules
- Cloud save support is title-specific. Require Legendary's per-game support flag and valid local save-path metadata; do not guess save paths.
- Do not sync while the game process is running. Never auto-download a remote save over a newer local save without explicit conflict resolution and a backup.
- Achievements are read-only in the launcher UI. Only the game should unlock achievements through its own legitimate integration; do not call unlock APIs to spoof progress.
- Keep tokens, presigned URLs, save contents, and raw API responses out of logcat and user-facing diagnostics.


## 2026-11-23 — First Epic account metadata endpoint

- Added Python bridge command `game_details` in commit `730c9c0324f12a0b7e9bd3f601546cbdfac43588`.
- Added `game_details` to the provider's explicit runtime-command allow-list in commit `72974c34bc24792f44fbbdba009913b5c734f1c7`.
- The command requires an authenticated Epic session, validates the app name, resolves the Windows title metadata, and makes read-only Epic Store achievement definition/user-state calls through Legendary's EPCAPI when a namespace and API object are available.
- It reports the title's cloud-save support flags and (when supported) only a remote-file count/availability bit. It deliberately strips signed URLs and raw save-file metadata. Playtime remains unavailable.
- This is a backend endpoint only: the details UI has not yet been wired to it, and live API responses have not yet been verified against an authenticated account. The response is bounded to 48 KiB per achievement payload.
- Next: verify LegendaryCore's EPCAPI member and model namespace/save-support fields against the exact pinned 0.21.1 package in CI; add mocked runner tests for supported/unsupported titles, API failures, missing namespace, malformed app names and oversized payloads. Then render actual achievement state and cloud-save status in the game page.
- No cloud save upload/download or mutation is enabled in this change.


## 2026-11-23 — Epic game-details endpoint repair and UI wiring

- CI log review identified the Epic runtime workflow failure on source commit `72974c34bc24792f44fbbdba009913b5c734f1c7`: Python import failed with an IndentationError in handle() because the game_details command branch had been inserted inside the library branch. The standard APK workflow passed, but the Epic runtime test job never reached its runtime/instrumentation/package stages.
- Fixed the dispatcher structure in commit `f0674d44de1b23d96aaffb1164270725b20a5356`.
- Corrected details metadata handling: Windows cloud-save support now uses only Legendary's Windows supports_cloud_saves flag; remote save metadata counts both list- and dictionary-shaped files results; app-name validation now rejects an actual NUL character.
- Added runner regression tests in commit `9039c900bc07983e5016f9cb3de154d903141f14` for Windows-vs-macOS capability flags, list/dictionary remote save records, and embedded-NUL app names.
- Connected the game-details screen to the read-only game_details command in commit `4d24258dcb7c502e498a1188f3533343f7faeee0`. The screen now reports whether Epic Store achievement definitions/account state were returned and whether Windows cloud saves are supported / remote records exist. It still does not display fabricated achievements, playtime, or perform save synchronization.
- Source-level UI/backend wiring is complete but CI validation for the latest branch head is pending. Do not install or distribute a new APK until both standard APK and Epic runtime/ARM64 workflows pass.
- Cloud-save synchronization remains disabled. Before enabling it, implement Legendary's local-vs-remote save-state comparison, backup/restore, conflict resolution, game-process exclusion, and a safe explicit user confirmation flow. Playtime remains unavailable pending an authoritative source.


## 2026-11-23 — Epic account-details endpoint and current ARM64 build validated

- Latest source commit: `523903bd9677ac2069acf954ea42f9863d4a34a1` (`test: provide account state in library dispatch fixture`).
- Both workflows completed successfully on this exact commit:
  - Standard APK run **45240800751**: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45240800751
  - Epic CPython/ARM64 runtime and package run **45240800887**: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45240800887
- Epic workflow job **137924615201** and standard APK job **137924617159** both concluded success.
- Epic package verification includes managed x86_64 runtime tests, Android JNI instrumentation, ARM64 Gradle assembly, APK signatures/package IDs/ABI/assets/ELF dependency closure and embedded companion audits.
- Published prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-523903bd9677
  - Host APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-523903bd9677/app-standard-debug.apk
  - Companion APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-523903bd9677/epicbridge-debug.apk
  - Checksums: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-523903bd9677/SHA256SUMS
- SHA-256:
  - `bb043773c7ae968d3fd6370c26f567739c0934f07c5c572a147e5c6254726525` — `app-standard-debug.apk`
  - `9af99e11700d387fecf0775b42cd4c8fc14814bbaa23978a00e2891eb3d1dd4f` — `epicbridge-debug.apk`
- Built package IDs: host `com.maliwinlator.app` (versionCode 24); companion `com.maliwinlator.epicbridge` (versionCode 2). These are debug-signed test packages, not production release builds.
- The branch now has a Python `game_details` command, provider allow-list entry and UI call. The command performs read-only achievement-definition/user-state queries and, only for Windows titles flagged as cloud-save-supported, reports remote record count/availability. It does not sync saves. Playtime remains explicitly unavailable.
- Regression coverage on the runner includes details dispatch, Windows-vs-macOS cloud-save capability, list/dictionary remote records, embedded NUL rejection and library dispatch fixture. CI passing proves the tested mocked/runtime packaging path, not authenticated Epic API behavior on the Xiaomi 15T.
- Important remaining gap: the game-details UI currently reports that achievement definitions/account progress were returned; it does not yet render individual achievement names, icons, unlock state or progress. The backend payload shape must be validated and safely normalized before presenting those fields.
- Device validation is now the next external gate: install the host APK and companion from the same release, sign in, open several game detail pages, and confirm the app remains responsive. Record whether achievement data and cloud-save counts appear; no cloud-save mutation is enabled.
- Next engineering work: inspect the exact Legendary 0.21.1 achievement response schemas, add bounded schema-aware normalization and tests, then render actual achievements without inventing progress. Separately design cloud-save preview/backup/conflict handling before any sync operation is enabled.

## 2026-11-23 — Achievement summaries rendered; build and tests passed

- Source commit: `57d6be1fe6a9619b70aec9994ae4873f428402df`.
- The Epic runtime workflow **45246911918** and standard APK workflow **45246912027** both completed successfully on this exact commit.
- Epic job `137946021591` and standard APK job `137946014197` concluded success. The Epic workflow ran Legendary 0.21.1 host/runtime checks, managed x86_64 Android runtime smoke, JNI instrumentation, ARM64 packaging, embedded companion and binary/dependency audits.
- Published prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-57d6be1fe6a9
  - Host APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-57d6be1fe6a9/app-standard-debug.apk
  - Companion APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-57d6be1fe6a9/epicbridge-debug.apk
  - Checksums: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-57d6be1fe6a9/SHA256SUMS
- Release asset SHA-256:
  - `909ac93d548ebbdb55de15ca2b8daec68a774b386110cdab36ade8ff1984f564` — `app-standard-debug.apk`
  - `bf99e7d8311c6d962a6993a840d89b025b8a5dbf7280c0fc85d7f638b8776337` — `epicbridge-debug.apk`
- New achievement backend uses Legendary 0.21.1 `core.get_achievements(game, update=True)` and normalizes its parsed summary. It caps entries at 100, bounds strings and response size, validates icon URLs, clamps progress, and redacts the display name, description and icon for locked hidden achievements.
- The game details UI now shows unlocked/total count, XP, up to 30 achievement entries, descriptions, locked/unlocked state and progress bars. This is source/CI verified; authenticated account data and title-specific accuracy still require device testing.
- Added runner tests for normalized summary output, hidden-achievement redaction, 100-entry cap and unavailable-summary behavior.
- Cloud saves remain read-only metadata only (support and remote record count); upload/download/sync is not enabled. Playtime remains unavailable rather than inferred.
- Next gate: device-test this release on the Xiaomi 15T with the host and companion APKs from this same release. Check sign-in, library navigation, game detail achievement list, and responsiveness while a download/install is active. Record title and whether achievement counts/items appear. Do not enable cloud-save mutation before preview, backup and conflict safeguards exist.


## 2026-11-23 — Achievement metadata hardening and detail-page lookup optimization

- Source commit: `d7a56423fe1a998e0ce9bb2cfa88ebad9231c42e`.
- Both CI workflows passed on this exact commit:
  - Epic CPython/runtime/ARM64 packaging and Android instrumentation: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45272578826
  - Standard Android APK build: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45272578903
- Published experimental prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-d7a56423fe1a
  - Host APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-d7a56423fe1a/app-standard-debug.apk
  - Companion APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-d7a56423fe1a/epicbridge-debug.apk
  - Checksums: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-d7a56423fe1a/SHA256SUMS
- APK SHA-256:
  - `14661e6637ec2f513cff42fbcc4e6e409fd732388d08806b9157e3263c9f7fb9` — host APK
  - `9325959f149688c2d30dd3a9543cd6b898ff70c9ef527f0893d870f7d67398f2` — companion APK
- Hardening: achievement numeric fields and text are type-checked and bounded; negative/malformed counters are normalized safely; non-finite progress is handled; icon URLs must be HTTPS with a hostname. Invalid metadata fields no longer invalidate the entire achievement section.
- Performance: per-game detail lookup now calls Legendary `core.get_game(app_name, update_meta=False, platform="Windows")` to reuse the already-populated Windows catalog instead of refreshing the full catalog whenever a detail page opens. Library enumeration remains responsible for metadata refresh.
- Regression tests cover malformed metadata and verify the cached metadata lookup flags.
- CI validates tests, runtime packaging and APK audits; it does not prove live authenticated Epic data behavior or responsiveness on the Xiaomi 15T.
- Cloud saves remain read-only metadata; upload/download/sync is not enabled. Playtime remains unavailable.
- Next device gate: install the host and companion APKs from this same release, sign in, open game details and confirm achievement cards populate. Then repeat the previously problematic navigation test while a game is actively downloading and installing, returning to Epic from another Winlator page. Record responsiveness/crashes and achievement accuracy.



## 2026-11-23 — Epic foreground-job polling isolation and malformed-response hardening

- Source commit: `069eead51fb8d69bc44e27a93dcd7ce424873a19`.
- Both required workflows passed on this exact source commit:
  - Standard APK workflow **45277644714**: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45277644714
  - Epic runtime/ARM64 package workflow **45277644511**: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45277644511
- Published debug prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-069eead51fb8
  - Host APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-069eead51fb8/app-standard-debug.apk
  - Companion APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-069eead51fb8/epicbridge-debug.apk
  - Checksums: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-069eead51fb8/SHA256SUMS
- SHA-256:
  - `11b7b15e2e496657a2523c36eed0aa1931fff22678f46c156b49d6aa8939025f` — host APK
  - `eb09e366ef363ad8fe257cd0ba94b2f07b41325cf0dc4ea2338d1c852d75b30d` — companion APK
- Host package is `com.maliwinlator.app` versionCode 24; companion is `com.maliwinlator.epicbridge` versionCode 2. Both are debug-signed test packages.
- Runtime/job lifecycle fixes since `d7a56423`:
  - Long-lived foreground-job polling is isolated from interactive Epic commands on a separate executor, so a download/install monitor should not monopolize the executor needed for library, details, or account requests.
  - Fragment teardown interrupts/detaches the monitor executor while preserving the persisted job identifier for a later fragment to resume monitoring.
  - Empty job identifiers are rejected before persisting or polling them.
  - Malformed foreground job responses now produce a bounded failure result instead of allowing invalid response data to destabilize the UI path.
  - Job start failure and active-job association handling were hardened.
- Existing achievement metadata validation and cached per-game metadata lookup optimization are included in this build.
- CI evidence includes managed x86_64 runtime tests, Android JNI instrumentation, ARM64 Gradle assembly, package/signature/ABI/assets/ELF dependency-closure audits and embedded companion checks. This is not a physical Xiaomi 15T test and does not prove authenticated live Epic responses.
- Cloud saves remain read-only metadata; no upload/download/sync is enabled. Playtime remains unavailable.
- Device validation remains required: sign in, inspect achievement cards on a title with achievements, and repeat navigation back to Epic while a game is actively downloading and installing. Confirm responsiveness, progress continuity, and absence of crashes.
- The branch subsequently advanced to documentation/publication commit `4f1b1fe8198ce457bea75d3294ec0e88140f47c5`; the validated application source for this release remains `069eead51fb8d69bc44e27a93dcd7ce424873a19`.


## 2026-11-24 — Foreground job monitor lifecycle handoff guard

- Application source commit: `a17df9acfb03b2a9203712e8e3092ba9d6034080`.
- Both required workflows passed on that exact source commit:
  - Standard Android APK workflow: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45280173776
  - Epic CPython/runtime/ARM64 workflow: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45280173630
- Published debug prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-a17df9acfb03
  - Host APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-a17df9acfb03/app-standard-debug.apk
  - Companion APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-a17df9acfb03/epicbridge-debug.apk
  - Checksums: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-a17df9acfb03/SHA256SUMS
- SHA-256:
  - `2d83aae7f8f2650ef157263e4e1ba58ec86d3333df031ecba1282ef55bb28064` — host APK
  - `694b89bb949a883e5dc33fefe3291f5d0cc3fa1134679b04a3de9a43df0ec294` — companion APK
- Source change: monitor submission now tolerates the Fragment being destroyed after the foreground service has accepted a job but before the monitor can be queued. A rejected submission no longer escapes as an executor lifecycle exception; the persisted job ID remains available for a replacement Fragment to reconnect.
- This complements the earlier separate job-polling executor, cancellation path separation, empty job-ID rejection and malformed status-response handling.
- CI completed managed x86_64 runtime smoke tests, Android JNI instrumentation, ARM64 APK assembly, embedded companion comparison and package/signature/ABI/assets/ELF dependency closure audits.
- These CI results do not replace physical-device validation. On the Xiaomi 15T, verify Epic sign-in, library/game detail and achievements, then navigate away and back during both active download and active install. Confirm the screen remains responsive, progress reconnects, and the job completes without duplicate starts or crashes.
- Cloud saves remain read-only metadata; no upload/download/sync is enabled. Playtime remains unavailable.
- Publication/docs commit on the development branch: `0a1c39539b49d8aa9a19410d4456ef9f865bfac6`.



## 2026-11-24 — Reconnect monitor shutdown-race fix validated and published

- Application source commit: `dd714b31428dcd8c1b61d2809d6aa99c495a2d4c`.
- Both required workflows passed on this exact source commit:
  - Standard APK workflow **45289767835**: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45289767835
  - Epic runtime/ARM64 workflow **45289767942**: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45289767942
- Epic workflow completed CPython 3.15.2 Android runtime build/audit, Legendary 0.21.1 host smoke tests, Epic library aggregation regression tests, managed x86_64 Android runtime smoke, JNI instrumentation, ARM64 runtime and APK packaging, and package/signature/ABI/assets/ELF dependency closure audits.
- Published debug prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-dd714b31428d
  - Host APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-dd714b31428d/app-standard-debug.apk
  - Companion APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-dd714b31428d/epicbridge-debug.apk
  - Checksums: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-dd714b31428d/SHA256SUMS
- APK SHA-256:
  - `0879fd25425d012831431fdb9af6fc36d3547cad5fe7b5d82edc2746303bbca5` — host APK
  - `a96fb5bfc44cf88cd6539eabcd59055d23afa9e38653747e565107fc363a3f72` — companion APK
- Source change: `resumePendingJob()` now submits its reconnect monitor through the same guarded `submitJobMonitor()` helper used for newly started jobs. If the executor has shut down during Fragment teardown, the rejected submission is contained, the polling flag is reset, and the persisted job ID remains available for a later Fragment to reconnect.
- This closes the corresponding direct-executor-submission gap in the reconnect path; it complements the earlier guard for newly started jobs. It does not yet prove the original active-download/active-install navigation freeze is resolved on the Xiaomi 15T.
- CI status document was automatically updated by the successful Epic workflow. Cloud saves remain read-only metadata; upload/download/sync is not enabled. Playtime remains unavailable.
- Next device validation: install both APKs from this same release, sign in, then navigate away from and back to Epic during an active download and during an active installation. Confirm the screen stays responsive, progress reconnects, the job is not duplicated, and no crash occurs. Also inspect achievement cards for a title known to have achievements.
- After device feedback, continue source audit of foreground-job state transitions and UI-thread work rather than changing the worker architecture without a reproduced defect.



## 2026-11-24 — Epic Library storefront refinement built and published

- Application source commit: `5f70c4b0da911fdd4587a7fe2bf38b99a7d4489a` (`ui: surface live Epic install state in featured and library cards`).
- The implementation continues the visual rebuild toward the previously discussed polished Epic storefront: structured EPIC GAMES header and collection subtitle, featured hero artwork, horizontal filter chips, larger game cards, detail-page artwork/overlay, clearer library navigation, and active-job state surfaced in featured/library cards.
- Search no longer causes the hero entrance animation to replay; the featured hero remains present while filtering/searching.
- Featured and library cards now read persisted active-job state and show a download/installation-in-progress label rather than a generic state when that game has an active operation.
- Standard APK workflow **45297673475** passed: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45297673475
- Epic runtime/ARM64 workflow **45297673513** passed: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45297673513
- Epic workflow verification includes managed x86_64 runtime tests, Android JNI instrumentation, CPython Android ARM64 runtime packaging, Legendary checks, ARM64 Gradle assembly, APK package/signature/ABI/assets/ELF dependency closure audits, and embedded companion comparison.
- Published debug prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-5f70c4b0da91
  - Host APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-5f70c4b0da91/app-standard-debug.apk
  - Companion APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-5f70c4b0da91/epicbridge-debug.apk
  - Checksums: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-5f70c4b0da91/SHA256SUMS
- SHA-256:
  - `e991cb49e85808c84016590565909cc475872fa7c867a3d7cfa08192ed33c901` — host APK
  - `46c22b6ef447afb6db9b06bdca9d3e8ec08009d92b752925e1843d67494cbcbc` — companion APK
- Host package: `com.maliwinlator.app`, versionCode 24. Companion: `com.maliwinlator.epicbridge`, versionCode 2. Both are debug-signed test builds.
- The CI status file was automatically updated by the Epic workflow. CI success is not physical-device validation; no screenshot comparison or Xiaomi 15T test of this storefront commit has yet been performed.
- Cloud-save integration remains read-only metadata; no save upload/download/sync is enabled. Playtime remains unavailable. Achievement UI displays only returned and normalized data.
- Next: continue visual hierarchy/card/detail polish without replacing the working Epic download engine, then run both workflows. After a validated build is ready, request device testing of the storefront and the previously problematic navigation during active download and active installation.


## 2026-11-24 — Epic storefront artwork/card refinement validated and published

- Application source commit: `676eb49e1d3b7b985dddc72e895a5b989c3c7b6d`.
- Updated library cards with larger artwork, gradient shading, clearer status pills, stronger title hierarchy and animated press feedback. Trusted Epic artwork URLs are used when available; missing/invalid artwork retains the designed fallback rather than fabricated game art.
- Standard APK workflow **45300733132** passed: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45300733132
- Epic runtime/ARM64 workflow **45300733162** passed: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45300733162
- Epic workflow completed managed x86_64 runtime prerequisite smoke tests, Android JNI instrumentation, ARM64 runtime/APK packaging and package/signature/ABI/assets/ELF dependency closure audits.
- Published debug prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-676eb49e1d3b
  - Host APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-676eb49e1d3b/app-standard-debug.apk
  - Companion APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-676eb49e1d3b/epicbridge-debug.apk
  - Checksums: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-676eb49e1d3b/SHA256SUMS
- SHA-256:
  - `013b0861988f59034a3f3daa828a46ea95e67ad2569957d4887dda51e5682966` — host APK
  - `77d2fd5669eed7445e1841621e18ccff947bece6153c9bf162e175657128a2af` — companion APK
- Host package: `com.maliwinlator.app`, versionCode 24. Companion: `com.maliwinlator.epicbridge`, versionCode 2. Both are debug-signed test builds.
- CI success does not establish physical Xiaomi 15T validation. This build still needs device testing of the storefront, game details, achievements, and navigation during active downloads and installations.
- Cloud saves remain read-only support metadata; no upload/download/sync is enabled. Playtime remains unavailable.
- Next UI task: split the current single GAME INFORMATION panel into visually distinct achievements, playtime and cloud-save sections. Keep achievements sourced only from Epic; do not invent playtime or claim cloud-save synchronization.


## 2026-11-24 — Epic game profile information cards validated and published

- Application source commit: `4bf04b6402dc0e3392758697519016fa2809576f` (`ui: separate Epic game profile information cards`).
- Replaced the dense GAME INFORMATION panel in `EpicFragment.showGameDetails(...)` with three distinct sections: ACHIEVEMENTS, PLAYTIME and CLOUD SAVES.
- Achievement content remains driven by the real Epic metadata response, including normalized achievement entries, progress, locked/hidden/unlocked state, rewards and dates (existing display cap retained). No synthetic achievements were added.
- Playtime explicitly states that the Epic library response does not currently provide it. Cloud saves display Windows save-support metadata only and explicitly state that save synchronization is not enabled. No cloud-save transfer behavior or download/install worker changes were introduced.
- Standard APK workflow **45302814407** passed: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45302814407
- Epic runtime/ARM64 workflow **45302814428** passed: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45302814428
- Epic workflow completed all 53 steps successfully, including Legendary 0.21.1/library aggregation regression checks, managed x86_64 Android runtime smoke, Epic Bridge x86_64 Android instrumentation, production Android ARM64 CPython runtime build/audit, ARM64 host/companion APK assembly, embedded companion comparison, package/signature/ABI/assets/ELF dependency-closure audits and release publication.
- Published debug prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-4bf04b6402dc
  - Host APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-4bf04b6402dc/app-standard-debug.apk
  - Companion APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-4bf04b6402dc/epicbridge-debug.apk
  - Checksums: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-4bf04b6402dc/SHA256SUMS
- APK SHA-256:
  - `5578c566ae6c4751162a07a620b8a94074f5c404a8f46f9e8e04924618df7d01` — host APK
  - `402054d449f9d2428548e02cd815065b9938e048b05da067099c16bd694c0755` — companion APK
- Host package: `com.maliwinlator.app`, versionCode 24. Companion: `com.maliwinlator.epicbridge`, versionCode 2. Both are debug-signed test builds.
- CI status file was automatically updated by the successful Epic workflow. CI/managed-emulator verification does not establish physical Xiaomi 15T validation. The new profile sections and storefront still require device review; also recheck navigation away/back during active downloads and active installations.
- Cloud saves remain read-only support metadata; no upload/download/sync is enabled. Playtime remains unavailable. Achievements display only returned Epic data.
- Next: continue the storefront/profile UI audit against the desired polished Epic launcher experience, keeping persisted job state and the existing download/install engine intact. Only add controls when their backend actions are genuinely implemented. After the next published build, request a focused device validation pass.


## 2026-11-24 — Epic library install-ready filter correction (published)

- Source commit: `e7a2080ea4135c4a5dfe5916d6ae249891cc8ee9`.
- EpicFragment.renderGameCards(...) now excludes catalog entries unsupported by the Epic bridge from the **Ready to install** filter. Existing installed and active-job exclusions remain in place. Filter-specific empty-state copy now distinguishes Installed, Ready to install, Downloading and search-no-results states.
- No download/install worker or IPC behavior changed.
- Standard APK workflow **45309341133** passed: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45309341133
- Epic runtime/ARM64 workflow **45309341058** passed: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45309341058
- Epic workflow completed successfully, including managed x86_64 runtime smoke, Android JNI instrumentation, CPython 3.15.2 x86_64/ARM64 runtime packaging, ARM64 APK assembly, package/signature/ABI/assets/ELF dependency closure and embedded-companion audits.
- Published debug prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-e7a2080ea413
  - Host APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-e7a2080ea413/app-standard-debug.apk
  - Companion APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-e7a2080ea413/epicbridge-debug.apk
  - Checksums: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-e7a2080ea413/SHA256SUMS
- APK SHA-256 (from the workflow-published PROJECT_EPIC_CI_STATUS.md):
  - `a50f75d041eddf81ec7274b627843981dc6cefd96333c8e16c67c9a8ea22b896` — host APK
  - `c4f4fa7f6e936b207d02a0ab35731c63e908b7071c38353f3cc9974e7e621e32` — companion APK
- Host package `com.maliwinlator.app` versionCode 24; companion `com.maliwinlator.epicbridge` versionCode 2. Debug-signed test build.
- CI success does not prove physical Xiaomi 15T validation. Device review still needed for Ready to install filter contents/empty states and existing active-download/install navigation responsiveness.
- Prior PROJECT_EPIC_CI_STATUS.md was automatically updated by workflow run 45309341058 and records the above hashes and release URL.



## 2026-11-24 — Epic storefront visual redesign (published)

- Source commit: `b464202c12583750984507af3573da14203dbe34`.
- Reworked the Epic Library presentation in `EpicFragment.java`: horizontally browsable cover-art game rail replaces the two-column grid; larger featured hero; stronger storefront typography and status treatment; larger artwork-led game detail header; staggered card entrance and press animations.
- Preserved real Epic Bridge install eligibility, installed state, persisted active-job identity, real progress/cancel behavior, achievement metadata and read-only cloud-save support metadata. No mock progress, invented playtime or save synchronization was introduced.
- Standard APK workflow **45388889849** passed: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45388889849
- Epic runtime/ARM64 workflow **45388889914** passed: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45388889914
- Epic workflow completed managed x86_64 runtime smoke, Android JNI instrumentation, CPython 3.15.2 Android x86_64/ARM64 packaging, ARM64 APK assembly, package/signature/ABI/assets/ELF dependency-closure and embedded-companion audits.
- Published debug prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-b464202c1258
  - Host APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-b464202c1258/app-standard-debug.apk
  - Companion APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-b464202c1258/epicbridge-debug.apk
  - Checksums: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-b464202c1258/SHA256SUMS
- APK SHA-256:
  - `75f45ed9c272743dcd4ca3e09af362795384cb4365f3382a0247bb661c3067f1` — host APK
  - `68377c428a66d945142839884525e29c9d46c604910641bc6dd33d18da3a543f` — companion APK
- Host package `com.maliwinlator.app` versionCode 24; companion `com.maliwinlator.epicbridge` versionCode 2. Debug-signed test build; a different debug signing certificate may require uninstalling an older debug package before installing.
- CI and managed-emulator success do not establish physical Xiaomi 15T visual validation. User review is required for the storefront appearance, artwork coverage, navigation, and active-download progress.
- Known feature boundaries remain: playtime unavailable; cloud saves are support metadata only with no upload/download/sync; achievements display real returned Epic metadata and are unlocked by games through their supported Epic/EOS integration, not by launcher UI.
- Next: get user's visual review of this build, then refine the hero/card/detail composition based on the actual result. Keep all download/install actions connected to the existing worker and never fake metadata or progress.


## 2026-11-24 — Full Epic storefront redesign target recorded

- User feedback: the published storefront is still far from the previously approved interactive demo. Do not characterize the current visual changes as recreating that experience.
- Source audit finding: `EpicFragment` remains a vertically stacked programmatic form; the featured hero is fixed-height and selects the first non-DLC result; game cards are generic horizontal-rail tiles; details remain a stack of generic metadata panels; connection/setup status and navigation still frame the library as a settings page. Small color/size/animation changes are insufficient.
- Added `docs/EPIC_STOREFRONT_FULL_REDESIGN.md` (commit `1228cb0399cc5762be2c5fc85950b4ca6754a74e`) defining the new target: a dedicated premium storefront, deterministic featured selection, real-data shelves, immersive game profiles, integrated live job state, coordinated transitions, and accessibility/responsive requirements.
- This is a design/engineering specification commit only; no APK was built or published from it. Next source milestone must implement a structural screen-composition/state redesign while preserving Epic Bridge/Legendary and foreground job behavior.
- No screenshot of the lost demo is currently available in the project context. If the user supplies one, use it as the visual reference; do not block the redesign on it.


## 2026-11-24 — Epic storefront structural UI milestone (published)

- Source commit: `36362097b5380d8a5b8df09e3b34b83cb6398cb9`.
- Moved Epic Bridge connection/diagnostic status out of the player-facing Library and into Account & setup. Library now has a clearer player-focused heading and spacing.
- Featured selection now prefers the last opened title, then an installed non-DLC title, then the first available non-DLC title. Last-opened selection is persisted in app preferences.
- Game detail profile artwork header increased to 340dp with an overlaid Library back action. Detail view hides storefront brand header and main navigation to create a more focused profile presentation; returning to a section restores them.
- Standard workflow **45395285653** passed: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45395285653
- Epic runtime/ARM64 workflow **45395285682** passed: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45395285682
- Workflow-published prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-36362097b538
  - Host APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-36362097b538/app-standard-debug.apk
  - Companion APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-36362097b538/epicbridge-debug.apk
  - Checksums: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-36362097b538/SHA256SUMS
- Host APK SHA-256: `d9be53ef3c24341f513de0f010a02f2b96603ab631cb72fa5f3d9dffbf56dd06`.
- Companion APK SHA-256: `00dac092f115cab38808f81dd7d5b08cf10b554ff5405ff8684bc6ed5c6149f4`.
- Host package `com.maliwinlator.app` versionCode 24; companion `com.maliwinlator.epicbridge` versionCode 2.
- Automated workflow checks passed; this is not physical Xiaomi 15T validation. No visual claims beyond source changes should be made until device review.
- This is an intermediate structural milestone, not a recreation of the lost demo. Next work should continue replacing the generic profile/library composition with a more intentional, artwork-led launcher experience while retaining real job state and worker integration. Avoid cosmetic-only commits.


## 2026-11-24 — Epic storefront shelves and catalog artwork pipeline (in progress)

- Shelf implementation source commit: `262c15c6df1292be5fa25ca381aa0d4911c1339b`. The All games view now groups real titles into Recently opened, Installed on this device, Active downloads & installs, Ready to install, and remaining Epic collection shelves. Existing filter/search behavior and actual install worker state are retained. A filter-name mismatch was corrected before this commit.
- Epic catalog artwork source commit: `b070391750062269f69299e3c56d8cbdeda36fea`. The companion now extracts separate portrait and landscape image URLs from Epic `keyImages` metadata, preferring known portrait box-art and wide offer-art types. Only bounded HTTPS URLs are returned; host-side trusted Epic/Unreal hostname validation remains the final display gate.
- Hero/profile image integration source commit: `11ca680a01df6ff8248e2ac3b91b31ee0c8bba68`. Featured hero and profile use `hero_url` with the existing cover as fallback. A follow-up correction at source commit `f545189719eae98715d75bea55392d4725026a4c` restores portrait `cover_url` for compact cards and reserves `hero_url` for profile surfaces.
- Standard APK workflow **45402633259** passed for source commit `11ca680a01df6ff8248e2ac3b91b31ee0c8bba68`: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45402633259
- Epic runtime/ARM64 workflow **45402633156** is still running for source commit `11ca680a01df6ff8248e2ac3b91b31ee0c8bba68`: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45402633156
- Important build caveat: the current running workflows checked out `11ca680`, not the later card/profile URL correction `f545189`. The final correction still needs a fresh standard + Epic workflow validation before any APK from it is described as tested or published.
- No new release link is published for these changes yet. Do not ask for physical device review until a build containing the final source commit is published.
- Next: finish the active Epic workflow, add regression coverage for artwork selection, trigger both workflows against the final branch head, verify the published release/checksums, then append the completed milestone here. This remains an intermediate storefront milestone, not a claim of recreating the lost interactive demo.

## 2026-11-24 — Epic artwork runtime regression fixed and prerelease published

- Final source commit: `56c195db66d3a1e9255fc1f8bb5d042d189988ed`.
- Root cause of the prior Epic runtime/ARM64 workflow failure: the artwork refactor replaced the old `_cover_image()` helper with `_artwork_images()`, but the embedded Android runtime self-test still called the removed helper. That NameError was surfaced by the bridge as a generic `EPIC_OPERATION_FAILED`.
- Fix commit `db170da71ae10ee0336db2636e05d08e66e61438` updated the runtime self-test to validate independent portrait-cover and wide-hero selection and reject non-HTTPS artwork URLs.
- Regression-test commit `56c195db66d3a1e9255fc1f8bb5d042d189988ed` adds host-side tests for independent portrait/landscape selection, rejection of non-HTTPS and oversized URLs, and safe empty output for missing or malformed catalog artwork. Tests were added; none were disabled.
- Standard Android APK workflow **45418865343** passed on the exact final source SHA: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45418865343
- Epic runtime/ARM64 workflow **45418865213** passed on the exact final source SHA: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45418865213. The workflow validates managed x86_64 runtime behavior, Android JNI instrumentation, CPython 3.15.2 Android ARM64 packaging, APK assembly, package/signature/ABI/assets/ELF dependency closure, and embedded companion checks.
- Published debug prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-56c195db66d3
  - Host APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-56c195db66d3/app-standard-debug.apk
  - Companion APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-56c195db66d3/epicbridge-debug.apk
  - Checksums: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-56c195db66d3/SHA256SUMS
- Host APK SHA-256: `44344aa3156fc5afa7d44d637c20d53c9312e90e5fc1081aec7cb83022d2e311`.
- Companion APK SHA-256: `860f5143d8e40a83da543047835c256fe4c0fa42cf8d3135963d49b752909042`.
- Host package `com.maliwinlator.app` versionCode 24; companion package `com.maliwinlator.epicbridge` versionCode 2. These are debug-signed test APKs.
- GitHub release API confirms the prerelease is published and contains both APKs and `SHA256SUMS`. The standard workflow artifact and Epic ARM64 workflow artifact were also produced from the same source SHA.
- CI/managed-runtime success is not physical-device validation. The new build has not yet been visually or functionally validated on the Xiaomi 15T.
- This is an intermediate shelves/artwork/runtime milestone, not a recreation of the lost interactive demo. Do not claim visual parity. Continue with a substantial structural storefront redesign, using real Epic metadata and the existing download worker; do not invent playtime, cloud-save sync, or progress.


## 2026-11-24 — Epic portrait-card / wide-profile artwork correction published

- Source commit: `5c98e482d6fa694c404ea8cb5529d0d7ac89fd13`.
- Corrected artwork roles in `EpicFragment.java`: compact library cards now prefer portrait `cover_url` with wide-hero fallback, use 158dp card width and 206dp artwork height; game profiles now prefer wide `hero_url` with portrait fallback. Featured hero already used wide artwork.
- Standard APK workflow **45440601790** passed on the exact source SHA: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45440601790
- Epic runtime/ARM64 workflow **45440601184** passed on the exact same SHA: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45440601184. All 53 workflow steps succeeded, including managed Android runtime smoke, production ARM64 CPython build/audit, Epic Bridge x86_64 instrumentation, ARM64 host/companion APK build, package/signature/ABI/assets/ELF dependency-closure audits, and prerelease publication.
- Published debug prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-5c98e482d6fa
  - Host APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-5c98e482d6fa/app-standard-debug.apk
  - Companion APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-5c98e482d6fa/epicbridge-debug.apk
  - Checksums: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-5c98e482d6fa/SHA256SUMS
- APK SHA-256:
  - `f668eb6af953331084b297b37c2055dd719817bd610ac23a5fe1c395d50f0f2d` — host APK
  - `68ec30543a2e808e60c5b557a1eb9c95c44c0ae622aca202c886943cb48bf8d0` — companion APK
- Host package `com.maliwinlator.app` versionCode 24; companion `com.maliwinlator.epicbridge` versionCode 2. Both are debug-signed experimental builds.
- CI success is not physical Xiaomi 15T validation. The portrait/hero composition still requires visual review on-device; do not claim that this change recreates the lost interactive demo.
- Existing capability boundaries remain: achievements display returned Epic metadata and are unlocked by games through supported Epic/EOS integration; playtime is unavailable; cloud saves remain support metadata only with no upload/download/sync.
- Next engineering task: proceed beyond the intermediate artwork correction to implement the structural storefront/profile composition in `docs/EPIC_STOREFRONT_FULL_REDESIGN.md`. Keep real Epic catalog data and the existing persisted install/download worker; do not add mock progress or imply unavailable services.


## 2026-11-24 — Game profile hero now owns its primary action and live progress (CI pending)

- Source commit: `237b043c623b8cd9f8f07177503b33b6fe27d2e6`.
- Re-composed the profile's top section so Install / Play / Cancel and the existing live download/install progress card sit inside the cinematic artwork hero instead of appearing as detached panels beneath it.
- Increased the profile hero height from 340dp to 430dp to accommodate title, status, action and progress while retaining the existing game artwork and gradient treatment.
- This changes only view composition. It continues to use the existing persisted job ID, foreground service, job monitor, cancel operation and real progress snapshots; no second worker or fabricated progress was introduced.
- Epic workflow **45445775858** was queued for this exact source SHA when this entry was written: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45445775858. Do not describe this source revision as build-validated or publish it until both required workflows complete successfully.
- This is a focused profile-composition step toward `docs/EPIC_STOREFRONT_FULL_REDESIGN.md`, not yet the complete storefront redesign or a recreation of the lost demo.


## 2026-11-24 — Integrated Epic game-profile hero build passed and published

- Source commit: `237b043c623b8cd9f8f07177503b33b6fe27d2e6`.
- Profile composition now places the existing Install / Play / Cancel action and live persisted download/install progress inside the wide artwork hero; hero height increased to 430dp. No worker, progress, or cancellation semantics were replaced.
- Standard APK workflow **45445775875** passed on this exact SHA: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45445775875
- Epic runtime/ARM64 workflow **45445775858** passed on the exact SHA: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45445775858. Managed x86_64 runtime smoke, production ARM64 runtime build/audit, Epic Bridge instrumentation, APK packaging audits and prerelease publication all passed.
- Published debug prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-237b043c623b
  - Host APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-237b043c623b/app-standard-debug.apk
  - Companion APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-237b043c623b/epicbridge-debug.apk
  - Checksums: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-237b043c623b/SHA256SUMS
- Asset SHA-256:
  - Host APK: `f9bfc2fccdf07f5ae2287be9b716d5b580b5db18cb92fda3949012aea0214f64`
  - Companion APK: `84629b7314550ea65fdabfb6bb8ac0643b471938ab4010a6048b5b1e02496abc`
- Host package `com.maliwinlator.app` versionCode 24; companion `com.maliwinlator.epicbridge` versionCode 2. Both debug-signed experimental builds; installation over a build signed with a different debug certificate may require uninstalling that package first.
- These are CI-validated artifacts, not yet visually or functionally validated on the Xiaomi 15T.


## 2026-11-24 — Dedicated Epic storefront header and account navigation (CI passed; prerelease published)

- Source commit: `4f618a315b88ef458968a7d25ad8a227881919e4`.
- Removed the full-width Library/Account segmented navigation bar from the storefront root. Library is now the default dedicated launcher surface; Account & setup remains reachable from a compact Account action in the brand header.
- Tightened root insets, increased the collection heading hierarchy, and reduced search/filter control height so the library's artwork and game shelves carry more of the visual weight.
- Account/setup workflow and the existing `showSection("account")` path are retained. Game profile still hides the storefront header and its back action returns to Library.
- Both required workflows passed for the exact source SHA `4f618a315b88ef458968a7d25ad8a227881919e4`:
  - Standard APK: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45464666917
  - Epic runtime/ARM64: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45464666941
- Prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-4f618a315b88
  - Host APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-4f618a315b88/app-standard-debug.apk
  - Companion APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-4f618a315b88/epicbridge-debug.apk
  - Checksums: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-4f618a315b88/SHA256SUMS
- GitHub release asset SHA-256:
  - Host APK: `1c3ca3b5cb82237b6605f13cc65584f4eddc1dfffb95f0e23dfbcfe4f330ed2a`
  - Companion APK: `87553aa70077fbb581157e939032ed43bcbae697252e191b65ac4d4877394a12`
- Host package `com.maliwinlator.app` versionCode 24; companion `com.maliwinlator.epicbridge` versionCode 2. Both are debug-signed experimental builds; installation over a build signed with a different debug certificate may require uninstalling that package first.
- CI validation passed; this does not establish visual or functional validation on the Xiaomi 15T. The larger immersive storefront/profile redesign remains the next implementation task.


## 2026-11-24 — Actionable featured hero and preserved animated profile navigation (CI pending)

- Source commit: `2b5b945ce5c4dcdd8296badf246b426ac0eaaa39`.
- The storefront featured hero now uses the actual game state for its primary action: Play for installed titles, Install when supported and storage is ready, View Progress for an active job, and profile/details when installation cannot safely start from the home screen.
- Opening a profile now captures the storefront scroll position. Returning through the profile back control animates the profile out, restores the library at its previous vertical position, and animates the storefront back in.
- Profile entrance motion now uses a coordinated fade, vertical translation, and subtle scale with a decelerating interpolator. The existing account, library, Epic Bridge, Legendary worker, persisted job ID, and progress paths are unchanged.
- This is a navigation/interaction improvement, not yet the complete approved premium visual redesign. The larger storefront/profile composition remains in progress.
- Standard APK and Epic runtime/ARM64 CI results are pending for this source revision. Do not publish until both pass; no Xiaomi 15T validation has occurred.


## 2026-11-24 — Actionable featured hero and animated profile navigation (CI passed; prerelease published)

- Source commit: `2b5b945ce5c4dcdd8296badf246b426ac0eaaa39`.
- The featured hero now chooses Play, Install, View Progress, or View Details from actual install/job/storage state. Profile navigation preserves the storefront scroll position and uses coordinated entrance/return animations.
- Standard APK workflow **45469607954** passed: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45469607954
- Epic runtime/ARM64 workflow **45469607855** passed: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45469607855. Managed x86_64 runtime smoke, Epic Bridge instrumentation, production ARM64 runtime audit and APK packaging completed successfully.
- Published debug prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-2b5b945ce5c4
  - Host APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-2b5b945ce5c4/app-standard-debug.apk
  - Companion APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-2b5b945ce5c4/epicbridge-debug.apk
  - Checksums: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-2b5b945ce5c4/SHA256SUMS
- Asset SHA-256:
  - Host APK: `87e39fb156865a1b7d3c888d4343415f0d3db040e508bdb88996d28fdc1e17ac`
  - Companion APK: `fb4255e06fdbaaa2d7415ffead04ad34d818a3bc8f5b10ddd9d4f2779d261b67`
- Both packages are debug-signed experimental builds. CI success is not physical Xiaomi 15T validation.

## 2026-11-24 — Game profile metadata redesigned into compact paired cards (CI pending)

- Source commit: `3322ff8103257b12b1287c54004dca849984c39f`.
- Replaced the vertically stacked playtime and cloud-save panels with a paired two-column metadata row beneath the achievements section. This gives the profile a clearer hierarchy and reduces repeated full-width settings-style panels.
- Playtime remains explicitly unavailable because Epic library responses do not provide it. Cloud-save metadata remains status-only; no save upload/download/sync is implied or performed.
- Epic runtime/ARM64 workflow **45472430840** is running: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45472430840
- Standard APK workflow has not yet appeared in the run list. Do not publish this source revision until both required workflows pass.
- No Xiaomi 15T visual validation has occurred.

## 2026-11-24 — Achievement rail and storefront hierarchy work

- Achievement cards in the game profile now form a horizontal rail instead of a long vertical list. The existing read-only Epic achievement fields, XP/tier/unlock/progress rendering, 30-card cap, and truncation notice are retained.
- Source commit: `9d4dbca79e284e1713e90c57ba57d1e8d1d3faa2`.
- Standard APK workflow **45477399053** passed: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45477399053
- Epic runtime/ARM64 workflow **45477398998** passed: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45477398998
- This verifies compilation/runtime packaging only; it is not a Xiaomi 15T visual test and does not add achievement unlocking functionality.
- Follow-up source commit `7f218d4fa4d775a0c03da805eb9ffbd936e479fd` changes the storefront hierarchy so the cinematic featured game leads immediately beneath the brand header, with collection heading/search/filters following it, and expands the hero from 310dp to 356dp. Its CI has not yet been observed; do not publish it until both required workflows pass.
- The larger redesign remains in progress. No claim of reproducing the lost original demo pixel-for-pixel is made.


## 2026-11-24 — Featured-first storefront hierarchy (CI passed; prerelease published)

- Source commit: `7f218d4fa4d775a0c03da805eb9ffbd936e479fd`.
- Moved the cinematic featured game immediately beneath the compact brand header; collection identity, search and filters now follow the hero. Increased hero height from 310dp to 356dp and tightened the collection heading/subtitle hierarchy.
- Standard Android APK workflow **45484179285** passed on the exact source SHA: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45484179285
- Epic runtime/ARM64 workflow **45484179167** passed on the exact source SHA: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45484179167. CPython 3.15.2 Android x86_64/ARM64 packaging completed successfully; the workflow also produced the ARM64 debug APK artifact.
- Published debug prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-7f218d4fa4d7
  - Host APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-7f218d4fa4d7/app-standard-debug.apk
  - Companion APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-7f218d4fa4d7/epicbridge-debug.apk
  - Checksums: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-7f218d4fa4d7/SHA256SUMS
- GitHub asset SHA-256: host APK `11490ce5bae50211ac9ad2bd5347af0ce5cdf2e783a7a47b0b97b65cf9d2d45b`; companion APK `b34976ddc9b4fd0928d839e7257aeb1441521e84acff0a115841762a5deec7d4`.
- Both APKs are debug-signed experimental builds. CI/runtime packaging success is not visual or functional validation on the Xiaomi 15T; do not claim the new composition has been device-tested or that it reproduces the lost demo.
- This remains an intermediate composition milestone. Continue with a genuinely substantial launcher/profile redesign rather than more isolated spacing/color tweaks; preserve real Epic metadata and the existing persisted foreground download worker.


## 2026-11-24 — Storefront motion and accessibility refinement (validation pending)

- Source commit: `698661a3a4343c09996a2267c840f36b1ee5030d`.
- Added one-shot entrance animation state for the featured hero and game cards. Search/filter re-renders no longer replay the entire card entrance sequence; subsequent renders place cards directly in their final visual state.
- Added a concise TalkBack content description to each clickable game card, including its real install/download status and the action to open details.
- Reviewed the exact GitHub commit diff and confirmed only `EpicFragment.java` changed. The source branch head is this commit.
- No CI runs were created for this commit at the time of this handoff update. The latest published build remains the prior validated source SHA `7f218d4fa4d775a0c03da805eb9ffbd936e479fd`; do not install/publish the new source revision until both standard APK and Epic runtime/ARM64 workflows validate it.
- Next: obtain CI validation for `698661a`, then continue the larger composition work. Do not treat the animation/accessibility refinement as the complete visual redesign.


## 2026-11-24 — Achievement presentation upgrade (validation pending)

- Source commit: `1eb94b4f926d3fb39902a205f2f30626c304c0ca`.
- The game profile now renders Epic's existing per-achievement `icon_link` artwork in the horizontal achievement rail, subject to the same trusted Epic artwork host validation used elsewhere in the UI. Text fallback remains when an icon URL is absent or rejected.
- Added an overall completion bar calculated strictly from Epic's returned unlocked/total achievement counts. The summary still shows the returned XP values; no local progress is invented and no achievement unlock behavior is added.
- Standard APK workflow **45495719392** is in progress: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45495719392
- Epic runtime/ARM64 workflow **45495719482** is pending: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45495719482
- Previous animation/accessibility commit `698661a3a4343c09996a2267c840f36b1ee5030d` has standard APK workflow **45495158540** passed; Epic runtime workflow **45495158521** remains in progress: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45495158521
- Do not publish `1eb94b4` until both workflows for that exact SHA pass. No physical Xiaomi 15T validation has occurred.


## 2026-11-24 — Achievement profile interaction (validation pending)

- Source commits: `9404e027a5ae38be7842d1b920da11395bc4cf36` (tap-to-open achievement detail dialog) and `9df83c7fed3981373cab3e661c6d249450bf77e9` (stable final title capture in click listener).
- Achievement rail cards are now actionable: tapping a card opens a compact detail dialog with the actual description, unlock/hidden/in-progress state, XP, tier/rarity, unlock date, and progress where available. The card also exposes a useful accessibility content description and keyboard focusability.
- No achievement state is modified; this is a read-only presentation of the account metadata already returned by Legendary.
- Standard APK workflow for final commit **45502418513** is running: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45502418513
- Epic runtime/ARM64 workflow for final commit **45502418476** is pending: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45502418476
- Earlier achievement artwork/completion commit `1eb94b4f926d3fb39902a205f2f30626c304c0ca` passed both workflows **45495719392** and **45495719482**. Its standard APK artifact is available from the workflow run page; the Epic runtime run also uploaded its ARM64 artifact.
- Do not publish the newer interaction commit until both workflows for the exact final SHA pass. No physical Xiaomi 15T validation has occurred.


## 2026-11-25 — Profile entry scroll correction (CI pending)

- Source commit: `2e56f2f004b12f335a3294fb4256fc68b005b58b`.
- Fixed a navigation defect in the shared storefront scroll container: opening a game profile now resets the outer scroll position to the top after the profile view is attached. Without this, opening a profile from a deeply scrolled shelf could leave the cinematic artwork/title clipped above the viewport.
- Returning to the Library continues to restore the previously saved Library scroll offset through the existing `returnToLibraryFromProfile()` path.
- This change does not alter Epic Bridge calls, Legendary behavior, persisted job identity, foreground install/download execution, or live progress polling.
- The prior achievement interaction source SHA `9df83c7fed3981373cab3e661c6d249450bf77e9` passed both standard APK workflow **45502418513** and Epic runtime/ARM64 workflow **45502418476**.
- CI for `2e56f2f` has not yet been confirmed. Do not publish until both required workflows pass for this exact SHA. No Xiaomi 15T device validation has occurred.
- Next visual milestone remains the substantial storefront/profile composition redesign described in `docs/EPIC_STOREFRONT_FULL_REDESIGN.md`; this scroll fix is a correctness improvement, not a claim that the lost interactive demo has been reproduced.


## 2026-11-25 — Profile scroll correction validated; storefront hero/shelf refinement (new CI pending)

- Profile scroll correction source SHA `2e56f2f004b12f335a3294fb4256fc68b005b58b` passed both required checks:
  - Standard APK workflow **45506270195**: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45506270195
  - Epic runtime/ARM64 workflow **45506270200**: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45506270200
- Follow-up source commit: `b40b890b8e53f7cdf05a537ff966902e4d9a3f50`.
- Featured hero now exposes two clear actions: a state-aware primary Play/Install/View Progress action and a separate Game Details action. Previously users had to tap the hero CTA or artwork to reach the profile; the dedicated secondary action makes the distinction explicit.
- Increased game shelf cards from 158dp to 174dp wide and portrait artwork from 206dp to 232dp high to make the game-cover rail more prominent on phone screens.
- Existing actual job state, install support checks, worker, persisted job identity, and progress polling are unchanged. This is a presentation/layout improvement, not new download functionality.
- Standard APK workflow **45508732700** and Epic runtime/ARM64 workflow **45508732689** are queued/running for the exact new source SHA. Do not publish until both pass. No Xiaomi 15T validation has occurred.
- The dedicated storefront/profile composition redesign remains ongoing; these incremental improvements do not claim parity with the unavailable original demo.


## 2026-11-25 — Live library overview beneath featured artwork (CI running)

- Source commit: `24da555340fb28920733dfea7548540ede3135c4`.
- Added a compact three-metric overview directly beneath the featured hero: total non-DLC games in the Epic catalog, locally installed games, and catalog entries currently eligible for installation.
- All values are derived from the current Epic catalog and local installed-state data; no sample values, invented download progress, or provider-side claims are used. The overview is shown on the unfiltered Library home and is rebuilt alongside the featured hero.
- The existing install/download worker, persisted job state, artwork URL validation, profile route, and account flow are unchanged.
- Standard APK workflow **45513947492** is running: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45513947492
- Epic runtime/ARM64 workflow **45513947490** is queued: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45513947490
- Do not describe this source revision as validated or publish it until both workflows pass on this exact SHA. No Xiaomi 15T device validation has occurred.
- This is a hierarchy/data-presentation step, not the completed premium storefront redesign or a claim of reproducing the lost demo.


## 2026-11-25 — Live counts on collection filters (CI queued)

- Source commit: `6ff9e33e41f7e124ed148d5ae06aa9ef1f4f8433`.
- Library filter chips now show live counts for All games, Installed, Ready to install, and Downloading. Counts exclude DLC and derive from the current Epic catalog, local install state, and persisted active-job identity; they do not synthesize progress.
- Filter chips retain selected styling and expose descriptive accessibility labels. Existing filter behavior and game shelf rendering remain in place.
- Standard Android APK workflow **45516661167** queued: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45516661167
- Epic runtime/ARM64 workflow **45516661544** queued: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45516661544
- Previous source SHA `24da555340fb28920733dfea7548540ede3135c4` passed both workflows. The new SHA is not validated until its two runs pass. No Xiaomi 15T device validation has occurred.


## 2026-11-25 — Continue-playing history uses actual launches (validation pending)

- Source commits: `08c4c945111696e777afcc248d04f629ad3a3848` and follow-up correctness fix `cec05d21e3e5c7ba50e1e58e34f28d7176028fd4`.
- Renamed the recent shelf to CONTINUE PLAYING and replaced the previous single “last opened” marker with a local, newest-first history of up to eight distinct Epic app names.
- History is recorded only after the validated launch path reaches `startActivity()`. Opening a game profile no longer counts as playing it. The shelf is ordered by actual launches and only displays titles still installed on this device; the featured hero prefers the newest launched title only when it remains installed.
- History is local presentation state only. It is not Epic playtime, cloud data, or an assertion that a game session completed.
- The previous filter-count source SHA `6ff9e33e41f7e124ed148d5ae06aa9ef1f4f8433` passed both required workflows:
  - Standard APK workflow **45516661167**: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45516661167
  - Epic runtime/ARM64 workflow **45516661544**: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45516661544
- The new launch-history source SHA `cec05d21e3e5c7ba50e1e58e34f28d7176028fd4` is not yet validated. Do not publish/install it until both standard APK and Epic runtime/ARM64 workflows pass for this exact source SHA.
- No Xiaomi 15T physical validation has occurred for this change. Continue the substantial storefront redesign; this is a behavioral correctness improvement, not a claim of reproducing the lost original demo.


## 2026-11-25 — Continue-playing history validated; active shelf motion (CI pending)

- Continue-playing source SHA `cec05d21e3e5c7ba50e1e58e34f28d7176028fd4` passed both required workflows:
  - Standard APK workflow **45519479126**: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45519479126
  - Epic runtime/ARM64 workflow **45519479305**: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45519479305
- Follow-up source commit: `9bcea22de0684c5f415c660fa7804b21badee78f`.
- Added a thin indeterminate activity indicator along the bottom edge of a game cover while that title owns the persisted active Epic job. It communicates that work is ongoing without inventing a percentage; exact progress remains sourced from the worker and shown on the selected game's profile.
- No changes to the foreground worker, job identity, progress polling, cancel behavior, or Epic Bridge contract.
- New source SHA is not validated until both standard APK and Epic runtime/ARM64 workflows pass for this exact commit. No physical Xiaomi 15T validation has occurred.


## 2026-11-25 — Actionable collection shelf headers (CI pending)

- Source commit: `233e76b5f2e19c314bb66a44603998216904f652`.
- Each non-empty Library shelf now has a compact header with a live count derived from the same filtered game list and a `SEE ALL` action. The action switches to the corresponding real Library filter (Installed for Continue Playing, Downloading, Ready to install, or All games) and scrolls to the collection area. Empty shelves remain omitted.
- This adds useful navigation and collection hierarchy without changing Epic catalog parsing, install eligibility, persisted job identity, worker/IPC, or progress polling. Counts do not represent fabricated download percentages.
- The previous source SHA `9bcea22de0684c5f415c660fa7804b21badee78f` passed standard APK workflow **45522859040** and Epic runtime/ARM64 workflow **45522858904**. The new source SHA must pass both required workflows before it is published.
- CI for `233e76b` is pending. No Xiaomi 15T device validation has occurred; the lost original demo has not been claimed as pixel-perfectly reproduced.


## 2026-11-25 — Persistent game-profile action dock (CI pending)

- Source commits: `8866ecff1bbbf3f61f5140085eaf71f59d1c1ec5` and follow-up `e468bbe0aa0bc534adc95c2a4c7e9d2046b7dab8`.
- Moved the selected game's primary Play/Install/Cancel action from the top artwork caption into a persistent bottom dock on the profile. The dock keeps the title and a concise install-state label visible while the user scrolls achievements and account metadata. The profile scroll viewport reserves bottom space so the final cards remain reachable above the dock.
- Follow-up keeps the dock state label synchronized with the existing persisted worker monitor (queued, running, cancelling, complete, cancelled, interrupted, failed) and the install-start path. It does not create a second progress source or alter worker ownership, cancellation, Epic Bridge IPC, or cloud-save behavior.
- Shelf-header source SHA `233e76b5f2e19c314bb66a44603998216904f652` passed standard APK workflow **45525203402** and Epic runtime/ARM64 workflow **45525203862**.
- The current source SHA `e468bbe0aa0bc534adc95c2a4c7e9d2046b7dab8` still requires both workflows to pass before publication. No Xiaomi 15T physical validation has occurred; this is a profile navigation/interaction improvement, not a claim of reproducing the lost demo pixel-for-pixel.


## 2026-11-25 — Reduced-motion support for storefront navigation (CI pending)

- Source commit: `5f85b1ab717319eda4f7ecef153531004e33de1c`.
- Added a centralized Android animator-setting check and applied it to the featured-hero entrance, game-card entrance, game-profile entrance, and profile-to-library transition. When Android animations are disabled, these views are placed directly in their final visible state; navigation and content remain available.
- This is an accessibility and motion-preference improvement. It does not change the Epic catalog, achievement data, download worker, persisted job identity, progress polling, install/cancel operations, or cloud-save capabilities.
- The immediately preceding source SHA `e468bbe0aa0bc534adc95c2a4c7e9d2046b7dab8` passed both required workflows: standard APK **45527807716** and Epic runtime/ARM64 **45527807803**. The new source SHA `5f85b1ab717319eda4f7ecef153531004e33de1c` must pass both workflows before publication.
- No Xiaomi 15T physical validation has occurred. Continue the substantial storefront redesign; do not claim pixel-perfect reproduction of the lost interactive demo.


## 2026-11-25 — Reduced-motion press feedback follow-up (CI pending)

- Follow-up source commit: `1c9de5ac51bca0affb7063361418ca1ea1d98102`.
- Extended the reduced-motion guard to game-card press feedback and the persistent profile action button. With Android animations disabled, selecting a card opens its profile immediately and Play/Install/Cancel actions execute without scale animation.
- This completes coverage of the storefront's authored entrance and interaction animations. The Epic Bridge, foreground job worker, persisted job identity, progress/cancel logic, and cloud-save limitations remain unchanged.
- The previous reduced-motion commit `5f85b1ab717319eda4f7ecef153531004e33de1c` triggered standard APK workflow **45529939933** and Epic runtime/ARM64 workflow **45529939923**; both were still running when checked. Those results do not validate the follow-up SHA. Confirm new workflow runs for `1c9de5a` and require both to pass before publication.
- No Xiaomi 15T physical validation has occurred.


## 2026-11-25 — Screen-adaptive storefront artwork (CI pending)

- Source commit: `0a0c6fe1320974d1d3daead7964c59aa964edf53`.
- Replaced fixed featured-hero and game-profile artwork heights with a shared screen-height-aware calculation. The featured hero scales to 40% of display height within 220–360dp; the profile artwork scales to 48% within 260–460dp. This keeps the cinematic artwork prominent on tall phones while reducing vertical dominance on short/landscape screens.
- Portrait shelf card proportions remain unchanged. No game data, artwork host validation, download worker, persisted job identity, progress/cancel flow, achievements, or cloud-save behavior changed.
- The immediately preceding source SHA `1c9de5ac51bca0affb7063361418ca1ea1d98102` passed standard APK workflow **45529990937** and Epic runtime/ARM64 workflow **45529990965**. The new source SHA `0a0c6fe1320974d1d3daead7964c59aa964edf53` must pass both workflows before publication.
- No Xiaomi 15T physical validation has occurred; the adaptive dimensions need device review after an APK is built.


## 2026-11-25 storefront responsive-card follow-up

- Source commit: `3a387d8fd072bde1ab9e448c7ef6510b37c3d03a`.
- Added display-width-aware Epic library cover sizing: portrait shelf cards remain at least 174dp on phones and scale up to 240dp on wider displays; artwork height tracks a consistent 3:4 portrait ratio.
- This specifically avoids leaving narrow phone-sized cards on tablets/foldables while preserving the existing phone baseline and horizontal-shelf interaction.
- Reused the existing card renderer and Epic artwork validation/Glide path; no download, install, account, or worker behavior changed.
- Validation for this source commit: pending new Standard Android APK and Epic runtime/ARM64 workflows. Previous source revision `0a0c6fe1320974d1d3daead7964c59aa964edf53` passed both required workflows (runs 45535706436 and 45535706360).
- Physical Xiaomi 15T appearance/runtime validation has not been performed; CI is not a substitute for device testing.


- CI follow-up: Standard APK workflow 45604211121 passed for source `3a387d8fd072bde1ab9e448c7ef6510b37c3d03a`. Epic runtime workflow 45604211118 failed before emulator tests could start because Maven Central returned HTTP 429 for 38 Gradle artifacts (dependency rate limiting); it was not a source compile or Epic regression failure. The failed job was re-run via GitHub Actions; retry result pending. CI automatically recorded the transient failure in `PROJECT_EPIC_CI_STATUS.md` (commit `de4d605`).


## 2026-11-25 library filter accessibility follow-up

- Source commit: `961ce75be4e89edad9376c35b16dc09cf2c3c544`.
- Increased the horizontal Library filter chip height/minimum touch target from 34/36dp to 44dp.
- Filter controls now expose their selected state through Android View selection semantics and include the selected state in their accessibility description; live counts continue to be calculated from catalog/install/persisted-job state.
- No Epic account, download worker, install, or runtime behavior changed.
- Validation pending for this source revision. The previous Epic workflow attempt on `3a387d8` failed solely because Maven Central returned HTTP 429 for Gradle dependencies; GitHub retry attempt 2 is running. Standard APK workflow for `3a387d8` passed.
- No physical Xiaomi device validation has been performed.


## 2026-11-25 — Dedicated game-profile progress section (CI pending)

- Source commit: `84b18249716e4b49ccfac1862ffa3ca0df12f54f`.
- Moved the live download/install progress card out of the artwork caption overlay and into its own full-width section immediately below the cinematic profile artwork. Long worker status text and the progress indicator no longer compete with the game title and artwork overlay, especially on shorter displays.
- The card continues to use the existing persisted Epic job monitor and exact worker progress; no new progress source, download engine, IPC, cancellation, install, or cloud-save behavior was introduced.
- The previous source SHA `961ce75be4e89edad9376c35b16dc09cf2c3c544` passed standard APK workflow **45607048562**. Its Epic runtime workflow **45607048560** failed before the managed emulator test started because Maven Central returned HTTP 429 while Gradle resolved dependencies (38 artifacts affected). This was dependency rate limiting, not an application compile or Epic smoke-test failure; a failed-job retry was requested. The new source SHA requires both workflows to pass before publication.
- No physical Xiaomi 15T validation has occurred. This is a focused profile hierarchy correction, not a claim of reproducing the lost demo pixel-for-pixel.


## 2026-11-25 — Remember the last opened game in the featured hero (CI pending)

- Follow-up source commit: `1ea088a54ca296ffa928098e428aab4528e909c5`.
- The storefront now persists the app name of the last game whose profile the player deliberately opened and prefers that title for the Library featured hero. If no remembered title is available in the current catalog, selection falls back to the most recently launched installed game, another installed game, then stable catalog order. DLC entries remain excluded.
- This uses a small dedicated UI preference and does not alter launch history, Epic catalog data, game installation, worker state, or network behavior. It makes the hero respond to the player's actual browsing rather than repeatedly choosing the first installed/catalog result.
- The immediately preceding source commit `84b18249716e4b49ccfac1862ffa3ca0df12f54f` triggered standard APK workflow **45610880274** and Epic runtime/ARM64 workflow **45610880208**; they were still running/pending at the time of the follow-up. The latest source SHA must pass both workflows before publication. The earlier Epic 429 failure/retry is recorded above and is not a code validation result.
- No physical Xiaomi 15T validation has occurred. This is a data-backed storefront behavior improvement, not a claim of reproducing the lost demo pixel-for-pixel.


## 2026-11-25 — Latest Epic storefront build validated and published

- Source revision validated: `1ea088a54ca296ffa928098e428aab4528e909c5`.
- Standard Android debug APK workflow **45610978708**: passed.
- Epic runtime / ARM64 workflow **45610978644**: passed. The run completed successfully, including Epic Bridge x86_64 Android instrumentation tests, production ARM64 runtime build, packaged-runtime audit, and upload of installable Epic APKs.
- GitHub prerelease published: [MaliWinlator 1.2.1 Epic test build](https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-1ea088a54ca2). Assets: `app-standard-debug.apk`, `epicbridge-debug.apk`, and `SHA256SUMS`.
- Workflow artifact bundle also contains the ARM64 Epic runtime debug package and CPython 3.15.2 Android ARM64 POC.
- Validation is CI only. No physical Xiaomi 15T / Android 16 installation or game-download test has been performed for this revision. Do not describe CI as device validation.


## 2026-11-25 — Cinematic first-visit featured hero entrance (CI pending)

- Source commit: `b1257c6e4e283a0b33d995c347ade9823c1e37fc`.
- On the first featured hero render only, the cover art now uses a restrained 1.055x-to-1.0 scale settle while the hero container fades/slides into place; its eyebrow, title, status and action row reveal in a short stagger. The existing one-shot guard prevents search, job-progress and ordinary catalog refreshes from replaying the animation.
- The animation remains gated by Android's system animator/reduced-motion setting. No timers, background work, artwork URLs, install state, or worker behavior changed.
- The immediately preceding source revision `1ea088a54ca296ffa928098e428aab4528e909c5` passed standard APK workflow **45610978708** and Epic runtime/ARM64 workflow **45610978644**, and was published at [the 1.2.1 Epic prerelease](https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-1ea088a54ca2). The new animation commit requires fresh passing workflows before any replacement build is published.
- No physical Xiaomi 15T test has been performed. This is a focused motion refinement, not a claim of exact lost-demo reproduction.


## Engineering step log — 2026-11-25: cinematic featured-hero motion and validated release

- Source revision: `b1257c6e4e283a0b33d995c347ade9823c1e37fc`.
- Added a coordinated first-visit featured-hero entrance in `EpicFragment`: the hero surface fades/slides into place, the wide artwork settles from a restrained 1.055 scale, and the hero copy/actions reveal in a short stagger. It is one-shot per Fragment render lifecycle and respects Android's reduced-motion/animator setting. Filtering and worker progress refreshes do not replay it.
- Standard APK workflow **45613837910** passed for the exact source SHA: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45613837910
- Epic runtime / ARM64 workflow **45613837929** passed for the same source SHA: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45613837929
- GitHub prerelease published: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-b1257c6e4e28
- Assets: `app-standard-debug.apk`, `epicbridge-debug.apk`, and `SHA256SUMS`.
- Direct APKs:
  - https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-b1257c6e4e28/app-standard-debug.apk
  - https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-b1257c6e4e28/epicbridge-debug.apk
- The release is CI-validated only. No Xiaomi 15T visual/performance test has been performed for this revision, and no claim is made that it reproduces the unavailable original demo.
- The storefront remains in active redesign. Existing implemented pieces include a deterministic personalized hero, portrait-art shelves, real catalog/install/job counts, filters, persistent worker progress, dedicated game profile, and reduced-motion-aware entrance transitions. The remaining gap is overall composition and interaction depth, not merely artwork sizing or color.
- Preserve the existing Legendary/foreground-service architecture. Do not fabricate achievements, playtime, cloud-save transfer, or download progress.

### Next implementation focus

Replace the current largely linear home-page composition with a more deliberate launcher hierarchy while retaining the real data and job contracts: make the hero and collection navigation feel like one cohesive surface, improve shelf grouping and focus/selection feedback, and ensure signed-out/loading/empty/error states are intentional. Continue to keep provider/network work off the UI thread and run both CI workflows before publishing another APK.


## Engineering step log — 2026-11-25: staggered game-profile section reveal

- Source commit: `c1776606479cf7f62603a6d71cc61bfd2ed6c2f3`.
- Replaced the profile's single simultaneous parent fade/scale with a short staggered reveal of its completed content sections. Artwork, live progress, achievements, and metadata now enter in reading order instead of appearing as one undifferentiated block.
- The reveal runs once per selected game title in the Fragment lifecycle, so rebuilding the same profile after catalog/worker reconciliation does not replay the entrance. It respects Android's reduced-motion/animator setting.
- No Epic provider calls, Legendary commands, install worker behavior, persisted job identity, or progress calculation were changed.
- Standard APK CI run **45616763537**: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45616763537 — pending at handoff update.
- Epic runtime / ARM64 CI run **45616763588**: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45616763588 — pending at handoff update.
- Do not publish a new APK until both workflows pass for this exact source SHA.


## Engineering step log — 2026-11-25: unique catalog count in storefront

- Source commit: `a0e40edf0baede7a6288ef88380ff43fbfebbfb5`.
- Corrected the Library's visible game total to count unique Epic catalog entries rather than rendered shelf placements. An installed/recent title can intentionally appear in multiple shelves, but it is still one game in the summary count.
- The unique count respects the active Installed / Ready to install / Downloading filter and the current search query. DLC and empty app identifiers are excluded. Shelf contents and worker behavior are unchanged.
- This is a display correctness fix; it does not alter Epic catalog data, install eligibility, or persisted job state.
- Standard and Epic ARM64 workflows are triggered by the source commit; do not publish until both pass for the exact SHA.



## Engineering step log — 2026-11-25: focused search and category browsing

- Source commit: `350ac4f7677dfcd22d418df9f8009353d6c02c63`.
- The personalized wide hero and its collection-stat tiles now appear only on the unfiltered `All games` home. When the player searches or selects Installed / Ready to install / Downloading, that featured-home block is hidden so the relevant results shelf is immediately the primary content rather than being pushed down by an unrelated hero.
- Returning to the unfiltered All games view restores the hero and metrics. Search, filter counts, shelf membership, catalog selection, and the persisted Epic download/install worker contract are unchanged.
- Standard APK and Epic runtime / ARM64 workflows were triggered by this source commit; do not publish until both pass for the exact source SHA.
- This is a navigation hierarchy correction within the larger storefront redesign. No physical Xiaomi 15T validation has occurred.


## Engineering step log — 2026-11-25: complete collection leads the storefront

- Source commit: `13994022c5cb7d2257e8b2c8a7c5aa3dcac11c25`.
- Reordered the unfiltered Library so the complete Epic collection is the first and primary cover-art shelf. Continue Playing, Installed, Active Downloads & Installs, and Ready to Install follow as secondary shortcuts.
- Corrected the collection shelf semantics: it now includes every non-DLC catalog entry, including installed and installable games. Previously the shelf labelled "YOUR EPIC COLLECTION" excluded installed, downloading, and supported titles, so it was only showing the residual catalog subset.
- The same game may appear in the complete collection and a relevant shortcut shelf by design. Unique library summary counts remain based on catalog entries, not shelf placements. Search/filter behavior and the foreground worker remain unchanged.
- The preceding source revision `350ac4f7677dfcd22d418df9f8009353d6c02c63` passed standard APK CI **45631005362**; Epic runtime/ARM64 CI **45631005155** was still running at the time of this update. The new source SHA requires both workflows to pass before release.
- No physical Xiaomi 15T validation has occurred.

## 2026-11-25 — Featured spotlight carousel

- Published CI-validated prerelease for source `13994022c5cb7d2257e8b2c8a7c5aa3dcac11c25`: [maliwinlator-1.2.1-epic-13994022c5cb](https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-13994022c5cb). Standard Android and Epic ARM64 runtime workflows both passed on that exact SHA. APKs: `app-standard-debug.apk` and `epicbridge-debug.apk`; this is CI validation, not physical-device validation.
- New source commit `07b0bf6498bbe828a26685a8f11709d339e80ed5` adds previous/next controls to the featured hero when multiple non-DLC catalog entries exist. The chosen title is persisted in storefront UI preferences, and a short one-shot transition is used when changing the spotlight. No fabricated titles or artwork are introduced; existing action state continues to use installed/job state.
- Next: run both workflows for `07b0bf6498bbe828a26685a8f11709d339e80ed5`, inspect failures, and publish only after both pass. Continue toward a purpose-built launcher composition; this carousel is one real interaction step, not full visual parity with the unavailable original demo.

## 2026-11-25 — Storefront composition correction

- User feedback: the shipped Library still does not resemble the approved interactive demo. The earlier changes (hero, shelves, transitions) were incremental and did not deliver the demo's primary interaction pattern.
- Recovered demo requirements from prior conversation context: dark polished “MALIWINLATOR (EPIC GAMES)” storefront; “Your Library” rich clickable game grid; search and Installed / Ready to install / Downloading filters; cinematic featured artwork; animated hero/card/detail transitions; in-profile Install-to-Cancel with real progress, phase, size and ETA where the worker actually reports them; Play / Update / Uninstall actions. Preserve Epic auth, download/install worker and notifications.
- Source commit `7a1e791fdc4316e035ee71c866ddfeb55fb6001a` replaces the primary full-collection horizontal rail with a responsive two-column portrait-art grid. Secondary shelves remain shortcut rails. This is the first structural correction toward the demo, not a claim of completed parity.
- Next: validate both standard Android and Epic ARM64 workflows for the new source; then continue the larger visual rebuild. Keep progress truthful (no fabricated size/ETA); inspect worker snapshot fields before exposing any additional metadata.


## 2026-11-25 — premium launcher visual pass

- Source commit: a61698506c412dc18de81e1d8576a9688dcfea94.
- Reworked the storefront brand header to lead with MaliWinlator identity, a compact EPIC GAMES / YOUR LIBRARY descriptor, and an Account action. Reordered the Library introduction into a clearer destination hierarchy (Your library, supporting copy, cinematic spotlight and live collection stats, then browsing controls and catalog).
- Tightened catalog card framing and spacing while preserving real Epic artwork, install-state labels, click-to-profile behavior and reduced-motion-safe entrance behavior.
- No Epic Bridge, Legendary, foreground job, progress polling, notification, account or storage contract was changed.
- Standard APK workflow: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45666017688. Epic ARM64 runtime workflow: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45666017709. Both were running when this handoff was written; do not publish this source revision until both pass.
- The previous two-column grid source 7a1e791fdc4316e035ee71e866ddfeb55fb6001a passed both CI workflows (45663018664 and 45663018854), but the latest visual pass must be validated independently.
- Visual target is the recovered premium dark storefront concept. Exact pixel parity with the unavailable demo cannot be asserted. No physical Xiaomi 15T validation has occurred.


### Approved interactive concept implementation — 2026-11-25
- The user explicitly designated the interactive dark storefront concept shown in chat as the next GitHub build target, with a strict 1:1 visual/interaction intent.
- Source commit `faa678cdf7834fa3aef56f75a83da70c3f75cba2` changes `EpicFragment.renderGameCards()` so the default Library presents the featured hero and one complete two-column `YOUR GAMES` collection, rather than appending repeated Continue Playing / Installed / Downloading / Ready shelves below the same grid. Focused filters still render their respective real catalog subset; search and unique catalog counts remain active.
- Preserve real Epic backend state. Never ship sample games, synthetic progress, fake achievements/playtime, or simulated cloud-save sync from the chat prototype.
- This is the first source pass toward the approved concept, not a claim of pixel-perfect parity or a finished build. The next build must continue through the hero/card/detail visual treatment and validate both standard APK and Epic ARM64 workflows before requesting device testing.


### Approved concept — implementation refinement
- Follow-up source commit `9f1d2a98f5b44968648a2876d6be371bf5612496` adds the explicit “Browse your games” heading and updates the search prompt to “Search your library...” to match the approved interactive concept's hierarchy. It also removes the live collection-stat tiles from the default featured area, leaving the cinematic hero as the main feature before browsing.
- Build verification for this newest commit is still pending; do not distribute an APK until both required workflows report success for the same commit.

## 2026-11-25 — Persistent storefront navigation and Downloads destination

- Source commit: `84b41a819405e1359f19639fc18685f32512a43f`.
- Added a persistent in-app destination bar for Library, Downloads, and Account beneath the compact MaliWinlator identity header. The selected destination receives a visible active state; the game profile remains immersive and hides the global header/navigation until the player returns to Library.
- Implemented a dedicated Downloads screen using the existing persisted Epic job identity and foreground worker observer. It displays the active title when it can be resolved from the real catalog, the worker's latest status label, a progress indicator that becomes determinate only when the worker reports a percentage, and the existing cancel action.
- No synthetic download entries, simulated progress, new worker, Legendary command, provider API, or storage behavior was introduced. The Downloads screen is a view over the existing single active Epic operation.
- Both standard Android APK and Epic ARM64 runtime workflows must pass for this exact source SHA before publishing a new APK. Previous source `9f1d2a98f5b44968648a2876d6be371bf5612496` passed both workflows (45669164878 and 45669164854).
- No physical Xiaomi 15T validation has occurred. This is a navigation/Downloads implementation milestone, not a claim of exact pixel parity with the unavailable original demo.

- Follow-up source commit `7cb04db15d61f049fc9e8d62d851fd13858f6a13` expands the destination bar to Library, Discover, Downloads, and Settings. Discover deliberately opens the official Epic Games Store website rather than fabricating a storefront catalog or pretending the owned-library API provides store-discovery data. Settings routes to the existing Account & setup screen.
- Fresh standard APK and Epic ARM64 workflow results are required for this follow-up SHA; the previous source commit `84b41a819405e1359f19639fc18685f32512a43f` had both workflows running at the time of this note.


## Engineering step log — 2026-11-27: embedded runtime readiness hardening

- Audited the current Epic host/embedded-runtime path on `development/maliwinlator-1.2.1-bootstrap` at the prior source tip `7832aa88277e1d2c160930c98b26982f547ed644`.
- Found a readiness-reporting gap: the embedded provider's `status` response treated packaged Python assets + loadable JNI library + AES-GCM self-test as proof that Legendary was ready. It did not run `EpicPythonRuntime.initialize()`, so a CPython extraction/initialization failure could be reported as ready and only surface on the first account/library request.
- Fixed the provider status contract:
  - Added a cheap `ping` response for provider discovery; it does not extract or initialize CPython.
  - `status` now runs the actual embedded CPython initialization on the provider's background process and reports `runtime_ready=true` only after initialization succeeds.
  - Added explicit `runtime_packaged` and `runtime_initialization_failed` reporting so packaged-but-unusable runtimes are not misreported as healthy.
- Updated `EpicBridgeClient.isInstalled()` to use the cheap ping and allow the optional companion fallback when embedded runtime initialization or the AES-GCM self-test fails. The embedded one-APK path remains primary; the companion remains a fallback, not a required install.
- Source commits:
  - `304ba9b652307666c5038983bea50d8da6890267` — provider readiness now reflects actual CPython initialization.
  - `4dfd7350cd4651187db051057d24a030380e7e37` — cheap provider discovery and companion fallback for initialization failures.
- CI triggered for exact source head `4dfd7350cd4651187db051057d24a030380e7e37`:
  - Standard APK workflow **45986159980**: queued at handoff update time.
  - Epic runtime/Android instrumentation/ARM64 workflow **45986160018**: queued at handoff update time.
  - Earlier runs for intermediate commit `304ba9b652307666c5038983bea50d8da6890267` were automatically cancelled when the second source commit arrived; do not treat those as failures.
- No new APK build result, artifact, or Xiaomi 15T runtime test is claimed yet. Next gate: inspect both exact-head workflow results and logs; if successful, run device validation of first launch/runtime extraction, account status, library refresh, and the active download/re-entry path.


## Engineering step log — 2026-11-27: install-result and cancellation recovery

- Audited the foreground install result from the Python bridge through `EpicRuntimeService`, `waitForJob()`, and the game-profile callback.
- Found that Legendary's successful response is nested as `{ok:true, game:{...}}`, while the host UI read prerequisite and compatibility-warning fields from the top level. The host now reads the nested `game` object and remains compatible with older flat responses.
- Corrected terminal install failure/cancellation handling: cancellation is now described as cancellation rather than an install failure, and the Library refreshes after a terminal failed/cancelled operation so game cards cannot remain visually stuck in a downloading state.
- Restored the game-profile primary action according to actual install support and storage readiness instead of unconditionally enabling an Install button after any terminal job.
- Added an Android instrumentation regression test for the provider contract: `ping` is lightweight and does not claim runtime readiness; `status` must confirm packaged assets and successful CPython initialization.
- Additional source commits:
  - `a1fd27411d93869420b539989b1482e8c8f5722d` — nested install metadata and valid post-job actions.
  - `aee503872d084bc603d5d96212674b9ed39eb4c0` — provider readiness instrumentation coverage.
  - `d1ea96368e5ca85045e5c3949926cb2672bb6447` — terminal cancellation/failure UI recovery.
- Exact latest source head: `d1ea96368e5ca85045e5c3949926cb2672bb6447`.
- CI for that exact head:
  - Standard APK build: **45987158199**, running.
  - Epic CPython/Android instrumentation/ARM64 packaging: **45987158152**, running.
- Do not distribute an APK from the earlier `a1fd` or `aee5038` runs as the latest build; wait for the exact `d1ea963` workflows and artifact verification.

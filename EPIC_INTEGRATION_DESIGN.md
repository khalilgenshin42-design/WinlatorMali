# Epic Games integration design

## Selected architecture

Legendary is the primary Epic Games integration. The official Epic Games Launcher is a secondary, isolated Wine-container option.

### Legendary process boundary

Legendary is a separate GPL-3.0 command-line application. Keep it separately distributed/installed as a guest-environment component; do not copy its implementation into the MIT Android application or statically link it into the app.

First prove that the selected Legendary version and Python dependencies run in the Bionic/proot guest. Do not assume Android host Python is available. Record the interpreter/dependency versions, guest path, process mechanism, exit status, stdout/stderr, cancellation, login, owned-game listing, and one small test download.

### Android-side service/API

Introduce narrow components (names provisional):
- `EpicAccountManager`: account status and sign-out.
- `LegendaryClient`: typed commands and structured output parsing.
- `EpicLibraryRepository`: normalized metadata and local install state.
- `EpicInstallService`: foreground install/update/uninstall, progress, cancellation, notifications.
- `EpicGameLauncher`: maps verified install manifests to a Wine container and executable.

Do not construct shell strings in UI code. Pass argument arrays through one process adapter; validate paths and never interpolate untrusted metadata into shell commands.

### Authentication and secrets

Use Legendary's supported authentication flow. Never scrape passwords from Epic Launcher or capture them in a WebView. Never log tokens or credentials. Store credential material in app-private storage with restrictive permissions; prefer Android Keystore-backed encryption where guest access can be implemented safely. Never store tokens in shared storage or game directories.

### Library and lifecycle

Separate Epic product/catalog IDs from Wine shortcut IDs. Prefer machine-readable Legendary output. Persist manifests and state atomically. Preflight storage and runtime requirements. Support cancellation and partial-download recovery. Do not report success until expected files/manifests validate. Preserve saves and unrelated files.

### Official launcher fallback

Use a dedicated Wine prefix; never mutate the default container. Test login, library rendering, downloads, updates, launch, overlay, and persistence separately. Installer completion alone is not proof of compatibility. The fallback must not be a dependency for Legendary.

### Acceptance criteria

Legendary integration is complete only after supported login, owned-game listing, install with progress/cancellation, safe retry, update/uninstall, launch through a selected container, working shortcut, and persistence after app restart. The official launcher has an independent compatibility matrix.


## Runtime feasibility audit (2026-11-19)

The successfully built 1.2.1 APK was inspected, including its embedded `assets/imagefs.tzst` root filesystem. A tar inventory found no `python`, `python3`, or `pip` executable in that imagefs. Therefore Legendary cannot be assumed to run in the stock guest environment, and the Epic feature must not yet be implemented as a Python command invocation.

The source already provides useful integration primitives:
- `xenvironment/ImageFs`: resolves the app-private guest root at `files/imagefs`.
- `xenvironment/components/GuestProgramLauncherComponent`: launches and configures guest programs in the active container environment.
- `contents/ContentsManager`, `ContentProfile`, and `ContentDownloads`: existing runtime-component catalog/install lifecycle.
- `contents/ContentDownloadService`: foreground download notification and wake-lock pattern.
- `container/ContainerManager` and `Container`: container identity and prefix management.
- `MainActivity` and `ContentsFragment`: current navigation and component-management entry points.

### Required next engineering gate: Python runtime proof

Before UI or game-library work:
1. Choose a redistributable ARM64 CPython runtime compatible with the Android/Bionic execution environment used by this app. Do not assume a generic glibc Linux wheel or executable is compatible.
2. Keep Python, its standard library, and Legendary's Python dependencies in a dedicated app-managed component directory; do not pollute the base rootfs or user Wine prefixes.
3. Run a guest-side probe for interpreter startup, TLS/CA validation, DNS/HTTPS, subprocess execution, filesystem access, and persistence across app restart.
4. Install a pinned Legendary release into that isolated runtime and test `--version`, authentication flow initiation, and non-secret metadata commands.
5. Verify all GPL-3.0 notices/source-distribution obligations for the separately distributed Legendary executable and its dependencies.
6. Only after those tests pass, add a typed Java client and foreground Epic install service.

The current APK does not include Legendary or a Python runtime. Epic Games integration is still design/proof-of-feasibility work, not a delivered feature.


### Python runtime compatibility decision

The upstream Legendary README currently requires 64-bit Python 3.10+ and `requests`. Its standalone Linux PyInstaller executable requires glibc >= 2.25. This app's imagefs is not a conventional glibc distribution: the `usr/lib/libc.so` entry is a symlink to Android's `/system/lib64/libc.so`, and the imagefs inventory contains no Python interpreter. Therefore do not bundle Legendary's Linux standalone executable into this Bionic-based guest and assume it will run.

Preferred investigation path: build a relocatable ARM64 Android/Bionic CPython runtime for MaliWinlator's app-private directory, using the Termux Bionic build recipes as a technical reference rather than copying their fixed-prefix packages blindly. The Termux Python recipe documents its Android-specific NDK toolchain and native dependencies. Verify relocation, interpreter startup, `ssl`, `ctypes`, `zlib`, `sqlite3`, subprocesses, and TLS before adding Legendary. Keep this runtime separate from the base imagefs and from the Wine prefix.


## Android-native CPython integration decision (2026-11-19)

The official CPython Android documentation provides a better-supported route than trying to execute a generic Linux Python binary: cross-build CPython for Android, package `libpython*.so` and its extension shared libraries as JNI libraries, package the standard library/site-packages as app assets, extract them to app-private storage, and initialize Python in embedded mode through JNI. This is the preferred implementation path for MaliWinlator's Android host process, subject to a working proof of concept on the project's NDK/API level.

The official CPython Android build guide documents the configure/build/package flow and JNI embedding model. Use a pinned CPython 3.12 or 3.13 release initially (not an unpinned moving main branch), then confirm Legendary's minimum Python requirement and dependency compatibility against that exact release before locking versions. Build Android-compatible wheels/extensions for ARM64; ordinary Linux wheels are not assumed compatible.

The initial JNI embedding investigation established that the CPython Android runtime can be initialized in embedded mode, but the GPL isolation decision below supersedes the proposal to add `epicpython` to the MIT host app's CMake target. The separate `com.maliwinlator.epicbridge` companion owns the CPython JNI library and app-private `files/epic-runtime` data directory. Its JNI layer must set Python home/module search paths, disable bytecode writes into packaged assets, and return structured results over IPC.

Do not use the host app's existing `ProcessHelper` as the Epic command adapter: its current implementation logs the full environment array and command string. Authorization material must never flow through that logger or through unredacted companion diagnostics. The companion invokes Legendary with controlled argv, captures output in memory, and exposes only typed, redacted results to the host UI.

Proof-of-concept gates before UI work:
1. Build CPython Android ARM64 with the selected NDK/API and produce JNI libraries plus standard library payload.
2. Load the JNI library on Xiaomi 15T and initialize Python without changing Winlator's base imagefs or Wine prefixes.
3. Import/test `ssl`, `sqlite3`, `ctypes`, `zlib`, `hashlib`, `subprocess`, and filesystem paths; validate HTTPS, CA trust and DNS.
4. Build/install pinned Android-compatible `requests`, `filelock`, and `pycryptodomex` dependencies; validate imports.
5. Invoke pinned Legendary's CLI entrypoint in-process for `--version` and a non-authenticated help/list command. Authentication must be tested only after output redaction, token storage and lifecycle are in place.

This is a design decision based on official CPython Android packaging guidance, not yet a successful build or runtime test.


## Initial dependency pin candidates (2026-11-19)

- CPython candidate: **3.15.2**, the current stable release with the official `Platforms/Android` cross-build tooling present in its tagged source tree. The earlier 3.14.10 candidate is withdrawn: inspection of the exact v3.14.10 source tag did not find the Android platform build scripts, whereas v3.15.2 contains them. Pin the exact source tag/commit and archive checksum in CI. Legendary declares Python >=3.10, but runtime compatibility with 3.15 and all native dependencies must still be tested.
- Legendary candidate: **0.21.1**, the latest published non-prerelease release verified during this investigation. Its project metadata declares Python >=3.10, GPL-3.0-or-later, and runtime dependencies `requests`, `filelock`, and `pycryptodomex`. Pin the release tag/commit and dependency hashes; do not build from floating `master`.
- Android target architecture is `aarch64-linux-android` / app ABI `arm64-v8a`. CPython's Android build tooling explicitly lists this host triplet and builds a shared `libpython` for embedding.

These are candidates for the reproducible POC, not yet validated compatible binaries. The critical risk is building and testing `pycryptodomex` and other native extensions for Android ARM64 rather than installing desktop Linux wheels.


### CPython version correction (2026-11-19)

Inspection of the exact CPython tags found that `v3.15.2` contains `Platforms/Android/README.md` and `Platforms/Android/__main__.py`, including the `aarch64-linux-android` host target. The exact `v3.14.10` source tag did not contain that directory. Therefore the initial POC pin is corrected to CPython **3.15.2**; 3.14.10 is not the selected build path. This does not yet establish Legendary 0.21.1 or `pycryptodomex` compatibility with Python 3.15, which remains a required test gate.

### Android native dependency build strategy (2026-11-20)

Legendary 0.21.1 imports the separate `Cryptodome` namespace supplied by `pycryptodomex`, not the `Crypto` namespace supplied by the default `pycryptodome` distribution. PyCryptodome 4.x no longer publishes the separate `pycryptodomex` distribution, so use the 3.x line (candidate pin 3.23.0) unless Legendary changes its imports/dependencies.

The upstream python-for-android project has a maintained Android recipe for PyCryptodome 3.23.0 and a wheel-oriented recipe build pipeline. PyCryptodome's source build supports a `.separate_namespace` marker that changes the installed distribution/package namespace from `Crypto` to `Cryptodome`. Investigate a small custom python-for-android recipe that creates this marker before the package build, compiles the extension for `arm64-v8a`, and emits an Android ELF extension set. This is a candidate engineering route only: it must be validated against CPython 3.15's stable ABI, Android API 26+, NDK compatibility, actual device loading, and Legendary's import tests before adoption.

Pure-Python dependencies (`requests`, `filelock`, and their pure-Python transitive dependencies) can be vendored into the isolated runtime after pinning and hash verification. Do not use desktop Linux wheels for native modules. Avoid the optional pywebview dependency initially; use Legendary's manual authorization-code flow through the system browser and a secure in-app code-entry surface.

### Winlator storage and game-launch boundary

The 1.2.1 source's default container drive mapping exposes Android shared storage as Wine drive `F:` (the mapping is defined by `Container.DEFAULT_DRIVES`). The proposed Epic game library should therefore install into a dedicated folder under the app's isolated `MaliWinlator` shared-storage root, with per-container subdirectories, so the files are visible to the existing Wine environment without writing into the base imagefs or silently modifying a container's C: drive.

Legendary's native `launch` path must not be invoked as an ordinary Android host process with the Wine binary: Wine requires the app's existing proot/X environment, wrapper, audio, input, and container setup. Game launch must be handed off to the existing guest launch pipeline (or a deliberately designed adapter around it). Until Legendary's launch metadata/environment can be safely translated to the guest launch contract, direct executable shortcuts are a separate fallback and must not be represented as equivalent to full Legendary online launch semantics.

### Legendary 0.21.1 command contract

The pinned CLI source exposes a manual login path at `https://legendary.gl/epiclogin` and accepts the returned `authorizationCode` via `auth --code <value>`. This supports a native Android flow: open the URL in the system browser, let the user paste the authorizationCode into a password-style input, submit it to the embedded runtime, and clear the input immediately. Do not enable Legendary's optional WebView dependency.

The CLI has machine-readable JSON output for `list --json` and `list-installed --json`; these are the initial catalog and local-library APIs. Commands must be invoked through argv arrays, never shell concatenation. Do not log the argv array because `auth --code` contains authorization material. Capture stdout/stderr in memory, redact sensitive patterns before presenting diagnostics, and keep Legendary's config/user-data directory under app-private storage. The user-facing UI must not expose the raw `user.json` or tokens.

### GPL isolation and Android companion boundary

The WinlatorMali host application is MIT-licensed, while Legendary 0.21.1 is GPL-3.0-or-later. Do not import Legendary into the host application's Python interpreter or link it into the host APK while claiming the integration is an independent MIT component. The selected implementation boundary is a separately packaged Android companion application, provisionally `com.maliwinlator.epicbridge`, containing the CPython runtime and Legendary source under its own GPL-3.0-or-later notices/source offer. The existing MaliWinlator APK retains the Epic UI and talks to the companion through a narrow, permission-protected Android IPC contract (a signature-protected ContentProvider is the current candidate). This keeps Legendary out of the host process and avoids mixing its Python/JNI libraries into the renderer/runtime.

The companion must be independently installable/updatable and report a clear unavailable state if absent. Both APKs must be signed with the same release certificate for signature-level IPC permission. Debug builds can use the shared Android debug certificate; release signing must be supplied by the project owner. This process boundary is a software architecture choice, not a legal opinion; retain all GPL notices and corresponding-source availability for Legendary and companion modifications.

### Download/install execution policy

Legendary 0.21.1's downloader emits structured progress log lines (`= Progress: N%`) and throughput/cache summaries, so the companion can report real progress without patching the download engine. The CLI downloader also supports `--max-workers` and `--max-shared-memory`. Start with a conservative mobile profile (4 workers, 256 MiB shared-memory budget), make the worker limit configurable later, and parse only the known progress fields into the foreground-service notification. Never forward full debug logs or credentials to logcat.

Install commands should be non-interactive (`--yes --skip-sdl`), target a validated per-container folder under the MaliWinlator shared-storage root, and perform free-space checks before starting. Do not use `--ignore-free-space`. Preserve Legendary's resume/repair behavior and validate its installed manifest before reporting success.

The companion is a separate Android package and therefore does not inherit MaliWinlator's shared-storage grant. It must request its own all-files access (or a future proven filesystem-provider alternative) before installing games into `/storage/emulated/0/MaliWinlator/EpicGames`. The host UI should detect this state and deep-link to the companion's own Android storage-access settings page. Do not silently fall back to the companion's private directory because Wine in the host app cannot see that directory.

### Online game launch handoff

Legendary 0.21.1's `launch` command supports `--json` and `--no-wine`. Its CLI returns a structured `LaunchParameters` object (including game directory/executable, launch arguments, and environment overrides) before attempting to spawn a process. Use this as the host/companion boundary:

1. Companion invokes `legendary launch <app> --json --no-wine` and returns a typed, one-shot launch payload over signature-protected IPC.
2. Host validates the install path is within the EpicGames shared-storage root, converts it to the container's Wine-visible `F:\\MaliWinlator\\EpicGames\\...` path, and combines the arguments safely.
3. Host launches through its existing `XEnvironment` / `GuestProgramLauncherComponent` for the selected container, not by letting Legendary spawn a host process.
4. Any transient Epic/EOS environment values returned by Legendary are memory-only, never logged or persisted in shortcuts/preferences, and are cleared after launch setup. IPC errors and launch payloads must redact token-like values.

This preserves Legendary's online authentication/launch preparation while retaining Winlator's proot, Wine, Box64/FEXCore, graphics wrapper, audio, input, and container setup. The handoff still requires an implementation-level validation against real game launch metadata and the exact existing launcher API.

### Critical Android multiprocessing requirement

The earlier CPython 3.15.2 ARM64 package built with the upstream default Android API level 24 was inspected directly. It contains `multiprocessing/shared_memory.py` but does **not** contain the native `_posixshmem` extension. Legendary 0.21.1's downloader imports `multiprocessing.shared_memory.SharedMemory` in both its manager and worker modules, so that package cannot run Legendary correctly even though CPython itself builds.

The CPython workflow now targets Android API 26 (matching MaliWinlator's minSdk) and requires the ARM64 package to contain `lib-dynload/_posixshmem*.so`. It also runs a managed-emulator smoke test that creates POSIX shared memory, starts a multiprocessing child, attaches by shared-memory name, verifies the transferred bytes, and cleans up. Until that test and ARM64 module audit pass, CPython/Legendary runtime integration remains blocked.

The API-24 artifact also lacks the native `_multiprocessing` extension. The Android API-26 runtime audit therefore requires both `_posixshmem` (named shared-memory attachment) and `_multiprocessing` (the `SemLock`/queue primitives used by Legendary's worker queues). The emulator smoke test uses a `multiprocessing.Queue` as well as a child process, so it exercises both native facilities rather than only checking that the modules exist.

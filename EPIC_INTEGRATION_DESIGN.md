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

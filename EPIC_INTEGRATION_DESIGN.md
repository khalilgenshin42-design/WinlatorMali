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

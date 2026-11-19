# Epic Games integration design

## Selected product architecture

Legendary is the primary Epic Games integration. The official Epic Games Launcher is a secondary, isolated Wine-container option.

### 1. Legendary process boundary

Legendary is a separate GPL-3.0 command-line application. Keep it as a separately distributed/installed executable or guest-environment component; do not copy its implementation into the MIT-licensed Android application or statically link it into the Android app.

Before selecting a packaging method, prove that the chosen Legendary version and its Python dependencies run in the app's Bionic/proot guest environment. Android host Python availability must not be assumed. The first proof of concept must record:
- Legendary version and source commit/release.
- Python interpreter and dependency versions.
- Guest filesystem path and process launch mechanism.
- Return code, stdout/stderr handling, timeout/cancellation behavior.
- Whether account login, owned-game listing, and one small test download work end-to-end.

### 2. Android-side service/API

Introduce a narrow Java service layer (names are provisional):
- `EpicAccountManager`: account status and sign-out.
- `LegendaryClient`: typed commands and structured output parsing.
- `EpicLibraryRepository`: normalized game metadata and local install state.
- `EpicInstallService`: foreground install/update/uninstall work, progress, cancellation, and notifications.
- `EpicGameLauncher`: maps a verified Legendary install manifest to a Wine container and executable.

Do not make UI code construct shell strings. Pass argument arrays through a single process adapter, validate paths, quote only at the process boundary if a shell is unavoidable, and never interpolate untrusted game metadata into shell commands.

### 3. Authentication and secrets

- Use Legendary's supported authentication flow; do not scrape passwords from the Epic Launcher or implement a password-capture WebView.
- Never write tokens to Android logs, crash reports, analytics, Git, or exported diagnostic bundles.
- Keep credential files in app-private storage with restrictive permissions. Prefer Android Keystore-backed encryption for secrets at rest; evaluate how guest-process access can be provided without exposing tokens to other apps.
- Sign-out must revoke/delete local credential material and invalidate cached account state.
- Do not store tokens in shared external storage or inside user-visible game directories.

### 4. Library and install lifecycle

- Separate Epic product/catalog IDs from Wine shortcut IDs.
- Parse machine-readable Legendary output where supported; do not scrape human-formatted output when a stable JSON mode exists.
- Persist install manifests and state transitions atomically.
- Preflight available storage and the selected container's architecture/runtime requirements.
- Support cancellation and clean partial-download recovery. Do not report success until the expected executable/manifest is present and validated.
- Game updates must not overwrite user saves or unrelated files.
- On launch, resolve the executable from the installed manifest, choose the selected container, and create/update a shortcut without changing the user's default graphics configuration.

### 5. Official Epic Games Launcher fallback

- Install into a dedicated Wine prefix/container; never reuse or mutate the user's default container automatically.
- Treat installer completion as only the first checkpoint. Validate login, library rendering, game download, update, launch, and persistence separately.
- The official Windows launcher may be unreliable under Wine and is not assumed to be supported by Epic in this environment. Keep this path optional and disclose known limitations.
- Do not make the fallback a dependency for Legendary functionality.

### 6. Acceptance criteria

A Legendary integration is not complete until a clean install can:
1. Authenticate using the supported flow.
2. List the account's owned library without exposing secrets.
3. Install a selected test game with progress and cancellation.
4. Resume/retry safely after process interruption.
5. Update and uninstall without damaging unrelated container data.
6. Launch through a selected Wine container and create a working shortcut.
7. Survive app restart and Android process recreation.

The official launcher fallback has its own separate acceptance matrix and must not be used as evidence that Legendary works (or vice versa).

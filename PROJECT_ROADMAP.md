# MaliWinlator project roadmap

## Project identity

- Android app working name: **MaliWinlator**.
- Source fork: [khalilgenshin42-design/WinlatorMali](https://github.com/khalilgenshin42-design/WinlatorMali).
- Upstream fork: [GunaCharanTeja/WinlatorMali](https://github.com/GunaCharanTeja/WinlatorMali).
- Baseline branch: `bionic-mali-1.2`.
- Development branch: `development/maliwinlator-bootstrap`.
- This project is independent of the PanVK-G720 driver repository. It must not replace Android's vendor Vulkan driver.

The fork currently retains the upstream repository name `WinlatorMali`; the Android application branding can be changed independently after package/update compatibility is planned.

## Product goal

Preserve the existing Winlator Mali runtime and user features while adding an Epic Games library workflow:
1. **Legendary integration (primary):** authenticate through Legendary's supported Epic account flow, list owned games, install/update/uninstall, and launch installed games through a selected Wine container.
2. **Official Epic Games Launcher (secondary):** offer an isolated Wine-prefix/container installation path as a compatibility fallback. It is not assumed to work until tested on-device.
3. Preserve existing container, graphics-wrapper, DXVK/VKD3D, Box64/FEX, controller, shortcut, save, and content-manager functionality.
4. Keep custom Vulkan drivers as optional, importable content. Never overwrite or replace the system Vulkan implementation.

## Milestones and exit criteria

### M0 — Fork and baseline audit
- [x] Verify fork ownership and upstream parent.
- [x] Create an isolated development branch.
- [x] Inspect the Gradle project, Android manifest, build workflow, core container/shortcut/content classes, and existing Steam artwork API.
- [ ] Run a clean baseline APK build from the fork.
- [ ] Record artifact checksum, build logs, APK package name, version, and install/launch result.

### M1 — Reproducible baseline
- Validate submodule checkout and external root-filesystem asset download.
- Fix only reproducible build blockers; keep upstream runtime behavior unchanged.
- Produce a debug APK artifact before changing product functionality.

### M2 — Epic integration proof of concept
- Establish a process boundary for Legendary; do not link GPL-3.0 code into the MIT Android app.
- Verify a compatible Python/runtime environment inside the app's guest root filesystem.
- Prove non-interactive command execution, cancellation, stdout/stderr capture, exit status, and safe token handling.
- Implement account status and owned-game metadata before download/installation UI.

### M3 — Game library and lifecycle
- Model Epic catalog entries separately from existing Wine shortcuts.
- Implement install location, disk-space preflight, resumable/retryable download behavior where supported, update state, and uninstall.
- Create a launch shortcut only after install metadata and executable mapping are verified.
- Keep long-running work in a foreground service with explicit user-visible progress and cancellation.

### M4 — Official launcher fallback
- Install the official Epic Games Launcher into a dedicated container/prefix.
- Test login, store/library rendering, download, update, launch, overlay, and restart persistence independently.
- Clearly label unsupported or unreliable behavior; do not claim compatibility based on installer success alone.

### M5 — Device validation and release
- Test on Xiaomi 15T / Dimensity 8400 Ultra / Mali-G720 MC7 / Android 16.
- Run a regression matrix for existing Winlator Mali features and representative DXVK/VKD3D games.
- Publish signed APKs with reproducible build metadata, checksums, changelog, and rollback path.

## Engineering rules

- Work only in the development branch until a tested milestone is ready.
- Do not modify upstream or the PanVK repository as part of this project.
- Never commit Epic credentials, OAuth tokens, signing keys, keystores, or private user data.
- Never log passwords, authorization headers, refresh tokens, or full credential-bearing command lines.
- Keep Legendary as a separate executable/process with its own license and notices.
- Do not silently migrate or delete existing Wine prefixes, saves, or user content.
- Do not claim a build or device test succeeded unless its workflow/result is observable.

# MaliWinlator project roadmap

## Project identity

- Android app working name: **MaliWinlator**.
- Source fork: [khalilgenshin42-design/WinlatorMali](https://github.com/khalilgenshin42-design/WinlatorMali).
- Upstream: [GunaCharanTeja/WinlatorMali](https://github.com/GunaCharanTeja/WinlatorMali).
- **Required baseline: Winlator Mali Bionic 1.2.1**, tag `bionic-mali-1.2.1`.
- Baseline release commit: `dfcdd4aaeff6def126ad8173619790806a5830db`.
- Development branch: `development/maliwinlator-1.2.1-bootstrap`.
- This project is independent of the PanVK-G720 driver repository. It must not replace Android's vendor Vulkan driver.

## Product goal

Preserve the 1.2.1 Winlator Mali runtime and user features while adding an Epic Games library workflow:
1. **Legendary integration (primary):** authenticate through Legendary's supported Epic account flow, list owned games, install/update/uninstall, and launch installed games through a selected Wine container.
2. **Official Epic Games Launcher (secondary):** offer an isolated Wine-prefix/container installation path as a compatibility fallback. It is not assumed to work until tested on-device.
3. Preserve existing container, graphics-wrapper, DXVK/VKD3D, Box64/FEX, controller, shortcut, save, logging, and content-manager functionality.
4. Keep custom Vulkan drivers as optional, importable content. Never overwrite or replace the system Vulkan implementation.

## Milestones

### M0 — Correct baseline and audit
- [x] Verify the official 1.2.1 release tag and commit.
- [x] Create a clean development branch directly from the 1.2.1 commit.
- [x] Reapply only MaliWinlator identity, documentation, and CI on top of that baseline.
- [x] Verify CI ancestry check and clean APK build (run 44584271920, commit d68e700c374c16c59a0812f7bec72a8bb5455a8a).
- [x] Record second APK artifact ZIP SHA-256: `44662ff6b8374d80535e3ae2963dfbf9bec8323ef3f7135e6ef4e7cf25235340`.
- [x] User installed the isolated-storage build on Xiaomi 15T and reported on 2026-11-19 that it works. Scope confirmed: successful basic install/launch smoke test; deeper game/container regression tests remain pending.

### M1 — Reproducible baseline
- [x] Validate submodule checkout and external root-filesystem asset download.
- [x] Produce reproducible standard debug APK builds with the 1.2.1 ancestry check.
- [x] Package the separately installable Epic Bridge APK inside the host APK; CI verifies the companion APK and shared signer (run 44649327523).
- [ ] Complete device regression checks after the next installable APK is provided.

### M2 — Epic integration proof of concept
- [x] Create a separate GPL-3.0 Epic Bridge companion package with signature-protected ContentProvider IPC and a host Epic Games navigation screen.
- [x] Build CPython 3.15.2 for Android x86_64 and run it on the managed emulator.
- [x] Validate Android TLS/CA trust through Requests + certifi, plus ctypes, SQLite, zlib, SHA-256, fcntl locks and subprocess execution (run 44649787558).
- [ ] Apply and validate the pinned Legendary 0.21.1 Android thread/anonymous-mmap backend; source patching currently has a cleanup-anchor mismatch being corrected.
- [ ] Build and validate Legendary's pure-Python Android dependencies and the JNI/JCA AES-GCM shim; do not bundle host Linux native wheels.
- [ ] Integrate the runtime into the companion and prove safe command output, cancellation, token handling, account status and owned-game metadata.

### M3 — Game library and lifecycle
- Model Epic catalog entries separately from existing Wine shortcuts.
- Implement install location, disk-space preflight, resumable/retryable downloads, update state, and uninstall.
- Create a launch shortcut only after install metadata and executable mapping are verified.
- Keep long-running work in a foreground service with visible progress and cancellation.

### M4 — Official launcher fallback
- Install the official Epic Games Launcher into a dedicated container/prefix.
- Test login, library rendering, download, update, launch, overlay, and restart persistence independently.
- Clearly label unsupported or unreliable behavior.

### M5 — Device validation and release
- Test on Xiaomi 15T / Dimensity 8400 Ultra / Mali-G720 MC7 / Android 16.
- Run a regression matrix for existing Winlator Mali features and representative DXVK/VKD3D games.
- Publish signed APKs with reproducible build metadata, checksums, changelog, and rollback path.

## Engineering rules

- Work only in the development branch until a tested milestone is ready.
- Do not modify upstream or the PanVK repository as part of this project.
- Never commit Epic credentials, OAuth tokens, signing keys, keystores, or private user data.
- Keep Legendary process-isolated and preserve license notices.
- Do not silently migrate or delete existing Wine prefixes, saves, or user content.
- Do not claim a build or device test succeeded unless its result is observable.


## Baseline build result (2026-11-19)

- GitHub Actions run: [44584271920](https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44584271920) — successful.
- Source commit: `d68e700c374c16c59a0812f7bec72a8bb5455a8a`.
- Official 1.2.1 baseline ancestry check: passed.
- Gradle task: `:app:assembleStandardDebug` — BUILD SUCCESSFUL.
- Artifact: `app-standard-debug.apk`, contained in the uploaded Actions artifact.
- Artifact ZIP size: 446,620,713 bytes; SHA-256: `388b073f6015d838a653115bb61815006c20457b88ebcfb99e8da6232cf6780e`.
- APK size: 447,758,192 bytes; SHA-256: `e0de614ae5bf93d66d885a92f6de5dba649a7afcc88695e894855ee27fcab67a`.
- ZIP integrity test: passed.
- CI build success does not establish device installation/runtime compatibility. Xiaomi 15T smoke testing remains pending.


## Isolated baseline rebuild (2026-11-19)

- Found upstream's default external folder was shared as `/storage/emulated/0/Winlator`, despite the app's distinct Android package ID.
- Changed MaliWinlator's fresh-install default external folder to `/storage/emulated/0/MaliWinlator` (shortcut export path follows this default).
- Source commit: `d9da8b4446edfcdae9abf5a0efabe54096769c3c`.
- CI run [44586902271](https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44586902271): successful, including 1.2.1 ancestry check, Gradle build and artifact upload.
- Artifact: `maliwinlator-1.2.1-standard-debug-d9da8b4446edfcdae9abf5a0efabe54096769c3c`.
- Artifact ZIP: 446,620,646 bytes; SHA-256: `44662ff6b8374d80535e3ae2963dfbf9bec8323ef3f7135e6ef4e7cf25235340`.
- Target device installation/launch is still untested.


## Target-device smoke test (2026-11-19)

The user confirmed that the latest MaliWinlator 1.2.1 build works after installation on the Xiaomi 15T. This closes the basic install/launch gate. No claim is made yet about Epic functionality, game installation, or full Winlator regression coverage.

Continuity log: [PROJECT_CONTINUITY_HANDOFF.md](PROJECT_CONTINUITY_HANDOFF.md).

## Current Epic implementation status (2026-11-20)

The Epic UI entry point, companion APK packaging, signature-protected IPC bootstrap, and Android CPython runtime probe are in place. The emulator has successfully validated the APIs needed for the Legendary control plane and HTTPS. Legendary itself is not yet running in the Android companion: its downloader uses POSIX shared memory and multiprocessing, which are unavailable in the selected Bionic build, so an Android-specific thread backend is being validated. PyCryptodomex native ARM64 compatibility and the embedded JNI runtime are also outstanding. Epic login, owned-game listing, installation, updates, uninstall and Wine-container launch are not yet implemented or claimed.

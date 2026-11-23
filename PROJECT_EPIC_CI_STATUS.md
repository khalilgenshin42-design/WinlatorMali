# Epic APK CI status
- Result: **SUCCESS**
- Run attempt: 1
- Source commit: d88a6bfb73f32665bb5b26ad9af262c74e63b4b7
- Workflow run: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45221637577
- GitHub prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-d88a6bfb73f3
- Actions artifact: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45221637577/artifacts/14842342241
- Artifact name: maliwinlator-1.2.1-epic-arm64-debug
- Built host package: com.maliwinlator.app (versionCode 24)
- Built companion package: com.maliwinlator.epicbridge (versionCode 2)
- Verification: managed x86_64 runtime tests, Android JNI instrumentation, ARM64 Gradle assembly, APK signatures/package IDs/ABI/assets/ELF dependency closure/embedded companion audits.

## APK SHA-256
- 2e5ca21a2ce8d3d201090d2a944f5c3781ca6853a1df40131eefffd42c684feb  app-standard-debug.apk
- eb4d741775625c9bd4f03068ef2064755638eac3dc3aebcaa7e6a461ea08e496  epicbridge-debug.apk


## 2026-11-23 — CI passed and experimental APK published
- Fix commit `d88a6bfb73f32665bb5b26ad9af262c74e63b4b7` passed standard workflow `45221637699` and Epic runtime/instrumentation workflow `45221637577`.
- Published release: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-d88a6bfb73f3
- Main ARM64 debug APK SHA-256: `2e5ca21a2ce8d3d201090d2a944f5c3781ca6853a1df40131eefffd42c684feb`.
- CI covered managed x86_64 runtime smoke, Android JNI instrumentation (LegendaryCore, AES-GCM, worker-thread, mmap and downloader lifecycle), ARM64 packaging and embedded companion/package audits.
- Device test remains pending. UI crash correction is CI-verified, not Xiaomi 15T device-verified; progress percentage still needs device confirmation.


## 2026-11-23 — Unified Epic Library redesign validation
- Implemented a unified Library/Account navigation; removed the standalone Downloads destination.
- Game cards open per-game details with install/launch, active job progress, cancel, update/uninstall, and capability placeholders for achievements/playtime/cloud saves.
- Added Library filters for All games, Installed, Ready to install, and Downloading.
- Saved app-name association lets the UI restore the correct game details while a foreground job remains active.
- Relevant source commits: `048aa327`, `45d4ccaa`, `6ed60c18`, `e5585743`, `db7c04d0`.
- Latest source revision: `db7c04d0070c5faa033f081014556f21e8fd7863`.
- CI status for latest revision: pending/running; do not publish APK until both workflows pass.
- Device verification pending. Progress uses existing provider JSON when present and an indeterminate state otherwise. Achievement, playtime, and cloud-save integration are not yet implemented or confirmed by Epic API data.

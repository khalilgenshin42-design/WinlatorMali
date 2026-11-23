# Epic APK CI status
- Result: **SUCCESS**
- Run attempt: 1
- Source commit: db7c04d0070c5faa033f081014556f21e8fd7863
- Workflow run: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45230482434
- GitHub prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-db7c04d0070c
- Actions artifact: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45230482434/artifacts/14848870030
- Artifact name: maliwinlator-1.2.1-epic-arm64-debug
- Built host package: com.maliwinlator.app (versionCode 24)
- Built companion package: com.maliwinlator.epicbridge (versionCode 2)
- Verification: managed x86_64 runtime tests, Android JNI instrumentation, ARM64 Gradle assembly, APK signatures/package IDs/ABI/assets/ELF dependency closure/embedded companion audits.

## APK SHA-256
- c8f1f1ff47353a7c25f26a22da5ebb5fb41fae636486582201acd864ae9d7f9c  app-standard-debug.apk
- 07ea7643b02e47cffe6da1a821015c836029146cfb23b1cde9c0003f1c301929  epicbridge-debug.apk


## Capability audit (2026-11-23)
- Legendary 0.21.1 source includes Epic Store achievement definition/user-state queries and EGS cloud-save listing/upload/download/sync primitives. See `EPIC_INTEGRATION_DESIGN.md` and `PROJECT_CONTINUITY_HANDOFF.md` for integration constraints.
- These APIs have not yet been wired into the Android bridge or game details UI. Current build does not yet provide achievements display or launcher-managed cloud-save synchronization.
- Playtime remains unavailable; no authoritative endpoint has been verified.
- CI success validates package/runtime build and tests, not live Epic API behavior or Xiaomi 15T device functionality.


## 2026-11-23 backend endpoint status
- Added read-only `game_details` runner command and provider allow-list (commits `730c9c0324f12a0b7e9bd3f601546cbdfac43588`, `72974c34bc24792f44fbbdba009913b5c734f1c7`).
- This endpoint is not yet connected to the game details UI. It has not been validated against a live Epic account. Cloud-save writes remain disabled; playtime remains unavailable.
- CI validation is required before treating this as a usable feature.

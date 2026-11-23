# Epic APK CI status

- Result: **SUCCESS** — both required workflows passed.
- Source commit: `57d6be1fe6a9619b70aec9994ae4873f428402df`
- Epic runtime/package workflow: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45246911918
- Standard APK workflow: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45246912027
- Epic job `137946021591`: success.
- Standard APK job `137946014197`: success.
- Published prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-57d6be1fe6a9

## Installable test APKs

- Host APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-57d6be1fe6a9/app-standard-debug.apk
- Companion APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-57d6be1fe6a9/epicbridge-debug.apk
- Checksums: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-57d6be1fe6a9/SHA256SUMS

## Actions artifacts

- Epic ARM64 package: `maliwinlator-1.2.1-epic-arm64-debug`, ID `14854832733`, 539,163,455 bytes.
- CPython ARM64 runtime POC: `maliwinlator-cpython-3.15.2-android-arm64-poc`, ID `14855247229`, 49,961,527 bytes.
- Standard APK build artifact: ID `14853308885`, 446,739,138 bytes.

## Package verification

- Host package: `com.maliwinlator.app`, versionCode 24.
- Companion package: `com.maliwinlator.epicbridge`, versionCode 2.
- Epic checks: managed x86_64 runtime smoke, Legendary 0.21.1 checks, JNI instrumentation, ARM64 Gradle assembly, embedded companion comparison, package/signature/ABI/assets/ELF dependency closure audits.
- These are debug-signed test APKs, not production release builds. A different debug certificate may require uninstalling an older debug package first.

## SHA-256

- `909ac93d548ebbdb55de15ca2b8daec68a774b386110cdab36ade8ff1984f564`  `app-standard-debug.apk`
- `bf99e7d8311c6d962a6993a840d89b025b8a5dbf7280c0fc85d7f638b8776337`  `epicbridge-debug.apk`

## Achievement feature included

- Backend uses Legendary's parsed achievement summary API, not raw GraphQL payloads.
- Response normalization caps at 100 entries and 48 KiB, bounds user-visible strings, validates HTTPS icon URLs, clamps progress, and redacts locked hidden achievements.
- UI displays unlocked/total, XP, up to 30 entries, description, state and progress.
- Runner tests cover normalization, hidden redaction, item cap and unavailable state.
- CI success validates tests and packaging, not live signed-in Epic behavior on a physical device.

## Known limitations / next validation

- Device validation remains outstanding for sign-in, title-specific achievement data and UI behavior during active downloads/installs.
- Cloud saves expose read-only support and remote record count only. No upload/download/sync.
- Playtime is unavailable; do not infer it from local launch count.
- Before enabling cloud sync, implement explicit preview, local backup, running-game exclusion and conflict handling.

# Epic APK CI status

- Result: **SUCCESS** (both workflows)
- Source commit: `523903bd9677ac2069acf954ea42f9863d4a34a1`
- Epic runtime/package workflow: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45240800887
- Standard APK workflow: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45240800751
- Epic workflow job: `137924615201` — success.
- Standard APK workflow job: `137924617159` — success.
- GitHub prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-523903bd9677

## Release APKs

- Host APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-523903bd9677/app-standard-debug.apk
- Companion APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-523903bd9677/epicbridge-debug.apk
- Checksums: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-523903bd9677/SHA256SUMS

## Artifact and package audit

- Epic Actions artifact: `maliwinlator-1.2.1-epic-arm64-debug`, ID `14852248214`, 539,156,514 bytes.
- CPython runtime artifact: `maliwinlator-cpython-3.15.2-android-arm64-poc`, ID `14852258252`, 49,957,129 bytes.
- Standard APK artifact: `maliwinlator-1.2.1-standard-debug-523903bd9677ac2069acf954ea42f9863d4a34a1`, ID `14851012758`, 446,738,252 bytes.
- Host package: `com.maliwinlator.app`, versionCode 24.
- Companion package: `com.maliwinlator.epicbridge`, versionCode 2.
- Verification: managed x86_64 runtime tests, Android JNI instrumentation, ARM64 Gradle assembly, APK signatures/package IDs/ABI/assets/ELF dependency closure/embedded companion audits.
- These are debug-signed test APKs, not production release builds.

## SHA-256

- `bb043773c7ae968d3fd6370c26f567739c0934f07c5c572a147e5c6254726525`  `app-standard-debug.apk`
- `9af99e11700d387fecf0775b42cd4c8fc14814bbaa23978a00e2891eb3d1dd4f`  `epicbridge-debug.apk`

## Validation boundary

- CI success validates the configured runtime tests, JNI instrumentation, packaging and static audits.
- It does not establish that live authenticated Epic achievement or cloud-save metadata calls succeed for every title or on the Xiaomi 15T.
- `game_details` is read-only. Cloud-save upload/download/sync remains disabled. Playtime remains unavailable.
- The UI currently reports the availability of achievement definitions/account state but does not yet display individual achievement entries.
- Next: validate the package on-device, then inspect Legendary 0.21.1 response schemas and implement bounded achievement normalization/rendering with regression tests.

# Epic APK CI status
- Result: **SUCCESS**
- Source commit: `d7a56423fe1a998e0ce9bb2cfa88ebad9231c42e`
- Epic runtime/ARM64 workflow run: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45272578826
- Standard APK workflow run: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45272578903
- Epic Actions artifact: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45272578826/artifacts/14866657841
- Published prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-d7a56423fe1a
- Host APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-d7a56423fe1a/app-standard-debug.apk
- Companion APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-d7a56423fe1a/epicbridge-debug.apk
- Checksums: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-d7a56423fe1a/SHA256SUMS
- Artifact name: `maliwinlator-1.2.1-epic-arm64-debug`
- Host package: `com.maliwinlator.app` (versionCode 24)
- Companion package: `com.maliwinlator.epicbridge` (versionCode 2)
- Verification: managed x86_64 runtime smoke/tests, Android JNI instrumentation, ARM64 runtime/APK packaging, package/signature/ABI/assets/ELF dependency closure and embedded companion checks.

## APK SHA-256
- `14661e6637ec2f513cff42fbcc4e6e409fd732388d08806b9157e3263c9f7fb9`  app-standard-debug.apk
- `9325959f149688c2d30dd3a9543cd6b898ff70c9ef527f0893d870f7d67398f2`  epicbridge-debug.apk

## Changes validated in this build
- Achievement metadata normalization now type-checks and bounds counters/text, safely handles malformed and non-finite numeric values, and accepts only HTTPS icon URLs with a hostname.
- Per-game metadata lookup uses Legendary `update_meta=False` with Windows platform to avoid refreshing the complete catalog every time a game details page opens.
- Regression tests cover malformed achievement metadata and cached metadata lookup behavior.
- Cloud-save upload/download/sync is still disabled; cloud-save display is metadata-only. Playtime is still unavailable.
- CI success is not a substitute for authenticated Epic account testing on the Xiaomi 15T.

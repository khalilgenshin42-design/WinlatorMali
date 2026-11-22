# Epic APK CI status
- Result: **SUCCESS**
- Source commit: 3e9c2172246a7ba3f9b611656107718ed29ad64c
- Epic runtime / instrumentation workflow: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44979673157
- Standard APK workflow: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44979673164
- Epic workflow: all 31 steps passed, including managed x86_64 runtime smoke, Legendary regressions, ARM64 CPython packaging, Android instrumentation (including progress-notification deduplication), ARM64 host/runtime build, APK audits, and release publication.
- Standard workflow: all 12 steps passed, including standard debug APK assembly and embedded Epic Bridge/shared-signer verification.
- GitHub prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-3e9c2172246a
- Standard host APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-3e9c2172246a/app-standard-debug.apk
- Companion fallback APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-3e9c2172246a/epicbridge-debug.apk
- Epic Actions artifact: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44979673157/artifacts/14737623195 (ID 14737623195)
- Standard APK Actions artifact: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44979673164/artifacts/14737401181 (ID 14737401181)
- Built host package: com.maliwinlator.app (versionCode 24)
- Built companion package: com.maliwinlator.epicbridge (versionCode 2)
- Both published APKs are experimental debug-signed builds, not production releases.

## APK SHA-256
- 31710ef65262fec687585587399c5790bd5d323885a5924d27e0064010fbca4f  app-standard-debug.apk
- 5c75aae3024d87cf0111488c198921dc5ddfbdff820326113e3fe3a70f48ea95  epicbridge-debug.apk

## Device acceptance status
- Xiaomi 15T real-device acceptance: **PENDING**.
- CI and instrumentation do not establish that Epic downloads are smooth or that the Winlator UI no longer becomes unresponsive.
- Keep companion fallback installed until embedded mode passes real-device acceptance. Preserve app data and Wine containers during in-place host APK update.
- If lag persists, capture timestamped logcat, frame-jank stats, and CPU/memory/storage observations during a controlled download before further architecture or concurrency changes.

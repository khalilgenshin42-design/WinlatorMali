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


## Active-device issue and next build
- User tested the 3e9c217 build on Xiaomi 15T: progress notification works, but returning to the Epic page during an active download/install freezes and then crashes Winlator. Navigation to other tabs remains responsive. This is a device-reported regression/bug; root cause is not yet confirmed by crash logs.
- First mitigation commit: 4c5ea1d1ced8db0c69251a9fbf0bf444ddda9c28. It skips the redundant embedded Python account-status call while a job is active, removes the synchronous provider probe from the UI-thread resume path, and shuts down fragment-owned polling when that Fragment is permanently destroyed. The foreground service continues to own the install job.
- Validation is pending: Epic workflow https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45017702452 and Standard APK workflow https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45017702441.
- Do not use the previous 3e9c217 release as evidence that this issue is fixed. Do not publish the 4c5ea1d build until both workflows pass.

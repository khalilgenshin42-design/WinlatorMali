# Epic APK CI status
- Result: **SUCCESS**
- Run attempt: 1
- Source commit: e7d852dbb7913e22cbff65cfb5f55e6519c70d31
- Workflow run: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45205610044
- GitHub prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-e7d852dbb791
- Actions artifact: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45205610044/artifacts/14833329172
- Artifact name: maliwinlator-1.2.1-epic-arm64-debug
- Built host package: com.maliwinlator.app (versionCode 24)
- Built companion package: com.maliwinlator.epicbridge (versionCode 2)
- Verification: managed x86_64 runtime tests, Android JNI instrumentation, ARM64 Gradle assembly, APK signatures/package IDs/ABI/assets/ELF dependency closure/embedded companion audits.

## APK SHA-256
- 233437f1d0acc009a85dd77401855e24d993563fecc1dd4aa5e962a1f50a9822  app-standard-debug.apk
- d7dcc69527b6e259f7a87459d19f9ef0ce319b010668cbe7a2ed920b5d22d688  epicbridge-debug.apk


## 2026-11-23 — Epic job recovery build published

Exact commit `e7d852dbb7913e22cbff65cfb5f55e6519c70d31`: standard workflow `45205610114` and Epic runtime/instrumentation/ARM64 packaging workflow `45205610044` both succeeded. Android instrumentation, managed x86_64 runtime smoke, ARM64 runtime build/audit, host APK packaging, and companion embedding checks passed.

Release: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-e7d852dbb791

Main APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-e7d852dbb791/app-standard-debug.apk

Companion APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-e7d852dbb791/epicbridge-debug.apk

Main APK SHA-256: `233437f1d0acc009a85dd77401855e24d993563fecc1dd4aa5e962a1f50a9822`. Companion SHA-256: `d7dcc69527b6e259f7a87459d19f9ef0ce319b010668cbe7a2ed920b5d22d688`.

This remains an experimental debug-signed test build. Device testing on Xiaomi 15T is required before considering the active-job UI/provider-affinity issue fixed in practice. Back up containers before uninstalling any prior debug build if signature mismatch requires it.


## 2026-11-23 — Downloads UI crash fix queued for validation
- User reproduced a crash when entering Downloads during an active Epic download. The notification still works; the Downloads screen can remain stuck on “Reconnecting to the saved Epic operation…” without progress or percentage.
- Source inspection found a repeat-entry view-parent bug in `EpicFragment.showSection`: the reusable `downloadsStatusText` TextView was not detached from the old panel before being attached to a newly-created panel. Fixed by removing it from its existing parent before reattachment.
- Added fallback live labels for queued/running/cancelling job states when a progress snapshot is not yet available. Percentage display remains driven by the provider's progress JSON.
- Fix commit: `d88a6bfb73f32665bb5b26ad9af262c74e63b4b7`.
- CI status: pending; no APK published for this fix yet. Device status: not yet verified.

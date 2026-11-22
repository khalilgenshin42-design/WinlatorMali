# Epic APK CI status
- Result: **SUCCESS**
- Run attempt: 1
- Source commit: 4c5ea1d1ced8db0c69251a9fbf0bf444ddda9c28
- Workflow run: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45017702452
- GitHub prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-4c5ea1d1ced8
- Actions artifact: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45017702452/artifacts/14752258938
- Artifact name: maliwinlator-1.2.1-epic-arm64-debug
- Built host package: com.maliwinlator.app (versionCode 24)
- Built companion package: com.maliwinlator.epicbridge (versionCode 2)
- Verification: managed x86_64 runtime tests, Android JNI instrumentation, ARM64 Gradle assembly, APK signatures/package IDs/ABI/assets/ELF dependency closure/embedded companion audits.

## APK SHA-256
- 75c03a420e5fb7fd8c59f899fc5299d045c65d8f7d55b8cfe828d172ebd4a04c  app-standard-debug.apk
- 27bbc15fb5b3cb897a717983ba72655d3d6ac49a6ed9a7bba61a0b27d6af0de4  epicbridge-debug.apk


## Standard APK workflow cross-check
- Result: **SUCCESS**
- Source commit: `4c5ea1d1ced8db0c69251a9fbf0bf444ddda9c28`
- Workflow run: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45017702441
- Actions artifact: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45017702441/artifacts/14752427077
- Artifact name: `maliwinlator-1.2.1-standard-debug-4c5ea1d1ced8db0c69251a9fbf0bf444ddda9c28`

## Device acceptance status
- The re-entry mitigation has passed CI but has **not** yet been retested on the Xiaomi 15T.
- Published host APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-4c5ea1d1ced8/app-standard-debug.apk
- Published companion fallback APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-4c5ea1d1ced8/epicbridge-debug.apk
- Host APK SHA-256: `75c03a420e5fb7fd8c59f899fc5299d045c65d8f7d55b8cfe828d172ebd4a04c`
- Companion APK SHA-256: `27bbc15fb5b3cb897a717983ba72655d3d6ac49a6ed9a7bba61a0b27d6af0de4`
- Device retest must preserve app data and containers. If Android rejects the in-place update due to signature mismatch, stop; do not uninstall or clear data.

# Epic APK CI status
- Result: **SUCCESS**
- Run attempt: 1
- Source commit: 25664d0cadacb0844fb333526cc218f4d2ecfb39
- Workflow run: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45042169424
- GitHub prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-25664d0cadac
- Actions artifact: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45042169424/artifacts/14762083720
- Artifact name: maliwinlator-1.2.1-epic-arm64-debug
- Built host package: com.maliwinlator.app (versionCode 24)
- Built companion package: com.maliwinlator.epicbridge (versionCode 2)
- Verification: managed x86_64 runtime tests, Android JNI instrumentation, ARM64 Gradle assembly, APK signatures/package IDs/ABI/assets/ELF dependency closure/embedded companion audits.

## APK SHA-256
- d75076e35bacc6bb6ce71c48c364aef9ee5983ca2b59c33f239c8ba330770c3e  app-standard-debug.apk
- ddc2c458e59206df6394417ab5f8b67c4811943becc6e291c2e248a35ac2a504  epicbridge-debug.apk


## Library re-entry cache fix — validation pending
- Source head: `b5e0e84632e2be443e105a748f9bf35c73ccf1ac`
- Fragment change: `5936ed418063f646b80242c9f42267aa297a7c7e`
- Activity-scoped cache class: `b5e0e84632e2be443e105a748f9bf35c73ccf1ac`
- Standard APK workflow: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45053802111 (pending at documentation update)
- Epic runtime / Android instrumentation / ARM64 workflow: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45053802067 (pending at documentation update)
- Previous standard run on intermediate commit `5936ed4` was cancelled after the final ViewModel commit triggered the exact-head workflows.
- No APK for this fix is published yet. Wait for both exact-head workflows to succeed, then publish and device-test.

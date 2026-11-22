# Epic APK CI status
- Result: **SUCCESS**
- Run attempt: 1
- Source commit: f2a920f270404ea7cd714f275f0b8b0c3372534f
- Workflow run: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45040047012
- GitHub prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-f2a920f27040
- Actions artifact: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45040047012/artifacts/14760904858
- Artifact name: maliwinlator-1.2.1-epic-arm64-debug
- Built host package: com.maliwinlator.app (versionCode 24)
- Built companion package: com.maliwinlator.epicbridge (versionCode 2)
- Verification: managed x86_64 runtime tests, Android JNI instrumentation, ARM64 Gradle assembly, APK signatures/package IDs/ABI/assets/ELF dependency closure/embedded companion audits.

## APK SHA-256
- 7c5db3239c4078fff9a74d1a141f4ac3b68551da9f5aab607e9060b13af8a0bc  app-standard-debug.apk
- fb72950a270cc996777717b7ecc357f831939627818844868628c26fa1d7b541  epicbridge-debug.apk


## Storefront artwork follow-up validation (2026-11-22)

- Artwork extraction source commit: `1a4706f2812b7f1e71449efd026a3aa924e6ac47`.
- Artwork UI source commit: `25664d0cadacb0844fb333526cc218f4d2ecfb39`.
- Standard APK workflow for the artwork UI commit: **45042169506** — in progress at this update.
- Epic runtime/instrumentation workflow for the artwork UI commit: **45042169424** — pending at this update.
- Epic runtime workflow for the artwork extraction commit: **45042166515** — in progress at this update.
- The previous storefront-only build `f2a920f270404ea7cd714f275f0b8b0c3372534f` is fully validated and published; do not confuse its release APK with the newer artwork build.
- The artwork build must not be published until both workflows for `25664d0` succeed. The latest docs-only commits do not alter app source.

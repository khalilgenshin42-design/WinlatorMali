# Epic APK CI status
- Latest completed validation: **FAILURE (infrastructure / dependency resolution)**
- Failed run: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44974686442
- Source commit tested: `b0ae58aaf8b6efca96b9f74ece5775b5952ee54d`
- Failure stage: upstream CPython Android managed-emulator smoke Gradle setup; Maven Central returned HTTP 429 for Android build dependencies.
- Embedded-provider smoke, Android instrumentation, all-flavor APK assembly and APK audits did **not** execute in that run.
- Retry policy: commit `e4a758b9481c5d0c143dd11539343c7b7b9d2da3` adds up to three bounded retries for explicit Maven HTTP 429 responses only.
- Current artifact status: **not verified; do not install an artifact from failed run 44974686442**.
- Next gate: workflow run triggered by `e4a758b`; require host-only `host_embedded=true` test and Standard/Ludashi/AnTuTu ARM64 APK audits to pass.

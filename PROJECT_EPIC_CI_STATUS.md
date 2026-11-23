# Epic APK CI status

- Result: **SUCCESS — both workflows passed on the same source commit**
- Application source commit: `a17df9acfb03b2a9203712e8e3092ba9d6034080`
- Standard APK workflow: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45280173776
- Epic runtime/ARM64 workflow: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45280173630
- Published debug prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-a17df9acfb03
- Host APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-a17df9acfb03/app-standard-debug.apk
- Companion APK: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-a17df9acfb03/epicbridge-debug.apk
- Checksums: https://github.com/khalilgenshin42-design/WinlatorMali/releases/download/maliwinlator-1.2.1-epic-a17df9acfb03/SHA256SUMS
- Epic ARM64 Actions artifact: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45280173630/artifacts/14871390029
- Standard APK artifact: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45280173776/artifacts/14869595395

## Package details

- Host: `com.maliwinlator.app`, versionCode 24.
- Companion: `com.maliwinlator.epicbridge`, versionCode 2.
- Debug-signed test APKs, not production release builds. A different debug certificate may require uninstalling an older debug package before installing.

## SHA-256

- `2d83aae7f8f2650ef157263e4e1ba58ec86d3333df031ecba1282ef55bb28064` — `app-standard-debug.apk`
- `694b89bb949a883e5dc33fefe3291f5d0cc3fa1134679b04a3de9a43df0ec294` — `epicbridge-debug.apk`

## Included changes

- Separate executor for long-lived foreground job polling so it does not block interactive Epic commands.
- Cancellation request runs independently of the monitor loop.
- Fragment teardown detaches observers while preserving the persisted job ID for reconnection.
- Monitor submission safely handles the Fragment being destroyed between successful job start and executor submission; rejected submission does not crash the host or clear the recovery ID.
- Empty job IDs and malformed foreground job responses are handled defensively.
- Achievement metadata fields are type-checked and bounded; hidden locked achievement details are redacted; invalid icon URLs are rejected.
- Per-game detail lookup reuses cached Windows catalog metadata rather than refreshing the whole catalog each time.

## Verification boundary and remaining work

- CI covers managed x86_64 runtime smoke, JNI instrumentation, ARM64 Gradle assembly, embedded companion comparison, package/signature/ABI/assets/ELF dependency-closure audits.
- CI does not prove live authenticated Epic behavior or physical Xiaomi 15T stability.
- Device validation still required: sign-in, achievement cards, and navigation back to Epic during active download and active install. Confirm progress reconnects and no UI freeze/crash occurs.
- Cloud saves remain read-only metadata. No upload/download/sync is enabled; implement preview, local backup, running-game exclusion and conflict handling before enabling mutation.
- Playtime remains unavailable; do not infer it from launch counts.

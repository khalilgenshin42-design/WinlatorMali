# MaliWinlator Epic Bridge

This is a separately installable Android companion application. It owns the embedded CPython runtime and the Legendary-based Epic Games integration; the MIT-licensed MaliWinlator host communicates with it through a signature-protected Android ContentProvider.

## License

This module is distributed under **GNU GPL version 3 or later**. See [LICENSE](LICENSE). The host WinlatorMali application remains under the repository's MIT license. The companion APK is bundled as a separate installable package inside the host APK; it is not loaded into the host process.

## Runtime provenance

- Legendary source: https://github.com/legendary-gl/legendary
- Intended version: 0.21.1
- Pinned upstream source commit: `42f6bdeadde3a9526dc8eb713763476999ac217b`
- CPython Android source: https://github.com/python/cpython
- CPython tag: `v3.15.2`
- CPython target: `aarch64-linux-android`, Android API 26, app ABI `arm64-v8a`

The companion bootstrap currently contains only the provider and setup activity. CPython, Legendary, and their native dependencies are not yet bundled; the runtime is gated on the API-26 POSIX shared-memory and multiprocessing validation documented in `PROJECT_CONTINUITY_HANDOFF.md`.

## IPC contract

- Package: `com.maliwinlator.epicbridge`
- Provider authority: `com.maliwinlator.epicbridge.provider`
- Permission: `com.maliwinlator.epicbridge.permission.ACCESS` (signature level)
- Protocol version: 1

Build the host and companion APKs in the same Gradle invocation so they share the same signing certificate. The host installer asks Android to install the companion as a separate package with user consent.

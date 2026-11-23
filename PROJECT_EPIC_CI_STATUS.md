# Epic APK CI status
- Result: **SUCCESS**
- Run attempt: 1
- Source commit: b5e0e84632e2be443e105a748f9bf35c73ccf1ac
- Workflow run: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45053802067
- GitHub prerelease: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-b5e0e84632e2
- Actions artifact: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45053802067/artifacts/14767267120
- Artifact name: maliwinlator-1.2.1-epic-arm64-debug
- Built host package: com.maliwinlator.app (versionCode 24)
- Built companion package: com.maliwinlator.epicbridge (versionCode 2)
- Verification: managed x86_64 runtime tests, Android JNI instrumentation, ARM64 Gradle assembly, APK signatures/package IDs/ABI/assets/ELF dependency closure/embedded companion audits.

## APK SHA-256
- 259002ec0ac0230901974ec42b6d636e4f02c41f46c52d233cf443b6058ba980  app-standard-debug.apk
- 256d1926b443066cb934e1591b968dc3c3665cab8eb3fea371bdc7c6c66ae43a  epicbridge-debug.apk


## Epic library navigation cache build — SUCCESS
- Exact source head: `b5e0e84632e2be443e105a748f9bf35c73ccf1ac`
- Standard APK workflow **45053802111**: success — https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45053802111
- Epic runtime / Android instrumentation / ARM64 workflow **45053802067**: success — https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45053802067
- Published release: https://github.com/khalilgenshin42-design/WinlatorMali/releases/tag/maliwinlator-1.2.1-epic-b5e0e84632e2
- Main APK SHA-256: `259002ec0ac0230901974ec42b6d636e4f02c41f46c52d233cf443b6058ba980`
- Companion APK SHA-256: `256d1926b443066cb934e1591b968dc3c3665cab8eb3fea371bdc7c6c66ae43a`
- Remaining: device acceptance of instant cached navigation, manual refresh, and cache invalidation after library mutations.


## Epic active-job recovery hardening — validation pending
- Source commit: `ce08bb6ff1eb65572762123857603addf3f31b35`
- Change: retain the persisted active Epic job ID when status remains `unknown`; mark the operation pending, reconnect when Downloads is opened, and surface reconnect/provider errors in the Downloads status label.
- No APK is published for this commit yet.
- The combined commit-status query returned no status entries at the time of this update. Verify the exact source commit through both the standard APK and Epic runtime/instrumentation/ARM64 workflows before publishing.


## Epic provider-affinity recovery — validation pending
- Active-job retention/reconnect fix: `ce08bb6ff1eb65572762123857603addf3f31b35`.
- Provider-affinity persistence and fallback recovery: `6bf0b9f73aa7cdf6d6855a05fd135b2153379c9f`, hardened by `47758e3c7d19d81df2f5564ff41aa6c01d49d08e`.
- The host now pins each job's status/cancel calls to the provider that accepted `start_job`; older jobs probe the alternate provider if the first returns `unknown`. The UI no longer deletes a saved job ID merely because status remains unknown for 30 seconds.
- No APK has been published for these commits. The combined commit-status query returned no status entries for `47758e3` when checked. Exact-head standard APK and Epic runtime/instrumentation/ARM64 validation is still required.
- Legacy cancellation routing follow-up: `e7d852dbb7913e22cbff65cfb5f55e6519c70d31` persists the resolved provider authority after probing the embedded or companion job registry.


## Exact-commit CI results — `e7d852dbb7913e22cbff65cfb5f55e6519c70d31`

- **Standard APK workflow:** PASS. Run [45205610114](https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45205610114). Standard debug APK built; embedded Epic Bridge APK matched the standalone bridge build byte-for-byte and host/bridge signer digests matched.
- Standard artifact: `maliwinlator-1.2.1-standard-debug-e7d852dbb7913e22cbff65cfb5f55e6519c70d31`; artifact ID `14834860041`; SHA-256 `5e247b22adf5f03c775c937194e6cf6a8fec0edf8ed87da6f6cb5877a0197326`.
- **Epic runtime workflow:** IN PROGRESS. Run [45205610044](https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45205610044). CPython x86_64/ARM64 builds, package audit, host tests, library regression tests, and managed-emulator runtime prerequisite smoke passed. x86_64 Epic Bridge instrumentation is currently running; ARM64 host/companion APK package and prerelease are pending.
- The standard APK artifact is not yet the final Epic-enabled package. Wait for the Epic runtime workflow's own artifacts and successful completion before asking for device installation.

# Epic APK CI status

- Result: **BUILD PASSED; GitHub prerelease publication blocked by token permissions**
- Source commit: `868139dffd985d96c27fe0ff41a9e78359d3ef2f`
- Workflow run: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44964489203
- Completed successfully: CPython x86_64/ARM64 builds, managed Android runtime smoke, Android JNI instrumentation, AAR/ARM64 APK build, companion package/signature/ABI/assets/ELF dependency audits, and artifact uploads.
- Failed step: `gh release create` returned HTTP 403, `Resource not accessible by integration`. The Actions token cannot publish Releases in this repository integration despite the workflow declaring `contents: write`.
- APK artifact: [maliwinlator-1.2.1-epic-arm64-debug](https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44964489203/artifacts/14731726432) (artifact ID `14731726432`, archive SHA-256 `69891d50c83e5f15154e3d6cad0ac47a18c504039f5ac2460243055b498d14f7`).
- CPython artifact: [maliwinlator-cpython-3.15.2-android-arm64-poc](https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44964489203/artifacts/14731352655) (artifact ID `14731352655`).
- Host `:app:assembleStandardDebug` also passed in standard APK workflow [#44964489199](https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44964489199); its artifact is [here](https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44964489199/artifacts/14730514861).
- Important qualification: the successful run built the host with `:epicruntime` as a Gradle dependency, but the old workflow did not yet inspect the merged host APK for runtime native libraries, Python assets, and provider authority. A new explicit host-merge audit is running in workflow [#44967380029](https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/44967380029). Do not treat the current artifact as a validated one-package device build until that audit passes and runtime wiring/device acceptance are completed.
- The previous two-app companion flow remains the tested fallback. These are debug-signed artifacts, not production-signed releases.

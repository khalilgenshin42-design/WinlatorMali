# Source audit — Winlator Mali Bionic 1.2.1

## Verified source baseline

- Upstream: `GunaCharanTeja/WinlatorMali`.
- Release tag: `bionic-mali-1.2.1`.
- Exact tag commit: `dfcdd4aaeff6def126ad8173619790806a5830db`.
- The development branch is created directly from that commit; the first MaliWinlator commit has that exact commit as its parent.
- Development branch: `development/maliwinlator-1.2.1-bootstrap`.
- The older `bionic-mali-1.2` branch is not the baseline for this work.

## 1.2.1 release fixes retained

The upstream 1.2.1 release notes identify fixes/polish for MediaTek Mali reboot reports, shortcut crashes, controller binding editor, D-pad stutter, File Manager dependencies/hangs, EGL buffer flushing, ZeroFG frame multiplier behavior, Gladio OpenGL context limit, D7VK content type, Wine/Proton prefix setup, and Log Manager. The development branch starts from the tagged release rather than attempting to cherry-pick these fixes onto 1.2.

## Android/build configuration

- Android module: `app`.
- Android Gradle Plugin: 8.8.0; Java source/target compatibility: 17.
- `compileSdk 34`, `targetSdkVersion 28`, `minSdkVersion 26`.
- NDK: `29.0.14206865`; CMake project: `app/src/main/cpp/CMakeLists.txt`.
- ABI filter: `arm64-v8a`.
- Baseline namespace/default application ID: `com.winlator.cmod`.
- Upstream standard flavor ID: `com.winlator.mali`; MaliWinlator standard flavor changes to `com.maliwinlator.app` to remain a separate install.
- Upstream standard label: `Winlator Mali`; MaliWinlator standard label: `MaliWinlator`.
- The root filesystem task downloads `imagefs.tzst` from the upstream Winlator-Extras GitHub release when absent.
- `.gitmodules` includes `libadrenotools`; CI checks out submodules recursively.

## Existing extension points

- `MainActivity`: primary navigation and initialization.
- `ShortcutsFragment`, `container/Shortcut`: game shortcut presentation/model.
- `container/ContainerManager`, `container/Container`: container and Wine-prefix lifecycle.
- `contents/ContentsManager`, `contents/Downloader`, `contents/ContentDownloadService`: downloadable runtime components and foreground download lifecycle.
- `core/steam/SteamStoreAPI`, `core/steamgrid/*`: Steam metadata/artwork support, not Epic account or install support.

## Audit boundaries

This is a static source/configuration audit plus Git ancestry verification. It is not yet a successful Gradle build, APK installation, runtime validation, security review, or complete license audit.

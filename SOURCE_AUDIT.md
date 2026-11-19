# Source audit — Winlator Mali Bionic 1.2.1

## Repository snapshot

- Fork: `khalilgenshin42-design/WinlatorMali`
- Parent: `GunaCharanTeja/WinlatorMali`
- Source branch: `bionic-mali-1.2`
- Audited commit: `0411a4b602ed00d71bef52e6a7ccb82a8e709b1a`
- Development branch created from that commit: `development/maliwinlator-bootstrap`
- Android module: `app`
- Root project name: `Winlator`

## Android/build configuration observed

- Gradle wrapper exists at repository root.
- Android Gradle Plugin version is 8.8.0; Java source/target compatibility is 17.
- `compileSdk 34`, `targetSdkVersion 28`, `minSdkVersion 26`.
- NDK is pinned to `29.0.14206865`; native build uses CMake at `app/src/main/cpp/CMakeLists.txt`.
- ABI filter is currently `arm64-v8a`.
- Standard product flavor package ID is `com.winlator.mali`; namespace/default application ID are `com.winlator.cmod`.
- App label in the standard flavor is currently `Winlator Mali`.
- The app build has a `downloadAssets` task that downloads `imagefs.tzst` from the upstream Winlator-Extras release when the file is absent.
- `.gitmodules` contains the `libadrenotools` submodule. CI checks out submodules recursively.
- The manual GitHub Actions workflow offers standard/AnTuTu/Ludashi and debug/release builds, and uploads APK artifacts.

## Relevant existing extension points

- `MainActivity`: primary navigation and app initialization.
- `ShortcutsFragment`: existing game shortcut/library presentation.
- `container/Shortcut`: shortcut data model.
- `container/ContainerManager` and `container/Container`: container and Wine-prefix lifecycle/configuration.
- `contents/ContentsManager`, `contents/Downloader`, `contents/ContentDownloadService`: downloadable runtime components and foreground download lifecycle.
- `core/steam/SteamStoreAPI` and `core/steamgrid/*`: Steam store search/artwork support. This is metadata/artwork integration, not an existing Epic account or game-install implementation.
- The app currently exposes Wine/Proton, DXVK/VKD3D, Box64/FEXCore, graphics-wrapper configuration and multiple wrapper-driver asset IDs through its container configuration.

## Build workflow observations requiring validation

1. The workflow is manual (`workflow_dispatch`); no automatic APK build is triggered merely by pushing a branch.
2. The workflow uses Java 17 and installs Android SDK 34, Build Tools 35.0.0, CMake 3.22.1, and the pinned NDK.
3. The Gradle `downloadAssets` task has a network dependency on an external GitHub release. CI must be able to fetch it; the downloaded asset should be verified by a pinned checksum before release-grade builds.
4. The repository includes a large number of runtime assets and submodule dependencies. Build success, artifact completeness, and APK size need to be measured, not inferred from static inspection.
5. The workflow currently names artifacts by flavor/build type and has a 14-day retention period. No successful workflow run or APK artifact has yet been verified in this project.

## Audit boundaries

This is a static source/configuration audit of the files listed above. It is not a successful Gradle build, APK installation, runtime validation, security review, or complete license audit. Those are explicit follow-up tasks.

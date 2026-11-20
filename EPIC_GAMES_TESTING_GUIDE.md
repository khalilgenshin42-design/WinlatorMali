# MaliWinlator 1.2.1 — Epic integration test guide

> This guide applies only to the independent `development/maliwinlator-1.2.1-bootstrap` fork. It does not replace or modify Android's system/vendor Vulkan driver.

## Build status

Use this guide only with the Epic ARM64 debug APK pair published by the Epic runtime workflow after its complete CI validation succeeds. The pair consists of:

- `app-standard-debug.apk` — MaliWinlator host app (`com.maliwinlator.app`)
- `epicbridge-debug.apk` — separate Epic companion/runtime (`com.maliwinlator.epicbridge`)

Do not use an artifact labelled bootstrap-only or a build whose CI status says failure, in progress, or unverified. Debug builds may require uninstalling an older copy signed with a different debug certificate.

## Install and prepare

1. Download both APKs from the same successful Epic prerelease/run.
2. Install **Epic Bridge** first, then install **MaliWinlator 1.2.1**.
3. Open Epic Bridge and grant its own **Allow access to manage all files** permission in Android settings.
4. Return to MaliWinlator and grant the host app the same all-files permission when prompted.
5. In Epic Bridge, tap **Validate Epic runtime**. Continue only if the runtime self-test passes.
6. Open MaliWinlator and create or select a Wine container.

## Sign in and install a game

1. Open the Epic tab and tap **Sign in**.
2. Complete sign-in on Epic's official web page. Copy the returned `authorizationCode` value into the host app's authorization field and submit it.
3. Refresh the library. Choose the Winlator container where the game should live.
4. Tap **Install** on a supported Windows title. The companion runs the download in a foreground service; keep the companion installed and do not force-stop it.
5. Wait for the host UI to report completion. If Android interrupts the service, reopen the Epic tab to resume status tracking; retry only after checking whether the prior job completed.
6. Tap **Launch** on the installed title. The integration maps the container-specific Epic directory into Wine's `C:\\Games\\Epic` path and passes Epic's launch parameters/environment to the selected container.

## Current limitations to expect

- This is an experimental debug integration, not an official Epic Games Launcher build.
- Legendary installs supported Windows library titles. Third-party-store titles and titles without a Windows build are not directly installable through this path.
- Epic prerequisite installers are reported but are not automatically run.
- Successful compilation and emulator instrumentation do not prove that every Epic account, game download, anti-cheat, launcher dependency, or game will work on the phone.
- Keep the original system/vendor Vulkan driver untouched. Choose the custom graphics driver only inside the Winlator container if the build exposes that option.

## What to include in a test report

- APK release tag and SHA-256 values
- Android version and device model
- Whether Epic Bridge runtime self-test passed
- Whether sign-in and library loading passed
- Game title, install result, selected container, and any error text
- Whether launch reached the game's main menu; include a logcat excerpt for crashes

Never share passwords, browser cookies, Epic session tokens, or ownership-token files in a report.

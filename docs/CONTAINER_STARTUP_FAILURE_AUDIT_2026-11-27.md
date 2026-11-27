# Container startup failure audit — 2026-11-27

## Evidence from the user's log bundle

The captured launch attempts repeatedly report an empty `.update-timestamp` and invoke:

```
.../bin/wine wineboot -h
```

Each attempt spends approximately 30 seconds in the prefix-update helper, then logs `wineboot exit 1`. The helper then terminates a PID after its global Wine-process polling period. This sequence repeats on subsequent launches.

## Source-level root cause

`GuestProgramLauncherComponent.updatePrefixBeforeSession()` compares the Wine prefix's `.update-timestamp` with the mtime of the active layer's `share/wine/wine.inf`. It then used `wineboot -h`.

Wine's own `programs/wineboot/wineboot.c` defines:
- `-h, --help`: display help and exit.
- `-u, --update`: update the wineprefix directory.

The source parses `-h` by calling its usage/help routine before prefix initialization/update. The code therefore never requested the update that the timestamp comparison requires. This is why the marker remains stale and the helper repeats on each launch.

Reference: Wine source, `programs/wineboot/wineboot.c`:
https://github.com/wine-mirror/wine/blob/master/programs/wineboot/wineboot.c

## Changes made

In `app/src/main/java/com/winlator/cmod/xenvironment/components/GuestProgramLauncherComponent.java`:
1. Changed the operation to `wineboot -u`.
2. Removed the 30-second global `/proc` Wine-process polling and `terminateAllWineProcessesAndWait()` cleanup. This cleanup was not scoped to the current prefix and could terminate unrelated Wine sessions. The prefix update is a pre-session step; its wineserver/services can be reused by the guest session immediately afterward.
3. Read `.update-timestamp` after the command and log success only when the exit status is zero and the timestamp matches the current `wine.inf` mtime. Failure is logged with the exit status, expected marker, actual marker, and elapsed time.

Added a CI static guard in `.github/workflows/maliwinlator-ci.yml` to reject regression to `wineboot -h`, reject global process cleanup in this helper, and require timestamp verification.

## Validation status

- Source change committed; CI is triggered by the source/workflow changes.
- The Wine option semantics are confirmed from Wine's own source.
- CI static validation and APK build must complete before an artifact is considered ready.
- No target-device runtime validation has yet been performed. In particular, we still need to confirm on the user's Android 16 device that `wineboot -u` returns zero, updates `.update-timestamp`, and allows the container to remain open.
- Do not reset/delete the user's container or uninstall the app as part of this diagnosis.

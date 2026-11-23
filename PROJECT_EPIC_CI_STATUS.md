# Epic APK CI status
- Result: **FAILURE**
- Run attempt: 1
- Source commit: 72974c34bc24792f44fbbdba009913b5c734f1c7
- Workflow run: https://github.com/khalilgenshin42-design/WinlatorMali/actions/runs/45234381233
- APK artifact: not verified; do not install.
- Failure details: inspect the failed steps in the workflow run above.


- Latest source head under validation: `4d24258dcb7c502e498a1188f3533343f7faeee0`.
- Previous Epic runtime run `45234381233` failed at Python import due to the malformed `game_details` dispatch block; see the workflow job log for the `IndentationError`. No Epic APK was produced from that run.
- The standard APK workflow for `72974c34bc24792f44fbbdba009913b5c734f1c7` passed, but it did not validate the Epic runtime.
- The dispatch fix, endpoint regression tests, and game-details UI wiring are now committed. Await CI runs for the latest branch head; do not install an artifact from the failed run.

# Physical TV navigation smoke

Run from PowerShell 7 with an already connected ADB device and the personal app installed:

```powershell
./tools/personal-tv-smoke.ps1 -AdbPath $env:ADB_PATH -Serial $env:ANDROID_SERIAL -OutputFolder ./work/tv-smoke -ColdStart
```

`AdbPath`, `Serial`, and `OutputFolder` are mandatory. The script contains no device address.
`ColdStart` explicitly permits force-stopping only `org.smarttubey.personal`; without it the
script launches the app normally. Neither path clears application data or changes settings,
accounts, permissions, networking, or the device's sleep state.

The fixture expects English controls and a populated Home page with its first video card focused.
The launcher uses MAIN/LEANBACK_LAUNCHER. A configured alternative boot page or an existing
modal screen fails that precondition rather than modifying preferences. Account and password
gates stop with `needs_input`; the script never types credentials or selects a saved account.

The journey checks Home rail/search, native keyboard hardware input, the public NASA channel
result and live shelf, Back restoration, account chooser cancellation, Advanced disclosure,
and opening/closing the original Accounts options without selecting a setting. It does not
click a live video, sign in, rate, subscribe, add to a playlist, or collect logcat. Player and
track-change journeys remain in `docs/personal-player.md` and the manual validation journey.

Use `-CheckSubscriptions` to append an optional All-feed check. When real card images exist,
the video pane must expose nonempty title metadata and a focused card with visible metadata.
No recommendation title or channel count is fixed. A focused known empty-feed/sign-in status
is recorded as `empty_skipped`, supporting both anonymous and signed-in runs.

Every checkpoint confirms that the resumed package is `org.smarttubey.personal`. Each ADB
operation and checkpoint has a maximum 20-second wait. All hierarchy polling shares one
180-second elapsed budget by default, including initial snapshots and retries; optional checks
use that same budget. `PollBudgetSeconds` can range from 1 to 300 seconds; individual checkpoints
and ADB operations retain their 20-second cap. The harness uses uncompressed hierarchy snapshots
to retain screen-container IDs. It estimates snapshot collection cost from observed timings and
stops before a retry that cannot fit the remaining window. A missed expected state or exhausted
collection window is reported as a harness assertion failure, without labeling the app as
unresponsive. There are no fixed sleeps or unbounded retry loops.

Each run creates a distinct folder with `summary.json` and `private/`. The summary contains
assertion names, status, timings, and evidence filenames, with no account names, video titles,
or device serial. Raw XML, screenshots, and process diagnostics are private evidence and may
contain account details, history, or the device identity. Do not publish that directory.

Exit codes: `0` passed, `1` failed, `2` needs manual input. The script removes only its own
uniquely named temporary XML/PNG files from the TV. This script has received parser and
recorded-hierarchy selector checks; its physical execution is performed by the integration owner.

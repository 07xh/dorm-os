# APK Reference Review — 白秋然雨 2.1

The user supplied the APK and stated that its author permitted reuse. The original source repository and a separate license file were not included in the sample. The static findings and their limits are recorded in [BAIQIU_APK_REVERSE_NOTES.md](BAIQIU_APK_REVERSE_NOTES.md).

## Verified capabilities

- Jetpack Compose / Material3 UI and AndroidX DataStore Preferences.
- Root/Shell process checks and execution wrappers. A `LIBSU` string exists, but the DEX type table does not establish a specific LibSU API dependency.
- SH manager models/actions: CreateScript, SaveScript, ExecuteScript, DeleteScript, ScriptFile, ScriptItem and ScriptsViewState.
- A dedicated `拓展命令` directory, recursive folder traversal, and explicit `.sh` extension validation.
- Execution/run log paths and Toast, vibration and flashlight helper features.
- UI references: lavender panels, four-tab navigation, searchable script cards and run/edit/delete actions.

## Incorporated into DORM OS

The new native app uses Kotlin/Compose. The KernelSU module owns the protected script store; the app provides SH import/create/edit/search/run/delete and NFC binding. SAF is used for import rather than copying the original app's broad all-files permission. NFC tags contain only UUIDs.

The campus network is not a separate feature. A campus script is an ordinary user-owned `校园网.sh`.

## Deliberately excluded

- Side-key, volume-key and power-key hooks, global/app gesture scopes.
- Fluid-cloud persistent notifications, app inventory scanning and unrelated flashlight/vibration panels.
- Magisk and LSPosed compatibility.
- KSU WebUI and any boot-time service or polling loop.
- Original APK, DEX and binary assets are not embedded in the new project.

## Limits

The APK is R8-obfuscated. The analysis maps functional evidence but is not a recovery of the original maintainable Kotlin project. The user's statement of author permission is recorded as provenance; it is not independently verifiable from the APK itself.
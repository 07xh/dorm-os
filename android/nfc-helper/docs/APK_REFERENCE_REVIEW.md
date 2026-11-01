# APK Reference Review — 白秋然雨 2.1

The user supplied the APK and stated that its author permitted reuse. No source repository or license text was supplied; the inspection separates binary evidence from the new implementation.

## Observed in APK strings and screenshots

- Jetpack Compose / AndroidX DataStore class names and dependencies.
- LIBSU and Root shell diagnosis symbols.
- Script-management action names: CreateScript, SaveScript, DeleteScript, ExecuteScript, ScriptsViewState.
- Script directory and log strings: 拓展命令, 命令执行日志.md, 运行日志.md.
- The screenshots show rounded lavender panels, four tabs, searchable SH cards, run/edit/delete controls and notification/haptic settings.

This confirms capabilities and UI inspiration, not the original internal source code architecture.

## Incorporated ideas

The new native app uses Kotlin/Compose and implements search, create, SAF import, edit, delete, Root run confirmation, plus NFC binding. The common KernelSU-only module dispatcher stores scripts under UUID paths and keeps display names in Base64 metadata. NFC never reads a Shell command from a tag.

No original APK/Dex/assets are embedded. Side-key hooks, persistent key listener, cloud capsule, accounts, Magisk and LSPosed are excluded. The campus network is one ordinary SH file, not a special feature.

## Testing limits

Preview until Android build and Ace 6 Android 16 NFC/Root device checks pass. Imported scripts run under Root; only run and bind code you have reviewed.

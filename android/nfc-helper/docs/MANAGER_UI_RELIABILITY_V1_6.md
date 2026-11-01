# DORM NFC Manager V1.6 — UI reliability and readable execution history

## UI failure handling

Module refresh and Root actions are wrapped with exception handling and loading-state cleanup. Coroutine cancellation is rethrown instead of being converted into a false Root failure. Unexpected Root bridge exceptions become a visible RootResult error, and the toast surfaces the first structured DORM marker or a concise error detail.

## Execution output

The home page displays the tail of the recent output so the final status is not hidden by a long script transcript. The full recent output remains available in Settings.

## Execution history

The module stores compact rows as epoch-seconds | script UUID | ok/failed. The app now formats the timestamp in local time, resolves UUIDs to script names where possible, labels outcomes in Chinese, skips malformed rows, and limits display to 80 valid events. No command output or credentials are copied into the persistent event log.

## Verification

Unit tests cover malformed rows, script-name resolution, success/failure labels, and the 80-event display limit. CI must pass before this preview increment is considered built.
